# Banco de efectividad v2: ThePredict contra la IA nativa y MythicMobs bien configurado

## Veredicto

- **defaults de entrega, sin Jev:** **no cumple** (alguna celda con cota inferior < 0,75). Peor celda: K3-OPEN18-60 contra adversarial, NONE, s = 0.000, cota 0.000 (0 V, 0 E, 400 D).
- **defaults de entrega, con Jev simulado (p = 0,85, latencia medida):** **no cumple** (alguna celda con cota inferior < 0,75). Peor celda: K1-OPEN18-60 contra adversarial, JEV(p=0.85, latencia medida), s = 0.024, cota 0.010 (7 V, 5 E, 388 D). p* = ninguna p medida.
- **defaults de entrega, con Jev simulado (p = 0,85, latencia 0):** **no cumple** (alguna celda con cota inferior < 0,75). Peor celda: K1-OPEN18-60 contra adversarial, JEV(p=0.85, latencia 0), s = 0.019, cota 0.007 (3 V, 9 E, 388 D). p* = ninguna p medida.
- **perfil del paquete, sin Jev:** **no cumple** (alguna celda con cota inferior < 0,75). Peor celda: K3-OPEN18-60 contra adversarial, NONE, s = 0.000, cota 0.000 (0 V, 0 E, 400 D).
- **perfil del paquete, con Jev simulado (p = 0,85, latencia medida):** **no cumple** (alguna celda con cota inferior < 0,75). Peor celda: K1-OPEN18-60 contra adversarial, JEV(p=0.85, latencia medida), s = 0.006, cota 0.001 (2 V, 1 E, 397 D). p* = ninguna p medida.
- **perfil del paquete, con Jev simulado (p = 0,85, latencia 0):** **no cumple** (alguna celda con cota inferior < 0,75). Peor celda: K1-OPEN18-60 contra adversarial, JEV(p=0.85, latencia 0), s = 0.024, cota 0.010 (3 V, 13 E, 384 D). p* = ninguna p medida.

La pregunta del usuario se responde con los **defaults de entrega**: es lo que se entrega. Un veredicto solo vale para este simulador; la evidencia en servidor sigue pendiente.

## Pregunta y criterio (fijados antes de medir)

- **Pregunta.** Sin Jev, ¿gana ThePredict al menos el 75 % contra un zombi vanilla con el mismo equipo, contra el clásico del paquete, contra el clásico con sus mismas ventanas y velocidades (`legal-equal`), contra el mejor clásico legal (`legal-best`) y contra el clásico legal buscado contra él (`adversarial`)?
- **Celda.** Un rival en un escenario: 200 semillas × los dos lados = 400 rondas. Victoria 1, empate ½, derrota 0.
- **Criterio.** En cada celda, la cota inferior de Wilson unilateral al 95 % con Bonferroni sobre las 29 celdas de la configuración es ≥ 0,75, y todas las puertas de validez pasan. Se publica pase o no.

## Método

- Simulador de duelo con el motor real de ThePredict (`DuelEngine`), mundo `VANILLA` y tick `FAIR` (§7.2): los dos lados deciden sobre la misma foto, se mueven a la vez, el orden del commit se sortea por tick y los efectos de un tick caen juntos (dos golpes letales son empate); invulnerabilidad vanilla con exceso, empuje de 0,4 bloques, salto en vuelo de 8 ticks, giro de 90° por tick, rozamiento 0,546 y modificadores cuadráticos (PLAUSIBLE, sin `javap`).
- Puertos del motor como en el servidor: tendencia y alcance del rival, línea de visión con pilares, golpes y muertes como eventos, progreso real y espera de `ImpactTiming`.
- Cada semilla sortea con `StableRandom` el ángulo y la distancia de aparición (simétrica, 8 a 24 bloques), la fase global de los temporizadores, la fase del reloj del motor y la paridad de selección de metas de cada mob. Semillas de evaluación desde 1000001; las búsquedas usan 1 a 10 000 y las puertas desde 2000001.
- ThePredict se mide con dos perfiles generados desde el kit y cargados con el `ProfileLoader` real: **perfil del paquete** (`Timing: TickLayer: casts, Cadence: standard`) y **defaults de entrega** (sin `Timing`). En nivel 4 juega el clásico del paquete de su kit, nunca el del rival.
- Una ronda nueva, con su motor, por cada partida: nada pasa de una ronda a otra. 4 hilos.

## Puertas de validez

Van antes de leer ninguna celda; si una falla, las celdas que dependen de ella no tienen veredicto.

| Puerta | Qué | Umbral | Medido | ¿Pasa? |
|---|---|---|---|---|
| V1 | espejo clásico (legal-equal, K1-OPEN18-60) | |s − 0,5| ≤ 0,05 y el Wilson 95 % contiene 0,5 | s = 0.500, Wilson [0.451; 0.549], 400 rondas | sí |
| V2 | espejo ThePredict (defaults de entrega, K1-OPEN18-60) | |s − 0,5| ≤ 0,05 y el Wilson 95 % contiene 0,5 | s = 0.504, Wilson [0.455; 0.552], 400 rondas | sí |
| V2 | espejo ThePredict (perfil del paquete, K1-OPEN18-60) | |s − 0,5| ≤ 0,05 y el Wilson 95 % contiene 0,5 | s = 0.504, Wilson [0.455; 0.552], 400 rondas | sí |
| V4 | dos golpes letales en el mismo tick | empate, exacto | empate | sí |
| V5 | mismas estadísticas y velocidades a los dos lados | comprobación estática | kit y vida compartidos; acercarse 1.15, retirarse 1.2, banda 1.15 dentro del espacio legal | sí |
| V6 | BASE forzado ≡ clásico (defaults de entrega) | mismo ganador y duración en cada semilla | 0 de 20 semillas distintas | sí |
| V6 | BASE forzado ≡ clásico (perfil del paquete) | mismo ganador y duración en cada semilla | 0 de 20 semillas distintas | sí |
| V3 | lado (defaults de entrega) | McNemar A frente a B: p ≥ 0,05 en el total y por celda con Holm | total p = 0.478 (202 frente a 187 pares discordantes); peor celda p Holm = 1.000 | sí |
| V3 | lado (perfil del paquete) | McNemar A frente a B: p ≥ 0,05 en el total y por celda con Holm | total p = 0.445 (201 frente a 185 pares discordantes); peor celda p Holm = 1.000 | sí |

## Rivales

| Kit | Rival | Contra | Origen | Configuración (MythicMobs) | Huella | Búsqueda |
|---|---|---|---|---|---|---|
| K0 | vanilla | todas | zombi vanilla | ; meleeAttack{speed=1} | `b098c32ac5a5` | — |
| K1 | pack | todas | reglas del paquete | DuelHeal ~onTimer:20 vida<50%; DuelGuard ~onTimer:20 <3 vida<60%; DuelStrike ~onTimer:10 <3; DuelLeap ~onTimer:20 ≥4 <8; DuelBolt ~onTimer:20 ≥4 <14; meleeAttack{speed=1} | `87656fb3aee2` | — |
| K1 | legal-equal | todas | escrito a mano | DuelHeal ~onTimer:1 vida<50%; DuelGuard ~onTimer:1 vida>30%; DuelStrike ~onTimer:1 <3 aura; DuelLeap ~onTimer:1 <8 aura; DuelBolt ~onTimer:1 <14 aura; fleeConditional{distance=8;speed=1.2;mc=[ - healthpercent{p=<50%} true ]}, fleeConditional{distance=5;speed=1.15;mc=[ - skillOnCooldown{s=DuelStrike} true ]}, moveWithinConditional{range=9;speed=1.15;mc=[ - skillOnCooldown{s=DuelStrike} true ]}, meleeAttackConditional{speed=1.15;mc=[ - skillOnCooldown{s=DuelStrike} false ]} | `45374cca0610` | — |
| K1 | legal-best | todas | búsqueda nueva | DuelHeal ~onTimer:1 vida<50%; DuelGuard ~onTimer:1 <3; DuelStrike ~onTimer:1 <3 aura; DuelLeap ~onTimer:1 <8 aura; DuelBolt ~onTimer:1 <14 aura; meleeAttack{speed=1.2} | `3b002ae08b67` | 8.020 (semillas 1-610) |
| K1 | adversarial | defaults de entrega | búsqueda nueva | DuelHeal ~onTimer:1 vida<50%; DuelGuard ~onTimer:1; DuelStrike ~onTimer:1 <3 aura; DuelLeap ~onTimer:1 <8 aura; DuelBolt ~onTimer:1 <14 aura; meleeAttack{speed=1} | `21eecc5a8eb6` | 0.024 (semillas 9001-9200) |
| K1 | adversarial | perfil del paquete | búsqueda nueva | DuelHeal ~onTimer:1 vida<50%; DuelGuard ~onTimer:1; DuelStrike ~onTimer:1 <3 aura; DuelLeap ~onTimer:1 <8 aura; DuelBolt ~onTimer:1 <14 aura; meleeAttack{speed=1} | `21eecc5a8eb6` | 0.024 (semillas 9001-9200) |
| K2 | pack | todas | reglas del paquete | RangedGuard ~onTimer:20 <3 vida<60%; RangedStrike ~onTimer:10 <3; RangedBolt ~onTimer:20 ≥4 <16; meleeAttack{speed=1} | `ada2edc2f91d` | — |
| K2 | legal-equal | todas | escrito a mano | RangedGuard ~onTimer:1 vida>30%; RangedStrike ~onTimer:1 <3 aura; RangedBolt ~onTimer:1 <16 aura; fleeConditional{distance=5;speed=1.15;mc=[ - skillOnCooldown{s=RangedStrike} true ]}, moveWithinConditional{range=9;speed=1.15;mc=[ - skillOnCooldown{s=RangedStrike} true ]}, meleeAttackConditional{speed=1.15;mc=[ - skillOnCooldown{s=RangedStrike} false ]} | `0c48232cfe53` | — |
| K2 | legal-best | todas | búsqueda nueva | RangedGuard ~onTimer:1; RangedStrike ~onTimer:1 <3 aura; RangedBolt ~onTimer:1 <16 aura; fleeConditional{distance=8;speed=1.2;mc=[ - healthpercent{p=<35%} true ]}, fleeConditional{distance=5;speed=1.15;mc=[ - skillOnCooldown{s=RangedStrike} true ]}, moveWithinConditional{range=9;speed=1.15;mc=[ - skillOnCooldown{s=RangedStrike} true ]}, meleeAttackConditional{speed=1.15;mc=[ - skillOnCooldown{s=RangedStrike} false ]} | `00113d722a9f` | 1.583 (semillas 1-610) |
| K2 | adversarial | defaults de entrega | búsqueda nueva | RangedGuard ~onTimer:5; RangedStrike ~onTimer:5 <3 aura; RangedBolt ~onTimer:5 <16 aura; fleeConditional{distance=5;speed=1.15;mc=[ - skillOnCooldown{s=RangedStrike} true ]}, moveWithinConditional{range=9;speed=1.15;mc=[ - skillOnCooldown{s=RangedStrike} true ]}, meleeAttackConditional{speed=1.15;mc=[ - skillOnCooldown{s=RangedStrike} false ]} | `2f7c6c968b7a` | 0.003 (semillas 9001-9200) |
| K2 | adversarial | perfil del paquete | búsqueda nueva | RangedGuard ~onTimer:5; RangedStrike ~onTimer:5 <3 aura; RangedBolt ~onTimer:5 <16 aura; fleeConditional{distance=5;speed=1.15;mc=[ - skillOnCooldown{s=RangedStrike} true ]}, moveWithinConditional{range=9;speed=1.15;mc=[ - skillOnCooldown{s=RangedStrike} true ]}, meleeAttackConditional{speed=1.15;mc=[ - skillOnCooldown{s=RangedStrike} false ]} | `2f7c6c968b7a` | 0.003 (semillas 9001-9200) |
| K3 | pack | todas | reglas del paquete | BurstGuard ~onTimer:20 <3 vida<60%; BurstStrike ~onTimer:10 <3; BurstLeap ~onTimer:20 ≥4 <8; meleeAttack{speed=1} | `0428d3feb22b` | — |
| K3 | legal-equal | todas | escrito a mano | BurstGuard ~onTimer:1 vida>30%; BurstStrike ~onTimer:1 <3 aura; BurstLeap ~onTimer:1 <8 aura; meleeAttack{speed=1.15} | `ccc0515ef699` | — |
| K3 | legal-best | todas | búsqueda nueva | BurstGuard ~onTimer:1 <3; BurstStrike ~onTimer:1 <3 aura; BurstLeap ~onTimer:1 <8 aura; meleeAttack{speed=1.2} | `717e918656cf` | 1.204 (semillas 1-610) |
| K3 | adversarial | defaults de entrega | búsqueda nueva | BurstGuard ~onTimer:1 <3; BurstStrike ~onTimer:1 <3 aura; BurstLeap ~onTimer:1 <8 aura; fleeConditional{distance=8;speed=1.2;mc=[ - healthpercent{p=<50%} true ]}, fleeConditional{distance=5;speed=1.15;mc=[ - skillOnCooldown{s=BurstStrike} true ]}, moveWithinConditional{range=9;speed=1.15;mc=[ - skillOnCooldown{s=BurstStrike} true ]}, meleeAttackConditional{speed=1;mc=[ - skillOnCooldown{s=BurstStrike} false ]} | `ea98c07a2f64` | 0.000 (semillas 9001-9200) |
| K3 | adversarial | perfil del paquete | búsqueda nueva | BurstGuard ~onTimer:1 <3; BurstStrike ~onTimer:1 <3 aura; BurstLeap ~onTimer:1 <8 aura; fleeConditional{distance=8;speed=1.2;mc=[ - healthpercent{p=<50%} true ]}, fleeConditional{distance=5;speed=1.15;mc=[ - skillOnCooldown{s=BurstStrike} true ]}, moveWithinConditional{range=9;speed=1.15;mc=[ - skillOnCooldown{s=BurstStrike} true ]}, meleeAttackConditional{speed=1;mc=[ - skillOnCooldown{s=BurstStrike} false ]} | `ea98c07a2f64` | 0.000 (semillas 9001-9200) |
| K4 | pack | todas | reglas del paquete | SustainHeal ~onTimer:20 vida<60%; SustainGuard ~onTimer:20 <3 vida<60%; SustainStrike ~onTimer:10 <3; meleeAttack{speed=1} | `9cf3d23e8802` | — |
| K4 | legal-equal | todas | escrito a mano | SustainHeal ~onTimer:1 vida<60%; SustainGuard ~onTimer:1 vida>30%; SustainStrike ~onTimer:1 <3 aura; fleeConditional{distance=8;speed=1.2;mc=[ - healthpercent{p=<60%} true ]}, meleeAttack{speed=1.15} | `2f1b3924c9dc` | — |
| K4 | legal-best | todas | búsqueda nueva | SustainHeal ~onTimer:1 vida<30%; SustainGuard ~onTimer:1 <3; SustainStrike ~onTimer:1 <3; meleeAttack{speed=1} | `6487dc8d5331` | 1.000 (semillas 1-610) |
| K4 | adversarial | defaults de entrega | búsqueda nueva | SustainHeal ~onTimer:1 vida<30%; SustainGuard ~onTimer:1 <3; SustainStrike ~onTimer:1 <3; meleeAttack{speed=1} | `02140a328bee` | 0.500 (semillas 9001-9200) |
| K4 | adversarial | perfil del paquete | búsqueda nueva | SustainHeal ~onTimer:1 vida<30%; SustainGuard ~onTimer:1 <3; SustainStrike ~onTimer:1 <3; meleeAttack{speed=1} | `02140a328bee` | 0.500 (semillas 9001-9200) |

