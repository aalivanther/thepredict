# E2. Duel matrix by cast mode and horizon (M19.9)

Simulator of M18.7 with the real engine. 200 paired seeds per arm; the same seeds in every arm and every configuration; spawn points swapped every two rounds. Modes NONE and ORACLE(0.85). Pruning stays off and the option cap stays 8. No network and no claim of superiority.

## Mode NONE (no key: LOCAL and BASE) — INTENT_ONLY, horizon 0

| Arm | Rounds | Red wins | Blue wins | Draws | Red score | Wilson 95 % | Ticks mean (sd, min-max) | Red dealt mean | Blue dealt mean | Retreats | Requests | Tokens | Duplicated casts |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | 200 | 84 | 116 | 0 | 0.420 | [0.354, 0.489] | 569 (24, 483-614) | 1.455 | 1.523 | 394 | 0 | 0 | 0 |
| B | 200 | 114 | 86 | 0 | 0.570 | [0.501, 0.637] | 569 (28, 468-614) | 1.523 | 1.455 | 389 | 0 | 0 | 0 |
| C | 200 | 100 | 100 | 0 | 0.500 | [0.431, 0.569] | 594 (31, 550-739) | 1.474 | 1.477 | 808 | 0 | 0 | 0 |
| D | 200 | 110 | 90 | 0 | 0.550 | [0.481, 0.617] | 557 (11, 542-586) | 1.492 | 1.486 | 0 | 0 | 0 | 0 |

| Arm | Skill | Red casts | Blue casts | Red hits | Blue hits |
|---|---|---|---|---|---|
| A | DuelBolt | 1548 | 190 | 1367 | 190 |
| A | DuelGuard | 671 | 594 | 0 | 0 |
| A | DuelHeal | 394 | 400 | 0 | 0 |
| A | DuelLeap | 390 | 163 | 390 | 74 |
| A | DuelStrike | 2739 | 3655 | 2515 | 3512 |
| B | DuelBolt | 184 | 1554 | 181 | 1381 |
| B | DuelGuard | 589 | 686 | 0 | 0 |
| B | DuelHeal | 400 | 389 | 0 | 0 |
| B | DuelLeap | 175 | 365 | 73 | 365 |
| B | DuelStrike | 3665 | 2737 | 3517 | 2517 |
| C | DuelBolt | 1630 | 1626 | 1484 | 1464 |
| C | DuelGuard | 604 | 603 | 0 | 0 |
| C | DuelHeal | 404 | 404 | 0 | 0 |
| C | DuelLeap | 675 | 676 | 649 | 660 |
| C | DuelStrike | 2683 | 2672 | 2387 | 2403 |
| D | DuelBolt | 172 | 174 | 170 | 168 |
| D | DuelGuard | 600 | 600 | 0 | 0 |
| D | DuelHeal | 400 | 400 | 0 | 0 |
| D | DuelLeap | 82 | 70 | 30 | 31 |
| D | DuelStrike | 3622 | 3624 | 3468 | 3453 |

| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |
|---|---|---|---|---|
| A | 0 | 0 | 92208 | 21641 |
| B | 0 | 0 | 92543 | 21279 |
| C | 0 | 0 | 182310 | 55474 |
| D | 0 | 0 | 0 | 0 |

| Arm | Tactic | Windows | Mean effect |
|---|---|---|---|
| A | guard_up | 459 | 0.000 |
| A | keep_distance_and_bolt | 1548 | 0.013 |
| A | leap_in | 390 | 0.250 |
| A | press_and_strike | 2061 | 0.226 |
| A | retreat_and_heal | 394 | 0.039 |
| B | guard_up | 468 | 0.000 |
| B | keep_distance_and_bolt | 1553 | 0.012 |
| B | leap_in | 365 | 0.250 |
| B | press_and_strike | 2075 | 0.226 |
| B | retreat_and_heal | 389 | 0.039 |
| C | approach | 40 | 0.231 |
| C | guard_up | 800 | 0.000 |
| C | keep_distance_and_bolt | 3290 | 0.130 |
| C | leap_in | 1274 | 0.250 |
| C | press_and_strike | 3745 | 0.220 |
| C | retreat_and_heal | 808 | 0.236 |

| Skill | Engine casts | Ticks in reach | Cooldown | Casts / cooldown max |
|---|---|---|---|---|
| DuelStrike | 7029 | 293906 | 30 | 71.7 % |
| DuelBolt | 6358 | 359050 | 60 | 106.2 % |
| DuelLeap | 2029 | 333790 | 120 | 72.9 % |
| DuelGuard | 1727 | 367061 | 160 | 75.3 % |
| DuelHeal | 1591 | 367061 | 200 | 86.7 % |

