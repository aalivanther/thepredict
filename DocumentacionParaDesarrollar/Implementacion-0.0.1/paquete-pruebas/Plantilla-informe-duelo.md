# Plantilla del informe del duelo (M18.8)

> Paquete de pruebas ([índice](README.md)) · Módulo: [M18](../M18-Duelo-de-mobs-controlados.md) · Sesión: [18](Guia-de-pruebas-por-sesiones.md#sesión-18-duelo-de-mobs-controlados-sin-clave-m18) · Guion: [G13 §13](../Guion-servidor-0.0.1.md#13-duelo-de-mobs-controlados-m186)

Se copia a `DocumentacionParaDesarrollar/Evidencia-0.0.1/<fecha>-duelo/informe.md` y se rellena con `summary.md` y los CSV de cada grabación (`local-<fecha>` sin clave y `jev-<fecha>` con clave). Las conclusiones se limitan a esta base: dos zombis iguales con las cinco skills del paquete en una arena plana; no afirman superioridad frente a otra IA ni en otros combates (plan §12).

## 1. Datos de la sesión

- Fecha, versiones (Paper, MythicMobs, Java) y SHA-256 del JAR de ThePredict:
- Grabaciones: `local-<fecha>-<UTC>` (80 rondas), `jev-<fecha>-<UTC>` (30 rondas), con `stopped_by` de cada `summary.md`:
- Filas descartadas (`rows dropped`) y bytes de cada grabación:
- Cambios hechos en el paquete (por ejemplo la velocidad de `leap`) y por qué:

## 2. Resultado por brazo

Copiar la tabla `Rounds by arm` de cada `summary.md`. Un empate cuenta medio punto para cada lado; los empates se publican aparte.

| Grabación | Brazo | Rondas | Victorias rojo | Victorias azul | Empates | Tasa de rojo (Wilson 95 %) | Tiempo hasta la muerte, mediana (IQR) s | Daño hecho rojo / azul |
|---|---|---|---|---|---|---|---|---|
| local | A | | | | | | | |
| local | B | | | | | | | |
| local | C | | | | | | | |
| local | D | | | | | | | |
| jev | A | | | | | | | |
| jev | B | | | | | | | |
| jev | C | | | | | | | |

**Comprobación de D:** la tasa de rojo de D debe dar ≈ 50 %. Con 20 rondas el intervalo de Wilson al 95 % tiene unos ±22 puntos: D solo detecta sesgos de 75/25 o más (15 de 20 da un intervalo que excluye el 50 %). Si D excluye el 50 %, la arena o la aparición tienen sesgo y se corrige antes de leer A–C.

## 3. Skills, retiradas, guardias y niveles

- `Casts, hits and misses per skill` por brazo (los clásicos se miden por intento, `MYTHIC_EVENT`, y acierto por daño en 20 ticks; DT-40 d):
- `Retreats and guards` por brazo:
- `Time per control level of the ThePredict duelists` por brazo (con clave, cuánto tiempo en `JEV` frente a `LOCAL` y `BASE`):
- `Outcome windows per tactic` por brazo (efecto medio de cada táctica):

## 4. Costo

| Grabación | Solicitudes | Tokens de entrada | USD (0,042 por millón) | Estimación previa (G13 §13) |
|---|---|---|---|---|
| jev | | | | ≈ 0,19 con tokens reales (cota W7 ≈ 0,32) |

## 5. Local frente a servidor

Comparar la [matriz del simulador](../evidencia/m18-simulador-2026-09-29/duel-local.md) (`./gradlew duelMeasure --offline`) con la sesión 18.3, brazo por brazo: tasas, tiempo hasta la muerte, casts por skill y reparto de niveles. Una diferencia grande indica que el simulador no reproduce algo del servidor (movimiento, alcance vanilla, invulnerabilidad, reglas por temporizador) o que el servidor hace algo que el paquete no espera.

## 6. Diagnóstico: qué mejorar

Se rellena con `outcomes.csv`, `casts.csv`, `damage.csv`, `intents.csv` y `decisions.jsonl`.

| Síntoma | Lectura | Acción | Observado en esta sesión |
|---|---|---|---|
| Una táctica con efecto medio negativo y mucho uso | Jev o el planificador la eligen donde no sirve | Revisar su `Purpose` (`BestWhen`, `Risks`) o su prioridad (M15) | |
| Aciertos bajos de una skill a distancia | Se lanza sin visión o fuera de alcance real | `Reach` o `NEEDS_LINE_OF_SIGHT` declarados (M15.6) | |
| Mucho tiempo en `BASE` o `LOCAL` con clave | Latencia, presupuesto o confianza baja (`intents.csv` y `decisions.jsonl` dan la causa de cada caída) | `minimumConfidence` por perfil (M16.7), `questionDetail` (M16.2), presupuesto | |
| Respuestas rechazadas por confianza en situaciones claras | Planteamiento | Pasar esos cuerpos de `decisions.jsonl` al banco de M16 y etiquetarlos (M16.9) | |
| ThePredict gana en local y pierde con Jev | Planteamiento o etiquetas | Banco de M16 con escenarios del duelo | |
| D no es simétrico | Arena o aparición sesgadas | Corregir antes de leer A–C | |

Los cuerpos de `decisions.jsonl` (líneas `"type":"body"`, ya con participantes `entity_N` y sin UUID) entran en el banco de M16 como escenarios nuevos, etiquetados por el usuario con el mismo procedimiento (M16.9). `./gradlew duelJoin --offline -Pdecisions=<ruta>/decisions.jsonl` une cada solicitud (`planned`, `body`, `result` y sus `acceptance`) en `build/reports/m18/decisions-joined.json`.

## 7. Conclusiones (limitadas a esta base)

-
