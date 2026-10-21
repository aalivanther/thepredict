# IA para videojuegos y Minecraft: estado del arte (septiembre de 2026) y camino de ThePredict después de la 0.0.1

> **Tipo:** investigación (sin cambios de código). **Fecha:** 2026-09-30. **Alcance:** web pública; fuentes primarias siempre que fue posible. No se leyó `.env` ni se llamó a `api.typesafe.ai`.
>
> **Base local leída (solo lectura):** [Informe de la 0.0.1](../Implementacion-0.0.1/Informe-0.0.1.md), [README](../../README.md), [plan de Jev y combate táctico](../Plan-Jev-y-combate-tactico.md), [M15](../Implementacion-0.0.1/M15-Primitivas-tipadas-de-skills.md), [M16](../Implementacion-0.0.1/M16-Efectividad-de-las-respuestas-de-Jev.md), [M17](../Implementacion-0.0.1/M17-Participantes-y-objetivos-como-entidades.md), [M18](../Implementacion-0.0.1/M18-Duelo-de-mobs-controlados.md) y la [matriz local del duelo](../Implementacion-0.0.1/evidencia/m18-simulador-2026-09-29/duel-local.md).
>
> **Convenciones:**
> - `[n]` remite a la lista de referencias (sección 12). Cada entrada lleva su fecha de publicación (si se conoce) y la fecha de consulta.
> - **NO VERIFICADO** marca lo que no pudo confirmarse en una fuente primaria.
> - **Inferencia** marca una conclusión propia, no una afirmación de la fuente.
> - Las cifras de potencia estadística de la sección 7 son **cálculos propios**.

---

## Resumen ejecutivo

**Dónde está ThePredict.** Su arquitectura coincide con la que la industria y la investigación convergieron en 2025–2026:

- una capa rápida que **nunca espera al modelo** (metas nativas de MythicMobs y vanilla);
- una capa lenta que fija la **intención** (Jev);
- opciones cerradas y tipadas en lugar de texto libre;
- umbral de confianza con fallback local.

El PUBG Ally de KRAFTON/NVIDIA (árbol de comportamiento a ritmo de tick, SLM asíncrono) [36] sigue el mismo patrón, igual que PIANO de Altera [151] y el planificar-actuar en paralelo aplicado a jefes de Minecraft [156]. No se encontró ningún plugin público que use un modelo remoto para elegir tácticas de jefes de MythicMobs (ausencia no demostrada; sección 3.6).

**Qué dicen los datos propios.** La latencia de Jev y su precio no son el cuello de botella. Lo es **la función de valor de las tácticas y el ritmo de ejecución**. En el simulador de M18 (200 semillas), el lado de ThePredict pierde contra el clásico:

| Modo | Puntuación de ThePredict en el brazo A | Intervalo Wilson 95 % |
|---|---|---|
| Sin clave (`NONE`) | 0,255 | [0,200; 0,320] |
| Oráculo | 0,055 | — |

El oráculo, que puntúa con la misma función que el plan local, lo hace *peor*: la función que define "la mejor táctica" está desalineada con el resultado. Hay dos síntomas más:

- `keep_distance_and_bolt` ocupa 1 175 ventanas con efecto medio 0,007, frente a 0,225 de `press_and_strike`.
- ThePredict lanza 2 610 `DuelStrike` frente a 3 527 del clásico.

Esto confirma la preferencia por el juego a distancia y el problema de cadencia que el usuario ya identificó.

**Qué hacer después (orden recomendado):**

1. **Ritmo y disponibilidad.** Disponibilidad nativa (`Cooldown`, `Charges`, `skillIsRecharging`), golpe base sin consumir la táctica (DT-42) y más de una acción por época cuando la skill lo permita. Es barato y ataca la causa medida.
2. **Valor de táctica aprendido del resultado.** Usar `OutcomeWindow` como prior del plan local y como hechos para Jev (patrón del selector de horizonte de DEPS [144]). Añadir vigilancia de colapso táctico: un estudio de 2026 vio a un LLM elegir la misma táctica el 83,8 % de las veces [40].
3. **Evaluación v2 antes de afirmar mejoras:**
   - duelos pareados;
   - SPRT;
   - "jugadores sintéticos" que usan escudo, torre y kiting;
   - barrido de latencia;
   - un oráculo definido por resultado y no por la misma función del plan.
4. **Lectura automática de metadatos de MythicMobs** (`Cooldown`, `Charges`, `Conditions`, `TargetConditions`, `MetaskillCondition`) [102][103].
5. **Posturas con el `stance` nativo de MythicMobs** y telegrafía explícita (anticipación, activo, recuperación), con Jev respondiendo una pregunta `Score` ordenada [5][106][107].
6. **Coordinación de grupo con fichas por objetivo** (patrón "Belgian AI"/DOOM/God of War) [170][175][176], resuelta en código y no por el modelo.

**Qué evitar:**
- LLM generativo por tick;
- pathfinding propio (Paper rechazó el pathfinding asíncrono en julio de 2026 [80]);
- control de movimiento de mobs con `Brain`;
- DDA que toque estadísticas visibles;
- Folia antes de que Folia 26.2 salga de beta [88];
- afirmar superioridad con 20 rondas: detectan solo diferencias de ≈ 80 % frente a 50 %.

---

## 1. Técnicas de IA de juego 2024–2026 para combate de NPCs y jefes

### 1.1 Resumen comparativo

| Técnica | Fortalezas en combate y jefes | Debilidades | Latencia y coste en ejecución | Control y autoría | Ejemplos verificados |
|---|---|---|---|---|---|
| **FSM / HFSM / StateTree** | Fases de jefe, enfurecimiento y ventanas de aturdimiento: deterministas y legibles | Explosión combinatoria cuando interactúan muchos factores | Microsegundos | Máximo; el diseñador lee el grafo | StateTree de UE5 (HFSM con selectores de BT) [18]; F.E.A.R.: FSM de solo 3 estados + planificador [20]; DOOM 2016: HFSM en 16 arquetipos [170] |
| **Árboles de comportamiento (BT)** | Reactivos, depurables; estándar de la industria | Prioridades fijas por orden; anidación profunda de condiciones | Microsegundos por agente | Alto | Unity Behavior 1.0 ("behavior graphs") [19]; sistema 1 de PUBG Ally [36]; Teammates de Ubisoft [39] |
| **GOAP** | Secuencias emergentes ("flanquea porque la cobertura está bloqueada") | Menos "autoral", más difícil de afinar y depurar; coste de búsqueda | A* sobre estados del mundo; crece con las acciones | Medio | F.E.A.R. [20]; panel GDC 2015 (Mordor, Tomb Raider, Deus Ex según resumen; **NO VERIFICADO** en la charla) [21] |
| **HTN** | "Guion de jefe con ramas": planes controlables y legibles | Coste de autoría; novedad limitada a los métodos escritos | Rápido; en *Transformers: Fall of Cybertron* fue más rápido que su GOAP previo [23] | Alto | Decima (Killzone, Horizon ZD/FW) [22] |
| **Utility AI (IAUS, DA:I)** | Elección suave y contextual; cooldowns y recursos naturales | Afinar curvas es el trabajo real; oscila con empates; no planifica secuencias | Lineal en acciones × consideraciones; barato | Alto con bandas de puntuación | Guild Wars 2 HoT [24][25]; *Dragon Age: Inquisition* [26] |
| **Mapas de influencia / consultas tácticas (EQS)** | Elegir *dónde* ponerse: cobertura, línea de tiro, flanco | Coste de muestreo; ruido si cambian cada frame | Proporcional a candidatos × pruebas | Alto | EQS de UE [27]; mapas modulares de Mark [28]; Killzone [178] |
| **MCTS** | Estrategia por turnos o campaña | Sin uso verificado en combate cuerpo a cuerpo en tiempo real | Alto por decisión | Bajo | Campaña de *Total War: Rome II* [29]; PokéChamp (LLM + minimax, 84 % frente al mejor bot de reglas) [30] |
| **RL** | Habilidad fuerte y "humana" | Reentrenar tras cada cambio de diseño; control débil; necesita simulador rápido | Inferencia barata; entrenamiento caro | Bajo (se recupera con *action masking*) | GT Sophy (carreras) [31]; *Roller Champions*, reentrenado en 1–4 días [32]; bots de *For Honor* con RL + BT y *action masking* [41] |
| **Aprendizaje por imitación** | Estilo humano con pocos datos | Sesgo del conjunto de datos; no generaliza a reglas nuevas | Barato en inferencia | Bajo | EA SEED, sobre todo para pruebas (−39 % de tiempo de navegación en BF2042) [33]; bots de modl.ai para Riot (prototipo, no publicado) [35] |
| **Agentes LLM/VLM** | Lenguaje, intención, adaptación "creíble" | Segundos por decisión; colapso de modo; razonamiento espacial pobre | 1–2 s (Teammates) [39]; 5–25 s en PillagerBench [155] | Bajo sin capa clásica | Teammates de Ubisoft [38][39]; jefe "Asterion" de MIR5 (anunciado; sin detalles técnicos ni lanzamiento verificado) [37] |
| **Híbrido LLM + clásico** | El modelo fija intención o parámetros; la capa clásica siempre actúa | Coherencia entre capas; el modelo puede colapsar a una táctica | La capa rápida no espera; el modelo va aparte | Alto si el modelo solo elige entre opciones del diseñador | PUBG Ally: BT a ritmo de tick que "never waits for language model inference" + Mistral-NeMo-Minitron-2B local [36]; Teammates: STT → LLM → EQS → BT [39] |

### 1.2 Lecturas relevantes para combate

**El "snippet" de *Dragon Age: Inquisition* es el análogo publicado más cercano a M15** [26]:
- Cada uso de habilidad es un fragmento con un **árbol de evaluación**, que devuelve una puntuación y un objetivo, y un **árbol de ejecución** (un BT que se acerca y dispara).
- Una misma habilidad puede tener varios fragmentos; *Charging Bull* sirve para entrar o para huir.
- Los diseñadores puntúan por **bandas**: básica 10, ofensiva 20–40, apoyo 25–45, reacción 50–70. Las reacciones a amenazas inminentes siempre ganan.
- Un personaje complejo registra más de 50 fragmentos; la media es 10–20.

**IAUS en *Guild Wars 2: Heart of Thorns*** [24]:
- Cada consideración se normaliza a [0,1] y se multiplica.
- Un 0 veta la acción y corta la evaluación temprano: las comprobaciones baratas van primero y los rayos de visión al final.
- Contra la oscilación entre acciones casi empatadas: un **bono de compromiso** para la última elección, cooldowns como consideración o una consideración que desempate. ThePredict ya aplica histéresis (`tacticSwitchMargin`, duración mínima; plan §6).

**Colapso táctico en un híbrido LLM + RL** [40] (Unity, 2026-08-27):
- Un Mistral-7B elige una de 4 etiquetas tácticas cada 5 s para PPO.
- Duplica la tasa de victoria contra un rival "Balanced" (11 % → 24 %).
- Pero elige "Surround" el **83,8 %** de 2 430 veces, sea cual sea el rival, y rinde peor contra el rival agresivo.
- Es la advertencia más directa para ThePredict: hay que medir la **distribución** de tácticas elegidas y no solo la tasa de victoria.

**Híbrido publicado de referencia: PUBG Ally** [36] (2026-06-25):
- La capa rápida es un BT a ritmo de tick; el SLM se ocupa de intención, coordinación y habla.
- Usa llamadas a herramientas para observar y un **prefijo de prompt estable** para reutilizar la caché KV.
- No publica cifras de latencia.
- El modelo bajó de 8B (anuncio CES 2025 [37]) a 2B en producción; el motivo no se publicó.

---

## 2. IA específica de Minecraft

### 2.1 Versiones y calendario (contexto de 26.x)

- **Numeración.** Desde 2026 Java Edition numera las versiones como AA.D.H: año, drop dentro del año y hotfix. Se anunció en diciembre de 2025 [60].
- **Drops recientes:**

  | Versión | Nombre | Fecha | Contenido relevante |
  |---|---|---|---|
  | 1.21.4 | The Garden Awakens | 2024-12-03 | Creaking [68] |
  | 1.21.6 | Chase the Skies | 2025-06-17 | Happy Ghast |
  | 1.21.9 | The Copper Age | 2025-09-30 | Copper Golem |
  | 1.21.11 | Mounts of Mayhem | 2025-12-09 | Lanza, *lunge*, nautilus, nautilus zombi, *camel husk*, *parched* [63] |
  | 26.1 | Tiny Takeover | 2026-03-24 | Primera que exige Java 25; primera publicada sin ofuscar [60] |
  | 26.2 | Chaos Cubed | 2026-06-16 | *Sulfur cube* [61] |
  | 26.3 | Wilderness Bound | 2026-09-15 | Sin mob nuevo encontrado [62] |

- **Paper.**
  - 26.2 build 129 es la última build **estable** de 26.2 (2026-09-23).
  - Paper 26.3 se anunció el 2026-09-26 y está en beta. Añade `EntityCollideWithEntityEvent` y `Entity#getRemovalReason`, y quita `@Experimental` de mucha API [83c].
  - **Consecuencia:** la matriz de versiones de ThePredict (Paper 26.2) ya va un drop por detrás de vanilla. Conviene planificar una verificación en 26.3 cuando sea estable.

### 2.2 Metas (`Goal`/`GoalSelector`) frente a `Brain`

**Metas** [64][65][66]:
- En cada tick, `Mob.serverAiStep()` ejecuta los sensores.
- En los ticks impares (según `(tickCount + id) % 2`) solo avanzan las metas en curso. En los pares se reevalúan `canUse`/`canContinueToUse` y hay prevención.
- La selección ocurre, por tanto, **cada 2 ticks escalonada por ID** (desde 1.18).
- Las banderas MOVE/LOOK/JUMP/TARGET son exclusivas; Paper añade `UNKNOWN_BEHAVIOR`.
- `adjustedTickDelay` divide los retardos por 2 salvo que `requiresUpdateEveryTick()` sea verdadero.
- Coincide con la evidencia de javap del plan (§2).

**`Brain`** [64]:
- Memorias (con caducidad opcional), sensores (cada 20 ticks por defecto), actividades (core, idle, fight...) y comportamientos (normales, OneShot, Gate).
- Mojang no ha explicado públicamente por qué los mobs nuevos lo usan. La lectura comunitaria (memoria persistente, percepción por sensores, actividades) es **interpretación** [65].

**Quién usa qué en 26.2** [64][66]:
- **Con `Brain`:** Allay, Armadillo, Axolotl, Camel (y Camel Husk), Frog, Tadpole, Goat, Copper Golem, Happy Ghast, Nautilus, Zombie Nautilus, Sniffer, **Breeze, Creaking, Hoglin, Piglin, Piglin Brute, Warden, Zoglin**, Villager.
- **Con metas:** zombis, la familia esqueleto (incluidos Bogged y Parched), Wither, guardianes y el *sulfur cube*.
- **El dragón** usa su propio gestor de fases (0–10) [71].

