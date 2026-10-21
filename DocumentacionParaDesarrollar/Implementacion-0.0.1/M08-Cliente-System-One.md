# M08. Cliente System One (Jev)

> **Estado:** Local listo — verificado con la cuenta real el 2026-09-29 salvo 403, 429 y 529 (ver [Verificación con la cuenta real](#verificación-con-la-cuenta-real-llamadas-de-pago-aprobadas-por-el-usuario-las-ejecutó-el-orquestador-en-m13)) · **Entrega:** C · **Depende de:** M02, M03 · **Desbloquea:** M09 · **Diseño:** [plan §6](../Plan-Jev-y-combate-tactico.md#6-cliente-directo-de-jev-protocolo-system-one), [plan §7](../Plan-Jev-y-combate-tactico.md#validación-de-eficiencia-velocidad-concurrencia-e-identificadores) · **Reglas:** [00](00-Arquitectura-y-reglas.md) · **Dominio:** [01](01-Modelo-de-dominio.md)

> **Revisión 25 (2026-10-09, M29).** `ModelExpectation` trata un identificador `kev-…` como modelo versionado. Con `jev.provider: kev` no se lee `TYPESAFE_API_KEY`; se usa `KEV_API_KEY` si existe. Un cambio de modelo marca la cobertura destilada como obsoleta (Errata E29-13 aplicada). Esto no es asesoramiento legal.

## Objetivo

Implementar el puerto `engine.DecisionClient` con un adapter directo y estricto del protocolo System One de TypeSafe. El adapter convierte un `DecisionRequest` inmutable en una solicitud `POST /v1/systemone`, valida la respuesta y devuelve un `DecisionResult`. También comprueba credenciales y modelo con `GET /v1/models`. Nunca toca el mundo ni el estado de `engine`.

## Alcance

### Incluye

- Cliente HTTP único por proceso, con URL base configurable y plazos acotados.
- Codificador semántico en inglés: de `CombatFacts` a `state`.
- Construcción de preguntas `choice` para la situación actual y para contingencias.
- Parser estricto de respuestas y clasificación de errores en `FailureKind`.
- Comprobación de servicio sin consumo de inferencia.
- Contabilidad de `usage.input_tokens`.
- Servidor HTTP de contrato y fixtures para pruebas locales.
- Protección de la clave de API.

### No incluye

- Decidir cuándo enviar, presupuestos, pausas y reintentos: M09.
- Aceptar respuestas y crear intenciones: M10.
- Capturar hechos del mundo: M07.
- Retirar el adapter de OpenRouter: M12.
- Prueba con la cuenta real de TypeSafe: puerta externa de M13 y M14.

## Base existente

| Archivo | Acción |
|---|---|
| `src/main/java/akyrion/thepredict/adapter/openrouter/OpenRouterDecisionProvider.java` | Referencia del patrón `sendAsync` más `orTimeout`, de la cancelación que se propaga al transporte y del parseo estricto. No se modifica; M12 lo elimina. |
| `src/main/java/akyrion/thepredict/decision/DecisionProvider.java` | Lo sustituye `engine.DecisionClient` (M02). M08 implementa el nuevo puerto. |
| `src/main/java/akyrion/thepredict/decision/ProviderFailure.java` | Referencia de clasificación (`fromHttpStatus`, `fromThrowable`). `domain.FailureKind` (M02) la sustituye; la excepción no se reutiliza. |
| `src/main/java/akyrion/thepredict/decision/ProviderHealth.java` | Lo sustituyen `domain.ServiceCheck` y `domain.ServiceState` (M02). |
| `src/main/java/akyrion/thepredict/config/ProviderSettings.java` | `config.JevSettings` (M03) aporta URL, modelo, clave y timeout. La raíz construye `SystemOneSettings` a partir de ella. |
| `src/main/java/akyrion/thepredict/DecisionProviderFactory.java` | La raíz crea `SystemOneClient` directamente. La fábrica se elimina en M12. |
| `src/test/java/akyrion/thepredict/adapter/openrouter/OpenRouterDecisionProviderTest.java` | Referencia del uso de `com.sun.net.httpserver.HttpServer` en `127.0.0.1:0`. Se elimina en M12. |
| `src/main/java/akyrion/thepredict/adapter/jev/` (vacío) | Recibe todas las clases de este módulo. |

## Subpuntos

### M08.1 Cliente HTTP y transporte

- **Propósito:** un solo cliente reutilizable, con plazos y límites, que no bloquee el hilo del servidor.
- **Diseño:**
  - `SystemOneClient implements DecisionClient`. Constructor: `SystemOneClient(SystemOneSettings settings, ServerClock clock)`. Crea un `HttpClient` con `HttpClient.newBuilder().version(HTTP_2).connectTimeout(settings.connectTimeout()).followRedirects(NEVER).build()`, **con el executor propio del JDK**: nunca se le entrega el executor acotado, porque en el JDK (`HttpClientImpl.DelegatingExecutor`) una tarea rechazada cierra el selector y deja el cliente muerto hasta reiniciar (hallazgo de la auditoría).
  - `executor`: `ThreadPoolExecutor` propio con 2 hilos fijos, cola `ArrayBlockingQueue` de 64 tareas, hilos daemon con nombre `ThePredict-SystemOne-N`. Se usa para la admisión, la codificación y el parseo, nunca para el transporte. Con la cola llena, `decide` devuelve al instante `Failed(NETWORK, "client queue is full")` (y `check`, `PAUSED` con el mismo detalle) sin tocar el `HttpClient`. El parseo de una respuesta ya recibida no se rechaza nunca: si la cola está llena corre en el hilo que completa la future del JDK (el `ForkJoinPool` común), para no descartar una respuesta pagada.
  - Una future pública que ya terminó (plazo, cancelación o cierre) no se codifica ni se envía: la tarea comprueba `isDone()` antes de codificar y otra vez antes de `sendAsync`.
  - `decide(DecisionRequest)` no serializa en el hilo que llama: `CompletableFuture.supplyAsync(() -> encoder.encode(request), executor)`, después `sendAsync` y después el parseo.
  - Solicitud: `POST {baseUrl}/v1/systemone` con `Authorization: Bearer <clave>`, `Content-Type: application/json`, `Accept: application/json` y `User-Agent: ThePredict/<versión>`.
  - Plazo por solicitud con `HttpRequest.timeout(settings.requestTimeout())`, 800 ms por defecto. Como respaldo, `completeOnTimeout(TIMEOUT, requestTimeout + 200 ms)` garantiza que la future pública termina con `Failed(TIMEOUT)` y no de forma excepcional; al completarse aborta el intercambio.
  - Cuerpo de respuesta leído con `BoundedBody.handler(settings.maxResponseBytes())`, 256 KiB por defecto: un suscriptor no bloqueante que corta al cruzar el límite (desviación 3 de "Estado de implementación"). Si se supera el límite, la respuesta es `INVALID_RESPONSE`.
  - Cancelación: si `engine` cancela la future devuelta, el adapter cancela la future del transporte, como hace hoy `OpenRouterDecisionProvider`. No se promete cancelar el cobro remoto (plan §6, punto 8).
  - Sin reintentos automáticos. Una solicitud fallida devuelve `Failed`; la siguiente decisión del encuentro la sustituye.
  - `close()`: `executor.shutdownNow()` y `client.shutdownNow()`. Tras cerrar, `decide` y `check` devuelven una future completada con `Failed(NETWORK, …)`.
  - La primera comprobación de servicio (M08.6) abre la conexión HTTP/2. Así la primera inferencia no paga el establecimiento de TLS.
- **Archivos:** crear `adapter/jev/SystemOneClient.java`, `adapter/jev/SystemOneSettings.java` y `adapter/jev/BoundedBody.java`.
- **Reglas:** D4, T2, T4, T5, C6, C7, C11.
- **Pruebas:** `SystemOneClientTest` contra el servidor de contrato de M08.8: éxito, timeout, respuesta mayor que el límite, cancelación antes de responder y llamadas después de `close()`.
- **Aceptación:**
  - [x] Un solo `HttpClient` y un solo executor por proceso. *Por instancia de `SystemOneClient`; vale para todo el proceso cuando M09.7 cree el cliente una sola vez con `SystemOneWiring` (hoy no hay otro punto de creación).*
  - [x] `decide` retorna sin serializar en el hilo que llama; una prueba lo comprueba con el nombre del hilo del codificador.
  - [x] Una respuesta que no llega en `requestTimeout + 200 ms` completa la future con `TIMEOUT`.
  - [x] Un cuerpo mayor que `maxResponseBytes` produce `INVALID_RESPONSE` sin cargarlo entero en memoria.
  - [x] `close()` deja cero hilos vivos del executor.

### M08.2 Codificador semántico

- **Propósito:** entregar a Jev hechos con significado, en inglés, sin números crudos ni texto de jugadores (plan §6, "Estado semántico, no numérico").
- **Diseño:**
  - `SemanticEncoder` es una clase pura: `JsonObject encodeState(CombatFacts facts, Map<MobKey, TacticalProfile> profiles)`. No lee relojes ni configuración global.
  - Estructura de `state`:
    - `encounter`: duración aproximada (`"about one minute"`) y número de participantes.
    - `mobs`: una entrada por `MobFacts`, con referencia `mob_1`, `mob_2`… en el orden de `facts.mobs()`, `objective` del perfil, banda de salud, relación con su objetivo y señales (`MobCue`).
    - `players`: una entrada por `ParticipantFacts`, con su alias (`player_1`…), banda de salud, banda de armadura, señales (`TargetCue`) y rango de amenaza (`"highest threat"` para 1).
    - `recent_events`: frases de `EncounterEvent`, del más reciente al más antiguo.
  - Cada enum se traduce con una tabla fija de frases en inglés, por ejemplo `DistanceBand.MELEE` → `"target is inside melee reach"`, `TargetCue.RAISING_SHIELD` → `"target is raising a shield but not blocking yet"`, `Trend.BACKING_AWAY` → `"target is backing away"`. Las frases salen de la tabla semántica del plan §6.
  - Orden determinista: listas en el orden recibido y claves de objeto en orden fijo. Dos codificaciones de los mismos hechos producen el mismo texto.
  - Tope de tamaño: `maxStateChars`, 12 000 caracteres por defecto (≈3 000 tokens, muy por debajo del límite de 32k del plan §6). Si se supera, se recorta en este orden: eventos más antiguos, señales de jugadores sin pregunta asociada y detalles de jugadores con menor amenaza. Nunca se recortan los mobs que tienen preguntas.
  - Textos permitidos: `objective` y `Description` del autor (configuración de confianza) y las tablas fijas. Nunca nombres de jugador, nombres de ítem, lore ni UUID.
- **Archivos:** crear `adapter/jev/SemanticEncoder.java` y `adapter/jev/SemanticPhrases.java` (tablas de frases).
- **Reglas:** A2 (el codificador no está en `domain` porque produce texto de protocolo), C3, C12, I6.
- **Pruebas:** `SemanticEncoderTest` con archivos golden en `src/test/resources/systemone/golden/` (`encounter-melee.json`, `encounter-shield.json`, `encounter-three-mobs.json`, `encounter-truncated.json`). Prueba de que cada valor de cada enum tiene frase. Prueba de recorte y de determinismo.
- **Aceptación:**
  - [x] Todos los valores de `HealthBand`, `DistanceBand`, `Trend`, `ArmorBand`, `MobCue`, `TargetCue` y `EventType` tienen frase; una prueba falla si se añade un valor sin frase.
  - [x] El `state` de los golden coincide byte a byte.
  - [x] Ningún golden contiene UUID, nombres ni números de posición.
  - [x] El recorte respeta el orden definido y nunca elimina un mob con pregunta.

### M08.3 Construcción de preguntas

- **Propósito:** una pregunta `choice` por `QuestionSpec`, con criterios que Jev pueda comparar.
- **Diseño:**
  - `QuestionBuilder.build(QuestionSpec spec, TacticalProfile profile, String mobRef) → JsonObject` con `type: "choice"`, `instructions` y `criteria`.
  - Instrucciones por `Situation`:
    - `NOW`: `"Which tactic should mob_N use now against its current target?"`.
    - `IF_TARGET_IN_MELEE`: `"Which tactic should mob_N use if its target enters melee reach?"`.
    - `IF_OWN_HEALTH_CRITICAL`: `"Which tactic should mob_N use if its own health becomes critical?"`.
  - Criterio de cada opción: frase del `Movement` (por ejemplo, `KEEP_DISTANCE` → `"stay between the inner and outer distance band"`), más las descripciones de `PredictValue` de su choice, más `Description` si existe. Las claves de `criteria` son los `TacticId`: son lo único del perfil que llega al modelo como nombre de opción (I4).
  - La clave de la pregunta en `questions` es `QuestionKey.value()` (`q0`, `q1`…). TypeSafe no pasa esa clave al modelo (I3).
  - Validación previa: entre 2 y 255 opciones por pregunta (plan §6); todas las opciones existen en el perfil. Una violación es un error de programación del ensamblador de M09 y produce `Failed(INVALID_REQUEST, …)` sin enviar nada.
  - `RequestEncoder` compone el cuerpo completo: `{"model": …, "state": …, "questions": {…}}`, con `model` tomado de `SystemOneSettings`.
- **Archivos:** crear `adapter/jev/QuestionBuilder.java` y `adapter/jev/RequestEncoder.java`.
- **Reglas:** I3, I4, C2, C7.
- **Pruebas:** `QuestionBuilderTest` (cada situación, criterios con y sin `Description`, 1 opción rechazada, 256 opciones rechazadas) y `RequestEncoderTest` (cuerpo completo contra un golden).
- **Aceptación:**
  - [x] El JSON de cada pregunta valida contra el esquema `ChoiceQuestion` de la OpenAPI oficial (0.2.0: `type` = `choice` y `criteria` objeto obligatorios, `instructions` opcional). `ContractFixturesTest` lo comprueba sobre el golden de la solicitud.
  - [x] Ninguna solicitud sale con menos de 2 o más de 255 opciones.
  - [x] Las claves de pregunta del cuerpo coinciden con las de `QuestionSpec`.

### M08.4 Parser estricto de respuestas

- **Propósito:** aceptar solo respuestas completas, coherentes con lo preguntado y numéricamente válidas.
- **Diseño:**
  - `ResponseParser.parse(String body, List<QuestionSpec> questions) → DecisionResult.Answered` con Gson (`JsonParser.parseString`).
  - Comprobaciones, en este orden:
    1. Raíz objeto con `model` (texto no vacío), `answers` (objeto) y `usage.input_tokens` (entero ≥ 0).
    2. Una respuesta por cada `QuestionKey` enviada; una clave desconocida en `answers` es inválida.
    3. Cada respuesta con `type` igual a `"choice"`, `choice` texto, `confidence` finito en [0, 1] y `probabilities` objeto.
    4. Las claves de `probabilities` son exactamente las opciones enviadas: ni faltan ni sobran.
    5. Cada probabilidad es finita y está en [0, 1]; la suma está en 1 ± 0,02.
    6. `choice` es la opción de mayor probabilidad; en empate se acepta cualquiera de las empatadas.
  - Campos nuevos no documentados se ignoran sin error (plan §6, punto 5).
  - `Answer` se construye con las probabilidades validadas y `confidence`. `effectiveModel` e `inputTokens` pasan a `Answered`.
  - Cualquier violación lanza `SystemOneProtocolException` (privada del paquete), que el cliente convierte en `Failed(INVALID_RESPONSE, detalle)`. El detalle nombra la regla violada y la clave de pregunta, nunca el cuerpo.
  - El parser no compara `model` con el configurado: esa decisión es de M10.1, que conoce si el modelo configurado es un alias.
- **Archivos:** crear `adapter/jev/ResponseParser.java` y `adapter/jev/SystemOneProtocolException.java`.
- **Reglas:** C4, C7, C8.
- **Pruebas:** `ResponseParserTest` con fixtures: respuesta válida, falta una pregunta, pregunta extra, `type` distinto, opción ajena en `probabilities`, opción faltante, probabilidad negativa, `NaN` o infinito, suma 0,95, `confidence` 1,2, `choice` que no es la máxima, campo nuevo tolerado, JSON roto.
- **Aceptación:**
  - [x] Cada regla tiene al menos un caso positivo y uno negativo.
  - [x] Una respuesta con un campo extra no documentado se acepta.
  - [x] Ningún detalle de error contiene fragmentos del cuerpo.

### M08.5 Clasificación de errores

- **Propósito:** traducir cada fallo a un `FailureKind` y a una pausa sugerida, sin exponer secretos.
- **Diseño:**
  - `FailureClassifier.fromStatus(int status, HttpHeaders headers, Optional<String> validationBody) → DecisionResult.Failed` y `fromThrowable(Throwable) → DecisionResult.Failed`.
  - Tabla de estado:

    | Estado | `FailureKind` | `retryAfter` |
    |---|---|---|
    | 400 con `detail.error_type` = `api_usage_error` y `detail.message` que empieza por `Unknown model` (forma real, M13) | `MODEL_UNAVAILABLE`, detalle fijo sin el modelo | vacío |
    | Otro 400 | `INVALID_REQUEST` | vacío |
    | 401, 403 | `AUTHENTICATION` | vacío |
    | 422 con `detail[].loc` que contiene `"model"` | `MODEL_UNAVAILABLE` | vacío |
    | 422 en otro caso | `INVALID_REQUEST` | vacío |
    | 429 | `RATE_LIMITED` | cabecera `retry-after` si existe |
    | 529 | `OVERLOADED` | cabecera `retry-after` si existe |
    | Otro 5xx | `SERVICE_ERROR` | vacío |
    | Otro código | `SERVICE_ERROR` | vacío |

  - 401 y 422 están documentados por TypeSafe; 403 y la forma exacta de un modelo inexistente en `/v1/systemone` no lo estaban. **Verificado con la cuenta real (M13, 2026-09-29):** un modelo inexistente es HTTP 400 `api_usage_error` y un 401 lleva `detail` como objeto; el cuerpo de un 400 se lee con `StrictJson`, acotado, y solo para decidir entre las dos filas de 400. 403 sigue sin observarse.
  - Para 422 solo se leen las rutas `loc` del esquema `HTTPValidationError` (nombres de campo), nunca `msg` ni `input`.
  - `RetryAfter.parse(String)`: segundos enteros o fecha HTTP (`DateTimeFormatter.RFC_1123_DATE_TIME`). Resultado acotado a [0 s, 300 s]. Convertir una fecha HTTP en duración es el único uso de reloj de pared del adapter (excepción documentada a C6).
  - Excepciones: `HttpTimeoutException` y `TimeoutException` → `TIMEOUT`; `ConnectException`, `IOException` y resto → `NETWORK`; `CancellationException` → `NETWORK` con detalle `"cancelled"`, que M10.1 descarta sin contar como fallo del servicio.
  - `detail` incluye el código HTTP y la regla o campo afectado. Nunca cabeceras, clave ni cuerpo.
- **Archivos:** crear `adapter/jev/FailureClassifier.java` y `adapter/jev/RetryAfter.java`.
- **Reglas:** C7, C8, A7.
- **Pruebas:** `FailureClassifierTest` (cada fila de la tabla, 422 con y sin `model` en `loc`) y `RetryAfterTest` (segundos, fecha, valor negativo, valor enorme acotado, texto inválido).
- **Aceptación:**
  - [x] Cada estado de la tabla produce el `FailureKind` indicado.
  - [x] `retryAfter` nunca supera 300 s ni es negativo.
  - [x] Una prueba verifica que ningún `detail` contiene la clave configurada.

### M08.6 Comprobación de servicio

- **Propósito:** saber si credencial y modelo son usables sin pagar una inferencia.
- **Diseño:**
  - `check()` envía `GET {baseUrl}/v1/models` con la misma autenticación y lee `models[].name`.
  - Resultado como `ServiceCheck`:
    - 200 y el modelo configurado aparece en la lista → `READY`, detalle `"model listed"`.
    - 200, el modelo no aparece y tiene forma de ID versionado (`^[a-z0-9-]+-\d+\.\d+\.\d+$`, por ejemplo `jev-1.13.0`) → `READY`, detalle `"credential valid; model unverified until first inference"` (plan §6, punto 2).
    - 200, el modelo no aparece y no es versionado → `INVALID_MODEL`.
    - 401 o 403 → `INVALID_CREDENTIALS`.
    - 429, 529, 5xx, red o timeout → `PAUSED`, con el detalle de M08.5.
  - La comprobación no demuestra calidad de combate ni que la inferencia funcione; el detalle lo dice.
  - `ModelCatalogParser` valida el esquema `ModelMetadataList` de la OpenAPI y tolera campos nuevos.
- **Archivos:** crear `adapter/jev/ModelCatalogParser.java`; ampliar `SystemOneClient.check()`.
- **Reglas:** C7, A7.
- **Pruebas:** `ModelCatalogCheckTest` con el servidor de contrato: alias listado, versionado no listado, alias no listado, 401, 529, lista vacía y JSON roto.
- **Aceptación:**
  - [x] `jev-1.13.0` ausente de la lista devuelve `READY` con el detalle de "sin verificar".
  - [x] Ningún resultado de `check()` consume `POST /v1/systemone`.

### M08.7 Tokens y trabajo fuera del hilo principal

- **Propósito:** medir el consumo real y garantizar que el adapter no carga el hilo del servidor.
- **Diseño:**
  - `Answered.inputTokens` lleva `usage.input_tokens`. `output_tokens` se ignora porque es gratuito (plan §6).
  - Un `Failed` no reporta tokens: el consumo es desconocido y M09 lo contabiliza con su estimación.
  - Codificación, envío y parseo corren en el executor de M08.1. `decide` solo crea la cadena de futures.
  - El adapter registra, con límite de frecuencia y solo si `decision.logDecisions` está activo, tiempos internos de codificación y parseo. Las métricas oficiales por tramo son de M09.6.
- **Archivos:** `SystemOneClient` (sin clases nuevas).
- **Reglas:** T1, T2, C8, C12.
- **Pruebas:** en `SystemOneClientTest`, `inputTokens` coincide con el fixture; el hilo que ejecuta `SemanticEncoder` no es el hilo que llamó a `decide`.
- **Aceptación:**
  - [x] `inputTokens` se propaga sin cambios.
  - [x] Ningún trabajo de codificación ocurre en el hilo llamante.

### M08.8 Servidor de contrato y fixtures

- **Propósito:** probar el ciclo HTTP completo sin cuenta real ni red externa.
- **Diseño:**
  - `SystemOneContractServer` (solo pruebas) sobre `com.sun.net.httpserver.HttpServer` en `127.0.0.1:0`, con contextos `/v1/systemone` y `/v1/models`.
  - Escenarios programables por prueba: éxito; 401; 422 de validación con `loc` de modelo y sin él; 429 con `retry-after: 2`; 529; 500; JSON roto; respuesta tardía (espera mayor que el timeout); conexión cortada antes de responder; opción ajena; probabilidades inválidas; `model` distinto del configurado; cuerpo mayor que el límite.
  - Registra cada solicitud recibida (cuerpo y cabeceras) para que las pruebas comprueben `Authorization`, `model` y claves de pregunta.
  - Fixtures en `src/test/resources/systemone/` con la forma exacta de `SystemOneResponse` y `ModelMetadataList` de la OpenAPI oficial.
- **Archivos:** crear `src/test/java/akyrion/thepredict/adapter/jev/SystemOneContractServer.java` y los fixtures.
- **Reglas:** P3, P2 (sin `sleep` en las aserciones: se espera a la future con plazo).
- **Pruebas:** el propio servidor se usa en M08.1 a M08.6 y en las pruebas de integración de M09.
- **Aceptación:**
  - [x] Todos los escenarios listados tienen una prueba que los usa.
  - [x] Ninguna prueba del módulo sale a la red.

### M08.9 Protección de la clave

- **Propósito:** que la clave nunca aparezca en registros, excepciones, estado ni volcados.
- **Diseño:**
  - `config.JevSettings` (M03) resuelve la clave: `TYPESAFE_API_KEY` (o la variable configurada en `jev.apiKeyEnvironment`) tiene prioridad sobre `jev.apiKey`.
  - La raíz construye `SystemOneSettings` a partir de `config.JevSettings` (D10). `SystemOneSettings` es un record con `baseUrl`, `model`, `apiKey`, `connectTimeout`, `requestTimeout`, `maxResponseBytes` y `maxStateChars`. Sobrescribe `toString()` para mostrar `apiKey=<redacted>`.
  - La cabecera `Authorization` se construye una vez y solo vive en `SystemOneClient`.
  - Ninguna excepción del adapter incluye cabeceras. `FailureClassifier` nunca copia texto de la respuesta.
- **Archivos:** `SystemOneSettings`, `SystemOneClient`.
- **Reglas:** C8, C13.
- **Pruebas:** `SystemOneSettingsTest` (`toString` sin clave) y una prueba que recorre los `detail` de todos los escenarios de M08.8 buscando la clave.
- **Aceptación:**
  - [x] La clave no aparece en `toString`, `detail` ni registros de ningún escenario.
  - [x] La variable de entorno gana a `jev.apiKey`.

## Riesgos y decisiones abiertas

- **Heredado de M07:** un objetivo no percibido sigue en el índice del encuentro, así que sus eventos pueden aparecer en `recentEvents` con un alias que no está en `participants`. El codificador lo tolera (no falla ni lo descarta en silencio; lo escribe con su alias). Fuera de combate, `combatSeconds` es la duración del último combate (semántica vanilla de `CombatTracker`).
- **Incorporado en 01 (revisión 2):** añadir a `DecisionRequest` el componente `Map<MobKey, TacticalProfile> profiles` (inmutable). El codificador necesita `objective`, `Movement`, `PredictValues` y `Description` para construir `state` y criterios, y `adapter.jev` no puede consultar `engine` (D7). Alternativa: incluir el `TacticalProfile` en cada `QuestionSpec`.
- ~~Forma exacta de un modelo inexistente en `/v1/systemone` (¿422 o 404?)~~ **Resuelto con la cuenta real (M13, 2026-09-29):** HTTP 400 `api_usage_error`; clasificado como `MODEL_UNAVAILABLE`. Uso real de 403: sin observar.
- Si el modelo configurado es un alias (`jev-latest`), `effectiveModel` será un ID versionado distinto. M10.1 debe fijar el primer ID recibido como esperado en ese caso.
- La latencia real host → TypeSafe y el tamaño medio de `state` los **mide M13**; `maxStateChars` y `requestTimeout` pueden ajustarse con esa evidencia.
- Claves incluidas en M03 (revisión 2): `jev.connectTimeoutMillis` (1 500 por defecto), `jev.maxResponseBytes` (262 144) y `jev.maxStateChars` (12 000).

## Terminado

- [x] M08.1 a M08.9 con sus aceptaciones marcadas.
- [x] `./gradlew clean build javadoc` verde, con `ArchitectureRulesTest` confirmando D4.
- [x] Ninguna prueba depende de red externa ni de `Thread.sleep`.
- [x] El documento refleja cualquier diferencia encontrada frente a la OpenAPI oficial ("Comparación con la OpenAPI 0.2.0" en "Estado de implementación").
- [x] Estado del módulo actualizado en [README.md](README.md).

## Estado de implementación

> **Local listo — verificación con la cuenta real pendiente (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` (también con `--rerun-tasks`) pasa sin avisos de compilación ni de Javadoc: **930 pruebas, 0 fallos** (807 de partida + 123 nuevas, 23 de ellas de las correcciones tras la auditoría). **No se hizo ninguna llamada real a TypeSafe ni a otro host**: todas las pruebas HTTP usan `SystemOneContractServer` en `127.0.0.1` con puerto efímero; ninguna prueba lee `.env` ni el entorno real (W6) ni usa `Thread.sleep` (P2). No se arrancó ningún servidor de Minecraft. 00 y 01 no cambian: el puerto `DecisionClient` ya estaba en 00 §4 y no hay tipos de dominio nuevos.

### Archivos

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…`) |
|---|---|
| Puerto | Creados `engine/DecisionClient` (puerto de 00 §4, con `unavailable(ServiceCheck)`) y `engine/UnavailableDecisionClient` (record de paquete, implementación nula sin E/S) |
| M08.1 | Creados `adapter/jev/SystemOneClient`, `SystemOneSettings`, `BoundedBody`, `SystemOneExecutor` (executor acotado), `PendingCalls` (futures públicas, plazos, admisión y cierre), `AbortableExchange` (cancelación del transporte) |
| M08.2 | Creados `adapter/jev/SemanticEncoder`, `SemanticPhrases`, `StateDraft` (partes del `state` y recorte) |
| M08.3 | Creados `adapter/jev/QuestionBuilder`, `RequestEncoder`, `RequestSizeGuard` (límites estimados de tokens), `SystemOneRequestException` |
| M08.4 | Creados `adapter/jev/ResponseParser`, `SystemOneProtocolException`, `StrictJson` (lectura JSON estricta compartida) |
| M08.5 | Creados `adapter/jev/FailureClassifier`, `RetryAfter` |
| M08.6 | Creado `adapter/jev/ModelCatalogParser`; `SystemOneClient.check()` |
| M08.7 | Creado `adapter/jev/TimingLog` (tiempos internos con límite de frecuencia) |
| M08.9 | Creado `SystemOneWiring` en la raíz: traduce `config.JevSettings` a `SystemOneSettings` (D10) y crea `SystemOneClient` o `DecisionClient.unavailable(UNCONFIGURED)`. **No se conecta en `onEnable`**: lo hace M09.7, para que ningún arranque haga un `check()` real antes del coordinador |
| M08.8 (pruebas) | `src/test/java/akyrion/thepredict/adapter/jev/SystemOneContractServer` y `JevFixtures`; fixtures en `src/test/resources/systemone/` (`response-valid.json`, `models.json`, `models-empty.json`, `validation-model.json`, `validation-other.json`) y golden en `systemone/golden/` (`encounter-melee.json`, `encounter-shield.json`, `encounter-three-mobs.json`, `encounter-truncated.json`, `request-two-questions.json`) |
| Sin cambios | `adapter/openrouter/*`, `decision/*`, `config/ProviderSettings`, `DecisionProviderFactory` (W5: siguen compilando y sus pruebas pasan; M12 los retira) |

### Pruebas nuevas (123, con las de la auditoría)

- `adapter.jev` (113): `SystemOneClientTest` (20; las 3 añadidas tras la auditoría figuran en su tabla: solicitud del contrato con `Authorization`, `Content-Type`, `Accept`, `User-Agent`, `model` y claves de pregunta; modelo efectivo distinto; codificación en `ThePredict-SystemOne-N` y no en el hilo llamante; respuesta que nunca empieza → `TIMEOUT`; cuerpo que se detiene tras las cabeceras → `TIMEOUT` al vencer el margen y executor libre después; cuerpo mayor que el límite, fragmentado y con `Content-Length`; 401, 403, 500, 503 y 529 sin reintentos; 422 con y sin `model` en `loc`; 429 con `retry-after: 2`; JSON roto, opción ajena, suma inválida y `confidence` ausente; conexión cortada sin reintento; opción no declarada y `state` que no cabe sin enviar nada; cancelación antes de responder; `close()` con una solicitud en vuelo; llamadas tras `close()`; tiempos con límite y sin clave; ningún `detail` de `decide` ni de `check` contiene la clave aunque el servidor la devuelva), `ModelCatalogCheckTest` (11: alias listado, versionado no listado, alias no listado, lista vacía, 401 y 403, 529, 429 y 500, JSON roto, timeout, fixture exacto de `ModelMetadataList`, tolerancia de metadatos, otra forma; ninguna comprobación hace `POST`), `ResponseParserTest` (15: claves repetidas y anidamiento, `output_tokens` ausente tolerado, cada regla con casos positivos y negativos, suma 0,98 y 1,02 aceptadas y 0,979, 1,021 y 0,95 rechazadas, `NaN` literal, `1e400`, empate, campo nuevo tolerado, raíz, contenido sobrante, ningún mensaje cita el cuerpo), `SemanticEncoderTest` (15: objetivo solo de mobs preguntados, objetivo oculto dicho una vez, cuatro golden byte a byte, sin UUID, nombres ni números, determinismo con otro orden de conjuntos, referencias y orden de eventos, alias de evento ausente de `participants`, mob de evento fuera de la captura, `state` justo en el límite y un carácter por encima, orden completo de recorte en 12 pasos, rechazo si no cabe, frases de duración), `SemanticPhrasesTest` (4), `QuestionBuilderTest` (7: tres situaciones, forma y claves de `criteria`, criterios con y sin descripción, 1 opción, 255 y 256 opciones, opción no declarada, opción repetida), `RequestEncoderTest` (8: golden del cuerpo completo, objetivo sin pregunta no filtrado, guarda de tokens en el límite, por `state` más pregunta y por total), `FailureClassifierTest` (10), `RetryAfterTest` (5), `BoundedBodyTest` (9), `SystemOneSettingsTest` (5), `TimingLogTest` (1), `ContractFixturesTest` (3).
- `engine`: `DecisionClientTest` (3). Raíz: `SystemOneWiringTest` (5: la variable gana a `jev.apiKey`, clave de `config.yml` sin variable, traducción completa, sin clave → `UNCONFIGURED`, con clave → `SystemOneClient` sin solicitudes). `ArchitectureRulesTest` (+2: Gson solo en `adapter.jev`; el puerto `DecisionClient` no alcanza `adapter.jev` ni `java.net.http`, y `adapter.jev` no alcanza otro adapter, `config` ni Bukkit).

### Comparación con la OpenAPI 0.2.0

La OpenAPI pública de TypeSafe (`openapi.json`, versión `0.2.0`, descargada sin clave por el orquestador y leída desde el scratchpad) se comparó con el código, los fixtures y los golden. `ContractFixturesTest` fija la comparación de los fixtures.

| Contrato | OpenAPI 0.2.0 | Implementación | Resultado |
|---|---|---|---|
| Autenticación | `HTTPBearer` | `Authorization: Bearer <clave>` | Coincide |
| `SystemOneRequest` | `model`, `state` y `questions` obligatorios; `state` texto, objeto o lista; `questions` con al menos una | `{"model", "state": {objeto}, "questions": {"q0": …}}` | Coincide |
| `ChoiceQuestion` | `type` = `choice` y `criteria` (objeto) obligatorios; `instructions` opcional (texto, objeto, lista o nulo); valores de `criteria` texto, objeto, lista o nulo | `type`, `instructions` (texto) y `criteria` con valores de texto | Coincide |
| Límite de 2 a 255 opciones | No figura en la OpenAPI | Se mantiene (documentación de modelos y plan §6) | Diferencia documentada |
| Otras preguntas | `noul` y `score` | No se usan | Sin efecto |
| `SystemOneResponse` | `model`, `answers` (al menos una) y `usage` obligatorios | Los tres obligatorios | Coincide |
| `ChoiceAnswer` | `type`, `choice`, `confidence` y `probabilities` obligatorios; "suman aproximadamente 1" | Los cuatro obligatorios; suma 1 ± 0,02 | Coincide; tolerancia verificado con la cuenta real 2026-09-29 (1 200 respuestas del informe) |
| `Usage` | `input_tokens` y `output_tokens` obligatorios (enteros) | Solo se exige `input_tokens`; `output_tokens` se ignora y su ausencia se tolera | Diferencia tolerante documentada (salida gratuita, plan §6) |
| `ModelMetadataList` | `models[]` con `name`, `description` y `release_date` obligatorios | Se lee solo `models[].name`; el resto se tolera, falte o sobre | Coincide; `models.json` sigue ahora el esquema exacto |
| Errores | Solo 422 `HTTPValidationError`: `detail[]` con `loc` (texto o entero), `msg` y `type` obligatorios; `input` y `ctx` opcionales | Se leen solo partes de `loc`; `MODEL_UNAVAILABLE` solo con `loc` = `["body", "model"]` | Coincide |
| 401, 403, 429 (`retry-after`) y 529 | No figuran en la OpenAPI | Clasificados según la documentación y el plan §6 | Confirmación real pendiente |

### Desviaciones y decisiones

| # | Desviación o decisión | Motivo |
|---|---|---|
| 1 | Se crea aquí `engine.DecisionClient` con `unavailable(ServiceCheck)`; la implementación nula (`UnavailableDecisionClient`) falla `decide` al instante con `AUTHENTICATION` (`UNCONFIGURED`, `INVALID_CREDENTIALS`), `MODEL_UNAVAILABLE` (`INVALID_MODEL`), `INVALID_REQUEST` (`INVALID_CONFIGURATION`) o `SERVICE_ERROR` (resto) | El puerto de 00 §4 no existía (como `MobControl` en M05 y `CombatReader` en M07); A7 exige una causa enumerada |
| 2 | `SystemOneSettings` tiene un octavo componente `pluginVersion` para `User-Agent: ThePredict/<versión>`, valida que modelo, clave y versión sean texto imprimible sin espacios (sin citar el valor), ofrece `endpoint(path)` y su `toString()` oculta, además de la clave, la ruta de `baseUrl` y un modelo que no sea `jev-`/`kev-` versionado | El diseño pide la cabecera con la versión y la firma no la incluía; una clave con salto de línea rompería la cabecera; mismo criterio W6 que `JevSettings.modelLabel()` |
| 3 | **Cuerpo de respuesta:** `BoundedBody` es un `BodySubscriber` no bloqueante (`BoundedBody.handler(max)`) en lugar de `BodyHandlers.ofInputStream()` más una lectura bloqueante. Rechaza un `Content-Length` mayor que el límite antes de pedir bytes y cancela la suscripción al cruzar el límite. Para un código distinto de 200 un cuerpo mayor se descarta como vacío para que el código se siga clasificando | Una lectura bloqueante ocuparía un hilo durante todo un cuerpo detenido: con 2 hilos acotados para admisión, codificación y parseo, dos cuerpos detenidos pararían el cliente hasta su plazo. Con el suscriptor ningún hilo espera y cancelar la future del transporte detiene el intercambio. Se cumple igual "sin cargarlo entero en memoria" (nunca más de `maxResponseBytes`) |
| 4 | Constructores: además de `SystemOneClient(settings, clock)`, uno público con `Consumer<String> diagnostics` (líneas de tiempos, solo si la raíz lo activa con `decision.logDecisions`) y uno de paquete con `java.time.Clock` y la función codificadora, para las pruebas. `TimingLog` escribe como mucho una línea por minuto con `ServerClock.nanoTime()` | `adapter.jev` no puede usar el logger de Bukkit (D4); inyectar la codificación permite comprobar el hilo del codificador (M08.1) |
| 5 | Tras `close()`, `check()` devuelve `ServiceCheck(PAUSED, "client closed")`; `decide` devuelve `Failed(NETWORK, "client closed")`. `close()` completa así toda future pendiente, llama a `shutdownNow()` del executor y del `HttpClient` y espera como máximo 500 ms a cada uno | `check()` devuelve `ServiceCheck`, no `DecisionResult`; la espera acotada garantiza "cero hilos vivos" al deshabilitar el plugin (T7) sin bloquear indefinidamente |
| 6 | Cola del executor acotado llena → `Failed(NETWORK, "client queue is full")` al instante, o `PAUSED` con el mismo detalle en `check()`, sin tocar el `HttpClient`, que usa el executor propio del JDK. El parseo de una respuesta recibida corre, solo si la cola está llena, en el hilo que completa la future del JDK, que es el `ForkJoinPool` común de la JVM (el JDK completa `sendAsync` allí); es trabajo corto y acotado por `maxResponseBytes`. `SystemOneExecutor` ya no se entrega al `HttpClient` | La cola de 64 tareas es un tope de admisión (A5); `FailureKind` no tiene causa local propia. Entregarlo al `HttpClient` lo mataba de forma permanente tras un rechazo (auditoría). Una respuesta ya pagada no se descarta por falta de cola. M09 limita las solicitudes en vuelo muy por debajo |
| 7 | Estructura de `state`: `encounter` (`duration`, `headcount`), `mobs` (`ref`, `objective` solo en mobs con pregunta según `request.questions()`, no según `profiles`, `facts`, `target` con `ref` y `facts`), `players` (`ref`, `facts`, `cues`) y `recent_events`, con claves en orden fijo y conjuntos ordenados por el orden del enum | Determinismo byte a byte con `Set.copyOf` del dominio; el objetivo del autor solo existe en los perfiles de mobs preguntados (`DecisionRequest.profiles`) |
| 8 | Las frases llevan sujeto: con `"target"` dan la redacción del plan (`target is inside melee reach`, `target is raising a shield but not blocking yet`, `target is backing away`); en `players` el sujeto es el alias (`player_1 is blocking`) y en `mobs` la referencia (`mob_1 is standing in water`). Cantidades en palabras (`two players and one mob are involved`), rango de amenaza en palabras (`highest`, `second`, `third`, `a lesser threat`) y duración en bandas (<10 s, <45 s, <90 s, <180 s, más) | Un jugador que no es objetivo de nadie no debe llamarse `target`; Jev lee literal y es débil con números y conteos (plan §6) |
| 9 | Recorte: (1) eventos más antiguos; (2) `cues` de jugadores que no son objetivo de un mob preguntado, del de menor amenaza hacia arriba; (3) entradas de esos jugadores, del de menor amenaza hacia arriba; (4) mobs sin pregunta, del último hacia atrás. Si aún no cabe → `SystemOneRequestException` → `Failed(INVALID_REQUEST)` sin enviar. `maxStateChars` mide el JSON compacto de `state` | El documento fija los tres primeros pasos y que nunca se recorta un mob con pregunta; el paso 4 es lo único que queda por recortar antes de rechazar. Se protegen también los objetivos de los mobs preguntados porque sus preguntas dependen de ellos |
| 10 | Mob de un evento fuera de `facts.mobs()` → `another mob`; evento sin mob → `a mob`; evento sin participante → `a player`. Un alias de evento que no está en `participants` se escribe con su alias (heredado de M07) | Nunca UUID en `state` (I6); el alias sigue siendo estable en el encuentro |
| 11 | `QuestionBuilder.build(Situation, List<TacticId>, perfil, ref)` además de `build(QuestionSpec, …)`: revalida 2–255 opciones, opciones repetidas y opciones no declaradas. Criterio = `<frase del Movement>; uses a skill that <valores>; <Description>`. Los textos de `PredictValue` son propios de `SemanticPhrases` y no `PredictValue.description()` | `QuestionSpec` ya impide 1 y 256 opciones, así que la comprobación del límite del protocolo solo se puede probar con la sobrecarga; M02 reservó a M08 sus propios textos en inglés (W5) |
| 12 | `FailureClassifier` es una instancia con `java.time.Clock` y `fromStatus(int, HttpHeaders, String body)` en lugar de `Optional<String> validationBody` | C4 prohíbe `Optional` como parámetro; el reloj de pared solo sirve para una fecha HTTP en `retry-after` (excepción a C6 ya prevista) |
| 13 | 422: se leen como mucho 6 partes de cada `loc` que cumplan `[A-Za-z0-9_]{1,40}`; nunca `msg` ni `input`. Detalles fijos: `HTTP 401: the service rejected the credentials`, `HTTP 422: the service rejected the model`, `HTTP 422: the service rejected the request at body.questions.q0.criteria`, `HTTP 429: rate limited`, `HTTP 529: service overloaded`, `HTTP 5xx: service error`, `HTTP <otro>: unexpected status`, `network failure (<clase>)`. `retry-after` negativo → 0 s | "Truncado y saneado": ningún texto libre del servicio llega a registros ni al estado |
| 14 | `ResponseParser` usa un lector Gson en modo `STRICT` (`StrictJson`) en lugar de `JsonParser.parseString`, que es permisivo. `NaN` e `Infinity` literales, comentarios y contenido sobrante son JSON inválido; `1e400` se detecta como no finito; `usage.input_tokens` debe ser un entero exacto de 0 a `Integer.MAX_VALUE`; `choice` debe ser una opción enviada | "Parser estricto" de M08.4 |
| 15 | `check()`: un 200 con un catálogo ilegible → `PAUSED` (`invalid model list: …`); cualquier código distinto de 200, 401 y 403 → `PAUSED`. El detalle de un modelo listado es `model listed; this check does not prove inference or combat quality` | El documento no fija esos casos; el detalle dice que la comprobación no demuestra calidad (M08.6) |
| 16 | `HttpClient` con `followRedirects(NEVER)` | Una redirección podría llevar la cabecera `Authorization` a otro host |
| 17 | Tipos no previstos: `AbortableExchange`, `PendingCalls`, `RequestSizeGuard`, `StateDraft`, `StrictJson`, `SystemOneExecutor`, `TimingLog`, `SystemOneRequestException` (paquete) y `SystemOneWiring` (raíz) | C10 y D10. `SystemOneClient` tiene 192 líneas tras sacar `PendingCalls`; ningún método pasa de 30 líneas |
| 18 | **Límite de tokens por solicitud:** `RequestSizeGuard` estima tokens como caracteres ÷ 1,5 (redondeo hacia arriba; hasta M13, ÷ 2) y rechaza con `Failed(INVALID_REQUEST)`, sin enviar, si `state` más la pregunta más larga pasa de 32 000 tokens o la solicitud completa de 64 000 | El plan §6 fija 64k por solicitud y 32k para `state` más la pregunta más larga. M08.2 solo daba una equivalencia media (4 caracteres por token) y M08 usó 2 por prudencia. **Calibrado en M13 con la cuenta real (2026-09-29):** 1 200 decisiones de `jev-1.13.0` dieron 1,59 / 1,73 / 1,82 caracteres por token (mínimo, media, máximo), así que 2 subestimaba en torno a un 15 %; 1,5 queda por debajo del mínimo observado y nunca subestima con texto en inglés; objetivos en otras escrituras (por ejemplo CJK) pueden tener menos caracteres por token, y M14 lo documenta. `jev.maxStateChars` baja su máximo a 45 000 para dejar sitio a la pregunta más larga bajo los 48 000 caracteres de la guarda (M03) ([informe](evidencia/live-scenario-bench-2026-09-29.md)) |
| 19 | `SemanticEncoder.encodeState(facts, profiles, askedMobs)` recibe los mobs preguntados, que `RequestEncoder` saca de `request.questions()` | Un perfil de `profiles` sin pregunta no debe filtrar su `objective` ni proteger su objetivo del recorte (auditoría) |
| 20 | `SystemOneSettings` rechaza también `http` fuera de loopback, información de usuario, consulta y fragmento en `baseUrl`, y su `toString()` muestra solo esquema, host y puerto | Defensa en profundidad además de `JevSettings` (auditoría) |

### Correcciones tras la auditoría (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **930 pruebas, 0 fallos** (23 nuevas en esta ronda). Ninguna llamada real; solo servidores de bucle local.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** el executor acotado 2/64 se entregaba también al `HttpClient`; una tarea rechazada cierra el selector del JDK y el cliente quedaba muerto (`NETWORK` y `PAUSED` hasta reiniciar) | El `HttpClient` usa su executor propio del JDK; el acotado queda para admisión, codificación y parseo; un parseo rechazado corre en el hilo del JDK; `RejectedExecutionException` → `client queue is full` (desviación 6, diseño de M08.1) | `SystemOneClientTest.aFullQueueFailsAtOnceWithoutHarmingTheHttpClientAndTheClientRecovers` (66 solicitudes admitidas, la 67ª y un `check` rechazados al instante, después todas responden, una nueva decisión funciona y `check` da `READY`); `FailureClassifierTest.rejectedWorkIsTheFullQueue` |
| **Bloqueante:** `models.json` no seguía `ModelMetadata` (faltaba `release_date`, sobraba `context_window`) | Fixture con `name`, `description` y `release_date` exactos; tolerancia probada aparte | `ModelCatalogCheckTest.theFixtureFollowsModelMetadataListExactly`, `extraOrMissingMetadataBesidesTheNameIsTolerated`, `anotherShapeIsRejected`; `ContractFixturesTest` (3: respuesta, errores 422 y golden de la solicitud frente a la OpenAPI) |
| **Bloqueante:** faltaba la comparación con la OpenAPI | Sección "Comparación con la OpenAPI 0.2.0"; casillas de `ChoiceQuestion`, de M08.1–M08.9 y de diferencias marcadas | — |
| Una future vencida o cancelada podía codificarse y enviarse igualmente | `isDone()` antes de codificar y antes de `sendAsync`; costura de paquete `AbortableExchange.Transport` (por defecto `HttpClient::sendAsync`) para contar los envíos en la prueba | `aRequestCancelledWhileQueuedIsNeverEncodedNorSent`, `aRequestCancelledWhileEncodingIsNeverSent` (el transporte solo recibe la segunda solicitud) |
| `objective` de un perfil sin pregunta en `state` | Mobs preguntados según `request.questions()` (desviación 19) | `SemanticEncoderTest.onlyMobsWithAQuestionCarryTheirObjectiveAndProtectTheirTarget`, `RequestEncoderTest.aProfileWithoutAQuestionDoesNotLeakItsObjective` |
| `MODEL_UNAVAILABLE` con cualquier `loc` que contuviera `model`; detalle `… request at ` vacío si se filtraban todas las partes | Solo `loc` = `["body", "model"]`; se usa el primer `loc` con partes válidas o ninguno | `onlyTheExactModelLocationIsModelUnavailable`, `aLocationWithoutPlainFieldNamesLeavesNoDanglingAt` |
| Sin guarda del tamaño total de la solicitud | `RequestSizeGuard` (desviación 18) | `RequestEncoderTest.theTokenGuardAcceptsTheExactLimitsAndRejectsOneCharacterMore`, `aStatePlusQuestionOverTheEstimatedLimitIsNeverSent`, `aRequestOverTheEstimatedTotalLimitIsNeverSent` |
| `SystemOneSettings` aceptaba `http` remoto e información de usuario | Rechazo y `toString()` con host y puerto (desviación 20) | `SystemOneSettingsTest.plainHttpIsOnlyForLoopbackAndUserInformationIsRejected` |
| `StrictJson` aceptaba claves repetidas (Gson conserva la última); UTF-8 inválido se reemplazaba; `Content-Length` mal formado acababa en `NETWORK` | Lector de árbol propio que rechaza claves repetidas y anidamiento de más de 64 niveles; UTF-8 con `REPORT` (`response body is not valid UTF-8`); `Content-Length` no numérico → `response has an invalid Content-Length` (`INVALID_RESPONSE`); en respuestas de error ambos casos dejan el cuerpo vacío | `ResponseParserTest.repeatedKeysAndDeepNestingAreInvalid`; `BoundedBodyTest` (3 nuevas) |
| `Usage.output_tokens` es obligatorio en la OpenAPI | Fixtures y cuerpos de prueba lo incluyen; su ausencia se sigue tolerando | `ResponseParserTest.theSpecRequiresOutputTokensButItsAbsenceIsTolerated` |
| Hilos comprobados con `isAlive` justo después de cerrar; plazos de 150 ms ajustados | `join` con plazo antes de `isAlive`; plazos de 400 ms en las pruebas de respuesta detenida (la espera real la acota `get(10 s)`) | `closingWhileARequestIsInFlightCompletesItAndStopsTheExecutor` y pruebas de timeout |
| Future completada `static final` en `SystemOneClient` (C11 estricto) | Se crea una future completada local en cada omisión | Suite completa |
| Frase repetida `mob_1 has no line of sight to its target` junto a `no line of sight to target` | La señal del mob se omite cuando la relación con el objetivo ya dice que no lo ve; golden `encounter-shield.json` actualizado | `SemanticEncoderTest.aHiddenTargetIsSaidOnceNotTwice` |

### Verificación con la cuenta real (llamadas de pago aprobadas por el usuario; las ejecutó el orquestador en M13)

Evidencia: [informe](evidencia/live-check-2026-09-29.md) de `GET /v1/models` y [informe](evidencia/live-scenario-bench-2026-09-29.md) de 1 200 decisiones, más los cuerpos de error de 401 y de modelo inexistente, que M13 guardó como fixtures. Ningún agente leyó `.env`.

- [x] **Primera inferencia real** (verificado con la cuenta real 2026-09-29): TypeSafe acepta la forma de la solicitud (`state` como objeto, `criteria` como mapa) y `ResponseParser` analiza todas las respuestas: 1 200 de 1 200 respondidas sin fallos, modelo efectivo `jev-1.13.0` en todas, `usage.input_tokens` presente (735 800 en total). Latencia desde la máquina de desarrollo: p50 179 ms, p95 228 ms, p99 328 ms, máximo 513 ms.
- [x] **Tolerancia de la suma de `probabilities`** (verificado con la cuenta real 2026-09-29): las 1 200 respuestas del informe pasaron el control de 1 ± 0,02.
- [x] **Modelo efectivo** (verificado con la cuenta real 2026-09-29): `jev-1.13.0` configurado y efectivo en todas las respuestas; ningún `MODEL_CHANGED`.
- [x] **`GET /v1/models` con la clave real** (verificado con la cuenta real 2026-09-29): 20 de 20 `READY` con el detalle "credential valid; model unverified until first inference"; p50 141 ms, p95 179 ms, máximo 534 ms.
- [x] **Modelo inexistente en `POST /v1/systemone`** (verificado con la cuenta real 2026-09-29): HTTP **400**, no 422 ni 404, con `{"detail":{"error_type":"api_usage_error","message":"Unknown model: …"}}`. Hasta M13 caía en `SERVICE_ERROR` y el servicio se pausaba y reintentaba sin fin; ahora es `MODEL_UNAVAILABLE` (`INVALID_MODEL`, bloqueado) con un detalle fijo que no repite el modelo, y cualquier otro 400 es `INVALID_REQUEST` (tabla de M08.5; fixture `error-400-unknown-model.json`).
- [x] **401 con una clave inválida** (verificado con la cuenta real 2026-09-29): `{"detail":{"error_type":"authentication_error","message":"Cannot authenticate with the server. Please check your API key and try again."}}`; se clasifica por el estado y el análisis del cuerpo nunca falla con ese `detail` en forma de objeto (fixture `error-401.json`).
- [ ] **Uso real de 403**: no se observó.
- [ ] **429 y 529 con `retry-after`**: no ocurrieron en ninguna llamada de esta verificación; se registran si aparecen.
- [x] **Relación de caracteres por token** (verificado con la cuenta real 2026-09-29): 1,59 / 1,73 / 1,82 (mínimo, media y máximo); `RequestSizeGuard` pasa a 1,5 (desviación 18).
- [ ] **Latencia desde el host del servidor y tamaño medio de `state` en combate real**: guion 0.0.1, §6 (servidor).

### Pendiente para módulos posteriores

- M09.7 (hecho): crear el cliente con `SystemOneWiring.create(jev, System::getenv, versión, clock, sink)` una sola vez por proceso; pasar un sink con límite solo si `decision.logDecisions`; llamar a `check()` al arrancar (`CHECKING`) y cerrar el cliente el último en `onDisable` (T7). Los callbacks de las futures solo depositan en `Inbox` (T5).
- M09 (hecho): tratar `Failed(NETWORK, "client queue is full")` como fallo local y mantener las solicitudes en vuelo muy por debajo de la cola de 64 tareas.
- M10.1 (hecho en M10): descartar `Failed(NETWORK, "cancelled")` sin contar fallo del servicio (el adapter lo produce solo si el transporte se cancela antes de completar la future pública); comparar `effectiveModel` con el configurado.
- M09/M13: `combatSeconds` fuera de combate es la duración del último combate (M07) y se escribe igual (`the fight has lasted …`); M09 solo decide durante el combate. M13 ajusta las bandas de duración, `maxStateChars` y `requestTimeout` con medidas reales.
- M12: retirar el camino OpenRouter y `ProviderSettings` (W5); presentar el estado del servicio. **Hecho en M12** (ver su [estado de implementación](M12-Diagnostico-instalacion-retiro-OpenRouter.md#estado-de-implementación)).
