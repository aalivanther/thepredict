# M09. Coordinador, presupuesto y velocidad

> **Estado:** Local listo — cuenta real verificada el 2026-09-29 salvo 429/529; servidor pendiente (ver [Estado de implementación](#estado-de-implementación)) · **Entrega:** C · **Depende de:** M06, M07, M08 · **Desbloquea:** M10 · **Diseño:** [plan §7](../Plan-Jev-y-combate-tactico.md#7-hilos-aislamiento-costes-y-vigencia), [plan §8](../Plan-Jev-y-combate-tactico.md#8-estados-fallos-y-recuperación) · **Reglas:** [00](00-Arquitectura-y-reglas.md) · **Dominio:** [01](01-Modelo-de-dominio.md)

## Objetivo

Llevar las decisiones de Jev al hilo del servidor sin bloquearlo y dentro de límites medidos. `engine.Coordinator` ejecuta cada tick un bucle acotado: aplica respuestas, atiende señales, programa decisiones por encuentro, respeta el presupuesto de la cuenta y reacciona a fallos del servicio con pausas crecientes. Este módulo sustituye el sondeo cada 10 ticks del código actual, que añade hasta 500 ms a cada respuesta (plan §7).

## Alcance

### Incluye

- Bucle por tick con orden fijo y topes de trabajo.
- `Inbox` acotada para resultados de hilos HTTP.
- `RequestBudget` por servidor, mundo, encuentro y tokens.
- `ServiceStatusTracker` con pausa creciente, `retry-after` y período de prueba.
- `Agenda` con latido, adelanto por contexto pendiente y equidad entre mundos.
- Ensamblado de `DecisionRequest` por encuentro y envío con `DecisionClient`.
- Métricas de latencia por tramo y contadores por causa.
- Cableado en `ThePredictPlugin` y retirada del runner actual del camino de ejecución.

### No incluye

- Qué hacer con cada respuesta aceptada o rechazada, contingencias y fallback: M10. M09 solo invoca esos pasos.
- Captura de hechos y eventos: M07. M09 consume `CombatReader` y `ContextSignals`.
- Cliente HTTP: M08.
- Presentación de estado y métricas: M12.
- Valores definitivos de los límites: M13 los ajusta con medidas.

## Base existente

| Archivo | Acción |
|---|---|
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/MythicPredictionRunner.java` | Se divide: el bucle pasa a `engine.Coordinator` y `engine.DecisionDispatcher`; la aceptación, a `engine.DecisionAcceptor` (M10). Se elimina en M09.7. |
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/RotatingMobCursor.java` | Se elimina; la agenda por encuentro la sustituye. |
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/DecisionReuseCache.java` | Se elimina: una respuesta no autoriza repetir un cast (plan §2). |
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/RemoteRequestBudget.java` | Base de `engine.RequestBudget`. Se conservan las ventanas de 60 s, la reserva antes de enviar y que una recarga no reinicie contadores. La unidad pasa de mob a encuentro y se añaden tokens. Se elimina al crear la nueva clase. |
| `src/main/java/akyrion/thepredict/adapter/paper/ServerThreadInbox.java` | Base de `engine.Inbox`: pasa de `ConcurrentLinkedQueue` sin tope a una cola acotada. Se elimina al crear la nueva clase. |
| `src/main/java/akyrion/thepredict/decision/ProviderHealthMonitor.java` | Referencia de `engine.ServiceStatusTracker`: se conserva la generación que ignora comprobaciones viejas. El bloqueo hasta `/thepredict check` se sustituye por pausa creciente. La clase queda sin uso y M12 la elimina junto con el camino de OpenRouter (W5). |
| `src/main/java/akyrion/thepredict/config/RuntimeSettings.java` | Sus límites por mob los sustituye `config.DecisionSettings` (M03); la raíz construye `engine.EngineSettings`. |
| `src/main/java/akyrion/thepredict/ThePredictPlugin.java` | Nuevo cableado y tarea por tick (M09.7). |
| `src/test/java/akyrion/thepredict/adapter/mythicmobs/RemoteRequestBudgetTest.java`, `adapter/paper/ServerThreadInboxTest.java` | Sus escenarios pasan a `RequestBudgetTest` e `InboxTest`; los originales se eliminan. |
| `src/test/java/akyrion/thepredict/adapter/mythicmobs/RotatingMobCursorTest.java`, `DecisionReuseCacheTest.java` | Se eliminan con sus clases. |

## Subpuntos

### M09.1 Bucle por tick del coordinador

- **Propósito:** un único punto que ordena todo el trabajo del motor en el hilo del servidor, con topes explícitos.
- **Diseño:**
  - `engine.Coordinator.tick()` se ejecuta cada tick. Lee una vez `clock.currentTick()` y `clock.nanoTime()` y sigue este orden:
    1. **Respuestas:** `inbox.drain(maxCompletionsPerTick, …)`, 16 por defecto. Un `CompletedCheck` va a `ServiceStatusTracker.onCheck`. Un `CompletedRequest` libera el presupuesto, informa al tracker y pasa a `DecisionAcceptor` (M10).
    2. **Vigilancia de solicitudes:** un `InFlightRequest` cuyo `deadlineNanos` ya pasó se libera localmente como `TIMEOUT`. Su respuesta tardía, si llega, se descarta por identidad en M10.1. Así una respuesta perdida nunca deja un encuentro bloqueado.
    3. **Señales:** `contextSignals.drain(maxSignalsPerTick, …)`, 64 por defecto. Cada UUID marca contexto pendiente en su encuentro y adelanta su agenda (M09.5).
    4. **Capa reactiva:** para las sesiones de este tick (mitad de las sesiones, escalonadas por `(tick + hash del UUID) % 2`), M10.4 ejecuta sondas, contingencias, alternativas y fallback.
    5. **Instalaciones:** hasta `maxInstallsPerTick` llamadas a `MobControl.ensureInstalled` para sesiones con instalación pendiente (M05, M06), 10 por defecto.
    6. **Decisiones:** `agenda.pollDue(tick, maxEncounterEvaluationsPerTick)`, 2 por defecto, y `DecisionDispatcher.dispatch` para cada encuentro (M09.5 y M09.8).
    7. **Métricas:** duración total del tick del coordinador en `DecisionMetrics`.
  - Tope de tiempo: si el coordinador supera `maxTickWorkMicros` (2 ms por defecto), los pasos 5 y 6 se aplazan al siguiente tick. Los pasos 1 a 4 siempre se ejecutan porque liberan recursos o protegen el control.
  - Errores: cada paso captura `RuntimeException` en su frontera. Un error de una sesión la pasa a `BASE` con `INTERNAL_ERROR` (M10.6). Un error del paso completo se registra con límite de frecuencia y el siguiente tick continúa (C7).
- **Archivos:** crear `engine/Coordinator.java` y `engine/EngineSettings.java`.
- **Reglas:** A3, A5, A6, T1, T4, T5, C7, C10.
- **Pruebas:** `CoordinatorTest` con dobles de los cinco puertos y `ServerClock` controlable: orden de pasos, topes por paso, aplazamiento por tiempo, vigilancia de solicitudes perdidas, error aislado por sesión.
- **Aceptación:**
  - [x] Una respuesta depositada en la inbox se aplica en el siguiente tick (≤50 ms), no a los 10 ticks. Local: se entrega a `ExecutionSteps.accept` (M10) en el tick siguiente (`CoordinatorTest.anAnswerDepositedInTheInboxIsHandedOverOnTheVeryNextTick`); publicar la intención es de M10.
  - [x] Ningún paso supera su tope por tick. `CoordinatorTest.noStepGoesBeyondItsLimitPerTick`, `installationsAndDecisionsWaitForTheNextTickOnceTheWorkBudgetIsSpent`, `aDecisionThatDidNotFitTheBudgetStaysDueForTheNextTick`.
  - [x] Una solicitud sin respuesta se libera tras su plazo y el encuentro vuelve a decidir. `CoordinatorTest.aRequestWithoutAnswerIsReleasedAfterItsDeadlineAndTheEncounterDecidesAgain`.
  - [x] Una excepción en una sesión no detiene a las demás. `CoordinatorTest.anExceptionInOneSessionDropsOnlyThatSessionToTheBaseAi`, `aFailingStepIsContainedCountedAndLoggedByTypeOnly`.
  - [x] Paso 5: 50 sesiones con `installPending` y `maxInstallsPerTick = 10` quedan instaladas en 5 ticks, nunca más de 10 por tick; una instalación `Deferred` (causa transitoria: mob no cargado o no válido) conserva `installPending` y se reintenta dentro del tope por tick; una `Unsupported` (especie sin metas de MythicMobs o conjunto incompleto) retira `installPending`, deja la sesión en `BASE` y no se reintenta hasta que cambie la generación o la revisión del catálogo (reanudar, cambio de mundo o recarga) (M05.4, pendiente heredado de M05). `CoordinatorTest.fiftyPendingInstallationsFinishInFiveTicksWithAtMostTenPerTick`, `aDeferredInstallationIsRetriedAndAnUnsupportedOneWaitsForANewGeneration`.
  - [x] Antes de decidir por un encuentro, el coordinador llama a `MobControl.ensureInstalled` para sus miembros (sin coste si el marcador de la revisión y generación está presente). `progress` solo alimenta la regla `NO_PROGRESS` de M10 (M05, pendiente heredado). `CoordinatorTest.theGoalsOfEveryMemberAreCheckedBeforeTheEncounterDecides`; M09 no llama a `progress`.

### M09.2 Inbox acotada

- **Propósito:** transferir resultados de hilos HTTP al hilo del servidor sin memoria ilimitada.
- **Diseño:**
  - `engine.Inbox<T>` con `ArrayBlockingQueue<T>` de capacidad fija.
  - `boolean offer(T entry)`: cualquier hilo; no bloquea; si la cola está llena devuelve `false` e incrementa un `AtomicLong` de rechazos.
  - `int drain(int max, Consumer<T> receiver)`: solo en el hilo del servidor; entrega como máximo `max` elementos.
  - `void clear()` en apagado.
  - Capacidad: `2 × maxInFlightRequests + 2`, porque cada solicitud produce un solo resultado y las comprobaciones de servicio son a lo sumo una a la vez. Un rechazo no pierde el presupuesto: la vigilancia de M09.1 libera la solicitud al vencer su plazo.
  - Tipo de elemento: `InboxEntry`, interfaz sellada con `CompletedRequest(InFlightRequest request, DecisionResult result, long completedAtNanos)` y `CompletedCheck(long generation, ServiceCheck check, long completedAtNanos)`.
  - El callback de la future (T5) solo construye el record y llama a `offer`. Una future completada con excepción se convierte en `DecisionResult.Failed` (`NETWORK`, detalle `"cancelled"` si fue `CancellationException`).
- **Archivos:** crear `engine/Inbox.java` y `engine/InboxEntry.java`; eliminar `adapter/paper/ServerThreadInbox.java` y su prueba.
- **Reglas:** A4, A5, T2, T5.
- **Pruebas:** `InboxTest`: productores concurrentes, `drain` con tope, rechazo contado con la cola llena, cada elemento entregado una sola vez (escenario heredado de `ServerThreadInboxTest`).
- **Aceptación:**
  - [x] La memoria de la inbox no crece más allá de su capacidad. `InboxTest`.
  - [x] Un rechazo incrementa el contador y no lanza excepción en el hilo HTTP. `InboxTest.aFullInboxRejectsAndCountsWithoutThrowingAndNeverGrows`.
  - [x] Ningún elemento se entrega dos veces. `InboxTest.concurrentProducersAreDeliveredOnceOnlyWhenTheServerThreadDrains`.

### M09.3 Presupuesto de solicitudes y tokens

- **Propósito:** no superar el límite de la cuenta de Jev ni el costo previsto, repartido entre mundos y encuentros.
- **Diseño:**
  - `engine.RequestBudget`, confinado al hilo del servidor. Una sola instancia por arranque: una recarga de perfiles no lo recrea y no reinicia sus ventanas (plan §7).
  - `boolean tryReserve(UUID worldId, EncounterId encounter, int estimatedTokens, long nowNanos)` comprueba, en este orden:
    1. Solicitudes en vuelo del servidor < `maxInFlightRequests` (8).
    2. Solicitudes en los últimos 60 s del servidor < `maxRequestsPerMinute` (600, la mitad del límite publicado de 1 200; plan §9).
    3. Solicitudes en los últimos 60 s del mundo < `maxRequestsPerMinutePerWorld` (300).
    4. Tokens estimados en los últimos 60 s + `estimatedTokens` ≤ `maxInputTokensPerMinute` (600 000).
  - La regla de una solicitud en vuelo por encuentro la aplica `Encounter`; el intervalo mínimo entre decisiones, la `Agenda`.
  - Ventanas con 60 cubetas de un segundo en un anillo: memoria fija y operaciones O(1).
  - `void complete(InFlightRequest request, OptionalInt actualTokens, long nowNanos)`: libera el cupo en vuelo y corrige la cubeta del envío con la diferencia entre tokens reales y estimados. La solicitud sigue contando en la ventana aunque haya fallado o llegado tarde (plan §7).
  - Estimación de tokens: `base + porPregunta × preguntas + porParticipante × participantes`, con valores iniciales 400, 120 y 60. Tras cada `Answered`, una media móvil exponencial ajusta el costo por pregunta. **M13 mide** los valores reales.
  - Lectura para M12: `requestsLastMinute()`, `tokensLastMinute()` e `inFlight()`.
- **Archivos:** crear `engine/RequestBudget.java`; eliminar `adapter/mythicmobs/RemoteRequestBudget.java` y su prueba.
- **Reglas:** A5, C5, C6, C10.
- **Pruebas:** `RequestBudgetTest` con reloj controlable: cada límite por separado, ventana deslizante al pasar 60 s, corrección de tokens, una solicitud tardía que sigue contando, instancia que sobrevive a una recarga simulada, reparto entre dos mundos.
- **Aceptación:**
  - [x] Ningún escenario supera los límites configurados. `RequestBudgetTest` (cada límite por separado).
  - [x] Las ventanas no se reinician con una recarga. `CoordinatorTest.aProfileReloadNeitherRecreatesNorResetsTheBudget`.
  - [x] La estimación converge hacia los tokens reales en una secuencia de respuestas de prueba. `RequestBudgetTest.theEstimateConvergesTowardTheRealTokensPerQuestion`.

### M09.4 Estado del servicio y pausa creciente

- **Propósito:** reaccionar a fallos de Jev sin bloquear el combate y recuperar el servicio sin intervención manual cuando el fallo es transitorio.
- **Diseño:**
  - `engine.ServiceStatusTracker` guarda `ServiceState`, racha de fallos, `pausedUntilNanos`, período de prueba y la generación de comprobaciones (heredada de `ProviderHealthMonitor`).
  - Transiciones:

    | Evento | Resultado |
    |---|---|
    | Sin clave o sin modelo al arrancar | `UNCONFIGURED`; nunca se envía |
    | Configuración inválida | `INVALID_CONFIGURATION` |
    | Arranque configurado | `CHECKING` y `DecisionClient.check()` |
    | `check()` devuelve `READY` | `READY` en período de prueba: solo una solicitud en vuelo en todo el servidor hasta el primer `Answered` |
    | `Answered` | `READY`; racha a 0; fin del período de prueba |
    | `Failed` con `RATE_LIMITED` u `OVERLOADED` | `PAUSED` durante `retryAfter` o, si falta, la pausa calculada |
    | `Failed` con `NETWORK` o `SERVICE_ERROR` | `PAUSED` con pausa calculada |
    | `Failed` con `TIMEOUT` | `PAUSED` tras 2 timeouts seguidos; uno solo no pausa |
    | `Failed` con `INVALID_RESPONSE` | `PAUSED` tras 3 seguidos; uno solo se descarta |
    | `Failed` con `INVALID_REQUEST` | `PAUSED` con la pausa máxima y registro, porque indica un error propio |
    | `Failed` con `AUTHENTICATION` | `INVALID_CREDENTIALS`; bloqueado hasta `/thepredict check` o reinicio |
    | `Failed` con `MODEL_UNAVAILABLE` | `INVALID_MODEL`; bloqueado igual |
    | `Failed` por cancelación local | Sin efecto sobre el servicio |
    | Fin de la pausa | `CHECKING`, `check()` y, si responde `READY`, período de prueba |
    | `/thepredict check` (M12) | Nueva generación y `CHECKING` inmediato |

  - Pausa calculada: `min(60 s, 1 s × 2^(racha − 1))`, multiplicada por un factor aleatorio uniforme en [0,8; 1,2]. El generador aleatorio se inyecta para las pruebas. `retry-after` ya viene acotado a 300 s por M08.5.
  - `boolean allowsDispatch(long nowNanos)` es verdadero solo en `READY`, y en período de prueba solo si no hay otra solicitud en vuelo.
  - Una pausa no cancela solicitudes en vuelo: una respuesta válida que llegue durante la pausa se procesa normalmente.
  - Cada cambio de estado se registra una vez, con causa y sin detalles sensibles (C8).
- **Archivos:** crear `engine/ServiceStatusTracker.java`.
- **Reglas:** A6, A7, C6, C7, C8.
- **Pruebas:** `ServiceStatusTrackerTest`: cada fila de la tabla, crecimiento y tope de la pausa, `retry-after` que manda sobre el cálculo, período de prueba con una sola solicitud, comprobación vieja ignorada por generación.
- **Aceptación:**
  - [x] Un 429 con `retry-after: 2` reanuda los envíos tras 2 s y una comprobación correcta. Local: `ServiceStatusTrackerTest.aRateLimitWithRetryAfterTwoSecondsResumesAfterTwoSecondsAndACheck`; con el servicio real, pendiente (llamada de pago).
  - [x] Credencial o modelo inválidos no se reintentan solos. `ServiceStatusTrackerTest.rejectedCredentialsOrModelBlockUntilACheckIsRequested`.
  - [x] Ya no hace falta `/thepredict check` para recuperarse de fallos transitorios. `ServiceStatusTrackerTest` (pausa, comprobación y prueba).

### M09.5 Política de agenda

- **Propósito:** decidir cuándo pide decisión cada encuentro, sin inanición entre mundos ni ráfagas por eventos.
- **Diseño:**
  - `engine.Agenda` mantiene un montículo por tick de vencimiento con borrado perezoso, una cola FIFO por mundo y un cursor rotatorio entre mundos.
  - `schedule(EncounterId, UUID worldId, long dueTick)`, `advance(EncounterId, long earliestTick)`, `remove(EncounterId)` y `List<EncounterId> pollDue(long tick, int max)`.
  - `pollDue` mueve los encuentros vencidos a la cola de su mundo y reparte en ronda: un encuentro por mundo por vuelta hasta `max`. Los no atendidos siguen vencidos y tienen prioridad el siguiente tick.
  - Latido: el menor `heartbeatTicks` entre los perfiles del encuentro. Intervalo mínimo: el mayor `minimumDecisionIntervalTicks` entre esos perfiles.
  - Tras un envío: `due = tick + latido`.
  - Contexto pendiente: `due = max(últimoEnvío + intervaloMínimo, tick)`. Muchas señales para el mismo encuentro producen un solo adelanto.
  - Antes de enviar se omite el encuentro, con causa contada, si: el servicio no permite enviar, ya tiene una solicitud en vuelo, la captura está vacía o ningún miembro tiene al menos dos tácticas elegibles. Un miembro con una sola táctica elegible la resuelve M10 sin Jev.
  - Un encuentro cerrado se descarta al salir de la cola.
- **Archivos:** crear `engine/Agenda.java`.
- **Reglas:** A5, C10, C12.
- **Pruebas:** `AgendaTest`: latido regular, adelanto que respeta el intervalo mínimo, ráfaga de 100 señales que produce un adelanto, 50 encuentros en dos mundos con `max = 2` atendidos en ≤25 ticks alternando mundos, encuentro cerrado omitido.
- **Aceptación:**
  - [x] Ningún encuentro decide antes de su intervalo mínimo. `AgendaTest` (M06) y `agenda.decided` tras cada evaluación.
  - [x] Ningún mundo acapara la agenda. `AgendaTest.fiftyEncountersInTwoWorldsAreServedWithinTwentyFiveTicksAlternatingWorlds`.
  - [x] Todo encuentro vencido se atiende en un número acotado de ticks. `AgendaTest.noEncounterWaitsLongerThanItsHeartbeatPlusOneFullRound`.

### M09.6 Métricas de latencia y causas

- **Propósito:** medir cada tramo para ajustar límites (M13) y mostrarlos al administrador (M12).
- **Diseño:**
  - `engine.DecisionMetrics`, confinado al hilo del servidor. Los tiempos tomados en hilos HTTP viajan dentro de `CompletedRequest`.
  - Tramos: `CAPTURE` (duración de `CombatReader.capture`), `DISPATCH` (duración de la llamada a `decide`), `ROUNDTRIP` (de `sentAtNanos` a `completedAtNanos`; incluye codificación, red, modelo y parseo), `INBOX_WAIT` (de `completedAtNanos` al vaciado), `APPLY` (aceptación y publicación de intenciones en M10) y `TICK_WORK` (trabajo total del coordinador).
  - Histograma de cubetas fijas en microsegundos: 50, 100, 250, 500, 1 000, 2 000, 5 000, 10 000, 25 000, 50 000, 100 000, 200 000, 300 000, 500 000, 800 000, 1 200 000, 2 000 000 e infinito. p50, p95 y p99 se leen por conteo acumulado.
  - Ventana: dos histogramas que se alternan cada minuto (actual y anterior). Memoria fija.
  - Contadores por causa: `FailureKind`, motivos de omisión de la agenda, rechazos de la inbox, liberaciones por vigilancia, tokens reales, y los de M10 (`FallbackReason`, `ExecutionOutcome`, descartes).
  - Sin series por UUID (plan §12).
- **Archivos:** crear `engine/DecisionMetrics.java`.
- **Reglas:** A5, A7, C12.
- **Pruebas:** `DecisionMetricsTest`: percentiles con distribuciones conocidas, alternancia por minuto, contadores por causa.
- **Aceptación:**
  - [x] M12 puede mostrar p50, p95 y p99 de captura, HTTP, espera en inbox y aplicación. `DecisionMetrics.percentileMicros`; `DecisionMetricsTest`.
  - [x] La memoria de métricas no depende del número de mobs. Memoria fija por tramo y por causa (`DecisionMetricsTest`).

### M09.7 Transición y cableado

- **Propósito:** pasar el camino de ejecución al nuevo motor sin dejar dos coordinadores activos.
- **Diseño:**
  - `ThePredictPlugin.onEnable` construye, en este orden: `ServerClock`, `EngineSettings` (desde `config.DecisionSettings` y `config.JevSettings` de M03), `SystemOneClient` si hay configuración (si no, `DecisionClient.unavailable(check)`), registros de M06, `RequestBudget`, `ServiceStatusTracker`, `Agenda`, `Inbox`, `IntentBoard`, `ContextSignals`, `DecisionMetrics`, `DecisionAcceptor`, `DecisionDispatcher` y `Coordinator`.
  - Tarea única: `Bukkit.getScheduler().runTaskTimer(plugin, coordinator::tick, 1L, 1L)`.
  - `onDisable` sigue T7: cancelar la tarea, cancelar futures en vuelo, vaciar `IntentBoard`, llamar a `MobControl.release` para cada sesión y cerrar el cliente.
  - Se eliminan `MythicPredictionRunner`, `RotatingMobCursor`, `DecisionReuseCache`, `RemoteRequestBudget` y `ServerThreadInbox` con sus pruebas.
  - `OpenRouterDecisionProvider`, `DecisionProviderFactory` y `ProviderHealthMonitor` siguen compilando sin uso hasta M12 (W5).
- **Archivos:** modificar `ThePredictPlugin.java`; eliminar los cinco archivos citados y sus pruebas.
- **Reglas:** D8, T7, W5.
- **Pruebas:** la suite completa pasa sin las clases eliminadas; una prueba de integración local une `Coordinator`, `SystemOneClient` y el servidor de contrato de M08.8 con un `CombatReader` falso.
- **Aceptación:**
  - [x] Solo existe una tarea periódica del motor. `ArchitectureRulesTest.theEngineRunsOnOnePeriodicTaskScheduledByTheRoot`; en servidor, pendiente.
  - [x] Ninguna clase de producción referencia las cinco clases eliminadas. `ArchitectureRulesTest.noProductionSourceNamesAClassRetiredByTheCoordinator` (las cinco más `MobDecisionStates` y `PaperCombatSnapshotReader`).
  - [x] El apagado deja cero futures pendientes y cero vistas en `IntentBoard`. `CoordinatorTest.theShutdownLeavesNoPendingFutureNoResultAndNoView`.

### M09.8 Ensamblado y envío por encuentro

- **Propósito:** convertir un encuentro vencido en una sola solicitud con todas sus preguntas.
- **Diseño:**
  - `engine.DecisionDispatcher.dispatch(Encounter encounter, long tick, long nowNanos)`:
    1. `combatReader.capture(encounter.id(), members)`; vacío → omitir con causa.
    2. Por cada `MobFacts` de una sesión `ACTIVE` con al menos dos tácticas elegibles: pregunta `NOW` con opciones ordenadas por prioridad descendente y luego por ID.
    3. Contingencias, hasta `contingenciesPerMob` del perfil: `IF_TARGET_IN_MELEE` si el objetivo aún no está en `MELEE`; `IF_OWN_HEALTH_CRITICAL` si la salud aún no es `CRITICAL`. Usan las mismas opciones; M10.4 revalida al dispararse.
    4. Claves `q0`, `q1`… en el orden anterior.
    5. `RequestBudget.tryReserve` con la estimación de tokens; si falla, omitir con `NO_BUDGET`.
    6. `InFlightRequest` con encuentro, secuencia nueva, `catalogRevision`, preguntas por clave, `sentAtNanos`, `deadlineNanos = sentAtNanos + requestTimeout + 1 s` y tokens estimados.
    7. `decisionClient.decide(request)` y registro del callback que solo deposita en la inbox.
  - Si `decide` lanza (error de programación), se libera la reserva y se trata como `Failed(INVALID_REQUEST)`.
- **Archivos:** crear `engine/DecisionDispatcher.java` y `engine/InFlightRequest.java`.
- **Reglas:** I1, I3, T4, T5, C10.
- **Pruebas:** `DecisionDispatcherTest`: preguntas y contingencias correctas, orden determinista de opciones, miembro con una opción excluido, reserva fallida sin envío, excepción de `decide` que libera la reserva.
- **Aceptación:**
  - [x] Nunca hay dos solicitudes en vuelo para el mismo encuentro. `DecisionDispatcherTest.theCallbackOnlyDepositsTheResultInTheInboxAndNeverTwoRequestsFlyForOneEncounter`, `InFlightRegistryTest`.
  - [x] Una solicitud contiene todas las preguntas del encuentro y ninguna de otro. `DecisionDispatcherTest.aRequestHoldsTheQuestionsOfItsOwnEncounterOnly`.

## Riesgos y decisiones abiertas

- **Cerrado en M09 (predicado de alcance de `assign`):** `CombatReader` gana `boolean withinFollowRange(MobKey mob, MobKey member)` (00 §4, revisión 5). Lo implementa `CombatCapture` con el `FOLLOW_RANGE` nativo del mob, acotado por `decision.searchRadiusBlocks` como su radio de percepción: una distancia al cuadrado entre dos entidades del mismo mundo, sin ruta ni línea de visión. El coordinador y `debug capture` usan `miembro -> miembro == sesión || reader.withinFollowRange(sesión, miembro)`. Alternativas descartadas: posiciones en `engine` (rompe D2) o un dato de la captura (la captura solo existe al decidir y no tiene distancias entre mobs). Para formar el primer encuentro desde una sonda, `MobProbe` gana `worldId` (01, revisión 4).
- **Cerrado en M09 (objetivo no percibido):** no se pregunta a Jev por un miembro sin objetivo percibido (`MobFacts.target` vacío): Jev no puede razonar sobre un objetivo que el mob no percibe (percepción natural, plan §6) y cada pregunta cuesta. El encuentro conserva su latido y el jugador sigue en el índice; en cuanto la captura lo percibe, el miembro vuelve a tener pregunta. Mientras tanto la sesión sigue con su intención vigente o con el nivel que decida M10 (local o base).
- **Incorporado en 01 §8 (revisión 2):** nuevos tipos de `engine`: `EngineSettings` (límites recibidos de la raíz, porque `engine` no depende de `config`), `DecisionDispatcher`, `InFlightRequest`, `InboxEntry` con `CompletedRequest` y `CompletedCheck`, y `DecisionMetrics`. Motivo: C10 y la separación entre bucle, envío y métricas.
- **Incorporado en 01 §8 (revisión 2):** añadir `worldId` a `Encounter`, necesario para el presupuesto por mundo.
- **Incorporado en 00 §4 (revisión 2):** método estático `DecisionClient.unavailable(ServiceCheck)` para arrancar sin configuración sin usar `Optional` como parámetro (C4).
- Claves incluidas en M03 (revisión 2): `decision.maxRequestsPerMinutePerWorld` (300), `decision.maxInputTokensPerMinute` (600 000), `decision.maxEncounterEvaluationsPerTick` (2), `decision.maxCompletionsPerTick` (16), `decision.maxSignalsPerTick` (64), `decision.maxTickWorkMicros` (2 000) y `decision.maxInstallsPerTick` (10, ya propuesta por M05).
- La relación entre `maxTickWorkMicros` y el costo real de la captura en servidores cargados la **mide M13**; si la captura es cara, se baja `maxEncounterEvaluationsPerTick`.
- Los umbrales de pausa (2 timeouts, 3 respuestas inválidas) son puntos de partida; **M13 mide** su efecto.

## Terminado

- [x] M09.1 a M09.8 con sus aceptaciones marcadas (locales; lo que exige un servidor o la cuenta real está en "Pendiente de verificación").
- [x] `./gradlew clean build javadoc` verde; `ArchitectureRulesTest` confirma que `engine` no importa `config` ni adapters.
- [x] Prueba de integración local de extremo a extremo con el servidor de contrato (`CoordinatorContractTest`).
- [x] Ningún `Thread.sleep` en las pruebas del módulo.
- [x] Estado del módulo actualizado en [README.md](README.md).

## Estado de implementación

> **Local listo — servidor y cuenta real pendientes (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` (también con `--rerun-tasks`) pasa sin avisos de compilación ni de Javadoc: **1001 pruebas, 0 fallos** (930 de partida − 12 retiradas con sus clases + 74 nuevas + 7 de las correcciones tras la auditoría + 2 de la segunda ronda). Ninguna prueba sale de la máquina: el cliente real solo habla con el servidor de contrato en la interfaz de bucle local (`CoordinatorContractTest`), y nadie lee `.env` (W6). Ninguna prueba nueva usa `Thread.sleep` (P2). No se arrancó ningún servidor ni se hizo ninguna llamada a TypeSafe.

### Archivos

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…` salvo indicación) |
|---|---|
| M09.1 | Creados `engine/Coordinator`, `EngineSettings`, `SessionUpkeep` (pasos 3 a 5 y mantenimiento de la decisión), `ExecutionSteps` (frontera con M10) e `InstallQueue` (paquete privado). `SessionRegistry` mantiene la cola de instalación |
| M09.2 | Creados `engine/Inbox`, `InboxEntry`. Eliminados `adapter/paper/ServerThreadInbox` y su prueba; el paquete `adapter.paper` desaparece |
| M09.3 | Creado `engine/RequestBudget`. Eliminados `adapter/mythicmobs/RemoteRequestBudget` y su prueba |
| M09.4 | Creados `engine/ServiceStatusTracker` y `ServicePause` (paquete privado) |
| M09.5 | `engine/Agenda` de M06 sin cambios (ya implementa el diseño de M09.5, desviación 6 de M06) |
| M09.6 | Creados `engine/DecisionMetrics` y `LatencyWindow` (paquete privado); `EncounterCapture` mide en `DecisionMetrics` (tramo `CAPTURE`) |
| M09.7 | Creado `DecisionServices` (raíz). Modificados `ThePredictPlugin` (metas antes que sesiones, cliente único, coordinador, una tarea, T7), `SessionServices` (accesos, captura de depuración sin `Agenda.decided`, resumen con p95), `SystemOneWiring` (`initialState`, `createForStart`), `StatusSnapshot` (Javadoc), `DebugCommandHandler` y `admin/DebugRequest`, `DebugCommandParser` (`debug signals` solo lee contadores: `ShowSignals`). Eliminados `adapter/mythicmobs/MythicPredictionRunner`, `RotatingMobCursor`, `DecisionReuseCache`, `MobDecisionStates`, `adapter/paper/PaperCombatSnapshotReader` y sus pruebas. `adapter/server/context/NearestK` vuelve a paquete privado |
| M09.8 | Creados `engine/DecisionDispatcher`, `InFlightRequest`, `InFlightRegistry`, `ResultIntake`, `EncounterQuestions` y `EncounterSchedule` (paquete privado: secuencia, solicitud en vuelo, contexto pendiente, siguiente y última decisión, versión de agenda y desplazamiento de rutas, fuera de `Encounter`) |
| Puerto (00 rev. 5, 01 rev. 4) | `engine/CombatReader.withinFollowRange`; `adapter/server/context/CombatCapture` lo implementa y su sonda lleva el mundo; `domain/MobProbe` gana `worldId` |
| Pruebas | Nuevas: `engine/CoordinatorTest` (22), `DecisionDispatcherTest` (10), `ServiceStatusTrackerTest` (15), `RequestBudgetTest` (10), `InboxTest` (4), `DecisionMetricsTest` (4), `EngineSettingsTest` (2), `InFlightRegistryTest` (2), `CoordinatorFixture` (dobles de los puertos y reloj controlable), `DecisionServicesTest` (4), `adapter/jev/CoordinatorContractTest` (1). Ampliadas: `SystemOneWiringTest` (+3), `CombatCaptureTest` (+2), `ArchitectureRulesTest` (+2: ninguna fuente nombra una clase retirada; una sola tarea periódica), `FailureClassifierTest` (+1: los fallos locales del cliente son los del tracker), `MobProbeTest` (+1). Adaptadas: `EncounterCaptureTest`, `SessionServicesTest`, `ContingencyPolicyTest`, `DebugCommandParserTest`. Eliminadas: `RotatingMobCursorTest`, `DecisionReuseCacheTest`, `RemoteRequestBudgetTest`, `MobDecisionStatesTest`, `ServerThreadInboxTest` (12 pruebas; sus escenarios están en `RequestBudgetTest` e `InboxTest`) |

### Orden real del tick

1. Resultados (`maxCompletionsPerTick`). 2. Vigilancia y, si toca, comprobación del servicio. 3. Señales (`maxSignalsPerTick`). 4. Sondas (`maxProbesPerTick`): colocación por objetivo con `assign` y ciclo reactivo de M10. 5′. Ciclo de vida de M06 (escaneo, liberaciones y reconciliación, 32 por tick cada uno). 5. Instalaciones (`maxInstallsPerTick`). 6. Decisiones (`maxEncounterEvaluationsPerTick`): preparar (recolocar miembros, comprobar metas), capturar, preguntar si procede, `ExecutionSteps.evaluated` (M10), cerrar si terminó el combate y, pase lo que pase, `decided` salvo que se haya cerrado. 7. `TICK_WORK`. Con `maxTickWorkMicros` gastado, 5 y 6 esperan al siguiente tick (se cuenta en `DEFERRED_TICKS`); 1 a 5′ siempre corren.

### Desviaciones

| # | Desviación | Motivo |
|---|---|---|
| 1 | `CombatReader.withinFollowRange(MobKey, MobKey)` (00 §4, revisión 5) | Decisión de puerto que M07 dejó a M09 ("Riesgos"). Es lo más nativo y directo: el `FOLLOW_RANGE` del mob, que MythicMobs fija con `Options.FollowRange`, acotado como la percepción; una distancia O(1), sin ruta ni línea de visión |
| 2 | `MobProbe` gana `worldId` (01, revisión 4) | `assign` necesita el mundo y, hasta M09, solo `debug capture` lo conocía (por el comando). La sonda ya lee la entidad, así que el mundo no cuesta nada |
| 3 | No se pregunta por un miembro sin objetivo percibido, ni por uno cuyas metas no están instaladas (`installPending` o sin `installedRevision`, por ejemplo `Unsupported`) | Objetivo no percibido: decisión abierta por M07 (ver "Riesgos"). Sin metas instaladas ThePredict no puede mover al mob, así que la respuesta se pagaría sin efecto |
| 4 | La captura se hace en toda evaluación, antes de comprobar servicio y presupuesto; el servicio no disponible, la falta de presupuesto y la ausencia de preguntas solo omiten el envío (causas contadas en `DecisionDispatcher.Outcome`) | El modo local de M10.6 usa "las tácticas elegibles de la última captura" y el cierre de M06.4 necesita los objetivos que registra la captura, también sin Jev. Coste de CPU acotado por `maxEncounterEvaluationsPerTick` y por el latido |
| 5 | `closeIfFinished` se llama después de la captura de cada evaluación, no antes | Antes de su primera captura un encuentro nuevo aún no tiene objetivos registrados y se cerraría al instante; después, un combate terminado se cierra en su primer latido (`CoordinatorTest.anEncounterClosesAtTheFirstHeartbeatAfterItsCombatEnded`) |
| 6 | `Agenda.decided` tras cada evaluación, haya envío o no | Un encuentro omitido no vuelve cada tick; el intervalo mínimo y el latido cuentan desde la última evaluación, que es también la última captura |
| 7 | `dispatch(Encounter)` sin `tick` ni `nowNanos` | El tiempo de envío se lee del reloj justo antes de `decide`, así `ROUNDTRIP` empieza en el envío real; la reserva del presupuesto usa ese mismo instante. La rotación de rutas ya no depende del tick (desviación 8) |
| 8 | `DecisionDispatcher.PATH_CHECKS_PER_EVALUATION = 2` constante, en rotación por un desplazamiento propio de cada encuentro (`Encounter.pathOffset`, que avanza tras cada captura con permisos); ningún permiso mientras el servicio no permite enviar | M07 pide "un conjunto de rutas acotado por tick" sin clave de configuración: como máximo 2 × `maxEncounterEvaluationsPerTick` rutas por tick, y cada miembro calcula ruta en `ceil(miembros / 2)` evaluaciones sea cual sea el latido. Sin Jev (sin clave, en pausa o bloqueado) la alcanzabilidad conserva su último valor conocido (M07.4): no la leen el cierre de M06.4 ni la elegibilidad de M10.6 (`Eligibility` no usa `reachable`); si M11 necesita alcanzabilidad fresca sin Jev para su decaimiento por inalcanzable, lo revisa allí (revisado en M11, desviaciones 4 y 5: no hace falta, la ruta en curso del navegador es fresca). M13 lo mide |
| 9 | `RequestBudget` también limita `maxInFlightRequestsPerWorld` (clave de M03, 4) y rechaza una segunda reserva del mismo encuentro | La clave existe y no la usaba nadie; la segunda comprobación hace cumplir "una en vuelo por encuentro" también en el presupuesto |
| 10 | `InFlightRequest` gana mundo, participantes y `captureNanos` | Presupuesto por mundo al liberar, aprendizaje de tokens por pregunta y frescura de M10.1 |
| 11 | Tipos nuevos de `engine`: `InFlightRegistry`, `ResultIntake`, `SessionUpkeep`, `ExecutionSteps` (01 §8, revisión 4) y auxiliares de paquete privado `InstallQueue`, `ServicePause`, `LatencyWindow`, `EncounterQuestions`, `EncounterSchedule` | C10: el bucle, la entrada de resultados, el mantenimiento de sesiones, las futures en vuelo y la frontera con M10 son responsabilidades distintas; `Coordinator` y `DecisionDispatcher` quedan sin estado propio (salvo el tick del último aviso) |
| 12 | `ExecutionSteps` con tres ganchos (`accept`, `react`, `evaluated`); `untilExecution()` hasta M10: una respuesta se cuenta y se mide pero no cambia ninguna intención, una sonda no ejecuta contingencias y una evaluación no lee progreso | M10 implementa `DecisionAcceptor` y `ReactiveLayer`; M09 solo los invoca (alcance). Consecuencia importante en "Pendiente" |
| 13 | Paso 5′ de ciclo de vida (escaneo, liberaciones y reconciliación de M06) entre los pasos 4 y 5, siempre ejecutado | M06 pide que el coordinador llame a `SessionLifecycle.tick`; cada parte ya tiene su tope (32 por tick) y liberar metas protege el control (A6) |
| 14 | La comprobación del servicio arranca en el paso 2 del primer tick, no dentro de `onEnable` | Una sola tarea y un solo punto que toca el servicio; llega ≤50 ms después del arranque. Una comprobación sin resultado se repite tras `requestTimeout + 1 s` |
| 15 | `Answered` solo cambia el estado desde `READY` (fin del período de prueba y de la racha); durante `PAUSED` o `CHECKING` no reanuda | "Una pausa no cancela solicitudes en vuelo": la respuesta se procesa, pero no anula un `retry-after`. La racha de pausas crece una vez por pausa; los fallos de otras solicitudes en vuelo solo la alargan |
| 16 | Fallos locales = detalles fijos de M08 (`cancelled`, `client queue is full`, `client closed`) en `ServiceStatusTracker.LOCAL_FAILURE_DETAILS` | M08 pide tratar la cola llena como fallo local y `FailureKind` no distingue el origen; `FailureClassifierTest` comprueba que las constantes del cliente son esas tres |
| 17 | La vigilancia entrega a `ExecutionSteps.accept` un `TIMEOUT` sintético y los resultados tardíos también llegan a `accept`; métricas y presupuesto solo cuentan cada solicitud una vez | M10.6 reacciona "tras cada `Failed`" y M10.1 descarta los tardíos por identidad |
| 18 | `EngineSettings` se construye desde `DecisionSettings` y `JevSettings` (M09.7), no desde `PluginSettings.timing()` | El latido, intervalos y contingencias ya viajan en el `DecisionTiming` de cada perfil (M03); el motor los lee de los perfiles y ningún valor global lo necesita |
| 19 | Métricas de captura de M07 en `DecisionMetrics` (`CAPTURE`); la línea `Sessions` dice `captures N (p95 … us, max … us)` en vez de `last`; `debug capture` mide su propia captura y ya no llama a `Agenda.decided` | Pendientes de M07; una sola fuente de métricas por tramo |
| 20 | `debug signals` solo muestra `pending` y `dropped` (`DebugRequest.ShowSignals`) | Pendiente de M04: el coordinador es el único consumidor de `ContextSignals` |
| 21 | Siguen compilando sin uso (W5): `ProviderHealthMonitor`, `DecisionProviderFactory`, el proveedor OpenRouter, `RuntimeSettings`, `decision.PredictionPlanner`/`DecisionPlan` y `snapshot` | 00 §10 asigna el retiro de `PredictionPlanner` a M09, pero `DecisionLifecycleSuiteTest` (camino OpenRouter) los usa y `decision.DecisionRequest` usa `snapshot`: se van con OpenRouter en M12. `DecisionFreshness`, `MythicSkillExecutor` y el `BoundProfile` antiguo son de M10 |
| 22 | `StatusSnapshot` conserva `ProviderHealth`, derivado del estado del tracker (`PAUSED` → `UNAVAILABLE`); `Remote usage` muestra solicitudes, tokens y en vuelo del presupuesto; `/thepredict check` pide una comprobación nueva (fila de M12 de la tabla de M09.4) | Presentación es de M12; sin `/thepredict check` un bloqueo por credencial solo se levantaría reiniciando |
| 23 | Un `decide` que devuelve `null` (violación del contrato) se trata como uno que lanza: reserva liberada, `INVALID_REQUEST`, `CLIENT_ERROR`. Una excepción de `ensureInstalled` cuenta como `Unsupported` más `INTERNAL_ERROR`; un `Unsupported` publica `IntentView.base` y suelta la intención; un `Deferred` se reintenta desde el tick siguiente | A6; "deja la sesión en BASE"; reintentar en el mismo tick gastaría el tope en un mob no cargado |
| 24 | C10: `ServiceStatusTracker` 237 líneas, `SessionUpkeep` 232, `SessionRegistry` 234, `RequestBudget` 225, `Encounter` 230 (270 antes: los datos de programación pasaron a `EncounterSchedule`), `SessionServices` 240 y `ThePredictPlugin` 428 (385 antes) | Tracker: la tabla completa de M09.4 en una sola máquina de estados (la pausa ya salió a `ServicePause`). Upkeep: pasos 3 a 5 y la preparación comparten colocar, instalar y degradar una sesión. Registry: +12 líneas de la cola de instalación. Budget: incluye su ventana de 60 cubetas como clase anidada y la poda de mundos. Encounter bajó de 270 a 230 líneas al extraer `EncounterSchedule`, antes de que M10 y M11 añadan campos. Raíz: composición heredada que M12 reduce; ningún método nuevo pasa de 30 líneas |
| 25 | `DecisionServices` (raíz, paquete privado) compone los componentes de M09 sobre los de `SessionServices`; `DecisionServices.UnavailableMobControl` sustituye a `GoalOwnership` si no arrancó | Mismo criterio que la desviación 25 de M06 (D8): solo `ThePredictPlugin` la crea, sin estado estático; si las metas no arrancan ningún mob se instala y todos conservan su IA (A6) |

### Correcciones tras la auditoría (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **999 pruebas, 0 fallos** (7 nuevas). Sin llamadas de red ni lectura de `.env`.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** la rotación de rutas `tick % miembros` se alineaba con el latido de 20 ticks: con 4, 5 o 10 miembros siempre calculaban ruta los mismos 2 y con 8 o 16 la mitad nunca, así que su `reachable` quedaba viejo | Desplazamiento propio del encuentro (`Encounter.pathOffset`, `advancePathOffset`) que avanza tras cada captura con permisos | `CoordinatorTest.everyMemberComputesAPathWithinHalfItsCountOfHeartbeatSpacedEvaluations` (4, 5 y 8 miembros, evaluaciones separadas 20 ticks) |
| **Bloqueante (A5):** el mapa de mundos de `RequestBudget` no se podaba y crecía con mundos de instancia | `tryReserve` olvida cada mundo sin solicitudes en vuelo y con la ventana vacía | `RequestBudgetTest.aWorldIsForgottenOnceNothingIsInFlightAndItsWindowIsEmpty` |
| **Bloqueante (C7):** `closeIfFinished` y `decided` estaban fuera del `try` por encuentro: una excepción de `inCombat` abortaba el paso y el encuentro quedaba el primero de su mundo, capturado cada tick | `Coordinator.evaluate` con `try`/`catch`/`finally`: `decided` en el `finally` salvo encuentro cerrado; el fallo se cuenta y se registra con límite | `CoordinatorTest.aFailingClosingCheckStillSchedulesTheEncounterAndTheOthersAreEvaluated` |
| **Bloqueante (C7):** si `accept` lanzaba en la vigilancia, las solicitudes vencidas siguientes no liberaban su presupuesto | `ResultIntake.expire` libera primero el presupuesto de todas y después llama a cada `accept` en su propio límite; lo mismo en `drain`; el primer fallo se relanza al final para que el coordinador lo cuente y registre | `CoordinatorTest.aFailingAcceptanceOfOneExpiredRequestNeitherLeaksTheBudgetNorSkipsTheOthers`, `aFailingAcceptanceOfOneAnswerDoesNotSkipTheNextAnswersOfTheTick` |
| **Bloqueante:** documentos: 00 §10 decía que M09 retira `PredictionPlanner`; M12 no listaba lo conservado por la desviación 21; M07 daba por retirado `snapshot` | 00 §10: "M12 retira"; M12.8 lista `PredictionPlanner`, `DecisionPlan`, `decision.DecisionRequest`, `DecisionProvider`, `snapshot`, `RuntimeSettings` y sus pruebas; M07 corrige la base existente y el pendiente | — |
| **Bloqueante:** M10 describía `ReactiveLayer.run` con su propia sonda y no había gancho para `NO_PROGRESS` por evaluación | `ExecutionSteps.evaluated(Encounter, tick, nowNanos)`, sin efecto hasta M10, llamado una vez por evaluación; M10 gana la sección "Frontera con M09" y M10.1, M10.4 y M10.6 usan las firmas reales | `CoordinatorTest.theEvaluationHookOfM10RunsOncePerEvaluation` |
| Rutas pedidas sin Jev | Sin permisos de ruta mientras `allowsDispatch` es falso (desviación 8) | `DecisionDispatcherTest.noMemberComputesAPathWhileJevCannotBeAskedAndAtMostTwoDoOtherwise` |
| `decide` que devuelve `null` | Se trata como fallo del cliente y libera la reserva (desviación 23) | `DecisionDispatcherTest.aClientThatReturnsNoFutureIsTreatedLikeOneThatThrows` |
| Documentación pendiente | M12: sacar `jevHealth`/`reportJevStatus` de la raíz y nota W3 sobre el `README.md` de la raíz, que aún describe OpenRouter. M13: medir la inanición por `maxTickWorkMicros` (se comprueba antes de cada evaluación, no durante) y la ventana efectiva de 59–60 s | — |

### Segunda ronda de correcciones (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **1001 pruebas, 0 fallos** (2 nuevas). Sin llamadas de red ni lectura de `.env`.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| M10.1 pedía informar al tracker de un `Failed` (doble cuenta) y medía la frescura con `facts.captureNanos()` | M10.1: M10 no informa al tracker; frescura con `completed.request().captureNanos()` | — |
| Un envío o un gancho `evaluated` que fallaba impedía el cierre por fin de combate | `Coordinator.evaluate`: envío y `evaluated` en un límite, `closeIfFinished` en otro, `decided` en el `finally`; M10 dice que `evaluated` no se llama si la evaluación falló antes | `CoordinatorTest.aFailingEvaluationHookNeverKeepsAFinishedEncounterOpen` |
| `Encounter` de 281 líneas antes de M10 y M11 | Datos de programación extraídos a `EncounterSchedule` (paquete privado, accesible con `Encounter.schedule()`); `Encounter` queda en 230 líneas; API pública sin cambios (`contextPending()` delega); 01 §8 actualizado | Suite existente (`AgendaTest`, `InFlightRegistryTest`, `EncounterRegistryTest`, `DecisionDispatcherTest`) |
| Sin nota de revisión para el cambio de 00 §10 | 00, revisión 6 | — |
| Falta una prueba de `complete` tras olvidar un mundo | Nueva prueba | `RequestBudgetTest.aCompletionAfterItsWorldWasForgottenNeitherThrowsNorCountsTwice` |

### Pendiente de verificación en servidor

- Una sola tarea del motor y su coste por tick (`TICK_WORK`) con mobs reales; relación entre `maxTickWorkMicros` y el coste real de la captura (M13).
- Instalación de metas en el paso 5 tras cargar un chunk con mobs vinculados, y autocomprobación antes de decidir con metas retiradas por una skill (DT-05, DT-15).
- `withinFollowRange` con `FOLLOW_RANGE` real (`Options.FollowRange`) y encuentros de varios mobs contra un jugador.
- Sondas cada 2 ticks y cierre de encuentros con el `CombatTracker` real (300 ticks tras el último daño) y la tabla de amenaza.

### Cuenta real (llamadas de pago aprobadas por el usuario; las ejecutó el orquestador en M13)

- [x] **Comprobación real y decisión real con el cliente que usa el coordinador** (verificado con la cuenta real 2026-09-29; [comprobación](evidencia/live-check-2026-09-29.md), [decisiones](evidencia/live-scenario-bench-2026-09-29.md)): 20 de 20 `GET /v1/models` en `READY` y 1 200 de 1 200 `POST /v1/systemone` respondidas, modelo efectivo `jev-1.13.0`. Las herramientas de M13 llaman a `SystemOneClient` directamente; el mismo cliente dentro del coordinador, en juego, queda para el servidor (guion 0.0.1, §5.2 variante 5.B y §6.2).
- [x] **Latencia de ida y vuelta real desde la máquina de desarrollo** (verificado con la cuenta real 2026-09-29): p50 179 ms, p95 228 ms, p99 328 ms, máximo 513 ms, por debajo de `timeoutMillis: 800`. La del host del servidor (`ROUNDTRIP` de `status`) sigue pendiente.
- [x] **`usage.input_tokens` frente a la estimación** (verificado con la cuenta real 2026-09-29): 735 800 tokens en 1 200 llamadas, 613 por llamada, con escenarios de una pregunta y uno o dos participantes; la estimación de M09 da 590 de media en esos escenarios (580 con un participante, 640 con dos), un 4 % menos. Los valores base (400, 120, 60) **no cambian**: todos los escenarios tienen una sola pregunta, así que la evidencia no separa el coste por pregunta del coste base, y la media móvil de `RequestBudget` ya corrige el coste por pregunta con cada respuesta real (converge hacia unos 150 para estos escenarios). Los escenarios semilla no llevan los textos de `PredictValues`; las solicitudes reales del servidor serán algo mayores.
- [ ] **429 y 529 con `retry-after` del servicio**: no ocurrieron.

### Qué hace el plugin con Jev desde M09

Con una clave (`TYPESAFE_API_KEY` o `jev.apiKey`): al habilitar crea un único `SystemOneClient` sin abrir conexión; en el primer tick hace un `GET /v1/models` (sin inferencia); si es `READY`, en cuanto un mob vinculado con `Predictor: true` tiene metas instaladas y un jugador objetivo percibido, su encuentro envía un `POST /v1/systemone` con todas sus preguntas (por defecto `NOW` más una contingencia por mob), primero una sola solicitud en todo el servidor (período de prueba) y después como máximo una por encuentro por latido (20 ticks) y los topes de M03 (600/min, 300/min por mundo, 8 en vuelo, 4 por mundo, 600 000 tokens estimados/min). Ante fallos pausa con `retry-after` o pausa creciente y vuelve a comprobar con `GET /v1/models`. **Hasta M10 las respuestas se cuentan y se miden, pero no cambian el comportamiento de ningún mob**, así que cada solicitud se paga sin efecto visible. Sin clave no hay ninguna llamada.

### Pendiente para módulos posteriores

- M10 (hecho, ver su estado de implementación): implementar `ExecutionSteps` con `DecisionAcceptor` (aceptación, publicación, coordinación) y `ReactiveLayer` (contingencias, alternativas, fallback, `NO_PROGRESS` con `MobControl.progress`), contar `FallbackReason`, `ExecutionOutcome` y `DiscardReason` con `DecisionMetrics.increment` y medir `APPLY`; decidir el nivel mientras falta la primera respuesta (local o base). Hasta entonces conviene no poner la clave en un servidor en vivo.
- M12: mostrar p50/p95/p99 por tramo, contadores por causa, `ServiceStatusTracker.status()` y el presupuesto en `/thepredict status`; resultado de `/thepredict check`; retirar `ProviderHealth`, `ProviderHealthMonitor`, OpenRouter, `decision`, `snapshot` y `RuntimeSettings`. **Hecho en M12** (ver su [estado de implementación](M12-Diagnostico-instalacion-retiro-OpenRouter.md#estado-de-implementación)).
- M13: medir `PATH_CHECKS_PER_EVALUATION`, los valores base de la estimación de tokens, los umbrales de pausa (2 timeouts, 3 respuestas inválidas), `maxTickWorkMicros` frente al coste de captura y la fragmentación de encuentros con `withinFollowRange`. **Hecho en local en [M13](M13-Suite-carga-latencia-calidad.md#estado-de-implementación)** (ventana de 59–60 s, umbrales de pausa y presupuesto de trabajo con capturas y sondas simuladas; lo que exige servidor o cuenta queda en su lista). Dos correcciones del paso 6: `Coordinator.decide` pide a la agenda un encuentro cada vez, porque pedir dos y aplazar el segundo dejaba siempre primero al mismo mundo y, con capturas que gastan el presupuesto del tick, el otro mundo no se evaluaba nunca (corrección 2 de M13, `CoordinatorWorkBudgetTest`); y `SessionUpkeep.prepare` recoloca por la sonda nueva (corrección 1). Tras la auditoría de M13, `DEFERRED_TICKS` solo cuenta los ticks en que un encuentro vencido o una instalación esperaban (`Agenda.hasDue`, corrección 4).
