# M16 re-bench on the labelled bank, recomputed with the current labels

Source DocumentacionParaDesarrollar/Implementacion-0.0.1/evidencia/m16-rebanco-2026-09-30/P2/answers.jsonl: 600 recorded calls, no new call. Prepared again from the bank, 600 of 600 bodies have the recorded size.

## Tokens, cost and failures per arm

| Arm | Calls | Failures | Body chars per call | Input tokens per call | Characters per token | Input tokens | USD |
|---|---|---|---|---|---|---|---|
| A | 120 | 3 | 2116 | 857 | 2.47 | 100383 | 0.004216 |
| B | 120 | 0 | 1195 | 673 | 1.77 | 80876 | 0.003397 |
| C | 120 | 0 | 2329 | 1026 | 2.27 | 123154 | 0.005172 |
| D | 120 | 0 | 2631 | 1111 | 2.37 | 133324 | 0.005600 |
| E | 120 | 0 | 5277 | 1935 | 2.73 | 232204 | 0.009753 |


Production request (1 mob, 1 participant, NOW plus one contingency, 7 options of the example) per arm: the mean tokens of a NOW call plus those of a contingency call, minus one state, which a production request carries once.
| Arm | Estimated real tokens of the production request |
|---|---|
| A | 1388 |
| B | 953 |
| C | 1701 |
| D | 1884 |
| E | 2950 |


## Per scenario (each distinct scenario weighs one)

