# M01. Guion de verificación en servidor

> Estado: **Pendiente de servidor**. Nadie lo ha ejecutado todavía. · Módulo: [M01](M01-Verificacion-nativa.md) · Registro: [M01-Registro-de-decisiones.md](M01-Registro-de-decisiones.md) · Reutilizado por M05.7 (sección 9), M06 (sección 10), M07 (sección 11), M10 (sección 12), M11 (sección 13) y M13 ([Guion-servidor-0.0.1.md](Guion-servidor-0.0.1.md), que añade carga, latencia, A/B y banco de escenarios)
>
> **Revisión M12 (2026-09-29):** M12 retiró las herramientas provisionales de M01 (`debug install` con presets o `profile`, `debug autoinstall`, `debug unbind`, `debug capture`, `debug reapply`, `debug signal`, `debug cast`, `debug trace`, `debug signals` y la sonda `predictorprobe`). Los pasos se reescribieron con los comandos que quedan (`status`, `check`, `inspect` y `debug intent|release|goals`), con la instalación automática de las sesiones (M09), con YAML nativo de MythicMobs y con spark, incluido en Paper. Cada paso conserva su número y su entrada del registro; lo que ya no se puede observar con esos medios se indica en el paso y en su entrada.

> **Revisión M14 (2026-09-29):** los mobs del pack de la sección 1.4 llevan `Options.Despawn: false`, y el [paquete de pruebas](paquete-pruebas/README.md) reagrupa este guion por sesión de juego con todos los archivos listos para copiar (validados por `UserTestPackTest`). Ningún paso cambia de número ni de resultado esperado.

Este guion convierte las preguntas de M01 en pasos que otra persona puede repetir. Cada paso termina copiando la salida al registro de decisiones, en la entrada `DT-nn` indicada. Lo que no se observe queda como `Abierta` con la alternativa del plan §13; nunca se rellena con resultados supuestos.

## 0. Reglas

- El administrador acepta el EULA de Minecraft editando `eula.txt` en persona. Ningún script ni agente lo hace por él.
- La carpeta del servidor está fuera del repositorio y nada de ella entra en Git.
- Cada caso se ejecuta dos veces (M01.3). Se copia la salida literal de consola y del chat, no un resumen.
- Se usa siempre Java 25 explícito: el `java` por defecto de la máquina de desarrollo es 21.
- Si un paso falla por sintaxis del pack, se corrige el pack, se anota la corrección en la entrada y se repite.
- Comandos disponibles desde M12 (permiso `thepredict.admin`; `debug` además `thepredict.debug`, que la consola siempre tiene):
  - `/thepredict status`: estado global en secciones (`Environment`, `Service`, `Catalog`, `Sessions`, `Encounters`, `Budget`, `Consumption`, `Latency`, `Errors`, `Counters`).
  - `/thepredict inspect <nearest|uuid>`: una sesión (nivel, intención, motivo, percepción de la última captura, objetivo por alias, últimos eventos, metas, movimiento y tabla de amenaza). Las capturas ocurren en cada evaluación del encuentro (latido de 20 ticks o antes con contexto pendiente); tras cambiar algo en juego se espera 1–2 s antes de `inspect`.
  - `/thepredict debug intent <nearest|uuid> <táctica> [ticks]`: fija una intención `LOCAL` de una táctica del perfil v2 del mob (exige sesión). Sin `[ticks]` dura el `maximumIntentTicks` del perfil (40 por defecto); el máximo es 6000. Mientras dura, el coordinador no toca la sesión ni pregunta a Jev por ella; al vencer, el mob baja a nivel 4 y la cadena de fallback decide en ≤2 ticks. Una táctica que la última captura no hizo elegible se rechaza con el motivo.
  - `/thepredict debug release <nearest|uuid>`: baja el mob a nivel 4 enseguida; la cadena decide de nuevo en ≤2 ticks.
  - `/thepredict debug goals <nearest|uuid>`: cabecera `Goals of <uuid> generation <n>: <metas>` (o `(no session)`), una línea por meta `<clave> [TIPOS]` con `running` si está en curso, y la línea `Movement: movement goal running …, path …, path reaches target …, target … blocks`.
- `nearest` busca el mob vinculado con sesión más cercano en **16 bloques** y exige jugador. Un mob sin sesión (`Predictor: false`) se consulta por UUID. Para obtenerla sin ThePredict: `/tellraw @s {"selector":"@e[type=minecraft:zombie,sort=nearest,limit=1]"}` y Mayús+clic en el nombre que aparece inserta su UUID en el chat (inserción vanilla del nombre de entidad; si no la inserta, pasar el ratón muestra la UUID). La cabecera de `debug goals` también muestra la UUID completa de un mob vinculado.
- Desde M10, un mob vinculado con metas instaladas y un jugador en `SURVIVAL` o `ADVENTURE` percibido como objetivo pasa a control local (`state=controlled`, nivel `local`) aunque no haya clave de Jev. Los pasos de las secciones 2 a 9 que esperan `state=base` o "sin intención" se ejecutan con el jugador en `CREATIVE` (M07 no lo percibe). Una intención de `debug intent` manda sobre el modo local mientras dura, y `debug release` devuelve el mob al nivel 4, desde donde la cadena lo pasa a local en ≤2 ticks si percibe objetivo.
- Desde M09, ThePredict instala el conjunto de metas generado **sin comando**: la sesión lo pide al abrirse o al cambiar de generación y el coordinador lo instala con el límite `decision.maxInstallsPerTick`. `inspect` lo confirma con `Goals: installed for revision <n>`. Un mob se desvincula con `Predictor: false` y `/mm reload`, o sacando su perfil de `AI` y `/mm reload`; ThePredict entonces quita sus metas de Paper y llama a `reapplyAIGoalSelectors` (M05.4).

## 1. Servidor reproducible (M01.1)

### 1.1 Carpeta y binarios

```bash
REPO=~/Desktop/Akyrion/ThePredict
BASE=~/ThePredict-verificacion/26.2-129
JAVA=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/java
mkdir -p "$BASE/plugins"
cp "$REPO/VersionsOfJars/paper-26.2-129.jar" "$BASE/"
cp "$REPO/VersionsOfJars/MythicMobsPremium-5.13.1-SNAPSHOT.jar" "$BASE/plugins/"
(cd "$REPO" && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew clean build --offline)
cp "$REPO/build/libs/ThePredict-0.0.1.jar" "$BASE/plugins/"
```

Huellas SHA-256 de referencia de los binarios de `VersionsOfJars` (calculadas en la máquina de desarrollo el 2026-09-29; el servidor debe dar las mismas):

| Archivo | SHA-256 |
|---|---|
| `paper-26.2-129.jar` | `b1d8f6bfa1b6101fa8e947b53041cb3bdf5540e7b83b6547ca19ba7edefeb083` |
| `MythicMobsPremium-5.13.1-SNAPSHOT.jar` | `0e9ffc1362d7831e3cb4875084d017939ac96c2071721b4b22cc70370ac0b733` |
| `ThePredict-0.0.1.jar` | Cambia en cada build; se anota la del JAR copiado |

### 1.2 Primer arranque y EULA

```bash
cd "$BASE"
"$JAVA" -Xms2G -Xmx2G -jar paper-26.2-129.jar --nogui 2>&1 | tee first-start.log
```

Paperclip descarga el servidor de Mojang, verifica su hash de `META-INF/download-context` y se detiene pidiendo el EULA. `first-start.log` se guarda como evidencia (DT-00).

Antes del segundo arranque, **el administrador** edita `eula.txt` y, en `server.properties`, fija `level-type=minecraft\:flat` (el mundo aún no existe, así que se genera plano).

### 1.3 Permiso de depuración sin plugins de permisos

`thepredict.debug` tiene `default: false`: ni los operadores lo tienen. En el servidor de verificación se concede a los operadores con el `permissions.yml` de Bukkit, en la raíz del servidor:

```yaml
thepredict.verifier:
  description: Grants ThePredict debug tools to operators on the verification server
  default: op
  children:
    thepredict.debug: true
```

La consola siempre puede usar `debug` (DT-17). Para la prueba negativa del permiso se usa un jugador operador **antes** de crear `permissions.yml`, o con `thepredict.debug: false` en `children`.

