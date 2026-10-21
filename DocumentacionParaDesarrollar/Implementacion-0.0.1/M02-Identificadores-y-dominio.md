# M02. Identificadores y contratos del dominio

> Estado: Local listo (2026-09-29; sin evidencia de servidor, no la exige) · Entrega: A · Depende de: M01 (parcial) · Desbloquea: M03–M10 · Diseño: [plan §4 y §7](../Plan-Jev-y-combate-tactico.md) · Reglas: [00](00-Arquitectura-y-reglas.md) · Dominio: [01](01-Modelo-de-dominio.md)

## Objetivo

Crear `domain` exactamente como lo define 01, con sus invariantes y reglas puras probadas, y hacer que las fronteras entre paquetes se comprueben en cada build. Al terminar, el resto de módulos construye sobre tipos estables sin Bukkit, MythicMobs, HTTP ni Gson.

## Alcance

### Incluye

- Paquetes `domain` y `engine` y la prueba de arquitectura.
- Todos los records y enums de 01 §2 a §6 con validación en el constructor canónico.
- Las reglas puras de 01 §7: `Eligibility`, `LocalPlanner`, `IntentPolicy`, `ContingencyPolicy` y `FallbackPolicy`.
- La transición de `PredictValue` y la retirada de lo que ya no se usa, sin romper el camino actual con OpenRouter.

### No incluye

- El estado mutable de `engine` (M04, M06 y M09).
- La lectura de YAML (M03) y cualquier adapter.
- La calibración de umbrales (M13).

## Base existente

| Archivo | Acción |
|---|---|
| `src/main/java/akyrion/thepredict/decision/PredictValue.java` | Se mueve a `domain` y gana `IGNORES_SHIELD` |
| `src/main/java/akyrion/thepredict/decision/PredictionPlanner.java` | Referencia de `Eligibility` y `LocalPlanner`; se conserva sin cambios hasta M09 (W5) |
| `src/main/java/akyrion/thepredict/decision/DecisionPlan.java` | Se conserva hasta que M09 retire el runner actual |
| `src/main/java/akyrion/thepredict/decision/DecisionRequest.java`, `DecisionProvider.java`, `ProviderHealth.java`, `ProviderFailure.java` | Se conservan para el camino actual; los sustitutos nuevos viven en `domain` y `engine` |
| `src/main/java/akyrion/thepredict/snapshot/CombatSnapshot.java`, `VitalSnapshot.java` | Se conservan hasta M07; `domain.CombatFacts` los sustituye |
| `src/main/java/akyrion/thepredict/stance/` (vacío) | Se elimina |
| Tipos mínimos creados en M01 (`MobKey`, `TacticId`, `Movement`, `ControlLevel`, `IntentView`) | Se completan y se prueban aquí |
| `src/test/java/akyrion/thepredict/decision/PredictionPlannerTest.java` | Sus casos se trasladan a `LocalPlannerTest` y `EligibilityTest` |

## Subpuntos

### M02.1 Paquetes y prueba de arquitectura

- **Propósito:** que las reglas D1–D7 de 00 fallen en el build, no en una revisión.
- **Diseño:**
  - Se crean `akyrion.thepredict.domain` y `akyrion.thepredict.engine`.
  - `ArchitectureRulesTest` recorre `src/main/java` con `Files.walk` y obtiene el paquete de cada archivo por su ruta.
  - Extrae importaciones con la expresión `^import\s+(static\s+)?([\w.]+)` y, además, busca referencias completamente calificadas en líneas de código a `org.bukkit.`, `io.papermc.`, `com.destroystokyo.paper.`, `io.lumine.mythic.`, `java.net.http.` y `com.google.gson.`, porque el código actual usa algunas sin `import`.
  - Tabla de prefijos permitidos:

    | Paquete | Prefijos externos permitidos | Paquetes propios permitidos |
    |---|---|---|
    | `domain` | `java.` | `domain` |
    | `engine` | `java.` | `domain`, `engine` |
    | `config` | `java.`, `org.bukkit.configuration.` | `domain`, `config` |
    | `adapter.jev` | `java.`, `com.google.gson.` | `domain`, `engine`, `adapter.jev` |
    | `adapter.server` | `java.`, `org.bukkit.`, `io.papermc.`, `com.destroystokyo.paper.`, `io.lumine.mythic.` | `domain`, `engine`, `adapter.server` |
    | `admin` | `java.`, `org.bukkit.command.` | `domain`, `engine`, `admin` |

  - Paquetes heredados excluidos hasta su retirada (W5): raíz `akyrion.thepredict`, `decision`, `snapshot`, `adapter.mythicmobs`, `adapter.paper` y `adapter.openrouter`. La lista vive en la prueba como constante y M12 la deja vacía.
  - El mensaje de fallo indica archivo, línea, importación y regla (`D1`–`D7`).