Los archivos de MythicMobs de cada rival están en `clasicos/`. Para `legal-best` la búsqueda da su fuerza de Bradley–Terry en la liga final; para `adversarial`, la puntuación de ThePredict contra él en semillas nuevas.

## Celdas: ThePredict con defaults de entrega, sin Jev

29 celdas, z = 2.925 (unilateral, Bonferroni sobre 29). TTK en segundos de las victorias de ThePredict. Δ log TTK: frente al espejo del rival con la misma semilla, media y cota superior (bootstrap unilateral al 95 %).

| Escenario | Rival | Rondas | V / E / D | s | Cota | ¿≥ 0,75? | TTK mediana [Q1; Q3] | Δ log TTK | Daño recibido | Golpes propios perdidos | BASE | McNemar lados |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| K1-OPEN18-60 | pack | 400 | 393 / 0 / 7 | 0.983 | 0.951 | sí | 28.6 [27.7; 29.5] | -0.060 (≤ -0.044) | 1.331 | 6.35 | 0.5 % | 0.375 |
| K1-OPEN18-60 | legal-equal | 400 | 355 / 44 / 1 | 0.943 | 0.898 | sí | 50.7 [43.8; 62.9] | -0.582 (≤ -0.530) | 2.002 | 4.03 | 0.4 % | 1.000 |
| K1-OPEN18-60 | legal-best | 400 | 17 / 0 / 383 | 0.043 | 0.021 | **no** | 18.7 [18.7; 18.7] | +0.178 (≤ +0.235) | 1.440 | 4.43 | 0.8 % | 0.508 |
| K1-OPEN18-60 | adversarial | 400 | 3 / 14 / 383 | 0.025 | 0.010 | **no** | 18.7 [18.6; 24.7] | +0.125 (≤ +0.301) | 1.532 | 4.35 | 0.6 % | 1.000 |
| K1-SMALL8-60 | pack | 400 | 400 / 0 / 0 | 1.000 | 0.979 | sí | 18.2 [18.0; 27.7] | -0.205 (≤ -0.185) | 1.165 | 4.29 | 0.6 % | 1.000 |
| K1-SMALL8-60 | legal-equal | 400 | 400 / 0 / 0 | 1.000 | 0.979 | sí | 38.2 [31.8; 43.8] | -0.755 (≤ -0.735) | 1.616 | 4.16 | 0.3 % | 1.000 |
| K1-SMALL8-60 | legal-best | 400 | 39 / 1 / 360 | 0.099 | 0.063 | **no** | 18.7 [18.7; 18.7] | +0.119 (≤ +0.120) | 1.438 | 2.52 | 0.7 % | 0.200 |
| K1-SMALL8-60 | adversarial | 400 | 226 / 85 / 89 | 0.671 | 0.600 | **no** | 37.9 [37.8; 61.5] | +0.868 (≤ +0.903) | 2.101 | 2.80 | 0.3 % | 0.864 |
| K1-PILLARS18-60 | pack | 400 | 393 / 0 / 7 | 0.983 | 0.951 | sí | 28.6 [27.7; 29.5] | -0.061 (≤ -0.044) | 1.329 | 6.32 | 0.5 % | 0.375 |
| K1-PILLARS18-60 | legal-equal | 400 | 355 / 44 / 1 | 0.943 | 0.898 | sí | 50.1 [43.8; 62.3] | -0.595 (≤ -0.547) | 1.983 | 4.04 | 0.4 % | 0.200 |
| K1-PILLARS18-60 | legal-best | 400 | 20 / 0 / 380 | 0.050 | 0.027 | **no** | 18.7 [18.7; 20.7] | +0.251 (≤ +0.347) | 1.443 | 4.33 | 0.8 % | 0.388 |
| K1-PILLARS18-60 | adversarial | 400 | 4 / 17 / 379 | 0.031 | 0.014 | **no** | 19.0 [18.6; 24.4] | +0.148 (≤ +0.517) | 1.552 | 4.33 | 0.6 % | 0.625 |
| K1-OPEN18-200 | pack | 400 | 400 / 0 / 0 | 1.000 | 0.979 | sí | 129.7 [109.1; 143.5] | +0.278 (≤ +0.304) | 1.446 | 18.50 | 0.2 % | 1.000 |
| K1-OPEN18-200 | legal-equal | 400 | 300 / 100 / 0 | 0.875 | 0.819 | sí | 168.7 [158.6; 180.7] | — | 1.978 | 14.27 | 0.4 % | 0.699 |
| K1-OPEN18-200 | legal-best | 400 | 310 / 89 / 1 | 0.886 | 0.832 | sí | 264.7 [186.7; 277.0] | +1.398 (≤ +1.416) | 2.379 | 11.23 | 1.1 % | 0.734 |
| K1-OPEN18-200 | adversarial | 400 | 16 / 378 / 6 | 0.513 | 0.440 | **no** | 174.7 [174.7; 174.7] | +1.092 (≤ +1.109) | 2.658 | 11.56 | 0.2 % | 1.000 |
| K2-OPEN18-60 | pack | 400 | 400 / 0 / 0 | 1.000 | 0.979 | sí | 21.2 [20.7; 22.0] | -0.527 (≤ -0.523) | 0.706 | 2.65 | 0.6 % | 1.000 |
| K2-OPEN18-60 | legal-equal | 400 | 33 / 14 / 353 | 0.100 | 0.064 | **no** | 25.9 [24.6; 27.1] | +0.153 (≤ +0.178) | 1.257 | 1.07 | 1.2 % | 0.281 |
| K2-OPEN18-60 | legal-best | 400 | 0 / 10 / 390 | 0.013 | 0.004 | **no** | NaN [NaN; NaN] | — | 1.272 | 0.94 | 0.7 % | 1.000 |
| K2-OPEN18-60 | adversarial | 400 | 2 / 2 / 396 | 0.008 | 0.002 | **no** | 25.5 [24.4; 26.6] | +0.034 (≤ +0.107) | 1.247 | 1.03 | 1.2 % | 0.500 |
| K3-OPEN18-60 | pack | 400 | 400 / 0 / 0 | 1.000 | 0.979 | sí | 34.4 [33.6; 35.0] | -0.173 (≤ -0.169) | 1.077 | 0.33 | 0.4 % | 1.000 |
| K3-OPEN18-60 | legal-equal | 400 | 57 / 0 / 343 | 0.143 | 0.099 | **no** | 33.0 [32.9; 33.1] | +0.111 (≤ +0.111) | 1.581 | 0.39 | 0.4 % | 1.000 |
| K3-OPEN18-60 | legal-best | 400 | 58 / 0 / 342 | 0.145 | 0.101 | **no** | 33.0 [32.9; 33.2] | -0.015 (≤ -0.015) | 1.581 | 0.39 | 0.4 % | 0.754 |
| K3-OPEN18-60 | adversarial | 400 | 0 / 0 / 400 | 0.000 | 0.000 | **no** | NaN [NaN; NaN] | — | 1.780 | 0.70 | 1.2 % | 1.000 |
| K4-OPEN18-60 | pack | 400 | 0 / 400 / 0 | 0.500 | 0.428 | **no** | NaN [NaN; NaN] | — | 2.931 | 0.00 | 0.1 % | 1.000 |
| K4-OPEN18-60 | legal-equal | 400 | 0 / 400 / 0 | 0.500 | 0.428 | **no** | NaN [NaN; NaN] | — | 2.898 | 0.00 | 0.1 % | 1.000 |
| K4-OPEN18-60 | legal-best | 400 | 0 / 400 / 0 | 0.500 | 0.428 | **no** | NaN [NaN; NaN] | — | 2.941 | 0.00 | 0.1 % | 1.000 |
| K4-OPEN18-60 | adversarial | 400 | 0 / 400 / 0 | 0.500 | 0.428 | **no** | NaN [NaN; NaN] | — | 2.941 | 0.00 | 0.1 % | 1.000 |
| K0-OPEN18-60 | vanilla | 400 | 400 / 0 / 0 | 1.000 | 0.979 | sí | 21.6 [20.8; 22.3] | -0.032 (≤ -0.031) | 0.950 | 0.00 | 0.6 % | 1.000 |

## Celdas: ThePredict con perfil del paquete, sin Jev

29 celdas, z = 2.925 (unilateral, Bonferroni sobre 29). TTK en segundos de las victorias de ThePredict. Δ log TTK: frente al espejo del rival con la misma semilla, media y cota superior (bootstrap unilateral al 95 %).