### 1.4 Pack de prueba

`plugins/MythicMobs/Mobs/ThePredictVerification.yml`:

```yaml
TPV_Zombie:
  Type: ZOMBIE
  Display: 'TPV Zombie'
  Health: 200
  Damage: 2
  Predictor: true
  Options:
    FollowRange: 32
    PreventOtherDrops: true
    Despawn: false
  Skills:
  - skill{s=TPV_ShowIntent} @self ~onTimer:100
  - skill{s=TPV_DecideMechanic} @self ~onTimer:20
  - skill{s=TPV_CaseLower} @self ~onTimer:100
  - skill{s=TPV_CaseMixed} @self ~onTimer:100
  - skill{s=TPV_CaseUpper} @self ~onTimer:100
  - skill{s=TPV_SignalUpper} @self ~onSignal:PREDICTOR_HOLD
  - skill{s=TPV_SignalLower} @self ~onSignal:predictor_hold

TPV_ZombiePlain:
  Type: ZOMBIE
  Display: 'TPV Zombie Plain'
  Health: 200
  Damage: 2
  Predictor: false
  Options:
    Despawn: false
  Skills:
  - skill{s=TPV_ShowIntent} @self ~onTimer:100

TPV_ZombieCustomAI:
  Type: ZOMBIE
  Display: 'TPV Zombie Custom AI'
  Health: 200
  Damage: 2
  Predictor: true
  Options:
    Despawn: false
  AIGoalSelectors:
  - 0 clear
  - 1 float
  - 2 meleeAttack
  - 3 randomStroll

TPV_ZombieAsync:
  Type: ZOMBIE
  Display: 'TPV Zombie Async'
  Health: 200
  Damage: 2
  Predictor: true
  Options:
    AsyncPathfinding: true
    FollowRange: 32
    Despawn: false

TPV_NoPriority:
  Type: ZOMBIE
  Display: 'TPV No Priority'
  Health: 200
  Damage: 2
  Predictor: false
  Options:
    Despawn: false
  AIGoalSelectors:
  - clear
  - float
  - meleeAttack
  - randomStroll

TPV_FleeTarget:
  Type: ZOMBIE
  Display: 'TPV Flee Target'
  Health: 200
  Predictor: false
  Options:
    Despawn: false
  AIGoalSelectors:
  - 0 clear
  - 1 float
  - 2 fleeConditional{distance=10;speed=1.2;targetconditions=[ - isTarget true ]}
  - 3 randomStroll

TPV_FleePlayer:
  Type: ZOMBIE
  Display: 'TPV Flee Player'
  Health: 200
  Predictor: false
  Options:
    Despawn: false
  AIGoalSelectors:
  - 0 clear
  - 1 float
  - 2 fleeConditional{distance=10;speed=1.2;targetconditions=[ - isplayer true ]}
  - 3 randomStroll

TPV_Skeleton:
  Type: SKELETON
  Display: 'TPV Skeleton'
  Health: 200
  Predictor: true
  Options:
    Despawn: false

TPV_Piglin:
  Type: PIGLIN
  Display: 'TPV Piglin'
  Health: 200
  Predictor: true
  Options:
    Despawn: false
```

`TPV_ZombieAsync` añade el caso de `Options.AsyncPathfinding` de M01.7 sin editar el mob entre pruebas. Todos llevan `Options.Despawn: false` (revisión M14): sin él usan `DespawnMode.NORMAL`, que no se guarda y despawnea al descargar el chunk (ver la preparación de la sección 10), y el paso 4.3 no podría observar la recarga. Los tres archivos de esta sección, listos para copiar, están en [`paquete-pruebas/archivos/01-verificacion/`](paquete-pruebas/archivos/01-verificacion/) y una prueba automática los valida (`UserTestPackTest`). `TPV_ZombiePlain`, `TPV_NoPriority`, `TPV_FleeTarget` y `TPV_FleePlayer` tienen `Predictor: false`: ThePredict no los toca y sirven para observar MythicMobs solo.

`plugins/MythicMobs/Skills/ThePredictVerification.yml`:

```yaml
TPV_ShowIntent:
  Skills:
  - message{m="[TPV] tactic=<caster.predictor.tactic> movement=<caster.predictor.movement> level=<caster.predictor.level> confidence=<caster.predictor.confidence> state=<caster.predictor.state>"} @PlayersInRadius{r=24}

TPV_DecideMechanic:
  Skills:
  - predictordecide @self

TPV_CaseLower:
  Conditions:
  - predictormove{m=HOLD} true
  Skills:
  - message{m="[TPV] predictormove passed"} @PlayersInRadius{r=24}

TPV_CaseMixed:
  Conditions:
  - PredictorMove{m=HOLD} true
  Skills:
  - message{m="[TPV] PredictorMove passed"} @PlayersInRadius{r=24}

TPV_CaseUpper:
  Conditions:
  - PREDICTORMOVE{m=HOLD} true
  Skills:
  - message{m="[TPV] PREDICTORMOVE passed"} @PlayersInRadius{r=24}

TPV_SignalUpper:
  Skills:
  - message{m="[TPV] onSignal PREDICTOR_HOLD fired"} @PlayersInRadius{r=24}

TPV_SignalLower:
  Skills:
  - message{m="[TPV] onSignal predictor_hold fired"} @PlayersInRadius{r=24}

TPV_Strike:
  Intention: DAMAGE, MELEE, DAMAGE_LOW
  PredictValues: [MELEE_ATTACK, SINGLE_TARGET, LOW_DAMAGE]
  Cooldown: 5
  Conditions:
  - targetwithin{d=16} true
  - predictormove{m=APPROACH} true
  Skills:
  - message{m="[TPV] TPV_Strike executed"} @PlayersInRadius{r=24}
  - damage{a=1} @trigger
```

`plugins/ThePredict/AI/ThePredictVerification.yml` (esquema 2; una táctica por movimiento para poder fijar cada movimiento con `debug intent`, más dos con la skill `TPV_Strike`; sin clave de Jev no generan solicitudes ni costo):

```yaml
schemaVersion: 2
tpv_zombie:
  MythicID: TPV_Zombie
  Objective: Verification mob without remote decisions
  Choices:
    strike: { MythicID: TPV_Strike }
  Tactics:
    approach: { Movement: APPROACH }
    press_and_strike: { Movement: APPROACH, Choice: strike }
    keep_distance: { Movement: KEEP_DISTANCE }
    retreat: { Movement: RETREAT }
    hold: { Movement: HOLD }
    hold_and_strike: { Movement: HOLD, Choice: strike }
    flank_and_strike: { Movement: FLANK, Choice: strike }
  Movement:
    Arena: { Radius: 24 }
tpv_zombie_custom_ai:
  MythicID: TPV_ZombieCustomAI
  Objective: Verification mob without remote decisions
  Choices:
    strike: { MythicID: TPV_Strike }
  Tactics:
    approach: { Movement: APPROACH }
    press_and_strike: { Movement: APPROACH, Choice: strike }
    keep_distance: { Movement: KEEP_DISTANCE }
    retreat: { Movement: RETREAT }
    hold: { Movement: HOLD }
tpv_zombie_async:
  MythicID: TPV_ZombieAsync
  Objective: Verification mob without remote decisions
  Tactics:
    approach: { Movement: APPROACH }
    keep_distance: { Movement: KEEP_DISTANCE }
    retreat: { Movement: RETREAT }
    hold: { Movement: HOLD }
tpv_skeleton:
  MythicID: TPV_Skeleton
  Objective: Verification mob without remote decisions
  Tactics:
    approach: { Movement: APPROACH }
    keep_distance: { Movement: KEEP_DISTANCE }
    hold: { Movement: HOLD }
  BaseAI:
  - 1 bowAttack
  - 2 randomStroll
tpv_piglin:
  MythicID: TPV_Piglin
  Objective: Verification mob without remote decisions
  Choices:
    strike: { MythicID: TPV_Strike }
  Tactics:
    strike_in_place: { Movement: NONE, Choice: strike }
```

`NONE` es el único movimiento aceptado para una especie con `Brain` (M03.4). Los mobs con `Predictor: false` no necesitan perfil.

