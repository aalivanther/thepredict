# M07. Contexto nativo y percepción

> **Estado:** Local listo — servidor pendiente (ver [Estado de implementación](#estado-de-implementación)) · **Entrega:** C · **Depende de:** M06 · **Desbloquea:** M08, M09, M11 · **Diseño:** [plan §6](../Plan-Jev-y-combate-tactico.md#estado-semántico-no-numérico), [plan §6 — Percepción natural](../Plan-Jev-y-combate-tactico.md#percepción-natural), [plan §6 — Señales por evento](../Plan-Jev-y-combate-tactico.md#señales-por-evento-no-por-sondeo) y [plan §2](../Plan-Jev-y-combate-tactico.md#evidencia-del-binario-paper-262-y-de-minecraft-vanilla)

## Objetivo

Construir los hechos de combate de [01 §4](01-Modelo-de-dominio.md#4-hechos-de-combate) leyendo solo APIs y eventos nativos de Paper y MythicMobs, y limitarlos a lo que el mob podría percibir. `adapter.server.context.CombatCapture` implementa el puerto `CombatReader`. Nada de lo que Paper, MythicMobs o vanilla ya exponen se recalcula (A1). Al terminar M07, un encuentro produce un `CombatFacts` reproducible sin Jev, que M08 convierte en texto.

## Alcance

### Incluye

- Captura completa por encuentro (`capture`) y sondas baratas por mob (`probe`).
- Percepción natural por `FOLLOW_RANGE`, línea de visión e invisibilidad.
- Traducción de estado del jugador a `TargetCue`, `HealthBand`, `ArmorBand`, `DistanceBand` y `Trend`.
- Suscripción a eventos de contexto con filtro O(1) y registro de `EncounterEvent`.
- Evaluación de los contextos nativos del perfil.
- Resumen del combate desde `CombatTracker`.

### No incluye

- El texto en inglés para Jev (M08).
- La decisión de cuándo solicitar (M09) y la aplicación de contingencias (M10).
- La amenaza enriquecida (M11); M11 reutiliza los listeners y el índice de este módulo.

## Base existente

| Archivo | Acción |
|---|---|
| `src/main/java/akyrion/thepredict/adapter/paper/PaperCombatSnapshotReader.java` | Se sustituye por `adapter.server.context.CombatCapture`. Se conservan la búsqueda de objetivo por tabla de amenaza con respaldo en `Mob#getTarget()` y la validación de salud máxima. |
| `src/main/java/akyrion/thepredict/adapter/paper/NearestK.java` | Se mueve sin cambios a `adapter.server.context`. |
| `src/test/java/akyrion/thepredict/adapter/paper/NearestKTest.java` | Se mueve con la clase. |
| `src/main/java/akyrion/thepredict/snapshot/CombatSnapshot.java`, `VitalSnapshot.java` | Los sustituyen los tipos de 01 §4 creados en M02; se eliminan en M12 con el proveedor OpenRouter, que los sigue usando a través de `decision.DecisionRequest` (W5; M09 retiró el runner, desviación 21 de M09). |

## Subpuntos

### M07.1 `CombatCapture`

- **Propósito:** producir un `CombatFacts` por encuentro en el hilo del servidor, con costo acotado y medido.
- **Diseño:**
  - `public final class CombatCapture implements CombatReader`, sin estado mutable propio (A3).
  - `Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context)`, con `CaptureContext` de 01 §4:
    1. Por cada miembro: `ActiveMob` con `getActiveMob(uuid)`, entidad `LivingEntity` válida y viva. Los miembros inválidos se omiten; sin miembros válidos, el resultado es vacío.
    2. Objetivo: jugador con mayor amenaza de `ActiveMob.getThreatTable().getTopThreatHolder()` si el mob tiene tabla; si no, `Mob#getTarget()` si es un jugador válido del mismo mundo. Es la lógica actual de `findTarget`.
    3. Participantes: `world.getNearbyPlayers(ubicación, radio)` por miembro, con el radio de M07.2, sin duplicados, solo en modo `SURVIVAL` o `ADVENTURE`, vivos y válidos. `NearestK` elige los 16 más cercanos a cualquier miembro.
    4. Alias: la captura llama a `context.aliasFor(uuid)`, que es `ParticipantAliases::aliasFor` del encuentro (M06.3); la captura no guarda alias propios.
    5. Se construyen `MobFacts`, `TargetRelation` y `ParticipantFacts`; `recentEvents` es `context.recentEvents()`, copia del historial del encuentro, y `combatSeconds` sale de M07.8.
  - El tiempo de captura se mide con `ServerClock.nanoTime()` y se publica como métrica (M09).
  - Todas las lecturas ocurren en el hilo del servidor (T1). El resultado es inmutable y puede cruzar hilos (A4).
- **Archivos:** crear `adapter/server/context/CombatCapture.java`; mover `NearestK.java`; eliminar `PaperCombatSnapshotReader.java` cuando M09 retire el runner.
- **Reglas:** A1, A3, A4, A5, T1, I6.
- **Pruebas:** las funciones de traducción (M07.3–M07.8) se prueban como funciones puras sobre valores leídos; la captura completa, en servidor con 1, 3 y 16 jugadores.
- **Aceptación:**
  - [x] Ningún `CombatFacts` contiene nombres, UUID ni textos de jugadores. Local: los tipos de 01 §4 solo llevan alias y bandas; `CombatCaptureTest` comprueba que el texto de los hechos no contiene la UUID del jugador.
  - [ ] La captura de un encuentro con 16 jugadores se mide y queda registrada en M13.
  - [x] Un miembro muerto durante la captura no produce excepción. Local, con dobles: un miembro muerto, inválido, sin perfil o sin salud máxima válida se omite (`CombatCaptureTest`).

### M07.2 Percepción natural

- **Propósito:** que Jev decida con la información que el mob podría tener (plan §6).
- **Diseño:**
  - Radio de percepción: `mob.getAttribute(Attribute.FOLLOW_RANGE).getValue()`, el mismo valor que fija `Options.FollowRange`, limitado por `decision.searchRadiusBlocks` como tope global de seguridad.
  - Las señales visuales (ítem en mano, escudo, arco, consumo, tótem y efectos visibles) solo se incluyen si `LivingEntity#hasLineOfSight(jugador)` es verdadero y `jugador.isInvisible()` es falso. Si el jugador es invisible, solo se incluye `INVISIBLE`.
  - La banda de salud del objetivo se incluye siempre; es una decisión de diseño documentada y ajustable en M13.
  - La línea de visión se calcula una vez por par mob-jugador y captura, y solo para los participantes seleccionados.
  - Que `isInvisible()` refleje también el efecto de invisibilidad: **M01 confirma**. Alternativa: comprobar además `hasPotionEffect(PotionEffectType.INVISIBILITY)`.
- **Archivos:** crear `adapter/server/context/Perception.java`.
- **Reglas:** A1, A5, C12.
- **Pruebas:** en servidor, jugador detrás de un muro sin señales visuales; jugador invisible solo con `INVISIBLE`; jugador fuera del `FOLLOW_RANGE` ausente del estado.
- **Aceptación:**
  - [x] Ningún jugador fuera del radio aparece en `participants`. Local: esfera de `FOLLOW_RANGE` con tope, esquinas de la caja excluidas y objetivo fuera del radio no percibido (`CombatCaptureTest`, `PerceptionTest`); en servidor, paso 11.2.
  - [x] Ninguna señal visual aparece sin línea de visión. Local (`TargetCueReaderTest`, `FactsAssemblerTest`); en servidor, pasos 11.3 y 11.4.

### M07.3 Señales del objetivo (`TargetCue`, `HealthBand`, `ArmorBand`)

- **Propósito:** convertir el estado del jugador en señales semánticas con APIs verificadas.
- **Diseño:** `final class TargetCueReader`, con un método por señal para probarlas por separado.

  | Señal | Fuente nativa |
  |---|---|
  | `BLOCKING` | `HumanEntity#isBlocking()`, que ya incluye el retraso de bloqueo vanilla |
  | `RAISING_SHIELD` | `hasActiveItem()`, `getActiveItem().hasData(DataComponentTypes.BLOCKS_ATTACKS)` y `isBlocking()` falso |
  | `SHIELD_DISABLED` | `hasCooldown(escudo en mano)` o un `PlayerShieldDisableEvent` en el historial del encuentro |
  | `ATTACK_RECHARGING` | `HumanEntity#getAttackCooldown() < 0.9f` |
  | `DRAWING_BOW` | Ítem activo de tipo `Material.BOW` |
  | `BOW_FULLY_DRAWN` | Lo anterior y `getActiveItemUsedTime() >= 20` (arco a plena potencia en vanilla) |
  | `CROSSBOW_LOADED` | Ítem en una mano con `getData(DataComponentTypes.CHARGED_PROJECTILES)` y `projectiles()` no vacío |
  | `CONSUMING` | Ítem activo con `hasData(DataComponentTypes.CONSUMABLE)` |
  | `HOLDS_TOTEM` | Ítem en una mano con `hasData(DataComponentTypes.DEATH_PROTECTION)` |
  | `PEARL_ON_COOLDOWN` | `HumanEntity#getCooldown(Material.ENDER_PEARL) > 0`; equivalencia con el grupo `USE_COOLDOWN`: **M01 confirma**; alternativa `getCooldown(Key)` |
  | `GLIDING` | `isGliding()` |
  | `LONG_REACH_WEAPON` | Ítem principal con `KINETIC_WEAPON`, `PIERCING_WEAPON` o `ATTACK_RANGE` |
  | `MACE_DIVE` | `EntityAttemptSmashAttackEvent` reciente en el historial, o `getFallDistance() > 1.5` con `Material.MACE` en la mano principal |
  | `INVISIBLE` | `isInvisible()` |

  - Excepción justificada a "sin listas de `Material`" (A1): arco, maza y perla no tienen un componente exclusivo verificado, así que se identifican por tipo.
  - `HealthBand`: `(getHealth() + getAbsorptionAmount()) / MAX_HEALTH`. `CRITICAL` hasta `HealthThresholds.criticalRatio` (0,3 por defecto); `WOUNDED` hasta `woundedRatio` (0,6); `HEALTHY` por encima. Los mismos umbrales valen para el mob.
  - `ArmorBand`: `ARMOR` menor que 8 es `LIGHT`; de 8 a 15, `MEDIUM`; desde 16, `HEAVY`. Un `ARMOR_TOUGHNESS` de 4 o más sube una banda.
  - Los umbrales son constantes documentadas que M13 calibra.
- **Archivos:** crear `adapter/server/context/TargetCueReader.java` y `adapter/server/context/Bands.java` (funciones puras de bandas); prueba `BandsTest.java`.
- **Reglas:** A1, C5, C12.
- **Pruebas:** `BandsTest` cubre todas las fronteras. En servidor, cada señal de la tabla con su situación real (escudo levantado frente a bloqueando, arco a mitad y completo, ballesta cargada, poción, tótem, perla recién lanzada, lanza, maza, invisible).
- **Aceptación:**
  - [ ] Cada señal tiene un escenario de servidor con evidencia.
  - [x] `RAISING_SHIELD` y `BLOCKING` nunca aparecen juntas. Local: las cuatro combinaciones (`TargetCueReaderTest`).

### M07.4 Relación con el objetivo (`TargetRelation`)

- **Propósito:** describir la situación táctica mob-objetivo con significado, no con coordenadas.
- **Diseño:**
  - `DistanceBand`: `MELEE` hasta `meleeReachBlocks`; `RANGED` hasta `rangedReachBlocks`; `BEYOND` por encima (01 §3).
  - `Trend`, sin estado en el adapter:
    - Con `Player#getCurrentInput()`: si el jugador mira hacia el mob (producto escalar de su mirada horizontal con la dirección hacia el mob mayor que 0,5), avanzar es `CLOSING` y retroceder es `BACKING_AWAY`. Solo izquierda o derecha es `STRAFING`. Sin entrada, `STEADY`.
    - Si `engine` guarda la distancia de la captura anterior (01, revisión 2), una variación mayor de 1 bloque corrige el resultado.
    - En jugadores no se usa `getVelocity()`: no refleja su desplazamiento al caminar.
  - `reachable`: `MobControl.progress(key).pathReachesTarget()` si hay una ruta en curso. Si no la hay y `engine` autorizó una comprobación para este mob en esta captura, `mob.getPathfinder().findPath(objetivo)` y `canReachFinalPoint()`. Sin ruta ni autorización, se conserva el último valor conocido por `engine`, o `true` si no hay ninguno.
  - `outsideTargetReach`: la distancia supera el alcance del objetivo, que es `ENTITY_INTERACTION_RANGE` o `AttackRange.maxReach()` si su arma tiene `ATTACK_RANGE`.
  - `visible`: resultado de M07.2.
  - `flankable`: el objetivo tiene `BLOCKING` o `RAISING_SHIELD`, es visible y el mob está dentro del ángulo de bloqueo según `ShieldGeometry.isOutsideBlockingAngle` (M05.3).
- **Archivos:** crear `adapter/server/context/TargetRelationReader.java`; reutilizar `adapter/server/context/ShieldGeometry.java` de M05.
- **Reglas:** A1, A3, C5, C12.
- **Pruebas:** tendencia para cada combinación de entrada y orientación; fronteras de bandas; `outsideTargetReach` con y sin `ATTACK_RANGE`; en servidor, jugador sobre pilares marcado como no alcanzable.
- **Aceptación:**
  - [x] Ninguna relación usa la velocidad de un jugador. Ninguna clase de M07 llama a `getVelocity()`.
  - [x] `findPath` solo se llama con autorización de `engine`. Local (`TargetRelationReaderTest`, `FactsAssemblerTest`).

### M07.5 Eventos: historial y contexto pendiente

- **Propósito:** reaccionar a lo que hace el jugador sin sondear (plan §6).
- **Diseño:**
  - `adapter.server.events.CombatContextListener`, con prioridad `MONITOR` e `ignoreCancelled = true` para observar el resultado final. Todos los eventos de la tabla son síncronos en Paper.
  - Filtro O(1): cada evento busca el jugador o mob en el índice de participantes y miembros de `EncounterRegistry` (M06.3). Si no pertenece a ningún encuentro, se descarta sin más trabajo.

  | Evento | Registro en el historial | Contexto pendiente |
  |---|---|---|
  | `PlayerReadyArrowEvent` | — | Sí |
  | `PlayerStopUsingItemEvent` | — | Sí |
  | `EntityLoadCrossbowEvent` | — | Sí |
  | `PlayerShieldDisableEvent` | `SHIELD_DISABLED` | Sí |
  | `PlayerItemConsumeEvent` | `POTION_CONSUMED` si es una poción | Sí |
  | `EntityResurrectEvent` de un participante | `TOTEM_USED` | Sí |
  | `EntityEquipmentChangedEvent` | — | Sí |
  | `PlayerItemGroupCooldownEvent` | — | Sí |
  | `PlayerTeleportEvent` con causa `ENDER_PEARL` | `PEARL_THROWN` | Sí |
  | `PrePlayerAttackEntityEvent` contra un miembro | — | Sí |
  | `EntityAttemptSmashAttackEvent` | — | Sí |
  | `EntityLungeEvent` | — | Sí |
  | `PlayerInputEvent` | — | Como máximo una vez cada 10 ticks por participante |
  | `EntityDamageByEntityEvent` de un miembro a un participante | `MELEE_HIT`, o `MELEE_BLOCKED` si `isBlocking()` | Solo si fue bloqueado |
  | `EntityDamageByEntityEvent` de un proyectil de un participante a un miembro | `RANGED_HIT_ON_MOB` | Sí |
  | `ProjectileHitEvent` de un participante sin entidad alcanzada | `RANGED_MISS` | No |
  | `PotionSplashEvent` y `AreaEffectCloudApplyEvent` con aliados afectados | `ALLY_ASSISTED` | Sí |
  | `EntityPathfindEvent` de un miembro | — (métrica de diagnóstico) | No |

  - El historial es el anillo de 16 eventos de `Encounter` (M06.3). Un evento igual del mismo participante dentro de 10 ticks se descarta.
  - No se escuchan `EntityMoveEvent`, que se dispara con cada movimiento de entidades vivas, ni `PlayerAttackEntityCooldownResetEvent`, marcado como obsoleto en su Javadoc.
  - Un evento nunca envía una solicitud: solo marca o registra (plan §6).
- **Archivos:** crear `adapter/server/events/CombatContextListener.java`; M11 añade sus pesos de amenaza en el mismo recorrido.
- **Reglas:** A1, A5, C12, T1, I6.
- **Pruebas:** en `engine`, varias marcas en un tick producen una sola marca; un evento repetido dentro de 10 ticks no duplica el historial; el anillo no supera 16. En servidor, cada fila de la tabla genera su efecto.
- **Aceptación:**
  - [x] Un evento de un jugador sin encuentro no hace trabajo adicional. Local: una búsqueda O(1) en el índice y nada más; los ítems, efectos y entidades afectadas solo se leen para un participante (`ContextEventsTest`, `CombatContextListenerTest`, `CombatOutcomeListenerTest`).
  - [x] Ningún evento de la tabla dispara una solicitud directa. `ContextEvents` solo registra y marca; no tiene acceso a `DecisionClient`.

### M07.6 Sondas baratas (`probe`)

- **Propósito:** alimentar `ContingencyPolicy` cada 2 ticks sin capturar el encuentro completo.
- **Diseño:**
  - `Optional<MobProbe> probe(MobKey key)`: entidad válida, `HealthBand` del mob, `DistanceBand` del objetivo mediante `distanceSquared` contra los cuadrados de los umbrales y cambio de objetivo.
  - Sin líneas de visión, sin rutas, sin registro y sin streams (C12).
  - `engine.Agenda.dueProbes` reparte las sondas por paridad del hash del UUID (M06.5).
- **Archivos:** modificar `adapter/server/context/CombatCapture.java`.
- **Reglas:** A5, C12, T1.
- **Pruebas:** microprueba en servidor con 100 mobs vinculados; el tiempo por sonda se registra en M13.
- **Aceptación:**
  - [x] Una sonda no calcula rutas ni líneas de visión. Local (`CombatCaptureTest`).
  - [x] Cada sesión recibe una sonda cada 2 ticks cuando el límite de la agenda lo permite. Desde M09 el paso 4 del coordinador une `Agenda.dueProbes` (M06.5, `AgendaTest`) con `probe` hasta `decision.maxProbesPerTick` (`CoordinatorTest.noStepGoesBeyondItsLimitPerTick`).

### M07.7 Contextos nativos activos

- **Propósito:** evaluar los contextos del perfil con condiciones de MythicMobs, sin lenguaje propio (plan §3).
- **Diseño:**
  - M03 guarda en `BoundProfile`, por cada `ContextRule`, las `SkillCondition` obtenidas con `getSkillManager().getConditions(nativeConditions)`.
  - En la captura, cada contexto se evalúa sobre el mob. `evaluateEntity(BukkitAdapter.adapt(entidad))` o `evaluateCaster(metadata)` con metadatos construidos como en `MythicSkillExecutor`: cuál sirve para condiciones del caster como `healthpercent{p=<30%}`, **M01 confirma**.
  - Un contexto está activo si todas sus condiciones son verdaderas. El resultado es `Set<ContextId>`.
  - Los atajos heredados (`ContextRule.SHORTCUTS`: `idle`, `attacking_phase`, `low_health_phase`, `tracking_enemy`, definidos en M03.3) se calculan en Java con las bandas de M07.3 y M07.4, sin MythicMobs.
  - Tope: 16 contextos por perfil (validado en M03); cada contexto se evalúa una vez por captura.
- **Archivos:** crear `adapter/server/context/NativeContextEvaluator.java`.
- **Reglas:** A1, A5, C13.
- **Pruebas:** en servidor, un contexto `healthpercent{p=<30%}` se activa al bajar la salud; los atajos coinciden con el comportamiento de `PredictionPlanner` en los casos de su prueba actual.
- **Aceptación:**
  - [x] Ningún contexto del perfil se evalúa con lógica propia cuando existe condición nativa. Solo los atajos de `ContextRule.SHORTCUTS`, que no tienen líneas nativas, se calculan en Java.
  - [x] Una condición que lanza excepción desactiva solo ese contexto y deja una causa registrada. Local (`NativeContextEvaluatorTest`), con registro limitado sin el mensaje de la excepción.

### M07.8 Resumen desde `CombatTracker`

- **Propósito:** usar el historial de combate vanilla en lugar de mantener uno propio.
- **Diseño:**
  - `combatSeconds`: `LivingEntity#getCombatTracker().getCombatDuration()` del miembro con mayor duración, convertido a segundos. La unidad de `getCombatDuration()`: **M01 confirma**; se supone ticks.
  - `inCombat(UUID)` para el cierre de encuentros de M06.4: `getCombatTracker().isInCombat()`.
  - Clasificación de entradas: `CombatEntry#getDamageSource().getDamageType()` comparado con etiquetas de `DamageTypeTagKeys` (`IS_PROJECTILE`, `IS_PLAYER_ATTACK`, `IS_EXPLOSION`, `IS_FIRE`) mediante `Registry#getTag(tagKey)` y `contains(TypedKey)`. La conversión exacta de `DamageType` a `TypedKey`: **M01 confirma**.
  - Como `CombatEntry` no expone el momento de cada golpe, los hechos "recientes" salen del historial por eventos (M07.5). `CombatTracker` aporta duración, estado de combate y totales por tipo de daño.
- **Archivos:** crear `adapter/server/context/CombatSummaryReader.java`.
- **Reglas:** A1, C5.
- **Pruebas:** en servidor, tres flechas contra el mob se clasifican como proyectil; `combatSeconds` crece durante el combate y el encuentro se cierra tras 300 ticks sin daño.
- **Nota de M12 (2026-09-29):** la clasificación de entradas por etiquetas de daño (`CombatSummaryReader.damageTotals`, `classify`, `DamageKind`) se retiró como código muerto: solo la mostraba `debug capture`, que M12 retiró, y ni M07 ni M11 la consumen (la amenaza enriquecida usa `EntityDamageEvent#getFinalDamage()`). Queda `combatSeconds`, que `inspect` muestra. Ver [DT-25](M01-Registro-de-decisiones.md#dt-25-resumen-de-combattracker).
- **Aceptación:**
  - [x] Ningún tipo de daño se clasifica con listas de causas escritas a mano. Solo etiquetas de `DamageTypeTagKeys`.
  - [x] El cierre de encuentros usa el estado de combate nativo — parte local: `CombatReader.inCombat` lee `CombatTracker#isInCombat()` y la tabla de amenaza, y el coordinador de M09 llama a `closeIfFinished(…, combatReader::inCombat)` en cada evaluación (`CoordinatorTest.anEncounterClosesAtTheFirstHeartbeatAfterItsCombatEnded`); la lectura en un servidor real sigue en el guion.

## Riesgos y decisiones abiertas

- **Costo de la línea de visión** con muchos participantes: el tope de 16 y el cálculo único por captura lo acotan; M13 mide el caso de 16 jugadores.
- **Entrada del jugador con latencia alta:** si `getCurrentInput()` resulta poco fiable, la tendencia usa solo la variación de distancia guardada por `engine`.
- **Umbrales de bandas:** son punto de partida; M13 los calibra con el banco de escenarios.
- **Incorporado en 00 y 01 (revisión 2):** cambiar la firma a `CombatReader.capture(EncounterId, List<MobKey>, CaptureContext)` con `CaptureContext(Function<UUID, ParticipantAlias> aliasFor, Set<MobKey> allowPathfinding, Map<MobKey, Double> previousTargetDistanceBlocks)`. Así los alias, la autorización de rutas y la distancia anterior siguen en `engine` (A3) y la captura no guarda estado.
- **Incorporado en 01 (revisión 2):** en `MobProbe`, sustituir `boolean targetChanged` por `Optional<UUID> targetId`; `engine` compara con `MobSession.lastTargetId`, porque el adapter no guarda el objetivo anterior.
- **Incorporado en 01 (revisión 2):** añadir `meleeReachBlocks` (valor inicial 2,5) y `rangedReachBlocks` (valor inicial 16) a `MovementSettings` para definir `DistanceBand`; `meleeReachBlocks` coincide con la propuesta de M05.
- **Incorporado en 00 (revisión 2):** añadir a `CombatReader` el método `boolean inCombat(UUID entityId)` usado por M06.4.

## Terminado

- [ ] Subpuntos M07.1–M07.8 aceptados.
- [x] `BandsTest` y las pruebas de tendencia y alcance en verde.
- [ ] Cada `TargetCue` y cada fila de eventos con evidencia de servidor.
- [ ] Costo de captura y de sonda medido y registrado para M13.
- [x] `ArchitectureRulesTest` verde: `adapter.server.context` y `adapter.server.events` no importan `adapter.jev`.

## Estado de implementación

> **Local listo — servidor pendiente (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` (también con `--rerun-tasks`) pasa sin avisos de compilación ni de Javadoc: **807 pruebas, 0 fallos** (669 de partida + 135 nuevas + 3 de las correcciones tras la auditoría). Ninguna prueba hace llamadas de red ni lee `.env` (W6); ninguna prueba nueva usa `Thread.sleep` (P2). No se arrancó ningún servidor: las aceptaciones que exigen juego, M09 o M13 siguen sin marcar y no hay evidencia de servidor inventada. 00 no cambia; 01 recibe la revisión 3 (desviación 1). El registro de decisiones de M01 recibe DT-22 a DT-25 (todas `Abierta`) y una nota en DT-21; el guion de M01 recibe la sección 11 (el cierre pasa a ser la 12).

### Archivos

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…`) |
|---|---|
| Puerto y dominio | Creados `engine/CombatReader` (puerto de 00 §4) y `domain/MobSighting`; `domain/CaptureContext` gana `worldId`, `lastUnreachable` y `sightings` |
| M07.1 | Creados en `adapter/server/context/`: `CombatCapture` (implementa el puerto), `FactsAssembler` y `LiveMember` (paquete privado), `ParticipantSelection` (paquete privado), `MemberSource` y `MythicMemberSource` (lecturas de MythicMobs), `EntityReads` y `PaperEntityReads` (lecturas de Paper). Movido `adapter/paper/NearestK` a `adapter/server/context/NearestK`. Creado `engine/EncounterCapture` (contexto de captura, alias, participantes, objetivos, cambio de mundo, hechos y medida) |
| M07.2 | Creado `adapter/server/context/Perception` (paquete privado) |
| M07.3 | Creados `adapter/server/context/TargetCueReader`, `ParticipantReading` y `Bands` |
| M07.4 | Creado `adapter/server/context/TargetRelationReader` (paquete privado); reutiliza `ShieldGeometry` de M05 |
| M07.5 | Creados `adapter/server/events/CombatContextListener` y `CombatOutcomeListener`; `engine/ContextEvents` y `ContextEventMetrics`. `engine/Encounter` gana `recordUnlessRepeated` (paquete privado) y hace público `contextPending()`; `engine/EventHistory` gana `hasRepeat`. `adapter/server/events/ServerThread` gana `runsNow` |
| M07.6 | `CombatCapture.probe` |
| M07.7 | Creados `adapter/server/context/NativeContextEvaluator` y `ShortcutFacts`; `ReadyTactics` (paquete privado) calcula las tácticas elegibles con `Eligibility` |
| M07.8 | Creado `adapter/server/context/CombatSummaryReader`; `CombatCapture.inCombat` |
| Raíz | `SessionServices` construye `ContextEvents`, `EncounterCapture` y los tres listeners, pasa `CombatReader::isActive` a la reconciliación (M06, desviación 3), añade `debugCapture` y los contadores de contexto y captura a `summary()`. `ThePredictPlugin` construye `CombatCapture` con `decision.searchRadiusBlocks` (D10) y registra los listeners. Creado `DebugCapture`; `DebugCommandHandler` y `admin/DebugCommandParser`/`DebugRequest.Capture` añaden `debug capture <mob>`. `adapter/paper/PaperCombatSnapshotReader` importa `NearestK` desde su nuevo paquete (W5) |
| Documentos | Este documento; `README.md` (fila de M07); `01-Modelo-de-dominio.md` (revisión 3); `M01-Registro-de-decisiones.md` (DT-21 a DT-25); `M01-Guion-de-servidor.md` (sección 11) |

### Pruebas (807; 138 nuevas, 3 de ellas de las correcciones tras la auditoría)

- `domain`: `MobSightingTest` (2, nueva: distancia solo con objetivo, distancias negativas o infinitas); `CaptureContextTest` (4, antes 3: componentes nuevos copiados y obligatorios).
- `engine` (reloj controlable, sin `Thread.sleep`): `ContextEventsTest` (15: jugador sin encuentro sin trabajo ni marcas; evento con alias y contexto pendiente; fallo sin contexto pendiente; repetido a 9 ticks descartado y a 10 registrado, otro tipo u otro participante sí; 100 marcas en un tick dan una sola entrada de la agenda; el anillo no pasa de 16 con 40 eventos; entrada cada 10 ticks en 0, 9 y 10; tabla de entradas de 256 que olvida al más antiguo; golpe de un miembro con mob y alias, marca solo si se pide; jugador ajeno al encuentro; mob suspendido; ataque a un miembro y rutas contadas; ayuda a otro participante; un evento llega a todos los encuentros del jugador en ese mundo y a ninguno de otro; dos mobs que golpean al mismo jugador en 10 ticks son dos eventos y el mismo mob dos veces es uno); `EncounterCaptureTest` (12: encuentro desconocido o vacío sin llamar al lector; alias del encuentro e índice; participante no percibido sale del índice y conserva su alias; captura sin hechos no toca participantes; objetivo y distancia guardados y borrados; miembro en otro mundo cambia de generación y sale del encuentro; avistamiento de otra generación sin efecto; contexto con mundo, historial, distancias, permiso y último inalcanzable; hechos guardados en cada sesión; medida con el reloj; objetivo no percibido que sigue indexado y `assign` repetido que conserva el encuentro; jugador nuevo que entra en un índice lleno cuando sale uno obsoleto).
- `adapter.server.context`: `BandsTest` (8: salud en 0,3 y 0,6 exactos y `nextUp`, absorción, umbrales del perfil, salud máxima inválida; distancia en 2,5 y 16 exactos y `nextUp`; la versión al cuadrado coincide en 401 distancias; armadura en 8 y 16, dureza en 4 sin pasar de `HEAVY`); `TargetCueReaderTest` (10: cada señal y sus fronteras 0,9, 20 ticks y 1,5 bloques; `RAISING_SHIELD` y `BLOCKING` nunca juntas; invisible solo `INVISIBLE`; sin visión ninguna); `TargetRelationReaderTest` (11: tendencia por cada combinación de teclas y orientación, frontera de 60°, corrección por distancia en 1 bloque exacto y por encima; ruta en curso sin calcular; `findPath` solo con permiso; alcance con y sin `ATTACK_RANGE`; ángulo de 90° con 89,9°, 90° y 90,1°); `PerceptionTest` (6: radio y tope, `FOLLOW_RANGE` ausente o inválido, superficie de la esfera y esquinas de la caja, modos de juego, visibilidad, línea de visión una vez por par); `ParticipantSelectionTest` (6: 20 jugadores dan los 16 más cercanos, 16 y 15 se conservan, objetivos primero y una vez, objetivos más allá del tope, rangos por amenaza, distancia y orden); `ReadyTacticsTest` (5: especie con `Brain`, sin objetivo, `FLANK` solo flanqueable, cooldown nativo sin preguntar por la choice vanilla, contexto activo); `NativeContextEvaluatorTest` (7: todas las condiciones, contexto sin condiciones, 16 contextos evaluados una vez, excepción que desactiva solo su contexto con registro limitado y sin mensaje, atajos por bandas, casos del `PredictionPlanner` heredado, cada atajo con significado); `CombatSummaryReaderTest` (3); `CombatCaptureTest` (15: hechos con alias y sin identidad del jugador, avistamientos, objetivo ausente, miembro en otro mundo, miembro inactivo, muerto o sin salud máxima válida, 40 miembros leen 32, 16 participantes dentro de la esfera sin espectador, creativo ni muerto, radio con tope, objetivo fuera del radio no percibido, objetivo en el borde primero, objetivo en creativo, sonda sin líneas de visión ni rutas, sonda vacía o sin objetivo, `inCombat`, radio inválido, fuera del hilo del servidor); `FactsAssemblerTest` (14: invisible, muro, líneas de visión por par, rutas, tendencia, flanqueo y frontera de 90°, alcance, rangos de amenaza, escudo desactivado por evento en 20 y 21 ticks, salud del mob en 0,3 y atajo, especie con `Brain`, cooldown, señales del mob y `combatSeconds`); `NearestKTest` (2, movida sin cambios). `CaptureDoubles` y `ContextFixtures` son ayudantes de prueba con proxies de `World`, `LivingEntity`, `Player` e `Input`.
- `adapter.server.events`: `CombatContextListenerTest` (7, eventos reales de Paper con el `engine` real) y `CombatOutcomeListenerTest` (7); `ContextListenerFixtures` es su ayudante.
- Raíz y `admin`: `DebugCaptureTest` (3), `SessionServicesTest` (8, antes 5: reconciliación con `CombatReader.isActive`, captura de depuración, orden de los listeners), `DebugCommandParserTest` (16, antes 15), `ArchitectureRulesTest` (18, antes 16: `adapter.server.context` implementa el puerto dentro de D5 sin alcanzar `adapter.jev`, `config`, `admin` ni `net.kyori`; el puerto y `ContextEvents` quedan libres del adapter y de Paper, D2).

### Evidencia estática nueva (javap; no confirma comportamiento en juego)

- Paper API 26.2: `World.getNearbyPlayers(Location, double)` busca en una caja (`getNearbyEntitiesByType` con los tres radios), así que la captura filtra la esfera. Existen `Player#getCurrentInput()` (`Input`), `HumanEntity#isBlocking`, `getAttackCooldown`, `hasCooldown(ItemStack)`, `getCooldown(Key)`, `LivingEntity#getActiveItem` (nunca nulo), `getActiveItemUsedTime`, `hasLineOfSight`, `isClimbing`, `isGliding`, `getCombatTracker`; `Entity#isInWater`, `isInLava`, `isInvisible`, `getFallDistance`; atributos `FOLLOW_RANGE`, `MAX_HEALTH`, `ARMOR`, `ARMOR_TOUGHNESS` y `ENTITY_INTERACTION_RANGE`; componentes `BLOCKS_ATTACKS`, `CONSUMABLE`, `DEATH_PROTECTION`, `CHARGED_PROJECTILES` (`projectiles()`), `KINETIC_WEAPON`, `PIERCING_WEAPON`, `ATTACK_RANGE` (`maxReach()`) y `POTION_CONTENTS`; `Pathfinder#hasPath`, `getCurrentPath`, `findPath(Entity)` y `PathResult#canReachFinalPoint`; `DamageTypeTagKeys.IS_PROJECTILE`, `IS_PLAYER_ATTACK`, `IS_EXPLOSION` e `IS_FIRE`; `Registry#getTag`, `RegistryKeySet#contains(TypedKey)` y `TypedKey.create(RegistryKey, Key)`; `PotionEffectType#getCategory()` (`PotionEffectTypeCategory`, no obsoleto). Los 18 eventos de la tabla de M07.5 existen; `EntityLoadCrossbowEvent` está en `io.papermc.paper.event.entity` (no en `com.destroystokyo`), `PotionSplashEvent` tiene su propia `HandlerList`.
- Servidor Paper 26.2 parcheado localmente (sin arrancar): `CombatTracker.getCombatDuration` en ticks y `recheckStatus` con 300 ticks en combate (DT-25); `isInvisible` y el efecto (DT-22); `getCooldown(Material)` construye un `ItemStack` y el grupo de cooldown (DT-23).
- MythicMobs 5.13.1: `SkillCondition.evaluateEntity` despacha `IEntityCondition`, `ILocationCondition` e `ICasterCondition` y captura sus errores; `HealthCondition` es `IEntityCondition` (DT-24). `ActiveMob.ThreatTable.getThreatTable()` devuelve el mapa vivo, `inCombat()` es `!threatTable.isEmpty()`; `ActiveMob` es `SkillCaster`, el parámetro de `Skill.onCooldown`.

### Desviaciones y decisiones

| # | Desviación o decisión | Motivo |
|---|---|---|
| 1 | **01, revisión 3:** `CaptureContext` gana `UUID worldId`, `Set<MobKey> lastUnreachable` y `Consumer<MobSighting> sightings`, y se crea `domain.MobSighting(mob, worldId, targetId, targetDistanceBlocks)`. 00 no cambia (la firma del puerto es la misma) | Sin ellos el adapter no puede excluir un miembro de otro mundo (la alternativa de DT-21 que pide M06), "conservar el último valor conocido por `engine`" de la alcanzabilidad (M07.4) ni devolver el objetivo y su distancia que `MobSession.recordTarget` guarda (M06) sin estado propio (A3) ni poner UUID o distancias en `CombatFacts` (I6) |
| 2 | Tipos de `engine` no previstos en el documento: `EncounterCapture`, `ContextEvents` y `ContextEventMetrics` (añadidos a 01 §8) | `CaptureContext` lo arma `engine` (01 §4) y alguien debe aplicar lo visto; los listeners no pueden tocar `Encounter` ni `Agenda` sin un punto de entrada en `engine` (D5, A3). Las métricas salen para C10 |
| 3 | Tipos de adapter no previstos: `EntityReads`/`PaperEntityReads`, `MemberSource`/`MythicMemberSource`, `FactsAssembler`, `LiveMember`, `ParticipantReading`, `ParticipantSelection`, `ReadyTactics` y `ShortcutFacts` | P4: la captura se prueba con dobles porque las lecturas de atributos, componentes, rutas y MythicMobs quedan detrás de dos interfaces pequeñas; las reglas son funciones puras sobre valores leídos (M07.1 lo pide). C10 |
| 4 | Un listener pasa a dos: `CombatContextListener` (lo que hace el jugador) y `CombatOutcomeListener` (golpes, proyectiles, pociones y rutas, donde M11 añadirá sus pesos) | C10: 18 manejadores con Javadoc no caben en una clase de unas 200 líneas |
| 5 | La captura calcula `MobFacts.eligibleTactics` con `Eligibility`: choices listas = objetivo si lo exigen y skill fuera de su cooldown nativo (`Skill.onCooldown`); movimientos = solo `NONE` para especies con `Brain`, `HOLD` y `NONE` sin objetivo, y `FLANK` solo si la relación es flanqueable (M10.8). Las condiciones propias de la skill se revalidan al lanzar (M10.3) | El documento no dice quién rellena el campo, pero M09.8 pregunta solo por miembros con dos tácticas elegibles y M10.1 usa "las `eligibleTactics` de la captura"; sin objetivo, los demás movimientos no tienen referencia |
| 6 | Objetivo: se conserva la búsqueda heredada (tabla de amenaza y luego `Mob#getTarget()`, jugador válido del mismo mundo) y además debe estar en `SURVIVAL` o `ADVENTURE`. Un objetivo dentro del radio de su mob entra primero en `participants`; uno fuera de todos los radios no se percibe: el mob queda sin `TargetRelation` en `CombatFacts`, pero el avistamiento lleva el objetivo a `engine` (`MobSession.lastTargetId`) y ese jugador **sigue en el índice de participantes del encuentro** (desviación 17) | `CombatFacts` exige que cada objetivo sea participante y M07.2 que ningún jugador fuera del radio aparezca en `participants`; el índice, en cambio, es de `engine` y debe contener el objetivo para que `EncounterRegistry.assign` conserve el encuentro. Consecuencia para M09/M10: con un objetivo real no percibido, `hasTarget` es falso en la captura, así que solo quedan `HOLD` y `NONE` y se activa `idle` aunque el mob siga teniendo objetivo |
| 7 | La banda de salud de los jugadores usa los umbrales del perfil del primer miembro | "Los mismos umbrales valen para el mob" y un participante tiene una sola banda por captura; los perfiles de un encuentro suelen coincidir |
| 8 | Tendencia: "mirar al mob" es un ángulo menor de 60° con `ShieldGeometry.angleFromFacingDegrees` (equivale a producto escalar mayor que 0,5); adelante y atrás a la vez es `STEADY`; adelante o atrás sin mirar al mob es `STEADY` hasta que la distancia diga otra cosa; la corrección exige una variación estrictamente mayor que 1 bloque | Reutiliza `ShieldGeometry` (pendiente de M05) y concreta los casos que el documento no fija; M13 calibra |
| 9 | `reachable` lee la ruta en curso del navegador con `PaperEntityReads.currentPath` (la misma lectura nativa que `MovementReading` de M05) en lugar de llamar a `MobControl.progress` | Un adapter no debe llamar a otro puerto; el resultado es el mismo dato nativo sin una búsqueda más del mob |
| 10 | `outsideTargetReach` compara la distancia entre posiciones con el alcance del jugador | Vanilla mide desde el ojo hasta la caja de colisión; M13 calibra si el margen importa |
| 11 | `SHIELD_DISABLED`: cooldown del ítem con `BLOCKS_ATTACKS` en cualquiera de las manos, o un evento `SHIELD_DISABLED` del participante en los últimos 20 ticks (`FactsAssembler.SHIELD_DISABLE_EVENT_TICKS`) | El evento se emite antes de que el cooldown se aplique; la ventana corta evita arrastrar un evento viejo del anillo |
| 12 | `MACE_DIVE` sale solo de `getFallDistance() > 1.5` con una maza en la mano principal | `EntityAttemptSmashAttackEvent` solo marca contexto (tabla de M07.5) y `EventType` no tiene una entrada para registrarlo |
| 13 | `PEARL_ON_COOLDOWN` lee `getCooldown(minecraft:ender_pearl)` por grupo | Evita un `ItemStack` por llamada y es el mismo grupo por defecto (DT-23; alternativa `getCooldown(Material)`) |
| 14 | `POTION_CONSUMED` exige el componente `POTION_CONTENTS`; `RANGED_MISS` solo cuenta `AbstractArrow` (flechas y tridentes); `ALLY_ASSISTED` exige un efecto de categoría `BENEFICIAL` y otro participante del mismo encuentro entre los afectados; `PrePlayerAttackEntityEvent` marca el encuentro del miembro atacado aunque el atacante aún no sea participante | Clasificación nativa sin listas de `Material` (A1); una perla o una bola de nieve no son un disparo fallido; una poción dañina no es ayuda; atacar a un miembro es relevante aunque el jugador acabe de llegar |
| 15 | Repetidos: mismo tipo, mismo participante y mismo mob (ausente en los eventos sin mob) con `tick` mayor que `tick − 10`; un repetido se cuenta y sí marca contexto pendiente (la agenda lo condensa) | Dos mobs que golpean al mismo jugador son dos hechos distintos para Jev; un mismo evento repetido no. "Un evento igual del mismo participante dentro de 10 ticks se descarta" solo habla del historial |
| 16 | La marca por entrada recuerda como mucho 256 jugadores y olvida al más antiguo | A5; olvidar produce como mucho una marca de más, que la agenda condensa |
| 17 | Tras cada captura con hechos, `EncounterCapture` saca del índice (`removeParticipant`, conserva su alias) a cada participante que no se percibió y que no es el objetivo actual (`lastTargetId`) de ningún miembro, y después añade los objetivos de los miembros y los jugadores percibidos, en ese orden. El alias se asigna en `aliasFor` y la entrada al índice ocurre en ese paso | El índice refleja la percepción sin perder el objetivo: los eventos de un jugador que se alejó no cuestan nada, el tope de 16 no se agota con jugadores obsoletos, un jugador recién percibido entra aunque el índice estuviera lleno y `assign` no rehace el encuentro de un objetivo lejano. Si no caben todos, entre los que aún no están indexados los objetivos entran antes que los percibidos nuevos; un objetivo nuevo no desplaza a percibidos ya indexados y entra cuando se libera un hueco (inocuo: `assign` de M09 lleva al miembro a un encuentro con su objetivo) |
| 18 | Los cuatro métodos de `CombatCapture` lanzan `IllegalStateException` fuera del hilo del servidor (`Bukkit::isPrimaryThread` inyectado) | Hace comprobable T1, como `GoalOwnership` en M05 (desviación 12); es un error de programación (C7) |
| 19 | La participación de cada jugador (`seen`) usa la línea de visión desde el miembro más cercano; la relación usa la del propio mob. Como mucho una línea de visión por par y captura, solo para jugadores elegidos | M07.2; acota el coste a unos 48 rayos con 32 mobs y 16 jugadores |
| 20 | Contextos nativos con `SkillCondition.evaluateEntity(BukkitAdapter.adapt(mob))` | Sin construir `SkillMetadata`; `healthpercent{p=<30%}` es `IEntityCondition` (DT-24). Las condiciones solo de metadatos quedan falsas; M14 lo documenta |
| 21 | `/thepredict debug capture <mob>` (`DebugCapture`, `SessionServices.debugCapture`): une la sesión al encuentro de su objetivo con `assign(…, miembro -> true)`, captura con permiso de ruta solo para ese mob y llama a `Agenda.decided`, que limpia el contexto pendiente | Sin el coordinador de M09 nada forma encuentros ni captura, y las aceptaciones de servidor no podrían observarse (guion 11). Solo muestra alias, bandas y señales. M12 lo retira |
| 22 | La línea `Sessions` de `/thepredict status` termina con `context events recorded …, repeated …, pending marks …, member paths …; captures N (last … us, max … us)` | Evidencia de M07.5 y del coste de captura para el guion; los campos nuevos van al final y las líneas existentes no cambian (W5) |
| 23 | `NearestK` pasa a público y su bucle se divide en un método auxiliar sin cambiar el comportamiento | `PaperCombatSnapshotReader` (heredado hasta M09, W5) la sigue usando desde otro paquete; el método pasaba de 30 líneas (C10). `NearestKTest` pasa sin cambios |
| 24 | La reconciliación recibe `CombatReader::isActive`; `MythicMobDirectory` sigue sirviendo al escaneo (`MobScan`) | M06, desviación 3; `closeIfFinished(…, combatReader::inCombat)` lo llamará el latido de M09 |
| 25 | Tamaño de clases: `CombatContextListener` 236, `ContextEvents` 234, `FactsAssembler` 222 y `SessionServices` 205 líneas; `Encounter` pasa de 251 a 270, `ThePredictPlugin` de 364 a 385 y `DebugCommandHandler` de 283 a 287 | Las tres primeras son sobre todo Javadoc de manejadores y operaciones públicas (C9); ningún método pasa de 30 líneas. `Encounter` es el contenedor de 01 §8; la raíz y el comando son heredados y M12 los reduce |
| 26 | Una sonda asigna `MobProbe`, sus `Optional` y dos `Location` | La firma del puerto devuelve un record; no hay líneas de visión, rutas, registro ni streams (C12). M13 mide el caso de 100 mobs |

### Pendiente de verificación en servidor

- Guion de M01, sección 11 (pasos 11.1 a 11.11), con dos jugadores donde se indica.
- DT-22 (invisibilidad), DT-23 (grupo de la perla), DT-24 (`evaluateEntity` con `healthpercent{p=<30%}`), DT-25 (ticks de `getCombatDuration` y etiquetas de daño) y la alternativa de DT-21.
- Aceptaciones sin marcar: escenarios de servidor de cada señal (M07.3), coste con 16 jugadores (M07.1) y de la sonda con 100 mobs (M07.6), que registra M13.

### Pendiente para módulos posteriores

- M08: convertir `CombatFacts` en texto con `ParticipantAlias.text()` y las bandas y señales; ningún `MobKey` ni UUID en el estado enviado. Fuera de combate, `combatSeconds` es la duración del último combate (semántica vanilla de `getCombatDuration`: `combatEndTime - combatStartTime`), no cero.
- M09 (hecho, ver el estado de implementación de M09): `DecisionDispatcher` llama a `EncounterCapture.capture(id, permitidos)` con un conjunto de rutas acotado por tick; `closeIfFinished(id, combatReader::inCombat)` en cada latido; `assign` al cambiar de objetivo con un predicado de alcance de seguimiento, para el que `CombatReader` no da posiciones: es una decisión de puerto de M09 (anotada en su documento); `dueProbes` → `probe`; trasladar las métricas de captura a `DecisionMetrics`; retirar el uso de `Agenda.decided` en `DebugCapture`, `PaperCombatSnapshotReader` y `adapter.paper`. Corrección: `snapshot` **no** se retiró en M09; pasa a M12 porque el proveedor OpenRouter aún lo usa (W5, desviación 21 de M09).
- M09 y M10: un objetivo real que la captura no percibe (más allá de todo radio de percepción) da `hasTarget` falso: solo `HOLD` y `NONE` son elegibles y se activa `idle`, aunque `MobSession.lastTargetId` siga presente. Decidir si se acepta (percepción natural) o si el latido debe esperar a percibirlo (anotado en M09). Cerrado en M10 (desviación 14): sin objetivo percibido no hay táctica local y la IA base conserva el control.
- M10 (hecho): usar `lastMobFacts().eligibleTactics()` como elegibilidad vigente; revalidar condiciones de la skill al lanzar; ángulo de `FLANK` con `ShieldGeometry`.
- M11 (hecho, ver el estado de implementación de M11): pesos de amenaza en `CombatOutcomeListener`, en el mismo recorrido. `CombatSummaryReader.damageTotals` no se usa: el daño de cada golpe es `getFinalDamage()` (desviación 3 de M11).
- M12: presentar los contadores de contexto y captura; retirar `debug capture`. **Hecho en M12** (ver su [estado de implementación](M12-Diagnostico-instalacion-retiro-OpenRouter.md#estado-de-implementación)).
- M13: calibrar bandas, los 60° de orientación, el bloque de variación, los 20 ticks del escudo desactivado y `MELEE_BLOCKED`, que sale de `isBlocking()` del jugador: un golpe por la espalda con el escudo levantado cuenta como bloqueado aunque vanilla no lo reduzca; medir la captura con 16 jugadores, las líneas de visión y las sondas con 100 mobs.
- M14: guía de autores: los contextos se evalúan con `evaluateEntity` (DT-24) y los atajos `idle`, `attacking_phase`, `tracking_enemy` y `low_health_phase`.

### Correcciones tras la auditoría (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **807 pruebas, 0 fallos** (3 nuevas). 00 no cambia; 01 no cambia en esta ronda.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** `EncounterCapture.keep` sacaba del índice a todo participante sin `aliasFor`, incluido el objetivo de un miembro más allá de todo radio de percepción (`FollowRange` mayor que `searchRadiusBlocks`, arquero más allá del `FollowRange`); el siguiente `assign` no lo encontraba, cerraba el encuentro (con historial y alias) y formaba otro en cada latido | Un participante no percibido que es el objetivo actual (`lastTargetId`, ya actualizado por los avistamientos) de un miembro no sale del índice, y los objetivos se vuelven a añadir tras la limpieza (desviaciones 6 y 17) | `EncounterCaptureTest.anUnperceivedTargetStaysIndexedAndRepeatedAssignKeepsTheEncounter` (tres latidos con `assign`: mismo encuentro, uno solo abierto) |
| **Bloqueante:** `aliasFor` ignoraba el `false` de `addParticipant`; con el índice lleno de participantes obsoletos, un jugador recién percibido tenía alias y aparecía en `CombatFacts` pero no en el índice hasta la captura siguiente, y sus eventos se perdían | `aliasFor` solo asigna el alias; `keep` quita primero los obsoletos y después añade objetivos y percibidos | `EncounterCaptureTest.aNewlyPerceivedPlayerJoinsAFullIndexWhenAStaleParticipantLeaves` (16 participantes y un cambio) |
| El filtro de repetidos ignoraba el mob: dos mobs que golpean a `player_1` en 10 ticks daban un solo `MELEE_HIT` | `EventHistory.hasRepeat` compara también el mob (desviación 15) | `ContextEventsTest.twoMobsHittingOnePlayerWithinTenTicksAreTwoEventsButOneMobTwiceIsOne` |
| Guion 11.6: el desgaste de la armadura dispara `EntityEquipmentChangedEvent` y daría `contextPendingBefore=true` tras el golpe sin escudo | El paso exige a A sin armadura | — (guion) |
| Guion 11.11 necesita tres jugadores | Marcado como paso que exige cuentas adicionales, igual que la preparación | — (guion) |
| C5: `RECHARGED_ATTACK`, `ParticipantReading.attackCooldown` y `fallDistance` sin unidad | Renombrados a `RECHARGED_ATTACK_RATIO`, `attackCooldownRatio` y `fallDistanceBlocks` | Suite completa |
| Faltaban notas para módulos posteriores | Objetivo existente no percibido (`hasTarget` falso, solo `HOLD`/`NONE`, `idle` activo) y predicado de alcance de `assign` como decisión de puerto, en "Pendiente para módulos posteriores" y en "Riesgos y decisiones abiertas" de M09; `combatSeconds` fuera de combate para M08; `MELEE_BLOCKED` por `isBlocking()` para M13 | — |

Nota de implementación M26 (2026-10-07, decisión 7, errata E26-1 aplicada). Las líneas 113 y 214 de este documento describen `getCurrentInput`. Esa lectura se retiró: `TargetTrend` del jugador usa la palabra cinemática y, si el bit de validez está apagado, queda `STEADY`. La escucha `onInput` de la línea 148 se eliminó. La prueba de paridad de bandas está en el simulador; la sesión 26.1 sigue pendiente.