| Escenario | Rival | Rondas | V / E / D | s | Cota | ¿≥ 0,75? | TTK mediana [Q1; Q3] | Δ log TTK | Daño recibido | Golpes propios perdidos | BASE | McNemar lados |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| K1-OPEN18-60 | pack | 400 | 393 / 0 / 7 | 0.983 | 0.951 | sí | 28.5 [27.7; 29.5] | -0.060 (≤ -0.044) | 1.331 | 6.35 | 0.5 % | 0.375 |
| K1-OPEN18-60 | legal-equal | 400 | 355 / 43 / 2 | 0.941 | 0.897 | sí | 50.7 [43.8; 63.2] | -0.582 (≤ -0.530) | 2.002 | 4.03 | 0.4 % | 1.000 |
| K1-OPEN18-60 | legal-best | 400 | 17 / 0 / 383 | 0.043 | 0.021 | **no** | 18.7 [18.7; 18.7] | +0.178 (≤ +0.235) | 1.440 | 4.43 | 0.8 % | 0.508 |
| K1-OPEN18-60 | adversarial | 400 | 3 / 14 / 383 | 0.025 | 0.010 | **no** | 18.7 [18.6; 24.7] | +0.125 (≤ +0.301) | 1.532 | 4.35 | 0.6 % | 1.000 |
| K1-SMALL8-60 | pack | 400 | 400 / 0 / 0 | 1.000 | 0.979 | sí | 18.2 [18.0; 27.7] | -0.205 (≤ -0.185) | 1.165 | 4.29 | 0.6 % | 1.000 |
| K1-SMALL8-60 | legal-equal | 400 | 400 / 0 / 0 | 1.000 | 0.979 | sí | 38.2 [31.8; 43.8] | -0.755 (≤ -0.735) | 1.616 | 4.16 | 0.3 % | 1.000 |
| K1-SMALL8-60 | legal-best | 400 | 39 / 1 / 360 | 0.099 | 0.063 | **no** | 18.7 [18.7; 18.7] | +0.119 (≤ +0.120) | 1.438 | 2.52 | 0.7 % | 0.200 |
| K1-SMALL8-60 | adversarial | 400 | 226 / 85 / 89 | 0.671 | 0.600 | **no** | 37.9 [37.8; 61.5] | +0.868 (≤ +0.903) | 2.101 | 2.80 | 0.3 % | 0.864 |
| K1-PILLARS18-60 | pack | 400 | 393 / 0 / 7 | 0.983 | 0.951 | sí | 28.6 [27.7; 29.5] | -0.061 (≤ -0.045) | 1.329 | 6.32 | 0.5 % | 0.375 |
| K1-PILLARS18-60 | legal-equal | 400 | 355 / 43 / 2 | 0.941 | 0.897 | sí | 50.1 [43.8; 62.2] | -0.595 (≤ -0.547) | 1.983 | 4.04 | 0.4 % | 0.200 |
| K1-PILLARS18-60 | legal-best | 400 | 20 / 0 / 380 | 0.050 | 0.027 | **no** | 18.7 [18.7; 20.7] | +0.251 (≤ +0.347) | 1.443 | 4.33 | 0.8 % | 0.388 |
| K1-PILLARS18-60 | adversarial | 400 | 4 / 17 / 379 | 0.031 | 0.014 | **no** | 19.0 [18.6; 24.4] | +0.148 (≤ +0.517) | 1.552 | 4.33 | 0.6 % | 0.625 |
| K1-OPEN18-200 | pack | 400 | 400 / 0 / 0 | 1.000 | 0.979 | sí | 129.7 [109.1; 147.7] | +0.279 (≤ +0.305) | 1.447 | 18.50 | 0.2 % | 1.000 |
| K1-OPEN18-200 | legal-equal | 400 | 301 / 99 / 0 | 0.876 | 0.820 | sí | 168.7 [158.6; 180.6] | — | 1.977 | 14.23 | 0.4 % | 0.795 |
| K1-OPEN18-200 | legal-best | 400 | 312 / 87 / 1 | 0.889 | 0.834 | sí | 261.7 [186.7; 277.0] | +1.399 (≤ +1.417) | 2.377 | 11.23 | 1.1 % | 0.567 |
| K1-OPEN18-200 | adversarial | 400 | 16 / 378 / 6 | 0.513 | 0.440 | **no** | 174.7 [174.7; 174.7] | +1.092 (≤ +1.109) | 2.658 | 11.56 | 0.2 % | 1.000 |
| K2-OPEN18-60 | pack | 400 | 400 / 0 / 0 | 1.000 | 0.979 | sí | 21.2 [20.7; 22.0] | -0.527 (≤ -0.523) | 0.706 | 2.65 | 0.6 % | 1.000 |
| K2-OPEN18-60 | legal-equal | 400 | 33 / 14 / 353 | 0.100 | 0.064 | **no** | 25.9 [24.6; 27.1] | +0.153 (≤ +0.178) | 1.257 | 1.07 | 1.2 % | 0.281 |
| K2-OPEN18-60 | legal-best | 400 | 0 / 10 / 390 | 0.013 | 0.004 | **no** | NaN [NaN; NaN] | — | 1.272 | 0.94 | 0.7 % | 1.000 |
| K2-OPEN18-60 | adversarial | 400 | 2 / 2 / 396 | 0.008 | 0.002 | **no** | 25.5 [24.4; 26.6] | +0.034 (≤ +0.107) | 1.247 | 1.02 | 1.2 % | 0.500 |
| K3-OPEN18-60 | pack | 400 | 400 / 0 / 0 | 1.000 | 0.979 | sí | 34.4 [33.6; 35.0] | -0.173 (≤ -0.169) | 1.077 | 0.33 | 0.4 % | 1.000 |
| K3-OPEN18-60 | legal-equal | 400 | 57 / 0 / 343 | 0.143 | 0.099 | **no** | 33.0 [32.9; 33.1] | +0.111 (≤ +0.111) | 1.581 | 0.39 | 0.4 % | 1.000 |
| K3-OPEN18-60 | legal-best | 400 | 58 / 0 / 342 | 0.145 | 0.101 | **no** | 33.0 [32.9; 33.2] | -0.015 (≤ -0.015) | 1.581 | 0.39 | 0.4 % | 0.754 |
| K3-OPEN18-60 | adversarial | 400 | 0 / 0 / 400 | 0.000 | 0.000 | **no** | NaN [NaN; NaN] | — | 1.780 | 0.70 | 1.2 % | 1.000 |
| K4-OPEN18-60 | pack | 400 | 0 / 400 / 0 | 0.500 | 0.428 | **no** | NaN [NaN; NaN] | — | 2.931 | 0.00 | 0.1 % | 1.000 |
| K4-OPEN18-60 | legal-equal | 400 | 0 / 400 / 0 | 0.500 | 0.428 | **no** | NaN [NaN; NaN] | — | 2.898 | 0.00 | 0.1 % | 1.000 |
| K4-OPEN18-60 | legal-best | 400 | 0 / 400 / 0 | 0.500 | 0.428 | **no** | NaN [NaN; NaN] | — | 2.941 | 0.00 | 0.1 % | 1.000 |
| K4-OPEN18-60 | adversarial | 400 | 0 / 400 / 0 | 0.500 | 0.428 | **no** | NaN [NaN; NaN] | — | 2.941 | 0.00 | 0.1 % | 1.000 |
| K0-OPEN18-60 | vanilla | 400 | 400 / 0 / 0 | 1.000 | 0.979 | sí | 21.6 [20.8; 22.3] | -0.032 (≤ -0.031) | 0.950 | 0.00 | 0.6 % | 1.000 |

## Daño por minuto (descriptivo: no cambia el veredicto)

Por minuto de juego de cada celda: la vida que quitaron los golpes de cada lado antes de la absorción, los golpes con daño que la ventana de invulnerabilidad del otro anuló enteros, y por habilidad los casts y los golpes que hicieron daño (casts → golpes). Con la regla vanilla, un golpe dentro de la ventana que supera al último hace solo el exceso y cuenta como golpe; uno que no lo supera se pierde entero. El golpe vanilla del clásico es su meta, no un cast: solo cuentan sus golpes.

### defaults de entrega

