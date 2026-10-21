# M22 calibración base — brazo D

Medido el 2026-10-07 con `EvalCalibrationBaseline.arm("D")`. Lee el banco congelado `src/test/resources/scenarios/bank` y `evidencia/m16-rebanco-2026-09-30/P2/answers.jsonl`. No reescribe ninguno de los dos.

| cifra | valor |
|---|---:|
| ECE10 | 0,205000 |
| Brier (etiqueta superior) | 0,151825 |

El check exige ECE10 0,205 ± 0,001 y Brier 0,152 ± 0,001. Esta corrida cae dentro. M22 mide; no recalibra.