Criterio de no regresion (INTENT_ONLY, horizon 0, Mode NONE (no key: LOCAL and BASE)): no cumple. Duplicados 0 (cumple). Inicios durante una preparacion 369 (no cumple). Ticks de espera lista-cast sin guarda 178275 (no cumple). Cadencia en alcance no cumple. Wilson del brazo A es la referencia INTENT_ONLY horizonte 0.

## Mode ORACLE(0.85) — INTENT_ONLY, horizon 0

| Arm | Rounds | Red wins | Blue wins | Draws | Red score | Wilson 95 % | Ticks mean (sd, min-max) | Red dealt mean | Blue dealt mean | Retreats | Requests | Tokens | Duplicated casts |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | 200 | 38 | 162 | 0 | 0.190 | [0.142, 0.250] | 551 (30, 462-624) | 1.334 | 1.482 | 364 | 3699 | 2589060 | 0 |
| B | 200 | 172 | 28 | 0 | 0.860 | [0.805, 0.901] | 551 (34, 461-627) | 1.488 | 1.332 | 367 | 3697 | 2587060 | 0 |
| C | 200 | 112 | 88 | 0 | 0.560 | [0.491, 0.627] | 646 (56, 519-971) | 1.480 | 1.460 | 815 | 8824 | 6172480 | 0 |
| D | 200 | 110 | 90 | 0 | 0.550 | [0.481, 0.617] | 557 (11, 542-586) | 1.492 | 1.486 | 0 | 0 | 0 | 0 |

| Arm | Skill | Red casts | Blue casts | Red hits | Blue hits |
|---|---|---|---|---|---|
| A | DuelBolt | 1305 | 177 | 1189 | 176 |
| A | DuelGuard | 588 | 561 | 0 | 0 |
| A | DuelHeal | 364 | 392 | 0 | 0 |
| A | DuelLeap | 405 | 171 | 403 | 80 |
| A | DuelStrike | 2523 | 3551 | 2326 | 3420 |
| B | DuelBolt | 177 | 1329 | 173 | 1208 |
| B | DuelGuard | 569 | 588 | 0 | 0 |
| B | DuelHeal | 389 | 365 | 0 | 0 |
| B | DuelLeap | 172 | 412 | 74 | 410 |
| B | DuelStrike | 3565 | 2506 | 3437 | 2307 |
| C | DuelBolt | 1521 | 1537 | 1408 | 1425 |
| C | DuelGuard | 664 | 651 | 0 | 0 |
| C | DuelHeal | 400 | 403 | 0 | 0 |
| C | DuelLeap | 664 | 669 | 617 | 622 |
| C | DuelStrike | 2667 | 2652 | 2461 | 2401 |
| D | DuelBolt | 172 | 174 | 170 | 168 |
| D | DuelGuard | 600 | 600 | 0 | 0 |
| D | DuelHeal | 400 | 400 | 0 | 0 |
| D | DuelLeap | 82 | 70 | 30 | 31 |
| D | DuelStrike | 3622 | 3624 | 3468 | 3453 |

| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |
|---|---|---|---|---|
| A | 78309 | 2338 | 10972 | 18497 |
| B | 78862 | 2350 | 10856 | 18148 |
| C | 187292 | 10522 | 21428 | 39284 |
| D | 0 | 0 | 0 | 0 |

| Arm | Tactic | Windows | Mean effect |
|---|---|---|---|
| A | approach | 345 | 0.246 |
| A | guard_up | 405 | 0.000 |
| A | keep_distance_and_bolt | 1300 | 0.008 |
| A | leap_in | 421 | 0.247 |
| A | press_and_strike | 1905 | 0.203 |
| A | retreat_and_heal | 359 | 0.017 |
| B | approach | 331 | 0.245 |
| B | guard_up | 406 | 0.000 |
| B | keep_distance_and_bolt | 1319 | 0.008 |
| B | leap_in | 428 | 0.247 |
| B | press_and_strike | 1888 | 0.202 |
| B | retreat_and_heal | 358 | 0.023 |
| C | approach | 1000 | 0.233 |
| C | guard_up | 947 | 0.000 |
| C | keep_distance_and_bolt | 3052 | 0.121 |
| C | leap_in | 1269 | 0.249 |
| C | press_and_strike | 4025 | 0.200 |
| C | retreat_and_heal | 814 | 0.107 |

| Skill | Engine casts | Ticks in reach | Cooldown | Casts / cooldown max |
|---|---|---|---|---|
| DuelStrike | 7415 | 338139 | 30 | 65.8 % |
| DuelBolt | 5692 | 394696 | 60 | 86.5 % |
| DuelLeap | 2054 | 380484 | 120 | 64.8 % |
| DuelGuard | 1757 | 402929 | 160 | 69.8 % |
| DuelHeal | 1532 | 402929 | 200 | 76.0 % |

Criterio de no regresion (INTENT_ONLY, horizon 0, Mode ORACLE(0.85)): no cumple. Duplicados 0 (cumple). Inicios durante una preparacion 323 (no cumple). Ticks de espera lista-cast sin guarda 282589 (no cumple). Cadencia en alcance no cumple. Wilson del brazo A es la referencia INTENT_ONLY horizonte 0.

