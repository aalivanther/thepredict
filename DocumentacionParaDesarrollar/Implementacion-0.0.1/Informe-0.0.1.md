# Informe de la 0.0.1

> **Estado (2026-09-29): candidato pendiente de integración.** Escrito en M14. **Nota del 2026-09-30 (decisiones del usuario tras cerrar M18):** disponibilidad en tiempo real de M10 (cada skill sale a su propio `Cooldown` en `LOCAL` y con Jev; desviación 29, DT-28 revisado) y DT-42 descartada; el build pasa a 1644 pruebas en 222 clases y el JAR a SHA-256 `8a2703ddd9d906f0892f7a5d98374f71de3512d637c042638f2c17b07c0736f5` (ver el [CHANGELOG](../../CHANGELOG.md#artefacto) y la sección 7). **Nota de M18 (entrega F):** M18 añadió el registrador de duelos `/thepredict duel`, el paquete de duelo y el simulador local; el build pasa a 1632 pruebas en 221 clases y el JAR a SHA-256 `4c9d213d915187d50365954c6834538e92d10b823726e4dbf6153e9fa3ee9b83` (tras la auditoría de M18) (ver [M18](M18-Duelo-de-mobs-controlados.md#estado-de-implementación) y la sección 7). **Nota de M15 (entrega F):** después de este informe, M15 añadió los propósitos tipados de las skills; el build pasa a 1350 pruebas en 189 clases y el JAR a SHA-256 `690b60a0d11315882003f515e7322b47ce5be778fe70754ff19bffc3db67af23` (ver [M15](M15-Primitivas-tipadas-de-skills.md#estado-de-implementación) y el [CHANGELOG](../../CHANGELOG.md#artefacto)); las cifras de la sección 1 son las de M14. **Nota de M16 (entrega F):** M16 añadió preguntas distinguibles con nivel de detalle (`compact` por defecto), idoneidad opcional, contingencias hipotéticas, ventanas de resultado y el banco etiquetado de 60 escenarios con su herramienta de re-banco; el build pasa a 1484 pruebas en 201 clases y el JAR a SHA-256 `41c21f9c46aec78f8cef0d8b208f1e8dedab3c52bb52e161bcc2bbf1b091a08a` (ver [M16](M16-Efectividad-de-las-respuestas-de-Jev.md#estado-de-implementación)). El re-banco de pago de M16.10 se hizo el 2026-09-30 (720 llamadas, ≈ 0,0315 USD) con las 60 etiquetas revisadas. Con él, el usuario cambió los defaults a `decision.questionDetail: full` y `decision.minimumConfidence: 0.45`, y DT-31 quedó Resuelta ([resultado](M16-Efectividad-de-las-respuestas-de-Jev.md#resultado-del-re-banco-m1610-2026-09-30)). **Nota de M17 (entrega F):** M17 generaliza participantes y objetivos a cualquier entidad viva, con una política de selectores nativos y bandos. Con los defaults, las solicitudes y los eventos son idénticos a los de antes de M17, como demuestran las referencias doradas de M17.0. El build pasa a 1576 pruebas en 211 clases y el JAR a SHA-256 `ee7e4be29ae516be86e9602581f7aa5a3b1d097e0c92cf880b3124bb232cbc2b` (tras la auditoría de M17) (ver [M17](M17-Participantes-y-objetivos-como-entidades.md#estado-de-implementación)). Resume lo hecho en local, lo verificado con la cuenta real de TypeSafe, lo pendiente en el servidor, las decisiones técnicas abiertas y lo que necesita la revisión del usuario. No sustituye al informe final de M13 (`DocumentacionParaDesarrollar/Evidencia-0.0.1/Informe-final.md`, guion 0.0.1 §10), que se escribe cuando haya evidencia de servidor. Índice: [README](README.md) · Cambios: [CHANGELOG](../../CHANGELOG.md).

## 1. Hecho y verificado en local

| Qué | Resultado |
|---|---|
| Módulos | M01 a M14 implementados; estado de cada uno en el [índice](README.md#módulos) |
| Build | `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` (también con `--rerun-tasks`): `BUILD SUCCESSFUL`, sin avisos de javac ni de Javadoc, sin red |
| Pruebas | **1230 pruebas en 180 clases, 0 fallos** (1208 de M13 + 22 de M14: `UserGuidesTest` 8 y `UserTestPackTest` 14) |
| Javadoc | Estricto desde M14 (`-Xdoclint:all` y `-Werror`): un miembro público sin documentar rompe el build (comprobado en una copia de trabajo) |
| JAR | `build/libs/ThePredict-0.0.1.jar`, 743 858 bytes, SHA-256 `14c7180b4b4b039c667ef6f17ae362ef6769912ae42edf953d7e6c5f9b895fc7`, reproducible |
| `verifyPluginJar` | Recursos obligatorios, versión de `plugin.yml` = versión del proyecto, `schemaVersion: 2` y `apiKey: ''`, Java 25, sin dependencias ni paquetes retirados, sin clases de prueba, de herramientas de pago ni de medida, sin secretos ni `.env`. Cada comprobación nueva se probó introduciendo su fallo en una copia de trabajo: todas fallan cuando deben |
| Arquitectura y reglas | `ArchitectureRulesTest` verde; C9 (ningún comentario salvo Javadoc) en todo el código tocado |
| Guías | [Guía del autor](../../Guias/Guia-del-autor.md) y [guía del administrador](../../Guias/Guia-del-administrador.md): cada fragmento YAML carga y se vincula; cada clave de `config.yml` con tipo, unidad, default y rango; cada `ServiceState` y `FallbackReason` con causa y acción; comandos y permisos (`UserGuidesTest`) |
| Paquete de pruebas | [paquete-pruebas/](paquete-pruebas/README.md): guía por sesiones, archivos listos para copiar validados con el cargador real de perfiles y el vinculador de ThePredict sobre dobles de MythicMobs; valida YAML y nombres, no el texto (`UserTestPackTest`), plantilla de informe y checklist |
| Red | Ni este módulo ni sus pruebas hicieron llamadas de red (solo el servidor de contrato en `127.0.0.1` de la suite); nadie leyó `.env`; no se arrancó ningún servidor de Minecraft |

## 2. Verificado con la cuenta real de TypeSafe

Ejecutado el 2026-09-29 por el orquestador con la aprobación del usuario, desde la máquina de desarrollo (M13; [comprobación](evidencia/live-check-2026-09-29.md), [banco](evidencia/live-scenario-bench-2026-09-29.md)):

- `GET /v1/models`: 20 de 20 `READY` (`credential valid; model unverified until first inference`); p50 141 ms, p95 179 ms, máximo 534 ms.
- `POST /v1/systemone`: 1 200 de 1 200 decisiones respondidas por `jev-1.13.0`, sin fallos; p50 179 ms, p95 228 ms, p99 328 ms, máximo 513 ms; 735 800 tokens, 0,0309 USD.
- Errores reales: modelo inexistente → HTTP 400 `api_usage_error` (corregido: `INVALID_MODEL`); clave inválida → 401 con `detail` en forma de objeto (DT-30, `Resuelta`).
- Calibración: 1,59 / 1,73 / 1,82 caracteres por token (la guarda pasa a 1,5); 613 tokens por decisión frente a 590 estimados (**medido antes de M15**: con los criterios de M15.7 cada solicitud crece (1 mob y 1 jugador: 2 253 → 3 755 caracteres; 16 × 16: 23 652 → 47 684), así que el consumo por decisión sube; M16 (`decision.questionDetail`) lo vuelve a medir: el re-banco de M16.10 da 953 tokens reales por solicitud de producción con `compact` y 1 884 con `full`, el default desde el 2026-09-30); tolerancia de la suma ± 0,02 confirmada. Umbrales de confianza solo respaldados de forma preliminar (sección 5).
- No observado: 403, 429 y 529.

## 3. Pendiente en el servidor (lo ejecuta el usuario)

Todo lo que exige juego está en el [paquete de pruebas](paquete-pruebas/Guia-de-pruebas-por-sesiones.md), ordenado por sesión:

- **Fase 1, sin clave (costo cero):** sesiones 1 a 13: instalación limpia, pack de verificación y permisos, depuración y vocabulario (incluidos los ejemplos de la guía del autor), metas y movimiento, señales y trigger, boss de ejemplo, ciclo de vida, percepción, ejecución y fallback, amenaza, piloto y carga, A/B local y sesión larga.
- **Fase 2, con clave (pago, cuando el usuario decida):** K1 (un solo mob, 60 s: decisiones de Jev y costo real, ≈ 0,002 USD), K2 (pérdida de servicio), K3 (latencia desde el host), K4 (carga) y K5 (A/B con Jev y banco grabado).
- **Entrega F:** sesiones 15 (propósitos), 16 (efectividad; 16.2 con clave), 17 (participantes como entidades, sin clave; DT-36 a DT-39) y 18 (duelo de mobs controlados: 80 rondas sin clave y, con aprobación, 30 con clave, ≈ 0,19 USD con los tokens reales y ≈ 0,32 USD con la cota W7; DT-40).
- **Aceptación alojada (M14.5):** ocho pasos, repartidos en esas sesiones más un paso final sin el plugin ([guion 0.0.1, §12](Guion-servidor-0.0.1.md#12-aceptación-alojada-m145)).
- **Capacidad y defaults definitivos:** pendientes de medición en servidor (guion 0.0.1, §5.6 y §6.5). Hasta M16 ningún default cambió en la 0.0.1. El re-banco de M16.10 (2026-09-30) cambió `questionDetail` a `full` y `minimumConfidence` a 0,45.

Para empezar: [paquete-pruebas/README.md](paquete-pruebas/README.md), sesión 1.

## 4. Decisiones técnicas abiertas

| Estado | Entradas |
|---|---|
| `Abierta` (esperan evidencia de servidor; cada módulo usa su alternativa) | DT-00 a DT-29 |
| `Resuelta` | DT-30 (errores reales de System One) |
| `Descartada` | DT-42 (golpes vanilla automáticos; decisión del usuario, 2026-09-30: el daño cuerpo a cuerpo lo dispara una skill) |
| `Resuelta en parte; Abierta en umbrales` | DT-31 (caracteres por token, estimación de tokens y tolerancia resueltos; umbrales de confianza, margen y `timeoutMillis` abiertos) |

Cada entrada, con su paso del paquete, está en el [checklist](paquete-pruebas/Checklist-DT-y-casillas.md).

## 5. Revisión pendiente del usuario

1. **Confianza mínima (DT-31, punto clave).** A `minimumConfidence` 0,5, **el 66,7 % de las respuestas pagadas del banco se descartaron por confianza baja** (`LOW_CONFIDENCE`). Detalle, curva y opciones en el [registro de cambios](../../CHANGELOG.md#revisión-pendiente-del-usuario). Opciones: mejor redacción de las preguntas y etiquetas, o un `minimumConfidence` por perfil (la 0.0.1 ya acepta `Timing.MinimumConfidence`). **Resuelto el 2026-09-30:** M16 mejoró el planteamiento y el re-banco eligió 0,45 con `full` por la regla de M16.7. Con ese umbral se conservan el 85,4 % de los escenarios, con un 90,2 % aceptable y 0 prohibidos. La muestra es modesta: 48 escenarios informativos × 2 repeticiones.
2. **Etiquetas del banco de calibración.** Desde M16 la calibración usa el banco de 60 escenarios `src/test/resources/scenarios/bank/` (hoja con `./gradlew labelReviewSheet --offline`). El usuario revisó las 24 de `core24` el 2026-09-29 y **las 36 restantes el 2026-09-30: las 60 están `REVIEWED`**. Las semillas de M13 son cinco (`src/test/resources/scenarios/seed/`): M16.9 retiró `seed-low-health`, cuyas dos opciones eran aceptables.
3. **Precio de Jev** (0,042 USD por millón de tokens de entrada): confirmarlo en la documentación de TypeSafe antes de las sesiones de pago.
4. **Ubicación de las guías** en `Guias/` en la raíz (propuesta de M14.1); si prefieres otra, solo cambian los enlaces.
5. **Canal de publicación** (M14.6): pendiente. No se creó repositorio, tag ni release.
6. **MythicScript 2077:** los pasos para añadir el vocabulario están en la guía del autor (§13); la extensión no se modificó.

## 6. Decisiones de M14

| Decisión | Motivo |
|---|---|
| Javadoc estricto (`-Xdoclint:all`, `-Werror`) | Todo el código cumple C9; un olvido rompe el build (M14.3) |
| JAR reproducible (sin marcas de tiempo, orden fijo) | La huella registrada debe poder comprobarse al reconstruir (M14.3, M14.6) |
| `verifyPluginJar` también rechaza clases de prueba, de herramientas de pago y de medida, y secretos | Las herramientas de pago y las medidas viven en `src/test` (M13); el JAR nunca debe llevarlas ni llevar una clave (W6) |
| Guías en `Guias/`, en español | Ubicación propuesta por M14.1; los README del JAR (en inglés) enlazan a ellas |
| Paquete de pruebas en `Implementacion-0.0.1/paquete-pruebas/` | Reagrupa los guiones sin duplicarlos de forma contradictoria; una prueba compara sus archivos con los bloques de los guiones |
| Los mobs del pack de M01 §1.4 llevan `Despawn: false` | Sin él despawnean al descargar el chunk y el paso 4.3 no se podría observar; el guion de M01 se actualizó igual |
| Guion 0.0.1 gana la §12 (aceptación alojada) | M14.5 lo pide; el paso 8 (sin el plugin) no existía |

## 7. Duelo de mobs controlados (M18)

**Local listo tras la auditoría; el duelo en servidor (sesión 18) y el duelo con clave están pendientes.** El informe del duelo se escribe con la [plantilla](paquete-pruebas/Plantilla-informe-duelo.md) cuando el usuario ejecute la sesión 18. Lo medido en local, sin red y sin costo, con el simulador de M18.7 (el motor real de ThePredict, dobles de los puertos y un mundo 2D; [matriz completa](evidencia/m18-simulador-2026-09-29/duel-local.md)), con el paquete corregido (`Damage: 0`, sin `punch`, contexto `healthy`); **no afirma superioridad** (plan §12), solo describe esta base:

| Modo | A (rojo ThePredict) | B (azul ThePredict) | C (los dos) | D (clásicos) |
|---|---|---|---|---|
| `NONE` (sin clave, 200 semillas) | puntuación de rojo 0,420 [0,354; 0,489] | 0,570 [0,501; 0,637] | 0,500 [0,431; 0,569] | 0,550 [0,481; 0,617] |
| `ORACLE(0,85)` (200 semillas) | 0,110 [0,074; 0,161] | 0,865 [0,811; 0,906] | 0,480 [0,412; 0,549] | 0,550 [0,481; 0,617] |

Matriz regenerada el 2026-09-30 con la disponibilidad en tiempo real de M10 (cada skill sale a su propio `Cooldown`); los perfiles del duelo no cambiaron. Antes: `NONE` A 0,255, B 0,730, C 0,515; `ORACLE` A 0,055, B 0,950, C 0,450; D igual. C y D siguen simétricos (sus intervalos incluyen el 50 %).

- D es simétrico dentro de su intervalo (incluye el 50 %); ningún cast duplicado en ningún brazo ni modo; un duelista de ThePredict forzado a nivel 4 lanza exactamente como el clásico.
- En esta base el lado de ThePredict gana menos rondas que el clásico sin clave y con el oráculo del simulador (que puntúa con la misma función que el plan local). No es una conclusión sobre Jev: con la cuenta real, Jev lee las descripciones; la sesión 18 lo mide.

**Límite conocido (DT-42):** mientras ThePredict controla un mob, su meta base de ataque no golpea (M05); el golpe vanilla solo llega como una choice `VANILLA_MELEE`, que ocupa la táctica. Un jefe clásico suma el golpe vanilla de `meleeAttack` a sus skills por trigger, así que un jefe de ThePredict pierde ese daño pasivo frente al mismo jefe clásico. El duelo lo iguala con `Damage: 0` en los dos lados. **Decisión del usuario (2026-09-30): DT-42 descartada:** el daño cuerpo a cuerpo lo dispara una skill; ThePredict no da golpes vanilla automáticos. El autor pone el daño cuerpo a cuerpo de un jefe controlado en una skill de su táctica (o en una choice `VANILLA_MELEE`), que sale al ritmo de su `Cooldown` (disponibilidad en tiempo real, M10 desviación 29).

**Condición de vida (DT-40 f):** `health{h=<N%}` compara la vida absoluta y en MythicMobs 5.13.1 falla con `%`; el ejemplo del JAR, el paquete de pruebas, las guías y los guiones usan ahora `healthpercent{p=<N%}`.
