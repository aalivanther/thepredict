# M20 duel evidence

Overrides via DecisionTiming.withLayer. Defaults stay REACTIONS and REACTIVE. The duel pack stays casts and standard. No superiority claim.

## E1. FULL byte for byte, arms A and C, seeds 1–200

Two passes. Compared 400 digests. Mismatches 0. cumple. golden/m20 was not rewritten.

## E2. REACTIONS and FULL against CASTS

ORACLE(0.85), 200 paired seeds, arms A–D. JEV coverage is JEV ticks / (JEV + ALTERNATIVE + LOCAL) on arm A. Gate 80%. Wilson lower bound of arm A must not fall below CASTS. Objective Wilson lower > 0.5 is not a gate. The simulator does not sample MSPT; the step bound is E3. The duel world does not run MicroGoal, so FULL can match REACTIONS.

## CASTS baseline

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

Gate CASTS baseline: no cumple. Duplicates 0 (cumple). Preparation starts 367 (no cumple). Late ready ticks 37768. Arm A Wilson [0.856, 0.938] vs CASTS (cumple). Median TTK on red wins 447.0 vs CASTS 447.0 (cumple). JEV coverage 30.4% of controlled ticks (cumple). Objective Wilson lower > 0.5: seen.

## REACTIONS

| Arm | Rounds | Red wins | Blue wins | Draws | Red score | Wilson 95 % | Ticks mean (sd, min-max) | Red dealt mean | Blue dealt mean | Retreats | Requests | Tokens | Duplicated casts |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | 200 | 195 | 5 | 0 | 0.975 | [0.943, 0.989] | 482 (57, 366-618) | 1.418 | 1.271 | 129 | 1513 | 1059100 | 0 |
| B | 200 | 1 | 199 | 0 | 0.005 | [0.001, 0.028] | 485 (56, 380-737) | 1.277 | 1.420 | 135 | 1486 | 1040200 | 0 |
| C | 200 | 105 | 95 | 0 | 0.525 | [0.456, 0.593] | 546 (65, 394-676) | 1.479 | 1.485 | 341 | 3135 | 2194500 | 0 |
| D | 200 | 110 | 90 | 0 | 0.550 | [0.481, 0.617] | 557 (11, 542-586) | 1.492 | 1.486 | 0 | 0 | 0 | 0 |

| Arm | Skill | Red casts | Blue casts | Red hits | Blue hits |
|---|---|---|---|---|---|
| A | DuelBolt | 1527 | 180 | 1447 | 176 |
| A | DuelGuard | 667 | 462 | 0 | 0 |
| A | DuelHeal | 307 | 325 | 0 | 0 |
| A | DuelLeap | 681 | 124 | 535 | 56 |
| A | DuelStrike | 2795 | 3046 | 2322 | 2923 |
| B | DuelBolt | 179 | 1540 | 174 | 1439 |
| B | DuelGuard | 463 | 668 | 0 | 0 |
| B | DuelHeal | 325 | 313 | 0 | 0 |
| B | DuelLeap | 115 | 698 | 41 | 545 |
| B | DuelStrike | 3075 | 2810 | 2943 | 2327 |
| C | DuelBolt | 1748 | 1743 | 1641 | 1623 |
| C | DuelGuard | 740 | 739 | 0 | 0 |
| C | DuelHeal | 364 | 358 | 0 | 0 |
| C | DuelLeap | 699 | 712 | 573 | 571 |
| C | DuelStrike | 3086 | 3083 | 2335 | 2362 |
| D | DuelBolt | 172 | 174 | 170 | 168 |
| D | DuelGuard | 600 | 600 | 0 | 0 |
| D | DuelHeal | 400 | 400 | 0 | 0 |
| D | DuelLeap | 82 | 70 | 30 | 31 |
| D | DuelStrike | 3622 | 3624 | 3468 | 3453 |

| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |
|---|---|---|---|---|
| A | 29596 | 1291 | 58418 | 7126 |
| B | 28840 | 1338 | 59637 | 7125 |
| C | 50780 | 3756 | 122950 | 40912 |
| D | 0 | 0 | 0 | 0 |

| Arm | Tactic | Windows | Mean effect |
|---|---|---|---|
| A | approach | 1052 | 0.248 |
| A | guard_up | 206 | 0.000 |
| A | keep_distance_and_bolt | 1157 | 0.002 |
| A | leap_in | 526 | 0.244 |
| A | press_and_strike | 1693 | 0.156 |
| A | retreat_and_heal | 93 | 0.008 |
| B | approach | 1052 | 0.246 |
| B | guard_up | 205 | 0.000 |
| B | keep_distance_and_bolt | 1141 | 0.002 |
| B | leap_in | 545 | 0.245 |
| B | press_and_strike | 1717 | 0.156 |
| B | retreat_and_heal | 97 | 0.013 |
| C | approach | 1996 | 0.245 |
| C | guard_up | 295 | 0.000 |
| C | keep_distance_and_bolt | 3038 | 0.103 |
| C | leap_in | 1437 | 0.209 |
| C | press_and_strike | 3926 | 0.166 |
| C | retreat_and_heal | 354 | 0.102 |