## Mode NONE (no key: LOCAL and BASE) — ANY_READY, horizon 0

| Arm | Rounds | Red wins | Blue wins | Draws | Red score | Wilson 95 % | Ticks mean (sd, min-max) | Red dealt mean | Blue dealt mean | Retreats | Requests | Tokens | Duplicated casts |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | 200 | 159 | 41 | 0 | 0.795 | [0.734, 0.845] | 477 (89, 380-652) | 1.387 | 1.263 | 71 | 0 | 0 | 0 |
| B | 200 | 48 | 152 | 0 | 0.240 | [0.186, 0.304] | 488 (93, 380-806) | 1.297 | 1.391 | 86 | 0 | 0 | 0 |
| C | 200 | 137 | 63 | 0 | 0.685 | [0.618, 0.745] | 636 (108, 532-1302) | 1.580 | 1.532 | 451 | 0 | 0 | 0 |
| D | 200 | 110 | 90 | 0 | 0.550 | [0.481, 0.617] | 557 (11, 542-586) | 1.492 | 1.486 | 0 | 0 | 0 | 0 |

| Arm | Skill | Red casts | Blue casts | Red hits | Blue hits |
|---|---|---|---|---|---|
| A | DuelBolt | 1511 | 193 | 1493 | 182 |
| A | DuelGuard | 667 | 467 | 0 | 0 |
| A | DuelHeal | 267 | 304 | 0 | 0 |
| A | DuelLeap | 685 | 86 | 662 | 42 |
| A | DuelStrike | 2886 | 3043 | 2168 | 2906 |
| B | DuelBolt | 187 | 1553 | 182 | 1541 |
| B | DuelGuard | 473 | 674 | 0 | 0 |
| B | DuelHeal | 313 | 282 | 0 | 0 |
| B | DuelLeap | 89 | 689 | 35 | 675 |
| B | DuelStrike | 3126 | 2968 | 2989 | 2144 |
| C | DuelBolt | 2101 | 2097 | 2019 | 2006 |
| C | DuelGuard | 822 | 794 | 0 | 0 |
| C | DuelHeal | 420 | 391 | 0 | 0 |
| C | DuelLeap | 899 | 873 | 881 | 864 |
| C | DuelStrike | 3335 | 3296 | 2227 | 2128 |
| D | DuelBolt | 172 | 174 | 170 | 168 |
| D | DuelGuard | 600 | 600 | 0 | 0 |
| D | DuelHeal | 400 | 400 | 0 | 0 |
| D | DuelLeap | 82 | 70 | 30 | 31 |
| D | DuelStrike | 3622 | 3624 | 3468 | 3453 |

| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |
|---|---|---|---|---|
| A | 0 | 0 | 87310 | 8096 |
| B | 0 | 0 | 88003 | 9650 |
| C | 0 | 0 | 212839 | 41403 |
| D | 0 | 0 | 0 | 0 |

| Arm | Tactic | Windows | Mean effect |
|---|---|---|---|
| A | approach | 345 | 0.250 |
| A | guard_up | 145 | 0.000 |
| A | keep_distance_and_bolt | 1339 | 0.007 |
| A | leap_in | 259 | 0.250 |
| A | press_and_strike | 1242 | 0.210 |
| A | retreat_and_heal | 71 | 0.141 |
| B | approach | 366 | 0.250 |
| B | guard_up | 129 | 0.000 |
| B | keep_distance_and_bolt | 1388 | 0.006 |
| B | leap_in | 299 | 0.250 |
| B | press_and_strike | 1268 | 0.211 |
| B | retreat_and_heal | 81 | 0.120 |
| C | approach | 1148 | 0.250 |
| C | guard_up | 370 | 0.000 |
| C | keep_distance_and_bolt | 2092 | 0.150 |
| C | leap_in | 1527 | 0.250 |
| C | press_and_strike | 2256 | 0.199 |
| C | retreat_and_heal | 450 | 0.133 |

| Skill | Engine casts | Ticks in reach | Cooldown | Casts / cooldown max |
|---|---|---|---|---|
| DuelStrike | 10544 | 324584 | 30 | 97.5 % |
| DuelBolt | 7262 | 380149 | 60 | 114.6 % |
| DuelLeap | 3114 | 355279 | 120 | 105.2 % |
| DuelGuard | 2760 | 388152 | 160 | 113.8 % |
| DuelHeal | 1353 | 388152 | 200 | 69.7 % |

Criterio de no regresion (ANY_READY, horizon 0, Mode NONE (no key: LOCAL and BASE)): no cumple. Duplicados 0 (cumple). Inicios durante una preparacion 119 (no cumple). Ticks de espera lista-cast sin guarda 28358 (no cumple). Cadencia en alcance no cumple. Wilson del brazo A [0.734, 0.845] frente a [0.354, 0.489] de INTENT_ONLY horizonte 0 (cumple).

