# M06. Sesiones, encuentros y ciclo de vida

> **Nota de M27 (2026-10-07):** el alta de un jugador puede leer `thepredict.memory.exempt` en el adaptador y consultar `MemoryCache` en el motor. El camino de producción de esa consulta queda en el informe de M27.

> **Estado:** Local listo — servidor pendiente (2026-09-29; ver "Estado de implementación") · **Entrega:** B · **Depende de:** M02, M05 · **Desbloquea:** M07, M09, M11 · **Diseño:** [plan §7](../Plan-Jev-y-combate-tactico.md#hilos-y-propiedad-del-estado), [plan §4](../Plan-Jev-y-combate-tactico.md#4-arquitectura-y-contrato-del-dominio), [plan §8](../Plan-Jev-y-combate-tactico.md#8-estados-fallos-y-recuperación)

## Objetivo

Dar identidad y aislamiento a cada instancia vinculada (`MobKey`), seguir su ciclo de vida con eventos nativos y agrupar las instancias en encuentros por combate. El encuentro es la unidad de solicitud a Jev, de latido y de presupuesto. Al terminar M06, `engine` sabe en todo momento qué mobs controla, en qué encuentro está cada uno y cuándo toca decidir, sin copiar periódicamente todos los mobs activos.

## Alcance

### Incluye

- `engine.SessionRegistry` y `engine.MobSession` con generaciones y estados.
- Listeners de ciclo de vida en `adapter.server.events`.
- `engine.EncounterRegistry` y `engine.Encounter`: formación, alias de participantes, cierre y cambio de encuentro.
- `engine.Agenda`: latido, contexto pendiente y equidad entre mundos.
- Reconciliación acotada contra el gestor de mobs de MythicMobs y métricas de sesiones.

### No incluye

- La captura de hechos y los eventos de contexto (M07); M06 solo mantiene el índice de participantes que M07 consulta.
- La instalación de metas (M05); M06 marca cuándo hace falta.
- El envío de solicitudes y el presupuesto (M09).
- La aplicación de intenciones (M10).

## Base existente

| Archivo | Acción |
|---|---|
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/MobDecisionStates.java` | Se sustituye por `engine.SessionRegistry` y `engine.MobSession`. |
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/RotatingMobCursor.java` | Se elimina; `engine.Agenda` la sustituye con equidad por encuentro y mundo. |
| `src/main/java/akyrion/thepredict/adapter/mythicmobs/MythicPredictionRunner.java` | Su limpieza cada 1200 ticks sobre todos los mobs activos pasa a la reconciliación acotada de M06.6; el resto lo retira M09. |
| `src/main/java/akyrion/thepredict/ThePredictPlugin.java` | Sus listeners de spawn, muerte, despawn y recarga pasan a `adapter.server.events.LifecycleListener`; `runOnServerThread` se conserva como utilidad de ese paquete. |
| `src/test/java/akyrion/thepredict/adapter/mythicmobs/MobDecisionStatesTest.java` | Sus casos de aislamiento por UUID se trasladan a `SessionRegistryTest`. |
| `src/test/java/akyrion/thepredict/adapter/mythicmobs/RotatingMobCursorTest.java` | Se elimina con la clase; la equidad se prueba en `AgendaTest`. |

## Subpuntos

### M06.1 `SessionRegistry` y `MobSession`

- **Propósito:** una sesión por mob vinculado, con identidad que invalida respuestas viejas (I1).
- **Diseño:**
  - `final class MobSession` (hilo del servidor), con los campos de [01 §8](01-Modelo-de-dominio.md#8-estado-mínimo-de-engine): `MobKey key`, `TacticalProfile profile`, `SessionState state`, encuentro actual, intención actual, racha de confianza baja y último `FallbackReason`. Añade `installPending` y `nextProbeTick` (01, revisión 2).
  - `final class SessionRegistry` con `Map<UUID, MobSession>`. Operaciones:
    - `MobSession open(UUID entityId, TacticalProfile profile)`: generación 0 la primera vez; si existía una sesión suspendida para ese UUID, generación + 1. Estado `ACTIVE`, `installPending = true`.
    - `void suspend(UUID)`: estado `SUSPENDED`, sale de su encuentro.
    - `MobSession resume(UUID)`: generación + 1, estado `ACTIVE`, `installPending = true`.
    - `void advanceGeneration(UUID)`: generación + 1 sin cambiar de estado; se usa en cambio de mundo y recarga.
    - `void close(UUID, FallbackReason)`: estado `CLOSED`, sale de su encuentro y se elimina del mapa.
    - `Optional<MobSession> find(UUID)`, `Optional<MobSession> find(MobKey)` (exige la misma generación), `Collection<MobSession> active()`.
  - Cada operación que cambia generación o estado actualiza `IntentBoard` en la misma llamada: publica `IntentView.base(generation)` al abrir o reanudar, y retira la vista al suspender o cerrar. Una condición nunca ve una generación que ya no existe (A6).
  - Sin límite artificial de sesiones: su número es el de mobs vinculados. El estado vive en `engine` (A3).
- **Archivos:** crear `engine/MobSession.java` y `engine/SessionRegistry.java`; prueba `src/test/java/akyrion/thepredict/engine/SessionRegistryTest.java`.
- **Reglas:** A3, A6, I1, T1, C4.
- **Pruebas:** dos instancias del mismo tipo con estado independiente; `find(MobKey)` con generación vieja devuelve vacío; suspender y reanudar incrementa la generación; cerrar retira la vista; `IntentBoard` refleja cada transición.
- **Aceptación:**
  - [x] Ninguna clave usa el ID entero de entidad.
  - [x] Una respuesta para una generación anterior no encuentra sesión.
  - [x] Cerrar una sesión elimina todas sus referencias en `engine`.

### M06.2 Listeners de ciclo de vida

- **Propósito:** mantener el registro con eventos nativos, sin sondear todos los mobs.
- **Diseño:** `adapter.server.events.LifecycleListener` traduce eventos a operaciones de `SessionRegistry`. Todo evento que MythicMobs marque como asíncrono pasa antes al hilo del servidor con `runOnServerThread` (T6).

  | Evento | Hilo | Acción |
  |---|---|---|
  | `MythicMobSpawnEvent` no cancelado | Puede ser asíncrono | Un tick después, si el tipo está vinculado y `Predictor` es verdadero: `open` |
  | `EntitiesLoadEvent` | Servidor | Un tick después, para cada entidad con `ActiveMob` de un tipo vinculado: `resume` u `open` |
  | `EntitiesUnloadEvent` | Servidor | `suspend` para cada entidad con sesión |
  | `MythicMobDeathEvent` | Puede ser asíncrono | `close(…, SESSION_INVALID)` |
  | `MythicMobDespawnEvent` | Puede ser asíncrono | `close(…, SESSION_INVALID)` |
  | `EntityRemoveEvent` | Servidor | Si la sesión sigue abierta: `close`. Cubre eliminaciones sin evento de MythicMobs; su cobertura: **M01 confirma** |
  | `EntityTeleportEvent` y `EntityPortalEvent` con cambio de mundo | Servidor | `advanceGeneration` y salida del encuentro. Cuál de los dos cubre todos los casos: **M01 confirma**; mientras tanto se escuchan ambos |
  | `MythicReloadedEvent` | Puede ser asíncrono | Tras el nuevo vínculo de M03: por cada sesión, `advanceGeneration` si su tipo sigue vinculado; `close(…, SESSION_INVALID)` si ya no lo está o `Predictor` pasó a falso |

  - El cambio de `Predictor` solo ocurre al recargar MythicMobs; lo cubre la última fila.
  - Al habilitar el plugin, un escaneo inicial recorre `MythicBukkit.inst().getMobManager().getActiveMobs()` a razón de 32 mobs por tick y abre sesiones para los vinculados.
  - Alternativa disponible y verificada en la API: `EntityAddToWorldEvent` y `EntityRemoveFromWorldEvent` de Paper. Se usan solo si M01 demuestra que los eventos de chunk no cubren algún caso.
- **Archivos:** crear `adapter/server/events/LifecycleListener.java` y `adapter/server/events/ServerThread.java` (utilidad `runOnServerThread` extraída de `ThePredictPlugin`); modificar `ThePredictPlugin.java` para registrar el listener y quitar los métodos trasladados.
- **Reglas:** T1, T6, A6, W5.
- **Pruebas:** en `engine`, secuencias de operaciones equivalentes a spawn, descarga, carga, cambio de mundo, muerte y recarga. En servidor (guion de M01): reaparición del mismo tipo, recarga con `Predictor` apagado y cambio de mundo por portal.
- **Aceptación:**
  - [x] Todo evento de la tabla tiene prueba de secuencia en `engine`.
  - [x] Ningún listener modifica estado fuera del hilo del servidor.
  - [x] Tras `/mm reload` con `Predictor: false`, la sesión se cierra y el mob recupera la IA de MythicMobs (M05.4) — parte local: la sesión se cierra, la vista se retira en la misma llamada y `release` se pide en el tick siguiente (`SessionLifecycleTest`, `LifecycleListenerTest`).
  - [ ] Tras `/mm reload` con `Predictor: false`, la sesión se cierra y el mob recupera la IA de MythicMobs (M05.4) — parte de servidor (guion 10.8 y 10.9, DT-04).
  - [x] Al desvincular, el orden es: retirar la vista de `IntentBoard` en la misma llamada y `MobControl.release` en el tick siguiente (M05.4, pendiente heredado de M05). Con más de 32 desvinculaciones en un tick, las siguientes se liberan en los ticks posteriores (desviación 8).
  - [x] Al deshabilitar el plugin se liberan como máximo 200 sesiones con `release` (T7); el resto conserva las metas generadas, cuyas condiciones `predictormove` son falsas sin vista, así que actúa su IA base (M05.4). Prueba en `engine` con 250 sesiones (M05.4, pendiente heredado de M05).
  - [x] La instalación de metas ocurre un tick después de `open`/`resume` (spawn o carga de chunk), marcando `installPending`; nunca en el mismo tick del evento (M05.4) — parte de M06: la sesión se abre o reanuda un tick después del evento con `installPending` (`LifecycleListenerTest`).
  - [x] La instalación de metas ocurre un tick después de `open`/`resume` — la llamada a `ensureInstalled` para las sesiones con `installPending` es el paso 5 del coordinador (M09.1): `CoordinatorTest.fiftyPendingInstallationsFinishInFiveTicksWithAtMostTenPerTick` y `aDeferredInstallationIsRetriedAndAnUnsupportedOneWaitsForANewGeneration` (M09).

### M06.3 Formación de encuentros

- **Propósito:** agrupar mobs que pelean contra los mismos jugadores para decidir con una solicitud por combate (plan §6).
- **Diseño:**
  - `final class Encounter` con los campos de 01 §8: `EncounterId`, miembros (`Set<MobKey>`), alias de participantes, historial de eventos (anillo de 16), secuencia de solicitudes, indicador de solicitud en vuelo, indicador de contexto pendiente y siguiente tick de decisión.
  - `final class EncounterRegistry`:
    - `EncounterId` desde un contador `long` monotónico que empieza en 1; nunca se reutiliza (I2).
    - Índice `ParticipantIndex` por (mundo, UUID del participante) con la lista ordenada de encuentros abiertos que lo incluyen; la búsqueda de la lista es O(1) y un mob se une al primero que lo acepta (desviación 2).
    - `void assign(MobSession session, Optional<UUID> targetId, double followRangeBlocks)`, llamada cuando cambia el objetivo de una sesión y en cada latido:
      1. Sin objetivo: la sesión conserva su encuentro hasta que se cumplan las reglas de cierre (M06.4).
      2. Si el objetivo ya es participante de un encuentro con algún miembro a menos de `followRangeBlocks`, la sesión se une a ese encuentro.
      3. Si no, se crea un encuentro nuevo con la sesión como único miembro y el objetivo como primer participante.
    - Topes: 16 miembros y 16 participantes por encuentro. Un mob que excedería el tope crea otro encuentro. Los participantes que superan el tope se descartan por distancia en la captura (M07), pero conservan su alias si ya lo tenían.
  - Alias estables (I6): el primer jugador visto recibe `ParticipantAlias(1)`, el siguiente `2`, y así sucesivamente. El alias no cambia mientras el encuentro existe. El mapa de alias guarda como máximo 32 entradas.
  - Los UUID de jugadores solo existen dentro de `engine`; nunca salen hacia `adapter.jev` (I6).
- **Archivos:** crear `engine/Encounter.java`, `engine/EncounterRegistry.java` y `engine/ParticipantAliases.java` (asignación de alias estable por encuentro); prueba `EncounterRegistryTest.java`.
- **Reglas:** A3, A5, I2, I6, T1.
- **Pruebas:** tres mobs contra el mismo jugador comparten encuentro; dos grupos lejanos contra jugadores distintos forman dos encuentros; un jugador que entra tarde recibe el siguiente alias; el alias no cambia tras salir y volver dentro del mismo encuentro; el tope de 16 miembros abre un segundo encuentro.
- **Aceptación:**
  - [x] La búsqueda de encuentro por participante es O(1).
  - [x] Ningún alias se reasigna dentro de un encuentro.
  - [x] Ningún encuentro supera los topes.

### M06.4 Cierre y cambio de encuentro

- **Propósito:** liberar encuentros terminados sin temporizadores inventados, usando el estado de combate nativo.
- **Diseño:**
  - Un encuentro se cierra cuando se cumple una de estas condiciones:
    - No le quedan miembros activos.
    - Ningún miembro tiene objetivo y ni miembros ni participantes siguen en combate según `LivingEntity#getCombatTracker().isInCombat()` (vanilla lo mantiene 300 ticks tras el último daño) o según `ActiveMob.getThreatTable().inCombat()` cuando el mob tiene tabla de amenaza.
  - La comprobación se hace en el latido del encuentro: como máximo 32 llamadas O(1) por encuentro.
  - **Cambio de encuentro:** si el nuevo objetivo de un miembro pertenece a otro encuentro dentro de su `followRangeBlocks`, el miembro se mueve a ese encuentro; si no pertenece a ninguno, forma uno nuevo. No hay fusión global: dos encuentros se acercan solo por movimientos individuales. Así el coste por cambio es O(k·16), con k los encuentros abiertos del objetivo en ese mundo (normalmente 1 o 2); M13 lo mide con mobs dispersos.
  - Al cerrar: se descarta el historial, se liberan los alias y se eliminan las entradas del índice de participantes. Una respuesta en vuelo para un encuentro cerrado se descarta en M10 por identidad.
- **Archivos:** modificar `engine/EncounterRegistry.java`; la lectura de `isInCombat` se expone por `CombatReader` (M07) para respetar D2.
- **Reglas:** A3, A5, A6, D2, I2.
- **Pruebas:** con dobles de `CombatReader`: cierre por falta de miembros; cierre por fin de combate; permanencia mientras un participante sigue en combate; cambio de encuentro de un miembro; respuesta tardía de un encuentro cerrado sin efecto.
- **Aceptación:**
  - [x] Ningún encuentro sobrevive a su combate más de un latido — la regla de cierre está en `EncounterRegistry.closeIfFinished` y probada; el coordinador de M09 la llama en cada evaluación del encuentro, justo después de su captura, con `CombatReader::inCombat` (`CoordinatorTest.anEncounterClosesAtTheFirstHeartbeatAfterItsCombatEnded`).
  - [x] Ningún `EncounterId` se reutiliza durante el arranque.

### M06.5 `Agenda`

- **Propósito:** decidir qué encuentro evalúa en cada tick, con trabajo acotado y reparto justo.
- **Diseño:**
  - `final class Agenda` guarda por encuentro el siguiente tick de latido y el indicador de contexto pendiente. `DecisionTiming` aporta `heartbeatTicks` y `minimumDecisionIntervalTicks`.
  - `List<EncounterId> due(long tick, int maximum)` devuelve primero los encuentros con contexto pendiente que ya cumplieron `minimumDecisionIntervalTicks` desde su última decisión, y después los que alcanzaron su latido. Un encuentro con solicitud en vuelo no se devuelve (plan §7).
  - `decision.maxEncounterEvaluationsPerTick` (valor inicial 2) limita el trabajo por tick.
  - Equidad: una cola por mundo y recorrido round-robin entre mundos, de modo que un mundo con muchos combates no retrasa a otro indefinidamente.
  - Contexto pendiente: el `Coordinator` vacía `ContextSignals` cada tick (UUID marcados por `predictordecide`), busca la sesión y marca su encuentro. Los eventos de M07 marcan directamente por participante. Varias marcas se condensan en una sola.
  - Sondas baratas: `List<MobKey> dueProbes(long tick, int maximum)` devuelve las sesiones cuya paridad `(uuid.hashCode() + tick) % 2 == 0`, hasta `decision.maxProbesPerTick` (valor inicial 64). Así cada sesión recibe una sonda cada 2 ticks, alineada con la selección de metas vanilla.
- **Archivos:** crear `engine/Agenda.java`; prueba `AgendaTest.java` con `ServerClock` controlable.
- **Reglas:** A5, P2, C6.
- **Pruebas:** un encuentro con contexto pendiente se adelanta al latido; nunca se devuelve un encuentro con solicitud en vuelo; dos mundos con 10 y 1 encuentros reciben turno de forma alterna; el límite por tick se respeta; cada sesión recibe una sonda cada 2 ticks cuando el límite lo permite.
- **Aceptación:**
  - [x] Ningún tick evalúa más encuentros que el límite.
  - [x] Ningún encuentro espera más de su latido más una vuelta completa de la agenda.

### M06.6 Limpieza, reconciliación y métricas

- **Propósito:** cubrir eventos perdidos sin recorrer todos los mobs en un solo tick.
- **Diseño:**
  - Cada 200 ticks empieza una ronda de reconciliación que revisa como máximo 32 sesiones por tick con `MythicBukkit.inst().getMobManager().getActiveMob(uuid)`. Una sesión sin `ActiveMob`, o cuya entidad ya no es válida, se cierra con `SESSION_INVALID`.
  - En la misma ronda se cierran los encuentros sin miembros.
  - Métricas para `/thepredict status` (M12): sesiones activas y suspendidas, encuentros abiertos, cierres por causa y duración media de encuentro. Contadores fijos, sin series por UUID.
- **Archivos:** crear `engine/SessionReconciler.java`; el acceso a MythicMobs se hace a través de un método de `CombatReader` o de un pequeño método de consulta en `MobControl` (01, revisión 2), para respetar D2.
- **Reglas:** A5, A7, D2, C8.
- **Pruebas:** 500 sesiones se reconcilian en 16 ticks sin superar 32 por tick; una sesión fantasma se cierra en una ronda.
- **Aceptación:**
  - [x] Ningún tick revisa más sesiones que el límite.
  - [x] Las métricas no crecen con el número histórico de mobs.

## Riesgos y decisiones abiertas

- **Cobertura de eliminaciones:** si `EntityRemoveEvent` y los eventos de MythicMobs no cubren algún caso, la reconciliación lo corrige en la siguiente ronda.
- **Cambio de mundo:** si ninguno de los dos eventos cubre algún caso, la reconciliación detecta el mundo distinto al capturar (M07) y avanza la generación.
- **Encuentros fragmentados:** sin fusión global, dos grupos pueden seguir separados aunque se acerquen. Se mide en M13; si afecta a la calidad, se añade una fusión acotada por latido.
- **Incorporado en 01 (revisión 2):** añadir a `MobSession` los campos `installPending`, `installedRevision`, `nextProbeTick`, `lastTargetId` (`Optional<UUID>`) y `lastTargetDistanceBlocks`, usados por M05, M07 y M10.
- **Incorporado en 01 (revisión 2):** añadir `ParticipantAliases` (clase de `engine`) a la lista de estado mínimo de 01 §8.
- **Incorporado en 00 (revisión 2):** que `CombatReader` exponga `boolean inCombat(UUID entityId)` y `boolean isActive(UUID entityId)` para el cierre de encuentros y la reconciliación sin que `engine` dependa de Bukkit.
- **Incorporado en M03 (revisión 2):** las claves `decision.maxEncounterEvaluationsPerTick` y `decision.maxProbesPerTick` (M03).

## Terminado

- [ ] Subpuntos M06.1–M06.6 aceptados.
- [x] `SessionRegistryTest`, `EncounterRegistryTest` y `AgendaTest` en verde, con reloj controlable y sin `Thread.sleep`.
- [ ] Escenarios de ciclo de vida verificados en servidor con el guion de M01.
- [x] `MobDecisionStates` y `RotatingMobCursor` eliminados o marcados para retiro en M09 según W5.
- [x] `ArchitectureRulesTest` verde: `engine` no importa Bukkit ni MythicMobs.

## Estado de implementación

> **Local listo — servidor pendiente (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` (también con `--rerun-tasks`) pasa sin avisos de compilación ni de Javadoc: **669 pruebas, 0 fallos** (569 de partida + 92 nuevas + 8 de las correcciones tras la auditoría). Ninguna prueba hace llamadas de red ni lee `.env` (W6); ninguna prueba nueva usa `Thread.sleep` (P2). No se arrancó ningún servidor: las aceptaciones que exigen juego o el coordinador de M09 siguen sin marcar y no hay evidencia de servidor inventada. 00 no cambia; 01 §8 recibe tres filas y una entrada en su registro de cambios (desviación 4). El registro de decisiones de M01 recibe DT-20 y DT-21 y evidencia estática nueva en DT-05, todas `Abierta`; el guion de M01 recibe la sección 10 (el cierre pasa a ser la 11).

### Archivos

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…`) |
|---|---|
| M06.1 | Creados `engine/MobSession` (campos de 01 §8, `recordInstall`) y `engine/SessionRegistry` |
| M06.2 | Creados en `adapter/server/events/`: `LifecycleListener`, `ServerThread` (extraído de `ThePredictPlugin.runOnServerThread`), `MobScan` (escaneo inicial y tras recarga, 32 por tick), `MobDirectory` y `MythicMobDirectory` (consulta al gestor de mobs de MythicMobs). Creado `engine/SessionLifecycle` (eventos → registro, liberaciones y apagado T7) |
| M06.3 | Creados `engine/Encounter`, `engine/EncounterRegistry`, `engine/ParticipantAliases`, y tras la auditoría `engine/ParticipantIndex` y `engine/EventHistory` (paquete privado: índice por mundo y jugador, anillo de eventos) |
| M06.4 | `EncounterRegistry.closeIfFinished`, `leave`, `closeEmpty` y el cambio de encuentro en `assign` |
| M06.5 | Creados `engine/Agenda` y `engine/ProbeRing` (paquete privado: sondas por paridad) |
| M06.6 | Creados `engine/SessionReconciler` y `engine/SessionMetrics` |
| Raíz | Creado `SessionServices` (composición de M06 y tarea por tick hasta M09). `ThePredictPlugin`: quita los listeners de spawn, muerte, despawn y recarga y `runOnServerThread`; construye las sesiones, arranca el escaneo y la tarea, sigue un cambio de catálogo tras la verificación diferida de condiciones y apaga en el orden de T7. `StatusSnapshot` y `ThePredictStatusCommand`: línea `Sessions: …` |
| Depuración (W5) | `DebugProfileGoals`, `DebugCommandHandler` y `adapter/server/events/DebugAutoInstaller` usan la generación de la sesión cuando el mob la tiene, también en los presets; `debug release` devuelve la vista de nivel 4 a una sesión; `debug unbind` cierra la sesión; `debug goals` añade `generation=N` al final de su primera línea |
| Heredado | `adapter/mythicmobs/MythicPredictionRunner`: sin la limpieza de todos los mobs activos cada 1200 ticks. `MobDecisionStates` y `RotatingMobCursor`: Javadoc de retiro en M09 |
| Documentos | Este documento; `README.md` (fila de M06); `01-Modelo-de-dominio.md` (§8 y registro de cambios); `M01-Registro-de-decisiones.md` (DT-05, DT-20, DT-21); `M01-Guion-de-servidor.md` (sección 10 y paso 9.8) |

### Pruebas (669; 100 nuevas, 8 de ellas de las correcciones tras la auditoría)

- `engine` (con `ServerClock` controlable, sin `Thread.sleep`): `SessionRegistryTest` (13: generación 0 con vista base e instalación pendiente; dos instancias del mismo tipo independientes, caso trasladado de `MobDecisionStatesTest`; respuesta de generación vieja sin sesión; suspender y reanudar; abrir un mob suspendido o activo; `resume` inválido; avanzar descarta el estado de la generación anterior; suspendida sin vista; cerrar elimina toda referencia; `IntentBoard` en cada transición; reapertura tras cierre; recuentos); `MobSessionTest` (5: `Installed`, `Deferred`, `Unsupported` sin reintento hasta otra generación, reinicio de la generación, intención de otra generación rechazada); `EncounterRegistryTest` (21: tres mobs contra un jugador; dos grupos lejanos; un segundo grupo fuera de alcance forma su encuentro y los siguientes mobs se unen al que corresponde; 17, 18, 32 y 33 mobs contra un jugador dan 2, 2, 2 y 3 encuentros; 1 mob en un mundo y 4 en otro dan 2; cerrar el primero conserva el índice de los demás sin dejar claves vacías; otro mundo; tope de 16 miembros abre otro encuentro; tope de 16 participantes; alias del jugador tardío y estable al salir y volver; cambio de encuentro con cierre del vacío; permanencia con el objetivo propio; identidades 1, 2, 3 sin reutilizar; cierre por fin de combate y permanencia mientras alguien pelea; como máximo 32 llamadas a `inCombat`; el cierre descarta historial, alias e índice; suspensión y cambio de mundo; miembros con la generación actual; solo sesiones activas; anillo de 16 eventos; secuencias y red de seguridad); `ParticipantAliasesTest` (3: alias consecutivos; tope de 32 sin reutilizar índices ni quitar el de un participante; caso extremo); `AgendaTest` (12: encuentro nuevo y latido; contexto pendiente respeta el intervalo mínimo; 100 marcas producen un adelanto; nunca con solicitud en vuelo; dos mundos con 10 y 1 encuentros; límite por tick de 0 a 5 y negativo; 50 encuentros en dos mundos en 25 ticks alternando mundos; espera acotada por latido más una vuelta en 400 ticks; encuentro cerrado omitido y omitido por el llamador sigue vencido; sonda cada 2 ticks con 40 sesiones; límite, rotación y sesiones suspendidas; paridad con hashes negativos); `SessionReconcilerTest` (4: 500 sesiones en 16 ticks con 32 como máximo; fantasma cerrado en una ronda; ronda cada 200 ticks sin revisar suspendidas; encuentros vacíos); `SessionLifecycleTest` (13: spawn, descarga y carga, cambio de mundo, muerte, despawn y eliminación sin liberar, recarga con vista retirada antes de `release` en el tick siguiente, `Unsupported` reintentado tras recarga, liberación omitida si hay sesión nueva, 40 desvinculaciones liberadas en dos ticks, apagado con 250 sesiones que libera 200 con el tablero ya vacío, desvinculados pendientes primero, fallo de `release` contado, reconciliación en el tick, desvincular un mob suspendido no encola liberación).
- `adapter.server.events`: `LifecycleListenerTest` (10, con el `engine` real, dobles de MythicMobs, del planificador y proxies de `World`, `Chunk` y `Entity`, y eventos reales de Paper: spawn solo en el tick siguiente; tipo no vinculado; descarga inmediata y carga al tick siguiente; chunk vacío; `EntityRemoveEvent` con `UNLOAD` y con otra causa; muerte asíncrona; evento síncrono fuera del hilo del servidor; teletransporte en el mismo mundo, sin destino y por portal a otro mundo; comparación de mundos; recarga: primero el vínculo, luego avance o desvinculación y escaneo); `MobScanTest` (4: 100 mobs en 32, 32, 32 y 4; reinicio del escaneo; resolución por catálogo; instancia inactiva ni se abre ni se reanuda); `ServerThreadTest` (4); `DebugAutoInstallerTest` (2, nueva: el preset usa la generación leída al vaciar la cola, no al llegar el evento; sin sesión, la generación de depuración).
- Raíz: `SessionServicesTest` (5: escaneo y resumen de contadores fijos, fallo del tick registrado por tipo una vez cada 200 ticks, apagado, cambio de catálogo, sesión de depuración que reanuda una suspendida activa y nunca reabre una cerrada); `DebugProfileGoalsTest` (7, antes 4, con un `SessionRegistry` real: clave de sesión para instalar, intenciones y presets; `unbind` que cierra la sesión antes de liberar; vista de nivel 4 solo para una sesión activa y ninguna para una suspendida); `ThePredictStatusCommandTest` (línea `Sessions`); `ArchitectureRulesTest` (16, antes 15: `adapter.server.events` bajo D5 y D7, y `engine` sin Bukkit ni MythicMobs).

### Evidencia estática nueva (javap; no confirma comportamiento en juego)

- MythicMobs 5.13.1: `MythicMobSpawnEvent`, `MythicMobDeathEvent`, `MythicMobDespawnEvent` y `MythicReloadedEvent` llaman a `Event()`, así que son síncronos. `MythicMobSpawnEvent` solo se construye en `MobType`; `MythicMobDeathEvent`, en `BukkitSkillTriggers.onDeathTrigger` (`EntityDeathEvent`); `MythicReloadedEvent`, en `ReloadCommand`. `MythicMobDespawnEvent` sale de `BukkitBootstrap.handleMobDespawnEvent`, que lo programa con `Schedulers.of(entity).run`, llamado por `setDespawnedSync`, `setDespawned` y `setUnloaded`; este último solo lo hace si el `DespawnMode` despawnea al descargar y, si no, guarda el mob y lo quita del registro activo con `MobRegistry.setInactive`. `MobExecutor.getActiveMob(UUID)` lee ese registro.
- Paper 26.2 (servidor parcheado localmente, sin arrancar): `PaperEventManager.callEvent` lanza `IllegalStateException` para un evento síncrono fuera del hilo principal salvo durante el apagado. `EntitiesLoadEvent`, `EntitiesUnloadEvent`, `EntityRemoveEvent`, `EntityTeleportEvent` y `EntityPortalEvent` son síncronos (`Event()`, `WorldEvent(world, false)`). `CraftEventFactory.callEntityRemoveEvent` no emite nada para jugadores ni con causa nula; `PaperHooks.unloadEntity` emite `EntityRemoveEvent` con causa `UNLOAD` en cada descarga de chunk; un cambio de dimensión retira la entidad antigua con `setRemoved(CHANGED_DIMENSION, null)` (sin evento) y crea otra con el mismo estado (`restoreFrom`), así que sus metas se pierden. Todo `Entity.teleport(TeleportTransition)` de una entidad que no es jugador emite `EntityTeleportEvent` (o `EntityTeleportEndGatewayEvent`). `EntityPortalEvent` tiene su propia `HandlerList`. `EntityTeleportEvent.getTo()` puede ser nulo.

### Desviaciones y decisiones

| # | Desviación o decisión | Motivo |
|---|---|---|
| 1 | `SessionRegistry.close` devuelve `Optional<MobKey>` y `advanceGeneration` devuelve `Optional<MobSession>` (el documento dice `void`); hay además `advanceGeneration(UUID, TacticalProfile)` para la recarga, `leaveEncounter(UUID)`, `all()` y `count(SessionState)`. `open` de una sesión activa la devuelve sin cambios; `resume` exige una sesión suspendida | La liberación del tick siguiente necesita la clave cerrada; la recarga cambia el perfil además de la generación; el cambio de mundo sale del encuentro sin cambiar de estado. Operar sobre un UUID desconocido no hace nada, salvo `resume`, que es un error de programación (C7) |
| 2 | `EncounterRegistry.assign(MobSession, UUID worldId, UUID targetId, Predicate<MobKey> withinFollowRange)` en lugar de `assign(MobSession, Optional<UUID>, double followRangeBlocks)`. "Sin objetivo" es no llamar a `assign`. El índice de participantes (`ParticipantIndex`) tiene como clave el mundo y el jugador, y cada clave guarda en orden los encuentros abiertos que listan al jugador en ese mundo: `assign` se queda en el encuentro propio si ya tiene al objetivo, si no se une al primero que lo acepte (mismo mundo, con sitio y con un miembro en alcance) y si no forma uno nuevo que se añade a la clave. Cerrar un encuentro o quitarle un participante lo saca de cada clave y borra las claves vacías. `ofParticipant(worldId, playerId)` devuelve esa lista en O(1) | C4 prohíbe `Optional` como parámetro. `engine` no tiene posiciones (D2): quien llama (M07/M09) evalúa la distancia con datos de `CombatReader`. `worldId` es el de `Encounter` (01 §8). La primera versión indexaba un solo encuentro por jugador y fragmentaba los combates (20 mobs contra un jugador daban 5 encuentros); ahora dan `ceil(mobs / 16)` más uno por grupo fuera de alcance, y cada mundo forma los suyos sin que el índice oscile entre mundos |
| 3 | El cierre por fin de combate (`closeIfFinished(EncounterId, Predicate<UUID> inCombat)`) y la reconciliación (`SessionReconciler(…, Predicate<UUID> isActive, …)`) reciben las lecturas como predicados. En M06 la lectura viva de `isActive` es `MythicMobDirectory.isActive` (instancia activa, entidad válida y viva) | El puerto `CombatReader` y su implementación `CombatCapture` son de M07; M06.6 permite "un pequeño método de consulta". M07 y M09 pasarán `combatReader::inCombat` y `combatReader::isActive` sin tocar estas clases |
| 4 | Tipos de `engine` no previstos: `SessionLifecycle`, `SessionMetrics` (con los enums `SessionEnd` y `EncounterEnd`) y `ProbeRing`, `ParticipantIndex` y `EventHistory` (paquete privado). 01 §8 recibe `SessionLifecycle`, `SessionReconciler` y `SessionMetrics`, y el tope de `ParticipantAliases` pasa a "32 alias, 16 participantes" como dice M06.3 | C10 y D2: la traducción de eventos, las liberaciones, la reconciliación y los contadores no caben en los registros. Todos los cierres de sesión son `SESSION_INVALID`, así que las métricas por causa necesitan su propio enum. 01 decía 16 alias, en contradicción con M06.3 |
| 5 | Tipos de adapter y raíz no previstos: `MobDirectory`, `MythicMobDirectory`, `MobScan` y `SessionServices` | P4 (listeners probados con dobles), D8 (la raíz compone) y C10 (`ThePredictPlugin` no crece: 365 → 364 líneas) |
| 6 | La `Agenda` implementa `due(tick, max)` y `dueProbes(tick, max)` de M06.5 con el diseño de M09.5: un montículo por mundo con borrado perezoso, una vuelta con un encuentro por mundo, y `schedule`, `markContextPending`, `decided` y `remove`. "Primero los de contexto pendiente" se logra adelantando su vencimiento a `max(última decisión + intervalo mínimo, ahora)`, con desempate a su favor. Un encuentro devuelto sigue vencido hasta `decided` o `schedule`. `decided` programa en `max(latido, intervalo mínimo)` | Poner siempre los pendientes delante permitiría que un mundo o encuentro con señales continuas retrasara indefinidamente los latidos, contra la segunda aceptación de M06.5. Así un encuentro que el llamador omite (M09.5, "los no atendidos siguen vencidos") no se pierde |
| 7 | Los datos de programación del encuentro (`requestInFlight`, `contextPending`, siguiente y última decisión, secuencia) viven en `Encounter` como dice 01 §8; la `Agenda` solo guarda la cola. Latido = mínimo de los perfiles miembros; intervalo = máximo (M09.5) | Un solo dueño por dato (A3) |
| 8 | Tras desvincular, como máximo 32 liberaciones por tick (`MAX_RELEASES_PER_TICK`); las demás, en los ticks siguientes. Una liberación se omite si el mob tiene de nuevo una sesión, que instalará sus propias metas | Regla de trabajo acotado por tick: una recarga que desvincula cientos de mobs no hace cientos de `release` en un tick. La vista ya se retiró en la misma llamada, así que las condiciones son falsas y actúa la IA base del conjunto generado (A6, M05.4) |
| 9 | Solo se libera tras desvincular (recarga, cambio de catálogo o `debug unbind`) y al apagar; no tras muerte, despawn, eliminación o reconciliación | La entidad ya no existe o no tiene instancia activa; `GoalOwnership.release` no haría nada |
| 10 | Al apagar se liberan primero los mobs desvinculados que esperaban su liberación y después las sesiones activas en orden de apertura, hasta 200 en total; las suspendidas no se liberan | Las suspendidas están descargadas y `release` no las encuentra; las desvinculadas pendientes tienen metas de ThePredict sin sesión |
| 11 | `EntityRemoveEvent` con causa `UNLOAD` suspende en lugar de cerrar | Evidencia estática: se emite en cada descarga de chunk (`PaperHooks.unloadEntity`); cerrar rompería la reanudación. DT-20 |
| 12 | Se escuchan `EntityTeleportEvent` y `EntityPortalEvent` (`MONITOR`, `ignoreCancelled`, solo con destino y cambio de mundo por UUID). Si un viaje por portal emite los dos, la generación sube dos veces | "Mientras tanto se escuchan ambos" (M06.2). La generación solo es identidad; subir dos veces es inocuo. DT-21 |
| 13 | El spawn y la carga de chunk se atienden siempre en el tick siguiente con el planificador de Bukkit, sin mirar si el evento es asíncrono; la carga recoge los UUID de todas las entidades del chunk | "Un tick después" (M06.2); el planificador acepta tareas desde cualquier hilo y nada toca `engine` antes (T6). La tabla habla de "cada entidad con `ActiveMob`" sin limitar el tipo de entidad |
| 14 | `ServerThread.run` ejecuta en el acto solo un evento síncrono en el hilo principal; cualquier otro pasa al tick siguiente | T6 sin depender de la etiqueta del evento; Paper solo entrega un evento síncrono fuera del hilo principal durante el apagado |
| 15 | La recarga recorre todas las sesiones en una sola llamada (sin llamadas al juego) y después arranca un escaneo acotado (32 por tick) que abre los mobs cuyo tipo acaba de vincularse. Un cambio de catálogo sin recarga de MythicMobs (contexto desactivado por la verificación diferida de M03.5, dos ticks después) se trata igual | A6 exige que ninguna vista de la generación anterior sobreviva al tick; el trabajo caro (liberaciones) está acotado. El documento no decía qué pasa con los mobs de un tipo que pasa a estar vinculado; el plan cuenta el cambio de `Predictor` como cambio de generación |
| 16 | Un mob cuya sesión se cerró y vuelve a abrirse empieza de nuevo en la generación 0 | "Generación 0 la primera vez" y "cerrar elimina todas sus referencias". Una respuesta de la sesión anterior se descarta en M10 por identidad de encuentro, secuencia y revisión del catálogo |
| 17 | La reconciliación empieza su primera ronda en el primer tick y cada ronda nueva 200 ticks después del inicio de la anterior, si la anterior terminó; al empezar copia los UUID de las sesiones activas y cierra los encuentros vacíos | Coste acotado por tick para lo que consulta MythicMobs; la copia no llama al juego |
| 18 | `ParticipantAliases`: con 32 alias, un jugador nuevo quita el alias más antiguo de quien ya no es participante; los índices nunca se reutilizan. Un jugador que perdió su alias y vuelve recibe uno nuevo | A5 (tope) sin reasignar índices (I6). Con 16 participantes como máximo nunca pierde el alias un participante actual |
| 19 | `MobSession` incluye ya los campos de M07 y M10 (intención, rachas, último resultado, objetivo, hechos) con accesores públicos y mutadores de paquete; `lastTargetId` alimenta la regla de cierre de M06.4 | 01 §8 los asigna a `MobSession`; el estado solo cambia dentro de `engine` (A3) |
| 20 | El runner de OpenRouter deja de recibir eventos de ciclo de vida y pierde su limpieza de todos los mobs cada 1200 ticks; `MobDecisionStates` y `RotatingMobCursor` quedan marcados para retiro en M09 y conservan sus pruebas; los casos de aislamiento por UUID también están en `SessionRegistryTest` | "Su limpieza … pasa a la reconciliación acotada; el resto lo retira M09". El listener no puede llamar al runner (D7) y el runner nunca tiene proveedor en el esquema 2, así que no crea estado por mob. W5: todo sigue compilando y sus pruebas pasan |
| 21 | Comando de depuración: intención, instalación del perfil y de presets (`install <mob> <preset>` y `autoinstall`), `goals` y `unbind` usan la generación de la sesión. Antes de leerla, una sesión suspendida cuyo mob vuelve a estar activo se reanuda (`SessionServices.currentSession`, que nunca abre una sesión nueva), así que el resultado no depende de si la tarea del tick siguiente de una carga de chunk ya corrió; `DebugAutoInstaller` lee la generación al vaciar su cola, no al recibir el evento. Una intención nunca se publica para una sesión suspendida; `release` devuelve `IntentView.base` solo a una sesión activa; `unbind` cierra la sesión (causa `UNBOUND`) y libera en el tick siguiente como antes; `goals` termina su primera línea con `generation=N` | A6 y W5: una vista con la generación 0 sobre una sesión de otra generación sería de una generación inexistente, y `HoldGoal`/`FlankGoal` exigen `view.generation() == generation`, así que presets e intenciones deben usar la misma. Sin ello, tras una carga de chunk, un cambio de mundo o una recarga, los pasos 6.1–6.4 del guion (DT-08) darían falsos negativos. Las líneas del guion de M01 conservan su formato (el campo nuevo va al final) |
| 22 | `/thepredict status` muestra `Sessions: …` con sesiones activas y suspendidas, encuentros, cierres por causa, duración media y liberaciones pendientes o fallidas | Métricas de M06.6 visibles para el guion (sección 10); M12 rediseña la presentación |
| 23 | Tamaño de clases: `MobSession` 273, `Encounter` 251 (el anillo de eventos ya salió a `EventHistory`), `EncounterRegistry` 245, `SessionRegistry` 222, `DebugAutoInstaller` 218 y `LifecycleListener` 211 líneas | Son contenedores de los campos de 01 §8 y registros cuyas operaciones públicas llevan Javadoc (C9); ningún método supera 30 líneas. `ThePredictPlugin` (364) y `DebugCommandHandler` (283) son heredados y M12 los reduce |
| 24 | Hasta M09 hay una tarea periódica de sesiones (`SessionServices.tick`: escaneo, liberaciones y reconciliación) además de la del runner heredado | M09.7 deja una sola tarea: el `Coordinator` llamará a `SessionLifecycle.tick` |
| 25 | `SessionServices` construye los componentes de M06 fuera de `ThePredictPlugin` | D8 se cumple en su sentido: `SessionServices` está en el paquete raíz, es de paquete privado, solo la crea `ThePredictPlugin` y no usa estado estático ni singletons; separarla evita que la raíz crezca (C10). 00 no cambia |

### Pendiente de verificación en servidor

- Guion de M01, sección 10: escaneo inicial, sesión un tick después del spawn, suspensión y reanudación con generación nueva, muerte, despawn y eliminación, cambio de mundo por portal y por comando, `/mm reload` con y sin `Predictor`, y apagado con 250 mobs.
- DT-20 (cobertura de eliminaciones y descargas) y DT-21 (qué evento llega al cambiar de mundo); DT-05 (paso 10.2).
- Aceptaciones sin marcar de M06.2 (parte de servidor de la recarga con `Predictor: false`).

### Pendiente para módulos posteriores

- M07: crear `CombatReader` y `CombatCapture` con `inCombat` e `isActive`; construir `CaptureContext` con `Encounter::aliasFor`; añadir participantes con `EncounterRegistry.addParticipant` y filtrar eventos con `ofParticipant(mundo, jugador)`, que devuelve todos los encuentros abiertos del jugador en ese mundo; registrar eventos con `Encounter.record` (el descarte de repetidos en 10 ticks es de M07) y marcar contexto con `Agenda.markContextPending`; si la captura ve otro mundo, `SessionLifecycle.changedWorld`.
- M09 (hecho, ver el estado de implementación de M09): una sola tarea: el `Coordinator` llama a `SessionLifecycle.tick` y retira la de `SessionServices`; paso 5 con `MobSession.installPending` y `recordInstall(resultado, catalogRevision)` (50 sesiones en 5 ticks); `assign` al cambiar el objetivo y en el latido, `closeIfFinished` en el latido; `Agenda.due`, `decided`, `requestInFlight` y `dueProbes`; vaciar `ContextSignals` hacia `markContextPending`; retirar el runner, `MobDecisionStates` y `RotatingMobCursor` con sus pruebas.
- M10 (hecho): `adoptIntent`, rachas, último resultado y hechos en `MobSession`; descarte por identidad (`SessionRegistry.find(MobKey)`, encuentro cerrado); trasladar `nextChoiceTick` de `MobDecisionStates` a `MobSession` como compatibilidad de `extraCooldownTicks`.
- M11: ventanas de amenaza en `Encounter`, descartadas con él.
- M12: presentar `SessionMetrics` y los recuentos en `/thepredict status` e `inspect`; retirar `debug unbind` e `install … profile`. **Hecho en M12** (ver su [estado de implementación](M12-Diagnostico-instalacion-retiro-OpenRouter.md#estado-de-implementación)).
- M13: medir encuentros fragmentados por la ausencia de fusión global y el coste del escaneo y de la reconciliación con muchos mobs. **Medido localmente en [M13](M13-Suite-carga-latencia-calidad.md#estado-de-implementación)** (fragmentación, `assign` y reconciliación); la suite de carga encontró que `SessionUpkeep.prepare` devolvía a su encuentro anterior, con el objetivo de la última captura, a un mob recién colocado por una sonda en un encuentro nuevo, y formaba un encuentro por sonda: M13 lo corrigió (corrección 1 de M13, `SessionUpkeepTest`).

### Correcciones tras la auditoría (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **669 pruebas, 0 fallos** (8 nuevas). 00 no cambia; 01 no cambia en esta ronda.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** el índice de participantes guardaba un solo encuentro por jugador (`putIfAbsent`); con ese encuentro lleno, en otro mundo o fuera de alcance, cada mob siguiente formaba su propio encuentro (20 mobs contra un jugador daban 5; 1 mob en un mundo y 4 en otro, 5) y multiplicaba las solicitudes de M09 | `ParticipantIndex` (paquete privado) con clave (mundo, jugador) y la lista ordenada de encuentros abiertos que listan al jugador; `assign` recorre esa lista y solo forma un encuentro nuevo si ninguno acepta al mob; cerrar o quitar un participante lo saca de cada clave y borra las vacías; `ofParticipant(worldId, playerId)` (desviación 2) | `EncounterRegistryTest.mobsBeyondTheMemberCapFillTheExistingEncountersInsteadOfFormingOneEach` (17, 18, 32, 33 mobs → 2, 2, 2, 3), `mobsOfTwoWorldsAgainstOnePlayerFormOneEncounterPerWorld`, `aSecondGroupOutOfRangeFormsItsOwnEncounterAndLaterMobsJoinTheMatchingOne`, `closingTheFirstEncounterKeepsTheIndexOfTheOthersAndLeavesNoEmptyKey`; `SessionRegistryTest` y el cierre comprueban `participantKeys() == 0` |
| **Bloqueante (W5, A6):** `debug intent` publicaba con la generación de la sesión pero los presets instalaban metas de Paper con la generación 0, así que tras una recarga de chunk, un cambio de mundo o una recarga `hold` y `flank` dejaban de controlar (pasos 6.1–6.4, DT-08) | `install <mob> <preset>` usa `profileGoals.key(uuid).generation()`; `DebugAutoInstaller` recibe `mobs`, el instalador y `generationOf` inyectados y lee la generación al vaciar la cola; `SessionServices.currentSession` reanuda antes una sesión suspendida cuyo mob está activo, sin depender del orden de las tareas del tick (desviación 21) | `DebugAutoInstallerTest` (2), `SessionServicesTest.theDebugSessionResumesASuspendedActiveMobButNeverReopensAClosedOne`, `DebugProfileGoalsTest.aMobWithASessionUsesTheKeyOfItsSessionForInstallsIntentsAndPresets` |
| **Bloqueante:** la sección 10 del guion no se podía ejecutar: el mob de ejemplo usa `DespawnMode.NORMAL` (no se guarda, despawnea al descargar), y en 10.8 `debug goals` no encuentra un mob con `Predictor: false` | Evidencia `javap` de `DespawnMode.get` y de `MobType` (DT-20). La preparación añade `Options: { Despawn: false }` (modo `NEVER`) y un mob temporal sin esa opción para el despawn de 10.5; 10.8 desvincula quitando el perfil con `Predictor: true`, y 10.9 cubre `Predictor: false` con la línea `Sessions` y el comportamiento observado | — (guion) |
| `MobScan.discover` abría o reanudaba sin comprobar que la instancia siguiera activa | Comprueba `MobDirectory.isActive` (instancia registrada, entidad válida y viva) antes de abrir o reanudar | `MobScanTest.aMobWhoseInstanceIsNoLongerActiveIsNeitherOpenedNorResumed` |
| `releaseView` y la intención de depuración podían publicar una vista para una sesión suspendida | Solo se publica para sesiones activas; una intención sobre una sesión suspendida responde que se reintente en el tick siguiente | `DebugProfileGoalsTest.endingADebugIntentGivesAnActiveSessionItsBaseViewBackAndNeverPublishesForASuspendedOne` |
| Las sesiones `SUSPENDED` no tienen red de seguridad | Documentado en DT-20 y como pendiente en M13 (riesgos): un plazo de cierre necesitaría una clave de configuración nueva | — |
| `SessionServices` construye componentes fuera de `ThePredictPlugin` (lectura estricta de D8) | Justificado en la desviación 25; 00 no cambia | — |
| `Encounter` de 262 líneas antes de que M11 añada ventanas de amenaza | El anillo de eventos pasa a `EventHistory` (paquete privado); `Encounter` queda en 251 líneas | `EncounterRegistryTest.theHistoryKeepsTheLastSixteenEventsOldestFirst` |
| La recarga encolaba liberaciones inútiles para sesiones suspendidas desvinculadas | `SessionLifecycle.unbind` solo encola la liberación de una sesión activa | `SessionLifecycleTest.unbindingASuspendedMobQueuesNoRelease` |

