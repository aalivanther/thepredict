# M04. Vocabulario de MythicMobs

> Estado: Local listo — servidor pendiente (2026-09-29; ver "Estado de implementación") · Entrega: B · Depende de: M02, M03 · Desbloquea: M05, M10 · Diseño: [plan §3 y §7](../Plan-Jev-y-combate-tactico.md) · Reglas: [00](00-Arquitectura-y-reglas.md) · Dominio: [01](01-Modelo-de-dominio.md)

## Objetivo

Que ThePredict se use con la sintaxis de MythicMobs: condiciones para gobernar metas y skills, una mecánica para pedir reevaluación desde triggers nativos, placeholders para mostrar la táctica y señales para telegrafiarla. Todo el vocabulario lee una vista inmutable de la intención y es seguro fuera del hilo principal.

## Alcance

### Incluye

- `IntentBoard` y `ContextSignals` en `engine`.
- Condiciones `predictorcontrol`, `predictormove` y `predictortactic`.
- Mecánica `predictordecide`.
- Placeholders `<caster.predictor.*>`.
- Registro del vocabulario en el primer arranque y en cada recarga.
- Formato y semántica de las señales `PREDICTOR_<TACTIC>`.
- Material de referencia para la guía de autores (M14).

### No incluye

- Decidir cuándo cambia una intención (M10) o qué hace el coordinador con las señales encoladas (M09).
- Instalar metas (M05).
- La mecánica `predictorprofile`, que queda fuera de la 0.0.1.

## Base existente

| Archivo | Acción |
|---|---|
| `engine/IntentBoard.java` (mínimo, de M01) | Se completa |
| `adapter/server/vocabulary/PredictorMoveCondition.java` (mínimo, de M01) | Se completa |
| Listener de registro provisional de M01 | Se sustituye por `VocabularyRegistrar` |
| `adapter/mythicmobs/MythicSkillExecutor.java` | Sin cambios; M10 lo traslada |
| `M01-Registro-de-decisiones.md` | Fuente de las decisiones de registro, hilos, señales y placeholders |

## Subpuntos

### M04.1 `IntentBoard` y `ContextSignals`

- **Propósito:** el único punto de contacto entre el hilo del servidor y el vocabulario, que puede ejecutarse en cualquier hilo (T3).
- **Diseño:**
  - `IntentBoard`:
    - Estado: `ConcurrentHashMap<UUID, IntentView>`.
    - `void publish(MobKey key, IntentView view)`: solo desde el hilo del servidor; exige `view.generation() == key.generation()`.
    - `void clear(UUID entityId)` y `void clearAll()`: `clearAll` forma parte del apagado (T7).
    - `IntentView current(UUID entityId)`: devuelve la vista o `IntentView.ABSENT`, sin asignar memoria ni devolver `null` (C4, C12).
    - La publicación segura la da el mapa concurrente; `IntentView` es inmutable.
  - `ContextSignals`:
    - Cola acotada de UUID pendientes de reevaluación, con deduplicación: `ConcurrentHashMap.newKeySet()` para los presentes y `ConcurrentLinkedQueue<UUID>` para el orden.
    - `boolean offer(UUID entityId)`: falso si ya está o si la cola llegó a su capacidad (1024); cuenta los descartes.
    - `int drain(Consumer<UUID> receiver, int max)`: solo en el hilo del servidor; entrega como máximo `max` y los quita del conjunto.
    - `long droppedCount()`: para el diagnóstico de M12.
- **Archivos:** `engine/IntentBoard.java`, `engine/ContextSignals.java` y sus pruebas.
- **Reglas:** A3, A4, A5, T3, C12.
- **Pruebas:** varios hilos publicando lecturas y escrituras simultáneas con `CountDownLatch` y un `ExecutorService`, sin `sleep`; `current` de una entidad desconocida devuelve `ABSENT`; generación distinta rechazada; deduplicación; tope y contador de descartes; `drain` respeta `max`.
- **Aceptación:**
  - [x] Ningún método de lectura asigna memoria.
  - [x] `clearAll` deja todas las condiciones en falso en la siguiente evaluación.

### M04.2 Condiciones `predictor*`

