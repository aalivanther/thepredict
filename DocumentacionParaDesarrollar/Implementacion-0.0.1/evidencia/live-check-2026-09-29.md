> Evidencia de M13 con la cuenta real de TypeSafe, 2026-09-29. Ejecutada por el orquestador con la aprobación del usuario (tarea `liveJevCheck`; 20 llamadas separadas 3 s, según el propio informe); copiada sin cambios debajo de esta nota. Revisada sin encontrar clave, cabeceras ni nombres de variables de entorno. Contexto: [M13, estado de implementación](../M13-Suite-carga-latencia-calidad.md#estado-de-implementación).

# M13 live check: GET /v1/models

Model jev-1.13.0, 20 calls 3 s apart.
| Samples | us p50 / p95 / p99 / max | States | Details |
|---|---|---|---|
| 20 | 140710.4 / 178554.8 / 533544.8 / 533544.8 | {READY=20} | credential valid; model unverified until first inference |