- **Archivos:** crear `src/test/java/akyrion/thepredict/ArchitectureRulesTest.java`.
- **Reglas:** D1–D9, P5.
- **Pruebas:** la propia prueba, más un caso negativo con una fuente sintética en un directorio temporal.
- **Aceptación:**
  - [x] La prueba pasa con el código actual gracias a la lista de heredados.
  - [x] Añadir `import org.bukkit.Bukkit;` a una clase de `domain` rompe el build con la regla D1.
  - [x] La lista de heredados tiene el módulo que retira cada paquete.

### M02.2 Identificadores

- **Propósito:** identidad fuerte y validada para sesiones, encuentros, tácticas y preguntas (00 §6).
- **Diseño:**
  - Records de 01 §2: `MobKey`, `EncounterId`, `TacticId`, `ChoiceId`, `ContextId`, `ParticipantAlias` y `QuestionKey`.
  - Patrones compilados una vez como `private static final Pattern`, que es inmutable.
  - `ParticipantAlias#text()` devuelve `"player_" + index`; `QuestionKey.of(int n)` construye `q<n>` con `0 <= n <= 9999`.
  - `toString()` de los identificadores textuales devuelve el valor tal cual, para que los mensajes de error sean legibles.
  - IDs reservados que el binder rechaza como tácticas del autor: `none` y el prefijo `debug_`. Ver "Riesgos".
- **Archivos:** `domain/MobKey.java`, `EncounterId.java`, `TacticId.java`, `ChoiceId.java`, `ContextId.java`, `ParticipantAlias.java`, `QuestionKey.java` y sus pruebas.
- **Reglas:** I1–I6, C2, C4.
- **Pruebas:** longitudes 3 y 40 de `TacticId` válidas, 2 y 41 inválidas; mayúsculas, guiones y espacios rechazados; `generation` negativa rechazada; `index = 0` rechazado; igualdad por valor.
- **Aceptación:**
  - [x] Todos los patrones coinciden con 00 §6.
  - [x] Ningún identificador acepta `null`.

### M02.3 Perfil táctico

- **Propósito:** representar un perfil ya validado como un valor inmutable que las reglas puedan consultar.
- **Diseño:**
  - Records y enums de 01 §3.
  - `TacticalProfile` valida referencias cruzadas: cada `Tactic.choice` existe en `choices`; cada `ContextRule` referencia tácticas existentes; al menos una táctica; `objective` no vacío.
  - `Tactic`: `FLANK` exige choice; `priority` entre 0 y 100.
  - `MovementSettings`: `0 < bandInnerBlocks < bandOuterBlocks`; velocidades finitas y positivas; `arenaRadiusBlocks > bandOuterBlocks` si existe.
  - `DecisionTiming`: `heartbeatTicks >= minimumDecisionIntervalTicks`; `maximumIntentTicks >= minimumDecisionIntervalTicks`; confianza y margen en `[0, 1)`; `contingenciesPerMob` entre 0 y 2.
  - `ThreatWeights`: valores finitos y no negativos.
  - La regla de especies con `Brain` no está aquí: el dominio no conoce especies y la aplica el binder (M03.4).
  - Los mapas conservan el orden de declaración (`LinkedHashMap` copiado a una vista no modificable), porque el orden del autor desempata prioridades iguales.
- **Archivos:** `domain/TacticalProfile.java`, `Tactic.java`, `Movement.java`, `SkillChoice.java`, `ChoiceKind.java`, `PredictValue.java`, `ContextRule.java`, `MovementSettings.java`, `HealthThresholds.java`, `ThreatWeights.java`, `DecisionTiming.java`.
- **Reglas:** C2, C3, C5, A2.
- **Pruebas:** un caso por invariante, con el mensaje de error esperado; perfil mínimo válido; orden estable de tácticas.
- **Aceptación:**
  - [x] Cada invariante de 01 §3 tiene una prueba que la rompe.
  - [x] Los mensajes nombran el campo en inglés, como los usará M03 en sus rutas.

### M02.4 Hechos de combate

- **Propósito:** un estado de combate pequeño, tipado y sin referencias vivas, que viaja a otros hilos.
- **Diseño:**
  - Records y enums de 01 §4.
  - Topes estructurales como constantes públicas: `CombatFacts.MAX_MOBS = 32`, `MAX_PARTICIPANTS = 16` (el límite actual de `NearestK`) y `MAX_EVENTS = 32`.
  - `CombatFacts` valida alias únicos, que cada `TargetRelation.target` exista en `participants`, `combatSeconds >= 0` y que `captureTick >= 0`.
  - `ParticipantFacts.threatRank` empieza en 1 y es único dentro del encuentro.