| Escenario | Rival | Daño / min (ThePredict · rival) | Golpes perdidos enteros / min (ThePredict · rival) | ThePredict: casts → golpes / min | Rival: casts → golpes / min |
|---|---|---|---|---|---|
| K1-OPEN18-60 | pack | 188.6 · 173.9 | 13.83 · 0.00 | DuelBolt 20.8 → 7.9; DuelGuard 8.4 → 0.0; DuelHeal 4.1 → 0.0; DuelLeap 9.0 → 8.0; DuelStrike 33.1 → 33.1 | DuelBolt 4.8 → 4.6; DuelGuard 5.3 → 0.0; DuelHeal 4.1 → 0.0; DuelLeap 3.0 → 2.9; DuelStrike 32.4 → 32.0 |
| K1-OPEN18-60 | legal-equal | 125.1 · 124.9 | 4.19 · 0.00 | DuelBolt 20.4 → 18.5; DuelGuard 8.0 → 0.0; DuelHeal 5.1 → 0.0; DuelLeap 6.5 → 4.1; DuelStrike 13.8 → 13.8 | DuelBolt 20.4 → 20.3; DuelGuard 5.8 → 0.0; DuelHeal 4.9 → 0.0; DuelLeap 10.2 → 10.2; DuelStrike 8.7 → 8.7 |
| K1-OPEN18-60 | legal-best | 211.3 · 259.2 | 13.28 · 0.00 | DuelBolt 21.3 → 9.6; DuelGuard 9.0 → 0.0; DuelHeal 3.4 → 0.0; DuelLeap 9.0 → 7.1; DuelStrike 36.7 → 36.5 | DuelBolt 21.3 → 21.3; DuelGuard 9.0 → 0.0; DuelHeal 3.7 → 0.0; DuelLeap 10.8 → 10.3; DuelStrike 35.0 → 35.0 |
| K1-OPEN18-60 | adversarial | 172.2 · 213.3 | 10.08 · 0.00 | DuelBolt 20.5 → 11.6; DuelGuard 8.4 → 0.0; DuelHeal 4.0 → 0.0; DuelLeap 7.0 → 5.8; DuelStrike 27.6 → 27.6 | DuelBolt 20.5 → 20.5; DuelGuard 8.4 → 0.0; DuelHeal 3.7 → 0.0; DuelLeap 11.1 → 11.1; DuelStrike 25.9 → 25.9 |
| K1-SMALL8-60 | pack | 217.3 · 181.3 | 11.12 · 0.00 | DuelBolt 21.2 → 12.2; DuelGuard 9.1 → 0.0; DuelHeal 4.0 → 0.0; DuelLeap 9.9 → 7.2; DuelStrike 36.3 → 36.3 | DuelBolt 4.4 → 4.1; DuelGuard 5.9 → 0.0; DuelHeal 4.0 → 0.0; DuelLeap 2.7 → 2.6; DuelStrike 34.9 → 34.1 |
| K1-SMALL8-60 | legal-equal | 156.3 · 148.9 | 6.38 · 0.00 | DuelBolt 20.9 → 18.1; DuelGuard 8.1 → 0.0; DuelHeal 4.8 → 0.0; DuelLeap 8.1 → 4.4; DuelStrike 20.3 → 20.3 | DuelBolt 20.9 → 20.5; DuelGuard 5.7 → 0.0; DuelHeal 4.7 → 0.0; DuelLeap 10.6 → 10.6; DuelStrike 13.2 → 13.2 |
| K1-SMALL8-60 | legal-best | 240.4 · 258.6 | 7.56 · 0.00 | DuelBolt 21.8 → 18.5; DuelGuard 9.0 → 0.0; DuelHeal 3.9 → 0.0; DuelLeap 8.9 → 4.2; DuelStrike 36.9 → 36.8 | DuelBolt 21.8 → 21.5; DuelGuard 9.0 → 0.0; DuelHeal 5.1 → 0.0; DuelLeap 11.5 → 11.2; DuelStrike 34.4 → 34.4 |
| K1-SMALL8-60 | adversarial | 132.1 · 135.5 | 3.00 · 0.00 | DuelBolt 20.3 → 18.6; DuelGuard 7.9 → 0.0; DuelHeal 5.3 → 0.0; DuelLeap 3.4 → 2.2; DuelStrike 15.4 → 15.3 | DuelBolt 20.3 → 20.3; DuelGuard 7.9 → 0.0; DuelHeal 5.1 → 0.0; DuelLeap 10.6 → 10.6; DuelStrike 10.7 → 10.7 |
| K1-PILLARS18-60 | pack | 188.6 · 173.5 | 13.75 · 0.00 | DuelBolt 20.8 → 8.0; DuelGuard 8.4 → 0.0; DuelHeal 4.1 → 0.0; DuelLeap 9.0 → 8.0; DuelStrike 33.1 → 33.1 | DuelBolt 4.8 → 4.6; DuelGuard 5.3 → 0.0; DuelHeal 4.1 → 0.0; DuelLeap 3.0 → 2.9; DuelStrike 32.4 → 31.9 |
| K1-PILLARS18-60 | legal-equal | 125.9 · 125.2 | 4.25 · 0.00 | DuelBolt 20.4 → 18.5; DuelGuard 8.0 → 0.0; DuelHeal 5.1 → 0.0; DuelLeap 6.6 → 4.2; DuelStrike 14.0 → 13.9 | DuelBolt 20.4 → 20.3; DuelGuard 5.9 → 0.0; DuelHeal 4.9 → 0.0; DuelLeap 10.2 → 10.2; DuelStrike 8.8 → 8.8 |
| K1-PILLARS18-60 | legal-best | 211.3 · 258.2 | 12.92 · 0.00 | DuelBolt 21.3 → 10.1; DuelGuard 9.0 → 0.0; DuelHeal 3.4 → 0.0; DuelLeap 8.9 → 6.9; DuelStrike 36.4 → 36.2 | DuelBolt 21.3 → 21.2; DuelGuard 8.9 → 0.0; DuelHeal 3.7 → 0.0; DuelLeap 10.8 → 10.2; DuelStrike 34.8 → 34.8 |
| K1-PILLARS18-60 | adversarial | 168.6 · 208.5 | 9.70 · 0.00 | DuelBolt 20.5 → 11.9; DuelGuard 8.3 → 0.0; DuelHeal 4.1 → 0.0; DuelLeap 6.7 → 5.6; DuelStrike 26.7 → 26.7 | DuelBolt 20.5 → 20.5; DuelGuard 8.4 → 0.0; DuelHeal 3.8 → 0.0; DuelLeap 11.1 → 11.1; DuelStrike 25.0 → 25.0 |
| K1-OPEN18-200 | pack | 147.6 · 144.0 | 9.21 · 0.00 | DuelBolt 20.3 → 11.6; DuelGuard 7.7 → 0.0; DuelHeal 4.2 → 0.0; DuelLeap 6.1 → 5.6; DuelStrike 23.0 → 23.0 | DuelBolt 9.3 → 9.3; DuelGuard 3.4 → 0.0; DuelHeal 4.6 → 0.0; DuelLeap 4.9 → 4.9; DuelStrike 22.7 → 22.7 |
| K1-OPEN18-200 | legal-equal | 116.8 · 113.9 | 4.11 · 0.00 | DuelBolt 20.1 → 18.1; DuelGuard 7.6 → 0.0; DuelHeal 5.1 → 0.0; DuelLeap 6.5 → 4.4; DuelStrike 12.5 → 12.5 | DuelBolt 20.1 → 20.0; DuelGuard 6.1 → 0.0; DuelHeal 4.8 → 0.0; DuelLeap 10.0 → 10.0; DuelStrike 6.8 → 6.8 |
| K1-OPEN18-200 | legal-best | 94.1 · 114.8 | 2.71 · 0.00 | DuelBolt 20.1 → 17.8; DuelGuard 7.6 → 0.0; DuelHeal 5.4 → 0.0; DuelLeap 1.7 → 1.3; DuelStrike 9.5 → 9.5 | DuelBolt 20.1 → 20.1; DuelGuard 2.7 → 0.0; DuelHeal 5.2 → 0.0; DuelLeap 10.0 → 10.0; DuelStrike 6.9 → 6.9 |
| K1-OPEN18-200 | adversarial | 84.0 · 109.0 | 2.37 · 0.00 | DuelBolt 20.0 → 17.9; DuelGuard 7.6 → 0.0; DuelHeal 5.6 → 0.0; DuelLeap 1.5 → 1.2; DuelStrike 6.1 → 6.1 | DuelBolt 20.0 → 20.0; DuelGuard 7.6 → 0.0; DuelHeal 3.0 → 0.0; DuelLeap 10.0 → 10.0; DuelStrike 5.8 → 5.8 |
| K2-OPEN18-60 | pack | 196.2 · 118.7 | 7.42 · 0.41 | RangedBolt 31.3 → 31.2; RangedGuard 8.4 → 0.0; RangedStrike 34.9 → 26.9 | RangedBolt 4.4 → 4.4; RangedGuard 5.6 → 0.0; RangedStrike 34.5 → 33.8 |
| K2-OPEN18-60 | legal-equal | 153.0 · 173.3 | 2.45 · 0.00 | RangedBolt 31.0 → 30.9; RangedGuard 8.7 → 0.0; RangedStrike 18.6 → 16.1 | RangedBolt 31.0 → 30.8; RangedGuard 6.8 → 0.0; RangedStrike 16.7 → 16.7 |
| K2-OPEN18-60 | legal-best | 150.6 · 169.9 | 2.09 · 0.00 | RangedBolt 31.0 → 30.9; RangedGuard 8.5 → 0.0; RangedStrike 17.0 → 14.9 | RangedBolt 31.0 → 31.0; RangedGuard 8.5 → 0.0; RangedStrike 15.3 → 15.3 |
| K2-OPEN18-60 | adversarial | 148.1 · 175.4 | 2.40 · 0.00 | RangedBolt 30.9 → 30.7; RangedGuard 8.4 → 0.0; RangedStrike 19.2 → 16.7 | RangedBolt 30.8 → 30.7; RangedGuard 8.4 → 0.0; RangedStrike 17.6 → 17.6 |
| K3-OPEN18-60 | pack | 147.8 · 114.3 | 0.57 · 0.00 | BurstGuard 5.3 → 0.0; BurstLeap 10.4 → 9.9; BurstStrike 15.7 → 15.7 | BurstGuard 3.5 → 0.0; BurstLeap 0.5 → 0.5; BurstStrike 15.4 → 14.2 |
| K3-OPEN18-60 | legal-equal | 147.6 · 164.4 | 0.67 · 0.00 | BurstGuard 5.2 → 0.0; BurstLeap 10.4 → 9.7; BurstStrike 15.6 → 15.6 | BurstGuard 5.2 → 0.0; BurstLeap 10.4 → 10.4; BurstStrike 15.4 → 15.4 |
| K3-OPEN18-60 | legal-best | 147.9 · 164.7 | 0.68 · 0.00 | BurstGuard 5.2 → 0.0; BurstLeap 10.4 → 9.7; BurstStrike 15.6 → 15.6 | BurstGuard 5.2 → 0.0; BurstLeap 10.4 → 10.4; BurstStrike 15.4 → 15.4 |
| K3-OPEN18-60 | adversarial | 111.5 · 131.3 | 0.86 · 0.00 | BurstGuard 5.3 → 0.0; BurstLeap 10.7 → 9.6; BurstStrike 11.9 → 11.9 | BurstGuard 5.3 → 0.0; BurstLeap 10.7 → 10.7; BurstStrike 11.1 → 11.1 |
| K4-OPEN18-60 | pack | 117.7 · 117.3 | 0.00 · 0.00 | SustainGuard 10.0 → 0.0; SustainHeal 5.3 → 0.0; SustainStrike 29.5 → 29.4 | SustainGuard 8.1 → 0.0; SustainHeal 6.7 → 0.0; SustainStrike 29.4 → 29.3 |
| K4-OPEN18-60 | legal-equal | 115.9 · 115.9 | 0.00 · 0.00 | SustainGuard 10.0 → 0.0; SustainHeal 5.2 → 0.0; SustainStrike 29.0 → 29.0 | SustainGuard 10.0 → 0.0; SustainHeal 5.2 → 0.0; SustainStrike 29.0 → 29.0 |
| K4-OPEN18-60 | legal-best | 117.7 · 117.7 | 0.00 · 0.00 | SustainGuard 10.0 → 0.0; SustainHeal 5.3 → 0.0; SustainStrike 29.5 → 29.4 | SustainGuard 10.0 → 0.0; SustainHeal 4.0 → 0.0; SustainStrike 29.5 → 29.4 |
| K4-OPEN18-60 | adversarial | 117.7 · 117.7 | 0.00 · 0.00 | SustainGuard 10.0 → 0.0; SustainHeal 5.3 → 0.0; SustainStrike 29.5 → 29.4 | SustainGuard 10.0 → 0.0; SustainHeal 4.0 → 0.0; SustainStrike 29.5 → 29.4 |
| K0-OPEN18-60 | vanilla | 167.0 · 158.6 | 0.00 · 0.00 | vanilla 55.7 → 55.7 | vanilla 0.0 → 52.9 |

### perfil del paquete

