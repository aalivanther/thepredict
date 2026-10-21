# M01. Verificación nativa en servidor

> Estado: Local listo — servidor pendiente · Entrega: A · Depende de: — · Desbloquea: M02 (parcial), M03, M04, M05 · Diseño: [plan §2, §3 y §13](../Plan-Jev-y-combate-tactico.md) · Reglas: [00](00-Arquitectura-y-reglas.md) · Dominio: [01](01-Modelo-de-dominio.md)

## Objetivo

Convertir la evidencia estática del plan §2, obtenida con `javap` sobre MythicMobs Premium 5.13.1-SNAPSHOT y Paper 26.2 build 129, en evidencia observada en un servidor real. Cada pregunta termina con una decisión registrada. Lo que no se confirme deja activa la alternativa documentada en el plan §13, y el módulo afectado la usa sin esperar.

## Alcance

### Incluye

- Un servidor de verificación reproducible por otra persona.
- Un comando de depuración mínimo que fija intenciones locales, instala conjuntos de metas de prueba y lista metas, sin Jev ni costo.
- Pruebas de registro del vocabulario, instalación de metas, semántica de metas condicionales, metas de Paper, hilos, señales, placeholders y trigger.
- El registro de decisiones técnicas que consumen M03, M04, M05, M06 y M10.

### No incluye

- Jev, consumo de API o credenciales.
- La implementación completa del vocabulario (M04) y de la propiedad de la IA (M05). M01 escribe la primera versión mínima de sus piezas en los paquetes definitivos; no hay código descartable en paquetes temporales.
- Pruebas de carga y latencia (M13) y Folia.

## Base existente

