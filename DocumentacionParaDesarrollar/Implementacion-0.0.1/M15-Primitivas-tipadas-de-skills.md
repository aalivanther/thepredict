# M15. Primitivas tipadas de skills

> **Estado:** Local listo — servidor pendiente (sesión 15 del paquete de pruebas; ver [Estado de implementación](#estado-de-implementación)) · **Entrega:** F (extensión de la 0.0.1) · **Depende de:** M03, M08, M10 · **Desbloquea:** M16, M17, M18 · **Diseño:** [plan §4, "Comportamientos, elecciones y combinaciones"](../Plan-Jev-y-combate-tactico.md#comportamientos-elecciones-y-combinaciones), [plan §5](../Plan-Jev-y-combate-tactico.md#5-control-de-movimiento-y-habilidades), [plan §6, "Estado semántico"](../Plan-Jev-y-combate-tactico.md#estado-semántico-no-numérico), [plan §9](../Plan-Jev-y-combate-tactico.md#9-configuración-ejemplo-y-migración) · **Reglas:** [00](00-Arquitectura-y-reglas.md) · **Dominio:** [01](01-Modelo-de-dominio.md)

## Objetivo

Que cada skill de un perfil diga, con tipos cerrados, **para qué sirve**: su papel táctico, cómo llega al objetivo, a qué distancia y en qué área, cuánto tarda en salir, cada cuánto se puede usar, cuánto daño hace, cuándo conviene y qué riesgo tiene. Con eso:

1. el binder detecta tácticas que nunca podrían lanzar su skill;
2. la elegibilidad y la revalidación previa al cast (M10) usan el alcance y la entrega reales de cada skill, no solo `MELEE_ATTACK`;
3. el codificador describe cada opción a Jev con frases fijas y precisas, en vez de la lista actual de `PredictValues` (que en el banco del 2026-09-29 ni siquiera llegó a Jev, ver M16);
4. el modo local (nivel 3) desempata con la intención declarada por el autor.

El autor lo declara en el perfil, con validación estricta; si no lo declara, se infiere de los `PredictValues` actuales, así que ningún perfil válido de la 0.0.1 deja de cargar ni cambia de comportamiento salvo donde este documento lo dice.

## Alcance

### Incluye

- Tipos de dominio: `SkillPurpose` y sus enums (`SkillRole`, `Delivery`, `CooldownClass`, `DamageTier`, `UseCondition`, `SkillRisk`, `PurposeOrigin`), la regla pura `PurposeRules`, la inferencia `PurposeInference` y las condiciones activas `ActiveUseConditions`.
- Sección `Purpose` en `Choices.<id>` del perfil (esquema 2, compatible hacia atrás) con validación M03.
- Resolución en el binder: inferencia desde `PredictValues`, clase de cooldown desde el `Cooldown` nativo, avisos de incoherencia y lint táctica–propósito.
- Elegibilidad con alcance (solo la regla que excluye tácticas imposibles) y el nuevo hecho `TargetRelation.grouped`.
- `HitChecks` por entrega: `damaging`, alcance por entrega, línea de visión declarada, `ignoresShield`.
- Frases fijas del propósito en `SemanticPhrases` y el criterio de cada opción construido desde el propósito (formato texto; el formato estructurado con contexto es de M16).
- Desempate del planificador local con `BestWhen` declarado.
- Ejemplo (`ThePredictGuardian`), paquete de pruebas, guía del autor y banco semilla regenerado.

### No incluye

- Descripciones de opción con "por qué ahora", instrucciones por tipo de pregunta, preguntas `noul`, deduplicación de opciones, banco etiquetado y re-banco de pago: M16.
- Participantes y objetivos no jugadores: M17. M15 no toca `Player` salvo para no romper firmas.
- Introspección de las mecánicas de una skill de MythicMobs para adivinar su entrega (se descarta, ver "Evidencia nativa").
- Ángulo de un cono, proyectiles con gravedad o velocidad y cancelación de skills en curso (plan §5: no se promete).
- Cambios en `ChoiceKind`, en `PredictValue` (se conserva cerrado con sus 13 valores) o en el trigger `THEPREDICT`.

## Base existente

| Archivo | Acción |
|---|---|
| `src/main/java/akyrion/thepredict/domain/PredictValue.java` | Se conserva sin valores nuevos. Pasa a ser la fuente de la inferencia y la forma corta heredada. |
| `src/main/java/akyrion/thepredict/domain/SkillChoice.java` | Gana el componente `SkillPurpose purpose`. Se mantiene un constructor de compatibilidad de seis argumentos que infiere el propósito (W5). |
| `src/main/java/akyrion/thepredict/domain/Eligibility.java` | `eligible` gana el conjunto de choices fuera de alcance y la configuración de movimiento para excluir tácticas que nunca lanzan (M15.5). |
| `src/main/java/akyrion/thepredict/domain/LocalPlanner.java` | Gana las condiciones de uso activas para desempatar (M15.8). |
| `src/main/java/akyrion/thepredict/domain/MobFacts.java`, `TargetRelation.java` | `MobFacts` gana `choicesOutOfReach`; `TargetRelation` gana `grouped` (M15.5, M15.8). |
| `src/main/java/akyrion/thepredict/config/ProfileParser.java`, `ProfileSchema.java` | Nueva sección `Purpose` en cada choice (M15.2), en una clase auxiliar nueva `PurposeParser` para no pasar de C10. |
| `src/main/java/akyrion/thepredict/adapter/server/binding/MythicProfileBinder.java`, `SkillMetadata.java`, `BindingWarnings.java` | Resolución del propósito, clase de cooldown nativa y avisos (M15.3, M15.4). `PredictValues` deja de ser obligatorio si hay `Purpose`. |
| `src/main/java/akyrion/thepredict/adapter/server/context/ReadyTactics.java`, `FactsAssembler.java`, `TargetRelationReader.java` | Estado de alcance por choice y `grouped` en cada captura (M15.5). |
| `src/main/java/akyrion/thepredict/adapter/server/execution/HitChecks.java`, `CombatActionsAdapter.java` | Revalidación por entrega (M15.6). |
| `src/main/java/akyrion/thepredict/adapter/jev/SemanticPhrases.java`, `QuestionBuilder.java` | Frases del propósito y criterio desde el propósito (M15.7). |
| `src/main/resources/examples/AI/ThePredictGuardian.yml`, `examples/MythicMobs/Skills/ThePredictGuardianSkills.yml`, `examples/MythicMobs/Mobs/ThePredictGuardian.yml` | `Purpose` declarado en las tres choices y una cuarta skill de área (M15.9). |
| `src/test/java/akyrion/thepredict/adapter/jev/ScenarioFacts.java`, `ScenarioSeedBank.java`; `src/test/resources/scenarios/seed/*.json` | El ejemplo declara sus propósitos, así que el banco semilla ya describe las skills sin binder; se regeneran los seis archivos (M15.9). |
| `Guias/Guia-del-autor.md`; `paquete-pruebas/archivos/03-guardian/…` | Sección nueva de propósitos; pack del guardián actualizado (M15.9). |

## Evidencia nativa y decisión "nativo primero"

Comprobado con `javap` sobre `VersionsOfJars/MythicMobsPremium-5.13.1-SNAPSHOT.jar` (el que usa `build.gradle.kts`) y `paper-api-26.2.build.129-stable.jar` de la caché de Gradle, sin servidor:

| Qué necesita ThePredict | Qué ofrece MythicMobs 5.13.1 | Decisión |
|---|---|---|
| Papel táctico, entrega, alcance, área, cuándo conviene y riesgos | Nada: `Skill` (`io.lumine.mythic.api.skills.Skill`) solo expone `getInternalName`, `getConfig`, `getConditions`, `getConditionsTarget`, `getConditionsTrigger`, `onCooldown`, `isUsable` y `executeIfUsable`. `AbstractSkill` añade `getTargeter(): Optional<SkillTargeter>` y `MetaSkill.getBaseSkill()` devuelve la cola interna de mecánicas (`MetaSkill$SkillAction`, paquete `core`) | El autor lo declara. Adivinar la entrega recorriendo la cola de mecánicas (`DamageMechanic`, `ProjectileMechanic`, `MissileMechanic`, `LeapMechanic`, `LungeMechanic`, `HealMechanic`, `ShieldMechanic`, `StunMechanic`, `SummonMechanic`, `AuraMechanic`…) exigiría leer clases internas de `core`, seguir `skill{s=…}`, `delay`, `randomskill` y targeters por línea, y aun así no daría "cuándo conviene". Se descarta (A1: no hace la decisión más directa y es frágil) |
| Cada cuánto se puede usar | `AbstractSkill.getCooldown(): PlaceholderDuration` (`IPlaceholder.isStatic()`, `isPresent()`; `IGenericPlaceholder.get(): Duration`; `Duration.getSeconds()` y `getTicks()`: `PlaceholderDuration` no tiene `getSeconds()`), `getCharges()`, `getCooldownMode()`; `onCooldown(SkillCaster)` ya se usa | Se **lee**, no se declara: la clase de cooldown sale del `Cooldown` nativo más el `CD` de compatibilidad (M15.3). Un `Cooldown` sin valor fijo legible (placeholder dinámico, `null` o `!isPresent()`) da `UNKNOWN` |
| Etiquetas cortas ya escritas por autores | `PredictValues` en el YAML de la skill, que el binder ya lee con `skill.getConfig()` | Se conservan como fuente de inferencia (M15.3) |
| Alcance real al lanzar | Condiciones de la skill (`targetwithin`, `lineofsight`…), ya evaluadas sin efectos por `SkillConditions` (M10.3) | Se siguen evaluando; el alcance declarado solo añade la comprobación que MythicMobs no hace por su cuenta (M15.6) |
| Línea de visión | Paper `LivingEntity#hasLineOfSight(Entity)` | Se usa solo si la skill declara el riesgo `NEEDS_LINE_OF_SIGHT` (M15.6) |

**Dónde se declara.** En el perfil de ThePredict (`Choices.<id>.Purpose`), no en el YAML de la skill de MythicMobs. Motivos: (1) la validación estricta con archivo y ruta, unidades y un solo lugar por default es la de M03 (C13), y el binder solo puede avisar sobre archivos de MythicMobs; (2) el alcance y el tiempo de preparación pueden depender del mob que la lanza (caja de colisión, condiciones), así que una misma skill puede servir distinto en dos perfiles; (3) ThePredict no amplía su huella en los archivos de MythicMobs más allá del `PredictValues` heredado; (4) MythicScript 2077 no necesita vocabulario nuevo, porque los perfiles no son archivos de MythicMobs. Un perfil que reutiliza la misma skill en varias choices puede usar anclas YAML dentro del mismo archivo. Decisión registrada como DT-32 (ver "Cambios propuestos a 00/01").

## Subpuntos

### M15.1 Taxonomía de dominio: `SkillPurpose`

- **Propósito:** tener en `domain` un vocabulario cerrado y validado de lo que hace una skill, sin números crudos hacia Jev y sin tipos de Bukkit (A2, D1).
- **Diseño:**

  ```java
  enum SkillRole { DAMAGE, CONTROL, GAP_CLOSE, ESCAPE, DEFENSE, SUSTAIN, BUFF, DEBUFF,
                   SUMMON, ZONE, INTERRUPT, FINISHER, OPENER }
  enum Delivery { MELEE, PROJECTILE, HITSCAN, AREA_AROUND_SELF, AREA_AT_TARGET, CONE, SELF }
  enum CooldownClass { UNKNOWN, NONE, SHORT, MEDIUM, LONG }
  enum DamageTier { NONE, UNRATED, LOW, MEDIUM, HIGH }
  enum UseCondition { TARGET_LOW_HEALTH, TARGET_SHIELDING, TARGET_FLEEING, SELF_LOW_HEALTH,
                      ENEMIES_GROUPED, TARGET_AT_RANGE, TARGET_IN_MELEE }
  enum SkillRisk { LEAVES_CASTER_EXPOSED, ROOTS_CASTER, SELF_DAMAGE, NEEDS_LINE_OF_SIGHT,
                   SLOW_PROJECTILE, BLOCKED_BY_SHIELD, HITS_ALLIES }
  enum PurposeOrigin { DECLARED, INFERRED, SKILL }   // SKILL reservado: aún no se produce (ver "Riesgos")

  record SkillPurpose(SkillRole primary, Set<SkillRole> roles, Delivery delivery,
                      OptionalDouble reachBlocks, OptionalDouble radiusBlocks, int windUpTicks,
                      CooldownClass cooldown, DamageTier damage, Set<UseCondition> bestWhen,
                      Set<SkillRisk> risks, boolean ignoresShield, PurposeOrigin origin) {}
  ```

  Significado de cada papel (la frase exacta para Jev está en M15.7):

  | `SkillRole` | Significa | Ejemplo MythicMobs |
  |---|---|---|
  | `DAMAGE` | Su fin principal es quitar vida | `damage{a=6} @target` |
  | `CONTROL` | Aturde, ralentiza, empuja o inmoviliza | `stun`, `potion{type=SLOWNESS}`, `throw` |
  | `GAP_CLOSE` | Acorta la distancia de golpe | `leap`, `lunge` hacia el objetivo |
  | `ESCAPE` | Aleja al lanzador del peligro de golpe | `leap` hacia atrás, `teleport` |
  | `DEFENSE` | Reduce el daño que recibe el lanzador | `shield`, `potion{type=RESISTANCE}` @self |
  | `SUSTAIN` | Recupera vida del lanzador | `heal @self` |
  | `BUFF` | Mejora al lanzador o a sus aliados | `potion{type=STRENGTH}` @self |
  | `DEBUFF` | Empeora al objetivo | veneno, `bad_omen`, debilidad |
  | `SUMMON` | Crea aliados | `summon` |
  | `ZONE` | Niega un área durante un tiempo | `summonAreaEffectCloud`, `totem` |
  | `INTERRUPT` | Corta lo que hace el objetivo (beber, tensar un arco) | empuje, `stun` breve |
  | `FINISHER` | Remata a un objetivo debilitado | golpe fuerte con condición de salud del objetivo |
  | `OPENER` | Abre el combate con ventaja | emboscada, primer golpe cargado |

  | `Delivery` | Significa | Alcance y área |
  |---|---|---|
  | `MELEE` | Golpe a un objetivo al alcance de la mano | `reachBlocks` obligatorio (hueco entre cajas) |
  | `PROJECTILE` | Proyectil que viaja y puede fallar | `reachBlocks` opcional |
  | `HITSCAN` | Efecto a distancia que impacta al instante | `reachBlocks` opcional |
  | `AREA_AROUND_SELF` | Área centrada en el lanzador | `radiusBlocks` obligatorio (el `r` del targeter de radio de MythicMobs, medido entre posiciones; ver M15.6); sin `reachBlocks` |
  | `AREA_AT_TARGET` | Área centrada en el objetivo | `radiusBlocks` obligatorio; `reachBlocks` opcional |
  | `CONE` | Barrido frente al lanzador | `reachBlocks` obligatorio (longitud del cono) |
  | `SELF` | Solo afecta al lanzador | sin alcance ni área |

  Invariantes (constructor canónico, C2; mensajes con el nombre del campo para que `ProfileSchema.yamlNames` los traduzca):

  | Regla | Límite |
  |---|---|
  | `roles` contiene `primary`; tamaño 1 a `MAX_ROLES = 3` | — |
  | `reachBlocks` finito en `(0, MAX_REACH_BLOCKS = 64]` | Presente en `MELEE` y `CONE`; ausente en `SELF` y `AREA_AROUND_SELF` |
  | `radiusBlocks` finito en `(0, MAX_RADIUS_BLOCKS = 16]` | Presente exactamente en `AREA_AROUND_SELF` y `AREA_AT_TARGET` |
  | `windUpTicks` en `[0, MAX_WIND_UP_TICKS = 100]` | — |
  | `damage != NONE` si `roles` contiene `DAMAGE` o `FINISHER` | — |
  | `bestWhen` y `risks` con como mucho `MAX_BEST_WHEN = 4` y `MAX_RISKS = 4` | Menos texto irrelevante para Jev ([jaggedness §5](https://docs.typesafe.ai/model-jaggedness/jev-1.13#large-state-full-of-irrelevant-detail)) |
  | `ignoresShield` y `risks.contains(BLOCKED_BY_SHIELD)` no pueden ser ambos verdaderos | — |
  | Colecciones copiadas con `Set.copyOf` (orden de presentación: `EnumSet`, como hace el codificador hoy) | — |

  Métodos derivados, puros y sin asignaciones evitables (C12): `boolean damaging()` ("es dañina": `roles` contiene `DAMAGE` o `FINISHER`, o `damage` es `LOW`, `MEDIUM`, `HIGH` o `UNRATED`; solo lo usa `HitChecks` con propósitos `DECLARED`, M15.6); `boolean authored()` (`origin` es `DECLARED` o `SKILL`: las reglas nuevas de M15.4, M15.5 y M15.8 solo se aplican a propósitos escritos por el autor); `boolean aimedAtTarget()` (entrega distinta de `SELF` y `AREA_AROUND_SELF`); `SkillPurpose withCooldown(CooldownClass)` (el binder completa la clase nativa).

  `SkillChoice` pasa a `SkillChoice(ChoiceId id, ChoiceKind kind, Optional<String> mythicSkillId, Set<PredictValue> values, boolean requiresTarget, int extraCooldownTicks, SkillPurpose purpose)`. Invariante nueva: `VANILLA_MELEE` exige `purpose.delivery() == MELEE` y `DAMAGE` en `roles`. El constructor de seis argumentos se conserva como compatibilidad y llama a `PurposeInference.infer(kind, values, requiresTarget, MovementSettings.DEFAULTS)` (M15.3); se retira cuando ninguna prueba lo use (W5).
- **Archivos:** crear `domain/SkillRole.java`, `Delivery.java`, `CooldownClass.java`, `DamageTier.java`, `UseCondition.java`, `SkillRisk.java`, `PurposeOrigin.java`, `SkillPurpose.java`; modificar `domain/SkillChoice.java`; pruebas `domain/SkillPurposeTest.java`, `SkillChoiceTest.java`.
- **Reglas:** A2, D1, C2, C3, C4 (`OptionalDouble` modela una ausencia del dominio), C5 (`Blocks`, `Ticks`), C9, P1.
- **Pruebas:** cada invariante en su frontera (reach 64 sí, 64,0001 no; radio en `CONE` rechazado; `DAMAGE` con `NONE` rechazado; cuatro condiciones sí, cinco no; `ignoresShield` con `BLOCKED_BY_SHIELD` rechazado); `damaging()` y `aimedAtTarget()` para las siete entregas; `VANILLA_MELEE` con entrega distinta de `MELEE` rechazado; igualdad y copia defensiva.
- **Aceptación:**
  - [x] Siete enums y `SkillPurpose` en `domain`, sin imports fuera de `java.*` (`ArchitectureRulesTest`).
  - [x] Todas las invariantes de la tabla con prueba de frontera.
  - [x] `SkillChoice` con propósito obligatorio y constructor de compatibilidad documentado.

### M15.2 Declaración en el perfil: `Choices.<id>.Purpose`

- **Propósito:** que el autor declare el propósito con la sintaxis y la validación de M03, sin romper perfiles existentes.
- **Diseño:**
  - Esquema 2 ampliado sin cambiar `schemaVersion` (la sección es opcional, así que todo perfil válido de la 0.0.1 sigue siendo válido). `ProfileSchema.CHOICE` gana `Purpose`; nuevo `ProfileSchema.PURPOSE = Set.of("Roles", "Delivery", "Reach", "Radius", "WindUp", "Damage", "BestWhen", "Risks", "IgnoresShield")`. Una clave desconocida es un error con ruta (C13).
  - Formato (nombres en PascalCase como el resto del perfil; unidades documentadas: bloques y ticks):

    ```yaml
    Choices:
      melee:
        MythicID: ThePredictGuardianMelee
        TypeOfMythicID: SKILL
        Purpose:
          Roles: [DAMAGE]            # 1 a 3; el primero es el principal
          Delivery: MELEE            # MELEE, PROJECTILE, HITSCAN, AREA_AROUND_SELF, AREA_AT_TARGET, CONE, SELF
          Reach: 2.5                 # bloques; obligatorio en MELEE y CONE
          WindUp: 5                  # ticks entre el cast y el golpe; 0 por defecto
          Damage: MEDIUM             # NONE, LOW, MEDIUM, HIGH; obligatorio con DAMAGE o FINISHER
          BestWhen: [TARGET_IN_MELEE]
          Risks: []
    ```

  - `PurposeParser` (clase de paquete nueva en `config`) lee la sección con `ConfigurationValues` y construye `SkillPurpose` con `origin = DECLARED` y `cooldown = UNKNOWN` (lo completa el binder). Validaciones, todas `ConfigurationException` con `archivo` y ruta `Choices.<id>.Purpose.<Clave>`:

    | Clave | Tipo | Regla | Mensaje (inglés, como el resto de errores de carga) |
    |---|---|---|---|
    | `Roles` | lista de nombres | Obligatoria con `Purpose`; 1 a 3; sin repetir; valores de `SkillRole` sin distinguir mayúsculas | `must list 1 to 3 roles, had N` / `unknown role 'X'; known: …` / `lists 'X' twice` |
    | `Delivery` | texto | Obligatoria; valor de `Delivery` | `unknown delivery 'X'; known: …` |
    | `Reach` | decimal, bloques | `(0, 64]`; obligatoria en `MELEE`/`CONE`; prohibida en `SELF`/`AREA_AROUND_SELF` | `is required for Delivery MELEE` / `must be absent for Delivery SELF` |
    | `Radius` | decimal, bloques | `(0, 16]`; obligatoria en `AREA_*`; prohibida en el resto | igual |
    | `WindUp` | entero, ticks | `[0, 100]`; default 0 | `must be between 0 and 100 ticks, was N` |
    | `Damage` | texto | `NONE`, `LOW`, `MEDIUM`, `HIGH` (`UNRATED` solo lo produce la inferencia); obligatoria con `DAMAGE` o `FINISHER`, que exigen un valor distinto de `NONE` | `is required when Roles has DAMAGE or FINISHER` |
    | `BestWhen` | lista | 0 a 4 valores de `UseCondition`, sin repetir | como `Roles` |
    | `Risks` | lista | 0 a 4 valores de `SkillRisk`, sin repetir | como `Roles` |
    | `IgnoresShield` | booleano | default `false`; incompatible con `Risks: [BLOCKED_BY_SHIELD]` | `cannot be true while Risks has BLOCKED_BY_SHIELD` |

  - `Kind: VANILLA_MELEE`: `Purpose` es opcional; si está, `Delivery` debe ser `MELEE` y `Roles` incluir `DAMAGE`; `Reach` por defecto es `Movement.MeleeReach` del perfil (el alcance que ya usa M10.9). Sin `Purpose`, el parser infiere el propósito completo de inmediato (M15.3), porque no necesita MythicMobs.
  - `Kind: MYTHIC_SKILL` sin `Purpose`: el parser deja un propósito provisional `PurposeInference.infer(kind, Set.of(), requiresTarget, movement)` con `origin = INFERRED`; el binder lo sustituye con los `PredictValues` reales (M15.3). Así `TacticalProfile` siempre es válido, también sin binder (herramientas y pruebas).
  - Aviso (no error) con `ConfigProblem.warning` si `Reach` supera `Movement.RangedReach`: "is beyond RangedReach; Jev reads it as beyond ranged reach".
  - Los límites viven en `SkillPurpose` (un solo lugar por default, C13); el parser los lee de sus constantes para los mensajes.
- **Archivos:** crear `config/PurposeParser.java`; modificar `config/ProfileParser.java` (llamada desde `choices`), `config/ProfileSchema.java` (`CHOICE`, `PURPOSE`, nombres de dominio para `yamlNames`: `reachBlocks`→`Reach`, `radiusBlocks`→`Radius`, `windUpTicks`→`WindUp`); pruebas en `config/ProfileLoaderTest.java` y nueva `config/PurposeParserTest.java`.
- **Reglas:** C13, C5, C7 (configuración inválida al cargar es la única excepción), D3, W3.
- **Pruebas:** un caso por fila de la tabla con su mensaje y ruta exacta; perfil sin `Purpose` idéntico al de hoy (mismos objetos salvo el propósito inferido); `VANILLA_MELEE` con y sin `Purpose`; mayúsculas indiferentes en valores y exactas en claves; aviso de `Reach` sobre `RangedReach`; la guía del autor carga (`UserGuidesTest`).
- **Aceptación:**
  - [x] Todo perfil válido de la 0.0.1 sigue cargando sin avisos nuevos.
  - [x] Cada error de la tabla nombra archivo y ruta completa.
  - [x] `ProfileParser` sigue por debajo de 200 líneas (C10) gracias a `PurposeParser`.

### M15.3 Resolución en el binder: inferencia, cooldown nativo y coherencia

- **Propósito:** que todo perfil vinculado tenga un propósito completo y honesto, aunque el autor no declare nada, sin inventar lo que MythicMobs no dice.
- **Diseño:**
  - `domain.PurposeInference` (clase `final` sin estado, P1):

    ```java
    static SkillPurpose infer(ChoiceKind kind, Set<PredictValue> values, boolean requiresTarget,
                              MovementSettings movement)
    static final double DEFAULT_AREA_RADIUS_BLOCKS = 3.0;
    ```

    Reglas, en este orden:

    | Campo | Regla de inferencia |
    |---|---|
    | `primary` | `HEALS_CASTER` → `SUSTAIN`; si no, `DEFENSE` → `DEFENSE`; si no, `SUMMONS_ALLY` → `SUMMON`; si no, si el conjunto es **dañino heredado** (abajo) → `DAMAGE`; si no, `APPLIES_POISON` o `APPLIES_BAD_OMEN` → `DEBUFF`; si no (conjuntos sin etiqueta de ataque, daño, curación, defensa, invocación ni efecto, como `[SINGLE_TARGET]`, `[AREA_TARGET]` o `[IGNORES_SHIELD]`): `requiresTarget` → `DEBUFF` ("actúa sobre el objetivo") y, sin objetivo, `BUFF`; en los dos casos con `damage = NONE`. `VANILLA_MELEE` → `DAMAGE` |
    | `roles` | `primary` más `DEBUFF` si hay veneno o mal presagio, más `DEFENSE` si hay `DEFENSE` y no es el principal; como mucho 3, en orden de `SkillRole` |
    | `delivery` | `MELEE_ATTACK` → `AREA_AROUND_SELF` con `AREA_TARGET`, si no `MELEE`; `RANGED_ATTACK` → `AREA_AT_TARGET` con `AREA_TARGET`, si no `PROJECTILE`; sin ataque: `requiresTarget` → `AREA_AT_TARGET` o `HITSCAN`; sin objetivo → `AREA_AROUND_SELF` o `SELF`. `VANILLA_MELEE` → `MELEE` |
    | `reachBlocks` | `MELEE` → `movement.meleeReachBlocks()` (lo que hoy aplica M10 a `MELEE_ATTACK`); el resto vacío, **para que la revalidación previa al cast no cambie** en perfiles sin `Purpose` |
    | `radiusBlocks` | `AREA_AROUND_SELF` → `meleeReachBlocks`; `AREA_AT_TARGET` → `DEFAULT_AREA_RADIUS_BLOCKS` |
    | `damage` | el mayor de `HIGH_DAMAGE`, `MEDIUM_DAMAGE`, `LOW_DAMAGE`; si no hay ninguno y el conjunto es dañino heredado (tiene `MELEE_ATTACK` o `RANGED_ATTACK`), `UNRATED`; en cualquier otro caso, `NONE` |
    | `windUpTicks` | 0 (la frase se omite cuando el origen es `INFERRED`, M15.7) |
    | `bestWhen` | `HEALS_CASTER` → `SELF_LOW_HEALTH`; `IGNORES_SHIELD` → `TARGET_SHIELDING`; `AREA_TARGET` → `ENEMIES_GROUPED`; `RANGED_ATTACK` → `TARGET_AT_RANGE`; `VANILLA_MELEE` → `TARGET_IN_MELEE` |
    | `risks` | vacío |
    | `ignoresShield` | `IGNORES_SHIELD` |
    | `cooldown` | `UNKNOWN` (lo completa el binder) |

    **"Dañino heredado"** es exactamente el predicado de la 0.0.1 en `HitChecks.DAMAGE_VALUES`: `PurposeInference.legacyDamaging(Set<PredictValue> values)` es verdadero si `values` contiene alguno de `MELEE_ATTACK`, `RANGED_ATTACK`, `LOW_DAMAGE`, `MEDIUM_DAMAGE` o `HIGH_DAMAGE`. `PurposeInference.legacyMeleeReach(values)` es `values.contains(MELEE_ATTACK)`. Los dos se usan para que un propósito `INFERRED` nunca cambie la revalidación previa al cast (M15.6): para él, `HitChecks` aplica los predicados heredados sobre `values`, no los derivados del propósito.

  - Binder (`MythicProfileBinder.bindChoices`), por cada choice `MYTHIC_SKILL`:
    1. `PredictValues`: si la choice declara `Purpose`, pueden faltar (`SkillMetadata.predictValuesOrEmpty`); si no, siguen siendo obligatorios con el mensaje de hoy.
    2. Propósito: declarado → se conserva; inferido → `PurposeInference.infer(kind, values, requiresTarget, profile.movement())`.
    3. `ignoresShield |= values.contains(IGNORES_SHIELD)` también para los declarados (la etiqueta corta ya autorizaba atacar un escudo en M10; no se pierde).
    4. Clase de cooldown: `SkillMetadata.cooldownClass(Skill, int extraCooldownTicks)`, en este orden: (a) la skill no es `AbstractSkill` → `UNKNOWN`; (b) su YAML no tiene la clave `Cooldown` (`SkillMetadata.hasNativeCooldown` falso) → `seconds = 0`; (c) `AbstractSkill.getCooldown()` (`PlaceholderDuration`) es `null`, `!isPresent()` o `!isStatic()` → `UNKNOWN` (hay clave pero no un valor fijo legible); (d) si no, `Duration d = getCooldown().get()` (`IGenericPlaceholder.get()`, solo con `isStatic()`); `d == null` → `UNKNOWN`; si no, `seconds = d.getSeconds()`. `PlaceholderDuration` no tiene `getSeconds()`: lo tiene `io.lumine.mythic.bukkit.utils.numbers.Duration` (javap). Después `seconds += extraCooldownTicks / 20.0`. Umbrales: `0` → `NONE`; `≤ 3` → `SHORT`; `≤ 10` → `MEDIUM`; más → `LONG`. `VANILLA_MELEE` → `SHORT` (su ritmo lo marcan la invulnerabilidad de 10 ticks y el `CD`, M10.9) salvo `CD` ≥ 60 ticks → `MEDIUM`.
    5. Coherencia con `PredictValues` (avisos `BindingProblem` de severidad `WARNING`, nunca errores: el propósito declarado manda y la etiqueta corta queda como historia): `MELEE_ATTACK` con entrega fuera de `MELEE`, `CONE` o `AREA_AROUND_SELF`; `RANGED_ATTACK` con `MELEE`; `HEALS_CASTER` sin `SUSTAIN`; `SUMMONS_ALLY` sin `SUMMON`; tier distinto del declarado. Formato: `Choices.<id>.Purpose: PredictValues say MELEE_ATTACK but Delivery is PROJECTILE; the declared purpose is used`.
  - `BoundProfile` gana la invariante "toda choice tiene `purpose.cooldown()` resuelto salvo `UNKNOWN` por placeholder", probada en `BoundProfileTest`.
- **Archivos:** crear `domain/PurposeInference.java`; modificar `adapter/server/binding/MythicProfileBinder.java`, `SkillMetadata.java` (`predictValuesOrEmpty`, `cooldownClass`), `BindingWarnings.java` (`purpose`); pruebas `domain/PurposeInferenceTest.java`, `adapter/server/binding/MythicProfileBinderSuiteTest.java`, dobles en `MythicFixtures`/`MythicYaml` con `Cooldown` estático y con placeholder.
- **Reglas:** A1, A7 (cada aviso con causa), D5, D7, I7, C13.
- **Pruebas:** tabla de inferencia completa: los 13 `PredictValue` solos y **todos** los subconjuntos (2^13 = 8 192) con `requiresTarget` verdadero y falso, comprobando que el propósito es válido y que `HitChecks.damaging` y el alcance cuerpo a cuerpo coinciden con el predicado de la 0.0.1 (prueba de propiedad, milisegundos); `[SINGLE_TARGET]`, `[AREA_TARGET]` e `[IGNORES_SHIELD]` dan `DEBUFF`/`BUFF` con `NONE`; cooldown sin clave, 0, 3, 3,05, 10, 10,05 s, placeholder, `null` y `!isPresent()`; `CD` que cruza un umbral; `IGNORES_SHIELD` sobre un propósito declarado; cada aviso de coherencia con su ruta; skill sin `PredictValues` pero con `Purpose` vincula; sin ninguno de los dos, error de hoy.
- **Aceptación:**
  - [x] Ningún perfil vinculado queda con un propósito provisional.
  - [x] Perfiles sin `Purpose`: mismas choices listas, mismas revalidaciones y mismos casts en `TacticalExecutionABTest` y `CastSemanticsTest` que antes de M15.
  - [x] Clase de cooldown leída del `Cooldown` nativo, nunca declarada por el autor.

### M15.4 Coherencia estática táctica–propósito

- **Propósito:** avisar al autor, al vincular, de tácticas cuyo movimiento hace imposible o improbable lanzar su skill (por ejemplo `KEEP_DISTANCE` con un golpe de 2,5 bloques).
- **Diseño:**
  - Regla pura `domain.PurposeRules`:

    ```java
    enum PurposeFit { FITS, CASTS_ONLY_IF_TARGET_COMES, NEVER_CASTS }
    static PurposeFit fit(Movement movement, SkillPurpose purpose, MovementSettings movement)
    static boolean closesDistance(Movement movement, SkillPurpose purpose, MovementSettings settings)
    ```

    Con `r` el alcance efectivo (`reachBlocks`, o `radiusBlocks` en `AREA_AROUND_SELF`); sin alcance (`SELF`, o entrega a distancia sin `Reach`) → `FITS`.

    | Movimiento | `FITS` si | `CASTS_ONLY_IF_TARGET_COMES` si | `NEVER_CASTS` si |
    |---|---|---|---|
    | `APPROACH`, `FLANK` | `r ≥ meleeReach − 0,25` (la meta se detiene en `meleeReach`) | `r < meleeReach − 0,25` | — |
    | `KEEP_DISTANCE` | `r ≥ bandOuter` | `bandInner ≤ r < bandOuter` | `r < bandInner` |
    | `RETREAT` | `r ≥ rangedReach` | resto | — |
    | `HOLD` | `r ≥ rangedReach` | resto | — |
    | `NONE` | siempre (movimiento nativo desconocido) | — | — |

    `closesDistance` es verdadero para `APPROACH` y `FLANK`, y para `KEEP_DISTANCE` con `r ≥ bandOuter`.
  - `BindingWarnings.tacticFit(profile, problems)` evalúa **solo las tácticas cuya choice tiene un propósito con `authored()` verdadero** (`DECLARED` hoy; `SKILL` cuando exista). Con un propósito `INFERRED` no evalúa nada, así que un perfil sin `Purpose` no gana ningún aviso ni cambia en nada (el alcance inferido de `MELEE_ATTACK` existe solo para reproducir la revalidación de hoy). Añade un `WARNING` (la única severidad no bloqueante de `BindingProblem.Severity`) por táctica `NEVER_CASTS`, con el texto `Tactics.<id>: Movement KEEP_DISTANCE keeps the target beyond Band.Inner 5 but skill 'melee' reaches 2.5; this tactic never casts`, y otro por táctica `CASTS_ONLY_IF_TARGET_COMES` (`… casts only if the target comes within reach`).
- **Archivos:** crear `domain/PurposeRules.java` (con `PurposeFit` anidado); modificar `adapter/server/binding/BindingWarnings.java`, `MythicProfileBinder.java`; pruebas `domain/PurposeRulesTest.java`, `MythicProfileBinderSuiteTest.java`.
- **Reglas:** A7, P1, C10.
- **Pruebas:** tabla completa en sus fronteras con los defaults de `MovementSettings` (2,5; 5; 9; 16) y con valores de perfil; un aviso por táctica declarada; ninguno para perfiles sin `Purpose`, incluido un `KEEP_DISTANCE` con una skill `MELEE_ATTACK` inferida (lista de problemas idéntica a la de la 0.0.1).
- **Aceptación:**
  - [ ] `/thepredict status` (contadores del `Catalog`) y el registro de carga muestran el aviso con la ruta de la táctica. (El diseño citaba un subcomando de preparación que `/thepredict` no tiene: el aviso sale en el registro de carga, una línea por problema, y cuenta en el `Catalog` de `/thepredict status`; en local lo comprueba `UserTestPackTest.thePurposeStageWarnsThatKeepDistanceWithTheDeclaredMeleeSkillNeverCasts`. Pendiente de servidor, sesión 15.2.)
  - [x] Ninguna táctica se rechaza por este lint (solo aviso).

### M15.5 Elegibilidad con alcance y hecho `grouped`

- **Propósito:** no ofrecer a Jev ni al planificador local tácticas que en este momento no pueden lanzar su skill nunca, y capturar el hecho que necesita `ENEMIES_GROUPED`.
- **Diseño:**
  - Captura (`FactsAssembler.mobFacts`): por cada choice con alcance y objetivo percibido, `ReadyTactics.reach(...)` decide si está dentro, con la misma geometría que la revalidación previa al cast (M15.6): hueco entre cajas para `MELEE` y `CONE`, distancia entre posiciones estrictamente menor que el radio para `AREA_AROUND_SELF` (como el `r` de `@PlayersInRadius`; revisión tras la auditoría, desviación 22), distancia entre centros para las entregas a distancia con `Reach`. El resultado entra en `MobFacts.choicesOutOfReach: Set<ChoiceId>` (vacío sin objetivo). Coste: como mucho `choices` (≤ 255, en la práctica < 10) cálculos O(1) por miembro y captura, sin línea de visión ni ruta.
  - `Eligibility.eligible(profile, active, readyChoices, availableMovements, choicesOutOfReach)`: una táctica con choice fuera de alcance deja de ser elegible **solo** si su propósito cumple `authored()` y `PurposeRules.fit(...) == NEVER_CASTS` para su movimiento. Con un propósito `INFERRED` la choice nunca entra en `choicesOutOfReach` (el adapter ni siquiera calcula su alcance), así que la elegibilidad de un perfil sin `Purpose` es la de la 0.0.1 byte a byte. Las demás siguen elegibles (esperar a que el objetivo llegue, `HOLD`, es una táctica legítima); M16 las marca como "poco oportunas ahora" en su descripción. La sobrecarga sin `choicesOutOfReach` se conserva y equivale a un conjunto vacío.
  - `TargetRelation` gana `boolean grouped`: el objetivo tiene al menos otro participante percibido a `GROUP_RADIUS_BLOCKS = 4.0` bloques o menos (distancia entre centros al cuadrado, O(participantes ≤ 16) por mob con objetivo). Frase para Jev (la añade el codificador en `target.facts`): `target stands close to other enemies`.
- **Archivos:** modificar `domain/Eligibility.java`, `MobFacts.java`, `TargetRelation.java`; `adapter/server/context/ReadyTactics.java`, `FactsAssembler.java`, `TargetRelationReader.java`; `adapter/jev/SemanticEncoder.java`, `SemanticPhrases.java` (`grouped`); pruebas `EligibilityTest`, `ReadyTacticsTest`, `FactsAssemblerTest`, `TargetRelationReaderTest`, `SemanticEncoderTest`, `CombatFactsTest`.
- **Reglas:** A5 (coste por captura acotado y probado), D2, I6 (ningún número llega a Jev), C12 (sin asignaciones evitables en la captura: el conjunto se crea solo si hay alguna choice fuera de alcance).
- **Pruebas:** `KEEP_DISTANCE` + golpe **declarado** de 2,5 fuera de alcance no es elegible y dentro de alcance sí; el mismo par con el golpe **inferido** de `MELEE_ATTACK` sigue elegible como en la 0.0.1; `HOLD` + golpe fuera de alcance sigue elegible; sin objetivo, conjunto vacío; `grouped` con 0, 1 y 2 compañeros en 3,9 / 4,0 / 4,1 bloques; los escenarios semilla sin cambios de elegibilidad salvo los que el ejemplo nuevo añade.
- **Aceptación:**
  - [x] Ninguna pregunta ofrece una táctica declarada `NEVER_CASTS` fuera de alcance (prueba en `EncounterQuestions` con un perfil de prueba que la declara).
  - [x] Perfiles sin `Purpose`: mismos conjuntos elegibles que en la 0.0.1 en toda la suite (M10, M13).
  - [x] Coste de `grouped` y del alcance medido en `m13Measure` (una fila nueva) y sin ruta ni línea de visión extra.

### M15.6 Revalidación previa al cast por entrega (`HitChecks`)

- **Propósito:** reutilizar M10.3 con la entrega real: esperar al alcance de cada skill y a la línea de visión declarada, y no aplicar reglas de golpe a skills que no golpean.
- **Diseño:**
  - `HitChecks.damaging(SkillChoice)`:
    - `VANILLA_MELEE` → verdadero (como hoy);
    - propósito `INFERRED` → `requiresTarget && PurposeInference.legacyDamaging(values)`, **el predicado exacto de la 0.0.1** (M15.3);
    - propósito con `authored()` verdadero (`DECLARED` y, cuando exista, `SKILL`: se tratan igual) → `requiresTarget && purpose.damaging() && purpose.aimedAtTarget()`, donde `damaging()` es "`roles` contiene `DAMAGE` o `FINISHER`, o `damage` es `LOW`, `MEDIUM`, `HIGH` o `UNRATED`" y `aimedAtTarget()` es "entrega distinta de `SELF` y `AREA_AROUND_SELF`".
  - `HitChecks.reach(SkillChoice, LivingEntity caster, LivingEntity target): ReachVerdict` sustituye a `meleeReach`. Con propósito `INFERRED` aplica solo la regla de hoy (`VANILLA_MELEE` o `legacyMeleeReach(values)` → hueco entre cajas ≤ `meleeReach`); con `authored()` (`DECLARED` o `SKILL`), la tabla:

    | Entrega | Comprobación | Resultado si falla |
    |---|---|---|
    | `MELEE`, `CONE` | `gapBlocks(cajas) ≤ reachBlocks` (hoy: solo `MELEE_ATTACK` y `VANILLA_MELEE` con `meleeReach`) | `NOT_ELIGIBLE` (se reintenta en el ciclo reactivo, M10.4) |
    | `AREA_AROUND_SELF` | distancia entre las posiciones (`getLocation()`, los pies) **estrictamente menor** que `radiusBlocks`: exactamente el `r` de los targeters de radio de MythicMobs (`@PlayersInRadius{r=3}` equivale a `Radius: 3`; revisión tras la auditoría, desviación 22) | `NOT_ELIGIBLE` |
    | `PROJECTILE`, `HITSCAN`, `AREA_AT_TARGET` con `Reach` | distancia entre centros `≤ reachBlocks` | `NOT_ELIGIBLE` |
    | Cualquiera con riesgo `NEEDS_LINE_OF_SIGHT` | `caster.hasLineOfSight(target)`, **después** de las comprobaciones baratas | `NOT_ELIGIBLE` |
    | `SELF`, o a distancia sin `Reach` | ninguna (las condiciones de la skill siguen evaluándose, M10.3) | — |

  - Escudo (`gate`): se usa `choice.purpose().ignoresShield()` en lugar de `values.contains(IGNORES_SHIELD)`. Las entregas de área se tratan igual que hoy (un escudo de frente bloquea el `damage` de MythicMobs con el lanzador como causante; se confirma en servidor, DT-33).
  - El alcance por perfil de `CombatActionsAdapter.movement` se conserva para `VANILLA_MELEE` sin `Purpose` (su propósito inferido ya lleva `meleeReach`, así que la función queda como respaldo cuando la sesión no tiene perfil).
  - Orden de `waiting` sin cambios salvo la sustitución: cooldown nativo → alcance y visión → invulnerabilidad y escudo si es dañina → condiciones de la skill.
- **Archivos:** modificar `adapter/server/execution/HitChecks.java`, `CombatActionsAdapter.java`; pruebas nuevas `adapter/server/execution/HitChecksTest.java` y casos en `CombatActionsAdapterTest.java` con `ExecutionDoubles` (caja, distancia y visión controladas).
- **Reglas:** A1 (MythicMobs sigue decidiendo sus condiciones; esto solo añade lo que no hace), A6, T1, C7, C12 (la línea de visión solo con el riesgo declarado y al final).
- **Pruebas:** una por fila de la tabla en la frontera exacta (2,5 sí, 2,5001 no); equivalencia total con la 0.0.1 para perfiles sin `Purpose`: los 8 192 subconjuntos de `PredictValue` × `requiresTarget` dan el mismo `damaging` y el mismo alcance que `HitChecks` de la 0.0.1 (copia congelada del predicado antiguo en la prueba), más `VANILLA_MELEE`; escudo con `ignoresShield` declarado y heredado de `IGNORES_SHIELD`; la visión no se calcula si falla antes el alcance.
- **Aceptación:**
  - [x] Perfiles sin `Purpose`: mismas `ExecutionOutcome` que antes en toda la suite de M10 y M13.
  - [x] Una skill a distancia con `Reach: 12` espera (`NOT_ELIGIBLE`) a 12,1 bloques y lanza a 12.
  - [ ] Guion de servidor, paso nuevo 15.3 (M15.9): el golpe de área no se lanza fuera de su radio. (Pendiente de servidor; en local, `CombatActionsAdapterTest.anAreaAroundTheCasterIsNotAimedAtTheTargetSoTheShieldDoesNotStopIt` y `HitChecksTest.anAreaAroundTheCasterWaitsUntilTheGapIsWithinItsRadius`.)

### M15.7 Frases fijas del propósito para Jev

- **Propósito:** describir cada opción con significado preciso y sin números, reutilizando las mismas palabras que el estado para que la lectura literal de Jev las empareje ([jaggedness §1 y §2](https://docs.typesafe.ai/model-jaggedness/jev-1.13#literal-reading)).
- **Diseño:**
  - `SemanticPhrases` gana `switch` exhaustivos (un valor nuevo sin frase no compila):

    | Pieza | Frases |
    |---|---|
    | Entrega | `MELEE` "strikes the target up close"; `PROJECTILE` "fires a projectile at the target"; `HITSCAN` "hits the target instantly from a distance"; `AREA_AROUND_SELF` "hits every enemy around the caster"; `AREA_AT_TARGET` "hits an area around the target"; `CONE` "sweeps a cone in front of the caster"; `SELF` "acts only on the caster" |
    | Papel (todos los de `roles`) | `DAMAGE` "to deal damage"; `CONTROL` "to stun, slow or push the target"; `GAP_CLOSE` "to close the distance quickly"; `ESCAPE` "to get the caster out of danger quickly"; `DEFENSE` "to protect the caster from damage"; `SUSTAIN` "to restore the caster's health"; `BUFF` "to strengthen the caster"; `DEBUFF` "to weaken the target"; `SUMMON` "to summon allies"; `ZONE` "to deny an area to enemies for a while"; `INTERRUPT` "to interrupt what the target is doing"; `FINISHER` "to finish off a weakened target"; `OPENER` "to open the fight with an advantage" |
    | Alcance (con `Reach`) | `r ≤ meleeReach + 0,5` "only inside melee reach"; `r ≤ bandOuter` "a short distance beyond melee reach"; `r ≤ rangedReach` "within ranged reach"; más "beyond ranged reach" |
    | Área | `≤ 3` "in a small area"; `≤ 6` "in a medium area"; más "in a large area" |
    | Preparación (solo con `authored()`) | 0 "at once"; `≤ 10` "after a short wind-up"; más "after a long, visible wind-up" |
    | Cooldown | `NONE`, `SHORT` "can be used again soon"; `MEDIUM` "has a moderate cooldown"; `LONG` "has a long cooldown"; `UNKNOWN` sin frase |
    | Daño | `UNRATED` "deals damage"; `LOW`/`MEDIUM`/`HIGH` "deals low/medium/high damage"; `NONE` sin frase |
    | `BestWhen` | `TARGET_LOW_HEALTH` "the target's health is critical"; `TARGET_SHIELDING` "the target is blocking or raising a shield"; `TARGET_FLEEING` "the target is backing away"; `SELF_LOW_HEALTH` "the caster's health is critical"; `ENEMIES_GROUPED` "the target stands close to other enemies"; `TARGET_AT_RANGE` "the target is outside melee reach"; `TARGET_IN_MELEE` "the target is inside melee reach" |
    | Riesgo | `LEAVES_CASTER_EXPOSED` "leaves the caster exposed afterwards"; `ROOTS_CASTER` "the caster cannot move while using it"; `SELF_DAMAGE` "hurts the caster"; `NEEDS_LINE_OF_SIGHT` "needs a clear line of sight"; `SLOW_PROJECTILE` "a moving target can dodge it"; `BLOCKED_BY_SHIELD` "a raised shield blocks it"; `HITS_ALLIES` "can also hit allies" |
    | Escudo | `ignoresShield` "goes through shields" |

    Las bandas de alcance usan los mismos umbrales de `MovementSettings` del perfil que las frases de distancia del estado (`inside melee reach`, `within ranged reach`), así que una skill "only inside melee reach" y un objetivo "outside melee reach but within ranged reach" se contradicen de forma literal y visible.
  - `QuestionBuilder.criterion(tactic, profile)` compone, en este orden y separado por `; `: movimiento (frase de hoy); `uses a skill that <entrega> <papeles>, reaching <alcance>, <área>, <preparación>, <daño>, <cooldown>` (omitiendo piezas vacías); `best when <bestWhen unidos por " or ">`; `risk: <riesgos unidos por ", ">`; la `Description` del autor. Es texto, como hoy. M16 decide cuánto de esto llega a Jev con `decision.questionDetail` (M16.2): `compact` (default) usa una forma corta de estas frases (≤ 120 caracteres por opción), `standard` estas frases completas y `full` añade "por qué ahora". Mientras M16 no esté implementado, este criterio es el que se envía.
  - Longitud: la opción más larga posible (3 papeles, 4 condiciones, 4 riesgos, descripción de 200 caracteres) cabe en 700 caracteres; `RequestSizeGuard` sigue midiendo la pregunta más larga (M08).
- **Archivos:** modificar `adapter/jev/SemanticPhrases.java`, `QuestionBuilder.java`; pruebas `SemanticPhrasesTest`, `QuestionBuilderTest`, `RequestEncoderTest` (tamaño máximo).
- **Reglas:** I4 (solo el `TacticId` y frases fijas llegan al modelo), I5 (el `ChoiceId` no llega), 01 §1 (sin números), C12 no aplica (fuera del hilo del servidor, T2).
- **Pruebas:** exhaustividad de cada `switch`; criterio exacto de las cuatro choices del ejemplo; propósito inferido sin frase de preparación; frontera de cada banda con el perfil por defecto y con uno propio.
- **Aceptación:**
  - [x] Ningún criterio contiene cifras.
  - [x] Dos tácticas con el mismo movimiento y skills distintas producen criterios distintos (se comprueba con todas las parejas del ejemplo).

### M15.8 Planificador local con propósito declarado

- **Propósito:** que el nivel 3 (sin Jev) use lo que el autor declaró, sin cambiar perfiles que no declaran nada.
- **Diseño:**
  - `domain.ActiveUseConditions.of(MobFacts mob, Optional<ParticipantFacts> target): Set<UseCondition>`: `TARGET_LOW_HEALTH` (salud del objetivo `CRITICAL`), `TARGET_SHIELDING` (`BLOCKING` o `RAISING_SHIELD`), `TARGET_FLEEING` (`BACKING_AWAY` y distancia distinta de `MELEE`), `SELF_LOW_HEALTH` (salud del mob `CRITICAL`), `ENEMIES_GROUPED` (`grouped`), `TARGET_AT_RANGE` (`RANGED`), `TARGET_IN_MELEE` (`MELEE`). Sin objetivo, solo `SELF_LOW_HEALTH`. Es la misma función que M16 usa para "encaja ahora".
  - `LocalPlanner.choose(profile, active, eligible, Set<UseCondition> now)` (sobrecarga del `choose(profile, active, eligible)` actual): puntuación = `priority + BEST_WHEN_BONUS × |bestWhen ∩ now|` con `BEST_WHEN_BONUS = 20`, **solo si `purpose.authored()`** (los inferidos puntúan su prioridad, como hoy). Empates: orden de declaración, como hoy. El `choose` de tres argumentos se conserva y equivale a `now` vacío.
  - `engine` pasa `now` desde `MobSession.lastMobFacts()` y los participantes de la última captura del encuentro (ya guardados, M12); sin captura, vacío.
- **Archivos:** crear `domain/ActiveUseConditions.java`; modificar `domain/LocalPlanner.java`, `engine/FallbackStep.java` (o el auxiliar que llama al planificador); pruebas `ActiveUseConditionsTest`, `LocalPlannerTest`, `FallbackChainTest`.
- **Reglas:** A6, P1, P2.
- **Pruebas:** cada condición en su caso límite; desempate por `BestWhen` declarado; perfiles sin `Purpose` eligen exactamente lo mismo que hoy en `LocalPlannerTest` y `TacticalExecutionABTest`.
- **Aceptación:**
  - [x] Con el perfil de prueba `src/test/resources/profiles/purpose-shield.yml` (dos tácticas `APPROACH` de prioridad 60: `press` con un golpe `MELEE` normal **sin `BestWhen`** (para que nunca empate por condiciones con la otra) y `break_guard` con un golpe declarado `IgnoresShield: true` y `BestWhen: [TARGET_SHIELDING]`), un objetivo que bloquea hace ganar a `break_guard` y uno que no bloquea deja ganar a `press` por orden de declaración. El mismo perfil lo usan M15.6 (escudo) y el banco de M16 (familia "Escudo"). (`LocalPlannerTest.thePurposeShieldTestProfileBreaksTheGuardOfABlockingTargetOnly`, `FallbackChainTest.theLocalPlannerBreaksTheGuardOfABlockingTargetWithTheDeclaredBestWhen`, `CombatActionsAdapterTest.thePurposeShieldTestProfileSkipsTheShieldOnlyWithTheNormalStrike`.)
  - [x] Ningún número del arnés A/B de M13 cambia para el perfil de `ExecutionFixture` (sin `Purpose`). (`m13Measure` tras M15: duelo 0,997 / 0,861 ± 0,061, 19,1 ± 1,2 y 16,6 ± 0,7 casts; escudo 14,3 y 3,1 `SKIPPED_SHIELD`; rearme 20,8 / 17,6 / 17,6: las mismas cifras de M13.)

### M15.9 Ejemplo, paquete de pruebas, guía y banco semilla

- **Propósito:** que el autor vea un ejemplo completo y que la evidencia local y de servidor use propósitos declarados.
- **Diseño:**
  - `examples/AI/ThePredictGuardian.yml` declara `Purpose` en sus choices y añade `slam`:

    | Choice | `Purpose` |
    |---|---|
    | `melee` (`ThePredictGuardianMelee`: `delay 5`, `damage{a=6}`, `Cooldown: 2`) | `Roles: [DAMAGE]`, `Delivery: MELEE`, `Reach: 2.5`, `WindUp: 5`, `Damage: MEDIUM`, `BestWhen: [TARGET_IN_MELEE]` |
    | `ranged` (`delay 10`, `damage{a=4} @target`, `Cooldown: 3`, `targetwithin{d=16}`, `lineofsight`) | `Roles: [DAMAGE]`, `Delivery: HITSCAN` (el `damage` va directo a `@target`; las partículas son solo visuales), `Reach: 16`, `WindUp: 10`, `Damage: LOW`, `BestWhen: [TARGET_AT_RANGE, TARGET_FLEEING]`, `Risks: [NEEDS_LINE_OF_SIGHT]` |
    | `defense` (`heal{a=8} @self`, `Cooldown: 5`, `healthpercent{p=<90%}`) | `Roles: [SUSTAIN]`, `Delivery: SELF`, `Damage: NONE`, `BestWhen: [SELF_LOW_HEALTH]` |
    | `slam` (nueva skill `ThePredictGuardianSlam`: partículas de aviso, `delay 15`, `damage{a=5} @PlayersInRadius{r=3}` y `throw{velocity=6;velocityY=1} @PlayersInRadius{r=3}` (targeter `playersInRadius`, alias `PIR`, javap: solo alcanza jugadores, así que no daña a otros mobs; las `TargetConditions` de la skill no filtran un targeter de área), `Cooldown: 8`; al usar `@PlayersInRadius`, **no alcanza a rivales que no sean jugadores**: el guardián está pensado para combatir jugadores; para duelos entre mobs, M18 usa skills con `@target`) | `Roles: [CONTROL, DAMAGE]`, `Delivery: AREA_AROUND_SELF`, `Radius: 3`, `WindUp: 15`, `Damage: MEDIUM`, `BestWhen: [ENEMIES_GROUPED, TARGET_IN_MELEE]`, `Risks: [ROOTS_CASTER]` |

    Táctica nueva `close_in_and_slam`: `Movement: APPROACH`, `Choice: slam`, `Priority: 65` (con `APPROACH` la meta se detiene a `MeleeReach` 2,5 ≤ radio 3, así que `PurposeRules.fit` da `FITS` y el ejemplo vincula sin avisos; con `HOLD` daría `CASTS_ONLY_IF_TARGET_COMES`), sin `Description` (su propósito ya la describe). El `PredictValues` de las skills se conserva tal cual (compatibilidad, W3). `ThePredictGuardianSlam` se añade a `ThePredictGuardianSkills.yml` y, como las otras, nunca con trigger normal en el mob.
  - `ScenarioFacts.example()` lee el perfil con `ProfileLoader` como hoy: como el ejemplo **declara** sus propósitos, los criterios del banco semilla describen las skills sin necesidad de binder (resuelve la desviación 9 de M13 para los propósitos; el cooldown queda `UNKNOWN` y sin frase en el banco, anotado). **Antes de regenerar**, los seis archivos actuales se copian sin cambios a `src/test/resources/scenarios/seed-2026-09-29/` (histórico congelado del banco pagado: M16 lo usa para su diagnóstico D1–D8 y una prueba comprueba que no cambia nunca); después se regeneran los seis `seed/*.json` (`ScenarioSeedBankTest` compara solo `seed/`). Las etiquetas no se tocan aquí: su rediseño es de M16.9.
  - Guía del autor: sección "Propósito de cada skill" con la tabla de claves, los defaults, la inferencia y el lint; nota de que `slam` del guardián usa `@PlayersInRadius` y solo daña jugadores (con participantes entidad de M17 hay que usar `@target` o un targeter de entidades); cada fragmento carga (`UserGuidesTest`).
  - Paquete de pruebas: `archivos/03-guardian/` con el perfil y las skills nuevas; guion de servidor, sesión nueva **15** (sin clave): 15.1 carga sin avisos; 15.2 perfil de prueba con `KEEP_DISTANCE` + `melee` muestra el aviso de M15.4; 15.3 `slam` (táctica `close_in_and_slam` forzada con `/thepredict debug intent`) no se lanza con el objetivo a más de 3 bloques de hueco (`/thepredict inspect`, resultado `not_eligible`) y sí a 2, y no daña a un zombi vanilla cercano; 15.4 `ranged` espera sin línea de visión (pilar de 3 bloques de alto). Checklist: DT-32 y DT-33.
  - MythicScript 2077: sin cambios (ninguna mecánica, condición ni targeter nuevo en MythicMobs).
- **Archivos:** los de "Base existente" del ejemplo, la guía y el paquete; `UserTestPackTest` y `UserGuidesTest` con los fragmentos nuevos; `PluginResourcesTest` (recursos obligatorios).
- **Reglas:** W1, W3, C14 (el JAR sigue sin dependencias).
- **Pruebas:** `UserGuidesTest`, `UserTestPackTest`, `ScenarioSeedBankTest`, `PluginResourcesTest`, `verifyPluginJar`.
- **Aceptación:**
  - [x] El ejemplo vincula sin avisos con el pack de verificación (ninguna táctica declarada da `NEVER_CASTS` ni `CASTS_ONLY_IF_TARGET_COMES`). (Local, con el vinculador de ThePredict sobre dobles de MythicMobs: `UserTestPackTest`, `MythicProfileBinderSuiteTest`; en servidor, sesión 15.1.)
  - [x] `seed-2026-09-29/` idéntico a los archivos del banco pagado.
  - [x] El banco semilla regenerado describe las cuatro skills en los criterios.
  - [x] La guía no afirma nada sin prueba (W3).

## Cambios propuestos a 00/01

Los aplica quien implemente M15, antes de escribir código (flujo de 01: "un módulo que necesite un tipo nuevo lo añade aquí primero").

**00. Arquitectura y reglas**

- §6, fila nueva **I12 Propósitos**: "`SkillRole`, `Delivery`, `UseCondition`, `SkillRisk`, `DamageTier` y `CooldownClass` son conjuntos cerrados de `domain`; llegan a Jev solo como frases fijas de `adapter.jev.SemanticPhrases`, nunca como nombres de enum ni con números (01 §1)."
- §4, sin cambios de puertos. §7, C13: añadir "incluidas las secciones anidadas del perfil, como `Purpose`".
- Encabezado: "Revisión 9 (fecha): I12 (M15)".

**01. Modelo de dominio**

- Encabezado: "**Revisión 10** (fecha): `SkillPurpose` y sus enums, `SkillChoice.purpose`, `MobFacts.choicesOutOfReach`, `TargetRelation.grouped`, `PurposeRules`, `PurposeInference` y `ActiveUseConditions` (M15, sección 10)."
- §5, nota de `profiles`: sustituir "el objetivo, el movimiento, los `PredictValues` y la descripción de cada opción" por "el objetivo, el movimiento, el **propósito** (`SkillPurpose`, que incluye lo que antes daban los `PredictValues`) y la descripción de cada opción".
- §3: los siete enums (con `PurposeOrigin { DECLARED, INFERRED, SKILL }`, `SKILL` reservado) y `SkillPurpose` de M15.1 con su tabla de invariantes; `SkillChoice` con `SkillPurpose purpose` y la invariante de `VANILLA_MELEE`; `PurposeRules` (con `PurposeFit`), `PurposeInference` y `ActiveUseConditions` en la tabla de §7 ("Reglas puras").
- §4: `MobFacts` gana `Set<ChoiceId> choicesOutOfReach` (vacío sin objetivo); `TargetRelation` gana `boolean grouped` (otro participante percibido a ≤ 4 bloques del objetivo).
- §7: `Eligibility` → "perfil, contextos activos, choices listas, movimiento disponible **y choices fuera de alcance** → `Set<TacticId>` (una táctica con propósito escrito por el autor `NEVER_CASTS` fuera de alcance no es elegible; los propósitos inferidos no cambian nada)"; `LocalPlanner` → "… **y condiciones de uso activas**; desempata con el `BestWhen` declarado".
- §10, fila: "`SkillPurpose` y sus enums; `SkillChoice.purpose`; `MobFacts.choicesOutOfReach`; `TargetRelation.grouped`; `PurposeRules`, `PurposeInference`, `ActiveUseConditions`: sin ellos el binder no detecta tácticas imposibles, M10 solo conoce el alcance de `MELEE_ATTACK` y Jev recibe una lista de etiquetas en vez de para qué sirve cada skill | M15".

**Registro de decisiones de M01 (entradas nuevas propuestas)**

- **DT-32. Dónde se declara el propósito de una skill.** Decisión del usuario (2026-09-29): **por ahora en el perfil** (motivos de "Evidencia nativa"). Dirección futura registrada en "Riesgos y decisiones abiertas": leerlo de la propia skill de MythicMobs (origen `SKILL`). Estado: Resuelta.
- **DT-33. Escudo frente a daño de área de MythicMobs.** Pregunta: ¿un escudo de frente bloquea el `damage` de una skill `AREA_AROUND_SELF` con el lanzador como causante? Se comprueba en la sesión 15 del guion; si no bloquea, las áreas pasan a `ignoresShield` por defecto en la inferencia. Estado: Abierta.
- **DT-34. `Cooldown` estático.** Pregunta: ¿`AbstractSkill.getCooldown().isStatic()` es falso para un `Cooldown` con placeholder y verdadero para un número? Evidencia estática: `IPlaceholder.isStatic()` existe (javap). Se comprueba en la sesión 15.1 con `/thepredict inspect`. Estado: Abierta.

## Riesgos y decisiones abiertas

| Riesgo | Mitigación |
|---|---|
| **Dirección futura (decisión del usuario, DT-32):** el addon debería leer los propósitos directamente de las skills de MythicMobs y añadirlos a ThePredict de forma automática, para simplificar la configuración y mantener la integración nativa | Los tipos ya lo admiten: `PurposeOrigin.SKILL` está reservado y `authored()` lo trata como escrito por el autor. Diseño previsto: el binder lee un bloque `PredictPurpose:` (mismas claves que `Purpose`) de `skill.getConfig()` con `PurposeParser`, que ya es independiente de la fuente; precedencia `DECLARED` (perfil) > `SKILL` (skill) > `INFERRED` (`PredictValues`); errores del bloque como `BindingProblem` con la ruta de la skill. No entra en la 0.0.1; no cambia ningún contrato de M15 |
| Cambiar el componente de `SkillChoice` rompe muchas pruebas | Constructor de compatibilidad de seis argumentos (W5) y retirada al final del módulo |
| El lint o la elegibilidad cambian el comportamiento de perfiles sin `Purpose` | Lint, exclusión, desempate y comprobaciones nuevas de `HitChecks` solo se aplican a propósitos escritos por el autor (`authored()`); los inferidos usan los predicados de la 0.0.1 sobre `values`; prueba de propiedad sobre los 8 192 subconjuntos y equivalencia con toda la suite de M10 y M13 |
| El autor declara un propósito falso (dice `HIGH` y hace poco daño) | Es configuración de confianza (plan §6); M16 mide el resultado real por táctica (M16.8) y M18 lo hace visible |
| Frases más largas suben tokens | El detalle que llega a Jev lo decide `decision.questionDetail` de M16 (`compact`: una frase corta por opción; el default es `full` desde el re-banco de M16.10); las frases completas de M15.7 solo con `standard` o `full`; `RequestSizeGuard` protege el límite |
| La inferencia de `AREA_AT_TARGET` usa un radio por defecto de 3 bloques | Solo para frases (`reachBlocks` vacío, sin comprobación previa); se documenta y el autor puede declarar el suyo |

## Terminado

M15 está terminado cuando se cumple W4 y además:

- [ ] Todas las casillas de M15.1–M15.9 marcadas.
- [x] `./gradlew clean build javadoc` en verde, `ArchitectureRulesTest` en verde y C9 en todo el código tocado.
- [x] Toda la suite de la 0.0.1 pasa con perfiles sin `Purpose` sin cambiar sus números (M10, M13), salvo los archivos semilla regenerados.
- [x] 00 y 01 actualizados con los cambios propuestos; DT-32 a DT-34 en el registro.
- [x] Sesión 15 del guion ejecutada por el usuario o marcada pendiente en el checklist.

## Estado de implementación

> **Local listo — servidor pendiente (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` (también con `--rerun-tasks`) termina en `BUILD SUCCESSFUL` sin avisos de javac ni de Javadoc estricto: **1350 pruebas en 189 clases, 0 fallos** (1230 de partida + 120: 73 en 9 clases nuevas y 47 en clases existentes; incluye las correcciones tras la auditoría). `verifyPluginJar` pasa. Ninguna llamada de red (ni a TypeSafe ni a otro host; `m13Measure` solo usa el servidor de contrato en `127.0.0.1`), no se ejecutaron las tareas de pago, nadie leyó `.env` (W6) y no se arrancó ningún servidor de Minecraft: las casillas de juego siguen sin marcar y no hay evidencia inventada. 00 recibe la revisión 9 (I12 y C13) y 01 la revisión 10; el registro de M01 recibe DT-32 (`Resuelta`), DT-33, DT-34 y DT-41 (`Abierta`).

### Artefacto

`build/libs/ThePredict-0.0.1.jar`, 792 034 bytes, 422 entradas, SHA-256 `690b60a0d11315882003f515e7322b47ce5be778fe70754ff19bffc3db67af23` (tras las correcciones de la auditoría; antes `a6a9ec87da5e793727769554ff21bf1d2872d701fefa82ea296c6ac793961407`); reproducible (dos builds limpios, uno con `--rerun-tasks`, dan la misma huella). Registrado en el [CHANGELOG](../../CHANGELOG.md#artefacto).

### Archivos

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…` salvo indicación) |
|---|---|
| M15.1 | Creados `domain/SkillRole`, `Delivery`, `CooldownClass`, `DamageTier`, `UseCondition`, `SkillRisk`, `PurposeOrigin`, `SkillPurpose`. Modificados `domain/SkillChoice` (componente `purpose`, invariante de `VANILLA_MELEE`, constructor de compatibilidad de seis argumentos, `withPurpose`) y `domain/MovementSettings` (`DEFAULTS`, fuente única de los defaults de movimiento; `config/ProfileDefaults.standard()` y `CombatActionsAdapter.DEFAULT_MELEE_REACH_BLOCKS` la usan) |
| M15.2 | Creado `config/PurposeParser`. Modificados `config/ProfileParser` (lee `Movement` antes de `Choices` y llama a `PurposeParser`) y `config/ProfileSchema` (`CHOICE` con `Purpose`, `PURPOSE`, nombres `Reach`, `Radius` y `WindUp`) |
| M15.3 | Creado `domain/PurposeInference` y `adapter/server/binding/ChoicePurposes` (resolución del propósito de cada choice). Modificados `adapter/server/binding/MythicProfileBinder`, `SkillMetadata` (`predictValuesOrEmpty`, `cooldownClass`, `classOf`, `vanillaCooldownClass`), `BindingWarnings` (`purpose`) y `BoundProfile` (invariante del propósito resuelto) |
| M15.4 | Creado `domain/PurposeRules` (con `PurposeFit`). `BindingWarnings.tacticFit`, llamado por el binder |
| M15.5 | Modificados `domain/Eligibility` (sobrecarga con `choicesOutOfReach`, `neverCasts`), `MobFacts` (`choicesOutOfReach`), `TargetRelation` (`grouped`, `GROUP_RADIUS_BLOCKS`); `adapter/server/context/ReadyTactics` (`outOfReach`, `measuresReach`), `FactsAssembler`, `TargetRelationReader` (`grouped`); creado `adapter/server/context/ReachGeometry` (geometría compartida con la revalidación); `adapter/jev/SemanticEncoder` y `SemanticPhrases` (`grouped`) |
| M15.6 | Modificados `adapter/server/execution/HitChecks` (`damaging` por origen, `reach` con `ReachVerdict`, `legacyMeleeReach`) y `CombatActionsAdapter` (alcance y visión por entrega; escudo con `purpose().ignoresShield()`) |
| M15.7 | Modificados `adapter/jev/SemanticPhrases` (entrega, papel, alcance, área, preparación, cooldown, daño, `BestWhen`, riesgo, escudo; se retira `value(PredictValue)`, que ya nada usa) y `QuestionBuilder.criterion` |
| M15.8 | Creado `domain/ActiveUseConditions`. Modificados `domain/LocalPlanner` (sobrecarga con `now`, `BEST_WHEN_BONUS`, `score`) y `engine/FallbackStep` (condiciones de la última captura del encuentro) |
| M15.9 | `src/main/resources/examples/AI/ThePredictGuardian.yml` (`Purpose` en las cuatro choices, `slam` y `close_in_and_slam`), `examples/MythicMobs/Skills/ThePredictGuardianSkills.yml` (`ThePredictGuardianSlam`), `examples/README.txt`; `src/test/resources/profiles/purpose-shield.yml`; `src/test/resources/scenarios/seed-2026-09-29/` (copia congelada) y `scenarios/seed/*.json` regenerados; [guía del autor](../../Guias/Guia-del-autor.md) (§3 con los archivos nuevos, §4.2, §7, §9, §9.1 nueva y §14); paquete de pruebas (`archivos/03-guardian` con el perfil y las skills, `04-slime`, `05-temporal`, `06-contexto-nativo` y `09-amenaza-sin-pesos` regenerados desde el ejemplo, `07-senales` y `08-amenaza` con `PREDICTOR_CLOSE_IN_AND_SLAM`, etapa nueva `15-propositos`; README, guía por sesiones con la sesión 15 y checklist con DT-32 a DT-34 y las casillas de M15); [guion de M01](M01-Guion-de-servidor.md) §12 (ocho tácticas); [guion 0.0.1](Guion-servidor-0.0.1.md) §7.1 y `archivos/10-ab` (`ABClassicSlam` en el brazo clásico) |
| Diagnóstico | `engine/SessionReport` (componente `purposes`), `engine/Diagnostics` y `admin/InspectFormatter` (línea `Skills`; `grouped` y `out of reach` en `Perception`), para que `inspect` muestre la clase de cooldown leída (DT-34) y los hechos nuevos |
| Documentos | Este documento; [README](README.md); [00](00-Arquitectura-y-reglas.md) (revisión 9); [01](01-Modelo-de-dominio.md) (revisión 10); [registro de M01](M01-Registro-de-decisiones.md) (DT-32 a DT-34); nota en la desviación 9 de [M13](M13-Suite-carga-latencia-calidad.md); [informe](Informe-0.0.1.md) (nota de cabecera); [CHANGELOG](../../CHANGELOG.md) |

### Pruebas (120 nuevas)

| Clase | Pruebas | Qué demuestra |
|---|---|---|
| `domain/SkillPurposeTest` (nueva) | 14 | Cada invariante en su frontera (alcance 64 sí y 64,0001 no; radio en `CONE` rechazado; `DAMAGE` y `FINISHER` con `NONE`; 4 condiciones y 4 riesgos sí, 5 no; `ignoresShield` con `BLOCKED_BY_SHIELD`; preparación 0–100); `damaging()` y `aimedAtTarget()` para las siete entregas; `authored()`; alcance efectivo; copia defensiva e igualdad; `withCooldown` e `ignoringShields` |
| `domain/PurposeInferenceTest` (nueva) | 8 | Los 8 192 subconjuntos de `PredictValue` × `requiresTarget` × `ChoiceKind` dan un propósito válido `INFERRED`, y `legacyDamaging`/`legacyMeleeReach` coinciden con una copia congelada del predicado de la 0.0.1; los 13 valores solos; `[SINGLE_TARGET]`, `[AREA_TARGET]` e `[IGNORES_SHIELD]` dan `DEBUFF`/`BUFF` con `NONE`; orden del papel principal; entregas, alcances y radios; nivel de daño; `VANILLA_MELEE`; tope de alcance y radio |
| `domain/PurposeRulesTest` (nueva) | 7 | Tabla completa en sus fronteras con los defaults (2,5; 5; 9; 16) y con un perfil propio; sin alcance siempre encaja; área alrededor del lanzador; `closesDistance` |
| `domain/ActiveUseConditionsTest` (nueva) | 5 | Cada condición en su caso límite; sin objetivo solo `SELF_LOW_HEALTH`; salud y escudo del participante objetivo |
| `config/PurposeParserTest` (nueva) | 15 | Una prueba por fila de la tabla con mensaje y ruta exactos; claves exactas y valores sin distinguir mayúsculas; `Purpose` que no es sección; aviso de `Reach` sobre `RangedReach`; perfil sin `Purpose` con el propósito provisional; `VANILLA_MELEE` con y sin `Purpose` y sus dos errores |
| `adapter/server/binding/SkillMetadataTest` (nueva) | 8 | La línea `Skills` de `inspect` para las cuatro choices del ejemplo, con las clases que da su `Cooldown` (la que cita el paso 15.1 del paquete); cooldown sin clave, 0, 3, 3,05, 10 y 10,05 s, placeholder, `null`, `!isPresent()` y `get()` nulo; skill que no es `AbstractSkill`; `CD` que cruza un umbral; `VANILLA_MELEE` con `CD` 59 y 60; `predictValuesOrEmpty` |
| `adapter/server/context/ReachGeometryTest` (nueva) | 5 | Cada entrega en la frontera exacta (hueco o centros); `AREA_AROUND_SELF` entre posiciones: `r − ε` dentro, `r` y `r + ε` fuera, y un hueco de 2,5 con 3,5 entre posiciones fuera |
| `adapter/server/execution/HitChecksTest` (nueva) | 8 | Equivalencia total con la 0.0.1 sobre los 8 192 subconjuntos × `requiresTarget` (daño, alcance cuerpo a cuerpo y veredicto, contra una copia congelada), más `VANILLA_MELEE`; una prueba por fila de la tabla en su frontera (el área alrededor del lanzador a 2,9999, 3 y 3,0001 bloques entre posiciones, y un hueco de 2,9 que no basta); la línea de visión solo con el riesgo declarado y después del alcance; `damaging` de un propósito declarado |
| `engine/EncounterQuestionsTest` (nueva) | 3 | Ninguna pregunta ofrece una táctica declarada `NEVER_CASTS` fuera de alcance; la misma se ofrece dentro de alcance; un golpe inferido se ofrece como en la 0.0.1 |
| Ampliadas | 47 | `SkillChoiceTest` (+3), `EligibilityTest` (+3), `LocalPlannerTest` (+4, incluido el perfil `purpose-shield`), `MobFactsTest` (+1), `TargetRelationTest` (+1), `MythicProfileBinderSuiteTest` (+6: `Purpose` sin `PredictValues`, error de hoy sin ninguno, propósito inferido y clases de cooldown, `IGNORES_SHIELD` sobre un declarado, cada aviso de coherencia, lint con sus cuatro textos y ninguno para un propósito inferido), `BoundProfileTest` (+1), `ReadyTacticsTest` (+3, con el radio del `slam` entre posiciones), `TargetRelationReaderTest` (+1: `grouped` con 0, 1 y 2 compañeros a 3,9 / 4,0 / 4,1), `FactsAssemblerTest` (+4: alcance declarado en la captura, sin cajas ni visión ni rutas extra con propósitos inferidos, sin objetivo, `grouped`), `CombatActionsAdapterTest` (+5: `Reach: 12` a 12,1 y 12, visión, escudo declarado y heredado, área alrededor, perfil `purpose-shield`), `SemanticPhrasesTest` (+2), `SemanticEncoderTest` (+1), `QuestionBuilderTest` (+4), `RequestEncoderTest` (+1), `ScenarioSeedBankTest` (+2: copia congelada por SHA-256 y las cuatro skills en el banco regenerado), `FallbackChainTest` (+1: el motor desempata con `BestWhen`), `PluginResourcesTest` (+1), `UserGuidesTest` (+1), `UserTestPackTest` (+2) |

**Pruebas existentes cambiadas por un comportamiento que M15 cambia a propósito:** `RequestEncoderTest.theCompleteBodyMatchesItsGolden` y el golden `systemone/golden/request-two-questions.json` (los criterios salen del propósito, M15.7; el `state` no cambia); `QuestionBuilderTest.criteriaCombineMovementSkillValuesAndTheOptionalDescription` se sustituye por `inferredPurposesAreDescribedWithoutWindUpNorCooldownAndKeepTheDescriptionLast` (mismo papel, frases nuevas); `SemanticPhrasesTest` cambia `PredictValue` por los enums nuevos (se retiró `SemanticPhrases.value`); `CombatActionsAdapterTest.theDamageAndReachRulesFollowTheDeclaredValues` llama a `HitChecks.legacyMeleeReach` (el `meleeReach` de M10 pasa a `reach`, M15.6); el ejemplo pasa de 7 a 8 tácticas y de 3 a 4 choices (`ProfileLoaderTest`, `ConfigurationRecoverySuiteTest`, `MythicProfileBinderSuiteTest`, `PluginResourcesTest`, `UserTestPackTest` con 8 señales); `InspectFormatterTest` (una línea más, `Skills`, y `grouped`/`out of reach` en `Perception`); los seis `scenarios/seed/*.json` se regeneran (M15.9). Ajustes de dobles sin cambio de lo que prueban: `ContextFixtures` da la clase `SHORT` al golpe vanilla (la invariante nueva de `BoundProfile`), `ExecutionDoubles.Caster` pasa su caja de 66 a 65,8 de alto (centros alineados con el objetivo; los huecos no cambian) y los records `MobFacts`/`TargetRelation` de las pruebas ganan su componente nuevo vacío o falso.

### Medida nueva (`./gradlew m13Measure`, esta máquina, dobles)

| Trabajo | Cajas leídas | Fuera de alcance | us p50 / p95 / p99 / máx. | Líneas de visión por captura | Rutas por captura |
|---|---|---|---|---|---|
| `ReadyTactics.outOfReach`, 4 choices declaradas, 3 con alcance | — | — | 0,1 / 0,1 / 0,2 / 25,1 | 0 | 0 |
| `TargetRelationReader.grouped`, 16 participantes | — | — | 0,0 / 0,5 / 0,6 / 12,3 | 0 | 0 |
| Captura completa, 1 mob y 16 jugadores, propósitos inferidos (0.0.1) | 0 | ninguna | 5,0 / 13,3 / 28,0 / 2 554,0 | 16 | 0 |
| Captura completa, 1 mob y 16 jugadores, propósitos declarados | 1 (del mob; y la del objetivo) | `strike`, `slam` | 4,4 / 5,4 / 5,8 / 1 747,8 | 16 | 0 |

Ninguna línea de visión ni ruta extra (los máximos aislados son pausas de la JVM). El arnés A/B de M13 da las mismas cifras que en M13 (casilla de M15.8). **Tamaño de las solicitudes:** con los criterios de M15.7 el cuerpo del ejemplo crece (1 mob y 1 jugador: 2 253 → 3 755 caracteres; 16 × 16: 23 652 → 47 684), siempre dentro de la guarda: el cuerpo de 16 × 16 estima 31 790 tokens (límite 64 000) y el estado más la pregunta más larga queda muy por debajo de los 32 000. La estimación previa de M09 (400 + 120 por pregunta + 60 por participante) queda por debajo con estos criterios; la media móvil por pregunta de M09 la corrige con cada respuesta, y el detalle `compact` de M16 los acorta (riesgo "Frases más largas suben tokens").

### Desviaciones y decisiones

| # | Desviación o decisión | Motivo |
|---|---|---|
| 1 | `ActiveUseConditions.of(MobFacts, List<ParticipantFacts>)` y `of(MobFacts, ParticipantFacts)` en lugar de `of(MobFacts, Optional<ParticipantFacts>)` | C4 prohíbe `Optional` como parámetro; la primera busca el objetivo entre los participantes de la captura (lo que usa `FallbackStep`), la segunda exige el participante del objetivo |
| 2 | `HitChecks.reach(choice, caster, target, meleeReachBlocks)` con un cuarto parámetro; el veredicto es `HitChecks.ReachVerdict` (`IN_REACH`, `OUT_OF_REACH`, `NO_LINE_OF_SIGHT`); el `meleeReach` de M10 se llama ahora `legacyMeleeReach` | Un propósito inferido aplica la regla de la 0.0.1 con el `MeleeReach` del perfil de la sesión, que `HitChecks` no conoce |
| 3 | `ReadyTactics.outOfReach` (el `ReadyTactics.reach(...)` de M15.5) y `ReadyTactics.measuresReach`; la geometría vive en la clase pública nueva `adapter.server.context.ReachGeometry`, que usan la captura y `HitChecks` (`HitChecks.gapBlocks` delega en ella) | "Con la misma geometría que la revalidación previa al cast": un solo lugar. `measuresReach` evita leer cajas cuando ninguna choice declara alcance (C12; los dobles de captura de M07 fallarían con cualquier lectura no prevista y siguen verdes) |
| 4 | La distancia entre centros de las entregas a distancia usa los centros de las cajas; `grouped` usa las posiciones de los jugadores percibidos (leídas una vez por captura al percibirlos); el `Radius` de `AREA_AROUND_SELF` usa las posiciones como MythicMobs (desviación 22) | Los jugadores comparten el tamaño de caja, así que la distancia entre posiciones es la de los centros, sin leer cajas; ambas son O(1) por par |
| 5 | Inferencia: un `VANILLA_MELEE` tiene como mínimo daño `UNRATED`; su `bestWhen` pone `TARGET_IN_MELEE` primero y como mucho tres condiciones más de sus etiquetas; el alcance y el radio tomados de `MeleeReach` se limitan a 64 y 16 | La regla literal daría `NONE` con `DAMAGE` (inválido) o hasta 5 condiciones, y un `MeleeReach` enorme haría inválido un perfil válido de la 0.0.1; nada de esto cambia comprobaciones (solo frases) |
| 6 | **"La opción más larga posible cabe en 700 caracteres" no se cumple:** la opción más larga que permite M15.7 (tres papeles, `AREA_AT_TARGET` con alcance y área, preparación larga, todas las piezas, cuatro condiciones, cuatro riesgos y 200 caracteres de descripción) mide **901** | Medido y fijado en `QuestionBuilderTest.theLongestPossibleOptionIsMeasured`; `RequestSizeGuard` sigue midiendo la pregunta más larga: una pregunta con 60 de esas opciones se rechaza sin enviarse (`RequestEncoderTest.theSizeGuardMeasuresTheLongestOptionsOfM15`). El detalle `compact` de M16 (120 caracteres por opción) es la solución prevista |
| 7 | En el criterio, los papeles se unen con `" and "` y la frase `goes through shields` cierra la parte de la skill | El documento no fija ni el separador de papeles ni dónde va la frase del escudo |
| 8 | La etiqueta `IGNORES_SHIELD` no se suma a un propósito declarado que declara el riesgo `BLOCKED_BY_SHIELD`; en su lugar, aviso de coherencia `PredictValues say IGNORES_SHIELD but Risks has BLOCKED_BY_SHIELD; the declared purpose is used` | Sumarla violaría la invariante de `SkillPurpose`; el propósito declarado manda |
| 9 | La invariante de `BoundProfile` se concreta en: toda choice de skill tiene su skill cargada; su clase de cooldown es la que `SkillMetadata.cooldownClass` lee (`UNKNOWN` solo cuando MythicMobs no da un valor fijo legible); un propósito inferido viene de al menos una etiqueta; un `VANILLA_MELEE` tiene `SHORT` o `MEDIUM` según su `CD` | "Resuelto salvo `UNKNOWN` por placeholder" hecho comprobable |
| 10 | El constructor de compatibilidad de seis argumentos de `SkillChoice` se conserva | Lo usan unas 20 construcciones de prueba (W5: se retira cuando ninguna lo use); el código de producción siempre pasa el propósito |
| 11 | `ProfileParser` lee `Movement` antes que `Choices` | El `Reach` por defecto de un `VANILLA_MELEE` y la inferencia provisional necesitan `MeleeReach`. Un perfil con errores en las dos secciones informa ahora primero del de `Movement` |
| 12 | Tamaño de clases (C10): `SemanticPhrases` 304 líneas (antes 196), `FactsAssembler` 243 (antes 222), `PurposeParser` 232, `SkillPurpose` 202, `MythicProfileBinder` 211 (sin cambio de tamaño: la resolución va a `ChoicePurposes`) | `SemanticPhrases` es una sola tabla de frases fijas con `switch` exhaustivos, donde el documento pide ponerlas; las otras son sobre todo Javadoc. Ningún método pasa de 30 líneas (tras la auditoría, `PurposeParser.declared` baja a 23 líneas con `roles`, `reach` y `radius`) |
| 13 | `inspect` muestra el propósito de cada choice (línea `Skills`), `grouped` y `out of reach`; `SessionReport` gana `purposes` (01, revisión 10) | DT-34 "se comprueba en la sesión 15.1 con `/thepredict inspect`", y `inspect` no mostraba la clase de cooldown |
| 14 | `/thepredict` no tiene subcomando de preparación (el diseño lo citaba): el aviso del lint se ve en el registro de carga (una línea por problema) y en los contadores del `Catalog` de `/thepredict status` | Casilla de M15.4 anotada y pendiente de la sesión 15.2 |
| 15 | La sección "Propósito de cada skill" de la guía del autor es la §9.1, dentro de la sección de skills | Una sección de nivel superior renumeraría las §10–§14, enlazadas desde M04, M11, M12 y el paquete de pruebas |
| 16 | `archivos/03-guardian` lleva ahora copias del perfil y las skills del ejemplo; `04-slime`, `05-temporal`, `06-contexto-nativo` y `09-amenaza-sin-pesos` se regeneran desde el ejemplo nuevo; `07-senales` y `08-amenaza` suman `PREDICTOR_CLOSE_IN_AND_SLAM` (y M01 §12 lo cita) | Lo pide M15.9 para `03-guardian`; el resto mantiene lo que comprueba `UserTestPackTest` (cada copia difiere del ejemplo solo donde el guion dice, y una línea `~onSignal` por táctica, hallazgo de M14) |
| 17 | Semillas regeneradas: las cinco que ofrecen `approach` ofrecen también `close_in_and_slam` (elegible donde lo es `approach`); etiquetas intactas | "El banco semilla regenerado describe las cuatro skills": sin ofrecer la táctica, el `slam` no aparecería; el rediseño de etiquetas es de M16.9 |
| 18 | Tipos y ayudantes no citados en el documento: `SkillPurpose.effectiveReachBlocks`, `ignoringShields`, `orderedRoles`; `SkillChoice.withPurpose`; `MovementSettings.DEFAULTS`; `Eligibility.neverCasts`; `LocalPlanner.score`; `PurposeRules.APPROACH_MARGIN_BLOCKS`; `adapter.server.binding.ChoicePurposes`; `adapter.server.context.ReachGeometry` | Un solo lugar para cada regla (C13) y C10 |
| 19 | El Javadoc y el guion llaman "sesión 15" a la sesión nueva; no hay sesión 14 | La numeración sigue la del módulo, como la 16 (M16), la 17 y la 18 |
| 20 | El brazo clásico del A/B (guion 0.0.1 §7.1 y `archivos/10-ab`) gana `ABClassicSlam`, que dispara `ThePredictGuardianSlam` con el objetivo a menos de 3 bloques entre posiciones (`targetwithin{d=3}`, la misma medida que `Radius: 3` tras la desviación 22) | El brazo B usa el ejemplo, que ahora tiene el `slam`; sin esto el brazo A tendría menos capacidades ("ningún brazo tiene más capacidades que el otro") |
| 21 | El banco semilla regenerado describe las skills sin binder (resuelve la desviación 9 de M13 para los propósitos) y deja la clase de cooldown en `UNKNOWN`, sin frase | La clase de cooldown la lee el binder de MythicMobs, que el banco no usa (anotado en M13) |
| 22 | **Corrección tras la auditoría (bloqueante):** `Radius` de `AREA_AROUND_SELF` ya no se mide como hueco entre cajas (≤ radio) sino como MythicMobs mide el `r` de sus targeters de radio: la distancia entre las posiciones de las entidades (`getLocation()`, los pies), estrictamente menor que el radio. Evidencia (javap, MythicMobs 5.13.1): `PlayersInRadiusTargeter.getEntities` guarda un jugador si `IEntitySelector.distanceSquared(caster.getLocation(), jugador) < r²`, y sin `bb=true` esa distancia es `AbstractLocation.distanceSquared` entre las dos posiciones; `@EntitiesInRadius` (`EntitiesNearbyTargeter`, forma esfera por defecto) guarda `≤ r²`, así que la prueba estricta nunca lanza un golpe que ninguno de los dos alcanzaría. `ReachGeometry.withinReach` recibe la distancia al cuadrado entre posiciones (en la captura, la que `CombatCapture` ya mide; en `HitChecks`, `getLocation()` solo para esta entrega) y `ReachGeometry.withinRadius` hace la prueba del targeter. `AREA_AT_TARGET` no cambia: su alcance es `Reach` (entre centros) y su `Radius` solo describe el área alrededor del objetivo, que siempre está dentro. Las entregas medidas por alcance (hueco en `MELEE` y `CONE`, centros a distancia) no cambian. Con la regla anterior, un objetivo a un hueco de 2,4 a 3 bloques (a más de 3 entre posiciones) hacía lanzar el `slam`, gastar sus 8 s de cooldown y fallar | Nativo primero (decisión del coordinador tras la auditoría): el autor copia el `r=3` del targeter en `Radius: 3` y los dos miden lo mismo. El brazo clásico del A/B queda simétrico: `targetwithin{d=3}` también compara las posiciones del mob y de su objetivo con `<` (javap de `TargetWithinCondition.check`). Confirmación en servidor: DT-41, sesión 15.3 |

### Correcciones tras la auditoría (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **1350 pruebas, 0 fallos** (2 nuevas). Sin red (salvo el servidor de contrato de `m13Measure` en `127.0.0.1`), sin leer `.env` y sin servidor. Los seis `scenarios/seed/*.json` no cambian de bytes, así que no se regeneran.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** el `slam` podía lanzarse sin alcanzar: `Radius` de `AREA_AROUND_SELF` se medía como hueco entre cajas, y `@PlayersInRadius{r=3}` mide entre posiciones; con un hueco de 2,4 a 3 bloques la skill salía, gastaba sus 8 s y fallaba | `Radius` = el `r` del targeter de MythicMobs: distancia entre posiciones estrictamente menor que el radio (desviación 22, DT-41); diseño de M15.5 y M15.6, guía del autor §3 y §9.1, paso 15.3 y nota del A/B (los dos brazos ya miden igual) | `ReachGeometryTest.anAreaAroundTheCasterUsesItsRadiusAsTheMythicMobsTargeterUsesR`, `HitChecksTest.anAreaAroundTheCasterWaitsUntilThePositionsAreCloserThanItsRadiusAsMythicMobsMeasuresR`, `ReadyTacticsTest.theSlamRadiusIsTheDistanceBetweenPositionsLikeTheMythicMobsTargeter`, `CombatActionsAdapterTest.anAreaAroundTheCasterIsNotAimedAtTheTargetSoTheShieldDoesNotStopIt` |
| Cifras de consumo anteriores a M15 sin marcar | "Medido antes de M15" con los tamaños nuevos (1 × 1: 2 253 → 3 755 caracteres; 16 × 16: 23 652 → 47 684) y remisión a M16 en la guía del administrador, M13 e informe | — (documentos) |
| Etiquetas de las semillas regeneradas | Nota en M16.9: necesitan revisión (por ejemplo `seed-melee-duel` solo acepta `press_and_strike` y ahora ofrece `close_in_and_slam`) | — |
| `FactsAssembler` leía otra vez la posición de cada participante | Se reutiliza la que `perceive` ya leyó | `FactsAssemblerTest` |
| `PurposeParser.declared` pasaba de 30 líneas | 23 líneas (`roles`, `reach`, `radius`); desviación 12 corregida | `PurposeParserTest` |
| Faltaba fijar la línea `Skills` del paso 15.1 | Prueba que la forma para las cuatro choices del ejemplo y comprueba que la guía por sesiones la cita | `SkillMetadataTest.theExampleSkillsGiveTheInspectLineOfPackStepFifteenOne` |
| Numeración de desviaciones | 22 filas numeradas de 1 a 22 | — |

### Pendiente de verificación en servidor

- Sesión 15 del [paquete de pruebas](paquete-pruebas/Guia-de-pruebas-por-sesiones.md): 15.1 (el ejemplo vincula sin avisos; línea `Skills` de `inspect`, DT-34), 15.1b (`Cooldown` con placeholder, DT-34), 15.2 (aviso del lint en consola y `status`), 15.3 (el `slam` no se lanza fuera de su radio, medido entre posiciones como `@PlayersInRadius`, DT-41, y no daña a un zombi vanilla), 15.4 (`ranged` espera sin línea de visión) y 15.5 (escudo frente al área, DT-33).
- Que MythicMobs acepte las líneas de `ThePredictGuardianSlam` (`effect:particles{p=cloud;…}`, `throw{velocity=6;velocityY=1}`) tal cual.
- DT-33, DT-34 y DT-41 siguen `Abierta`.

### Pendiente para módulos posteriores

- M16: la forma `compact` de las frases (≤ 120 caracteres) y "por qué ahora" con `ActiveUseConditions`, `PurposeRules.closesDistance` y `MobFacts.choicesOutOfReach`; la estimación de tokens por pregunta de M09 frente a los criterios nuevos; el banco etiquetado lee `src/test/resources/profiles/purpose-shield.yml` y `scenarios/seed-2026-09-29/`.
- M17: usar `@target` o un targeter de entidades en lugar de `@PlayersInRadius` cuando el rival no sea un jugador; el mayor `reachBlocks` de las choices dañinas con propósito da el alcance de un mob vinculado.
- Dirección futura (DT-32): leer el propósito de la propia skill (origen `SKILL`).