| Scenario | Arm | Label | Modal best | Mean confidence | Changes between repetitions | Judged |
|---|---|---|---|---|---|---|
| bank-melee-01 | A | REVIEWED | press_and_strike | 0.880 | 0 | best |
| bank-melee-01 | B | REVIEWED | press_and_strike | 0.645 | 0 | best |
| bank-melee-01 | C | REVIEWED | press_and_strike | 0.965 | 0 | best |
| bank-melee-01 | D | REVIEWED | press_and_strike | 0.970 | 0 | best |
| bank-melee-01 | E | REVIEWED | press_and_strike | 0.965 | 0 | best |
| bank-melee-02 | A | REVIEWED | press_and_strike | 0.935 | 0 | best |
| bank-melee-02 | B | REVIEWED | press_and_strike | 0.625 | 0 | best |
| bank-melee-02 | C | REVIEWED | press_and_strike | 0.930 | 0 | best |
| bank-melee-02 | D | REVIEWED | press_and_strike | 0.890 | 0 | best |
| bank-melee-02 | E | REVIEWED | press_and_strike | 0.885 | 0 | best |
| bank-melee-03 | A | REVIEWED | press_and_strike | 0.450 | 0 | best |
| bank-melee-03 | B | REVIEWED | press_and_strike | 0.580 | 0 | best |
| bank-melee-03 | C | REVIEWED | press_and_strike | 0.905 | 0 | best |
| bank-melee-03 | D | REVIEWED | press_and_strike | 0.925 | 0 | best |
| bank-melee-03 | E | REVIEWED | press_and_strike | 0.910 | 0 | best |
| bank-melee-04 | A | REVIEWED | press_and_strike | 0.975 | 0 | best |
| bank-melee-04 | B | REVIEWED | press_and_strike | 0.855 | 0 | best |
| bank-melee-04 | C | REVIEWED | press_and_strike | 0.985 | 0 | best |
| bank-melee-04 | D | REVIEWED | press_and_strike | 0.980 | 0 | best |
| bank-melee-04 | E | REVIEWED | press_and_strike | 0.980 | 0 | best |
| bank-melee-05 | A | REVIEWED | press_and_strike | 0.950 | 0 | best |
| bank-melee-05 | B | REVIEWED | press_and_strike | 0.640 | 0 | best |
| bank-melee-05 | C | REVIEWED | press_and_strike | 0.935 | 0 | best |
| bank-melee-05 | D | REVIEWED | press_and_strike | 0.935 | 0 | best |
| bank-melee-05 | E | REVIEWED | press_and_strike | 0.935 | 0 | best |
| bank-melee-06 | A | REVIEWED | press_and_strike | 0.675 | 0 | best |
| bank-melee-06 | B | REVIEWED | press_and_strike | 0.720 | 0 | best |
| bank-melee-06 | C | REVIEWED | press_and_strike | 0.930 | 0 | best |
| bank-melee-06 | D | REVIEWED | press_and_strike | 0.945 | 0 | best |
| bank-melee-06 | E | REVIEWED | press_and_strike | 0.945 | 0 | best |
| bank-shooter-01 | A | REVIEWED | keep_distance_and_shoot | 0.980 | 0 | acceptable |
| bank-shooter-01 | B | REVIEWED | keep_distance_and_shoot | 0.220 | 0 | acceptable |
| bank-shooter-01 | C | REVIEWED | keep_distance_and_shoot | 0.970 | 0 | acceptable |
| bank-shooter-01 | D | REVIEWED | keep_distance_and_shoot | 0.970 | 0 | acceptable |
| bank-shooter-01 | E | REVIEWED | keep_distance_and_shoot | 0.950 | 0 | acceptable |
| bank-shooter-02 | A | REVIEWED | keep_distance_and_shoot | 0.980 | 0 | best |
| bank-shooter-02 | B | REVIEWED | keep_distance_and_shoot | 0.240 | 0 | best |
| bank-shooter-02 | C | REVIEWED | keep_distance_and_shoot | 0.970 | 0 | best |
| bank-shooter-02 | D | REVIEWED | keep_distance_and_shoot | 0.970 | 0 | best |
| bank-shooter-02 | E | REVIEWED | keep_distance_and_shoot | 0.965 | 0 | best |
| bank-shooter-03 | A | REVIEWED | keep_distance_and_shoot | 0.985 | 0 | acceptable |
| bank-shooter-03 | B | REVIEWED | keep_distance_and_shoot | 0.330 | 0 | acceptable |
| bank-shooter-03 | C | REVIEWED | keep_distance_and_shoot | 0.970 | 0 | acceptable |
| bank-shooter-03 | D | REVIEWED | keep_distance_and_shoot | 0.960 | 0 | acceptable |
| bank-shooter-03 | E | REVIEWED | keep_distance_and_shoot | 0.965 | 0 | acceptable |
| bank-shooter-04 | A | REVIEWED | keep_distance_and_shoot | 0.990 | 0 | best |
| bank-shooter-04 | B | REVIEWED | keep_distance_and_shoot | 0.325 | 0 | best |
| bank-shooter-04 | C | REVIEWED | keep_distance_and_shoot | 0.970 | 0 | best |
| bank-shooter-04 | D | REVIEWED | keep_distance_and_shoot | 0.970 | 0 | best |
| bank-shooter-04 | E | REVIEWED | keep_distance_and_shoot | 0.975 | 0 | best |
| bank-shooter-05 | A | REVIEWED | keep_distance_and_shoot | 0.990 | 0 | acceptable |
| bank-shooter-05 | B | REVIEWED | keep_distance_and_shoot | 0.375 | 0 | acceptable |
| bank-shooter-05 | C | REVIEWED | keep_distance_and_shoot | 0.975 | 0 | acceptable |
| bank-shooter-05 | D | REVIEWED | keep_distance_and_shoot | 0.975 | 0 | acceptable |
| bank-shooter-05 | E | REVIEWED | keep_distance_and_shoot | 0.965 | 0 | acceptable |
| bank-shooter-06 | A | REVIEWED | keep_distance_and_shoot | 0.990 | 0 | best |
| bank-shooter-06 | B | REVIEWED | keep_distance_and_shoot | 0.325 | 0 | best |
| bank-shooter-06 | C | REVIEWED | keep_distance_and_shoot | 0.975 | 0 | best |
| bank-shooter-06 | D | REVIEWED | keep_distance_and_shoot | 0.970 | 0 | best |
| bank-shooter-06 | E | REVIEWED | keep_distance_and_shoot | 0.970 | 0 | best |
| bank-shield-01 | A | REVIEWED | break_guard | 1.000 | 0 | best |
| bank-shield-01 | B | REVIEWED | break_guard | 0.940 | 0 | best |
| bank-shield-01 | C | REVIEWED | break_guard | 0.985 | 0 | best |
| bank-shield-01 | D | REVIEWED | break_guard | 0.990 | 0 | best |
| bank-shield-01 | E | REVIEWED | break_guard | 0.985 | 0 | best |
| bank-shield-02 | A | REVIEWED | keep_distance_and_shoot | 0.745 | 0 | not acceptable |
| bank-shield-02 | B | REVIEWED | flank_and_strike | 0.905 | 0 | best |
| bank-shield-02 | C | REVIEWED | keep_distance_and_shoot | 0.680 | 0 | not acceptable |
| bank-shield-02 | D | REVIEWED | flank_and_strike | 0.455 | 0 | best |
| bank-shield-02 | E | REVIEWED | flank_and_strike | 0.400 | 0 | best |
| bank-shield-03 | A | REVIEWED | break_guard | 0.975 | 0 | best |
| bank-shield-03 | B | REVIEWED | break_guard | 0.870 | 0 | best |
| bank-shield-03 | C | REVIEWED | break_guard | 0.935 | 0 | best |
| bank-shield-03 | D | REVIEWED | break_guard | 0.965 | 0 | best |
| bank-shield-03 | E | REVIEWED | break_guard | 0.965 | 0 | best |
| bank-shield-04 | A | REVIEWED | keep_distance_and_shoot | 0.670 | 0 | not acceptable |
| bank-shield-04 | B | REVIEWED | flank_and_strike | 0.890 | 0 | best |
| bank-shield-04 | C | REVIEWED | keep_distance_and_shoot | 0.605 | 0 | not acceptable |
| bank-shield-04 | D | REVIEWED | keep_distance_and_shoot | 0.405 | 0 | not acceptable |
| bank-shield-04 | E | REVIEWED | keep_distance_and_shoot | 0.420 | 0 | not acceptable |
| bank-shield-05 | B | REVIEWED | break_guard | 0.295 | 1 | acceptable |
| bank-shield-05 | C | REVIEWED | press | 0.570 | 0 | best |
| bank-shield-05 | D | REVIEWED | press | 0.510 | 0 | best |
| bank-shield-05 | E | REVIEWED | press | 0.530 | 0 | best |
| bank-shield-06 | A | REVIEWED | keep_distance_and_shoot | 0.760 | 0 | not acceptable |
| bank-shield-06 | B | REVIEWED | flank_and_strike | 0.535 | 0 | acceptable |
| bank-shield-06 | C | REVIEWED | keep_distance_and_shoot | 0.715 | 0 | not acceptable |
| bank-shield-06 | D | REVIEWED | keep_distance_and_shoot | 0.820 | 0 | not acceptable |
| bank-shield-06 | E | REVIEWED | keep_distance_and_shoot | 0.800 | 0 | not acceptable |
| bank-lowhp-01 | A | REVIEWED | retreat_and_defend | 0.830 | 0 | best |
| bank-lowhp-01 | B | REVIEWED | retreat_and_defend | 0.415 | 0 | best |
| bank-lowhp-01 | C | REVIEWED | retreat_and_defend | 0.735 | 0 | best |
| bank-lowhp-01 | D | REVIEWED | retreat_and_defend | 0.775 | 0 | best |
| bank-lowhp-01 | E | REVIEWED | retreat_and_defend | 0.770 | 0 | best |
| bank-lowhp-02 | A | REVIEWED | retreat_and_defend | 0.585 | 0 | best |
| bank-lowhp-02 | B | REVIEWED | retreat_and_defend | 0.290 | 0 | best |
| bank-lowhp-02 | C | REVIEWED | retreat_and_defend | 0.605 | 0 | best |
| bank-lowhp-02 | D | REVIEWED | retreat_and_defend | 0.660 | 0 | best |
| bank-lowhp-02 | E | REVIEWED | retreat_and_defend | 0.605 | 0 | best |
| bank-lowhp-03 | A | REVIEWED | retreat_and_defend | 0.450 | 0 | acceptable |
| bank-lowhp-03 | B | REVIEWED | press_and_strike | 0.720 | 0 | best |
| bank-lowhp-03 | C | REVIEWED | press_and_strike | 0.480 | 0 | best |
| bank-lowhp-03 | D | REVIEWED | retreat_and_defend | 0.490 | 0 | acceptable |
| bank-lowhp-03 | E | REVIEWED | retreat_and_defend | 0.465 | 0 | acceptable |
| bank-lowhp-04 | A | REVIEWED | keep_distance_and_shoot | 0.450 | 0 | best |
| bank-lowhp-04 | B | REVIEWED | keep_distance_and_shoot | 0.385 | 0 | best |
| bank-lowhp-04 | C | REVIEWED | keep_distance_and_shoot | 0.470 | 0 | best |
| bank-lowhp-04 | D | REVIEWED | retreat_and_defend | 0.480 | 0 | acceptable |
| bank-lowhp-04 | E | REVIEWED | retreat_and_defend | 0.505 | 0 | acceptable |
| bank-lowhp-05 | A | REVIEWED | retreat_and_defend | 0.425 | 0 | best |
| bank-lowhp-05 | B | REVIEWED | keep_distance_and_shoot | 0.180 | 0 | acceptable |
| bank-lowhp-05 | C | REVIEWED | keep_distance_and_shoot | 0.495 | 0 | acceptable |
| bank-lowhp-05 | D | REVIEWED | keep_distance_and_shoot | 0.470 | 0 | acceptable |
| bank-lowhp-05 | E | REVIEWED | keep_distance_and_shoot | 0.420 | 1 | acceptable |
| bank-lowhp-06 | A | REVIEWED | keep_distance_and_shoot | 0.750 | 0 | best |
| bank-lowhp-06 | B | REVIEWED | keep_distance_and_shoot | 0.395 | 0 | best |
| bank-lowhp-06 | C | REVIEWED | keep_distance_and_shoot | 0.765 | 0 | best |
| bank-lowhp-06 | D | REVIEWED | keep_distance_and_shoot | 0.635 | 0 | best |
| bank-lowhp-06 | E | REVIEWED | keep_distance_and_shoot | 0.655 | 0 | best |
| bank-pillar-01 | A | REVIEWED | keep_distance_and_shoot | 0.955 | 0 | best |
| bank-pillar-01 | B | REVIEWED | keep_distance_and_shoot | 0.780 | 0 | best |
| bank-pillar-01 | C | REVIEWED | keep_distance_and_shoot | 0.900 | 0 | best |
| bank-pillar-01 | D | REVIEWED | keep_distance_and_shoot | 0.955 | 0 | best |
| bank-pillar-01 | E | REVIEWED | keep_distance_and_shoot | 0.960 | 0 | best |
| bank-pillar-02 | A | REVIEWED | keep_distance_and_shoot | 0.470 | 0 | not acceptable |
| bank-pillar-02 | B | REVIEWED | keep_distance | 0.335 | 0 | best |
| bank-pillar-02 | C | REVIEWED | keep_distance_and_shoot | 0.380 | 0 | not acceptable |
| bank-pillar-02 | D | REVIEWED | keep_distance_and_shoot | 0.320 | 0 | not acceptable |
| bank-pillar-02 | E | REVIEWED | keep_distance_and_shoot | 0.260 | 0 | not acceptable |
| bank-pillar-03 | A | REVIEWED | keep_distance_and_shoot | 0.605 | 0 | best |
| bank-pillar-03 | B | REVIEWED | keep_distance_and_shoot | 0.775 | 0 | best |
| bank-pillar-03 | C | REVIEWED | keep_distance_and_shoot | 0.565 | 0 | best |
| bank-pillar-03 | D | REVIEWED | keep_distance_and_shoot | 0.695 | 0 | best |
| bank-pillar-03 | E | REVIEWED | keep_distance_and_shoot | 0.705 | 0 | best |
| bank-pillar-04 | A | REVIEWED | approach | 0.330 | 0 | FORBIDDEN |
| bank-pillar-04 | B | REVIEWED | hold | 0.305 | 0 | not acceptable |
| bank-pillar-04 | C | REVIEWED | approach | 0.530 | 0 | FORBIDDEN |
| bank-pillar-04 | D | REVIEWED | hold | 0.550 | 0 | not acceptable |
| bank-pillar-04 | E | REVIEWED | hold | 0.540 | 0 | not acceptable |
| bank-grouped-01 | A | REVIEWED | press_and_strike | 0.415 | 0 | acceptable |
| bank-grouped-01 | B | REVIEWED | press_and_strike | 0.590 | 0 | acceptable |
| bank-grouped-01 | C | REVIEWED | press_and_strike | 0.575 | 0 | acceptable |
| bank-grouped-01 | D | REVIEWED | press_and_strike | 0.650 | 0 | acceptable |
| bank-grouped-01 | E | REVIEWED | press_and_strike | 0.605 | 0 | acceptable |
| bank-grouped-02 | A | REVIEWED | press_and_strike | 0.440 | 0 | acceptable |
| bank-grouped-02 | B | REVIEWED | press_and_strike | 0.560 | 0 | acceptable |
| bank-grouped-02 | C | REVIEWED | press_and_strike | 0.560 | 0 | acceptable |
| bank-grouped-02 | D | REVIEWED | press_and_strike | 0.550 | 0 | acceptable |
| bank-grouped-02 | E | REVIEWED | press_and_strike | 0.575 | 0 | acceptable |
| bank-grouped-03 | A | REVIEWED | press_and_strike | 0.245 | 0 | acceptable |
| bank-grouped-03 | B | REVIEWED | retreat_and_defend | 0.360 | 0 | not acceptable |
| bank-grouped-03 | C | REVIEWED | press_and_strike | 0.335 | 0 | acceptable |
| bank-grouped-03 | D | REVIEWED | press_and_strike | 0.485 | 0 | acceptable |
| bank-grouped-03 | E | REVIEWED | press_and_strike | 0.455 | 0 | acceptable |
| bank-grouped-04 | A | REVIEWED | press_and_strike | 0.280 | 0 | acceptable |
| bank-grouped-04 | B | REVIEWED | retreat_and_defend | 0.410 | 0 | acceptable |
| bank-grouped-04 | C | REVIEWED | press_and_strike | 0.280 | 0 | acceptable |
| bank-grouped-04 | D | REVIEWED | press_and_strike | 0.405 | 0 | acceptable |
| bank-grouped-04 | E | REVIEWED | press_and_strike | 0.435 | 0 | acceptable |
| bank-finisher-01 | A | REVIEWED | execute | 0.965 | 0 | best |
| bank-finisher-01 | B | REVIEWED | kite_and_shoot | 0.370 | 0 | acceptable |
| bank-finisher-01 | C | REVIEWED | execute | 0.975 | 0 | best |
| bank-finisher-01 | D | REVIEWED | execute | 0.975 | 0 | best |
| bank-finisher-01 | E | REVIEWED | execute | 0.970 | 0 | best |
| bank-finisher-02 | A | REVIEWED | execute | 0.460 | 0 | acceptable |
| bank-finisher-02 | B | REVIEWED | kite_and_shoot | 0.745 | 0 | best |
| bank-finisher-02 | C | REVIEWED | kite_and_shoot | 0.415 | 1 | best |
| bank-finisher-02 | D | REVIEWED | kite_and_shoot | 0.445 | 0 | best |
| bank-finisher-02 | E | REVIEWED | kite_and_shoot | 0.430 | 1 | best |
| bank-finisher-03 | A | REVIEWED | kite_and_shoot | 0.395 | 0 | acceptable |
| bank-finisher-03 | B | REVIEWED | kite_and_shoot | 0.255 | 0 | acceptable |
| bank-finisher-03 | C | REVIEWED | kite_and_shoot | 0.420 | 0 | acceptable |
| bank-finisher-03 | D | REVIEWED | execute | 0.325 | 1 | acceptable |
| bank-finisher-03 | E | REVIEWED | execute | 0.310 | 0 | acceptable |
| bank-finisher-04 | A | REVIEWED | kite_and_shoot | 0.555 | 0 | acceptable |
| bank-finisher-04 | B | REVIEWED | close_in | 0.510 | 0 | acceptable |
| bank-finisher-04 | C | REVIEWED | kite_and_shoot | 0.695 | 0 | acceptable |
| bank-finisher-04 | D | REVIEWED | lunge_in | 0.475 | 0 | best |
| bank-finisher-04 | E | REVIEWED | lunge_in | 0.450 | 0 | best |
| bank-drinking-01 | A | REVIEWED | interrupt_shot | 0.900 | 0 | best |
| bank-drinking-01 | B | REVIEWED | kite_and_shoot | 0.830 | 0 | acceptable |
| bank-drinking-01 | C | REVIEWED | interrupt_shot | 0.840 | 0 | best |
| bank-drinking-01 | D | REVIEWED | interrupt_shot | 0.735 | 0 | best |
| bank-drinking-01 | E | REVIEWED | interrupt_shot | 0.740 | 0 | best |
| bank-drinking-02 | A | REVIEWED | interrupt_shot | 0.935 | 0 | best |
| bank-drinking-02 | B | REVIEWED | kite_and_shoot | 0.570 | 0 | not acceptable |
| bank-drinking-02 | C | REVIEWED | interrupt_shot | 0.890 | 0 | best |
| bank-drinking-02 | D | REVIEWED | interrupt_shot | 0.870 | 0 | best |
| bank-drinking-02 | E | REVIEWED | interrupt_shot | 0.885 | 0 | best |
| bank-drinking-03 | A | REVIEWED | interrupt_shot | 0.870 | 0 | best |
| bank-drinking-03 | B | REVIEWED | kite_and_shoot | 0.330 | 0 | acceptable |
| bank-drinking-03 | C | REVIEWED | interrupt_shot | 0.745 | 0 | best |
| bank-drinking-03 | D | REVIEWED | interrupt_shot | 0.845 | 0 | best |
| bank-drinking-03 | E | REVIEWED | interrupt_shot | 0.845 | 0 | best |
| bank-drinking-04 | A | REVIEWED | interrupt_shot | 0.785 | 0 | acceptable |
| bank-drinking-04 | B | REVIEWED | kite_and_shoot | 0.530 | 0 | acceptable |
| bank-drinking-04 | C | REVIEWED | interrupt_shot | 0.565 | 0 | acceptable |
| bank-drinking-04 | D | REVIEWED | kite_and_shoot | 0.625 | 0 | acceptable |
| bank-drinking-04 | E | REVIEWED | kite_and_shoot | 0.605 | 0 | acceptable |
| bank-nolos-01 | B | REVIEWED | approach | 0.635 | 0 | best |
| bank-nolos-01 | C | REVIEWED | approach | 0.365 | 0 | best |
| bank-nolos-01 | D | REVIEWED | approach | 0.205 | 0 | best |
| bank-nolos-01 | E | REVIEWED | approach | 0.300 | 0 | best |
| bank-nolos-02 | A | REVIEWED | keep_distance_and_shoot | 0.360 | 0 | FORBIDDEN |
| bank-nolos-02 | B | REVIEWED | approach | 0.635 | 0 | best |
| bank-nolos-02 | C | REVIEWED | approach | 0.385 | 0 | best |
| bank-nolos-02 | D | REVIEWED | approach | 0.420 | 0 | best |
| bank-nolos-02 | E | REVIEWED | approach | 0.385 | 0 | best |
| bank-nolos-03 | A | REVIEWED | approach | 0.660 | 0 | acceptable |
| bank-nolos-03 | B | REVIEWED | approach | 0.805 | 0 | acceptable |
| bank-nolos-03 | C | REVIEWED | approach | 0.760 | 0 | acceptable |
| bank-nolos-03 | D | REVIEWED | approach | 0.715 | 0 | acceptable |
| bank-nolos-03 | E | REVIEWED | approach | 0.690 | 0 | acceptable |
| bank-nolos-04 | A | REVIEWED | approach | 0.800 | 0 | best |
| bank-nolos-04 | B | REVIEWED | approach | 0.885 | 0 | best |
| bank-nolos-04 | C | REVIEWED | approach | 0.860 | 0 | best |
| bank-nolos-04 | D | REVIEWED | approach | 0.895 | 0 | best |
| bank-nolos-04 | E | REVIEWED | approach | 0.900 | 0 | best |
| bank-fleeing-01 | A | REVIEWED | kite_and_shoot | 0.835 | 0 | not acceptable |
| bank-fleeing-01 | B | REVIEWED | kite_and_shoot | 0.525 | 0 | not acceptable |
| bank-fleeing-01 | C | REVIEWED | kite_and_shoot | 0.925 | 0 | not acceptable |
| bank-fleeing-01 | D | REVIEWED | kite_and_shoot | 0.475 | 0 | not acceptable |
| bank-fleeing-01 | E | REVIEWED | kite_and_shoot | 0.510 | 0 | not acceptable |
| bank-fleeing-02 | A | REVIEWED | kite_and_shoot | 0.780 | 0 | not acceptable |
| bank-fleeing-02 | B | REVIEWED | close_in | 0.345 | 0 | acceptable |
| bank-fleeing-02 | C | REVIEWED | kite_and_shoot | 0.790 | 0 | not acceptable |
| bank-fleeing-02 | D | REVIEWED | lunge_in | 0.490 | 0 | best |
| bank-fleeing-02 | E | REVIEWED | lunge_in | 0.530 | 0 | best |
| bank-fleeing-03 | A | REVIEWED | kite_and_shoot | 0.890 | 0 | not acceptable |
| bank-fleeing-03 | B | REVIEWED | kite_and_shoot | 0.495 | 0 | not acceptable |
| bank-fleeing-03 | C | REVIEWED | kite_and_shoot | 0.940 | 0 | not acceptable |
| bank-fleeing-03 | D | REVIEWED | kite_and_shoot | 0.470 | 0 | not acceptable |
| bank-fleeing-03 | E | REVIEWED | kite_and_shoot | 0.450 | 0 | not acceptable |
| bank-fleeing-04 | A | REVIEWED | kite_and_shoot | 0.805 | 0 | not acceptable |
| bank-fleeing-04 | B | REVIEWED | close_in | 0.455 | 0 | acceptable |
| bank-fleeing-04 | C | REVIEWED | kite_and_shoot | 0.845 | 0 | not acceptable |
| bank-fleeing-04 | D | REVIEWED | lunge_in | 0.500 | 0 | best |
| bank-fleeing-04 | E | REVIEWED | lunge_in | 0.490 | 0 | best |
| bank-cont-melee-01 | A | REVIEWED | press_and_strike | 0.950 | 0 | best |
| bank-cont-melee-01 | B | REVIEWED | press_and_strike | 0.710 | 0 | best |
| bank-cont-melee-01 | C | REVIEWED | press_and_strike | 0.990 | 0 | best |
| bank-cont-melee-01 | D | REVIEWED | press_and_strike | 0.980 | 0 | best |
| bank-cont-melee-01 | E | REVIEWED | press_and_strike | 0.975 | 0 | best |
| bank-cont-melee-02 | A | REVIEWED | break_guard | 0.990 | 0 | best |
| bank-cont-melee-02 | B | REVIEWED | break_guard | 0.700 | 0 | best |
| bank-cont-melee-02 | C | REVIEWED | break_guard | 0.980 | 0 | best |
| bank-cont-melee-02 | D | REVIEWED | break_guard | 0.980 | 0 | best |
| bank-cont-melee-02 | E | REVIEWED | break_guard | 0.980 | 0 | best |
| bank-cont-melee-03 | A | REVIEWED | flank_and_strike | 0.495 | 0 | best |
| bank-cont-melee-03 | B | REVIEWED | flank_and_strike | 0.815 | 0 | best |
| bank-cont-melee-03 | C | REVIEWED | flank_and_strike | 0.705 | 0 | best |
| bank-cont-melee-03 | D | REVIEWED | flank_and_strike | 0.915 | 0 | best |
| bank-cont-melee-03 | E | REVIEWED | flank_and_strike | 0.925 | 0 | best |
| bank-cont-melee-04 | A | REVIEWED | break_guard | 0.820 | 0 | acceptable |
| bank-cont-melee-04 | B | REVIEWED | break_guard | 0.470 | 0 | acceptable |
| bank-cont-melee-04 | C | REVIEWED | break_guard | 0.725 | 0 | acceptable |
| bank-cont-melee-04 | D | REVIEWED | break_guard | 0.760 | 0 | acceptable |
| bank-cont-melee-04 | E | REVIEWED | break_guard | 0.755 | 0 | acceptable |
| bank-cont-melee-05 | A | REVIEWED | press_and_strike | 0.490 | 0 | acceptable |
| bank-cont-melee-05 | B | REVIEWED | flank_and_strike | 0.780 | 0 | best |
| bank-cont-melee-05 | C | REVIEWED | flank_and_strike | 0.535 | 0 | best |
| bank-cont-melee-05 | D | REVIEWED | flank_and_strike | 0.910 | 0 | best |
| bank-cont-melee-05 | E | REVIEWED | flank_and_strike | 0.910 | 0 | best |
| bank-cont-melee-06 | A | REVIEWED | break_guard | 0.900 | 0 | best |
| bank-cont-melee-06 | B | REVIEWED | break_guard | 0.685 | 0 | best |
| bank-cont-melee-06 | C | REVIEWED | break_guard | 0.870 | 0 | best |
| bank-cont-melee-06 | D | REVIEWED | break_guard | 0.920 | 0 | best |
| bank-cont-melee-06 | E | REVIEWED | break_guard | 0.940 | 0 | best |
| bank-cont-critical-01 | A | REVIEWED | retreat_and_defend | 1.000 | 0 | best |
| bank-cont-critical-01 | B | REVIEWED | retreat_and_defend | 0.895 | 0 | best |
| bank-cont-critical-01 | C | REVIEWED | retreat_and_defend | 0.990 | 0 | best |
| bank-cont-critical-01 | D | REVIEWED | retreat_and_defend | 0.980 | 0 | best |
| bank-cont-critical-01 | E | REVIEWED | retreat_and_defend | 0.965 | 0 | best |
| bank-cont-critical-02 | A | REVIEWED | retreat_and_defend | 1.000 | 0 | best |
| bank-cont-critical-02 | B | REVIEWED | retreat_and_defend | 0.810 | 0 | best |
| bank-cont-critical-02 | C | REVIEWED | retreat_and_defend | 0.980 | 0 | best |
| bank-cont-critical-02 | D | REVIEWED | retreat_and_defend | 0.955 | 0 | best |
| bank-cont-critical-02 | E | REVIEWED | retreat_and_defend | 0.950 | 0 | best |
| bank-cont-critical-03 | A | REVIEWED | retreat_and_defend | 0.990 | 0 | acceptable |
| bank-cont-critical-03 | B | REVIEWED | retreat_and_defend | 0.855 | 0 | acceptable |
| bank-cont-critical-03 | C | REVIEWED | retreat_and_defend | 0.975 | 0 | acceptable |
| bank-cont-critical-03 | D | REVIEWED | retreat_and_defend | 0.930 | 0 | acceptable |
| bank-cont-critical-03 | E | REVIEWED | retreat_and_defend | 0.930 | 0 | acceptable |
| bank-cont-critical-04 | A | REVIEWED | retreat_and_defend | 1.000 | 0 | uninformative |
| bank-cont-critical-04 | B | REVIEWED | retreat_and_defend | 0.795 | 0 | uninformative |
| bank-cont-critical-04 | C | REVIEWED | retreat_and_defend | 0.990 | 0 | uninformative |
| bank-cont-critical-04 | D | REVIEWED | retreat_and_defend | 0.980 | 0 | uninformative |
| bank-cont-critical-04 | E | REVIEWED | retreat_and_defend | 0.970 | 0 | uninformative |
| bank-cont-critical-05 | A | REVIEWED | retreat_and_defend | 0.990 | 0 | uninformative |
| bank-cont-critical-05 | B | REVIEWED | retreat_and_defend | 0.810 | 0 | uninformative |
| bank-cont-critical-05 | C | REVIEWED | retreat_and_defend | 0.985 | 0 | uninformative |
| bank-cont-critical-05 | D | REVIEWED | retreat_and_defend | 0.945 | 0 | uninformative |
| bank-cont-critical-05 | E | REVIEWED | retreat_and_defend | 0.930 | 0 | uninformative |
| bank-cont-critical-06 | A | REVIEWED | retreat_and_defend | 0.990 | 0 | uninformative |
| bank-cont-critical-06 | B | REVIEWED | retreat_and_defend | 0.755 | 0 | uninformative |
| bank-cont-critical-06 | C | REVIEWED | retreat_and_defend | 0.980 | 0 | uninformative |
| bank-cont-critical-06 | D | REVIEWED | retreat_and_defend | 0.970 | 0 | uninformative |
| bank-cont-critical-06 | E | REVIEWED | retreat_and_defend | 0.965 | 0 | uninformative |
| bank-shield-05 | A | REVIEWED | break_guard | 0.280 | 0 | acceptable |
| bank-nolos-01 | A | REVIEWED | keep_distance_and_shoot | 0.240 | 0 | FORBIDDEN |


