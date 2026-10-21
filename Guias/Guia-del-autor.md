# Guía del autor de ThePredict 0.0.1

> Para autores de MythicMobs que quieren que un mob o un boss decida su táctica con ThePredict, sin leer Java. Complementa a la [guía del administrador](Guia-del-administrador.md).
>
> **Estado de la versión (2026-09-29): candidata pendiente de integración.** Todo lo que describe esta guía está implementado y cubierto por pruebas automáticas, y el cliente de Jev se verificó con la cuenta real de TypeSafe. **Nada se ha observado todavía en un servidor Paper real**: cada comportamiento en juego lleva su marca **(pendiente de servidor, DT-nn)** (o el paso del guion que lo comprueba), que remite al [registro de decisiones](../DocumentacionParaDesarrollar/Implementacion-0.0.1/M01-Registro-de-decisiones.md). Si en tu servidor algo no se comporta como aquí se dice, es un fallo que hay que reportar (ver el [paquete de pruebas](../DocumentacionParaDesarrollar/Implementacion-0.0.1/paquete-pruebas/README.md)).
>
> Todos los bloques YAML de esta guía son archivos completos que una prueba automática (`UserGuidesTest`) lee con el cargador real de perfiles y el vinculador de ThePredict sobre dobles de MythicMobs; valida YAML y nombres, no el texto: si los YAML o los nombres de la guía dejan de coincidir con el código, el build falla. La exactitud del texto la revisa una persona.

## Índice

