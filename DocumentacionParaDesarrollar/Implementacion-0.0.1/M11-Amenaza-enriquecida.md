# M11. Amenaza enriquecida

> **Estado:** Local listo — servidor pendiente (ver [Estado de implementación](#estado-de-implementación)) · **Entrega:** D · **Depende de:** M06, M07, M10 · **Desbloquea:** M12 · **Diseño:** [plan §5, "Objetivo y amenaza enriquecida"](../Plan-Jev-y-combate-tactico.md#objetivo-y-amenaza-enriquecida) y [plan §2](../Plan-Jev-y-combate-tactico.md#evidencia-estática-del-jar-suministrado) · Reglas: [00](00-Arquitectura-y-reglas.md) · Dominio: [01](01-Modelo-de-dominio.md)

## Objetivo

Mejorar la selección de objetivo de los mobs vinculados sin sustituir la tabla de amenaza de MythicMobs. ThePredict suma o resta amenaza a partir de hechos de combate que MythicMobs no mide, con pesos declarados en el perfil. La tabla nativa sigue eligiendo el objetivo; ThePredict solo le da mejor información. El resultado funciona con Jev y sin Jev, así que también mejora los niveles 3 y 4 del fallback (plan §8).

## Alcance

### Incluye

- Semántica, unidades y valores por defecto de `ThreatWeights` (tipo de 01, cargado por M03).
- Traducción de cuatro hechos a cambios de amenaza: asistencia a otros participantes, golpes bloqueados, acoso a distancia de quien no es el objetivo y objetivo inalcanzable sostenido.
- Un limitador por participante y tipo de hecho en una ventana fija.
- La aplicación con la API de `ActiveMob.ThreatTable` en el hilo del servidor.
- El hecho `ParticipantFacts.threatRank` para el estado que recibe Jev.
- Pruebas unitarias y escenarios de servidor.

### No incluye

- Que Jev elija objetivo. Queda fuera de la 0.0.1; cuando llegue usará `Taunt`, el mismo camino nativo (plan §1).
- Una tabla de amenaza propia o un selector de objetivo propio (regla A1).
- Duplicar opciones nativas: `ThreatTable.UseDamageTaken`, `ThreatTable.DecayUnreachable`, `ThreatTable.DropUnreachableSeconds` y `Options.MaxThreatDistance` siguen siendo de MythicMobs.
- Amenaza por hechos que exigen sondeo continuo. Todo cambio nace de un evento o de una decisión ya programada.

## Base existente

| Archivo | Acción |
|---|---|
| `adapter/paper/PaperCombatSnapshotReader.java` | Ya lee `getThreatTable().getTopThreatHolder()` para hallar el objetivo. M07 lo mueve a `adapter.server.context`; M11 reutiliza esa lectura para `threatRank`. (M07 la sustituyó por `CombatCapture` y ya calcula `threatRank`; M09 retiró este archivo.) |
| `ThePredictPlugin.java` | Registra hoy los listeners de ciclo de vida. M11 añade el registro del listener de amenaza mediante la raíz de composición (regla D8). (Implementado: la amenaza va en `CombatOutcomeListener`, que la raíz ya registra; desviación 1.) |
| — | No existe código de amenaza enriquecida. Todo lo demás es nuevo. |

## Subpuntos

### M11.1 Semántica y valores por defecto de `ThreatWeights`

- **Propósito:** fijar qué significa cada peso antes de aplicarlo, para que el autor pueda razonar sobre él.
- **Diseño:**
  - Unidad: un punto de amenaza de la tabla de MythicMobs. Con `ThreatTable.UseDamageTaken` activo, MythicMobs suma amenaza por el daño recibido; los pesos de ThePredict se expresan en esa misma escala. **M01 confirma** la relación exacta entre daño y amenaza en la build suministrada. Alternativa si no se confirma: documentar los pesos como "puntos de amenaza" sin equivalencia con el daño.
  - Claves del perfil v2 (esquema de M03): `Threat.AssistsOthers`, `Threat.RangedHarass`, `Threat.BlockedHits`, `Threat.UnreachableDecayPerSecond`, `Threat.MaxPerWindow`. Se mapean uno a uno a los componentes de `ThreatWeights`.
  - Valores por defecto propuestos, en `profileDefaults` de M03 (un solo lugar, regla C13):

    | Componente | Defecto | Significado |
    |---|---|---|
    | `assistsOthers` | 1.5 | Puntos por cada otro participante asistido en un mismo evento |
    | `rangedHarass` | 1.0 | Multiplicador sobre el daño final de un proyectil de un jugador que no es el objetivo |
    | `blockedHits` | 0.25 | Puntos por cada golpe del mob bloqueado con escudo |
    | `unreachableDecayPerSecond` | 2.0 | Puntos que pierde por segundo el objetivo inalcanzable |
    | `maxPerWindow` | 20.0 | Tope de puntos por participante y tipo de hecho en cada ventana |

  - Validación (M03): todos los valores finitos y `>= 0`; `maxPerWindow > 0`. Un peso `0` desactiva ese hecho.
  - Si el mob no tiene tabla de amenaza (`hasThreatTable()` falso), la amenaza enriquecida queda inactiva para ese mob y M12 lo muestra en `inspect`.
- **Archivos:** ninguno nuevo; M11 consume `domain.ThreatWeights` y la resolución de defaults de `config.ProfileDefaults` que entrega M03.
- **Reglas:** C5 (unidades), C13 (defaults en un lugar), A7.
- **Pruebas:** en M03, `ProfileLoaderTest` rechaza pesos negativos, no finitos o `maxPerWindow = 0`, y hereda defaults cuando falta `Threat`.
- **Aceptación:**
  - [x] La tabla de defaults está en `profileDefaults` y en la guía del autor (M14). `ProfileDefaults.THREAT` (1,5; 1,0; 0,25; 2,0; 20,0, M03) y la [guía del autor, §11](../../Guias/Guia-del-autor.md#11-amenaza-enriquecida) con pesos, unidades, tope por ventana, `DecayUnreachable` y mobs sin tabla (M14; `UserGuidesTest` comprueba que nombra cada campo de `Threat`).
  - [x] Un peso `0` desactiva su hecho sin errores (`ThreatPolicyTest.aZeroWeightOrAZeroMagnitudeDisablesTheFact`, `ThreatEnrichmentTest.aZeroWeightDisablesItsFactWithoutOpeningAWindow`, `EnrichedThreatScenarioTest.zeroWeightsLeaveTheNativeTableUntouchedForTheBaselineArm`).
  - [x] La unidad está documentada con la evidencia de M01 o con la alternativa: evidencia estática de DT-27 (con `UseDamageTaken`, MythicMobs suma un punto por punto de daño final) y, hasta el paso 13.1 del guion, la alternativa "puntos de amenaza".

### M11.2 Traducción de eventos a cambios de amenaza

- **Propósito:** convertir eventos nativos en un cambio de amenaza con una regla pura y probada.
- **Diseño:**
  - Separación en dos pasos (reglas A2 y D7):
    1. `adapter.server.events.ThreatEventListener` escucha los eventos nativos, descarta en O(1) los que no pertenecen a un encuentro activo y produce un `EncounterEvent` más la magnitud observada (número de asistidos o daño final).
    2. La regla pura `domain.ThreatPolicy` calcula el cambio a partir del `EventType`, la magnitud y `ThreatWeights`. **Incorporado en 01 §7 (revisión 2):** añadir `ThreatPolicy` a las reglas puras.
  - Tabla de hechos:

    | Hecho | Evento nativo | Condición | `EventType` | Cambio |
    |---|---|---|---|---|
    | Asistencia | `PotionSplashEvent` | Lanzador jugador participante; afectados que son otros participantes con al menos un efecto beneficioso | `ALLY_ASSISTED` | `+assistsOthers × asistidos` al lanzador |
    | Asistencia | `AreaEffectCloudApplyEvent` | Fuente jugador participante; mismas condiciones | `ALLY_ASSISTED` | Igual |
    | Golpe bloqueado | `EntityDamageByEntityEvent` | Atacante: mob vinculado; víctima: jugador participante con `isBlocking()` verdadero | `MELEE_BLOCKED` | `+blockedHits` a la víctima |
    | Acoso a distancia | `EntityDamageByEntityEvent` | Atacante: proyectil cuyo lanzador es un jugador participante; víctima: mob vinculado; el lanzador no es el objetivo actual del mob | `RANGED_HIT_ON_MOB` | `+rangedHarass × daño final` al lanzador |
    | Objetivo inalcanzable | Decisión programada (no evento) | `MovementProgress.pathReachesTarget` falso en dos decisiones seguidas | — | `−unreachableDecayPerSecond × segundos transcurridos` al objetivo |

  - Métodos de Bukkit usados: lanzador de la poción y de la nube, entidades afectadas, `getFinalDamage()` y lanzador del proyectil. **M01 confirma** sus firmas en Paper 26.2; la alternativa es leer el lanzador desde el proyectil o la nube con la API que exista.
  - "Efecto beneficioso": **M01 confirma** si `PotionEffectType` expone su categoría en 26.2. Alternativa: lista fija en `adapter.server.events` (curación instantánea, regeneración, fuerza, resistencia, absorción, velocidad, resistencia al fuego), documentada en la guía del autor.
  - El plan cita `ProjectileHitEvent` para el acoso. El daño final solo está en `EntityDamageByEntityEvent`, así que la amenaza se calcula allí; `ProjectileHitEvent` queda para el historial de fallos (`RANGED_MISS`, M07).
  - El acoso suma sobre lo que ya hace `UseDamageTaken`: el multiplicador expresa la amenaza **extra** por atacar sin ser el objetivo.
  - El decaimiento por inalcanzable no se aplica si el mob tiene activo `ThreatTable.DecayUnreachable`: el binder (M03) lee esa clave de `MythicMob.getConfig()` y desactiva el hecho para ese perfil, con un aviso.
- **Archivos:** crear `adapter/server/events/ThreatEventListener.java` y `domain/ThreatPolicy.java`; modificar la raíz para registrar el listener. (Implementado: sin `ThreatEventListener`; los pesos van en `CombatOutcomeListener` y en `engine.ThreatEnrichment`, desviación 1.)
- **Reglas:** A1, A2, D7, T1, T6, C12 (el filtro por encuentro activo es O(1) y sin asignaciones antes de descartar).
- **Pruebas:** `ThreatPolicyTest` cubre cada fila, magnitudes cero, pesos cero y valores no finitos rechazados. `ThreatEventListener` se prueba con una función de traducción separada que recibe datos simples (UUID del lanzador, UUID de afectados, daño) para no construir eventos de Bukkit.
- **Aceptación:**
  - [x] Cada fila de la tabla tiene una prueba de la regla pura (`ThreatPolicyTest`: asistencia, golpe bloqueado, acoso y objetivo inalcanzable, más tipos sin efecto, magnitud cero y valores no finitos).
  - [x] Eventos de entidades fuera de encuentros activos se descartan sin trabajo adicional (`ThreatEnrichmentTest.eventsOutsideAnActiveEncounterOrFromOtherPlayersDoNoWork`, `CombatOutcomeListenerTest.hitsBetweenStrangersNeverReachTheThreatTable`).
  - [x] No hay doble decaimiento con `DecayUnreachable` activo (`MythicProfileBinderSuiteTest.nativeUnreachableDecayReplacesTheProfileDecay` de M03 y `ThreatEnrichmentTest.theNativeUnreachableDecayReplacesOursWithoutAnyReading`; el paso 13.5 del guion lo observa en servidor).

### M11.3 Limitador por participante y tipo

- **Propósito:** impedir que un solo hecho repetido domine la tabla (plan §5).
- **Diseño:**
  - Ventana fija de 100 ticks, alineada con `RESET_DAMAGE_STATUS_TIME` de `CombatTracker` (plan §2).
  - Clave: `(EncounterId, UUID participante, EventType)`. Valor: puntos acumulados y tick de inicio de la ventana. (Implementado con la tabla del mob en la clave, `(EncounterId, MobKey, UUID participante, EventType)`, tope 16 × 16 × 3 = 768 por encuentro; desviación 2.)
  - Regla: el cambio aplicado es `min(cambio, maxPerWindow − acumulado)`; si el resultado es `<= 0`, no se aplica nada y se cuenta como "limitado" para diagnóstico.
  - Los cambios negativos (decaimiento) no pasan por el limitador; su tope es la amenaza actual del participante, que MythicMobs no deja por debajo de cero (**M01 confirma**; alternativa: limitar el decaimiento acumulado a `maxPerWindow` por ventana).
  - El estado vive en `engine.Encounter`, que ya es dueño de participantes e historial (regla A3). **Incorporado en 01 §8 (revisión 2):** añadir "ventanas de amenaza por participante y tipo" al contenido de `Encounter`, con tope igual a participantes × tipos.
  - Al cerrarse el encuentro (M06), las ventanas se descartan con él.
- **Archivos:** modificar `engine/Encounter.java` (creado en M06) y la lógica de aplicación en `engine/Coordinator.java` o en una clase de apoyo del engine si supera el tamaño de la regla C10.
- **Reglas:** A3, A5 (tope explícito), C6 (ticks para la ventana), C10.
- **Pruebas:** con `ServerClock` controlable (P2): acumulación hasta el tope, reinicio al cambiar de ventana, tipos independientes, participantes independientes y descarte al cerrar el encuentro.
- **Aceptación:**
  - [x] Ningún participante recibe más de `maxPerWindow` puntos de un mismo tipo por ventana, en la tabla de cada mob (`ThreatWindowsTest.aWindowAdmitsUpToItsCapAndOpensTheNextOneExactlyAtOneHundredTicks`, `ThreatEnrichmentTest.repeatedFactsAreCappedPerWindowAndTheNextWindowOpensAtItsBoundary`).
  - [x] Las ventanas no crecen sin límite y desaparecen con el encuentro (`ThreatWindowsTest.theWindowsNeverExceedTheirCapacityAndExpiredOnesMakeRoom`, `ThreatEnrichmentTest.theWindowsAndMarksDisappearWithTheEncounter`).

### M11.4 Aplicación con la tabla nativa

- **Propósito:** escribir la amenaza en MythicMobs sin romper su comportamiento.
- **Diseño:**
  - El engine llama al puerto `CombatActions.addThreat(MobKey, UUID, double)` (00 §4). **Ya implementado y probado en M10** (`CombatActionsAdapterTest.threatChangesOnlyReachAnOnlinePlayerInTheSameWorld`); M11 solo lo llama. La implementación en `adapter.server.execution.CombatActionsAdapter`:
    1. Resuelve el `ActiveMob` por el UUID de `MobKey`. La comprobación de que la generación sigue vigente (I1) la hace `engine` antes de llamar: el adapter no conoce las sesiones, y `engine` solo pasa la clave de la sesión actual en el mismo tick (desviación 9 de M10).
    2. Si `hasThreatTable()` es falso, no hace nada y devuelve sin error.
    3. Resuelve el jugador; si no está en línea o en otro mundo, descarta el cambio. (M11 descarta también a un jugador muerto; desviación 8.)
    4. Positivo: `getThreatTable().threatGain(BukkitAdapter.adapt(player), amount)`. Negativo: `threatLoss(BukkitAdapter.adapt(player), -amount)`.
  - Todo ocurre en el hilo del servidor (T1). Ningún callback HTTP aplica amenaza (T5).
  - No se usa `threatSet` ni `Taunt` en la 0.0.1: fijar valores absolutos anularía la lógica nativa.
  - Registro: solo con `decision.logDecisions`, con límite de frecuencia y sin nombres de jugadores (C8, I6); en el registro se usan alias.
- **Archivos:** `adapter/server/execution/CombatActionsAdapter.java` ya aplica los pasos 2 a 4 (M10); M11 añade las llamadas desde `engine` con la comprobación de generación del paso 1. (Implementado en `engine/ThreatChanges.java`, el único llamador de `addThreat`, que `ArchitectureRulesTest` vigila.)
- **Reglas:** A6 (si algo falla, no se aplica y el mob sigue con su tabla), T1, I1, C7, C8.
- **Pruebas:** doble de `CombatActions` en pruebas del engine que verifica llamadas y signos. En servidor: `inspect` (M12) muestra la amenaza sumada por ThePredict y la tabla cambia de líder cuando corresponde.
- **Aceptación:**
  - [x] Sin tabla de amenaza no hay errores ni efectos (`CombatActionsAdapterTest.threatChangesOnlyReachAnOnlinePlayerInTheSameWorld`, M10).
  - [x] Un jugador desconectado o en otro mundo no recibe amenaza, tampoco uno muerto, y una pérdida pasa las mismas comprobaciones (`CombatActionsAdapterTest.threatChangesOnlyReachAnOnlinePlayerInTheSameWorld` y `aDeadPlayerGetsNoThreatAndALossFollowsTheSameChecksAsAGain`).
  - [x] Ninguna llamada a la tabla ocurre fuera del hilo del servidor (`CombatActionsAdapterTest.everyActionIsRefusedOffTheServerThread`; `CombatOutcomeListenerTest.aHitSeenOffTheServerThreadChangesThreatOnlyOnTheServerThreadNextTick`; `ArchitectureRulesTest.enrichedThreatReachesTheNativeTableOnlyThroughOneWriterAndOneAdapter`).

### M11.5 Hecho de amenaza para Jev

- **Propósito:** que Jev sepa quién concentra la amenaza sin recibir números crudos.
- **Diseño:**
  - `ParticipantFacts.threatRank` (01 §4): `1` para `getTopThreatHolder()` del mob que decide y `2` para el resto de participantes. La API verificada no expone un listado ordenado; **M01 confirma** si existe. Si existe, el rango se amplía a `1..n` sin cambiar el tipo.
  - **Resuelto (M11, desviación 9):** el listado existe (`ActiveMob.ThreatTable.getThreatTable()` devuelve el mapa vivo `UUID → amenaza` y `asMap()` una copia; javap, DT-27), así que se aplica la ampliación `1..n`. "El resto con 2" chocaba con 01 §4 y M02 (`threatRank` único dentro del encuentro, que `CombatFacts` valida). M07 ya ordena por amenaza total sobre los miembros, después por distancia y después por orden de selección: rangos únicos y deterministas. "Líder" es el valor más alto de la tabla (el criterio de `targetHighestThreat`); `getTopThreatHolder()` devuelve el objetivo actual, que MythicMobs solo cambia cuando otro lo supera en un 10 %. Sin tabla, todas las amenazas son 0 y el orden sigue la distancia, en lugar de "todos con 2".
  - El codificador de M08 lo traduce a frases como `player_2 holds my highest threat` o `player_3 is assisting others` (esta última desde el historial `ALLY_ASSISTED`).
  - El rango se calcula en la captura de M07, no en cada tick.
- **Archivos:** modificar `adapter/server/context/CombatCapture.java` (M07) para rellenar `threatRank`; el texto lo produce `adapter/jev` (M08).
- **Reglas:** I6 (alias, nunca nombres), A1, C12.
- **Pruebas:** en M07 con dobles: líder con rango 1, resto con 2, sin tabla todos con 2. En M08: el codificador produce la frase esperada. (Con la resolución anterior: líder con 1 y el resto con rangos únicos `2..n`, también sin tabla; `FactsAssemblerTest.theLeaderOfTheTableHasRankOneAndEveryOtherPlayerAUniqueLowerRankWithOrWithoutATable` y `threatRanksAddTheThreatOfEveryMember`, `ParticipantSelectionTest.ranksFollowThreatThenDistanceThenSelectionOrderAndAreUnique`; frases en `SemanticPhrasesTest`.)
- **Aceptación:**
  - [x] El estado enviado nunca contiene valores numéricos de amenaza: `ParticipantFacts` solo lleva el rango, que M08 escribe en palabras (`SemanticPhrasesTest`; `SemanticEncoderTest.goldensCarryNoUuidNameOrPositionNumber` comprueba que no hay cifras salvo las referencias).
  - [x] El rango coincide con el líder de la tabla en la captura, entendido como el valor más alto (`FactsAssemblerTest.theLeaderOfTheTableHasRankOneAndEveryOtherPlayerAUniqueLowerRankWithOrWithoutATable`).

### M11.6 Pruebas y escenarios de servidor

- **Propósito:** demostrar que la amenaza enriquecida cambia decisiones de forma útil y medible.
- **Diseño:** escenarios en la arena de M13, cada uno con Predictor activo, primero sin `Threat` (pesos a cero) y después con los defaults:

  | Escenario | Configuración | Resultado esperado |
  |---|---|---|
  | Sanador | Un jugador cura con pociones arrojadizas a otro que combate | El sanador sube en la tabla y el mob lo elige antes que sin enriquecer |
  | Arquero que acosa | Un jugador tanquea cuerpo a cuerpo y otro dispara | El arquero gana amenaza extra acotada por ventana |
  | Bloqueos repetidos | Un jugador bloquea con escudo sin atacar | Gana poca amenaza; no roba el objetivo por sí solo |
  | Objetivo inalcanzable | El objetivo se sube a un pilar | Pierde amenaza gradualmente; con `DecayUnreachable` activo no hay doble decaimiento |
  | Varios jugadores | Cuatro jugadores con roles mezclados | Sin oscilación de objetivo en menos de una ventana |

- **Archivos:** crear `domain/ThreatPolicyTest.java` y las pruebas del limitador en `engine`; los escenarios se documentan en el guion de servidor de M13. (Implementado: `engine/ThreatWindowsTest`, `ThreatEnrichmentTest` y `EnrichedThreatScenarioTest`; los escenarios están en la sección 13 del guion de M01, que M13 reutiliza.)
- **Reglas:** P1, P2, P4, A8.
- **Pruebas:** las de M11.2 a M11.4 más los escenarios anteriores, con registro de eventos y de cambios de líder.
- **Aceptación:**
  - [ ] Cada escenario tiene evidencia registrada en el formato de M13.
  - [ ] La comparación sin enriquecer y con defaults está en el informe de M13.

## Riesgos y decisiones abiertas

- **Incorporado en 01 §7 (revisión 2):** añadir `ThreatPolicy` como regla pura (hecho y magnitud → cambio de amenaza). Firma real en la revisión 7.
- **Incorporado en 01 §8 (revisión 2):** añadir a `Encounter` las ventanas de amenaza por participante y tipo. Por tabla de mob en la revisión 7.
- Relación exacta entre daño y amenaza con `UseDamageTaken`: **M01 confirma**; afecta solo a la documentación de los pesos. Evidencia estática en DT-27 (un punto por punto de daño final); servidor pendiente (paso 13.1).
- Categoría de efectos de pociones en la API 26.2: **M01 confirma**; alternativa de lista fija. Resuelto por M07 con `PotionEffectType#getCategory()` (javap), sin lista fija.
- Listado ordenado de la tabla de amenaza: **M01 confirma**; alternativa de rango 1 y 2. Resuelto: existe (DT-27); rangos únicos `1..n` (desviación 9).
- Si el autor desactiva la tabla de amenaza del mob, M11 no hace nada; la guía del autor lo explica.

## Terminado

- Pruebas de `ThreatPolicy` y del limitador verdes; `ArchitectureRulesTest` verde.
- Escenarios de M11.6 ejecutados con evidencia.
- `inspect` de M12 muestra la amenaza añadida y el estado de la tabla.
- Guía del autor con pesos, unidades y límites (M14).
- Regla W4 de 00 cumplida.

## Estado de implementación

> **Local listo — servidor pendiente (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` (también con `--rerun-tasks`) pasa sin avisos de compilación ni de Javadoc: **1143 pruebas, 0 fallos** (1093 de partida + 50 nuevas, 10 de ellas de las dos rondas de correcciones tras la auditoría; ninguna existente cambió sus aserciones, solo se adaptaron constructores de fixtures). Ninguna prueba sale de la máquina ni usa `Thread.sleep` (P2): dobles de los puertos y reloj controlable. No hubo llamadas a TypeSafe ni a ningún otro host, nadie leyó `.env` (W6) y no se arrancó ningún servidor: las aceptaciones de M11.6 siguen sin marcar y no hay evidencia de servidor inventada. 00 no cambia; 01 recibe la revisión 7. El registro de M01 recibe DT-27 (`Abierta`) y una nota en DT-25; el guion recibe la sección 13 (el cierre pasa a la 14).

### Archivos

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…`) |
|---|---|
| M11.1 | Ninguno nuevo: `domain.ThreatWeights`, `config.ProfileDefaults.THREAT` y el binder de M03 (`BindingWarnings.threat` con `ThreatTable.DecayUnreachable`) |
| M11.2 | Creados `domain/ThreatPolicy` (cambio por hecho, pérdida por objetivo inalcanzable y tope por ventana) y `engine/ThreatEnrichment` (entrada de los listeners y de cada evaluación). Modificado `adapter/server/events/CombatOutcomeListener` (el mismo recorrido de M07 entrega golpe bloqueado, acoso y asistencia) |
| M11.2, decaimiento | Creado `engine/UnreachableDecay` (paquete privado). `engine/TacticalExecution` llama a la amenaza tras `NO_PROGRESS` en cada evaluación; su `Parts` gana `threat`. Javadoc de `engine/ExecutionSteps.evaluated` |
| M11.3 | Creado `engine/ThreatWindows` (paquete privado); `engine/Encounter` es su dueño (`threat()`) y lo vacía al cerrarse |
| M11.4 | Creado `engine/ThreatChanges` (paquete privado, único llamador de `addThreat`); `adapter/server/execution/CombatActionsAdapter.addThreat` descarta también a un jugador muerto; Javadoc de `engine/CombatActions.addThreat` |
| Métricas | Creado `engine/ThreatMetrics` (contadores fijos para M12) |
| Raíz | `SessionServices` recibe las `CombatActions`, construye `ThreatEnrichment` y la entrega al listener; `threat()` para el coordinador, cuya `TacticalExecution` le comparte su `ExecutionLog`. `DecisionServices` la pasa a `TacticalExecution.Parts`. `ThePredictPlugin` construye `CombatActionsAdapter` antes de las sesiones (`combatActions()` sin parámetro; los ajustes de movimiento se leen de las sesiones en cada cast) y la usa en los dos sitios |
| Documentos | Este documento; `README.md` (fila de M11); `01-Modelo-de-dominio.md` (revisión 7); `M01-Registro-de-decisiones.md` (DT-27, nota en DT-25, cabecera); `M01-Guion-de-servidor.md` (sección 13) |

### Pruebas (50 nuevas)

- `domain`: `ThreatPolicyTest` (11, dos de la auditoría: magnitud y peso enormes o infinitos que nunca desbordan ni pasan del tope, y pérdida finita; producto exacto por debajo del tope aunque la magnitud sola lo supere; asistencia por cada asistido, golpe bloqueado, acoso por daño final con dos pesos, pérdida por segundos con 1, 20, 30 y 0 ticks, tipos sin efecto, peso y magnitud cero, magnitudes negativas o `NaN` sin observación y tiempos negativos rechazados, tope con lo que queda, justo en el tope y agotado, ventana de 100 ticks).
- `engine` (reloj controlable, dobles de `CombatActions` y `MobControl`): `ThreatWindowsTest` (6, una de la auditoría: la marca reconoce solo la instancia de hechos de su captura; ventana con tope y siguiente ventana exactamente en el tick 100, también en 199 y 200; ventanas independientes por hecho, participante, tabla de mob y generación; tope de 768 ventanas con rechazo en el tick 99 y hueco en el 100; marca de inalcanzable con el mismo objetivo y generación, otro objetivo, otra generación y olvido; vaciado). `ThreatEnrichmentTest` (19, tres de la auditoría: daño enorme, infinito o `NaN` sin excepción y acotado por la ventana; valor inalcanzable viejo sin ruta viva que nunca resta; decisión sin captura nueva que reinicia la cuenta; golpe bloqueado en la tabla del atacante; acoso con el peso por el daño final, nada para el objetivo ni con daño negativo o NaN; asistencia por participante distinto en la tabla de cada miembro activo, sin contar al lanzador, a un extraño ni repetidos; asistencia sin otro participante o en otro mundo; mob desconocido, sin encuentro o suspendido y tirador que no participa, sin trabajo ni ventanas; tope por ventana con frontera en 99 y 100; peso cero sin ventana; pérdida desde la segunda decisión seguida por los segundos entre ellas y reinicio si en medio fue alcanzable; objetivo alcanzable sin lecturas de ruta, ruta que llega o sin objetivo; decaimiento nativo sin lecturas; otro objetivo o generación vieja solo marcan; generación nueva con su propia tabla y la clave vigente en cada cambio; ventanas y marcas que desaparecen con el encuentro; un fallo del puerto contado sin frenar a los demás miembros; registro con alias, sin UUID del jugador, sin asignar alias, solo con `logDecisions` y solo con el registro compartido; dos jugadores iguales reciben lo mismo sea cual sea su orden de llegada). `EnrichedThreatScenarioTest` (5, dos de la auditoría: la misma pérdida al mismo ritmo con Jev y sin Jev, y un mob sin ruta nunca vacía la amenaza de su objetivo en ningún nivel; sobre el coordinador de M09 y la ejecución de M10: los mismos hechos dan la misma amenaza en `LOCAL` sin Jev, en `BASE` y en `JEV`; el objetivo inalcanzable pierde 0,5 puntos en cada evaluación programada sin Jev y sin solicitudes; con pesos cero ninguna llamada a la tabla, el brazo sin enriquecer del protocolo A/B).
- `adapter.server.events`: `CombatOutcomeListenerTest` (+4, eventos reales de Paper con el `engine` real: golpe bloqueado con amenaza y sin bloquear sin ella; flecha de un participante que no es el objetivo con el daño final y ninguna para el objetivo; golpes entre extraños y flecha de quien no participa sin amenaza; evento fuera del hilo del servidor aplicado solo en el tick siguiente).
- `adapter.server.execution`: `CombatActionsAdapterTest` (+1: jugador muerto sin amenaza; una pérdida pasa por las mismas comprobaciones de mundo y conexión).
- `adapter.server.context`: `FactsAssemblerTest` (+1: sin tabla el más cercano tiene el rango 1 y todos rangos únicos; una ganancia que pone a otro jugador en cabeza le da el 1).
- `ArchitectureRulesTest` (+3: solo `ThreatChanges` llama a `addThreat`, también como referencia `::addThreat`, y solo `MythicCastSource` llama a `threatGain`/`threatLoss`; aparte, ningún archivo llama ni referencia `threatSet`, `Taunt`, `taunt` ni `reduceThreat`, ni muta el mapa de `getThreatTable()`; con cualquier espaciado antes de `(` o tras `::`, y una prueba de las propias expresiones con casos que deben y no deben coincidir).
- Adaptados sin cambiar aserciones: `ExecutionFixture` (construye `ThreatEnrichment`, registra cada cambio de amenaza y permite un objetivo inalcanzable), `ContextListenerFixtures`, `EngineFixtures` (perfil con pesos), `SessionServicesTest`, `DecisionServicesTest` y `ExecutionDoubles` (`isDead`).

### Evidencia estática nueva (javap; no confirma comportamiento en juego)

Registrada en DT-27. MythicMobs 5.13.1: `DamageTriggerListeners.onCombatTrigger` suma con `UseDamageTaken` el daño final (`EntityAttackMetadata.getFinalDamage`) al lanzador del proyectil o al atacante; `ThreatTable.threatGain` ignora al propio mob, a su dueño y a jugadores en `CREATIVE` o `SPECTATOR`, fija como objetivo a la entidad si no hay ninguno y cambia de objetivo solo por encima de `1,1 ×` la amenaza del actual; `threatLoss` no baja de 0 y, si era el objetivo, vuelve a elegir el máximo; `getTopThreatHolder()` es el objetivo actual; `getThreatTable()`/`asMap()` dan el listado; `tickThreat` descarta objetivos muertos, desconectados o en otro mundo y aplica `DecayUnreachable` (×0,96 sin línea de visión); `MythicMob.getThreatTableDecaysUnreachable()` existe. Paper API 26.2: `EntityDamageByEntityEvent#getDamager`, `EntityDamageEvent#getFinalDamage` (final), `Projectile#getShooter`, `Mob#getTarget`, `HumanEntity#isBlocking`, `Entity#isDead`, `OfflinePlayer#isOnline`. Servidor parcheado (sin arrancar): `LivingEntity.hurtServer` aplica `applyItemBlocking` antes de lanzar el evento con el modificador `BLOCKING`; `CombatTracker.RESET_DAMAGE_STATUS_TIME = 100`.

### Desviaciones y decisiones

| # | Desviación o decisión | Motivo |
|---|---|---|
| 1 | No se crea `ThreatEventListener`: golpe bloqueado, acoso y asistencia salen de `CombatOutcomeListener` en el mismo recorrido de M07, y solo después de que `ContextEvents` encuentre al miembro o al participante. La "función de traducción con datos simples" de M11.2 es `engine.ThreatEnrichment` (UUID, magnitud), probada sin eventos de Bukkit | M07, desviación 4 ("donde M11 añadirá sus pesos"): un solo manejador por evento y un solo filtro O(1); un segundo listener repetiría el filtro, la lectura de efectos y los afectados |
| 2 | La ventana del limitador es por **tabla de mob** (`MobKey`), participante y hecho, no solo por participante y hecho; tope 16 × 16 × 3 = 768 por encuentro, con las ventanas vencidas liberadas antes de rechazar; una generación nueva es otra tabla (01, revisión 7). `ThreatPolicy.change` devuelve `min(peso × magnitud, maxPerWindow)`, con peso cero o magnitud negativa o `NaN` → 0 (corregido en las dos rondas de la auditoría) | Cada mob tiene su propia tabla y los pesos (incluido `maxPerWindow`) son del perfil de cada mob; con una ventana compartida, acosar a un mob gastaría el tope de otro y un encuentro con perfiles distintos no tendría un tope único. El tope sobre el producto evita que `peso × daño` quede infinito sin cambiar la fórmula de M11.2 por debajo del tope (peso 0,5, daño 30, tope 20 → 15); limitar antes la magnitud daba 10 |
| 3 | `CombatSummaryReader.damageTotals` no se usa (pendiente de M07 cerrado); la magnitud del acoso es `EntityDamageEvent#getFinalDamage()` leído en `MONITOR` | Es O(1) y es el daño que recibió el mob. No es el mismo valor que suma `UseDamageTaken`: MythicMobs, en `HIGHEST`, suma `EntityAttackMetadata.getFinalDamage()` y lo fija como daño base (`setDamage`), y el `getFinalDamage()` de Bukkit en `MONITOR` es posterior a armadura, resistencia y absorción, así que es menor o igual (corregido tras la auditoría; DT-27, nota para M14). `damageTotals` recorre todo el `CombatTracker` y el registro en cada llamada y no identifica al tirador |
| 4 | Objetivo inalcanzable = el navegador sigue una ruta **viva** que no llega (`hasPath` y no `pathReachesTarget`, leído con `MobControl.progress` sin calcular nada). Sin ruta (`NONE`) nunca se resta, diga lo que diga el último valor conocido. Los hechos de la captura filtran antes, sin lectura (una captura lee primero esa misma ruta en curso, así que una ruta viva que no llega siempre sale inalcanzable), y deben ser de una captura nueva: una decisión cuya captura no leyó al miembro (misma instancia de `MobFacts`) reinicia la cuenta. Solo la segunda decisión seguida con el mismo objetivo y generación resta, por los ticks entre las dos; la pérdida es finita y sin tope (corregido tras la auditoría) | Con `NONE`, `pathReachesTarget` es falso sin más: sin Jev no hay permisos de ruta, la captura conserva "inalcanzable" para siempre y un mob pegado a su objetivo le quitaría 2 puntos por segundo, mientras que con Jev una ruta calculada lo corrige; exigir una ruta viva hace el decaimiento igual en `LOCAL`, `BASE` y `JEV` (plan §12). MythicMobs deja la amenaza en 0 como mínimo (DT-27) |
| 5 | Sin Jev no hay permisos de ruta (M09, desviación 8) y M09 no cambia | El decaimiento solo usa la ruta en curso del navegador, fresca en cada evaluación y la misma con o sin Jev (un mob que persigue a un objetivo en un pilar mantiene una ruta que no llega); los permisos de ruta de M09 solo cambian el último valor conocido, que ya no puede provocar una pérdida. `EnrichedThreatScenarioTest.theUnreachableDecayIsTheSameWithJevAndWithoutJev` y `aMobNextToItsTargetWithoutAPathNeverDrainsItsThreatAtAnyLevel` lo demuestran |
| 6 | El decaimiento corre en `ExecutionSteps.evaluated` (`TacticalExecution`, después de `NO_PROGRESS`), no en `Coordinator` | Es la "decisión programada" de M11.2, ocurre después de la captura y no toca la firma del coordinador; si la evaluación falla antes, no hay decaimiento en ella y la siguiente cubre el tiempo transcurrido |
| 7 | "El lanzador no es el objetivo actual" se lee con `Mob#getTarget()` en el momento del golpe, solo si el mob es miembro. La lectura ocurre después de la amenaza nativa del mismo golpe: MythicMobs, en `HIGHEST`, ya aplicó `threatGain` por el daño y pudo cambiar de objetivo, así que un tirador que acaba de pasar a ser el objetivo con ese golpe ya no recibe el acoso | `MobSession.lastTargetId` puede tener un latido de retraso; MythicMobs fija el objetivo de Paper desde su tabla (`setTarget`), así que es la misma elección |
| 8 | `CombatActionsAdapter.addThreat` descarta también a un jugador muerto (00 §4 no cambia: la firma es la misma) | `threatGain` sobre un jugador muerto lo metería en la tabla y, si el mob no tiene objetivo, lo fijaría como tal (javap, DT-27); A6 |
| 9 | `threatRank` único `1..n` en lugar de "1 y el resto con 2" (M11.5 resuelto con su propia cláusula) | El listado existe (DT-27) y 01 §4 y M02 exigen rangos únicos, que `CombatFacts` valida; lo implementó M07 y M11 añade la prueba del líder. "Líder" es el valor más alto de la tabla, no `getTopThreatHolder()` |
| 10 | Una asistencia cuenta cada participante distinto alcanzado (sin el lanzador) y va a la tabla de cada miembro activo de cada encuentro del lanzador; un repetido dentro de 10 ticks no entra en el historial (M07) pero sí en la amenaza, acotada por la ventana | La nube persistente se aplica muchas veces; el historial evita ruido para Jev y el tope por ventana evita que domine la tabla (plan §5) |
| 11 | El puerto es `void`, así que `engine` no ve los descartes del adapter: las líneas de `decision.logDecisions` dicen `Threat +<puntos> <HECHO> for player_<n> sent to the table of mob <uuid>` (o `for a player without alias`: el registro nunca asigna un alias) y los contadores de `ThreatMetrics` cuentan cambios enviados. Las líneas usan el **mismo** `ExecutionLog` que la ejecución táctica (`TacticalExecution` se lo comparte a `ThreatEnrichment` al construirse), así que el total sigue en 20 líneas cada 200 ticks; antes de compartirlo no se escribe nada. No se añade nada a `/thepredict status` | A7 sin cambiar 00 §4; mostrar los contadores es de M12 (`inspect` y `status`), y cambiar la línea `Sessions` alteraría una salida que el guion y `SessionServicesTest` fijan (W5) |
| 12 | `MobSession` no gana estado: las marcas de inalcanzable viven en `ThreatWindows` del encuentro | Indicación de M10 (no crecer `MobSession`, 374 líneas) y 01 §8 (las ventanas son del encuentro) |
| 13 | Tamaños: `SessionServices` 254 líneas (+14), `ThePredictPlugin` 459 (+7), `Encounter` 238 (+8), `CombatOutcomeListener` 219 (+45). Clases nuevas por debajo de 200; ningún método pasa de 30 líneas | Raíz de composición heredada que M12 reduce; `Encounter` es el contenedor de 01 §8; el listener crece sobre todo en Javadoc y en tres métodos pequeños. `ThreatEnrichment` se dividió en `ThreatChanges` y `UnreachableDecay` para quedar en 185 |

### Qué hace la amenaza ahora para los jugadores

MythicMobs sigue eligiendo el objetivo con su tabla; ThePredict solo le añade información, igual con Jev que sin Jev y en cualquier nivel de control. En un mob vinculado con tabla de amenaza, dentro de un encuentro abierto y con los pesos por defecto: quien cura o potencia a otro participante con una poción arrojadiza o persistente gana 1,5 puntos por cada otro participante alcanzado, en la tabla de cada mob del encuentro; quien bloquea con escudo un golpe del mob gana 0,25 puntos (no roba el objetivo por sí solo); quien dispara al mob sin ser su objetivo gana, además de la amenaza nativa por daño, tantos puntos como daño final recibió el mob tras armadura, resistencia y absorción. Cada tabla da como mucho 20 puntos a un mismo jugador por cada uno de esos tres hechos en cada ventana de 5 s. Si en dos decisiones seguidas el mob persigue a su objetivo por una ruta que no llega (por ejemplo, subido a un pilar), ese objetivo pierde 2 puntos por segundo hasta que la ruta vuelve a llegar, el mob deja de tener ruta o la amenaza queda en 0, salvo que el mob ya use `ThreatTable.DecayUnreachable`; un mob quieto junto a su objetivo nunca le resta nada, con Jev o sin Jev. Un jugador muerto, desconectado o en otro mundo no recibe nada; un mob sin tabla no cambia. Jev solo ve el rango de cada jugador en palabras (`player_2 is the highest threat`), nunca números.

### Pendiente de verificación en servidor

- Sección 13 del guion de M01 (13.1 a 13.8, dos jugadores y cuatro en 13.7) y DT-27: escala de la amenaza con `UseDamageTaken`, suelo de `threatLoss`, evento del golpe bloqueado, objetivo de Paper frente al de la tabla, placeholder `<caster.tt.top>`.
- Aceptaciones sin marcar: M11.6 (escenarios con evidencia y comparación sin enriquecer y con defaults en el informe de M13) y la parte de guía de M11.1 (M14).

### Pendiente para módulos posteriores

- M12: mostrar en `inspect` el estado de la tabla de cada mob y en `status` los contadores de `ThreatEnrichment.metrics()` (ganancias por hecho, pérdidas, limitadas, fallos y puntos enviados). **Hecho en M12** (ver su [estado de implementación](M12-Diagnostico-instalacion-retiro-OpenRouter.md#estado-de-implementación)).
- M13: escenarios de M11.6 con y sin pesos (13.8 es el brazo sin enriquecer); calibrar los pesos por defecto, la ventana de 100 ticks, la definición de inalcanzable (dos decisiones) y `MELEE_BLOCKED` por `isBlocking()` (un golpe por la espalda con el escudo levantado cuenta como bloqueado, M07).
- M14: guía del autor con pesos, unidades (puntos de daño final según DT-27 o la alternativa; el acoso usa el daño tras armadura, resistencia y absorción, menor o igual que el que suma `UseDamageTaken`), el tope por tabla y ventana, la interacción con `DecayUnreachable` y que un mob sin `Modules.ThreatTable` no usa la amenaza enriquecida.

### Correcciones tras la auditoría (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **1141 pruebas, 0 fallos** (8 nuevas). Sin llamadas de red, sin leer `.env` y sin servidor. La segunda ronda está en la tabla siguiente.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante (A6, C7):** con peso > 1, `rangedHarass × daño` podía dar `+Infinity` (peso 2,0 y daño 1e308) y `admitted` lanzaba `IllegalArgumentException` en un manejador `MONITOR` en cada evento | `ThreatPolicy.change` limitaba la magnitud a `maxPerWindow` antes de multiplicar y el resultado a `maxPerWindow` (sustituido en la segunda ronda por el tope sobre el producto); una magnitud negativa o `NaN` no es una observación y da 0; una infinita cuenta como el tope; la pérdida por inalcanzable es siempre finita (desviación 2) | `ThreatPolicyTest.aHugeMagnitudeOrWeightNeverOverflowsAndNeverPassesTheCap`, `negativeOrNotANumberMagnitudesAreNoObservationAndNegativeTimesAreRejected`; `ThreatEnrichmentTest.aHugeOrNonFiniteDamageNeverThrowsAndIsCappedByTheWindow` |
| **Bloqueante (plan §12):** con la ruta `NONE`, `pathReachesTarget` es falso sin más; sin Jev el último valor "inalcanzable" quedaba fijo y un mob pegado a su objetivo le restaba 2 por segundo, con Jev no | Solo resta una ruta viva que no llega (`hasPath && !pathReachesTarget`); sin ruta nunca (decisión del coordinador; desviaciones 4 y 5) | `ThreatEnrichmentTest.aStaleUnreachableValueWithoutALivePathNeverDecays`; `EnrichedThreatScenarioTest.theUnreachableDecayIsTheSameWithJevAndWithoutJev`, `aMobNextToItsTargetWithoutAPathNeverDrainsItsThreatAtAnyLevel` |
| La regla de arquitectura se podía esquivar con referencias de método o con `threatSet`/`Taunt` en otro archivo | Llamadas y referencias `::addThreat` solo en `ThreatChanges`; llamadas a `threatGain`/`threatLoss` solo en `MythicCastSource`; prueba aparte: ningún archivo con `threatSet(`, `::threatSet`, `Taunt(` ni `::Taunt` | `ArchitectureRulesTest.enrichedThreatReachesTheNativeTableOnlyThroughOneWriterAndOneAdapter`, `noSourceFixesReducesOrTauntsThreatOrEditsTheTableMapInThisRelease` (ampliada en la segunda ronda) |
| `ThreatEnrichment` creaba su propio `ExecutionLog`: 20 + 20 líneas cada 10 s y el aviso de líneas suprimidas decía "Tactical execution" | `TacticalExecution` comparte su `ExecutionLog` con `ThreatEnrichment` al construirse; un solo límite y un solo aviso; `SessionServices` recibe solo las `CombatActions` (el record `ThreatWiring` desaparece) (desviación 11) | `ThreatEnrichmentTest.eachChangeIsLoggedWithTheAliasOnlyAndOnlyWithDecisionLogging` |
| "El mismo valor que `UseDamageTaken`" era falso | Javadoc de `CombatOutcomeListener`, desviación 3 y DT-27 corregidos: el daño de Bukkit en `MONITOR` es menor o igual que el que MythicMobs suma en `HIGHEST`; nota para M14 | — |
| La lectura del objetivo ocurre después de la amenaza nativa | Documentado en la desviación 7 y en el Javadoc del listener | — |
| El registro llamaba a `aliasFor`, que asigna un alias como efecto secundario; import sin uso en `ThreatChanges` | `Encounter.aliases().find`; sin alias, `a player without alias`; import retirado | `ThreatEnrichmentTest.eachChangeIsLoggedWithTheAliasOnlyAndOnlyWithDecisionLogging` |
| Guion: solo 13.7 necesita cuatro jugadores | Preparación corregida; 13.4 aclara que solo resta mientras el mob sigue una ruta que no llega | — |
| El decaimiento usaba los hechos viejos de una captura vacía | Una decisión cuya captura no leyó al miembro (misma instancia de `MobFacts`, `ThreatWindows.sameCapture`) no resta y reinicia la cuenta | `ThreatEnrichmentTest.aDecisionWithoutANewCaptureRestartsTheCount`, `ThreatWindowsTest.aMarkRecognisesTheVeryFactsOfItsCaptureOnly` |

### Segunda ronda de correcciones (2026-09-29)

`./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **1143 pruebas, 0 fallos** (2 nuevas). Sin llamadas de red, sin leer `.env` y sin servidor.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** limitar la magnitud antes de ponderar cambiaba la fórmula de M11.2 con peso < 1 (peso 0,5, daño 30, tope 20 daba 10 en vez de 15; tope 5, daño 8, peso 0,5 daba 2,5 en vez de 4) | `ThreatPolicy.change`: peso 0 → 0; magnitud negativa o `NaN` → 0; si no, `min(peso × magnitud, maxPerWindow)`. Un producto infinito cae en el tope y no puede salir `NaN` porque el peso 0 se resuelve antes (desviación 2) | `ThreatPolicyTest.aProductBelowTheCapIsExactEvenWhenTheMagnitudeAloneIsAboveIt`; siguen `aHugeMagnitudeOrWeightNeverOverflowsAndNeverPassesTheCap` (MAX_VALUE, 1e308, +Inf, peso cero con +Inf) y `negativeOrNotANumberMagnitudesAreNoObservation…` |
| La regla de arquitectura dejaba pasar `reduceThreat` y `taunt(` de `BukkitAPIHelper`, las mutaciones del mapa de `getThreatTable()` y los nombres con espacios antes de `(` o tras `::` | Expresiones regulares sobre el texto completo: llamadas y referencias a `threatSet`, `Taunt`, `taunt` y `reduceThreat`; `getThreatTable()` seguido de `put…`, `merge`, `replace…`, `remove…`, `compute…` o `clear`; `addThreat` y `threatGain`/`threatLoss` con cualquier espaciado. Nada de producción las viola | `ArchitectureRulesTest.noSourceFixesReducesOrTauntsThreatOrEditsTheTableMapInThisRelease`, `theThreatRulesSeeCallsAndReferencesWhateverTheirSpacing`, `enrichedThreatReachesTheNativeTableOnlyThroughOneWriterAndOneAdapter` |