## Mode ORACLE(0.85) — ANY_READY, horizon 0

| Arm | Rounds | Red wins | Blue wins | Draws | Red score | Wilson 95 % | Ticks mean (sd, min-max) | Red dealt mean | Blue dealt mean | Retreats | Requests | Tokens | Duplicated casts |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | 200 | 181 | 19 | 0 | 0.905 | [0.856, 0.938] | 482 (70, 365-879) | 1.406 | 1.262 | 136 | 1148 | 803600 | 0 |
| B | 200 | 20 | 180 | 0 | 0.100 | [0.066, 0.149] | 491 (66, 369-637) | 1.290 | 1.414 | 167 | 1159 | 811300 | 0 |
| C | 200 | 88 | 112 | 0 | 0.440 | [0.373, 0.509] | 523 (112, 373-1272) | 1.406 | 1.429 | 273 | 2487 | 1740900 | 0 |
| D | 200 | 110 | 90 | 0 | 0.550 | [0.481, 0.617] | 557 (11, 542-586) | 1.492 | 1.486 | 0 | 0 | 0 | 0 |

| Arm | Skill | Red casts | Blue casts | Red hits | Blue hits |
|---|---|---|---|---|---|
| A | DuelBolt | 1575 | 181 | 1520 | 181 |
| A | DuelGuard | 659 | 461 | 0 | 0 |
| A | DuelHeal | 293 | 309 | 0 | 0 |
| A | DuelLeap | 658 | 114 | 611 | 51 |
| A | DuelStrike | 2840 | 3055 | 2219 | 2900 |
| B | DuelBolt | 179 | 1604 | 174 | 1537 |
| B | DuelGuard | 469 | 665 | 0 | 0 |
| B | DuelHeal | 321 | 305 | 0 | 0 |
| B | DuelLeap | 122 | 665 | 40 | 620 |
| B | DuelStrike | 3128 | 2904 | 2976 | 2224 |
| C | DuelBolt | 1684 | 1694 | 1611 | 1637 |
| C | DuelGuard | 657 | 657 | 0 | 0 |
| C | DuelHeal | 330 | 326 | 0 | 0 |
| C | DuelLeap | 719 | 722 | 679 | 696 |
| C | DuelStrike | 2903 | 2903 | 2135 | 2169 |
| D | DuelBolt | 172 | 174 | 170 | 168 |
| D | DuelGuard | 600 | 600 | 0 | 0 |
| D | DuelHeal | 400 | 400 | 0 | 0 |
| D | DuelLeap | 82 | 70 | 30 | 31 |
| D | DuelStrike | 3622 | 3624 | 3468 | 3453 |

| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |
|---|---|---|---|---|
| A | 27017 | 6070 | 55910 | 7354 |
| B | 27511 | 5948 | 56395 | 8331 |
| C | 52827 | 19190 | 104853 | 32306 |
| D | 0 | 0 | 0 | 0 |

| Arm | Tactic | Windows | Mean effect |
|---|---|---|---|
| A | approach | 764 | 0.249 |
| A | guard_up | 129 | 0.000 |
| A | keep_distance_and_bolt | 923 | 0.005 |
| A | leap_in | 505 | 0.247 |
| A | press_and_strike | 1236 | 0.177 |
| A | retreat_and_heal | 126 | 0.036 |
| B | approach | 793 | 0.249 |
| B | guard_up | 119 | 0.000 |
| B | keep_distance_and_bolt | 941 | 0.006 |
| B | leap_in | 520 | 0.247 |
| B | press_and_strike | 1225 | 0.177 |
| B | retreat_and_heal | 175 | 0.037 |
| C | approach | 1860 | 0.246 |
| C | guard_up | 423 | 0.000 |
| C | keep_distance_and_bolt | 2010 | 0.089 |
| C | leap_in | 1203 | 0.247 |
| C | press_and_strike | 2534 | 0.175 |
| C | retreat_and_heal | 269 | 0.100 |

| Skill | Engine casts | Ticks in reach | Cooldown | Casts / cooldown max |
|---|---|---|---|---|
| DuelStrike | 9946 | 308209 | 30 | 96.8 % |
| DuelBolt | 6557 | 347501 | 60 | 113.2 % |
| DuelLeap | 2727 | 331354 | 120 | 98.8 % |
| DuelGuard | 2419 | 355721 | 160 | 108.8 % |
| DuelHeal | 1242 | 355721 | 200 | 69.8 % |

Criterio de no regresion (ANY_READY, horizon 0, Mode ORACLE(0.85)): no cumple. Duplicados 0 (cumple). Inicios durante una preparacion 367 (no cumple). Ticks de espera lista-cast sin guarda 37768 (no cumple). Cadencia en alcance no cumple. Wilson del brazo A [0.856, 0.938] frente a [0.142, 0.250] de INTENT_ONLY horizonte 0 (cumple).