1. [Qué hace ThePredict y qué conserva MythicMobs](#1-qué-hace-thepredict-y-qué-conserva-mythicmobs)
2. [Activar un mob](#2-activar-un-mob)
3. [El boss de ejemplo, paso a paso](#3-el-boss-de-ejemplo-paso-a-paso)
4. [Perfil v2: referencia completa](#4-perfil-v2-referencia-completa)
   - [Combate por tick](#combate-por-tick)
   - [4.9 Participantes y objetivos (`Participants`)](#49-participantes-y-objetivos-participants)
   - [4.10 Duelos seguros (M18)](#410-duelos-seguros-m18)
5. [Contextos con condiciones de MythicMobs](#5-contextos-con-condiciones-de-mythicmobs)
6. [Nombres de tácticas](#6-nombres-de-tácticas)
7. [`Objective` y `Description` en inglés](#7-objective-y-description-en-inglés)
8. [Movimientos y metas nativas](#8-movimientos-y-metas-nativas)
9. [Skills: la línea `Intention`, el `Cooldown` nativo y `CD`](#9-skills-la-línea-intention-el-cooldown-nativo-y-cd)
   - [Intención en la skill](#intención-en-la-skill)
10. [Vocabulario de ThePredict en MythicMobs](#10-vocabulario-de-thepredict-en-mythicmobs)
11. [Amenaza enriquecida](#11-amenaza-enriquecida)
12. [Percepción y juego limpio](#12-percepción-y-juego-limpio)
13. [Vocabulario para MythicScript 2077](#13-vocabulario-para-mythicscript-2077)
14. [Límites conocidos](#14-límites-conocidos)

## 1. Qué hace ThePredict y qué conserva MythicMobs

ThePredict decide **qué táctica** usa un mob en cada momento de un combate: acercarse para golpear, mantener distancia para disparar, retirarse para defenderse, flanquear un escudo o esperar. La decisión la toma Jev, el modelo de decisión de TypeSafe, con un resumen del combate en inglés; si Jev no está disponible, decide el propio perfil en local, sin costo.

| Responsable | Qué hace |
|---|---|
| **MythicMobs** | Define el mob, sus skills, condiciones, targeters, cooldowns, efectos y proyectiles. Elige el objetivo con su tabla de amenaza. Ejecuta el movimiento con sus metas y el navegador nativo. |
| **ThePredict** | Genera e instala las metas de movimiento del mob a partir del perfil (sustituye sus `AIGoalSelectors`), elige la táctica (Jev o local), publica la intención vigente con condiciones `predictor*`, lanza como mucho una vez por ciclo la skill de la táctica y suma amenaza por hechos que MythicMobs no mide. |
| **Jev (TypeSafe)** | Elige entre las tácticas válidas en ese momento, con probabilidades. Nunca ejecuta nada. |

El combate tiene dos capas:

- **Capa reactiva, local, cada 2 ticks:** mantiene la intención vigente, revalida alcance y condiciones antes de cada skill, y cambia al instante a una alternativa o contingencia ya recibida.
- **Capa deliberativa, Jev, por encuentro:** una solicitud por grupo de mobs que pelea contra los mismos jugadores, cada 20 ticks o antes si pasa algo relevante. Nunca bloquea el servidor.

Si Jev falla, el mob baja por una cadena de cuatro niveles y nunca se queda congelado **(pendiente de servidor, pasos 12.1–12.8 del guion de M01)**:

| Nivel | Nombre en `inspect` y placeholders | Quién decide |
|---|---|---|
| 1 | `JEV` / `jev` | La respuesta de Jev |
| 2 | `ALTERNATIVE` / `alternative` | La siguiente táctica válida o una contingencia de esa misma respuesta |
| 3 | `LOCAL` / `local` | El perfil: contexto activo y prioridad, sin red y sin costo |
| 4 | `BASE` / `base` | La IA base del mob (sección [8](#8-movimientos-y-metas-nativas)); ThePredict no controla |

Con `Predictor: false`, o sin la clave, ThePredict no envía ninguna solicitud. Con `Predictor: false` el mob es un mob normal de MythicMobs.

## 2. Activar un mob

Un mob participa cuando se cumplen las tres cosas:

1. Su archivo de MythicMobs tiene `Predictor: true` (un valor que no sea `true` ni `false` es un error del catálogo: `<Mob>.Predictor: must be true or false`).
2. Un perfil en `plugins/MythicMobs/AIConfig/Profiles/*.yml` tiene `MythicID` igual al ID exacto del mob (sensible a mayúsculas; si solo difiere en mayúsculas, el error sugiere el correcto: `unknown mob 'guardian'; IDs are case-sensitive, did you mean 'Guardian'?`).
3. El perfil no tiene errores. Un mob con `Predictor: true` sin perfil válido da `<Mob>.Predictor: is true but no valid AI profile targets this mob`.

`PredictorTraining` es opcional. Sin la clave vale `false`. Solo cuenta si también hay `Predictor: true`. Con las dos en `true`, ese mob puede explorar también contra jugadores. Con `PredictorTraining: false`, o sin la clave, no explora contra jugadores. Se quita borrando la clave. No hay comando, temporizador ni interruptor global. El jugador no ve el entrenamiento. Un valor distinto de `true` o `false` es un error del catálogo.

```yaml
Predictor: true
PredictorTraining: true
```

`PredictorDifficulty` y `PredictorEntropy` también son opcionales y van en el archivo del mob, no en el perfil (M22). Solo cuentan con `Predictor: true`.

- **`PredictorDifficulty`** elige el preset de dificultad por tiempo objetivo: `Fácil`, `Normal`, `Difícil`, `Caos` o `Vacío` (sin distinguir mayúsculas ni acentos; `Facil` o `dificil` valen). Fija la duración objetivo de la pelea entera contra jugadores: 45, 30, 15, 10 y 5 minutos. Sin la clave vale `difficulty.defaultPreset` de `config.yml` (`normal`). Otro valor es un error del catálogo: `<Mob>.PredictorDifficulty: must be one of Fácil, Normal, Difícil, Caos, Vacío`.
- **`PredictorEntropy`** activa el modo Entropía: voluntario y letal. El mob juega siempre a la presión máxima, sin regulación, y sus tiempos de reacción, adaptación y replanificación bajan en línea recta durante la pelea hasta el suelo del preset. Sin la clave vale `false`. Otro valor que no sea `true` o `false` es un error del catálogo: `<Mob>.PredictorEntropy: must be true or false`. Nunca levanta C21 (lecturas ocultas del enemigo).
- El administrador puede imponer preset y Entropía por mundo (`difficulty.worlds.<mundo>.preset` y `.entropy`). El jugador no elige la dificultad. La sección `Difficulty` del perfil (sección 4.1) ajusta el controlador, no el preset.

```yaml
Predictor: true
PredictorDifficulty: Difícil
PredictorEntropy: false
```

Nada de esto está verificado en servidor (sesión 22, pendiente).

Después, `/mm reload` (vuelve a leer mobs, skills y perfiles) y `/thepredict status`: la línea `Profiles:` dice cuántos perfiles se leyeron y cuántos están activos.

Recomendaciones del mob:

- **`Options.FollowRange`**: es el radio en que el mob percibe jugadores, limitado por `decision.searchRadiusBlocks` (48 por defecto), y el alcance máximo (`maxrange`) de la meta de `APPROACH`, **sin** ese límite (si el mob no tiene el atributo, se usa `RangedReach`). El ejemplo usa 32.
- **Tabla de amenaza** (`Modules.ThreatTable: true`): necesaria para la [amenaza enriquecida](#11-amenaza-enriquecida). Sin ella el mob funciona, pero ThePredict no puede influir en su objetivo.
- **Sin `AIGoalSelectors` gobernadas**: con `Predictor: true`, ThePredict genera las metas de movimiento. Las `AIGoalSelectors` del mob solo se usan como IA base si el perfil no declara `BaseAI` (sección [8](#8-movimientos-y-metas-nativas)).
- **Skills de ataque con triggers normales** (`~onAttack`, `~onTimer`) siguen activas mientras ThePredict controla. Si no quieres sumar su daño al de la táctica, condiciónalas con `predictorcontrol false` (sección [10](#10-vocabulario-de-thepredict-en-mythicmobs)). El catálogo avisa cuando el mob lanza con un trigger la misma skill que una `Choice`: `mob '<Mob>' also casts '<Skill>' from its Skills with a trigger; it may add damage while ThePredict controls the mob`.
- **Especies con `Brain`** (allay, armadillo, axolotl, camel, frog, tadpole, goat, copper golem, happy ghast, nautilus, zombie nautilus, sniffer, zoglin, breeze, creaking, hoglin, piglin, piglin brute, warden y villager): las metas no gobiernan su movimiento, así que su perfil solo admite tácticas con `Movement: NONE`; cualquier otro movimiento es un error (`species PIGLIN uses a Brain; only Movement: NONE is allowed`). ThePredict no les instala metas y `inspect` dice `Goals: not installed (MythicMobs keeps its AI)` **(pendiente de servidor, DT-19)**. Ejemplo:

```yaml
GuardPiglin:
  Type: PIGLIN
  Display: 'Guard Piglin'
  Health: 60
  Predictor: true
  Options:
    FollowRange: 24
GuardPiglinStrike:
  Intention: DAMAGE, MELEE, DAMAGE_LOW, WHEN_TARGET_IN_MELEE
  PredictValues: [MELEE_ATTACK, SINGLE_TARGET, LOW_DAMAGE]
  Cooldown: 3
  Skills:
  - damage{a=3} @target
```

```yaml
schemaVersion: 2
guard_piglin:
  MythicID: GuardPiglin
  Objective: Guard the gate and strike whoever comes close
  Choices:
    strike: { MythicID: GuardPiglinStrike }
  Tactics:
    strike_in_place: { Movement: NONE, Choice: strike }
    wait: { Movement: NONE }
```

- **Mobs que no son `Creature`** (slime, magma cube, ghast, phantom, bat): MythicMobs no les da metas de movimiento, así que ThePredict tampoco instala las suyas: `inspect` dice `Goals: not installed (MythicMobs keeps its AI)` y el mob conserva su IA **(pendiente de servidor, paso 9.10 del guion)**.

## 3. El boss de ejemplo, paso a paso

ThePredict incluye un boss de ejemplo, `ThePredictGuardian`, en `plugins/ThePredict/examples/` (se crea en el primer inicio y queda inactivo allí). El mob de abajo es el del ejemplo (con `Predictor: false`). Las skills y el perfil de abajo son los tres archivos empaquetados: cada skill lleva su línea `Intention` y el perfil no lleva `Purpose`. Que la consola de MythicMobs acepte la clave `Intention` está **(pendiente de servidor, DT-43)**.

`plugins/MythicMobs/Mobs/ThePredictGuardian.yml`:

```yaml
ThePredictGuardian:
  Type: ZOMBIE
  Display: 'ThePredict Guardian'
  Health: 100
  Damage: 3
  Predictor: false
  Options:
    FollowRange: 32
    PreventOtherDrops: true
  Modules:
    ThreatTable: true
  ThreatTable:
    UseDamageTaken: true
    DecayUnreachable: false
  Skills:
  - damage{a=2} @target ~onAttack
  - setstance{stance=pressing} @self ~onSignal:PREDICTOR_STANCE_PRESSING
  - setstance{stance=guarded} @self ~onSignal:PREDICTOR_STANCE_GUARDED
  - setstance{stance=evasive} @self ~onSignal:PREDICTOR_STANCE_EVASIVE
```

`plugins/MythicMobs/Skills/ThePredictGuardianSkills.yml`:

```yaml
ThePredictGuardianMelee:
  Intention: DAMAGE, MELEE, DAMAGE_MEDIUM, WHEN_TARGET_IN_MELEE
  PredictValues: [MELEE_ATTACK, SINGLE_TARGET, MEDIUM_DAMAGE]
  Cooldown: 2
  Skills:
  - effect:particles{p=crit;a=12;hs=0.4;vs=0.4;y=1} @self
  - delay 5
  - damage{a=6} @target

ThePredictGuardianRanged:
  Intention: DAMAGE, HITSCAN, DAMAGE_LOW, WHEN_TARGET_FLEEING, WHEN_TARGET_AT_RANGE, RISK_NEEDS_LINE_OF_SIGHT
  PredictValues: [RANGED_ATTACK, SINGLE_TARGET, LOW_DAMAGE]
  Cooldown: 3
  Conditions:
  - targetwithin{d=16} true
  TargetConditions:
  - lineofsight true
  Skills:
  - effect:particles{p=flame;a=20;hs=0.3;vs=0.6;y=1} @self
  - delay 10
  - effect:particles{p=flame;a=10;hs=0.2;vs=0.2;y=1} @target
  - damage{a=4} @target

ThePredictGuardianDefense:
  Intention: SUSTAIN, SELF, WHEN_SELF_LOW_HEALTH
  PredictValues: [DEFENSE, HEALS_CASTER]
  Cooldown: 5
  Conditions:
  - healthpercent{p=<90%} true
  Skills:
  - effect:particles{p=heart;a=6;hs=0.5;vs=0.5;y=1.5} @self
  - heal{a=8} @self

ThePredictGuardianSlam:
  Intention: CONTROL, DAMAGE, AREA_AROUND_SELF, DAMAGE_MEDIUM, WHEN_ENEMIES_GROUPED, WHEN_TARGET_IN_MELEE, RISK_ROOTS_CASTER
  PredictValues: [MELEE_ATTACK, AREA_TARGET, MEDIUM_DAMAGE]
  Cooldown: 8
  Skills:
  - effect:particles{p=cloud;a=30;hs=1.5;vs=0.1;y=0.2} @self
  - delay 15
  - damage{a=5} @PlayersInRadius{r=3}
  - throw{velocity=6;velocityY=1} @PlayersInRadius{r=3}
```

`plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardian.yml`:

```yaml
schemaVersion: 2
thepredict_guardian:
  MythicID: ThePredictGuardian
  TypeOfMythicID: MOB
  Type: NORMAL
  Objective: Survive while defeating the current target
  Choices:
    melee:
      MythicID: ThePredictGuardianMelee
      TypeOfMythicID: SKILL
    ranged:
      MythicID: ThePredictGuardianRanged
      TypeOfMythicID: SKILL
    defense:
      MythicID: ThePredictGuardianDefense
      TypeOfMythicID: SKILL
      RequiresTarget: false
    slam:
      MythicID: ThePredictGuardianSlam
      TypeOfMythicID: SKILL
  Tactics:
    approach:
      Movement: APPROACH
    press_and_strike:
      Movement: APPROACH
      Choice: melee
      Priority: 70
    keep_distance:
      Movement: KEEP_DISTANCE
    keep_distance_and_shoot:
      Movement: KEEP_DISTANCE
      Choice: ranged
      Description: Stay outside melee reach and shoot while the target approaches
      Priority: 60
    retreat_and_defend:
      Movement: RETREAT
      Choice: defense
      Priority: 90
    flank_and_strike:
      Movement: FLANK
      Choice: melee
    close_in_and_slam:
      Movement: APPROACH
      Choice: slam
      Priority: 65
    hold:
      Movement: HOLD
      Priority: 10
  Contexts:
    low_health_phase:
      Priority: 90
      Tactics: [retreat_and_defend, keep_distance_and_shoot]
    idle:
      Priority: 10
      Tactics: [hold]
      DirectTactic: hold
  Stances:
    Native: hybrid
    Describe: false
    ByMovement:
      APPROACH: PRESSING
      HOLD: GUARDED
      RETREAT: EVASIVE
  Telegraph:
    AgainstPlayers: enforce
    AgainstEntities: off
    Commitment: 100
    EntropyCommitment: 25
    ShortWindUp: lead
    DefaultRecoveryTicks: 20
  Poise:
    Max: 0.15
    RegenDelayTicks: 60
    RegenPerSecond: 0.05
    WindUpMultiplier: 1.5
    BlockedHitPoise: 0.05
    StaggerTicks: 30
    ImmunityTicks: 100
  Movement:
    Band: { Inner: 5, Outer: 9 }
    Retreat: { Distance: 10, Speed: 1.2 }
    Arena: { Radius: 24 }
  Threat:
    AssistsOthers: 1.5
    RangedHarass: 1.0
    BlockedHits: 0.25
  BaseAI:
  - 1 meleeAttack
  - 2 randomStroll
```

Cada skill declara su línea `Intention` ([Intención en la skill](#intención-en-la-skill)): para qué sirve, cómo llega y cuándo conviene. El alcance, el radio y la preparación los lee ThePredict de la propia skill (`targetwithin`, el `r` del targeter y el `delay`). `PredictValues` se conserva como historia y no se lee. La cuarta skill, `ThePredictGuardianSlam` (táctica `close_in_and_slam`), golpea y empuja a **todos los jugadores** a menos de 3 bloques del guardián (distancia entre sus posiciones, como mide `@PlayersInRadius{r=3}`) tras avisar con una nube de partículas; usa el targeter `@PlayersInRadius` (alias `PIR`), que solo alcanza jugadores, así que **no daña a otros mobs**: el guardián está pensado para pelear contra jugadores. Si en el futuro un rival no jugador debe recibir el golpe (participantes como entidades, M17), usa `@target` o un targeter de entidades **(pendiente de servidor, sesión 15 del paquete de pruebas)**.

Pasos (los mismos de `plugins/ThePredict/examples/README.txt`):

1. Copia los tres archivos a `plugins/MythicMobs/Mobs/`, `plugins/MythicMobs/Skills/` y `plugins/MythicMobs/AIConfig/Profiles/`.
2. `/mm reload` y `/thepredict status`. Con `Predictor: false` el perfil se lee pero no está activo. En una instalación sin otros perfiles, la sección `Catalog` muestra (el número de revisión varía): `Profiles: SETUP_PENDING (revision N, 1 profiles read, 0 active, 0 errors, 0 warnings): no mob targeted by a profile has Predictor: true; set it in MythicMobs and reload MythicMobs`.
3. `/mm m spawn ThePredictGuardian 1`: con `Predictor: false` es un zombie normal de MythicMobs que te persigue y golpea **(pendiente de servidor)**.
4. Cambia a `Predictor: true` en el mob copiado, `/mm reload` y `/thepredict status`: `Profiles: LOADED (revision N, 1 profiles read, 1 active, 0 errors, 0 warnings)`.
5. Pelea en `SURVIVAL` y usa `/thepredict inspect nearest` (busca en 16 bloques). Sin clave de Jev el mob pelea en nivel `LOCAL`, sin costo; con clave y el servicio `READY`, en nivel `JEV` **(pendiente de servidor)**. Si hay agregados, `inspect` puede mostrar la fila de `outcome_stats` de la táctica actual.
6. Para probar una táctica sin Jev, con el permiso `thepredict.debug` (o desde la consola con la UUID del mob): `/thepredict debug intent nearest keep_distance` y `/thepredict debug release nearest`.

Para quitar el ejemplo, borra los tres archivos copiados y ejecuta `/mm reload`.

## 4. Perfil v2: referencia completa

Un archivo de perfil es un `.yml` o `.yaml` de `plugins/MythicMobs/AIConfig/Profiles/` con `schemaVersion: 2` y uno o más perfiles. La clave de cada perfil es su ID (en `snake_case`). La configuración es estricta: un campo desconocido es un error con archivo y ruta (`is not a known field`), y un perfil con error se ignora sin afectar a los demás. Los archivos `*.yml.example` se ignoran.

Precedencia de los valores: defaults internos → `profileDefaults` de `config.yml` → perfil. Los límites globales (`decision.*`) nunca se amplían desde un perfil.

### 4.1 Campos del perfil

| Campo | Obligatorio | Valores | Qué hace |
|---|---|---|---|
| `MythicID` | sí | ID exacto de un mob de MythicMobs | Mob que controla el perfil. Dos perfiles para el mismo mob se excluyen los dos. |
| `TypeOfMythicID` | no | solo `MOB` | Compatibilidad con el esquema 1. |
| `Type` | no | solo `NORMAL` | Modo del perfil; la 0.0.1 no tiene otros. |
| `Objective` | sí | texto en inglés | Qué intenta conseguir el mob. Llega a Jev ([sección 7](#7-objective-y-description-en-inglés)). |
| `Choices` | no | mapa de choices | Skills (o golpe vanilla) que pueden usar las tácticas. |
| `Tactics` | sí | de 1 a 255 tácticas | Lo que Jev o el modo local pueden elegir. |
| `Contexts` | no | hasta 16 contextos | Limitan las tácticas según la situación ([sección 5](#5-contextos-con-condiciones-de-mythicmobs)). |
| `Movement` | no | ver 4.4 | Distancias y velocidades de los movimientos. |
| `Health` | no | ver 4.5 | Umbrales de salud. |
| `Threat` | no | ver [sección 11](#11-amenaza-enriquecida) | Pesos de la amenaza enriquecida. |
| `BaseAI` | no | líneas con sintaxis de `AIGoalSelectors` | IA base (nivel 4). |
| `Timing` | no | ver 4.6 | Confianza mínima, margen de cambio, detalle de las preguntas, comprobación de idoneidad, modo de lanzamiento y horizonte de este perfil. |
| `Participants` | no | ver [4.9](#49-participantes-y-objetivos-participants) | Qué jugadores y qué otras entidades vivas pueden ser participantes y objetivos de los mobs de este perfil (M17). Sin la sección, los de `config.yml` (por defecto, solo jugadores en `SURVIVAL` o `ADVENTURE`, como en la 0.0.1). |
| `Difficulty` | no | `Mode`, `TargetSeconds`, `Tolerance`, `MinRung`, `MaxRung`, `StartRung`, `InFight`, `Learn`, `NativeLevel` | Tiempo objetivo y palancas (M22). Sin la sección se hereda `difficulty` de `config.yml`. `TargetSeconds` es opcional (5 a 3600). `Tolerance` se valida, pero esta versión no la lee: se usa siempre `difficulty.toleranceRatio`. El jugador no elige el preset: en el mob, con `Predictor: true`, `PredictorDifficulty` vale `Fácil`, `Normal`, `Difícil`, `Caos` o `Vacío`, y `PredictorEntropy` es `true` o `false`. |

En las claves de modo de un perfil (`Stances.Native`, `Difficulty.NativeLevel`, `Decision.Pruning`, `Decision.Diversity`, `Zone.Mode` y el resto), `off` y `on` pueden ir sin comillas: YAML los lee como `false` y `true`, y ThePredict los entiende como `off` y `on`.

### 4.2 `Choices`

| Campo | Default | Valores | Qué hace |
|---|---|---|---|
| `Kind` | `MYTHIC_SKILL` | `MYTHIC_SKILL` o `VANILLA_MELEE` | `MYTHIC_SKILL` lanza una skill; `VANILLA_MELEE` da un golpe vanilla (`LivingEntity#attack`) y no admite `MythicID`. |
| `MythicID` | obligatorio con `MYTHIC_SKILL` | ID exacto de una skill | La skill ([sección 9](#9-skills-la-línea-intention-el-cooldown-nativo-y-cd)). |
| `TypeOfMythicID` | `SKILL` | solo `SKILL` | Compatibilidad con el esquema 1. |
| `RequiresTarget` | `true` | `true` o `false` | Si la choice necesita objetivo. Una defensa o una curación propia suele ser `false`. |
| `CD` | `0` | ticks, `>= 0` | Cooldown extra por instancia, solo por compatibilidad. Usa el `Cooldown` nativo de la skill. |

**El golpe vanilla mientras ThePredict controla.** Mientras ThePredict controla el mob, su meta base de ataque no golpea (M05): el golpe vanilla solo llega como una choice `Kind: VANILLA_MELEE`, que ocupa la táctica como cualquier skill. Un jefe clásico de MythicMobs, en cambio, suma el golpe vanilla de `meleeAttack` a las skills que lanza por trigger. Por eso un jefe de ThePredict pierde ese daño pasivo cuerpo a cuerpo frente al mismo jefe clásico. ThePredict no da golpes vanilla automáticos, por decisión del usuario ([DT-42](../DocumentacionParaDesarrollar/Implementacion-0.0.1/M01-Registro-de-decisiones.md#dt-42-opción-del-motor-golpes-vanilla-en-alcance-durante-approachhold-sin-consumir-la-choice), descartada): **pon el daño cuerpo a cuerpo en una skill** de la táctica (una skill con línea `Intention` de golpe, o que conserve `MELEE_ATTACK` como historia) o en una choice `Kind: VANILLA_MELEE`. Esa skill sale al ritmo de su `Cooldown` (ver «Ritmo» en la [sección 9](#9-skills-la-línea-intention-el-cooldown-nativo-y-cd)), así que su `Cooldown` y su daño fijan cuánto pega el jefe. Tenlo en cuenta al fijar `Damage` y al comparar con un jefe clásico; el duelo de la sección [4.10](#410-duelos-seguros-m18) lo iguala con `Damage: 0` en los dos lados.

### 4.3 `Tactics`

| Campo | Default | Valores | Qué hace |
|---|---|---|---|
| `Movement` | obligatorio | `APPROACH`, `KEEP_DISTANCE`, `RETREAT`, `HOLD`, `FLANK`, `NONE` | Cómo se mueve el mob ([sección 8](#8-movimientos-y-metas-nativas)). `FLANK` exige `Choice`. |
| `Choice` | ninguna | ID de una choice del perfil | La skill que lanza la táctica. |
| `Description` | ninguna | texto en inglés | Qué hace la táctica, para Jev. |
| `Priority` | `50` | 0 a 100 | Orden del modo local: entre las tácticas elegibles gana la de mayor prioridad. |

### 4.4 `Movement` (bloques y multiplicadores de velocidad)

| Campo | Default | Regla |
|---|---|---|
| `MeleeReach` | `2.5` | `> 0`. Distancia de cuerpo a cuerpo: banda `MELEE`, parada de `APPROACH` y alcance de las choices de golpe. |
| `Band.Inner` | `5` | `> MeleeReach`. Borde interior de `KEEP_DISTANCE`. |
| `Band.Outer` | `9` | `> Band.Inner`. Borde exterior de `KEEP_DISTANCE`. |
| `RangedReach` | `16` | `>= Band.Outer`. Hasta aquí el objetivo está en banda `RANGED`; más lejos, `BEYOND`. |
| `Retreat.Distance` | `10` | `> 0`. Distancia que intenta abrir `RETREAT`. |
| `Retreat.Speed` | `1.2` | 0,1 a 3. |
| `Approach.Speed` | `1.15` | 0,1 a 3. También la velocidad de `KEEP_DISTANCE` y de vuelta a la arena. |
| `Arena.Radius` | sin arena | `> Band.Outer`. Si existe, el mob vuelve a su punto de aparición cuando se aleja más de este radio. |

### 4.5 `Health` (proporción de vida, sobre salud más absorción)

| Campo | Default | Regla |
|---|---|---|
| `Critical` | `0.3` | `0 < Critical < Wounded`. Igual o por debajo, banda `CRITICAL` (también para jugadores). |
| `Wounded` | `0.6` | `< 1`. Igual o por debajo (y por encima de `Critical`), banda `WOUNDED`; por encima, `HEALTHY`. |

### 4.6 `Timing`

| Campo | Default | Regla |
|---|---|---|
| `MinimumConfidence` | `decision.minimumConfidence` (`0.45`) | 0 a 0,99. Una respuesta de Jev con menos confianza se descarta. |
| `SwitchMargin` | `decision.tacticSwitchMargin` (`0.15`) | 0 a 0,99. Para cambiar de táctica, la nueva debe superar a la vigente en este margen de probabilidad. |
| `QuestionDetail` | `decision.questionDetail` (`full`) | `compact`, `standard` o `full` (sin distinguir mayúsculas). Cuánto dice cada opción de este perfil a Jev y cuánto cuesta (ver 4.8 y la sección 7.1 de la guía del administrador). Solo afecta a las preguntas sobre mobs de este perfil y nunca amplía los límites globales de tokens. |
| `FitChecks` | `decision.fitChecks` (`false`) | `true` o `false`. Añade, en la misma solicitud, una pregunta `noul` de idoneidad por opción ("¿es esta táctica sensata ahora?"), que puede vetar una opción o rescatar una respuesta de confianza baja. **Solo se envía con `QuestionDetail: full`**: con otro nivel se ignora y la carga avisa (`is ignored: fit checks are only sent with QuestionDetail full, and this profile uses compact`). Con `decision.fitChecksAllowed: false` es un error (`must be false while decision.fitChecksAllowed is false`). Cuesta tokens: úsala solo si el re-banco la justifica. |
| `CastMode` | `decision.castMode` (`any_ready`) | `any_ready` o `intent_only` (sin distinguir mayúsculas; un guion vale como `_`). Con `any_ready`, cada skill con línea `Intention` válida sale en el primer tick en que está lista, además de la choice de la táctica. Con `intent_only` solo sale la choice de la táctica: es una exclusión voluntaria de este perfil y gana al valor global. |
| `ReadyHorizon` | `decision.readyHorizonTicks` (`0`) | Ticks, de 0 a 100. Con `0` la pregunta ve las mismas tácticas que hoy. Un valor mayor solo afecta a la pregunta, nunca al planificador local. El default de entrega es 0; subirlo queda pendiente de la evaluación E2, no medida en esta entrega. |
| `TickLayer` | `decision.tickLayer.mode` | `casts`, `reactions` o `full`. Si el perfil pide más que el global, la carga avisa y usa el menor. |
| `Reactions` | `all` | `all`, `survival` o `none`. Qué bandas de reacción se evalúan. |
| `ReactionDelay` | `0` | Ticks, de 0 a 20. El disparador tiene que mantenerse ese tiempo. Contra jugadores manda además el suelo de reacción del preset de dificultad. |
| `Cadence` | `reactive` | `reactive` o `standard` (ahorro). `standard` ignora `IntentHorizon` con un aviso. |
| `ReactiveInterval` | `6` | Ticks, de 4 a 20. Mínimo entre preguntas reactivas. |
| `IntentHorizon` | `min(10, maximumIntentDurationTicks)`: 10 con los defaults | Ticks, de 4 a 200 y no mayor que la vigencia máxima de la intención (`profileDefaults.maximumIntentDurationTicks`). |

La sección `Reactions` del perfil (fuera de `Timing`) tiene una clave, `Ahead`: las bandas que disparan antes de la choice de la intención (`survival`, `opportunity` o `spacing`). El default es supervivencia y oportunidad.

El latido del encuentro, los intervalos y las contingencias son globales (guía del administrador); un perfil no los cambia. **Nota sobre `MinimumConfidence` y `QuestionDetail`:** desde el re-banco de M16.10 (decisión del usuario del 2026-09-30) los defaults son `full` y `0.45`: con las 60 etiquetas del banco revisadas, el brazo `full` cumple la regla de M16.7 a 0,45 (41 de 48 escenarios informativos conservados, 90,2 % aceptables, límite inferior de Wilson 0,775, ninguno prohibido). Es una muestra modesta (48 escenarios informativos con 2 repeticiones). `compact` sigue disponible por perfil (`QuestionDetail: compact`) y cuesta la mitad de tokens. **Cuándo subirla en un perfil:** en un jefe cuyo error es caro (una táctica mala le cuesta el combate), un umbral más alto descarta más respuestas dudosas y deja más decisiones al modo local; bajarla sin evidencia propia no se recomienda.

### 4.7 Un perfil con todas las secciones

```yaml
schemaVersion: 2
guardian_full:
  MythicID: ThePredictGuardian
  TypeOfMythicID: MOB
  Type: NORMAL
  Objective: Protect the altar and punish players who attack it from range
  Choices:
    melee: { MythicID: ThePredictGuardianMelee, TypeOfMythicID: SKILL, RequiresTarget: true }
    ranged: { MythicID: ThePredictGuardianRanged }
    defense: { MythicID: ThePredictGuardianDefense, RequiresTarget: false }
    punch: { Kind: VANILLA_MELEE }
  Tactics:
    press_and_strike: { Movement: APPROACH, Choice: melee, Priority: 70 }
    brawl: { Movement: APPROACH, Choice: punch, Priority: 40 }
    keep_distance_and_shoot:
      Movement: KEEP_DISTANCE
      Choice: ranged
      Description: Stay outside melee reach and shoot while the target approaches
      Priority: 60
    retreat_and_defend: { Movement: RETREAT, Choice: defense, Priority: 90 }
    flank_and_strike: { Movement: FLANK, Choice: melee }
    hold: { Movement: HOLD, Description: Wait for the target to come closer, Priority: 10 }
  Contexts:
    wounded:
      Conditions:
      - healthpercent{p=<30%}
      Priority: 95
      Tactics: [retreat_and_defend, keep_distance_and_shoot]
    idle:
      Priority: 10
      Tactics: [hold]
      DirectTactic: hold
  Movement:
    MeleeReach: 2.5
    RangedReach: 16
    Band: { Inner: 5, Outer: 9 }
    Retreat: { Distance: 10, Speed: 1.2 }
    Approach: { Speed: 1.15 }
    Arena: { Radius: 24 }
  Health: { Critical: 0.3, Wounded: 0.6 }
  Threat: { AssistsOthers: 1.5, RangedHarass: 1.0, BlockedHits: 0.25, UnreachableDecayPerSecond: 2.0, MaxPerWindow: 20.0 }
  BaseAI:
  - 1 meleeAttack
  - 2 randomStroll
  Timing: { MinimumConfidence: 0.45, SwitchMargin: 0.15, QuestionDetail: full, FitChecks: false, CastMode: any_ready, ReadyHorizon: 0 }
  Participants:
    PlayerGameModes: [SURVIVAL, ADVENTURE]
    Entities:
      Allow: ['kind:MONSTER', 'faction:altar_raiders']
      Deny: ['category:ANIMAL']
      PerceiveNonTargets: false
```

### 4.8 Cómo ve Jev cada opción (`QuestionDetail`)

ThePredict nunca envía a Jev dos opciones que se lean igual, ni el nombre de una choice ni números. Lo que dice de cada táctica depende del detalle del perfil:

| Detalle | Criterio de cada opción | Instrucción |
|---|---|---|
| `compact` | Un texto de como mucho 120 caracteres: `<movimiento corto>; <skill corta>` y como mucho **una** frase `poor fit now: …` calculada en código (por ejemplo `shield would block it`). La `Description` no aparece salvo como extracto `note: …` cuando dos opciones se leerían igual. | Texto corto: `Which tactic best serves mob_1's objective now?` |
| `standard` | Un objeto con las mismas claves en todas las opciones, sin las vacías: `movement`, `skill`, `best_when`, `risks` y `note` (tu `Description`) | Objeto con la pregunta, la hipótesis de una contingencia (`assume`) y las rutas del estado que debe mirar Jev (`objective`, `judge_by`) |
| `full` (default desde M16.10) | `standard` más `fits_now` y `poor_fit_now` (hasta 3 frases cada una: qué del momento favorece o desaconseja la opción, calculado en código) | `standard` más una frase fija de enfoque (`focus`) |

Ejemplo `compact` (guardián, objetivo bloqueando a distancia cuerpo a cuerpo): `press_and_strike` se lee `close in; melee hit, medium damage; poor fit now: shield would block it`, `flank_and_strike` `circle to the target's side; melee hit, medium damage`, `retreat_and_defend` `retreat; self heal` y `approach` `close in`. Cuando dos opciones colisionan, cada una recibe, por orden, un extracto de su descripción (`note:`), la banda de alcance de su skill (`reach: melee|short|ranged|far`) o su primera condición `BestWhen` (`best when target shielding`), y puede llegar a 160 caracteres.

**Tácticas que Jev no puede distinguir.** Dos tácticas con el mismo movimiento, la misma `Description` y el mismo propósito de skill (o ninguna skill) son idénticas para Jev: solo se envía la de mayor prioridad y el vinculador avisa `Tactics.<a> and Tactics.<b> describe the same option to Jev; Jev only sees '<a>'`. Si dos tácticas siguen leyéndose igual en el texto final (por ejemplo, en `compact` sin descripción ni propósito que las separe), también se envía solo una: dales una `Description` o un propósito distintos. Cada pregunta lleva como mucho `decision.maxOptionsPerQuestion` opciones (8 por defecto), elegidas por prioridad; **la táctica vigente del mob nunca se corta**, para que el margen de cambio siga comparando con ella.

**Contingencias con opciones hipotéticas.** Una contingencia (por ejemplo "si tu salud se vuelve crítica") solo ofrece las tácticas que serían elegibles **en esa situación**: los contextos atajo se evalúan con la hipótesis (con salud crítica, `low_health_phase` limita el ejemplo a `retreat_and_defend` y `keep_distance_and_shoot`) y, con el objetivo en cuerpo a cuerpo, las skills de alcance cuerpo a cuerpo cuentan como dentro de alcance. Los contextos con condiciones nativas de MythicMobs no se reevalúan bajo la hipótesis: conservan su estado real. Con `compact`, una contingencia ofrece como mucho 5 opciones; con menos de 2 no se pregunta.

### 4.9 Participantes y objetivos (`Participants`)

Desde M17 un participante (lo que el mob percibe, su objetivo y contra quien lucha) puede ser **cualquier entidad viva**, no solo un jugador. Quién puede serlo lo decide una política pequeña construida solo con distinciones nativas de Minecraft, Paper y MythicMobs. **Sin configurar nada todo es como en la 0.0.1**: solo jugadores en `SURVIVAL` o `ADVENTURE`, y las solicitudes a Jev son idénticas byte a byte (lo demuestra la prueba de paridad `EntityGeneralizationParityTest`). Un duelo mob contra mob es solo un perfil que admite entidades.

La política global está en `participants` de `config.yml` ([guía del administrador, 4.4](Guia-del-administrador.md#44-participants-quién-puede-ser-participante-u-objetivo)). Un perfil la afina con su sección opcional:

| Campo | Default | Regla |
|---|---|---|
| `PlayerGameModes` | los de `participants.playerGameModes` | Subconjunto de la lista global (`SURVIVAL`, `ADVENTURE`, `CREATIVE`, `SPECTATOR`); un modo fuera de ella es un error. `[]`: los mobs del perfil nunca perciben ni atacan jugadores. |
| `Entities` | sección opcional | Entidades que no son jugadores. |
| `Allow` | `[]` | Selectores que **se suman** a `participants.entities.allow`. Se ignoran con un aviso si el administrador puso `participants.entities.profilesMayAllow: false`. |
| `Deny` | `[]` | Selectores que **se suman** a `participants.entities.deny`; nunca participan. |
| `PerceiveNonTargets` | `participants.entities.perceiveNonTargets` | `true` solo si el global lo permite (si no, es un error). Con `false` el mob solo percibe a su **objetivo** entre las entidades; con `true` percibe además las entidades admitidas cercanas (como mucho `participants.entities.maxCandidatesPerMember` por mob). |

**Selectores** (texto `prefijo:valor`; hasta 16 por lista). La configuración solo comprueba la forma; los valores se resuelven al vincular, en cada revisión del catálogo, y uno desconocido da un aviso con su ruta (`Participants.Entities.Allow[0]`) y se ignora:

| Selector | Coincide si | API nativa |
|---|---|---|
| `type:<clave>` (`type:minecraft:zombie`; sin espacio de nombres es `minecraft`) | el tipo de la entidad es ese | `Entity#getType()`, `Registry.ENTITY_TYPE` |
| `tag:<clave>` (`tag:minecraft:undead`, `tag:mipack:duelistas`) | el tipo está en la etiqueta de tipos, vanilla o de un datapack | `Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, clave, EntityType.class)`, leída al vincular: tras `/minecraft:reload` haz `/mm reload` (DT-39) |
| `category:<SpawnCategory>` (`MONSTER`, `ANIMAL`, `WATER_ANIMAL`, `WATER_AMBIENT`, `WATER_UNDERGROUND_CREATURE`, `AMBIENT`, `AXOLOTL`, `MISC`) | su categoría de aparición | `Entity#getSpawnCategory()` |
| `kind:LIVING`, `kind:MOB`, `kind:ENEMY`, `kind:MONSTER`, `kind:ANIMAL` | es una entidad viva, un `Mob`, un `Enemy` (más amplio que `Monster`: incluye slime, ghast, phantom, hoglin y shulker), un `Monster` o un `Animals` | interfaces de `org.bukkit.entity` |
| `mythic:*` / `mythic:<IdDeMob>` | es un mob de MythicMobs / de ese tipo exacto (distingue mayúsculas) | `MobExecutor.getActiveMob(UUID)` |
| `faction:<nombre>` | su facción de MythicMobs, sin distinguir mayúsculas | `ActiveMob.getFaction()` (`Faction:` del mob, mecánica `setfaction`) |
| `team:<nombre>` | su equipo del scoreboard principal, sin distinguir mayúsculas | `Scoreboard#getEntityTeam(Entity)` (`/team`) |
| `tagged:<etiqueta>` | tiene esa etiqueta de `/tag`, exacta | `Entity#getScoreboardTags()` |
| `predictor` | es un mob vinculado a ThePredict (un `ActiveMob` cuyo tipo tiene perfil con `Predictor: true`) | catálogo de perfiles de ThePredict |

**Orden de decisión** (la primera regla que decide, gana): una entidad inválida o muerta no participa; el propio mob y los miembros de su encuentro (sus aliados) tampoco; un jugador participa si su modo de juego está en la lista, **y nada más se aplica a jugadores**; un soporte de armadura nunca; lo que coincide con un `Deny` nunca; lo que es **del mismo bando** que el mob nunca; lo que coincide con un `Allow` sí; el resto, no.

**Bandos.** Dos entidades son del mismo bando si las dos son mobs de MythicMobs con la misma facción (sin distinguir mayúsculas) o, si no, si están en el mismo equipo del scoreboard principal. Los bandos nunca se aplican a jugadores, aunque estén en un equipo. **Un encuentro es un bando**: sus miembros son aliados y sus participantes, rivales; un mob nunca se une a un encuentro que lo lista como participante, así que dos mobs de ThePredict que se enfrentan forman dos encuentros, cada uno con su propia solicitud y su propio punto de vista.

**Mobs de ThePredict sin facción ni equipo.** Si un perfil admite a otros mobs de ThePredict (con `kind:MONSTER`, `kind:MOB`, `predictor`…) y esos mobs no tienen `Faction` ni equipo de scoreboard, **son rivales entre sí**. Consecuencias: dos de ellos que pelean contra el mismo jugador forman encuentros separados, cada uno con su solicitud, y si su `AITargetSelectors` (por ejemplo `- 1 monsters`) los elige, se atacan entre ellos. Para un grupo que debe pelear junto, dales la misma `Faction:` en MythicMobs y apunta con `- 1 nearestOtherFactionMonsters` en lugar de `- 1 monsters`: MythicMobs no elige mobs de la propia facción, y ThePredict los trata como aliados.

**Un jugador en la tabla de amenaza que la política no admite.** Si encabeza la tabla de amenaza de un mob un jugador que la política no admite (por ejemplo, en creativo), el mob queda sin objetivo para ThePredict, igual que en la 0.0.1: ThePredict no pasa entonces a `Mob#getTarget()`. Es seguro: la sonda y la captura filtran por la política, la intención vigente se invalida al perder el objetivo y nunca hay un cast contra un jugador no admitido. Con una entidad que la política no admite, en cambio, ThePredict sí pasa a `Mob#getTarget()`.

**Qué recibe Jev.** Un jugador es `player_N` y cualquier otra entidad `entity_N`, con un contador por tipo; de una entidad solo viaja además su **especie**, la clave de su tipo sin espacio de nombres, como primer hecho (`entity_1 is a zombie`, `entity_2 is a wither skeleton`) y en los hechos del objetivo (`target is a zombie`). El recuento dice `one player, one other creature and one mob are involved`. **Nunca** viajan el nombre personalizado, el `Display`, el ID de MythicMobs, la facción ni el UUID: no mejoran la decisión y cuestan tokens y precisión. Tus salidas locales (`inspect`, registro) sí muestran la especie: `Target: entity_1 (zombie) health …`.

**Qué ve un mob de otro mob** (✓ = como con un jugador; ≈ = equivalente nativo; — = no existe y no se envía):

| Hecho | Jugador | Mob que usa objetos (esqueleto, pillager, piglin, bruja, zombi con equipo) | Otra entidad viva |
|---|---|---|---|
| Salud y armadura | ✓ | ✓ | ✓ |
| `RAISING_SHIELD`, `BLOCKING` | ✓ | ≈ objeto activo con `BLOCKS_ATTACKS`; bloquea tras `Math.round(blockDelaySeconds × 20)` ticks, como vanilla (DT-38). Ni vanilla ni MythicMobs hacen que un mob levante el escudo por sí solo | ≈ igual |
| `SHIELD_DISABLED`, `ATTACK_RECHARGING`, `PEARL_ON_COOLDOWN` | ✓ | — | — |
| `DRAWING_BOW`, `BOW_FULLY_DRAWN` | ✓ | ≈ objeto activo arco y su tiempo de uso | — |
| `CROSSBOW_LOADED`, `HOLDS_TOTEM` | ✓ | ≈ equipo | — |
| `CONSUMING` | ✓ | ≈ objeto activo consumible | — |
| `GLIDING`, `INVISIBLE`, `MACE_DIVE` | ✓ | ✓ / ≈ | ✓ / ≈ |
| `LONG_REACH_WEAPON` | ✓ | ≈ arma en mano | — |
| Tendencia | ✓ teclas y distancia | ≈ velocidad proyectada y distancia (DT-36) | ≈ igual |
| Alcanzable a pie, visible, flanqueable | ✓ | ✓ | ✓ |
| Fuera del alcance del objetivo | ✓ | ≈ perfil del rival de ThePredict, o la comprobación vanilla de un `Mob` (DT-37): su caja inflada ≈ 0,83 bloques solo en horizontal, o el `ATTACK_RANGE` de su arma (lanzas), cada eje horizontal por separado y solapando en altura; nada con arco, ballesta o tridente, con ataque a distancia nativo (esqueletos, bruja, blaze, ghast, evoker…) o si es un mob de MythicMobs sin perfil de ThePredict | ≈ la misma comprobación vanilla si es un `Mob`, salvo ataque a distancia nativo (blaze, ghast, creeper, guardián, shulker, warden…); nada si no es un `Mob` |
| Amenaza | ✓ | ✓ | ✓ |
| Especie | — | ✓ | ✓ |

Jev recibe **menos hechos** de una entidad, nunca hechos inventados. El objetivo lo sigue eligiendo MythicMobs o vanilla (tabla de amenaza, `AITargetSelectors`): para que un mob ataque a otras criaturas, dale un selector de objetivo nativo, por ejemplo `- 1 monsters` o `- 1 nearestSpecificFaction{f=blue}`, y admítelas en `Participants`.

Ejemplo de duelo de dos facciones (el mob de la facción contraria usa el mismo perfil con `faction:duel_red`):

```yaml
schemaVersion: 2
duel_blue_ai:
  MythicID: ThePredictGuardian
  Objective: Defeat the red duelist in front of you
  Choices:
    punch: { Kind: VANILLA_MELEE }
  Tactics:
    press: { Movement: APPROACH, Choice: punch, Description: Close in and hit the rival }
    hold: { Movement: HOLD, Description: Wait for the rival to come closer }
  Participants:
    PlayerGameModes: []
    Entities:
      Allow: ['faction:duel_red']
```

### 4.10 Duelos seguros (M18)

Dos mobs que se enfrentan entre sí, sin afectar nunca a jugadores ni a otros mobs, como el paquete de duelo del [paquete de pruebas](../DocumentacionParaDesarrollar/Implementacion-0.0.1/paquete-pruebas/archivos/13-duelo/) (`13-duelo`, sesión 18). Todo es nativo y está comprobado en local; en juego, **pendiente de servidor** (sesión 18, DT-40):

- **Una plantilla y variantes.** `DuelBase` lleva lo común (vida, daño, `Options`, `Modules`, `AIGoalSelectors` y las reglas clásicas en `Skills`); cada variante pone `Template: DuelBase` y solo lo suyo. MythicMobs 5.13.1 suma las listas de la plantilla y de la variante, primero las de la plantilla (javap de `MythicConfigImpl.merge`), y un valor que la variante pone gana. Así las cuatro variantes tienen exactamente las mismas estadísticas, metas base, reglas y skills.
- **Solo el rival, nunca jugadores**, por cuatro vías independientes:
  1. `AITargetSelectors` con `- 0 clear` y `- 1 nearestSpecificFaction{faction=<rival>}`. Un jugador solo cuenta como de una facción con el permiso `faction.<nombre en minúsculas>` y sin `mythicmobs.nofaction`; operadores y `*` tienen los dos, así que quedan fuera. Nunca des `faction.<rival>` a un jugador.
  2. `Modules: ThreatTable: false`: un golpe de un jugador no cambia el objetivo.
  3. Perfiles con `Participants: PlayerGameModes: []` y `Entities: Allow: ['faction:<rival>']` (sección [4.9](#49-participantes-y-objetivos-participants)): ThePredict nunca percibe, apunta ni lanza contra otra cosa.
  4. Todas las skills de daño van a `@target`, nunca a áreas.
- **Mismas reglas para los dos lados.** Las variantes clásicas lanzan las skills por reglas con temporizador; esas reglas empiezan por `- predictorcontrol false`, así que en las variantes de ThePredict solo actúan cuando ThePredict no controla (nivel 4). Sin `BaseAI` en el perfil, la IA base es la de los `AIGoalSelectors` de la plantilla, la misma que la de las clásicas (M03). Mientras ThePredict controla, la meta de ataque base no golpea (M05), así que la plantilla pone `Damage: 0` y el perfil no tiene choice `VANILLA_MELEE`: todo el daño de los dos lados sale de las cinco skills (sección [4.2](#42-choices)).
- **Contextos que no dejan retirarse con la vida llena.** El plan local elige por prioridad entre las tácticas elegibles, y una curación está "lista" en cuanto sale de cooldown aunque su condición de vida se compruebe al lanzar; por eso el perfil tiene un contexto `healthy` (`Conditions: ['healthpercent{p=>50%} true']`, prioridad 80) que deja fuera `retreat_and_heal` por encima del 50 % de vida. La vida en porcentaje se escribe `healthpercent{p=<N%}` o `{p=>N%}`: `health{h=…}` compara la vida absoluta y en MythicMobs 5.13.1 falla con `%` (DT-40 f).
- **Que siempre termine.** Una skill `~onSpawn` con `delay 1800` y `remove @self` cierra la ronda a los 90 s; la limpieza se hace con `/mm mobs kill <tipo>` por cada variante, nunca `killall`.
- **Arena en un mundo propio** con `spawn_mobs false` (sin aparición natural y sin refuerzos de zombi, que exigen esa regla: javap de `Zombie.hurtServer`) y `advance_time false`; las reglas de juego son por mundo y no cambian el mundo de los jugadores. Observadores en `SPECTATOR`.
- **Ayudas visuales nativas:** `glow{color=RED;duration=2400} @self ~onSpawn` (obsoleta en 5.13.1 pero registrada; su duración por defecto es de 200 ticks, por eso 2400), una `BossBar` con `<caster.predictor.tactic>` y `<caster.predictor.state>`, partículas o un sonido por `~onSignal:PREDICTOR_<TÁCTICA>` y un marcador con `modifyscore{objective=duel;entry=<lado>_wins;action=ADD;value=1} @self ~onDeath`.
- **Registro:** `/thepredict duel start` (guía del administrador, sección 8.3) guarda en tiempo real cada decisión, cast, golpe, defensa, retirada y ronda.

## Combate por tick

La capa por tick ejecuta la intención que ya eligió Jev. No cambia la táctica, el nivel ni el vencimiento, salvo la prórroga de la cadencia `reactive` (misma táctica, plazo más largo, sin nueva pregunta).

| Reacción | Banda | Qué skill quiere | Por qué |
|---|---|---|---|
| `EVADE_CRITICAL` | supervivencia | un papel `ESCAPE`, `DEFENSE` o `SUSTAIN` | Con la vida crítica y el rival encima, el jefe necesita salir, cubrirse o sostenerse. |
| `DODGE_SHOT` | supervivencia | solo movimiento (no lanza) | Un disparo visto se esquiva con el micro, no con otra skill. Solo se evalúa con `TickLayer: full`. |
| `PUNISH_CONSUMING` | oportunidad | `INTERRUPT`, `GAP_CLOSE` o `DAMAGE` | El rival bebe o come: conviene cortarlo. `GAP_CLOSE` puede salir también con `HOLD` o `NONE`. |
| `PUNISH_RECHARGE` | oportunidad | golpe `MELEE` de daño o remate | Solo contra un jugador, que es el único cuya recarga de ataque se puede leer. |
| `RESPECT_SHIELD` | oportunidad | `CONTROL` o una skill que ignora el escudo | Un escudo levantado no se come con el mismo golpe. |
| `FINISH` | oportunidad | `FINISHER` | La vida del objetivo ya es crítica. |
| `SPACE_OUT` | espacio | solo movimiento | Abrir hueco. Solo con `full`. |
| `EDGE_DANGER` | supervivencia | solo movimiento | El mob pisa un borde letal y se aparta. Solo con `full` y con el modo de zona efectivo en `guard` o `full`. |
| `HAZARD_PUSH` | oportunidad | `CONTROL` o una skill que desplaza al objetivo | Empujar a una entidad hacia un peligro, con `Zone.HazardPush: exploit` (sección [Terreno y zona](#terreno-y-zona)). |
| `EXPLOIT_PATTERN` | oportunidad | la contramedida del patrón activo | Un patrón del objetivo está activo. Solo con `Patterns.Mode` efectivo `bias` o `full`, dentro de la cuota y del descanso (sección [Patrones del enemigo](#patrones-del-enemigo)). Va después de las demás oportunidades. |

`ESCAPE` con `EVADE_CRITICAL` puede salir con cualquier movimiento salvo `APPROACH`, incluidos `HOLD` y `NONE`.

`Timing.TickLayer` es `casts` (solo lanzar, como M19), `reactions` (el default: reacciones y precisión de impacto) o `full` (además, micro-comportamientos). Si el perfil pide más que `decision.tickLayer.mode`, la carga avisa y usa el menor. `Timing.Reactions` es `all`, `survival` o `none`. La sección `Reactions.Ahead` lista las bandas que se intentan antes de la choice de Jev; el default es `SURVIVAL` y `OPPORTUNITY`.

La cadencia `Timing.Cadence: reactive` es el default: el horizonte de la intención es corto (`IntentHorizon`, 10 ticks) y se prorroga si la situación no cambió. Cuesta más preguntas. En el peor caso, con los topes por defecto, cabe **≈ 1 encuentro por mundo (300 / 200 ≈ 1,5)** y el techo publicado es de hasta **≈ 0,95 USD por mob y hora**. `standard` es el modo ahorro: la vigencia es la de siempre y, con los mismos topes, unos **5** encuentros por mundo. Los duelos y las sesiones de pago fijan `standard`.

Los impulsos no son una velocidad que ponga ThePredict. Se escriben como skills del autor (`leap`, `velocity` y el resto de mecánicas de MythicMobs). ThePredict no llama a `setVelocity` ni a `knockback`.

El micro (`thepredict:micro`, prioridad 3) solo existe con `full`. Rodea, se aparta, entra o retrocede con destinos cortos del `Pathfinder`. `HOVER` solo contra un jugador. Contra el aire o un golpe recibido no pide ruta. La meta de Paper se evalúa cada 2 ticks: el retraso entre la vista y el movimiento puede ser de 0 a 2 ticks (pendiente de servidor, DT-50).

## 5. Contextos con condiciones de MythicMobs

Un contexto limita las tácticas elegibles cuando se cumple. Si hay varios activos, manda el de mayor `Priority` (con prioridades iguales gana el declarado primero, y el catálogo avisa: `has the same Priority 50 as context '…'; the one declared first wins when both are active`). Sin contexto activo, todas las tácticas son elegibles.

| Campo | Regla |
|---|---|
| `Conditions` | Líneas de condiciones de MythicMobs, al menos una. Prohibido en los contextos atajo. |
| `Priority` | 0 a 100; default 50. |
| `Tactics` | Al menos una táctica del perfil, sin repetir. |
| `DirectTactic` | Opcional; debe estar en `Tactics`. En modo local se elige directamente. |

**Contextos atajo**, calculados sin condiciones (no admiten `Conditions`):

| Nombre | Activo cuando |
|---|---|
| `idle` | El mob no tiene objetivo percibido. |
| `attacking_phase` | El objetivo está en la banda `MELEE` (a `MeleeReach` o menos). |
| `tracking_enemy` | El objetivo está más lejos que `MeleeReach`. |
| `low_health_phase` | La salud del mob está en la banda `CRITICAL`. |

**Cómo se evalúan las condiciones nativas.** ThePredict las evalúa sobre el propio mob con `SkillCondition.evaluateEntity`, sin construir el contexto de una skill. Por eso:

- Funcionan las condiciones de entidad (`IEntityCondition`), por ejemplo `healthpercent{p=<30%}` **(pendiente de servidor, DT-24)**.
- **Las condiciones que solo existen para el contexto de una skill (dependen del trigger, del objetivo de la skill o de metadatos) quedan siempre falsas** en un contexto de ThePredict. No las uses aquí; úsalas en las `Conditions` de la skill. **(Pendiente de servidor, DT-24.)**
- Una línea que MythicMobs rechaza desactiva solo ese contexto, con un error del catálogo (`MythicMobs rejected the condition '…'; context '…' is disabled`); una condición de otro plugin que nadie resuelve se desactiva un tick después (`no plugin resolved the condition '…'`).

## 6. Nombres de tácticas

El ID de una táctica **llega a Jev como nombre de la opción**, así que debe describir la acción en inglés semántico. Formato: `snake_case`, patrón `[a-z][a-z0-9_]{2,39}` (de 3 a 40 caracteres, empieza por letra). No se puede usar `none` ni el prefijo `debug_`, reservados.

| Bien | Mal | Por qué |
|---|---|---|
| `press_and_strike` | `t1`, `opcion_a` | Jev solo ve el nombre: sin significado no hay decisión útil. |
| `keep_distance_and_shoot` | `KeepDistance`, `keep-distance` | No cumple el patrón (mayúsculas o guion). |
| `retreat_and_defend` | `retirarse` | Jev razona en inglés. |
| `flank_and_strike` | `debug_flank`, `none` | Reservados. |

Los IDs de choices y contextos (`melee`, `wounded`) son locales al perfil y no llegan a Jev; patrón `[a-z][a-z0-9_]{0,39}`.

## 7. `Objective` y `Description` en inglés

Jev se entrenó sobre todo en inglés y otros idiomas tienen menor precisión (plan §6). ThePredict envía a Jev `Objective`, el nombre de cada táctica, su `Description`, su movimiento y para qué sirve su skill (la línea `Intention`, dicha con frases fijas en inglés y sin números ni nombres de primitiva), nunca nombres ni UUID de jugadores. Escribe `Objective` y `Description` en inglés claro y corto.

El catálogo avisa (sin bloquear) si un texto parece no estar en inglés: `may not be in English (contains the word 'objetivo', which is common in other languages); Jev reads English` o `(contains the non-ASCII character 'ñ')`.

Con el detalle `compact`, la `Description` solo llega a Jev como extracto (`note:`, sus primeros 40 caracteres) cuando hace falta para distinguir dos opciones; con `standard` y `full` llega entera como `note` (sección [4.8](#48-cómo-ve-jev-cada-opción-questiondetail)). Pon lo esencial al principio.

**Tamaño y escritura.** ThePredict estima el tamaño de cada solicitud a 1,5 caracteres por token, calibrado con la cuenta real sobre texto en inglés (1,59 a 1,82 observados). Un texto en otras escrituras, por ejemplo CJK, puede tener menos caracteres por token y la estimación se quedaría corta: otra razón para escribir en inglés.

## 8. Movimientos y metas nativas

Con `Predictor: true`, ThePredict sustituye las metas de movimiento del mob por un conjunto generado desde el perfil y lo instala con la API de IA de MythicMobs (`inspect` muestra `Goals: installed for revision <n>`). Cada meta táctica solo corre mientras la intención vigente pide su movimiento (condición `predictormove`). Solo se generan las metas de los movimientos que usa alguna táctica. **(Instalación y reinstalación pendientes de servidor, DT-05 y DT-15.)**

| Prioridad | Meta generada | Movimiento |
|---|---|---|
| 0 | `clear` | — |
| 1 | `float` | Siempre |
| 2 | `goToSpawnLocation{maxrange=<Arena.Radius>;speed=<Approach.Speed>}` | Solo con `Arena` |
| 3 | `fleeConditional{distance=<Retreat.Distance>;speed=<Retreat.Speed>;mc=[ - predictormove{m=RETREAT} true ];targetconditions=[ - isTarget true ]}` | `RETREAT` |
| 4 | `fleeConditional{distance=<Band.Inner>;speed=<Approach.Speed>;mc=[ - predictormove{m=KEEP_DISTANCE} true ];targetconditions=[ - isTarget true ]}` | `KEEP_DISTANCE` (interior) |
| 5 | `moveWithinConditional{range=<Band.Outer>;speed=<Approach.Speed>;mc=[ - predictormove{m=KEEP_DISTANCE} true ]}` | `KEEP_DISTANCE` (exterior) |
| 6 | `moveTowardsTargetConditional{speed=<Approach.Speed>;minrange=<MeleeReach>;maxrange=<FollowRange>;mc=[ - predictormove{m=APPROACH} true ]}` | `APPROACH` |
| 7 | `thepredict:flank` (meta de Paper) | `FLANK` |
| 8 | `thepredict:hold` (meta de Paper, solo `MOVE`) | `HOLD` (si hay algún movimiento) |
| base + 10 | Tu `BaseAI` (o `AIGoalSelectors`, o `0 meleeAttack` y `1 randomStroll`), sin `clear` | Nivel 4 |
| siguiente | `lookAtTarget` si la IA base no la tiene | Siempre |
| última | `thepredict:owner` (marcador de propiedad) | — |

Las dos metas de huida llevan `targetconditions=[ - isTarget true ]`: el mob solo huye de su objetivo, el de su IA, y no de la entidad viva más cercana, aunque sea un aliado **(pendiente de servidor: dos mobs juntos y el jugador lejos)**. Con `Timing.TickLayer: full`, la meta de Paper `thepredict:micro` ocupa la prioridad 3 y las metas de 3 a 8 de la tabla suben una; la IA base no se mueve.

| Movimiento | Qué hace | Estado |
|---|---|---|
| `APPROACH` | Se acerca con el navegador nativo y se detiene hacia `MeleeReach`. | Pendiente de servidor (DT-07) |
| `KEEP_DISTANCE` | Se separa si el objetivo está a menos de `Band.Inner` y se acerca si está a más de `Band.Outer`; entre ambas no se mueve. | Pendiente de servidor (DT-07) |
| `RETREAT` | Se aleja por terreno transitable hasta `Retreat.Distance`; con `Arena`, vuelve a la arena. | Pendiente de servidor (DT-07) |
| `HOLD` | No se desplaza, sigue mirando al objetivo y flotando. | Pendiente de servidor (DT-08) |
| `FLANK` | Rodea a un objetivo que levanta un escudo hasta salir de su ángulo de bloqueo y ataca desde ahí. Solo es elegible contra un escudo levantado. | Pendiente de servidor (DT-08) |
| `NONE` | No toca el movimiento: la táctica solo lanza su skill. Único movimiento de las especies con `Brain`. | — |

**IA base (`BaseAI`).** Es lo que hace el mob en nivel 4. Orden: `BaseAI` del perfil; si no hay, `AIGoalSelectors` del mob; si tampoco, `0 meleeAttack` y `1 randomStroll`. Una línea `clear` se descarta. Si una línea no existe en MythicMobs, la instalación se rechaza y el mob conserva su IA de MythicMobs (`install incomplete: 1 MythicMobs goals missing`) **(pendiente de servidor, paso 9.11)**.

Si un movimiento no progresa en dos decisiones seguidas (sin meta de movimiento en curso, o sin acercarse o alejarse como debe), la intención se abandona con la causa `NO_PROGRESS` **(pendiente de servidor, paso 12.6 del guion de M01)**.

## 9. Skills: la línea `Intention`, el `Cooldown` nativo y `CD`

La skill de una `Choice` dice para qué sirve con la línea `Intention`, al nivel de `Cooldown`. Sin esa línea, y sin una anulación interna del administrador, la choice queda **sin tipo**: solo sale como choice de su propia táctica (`tactic_only`). `PredictValues` ya no da un propósito. El ejemplo los conserva y vincula sin avisos porque cada skill tiene una línea válida.

### Intención en la skill

Vocabulario Intention v2: 43 primitivas. Se escriben en mayúsculas; al leer no se distinguen mayúsculas, y un guion o un espacio interior valen como `_`. Lista YAML o texto separado por comas, igual que `PredictValues`. Como mucho 16 primitivas en bruto y 400 caracteres. Una línea válida tiene como mucho 15: 3 papeles, 1 entrega, 1 nivel de daño, 4 `WHEN_`, 4 `RISK_`, `IGNORES_SHIELD` y una `ANSWER_*` (`ANSWER_BLOCK`, `ANSWER_DODGE`, `ANSWER_BACK_OFF`, `ANSWER_BREAK_SIGHT`, `ANSWER_INTERRUPT`), que fija la respuesta que pide el aviso de la skill (sección [Posturas y telegrafiado](#posturas-y-telegrafiado)). El primer papel es el principal. Una primitiva desconocida o repetida se ignora con aviso y no invalida el resto; una línea inválida (sin papel, sin entrega, demasiadas de una familia, o `IGNORES_SHIELD` junto a `RISK_BLOCKED_BY_SHIELD`) no se usa a medias: la choice queda sin tipo. MythicMobs no puede rechazar el archivo por esa línea: el aviso es de ThePredict. Que la consola de MythicMobs no se queje de la clave está **(pendiente de servidor, DT-43)**.

```text
ThePredictGuardianMelee:
  Intention: DAMAGE, MELEE, DAMAGE_MEDIUM, WHEN_TARGET_IN_MELEE
  Cooldown: 2
  Skills:
  - delay 5
  - damage{a=6} @target
```

La frase que llega a Jev es la de siempre, nunca el nombre de la primitiva. El orden al reescribir la línea es el canónico: papeles (el principal primero), entrega, daño, `WHEN_` y `RISK_` en el orden de sus enums, e `IGNORES_SHIELD`. Por eso el disparo del ejemplo se escribe `WHEN_TARGET_FLEEING` y después `WHEN_TARGET_AT_RANGE`.

**Papeles** (familia `ROLE`; de 1 a 3; el primero es el principal):

| Primitiva | M15 | Significa | Por qué |
|---|---|---|---|
| `DAMAGE` | `SkillRole.DAMAGE` | Su fin principal es quitar vida | Separa el golpe del resto; activa las comprobaciones de daño de `HitChecks` |
| `CONTROL` | `CONTROL` | Aturde, ralentiza, empuja o inmoviliza | Jev la prefiere para cortar la huida o separar al objetivo |
| `GAP_CLOSE` | `GAP_CLOSE` | Acorta la distancia de golpe | Hace útil una táctica de acercarse aunque el objetivo esté lejos (`closesDistance`); con `ANY_READY` sale en la dirección de acercarse (M19.5). Dos excepciones, salvo con `RETREAT`: si también hace `DAMAGE` y el objetivo está a su alcance en cuerpo a cuerpo, sale como cualquier golpe; y con la reacción `PUNISH_CONSUMING` sale con cualquier movimiento |
| `ESCAPE` | `ESCAPE` | Aleja al lanzador del peligro de golpe | Encaja al alejarse: con `ANY_READY` sale en la dirección de retirada (M19.5). Con la reacción `EVADE_CRITICAL` sale con cualquier movimiento salvo `APPROACH` |
| `DEFENSE` | `DEFENSE` | Reduce el daño que recibe el lanzador | Distingue protegerse de curarse; encaja con salud baja o frente a presión |
| `SUSTAIN` | `SUSTAIN` | Recupera vida del lanzador | Con `decision.pruning: safe`, la poda la quita cuando el lanzador está sano (`SUSTAIN_WHILE_HEALTHY`, M19.6). Con el default `off` no se poda |
| `BUFF` | `BUFF` | Mejora al lanzador o a sus aliados | Acción sin objetivo que no compite con el golpe |
| `DEBUFF` | `DEBUFF` | Empeora al objetivo | Distingue debilitar de dañar |
| `SUMMON` | `SUMMON` | Crea aliados | Cambia el número de rivales; Jev lo pesa distinto |
| `ZONE` | `ZONE` | Niega un área durante un tiempo | Útil para cortar el paso o el *kiting* |
| `INTERRUPT` | `INTERRUPT` | Corta lo que hace el objetivo (beber, tensar un arco) | Encaja con las señales `CONSUMING` y `DRAWING_BOW` de M07 |
| `FINISHER` | `FINISHER` | Remata a un objetivo debilitado | Encaja con `TARGET_LOW_HEALTH`; cuenta como dañina |
| `OPENER` | `OPENER` | Abre el combate con ventaja | Distingue la primera acción de la rutina |

**Entrega** (familia `DELIVERY`; exactamente una):

| Primitiva | M15 | Significa | Por qué |
|---|---|---|---|
| `MELEE` | `Delivery.MELEE` | Golpe a un objetivo al alcance de la mano | El alcance es el `MeleeReach` del perfil (el de hoy), salvo `targetwithin` (M19.3) |
| `PROJECTILE` | `PROJECTILE` | Proyectil que viaja y puede fallar | Se puede esquivar: se combina con `RISK_SLOW_PROJECTILE` |
| `HITSCAN` | `HITSCAN` | Efecto a distancia que impacta al instante | No se esquiva; su alcance es el de `targetwithin` |
| `AREA_AROUND_SELF` | `AREA_AROUND_SELF` | Área centrada en el lanzador | Su radio es el `r` del targeter de radio, como en M15.6 |
| `AREA_AT_TARGET` | `AREA_AT_TARGET` | Área centrada en el objetivo | Da igual la precisión y castiga a los agrupados |
| `CONE` | `CONE` | Barrido frente al lanzador | Su longitud es el `range` de `@EntitiesInCone`, o `targetwithin` |
| `SELF` | `SELF` | Solo afecta al lanzador | Nunca necesita alcance ni objetivo |

**Daño** (familia `DAMAGE`; como mucho una):

| Primitiva | M15 | Significa | Por qué |
|---|---|---|---|
| `DAMAGE_LOW` | `DamageTier.LOW` | Hace poco daño | Jev compara opciones dañinas; el número exacto no le sirve (01 §1) |
| `DAMAGE_MEDIUM` | `MEDIUM` | Hace un daño medio | Igual |
| `DAMAGE_HIGH` | `HIGH` | Hace mucho daño | Igual; también ordena las skills listas de `ANY_READY` (M19.5) |
| *(ninguna)* | `UNRATED` si hay `DAMAGE` o `FINISHER` **o si la skill tiene una mecánica dañina** (`NativeSkillFacts.hasDamagingMechanic`, M19.3; por ejemplo, un `CONTROL` que además hace `damage`); si no, `NONE` | "Hace daño", sin nivel | La línea mínima (`DAMAGE, MELEE`) vale sin obligar a declarar el nivel. M15 lo exige en el perfil; aquí se relaja para que la línea sea mínima. Leer la mecánica evita declarar `NONE` para una skill que sí daña, lo que apagaría las comprobaciones de daño de `HitChecks` |

**Cuándo conviene** (familia `BEST_WHEN`; de 0 a 4):

| Primitiva | M15 | Significa | Por qué |
|---|---|---|---|
| `WHEN_TARGET_LOW_HEALTH` | `UseCondition.TARGET_LOW_HEALTH` | La salud del objetivo es crítica | Remates; desempate local +20 (M15.8) |
| `WHEN_TARGET_SHIELDING` | `TARGET_SHIELDING` | El objetivo bloquea o levanta el escudo | Skills que rompen o rodean el escudo |
| `WHEN_TARGET_FLEEING` | `TARGET_FLEEING` | El objetivo se aleja | Castigo a distancia o cierre de distancia |
| `WHEN_SELF_LOW_HEALTH` | `SELF_LOW_HEALTH` | La salud del lanzador es crítica | Curas y defensas |
| `WHEN_ENEMIES_GROUPED` | `ENEMIES_GROUPED` | El objetivo tiene a otro enemigo cerca | Áreas |
| `WHEN_TARGET_AT_RANGE` | `TARGET_AT_RANGE` | El objetivo está fuera del cuerpo a cuerpo | Ataques a distancia |
| `WHEN_TARGET_IN_MELEE` | `TARGET_IN_MELEE` | El objetivo está en cuerpo a cuerpo | Golpes y áreas cortas |

**Riesgos** (familia `RISK`; de 0 a 4):

| Primitiva | M15 | Significa | Por qué |
|---|---|---|---|
| `RISK_LEAVES_CASTER_EXPOSED` | `SkillRisk.LEAVES_CASTER_EXPOSED` | Deja expuesto al lanzador después | Poco oportuna con salud crítica (M16.2) |
| `RISK_ROOTS_CASTER` | `ROOTS_CASTER` | El lanzador no puede moverse mientras la usa | Igual; Jev lo pesa frente a la huida |
| `RISK_SELF_DAMAGE` | `SELF_DAMAGE` | Daña al lanzador | Jev lo pesa con la salud propia |
| `RISK_NEEDS_LINE_OF_SIGHT` | `NEEDS_LINE_OF_SIGHT` | Necesita línea de visión | Activa la comprobación de visión previa al cast; el binder la sugiere si hay `lineofsight` (M19.3) |
| `RISK_SLOW_PROJECTILE` | `SLOW_PROJECTILE` | Un objetivo en movimiento puede esquivarla | Poco oportuna si el objetivo huye |
| `RISK_BLOCKED_BY_SHIELD` | `BLOCKED_BY_SHIELD` | Un escudo levantado la bloquea | Hace explícito lo que el `damage` hace por defecto |
| `RISK_HITS_ALLIES` | `HITS_ALLIES` | Puede dañar a aliados | Encuentros de varios mobs (M25) |

**Escudo** (familia `SHIELD`):

| Primitiva | M15 | Significa | Por qué |
|---|---|---|---|
| `IGNORES_SHIELD` | `ignoresShield = true` | Atraviesa escudos | Evita el `SKIPPED_SHIELD` de M10; se deduce también de `ignoreshield=true` en el `damage` (M19.3) |

Nombres de enum que usa el vínculo, además de la primitiva: `TARGET_LOW_HEALTH`, `TARGET_SHIELDING`, `TARGET_FLEEING`, `SELF_LOW_HEALTH`, `ENEMIES_GROUPED`, `TARGET_AT_RANGE`, `TARGET_IN_MELEE`, `LEAVES_CASTER_EXPOSED`, `ROOTS_CASTER`, `SELF_DAMAGE`, `NEEDS_LINE_OF_SIGHT`, `SLOW_PROJECTILE`, `BLOCKED_BY_SHIELD`, `HITS_ALLIES`, `NONE`, `UNRATED`, `LOW`, `MEDIUM`, `HIGH`.

El alcance, el radio y la preparación **no se escriben en la línea**. Salen de lo que MythicMobs ya aplica: `MeleeReach` o `targetwithin` para el alcance, el `r` de `@PlayersInRadius` (o un targeter de radio) para el área alrededor, el `range` de `@EntitiesInCone` para el cono, y el `delay` de la skill para la preparación. Un `AREA_AROUND_SELF` sin targeter de radio, o un `CONE` sin `range` ni `targetwithin`, no se puede completar: la choice queda sin tipo y el aviso lo dice. Añadir `targetwithin{d=N}` a las `Conditions` **cambia lo que hace MythicMobs**: rechaza la skill más allá de N bloques de `Mob.getTarget()` para cualquier trigger, no solo para ThePredict. Solo se añade si esa es la intención del autor **(pendiente de servidor)**.

**Skills que llaman a otras.** ThePredict no sigue lo que ejecuta una skill dentro de otra (`skill{s=…}`, `randomskill`), un proyectil (`projectile`) ni un aura (`aura`). Una skill con alguna de esas mecánicas cuenta como una skill que puede dañar, con un empuje supuesto (`KNOCKBACK`), y el vinculador avisa una vez por skill: `skill '<S>': it runs nested skills, projectiles or auras that ThePredict does not follow; it may deal damage and a knockback is assumed`. Si la skill no empuja, `Displacement: NONE` en su choice anula esa lectura (sección [Terreno y zona](#terreno-y-zona)).

**Versionado.** La versión sube cuando se añade una primitiva. Una primitiva nunca cambia de significado ni de familia. Una desconocida que empieza por `WHEN_`, `RISK_` o `DAMAGE_` avisa que hay que actualizar ThePredict o quitarla (`FUTURE_TOKEN`); cualquier otra desconocida es `UNKNOWN_TOKEN`. No se escribe versión en la línea. `/thepredict status` y esta guía dicen «vocabulario Intention v2». Una desconocida que empieza por `ANSWER_` también es `FUTURE_TOKEN`.

### `PredictValues` (historia)

Estos valores ya no se leen para decidir el propósito. Si un archivo viejo los tiene y la skill tiene una `Intention` válida, se conservan y no generan un aviso nuevo. Si la choice queda sin tipo y la skill aún los tiene, el aviso, uno por vinculación, es `PredictValues of skill '<id>' are no longer read; write an Intention`. Sin ellos, el aviso es `skill '<id>' has no Intention; the choice is untyped and only casts with its own tactic`. Un valor desconocido en la lista no crea un propósito. Que el autor vea además el texto `Unknown PredictValues entry: TELEPORTS` no está medido: el aviso escrito para una choice sin tipo es el de arriba.

| Valor | Significado que tenía |
|---|---|
| `MELEE_ATTACK` | Golpe cuerpo a cuerpo. |
| `RANGED_ATTACK` | Ataque a distancia. |
| `DEFENSE` | Acción defensiva. |
| `SINGLE_TARGET` / `AREA_TARGET` | Un objetivo o un área. |
| `LOW_DAMAGE` / `MEDIUM_DAMAGE` / `HIGH_DAMAGE` | Daño aproximado. |
| `APPLIES_POISON` / `APPLIES_BAD_OMEN` | Efectos que aplica. |
| `HEALS_CASTER` | Cura al lanzador. |
| `SUMMONS_ALLY` | Invoca aliados. |
| `IGNORES_SHIELD` | La etiqueta decía que la skill no se detiene ante un escudo. Hoy eso lo dice `IGNORES_SHIELD` en la línea `Intention`, o `ignoreshield=true` en el `damage`. |

**Cómo lanza ThePredict una skill.** Con su propio trigger de MythicMobs, `THEPREDICT` (no se escribe a mano); `@target` y `@trigger` son el objetivo del mob. Justo antes de lanzarla comprueba, en orden: que el objetivo sea el esperado (el líder de la tabla de amenaza y, si no, `Mob.getTarget()`; una skill con `targetwithin` nativo espera además a que `Mob.getTarget()` sea ese mismo objetivo), `Cooldown` nativo y cargas, alcance de la entrega cuando la línea se pudo completar (y la línea de visión si declara `RISK_NEEDS_LINE_OF_SIGHT` o la skill tiene `lineofsight`), invulnerabilidad del objetivo, escudo y las `Conditions` de la skill. Una condición `chance` no se comprueba antes: la tira MythicMobs una sola vez, al lanzar, y si falla el cast cuenta como `REJECTED_BY_MYTHICMOBS`. Una skill de daño contra un jugador que bloquea con escudo no se lanza (`SKIPPED_SHIELD`) salvo `IGNORES_SHIELD` en la línea o `ignoreshield=true` en el `damage`; si el objetivo acaba de recibir daño y vanilla ignoraría el golpe, espera (`DEFERRED_INVULNERABLE`), salvo que todas las mecánicas dañinas declaren `ignoreinvulnerability=true`. Una condición que aún no se cumple hace esperar a la skill sin consumirla (`NOT_ELIGIBLE`). **(Pendiente de servidor, DT-12, DT-26 y DT-47.)**

**Ritmo.** **La skill sale a su propio `Cooldown`; ajusta el `Cooldown` para controlar el spam.** Con `CastMode: any_ready` (el default), cada skill con línea válida sale en el primer tick en que está lista, usando su `Cooldown` y sus `Charges`, además de la choice de la táctica. Con `intent_only` solo sale la choice de la táctica. **Una skill sin `Cooldown` hace spam** con `any_ready`: sale cada vez que puede, y ese ritmo es cosa del autor. Las únicas guardas son un inicio de cast por tick y la preparación (`busyUntilTick`, el tick del cast más el `delay` leído). No hay un suelo de ticks añadido por ThePredict. Un golpe vanilla (`Kind: VANILLA_MELEE`) no tiene `Cooldown`: conserva el ritmo de ataque vanilla, un golpe cada 20 ticks, como `MeleeAttackGoal`. La señal `PREDICTOR_…` no se repite al relanzar la misma táctica. Las skills con `delay` o proyectiles ya iniciados no se cancelan al cambiar de táctica. Con `decision.castThread: same_tick` (el default) un `delay 10` espera 10 ticks de servidor; `mythic_default` conserva el hilo de la 0.0.1. La cadencia exacta en juego está **(pendiente de servidor, DT-28 y sesión 19)**.

**`Cooldown` nativo frente a `CD`.** Usa el `Cooldown` de la skill (segundos, MythicMobs) y `Charges` si las hay. `CD` en la choice es un cooldown extra por instancia, en ticks, solo por compatibilidad con el esquema 1. ThePredict clasifica ese cooldown en `NONE` (0 s), `SHORT` (hasta 3 s), `MEDIUM` (hasta 10 s), `LONG` (más) o `UNKNOWN` (sin valor fijo legible, por ejemplo un placeholder). Un golpe `VANILLA_MELEE` es `SHORT` (o `MEDIUM` con un `CD` de 60 ticks o más). `/thepredict inspect` muestra la clase de cada skill en la línea `Skills` **(pendiente de servidor, DT-34)**.

**Golpe vanilla.** Una choice `Kind: VANILLA_MELEE` da un golpe vanilla, nunca a más de `MeleeReach` entre cajas, y **conserva el ritmo de ataque vanilla: un golpe cada 20 ticks** (el intervalo de `MeleeAttackGoal`), aunque la invulnerabilidad del objetivo sea más corta. ThePredict nunca pega más rápido que vanilla **(pendiente de servidor, DT-26)**.

## 10. Vocabulario de ThePredict en MythicMobs

> **Estado:** implementado y probado en local; **ningún ejemplo de esta sección se ha ejecutado todavía en un servidor** (DT-02, DT-11, DT-13, DT-14). El paquete de pruebas lo verifica.

| Elemento | Argumentos | Qué hace |
|---|---|---|
| Condición `predictorcontrol` | ninguno | Verdadera mientras ThePredict controla el mob (nivel `JEV`, `ALTERNATIVE` o `LOCAL`, sin vencer). Falsa sin sesión, en `BASE`, al vencer y al apagar. |
| Condición `predictormove` | `m` o `movement`: `APPROACH`, `KEEP_DISTANCE`, `RETREAT`, `HOLD`, `FLANK`, `NONE` (sin distinguir mayúsculas) | Verdadera si controla y pide ese movimiento. Un valor desconocido la deja siempre falsa y avisa una vez por carga. |
| Condición `predictortactic` | `t` o `tactic`: el ID exacto de una táctica, en minúsculas | Verdadera si controla con esa táctica. Un valor inválido (o `none`) la deja siempre falsa y avisa; si solo difiere en mayúsculas, el aviso sugiere la forma correcta. |
| Mecánica `predictordecide` | ninguno | Pide a ThePredict que adelante la siguiente decisión del encuentro del mob (el coordinador vacía la cola cada tick; un mob sin encuentro no cambia nada). Nunca espera ni falla. Un mob ya marcado no se marca dos veces; con 1024 pendientes, los nuevos se descartan y se cuentan en `status`. |
| Condición `predictorstance` | `s` o `stance` | Verdadera si la postura publicada es una de la lista (sin distinguir mayúsculas, separadas por comas) o si la lista incluye `enraged` y el mob está enfurecido. Falsa sin sesión y con un nombre desconocido. |
| Placeholders | `<caster.predictor.tactic>`, `<caster.predictor.movement>`, `<caster.predictor.level>`, `<caster.predictor.confidence>`, `<caster.predictor.state>`, `<caster.predictor.pressure>`, `<caster.predictor.stance>`, `<caster.predictor.poise>`, `<caster.predictor.answer>`, `<caster.predictor.telegraph>` | Táctica, movimiento efectivo (minúsculas), nivel, confianza, estado, peldaño, postura (`neutral`…`staggered`), aguante (`0`–`100`), respuesta del aviso y ticks que faltan para el impacto. Sin sesión o sin dato: `none`. |
| Señal `PREDICTOR_<TACTIC>` | — | `PREDICTOR_` más el ID de la táctica en mayúsculas, por ejemplo `PREDICTOR_RETREAT_AND_DEFEND`. Se envía una vez cuando empieza una táctica nueva en `JEV`, `ALTERNATIVE` o `LOCAL`; nunca al renovarla ni al pasar a `BASE`. Como todas las señales de ThePredict, solo se envía si el mob la escucha: una línea `~onSignal:<NOMBRE>`, o un `~onSignal` sin nombre, que MythicMobs corre para cualquier señal sin manejador propio, en los `Skills` del mob o de su `Template`. Un manejador escrito dentro de otra skill no cuenta, porque MythicMobs tampoco lo registra. |

## Posturas y telegrafiado

El perfil puede declarar `Stances:`, `Telegraph:` y `Poise:`. Una táctica puede declarar `Stance:`. Una choice puede declarar `Telegraph:` con `Danger`, `Answer` y `RecoveryTicks`. `Stances.Describe: false` es el default: el texto que ve Jev no cambia. `Telegraph.Commitment` (50–100, default 100) y `Telegraph.EntropyCommitment` (0–50, default 25) son las dos claves de finta. Un valor fuera de rango es un error de carga con archivo y ruta. Los nombres `tell_` y `stance_` están reservados. ThePredict no añade partículas ni sonidos: solo envía `PREDICTOR_TELL_BLOCK`, `PREDICTOR_TELL_DODGE`, `PREDICTOR_TELL_BACK_OFF`, `PREDICTOR_TELL_BREAK_SIGHT`, `PREDICTOR_TELL_INTERRUPT`, `PREDICTOR_STANCE_<POSTURA>`, `PREDICTOR_STANCE_ENRAGED` y `PREDICTOR_STANCE_CALM` si el mob ya tiene el `~onSignal`. En `hybrid` escribe la variable `predictor_stance` en minúsculas. Nada de esta sección está verificado en servidor (sesión 23, pendiente).

**`Stances`** (sin la sección se heredan `stance.nativeMode`, `stance.minDwellTicks` y `stance.hysteresisCycles` de `config.yml`):

| Campo | Default | Regla |
|---|---|---|
| `Native` | `stance.nativeMode` (`hybrid`) | `hybrid`, `read`, `write` u `off`. `hybrid` escribe la variable `predictor_stance` y lee la `stance` del mob, sin llamar nunca a `setStance`. `read` solo lee. `write` llama a `setStance`, y la `stance` del autor gana un conflicto hasta el siguiente cambio de acción. `off` ni lee ni escribe. |
| `MinDwellTicks` | `stance.minDwellTicks` (`20`) | Ticks, de 4 a 200. Tiempo mínimo que se mantiene una postura base antes de cambiarla. |
| `HysteresisCycles` | `stance.hysteresisCycles` (`2`) | Ciclos reactivos seguidos, de 1 a 7 (2 ticks cada uno), en los que tiene que pedirse una postura base nueva antes de aplicarla. La reacción de supervivencia `EVADE_CRITICAL` se salta esta espera y la de `MinDwellTicks`. |
| `ByMovement` | `APPROACH` y `FLANK`: `PRESSING`; `HOLD`: `GUARDED`; `RETREAT`: `EVASIVE`; `KEEP_DISTANCE` y `NONE`: `NEUTRAL` | Mapa de movimiento a postura base (`NEUTRAL`, `PRESSING`, `GUARDED` o `EVASIVE`) para las tácticas sin `Stance:`. |
| `Casts` | vacío: ninguna postura filtra | Mapa de postura base a papeles (`DAMAGE`, `DEFENSE`…) que `CastMode: any_ready` puede lanzar en esa postura. La choice de la táctica nunca se filtra. Un papel desconocido es un error. |
| `EnragedContexts` | `[]` | Contextos del perfil que enfurecen al mob mientras están activos: `predictorstance{s=enraged}` es verdadera y el mob recibe `PREDICTOR_STANCE_ENRAGED` al entrar y `PREDICTOR_STANCE_CALM` al salir. No acorta avisos ni recuperaciones por debajo de su mínimo. Un contexto que no existe es un error. |
| `EnragedNative` | `[]` | Valores exactos de la `stance` nativa del mob que también lo enfurecen. Solo con `Native: hybrid` o `write`; con `read` u `off` la carga avisa: `EnragedNative has no mirror when Native is read`. |
| `Describe` | `false` | Sin efecto en esta versión: el texto que ve Jev no cambia. Con `true` la carga avisa `has no effect in this build`. |

**`Telegraph`** del perfil (sin la sección se heredan `telegraph.*` de `config.yml`):

| Campo | Default | Regla |
|---|---|---|
| `AgainstPlayers` | `telegraph.againstPlayers` (`enforce`) | `enforce` u `off`. Con `enforce`, cada golpe `MEDIUM`, `HEAVY` o `SEVERE` contra un jugador se avisa durante su mínimo (`telegraph.minTicks` de su clase, más `telegraph.answerTicks` de su respuesta y `telegraph.deliveryMarginTicks`). En Entropía el mínimo no se aplica. |
| `AgainstEntities` | `telegraph.againstEntities` (`off`) | `off` o `enforce`. Lo mismo contra entidades que no son jugadores. |
| `Commitment` | `100` | 50 a 100. Probabilidad, en tanto por ciento, de que un aviso sea real; con 100 no hay fintas. |
| `EntropyCommitment` | `25` | 0 a 50. La misma probabilidad mientras Entropía está activa. |
| `ShortWindUp` | `lead` | `lead` o `immediate`. Qué pasa cuando la preparación de la skill es más corta que su mínimo contra un jugador: `lead` avisa primero y lanza al cumplirlo; `immediate` lanza al momento con la preparación del autor, y el vinculador avisa (`immediate lets a short wind-up skip the minimum; status counts those hits`). |
| `DefaultRecoveryTicks` | `20` | Ticks, de 0 a 100. Recuperación tras el golpe de una skill con `RISK_LEAVES_CASTER_EXPOSED` o `RISK_ROOTS_CASTER` cuando ni la choice ni la skill dan otra. |

**`Telegraph`** de una choice: `Danger` (`NONE`, `LIGHT`, `MEDIUM`, `HEAVY` o `SEVERE`) y `Answer` (`BLOCK`, `DODGE`, `BACK_OFF`, `BREAK_SIGHT` o `INTERRUPT`) ganan a lo que ThePredict deduce de la skill. `RecoveryTicks` (de 0 a 100) gana a la recuperación leída de los `delay` finales de la skill. La respuesta sale, por este orden, de `Answer` de la choice, de la `ANSWER_*` de la línea `Intention` de la skill y, si no hay ninguna, de la entrega. Un `Purpose` del perfil anula la línea entera, también su `ANSWER_*`.

**`Poise`** (aguante; solo por perfil, sin clave global; sin la sección no hay aguante ni interrupción):

| Campo | Default | Regla |
|---|---|---|
| `Max` | `0.15` | De 0,02 a 1. Fracción de la vida máxima que hay que quitar para romper el aguante. |
| `RegenDelayTicks` | `60` | Ticks, de 0 a 400. Sin daño durante este plazo, el aguante empieza a recuperarse. |
| `RegenPerSecond` | `0.05` | De 0 a 1. Fracción que se recupera cada segundo después del plazo. |
| `WindUpMultiplier` | `1.5` | De 1 a 3. El daño recibido durante un aviso cuenta más. |
| `BlockedHitPoise` | `0.05` | De 0 a 0,5. Lo que suma un golpe `HEAVY` o `SEVERE` del propio mob que el objetivo bloquea con el escudo. |
| `StaggerTicks` | `30` | Ticks, de 5 a 100. Duración del aturdimiento (`staggered`): el mob no lanza skills ni reacciona, y ThePredict no lo mueve. Una rotura durante un aviso lo interrumpe. |
| `ImmunityTicks` | `100` | Ticks, de 0 a 1 200. Tras un aturdimiento, el aguante no se llena durante este plazo. |

- Los nombres de condiciones y mecánicas funcionan con cualquier capitalización (MythicMobs los compara en mayúsculas) **(pendiente de servidor, DT-02)**.
- **La señal se envía en mayúsculas.** Escribe `~onSignal:PREDICTOR_RETREAT_AND_DEFEND` exactamente así: que MythicMobs compare las señales sin distinguir mayúsculas no está confirmado **(pendiente de servidor, DT-11)**.
- **Un mob atascado repite su señal.** Si el modo local no progresa (`NO_PROGRESS`), el mob baja a nivel 4 y vuelve a la táctica en la siguiente decisión, y cada vuelta cuenta como táctica nueva: con un mob local atascado, un `~onSignal` puede dispararse del orden de 30 veces por minuto (medida local). No pongas en un `~onSignal` efectos que no deban repetirse **(pendiente de servidor, hallazgo 5 de M13)**.
- Las condiciones de control (`predictorcontrol`, `predictormove`, `predictortactic`, `predictorrole`, `predictorplan`, `predictortoken`, `predictorzone` y `predictorpattern`) son falsas sin intención vigente: si ThePredict se detiene, pierde el servicio o la intención vence, el mob vuelve a sus metas base en el siguiente ciclo. `predictorstance` no mira el control: es verdadera mientras el mob tiene sesión y su postura publicada coincide, también en `BASE`.

Ejemplo (mob y skills; pendiente de servidor):

```yaml
VocabularyGuardian:
  Type: ZOMBIE
  Display: 'Vocabulary Guardian'
  Health: 100
  Predictor: true
  Options:
    FollowRange: 32
    Despawn: false
  Skills:
  - skill{s=GuardianSlam} @target ~onTimer:60
  - skill{s=GuardianDefendAura} @self ~onTimer:20
  - predictordecide @self ~onDamaged
  - sound{s=entity.warden.roar} @self ~onSignal:PREDICTOR_RETREAT_AND_DEFEND
  - message{m="<caster.predictor.tactic> (<caster.predictor.confidence>)"} @PlayersInRadius{r=16} ~onTimer:40
```

```yaml
GuardianSlam:
  Conditions:
  - predictorcontrol false
  Skills:
  - damage{a=6} @target
GuardianDefendAura:
  Conditions:
  - predictortactic{t=retreat_and_defend} true
  Skills:
  - effect:particles{p=crit;a=10} @self
GuardianShield:
  Intention: SUSTAIN, SELF, WHEN_SELF_LOW_HEALTH
  PredictValues: [DEFENSE, HEALS_CASTER]
  Cooldown: 5
  Skills:
  - heal{a=6} @self
```

```yaml
schemaVersion: 2
vocabulary_guardian:
  MythicID: VocabularyGuardian
  Objective: Survive while defeating the current target
  Choices:
    shield: { MythicID: GuardianShield, RequiresTarget: false }
  Tactics:
    approach: { Movement: APPROACH, Priority: 60 }
    retreat_and_defend: { Movement: RETREAT, Choice: shield, Priority: 90 }
    hold: { Movement: HOLD, Priority: 10 }
  Contexts:
    low_health_phase: { Priority: 90, Tactics: [retreat_and_defend] }
```

`GuardianSlam` solo golpea cuando ThePredict **no** controla (así no suma su daño al de la táctica); `GuardianDefendAura` solo se ve durante `retreat_and_defend`; `predictordecide` adelanta la decisión cuando el mob recibe daño; el rugido suena al empezar `retreat_and_defend`.

## 11. Amenaza enriquecida

La tabla de amenaza de MythicMobs sigue eligiendo el objetivo. ThePredict le suma (o resta) amenaza por hechos que MythicMobs no mide, con los pesos del perfil. Funciona con Jev y sin Jev.

| Campo de `Threat` | Default | Unidad y efecto |
|---|---|---|
| `AssistsOthers` | `1.5` | Cuando un jugador cura o potencia a otros participantes con una poción arrojadiza o persistente: peso × número de otros participantes alcanzados, sumado a ese jugador en la tabla de **cada miembro activo** del encuentro. **(Pendiente de servidor, DT-27.)** |
| `RangedHarass` | `1.0` | Multiplica el daño final de cada proyectil de un jugador que **no** es el objetivo del mob, en la tabla de ese mob. **(Pendiente de servidor, DT-27.)** |
| `BlockedHits` | `0.25` | Puntos por cada golpe del mob que el jugador bloquea con escudo, en la tabla de ese mob. **(Pendiente de servidor, DT-27.)** |
| `UnreachableDecayPerSecond` | `2.0` | Puntos que pierde por segundo el objetivo al que el mob no puede llegar a pie: tras dos decisiones en que el mob sigue una ruta que no llega al objetivo, se resta en cada decisión (con el latido de 20 ticks, 2 puntos). Si el mob se queda quieto sin ruta, no se resta nada. Este descuento no tiene tope por ventana. **(Pendiente de servidor, DT-27.)** |
| `MaxPerWindow` | `20.0` | Tope de puntos añadidos por tabla de mob, jugador y hecho en cada ventana de 100 ticks (5 s). Debe ser `> 0`. **(Pendiente de servidor, DT-27.)** |

Unidades y límites:

- La tabla nativa suma un punto por punto de daño con `ThreatTable.UseDamageTaken: true`, así que los pesos se expresan en **puntos de daño final** **(pendiente de servidor, DT-27)**. El acoso a distancia usa el daño final de Bukkit, posterior a armadura, resistencia y absorción: es menor o igual que el que suma `UseDamageTaken` por el mismo golpe.
- MythicMobs cambia de objetivo cuando otra entidad supera la amenaza del objetivo actual en un 10 % (evidencia estática de MythicMobs 5.13.1, DT-27); ThePredict no fuerza ese cambio.
- Con `ThreatTable.DecayUnreachable: true` en el mob, MythicMobs ya reduce la amenaza del inalcanzable: ThePredict ignora `UnreachableDecayPerSecond` para no restar dos veces, y el catálogo avisa: `Threat.UnreachableDecayPerSecond: is ignored because mob '<Mob>' enables ThreatTable.DecayUnreachable`.
- **Un mob sin `Modules.ThreatTable` no usa la amenaza enriquecida**: `inspect` dice `no threat table (Modules.ThreatTable off): enriched threat inactive for this mob`.
- Todos los pesos a `0` desactivan la amenaza enriquecida de ese perfil.
- Con `decision.logDecisions: true` la consola muestra líneas `Threat ±<puntos> <HECHO> for player_<n> sent to the table of mob <uuid>`, con signo y dos decimales y sin nombres de jugadores; por ejemplo `Threat +1.50 ALLY_ASSISTED for player_2 sent to the table of mob <uuid>` o `Threat -2.00 UNREACHABLE for player_1 sent to the table of mob <uuid>` **(pendiente de servidor, DT-27)**.

## 12. Percepción y juego limpio

ThePredict decide solo con lo que el mob podría percibir, para que el comportamiento sea creíble y justo. Todo lo de esta sección está probado en local y **pendiente de servidor** (DT-22 a DT-25 y DT-26):

- Solo los participantes que admite la política de participantes dentro del `FollowRange` del mob (con el tope `decision.searchRadiusBlocks`): por defecto, jugadores vivos en `SURVIVAL` o `ADVENTURE`; un jugador en `CREATIVE` o `SPECTATOR` no existe para ThePredict **(pendiente de servidor, paso 11.2)**. Otras entidades vivas solo si el perfil o el administrador las admiten (sección [4.9](#49-participantes-y-objetivos-participants), pendiente de servidor, sesión 17).
- Los detalles visibles de un jugador (escudo, arco tensado, ítem en mano, efectos) solo con línea de visión y si no es invisible **(pendiente de servidor, DT-22 y paso 11.3)**.
- Jev recibe bandas y señales (`MELEE`, `RANGED`, `CRITICAL`, `RAISING_SHIELD`…), nunca coordenadas, nombres ni UUID: los jugadores son `player_1`, `player_2`… y las demás entidades `entity_1`, `entity_2`… (con su especie) dentro de cada encuentro.
- Ninguna decisión de ThePredict dobla el daño: una skill por época de la choice (a su `Cooldown`), un golpe vanilla por época, sin golpes durante la invulnerabilidad del objetivo y sin atacar escudos que bloquean (salvo `IGNORES_SHIELD`). La amenaza añadida tiene tope por ventana **(pendiente de servidor, DT-26, DT-27 y DT-28)**.
- El historial del encuentro se descarta al cerrarse.

## 13. Vocabulario para MythicScript 2077

La extensión MythicScript 2077 de VS Code marca como desconocidos los nombres que no están en su vocabulario. ThePredict no la modifica; estos son los pasos para añadir sus nombres a mano. Hay dos vías.

**1. Solo colores.** En `~/.vscode/extensions/local.mythicscript-2077-1.0.0/vocab/`, añade a cada lista plana:

| Archivo | Entradas |
|---|---|
| `conditions.json` | `"predictorcontrol"`, `"predictormove"`, `"predictortactic"` |
| `mechanics.json` | `"predictordecide"` |
| `keys-leaf.json` | `"Predictor"`, `"PredictorTraining"`, `"PredictValues"`, `"Intention"` y las 38 primitivas: `"DAMAGE"`, `"CONTROL"`, `"GAP_CLOSE"`, `"ESCAPE"`, `"DEFENSE"`, `"SUSTAIN"`, `"BUFF"`, `"DEBUFF"`, `"SUMMON"`, `"ZONE"`, `"INTERRUPT"`, `"FINISHER"`, `"OPENER"`, `"MELEE"`, `"PROJECTILE"`, `"HITSCAN"`, `"AREA_AROUND_SELF"`, `"AREA_AT_TARGET"`, `"CONE"`, `"SELF"`, `"DAMAGE_LOW"`, `"DAMAGE_MEDIUM"`, `"DAMAGE_HIGH"`, `"WHEN_TARGET_LOW_HEALTH"`, `"WHEN_TARGET_SHIELDING"`, `"WHEN_TARGET_FLEEING"`, `"WHEN_SELF_LOW_HEALTH"`, `"WHEN_ENEMIES_GROUPED"`, `"WHEN_TARGET_AT_RANGE"`, `"WHEN_TARGET_IN_MELEE"`, `"RISK_LEAVES_CASTER_EXPOSED"`, `"RISK_ROOTS_CASTER"`, `"RISK_SELF_DAMAGE"`, `"RISK_NEEDS_LINE_OF_SIGHT"`, `"RISK_SLOW_PROJECTILE"`, `"RISK_BLOCKED_BY_SHIELD"`, `"RISK_HITS_ALLIES"`, `"IGNORES_SHIELD"` |

Si escribes `PredictValues` o `Intention` como bloque de lista, añade también esas claves a `keys-block.json`. La extensión no se modifica: estos nombres se añaden a mano.

**2. Colores y autocompletado.** Declara un archivo por tipo en `MythicScribe.customDatasets` de tu `settings.json`, con `source: "File"`, por ejemplo `{ "elementType": "Condition", "source": "File", "path": "/ruta/thepredict-conditions.json" }` y otro con `"elementType": "Mechanic"`. Contenido del archivo de condiciones (formato de los datasets de MythicScribe 1.7.8):

```json
[
  { "name": ["predictorcontrol"], "description": "True while ThePredict controls the mob", "attributes": [] },
  { "name": ["predictormove"], "description": "True while ThePredict asks for this movement",
    "attributes": [ { "name": ["movement", "m"], "type": "String",
      "description": "APPROACH, KEEP_DISTANCE, RETREAT, HOLD, FLANK or NONE", "default_value": "" } ] },
  { "name": ["predictortactic"], "description": "True while this tactic of the profile is current",
    "attributes": [ { "name": ["tactic", "t"], "type": "String",
      "description": "snake_case tactic ID of the profile", "default_value": "" } ] }
]
```

Y el de mecánicas:

```json
[ { "name": ["predictordecide"], "description": "Asks ThePredict to decide again soon", "attributes": [] } ]
```

Los placeholders `<caster.predictor.*>` no necesitan entrada: la gramática los reconoce por el punto. Después de cualquiera de las dos vías: `python3 tools/regen.py` en la carpeta de la extensión y recarga la ventana de VS Code.

## 14. Límites conocidos

- **Todo lo de juego está pendiente de servidor** en la 0.0.1 (ver el estado al principio).
- **Voladores y acuáticos:** la 0.0.1 solo apunta a mobs terrestres. Los que no son `Creature` (slime, magma cube, ghast, phantom, bat) no reciben metas; los voladores y acuáticos que sí lo son reciben metas, pero su movimiento no se ha probado y no se recomienda.
- **Especies con `Brain`:** solo tácticas `NONE` (sección [2](#2-activar-un-mob)).
- **Skills ya iniciadas:** los `delay`, proyectiles y efectos en curso no se cancelan al cambiar de táctica.
- **Intención:** el autor escribe la línea `Intention`. ThePredict completa el alcance, el radio y la preparación con lo que MythicMobs ya aplica (`targetwithin`, el `r` del targeter, el `delay` y si el `damage` ignora el escudo). No adivina el papel desde `PredictValues`. El ángulo de un cono, la gravedad o velocidad de un proyectil y la cancelación de una skill en curso no se modelan.
- **Golpes de área con `@PlayersInRadius`:** solo alcanzan jugadores; el `slam` del ejemplo nunca daña a otros mobs. Contra entidades admitidas (sección [4.9](#49-participantes-y-objetivos-participants)) usa `@target` o un targeter de entidades.
- **Participantes que no son jugadores (M17):** Jev recibe de ellos menos hechos que de un jugador (matriz de 4.9); su tendencia sale de la velocidad (DT-36), su alcance del perfil o del valor vanilla (DT-37) y su escudo del objeto activo (DT-38), todo **pendiente de servidor** (sesión 17). Las entidades no vivas (soportes de armadura, marcadores, vehículos) nunca participan.
- **Condiciones de contexto:** solo condiciones de entidad; las de metadatos de skill quedan falsas (DT-24).
- **Cambio de objetivo:** cuando MythicMobs cambia el objetivo, la decisión en curso se descarta y el mob pasa a nivel 4 hasta la siguiente captura de su encuentro (de 2 a 4 ticks de ordinario, como mucho unos 22).
- **Señales repetidas** con un mob local atascado (sección [10](#10-vocabulario-de-thepredict-en-mythicmobs)).
- **Grupos que se juntan:** dos grupos de mobs que empiezan a pelear lejos y luego se juntan siguen siendo dos encuentros y dos solicitudes.
- **Aprendizaje (M28):** con los defaults el mob registra ventanas y no cambia la decisión ni lo que ve el jugador. Explorar contra jugadores exige `PredictorTraining: true` junto a `Predictor: true`. No hay perfiles `TRAINED` ni `UNPREDICTABLE`.
- **Idioma:** Jev razona en inglés y la estimación de tamaño está calibrada para inglés (sección [7](#7-objective-y-description-en-inglés)).

## Terreno y zona

`Zone` en el perfil hereda el modo global. Las claves son `Mode`, `Radius`, `Facts` y `HazardPush` (`off` o `exploit`). `exploit` solo empuja entidades. Un perfil por encima de `zone.mode` avisa con archivo y ruta y usa el mínimo. `Displacement` en una choice anula la lectura del desplazamiento.

La condición `predictorzone` (alias `predictorzonefact`, clave `fact` o `f`) nombra un hecho expuesto. No acepta `target_near_drop`. Un nombre que no es un hecho expuesto deja la condición siempre falsa y la carga lo avisa: `predictorzone: fact is not an exposed zone fact; the condition is always false`. Los placeholders son `<caster.predictor.zone>` y `<caster.predictor.pushdanger>`. Solo hay hechos de zona con el modo efectivo `full` (`zone.mode` y `Zone.Mode`) y `Zone.Facts: true`: con el default `observe`, `predictorzone` siempre es falsa y `<caster.predictor.zone>` da `none`. `<caster.predictor.pushdanger>` se publica con cualquier modo menos `off`.

## Grupos

`Group` en el perfil hereda el modo global. Las claves son `Mode`, `Squad`, `LeaderPriority`, `Weight`, `Roles`, `OnLeaderDeath`, `AgainstEntities`, `Questions` y `PlanQuestion`. El orden es `off`, `fair`, `coordinate` y `full`. `Group.Mode` solo puede bajar `group.mode`. Un valor por encima avisa con archivo y ruta `Group.Mode` y usa el mínimo. El default entregado sigue en `fair`. `TargetsAllies` en una choice anula la lectura de aliados.

`Roles` limita los papeles que el grupo puede dar a este mob; vacío, todos los que el perfil puede cubrir. `reserve` siempre es posible. `PlanQuestion` no tiene efecto en esta versión: la pregunta de plan va en cada solicitud que la lleva, y `every_heartbeat` avisa al cargar (`has no effect in this build`).

En `full` y con líder, `Questions` del perfil del líder decide qué va en la solicitud: `plan_and_leader` (la pregunta de plan y la del líder; el default con líder), `plan_only` (solo la de plan), `leader_only` (solo la del líder) o `members` (una pregunta por miembro, como en `fair` y `coordinate`). Un miembro sin pregunta propia sigue en el estado que ve Jev y juega en local según su papel. Sin líder no hay pregunta de plan y se pregunta a cada miembro. Las candidatas de plan son como mucho `group.maxPlanCandidates`, una por familia (atacar, proteger, retirarse y buscar), con el plan local primero. `search_last_known` solo se ofrece cuando ningún miembro ve a su objetivo y alguno lo vio hace como mucho `group.lkpForgetTicks`. ThePredict no lleva a nadie a la última posición conocida: tus skills ven el plan con `predictorplan{p=search_last_known}` o `PREDICTOR_PLAN_SEARCH_LAST_KNOWN`.

Las condiciones son `predictorrole` (clave `role` o `r`), `predictorplan` (clave `plan` o `p`) y `predictortoken` (clave `pool`, `melee` o `ranged`). Las tres son falsas en `BASE` y con la intención vencida, aunque el mob conserve su rol, su plan o su ficha, igual que `predictorcontrol`. Los placeholders son `<caster.predictor.role>`, `<caster.predictor.plan>`, `<caster.predictor.token>`, `<caster.predictor.slot>` y `<caster.predictor.squad>`. Vacío se lee `none`. Si el mob tiene un manejador, ThePredict puede emitir `PREDICTOR_ROLE_<ROL>` como mucho una vez cada 5 s y `PREDICTOR_PLAN_<PLAN>` cuando el plan cambia. La sintaxis en una skill del autor queda (pendiente de servidor).

## Patrones del enemigo

`Patterns` en el perfil hereda el modo global. Las claves son `Mode`, `AgainstEntities` y `Facts`. El orden es `off`, `observe`, `bias` y `full`. `Patterns.Mode` solo puede bajar `patterns.mode`. Un valor por encima avisa con archivo y ruta `Patterns.Mode` y el texto `is above Patterns.Mode and uses the minimum`, y usa el mínimo. `AgainstEntities` es `free` o `fair`. `Facts` es booleano y, con `full`, decide si Jev recibe frases.

`Decision` tiene las claves `Pruning` (`off` o `safe`), `MaxOptions` (2 a 8), `CandidateScoring` (`priority`, `utility` o `learned`) y `Diversity` (`off` o `on`); `inherit` (o `0` en `MaxOptions`) hereda. Se validan y se guardan para las superposiciones, pero esta versión decide con las claves globales (`decision.pruning`, `decision.maxOptionsPerQuestion` y `decision.candidateScoring`): un valor distinto de `inherit` no tiene efecto y la carga lo avisa (`has no effect in this build`).

`Distill` solo puede bajar `distill.mode`. La clave es `Mode`: `off`, `shadow`, `assist` o `autonomous`. Un valor por encima avisa con archivo y ruta `Distill.Mode` y el texto `is above distill.mode and uses the minimum`, y usa el mínimo. Con un mob, 1884 tokens y `STANDARD`, `assist` cabe en 5, 9 y 26 encuentros cuando la fracción cubierta es 0,17, 0,5 y 0,9 (`60·[(1−f)+0,1·f]`). `autonomous` cabe en 6, 10 y 50 (`60·(1−f)`). Un grupo con `plan_and_leader` de 3 o más es una cota inferior, no una promesa de victorias.

**Superposición de autoconfiguración.** El administrador puede aplicar a tu perfil una superposición (`/thepredict autoconfig apply`, guía del administrador, sección 4.12). ThePredict nunca escribe tu archivo: el perfil efectivo es el tuyo más la superposición, y cada superposición vale solo para un perfil. Sus claves ganan a las tuyas, salvo las que la propuesta fija con `pin=` (`authorPinned`). Un `Timing.TickLayer` de la superposición por encima de `decision.tickLayer.mode` se baja a ese modo, como el tuyo. Si cambias tu perfil después, la superposición caduca (`OVERLAY_STALE`, aviso en la consola) y vuelve a mandar tu perfil tal como está.

La condición `predictorpattern` (clave `pattern` o `p`) es verdadera si el objetivo actual del caster tiene ese patrón en `ACTIVE`. Un nombre que no es un patrón la deja siempre falsa y la carga lo avisa: `predictorpattern: pattern is not an enemy pattern; the condition is always false`. Los nombres son los de la tabla, en minúsculas. Los placeholders son `<caster.predictor.pattern>` (el de mayor confianza, o `none`) y `<caster.predictor.forecast>` (el pronóstico si la precisión es al menos 0,4; si no, `none`). Cada vez que un patrón pasa a `ACTIVE`, ThePredict envía `PREDICTOR_PATTERN_<PATRÓN>` una sola vez a cada mob del encuentro cuyo objetivo es ese enemigo, si el mob tiene el manejador. La señal dice qué se ha leído, no qué hará el mob. Condición, placeholders y señal solo actúan con el modo efectivo `bias` o `full`: con el default `observe` la condición es falsa, los placeholders dan `none` y no se envía la señal, porque un modo que no cambia decisiones tampoco mueve tus skills. ThePredict no escribe variables del autor.

La tendencia de un jugador sale de la lectura cinemática (desplazamiento observado), no de las teclas. La condición nativa `input` lee la entrada cruda del cliente y `HasItem` lee el inventario entero: son decisiones del autor que A14 no cubre. ThePredict no las lee.

Para una sola costumbre fija, sin ThePredict:

```yaml
without_predict:
  - "variableAdd{var=caster.tp_spam;amount=1} @self ~onDamaged ?varInRange{var=skill.hit-attack-cooldown;value=0to0.5}"
  - "shieldbreak{d=100} @trigger ~onAttack ?~blocking"
```

Con ThePredict, que decide cuándo la costumbre es real y cuándo cambió. El ejemplo solo guarda una variable del autor. No es una recomendación de efecto visible:

```yaml
with_predict:
  - "skill{s=Boss_ShieldBreaker} @target ~onTimer:10 ?predictorpattern{p=shield_turtle}"
  - "setvariable{var=caster.read_guard;value=1;type=INTEGER} @self ~onSignal:PREDICTOR_PATTERN_SHIELD_TURTLE"
```

`FIXED_CADENCE` no tiene frase para Jev. La tabla de patrones (θ entre paréntesis): `SPAMS_CLICKS` (0,60), `CRIT_JUMPER` (0,50), `WTAP_PRESSURE` (0,60), `PREDICTABLE_STRAFE_LEFT` (0,60), `PREDICTABLE_STRAFE_RIGHT` (0,60), `CHASES_RETREAT` (0,60), `SHIELD_TURTLE` (0,50), `SHIELD_LATE` (0,60), `OVEREXTENDS_AFTER_BLOCK` (0,60), `NO_SHIELD` (0,80), `FAILS_ANSWER_BLOCK` (0,60), `FAILS_ANSWER_DODGE` (0,60), `FAILS_ANSWER_BACK_OFF` (0,60), `FAILS_ANSWER_BREAK_SIGHT` (0,60), `FAILS_ANSWER_INTERRUPT` (0,60), `FAILS_DODGE_PROJECTILE` (0,60), `WEAK_TO_AREA` (0,60), `HEALS_AT_LOW_HP` (0,60), `PEARLS_WHEN_LOW` (0,50), `RETREATS_WHEN_LOW` (0,60), `RELIES_ON_TOTEM` (0,50), `KITES_AT_RANGE` (0,60), `SEEKS_HIGH_GROUND` (0,60), `MACE_DIVER` (0,50). `FIXED_CADENCE` es solo de entidades y no tiene θ.

Con olvido λ = 0,98 la detección es del 92,5 % a 80/20 (mediana 24 ensayos), del 41,8 % a 70/30 y los falsos positivos son el 0,1 %. Esas tres cifras son las del detector con olvido. El default de `patterns.mode` sigue en `observe`.

Con cadencia `REACTIVE` y los topes por defecto, `GroupCapacity` sigue usando la estimación de 3114 tokens para una pregunta `plan_and_leader` de cinco miembros: cabe en menos de 1 encuentro (≈ 0,96). Con `STANDARD`, una pregunta cada 60 segundos, caben 3. La columna local del codificador (sin `m13Measure` y sin la sesión de pago) mide 6 981 caracteres para ese caso, 4 654 tokens a 1,5 y 2 738 a 2,55, iguales en las dos cadencias: solo van la pregunta del líder y la de plan. Con esos 4 654 la fórmula da unos 0,64 y unos 2,1 encuentros; a 2,55, unos 1,1 y 3,7. La estimación de 3114 queda entre las dos medidas. No se cambiaron los topes de 600 solicitudes por minuto, 300 por mundo ni 600 000 tokens por minuto, ni el 3114.

Los ejemplos `jefe_invocador`, `patrulla_5` y `trio_hechiceros` están en el paquete de la sesión 25. No se han ejecutado.

Los malus de camino `AIPathfindingMalus` y `setpathfindingmalus` quedan **(pendiente de servidor)**. ThePredict no llama a `setPathfindingMalus` y no escribe variables de zona.

Contra un jugador, un empuje letal sin aviso no se lanza. `Zone.HazardPush` no abre esa puerta para jugadores. Entropía levanta ese límite y el hecho del borde del objetivo, y no levanta la consulta de destino ni las guardas del mob. No existe la clave `zone.verifyCoverWithRay`. Un borde es una caída mayor que `zone.maxSafeDropBlocks` (default 3): con el default, 4 o más bloques.