- **Archivos:** `domain/CombatFacts.java`, `MobFacts.java`, `TargetRelation.java`, `ParticipantFacts.java`, `EncounterEvent.java` `CaptureContext.java` y los enums `HealthBand`, `DistanceBand`, `Trend`, `ArmorBand`, `MobCue`, `TargetCue` y `EventType`.
- **Reglas:** A4, A5, C2.
- **Pruebas:** topes exactos y excedidos; alias duplicado; objetivo inexistente; colecciones de entrada modificadas después de construir no alteran el record.
- **Aceptación:**
  - [x] Ningún campo guarda entidades, coordenadas ni texto de jugadores.
  - [ ] Los topes están en un solo lugar y M07 los usa. (En un solo lugar: sí, `CombatFacts.MAX_*`; el uso por M07 se comprueba en M07.)

### M02.5 Decisión remota

- **Propósito:** contratos de solicitud y respuesta independientes de HTTP.
- **Diseño:**
  - Records de 01 §5.
  - `DecisionRequest` exige preguntas no vacías con `QuestionKey` únicas y mobs presentes en `facts`.
  - `QuestionSpec.options` tiene entre 2 y 255 tácticas sin repetir.
  - `Answer` exige probabilidades finitas en `[0, 1]` cuya suma difiera de 1 como máximo en 0,02, y `confidence` en `[0, 1]`.
  - `Answer#best()` devuelve la táctica de mayor probabilidad y desempata por orden alfabético de `TacticId`, para que el resultado sea determinista.
  - `Answer#ranked()` devuelve la lista completa en ese mismo orden; `probabilityOf(TacticId)` devuelve 0 si la táctica no aparece.
- **Archivos:** `domain/DecisionRequest.java`, `QuestionSpec.java`, `Situation.java`, `DecisionResult.java`, `Answer.java`, `FailureKind.java`, `ServiceCheck.java`, `ServiceState.java`.
- **Reglas:** C3, C7, I3.
- **Pruebas:** empate con desempate estable; `NaN` e infinitos rechazados; suma 0,97 aceptada y 0,95 rechazada; opciones duplicadas rechazadas.
- **Aceptación:**
  - [x] `DecisionResult` es `sealed` y solo tiene `Answered` y `Failed`.
  - [x] La tolerancia de suma está documentada en el Javadoc de `Answer`.

### M02.6 Intención y control

- **Propósito:** la intención que ejecuta un mob y la vista mínima que leen las condiciones.
- **Diseño:**
  - Records y enums de 01 §6.
  - `TacticalIntent#withSkillConsumed()` devuelve una copia; `expired(long nowNanos)` compara con `nowNanos - expiresAtNanos >= 0` para tolerar el desbordamiento de `System.nanoTime()`.
  - `TacticalIntent#toView()` construye la `IntentView` con `mob.generation()`.
  - `alternatives` no contiene la táctica actual; `contingencies` solo usa `IF_TARGET_IN_MELEE` y `IF_OWN_HEALTH_CRITICAL`.
  - `IntentView#controls(long nowNanos)` es verdadero si `level` no es `BASE` y `nowNanos - expiresAtNanos < 0`.
  - `IntentView.base(int generation)` usa `Movement.NONE` y la táctica reservada `none`.
  - `IntentView.ABSENT`, constante con `generation = -1`, representa "sin sesión" sin asignar objetos ni usar `null` (ver "Riesgos").
- **Archivos:** `domain/TacticalIntent.java`, `ControlLevel.java`, `IntentView.java`, `SessionState.java`, `ExecutionOutcome.java`, `FallbackReason.java`, `DiscardReason.java`, `MobProbe.java`, `MovementProgress.java`, `GoalSummary.java`, `InstallResult.java`.
- **Reglas:** A6, C4, C6, T3.
- **Pruebas:** vencimiento justo en el límite y con valores cercanos al desbordamiento; `base` y `ABSENT` nunca controlan; `withSkillConsumed` no altera el original.
- **Aceptación:**
  - [x] `IntentView` no tiene campos mutables ni métodos que asignen memoria (solo la fábrica estática `base(int)` crea una vista, por naturaleza; `controls` y `ABSENT` no asignan).
  - [x] La correspondencia con los estados del plan (01 §6) está en el Javadoc de `ControlLevel`.