| Archivo | Acción |
|---|---|
| `src/main/java/akyrion/thepredict/ThePredictStatusCommand.java` | Recibe el subcomando `debug` de forma provisional; M12 lo traslada a `admin` |
| `src/main/resources/plugin.yml` | Añade el permiso `thepredict.debug` con `default: false` |
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/MythicSkillExecutor.java` | Referencia del trigger `THEPREDICT` que M01.8 evalúa |
| `VersionsOfJars/paper-26.2-129.jar` y `VersionsOfJars/MythicMobsPremium-5.13.1-SNAPSHOT.jar` | Binarios de referencia; siguen fuera de Git (`.gitignore`) |
| `DocumentacionParaDesarrollar/archive/Verificacion-servidor-0.0.1.md` | Matriz anterior con OpenRouter; M01 la complementaba con los casos nativos. Archivada en M12 (M12.8) |

## Subpuntos

### M01.1 Servidor de verificación reproducible

- **Propósito:** que todas las pruebas de M01, M05 y M13 usen el mismo entorno y que otra persona pueda repetirlas.
- **Diseño:**
  - Carpeta fuera del repositorio, por ejemplo `~/ThePredict-verificacion/26.2-129/`. Nada de esa carpeta entra en Git.
  - Contenido: `paper-26.2-129.jar` (Paperclip), `plugins/MythicMobsPremium-5.13.1-SNAPSHOT.jar` y `plugins/ThePredict-0.0.1.jar` generado con `./gradlew clean build`.
  - Arranque con Java 25 explícito, porque el `java` por defecto de la máquina de desarrollo es 21: `…/temurin-25.jdk/Contents/Home/bin/java -Xms2G -Xmx2G -jar paper-26.2-129.jar --nogui`.
  - Paperclip descarga el servidor de Mojang y verifica su hash declarado en `META-INF/download-context` antes de parchearlo. La salida del primer arranque se guarda como evidencia.
  - El administrador acepta el EULA de Minecraft editando `eula.txt`. Ningún script ni agente lo acepta en su nombre.
  - Mundo plano de prueba (`level-type=minecraft:flat`) con una arena marcada: un pasillo, una pared, una esquina, un desnivel de 2 bloques y una zona con agua.
  - Pack de prueba en `plugins/MythicMobs/Mobs/ThePredictVerification.yml`:
    - `TPV_Zombie`: `Type: ZOMBIE`, `Predictor: true`, sin `AIGoalSelectors`.
    - `TPV_ZombieCustomAI`: igual, con `AIGoalSelectors` propios que empiezan por `clear`.
    - `TPV_Skeleton`: `Type: SKELETON`, `Predictor: true`, para `bowAttackConditional`.
    - `TPV_Piglin`: `Type: PIGLIN`, especie con `Brain`, para confirmar su exclusión del control de movimiento.
  - Registro `versions.md` en esa carpeta: salida de `/version`, `/version MythicMobs`, `java -version` y `shasum -a 256` de cada JAR.
- **Archivos:** ninguno del servidor en el repositorio. El procedimiento paso a paso, con el pack de prueba, está en [M01-Guion-de-servidor.md](M01-Guion-de-servidor.md); los resultados, en [M01-Registro-de-decisiones.md](M01-Registro-de-decisiones.md) (M01.9).
- **Reglas:** A8, W3.
- **Pruebas:** dos arranques consecutivos sin errores de ThePredict ni de MythicMobs; `/thepredict status` en ambos.
- **Aceptación:**
  - [ ] Carpeta creada fuera del repositorio con los tres JAR y el pack de prueba.
  - [ ] `versions.md` con versiones y SHA-256.
  - [ ] EULA aceptada por el administrador y registrada como tal.
  - [ ] Dos arranques limpios documentados.

### M01.2 Instrumentación de depuración

- **Propósito:** fijar intenciones y observar metas sin Jev, para todas las pruebas de M01 y para la entrega B.
- **Diseño:**
  - Permiso `thepredict.debug`, `default: false`: ni siquiera los operadores lo tienen si no se concede.
  - Resolución del mob: `nearest` exige un jugador y elige el `ActiveMob` vinculado más cercano en 32 bloques; `<uuid>` funciona también en consola y usa `MythicBukkit.inst().getMobManager().getActiveMob(uuid)`.
  - `/thepredict debug intent <nearest|uuid> <movement> [ticks]`: publica una `IntentView` con `level = LOCAL`, `movement` indicado, `tactic = debug_<movimiento en minúsculas>` y vencimiento a `ticks` (200 por defecto, máximo 6000) convertido a nanosegundos monotónicos.
  - `/thepredict debug release <nearest|uuid>`: retira la vista; todas las condiciones pasan a falso.
  - `/thepredict debug goals <nearest|uuid>`: lista `MobGoals#getAllGoals(mob)` y `getRunningGoals(mob)` con `getKey().getNamespacedKey()` y `getTypes()`, más `Pathfinder#hasPath()`.
  - `/thepredict debug install <nearest|uuid> <preset>`: instala un conjunto de líneas de prueba (M01.4 y M01.5) con `VolatileAIHandler`.
  - `/thepredict debug trace <on|off>`: activa el registro de hilo de las condiciones (M01.7), con límite de una línea por contexto y segundo.
- **Adelanto de dominio:** M01 crea, siguiendo 01 exactamente, `MobKey`, `TacticId`, `Movement`, `ControlLevel` e `IntentView`, e `IntentBoard` en `engine`. M02 y M04 los completan con todas sus pruebas; no se crean versiones alternativas.
- **Archivos:**
  - Modificar `ThePredictStatusCommand.java` (subcomando provisional) y `plugin.yml`.
  - Crear `domain/MobKey.java`, `domain/TacticId.java`, `domain/Movement.java`, `domain/ControlLevel.java`, `domain/IntentView.java` y `engine/IntentBoard.java` en su forma mínima.