| Escenario | Rival | Daño / min (ThePredict · rival) | Golpes perdidos enteros / min (ThePredict · rival) | ThePredict: casts → golpes / min | Rival: casts → golpes / min |
|---|---|---|---|---|---|
| K1-OPEN18-60 | pack | 188.7 · 173.9 | 13.84 · 0.00 | DuelBolt 20.8 → 7.9; DuelGuard 8.4 → 0.0; DuelHeal 4.1 → 0.0; DuelLeap 9.0 → 8.0; DuelStrike 33.1 → 33.1 | DuelBolt 4.8 → 4.6; DuelGuard 5.3 → 0.0; DuelHeal 4.1 → 0.0; DuelLeap 3.0 → 2.9; DuelStrike 32.5 → 32.0 |
| K1-OPEN18-60 | legal-equal | 125.2 · 124.9 | 4.19 · 0.00 | DuelBolt 20.4 → 18.5; DuelGuard 8.0 → 0.0; DuelHeal 5.1 → 0.0; DuelLeap 6.5 → 4.1; DuelStrike 13.8 → 13.8 | DuelBolt 20.4 → 20.3; DuelGuard 5.8 → 0.0; DuelHeal 4.9 → 0.0; DuelLeap 10.2 → 10.2; DuelStrike 8.7 → 8.7 |
| K1-OPEN18-60 | legal-best | 211.3 · 259.2 | 13.28 · 0.00 | DuelBolt 21.3 → 9.6; DuelGuard 9.0 → 0.0; DuelHeal 3.4 → 0.0; DuelLeap 9.0 → 7.1; DuelStrike 36.7 → 36.5 | DuelBolt 21.3 → 21.3; DuelGuard 9.0 → 0.0; DuelHeal 3.7 → 0.0; DuelLeap 10.8 → 10.3; DuelStrike 35.0 → 35.0 |
| K1-OPEN18-60 | adversarial | 172.2 · 213.3 | 10.08 · 0.00 | DuelBolt 20.5 → 11.6; DuelGuard 8.4 → 0.0; DuelHeal 4.0 → 0.0; DuelLeap 7.0 → 5.8; DuelStrike 27.6 → 27.6 | DuelBolt 20.5 → 20.5; DuelGuard 8.4 → 0.0; DuelHeal 3.7 → 0.0; DuelLeap 11.1 → 11.1; DuelStrike 25.9 → 25.9 |
| K1-SMALL8-60 | pack | 217.3 · 181.3 | 11.12 · 0.00 | DuelBolt 21.2 → 12.2; DuelGuard 9.1 → 0.0; DuelHeal 4.0 → 0.0; DuelLeap 9.9 → 7.2; DuelStrike 36.3 → 36.3 | DuelBolt 4.4 → 4.1; DuelGuard 5.9 → 0.0; DuelHeal 4.0 → 0.0; DuelLeap 2.7 → 2.6; DuelStrike 34.9 → 34.1 |
| K1-SMALL8-60 | legal-equal | 156.3 · 148.9 | 6.38 · 0.00 | DuelBolt 20.9 → 18.1; DuelGuard 8.1 → 0.0; DuelHeal 4.8 → 0.0; DuelLeap 8.1 → 4.4; DuelStrike 20.3 → 20.3 | DuelBolt 20.9 → 20.5; DuelGuard 5.7 → 0.0; DuelHeal 4.7 → 0.0; DuelLeap 10.6 → 10.6; DuelStrike 13.2 → 13.2 |
| K1-SMALL8-60 | legal-best | 240.4 · 258.6 | 7.56 · 0.00 | DuelBolt 21.8 → 18.5; DuelGuard 9.0 → 0.0; DuelHeal 3.9 → 0.0; DuelLeap 8.9 → 4.2; DuelStrike 36.9 → 36.8 | DuelBolt 21.8 → 21.5; DuelGuard 9.0 → 0.0; DuelHeal 5.1 → 0.0; DuelLeap 11.5 → 11.2; DuelStrike 34.4 → 34.4 |
| K1-SMALL8-60 | adversarial | 132.1 · 135.5 | 3.00 · 0.00 | DuelBolt 20.3 → 18.6; DuelGuard 7.9 → 0.0; DuelHeal 5.3 → 0.0; DuelLeap 3.4 → 2.2; DuelStrike 15.4 → 15.3 | DuelBolt 20.3 → 20.3; DuelGuard 7.9 → 0.0; DuelHeal 5.1 → 0.0; DuelLeap 10.6 → 10.6; DuelStrike 10.7 → 10.7 |
| K1-PILLARS18-60 | pack | 188.7 · 173.6 | 13.75 · 0.00 | DuelBolt 20.8 → 8.0; DuelGuard 8.4 → 0.0; DuelHeal 4.1 → 0.0; DuelLeap 9.0 → 8.0; DuelStrike 33.1 → 33.1 | DuelBolt 4.8 → 4.6; DuelGuard 5.3 → 0.0; DuelHeal 4.1 → 0.0; DuelLeap 3.0 → 2.9; DuelStrike 32.4 → 31.9 |
| K1-PILLARS18-60 | legal-equal | 125.9 · 125.3 | 4.25 · 0.00 | DuelBolt 20.4 → 18.5; DuelGuard 8.0 → 0.0; DuelHeal 5.1 → 0.0; DuelLeap 6.6 → 4.2; DuelStrike 14.0 → 13.9 | DuelBolt 20.4 → 20.3; DuelGuard 5.9 → 0.0; DuelHeal 4.9 → 0.0; DuelLeap 10.2 → 10.2; DuelStrike 8.8 → 8.8 |
| K1-PILLARS18-60 | legal-best | 211.3 · 258.2 | 12.92 · 0.00 | DuelBolt 21.3 → 10.1; DuelGuard 9.0 → 0.0; DuelHeal 3.4 → 0.0; DuelLeap 8.9 → 6.9; DuelStrike 36.4 → 36.2 | DuelBolt 21.3 → 21.2; DuelGuard 8.9 → 0.0; DuelHeal 3.7 → 0.0; DuelLeap 10.8 → 10.2; DuelStrike 34.8 → 34.8 |
| K1-PILLARS18-60 | adversarial | 168.6 · 208.5 | 9.70 · 0.00 | DuelBolt 20.5 → 11.9; DuelGuard 8.3 → 0.0; DuelHeal 4.1 → 0.0; DuelLeap 6.7 → 5.6; DuelStrike 26.7 → 26.7 | DuelBolt 20.5 → 20.5; DuelGuard 8.4 → 0.0; DuelHeal 3.8 → 0.0; DuelLeap 11.1 → 11.1; DuelStrike 25.0 → 25.0 |
| K1-OPEN18-200 | pack | 147.6 · 144.0 | 9.20 · 0.00 | DuelBolt 20.3 → 11.6; DuelGuard 7.7 → 0.0; DuelHeal 4.2 → 0.0; DuelLeap 6.1 → 5.6; DuelStrike 23.0 → 23.0 | DuelBolt 9.3 → 9.3; DuelGuard 3.4 → 0.0; DuelHeal 4.6 → 0.0; DuelLeap 4.9 → 4.9; DuelStrike 22.7 → 22.6 |
| K1-OPEN18-200 | legal-equal | 116.9 · 113.9 | 4.10 · 0.00 | DuelBolt 20.1 → 18.1; DuelGuard 7.6 → 0.0; DuelHeal 5.1 → 0.0; DuelLeap 6.4 → 4.4; DuelStrike 12.5 → 12.5 | DuelBolt 20.1 → 20.0; DuelGuard 6.1 → 0.0; DuelHeal 4.8 → 0.0; DuelLeap 10.0 → 10.0; DuelStrike 6.8 → 6.8 |
| K1-OPEN18-200 | legal-best | 94.0 · 114.9 | 2.71 · 0.00 | DuelBolt 20.1 → 17.8; DuelGuard 7.6 → 0.0; DuelHeal 5.4 → 0.0; DuelLeap 1.7 → 1.3; DuelStrike 9.5 → 9.5 | DuelBolt 20.1 → 20.1; DuelGuard 2.6 → 0.0; DuelHeal 5.2 → 0.0; DuelLeap 10.0 → 10.0; DuelStrike 6.9 → 6.9 |
| K1-OPEN18-200 | adversarial | 84.0 · 109.0 | 2.37 · 0.00 | DuelBolt 20.0 → 17.9; DuelGuard 7.6 → 0.0; DuelHeal 5.6 → 0.0; DuelLeap 1.5 → 1.2; DuelStrike 6.1 → 6.1 | DuelBolt 20.0 → 20.0; DuelGuard 7.6 → 0.0; DuelHeal 3.0 → 0.0; DuelLeap 10.0 → 10.0; DuelStrike 5.8 → 5.8 |
| K2-OPEN18-60 | pack | 196.2 · 118.7 | 7.42 · 0.41 | RangedBolt 31.3 → 31.2; RangedGuard 8.4 → 0.0; RangedStrike 34.9 → 26.9 | RangedBolt 4.4 → 4.4; RangedGuard 5.6 → 0.0; RangedStrike 34.5 → 33.8 |
| K2-OPEN18-60 | legal-equal | 153.0 · 173.3 | 2.45 · 0.00 | RangedBolt 31.0 → 30.9; RangedGuard 8.7 → 0.0; RangedStrike 18.6 → 16.1 | RangedBolt 31.0 → 30.8; RangedGuard 6.8 → 0.0; RangedStrike 16.7 → 16.7 |
| K2-OPEN18-60 | legal-best | 150.5 · 169.9 | 2.09 · 0.00 | RangedBolt 31.0 → 30.9; RangedGuard 8.5 → 0.0; RangedStrike 17.0 → 14.9 | RangedBolt 31.0 → 31.0; RangedGuard 8.5 → 0.0; RangedStrike 15.3 → 15.3 |
| K2-OPEN18-60 | adversarial | 148.1 · 175.4 | 2.40 · 0.00 | RangedBolt 30.9 → 30.7; RangedGuard 8.4 → 0.0; RangedStrike 19.2 → 16.7 | RangedBolt 30.8 → 30.7; RangedGuard 8.4 → 0.0; RangedStrike 17.6 → 17.6 |
| K3-OPEN18-60 | pack | 147.8 · 114.3 | 0.57 · 0.00 | BurstGuard 5.3 → 0.0; BurstLeap 10.4 → 9.9; BurstStrike 15.7 → 15.7 | BurstGuard 3.5 → 0.0; BurstLeap 0.5 → 0.5; BurstStrike 15.4 → 14.2 |
| K3-OPEN18-60 | legal-equal | 147.6 · 164.4 | 0.67 · 0.00 | BurstGuard 5.2 → 0.0; BurstLeap 10.4 → 9.7; BurstStrike 15.6 → 15.6 | BurstGuard 5.2 → 0.0; BurstLeap 10.4 → 10.4; BurstStrike 15.4 → 15.4 |
| K3-OPEN18-60 | legal-best | 147.9 · 164.7 | 0.68 · 0.00 | BurstGuard 5.2 → 0.0; BurstLeap 10.4 → 9.7; BurstStrike 15.6 → 15.6 | BurstGuard 5.2 → 0.0; BurstLeap 10.4 → 10.4; BurstStrike 15.4 → 15.4 |
| K3-OPEN18-60 | adversarial | 111.5 · 131.3 | 0.86 · 0.00 | BurstGuard 5.3 → 0.0; BurstLeap 10.7 → 9.6; BurstStrike 11.9 → 11.9 | BurstGuard 5.3 → 0.0; BurstLeap 10.7 → 10.7; BurstStrike 11.1 → 11.1 |
| K4-OPEN18-60 | pack | 117.7 · 117.3 | 0.00 · 0.00 | SustainGuard 10.0 → 0.0; SustainHeal 5.3 → 0.0; SustainStrike 29.5 → 29.4 | SustainGuard 8.1 → 0.0; SustainHeal 6.7 → 0.0; SustainStrike 29.4 → 29.3 |
| K4-OPEN18-60 | legal-equal | 115.9 · 115.9 | 0.00 · 0.00 | SustainGuard 10.0 → 0.0; SustainHeal 5.2 → 0.0; SustainStrike 29.0 → 29.0 | SustainGuard 10.0 → 0.0; SustainHeal 5.2 → 0.0; SustainStrike 29.0 → 29.0 |
| K4-OPEN18-60 | legal-best | 117.7 · 117.7 | 0.00 · 0.00 | SustainGuard 10.0 → 0.0; SustainHeal 5.3 → 0.0; SustainStrike 29.5 → 29.4 | SustainGuard 10.0 → 0.0; SustainHeal 4.0 → 0.0; SustainStrike 29.5 → 29.4 |
| K4-OPEN18-60 | adversarial | 117.7 · 117.7 | 0.00 · 0.00 | SustainGuard 10.0 → 0.0; SustainHeal 5.3 → 0.0; SustainStrike 29.5 → 29.4 | SustainGuard 10.0 → 0.0; SustainHeal 4.0 → 0.0; SustainStrike 29.5 → 29.4 |
| K0-OPEN18-60 | vanilla | 167.0 · 158.6 | 0.00 · 0.00 | vanilla 55.7 → 55.7 | vanilla 0.0 → 52.9 |

## Conducta de ThePredict (descriptiva: no cambia el veredicto)

Por celda: reacciones de M20.4 armadas por minuto (`DecisionTap.reactionFired`); parte de los ticks con control en retirada y en banda, y con el rival al alcance cuerpo a cuerpo; casts que lo desplazan por minuto; guardias por minuto, las que absorbieron un golpe del rival y las que lo hicieron en menos de 20 ticks (puestas antes del impacto), y la absorción por ronda; parte del tiempo sin línea de visión y ticks medios hasta recuperarla (solo `PILLARS18` tiene pilares); parte de sus golpes que cayeron mientras el golpe cuerpo a cuerpo del rival recargaba; y la previsión del modelo de patrones del rival (`EnemyModel.forecast`, modo `OBSERVE` por defecto): cobertura, acierto sobre los golpes del rival y precisión propia del modelo. Es un diagnóstico: en `OBSERVE` el motor no la usa para decidir y no llega a `IntentView.forecast`, que queda en −1 también en el servidor. Contra una entidad el paso de patrones solo ve `STRIKE` cuando un golpe del rival cae; no hay lectura de teclas ni de carga, que son de jugador.

### defaults de entrega