Elegibilidad de `debug intent` (M12.3): antes de la primera captura del mob (sin objetivo percibido) se acepta cualquier táctica del perfil; después se usa lo que vio la última captura, que puede estar desfasada: solo se captura mientras el mob está en un encuentro, así que al terminar un combate quedan los hechos de su última evaluación hasta la siguiente. La condición `Conditions` de la skill no entra en esta comprobación: la evalúa el cast (M10). Sin objetivo solo quedan `HOLD` y `NONE`; `FLANK` exige que la captura viera el escudo del objetivo levantado, y una táctica con skill exige la skill fuera de su `Cooldown`. Antes de un `debug intent` que dependa de eso (escudo, objetivo), la situación se mantiene 2 s para que la capture una evaluación.

### 1.5 Arena y versiones

- Arena en el mundo plano: un pasillo de 3 bloques de ancho y 20 de largo, una pared, una esquina, un desnivel de 2 bloques y una zona con agua. Se anotan sus coordenadas en `versions.md`.
- `versions.md` en `$BASE`: salida de `/version`, `/version MythicMobs`, `"$JAVA" -version` y `shasum -a 256 paper-26.2-129.jar plugins/*.jar`.
- Dos arranques consecutivos (`"$JAVA" -Xms2G -Xmx2G -jar paper-26.2-129.jar --nogui 2>&1 | tee start-N.log`) con `/thepredict status` en ambos. Se buscan errores de ThePredict o MythicMobs con `grep -nE 'ERROR|SEVERE|ThePredict' start-N.log`.

**Esperado en el log de arranque:** líneas `MythicMobs resolved condition predictormove on thread …` y `… mechanic predictordecide …`; la línea `Profile catalog LOADED (revision <n>, 5 profiles read, 5 active, 0 errors, <w> warnings)`; `Jev not configured: set TYPESAFE_API_KEY or jev.apiKey. MythicMobs works normally. …` (sin clave). **Registrar en:** DT-00, DT-02, DT-03.

## 2. Comando de depuración (M01.2, M12.3)

| Paso | Acción | Esperado | Registrar en |
|---|---|---|---|
| 2.1 | Operador **sin** `thepredict.debug`: `/thepredict debug intent nearest hold`; pulsar Tab tras `/thepredict ` | `You need the thepredict.debug permission to use debug tools`; el autocompletado ofrece `status`, `check` e `inspect`, sin `debug`; `TPV_ShowIntent` sigue mostrando `state=base` | DT-17 |
| 2.2 | Con permiso y en `CREATIVE`: `/mm m spawn TPV_Zombie 1`, esperar 2 s, `/thepredict debug goals nearest` | Cabecera `Goals of <uuid> generation 0: <n>` con la UUID completa, una línea por meta con clave y tipos (`running` en las que corren) y la línea `Movement: …` | DT-06 |
| 2.3 | `/thepredict debug intent nearest hold 400` | `Debug intent hold (HOLD, LOCAL) on <uuid corta> generation 0 for 400 ticks; it never sends a request to Jev`; en ≤5 s `TPV_ShowIntent` muestra `tactic=hold movement=hold level=local confidence=1.00 state=controlled`; `/thepredict inspect nearest` marca la intención con `[debug]` | DT-11 |
| 2.4 | `/thepredict debug release nearest` | `Released <uuid corta> to level 4 …`; `TPV_ShowIntent` vuelve a `tactic=none … level=base … state=base`; `inspect` muestra `Intent: none` | DT-11 |
| 2.5 | Consola: `thepredict debug goals <uuid>` y `thepredict debug intent <uuid> approach` | Funcionan igual que en jugador; `thepredict debug goals nearest` desde consola responde `'nearest' needs a player …` | DT-17 |
| 2.6 | Argumentos inválidos: `intent nearest dance_around`, `intent nearest hold 7000`, `goals 1-2-3-4-5`, `cast nearest TPV_Strike` | `Debug intent refused … does not declare the tactic dance_around …`, `Ticks must be a whole number between 1 and 6000`, `Mob must be 'nearest' or an entity UUID …` y `Unknown debug action 'cast' …`; nada cambia | — |

## 3. Registro del vocabulario (M01.3)

| Paso | Acción | Esperado | Registrar en |
|---|---|---|---|
| 3.1 | Primer arranque con el pack ya instalado | Sin `Unknown condition`/`Invalid condition` para `predictormove`, `PredictorMove`, `PREDICTORMOVE` ni `Unknown mechanic` para `predictordecide`; líneas `MythicMobs resolved …` | DT-02, DT-03 |
| 3.2 | `debug intent nearest hold 400` sobre `TPV_Zombie` | Aparecen los tres mensajes `[TPV] predictormove passed`, `PredictorMove passed` y `PREDICTORMOVE passed` | DT-02 |
| 3.3 | `/mm reload` con el mob vivo; la recarga sube la generación y termina la intención de depuración, así que se repite 3.2 | Mismo resultado; nuevas líneas `MythicMobs resolved …` tras la recarga | DT-02, DT-03 |
| 3.4 | Placeholders: observar `TPV_ShowIntent` en `TPV_ZombiePlain` (sin sesión) y en `TPV_Zombie` sin intención, con `debug intent nearest hold 400`, tras `release` y tras `/mm reload` | `none/none/base/0.00/none` sin sesión; `none/none/base/0.00/base` con sesión sin intención; `hold/hold/local/1.00/controlled` con la intención; línea `Registered <caster.predictor.*> placeholders after MythicPlaceholdersLoadEvent` tras recargar | DT-11, DT-13 |
| 3.5 | Buscar en el log el aviso de API obsoleta de MythicMobs por `Placeholder.meta` | Copiar el texto literal si aparece | DT-13 |
| 3.6 | `predictordecide` como mecánica: con `TPV_Zombie` vivo, esperar 10 s y ejecutar `/thepredict status` | Ningún error de MythicMobs mientras `TPV_DecideMechanic` se ejecuta cada segundo; la línea `MythicMobs resolved mechanic predictordecide on thread …` del arranque; en `Counters`, `Context: … predictordecide pending 0, dropped 0` (el coordinador vacía la cola cada tick). Es la evidencia de que la mecánica se registró como `INoTargetSkill` directo | DT-14 |

Si 3.2 no muestra un mensaje, se anota qué capitalización falla. Si 3.4 muestra el texto `<caster.predictor.tactic>` sin sustituir, se prueba `/mm reload` y se anota si `recheckForPlaceholders()` hace falta (la llamada `register(String, Placeholder)` ya la ejecuta si el gestor está inicializado, según `javap`).

## 4. Instalación de metas (M01.4)

Para cada caso se toma `debug goals` antes y después y se copia la salida. El jugador está en `CREATIVE`.