## Mode NONE (no key: LOCAL and BASE) — INTENT_ONLY, horizon 20

| Arm | Rounds | Red wins | Blue wins | Draws | Red score | Wilson 95 % | Ticks mean (sd, min-max) | Red dealt mean | Blue dealt mean | Retreats | Requests | Tokens | Duplicated casts |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | 200 | 84 | 116 | 0 | 0.420 | [0.354, 0.489] | 569 (24, 483-614) | 1.455 | 1.523 | 394 | 0 | 0 | 0 |
| B | 200 | 114 | 86 | 0 | 0.570 | [0.501, 0.637] | 569 (28, 468-614) | 1.523 | 1.455 | 389 | 0 | 0 | 0 |
| C | 200 | 100 | 100 | 0 | 0.500 | [0.431, 0.569] | 594 (31, 550-739) | 1.474 | 1.477 | 808 | 0 | 0 | 0 |
| D | 200 | 110 | 90 | 0 | 0.550 | [0.481, 0.617] | 557 (11, 542-586) | 1.492 | 1.486 | 0 | 0 | 0 | 0 |

| Arm | Skill | Red casts | Blue casts | Red hits | Blue hits |
|---|---|---|---|---|---|
| A | DuelBolt | 1548 | 190 | 1367 | 190 |
| A | DuelGuard | 671 | 594 | 0 | 0 |
| A | DuelHeal | 394 | 400 | 0 | 0 |
| A | DuelLeap | 390 | 163 | 390 | 74 |
| A | DuelStrike | 2739 | 3655 | 2515 | 3512 |
| B | DuelBolt | 184 | 1554 | 181 | 1381 |
| B | DuelGuard | 589 | 686 | 0 | 0 |
| B | DuelHeal | 400 | 389 | 0 | 0 |
| B | DuelLeap | 175 | 365 | 73 | 365 |
| B | DuelStrike | 3665 | 2737 | 3517 | 2517 |
| C | DuelBolt | 1630 | 1626 | 1484 | 1464 |
| C | DuelGuard | 604 | 603 | 0 | 0 |
| C | DuelHeal | 404 | 404 | 0 | 0 |
| C | DuelLeap | 675 | 676 | 649 | 660 |
| C | DuelStrike | 2683 | 2672 | 2387 | 2403 |
| D | DuelBolt | 172 | 174 | 170 | 168 |
| D | DuelGuard | 600 | 600 | 0 | 0 |
| D | DuelHeal | 400 | 400 | 0 | 0 |
| D | DuelLeap | 82 | 70 | 30 | 31 |
| D | DuelStrike | 3622 | 3624 | 3468 | 3453 |

| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |
|---|---|---|---|---|
| A | 0 | 0 | 92208 | 21641 |
| B | 0 | 0 | 92543 | 21279 |
| C | 0 | 0 | 182310 | 55474 |
| D | 0 | 0 | 0 | 0 |

| Arm | Tactic | Windows | Mean effect |
|---|---|---|---|
| A | guard_up | 459 | 0.000 |
| A | keep_distance_and_bolt | 1548 | 0.013 |
| A | leap_in | 390 | 0.250 |
| A | press_and_strike | 2061 | 0.226 |
| A | retreat_and_heal | 394 | 0.039 |
| B | guard_up | 468 | 0.000 |
| B | keep_distance_and_bolt | 1553 | 0.012 |
| B | leap_in | 365 | 0.250 |
| B | press_and_strike | 2075 | 0.226 |
| B | retreat_and_heal | 389 | 0.039 |
| C | approach | 40 | 0.231 |
| C | guard_up | 800 | 0.000 |
| C | keep_distance_and_bolt | 3290 | 0.130 |
| C | leap_in | 1274 | 0.250 |
| C | press_and_strike | 3745 | 0.220 |
| C | retreat_and_heal | 808 | 0.236 |

| Skill | Engine casts | Ticks in reach | Cooldown | Casts / cooldown max |
|---|---|---|---|---|
| DuelStrike | 7029 | 293906 | 30 | 71.7 % |
| DuelBolt | 6358 | 359050 | 60 | 106.2 % |
| DuelLeap | 2029 | 333790 | 120 | 72.9 % |
| DuelGuard | 1727 | 367061 | 160 | 75.3 % |
| DuelHeal | 1591 | 367061 | 200 | 86.7 % |

Criterio de no regresion (INTENT_ONLY, horizon 20, Mode NONE (no key: LOCAL and BASE)): no cumple. Duplicados 0 (cumple). Inicios durante una preparacion 369 (no cumple). Ticks de espera lista-cast sin guarda 178275 (no cumple). Cadencia en alcance no cumple. Wilson del brazo A [0.354, 0.489] frente a [0.354, 0.489] de INTENT_ONLY horizonte 0 (cumple).