## Calibration by confidence band, per arm (informative scenarios, NOW)

| Arm | Confidence | Scenarios | Acceptable | Forbidden | Margin top two | Normalized entropy |
|---|---|---|---|---|---|---|
| A | 0.0-0.3 | 4 | 75.0 % | 1 | 0.075 | 0.731 |
| A | 0.3-0.5 | 11 | 72.7 % | 2 | 0.133 | 0.505 |
| A | 0.5-0.7 | 6 | 83.3 % | 0 | 0.446 | 0.415 |
| A | 0.7-1.0 | 27 | 77.8 % | 0 | 0.843 | 0.150 |
| B | 0.0-0.3 | 6 | 100.0 % | 0 | 0.096 | 0.766 |
| B | 0.3-0.5 | 16 | 81.3 % | 0 | 0.195 | 0.695 |
| B | 0.5-0.7 | 13 | 84.6 % | 0 | 0.471 | 0.555 |
| B | 0.7-1.0 | 13 | 100.0 % | 0 | 0.775 | 0.301 |
| C | 0.0-0.3 | 1 | 100.0 % | 0 | 0.075 | 0.750 |
| C | 0.3-0.5 | 9 | 88.9 % | 0 | 0.151 | 0.538 |
| C | 0.5-0.7 | 10 | 70.0 % | 1 | 0.410 | 0.456 |
| C | 0.7-1.0 | 28 | 82.1 % | 0 | 0.849 | 0.156 |
| D | 0.0-0.3 | 1 | 100.0 % | 0 | 0.030 | 0.716 |
| D | 0.3-0.5 | 15 | 73.3 % | 0 | 0.173 | 0.512 |
| D | 0.5-0.7 | 9 | 88.9 % | 0 | 0.407 | 0.453 |
| D | 0.7-1.0 | 23 | 95.7 % | 0 | 0.882 | 0.136 |
| E | 0.0-0.3 | 1 | 0.0 % | 0 | 0.060 | 0.650 |
| E | 0.3-0.5 | 13 | 84.6 % | 0 | 0.151 | 0.537 |
| E | 0.5-0.7 | 11 | 81.8 % | 0 | 0.369 | 0.448 |
| E | 0.7-1.0 | 23 | 95.7 % | 0 | 0.878 | 0.145 |


