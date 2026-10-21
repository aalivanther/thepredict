# M10. Ejecución táctica y cadena de fallback

> **Estado:** Local listo — servidor y cuenta real pendientes (ver [Estado de implementación](#estado-de-implementación)) · **Entrega:** D · **Depende de:** M04, M05, M09 · **Desbloquea:** M11 · **Diseño:** [plan §5](../Plan-Jev-y-combate-tactico.md#5-control-de-movimiento-y-habilidades), [plan §6](../Plan-Jev-y-combate-tactico.md#probabilidades-confianza-e-histéresis), [plan §7](../Plan-Jev-y-combate-tactico.md#aceptación-de-una-respuesta), [plan §8](../Plan-Jev-y-combate-tactico.md#cadena-de-fallback) · **Reglas:** [00](00-Arquitectura-y-reglas.md) · **Dominio:** [01](01-Modelo-de-dominio.md)

## Nota de M20 (revisión de coherencia, 2026-10-06)

`FallbackStep` aplica `candidates.get(0)` (en el código, `candidates().getFirst()`) cuando la sesión guarda un `CandidateSet`. Hoy ninguna captura lo guarda, así que el planificador local sigue siendo el nivel `LOCAL`. La `HeldIntent` de M20.7 irá antes que esa candidata 0 cuando M28.8 y M29.4 introduzcan el tipo. Este módulo no la implementa (errata G5, E20-1).

## Objetivo

Convertir respuestas de Jev en control real del mob: aceptar solo respuestas vigentes, publicar la intención que leen las condiciones de MythicMobs, lanzar la skill asociada como máximo una vez y degradar por los cuatro niveles del fallback sin dejar nunca un mob congelado. Al cerrar el módulo, el mismo boss combate con Jev, sin Jev y durante la pérdida de Jev.

## Alcance

### Incluye

- Aceptación por pregunta con identidad, frescura y revalidación.
- Creación y publicación de intenciones, con señales de telegrafía.
- Ejecución única de la skill con revalidación de invulnerabilidad y escudo.
- Capa reactiva cada 2 ticks: contingencias, alternativas, progreso y vencimiento.
- Confianza e histéresis de extremo a extremo.
- Cadena de fallback con modo local.
- Coordinación entre mobs de un mismo encuentro.
- `FLANK` y ataque vanilla declarado.

### No incluye

- Reglas puras (`IntentPolicy`, `ContingencyPolicy`, `FallbackPolicy`, `LocalPlanner`, `Eligibility`): se definen en M02; M10 las usa y las prueba de extremo a extremo.
- Instalación de metas, `FlankGoal` y `ShieldGeometry`: M05.
- Condiciones, placeholders e `IntentBoard`: M04.
- Bucle, presupuesto y envío: M09.
- Amenaza enriquecida: M11.
- Cancelar skills ya iniciadas: MythicMobs las termina (plan §5).

## Base existente

| Archivo | Acción |
|---|---|
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/MythicSkillExecutor.java` | Se mueve a `adapter/server/execution/`. Se conservan `isUsable`, `executeIfUsable` y el trigger `THEPREDICT` creado con `SkillTrigger.create`. |
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/DecisionFreshness.java` | Su regla (mundo, objetivo y edad) pasa a `engine.DecisionAcceptor`. Se elimina con su prueba. |
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/MythicPredictionRunner.java` | Referencia de `finish` y `apply`: identidad por future, revalidación antes del cast y registro con límite. M09 lo elimina. |
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/MobDecisionStates.java` | Su `nextChoiceTick` (cooldown extra por instancia) pasa a `MobSession` como compatibilidad de `extraCooldownTicks` (M06). |
| `src/test/java/akyrion/thepredict/adapter/mythicmobs/DecisionFreshnessTest.java` | Sus casos pasan a `DecisionAcceptorTest`. |

## Frontera con M09 (código existente)

M09 ya ejecuta el bucle y llama a M10 solo a través de `engine.ExecutionSteps`; M10 sustituye `ExecutionSteps.untilExecution()` en `DecisionServices.Wiring` por su implementación (por ejemplo una clase que delega en `DecisionAcceptor` y `ReactiveLayer`). Firmas vigentes, todas en el hilo del servidor:

| Método | Lo llama | Cuándo | Qué ya hizo M09 antes |
|---|---|---|---|
| `accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos)` | `ResultIntake`, paso 1 y paso 2 | Cada resultado drenado de la inbox, **también los tardíos** (ya no en vuelo), y un `Failed(TIMEOUT)` sintético por cada solicitud liberada por la vigilancia | Liberó el presupuesto (una vez por solicitud), informó al `ServiceStatusTracker`, contó el resultado y midió `ROUNDTRIP` e `INBOX_WAIT`; mide `APPLY` alrededor de `accept`. Una excepción de `accept` no salta a los demás resultados: se cuenta y se registra con límite |
| `react(MobSession session, MobProbe probe, long tick, long nowNanos)` | `SessionUpkeep.probes`, paso 4 | Una vez cada 2 ticks por sesión activa (`Agenda.dueProbes`, escalonado por paridad), **solo si la sonda devolvió datos** | Llamó a `CombatReader.probe`; colocó la sesión por su objetivo (`EncounterRegistry.assign` con `withinFollowRange`) y marcó contexto pendiente si el objetivo cambió dentro del encuentro. Una sonda vacía no llega a M10: la sesión queda para la reconciliación de M06.6. Una excepción de `react` deja la sesión en `BASE` con `INTERNAL_ERROR` (`SessionUpkeep`) |
| `evaluated(Encounter encounter, long tick, long nowNanos)` | `Coordinator`, paso 6 | Una vez por evaluación del encuentro, tras su captura y su envío (u omisión) y antes de la comprobación de cierre | Preparó el encuentro (recolocó miembros, comprobó metas con `ensureInstalled`) y capturó; los hechos de cada miembro están en `MobSession.lastMobFacts()`. **No se llama** si la evaluación falló antes (una excepción de la captura o del envío): esa evaluación no cuenta para `NO_PROGRESS`. El cierre por fin de combate corre en su propio límite después, falle o no `evaluated` |

Además: `DecisionMetrics.increment(FallbackReason | ExecutionOutcome | DiscardReason)` ya cuenta las causas de M10; las preguntas solo incluyen miembros con metas instaladas, objetivo percibido y dos tácticas elegibles; mientras falta la primera respuesta la sesión tiene la vista `BASE` publicada al abrirse.

## Subpuntos

### M10.1 Aceptación de respuestas

- **Propósito:** aplicar una respuesta solo a la misma encarnación, catálogo y contexto para los que se pidió (plan §7).
- **Diseño:**
  - `engine.DecisionAcceptor.accept(CompletedRequest completed, long tick, long nowNanos)` se ejecuta en el hilo del servidor desde `ExecutionSteps.accept` (pasos 1 y 2 de M09.1). Recibe también los resultados tardíos y los `TIMEOUT` sintéticos de la vigilancia: un resultado cuya solicitud ya no es la vigente del encuentro se descarta con `STALE_SEQUENCE`.
  - Comprobaciones de la respuesta completa, en orden:
    1. El encuentro existe y su secuencia vigente es `completed.request().sequence()`.
    2. `catalogRevision` coincide con la actual.
    3. Modelo: si el configurado es un ID versionado, `effectiveModel` debe ser igual. Si es un alias, el primer `effectiveModel` recibido se fija como esperado; un cambio posterior descarta esa respuesta, registra el cambio una vez y fija el nuevo modelo (plan §6: los umbrales se recalibran por versión en M13).
    4. Frescura: `nowNanos − completed.request().captureNanos() ≤ maxDecisionAgeNanos`, por defecto `requestTimeout + 200 ms`. `InFlightRequest.captureNanos` es el instante de la captura de la que salió la solicitud (M09.8); la `CompletedRequest` no lleva los hechos.
  - Comprobaciones por pregunta:
    1. `SessionRegistry.find(MobKey)` encuentra la sesión con la misma generación y estado `ACTIVE`.
    2. `CombatReader.probe(mob)` devuelve datos y su `targetId` coincide con `MobSession.lastTargetId`; si no, se descarta con `TARGET_CHANGED`.
    3. Las respuestas de contingencia se agrupan con la pregunta `NOW` del mismo mob.
  - Cada descarte registra una causa enumerada y se cuenta en `DecisionMetrics` (A7).
  - `Failed` no crea intenciones y M10 **no** informa al `ServiceStatusTracker`: `ResultIntake` de M09 ya lo hizo antes de `accept` y hacerlo otra vez contaría dos veces el fallo. Las sesiones siguen con su intención vigente o pasan por M10.6.
  - Elegibilidad actual: las `eligibleTactics` de la captura que originó la solicitud, guardadas en la sesión. La revalidación completa ocurre al lanzar la skill (M10.3).
- **Archivos:** crear `engine/DecisionAcceptor.java`; eliminar `adapter/mythicmobs/DecisionFreshness.java` y su prueba.
- **Reglas:** A6, A7, I1, I11, T1, C7.
- **Pruebas:** `DecisionAcceptorTest` con dobles: secuencia vieja, generación distinta, sesión cerrada, catálogo recargado, modelo versionado distinto, alias que cambia, respuesta demasiado vieja, objetivo cambiado, respuesta duplicada, una pregunta válida y otra descartada en la misma respuesta.
- **Aceptación:**
  - [x] Ninguna respuesta se aplica a otra generación, catálogo o encuentro. `DecisionAcceptorTest` (`anAnswerForAnOlderGenerationIsDiscarded`, `anAnswerBuiltForAnOlderCatalogIsDiscarded`, `aSessionReopenedAtGenerationZeroInAnotherEncounterIsDiscardedByEncounterIdentity`, `aLateAnswerOfAnOlderSequenceIsDiscardedAfterTheWatchdogReleasedIt`).
  - [x] Una respuesta duplicada se descarta la segunda vez. `DecisionAcceptorTest.aDuplicateAnswerIsDiscardedTheSecondTime`.
  - [x] Cada descarte tiene causa enumerada y contador. `DecisionAcceptorTest` cubre las ocho causas de `DiscardReason`; `everyDiscardIsCountedByItsCauseAndLoggedOnlyWithDecisionLogging`.

### M10.2 Creación y publicación de intenciones

- **Propósito:** que una respuesta aceptada cambie el comportamiento en ≤2 ticks y que el cambio sea visible y explicable.
- **Diseño:**
  - Para cada mob aceptado: `IntentPolicy.accept(current, mob, now, contingencies, eligibleNow, profile, tick, nowNanos)` (firma de M02).
  - `IntentPolicy` ordena las alternativas por probabilidad (hasta 3), conserva las contingencias con confianza suficiente y fija `expiresAtNanos = nowNanos + maximumIntentTicks × 50 000 000`. El plazo en tiempo monotónico no se alarga si bajan los TPS (plan §7).
  - Resultado adoptado: la sesión guarda la intención; `IntentBoard` publica `IntentView(generation, JEV, movement, tactic, confidence, expiresAtNanos)`; si la táctica es distinta de la anterior, `CombatActions.signalTactic(mob, tactic)` emite `PREDICTOR_<TÁCTICA>` una sola vez. Una renovación de la misma táctica no emite señal.
  - Resultado renovado: se actualizan vencimiento y alternativas; se conserva `skillConsumed`. **Cambio de M13 (DT-28), revisado el 2026-09-30 (disponibilidad en tiempo real, decisión del usuario, desviación 29):** la choice de una intención `JEV` o `ALTERNATIVE` renovada se rearma por disponibilidad, nunca por una respuesta: en el primer ciclo reactivo posterior a su último consumo en que la choice está **lista** (cooldown nativo pasado según `CombatActions.ready` y cooldown extra cumplido), la intención sin vencer puede intentarla otra vez. Así sale al ritmo del `Cooldown` de la skill, igual que en local. Tras un consumo que no fue `CAST` (`REJECTED_BY_MYTHICMOBS`, `SKIPPED_SHIELD` sin alternativa) espera además una vida de intención local (`FallbackStep.localIntentTicks`), el ritmo de reintento anterior. Antes (desviación 28) se rearmaba cada vida de intención local.
  - Resultado rechazado por confianza baja: la racha de la sesión suma 1 y la intención vigente sigue hasta su vencimiento. M10.6 decide si pasa a `LOCAL`.
  - Una intención adoptada con choice intenta el cast en el mismo tick (M10.3).
- **Archivos:** lógica en `engine/DecisionAcceptor.java` y `engine/MobSession.java`.
- **Reglas:** A3, A4, T1, I9.
- **Pruebas:** `IntentPublicationTest`: publicación con la generación de la sesión, señal solo al cambiar de táctica, renovación sin señal, racha de confianza baja, vencimiento calculado en nanosegundos.
- **Aceptación:**
  - [x] La vista publicada lleva la generación correcta y vence en el plazo configurado. `IntentPublicationTest.anAdoptedIntentIsPublishedWithTheGenerationOfItsSessionAndExpiresInTheConfiguredNanoseconds`.
  - [x] Una táctica renovada no repite la señal, ni la skill antes de que su choice vuelva a estar lista (DT-28 revisado, desviación 29: `CastSemanticsTest.aRenewalThatArrivesBeforeTheSkillIsReadyNeverCastsAgain`, `IntentPublicationTest.aRenewedTacticNeverSignalsAgainAndRecastsOnlyWhenItsSkillIsReady`, `ReadinessCadenceTest.anAnswerAloneNeverRecastsAConsumedChoice`).

### M10.3 Ejecución única de la skill

- **Propósito:** lanzar la skill de la táctica como máximo una vez por época de la choice, en el momento en que vanilla la dejaría acertar (plan §5). Una intención nueva abre una época; un rearme abre otra en cuanto la choice está lista de nuevo (desviación 29, que sustituye el rearme por reloj de la desviación 28): cooldown nativo, cooldown extra y elegibilidad de la última captura, alcance incluido. Vale para `JEV`, `ALTERNATIVE` y `LOCAL`, así que los dos modos lanzan al ritmo del `Cooldown` de la skill (paridad del plan §12).
- **Diseño:**
  - `CombatActionsAdapter` (en `adapter.server.execution`) implementa `CombatActions.cast` en el hilo del servidor:
    1. Resuelve `ActiveMob` y su objetivo actual; si la choice exige objetivo y no hay, `NOT_ELIGIBLE`.
    2. `MythicSkillExecutor.isUsable` (cooldown nativo y condiciones de la skill); falso → `NOT_ELIGIBLE`. El cooldown extra de compatibilidad (`extraCooldownTicks`) también da `NOT_ELIGIBLE`.
    3. Invulnerabilidad: si `target.getNoDamageTicks() > target.getMaximumNoDamageTicks() / 2`, `DEFERRED_INVULNERABLE`. Reproduce la regla vanilla verificada en el binario (plan §5).
    4. Escudo: si el objetivo es `HumanEntity`, `isBlocking()` es verdadero y la skill no tiene `IGNORES_SHIELD`, `SKIPPED_SHIELD`. `FLANK` usa otra regla (M10.8).
    5. `MythicSkillExecutor.execute` con el trigger `THEPREDICT`: verdadero → `CAST`; falso → `REJECTED_BY_MYTHICMOBS`.
  - Semántica en `engine`:

    | Resultado | Final | Acción |
    |---|---|---|
    | `CAST` | Sí | `withSkillConsumed()`; el movimiento sigue |
    | `REJECTED_BY_MYTHICMOBS` | Sí | Consumida; no se reintenta la misma respuesta (plan §7) |
    | `SKIPPED_SHIELD` | Sí para esta táctica | `IntentPolicy.nextAlternative` |
    | `NOT_ELIGIBLE` | No | Se reintenta en el siguiente ciclo reactivo hasta el vencimiento |
    | `DEFERRED_INVULNERABLE` | No | Igual; espera como máximo la ventana de invulnerabilidad |
    | `EXPIRED` | Sí | La intención venció antes del cast |

  - El intento de cast y la marca de consumida ocurren en la misma operación del hilo del servidor (plan §7).
  - Si `decision.logDecisions` está activo, se registra táctica, choice y resultado.
- **Archivos:** mover `MythicSkillExecutor.java` a `adapter/server/execution/`; crear `adapter/server/execution/CombatActionsAdapter.java`.
- **Reglas:** A1, A6, T1, C7, C8.
- **Pruebas:** `CombatActionsAdapterTest` con dobles de `LivingEntity`, `HumanEntity` y `ActiveMob` (P4): cada fila de la tabla, frontera exacta de invulnerabilidad, skill con `IGNORES_SHIELD`. En servidor (guion de M13): el jugador recibe un solo golpe por intención y ningún golpe vanilla mientras ThePredict controla.
- **Aceptación:**
  - [x] Como máximo un `CAST` por época de la choice, y una época nueva solo cuando la choice vuelve a estar lista (desviación 29). Local: `CastSemanticsTest.noAnswerRecastsBeforeTheSkillIsReadyAndEachEpochCastsAtMostOnce`, `aRenewalThatArrivesBeforeTheSkillIsReadyNeverCastsAgain`, `aRenewedIntentGetsItsChoiceBackAtTheCooldownOfItsSkillWithoutSignalOrNewStart`, `aRejectionByMythicMobsConsumesTheChoiceForItsEpochAndTheNextEpochTriesAgain`; `ReadinessCadenceTest` (cadencia de 30 ± 2 ticks con un `Cooldown` de 30 en `LOCAL` y con Jev, sin casts duplicados por época); el golpe único por época en juego, pendiente de servidor (guion 12.3).
  - [x] Ningún cast ocurre fuera del hilo del servidor ni desde un callback. `CombatActionsAdapterTest.everyActionIsRefusedOffTheServerThread`, `CastSemanticsTest.noActionHappensFromTheCallbackThreadAndEveryActionRunsOnTheTickThread`.
  - [x] Un golpe durante la invulnerabilidad se aplaza en lugar de desperdiciarse. Local: `CombatActionsAdapterTest.theInvulnerabilityWindowDefersTheHitAtTheExactVanillaBoundary` y `CastSemanticsTest.notEligibleAndDeferredAreRetriedEveryReactiveCycleUntilTheCast`; en juego, pendiente de servidor (guion 12.4, DT-26).

### M10.4 Capa reactiva cada 2 ticks

- **Propósito:** reaccionar a cambios del combate sin esperar a la red.
- **Diseño:**
  - `engine.ReactiveLayer` implementa `ExecutionSteps.react(MobSession session, MobProbe probe, long tick, long nowNanos)`, llamada por el paso 4 de M09.1 (`SessionUpkeep.probes`) para la mitad de las sesiones de cada tick, con la sonda ya hecha.
  - Ya hecho por M09 antes de `react`: la sonda (`CombatReader.probe`; una sonda vacía no llega a `react` y la sesión queda para la reconciliación de M06), la colocación por objetivo con `assign` y la marca de contexto pendiente si el objetivo cambió dentro del encuentro.
  - Orden dentro de `react`:
    1. Solo lectura de la sonda recibida; ninguna sonda ni captura nueva.
    2. Objetivo cambiado respecto de `MobSession.lastTargetId` → la intención deja de ser válida (el contexto pendiente ya lo marcó M09).
    3. `ContingencyPolicy.triggered(intent, probe)`: si la táctica de la contingencia sigue elegible, nueva intención con nivel `ALTERNATIVE`, publicación, señal y contexto pendiente (una contingencia consumida es un evento de reconsideración, plan §7).
    4. Intención vencida o inválida → M10.6. Con una intención `LOCAL` válida, disponibilidad (desviación 29): si su choice está consumida o no está lista, y el planificador local, con los cooldowns leídos ahora, elige una táctica cuya choice sí lo está, se rearma la misma táctica o se cambia a la otra sin esperar a que venza la intención; el cambio respeta la permanencia mínima.
    5. Rearme por disponibilidad de una intención `JEV` o `ALTERNATIVE` e intento de cast si la intención tiene choice sin consumir (M10.3).
  - Falta de progreso, con la regla de M05: en cada decisión del encuentro, desde `ExecutionSteps.evaluated(Encounter, tick, nowNanos)` (paso 6 de M09.1, una vez por evaluación), se lee `MobControl.progress(mob)` de cada miembro. Dos decisiones seguidas sin meta de movimiento cuando el movimiento la exige, o sin cambio de al menos 0,5 bloques en la dirección esperada, producen `NO_PROGRESS` e `IntentPolicy.nextAlternative`.
  - Costo: una sonda O(1) por sesión cada 2 ticks; sin capturas completas ni rutas nuevas (C12). La disponibilidad solo añade, cuando la choice vigente no está lista, una consulta O(1) de cooldown por táctica de `MobFacts.eligibleWhenReady` y una elección del planificador local sobre ese conjunto (medido en M13, `m13Measure`).
- **Archivos:** crear `engine/ReactiveLayer.java` y la implementación de `engine.ExecutionSteps` que M10 conecta en `DecisionServices.Wiring` en lugar de `ExecutionSteps.untilExecution()`.
- **Reglas:** A5, A6, C12, T1.
- **Pruebas:** `ReactiveLayerTest` con `ServerClock` controlable: contingencia de cuerpo a cuerpo que se dispara una sola vez, contingencia de salud crítica, objetivo cambiado, dos evaluaciones sin progreso (a través de `evaluated`). La sonda vacía y el escalonado entre ticks pares e impares ya los prueban `CoordinatorTest` y `AgendaTest` de M09.
- **Aceptación:**
  - [x] Una contingencia se aplica en ≤2 ticks desde que ocurre su situación. `ReactiveLayerTest.theMeleeContingencyAppliesWithinTwoTicksAndOnlyOnce`, `theCriticalHealthContingencyHasPrecedence`.
  - [x] Cada contingencia se usa una sola vez. `ReactiveLayerTest.theMeleeContingencyAppliesWithinTwoTicksAndOnlyOnce`.
  - [x] La capa reactiva no llama a `capture` ni calcula rutas. `ReactiveLayerTest.theReactiveCycleNeverCapturesProbesOrReadsProgress`.

### M10.5 Confianza e histéresis de extremo a extremo

- **Propósito:** evitar decisiones dudosas y oscilaciones entre avanzar y retroceder (plan §6).
- **Diseño:**
  - Reglas en `IntentPolicy` (M02): cambiar de táctica solo si la nueva supera a la vigente por `switchMargin` en probabilidad y la vigente cumplió `minimumDecisionIntervalTicks`; con confianza bajo `minimumConfidence`, conservar la vigente o rechazar con `LOW_CONFIDENCE`.
  - M10 añade la racha: tras `lowConfidenceStreakForLocal` rechazos seguidos (3 por defecto), `FallbackPolicy` pasa la sesión a `LOCAL`. Una adopción reinicia la racha.
  - Los valores iniciales (0,5 y 0,15) son puntos de partida; M13 los calibra por versión de modelo. **Con la cuenta real (M13, 2026-09-29, DT-31), quedan respaldados de forma preliminar: las etiquetas son las propuestas por M13 (`labelsProposedBy`) y el usuario no las ha revisado; la muestra efectiva son 6 escenarios de una sola pregunta repetidos 200 veces; la curva no es monótona (0,0–0,3: 69 % aceptable; 0,3–0,5: 5,8 %) y no hubo contingencias. Siguen abiertos hasta que el usuario revise las etiquetas y haya escenarios más variados.** A 0,5, el 66,7 % de las respuestas pagadas de este banco se descartan por confianza baja (`LOW_CONFIDENCE`): la mayor parte del gasto compra respuestas que no se usan. **Punto clave para que el usuario lo revise**; opciones para M14 o la 0.0.2: mejorar la redacción de las preguntas y las etiquetas, o un `minimumConfidence` por perfil.
- **Archivos:** sin clases nuevas; uso en `DecisionAcceptor` y `ReactiveLayer`.
- **Reglas:** A7.
- **Pruebas:** `HysteresisScenarioTest` de extremo a extremo en `engine`: secuencia alternante de respuestas A/B cerca del margen que no provoca oscilación; respuesta con margen suficiente que cambia; tres respuestas de baja confianza que llevan a `LOCAL`.
- **Aceptación:**
  - [x] Ninguna táctica cambia dos veces dentro de `minimumDecisionIntervalTicks` por respuestas cercanas al margen. `HysteresisScenarioTest.answersAlternatingNearTheMarginNeverMakeTheTacticOscillate`, `aSufficientMarginSwitchesButNeverTwiceWithinTheMinimumInterval`.
  - [x] La racha de confianza baja lleva a `LOCAL` y una adopción la reinicia. `HysteresisScenarioTest.threeLowConfidenceAnswersLeadToLocalOnceTheIntentExpiresAndAnAdoptionResetsTheStreak`.

### M10.6 Cadena de fallback

- **Propósito:** degradar por niveles sin esperar a la red y devolver el control a la IA base cuando no queda nada mejor (plan §8).
- **Diseño:**
  - `FallbackPolicy.level(serviceState, budgetAvailable, lowConfidenceStreak, sessionValid, intentValid, localTacticAvailable)` (firma de M02) se evalúa en el ciclo reactivo de cada sesión sin intención válida de nivel `JEV` o `ALTERNATIVE`, y tras cada `Failed` recibido en `accept`. El estado del servicio sale de `ServiceStatusTracker.status()` y `allowsDispatch`.
  - `LOCAL`: `LocalPlanner.choose(profile, eligible)` sobre las tácticas elegibles de la última captura. Intención con nivel `LOCAL`, confianza 0 y vigencia corta: `min(maximumIntentTicks, minimumDecisionIntervalTicks)` ticks. Se replanifica al vencer mientras dure el modo local y, desde la desviación 29, también en cuanto el planificador, con los cooldowns leídos en el ciclo reactivo, elige una táctica cuya choice está lista mientras la vigente no lo está; al replanificar sin intención válida se prefiere esa táctica lista a la de la captura. Costo cero y sin red.
  - `LOCAL` también cubre la espera de la primera respuesta, si hay táctica local; si no, `BASE`. Coincide con el estado `REQUESTING` del plan §8.
  - `BASE`: `IntentBoard` publica `IntentView.base(generation)`; todas las condiciones responden falso y la IA base del conjunto generado toma el control en el siguiente ciclo de metas (M05).
  - Excepción al procesar una sesión: `BASE` con `INTERNAL_ERROR`, registro con límite de frecuencia. Para `react` ya lo hace `SessionUpkeep` de M09; `accept` y `evaluated` deben aplicarlo por sesión dentro de su propio código, porque una excepción que escape de ellos solo la cuenta y registra el coordinador.
  - La primera respuesta aceptada después vuelve a `JEV`.
  - Cambiar de nivel nunca reinstala metas.
  - Cada cambio de nivel guarda su `FallbackReason` en la sesión para `inspect` (M12).
- **Archivos:** lógica en `engine/ReactiveLayer.java` y `engine/DecisionAcceptor.java`.
- **Reglas:** A6, A7, C7.
- **Pruebas:** `FallbackChainTest` en `engine` con dobles y reloj: 429 en pleno combate que pasa a `LOCAL` en ≤2 ticks, recuperación a `JEV` tras la pausa, falta de presupuesto, excepción de una sesión que solo afecta a ella, sesión inválida que pasa a `BASE`, sin táctica local que pasa a `BASE`.
- **Aceptación:**
  - [x] Ningún nivel espera una respuesta HTTP. `FallbackChainTest.aRateLimitMovesASessionWithoutAValidJevIntentToLocalInTheSameTick`, `aRateLimitKeepsAValidJevIntentUntilItExpiresAndThenGoesLocalWithinTwoTicks`, `withoutJevTheLocalPlannerControlsAtZeroCostAndPlansAgainWhenItsShortIntentExpires` (con futures pendientes y sin cliente).
  - [x] Un error interno deja la sesión en `BASE`, nunca congelada. `FallbackChainTest.anExceptionWhileHandlingOneSessionDropsOnlyThatSessionToBase`, `aFailingProbeInTheReactiveCycleDropsOnlyThatSessionToBase`.
  - [x] La vuelta a `JEV` ocurre con la primera respuesta aceptada. `FallbackChainTest.theFirstAcceptedAnswerAfterThePauseReturnsTheSessionToJev`.

### M10.7 Coordinación dentro del encuentro

- **Propósito:** evitar decisiones individuales que, juntas, rompen el combate; por ejemplo, que todos se retiren a la vez (plan §6).
- **Diseño:**
  - Tras aceptar todas las preguntas de una respuesta, una regla pura limita las intenciones `RETREAT` simultáneas a `max(1, miembros / 3)` con división entera.
  - Se conservan las de mayor confianza. Las demás toman su siguiente alternativa cuyo movimiento no sea `RETREAT`; si no hay, se renueva su intención anterior o pasan a `LOCAL`.
  - La regla es determinista: empate por orden del encuentro.
- **Archivos:** regla pura `domain/CoordinationPolicy.java` (01 §7); aplicación en `engine/DecisionAcceptor.java`.
- **Reglas:** A2, A7.
- **Pruebas:** `CoordinationRuleTest`: 1, 3, 6 y 7 miembros que piden `RETREAT`; empate; exceso sin alternativa no `RETREAT`.
- **Aceptación:**
  - [x] Nunca hay más retiradas simultáneas que el tope. `CoordinationRuleTest` (1, 3, 6 y 7 miembros, retiradas en curso), `EncounterCoordinationTest.onlyTheMostConfidentRetreatOfThreeMembersRunsAndTheOthersTakeTheirNextAlternative`.
  - [x] El resultado no depende del orden de llegada de las respuestas. `CoordinationRuleTest.aTieIsBrokenByEncounterOrder`, `EncounterCoordinationTest.theResultDoesNotDependOnTheOrderOfTheAnswersInTheResponse`.

### M10.8 `FLANK` de extremo a extremo

- **Propósito:** aprovechar el ángulo de bloqueo del escudo con la mínima lógica propia (plan §5, "Flanqueo").
- **Diseño:**
  - Elegibilidad (regla de `Eligibility`, M02): el objetivo tiene `RAISING_SHIELD` o `BLOCKING` y `TargetRelation.flankable` es verdadero (M07).
  - Movimiento: `FlankGoal` de M05 lleva al mob a un destino lateral.
  - Cast: para una intención `FLANK`, `CombatActionsAdapter` sustituye la regla de escudo de M10.3 por `ShieldGeometry.isOutsideBlockingAngle(target, mob, ángulo)`. El ángulo es el máximo `horizontalBlockingAngle` de `BLOCKS_ATTACKS` del ítem activo, 90° si no hay reducciones. Dentro del ángulo → `NOT_ELIGIBLE` (espera); fuera → cast normal.
  - Si el objetivo deja de bloquear, no hay escudo que evitar y se aplican las comprobaciones normales de M10.3.
  - Sin progreso en dos decisiones → `NO_PROGRESS` y siguiente alternativa.
- **Archivos:** `CombatActionsAdapter.java`; `ShieldGeometry` de M05.
- **Reglas:** A1, C5 (`Degrees`).
- **Pruebas:** en `CombatActionsAdapterTest`, escudo dentro y fuera del ángulo, ángulo leído del componente, objetivo que deja de bloquear. En servidor (M13): el golpe final llega fuera del ángulo y hace daño completo.
- **Aceptación:**
  - [x] `FLANK` solo es elegible con escudo levantado o bloqueando. Regla de M07: `ReadyTacticsTest.flankIsEligibleOnlyWithAFlankableTarget`, `TargetRelationReaderTest.flankingNeedsAShieldUpSightAndAMobStillInsideTheBlockingAngle`, `FactsAssemblerTest.aShieldFacingTheMobIsFlankableAndMakesFlankEligible`; M10 la usa con `lastMobFacts().eligibleTactics()`.
  - [x] El cast de `FLANK` nunca ocurre dentro del ángulo de bloqueo. Local: `CombatActionsAdapterTest.aFlankCastWaitsInsideTheBlockingAngleAndLandsOutsideIt`, `theFlankAngleIsTheOneReadFromTheShieldComponent`; el daño completo en juego, pendiente de servidor (guion 12.5).

### M10.9 Ataque vanilla declarado

- **Propósito:** permitir un golpe cuerpo a cuerpo vanilla como choice distinta, sin duplicar daño (plan §5).
- **Diseño:**
  - Una choice con `ChoiceKind.VANILLA_MELEE` se ejecuta con `LivingEntity#attack(target)` y `swingMainHand()` de la API de Paper, con las mismas comprobaciones de objetivo, invulnerabilidad y escudo de M10.3.
  - Alcance: distancia entre cajas de colisión ≤ `MovementSettings.meleeReachBlocks` (2,5 bloques por defecto). **M01 confirma** si `attack` aplica su propio límite de alcance; si lo aplica, se usa ese.
  - Solo es posible cuando ThePredict controla: la meta de ataque base no corre entonces (plan §3), así que no hay doble golpe.
- **Archivos:** `CombatActionsAdapter.java`.
- **Reglas:** A1, C7.
- **Pruebas:** `CombatActionsAdapterTest` con dobles: alcance dentro y fuera, invulnerabilidad, escudo. En servidor (M13): un solo golpe vanilla por intención.
- **Aceptación:**
  - [x] El ataque vanilla respeta las mismas reglas que una skill. `CombatActionsAdapterTest.aVanillaMeleeChoiceAttacksOnlyWithinReachAndFollowsTheSameRules`.
  - [ ] No hay dos golpes en la misma intención. Pendiente de servidor (guion 12.3, M13): la parte de ThePredict (un `CAST` por intención, también para `VANILLA_MELEE`) está probada en `CastSemanticsTest`; que la meta de ataque base no golpee a la vez solo se observa en juego.

## Riesgos y decisiones abiertas

- **Incorporado en 01 (revisión 2):** `enum DiscardReason { STALE_SEQUENCE, SESSION_CHANGED, SESSION_INACTIVE, CATALOG_CHANGED, MODEL_CHANGED, TOO_OLD, TARGET_CHANGED, PROBE_UNAVAILABLE }` para las causas de descarte de M10.1 (A7). `FallbackReason` no cubre estos casos.
- **Incorporado en 00 §4 (revisión 2):** `CombatActions.cast(MobKey, SkillChoice, Movement)`; el adapter necesita saber si la intención es `FLANK` para cambiar la regla del escudo.
- **Incorporado en 01 (revisión 2):** `SkillChoice` con un componente `ChoiceKind kind` (`MYTHIC_SKILL`, `VANILLA_MELEE`) para declarar el ataque vanilla de M10.9 sin un ID reservado.
- **Incorporado en 01 §7 (revisión 2):** regla pura `CoordinationPolicy.limitRetreats(...)` para M10.7, o un método equivalente en `IntentPolicy`.
- **Incorporado en 01 §8 (revisión 2):** tipo `engine.ReactiveLayer` y campos de `MobSession` `lastMobFacts` (tácticas elegibles y contextos de la última captura) y `noProgressStreak`, que completan las propuestas de M06.
- Los resultados tipados `IntentOutcome` y `FallbackDecision` están en 01 §7; la causa viaja con cada resultado.
- Claves incluidas en M03 (revisión 2): `decision.maxDecisionAgeMillis` (`timeoutMillis + 200` por defecto) y `decision.lowConfidenceStreakForLocal` (3).
- Las skills con delays o proyectiles ya iniciados no se cancelan al cambiar de táctica; M13 documenta el límite por tipo de skill del ejemplo (plan §13).

## Terminado

- [ ] M10.1 a M10.9 con sus aceptaciones marcadas: todas salvo la segunda de M10.9, que exige servidor.
- [x] `./gradlew clean build javadoc` verde; `ArchitectureRulesTest` confirma que `domain` y `engine` no importan Bukkit (y `theExecutionAdapterFollowsRuleD5AndTheCombatActionsPortStaysFreeOfIt`).
- [ ] Escenarios de servidor de M13 para daño doble, flanqueo, pérdida de Jev en combate y recuperación registrados con evidencia.
- [x] Ninguna acción sobre el mundo desde callbacks, comprobado por prueba. `CastSemanticsTest.noActionHappensFromTheCallbackThreadAndEveryActionRunsOnTheTickThread`.
- [x] Estado del módulo actualizado en [README.md](README.md).

## Estado de implementación

> **Local listo — servidor y cuenta real pendientes (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` pasa sin avisos de compilación ni de Javadoc: **1093 pruebas, 0 fallos** (1001 de partida − 1 retirada con `DecisionFreshness` + 93 nuevas, 12 de ellas de las dos rondas de correcciones tras la auditoría). Ninguna prueba sale de la máquina (dobles de los puertos y reloj controlable; ninguna llamada a TypeSafe ni al servidor de contrato hizo falta) y nadie leyó `.env` (W6). Ninguna prueba nueva usa `Thread.sleep` (P2): la única prueba con otro hilo lo espera con `join()`. No se arrancó ningún servidor: las aceptaciones que exigen juego siguen sin marcar y no hay evidencia de servidor inventada. 00 recibe la revisión 7 (una fila de §10) y 01 las revisiones 5 y 6 (ver desviaciones). Incluye las correcciones tras la auditoría (tabla al final de esta sección).

### Archivos

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…`) |
|---|---|
| Frontera con M09 | Creados `engine/TacticalExecution` (implementa `ExecutionSteps`, con el record `Parts`), `engine/CombatActions` (puerto de 00 §4) y `engine/ExecutionSettings`. `engine/ExecutionSteps` pierde `untilExecution()`. `DecisionServices` construye `TacticalExecution` sobre su presupuesto y su tracker; `Wiring` recibe `CombatActions` y `ExecutionSettings` en lugar de `ExecutionSteps`; `executionSettings(DecisionSettings, JevSettings)` (D10) y `UnavailableCombatActions`. `ThePredictPlugin.combatActions` construye el adapter |
| M10.1 | Creados `engine/DecisionAcceptor`, `AnswerIdentity`, `AnswerGroups` y `ModelExpectation` (paquete privado). `engine/EncounterSchedule` gana `sequence()` y `handledSequence`. Eliminados `adapter/mythicmobs/DecisionFreshness`, el `BoundProfile` antiguo y el paquete `adapter.mythicmobs`, con `DecisionFreshnessTest` |
| M10.2 | Creado `engine/IntentControl` (paquete privado: aplicar, publicar, señal, bajar a `BASE`, cast). `engine/MobSession` gana `nextChoiceTick`, `progressReferenceBlocks` y `jevAnswered` con sus mutadores de paquete |
| M10.3 | Movido `adapter/mythicmobs/MythicSkillExecutor` a `adapter/server/execution/` (inyecta `SkillExecutor`). Creados `adapter/server/execution/CombatActionsAdapter`, `CastSource`, `MythicCastSource`, y `HitChecks` y `SkillConditions` (paquete privado: reglas de golpe y comprobación sin efectos de las condiciones de la skill) |
| M10.4 | Creados `engine/ReactiveLayer` y `engine/ProgressWatch` (paquete privado) |
| M10.5 | Uso de `IntentPolicy` y `FallbackPolicy` en `DecisionAcceptor` y `FallbackStep`; `domain/FallbackPolicy` gana la sobrecarga con la racha configurada |
| M10.6 | Creado `engine/FallbackStep` (paquete privado). `engine/RequestBudget.available` y `engine/ServiceStatusTracker.state` |
| M10.7 | Creados `domain/CoordinationPolicy` (con `Candidate`) y `engine/IntentCoordination` (paquete privado) |
| M10.8, M10.9 | `CombatActionsAdapter` (ángulo con `ShieldGeometry`, alcance con `HitChecks.gapBlocks`) |
| Registro | `engine/ExecutionLog` (paquete privado, límite de frecuencia de C8) |
| Documentos | Este documento; `README.md`; `00-Arquitectura-y-reglas.md` (revisión 7); `01-Modelo-de-dominio.md` (revisión 5); `M01-Registro-de-decisiones.md` (DT-11, DT-12 y DT-26 nueva, todas `Abierta`); `M01-Guion-de-servidor.md` (nota en §0 y sección 12; el cierre pasa a la 13); casillas cubiertas de M04.6 y M05.5; pendiente de M02 |

### Pruebas (93 nuevas)

- `engine` (dobles de los puertos y `ServerClock` controlable a 50 ms por tick, `ExecutionFixture` compone el coordinador de M09 con `TacticalExecution`): `DecisionAcceptorTest` (12: respuesta tardía tras la vigilancia, duplicada, generación vieja, sesión cerrada con otra válida en la misma respuesta, sesión reabierta en la generación 0 en otro encuentro, catálogo recargado, modelo versionado distinto, alias que cambia una vez con registro único, frescura y su frontera exacta, objetivo cambiado y sonda vacía, cancelada sin fallback y respuesta real aplicada después, conteo y registro solo con `logDecisions`); `IntentPublicationTest` (4); `ReactiveLayerTest` (8: contingencia de cuerpo a cuerpo en ≤2 ticks y una sola vez, salud crítica con precedencia, objetivo cambiado, dos evaluaciones sin progreso, sin capturas ni sondas ni lecturas de progreso en la capa reactiva, intención de depuración intacta, dirección esperada de cada movimiento, meta en curso y referencia ausente); `HysteresisScenarioTest` (4: más una respuesta de confianza baja sin intención válida → `BASE`, racha completa → `LOCAL` con `LOW_CONFIDENCE` y una adopción que reinicia la racha y la espera local); `FallbackChainTest` (17: 429 sin intención válida → `LOCAL` en el mismo tick; 429 con intención `JEV` → `LOCAL` ≤2 ticks tras vencer; recuperación a `JEV`; presupuesto agotado; excepción en el cast y en la sonda de una sola sesión; sesión inválida; modo local sin Jev con replanificación y cero solicitudes; sin táctica local; táctica local que deja de ser elegible; objetivo no percibido; objetivo perdido; `TIMEOUT` contado una sola vez; intención vencida con la solicitud en vuelo → `LOCAL` y vuelta a `JEV`; sin parpadeo a `BASE` entre latidos; intención republicada tras `debug release`; un mob local escribe como mucho 4 líneas en 400 ticks); `CastSemanticsTest` (8: un cast por intención renovada, `NOT_ELIGIBLE` y `DEFERRED_INVULNERABLE` reintentados, `REJECTED_BY_MYTHICMOBS` consumida, `SKIPPED_SHIELD` → alternativa, `EXPIRED` una vez, cooldown extra de compatibilidad sin llamar a MythicMobs, ninguna acción desde el hilo del callback, un resultado repetido contado y registrado una sola vez por intención); `EncounterCoordinationTest` (3: más la causa `RETREAT_LIMIT` de una retirada rechazada, en `LOCAL` y en `BASE`); `ModelExpectationTest` (3); `ExecutionLogTest` (2); `ExecutionSettingsTest` (1).
- `domain`: `CoordinationRuleTest` (6: 1, 3, 6 y 7 miembros; mayor confianza y alternativa sin `RETREAT`; empate por orden del encuentro; exceso sin alternativa con intención anterior, vencida y sin nada; retiradas en curso; intenciones que no se retiran); `FallbackPolicyTest` (+1: racha configurada).
- `adapter.server.execution`: `CombatActionsAdapterTest` (19, con proxies de `World`, `LivingEntity`, `Player` y `Skill` y un `CastSource` que registra: cada fila de M10.3, frontera exacta de invulnerabilidad con máximo 20 y 21, `IGNORES_SHIELD`, escudo levantado sin bloquear, `FLANK` dentro y fuera del ángulo, ángulo leído del componente, `FLANK` sin escudo, ataque vanilla dentro y fuera de alcance con el alcance del perfil, invulnerabilidad y escudo, señal, amenaza con jugador desconectado, en otro mundo y sin tabla, rechazo fuera del hilo del servidor, distancia entre cajas; tras la auditoría: condiciones de la skill que fallan → `NOT_ELIGIBLE` sin ejecutar ni consumir, condiciones evaluadas solo tras las demás comprobaciones, skill `MELEE_ATTACK` que espera al alcance y skill `RANGED_ATTACK` que no, skill que no golpea al objetivo sin escudo ni invulnerabilidad, reglas de daño y alcance por valores declarados). `ModelExpectationTest` comprueba además el aviso único por modelo distinto de un ID versionado.
- Ampliadas: `RequestBudgetTest` (+1: `available`), `DecisionServicesTest` (+2: traducción de `ExecutionSettings`, acciones nulas), `ArchitectureRulesTest` (+2: nada nombra lo retirado por M10; `adapter.server.execution` bajo D5 y el puerto `CombatActions` libre de adapters y de Paper). Adaptada: `DecisionServicesTest` (nuevo `Wiring`). Retirada: `DecisionFreshnessTest` (su regla de mundo, objetivo y edad está en `DecisionAcceptorTest`: el cambio de mundo sube la generación, `SESSION_CHANGED`; el objetivo, `TARGET_CHANGED`; la edad, `TOO_OLD`).

### Evidencia estática nueva (javap; no confirma comportamiento en juego)

- MythicMobs 5.13.1 (corregido tras la auditoría): `MetaSkill.executeIfUsable(meta)` sobrescribe el método por defecto: `isUsable(meta)` y después el privado `executeInternal(meta)`, que lanza el `MythicSkillEvent` cancelable, toma el bloqueo de cooldown y consume una carga, y devuelve `false` si alguno lo impide. `MetaSkill.isUsable` tira `rollChance`, comprueba `isSkillOnCooldown` y evalúa las condiciones de lanzador, disparador y objetivo (`getConditions`/`evaluateCaster`, `getConditionsTrigger`/`evaluateTrigger`, `getConditionsTarget`/`evaluateTargets`) ejecutando sus skills `onFail`/`onPass`. `SkillCondition.handleOutcome` ejecuta otra skill con `CAST`, `CASTINSTEAD` y `ORELSECAST`, multiplica la potencia con `POWER` y con `REQUIRED`/`CANCEL` solo envía un mensaje si el lanzador es un jugador; `getACTION()` da la acción. `ActiveMob.signalMob(AbstractEntity, String)`, `hasThreatTable()`, `getThreatTable()` y `ThreatTable.threatGain/threatLoss(AbstractEntity, double)` existen con esas firmas; `BukkitAdapter.adapt(Player)` da el `AbstractPlayer`; `SkillExecutor.getSkill(String)` devuelve `Optional<Skill>`.
- Paper 26.2 (servidor parcheado localmente, sin arrancar): `CraftLivingEntity.attack(Entity)` llama a `doHurtTarget` sin límite de alcance para un mob; `swingMainHand()` llama a `swing(MAIN_HAND, true)`; `getNoDamageTicks()` y `getMaximumNoDamageTicks()` leen `invulnerableTime` e `invulnerableDuration`; `LivingEntity.hurtServer` ignora un golpe que no supera al anterior mientras `(float) invulnerableTime > (float) invulnerableDuration / 2`; `CraftHumanEntity.isBlocking()` es `Player.isBlocking()`. Registrado en DT-12 y DT-26.

### Desviaciones y decisiones

| # | Desviación o decisión | Motivo |
|---|---|---|
| 1 | `ExecutionSteps.untilExecution()` se elimina y `DecisionServices.Wiring` recibe `CombatActions actions` y `ExecutionSettings execution` en lugar de `ExecutionSteps steps`; `DecisionServices` construye `TacticalExecution` | La ejecución necesita el `ServiceStatusTracker` y el `RequestBudget` que `DecisionServices` crea; nada usa ya el marcador de M09 (`ArchitectureRulesTest` lo comprueba) |
| 2 | `DecisionAcceptor` y `ReactiveLayer` son de paquete privado; la entrada pública es `TacticalExecution`. Auxiliares de paquete privado: `IntentControl`, `FallbackStep`, `ProgressWatch`, `AnswerGroups`, `AnswerIdentity`, `IntentCoordination`, `ModelExpectation` y `ExecutionLog` | C10 (ninguna clase nueva pasa de 200 líneas ni ningún método de 30) y una sola API pública para la raíz |
| 3 | Tipos públicos nuevos de `engine`: `CombatActions` (el puerto de 00 §4, que aún no existía), `ExecutionSettings` y `TacticalExecution`; `MobSession` gana `nextChoiceTick`, `progressReferenceBlocks` y `jevAnswered`, y `EncounterSchedule` la secuencia atendida. 01, revisión 5 | D10 (los settings de M10 vienen de `config`), M06 (el cooldown extra por instancia pasa a `MobSession`) y las reglas de progreso, duplicados y espera de la primera respuesta |
| 4 | `FallbackPolicy.level(…, int streakForLocal, …)`: sobrecarga con la racha configurada (`decision.lowConfidenceStreakForLocal`); la firma de M02 delega con `LOW_CONFIDENCE_STREAK_FOR_LOCAL` | La clave existe en M03 (1 a 20) y M02 fijaba 3 |
| 5 | Cast: `onCooldown` (sin efectos), alcance, invulnerabilidad y escudo, después las condiciones `REQUIRED`/`CANCEL` de la skill evaluadas sin efectos (`SkillConditions`: lanzador, disparador y objetivo con metadatos propios; si falla alguna, `NOT_ELIGIBLE` y reintento en la misma intención) y por último **un solo** `executeIfUsable`; `MythicSkillExecutor` gana `conditionsHold`/`conditionsHoldAt` y pierde `isUsable`. Un `Chance` fallido, un `MythicSkillEvent` cancelado o sin carga dan `REJECTED_BY_MYTHICMOBS` (final). Las condiciones con acciones `CAST`, `CASTINSTEAD`, `ORELSECAST`, `POWER` o `LEVEL` no se evalúan antes: se resuelven una vez al lanzar. Una condición de objetivo que falla hace esperar aunque la skill no pare sin objetivos | Evidencia javap: `isUsable` tira el `Chance` y ejecuta `onFail`/`onPass`, y `handleOutcome` ejecuta skills o cambia la potencia con las demás acciones; `REQUIRED` y `CANCEL` no tienen efectos con un mob como lanzador. Decisión del coordinador tras la auditoría (una skill no se pierde por una condición que aún no se cumple). DT-12 |
| 6 | La skill se busca con `SkillExecutor.getSkill(mythicSkillId)` en cada cast; una skill que ya no existe da `REJECTED_BY_MYTHICMOBS`. El objetivo es el de la captura (`MemberSource.target`: amenaza y luego `Mob#getTarget()`) | Siempre el objeto vigente tras `/mm reload` sin estado en el adapter; el cast apunta al objetivo sobre el que se preguntó a Jev |
| 7 | El cooldown extra de compatibilidad (`extraCooldownTicks`) lo comprueba `engine` antes de llamar al adapter (`NOT_ELIGIBLE` sin llamada); uno por instancia, fijado tras cada `CAST` | M06: `nextChoiceTick` vive en `MobSession`, que el adapter no puede leer (D7) |
| 8 | Alcance: una choice `VANILLA_MELEE` o una skill con `MELEE_ATTACK` espera (`NOT_ELIGIBLE`) hasta que la distancia entre cajas de colisión sea ≤ `meleeReachBlocks` del perfil de la sesión (2,5 si no hay). Invulnerabilidad y escudo solo para choices que golpean al objetivo: `VANILLA_MELEE`, o `RequiresTarget` con `MELEE_ATTACK`, `RANGED_ATTACK`, `LOW_DAMAGE`, `MEDIUM_DAMAGE` o `HIGH_DAMAGE` (`HitChecks.damaging`). `FLANK` con escudo levantado usa solo el ángulo (`ShieldGeometry::raisedShieldAngleDegrees`, inyectado); sin escudo, las comprobaciones normales. `VANILLA_MELEE` exige siempre objetivo; invulnerabilidad con la comparación en `float` de vanilla | M10.8, M10.9, plan §5 (las comprobaciones son de un cast de daño) y la evidencia javap (`attack` no limita alcance). `PredictValue` (01) es la etiqueta de ataque y daño del modelo; `ChoiceKind` solo distingue skill y golpe vanilla |
| 9 | `addThreat` se implementa ya con los pasos 2 a 4 de M11.4; `engine` no lo llama hasta M11. El paso 1 (generación vigente) no está en el adapter | El puerto lo exige; `engine` es el único llamador y solo pasa la clave de la sesión actual en el mismo tick |
| 10 | Modelo: versionado si tiene la forma `nombre(-parte)*-N.N(.N)*` (`jev-1.13.0`); si no, alias (`jev-latest`). El registro solo cita un ID `[A-Za-z0-9._-]{1,64}` | Plan §6 y M08 (alias en `/v1/models`); ningún texto arbitrario del servicio llega a la consola |
| 11 | `STALE_SEQUENCE` cubre también un encuentro cerrado o desconocido, un resultado ya atendido (duplicado o tardío tras el `TIMEOUT` sintético) y un `Failed` con detalle `cancelled` (sin fallback ni cuenta de fallo); `SESSION_INACTIVE` cubre un mob sin sesión y una sesión sostenida por el comando de depuración; `SESSION_CHANGED`, una sesión en otro encuentro. Frescura con frontera inclusiva (`≤`) | `DiscardReason` es cerrado (01); una solicitud cancelada ya no es la vigente; M08 pide no contar `cancelled` como fallo |
| 12 | Tras un `Failed` vigente, la cadena de fallback solo corre para las sesiones preguntadas **sin** intención `JEV` o `ALTERNATIVE` válida; las demás siguen hasta su vencimiento y bajan en ≤2 ticks después | `FallbackPolicy` (M02, auditada) conserva una intención `JEV` vigente aunque el servicio falle, y el plan §8 permite conservarla "únicamente hasta su vencimiento". La prueba "429 → `LOCAL` en ≤2 ticks" se cumple así en los dos casos (`FallbackChainTest`) |
| 13 | Espera de una respuesta: si `FallbackPolicy` da `BASE`/`EXPIRED` (servicio sano, sin intención válida, sin degradación), hay táctica local **y la racha de confianza baja es 0**, la sesión pasa a `LOCAL` en `engine` cuando Jev aún no respondió sobre la generación **o** la sesión está en un encuentro abierto. Sin causa mientras espera la primera respuesta; `EXPIRED` si venía de `JEV`/`ALTERNATIVE`. `FallbackPolicy` no cambia. En la práctica `answerComing` es "encuentro abierto": un encuentro abierto siempre tiene su solicitud en vuelo o su evaluación siguiente programada a menos de un latido (M09.5, latido ≥ intervalo mínimo), así que el código se simplificó a esa comprobación. Con racha > 0 y sin intención válida, `BASE` (con `EXPIRED`, la causa que da `FallbackPolicy`) hasta que la racha llega a `decision.lowConfidenceStreakForLocal` y `FallbackPolicy` da `LOCAL` con `LOW_CONFIDENCE`; una respuesta aceptada reinicia la racha y la espera local | M10.6 ("`LOCAL` también cubre la espera"), corrección 5 del coordinador (sin parpadeo a `BASE` entre respuestas) y su decisión en la segunda ronda: plan §6, "por debajo de `minimumConfidence` … si no existe, ceder a las metas base", y M10.5 (racha de 3) |
| 14 | El planificador local solo tiene táctica con metas instaladas, un objetivo percibido en la última captura y el mismo objetivo en la sonda; si no, `BASE` | Cierra la decisión abierta de M07/M09: un objetivo real no percibido (más allá de todo radio) deja la IA base, que ya lo persigue de forma nativa, en vez de fijar `HOLD` desde hechos sin objetivo. Un mob fuera de combate nunca pasa a `LOCAL` |
| 15 | Una intención `LOCAL` replanificada al vencer es una intención nueva (puede lanzar otra vez cuando el cooldown nativo lo permita); una renovación `JEV` conserva `skillConsumed` (M02). Replanificar la misma táctica no emite señal | "Se replanifica al vencer"; sin Jev el boss sigue combatiendo con sus skills como la IA clásica; la señal solo marca una táctica nueva (M04.6) |
| 16 | Una respuesta renovada también reinicia la racha de confianza baja | Superó el umbral de confianza, igual que una adopción |
| 17 | Objetivo cambiado → la intención deja de ser válida y decide la cadena (`BASE` con `EXPIRED` si el servicio está sano y ya respondió) | `FallbackReason` no tiene causa de objetivo (01); la siguiente decisión llega pronto porque M09 marcó contexto pendiente |
| 18 | La regla `NO_PROGRESS` vive en `engine.ProgressWatch` (partes puras en `reached` y `advanced`), no en `domain.IntentPolicy` como decía M05.5: `APPROACH` cumple a ≤ `meleeReach + 0,5`; `RETREAT` a ≥ `retreatDistance − 0,5`; `KEEP_DISTANCE` dentro de la banda; `FLANK` con su meta en curso; sin distancia solo cuenta la meta. La racha se reinicia al cambiar de táctica y sigue en las renovaciones. Sin alternativa (o en `LOCAL`) baja a `BASE` con `NO_PROGRESS` | Necesita la racha y la distancia anterior de `MobSession`; `IntentPolicy` decide respuestas. Un mob local atascado puede alternar `LOCAL`/`BASE` cada dos evaluaciones: M13 lo mide |
| 19 | `SKIPPED_SHIELD` sin alternativa consume la choice y conserva el movimiento; `EXPIRED` lo registra `engine` cuando una intención vence sin haber lanzado su choice | El adapter nunca devuelve `EXPIRED` (00 §4); "final para esta táctica" |
| 20 | Una vista `debug_*` que controla sostiene la sesión: M10 no reacciona, no juzga progreso y descarta respuestas (`SESSION_INACTIVE`) hasta que vence o se suelta | W5: `debug intent` (pasos 2.3, 6.x, 8.5 y 9.x del guion) sigue funcionando aunque el modo local esté activo |
| 21 | Registro de M10 en `info`, 20 líneas cada 200 ticks como máximo; decisiones, descartes y casts solo con `decision.logDecisions`; el cambio de modelo siempre | C8 |
| 22 | Tamaño: `ThePredictPlugin` 452 líneas (+24), `DecisionServices` 216, `MobSession` 374, `RequestBudget` 243, `ServiceStatusTracker` 246, `SessionUpkeep` 233, `ExecutionFixture` (prueba) 414 | Raíz heredada que M12 reduce; `MobSession` es el contenedor de campos de 01 §8 (desviación 23 de M06); `RequestBudget` y `ServiceStatusTracker` solo ganan un accesor documentado cada uno; ningún método supera 30 líneas |
| 23 | 00 §10 (revisión 7): `DecisionFreshness` lo retira M10, no M12 | El documento del módulo lo pedía y su sustituto (`DecisionAcceptor`) ya está conectado (W5) |
| 24 | Resultados de cast: se **cuentan** una vez por época de la choice (táctica y tick de inicio, que una renovación conserva, más el contador de rearmes `MobSession.choiceEpoch` que M13 añadió con DT-28) y se **registran** solo cuando cambia el resultado, la táctica o el nivel; una intención local que vence sin lanzar no registra `EXPIRED` (solo se replanifica). `IntentControl.apply` registra solo un cambio de táctica o de nivel, nunca una renovación ni una replanificación local de la misma táctica. `MobSession` guarda táctica, nivel, tick de inicio y época del último resultado | C8 y C12: un reintento cada 2 ticks, y cada replanificación local (≈1 s), llenaban el límite de 20 líneas cada 200 ticks y ocultaban la evidencia del guion §12; la época hace que un cast rearmado se cuente otra vez (DT-28) |
| 25 | Una retirada rechazada por la coordinación que planifica en local o baja a `BASE` registra `FallbackReason.RETREAT_LIMIT` (01, revisión 6) | A7: una causa propia mantiene honestos los contadores de M12; `INVALID_RESPONSE` (primera ronda) mezclaba respuestas inválidas con el tope de retiradas |
| 26 | Tras `debug release`, la capa reactiva publica otra vez una intención de `MobSession` que sigue válida (comparación de campos, sin asignaciones mientras la vista está al día) | W5: el mob no se queda en `BASE` hasta que venza |
| 27 | `SessionUpkeep.fail` (M09) cuenta `FallbackReason.INTERNAL_ERROR`, como `IntentControl.fail` | A7: la misma causa se cuenta igual venga de donde venga |
| 28 | **Sustituida el 2026-09-30 por la desviación 29 (disponibilidad en tiempo real); se conserva como historia.** **Añadida en M13 (DT-28, decisión del orquestador, informada al usuario sin objeción):** `IntentControl.cast` rearma la choice de una intención `JEV` o `ALTERNATIVE` sin vencer y ya consumida cuando ha pasado la vida de una intención local, `min(maximumIntentTicks, minimumDecisionIntervalTicks)`, desde su último consumo (`CAST`, `REJECTED_BY_MYTHICMOBS`, `SKIPPED_SHIELD` sin alternativa o `EXPIRED`), guardado en `MobSession.choiceConsumedTick`. El valor sale de `FallbackStep.localIntentTicks`, el mismo que usa la replanificación local (desviación 15), así que no hay dos fórmulas. No publica, no emite señal y no cambia `startTick` (histéresis y permanencia mínima intactas); lo que limita cuántas veces sale una skill es su `Cooldown` nativo y el cooldown extra, igual que en local. Cada rearme suma uno a `MobSession.choiceEpoch`, que forma parte de la clave con la que un resultado se cuenta una vez (desviación 24): como mucho un cast por época, y una respuesta sin intervalo transcurrido nunca vuelve a lanzar | El arnés A/B de M13 mostró que una táctica sostenida por Jev lanzaba su skill una sola vez (1 cast en 30 s en el duelo frente a 19 en local), lo que rompe la equidad del plan §12, cuya regla es un cast por intención (una replanificación local es una intención nueva). Con la temporización por defecto, el duelo da ahora 17,6 ± 1,3 casts con Jev frente a 19,1 ± 1,2 en local |
| 28 | Con un ID versionado configurado, una respuesta de otro modelo da un aviso visible (no solo con `logDecisions`) una vez por modelo distinto y con el límite del registro; el texto nombra solo modelos del servicio con forma de ID versionado o de alias conocido, nunca el valor configurado | C8 y W6: el operador ve por qué Jev no controla sin que un valor pegado en `jev.model` llegue a la consola |
| 29 | **Disponibilidad en tiempo real (decisión del usuario, 2026-09-30; sustituye el rearme por reloj de la desviación 28 y revisa DT-28):** una choice está **lista** cuando su táctica está en `MobFacts.eligibleWhenReady` de la última captura (contextos, objetivo, movimientos y alcance de M15 y M17, sin mirar cooldowns), su skill está fuera de cooldown nativo según `CombatActions.ready` (O(1), sin efectos) y el cooldown extra se cumplió. En cada ciclo reactivo (cada 2 ticks): (a) `JEV`/`ALTERNATIVE`: `IntentControl.cast` rearma la choice consumida de una intención sin vencer en cuanto está lista y el tick es posterior al consumo; (b) `LOCAL` con intención válida: si su choice está consumida o no está lista (o no tiene choice), `FallbackStep` corre `LocalPlanner.choose` sobre `eligibleWhenReady` menos las tácticas cuya skill sigue en cooldown ahora; si elige la misma táctica con la choice consumida y lista, la rearma igual que (a); si elige otra con la choice lista, planifica una intención local nueva solo si la táctica vigente cumplió `minimumDecisionIntervalTicks` desde que empezó (`MobSession.tacticStartTick`) o ya no está en `eligibleWhenReady`; (c) `LOCAL` sin intención válida: esa elección cuenta como táctica local disponible y se prefiere a la de la captura; (d) sin elección lista, una intención `LOCAL` vencida cuya táctica sigue siendo la que el planificador elige sobre `eligibleWhenReady` (es decir, solo la aparta el cooldown de su propia skill) se conserva con una intención local nueva de la misma táctica, sin señal, igual que Jev renueva su táctica; la choice espera (`NOT_ELIGIBLE`) y sale en cuanto el cooldown termina; si no, se planifica la táctica de la captura como antes. Un rearme no publica, no emite señal y no cambia `startTick`; suma uno a `choiceEpoch`, así que se mantiene un cast como mucho por época. Tras un consumo que no fue `CAST` (`MobSession.choiceConsumedByCast` falso) el rearme espera además una vida de intención local, el ritmo de reintento anterior. La elegibilidad de la replanificación al vencer y la regla de nivel de `FallbackPolicy` no cambian. **Golpe vanilla (auditoría de la disponibilidad, 2026-09-30):** una choice `VANILLA_MELEE` no tiene `Cooldown` nativo, así que solo está lista 20 ticks después de su último `CAST` (`IntentControl.VANILLA_MELEE_INTERVAL_TICKS`, `MobSession.nextMeleeTick`); `IntentControl.cast` también responde `NOT_ELIGIBLE` antes de ese plazo sin llamar al adaptador, como con el cooldown extra (desviación 7). Es el ritmo de `MeleeAttackGoal` (javap de Paper 26.2: `resetAttackCooldown` pone `adjustedTickDelay(20)`, que vale 20 porque `requiresUpdateEveryTick` es verdadero, y `tick` lo baja de uno en uno hasta 0) | Con el rearme por reloj, una skill con `Cooldown` de 30 ticks salía cada 40: la intención esperaba a la siguiente ventana de 20 ticks. El usuario decidió que cada skill controle su ritmo con su propio `Cooldown` ("se controlará el cd en cada skill"). La permanencia mínima evita el parpadeo: un cambio por disponibilidad nunca es más frecuente que un cambio por respuesta. El punto (d) es necesario para las dos garantías a la vez: sin él, al vencer la intención durante el enfriamiento el planificador cambiaba a una táctica de relleno (por ejemplo `approach`, sin choice) y la vuelta, 10 ticks después, la bloqueaba la permanencia mínima, así que la skill seguía saliendo cada 40 ticks. Si otra táctica con su propia skill lista gana según el planificador, se usa (rotación de skills) y la vuelta a la primera sí espera la permanencia mínima, así que en ese caso la cadencia de cada skill puede superar su `Cooldown` |

**Desviación 29 en el código y las pruebas (2026-09-30).** `engine/CombatActions.ready` (puerto; `CombatActionsAdapter.ready` lee `onCooldown` sin ejecutar nada, `NoCombatActions` responde falso), `domain/MobFacts.eligibleWhenReady` (constructores de 7 y 8 argumentos lo igualan a `eligibleTactics`), `adapter/server/context/FactsAssembler` (lo calcula con `ReadyTactics.eligible` y todas las skills cargadas como listas), `engine/IntentControl` (`rearmWhenReady`, `ready`, `offCooldown`; el consumo recuerda si fue `CAST`), `engine/FallbackStep` (`readyTactic`, `keptTactic`, `replanForReadiness`, `planFromChain`) y `engine/MobSession` (`choiceConsumedByCast`, `tacticStartTick`). Pruebas: `ReadinessCadenceTest` (7: cadencia de 30 ± 2 ticks con un `Cooldown` de 30 en `LOCAL` y con Jev; ninguna época lanza dos veces; una respuesta sola no relanza; ningún cambio local antes de la permanencia mínima; sin parpadeo mientras la skill se enfría; un golpe vanilla cada 20–22 ticks en `LOCAL` y con Jev), `MobFactsTest.aTacticEligibleNowMustAlsoBeEligibleWhenReady`, `CombatActionsAdapterTest.readinessReadsOnlyTheNativeCooldownWithoutCastingAnything`, `MobFactsTest.theTacticsEligibleWhenReadyAreCopiedAndDefaultToTheTacticsEligibleNow`, `FactsAssemblerTest.theTacticsEligibleWhenReadyKeepTheReachOfTheCapture` y `aSkillOnItsNativeCooldownIsNotEligible` (ampliada); `CastSemanticsTest` e `IntentPublicationTest` renombradas a la disponibilidad. Los dobles de `ExecutionFixture` y `LoadFixture` modelan un `Cooldown` nativo de 20 ticks por defecto (`NativeCooldowns`); `FallbackChainTest.theLocalPlannerBreaksTheGuardOfABlockingTargetWithTheDeclaredBestWhen` fija cooldown 0 para que decida `BestWhen` y no la rotación de skills.

### Qué hacen los mobs ahora

- **Con Jev** (clave configurada y servicio `READY`): un mob vinculado con metas instaladas y un jugador percibido como objetivo juega en `LOCAL` mientras espera una respuesta (la primera, o la siguiente cuando su intención vence antes de que llegue), salvo tras una respuesta rechazada por confianza baja. Cada respuesta se acepta por pregunta: solo para la misma generación, sesión activa en el mismo encuentro, mismo catálogo, mismo modelo, captura de menos de `maxDecisionAgeMillis` y mismo objetivo. La táctica de mayor probabilidad aplicable se publica en `IntentBoard` en el mismo tick (nivel `JEV`) y emite una vez `PREDICTOR_<TÁCTICA>` si es nueva. Su skill se lanza como mucho una vez por época de la choice (una época nueva en cuanto la skill vuelve a estar lista, desviación 29: sale al ritmo de su `Cooldown`) y **espera** (reintento cada 2 ticks dentro de la intención) mientras esté en cooldown, mientras una skill de cuerpo a cuerpo (`MELEE_ATTACK`) o un golpe vanilla no tenga el objetivo al alcance, mientras sus condiciones `REQUIRED`/`CANCEL` no se cumplan y, si golpea al objetivo, mientras dure su invulnerabilidad o el escudo la bloquee (con `FLANK`, hasta salir del ángulo; si no, pasa a la siguiente alternativa). Solo cambia de táctica si la nueva supera a la vigente por `switchMargin` y la vigente duró `minimumDecisionIntervalTicks`. Una respuesta con confianza baja se rechaza: la intención vigente sigue hasta vencer y después el mob cede a su IA base; tras `lowConfidenceStreakForLocal` rechazos seguidos (3) pasa a `LOCAL` con `LOW_CONFIDENCE`, y la primera respuesta aceptada lo devuelve a `JEV` y reinicia la racha. Cada 2 ticks dispara las contingencias preparadas una sola vez y pasa a la siguiente alternativa si la táctica deja de ser elegible; en cada evaluación comprueba el progreso (`NO_PROGRESS` tras dos). En un encuentro, como mucho `max(1, miembros / 3)` mobs se retiran a la vez.
- **Sin Jev** (sin clave, clave o modelo inválidos, servicio en pausa o comprobándose, presupuesto agotado o confianza baja repetida): el mismo mob pasa a `LOCAL` en cuanto no le queda una intención de Jev válida (en el mismo tick del fallo si no tenía, o ≤2 ticks tras vencer la vigente): el planificador local elige por contexto y prioridad, con confianza 0 y vigencia corta (`min(maximumIntentTicks, minimumDecisionIntervalTicks)`), se replanifica al vencer o, antes, en cuanto una táctica con la choice lista le toca según el planificador (respetando la permanencia mínima al cambiar de táctica), lanza sus skills al ritmo de su `Cooldown` con las mismas esperas y emite la señal cuando cambia de táctica. Coste cero, sin red. La primera respuesta aceptada después lo devuelve a `JEV`.
- **`BASE`**: sin táctica local (ninguna elegible), sin objetivo percibido o fuera de combate, con el objetivo cambiado tras una respuesta, tras una respuesta de confianza baja sin intención válida (racha por debajo del umbral), tras `NO_PROGRESS` sin alternativa, una retirada rechazada sin táctica local o un error interno, la vista pasa a `IntentView.base`: las condiciones `predictor*` responden falso y la IA base del conjunto generado (o la de MythicMobs si las metas no se instalaron) toma el control en el siguiente ciclo de metas, sin reinstalar nada. Con el servicio sano, sin racha de confianza baja y en un encuentro abierto, una intención vencida ya no baja a `BASE`. Un mob en `LOCAL` escribe en el registro solo sus cambios de táctica, de nivel o de resultado.

### Correcciones tras la auditoría (2026-09-29)

`./gradlew clean build javadoc --offline` pasa sin avisos con **1091 pruebas, 0 fallos** (10 nuevas). Sin llamadas de red, sin leer `.env` y sin servidor.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** una skill `MYTHIC_SKILL` se lanzaba en el tick de adopción a cualquier distancia, y unas condiciones que fallaban la consumían (`REJECTED_BY_MYTHICMOBS`) | Comprobación previa sin efectos de las condiciones `REQUIRED`/`CANCEL` (`SkillConditions`, `CastSource.conditionsHold/conditionsHoldAt`) → `NOT_ELIGIBLE` y reintento; después un solo `executeIfUsable` (`Chance`, evento cancelado o sin carga → `REJECTED`); una skill `MELEE_ATTACK` espera al alcance como el golpe vanilla (desviaciones 5 y 8; DT-12 y paso 12.3 del guion actualizados) | `CombatActionsAdapterTest.failingSkillConditionsWaitWithoutConsumingTheChoiceAndAreCheckedWithoutExecuting`, `conditionsAreOnlyEvaluatedOnceEveryOtherCheckPasses`, `aMeleeSkillWaitsUntilItsTargetIsInReachWhileARangedSkillDoesNot`, `aRefusedOrUnloadedSkillIsRejectedByMythicMobs` |
| **Bloqueante:** invulnerabilidad y escudo se aplicaban a toda choice con objetivo, también a las que no golpean | Solo para choices que golpean al objetivo (`HitChecks.damaging`: `VANILLA_MELEE`, o `RequiresTarget` con un valor de ataque o daño) | `aSkillThatDoesNotHitTheTargetIgnoresShieldsAndInvulnerability` (`defense`, `HEALS_CASTER`, sin objetivo: nunca `SKIPPED_SHIELD`), `theDamageAndReachRulesFollowTheDeclaredValues` |
| **Bloqueante:** cada reintento `NOT_ELIGIBLE`/`DEFERRED_INVULNERABLE` contaba la métrica y escribía una línea cada 2 ticks (C12) | Se cuenta y registra solo el cambio de resultado de la misma intención (desviación 24) | `CastSemanticsTest.aRetriedOutcomeIsCountedAndLoggedOnceForTheSameIntent` |
| **Bloqueante:** la casilla de M04.6 decía "(M10, en servidor)" y se marcó con evidencia local | Desmarcada con "en servidor" restaurado; la evidencia local queda como nota | — |
| Corrección del coordinador: tras responder Jev, una intención que vence antes de la respuesta siguiente dejaba el mob en `BASE` hasta que llegaba (parpadeo entre táctica e IA base) | Espera en `LOCAL` también con la solicitud en vuelo o la evaluación siguiente a menos de un latido (desviación 13; la segunda ronda la limita a racha 0 y la simplifica a "encuentro abierto"); `FallbackPolicy` intacta | `FallbackChainTest.anIntentThatExpiresWhileTheNextRequestIsInFlightWaitsLocallyAndTheNextAnswerReturnsToJev`, `betweenTwoHeartbeatsAnExpiredIntentWaitsLocallyInsteadOfFlickeringToTheBaseAi` |
| `withoutRetreat` bajaba sin causa o con `EXPIRED` | `INVALID_RESPONSE` en `LOCAL` y en `BASE`; sustituido por `RETREAT_LIMIT` en la segunda ronda (desviación 25) | `EncounterCoordinationTest.aRetreatRefusedByTheCoordinationRecordsRetreatLimitWhetherItPlansLocallyOrFallsToBase` |
| Tras `debug release` una intención válida quedaba en `BASE` hasta vencer | Se publica otra vez (desviación 26) | `FallbackChainTest.aValidIntentIsPublishedAgainAfterTheDebugCommandReleasesTheMob` |
| `SessionUpkeep.fail` no contaba `INTERNAL_ERROR` | Cuenta la causa (desviación 27) | `FallbackChainTest.aFailingProbeInTheReactiveCycleDropsOnlyThatSessionToBase` |
| Un ID versionado distinto solo se veía con `logDecisions` | Aviso visible una vez por modelo, sin el valor configurado (desviación 28) | `ModelExpectationTest.aVersionedIdRefusesEveryOtherModelWithOneVisibleWarningPerModel` |
| Documentos | M05.5 apunta a `engine.ProgressWatch`; M11.4 dice que `addThreat` ya está implementado y probado y que la generación la comprueba `engine`; tamaños de la desviación 22; evidencia de `executeIfUsable` corregida aquí y en DT-12; fila `ReactiveLayer` de 01 (recibe la sonda, no sondea) | — |

### Segunda ronda de correcciones (2026-09-29)

`./gradlew clean build javadoc --offline` pasa sin avisos con **1093 pruebas, 0 fallos** (2 nuevas). Sin llamadas de red, sin leer `.env` y sin servidor.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** con la espera local de la primera ronda, una sola respuesta de confianza baja dejaba el mob en `LOCAL` con `EXPIRED`; `LOW_CONFIDENCE` nunca se registraba (plan §6, M10.5, A7) | La espera local exige racha 0: sin intención válida tras un rechazo, `BASE`; con la racha completa, `LOCAL` con `LOW_CONFIDENCE`; una adopción reinicia la racha y la espera (desviación 13 corregida) | `HysteresisScenarioTest.oneLowConfidenceAnswerYieldsToTheBaseAiAndAFullStreakGoesLocalUntilAnAdoptionRestoresTheLocalWait` |
| Un mob local escribía unas 25 líneas cada 200 ticks (renovaciones, `EXPIRED` y `NOT_ELIGIBLE` de cada replanificación) | Solo se registran cambios de táctica, nivel o resultado; una intención local que vence no registra `EXPIRED` (desviación 24) | `FallbackChainTest.aLocalMobKeepsItsLogWellUnderTheLimitWhileItWaitsOrCastsAgain` (≤ 4 líneas en 400 ticks, esperando o lanzando) |
| Paso 12.3 del guion inexacto para `JEV` y `LOCAL` | Líneas esperadas por nivel | — |
| `withoutRetreat` usaba `INVALID_RESPONSE` | `FallbackReason.RETREAT_LIMIT` (01, revisión 6, con su línea en el registro de cambios) en `LOCAL` y en `BASE` (desviación 25) | `EncounterCoordinationTest.aRetreatRefusedByTheCoordinationRecordsRetreatLimitWhetherItPlansLocallyOrFallsToBase` |
| La cláusula "evaluación a menos de un latido" de `answerComing` nunca decidía | Código simplificado a "encuentro abierto" y documentado (desviación 13) | Suite existente |
| 01: fila de `MobSession` sin la clave del último resultado y `jevAnswered` descrito como "la primera respuesta" | Fila corregida (revisión 6) | — |
| Javadoc de `SkillConditions` con el orden de las listas invertido | Orden de `isUsable` (objetivo, disparador, lanzador) en el código y en el Javadoc, también en `MythicSkillExecutor` | Suite existente |

### Pendiente de verificación en servidor

- Guion de M01, sección 12: modo local sin clave, señales `~onSignal:PREDICTOR_<TÁCTICA>` una vez por táctica (DT-11), un golpe por intención y ningún golpe base mientras ThePredict controla (DT-12, M10.9), aplazamiento por invulnerabilidad (DT-26), escudo y `FLANK` con daño completo fuera del ángulo, `NO_PROGRESS` con un objetivo sobre pilares, cesión a la IA base al perder el objetivo, `debug intent` por encima del modo local.
- Aceptación sin marcar de M10.9 ("no hay dos golpes") y escenarios de M13 del apartado "Terminado".

### Pendiente de la cuenta real (llamada de pago, requiere aprobación del usuario)

- Respuestas reales de Jev aplicadas en combate (paso 12.8 del guion): nivel `JEV`, contingencias, alternativas, `effectiveModel` real frente a `jev.model` y recuperación tras un 429/529 o una caída de red. Los umbrales (0,5 y 0,15) siguen sin calibrar (M13).

### Pendiente para módulos posteriores

- M11 (hecho, ver el estado de implementación de M11): `engine.ThreatChanges` llama a `CombatActions.addThreat` con la clave de la sesión vigente; el adapter descarta además a un jugador muerto (desviación 8 de M11).
- M12: mostrar en `inspect` `lastFallbackReason`, `lastOutcome`, la intención y las rachas de `MobSession`, y en `status` los contadores de `FallbackReason`, `ExecutionOutcome` y `DiscardReason` y el tramo `APPLY`; retirar `TriggeredSkillCast` con el comando de depuración. **Hecho en M12** (ver su [estado de implementación](M12-Diagnostico-instalacion-retiro-OpenRouter.md#estado-de-implementación)).
- M13: calibrar `minimumConfidence`, `switchMargin` y la racha por versión de modelo; medir la alternancia `LOCAL`/`BASE` de un mob local atascado, las contingencias usadas, el efecto de aplazar por invulnerabilidad y el coste de la capa reactiva con muchos mobs. **Medido localmente en [M13](M13-Suite-carga-latencia-calidad.md#estado-de-implementación)**: un mob local atascado cae a nivel 4 29 veces por minuto (2 ticks cada vez) y reemite su señal; el arnés A/B mostró que una táctica renovada por Jev no volvía a lanzar su skill (desviación 15); resuelto por decisión del orquestador (informada al usuario) con el rearme por reloj (desviación 28, DT-28). La calibración de umbrales sigue pendiente de la cuenta real.