| Escenario | Rival | Reacciones / min | Retirada | Banda | Cuerpo a cuerpo | Desplazamientos / min | Guardias / min | Absorben (< 20 ticks) | Absorción / ronda | Sin visión (ticks hasta verla) | Castigos en recarga | Previsión: cobertura, acierto, modelo |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| K1-OPEN18-60 | pack | EVADE_CRITICAL 6.34; FINISH 22.55 | 16.4 % | 20.0 % | 78.5 % | 8.99 | 8.44 | 99.6 % (55.6 %) | 21.8 | 0.0 % (—) | 82.3 % | 52.2 %, 49.2 %, 70.9 % |
| K1-OPEN18-60 | legal-equal | EVADE_CRITICAL 5.82; FINISH 19.60 | 41.7 % | 15.0 % | 17.4 % | 6.49 | 8.04 | 97.7 % (52.3 %) | 44.3 | 0.0 % (—) | 25.2 % | 63.4 %, 46.5 %, 78.2 % |
| K1-OPEN18-60 | legal-best | EVADE_CRITICAL 10.09; FINISH 7.42 | 19.3 % | 19.5 % | 86.6 % | 8.99 | 9.02 | 100.0 % (70.7 %) | 18.0 | 0.0 % (—) | 81.5 % | 22.7 %, 21.4 %, 48.4 % |
| K1-OPEN18-60 | adversarial | EVADE_CRITICAL 10.01; FINISH 7.94 | 33.0 % | 19.5 % | 62.6 % | 6.97 | 8.37 | 99.0 % (66.6 %) | 21.4 | 0.0 % (—) | 73.7 % | 40.8 %, 30.7 %, 51.2 % |
| K1-SMALL8-60 | pack | EVADE_CRITICAL 4.07; FINISH 21.86 | 9.4 % | 27.7 % | 84.8 % | 9.87 | 9.12 | 98.9 % (66.1 %) | 19.8 | 0.0 % (—) | 86.1 % | 45.1 %, 39.3 %, 64.4 % |
| K1-SMALL8-60 | legal-equal | EVADE_CRITICAL 4.91; FINISH 22.98 | 27.2 % | 25.2 % | 27.7 % | 8.06 | 8.09 | 98.5 % (51.1 %) | 30.7 | 0.0 % (—) | 36.2 % | 48.4 %, 32.8 %, 62.2 % |
| K1-SMALL8-60 | legal-best | EVADE_CRITICAL 9.92; FINISH 18.98 | 19.5 % | 30.6 % | 86.1 % | 8.90 | 8.99 | 100.0 % (77.8 %) | 18.0 | 0.0 % (—) | 82.4 % | 23.0 %, 19.5 %, 47.6 % |
| K1-SMALL8-60 | adversarial | EVADE_CRITICAL 8.93; FINISH 24.15 | 50.2 % | 31.0 % | 27.2 % | 3.43 | 7.85 | 97.1 % (51.5 %) | 42.6 | 0.0 % (—) | 41.6 % | 73.2 %, 54.2 %, 72.9 % |
| K1-PILLARS18-60 | pack | EVADE_CRITICAL 6.23; FINISH 22.56 | 16.3 % | 20.1 % | 78.3 % | 8.99 | 8.44 | 99.5 % (55.5 %) | 21.9 | 0.2 % (30.6) | 82.4 % | 52.1 %, 49.1 %, 70.8 % |
| K1-PILLARS18-60 | legal-equal | EVADE_CRITICAL 5.75; FINISH 19.56 | 41.0 % | 15.2 % | 17.6 % | 6.56 | 8.04 | 97.8 % (52.1 %) | 43.8 | 0.1 % (21.8) | 25.5 % | 63.5 %, 46.5 %, 77.7 % |
| K1-PILLARS18-60 | legal-best | EVADE_CRITICAL 9.87; FINISH 8.13 | 19.2 % | 20.2 % | 86.0 % | 8.93 | 9.00 | 100.0 % (70.8 %) | 18.1 | 0.3 % (23.4) | 81.4 % | 23.0 %, 21.5 %, 48.5 % |
| K1-PILLARS18-60 | adversarial | EVADE_CRITICAL 9.95; FINISH 8.34 | 34.4 % | 19.8 % | 60.5 % | 6.73 | 8.31 | 99.0 % (66.1 %) | 22.1 | 0.3 % (30.6) | 72.5 % | 42.8 %, 31.9 %, 52.1 % |
| K1-OPEN18-200 | pack | EVADE_CRITICAL 5.83; FINISH 34.49 | 37.5 % | 19.5 % | 54.5 % | 6.15 | 7.67 | 99.5 % (55.1 %) | 91.4 | 0.0 % (—) | 74.1 % | 89.1 %, 79.3 %, 81.2 % |
| K1-OPEN18-200 | legal-equal | EVADE_CRITICAL 4.37; FINISH 12.97 | 35.6 % | 17.1 % | 14.1 % | 6.45 | 7.62 | 99.7 % (53.1 %) | 157.3 | 0.0 % (—) | 20.8 % | 91.0 %, 72.3 %, 85.8 % |
| K1-OPEN18-200 | legal-best | EVADE_CRITICAL 10.41; FINISH 34.96 | 65.7 % | 20.2 % | 19.0 % | 1.70 | 7.61 | 100.0 % (41.7 %) | 187.7 | 0.0 % (—) | 32.0 % | 93.8 %, 88.6 %, 92.0 % |
| K1-OPEN18-200 | adversarial | EVADE_CRITICAL 8.93; FINISH 1.46 | 66.0 % | 22.0 % | 14.2 % | 1.48 | 7.60 | 100.0 % (40.1 %) | 219.6 | 0.0 % (—) | 29.7 % | 94.8 %, 90.1 %, 96.6 % |
| K2-OPEN18-60 | pack | FINISH 19.74 | 0.0 % | 34.5 % | 83.9 % | 0.00 | 8.42 | 99.9 % (66.8 %) | 12.0 | 0.0 % (—) | 89.5 % | 41.3 %, 39.0 %, 66.8 % |
| K2-OPEN18-60 | legal-equal | EVADE_CRITICAL 2.78; FINISH 14.58 | 0.0 % | 53.6 % | 20.5 % | 0.00 | 8.65 | 99.7 % (99.7 %) | 15.0 | 0.0 % (—) | 57.3 % | 47.1 %, 39.3 %, 53.4 % |
| K2-OPEN18-60 | legal-best | EVADE_CRITICAL 1.27; FINISH 15.44 | 0.0 % | 55.3 % | 18.8 % | 0.00 | 8.46 | 100.0 % (100.0 %) | 15.2 | 0.0 % (—) | 54.8 % | 49.0 %, 40.2 %, 53.0 % |
| K2-OPEN18-60 | adversarial | EVADE_CRITICAL 2.99; FINISH 11.94 | 0.0 % | 51.5 % | 23.9 % | 0.00 | 8.40 | 100.0 % (99.1 %) | 14.3 | 0.0 % (—) | 63.2 % | 44.0 %, 37.4 %, 53.3 % |
| K3-OPEN18-60 | pack | FINISH 15.44 | 0.0 % | 0.0 % | 94.5 % | 10.43 | 5.31 | 100.0 % (5.0 %) | 29.8 | 0.0 % (—) | 94.4 % | 9.9 %, 1.5 %, 44.5 % |
| K3-OPEN18-60 | legal-equal | EVADE_CRITICAL 9.19; FINISH 9.41 | 0.0 % | 0.0 % | 94.3 % | 10.40 | 5.20 | 100.0 % (21.3 %) | 30.0 | 0.0 % (—) | 86.3 % | 34.5 %, 26.0 %, 57.3 % |
| K3-OPEN18-60 | legal-best | EVADE_CRITICAL 9.23; FINISH 9.44 | 0.0 % | 0.0 % | 94.4 % | 10.42 | 5.21 | 100.0 % (22.5 %) | 30.0 | 0.0 % (—) | 86.3 % | 34.6 %, 26.0 %, 57.3 % |
| K3-OPEN18-60 | adversarial | EVADE_CRITICAL 4.26; FINISH 8.47 | 0.0 % | 0.0 % | 35.7 % | 10.69 | 5.35 | 100.0 % (20.6 %) | 43.5 | 0.0 % (—) | 55.7 % | 40.1 %, 23.5 %, 54.4 % |
| K4-OPEN18-60 | pack | ninguna | 0.0 % | 0.0 % | 97.3 % | 0.00 | 10.00 | 100.0 % (44.7 %) | 89.8 | 0.0 % (—) | 99.1 % | 81.2 %, 81.8 %, 98.6 % |
| K4-OPEN18-60 | legal-equal | ninguna | 0.0 % | 0.0 % | 94.8 % | 0.00 | 10.00 | 100.0 % (46.5 %) | 90.0 | 0.0 % (—) | 100.0 % | 81.8 %, 81.6 %, 98.5 % |
| K4-OPEN18-60 | legal-best | FINISH 12.14 | 0.0 % | 0.0 % | 97.3 % | 0.00 | 10.00 | 100.0 % (36.5 %) | 89.9 | 0.0 % (—) | 100.0 % | 81.4 %, 81.9 %, 98.6 % |
| K4-OPEN18-60 | adversarial | FINISH 12.14 | 0.0 % | 0.0 % | 97.3 % | 0.00 | 10.00 | 100.0 % (36.5 %) | 89.9 | 0.0 % (—) | 100.0 % | 81.4 %, 81.9 %, 98.6 % |
| K0-OPEN18-60 | vanilla | EVADE_CRITICAL 8.35; FINISH 16.70 | 0.0 % | 0.0 % | 88.7 % | 0.00 | 0.00 | 0.0 % (0.0 %) | 0.0 | 0.0 % (—) | 95.0 % | 25.6 %, 26.3 %, 53.7 % |

### perfil del paquete

| Escenario | Rival | Reacciones / min | Retirada | Banda | Cuerpo a cuerpo | Desplazamientos / min | Guardias / min | Absorben (< 20 ticks) | Absorción / ronda | Sin visión (ticks hasta verla) | Castigos en recarga | Previsión: cobertura, acierto, modelo |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| K1-OPEN18-60 | pack | ninguna | 16.4 % | 20.0 % | 78.5 % | 9.00 | 8.45 | 99.6 % (55.6 %) | 21.8 | 0.0 % (—) | 82.3 % | 52.2 %, 49.2 %, 70.9 % |
| K1-OPEN18-60 | legal-equal | ninguna | 41.7 % | 15.0 % | 17.4 % | 6.49 | 8.04 | 97.7 % (52.1 %) | 44.3 | 0.0 % (—) | 25.2 % | 63.3 %, 46.4 %, 78.1 % |
| K1-OPEN18-60 | legal-best | ninguna | 19.3 % | 19.5 % | 86.6 % | 8.99 | 9.02 | 100.0 % (70.7 %) | 18.0 | 0.0 % (—) | 81.5 % | 22.7 %, 21.4 %, 48.4 % |
| K1-OPEN18-60 | adversarial | ninguna | 33.0 % | 19.5 % | 62.6 % | 6.97 | 8.37 | 99.0 % (66.6 %) | 21.4 | 0.0 % (—) | 73.7 % | 40.8 %, 30.7 %, 51.2 % |
| K1-SMALL8-60 | pack | ninguna | 9.4 % | 27.7 % | 84.8 % | 9.87 | 9.12 | 98.9 % (66.1 %) | 19.8 | 0.0 % (—) | 86.1 % | 45.1 %, 39.3 %, 64.4 % |
| K1-SMALL8-60 | legal-equal | ninguna | 27.2 % | 25.2 % | 27.7 % | 8.06 | 8.09 | 98.4 % (51.0 %) | 30.7 | 0.0 % (—) | 36.2 % | 48.4 %, 32.8 %, 62.2 % |
| K1-SMALL8-60 | legal-best | ninguna | 19.5 % | 30.6 % | 86.1 % | 8.90 | 8.99 | 100.0 % (77.8 %) | 18.0 | 0.0 % (—) | 82.4 % | 23.0 %, 19.5 %, 47.6 % |
| K1-SMALL8-60 | adversarial | ninguna | 50.2 % | 31.0 % | 27.2 % | 3.43 | 7.85 | 97.1 % (51.5 %) | 42.6 | 0.0 % (—) | 41.6 % | 73.2 %, 54.2 %, 72.9 % |
| K1-PILLARS18-60 | pack | ninguna | 16.4 % | 20.0 % | 78.4 % | 9.00 | 8.44 | 99.5 % (55.5 %) | 21.9 | 0.2 % (30.6) | 82.4 % | 52.1 %, 49.1 %, 70.8 % |
| K1-PILLARS18-60 | legal-equal | ninguna | 41.0 % | 15.2 % | 17.6 % | 6.57 | 8.04 | 97.8 % (52.0 %) | 43.8 | 0.1 % (21.8) | 25.5 % | 63.4 %, 46.4 %, 77.6 % |
| K1-PILLARS18-60 | legal-best | ninguna | 19.2 % | 20.2 % | 86.0 % | 8.93 | 9.00 | 100.0 % (70.8 %) | 18.1 | 0.3 % (23.4) | 81.4 % | 23.0 %, 21.5 %, 48.5 % |
| K1-PILLARS18-60 | adversarial | ninguna | 34.4 % | 19.8 % | 60.5 % | 6.73 | 8.31 | 99.0 % (66.1 %) | 22.1 | 0.3 % (30.6) | 72.5 % | 42.8 %, 31.9 %, 52.1 % |
| K1-OPEN18-200 | pack | ninguna | 37.5 % | 19.5 % | 54.5 % | 6.14 | 7.67 | 99.5 % (55.1 %) | 91.4 | 0.0 % (—) | 74.1 % | 89.1 %, 79.3 %, 81.2 % |
| K1-OPEN18-200 | legal-equal | ninguna | 35.7 % | 17.0 % | 14.1 % | 6.45 | 7.62 | 99.7 % (53.1 %) | 157.3 | 0.0 % (—) | 20.8 % | 91.0 %, 72.3 %, 85.8 % |
| K1-OPEN18-200 | legal-best | ninguna | 65.5 % | 20.3 % | 19.0 % | 1.70 | 7.61 | 100.0 % (41.7 %) | 187.6 | 0.0 % (—) | 32.1 % | 93.8 %, 88.6 %, 92.0 % |
| K1-OPEN18-200 | adversarial | ninguna | 66.0 % | 22.0 % | 14.2 % | 1.48 | 7.60 | 100.0 % (40.1 %) | 219.6 | 0.0 % (—) | 29.7 % | 94.8 %, 90.1 %, 96.6 % |
| K2-OPEN18-60 | pack | ninguna | 0.0 % | 34.5 % | 83.9 % | 0.00 | 8.42 | 99.9 % (66.8 %) | 12.0 | 0.0 % (—) | 89.5 % | 41.3 %, 39.0 %, 66.8 % |
| K2-OPEN18-60 | legal-equal | ninguna | 0.0 % | 53.6 % | 20.5 % | 0.00 | 8.65 | 99.7 % (99.7 %) | 15.0 | 0.0 % (—) | 57.3 % | 47.1 %, 39.3 %, 53.4 % |
| K2-OPEN18-60 | legal-best | ninguna | 0.0 % | 55.3 % | 18.8 % | 0.00 | 8.46 | 100.0 % (100.0 %) | 15.2 | 0.0 % (—) | 54.8 % | 49.0 %, 40.2 %, 53.0 % |
| K2-OPEN18-60 | adversarial | ninguna | 0.0 % | 51.5 % | 23.9 % | 0.00 | 8.40 | 100.0 % (99.2 %) | 14.3 | 0.0 % (—) | 63.2 % | 44.0 %, 37.4 %, 53.3 % |
| K3-OPEN18-60 | pack | ninguna | 0.0 % | 0.0 % | 94.5 % | 10.43 | 5.31 | 100.0 % (5.0 %) | 29.8 | 0.0 % (—) | 94.4 % | 9.9 %, 1.5 %, 44.5 % |
| K3-OPEN18-60 | legal-equal | ninguna | 0.0 % | 0.0 % | 94.3 % | 10.40 | 5.20 | 100.0 % (21.3 %) | 30.0 | 0.0 % (—) | 86.3 % | 34.5 %, 26.0 %, 57.3 % |
| K3-OPEN18-60 | legal-best | ninguna | 0.0 % | 0.0 % | 94.4 % | 10.42 | 5.21 | 100.0 % (22.5 %) | 30.0 | 0.0 % (—) | 86.3 % | 34.6 %, 26.0 %, 57.3 % |
| K3-OPEN18-60 | adversarial | ninguna | 0.0 % | 0.0 % | 35.7 % | 10.69 | 5.35 | 100.0 % (20.6 %) | 43.5 | 0.0 % (—) | 55.7 % | 40.1 %, 23.5 %, 54.4 % |
| K4-OPEN18-60 | pack | ninguna | 0.0 % | 0.0 % | 97.3 % | 0.00 | 10.00 | 100.0 % (44.7 %) | 89.8 | 0.0 % (—) | 99.1 % | 81.2 %, 81.8 %, 98.6 % |
| K4-OPEN18-60 | legal-equal | ninguna | 0.0 % | 0.0 % | 94.8 % | 0.00 | 10.00 | 100.0 % (46.5 %) | 90.0 | 0.0 % (—) | 100.0 % | 81.8 %, 81.6 %, 98.5 % |
| K4-OPEN18-60 | legal-best | ninguna | 0.0 % | 0.0 % | 97.3 % | 0.00 | 10.00 | 100.0 % (36.5 %) | 89.9 | 0.0 % (—) | 100.0 % | 81.4 %, 81.9 %, 98.6 % |
| K4-OPEN18-60 | adversarial | ninguna | 0.0 % | 0.0 % | 97.3 % | 0.00 | 10.00 | 100.0 % (36.5 %) | 89.9 | 0.0 % (—) | 100.0 % | 81.4 %, 81.9 %, 98.6 % |
| K0-OPEN18-60 | vanilla | ninguna | 0.0 % | 0.0 % | 88.7 % | 0.00 | 0.00 | 0.0 % (0.0 %) | 0.0 | 0.0 % (—) | 95.0 % | 25.6 %, 26.3 %, 53.7 % |