- **Propósito:** gobernar metas y skills según la intención vigente, con seguridad ante fallos (A6).
- **Diseño:**
  - Clases en `adapter.server.vocabulary`: `PredictorControlCondition`, `PredictorMoveCondition` y `PredictorTacticCondition`.
  - Implementan `IEntityCondition` (`check(AbstractEntity)`), que es la interfaz de las condiciones nativas de mob como `isplayer`, e `ICasterCondition` (`check(SkillCaster)`) para las condiciones de skills. M01 confirma cuál usa cada contexto.
  - Argumentos, leídos con `MythicLineConfig.getString(String[], String, String...)`:
    - `predictormove{m=HOLD}` o `movement=`: un valor de `Movement`, sin distinguir mayúsculas.
    - `predictortactic{t=retreat_and_defend}` o `tactic=`: un `TacticId` válido.
    - `predictorcontrol`: sin argumentos.
  - Un argumento inválido se detecta al construir: la condición queda siempre falsa y se emite un aviso de carga con límite de frecuencia.
  - Evaluación: UUID desde `AbstractEntity.getUniqueId()` o `SkillCaster.getEntity().getUniqueId()`; `IntentView view = board.current(uuid)`; resultado `view.controls(clock.nanoTime())` y, según la clase, `view.movement() == movement` o `view.tactic().equals(tactic)`.
  - La lógica comparativa vive en un método estático puro, por ejemplo `static boolean matches(IntentView view, Movement movement, long nowNanos)`, probado sin MythicMobs.
  - Campos `final`; sin llamadas a Bukkit ni registro en la evaluación.
- **Archivos:** las tres clases y sus pruebas.
- **Reglas:** A6, T3, C4, C12, I8.
- **Pruebas:** unitarias sobre `matches` para cada nivel de control, vista vencida, `ABSENT` y generación antigua; análisis de argumentos válidos e inválidos. En servidor (M01, M05): gobierno real de metas y de skills.
- **Aceptación:**
  - [x] Cualquier estado inválido produce falso.
  - [x] La evaluación no asigna memoria ni toca Bukkit (salvo la lectura del UUID que permite T3).

### M04.3 Mecánica `predictordecide`

- **Propósito:** que el autor pida una reevaluación en los momentos que ya conoce, con triggers nativos (`~onDamaged`, `~onSignal:…`).
- **Diseño:**
  - `PredictorDecideMechanic` implementa `INoTargetSkill`: `cast(SkillMetadata)` llama a `signals.offer(uuid del caster)` y devuelve `SkillResult.SUCCESS`, aunque la cola esté llena.
  - `getThreadSafetyLevel()` devuelve `ThreadSafetyLevel.EITHER` de forma explícita.
  - No envía solicitudes ni espera respuestas: el coordinador (M09) decide si hay presupuesto.
  - Uso: `- predictordecide @self ~onDamaged`.
- **Archivos:** `adapter/server/vocabulary/PredictorDecideMechanic.java` y su prueba.
- **Reglas:** T3, A5, A7.
- **Pruebas:** unitaria con un `ContextSignals` real; en servidor, `~onDamaged` encola una sola vez por mob hasta que el coordinador vacía la cola.
- **Aceptación:**
  - [x] La mecánica nunca bloquea ni lanza excepciones.
  - [x] Los descartes por cola llena son visibles en `droppedCount()`.

### M04.4 Placeholders

- **Propósito:** mostrar la táctica en hologramas, bossbar y mensajes de depuración.
- **Diseño:**

  | Placeholder | Valor | Sin sesión |
  |---|---|---|
  | `<caster.predictor.tactic>` | `TacticId` vigente | `none` |
  | `<caster.predictor.movement>` | movimiento en minúsculas | `none` |
  | `<caster.predictor.level>` | `jev`, `alternative`, `local` o `base` | `base` |
  | `<caster.predictor.confidence>` | confianza con dos decimales y `Locale.ROOT` | `0.00` |
  | `<caster.predictor.state>` | `controlled` si la vista controla; si no, `base` | `none` |

  - Registro con `MythicBukkit.inst().getPlaceholderManager().register(String, Placeholder)`, usando `Placeholder.meta(...)` o `Placeholder.entity(...)`. M01 confirma el nombre de registro que produce `<caster.…>` y el accesor del caster.
  - Solo leen `IntentBoard`; son seguros en cualquier hilo.
- **Archivos:** `adapter/server/vocabulary/PredictorPlaceholders.java` y su prueba.
- **Reglas:** T3, I8.
- **Pruebas:** formato de cada valor a partir de vistas de prueba; en servidor, un `message` que muestra cada placeholder con y sin sesión.
- **Aceptación:**
  - [x] Los cinco placeholders devuelven valores estables y documentados — parte local: formato probado contra vistas de prueba.
  - [ ] Los cinco placeholders devuelven valores estables y documentados — parte de servidor: `<caster.predictor.*>` se sustituye en un `message` (DT-11).
  - [ ] Siguen funcionando tras `/mm reload`.

### M04.5 Registro del vocabulario