## Kept at minimumConfidence 0.5 and their accuracy, REVIEWED apart from PROPOSED (NOW)

| Arm | Labels | Scenarios | Kept | Acceptable among kept | Wilson lower | Best among kept | Forbidden kept |
|---|---|---|---|---|---|---|---|
| A | REVIEWED | 48 | 68.8 % | 78.8 % | 0.622 | 60.6 % | 0 |
| B | REVIEWED | 48 | 54.2 % | 92.3 % | 0.759 | 65.4 % | 0 |
| C | REVIEWED | 48 | 79.2 % | 78.9 % | 0.637 | 57.9 % | 1 |
| D | REVIEWED | 48 | 66.7 % | 93.8 % | 0.799 | 71.9 % | 0 |
| E | REVIEWED | 48 | 70.8 % | 91.2 % | 0.770 | 67.6 % | 0 |


## Contingencies by situation (informative scenarios, kept at 0.5)

| Arm | Situation | Scenarios | Kept | Acceptable among kept |
|---|---|---|---|---|
| A | IF_TARGET_IN_MELEE | 6 | 66.7 % | 100.0 % |
| A | IF_OWN_HEALTH_CRITICAL | 3 | 100.0 % | 100.0 % |
| B | IF_TARGET_IN_MELEE | 6 | 83.3 % | 100.0 % |
| B | IF_OWN_HEALTH_CRITICAL | 3 | 100.0 % | 100.0 % |
| C | IF_TARGET_IN_MELEE | 6 | 100.0 % | 100.0 % |
| C | IF_OWN_HEALTH_CRITICAL | 3 | 100.0 % | 100.0 % |
| D | IF_TARGET_IN_MELEE | 6 | 100.0 % | 100.0 % |
| D | IF_OWN_HEALTH_CRITICAL | 3 | 100.0 % | 100.0 % |
| E | IF_TARGET_IN_MELEE | 6 | 100.0 % | 100.0 % |
| E | IF_OWN_HEALTH_CRITICAL | 3 | 100.0 % | 100.0 % |