- **Reglas:** T1, C4, C8, I1, I4.
- **Pruebas:** prueba unitaria del análisis de argumentos (movimiento inválido, ticks fuera de rango, UUID mal formado); prueba en servidor de cada subcomando en jugador y en consola.
- **Aceptación:**
  - [ ] Sin el permiso, el comando responde con un error y no cambia nada.
  - [ ] `intent` y `release` cambian el resultado de `predictormove` en el siguiente ciclo de metas.
  - [ ] `goals` muestra claves y tipos de todas las metas, incluidas las de MythicMobs y Paper.

### M01.3 Registro del vocabulario

- **Propósito:** confirmar cómo y cuándo MythicMobs resuelve las condiciones, la mecánica y los placeholders de ThePredict.
- **Diseño:**
  - Se registra `predictormove` con un listener de `MythicConditionLoadEvent` que llama a `event.register(...)` con una instancia construida por ThePredict y dependencias inyectadas (regla C15). `CustomComponentRegistry` no se prueba: instancia las clases por reflexión con constructores fijos, incompatibles con D8.
  - Casos: primer arranque con un mob que usa la condición en `AIGoalSelectors` y en `Conditions` de una skill; `/mm reload`; nombre escrito como `predictormove`, `PredictorMove` y `PREDICTORMOVE`.
  - Placeholders: registro de `caster.predictor.tactic` con `MythicBukkit.inst().getPlaceholderManager().register(...)` al habilitar ThePredict, probado en `message{m="<caster.predictor.tactic>"}` antes y después de `recheckForPlaceholders()`, y tras `/mm reload`, que emite `MythicPlaceholdersLoadEvent`.
  - Evidencia: ausencia o presencia de errores de condición desconocida en la consola de MythicMobs y el valor observado en juego.
- **Archivos:** `adapter/server/vocabulary/PredictorMoveCondition.java` en su forma mínima y un listener de registro provisional.
- **Reglas:** A1, D8, T3, I8.
- **Pruebas:** en servidor, cada caso dos veces.
- **Aceptación:**
  - [ ] Se sabe si la resolución diferida de un tick basta en el primer arranque, con cada mecanismo.
  - [ ] Se sabe si hace falta `recheckForPlaceholders()` y cuándo.
  - [ ] Se sabe si las mayúsculas del nombre importan.
  - [ ] Hay una decisión DT que confirma que los eventos de carga cubren el primer arranque y la recarga, o documenta el caso que no cubren.

### M01.4 Instalación de metas con `VolatileAIHandler`

- **Propósito:** confirmar que ThePredict puede sustituir el conjunto de metas de un mob vinculado y mantenerlo instalado.
- **Diseño:**
  - Llamada: `MythicBukkit.inst().getVolatileCodeHandler().getAIHandler().addPathfinderGoals(entity, lines)` con `lines` empezando por `0 clear`.
  - Momentos: un tick después de `MythicMobSpawnEvent` y un tick después de `EntitiesLoadEvent`, buscando `getActiveMob(uuid)` para cada entidad cargada.
  - Casos:
    1. Spawn de `TPV_Zombie` (sin `AIGoalSelectors`).
    2. Spawn de `TPV_ZombieCustomAI`: ¿quedan solo las metas de ThePredict?
    3. Descarga y carga del chunk: las metas no se guardan; ¿MythicMobs reinstala las suyas y ThePredict las sustituye otra vez?
    4. `/mm reload` con mobs vivos: ¿MythicMobs reinstala metas en mobs existentes?
    5. `MythicMob#reapplyAIGoalSelectors(activeMob)` con y sin `AIGoalSelectors` en el mob.
    6. `clear` no debería tocar el selector de objetivos: comparar `getAllGoals(mob, GoalType.TARGET)` antes y después.
    7. Claves que Paper asigna en `getRunningGoals` a las metas de MythicMobs.
    8. Costo: `System.nanoTime()` alrededor de la instalación para 1 y 30 mobs.
