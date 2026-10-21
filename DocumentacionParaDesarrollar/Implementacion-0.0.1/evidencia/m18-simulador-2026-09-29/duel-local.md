# Local duel matrix (M18.7)

Simulator of M18.7 with the real engine, the classic duelist and the skills of the duel pack. It measures the path and claims no superiority (plan §12). 200 seeds per arm; the same seeds in every arm; spawn points swapped every two rounds.

## Mode NONE (no key: LOCAL and BASE)

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
| A | DuelLeap | 389 | 163 | 389 | 74 |
| A | DuelStrike | 2740 | 3655 | 2515 | 3512 |
| B | DuelBolt | 184 | 1554 | 181 | 1383 |
| B | DuelGuard | 589 | 686 | 0 | 0 |
| B | DuelHeal | 400 | 389 | 0 | 0 |
| B | DuelLeap | 175 | 365 | 73 | 365 |
| B | DuelStrike | 3665 | 2737 | 3517 | 2515 |
| C | DuelBolt | 1629 | 1625 | 1475 | 1455 |
| C | DuelGuard | 604 | 603 | 0 | 0 |
| C | DuelHeal | 404 | 404 | 0 | 0 |
| C | DuelLeap | 679 | 681 | 653 | 665 |
| C | DuelStrike | 2679 | 2665 | 2391 | 2406 |
| D | DuelBolt | 172 | 174 | 170 | 168 |
| D | DuelGuard | 600 | 600 | 0 | 0 |
| D | DuelHeal | 400 | 400 | 0 | 0 |
| D | DuelLeap | 82 | 70 | 30 | 31 |
| D | DuelStrike | 3622 | 3624 | 3468 | 3453 |

| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |
|---|---|---|---|---|
| A | 0 | 0 | 92228 | 21621 |
| B | 0 | 0 | 92541 | 21281 |
| C | 0 | 0 | 182591 | 55121 |
| D | 0 | 0 | 0 | 0 |

| Arm | Tactic | Windows | Mean effect |
|---|---|---|---|
| A | guard_up | 459 | 0.000 |
| A | keep_distance_and_bolt | 1548 | 0.013 |
| A | leap_in | 389 | 0.250 |
| A | press_and_strike | 2062 | 0.226 |
| A | retreat_and_heal | 394 | 0.039 |
| B | guard_up | 468 | 0.000 |
| B | keep_distance_and_bolt | 1553 | 0.012 |
| B | leap_in | 365 | 0.250 |
| B | press_and_strike | 2075 | 0.226 |
| B | retreat_and_heal | 389 | 0.039 |
| C | approach | 42 | 0.232 |
| C | guard_up | 800 | 0.000 |
| C | keep_distance_and_bolt | 3289 | 0.130 |
| C | leap_in | 1283 | 0.250 |
| C | press_and_strike | 3747 | 0.220 |
| C | retreat_and_heal | 808 | 0.236 |

## Mode ORACLE(0.85)

| Arm | Rounds | Red wins | Blue wins | Draws | Red score | Wilson 95 % | Ticks mean (sd, min-max) | Red dealt mean | Blue dealt mean | Retreats | Requests | Tokens | Duplicated casts |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | 200 | 22 | 178 | 0 | 0.110 | [0.074, 0.161] | 552 (32, 458-623) | 1.317 | 1.488 | 367 | 3718 | 2602240 | 0 |
| B | 200 | 173 | 27 | 0 | 0.865 | [0.811, 0.906] | 551 (32, 461-629) | 1.486 | 1.324 | 366 | 3704 | 2592320 | 0 |
| C | 200 | 96 | 104 | 0 | 0.480 | [0.412, 0.549] | 649 (61, 493-827) | 1.468 | 1.474 | 828 | 8822 | 6172280 | 0 |
| D | 200 | 110 | 90 | 0 | 0.550 | [0.481, 0.617] | 557 (11, 542-586) | 1.492 | 1.486 | 0 | 0 | 0 | 0 |