## Mode ORACLE(0.85) — INTENT_ONLY, horizon 20

| Arm | Rounds | Red wins | Blue wins | Draws | Red score | Wilson 95 % | Ticks mean (sd, min-max) | Red dealt mean | Blue dealt mean | Retreats | Requests | Tokens | Duplicated casts |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | 200 | 0 | 200 | 0 | 0.000 | [0.000, 0.019] | 479 (28, 426-586) | 1.028 | 1.291 | 216 | 3516 | 2448000 | 0 |
| B | 200 | 200 | 0 | 0 | 1.000 | [0.981, 1.000] | 479 (31, 424-574) | 1.296 | 1.025 | 216 | 3513 | 2445420 | 0 |
| C | 200 | 91 | 109 | 0 | 0.455 | [0.387, 0.524] | 627 (76, 486-838) | 1.305 | 1.316 | 606 | 9570 | 6654120 | 0 |
| D | 200 | 110 | 90 | 0 | 0.550 | [0.481, 0.617] | 557 (11, 542-586) | 1.492 | 1.486 | 0 | 0 | 0 | 0 |

| Arm | Skill | Red casts | Blue casts | Red hits | Blue hits |
|---|---|---|---|---|---|
| A | DuelBolt | 733 | 178 | 676 | 178 |
| A | DuelGuard | 321 | 409 | 0 | 0 |
| A | DuelHeal | 216 | 217 | 0 | 0 |
| A | DuelLeap | 183 | 166 | 180 | 75 |
| A | DuelStrike | 2190 | 3085 | 1989 | 2961 |
| B | DuelBolt | 180 | 726 | 173 | 678 |
| B | DuelGuard | 405 | 330 | 0 | 0 |
| B | DuelHeal | 220 | 214 | 0 | 0 |
| B | DuelLeap | 166 | 159 | 71 | 158 |
| B | DuelStrike | 3100 | 2202 | 2979 | 1989 |
| C | DuelBolt | 939 | 898 | 828 | 805 |
| C | DuelGuard | 403 | 419 | 0 | 0 |
| C | DuelHeal | 303 | 301 | 0 | 0 |
| C | DuelLeap | 382 | 371 | 339 | 351 |
| C | DuelStrike | 2736 | 2735 | 2500 | 2534 |
| D | DuelBolt | 172 | 174 | 170 | 168 |
| D | DuelGuard | 600 | 600 | 0 | 0 |
| D | DuelHeal | 400 | 400 | 0 | 0 |
| D | DuelLeap | 82 | 70 | 30 | 31 |
| D | DuelStrike | 3622 | 3624 | 3468 | 3453 |

| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |
|---|---|---|---|---|
| A | 68538 | 5660 | 5117 | 16385 |
| B | 68806 | 5522 | 4759 | 16764 |
| C | 185744 | 12212 | 14883 | 38089 |
| D | 0 | 0 | 0 | 0 |

| Arm | Tactic | Windows | Mean effect |
|---|---|---|---|
| A | approach | 1216 | 0.249 |
| A | guard_up | 174 | 0.000 |
| A | keep_distance_and_bolt | 731 | 0.006 |
| A | leap_in | 200 | 0.244 |
| A | press_and_strike | 1838 | 0.200 |
| A | retreat_and_heal | 218 | 0.011 |
| B | approach | 1219 | 0.249 |
| B | guard_up | 178 | 0.000 |
| B | keep_distance_and_bolt | 728 | 0.004 |
| B | leap_in | 174 | 0.241 |
| B | press_and_strike | 1844 | 0.201 |
| B | retreat_and_heal | 218 | 0.011 |
| C | approach | 3415 | 0.247 |
| C | guard_up | 444 | 0.000 |
| C | keep_distance_and_bolt | 1826 | 0.098 |
| C | leap_in | 675 | 0.247 |
| C | press_and_strike | 4598 | 0.210 |
| C | retreat_and_heal | 593 | 0.062 |

| Skill | Engine casts | Ticks in reach | Cooldown | Casts / cooldown max |
|---|---|---|---|---|
| DuelStrike | 7184 | 331028 | 30 | 65.1 % |
| DuelBolt | 3296 | 362949 | 60 | 54.5 % |
| DuelLeap | 991 | 351221 | 120 | 33.9 % |
| DuelGuard | 796 | 371241 | 160 | 34.3 % |
| DuelHeal | 1034 | 371241 | 200 | 55.7 % |

Criterio de no regresion (INTENT_ONLY, horizon 20, Mode ORACLE(0.85)): no cumple. Duplicados 0 (cumple). Inicios durante una preparacion 199 (no cumple). Ticks de espera lista-cast sin guarda 594759 (no cumple). Cadencia en alcance no cumple. Wilson del brazo A [0.000, 0.019] frente a [0.142, 0.250] de INTENT_ONLY horizonte 0 (no cumple).