### M02.7 Reglas puras

- **Propósito:** toda la lógica de decisión local se prueba sin servidor ni reloj.
- **Diseño:** clases `final` sin estado; el tiempo entra como parámetro.

  | Regla | Firma | Comportamiento |
  |---|---|---|
  | `Eligibility` | `Set<TacticId> eligible(TacticalProfile, Set<ContextId> active, Set<ChoiceId> readyChoices, Set<Movement> availableMovements)` y `Optional<TacticId> direct(...)` | La regla de contexto activa de mayor prioridad acota las tácticas; sin ninguna activa, todas. Filtra choices no listas y movimientos no disponibles. Orden: prioridad descendente y orden de declaración. |
  | `LocalPlanner` | `Optional<Tactic> choose(TacticalProfile, Set<TacticId> eligible)` | Mayor prioridad; empate por orden de declaración. Hereda la semántica de `PredictionPlanner`. |
  | `IntentPolicy` | `accept(Optional<TacticalIntent> current, MobKey, Answer now, Map<Situation, Answer> contingencies, Set<TacticId> eligibleNow, TacticalProfile, long tick, long nowNanos)` | Filtra probabilidades a `eligibleNow`. Si la confianza es menor que `minimumConfidence`, conserva la intención vigente o rechaza con `LOW_CONFIDENCE`. Cambia de táctica solo si la nueva supera a la vigente por `switchMargin` y la vigente cumplió `minimumDecisionIntervalTicks`; si no, renueva la vigente. Guarda hasta 3 alternativas y las contingencias con confianza suficiente. |
  | `IntentPolicy` | `Optional<TacticalIntent> nextAlternative(TacticalIntent, Set<TacticId> eligibleNow)` | Primera alternativa todavía elegible, con nivel `ALTERNATIVE` y el mismo vencimiento. |
  | `ContingencyPolicy` | `Optional<TacticId> triggered(TacticalIntent, MobProbe)` | `IF_TARGET_IN_MELEE` si la distancia pasa a `MELEE`; `IF_OWN_HEALTH_CRITICAL` si la salud pasa a `CRITICAL`. Cada contingencia se usa una vez. |
  | `FallbackPolicy` | `level(ServiceState, boolean budgetAvailable, int lowConfidenceStreak, boolean sessionValid, boolean intentValid, boolean localTacticAvailable)` | Sesión inválida o error → `BASE`; intención válida → `JEV` o `ALTERNATIVE`; servicio en pausa, sin presupuesto o racha ≥ `lowConfidenceStreakForLocal` (3) → `LOCAL` si hay táctica local, si no `BASE`. |

  `IntentPolicy.accept` devuelve `IntentOutcome` y `FallbackPolicy.level` devuelve `FallbackDecision` (01 §7), para que la causa viaje con el resultado. `CoordinationPolicy` y `ThreatPolicy` también son reglas de 01 §7, pero se crean en M10 y M11.
- **Archivos:** `domain/Eligibility.java`, `LocalPlanner.java`, `IntentPolicy.java`, `IntentOutcome.java`, `ContingencyPolicy.java`, `FallbackPolicy.java`, `FallbackDecision.java` y sus pruebas.
- **Reglas:** A2, A6, A7, C6, P1.
- **Pruebas:**
  - Contextos simultáneos con distinta prioridad; empate de prioridades.
  - Choice en cooldown que excluye su táctica pero no las de solo movimiento.
  - Táctica directa elegible y no elegible.
  - Confianza justo en el umbral y por debajo; cambio justo en el margen y por debajo; cambio antes de la permanencia mínima.
  - Alternativas agotadas; contingencia usada una sola vez.
  - Cada combinación de entradas de `FallbackPolicy` que cambia el nivel.
- **Aceptación:**
  - [x] Ninguna regla lee relojes ni estado global.
  - [x] Los casos de `PredictionPlannerTest` tienen equivalente.
  - [x] Cada rechazo de `IntentPolicy` y cada bajada a `LOCAL` o `BASE` de `FallbackPolicy` lleva un `FallbackReason`. Los pasos `JEV → ALTERNATIVE` (`IntentPolicy.nextAlternative` y `ContingencyPolicy.fire`) no llevan causa por diseño: devuelven la nueva intención, no una decisión de fallback, y el motivo (alternativa o contingencia) queda en su nivel `ALTERNATIVE`.

### M02.8 Transición

