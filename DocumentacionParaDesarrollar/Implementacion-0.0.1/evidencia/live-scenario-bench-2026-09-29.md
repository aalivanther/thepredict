> Evidencia de M13 con la cuenta real de TypeSafe, 2026-09-29. Ejecutada por el orquestador con la aprobación del usuario (tarea `liveScenarioBench` sobre el banco semilla, 200 repeticiones, según el propio informe); copiada sin cambios debajo de esta nota. Revisada sin encontrar clave, cabeceras ni nombres de variables de entorno. Contexto: [M13, estado de implementación](../M13-Suite-carga-latencia-calidad.md#estado-de-implementación).

# M13 live scenario bench

Model jev-1.13.0, 6 scenarios, 200 repetitions, one call at a time on one client, so only the very first call includes the connection and TLS handshake.

## Latency, failures and tokens

| Calls | us p50 / p95 / p99 / max | Failures | Effective models | Input tokens | USD at 0.042 per million | Characters per token min / mean / max |
|---|---|---|---|---|---|---|
| 1200 | 179136.5 / 227802.1 / 328255.0 / 513407.1 | {} | {jev-1.13.0=1200} | 735800 | 0.030904 | 1.59 / 1.73 / 1.82 |


## Reliability of the confidence (labelled questions)

| Confidence | Answers | Acceptable | Forbidden chosen |
|---|---|---|---|
| 0.0-0.3 | 248 | 69.0 % | 7 |
| 0.3-0.5 | 552 | 5.8 % | 190 |
| 0.5-0.7 | 16 | 100.0 % | 0 |
| 0.7-1.0 | 384 | 100.0 % | 0 |

| minimumConfidence | Answers kept | Acceptable among kept |
|---|---|---|
| 0.30 | 79.3 % | 45.4 % |
| 0.40 | 39.3 % | 84.7 % |
| 0.50 | 33.3 % | 100.0 % |
| 0.60 | 33.3 % | 100.0 % |
| 0.70 | 32.0 % | 100.0 % |


## Switches allowed by tacticSwitchMargin between repeated answers of the same question

| Margin | Repeated answers | Switches |
|---|---|---|
| 0.00 | 1194 | 0.5 % |
| 0.10 | 1194 | 0.0 % |
| 0.15 | 1194 | 0.0 % |
| 0.20 | 1194 | 0.0 % |
| 0.30 | 1194 | 0.0 % |


## Contingencies that differ from the answer for now

| Situation | Answers | Different from NOW |
|---|---|---|