| Caso | Acción | Pregunta | Registrar en |
|---|---|---|---|
| 4.1 | `/mm m spawn TPV_Zombie 1`, esperar 2 s, `debug goals nearest` e `inspect nearest` | ¿Quedan solo `float`, las metas de las tácticas del perfil (`fleeConditional` ×2, `moveWithinConditional`, `moveTowardsTargetConditional`, `goToSpawnLocation`), `thepredict:flank`, `thepredict:hold`, `thepredict:owner` y la IA base desplazada +10 con `lookAtTarget`? `inspect`: `Goals: installed for revision <n>` | DT-05 |
| 4.2 | `TPV_ZombieCustomAI`: igual | ¿Quedan solo las metas de ThePredict y su IA base (sus `AIGoalSelectors` desplazadas +10), sin `meleeAttack` duplicado? | DT-05 |
| 4.3 | Alejarse hasta descargar el chunk de `TPV_Zombie` y volver; esperar 2 s | ¿MythicMobs reinstala sus metas al cargar? `inspect` muestra la generación siguiente y `installed for revision <n>`: ThePredict volvió a instalar su conjunto (`debug goals` igual que en 4.1) | DT-05 |
| 4.4 | Con metas de ThePredict instaladas: `/mm reload`, esperar 2 s | ¿MythicMobs reinstala metas en mobs vivos? `inspect` muestra la revisión nueva y `debug goals` un solo `thepredict:owner`, `thepredict:hold` y `thepredict:flank` | DT-05, DT-15 |
| 4.5 | Anotar las UUID de `TPV_Zombie` y `TPV_ZombieCustomAI` (cabecera de `debug goals`); poner `Predictor: false` en ambos y `/mm reload`; `thepredict debug goals <uuid>` para cada uno | ¿Qué conjunto deja `reapplyAIGoalSelectors` con `AIGoalSelectors` que empiezan por `clear` (`TPV_ZombieCustomAI`) y sin ellos (`TPV_Zombie`)? Ninguna clave `thepredict:*`. Devolver `Predictor: true` y `/mm reload` al terminar | DT-04 |
| 4.6 | Comparar las metas con tipo `TARGET` de 4.5 (sin ThePredict) con las de 4.1 | ¿`clear` deja intacto el selector de objetivos? | DT-05 |
| 4.7 | Copiar literalmente las claves de las metas de MythicMobs de 4.1, 4.2 y 4.5 | Claves que Paper asigna a metas NMS | DT-06 |
| 4.8 | `/thepredict status` y copiar la línea `tick_work` de `Latency`; `/mm m spawn TPV_Zombie 1`, 2 s después `status` otra vez; después `/mm m spawn TPV_Zombie 30` y `status` a los 2 s. Opcional: `/spark tickmonitor` durante las apariciones | `tick_work` (p50, p95, p99 y `max`) con 1 y con 30 instalaciones, repartidas a 10 por tick (`decision.maxInstallsPerTick`); las medidas `micros=` por instalación de M01 se retiraron con los presets; la carga masiva de chunk está en el [guion 0.0.1, §5.3](Guion-servidor-0.0.1.md#53-carga-masiva-de-chunk-m054-dt-18) | DT-18 |
| 4.9 | `/mm m spawn TPV_NoPriority 1`, obtener su UUID (sección 0) y `thepredict debug goals <uuid>` | ¿MythicMobs instala las cuatro líneas sin prioridad? ¿Cuál queda `running` con el jugador cerca en `SURVIVAL`? | DT-16 |
| 4.10 | Repetir 4.4 dos veces seguidas | `debug goals` sigue mostrando una sola meta por clave `thepredict:*`. La medida `paperBefore`/`paperAfterClear`/`paperAdded` se retiró con los presets: M05 ya quita las tres claves antes de instalar (alternativa de DT-15), así que la pregunta sobre `clear` queda sin observación directa | DT-15 |
| 4.11 | `/mm m spawn TPV_Piglin 1`, `debug goals nearest` e `inspect nearest` | `inspect`: `Goals: not installed (MythicMobs keeps its AI)`; `debug goals` muestra las metas propias del piglin: confirma la exclusión de especies con `Brain` | DT-19 |

## 5. Semántica de metas condicionales (M01.5)

Jugador A inmóvil en la arena; jugador B se acerca y se aleja. Durante 30 s se toma la distancia cada 10 ticks (por ejemplo con la línea `Movement: … target … blocks` de `debug goals`, o con `/data get entity` y `/tp` de referencia) y se cuentan cambios de sentido. `TPV_Zombie` tiene instalado su conjunto generado (4.1); las intenciones se fijan con `debug intent nearest <táctica> 600` y se renuevan si vencen antes de terminar.

| Caso | Táctica o mob | Pregunta | Registrar en |
|---|---|---|---|
| 5.1 | `retreat` con A y B cerca (meta generada `fleeConditional{distance=10;speed=1.2;mc=[ - predictormove{m=RETREAT} true ]}`) | ¿Huye solo del objetivo o de cualquier entidad? | DT-01, DT-07 |
| 5.2 | `TPV_FleeTarget` (MythicMobs solo: `targetconditions=[ - isTarget true ]`), A lo golpea para ser su objetivo y B se acerca | ¿Limita la huida al objetivo? | DT-07 |
| 5.3 | `TPV_FleePlayer` (`targetconditions=[ - isplayer true ]`) | Alternativa si 5.2 falla | DT-07 |
| 5.4 | `keep_distance` (`fleeConditional` a 5 y `moveWithinConditional` a 9) | ¿`minrange`, `range`, `maxrange` mantienen la banda o solo acercan? | DT-07 |
| 5.5 | `approach` (`moveTowardsTargetConditional{…;minrange=2.5;maxrange=32;…}`) | ¿`maxrange` es la distancia máxima para activarse? ¿Se detiene en `minrange`? | DT-07 |
| 5.6 | Sin intención, alejar el mob más de 24 bloques del spawn (`goToSpawnLocation{maxrange=24;…}` del perfil) | ¿Cuándo vuelve y si suelta el objetivo? La variante `spawn_drop` se retiró con los presets | DT-07 |
| 5.7 | `hold` con la IA base | ¿`lookAtTarget` corre en paralelo con `thepredict:hold` (`running` en los dos)? | DT-01 |
| 5.8 | `keep_distance` durante 30 s | Cruces de 5 y 9 bloques en 30 s; valores iniciales sin oscilación visible | DT-07 |
| 5.9 | `TPV_Skeleton` con `keep_distance` (IA base `1 bowAttack`, desplazada a 11) | ¿`bowAttack` dispara mientras las metas de la banda mueven al esqueleto, y la banda se mantiene? | DT-01 |

## 6. Metas de Paper (M01.6)

| Paso | Acción | Esperado | Registrar en |
|---|---|---|---|
| 6.1 | `debug intent nearest hold 400` en `TPV_Zombie`, jugador en movimiento | El mob no se desplaza y sigue mirando al jugador; `debug goals` muestra `thepredict:hold … running` y `lookAtTarget … running` | DT-08 |
| 6.2 | Durante 6.1, observar si alguna meta base (`meleeAttack`, `randomStroll`) aparece `running` | Ninguna | DT-08 |
| 6.3 | Con 6.1 activo y el jugador en `SURVIVAL` a 6–10 bloques: `debug release nearest` e inmediatamente `debug goals nearest` dos veces | La meta base (`meleeAttack` o `randomStroll`) o una meta local aparece `running` en la primera lectura posible. La lectura tick a tick (`Release +1…+4 tick`) se retiró con la herramienta de M01; el tiempo exacto de cesión (≤2 ticks, plan §2) no se puede leer tick a tick con los comandos que quedan; el [guion 0.0.1, §6.4](Guion-servidor-0.0.1.md#64-cesión-a-la-ia-base-tras-release-dt-08) comprueba la cesión en la primera lectura posible | DT-08 |
| 6.4 | Jugador en `SURVIVAL` con escudo levantado 2 s antes: `debug intent nearest flank_and_strike 400` | El mob alcanza un lateral; `Movement: … path yes` durante el recorrido | DT-08 |
| 6.5 | Retirado en M12: los presets `paper`, `paper_look` y `paper_unknown` y el marcador de tipos configurables desaparecieron; el marcador `owner` declara siempre `LOOK` | — | DT-08 |
| 6.6 | Tras 4.4, `debug goals nearest` e `inspect nearest` | Una sola `thepredict:owner`; `inspect` con la revisión nueva y sin reinstalaciones repetidas: `getGoal(owner)` devuelve la instancia de ThePredict | DT-15 |

`doNothing` y `Mob#setAware(false)` quedan descartados por la evidencia estática del plan §2; si se prueban, se anota el resultado en DT-08.

## 7. Hilos y ruta asíncrona (M01.7)

La traza `debug trace` y la sonda `predictorprobe` se retiraron en M12. El hilo de evaluación se observa con spark, incluido en Paper:

1. `/spark profiler start --thread *` con `TPV_Zombie` y `debug intent nearest retreat 600`, luego `keep_distance` y `approach` (30 s cada una).
2. `/spark profiler stop --save-to-file` (sin `--save-to-file`, spark 1.10.180 sube el informe a su servicio web; abrirlo en el visor web también exige la aprobación del administrador, ver DT-29) y, en el informe, buscar `PredictorMoveCondition`: copiar en qué hilos aparece su evaluación (`Server thread`, `Mythic` o un hilo asíncrono de MythicMobs).
3. Repetir con `TPV_ZombieAsync` y los casos 5.1, 5.4 y 5.5.
4. Las líneas `MythicMobs resolved condition … on thread …` del arranque dan el hilo de **resolución**, no el de evaluación.

**Registrar en:** DT-09 (hilos) y DT-10 (`AsyncPathfinding`). Si el informe de spark no muestra la condición (evaluación demasiado breve para el muestreo), se anota y DT-09 sigue `Abierta` con su alternativa: las condiciones ya son seguras en cualquier hilo (T3).

## 8. Señales, placeholders y trigger (M01.8)

Preparación: `decision.logDecisions: true` en `config.yml` y reinicio (las líneas `Mob <uuid> … choice strike: <resultado>` salen solo con esa opción).

| Paso | Acción | Esperado | Registrar en |
|---|---|---|---|
| 8.1 | Con `TPV_Zombie` en otra táctica (`debug intent nearest approach 100`), `debug intent nearest hold 400`: ThePredict envía la señal `PREDICTOR_HOLD` al cambiar de táctica | ¿Aparece `onSignal PREDICTOR_HOLD fired`? ¿También `onSignal predictor_hold fired`? | DT-11 |
| 8.2 | Retirado en M12 (`debug signal`): ThePredict siempre envía la señal en mayúsculas; 8.1 ya compara las dos grafías de `~onSignal` | — | DT-11 |
| 8.3 | Placeholders con y sin sesión (paso 3.4) | Nombres confirmados | DT-11 |
| 8.4 | Jugador en `SURVIVAL` a 2 bloques: `debug intent nearest hold_and_strike 400` (movimiento `HOLD`, skill `TPV_Strike` con condición `predictormove{m=APPROACH}`) | Línea `… hold_and_strike choice strike: NOT_ELIGIBLE` y ningún `[TPV] TPV_Strike executed`: la condición se respeta | DT-12 |
| 8.5 | `debug intent nearest press_and_strike 400` y, antes de 5 s, `debug intent nearest approach 20` seguido de `debug intent nearest press_and_strike 400` | Primera: `choice strike: CAST` y `[TPV] TPV_Strike executed`. Segunda: o bien `Debug intent refused … not eligible at the last capture: its skill was on its native cooldown or it needs a target, …`, si una captura ya vio la skill en su `Cooldown: 5`, o bien la línea `choice strike: NOT_ELIGIBLE`; en ningún caso un segundo mensaje `executed` | DT-12 |
| 8.6 | Jugador en `CREATIVE` (sin objetivo): desde consola `thepredict debug intent <uuid> press_and_strike 100` | La intención se rechaza por no elegible (sin objetivo `APPROACH` no está disponible) o, si el mob no tiene captura, se aplica y la línea `choice strike: NOT_ELIGIBLE` muestra que sin objetivo ThePredict no lanza una skill que lo requiere (M10). El caso de `@trigger` sin jugador de M01 ya no se da | DT-12 |

## 9. Propiedad de la IA de movimiento (M05.7)

Preparación: copiar el ejemplo empaquetado siguiendo `plugins/ThePredict/examples/README.txt` y comprobar la línea `Profiles:` de `/thepredict status` en sus pasos 4 y 6; `/thepredict status` no lista nombres de perfiles, solo cuentas. Con el pack de la sección 1.4 instalado, sus cinco perfiles `tpv_*` también cuentan (los avisos pueden variar; no debe haber errores):

- Paso 4 (`ThePredictGuardian` aún con `Predictor: false`): `[ThePredict] Profiles: LOADED (revision <n>, 6 profiles read, 5 active, 0 errors, <w> warnings)`. En una instalación sin el pack: `[ThePredict] Profiles: SETUP_PENDING (revision <n>, 1 profiles read, 0 active, 0 errors, 0 warnings): no mob targeted by a profile has Predictor: true; set it in MythicMobs and reload MythicMobs`.
- Paso 6 (`Predictor: true` y `/mm reload`): `[ThePredict] Profiles: LOADED (revision <n>, 6 profiles read, 6 active, 0 errors, <w> warnings)`; sin el pack, `[ThePredict] Profiles: LOADED (revision <n>, 1 profiles read, 1 active, 0 errors, 0 warnings)`.

Después, `/mm m spawn ThePredictGuardian 1`. El mob del ejemplo ya trae `Options.FollowRange: 32` y la tabla de amenaza. Cada caso toma `debug goals nearest` antes y después; su última línea (`Movement: movement goal running … path … path reaches target … target …`) es la lectura de `MobControl.progress`. Nada de esto usa Jev.

| Paso | Acción | Esperado | Registrar en |
|---|---|---|---|
| 9.1 | Esperar 2 s tras la aparición; `inspect nearest` y `debug goals nearest` | `Goals: installed for revision <n>; 12 goals …`; sin `An error occurred while adding an AIGoalSelector` ni `Failed to construct AI pathfinder` en consola; metas `float`, `goToSpawnLocation`, dos `fleeConditional`, `moveWithinConditional`, `moveTowardsTargetConditional`, `meleeAttack`, `randomStroll`, `lookAtTarget` y `thepredict:flank`, `thepredict:hold`, `thepredict:owner` | M05, DT-01, DT-05 |
| 9.2 | `debug intent nearest approach 400` con el jugador a 10 bloques | El mob se acerca y se detiene hacia 2,5 bloques; `movement goal running yes` mientras se mueve | M05.7, DT-07 |
| 9.3 | `debug intent nearest keep_distance 600` con el jugador acercándose y alejándose durante 30 s | La banda 5–9 se mantiene sin alternar avance y retroceso; `target` anotado cada 10 ticks | M05.7, DT-07 |
| 9.4 | `debug intent nearest retreat_and_defend 600` y luego alejar al mob más de 24 bloques del punto de aparición | Se aleja por terreno transitable y `goToSpawnLocation` lo devuelve a la arena | M05.7, DT-07 |
| 9.5 | `debug intent nearest hold 400`; después `flank_and_strike` contra un jugador con escudo levantado (2 s antes) y contra uno sin escudo | `hold`: no se desplaza y sigue mirando al jugador (`thepredict:hold` y `lookAtTarget` `running`). `flank_and_strike` con escudo: termina fuera del ángulo de bloqueo. Sin escudo levantado la táctica no es elegible (M10 solo hace elegible `FLANK` contra un escudo): `debug intent` la rechaza con `Debug intent refused … not eligible at the last capture: … FLANK was not available (no target, or FLANK without a raised shield to flank)` | M05.7, DT-08 |
| 9.6 | Desvincular `ThePredictGuardian` y un mob con `AIGoalSelectors` que empiece por `clear` poniendo `Predictor: false` y `/mm reload`; `debug goals <uuid>` con las UUID anotadas antes | En `status`, `Lifecycle: … unbound=` sube; sin `clear` quedan las metas generadas sin `thepredict:*` y la IA base actúa; con `clear` vuelven las del mob | M05.4, DT-04 |
| 9.7 | `/mm reload` dos veces seguidas con el mob vinculado | Tras cada recarga la revisión nueva reinstala una vez (`inspect`) y no hay claves `thepredict:*` repetidas | M05.2, DT-15 |
| 9.8 | Descarga y carga del chunk; 30 mobs vinculados cargando a la vez | Todos quedan instalados en pocos ticks (`inspect` de varios y `tick_work` en `status`); el reparto por tick lo mide M13 ([guion 0.0.1, §5.3](Guion-servidor-0.0.1.md#53-carga-masiva-de-chunk-m054-dt-18)) | M05.4, M13 |
| 9.9 | Con el perfil v2 `tpv_piglin` del paso 1.4 activo, `/mm m spawn TPV_Piglin 1`, `inspect nearest` | `Goals: not installed (MythicMobs keeps its AI)` y `debug goals` con las metas propias del piglin. Si `inspect` responde `has no session`, el perfil no cargó: revisar `/thepredict status` | DT-19 |
| 9.10 | Copiar el mob y el perfil del ejemplo cambiando `Type` del mob a `SLIME` (y `MythicID`/ID del perfil a un nombre nuevo), `/mm reload`, spawn e `inspect nearest` | `Goals: not installed (MythicMobs keeps its AI)`; ninguna línea `not valid for mob type` en consola porque no se intenta instalar | M05.2, DT-15 |
| 9.11 | En un mob del ejemplo, añadir a su `BaseAI` una línea que MythicMobs no reconozca (por ejemplo `3 metaInexistente`), `/mm reload`, spawn e `inspect nearest`; después `/mm reload` otra vez | Aviso con límite en consola con la causa `install incomplete: 1 MythicMobs goals missing`, `inspect` con `not installed`, el mob conserva su IA de MythicMobs y `thepredict:owner` es el marcador de rechazo. Sin cambios en el archivo, la segunda recarga reintenta con la revisión nueva y vuelve a rechazar sin repetir errores por tick. Tras corregir el archivo y `/mm reload`, `installed for revision <n>` | M05.2, DT-15 |

## 10. Sesiones y ciclo de vida (M06)

Preparación: la de la sección 9 (perfil `thepredict_guardian` activo con `Predictor: true`), con `Options: { FollowRange: 32, Despawn: false }` en el mob copiado. Sin `Despawn: false` el mob usa `DespawnMode.NORMAL`, que no se guarda en disco y despawnea al descargar su chunk (javap de MythicMobs 5.13.1: `DespawnMode.get` convierte `false`, `no` y `never` en `NEVER`, que se guarda y no despawnea al descargar); así 10.1 y 10.3 no serían posibles. Para 10.5 se copia además el mismo mob con otro ID, `ThePredictGuardianTemp`, con `Options: { Despawn: true }` explícito (no depende de `Mobs.DespawnByDefault` del servidor) y con su propio perfil (el mismo archivo copiado con otro ID de perfil y `MythicID: ThePredictGuardianTemp`). Después de cada paso se ejecuta `/thepredict status` y se copian las líneas `Bound mobs: …` (sesiones activas y suspendidas por nivel) y `Lifecycle: …` (abiertas, cerradas por causa, liberaciones), y `debug goals nearest` cuando el mob sigue vivo y vinculado: su cabecera termina en `generation <n>: …`, la generación de la sesión.

| Paso | Acción | Esperado | Registrar en |
|---|---|---|---|
| 10.1 | Con dos `ThePredictGuardian` vivos, reiniciar el servidor sin matarlos y ejecutar `/thepredict status` al entrar | El escaneo inicial, o la carga del chunk si aún no estaba cargado, abre ambas sesiones (`2 active`) en pocos ticks, sin avisos en consola | M06.2 |
| 10.2 | `/mm m spawn ThePredictGuardian 1` y, en el mismo segundo, `debug goals nearest` dos veces | La sesión aparece un tick después de la aparición (`1 active` más), con `generation 0` | M06.2, DT-05 |
| 10.3 | Alejarse hasta descargar el chunk del mob y volver | Al descargar: `1 suspended` y `ended … despawned` y `removed` sin cambios (la causa `UNLOAD` de `EntityRemoveEvent` suspende). Al volver: `1 active` y `generation 1` | M06.2, DT-20 |
| 10.4 | Matar el mob | `died` sube en 1; la sesión desaparece | M06.2 |
| 10.5 | `/mm m spawn ThePredictGuardianTemp 1`, comprobar `1 active` más y alejarse hasta descargar su chunk. Si el entorno tiene una forma de descartar un mob sin muerte ni despawn (un comando o plugin), probarla con otro mob | `despawned` sube en 1 para el mob temporal (su `DespawnMode` despawnea al descargar); según el orden entre `ActiveMob.remove` y el evento puede subir `removed` en su lugar, y DT-20 registra cuál se observa; con el descarte, `removed` sube en 1. Tras 200 ticks no queda ninguna sesión fantasma activa (`reconciled` solo sube si faltó algún evento) | M06.2, M06.6, DT-20 |
| 10.6 | Llevar un mob por un portal del Nether y, en otra prueba, `/tp` del mob a otro mundo | `generation` aumenta al cambiar de mundo (una o dos unidades; anotar cuántas y qué evento llegó) y la sesión sigue `active` | M06.2, DT-21 |
| 10.7 | `/mm reload` sin cambios | `generation` aumenta en uno para cada mob vivo (dos si la verificación diferida de condiciones desactiva un contexto); `Bound mobs` no pierde sesiones | M06.2 |
| 10.8 | Desvinculación sin tocar `Predictor`: `debug goals nearest` (anotar la UUID); mover `plugins/ThePredict/AI/ThePredictGuardian.yml` fuera de `AI` y `/mm reload`; en el tick siguiente `thepredict debug goals <uuid>` | Antes: `thepredict:*` en la lista. Después: `unbound` sube en tantos como `ThePredictGuardian` vivos haya, la cabecera dice `(no session)`, ninguna clave `thepredict:*` y, si el mob tiene `AIGoalSelectors` con `clear`, sus metas de MythicMobs (DT-04). Devolver el archivo a `AI` y `/mm reload` al terminar | M06.2, M05.4, DT-04 |
| 10.9 | Desvinculación con `Predictor: false`: `debug intent nearest retreat_and_defend 600` con el jugador a 5 bloques (el mob huye); poner `Predictor: false` en el mob y `/mm reload` | `unbound` sube en 1 en `Lifecycle` (contador acumulado); el mob deja de huir en ese momento y vuelve a perseguir y atacar al jugador con su IA base | M06.2, M05.4 |
| 10.10 | Con 250 mobs vinculados vivos (`/mm m spawn ThePredictGuardian 250`), `/stop` | El apagado termina sin errores ni pausas visibles; no hace falta más evidencia que el registro de consola del apagado | M06.2 (T7) |

## 11. Contexto nativo y percepción (M07)

Preparación, además de la de la sección 10 (perfil `thepredict_guardian` activo con `Predictor: true`, y `Options: { FollowRange: 32, Despawn: false }` en el mob copiado, para que sobreviva a las descargas de chunk):

1. En `plugins/ThePredict/AI/ThePredictGuardian.yml`, dentro de `Contexts`, añadir este contexto nativo y ejecutar `/mm reload`; `/thepredict status` debe seguir mostrando el perfil activo y sin errores:

   ```yaml
       wounded_native:
         Conditions:
         - healthpercent{p=<30%}
         Priority: 95
         Tactics: [retreat_and_defend, keep_distance_and_shoot]
   ```

2. Jugador A en modo `SURVIVAL` (`/gamemode survival`). Los pasos 11.2, 11.6 (escudo desactivado y ayuda a un aliado) y 11.10 necesitan un segundo jugador B en `SURVIVAL`, y el paso 11.11 un tercero; si no hay segunda cuenta, esos pasos se anotan como no ejecutados.
3. `/mm m spawn ThePredictGuardian 1` a unos 6 bloques de A y golpearlo una vez para que lo tome como objetivo.
4. `debug capture` se retiró en M12. Cada paso mantiene la situación al menos 2 s (una evaluación del encuentro captura como mucho cada 20 ticks) y ejecuta `/thepredict inspect nearest` (o, desde la consola, `thepredict inspect <uuid>` con la UUID de `debug goals`, para capturar mientras el jugador mantiene un ítem en uso), copiando las líneas `Perception`, `Target` y `Events`: son los hechos de la última captura del encuentro, solo con alias, bandas y señales. Tras cada paso se copia además la línea `Context: …` de `/thepredict status` y la línea `capture` de `Latency`; sus contadores son acumulados, así que se anota cuánto "sube".

| Paso | Acción | Esperado | Registrar en |
|---|---|---|---|
| 11.1 | A como objetivo a unos 6 bloques | `Perception: health HEALTHY; target player_1 RANGED …`; `Target: player_1 health HEALTHY, armor …`; ninguna línea contiene el nombre ni la UUID de A; en `Latency`, `capture` suma muestras con p50, p95, p99 y `max` | M07.1 |
| 11.2 | B en `SURVIVAL` a 40 bloques del mob y A a 6; `inspect`. B se acerca a 10 bloques; `inspect`. B pasa a `CREATIVE` a 10 bloques; `inspect` | `perceived players 1`; después `2`; en `CREATIVE`, `1` otra vez | M07.2 |
| 11.3 | A detrás de un muro de 3 bloques de alto, con el escudo levantado; `inspect` desde la consola | `Perception: … visible no …; cues [NO_LINE_OF_SIGHT]` y `Target: player_1 … cues []` | M07.2 |
| 11.4 | `/effect give @s minecraft:invisibility 60` a A, con un escudo levantado y a la vista; `inspect` desde la consola | `Target: player_1 … cues [INVISIBLE]` y `visible no` | M07.2, DT-22 |
| 11.5 | Cada situación, con `inspect` desde la consola mientras dura (mantenerla 2 s): `/give @s minecraft:shield[minecraft:blocks_attacks={block_delay_seconds:5}]` y levantarlo (los 5 primeros segundos y después); arco tensado 0,5 s y 2 s; ballesta cargada en la mano; beber una poción; tótem en la mano secundaria; lanzar una perla; `/give @s minecraft:iron_spear` en la mano; planear con élitros; golpear al aire; saltar desde 5 bloques con una maza en la mano | En el orden de la acción, en `Target … cues`: `RAISING_SHIELD` y luego `BLOCKING` (nunca juntos); `DRAWING_BOW` y luego además `BOW_FULLY_DRAWN`; `CROSSBOW_LOADED`; `CONSUMING`; `HOLDS_TOTEM`; `PEARL_ON_COOLDOWN`; `LONG_REACH_WEAPON`; `GLIDING`; `ATTACK_RECHARGING`; `MACE_DIVE`. Las señales breves (perla, golpe al aire, caída) solo aparecen si una captura cae dentro de su duración; tensar el arco o lanzar la perla marca contexto pendiente y adelanta la captura. Anotar las que no se observen | M07.3, DT-23 |
| 11.6 | Eventos, uno por vez y con `inspect` después de cada uno, con A **sin armadura** (su desgaste emitiría `EntityEquipmentChangedEvent` y marcaría contexto): el mob golpea a A sin escudo y luego con el escudo levantado; A dispara una flecha al mob y otra al suelo; A lanza una perla; A bebe una poción; con un tótem en la mano, `/damage @s 100 minecraft:generic`; B golpea con un hacha el escudo levantado de A; con B a menos de 10 bloques y percibido, A lanza a B una poción arrojadiza de curación (`minecraft:splash_potion[minecraft:potion_contents={potion:"minecraft:healing"}]`); con `/give @s minecraft:crossbow[minecraft:enchantments={"minecraft:multishot":1}]`, un disparo al mob a 3 bloques | `Events: … MELEE_HIT player_1 at tick …` y `MELEE_BLOCKED`, `RANGED_HIT_ON_MOB`, `RANGED_MISS`, `PEARL_THROWN`, `POTION_CONSUMED`, `TOTEM_USED`, `SHIELD_DISABLED`, `ALLY_ASSISTED` (de `player_1`); en `Context`, `events recorded` sube en 1 por evento. El disparo múltiple deja un solo `RANGED_HIT_ON_MOB` y `repeated` sube en tantas flechas como acierten además de la primera (hasta 2) | M07.5 |
| 11.7 | Sin escudo, preparar un arco y soltarlo, cargar una ballesta, cambiar el ítem de la mano, pulsar teclas de movimiento durante 2 s; `status` tras cada una | `pending marks` sube; ninguna línea nueva en `Events`. Pulsar teclas 2 s sube `pending marks` como mucho en 5 (una marca cada 10 ticks) | M07.5 |
| 11.8 | `/damage @e[type=minecraft:zombie,sort=nearest,limit=1] 75 minecraft:out_of_world`; `inspect` | `Perception: health CRITICAL; … contexts [low_health_phase, wounded_native]` (en cualquier orden) y `eligible` solo con `retreat_and_defend` y `keep_distance_and_shoot` que estén listas; sin errores de condición en consola | M07.7, DT-24 |
| 11.9 | Con un mob nuevo, A le dispara tres flechas; `inspect`; seguir peleando 20 s e `inspect` otra vez | `Target: … combat <n> s` crece entre las dos lecturas. La clasificación de daño por etiquetas (`Damage entries by tag`) se retiró en M12 porque nada la consumía | M07.8, DT-25 |
| 11.10 | B se aleja a 60 bloques (deja de figurar: `perceived players 1`) y después B, sin golpear al mob ni ser su objetivo, lanza perlas, bebe pociones y dispara flechas al suelo | Ningún contador de `Context` cambia | M07.5 |
| 11.11 | **Exige dos cuentas adicionales** (tres jugadores en total); si no las hay, solo el caso de 1 jugador y se anota el resto como no ejecutado. Con 1 y con 3 jugadores cerca del mob, 20 s de combate | Copiar la línea `capture` de `Latency` (p50, p95, p99, muestras y `max`); el caso de 16 jugadores y el de 100 mobs para las sondas los mide M13 ([guion 0.0.1, §5.4](Guion-servidor-0.0.1.md#54-sondas-con-100-mobs-m076-y-captura-con-varios-jugadores-m071)) | M07.1, M07.6, M13 |

## 12. Ejecución táctica y fallback (M10)

Preparación, además de la de la sección 11 (perfil `thepredict_guardian` activo, jugador A en `SURVIVAL`, `Options: { FollowRange: 32, Despawn: false }` en el mob):

1. En `config.yml`, `decision.logDecisions: true` y reiniciar el servidor (la configuración se lee al habilitar): la consola muestra líneas `Mob <uuid> LOCAL <táctica>`, `… choice <choice>: <resultado>` y `… fell back: <causa>`, como mucho 20 cada 10 s.
2. En el mob de ejemplo, una línea por táctica en `Skills`: `- message{m="signal <caster.predictor.tactic> <caster.predictor.level>"} @PIR{r=20} ~onSignal:PREDICTOR_PRESS_AND_STRIKE`, y lo mismo con `PREDICTOR_APPROACH`, `PREDICTOR_KEEP_DISTANCE`, `PREDICTOR_KEEP_DISTANCE_AND_SHOOT`, `PREDICTOR_RETREAT_AND_DEFEND`, `PREDICTOR_FLANK_AND_STRIKE`, `PREDICTOR_CLOSE_IN_AND_SLAM` y `PREDICTOR_HOLD` (las ocho tácticas del ejemplo desde M15.9: ThePredict emite la señal de cualquier táctica nueva); `/mm reload`. El mob listo está en [`paquete-pruebas/archivos/07-senales/`](paquete-pruebas/archivos/07-senales/).
3. Sin clave de Jev salvo en 12.8: `/thepredict status` muestra `State: Jev UNCONFIGURED (…)`. Ningún paso salvo 12.8 hace llamadas a TypeSafe.

| Paso | Acción | Esperado | Registrar en |
|---|---|---|---|
| 12.1 | `/mm m spawn ThePredictGuardian 1` a 6 bloques de A y golpearlo | En ≤2 s una línea `LOCAL <táctica>`; `TPV_ShowIntent` (o `<caster.predictor.level>`) muestra `local` y `controlled`; `inspect` con `level LOCAL`; ninguna solicitud en `Budget` (`Server: 0/…`) | M10.6 |
| 12.2 | Seguir el combate 30 s moviéndose alrededor del mob | Un mensaje `signal <táctica> <nivel>` (el que imprime la línea `~onSignal:PREDICTOR_<TÁCTICA>` de `07-senales`) cada vez que cambia la táctica, nunca al replanificar la misma (cada 1 s) | M04.6, DT-11 |
| 12.3 | Con `press_and_strike` activa y A a 6 bloques, contar los golpes de `ThePredictGuardianMelee` (etiqueta `MELEE_ATTACK`) y los de la meta base; añadir a la skill una condición `- targetwithin{d=3} true` y repetir; repetir con una choice `Kind: VANILLA_MELEE` | Sin clave (nivel `LOCAL`): una línea `Mob <uuid> LOCAL press_and_strike` al empezar; a 6 bloques ningún golpe de la skill y una sola línea `press_and_strike choice melee: NOT_ELIGIBLE`; al llegar a 2,5 bloques entre cajas, una línea `… choice melee: CAST` y el golpe. Cada replanificación local (≈1 s) de la misma táctica no escribe la línea de táctica ni emite señal; una línea de resultado solo aparece cuando el resultado cambia, nunca una por ciclo. Con clave (12.8, nivel `JEV`): una línea `Mob <uuid> JEV press_and_strike` al adoptarla, las mismas líneas `NOT_ELIGIBLE` y `CAST` una sola vez cada una y ningún segundo golpe mientras se renueva. En ambos niveles, ningún golpe de la meta base mientras `state=controlled`; con la condición, la skill espera sin consumirse hasta que se cumple; con `VANILLA_MELEE`, un solo golpe vanilla por intención y nunca a más de 2,5 bloques entre cajas | M10.3, M10.9, DT-12, DT-26 |
| 12.4 | `/damage @s 1` a A justo antes de que el mob ataque (o golpes de otro mob) | Líneas `DEFERRED_INVULNERABLE` y después `CAST` en la misma intención; el golpe hace daño | M10.3, DT-26 |
| 12.5 | A bloquea con un escudo de frente; después A levanta el escudo y el mob tiene `flank_and_strike` | Frente al escudo: `SKIPPED_SHIELD`. Con `flank_and_strike`: `NOT_ELIGIBLE` mientras el mob está dentro del ángulo y `CAST` solo fuera; el golpe hace daño completo | M10.3, M10.8 |
| 12.6 | A sube a un pilar de 3 bloques sin acceso con el mob en `press_and_strike` | Tras dos decisiones (≈2 s), `fell back: NO_PROGRESS`; el mob pasa a otra táctica o a su IA base; `inspect` con `last fallback NO_PROGRESS` | M10.4, M05.5 |
| 12.7 | A pasa a `CREATIVE`, o se aleja 60 bloques | `TPV_ShowIntent` vuelve a `state=base` en ≤2 s y la IA base (MythicMobs) toma el control sin reinstalar metas (`debug goals` igual que antes, `inspect` con la misma revisión) | M10.6 |
| 12.8 | **Llamada de pago, requiere aprobación del usuario.** Con la clave: `/thepredict check` y después combate de 60 s; después, cortar la red del servidor 30 s en pleno combate y restablecerla | `check` responde enseguida y, al llegar, `Jev check: READY (…)`; líneas `JEV <táctica>`; al cortar, la intención `JEV` sigue hasta vencer y después `LOCAL` con `SERVICE_PAUSED` en ≤2 ticks; `status` muestra `Pause: ends in … s` y `Recent failures`; al volver, `JEV` con la primera respuesta aceptada; ningún `MODEL_CHANGED` con `jev-1.13.0` | M10.1, M10.6, M12.2 |
| 12.9 | En pleno control local, `debug intent nearest hold 400` y después `debug release nearest` | `hold` controla (`inspect` con `[debug]`) sin que M10 lo cambie; tras `release`, nivel 4 y control local de nuevo en ≤2 ticks | M10, M12.3 |

## 13. Amenaza enriquecida (M11)

Preparación, además de la de la sección 12 (`decision.logDecisions: true`, sin clave de Jev):

1. El mob del ejemplo ya activa la tabla nativa (`Modules.ThreatTable: true`, `ThreatTable.UseDamageTaken: true`, `DecayUnreachable: false`). Añadir a su `Skills` una línea que muestre el líder cada segundo y `/mm reload`:

   ```yaml
     Skills:
     - message{m="top <caster.tt.top>"} @PIR{r=20} ~onTimer:20
   ```

   El nombre `tt.top` sale de `@MythicPlaceholder(placeholder="tt.top")` (javap, DT-27); si no se sustituye, el líder se deduce de las líneas `Threat …` y del objetivo que persigue el mob, y se anota en DT-27. `inspect nearest` muestra además `Threat: table on, <n> entries, leader …, total …; added by ThePredict in the open windows …`.
2. El perfil `ThePredictGuardian.yml` usa los pesos por defecto (`Threat` sin cambios: 1,5; 1,0; 0,25; 2,0; 20,0). Las líneas de M11 tienen la forma `Threat +<puntos> <HECHO> for player_<n> sent to the table of mob <uuid>` y nunca nombran al jugador; comparten el límite de 20 líneas cada 10 s de la sección 12. La línea `Threat: …` de `status` (`Counters`) suma lo enviado.
3. Hacen falta dos jugadores, A y B, en `SURVIVAL`; solo 13.7 exige cuatro, con cuentas adicionales como 11.11.

| Paso | Acción | Esperado | Registrar en |
|---|---|---|---|
| 13.1 | A combate cuerpo a cuerpo sin escudo; B dispara flechas al mob desde 10 bloques durante 10 s | Por flecha de B, una línea `Threat +<daño final> RANGED_HIT_ON_MOB for player_<B>`; ninguna por las flechas de A mientras A es el objetivo; en cada ventana de 5 s (100 ticks) la suma de las líneas de B no pasa de 20; `top` solo cambia a B cuando su amenaza supera la de A en un 10 % | M11.2, M11.3, DT-27 |
| 13.2 | A bloquea de frente con escudo 20 golpes seguidos sin atacar | Una línea `Threat +0.25 MELEE_BLOCKED for player_<A>` por golpe bloqueado (golpe sin escudo: ninguna); `top` no cambia por los bloqueos solos; `inspect` muestra la amenaza añadida en la ventana | M11.2, DT-27 |
| 13.3 | B lanza una poción arrojadiza de curación que alcanza a A y a B; después una persistente de regeneración sobre A | Una línea `Threat +1.50 ALLY_ASSISTED for player_<B>` por mob del encuentro con la arrojadiza; con la persistente, líneas repetidas cuya suma no pasa de 20 por ventana de 5 s; una poción de daño no da ninguna | M11.2, M11.3 |
| 13.4 | A sube a un pilar de 3 bloques sin acceso mientras B está cerca y no es el objetivo | Mientras el mob sigue una ruta que no llega a A (se mueve al pie del pilar), tras dos decisiones una línea `Threat -<2 × segundos> UNREACHABLE for player_<A>` en cada decisión (con el latido de 20 ticks, `-2.00`); si el mob se queda quieto sin ruta, ninguna línea; el líder pasa a B cuando la amenaza de A baja de la de B / 1,1; al bajar A del pilar, las líneas paran. Un mob pegado a A en el suelo nunca escribe `UNREACHABLE` | M11.2, DT-27 |
| 13.5 | Repetir 13.4 con `ThreatTable.DecayUnreachable: true` en el mob y `/mm reload` | La consola muestra, al recargar el catálogo, el aviso `Threat.UnreachableDecayPerSecond: is ignored because mob '…' enables ThreatTable.DecayUnreachable`; ninguna línea `UNREACHABLE` (sin doble decaimiento) | M11.2, M03 |
| 13.6 | Mientras B acosa, A muere, se desconecta o cruza a otro mundo; repetir con un mob sin `Modules.ThreatTable` | Ningún error en consola; la tabla no gana entradas de A (`top` nunca muestra a A mientras no está vivo, en línea y en el mismo mundo); sin tabla, `inspect` dice `no threat table (Modules.ThreatTable off): enriched threat inactive for this mob`, las líneas `Threat …` pueden aparecer pero el mob no cambia de objetivo por ellas | M11.4, DT-27 |
| 13.7 | Cuatro jugadores con roles mezclados (tanque, arquero, sanador, escudo) durante 60 s | Ningún cambio de `top` dos veces dentro de la misma ventana de 5 s; se copian las líneas y los cambios de líder para M13 | M11.6 |
| 13.8 | Repetir 13.1 a 13.4 con `Threat: { AssistsOthers: 0, RangedHarass: 0, BlockedHits: 0, UnreachableDecayPerSecond: 0 }` y `/mm reload` | Ninguna línea `Threat …`: el brazo sin amenaza enriquecida de la comparación de M13 | M11.1, M11.6 |

## 14. Cierre

- Cada fila de la tabla de M01.9 tiene su entrada con evidencia y estado.
- `M01-Verificacion-nativa.md` cambia a `Verificado en servidor` solo si todas las entradas están `Confirmada` o `Alternativa aplicada`.
- Se borran `permissions.yml` y el pack si el servidor se reutiliza para otra cosa.

## Errata (pulido del 2026-10-17, DOC-10)

En el paso 3.4, la línea esperada sin sesión es `none/none/none/none/none`. Con sesión sin intención no cambia: sigue siendo `none/none/base/0.00/base`. El paquete de pruebas y el registro de servidor ya lo dicen así.