Gate REACTIONS: no cumple. Duplicates 0 (cumple). Preparation starts 798 (no cumple). Late ready ticks 14037. Arm A Wilson [0.943, 0.989] vs CASTS (cumple). Median TTK on red wins 474.0 vs CASTS 447.0 (no cumple). JEV coverage 33.1% of controlled ticks (no cumple — risk: JEV covers under 80% of controlled ticks). Objective Wilson lower > 0.5: seen.

## FULL

| Arm | Rounds | Red wins | Blue wins | Draws | Red score | Wilson 95 % | Ticks mean (sd, min-max) | Red dealt mean | Blue dealt mean | Retreats | Requests | Tokens | Duplicated casts |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | 200 | 195 | 5 | 0 | 0.975 | [0.943, 0.989] | 482 (57, 366-618) | 1.418 | 1.271 | 129 | 1513 | 1059100 | 0 |
| B | 200 | 1 | 199 | 0 | 0.005 | [0.001, 0.028] | 485 (56, 380-737) | 1.277 | 1.420 | 135 | 1486 | 1040200 | 0 |
| C | 200 | 105 | 95 | 0 | 0.525 | [0.456, 0.593] | 546 (65, 394-676) | 1.479 | 1.485 | 341 | 3135 | 2194500 | 0 |
| D | 200 | 110 | 90 | 0 | 0.550 | [0.481, 0.617] | 557 (11, 542-586) | 1.492 | 1.486 | 0 | 0 | 0 | 0 |

| Arm | Skill | Red casts | Blue casts | Red hits | Blue hits |
|---|---|---|---|---|---|
| A | DuelBolt | 1527 | 180 | 1447 | 176 |
| A | DuelGuard | 667 | 462 | 0 | 0 |
| A | DuelHeal | 307 | 325 | 0 | 0 |
| A | DuelLeap | 681 | 124 | 535 | 56 |
| A | DuelStrike | 2795 | 3046 | 2322 | 2923 |
| B | DuelBolt | 179 | 1540 | 174 | 1439 |
| B | DuelGuard | 463 | 668 | 0 | 0 |
| B | DuelHeal | 325 | 313 | 0 | 0 |
| B | DuelLeap | 115 | 698 | 41 | 545 |
| B | DuelStrike | 3075 | 2810 | 2943 | 2327 |
| C | DuelBolt | 1748 | 1743 | 1641 | 1623 |
| C | DuelGuard | 740 | 739 | 0 | 0 |
| C | DuelHeal | 364 | 358 | 0 | 0 |
| C | DuelLeap | 699 | 712 | 573 | 571 |
| C | DuelStrike | 3086 | 3083 | 2335 | 2362 |
| D | DuelBolt | 172 | 174 | 170 | 168 |
| D | DuelGuard | 600 | 600 | 0 | 0 |
| D | DuelHeal | 400 | 400 | 0 | 0 |
| D | DuelLeap | 82 | 70 | 30 | 31 |
| D | DuelStrike | 3622 | 3624 | 3468 | 3453 |

| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |
|---|---|---|---|---|
| A | 29596 | 1291 | 58418 | 7126 |
| B | 28840 | 1338 | 59637 | 7125 |
| C | 50780 | 3756 | 122950 | 40912 |
| D | 0 | 0 | 0 | 0 |

| Arm | Tactic | Windows | Mean effect |
|---|---|---|---|
| A | approach | 1052 | 0.248 |
| A | guard_up | 206 | 0.000 |
| A | keep_distance_and_bolt | 1157 | 0.002 |
| A | leap_in | 526 | 0.244 |
| A | press_and_strike | 1693 | 0.156 |
| A | retreat_and_heal | 93 | 0.008 |
| B | approach | 1052 | 0.246 |
| B | guard_up | 205 | 0.000 |
| B | keep_distance_and_bolt | 1141 | 0.002 |
| B | leap_in | 545 | 0.245 |
| B | press_and_strike | 1717 | 0.156 |
| B | retreat_and_heal | 97 | 0.013 |
| C | approach | 1996 | 0.245 |
| C | guard_up | 295 | 0.000 |
| C | keep_distance_and_bolt | 3038 | 0.103 |
| C | leap_in | 1437 | 0.209 |
| C | press_and_strike | 3926 | 0.166 |
| C | retreat_and_heal | 354 | 0.102 |

Gate FULL: no cumple. Duplicates 0 (cumple). Preparation starts 798 (no cumple). Late ready ticks 14037. Arm A Wilson [0.943, 0.989] vs CASTS (cumple). Median TTK on red wins 474.0 vs CASTS 447.0 (no cumple). JEV coverage 33.1% of controlled ticks (no cumple — risk: JEV covers under 80% of controlled ticks). Objective Wilson lower > 0.5: seen.

Measured in 3.4 s on this machine.