## Mode NONE (no key: LOCAL and BASE) — ANY_READY, horizon 20

| Arm | Rounds | Red wins | Blue wins | Draws | Red score | Wilson 95 % | Ticks mean (sd, min-max) | Red dealt mean | Blue dealt mean | Retreats | Requests | Tokens | Duplicated casts |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | 200 | 159 | 41 | 0 | 0.795 | [0.734, 0.845] | 477 (89, 380-652) | 1.387 | 1.263 | 71 | 0 | 0 | 0 |
| B | 200 | 48 | 152 | 0 | 0.240 | [0.186, 0.304] | 488 (93, 380-806) | 1.297 | 1.391 | 86 | 0 | 0 | 0 |
| C | 200 | 137 | 63 | 0 | 0.685 | [0.618, 0.745] | 636 (108, 532-1302) | 1.580 | 1.532 | 451 | 0 | 0 | 0 |
| D | 200 | 110 | 90 | 0 | 0.550 | [0.481, 0.617] | 557 (11, 542-586) | 1.492 | 1.486 | 0 | 0 | 0 | 0 |

| Arm | Skill | Red casts | Blue casts | Red hits | Blue hits |
|---|---|---|---|---|---|
| A | DuelBolt | 1511 | 193 | 1493 | 182 |
| A | DuelGuard | 667 | 467 | 0 | 0 |
| A | DuelHeal | 267 | 304 | 0 | 0 |
| A | DuelLeap | 685 | 86 | 662 | 42 |
| A | DuelStrike | 2886 | 3043 | 2168 | 2906 |
| B | DuelBolt | 187 | 1553 | 182 | 1541 |
| B | DuelGuard | 473 | 674 | 0 | 0 |
| B | DuelHeal | 313 | 282 | 0 | 0 |
| B | DuelLeap | 89 | 689 | 35 | 675 |
| B | DuelStrike | 3126 | 2968 | 2989 | 2144 |
| C | DuelBolt | 2101 | 2097 | 2019 | 2006 |
| C | DuelGuard | 822 | 794 | 0 | 0 |
| C | DuelHeal | 420 | 391 | 0 | 0 |
| C | DuelLeap | 899 | 873 | 881 | 864 |
| C | DuelStrike | 3335 | 3296 | 2227 | 2128 |
| D | DuelBolt | 172 | 174 | 170 | 168 |
| D | DuelGuard | 600 | 600 | 0 | 0 |
| D | DuelHeal | 400 | 400 | 0 | 0 |
| D | DuelLeap | 82 | 70 | 30 | 31 |
| D | DuelStrike | 3622 | 3624 | 3468 | 3453 |

| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |
|---|---|---|---|---|
| A | 0 | 0 | 87310 | 8096 |
| B | 0 | 0 | 88003 | 9650 |
| C | 0 | 0 | 212839 | 41403 |
| D | 0 | 0 | 0 | 0 |

| Arm | Tactic | Windows | Mean effect |
|---|---|---|---|
| A | approach | 345 | 0.250 |
| A | guard_up | 145 | 0.000 |
| A | keep_distance_and_bolt | 1339 | 0.007 |
| A | leap_in | 259 | 0.250 |
| A | press_and_strike | 1242 | 0.210 |
| A | retreat_and_heal | 71 | 0.141 |
| B | approach | 366 | 0.250 |
| B | guard_up | 129 | 0.000 |
| B | keep_distance_and_bolt | 1388 | 0.006 |
| B | leap_in | 299 | 0.250 |
| B | press_and_strike | 1268 | 0.211 |
| B | retreat_and_heal | 81 | 0.120 |
| C | approach | 1148 | 0.250 |
| C | guard_up | 370 | 0.000 |
| C | keep_distance_and_bolt | 2092 | 0.150 |
| C | leap_in | 1527 | 0.250 |
| C | press_and_strike | 2256 | 0.199 |
| C | retreat_and_heal | 450 | 0.133 |

| Skill | Engine casts | Ticks in reach | Cooldown | Casts / cooldown max |
|---|---|---|---|---|
| DuelStrike | 10544 | 324584 | 30 | 97.5 % |
| DuelBolt | 7262 | 380149 | 60 | 114.6 % |
| DuelLeap | 3114 | 355279 | 120 | 105.2 % |
| DuelGuard | 2760 | 388152 | 160 | 113.8 % |
| DuelHeal | 1353 | 388152 | 200 | 69.7 % |

Criterio de no regresion (ANY_READY, horizon 20, Mode NONE (no key: LOCAL and BASE)): no cumple. Duplicados 0 (cumple). Inicios durante una preparacion 119 (no cumple). Ticks de espera lista-cast sin guarda 28358 (no cumple). Cadencia en alcance no cumple. Wilson del brazo A [0.734, 0.845] frente a [0.354, 0.489] de INTENT_ONLY horizonte 0 (cumple).