- **Archivos:** preset de líneas en el subcomando `debug install`; `adapter/server/goals/GoalOwnership.java` en su forma mínima (solo instalar).
- **Reglas:** A1, A5, T1.
- **Pruebas:** en servidor, los ocho casos con la salida de `debug goals` antes y después.
- **Aceptación:**
  - [ ] Los casos 1–6 tienen resultado observado y decisión.
  - [ ] Las claves del caso 7 están copiadas literalmente en el registro.
  - [ ] El costo del caso 8 está medido y define el valor inicial de `decision.maxInstallsPerTick` (M03).

### M01.5 Semántica de metas condicionales

- **Propósito:** fijar el significado real de los parámetros antes de que M05 genere líneas y M03 valide perfiles.
- **Diseño:** cada caso se ejecuta con `debug install` y `debug intent`, con un jugador inmóvil y otro que se acerca y se aleja. Se toman muestras de distancia cada 10 ticks durante 30 s y se cuentan cambios de sentido para medir oscilación.

  | Meta | Parámetros a probar | Pregunta |
  |---|---|---|
  | `fleeConditional` | `distance`, `speed`, `safespeed`; `targetconditions` con `isTarget` (alias verificado de la condición `target`) o `isplayer` | ¿Huye solo del objetivo o de cualquier entidad cercana? ¿Qué expresión la limita? |
  | `moveWithinConditional` | `minrange`, `range`, `maxrange` | ¿Mantiene la banda o solo se acerca? |
  | `moveTowardsTargetConditional` | `minrange`, `maxrange` | ¿`maxrange` es la distancia máxima para activarse? |
  | `goToSpawnLocation` | `maxrange`, `minrange`, `speed`, `droptarget` | ¿Cuándo vuelve a la arena y si suelta el objetivo? |
  | `lookAtTarget` | sin parámetros | ¿Corre en paralelo con metas solo de `MOVE`? |

  La banda nativa se prueba como `fleeConditional{distance=5}` más `moveWithinConditional{range=9}` y se mide cuántas veces cruza cada frontera.
- **Archivos:** presets de prueba en el subcomando `debug install`.
- **Reglas:** A1, A6.
- **Pruebas:** en servidor, una fila del registro por caso con muestras resumidas.
- **Aceptación:**
  - [ ] Cada parámetro tiene significado confirmado o una alternativa documentada.
  - [ ] Existe una expresión confirmada para limitar la huida al objetivo, o la decisión de usar `isplayer`.
  - [ ] Hay valores iniciales de banda sin oscilación visible para M05.

### M01.6 Metas de Paper junto a las de MythicMobs

- **Propósito:** confirmar que `hold`, `flank` y `owner` conviven con las metas de MythicMobs en el mismo selector.
- **Diseño:**
  - Prototipos en `adapter.server.goals`: `HoldGoal` con `EnumSet.of(GoalType.MOVE)`, que detiene la navegación mientras la vista pida `HOLD`; `FlankGoal` con `MOVE`, que lleva el mob a un punto lateral con `Pathfinder.findPath` y `moveTo` como máximo cada 10 ticks; `OwnerMarkerGoal`, que nunca se activa.
  - Se añaden con `Bukkit.getMobGoals().addGoal(mob, priority, goal)` después de las líneas de MythicMobs, con las prioridades del plan §3.
  - Comprobaciones: `hold` bloquea el ataque base (`MOVE` en conflicto) pero no `lookAtTarget`; al retirar la vista, la IA base arranca en ≤2 ticks; `flank` alcanza el lateral en la arena.
  - Se documenta con evidencia por qué se descartan `doNothing` (declara `MOVE_LOOK` y el mob deja de mirar) y `Mob#setAware(false)` (detiene navegación y metas).
  - ¿Acepta Paper un `getTypes()` vacío para `owner`? Alternativa: `GoalType.UNKNOWN_BEHAVIOR`.
