# M16 re-bench on the labelled bank

Model jev-1.13.0, 12 scenarios, arms [B], 10 repetitions, one call at a time on one client. Estimate: 120 calls, at most 94670 input tokens, about 0.0040 USD.
| Arm | Calls | Body chars per call | Tokens per call (1.5) | Tokens (1.5) | USD |
|---|---|---|---|---|---|
| B | 120 | 1182 | 788 | 94670 | 0.0040 |


## Tokens, cost and failures per arm

| Arm | Calls | Failures | Body chars per call | Input tokens per call | Characters per token | Input tokens | USD |
|---|---|---|---|---|---|---|---|
| B | 120 | 0 | 1182 | 672 | 1.76 | 80670 | 0.003388 |


Production request (1 mob, 1 participant, NOW plus one contingency, 7 options of the example) per arm: the mean tokens of a NOW call plus those of a contingency call, minus one state, which a production request carries once.
| Arm | Estimated real tokens of the production request |
|---|---|
| B | 995 |


## Per scenario (each distinct scenario weighs one)

| Scenario | Arm | Label | Modal best | Mean confidence | Changes between repetitions | Judged |
|---|---|---|---|---|---|---|
| bank-melee-01 | B | REVIEWED | press_and_strike | 0.659 | 0 | best |
| bank-shooter-01 | B | REVIEWED | keep_distance_and_shoot | 0.234 | 0 | acceptable |
| bank-shield-01 | B | REVIEWED | break_guard | 0.928 | 0 | best |
| bank-lowhp-01 | B | REVIEWED | retreat_and_defend | 0.393 | 0 | best |
| bank-pillar-01 | B | REVIEWED | keep_distance_and_shoot | 0.794 | 0 | best |
| bank-grouped-01 | B | REVIEWED | press_and_strike | 0.559 | 0 | acceptable |
| bank-finisher-01 | B | REVIEWED | kite_and_shoot | 0.356 | 2 | acceptable |
| bank-drinking-01 | B | REVIEWED | kite_and_shoot | 0.829 | 0 | acceptable |
| bank-nolos-01 | B | REVIEWED | approach | 0.633 | 0 | best |
| bank-fleeing-01 | B | REVIEWED | kite_and_shoot | 0.557 | 0 | not acceptable |
| bank-cont-melee-01 | B | REVIEWED | press_and_strike | 0.682 | 0 | best |
| bank-cont-critical-01 | B | REVIEWED | retreat_and_defend | 0.878 | 0 | best |


## Calibration by confidence band, per arm (informative scenarios, NOW)

| Arm | Confidence | Scenarios | Acceptable | Forbidden | Margin top two | Normalized entropy |
|---|---|---|---|---|---|---|
| B | 0.0-0.3 | 1 | 100.0 % | 0 | 0.139 | 0.809 |
| B | 0.3-0.5 | 2 | 100.0 % | 0 | 0.180 | 0.699 |
| B | 0.5-0.7 | 4 | 75.0 % | 0 | 0.497 | 0.547 |
| B | 0.7-1.0 | 3 | 100.0 % | 0 | 0.783 | 0.243 |


## Kept at minimumConfidence 0.5 and their accuracy, REVIEWED apart from PROPOSED (NOW)

| Arm | Labels | Scenarios | Kept | Acceptable among kept | Wilson lower | Best among kept | Forbidden kept |
|---|---|---|---|---|---|---|---|
| B | REVIEWED | 10 | 70.0 % | 85.7 % | 0.487 | 57.1 % | 0 |


## Contingencies by situation (informative scenarios, kept at 0.5)

| Arm | Situation | Scenarios | Kept | Acceptable among kept |
|---|---|---|---|---|
| B | IF_TARGET_IN_MELEE | 1 | 100.0 % | 100.0 % |
| B | IF_OWN_HEALTH_CRITICAL | 1 | 100.0 % | 100.0 % |


## Fit checks of arm E against the labels

| Label of the option | Fits | Mean fit | Below veto 0.2 | At or above rescue 0.7 |
|---|---|---|---|---|


## Rule of M16.7 on arm B, NOW

| Threshold | n(t) | Kept | Acceptable | Wilson lower | Forbidden | Meets |
|---|---|---|---|---|---|---|
| 0.20 | 10 | 100.0 % | 90.0 % | 0.596 | 0 | no |
| 0.25 | 9 | 90.0 % | 88.9 % | 0.565 | 0 | no |
| 0.30 | 9 | 90.0 % | 88.9 % | 0.565 | 0 | no |
| 0.35 | 9 | 90.0 % | 88.9 % | 0.565 | 0 | no |
| 0.40 | 7 | 70.0 % | 85.7 % | 0.487 | 0 | no |
| 0.45 | 7 | 70.0 % | 85.7 % | 0.487 | 0 | no |
| 0.50 | 7 | 70.0 % | 85.7 % | 0.487 | 0 | no |
| 0.55 | 7 | 70.0 % | 85.7 % | 0.487 | 0 | no |
| 0.60 | 5 | 50.0 % | 100.0 % | 0.566 | 0 | no |
| 0.65 | 4 | 40.0 % | 100.0 % | 0.510 | 0 | no |
| 0.70 | 3 | 30.0 % | 100.0 % | 0.438 | 0 | no |


PRELIMINARY (not every label of the bank is REVIEWED; no default changes). Eligible scenarios 10; chosen threshold none (0.5 stays).

## Rule of M16.7 on arm B, contingencies

| Threshold | n(t) | Kept | Acceptable | Wilson lower | Forbidden | Meets |
|---|---|---|---|---|---|---|
| 0.20 | 2 | 100.0 % | 100.0 % | 0.342 | 0 | no |
| 0.25 | 2 | 100.0 % | 100.0 % | 0.342 | 0 | no |
| 0.30 | 2 | 100.0 % | 100.0 % | 0.342 | 0 | no |
| 0.35 | 2 | 100.0 % | 100.0 % | 0.342 | 0 | no |
| 0.40 | 2 | 100.0 % | 100.0 % | 0.342 | 0 | no |
| 0.45 | 2 | 100.0 % | 100.0 % | 0.342 | 0 | no |
| 0.50 | 2 | 100.0 % | 100.0 % | 0.342 | 0 | no |
| 0.55 | 2 | 100.0 % | 100.0 % | 0.342 | 0 | no |
| 0.60 | 2 | 100.0 % | 100.0 % | 0.342 | 0 | no |
| 0.65 | 2 | 100.0 % | 100.0 % | 0.342 | 0 | no |
| 0.70 | 1 | 50.0 % | 100.0 % | 0.207 | 0 | no |


PRELIMINARY (not every label of the bank is REVIEWED; no default changes). Eligible scenarios 2; chosen threshold none (0.5 stays).

## Objectives of M16.10 (arm B, NOW, REVIEWED)

| Measure | Objective | Result |
|---|---|---|
| Kept at 0.5 | >= 70 % | 70.0 % |
| Acceptable among kept | >= 90 % and Wilson lower >= 0.75 with n >= 30 | 85.7 %, Wilson 0.487, n 7 |
| Forbidden among kept | <= 1 (2 % from n >= 50) | 0 |
| Best among kept | >= 60 % | 57.1 % |
| Improvement over A in kept at 0.5 | +30 points | - |
| Real tokens of the production request | <= 1 200 | 995 |