## Fit checks of arm E against the labels

| Label of the option | Fits | Mean fit | Below veto 0.2 | At or above rescue 0.7 |
|---|---|---|---|---|
| acceptable | 270 | 0.399 | 16.7 % | 14.1 % |
| forbidden | 256 | 0.318 | 23.0 % | 2.3 % |
| neither | 152 | 0.357 | 30.9 % | 15.1 % |


## Rule of M16.7 on arm B, NOW

| Threshold | n(t) | Kept | Acceptable | Wilson lower | Forbidden | Meets |
|---|---|---|---|---|---|---|
| 0.20 | 47 | 97.9 % | 89.4 % | 0.774 | 0 | no |
| 0.25 | 45 | 93.8 % | 88.9 % | 0.765 | 0 | no |
| 0.30 | 42 | 87.5 % | 88.1 % | 0.750 | 0 | no |
| 0.35 | 35 | 72.9 % | 88.6 % | 0.740 | 0 | no |
| 0.40 | 30 | 62.5 % | 90.0 % | 0.744 | 0 | no |
| 0.45 | 28 | 58.3 % | 89.3 % | 0.728 | 0 | no |
| 0.50 | 26 | 54.2 % | 92.3 % | 0.759 | 0 | no |
| 0.55 | 22 | 45.8 % | 95.5 % | 0.782 | 0 | no |
| 0.60 | 18 | 37.5 % | 100.0 % | 0.824 | 0 | no |
| 0.65 | 13 | 27.1 % | 100.0 % | 0.772 | 0 | no |
| 0.70 | 13 | 27.1 % | 100.0 % | 0.772 | 0 | no |