- **Archivos:** `adapter/server/goals/HoldGoal.java`, `FlankGoal.java` y `OwnerMarkerGoal.java` en su forma mínima.
- **Reglas:** A1, A6, I10.
- **Pruebas:** en servidor, con `debug trace` para contar ticks entre la retirada de la vista y el arranque de la meta base.
- **Aceptación:**
  - [ ] `HOLD` mantiene al mob quieto y mirando al objetivo.
  - [ ] Ninguna meta base arranca mientras la vista controla.
  - [ ] La cesión de control a la IA base tarda como máximo 2 ticks.
  - [ ] Hay decisión sobre los tipos de `owner`.

### M01.7 Hilos y ruta asíncrona

- **Propósito:** confirmar la regla T3 y la compatibilidad de `Options.AsyncPathfinding`.
- **Diseño:**
  - Una condición de prueba registra `Thread.currentThread().getName()` una vez por contexto y segundo cuando `debug trace` está activo.
  - Contextos: `mobconditions` de `moveTowardsTargetConditional`; `targetconditions` y `mobconditions` de `fleeConditional`, que usa `AsyncAvoidEntityGoal` y `AsyncEntityScan`; `Conditions` de una skill con `~onTimer`; una skill que MythicMobs ejecute de forma asíncrona, si el pack de prueba puede provocarla.
  - Se repiten los casos de M01.5 con `Options.AsyncPathfinding: true` en el mob de prueba.
- **Archivos:** solo la condición de prueba y el interruptor de trazas.
- **Reglas:** T3, C12.
- **Pruebas:** en servidor; los nombres de hilo se copian al registro.
- **Aceptación:**
  - [ ] Se conoce el hilo de cada contexto.
  - [ ] Hay decisión sobre `Options.AsyncPathfinding` para mobs vinculados.

### M01.8 Señales, placeholders y trigger

- **Propósito:** fijar formatos antes de documentarlos a los autores.
- **Diseño:**
  - Señales: `ActiveMob.signalMob(entity, "PREDICTOR_HOLD")` con el propio mob como entidad disparadora, frente a skills con `~onSignal:PREDICTOR_HOLD` y `~onSignal:predictor_hold`. Se registra si la comparación distingue mayúsculas.
  - Placeholders: nombres `<caster.predictor.tactic>`, `movement`, `level`, `confidence` y `state`, con y sin sesión.
  - Trigger `THEPREDICT`, creado hoy con `SkillTrigger.create("THEPREDICT")`: se confirma que `executeIfUsable` con esos metadatos respeta condiciones, targeters y cooldown de la skill. Se decide si se conserva el nombre.
- **Archivos:** skills de prueba en el pack de verificación.
- **Reglas:** I8, I9.
- **Pruebas:** en servidor.
- **Aceptación:**
  - [ ] Formato de señal confirmado.
  - [ ] Nombres de placeholders confirmados.
  - [ ] Decisión sobre el trigger `THEPREDICT`.

### M01.9 Registro de decisiones técnicas

- **Propósito:** que cada módulo consuma decisiones con evidencia y no suposiciones.
- **Diseño:** el archivo `M01-Registro-de-decisiones.md` se crea al implementar M01. Cada entrada `DT-nn` tiene: pregunta, contexto, evidencia (versiones, pasos, salida de consola), decisión, consecuencia, módulos afectados y estado (`Confirmada`, `Alternativa aplicada` o `Abierta`).

  | Pregunta (plan §13 y subpuntos) | Subpunto | Desbloquea |
  |---|---|---|
  | Condiciones del mob en cada meta condicional | M01.5 | M05 |
  | Registro de extensiones en primer arranque y recarga | M01.3 | M04 |
  | Registro por eventos de carga en primer arranque y recarga | M01.3 | M04 |
  | Restauración con `reapplyAIGoalSelectors`, con y sin `AIGoalSelectors` | M01.4 | M05, M10 |
  | Reinstalación tras carga, recarga o skills que cambian la IA | M01.4 | M05, M06 |
  | Clave de las metas de MythicMobs en `getRunningGoals` | M01.4 | M05, M12 |
  | Significado de rangos y forma de limitar la huida | M01.5 | M03, M05 |
  | Navegación de Paper 26.2 para `flank` y tipos de `owner` | M01.6 | M05 |
  | Hilo de evaluación de condiciones | M01.7 | M04 |
  | `Options.AsyncPathfinding` con metas condicionales | M01.7 | M03, M05 |
  | Comparación de `~onSignal` y nombres de placeholders | M01.8 | M04, M10 |

  Las preguntas de latencia de Jev y umbrales pertenecen a M08 y M13.