- **Propósito:** que el vocabulario esté disponible en el primer arranque y tras cada recarga, respetando D8.
- **Diseño:**
  - `VocabularyRegistrar`, un listener que construye la raíz con `IntentBoard`, `ContextSignals` y `ServerClock` inyectados:
    - `MythicConditionLoadEvent`: si `getConditionName()` coincide, sin distinguir mayúsculas, con `predictorcontrol`, `predictormove` o `predictortactic`, llama a `event.register(new …Condition(event.getConfig(), board, clock))`.
    - `MythicMechanicLoadEvent`: si `getMechanicName()` es `predictordecide`, llama a `event.register(new PredictorDecideMechanic(signals))`.
    - Placeholders: se registran al habilitar ThePredict y otra vez en `MythicPlaceholdersLoadEvent`, que MythicMobs emite en cada recarga.
    - Como MythicMobs resuelve los componentes un tick después de leerlos (plan §2), registrar el listener en `onEnable` alcanza la carga inicial. M01 lo confirma.
  - `CustomComponentRegistry` no se usa (C15): instancia las clases por reflexión con `(JavaPlugin, evento)` o `(evento)` y no permite inyectar `IntentBoard` sin estado estático.
- **Archivos:** `adapter/server/vocabulary/VocabularyRegistrar.java`; cambios en `ThePredictPlugin.java` para construirlo y registrarlo.
- **Reglas:** D5, D8, A1, I8.
- **Pruebas:** unitaria del filtro de nombres; en servidor, los casos de M01.3 repetidos con la implementación final.
- **Aceptación:**
  - [ ] Ningún error de condición desconocida en el primer arranque ni tras `/mm reload`.
  - [x] Los nombres funcionan con cualquier capitalización — parte local: el filtro acepta cualquier capitalización con eventos de carga reales.
  - [ ] Los nombres funcionan con cualquier capitalización — parte de servidor (DT-02).

### M04.6 Señales `PREDICTOR_<TACTIC>`

- **Propósito:** que el autor telegrafíe el inicio de una táctica con triggers nativos (`~onSignal:`).
- **Diseño:**
  - Nombre: `"PREDICTOR_" + tactic.value().toUpperCase(Locale.ROOT)`, calculado por `TacticSignals.nameOf(TacticId)` en `adapter.server.vocabulary`.
  - Semántica: una señal cuando empieza una intención con una táctica nueva, en los niveles `JEV`, `ALTERNATIVE` o `LOCAL`. Nunca al renovar la misma táctica ni al pasar a `BASE`.
  - Emisión: M10 llama a `CombatActions.signalTactic(MobKey, TacticId)`, que el adapter implementa con `ActiveMob.signalMob(activeMob.getEntity(), nombre)` en el hilo del servidor.
  - M01 confirma si `~onSignal` distingue mayúsculas; si no las distingue, el formato se mantiene igual por claridad.
- **Archivos:** `adapter/server/vocabulary/TacticSignals.java` y su prueba. La emisión se implementa en M10.
- **Reglas:** I9, T1.
- **Pruebas:** nombre de señal para IDs de 3 y 40 caracteres; en servidor (M10), una sola señal por táctica nueva.
- **Aceptación:**
  - [x] El formato está documentado para autores (sección "Vocabulario"; DT-11 confirma si `~onSignal` distingue mayúsculas).
  - [ ] No hay señales repetidas al renovar una intención (M10, en servidor). Nota: la regla de emisión ya está probada en local (`IntentPublicationTest.theSignalIsSentOnlyWhenTheTacticChangesAndNeverOnARenewal`, `FallbackChainTest.withoutJevTheLocalPlannerControlsAtZeroCostAndPlansAgainWhenItsShortIntentExpires`); la casilla espera la observación en servidor de `~onSignal` (DT-11, guion 12.2).

### M04.7 Referencia para autores

- **Propósito:** que M14 tenga material exacto y probado.
- **Diseño:** tabla con nombre, argumentos, valores, garantías y ejemplos:
  - Silenciar una skill clásica mientras ThePredict controla: `Conditions: [ - predictorcontrol false ]`.
  - Variante por táctica: `Conditions: [ - predictortactic{t=retreat_and_defend} true ]`.
  - Pedir reevaluación: `- predictordecide @self ~onDamaged`.
  - Telegrafía: `- sound{s=…} @self ~onSignal:PREDICTOR_RETREAT_AND_DEFEND`.
  - Holograma: `<caster.predictor.tactic>`.

  Se indica que las condiciones son falsas sin intención vigente y que los nombres usan el prefijo `predictor` para no confundirse con `@PredictedTargetLocation` o `@targetPredictedLoc`.
