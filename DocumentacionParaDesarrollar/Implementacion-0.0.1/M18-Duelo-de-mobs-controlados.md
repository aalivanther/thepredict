# M18. Duelo de mobs controlados

> **Estado:** Local listo — servidor pendiente (sesión 18; DT-40); duelo con clave pendiente de la aprobación del usuario · **Entrega:** F (extensión de la 0.0.1) · **Depende de:** M15, M16, M17 · **Desbloquea:** — (cierra la entrega F) · **Diseño:** [plan §12, protocolo A/B](../Plan-Jev-y-combate-tactico.md#12-matriz-de-pruebas-y-evidencia), [M13.5](M13-Suite-carga-latencia-calidad.md#m135-protocolo-ab-frente-a-la-ia-de-mythicmobs), [guion 0.0.1 §7](Guion-servidor-0.0.1.md#7-ab-frente-a-la-ia-de-mythicmobs-m135) · **Reglas:** [00](00-Arquitectura-y-reglas.md) · **Dominio:** [01](01-Modelo-de-dominio.md)

## Objetivo

Una prueba **visual y repetible** en la que dos mobs totalmente controlados intentan eliminarse: movimiento, ataques y defensas a la vista, con y sin ThePredict, en una arena cerrada que nunca afecta a jugadores ni a otros mobs. Sirve para:

1. comparar de forma evidente ThePredict (modo local y con Jev) frente a la IA clásica de MythicMobs, con las **mismas** capacidades (plan §12);
2. registrar **todo** lo que deciden y hacen los agentes (cada pregunta, respuesta y aceptación; cada cast, golpe, movimiento, defensa, retirada, cambio de táctica, caída de nivel y cambio de amenaza) para ver **qué mejorar**;
3. tener un simulador local, determinista y gratuito, que reproduce el duelo en las pruebas.

## Alcance

### Incluye

- Paquete de duelo nativo: dos duelistas con facciones, selectores de objetivo de MythicMobs, las mismas metas base, skills tipadas (M15), variantes clásicas y perfiles de ThePredict que solo admiten al rival (M17).
- Arena en un mundo propio y reglas de seguridad.
- Matriz de duelos (ThePredict contra clásico, espejo, ThePredict contra ThePredict y clásico contra clásico) con rondas, orden y reglas de justicia.
- Registrador de duelos **completo** en el JAR (`/thepredict duel`, permiso `thepredict.debug`, apagado por defecto), con formatos de archivo definidos.
- Ayudas visuales solo nativas: barra de jefe de MythicMobs, brillo de color, partículas por señal de táctica y marcador vanilla.
- Guion de servidor con comandos exactos, primero sin clave y después con clave, y costo por ronda.
- Simulador local de duelo sobre el motor real, extendiendo los arneses A/B.

### No incluye

- Generalización a entidades (M17), nivel de detalle y métrica `OutcomeWindow` (M16): se usan.
- Duelos de más de 2 contra 2, monturas, mobs voladores o especies con `Brain` (M05).
- Que Jev elija el objetivo (sigue siendo nativo, plan §5).
- Afirmar superioridad frente a cualquier IA manual (plan §12): las conclusiones se limitan a esta base.

## Base existente

| Archivo | Acción |
|---|---|
| `src/test/java/akyrion/thepredict/engine/TacticalAB.java`, `ExecutionFixture.java`, `EngineFixtures.java` | Base del simulador: el motor real con dobles de los puertos y reloj controlable (M18.7) |
| `src/test/java/akyrion/thepredict/engine/TacticalExecutionABTest.java` | Modelo de prueba de arnés que no afirma superioridad |
| `Guion-servidor-0.0.1.md` §7 | Variante clásica con las mismas skills por triggers (`ABClassic*`): el duelo sigue el mismo patrón |
| `adapter/jev/ScenarioRecorder.java` | Patrón del escritor del registrador: hilo daemon propio, cola acotada, temporal y movimiento atómico (M13, desviación 4) (M18.4) |
| `engine/DecisionDispatcher.java`, `ResultIntake.java`, `DecisionAcceptor.java`, `IntentControl.java`, `FallbackStep.java`, `TacticalExecution.java`, `ThreatChanges.java`, `OutcomeWindow.java` (M16) | Puntos donde el motor avisa al registrador (M18.4) |
| `engine/DecisionDispatcher.java` (tras M16.1) | Entrega al registrador el plan, la preparación y el cuerpo exacto enviado, en el hilo del servidor (M18.4) |
| `admin/ThePredictCommand.java`, `CommandServices.java` | Subcomando `duel` (M18.4) |
| `paquete-pruebas/archivos/` | Carpeta nueva `13-duelo/` (M18.1) |

## Subpuntos

### M18.1 Paquete de duelo nativo

- **Propósito:** dos duelistas iguales en todo salvo quién decide, construidos con MythicMobs y ThePredict sin lógica nueva de combate (A1).
- **Diseño:**
  - **Mobs** (`paquete-pruebas/archivos/13-duelo/plugins/MythicMobs/Mobs/ThePredictDuel.yml`). Una plantilla y cuatro variantes con `Template:` de MythicMobs:

    ```yaml
    DuelBase:
      Type: ZOMBIE
      Health: 60
      Damage: 0                       # sin golpe vanilla: todo el daño sale de las cinco skills (auditoría de M18)
      Options:
        FollowRange: 32
        PreventSunburn: true
        PreventOtherDrops: true
        PreventItemPickup: true
        Despawn: false
      Modules:
        ThreatTable: false            # el golpe de un jugador no puede cambiar el objetivo (M18.2)
      AIGoalSelectors:                # mismas metas base para las cuatro variantes (justicia, M18.3)
      - 0 clear
      - 1 meleeAttack
      - 2 randomStroll
      Skills:
      - skill{s=DuelRuleHeal} @self ~onTimer:20
      - skill{s=DuelRuleGuard} @self ~onTimer:20
      - skill{s=DuelRuleStrike} @self ~onTimer:10
      - skill{s=DuelRuleLeap} @self ~onTimer:20
      - skill{s=DuelRuleBolt} @self ~onTimer:20
      - skill{s=DuelTimeout} @self ~onSpawn
    DuelRed:
      Template: DuelBase
      Display: 'Duelist Red'
      Faction: duel_red
      Predictor: true
      AITargetSelectors:
      - 0 clear
      - 1 nearestSpecificFaction{faction=duel_blue}
      BossBar: { Enabled: true, Title: '[RED · ThePredict] <caster.predictor.tactic> <caster.predictor.state>', Range: 48, Color: RED, Style: SOLID }
      Skills:
      - glow{color=RED;duration=2400} @self ~onSpawn
      - modifyscore{objective=duel;entry=blue_wins;action=ADD;value=1} @self ~onDeath
      - command{c="say DUEL red died"} @self ~onDeath
    DuelRedClassic:
      Template: DuelBase
      Display: 'Duelist Red'
      Faction: duel_red
      Predictor: false
      AITargetSelectors:
      - 0 clear
      - 1 nearestSpecificFaction{faction=duel_blue}
      BossBar: { Enabled: true, Title: '[RED · classic]', Range: 48, Color: RED, Style: SOLID }
      Skills:
      - glow{color=RED;duration=2400} @self ~onSpawn
      - modifyscore{objective=duel;entry=blue_wins;action=ADD;value=1} @self ~onDeath
      - command{c="say DUEL red died"} @self ~onDeath
    # DuelBlue y DuelBlueClassic: iguales con duel_blue, color BLUE, objetivo duel_red y entry red_wins.
    ```

    Comprobado con javap en el JAR 5.13.1: el selector `nearestSpecificFaction` (alias `specificFaction`, argumento `faction`/`f`), `modifyscore` (`objective`, `entry`, `action`, `value`), `remove`, `setTarget` (alias `target`), `setfaction`, la opción `Faction` y el targeter `playersInRadius`. **`glow`**: la mecánica `effect:glow` (alias `glow`, `e:glow`) está marcada `deprecated=true` en su anotación `MythicMechanic` pero sigue registrada; es un aura (`Aura`) cuya `duration` por defecto es **200 ticks** (`MythicField` `defValue="200"`), así que se fija `duration=2400` (más que una ronda de 1 800 ticks). Si una versión futura la retira, la alternativa vanilla es `potion{type=GLOWING;duration=2400;level=0} @self` más el color del equipo (`/team modify duel_red color red` y `command{c="team join duel_red <caster.uuid>"} @self ~onSpawn`), coherente con los bandos de M17. Que las líneas `Skills` y `AIGoalSelectors` de la plantilla se hereden y se sumen se confirma al recargar (DT-40).
  - **Skills** (`Skills/ThePredictDuelSkills.yml`), las mismas para las cuatro variantes; ThePredict las lanza con su trigger y las clásicas por reglas:

    | Skill | Líneas | `Cooldown` | Condiciones propias | `Purpose` en el perfil (M15) |
    |---|---|---|---|---|
    | `DuelStrike` | partículas `crit`; `delay 5`; `damage{a=5} @target` | 1,5 | `targetwithin{d=3}` | `DAMAGE`, `MELEE`, `Reach: 2.5`, `WindUp: 5`, `MEDIUM`, `BestWhen: [TARGET_IN_MELEE]` |
    | `DuelBolt` | partículas `flame`; `delay 10`; `damage{a=3} @target` | 3 | `targetwithin{d=14}`, `lineofsight` | `DAMAGE`, `HITSCAN`, `Reach: 14`, `WindUp: 10`, `LOW`, `BestWhen: [TARGET_AT_RANGE]`, `Risks: [NEEDS_LINE_OF_SIGHT]` |
    | `DuelLeap` | `leap @target` (velocidad ajustada en la sesión 18.2); `delay 10`; `damage{a=2} @target` | 6 | `targetwithin{d=8}` | `GAP_CLOSE, DAMAGE`, `HITSCAN`, `Reach: 8`, `WindUp: 10`, `LOW`, `BestWhen: [TARGET_FLEEING, TARGET_AT_RANGE]` |
    | `DuelGuard` | partículas `enchanted_hit`; `shield{amount=6;maxShield=6} @self` | 8 | — | `DEFENSE`, `SELF`, `NONE`, `BestWhen: [TARGET_IN_MELEE]` |
    | `DuelHeal` | partículas `heart`; `heal{a=6} @self` | 10 | `healthpercent{p=<50%}` | `SUSTAIN`, `SELF`, `NONE`, `BestWhen: [SELF_LOW_HEALTH]` |
    | `DuelTimeout` | `delay 1800`; `command{c="say DUEL timeout"}`; `remove @self` | — | — | (no es choice) |

    Las reglas clásicas `DuelRule*` envuelven cada skill con `Conditions` que empiezan por `- predictorcontrol false` (M04): en las variantes clásicas siempre se cumplen; en las de ThePredict solo cuando ThePredict **no** controla (nivel 4). Reglas: `DuelRuleStrike` a ≤ 3 bloques; `DuelRuleBolt` entre 4 y 14; `DuelRuleLeap` entre 4 y 8; `DuelRuleGuard` a ≤ 3 con salud < 60 %; `DuelRuleHeal` con salud < 50 %.
  - **Perfiles** (`plugins/ThePredict/AI/ThePredictDuel.yml`), `duel_red` para `DuelRed` y `duel_blue` para `DuelBlue`, idénticos salvo `MythicID` y la facción admitida. **Sin `BaseAI`**: la IA base es la de los `AIGoalSelectors` del mob (M03: si falta `BaseAI`, se usan los del mob), la misma que la de las variantes clásicas.

    ```yaml
    duel_red:
      MythicID: DuelRed
      TypeOfMythicID: MOB
      Type: NORMAL
      Objective: Defeat the rival duelist and survive
      Participants:
        PlayerGameModes: []                    # nunca jugadores (M17.2)
        Entities:
          Allow: ['faction:duel_blue']
      Choices:
        strike: { MythicID: DuelStrike, Purpose: { … } }
        bolt:   { MythicID: DuelBolt,   Purpose: { … } }
        leap:   { MythicID: DuelLeap,   Purpose: { … } }
        guard:  { MythicID: DuelGuard,  RequiresTarget: false, Purpose: { … } }
        heal:   { MythicID: DuelHeal,   RequiresTarget: false, Purpose: { … } }
      Tactics:
        press_and_strike:       { Movement: APPROACH,      Choice: strike, Priority: 70 }
        keep_distance_and_bolt: { Movement: KEEP_DISTANCE, Choice: bolt,   Priority: 60 }
        leap_in:                { Movement: APPROACH,      Choice: leap,   Priority: 55 }
        guard_up:               { Movement: HOLD,          Choice: guard,  Priority: 50 }
        retreat_and_heal:       { Movement: RETREAT,       Choice: heal,   Priority: 80 }
        approach:               { Movement: APPROACH,                      Priority: 10 }
      Contexts:
        low_health_phase: { Priority: 90, Tactics: [retreat_and_heal, keep_distance_and_bolt] }
        healthy: { Priority: 80, Conditions: ['healthpercent{p=>50%} true'],
                   Tactics: [press_and_strike, keep_distance_and_bolt, leap_in, guard_up, approach] }
      Movement:
        Band: { Inner: 5, Outer: 9 }
        RangedReach: 14
        Retreat: { Distance: 8, Speed: 1.2 }
        Arena: { Radius: 18 }
    ```

    **Corrección de la auditoría:** el diseño tenía `Damage: 2` y la choice `punch` (`Kind: VANILLA_MELEE`) con la táctica `press_and_punch` para igualar el golpe vanilla del clásico; pero mientras ThePredict controla, la meta base de ataque no golpea (M05) y el clásico suma su golpe a las reglas, así que los dos lados no tenían el mismo daño. Ahora `DuelBase` tiene `Damage: 0` y no hay `punch`: todo el daño sale de las cinco skills compartidas (DT-40 e confirma en 18.2 que un golpe de 0 no da empuje ni invulnerabilidad asimétricos). El contexto `healthy` (vida por encima del 50 %, prioridad 80, sin `retreat_and_heal`) evita que el plan local elija retirarse con la vida llena, como mostró el simulador. La condición de vida en porcentaje es `healthpercent{p=<N%}`: `health{h=<N%}` compara la vida absoluta y en 5.13.1 falla con el `%` (javap de `HealthCondition` y `RangedDouble`; DT-40 f). El problema general (un jefe de ThePredict pierde el daño pasivo del golpe vanilla) queda anotado en DT-42 (descartada por el usuario el 2026-09-30: el daño cuerpo a cuerpo lo dispara una skill). Con el lint de M15.4 ninguna táctica da aviso (todas `FITS`).
- **Archivos:** `paquete-pruebas/archivos/13-duelo/…` (mobs, skills, perfil); `UserTestPackTest` (validación con el cargador y el vinculador reales sobre dobles de MythicMobs).
- **Reglas:** A1, W3, I7.
- **Pruebas:** `UserTestPackTest` carga y vincula el perfil sin avisos y comprueba que las cuatro variantes comparten `Health`, `Damage`, `AIGoalSelectors`, `Skills` de la plantilla y `Cooldown`.
- **Aceptación:**
  - [x] Mismas estadísticas, metas base, skills y cooldowns en las cuatro variantes (prueba del paquete: `UserTestPackTest.theFourDuelistsShareTheStatsGoalsRulesAndCooldownsOfTheirTemplate`; en juego, DT-40 a en 18.2).
  - [x] El perfil solo admite al duelista rival (`UserTestPackTest.theDuelStageBindsBothDuelistsAdmittingOnlyTheRivalWithoutAnyWarning`).

### M18.2 Arena y seguridad

- **Propósito:** que el duelo nunca afecte a jugadores ni a otros mobs, y que siempre termine.
- **Diseño:**
  - **Arena en un mundo propio** `duel` (plano, creado con un gestor de mundos, por ejemplo Multiverse-Core; se descarta la zona lejana en un mundo compartido porque las reglas de juego que exige el duelo son por mundo y cambiarían el mundo de los jugadores). Reglas de juego de Paper 26.2 (claves comprobadas con javap en `org.bukkit.GameRules`): `spawn_mobs false` (sin aparición natural y, en vanilla, sin **refuerzos de zombi** en dificultad difícil, que darían ventaja a cualquier lado; se confirma en la sesión 18.2, DT-40) y `advance_time false`. Suelo de 41 × 41 en y = 64 y caja de `barrier` hueca de y = 64 a y = 70, que deja **5 bloques de aire** (y = 65–69) sobre el suelo y techo de `barrier` en y = 70; centro `0 65 0`; puntos de aparición `0.5 65 -9.5` y `0.5 65 10.5`; sin agua, lava ni desniveles. `Movement.Arena.Radius: 18` en los perfiles; los clásicos quedan dentro por la pared.
  - **Solo se enfrentan entre sí** por cuatro vías nativas e independientes:
    1. `AITargetSelectors` con `clear` y `nearestSpecificFaction` del rival. Un jugador solo cuenta como de una facción si tiene el permiso `faction.<nombre en minúsculas>` y **no** tiene `mythicmobs.nofaction` (javap de `PermissionFactionProvider.isInFaction`). Los operadores y quien tenga `*` tienen los dos, así que quedan fuera; un jugador normal solo sería objetivo si alguien le concede `faction.duel_red` o `faction.duel_blue`, cosa que el guion prohíbe y comprueba (18.0).
    2. `Modules: ThreatTable: false`: un golpe de un jugador no cambia su objetivo.
    3. Perfiles con `PlayerGameModes: []` y `Allow` solo del rival (M17.2): ThePredict nunca percibe, apunta ni lanza contra otra cosa.
    4. Todas las skills de daño van a `@target` (nunca áreas), y el objetivo solo puede ser el rival.
  - **Observadores en `SPECTATOR`**: vanilla no los apunta y M17 no los admite.
  - **Límites:** como mucho 2 duelistas vivos (el guion mata los restos antes de cada ronda); cada duelista se elimina solo a los 90 s (`DuelTimeout`, empate); `/mm mobs kill <tipo>` por cada una de las cuatro variantes al terminar, nunca `killall`. El marcador usa el scoreboard principal, que es **global al servidor**, no por mundo: por eso usa un objetivo propio `duel` que no toca ningún otro.
- **Archivos:** guion (M18.6); guía del autor, sección "Duelos seguros".
- **Reglas:** A6 (ante la duda, no actuar), W3.
- **Pruebas:** en servidor, sesión 18.1.
- **Aceptación:**
  - [ ] Ningún jugador ni mob ajeno recibe daño de un duelista en la sesión 18.1.

### M18.3 Matriz de duelos y reglas de justicia

- **Propósito:** una comparación limpia y visual, sin ventajas ocultas (plan §12).
- **Diseño:**

  | Brazo | Rojo | Azul | Rondas sin clave (modo local) | Rondas con clave |
  |---|---|---|---|---|
  | A | `DuelRed` (ThePredict) | `DuelBlueClassic` | 20 | 10 |
  | B (espejo) | `DuelRedClassic` | `DuelBlue` (ThePredict) | 20 | 10 |
  | C | `DuelRed` (ThePredict) | `DuelBlue` (ThePredict) | 20 | 10 |
  | D (base) | `DuelRedClassic` | `DuelBlueClassic` | 20 | — |

  - **Ronda:** matar restos; aparecer rojo y azul en sus puntos; termina con la primera muerte o con `DUEL timeout` (empate). Máximo 90 s. **El marcador no se reinicia entre rondas:** acumula las victorias de toda la sesión y solo se reinicia al empezar la sesión (18.0); los empates y el desglose por ronda los da el registrador (M18.4).
  - **Orden y lados:** los brazos se alternan A, B, C, D, D, C, B, A…; los puntos de aparición de rojo y azul se intercambian cada dos rondas para anular sesgos del terreno. B es A con los colores y el lado cambiados.
  - **Justicia (plan §12):** mismas estadísticas, metas base (`AIGoalSelectors` de la plantilla), skills y `Cooldown` nativos, sin golpe vanilla (`Damage: 0`, corrección de la auditoría); las reglas clásicas son las mismas skills disparadas por triggers; `spawn_mobs false` elimina los refuerzos de zombi para todos. El nivel `BASE` de ThePredict es **equivalente** al clásico, no idéntico: tiene las mismas metas base (restauradas por M05 con `reapplyAIGoalSelectors`, DT-04) y las mismas reglas `DuelRule*`, pero la caída a `BASE` llega con un retraso de ticks y la reinstalación de metas tiene su propio momento (M05, DT-05); el simulador (M18.7) mide esa diferencia y el informe la cita. Un cast por intención y época (DT-28): un aumento de daño por duplicación es imposible y además se comprueba (el registrador cuenta casts por skill; el simulador falla si hay duplicados).
  - **Comprobación de D:** el brazo D debe dar ≈ 50 %. Los empates cuentan medio punto para cada lado en la tasa y se publican aparte. Con 20 rondas el intervalo de Wilson al 95 % tiene unos ±22 puntos, así que D solo detecta sesgos grandes, de 75/25 o más (15 de 20 da un intervalo que excluye el 50 %); sesgos menores necesitan más rondas locales (gratis) o el simulador. Si D excluye el 50 %, la arena o la aparición tienen sesgo y se corrige antes de leer A–C.
  - **Primero sin clave:** 80 rondas en modo local (ThePredict sin Jev, nivel `LOCAL`/`BASE`), costo 0. Después, con la aprobación del usuario, 30 rondas con clave (A, B y C).
  - **Lectura:** victorias con Wilson al 95 %; tiempo hasta la muerte (mediana y rango intercuartílico); daño hecho y recibido; aciertos y fallos por skill; retiradas y usos de guardia; reparto de niveles de ThePredict. Con 20 rondas sirve para ver diferencias grandes; para afinar, más rondas locales.
- **Archivos:** guion (M18.6); plantilla de informe (M18.8).
- **Reglas:** A7, W3; plan §12.
- **Pruebas:** el simulador (M18.7) reproduce la matriz y comprueba que D es simétrico.
- **Aceptación:**
  - [ ] Informe con los cuatro brazos, dispersión, intervalos y empates, y la comprobación de D con su límite de detección.

### M18.4 Registrador de duelos completo (`/thepredict duel`)

- **Propósito:** registrar en tiempo real **todo** lo que deciden y hacen los agentes del duelo, en el JAR (decisión del usuario), detrás de `thepredict.debug` y apagado por defecto. MythicMobs y vanilla no lo miden: los contadores de scoreboard `damage_dealt`/`damage_taken` solo existen para jugadores y ninguna mecánica da una traza por mob.
- **Diseño:**
  - **Comandos** (permiso `thepredict.debug`):

    ```text
    /thepredict duel start <etiqueta> <facciónA> <facciónB> [radio=24] [skills=DuelStrike,DuelBolt,…]              (jugador: mundo y centro = su posición)
    /thepredict duel start <etiqueta> <facciónA> <facciónB> <mundo> <x> <y> <z> [radio=24] [skills=…]            (consola o jugador: mundo y centro explícitos)
    /thepredict duel status
    /thepredict duel stop
    ```

    `<etiqueta>` debe cumplir `[a-z0-9-]{1,40}` (sin barras ni puntos: no hay recorrido de rutas); además, la ruta resuelta debe quedar dentro de `plugins/ThePredict/duels/`. `facciónA` es el lado `red` y `facciónB` el `blue` en los archivos. `skills=` lista las skills que se cuentan como casts; por defecto, la unión de las choices `MYTHIC_SKILL` de los perfiles vinculados cuyo `Participants.Entities.Allow` nombra alguna de las dos facciones (en el paquete: las cinco skills del duelo; así los envoltorios `DuelRule*` y `DuelTimeout` no cuentan).
  - **Puertos nuevos en `engine`** (propuesta de enmienda de 00 §4 abajo):
    - `DuelRecording` (lo llama `admin`, lo implementa `adapter.server.duel.DuelRecorder`): `DuelStatus start(DuelRequest)`, `DuelStatus status()`, `DuelStatus stop()`.
    - `DecisionTap` (el motor y `adapter.jev` avisan, el registrador escucha; por defecto `DecisionTap.NONE`, sin coste; la raíz conecta un `SwitchableDecisionTap` de `engine` que reenvía al registrador solo mientras graba, con una comprobación `volatile` O(1)):

      | Método | Lo llama | Hilo |
      |---|---|---|
      | `questionsPlanned(EncounterId, long sequence, List<QuestionSpec> planned, Preparation preparation, DecisionDispatcher.Outcome dispatch, CombatFacts)` | `DecisionDispatcher`, una vez decidido el despacho (después de `prepare` y del intento de reserva, antes de `decide`); `sequence` es siempre la tomada con `nextSequence()` antes de `prepare` (M16.1), también si no se envía | servidor |
      | `requestBody(EncounterId, long sequence, String body)` | `DecisionDispatcher`, justo después de entregar `PreparedRequest` a `decide` (el cuerpo ya existe desde `prepare`, M16.1) | servidor |
      | `resultReceived(EncounterId, long sequence, DecisionResult, long roundTripNanos)` | `ResultIntake` | servidor |
      | `answerDiscarded(EncounterId, long sequence, DiscardReason)` | `DecisionAcceptor` | servidor |
      | `intentEvent(MobKey, IntentEvent)` (`ADOPTED`, `RENEWED`, `REJECTED`, `CONTINGENCY_FIRED`, `LOCAL_PLANNED`, `LEVEL_CHANGED`, con táctica, movimiento, confianza, nivel anterior y nuevo, clave de pregunta y causa `FallbackReason`) | `IntentControl`, `FallbackStep`, `ReactiveLayer` | servidor |
      | `castAttempted(MobKey, TacticId, ChoiceId, ExecutionOutcome)` | `TacticalExecution` | servidor |
      | `threatChanged(MobKey, UUID participant, double amount, String cause)` | `ThreatChanges` | servidor |
      | `outcomeClosed(MobKey, OutcomeWindow.Summary)` | `OutcomeWindow` (M16.8, que define el record) | servidor |

  - **Lo que el registrador lee por su cuenta** (`adapter.server.duel`, hilo del servidor): muestras cada 5 ticks de cada duelista seguido (como mucho 8: `ActiveMob` vivos de las dos facciones en el mundo y el radio; se detectan también los que aparecen después); `EntityDamageByEntityEvent` y `EntityDamageEvent` entre seguidos o sobre ellos (daño final, fracción de vida, causa, si la víctima bloqueaba); `EntityDeathEvent` y `EntityRemoveEvent` (fin de ronda por muerte o por `DuelTimeout`); `MythicSkillEvent` en `MONITOR` e ignorando cancelados, solo para las skills de `skills=` y lanzadores seguidos. **Límite conocido** (javap y DT-40): `MythicSkillEvent` se dispara en `executeInternal` **antes** del bloqueo de cooldown y del consumo de cargas, y también en `execute()` sin comprobación de uso; por eso un evento no demuestra que la skill terminara. En ThePredict, el cast se confirma con `castAttempted` (`CAST`); en los clásicos, el evento se registra como "intento" y el **acierto** se mide por daño (un daño del lanzador a su rival en los 20 ticks siguientes).
  - **Ronda y brazo:** la ronda empieza con la primera muestra con un duelista vivo de cada lado y termina cuando un lado no tiene ninguno. El **brazo** se deduce del tipo de cada lado (`tp` si el mob tiene perfil vinculado de ThePredict, `classic` si no): A = rojo `tp` y azul `classic`; B = al revés; C = los dos `tp`; D = los dos `classic`. El intercambio de lados se deduce del punto de aparición (signo de `z` inicial).
  - **Formatos** (decisión de diseño: JSONL para las decisiones, que son anidadas, y un CSV por tipo de evento para abrirlos en una hoja de cálculo), en `plugins/ThePredict/duels/<etiqueta>-<UTC yyyyMMdd-HHmmss>/`. Todas las filas de los **CSV** llevan `tick`, `round`, `arm` y el **UUID** de los mobs implicados (decisión del usuario: las salidas locales pueden llevar UUID); las líneas de `decisions.jsonl` llevan los campos definidos para su `type` (tabla de más abajo):

    | Archivo | Una fila por | Campos |
    |---|---|---|
    | `decisions.jsonl` | Suceso de una decisión (**líneas tipadas**, ver abajo) | Campo `type` ∈ `planned`, `body`, `result`, `acceptance`, `local`; cada línea lleva la clave de unión `encounter` + `sequence` (más `question` en `acceptance`) y la marca `t_nanos` (`ServerClock.nanoTime()`, que 00 §4 declara válido en cualquier hilo, así que todas las líneas usan el mismo reloj); el resto de campos, abajo |
    | `samples.csv` | Duelista y 5 ticks | `tick,round,arm,uuid,side,kind,health_ratio,absorption,x,y,z,yaw,distance_to_rival,gap_to_rival,target_uuid,level,tactic,movement,confidence,raised_shield,blocking,active_item` |
    | `casts.csv` | Intento de cast | `tick,round,arm,uuid,side,kind,source,skill,tactic,choice,outcome` (`source` `THEPREDICT` con su `ExecutionOutcome`, o `MYTHIC_EVENT` con `ATTEMPT`) |
    | `damage.csv` | Daño recibido por un seguido o hecho por él | `tick,round,arm,attacker_uuid,attacker_side,victim_uuid,victim_side,cause,final_damage,victim_health_fraction,victim_blocking,attributed_skill,is_hit_of_cast` |
    | `intents.csv` | Evento de intención o de nivel | `tick,round,arm,uuid,event,from_level,to_level,tactic,movement,confidence,question_key,reason` (cambios de táctica, contingencias, caídas y vueltas de nivel) |
    | `defense.csv` | Uso de guardia o escudo | `tick,round,arm,uuid,kind` (`GUARD_CAST`, `SHIELD_RAISED`, `SHIELD_BLOCKING`, `BLOCKED_HIT`) |
    | `retreats.csv` | Retirada | `tick,round,arm,uuid,source,distance_before,distance_after` (`INTENT` para una intención `RETREAT`; `MOTION` para un clásico que se aleja más de 4 bloques del rival en 40 ticks mientras el rival se acerca) |
    | `threat.csv` | Cambio de amenaza | `tick,round,arm,mob_uuid,participant_uuid,amount,cause` (en el paquete de duelo, con `ThreatTable: false`, queda vacío; se usa en otras arenas) |
    | `outcomes.csv` | Ventana de resultado cerrada (M16.8) | `tick,round,arm,uuid,level,tactic,movement,dealt,taken,reached,casts,hits,mob_died,target_died,effect` |
    | `rounds.csv` | Ronda | `round,arm,spawn_swapped,red_uuid,red_kind,blue_uuid,blue_kind,start_tick,end_tick,winner,end_reason,red_dealt,blue_dealt,red_hits,blue_hits,requests,input_tokens` |
    | `summary.md` | Grabación | Totales por brazo (victorias con Wilson, empates, tiempo hasta la muerte, daño, aciertos por skill, retiradas, guardias, reparto de niveles, solicitudes, tokens y costo), filas descartadas por cola llena y motivo de parada |

    **`decisions.jsonl` con líneas tipadas** (decisión: es la opción más simple y robusta; no hay tabla de espera en el servidor ni emparejado en vivo). Cada suceso se escribe **cuando ocurre y en el hilo donde ocurre**, con la clave de unión en todas las líneas; el informe los une después:

    | `type` | Lo escribe | Hilo | Campos, además de `type`, `encounter`, `sequence` y `t_nanos` |
    |---|---|---|---|
    | `planned` | `questionsPlanned` | servidor | `tick`, `round`, `arm`, `level_detail`, `mobs` (UUID, lado, tipo), `questions` (clave, UUID del mob, situación, opciones planeadas por el motor), `dispatch` (`SENT`, `NO_BUDGET`, `NOTHING_TO_ASK` o `PREPARE_FAILED` con su detalle), `sent_options` (por clave de pregunta, las opciones que se enviarían tras la deduplicación de M16.1; vacío con `NOTHING_TO_ASK` y `PREPARE_FAILED`), `dropped_questions` y `local_mobs` (UUID de los mobs resueltos en local porque su `NOW` no se envía) |
    | `body` | `requestBody` | servidor | Solo si hubo envío (`READY`): `body` (el JSON exacto enviado) y `chars`; lleva `tick`; `round` y `arm` se heredan de la línea `planned` con la misma clave. Con `NOTHING_TO_ASK` o `INVALID` no hay línea `body` ni `result` |
    | `result` | `resultReceived` | servidor | `tick`, `outcome` (`answered` o `failed` con `FailureKind`), `model`, `input_tokens`, `round_trip_ms`, por pregunta `choice`, `probabilities`, `confidence` y `fits` si hubo idoneidad |
    | `acceptance` | `answerDiscarded` e `intentEvent` | servidor | `tick`, `question` (clave), `mob` (UUID), `decision` (`ADOPTED`, `RENEWED`, `REJECTED`, `DISCARDED`, `CONTINGENCY_STORED`), `level`, `tactic`, `reason` (`FallbackReason` o `DiscardReason`) |
    | `local` | `intentEvent(LOCAL_PLANNED)` | servidor | `tick`, `round`, `arm`, `mob`, `tactic`, `level`, `reason`; `sequence` = −1 (sin solicitud; es el **único** uso de −1) |

    Líneas que siguen a cada valor de `dispatch` en `planned` (misma `encounter` y `sequence`):

    | `dispatch` | Líneas posteriores |
    |---|---|
    | `SENT` | Un `body`; después un `result` (`answered`, o `failed` con su `FailureKind`, incluido `TIMEOUT` si vence el plazo); después, una `acceptance` por pregunta respondida |
    | `NO_BUDGET` | Ninguna (la secuencia queda como hueco) |
    | `NOTHING_TO_ASK` | Ninguna |
    | `PREPARE_FAILED` | Ninguna (sin efecto en el servicio desde la auditoría de M16: se cuenta como `INVALID_REQUEST` en `status`) |

    La unión es `(encounter, sequence)` (y `question` para las aceptaciones); `encounter` es único por arranque (I2) y cada grabación es de un solo arranque. Una solicitud cuyo plazo vence **sí** tiene línea `result`, con `outcome: failed` y `FailureKind` `TIMEOUT`: `ResultIntake` libera las solicitudes vencidas como `TIMEOUT` sintético (M09). `DISCARDED` en `acceptance` es otra cosa: respuestas que llegan tarde o que `DecisionAcceptor` descarta (secuencia vieja, sesión cambiada, objetivo cambiado…, con su `DiscardReason`). Una solicitud sin `result` solo se ve si el servidor se detuvo durante la grabación. `summary.md` no necesita la unión: se calcula con contadores en memoria mientras se graba. Para análisis se incluye la tarea `duelJoin` (`src/test`, sin red) que une las líneas por clave en un JSON por solicitud e **ignora el grupo `sequence` = −1** (las líneas `local`, que no pertenecen a ninguna solicitud), y el equivalente con `jq`: `jq -s 'group_by([.encounter,.sequence]) | map({key: .[0].encounter, seq: .[0].sequence, lines: .})' decisions.jsonl`.

    CSV con cabecera, separador coma y comillas RFC 4180; JSONL UTF-8, una línea por objeto.
  - **Escritura:** las filas se formatean como texto inmutable en el hilo del servidor y se encolan en una cola acotada de **8 192** filas; un **hilo daemon propio** (`ThePredict-DuelWriter`, uno solo, vivo solo mientras se graba) las escribe con búfer; si la cola se llena, la fila se descarta y se cuenta (aparece en `status` y en `summary.md`). Es una excepción explícita y acotada de T1 (propuesta abajo), el mismo patrón que `ScenarioRecorder` (M13, desviación 4). En el apagado (T7), `stop` con una espera máxima de 2 s antes de cerrar el cliente HTTP.
  - **Límites (A5), generosos para una sesión completa de 80 + 30 rondas:** 8 duelistas seguidos; 500 rondas; 4 h por grabación (la sesión completa dura ≤ 2 h 45 min con rondas de 90 s); **128 MB por archivo y 512 MB por grabación**. Estimación de la sesión completa: `samples.csv` ≈ 16 MB (2 mobs × 4 muestras/s × 9 900 s × ≈ 200 B), `decisions.jsonl` ≈ 7 MB con `compact` (≈ 2 400 solicitudes × ≈ 3 KB; ≈ 20 MB con `full` e idoneidad), el resto < 10 MB: < 60 MB en total. **Al alcanzar un límite**, el registrador se detiene solo: vacía la cola, escribe `summary.md` con `stopped_by: <límite>`, avisa al emisor del comando si está conectado y deja en el registro `Duel recording '<etiqueta>' stopped: <límite> reached`.
  - Sin coste cuando no graba: `DecisionTap.NONE` y listeners que salen en O(1).
  - `verifyPluginJar`: el registrador es una función de diagnóstico del plugin (como `debug.recordScenarios`), no una herramienta de medida de `src/test`; su nombre no usa las palabras que la comprobación rechaza (se revisa la lista al implementar).
- **Archivos:** crear `engine/DuelRecording.java` (con `DuelRequest`, `DuelStatus`), `engine/DecisionTap.java` (con `IntentEvent`; `OutcomeSummary` **no** se crea aquí: es `OutcomeWindow.Summary`, definido en M16.8, y `DecisionTap.outcomeClosed` lo recibe), `engine/SwitchableDecisionTap.java`, `adapter/server/duel/DuelRecorder.java`, `DuelSampler.java`, `DuelRounds.java`, `DuelListener.java`, `DuelRows.java` (formato CSV y líneas JSONL tipadas), `DuelWriter.java`; `src/test/java/akyrion/thepredict/adapter/server/duel/DuelJoin.java` y tarea `duelJoin` en `build.gradle.kts` (fuera de `check`); modificar los puntos del motor de "Base existente" (incluido `DecisionDispatcher` para `questionsPlanned` y `requestBody`), `admin/ThePredictCommand.java` (o un `DuelCommand` nuevo), `admin/CommandServices.java`, raíz (`ServerComponents`, `SessionServices`, `DecisionServices`), `plugin.yml` (uso del subcomando); pruebas `DuelRecorderTest` (dobles), `DuelRoundsTest`, `DuelRowsTest`, `DuelWriterTest`, `SwitchableDecisionTapTest`, pruebas del motor que comprueban cada llamada del tap, `ThePredictCommandTest`, `ArchitectureRulesTest` (el subpaquete respeta D5 y D7).
- **Reglas:** A3, A5, A7, D2, D4, D5, D6, D7, D8, T1 (con la excepción propuesta), T2, T5, T7, C8, C10, I6 (lo enviado a Jev no cambia; los archivos locales sí llevan UUID), W3.
- **Pruebas:** ronda completa con dobles (inicio, fin por muerte y por eliminación, brazo y lado deducidos); `decisions.jsonl` con una solicitud respondida, una vencida (línea `result` con `failed: TIMEOUT`), una respondida tarde y descartada (`acceptance` `DISCARDED` con `STALE_SEQUENCE`: este caso **solo se puede construir con dobles**, porque en el motor real `InFlightRegistry.expire` cancela la future de la solicitud vencida y su respuesta ya no llega; el doble del cliente entrega la respuesta a destiempo a propósito), un `NOTHING_TO_ASK`, un `NO_BUDGET` y un `PREPARE_FAILED` (cada uno, una línea `planned` con su `dispatch` y su secuencia real, sin líneas posteriores; `duelJoin` los muestra como solicitudes de una sola línea) y un plan local (`sequence` = −1, que `duelJoin` ignora): cada línea lleva su `type` y su clave, y `duelJoin` reconstruye cada solicitud completa (`planned` + `body` + `result` + `acceptance`) aunque las líneas lleguen intercaladas con las de otros encuentros; cada archivo con su cabecera y una fila de cada tipo; acierto y fallo por la ventana de 20 ticks; `requestBody` desde otro hilo solo encola; cada límite detiene la grabación con su motivo y aviso; etiqueta con `/`, `..` o mayúsculas rechazada; forma de consola sin mundo rechazada; el escritor no bloquea el hilo llamador.
- **Aceptación:**
  - [x] **Local:** una sesión completa (80 + 30 rondas) cabe en los límites y produce todos los archivos, con el registrador real sobre el simulador (`DuelMatrixTest.aFullSessionFitsTheLimitsWritesEveryFileAndJoinsEveryRequest`).
  - [ ] **Servidor:** la sesión completa 18.3 + 18.4 cabe en los límites y produce todos los archivos (18.5).
  - [x] **Local:** cada decisión se reconstruye uniendo por `(encounter, sequence)` sus líneas `planned`, `body`, `result` y `acceptance`: cuerpo, respuesta, probabilidades, confianza, nivel y aceptación o rechazo con causa (`DuelRecorderTest.decisionsJsonlJoinsEveryRequestByItsKeyWhateverTheOrderOfTheLines`, `DuelMatrixTest` con el Jev guionizado).
  - [ ] **Servidor:** lo mismo con las respuestas reales de Jev (18.4 y 18.5).
  - [x] **Local:** sin coste medible cuando no graba (`duelMeasure`: el mismo tiempo por tick simulado con `DecisionTap.NONE` y con el `SwitchableDecisionTap` suelto; sin grabación no se construye ningún evento).
  - [ ] **Servidor:** sin coste medible en `tick_work` cuando no graba.

### M18.5 Ayudas visuales nativas

- **Propósito:** que la diferencia se vea a simple vista.
- **Diseño (todo nativo, ya en el paquete de M18.1):**
  - **Brillo de color** por lado: `glow{color=RED;duration=2400}` / `BLUE` al aparecer (mecánica obsoleta pero registrada en 5.13.1; alternativa vanilla en M18.1).
  - **Barra de jefe** de MythicMobs por duelista: en las variantes de ThePredict, el título muestra la táctica y el estado con los placeholders de M04 (`<caster.predictor.tactic>`, `<caster.predictor.state>`); en las clásicas, `[RED · classic]`.
  - **Partículas y sonido por táctica** (solo ThePredict, con las señales de M04): `- effect:particles{p=heart;a=12} @self ~onSignal:PREDICTOR_RETREAT_AND_HEAL`, `- effect:particles{p=cloud;a=20} @self ~onSignal:PREDICTOR_LEAP_IN`, `- effect:particles{p=enchanted_hit;a=16} @self ~onSignal:PREDICTOR_GUARD_UP`, `- effect:particles{p=flame;a=10} @self ~onSignal:PREDICTOR_KEEP_DISTANCE_AND_BOLT`, `- effect:sound{s=block.note_block.pling} @self ~onSignal:PREDICTOR_PRESS_AND_STRIKE`. Una vez por intención nueva (M04).
  - **Marcador vanilla** en la barra lateral: objetivo `duel` del scoreboard principal (global al servidor), victorias acumuladas por `modifyscore` al morir cada duelista; se reinicia solo al empezar la sesión.
  - Registro opcional con `decision.logDecisions: true` para ver en la consola cada táctica y cada ventana de resultado (M16.8).
- **Archivos:** los del paquete de M18.1.
- **Reglas:** A1; I9.
- **Pruebas:** `UserTestPackTest` (líneas válidas con el vocabulario de M04); sesión 18.2.
- **Aceptación:**
  - [ ] En la sesión 18.2 se ven el brillo durante toda la ronda, la barra con la táctica y las partículas de cada señal.

### M18.6 Guion de servidor del duelo

- **Propósito:** que el usuario lo ejecute paso a paso, primero gratis, y devuelva la evidencia.
- **Diseño:** sesión nueva **18** del paquete de pruebas (y §13 del guion 0.0.1). Requiere un gestor de mundos (los comandos usan Multiverse-Core; con otro, sus equivalentes de crear y teletransportar).
  - **18.0 Preparación (una vez por sesión):**

    ```text
    (copiar paquete-pruebas/archivos/13-duelo/plugins/ al servidor)
    /mm reload                                          (vuelve a leer y vincular los perfiles de ThePredict)
    /thepredict status                                  -> Profiles: LOADED (…, 2 profiles read, 2 active, 0 errors, 0 warnings)
    /mv create duel normal -t FLAT
    /mv tp duel                                         (lo que sigue se ejecuta en el mundo duel)
    /gamerule spawn_mobs false
    /gamerule advance_time false
    /time set noon
    /fill -21 64 -21 21 70 21 minecraft:barrier hollow
    /fill -20 64 -20 20 64 20 minecraft:grass_block
    /scoreboard objectives add duel dummy "Duel"
    /scoreboard objectives setdisplay sidebar duel
    /scoreboard players reset red_wins duel
    /scoreboard players reset blue_wins duel
    /gamemode spectator
    ```

    Desde la consola, las reglas y el relleno se ejecutan en el mundo del duelo con `execute in minecraft:duel run gamerule spawn_mobs false` (y así con cada línea; `minecraft:duel` es la clave de dimensión que da Multiverse al mundo `duel`). Comprobar que ningún jugador de prueba tiene `faction.duel_red` ni `faction.duel_blue` (con LuckPerms: `/lp user <jugador> permission check faction.duel_red`) (M18.2).
  - **18.1 Seguridad (sin clave):** un segundo jugador en supervivencia dentro de la arena; ronda de A; 60 s: el jugador no recibe daño ni es objetivo (`/thepredict inspect` sobre el duelista de ThePredict: solo `entity_1`).
  - **18.2 Visual y comprobaciones (sin clave):** una ronda de C; brillo durante toda la ronda, barras con táctica, partículas por señal; `DuelLeap` no saca a nadie de la caja (ajustar su velocidad si hace falta); ningún zombi de refuerzo aparece al herir a un duelista (DT-40).
  - **18.3 Matriz local (sin clave, costo 0):** `config.yml` con `jev.apiKey: ''` y sin `TYPESAFE_API_KEY` en el entorno (`/thepredict status` en `UNCONFIGURED`); `/thepredict duel start local-<fecha> duel_red duel_blue duel 0 65 0 30`; 80 rondas en el orden de M18.3. Por ronda:

    ```text
    /mm mobs kill DuelRed
    /mm mobs kill DuelRedClassic
    /mm mobs kill DuelBlue
    /mm mobs kill DuelBlueClassic
    /mm mobs spawn <rojo del brazo> 1 duel,0.5,65,-9.5       (o 10.5 si toca intercambiar lados)
    /mm mobs spawn <azul del brazo> 1 duel,0.5,65,10.5
    (esperar "DUEL red died", "DUEL blue died" o "DUEL timeout")
    ```

    Al final: `/thepredict duel stop` y `/thepredict status`.
  - **18.4 Con clave (PAGO, tras M16.10 y con aprobación):** clave en el entorno del servidor (W6), `/thepredict check` → `READY`; `/thepredict status` antes; `/thepredict duel start jev-<fecha> duel_red duel_blue duel 0 65 0 30`; 30 rondas (10 de A, 10 de B, 10 de C) en orden alterno; `/thepredict status` cada 5 rondas para vigilar `input_tokens`; `duel stop`; `status` final.
  - **18.5 Copia de vuelta:** la carpeta `plugins/ThePredict/duels/<etiqueta>-<UTC>/` completa, las salidas de `/thepredict status` antes y después y `logs/latest.log` filtrado (`grep -E 'DUEL|Outcome|Tactical execution|Duel recording' logs/latest.log`) a `Evidencia-0.0.1/<fecha>-duelo/`. Los archivos llevan UUID de mobs a propósito (M18.4); los cuerpos de `decisions.jsonl` no deben llevar ninguno (I6): `grep -c -E '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'` sobre el campo `body` da 0.
  - **Costo por ronda con clave** (estimación a 1,5 caracteres por token, cota superior; solicitud de producción de M16.2: 1 mob, 1 entidad, `NOW` + 1 contingencia, 7 opciones; los tokens reales salen de `status` y de `decisions.jsonl`):

    | Brazo | Encuentros de ThePredict | Solicitudes por ronda (≤ 1/s; ronda típica 60 s, máximo 90) | `compact` (≈ 1 400 tokens por solicitud a 1,5 c/t; 953 reales) | `full`, el default (≈ 3 204 tokens a 1,5 c/t; 1 884 reales) | `full` + idoneidad (≈ 4 600 a 1,5 c/t) |
    |---|---|---|---|---|---|
    | A o B | 1 | 60 (máx. 90) | ≈ 0,0035 USD (máx. 0,0053) | ≈ 0,0081 (máx. 0,012); real ≈ 0,0047 | ≈ 0,012 (máx. 0,017) |
    | C | 2 | 120 (máx. 180) | ≈ 0,0071 (máx. 0,011) | ≈ 0,016 (máx. 0,024); real ≈ 0,0095 | ≈ 0,023 (máx. 0,035) |
    | **Total 18.4** (10 A + 10 B + 10 C) | | ≈ 2 400 (máx. 3 600) | **≈ 0,14 USD (máx. 0,21)**; real ≈ 0,096 | **≈ 0,32 USD (máx. 0,48)**; real **≈ 0,19 (máx. 0,28)** | ≈ 0,46 (máx. 0,70) |

    Con `full`, el default desde el re-banco de M16.10: **≈ 0,32 USD** con la cota W7 de 1,5 caracteres por token (≈ 3 204 tokens por solicitud; máximo ≈ 0,48 USD con rondas de 90 s) y **≈ 0,19 USD** con los 1 884 tokens reales del re-banco (máximo ≈ 0,28). Con `compact`, ≈ 0,14 USD (≈ 0,096 reales). Techo propuesto para M18: **0,60 USD**; la sesión se detiene si la diferencia de `input_tokens` de `status` supera 14 M (≈ 0,59 USD), lo que solo puede ocurrir con `full` e idoneidad y rondas largas.
- **Archivos:** `Guion-servidor-0.0.1.md` (§13 nueva y §9 con las filas de costo), `paquete-pruebas/Guia-de-pruebas-por-sesiones.md`, `Checklist-DT-y-casillas.md` (DT-40), `Como-reportar-resultados.md`.
- **Reglas:** W3, W6, W7 (propuesta en M16).
- **Pruebas:** `UserTestPackTest` (archivos y bloques de comandos iguales a los del guion).
- **Aceptación:**
  - [x] Sesión 18 con comandos exactos para el mundo del duelo, esperado, costo y copia de vuelta ([sesión 18](paquete-pruebas/Guia-de-pruebas-por-sesiones.md#sesión-18-duelo-de-mobs-controlados-sin-clave-m18), [G13 §13](Guion-servidor-0.0.1.md#13-duelo-de-mobs-controlados-m186); `UserTestPackTest` y `DuelCommandTest` comparan los bloques y las líneas con el código).

### M18.7 Simulador local de duelo

- **Propósito:** reproducir el duelo en las pruebas, de forma determinista y gratis, con el motor real de ThePredict; detectar regresiones y ver qué mejorar antes de ir al servidor.
- **Diseño:**
  - `src/test/java/akyrion/thepredict/engine/duel/`: `DuelSimulator`, `DuelWorld`, `ClassicDuelist`, `OracleJev`, `DuelResult`, `DuelMatrix`, pruebas `DuelSimulatorTest` y `DuelMatrixTest`. Extiende la idea de `TacticalAB`: mismo motor (`ExecutionFixture`, reloj de 50 ms por tick, dobles de los puertos), ahora con **dos** combatientes y un mundo.
  - **Mundo (`DuelWorld`):** plano de radio 18, posiciones `(x, z)` y caja de 0,6 bloques; movimiento por tick según la meta vigente: `APPROACH` a `0,23 × approachSpeed` hasta el alcance cuerpo a cuerpo; `KEEP_DISTANCE` a la banda; `RETREAT` alejándose hasta `retreatDistance` con `retreatSpeed`; `HOLD` quieto; `FLANK` en arco; nivel `BASE` y clásico: `meleeAttack` y `randomStroll` (acercarse y golpe vanilla cada 20 ticks en alcance, de 0 de daño con `Damage: 0`). Invulnerabilidad de 10 ticks tras un golpe. Salto de `DuelLeap`: coloca al lanzador a 2 bloques del objetivo. Sin refuerzos (como `spawn_mobs false`).
  - **Skills:** tabla que replica `ThePredictDuelSkills.yml` (daño, `delay`, `Cooldown`, condiciones); `DuelMatrixTest` lee el YAML del paquete (con `MythicYaml` de las pruebas de binding) y falla si la tabla no coincide.
  - **Lado ThePredict:** el motor real (sesión, coordinador, `IntentControl`, `ReactiveLayer`, `OutcomeWindow`, `DecisionTap`). `CombatReader` doble construye `CombatFacts` desde el mundo (bandas, tendencia, línea de visión siempre verdadera, `grouped` falso, participante `entity_1` con especie `zombie`); `CombatActions` doble aplica los casts al mundo respetando las reglas de `HitChecks` (alcance por entrega, invulnerabilidad); `MobControl` doble informa progreso real. Un `DecisionTap` de prueba escribe los mismos archivos que el registrador (M18.4), así que el informe local y el del servidor se comparan con las mismas herramientas.
  - **Modos de Jev:** `NONE` (sin clave: `LOCAL`/`BASE`), `ORACLE(correctRatio, semilla)` (respuestas guionizadas: con probabilidad `correctRatio` la táctica que puntúa mejor una función fija de propósitos y condiciones activas, si no otra elegible; confianza fija 0,8; mide el camino, no el modelo, como `TacticalAB`), y `REPLAY(answers.jsonl)` opcional (respuestas crudas del re-banco de M16.10 o de `decisions.jsonl` de un duelo real; solo para análisis manual).
  - **Lado clásico (`ClassicDuelist`):** las mismas reglas `DuelRule*` (temporizadores de 10 y 20 ticks con fase aleatoria por semilla, condiciones de distancia y salud) y golpe vanilla (de 0 de daño).
  - **Determinismo:** semilla por ronda para la fase de los temporizadores y ±1 bloque de posición inicial; mismas semillas en todos los brazos; sin `Thread.sleep` (P2).
  - **Salida (`DuelResult`):** ganador, ticks, daño hecho y recibido, casts, aciertos y fallos por skill, retiradas, reparto de niveles, solicitudes y tokens estimados, resultado medio por táctica. `DuelMatrix.run(brazos, rondas, modo)` agrega con media, desviación, mínimo, máximo y Wilson.
  - **Pruebas que sí afirman (invariantes, no superioridad):** D simétrico (200 semillas: victorias de rojo en 50 % ± 10 puntos); ningún cast duplicado por época en ningún brazo; la variante de ThePredict en `BASE` forzado desde el primer tick juega igual que la clásica (misma secuencia de casts con la misma semilla) y, con la caída a `BASE` a mitad de ronda, la diferencia se mide y se publica (la "equivalencia, no identidad" de M18.3); todas las rondas terminan antes de 1 800 ticks.
  - **Medida sin afirmar:** tarea `duelMeasure` (como `m13Measure`, fuera de `check`) que escribe `build/reports/m18/duel-local.md` con la matriz local (A–D × 200 semillas, modos `NONE` y `ORACLE(0,85)`).
- **Archivos:** los de `engine/duel/` en `src/test`; `build.gradle.kts` (`duelMeasure`); `verifyPluginJar` (el paquete de pruebas `duel` nunca entra en el JAR).
- **Reglas:** P2, P6, A5, C10, C11; plan §12 (sin afirmar superioridad).
- **Pruebas:** las listadas.
- **Aceptación:**
  - [x] `./gradlew duelMeasure --offline` escribe la matriz local; el build normal corre solo las pruebas de invariantes (rápidas).
  - [x] D simétrico y ningún cast duplicado.

### M18.8 Informe y lectura: qué mejorar

- **Propósito:** convertir el duelo en decisiones de mejora.
- **Diseño:**
  - Plantilla `paquete-pruebas/Plantilla-informe-duelo.md`: por brazo, victorias (Wilson) y empates, tiempo hasta la muerte, daño, aciertos por skill, retiradas y guardias, niveles, costo; comprobación de D con su límite de detección; comparación local frente a servidor (simulador frente a sesión 18.3).
  - Tabla de diagnóstico (se rellena con `outcomes.csv`, `casts.csv`, `damage.csv`, `intents.csv` y `decisions.jsonl`):

    | Síntoma | Lectura | Acción |
    |---|---|---|
    | Una táctica con efecto medio negativo y mucho uso | Jev o el planificador la eligen donde no sirve | Revisar su `Purpose` (`BestWhen`, `Risks`) o su prioridad (M15) |
    | Aciertos bajos de una skill a distancia | Se lanza sin visión o fuera de alcance real | `Reach` o `NEEDS_LINE_OF_SIGHT` declarados (M15.6) |
    | Mucho tiempo en `BASE` o `LOCAL` con clave | Latencia, presupuesto o confianza baja (`intents.csv` y `decisions.jsonl` dan la causa de cada caída) | `minimumConfidence` por perfil (M16.7), `questionDetail` (M16.2), presupuesto |
    | Respuestas rechazadas por confianza en situaciones claras | Planteamiento | Pasar esos cuerpos de `decisions.jsonl` al banco de M16 y etiquetarlos (M16.9) |
    | ThePredict gana en local y pierde con Jev | Planteamiento o etiquetas | Banco de M16 con escenarios del duelo |
    | D no es simétrico | Arena o aparición sesgadas | Corregir antes de leer A–C |
  - Los cuerpos de `decisions.jsonl` (ya con participantes `entity_N`) entran en el banco de M16 como escenarios nuevos, etiquetados por el usuario con el mismo procedimiento (M16.9).
- **Archivos:** plantilla; `Informe-0.0.1.md` (sección nueva al cerrar M18).
- **Reglas:** W1, W3.
- **Pruebas:** `UserTestPackTest` (la plantilla existe y enlaza).
- **Aceptación:**
  - [ ] Informe del duelo escrito con conclusiones limitadas a esta base (plan §12).

## Cambios propuestos a 00/01

**00. Arquitectura y reglas**

- §2: `adapter.server` gana el subpaquete `duel` ("registro de duelos para diagnóstico").
- §4, **enmienda de la regla de puertos** ("no se crean puertos sin ese motivo"): se añaden dos puertos, los dos probados con dobles, que es el motivo que exige §4:
  - **`DuelRecording`** (`start`, `status`, `stop`; hilo: servidor; implementa `adapter.server.duel.DuelRecorder`). Motivo: `admin` debe controlar una función que necesita Paper y MythicMobs, y D6 le impide depender de un adapter; las pruebas de `admin` usan un doble.
  - **`DecisionTap`** (métodos de M18.4; hilo: servidor, todos, incluido `requestBody`, porque desde M16.1 el cuerpo se prepara en el hilo del servidor; implementa `adapter.server.duel.DuelRecorder`; `DecisionTap.NONE` y `SwitchableDecisionTap` en `engine`). Motivo: el motor no puede importar adapters (D2) y `adapter.jev` no puede hablar con `adapter.server` salvo a través de `engine` (D7); las pruebas del motor verifican cada llamada con un doble.
- §5, **excepción acotada de T1** (T1-bis): "`adapter.server.duel.DuelWriter` PUEDE tener un único hilo daemon propio, vivo solo mientras hay una grabación, que recibe filas de texto inmutables por una cola acotada (8 192) y escribe archivos en `plugins/ThePredict/duels/`; nunca llama a Bukkit, MythicMobs ni `engine` ni lee estado mutable. Todo lo demás del registrador, incluido `requestBody` (que desde M16.1 se llama en el hilo del servidor, porque el cuerpo existe desde `prepare`), corre en el hilo del servidor. Mismo patrón que `adapter.jev.ScenarioRecorder`. T7: el apagado detiene el escritor, con 2 s de espera como máximo, antes de cerrar el cliente HTTP."
- §9, W7 (M16) se aplica también al duelo con clave.
- Encabezado: "Revisión 12: subpaquete `duel`, puertos `DuelRecording` y `DecisionTap` y T1-bis (M18)".

**01. Modelo de dominio**

- Encabezado: "**Revisión 13** (fecha): puertos `DuelRecording` y `DecisionTap` para `/thepredict duel` (M18, sección 10)."
- §8: `DuelRecording` con `record DuelRequest(String label, String sideA, String sideB, UUID worldId, double centerX, double centerY, double centerZ, double radiusBlocks, Set<String> countedSkills)` (`label` `[a-z0-9-]{1,40}`; radio en `(0, 64]`) y `record DuelStatus(boolean recording, String label, int rounds, int trackedMobs, long rowsDropped, long bytesWritten, Optional<String> stoppedBy, String directory)`; `DecisionTap` con los métodos de M18.4, `enum IntentEventKind { ADOPTED, RENEWED, REJECTED, CONTINGENCY_FIRED, LOCAL_PLANNED, LEVEL_CHANGED }` y `record IntentEvent(IntentEventKind kind, TacticId tactic, Movement movement, double confidence, ControlLevel from, ControlLevel to, Optional<QuestionKey> question, Optional<FallbackReason> reason)`; `SwitchableDecisionTap`.
- §10, fila: "Puertos `DuelRecording` y `DecisionTap` | M18".
- Sin tipos nuevos en `domain`.

**Registro de decisiones (entrada nueva propuesta)**

- **DT-40. Paquete de duelo en 5.13.1 y Paper 26.2.** Preguntas: (a) ¿las líneas `Skills` y `AIGoalSelectors` de una variante se heredan y se suman a las de su `Template`? (b) ¿`glow{color=…;duration=2400}` (marcada obsoleta) sigue funcionando? (c) ¿`spawn_mobs false` impide los refuerzos de zombi en difícil? (d) `MythicSkillEvent` se dispara en `executeInternal` antes del bloqueo de cooldown y de las cargas, en `execute()` sin comprobación de uso y en los envoltorios `DuelRule*`: ¿cuántos eventos por cast real da cada regla clásica? Sesión 18.2 (conteo de `casts.csv` frente a partículas y daño observados). Alternativas: (a) repetir las líneas en cada variante; (b) `potion{type=GLOWING}` con equipos; (c) `Options` de refuerzos si MythicMobs los expone o elegir una especie sin refuerzos; (d) medir los clásicos solo por daño. Estado: Abierta.

## Riesgos y decisiones abiertas

| Riesgo | Mitigación |
|---|---|
| 20 rondas no separan diferencias pequeñas (D solo detecta sesgos de 75/25 o más) | Rondas locales gratis ilimitadas; el simulador da 200 semillas; con clave, ampliar solo si el primer pase es prometedor |
| El modo local ya gana al clásico y oculta el aporte de Jev | El brazo C con clave frente al mismo C en local lo separa; M16.8 mide por nivel |
| `BASE` de ThePredict no es idéntico al clásico | Mismas metas base y reglas; el simulador mide la diferencia de la caída tardía y el informe la cita |
| Una skill de `leap` saca a un duelista de la arena | Caja de `barrier` con 5 bloques de aire y techo, y `Arena.Radius`; `DuelTimeout` cierra la ronda |
| El costo con clave crece con más detalle o rondas largas | Default `full`: ≈ 0,32 USD con la cota W7 (máx. ≈ 0,48) y ≈ 0,19 USD con tokens reales (máx. ≈ 0,28); techo de 0,60 USD y control de `input tokens` de `/thepredict duel status` cada 5 rondas |
| El registrador se deja encendido o llena el disco | Parada automática a las 4 h y en cada límite de tamaño, con aviso |
| Un jugador con permiso de facción entra en la arena | El guion comprueba que nadie tiene `faction.duel_*`; ThePredict no admite jugadores en estos perfiles |

## Terminado

M18 está terminado cuando se cumple W4 y además:

- [ ] Casillas de M18.1–M18.8 marcadas.
- [ ] Matriz local ejecutada en el simulador y en el servidor (18.3); matriz con clave ejecutada o pendiente con la aprobación del usuario registrada.
- [x] 00 y 01 actualizados; DT-40 registrada.
- [ ] Informe del duelo en `Evidencia-0.0.1/` y enlazado desde el informe de la 0.0.1.

## Estado de implementación

> **Local listo — servidor pendiente (2026-09-29; disponibilidad en tiempo real de M10 y DT-42 cerrada el 2026-09-30: 1644 pruebas en 222 clases, ver «Tras el cierre»).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` termina en `BUILD SUCCESSFUL` sin avisos de javac ni de Javadoc estricto: **1632 pruebas en 221 clases, 0 fallos** (1576 en 211 antes de M18: 56 pruebas y 10 clases nuevas; 1626 en la entrega, antes de las correcciones de la auditoría). `verifyPluginJar` y `ArchitectureRulesTest` pasan; ningún comentario `//` ni `/* */` en Java, principal ni de pruebas (C9, comprobado con un analizador que salta cadenas, caracteres y bloques de texto). La prueba de paridad de M17 (`EntityGeneralizationParityTest`) pasa y **ninguna referencia dorada cambió**: sin `duel start` el motor se comporta igual que antes. **Ninguna llamada de red** (ni a TypeSafe ni a otro host; ninguna tarea `live*`), nadie leyó `.env` y no se arrancó ningún servidor de Minecraft: las casillas de juego (M18.2, M18.3, M18.5, M18.8 y el `tick_work` de M18.4) siguen sin marcar. 00 recibe la revisión 13 (subpaquete `duel`, puertos `DuelRecording` y `DecisionTap`, T1-bis, T7 y W7) y 01 la revisión 13; el registro de M01 recibe DT-40 (`Abierta`, con la evidencia estática de javap).

### Artefacto

`build/libs/ThePredict-0.0.1.jar`, 1 063 815 bytes, 534 entradas, SHA-256 `8a2703ddd9d906f0892f7a5d98374f71de3512d637c042638f2c17b07c0736f5` (con la disponibilidad en tiempo real de M10, 2026-09-30; tras las correcciones de la auditoría de M18 era `4c9d213d915187d50365954c6834538e92d10b823726e4dbf6153e9fa3ee9b83`, 1 060 257 bytes; el de la entrega era `37520312…7227`, 1 056 928 bytes); reproducible (los builds limpios dan la misma huella). Registrado en el [CHANGELOG](../../CHANGELOG.md#artefacto).

### Archivos

Rutas relativas a `src/main/java/akyrion/thepredict/` salvo que se indique otra cosa.

| Subpunto | Archivos |
|---|---|
| M18.1 | `paquete-pruebas/archivos/13-duelo/plugins/MythicMobs/Mobs/ThePredictDuel.yml` (`DuelBase` y los cuatro duelistas), `…/MythicMobs/Skills/ThePredictDuelSkills.yml` (cinco skills, `DuelTimeout` y reglas `DuelRule*`), `…/ThePredict/AI/ThePredictDuel.yml` (`duel_red`, `duel_blue`) |
| M18.2 | Guía del autor §4.10 "Duelos seguros"; sesión 18 del paquete |
| M18.3 | Sesión 18 (orden A, B, C, D, D, C, B, A; lados cada dos rondas), plantilla del informe |
| M18.4 | Creados `engine/DuelRecording` (con `DuelRequest`, `DuelStatus`), `engine/DecisionTap` (con `IntentEventKind`, `IntentEvent`), `engine/SwitchableDecisionTap`, `engine/IntentTap` y `engine/AcceptanceTap` (auxiliares de paquete de `IntentControl` y `DecisionAcceptor`); en `adapter/server/duel/`: `DuelRecorder`, `DuelSampler`, `DuelRounds`, `DuelListener`, `DuelRows`, `DuelWriter`, y además `DuelFile`, `DuelScene`, `DuelistReading`, `LiveDuelScene`, `DuelSession`, `DuelSummary`, `DuelCombat`, `DuelTap` y `DuelDecisionLines`; `admin/DuelCommand`; raíz `DuelWiring`. Modificados `engine/DecisionDispatcher` (`questionsPlanned`, `requestBody`), `ResultIntake` (`resultReceived`, también el `TIMEOUT` sintético), `DecisionAcceptor` (`answerDiscarded` y veredicto con pregunta), `AnswerGroups` (`questionOf`), `IntentControl` (`applyAnswered`, `applyLocal`, eventos de intención y `castAttempted`), `FallbackStep` (causa de la caída calculada antes del plan local), `IntentCoordination`, `TacticalExecution` (`Parts.tap`), `OutcomeEvents` (`outcomeClosed`), `ThreatEnrichment` y `ThreatChanges` (`threatChanged`); `admin/ThePredictCommand` (subcomando `duel`); raíz `DecisionServices` (`Wiring.tap`), `ProfileCatalogs` (`boundProfiles`), `ThePredictPlugin` (tap, registrador, muestras en la tarea del motor y apagado T7); `src/main/resources/plugin.yml` (uso y permiso) |
| M18.4 (pruebas y herramientas) | `src/test/…/adapter/server/duel/`: `DuelJoin` (tarea `duelJoin`), `DuelTestKit`, `DuelRecorderTest`, `DuelRoundsTest`, `DuelRowsTest`, `DuelWriterTest`; `engine/SwitchableDecisionTapTest`, `engine/DecisionTapCallsTest`; `admin/DuelCommandTest`; `DuelWiringTest`; `ArchitectureRulesTest` (+1); `build.gradle.kts` (`duelJoin`, `duelMeasure`, fuera de `check`) |
| M18.5 | Líneas de `ThePredictDuel.yml` (brillo, barra, señales, marcador) |
| M18.6 | [Guía por sesiones](paquete-pruebas/Guia-de-pruebas-por-sesiones.md) (sesión 18 y su parte con clave), [G13 §13](Guion-servidor-0.0.1.md#13-duelo-de-mobs-controlados-m186) y §9, [checklist](paquete-pruebas/Checklist-DT-y-casillas.md) (DT-40, casillas de M18), [cómo reportar](paquete-pruebas/Como-reportar-resultados.md), [README del paquete](paquete-pruebas/README.md) |
| M18.7 | `src/test/…/engine/duel/`: `DuelSimulator`, `DuelWorld`, `ClassicDuelist`, `OracleJev`, `DuelResult`, `DuelMatrix`, `DuelSimulatorTest`, `DuelMatrixTest`, y además `DuelSkill` (la tabla del YAML), `DuelEngine` (el motor real sobre dobles), `DuelSessionRun` y `DuelMeasure` (tarea `duelMeasure`); [matriz medida](evidencia/m18-simulador-2026-09-29/duel-local.md) |
| M18.8 | [Plantilla del informe del duelo](paquete-pruebas/Plantilla-informe-duelo.md); sección 7 del [informe](Informe-0.0.1.md) |
| Auditoría (2026-09-30) | `engine/SwitchableDecisionTap` (contención de fallos), `DecisionDispatcher` y `ResultIntake` (orden del tap), `IntentTap`, `IntentControl`, `FallbackStep` y `DecisionAcceptor` (sin asignaciones sin grabación), `RequestBudget` (precio único) y `admin/StatusFormatter`; `adapter/server/duel/DuelRecorder` (`refusal`, `awaitWriter`, sobrecargas de `damaged`, `Notifier` con dos métodos, `stopped_by: tap failure`, ronda `unfinished`), `DuelWriter` (`create`, `begin`), `DuelRounds` (`abort`), `DuelSession`, `DuelSummary`, `DuelListener`; `adapter/server/context/ParticipantSelectors` (aviso con `/mm reload`); raíz `DuelWiring`; `src/main/resources/examples/MythicMobs/Skills/ThePredictGuardianSkills.yml` y `AI/example.yml.example` (`healthpercent`); paquete `13-duelo` (`Damage: 0`, sin `punch`, contexto `healthy`, `healthpercent`) y etapas 03, 06, 09 y 10; guías, guiones, M01 (DT-24, DT-39, DT-40, DT-42), M07, M15, M17, CHANGELOG e informe |
| Documentos | Este documento; [README](README.md); [00](00-Arquitectura-y-reglas.md) (revisión 13); [01](01-Modelo-de-dominio.md) (revisión 13); [registro de M01](M01-Registro-de-decisiones.md) (DT-40); [guía del autor](../../Guias/Guia-del-autor.md) (§4.10); [guía del administrador](../../Guias/Guia-del-administrador.md) (§5, §8.3); [CHANGELOG](../../CHANGELOG.md); README raíz |

### Pruebas (56 nuevas: 50 de la entrega y 6 de la auditoría)

| Clase | Pruebas | Qué demuestra |
|---|---|---|
| `adapter/server/duel/DuelRecorderTest` (nueva) | 9 | Ronda de A de principio a fin con dobles: brazo, lado, `rounds.csv`, cast de ThePredict (`THEPREDICT`, `CAST`) y del clásico (`MYTHIC_EVENT`, `ATTEMPT`), acierto dentro de 20 ticks y fallo fuera, envoltorio `DuelRule*` sin contar, `summary.md`; eliminación = empate y lados intercambiados por la `z`; `decisions.jsonl` con una respondida, una vencida (`result` `failed` `TIMEOUT`), una tardía descartada (`STALE_SEQUENCE`), `NOTHING_TO_ASK`, `NO_BUDGET` y `PREPARE_FAILED` (una línea `planned` con su secuencia real y nada más) y un plan local (`sequence` −1, que `duelJoin` ignora), intercaladas entre dos encuentros y reconstruidas por `DuelJoin`; ningún UUID en las líneas `body`; parada por 4 h y por 500 rondas con su motivo y aviso; parada por archivo lleno y por fallo de escritura; etiquetas con `/`, `..`, `.`, mayúsculas, vacía o de 41 caracteres rechazadas; inicio con otra grabación en marcha o con el mundo descargado rechazado; sin grabación nada se crea y el tap sigue mudo; skills contadas por defecto; un cuerpo avisado desde otro hilo solo se encola mientras el disco está bloqueado |
| `adapter/server/duel/DuelRoundsTest`, `DuelRowsTest`, `DuelWriterTest` (nuevas) | 4 + 4 + 4 | Inicio y fin de ronda, los cuatro brazos, tope de 8 seguidos, un lado con otro vivo; CSV RFC 4180, números, JSON escapado y cabeceras con `tick`, `round`, `arm`; cada archivo con su cabecera y `summary.md` al final, disco lento que nunca bloquea al que llama con cola llena que descarta y cuenta, límite por archivo y por grabación, hilo daemon `ThePredict-DuelWriter` |
| `engine/DecisionTapCallsTest` (nueva) | 7 | Cada llamada del tap desde el motor real (`ExecutionFixture`): `planned SENT`, `body`, `result` antes del veredicto `ADOPTED` con `q0`, `CONTINGENCY_STORED` y `castAttempted`; `TIMEOUT` sintético y la respuesta cancelada descartada como `STALE_SEQUENCE`; `NOTHING_TO_ASK` y `PREPARE_FAILED` con su secuencia real y sin más líneas; `NO_BUDGET`; sin clave, `LOCAL_PLANNED` y ventanas `LOCAL`; cambio de amenaza; nada sin grabación |
| `engine/SwitchableDecisionTapTest` (nueva) | 2 | Sin grabación no reenvía; enganchada reenvía todo hasta que se suelta |
| `admin/DuelCommandTest` (nueva) | 5 | Forma de consola con mundo, centro, radio y `skills=`; forma de jugador centrada en él con el radio por defecto y `radius=`; consola sin mundo, etiquetas, mundo descargado, radio y facciones iguales rechazados; `start`, `status` y `stop` con las líneas que cita la guía por sesiones (comparadas con el archivo); permiso y autocompletado |
| `DuelWiringTest` (nueva) | 2 | Las muestras corren cada 5 ticks dentro de la única tarea del motor y paran al cerrar; cerrar una grabación vieja nunca para la nueva |
| `engine/duel/DuelSimulatorTest`, `DuelMatrixTest` (nuevas) | 5 + 3 | D simétrico en 200 semillas (50 % ± 10 puntos); ningún cast duplicado en A, B y C con `NONE` y `ORACLE`; nivel 4 forzado igual al clásico (misma secuencia de casts, ganador y ticks); caída tardía a nivel 4 medida; toda ronda termina en 1 800 ticks o antes; tabla de skills y reglas igual al YAML del paquete (cooldown, daño, `delay`, escudo, curación, distancias, vida y temporizadores, en el orden de `DuelBase`); agregados y Wilson (15 de 20 excluye el 50 %, 14 no); una sesión completa de 80 + 30 rondas grabada por el registrador real cabe en los límites, escribe todos los archivos y une cada solicitud |
| Ampliadas | 5 | `ArchitectureRulesTest` (+1: el subpaquete `duel` cumple D5 y D7, `admin` no lo importa, `engine` tampoco); `UserTestPackTest` (+4: el paquete de duelo vincula `duel_red` y `duel_blue` sin avisos y solo con el rival; las cuatro variantes heredan lo común de la plantilla, las reglas empiezan por `predictorcontrol false` y el daño va a `@target`; las señales nombran tácticas del perfil; la sesión 18 tiene los mismos bloques que G13 §13, las líneas del código, el costo, DT-40 y la plantilla) |

**Pruebas existentes cambiadas:**
- Firma: `ExecutionFixture`, `CoordinatorFixture`, `LoadFixture`, `CoordinatorContractTest`, `OutcomeEventsTest`, `EncounterCoordinationTest` y `DecisionServicesTest` pasan el tap (`DecisionTap.NONE`, o un `SwitchableDecisionTap` sin nada enganchado en `ExecutionFixture`); `ThePredictCommandTest` usa el constructor de cuatro argumentos.
- Comportamiento querido: `ThePredictCommandTest` espera `duel` en el autocompletado con el permiso de depuración y `PluginResourcesTest` el uso `/thepredict [status|check|inspect <nearest|uuid>|debug|duel]`.
- Auxiliares: `MythicYaml` resuelve `Template` como MythicMobs (listas del padre y después las propias; un valor propio gana; javap de `MythicConfigImpl.merge`), y `UserTestPackTest.everyTestMobKeepsItsChunk…` sigue la plantilla para leer `Options.Despawn`.

### Medidas locales (sin red; `./gradlew duelMeasure --offline`, 200 semillas por brazo; [informe](evidencia/m18-simulador-2026-09-29/duel-local.md))

Con el paquete corregido tras la auditoría (`Damage: 0`, sin `punch`, contexto `healthy`) y, desde el 2026-09-30, con la disponibilidad en tiempo real de M10 (desviación 29: cada skill sale a su propio `Cooldown` en `LOCAL` y con Jev). Puntuación de rojo (un empate vale medio) con su Wilson al 95 %; **no afirma superioridad** (plan §12):

| Modo | A (rojo ThePredict) | B (azul ThePredict) | C (los dos) | D (clásicos) |
|---|---|---|---|---|
| `NONE` | 0,420 [0,354; 0,489] | 0,570 [0,501; 0,637] | 0,500 [0,431; 0,569] | 0,550 [0,481; 0,617] |
| `ORACLE(0,85)` | 0,110 [0,074; 0,161] | 0,865 [0,811; 0,906] | 0,480 [0,412; 0,549] | 0,550 [0,481; 0,617] |

Antes de la disponibilidad (misma base, mismas semillas): `NONE` A 0,255 [0,200; 0,320], B 0,730, C 0,515, D 0,550; `ORACLE(0,85)` A 0,055 [0,031; 0,096], B 0,950, C 0,450, D 0,550. Los perfiles del duelo no cambiaron. En el brazo A sin clave el rojo de ThePredict lanza ahora 1 548 `DuelBolt` y 2 740 `DuelStrike` (antes 1 202 y 2 610). D no cambia (no usa el motor). El arnés cuenta ahora los tokens de una solicitud al enviarla, en la ronda que la envió: antes, la respuesta tardía de la última ronda de C (el motor del simulador se comparte entre brazos) sumaba 700 tokens a la ronda de D siguiente; ahora D queda con 0 solicitudes y 0 tokens y C con 6 172 280 (`DuelMatrixTest.theClassicArmNeverCountsATokenBecauseTokensBelongToTheRoundThatSentThem`). El resto de la matriz no cambia.

- D sigue simétrico: su intervalo incluye el 50 % y la prueba de invariantes (50 % ± 10 puntos) pasa. C, con los dos lados de ThePredict, también queda en torno al 50 % (0,500 y 0,480).
- En A y B, en esta base, el lado de ThePredict gana menos rondas que el clásico, sin clave y con el oráculo. El oráculo puntúa con la misma función que el plan local (M18.7) y elige otra opción el 15 % de las veces; no dice nada de Jev, que lee las descripciones. Antes de la corrección, con `Damage: 2`, `punch` y sin `healthy`, A daba 0,025 sin clave y C terminaba siempre en empate sin daño (el plan local elegía `retreat_and_heal` con la vida llena).
- Ningún cast duplicado en ningún brazo ni modo; nivel 4 forzado idéntico al clásico; caída tardía a nivel 4 de 0,5 ticks de media cuando el rojo deja de percibir a su rival.
- Tap sin grabación: 1 648 ns por tick simulado con `DecisionTap.NONE` y 1 440 con un `SwitchableDecisionTap` suelto: ruido de la máquina.
- Una sesión completa grabada (80 rondas sin clave y 30 con el oráculo): `samples.csv` de la local 3,0 MB y `decisions.jsonl` de la del oráculo 2,2 MB; muy por debajo de 128 MB por archivo y 512 MB por grabación.

### Costo del duelo con clave (G13 §13 y §9)

30 rondas (10 A, 10 B, 10 C) ≈ 2 400 solicitudes (máximo 3 600 con rondas de 90 s). Con el default `full`: **≈ 0,32 USD** con la cota W7 de 1,5 caracteres por token (≈ 3 204 tokens por solicitud; máximo ≈ 0,48) y **≈ 0,19 USD** con los 1 884 tokens reales del re-banco (máximo ≈ 0,28). Con `compact`: ≈ 0,14 (≈ 0,096 reales). Techo 0,60 USD: parar si `input tokens` de `/thepredict duel status` pasa de 14 000 000.

### Evidencia estática (javap y fuentes, sin red)

- **MythicMobs 5.13.1** (`VersionsOfJars/MythicMobsPremium-5.13.1-SNAPSHOT.jar`): `SpecificFactionGoal` (`nearestSpecificFaction`, alias `specificFaction`, `faction`/`f`, lista con comas en mayúsculas; jugadores por `FactionProvider.isInFaction`, `ActiveMob.getFaction()` para mobs) y `OtherFactionMonstersGoal` (`nearestOtherFactionMonsters`) en `volatilecode.v26_2`; `PermissionFactionProvider.isInFaction` (`faction.<minúsculas>` y `mythicmobs.nofaction`); `MobType` lee `Faction`, `Modules.ThreatTable`, `AIGoalSelectors`, `AITargetSelectors`, `BossBar.Enabled`/`Title`/`Range`/`Color`/`Style` (`AbstractBossBar.BarColor.RED`/`BLUE`, `BarStyle.SOLID`) y `Options.*`; `MobExecutor` lee `Template`/`Templates` y `TemplateUtil.processTemplate` + `MythicConfigImpl.merge` suman las listas (padre primero); `GlowEffect` (`effect:glow`, alias `glow`, `deprecated=true`, `color`/`c`, `duration` de `Aura` 200 por defecto); `ModifyScoreMechanic` (`objective`, `entry`, `action` `ADD`, `value`, scoreboard principal); `ShieldMechanic` (`amount`, alias exactos `maxabsorb`, `maxshield`, `ma`, `ms`); `LeapMechanic` (`velocity` 100 por defecto); `HealMechanic`, `DamageMechanic`, `RemoveMechanic`, `ParticleEffect` (`p`, `a`), `SoundEffect` (`s`), `CommandMechanic`; condiciones `targetWithin` (`d`), `lineOfSight` y `healthPercent` (`HealthPercentCondition`: `healthpercent`, alias `hppercent`, `percent`/`p`; con `%` compara `vida / vida máxima × 100`, sin `%` la razón); `HealthCondition` (`health`) compara la vida **absoluta** y `RangedDouble` no admite `%` (`Double.parseDouble` tras quitar el operador), así que `health{h=<50%}` no sirve (DT-40 f; la primera versión de este documento lo daba por comprobado, y no lo estaba); `/mm mobs kill` (`setDespawned` y `remove`, sin muerte), `spawn` (`<mundo>,<x>,<y>,<z>`), `listactive`, `info`; `MythicSkillEvent` (`getSkillMetadata().getCaster().getEntity()`, `getSkill().getInternalName()`, cancelable).
- **Paper 26.2** (`paper-api` 26.2 y el servidor parcheado del scratchpad): `org.bukkit.GameRules.SPAWN_MOBS` (`spawn_mobs`), `SPAWN_MONSTERS` y `ADVANCE_TIME` (`advance_time`); `Zombie.hurtServer` solo crea refuerzos con `ServerLevel.isSpawningMonsters()` (`SPAWN_MOBS` y `SPAWN_MONSTERS`); `WorldCreator` da `minecraft:<nombre en minúsculas>` a un mundo creado por nombre; `ExecuteCommand` (`in`, `dimension`, `run`), `GameRuleCommand`, `FillCommand` (`hollow`); `EntityDamageEvent.getCause`/`getFinalDamage`, `EntityRemoveEvent`, `LivingEntity#getAbsorptionAmount`, `getBodyYaw`, `hasActiveItem`/`getActiveItem`, `World#getNearbyLivingEntities(Location, double)` y `CommandSender#sendPlainMessage` (no obsoleto).
- **Multiverse-Core** no está entre los JAR comprobados: `/mv create duel normal -t FLAT` y `/mv tp duel` siguen la sintaxis del diseño y la sesión dice cómo usar otro gestor.

### Desviaciones y decisiones

| # | Desviación o decisión | Motivo |
|---|---|---|
| 1 | La sesión 18 usa `/mm reload` y la línea `Profile catalog LOADED (…, 2 profiles read, 2 active, 0 errors, 0 warnings)` (y `Profiles:` de `status`); el diseño de 18.0 se corrigió igual | `/thepredict` no tiene subcomando de recarga ni de preparación (desviación 14 de M15); `/mm reload` vuelve a leer y vincular los perfiles (`ProfileCatalogs.onMythicReload`). El bloque de 18.0 añade `/tp @s 0.5 67 0.5` antes de `SPECTATOR` y la versión de consola incluye `time set noon` |
| 2 | La sección del guion es la §13, como dice el diseño (no §18) | Conformidad con M18.6; no existía una §13 |
| 3 | `DecisionTap` añade `recording()`, la sobrecarga `answerDiscarded(EncounterId, long, QuestionKey, MobKey, DiscardReason)` e `IntentEventKind.CONTINGENCY_STORED` | Sin `recording()` el motor construiría eventos sin grabación; la línea `acceptance` pide pregunta y mob, que un descarte de un solo mob tiene y el de toda la respuesta no; `CONTINGENCY_STORED` es una de las decisiones de la línea `acceptance` y no tenía evento |
| 4 | Una línea `acceptance` toma `encounter` y `sequence` del resultado que se está aceptando: `ResultIntake` llama a `resultReceived` justo antes de entregarlo a la aceptación, en la misma llamada del hilo del servidor, y solo esa aceptación produce eventos con pregunta (contrato en el Javadoc de `DecisionTap`, probado en `DecisionTapCallsTest`) | `IntentEvent` no lleva la clave de unión y el diseño no quiere tabla de espera |
| 5 | El veredicto con pregunta (`ADOPTED`, `RENEWED`, `REJECTED` con la mejor táctica de la respuesta, `CONTINGENCY_STORED`) lo dice la aceptación antes de la coordinación; `IntentControl` solo cuenta lo que ninguna respuesta explica (`CONTINGENCY_FIRED`, `LOCAL_PLANNED` con la causa, `LEVEL_CHANGED` en cambios de nivel y caídas a nivel 4, `ADOPTED` sin pregunta para una alternativa del mismo nivel) | Sin duplicar eventos; una retirada que la coordinación cambia se ve después en `samples.csv` |
| 6 | `FallbackStep` calcula la causa de la caída antes de aplicar el plan local (antes, después) para dársela al tap; mismo resultado | La línea `local` lleva `reason` |
| 7 | `castAttempted` lo llama `IntentControl` (dentro de `TacticalExecution`) una vez por resultado contado (la cuenta de M10.3), no en cada reintento | Sin inundar `casts.csv` con `NOT_ELIGIBLE` repetidos; un cast duplicado se vería como diferencia entre casts del mundo y del tap (invariante del simulador) |
| 8 | `DuelRequest.requester` y `DuelStatus.requests` e `inputTokens` | Avisar al emisor si está conectado; vigilar el costo cada 5 rondas (W7) con los tokens acumulados de la grabación, porque `status` solo da los del último minuto |
| 9 | Un inicio rechazado devuelve `DuelStatus.refused` (`recording` falso y el motivo en `stoppedBy`); `stop` no espera al escritor, `shutdown` sí (2 s, T7); un inicio espera a que el escritor anterior termine | El puerto devuelve un `DuelStatus`; el comando nunca bloquea el hilo del servidor; nunca hay dos hilos escritores |
| 10 | Las muestras corren dentro de la única tarea periódica del motor (`DuelWiring.SampleHook`), no en otra tarea de Bukkit | `ArchitectureRulesTest` exige una sola `runTaskTimer`, en la raíz |
| 11 | Clases además de las listadas: `DuelFile`, `DuelScene`, `DuelistReading`, `LiveDuelScene`, `DuelSession`, `DuelSummary`, `DuelCombat`, `DuelTap`, `DuelDecisionLines`, `IntentTap`, `AcceptanceTap`, `DuelWiring` y, en el simulador, `DuelSkill`, `DuelEngine`, `DuelSessionRun`, `DuelMeasure` | C10 (una responsabilidad por clase) y P4: el registrador se prueba y se alimenta desde el simulador sin Bukkit gracias a `DuelScene` |
| 12 | Una ronda que termina por eliminación (`DuelTimeout` o `/mm mobs kill`) es empate (`winner` `draw`, `end_reason` `removed`); por muerte, gana el otro lado (`death`) | El diseño cuenta el `DUEL timeout` como empate y la primera eliminación no debe dar la ronda a nadie |
| 13 | Una línea `local` lleva `"encounter": null` | No pertenece a ninguna solicitud; `sequence` −1 es su marca |
| 14 | Un `MythicSkillEvent` de un duelista de ThePredict que controla se escribe pero no se cuenta como cast (el motor ya lo cuenta); a nivel 4 sí, porque lo lanzan las reglas | Sin contar dos veces el mismo cast |
| 15 | Acierto: el primer daño del lanzador sobre el rival dentro de 20 ticks confirma el cast pendiente más antiguo de una skill dañina (o del golpe `VANILLA_MELEE`); fallos = casts contados − aciertos | Ventana de 20 ticks de M16.8 y del diseño |
| 16 | Un cliente que lanza una excepción en `decide` deja una línea `planned` `SENT` sin `body` (`CLIENT_ERROR`); una respuesta cancelada tras vencer da una segunda línea `result` (`NETWORK`, `cancelled`) y un `acceptance` `DISCARDED` `STALE_SEQUENCE` | Lo que ocurre en el motor real (M09); `duelJoin` lo muestra en la misma solicitud |
| 17 | El escritor crea al terminar cada archivo que no recibió filas, con su cabecera, y cuenta los bytes de `summary.md` | "Produce todos los archivos" aunque `threat.csv` quede vacío |
| 18 | En el paquete: `shield{amount=6;maxshield=6}` (el diseño escribía `maxShield`), `leap{velocity=100}` (el default, explícito para ajustarlo en 18.2), `lineofsight` como `TargetConditions` como en el ejemplo, `BossBar` como sección, y las reglas llaman a la skill con `@target` (o `@self` para guardia y curación), como `ABClassic` | Alias exactos de javap y el patrón ya probado del A/B |
| 19 | El simulador alterna quién actúa primero en cada tick y cuenta como empate la muerte de los dos en el mismo tick | Sin eso D daba 0,43 [0,363; 0,499] en 200 semillas por el orden fijo; con ello, 0,498 |
| 20 | El oráculo elige la mejor opción con `LocalPlanner.score` (prioridad y `BestWhen` con las condiciones de uso activas) | "Una función fija de propósitos y condiciones activas" (M18.7) |
| 21 | Tamaño de clases (C10, "unas 200 líneas"), líneas totales, de Javadoc y de código: `engine/DecisionDispatcher` 352 (94 / 214; 320 antes), `IntentControl` 298 (75 / 187; 272 antes), `ResultIntake` 246 (66 / 151; 236 antes), `DecisionAcceptor` 217 (25 / 162; 201 antes), `adapter/server/duel/DuelRecorder` 345 (98 / 189; unas 60 líneas son el reenvío de los métodos del tap), `DuelWriter` 286 (73 / 174), `DuelSession` 219, `DuelRounds` 206, `ThePredictPlugin` 228 (210 antes), `admin/ThePredictCommand` 202 (185 antes). Ningún método pasa de 30 líneas | Las piezas nuevas del motor se sacaron a `IntentTap` y `AcceptanceTap`; lo que queda es el cableado que el diseño asigna a cada clase |
| 22 | El subcomando de recarga que citaban M17, su guion, las guías, el registro (DT-39) y el aviso de `ParticipantSelectors` no existe | Corregido en la auditoría: todo dice `/mm reload` |
| 23 | La grabación se pide con radio 30, no con el 24 por defecto | La caja interior de 41 × 41 deja las esquinas a ≈ 29 bloques del centro `0 65 0` (corrección de la auditoría) |
| 24 | `DuelRecorder.damaged` tiene dos sobrecargas (con y sin atacante) y `Notifier` dos métodos (consola y jugador), en lugar de parámetros `Optional` | C4: `Optional` no va como parámetro público |
| 25 | `DuelWriter.create` construye el escritor sin hilo y el registrador llama a `begin()` al final de `start`; `awaitWriter(millis)` espera al escritor tras `stop` (el apagado lo usa con 2 s; las pruebas, con 60 s) | Un inicio que falla no deja un hilo ni bloquea el siguiente; pruebas sin esperas sensibles a la carga |
| 26 | En cada muestra se comprueban los límites antes de muestrear; una ronda en curso al parar se escribe como `unfinished` (`end_reason` `stopped`) en `rounds.csv` y en `summary.md`, fuera de las tasas | Corrección de la auditoría |

### Correcciones tras la auditoría (2026-09-30)

Sin red, sin leer `.env` y sin servidor. Las 81 referencias doradas de M17 no cambiaron (`EntityGeneralizationParityTest` pasa; cambiar las skills y la plantilla del ejemplo del JAR no toca ningún cuerpo ni ningún registro dorado).

| # | Hallazgo | Corrección | Prueba |
|---|---|---|---|
| B1 | `health{h=<N%}` no funciona en 5.13.1: `HealthCondition` compara la vida absoluta y `RangedDouble` lanza `NumberFormatException` con `%` | javap de `HealthPercentCondition`: la forma correcta es `healthpercent{p=<N%}` (con ` true` en las `Conditions` de skills). Cambiado en todo el repositorio: paquete de duelo (skills y tabla de M18.1), skill `ThePredictGuardianDefense` y contexto de `AI/example.yml.example` del JAR, etapas 03, 06, 09 y 10 del paquete, guías, guiones (M01, G13), M07, M15, pruebas y dobles; la afirmación de javap de este documento, corregida; DT-40 (f) en el registro y nota en DT-24 | `UserTestPackTest.noShippedPackOrDocumentedYamlChecksHealthInPercentWithTheAbsoluteHealthCondition` (archivos YAML y bloques de los documentos); `DuelMatrixTest` lee `healthpercent` |
| B2 | Justicia del duelo: el plan local se retiraba con la vida llena, y el golpe vanilla solo lo tenía el clásico | Contexto `healthy` (`healthpercent{p=>50%} true`, prioridad 80, sin `retreat_and_heal`) en los dos perfiles; `Damage: 0` en `DuelBase` y sin `punch`/`press_and_punch`, así que todo el daño sale de las cinco skills; simulador con golpe vanilla de 0 y contextos nativos de `healthpercent`; DT-40 (e) para confirmar en 18.2 que un golpe de 0 no da empuje ni invulnerabilidad; el problema general (un jefe de ThePredict pierde el daño pasivo del golpe vanilla) documentado en la guía del autor (§4.2), en el informe y en el CHANGELOG, y abierto como **DT-42** (descartada por el usuario el 2026-09-30) | `DuelMatrixTest` (daño y vida de `DuelBase`), `UserTestPackTest` (vinculación sin avisos), matriz de `duelMeasure` |
| B3 | Un tap que lanza podía cortar el motor tras reservar presupuesto | `SwitchableDecisionTap` captura `RuntimeException` en cada reenvío, suelta la grabación y avisa una vez: el registrador se detiene con `stopped_by: tap failure`. `requestBody` va después de `track(...)`; `resultReceived` va en su propio `failures.run`, separado de la aceptación | `DecisionTapCallsTest.aTapThatThrowsNeverLeaksBudgetOrRequestsAndLeavesTheServiceAsItWas`, `SwitchableDecisionTapTest.aRecordingThatThrowsIsDetachedAndToldOnceAndTheCallerNeverSeesIt`, `DuelRecorderTest.aRecorderWhoseTapCallThrowsStopsWithTapFailureAndTheEngineGoesOn` |
| B4 | Las líneas de costo del diseño decían `compact` | Con el default `full`: ≈ 0,32 USD con la cota W7 (máx. ≈ 0,48) y ≈ 0,19 USD con tokens reales (máx. ≈ 0,28); techo 0,60 | — |
| B5 | `/thepredict reload` y `/thepredict setup` no existen | `/mm reload` en el aviso de `ParticipantSelectors`, las guías, G13, la guía por sesiones, M17, el registro (DT-39), M15 y M18 | `UserGuidesTest` (las guías no citan esos subcomandos), `ParticipantPolicyTest`, `MythicProfileBinderSuiteTest` |
| — | Asignaciones sin grabación | `IntentTap.dropped` con sobrecargas (el `Optional` se crea solo al grabar), constantes en `FallbackStep` y el descarte de `DecisionAcceptor` dentro de `tap.recording()` | Paridad de M17 |
| — | C4, hilo del escritor, carpeta existente, ronda cortada, límites antes de muestrear, radio, precio en un solo lugar, `DuelJoin` con la última línea cortada, esperas de las pruebas, casillas de M18.4 | Desviaciones 23 a 26; `RequestBudget.USD_PER_MILLION_INPUT_TOKENS` es el único precio (`StatusFormatter` y `summary.md` lo usan); `DuelJoin` salta una última línea incompleta; las pruebas esperan al escritor hasta 60 s; casillas de M18.4 partidas en local y servidor | `DuelRecorderTest.anExistingFolderIsRefusedUpFrontAndAFailedStartLeavesNoThreadBehind`, `aRoundCutByTheStopIsWrittenAsUnfinishedInBothFiles`, `decisionsJsonl…` (línea cortada) |

### Tras el cierre: disponibilidad en tiempo real y DT-42 (decisiones del usuario, 2026-09-30)

- **Disponibilidad en tiempo real (M10, desviación 29; DT-28 revisado):** la choice se rearma, o la táctica local se replanifica, en el primer ciclo reactivo en que está lista (cooldown nativo por `CombatActions.ready`, cooldown extra y `MobFacts.eligibleWhenReady` con el alcance), así que cada skill sale a su propio `Cooldown` en `LOCAL` y con Jev. Los perfiles del duelo no cambiaron; la matriz de arriba es la nueva (C 0,500 y 0,480, D 0,550: simétricos). Build: 1644 pruebas en 222 clases.
- **DT-42 descartada:** el daño cuerpo a cuerpo lo dispara una skill; ThePredict no da golpes vanilla automáticos. El duelo sigue con `Damage: 0` en los dos lados.

### Pendiente

- **Sesión 18 del paquete** (sin clave): 18.1 (seguridad), 18.2 (visual y DT-40), 18.3 (80 rondas locales) y 18.5 (copia de vuelta). **Parte con clave** (18.4, 30 rondas, ≈ 0,19 USD con tokens reales / ≈ 0,32 USD con la cota W7, máximos ≈ 0,28 / ≈ 0,48, techo 0,60) solo con la aprobación explícita del usuario.
- Casillas de servidor: M18.2, M18.3, M18.5, M18.8 y "sin coste medible en `tick_work`" de M18.4 (medido en local sin diferencia; `tick_work` del servidor pendiente).
- DT-40 sigue `Abierta` en (a)–(e); (f) resuelta con javap.
- ~~Decisión del usuario sobre DT-42~~ **Descartada por decisión del usuario (2026-09-30):** el daño cuerpo a cuerpo lo dispara una skill; ThePredict no da golpes vanilla automáticos.