- **Archivos:** [M01-Registro-de-decisiones.md](M01-Registro-de-decisiones.md), creado con plantilla, índice y una entrada por pregunta (DT-00 a DT-19), todas `Abierta` hasta ejecutar el guion.
- **Reglas:** W2, W3.
- **Pruebas:** revisión cruzada: cada fila de la tabla tiene una entrada `DT`.
- **Aceptación:**
  - [ ] Todas las filas tienen entrada con estado.
  - [ ] Cada entrada `Abierta` indica la alternativa que usa su módulo.

## Riesgos y decisiones abiertas

- El servidor alojado puede no estar disponible. M02, M03, M07 y M08 avanzan; M04 y M05 usan las alternativas del plan §13 hasta cerrar M01.
- MythicMobs es una versión SNAPSHOT: una actualización puede cambiar comportamientos. Las huellas SHA-256 del registro identifican la build probada.
- Algunos casos (skills asíncronas, `/mm reload` con muchos mobs) pueden no reproducirse con el pack mínimo; quedan como `Abierta` con su alternativa.
- El subcomando `debug` convive con el comando actual hasta M12; su permiso evita exponerlo por error.

## Terminado

- [ ] M01.1 a M01.9 con aceptación cumplida o alternativa registrada.
- [ ] `M01-Registro-de-decisiones.md` completo y enlazado desde M03, M04 y M05.
- [x] Las piezas mínimas creadas en `domain`, `engine` y `adapter.server` cumplen 00 y 01, con pruebas unitarias donde no requieren servidor.
- [x] `./gradlew clean build javadoc` pasa.

## Estado de implementación

> **Local listo — servidor pendiente (2026-09-29).** Todo lo que se puede hacer sin servidor está hecho y probado. Ninguna aceptación que exige juego está marcada: el servidor de verificación necesita que el administrador acepte el EULA y un jugador real. No hay evidencia de servidor inventada.

