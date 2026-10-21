# M17. Participantes y objetivos como entidades

> **Nota de M27 (2026-10-07):** el alta de un participante jugador es el sitio del permiso `thepredict.memory.exempt`.

> **Estado:** Local listo — servidor pendiente (sesión 17; DT-36..39) · **Entrega:** F (extensión de la 0.0.1) · **Depende de:** M15, M16 (orden, ver abajo); base: M06, M07, M10, M11 · **Desbloquea:** M18 · **Diseño:** [plan §3](../Plan-Jev-y-combate-tactico.md#regla-de-reinvención-y-responsabilidades-nativas), [plan §5, "Objetivo y amenaza enriquecida"](../Plan-Jev-y-combate-tactico.md#objetivo-y-amenaza-enriquecida), [plan §6, "Percepción natural"](../Plan-Jev-y-combate-tactico.md#percepción-natural) · **Reglas:** [00](00-Arquitectura-y-reglas.md) · **Dominio:** [01](01-Modelo-de-dominio.md)

## Objetivo

Generalizar participantes y objetivos de "jugador en supervivencia o aventura" a **cualquier `LivingEntity`**, con el jugador como un tipo de entidad más. Qué entidades pueden participar lo decide una política pequeña y configurable construida **solo con distinciones nativas** de Minecraft, Paper y MythicMobs (tipo de entidad, etiquetas de tipos extensibles por datapack, categoría de aparición, interfaces `Enemy`/`Monster`/`Animals`/`Mob`, instancia y facción de MythicMobs, equipo de scoreboard, etiquetas de `/tag` y modo de juego), no con una lista cerrada propia.

Los **defaults reproducen exactamente la 0.0.1**: solo jugadores en `SURVIVAL` o `ADVENTURE`, mismas solicitudes byte a byte. Un duelo mob contra mob (M18) es solo un perfil o una arena que permite entidades.

## Alcance

### Incluye

- Dominio: tipo de participante, alias por tipo (`player_N`, `entity_N`), especie de la entidad y `CaptureContext` con tipo.
- Política de participantes global (`config.yml`) y por perfil, con selectores nativos, bandos (facción de MythicMobs o equipo de scoreboard) y exclusiones fijas.
- Percepción, selección, lecturas y señales para cualquier `LivingEntity`, con la matriz de qué hecho existe para qué tipo.
- Objetivo del mob, sondas, encuentros por bando, ejecución, amenaza y eventos generalizados.
- Codificador: entradas de entidad con su especie; nada cambia si no hay entidades.
- Inventario de cada supuesto "jugador" del código actual y su cambio.

### No incluye

- Arena, paquete de duelo, matriz de duelos, registrador y simulador: M18.
- Elección del objetivo por Jev (sigue siendo nativa: tabla de amenaza y selectores de MythicMobs, plan §5).
- Eventos nuevos de tipo "un participante golpeó al mob cuerpo a cuerpo" (cambiarían el comportamiento con jugadores; la medida de duelo sale de `OutcomeWindow`, M16.8).
- Entidades no vivas (armor stands, marcadores, vehículos) como participantes.
- Folia (T8).

### Orden respecto a M15 y M16

M17 usa el alcance declarado de M15 para saber si un rival controlado por ThePredict "llega" al mob (M17.4). Comparte con M16 `CombatOutcomeListener` (M16.8 añade daño y muertes; M17 generaliza sus víctimas y tiradores) y el estado de Jev (M16.4 ya renombra `players` a `participants`, así que M17 no cambia el cuerpo de encuentros solo con jugadores). Por eso va **después de M16.8**; puede avanzar en paralelo a M16.9–M16.11 (banco, re-banco y sesión de servidor), que no tocan los adapters de servidor. Su **primer paso** es M17.0: grabar las referencias doradas con el código de M16 cerrado, antes de cualquier cambio.

## Base existente

| Archivo | Acción |
|---|---|
| `domain/ParticipantAlias.java`, `ParticipantFacts.java`, `CaptureContext.java` | Tipo de participante, alias por tipo, especie (M17.1) |
| `engine/ParticipantAliases.java`, `Encounter.java`, `EncounterRegistry.java`, `ParticipantIndex.java`, `EncounterCapture.java`, `ContextEvents.java`, `ThreatEnrichment.java`, `ThreatChanges.java` | Nombres (`playerId` → `participantId`), alias por tipo, regla de bandos, baja por muerte (M17.1, M17.6, M17.8) |
| `config/PluginSettings.java`, `DecisionSettings.java`, `ProfileSchema.java`, `ProfileSectionParser.java`; `src/main/resources/config.yml` | Sección `participants` y `Participants` del perfil (M17.2) |
| `adapter/server/context/*` (ver inventario) | Política, percepción, lecturas, relación y señales (M17.2–M17.5) |
| `adapter/server/execution/*` | Objetivo y cast sobre `LivingEntity`, escudo genérico, amenaza (M17.7) |
| `adapter/server/events/CombatContextListener.java`, `CombatOutcomeListener.java` | Eventos con víctimas y tiradores de cualquier tipo (M17.8) |
| `adapter/jev/SemanticEncoder.java`, `SemanticPhrases.java` | Especie y recuento de criaturas (M17.9) |
| `ServerComponents.java` | Búsqueda de entidad por UUID en lugar de jugador (M17.7) |

## Evidencia nativa

`javap` sobre `paper-api-26.2.build.129-stable.jar` (caché de Gradle) y `VersionsOfJars/MythicMobsPremium-5.13.1-SNAPSHOT.jar`, sin servidor:

| Distinción | API nativa | Uso en M17 |
|---|---|---|
| Tipo de entidad | `Entity#getType(): EntityType` (`getKey()`, `getEntityClass()`, `isAlive()`) | Selector `type:`; especie para Jev (`getKey().getKey()`) |
| Etiquetas de tipos, extensibles por datapack | `Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, NamespacedKey, EntityType.class)`; constantes `Tag.ENTITY_TYPES_UNDEAD`, `…_SKELETONS`, `…_RAIDERS`, `…_ILLAGER`, `…_ZOMBIES`… (`isTagged`); `io.papermc.paper.registry.keys.tags.EntityTypeTagKeys` | Selector `tag:` (vanilla y de datapack) |
| Categoría de aparición | `Entity#getSpawnCategory(): SpawnCategory` (`MONSTER`, `ANIMAL`, `WATER_ANIMAL`, `WATER_AMBIENT`, `WATER_UNDERGROUND_CREATURE`, `AMBIENT`, `AXOLOTL`, `MISC`) | Selector `category:` |
| Hostil, monstruo, animal, mob | Interfaces `org.bukkit.entity.Enemy extends LivingEntity`, `Monster extends Creature, Enemy`, `Animals extends Breedable`, `Mob` | Selector `kind:` (`ENEMY` es más amplio que `MONSTER`: incluye slime, ghast, phantom, hoglin, shulker) |
| Instancia y tipo de MythicMobs | `MobExecutor.getActiveMob(UUID)`, `ActiveMob.getType().getInternalName()` | Selector `mythic:` |
| Facción de MythicMobs | `ActiveMob.getFaction()`, `hasFaction()`, `setFaction(String)`; `MythicMob.getFaction()`; mecánica `setfaction`; condiciones `faction` y `samefaction`; selectores de IA `nearestOtherFaction` (alias `otherFaction`), `nearestSpecificFaction` (alias `specificFaction`), `…Monsters` | Selector `faction:` y bandos (M17.2) |
| Equipo de scoreboard | `Scoreboard#getEntityTeam(Entity)`, `getEntryTeam(String)` (`/team` vanilla, jugadores y entidades) | Selector `team:` y bandos |
| Marcas del autor | `Entity#getScoreboardTags()` (`/tag` vanilla) | Selector `tagged:`; hace innecesario un marcador PDC propio |
| Modo de juego | `Player#getGameMode()` | Lista de modos para jugadores |
| Vecinos vivos | `World#getNearbyLivingEntities(Location, double, Predicate)`; `getNearbyPlayers` (hoy) | Percepción de no objetivos, opcional (M17.3) |
| Entidad por UUID | `Bukkit.getEntity(UUID)` | Amenaza y cast (M17.7) |
| Objetivo de un mob | `Mob#getTarget(): LivingEntity`; `ActiveMob.ThreatTable.getTopThreatHolder(): AbstractEntity`, `threatGain/threatLoss(AbstractEntity, double)` | Objetivo y amenaza de cualquier tipo (M17.5, M17.7) |
| Cast contra una entidad | `BukkitAdapter.adapt(org.bukkit.entity.Entity): AbstractEntity` | `MythicSkillExecutor.executeAt` (M17.7) |
| Ruta hacia una entidad | `Pathfinder#findPath(LivingEntity)` | Alcanzable a pie (M17.4) |
| Escudo de un no jugador | `LivingEntity#hasActiveItem()`, `getActiveItem()`, `getActiveItemUsedTime()`; componente `BLOCKS_ATTACKS` con `blockDelaySeconds()` (`float`, en segundos; 0 = bloquea al instante) y `damageReductions()`; `HumanEntity#isBlocking()` solo en humanos. Fuentes de `paper-api` 26.2: `isHandRaised()` y `getHandRaisedTime()` están marcados `@ApiStatus.Obsolete(since = "1.20.4")`; `getShieldBlockingDelay()` está `@Deprecated(since = "1.21.9")` y devuelve 5 fijo. No se usa ninguno de los tres. `ShieldGeometry` hoy solo lee `damageReductions()` (el ángulo) | Escudo genérico (M17.4) |
| Movimiento de un no jugador | `Entity#getVelocity()` (en jugadores no refleja el paso, plan §6; en mobs lo mueve el servidor) | Tendencia (M17.4, DT-36) |

## Inventario de supuestos "jugador" en el código actual

`grep -rn -E '\bPlayer\b|getPlayer|GameMode|getNearbyPlayers|HumanEntity|playerId|ParticipantAlias|player_' src/main/java` (2026-09-29). Cada fila dice cómo cambia; "nombre" significa solo renombrar identificadores y Javadoc, sin cambio de comportamiento.

| Archivo:línea | Supuesto | Cambio (subpunto) |
|---|---|---|
| `domain/ParticipantAlias.java` | Solo `player_` + índice | `ParticipantAlias(ParticipantKind kind, int index)`; texto `player_N` o `entity_N` (M17.1) |
| `domain/ParticipantFacts.java:7-12`, `CombatFacts.java:20`, `EncounterEvent.java:11`, `CaptureContext.java:16` (Javadoc) | "player" | Nombre; `ParticipantFacts` gana `Optional<String> species` (M17.1). `TargetRelation` no nombra jugadores: solo cambia por el tipo de `ParticipantAlias` |
| `domain/CaptureContext.java` | `Function<UUID, ParticipantAlias> aliasFor` de un jugador | `BiFunction<UUID, ParticipantKind, ParticipantAlias>` (M17.1) |
| `engine/ParticipantAliases.java` | Un contador `nextIndex` | Un contador por tipo; capacidad 32 compartida (M17.1) |
| `engine/Encounter.java:109-121, 236-242`, `EncounterRegistry.java:137-178, 242`, `EncounterCapture.java:101-140` | `playerId` | Nombre (`participantId`); `aliasFor` con tipo (M17.1, M17.6) |
| `engine/EncounterRegistry.assign` | Cualquier encuentro del objetivo con miembro en alcance | Nunca se une a un encuentro que lo lista como participante; el predicado excluye rivales (M17.6) |
| `engine/ContextEvents.java:15-184` | `isParticipant(world, playerId)`, `participantChanged`, tabla de input de 256 jugadores | Nombre; baja por muerte `participantGone` (M17.8); la tabla de input sigue siendo solo de jugadores |
| `engine/ThreatEnrichment.java:22-181`, `ThreatChanges.java:52` | `playerId`, texto "a player without alias" | Nombre; "a participant without alias" (M17.7) |
| `engine/CombatActions.java:41-49` (`addThreat(MobKey, UUID, double)`) | UUID de jugador en el Javadoc | Nombre: UUID de una entidad participante (M17.7) |
| `adapter/server/context/Perception.java:52-54` | `eligible(Player)` con `SURVIVAL`/`ADVENTURE` | `ParticipantPolicy.admits(LivingEntity, …)` (M17.2) |
| `adapter/server/context/Perception.java:75` | `lineOfSight(LivingEntity, Player)` | `LivingEntity` (M17.3) |
| `adapter/server/context/FactsAssembler.java:55, 111, 121, 161` | `Map<UUID, Player>`, `getNearbyPlayers`, `offer(Player…)`, `relation(…, Player)` | `LivingEntity`; objetivos primero; vecinos con `getNearbyPlayers` o, si la política lo pide, `getNearbyLivingEntities` (M17.3) |
| `adapter/server/context/LiveMember.java:22` | `Optional<Player> target` | `Optional<LivingEntity>` (M17.5) |
| `adapter/server/context/MemberSource.java:52`, `MythicMemberSource.java:51-63, 124` | Objetivo solo si es `Player` | Cualquier `LivingEntity` admitida por la política (M17.5) |
| `adapter/server/context/CombatCapture.java:110-117` (`probe`) y `:171` (`resolve`) | `members.target(…).filter(Perception::eligible)`: solo objetivos jugadores | `ParticipantPolicy.admits` (M17.2, M17.5) |
| `adapter/server/context/CombatCapture.java:173` | `Player::getUniqueId` | `Entity::getUniqueId` (M17.5) |
| `adapter/server/context/EntityReads.java:46-80`, `PaperEntityReads.java:45-109` | `participant(Player)`, `reachBlocks(Player)`, `raisedShieldAngleDegrees(Player)`, `findPathReaches(LivingEntity, Player)`, `shieldOnCooldown(Player…)` | `LivingEntity`; campos solo de jugador con valor neutro; alcance `OptionalDouble` (M17.4) |
| `adapter/server/context/TargetRelationReader.java:37` | `Player target`, `getCurrentInput()` | `LivingEntity`; input solo si es jugador; velocidad si no (M17.4) |
| `adapter/server/context/ShieldGeometry.java:118` | `raisedShieldAngleDegrees` solo para `HumanEntity` con `isHandRaised` | Cualquier `LivingEntity` con `hasActiveItem()` y `BLOCKS_ATTACKS` en `getActiveItem()` (M17.4) |
| `adapter/server/execution/CastSource.java:31-97`, `MythicCastSource.java:50-87`, `MythicSkillExecutor.java:78-109` | `Player` en objetivo, condiciones, ejecución y amenaza | `LivingEntity`; `BukkitAdapter.adapt(Entity)` (M17.7) |
| `adapter/server/execution/CombatActionsAdapter.java:51, 67, 81, 151, 160, 167, 190` | `players` por UUID, `HumanEntity#isBlocking` | `entities` por UUID; escudo genérico (M17.7) |
| `ServerComponents.java:117` | `Bukkit.getPlayer(entityId)` | `Bukkit.getEntity(entityId)` filtrado a `LivingEntity` (M17.7) |
| `adapter/server/events/CombatOutcomeListener.java:75-213` | Víctima `Player`, tirador `Player`, fuentes de pociones `Player`, `players(affected)` | Víctimas, tiradores y fuentes `LivingEntity` participantes (M17.8) |
| `adapter/server/events/CombatContextListener.java:79, 121, 133, 182, 194` | `event.getEntity() instanceof Player` en eventos de entidad | Cualquier `LivingEntity` participante; los eventos `Player*` siguen siendo de jugador (M17.8) |
| `adapter/server/events/CombatContextListener.java:217` (`changed`) y `:227` (`recorded`) | Auxiliares con parámetro `Player` | `LivingEntity` (los llaman también los eventos de entidad generalizados) (M17.8) |
| `adapter/jev/SemanticEncoder.java:25, 98-106, 154-168`, `SemanticPhrases.java:18, 85-95, 182-183` | Recuento "players", fallback "a player", parámetro `player` de `event` | Especie de entidades y recuento de criaturas; idéntico sin entidades (M17.9) |
| `adapter/jev/StateDraft.java:40` (`players`) | Arreglo `players` | Ya cubierto por M16.4 (`participants`); M17 no lo toca |
| `NoCombatActions.java:24` (Javadoc) | "player" | Nombre |
| `engine/ParticipantIndex.java:10-46` | `playerId` | Nombre |
| `adapter/server/context/ParticipantReading.java:4` (Javadoc) | Lectura de un jugador | Gana `ParticipantKind kind` (M17.4) |
| `admin/ThePredictCommand.java:109, 155-167`, `ThePredictPlugin.java:105-106` | El emisor del comando o quien entra al servidor es un jugador | **Sin cambio**: no son participantes |

## Subpuntos

### M17.0 Referencias doradas antes de tocar código (primer paso obligatorio)

- **Propósito:** poder **demostrar** que los defaults no cambian. Las pruebas de carga y A/B de M13 usan dobles de `CombatReader`, así que por sí solas no ven la captura real ni los listeners, y hoy no hay una referencia guardada con la que comparar.
- **Diseño:** antes de modificar una sola línea de `src/main` en M17, con el código de M16 cerrado:
  1. **Cuerpos de solicitud:** los 60 escenarios del banco de M16 codificados con los tres niveles de detalle → `src/test/resources/golden/m17/bank-<nivel>/*.json` (texto exacto).
  2. **Motor:** la carga de M13 (1, 10 y 30 mobs, semilla fija) y el A/B local (`TacticalAB`, 5 escenarios × 10 semillas) → `golden/m17/engine/*.txt` con: el **SHA-256** de cada cuerpo de solicitud (en orden) y los cuerpos completos solo de las 20 primeras solicitudes de cada escenario; un **registro solo de cambios** de las `IntentView` publicadas (tick y vista únicamente cuando cambia la de un mob, no una línea por tick: 30 mobs × 12 000 ticks serían 360 000 líneas); y al final los contadores de `DecisionMetrics`, `ThreatMetrics`, `ContextEventMetrics` y `SessionMetrics`. **Los SHA-256 son de cuerpos reales**, codificados con `RequestEncoder` (el cliente de System One sin red), no de los cuerpos de relleno (`"x"` repetida) que devuelve `PreparedFixtures` en los dobles del motor: con esos cuerpos la referencia no vería ningún cambio del estado ni de las preguntas. Objetivo de tamaño: menos de 2 MB en total en el repositorio para todas las referencias de M17.0.
  3. **Adapter con dobles de `Player`:** escenarios nuevos (antes de M17, con el código de hoy) que pasan por `CombatCapture` real con dobles de Paper y MythicMobs de `CaptureDoubles` (mundo que devuelve jugadores por `getNearbyPlayers`, un zombi cercano que **no** debe entrar, jugadores en los cuatro modos de juego, objetivo por tabla de amenaza y por `Mob#getTarget`): se guardan los `CombatFacts`, los `MobSighting`, las llamadas a `getNearbyPlayers` y a `hasLineOfSight` (con su orden) → `golden/m17/capture/*.txt`.
  4. **Listeners:** secuencias de eventos de `CombatOutcomeListener` y `CombatContextListener` (golpe, golpe bloqueado, proyectil, fallo, poción, nube, eventos de entidad con víctima jugador y con víctima zombi) con dobles de `ContextEvents`, `ThreatEnrichment` y `OutcomeEvents` que registran cada llamada, incluidas las de `OutcomeEvents.dealt` (daño hecho de M16.8, con su fracción de vida), `taken` y `died` → `golden/m17/listeners/*.txt`.
  - **Nota tras la auditoría de M16 (2026-09-29):** si el re-banco de M16.10 cambia algún default (`questionDetail`, `minimumConfidence`, `fitVeto`, `fitRescue`) o la redacción de criterios, instrucciones o estado, las referencias de M17.0 se graban **después** de ese cambio o se regeneran en un paso propio antes del primer cambio de M17; una referencia grabada con los defaults anteriores no demuestra nada.
  - Un generador (`GoldenRecorder`, `src/test`, tarea `m17Goldens` fuera de `check`) escribe los archivos; se revisan y se guardan en el repositorio en un paso propio. A partir de ahí, `EntityGeneralizationParityTest` (M17.10) los compara byte a byte en cada build. Las referencias guardan **contenido observable** (cuerpos, hechos, llamadas a Paper con sus argumentos, eventos), no firmas Java: cuando M17 cambie firmas (`Player` → `LivingEntity`, `aliasFor` con tipo), los dobles de las pruebas se adaptan a las firmas nuevas y deben seguir produciendo exactamente el mismo contenido.
- **Archivos:** `src/test/java/akyrion/thepredict/golden/GoldenRecorder.java`; `src/test/resources/golden/m17/…`; `build.gradle.kts` (`m17Goldens`).
- **Reglas:** P2, P4, W4, A8.
- **Pruebas:** el propio generador es determinista (dos ejecuciones dan los mismos archivos).
- **Aceptación:**
  - [x] Referencias grabadas con el código de M16 cerrado: copia aislada de `snapshots/M16` con solo los defaults de M16.10, sin ningún cambio de M17, y reproducibles: una regrabación independiente desde la instantánea da los mismos bytes. Nota: en el repositorio, M17 empezó a las 20:36, antes de la grabación de las 20:49; la copia aislada y la regrabación lo hacen irrelevante. (Casilla reformulada y aceptada por el coordinador como desviación 17.)

### M17.1 Participante como entidad en el dominio

- **Propósito:** que el dominio no suponga jugadores y que Jev reciba referencias cortas y estables (I6: por eficiencia, es decir, calidad de respuesta y tokens; la privacidad no es un criterio de diseño para este usuario).
- **Diseño:**

  ```java
  enum ParticipantKind { PLAYER, ENTITY }
  record ParticipantAlias(ParticipantKind kind, int index) {}      // index >= 1; texto "player_" o "entity_" + index
  record ParticipantFacts(ParticipantAlias alias, HealthBand health, ArmorBand armor,
                          Set<TargetCue> cues, int threatRank, Optional<String> species) {}
  record CaptureContext(BiFunction<UUID, ParticipantKind, ParticipantAlias> aliasFor, …) {}   // resto igual
  ```

  - `ParticipantAlias(int index)` se conserva como constructor de compatibilidad (`PLAYER`), W5. `PREFIX` pasa a `PLAYER_PREFIX = "player_"` y `ENTITY_PREFIX = "entity_"`.
  - `species`: presente solo en `ENTITY`, patrón `[a-z0-9_]{1,40}`, la clave de `EntityType` sin espacio de nombres (`zombie`, `wither_skeleton`). Nunca el nombre personalizado, el `Display` ni el ID de MythicMobs en lo que se envía a Jev (I6): son texto de autor o de jugador, largo, variable y a veces engañoso, que cuesta tokens y resta precisión ([jaggedness §5 y §6](https://docs.typesafe.ai/model-jaggedness/jev-1.13)); una entidad de MythicMobs envía su tipo base.
  - `ParticipantAliases`: un contador por tipo (`player_1`, `entity_1` pueden coexistir); capacidad 32 compartida y la regla de desalojo de hoy (nunca un participante actual). `aliasFor(UUID, ParticipantKind)`; si un UUID ya tiene alias, se devuelve el suyo aunque cambie el tipo (no ocurre: el UUID de una entidad no cambia de tipo).
  - Invariantes de `CombatFacts` sin cambios (alias únicos, cada objetivo es participante, rangos de amenaza únicos); un alias `player_` exige `species` vacía y uno `entity_` la exige presente.
- **Archivos:** crear `domain/ParticipantKind.java`; modificar `domain/ParticipantAlias.java`, `ParticipantFacts.java`, `CaptureContext.java`, `CombatFacts.java` (invariante de especie); `engine/ParticipantAliases.java`, `Encounter.java`, `EncounterCapture.java`; pruebas `ParticipantAliasTest`, `ParticipantFactsTest`, `CombatFactsTest`, `CaptureContextTest`, `ParticipantAliasesTest`, `EncounterCaptureTest`.
- **Reglas:** A2, D1, D2, I2, I6 (nueva redacción), C2, C4.
- **Pruebas:** textos de alias; contadores independientes; desalojo con los dos tipos; especie rechazada en jugador y exigida en entidad; patrón de especie.
- **Aceptación:**
  - [x] Con solo jugadores, `CombatFacts` y alias iguales a los de la 0.0.1 (misma prueba de igualdad sobre el banco de M16).

### M17.2 Política de participantes: selectores nativos, bandos y defaults

- **Propósito:** decidir qué entidades pueden ser objetivo o participante con distinciones nativas, configurables de forma global y por perfil, con defaults idénticos a la 0.0.1.
- **Diseño:**
  - `config.yml` (sección opcional; ausente = defaults):

    ```yaml
    participants:
      # Players in these game modes can be perceived, targeted and fought.
      playerGameModes: [SURVIVAL, ADVENTURE]
      entities:
        # Selectors of non-player living entities that can take part. Empty: none (0.0.1 behavior).
        allow: []
        # Selectors that never take part, even if a profile allows them.
        deny: []
        # Whether profiles may add entity selectors of their own.
        profilesMayAllow: true
        # Whether entities other than the mobs' targets are perceived (one nearby-entity query per member).
        perceiveNonTargets: false
        # Most non-target entities kept per member and capture when perceiveNonTargets is true (1 to 16).
        maxCandidatesPerMember: 8
    ```

  - Perfil (sección opcional `Participants`):

    ```yaml
    Participants:
      PlayerGameModes: []                  # subconjunto de participants.playerGameModes; [] = nunca jugadores
      Entities:
        Allow: ['faction:duel_blue']       # se suma a participants.entities.allow
        Deny: []                           # se suma a participants.entities.deny
        PerceiveNonTargets: false          # solo puede activarse si el global lo permite (true)
    ```

  - **Selectores** (texto `prefijo:valor`; `config` solo valida la sintaxis porque no puede leer registros de Bukkit, D3; el adapter resuelve los valores al vincular y avisa de los desconocidos):

    | Selector | Coincide si | Resolución (una vez por revisión de catálogo) | Coste por entidad |
    |---|---|---|---|
    | `type:<clave>` (`type:minecraft:zombie`, sin espacio de nombres = `minecraft`) | `getType()` es ese tipo | `Registry.ENTITY_TYPE` | `EnumSet.contains` |
    | `tag:<clave>` (`tag:minecraft:undead`, `tag:mipack:duelistas`) | el tipo está en la etiqueta | `Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, key, EntityType.class)`, volcada a un `EnumSet` | `EnumSet.contains` |
    | `category:<SpawnCategory>` | `getSpawnCategory()` | nombre del enum | `EnumSet.contains` |
    | `kind:LIVING\|MOB\|ENEMY\|MONSTER\|ANIMAL` | `instanceof LivingEntity`, `Mob`, `Enemy`, `Monster`, `Animals` | — | `instanceof` |
    | `mythic:*` / `mythic:<IdDeMob>` | es un `ActiveMob` / de ese tipo exacto (I7) | — | `getActiveMob(UUID)` O(1) |
    | `faction:<nombre>` | `ActiveMob.getFaction()` igual **sin distinguir mayúsculas** (`equalsIgnoreCase`, como MythicMobs: su `PermissionFactionProvider` pasa el nombre a minúsculas) | — | O(1) |
    | `team:<nombre>` | `getMainScoreboard().getEntityTeam(e)` cuyo nombre es igual sin distinguir mayúsculas (`equalsIgnoreCase`); si dos equipos del servidor solo difieren en mayúsculas, aviso al vincular | — | búsqueda en el mapa de entradas del scoreboard |
    | `tagged:<etiqueta>` | `getScoreboardTags()` la contiene (`/tag` vanilla) | — | `Set.contains` |
    | `predictor` | es un mob vinculado a ThePredict: `ActiveMob` cuyo tipo (`getType().getInternalName()`, exacto, I7) tiene `BoundProfile` en el catálogo vigente (el binder solo incluye mobs con `Predictor: true`). **No** es `CombatReader.isActive`, que solo dice "`ActiveMob` vivo" | catálogo de `BoundProfile` de la revisión vigente | `getActiveMob(UUID)` O(1) + búsqueda en el mapa del catálogo |

    Validación en `config` (error con archivo y ruta, C13): prefijo conocido; `type`/`tag` con patrón de `NamespacedKey`; `category` en la lista de nombres de `SpawnCategory` (copiada como texto en `config`, y una prueba del adapter comprueba que coincide con `SpawnCategory.values()`); `kind` en su lista; `faction`/`team`/`tagged` con `[A-Za-z0-9_.+-]{1,64}`; como mucho 16 selectores por lista. `PlayerGameModes` del perfil ⊆ lista global; modos válidos `SURVIVAL`, `ADVENTURE`, `CREATIVE`, `SPECTATOR`.
  - **Evaluación** `adapter.server.context.ParticipantPolicy.admits(LivingEntity e, LiveMember member, Set<UUID> encounterMembers)`, en este orden (el primero que decide, gana):
    1. No válida o muerta → no.
    2. Es el propio mob o un miembro de su encuentro (aliados de hecho) → no.
    3. Jugador → su modo de juego está en la lista efectiva (perfil o global). Nada más se aplica a jugadores, para no cambiar los defaults.
    4. `ArmorStand` → no (siempre; es `LivingEntity` pero no combate).
    5. Coincide con algún `deny` (global ∪ perfil) → no.
    6. **Mismo bando** que el mob → no. Bando (`SideReader`): los dos son `ActiveMob` con facción y son iguales con `equalsIgnoreCase`; si no, los dos tienen equipo en el scoreboard principal y es el mismo objeto `Team` (o nombres iguales con `equalsIgnoreCase`); si no, no son del mismo bando.
    7. Coincide con algún `allow` (global ∪ perfil si `profilesMayAllow`) → sí; si no, no.

    `ParticipantPolicy` se compila por perfil al vincular (`MythicProfileBinder` o un compilador propio en `context`) y se guarda en `BoundProfile`; los selectores con valor desconocido dan un `BindingProblem` `WARNING` con la ruta (`participants.entities.allow[2]` o `Participants.Entities.Allow[0]`) y se ignoran.
  - Dominio: `TacticalProfile` gana `ParticipantRules participants` (`record ParticipantRules(Set<String> playerGameModes, List<String> entityAllow, List<String> entityDeny, boolean perceiveNonTargets)`), texto opaco como las condiciones nativas (01 §1): `domain` no lo interpreta.
- **Archivos:** crear `config/ParticipantSettings.java` (y su validación), `domain/ParticipantRules.java`, `adapter/server/context/ParticipantPolicy.java`, `ParticipantSelectors.java` (compilación), `SideReader.java`; modificar `config/PluginSettings.java`, `ProfileSchema.java` (`PROFILE` gana `Participants`; `PARTICIPANTS`, `PARTICIPANT_ENTITIES`), `ProfileSectionParser.java` (o un `ParticipantsParser` nuevo por C10), `PluginConfigurationLoader.java`, `src/main/resources/config.yml`; `domain/TacticalProfile.java`; `adapter/server/binding/BoundProfile.java`, `MythicProfileBinder.java`; raíz (D10); pruebas `ParticipantSettingsTest`, `ProfileLoaderTest`, `ParticipantPolicyTest` (con dobles de entidades), `SideReaderTest`, `MythicProfileBinderSuiteTest`, `PluginConfigurationLoaderTest`.
- **Reglas:** A1, A5 (listas de 16, candidatos acotados), C13, D3, D5, D10, I7.
- **Pruebas:** cada selector con una entidad que coincide y otra que no; orden de evaluación (un `deny` gana a un `allow`; el bando gana a un `allow`; jugador nunca afectado por selectores); perfil que intenta un modo de juego fuera de la lista global → error; `profilesMayAllow: false` ignora los `Allow` del perfil con aviso; etiqueta de datapack desconocida → aviso; sin sección → política igual a la de hoy (prueba de igualdad con `Perception.eligible` de la 0.0.1 para jugadores de los cuatro modos).
- **Aceptación:**
  - [x] `config.yml` sin la sección y con la sección por defecto producen exactamente el comportamiento de la 0.0.1.
  - [x] Cada selector documentado en la guía del administrador y del autor con su API nativa (W3).

### M17.3 Percepción y selección para cualquier `LivingEntity`

- **Propósito:** percibir entidades con el mismo coste que hoy cuando no se permiten, y acotado cuando sí.
- **Diseño:**
  - **Objetivos primero (sin coste extra):** el objetivo de cada miembro (M17.5) se ofrece si la política lo admite, como hoy con el jugador objetivo.
  - **Vecinos:** si la política efectiva del miembro **no** admite entidades o `perceiveNonTargets` es falso, se usa exactamente `getNearbyPlayers(at, radius)` de hoy. Si admite entidades y `perceiveNonTargets` es verdadero, una sola consulta `getNearbyLivingEntities(at, radius, e -> policy.admitsCheap(e))` (tipo, categoría, clase; lo caro, como el scoreboard, después) sustituye a la de jugadores y devuelve jugadores y entidades; de las entidades no jugadoras se quedan las `maxCandidatesPerMember` más cercanas con `NearestK` (sin ordenar todas).
  - `ParticipantSelection.select` y `ranks` no cambian (trabajan con UUID); el tope de 16 participantes por captura (`CombatFacts.MAX_PARTICIPANTS`) y de 16 por encuentro (`EncounterRegistry.MAX_PARTICIPANTS`) se mantienen para todos los tipos juntos.
  - Visibilidad y línea de visión: `Perception.lineOfSight(LivingEntity, LivingEntity)`, una por pareja y captura como hoy; `visible = lineOfSight && !isInvisible()` para todos.
  - Coste acotado (A5): con `perceiveNonTargets: false`, O(1) extra por miembro; con `true`, una consulta de vecinos por miembro (la misma que hoy para jugadores, ahora sobre entidades vivas) y como mucho `maxCandidatesPerMember` lecturas extra por miembro. `m13Measure` gana una fila con 30 mobs y 60 entidades cercanas.
- **Archivos:** `adapter/server/context/FactsAssembler.java`, `Perception.java`, `CombatCapture.java`, `LiveMember.java`; pruebas `FactsAssemblerTest`, `PerceptionTest`, `CombatCaptureTest`, `CaptureDoubles`.
- **Reglas:** A5, T1, C12, I6.
- **Pruebas:** sin entidades permitidas, la captura llama solo a `getNearbyPlayers` (doble que cuenta llamadas); con `perceiveNonTargets: false`, solo el objetivo entidad entra; con `true`, corte en `maxCandidatesPerMember`; mezcla de jugadores y entidades hasta 16; línea de visión una vez por pareja.
- **Aceptación:**
  - [x] Capturas de la 0.0.1 idénticas con la política por defecto (misma lista de participantes y mismas llamadas a Paper en los dobles).

### M17.4 Lecturas y señales por tipo

- **Propósito:** que los hechos de un participante existan para cualquier entidad y que los que solo tienen sentido en jugadores se degraden sin inventar nada.
- **Diseño:**
  - `EntityReads.participant(LivingEntity)` devuelve `ParticipantReading` con `ParticipantKind kind`; los campos que solo existen en jugadores toman un valor neutro que no produce señal (`attackCooldownRatio = 1`, `pearlCooldownTicks = 0`, `shieldOnCooldown = false`). `TargetCueReader` no cambia.
  - **Matriz de hechos** (✓ = mismo cálculo que hoy; ≈ = equivalente nativo; — = no existe, no se envía):

    | Hecho | Jugador | Mob que usa objetos (esqueleto, pillager, piglin, bruja, zombi con equipo) | Otra entidad viva | Fuente |
    |---|---|---|---|---|
    | `HealthBand` | ✓ | ✓ | ✓ | vida + absorción sobre `MAX_HEALTH` |
    | `ArmorBand` | ✓ | ✓ | ✓ | atributos `ARMOR` y `ARMOR_TOUGHNESS` |
    | `RAISING_SHIELD`, `BLOCKING` | ✓ `isBlocking` | ≈ objeto activo con `BLOCKS_ATTACKS`, bloqueando si `getActiveItemUsedTime()` ≥ `Math.round(blockDelaySeconds() × 20f)` (como `BlocksAttacks.blockDelayTicks` de vanilla; 0 = al instante) | ≈ igual | `Blocking.of(LivingEntity)` (DT-38) |
    | `SHIELD_DISABLED` | ✓ | — | — | cooldown de objeto (solo humanos) |
    | `ATTACK_RECHARGING` | ✓ | — | — | `HumanEntity#getAttackCooldown` |
    | `DRAWING_BOW`, `BOW_FULLY_DRAWN` | ✓ | ≈ `getActiveItem`, `getActiveItemUsedTime` | — | Paper `LivingEntity` |
    | `CROSSBOW_LOADED` | ✓ | ≈ equipo con `CHARGED_PROJECTILES` | — | `getEquipment()` |
    | `CONSUMING` | ✓ | ≈ objeto activo con `CONSUMABLE` | — | Paper |
    | `HOLDS_TOTEM` | ✓ | ≈ equipo con `DEATH_PROTECTION` | — | `getEquipment()` |
    | `PEARL_ON_COOLDOWN` | ✓ | — | — | cooldown de grupo (solo humanos) |
    | `GLIDING` | ✓ | ✓ | ✓ | `isGliding()` |
    | `LONG_REACH_WEAPON` | ✓ | ≈ | — | componentes del arma en mano |
    | `MACE_DIVE` | ✓ | ≈ | ≈ | caída + maza |
    | `INVISIBLE` | ✓ | ✓ | ✓ | `isInvisible()` |
    | Tendencia (`Trend`) | ✓ input + distancia | ≈ velocidad proyectada + distancia | ≈ igual | `getVelocity()` (DT-36) |
    | Alcanzable a pie | ✓ | ✓ | ✓ | `Pathfinder#findPath(LivingEntity)` |
    | Fuera del alcance del objetivo | ✓ `ENTITY_INTERACTION_RANGE` o `ATTACK_RANGE` | ≈ ver abajo | ≈ ver abajo | M15 y constante vanilla |
    | Flanqueable | ✓ | ≈ con `Blocking.of` | ≈ | `ShieldGeometry` |
    | Amenaza (`threatRank`) | ✓ | ✓ | ✓ | tabla de MythicMobs por UUID |
    | Especie | — | ✓ | ✓ | `getType().getKey()` |

  - **Tendencia sin input:** `TargetRelationReader.trendFromVelocity(dx, dz, vx, vz)`: proyección horizontal de la velocidad sobre la dirección hacia el mob; más de `0,05` bloques/tick hacia el mob → `CLOSING`, alejándose → `BACKING_AWAY`, lateral mayor → `STRAFING`, si no `STEADY`; después la misma corrección por distancia de hoy (`correctedByDistance`). Para jugadores nada cambia.
  - **Alcance del objetivo no jugador, corregido tras la auditoría** (`EntityReads.targetReach(LivingEntity): TargetReach`, en lugar del `reachBlocks: OptionalDouble` del diseño). Cada alcance se compara como lo mide su fuente:
    - un jugador, con la distancia, como en la 0.0.1;
    - un rival de ThePredict, con el hueco entre cajas de su perfil;
    - un `Mob` vanilla, con la comprobación exacta de `Mob.isWithinMeleeAttackRange` (javap, DT-37). La caja del atacante, ensanchada hasta la de su vehículo, se infla solo en horizontal con el `ATTACK_RANGE` de su arma (`maxReach × mobFactor`, con mínimo) o con `DEFAULT_ATTACK_REACH` (≈ 0,83), y debe cortar la del mob eje por eje;
    - nada se afirma con un ataque a distancia nativo (`RangedEntity`, `Spellcaster`, blaze, breeze, creeper, ender dragon, ghast, guardian, shulker, warden), un arco, ballesta o tridente, un mob de MythicMobs sin perfil de ThePredict o una entidad que no es un `Mob`.

    El diseño original decía: si es un mob vinculado a ThePredict (misma fuente que el selector `predictor`: `MobExecutor.getActiveMob(uuid)` → `getType().getInternalName()` → `BoundProfile` del catálogo, la misma función `profiles` que ya recibe `MythicMemberSource`), el mayor `reachBlocks` de sus choices dañinas con propósito (M15) o, si no hay ninguno, su `Movement.MeleeReach`; si es un `Mob` con arco o ballesta en mano, vacío (no se afirma nada); si es otro `Mob`, alcance cuerpo a cuerpo vanilla `MOB_MELEE_REACH_BLOCKS` medido como hueco entre cajas (valor a confirmar con javap sobre el servidor parcheado con paperclip antes de implementar, DT-37); si no, vacío. `outsideTargetReach` solo es verdadero con un alcance conocido.
  - **Escudo genérico** `adapter.server.context.Blocking` (`record BlockState(boolean raised, boolean blocking)`): para humanos, `isBlocking()` como hoy; para el resto, `raised` = `hasActiveItem()` y `getActiveItem()` tiene `BLOCKS_ATTACKS`, y `blocking` = `raised` y `getActiveItemUsedTime()` ≥ `Math.round(blockDelaySeconds() × 20f)` ticks (corregido tras la auditoría: vanilla redondea al más cercano, no hacia arriba) (`blockDelaySeconds()` es un `float` en segundos y `0` significa que bloquea al instante). No se usan `isHandRaised()` ni `getHandRaisedTime()` (obsoletos desde 1.20.4, `@ApiStatus.Obsolete`) ni `getShieldBlockingDelay()` (`@Deprecated` desde 1.21.9, devuelve 5 fijo). `ShieldGeometry.raisedShieldAngleDegrees` se generaliza igual (`hasActiveItem()` en lugar de `isHandRaised()`) y sigue leyendo solo `damageReductions()` para el ángulo. Lo usan la captura, la revalidación previa al cast (M17.7) y el listener de golpes bloqueados (M17.8).
- **Archivos:** crear `adapter/server/context/Blocking.java`; modificar `EntityReads.java`, `PaperEntityReads.java`, `ParticipantReading.java`, `TargetRelationReader.java`, `ShieldGeometry.java`; pruebas `PaperEntityReads` con dobles, `TargetRelationReaderTest`, `TargetCueReaderTest`, `ShieldGeometryTest`, `BlockingTest`.
- **Reglas:** A1, 01 §1 (bandas y señales, sin números a Jev), plan §6 (percepción natural), C12.
- **Pruebas:** una fila de la matriz por prueba con dobles; tendencia por velocidad en sus fronteras; alcance de un rival de ThePredict desde su perfil; ninguna señal de jugador aparece en una entidad.
- **Seguridad de la desviación 1 (holder jugador no admitido):** un jugador que encabeza la tabla de amenaza sin que la política lo admita (por ejemplo, en creativo) deja al mob sin objetivo para ThePredict, como en la 0.0.1, en lugar de pasar a `Mob#getTarget()`. Esto no permite ningún efecto sobre ese jugador:
  - `CombatCapture.probe` y `resolve` filtran el objetivo por la política;
  - sin objetivo admitido, la intención se invalida y el mob queda en `idle` sin preguntas;
  - un cast solo apunta al objetivo de los hechos, que siempre está admitido.
- **Aceptación:**
  - [x] Matriz publicada en la guía del autor (qué ve un mob de otro mob).
  - [x] Lecturas de jugadores idénticas a la 0.0.1.

### M17.5 Objetivo del mob y sondas

- **Propósito:** que el objetivo nativo del mob pueda ser cualquier entidad admitida.
- **Diseño:** `MemberSource.target(UUID): Optional<LivingEntity>`: primero `getTopThreatHolder()` de la tabla de amenaza si su entidad Bukkit es `LivingEntity`, está en el mismo mundo y la política la admite; si no, `Mob#getTarget()` con las mismas condiciones. `CombatCapture.probe` y `resolve` usan la política en lugar de `Perception::eligible`; `MobSighting` y `MobProbe` no cambian (UUID y banda). `LiveMember.target` pasa a `Optional<LivingEntity>`.
- **Archivos:** `adapter/server/context/MemberSource.java`, `MythicMemberSource.java`, `CombatCapture.java`, `LiveMember.java`; pruebas `CombatCaptureTest`, `MythicMemberSource` con dobles.
- **Reglas:** A1 (el objetivo lo sigue eligiendo MythicMobs o vanilla), T1, I6.
- **Pruebas:** objetivo zombi admitido y no admitido; jugador en creativo no admitido (como hoy); objetivo en otro mundo descartado.
- **Aceptación:**
  - [x] Un mob que apunta a una entidad no admitida se comporta como hoy sin objetivo (`idle`, sin preguntas).

### M17.6 Encuentros por bando

- **Propósito:** que dos mobs de ThePredict que se enfrentan no compartan encuentro ni solicitud, y que las reglas de encuentro sigan siendo O(miembros).
- **Diseño:**
  - **Un encuentro es un bando:** sus miembros son aliados y sus participantes, rivales. Consecuencias: cada bando pregunta a Jev solo por sus mobs y con solo lo que sus mobs perciben (percepción natural); `CoordinationPolicy.limitRetreats` sigue contando retiradas de aliados, no de rivales.
  - `EncounterRegistry.assign`: además de lo de hoy, **nunca** se une a un encuentro que lista a la propia entidad del mob como participante (`candidate.isParticipant(session.key().entityId())` → se salta).
  - `CombatReader.withinFollowRange(mob, member)` añade "y no son rivales": falso si la política de uno admite al otro como participante y no son del mismo bando. Con la política por defecto (sin entidades) nunca son rivales, así que el predicado es el de hoy.
  - Captura: los UUID de los miembros del encuentro nunca son participantes (regla 2 de M17.2).
  - Muerte o retirada de un participante (`EntityDeathEvent`, `EntityRemoveEvent`): `ContextEvents.participantGone(worldId, uuid)` marca contexto pendiente en sus encuentros (búsqueda O(1) por el índice); la baja efectiva la hace la captura siguiente, como hoy con un jugador que se va.
  - Topes sin cambios: 16 miembros y 16 participantes por encuentro; 32 alias.
  - Duelo 1 contra 1 entre dos mobs de ThePredict A y B: A forma E1 (miembro A, participante `entity_1` = B); B forma E2 (miembro B, participante `entity_1` = A). Dos solicitudes independientes por latido, cada una con su punto de vista.
- **Archivos:** `engine/EncounterRegistry.java`, `ContextEvents.java`, `CombatReader.java` (Javadoc); `adapter/server/context/CombatCapture.java` (`withinFollowRange`); `adapter/server/events/LifecycleListener.java` o `CombatContextListener.java` (muerte de participantes); pruebas `EncounterRegistryTest`, `EncounterRegistryScaleTest`, `ContextEventsTest`, `CombatCaptureTest`.
- **Reglas:** A3, A5, D2 (el motor no conoce bandos: el adapter lo resuelve en el predicado), I2, T1.
- **Pruebas:** A y B se apuntan mutuamente → dos encuentros, ninguno con el otro como miembro; 2 contra 2 con facciones → dos encuentros de dos miembros; tres mobs neutrales contra un jugador → uno, como hoy; escala de `assign` (M13) sin cambios con la política por defecto.
- **Aceptación:**
  - [x] Ningún encuentro tiene a la vez a una entidad como miembro y como participante (propiedad comprobada en la carga de M13 con entidades).

### M17.7 Ejecución y amenaza sobre entidades

- **Propósito:** lanzar skills, golpear y enriquecer la amenaza contra cualquier participante.
- **Diseño:**
  - `CastSource.target(UUID): Optional<LivingEntity>`; `conditionsHoldAt` y `executeAt` con `LivingEntity`, adaptada con `BukkitAdapter.adapt(Entity)`; `changeThreat(UUID mobId, LivingEntity, double)` con `threatGain`/`threatLoss(AbstractEntity, double)`.
  - `CombatActionsAdapter`: `Function<UUID, Optional<LivingEntity>> entities` (raíz: `Bukkit.getEntity(uuid)` filtrada a `LivingEntity` válida y viva); el escudo con `Blocking.of(target).blocking()` y `ignoresShield` (M15.6); invulnerabilidad (`getNoDamageTicks`) y alcance ya valen para cualquier `LivingEntity`; el golpe vanilla `attack(Entity)` ya acepta cualquier entidad.
  - `addThreat` exige que la entidad sea participante del encuentro del mob (ya lo filtra `ThreatEnrichment`) y del mismo mundo.
- **Archivos:** `adapter/server/execution/CastSource.java`, `MythicCastSource.java`, `MythicSkillExecutor.java`, `CombatActionsAdapter.java`; `ServerComponents.java`; `engine/ThreatChanges.java` (texto); pruebas `CombatActionsAdapterTest`, `ExecutionDoubles`.
- **Reglas:** T1, A6, C7, D5, D8.
- **Pruebas:** cast contra un zombi; `SKIPPED_SHIELD` contra un mob con escudo levantado del todo; amenaza sobre entidad participante y rechazo si no lo es.
- **Aceptación:**
  - [x] Todas las pruebas de M10 y M11 con jugadores sin cambios (solo firmas y el texto "a participant without alias" que pide M17.7).

### M17.8 Eventos de contexto y de resultado

- **Propósito:** que los hechos del historial y el contexto pendiente funcionen con rivales no jugadores.
- **Diseño:**
  - `CombatOutcomeListener.onDamage`, reestructurado en tres casos explícitos (hoy el primero absorbe cualquier víctima jugadora):
    1. causante directo miembro y víctima `LivingEntity` participante → `MELEE_HIT` o `MELEE_BLOCKED` (con `Blocking.of`), y amenaza por golpe bloqueado como hoy;
    2. proyectil cuyo tirador es `LivingEntity` participante y víctima miembro → `RANGED_HIT_ON_MOB` y acoso a distancia como hoy;
    3. (M16.8) daño hecho y recibido para `OutcomeWindow`.
  - `onProjectileHit`: tirador `LivingEntity` participante → `RANGED_MISS`. `onPotionSplash` y `onCloudApply`: fuente `LivingEntity` participante; afectados participantes de cualquier tipo → `ALLY_ASSISTED`.
  - `CombatContextListener`: los eventos `Player*` siguen siendo de jugador; los de entidad (`EntityLoadCrossbowEvent`, `EntityResurrectEvent`, `EntityEquipmentChangedEvent`, `EntityAttemptSmashAttackEvent`, `EntityLungeEvent`) aceptan cualquier `LivingEntity` participante; nuevo `EntityShootBowEvent` para no jugadores (contexto pendiente); `EntityDeathEvent` → `participantGone` (M17.6). Todos pasan primero por la búsqueda O(1) `isParticipant(world, uuid)`, así que un evento de una entidad ajena no hace más trabajo.
  - `EventType` no cambia; `PEARL_THROWN`, `POTION_CONSUMED` y `SHIELD_DISABLED` siguen siendo de jugador.
- **Archivos:** `adapter/server/events/CombatOutcomeListener.java`, `CombatContextListener.java`; `engine/ContextEvents.java`; pruebas `CombatOutcomeListenerTest`, `CombatContextListenerTest`, `ContextEventsTest`.
- **Reglas:** T1, T6, A5, C12.
- **Pruebas:** cada evento con un jugador (igual que hoy) y con una entidad participante y otra que no lo es.
- **Aceptación:**
  - [x] Con la política por defecto, los mismos eventos y contadores que en la 0.0.1.

### M17.9 Codificador y alias para Jev

- **Propósito:** que Jev distinga jugadores de otras criaturas con el mínimo de texto útil (I6, por eficiencia).
- **Diseño:**
  - Entrada de participante `entity_N`: primer hecho `entity_1 is a zombie` (especie con `_` → espacio); el resto de hechos como hoy. Sin especie para jugadores.
  - `headcount(players, entities, mobs)`: si `entities == 0`, la frase de hoy (byte a byte); si no, `one player, one other creature and one mob are involved` (con los mismos números en palabras).
  - Frases con sujeto (`health`, `armor`, `threat`, señales) ya reciben el alias como texto: sin cambios.
  - El estado enviado a Jev nunca incluye el `Display`, el nombre personalizado, el ID de MythicMobs, la facción ni UUID de una entidad (I6, por eficiencia); la facción solo decide bandos en el adapter. Las salidas **locales** (registro, `inspect`, depuración, registrador de duelos de M18) sí pueden mostrar UUID, nombres y facciones cuando ayudan (decisión del usuario).
- **Archivos:** `adapter/jev/SemanticEncoder.java`, `SemanticPhrases.java`; pruebas `SemanticEncoderTest`, `SemanticPhrasesTest`, `ScenarioBankTest` (igualdad con el banco de M16 sin entidades).
- **Reglas:** I6 (nueva redacción), I4, T2.
- **Pruebas:** banco de M16 codificado byte a byte igual; encuentro con un jugador y una entidad; especie con guion bajo.
- **Aceptación:**
  - [x] Ningún nombre ni UUID en el estado **enviado a Jev** de un encuentro con entidades (grep de UUID sobre los cuerpos grabados, como en el guion §8.2).

### M17.10 Defaults idénticos y verificación en servidor

- **Propósito:** demostrar que nada cambia para quien no usa entidades y que la generalización funciona en juego.
- **Diseño:**
  - Prueba de no regresión `EntityGeneralizationParityTest`: con la configuración por defecto, vuelve a ejecutar exactamente lo que grabó M17.0 y compara **byte a byte** con `src/test/resources/golden/m17/`: los cuerpos del banco en los tres niveles; la carga de M13 y el A/B local (solicitudes, vistas publicadas y contadores); y, **a nivel de adapter con dobles de `Player`**, las capturas de `CombatCapture` (hechos, avistamientos y las llamadas a `getNearbyPlayers` y `hasLineOfSight` con su orden: con la política por defecto no debe aparecer ninguna llamada a `getNearbyLivingEntities`) y las secuencias de `CombatOutcomeListener` y `CombatContextListener` (el zombi víctima no produce ninguna llamada). Cualquier diferencia falla el build; una diferencia querida exige regenerar la referencia en un paso propio y justificarlo en este documento.
  - Sesión nueva **17** del guion (sin clave, costo 0):
    - 17.1 Defaults: guardián contra un jugador con un zombi vanilla cerca; `/thepredict inspect` muestra solo `player_1`.
    - 17.2 Perfil de prueba con `Entities.Allow: ['kind:MONSTER']` y `AITargetSelectors` de MythicMobs `- 1 monsters`: el mob apunta al zombi; `inspect` muestra `entity_1` con especie `zombie`, salud, tendencia y alcance.
    - 17.3 Dos mobs de ThePredict con `Faction: red` y `Faction: blue`, `AITargetSelectors: - 1 nearestSpecificFaction{f=blue}` y a la inversa: `/thepredict status` muestra dos encuentros, cada uno con un miembro y un participante `entity_1`.
    - 17.4 Un jugador en supervivencia entra en la zona de 17.3 con `PlayerGameModes: []` en los perfiles: nunca es participante ni objetivo de ThePredict (`inspect`).
    - 17.5 Un esqueleto vanilla admitido tensa el arco: señal `DRAWING_BOW` en `inspect` (DT-38 y la matriz).
    - 17.6 Coste: `/thepredict status` (`tick_work`) con 10 mobs y 30 entidades admitidas, `perceiveNonTargets` falso y verdadero.
- **Archivos:** `src/test/java/akyrion/thepredict/golden/EntityGeneralizationParityTest.java`, que usa las referencias de M17.0; `Guion-servidor-0.0.1.md`, `paquete-pruebas/` (archivos `12-entidades/`), `Checklist-DT-y-casillas.md` (DT-36 a DT-38); guías.
- **Reglas:** W3, W4, A8.
- **Pruebas:** la de paridad; `UserTestPackTest` con los archivos nuevos.
- **Aceptación:**
  - [x] Paridad demostrada con la configuración por defecto contra las referencias grabadas antes de M17 (motor y adapter).
  - [x] Sesión 17 escrita con comandos exactos y esperados.

## Cambios propuestos a 00/01

**00. Arquitectura y reglas**

- §6, **I6** nueva redacción (motivo: eficiencia, decisión del usuario): "Participante. En lo que se **envía a Jev**, cada participante es un `ParticipantAlias` estable dentro de un encuentro: `player_N` para jugadores y `entity_N` para cualquier otra entidad viva, con un contador por tipo; de una entidad solo viaja además su especie (clave de `EntityType` sin espacio de nombres). No se envían nombres, nombres personalizados, `Display` o ID de MythicMobs, facciones ni UUID, **porque no mejoran la decisión y cuestan tokens y precisión** (texto largo, variable o engañoso). La regla solo se aplica al cuerpo enviado a TypeSafe; las salidas locales (registro, `inspect`, depuración, registrador de duelos) pueden incluir UUID y nombres cuando ayudan."
- §7, **C8** añade: "Las líneas de registro y los archivos locales de diagnóstico pueden incluir UUID de entidades; nunca claves, cabeceras de autorización ni cuerpos HTTP con credenciales."
- §6, fila nueva **I13 Bandos**: "Un encuentro agrupa aliados; sus participantes son rivales. Una entidad nunca es a la vez miembro y participante del mismo encuentro, y un mob nunca se une a un encuentro que lo lista como participante. El bando lo resuelve el adapter (facción de MythicMobs, luego equipo del scoreboard principal)."
- §4, `CombatReader.withinFollowRange`: "… y no son rivales según la política de participantes (M17)".
- §5, T3 sin cambios; §7, C13: "incluidos los selectores de `participants`".
- Encabezado: "Revisión 11: I6 (eficiencia; solo lo enviado a Jev), I13, C8 y `withinFollowRange` (M17)".

**01. Modelo de dominio**

- Encabezado: "**Revisión 12** (fecha): participantes como entidades (`ParticipantKind`, alias por tipo, especie), `ParticipantRules`, bandos en `assign` y `participantGone` (M17, sección 10)."
- §2: `enum ParticipantKind { PLAYER, ENTITY }`; `ParticipantAlias(ParticipantKind kind, int index)` con texto `player_N` / `entity_N`.
- §3: `TacticalProfile` gana `ParticipantRules participants`; `record ParticipantRules(Set<String> playerGameModes, List<String> entityAllow, List<String> entityDeny, boolean perceiveNonTargets)` (texto opaco; `domain` no lo interpreta).
- §4: `ParticipantFacts` gana `Optional<String> species` (presente solo con `ENTITY`); `CaptureContext.aliasFor` pasa a `BiFunction<UUID, ParticipantKind, ParticipantAlias>`; notas: "participante" = cualquier entidad viva admitida por la política; "Percepción natural" vale para todos los tipos.
- §8: `ParticipantAliases` con un contador por tipo y 32 alias compartidos; `Encounter` y `EncounterRegistry` hablan de participantes (no jugadores); regla de bandos en `assign`; `ContextEvents.participantGone`.
- §10, fila: "Participantes como entidades: alias por tipo, especie, política de participantes, bandos | M17".

**Plan (`Plan-Jev-y-combate-tactico.md`)**

- §6, "Percepción natural": sustituir "El estado incluirá solo jugadores dentro del `FOLLOW_RANGE` del mob" por "El estado incluirá solo los participantes que admite la política de participantes (por defecto, jugadores en supervivencia o aventura) dentro del `FOLLOW_RANGE` del mob", con una referencia a M17.
- §6, "Estado semántico": "jugadores" → "participantes" donde describe a quién percibe el mob.

**Registro de decisiones (entradas nuevas propuestas)**

- **DT-36. Tendencia de una entidad por su velocidad.** ¿`getVelocity()` de un mob que camina con el navegador refleja su desplazamiento? Sesión 17.2. Alternativa: solo la corrección por distancia entre capturas. Estado: Abierta.
- **DT-37. Alcance cuerpo a cuerpo vanilla de un mob.** Valor de la caja de ataque de `Mob` en Paper 26.2 (javap del servidor parcheado con paperclip). Alternativa: no afirmar `outsideTargetReach` para mobs sin perfil. Estado: Abierta.
- **DT-38. Escudo de un no jugador.** ¿`hasActiveItem`, el objeto activo con `BLOCKS_ATTACKS` y `getActiveItemUsedTime()` frente a `blockDelaySeconds()` del componente bastan para saber que un mob bloquea? Sesión 17.5 con un mob de MythicMobs que levanta escudo. Alternativa: solo `RAISING_SHIELD`. Estado: Abierta.
- **DT-39. Etiquetas de tipos tras recargar datapacks.** ¿El `Tag` de `Bukkit.getTag` refleja una etiqueta cambiada por `/minecraft:reload`? M17 la vuelca a un `EnumSet` en cada revisión de catálogo; si no se refresca, se documenta que hace falta `/mm reload` tras recargar datapacks. Estado: Abierta.

## Riesgos y decisiones abiertas

| Riesgo | Mitigación |
|---|---|
| Un selector amplio (`kind:LIVING`) hace que los mobs perciban decenas de animales | `perceiveNonTargets: false` por defecto (solo objetivos), `maxCandidatesPerMember`, `deny` global del administrador y la regla de bandos |
| Cambios de firma en todo `adapter.server` | Tipos compatibles (constructor de `ParticipantAlias` de un argumento, W5) y prueba de paridad antes de tocar comportamiento |
| Un jugador y un mob en el mismo equipo de scoreboard | El bando no se aplica a jugadores (regla 3 de M17.2): los defaults no cambian aunque el servidor use equipos |
| Dos mobs de ThePredict duplican solicitudes en un duelo | Es lo correcto (dos bandos, dos puntos de vista); el presupuesto por encuentro y global de M09 lo acota; M18 lo mide |
| Hechos de jugador que no existen en mobs empobrecen el estado | La matriz lo documenta; Jev recibe menos hechos, no hechos falsos (A6) |

## Terminado

M17 está terminado cuando se cumple W4 y además:

- [x] Casillas de M17.0–M17.10 marcadas; referencias de M17.0 grabadas con el código de M16 cerrado (casilla de M17.0 reformulada, desviación 17).
- [x] Paridad total con la configuración por defecto (M17.10).
- [x] Inventario de supuestos "jugador" cerrado: cada fila aplicada o justificada.
- [x] 00 y 01 actualizados; DT-36 a DT-39 registradas.
- [x] Sesión 17 en el paquete de pruebas.

## Estado de implementación

> **Local listo — servidor pendiente (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` termina en `BUILD SUCCESSFUL` sin avisos de javac ni de Javadoc estricto: **1576 pruebas en 211 clases, 0 fallos** (1484 en 201 clases antes de M17: 92 pruebas y 10 clases nuevas; 1562 en 210 en la entrega, antes de las correcciones de la auditoría). `verifyPluginJar` y `ArchitectureRulesTest` pasan. No hay comentarios `//` ni `/* */` en Java (C9, comprobado con un analizador que salta cadenas y bloques de texto). **Ninguna llamada de red:** ni a TypeSafe ni a otro host, y ninguna tarea `live*`. Nadie leyó `.env` y no se arrancó ningún servidor de Minecraft. Las casillas que exigen servidor (sesión 17, DT-36 a DT-39) siguen sin marcar. La entrega incluye los defaults del re-banco de M16.10, que decidió el usuario: `decision.questionDetail: full` y `decision.minimumConfidence: 0.45` (ver [M16](M16-Efectividad-de-las-respuestas-de-Jev.md#resultado-del-re-banco-m1610-2026-09-30)).

### Artefacto

`build/libs/ThePredict-0.0.1.jar`, 938 533 bytes, 480 entradas, SHA-256 `ee7e4be29ae516be86e9602581f7aa5a3b1d097e0c92cf880b3124bb232cbc2b` (tras las correcciones de la auditoría; el de la entrega era `0007f574…c445`, 932 251 bytes). Es reproducible: dos builds limpios dan la misma huella. Está registrado en el [CHANGELOG](../../CHANGELOG.md#artefacto).

### Referencias de M17.0 y prueba de paridad

**Cómo se grabaron.**

1. Se hizo una copia de trabajo de `scratchpad/snapshots/M16/`, el código de M16 cerrado y anterior a cualquier cambio de M17. La instantánea no se modificó.
2. En la copia solo se aplicó el cambio de defaults de M16.10: `QuestionDetail.DEFAULT = FULL`, `DecisionSettings.DEFAULT_MINIMUM_CONFIDENCE = 0.45` y las dos claves de `config.yml`. Así se cumple la nota de M17.0: si M16.10 cambia un default, las referencias se graban después de ese cambio.
3. En la copia se añadió el generador (`GoldenRecorder`, tarea `m17Goldens`), escrito con las firmas de antes de M17.
4. Se grabó el **2026-09-29 a las 20:49:12**.
5. Los 81 archivos (1 924 526 bytes, menos de 2 MB) se copiaron a `src/test/resources/golden/m17/` a las 20:49:45.

**Orden real:** en el repositorio, los primeros archivos nuevos de `src/main` de M17 son de las 20:36, antes de la grabación. Las referencias no salen de ese código, sino de la copia aislada del punto 1, que no contiene nada de M17. La auditoría de M17 las volvió a grabar por su cuenta desde la instantánea y obtuvo los mismos bytes. El texto original de la casilla pedía un orden temporal que no se dio. El coordinador aceptó reformularla (desviación 17), y la casilla reformulada está marcada.

**Qué contienen.**

- `bank-sha256.txt`: el SHA-256 de los 180 cuerpos del banco (60 escenarios × 3 niveles).
- `bank-<nivel>/`: el texto completo de los 20 primeros cuerpos de cada nivel.
- `engine/`: la carga de M13 con 1, 10 y 30 mobs y el A/B de `TacticalAB` (5 escenarios × 10 semillas). Para cada ejecución guarda:
  - el SHA-256 de cada cuerpo real, codificado con `RequestEncoder`;
  - los 20 primeros cuerpos completos;
  - el registro de las `IntentView` publicadas, solo con los cambios, más el SHA-256 del registro completo;
  - los contadores de `DecisionMetrics`, `ThreatMetrics`, `ContextEventMetrics` y `SessionMetrics`.
- `capture/`: 9 escenarios de `CombatCapture` con dobles de `Player`. Guardan los hechos, avistamientos, sondas, `withinFollowRange`, las llamadas a `getNearbyPlayers` y `hasLineOfSight` en orden, y cada llamada al zombi cercano.
- `listeners/`: las secuencias de `CombatOutcomeListener` y `CombatContextListener`, con víctima jugador y víctima zombi.

**Prueba.** `EntityGeneralizationParityTest` vuelve a generar todo con el código de M17 y la configuración por defecto, y lo compara byte a byte. Tiene 5 pruebas y pasa: banco, capturas y listeners; motor; conjunto exacto de archivos por debajo de 2 MB; generador determinista; ningún UUID en los cuerpos. Una diferencia escribe el archivo nuevo en `build/golden-m17/`. Desde la grabación, **ninguna referencia se regeneró**.

**Regeneradas para el cambio de disponibilidad de M10 (decisión del usuario, 2026-09-30).** Es un cambio de comportamiento querido del ritmo de casts local y con Jev (M10, desviación 29; DT-28 revisado). Con la tarea `m17Goldens` se volvieron a grabar las 81 referencias y se compararon con las anteriores (copia guardada antes de regenerar). Solo cambian 3 archivos del motor, y en cada uno solo contadores de resultados de cast:

| Archivo | Línea | Antes | Después |
|---|---|---|---|
| `engine/load-1.txt` | `decision.ExecutionOutcome.NOT_ELIGIBLE` | 0 | 12 |
| `engine/load-10.txt` | `decision.ExecutionOutcome.NOT_ELIGIBLE` | 0 | 113 |
| `engine/load-30.txt` | `decision.ExecutionOutcome.CAST` | 18013 | 18000 |
| `engine/load-30.txt` | `decision.ExecutionOutcome.NOT_ELIGIBLE` | 0 | 342 |
| `engine/load-30.txt` | `casts … signals … threatChanges …` | `casts 18013` | `casts 18000` (señales 5400 y amenaza 0 iguales) |

Los cuerpos de solicitud (y sus SHA-256), los registros de `IntentView`, el banco, las capturas, los listeners, los archivos A/B (`ab-*.txt`) y el resto de contadores son idénticos byte a byte. La causa es el doble de `LoadFixture`, que ahora modela el `Cooldown` nativo de 20 ticks que `CombatActions.ready` necesita: una intención nueva que llega antes de que pase el cooldown del último cast espera (`NOT_ELIGIBLE`) en lugar de lanzar. Es tiempo de cast, no otra cosa. 81 archivos, 1 924 531 bytes.

### Archivos

Todas las rutas son relativas a `src/main/java/akyrion/thepredict/`, salvo que se indique otra cosa.

| Subpunto | Archivos |
|---|---|
| M17.1 | Creado `domain/ParticipantKind`. Modificados `domain/ParticipantAlias` (tipo, `PLAYER_PREFIX` y `ENTITY_PREFIX`, constructor de un argumento), `ParticipantFacts` (`species`, patrón y constructor de cinco argumentos), `CaptureContext` (`BiFunction`), Javadoc de `CombatFacts`, `EncounterEvent` y `ThreatTableState`. En `engine/`: `ParticipantAliases` (un contador por tipo), `Encounter`, `EncounterCapture` y `ParticipantIndex` |
| M17.2 | Creados `domain/ParticipantRules`, `config/ParticipantSettings` y `config/ParticipantsParser`. En `adapter/server/context/`: `ParticipantDefaults`, `ParticipantLookup`, `MythicParticipantLookup`, `SelectorRegistry`, `BukkitSelectorRegistry`, `ParticipantSelectors`, `ParticipantPolicy` y `SideReader`. Modificados `domain/TacticalProfile`; en `config/`: `PluginSettings`, `PluginConfigurationLoader`, `ProfileSchema`, `ProfileParser`, `ConfigurationMigration` y `ProposedConfiguration`; `src/main/resources/config.yml`; `adapter/server/binding/BoundProfile` y `MythicProfileBinder`; en la raíz, `ServerComponents` y `ThePredictPlugin` |
| M17.3 | En `adapter/server/context/`: `FactsAssembler` (objetivos primero y vecinos con `getNearbyPlayers` o `getNearbyLivingEntities` y `NearestK`), `Perception` (sin `eligible`; `lineOfSight(LivingEntity, LivingEntity)`), `CombatCapture` y `LiveMember` |
| M17.4 | Creados `adapter/server/context/Blocking`, `TargetReach` (alcance del objetivo y comprobación vanilla, tras la auditoría), `TargetTrend` (tendencia, separada de `TargetRelationReader` por C10) y `VanillaMobReach` (arma y alcance de un `Mob` vanilla). Modificados `EntityReads` (`targetReach`), `ParticipantReading` (`kind`), `PaperEntityReads`, `TargetRelationReader`, `ShieldGeometry` (`hasActiveItem`) y `ReachGeometry` (`profileReachBlocks`) |
| M17.5 | `adapter/server/context/MemberSource` y `MythicMemberSource` (`choose`), y `CombatCapture` (`probe`, `resolve`) |
| M17.6 | `engine/EncounterRegistry` (`assign` salta un encuentro que lista al mob) y `ContextEvents` (`participantGone`); Javadoc de `engine/CombatReader`; `adapter/server/context/CombatCapture.withinFollowRange` (rivales) |
| M17.7 | En `adapter/server/execution/`: `CastSource`, `MythicCastSource`, `MythicSkillExecutor` y `CombatActionsAdapter`. En `engine/`: `ThreatEnrichment`, `ThreatChanges` y Javadoc de `CombatActions`. En la raíz, `ServerComponents` (`Bukkit.getEntity` filtrado a `LivingEntity`) y `SessionServices` |
| M17.8 | En `adapter/server/events/`: `CombatOutcomeListener`, `CombatContextListener` (`EntityShootBowEvent`, `EntityDeathEvent` y `EntityRemoveEvent`) y `OutcomeListener`. En `engine/`: `ContextEvents` y Javadoc de `OutcomeEvents` y `ThreatMetrics` |
| M17.9 | `adapter/jev/SemanticEncoder`, `SemanticPhrases` y Javadoc de `StateDraft`; `admin/InspectFormatter` (especie del objetivo) |
| M16.10 (defaults) | `domain/QuestionDetail` (`DEFAULT = FULL`), `config/DecisionSettings` (`DEFAULT_MINIMUM_CONFIDENCE = 0.45`), Javadoc de `config/QuestionSettings`, `config.yml` y `AI/example.yml.example` (`MinimumConfidence: 0.45`) |
| Pruebas (`src/test`) | **Nuevas:** `golden/` (`GoldenRecorder`, `GoldenSet`, `FactsText`, `TestRegistryAccess` (atributos y tipos de componente), `EntityGeneralizationParityTest`), `engine/GoldenEngineRuns`, `engine/GoldenWindows`, `adapter/jev/GoldenBodies`, `context/CaptureGoldens`, `events/ListenerGoldens`, `ParticipantRulesTest`, `ParticipantSettingsTest`, `ParticipantPolicyTest`, `SideReaderTest`, `BlockingTest`, `EntityCaptureTest`, `PaperEntityReadsTest` (filas de la matriz con dobles de objetos), `TargetReachTest`, `EntityEncounterLoadTest`, los auxiliares `EntityDoubles` y `EntityMeasurements`, `BankReanalysis` (tarea `reanalyseBank`) y `META-INF/services/io.papermc.paper.registry.RegistryAccess`. **Ampliadas o adaptadas:** 43 archivos, entre ellas `CaptureDoubles`, `LoadFixture`, `TacticalAB`, `UserGuidesTest` y `UserTestPackTest` |
| Documentos | 00 (revisiones 11 y 12), 01 (revisión 12), plan §6, registro M01 (DT-36 a DT-39; DT-31 Resuelta), guías del autor (§4.9 nueva) y del administrador (§4.4 nueva, §7.1 con tokens reales), guion §17, paquete de pruebas (sesión 17, `archivos/12-entidades/`, checklist), M16, CHANGELOG, informe y README |

### Pruebas

Las pruebas nuevas cubren todas las de los subpuntos:

- **M17.1:** alias por tipo, contadores independientes, desalojo con los dos tipos y especie exigida o prohibida.
- **M17.2:** cada selector con una entidad que coincide y otra que no; orden de evaluación; modos del perfil fuera de la lista global; `profilesMayAllow: false`; etiqueta desconocida.
- **M17.2, igualdad con la 0.0.1:** sin sección y con la sección por defecto, igual que `Perception.eligible` de la 0.0.1 en los cuatro modos.
- **M17.3:** solo `getNearbyPlayers` por defecto; solo el objetivo con `perceiveNonTargets: false`; corte en `maxCandidatesPerMember`; 16 participantes como máximo; una línea de visión por pareja.
- **M17.4:** la matriz con dobles, tendencia por velocidad en sus fronteras, alcance de un rival desde su perfil y `Blocking`.
- **M17.5:** holder y objetivo admitidos y no admitidos, creativo y otro mundo.
- **M17.6:** duelo A contra B con dos encuentros; 2 contra 2 con facciones; carga de M13 con entidades, donde nunca hay un miembro que también sea participante.
- **M17.7:** cast y amenaza contra un zombi; `SKIPPED_SHIELD` contra un mob que bloquea.
- **M17.8:** cada evento con un jugador, con una entidad participante y con otra que no lo es.
- **M17.9:** frases de especie y recuento; sin UUID, nombre, ID de MythicMobs ni facción en el estado.

**Pruebas adaptadas a los defaults nuevos** (cambio querido, M16.10):

- `DecisionSettingsTest` y `DecisionTimingTest` esperan `FULL` y 0,45.
- `TacticalAB.DEFAULTS` usa 0,45.
- `RequestEncoderTest`, `ScenarioSeedBank` y `OptionTextDedupTest` fijan `COMPACT` de forma explícita, porque sus goldens de `systemone/` y sus formas se escribieron para `compact`. Sus archivos no cambian.
- `LiveToolsLoopbackTest` exige el banco entero `REVIEWED` y un informe sin `PRELIMINARY`, porque las 60 etiquetas se revisaron el 2026-09-30.
- `UserGuidesTest` comprueba el texto `full` del default.

**Otros cambios en pruebas existentes:**

- **Por el diseño:**
  - `ThreatEnrichmentTest` espera "a participant without alias" (M17.7).
  - Los dobles de `OutcomeListenerTest` y `CombatContextListenerTest` responden `getWorld` y `getUniqueId`, porque la comprobación de participante de M17.8 los lee.
  - El zombi de `ShieldGeometryTest` responde `hasActiveItem` (M17.4).
- **Solo firmas:** el resto son cambios de firma (`aliasFor` con tipo, `LivingEntity`, `ParticipantSettings`).

### Medidas locales (sin red; `./gradlew m13Measure --offline`, tabla "Capture cost with entities around (M17.3)")

| Captura | Consultas de jugadores | Consultas de vivos | Participantes (jugadores + entidades) | Líneas de visión | µs p50 / p95 / p99 |
|---|---|---|---|---|---|
| 30 mobs, 2 jugadores, 60 zombis, política por defecto (0.0.1) | 30 | 0 | 2 + 0 | 22 | 58,8 / 159,8 / 208,8 |
| Igual, zombis admitidos (`kind:MONSTER`), solo objetivos | 30 | 0 | 2 + 10 | 32 | 70,9 / 157,6 / 223,6 |
| Igual, `perceiveNonTargets: true`, `maxCandidatesPerMember: 8` | 0 | 30 | 1 + 15 | 42 | 244,0 / 437,1 / 571,9 |

Los tiempos son de esta máquina, con dobles de proxy, no de un servidor. La guía del administrador (§4.4) los publica.

### Claves nuevas de configuración y defaults

| Clave | Default |
|---|---|
| `participants.playerGameModes` | `[SURVIVAL, ADVENTURE]` |
| `participants.entities.allow` | `[]` |
| `participants.entities.deny` | `[]` |
| `participants.entities.profilesMayAllow` | `true` |
| `participants.entities.perceiveNonTargets` | `false` |
| `participants.entities.maxCandidatesPerMember` | `8` (1–16) |
| Perfil `Participants.PlayerGameModes`, `Entities.Allow`, `Entities.Deny`, `Entities.PerceiveNonTargets` | Sin sección: los globales |
| `decision.questionDetail` (cambiado, M16.10) | `full` (antes `compact`) |
| `decision.minimumConfidence` (cambiado, M16.10) | `0.45` (antes `0.5`) |

Un perfil sigue pudiendo fijar `Timing.QuestionDetail` y `Timing.MinimumConfidence`.

### Evidencia estática (javap, sin red)

- **DT-37 (ampliada tras la auditoría).** javap sobre el servidor parcheado `scratchpad/paper/versions/26.2/paper-26.2.jar`, obtenido antes con paperclip y sin red, y sobre `paper-api` 26.2. `Mob.isWithinMeleeAttackRange` lee `ATTACK_RANGE` del objeto activo del servidor (el objeto en uso o el de la mano principal): `effectiveMaxRange` y `effectiveMinRange`, `maxReach × mobFactor` para un no jugador. Sin el componente usa `DEFAULT_ATTACK_REACH = Math.sqrt(2.04f) - 0.6f`. `getAttackBoundingBox` ensancha la caja hasta la del vehículo e infla con `inflate(r, 0, r)`; `AABB.intersects` es estricto en los tres ejes. La API de Paper lo expone con `AttackRange#maxReach`, `minReach` y `mobFactor`, `hasActiveItem`, `getActiveItem`, `getEquipment` y `getVehicle`. El detalle está en el registro M01. La primera versión (hueco euclídeo, siempre 0,83) queda superada. `isWithinMeleeAttackRange` lo suma en horizontal con `getAttackBoundingBox`. `PaperEntityReads.MOB_MELEE_REACH_BLOCKS` usa ese valor como hueco entre cajas. DT-37 sigue `Abierta` hasta verlo en juego.
- **MythicMobs 5.13.1.** `BukkitAdapter.adapt(Entity)` siempre construye un `BukkitEntity`, así que un jugador sigue usando `adapt(Player)` para no cambiar nada.
- **Escudo (DT-38).** `BlocksAttacks.blockDelayTicks = Math.round(blockDelaySeconds × 20f)` y `LivingEntity.getItemBlockingWith` compara el tiempo de uso con ese valor (javap). Ni vanilla ni MythicMobs 5.13.1 levantan el escudo de un mob: MythicMobs solo tiene `StopUsingItem`.
- **Formato de equipo en `/summon`.** El servidor 26.2 guarda el equipo como `equipment` con las ranuras `mainhand` y `offhand` (javap de `LivingEntity` y `EquipmentSlot`); `Mob` ya no tiene `HandItems`.
- **Paper.** `LivingEntity#hasActiveItem`, `getActiveItemUsedTime`, el componente `BLOCKS_ATTACKS` (`blockDelaySeconds`), `World#getNearbyLivingEntities`, `Entity#getSpawnCategory` y `Scoreboard#getEntityTeam` existen en `paper-api` 26.2. No se usa ninguna API obsoleta ni deprecada.

### Inventario de supuestos "jugador"

Cada fila está aplicada como dice su subpunto, con dos excepciones:

- `StateDraft` solo cambia su Javadoc, porque M16.4 ya lo cubría.
- Las filas "Sin cambio" siguen sin cambio: el emisor del comando y quien entra al servidor no son participantes.

`ServerComponents` busca con `Bukkit.getEntity` filtrado a `LivingEntity`. `Perception.eligible` se retiró; su regla vive en `ParticipantPolicy`, y una prueba la compara con la de la 0.0.1.

### Desviaciones y decisiones

| # | Desviación o decisión | Motivo |
|---|---|---|
| 1 | Un jugador que encabeza la tabla de amenaza decide el objetivo como en la 0.0.1 aunque la política no lo admita (por ejemplo, en creativo): la captura lo descarta después, como antes. Una entidad que encabeza la tabla solo decide si la política la admite; si no, decide `Mob#getTarget()` | Paridad exacta: en la 0.0.1 un holder jugador no admitido dejaba al mob sin objetivo, no pasaba a `getTarget` (referencia `creative-holder`) |
| 2 | `participantGone` solo se llama para no jugadores (`EntityDeathEvent`, `EntityRemoveEvent`) | La muerte y la salida de un jugador ya tienen su camino en la 0.0.1; llamarlo también cambiaría los eventos por defecto |
| 3 | `ContextEvents.participantEvent`, `allyAssisted` y `memberEvent` reciben además el `ParticipantKind` del participante | El alias se crea en el evento; sin el tipo, una entidad recibiría un alias `player_N` |
| 4 | Las referencias de los listeners guardan los **efectos** en el motor (eventos, contexto pendiente, contadores de amenaza y ventanas de resultado), no cada llamada | `ContextEvents`, `ThreatEnrichment` y `OutcomeEvents` son clases finales; los efectos son el contenido observable que pide M17.0, e incluyen `dealt`, `taken` y las muertes |
| 5 | El banco guarda el SHA-256 de los 180 cuerpos y el texto completo de los 20 primeros de cada nivel | Tope de 2 MB para todas las referencias; los 180 textos completos lo superaban |
| 6 | El escudo se inyecta como `Predicate<LivingEntity>` en `CombatActionsAdapter` y `CombatOutcomeListener`; producción pasa `Blocking::blocking` | Probar el escudo de un no jugador sin un servidor (los componentes de objeto necesitan el registro de Paper) |
| 7 | Una asistencia de una entidad se describe como "helped an ally", no como "helped another player" | No afirmar a Jev que hay otro jugador |
| 8 | La especie lleva artículo "a" o "an" (`entity_1 is an enderman`) | Inglés correcto para Jev, sin coste apreciable |
| 9 | La especie del objetivo también aparece en el bloque del mob que lo tiene de objetivo | Mismo criterio que la desviación 9 de M16: ningún hecho del objetivo obliga a mirar otro arreglo |
| 10 | La etiqueta local `perceived players` de `inspect` se conserva y cuenta a todos los participantes | Salida local; renombrarla cambiaría la salida esperada de sesiones anteriores |
| 11 | La carpeta del paquete se llama `12-entidades` | Sigue la numeración de las carpetas, tras `11-vocabulario`. `15-propositos` usa el número de su sesión |
| 12 | `PerceiveNonTargets: true` en un perfil con el global en `false` es un error de configuración, con archivo y ruta. Un `Allow` de perfil con `profilesMayAllow: false` da un aviso y se ignora | Como pide M17.2 ("solo puede activarse si el global lo permite"; "ignora … con aviso") |
| 13 | La política usa una interfaz `ParticipantLookup`, con `MythicParticipantLookup` en producción y `NONE` en pruebas, y una `SelectorRegistry`, con `BukkitSelectorRegistry` en producción | D5 y P4: el adapter se prueba sin MythicMobs ni registros reales |
| 14 | Las pruebas cargan los atributos de Paper con un `RegistryAccess` de prueba servido por `ServiceLoader`, que solo conoce los atributos | Leer `Attribute.MAX_HEALTH` en las pruebas de entidades sin servidor |
| 15 | Tamaño de clases (C10, "unas 200 líneas"). Se partió `TargetRelationReader` (237 → 115 líneas) en `TargetTrend` (tendencia) y `TargetReach` (alcance), y el alcance vanilla vive en `VanillaMobReach`, así `PaperEntityReads` queda en 176. Pasan de 200 y se justifican así, con líneas totales, de Javadoc y de código:<br>`SemanticPhrases` 502 (44 / 407): tablas de frases fijas que el diseño pone ahí; M17 solo suma 17.<br>`FactsAssembler` 323 (39 / 260): percepción y ensamblado de hechos, una responsabilidad; M17 añade objetivos primero y `NearestK`.<br>`CombatContextListener` 290 (105 / 162) y `CombatOutcomeListener` 289 (84 / 186): un manejador por evento de Paper, con su Javadoc.<br>`ParticipantSelectors` 285 (51 / 213): un selector nativo por tipo, compilados juntos.<br>`ContextEvents` 261 (103 / 140): M17 suma `participantGone` y los tipos, sobre todo Javadoc.<br>`ParticipantPolicy` 256 (99 / 139): el orden de las siete reglas, documentado.<br>`engine/EncounterRegistry` 260 (256 antes de M17): `assign` salta un encuentro que lista al mob como participante (regla de bandos, M17.6).<br>`engine/Encounter` 256 (254 antes): `aliasFor` recibe el tipo de participante (M17.1) y los nombres pasan a `participantId`.<br>`ThePredictPlugin` 210 (209 antes): la raíz pasa los ajustes de participantes al binder (D10) y el aviso de entrada como `Component`.<br>`MythicProfileBinder` 245 (39 / 188).<br>`SemanticEncoder` 231 (37 / 177): M17 suma 9.<br>`CombatActionsAdapter` 214 (57 / 143): M17 suma 15, el predicado de escudo y la búsqueda de entidades.<br>`CombatCapture` 208 (37 / 158): M17 suma la admisión por política y los rivales. | Partir cualquiera de ellas separaría lo que el diseño asigna a una clase o dejaría clases de una función; ningún método pasa de 30 líneas (`ArchitectureRulesTest`) |
| 16 | La capacidad con `compact` en varios mundos es de **10 mobs**, no de 8 como decía la nota del orquestador: por tokens, 600 000 / (953 × 60) = 10,5, y por el tope global de solicitudes, 10 (mandan los dos a la vez) | El 8 es la estimación de diseño de M16.2, de ≈ 1 208 tokens; el 10 sale de los 953 tokens reales del re-banco. Con `full`, 5 en ambos casos |
| 17 | La casilla de M17.0 pedía grabar las referencias **antes** del primer cambio de código de M17. En el repositorio M17 empezó a las 20:36 y la grabación es de las 20:49. La casilla se reformuló: referencias grabadas con el código de M16 cerrado, en una copia aislada de `snapshots/M16` con solo los defaults de M16.10, y reproducibles | La intención del diseño era una paridad demostrada, y se cumple con más fuerza de la que daría una marca de tiempo. La copia aislada no contiene nada de M17, y la regrabación independiente del auditor desde la instantánea da los mismos bytes. El coordinador lo aceptó |

### Correcciones tras la auditoría (2026-09-29)

La auditoría volvió a grabar las referencias de M17.0 por su cuenta y obtuvo los mismos bytes. Tras estas correcciones las 81 referencias siguen iguales: `EntityGeneralizationParityTest` pasa y ninguna se regeneró.

| # | Hallazgo | Corrección |
|---|---|---|
| B1 | El alcance de un `Mob` no era el vanilla: hueco euclídeo en 3D y siempre 0,83, sin `ATTACK_RANGE` y sin mirar el ataque a distancia. Así se afirmaban a Jev "fuera del alcance" falsos (A6) | `TargetReach` reproduce `Mob.isWithinMeleeAttackRange` exacto (javap): caja ensanchada al vehículo e inflada solo en horizontal, cada eje por separado con solape vertical, y `ATTACK_RANGE × mobFactor` con mínimo. `VanillaMobReach` toma el arma como el servidor. Nada se afirma para `RangedEntity`, `Spellcaster`, blaze, breeze, creeper, ender dragon, ghast, guardian, shulker y warden, con arco, ballesta o tridente, o para un mob de MythicMobs sin perfil de ThePredict. `EntityReads.reachBlocks` pasa a `targetReach`. Pruebas en `TargetReachTest` (diagonal de ≈ 1,16, desnivel, lanza con mínimo, vehículo) y `PaperEntityReadsTest`. DT-37 ampliada |
| B2 | Matriz de la guía del autor con el alcance anterior | Fila corregida, con la columna "otra entidad viva" |
| B3 | La casilla y el relato de M17.0 decían que las referencias se grabaron antes del primer cambio de `src/main` (hay archivos de las 20:36; la grabación es de las 20:49) | Relato corregido. Tras la reauditoría, casilla reformulada y marcada (desviación 17, aceptada por el coordinador) |
| B4 | 17.2b no podía observar DT-39: el cazador ya admite `kind:MONSTER` | Lobo nuevo `TP_TagWatcher`, cuya única regla es `tag:mipack:presas`, y datapack `world/datapacks/mipack` en `12-entidades` (la etiqueta empieza con conejos y pasa a ovejas). `UserTestPackTest` lo vincula leyendo la etiqueta del JSON del paquete |
| B5 | 17.3 esperaba de `status` algo que no imprime | Cadenas literales de `StatusFormatter` (`Encounters: 2 active, …`) y de `InspectFormatter` (`Session: …, encounter <n>` y `Target: entity_1 (zombie) …`) en la guía por sesiones, el guion y el checklist |
| B6 | 17.5 usaba `HandItems` (obsoleto desde 1.21.5) y un zombi, que nunca levanta el escudo | `{equipment:{offhand:{id:"minecraft:shield",count:1}}}` (clave y ranura comprobadas con javap). javap sobre MythicMobs 5.13.1: no hay forma nativa de que un mob levante el escudo (solo `StopUsingItem`). El paso dice que la parte de DT-38 con escudo levantado exige una configuración propia (`startUsingItem(EquipmentSlot.OFF_HAND)`); DT-38 lo anota |
| B7 | La casilla "Sesión 17 escrita" solo es cierta tras B4–B6 | Queda marcada con B4–B6 hechas |
| B8 | Defaults duplicados (C13): 8 y 1–16 en `ParticipantDefaults`, `SURVIVAL`/`ADVENTURE` en `ParticipantPolicy` | Constantes únicas en `ParticipantRules` (`DEFAULT_PROFILES_MAY_ALLOW`, `DEFAULT_MAX_CANDIDATES_PER_MEMBER`, `MIN_`/`MAX_CANDIDATES_PER_MEMBER`); `ParticipantSettings` y `ParticipantDefaults` las usan, y `ParticipantPolicy.DEFAULT` se deriva de `ParticipantRules.DEFAULT` |
| B9 | Desviación 15 incompleta (C10) | `TargetRelationReader` partido; desviación 15 con todas las clases de más de 200 líneas y su motivo |
| — | `Blocking` redondeaba el retardo hacia arriba | `Math.round(s × 20f)`, como `BlocksAttacks.blockDelayTicks`; M17.4 y DT-38 corregidos. `BlockingTest` cambia a propósito: 0,26 s dan ahora 5 ticks, no 6, y 0,28 s dan 6 |
| — | Mobs de ThePredict sin facción que se admiten entre sí son rivales | Aviso en las dos guías, con la recomendación de una `Faction` común y `nearestOtherFactionMonsters`. El cazador del paquete tiene `Faction: hunters` y ese selector, así que en 17.6 los cazadores no se atacan |
| — | La seguridad de la desviación 1 no estaba escrita | En M17.4 y en la guía del autor |
| — | Pruebas | Filas de la matriz por objeto (`DRAWING_BOW`, `CROSSBOW_LOADED`, `CONSUMING`, `HOLDS_TOTEM`, `LONG_REACH_WEAPON`, `MACE_DIVE`, escudo) con dobles de `ItemStack` y componentes. `EntityCaptureTest` comprueba cuáles son los candidatos más cercanos. `ParticipantPolicyTest` prueba `isValid` en jugadores y entidades. La paridad comprueba además que los cuerpos reales de un duelo de facciones no llevan UUID ni facción |
| — | DT-31 en la tabla del registro y en el checklist | Resuelta en los dos |
| — | Capacidad de M16 con `compact` | Mandan a la vez el tope de tokens y el global de solicitudes (10), como en la guía del administrador |
| — | `sendMessage(String)` obsoleto en `ThePredictPlugin` | `SetupMessages.joinMessage` da un `Component` y se envía con `sendMessage(Component)` (la raíz no importa Adventure, D8) |
| — | `README.md` raíz con 1230 pruebas | Cifra actual |

### Pendiente

- **Sesión 17 del paquete** (sin clave, costo 0): 17.1–17.6 y 17.2b. Cierra DT-36 (tendencia por velocidad), DT-37 (alcance vanilla de un mob), DT-38 (escudo de un no jugador) y DT-39 (etiquetas tras `/minecraft:reload`; mientras tanto, las guías piden `/mm reload` después de recargar datapacks).
- **Usos obsoletos heredados, abiertos:** `PredictorPlaceholders.registerWith` usa `Placeholder.meta` (marcado para retirar en MythicMobs; DT-13, sin alternativa que no rompa D8). `sendMessage(String)` ya no se usa.
- **Para M18:** la política de entidades, los bandos y `participantGone` están listos. El duelo usa perfiles con `Entities.Allow: ['faction:…']`, como `TP_DuelRed` y `TP_DuelBlue` en `archivos/12-entidades/`.

Nota de implementación M26 (2026-10-07). La matriz de entidad no gana columnas nuevas en el código de M17. La rama de entidad de los patrones es el predicado `PatternRules.kites` y el barrido C21. No hay tokenizador de esqueleto en vivo. `SPRINT_*` y `CRIT_STRIKE` siguen en el enum; no se emiten para una entidad en esta entrega.
