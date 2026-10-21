# E1. Poda del banco (M19.9)

60 escenarios de scenarios/bank, etiquetas almacenadas, un prepare local, sin red y sin Jev. Ocho filas: poda OFF y SAFE, topes 8 y 4, modos INTENT_ONLY y ANY_READY. La puerta es SAFE con tope 4. Los defaults no cambian: pruning off, tope 8, horizonte 0. Ajuste unico de E1, sin tocar etiquetas: SUSTAIN_WHILE_HEALTHY no quita la cura cuando la pregunta supone SELF_LOW_HEALTH ni cuando el objetivo no se ve o no se alcanza.

| Config | Reviewed | Best | Acceptable | Unique removed | Forbidden removed | Cures by SUSTAIN | Healthy sustain removed | Chars | Local <2 | Mismatches | Vetoes undone |
|---|---|---|---|---|---|---|---|---|---|---|---|
| INTENT_ONLY OFF cap 8 | 60 | 58/58 | 60/60 | 0 | 0 | 0 | 0 | 157908 | 0 | 0 | 0 |
| INTENT_ONLY OFF cap 4 | 60 | 46/58 | 59/60 | 1 | 86 | 0 | 0 | 142734 | 0 | 0 | 0 |
| INTENT_ONLY SAFE cap 8 | 60 | 58/58 | 60/60 | 0 | 30 | 0 | 18 | 148264 | 0 | 0 | 0 |
| INTENT_ONLY SAFE cap 4 | 60 | 49/58 | 60/60 | 0 | 110 | 0 | 18 | 137576 | 0 | 0 | 0 |
| ANY_READY OFF cap 8 | 60 | 58/58 | 60/60 | 0 | 0 | 0 | 0 | 157908 | 0 | 0 | 0 |
| ANY_READY OFF cap 4 | 60 | 46/58 | 59/60 | 1 | 86 | 0 | 0 | 142734 | 0 | 0 | 0 |
| ANY_READY SAFE cap 8 | 60 | 40/58 | 55/60 | 0 | 60 | 0 | 18 | 124299 | 0 | 0 | 0 |
| ANY_READY SAFE cap 4 | 60 | 40/58 | 55/60 | 0 | 87 | 0 | 18 | 123291 | 0 | 0 | 0 |

| Config | SUSTAIN_WHILE_HEALTHY | OUT_OF_REACH_STATIC | NO_SIGHT_STATIC | UNREACHABLE_ON_FOOT | SAME_EFFECT_AS_HIGHER | BELOW_OPTION_CAP |
|---|---|---|---|---|---|---|
| INTENT_ONLY OFF cap 8 | 0 | 0 | 0 | 0 | 0 | 0 |
| INTENT_ONLY OFF cap 4 | 0 | 0 | 0 | 0 | 0 | 0 |
| INTENT_ONLY SAFE cap 8 | 20 | 4 | 0 | 12 | 0 | 0 |
| INTENT_ONLY SAFE cap 4 | 20 | 4 | 0 | 12 | 0 | 134 |
| ANY_READY OFF cap 8 | 0 | 0 | 0 | 0 | 0 | 0 |
| ANY_READY OFF cap 4 | 0 | 0 | 0 | 0 | 0 | 0 |
| ANY_READY SAFE cap 8 | 20 | 4 | 0 | 12 | 112 | 0 |
| ANY_READY SAFE cap 4 | 20 | 4 | 0 | 12 | 112 | 28 |

## Produccion FULL, sin fit checks

El prefijo de 4 opciones es el recorte de M19.7 (orden de la pregunta, sin vetos). La fila SAFE reordena por prioridad y aplica los vetos. El criterio de caracteres usa ese prefijo y, aparte, el agregado del banco.

| Fila | Chars | Tokens a 1,5 |
|---|---|---|
| completo | 4805 | 3204 |
| prefijo tope 4 | 4441 | 2961 |
| SAFE tope 4 INTENT_ONLY | 4121 | 2748 |
| SAFE tope 4 ANY_READY | 3395 | 2264 |

## Puerta para activar safe y el tope 4

No se activa. Fila de puerta: ANY_READY SAFE cap 4, frente a ANY_READY OFF cap 8.

no cumple. best 40/58 (no cumple). aceptable 55/60 (no cumple). aceptable unica quitada 0 (cumple). curas best o aceptables quitadas por SUSTAIN_WHILE_HEALTHY 0 (cumple). caracteres de produccion 4805 a 4441, 7.6 % (no cumple; el criterio pide 25 % o mas). banco 157908 a 123291, 21.9 % (no cumple).

Misma poda en INTENT_ONLY (no es la fila de puerta): INTENT_ONLY SAFE cap 4. no cumple. best 49/58 (no cumple). aceptable 60/60 (cumple). aceptable unica quitada 0 (cumple). curas best o aceptables quitadas por SUSTAIN_WHILE_HEALTHY 0 (cumple). caracteres de produccion 4805 a 4441, 7.6 % (no cumple; el criterio pide 25 % o mas). banco 157908 a 137576, 12.9 % (no cumple).

## Perdidas de la fila SAFE tope 4

ANY_READY SAFE cap 4:
- bank-cont-melee-04 best press SAME_EFFECT_AS_HIGHER
- bank-drinking-04 best close_in SAME_EFFECT_AS_HIGHER
- bank-drinking-04 best lunge_in SAME_EFFECT_AS_HIGHER
- bank-finisher-03 best lunge_in SAME_EFFECT_AS_HIGHER
- bank-finisher-04 best lunge_in SAME_EFFECT_AS_HIGHER
- bank-fleeing-01 best lunge_in SAME_EFFECT_AS_HIGHER
- bank-fleeing-02 best lunge_in SAME_EFFECT_AS_HIGHER
- bank-fleeing-03 best lunge_in SAME_EFFECT_AS_HIGHER
- bank-fleeing-04 best lunge_in SAME_EFFECT_AS_HIGHER
- bank-grouped-01 best close_in_and_slam SAME_EFFECT_AS_HIGHER
- bank-grouped-02 best close_in_and_slam SAME_EFFECT_AS_HIGHER
- bank-grouped-03 best close_in_and_slam SAME_EFFECT_AS_HIGHER
- bank-grouped-04 best close_in_and_slam SAME_EFFECT_AS_HIGHER
- bank-nolos-01 best approach SAME_EFFECT_AS_HIGHER
- bank-nolos-02 best approach SAME_EFFECT_AS_HIGHER
- bank-nolos-03 best close_in_and_slam SAME_EFFECT_AS_HIGHER
- bank-nolos-04 best approach SAME_EFFECT_AS_HIGHER
- bank-shield-05 best press SAME_EFFECT_AS_HIGHER
INTENT_ONLY SAFE cap 4:
- bank-drinking-01 best interrupt_shot BELOW_OPTION_CAP
- bank-drinking-02 best interrupt_shot BELOW_OPTION_CAP
- bank-drinking-03 best interrupt_shot BELOW_OPTION_CAP
- bank-drinking-04 best close_in BELOW_OPTION_CAP
- bank-nolos-01 best approach BELOW_OPTION_CAP
- bank-nolos-02 best approach BELOW_OPTION_CAP
- bank-nolos-04 best approach BELOW_OPTION_CAP
- bank-shield-02 best flank_and_strike BELOW_OPTION_CAP
- bank-shield-04 best flank_and_strike BELOW_OPTION_CAP

Opciones del archivo distintas del vivo OFF tope 8: 0.