Eligible scenarios 48; chosen threshold none (0.5 stays).

## Rule of M16.7 on arm B, contingencies

| Threshold | n(t) | Kept | Acceptable | Wilson lower | Forbidden | Meets |
|---|---|---|---|---|---|---|
| 0.20 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.25 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.30 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.35 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.40 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.45 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.50 | 8 | 88.9 % | 100.0 % | 0.676 | 0 | no |
| 0.55 | 8 | 88.9 % | 100.0 % | 0.676 | 0 | no |
| 0.60 | 8 | 88.9 % | 100.0 % | 0.676 | 0 | no |
| 0.65 | 8 | 88.9 % | 100.0 % | 0.676 | 0 | no |
| 0.70 | 7 | 77.8 % | 100.0 % | 0.646 | 0 | no |


Eligible scenarios 9; chosen threshold none (0.5 stays).

## Objectives of M16.10 (arm B, NOW, REVIEWED)

| Measure | Objective | Result |
|---|---|---|
| Kept at 0.5 | >= 70 % | 54.2 % |
| Acceptable among kept | >= 90 % and Wilson lower >= 0.75 with n >= 30 | 92.3 %, Wilson 0.759, n 26 |
| Forbidden among kept | <= 1 (2 % from n >= 50) | 0 |
| Best among kept | >= 60 % | 65.4 % |
| Improvement over A in kept at 0.5 | +30 points | -14.6 points |
| Real tokens of the production request | <= 1 200 | 953 |


