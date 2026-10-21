# M05. Propiedad de la IA de movimiento

> **Estado:** Local listo — servidor pendiente (2026-09-29; ver "Estado de implementación") · **Entrega:** B · **Depende de:** M01, M03, M04 · **Desbloquea:** M06, M10 · **Diseño:** [plan §3](../Plan-Jev-y-combate-tactico.md#propiedad-de-la-ia-de-movimiento), [plan §5](../Plan-Jev-y-combate-tactico.md#5-control-de-movimiento-y-habilidades) y [plan §2](../Plan-Jev-y-combate-tactico.md#evidencia-del-binario-paper-262-y-de-minecraft-vanilla)

## Objetivo

En un mob vinculado, ThePredict sustituye la gestión de metas de MythicMobs por un conjunto de metas generado desde el perfil. No reescribe comportamientos: instala metas de MythicMobs y vanilla con la misma API que usa MythicMobs para `AIGoalSelectors` y añade solo tres metas de Paper que no existen de forma nativa (`hold`, `flank` y `owner`). La IA base queda debajo, con menor prioridad, y actúa sola cuando ThePredict no controla: es el nivel 4 del fallback.

Al terminar M05, un mob vinculado se mueve según intenciones fijadas con el comando de depuración de M01, sin Jev ni costo (entrega B).

## Alcance

### Incluye

- Generación determinista de la lista de metas desde `TacticalProfile`.
- Instalación, reinstalación y liberación de metas mediante el puerto `MobControl`.
- Las metas de Paper `HoldGoal`, `FlankGoal` y `OwnerMarkerGoal`.
- Observación del movimiento (`MovementProgress`) para que M10 detecte falta de progreso.
- Resolución de la IA base y exclusión de especies con `Brain`.

### No incluye

- Decidir tácticas o aplicar la cadena de fallback (M10).
- Las condiciones `predictor*` que leen las metas (M04).
- Sesiones, encuentros y listeners de ciclo de vida (M06); M05 expone las operaciones que M06 invoca.
- Lanzar skills y comprobar el ángulo de golpe de `FLANK` (M10).
- La consolidación del comando de depuración como comando administrativo (M12).

## Base existente

| Archivo | Acción |
|---|---|
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/MythicProfileBinder.java` | Se reutiliza para saber qué tipos de mob están vinculados. M03 lo mueve a `adapter.server.binding`. |
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/BoundProfile.java` | Se reutiliza; M03 añade las condiciones de contexto vinculadas. M05 no la modifica. |
| `src/main/java/akyrion/thepredict/ThePredictPlugin.java` | Solo cambia en M06, que traslada los listeners. M05 no la modifica. |
| — | No existe código de metas. Todo lo de este módulo es nuevo en `adapter/server/goals/` y `adapter/server/context/ShieldGeometry.java`. |

## Subpuntos

### M05.1 Generador del conjunto de metas

- **Propósito:** traducir un perfil y su IA base en una lista ordenada de líneas de MythicMobs y huecos para metas de Paper, sin tocar el servidor, para poder probarla con texto exacto.
- **Diseño:**
  - `final class GoalLineGenerator` con un único método `GoalPlan generate(TacticalProfile profile, BaseAiSource baseAi, double followRangeBlocks, long catalogRevision)`. No tiene estado ni lee relojes.
  - Tipos locales del adapter, en `adapter.server.goals`: `record GoalPlan(List<String> mythicLines, List<PaperGoalSlot> paperGoals, long revision)`, `record PaperGoalSlot(PaperGoalKind kind, int priority)`, `enum PaperGoalKind { FLANK, HOLD, OWNER }` y `record BaseAiSource(List<String> lines, BaseAiOrigin origin)` con `enum BaseAiOrigin { PROFILE, MOB_CONFIG, GENERIC }`.
  - Solo se generan metas para los movimientos que usa alguna táctica del perfil:

    | Prioridad | Línea o meta | Se incluye si |
    |---|---|---|
    | 0 | `clear` | Siempre |
    | 1 | `float` | Siempre |
    | 2 | `goToSpawnLocation{maxrange=<arena>;speed=<approach>}` | `arenaRadiusBlocks` presente |
    | 3 | `fleeConditional{distance=<retreatDistance>;speed=<retreatSpeed>;mc=[ - predictormove{m=RETREAT} true ]}` | Alguna táctica usa `RETREAT` |
    | 4 | `fleeConditional{distance=<bandInner>;speed=<approach>;mc=[ - predictormove{m=KEEP_DISTANCE} true ]}` | Alguna táctica usa `KEEP_DISTANCE` |
    | 5 | `moveWithinConditional{range=<bandOuter>;speed=<approach>;mc=[ - predictormove{m=KEEP_DISTANCE} true ]}` | Alguna táctica usa `KEEP_DISTANCE` |
    | 6 | `moveTowardsTargetConditional{speed=<approach>;minrange=<meleeReach>;maxrange=<followRange>;mc=[ - predictormove{m=APPROACH} true ]}` | Alguna táctica usa `APPROACH` |
    | 7 | Paper `FLANK` | Alguna táctica usa `FLANK` |
    | 8 | Paper `HOLD` | Alguna táctica tiene movimiento distinto de `NONE` |
    | 10+ | IA base, desplazada +10, en su orden original | Siempre |
    | última + 1 | `lookAtTarget` | La IA base no contiene `lookAtTarget` |
    | última + 2 | Paper `OWNER` | Siempre |

  - `HOLD` cubre también la banda de `KEEP_DISTANCE`: cuando ninguna meta táctica se mueve, `hold` ocupa `MOVE` y la IA base no arranca (plan §3).
  - Los números se escriben con `Locale.ROOT`, sin notación científica y con dos decimales como máximo.
  - Los nombres de condición salen de las constantes de M04; el generador no concatena texto de usuario, solo números validados por M03.
  - `revision` es `catalogRevision`; permite saber si un conjunto instalado quedó obsoleto tras una recarga.
- **Archivos:** crear `adapter/server/goals/GoalLineGenerator.java`, `GoalPlan.java`, `PaperGoalSlot.java`, `PaperGoalKind.java`, `BaseAiSource.java` y `BaseAiOrigin.java`; prueba `src/test/java/akyrion/thepredict/adapter/server/goals/GoalLineGeneratorTest.java`.
- **Reglas:** A1, C2, C3, C5, C12, I8, P4.
- **Pruebas:** comparación exacta de líneas para cada combinación de movimientos; perfil sin arena; perfil solo con `HOLD`; perfil con `FLANK`; IA base con y sin `lookAtTarget`; formato numérico con `Locale` distinto al del sistema.
- **Aceptación:**
  - [x] La misma entrada produce siempre la misma lista.
  - [x] Un movimiento no usado no genera metas.
  - [x] Todas las prioridades de la IA base quedan por encima de 9 y conservan su orden relativo.
  - [ ] Las líneas generadas son las del ejemplo del plan §3 para el perfil de ejemplo. Estructura, orden, prioridades, distancias y condiciones coinciden; las velocidades y `minrange` siguen la tabla de M05.1 y difieren del boceto del plan (ver "Estado de implementación", desviación 9).

### M05.2 Instalador `GoalOwnership`

- **Propósito:** aplicar un `GoalPlan` a una entidad viva mediante las APIs nativas e implementar el puerto `MobControl`.
- **Diseño:**
  - `public final class GoalOwnership implements MobControl`. El constructor recibe `Plugin`, `VolatileAIHandler` (de `MythicBukkit.inst().getVolatileCodeHandler().getAIHandler()`), `MobGoals` (de `Bukkit.getMobGoals()`), `IntentBoard`, `ServerClock`, `GoalLineGenerator` y `BaseAiResolver`. No guarda mapas propios: el estado vive en `engine` (A3) y en las propias metas del mob.
  - `InstallResult ensureInstalled(MobKey key, TacticalProfile profile)`:
    1. Resuelve el `ActiveMob` con `MythicBukkit.inst().getMobManager().getActiveMob(key.entityId())`. Si no existe o la entidad no es `org.bukkit.entity.Mob`, devuelve `Deferred("entity unavailable")`.
    2. Si la especie usa `Brain`, devuelve `Unsupported("brain species")` (M05.6).
    3. Si `mobGoals.hasGoal(mob, GoalKeys.OWNER)` y el marcador instalado tiene la misma `revision`, no hace nada y devuelve `Installed(goalCount)`. Leer el marcador con `mobGoals.getGoal(mob, GoalKeys.OWNER)` y comprobar que devuelve la instancia de ThePredict: **M01 confirma**. Alternativa: `engine` guarda la revisión instalada en `MobSession` y solo pide reinstalar si cambió.
    4. Genera el plan y llama a `addPathfinderGoals(livingEntity, plan.mythicLines())`.
    5. Añade cada `PaperGoalSlot` con `mobGoals.addGoal(mob, slot.priority(), goal)`.
    6. Devuelve `Installed(plan.mythicLines().size() - 1 + plan.paperGoals().size())`, sin contar `clear`.
  - Que `clear` elimine también las metas de Paper instaladas antes: **M01 confirma**. Alternativa: antes del paso 4, `mobGoals.removeGoal(mob, key)` para las tres claves de ThePredict.
  - Toda excepción `RuntimeException` o `LinkageError` se captura en el método, se registra con límite de frecuencia y produce `Unsupported("install failed: " + clase)` (corrección tras la segunda revisión; antes `Deferred`). La sesión sigue en nivel 4. `Deferred` queda solo para la causa transitoria del paso 1.
  - `GoalKeys` define `HOLD`, `FLANK` y `OWNER` como `GoalKey.of(Mob.class, new NamespacedKey(plugin, "hold" | "flank" | "owner"))` (I10).
  - Todo el instalador se ejecuta en el hilo del servidor (T1).
- **Archivos:** crear `adapter/server/goals/GoalOwnership.java`, `GoalKeys.java` e `InstallDecision.java` (función pura: presencia del marcador, revisión instalada y esperada → `NO_OP | INSTALL`); prueba `InstallDecisionTest.java`.
- **Reglas:** A1, A3, A6, C7, C8, T1, I10.
- **Pruebas:** `InstallDecisionTest` cubre marcador ausente, revisión distinta y revisión igual. La instalación real se comprueba en servidor (M05.7).
- **Aceptación:**
  - [x] Instalar dos veces seguidas con la misma revisión no cambia las metas del mob — parte local: con dobles de `MobGoals` y `VolatileAIHandler` (`GoalOwnershipTest`).
  - [ ] Instalar dos veces seguidas con la misma revisión no cambia las metas del mob — parte de servidor (DT-15).
  - [x] Una revisión nueva reinstala el conjunto completo sin duplicar metas de Paper — parte local: con dobles.
  - [ ] Una revisión nueva reinstala el conjunto completo sin duplicar metas de Paper — parte de servidor (DT-15).
  - [x] Un error de instalación deja al mob con su IA de MythicMobs y una causa registrada — parte local: quita las metas de Paper, reaplica `AIGoalSelectors`, devuelve `Unsupported`, deja un marcador de rechazo y registra con límite.
  - [ ] Un error de instalación deja al mob con su IA de MythicMobs y una causa registrada — parte de servidor (DT-04).

### M05.3 Metas de Paper

- **Propósito:** cubrir las dos capacidades que no existen de forma nativa y marcar la propiedad del conjunto.
- **Diseño:**
  - Recordatorio verificado: `PaperCustomGoal` se elige cada 2 ticks y su `tick()` corre cada 2 ticks. Estas metas no son más rápidas que las nativas; solo aportan capacidades ausentes.
  - **`HoldGoal implements Goal<Mob>`:**
    - `getTypes()`: `EnumSet.of(GoalType.MOVE)`. Deja `LOOK` libre para `lookAtTarget`; por eso no se usa `doNothing`, que declara `MOVE_LOOK`.
    - `shouldActivate()` y `shouldStayActive()`: la `IntentView` de la sesión existe, su generación coincide y `controls(clock.nanoTime())` es verdadero.
    - `start()`: `mob.getPathfinder().stopPathfinding()`. `tick()` no hace nada.
    - Las metas tácticas tienen mayor prioridad y la interrumpen cuando su condición se cumple; `hold` ocupa `MOVE` en los demás casos mientras ThePredict controla.
  - **`FlankGoal implements Goal<Mob>`:**
    - `getTypes()`: `EnumSet.of(GoalType.MOVE)`.
    - Se activa si la vista controla con movimiento `FLANK` y `mob.getTarget()` es un `HumanEntity` con escudo activo.
    - Lee el ángulo de bloqueo del ítem activo del objetivo: componente `BLOCKS_ATTACKS`, máximo de `damageReductions().horizontalBlockingAngle()`, 90° si no hay reducciones.
    - Calcula dos destinos, uno a cada lado del objetivo, a `meleeReachBlocks` de distancia y a `ángulo de bloqueo + 20°` de su mirada, con la geometría de `ShieldGeometry`.
    - Elige el lado cuyo `mob.getPathfinder().findPath(destino)` no es nulo, alcanza el punto final y tiene menos puntos; luego `moveTo(path, approachSpeed)`.
    - Recalcula solo si el objetivo se movió más de 1 bloque o giró más de 30°, y como máximo cada 10 ticks.
    - `shouldStayActive()` es falso sin ruta en ambos lados o si la vista deja de pedir `FLANK`. La falta de progreso en dos decisiones la decide M10 con `MovementProgress`.
  - **`OwnerMarkerGoal implements Goal<Mob>`:** `shouldActivate()` devuelve siempre `false`. Guarda `revision` y `generation` para diagnóstico. `getTypes()`: `EnumSet.of(GoalType.LOOK)`, que no afecta porque nunca se activa. Si un conjunto vacío es válido: **M01 confirma**.
  - **`ShieldGeometry`** (en `adapter.server.context`, reutilizada por M07 y M10): con yaw de Minecraft, la mirada horizontal es `x = -sin(yaw)`, `z = cos(yaw)`. Un punto lateral se obtiene girando ese vector ±(ángulo + margen). `isOutsideBlockingAngle(target, attacker, angleDegrees)` compara el ángulo entre la mirada del objetivo y la dirección objetivo → atacante.
- **Archivos:** crear `adapter/server/goals/HoldGoal.java`, `FlankGoal.java`, `OwnerMarkerGoal.java` y `adapter/server/context/ShieldGeometry.java`; pruebas `ShieldGeometryTest.java` y `HoldGoalActivationTest.java` (con `IntentBoard` y `ServerClock` de prueba, sin entidades).
- **Reglas:** A1, A6, C5, C12, T1, T3.
- **Pruebas:** ángulos en los cuatro cuadrantes y en la frontera de 90°; activación de `HoldGoal` con vista vencida, de otra generación o sin vista; comportamiento en servidor en M05.7.
- **Aceptación:**
  - [x] `HoldGoal` nunca declara `LOOK`.
  - [x] `FlankGoal` no llama a `findPath` más de una vez cada 10 ticks por mob (una ronda de planificación, que pide una ruta por lado, como máximo cada 10 ticks; `FlankGoalTest`).
  - [x] Un ataque desde un destino de flanqueo queda fuera del ángulo de bloqueo en `ShieldGeometryTest`.

### M05.4 Ciclo de vida de la instalación

- **Propósito:** que el conjunto esté instalado siempre que la sesión lo necesite, sin picos de tick.
- **Diseño:**
  - **Disparadores** (los listeners viven en M06 y marcan la sesión con instalación pendiente):
    - `MythicMobSpawnEvent`: la instalación ocurre un tick después, cuando MythicMobs ya aplicó sus propias metas.
    - `EntitiesLoadEvent`: un tick después, para cada entidad con `ActiveMob` de un tipo vinculado. MythicMobs reinstala sus metas al cargar sin lanzar `MythicMobSpawnEvent`.
    - Recarga (`MythicReloadedEvent`): M03 incrementa `catalogRevision`; cada sesión se reinstala en su siguiente decisión porque su revisión quedó obsoleta.
    - Autocomprobación: antes de cada decisión de un encuentro, M09 llama a `ensureInstalled` para sus miembros. Si una skill o una mecánica reinstaló otras metas, el marcador falta y se reinstala.
  - **Límite por tick:** `engine` llama a `ensureInstalled` como máximo `decision.maxInstallsPerTick` veces por tick (valor inicial 10). Las sesiones pendientes esperan con la IA de MythicMobs. El límite vive en `engine` para respetar A3.
  - **Desvinculación** (Predictor apagado tras recarga, perfil inválido o sesión cerrada): `engine` retira la vista de `IntentBoard`, con lo que las condiciones responden falso de inmediato. En el tick siguiente llama a `release(key)`: `GoalOwnership` quita las tres metas de Paper y ejecuta `activeMob.getType().reapplyAIGoalSelectors(activeMob)`. Si el mob no tenía `AIGoalSelectors`, conserva la IA base del conjunto generado (plan §3).
  - **Apagado del plugin** (T7): tras vaciar `IntentBoard`, se liberan como máximo 200 sesiones. Si hay más, las restantes quedan con las metas generadas: sus condiciones y `HoldGoal` ya son falsas y la IA base actúa. Así el apagado no bloquea el servidor.
  - **Descarga de chunk:** las metas desaparecen con la entidad; la sesión pasa a `SUSPENDED` (M06) y al volver a cargar se reinstala.
- **Archivos:** modificar `GoalOwnership.java` (método `release`); la marca de instalación pendiente y el límite por tick están en `engine.MobSession` y `engine.Coordinator` (M06 y M09).
- **Reglas:** A3, A5, A6, T1, T7, W5.
- **Pruebas:** en `engine`, con dobles de `MobControl` y reloj controlable: 50 sesiones pendientes se instalan en 5 ticks con límite 10; una desvinculación retira la vista antes de llamar a `release`.
- **Aceptación:**
  - [x] Ningún tick instala más metas que el límite configurado — paso 5 del coordinador de M09 (`CoordinatorTest.fiftyPendingInstallationsFinishInFiveTicksWithAtMostTenPerTick`, `noStepGoesBeyondItsLimitPerTick`).
  - [ ] Tras cargar un chunk con mobs vinculados, todos quedan instalados en un número de ticks acotado.
  - [ ] Desvincular un mob devuelve su IA de MythicMobs sin reiniciar el servidor.

### M05.5 Observación del movimiento

- **Propósito:** dar a M10 los datos para detectar falta de progreso sin implementar otra detección de atascos, que ya hace `PathNavigation`.
- **Diseño:**
  - `MovementProgress progress(MobKey key)` en `GoalOwnership`:
    - `movementGoalRunning`: `mobGoals.getRunningGoals(mob, GoalType.MOVE)` contiene alguna meta distinta de `GoalKeys.HOLD`.
    - `hasPath`: `mob.getPathfinder().hasPath()`.
    - `pathReachesTarget`: `getCurrentPath()` no nulo y `canReachFinalPoint()`.
    - `targetDistanceBlocks`: distancia a `mob.getTarget()`; sin objetivo, `OptionalDouble.empty()`.
  - Coste: una lista de unas 15 metas y dos lecturas del navegador por mob y decisión.
  - Regla de falta de progreso, aplicada por M10: dos decisiones seguidas sin meta de movimiento en curso cuando el movimiento lo exige, o sin cambio de distancia de al menos 0,5 bloques en la dirección esperada (`APPROACH` acercarse; `RETREAT` alejarse; `KEEP_DISTANCE` volver a la banda). El resultado es `FallbackReason.NO_PROGRESS`.
- **Archivos:** modificar `GoalOwnership.java`; la regla vive en `engine.ProgressWatch` (M10, desviación 18 de M10: necesita la racha y la distancia anterior que guarda `MobSession`).
- **Reglas:** A1, A7, C12, T1.
- **Pruebas:** en servidor, un objetivo sobre pilares sin acceso produce `hasPath` falso o `pathReachesTarget` falso.
- **Aceptación:**
  - [x] `progress` no calcula rutas nuevas (`MovementReadingTest` comprueba que no llama a `findPath` ni a `moveTo`).
  - [x] Un mob que no avanza durante dos decisiones produce `NO_PROGRESS` en M10: local, `ReactiveLayerTest.twoEvaluationsWithoutProgressGiveNoProgressAndTheNextAlternative` y `progressFollowsTheExpectedDirectionOfEachMovement` (la regla vive en `engine.ProgressWatch`, desviación de M10); con un mob atascado real, pendiente de servidor (guion 12.6).

### M05.6 IA base y especies con `Brain`

- **Propósito:** definir qué hace el mob cuando ThePredict no controla y excluir las especies donde las metas no gobiernan el movimiento.
- **Diseño:**
  - `final class BaseAiResolver` con `BaseAiSource resolve(TacticalProfile profile, MythicMob type)`:
    1. `profile.baseAi()` no vacío → `PROFILE`.
    2. `type.getAIGoalSelectors()` no vacío → `MOB_CONFIG`, sin líneas `clear` (con o sin prioridad, sin distinguir mayúsculas).
    3. En otro caso → `GENERIC`: `meleeAttack` y `randomStroll`.
  - Cada línea se desplaza +10 leyendo su prioridad inicial. Una línea sin prioridad recibe su posición en la lista (desde 1), como hace MythicMobs según la evidencia estática de DT-16 (corrección tras la auditoría); M01 confirma en servidor. El generador siempre escribe prioridades explícitas.
  - Especies con `Brain` (plan §2): `ALLAY`, `ARMADILLO`, `AXOLOTL`, `CAMEL`, `FROG`, `TADPOLE`, `GOAT`, `COPPER_GOLEM`, `HAPPY_GHAST`, `NAUTILUS`, `ZOMBIE_NAUTILUS`, `SNIFFER`, `ZOGLIN`, `BREEZE`, `CREAKING`, `HOGLIN`, `PIGLIN`, `PIGLIN_BRUTE`, `WARDEN` y `VILLAGER`, como `Set<EntityType>` en `BrainSpecies`. Para ellas `ensureInstalled` devuelve `Unsupported` y M03 rechaza tácticas con movimiento distinto de `NONE`.
- **Archivos:** crear `adapter/server/goals/BaseAiResolver.java` y `BrainSpecies.java`; pruebas `BaseAiResolverTest.java` y `BrainSpeciesTest.java`.
- **Reglas:** A1, A6, C13.
- **Pruebas:** desplazamiento de prioridades y eliminación de `clear`; `BrainSpeciesTest` resuelve cada nombre con `EntityType.valueOf` contra Paper API 26.2 y falla si alguno no existe.
- **Aceptación:**
  - [ ] Un mob sin `AIGoalSelectors` ni `BaseAI` persigue y ataca como un zombie de MythicMobs cuando ThePredict no controla.
  - [x] Ninguna especie con `Brain` recibe metas de ThePredict — parte local: `Unsupported` sin tocar las metas (`GoalOwnershipTest`, `BrainSpeciesTest`).
  - [ ] Ninguna especie con `Brain` recibe metas de ThePredict — parte de servidor (DT-19).

### M05.7 Pruebas en servidor

- **Propósito:** demostrar el comportamiento en juego, que las pruebas unitarias no pueden demostrar (P4).
- **Diseño:** guion ejecutado con el comando de depuración de M01 (`intent` para fijar una táctica local, `goals` para listar metas en curso con `getRunningGoals`):

  | Escenario | Resultado esperado |
  |---|---|
  | `APPROACH` | El mob se acerca y se detiene a `meleeReachBlocks` |
  | `KEEP_DISTANCE` con el jugador acercándose y alejándose | El mob mantiene la banda sin alternar avance y retroceso |
  | `RETREAT` | El mob se aleja por terreno transitable y respeta la arena |
  | `HOLD` | El mob no se desplaza y sigue mirando al objetivo |
  | `FLANK` contra un jugador con escudo | El mob termina fuera del ángulo de bloqueo |
  | Intención liberada | La IA base retoma el control en el siguiente ciclo de metas |
  | Descarga y carga del chunk | El conjunto se reinstala y el marcador está presente |
  | `/mm reload` | El conjunto se reinstala con la revisión nueva |
  | 30 mobs vinculados cargando a la vez | Instalación en varios ticks sin picos atribuibles en el perfilador |

- **Archivos:** el guion se registra en el documento de M01 y su evidencia en M13.
- **Reglas:** A8, P4, W3.
- **Pruebas:** las del cuadro, con tiempo de tick medido con el perfilador incluido en Paper.
- **Aceptación:**
  - [ ] Todos los escenarios tienen evidencia registrada.
  - [ ] Ningún escenario deja un mob congelado.

## Riesgos y decisiones abiertas

- **`clear` y metas de Paper:** si `clear` no las elimina, se quitan por clave antes de reinstalar (M05.2).
- **Skills que cambian la IA en tiempo de ejecución:** la autocomprobación reinstala en la siguiente decisión; el binder de M03 avisará si detecta mecánicas conocidas que lo hagan.
- **`AsyncPathfinding`:** si M01 detecta incompatibilidad con las metas condicionales o con `FlankGoal`, se desactiva para mobs vinculados.
- **Prioridades iguales entre metas de MythicMobs y de Paper:** el generador nunca repite una prioridad; si M01 muestra otro orden efectivo, se ajusta la tabla.
- **Incorporado en 01 (revisión 2):** añadir `meleeReachBlocks` a `MovementSettings` (valor inicial 2,5). Lo usan `minrange` de `APPROACH`, los destinos de `FlankGoal` y la banda `MELEE` de M07.
- **Incorporado en 01 (revisión 2):** que `MovementProgress.targetDistanceBlocks` sea `OptionalDouble` en lugar del valor centinela `-1.0`.
- **Incorporado en 01 (revisión 2):** que `MobSession` guarde la revisión instalada y la marca de instalación pendiente si M01 no confirma la lectura del marcador.
- **Incorporado en M03 (revisión 2):** la clave `decision.maxInstallsPerTick` (M03).

## Terminado

- [ ] Subpuntos M05.1–M05.7 aceptados.
- [x] Pruebas unitarias del generador, de la decisión de instalación, de `ShieldGeometry`, de `BaseAiResolver` y de `BrainSpecies` en verde.
- [ ] Escenarios de servidor de M05.7 con evidencia.
- [x] `ArchitectureRulesTest` verde: `adapter.server.goals` no importa `adapter.jev`.
- [ ] Documento actualizado con el estado `Verificado en servidor` y las decisiones de M01 aplicadas.

## Estado de implementación

> **Local listo — servidor pendiente (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` (también con `--rerun-tasks`) pasa sin avisos de compilación ni de Javadoc: **569 pruebas, 0 fallos** (474 de partida + 84 nuevas + 6 de las correcciones tras la auditoría + 5 de la segunda revisión). Ninguna prueba hace llamadas de red ni lee `.env` (W6). No se arrancó ningún servidor: las aceptaciones que exigen juego siguen sin marcar y no hay evidencia de servidor inventada. 00 y 01 no cambian. El registro de decisiones de M01 recibe evidencia estática nueva (DT-01, DT-04, DT-08, DT-15 y DT-16), todas siguen `Abierta`; el guion de M01 recibe la sección 9 con los escenarios de M05.7 (el cierre pasa a ser la sección 10).

### Archivos

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…`) |
|---|---|
| M05.1 | Creados en `adapter/server/goals/`: `GoalLineGenerator`, `GoalPlan`, `PaperGoalSlot`, `PaperGoalKind`, `BaseAiSource`, `BaseAiOrigin` y `BaseAiLines` (paquete privado: lectura de prioridades y de `clear` como MythicMobs) |
| M05.2 | Reescrito `adapter/server/goals/GoalOwnership` (implementa el puerto); creados `GoalSetInstaller` (escritura nativa de metas), `InstallDecision` (enum con el `Marker` sellado), `LiveMob` y `FailureLog` (paquete privado); creado `engine/MobControl` (puerto de 00 §4). `GoalKeys` sin cambios |
| M05.3 | Completados `HoldGoal` (`ServerClock`), `FlankGoal` (escudo, umbrales y ritmo) y `OwnerMarkerGoal` (revisión, generación y número de metas); creados `FlankPaths` (paquete privado) y `adapter/server/context/ShieldGeometry` |
| M05.4 | `GoalOwnership.release` y la restauración tras un fallo. El límite por tick y la marca de instalación pendiente viven en `engine` (M06, M09) |
| M05.5 | `GoalOwnership.progress`; creado `MovementReading` (paquete privado); `GoalSetInstaller.movementGoalRunning` |
| M05.6 | Creado `BaseAiResolver`; ampliado `adapter/server/binding/BrainSpecies` (lista única, pendiente de M03) con `SPECIES` (`Set<EntityType>`) y `contains(EntityType)` |
| Depuración | Creado `DebugProfileGoals` (raíz); `DebugCommandHandler` (`install <mob> profile`, `unbind` y línea de progreso en `goals`); `admin/DebugCommandParser` y `DebugRequest.Unbind`; `DebugGoalPresets` (prioridades desde el generador y constantes de `flank` de depuración); `GoalInspector.summaries` |
| Raíz | `ThePredictPlugin.enableVerificationTools` construye `GoalSetInstaller`, `GoalOwnership` y `DebugProfileGoals` |
| Documentos | Este documento; `README.md` (fila de M05); `M01-Registro-de-decisiones.md` (evidencia estática); `M01-Guion-de-servidor.md` (sección 9) |

### Pruebas (569; 84 nuevas, 6 de las correcciones tras la auditoría y 5 de la segunda revisión)

- `adapter.server.goals`: `GoalLineGeneratorTest` (13, nueva: perfil de ejemplo empaquetado cargado con `ProfileLoader`, determinismo con otro orden de tácticas, las 32 combinaciones de movimientos con líneas exactas, sin arena y con arena, solo `HOLD`, solo `NONE`, `FLANK`, IA base con y sin `lookAtTarget`, desplazamiento y orden relativo, prioridades sin repetir, formato numérico con `Locale` alemán, francés y árabe, constante `PredictorMoveCondition.NAME`, alcance y revisión inválidos); `BaseAiResolverTest` (5, nueva); `BaseAiLinesTest` (5, nueva: prioridad siguiente, 6 y 7 cifras, espacios dentro de argumentos, `clear` en cualquier forma); `InstallDecisionTest` (6, nueva: marcador ausente, ajeno, misma revisión, revisión distinta en ambos sentidos, revisión de depuración, generación distinta); `GoalOwnershipTest` (9, nueva, con dobles de `MobGoals` y `VolatileAIHandler`: dos instalaciones iguales, revisión nueva sin duplicados, generación nueva y marcador ausente, especies con `Brain`, fallo con restauración y registro limitado sin mensaje de la excepción, `LinkageError`, mob no disponible, rechazo fuera del hilo del servidor, líneas instaladas escapadas); `GoalSetInstallerTest` (6, nueva: escape con `MythicLineConfigImpl.unparseBlock` real y partición como `addPathfinderGoals`, idempotencia, reinstalación sin duplicados, lectura del marcador, presets de M01 con escape, `MOVE` distinto de `hold`); `MovementReadingTest` (3, nueva); `HoldGoalActivationTest` (5, nueva, con `IntentBoard` real y reloj controlable); `FlankPathsTest` (3, nueva: 1 bloque y 30° en la frontera, giro a través de 0°/360°); `FlankGoalTest` (11, antes 4: un máximo de una ronda cada 10 ticks con el objetivo moviéndose cada tick, objetivo quieto planificado una vez, reinicio dentro del intervalo, lado alcanzable con menos puntos, sin ruta en ambos lados, sin escudo o con ángulo de 180°, otro movimiento); `OwnerMarkerGoalTest` (5, antes 2); `GoalInspectorTest` (3, antes 2).
- `adapter.server.context.ShieldGeometryTest` (8, nueva: cuatro cuadrantes, 0°/90°/180° en seis orientaciones, atacante en el mismo punto, frontera de 90° con ±0,1° y con el valor exacto, los dos destinos de flanqueo fuera del ángulo para 9 orientaciones y 7 ángulos, tope de 180°, ángulo de las reducciones).
- `adapter.server.binding.BrainSpeciesTest` (4, antes 2: cada nombre resuelto con `EntityType.valueOf` contra Paper API 26.2 y presente en la lista).
- Raíz y `admin`: `DebugProfileGoalsTest` (4, nueva: perfil vinculado con generación de depuración, tipo sin perfil, `unbind` retira la vista antes de `release` en el tick siguiente, textos fijos con `Locale` alemán); `DebugCommandParserTest` (15, antes 13); `ArchitectureRulesTest` (15, antes 13: `adapter.server.goals` y `adapter.server.context` bajo D5 y con acceso mutuo; el puerto de `engine` no alcanza el adapter ni Paper, D2).

### Evidencia estática nueva (javap; no confirma comportamiento en juego)

- MythicMobs 5.13.1 (`v26_2.VolatileAIHandlerImpl.addPathfinderGoals`): divide cada línea con `split(" ")`; si la primera parte es numérica es la prioridad, si no la prioridad es la posición de la línea en la lista (desde 1); `clear` llama a `clearGoalSelector`, que vacía `lockedFlags` y `getAvailableGoals()` del selector de metas (no el de objetivos). `MobType` aplica `MythicLineConfigImpl.unparseBlock` a cada línea de `AIGoalSelectors` al leerla, que cambia los espacios por `<&csp>` y los guiones por `<&da>` dentro de los bloques `{…}` de primer nivel. Sin ese escape, una línea como `3 fleeConditional{…mc=[ - predictormove{m=RETREAT} true ]}` se cortaría en el primer espacio interior.
- `MobType.reapplyAIGoalSelectors` no hace nada si las líneas del mob no contienen `clear` (`aiGoalSelectorsClear`).
- Paper 26.2 (servidor parcheado localmente, sin arrancar): `PaperMobGoals.addGoal` envuelve la meta en `PaperCustomGoal` dentro de `getGoalSelector()` (o `targetSelector` si declara `TARGET`); `getGoal`/`hasGoal` recorren `getAvailableGoals()` y `getGoal` devuelve `PaperCustomGoal.getHandle()`, la instancia de ThePredict; `PaperCustomGoal` añade `UNKNOWN_BEHAVIOR` a una meta sin tipos.
- Vanilla 26.2 (`LivingEntity.applyItemBlocking`, `BlocksAttacks.DamageReduction.resolve`): el ángulo es `acos(dot(normalize(atacante − bloqueador en xz), vista(yaw de la cabeza, pitch 0)))`, `π` sin posición de origen, y la reducción es 0 si el ángulo supera `horizontalBlockingAngle`. Con desplazamiento horizontal nulo `Vec3.normalize` da cero y el ángulo es 90°.

### Desviaciones y decisiones

| # | Desviación o decisión | Motivo |
|---|---|---|
| 1 | `BrainSpecies` se reutiliza en `adapter.server.binding` (no se crea `goals/BrainSpecies`); gana `SPECIES` como `Set<EntityType>` y `contains(EntityType)`; el conjunto normalizado de M03 se deriva de esa lista | Pendiente de M03: una sola lista. El instalador consulta el `EntityType` de la entidad viva, que es exacto |
| 2 | Constructor de `GoalOwnership`: `(Function<UUID, Optional<ActiveMob>>, GoalSetInstaller, GoalLineGenerator, BaseAiResolver, LongSupplier catalogRevision, BooleanSupplier serverThread, ServerClock, Consumer<String>)` en lugar de `(Plugin, VolatileAIHandler, MobGoals, IntentBoard, ServerClock, GoalLineGenerator, BaseAiResolver)` | La firma del puerto no lleva la revisión del catálogo, así que se inyecta su lectura (I11). La búsqueda del `ActiveMob` se inyecta (`MythicBukkit.inst().getMobManager()::getActiveMob` en la raíz) en vez de llamarla dentro (D8, pruebas con dobles). `VolatileAIHandler`, `MobGoals`, `GoalKeys`, `IntentBoard` y el reloj de las metas viven en `GoalSetInstaller` para respetar C10 |
| 3 | Tipos no previstos: `GoalSetInstaller`, `DebugPresetInstaller`, `SpeciesSupport`, `LiveMob`, `FailureLog`, `MovementReading`, `BaseAiLines`, `FlankPaths` y `DebugProfileGoals` (raíz); `engine/MobControl` se crea aquí | C10 (clases de unas 200 líneas y métodos de 30); el puerto de 00 §4 no existía todavía. Son tipos locales del adapter o de la raíz; 01 no cambia |
| 4 | `GoalSetInstaller.selectorLines` aplica `MythicLineConfigImpl.unparseBlock` a cada línea antes de `addPathfinderGoals`, también a los presets de M01 | Evidencia estática de arriba: es lo que hace MythicMobs al leer `AIGoalSelectors`. Los presets de M01 con `mc=[ - … ]` llegaban sin escape y MythicMobs los habría cortado; W5 se cumple mejorándolos. Confirmación en servidor: guion 9.1 |
| 5 | `InstallDecision` es un enum `NO_OP/INSTALL` con `decide(Marker, revisión, generación)`; `Marker` es sellado: `Missing`, `Foreign` (clave `owner` con otra clase, por ejemplo tras recargar el plugin), `Owned(revision, generation, goalCount)` y `Refused(revision, generation, reason)` (marcador de rechazo, segunda revisión). La generación también decide | Las metas de Paper comprueban la generación de su sesión; con otra generación quedarían mudas. C4 impide pasar `OptionalLong`. La lectura del marcador usa `getGoal` (evidencia estática: devuelve la instancia); mientras DT-15 siga abierta se aplica además su alternativa: `removeGoal` de las tres claves antes de instalar. `MobSession` no existe hasta M06 |
| 6 | `OwnerMarkerGoal` guarda revisión, generación y número de metas; `NO_OP` devuelve `Installed(goalCount)` del marcador sin generar el plan. El constructor de M01 con tipos configurables se conserva con `DEBUG_REVISION = -1` | "Guarda revision y generation para diagnóstico"; un conjunto de depuración nunca coincide con una revisión del catálogo y se reemplaza |
| 7 | `clear` se quita de las tres fuentes de IA base (no solo de `AIGoalSelectors`) y una fuente sin líneas útiles cuenta como ausente; una línea sin número recibe su posición en la lista original (desde 1), como MythicMobs, y `BaseAiResolver` la escribe explícita antes de quitar `clear`, para que quitar una línea no cambie la prioridad de las demás; prioridades de más de 6 cifras o con signo, líneas vacías y números sin meta se descartan. `GENERIC` es `0 meleeAttack` y `1 randomStroll` (quedan 10 y 11, como el preset `generated` de M01) | Un `clear` en prioridad 10 o más borraría las metas tácticas; M03 no valida `BaseAI`. La regla de posición sigue la evidencia estática de DT-16 (A1), así que una lista mixta conserva el orden relativo nativo; DT-16 sigue abierta y el generador siempre escribe prioridades explícitas |
| 8 | `followRange` sale del atributo `FOLLOW_RANGE` de la entidad (lo fija `Options.FollowRange` de MythicMobs); sin atributo o con valor no positivo se usa `rangedReachBlocks` del perfil | A1 (atributo nativo del plan §2). Confirmación en servidor: guion 9.1 |
| 9 | El generador sigue la tabla de M05.1: `goToSpawnLocation` y la banda usan la velocidad de aproximación y `minrange` es `meleeReachBlocks` (2,5). El boceto del plan §3 escribe 1,2 y 1,0 y `minrange=2` | La tabla es la especificación del módulo; el perfil de ejemplo no tiene velocidades propias para esas metas. La cuarta casilla de M05.1 queda sin marcar hasta que la revisión decida si se ajusta la tabla o el boceto |
| 10 | `FlankGoal`: solo se activa si una ronda de planificación está permitida, para que parar y reanudar no supere una ronda cada 10 ticks; "una vez" se interpreta como una ronda, que pide una ruta por lado; el escudo es un `HumanEntity` con la mano levantada y un ítem activo con `BLOCKS_ATTACKS` (cubre levantarlo y bloquear); con un ángulo de 180° o más no se activa; si baja el escudo en mitad del flanqueo conserva el último ángulo; la lectura del ángulo se inyecta (`ShieldGeometry::raisedShieldAngleDegrees`); los ticks se comparan módulo 2³² como el contador de Bukkit (regla `shouldReplan` de M01) | Aceptación de M05.3 y plan §5 ("bloqueando o levantando un escudo"); pruebas sin servidor |
| 11 | `ShieldGeometry` ofrece métodos puros con yaw y desplazamientos y otros con `Location`; usa `Location.getYaw()` del objetivo | Vanilla usa el yaw de la cabeza; en jugadores coincide con `getYaw()`. Pendiente de servidor con un escudo real |
| 12 | Los cuatro métodos del puerto lanzan `IllegalStateException` fuera del hilo del servidor (`Bukkit::isPrimaryThread` inyectado) | Hace comprobable T1; es un error de programación (C7) |
| 13 | Un fallo o una instalación incompleta quita las metas de Paper, reaplica `AIGoalSelectors` y se registra como máximo una vez cada 200 ticks, con el tipo de excepción o el número de metas que faltan y el recuento omitido, nunca con un mensaje de excepción. El límite vive en `FailureLog`, estado mutable fuera de `engine` | A6, C8 y W6. A3 reserva el estado mutable a `engine`; este es solo el límite de un registro del adapter, sin estado por mob ni efecto en decisiones, y C8 lo exige |
| 17 | Un fallo inesperado o una instalación incompleta responde `Unsupported`, no `Deferred`; se restaura el mob y se deja un marcador `owner` de rechazo (`OwnerMarkerGoal.refused`) con la revisión, la generación y la causa. Una llamada repetida con la misma revisión y generación responde lo mismo sin tocar las metas; una generación o revisión nueva reintenta, y `release` quita el marcador. Los mobs que no son `Creature` y las especies con `Brain` responden `Unsupported` antes de cualquier cambio (`SpeciesSupport`) | Decisión de la segunda revisión: las metas de movimiento de MythicMobs exigen `isCreature` (javap), así que el conjunto nunca estaría completo en esos mobs, y reintentar cada decisión borraría y reinstalaría metas sin límite. La excepción inesperada también es `Unsupported`: es un error de programación o de API (C7), determinista para las mismas entradas, y la única causa transitoria (mob no cargado o no válido) se detecta antes y sigue en `Deferred`. El adaptador sigue sin estado por mob (A3): el rechazo vive en el marcador del propio mob, como la revisión instalada; M09.1 deja de pedir la instalación tras `Unsupported` |
| 14 | Depuración: `install <mob> profile` instala el conjunto del perfil vinculado con la generación 0 de depuración; `unbind <mob>` retira la vista y llama a `release` en el tick siguiente (orden de M05.4); `goals` añade la línea de progreso. `autoinstall` sigue aceptando solo presets | Entrega B: mover un mob vinculado con intenciones fijadas por el comando, sin Jev. Los flujos y líneas de M01 no cambian (W5) |
| 15 | `ThePredictPlugin` cambia (el documento decía que no) y pasa de 357 a 365 líneas; `DebugCommandHandler` de 270 a 280 | El constructor de `GoalOwnership` cambia y hay que construir `GoalSetInstaller`; ambos son heredados o provisionales y M12 los reduce. Ningún método supera 30 líneas |
| 16 | Las partes de `engine` de M05.4 (límite `decision.maxInstallsPerTick`, marca de instalación pendiente, prueba de 50 sesiones en 5 ticks, liberación de hasta 200 sesiones en el apagado) no se implementan | El documento las asigna a `engine.MobSession` y `engine.Coordinator` (M06 y M09), que aún no existen |

### Pendiente de verificación en servidor

- Guion de M01, sección 9 (M05.7): cada movimiento, banda sin oscilación, arena, `HOLD` mirando al objetivo, flanqueo contra un escudo, IA base al soltar, `unbind`, `/mm reload` y marcador.
- DT-15: que `clear` elimine las metas de Paper y que `getGoal(owner)` devuelva la instancia de ThePredict; que dos `install … profile` seguidos no dupliquen metas.
- DT-16, DT-04 y DT-19: línea sin prioridad, `release` con y sin `clear` en `AIGoalSelectors`, especies con `Brain`.
- Que MythicMobs acepta las líneas escapadas sin `An error occurred while adding an AIGoalSelector` y que los presets de M01 se comportan igual que antes.
- Lectura de `FOLLOW_RANGE` y del componente `BLOCKS_ATTACKS` con un escudo real.
- Que el recuento de `MobGoals` tras `addPathfinderGoals` coincide con las líneas del conjunto generado (paso 9.1), y que una línea rota produce `deferred (install incomplete: …)` sin marcador.
- Aceptaciones sin marcar de M05.2, M05.4, M05.6 y M05.7.

### Pendiente para módulos posteriores

- M06: marcar la instalación pendiente un tick después de `MythicMobSpawnEvent` y de `EntitiesLoadEvent`; al desvincular, retirar la vista y llamar a `release` en el tick siguiente; en el apagado (T7) liberar como máximo 200 sesiones; publicar vistas con la generación de la sesión (el marcador guarda la generación). Si DT-15 no confirma la lectura del marcador, guardar `installedRevision` en `MobSession`.
- M09 (hecho): límite `decision.maxInstallsPerTick` en el coordinador, con la prueba de 50 sesiones en 5 ticks, y autocomprobación con `ensureInstalled` antes de cada decisión.
- M10 (hecho): regla de falta de progreso con `MovementProgress` (`NO_PROGRESS`) y comprobación del ángulo antes del cast de `FLANK` con `ShieldGeometry.isOutsideBlockingAngle`.
- M07: reutilizar `ShieldGeometry` para `RAISING_SHIELD`, `BLOCKING` y `flankable`.
- M12: `/thepredict inspect` con `describeGoals`; retiro de `debug install … profile` y `debug unbind` junto con el comando de depuración. **Hecho en M12** (ver su [estado de implementación](M12-Diagnostico-instalacion-retiro-OpenRouter.md#estado-de-implementación)).
- Revisión de M05.1: decidir si las velocidades del boceto del plan §3 o las de la tabla del módulo son las correctas (desviación 9).

### Correcciones tras la auditoría (2026-09-29)

La auditoría aprobó el módulo sin bloqueos. `./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **564 pruebas, 0 fallos** (6 nuevas). 00, 01, el plan, M06 y M09 no cambian.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| Instalación parcial: `addPathfinderGoals` captura cualquier `Exception`, registra `An error occurred while adding an AIGoalSelector` y retorna sin lanzar, así que una línea con error cortaba el resto (IA base, `lookAtTarget`) y el marcador se añadía igual, sin reintento | `GoalSetInstaller.install` cuenta con `MobGoals` las metas del selector que no son de objetivo ni de ThePredict (`mythicGoalCount`) y devuelve cuántas faltan respecto al plan; si falta alguna no añade las metas de Paper ni el marcador. `GoalOwnership` restaura (quita las metas de Paper y reaplica `AIGoalSelectors`) y registra con límite en `FailureLog`; el resultado lo fijó la segunda revisión (ver la tabla siguiente). Evidencia en DT-15 y DT-16; ambas siguen `Abierta` | `GoalOwnershipTest.aSetCutShortIsUnsupportedRestoredAndNotReinstalledForTheSameRevisionAndGeneration`, `GoalSetInstallerTest.aSetCutShortByMythicMobsReportsTheMissingGoalsAndGetsNoPaperGoals`, `mythicGoalCountIgnoresTargetGoalsAndThePaperGoals`. `FakeAiHandler` reproduce el comportamiento real: registra el error, deja de añadir líneas y no lanza. El fallo con excepción se prueba ahora con un fallo de Paper al añadir la meta |
| `BaseAiLines` daba a una línea sin número la prioridad anterior + 1; MythicMobs usa la posición en la lista | Regla nativa (A1): posición en la lista original, desde 1; `BaseAiResolver` escribe la prioridad explícita antes de quitar `clear`; también se descartan prioridades con signo | `BaseAiLinesTest.aMixedListKeepsTheNativeRelativeOrder` (`5 meleeAttack`, `randomStroll` da 5 y 2), `BaseAiResolverTest.aLineWithoutNumberKeepsItsNativePositionAfterClearIsDropped`, `GoalLineGeneratorTest` actualizado |
| `FailureLog` es estado mutable fuera de `engine` sin citar A3 | Desviación 13 cita A3 y la justificación por C8; el intervalo pasa a `FailureLog.INTERVAL_TICKS` | — |
| `FlankGoal.FLANK_ANGLE_DEGREES` y `flankOffset` duplicaban `ShieldGeometry` y solo los usaban las pruebas | Eliminados; las pruebas usan `ShieldGeometry.offset` y `flankTurnDegrees`. `FlankGoal` baja a 197 líneas | `FlankGoalTest` |
| Faltaba una prueba de ida y vuelta con las clases reales de MythicMobs | Cada línea generada (con y sin arena, todos los movimientos, IA base con `mc` anidado y números negativos) y cada línea de cada preset: la línea escapada se divide en exactamente prioridad y meta (o solo la meta si no lleva número), `parseBlock` devuelve la meta original, el escape es idempotente y `MythicLineConfigImpl` devuelve la clave y la lista `mc=[ - … ]` sin cambios | `GoalSetInstallerTest.everyGeneratedAndPresetLineSurvivesTheRealMythicMobsRoundTrip` |
| Guion de M01: la sección de M05 iba después del cierre; el paso de `TPV_Piglin` no daba el perfil v2; el paso de `FLANK` no explicaba el caso sin escudo | La sección de M05 pasa a ser la 9 y el cierre la 10 (referencias actualizadas en el registro y en este documento); el paso 9.9 incluye el perfil v2 exacto (`Movement: NONE`, comprobado con `ProfileLoader`); el paso 9.5 indica que sin escudo `thepredict:flank` no arranca y `hold` deja al mob quieto, que es lo esperado porque M10 filtra la elegibilidad | — |
| Tamaño de clases | Para mantener unas 200 líneas: `DebugPresetInstaller` (paquete privado) recibe la instalación de presets de M01; `MovementReading.NONE` sustituye la constante de `GoalOwnership`. `GoalOwnership` 199, `GoalSetInstaller` 190 y `FlankGoal` 197 líneas | Suite completa |

### Correcciones tras la segunda revisión (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **569 pruebas, 0 fallos** (5 nuevas). 00, 01, el plan, M06 y M09 no cambian.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** `addPathfinderGoals` salta una línea no válida para la especie y continúa; `float`, `randomStroll` y `fleeConditional` exigen `isCreature()`, así que slime, magma cube, ghast, phantom y murciélago (no son `Brain`) quedaban `Deferred` para siempre, igual que una criatura con una línea de IA base no válida o desconocida, con reinstalaciones y registros de MythicMobs sin límite | `SpeciesSupport.unsupportedReason`: especie con `Brain` o mob que no es `org.bukkit.entity.Creature` responde `Unsupported("species without MythicMobs movement goals")` sin tocar metas. Una instalación incompleta o un fallo inesperado responden `Unsupported`, restauran el mob y dejan un marcador de rechazo que evita reinstalar con la misma revisión y generación (desviación 17). `MobControl.ensureInstalled` documenta los nuevos casos | `GoalOwnershipTest.aMobThatIsNotACreatureIsUnsupportedWithoutAnyInstallAttempt`, `aBaseAiLineMythicMobsSkipsForTheSpeciesMakesTheSetUnsupportedUntilTheGenerationChanges`, `aSetCutShortIsUnsupportedRestoredAndNotReinstalledForTheSameRevisionAndGeneration`, `aLinkageErrorIsAlsoUnsupported`; `InstallDecisionTest.aRefusalOfTheSameRevisionAndGenerationIsKeptAndAnyChangeRetries`; `OwnerMarkerGoalTest.refusalMarkerKeepsItsReasonNeverRunsAndNeedsAReason` |
| El Javadoc de `mythicGoalCount` decía que una meta sin tipos no se cuenta | Corregido con la evidencia: `Goal` de `net.minecraft` añade `UNKNOWN_BEHAVIOR` a un conjunto vacío y `PaperMobGoals.getAllGoals` recorre todos los `GoalType`; solo quedaría fuera una meta del selector de metas que declarase `TARGET`, y ninguna lo hace. Corregido también en DT-15 | — |
| `FakeAiHandler` no modelaba el salto de una línea no válida | Salta la línea, registra `AI pathfinder … is not valid for mob type` y continúa | `GoalSetInstallerTest.aLineInvalidForTheSpeciesIsSkippedAndTheRestStillCountsAsIncomplete` |
| DT-19 no citaba el paso 9.9 | Añadido a su evidencia en servidor | — |
| Tamaño de clases | `SpeciesSupport` (paquete privado) y `MovementReading.followRangeBlocks` salen de `GoalOwnership` (196 líneas); `GoalSetInstaller` queda en 200 | Suite completa |