> **Nota de M12 (2026-09-29):** las herramientas provisionales de esta tabla se retiraron o trasladaron en M12: el comando pasó a `admin.ThePredictCommand` con `debug intent <mob> <táctica> [ticks]`, `debug release` y `debug goals` (M12.3); `DebugCommandHandler`, `DebugProfileGoals`, `DebugCapture`, `PredictorMobLocator`, `ConditionTrace`, `predictorprobe`, `TriggeredSkillCast`, los presets y `DebugAutoInstaller` se eliminaron. El [guion](M01-Guion-de-servidor.md) se reescribió con los comandos que quedan y conserva los números de paso. Ver el estado de implementación de [M12](M12-Diagnostico-instalacion-retiro-OpenRouter.md#estado-de-implementación).

### Implementado

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…`) | Forma |
|---|---|---|
| M01.2 dominio (W5) | `domain/MobKey`, `TacticId`, `Movement`, `ControlLevel`, `IntentView`; `engine/IntentBoard` | Definitiva mínima, según 01; M02 y M04 completan |
| M01.2 comando | `ThePredictStatusCommand` (subcomando `debug`), `DebugCommandHandler`; `admin/DebugCommandParser`, `DebugRequest`, `DebugParseResult`, `MobSelector`; `adapter/server/binding/PredictorMobLocator`; `plugin.yml` (`thepredict.debug`, `default: false`) | Provisional |
| M01.3 vocabulario | `adapter/server/vocabulary/PredictorMoveCondition` (mínima); `ProvisionalVocabularyListener` (eventos de carga, C15); `PredictorPlaceholders`; `PredictorProbeCondition`, `PredictorProbeMechanic`, `ProbeLabels` | `PredictorMoveCondition` definitiva mínima; el resto provisional |
| M01.4 instalación | `adapter/server/goals/GoalOwnership` (solo instalar y `reapply`), `DebugGoalPresets`, `DebugGoalPreset`, `InstallMeasurement`, `GoalInspector`; `adapter/server/events/DebugAutoInstaller` | Provisional |
| M01.6 metas de Paper | `adapter/server/goals/GoalKeys`, `HoldGoal`, `FlankGoal`, `OwnerMarkerGoal` | Prototipos; M05 los completa |
| M01.7 hilos | `adapter/server/vocabulary/ConditionTrace` y el interruptor `debug trace` | Provisional |
| M01.8 trigger | `adapter/server/execution/TriggeredSkillCast` y `debug cast` | Provisional |
| M01.1 y M01.9 | [M01-Guion-de-servidor.md](M01-Guion-de-servidor.md) y [M01-Registro-de-decisiones.md](M01-Registro-de-decisiones.md) | Documentación |
| Raíz | `ThePredictPlugin` construye las piezas en `enableVerificationTools()` y vacía `IntentBoard` en `onDisable` (T7). El camino de OpenRouter no cambia (W5) | — |

Subcomandos: `intent`, `release`, `goals`, `install`, `reapply`, `signal`, `cast`, `trace`, `autoinstall`. Presets: `generated`, `paper`, `paper_look`, `paper_unknown`, `spawn`, `spawn_drop`, `flee`, `flee_target`, `flee_player`, `within`, `towards`, `band`, `ranged`, `trace`, `no_priority`.

Pruebas nuevas (72; la suite pasa de 67 a 139, todas verdes): `domain/MobKeyTest`, `TacticIdTest`, `MovementTest`, `IntentViewTest`; `engine/IntentBoardTest` (incluye lectores y escritor concurrentes sin `sleep`); `admin/DebugCommandParserTest`; `adapter/server/vocabulary/PredictorMoveConditionTest`, `ConditionTraceTest`, `PredictorPlaceholdersTest`, `ProvisionalVocabularyListenerTest`, `ProbeLabelsTest`; `adapter/server/goals/HoldGoalTest`, `FlankGoalTest`, `DebugGoalPresetsTest`, `GoalInspectorTest`, `OwnerMarkerGoalTest`; cuatro casos nuevos en `ThePredictStatusCommandTest` (sin permiso, con permiso, argumentos inválidos y consola sin permiso, DT-17). `./gradlew clean build javadoc --offline` pasa sin avisos de compilación ni de Javadoc.

### Pendiente de servidor

- M01.1: carpeta, `versions.md`, EULA aceptada por el administrador y dos arranques limpios (guion §1).
- M01.2: permiso, `intent`/`release` sobre `predictormove` en el siguiente ciclo de metas, `goals` con metas de MythicMobs y Paper, uso en consola (guion §2).
- M01.3 a M01.8: todos los casos del guion §3 a §8.
- M01.9: las 20 entradas del registro están `Abierta`, cada una con la alternativa que usa su módulo.

### Desviaciones del documento y motivo

| Desviación | Motivo |
|---|---|
| Paquete `admin` creado con el analizador puro del comando | Permite probar el análisis sin Bukkit (P6) y ya está en su paquete final (D6) |
| `DebugCommandHandler` en la raíz, con 249 líneas (C10 recomienda ~200) | Une `engine` y varias capacidades de `adapter.server`, que `admin` no puede importar (D6, D7). Es provisional: M12 lo reparte entre `admin` y los puertos |
| Subcomandos extra `reapply`, `signal`, `cast`, `autoinstall`; `release` informa las metas `MOVE` en curso durante 4 ticks | Sin ellos no hay forma, sin Jev ni costo, de responder M01.4 caso 5 y momentos de instalación, M01.8 (señales y trigger) ni medir la cesión de ≤2 ticks de M01.6 |
| Sonda `predictorprobe` (condición siempre verdadera y mecánica) en lugar de trazar `predictormove` | Mide el hilo de cualquier contexto sin alterar su resultado y responde la pregunta de M04 sobre `INoTargetSkill` (DT-14). Con la traza apagada cuesta una lectura volátil; encendida escribe como máximo una línea por etiqueta y segundo con el logger de Java, nunca con Bukkit |
| Placeholders registrados ya en M01 con `Placeholder.meta`, obsoleta para eliminación | M01.3 y M01.8 deben probar sus nombres. Su sustituto recomendado rompe D8; decisión en DT-13 |
| Reloj como `LongSupplier` (`System::nanoTime`) en lugar de `engine.ServerClock` | W5 limita los tipos de M01 a los seis listados |
| Métodos añadidos a los tipos de 01: `TacticId.NONE`, `DEBUG_PREFIX`, `debug(Movement)`, `isReserved()`; `Movement.parse`; `IntentBoard.size()` | Implementan las reservas de 01 §2 y la lectura de argumentos sin distinguir mayúsculas de M04; no son tipos nuevos |
| Generación fija 0 en intenciones y metas de depuración; "vinculado" significa `Predictor: true` | `SessionRegistry` llega en M06 y el binder nuevo en M03 |
| `FlankGoal` sin comprobación de escudo ni umbrales de movimiento y giro | Solo lo necesario para verificar la navegación de Paper en M01.6; M05 completa la meta. El intervalo de replanificación ya se cuenta en ticks de servidor (`Bukkit::getCurrentTick`, C6) |
| Guion en archivo propio y pack con `TPV_ZombieAsync` y perfiles de IA mínimos | El guion es largo y lo reutilizan M05.7 y M13; el mob asíncrono evita editar el pack entre casos; los perfiles evitan el error del binder actual con `Predictor: true` sin perfil |
| La consola siempre puede usar `debug` | Bukkit no concede un permiso `default: false` a la consola; decisión en DT-17 |

### Correcciones tras la auditoría (2026-09-29)

| Hallazgo | Corrección |
|---|---|
| C4: `Optional` como parámetro en `TriggeredSkillCast.cast` y su método privado | `cast(mob, skill)` sin disparador y `castAt(mob, skill, Player)`; un método privado común recibe los metadatos ya construidos |
| C4: `DebugAutoInstaller.setPreset(Optional)` | `enable(DebugGoalPreset)` y `disable()` |
| C4: `DebugCommandHandler.setAutoInstall(CommandSender, Optional)` | Ramas separadas `enableAutoInstall` y `disableAutoInstall`; el componente `Optional` del record `SetAutoInstall` se mantiene |
| `ProbeLabels.normalize` aceptaba `null` | Rechaza `null` con `Objects.requireNonNull`; la prueba lo comprueba |
| C7: `reapply`, `signal` y `cast` no capturaban errores | Capturan `RuntimeException` y la comunican al remitente como `install` |
| C8: una línea de registro por mob fallido en `DebugAutoInstaller` | Los fallos se resumen en la única línea por tick (`failed=N firstFailure=<tipo>`) |
| Sin prueba de la excepción de consola (DT-17) | Nueva prueba: la consola usa `debug` sin consultar el permiso |
| C6: replanificación de `FlankGoal` cada 500 ms en nanosegundos | Cada 10 ticks de servidor, con la regla pura `shouldReplan` probada (incluido el desbordamiento del contador) |

Con estas correcciones, las piezas mínimas de `domain`, `engine` y `adapter.server` cumplen 00 y 01 (casilla de "Terminado" marcada). `./gradlew clean build javadoc --offline` pasa: 139 pruebas, sin fallos ni avisos.