## Rule of M16.7 on arm A, NOW

| Threshold | n(t) | Kept | Acceptable | Wilson lower | Forbidden | Meets |
|---|---|---|---|---|---|---|
| 0.20 | 48 | 100.0 % | 77.1 % | 0.635 | 3 | no |
| 0.25 | 46 | 95.8 % | 78.3 % | 0.644 | 2 | no |
| 0.30 | 44 | 91.7 % | 77.3 % | 0.630 | 2 | no |
| 0.35 | 43 | 89.6 % | 79.1 % | 0.648 | 1 | no |
| 0.40 | 41 | 85.4 % | 80.5 % | 0.660 | 0 | no |
| 0.45 | 38 | 79.2 % | 78.9 % | 0.637 | 0 | no |
| 0.50 | 33 | 68.8 % | 78.8 % | 0.622 | 0 | no |
| 0.55 | 33 | 68.8 % | 78.8 % | 0.622 | 0 | no |
| 0.60 | 31 | 64.6 % | 77.4 % | 0.602 | 0 | no |
| 0.65 | 30 | 62.5 % | 76.7 % | 0.591 | 0 | no |
| 0.70 | 27 | 56.3 % | 77.8 % | 0.592 | 0 | no |


Eligible scenarios 48; chosen threshold none (0.5 stays).

## Rule of M16.7 on arm A, contingencies