- **Archivos:** sección "Vocabulario" en este documento al cerrar el módulo; M14 la traslada a la guía.
- **Reglas:** W3.
- **Pruebas:** cada ejemplo se ejecuta en el servidor de verificación antes de publicarse.
- **Aceptación:**
  - [ ] Cada ejemplo tiene una prueba en servidor registrada.

## Riesgos y decisiones abiertas

- **Incorporado en 01 (revisión 2):** `IntentView.ABSENT` y la táctica reservada `none`, necesarias para que `IntentBoard.current` no asigne memoria ni devuelva `null` (también pedida en M02).
- **Resuelto en 00 (C15):** el vocabulario se registra con eventos de carga desde `VocabularyRegistrar`; `CustomComponentRegistry` no se usa porque obligaría a estado estático (D8).
- El plan §2 (revisión 4) ya descarta `CustomComponentRegistry`; M01 confirma que los eventos de carga cubren todos los casos.
- M01 confirma si `MythicMechanicLoadEvent.register` acepta una implementación directa de `INoTargetSkill` o exige extender la clase base de mecánicas de MythicMobs.

## Terminado

- [ ] Las tres condiciones, la mecánica y los placeholders funcionan en el primer arranque y tras `/mm reload`.
- [x] Pruebas concurrentes de `IntentBoard` y `ContextSignals` verdes.
- [ ] Ejemplos para autores verificados en servidor.
- [x] `./gradlew clean build javadoc` pasa.

## Vocabulario

