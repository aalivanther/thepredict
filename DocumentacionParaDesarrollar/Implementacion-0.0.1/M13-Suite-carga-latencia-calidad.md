# M13. Suite integral, carga, latencia y calidad

> **Estado:** Local listo — banco con la cuenta real verificado el 2026-09-29; servidor pendiente (ver [Estado de implementación](#estado-de-implementación)) · **Entrega:** E · **Depende de:** M01 a M12 · **Desbloquea:** M14 · **Diseño:** [plan §12](../Plan-Jev-y-combate-tactico.md#12-matriz-de-pruebas-y-evidencia), [plan §7](../Plan-Jev-y-combate-tactico.md#validación-de-eficiencia-velocidad-concurrencia-e-identificadores) y [plan §13](../Plan-Jev-y-combate-tactico.md#13-decisiones-pendientes-de-evidencia-no-de-preferencias) · Reglas: [00](00-Arquitectura-y-reglas.md) · Dominio: [01](01-Modelo-de-dominio.md)

## Objetivo

Demostrar con medidas que la 0.0.1 cumple su contrato: corrección, aislamiento, límites de carga, latencia y utilidad de las decisiones frente a la IA de MythicMobs. El módulo ajusta los valores por defecto con esa evidencia y publica la capacidad soportada. No se cierra solo con pruebas unitarias (plan §11).

## Alcance

### Incluye

- Correspondencia entre la matriz del plan §12 y las pruebas automáticas y de servidor.
- Guion de servidor y plantilla de evidencia.
- Pruebas de carga con 1, 10 y 30 mobs en varios mundos.
- Medida de latencia hacia TypeSafe y de extremo a extremo por tramo.
- Protocolo A/B frente a la IA de MythicMobs.
- Banco de escenarios grabados para calibrar umbrales por versión de modelo.
- Informe final y actualización de defaults.

### No incluye

- Nuevas funciones para compensar un fallo: se corrige el contrato del módulo afectado y se repite la prueba (plan §11).
- Publicar la versión, que es de M14.
- Subir datos del servidor a servicios externos sin aprobación explícita del administrador.

## Base existente

| Archivo | Acción |
|---|---|
| `DocumentacionParaDesarrollar/Verificacion-servidor-0.0.1.md` | Matriz de aceptación con OpenRouter. Se sustituye por el guion v2 de M13.2 ([Guion-servidor-0.0.1.md](Guion-servidor-0.0.1.md)) y pasa a `archive/` (M12.8, hecho). |
| Suite actual de `src/test` (25 clases) | Se conserva lo que siga vigente tras M02 a M12; M13.1 comprueba que cada fila de la matriz tiene cobertura. |
| Métricas de latencia y presupuesto de M09 | Fuente de los percentiles; M13 no crea métricas nuevas salvo las del banco de escenarios. |
| spark incluido en Paper (`me/lucko/spark-paper` en las bibliotecas del servidor) | Herramienta nativa para tiempo por tick y memoria. |

## Subpuntos

### M13.1 Matriz de pruebas a clases

- **Propósito:** que cada fila de la matriz del plan §12 tenga pruebas identificables.
- **Diseño:**

  | Fila del plan §12 | Automático (paquete y módulo) | Servidor (sección del guion) |
  |---|---|---|
  | Dominio | `domain`: `Eligibility`, `LocalPlanner`, `IntentPolicy`, `ContingencyPolicy`, `FallbackPolicy` (M02, M10) | — |
  | Configuración | `config`: loader, perfiles, migración (M03, M12) | Instalación limpia y config rota |
  | HTTP | `adapter.jev`: `SystemOneClient` contra servidor de contrato (M08) | `check` con credencial real |
  | Concurrencia | `engine`: `Coordinator`, `Inbox`, `RequestBudget`, `SessionRegistry` con `ServerClock` controlable (M06, M09) | Carga de M13.3 |
  | Integración Mythic | `adapter.server.binding` con dobles (M03) | Condiciones, targeters, cooldown, delays, triggers y recarga |
  | Vocabulario Mythic | `adapter.server.vocabulary` con vistas cambiantes (M04) | Primer arranque y `/mm reload` |
  | Encuentros | `engine.EncounterRegistry` (M06) | Dos combates simultáneos |
  | Contexto nativo | `adapter.server.context` con dobles (M07) | Escudo, arco, ballesta, tótem, perla, pilar, invisible |
  | Propiedad de la IA | Generador de metas (M05) | Spawn, carga de chunk, recarga, 30 mobs cargando |
  | Fallback escalonado | `domain.FallbackPolicy`, `engine.Coordinator` (M10) | Red caída, 429, 529, timeout |
  | Movimiento | — | Pasillo, pared, esquina, desnivel, ruta imposible, agua, objetivo que huye |
  | Lifecycle | `engine.SessionRegistry` (M06) | Spawn, muerte, unload, respawn, apagado durante HTTP |
  | Fallos en combate | `engine` con dobles de `DecisionClient` (M09, M10) | Desconectar API durante combate |
  | Carga sostenida | — | M13.3 |
  | Calidad táctica | Banco de escenarios (M13.6) | A/B de M13.5 |

  - Nombres de clases de prueba según P6 (`<Clase>Test`). Los nombres de las clases de adapters se toman del documento de cada módulo.
  - Una fila sin cobertura bloquea el cierre de M13.
  - Clases concretas de cada fila: tabla [Matriz de M13.1 con clases](#matriz-de-m131-con-clases) del estado de implementación; secciones de servidor: [guion, sección 3](Guion-servidor-0.0.1.md#3-matriz-de-m131-filas-y-secciones-de-servidor).
- **Archivos:** tabla anterior mantenida en este documento; clases de prueba en sus módulos.
- **Reglas:** A8, P1–P7.
- **Pruebas:** revisión de la tabla contra `src/test` al cerrar cada entrega intermedia.
- **Aceptación:**
  - [x] Cada fila tiene al menos una prueba automática o una sección del guion. [Matriz con clases](#matriz-de-m131-con-clases) y [guion §3](Guion-servidor-0.0.1.md#3-matriz-de-m131-filas-y-secciones-de-servidor).
  - [x] `./gradlew clean build javadoc` verde con toda la suite. 1208 pruebas, 0 fallos, sin avisos (`--offline`, también con `--rerun-tasks`), tras las correcciones de la auditoría.

### M13.2 Guion de servidor y plantilla de evidencia

- **Propósito:** pruebas en servidor repetibles y evidencia revisable sin secretos.
- **Diseño:**
  - Nuevo guion `Implementacion-0.0.1/Guion-servidor-0.0.1.md`, creado en este módulo, con una sección por fila de M13.1: preparación, pasos, resultado esperado y datos a capturar.
  - Entorno fijo: Java 25, Paper 26.2 build 129, MythicMobs Premium 5.13.1-SNAPSHOT, boss de ejemplo de M12.5 y una arena guardada como copia de mundo para repetir escenarios.
  - Plantilla de evidencia por ejecución, en `DocumentacionParaDesarrollar/Evidencia-0.0.1/<fecha>-<escenario>/`:
    1. Salida de `/version`, `/version MythicMobs` y `/thepredict status`.
    2. `config.yml` y perfiles con `jev.apiKey` vacío o redactado.
    3. Registro del servidor filtrado a líneas de ThePredict, sin nombres de jugadores.
    4. Salidas de `/thepredict inspect` en los momentos clave.
    5. Medidas de tiempo por tick con spark.
    6. Resultado: aprobado, fallido o bloqueado, con la causa.
  - spark: **M13 confirma** qué comandos ofrece la versión incluida en Paper 26.2 (por ejemplo, monitor de ticks y perfilador). Si un informe solo puede publicarse en el visor web público de spark, se pide aprobación al administrador antes de subirlo; la alternativa es copiar las cifras del monitor de ticks al registro.
- **Archivos:** crear `Guion-servidor-0.0.1.md` y la carpeta de evidencia al ejecutar la primera prueba.
- **Reglas:** C8, W2, W3.
- **Pruebas:** una ejecución piloto del guion completo antes de las pruebas de carga.
- **Aceptación:**
  - [ ] Cada sección del guion produce evidencia con la plantilla. Guion y plantilla creados ([§2](Guion-servidor-0.0.1.md#2-plantilla-de-evidencia-m132)); pendiente de servidor (piloto, §4).
  - [ ] Ninguna evidencia contiene claves, cabeceras ni nombres de jugadores. Comprobaciones de la plantilla escritas; pendiente de evidencia real.

### M13.3 Carga

- **Propósito:** conocer el costo real por tick y el límite soportado del host.
- **Diseño:**
  - Escalones: 1, 10 y 30 mobs vinculados, repartidos entre dos mundos, con al menos dos jugadores de prueba por mundo. Cada escalón dura 10 minutos de combate continuo.
  - Medidas por escalón:

    | Medida | Fuente |
    |---|---|
    | Tiempo por tick del servidor (media, p95, máximo) | spark |
    | Tiempo por tick del coordinador de ThePredict (p50, p95, p99) | Métricas de M09 |
    | Instalaciones de metas por tick y picos al cargar chunks | Métricas de M05 y M09 |
    | Solicitudes por minuto frente a `maxRequestsPerMinute` | `status` |
    | Tokens por minuto y costo estimado | `status` |
    | Tamaño de `Inbox`, `ContextSignals` e historial de encuentros | `status` o métricas de M09 |
    | Memoria de montículo antes y después | spark |

  - Condiciones obligatorias (plan §12): cero accesos al mundo desde callbacks, cero casts duplicados por respuesta, colas y memoria acotadas, ningún envío con Predictor apagado y fallback comprobado.
  - Objetivo inicial propuesto: el coordinador no supera 1 ms de p99 por tick con 30 mobs, un 2 % del presupuesto de 50 ms. **M13 mide** y el informe publica el valor real.
  - Un escalón que supera los límites se repite tras corregir el módulo responsable.
- **Archivos:** sección de carga del guion y evidencia.
- **Reglas:** A5, T1, C12.
- **Pruebas:** los tres escalones más una carga masiva de chunk con 30 mobs vinculados.
- **Aceptación:**
  - [ ] Tabla de medidas completa para los tres escalones. Pendiente de servidor ([guion §5.2](Guion-servidor-0.0.1.md#52-escalones-de-1-10-y-30-mobs)); la tabla local con dobles está en [Medidas locales](#medidas-locales).
  - [ ] Ninguna cola ni mapa crece sin límite durante los 10 minutos. Local: `CoordinatorLoadTest` (1, 10 y 30 mobs, 10 minutos simulados, todos los topes); en servidor, pendiente.
  - [ ] Capacidad soportada del host publicada: mobs, solicitudes por minuto y tiempo añadido por tick. Pendiente de servidor (guion §5.6); la referencia local es el límite de tokens (unos 30 mobs en combate continuo).

### M13.4 Latencia

- **Propósito:** comprobar los objetivos del plan §7 con datos del host.
- **Diseño:**
  - Red hacia TypeSafe: 50 ejecuciones de `/thepredict check` separadas al menos 10 segundos; tiempo de `GET /v1/models`, que no consume tokens.
  - Inferencia: al menos 200 decisiones reales por escenario; tiempo de `POST /v1/systemone` desde el envío hasta la respuesta.
  - Extremo a extremo por tramo, con las marcas de M09: evento → captura, captura → envío, envío → respuesta, respuesta → aplicación en el hilo del servidor, aplicación → meta de movimiento en curso (observada con `MobGoals`).
  - Contingencias: tiempo desde el cumplimiento de la situación hasta el cambio de meta, sin red.
  - Resultado: p50, p95 y p99 por tramo, comparados con los objetivos del plan §7: 0,25–0,45 s por la ruta de Jev y ≤100 ms por contingencia.
  - Si p99 supera `timeoutMillis` de forma habitual, se ajustan latido, contingencias y `timeoutMillis` en M13.7; no se aumentan consultas sin límite (plan §13).
- **Archivos:** sección de latencia del guion y evidencia.
- **Reglas:** C6 (tiempo monotónico para medir), A5.
- **Pruebas:** las series anteriores en dos horarios distintos del host.
- **Aceptación:**
  - [ ] Percentiles por tramo publicados con número de muestras. Desde la máquina de desarrollo (verificado con la cuenta real 2026-09-29): `GET /v1/models` p50 141 ms, p95 179 ms, máximo 534 ms (20 muestras, [informe de la comprobación](evidencia/live-check-2026-09-29.md)); `POST /v1/systemone` p50 179 ms, p95 228 ms, p99 328 ms, máximo 513 ms (1 200 muestras). Los tramos del host en combate (guion §6.2–6.3) siguen pendientes de servidor.
  - [ ] Decisión documentada sobre `timeoutMillis` y latido con esos datos. Con la cuenta real desde la máquina de desarrollo el p99 es 328 ms, muy por debajo de 800; la regla del guion §6.5 exige los datos del host en dos horarios, pendientes de servidor.

### M13.5 Protocolo A/B frente a la IA de MythicMobs

- **Propósito:** medir si Jev mejora las decisiones frente a la base concreta, sin trampas (plan §12).
- **Diseño:**
  - Brazo A: Predictor apagado. Brazo B: Predictor activo. Mismo boss, estadísticas, skills, cooldowns, arena y escenario.
  - Para una comparación justa, el brazo A usa una variante del boss con las mismas skills disparadas por reglas clásicas de MythicMobs (triggers y condiciones). Sin ella, el brazo A no tendría acceso a las mismas capacidades.
  - Escenarios: duelo cuerpo a cuerpo, jugador que dispara y retrocede, jugador con escudo, mob con salud baja, dos jugadores (uno tanquea y otro dispara) y objetivo sobre un pilar.
  - Al menos 10 repeticiones por brazo y escenario, alternando el orden de los brazos.
  - Medidas: tiempo en alcance útil, golpes evitados durante invulnerabilidad (`DEFERRED_INVULNERABLE`), ataques evitados contra escudo efectivo (`SKIPPED_SHIELD`), cambios de posición, tiempo de supervivencia del mob, daño hecho y recibido, solicitudes, tokens y costo.
  - Un aumento de daño por duplicación no cuenta como mejora. No se afirma superioridad frente a cualquier IA manual (plan §12).
- **Archivos:** variante clásica del boss en la carpeta de pruebas del servidor; sección A/B del guion; evidencia.
- **Reglas:** A7, W3.
- **Pruebas:** las repeticiones descritas, con resultados por escenario y por brazo.
- **Aceptación:**
  - [ ] Resultados A/B por escenario con dispersión, no solo medias. Pendiente de servidor (guion §7); el arnés local `TacticalAB` (con y sin Jev guionizado) publica media, desviación, mínimo y máximo, pero no sustituye al A/B frente a MythicMobs.
  - [ ] Conclusiones limitadas a la base medida. Pendiente con los resultados del servidor.

### M13.6 Banco de escenarios y calibración

- **Propósito:** calibrar `minimumConfidence`, `tacticSwitchMargin` y el uso de contingencias por versión de modelo, con datos repetibles.
- **Diseño:**
  - Grabación: durante sesiones de prueba, con una opción de depuración explícita, se guardan la `DecisionRequest` y los `CombatFacts` de cada decisión. Solo contienen alias (I6). Se escriben como JSON en `plugins/ThePredict/scenarios/`. **Incorporado en M03 (revisión 2):** una clave de depuración desactivada por defecto que habilite la grabación.
  - Etiquetado: cada escenario recibe a mano un conjunto de tácticas aceptables y, si aplica, una táctica prohibida.
  - Ejecución: una tarea de Gradle aparte, excluida del build normal, que exige `TYPESAFE_API_KEY` en el entorno y una propiedad explícita. Consume API: con unos 1 000 tokens por solicitud, 500 escenarios cuestan unos 0,02 USD con el precio del plan §6.
  - Resultados por versión de modelo (`effectiveModel`): tasa de elección aceptable, curva de fiabilidad de la confianza por tramos, efecto de distintos márgenes de cambio en la oscilación simulada y utilidad de cada contingencia.
  - Una versión nueva del modelo exige repetir el banco antes de cambiar el ID fijado (plan §6).
- **Archivos:** tarea de Gradle en `build.gradle.kts` (excluida de `check`), serializador de escenarios en `adapter.jev` (ya dispone de Gson) y resultados en la evidencia. (Implementado: `adapter/jev/ScenarioRecorder` y `ScenarioSerializer`, conectados en la raíz con `debug.recordScenarios`; tareas `liveScenarioBench` y `liveJevCheck`; banco semilla en `src/test/resources/scenarios/seed/`. El archivo guarda el cuerpo exacto de la solicitud, que ya se escribió desde `DecisionRequest` y `CombatFacts` solo con alias, y no esos records, que llevan UUID de mobs: desviación 3.)
- **Reglas:** A7, C11, W3; el build normal nunca consume API.
- **Pruebas:** la tarea falla sin clave o sin la propiedad explícita; el build normal no la ejecuta.
- **Aceptación:**
  - [ ] Umbrales elegidos con la curva de fiabilidad del modelo fijado. Con la cuenta real (2026-09-29, [informe del banco](evidencia/live-scenario-bench-2026-09-29.md)) 0,5 y 0,15 quedan respaldados de forma preliminar: las etiquetas son las propuestas por M13 (`labelsProposedBy`) y el usuario no las ha revisado; la muestra efectiva son 6 escenarios de una sola pregunta repetidos 200 veces; la curva no es monótona (0,0–0,3: 69 % aceptable; 0,3–0,5: 5,8 %) y no hubo contingencias. Siguen abiertos hasta que el usuario revise las etiquetas y haya escenarios más variados (DT-31). A 0,5, el 66,7 % de las respuestas pagadas de este banco se descartan por confianza baja (`LOW_CONFIDENCE`): la mayor parte del gasto compra respuestas que no se usan. **Punto clave para que el usuario lo revise**; opciones para M14 o la 0.0.2: mejorar la redacción de las preguntas y las etiquetas, o un `minimumConfidence` por perfil.
  - [x] Informe del banco guardado junto al ID de modelo (verificado con la cuenta real 2026-09-29): [informe del banco](evidencia/live-scenario-bench-2026-09-29.md), modelo configurado y efectivo `jev-1.13.0`.

### M13.7 Informe final y defaults

- **Propósito:** cerrar la evidencia y trasladarla a la configuración y a las guías.
- **Diseño:**
  - Plantilla del informe: entorno y versiones, cobertura de la matriz, carga, latencia, A/B, calibración, fallos pendientes y decisiones.
  - Defaults a revisar con la evidencia: `timeoutMillis`, `encounterHeartbeatTicks`, `minimumDecisionIntervalTicks`, `maximumIntentDurationTicks`, `maxRequestsPerMinute`, `maxInFlightRequests`, `contingenciesPerMob`, `minimumConfidence`, `tacticSwitchMargin` y pesos de amenaza.
  - Cada default cambia en un solo lugar (C13) y en la guía del administrador (M14).
  - Capacidad soportada publicada con el host de referencia.
- **Archivos:** `Evidencia-0.0.1/Informe-final.md`; recursos de configuración por defecto; guías de M14.
- **Reglas:** C13, W1, W3.
- **Pruebas:** la suite completa tras actualizar defaults.
- **Aceptación:**
  - [ ] Informe con todas las secciones y sin afirmaciones sin evidencia. Plantilla en el guion §10; pendiente de evidencia.
  - [ ] Defaults y guías coinciden. Ningún default cambia en M13 (ver [Calibración](#calibración)); las guías son de M14.

## Riesgos y decisiones abiertas

- Jugadores de prueba: la carga necesita varios jugadores reales o de prueba por mundo; ThePredict solo elige jugadores como objetivo.
- Los informes web de spark pueden publicar datos del servidor; se pide aprobación antes de subirlos.
- **Incorporado en M03 (revisión 2):** clave de depuración para grabar escenarios.
- Los límites de TypeSafe cambian sin aviso; la carga se repite si cambian (plan §6).
- **Pendiente de M06:** las sesiones `SUSPENDED` no tienen red de seguridad: si un mob se borra mientras su chunk está descargado (mundo eliminado o regiones editadas), su sesión suspendida queda en memoria hasta el siguiente arranque. M13 mide cuántas sesiones suspendidas se acumulan en un servidor de larga duración y, si crecen, propone un plazo configurable de cierre de sesiones suspendidas (DT-20). **Local (M13):** probado que persisten (`SessionReconcilerScaleTest`); el ritmo de acumulación es de servidor (guion §5.5).
- **Pendiente de M06 (coste de `assign`):** con mobs dispersos, un jugador puede tener k encuentros abiertos en un mundo y `EncounterRegistry.assign` evalúa hasta k·16 predicados de alcance al cambiar de objetivo. Medir k y el tiempo por `assign` con 200 mobs dispersos contra 1 y 10 jugadores; si supera el presupuesto por tick, limitar los candidatos evaluados. **Medido en M13:** k ≤ 14, como mucho 28 predicados y 0,2–1,2 us por llamada; no se limita ([Medidas locales](#medidas-locales)).
- **Pendiente de M09 (presupuesto de trabajo por tick):** `maxTickWorkMicros` se comprueba antes de cada instalación y de cada evaluación, no durante una captura; una captura cara puede pasar el tope en un tick y dejar para el siguiente las evaluaciones restantes. Medir `TICK_WORK` y `DEFERRED_TICKS` con 16 jugadores y 16 mobs por encuentro, y si un mundo o un encuentro queda relegado de forma sostenida (inanición), bajar `maxEncounterEvaluationsPerTick` o dividir la captura. **M13:** la simulación encontró inanición de un mundo entero y se corrigió en `Coordinator` (desviación 2); el coste real de la captura es de servidor (guion §5).
- **Pendiente de M09 (ventana efectiva):** las ventanas de `RequestBudget` son 60 cubetas de un segundo entero, así que una solicitud deja de contar entre 59 y 60 s después de enviarse según la fase del segundo. Comprobar con la cuenta real que el margen frente al límite publicado (600 de 1 200 por minuto) cubre esa diferencia. **Medido en M13:** en cualquier minuto real el presupuesto admite como mucho 1 200 (el doble del límite, exactamente el publicado) con un emisor voraz y 601 con uno constante; queda por confirmar con la cuenta cómo cuenta TypeSafe su ventana.

## Terminado

- Todas las filas de la matriz cubiertas y ejecutadas.
- Informe final con carga, latencia, A/B y calibración.
- Defaults y capacidad publicados.
- Regla W4 de 00 cumplida.

## Estado de implementación

> **Local listo — banco con la cuenta real verificado; servidor pendiente (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` (también con `--rerun-tasks`) termina en `BUILD SUCCESSFUL` sin avisos de javac ni de Javadoc: **1208 pruebas en 178 clases, 0 fallos** (1128 de partida + 80: 70 en 13 clases nuevas, 2 más en `CastSemanticsTest`, 1 más en `SystemOneWiringTest` y, con la evidencia de la cuenta real, 3 más en `FailureClassifierTest`, 1 en `SystemOneClientTest`, 2 en `RequestEncoderTest`, 1 en `ModelCatalogCheckTest` y 1 en `JevSettingsTest` sin cambiar su número), con las cuatro rondas de correcciones (tablas al final). Toda la suite de pruebas tarda unos 3,5 s; las pruebas de carga simulan 10 minutos de combate en décimas de segundo, así que van en el build normal, y lo que mide tiempos va aparte (`./gradlew m13Measure`). Ninguna prueba ni herramienta ejecutada salió de la máquina: el único HTTP es el servidor de contrato en `127.0.0.1` con puerto efímero. No hubo ninguna llamada a TypeSafe, nadie leyó `.env` (W6), ninguna prueba nueva usa `Thread.sleep` (P2) y no se arrancó ningún servidor de Minecraft: las casillas que exigen juego o la cuenta real siguen sin marcar y no hay evidencia inventada. 00 no cambia; 01 recibe la revisión 9 (dos campos de `MobSession` para DT-28). El registro de M01 recibe DT-28 (decidida y aplicada; abierta solo por la confirmación en servidor) y DT-29 (`Abierta`); el guion v2 es [Guion-servidor-0.0.1.md](Guion-servidor-0.0.1.md).

### Archivos

| Tipo | Archivos |
|---|---|
| Código (`src/main/java/akyrion/thepredict/…`) | Creados `adapter/jev/ScenarioRecorder` y `ScenarioSerializer` (grabación de M13.6). Modificados `adapter/jev/SystemOneClient` (constructor con grabador; entrega la decisión al grabador después de completar el resultado y lo cierra con el cliente), `SystemOneWiring` (`recorder`, que traduce `debug.recordScenarios`, D10) y `ThePredictPlugin`, `engine/Coordinator` (correcciones 2 y 4), `engine/Agenda` (`hasDue`), `engine/SessionUpkeep` (corrección 1 e `installsPending`), `engine/IntentControl` y `engine/MobSession` (corrección 3, DT-28) |
| Pruebas (`src/test/java/akyrion/thepredict/…`) | Nuevas: `engine/CoordinatorLoadTest`, `CoordinatorWorkBudgetTest`, `EncounterRegistryScaleTest`, `SessionReconcilerScaleTest`, `SessionUpkeepTest`, `ProgressWatchScenarioTest`, `RequestBudgetWindowTest`, `TacticalExecutionABTest`, `ThreatEnrichmentABTest`, `adapter/jev/ScenarioRecorderTest`, `ScenarioSeedBankTest`, `LiveJevGateTest`, `LiveToolsLoopbackTest`. Arneses: `engine/LoadFixture` (motor completo sobre un mundo geométrico doble), `LoadScenarios`, `EncounterScale`, `StuckMobs`, `RequestBudgetWindow`, `TacticalAB`, `ThreatAB`; `adapter/jev/ScenarioFacts`, `ScenarioSeedBank`, `ScenarioDocument`, `BenchStatistics`. Medidas: `M13Measurements`, `MeasurementReport`, `engine/EngineMeasurements`, `EngineScenarioMeasurements`, `adapter/jev/JevMeasurements`. Herramientas de pago: `adapter/jev/LiveJevGate`, `LiveJevCheck`, `LiveScenarioBench`. Adaptados: `ExecutionFixture` (jugadores percibidos sin ser objetivo y contextos activos en el lector doble), `SystemOneWiringTest` |
| Recursos de prueba | `src/test/resources/scenarios/seed/seed-*.json` (seis escenarios semilla con etiquetas propuestas en M13; nota histórica: desde M16 son cinco, porque M16.9 retiró `seed-low-health`, y la calibración usa el banco etiquetado `src/test/resources/scenarios/bank/`) |
| Build | `build.gradle.kts`: tareas `m13Measure`, `liveJevCheck` y `liveScenarioBench` (`JavaExec` sobre el classpath de pruebas, fuera de `check` y de `build`; nada entra en el JAR y `verifyPluginJar` sigue igual) |
| Documentos | Este documento; [README.md](README.md); [Guion-servidor-0.0.1.md](Guion-servidor-0.0.1.md) (nuevo); [guion de M01](M01-Guion-de-servidor.md) (cabecera y referencias de 4.8, 6.3, 9.8 y 11.11 a M13); [registro de M01](M01-Registro-de-decisiones.md) (DT-28, DT-29); notas de corrección en [M06](M06-Sesiones-encuentros-ciclo-de-vida.md) y [M09](M09-Coordinador-presupuesto-velocidad.md); [M10](M10-Ejecucion-tactica-fallback.md) (desviación 28 y nota en M10.2); [01](01-Modelo-de-dominio.md) (revisión 9) |

### Pruebas nuevas (73)

| Clase | Pruebas | Qué demuestra |
|---|---|---|
| `CoordinatorLoadTest` | 7 | Escalones de 1, 10 y 30 mobs en dos mundos con dos jugadores cada uno, 10 minutos simulados: capturas, envíos, sondas e instalaciones por tick dentro de su tope; inbox, en vuelo (servidor y mundo), solicitudes por minuto (servidor y mundo), tokens, encuentros, miembros, participantes, historial, ventanas de amenaza, señales y vistas acotados; cero casts duplicados; un mob con objetivo nunca pasa más de `minimumDecisionIntervalTicks + 2` en nivel 4; todo acceso a los puertos desde el hilo del servidor y las respuestas completadas en otro hilo (T5). Predictor apagado: cero envíos, cero sesiones, vistas y encuentros, y cada meta liberada una vez. 30 mobs cargando a la vez: 10 instalaciones por tick. 6 000 señales: como mucho 1 024 pendientes y 64 por tick. Caída de red bajo carga: cero envíos en pausa, ningún mob en `JEV`, vuelta a `JEV` al recuperarse |
| `CoordinatorWorkBudgetTest` | 7 | Capturas simuladas de 0,5 a 3 ms con 10 a 40 encuentros: ningún encuentro espera más de una vuelta tras su latido y los dos mundos se reparten el presupuesto (regresión de la corrección 2); tres mundos con el presupuesto agotado reciben como mucho 2 capturas de diferencia; un tick sin encuentro vencido ni instalación pendiente nunca cuenta como aplazado (corrección 4); unas sondas que gastan todo el presupuesto dejan sin evaluaciones hasta que se abaratan (hallazgo 3) |
| `EncounterRegistryScaleTest` | 5 | `assign` evalúa como mucho k·16 predicados con 200 mobs contra 1 y 10 jugadores; los encuentros de mobs dispersos nunca bajan del mínimo de las reglas; dos grupos que se encuentran siguen siendo dos encuentros (límite conocido de M06.3) |
| `SessionReconcilerScaleTest` | 4 | 200, 1 000 y 7 000 sesiones: como mucho 32 comprobaciones por tick y un fantasma cerrado en un intervalo más una vuelta; una sesión suspendida cuyo mob desapareció sigue en memoria una hora simulada (DT-20) |
| `SessionUpkeepTest` | 4 | Regresión de la corrección 1: un mob que cambia a un jugador sin encuentro forma un encuentro nuevo una sola vez y se captura en él, cualquiera que sea la paridad del tick |
| `ProgressWatchScenarioTest` | 2 | Mob local atascado: como mucho una caída a nivel 4 cada dos evaluaciones y de un periodo de sonda; con Jev, alternativa y nunca nivel 4 |
| `RequestBudgetWindowTest` | 2 | Ventana efectiva de 59–60 s: un emisor voraz mete 1 200 solicitudes (dos veces el límite) en un minuto real y nunca más; uno constante, el límite más un segundo de su ritmo |
| `TacticalExecutionABTest` | 12 | Arnés A/B local: ambos brazos ven el mismo combate, el brazo sin Jev no cuesta nada, ningún brazo duplica casts, las ejecuciones se repiten con la misma semilla y, con un Jev guionizado siempre aceptable, el motor pierde como mucho una reacción (latido + latencia + sonda) por cambio del combate; un Jev que renueva siempre la misma táctica lanza la skill al ritmo del local con la temporización por defecto (DT-28) |
| `ThreatEnrichmentABTest` | 8 | Comparación de M11.6 sobre una tabla modelo: el brazo sin amenaza enriquecida nunca llega al puerto; el acoso se limita a 20 puntos por ventana; el decaimiento por inalcanzable cede el liderazgo solo con amenaza enriquecida; golpes bloqueados y asistencias suman su peso |
| `ScenarioRecorderTest` | 8 | Un archivo por decisión respondida con el cuerpo exacto, preguntas, respuesta, tokens y etiquetas vacías, sin UUID ni clave; un disco bloqueado no retrasa la respuesta y el archivo llega al desbloquearse; cola llena: la decisión se descarta, se cuenta y se avisa una vez; las decisiones aún en cola al cerrar se descartan y se cuentan; el aviso del límite sale aunque antes fallara una escritura, sin cuerpo; dos arranques en el mismo segundo no se pisan y no queda ningún temporal; ninguno por una respuesta fallida; grabador desactivado |
| `CastSemanticsTest` (+2) | 10 | Una renovación que llega antes del intervalo desde el último cast nunca vuelve a lanzar; una intención renovada recupera su choice una vez por vida de intención local, sin señal ni nuevo `startTick`, y cada cast rearmado se cuenta una vez; ninguna respuesta sin intervalo transcurrido vuelve a lanzar y cada época lanza como mucho una vez; un rechazo de MythicMobs consume la choice en su época y la época siguiente la intenta otra vez (DT-28). `IntentPublicationTest` adapta la prueba de renovación: sin señal y rearme solo por reloj |
| `SystemOneWiringTest` (+1) | 9 | `debug.recordScenarios` se traduce en un grabador de `<datos>/scenarios`, y apagado en el grabador desactivado |
| `ScenarioSeedBankTest` | 6 | Cada semilla es lo que el codificador escribe hoy, sin UUID, y solo etiqueta opciones ofrecidas |
| `LiveJevGateTest` | 3 | Las herramientas de pago se niegan sin `-PliveJev=true` (aunque haya clave) y sin `TYPESAFE_API_KEY` en el entorno, sin mostrar la clave ni sugerir leer `.env` |
| `LiveToolsLoopbackTest` | 2 | `LiveJevCheck` y `LiveScenarioBench` completos contra el servidor de contrato: espaciado, cuerpos reenviados tal cual, todas las secciones del informe y la estimación previa |

### Matriz de M13.1 con clases

| Fila del plan §12 | Pruebas automáticas | Servidor |
|---|---|---|
| Dominio | `domain/EligibilityTest`, `LocalPlannerTest`, `IntentPolicyTest`, `ContingencyPolicyTest`, `FallbackPolicyTest`, `CoordinationRuleTest`, `ThreatPolicyTest` | — |
| Configuración | `config/PluginConfigurationLoaderTest`, `ProfileLoaderTest`, `MigrationPlannerTest`, `ConfigurationRecoverySuiteTest`, `StartupStateTest`, `PluginResourcesTest` | Guion v2 §3 (M01 §1.5 y §9) |
| HTTP | `adapter/jev/SystemOneClientTest`, `ModelCatalogCheckTest`, `FailureClassifierTest`, `ResponseParserTest`, `ContractFixturesTest`, `CoordinatorContractTest`, `ScenarioRecorderTest`, `LiveToolsLoopbackTest` | Guion v2 §6.1 y M01 §12.8 (**PAGO**) |
| Concurrencia | `engine/CoordinatorTest`, `InboxTest`, `RequestBudgetTest`, `RequestBudgetWindowTest`, `SessionRegistryTest`, `CoordinatorLoadTest`, `CoordinatorWorkBudgetTest` | Guion v2 §5 |
| Integración Mythic | `adapter/server/binding/MythicProfileBinderSuiteTest`, `BoundProfileTest`, `adapter/server/execution/CombatActionsAdapterTest` | M01 §3, §4, §8 y §12 |
| Vocabulario Mythic | `adapter/server/vocabulary/VocabularyRegistrarTest`, `PredictorMoveConditionTest`, `PredictorPlaceholdersTest`, `PredictorDecideMechanicTest`, `TacticSignalsTest` | M01 §3 |
| Encuentros | `engine/EncounterRegistryTest`, `EncounterRegistryScaleTest`, `SessionUpkeepTest`, `EncounterCoordinationTest` | M01 §10 y §11.2; guion v2 §5.2 |
| Contexto nativo | `adapter/server/context/CombatCaptureTest`, `TargetCueReaderTest`, `TargetRelationReaderTest`, `PerceptionTest`, `adapter/server/events/CombatContextListenerTest` | M01 §11 |
| Propiedad de la IA | `adapter/server/goals/GoalOwnershipTest`, `GoalSetInstallerTest`, `GoalLineGeneratorTest`, `InstallDecisionTest`; `CoordinatorLoadTest` (30 instalaciones) | M01 §4, §6 y §9; guion v2 §5.3 |
| Fallback escalonado | `domain/FallbackPolicyTest`, `engine/FallbackChainTest`, `CoordinatorLoadTest` (caída bajo carga) | M01 §12.1, §12.6–12.8 |
| Movimiento | `ProgressWatchScenarioTest`, `adapter/server/goals/MovementReadingTest`, `FlankPathsTest` | M01 §5 y §9 |
| Lifecycle | `engine/SessionLifecycleTest`, `SessionReconcilerTest`, `SessionReconcilerScaleTest`, `adapter/server/events/LifecycleListenerTest` | M01 §10 |
| Fallos en combate | `engine/FallbackChainTest`, `CastSemanticsTest`, `DecisionAcceptorTest`, `CoordinatorLoadTest` | M01 §12.8 (**PAGO**); guion v2 §5.2, paso 7 |
| Carga sostenida | `CoordinatorLoadTest`, `CoordinatorWorkBudgetTest` (y `m13Measure`) | Guion v2 §5 |
| Calidad táctica | `TacticalExecutionABTest`, `ThreatEnrichmentABTest`, `HysteresisScenarioTest`, `EnrichedThreatScenarioTest`; banco (`ScenarioSeedBankTest`, `liveScenarioBench`) | Guion v2 §7 y §8 |

### Medidas locales

Máquina: Apple M5 (10 núcleos), 16 GB, macOS 27.0 (26A428); JVM Temurin 25.0.4.1+1 (OpenJDK 64-Bit Server VM), montón de 2 GiB en `m13Measure`. `./gradlew m13Measure --offline` las regenera en `build/reports/m13/measurements.md`; se ejecutó tres veces antes de la auditoría y dos después: los recuentos salen idénticos entre ejecuciones con el mismo código y los tiempos varían (se citan los de la última, después de las correcciones de la auditoría; el rearme de DT-28 cambia el número de casts, no el de solicitudes ni tokens). Los tiempos son de esta máquina con dobles O(1) de los puertos: miden el propio motor y el cliente, nunca el servidor, la red ni el modelo. Las cifras de tokens son la estimación de M09, no `usage.input_tokens`.

**Carga con dobles (10 minutos de combate simulado, dos mundos, dos jugadores por mundo, Jev guionizado a 300 ms, un `predictordecide` por mob y segundo, cambio de objetivo de un quinto de los mobs cada 10 s):**

| Mobs | Solicitudes | Tokens estimados | USD | Pico sol./min | Pico tokens/min | Pico en vuelo | Pico encuentros | Casts duplicados | Nivel 4 con objetivo (tiempo / racha máx.) | us por tick: media; p50/p95/p99/máx. |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 600 | 419 300 | 0,0176 | 60 | 42 000 | 1 | 1 | 0 | 0,22 % / 2 ticks | 4,5; 1,6 / 23,8 / 43,7 / 322,2 |
| 10 | 2 279 | 2 485 740 | 0,1044 | 230 | 249 800 | 4 | 4 | 0 | 0,22 % / 3 | 5,8; 1,3 / 27,2 / 36,4 / 1 490,5 |
| 30 | 2 399 | 5 416 620 | 0,2275 | 240 | 542 400 | 4 | 4 | 0 | 0,22 % / 4 | 6,4; 1,6 / 24,9 / 47,5 / 3 123,7 |
| 100 | 1 730 | 5 992 760 | 0,2517 | 173 | 599 660 | 8 | 8 | 0 | 0,30 % / 11 | 13,0; 8,3 / 32,3 / 40,7 / 2 051,0 |
| 200 | 1 590 | 5 996 400 | 0,2518 | 167 | 600 000 | 8 | 17 | 0 | 0,54 % / 22 | 21,6; 13,8 / 41,7 / 61,0 / 2 987,2 |

Los máximos aislados de 1,5–3,1 ms son pausas de la JVM de esta máquina (aparecen en ejecuciones distintas en escalones distintos). Con los defaults, el límite que manda es `maxInputTokensPerMinute` (600 000): se alcanza con unos 30 mobs en combate continuo (542 400 de pico; **medido antes de M15**: con los criterios de M15.7 cada solicitud crece (1 mob y 1 jugador: 2 253 → 3 755 caracteres; 16 × 16: 23 652 → 47 684), así que el consumo por decisión sube; M16 (`decision.questionDetail`, `compact` por defecto) lo vuelve a medir) y a partir de ahí los mobs sin presupuesto juegan en local (`NO_BUDGET`) sin error. Al precio publicado, el límite de tokens equivale a 1,51 USD por hora de combate continuo.

**Otras medidas:**

| Medida | Resultado |
|---|---|
| Fragmentación, 200 mobs a ≤32 bloques de su jugador, 3 semillas | 1 jugador: 13–14 encuentros frente a un mínimo de 13 (0–1 de más); 10 jugadores: 22–24 frente a 18–19 (3–6 de más). Dos grupos de 5 que se forman lejos y luego se juntan siguen siendo 2 encuentros: 123 solicitudes en 62 s frente a 62 si empiezan juntos |
| Coste de `assign`, 200 mobs, 800 llamadas con cambio de objetivo aleatorio | 1 jugador: k ≤ 14, como mucho 11 predicados, 0,46 de media, 0,25 us por llamada; 10 jugadores: k ≤ 14, como mucho 28 predicados, 9,21 de media, 1,25 us por llamada |
| Reconciliación | 200 / 1 000 / 7 000 sesiones: vueltas de 7 / 32 / 219 ticks, fantasma cerrado en ≤ 207 / 232 / 419 ticks; p99 < 1 us por tick, máximo 239 us en el tick que copia 7 000 UUID |
| Presupuesto de trabajo contra capturas caras (después de la corrección 2) | Capturas de 0,5 y 1,5 ms: 2 por tick, sin ticks aplazados. De 3 ms: 1 por tick y, tras la corrección 4, solo cuentan como aplazados los ticks en que un encuentro esperaba (16 y 36 en 60 s con 10 y 20 encuentros, 1 200 con 40); con 10 y 20 encuentros la mayor espera entre capturas de un encuentro es 22 ticks; con 40 encuentros, 40 ticks (el latido efectivo se dobla); los dos mundos reciben 301/300, 601/600 y 602/600 capturas |
| Presupuesto contra sondas caras (30 mobs, 15 sondas por tick) | Hasta 120 us por sonda las evaluaciones siguen (239–240 capturas por minuto); desde 134 us, ninguna evaluación en todo el minuto |
| Mob local atascado, 1 minuto | Sin Jev: 29 caídas a nivel 4 (2 ticks cada una, 5 % del tiempo), 29 `NO_PROGRESS`, 30 señales de táctica, 60 intentos de cast; con Jev insistiendo en acercarse: 0 caídas a nivel 4, 29 ticks en alternativa, 59 señales |
| Ventana de 59–60 s, `maxRequestsPerMinute: 600`, 5 minutos | Emisor voraz: 3 000 admitidas, como mucho 1 200 en un minuto real; constante a 10, 20 y 40 por segundo: como mucho 600, 601 y 601 |
| Umbrales de pausa (10 000 s, una solicitud por segundo, fallos independientes) | `TIMEOUT` al 1/5/10/20 %: 0,10/2,30/8,11/29,80 pausas por 1 000 solicitudes y 0/0/0,12/1,35 % del tiempo sin enviar; `INVALID_RESPONSE` al 1/5/10/20 %: 0/0/1,20/6,72 pausas por 1 000 y como mucho 0,27 % sin enviar |
| A/B local (30 s, 10 semillas, Jev guionizado aceptable en el 85 % de las respuestas para ahora; media ± desviación del tiempo con movimiento aceptable) | Duelo: sin Jev 0,997 ± 0,000, con Jev 0,861 ± 0,061 [0,777–0,997]. Tirador que retrocede: 0,997 / 0,847 ± 0,075. Escudo: 0,515 ± 0,039 (14,3 `SKIPPED_SHIELD`) / 0,636 ± 0,065 (3,1). Salud baja: 0,977 ± 0,010 / 0,947 ± 0,028. Pilar: 0,000 / 0,839 ± 0,075. Con Jev: 30 solicitudes por combate, unos 21 000 tokens estimados (0,00088 USD). Perfil con la temporización de `config.yml` (intervalo y latido 20, intenciones de 40). Casts en el duelo tras la corrección 3: 19,1 ± 1,2 sin Jev frente a 16,6 ± 0,7 con Jev (17,6 ± 1,3 con un Jev que siempre repite la misma táctica; antes, 1,0). Escudo: 15,7 / 12,2 casts; salud baja: 11,8 / 11,2. Ningún cast duplicado por época |
| Rearme de la choice (DT-28), duelo con un Jev que repite la táctica, intervalo mínimo 20, según `maximumIntentDurationTicks` | 20: 20,8 ± 1,2 casts; 40 (default): 17,6 ± 1,3; 100: 17,6 ± 1,3; el local, 19,1 ± 1,2 en los tres. El rearme usa la vida de intención local, `min(maximumIntentTicks, minimumDecisionIntervalTicks)`, así que los dos brazos lanzan al ritmo que dejan los cooldowns. **Desde el 2026-09-30 el rearme es por disponibilidad (M10, desviación 29)**; los dobles modelan un `Cooldown` nativo de 20 ticks y las cifras no cambian (ver «Disponibilidad en tiempo real» al final) |
| Amenaza enriquecida, tabla modelo (regla del 10 % de DT-27) | Acoso a distancia: sin pesos, el líder cambia 1 vez (tick 150); con los del ejemplo, 3 veces en los primeros 100 ticks y 6 aportes recortados por la ventana (120 puntos en 30 s). Golpes bloqueados: 15 puntos, sin cambio de líder. Asistencias: 22,5 puntos, sin cambio. Pilar: con pesos, 30 pérdidas de 2 puntos y el líder pasa al jugador alcanzable en el tick 55; sin pesos, nunca. Roles mezclados (60 s): 3 cambios en la primera ventana de 5 s con pesos, 0 sin ellos |
| Tamaño de la solicitud con el perfil de ejemplo (5 opciones, `NOW` y una contingencia por mob, 16 eventos; **medido antes de M15**: con los criterios de M15.7 cada solicitud crece (1 mob y 1 jugador: 2 253 → 3 755 caracteres; 16 × 16: 23 652 → 47 684), así que el consumo por decisión sube; M16 (`decision.questionDetail`, `compact` por defecto) lo vuelve a medir) | Archivo de escenario grabado: 4 452 bytes con 1×1 y 51 117 con 16×16 (el peor caso: 1 000 archivos son unos 51 MB por arranque). Solicitud 1×1: 2 253 caracteres, estado 1 216; 8×4: 11 451 y 3 464; 16×16 (el máximo de M06.3): 23 652 y 7 699. Ningún estado se recorta a 12 000. Tokens a 1,5 / 4 caracteres y estimación de M09: 1×1 1 502 / 564 / 700; 16×16 15 768 / 5 913 / 5 200 (con la cuenta real el cuerpo sale a 1,59–1,82 caracteres por token) |
| CPU del codificador y del analizador (p50) | Codificar: 17–27 us (1 mob) a 118 us (16×16); analizar: 3–5 us a 35 us. Corren en el executor del cliente, fuera del hilo del servidor |
| Ida y vuelta del cliente real al servidor de contrato en bucle local | `decide` (1 mob, 2 preguntas), 300 llamadas: p50 0,390 ms, p95 0,499, p99 0,595, máx. 1,099; `check`, 100 llamadas: 0,274 / 0,356 / 0,671 / 1,151 ms |

### Calibración

Ningún default cambia en M13: la evidencia local respalda los valores actuales o no basta para cambiarlos. Cada fila dice qué falta.

| Valor | Evidencia local | Decisión | Pendiente |
|---|---|---|---|
| `jev.maxStateChars` 12 000 | El encuentro más grande que admite el registro (16 mobs, 16 participantes, 16 eventos) codifica 7 699 caracteres de estado con el ejemplo: margen del 36 % | Se mantiene | Objetivos de perfil mucho más largos que el del ejemplo (servidor) |
| Caracteres por token de `RequestSizeGuard`: **2 → 1,5** | Con la cuenta real (verificado con la cuenta real 2026-09-29, [informe del banco](evidencia/live-scenario-bench-2026-09-29.md)), 1,59 / 1,73 / 1,82 (mínimo, media, máximo) en 1 200 decisiones: 2 subestimaba en torno a un 15 % (el banco estimó 639 200 tokens y usó 735 800). Con 1,5 la solicitud más grande posible estima 15 768 tokens, lejos de 64 000 y 32 000 | **Cambia a 1,5**, por debajo del mínimo observado (M08, desviación 18) | Solicitudes reales del servidor con `PredictValues` (se revisa si la relación baja de 1,5) |
| Estimación de tokens 400 / 120 / 60 | Con la cuenta real (verificado con la cuenta real 2026-09-29): 613 tokens por llamada en escenarios de una pregunta y uno o dos participantes, frente a 590 de la estimación (un 4 % menos; **medido antes de M15**: con los criterios de M15.7 cada solicitud crece (1 mob y 1 jugador: 2 253 → 3 755 caracteres; 16 × 16: 23 652 → 47 684), así que el consumo por decisión sube; M16 (`decision.questionDetail`, `compact` por defecto) lo vuelve a medir). Todos los escenarios tienen una pregunta, así que no se puede separar el coste por pregunta del base; la media móvil de M09 corrige el coste por pregunta en cada respuesta | Se mantiene (análisis en M09, «Cuenta real») | Solicitudes con varias preguntas en el servidor (5.B) |
| `maxRequestsPerMinute` 600 y ventana de 59–60 s | Como mucho 1 200 en un minuto real con un emisor voraz: igual al límite publicado; 601 con uno constante | Se mantiene | Cómo cuenta TypeSafe su ventana (**PAGO**, carga 5.B) |
| Umbrales de pausa (2 timeouts, 3 inválidas) | Con fallos independientes ≤ 5 %, como mucho 2,3 pausas por 1 000 solicitudes y ningún tiempo sin enviar; al 20 %, 29,8 y 1,35 % | Se mantienen | Tasa real de fallos del host (servidor, **PAGO**) |
| `maxTickWorkMicros` 2 000 y `maxEncounterEvaluationsPerTick` 2 | El motor gasta de media 4–22 us por tick (p99 ≤ 61 us) de 1 a 200 mobs sin servidor; tras la corrección 2 no hay inanición con capturas caras | Se mantienen | Coste real de captura y sonda (guion §5.2 y §5.4); si `tick_work` p95 se acerca a 2 ms, bajar `maxProbesPerTick` o las evaluaciones |
| `PATH_CHECKS_PER_EVALUATION` 2 | Sin medida local posible: el coste es de `findPath` en el servidor | Se mantiene | Guion §5.2 (`capture` con y sin clave) |
| `requestTimeout` 800 ms | El cliente añade menos de 1 ms (p99 0,595 ms en bucle local); con la cuenta real, desde la máquina de desarrollo, la ida y vuelta completa da p99 328 ms y máximo 513 ms en 1 200 llamadas, sin ningún `TIMEOUT` (verificado con la cuenta real 2026-09-29) | Se mantiene | Latencia desde el host (guion §6) |
| Umbrales de confianza 0,5 y margen 0,15 | Con la cuenta real (2026-09-29): 0,50 conserva el 33,3 % de las respuestas y todas aceptables; 0,40, el 39,3 % con un 84,7 %; 0,30, el 79,3 % con un 45,4 %; tramo 0,0–0,3 con un 69 % aceptable y 0,3–0,5 con un 5,8 % (curva no monótona). Margen 0,00: 0,5 % de cambios; desde 0,10: ninguno. Tolerancia de la suma ± 0,02 confirmada en las 1 200 respuestas del informe. A 0,5, el 66,7 % de las respuestas pagadas de este banco se descartan por confianza baja (`LOW_CONFIDENCE`): la mayor parte del gasto compra respuestas que no se usan. **Punto clave para que el usuario lo revise**; opciones para M14 o la 0.0.2: mejorar la redacción de las preguntas y las etiquetas, o un `minimumConfidence` por perfil. | Se mantienen, respaldados de forma preliminar: las etiquetas son las propuestas por M13 (`labelsProposedBy`) y el usuario no las ha revisado; la muestra efectiva son 6 escenarios de una sola pregunta repetidos 200 veces; la curva no es monótona (0,0–0,3: 69 % aceptable; 0,3–0,5: 5,8 %) y no hubo contingencias. Siguen abiertos hasta que el usuario revise las etiquetas y haya escenarios más variados | Revisión de etiquetas por el usuario y banco grabado en el servidor |
| 60° de orientación, 1 bloque de tendencia, 20 ticks de escudo desactivado, bandas de duración, `MELEE_BLOCKED` por `isBlocking()` | Dependen de jugadores reales con latencia | Se mantienen | M01 §11 y A/B del guion §7 |
| Pesos de amenaza, ventana de 100 ticks, regla de dos decisiones | Tabla modelo: el tope por ventana actúa y el decaimiento cede el liderazgo; al empezar un combate con totales pequeños la regla del 10 % permite 3 cambios de líder en 5 s con acoso | Se mantienen | M01 §13 (13.7 puede fallar al inicio del combate; se anota) |
| Racha de alternancia `LOCAL`/`BASE` | 29 caídas por minuto de 2 ticks | Sin cambio (contrato de M10) | Efecto en juego y señales repetidas (guion §7) |
| Rearme de DT-28 | Con Jev la skill se rearma cada vida de intención local, igual que el local replanifica (17,6 frente a 19,1 casts en el duelo con los defaults) | Sin valor nuevo: el intervalo es `min(maximumIntentTicks, minimumDecisionIntervalTicks)` | Daño y tiempo en alcance del A/B (guion §7) |

### Correcciones de módulos anteriores (plan §11: se corrige el contrato del módulo y se repite la prueba)

| # | Módulo | Fallo encontrado por la suite de M13 | Corrección | Prueba |
|---|---|---|---|---|
| 1 | M06/M09, `SessionUpkeep.prepare` | Un mob que cambiaba a un jugador sin encuentro en su mundo formaba un encuentro nuevo en la sonda, y `prepare` lo devolvía al anterior con el objetivo de la última captura antes de capturarlo en el nuevo: un encuentro nuevo cada 2 ticks (400 en 40 s) y el mob en nivel 4 hasta 40 s | El objetivo de la última captura solo indica que el miembro podría tener que moverse; el movimiento sigue el objetivo que lee una sonda nueva. Como mucho una sonda extra por miembro afectado | `SessionUpkeepTest` (4 paridades); `CoordinatorLoadTest` |
| 2 | M09, `Coordinator.decide` | Con una captura que gasta el presupuesto del tick, `Agenda.due(tick, 2)` avanzaba la rotación de mundos dos veces y el coordinador solo evaluaba el primero: un mundo recibió 1 201 capturas por minuto y el otro 1 | El coordinador pide a la agenda un encuentro cada vez (`due(tick, 1)`) y la rotación solo avanza con los evaluados; con presupuesto suficiente el orden es el mismo que antes | `CoordinatorWorkBudgetTest`; `CoordinatorTest` sin cambios |
| 3 | M10, `IntentControl.cast` (tras la auditoría, decisión del orquestador sin objeción del usuario, DT-28) | Una intención `JEV` renovada conservaba `skillConsumed` para siempre: una táctica sostenida por Jev lanzaba su skill una sola vez (1 cast en 30 s en el duelo frente a 19 en local) | Rearme por reloj cada vida de intención local (`min(maximumIntentTicks, minimumDecisionIntervalTicks)`, `FallbackStep.localIntentTicks`, compartido con la replanificación local); sin señal, sin tocar `startTick`; cooldowns nativo y extra intactos (M10, desviación 28; 01, revisión 9) | `CastSemanticsTest`, `IntentPublicationTest`, `TacticalExecutionABTest` |
| 4 | M09, `Coordinator` (tras la auditoría) | `DEFERRED_TICKS` contaba cualquier tick sin presupuesto, aunque nada esperase | Cuenta solo si `Agenda.hasDue` (sin sondear ni rotar) o hay instalaciones pendientes | `CoordinatorWorkBudgetTest` |
| 5 | M08, `FailureClassifier` (tras la ejecución con la cuenta real) | Un modelo inexistente responde HTTP 400 `{"detail":{"error_type":"api_usage_error","message":"Unknown model: …"}}`, no 422 ni 404; caía en `SERVICE_ERROR`, así que un modelo mal escrito pausaba y reintentaba sin fin en vez de bloquear | 400 con `api_usage_error` y un mensaje que empieza por `Unknown model` → `MODEL_UNAVAILABLE` (`INVALID_MODEL`, bloqueado) con un detalle fijo que no repite el modelo; cualquier otro 400 → `INVALID_REQUEST`; cuerpo leído con `StrictJson`; el 401 real con `detail` en forma de objeto se clasifica sin error | `FailureClassifierTest` (3 nuevas), `SystemOneClientTest.theRealBodiesOfAnUnknownModelAndOfARejectedKeyAreClassifiedOverHttp`; fixtures `error-400-unknown-model.json` y `error-401.json` |
| 6 | M08, `RequestSizeGuard` (calibración con la cuenta real) | 2 caracteres por token subestimaba un 15 % | 1,5 caracteres por token | `RequestEncoderTest` (límites exactos y `tokensAreEstimatedAtOneAndAHalfCharactersEachRoundedUp`) |

### Hallazgos abiertos

1. ~~Una táctica renovada por Jev no vuelve a lanzar su skill~~ **Resuelto tras la auditoría (corrección 3, DT-28):** la choice de una intención renovada se rearma cada vida de intención local, así que con los defaults el brazo con Jev lanza casi como el local (17,6 frente a 19,1 casts en el duelo); el A/B del guion §7 confirma el daño en el servidor.
2. **Un cambio de objetivo deja al mob en nivel 4 hasta la siguiente captura de su encuentro** (`TARGET_CHANGED`, M10): 2–4 ticks de ordinario y como mucho `minimumDecisionIntervalTicks + 2` (22). Es el diseño; en la carga supone del 0,22 al 0,54 % del tiempo.
3. **Las sondas pueden dejar sin evaluaciones** porque los pasos 1 a 4 siempre corren (M09.1): con sondas de unos 2 000 / (mobs / 2) us o más (31 us desde 128 mobs), ningún encuentro decide. Lo decide la medida de sondas del servidor (guion §5.4); la respuesta sería bajar `decision.maxProbesPerTick`.
4. **Encuentros fragmentados**: sin fusión global (M06.3), dos grupos que se juntan duplican las solicitudes. Con mobs a ≤32 bloques de su jugador la fragmentación es pequeña (0–6 encuentros de más con 200 mobs). Se mantiene sin fusión salvo que el A/B muestre pérdida de calidad.
5. **Un mob local atascado reemite su señal de táctica** cada vez que vuelve de nivel 4 (30 por minuto): un `~onSignal` del autor se dispararía otras tantas veces. Se observa en el servidor (guion §7).

### Desviaciones

| # | Desviación | Motivo |
|---|---|---|
| 1 | Las pruebas de carga y los arneses A/B van en el build normal; los tiempos van en la tarea aparte `m13Measure` y ninguna prueba falla por tiempo | Con `ServerClock` controlable, 10 minutos de combate simulado tardan décimas de segundo; los tiempos de CPU dependen de la máquina (P2 y la instrucción de no hacer frágil la suite) |
| 2 | El arnés A/B compara con y sin Jev guionizado, no con la IA de MythicMobs | El brazo A de M13.5 (boss clásico) solo existe en el servidor; su variante está en el guion §7.1. Las respuestas guionizadas miden el camino de ejecución, no el modelo |
| 3 | El escenario grabado guarda el cuerpo exacto de la solicitud (ya con alias) más preguntas, recuentos, respuesta y etiquetas vacías, en vez de `DecisionRequest` y `CombatFacts` | Esos records llevan la UUID de cada mob (`MobKey`), que no puede salir del servidor (I6); el cuerpo es además lo que se reenvía a Jev para calibrar, sin volver a codificar |
| 4 | Grabación limitada a 1 000 archivos por arranque, solo para respuestas `Answered` que completaron el resultado, entregada después de completarlo a un hilo propio del grabador con una cola de 64 (llena: se descarta y se cuenta), escrita en un temporal y movida de forma atómica, con nombre `scenario-<UTC>-<nonce>-<nnnn>.json`; el cliente cierra el grabador con una espera acotada de 1 s (las decisiones aún en cola se descartan y se cuentan; el cierre completo del cliente puede tardar unos 2 s: 0,5 del executor, 0,5 del cliente HTTP y 1 del grabador) | A5 (disco y memoria acotados), T2 (el hilo de análisis solo encola: un disco lento no retrasa respuestas ni `ROUNDTRIP`) y ningún escenario a medias; una respuesta fallida no tiene nada que calibrar. Es una opción de depuración que se deja apagada en producción |
| 5 | `SystemOneClient` pasa de 198 a 232 líneas por los constructores con grabador; `ScenarioRecorder` 248; `SessionUpkeep` 249; `MobSession` 421; `IntentControl` 241 | Los constructores de paquete sirven a las pruebas y a las herramientas; `ScenarioRecorder` y `MobSession` son sobre todo Javadoc de accesores públicos (C9); `SessionUpkeep` gana el Javadoc de la corrección 1; `IntentControl` el rearme documentado. Ningún método pasa de 30 líneas |
| 6 | `LoadFixture` tiene 567 líneas | Compone el motor completo y cinco dobles anidados (mundo, metas, acciones, Jev, picos), como `ExecutionFixture` (436); ningún método pasa de 30 líneas |
| 7 | Las herramientas de pago viven en `src/test` como `main` y se lanzan con `JavaExec` (`liveJevCheck`, `liveScenarioBench`) | Así nunca entran en el JAR ni en `check`; comparten el cliente real y el analizador. Doble puerta: la tarea de Gradle exige `-PliveJev=true` antes de ejecutar y la herramienta exige además la clave en el entorno. Gradle nunca lee la clave (no queda en la caché de configuración) |
| 8 | La latencia de `GET /v1/models` desde el host se mide con `curl -K -` (clave por la entrada estándar), no con `/thepredict check` | `check` no mide ni muestra su tiempo y M13 no crea métricas nuevas; `curl` es nativo del host y no pone la clave en los argumentos |
| 9 | El banco semilla usa el perfil de ejemplo leído sin MythicMobs, así que sus criterios no incluyen los `PredictValues` que añade el binder | Sin servidor no hay metadatos de skills; las solicitudes reales son algo más largas. Los escenarios grabados en el servidor son la referencia. **Resuelta en M15.9 para los propósitos:** el ejemplo los declara, así que el banco regenerado describe las cuatro skills sin binder; la clase de cooldown queda `UNKNOWN` y sin frase (la lee el binder). El banco pagado se conserva en `scenarios/seed-2026-09-29/` |
| 10 | Las etiquetas del banco semilla son una propuesta de M13 (`labelsProposedBy`) | M13.6 pide etiquetado a mano: el usuario las revisa antes de calibrar |

### Pendiente de verificación en servidor

- Piloto y plantilla de evidencia (guion §2 y §4), incluidos los comandos de spark 1.10.180 (DT-29).
- Carga de 1, 10 y 30 mobs en dos mundos, sin clave y con clave, carga masiva de chunk con 30 mobs, sondas con 100 mobs, captura con varios jugadores y capacidad del host (guion §5; pendientes de M05, M07 y M09, DT-18).
- Sesiones suspendidas en un servidor de larga duración (guion §5.5, DT-20).
- Latencia por tramo (guion §6.2–6.3) y cesión a la IA base tras `release` en la primera lectura posible (guion §6.4, M01 §6.3, DT-08); el número exacto de ticks no se puede leer con los comandos que quedan y solo lo respalda la evidencia local.
- A/B de M13.5 con la variante clásica y el ejemplo con Predictor apagado y encendido (guion §7; pendiente de M12.5), incluidos los golpes sin daño de DT-28 y las señales repetidas del hallazgo 5.
- Escenarios de M11.6 con y sin pesos (M01 §13, 13.8 como brazo sin enriquecer) y `MELEE_BLOCKED` por `isBlocking()`.
- Umbrales de M07 (60°, 1 bloque, 20 ticks de escudo) y bandas de duración, en M01 §11 y en el A/B.

### Cuenta real (**PAGO**)

**Ejecutado el 2026-09-29** por el orquestador con la aprobación del usuario (20 comprobaciones, 6 decisiones de humo, 1 200 del banco semilla y dos llamadas de error a propósito: clave inválida y modelo inexistente); evidencia en [informe de la comprobación](evidencia/live-check-2026-09-29.md) y [informe del banco](evidencia/live-scenario-bench-2026-09-29.md), revisada sin clave ni cabeceras. Resultados: 20/20 `READY`; 1 200/1 200 respondidas por `jev-1.13.0` sin fallos; 735 800 tokens, 0,0309 USD; tolerancia y caracteres por token resueltos; umbrales respaldados solo de forma preliminar (tabla de calibración, DT-31); un modelo inexistente responde **HTTP 400** `api_usage_error`, que se corrigió (corrección 5); 401 con `detail` en forma de objeto. No hubo 429 ni 529.

Tabla de la propuesta original, para repetir las ejecuciones:

Precio usado: 0,042 USD por millón de tokens de entrada (`StatusFormatter.USD_PER_MILLION_INPUT_TOKENS`, plan §6); confirmar en `https://docs.typesafe.ai/models` antes de aprobar (M12 lo dejó pendiente; M13 no consultó la web). Estimación de tokens: 400 + 120 por pregunta + 60 por participante (M09) o 1,5 caracteres por token (cota de `RequestSizeGuard` tras la calibración; con 2 la estimación del banco se quedó un 13 % por debajo del consumo real). Todas se lanzan en el mismo shell tras `set -a; source .env; set +a`.

| Qué | Comando | Llamadas | Tokens estimados | USD estimado |
|---|---|---|---|---|
| `check` real y latencia de red (M13.4, M12) | `./gradlew liveJevCheck --no-daemon -PliveJev=true` (opcional `-Pcalls=50 -PspacingSeconds=10`) | 50 × `GET /v1/models` | 0 | 0 |
| Banco semilla, una pasada (humo) | `./gradlew liveScenarioBench --no-daemon -PliveJev=true` | 6 | ≤ 4 264 | ≤ 0,0002 |
| Serie de inferencia y calibración (M13.4, M13.6: relación caracteres/token, curva de fiabilidad, margen, contingencias) | `./gradlew liveScenarioBench --no-daemon -PliveJev=true -Prepetitions=200` | 1 200 | ≤ 852 800 (real: 735 800) | ≤ 0,036 (real: 0,0309) |
| Banco grabado en el servidor | `./gradlew liveScenarioBench --no-daemon -PliveJev=true -PscenarioDir=<carpeta> -Prepetitions=<n>` | archivos × n (tope `-PmaxCalls`, 1 300) | ≤ caracteres ÷ 1,5 por llamada | se imprime antes de empezar |
| Servidor: carga 5.B, latencia §6.2, A/B §7.2 con clave | Guion §9 | ≈ 600 a 3 600 por paso | ≈ 0,4 a 5,4 M por paso | ≈ 0,02 a 0,23 por paso; ≈ 0,640 en el servidor (0,018 + 0,104 + 0,228 + 0,12 + 0,17) y ≈ 0,676 con las dos ejecuciones del banco a la cota de 1,5 (+ 0,0002 + 0,036) |

Las herramientas escriben su informe en `build/reports/m13/` sin clave ni cabeceras; el modelo es `jev-1.13.0` salvo `-Pmodel=…`.

### Pendiente para M14

- Publicar la capacidad soportada y los defaults solo con la evidencia del guion §5 y §6 (C13, W3); hoy ningún default cambia.
- **Punto clave para el usuario (DT-31):** A 0,5, el 66,7 % de las respuestas pagadas de este banco se descartan por confianza baja (`LOW_CONFIDENCE`): la mayor parte del gasto compra respuestas que no se usan. **Punto clave para que el usuario lo revise**; opciones para M14 o la 0.0.2: mejorar la redacción de las preguntas y las etiquetas, o un `minimumConfidence` por perfil.
- Guía del autor y del administrador: la calibración de 1,5 caracteres por token es para texto en inglés; objetivos y descripciones en otras escrituras (por ejemplo CJK) pueden tener menos caracteres por token, y `jev.maxStateChars` acepta como mucho 45 000.
- Guía del administrador: `debug.recordScenarios` (qué graba, dónde, límite de 1 000 archivos por arranque con aviso único al llegar, cola de 64 que descarta y avisa, cierre del cliente de hasta unos 2 s al apagar con la grabación activa, peor caso de disco por arranque unos 51 MB con encuentros de 16 mobs y 16 jugadores y unos 4,5 MB con uno contra uno, dejarlo apagado), las tareas de pago y su puerta, y que `maxInputTokensPerMinute` es el límite que manda con muchos mobs (1,51 USD por hora al máximo).
- Guía del autor: la skill de una táctica sale como mucho una vez por intención; tanto en local como con Jev (DT-28) eso es cada `min(maximumIntentTicks, minimumDecisionIntervalTicks)` ticks, y lo que la limita es su `Cooldown` (sustituido el 2026-09-30: la skill sale a su propio `Cooldown`, M10 desviación 29); y que un `~onSignal` puede repetirse con un mob atascado (hallazgo 5).
- Informe final del guion §10 y el subconjunto de aceptación alojada.
- **Hecho en M14 (2026-09-29):** guías del autor y del administrador con todo lo anterior (`Guias/`), capacidad y defaults rotulados "pendiente de medición en servidor", la revisión pendiente del usuario en el [CHANGELOG](../../CHANGELOG.md#revisión-pendiente-del-usuario), la aceptación alojada en la §12 del [guion](Guion-servidor-0.0.1.md#12-aceptación-alojada-m145) y el [paquete de pruebas](paquete-pruebas/README.md). El informe final del guion §10 sigue pendiente de evidencia de servidor.

### Correcciones tras la auditoría (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **1200 pruebas, 0 fallos** (8 más que antes de la auditoría). Sin red salvo el bucle local, sin leer `.env`, sin servidor y sin ejecutar las tareas de pago.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante (T2):** el grabador serializaba y escribía en el hilo de análisis antes de completar el resultado: un disco lento retrasaba cada respuesta, podía convertir respuestas válidas en `TIMEOUT` (dos seguidos pausan el servicio), inflaba `ROUNDTRIP`, ocupaba el executor compartido y `shutdownNow` podía dejar JSON truncado | El cliente completa el resultado y después entrega la decisión al grabador, que escribe en su propio hilo con una cola acotada (64; llena: descarta, cuenta y avisa una vez); temporal y movimiento atómico; el cliente cierra el grabador con una espera de 1 s; nombres `scenario-<UTC>-<nonce>-<nnnn>.json`, únicos entre arranques del mismo segundo; avisos separados para límite, descartes y fallos, así el del límite sale aunque antes fallara una escritura; `SystemOneWiring.recorder` traduce `debug.recordScenarios` | `ScenarioRecorderTest` (disco lento, cola llena, límite tras fallo, dos arranques en el mismo segundo sin temporales), `SystemOneWiringTest.recordScenariosTranslatesIntoARecorderOfTheScenariosFolderAndOffIntoTheDisabledOne` |
| **Bloqueante (C4):** constructor de `ScenarioRecorder` con un parámetro `Optional<Path>` | Carpeta y marca `enabled`; `disabled()` e `into(…)`; el accesor `directory()` devuelve `Optional` | `ScenarioRecorderTest.theDisabledRecorderWritesNothingAndHasNoFolder` |
| **Bloqueante:** guion: `0 in flight` en vez de `0/8 in flight`; spark da mediana (`med`), no media; `profiler stop` y `heapsummary` suben a bytebin sin `--save-to-file`; M01 6.3 citaba un §6.3 sin paso de `release` | `Server: 0/600 requests in 60 s, 0/8 in flight`; tabla y fórmula de §5.6 con la mediana; regla de §0 y M01 §7 con `--save-to-file`; nuevo paso §6.4 ejecutable (cesión en la primera lectura) y referencias de M01 6.3 y de este documento corregidas: el número exacto de ticks no se puede leer | — (guion) |
| **Decisión del usuario (DT-28), revisada:** una intención `JEV` renovada conservaba `skillConsumed` para siempre; la primera corrección rearmaba cada `maximumIntentTicks` (mitad de casts que el local con los defaults) y el usuario pidió paridad con el local | Corrección 3: rearme por reloj exactamente cada vida de intención local, `min(maximumIntentTicks, minimumDecisionIntervalTicks)`, del mismo `FallbackStep.localIntentTicks` que la replanificación local; sin señal, sin tocar histéresis ni permanencia mínima; el cast sigue sujeto a los cooldowns; `MobSession.choiceConsumedTick` y `choiceEpoch` (01, revisión 9); M10 desviación 28 | `CastSemanticsTest` (renovación antes del intervalo sin cast, rearme una vez por vida local, ninguna respuesta sin intervalo relanza, un cast por época), `IntentPublicationTest`, `TacticalExecutionABTest.withTheConfigDefaultsAJevThatRenewsTheSameTacticCastsAtTheLocalRate` (duelo: 17,6 frente a 19,1) |
| `DEFERRED_TICKS` contaba ticks en que no esperaba nada | Corrección 4: solo cuenta si `Agenda.hasDue` (sin sondear ni rotar) o hay instalaciones pendientes; guion §5.4.1: `deferred_ticks=` no aparece | `CoordinatorWorkBudgetTest.aTickWithoutAnyDueEncounterOrPendingInstallIsNeverCountedAsDeferred` |
| Faltaba equidad con tres mundos | Prueba nueva | `CoordinatorWorkBudgetTest.threeWorldsShareAnExhaustedBudgetEvenly` |
| Disco del grabador sin documentar | Peor caso medido (51 117 bytes por archivo con 16×16) y nota para M14 | `m13Measure` |
| `liveJevCheck` sin `--no-daemon` en §6.1; comprobación de la clave literal; líneas de cambio de táctica de §7.2; total de costes | `--no-daemon`; `grep -rF -f <(printf '%s\n' "$TYPESAFE_API_KEY") <carpeta>` en §2; solo `Mob <uuid> <NIVEL> <táctica>` y aviso de `log line(s) suppressed` (20 líneas cada 10 s); total 0,638 / 0,665 USD | — (documentos) |
| El banco abría un cliente (y un handshake TLS) por escenario | Un solo cliente para todos los escenarios (cuerpos por identidad de la solicitud); solo la primera llamada paga el handshake | `LiveToolsLoopbackTest` (primer y último cuerpo reenviados tal cual) |

### Segunda ronda de correcciones tras la auditoría (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **1201 pruebas, 0 fallos**. Sin red salvo el bucle local, sin leer `.env`, sin servidor y sin ejecutar las tareas de pago. Las cifras del A/B salen de una ejecución nueva de `m13Measure` tras la corrección 3 revisada (paridad con el local).

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** la casilla de M10.3 "una intención produce como máximo un `CAST`" era falsa tras DT-28 y citaba una prueba retirada; la prueba del rechazo solo pasaba porque duraba 10 ticks | Casilla, propósito de M10.3 y desviación 24 de M10 reescritos: como máximo un `CAST` por época de la choice, una época por vida de intención local; la prueba del rechazo muestra la choice consumida en su época y otra vez intentada en la siguiente | `CastSemanticsTest.aRejectionByMythicMobsConsumesTheChoiceForItsEpochAndTheNextEpochTriesAgain` |
| **Bloqueante:** fila A/B de este documento con `SKIPPED_SHIELD` 1,3 en el escudo con Jev | 3,1, de la ejecución nueva de `m13Measure`; el resto de la fila coincide con esa ejecución | `m13Measure` |
| **Bloqueante:** guion §6.4 esperaba una meta base `running` con el jugador en creativo | Esperado: `thepredict:hold` ya no está `running` y ninguna meta `thepredict:*` lo está; sin objetivo, las metas base pueden estar inactivas | — (guion) |
| Aserción de reloj redundante y cierre con espera fija en `ScenarioRecorderTest` | Sin la aserción de 5 s; la espera de cierre se inyecta (30 s en las pruebas, 1 s en producción) | `ScenarioRecorderTest` |
| Decisiones en cola perdidas sin contar al cerrar | `close()` cuenta en `dropped()` lo que `shutdownNow` devuelve; el Javadoc de `SystemOneClient.close()` dice que el cierre puede tardar unos 2 s | `ScenarioRecorderTest.decisionsStillQueuedWhenTheRecorderClosesAreDroppedAndCounted` |
| Receta de la clave del guion §2 con una clave vacía coincidiría con todo | `"${TYPESAFE_API_KEY:?}"` | — (guion) |
| DT-08 decía que el tiempo exacto de cesión pasaba a M13 | Solo evidencia local, como el guion §6.4 | — (registro) |
| Separador ausente en la fila de `MobSession` de 01 | Corregido | — |
| Plan §12, fila Dominio: "un cast por intención" | Referencia a la época de la choice y a DT-28 | — (plan) |

### Tercera ronda: evidencia de la cuenta real (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **1206 pruebas, 0 fallos** (5 nuevas: 3 de `FailureClassifierTest`, 1 de `SystemOneClientTest` y 1 de `RequestEncoderTest`). Las llamadas de pago las ejecutó el orquestador con la aprobación del usuario; este módulo no hizo ninguna llamada de red, no ejecutó las tareas de pago ni leyó `.env`. Los informes se copiaron a `evidencia/` tras comprobar que no tienen clave, cabeceras ni nombres de variables de entorno.

| Evidencia | Cambio | Prueba |
|---|---|---|
| Modelo inexistente: HTTP 400 `api_usage_error` (bloqueante) | Corrección 5 | `FailureClassifierTest`, `SystemOneClientTest` |
| 401 real con `detail` en forma de objeto | Fixture y pruebas; el análisis nunca falla | `FailureClassifierTest`, `SystemOneClientTest` |
| 1,59 / 1,73 / 1,82 caracteres por token | Corrección 6: `RequestSizeGuard` a 1,5; M08 desviación 18; estimación previa de `liveScenarioBench` a 1,5; columna de `m13Measure` | `RequestEncoderTest` |
| 613 tokens por llamada frente a 590 estimados (**medido antes de M15**: con los criterios de M15.7 cada solicitud crece (1 mob y 1 jugador: 2 253 → 3 755 caracteres; 16 × 16: 23 652 → 47 684), así que el consumo por decisión sube; M16 (`decision.questionDetail`, `compact` por defecto) lo vuelve a medir) | Sin cambio de los valores base (M09, «Cuenta real») | — |
| Confianza, margen, tolerancia, latencia, modelo efectivo | Tolerancia y modelo efectivo resueltos; umbrales respaldados de forma preliminar; casillas de M08, M09 y M13 marcadas con la fecha; DT-30 y DT-31 del registro | — |

### Cuarta ronda: auditoría de la evidencia (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **1208 pruebas, 0 fallos**. Sin red, sin tareas de pago y sin leer `.env`.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** casilla de umbrales de M13.6 y DT-31 «Resuelta» prematuras (etiquetas propuestas por M13 sin revisar, 6 escenarios de una pregunta, curva no monótona, sin contingencias) | Casilla desmarcada; DT-31 dividida (caracteres por token, estimación de M09 y tolerancia resueltas; umbrales y `timeoutMillis` abiertos); misma nota en M10; consecuencia documentada: a 0,5 se descarta el 66,7 % de las respuestas pagadas, punto clave para el usuario con opciones para M14 o la 0.0.2 | — (documentos) |
| **Bloqueante:** «1 206 respuestas» no trazable (el informe de humo se sobrescribió) | 1 200 en M08, DT-31 y la calibración de M13 | — |
| Total de costes | 0,640 y 0,676 con el banco | — |
| `jev.maxStateChars` aceptaba hasta 100 000 y la guarda rechaza estado más pregunta por encima de 48 000 caracteres | Máximo 45 000 (`JevSettings.MAX_STATE_CHARS`), tabla de M03 y comentario de `config.yml`; una prueba del cliente mantiene los dos valores de acuerdo (D3 impide que `config` lea la guarda) | `JevSettingsTest`, `RequestEncoderTest.theLargestAcceptedStateLeavesRoomForTheLongestQuestionUnderTheGuard` |
| Calibración de 1,5 solo válida para inglés | Nota para M14 aquí, en M08 (desviación 18) y en DT-31 | — |
| Un 400 en la comprobación de arranque | Prueba: `PAUSED` con detalle fijo | `ModelCatalogCheckTest.aBadRequestOnTheStartupCheckPausesWithAFixedDetail` |
| Plantilla del registro sin `Resuelta` | Añadido | — |

### Disponibilidad en tiempo real (M10, decisión del usuario, 2026-09-30)

La skill de una táctica sale ahora a su propio `Cooldown` en `LOCAL` y con Jev (M10, desviación 29; DT-28 revisado). Afecta a M13 así:

- **Dobles.** `CombatActions` gana `ready`. Los dobles de `ExecutionFixture` y `LoadFixture` modelan un `Cooldown` nativo por mob y choice (`NativeCooldowns`, 20 ticks por defecto, el ritmo al que se midió M13); mientras dura, `cast` responde `NOT_ELIGIBLE` como el adaptador. `ExecutionFixture.captureCooldowns()` hace que la captura del doble excluya, como `FactsAssembler`, las tácticas cuya skill se enfría. El arnés A/B (`TacticalAB`) y la tabla de rearme dan las mismas cifras que antes (17,6 ± 1,3 con Jev y 19,1 ± 1,2 en local con los defaults), porque su `Cooldown` coincide con la vida de intención local.
- **Cadencia (`ReadinessCadenceTest`, 7 pruebas).** Con un `Cooldown` de 30 ticks la skill sale cada 30 ± 2 ticks en `LOCAL` y con Jev renovando la táctica (antes, 40 en `LOCAL` con una captura que excluye la skill que se enfría); ninguna época lanza dos veces; una respuesta sola nunca relanza; ningún cambio de táctica local llega antes de la permanencia mínima; una skill que se enfría no hace parpadear la táctica local. Un golpe vanilla sale cada 20–22 ticks, el ritmo de `MeleeAttackGoal`, en `LOCAL` y con Jev.
- **Costo por tick (`m13Measure`, esta máquina, dos pasadas de cada versión con el mismo código salvo la disponibilidad):**

| Escalón | Sin disponibilidad (media us/tick; p99) | Con disponibilidad (media us/tick; p99) |
|---|---|---|
| Carga con Jev, 30 mobs | 6,7–7,1; 34–37 | 6,7–7,4; 34–35 |
| Carga con Jev, 100 mobs | 16,9–17,5; 55–56 | 19,0–19,5; 55–58 |
| Carga con Jev, 200 mobs | 29,4–29,6; 78–80 | 32,2–33,3; 79–86 |
| Sin clave (todo `LOCAL`), 30 mobs | 3,8; 19 | 4,4–4,5; 20 |
| Sin clave, 100 mobs | 13,5; 41–42 | 15,6–15,8; 44 |
| Sin clave, 200 mobs | 24,4; 58–59 | 26,7–27,2; 61 |

  Unos 2–3 us más por tick con 200 mobs (≈ 30 ns por sesión y ciclo reactivo), lejos de `maxTickWorkMicros` 2 000. **Medido con dobles:** el `ready` de los dobles es una búsqueda en un mapa; en un servidor, `CombatActionsAdapter.ready` pasa por la búsqueda del `ActiveMob` y el `CooldownRegistry` de MythicMobs, así que cabe esperar unos 0,1–1 µs por sesión que espera a su skill: sigue siendo despreciable frente al presupuesto (con 100 sesiones esperando, ≤ 100 µs por tick en el peor caso; pendiente de `tick_work` en servidor). La disponibilidad solo lee el cooldown de las tácticas de `eligibleWhenReady` mientras la choice vigente no está lista, sin capturas, rutas ni medidas nuevas; el planificador corre, y reserva su conjunto, solo cuando alguna táctica con choice está lista. La tabla nueva «Load steps without a key» de `m13Measure` mide el caso todo local.