**Acceso desde Paper** [78][79]:
- Solo `LivingEntity#getMemory/setMemory` con unas 45 `MemoryKey` (por ejemplo `ANGRY_AT`, `UNIVERSAL_ANGER`, `ATTACK_TARGET_COOLDOWN`, `SPEAR_*`).
- No hay `WALK_TARGET`, ni API de comportamientos o actividades.
- Un mantenedor de Paper confirma que los comportamientos de `Brain` "override whatever you do with the Pathfinder" (issue #10442, cerrado 2025-01-09).
- **Consecuencia:** la exclusión de ThePredict para las especies con `Brain` (README, "Límites conocidos") es correcta y seguirá siéndolo mientras no exista una API de `Brain`.
- **Implicación de diseño:** los jefes "modernos" de vanilla no son bases útiles para el control de movimiento. Un jefe de ThePredict debe basarse en especies con metas (zombi, esqueleto, *vindicator*, *wither skeleton*...).

### 2.3 Jefes y mobs recientes como referencias de diseño

Son diseños nativos que se pueden imitar con skills de MythicMobs:

- **Warden** [69]: una **tabla de ira por sospechoso**. Es el precedente vanilla más claro de "amenaza con memoria".
  - Escala 0–150; hostil desde 80; decae 1 por segundo.
  - Vibración a 15 bloques: +35 (proyectiles +10, o +35 si llegan varios en 5 s), con 2 s de enfriamiento de detección.
  - Olfato: +35 al objetivo más cercano.
  - Golpe directo: +100.
  - Explosión sónica con carga de 1,7 s y recuperación de 1,3 s, que ignora armadura.
  - Su melé deshabilita escudos 5 s.
  - Oscuridad pulsante.
- **Breeze** [67]:
  - Carga de viento a 16 bloques con 32 ticks de cooldown.
  - Saltos de hasta 15 bloques **telegrafiados por un sonido de inhalación**.
  - Desvía proyectiles.
- **Creaking** [68]: se congela si un jugador lo mira dentro de 60° sin obstáculos, y es invulnerable mientras su corazón existe. Es una mecánica de "lectura de la mirada" que MythicMobs puede imitar con condiciones.
- **Wither** [70]: invulnerable al aparecer; inmune a flechas por debajo del 50 %; rompe bloques al recibir daño. Es un ejemplo de **cambio de fase que anula una estrategia del jugador**.
- **Dragón** [71]: fases explícitas; los cristales curan; sobre el pedestal recibe ≈ 75 % menos daño salvo en la cabeza.
- **Lanza (1.21.11)** [74]:
  - Los zombis usan `SpearUseGoal` en prioridad 2, antes de `ZombieAttackGoal`; los piglins usan comportamientos `Brain` (`SpearApproach/Attack/Retreat`).
  - El ciclo "cargar, alejarse, volver a cargar" es una **skill de carga nativa**.
  - Paper expone `VanillaGoal.SPEAR_USE` y `EntityLungeEvent` [83a].
  - **Atención:** un zombi con lanza tiene una meta vanilla que reclama MOVE en prioridad 2. Si ThePredict instala sus metas sin `clear`, pueden competir.
- **Cámaras de prueba / *trial spawner*** [72]: diseño de encuentro escalado por jugadores.
  - Total = 6 + 2 por jugador extra; simultáneos = 2 + 1 por jugador extra.
  - Un intento cada 40 ticks; enfriamiento de 30 min.
  - Variante *ominous*.
  - Es la referencia vanilla de "director" simple, útil para DDA (sección 6.6).

### 2.4 Límites del pathfinding

Todo según el código de 26.2 y los parches de Paper [66][80]:

- **Algoritmo y presupuesto.** A* sobre nodos de bloque con presupuesto `max(FOLLOW_RANGE, 16) × 16` nodos (35 → ≈ 560) dentro de un cubo de radio longitud + 8, o + 16 si el destino es una entidad.
- **Rutas parciales.** Si no llega, devuelve la ruta al nodo más cercano con `reached = false`. Es lo que Paper expone como `PathResult#canReachFinalPoint()` [77].
- **Recalcular una ruta:** diferido si hubo otro recálculo hace ≤ 20 ticks.
- **Atascos:** comprobación cada 100 ticks con factor 0,25.
- **Tiempo límite por nodo:** 3× lo esperado.
- **`MeleeAttackGoal`:** recalcula cada 4–10 ticks más penalizaciones por distancia.
- **Añadidos de Paper:**
  - `EntityPathfindEvent` para cada destino, cancelable.
  - Destinos fuera del borde del mundo se descartan.
  - `moveTo(Entity)` deja de intentarlo 40 ticks tras más de 10 fallos seguidos. **Un `false` es una señal, no un error.**
  - Las sobrecargas `findPath(..., reachRange)` (PR #13026, 2026-07-28) están en 26.2 build 129 [77].
- **Asincronía:**
  - Paper **cerró sin fusionar** un PR de A* asíncrono (#14029, 2026-07-08): "not interested in maintaining an async pathfinding patch" [80].
  - Solo bifurcaciones como Leaf ofrecen pathfinding asíncrono [94].
  - Pufferfish+ anuncia `enable-async-entity-pathfinding` (**NO VERIFICADO**: la página devolvió 502).
- **La opción `AsyncPathfinding` de MythicMobs:**
  - El plan (§2) la encontró con javap en `MobType` del JAR 5.13.1.
  - No aparece en la documentación pública de MythicMobs [96].
  - **Inferencia:** existe en código, pero no está documentada. Su compatibilidad con metas condicionales sigue pendiente de M01 (DT abiertas).

### 2.5 Mecánicas de combate que condicionan la táctica

- **Invulnerabilidad de daño:** 10 ticks. Dentro de la ventana solo aplica la diferencia si el golpe nuevo es mayor [75]. ThePredict ya lo respeta antes del cast (plan §5).
- **Melé vanilla:** `attackInterval = 20` ticks [66].
- **Escudo** [73]:
  - Bloquea tras 5 ticks.
  - El ángulo por defecto de 90° cubre el semicírculo frontal (componente `blocks_attacks` desde 1.21.5).
  - Hachas y Warden lo desactivan 5 s; las flechas perforantes lo atraviesan.
  - Es la base de `FLANK` (plan §5).
- **Maza:** golpe aplastante tras ≥ 1,5 bloques de caída [79b]. Que en Java los mobs escalen el daño de la maza con la caída está **NO VERIFICADO** (la wiki cita una versión de Bedrock).
- **Lanza:** alcance 2–4,5; daño de carga según velocidad relativa (≥ 4,6 bloques/s) [74].

### 2.6 APIs de Paper para IA

- **`MobGoals`** [76]:
  - `addGoal`, `removeGoal`, `removeAllGoals` (opcionalmente por tipo), `getRunningGoals`...
  - Los `VanillaGoal` generados incluyen `SPEAR_USE` y los del *sulfur cube*.
  - Sin prioridades expuestas.
- **`Pathfinder`** [77]:
  - `findPath` (síncrono), `moveTo`, `stopPathfinding`, `getCurrentPath`.
  - `PathResult` con `canReachFinalPoint` (PR #10636, 2024).
- **`CombatTracker`:** `LivingEntity#getCombatTracker()`, añadido en el PR #11853 (fusionado 2025-04-30) [81].
- **`setAware(false)`:** además de congelar la IA, desde el PR #13781 (2026-04-19) salta la IA de ticks inactivos [82].
- **Plataforma:**
  - Paper es bifurcación completa (hard fork) desde 1.21.4 [84].
  - Ejecución con nombres de Mojang desde 1.20.5.
  - `paper-plugin.yml` sigue "not recommended as it is still in development" [85b].

---

## 3. Ecosistema de plugins para IA de mobs

### 3.1 MythicMobs (Premium 5.x): lo nativo que ThePredict debe reutilizar

**Versiones.** 5.12.0 salió el 2026-05-02 y 5.13.0 el 2026-07-22 (soporta 1.21.4–26.2) [103][112]. La fecha de 5.13.1 está **NO VERIFICADA**; su entrada en el registro de cambios solo trae overrides de packs [103].

**Metas de IA** [96][97][98]:
- La familia *Conditional* es **Premium**: `meleeAttackConditional`, `moveTowardsTargetConditional`, `moveWithinDistanceOfTargetConditional`, `fleeConditional`, `bowAttackConditional`, `crossbowAttackConditional` y `nearestConditionalTarget` [99]. ThePredict depende de ella, como ya declara el README.
- `goToSpawn{maxrange;dropTarget}` es el límite de arena nativo.
- **Novedades útiles:**
  - 5.13.0: mecánica `clearpath` (`stoppath`/`clearnavigation`) y triggers `onEnterBounds`/`onExitBounds` con la condición `inBounds` [103].
  - 5.11: el targeter `@EscapeLocation` ("Enderman-like escape location") [103].
- **Advertencia documentada sobre `AIPathfindingMalus`:** las metas que eligen su propio destino (paseo, huidas) solo aceptan posiciones con malus exactamente 0. Un mob en medio de un estanque no se mueve si no se pone `WATER 0` [96]. Afecta a `RETREAT` en ThePredict, que usa `fleeConditional`.
- **`runAIGoalSelector`** cambia metas desde una skill: una por llamada, hay que hacer `clear` antes y se añaden al final [109]. Es una vía nativa, pero más tosca que `VolatileAIHandler.addPathfinderGoals`, que ThePredict ya usa.

**Tabla de amenaza** [100][101][103]:
- Se activa con `Modules.ThreatTable` o `Options.UseThreatTable`.
- Cambia de objetivo cuando otro alcanza el **110 %** de la amenaza actual.
- Mecánicas: `threat{mode=add|remove|multiply|divide|set|forcetop}`, `taunt` (5.7) y `clearThreat`.
- Targeters: `@ThreatTable`, `@RandomThreatTarget`.
- **`ThreatTable.DropUnreachableSeconds`** es de **5.12.0** (verificado en el registro de cambios) [103].
- Las opciones `UseDamageTaken`, `DecayUnreachable` y `MaxThreatDistance`, que el plan (§5) cita, **no aparecen en la documentación pública**. **Inferencia:** el plan las leyó en el JAR, así que existen en código pero no están documentadas. Conviene confirmarlas en servidor antes de exponerlas en guías.

**Cooldowns, cargas y disponibilidad** [102][103][104]:
- Campos de metaskill: `Cooldown`, `OnCooldownSkill`, `Conditions`, `TargetConditions`, `TriggerConditions`, `FailedConditionsSkill`, `CancelIfNoTargets`, `Skills`.
- **5.12.0** añadió:
  - **`Charges`** y **`CooldownMode`** en skills y metaskills;
  - la mecánica **`addSkillCharges`**;
  - la condición **`skillIsRecharging`**;
  - los placeholders `<...skill.X.charges>`/`maxcharges` (verificado [103]).
  - Del conjunto de valores de `CooldownMode` solo se vio `PARALLEL` (**NO VERIFICADO** el resto).
- También existen `setSkillCooldown`, `GlobalCooldown` y las condiciones `skillOnCooldown` y `offGCD`.
- **5.11** añadió **`MetaskillCondition`** y `determineCondition`: una metaskill del autor decide si una condición pasa [103].
- **Consecuencia:** la "disponibilidad en tiempo real" que ThePredict está corrigiendo tiene fuentes nativas. Además, una metaskill de elegibilidad escrita por el autor puede sustituir reglas propias.

**Posturas nativas** [106][107][104]:
- `setstance{stance=...}` fija la "stance" de un Mythic Mob; `stance{s=...;strict=...}` es la condición (estricta por defecto desde 5.9).
- En la API existe `ActiveMob.setStance(String)`. Un `getStance()` público está **NO VERIFICADO** (javadoc público de 5.4.0-SNAPSHOT).
- No hay sección de configuración de posturas: es un texto por instancia.
- **Esto resuelve el "nativo primero" de las posturas.** ThePredict no necesita un estado de postura propio visible para el autor: basta con escribir la postura en la stance nativa, y las skills del autor se condicionan con `stance`.

**Señales y audición** [108][105]:
- `ActiveMob.signalMob(entity, signal)` → `~onSignal:X`; la condición `lastSignal`.
- `Hearing.Enabled` + `~onHear` (5.7; en 5.13 se corrigió que ignorara los sonidos de proyectiles).
- Útiles para búsqueda en la última posición conocida (LKP, sección 6.5) y para telegrafía.

**API para addons** [110][111]:
- Registro de componentes por anotaciones o por eventos de carga (ThePredict usa eventos por la regla D8, plan §2).
- El índice de clases muestra `@MythicAIGoal` y una clase abstracta `Pathfinder` (`shouldStart/start/tick/shouldEnd/end`).
- **Pero:** el plan verificó con javap que `VolatileAIHandlerImpl` llena un mapa **privado** por anotaciones del paquete de MythicMobs, sin registro público. La documentación pública (5.4.0-SNAPSHOT) no contradice esa evidencia más reciente. Registrar metas propias en `AIGoalSelectors` sigue descartado sin reflexión.

**Folia:**
- Modrinth y Hangar declaran soporte de Folia desde 5.12.0 [112].
- El registro de cambios recoge "Improved Folia handling" en 5.12.1 y 5.13.0 [103].
- Es un soporte declarado pero en maduración (sección 8.4).

### 3.2 ModelEngine R4

- **Animación:** `state{mid;s;li;lo;loop;priority}` y `defaultstate` para idle/walk/strafe [113]. R4 usa máquinas de estados de animación por prioridad.
- **No hay condición "está reproduciendo la animación X"** [114]. La telegrafía debe llevar su propio reloj (estado → retardo → golpe) en la skill o en el addon.
- **Bug abierto** (2026-09-27): en Folia/CanvasMC 26.1.2 con ME R4.1.1 dev, `state{}` bloquea el hilo de región; `defaultstate{}` no [115]. Es un riesgo concreto para cualquier soporte de Folia.

### 3.3 Citizens y Sentinel

- **Citizens** [116]:
  - A* propio opcional (`use-new-finder`, 10 000 iteraciones máximas) o el de Minecraft; `update-path-rate` 20 ticks.
  - Quejas históricas: un objetivo inalcanzable dispara la CPU [118].
- **Sentinel** [117]:
  - Combate de NPC con `attackrate`, `reach`, `chaserange`, `avoidrange`, `runaway`, y `squad`, que comparte objetivos.
  - Cambia de modo de pathfinder si el NPC parece atascado.
  - Mismo problema de CPU con objetivos inalcanzables [118].
  - No se documenta integración nativa con MythicMobs.

### 3.4 Otros plugins y mods de IA de mobs

Todos son de guion manual; ninguno usa ML.

- **SentientMobs** v2.4.1 (2026-09-06): zombis que flanquean y se retiran heridos, esqueletos que se reposicionan; "behavior trees and state machines" [119].
- **Advanced Mob AI** (2025): *creepers* que rompen muros y tienen "pillar explosion AI" contra las torres [120].
- **ZombieBlockBreaker** y **Unboat** (anti-barcas) [120b].
- **EliteMobs**, **LevelledMobs** e **InfernalMobs** cambian poderes y estadísticas, no tácticas de movimiento.
- **Mods:**
  - Enhanced AI (actualizado 2026-09-22; 21,2 M descargas): excavación, escalada, "anti-cheese" de barcas [121].
  - Epic Fight: combos y ventanas de castigo tipo *souls*.

### 3.5 NPC con LLM (2024–2026): ¿combate o diálogo?

- **El patrón es uniforme en todo lo verificado:** el LLM emite órdenes de alto nivel y un motor determinista ejecuta el combate. Ninguno elige acciones por tick.
- **Player2NPC:** el LLM traduce el chat a comandos como `get oak_log 10`, que ejecutan AltoClef/Baritone. Usa un prompt de sistema estable para la caché KV [122].
- **AI Companion Fabric** (2026-08-06): "fights like a player"; el motor sigue funcionando "even if the LLM is offline"; topes de coste [123].
- **AI_NPC** (Paper 1.18.2–26.2, v2.0.0 2026-07-22): solo diálogo, misiones e intercambios; "All API calls run off the main thread" [124].
- **Touhou Little Maid 1.3.0:** una sola función LLM, cambiar el modo de combate de la sirvienta [125].

### 3.6 Quejas de la comunidad

(Reddit no fue accesible, así que no se cita ningún hilo de Reddit.)

- **Metas que "no hacen nada" o no atacan** (SpigotMC 2018; issue #976 de MythicMobs, ≈ 2022) [96b].
- **Mobs congelados por el malus de agua** [96].
- **Lag por objetivos inalcanzables** en Citizens/Sentinel [118].
- **Torres ("pillar cheese") y barcas:** hay plugins dedicados a combatirlas [120][120b].
- **Previsibilidad de jefes:** no se encontró un hilo citable, solo el consejo genérico de usar `randomskill`.
- **Ausencia no demostrada:** no se encontró ningún plugin que use un modelo remoto para elegir tácticas de jefes de MythicMobs. ThePredict parece ocupar un hueco vacío.

---

## 4. Investigación de IA en Minecraft

### 4.1 Mapa de trabajos (verificados en arXiv o en páginas oficiales)

| Trabajo | Fecha / sede | Qué es | Dato clave | ¿Transfiere a tácticas de mobs? |
|---|---|---|---|---|
| MineRL [126], BASALT [127] | 2019; NeurIPS 2021 | Datos humanos; tareas difusas juzgadas por personas | En 2019 nadie consiguió el diamante | **BASALT sí, como método:** juicio humano por pares para "¿es justo y legible este jefe?" |
| MineDojo + MineCLIP [128] | NeurIPS 2022 | Miles de tareas; recompensa por video-lenguaje | Combate: araña 80,5 %, zombi 47,3 %, *pigman* 1,6 %, *enderman* 0 % | No; muestra que la RL con píxeles se hunde contra rivales con comportamiento |
| VPT [129] | 2022 | Clonado de comportamiento desde YouTube con modelo de dinámica inversa | Herramientas de diamante a 20 Hz con teclado y ratón | Solo la receta: pocas etiquetas → etiquetar en masa → clonar |
| Crafter / Craftax [130][131] | 2021 / ICML 2024 | *Benchmarks* 2D; Craftax en JAX es 250× más rápido | 1 000 M de interacciones PPO en < 1 h | Sí, si algún día se entrena: **un simulador abstracto rápido gana al servidor real** |
| Craftium [132] | 2024 (ICML 2025) | Sobre Luanti, > 2 000 fps | Entorno de combate 1v1 con PPO en *self-play* | Sí, como referencia de simulador de duelo |
| STEVE-1, GROOT/GROOT-2, MrSteve, ROCKET-1/2, JARVIS-VLA, OmniJARVIS, Optimus-1/2/3, OpenHA [133]–[141] | 2023–2025 | Políticas de píxeles / VLA | JARVIS-VLA a 5,5–15 FPS; ROCKET: el lenguaje es mal canal para objetivos espaciales | Casi nada. Excepciones: **memoria episódica por lugar y tiempo** (MrSteve), **acción abstracta como paso intermedio** (OpenHA ≈ "táctica → metas nativas") y **pasar IDs y coordenadas, no prosa** (ROCKET) |
| MineStudio [142], MCU [143] | 2024; ICML 2025 | Herramientas; 3 452 tareas atómicas | Combate entre las categorías peores; caída fuerte ante pequeñas perturbaciones | **Sí, como método:** plantillas de escenario con perturbaciones |
| DEPS [144] | NeurIPS 2023 | Describir fallo → explicar → replanificar + **selector de horizonte aprendido** | +22 a +33 puntos por el selector | **Sí, mucho:** un "coste o tiempo esperado hasta el efecto" por táctica, aprendido de registros |
| Plan4MC [145] | 2023 | Grafo de dependencias de skills (consume / requiere / obtiene) | 40 tareas | **Sí:** precondiciones y efectos tipados (≈ M15) |
| Voyager [146] | TMLR 2024 | Biblioteca de skills como código indexada por descripción; autoverificación | 3,3× más ítems; aprende `combatZombieWithSword` | La biblioteca tipada sí; la generación de código en ejecución no |
| GITM [147], JARVIS-1 [148] | 2023 | Acciones estructuradas (`attack` persigue hasta matar); memoria | GITM: +47,5 puntos en ObtainDiamond | Primitivas de combate de guion bajo un planificador |
| Odyssey [149] | IJCAI 2025 | Voyager + LLaMA-3 ajustado; **benchmark de combate** | 8/8 contra un mob; 1/8 contra 3 zombis; **8–14 min y ≈ 10 iteraciones LLM por pelea** | Muestra la escala de tiempo del LLM frente a la de un combate |
| Plancraft [150] | 2024 | Planificación con casos **irresolubles** | Un planificador manual es la referencia | **Sí:** incluir en el banco escenarios donde ninguna opción es buena |
| Project Sid / PIANO [151] | 2024 | 10–1 000+ agentes; ≈ 10 módulos concurrentes a distintas velocidades; **controlador cognitivo** como cuello de botella | "slow mental processes... should not block agents from responding to immediate threats" | **Sí:** el análogo conceptual más cercano a ThePredict |
| MineLand [152], TeamCraft [153] | 2024 | Multiagente con sentidos limitados | Mala generalización a otros tamaños de equipo | Aviso para la coordinación de escuadras |
| Mindcraft / MineCollab [154] | 2025 | Bots Mineflayer; 47 comandos parametrizados | Éxito de ≈ 90 % con 2 agentes a < 30 % con 5; comunicar planes cuesta hasta un 15 % | **Coordinar por roles asignados desde arriba, no por negociación** |
| PillagerBench / TactiCrafter [155] | IEEE CoG 2025 | Equipos en tiempo real contra rivales de reglas | 0,46 frente a 0,42; **latencias LLM de 5,4–25,5 s**; sobreajuste en *self-play* | Aviso: sobreajuste a un rival |
| Parallelized Planning-Acting (PPA) [156] | 2025 (v2 2026-03) | Hilo de planificación y hilo de ejecución **interrumpible** | Jefes: Elder Guardian 100 % en 1,2 min; dragón 91,7 % con 10 agentes | **Sí:** la interrupción es la ventaja; ThePredict tiene contingencias pero no cancela skills (plan §5) |
| TickingCollabBench [157] | 2026-06 | Colaboración con plazos estrictos | Los LLM "frequently fail under dynamic environments" | Confirma que el LLM no sirve como capa reactiva |
| Oasis, MineWorld, Genie 3 [158] | 2024–2025 | Modelos del mundo por píxeles | 4–24 fps | **No:** el servidor ya es el simulador exacto |
| SIMA 2 [159], Game-TARS [160], Lumine [161], NitroGen [162] | 2025–2026 | VLA generalistas | Lumine: 5 Hz de percepción, 30 Hz de acción, combate en Genshin | No directamente |
| CombatVLA [163] | ICCV 2025 | VLA de 3B para Wukong/Sekiro | **1,85 s por inferencia** frente a 62–90 s de agentes previos | El dato de latencia más honesto: incluso un modelo de combate a medida tarda ≈ 2 s |
| Benchmarks de juegos [45][164] | 2025 | VideoGameBench, lmgame-Bench, Orak | VideoGameBench: 0,48 % en tiempo real frente a 1,6 % en pausa | La latencia domina la calidad |
| Adaptive-Minecraft-Mob-Ai [165] | GitHub | Mod: Double DQN en el servidor, 10 acciones de combate, caché de 10 ticks | "2,4 ms" | **NO VERIFICADO:** sin evaluación publicada |

**No se encontró investigación revisada por pares sobre mobs de Minecraft entrenados con RL** (el agente del lado del mob). El análogo más cercano es el estudio en Unity de [40].

### 4.2 Qué transfiere y qué no

**No transfiere:**
- percepción por píxeles, teclado y ratón, dinámica inversa;
- pesos de VPT/STEVE/GROOT/ROCKET, recompensas MineCLIP;
- modelos del mundo;
- currículos de árbol tecnológico;
- generación de código en ejecución (riesgo de seguridad: Mindcraft advierte no conectar a servidores públicos con el código activado [154]);
- y sobre todo **la métrica**. Un agente jugador maximiza el éxito; un jefe debe ser **vencible, legible y divertido**. La tasa de victoria contra un mob clásico no es el objetivo final (sección 7).

**Transfiere:**
1. separar reflejos de deliberación sin que la deliberación bloquee (PIANO, PPA, Optimus-3);
2. un solo "controlador" que decide la intención y todo lo demás la sigue (PIANO);
3. skills tipadas con precondiciones y efectos (Plan4MC, GITM, Mindcraft);
4. ordenar candidatos por el **valor esperado aprendido** (DEPS);
5. devolver los fallos de ejecución como códigos estructurados, no como prosa (DEPS, Voyager);
6. geometría como datos calculados, no como descripción verbal (ROCKET). ThePredict ya lo hace con bandas semánticas calculadas en código;
7. memoria episódica corta por pelea (MrSteve, TactiCrafter);
8. vigilar el colapso y el sobreajuste al rival [40][155];
9. coordinación por roles asignados centralmente [154];
10. método de evaluación: perturbaciones (MCU), población de rivales guionizados [40][155], valoraciones Elo o por pares (GROOT) y juicio humano (BASALT).

---

## 5. Toma de decisiones con LLM y modelos pequeños en juegos

### 5.1 Presupuestos de latencia

- **Tick de Minecraft.** A 20 TPS cada tick dura 50 ms [42]. Los 180 ms p50 de Jev son ≈ 3,6 ticks, sin contar la red del host (pendiente de medir en servidor, K3).
- **Tolerancia por perspectiva** (Claypool y Claypool) [43]:

  | Tipo de juego | Tolerancia |
  |---|---|
  | Avatar en primera persona | ≈ 100 ms |
  | Avatar en tercera persona | ≈ 500 ms |
  | "Omnipresente" (RTS) | ≈ 1 000 ms |

  **Inferencia:** la *táctica* de un mob está en el rango de 500–1 000 ms; la *reacción* (bloquear, esquivar) en el de 100 ms. Por eso la reacción debe ser local.
- **Tiempo de reacción humano:** media de 231 ms (213 ms corregidos) en 1 469 sujetos [44]. Una decisión táctica en ≈ 200 ms es "humanamente plausible".
- **Más rápido y peor puede ganar a más lento y mejor:**
  - "Win Fast or Lose Slow": hasta +80 % de victorias en Street Fighter al cambiar calidad por latencia [46].
  - LLM Colosseum: "fast decisions are key" [59].
  - Real-Time Reasoning Gym: un agente con modos reactivo y planificador supera a los de un solo modo cuando aprieta el tiempo [47].
- **Arquitecturas duales publicadas:**
  - HLA: mente lenta + rápida + ejecutor [48].
  - DPT-Agent: sistema 1 = FSM + código; sistema 2 = reflexión asíncrona [49].
  - Latent Bridge (2026-06): razonador de ≈ 1,5 s + bucle reactivo a 15 Hz. +57 % en MsPacman, pero **ningún beneficio en MetaDrive**; la ganancia se correlaciona (r = 0,93) con si razonar supera a reaccionar en esa tarea [50]. **Implicación:** Jev aporta donde elegir la táctica importa más que la ejecución fina. El duelo debe medir en qué escenarios pasa eso.

### 5.2 Salidas estructuradas frente a texto libre

- **Restringir el formato puede degradar el razonamiento** de los LLM generativos [51].
- **La decodificación restringida garantiza la estructura** (XGrammar, JSONSchemaBench), pero deja una brecha semántica en modelos pequeños (2026) [51b].
- **Un clasificador sobre un conjunto cerrado evita los dos problemas:** no hay nada que parsear ni razonamiento libre que penalizar. Es la ventaja estructural de Jev para este caso.

### 5.3 Calibración y confianza

- **Las redes modernas están mal calibradas;** el escalado por temperatura corrige mucho sin cambiar la precisión [52].
- **La confianza verbalizada de los LLM es sobreconfiada** [53].
- **Los modelos entrenados con RL tienden a la sobreconfianza.** RLCR (recompensa de Brier) [54] y la RL consciente de la calibración (2601.13284) la reducen.
- **KnowNo** [55] convierte la planificación en opción múltiple (con "ninguna de estas") y usa **predicción conformal** para decidir cuándo pedir ayuda, con garantía estadística. Es la receta con fundamento para el umbral de fallback de ThePredict.
- **Recalibración en el dominio.** Un estudio aplicado sobre Jev (2026-09-21), que no es de TypeSafe, midió F1 0,908 frente a etiquetas humanas en narrativas de accidentes. Recalibrar con etiquetas del dominio **redujo el error de calibración 3,3×** [17]. **Implicación:** conviene recalibrar las probabilidades de Jev con resultados propios (banco y duelos), no solo elegir un umbral.

### 5.4 Caché, lotes y contingencias

- **Caché de prefijo o KV** (vLLM, OpenAI, Anthropic): abarata el *prefill* si el prefijo es estable. PUBG Ally y Player2 lo usan [36][122].
- **TypeSafe no documenta caché de prompt, ni límite de tamaño, ni idempotencia** [13]. **Consecuencia:** cada token se factura, y cualquier caché debe ser **del lado de ThePredict**.
- **La caché semántica por *embeddings* tiene riesgo de colisiones y falsos positivos** [58]. En combate es más segura una **clave exacta sobre hechos discretizados**, que ThePredict ya produce en bandas.
- **Destilar las decisiones remotas frecuentes a una política local** abarata sin perder calidad (Affordable Generative Agents) [57].
- **Acciones especulativas:** un modelo rápido predice y ejecuta; se confirma si el lento coincide. Acierto del 55 % y hasta −20 % de latencia [56]. ThePredict ya tiene contingencias especulativas (plan §6) y alternativas por probabilidad.
- **Varios agentes en una solicitud:** no se encontró medición específica de juegos (**NO VERIFICADO**). TypeSafe sí mide el agrupar preguntas (5.5).

### 5.5 Cómo se posiciona TypeSafe Jev / System One (solo docs.typesafe.ai y typesafe.ai)

- **Qué es.** "System One models make fast, structured decisions for software". Devuelven "typed decisions and probabilities rather than generated text" [1].
- **Calibración:** "Calibration is measured across groups of predictions; it does not guarantee that an individual answer is correct" [1]. La web la atribuye a "Reinforcement Learning for Calibrated Decisions (RLCD)" [14].
- **Precio:** 0,042 USD por millón de tokens de entrada; la salida es gratis [2].
- **Límites de uso publicados hoy: "100K tokens per second / 40 requests per second"**, con la nota "Rate limits are adjusting dynamically" [2].
  - **Discrepancia:** el plan (§6, 2026-09-28) registró 1 200 solicitudes/min y 250 000 tokens/s. Los límites cambiaron.
  - Con 1 884 tokens (`full`) y ≈ 60 solicitudes/min por mob aislado, el límite de cuenta daría ≈ 40 mobs (**cálculo propio**). El cuello de botella real sigue siendo el tope propio por mundo de ThePredict (5 mobs, M16.10).
- **Contexto:** 64k tokens por solicitud; 32k para el estado más la pregunta más larga. Solo texto [2].
- **Latencia:**
  - En los casos de uso, bajo "Gaming": "Frontier intelligence at real-time speeds (150ms)... Fast and smart enough to be programmed to play games" [3].
  - La web muestra "0.114s" frente a "8.566s" de LLMs y "193.6x Faster, 444.6x Cheaper" [14]. Son cifras de marketing sin metodología en esa página.
  - La medida propia de M13 (p50 179 ms, p99 328 ms) es la referencia fiable.
- **Filosofía:**
  - "Code owns the workflow and AI handles narrow, structured decisions".
  - Descomponer en juicios atómicos.
  - "Make code take different actions for confident and unconfident answers".
  - "Combine independent answers with deterministic rules or weighted sums" [7].
- **Primitivas:**
  - `Score` admite 2–10 niveles que "Describe situations, not degrees" y devuelve probabilidades por nivel y una posición ponderada [5].
  - El patrón **Composite scoring** combina Scores atómicos con pesos en código [8]. **Esto es literalmente utility AI con Jev como generador de consideraciones.**
- **Lotes:**
  - Cookbook: 13 preguntas en una llamada son "12.2x cheaper, 10.0x faster" [10].
  - La página de primitivas dice "11.5x cheaper and 9.6x faster" [4]. Pequeña discrepancia interna de la documentación; el plan citaba la primera.
- **Autoconsistencia:** la etiqueta mayoritaria se repite el 90,8 %; con probabilidad máxima ≥ 0,60, el acuerdo sube al 99,2 % con acción automática en el 74,2 % [11]. Coincide con G13 de M16.
- **Debilidades publicadas de jev-1.13** [12]: lectura literal, números, fechas, indirección, estado irrelevante, contenido adversarial, contradicciones e invariantes estructurales (dos Nouls negados sumaron 1,19). M16 ya aplica estas guías.
- **Otras vías de acceso:**
  - OpenRouter publica Jev como `typesafe/jev-1.13` en un endpoint *alpha* de decisiones (2026-09-21) [16]. No cambia la recomendación de conexión directa del plan.
  - **Kev:** familia Apache-2.0 sobre Qwen; 18–47 ms en GPU; 0,817–0,848 frente a 0,857 de Jev en fuentes nuevas [15].
- **Ofertas parecidas.** No se encontró ninguna otra API comercial de "decisión calibrada para juegos". NVIDIA ACE, Inworld, Convai y modl.ai venden SLM en el dispositivo, runtimes conversacionales o bots [36][37][35]. Un "LLM Gateway `/v1/systemone`" apareció solo en fragmentos de búsqueda y devolvió 503 (**NO VERIFICADO**).

---

## 6. Diseño de combate en juegos publicados y su encaje en Minecraft

### 6.1 Anatomía del ataque, telegrafía y ventanas de castigo

**Anticipación → activo → recuperación.** La ventana de castigo vive en la recuperación. La anticipación debe cubrir la reacción humana (≈ 0,25 s) más el tiempo de accionar la respuesta más un margen [166].

**Cifras de jefes (Vigil Games)** [167]:
- anticipación de 1–1,5 s, 2 s como máximo;
- ventanas que permitan al menos un combo completo;
- **ataques distintos no deben compartir la misma animación de arranque ni el mismo sonido**.

**Telegrafía multicanal coherente** (animación, sonido, efectos) [168]. La crítica a *Sekiro*: un aviso genérico ("peligro") que no dice qué contraataque usar castiga en exceso [169]. **Regla: un aviso por respuesta, no por nivel de peligro.**

**En Minecraft:**
- 0,25 s = 5 ticks; 1–1,5 s = 20–30 ticks, más la latencia cliente-servidor.
- Canales disponibles: partículas, sonidos, marcas en el suelo, títulos, barra de jefe y estados de ModelEngine.
- La inmunidad de 10 ticks comprime los combos: una ventana de < 10–20 ticks deja al jugador un solo golpe efectivo [75].

**Encaje con M15.** `SkillPurpose.windUpTicks` ya existe (M15.1) y la frase "after a long, visible wind-up" llega a Jev (M15.7). Faltan tres cosas:
- **recuperación** y **canal del aviso**, como metadatos;
- una comprobación estática de que dos skills no comparten aviso;
- un hecho del lado del jugador: "el objetivo está en su ventana de recuperación o ya reaccionó".

### 6.2 Fases, posturas y estados de agresión

- **DOOM 2016** [170][171]:
  - escalera de dolor por DPS: *twitch* → *falter* → *stagger* (abre el remate);
  - HFSM en 16 arquetipos;
  - durante el remate el jugador es invulnerable y la IA no inicia ataques.
- ***Sekiro*:** medidor de postura que se llena con daño y desvíos [169]. El ajuste interno oficial está **NO VERIFICADO**.
- ***Halo 2*** [173]: DAG de ≈ 50 comportamientos, con estímulos insertados por evento y **"styles" del diseñador** (listas de comportamientos permitidos y prohibidos).
  - **Es exactamente el patrón de los `Combinators` de ThePredict por contexto.** Una postura puede ser un "style".
- ***For Honor*** (GDC 2025): bots con RL + BT y *action masking* para mantener el control del diseñador; recortó ≈ 4 semanas de guion por héroe [41].
- **Monster Hunter** ("Think Tables") y los "estados de agresión" de Souls: solo hay fuentes comunitarias, **NO VERIFICADO**.

**En Minecraft:**
- La postura se implementa con **`setstance` nativo** (3.1).
- Las fases se autoran como "styles": qué tácticas permite cada postura. ThePredict ya tiene contextos y combinadores (plan §4).
- Un **medidor de postura** en la barra de jefe da a los jugadores de melé un objetivo que no choca con la inmunidad de 10 ticks: bloqueos y golpes sobre escudo pueden llenarlo.

### 6.3 Coordinación de grupos: fichas, círculos y roles

**DOOM 2016** [170]: reservas de fichas por tipo de ataque. Su tamaño varía con la dificultad y los demonios cercanos pueden **robar** fichas (fuente secundaria).

***God of War* (2018)** [175]:
- **Puntuación de agresión** periódica, por este orden:
  1. puede ser agresivo (no está en reacción a golpe);
  2. prioridad del diseñador con tope de distancia;
  3. el jugador lo apunta;
  4. rango por pantalla, ángulo y distancia.
- Reparto de fichas por orden de puntuación desde una reserva fija (14 en el ejemplo); los grandes consumen más.
- Los agresivos se colocan en media luna visible; el resto en un anillo exterior.

***Kingdoms of Amalur* ("Belgian AI")** [176]:
- Una rejilla por objetivo con **capacidad** (p. ej. 12) y **capacidad de ataque** (p. ej. 10).
- Cada criatura pesa (soldado 4, trol 8) y cada ataque pesa (carga de trol 6).
- Círculos interior y exterior; las capacidades escalan con la dificultad.

**Fichas de precisión** [177]: todos disparan, pero solo el portador de la ficha acierta. El intervalo se multiplica por reglas (distancia, agachado, cobertura, de espaldas).

***The Last of Us*** [172]: coordinador con roles Flanker, Approacher, Investigator, StayUpAndAimer y OpportunisticShooter. Arregló el fallo de "el jugador corre sin que nadie le dispare".

**F.E.A.R.** [20]: no tenía comportamientos de escuadra complejos. El flanqueo **emergía** de ir a coberturas válidas, y el diálogo explicaba la intención al jugador.

**En Minecraft:**
- Una rejilla **por jugador** (no global) escala a raids de 10+ jugadores.
- Para el jefe se invierte la lógica: fichas que limitan **a cuántos jugadores** puede enfocar a la vez su área o especial.
- Las fichas deben regular **intentos**, no solo aciertos; si no, el jugador ve golpes "desperdiciados".
- **Encaje con ThePredict:**
  - El plan (§6) ya dice que la coordinación entre mobs se resuelve **en código al aceptar respuestas**.
  - MineCollab [154] muestra que la negociación entre agentes escala mal.
  - Recomendación: fichas por objetivo con pesos derivados de `SkillPurpose` (entrega, daño) y roles asignados en código. Jev no debe negociar entre mobs.

### 6.4 Posición, flanqueo, cobertura y *kiting*

- **Killzone** [178]: evaluación de posiciones como suma ponderada (proximidad 20, línea de fuego 40 con cobertura parcial, cobertura frente a amenazas secundarias 20, rango preferido). Los mismos términos sirven como costes de A*.
- **Consultas tácticas** (Matthew Jack) [179]: generador + pruebas ponderadas y filtros. Es el patrón de EQS.
- **DOOM, "cobertura invertida":** posiciones abiertas cerca de cobertura que maximizan la visibilidad [170].
- ***The Last of Us***: sustituyó un mapa de exposición que cambiaba cada frame por un coste fijo a lo largo del "vector de combate" [172].
- **En Minecraft:**
  - La rejilla de bloques hace trivial generar candidatos (anillo de bloques pisables al alcance).
  - **Toda puntuación debe incluir el alcance vertical** contra torres, con una transición "no llego → a distancia o negación de área".
  - El flanqueo apunta fuera del semicírculo frontal del escudo, como `FLANK`.
- **Nativo primero:**
  - ThePredict ya delega la huida a `AvoidEntityGoal`/`DefaultRandomPos` y usa `Pathfinder` para `FLANK`.
  - No conviene un EQS propio salvo para `FLANK` y, si hiciera falta, "posición de tiro con línea de visión".
  - `@EscapeLocation` de MythicMobs (5.11) es otra fuente nativa de destinos [103].

### 6.5 Percepción, última posición conocida (LKP) y búsqueda

***The Last of Us*** [172]:
- Temporizador de percepción de 1–2 s.
- Al ver al jugador crea una **entidad LKP con marca de tiempo** que se difunde a los aliados.
- Búsqueda por rejilla que se difunde desde la LKP; las celdas se limpian al verlas.
- **Trampa deliberada:** si el jugador se aleja más de 5 m de la LKP, se envía a uno al instante. El ciclo combate → búsqueda bajó de ≈ 2 min a ≈ 30 s.

***Alien: Isolation*** [180]:
- Un director con "menace gauge" sabe dónde está el jugador pero solo da pistas periódicas.
- Capacidades del BT desbloqueadas progresivamente, **nunca** a raíz del evento que mató al jugador.

**En Minecraft:**
- ThePredict ya aplica percepción natural (M07): solo lo que el mob percibe.
- Una LKP es barata:
  - al perder la línea de visión, guardar posición y tick;
  - ir con `Pathfinder.moveTo` o la meta `goTo`;
  - escuchar con `Hearing`/`~onHear` [105];
  - soltar tras `DropUnreachableSeconds` [103].
- Aporta más a mobs de mundo abierto que a arenas de jefe.
- La norma de los juegos publicados es **honestidad con pistas**, no omnisciencia.

### 6.6 Dificultad adaptativa (DDA)

- **Director de *Left 4 Dead*** [181]:
  - Intensidad por superviviente (daño, incapacitación, muertes cercanas) que decae con el tiempo.
  - Máquina de estados Build Up → Sustain Peak (3–5 s) → Fade → Relax (30–45 s).
  - **Modula población y ritmo, no estadísticas.**
- **Hamlet** (Hunicke 2005) [182]: advierte que el jugador se siente "engañado" si nota el ajuste.
- **Crash Bandicoot** [183]: ayuda al débil sin cambiar el juego del fuerte.
- **RE4 "Game Rank":** Capcom nunca lo confirmó (**NO VERIFICADO** en sus valores).
- **Academia:**
  - Una revisión de 2024 encuentra efectos significativos de la DDA en disfrute, flow e inmersión [184].
  - Guo et al. (2024) piden medir la dificultad **objetiva y la percibida** [185].
- **En Minecraft:**
  - Un director de raid que modula **fichas, cadencia y oleadas**, nunca la vida o el daño del jefe.
  - Con muchos jugadores, usar el máximo o un percentil de intensidad.
  - El *trial spawner* vanilla (sección 2.3) ya escala con el número de jugadores.

### 6.7 Encaje en Minecraft: patrones ordenados

| # | Patrón | Encaje | Motivo |
|---|---|---|---|
| 1 | Anatomía del ataque + telegrafía multicanal | Muy alto | Ticks, partículas, sonidos, ModelEngine; `windUpTicks` ya existe |
| 2 | Capacidad por objetivo / pesos de ataque (Amalur, DOOM) | Muy alto | Escala a raids; capacidad = mando de dificultad |
| 3 | Puntuación de agresión + fichas (GoW) | Alto | Usar el vector de mirada del jugador en vez de la cámara |
| 4 | Postura / escalera de aturdimiento | Alto | Esquiva la limitación de la inmunidad de 10 ticks; da sentido a escudos |
| 5 | Fases como "styles" | Alto | Ya existe en `Combinators`; nativo con `stance` |
| 6 | Coordinador de roles (TLOU, F.E.A.R.) | Alto | Barato y legible; flanqueo emergente |
| 7 | Posición táctica ponderada | Medio | Solo donde lo nativo no llega (FLANK, tiro) |
| 8 | Director de intensidad | Medio | Cadencia y oleadas, no estadísticas |
| 9 | LKP + búsqueda | Medio-bajo | Más útil fuera de arenas |
| 10 | Fichas de precisión | Bajo para jefes | Un fallo deliberado del jefe se lee mal |

---

## 7. Metodología de evaluación

### 7.1 Estadística de tasas de victoria

- **Intervalos.** Wilson o Jeffreys son preferibles a Wald para `n` pequeño [187]. ThePredict ya usa Wilson.
- **Anchura** (cálculo propio):

  | Resultado | Wilson 95 % |
  |---|---|
  | 10/20 | [0,30; 0,70] |
  | 15/20 | [0,53; 0,89] |
  | 100/200 | [0,43; 0,57] |

- **Potencia** (cálculo propio, α = 0,05 bilateral, 80 %, frente a 50 %):

  | Tasa real | Elo ≈ | `n` necesario |
  |---|---|---|
  | 55 % | 35 | 783 (1 047 al 90 %) |
  | 60 % | 70 | 194 |
  | 65 % | — | 85 |
  | 70 % | — | 47 |

  - Efecto mínimo detectable: ≈ 80 % con `n` = 20, ≈ 65 % con 80, ≈ 60 % con 200.
  - Con dos brazos independientes, detectar una diferencia de 5 puntos necesita ≈ 1 565 rondas por brazo.
- **Contraste secuencial.**
  - Fishtest de Stockfish usa **GSPRT** con α = β = 0,05, límites en Elo normalizado y un **modelo pentanomial sobre pares de partidas con colores invertidos**, que también estima el sesgo de la apertura [188].
  - Muestra esperada (Wald, cálculo propio):

    | Hipótesis | Media | Peor caso | Contraste fijo unilateral |
    |---|---|---|---|
    | 50 % frente a 55 % | ≈ 530 | ≈ 860 | 1 077 |
    | 50 % frente a 60 % | ≈ 130 | — | — |

- **Empates como medio punto.** Válido como puntuación, pero la varianza no es binomial. Hay que publicar V/E/D por separado y usar varianza trinomial o pentanomial.
- **Valoraciones entre muchas variantes:**
  - El Elo secuencial depende del orden [189]. Mejor Bradley–Terry por máxima verosimilitud con *bootstrap*.
  - Glicko-2 funciona mejor con ≥ 10–15 partidas por periodo [190].
  - TrueSkill modela empates y equipos [191]; TrueSkill 2 predijo resultados de Halo 5 con un 68 % frente al 52 % [192]. Encaja con raids.
- **Comparaciones múltiples:** Holm entre brazos y métricas principales; Benjamini–Hochberg entre escenarios.

### 7.2 Experiencia del jugador

- **Cuestionarios:**
  - PXI (30 ítems, 10 constructos) y miniPXI [193][194];
  - PENS (autodeterminación) [195];
  - GEQ, con problemas psicométricos documentados [196]: mejor PXI o miniPXI.
- **Credibilidad:** en la 2K BotPrize, dos bots superaron el 50 % de "humanidad" en 2012 [197].
- **Telemetría a añadir** (recomendación):
  - tiempo hasta matar (jefe y jugador);
  - picos de daño recibido por jugador en ventanas de 1 s;
  - muertes por encuentro y *wipes*;
  - reintentos y abandonos;
  - **"golpes injustos"** (daño recibido con aviso de < 5 ticks);
  - uso de las ventanas de castigo;
  - incidentes de torre;
  - dificultad percibida (un ítem de 7 puntos), medida aparte de la objetiva [185].

### 7.3 Simulador frente a servidor, y evaluación fuera de línea

- **Reparto de papeles.** El simulador sirve para regresión y `n` grande; el servidor, para la experiencia. *For Honor* entrenó y probó sus bots contra bots guionizados antes de publicarlos [41].
- **Evaluación contrafactual.** El método de *replay* estima sin sesgo solo si las acciones registradas vienen de una política **aleatorizada y conocida** [198]. ThePredict ya registra distribuciones y candidatos (M18.4), pero ejecuta la opción de mayor probabilidad. Para evaluar políticas nuevas fuera de línea hace falta **exploración ε** en el simulador.

### 7.4 ¿Es sólida la metodología del duelo (M18)?

**Lo sólido:**
- brazos A/B espejados con lados invertidos;
- brazo D como control de sesgo;
- las mismas semillas en todos los brazos del simulador (números aleatorios comunes);
- Wilson;
- registrador completo;
- invariantes que sí se afirman (sin casts duplicados, nivel 4 idéntico al clásico);
- banco etiquetado con respuestas crudas;
- y la prudencia de no afirmar superioridad.

**Problemas y mejoras:**

1. **Potencia en servidor.**
   - Problema: 20 rondas por brazo solo detectan ≈ 80 % frente a 50 %, y el propio M18.3 lo reconoce. Con clave, 10 rondas por brazo no detectan nada salvo diferencias enormes.
   - Mejora: en servidor, el objetivo debe ser **validar el simulador** (distribuciones de tiempo, casts por minuto y daño comparables), no medir el efecto. El efecto se mide en el simulador con `n` ≥ 800 por comparación o con SPRT.
2. **El oráculo es circular.**
   - Problema: `ORACLE` elige "la mejor táctica" con la misma función que el plan local. Que pierda más que `NONE` (0,055 frente a 0,255) dice que **esa función está mal**, no que Jev fallaría.
   - Mejora: un **oráculo por resultado**. En el simulador, en cada época, clonar el estado, simular cada táctica elegible durante k ticks y elegir la de mejor resultado. Eso da una cota superior real y etiquetas objetivas para el banco.
3. **Emparejar.**
   - Problema: intercambiar lados cada 2 rondas solo reduce varianza si cada par comparte semilla y aparición.
   - Mejora: pares al estilo Fishtest (misma semilla, lados invertidos), analizados con varianza pentanomial.
4. **`Damage: 0` en el golpe vanilla.** Iguala los brazos, pero elimina la interacción real entre golpes vanilla, inmunidad y skills (DT-42). Hace falta una ablación con el golpe vanilla activo.
5. **Construcción.** Un mob clásico no usa escudo a 90°, no hace torres, no hace *strafe* con arco ni usa hachas. Hacen falta **jugadores sintéticos** guionizados: escudo y flanco, arquero que hace *kiting*, torre, espadachín con salto crítico. Cada arquetipo es un rival del duelo [40][155].
6. **Cadencia y latencia.** Hay que barrer el latido (10/20/40 ticks), la latencia inyectada (0/100/200/400 ms) y "una acción por época" frente a "acción cuando esté lista" [46].
7. **Banco de 60 escenarios.**
   - Elegir el umbral sobre los mismos 60 sobreajusta. 54/60 da Wilson [0,80; 0,95].
   - Mejoras: validación cruzada o predicción conformal [55]; publicar "prohibidas" como fallo de seguridad aparte; crecer a ≥ 200 escenarios con cuerpos de duelos reales (ya previsto en M18.8).
8. **Del 2D al servidor.** El simulador no tiene eje Y (torres, caídas, empuje vertical), colisiones de bloque, fallos de ruta ni caídas de TPS. Por eso el punto 1 importa.
9. **Escalera de valoración.** Todas las variantes en *round-robin* con Bradley–Terry: Jev, local, oráculo por resultado, aleatorio legal, clásico, cadencias, golpe vanilla activado o no.
10. **Colapso táctico.** Publicar la entropía de la distribución de tácticas por encuentro y por rival [40].
11. **Personas.**
    - Prueba intrasujeto contrabalanceada: ≈ 34 jugadores para un efecto medio (d = 0,5) en miniPXI o dificultad percibida.
    - ≈ 85 preferencias por pares para detectar 65:35 (cálculo propio).

---

## 8. Rendimiento en servidores de Minecraft

### 8.1 Presupuesto de tick

- **Presupuesto:** 50 ms por tick; MSPT es la media [90]. Paper incluye spark desde 1.21 [85].
- **Coste medido del registrador de ThePredict:** ≈ 1,4–1,6 µs por tick simulado sin grabación (M18). Es ruido.
- **`Pathfinder.findPath` es síncrono** [77]. `FLANK` ya lo limita (como mucho cada 10 ticks, plan §5).

### 8.2 Activación de entidades y ritmos de IA

- **Rango de activación (EAR 2.0)** [91]:
  - Monstruos a > 32 bloques del jugador pasan a inactivos; sus selectores solo corren cada 3.er tick inactivo.
  - Tener un objetivo da 20 ticks de inmunidad; estar herido, 1.
  - **Un jefe con objetivo sigue activo.** Los mobs de un encuentro lejos del jugador pueden ir "a saltos".
- **Configuración de Paper** [92]:
  - `tick-rates.behavior/sensor` (solo mobs con `Brain`);
  - `max-entity-collisions`;
  - `update-pathfinding-on-block-update`.
- **Guías comunitarias** [95]: bajan los límites de aparición, el rango de activación a 24 y las colisiones a 2.
- **DAB de Pufferfish:** limita metas y cerebros por distancia; no es asíncrono [93]. Paper tiene abierto un PR para limitar metas inactivas (#12781).

### 8.3 Fronteras asíncronas

- **Qué no puede salir del hilo:** "Large portions of the Bukkit API are not safe" fuera del hilo propietario [85]. Mundo y entidades se tocan solo en él.
- **Qué sí:** cálculo puro y HTTP. ThePredict ya cumple esto (plan §7).
- **Paper no tiene pathfinding asíncrono** [80]. Cada ruta que añada ThePredict cuenta contra los 50 ms.

### 8.4 Folia

- **Modelo.** Regiones independientes a 20 TPS en un grupo de hilos, sin hilo principal.
  - Los plugins declaran `folia-supported: true`.
  - "Code that is running in one region under no circumstance can be accessing or modifying data that is in another region".
  - Schedulers Global, Region, Async y Entity; el de entidad "will follow the entity wherever it goes" [86][87].
- **Estado a 2026-09-30:**
  - La estable publicada es 26.1.2 build 8 (2026-05-06). La rama 26.2 está en **BETA** y hay un issue abierto para 26.3 [88].
  - Canvas es una bifurcación verificada de Folia, ya estable en 26.2 [89].
  - MythicMobs declara soporte desde 5.12.0 y sigue corrigiendo carreras en 5.12.1 y 5.13.0 [103][112].
  - ModelEngine tiene un bloqueo abierto con `state{}` en Folia/Canvas [115].
- **Implicaciones para ThePredict:**
  - El coordinador, los encuentros con estado compartido y la bandeja `ServerThreadInbox` suponen un hilo del servidor.
  - En Folia, un encuentro puede abarcar dos regiones. El coordinador no puede leer síncronamente mobs de otra región.
  - Cada aplicación de intención debería ir al `EntityScheduler` del mob, y el estado del encuentro debería ser seguro entre hilos o dividirse por región.
  - **Recomendación:**
    - No soportarlo todavía.
    - Mantener las reglas de hilos (T1–T8) de forma que el día de mañana baste cambiar el despachador: toda escritura al mundo por un puerto "ejecuta en el hilo dueño de esta entidad".
    - Diseñar los encuentros como locales (todos los participantes en el `FOLLOW_RANGE`), lo que en Folia casi siempre cae en una región.
    - Revisar cuando Folia 26.x sea estable y un usuario lo pida.

---

## 9. Síntesis para ThePredict

### 9.1 Dónde está frente al estado del arte

**Alineado con el estado del arte:**

| ThePredict | Precedente publicado |
|---|---|
| Capa reactiva local que nunca espera | PUBG Ally [36], PIANO [151], PPA [156], DPT-Agent [49] |
| Pregunta cerrada y tipada, con probabilidades | KnowNo [55]; evita la penalización del formato libre [51] |
| Umbral de confianza con fallback escalonado | "Route on uncertainty" [7]; umbral por riesgo [6] |
| Skills tipadas (M15) | Snippets de DA:I [26], Plan4MC [145], GITM [147] |
| Percepción natural, estado semántico, sin números | Jaggedness de Jev [12]; ROCKET: geometría calculada, no prosa [137] |
| Agrupar por encuentro y contingencias especulativas | Parallel questions [10], fan-out [9], Speculative Actions [56] |
| Amenaza enriquecida sobre la tabla nativa | Warden (ira por sospechoso) [69]; `threat`/`taunt` nativos [101] |
| Duelo con control de sesgo, Wilson y registrador | Mejor que la mayoría de *benchmarks* de agentes de Minecraft |

**Por detrás del estado del arte:**

1. **No aprende del resultado.** `OutcomeWindow` mide, pero nada usa esa medida para decidir. DEPS [144] y la recalibración en el dominio [17] muestran que ahí está la ganancia.
2. **Cadencia de acción.** Una acción por época frente a disparadores de 10/20 ticks en el clásico. En los shooters y juegos de acción publicados, la capa táctica **no limita el ritmo de ataque**: lo limitan fichas, cooldowns y animación.
3. **Sin coordinación de grupo** (fichas, roles).
4. **Sin modelo de telegrafía, justicia ni ventanas de castigo.**
5. **Evaluación sin potencia ni jugadores sintéticos.**
6. **Ninguna medición en servidor real todavía** (README).

### 9.2 Diagnóstico con los datos propios

Fuente: matriz local de M18, brazo A, modo `NONE`.

- **ThePredict pierde 51 a 149.** Lanza 1 202 `DuelBolt` frente a 190 del clásico, y 2 610 `DuelStrike` frente a 3 527.
- **Efecto medio por táctica:**

  | Táctica | Efecto medio |
  |---|---|
  | `leap_in` | 0,250 |
  | `approach` | 0,250 |
  | `press_and_strike` | 0,225 |
  | `keep_distance_and_bolt` | **0,007** (1 175 ventanas) |
  | `guard_up`, `retreat_and_heal` | 0 |

- **Inferencia 1:** contra un rival que siempre se acerca, mantener la distancia hace que el mob sea perseguido y dispare poco o sin efecto. En el brazo C (ThePredict contra ThePredict) la misma táctica rinde 0,208, porque el rival también se aleja. **El valor de una táctica depende del comportamiento del rival.** Por eso hace falta un valor aprendido por contexto y no una prioridad fija.
- **Inferencia 2:** el oráculo, con la misma función, pierde más (0,055). La función de "mejor táctica" del plan local y del oráculo está desalineada con el resultado. Mientras el banco de M16 se etiquete con el mismo criterio humano, puede heredar el sesgo. Las etiquetas "best" deberían contrastarse con el oráculo por resultado (7.4, punto 2).
- **Inferencia 3:** las skills defensivas (`guard_up`, `retreat_and_heal`) tienen efecto 0 porque `OutcomeWindow` mide daño hecho y recibido en 60 ticks. Su valor (daño **evitado**) no se mide. La métrica necesita un término contrafactual o un horizonte mayor para esas tácticas.

### 9.3 Oportunidades ordenadas por impacto/esfuerzo

Escala: impacto A (alto), M (medio), B (bajo); esfuerzo S (pequeño), M, L (grande).

| # | Oportunidad | Impacto | Esfuerzo | Evidencia | Nativo primero |
|---|---|---|---|---|---|
| 1 | **Ritmo y disponibilidad:** leer `Cooldown`, `Charges` y `skillIsRecharging` en tiempo real; acción cuando la skill esté lista, no una por época; golpe base en `APPROACH`/`HOLD` sin consumir la táctica (DT-42) | A | S–M | Matriz M18 (menos golpes, pierde 0,255); DA:I separa evaluación de ejecución [26]; fichas y cooldowns, no la capa táctica, marcan el ritmo [170][175] | `Charges`/`CooldownMode`/`skillIsRecharging` (5.12) [103]; `meleeAttackConditional` ya golpea |
| 2 | **Valor de táctica aprendido del resultado:** tabla por (táctica × banda de distancia × tendencia del rival × postura) con la media de `OutcomeWindow`; prior del plan local; hecho semántico para Jev ("my ranged attacks have not been landing"); medir la entropía | A | M | DEPS +22 a +33 puntos [144]; colapso del 83,8 % [40]; recalibración 3,3× [17]; inferencias 9.2 | Sin API nativa equivalente; es lógica que hace más directa la decisión (regla de reinvención) |
| 3 | **Evaluación v2:** pares, SPRT, oráculo por resultado, jugadores sintéticos, barrido de latencia y cadencia, Bradley–Terry, validación cruzada del umbral | A (habilita todo) | M | Sección 7; Fishtest [188]; KnowNo [55] | Usa `/thepredict duel` y el simulador existentes |
| 4 | **Leer metadatos de MythicMobs automáticamente:** `Cooldown`, `Charges`, `Conditions`, `TargetConditions`, `CancelIfNoTargets`, retardos visibles; proponer `Purpose` inferido (origen `SKILL`, ya reservado en M15.1) para que el autor lo confirme | A (autoría) | M | Deseo explícito del usuario; M15 reservó `PurposeOrigin.SKILL` | API de skills de MythicMobs; `MetaskillCondition` (5.11) para elegibilidad escrita por el autor [103] |
| 5 | **Posturas nativas:** ThePredict escribe la postura con `ActiveMob.setStance`; las skills del autor se condicionan con `stance`; las posturas actúan como "styles" (qué tácticas permite cada una); Jev elige postura con una pregunta `Score` ordenada (p. ej. "defensiva / tanteo / presión / furia") y táctica con `choice` | A | M | Styles de Halo 2 [173]; escalera de DOOM [170]; `Score` "describe situations" [5] | `setstance`/`stance` [106][107] |
| 6 | **Telegrafía y justicia:** metadatos de anticipación, recuperación y canal del aviso; comprobar avisos distintos; hecho "target is in its recovery window"; métrica de "golpes injustos" | A (experiencia) | M | [166][167][168][169]; inmunidad de 10 ticks [75] | Señales, `~onSignal`, estados de ModelEngine [113]; `windUpTicks` ya existe |
| 7 | **Skills de carga:** (a) cargas nativas (`Charges`) como recurso que Jev ve en bandas ("two dashes ready"); (b) ataques cargados con ventana de interrupción del jugador | M | S | 5.12 [103]; lanza vanilla como carga nativa [74] | Todo nativo |
| 8 | **Coordinación de grupo con fichas por objetivo:** capacidad por jugador, pesos por `DamageTier`/`Delivery`, roles (atacante, flanqueador, tirador) asignados en código al aceptar respuestas | A (encuentros multi-mob) | M | Amalur [176], GoW [175], DOOM [170], TLOU [172]; MineCollab [154] | Plan §6 ya lo prevé en código; `signalMob` para avisar a aliados |
| 9 | **Mejor planteamiento para Jev:** (a) umbral por predicción conformal y validación cruzada; (b) recalibración por temperatura con resultados; (c) postura como `Score`; (d) no añadir más Nouls (el brazo E de M16 costó +57 % sin mejora); (e) hechos de disponibilidad y de resultado reciente | M | S–M | [55][17][5]; M16.10 | Guías de TypeSafe [7][8][12] |
| 10 | **Caché de distribuciones:** si la clave exacta de hechos discretizados no cambió desde la última respuesta vigente, reutilizar la distribución (nunca repetir un cast) y alargar el latido | M (capacidad: hoy 5 mobs por mundo) | S | Capacidad M16.10; TypeSafe sin caché de prompt [13]; caché semántica arriesgada [58] | Intención vigente ya existe; esto la extiende |
| 11 | **LKP y búsqueda** | M-B | S–M | TLOU [172]; Alien [180] | `Hearing`/`~onHear` [105], `Pathfinder.moveTo`, `DropUnreachableSeconds` |
| 12 | **DDA tipo director** (cadencia, fichas, oleadas; nunca vida o daño) | M | M | L4D [181], Hamlet [182], percibida frente a objetiva [185] | Spawners de MythicMobs, `trial spawner` como referencia [72] |
| 13 | **Destilar Jev en una política local** (con registros suficientes) | M (coste/latencia) | L | Affordable Generative Agents [57]; Odyssey/MineCollab (modelos pequeños ajustados) [149][154] | Lógica propia; solo si 2 no basta |
| 14 | **Folia** | B hoy | L | Folia 26.2 beta [88]; bloqueo en ME [115] | MythicMobs ya lo declara [112] |

### 9.4 Riesgos y qué evitar

- **Colapso táctico** (una táctica domina). Mitigación: entropía por encuentro en el registrador, umbral de alarma y penalización de repetición en el plan local [40].
- **Sobreajuste al rival del duelo.** Optimizar contra `DuelBlueClassic` puede producir un jefe que castiga a jugadores reales o que un jugador real explota en 30 segundos. Mitigación: población de jugadores sintéticos [155].
- **Omnisciencia injusta.** No romper la percepción natural (M07) al añadir LKP, fichas o director [180].
- **Sustituir lo nativo por comodidad:** tabla de amenaza propia, pathfinding propio [80][118], posturas propias en vez de `stance`, cooldowns en espejo en vez de leer los nativos.
- **Mobs con `Brain` para movimiento.** Paper confirma que no se puede [79]. Documentar qué especies base usar para jefes.
- **Metas vanilla que compiten:** `SpearUseGoal` en prioridad 2 en zombis con lanza [74]. El binder debería avisar si la especie trae metas que reclaman MOVE por delante.
- **Coste por detalle.** `full` duplica los tokens; más Nouls no mejoraron (M16.10). Cada pregunta o contingencia nueva debe demostrar que cambia decisiones.
- **Límites y precios de TypeSafe que cambian.** Los límites ya cambiaron entre el 28 y el 30 de septiembre [2]. Leer los límites de la cuenta en cada arranque, o al menos documentarlos por fecha.
- **Afirmar superioridad sin potencia** (sección 7).
- **Folia prematuro**, ModelEngine `state{}` en Folia [115].
- **DDA visible** (sensación de trampa) [182].

---

## 10. Hoja de ruta sugerida (M19–M24)

Cada módulo sigue el flujo del usuario: un documento profundo por módulo y reglas normativas antes de programar. El orden prioriza **medir bien → corregir la causa medida → ampliar el alcance**.

| Módulo | Contenido | Por qué ahora | Criterio de terminado (propuesto) |
|---|---|---|---|
| **M19. Ritmo de combate y disponibilidad** | Disponibilidad en tiempo real con `Cooldown`/`Charges`/`skillIsRecharging`/`offGCD`; ejecutar la skill de la intención vigente cuando esté lista (no una por época), con límite por época; golpe base en alcance sin consumir la táctica (decisión DT-42); coste de las esperas; cargas visibles en bandas para Jev | Causa medida del 0,255 y de la preferencia por distancia; lo está arreglando ya el usuario | Simulador: brazo A sin clave ≥ 0,45 con Wilson; `DuelStrike` de ThePredict ≥ clásico; ningún cast duplicado |
| **M20. Evaluación v2** | Pares con semilla común y lados invertidos; SPRT (GSPRT pentanomial); oráculo por resultado (simular k ticks por táctica); jugadores sintéticos (escudo y flanco, arquero que hace *kiting*, torre, crítico con salto); barrido de latencia y latido; Bradley–Terry; entropía táctica; umbral con validación cruzada o conformal; exploración ε solo en el simulador | Sin esto, M21–M24 no pueden demostrar nada; es local y gratis | Informe de la escalera con IC por *bootstrap*; simulador validado con la sesión 18 del servidor (distribuciones de TTK y casts/min) |
| **M21. Valor de táctica aprendido** | Tabla de efecto por contexto desde `OutcomeWindow` (con término de daño evitado para tácticas defensivas); prior del plan local; hechos semánticos para Jev; recalibración por temperatura de las probabilidades con resultados; etiquetas "best" del banco contrastadas con el oráculo | Ataca la desalineación del oráculo y el ranged bias; DEPS | Contra jugadores sintéticos, mejora significativa con SPRT frente a M19; entropía por encima del umbral |
| **M22. Metadatos nativos de skills** | Leer de MythicMobs cooldown, cargas, condiciones, alcance deducible de targeters y retardos; proponer `Purpose` (origen `SKILL`) para que el autor lo confirme; `MetaskillCondition` como elegibilidad escrita por el autor | Dirección declarada por el usuario; reduce la autoría | Un perfil sin `Purpose` obtiene propuestas y avisos coherentes; los perfiles actuales no cambian de comportamiento |
| **M23. Posturas y telegrafía** | Postura con `setstance` nativo; postura como "style" (tácticas permitidas); pregunta `Score` de postura; metadatos de anticipación, recuperación y aviso; comprobación de avisos distintos; hecho "ventana de recuperación del objetivo"; métrica de golpes injustos | Petición del usuario; mayor salto de calidad percibida; nativo | Jefe de ejemplo con 3 posturas autoradas solo en YAML nativo; golpes injustos < 5 % frente a jugadores sintéticos |
| **M24. Coordinación de grupo** (y después LKP/director) | Fichas por objetivo con capacidad y pesos; roles asignados al aceptar respuestas; aviso a aliados con `signalMob`; después, LKP con `Hearing` y un director de intensidad que toque cadencia y fichas | Encuentros multi-mob y raids; coste marginal bajo porque Jev ya agrupa por encuentro | Con 3 mobs contra 1 jugador sintético, sin "dogpile" (≤ capacidad) y sin mobs ociosos > X s |

**Fuera de la hoja de ruta por ahora:** Folia (revisar cuando Folia 26.x sea estable y haya demanda), destilación local (revisar tras M21 si el coste o la latencia importan) y RL de mobs (sin evidencia de ganancia frente a su coste de control).

---

## 11. Afirmaciones no verificadas (resumen)

- Opciones de amenaza `UseDamageTaken`, `DecayUnreachable`, `MaxThreatDistance` y la opción `AsyncPathfinding` de MythicMobs: no documentadas públicamente (solo evidencia javap local del plan).
- Existencia de `ActiveMob.getStance()` público; conjunto completo de `CooldownMode`; `parsedStance`.
- Vigencia de `@MythicAIGoal`/`Pathfinder` como API pública en 5.13.x (javadoc público 5.4.0).
- Fecha de lanzamiento de MythicMobs 5.13.1.
- Semántica de `enable-async-entity-pathfinding` en Pufferfish+.
- Razón oficial de Mojang para usar `Brain` en mobs nuevos.
- Escalado de la maza en mobs de Java.
- Si el espejo de código 26.2 [66] coincide exactamente con la versión final.
- Detalles internos de fichas de DOOM (solo fuente secundaria); círculos de Batman/Assassin's Creed; "Game Rank" de RE4; estados de agresión de Souls; tablas de Monster Hunter; LKP de Splinter Cell.
- Latencias de PUBG Ally; estado de lanzamiento de Asterion (MIR5); uso de GOAP en Mordor/Tomb Raider/Deus Ex (resumen del panel).
- Afirmaciones de Adaptive-Minecraft-Mob-Ai (2,4 ms, etc.).
- Existencia de otra API comercial de "decisión calibrada" (LLM Gateway devolvió 503).
- Medición específica de juegos para agrupar varios agentes en una solicitud.
- Que no exista ningún plugin de tácticas de MythicMobs con modelo remoto (ausencia no demostrada).
- Cifras de marketing de typesafe.ai ("193.6x Faster", "0.114s"): sin metodología en la página.
- Reducción de muestra por emparejamiento (ρ): ilustrativa, no medida.

---

## 12. Referencias

Todas consultadas el **2026-09-30**. "Fecha" es la de publicación o actualización si se conoce; "s.f." significa sin fecha visible.

**TypeSafe / Jev**
1. TypeSafe, "System One". https://docs.typesafe.ai/concepts/system-one (s.f.)
2. TypeSafe, "Models". https://docs.typesafe.ai/models (s.f.)
3. TypeSafe, "Example use cases". https://docs.typesafe.ai/concepts/use-case-map (s.f.)
4. TypeSafe, "Primitives (Questions)". https://docs.typesafe.ai/primitives (s.f.)
5. TypeSafe, "Score". https://docs.typesafe.ai/primitives/score (s.f.)
6. TypeSafe, "Confidence". https://docs.typesafe.ai/confidence (s.f.)
7. TypeSafe, "How to build with TypeSafe". https://docs.typesafe.ai/concepts/how-to-build-with-system-one (s.f.)
8. TypeSafe, "Composite scoring". https://docs.typesafe.ai/patterns/composite-scoring (s.f.)
9. TypeSafe, "Speculative fan-out". https://docs.typesafe.ai/patterns/fan-out (s.f.)
10. TypeSafe, "Parallel questions". https://docs.typesafe.ai/cookbooks/parallel_questions (s.f.)
11. TypeSafe, "Self-consistency: choices". https://docs.typesafe.ai/cookbooks/consistency_choice_cookbook (s.f.)
12. TypeSafe, "Jev 1.13 jaggedness". https://docs.typesafe.ai/model-jaggedness/jev-1.13 (s.f.)
13. TypeSafe, "API reference". https://docs.typesafe.ai/api (s.f.)
14. TypeSafe, página principal. https://typesafe.ai (2026)
15. J. Palmer et al., "Kev" (GitHub). https://github.com/jaredpalmer/kev (s.f.)
16. OpenRouter, "What is Jev". https://openrouter.ai/blog/insights/what-is-jev/ (2026-09-21, act. 2026-09-24)
17. A. Rafe, S. Das, "Calibrated Decisions at Scale... with a System One Model (Jev)", arXiv 2609.24052. https://arxiv.org/abs/2609.24052 (2026-09-21)

**Técnicas de IA de juego e híbridos**

18. Epic Games, "Overview of State Tree". https://dev.epicgames.com/documentation/unreal-engine/overview-of-state-tree-in-unreal-engine (s.f.)
19. Unity, "About Unity Behavior" y changelog. https://docs.unity3d.com/Packages/com.unity.behavior@1.0/manual/index.html (changelog 2026-02-02)
20. J. Orkin, "Three States and a Plan: The AI of F.E.A.R.", GDC 2006. https://www.gamedevs.org/uploads/three-states-plan-ai-of-fear.pdf (2006)
21. GDC Vault, "Goal-Oriented Action Planning: Ten Years Old and No Fear!". https://www.gdcvault.com/play/1022019/Goal-Oriented-Action-Planning-Ten (2015)
22. T. Verweij, "HTN Planning in Decima", Guerrilla. https://www.guerrilla-games.com/read/htn-planning-in-decima (2024-11-25)
23. T. Humphreys, "Exploring HTN Planners through Example", Game AI Pro cap. 12. https://www.gameaipro.com/GameAIPro/GameAIPro_Chapter12_Exploring_HTN_Planners_through_Example.pdf (2013)
24. M. Lewis, "Choosing Effective Utility-Based Considerations", Game AI Pro 3 cap. 13. https://www.gameaipro.com/GameAIPro3/GameAIPro3_Chapter13_Choosing_Effective_Utility-Based_Considerations.pdf (2017)
25. D. Mark, M. Lewis, "Building a Better Centaur", GDC 2015. https://www.gdcvault.com/play/1021848/Building-a-Better-Centaur-AI (2015)
26. S. Hanlon, C. Watts, "Behavior Decision System: Dragon Age Inquisition's Utility Scoring Architecture", Game AI Pro 3 cap. 31. https://www.gameaipro.com/GameAIPro3/GameAIPro3_Chapter31_Behavior_Decision_System_Dragon_Age_Inquisition%E2%80%99s_Utility_Scoring_Architecture.pdf (2017)
27. Epic Games, "Environment Query System Overview". https://dev.epicgames.com/documentation/en-us/unreal-engine/environment-query-system-overview-in-unreal-engine (s.f.)
28. D. Mark, "Modular Tactical Influence Maps", Game AI Pro 2 cap. 30. https://www.gameaipro.com/GameAIPro2/GameAIPro2_Chapter30_Modular_Tactical_Influence_Maps.pdf (2015)
29. AiGameDev, "MCTS in Total War: Rome II". http://aigamedev.com/open/coverage/mcts-rome-ii/ (≈ 2014)
30. PokéChamp, arXiv 2503.04094 (ICML 2025). https://huggingface.co/papers/2503.04094 (2025-03)
31. P. Wurman et al., "Outracing champion Gran Turismo drivers with deep RL", Nature. https://www.nature.com/articles/s41586-021-04357-7 (2022-02); GT Sophy 2.1: https://www.gran-turismo.com/us/news/00_4957003.html (2025-03-27)
32. Ubisoft La Forge, Roller Champions RL, arXiv 2012.06031. https://arxiv.org/pdf/2012.06031 (2020-12)
33. EA SEED, arXiv 2307.11105. https://arxiv.org/pdf/2307.11105 (2023-07); https://www.ea.com/seed/news/seed-ml-research-aaa-game-testing (s.f.)
34. Microsoft Research, "Introducing Muse". https://www.microsoft.com/en-us/research/blog/introducing-muse-our-first-generative-ai-model-designed-for-gameplay-ideation/ (2025-02-19)
35. modl.ai, "Riot Games and modl shooter bots". https://modl.ai/riot-games-and-modl-shooter-bots (2025-05-07)
36. NVIDIA, "How KRAFTON built PUBG Ally...". https://developer.nvidia.com/blog/how-krafton-built-pubg-ally-a-co-playable-character-powered-by-nvidia-ace/ (2026-06-25)
37. NVIDIA, "ACE autonomous AI companions" (CES 2025). https://www.nvidia.com/en-us/geforce/news/nvidia-ace-autonomous-ai-companions-pubg-naraka-bladepoint/ (2025-01-06); Wemade/MIR5: https://www.businesswire.com/news/home/20250106300797/en/ (2025-01-06)
38. Ubisoft, "Ubisoft reveals Teammates". https://news.ubisoft.com/en-us/article/3mWlITIuWuu0MoVuR6o8ps/ubisoft-reveals-teammates-an-ai-experiment-to-change-the-game (2025-11-21)
39. T. Thompson (AI and Games), "Ubisoft's Teammates demo". https://www.aiandgames.com/p/ubisofts-teammates-demo-and-their (2025-11-26)
40. H. D. Nair, K. Karim, "LLM-Guided Reinforcement Learning for Adaptive NPC Behavior in Multi-Agent Combat Games", arXiv 2609.02931. https://arxiv.org/abs/2609.02931 (2026-08-27)
41. Azzouni, Marcotte, "Streamlining Bot Development in For Honor", GDC 2025. https://gdcvault.com/play/1035522/Streamlining-Bot-Development-in-For (2025)

**Latencia, formato, calibración, caché**

42. Minecraft Wiki, "Tick". https://minecraft.wiki/w/Tick (s.f.)
43. M. Claypool, K. Claypool, "Latency and player actions in online games", CACM. https://web.cs.wpi.edu/~claypool/papers/precision-deadline/final.pdf (2006)
44. D. Woods et al., "Factors influencing the latency of simple reaction time", Front. Hum. Neurosci. https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4374455/ (2015)
45. VideoGameBench, arXiv 2505.18134. https://arxiv.org/abs/2505.18134 (2025-05-23)
46. "Win Fast or Lose Slow", arXiv 2505.19481. https://arxiv.org/abs/2505.19481 (2025-05-26)
47. Real-Time Reasoning Gym, arXiv 2511.04898. https://arxiv.org/abs/2511.04898 (2025-11-07)
48. HLA (AAMAS 2024), arXiv 2312.15224. https://arxiv.org/abs/2312.15224 (2023-12)
49. DPT-Agent (ACL 2025), arXiv 2502.11882. https://arxiv.org/abs/2502.11882 (2025-02)
50. "The Latent Bridge", arXiv 2606.24470. https://arxiv.org/abs/2606.24470 (2026-06-23)
51. "Let Me Speak Freely?", EMNLP 2024 Industry. https://aclanthology.org/2024.emnlp-industry.91/ (2024-11)
51b. XGrammar, arXiv 2411.15100 (2024-11): https://arxiv.org/abs/2411.15100. JSONSchemaBench, arXiv 2501.10868 (2025-01): https://arxiv.org/abs/2501.10868. arXiv 2609.23742 (2026-09-20): https://arxiv.org/abs/2609.23742
52. C. Guo et al., "On Calibration of Modern Neural Networks", ICML 2017. https://proceedings.mlr.press/v70/guo17a/guo17a.pdf (2017)
53. M. Xiong et al., "Can LLMs Express Their Uncertainty?", ICLR 2024. https://arxiv.org/pdf/2306.13063 (2023-06)
54. M. Damani et al., RLCR, arXiv 2507.16806. https://arxiv.org/abs/2507.16806 (2025-07); calibración en RL de decisión, arXiv 2601.13284. https://arxiv.org/abs/2601.13284 (2026-01-19)
55. A. Ren et al., KnowNo, arXiv 2307.01928. https://arxiv.org/abs/2307.01928 (2023-07)
56. "Speculative Actions", arXiv 2510.04371. https://arxiv.org/abs/2510.04371 (2025-10-05)
57. "Affordable Generative Agents" (TMLR 2024), arXiv 2402.02053. https://arxiv.org/abs/2402.02053 (2024-02)
58. Riesgos de la caché semántica, arXiv 2504.02268. https://arxiv.org/abs/2504.02268 (2025-04); arXiv 2601.23088. https://arxiv.org/html/2601.23088v1 (2026-01)
59. OpenGenerativeAI, "LLM Colosseum" README. https://github.com/OpenGenerativeAI/llm-colosseum/blob/main/README.md (2024)

**Minecraft vanilla y Paper**

60. Minecraft Wiki, "Java Edition 26.1". https://minecraft.wiki/w/Java_Edition_26.1 (s.f.)
61. Minecraft Wiki, "Java Edition 26.2". https://minecraft.wiki/w/Java_Edition_26.2 (s.f.)
62. Minecraft Wiki, "Java Edition 26.3". https://minecraft.wiki/w/Java_Edition_26.3 (s.f.)
63. Minecraft Wiki, "Mounts of Mayhem". https://minecraft.wiki/w/Mounts_of_Mayhem (s.f.); 1.21.4: https://minecraft.wiki/w/Java_Edition_1.21.4
64. Minecraft Wiki, "Mob AI". https://minecraft.wiki/w/Mob_AI (s.f.)
65. IzzelAliz, "Living things" (análisis de IA de mobs). https://izzel.io/2021/12/19/living-things/ (2021-12-19)
66. Renekovski, espejo de código 26.2 (MCP). https://github.com/Renekovski/26.2-mcp (último commit 2026-04-15)
67. Minecraft Wiki, "Breeze". https://minecraft.wiki/w/Breeze (s.f.)
68. Minecraft Wiki, "Creaking". https://minecraft.wiki/w/Creaking (s.f.)
69. Minecraft Wiki, "Warden". https://minecraft.wiki/w/Warden (s.f.)
70. Minecraft Wiki, "Wither". https://minecraft.wiki/w/Wither (s.f.)
71. Minecraft Wiki, "Ender Dragon". https://minecraft.wiki/w/Ender_Dragon (s.f.)
72. Minecraft Wiki, "Trial Spawner". https://minecraft.wiki/w/Trial_Spawner (s.f.)
73. Minecraft Wiki, "Shield". https://minecraft.wiki/w/Shield (s.f.)
74. Minecraft Wiki, "Spear". https://minecraft.wiki/w/Spear (s.f.)
75. Minecraft Wiki, "Damage". https://minecraft.wiki/w/Damage (s.f.)
76. PaperMC Javadoc, `MobGoals`. https://jd.papermc.io/paper/com/destroystokyo/paper/entity/ai/MobGoals.html (26.3.build.140-beta)
77. PaperMC Javadoc, `Pathfinder`. https://jd.papermc.io/paper/com/destroystokyo/paper/entity/Pathfinder.html (s.f.)
78. PaperMC Javadoc, `MemoryKey`. https://jd.papermc.io/paper/org/bukkit/entity/memory/MemoryKey.html (s.f.)
79. PaperMC, issue #10442 (Brain frente a Pathfinder). https://github.com/PaperMC/Paper/issues/10442 (cerrado 2025-01-09)
79b. Minecraft Wiki, "Mace". https://minecraft.wiki/w/Mace (s.f.)
80. PaperMC, PR #14029 (A* asíncrono, cerrado sin fusionar). https://github.com/PaperMC/Paper/pull/14029 (2026-07-08)
81. PaperMC, PR #11853 (CombatTracker API). https://github.com/PaperMC/Paper/pull/11853 (2025-04-30)
82. PaperMC, PR #13781 (`setAware` e IA inactiva). https://github.com/PaperMC/Paper/pull/13781 (2026-04-19)
83a. PaperMC, "Paper 26.1". https://papermc.io/news/26-1/ (2026-04-26)
83b. PaperMC, "Paper 26.2". https://papermc.io/news/26-2/ (2026-06-12)
83c. PaperMC, "Paper 26.3". https://papermc.io/news/26-3/ (2026-09-26)
84. PaperMC, "The future of Paper – Hard fork". https://papermc.io/news/the-future-of-paper-hard-fork/ (2024-12)
85. PaperMC, "Scheduling" y "Profiling". https://docs.papermc.io/paper/dev/scheduler/ ; https://docs.papermc.io/paper/profiling/ (s.f.)
85b. PaperMC, "Project setup". https://docs.papermc.io/paper/dev/project-setup/ (s.f.)
86. PaperMC, "Supporting Paper and Folia". https://docs.papermc.io/paper/dev/folia-support/ (s.f.)
87. PaperMC, Folia README. https://github.com/PaperMC/Folia (s.f.)
88. PaperMC, descargas de Folia e issue #509. https://papermc.io/downloads/folia ; https://github.com/PaperMC/Folia/issues/509 (consultado 2026-09-30)
89. CraftCanvasMC, Canvas. https://github.com/CraftCanvasMC/Canvas (s.f.)
90. lucko, "spark: TPS and MSPT". https://spark.lucko.me/docs/guides/TPS-and-MSPT (s.f.)
91. PaperMC, parche Entity Activation Range 2.0 y referencia de spigot.yml. https://github.com/PaperMC/Paper/blob/main/paper-server/patches/features/0004-Entity-Activation-Range-2.0.patch ; https://docs.papermc.io/paper/reference/spigot-configuration/ (s.f.)
92. PaperMC, "World configuration". https://docs.papermc.io/paper/reference/world-configuration/ (s.f.)
93. Pufferfish (DAB). https://github.com/pufferfish-gg/Pufferfish (rama ver/1.21, 2025-12-21)
94. Winds-Studio, Leaf. https://github.com/Winds-Studio/Leaf (2026)
95. YouHaveTrouble, "minecraft-optimization". https://github.com/YouHaveTrouble/minecraft-optimization (s.f.)

**Plugins y mods**

96. MythicMobs Wiki, "Custom AI". https://wiki.mythiccraft.io/mythicmobs/Mobs/Custom-AI (act. 2026-09-23)
96b. SpigotMC, "Mythic Mobs AIGoals and target selectors do nothing". https://www.spigotmc.org/threads/mythic-mobs-aigoals-and-target-selectors-do-nothing.340241/ (2018-09-21); MythicMobs issue #976: https://git.mythiccraft.io/mythiccraft/MythicMobs/-/issues/976 (≈ 2022)
97. MythicMobs Wiki, `meleeAttackConditional`. https://wiki.mythiccraft.io/mythicmobs/Mobs/ai/goals/meleeattackconditional (s.f.)
98. MythicMobs Wiki, `fleeConditional`. https://wiki.mythiccraft.io/mythicmobs/Mobs/ai/goals/fleeconditional (s.f.)
99. MythicMobs Wiki, "Premium Features". https://wiki.mythiccraft.io/mythicmobs/Premium-Features (act. 2026-08-19)
100. MythicMobs Wiki, "ThreatTables". https://wiki.mythiccraft.io/mythicmobs/Mobs/ThreatTables (act. 2026-08-19)
101. MythicMobs Wiki, mecánica `threat`. https://wiki.mythiccraft.io/mythicmobs/Skills/Mechanics/threat (s.f.)
102. MythicMobs Wiki, "Metaskills". https://wiki.mythiccraft.io/mythicmobs/Skills/Metaskills (s.f.)
103. MythicMobs Wiki, "Changelogs" (5.11–5.13.1). https://wiki.mythiccraft.io/mythicmobs/changelogs/Changelogs (act. ≈ 2026-09-22; verificado directamente)
104. MythicMobs Wiki, "5.9.x changelogs". https://wiki.mythiccraft.io/mythicmobs/changelogs/5.9.x_changelogs (s.f.)
105. MythicMobs Wiki, "5.7.x changelogs". https://wiki.mythiccraft.io/mythicmobs/changelogs/5.7.x_changelogs (s.f.)
106. MythicMobs Wiki, mecánica `setstance`. https://wiki.mythiccraft.io/mythicmobs/skills/mechanics/setstance (s.f.)
107. MythicMobs Wiki, condición `stance`. https://wiki.mythiccraft.io/mythicmobs/skills/conditions/stance (s.f.)
108. MythicMobs Wiki, mecánica `signal`. https://wiki.mythiccraft.io/mythicmobs/skills/mechanics/signal (s.f.)
109. MythicMobs Wiki, `runAIGoalSelector`. https://wiki.mythiccraft.io/mythicmobs/skills/mechanics/runaigoalselector (s.f.)
110. MythicMobs Wiki, "API". https://wiki.mythiccraft.io/mythicmobs/API (s.f.)
111. MythicCraft Javadoc (5.4.0-SNAPSHOT), `ActiveMob` y `Pathfinder`. https://www.mythiccraft.io/javadocs/mythic/io/lumine/mythic/core/mobs/ActiveMob.html ; https://www.mythiccraft.io/javadocs/mythic/io/lumine/mythic/core/mobs/ai/Pathfinder.html (s.f.)
112. Hangar, MythicMobs ("Supports Folia"); CurseForge 5.13.0. https://hangar.papermc.io/Lumine/MythicMobs ; https://www.curseforge.com/minecraft/bukkit-plugins/mythicmobs/files/8490088 (2026-07-22)
113. ModelEngine Wiki, mecánica `State`. https://wiki.mythiccraft.io/modelengine/Skills/Mechanics/State (s.f.)
114. ModelEngine Wiki, "Conditions". https://wiki.mythiccraft.io/modelengine/Skills/Conditions (s.f.)
115. ModelEngine, issue #10064 (bloqueo de `state{}` en Folia/Canvas). https://issues.mythiccraft.io/modelengine/i/10064 (2026-09-27)
116. Citizens Wiki, "Configuration". https://wiki.citizensnpcs.co/Configuration (s.f.)
117. mcmonkey, Sentinel README y config.yml. https://github.com/mcmonkeyprojects/Sentinel/blob/master/README.md (s.f.)
118. Citizens2 issues #1561 (2018-09-01), #2355 (2020-10-07), #2730 (2022-01-15); Sentinel issue #179 (2017-07-15). https://github.com/CitizensDev/Citizens2/issues/1561 ; https://github.com/CitizensDev/Citizens2/issues/2730 ; https://github.com/mcmonkeyprojects/Sentinel/issues/179
119. SpigotMC, SentientMobs. https://www.spigotmc.org/resources/sentientmobs.135227/ (2026-09-06)
120. SpigotMC, Advanced Mob AI. https://www.spigotmc.org/resources/advanced-mob-ai.124478/ (2025-06-28)
120b. SpigotMC, ZombieBlockBreaker. https://www.spigotmc.org/resources/zbb-%C2%BBzombieblockbreaker%C2%AB-1-21.125544/ (s.f.); Modrinth, Unboat: https://modrinth.com/plugin/unboat (2025-10-11)
121. CurseForge, Enhanced AI. https://www.curseforge.com/minecraft/mc-mods/enhanced-ai (act. 2026-09-22)
122. Goodbird, Player2NPC. https://github.com/Goodbird-git/Player2NPC (s.f.); Player2, "Building AI NPCs with Player2 API": https://blog.player2.game/p/building-ai-npcs-with-player2-api (2025-05-13)
123. CurseForge, AI Companion Fabric. https://www.curseforge.com/minecraft/mc-mods/ai-companion-fabric (v0.2.9, 2026-08-06)
124. Hangar, AI_NPC. https://hangar.papermc.io/NNNNTX/AI_NPC (v2.0.0, 2026-07-22)
125. Modrinth, Touhou Little Maid 1.3.0. https://modrinth.com/mod/touhou-little-maid/version/1.3.0 (≈ 2025)

**Investigación de IA en Minecraft y juegos**

126. W. Guss et al., MineRL, arXiv 1907.13440. https://arxiv.org/abs/1907.13440 (2019-07)
127. R. Shah et al., MineRL BASALT, arXiv 2107.01969. https://arxiv.org/abs/2107.01969 (2021-07)
128. L. Fan et al., MineDojo, arXiv 2206.08853. https://arxiv.org/abs/2206.08853 (2022-06)
129. B. Baker et al., VPT, arXiv 2206.11795. https://arxiv.org/abs/2206.11795 (2022-06)
130. D. Hafner, Crafter, arXiv 2109.06780. https://arxiv.org/abs/2109.06780 (2021-09)
131. M. Matthews et al., Craftax, arXiv 2402.16801. https://arxiv.org/abs/2402.16801 (2024-02); Multi-Agent Craftax, arXiv 2511.04904 (2025-11)
132. Craftium, arXiv 2407.03969. https://arxiv.org/abs/2407.03969 (2024-07)
133. S. Lifshitz et al., STEVE-1, arXiv 2306.00937. https://arxiv.org/abs/2306.00937 (2023-06)
134. S. Cai et al., GROOT, arXiv 2310.08235. https://arxiv.org/abs/2310.08235 (2023-10)
135. GROOT-2, arXiv 2412.10410. https://arxiv.org/abs/2412.10410 (2024-12)
136. MrSteve, arXiv 2411.06736. https://arxiv.org/abs/2411.06736 (2024-11)
137. ROCKET-1, arXiv 2410.17856 (2024-10): https://arxiv.org/abs/2410.17856; ROCKET-2, arXiv 2503.02505 (2025-03): https://arxiv.org/abs/2503.02505
138. JARVIS-VLA, arXiv 2503.16365. https://arxiv.org/abs/2503.16365 (2025-03)
139. OmniJARVIS, arXiv 2407.00114. https://arxiv.org/abs/2407.00114 (2024-06)
140. Optimus-1, arXiv 2408.03615 (2024-08): https://arxiv.org/abs/2408.03615; Optimus-2, arXiv 2502.19902 (2025-02): https://arxiv.org/abs/2502.19902; Optimus-3, arXiv 2506.10357 (2025-06, rev. 2026-02): https://arxiv.org/abs/2506.10357
141. OpenHA, arXiv 2509.13347. https://arxiv.org/abs/2509.13347 (2025-09)
142. MineStudio, arXiv 2412.18293. https://arxiv.org/abs/2412.18293 (2024-12)
143. MCU, arXiv 2310.08367. https://arxiv.org/abs/2310.08367 (2023-10, rev. 2025-06)
144. Z. Wang et al., DEPS, arXiv 2302.01560. https://arxiv.org/abs/2302.01560 (2023-02)
145. H. Yuan et al., Plan4MC, arXiv 2303.16563. https://arxiv.org/abs/2303.16563 (2023-03)
146. G. Wang et al., Voyager, arXiv 2305.16291. https://arxiv.org/abs/2305.16291 (2023-05)
147. X. Zhu et al., Ghost in the Minecraft, arXiv 2305.17144. https://arxiv.org/abs/2305.17144 (2023-05)
148. Z. Wang et al., JARVIS-1, arXiv 2311.05997. https://arxiv.org/abs/2311.05997 (2023-11)
149. Odyssey, arXiv 2407.15325. https://arxiv.org/abs/2407.15325 (2024-07); https://github.com/zju-vipa/Odyssey
150. Plancraft, arXiv 2412.21033. https://arxiv.org/abs/2412.21033 (2024-12)
151. Altera, Project Sid, arXiv 2411.00114. https://arxiv.org/abs/2411.00114 (2024-10)
152. MineLand, arXiv 2403.19267. https://arxiv.org/abs/2403.19267 (2024-03)
153. TeamCraft, arXiv 2412.05255. https://arxiv.org/abs/2412.05255 (2024-12)
154. "Collaborating Action by Action" (MineCollab), arXiv 2504.17950 (2025-04): https://arxiv.org/abs/2504.17950; Mindcraft: https://github.com/kolbytn/mindcraft
155. PillagerBench, arXiv 2509.06235. https://arxiv.org/abs/2509.06235 (2025-09-07)
156. Y. Li et al., "Parallelized Planning-Acting...", arXiv 2503.03505. https://arxiv.org/abs/2503.03505 (2025-03-05; v2 2026-03-07)
157. TickingCollabBench, arXiv 2606.15684. https://arxiv.org/abs/2606.15684 (2026-06)
158. Oasis: https://oasis-model.github.io/ (2024-10-31); MineWorld, arXiv 2504.08388 (2025-04): https://arxiv.org/abs/2504.08388; Genie 3: https://deepmind.google/blog/genie-3-a-new-frontier-for-world-models/ (2025-08-05)
159. SIMA 2, arXiv 2512.04797. https://arxiv.org/abs/2512.04797 (2025-12); blog (2025-11-13)
160. Game-TARS, arXiv 2510.23691. https://arxiv.org/abs/2510.23691 (2025-10)
161. Lumine, arXiv 2511.08892. https://arxiv.org/abs/2511.08892 (2025-11)
162. NitroGen, arXiv 2601.02427. https://arxiv.org/abs/2601.02427 (2026-01)
163. CombatVLA, arXiv 2503.09527. https://arxiv.org/abs/2503.09527 (2025-03)
164. lmgame-Bench, arXiv 2505.15146 (2025-05): https://arxiv.org/abs/2505.15146; Orak, arXiv 2506.03610 (2025-06): https://arxiv.org/abs/2506.03610
165. smokydastona, Adaptive-Minecraft-Mob-Ai. https://github.com/smokydastona/Adaptive-Minecraft-Mob-Ai (s.f.)

**Diseño de combate**

166. N. Kraj, "Keys to Combat Design: Anatomy of an Attack". https://gdkeys.com/keys-to-combat-design-1-anatomy-of-an-attack/ (2020-04-25)
167. M. Birkhead, "Boss Design – Tips From A Combat Designer". https://www.gamedeveloper.com/design/opinion-boss-design---tips-from-a-combat-designer (2011-10-20)
168. M. Stout, "Enemy Attacks and Telegraphing". https://www.gamedeveloper.com/design/enemy-attacks-and-telegraphing (2015-09-02)
169. J. Bycer, "A Different Take on Sekiro". https://www.gamedeveloper.com/design/a-different-take-on-sekiro-shadows-die-twice (2019-04-02)
170. T. Thompson, "Cyber Demons: The AI of DOOM (2016)". https://www.gamedeveloper.com/design/cyber-demons-the-ai-of-doom-2016- (2018-08-06)
171. GDC 2018, "Embracing Push Forward Combat in DOOM". https://www.gdcvault.com/play/1024940/Embracing-Push-Forward-Combat-in (2018)
172. T. McIntosh, "Human Enemy AI in The Last of Us", Game AI Pro 2 cap. 34. https://www.gameaipro.com/GameAIPro2/GameAIPro2_Chapter34_Human_Enemy_AI_in_The_Last_of_Us.pdf (2015)
173. D. Isla, "Handling Complexity in the Halo 2 AI". https://www.gamedeveloper.com/programming/gdc-2005-proceeding-handling-complexity-in-the-i-halo-2-i-ai (2005-03-11)
174. D. Isla, "Building a Better Battle: Halo 3 AI Objectives", GDC 2008. https://gdcvault.com/play/497/Building-a-Better-Battle-HALO (2008)
175. M. Sheth, "Evolving Combat in God of War", GDC 2019. https://media.gdcvault.com/gdc2019/presentations/Sheth_Mihir_EvolvingCombat.pdf (2019)
176. M. Dawe, "Beyond the Kung-Fu Circle", Game AI Pro cap. 28. http://www.gameaipro.com/GameAIPro/GameAIPro_Chapter28_Beyond_the_Kung-Fu_Circle_A_Flexible_System_for_Managing_NPC_Attacks.pdf (2013)
177. S. Ocio Barriales, "Using Your Combat AI Accuracy to Balance Difficulty", Game AI Pro 3 cap. 33. https://www.gameaipro.com/GameAIPro3/GameAIPro3_Chapter33_Using_Your_Combat_AI_Accuracy_to_Balance_Difficulty.pdf (2017)
178. Beij, Straatman, "Killzone's AI: Dynamic Procedural Tactics", GDC Europe. https://www.guerrilla-games.com/media/News/Files/gdce05_killzone_ai.pdf (2005-08-31)
179. M. Jack, "Tactical Position Selection", Game AI Pro cap. 26. https://www.gameaipro.com/GameAIPro/GameAIPro_Chapter26_Tactical_Position_Selection.pdf (2013)
180. T. Thompson, "The Perfect Organism: The AI of Alien: Isolation". https://www.gamedeveloper.com/design/the-perfect-organism-the-ai-of-alien-isolation (2017-10-31)
181. M. Booth, "The AI Systems of Left 4 Dead", AIIDE. https://steamcdn-a.akamaihd.net/apps/valve/2009/ai_systems_of_l4d_mike_booth.pdf (2009)
182. R. Hunicke, "The Case for Dynamic Difficulty Adjustment in Games", ACE. https://dl.acm.org/doi/10.1145/1178477.1178573 (2005)
183. A. Gavin, "Making Crash Bandicoot – part 6". https://all-things-andy-gavin.com/2011/02/07/making-crash-bandicoot-part-6/ (2011-02-07)
184. Revisión sistemática de DDA, Multimedia Tools and Applications. https://link.springer.com/article/10.1007/s11042-024-18768-x (2024)
185. Guo, Thawonmas, Ren, Entertainment Computing 50:100663. https://www.sciencedirect.com/science/article/abs/pii/S1875952124000314 (2024)
186. Game Developer, "Enemy AI Design in Tom Clancy's The Division". https://www.gamedeveloper.com/design/enemy-ai-design-in-tom-clancy-s-the-division (s.f.)

**Evaluación**

187. L. Brown, T. Cai, A. DasGupta, "Interval Estimation for a Binomial Proportion", Statistical Science 16(2). https://projecteuclid.org/journals/statistical-science/volume-16/issue-2/Interval-Estimation-for-a-Binomial-Proportion/10.1214/ss/1009213286.full (2001)
188. Stockfish, "Fishtest Mathematics" y FAQ. https://official-stockfish.github.io/docs/fishtest-wiki/Fishtest-Mathematics.html (s.f.)
189. "Elo Uncovered", arXiv 2311.17295. https://arxiv.org/abs/2311.17295 (2023–2024)
190. M. Glickman, "Example of the Glicko-2 system". https://www.glicko.net/glicko/glicko2.pdf (2022-03-22)
191. R. Herbrich, T. Minka, T. Graepel, "TrueSkill", NIPS 2006. https://www.microsoft.com/en-us/research/publication/trueskilltm-a-bayesian-skill-rating-system/ (2006)
192. T. Minka, R. Cleven, Y. Zaykov, "TrueSkill 2", MSR-TR-2018-8. https://www.microsoft.com/en-us/research/wp-content/uploads/2018/03/trueskill2.pdf (2018-03)
193. V. Vanden Abeele et al., PXI, IJHCS 135:102370. https://eprints.qut.edu.au/133989/ (2020)
194. miniPXI. https://dl.acm.org/doi/10.1145/3549507 (2022); validación CHI 2024: https://dl.acm.org/doi/10.1145/3613904.3642270
195. R. Ryan, S. Rigby, A. Przybylski, "The Motivational Pull of Video Games" (PENS), Motivation & Emotion 30(4). https://www.researchgate.net/publication/225998888 (2006)
196. E. Law, F. Brühlmann, E. Mekler, "Systematic Review and Validation of the GEQ", CHI PLAY. https://dl.acm.org/doi/10.1145/3242671.3242683 (2018)
197. UT Austin, "Artificially intelligent game bots pass the Turing test". https://news.utexas.edu/2012/09/26/artificially-intelligent-game-bots-pass-the-turing-test-on-turings-centenary/ (2012-09-26)
198. L. Li, W. Chu, J. Langford, X. Wang, "Unbiased Offline Evaluation of Contextual-bandit-based News Article Recommendation", WSDM. https://arxiv.org/abs/1003.5956 (2011)