| Arm | Skill | Red casts | Blue casts | Red hits | Blue hits |
|---|---|---|---|---|---|
| A | DuelBolt | 1314 | 177 | 1194 | 175 |
| A | DuelGuard | 581 | 568 | 0 | 0 |
| A | DuelHeal | 365 | 391 | 0 | 0 |
| A | DuelLeap | 404 | 174 | 387 | 83 |
| A | DuelStrike | 2489 | 3565 | 2290 | 3433 |
| B | DuelBolt | 177 | 1301 | 174 | 1170 |
| B | DuelGuard | 568 | 579 | 0 | 0 |
| B | DuelHeal | 393 | 366 | 0 | 0 |
| B | DuelLeap | 164 | 394 | 70 | 385 |
| B | DuelStrike | 3567 | 2525 | 3434 | 2322 |
| C | DuelBolt | 1540 | 1531 | 1412 | 1409 |
| C | DuelGuard | 647 | 658 | 0 | 0 |
| C | DuelHeal | 403 | 403 | 0 | 0 |
| C | DuelLeap | 682 | 676 | 605 | 613 |
| C | DuelStrike | 2657 | 2649 | 2433 | 2447 |
| D | DuelBolt | 172 | 174 | 170 | 168 |
| D | DuelGuard | 600 | 600 | 0 | 0 |
| D | DuelHeal | 400 | 400 | 0 | 0 |
| D | DuelLeap | 82 | 70 | 30 | 31 |
| D | DuelStrike | 3622 | 3624 | 3468 | 3453 |

| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |
|---|---|---|---|---|
| A | 78840 | 2340 | 11010 | 18171 |
| B | 78430 | 2142 | 11195 | 18373 |
| C | 187830 | 9396 | 22369 | 39837 |
| D | 0 | 0 | 0 | 0 |

| Arm | Tactic | Windows | Mean effect |
|---|---|---|---|
| A | approach | 367 | 0.245 |
| A | guard_up | 404 | 0.000 |
| A | keep_distance_and_bolt | 1309 | 0.008 |
| A | leap_in | 427 | 0.248 |
| A | press_and_strike | 1910 | 0.202 |
| A | retreat_and_heal | 356 | 0.025 |
| B | approach | 349 | 0.244 |
| B | guard_up | 405 | 0.000 |
| B | keep_distance_and_bolt | 1292 | 0.009 |
| B | leap_in | 408 | 0.248 |
| B | press_and_strike | 1921 | 0.204 |
| B | retreat_and_heal | 362 | 0.029 |
| C | approach | 993 | 0.231 |
| C | guard_up | 929 | 0.000 |
| C | keep_distance_and_bolt | 3063 | 0.121 |
| C | leap_in | 1295 | 0.249 |
| C | press_and_strike | 3981 | 0.199 |
| C | retreat_and_heal | 831 | 0.104 |

## Level 4 equivalent, not identical (M18.3)

Red of arm A stops perceiving its rival at tick 200 of the round: it reaches level 4 after 0.5 ticks on average (sd 0.5, min 0, max 1; 200 of 200 rounds lasted that long). From then on it plays the classic rules; before, it keeps its last local intent. With level 4 forced from the first tick it casts exactly as the classic duelist (DuelSimulatorTest).

## Cost of the tap without a recording (M18.4)

| Engine tap | ns per simulated tick |
|---|---|
| DecisionTap.NONE | 2078 |
| SwitchableDecisionTap, detached | 1856 |

The same 40 rounds of arm C with the oracle, three passes each; the difference is noise of this machine: without a recording each call is one volatile read and no event is built.

## One recorded session (M18.4 limits)

| Recording | File | Bytes |
|---|---|---|
| local-sim-20260929-120000 | decisions.jsonl | 409540 |
| local-sim-20260929-120000 | samples.csv | 2991500 |
| local-sim-20260929-120000 | casts.csv | 685794 |
| local-sim-20260929-120000 | damage.csv | 444000 |
| local-sim-20260929-120000 | intents.csv | 244814 |
| local-sim-20260929-120000 | defense.csv | 28934 |
| local-sim-20260929-120000 | retreats.csv | 10185 |
| local-sim-20260929-120000 | threat.csv | 54 |
| local-sim-20260929-120000 | outcomes.csv | 214943 |
| local-sim-20260929-120000 | rounds.csv | 11280 |
| local-sim-20260929-120000 | summary.md | 3501 |
| oracle-sim-20260929-120000 | decisions.jsonl | 2087569 |
| oracle-sim-20260929-120000 | samples.csv | 1187677 |
| oracle-sim-20260929-120000 | casts.csv | 282014 |
| oracle-sim-20260929-120000 | damage.csv | 164853 |
| oracle-sim-20260929-120000 | intents.csv | 140746 |
| oracle-sim-20260929-120000 | defense.csv | 10472 |
| oracle-sim-20260929-120000 | retreats.csv | 4892 |
| oracle-sim-20260929-120000 | threat.csv | 54 |
| oracle-sim-20260929-120000 | outcomes.csv | 111045 |
| oracle-sim-20260929-120000 | rounds.csv | 4410 |
| oracle-sim-20260929-120000 | summary.md | 3228 |

Measured in 2.6 s on this machine.