| Threshold | n(t) | Kept | Acceptable | Wilson lower | Forbidden | Meets |
|---|---|---|---|---|---|---|
| 0.20 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.25 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.30 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.35 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.40 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.45 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.50 | 7 | 77.8 % | 100.0 % | 0.646 | 0 | no |
| 0.55 | 7 | 77.8 % | 100.0 % | 0.646 | 0 | no |
| 0.60 | 7 | 77.8 % | 100.0 % | 0.646 | 0 | no |
| 0.65 | 7 | 77.8 % | 100.0 % | 0.646 | 0 | no |
| 0.70 | 7 | 77.8 % | 100.0 % | 0.646 | 0 | no |


Eligible scenarios 9; chosen threshold none (0.5 stays).

## Rule of M16.7 on arm C, NOW

| Threshold | n(t) | Kept | Acceptable | Wilson lower | Forbidden | Meets |
|---|---|---|---|---|---|---|
| 0.20 | 48 | 100.0 % | 81.3 % | 0.681 | 1 | no |
| 0.25 | 48 | 100.0 % | 81.3 % | 0.681 | 1 | no |
| 0.30 | 47 | 97.9 % | 80.9 % | 0.675 | 1 | no |
| 0.35 | 46 | 95.8 % | 80.4 % | 0.668 | 1 | no |
| 0.40 | 43 | 89.6 % | 81.4 % | 0.674 | 1 | no |
| 0.45 | 41 | 85.4 % | 80.5 % | 0.660 | 1 | no |
| 0.50 | 38 | 79.2 % | 78.9 % | 0.637 | 1 | no |
| 0.55 | 37 | 77.1 % | 81.1 % | 0.658 | 0 | no |
| 0.60 | 32 | 66.7 % | 78.1 % | 0.612 | 0 | no |
| 0.65 | 30 | 62.5 % | 80.0 % | 0.627 | 0 | no |
| 0.70 | 28 | 58.3 % | 82.1 % | 0.644 | 0 | no |


Eligible scenarios 48; chosen threshold none (0.5 stays).

## Rule of M16.7 on arm C, contingencies

| Threshold | n(t) | Kept | Acceptable | Wilson lower | Forbidden | Meets |
|---|---|---|---|---|---|---|
| 0.20 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.25 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.30 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.35 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.40 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.45 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.50 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.55 | 8 | 88.9 % | 100.0 % | 0.676 | 0 | no |
| 0.60 | 8 | 88.9 % | 100.0 % | 0.676 | 0 | no |
| 0.65 | 8 | 88.9 % | 100.0 % | 0.676 | 0 | no |
| 0.70 | 8 | 88.9 % | 100.0 % | 0.676 | 0 | no |


Eligible scenarios 9; chosen threshold none (0.5 stays).

## Rule of M16.7 on arm D, NOW

| Threshold | n(t) | Kept | Acceptable | Wilson lower | Forbidden | Meets |
|---|---|---|---|---|---|---|
| 0.20 | 48 | 100.0 % | 87.5 % | 0.753 | 0 | no |
| 0.25 | 47 | 97.9 % | 87.2 % | 0.748 | 0 | no |
| 0.30 | 47 | 97.9 % | 87.2 % | 0.748 | 0 | no |
| 0.35 | 45 | 93.8 % | 88.9 % | 0.765 | 0 | no |
| 0.40 | 45 | 93.8 % | 88.9 % | 0.765 | 0 | no |
| 0.45 | 41 | 85.4 % | 90.2 % | 0.775 | 0 | yes |
| 0.50 | 32 | 66.7 % | 93.8 % | 0.799 | 0 | yes |
| 0.55 | 30 | 62.5 % | 93.3 % | 0.787 | 0 | yes |
| 0.60 | 28 | 58.3 % | 96.4 % | 0.823 | 0 | no |
| 0.65 | 26 | 54.2 % | 96.2 % | 0.811 | 0 | no |
| 0.70 | 23 | 47.9 % | 95.7 % | 0.790 | 0 | no |


Eligible scenarios 48; chosen threshold 0.45.

## Rule of M16.7 on arm D, contingencies

| Threshold | n(t) | Kept | Acceptable | Wilson lower | Forbidden | Meets |
|---|---|---|---|---|---|---|
| 0.20 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.25 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.30 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.35 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.40 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.45 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.50 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.55 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.60 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.65 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.70 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |


Eligible scenarios 9; chosen threshold none (0.5 stays).

## Rule of M16.7 on arm E, NOW

| Threshold | n(t) | Kept | Acceptable | Wilson lower | Forbidden | Meets |
|---|---|---|---|---|---|---|
| 0.20 | 48 | 100.0 % | 87.5 % | 0.753 | 0 | no |
| 0.25 | 48 | 100.0 % | 87.5 % | 0.753 | 0 | no |
| 0.30 | 47 | 97.9 % | 89.4 % | 0.774 | 0 | no |
| 0.35 | 45 | 93.8 % | 88.9 % | 0.765 | 0 | no |
| 0.40 | 44 | 91.7 % | 88.6 % | 0.760 | 0 | no |
| 0.45 | 39 | 81.3 % | 89.7 % | 0.764 | 0 | no |
| 0.50 | 34 | 70.8 % | 91.2 % | 0.770 | 0 | yes |
| 0.55 | 29 | 60.4 % | 96.6 % | 0.828 | 0 | no |
| 0.60 | 28 | 58.3 % | 96.4 % | 0.823 | 0 | no |
| 0.65 | 25 | 52.1 % | 96.0 % | 0.805 | 0 | no |
| 0.70 | 23 | 47.9 % | 95.7 % | 0.790 | 0 | no |


Eligible scenarios 48; chosen threshold 0.50.

## Rule of M16.7 on arm E, contingencies

| Threshold | n(t) | Kept | Acceptable | Wilson lower | Forbidden | Meets |
|---|---|---|---|---|---|---|
| 0.20 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.25 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.30 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.35 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.40 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.45 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.50 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.55 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.60 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.65 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |
| 0.70 | 9 | 100.0 % | 100.0 % | 0.701 | 0 | no |


Eligible scenarios 9; chosen threshold none (0.5 stays).
