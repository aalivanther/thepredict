# Guía del administrador de ThePredict 0.0.1

> Para quien instala, configura, diagnostica y paga ThePredict en un servidor Paper, sin leer Java. Complementa a la [guía del autor](Guia-del-autor.md).
>
> **Estado de la versión (2026-09-29): candidata pendiente de integración.** El build, las 1576 pruebas automáticas y el cliente de Jev con la cuenta real de TypeSafe están verificados. **Nada se ha observado todavía en un servidor Paper real**: lo que depende del juego lleva la marca **(pendiente de servidor)**. Los valores de capacidad y los defaults definitivos se publican solo con evidencia del servidor; hasta entonces figuran como **pendiente de medición en servidor**.
>
> Una prueba automática (`UserGuidesTest`) comprueba que cada clave de `config.yml`, cada `ServiceState` y cada `FallbackReason` aparecen en esta guía, y que los defaults coinciden con el `config.yml` incluido en el JAR.

## Índice

1. [Requisitos y matriz de versiones](#1-requisitos-y-matriz-de-versiones)
2. [Instalación y primer inicio](#2-instalación-y-primer-inicio)
   - [2.1 Ubicación de la configuración y migración](#21-ubicación-de-la-configuración-y-migración)
3. [Clave de Jev](#3-clave-de-jev)
4. [`config.yml` v2](#4-configyml-v2)
   - [4.13 Claves sin efecto en esta versión](#413-claves-sin-efecto-en-esta-versión)
5. [Comandos y permisos](#5-comandos-y-permisos)
6. [Diagnóstico](#6-diagnóstico)
   - [6.6 Propósito interno (`Purpose`) y migración a `Intention`](#66-propósito-interno-purpose-y-migración-a-intention)
7. [Costos, límites y capacidad](#7-costos-límites-y-capacidad)
8. [Grabación de escenarios y herramientas de pago](#8-grabación-de-escenarios-y-herramientas-de-pago)
9. [Migración desde el esquema 1](#9-migración-desde-el-esquema-1)
10. [Desactivar y apagar](#10-desactivar-y-apagar)

## 1. Requisitos y matriz de versiones

- **Java 25.** ThePredict lo comprueba al iniciar (`Java <n> is unsupported; ThePredict requires Java 25 or newer`); una JVM anterior puede rechazar el bytecode antes de esa comprobación.
- **Paper 26.2.** Con una versión de Minecraft anterior: `Minecraft <v> is unsupported; this Paper build requires 26.2 or newer`. Folia no está soportado.
- **MythicMobs Premium 5.13.1** (dependencia obligatoria, `depend: [MythicMobs]`).
- **Para decidir con Jev**, una clave de TypeSafe. Sin clave, ThePredict no consulta ningún servicio y los mobs pelean en modo local.

Matriz de versiones. Solo se declara lo que tiene evidencia; cualquier otra combinación (otras builds de Paper 26.x, otras versiones de MythicMobs, Kev) es **no probada**.

| Componente | Versión | Evidencia actual | Probada en servidor |
|---|---|---|---|
| ThePredict | 0.0.1 | `build.gradle.kts` y `plugin.yml`; `verifyPluginJar` exige que coincidan | Pendiente |
| Java | 25 (Temurin 25.0.4.1+1 en la máquina de desarrollo) | Toolchain del build; bytecode de versión mayor 69 comprobado por `verifyPluginJar` | Pendiente |
| Paper | 26.2 build 129 (`paper-api 26.2.build.129-stable`) | Compilación y 1576 pruebas contra esa API; análisis estático del servidor parcheado localmente (`javap`) | Pendiente |
| MythicMobs Premium | 5.13.1-SNAPSHOT (build `530971de`, `plugin.yml` del JAR suministrado) | Compilación y pruebas con dobles de su API; análisis estático (`javap`) | Pendiente |
| Modelo de Jev | `jev-1.13.0` | Cuenta real, 2026-09-29: 20 de 20 comprobaciones `READY` y 1 200 de 1 200 decisiones respondidas por `jev-1.13.0` ([comprobación](../DocumentacionParaDesarrollar/Implementacion-0.0.1/evidencia/live-check-2026-09-29.md), [banco](../DocumentacionParaDesarrollar/Implementacion-0.0.1/evidencia/live-scenario-bench-2026-09-29.md)) | No aplica (desde la máquina de desarrollo; falta desde el host) |
| API de TypeSafe | OpenAPI `0.2.0` | Comparación del contrato en M08 (`ContractFixturesTest`) y llamadas reales del 2026-09-29 | No aplica |

## 2. Instalación y primer inicio

1. Copia `ThePredict-0.0.1.jar` junto a MythicMobs Premium en `plugins/` y arranca el servidor.
2. El primer inicio crea solo los archivos que faltan y **nunca reemplaza uno existente**:
   - `plugins/MythicMobs/AIConfig/config.yml`: esquema 2, clave vacía y el modelo probado `jev-1.13.0`.
   - `plugins/MythicMobs/AIConfig/Profiles/README.txt` y `Profiles/example.yml.example`: la plantilla de perfil, ignorada hasta copiarla a un `.yml` o `.yaml`.
   - `plugins/ThePredict/examples/`: el boss de ejemplo (mob, tres skills y perfil), inactivo; `examples/README.txt` explica cómo probarlo. ThePredict no escribe nada más dentro de la carpeta de MythicMobs.
3. Una instalación nueva no es un error. La consola informa, cada caso con su paso siguiente:
   - `Jev not configured: set TYPESAFE_API_KEY or jev.apiKey. MythicMobs works normally. Next: set the key and restart the server, then run /thepredict status.`
   - `No profiles: copy examples/ or create a .yml file in plugins/MythicMobs/AIConfig/Profiles/. Next: read plugins/MythicMobs/AIConfig/Profiles/README.txt.`
   - Con perfiles leídos pero ningún mob con `Predictor: true`: `<n> profiles read, but no mob targeted by a profile has Predictor: true. Next: set Predictor: true on the MythicMobs mob and run /mm reload.`
   - La línea del catálogo, por ejemplo `Profile catalog SETUP_PENDING (revision N, 0 profiles read, 0 active, 0 errors, 0 warnings): configuration pending; copy AIConfig/Profiles/example.yml.example to a .yml file`.
4. No se lanza ninguna inferencia de prueba. Con clave, ThePredict comprueba el servicio con `GET /v1/models` (sin tokens) y la consola dice `Checking Jev without spending tokens; the result follows in the console.` y después, por ejemplo, `Jev check: READY (credential valid; model unverified until first inference). Decisions are enabled. The check reads the key and the model list; it does not verify an inference or its quality.`
5. Un administrador (permiso `thepredict.admin`) que entra recibe un aviso si algo necesita atención: `[ThePredict] <causas>. Run /thepredict status for details.`, donde las causas son `plugins/MythicMobs/AIConfig/config.yml has errors`, `old configuration files are ignored`, `some profiles have errors`, `a migration is pending` o `Jev is <ESTADO>` (también `UNCONFIGURED` y `CHECKING`, que son informativos).
6. **`plugins/MythicMobs/AIConfig/config.yml` y la clave se leen solo al arrancar.** Tras cambiarlos, reinicia el servidor. Un valor desconocido o fuera de rango deja ThePredict inactivo con archivo, ruta y motivo (`Invalid configuration: AIConfig/config.yml: <ruta>: <motivo>. ThePredict stays inactive and /thepredict status still works. Next: fix plugins/MythicMobs/AIConfig/config.yml and restart the server, then run /thepredict status.`); MythicMobs sigue funcionando. Con el maestro inválido tampoco se abre el almacenamiento: no se crean `records/` ni `aggregates.db`. `/mm reload` vuelve a leer `Profiles/`, no el maestro.

(Todo lo anterior está probado en local; el arranque real está **pendiente de servidor**.)

### 2.1 Ubicación de la configuración y migración

La configuración que edita una persona vive en la carpeta de datos de MythicMobs, no en la de ThePredict. ThePredict la crea solo si falta y nunca sobrescribe un archivo existente. No escribe nada más dentro de `plugins/MythicMobs/`.

**Qué vive dónde**

| Qué | Dónde |
|---|---|
| Maestro (`config.yml`) | `plugins/MythicMobs/AIConfig/config.yml` |
| Perfiles (`.yml` y `.yaml`) | `plugins/MythicMobs/AIConfig/Profiles/` |
| Plantilla y su README | `Profiles/README.txt` y `Profiles/example.yml.example` (no se cargan hasta copiar la plantilla a un `.yml` o `.yaml`) |
| Boss de ejemplo | `plugins/ThePredict/examples/` |
| Registros JSONL, copias, export y el lock | `plugins/ThePredict/records/` (`backups/`, `exports/` y `.lock` siguen dentro) |
| Base de agregados | `plugins/ThePredict/aggregates.db`, al lado de `records/`, no dentro |
| Veredictos de las puertas | `plugins/ThePredict/records/eval/verdicts/` (dentro de `records.directory`), un archivo `<huella>-<etapa>.json` por etapa; con SQLite, también la tabla `m22_gate_verdicts` |
| Duelos, escenarios, propuestas, aprendizaje y autoconfiguración | `plugins/ThePredict/duels/`, `scenarios/`, `migration/`, `learn/`, `autoconfig/` |

MythicMobs, según el enum `DataFolder` leído con `javap`, no lista `AIConfig`. Que la ignore al cargar y con `/mm reload` es DT-100 **(pendiente de servidor, sesión AIConfig)**.

**Migración desde la ubicación antigua** (`plugins/ThePredict/config.yml` y `plugins/ThePredict/AI/`). ThePredict no mueve ni borra esos archivos.

- Solo la ubicación antigua tiene contenido (el maestro, o al menos un `.yml` o `.yaml` en `AI/`) y `AIConfig/` no tiene `config.yml` ni un perfil: ThePredict no carga la ubicación antigua, no activa consultas y no crea el maestro nuevo (así el siguiente arranque no pasa a "las dos ubicaciones"). Escribe una propuesta en `plugins/ThePredict/migration/<AAAAMMDD-HHMMSS>/`: `AIConfig/config.yml.proposed`, `AIConfig/Profiles/<archivo>.proposed` (conserva `.yml` o `.yaml`) y `report.md`. La consola dice `Migration proposal: review <informe> and the .proposed files next to it, then copy them into plugins/MythicMobs/AIConfig/ by hand. No file was changed and no query was enabled.` `status` muestra `Migration pending: review …` y, al entrar, `a migration is pending`. La misma propuesta se reutiliza en cada arranque mientras los archivos no cambien.
- Las dos ubicaciones tienen contenido: manda `AIConfig/`. No hay propuesta de traslado. La consola, `status` (línea `Location`) y el aviso al entrar dicen `Old files in plugins/ThePredict/config.yml and plugins/ThePredict/AI/ are ignored because plugins/MythicMobs/AIConfig/ already has configuration. Nothing was moved or deleted.` Esas dos rutas son la ubicación antigua.
- Solo la ubicación nueva tiene contenido: no hay aviso de ubicación.
- Si los archivos de la ubicación antigua además son del esquema 1, la propuesta es una sola, ya convertida al esquema actual y con las rutas nuevas. Un esquema 1 que ya está en `AIConfig/` sigue proponiendo la conversión y no activa consultas (sección 9). `README.txt` y `example.yml.example` no cuentan como contenido.

## 3. Clave de Jev

- **Recomendado:** la variable de entorno `TYPESAFE_API_KEY` en el proceso del servidor. Tiene prioridad sobre `jev.apiKey`.
- **Alternativa:** `jev.apiKey` en `config.yml`, si no puedes definir la variable.
- `jev.apiKeyEnvironment` cambia el nombre de la variable (vacío: solo se usa `jev.apiKey`).
- **Tras poner o cambiar la clave, reinicia el servidor**: la clave se lee solo al arrancar; `/thepredict check` vuelve a comprobar con la clave ya cargada, no lee una nueva.
- **Nunca** pegues la clave en chats, capturas, informes o evidencias, ni la pases como argumento de un comando. ThePredict nunca la muestra: ni en la consola, ni en `status`, ni en los informes de migración. Si una evidencia se tiene que compartir, redacta `jev.apiKey` antes.

## 4. `config.yml` v2

Cada clave con tipo, unidad, default (tal como aparece en el `config.yml` incluido) y rango. Las unidades van en los nombres (`Millis`, `Ticks`, `Blocks`, `Micros`). Una clave desconocida o del esquema 1 es un error con su ruta. Los límites de `decision.*` son globales: ningún perfil los amplía.

### 4.1 `jev`

| Clave | Tipo | Unidad | Default | Rango y notas |
|---|---|---|---|---|
| `schemaVersion` | entero | — | `2` | Obligatorio; solo `2`. |
| `jev.baseUrl` | URL | — | `'https://api.typesafe.ai'` | `https`; `http` solo con un host local (`localhost`, `127.0.0.1` o `::1`). Sin usuario, consulta ni fragmento. |
| `jev.model` | texto | — | `'jev-1.13.0'` | ID versionado, 1 a 64 caracteres (letras, dígitos, `.`, `_`, `-`). Solo `jev-1.13.0` está probado. |
| `jev.apiKeyEnvironment` | texto | — | `'TYPESAFE_API_KEY'` | Vacío o un nombre de variable de entorno. |
| `jev.apiKey` | texto | — | `''` | Puede estar vacío. La variable de entorno tiene prioridad. |
| `jev.provider` | texto | — | `typesafe` | `typesafe` o `kev`. `kev` ignora `jev.apiKey` y `jev.apiKeyEnvironment`, no lee `TYPESAFE_API_KEY` y exige `jev.baseUrl` en `localhost` o `127.0.0.1`. |
| `jev.timeoutMillis` | entero | ms | `800` | 100 a 5000; además `< profileDefaults.maximumIntentDurationTicks × 50`. |
| `jev.connectTimeoutMillis` | entero | ms | `1500` | 100 a 10 000. |
| `jev.maxResponseBytes` | entero | bytes | `262144` | 16 384 a 4 194 304. |
| `jev.maxStateChars` | entero | caracteres | `12000` | 2000 a **45 000**. Tamaño máximo del estado de combate enviado. El cliente rechaza, sin enviarla, una solicitud cuyo estado más la pregunta más larga pase de 32 000 tokens estimados a 1,5 caracteres por token (48 000 caracteres); 45 000 deja sitio a la pregunta. La estimación está calibrada para texto en inglés. |

### 4.2 `decision` y `debug`

| Clave | Tipo | Unidad | Default | Rango y notas |
|---|---|---|---|---|
| `decision.maxRequestsPerMinute` | entero | solicitudes/min | `600` | 1 a 1200. La mitad del límite publicado de la cuenta (1 200/min). |
| `decision.maxRequestsPerMinutePerWorld` | entero | solicitudes/min | `300` | 1 a `maxRequestsPerMinute`. |
| `decision.maxInFlightRequests` | entero | solicitudes | `8` | 1 a 64. |
| `decision.maxInFlightRequestsPerWorld` | entero | solicitudes | `4` | 1 a `maxInFlightRequests`. |
| `decision.maxInputTokensPerMinute` | entero | tokens/min | `600000` | 1000 a 10 000 000. **Es el límite que manda con muchos mobs** (sección 7). |
| `decision.encounterHeartbeatTicks` | entero | ticks | `20` | 10 a 200; `>= profileDefaults.minimumDecisionIntervalTicks`. Latido de decisión de cada encuentro. |
| `decision.contingenciesPerMob` | entero | preguntas | `1` | 0 a 2. Preguntas extra por mob para situaciones previstas. |
| `decision.minimumConfidence` | decimal | — | `0.45` | 0 a 0,99. Default de `Timing.MinimumConfidence` de los perfiles. **0,45 desde el re-banco de M16.10** (antes 0,5), elegido con la regla de M16.7 sobre las 60 etiquetas revisadas (sección 7.2). |
| `decision.tacticSwitchMargin` | decimal | — | `0.15` | 0 a 0,99. Default de `Timing.SwitchMargin`. |
| `decision.maxInstallsPerTick` | entero | mobs/tick | `10` | 1 a 100. Instalaciones de metas por tick. |
| `decision.maxEncounterEvaluationsPerTick` | entero | encuentros/tick | `2` | 1 a 64. |
| `decision.maxProbesPerTick` | entero | sesiones/tick | `64` | 8 a 1024. Si `tick_work` p95 se acerca a 2 ms, bajarlo. |
| `decision.maxCompletionsPerTick` | entero | respuestas/tick | `16` | 1 a 256. |
| `decision.maxSignalsPerTick` | entero | señales/tick | `64` | 1 a 1024. Marcas de `predictordecide` atendidas por tick. |
| `decision.maxTickWorkMicros` | entero | µs | `2000` | 100 a 20 000. Presupuesto de trabajo de ThePredict por tick. |
| `decision.maxDecisionAgeMillis` | entero | ms | no aparece en `config.yml`: `jev.timeoutMillis` + 200 (1000), como mucho 5000 | 100 a 5000; `>= jev.timeoutMillis`. Edad máxima de una respuesta aplicable. |
| `decision.lowConfidenceStreakForLocal` | entero | respuestas | `3` | 1 a 20. Respuestas seguidas con confianza baja antes de pasar a `LOCAL`. |
| `decision.searchRadiusBlocks` | decimal | bloques | `48.0` | 8 a 128. Tope de seguridad sobre el `FollowRange` de cada mob. |
| `decision.logDecisions` | booleano | — | `false` | Registra cada decisión, caída, resultado de cast, cambio de amenaza y ventana de resultado cerrada (`Outcome …`, sección 6.5) (como mucho 20 líneas cada 10 s). |
| `decision.maxOptionsPerQuestion` | entero | opciones | `8` | 2 a 32. Tope de opciones de una pregunta **después** de fusionar tácticas idénticas; la táctica vigente del mob nunca se corta. Un perfil no lo amplía. El ejemplo tiene 8 tácticas, así que el default no corta nada en él. |
| `decision.questionDetail` | texto | — | `full` | `compact`, `standard` o `full` (sin distinguir mayúsculas). Cuánto dice cada opción a Jev y cuánto cuesta (sección 7.1). Default de `Timing.QuestionDetail` de los perfiles; **`full` desde el re-banco de M16.10** (antes `compact`, que sigue disponible y cuesta la mitad). Nunca amplía los límites de tokens. |
| `decision.fitChecks` | booleano | — | `false` | Default de `Timing.FitChecks`: una pregunta `noul` de idoneidad por opción en la misma solicitud. **Solo se envía con `questionDetail: full`**; con otro nivel se ignora y la carga del perfil avisa. No puede ser `true` con `fitChecksAllowed: false`. |
| `decision.fitChecksAllowed` | booleano | — | `true` | `false` prohíbe la idoneidad en todos los perfiles: un perfil con `Timing.FitChecks: true` da error. |
| `decision.fitVeto` | decimal | probabilidad | `0.2` | 0 a 1 y menor que `fitRescue`. Una opción cuya idoneidad queda por debajo se veta. Provisional: el re-banco (M16.10) lo calibra con la regla de M16.7. |
| `decision.fitRescue` | decimal | probabilidad | `0.7` | 0 a 1 y mayor que `fitVeto`. Idoneidad con la que una sola opción rescata una respuesta de confianza baja. Provisional, como `fitVeto`. |
| `decision.castMode` | texto | — | `any_ready` | `any_ready` o `intent_only`. Con `any_ready`, cada skill con `Intention` válida sale en el primer tick en que está lista. Un perfil puede excluirse con `Timing.CastMode: intent_only`: el perfil gana (no es un modo menor). |
| `decision.castThread` | texto | — | `same_tick` | `same_tick` o `mythic_default`. Global: no es clave de perfil. `same_tick` ejecuta el `delay` de la skill en el hilo del servidor. `mythic_default` conserva el hilo de la 0.0.1 (`sync` falso). Solo lo lee la ejecución. La diferencia en juego está pendiente de servidor (sesión 19.9; DT-48 se registra con M20). |
| `decision.untypedCast` | texto | — | `tactic_only` | `tactic_only` o `any_ready`. Una skill sin `Intention` válida y sin `Purpose` interno solo sale como choice de su táctica. Decidido por el usuario el 2026-10-01. |
| `decision.readyHorizonTicks` | entero | ticks | `0` | 0 a 100. Con 0 la pregunta ve el mismo conjunto de hoy. El planificador local no lo lee. Subirlo a 20 queda pendiente de E2, no medida. |
| `decision.maxReadyChecksPerTick` | entero | sesiones/tick | — | **Obsoleta.** No viene en el `config.yml` entregado. Si un `config.yml` antiguo la trae, se acepta, sale una línea INFO al arrancar y no cambia nada: el tope es `decision.tickLayer.maxSessionsPerTick`. Se puede borrar. |
| `decision.pruning` | texto | — | `"off"` | `off` conserva el orden de la pregunta de hoy. `safe` quita opciones que no pueden servir. El default de entrega es `off`. Activar `safe` queda pendiente de E1, no medida. |
| `decision.tickLayer.mode` | texto | — | `reactions` | `casts`, `reactions` o `full`. `off` es un error: removed; use casts. Un perfil no puede pedir un modo mayor que este: se avisa y se usa el menor. |
| `decision.tickLayer.maxSessionsPerTick` | entero | sesiones/tick | `96` | 8 a 1024. Visitas de la capa por tick. Sustituye a `decision.maxReadyChecksPerTick`, que se acepta con aviso y no cambia este tope. |
| `decision.tickLayer.maxMicros` | entero | µs | `600` | 100 a 5000. Presupuesto de la capa dentro del tick. |
| `decision.tickLayer.guardMspt` | entero | ms | `0` | 0, o 20 a 50. Con 0 no hay guarda de MSPT. |
| `decision.tickLayer.microMinHoldTicks` | entero | ticks | `4` | 1 a 40. Permanencia mínima de un micro-comportamiento. |
| `decision.tickLayer.waypointRefreshTicks` | entero | ticks | `5` | 2 a 40. |
| `decision.tickLayer.maxWaypointPathsPerTick` | entero | rutas/tick | `8` | 0 a 64. |
| `decision.tickLayer.maxWaypointPathMicrosPerTick` | entero | µs | `400` | 0 a 5000. |
| `decision.candidateScoring` | texto | — | `priority` | `priority`, `utility` o `learned`. `learned` solo actúa con un artefacto activo. |
| `decision.confidenceGate` | texto | — | `raw` | `raw` o `calibrated`. El default `raw` no cambia el umbral de 0,45. |
| `decision.minimumCalibratedConfidence` | texto | — | `parity` | `parity` o un número. `parity` pide el mismo corte que el crudo a 0,45. |
| `decision.candidateDiversity` | booleano | — | `false` | Sin efecto en esta versión (sección 4.13). El motor no ordena las candidatas por movimiento. |
| `debug.recordScenarios` | booleano | — | `false` | Graba decisiones como escenarios anónimos (sección 8). Déjalo apagado en producción. |
| `debug.seed` | entero | — | `0` | Semilla base de `StableRandom`. La misma semilla y el mismo tick dan el mismo número. |

### 4.3 `profileDefaults` (heredados por cada perfil que no los fija)

| Clave | Tipo | Unidad | Default | Rango y notas |
|---|---|---|---|---|
| `profileDefaults.observationIntervalTicks` | entero | ticks | `2` | 1 a 20. Intervalo de las sondas baratas de cada sesión. |
| `profileDefaults.minimumDecisionIntervalTicks` | entero | ticks | `20` | 10 a 200. Permanencia mínima de una táctica adoptada. |
| `profileDefaults.maximumIntentDurationTicks` | entero | ticks | `40` | 20 a 400; `>= minimumDecisionIntervalTicks`. Vigencia de una intención. |
| `profileDefaults.criticalHealthRatio` | decimal | proporción | `0.3` | `> 0` y `< woundedHealthRatio`. |
| `profileDefaults.woundedHealthRatio` | decimal | proporción | `0.6` | `> criticalHealthRatio` y `< 1`. |
| `profileDefaults.meleeReachBlocks` | decimal | bloques | `2.5` | `> 0`. |
| `profileDefaults.bandInnerBlocks` | decimal | bloques | `5.0` | `> meleeReachBlocks`. |
| `profileDefaults.bandOuterBlocks` | decimal | bloques | `9.0` | `> bandInnerBlocks`. |
| `profileDefaults.rangedReachBlocks` | decimal | bloques | `16.0` | `>= bandOuterBlocks`. |
| `profileDefaults.retreatDistanceBlocks` | decimal | bloques | `10.0` | `> 0`. |
| `profileDefaults.retreatSpeed` | decimal | multiplicador | `1.2` | 0,1 a 3. |
| `profileDefaults.approachSpeed` | decimal | multiplicador | `1.15` | 0,1 a 3. |

Validaciones cruzadas: `encounterHeartbeatTicks >= minimumDecisionIntervalTicks`; `maximumIntentDurationTicks >= minimumDecisionIntervalTicks`; `timeoutMillis < maximumIntentDurationTicks × 50`, para que una respuesta pueda llegar antes de que venza la intención; `maxDecisionAgeMillis >= timeoutMillis`.

**Defaults.** Ningún default cambió con la evidencia de M13: los valores locales y de la cuenta real los respaldan o no bastan para cambiarlos. Los defaults definitivos de latido, `timeoutMillis`, límites y umbrales están **pendientes de medición en servidor** (guion de M13, §5 y §6).

### 4.4 `participants` (quién puede ser participante u objetivo)

Sección opcional (M17). **Sin ella, o con los valores del `config.yml` incluido, todo es como en la 0.0.1**: los mobs solo perciben, apuntan y combaten jugadores en `SURVIVAL` o `ADVENTURE`, y las solicitudes a Jev son idénticas byte a byte. Admitir otras entidades vivas sirve, por ejemplo, para un duelo entre mobs de ThePredict (dos facciones) o para mobs que cazan monstruos.

| Clave | Tipo | Unidad | Default | Rango y notas |
|---|---|---|---|---|
| `participants.playerGameModes` | lista | modos | `[SURVIVAL, ADVENTURE]` | `SURVIVAL`, `ADVENTURE`, `CREATIVE` o `SPECTATOR`. Los jugadores en estos modos pueden ser percibidos, objetivo y combatidos. `[]`: ningún jugador. Un perfil solo puede restringirla (`Participants.PlayerGameModes`). |
| `participants.entities.allow` | lista | selectores | `[]` | Hasta 16 selectores de entidades vivas que no son jugadores. Vacía: ninguna (comportamiento de 0.0.1). Los perfiles suman los suyos (`Participants.Entities.Allow`). |
| `participants.entities.deny` | lista | selectores | `[]` | Hasta 16 selectores que **nunca** participan, aunque un perfil los admita. Los perfiles suman los suyos. |
| `participants.entities.profilesMayAllow` | booleano | — | `true` | Con `false`, los `Allow` de los perfiles se ignoran con un aviso al cargar (`is ignored: participants.entities.profilesMayAllow is false`): solo cuenta la lista global. |
| `participants.entities.perceiveNonTargets` | booleano | — | `false` | Con `false`, de las entidades solo se percibe el **objetivo** de cada mob (sin ninguna consulta extra). Con `true`, cada mob hace una consulta de entidades vivas cercanas (`getNearbyLivingEntities`) en lugar de la de jugadores y percibe también las admitidas cercanas. Un perfil solo puede activarlo si esta clave es `true`. |
| `participants.entities.maxCandidatesPerMember` | entero | entidades | `8` | 1 a 16. Con `perceiveNonTargets`, las entidades no jugadoras más cercanas que se leen por mob y captura. El tope de 16 participantes por captura y por encuentro vale para jugadores y entidades juntos. |

Dificultad por tiempo objetivo (M22). Sin esta sección el modo es `auto` y el preset es `normal`: contra jugadores el controlador busca la duración del preset y contra entidades el mob juega a plena potencia. `difficulty.worlds.<mundo>.preset` y `.entropy` imponen un mundo; el nombre del mundo no se lista de antemano. Los jugadores no eligen la dificultad.

Fuera de un mundo impuesto, el preset y Entropía los elige el autor en el archivo de MythicMobs de cada mob con `Predictor: true`: `PredictorDifficulty` (`Fácil`, `Normal`, `Difícil`, `Caos` o `Vacío`; sin la clave, `difficulty.defaultPreset`) y `PredictorEntropy` (`true` o `false`; sin la clave, `false`). Entropía juega siempre a la presión máxima y baja la reacción hasta `entropyReactionFloorTicks` del preset. Un valor inválido es un error del catálogo con el nombre del mob (`<Mob>.PredictorDifficulty: must be one of Fácil, Normal, Difícil, Caos, Vacío` o `<Mob>.PredictorEntropy: must be true or false`) y el mob queda sin vincular. Ver la guía del autor, sección 2.

| Clave | Tipo | Unidad | Default | Rango y notas |
|---|---|---|---|---|
| `difficulty.defaultMode` | texto | — | `auto` | `auto`, `bench` o `player`. `auto` controla contra jugadores y usa el banco contra entidades. |
| `difficulty.defaultPreset` | texto | — | `normal` | `facil`, `normal`, `dificil`, `caos` o `vacio`. |
| `difficulty.adjustIntervalTicks` | entero | ticks | `100` | 40 a 600. Separación del lazo rápido, repartida entre sesiones. |
| `difficulty.toleranceRatio` | decimal | fracción | `0.20` | 0,05 a 0,50. Banda del lazo rápido. |
| `difficulty.deadbandRatio` | decimal | fracción | `0.10` | 0 a 0,50. Banda del lazo lento. |
| `difficulty.learningGain` | decimal | — | `0.25` | 0,05 a 0,50. Antes del recorte `g_eff = min(ganancia, k_min/k_max)`. |
| `difficulty.projectedTtkCapRatio` | decimal | veces T | `4` | 2 a 10. Tope de la proyección. |
| `difficulty.dampingReversals` | entero | peleas | `4` | 3 a 9. Con 4 cambios de signo en diez peleas se parte la ganancia. Con 3 no. |
| `difficulty.estimatorHalfLifeTicks` | entero | ticks | `100` | 20 a 600. Semivida de referencia a 45 s; se escala con T. |
| `difficulty.minEvidenceTicks` | entero | ticks | `60` | 20 a 400. Antes de esto la estimación no cuenta. |
| `difficulty.disengageTicks` | entero | ticks | `200` | 40 a 2400. Sin daño `dealt` la pelea se cierra. |
| `difficulty.maxTrackedFights` | entero | relojes | `4` | 1 a 16. Sin efecto en esta versión (sección 4.13). |
| `difficulty.nativeLevelAllowed` | booleano | — | `false` | Con `true` un perfil `NativeLevel: spawn` puede mover ±1 el nivel del siguiente spawn, dentro de `[Level−2, Level+2]`. Nunca durante la pelea. |
| `difficulty.presets.facil.targetMinutes` | entero | minutos | `45` | 1 a 60. T del preset fácil si nadie fija segundos. |
| `difficulty.presets.facil.reactionFloorTicks` | entero | ticks | `5` | 0 a 20. Suelo de reacción contra jugadores. |
| `difficulty.presets.facil.entropyReactionFloorTicks` | entero | ticks | `4` | 0 a 5. Suelo de `r(t)` con Entropía. |
| `difficulty.presets.facil.entropyAdaptStartSeconds` | decimal | segundos | `7` | 0 a 30. `c(0)` de Entropía. |
| `difficulty.presets.normal.targetMinutes` | entero | minutos | `30` | 1 a 60. |
| `difficulty.presets.normal.reactionFloorTicks` | entero | ticks | `5` | 0 a 20. |
| `difficulty.presets.normal.entropyReactionFloorTicks` | entero | ticks | `3` | 0 a 5. |
| `difficulty.presets.normal.entropyAdaptStartSeconds` | decimal | segundos | `5` | 0 a 30. |
| `difficulty.presets.dificil.targetMinutes` | entero | minutos | `15` | 1 a 60. |
| `difficulty.presets.dificil.reactionFloorTicks` | entero | ticks | `3` | 0 a 20. |
| `difficulty.presets.dificil.entropyReactionFloorTicks` | entero | ticks | `0` | 0 a 5. |
| `difficulty.presets.dificil.entropyAdaptStartSeconds` | decimal | segundos | `3.5` | 0 a 30. |
| `difficulty.presets.caos.targetMinutes` | entero | minutos | `10` | 1 a 60. |
| `difficulty.presets.caos.reactionFloorTicks` | entero | ticks | `1` | 0 a 20. |
| `difficulty.presets.caos.entropyReactionFloorTicks` | entero | ticks | `0` | 0 a 5. |
| `difficulty.presets.caos.entropyAdaptStartSeconds` | decimal | segundos | `1.5` | 0 a 30. |
| `difficulty.presets.vacio.targetMinutes` | entero | minutos | `5` | 1 a 60. |
| `difficulty.presets.vacio.reactionFloorTicks` | entero | ticks | `0` | 0 a 20. |
| `difficulty.presets.vacio.entropyReactionFloorTicks` | entero | ticks | `0` | 0 a 5. |
| `difficulty.presets.vacio.entropyAdaptStartSeconds` | decimal | segundos | `0` | 0 a 30. |

`/thepredict eval status`, `/thepredict eval difficulty <nearest|uuid>`, `/thepredict eval report [tag] [since] [md|jsonl|html]`, `/thepredict eval verdict import <archivo>`, `/thepredict eval verdict list` y `/thepredict eval pressure <nearest|uuid> <0-8|auto>` leen o anotan la dificultad. **`thepredict.eval` tiene `default: op`**. `pressure` exige además `thepredict.debug` y no se guarda. `report` no se genera en el servidor en esta entrega: responde `eval report is not available on the server in this release; run ./gradlew evalReport`. La sesión 22 y las evaluaciones de pago no están verificadas en servidor.

**Veredictos de las puertas.** `eval verdict import <archivo>` valida el archivo (huella de 64 hexadecimales, etapa `G0` a `G4`, veredicto `PASS`, `PASS_SIM_ONLY`, `FAIL` o `INCONCLUSIVE`) y lo copia a `records/eval/verdicts/<huella>-<etapa>.json`; con SQLite, también a `m22_gate_verdicts`. `<archivo>` es una ruta dentro de la carpeta del plugin (`plugins/ThePredict/`): relativa a ella, nunca a la carpeta desde la que arranca el servidor, o absoluta dentro de ella. Una ruta con `..` se rechaza con `path`; una ruta absoluta fuera de la carpeta, o un enlace que lleva fuera, con `path: must stay inside the plugin folder`, y no se importa nada. Cada etapa tiene su archivo, así que `G1` y `G2` de una misma huella conviven también sin SQLite. Tras importar, el escritor vuelve a leer todos los veredictos y los modos que esperan uno (memoria, patrones y calibrador) se vuelven a ofrecer sin reiniciar. Por huella y etapa manda el veredicto con el `decided_epoch_ms` más reciente. Con `records.level: off` y `storage.aggregates: off` el escritor arranca solo para los veredictos: los lee al iniciar, acepta `eval verdict import` y `status` lo dice en la línea `State` (`DISABLED VERDICTS_ONLY`). `eval verdict list` muestra los veredictos cargados, una línea por huella y etapa: los 12 primeros hexadecimales, la etapa y el veredicto; como mucho 8 líneas, y la última dice cuántos faltan. Antes de la primera carga responde `pending: the verdicts have not loaded yet` y, sin ninguno, `no verdict loaded`.

Registros locales y agregados (M21). El JSONL vive en `records/` y la base SQLite queda al lado, no dentro. `mysql` está reservado y el archivo no arranca.

| Clave | Tipo | Unidad | Default | Rango y notas |
|---|---|---|---|---|
| `decision.answerCache` | texto | — | `"off"` | `off` (entregado), `shadow` o `on`. `on` no se cruza en esta entrega: el gate M21.7 sigue pendiente. |
| `decision.answerCacheTtlTicks` | entero | ticks | `100` | 20 a 600. Validez de una respuesta guardada. |
| `decision.answerCacheEntries` | entero | entradas | `2048` | 64 a 65536. Tope de entradas; también hay un tope de 8 000 000 caracteres. |
| `decision.answerCacheRevalidateRatio` | decimal | fracción | `0.1` | 0 a 1. Fracción de aciertos que además se envían. |
| `records.level` | texto | — | `decisions` | `off`, `decisions` o `full`. `decisions` incluye la línea `telegraph`. `full` añade casts, amenaza, reacciones, micro y la línea `stance`. |
| `records.bodies` | texto | — | `hash` | `none`, `hash`, `dedup` o `all`. |
| `records.directory` | texto | carpeta | `records` | Relativa a la carpeta del plugin. Rechaza `..` y una ruta absoluta. |
| `records.queueCapacity` | entero | eventos | `16384` | 1024 a 131072. |
| `records.segmentMegabytes` | entero | MiB | `64` | 8 a 512. Rotación por tamaño. |
| `records.segmentMinutes` | entero | minutos | `60` | 5 a 1440. Rotación por edad. |
| `records.compressClosedSegments` | booleano | — | `true` | Un segmento cerrado se comprime en gzip de 1 MiB y el original se borra solo si el `.gz` cierra bien. |
| `records.retentionDays` | entero | días | `14` | 1 a 365. Nunca borra el día en curso. |
| `records.maxTotalMegabytes` | entero | MiB | `2048` | 64 a 65536. Si solo el día en curso lo supera, los eventos se pausan y los agregados siguen. |
| `storage.aggregates` | texto | — | `sqlite` | `off` o `sqlite`. `mysql` está reservado y es un error. |
| `storage.file` | texto | archivo | `aggregates.db` | Nombre de archivo, sin barras, junto a `records/`. |
| `storage.synchronous` | texto | — | `normal` | `normal` o `full`. |
| `storage.busyTimeoutMillis` | entero | ms | `2000` | 0 a 30000. |
| `storage.flushIntervalTicks` | entero | ticks | `100` | 20 a 1200. |
| `storage.maxPendingKeys` | entero | claves | `4096` | 256 a 65536. |
| `storage.backupEveryHours` | entero | horas | `24` | 0, o 1 a 168. 0 no copia. |
| `storage.backupsKept` | entero | copias | `7` | 1 a 60. |
| `telegraph.againstPlayers` | texto | — | `enforce` | `enforce` u `off`. En Entropía el mínimo no se aplica. |
| `telegraph.againstEntities` | texto | — | `off` | `off` o `enforce`. |
| `telegraph.minTicks.light` | entero | ticks | `0` | 0 a 20. |
| `telegraph.minTicks.medium` | entero | ticks | `8` | Desde `light` hasta 30. |
| `telegraph.minTicks.heavy` | entero | ticks | `16` | Desde `medium` y al menos 10, hasta 40. |
| `telegraph.minTicks.severe` | entero | ticks | `24` | Desde `heavy` hasta 60. |
| `telegraph.answerTicks.block` | entero | ticks | `0` | 0 a 10. |
| `telegraph.answerTicks.dodge` | entero | ticks | `0` | 0 a 10. |
| `telegraph.answerTicks.backOff` | entero | ticks | `3` | 0 a 10. |
| `telegraph.answerTicks.breakSight` | entero | ticks | `3` | 0 a 10. |
| `telegraph.answerTicks.interrupt` | entero | ticks | `3` | 0 a 10. |
| `telegraph.deliveryMarginTicks` | entero | ticks | `1` | 0 a 4. |
| `telegraph.maxReleasesPerTick` | entero | avisos | `32` | 4 a 256. |
| `telegraph.whiffRecoveryTicks` | entero | ticks | `10` | 0 a 40. Recuperación global tras un fallo. |
| `telegraph.whiffCooldownTicks` | entero | ticks | `40` | 0 a 400. |
| `telegraph.resultWindowTicks` | entero | ticks | `10` | 2 a 60. Con proyectil, 40. |
| `stance.nativeMode` | texto | — | `hybrid` | `hybrid`, `read`, `write` u `off`. |
| `stance.minDwellTicks` | entero | ticks | `20` | 4 a 200. |
| `stance.hysteresisCycles` | entero | ciclos | `2` | 1 a 7. |
| `zone.mode` | texto | — | `observe` | `off`, `observe`, `guard` o `full`. El modo efectivo de un perfil es el mínimo con `Zone.Mode`. |
| `zone.defaultRadiusBlocks` | entero | bloques | `24` | 8 hasta `zone.maxRadiusBlocks`. |
| `zone.maxRadiusBlocks` | entero | bloques | `48` | 16 a 64. |
| `zone.bandBlocks` | entero | bloques | `12` | 6 a 24. Banda vertical. |
| `zone.maxZones` | entero | zonas | `64` | 1 a 256. Tope A5. |
| `zone.maxSnapshotsPerTick` | entero | chunks | `4` | 1 a 32. Tope A5. |
| `zone.maxConcurrentBuilds` | entero | construcciones | `2` | 1 a 8. Tope A5. |
| `zone.maxDropScanBlocks` | entero | bloques | `64` | 8 a 384. |
| `zone.maxMicrosPerTick` | entero | µs | `250` | 50 a 2000. Tope A5 del parche. |
| `zone.maxColumnReadsPerTick` | entero | columnas | `16` | 0 a 256. Tope A5. |
| `zone.minColumnRefreshTicks` | entero | ticks | `4` | 1 a 40. |
| `zone.maxDirtyQueue` | entero | columnas | `256` | 16 a 4096. Tope A5. |
| `zone.dirtyRebuildThreshold` | entero | columnas | `64` | 8 hasta la cola. |
| `zone.verifyIntervalTicks` | entero | ticks | `200` | 40 a 2400. |
| `zone.idleDropTicks` | entero | ticks | `600` | 100 a 6000. |
| `zone.maxQueriesPerTick` | entero | consultas | `32` | 4 a 256. Tope A5. |
| `zone.knockbackDepthBlocks` | entero | bloques | `2` | 1 a 6. |
| `zone.strongPushDepthBlocks` | entero | bloques | `7` | desde el knockback hasta 16. |
| `zone.maxSafeDropBlocks` | entero | bloques | `3` | 0 a 8. Un borde es una caída mayor que este valor. |
| `zone.factPeriodTicks` | entero | ticks | `10` | 2 a 40. |
| `zone.factOnTicks` | entero | ticks | `10` | 2 a 100. Histéresis de entrada. |
| `zone.factOffTicks` | entero | ticks | `40` | desde `factOnTicks` hasta 400. |
| `zone.maxFactsPerMob` | entero | hechos | `3` | 0 a 5. |
| `zone.pushDeferTicks` | entero | ticks | `10` | 2 a 40. |
| `zone.pushDeferMaxTicks` | entero | ticks | `60` | desde el aplazamiento hasta 400. |
| `zone.pushAttributionTicks` | entero | ticks | `60` | 20 a 200. |
| `zone.answerRetryTicks` | entero | ticks | `4` | 1 a 20. |
| `zone.answerMaxWaitTicks` | entero | ticks | `40` | desde el reintento hasta 200. |
| `group.mode` | texto | — | `fair` | `off`, `fair`, `coordinate` o `full`. El perfil solo puede bajarlo. El default no pasa a `coordinate` ni a `full`. |
| `group.maxSquads` | entero | escuadras | `4` | 1 a 8. Escuadras por encuentro, en orden: primero los nombres declarados y después las cabezas con hijos. Los miembros de una escuadra de más quedan en el resto, como un mob sin escuadra. |
| `group.brokenTicks` | entero | ticks | `100` | 20 a 600. |
| `group.arbitrationPeriodTicks` | entero | ticks | `4` | 1 a 20. |
| `group.replanPeriodTicks` | entero | ticks | `20` | 10 a 100. |
| `group.maxReplansPerTick` | entero | planes | `2` | 1 a 10. Tope A5. |
| `group.maxMicrosPerTick` | entero | µs | `150` | 50 a 1000. Tope A5. |
| `group.maxMeleeAttackersPerPlayer` | entero | peso | `2` | 1 a 4. No baja de 2 en A13 por esta clave sola: el suelo es constante. |
| `group.maxRangedAttackersPerPlayer` | entero | peso | `2` | 1 a 6. |
| `group.minImpactGapTicks` | entero | ticks | `6` | 4 a 20. El suelo de 4 es constante. |
| `group.tokenIdleTimeoutTicks` | entero | ticks | `30` | 10 a 100. |
| `group.stealMarginMillis` | entero | ms | `150` | 0 a 500. |
| `group.idleWarnTicks` | entero | ticks | `100` | 20 a 600. Sin efecto en esta versión (sección 4.13). |
| `group.roleSwitchMarginMillis` | entero | ms | `150` | 0 a 500. |
| `group.exitSectorDegrees` | entero | grados | `90` | 60 a 180. El suelo de 60 es constante. |
| `group.lkpForgetTicks` | entero | ticks | `200` | 20 a 1200. Mientras ningún miembro ve a su objetivo, el grupo puede ofrecer el plan `search_last_known` si alguno lo vio hace como mucho estos ticks. |
| `group.maxMediumTellsPerPlayer` | entero | avisos | `2` | 1 a 3. |
| `group.bigImpactSpacingTicks` | entero | ticks | `10` | 8 a 40. El suelo de 8 es constante. |
| `group.leaderReserveTicks` | entero | ticks | `10` | 0 a 40. |
| `group.projectileFlightMarginTicks` | entero | ticks | `2` | 0 a 5. |
| `group.maxPlanCandidates` | entero | planes | `4` | 3 a 4. |
| `group.minPlanTicks` | entero | ticks | `40` | 10 a 200. |
| `group.planHorizonTicks` | entero | ticks | `100` | desde `minPlanTicks` hasta 600. |
| `group.assignmentThreatMargin` | entero | puntos | `5` | 0 a 50. |
| `group.factOnTicks` | entero | ticks | `10` | 2 a 100. Sin efecto en esta versión (sección 4.13). |
| `group.factOffTicks` | entero | ticks | `40` | desde `factOnTicks` hasta 400. Sin efecto en esta versión (sección 4.13). |
| `group.maxFacts` | entero | hechos | `4` | 0 a 7. Sin efecto en esta versión (sección 4.13). Ningún hecho de grupo llega a Jev. |
| `records.patternSummaryBinary` | booleano | — | `false` | Sin efecto en esta versión (sección 4.13). El JSONL no lleva `summary_b64`. |
| `patterns.mode` | texto | — | `observe` | `off`, `observe`, `bias` o `full`. Sin veredicto se queda en `observe`. |
| `patterns.hearingBlocks` | entero | bloques | `16` | 4 a 32. Sin efecto en esta versión (sección 4.13). Una acción solo cuenta si un miembro la ve. |
| `patterns.sampleTicks` | entero | ticks | `2` | 1 a 10. |
| `patterns.maxWatchedPerEncounter` | entero | enemigos | `4` | 1 a 16. Sin efecto en esta versión (sección 4.13). |
| `patterns.maxReadsPerTick` | entero | lecturas | `16` | 1 a 64. Lecturas de movimiento de enemigos por tick en todo el servidor. Las que no caben esperan al tick siguiente y cuentan `PATTERN_READS_DEFERRED`. |
| `patterns.strafeBlocks` | decimal | bloques | `0.15` | 0,05 a 0,5. |
| `patterns.airSwings` | texto | — | `observe` | `observe` o `on`. `on` solo tras cerrar DT-75. |
| `patterns.mergeTicks` | entero | ticks | `6` | 0 a 20. |
| `patterns.idleTicks` | entero | ticks | `20` | 10 a 100. |
| `patterns.maxTokensPerSecond` | entero | tokens | `20` | 5 a 40. |
| `patterns.order2Contexts` | entero | contextos | `32` | 0 a 64. |
| `patterns.forgetting` | decimal | — | `0.98` | 0,90 a 1. |
| `patterns.evidenceForgetting` | decimal | — | `0.98` | 0,90 a 1. |
| `patterns.confidenceQuantile` | decimal | — | `0.05` | 0,01 a 0,20. |
| `patterns.minTrials` | entero | ensayos | `8` | 4 a 30. |
| `patterns.persistUpdates` | entero | updates | `3` | 1 a 10. |
| `patterns.exitMargin` | decimal | — | `0.05` | 0 a 0,2. |
| `patterns.changepointH` | decimal | — | `4.0` | 2 a 8. |
| `patterns.forecastMinAccuracy` | decimal | — | `0.4` | 0,2 a 0,9. |
| `patterns.histogramBinTicks` | entero | ticks | `2` | 1 a 4. |
| `patterns.maxMicrosPerTick` | entero | µs | `60` | 10 a 500. Tope A5. |
| `patterns.maxExploitShare` | decimal | cuota | `0.5` | 0 a 1. Parte de los casts de la ventana `exploitWindowTicks` que un patrón puede motivar contra un jugador. La escalera puede bajarla. |
| `patterns.maxCounterDelayTicks` | entero | ticks | `20` | 0 a 40. Sin efecto en esta versión (sección 4.13). |
| `patterns.exploitWindowTicks` | entero | ticks | `400` | 100 a 1200. |
| `patterns.restAfterSuccesses` | entero | aciertos | `3` | 1 a 10. |
| `patterns.restTicks` | entero | ticks | `300` | 0 a 2400. |
| `patterns.maxExploitedPatterns` | entero | patrones | `2` | 1 a 4. |
| `patterns.factOnTicks` | entero | ticks | `40` | 10 a 200. |
| `patterns.factOffTicks` | entero | ticks | `80` | 20 a 400. |
| `patterns.maxFactsPerParticipant` | entero | hechos | `2` | 0 a 3. |
| `patterns.maxFactsPerRequest` | entero | hechos | `4` | 0 a 6. Hechos de patrón de toda la solicitud: se suman los participantes en su orden, después del tope por participante. |

Con olvido λ = 0,98 la detección es del 92,5 % a 80/20 (mediana 24 ensayos), del 41,8 % a 70/30 y los falsos positivos son el 0,1 %. El default de `patterns.mode` sigue en `observe`. Sin veredicto, `bias` y `full` se quedan en `observe` y `status` nombra la causa `UNVERIFIED_PATTERN_MODE`. `inspect patterns` lee el modelo vivo del objetivo. Si no hay sesión, objetivo o modelo, no escribe una línea. El texto exacto en juego queda pendiente de la sesión 26.

`/thepredict zone` usa el mismo permiso que `inspect`: **`thepredict.admin`**. No hay rayo de cobertura.

**Selectores** (texto `prefijo:valor`). `config.yml` solo comprueba la forma (un prefijo desconocido o un valor mal formado es un error con archivo y ruta); los valores se resuelven al vincular los perfiles, en cada `/mm reload` (que vuelve a leer y vincular los perfiles de ThePredict, `ProfileCatalogs.onMythicReload`), y uno desconocido da un aviso con su ruta (`participants.entities.allow[2]`) y se ignora.

| Selector | Coincide si | API nativa (Paper 26.2 / MythicMobs 5.13.1) |
|---|---|---|
| `type:<clave>` | el tipo de la entidad es ese (`type:minecraft:zombie`; sin espacio de nombres, `minecraft`) | `Entity#getType()`, `Registry.ENTITY_TYPE` |
| `tag:<clave>` | el tipo está en la etiqueta de tipos, vanilla o de un datapack (`tag:minecraft:undead`) | `Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, …)`. Se lee al vincular: **tras `/minecraft:reload` ejecuta `/mm reload`** (DT-39, pendiente de servidor) |
| `category:<SpawnCategory>` | su categoría de aparición (`MONSTER`, `ANIMAL`, `WATER_ANIMAL`, `WATER_AMBIENT`, `WATER_UNDERGROUND_CREATURE`, `AMBIENT`, `AXOLOTL`, `MISC`) | `Entity#getSpawnCategory()` |
| `kind:<LIVING\|MOB\|ENEMY\|MONSTER\|ANIMAL>` | la entidad implementa esa interfaz (`ENEMY` incluye slime, ghast, phantom, hoglin y shulker, que no son `MONSTER`) | `LivingEntity`, `Mob`, `Enemy`, `Monster`, `Animals` |
| `mythic:*` o `mythic:<IdDeMob>` | es un mob de MythicMobs, o de ese tipo exacto (distingue mayúsculas) | `MobExecutor.getActiveMob(UUID)` |
| `faction:<nombre>` | su facción de MythicMobs, sin distinguir mayúsculas | `ActiveMob.getFaction()` |
| `team:<nombre>` | su equipo del scoreboard principal, sin distinguir mayúsculas (si dos equipos solo difieren en mayúsculas, aviso al vincular) | `Scoreboard#getEntityTeam(Entity)` |
| `tagged:<etiqueta>` | tiene esa etiqueta de `/tag` (exacta) | `Entity#getScoreboardTags()` |
| `predictor` | es un mob vinculado a ThePredict (su tipo tiene perfil con `Predictor: true`) | catálogo de ThePredict |

**Reglas fijas.** Un jugador solo depende de su modo de juego (ningún selector, `deny` ni bando le afecta); un soporte de armadura nunca participa; un `deny` gana a un `allow`; dos entidades del **mismo bando** (misma facción de MythicMobs o, si no, mismo equipo del scoreboard; nunca jugadores) nunca se enfrentan. Un encuentro es un bando: dos mobs de ThePredict que se enfrentan forman dos encuentros y envían dos solicitudes por latido, cada una con su punto de vista, dentro del presupuesto de siempre.

**Mobs de ThePredict sin facción ni equipo.** Un `allow` amplio (`kind:MONSTER`, `kind:MOB`, `predictor`) también admite a otros mobs de ThePredict. Si no tienen `Faction` ni equipo, son rivales: los que pelean contra el mismo jugador forman encuentros separados (más solicitudes) y, si su `AITargetSelectors` los elige, se atacan entre ellos. Dales una `Faction:` común en MythicMobs (y `- 1 nearestOtherFactionMonsters` en lugar de `- 1 monsters`) o añade su facción o equipo al `deny`.

**Costo.** Con `perceiveNonTargets: false` (default) no hay ninguna consulta extra: solo se añade el objetivo de cada mob. Con `true`, una consulta por mob (la misma que antes se hacía para jugadores, ahora de entidades vivas) y como mucho `maxCandidatesPerMember` lecturas extra por mob; medido en local con dobles (`./gradlew m13Measure`, 30 mobs, 2 jugadores y 60 zombis alrededor), una captura pasa de ≈ 59 µs (p50, política por defecto) a ≈ 71 µs admitiendo zombis como objetivos y a ≈ 244 µs percibiéndolos también como no objetivos. En el servidor: **pendiente de medición en servidor** (sesión 17.6).

Lo que Jev recibe de una entidad es su alias `entity_N` y su especie (`entity_1 is a zombie`); nunca su nombre, `Display`, ID de MythicMobs, facción ni UUID. `inspect` y el registro sí pueden mostrarla (`Target: entity_1 (zombie) …`), y el recuento `perceived players` de `inspect` cuenta todos los participantes. Detalles para autores: [guía del autor, 4.9](Guia-del-autor.md#49-participantes-y-objetivos-participants).

### Memoria por jugador

El modo entregado es `record`. En ese modo se guarda y no se decide. `seed` y `full` no pasan sin veredicto. `mysql` está reservado y la carga lo rechaza: `AIConfig/config.yml: memory.backend: is reserved and not available in this release`. `memory.refreshSeconds` se valida y no cambia nada con SQLite. `learning.allowUnverified` default `false`: si es `true`, un modo o un artefacto sin veredicto puede activarse y cada arranque avisa con el archivo y la ruta absoluta de `learn/active.json`. Un perfil `Memory.Mode` solo puede bajar el global. `Memory.Facts` default `true` y solo aporta frases con `full`. Una pelea usa la memoria solo si ya estaba `LOADED` o `READ_ONLY`. `NOT_LOADED` dura toda la pelea. El tick drena como mucho 16 pistas y deja el resto en la cola.

| Clave | Tipo | Unidad | Default | Qué hace |
|---|---|---|---|---|
| `memory.mode` | texto | — | `record` | `off`, `record`, `seed` o `full`. Sin veredicto se queda en `record`. |
| `memory.fightDecay` | decimal | — | `0.9` | Olvido por pelea, 0,5 a 1. |
| `memory.halfLifeDays` | entero | días | `30` | Vida media del olvido, 1 a 365. |
| `memory.crossFightH` | entero | — | `4` | Umbral del CUSUM entre peleas, 2 a 8. |
| `memory.styleBreakFactor` | decimal | — | `0.25` | Factor de ruptura, 0,05 a 0,5. |
| `memory.seedMaxTrials` | entero | ensayos | `6` | Tope de la semilla, 0 a 12. |
| `memory.minFightTrials` | entero | ensayos | `4` | Ensayos propios antes de `ACTIVE`, 1 a 8. |
| `memory.rungPriorFights` | entero | peleas | `3` | Peso del peldaño del perfil, 0 a 20. |
| `memory.maxRungOffset` | entero | peldaños | `3` | Recorte alrededor del perfil, 0 a 8. |
| `memory.minDamageShareRatio` | decimal | — | `0.2` | Parte del daño para contar una victoria, 0 a 1. |
| `memory.maxProfilesPerPlayer` | entero | perfiles | `8` | 1 a 12. El registro no pasa de 1 024 bytes. El códec mide 680 y 713 en los peores casos construidos. En local, con 1 a 8 perfiles por jugador, el p99 fue 200 bytes y el máximo 200 (cumple). El p99 de registros reales queda pendiente de servidor. |
| `memory.maxFactsPerRequest` | entero | hechos | `2` | 0 a 4. |
| `memory.factMaxAgeDays` | entero | días | `60` | Antigüedad máxima de una frase, 1 a 365. |
| `memory.cacheSubjects` | entero | jugadores | `512` | 64 a 8 192. |
| `memory.maxHintsPerTick` | entero | pistas | `16` | 1 a 64. |
| `memory.loadTimeoutSeconds` | entero | segundos | `10` | 2 a 60. |
| `memory.refreshSeconds` | entero | segundos | `60` | Se valida. No cambia nada con SQLite. |
| `memory.maxPendingDigestsPerSubject` | entero | digestos | `8` | 2 a 32. |
| `memory.maxPendingSubjects` | entero | jugadores | `1024` | 64 a 8 192. |
| `memory.maxSubjectsPerBatch` | entero | jugadores | `64` | 8 a 256. |
| `memory.retentionDays` | entero | días | `180` | 7 a 3 650. Borrado en trozos de 1 000. |
| `memory.maxRows` | entero | filas | `1000000` | 1 000 a 10 000 000. También en trozos. |
| `memory.backend` | texto | — | `sqlite` | `mysql` es un error: `is reserved and not available in this release`. |
| `learning.allowUnverified` | booleano | — | `false` | Si es `true`, un modo o un artefacto sin veredicto puede activarse y cada arranque avisa con el archivo y la ruta absoluta de `learn/active.json`. |
| `learning.collect` | booleano | — | `true` | Registra ventanas. No cambia la decisión ni el cuerpo enviado a Jev. |
| `learning.scope` | texto | — | `bench` | `bench` o `all`. `all` exige un veredicto PASS con G4. |
| `learning.baseScoring` | texto | — | `priority` | `priority` o `utility`. Nunca `learned`. |
| `learning.trainingSources` | lista | — | `[sim, bench, player_full]` | Fuentes que entrenan. `player_paced` queda fuera del default. |
| `learning.contextVersion` | entero | — | `2` | Solo 2. |
| `learning.maxContextKeysPerProfile` | entero | claves | `512` | 64 a 4096. Sin efecto en esta versión (sección 4.13). |
| `learning.maxOpenWindowsPerSession` | entero | ventanas | `32` | 8 a 128. Sin efecto en esta versión (sección 4.13). El tope es fijo: 32 ventanas por sesión. |
| `learning.creditShare` | decimal | — | `0.5` | Peso del resultado individual, 0 a 1. |
| `learning.groupCreditShare` | decimal | — | `0.5` | Peso del resultado de grupo, 0 a 1. |
| `learning.creditDecayPerWindow` | decimal | — | `0.8` | Decaimiento por ventana, 0,5 a 1. |
| `learning.minWindowsForValue` | entero | ventanas | `20` | Mínimo para usar el valor, 5 a 200. |
| `learning.halfLifeDays` | entero | días | `14` | Vida media del olvido semanal, 3 a 90. |
| `learning.weeksKept` | entero | semanas | `8` | Retención, 2 a 26. |
| `learning.maxEntries` | entero | filas | `20000` | Tope de la tabla, 1000 a 100000. |
| `learning.minValueSpreadMillis` | entero | milésimas | `20` | Por debajo, el factor aprendido es 1. |
| `learning.buildChunkRows` | entero | filas | `2000` | Filas por trozo de construcción, 200 a 10000. |
| `learning.exploreInDuels` | booleano | — | `false` | Con `true`, un duelo de entidades puede explorar. |
| `learning.exploration.policy` | texto | — | `epsilon` | Solo `epsilon`. |
| `learning.exploration.epsilon` | decimal | — | `0.10` | Probabilidad de explorar, 0 a 0,3. |
| `learning.exploration.maxShare` | decimal | — | `0.2` | Tope de adopciones exploradas, 0 a 0,5. |
| `learning.calibration.minDecisions` | entero | decisiones | `48` | 24 a 1000. Sin efecto en esta versión (sección 4.13). |
| `learning.calibration.priorStrength` | entero | — | `20` | Fuerza del prior de Platt, 0 a 200. |
| `learning.calibration.driftReferenceWindows` | entero | ventanas | `500` | 100 a 5000. Sin efecto en esta versión (sección 4.13). |
| `learning.voiSkip` | texto | — | `"off"` | `off`, `shadow` u `on`. `on` espera su puerta. |
| `learning.voi.minWindows` | entero | ventanas | `50` | Tamaño usable de la celda, 20 a 1000. |
| `learning.voi.minAgreement` | decimal | — | `0.90` | Cota inferior de Wilson, 0,5 a 0,99. |
| `learning.voi.maxExpectedGain` | decimal | — | `0.02` | Ganancia máxima en unidades de recompensa, 0 a 0,2. |
| `learning.voi.maxConsecutiveSkips` | entero | saltos | `2` | 1 a 10. |
| `learning.jevHint` | texto | — | `"off"` | `off` o `shadow`. En sombra calcula la pista, la guarda en `planned.jev_hint` y no la envía. El cuerpo queda igual que con `off`. |
| `learning.groupPlans` | texto | — | `"off"` | Solo `off`. |

### 4.12 `distill` y `autoconfig`

| Clave | Tipo | Unidad | Default | Rango y notas |
|---|---|---|---|---|
| `distill.mode` | texto | — | `"off"` | `off`, `shadow`, `assist` o `autonomous`. El perfil solo puede bajarlo. |
| `distill.scope` | texto | — | `bench` | `bench` o `all`. `all` exige un veredicto PASS con G4. |
| `distill.collect` | booleano | — | `true` | Registra la enseñanza. Con `false` y modo `off` el JSONL no gana campos. |
| `distill.minCellOffers` | entero | ofertas | `8` | 2 a 100. Por debajo, la celda usa el árbol. |
| `distill.maxTreeDepth` | entero | niveles | `6` | 2 a 10. |
| `distill.maxLeaves` | entero | hojas | `64` | 4 a 512. |
| `distill.minLeafDecisions` | entero | decisiones | `20` | 5 a 500. |
| `distill.overrideZ` | entero | — | `2` | 1 a 4. Umbral de SPIBB frente a `LOCAL`, no el de la cobertura. |
| `distill.minCellDecisions` | entero | decisiones | `50` | 20 a 1000. Mínimo de la no inferioridad. |
| `distill.nonInferiorityMargin` | decimal | recompensa | `0.02` | 0 a 0,10. La cota inferior de la media tiene que quedar en o por encima de este margen negativo. |
| `distill.coverageRefreshMinutes` | entero | minutos | `10` | 1 a 120. |
| `distill.coverageMaxAgeMinutes` | entero | minutos | `60` | 10 a 1440. Pasado el plazo, la celda vuelve a sombra. |
| `distill.auditEvery` | entero | decisiones | `10` | 2 a 100. En `assist`, una de cada tantas delegaciones pregunta a Jev. El sorteo usa `StableRandom` con sal 7. Errata E29-1 aplicada. |
| `distill.assistMinDelegable` | entero | decisiones | `500` | 100 a 10000. |
| `distill.autonomous.jevForOod` | booleano | — | `true` | Fuera de cobertura, `autonomous` pregunta a Jev. |
| `distill.regime.openingSeconds` | entero | segundos | `10` | 3 a 60. Antes, la fase es `OPENING`. |
| `distill.regime.dwellTicks` | entero | ticks | `40` | 10 a 200. Permanencia desde el evento. |
| `distill.regime.maxSwitchesPerEncounter` | entero | cambios | `6` | 1 a 20. |
| `distill.regime.minGain` | decimal | recompensa | `0.02` | 0 a 0,2. Ganancia mínima para partir un régimen. |
| `distill.regime.minDecisions` | entero | decisiones | `2000` | 200 a 50000. Cada lado. |
| `distill.regime.extraDimensions` | lista | — | `[]` | Subconjunto de `zone`, `group` y `pattern`. Nunca `memory`. |
| `distill.maxRegimesPerProfile` | entero | regímenes | `4` | 1 a 4. Errata E29-12 aplicada. |
| `distill.maxArtifactBytes` | entero | bytes | `65536` | 4096 a 1048576. El `.tpld` publicado cabe en 64 KiB. |
| `distill.weeksKept` | entero | semanas | `8` | 2 a 26. |
| `distill.drift.cusumH` | entero | — | `5` | 2 a 10. Umbral del CUSUM de auditoría. |
| `autoconfig.initialConfigs` | entero | configuraciones | `81` | 9 a 729. Incluye la actual y los defaults. Sin efecto en esta versión (sección 4.13). |
| `autoconfig.initialPairs` | entero | pares | `20` | 5 a 100. Pares de la primera ronda. Sin efecto en esta versión (sección 4.13). |
| `autoconfig.eta` | entero | — | `3` | 2 a 4. Factor de la reducción sucesiva. Sin efecto en esta versión (sección 4.13). |
| `autoconfig.maxPairs` | entero | pares | `8000` | 1000 a 50000. Sin efecto en esta versión (sección 4.13). |
| `autoconfig.maxMinutes` | entero | minutos | `90` | 10 a 600. Sin efecto en esta versión (sección 4.13). |
| `autoconfig.autoApply` | texto | — | `"off"` | Solo `off`. Nada se aplica solo. |

`shadow` calcula y registra y no cambia la decisión; pide `PASS` solo con G0. `assist` decide en la celda cubierta, salvo la auditoría; con `scope: bench` pide `PASS_SIM_ONLY` y contra jugadores `PASS` con G4. `autonomous` no audita y, fuera de cobertura, pregunta a Jev solo si `jevForOod` es verdadero; además exige la cinta del campeón. Bajar de modo es automático. Subir solo lo hace `/thepredict distill mode`. `distill disable` actúa en la captura siguiente.

La delegación crece con el uso: no hay un mob entrenado en una tarde. Con un mob, 1 884 tokens y `STANDARD`, la fórmula `60·[(1−f)+0,1·f]` da 5, 9 y 26 encuentros en `assist` para f = 0,17, 0,5 y 0,9. `autonomous` usa `60·(1−f)` y da 6, 10 y 50. Un grupo con `plan_and_leader` de 3 o más es una cota inferior, no una promesa de victorias.

El ciclo de autoconfiguración es `autoconfigSearch`, `evalGate`, `import`, `show`, `apply` y `revert`. Nunca toca archivos del autor ni de MythicMobs. `autoApply` solo admite `off`. En esta entrega `autoconfigSearch` no ejecuta la búsqueda: la propuesta se escribe a mano.

**Propuesta.** Es un archivo de texto bajo `plugins/ThePredict/autoconfig/` con una línea `clave=valor` por clave de perfil (por ejemplo `Timing.Cadence=reactive` o `Zone.Mode=guard`). Lleva además la línea `base=` con la huella del perfil para el que se pensó y, si hace falta, `profile=<id del perfil>` (sin ella, el perfil es el nombre del archivo sin extensión) y una o más líneas `pin=<clave>`, que dejan esa clave fuera. Las líneas que empiezan por `#` no cuentan.

**Superposición activa.** `apply` escribe `autoconfig/active/<archivo>`: primero `profile=` y `base=`, después las claves. Cada archivo de `autoconfig/active/` es la superposición de **un** perfil. `apply` y `revert` recargan el catálogo y los jefes vivos pasan al perfil nuevo, como tras un `/mm reload`. Una clave de la superposición gana a la misma clave del perfil del autor, salvo las que la propuesta fija con `pin=`. Si el autor cambia su perfil después de `apply`, la base deja de coincidir y vuelve a mandar el perfil del autor. Para cambiar la superposición de un perfil, `revert` la anterior antes de aplicar otra propuesta con otro nombre de archivo.

`apply` no escribe nada, y responde el motivo, cuando:

- una clave sube un modo global (`Zone.Mode`, `Group.Mode`, `Patterns.Mode` o `Distill.Mode` por encima de `zone.mode`, `group.mode`, `patterns.mode` o `distill.mode`) o pone `Zone.Mode` o `Group.Mode` en `off`: `OVERLAY_RAISES_GLOBAL`. El valor se lee sin distinguir mayúsculas, así que `Zone.Mode=OFF` también se rechaza;
- un modo no es válido, falta la línea `base=` o una línea no tiene `=`: `OVERLAY_INVALID`;
- el archivo pasa de 64 KiB: `OVERLAY_UNREADABLE`;
- ningún perfil leído ahora tiene ese nombre: `OVERLAY_UNMATCHED`;
- `base=` no es la huella del perfil leído ahora: `OVERLAY_STALE`. El mensaje da la huella actual;
- una clave no se puede aplicar al perfil, por ejemplo `Zone.Radius=100` (el rango es de 8 a 64): `OVERLAY_INVALID` con el motivo.

Al recargar (al arrancar, con cada `/mm reload` y tras `apply` o `revert`), ThePredict lee como mucho 256 archivos de 64 KiB de `autoconfig/active/` y aplica cada uno solo a su perfil. Si un archivo no se puede aplicar, se queda el perfil del autor y la consola lo avisa con el archivo: `OVERLAY_UNMATCHED` (ningún perfil con ese nombre), `OVERLAY_DUPLICATE` (un archivo anterior ya es la superposición de ese perfil), `OVERLAY_STALE` (el autor cambió el perfil después de `apply`), `OVERLAY_INVALID` (una clave no se aplica) u `OVERLAY_UNREADABLE` (archivo ilegible o de más de 64 KiB).

Al recargar, un `Timing.TickLayer` de la superposición por encima de `decision.tickLayer.mode` se baja a ese modo, igual que el del perfil del autor, y la consola lo avisa con el archivo: `Timing.TickLayer is above the global reactions; using reactions`.

Kev en local es experimental. Las cifras de latencia y de modelos son del proveedor hasta la sesión 29.7. Con `jev.provider: kev` no se lee ni se envía `TYPESAFE_API_KEY`.

El estudiante no imita a Jev. Hay que confirmar el MCA §2.3(b) con TypeSafe antes de una distribución comercial. Esto no es asesoramiento legal. La integración se describe como compatible con la API de System One. No hay marca ni respaldo de TypeSafe en el artefacto.

Una caída entre el último vaciado y el cierre del proceso pierde esas peleas, igual que los demás agregados. La copia de `aggregates.db` incluye la tabla. Un cambio de nombre en modo sin conexión es otro UUID: el registro no viaja con el nombre.

`/thepredict status` puede añadir un bloque Memory: modo, estados de la caché y contadores. `memory show <jugador|uuid>` muestra peldaño, semilla y previo solo a quien tiene `thepredict.admin` o `thepredict.memory`; `inspect` no tiene detalle de memoria. El jugador no tiene comando. Medido en local, sin servidor: alta p50 208 ns y p99 unos 4,8 µs; un digesto unos 1,5 µs (no cumple el presupuesto de 1 µs); drenar 16 pistas unos 6,0 µs (no cumple 1 µs); la caché de 500 entradas retuvo unos 1,08 MB (no cumple 416 bytes por entrada). La campaña E1 con semilla 27 y 500 campañas dio `MEMORY_DETECTION_GAIN` 3,93 (no cumple 8). El modo no se movió de `record`. Nada de esto es asesoramiento legal. La patente US 10,926,179 B2, reivindicación 1, sigue activa hasta alrededor de 2036. La consulta a un abogado de patentes (DT-83) queda pendiente del usuario antes de cualquier distribución. Los nombres son neutros. No hay rangos, facciones ni el nombre "nemesis". La sesión 27 del paquete de pruebas no se ha ejecutado.

### 4.13 Claves sin efecto en esta versión

Estas claves se aceptan y se validan, pero esta versión no las lee: cambiarlas no cambia nada. Ninguna se ha quitado, así que un `config.yml` o un perfil que las tengan siguen cargando. Si una tiene un valor distinto del de entrega, la consola lo avisa una vez al cargar con el archivo, la ruta y el texto `has no effect in this build`, por ejemplo `AIConfig/config.yml: group.idleWarnTicks: has no effect in this build`. El `config.yml` incluido escribe todas con su valor de entrega y no avisa.

- **`config.yml`:** `decision.candidateDiversity`; `records.patternSummaryBinary`; `group.idleWarnTicks`, `group.factOnTicks`, `group.factOffTicks` y `group.maxFacts` (ningún hecho de grupo llega a Jev); `patterns.hearingBlocks`, `patterns.maxWatchedPerEncounter` y `patterns.maxCounterDelayTicks`; `learning.maxContextKeysPerProfile`, `learning.maxOpenWindowsPerSession`, `learning.calibration.minDecisions` y `learning.calibration.driftReferenceWindows`; `autoconfig.initialConfigs`, `autoconfig.initialPairs`, `autoconfig.eta`, `autoconfig.maxPairs` y `autoconfig.maxMinutes` (la búsqueda no corre en el servidor).
- **Perfiles** (guía del autor): `Decision.Pruning`, `Decision.MaxOptions`, `Decision.CandidateScoring` y `Decision.Diversity` con un valor distinto de `inherit` (se guardan para las superposiciones, pero se decide con las claves globales), `Stances.Describe: true` y `Group.PlanQuestion: every_heartbeat`. Una superposición que las cambia tampoco cambia nada.
- **Dificultad:** `difficulty.maxTrackedFights` y `Difficulty.Tolerance` de un perfil tampoco se leen (se usa siempre `difficulty.toleranceRatio`). Estas dos no avisan al cargar.

## 5. Comandos y permisos

| Comando | Permiso | Qué hace |
|---|---|---|
| `/thepredict` o `/thepredict status` | `thepredict.admin` | Estado en secciones fijas: `Environment`, `Service`, `Catalog`, `Sessions`, `Encounters`, `Budget`, `Consumption`, `Latency (p50, p95, p99 of the last two minutes)`, `Errors`, `Counters` y `Effectiveness`. Si el almacenamiento está cableado, añade `Storage` (estado, registros, SQLite). Como mucho 48 líneas en total: si hay más, la última dice cuántas no se muestran (`<n> more status lines are not shown`). Nunca muestra claves ni nombres de jugadores. |
| `/thepredict check` | `thepredict.admin` | Pide una comprobación del servicio sin bloquear el servidor: responde `Jev check started; the result arrives here without blocking the server. …` y, al llegar, `Jev check: <ESTADO> (<detalle>). …` con el paso siguiente. `GET /v1/models` no consume tokens y no verifica una inferencia. |
| `/thepredict inspect <nearest\|uuid>` | `thepredict.admin` | Explica un mob vinculado: sesión y nivel, intención, alternativas y contingencias, motivo (`Why`), percepción, objetivo por alias, últimos eventos, metas, movimiento, tabla de amenaza y las últimas ventanas de resultado (`Outcomes:`, sección 6.5). Con M19, el origen de cada skill (`origin=skill` cuando la línea `Intention` se usó) y si la choice está sin tipo. `nearest` busca el mob vinculado más cercano en 16 bloques y exige ser jugador; desde la consola se usa la UUID. Un tercer argumento pide un detalle: `tick`, `stance`, `telegraph`, `group` o `patterns`, sin distinguir mayúsculas; el uso y el autocompletado los listan. No hay detalle de zona ni de memoria. Un mob sin sesión: `<uuid corta> has no session: it is not a bound mob with Predictor: true and an active profile.` El texto exacto de `inspect` en juego está pendiente de servidor (sesión 19.1). |
| `/thepredict intention suggest [perfil]` | `thepredict.admin` | Imprime, para cada choice vinculada, la línea canónica `text(render(draftOf(purpose)))` y lo que no cabe en ella (por ejemplo un alcance sin primitiva). Una choice sin tipo no tiene línea. Nunca escribe archivos de MythicMobs. Sin `[perfil]`, los perfiles vinculados. El mismo permiso que `inspect`. |
| `/thepredict records stats` | `thepredict.admin` | Tres datos del bloque Storage: estado, registros y SQLite. No bloquea. |
| `/thepredict eval status` / `difficulty <nearest\|uuid>` / `report [tag] [since] [md\|jsonl\|html]` / `verdict import <archivo>` / `verdict list` | `thepredict.eval` | Leen o anotan la dificultad (M22). Detalle en el párrafo de `/thepredict eval` de la sección 4 (`difficulty`). |
| `/thepredict eval pressure <nearest\|uuid> <0-8\|auto>` | `thepredict.eval` y `thepredict.debug` | Fija la presión de un mob para probar. No se guarda. |
| `/thepredict zone` | `thepredict.admin` | Una línea fija: `No ready zone. Effective mode is the minimum of zone.mode and Zone.Mode. Permission: thepredict.admin.` (E24-6). En esta entrega ni `zone` ni `inspect` muestran la zona de un mob. |
| `/thepredict memory stats` | `thepredict.admin` | Estado de la memoria. Responde `queued`. |
| `/thepredict memory show <jugador\|uuid>` | `thepredict.admin` o `thepredict.memory` | Peldaño, semilla y previo. Responde `queued`. |
| `/thepredict memory reset <jugador\|uuid>` / `purge <días>` / `export [jugador\|uuid\|all]` / `import <archivo> newer\|replace` | `thepredict.memory` | Responde `queued`. `export` sin selector exporta todo. `import` rechaza línea a línea (versión nueva, UUID inválido o más de 1 024 bytes) y no deshace el archivo entero. `reset` descarta digestos pendientes y un lote en vuelo; el siguiente digesto empieza en cero. Un nombre que no está en línea ni en caché tiene que ser un UUID. `import` solo lee `records/imports/` y rechaza `..`. |
| `/thepredict learn status` | `thepredict.admin` o `thepredict.learn` | Una línea: `learn none artifact none verdict none collect <learning.collect> scope <learning.scope>`. `collect` y `scope` son los del `config.yml` cargado. El servidor no carga un artefacto aprendido, por eso el estado es `none`. No bloquea. |
| `/thepredict learn build` / `export <sha> <archivo>` / `import <archivo>` / `rollback` / `disable value\|calibration\|all` | `thepredict.learn` | Con el escritor, responden `queued` y el trabajo corre en `ThePredict-Storage`. Sin escritor, dicen que hace falta. No abren SQLite en el hilo del servidor y no arrancan un hilo nuevo. `build` lee `m28_value_stats` por páginas y escribe `learn/artifacts/<sha>.tplv` y `learn/active.json`. |
| `/thepredict distill status` / `build [perfil]` / `export <archivo>` / `import <archivo>` / `rollback` / `disable all` / `inspect` / `mode <off\|shadow\|assist\|autonomous>` | `thepredict.distill` | `status` e `inspect` responden al momento con `state=NONE mode=<modo> regime=r1:ALL`, donde `<modo>` es el que usará la captura siguiente después de `mode` y `disable all` (el servidor no carga un artefacto destilado, por eso el estado es `NONE`). El resto responde `queued` en el escritor. `build` no entrena. `mode` es la única subida y nunca pasa de `distill.mode` ni del veredicto. Sin `verdict.json` el techo es `off`. Un `PASS` sin cinta deja `autonomous` en `assist`; un `PASS` con cinta puede llegar a `autonomous`; un `G0` solo deja el techo en `shadow`. `disable all` actúa en la captura siguiente y no toca el artefacto de `learn`. Las rutas quedan bajo `plugins/ThePredict/learn/distill/` y rechazan `..`. |
| `/thepredict autoconfig import <ruta>` / `show <ruta>` / `apply <ruta>` / `revert <ruta>` / `status` | `thepredict.autoconfig` | El ciclo es importar, mostrar, aplicar y revertir. La ruta tiene que vivir bajo `plugins/ThePredict/autoconfig/` y no puede llevar `..`. `import` copia la propuesta a `autoconfig/inbox/`. `show` devuelve la copia activa de esa propuesta, o `none`. `apply` escribe solo `autoconfig/active/`, no abre el perfil del autor y rechaza con su motivo lo que la recarga no aplicaría (sección 4.12). `revert` borra esa copia activa. `apply` y `revert` recargan el catálogo. `status` responde `autoApply=off`. |
| `/thepredict records export outcomes [perfil]` / `export events <desde> <hasta> [tipos]` / `prune [días]` / `verify` / `backup` / `flush` / `cache clear` / `tag <etiqueta>` / `tag clear` | `thepredict.records` | Responde `queued` y el resultado llega después. Un segundo comando del mismo tipo se rechaza. El export de eventos se corta en 512 MiB. |
| `/thepredict debug intent <nearest\|uuid> <tactic> [ticks]` | `thepredict.debug` | Fija una intención `LOCAL` de una táctica del perfil, de 1 a 6000 ticks (sin `[ticks]`, la vigencia del perfil, 40 por defecto). **Nunca envía una solicitud a Jev.** Rechaza, con el motivo, una táctica que no existe o que la última captura no hizo elegible. |
| `/thepredict debug release <nearest\|uuid>` | `thepredict.debug` | Baja el mob a nivel 4; la cadena de fallback decide de nuevo en ≤2 ticks. |
| `/thepredict debug goals <nearest\|uuid>` | `thepredict.debug` | UUID completa y generación, una línea por meta (`running` si corre) y el progreso del movimiento. |
| `/thepredict duel start <etiqueta> <facciónA> <facciónB> [radio] [skills=A,B]` | `thepredict.debug` | Solo jugador: graba un duelo en su mundo, con centro en su posición (sección 8.3). |
| `/thepredict duel start <etiqueta> <facciónA> <facciónB> <mundo> <x> <y> <z> [radio] [skills=A,B]` | `thepredict.debug` | Consola o jugador: mundo y centro explícitos. La consola sin mundo se rechaza. |
| `/thepredict duel status` / `/thepredict duel stop` | `thepredict.debug` | Estado de la grabación (rondas, duelistas seguidos, solicitudes, tokens de entrada, filas descartadas, bytes y carpeta) y parada con `summary.md`. |

`distill build` conserva la tabla, reanuda una página por turno del escritor y no pasa de 5 ms en ese turno; un recurso `jar` de `learn/packaged/` con `verdict.json` queda importado. El perfil efectivo de `autoconfig apply` es el del autor más su superposición de `autoconfig/active`, y el archivo del autor no se escribe.

`/thepredict inspect <nearest|uuid> group` añade la época de membresía, el plan, el rol, el hueco y si el miembro ve al objetivo. El texto exacto en juego queda (pendiente de servidor).

- **`thepredict.admin`** tiene `default: op`. Es el permiso del comando `/thepredict` en `plugin.yml`: un jugador sin él no usa ninguna orden, y los permisos de abajo se suman a él. `thepredict.admin` solo sustituye al permiso propio en `records stats`, `memory stats`, `memory show` y `learn status`.
- **`thepredict.eval`**, **`thepredict.distill`** y **`thepredict.autoconfig`** tienen `default: op`. Cada uno cubre toda su orden, también `distill status`, `distill inspect` y `autoconfig status`.
- **`thepredict.records`** tiene `default: op`. Cubre todo `/thepredict records` salvo `stats`, que basta con `thepredict.admin`.
- **`thepredict.memory`** tiene `default: op`. `stats` y `show` también aceptan `thepredict.admin`. **`thepredict.memory.exempt`** tiene `default: false`: ese jugador no recibe semilla, previo ni frases.
- **`thepredict.learn`** tiene `default: op`. `status` también acepta `thepredict.admin`. El jugador no ve el valor aprendido. Con los defaults no cambia ninguna decisión. `parity` compara en el espacio crudo: el ajuste del brazo D lleva la confianza 0,45 a una probabilidad calibrada de aproximadamente 0,777, y el umbral inverso decide igual que el crudo a 0,45. El ciclo es `build`, `evalGate`, importar el veredicto, y luego `rollback` o `disable`. `voiSkip: on` y `scope: all` esperan la puerta del administrador. No se explora contra jugadores salvo en un mob con `Predictor: true` y `PredictorTraining: true`.
- **`thepredict.debug` tiene `default: false`: ni los operadores lo tienen.** Sin él, `debug` responde `You need the thepredict.debug permission to use debug tools`, `duel` responde `You need the thepredict.debug permission to record duels`, y ninguno aparece en el autocompletado. La consola siempre puede usar `debug`. Para darlo a los operadores sin plugin de permisos, crea `permissions.yml` en la raíz del servidor:

  ```yaml
  thepredict.verifier:
    description: Grants ThePredict debug tools to operators on the verification server
    default: op
    children:
      thepredict.debug: true
  ```

(Comandos y permisos probados en local; en juego, **pendiente de servidor**, DT-17.)

**Líneas de `status` que más se usan.**

| Línea | Qué leer |
|---|---|
| `State: Jev <ESTADO> (<detalle>)` | Estado del servicio (sección 6); `, trial` durante la prueba tras una pausa. |
| `Model: configured jev-1.13.0; last answer <modelo>` | El modelo de la última respuesta. |
| `Pause: ends in <n> s` | Solo durante una pausa. |
| `Profiles: <ESTADO> (revision N, <r> profiles read, <a> active, <e> errors, <w> warnings)` | Catálogo; `INVALID` añade el primer error. |
| `Bound mobs: <n> active, <m> suspended; levels JEV …, ALTERNATIVE …, LOCAL …, BASE …; debug intents <d>` | Sesiones por nivel. |
| `Server: <x>/600 requests in 60 s, <y>/8 in flight` y `Your world: …` | Presupuesto usado. |
| `Input tokens: <t>/600000 in 60 s; estimated cost <c> USD per hour (…)` | Consumo y costo estimado. |
| `capture`, `prepare`, `roundtrip`, `inbox_wait`, `apply`, `dispatch`, `tick_work` | Latencias p50, p95, p99, muestras y máximo de los dos últimos minutos. `prepare` es la codificación y deduplicación de la solicitud en el hilo del servidor (M16); `roundtrip` es el tiempo hasta Jev y vuelta; `tick_work` el trabajo de ThePredict por tick. |
| `Recent failures`, `Recent fallbacks` | Las tres últimas causas con su antigüedad. |
| `totals`, `failures`, `dispatch`, `fallbacks`, `outcomes`, `discards` | Contadores acumulados; cada grupo muestra solo sus valores distintos de cero, y un grupo sin ninguno muestra `none`. `totals` incluye desde M16 `options_merged`, `contingencies_asked`, `contingencies_fired` y `contingencies_effective` (sección 6.5). |
| `outcomes: level=JEV windows=… dealt=… taken=… reach=… hit_rate=… effect=…` y `contingencies: asked=… fired=… effective=…` | Sección `Effectiveness`: qué consiguieron las intenciones adoptadas, por nivel (sección 6.5). |

## 6. Diagnóstico

La justicia de los mínimos de aviso, el coste de una señal `PREDICTOR_TELL_*` y la lectura en vivo de `predictor_stance` no están medidos en servidor. La sesión 23 los deja pendientes. Esas señales no llaman a Jev.

### 6.1 Estado del servicio (`ServiceState`)

| Estado | Causa típica | Acción |
|---|---|---|
| `UNCONFIGURED` | Falta la clave | Definir `TYPESAFE_API_KEY` (o `jev.apiKey`) y reiniciar el servidor. Mientras, modo local sin costo. |
| `INVALID_CONFIGURATION` | `config.yml` inválido (la consola da archivo, ruta y motivo) | Corregir `config.yml` y reiniciar. ThePredict queda inactivo; MythicMobs funciona. |
| `CHECKING` | Comprobación en curso (arranque o tras una pausa) | Esperar el resultado en la consola o en `/thepredict status`. |
| `READY` | Servicio disponible | Ninguna. |
| `PAUSED` | 429 o 529 (con `retry-after` si lo hay), error de red o del servicio, 2 timeouts seguidos, 3 respuestas inválidas seguidas o una solicitud rechazada por Jev (pausa de 60 s; una solicitud que ThePredict no llega a preparar, `PREPARE_FAILED`, no pausa) | Esperar: ThePredict reintenta solo con una pausa creciente (1 s a 60 s) y después una prueba con una sola solicitud. Si se repite, revisar la latencia (`roundtrip`) y los límites. |
| `INVALID_CREDENTIALS` | Clave rechazada (401 o 403) | Revisar la clave en la consola de TypeSafe, ponerla y **reiniciar**: la clave solo se lee al arrancar. |
| `INVALID_MODEL` | Modelo inexistente (la cuenta real responde 400 `Unknown model`) | Usar el ID versionado probado, `jev-1.13.0`, y reiniciar. |

Con `INVALID_CREDENTIALS` o `INVALID_MODEL` ThePredict no envía nada hasta un reinicio o un `/thepredict check`; los mobs pelean en modo local.

### 6.2 Motivos de caída de nivel (`FallbackReason`)

Aparecen en `inspect` (`Why: last fallback …`), en `status` (`fallbacks`, `Recent fallbacks`) y, con `decision.logDecisions`, como `Mob <uuid> fell back: <MOTIVO>`.

| Motivo | Causa típica | Acción |
|---|---|---|
| `SERVICE_PAUSED` | El servicio está en pausa (429, 529, timeout o red) o comprobándose (`CHECKING`) | Esperar la pausa; revisar latencia y límites si se repite. |
| `SERVICE_BLOCKED` | El servicio no se puede usar sin cambiar la configuración: `UNCONFIGURED`, `INVALID_CONFIGURATION`, `INVALID_CREDENTIALS` o `INVALID_MODEL` | Ver `State` en `status` y corregir la clave o el modelo; reiniciar. |
| `NO_BUDGET` | Se agotó el presupuesto de solicitudes, tokens o solicitudes en vuelo (servidor, mundo o encuentro) | Revisar `maxRequestsPerMinute`, `maxInputTokensPerMinute` y la carga; los mobs sin presupuesto juegan en local sin error. |
| `LOW_CONFIDENCE` | Confianza baja repetida (`lowConfidenceStreakForLocal` respuestas seguidas bajo `MinimumConfidence`) | Revisar perfil, contextos, nombres y descripciones de las tácticas. |
| `INVALID_RESPONSE` | La respuesta no tenía ninguna táctica aplicable | Revisar que el perfil ofrezca tácticas elegibles; si se repite, reportar con el registro. |
| `EXPIRED` | La intención venció sin reemplazo | Normal si ocurre de forma aislada; si es habitual, revisar `roundtrip` frente a `maximumIntentDurationTicks`. |
| `NO_PROGRESS` | El movimiento no avanza en dos decisiones (objetivo en un pilar, pared, agua) | Revisar terreno, arena y especie del mob. |
| `RETREAT_LIMIT` | El encuentro ya tiene tantas retiradas simultáneas como permite (`max(1, miembros / 3)`) | Ninguna; es la coordinación entre mobs. |
| `SESSION_INVALID` | Mob recargado, muerto o cambiado de mundo | Ninguna; es transitorio. |
| `INTERNAL_ERROR` | Excepción en ThePredict (la consola dice `<n> session(s) fell back to the base AI after an internal error`) | Reportar con el registro; el mob vuelve a su IA base. |

### 6.3 Otras causas que muestra `status`

| Grupo | Valores |
|---|---|
| `failures` (`FailureKind`) | `AUTHENTICATION` (401 o 403), `MODEL_UNAVAILABLE` (modelo inexistente), `INVALID_REQUEST` (solicitud rechazada o demasiado grande), `RATE_LIMITED` (429), `OVERLOADED` (529), `SERVICE_ERROR` (un 5xx, o cualquier otro estado que no es ninguno de los anteriores), `NETWORK`, `TIMEOUT`, `INVALID_RESPONSE` |
| `dispatch` | `SENT`, `EMPTY_CAPTURE` (ningún miembro legible), `NO_QUESTIONS` (ningún mob con objetivo percibido y dos tácticas elegibles distintas, ni pregunta de plan de grupo), `SERVICE_UNAVAILABLE`, `NO_BUDGET`, `NOTHING_TO_ASK` (al preparar la solicitud, ninguna pregunta conservó dos opciones que se lean distinto: no se envía ni se reserva nada y los mobs juegan en local), `PREPARE_FAILED` (la solicitud no se pudo codificar, por ejemplo por superar la guarda de tamaño: se cuenta como `INVALID_REQUEST` en `failures`, no se envía ni se reserva nada, los mobs de ese encuentro juegan en local y **el servicio no se pausa**, así que los demás encuentros siguen preguntando a Jev; una línea de aviso, como mucho cada 10 s y sin el cuerpo), `CLIENT_ERROR`, `CACHE_HIT` (solo con `decision.answerCache: on`: la caché de respuestas sirvió la respuesta y no se llamó a Jev) |
| `outcomes` (`ExecutionOutcome`) | `CAST`, `DEFERRED_INVULNERABLE` (objetivo invulnerable, se reintenta), `SKIPPED_SHIELD` (escudo que bloquea), `REJECTED_BY_MYTHICMOBS` (probabilidad, evento cancelado o sin cargas), `NOT_ELIGIBLE` (aún no lista, se reintenta), `EXPIRED` |
| `discards` (`DiscardReason`) | `STALE_SEQUENCE`, `SESSION_CHANGED`, `SESSION_INACTIVE`, `CATALOG_CHANGED`, `MODEL_CHANGED` (respondió otro modelo), `TOO_OLD`, `TARGET_CHANGED`, `PROBE_UNAVAILABLE`, `SITUATION_CHANGED` (la respuesta ya no encaja con la situación viva del mob), `GROUP_EPOCH_CHANGED` (la respuesta de plan de grupo se pidió con otra composición del grupo; la táctica del líder se conserva) |

### 6.4 Problemas de perfiles

`/thepredict status` resume el catálogo en la línea `Profiles:` y la consola da cada problema con archivo y ruta, por ejemplo `guardian_ai.MythicID: unknown mob 'guardian'; IDs are case-sensitive, did you mean 'Guardian'?`. Un perfil con error se ignora; los demás siguen activos. La [guía del autor](Guia-del-autor.md) explica cada campo.

Avisos de M16 (no excluyen el perfil):

- `Tactics.<a> and Tactics.<b> describe the same option to Jev; Jev only sees '<a>'`: dos tácticas idénticas en todo (mismo movimiento, misma descripción y propósito igual); Jev solo recibe la primera por prioridad.
- `<perfil>.Timing.FitChecks: is ignored: fit checks are only sent with QuestionDetail full, and this profile uses compact` (con la ruta `decision.fitChecks` si el perfil lo hereda del global): la idoneidad solo se envía con `full`. Con `decision.fitChecksAllowed: false`, `Timing.FitChecks: true` es un error: `must be false while decision.fitChecksAllowed is false`.

Avisos de M19 (no excluyen el perfil). Una línea `Intention` en un archivo de MythicMobs es siempre `WARNING`, nunca un error de configuración de ThePredict:

- `skill '<S>' has no Intention; the choice is untyped and only casts with its own tactic` cuando no hay línea y la choice queda sin tipo.
- `PredictValues of skill '<S>' are no longer read; write an Intention` cuando la choice queda sin tipo y la skill aún tiene `PredictValues`. Un perfil de la 0.0.1 con `Purpose` completo no gana este aviso: `PredictValues` solo se avisan si la choice queda sin tipo (errata E19-1 DP4).
- `Choices.<id>.Purpose overrides the Intention of skill '<S>'` cuando el perfil tiene `Purpose` y la skill también tiene línea: manda el perfil, entero.
- Coherencia, en inglés: `skill '<S>' checks lineofsight but its purpose has no NEEDS_LINE_OF_SIGHT risk` y el inverso `purpose has the NEEDS_LINE_OF_SIGHT risk but skill '<S>' does not check lineofsight`; `purpose deals damage but skill '<S>' has no damage mechanic` y `skill '<S>' has a damage mechanic but the purpose deals no damage`; `skill '<S>' ignores shields but the purpose says BLOCKED_BY_SHIELD`. En un `Purpose` declarado, además: `Reach 12 differs from targetwithin{d=16} of skill '<S>'; MythicMobs refuses beyond 16` y `WindUp 5 differs from the delays of skill '<S>' (10 ticks)`. Un `Reach` por encima de `RangedReach` sigue avisando `is beyond RangedReach; Jev reads it as beyond ranged reach`.
- Lint de táctica, sin cambios de texto: `Tactics.keep_distance_and_strike: Movement KEEP_DISTANCE keeps the target beyond Band.Inner 5 but skill 'melee' reaches 2.5; this tactic never casts`.
- `PredictValues say MELEE_ATTACK but Delivery is PROJECTILE; the declared purpose is used` cuando un `Purpose` declarado contradice una etiqueta histórica. `IGNORES_SHIELD` de `PredictValues` no se copia a un `Purpose` declarado (errata E19-1 DP5).
- `skill '<S>': it runs nested skills, projectiles or auras that ThePredict does not follow; it may deal damage and a knockback is assumed` cuando la skill ejecuta otras skills (`skill{s=…}`, `randomskill`), un proyectil (`projectile`) o un aura (`aura`). ThePredict no sigue lo que hacen: la trata como una skill que puede dañar y supone un empuje (`KNOCKBACK`), lo prudente.

### 6.5 Efectividad: ventanas de resultado (M16)

Cada vez que un mob **adopta** una intención nueva de nivel `JEV`, `ALTERNATIVE` o `LOCAL` (nunca en una renovación), ThePredict abre una ventana de resultado que se cierra al terminar la intención o a los 60 ticks, lo primero. La ventana suma el daño hecho y recibido como **fracción de la vida máxima** de la víctima, cuenta los casts y los **golpes confirmados** (daño al objetivo en los 20 ticks siguientes a un cast dañino), anota si el movimiento **alcanzó su meta** (`APPROACH` llegó a cuerpo a cuerpo; `KEEP_DISTANCE` terminó a distancia; `RETREAT` terminó más lejos que empezó; `FLANK` lanzó flanqueando; `HOLD` y `NONE` no tienen meta) y las muertes del mob y de su objetivo.

| Dónde | Qué leer |
|---|---|
| `status`, sección `Effectiveness`: `outcomes: level=JEV windows=3 dealt=0.12 taken=0.03 reach=0.67 hit_rate=0.50 effect=0.21` | Una línea por nivel con ventanas cerradas. `dealt` y `taken`: media por ventana en fracciones de vida máxima; `reach`: proporción de ventanas cuyo movimiento alcanzó su meta; `hit_rate`: golpes confirmados por cast; `effect`: media del índice comparativo `dealt − taken + 0,25·reached + objetivo muerto − mob muerto`. El índice no es "la verdad": sirve para comparar niveles o brazos con el mismo perfil. Sin ventanas: `outcomes: no closed window yet`. |
| `status`: `contingencies: asked=4 fired=2 effective=1` | Contingencias enviadas a Jev, disparadas en combate y disparadas cuya ventana cerró con efecto positivo. |
| `status`, `totals`: `options_merged=` | Opciones que no se enviaron porque se leían igual que otra de la misma pregunta. |
| `inspect`: `Outcomes: JEV keep_distance_and_shoot dealt=0.12 taken=0.03 reached=yes casts=1 hits=1; …` | Las últimas 8 ventanas del mob, la más reciente al final; añade `mob died` o `target died` si ocurrió. |
| Consola, con `decision.logDecisions: true`: `Outcome JEV keep_distance_and_shoot dealt=0.12 taken=0.03 reached=yes casts=1 hits=1` | Una línea por ventana cerrada, con el mismo límite de frecuencia que el resto del registro de decisiones. |

El daño de una skill de MythicMobs se atribuye al mob que la lanzó si Paper lo notifica con el lanzador como causante; eso está **pendiente de servidor** (DT-35, sesión 16.1 del paquete de pruebas).

### 6.6 Propósito interno (`Purpose`) y migración a `Intention`

`Purpose` no es para autores. Es un dato interno y una anulación del administrador, bajo `Choices.<id>.Purpose`, para ajustar un mob concreto. Es la única forma que admite números (`Reach`, `Radius`, `WindUp`). Si el perfil tiene `Purpose` y la skill tiene `Intention`, manda el `Purpose` entero (origen `DECLARED`) y el vinculador avisa. Sin los dos, la choice queda sin tipo. Nada se infiere de `PredictValues` (decisión 5 del usuario, 2026-09-30).

| Clave | Obligatoria | Valores |
|---|---|---|
| `Roles` | sí | 1 a 3 de `DAMAGE`, `CONTROL`, `GAP_CLOSE`, `ESCAPE`, `DEFENSE`, `SUSTAIN`, `BUFF`, `DEBUFF`, `SUMMON`, `ZONE`, `INTERRUPT`, `FINISHER`, `OPENER`. El primero es el principal. |
| `Delivery` | sí | `MELEE`, `PROJECTILE`, `HITSCAN`, `AREA_AROUND_SELF`, `AREA_AT_TARGET`, `CONE`, `SELF` |
| `Reach` | según la entrega | bloques en `(0, 64]`. Obligatorio con `MELEE` y `CONE`; prohibido con `SELF` y `AREA_AROUND_SELF`. |
| `Radius` | según la entrega | bloques en `(0, 16]`. Obligatorio con `AREA_AROUND_SELF` y `AREA_AT_TARGET`. |
| `WindUp` | no (`0`) | ticks, de 0 a 100 |
| `Damage` | con `DAMAGE` o `FINISHER` | `NONE`, `LOW`, `MEDIUM`, `HIGH`. Con papel dañino no puede ser `NONE`. `UNRATED` no se escribe aquí. |
| `BestWhen` | no | 0 a 4 de `TARGET_LOW_HEALTH`, `TARGET_SHIELDING`, `TARGET_FLEEING`, `SELF_LOW_HEALTH`, `ENEMIES_GROUPED`, `TARGET_AT_RANGE`, `TARGET_IN_MELEE` |
| `Risks` | no | 0 a 4 de `LEAVES_CASTER_EXPOSED`, `ROOTS_CASTER`, `SELF_DAMAGE`, `NEEDS_LINE_OF_SIGHT`, `SLOW_PROJECTILE`, `BLOCKED_BY_SHIELD`, `HITS_ALLIES` |
| `IgnoresShield` | no (`false`) | `true` o `false`. Incompatible con `BLOCKED_BY_SHIELD`. |

La medida del alcance no se escribe: un `Purpose` declarado usa la de su entrega (`MELEE` y `CONE`, hueco entre cajas; `AREA_AROUND_SELF`, distancia entre posiciones estricta; el resto, distancia entre centros). `AREA_AROUND_SELF` sin radio no es válido.

**Migrar un perfil que ya tiene `Purpose`** (errata E19-1 DP4):

1. `/thepredict intention suggest <perfil>` y copiar cada línea a la skill, bajo `Intention`.
2. `/mm reload`. `inspect` debe mostrar `origin=skill` con los mismos papeles, entrega y condiciones. Pendiente de servidor (sesión 19.1).
3. Quitar `Purpose` del perfil. Si se deja, manda el perfil y el aviso lo dice.
4. Quitar `PredictValues` es opcional. Ya no se leen. El vinculador solo avisa de ellos cuando la choice queda sin tipo. Un perfil de la 0.0.1 con `Purpose` completo no gana avisos nuevos por conservarlos. El ejemplo los conserva junto a `Intention` y vincula sin esos avisos.

**Revertir:** volver a poner `Purpose` en el perfil. Gana al de la skill.

La configuración humana vive en `plugins/MythicMobs/AIConfig/` (sección [2.1](#21-ubicación-de-la-configuración-y-migración)). Esta entrega no cambia claves ni formatos.

## 7. Costos, límites y capacidad

- **Avisos y posturas (M23):** una señal no es una llamada de pago. El coste por tick de la postura y de la liberación está medido solo en local (`m13Measure`, tabla «Stances and telegraphs (M23)», 20, 100 y 200 sesiones). El coste en un servidor Paper no está medido (sesión 23.7).
- **Precio:** 0,042 USD por millón de tokens de entrada; la salida es gratuita (Jev 1.13, documentación de TypeSafe revisada el 2026-09-28; confírmalo en `https://docs.typesafe.ai/models`, TypeSafe puede cambiarlo sin aviso). `status` estima el costo por hora con ese precio: tokens del último minuto × 60 × 0,042 / 1 000 000.
- **Límites publicados de la cuenta:** 1 200 solicitudes por minuto y 250 000 tokens por segundo; 64k tokens por solicitud y 32k para el estado más la pregunta más larga. ThePredict usa la mitad del límite de solicitudes por defecto.
- **Grupos (M25).** Los topes de M09 no cambian: 600 solicitudes/min, 300 por mundo y 600 000 tokens/min. `GroupCapacity` sigue con la estimación de 3114 tokens para `plan_and_leader` y 5 miembros: con ella `REACTIVE` cabe en &lt; 1 encuentro (≈ 0,96) y `STANDARD` cabe en 3. `Group.Questions` decide qué preguntas van: con `plan_and_leader` solo la del líder y la del plan; los demás miembros siguen en el estado y juegan en local. La columna local del codificador, sin `m13Measure` y sin llamada de pago, mide para ese mismo caso 6 981 caracteres (4 654 tokens a 1,5 y 2 738 a 2,55); con una pregunta por miembro (`members`) eran 22 182. `REACTIVE` y `STANDARD` comparten esos caracteres: la cadencia no entra en el cuerpo. Con 4 654 tokens la fórmula da unos 0,64 encuentros en `REACTIVE` y unos 2,1 en `STANDARD`; a 2,55 caracteres por token, unos 1,1 y 3,7. La estimación de 3114 queda entre las dos medidas. No se movieron los topes ni el 3114. La sesión 25.8, de pago, no se ejecutó.
- **Qué límite manda depende del número de mundos** (tabla de la sección 7.1). Con los defaults (`full` desde M16.10) y los tokens medidos en el re-banco (≈ 1 884 por solicitud de producción): en un solo mundo, unos **5 mobs aislados** en combate continuo, igual que con `compact` (el tope de solicitudes por mundo, 300/min, manda antes); en varios mundos, también unos **5**, ahora por el tope de tokens (600 000/min), frente a unos 10 con `compact` (≈ 953 tokens). A partir de ahí los mobs sin presupuesto juegan en local (`NO_BUDGET`) sin error. **En el tope de tokens, el costo es de unos 1,5 USD por hora de combate continuo** (600 000 × 60 × 0,042 / 1 000 000 = 1,512 USD). Para gastar menos, baja `maxInputTokensPerMinute`. (La cifra anterior de "unos 30 mobs" de la simulación de M13 era **anterior a M15 y M16** y suponía otra forma de solicitud; la sustituye la tabla de 7.1.)
- **Reserva de presupuesto por tamaño real (M16).** Antes de reservar, ThePredict prepara el cuerpo exacto que enviará y reserva `ceil(caracteres del cuerpo / cpt)` tokens, donde `cpt` (caracteres por token) empieza en 1,5 y aprende de cada respuesta (`cpt += 0,2 · (caracteres / input_tokens − cpt)`, acotado a 1,3–2,0). Sustituye a la estimación fija de 0.0.1 (400 + 120 por pregunta + 60 por participante). La guarda de tamaño que impide enviar una solicitud por encima de los límites de Jev sigue fija en 1,5 caracteres por token: el valor aprendido nunca la mueve.
- **Consumo medido con la cuenta real.** Re-banco de M16.10 (2026-09-30, 600 llamadas): la solicitud de producción (1 mob, 1 participante, `NOW` más una contingencia, 7 opciones) cuesta **≈ 1 884 tokens reales en `full`** y ≈ 953 en `compact`. Un mob aislado en combate continuo envía unas 60 solicitudes por minuto: **≈ 0,28 USD por hora de combate con `full`** (1 884 × 60 × 60 × 0,042 / 1 000 000) y ≈ 0,14 USD con `compact`. (Antes de M15 y M16 se midieron 613 tokens por decisión de una pregunta; aquellas solicitudes eran de otra forma.)
- **Latencia medida desde la máquina de desarrollo:** `POST /v1/systemone` p50 179 ms, p95 228 ms, p99 328 ms, máximo 513 ms (1 200 llamadas, sin ningún timeout); `GET /v1/models` p50 141 ms. Desde el host del servidor: **pendiente de medición en servidor**.
- **Capacidad soportada del host** (mobs en combate sostenido, solicitudes por minuto y tiempo añadido por tick): **pendiente de medición en servidor** (guion de M13, §5.6). La referencia local es que el propio motor gasta de media 4 a 22 µs por tick de 1 a 200 mobs con dobles; no incluye el costo real de captura ni de rutas en el servidor.

### 7.1 Costo y capacidad por nivel de detalle (`decision.questionDetail`)

Con `decision.pruning: "off"` (el default de esta entrega) y `decision.maxOptionsPerQuestion: 8` la pregunta no cambia: las solicitudes de producción siguen siendo las de M17.0 byte a byte, y las cifras medidas de M16.10 de la tabla de abajo siguen aplicando. Ni la poda `safe`, ni el tope 4, ni el horizonte 20 están activos.

Medido el 2026-10-06 en local, sin red y sin llamar a TypeSafe (`JevMeasurements`, el mismo `prepare` de la solicitud de producción en `full`: 1 mob, 1 participante, `NOW` más una contingencia, 7 opciones). El cuerpo completo mide 4 805 caracteres y 3 204 tokens estimados a 1,5 caracteres por token. El mismo cuerpo con un tope de 4 opciones mide 4 441 caracteres y **2 961** tokens a 1,5. El plan estimaba como mucho 2 200 tokens a 1,5 (unos 3 300 caracteres, cerca de un 31 % menos); **2 200 era una estimación y no se alcanzó**. El ahorro medido es de 364 caracteres (un 7,6 %), no el 31 % estimado: las frases de M16, `SemanticPhrases` y `criteria` no se acortaron. Esos 2 961 son la cota de la guarda de tamaño, no tokens reales de la cuenta; los tokens reales de `full` siguen siendo los ≈ 1 884 del re-banco de M16.10.

Solicitud de producción: 1 mob, 1 participante, `NOW` + 1 contingencia, 7 opciones del ejemplo, estado 1 contra 1 de unos 700 caracteres. Los **caracteres están medidos** con el codificador de M16 (`ScenarioFacts.production`, tabla "Request size per question detail" de `./gradlew m13Measure`). **Los tokens de `compact`, `standard`, `full` y `full` + idoneidad son reales**: los del re-banco de M16.10 (2026-09-30, `evidencia/m16-rebanco-2026-09-30/`, tabla "Production request" del informe recalculado), no estimaciones; entre paréntesis, la cota a 1,5 caracteres por token que usan las herramientas y la guarda de tamaño. La capacidad se da en "mobs aislados en combate continuo": cada uno es un encuentro que envía unas 60 solicitudes por minuto (una por latido de 20 ticks). La capacidad efectiva es el **mínimo** de dos topes: tokens (`decision.maxInputTokensPerMinute` ÷ (60 × tokens por solicitud)) y solicitudes (`decision.maxRequestsPerMinute`, 600 → 10 mobs en todo el servidor; `decision.maxRequestsPerMinutePerWorld`, 300 → 5 mobs por mundo). La cuenta de TypeSafe admite 1 200 solicitudes/min (20 mobs aislados) y `maxRequestsPerMinute` no puede pasar de 1 200.

| Nivel | Caracteres por solicitud | Tokens reales por solicitud | Mobs por tokens a 600 000/min | Mobs por tokens a 1 500 000/min | Tope de solicitudes con defaults: global (600/min) / por mundo (300/min) | Tope de la cuenta (1 200/min) | **Efectivo con defaults**: 1 mundo / varios mundos | **Efectivo con 1 500 000 tokens/min y los dos topes de solicitudes a 1 200** | Costo por hora de un mob aislado |
|---|---|---|---|---|---|---|---|---|---|
| 0.0.1 antes de M16 (5 opciones; estimado a partir de 2 253 caracteres de M13) | ≈ 2 250 | ≈ 1 300 (estimado) | ≈ 7,7 | ≈ 19,2 | 10 / 5 | 20 | 5 / 7 | 19 | ≈ 0,20 USD |
| `compact` | 1 812 | ≈ 953 (≤ 1 208) | ≈ 10,5 | ≈ 26,2 | 10 / 5 | 20 | **5 / 10** | **20** (manda la cuenta) | ≈ 0,14 USD |
| `standard` | 4 276 | ≈ 1 701 (≤ 2 851) | ≈ 5,9 | ≈ 14,7 | 10 / 5 | 20 | 5 / 5 | 14 | ≈ 0,26 USD |
| `full` (default) | 4 805 | ≈ 1 884 (≤ 3 204) | ≈ 5,3 | ≈ 13,3 | 10 / 5 | 20 | **5 / 5** | **13** | **≈ 0,28 USD** |
| `full` + idoneidad (`fitChecks`) | 8 066 | ≈ 2 950 (≤ 5 378) | ≈ 3,4 | ≈ 8,5 | 10 / 5 | 20 | 3 / 3 | 8 | ≈ 0,45 USD |

Con más historia de eventos la solicitud crece: 1 contra 1 con 16 eventos mide 2 444 caracteres en `compact` (4 608 en `standard`, 5 459 en `full`), y 16 mobs contra 16 jugadores, 25 592 en `compact` (61 094 en `standard`, 71 888 en `full`); con `full` + idoneidad y 16 × 16 la guarda de tamaño rechaza la solicitud (supera los 64 000 tokens estimados), así que ese encuentro juega en local (`PREPARE_FAILED`) **sin pausar el servicio**: los demás encuentros siguen preguntando a Jev. Preparar la solicitud en el hilo del servidor cuesta, medido en local (p50), unos 17 µs para la solicitud de producción en `compact`, 188 µs para 16 × 16 en `compact`, 425 µs en `standard` y 475 µs en `full`, dentro de `decision.maxTickWorkMicros` (2 000 µs); `status` lo muestra como tramo `prepare`.

Cómo leerla:

- Las columnas "efectivo" redondean hacia abajo (un mob no se reparte). Con los defaults, en **un solo mundo** manda el tope de solicitudes por mundo (5 mobs aislados, con `full` y con `compact`); en varios mundos, con `full` manda el de tokens (5 mobs) y con `compact`, a la vez el de tokens y el global de solicitudes (10). El presupuesto reserva por el tamaño preparado del cuerpo con los caracteres por token aprendidos (1,3–2,0; con `full` la cuenta real midió ≈ 2,37), así que hasta que aprende puede admitir algo menos que la tabla.
- `decision.maxInputTokensPerMinute` es un **tope local** de ThePredict, no el límite de la cuenta (250 000 tokens/s y 1 200 solicitudes/min). **Subir el tope de tokens no basta:** hay que subir también `decision.maxRequestsPerMinute` y `decision.maxRequestsPerMinutePerWorld`, y ninguno puede pasar de las 1 200 solicitudes/min de la cuenta, que limitan a unos 20 mobs aislados sea cual sea el detalle.
- A plena carga, 600 000 tokens/min cuestan ≈ 1,51 USD/h y 1 500 000 ≈ 3,78 USD/h.
- **Los mobs que comparten encuentro gastan menos solicitudes:** un encuentro envía una sola solicitud por latido con una pregunta por mob, así que 3 mobs contra el mismo jugador gastan 60 solicitudes/min en total, no 180; en ese caso manda el tope de tokens, y cada mob añade sus preguntas (≈ 500–800 tokens en `compact`) pero no un estado nuevo.
- **Por qué `full` es el default** (decisión del usuario del 2026-09-30, sección 7.2): con las 60 etiquetas revisadas, `full` es el brazo que cumple la regla de M16.7 al umbral más bajo (0,45) y, entre las respuestas conservadas a 0,5, da un 94 % aceptables y un 72 % mejores; `compact` conserva menos (54 % a 0,5) aunque acierta de forma parecida. `compact` sigue disponible por perfil (`Timing.QuestionDetail: compact`) si el costo pesa más.

### 7.2 Umbral de confianza (`minimumConfidence`)

El default es **0,45** desde el re-banco de M16.10 (antes 0,5). Se eligió con la regla de M16.7: el menor umbral que, sobre escenarios distintos con etiquetas revisadas por el usuario, conserva al menos 30 escenarios, con al menos un 90 % aceptable, límite inferior de Wilson al 95 % de al menos 0,75 y como mucho un escenario prohibido. Con las 60 etiquetas revisadas y el detalle `full`, a 0,45 se conservan 41 de 48 escenarios informativos (85,4 %), un 90,2 % aceptables, Wilson 0,775 y ninguno prohibido. Es una muestra modesta (48 escenarios informativos, 2 repeticiones): se revisará con más evidencia del servidor. Un perfil puede fijar el suyo con `Timing.MinimumConfidence`: súbelo para un jefe cuyo error es caro (con un umbral más alto se descartan más respuestas y el mob juega más en local); `fitVeto` y `fitRescue` se calibrarán con la misma regla.

### 7.3 Capa por tick (M20)

Con `Timing.Cadence: reactive` (el default) y los topes de esta entrega, el peor caso publicado es **≈ 1 encuentro por mundo (300 / 200 ≈ 1,5)** y hasta **≈ 0,95 USD por mob y hora**. `standard` es el modo ahorro: unos **5** encuentros por mundo con el mismo latido de 20 ticks. Esas dos filas son el techo de solicitudes, no una medida de servidor.

Presupuesto de la capa, en `config.yml`: `decision.tickLayer.maxSessionsPerTick` 96, `maxMicros` 600 (la cota del paso es ese tope más 8 visitas sin cast más 1 cast), 8 rutas y 400 µs por tick. Una ruta puede pasar el tope de microsegundos; la siguiente espera. El coste real de una ruta corta en el servidor está **(pendiente de servidor)** (DT-49). MythicMobs puede calcular rutas fuera del hilo (`Options.AsyncPathfinding`, y en Paper `update-pathfinding-on-block-update`): si una ruta corta pasa de 100 µs en el hilo, el siguiente ajuste baja el tope a 4 rutas. No está medido aquí.

`decision.castThread: same_tick` mueve al hilo del servidor el tramo que, con `delay`, MythicMobs dejaría en un hilo asíncrono. Sin `delay` el cast ya corría en el hilo del servidor. El tick de impacto y el coste por cast están **(pendiente de servidor)** (sesión 20.2, DT-48). `prepare` sigue en el hilo del servidor. No existe la clave `decision.prepareOffThread` (errata E20-4): escribirla es un error de clave desconocida.

## 8. Grabación de escenarios y herramientas de pago

### 8.1 `debug.recordScenarios`

Opción de depuración para calibrar umbrales. Déjala en `false` en producción.

- Con `true` (y reinicio), cada decisión respondida por Jev se guarda como un archivo `plugins/ThePredict/scenarios/scenario-<AAAAMMDD-HHMMSS>-<nonce>-<nnnn>.json` (hora UTC del arranque y un nonce por arranque). Contiene el cuerpo exacto enviado (solo alias `player_n`, nunca nombres ni UUID), las preguntas, la respuesta, los tokens y etiquetas vacías; nunca la clave.
- **Como mucho 1 000 archivos por arranque.** Al llegar al límite, una sola línea: `Scenario recording reached its limit of 1000 files; later decisions are not recorded`.
- Escribe en un hilo propio con una cola de 64; si se llena, descarta la decisión, la cuenta y avisa una vez (`Scenario recording dropped a decision: its queue of 64 is full`). Un disco lento nunca retrasa las respuestas.
- **Disco:** unos 4,5 KB por archivo en un uno contra uno y 51 KB en el peor caso (encuentros de 16 mobs y 16 jugadores): **unos 51 MB por arranque en el peor caso** y unos 4,5 MB con uno contra uno.
- **Apagado:** con la grabación activa, cerrar el cliente de Jev puede tardar **unos 2 s** (0,5 s del executor, 0,5 s del cliente HTTP y hasta 1 s para vaciar la cola); lo que siga en cola se descarta y se cuenta.

### 8.2 Herramientas de pago (desde el código fuente, no en el servidor)

Dos tareas de Gradle llaman a la cuenta real y **nunca** forman parte del build normal:

| Tarea | Qué hace | Costo |
|---|---|---|
| `./gradlew liveJevCheck --no-daemon -PliveJev=true` (opcional `-Pcalls=50 -PspacingSeconds=10`) | `GET /v1/models` repetido: estado y latencia de red | Sin tokens |
| `./gradlew liveScenarioBench --no-daemon -PliveJev=true` (opcional `-Prepetitions=<n>` y `-PscenarioDir=<carpeta>`; desde M16 el banco etiquetado `src/test/resources/scenarios/bank` es la carpeta por defecto y admite `-Parms=A,B,C,D,E` (`B` por defecto), `-Pscenarios=core12\|core24\|all\|<ids>` (`core12` por defecto), `-PmaxCalls=<n>` (780 por defecto; 1 300 al reenviar cuerpos grabados de otra carpeta) y `-PmaxUsd=<USD>` (0,10 por defecto)) | Envía escenarios a Jev y mide latencia, tokens y la curva de confianza; desde M16 codifica cada escenario con el brazo pedido y guarda las respuestas crudas en `build/reports/m16/answers.jsonl` | Imprime llamadas, tokens estimados a 1,5 caracteres por token y costo antes de empezar y se niega por encima de `maxCalls` o de `maxUsd` (regla W7); la serie de 1 200 decisiones de M13 costó 0,0309 USD; el re-banco completo de M16 mide 720 llamadas, ≈ 1,18 M tokens de cota y ≈ 0,050 USD (techo 0,10 USD) |

**Doble puerta:** las dos se niegan sin `-PliveJev=true` y sin `TYPESAFE_API_KEY` en el entorno. Se ejecutan en el mismo shell tras cargar el entorno sin mostrarlo (`set -a; source .env; set +a`) y con `--no-daemon`, para que la clave no quede en un proceso de Gradle. El archivo `.env` nunca se imprime, copia ni comparte. Los informes se escriben en `build/reports/m13/` sin clave ni cabeceras.

**El JAR nunca lleva una clave.** `verifyPluginJar` rechaza el build si un recurso del JAR contiene algo con forma de clave (`TYPESAFE_API_KEY=<valor>`, `Bearer <token>`, `apiKey:` con valor) o un archivo `.env`. Esa búsqueda lee todas las entradas que no son `.class`; las clases se controlan de otra forma (por nombre y porque no puede haber en el JAR ninguna clase de las pruebas, ni las herramientas de pago ni las de medida). Una clave escrita en el código Java no la detectaría esa búsqueda: la regla W6 lo prohíbe y la revisión de código lo comprueba.

### 8.3 Registrador de duelos (`/thepredict duel`, M18)

Una función de diagnóstico, **apagada por defecto**, para grabar en tiempo real todo lo que deciden y hacen dos mobs que se enfrentan (paquete `13-duelo`, sesión 18 del paquete de pruebas). Sin grabación no cuesta nada: el motor no construye ningún evento y los listeners salen tras leer un campo. Probado en local y con el simulador de M18.7; en juego, **pendiente de servidor** (sesión 18).

- **Inicio:** `/thepredict duel start local-20261001 duel_red duel_blue duel 0 65 0 30`. La etiqueta cumple `[a-z0-9-]{1,40}` (sin barras ni puntos) y la carpeta queda dentro de `plugins/ThePredict/duels/`: `<etiqueta>-<UTC yyyyMMdd-HHmmss>/`. `facciónA` es el lado `red` y `facciónB` el `blue`. `skills=` fija las skills que cuentan como cast; por defecto, las choices `MYTHIC_SKILL` de los perfiles vinculados cuyo `Entities.Allow` nombra alguna de las dos facciones. Responde `Duel recording '<etiqueta>' started: red = <A>, blue = <B>, radius <r>, folder <carpeta>`.
- **Qué sigue:** hasta 8 duelistas vivos de las dos facciones dentro del radio, muestreados cada 5 ticks; una ronda empieza cuando hay uno de cada lado y termina cuando un lado no tiene ninguno (una muerte da la ronda al otro lado; una eliminación, como el `DuelTimeout` o `/mm mobs kill`, es empate). El brazo sale del tipo de cada lado (`tp` con perfil vinculado, `classic` sin él).
- **Archivos:** `decisions.jsonl` (líneas `planned`, `body`, `result`, `acceptance` y `local`, unidas por `encounter` y `sequence`; `local` usa `sequence` −1), `samples.csv`, `casts.csv`, `damage.csv`, `intents.csv`, `defense.csv`, `retreats.csv`, `threat.csv`, `outcomes.csv`, `rounds.csv` y `summary.md` (victorias con Wilson, tiempo hasta la muerte, daño, aciertos y fallos por skill, retiradas, guardias, tiempo por nivel, efecto por táctica, solicitudes, tokens y costo). Los CSV y `decisions.jsonl` llevan UUID de los duelistas (salida local); los cuerpos enviados a Jev, nunca.
- **Límites:** 4 h, 500 rondas, 128 MB por archivo y 512 MB por grabación. Al alcanzar uno, la grabación se detiene sola, escribe `summary.md` con `stopped_by: <límite>`, avisa al jugador que la empezó si está conectado y deja en la consola `Duel recording '<etiqueta>' stopped: <límite> reached`.
- **Escritura:** un solo hilo daemon `ThePredict-DuelWriter`, vivo solo mientras se graba, con una cola de 8 192 filas; si se llena, la fila se descarta y se cuenta (`rows dropped` en `duel status` y en `summary.md`). Al apagar el servidor, la grabación se detiene con 2 s de espera como máximo antes de cerrar el cliente HTTP.
- **Costo con clave:** `/thepredict duel status` muestra `requests <r>, input tokens <t>` acumulados de la grabación; a 0,042 USD por millón de tokens de entrada.

### 8.4 Registros locales y agregados (M21)

Lo que se guarda es local. Los UUID de mobs y de mundos pueden aparecer en el JSONL. A Jev solo siguen yendo alias (`player_N`, `entity_N`). `config_sha256` se calcula sin `apiKey`. Una línea no lleva la clave ni una cabecera `Authorization`.

| Qué | Dónde |
|---|---|
| Segmentos JSONL v1, uno por línea, sin espacios entre campos | `plugins/ThePredict/records/events/<aaaa-mm-dd, en UTC>/events-<run>-<índice>.jsonl` (y `.gz` al cerrar), con los cuerpos en `bodies-<run>-<índice>.jsonl` al lado |
| Copia de seguridad, export y el lock | Dentro de `records/`: `backups/`, `exports/`, `.lock` |
| Base de agregados | `plugins/ThePredict/aggregates.db`, al lado de `records/`, no dentro |

Retención: 14 días y 2 GB con los defaults. Nunca se borra el día en curso. Una copia cada `storage.backupEveryHours` horas (24 por defecto, una al día) guarda las últimas `storage.backupsKept` (7). Si el archivo no pasa `quick_check`, el arranque intenta la copia más nueva y, si no hay, deja los agregados en `FAILED` sin borrar la base. Cada arranque con registros empieza por una línea `run_start` con la versión del plugin, el modelo, `question_detail` (el `decision.questionDetail` en uso), `config_sha256`, el nivel de registros, la política de cuerpos y `answer_cache` (el `decision.answerCache` en uso). Un `stop` escribe `run_stop` con `clean: true`. Un corte brusco deja el aviso de cierre sucio; `records verify` comprueba la base **(pendiente de servidor, sesión 21.3)**.

La carpeta no debe estar en red: WAL no es seguro en NFS, CIFS, SMB ni sshfs. Si el almacén lo parece, `status` añade `network` en la línea `Store`. El driver es el `sqlite-jdbc` 3.49.1.0 que ya trae Paper; ThePredict no lo empaqueta. El aviso de acceso nativo (JEP 472) no debería salir si el servidor arranca con `Enable-Native-Access: ALL-UNNAMED`, que es lo que declara el paperclip. Confirmarlo en el servidor real es DT-51 **(pendiente de servidor, sesión 21.1)**. Sin driver, los agregados quedan apagados y el JSONL sigue.

Volumen estimado, no medido en servidor (DT-52, sesión 21.2): el plan cifra ≈ 47 MB/h sin comprimir con 5 jefes, `decisions` y `bodies: hash`. En local (`m21Measure`, 2026-10-06) `tap.verdicts` p99 fue 1,959 µs, por debajo de 20 µs, así que el default sigue en `decisions`. La misma pasada midió `tap.recordOffer` p99 en 8,833 µs (el presupuesto de 1 µs por evento no se cumple) y el vaciado de 1024 claves en 190,375 µs p99 (el presupuesto de 50 µs no se cumple). Esa ráfaga no sustituye la hora de juego.

`/thepredict status` añade el bloque `Storage` cuando el servicio está cableado: `State` (estado, causa, nivel, descartes, lotes en vuelo y fallidos) y `Store` (versión de SQLite, synchronous, journal, pausa por tamaño, aviso de red y fallo de registros). `records stats` repite el estado sin permiso extra. El resto de `/thepredict records` exige `thepredict.records`, responde `queued` y entrega el resultado después.

Fuera del servidor, con el proceso parado: `jq -c . plugins/ThePredict/records/events/*/*.jsonl` y, para la base, `duckdb plugins/ThePredict/aggregates.db "SELECT profile, windows FROM outcome_stats LIMIT 20"`. No hace falta abrir el servidor para leerlos.

## 9. Migración desde el esquema 1

La 0.0.1 **no tiene comando de migración**: la migración es manual y ThePredict nunca cambia tus archivos.

1. Al arrancar con archivos del esquema 1 (el maestro con la sección `provider` u otras claves del esquema 1, o perfiles con `Behaviors`, `Operations` o sin `Tactics`), ThePredict no los carga y escribe una propuesta en `plugins/ThePredict/migration/<AAAAMMDD-HHMMSS>/`: `AIConfig/config.yml.proposed`, `AIConfig/Profiles/<archivo>.proposed` y `report.md`. Si esos archivos siguen en la ubicación antigua (`plugins/ThePredict/config.yml` o `plugins/ThePredict/AI/`), la propuesta es la misma y ya usa las rutas nuevas (sección 2.1). La consola dice `Migration proposal: review <informe> and the .proposed files next to it, then copy them into plugins/MythicMobs/AIConfig/ by hand. No file was changed and no query was enabled.`; `status` muestra `Migration pending: review …` y el aviso al entrar, `a migration is pending`.
2. La propuesta se calcula en cada arranque mientras siga habiendo esquema 1, y es la misma cada vez: si ya existe una carpeta con una propuesta idéntica, no se escribe nada nuevo y se reutiliza esa carpeta (solo un cambio en tus archivos crea una carpeta nueva).
3. `report.md` lista los archivos propuestos, las decisiones de conversión y lo que necesita revisión manual, y termina con los pasos para aplicarla: parar el servidor y guardar una copia; revisar cada decisión; copiar cada `.proposed` dentro de `plugins/MythicMobs/AIConfig/` quitando `.proposed` (el maestro a `config.yml` y cada perfil a `Profiles/`); poner `TYPESAFE_API_KEY` (la clave del proveedor anterior nunca se copia, porque no es una clave de TypeSafe); arrancar y ejecutar `/thepredict status`. No borres la ubicación antigua hasta ver que `status` ya no pide la propuesta.
4. Qué convierte el migrador: `provider` se sustituye por `jev` con `jev-1.13.0` y la clave vacía; los límites con equivalente se trasladan si caben en los rangos nuevos (por ejemplo `decision.maxConcurrentRequests` pasa a `decision.maxInFlightRequests`, `profileDefaults.decisionIntervalTicks` a `minimumDecisionIntervalTicks`, `profileDefaults.lowHealthRatio` a `criticalHealthRatio`); las claves sin equivalente se listan con el motivo; cada choice de un perfil pasa a una táctica sin movimiento que conserva sus restricciones; cada `Operation` pasa al contexto atajo equivalente (`idle`, `attacking_phase`, `tracking_enemy`, `low_health_phase`), con más prioridad las anteriores, y `DirectChoice` pasa a `DirectTactic`. Las combinaciones ambiguas quedan para revisión manual.
5. Después de migrar, añade movimientos a las tácticas (la conversión las deja sin movimiento) siguiendo la [guía del autor](Guia-del-autor.md).

## 10. Desactivar y apagar

- **Un mob:** `Predictor: false` en MythicMobs y `/mm reload`. ThePredict cierra la sesión, quita sus metas y MythicMobs vuelve a aplicar las del mob (`reapplyAIGoalSelectors`) **(pendiente de servidor, DT-04)**. Sacar el perfil de `plugins/MythicMobs/AIConfig/Profiles/` y `/mm reload` tiene el mismo efecto.
- **Todo ThePredict:** quitar el JAR y reiniciar. Al apagar, ThePredict detiene su tarea, cancela las solicitudes en vuelo, pone todas las condiciones `predictor*` en falso, libera las metas de hasta 200 sesiones, para una grabación de duelo (como mucho 2 s), cierra los registros y los agregados con el lote final (como mucho 3 s) y cierra el cliente de Jev el último. El apagado puede tardar unos 2 s con la grabación de escenarios activa (sección 8.1) y hasta 3 s más si el escritor de registros sigue vaciando.
- Sin clave, nada sale del servidor: los mobs vinculados pelean con el modo local de su perfil.