## Mode ORACLE(0.85) — ANY_READY, horizon 20

| Arm | Rounds | Red wins | Blue wins | Draws | Red score | Wilson 95 % | Ticks mean (sd, min-max) | Red dealt mean | Blue dealt mean | Retreats | Requests | Tokens | Duplicated casts |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | 200 | 185 | 15 | 0 | 0.925 | [0.880, 0.954] | 484 (64, 372-616) | 1.409 | 1.275 | 122 | 3866 | 2388080 | 0 |
| B | 200 | 20 | 180 | 0 | 0.100 | [0.066, 0.149] | 492 (62, 372-616) | 1.300 | 1.417 | 132 | 3888 | 2397960 | 0 |
| C | 200 | 91 | 109 | 0 | 0.455 | [0.387, 0.524] | 460 (70, 354-626) | 1.308 | 1.326 | 200 | 6898 | 4272880 | 0 |
| D | 200 | 110 | 90 | 0 | 0.550 | [0.481, 0.617] | 557 (11, 542-586) | 1.492 | 1.486 | 0 | 0 | 0 | 0 |

| Arm | Skill | Red casts | Blue casts | Red hits | Blue hits |
|---|---|---|---|---|---|
| A | DuelBolt | 1557 | 176 | 1516 | 174 |
| A | DuelGuard | 655 | 470 | 0 | 0 |
| A | DuelHeal | 299 | 303 | 0 | 0 |
| A | DuelLeap | 715 | 108 | 667 | 52 |
| A | DuelStrike | 2868 | 3076 | 2205 | 2935 |
| B | DuelBolt | 183 | 1579 | 180 | 1533 |
| B | DuelGuard | 476 | 662 | 0 | 0 |
| B | DuelHeal | 313 | 309 | 0 | 0 |
| B | DuelLeap | 115 | 721 | 40 | 674 |
| B | DuelStrike | 3122 | 2923 | 2996 | 2212 |
| C | DuelBolt | 1481 | 1475 | 1415 | 1411 |
| C | DuelGuard | 592 | 590 | 0 | 0 |
| C | DuelHeal | 271 | 283 | 0 | 0 |
| C | DuelLeap | 651 | 647 | 629 | 638 |
| C | DuelStrike | 2682 | 2684 | 2038 | 2081 |
| D | DuelBolt | 172 | 174 | 170 | 168 |
| D | DuelGuard | 600 | 600 | 0 | 0 |
| D | DuelHeal | 400 | 400 | 0 | 0 |
| D | DuelLeap | 82 | 70 | 30 | 31 |
| D | DuelStrike | 3622 | 3624 | 3468 | 3453 |

| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |
|---|---|---|---|---|
| A | 82153 | 2094 | 5052 | 7541 |
| B | 82240 | 2194 | 5423 | 8475 |
| C | 142799 | 5514 | 9458 | 26203 |
| D | 0 | 0 | 0 | 0 |

| Arm | Tactic | Windows | Mean effect |
|---|---|---|---|
| A | approach | 684 | 0.248 |
| A | guard_up | 102 | 0.000 |
| A | keep_distance_and_bolt | 352 | 0.007 |
| A | leap_in | 92 | 0.228 |
| A | press_and_strike | 614 | 0.103 |
| A | retreat_and_heal | 99 | 0.018 |
| B | approach | 724 | 0.248 |
| B | guard_up | 110 | 0.000 |
| B | keep_distance_and_bolt | 363 | 0.009 |
| B | leap_in | 109 | 0.239 |
| B | press_and_strike | 609 | 0.103 |
| B | retreat_and_heal | 107 | 0.030 |
| C | approach | 1501 | 0.248 |
| C | guard_up | 190 | 0.000 |
| C | keep_distance_and_bolt | 940 | 0.091 |
| C | leap_in | 297 | 0.243 |
| C | press_and_strike | 1229 | 0.102 |
| C | retreat_and_heal | 148 | 0.152 |

| Skill | Engine casts | Ticks in reach | Cooldown | Casts / cooldown max |
|---|---|---|---|---|
| DuelStrike | 9724 | 304398 | 30 | 95.8 % |
| DuelBolt | 6092 | 328738 | 60 | 111.2 % |
| DuelLeap | 2709 | 317109 | 120 | 102.5 % |
| DuelGuard | 2330 | 336927 | 160 | 110.6 % |
| DuelHeal | 1150 | 336927 | 200 | 68.3 % |

Criterio de no regresion (ANY_READY, horizon 20, Mode ORACLE(0.85)): no cumple. Duplicados 0 (cumple). Inicios durante una preparacion 179 (no cumple). Ticks de espera lista-cast sin guarda 35153 (no cumple). Cadencia en alcance no cumple. Wilson del brazo A [0.880, 0.954] frente a [0.142, 0.250] de INTENT_ONLY horizonte 0 (cumple).