## Sensibilidad a Jev

Juez: `LookaheadOracle` (40 ticks contra el rival congelado), nunca la puntuación del plan local. Con probabilidad p elige la mejor opción y, si no, otra al azar; p = 1/k es el azar uniforme. Latencia medida: lognormal 179/228 ms con 1 % entre 330 y 800 ms y 0,5 % sin respuesta (caduca y decide `LOCAL`); latencia 0 es el techo.

### defaults de entrega

24 celdas, z = 2.865. p* con latencia medida: ninguna p medida; con latencia 0: ninguna p medida.

| Rival | Modo | Rondas | V / E / D | s(p) | Cota | ¿≥ 0,75? | s(p) − s(NONE) | Peticiones por ronda |
|---|---|---|---|---|---|---|---|---|
| legal-best | JEV(p=1/k, latencia medida) | 400 | 24 / 0 / 376 | 0.060 | 0.034 | **no** | +0.017 | 2.0 |
| legal-best | JEV(p=0.40, latencia medida) | 400 | 22 / 0 / 378 | 0.055 | 0.030 | **no** | +0.012 | 2.0 |
| legal-best | JEV(p=0.55, latencia medida) | 400 | 20 / 0 / 380 | 0.050 | 0.027 | **no** | +0.008 | 2.0 |
| legal-best | JEV(p=0.70, latencia medida) | 400 | 21 / 0 / 379 | 0.053 | 0.029 | **no** | +0.010 | 1.9 |
| legal-best | JEV(p=0.85, latencia medida) | 400 | 19 / 0 / 381 | 0.048 | 0.025 | **no** | +0.005 | 2.0 |
| legal-best | JEV(p=1.00, latencia medida) | 400 | 19 / 0 / 381 | 0.048 | 0.025 | **no** | +0.005 | 2.0 |
| legal-best | JEV(p=1/k, latencia 0) | 400 | 18 / 0 / 382 | 0.045 | 0.023 | **no** | +0.002 | 1.9 |
| legal-best | JEV(p=0.40, latencia 0) | 400 | 16 / 0 / 384 | 0.040 | 0.020 | **no** | -0.003 | 1.9 |
| legal-best | JEV(p=0.55, latencia 0) | 400 | 15 / 0 / 385 | 0.038 | 0.018 | **no** | -0.005 | 1.9 |
| legal-best | JEV(p=0.70, latencia 0) | 400 | 16 / 0 / 384 | 0.040 | 0.020 | **no** | -0.003 | 1.9 |
| legal-best | JEV(p=0.85, latencia 0) | 400 | 12 / 0 / 388 | 0.030 | 0.013 | **no** | -0.013 | 1.9 |
| legal-best | JEV(p=1.00, latencia 0) | 400 | 10 / 0 / 390 | 0.025 | 0.010 | **no** | -0.018 | 1.9 |
| adversarial | JEV(p=1/k, latencia medida) | 400 | 20 / 6 / 374 | 0.058 | 0.032 | **no** | +0.033 | 2.2 |
| adversarial | JEV(p=0.40, latencia medida) | 400 | 16 / 8 / 376 | 0.050 | 0.027 | **no** | +0.025 | 2.2 |
| adversarial | JEV(p=0.55, latencia medida) | 400 | 12 / 6 / 382 | 0.038 | 0.018 | **no** | +0.012 | 2.2 |
| adversarial | JEV(p=0.70, latencia medida) | 400 | 9 / 5 / 386 | 0.029 | 0.013 | **no** | +0.004 | 2.2 |
| adversarial | JEV(p=0.85, latencia medida) | 400 | 7 / 5 / 388 | 0.024 | 0.010 | **no** | -0.001 | 2.3 |
| adversarial | JEV(p=1.00, latencia medida) | 400 | 6 / 2 / 392 | 0.018 | 0.006 | **no** | -0.008 | 2.3 |
| adversarial | JEV(p=1/k, latencia 0) | 400 | 9 / 12 / 379 | 0.038 | 0.018 | **no** | +0.012 | 2.2 |
| adversarial | JEV(p=0.40, latencia 0) | 400 | 10 / 12 / 378 | 0.040 | 0.020 | **no** | +0.015 | 2.2 |
| adversarial | JEV(p=0.55, latencia 0) | 400 | 8 / 10 / 382 | 0.033 | 0.015 | **no** | +0.008 | 2.3 |
| adversarial | JEV(p=0.70, latencia 0) | 400 | 5 / 9 / 386 | 0.024 | 0.010 | **no** | -0.001 | 2.3 |
| adversarial | JEV(p=0.85, latencia 0) | 400 | 3 / 9 / 388 | 0.019 | 0.007 | **no** | -0.006 | 2.3 |
| adversarial | JEV(p=1.00, latencia 0) | 400 | 4 / 8 / 388 | 0.020 | 0.008 | **no** | -0.005 | 2.3 |

### perfil del paquete

24 celdas, z = 2.865. p* con latencia medida: ninguna p medida; con latencia 0: ninguna p medida.

| Rival | Modo | Rondas | V / E / D | s(p) | Cota | ¿≥ 0,75? | s(p) − s(NONE) | Peticiones por ronda |
|---|---|---|---|---|---|---|---|---|
| legal-best | JEV(p=1/k, latencia medida) | 400 | 19 / 1 / 380 | 0.049 | 0.026 | **no** | +0.006 | 2.0 |
| legal-best | JEV(p=0.40, latencia medida) | 400 | 19 / 1 / 380 | 0.049 | 0.026 | **no** | +0.006 | 2.0 |
| legal-best | JEV(p=0.55, latencia medida) | 400 | 23 / 1 / 376 | 0.059 | 0.033 | **no** | +0.016 | 2.0 |
| legal-best | JEV(p=0.70, latencia medida) | 400 | 25 / 1 / 374 | 0.064 | 0.037 | **no** | +0.021 | 2.0 |
| legal-best | JEV(p=0.85, latencia medida) | 400 | 28 / 1 / 371 | 0.071 | 0.042 | **no** | +0.029 | 2.0 |
| legal-best | JEV(p=1.00, latencia medida) | 400 | 30 / 0 / 370 | 0.075 | 0.045 | **no** | +0.032 | 2.0 |
| legal-best | JEV(p=1/k, latencia 0) | 400 | 17 / 0 / 383 | 0.043 | 0.022 | **no** | +0.000 | 2.0 |
| legal-best | JEV(p=0.40, latencia 0) | 400 | 19 / 0 / 381 | 0.048 | 0.025 | **no** | +0.005 | 2.0 |
| legal-best | JEV(p=0.55, latencia 0) | 400 | 16 / 0 / 384 | 0.040 | 0.020 | **no** | -0.003 | 2.0 |
| legal-best | JEV(p=0.70, latencia 0) | 400 | 15 / 0 / 385 | 0.038 | 0.018 | **no** | -0.005 | 1.9 |
| legal-best | JEV(p=0.85, latencia 0) | 400 | 13 / 1 / 386 | 0.034 | 0.016 | **no** | -0.009 | 2.0 |
| legal-best | JEV(p=1.00, latencia 0) | 400 | 10 / 1 / 389 | 0.026 | 0.011 | **no** | -0.016 | 2.0 |
| adversarial | JEV(p=1/k, latencia medida) | 400 | 16 / 0 / 384 | 0.040 | 0.020 | **no** | +0.015 | 2.2 |
| adversarial | JEV(p=0.40, latencia medida) | 400 | 13 / 0 / 387 | 0.033 | 0.015 | **no** | +0.008 | 2.3 |
| adversarial | JEV(p=0.55, latencia medida) | 400 | 9 / 1 / 390 | 0.024 | 0.010 | **no** | -0.001 | 2.3 |
| adversarial | JEV(p=0.70, latencia medida) | 400 | 7 / 1 / 392 | 0.019 | 0.007 | **no** | -0.006 | 2.4 |
| adversarial | JEV(p=0.85, latencia medida) | 400 | 2 / 1 / 397 | 0.006 | 0.001 | **no** | -0.019 | 2.4 |
| adversarial | JEV(p=1.00, latencia medida) | 400 | 2 / 0 / 398 | 0.005 | 0.001 | **no** | -0.020 | 2.5 |
| adversarial | JEV(p=1/k, latencia 0) | 400 | 5 / 17 / 378 | 0.034 | 0.016 | **no** | +0.009 | 2.2 |
| adversarial | JEV(p=0.40, latencia 0) | 400 | 6 / 16 / 378 | 0.035 | 0.017 | **no** | +0.010 | 2.3 |
| adversarial | JEV(p=0.55, latencia 0) | 400 | 3 / 16 / 381 | 0.028 | 0.012 | **no** | +0.002 | 2.3 |
| adversarial | JEV(p=0.70, latencia 0) | 400 | 4 / 14 / 382 | 0.028 | 0.012 | **no** | +0.002 | 2.3 |
| adversarial | JEV(p=0.85, latencia 0) | 400 | 3 / 13 / 384 | 0.024 | 0.010 | **no** | -0.001 | 2.3 |
| adversarial | JEV(p=1.00, latencia 0) | 400 | 3 / 12 / 385 | 0.023 | 0.009 | **no** | -0.003 | 2.4 |

## Sensibilidades (fuera del veredicto)

En el servidor, la choice `VANILLA_MELEE` golpea con `Mob.attack` (`CombatActionsAdapter`): a 2,5 bloques de hueco y sin enfriamiento propio, solo la invulnerabilidad; el zombi nativo golpea a 1,43 bloques cada 20 ticks. Las celdas K0 del veredicto usan lo del servidor; las de «golpe propio del zombi», el del zombi. Las celdas con `retreat_and_heal` en `APPROACH` cambian una línea del perfil de entrega (no el producto) para medir lo que cuesta retirarse mientras la cura recarga.

| Configuración | Escenario | Rival | Modo | Rondas | V / E / D | s | Cota (z de una celda) |
|---|---|---|---|---|---|---|---|
| defaults de entrega | K0-OPEN18-60 | vanilla | NONE, golpe propio del zombi | 400 | 0 / 209 / 191 | 0.261 | 0.227 |
| perfil del paquete | K0-OPEN18-60 | vanilla | NONE, golpe propio del zombi | 400 | 0 / 209 / 191 | 0.261 | 0.227 |
| defaults de entrega con retreat_and_heal en APPROACH | K1-OPEN18-60 | pack | NONE | 400 | 400 / 0 / 0 | 1.000 | 0.993 |
| defaults de entrega con retreat_and_heal en APPROACH | K1-OPEN18-60 | legal-equal | NONE | 400 | 250 / 0 / 150 | 0.625 | 0.584 |
| defaults de entrega con retreat_and_heal en APPROACH | K1-OPEN18-60 | legal-best | NONE | 400 | 15 / 0 / 385 | 0.038 | 0.025 |
| defaults de entrega con retreat_and_heal en APPROACH | K1-OPEN18-60 | adversarial | NONE | 400 | 30 / 0 / 370 | 0.075 | 0.056 |

## Matriz A–D medida otra vez (duel pack contra el clásico del paquete)