> Referencia para la guía de autores (M04.7). **Pendiente de verificación en servidor:** ningún ejemplo se ha ejecutado todavía en el servidor de verificación. **Revisión M14 (2026-09-29):** M14 trasladó esta referencia a la [guía del autor, §10](../../Guias/Guia-del-autor.md#10-vocabulario-de-thepredict-en-mythicmobs) con cada elemento marcado como pendiente de servidor (W3), y su ejemplo, ya como mob completo (`VocabularyGuardian`), se prueba en la sesión 3 del [paquete de pruebas](paquete-pruebas/Guia-de-pruebas-por-sesiones.md) (pasos 3.8–3.13).

| Elemento | Argumentos | Valores | Garantías |
|---|---|---|---|
| Condición `predictorcontrol` | ninguno | — | Verdadera mientras la vista del mob está en `JEV`, `ALTERNATIVE` o `LOCAL` y no venció. Falsa sin sesión, en `BASE`, vencida, tras `clearAll` (apagado) o si falta el caster o la entidad |
| Condición `predictormove` | `m` o `movement` | `APPROACH`, `KEEP_DISTANCE`, `RETREAT`, `HOLD`, `FLANK`, `NONE`, sin distinguir mayúsculas | Verdadera si controla y pide ese movimiento. Un valor desconocido o ausente la deja siempre falsa y avisa una vez por carga |
| Condición `predictortactic` | `t` o `tactic` | Un `TacticId` exacto en minúsculas (`[a-z][a-z0-9_]{2,39}`), salvo el reservado `none` | Verdadera si controla con esa táctica. Un valor inválido la deja siempre falsa y avisa una vez por carga; si solo difiere en mayúsculas, el aviso sugiere la forma correcta |
| Mecánica `predictordecide` | ninguno | — | Marca el caster para reevaluación; nunca espera, bloquea ni falla, y devuelve siempre éxito. Un mob marcado no se marca de nuevo hasta que se atiende; con 1024 mobs pendientes, los nuevos se descartan y se cuentan |
| Placeholders `<caster.predictor.*>` | — | `tactic`, `movement`, `level`, `confidence`, `state` (tabla de M04.4) | Solo lectura, seguros en cualquier hilo. Sin sesión: `none`, `none`, `base`, `0.00`, `none` |
| Señal `PREDICTOR_<TACTIC>` | — | `PREDICTOR_` y el `TacticId` en mayúsculas, hasta 50 caracteres | Una vez cuando empieza una intención con una táctica nueva en `JEV`, `ALTERNATIVE` o `LOCAL`; nunca al renovarla ni al pasar a `BASE` (emisión en M10) |

Ejemplos (archivo de mobs y archivo de skills):

```yaml
ThePredictGuardian:
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
```

- Todas las condiciones son falsas sin intención vigente: si ThePredict se detiene, pierde el servicio o la intención vence, el mob vuelve a sus metas base en el siguiente ciclo.
- Los nombres usan el prefijo `predictor` para no confundirse con `@PredictedTargetLocation` o `@targetPredictedLoc` de MythicMobs.

## Estado de implementación

> **Local listo — servidor pendiente (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` (también con `--rerun-tasks`) pasa sin avisos de compilación ni de Javadoc: **474 pruebas, 0 fallos** (409 de partida − 2 retiradas + 63 nuevas + 4 de las correcciones tras la auditoría). Ninguna prueba hace llamadas de red ni lee `.env` (W6). No se arrancó ningún servidor: las aceptaciones que exigen juego siguen sin marcar y no hay evidencia de servidor inventada. 01 no cambia; 00 recibe una precisión mínima de T3 (revisión 4, ver desviaciones).

### Archivos

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…` salvo indicación) |
|---|---|
| M04.1 | Creados `engine/ContextSignals` y `engine/ServerClock` (puerto de 00 §4). `engine/IntentBoard` de M01 ya cumplía el diseño (lectura sin asignaciones, `ABSENT`, generación exigida, `clearAll`); solo se amplían sus pruebas |
| M04.2 | Creados `adapter/server/vocabulary/IntentCondition` (base sellada, paquete privado), `PredictorControlCondition`, `PredictorTacticCondition`, `ArgumentText` (paquete privado). Completado `PredictorMoveCondition` |
| M04.3 | Creado `adapter/server/vocabulary/PredictorDecideMechanic` |
| M04.4 | Reescrito `adapter/server/vocabulary/PredictorPlaceholders` (textos precalculados, sin `String.format`; `ServerClock`) |
| M04.5 | Creados `adapter/server/vocabulary/VocabularyRegistrar` y `LoadWaveLog` (paquete privado). Borrados `adapter/server/vocabulary/ProvisionalVocabularyListener` y su prueba. `ThePredictPlugin`: `ContextSignals`, `ServerClock` y `enableVocabulary` (registra el listener y los placeholders en `onEnable`) |
| M04.6 | Creado `adapter/server/vocabulary/TacticSignals` |
| M04.7 | Sección "Vocabulario" de este documento |
| Seguimiento de la auditoría de M01 | `adapter/server/vocabulary/ConditionTrace` (carrera del tope corregida); `PredictorPlaceholders` (sin `String.format` en el camino caliente); nota de T3 sobre `getUniqueId` en `IntentCondition` y en 00 |
| Depuración | `admin/DebugRequest` (`DrainSignals`), `admin/DebugCommandParser` (`debug signals`), `DebugCommandHandler` (recibe `ContextSignals`) |
| Documentos | Este documento; `README.md` (fila de M04); `00-Arquitectura-y-reglas.md` (T3, revisión 4); `M01-Registro-de-decisiones.md` (evidencia estática de DT-02, DT-13 y DT-14; todas siguen `Abierta`) |

### Pruebas (474; 63 nuevas, 2 retiradas y 4 de las correcciones tras la auditoría)

- `engine`: `ContextSignalsTest` (7, nueva: deduplicación sin contar descarte, orden y `max`, reoferta tras vaciar, `max = 0` y negativo, tope 1024 y descarte 1025, 8 productores concurrentes que nunca superan el tope, productores y vaciado concurrentes sin perder mobs); `ServerClockTest` (2, nueva); `IntentBoardTest` (7, antes 5: lectura sin asignaciones con vista y sin ella; `clearAll` deja de controlar).
- `adapter.server.vocabulary`: `PredictorControlConditionTest` (6, nueva: cada nivel, `BASE`, vencida en el nanosegundo exacto, `ABSENT`, desbordamiento del reloj, `clearAll` pone las tres condiciones en falso, sin asignaciones); `PredictorMoveConditionTest` (10, antes 5: generación nueva en `BASE`, caster o entidad ausentes, claves `m` y `movement`, argumento ausente o con símbolos sin eco, sin asignaciones); `PredictorTacticConditionTest` (7, nueva: 3 y 40 caracteres válidos, 2 y 41 inválidos, `debug_hold`, mayúsculas con sugerencia, sin eco, sin asignaciones); `PredictorDecideMechanicTest` (4, nueva); `PredictorPlaceholdersTest` (9, antes 4: cada nivel, redondeo en `0.125`, `0.995` y `1`, igualdad con `%.2f` en 100 001 valores y en cada frontera `x.xx5` con `nextUp`/`nextDown`, caster ausente, sin asignaciones); `TacticSignalsTest` (4, nueva); `VocabularyRegistrarTest` (9, nueva, con `MythicConditionLoadEvent` y `MythicMechanicLoadEvent` reales: capitalización, nombres ajenos, aviso único sin eco, líneas de evidencia); `LoadWaveLogTest` (3, nueva, con escritores concurrentes); `ArgumentTextTest` (4, nueva); `ConditionTraceTest` (5, antes 3: 8 hilos con 800 etiquetas no superan 64; reactivar empieza de cero). `VocabularyFixtures` es un ayudante de prueba. `ProvisionalVocabularyListenerTest` (2) se retira con su clase; sus casos pasan a `VocabularyRegistrarTest`.
- `admin.DebugCommandParserTest` (13, antes 12); `ArchitectureRulesTest` (13, antes 11: la regla T3 permite eventos y API de MythicMobs y rechaza `Bukkit`, `MythicBukkit` y nombres calificados de `org.bukkit.entity`; el vocabulario no alcanza otras capacidades de `adapter.server`).
- `src/test/java/akyrion/thepredict/AllocationProbe` mide los bytes asignados por el hilo con `com.sun.management.ThreadMXBean` (20 000 llamadas de calentamiento y 200 000 medidas; tolerancia 64 KiB, muy por debajo de una asignación por llamada). Detectó un falso negativo en la propia prueba (`Field.values()` asigna 40 bytes por llamada), corregido.

### Desviaciones y decisiones

| Desviación o decisión | Motivo |
|---|---|
| **00 T3 (revisión 4):** el vocabulario puede leer el UUID con `AbstractEntity.getUniqueId()` | Es la única forma de saber qué vista leer. Evidencia `javap`: `BukkitEntity.getUniqueId()` guarda el UUID en `cachedUuid` tras la primera lectura, que llama a `org.bukkit.entity.Entity#getUniqueId()`; en el servidor Paper 26.2 (parcheado localmente con Paperclip `-Dpaperclip.patchonly=true`, sin arrancarlo) `CraftEntity.getUniqueId()` devuelve `Entity.getUUID()`, que solo lee el campo `uuid`. Sin acceso al mundo, bloqueos ni efectos. Para las condiciones de skill, `caster.getEntity()` es `ActiveMob.getEntity()`: fuera del hilo principal y sin entidad en caché programa `Schedulers.sync().run(...)` y devuelve `null` (javap), que las condiciones tratan como falso; es inocuo. Documentado en el Javadoc de `IntentCondition`, `PredictorDecideMechanic` y `PredictorPlaceholders` |
| `engine.ServerClock` se crea en M04 con `static ServerClock of(LongSupplier ticks, LongSupplier nanos)` | M04.2 y M04.5 lo inyectan; 00 §4 ya lo define y M01 usó `LongSupplier` solo por W5. La fábrica sigue el precedente de `DecisionClient.unavailable`. `GoalOwnership`, `DebugCommandHandler` y `ConditionTrace` (provisionales) siguen con `LongSupplier`; la raíz les pasa `clock::nanoTime` |
| Base común `IntentCondition` (sellada, paquete privado) con `test(UUID)`, `isValid()` y `problem()` | Una sola implementación de la evaluación segura (A6, C12) y del aviso; `test(UUID)` permite medir asignaciones sin dobles de MythicMobs |
| Cada condición tiene constructor `(MythicLineConfig, IntentBoard, ServerClock)` como pide M04.5, otro con el texto del argumento y `static argument(MythicLineConfig)` | Probar el análisis sin MythicMobs; el registrador usa el constructor del diseño |
| `predictortactic` acepta cualquier `TacticId` válido salvo el reservado `none`, incluidos los reservados `debug_*`; no convierte a minúsculas | M04.2 pide "un `TacticId` válido". El plan §3 decía "solo acepta tácticas del perfil", pero al cargar una línea no hay perfil: una skill puede servir a varios mobs y el vínculo llega después. `debug_*` permite verificar con `debug intent`. `none` es inválido con aviso: según 01 §2 solo lo llevan `IntentView.ABSENT` e `IntentView.base`, que nunca controlan, así que la condición nunca podría ser verdadera. Un valor en mayúsculas es inválido, con sugerencia en el aviso |
| Avisos de argumentos inválidos con `LoadWaveLog`: una vez por mensaje y carga (pausa de 2 s), como máximo 32 por carga; el argumento solo se cita si es un nombre simple de hasta 40 caracteres | C8 (registro con límite) y W6 (ningún texto arbitrario en consola), tras el bloqueo de M03 |
| Se conservan las líneas `MythicMobs resolved <condition\|mechanic> <nombre> on thread <hilo>` y `Registered <caster.predictor.*> placeholders after MythicPlaceholdersLoadEvent` con el mismo formato y límite | El guion de M01 (1.5, 3.1–3.4) las usa como evidencia de DT-02 y DT-03 |
| `VocabularyRegistrar` también registra la sonda `predictorprobe` (condición y mecánica) y recibe `ConditionTrace` y dos receptores de registro | W5: los flujos `/thepredict debug trace` y el guion M01.7 siguen funcionando. Las sondas son provisionales; M12 decide su retiro junto con el comando de depuración |
| Placeholders con `Placeholder.meta` (obsoleta) | DT-13: la vía no obsoleta crea las clases por reflexión con un constructor fijo (nueva evidencia en el registro) y obligaría a estado estático (D8). Se registran al habilitar y en cada `MythicPlaceholdersLoadEvent` |
| `PredictorPlaceholders.resolve(Field, PlaceholderMeta)` público y constantes `NONE`, `CONTROLLED`, `BASE` | Probar la resolución con dobles; valores documentados en un solo lugar |
| Confianza con una tabla de 101 textos indexada por `Math.round((double) confianza × 100)` | Sin `String.format` (seguimiento de M01, C12). Un `float` por 100 es exacto en `double`, así que para toda confianza que admite `IntentView` (en [0, 1], con `-0.0f` normalizado a `0.0f` en el constructor) el texto es idéntico a `String.format(Locale.ROOT, "%.2f", confianza)`: redondeo medio hacia arriba y nunca `-0.00`. La prueba lo compara con `String.format` |
| `ContextSignals`: un mob ya pendiente se fusiona y **no** cuenta como descarte; `droppedCount()` solo cuenta ofertas con la cola llena; se añade `pendingCount()`; el hueco se reserva con CAS antes de encolar | La aceptación de M04.3 habla de "descartes por cola llena"; una reoferta no pierde información. La reserva garantiza el tope bajo concurrencia |
| `PredictorDecideMechanic` protege caster, entidad y UUID ausentes con comprobaciones, sin capturar excepciones | "Nunca lanza": la única operación restante es `offer`, que no falla con un UUID; capturar ocultaría errores de programación |
| Subcomando nuevo `/thepredict debug signals` (`DebugRequest.DrainSignals`): vacía la cola y muestra `drained`, `dropped` y hasta 5 UUID | Sin él, M04.3 no se puede verificar en servidor hasta M09. `DebugCommandHandler` pasa de 249 a 270 líneas y `ThePredictPlugin` de 332 a 345 (C10); ambos son heredados o provisionales y M12 los reduce. Ningún método nuevo supera 30 líneas ni ninguna clase nueva 200 |
| `TacticSignals` solo ofrece `nameOf`; la semántica (táctica nueva sí, renovación y `BASE` no) queda en su Javadoc y en "Vocabulario" | La decisión de emitir la toma `engine` en M10, que no puede importar `adapter` (D2, D7) |
| `ConditionTrace`: cada activación crea un conjunto de etiquetas nuevo con su contador; una etiqueta reserva su hueco con CAS dentro de `computeIfAbsent`; se añade `trackedLabels()` | Seguimiento de M01: la comprobación `size() >= MAX_LABELS` seguida de `computeIfAbsent` permitía superar el tope con varios hilos, y `clear()` durante una grabación podía desajustar el contador |
| `ArchitectureRulesTest`: regla propia `adapter.server.vocabulary` (T3) con `java.`, `org.bukkit.event.`, `io.lumine.mythic.api.`, `io.lumine.mythic.core.` e `io.lumine.mythic.bukkit.events.`, e internos `domain`, `engine` y el propio paquete; D7 solo se informa entre adapters distintos | Hace comprobable "no llama a Bukkit" (T3); antes una referencia de un subpaquete de `adapter.server` a otro se habría etiquetado D7 por error. **Límite:** la regla se basa en nombres (importaciones y nombres calificados), no en llamadas; no detecta llamadas respaldadas por Bukkit a través de tipos permitidos, como cualquier cosa de `io.lumine.mythic.core.*` o `AbstractEntity.getBukkitEntity()` y `getLocation()`. Esas llamadas las cubre la revisión de código; el límite está en el Javadoc de `ArchitectureRulesTest` |

### Correcciones tras la auditoría (2026-09-29)

La auditoría aprobó el módulo sin bloqueos. Correcciones menores aplicadas; `./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **474 pruebas, 0 fallos**.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| La evidencia de T3 no citaba `ActiveMob.getEntity()` | Fila de T3 y Javadoc de `IntentCondition`: fuera del hilo principal y sin caché programa `Schedulers.sync().run(...)` y devuelve `null`, tratado como falso (javap) | — |
| La tabla de confianza daba `0.00` donde `%.2f` daría `-0.00` para `-0.0f` | `IntentView` normaliza `-0.0f` a `0.0f` en el constructor canónico; frase de equivalencia con `%.2f` precisada | `IntentViewTest.negativeZeroConfidenceIsNormalizedToPositiveZero`, `PredictorPlaceholdersTest.negativeZeroConfidenceShowsTheSameTextAsFormattingTheStoredValue` |
| `predictortactic{t=none}` se aceptaba en silencio | `none` es inválido y produce el aviso limitado de argumento inválido (01 §2: solo lo llevan vistas que nunca controlan) | `PredictorTacticConditionTest.reservedNoneTacticIsInvalidBecauseNoControllingViewCarriesIt`, `VocabularyRegistrarTest.reservedNoneTacticIsRegisteredAsInvalidWithOneWarning` |
| El límite de la regla T3 (basada en nombres) no estaba documentado | Documentado en la fila de `ArchitectureRulesTest` y en su Javadoc | — |
| C10: `ThePredictPlugin.onEnable` tenía 32 líneas | Extraído `verifyPrerequisites()`; `onEnable` queda en 21 | Suite completa |
| DT-11 y DT-13 sin la evidencia estática nueva | Añadida en el registro de M01; todas siguen `Abierta` | — |

### Pendiente de verificación en servidor

- M04.5, DT-02 y DT-03: sin `Unknown condition` ni `Invalid condition` para `predictorcontrol`, `predictormove`, `predictortactic`, `predictordecide` y `predictorprobe` con varias capitalizaciones, en el primer arranque y tras `/mm reload`; líneas `MythicMobs resolved …` en ambos casos.
- DT-14: `predictordecide` aceptado como `INoTargetSkill` directo. `~onDamaged` repetido encola una sola vez por mob: `debug signals` muestra `drained=1`; tras vaciar, un golpe nuevo vuelve a encolar.
- DT-09: hilo real de evaluación de las condiciones y de la mecánica.
- DT-11 y DT-13: `<caster.predictor.*>` sustituidos en un `message` con y sin sesión (`none/none/base/0.00/none` y `debug_hold/hold/local/1.00/controlled`), tras `/mm reload`, y el texto literal del aviso de API obsoleta.
- DT-11: si `~onSignal` distingue mayúsculas (formato de señales para autores, W3).
- `predictorcontrol false` silencia una skill clásica durante `debug intent` y la libera tras `debug release`; `predictortactic{t=debug_hold} true` pasa con `debug intent nearest hold`.
- Un `predictormove{m=SIDEWAYS}` produce un único aviso por carga y la condición queda falsa.
- Cada ejemplo de "Vocabulario" (M04.7) con su prueba registrada.

### Pendiente para módulos posteriores

- M05: generar las líneas de metas con `PredictorMoveCondition.NAME`; pasar `GoalOwnership` de `LongSupplier` a `ServerClock`.
- M06: al cambiar la generación, publicar `IntentView.base(generación)` o limpiar la vista, para que ninguna vista antigua siga controlando.
- M09 (hecho): el coordinador vacía `ContextSignals` con `decision.maxSignalsPerTick` e ignora UUID sin sesión; `debug signals` deja de ser el consumidor y solo muestra `pending` y `dropped` (`DebugRequest.ShowSignals`).
- M10 (hecho): regla de emisión de señales en `engine` (táctica nueva en `JEV`, `ALTERNATIVE` o `LOCAL`; nunca al renovar ni en `BASE`) y `CombatActions.signalTactic` con `TacticSignals.nameOf`.
- M12: `droppedCount()` en el diagnóstico; retiro o traslado de `predictorprobe`, `ConditionTrace`, `debug signals` y `DebugCommandHandler`; decisión final de DT-13 con evidencia; nombres en el vocabulario de MythicScript 2077. **Hecho en M12** (ver su [estado de implementación](M12-Diagnostico-instalacion-retiro-OpenRouter.md#estado-de-implementación)); DT-13 sigue abierta sin evidencia de servidor y la extensión MythicScript 2077 no se modifica (M12.7).
- M14: trasladar "Vocabulario" a la guía de autores cuando cada ejemplo tenga su prueba en servidor.

## Errata (pulido del 2026-10-17, DOC-10)

Desde el pulido del 2026-10-17, **sin sesión los cinco placeholders dan `none`** (`tactic`, `movement`, `level`, `confidence` y `state`). Así el autor distingue un mob sin sesión de una sesión en IA base, que sigue dando `base` y `0.00`. La tabla de M04.4, la fila de placeholders y la nota de DT-11 y DT-13 conservan el texto original como registro histórico.