- **Propósito:** introducir el dominio nuevo sin romper la base que funciona hoy (W5).
- **Diseño:**
  - `PredictValue` se mueve a `domain` y se actualizan sus importaciones en `BoundProfile`, `MythicProfileBinder` y las pruebas.
  - `stance/` se elimina porque está vacío.
  - `PredictionPlanner`, `DecisionPlan` y el resto de `decision` y `snapshot` se conservan intactos hasta que M09 retira el runner actual; M12 borra lo que quede.
  - Ningún tipo nuevo se usa todavía desde el runner: la conexión llega en M09 y M10.
- **Archivos:** mover `decision/PredictValue.java`; borrar `stance/`; actualizar importaciones.
- **Reglas:** W5, D9.
- **Pruebas:** la suite completa actual sigue verde.
- **Aceptación:**
  - [x] `./gradlew clean build javadoc` pasa sin cambios de comportamiento.
  - [x] Las 67 pruebas existentes siguen pasando o se trasladan con el mismo caso (la suite de partida ya tenía 139 tras M01; todas siguen verdes).

## Riesgos y decisiones abiertas

- **Incorporado en 01 (revisión 2):** reservar los `TacticId` `none` y el prefijo `debug_`, y añadir la constante `IntentView.ABSENT` (`generation = -1`) para leer "sin sesión" sin `null` ni asignaciones.
- **Incorporado en 01 (revisión 2):** un resultado tipado para `IntentPolicy`, por ejemplo `IntentOutcome` sellado con `Adopted`, `Renewed` y `Rejected(FallbackReason)`, y otro para `FallbackPolicy`, por ejemplo `FallbackDecision(ControlLevel, Optional<FallbackReason>)`. Sin ellos, la causa exigida por A7 no tiene dónde viajar.
- 00 §10 (revisión 2) ya asigna la retirada de `PredictionPlanner` y `DecisionPlan` a M09, según W5.
- La tolerancia de suma de probabilidades (0,02) es una elección de diseño; M08 la confirma con respuestas reales de Jev.

## Terminado

- [x] Todos los tipos de 01 §2–§7 existen con Javadoc y pruebas (salvo `CoordinationPolicy` y `ThreatPolicy`, que 01 §7 y M02.7 asignan a M10 y M11).
- [x] `ArchitectureRulesTest` pasa y detecta una violación sintética.
- [x] Los tipos añadidos en 01 (revisión 2) a partir de las propuestas de este módulo existen con Javadoc y pruebas.
- [x] `./gradlew clean build javadoc` pasa.

## Estado de implementación

> **Local listo (2026-09-29).** M02 no exige pruebas en servidor. `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` pasa sin avisos de compilación ni de Javadoc: 313 pruebas, 0 fallos (139 de partida + 174 nuevas). Incluye las correcciones de la auditoría (ver abajo).