| Mundo | Brazo | Rondas | s rojo | Wilson 95 % | Empates | Ticks medios |
|---|---|---|---|---|---|---|
| LEGACY | A | 400 | 1.000 | [0.990; 1.000] | 0 | 395 |
| LEGACY | B | 400 | 0.000 | [0.000; 0.010] | 0 | 395 |
| LEGACY | C | 400 | 0.500 | [0.451; 0.549] | 400 | 1800 |
| LEGACY | D | 400 | 0.518 | [0.469; 0.566] | 0 | 557 |
| VANILLA + FAIR | A | 400 | 0.978 | [0.958; 0.988] | 0 | 549 |
| VANILLA + FAIR | B | 400 | 0.013 | [0.005; 0.029] | 0 | 549 |
| VANILLA + FAIR | C | 400 | 0.501 | [0.452; 0.550] | 399 | 1799 |
| VANILLA + FAIR | D | 400 | 0.500 | [0.451; 0.549] | 400 | 572 |

A y B: ThePredict de rojo y de azul; C: ThePredict contra sí mismo; D: el clásico contra sí mismo. `LEGACY` es el simulador de M18.7 (dorado); `VANILLA + FAIR`, el del banco.

## Búsquedas

- **legal-best de K1:**
  - etapa 1: 77760 configuraciones, 3110400 rondas; mejores: cfg-1085 1.000 cfg-1175 1.000 cfg-6485 1.000
  - etapa 2: 9720 configuraciones, 1555200 rondas; mejores: cfg-6570 1.000 cfg-6571 1.000 cfg-6840 1.000
  - etapa 3: 1215 configuraciones, 3110400 rondas; mejores: cfg-44282 0.996 cfg-44312 0.995 cfg-44310 0.995
  - etapa 4 (todos contra todos, Bradley-Terry): 10 configuraciones, 36000 rondas; mejores: cfg-44280 8.020 cfg-44282 1.061 cfg-44281 0.617
- **adversarial de K1 contra defaults de entrega:**
  - generación 1: 256 configuraciones, 10200 rondas; mejores: cfg-44280 0.025 cfg-43320 0.238 cfg-45096 0.350
  - generación 2: 256 configuraciones, 10040 rondas; mejores: cfg-42241 0.125 cfg-42782 0.175 cfg-41253 0.200
  - generación 3: 256 configuraciones, 9480 rondas; mejores: cfg-40050 0.025 cfg-41130 0.038 cfg-42210 0.038
  - generación 4: 256 configuraciones, 6080 rondas; mejores: cfg-40051 0.025 cfg-42210 0.038 cfg-40080 0.063
  - generación 5: 256 configuraciones, 3640 rondas; mejores: cfg-40050 0.025 cfg-40051 0.025 cfg-42227 0.025
  - finalistas en semillas nuevas: 5 configuraciones, 2000 rondas; mejores: cfg-40050 0.024 cfg-40051 0.035 cfg-44280 0.063
- **adversarial de K1 contra perfil del paquete:**
  - generación 1: 256 configuraciones, 10200 rondas; mejores: cfg-44280 0.025 cfg-43320 0.238 cfg-45096 0.350
  - generación 2: 256 configuraciones, 10040 rondas; mejores: cfg-42241 0.125 cfg-42782 0.175 cfg-42333 0.200
  - generación 3: 256 configuraciones, 9160 rondas; mejores: cfg-40050 0.025 cfg-41130 0.038 cfg-42210 0.038
  - generación 4: 256 configuraciones, 6160 rondas; mejores: cfg-42210 0.038 cfg-40080 0.063 cfg-41132 0.063
  - generación 5: 256 configuraciones, 3400 rondas; mejores: cfg-40050 0.025 cfg-40051 0.025 cfg-41130 0.038
  - finalistas en semillas nuevas: 5 configuraciones, 2000 rondas; mejores: cfg-40050 0.024 cfg-40051 0.035 cfg-42210 0.060
- **legal-best de K2:**
  - etapa 1: 12960 configuraciones, 518400 rondas; mejores: cfg-6480 1.000 cfg-6510 1.000 cfg-6540 1.000
  - etapa 2: 1620 configuraciones, 259200 rondas; mejores: cfg-6840 1.000 cfg-6841 1.000 cfg-6870 1.000
  - etapa 3: 203 configuraciones, 519680 rondas; mejores: cfg-11910 0.910 cfg-12630 0.910 cfg-12270 0.909
  - etapa 4 (todos contra todos, Bradley-Terry): 10 configuraciones, 36000 rondas; mejores: cfg-12270 1.583 cfg-12630 1.508 cfg-11190 1.378
- **adversarial de K2 contra defaults de entrega:**
  - generación 1: 256 configuraciones, 10160 rondas; mejores: cfg-12270 0.000 cfg-3997 0.050 cfg-10835 0.050
  - generación 2: 256 configuraciones, 9840 rondas; mejores: cfg-10111 0.000 cfg-11911 0.000 cfg-12285 0.000
  - generación 3: 256 configuraciones, 8280 rondas; mejores: cfg-9030 0.000 cfg-9751 0.000 cfg-10111 0.000
  - generación 4: 256 configuraciones, 3480 rondas; mejores: cfg-9751 0.000 cfg-10110 0.000 cfg-10111 0.000
  - generación 5: 256 configuraciones, 1360 rondas; mejores: cfg-9751 0.000 cfg-10110 0.000 cfg-10111 0.000
  - finalistas en semillas nuevas: 5 configuraciones, 2000 rondas; mejores: cfg-10112 0.003 cfg-10110 0.005 cfg-9751 0.023
- **adversarial de K2 contra perfil del paquete:**
  - generación 1: 256 configuraciones, 10160 rondas; mejores: cfg-12270 0.000 cfg-3997 0.050 cfg-10835 0.050
  - generación 2: 256 configuraciones, 9840 rondas; mejores: cfg-10111 0.000 cfg-11911 0.000 cfg-12285 0.000
  - generación 3: 256 configuraciones, 8280 rondas; mejores: cfg-9030 0.000 cfg-9751 0.000 cfg-10111 0.000
  - generación 4: 256 configuraciones, 3480 rondas; mejores: cfg-9751 0.000 cfg-10110 0.000 cfg-10111 0.000
  - generación 5: 256 configuraciones, 1360 rondas; mejores: cfg-9751 0.000 cfg-10110 0.000 cfg-10111 0.000
  - finalistas en semillas nuevas: 5 configuraciones, 2000 rondas; mejores: cfg-10112 0.003 cfg-10110 0.005 cfg-9751 0.023
- **legal-best de K3:**
  - etapa 1: 6480 configuraciones, 259200 rondas; mejores: cfg-540 1.000 cfg-555 1.000 cfg-720 1.000
  - etapa 2: 810 configuraciones, 129600 rondas; mejores: cfg-540 1.000 cfg-555 1.000 cfg-720 1.000
  - etapa 3: 102 configuraciones, 261120 rondas; mejores: cfg-3601 0.986 cfg-3616 0.986 cfg-3631 0.986
  - etapa 4 (todos contra todos, Bradley-Terry): 10 configuraciones, 36000 rondas; mejores: cfg-3600 1.204 cfg-3615 1.204 cfg-3630 1.204
- **adversarial de K3 contra defaults de entrega:**
  - generación 1: 256 configuraciones, 10040 rondas; mejores: cfg-5946 0.000 cfg-6152 0.050 cfg-4398 0.075
  - generación 2: 256 configuraciones, 9200 rondas; mejores: cfg-4186 0.000 cfg-4201 0.000 cfg-5400 0.000
  - generación 3: 256 configuraciones, 6880 rondas; mejores: cfg-5401 0.000 cfg-5416 0.000 cfg-5445 0.000
  - generación 4: 256 configuraciones, 3400 rondas; mejores: cfg-4186 0.000 cfg-5401 0.000 cfg-5415 0.000
  - generación 5: 256 configuraciones, 1480 rondas; mejores: cfg-4201 0.000 cfg-5400 0.000 cfg-5401 0.000
  - finalistas en semillas nuevas: 5 configuraciones, 2000 rondas; mejores: cfg-5400 0.000 cfg-5415 0.000 cfg-5401 0.005
- **adversarial de K3 contra perfil del paquete:**
  - generación 1: 256 configuraciones, 10040 rondas; mejores: cfg-5946 0.000 cfg-4398 0.075 cfg-6152 0.075
  - generación 2: 256 configuraciones, 9120 rondas; mejores: cfg-4186 0.000 cfg-4201 0.000 cfg-5400 0.000
  - generación 3: 256 configuraciones, 6600 rondas; mejores: cfg-5401 0.000 cfg-5416 0.000 cfg-5445 0.000
  - generación 4: 256 configuraciones, 2960 rondas; mejores: cfg-4186 0.000 cfg-5400 0.000 cfg-5401 0.000
  - generación 5: 256 configuraciones, 1440 rondas; mejores: cfg-4201 0.000 cfg-5400 0.000 cfg-5401 0.000
  - finalistas en semillas nuevas: 5 configuraciones, 2000 rondas; mejores: cfg-5400 0.000 cfg-5415 0.000 cfg-5401 0.005
- **legal-best de K4:**
  - etapa 1: 6480 configuraciones, 259200 rondas; mejores: cfg-0 0.500 cfg-1 0.500 cfg-2 0.500
  - etapa 2: 810 configuraciones, 129600 rondas; mejores: cfg-0 0.500 cfg-1 0.500 cfg-2 0.500
  - etapa 3: 102 configuraciones, 261120 rondas; mejores: cfg-0 0.500 cfg-1 0.500 cfg-2 0.500
  - etapa 4 (todos contra todos, Bradley-Terry): 10 configuraciones, 36000 rondas; mejores: cfg-0 1.000 cfg-1 1.000 cfg-2 1.000
- **adversarial de K4 contra defaults de entrega:**
  - generación 1: 256 configuraciones, 9960 rondas; mejores: cfg-0 0.500 cfg-19 0.500 cfg-23 0.500
  - generación 2: 256 configuraciones, 9080 rondas; mejores: cfg-0 0.500 cfg-2 0.500 cfg-3 0.500
  - generación 3: 256 configuraciones, 6440 rondas; mejores: cfg-0 0.500 cfg-1 0.500 cfg-2 0.500
  - generación 4: 256 configuraciones, 2000 rondas; mejores: cfg-0 0.500 cfg-1 0.500 cfg-3 0.500
  - generación 5: 256 configuraciones, 600 rondas; mejores: cfg-0 0.500 cfg-1 0.500 cfg-2 0.500
  - finalistas en semillas nuevas: 5 configuraciones, 2000 rondas; mejores: cfg-0 0.500 cfg-1 0.500 cfg-2 0.500
- **adversarial de K4 contra perfil del paquete:**
  - generación 1: 256 configuraciones, 9960 rondas; mejores: cfg-0 0.500 cfg-19 0.500 cfg-23 0.500
  - generación 2: 256 configuraciones, 9080 rondas; mejores: cfg-0 0.500 cfg-2 0.500 cfg-3 0.500
  - generación 3: 256 configuraciones, 6440 rondas; mejores: cfg-0 0.500 cfg-1 0.500 cfg-2 0.500
  - generación 4: 256 configuraciones, 2000 rondas; mejores: cfg-0 0.500 cfg-1 0.500 cfg-3 0.500
  - generación 5: 256 configuraciones, 600 rondas; mejores: cfg-0 0.500 cfg-1 0.500 cfg-2 0.500
  - finalistas en semillas nuevas: 5 configuraciones, 2000 rondas; mejores: cfg-0 0.500 cfg-1 0.500 cfg-2 0.500

## Límites

- Es un simulador. La física `VANILLA` (velocidad, giro, empuje, vuelo del salto) es PLAUSIBLE: no se verificó con `javap` porque el JAR de Paper es un parche. Se publica también la matriz A–D en `LEGACY`.
- El golpe vanilla de 0 del clásico no abre ventana de invulnerabilidad ni empuja (SIM-19, pendiente de DT-40 e en el servidor).
- El aura que hace esperar al clásico su propia ventana supone que una aplicación posterior no acorta la anterior (PLAUSIBLE; comprobar en juego).
- Un espejo clásico idéntico acaba siempre en empate: la distancia es común y las reglas son simétricas. V1 confirma que no queda sesgo de orden, no que haya ruido.
- El rival se busca en un espacio legal amplio pero finito (temporizadores, distancias, vida, velocidades, huida, banda y aura). Un autor puede escribir cosas fuera de él.
- Jev real no se mide: el juez es un oráculo de anticipación de 40 ticks. p* dice qué precisión necesitaría Jev, no cuál tiene.
- La evidencia en servidor (G4 de M22: TTK y casts por minuto frente al simulador) sigue pendiente.

## Reproducción

```
JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew effectivenessBench --offline
```

Propiedades: `-Pseeds=` (200), `-Pthreads=` (4), `-Psearch=auto|force|never`, `-Pfreeze=true` para escribir los ganadores en `src/test/resources/eval`.

Medido en 640 s en esta máquina.