### Archivos

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…`) |
|---|---|
| M02.1 | `src/test/java/akyrion/thepredict/ArchitectureRulesTest.java`; paquetes `domain` y `engine` (ya creados en M01) |
| M02.2 | `domain/EncounterId`, `ChoiceId`, `ContextId`, `ParticipantAlias`, `QuestionKey`; `MobKey` y `TacticId` de M01 sin cambios (ya cumplían 01) |
| M02.3 | `domain/TacticalProfile`, `Tactic`, `SkillChoice`, `ChoiceKind`, `PredictValue` (movido desde `decision`, con `IGNORES_SHIELD`), `ContextRule`, `MovementSettings`, `HealthThresholds`, `ThreatWeights`, `DecisionTiming`; `Movement` de M01 sin cambios |
| M02.4 | `domain/CombatFacts`, `MobFacts`, `TargetRelation`, `ParticipantFacts`, `EncounterEvent`, `CaptureContext`, `HealthBand`, `DistanceBand`, `Trend`, `ArmorBand`, `MobCue`, `TargetCue`, `EventType` |
| M02.5 | `domain/DecisionRequest`, `QuestionSpec`, `Situation`, `DecisionResult`, `Answer`, `FailureKind`, `ServiceCheck`, `ServiceState` |
| M02.6 | `domain/TacticalIntent`, `SessionState`, `ExecutionOutcome`, `FallbackReason`, `DiscardReason`, `MobProbe`, `MovementProgress`, `GoalSummary`, `InstallResult`; `ControlLevel` e `IntentView` de M01 sin cambios (ya tenían `controls`, `base`, `ABSENT` y la correspondencia de estados) |
| M02.7 | `domain/Eligibility`, `LocalPlanner`, `IntentPolicy`, `IntentOutcome`, `ContingencyPolicy`, `FallbackPolicy`, `FallbackDecision` |
| Apoyo | `domain/DomainChecks` (paquete privado, validaciones comunes de los constructores canónicos) |
| M02.8 | Borrado `decision/PredictValue.java` y el directorio vacío `stance/`; importaciones actualizadas en `adapter/mythicmobs/BoundProfile`, `MythicProfileBinder` y `src/test/…/openrouter/DecisionLifecycleSuiteTest`. `PredictionPlanner`, `DecisionPlan`, `decision` y `snapshot` intactos (W5) |

### Pruebas nuevas (174)

`ArchitectureRulesTest` (11: código actual, declaración `package` que no coincide con la ruta → D9, nombres completamente calificados de Guava, `javax` y SLF4J en `domain` → D1, `import` de Bukkit en `domain` → D1 con archivo y línea, nombre completamente calificado sin `import` → D1, adapter que importa otro adapter → D7, `engine` que importa `config` → D2, `java.net.http` solo en `adapter.jev`, comentarios y literales ignorados, paquete sin regla, cada heredado con su módulo). En `src/test/java/akyrion/thepredict/domain/`: `EncounterIdTest`, `ChoiceIdTest`, `ContextIdTest`, `ParticipantAliasTest`, `QuestionKeyTest`, `TacticTest`, `SkillChoiceTest`, `ContextRuleTest`, `MovementSettingsTest`, `HealthThresholdsTest`, `ThreatWeightsTest`, `DecisionTimingTest`, `TacticalProfileTest`, `PredictValueTest`, `CombatFactsTest`, `MobFactsTest`, `ParticipantFactsTest`, `EncounterEventTest`, `CaptureContextTest`, `TargetRelationTest`, `QuestionSpecTest`, `DecisionRequestTest`, `AnswerTest`, `DecisionResultTest`, `ServiceCheckTest`, `TacticalIntentTest`, `MobProbeTest`, `MovementProgressTest`, `GoalSummaryTest`, `InstallResultTest`, `ExecutionOutcomeTest`, `EligibilityTest`, `LocalPlannerTest`, `IntentPolicyTest`, `ContingencyPolicyTest`, `FallbackPolicyTest`; `DomainFixtures` es un ayudante de prueba. `PredictionPlannerTest` se conserva porque `PredictionPlanner` sigue vivo (W5); sus cuatro casos tienen equivalente en `EligibilityTest` y `LocalPlannerTest`.

Comprobación manual de M02.1: añadir `import org.bukkit.Bukkit;` a `domain/EncounterId.java` hizo fallar `./gradlew build` en `ArchitectureRulesTest`; se revirtió.

### Desviaciones y decisiones

| Desviación o decisión | Motivo |
|---|---|
| `IntentPolicy.accept` tiene dos sobrecargas: `accept(MobKey, …)` sin intención actual y `accept(TacticalIntent current, …)`, en lugar de `Optional<TacticalIntent> current` | C4 prohíbe `Optional` como parámetro |
| `IntentPolicy.nextAlternative(intent, eligibleNow, profile, tick)` recibe además el perfil y el tick | Sin el perfil no hay movimiento ni choice de la alternativa; el tick es el `startTick` de la nueva intención |
| `FallbackPolicy.level(service, budgetAvailable, lowConfidenceStreak, sessionValid, ControlLevel validIntentLevel, localTacticAvailable)`: el `boolean intentValid` pasa a ser el nivel de la intención vigente (`BASE` = ninguna); `internalError()` cubre "o error" (M12 lo retiró por no tener uso: el engine aplica `INTERNAL_ERROR` en `IntentControl.fail`, M10) | Un booleano no distingue `JEV`, `ALTERNATIVE` y una intención `LOCAL` vigente, y `Optional<ControlLevel>` violaría C4. No había entrada para "error" |
| `FallbackPolicy`: `PAUSED` y `CHECKING` → `SERVICE_PAUSED`; `UNCONFIGURED`, `INVALID_CONFIGURATION`, `INVALID_CREDENTIALS`, `INVALID_MODEL` → `SERVICE_BLOCKED`; una intención `JEV` o `ALTERNATIVE` vigente conserva su nivel aunque el servicio falle; una intención `LOCAL` vigente con el servicio sano sigue en `LOCAL` sin causa solo si hay táctica local | Orden de M02.7 y diagrama de 01 §6 ("intención vencida sin reemplazo" → `BASE`; `LOCAL → JEV` solo con respuesta aceptada) |
| `FallbackPolicy`: con el servicio sano, una intención `LOCAL` sin táctica local disponible, una intención vencida y una sesión que nunca tuvo intención bajan a `BASE` con `EXPIRED` | 01 §6 pide `LOCAL → BASE` "sin táctica local válida" y `JEV → BASE` "intención vencida sin reemplazo". En los tres casos no queda ninguna intención válida y nada la sustituye, que es lo que describe `EXPIRED`; `NO_PROGRESS` se reserva para el movimiento atascado (M10). No se añade una causa nueva a `FallbackReason` porque 01 no la prevé; para una sesión recién creada `BASE → BASE` no es un cambio de nivel y el engine no la registra como bajada |
| `LocalPlanner.choose(profile, active, eligible)` recibe los contextos activos | 01 §7 (normativo) los incluye; la táctica directa del contexto gobernante gana, como en `PredictionPlanner` |
| `Eligibility.direct(profile, active, eligible)` concreta el `direct(...)` sin firma de M02.7; `governingContext` es público | Lo reutilizan `LocalPlanner` y las pruebas |
| Empates por orden de declaración, no por ID alfabético como `PredictionPlanner` | Lo pide M02.3 y M02.7; el caso equivalente de `PredictionPlannerTest` lo refleja |
| `ContingencyPolicy.fire(intent, probe, eligibleNow, profile, tick)` además de `triggered` | "Cada contingencia se usa una vez" necesita quitarla de la intención; `triggered` es sin estado. `IF_OWN_HEALTH_CRITICAL` tiene precedencia. Se evalúa el estado actual de la sonda, no la transición: el engine solo pregunta por una contingencia cuando aún no se cumple (M09) |
| `IntentPolicy`: una táctica solo es aplicable con probabilidad > 0, elegible y declarada; sin táctica aplicable se rechaza con `INVALID_RESPONSE`; una contingencia solo elige tácticas declaradas con probabilidad > 0; el cambio exige ganancia estrictamente positiva además del margen, para que `switchMargin = 0` no cambie de táctica por un empate resuelto alfabéticamente; un `Rejected` no invalida la intención vigente; margen y permanencia mínima solo frente a una intención `JEV` (una `LOCAL` o `ALTERNATIVE` se sustituye al llegar la respuesta); la vigente vencida o no elegible se sustituye siempre; `Renewed` conserva `startTick` y `skillConsumed`; alternativas solo entre tácticas aplicables; contingencias con la mejor táctica declarada con probabilidad > 0, en orden salud-melee, hasta `contingenciesPerMob`, y descartadas si coinciden con la táctica elegida; respuesta sin táctica aplicable → `INVALID_RESPONSE` | Concreción mínima de M02.7 para que el resultado sea determinista y probado |
| Vigencia en nanos = `maximumIntentTicks × IntentPolicy.NANOS_PER_TICK` (50 ms nominales) | `expiresAtNanos` es un plazo real (C6) y el perfil lo expresa en ticks |
| Prueba de suma de `Answer`: se aceptan 0,98 y 1,02 y se rechazan 0,97, 0,95 y 1,03, no "0,97 aceptada" | M02.5 fija la tolerancia en 0,02 dos veces (diseño y riesgos); 0,97 difiere en 0,03. Se añade `ROUNDING_EPSILON = 1e-9` para el redondeo de coma flotante, también en el margen de cambio |
| Prefijos de `admin`: `org.bukkit.` y `net.kyori.adventure.` en lugar de `org.bukkit.command.` | D6 de 00 (normativo) permite permisos, búsqueda de entidades y Adventure |
| `java.net.http.` prohibido fuera de `adapter.jev` aunque `java.` esté permitido | A2 y D4 |
| Un paquete sin regla ni entrada de heredados falla la prueba; la declaración `package` de cada archivo (también heredados) debe coincidir con su ruta (D9) | Obliga a declarar la regla de cada paquete nuevo; sin la comprobación de ruta, un archivo mal ubicado evadiría las reglas de su paquete real |
| Los nombres completamente calificados se buscan para `org.bukkit`, `io.papermc`, `com.destroystokyo.paper`, `io.lumine.mythic`, `java.net.http`, `com.google` (Gson y Guava), `javax`, `org.slf4j`, `net.kyori` y `akyrion.thepredict` | Una detección genérica de cualquier prefijo confundiría cadenas de llamadas (`a.b().c`) con nombres de paquete; la lista cubre las bibliotecas presentes en el classpath del servidor |
| La raíz `akyrion.thepredict` está en la lista de heredados (M12) | Lo pide M02.1; ver "Pendiente" |
| Invariantes añadidas: `ContextRule.allowedTactics` no vacío y condiciones no en blanco; ticks de `DecisionTiming` > 0; `retreatDistanceBlocks` > 0; `MobProbe.targetDistance` exige `targetId`; `MovementProgress.pathReachesTarget` exige `hasPath`; `TacticalProfile` rechaza tácticas reservadas, IDs de contexto repetidos y claves que no coinciden con su valor; `CombatFacts` rechaza sesiones repetidas; `DecisionRequest` exige `facts.encounter == encounter`, `sequence >= 0` y un perfil por mob preguntado; `TacticalIntent` admite como máximo 3 alternativas distintas y ninguna contingencia `NOW`; `Answer` no vacía | Consecuencias directas de 01 (reservas de §2, "hasta 3 alternativas", `profiles` de §5) o valores sin sentido que ninguna regla podría usar |
| Ayudantes añadidos: `ExecutionOutcome.isFinal()`, `CombatFacts.mob(MobKey)`, `TacticalProfile.tactic(TacticId)` y constantes (`ParticipantAlias.PREFIX`, `QuestionKey.MAX_INDEX`, `Tactic.MIN_PRIORITY`/`MAX_PRIORITY`, `QuestionSpec.MIN_OPTIONS`/`MAX_OPTIONS`, `Answer.SUM_TOLERANCE`, `DecisionTiming.MAX_CONTINGENCIES_PER_MOB`, `TacticalIntent.MAX_ALTERNATIVES`, `FallbackPolicy.LOW_CONFIDENCE_STREAK_FOR_LOCAL`) | Expresan reglas de 01 y M02 en un solo lugar; no son tipos nuevos |
| Las reglas puras son clases `final` con métodos estáticos y constructor privado | Sin estado (01 §7); no requieren instancia ni dobles |
| `PredictValue` conserva `description()` en inglés y `parse(List<String>)` (ahora con `Locale.ROOT`) | W5: el camino de OpenRouter los usa. 01 §9 deja los textos en inglés para Jev en `adapter.jev`; M08 escribe los suyos y M12 retira estos |
| `TacticalProfile` copia sus mapas a `LinkedHashMap` no modificable en lugar de `Map.copyOf` | M02.3: el orden de declaración desempata prioridades |

### Correcciones tras la auditoría (2026-09-29)

| Hallazgo | Corrección | Prueba |
|---|---|---|
| `IntentPolicy` adoptaba una táctica elegible con probabilidad 0 | Aplicable solo con probabilidad > 0; si no queda ninguna, `INVALID_RESPONSE` | `IntentPolicyTest.eligibleTacticWithZeroProbabilityIsNotApplicable` |
| Una contingencia podía elegir una táctica con probabilidad 0 | Mismo filtro en las contingencias | `IntentPolicyTest.contingencyNeverPlansAZeroProbabilityTactic` |
| `FallbackPolicy` mantenía `LOCAL` sin causa aunque no hubiera táctica local | Baja a `BASE` con `EXPIRED` | `FallbackPolicyTest.localIntentWithoutALocalTacticGoesToBaseAsExpired` |
| Con `switchMargin = 0` un empate cambiaba de táctica por orden alfabético | El cambio exige ganancia > 0 | `IntentPolicyTest.zeroMarginDoesNotSwitchOnAProbabilityTie` |
| `EXPIRED` para una sesión que nunca tuvo intención | Documentado (ver desviaciones); sin causa nueva | — |
| Casilla de M02.7 sobre `FallbackReason` inexacta para `JEV → ALTERNATIVE` | Redacción precisada | — |
| `ArchitectureRulesTest` no comprobaba `package` frente a la ruta | Nueva comprobación D9 | `packageDeclarationMustMatchThePath` |
| `ArchitectureRulesTest` no buscaba Guava, `javax` ni SLF4J calificados | Prefijos añadidos | `qualifiedGuavaJavaxAndLoggingReferencesAreDetected` |
| Orden de importaciones en `DecisionLifecycleSuiteTest` | Restaurado el orden alfabético | — |

### Pendiente

- M07 debe usar `CombatFacts.MAX_MOBS`, `MAX_PARTICIPANTS` y `MAX_EVENTS` (última casilla de M02.4).
- M12, al vaciar la lista de heredados, debe dar a la raíz de composición una regla propia (D8: solo `ThePredictPlugin` conecta y puede importar todo), porque `ThePredictPlugin` seguirá importando los adapters. **Hecho en M12** (ver su [estado de implementación](M12-Diagnostico-instalacion-retiro-OpenRouter.md#estado-de-implementación)).
- `CoordinationPolicy` (hecho en M10: `limitRetreats` con `Candidate`, y `FallbackPolicy` gana la sobrecarga con la racha configurada) y `ThreatPolicy` (M11).
- M08 confirma la tolerancia de 0,02 con respuestas reales de Jev.
