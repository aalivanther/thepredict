# M16. Efectividad de las respuestas de Jev

> **Nota de M27 (2026-10-07):** `SemanticPhrases` gana las cuatro frases de historia y `StateDraft` recorta zona, grupo, memoria y después patrones.

> **Estado:** Local listo — re-banco hecho (2026-09-30; defaults nuevos `questionDetail: full` y `minimumConfidence: 0.45`); servidor pendiente (sesión 16; DT-35) · **Entrega:** F (extensión de la 0.0.1) · **Depende de:** M15, M08, M10, M13 · **Desbloquea:** M18 · **Diseño:** [plan §6](../Plan-Jev-y-combate-tactico.md#6-cliente-directo-de-jev-protocolo-system-one), [plan §12](../Plan-Jev-y-combate-tactico.md#12-matriz-de-pruebas-y-evidencia), [M13.6](M13-Suite-carga-latencia-calidad.md#m136-banco-de-escenarios-y-calibración), [DT-31](M01-Registro-de-decisiones.md#dt-31-calibración-con-la-cuenta-real) · **Reglas:** [00](00-Arquitectura-y-reglas.md) · **Dominio:** [01](01-Modelo-de-dominio.md)

## Objetivo

Que cada respuesta de Jev sea **más útil en combate**, no solo que se conserven más. Tres resultados medibles:

1. **Preguntas mejor planteadas:** opciones distinguibles, descritas con su propósito (M15) y con lo que el código ya sabe de "por qué ahora"; instrucciones y contingencias que dicen exactamente qué se pregunta. Con el mismo `minimumConfidence` 0,5 se conservan muchas más respuestas y siguen siendo aceptables.
2. **Calibración demostrada** con un banco de escenarios distintos, con etiquetas revisadas por el usuario y una regla explícita para elegir el umbral por defecto y por perfil.
3. **Efectividad medida por su resultado:** tras adoptar una intención, qué pasó (daño hecho y recibido, alcance logrado, golpes que conectaron, muertes), en el servidor y en los arneses locales. Es la medida que M18 hace visible con el duelo.

## Alcance

### Incluye

- Diagnóstico del banco pagado del 2026-09-29 y resumen citado de la guía de TypeSafe aplicable.
- Opciones distinguibles: deduplicación, aviso del binder y tope por pregunta.
- Nivel de detalle configurable por el usuario (`decision.questionDetail` y `Timing.QuestionDetail`: `compact`, `standard`, `full`; `compact` por defecto en el diseño y `full` desde el re-banco de M16.10), con su coste en tokens y capacidad documentados.
- Criterios estructurados por opción con el propósito de M15 y "encaja ahora / encaja mal ahora".
- Instrucciones estructuradas por tipo de pregunta (`NOW` y contingencias) y mejoras del estado.
- Señal opcional de idoneidad por opción (`noul`) y su regla de aceptación.
- Contingencias con opciones hipotéticas.
- Regla para elegir `minimumConfidence` global y por perfil.
- Métrica de efectividad (`OutcomeWindow`) en el motor, en `/thepredict status` e `inspect`, y en los arneses.
- Banco etiquetado nuevo, formato de etiquetas, procedimiento de revisión para el usuario y corrección de `seed-low-health`.
- Plan de re-banco de pago con llamadas, tokens, costo y objetivos de aceptación.

### No incluye

- Taxonomía de propósitos y sus frases base: M15.
- Participantes no jugadores: M17 (el banco de M16 solo tiene jugadores; M18 añade escenarios con entidades).
- Duelo y simulador de duelo: M18, que reutiliza `OutcomeWindow`.
- Una segunda solicitud dependiente por decisión (descartada, M16.5).
- Preguntas `score` para elegir táctica (descartadas, M16.5).
- Cambiar el modelo fijado `jev-1.13.0` o su precio.

## Base existente

| Archivo | Acción |
|---|---|
| `src/main/java/akyrion/thepredict/adapter/jev/QuestionBuilder.java` | Criterios e instrucciones estructurados; preguntas `noul` de idoneidad (M16.2, M16.3, M16.5). |
| `src/main/java/akyrion/thepredict/adapter/jev/SemanticEncoder.java`, `StateDraft.java`, `SemanticPhrases.java` | Estado con señales del objetivo dentro de `target`, hecho relativo de salud y filtro de eventos; el estado se codifica antes que las preguntas para conocer las rutas (M16.4). |
| `src/main/java/akyrion/thepredict/adapter/jev/RequestEncoder.java`, `ResponseParser.java` | Orden estado → preguntas; análisis de respuestas `noul` (M16.3, M16.5). |
| `src/main/java/akyrion/thepredict/engine/EncounterQuestions.java` | Opciones distinguibles y tope; opciones hipotéticas en contingencias; preguntas de idoneidad (M16.1, M16.5, M16.6). |
| `src/main/java/akyrion/thepredict/domain/IntentPolicy.java`, `Answer.java`, `DecisionRequest.java`, `DecisionResult.java`, `MobFacts.java` | Regla `FitGate`; `FitCheck` y respuestas de idoneidad; `contingencyEligible` (M16.5, M16.6). |
| `src/main/java/akyrion/thepredict/adapter/server/context/ReadyTactics.java`, `FactsAssembler.java` | Elegibilidad hipotética para contingencias (M16.6). |
| `src/main/java/akyrion/thepredict/adapter/server/events/CombatOutcomeListener.java` | Daño hecho y recibido, y muertes, hacia `OutcomeEvents` (M16.8). |
| `src/main/java/akyrion/thepredict/engine/IntentControl.java`, `DecisionMetrics.java`, `Diagnostics.java`, `StatusReport.java`, `SessionReport.java` | Ventanas de resultado y su informe (M16.8). |
| `src/main/java/akyrion/thepredict/config/DecisionSettings.java`, `ProfileSectionParser.java`, `ProfileSchema.java` | `decision.maxOptionsPerQuestion`, `decision.questionDetail`, `decision.fitChecks`, `Timing.QuestionDetail`, `Timing.FitChecks` (M16.1, M16.2, M16.5, M16.7). |
| `src/test/java/akyrion/thepredict/adapter/jev/LiveScenarioBench.java`, `BenchStatistics.java`, `ScenarioDocument.java`, `ScenarioSeedBank.java`, `ScenarioFacts.java` | Brazos, respuestas crudas, estadísticas por escenario, formato de etiquetas 2 (M16.9, M16.10). |
| `src/test/resources/scenarios/seed-2026-09-29/` (copia congelada que crea M15.9 antes de regenerar `seed/`) | Histórico del banco del 2026-09-29, fuente del diagnóstico; `seed/` lo regenera M15 y no se usa para calibrar; el banco nuevo vive en `src/test/resources/scenarios/bank/` (M16.9). |
| `src/test/java/akyrion/thepredict/engine/TacticalAB.java` | Informa efectividad con `OutcomeWindow` simulado por el arnés (M16.8). |

## Guía de TypeSafe aplicada (investigación)

Leída en la documentación pública ([llms-full.txt](https://docs.typesafe.ai/llms-full.txt), revisada el 2026-09-29) y en la [OpenAPI pública](https://api.typesafe.ai/openapi.json) (`ChoiceQuestion.criteria` e `instructions` admiten texto, objeto, arreglo o `null`; `NoulQuestion.criteria` con `true` y `false`). Cada hallazgo, con su consecuencia en M16:

| # | Hallazgo | Fuente (URL, sección) | Consecuencia |
|---|---|---|---|
| G1 | "The option names and their descriptions are both sent to the model, so **write descriptions that separate the options from each other**." | [Choice](https://docs.typesafe.ai/primitives/choice), "Request structure" | Deduplicación y criterios contrastivos (M16.1, M16.2) |
| G2 | La confianza se deriva de la forma de `probabilities`: "a flatter distribution means lower confidence: low confidence on a Choice often means **none of the options are a clear winner** over the others". La fórmula no se publica y "you are never locked into our definition" | [Confidence](https://docs.typesafe.ai/confidence), "Confidence is derived from the probabilities" | Dos opciones iguales parten la probabilidad y bajan la confianza por construcción; el banco registra también margen entre las dos primeras y entropía para comparar (M16.10) |
| G3 | Cuando dos opciones se confunden, describir cada una con un objeto: qué cubre, qué pertenece a la vecina, ejemplos; "Use the same field names across options so the model can compare them directly". Los nombres de campo son libres y el modelo los ve | [Choice](https://docs.typesafe.ai/primitives/choice), "Structured instructions and criteria"; [How to build](https://docs.typesafe.ai/concepts/how-to-build-with-system-one), "Use structure in the questions"; [Advanced: structure](https://docs.typesafe.ai/primitives/advanced), "Structured Choice options" | Criterio de cada opción como objeto con claves fijas (M16.2) |
| G4 | `instructions` puede ser objeto: "put the question in one field and the data that guides the question in the others"; referenciar partes del estado con rutas entre comillas invertidas (`` `ticket.messages[0].text` ``) | [Primitives](https://docs.typesafe.ai/primitives), "Reference specific fields"; [Advanced](https://docs.typesafe.ai/primitives/advanced), "Structured instructions" | Instrucciones estructuradas con rutas al mob y a su objetivo (M16.3) |
| G5 | Lectura literal: "`jev-1.13` answers the question you wrote, not the one you meant … Put boundary cases in the criteria"; sin aritmética ni números ("Keep the arithmetic in code"); menos saltos de indirección; estado irrelevante resta precisión ("Filter first") | [Jev 1.13 jaggedness](https://docs.typesafe.ai/model-jaggedness/jev-1.13), filas 1, 2, 4 y 5 | "Encaja ahora" calculado en código (M16.2); señales del objetivo dentro del mob (M16.4); eventos filtrados (M16.4) |
| G6 | Instrucciones y criterios no deben contradecirse | Jaggedness, "Contradictory instructions and criteria" | La instrucción de contingencia nombra la hipótesis y los criterios se calculan bajo esa misma hipótesis (M16.6) |
| G7 | Un juicio rápido por pregunta; los juicios con varios factores se descomponen y se combinan en código | [Primitives](https://docs.typesafe.ai/primitives), "Ask for one snap judgment per question"; [How to build](https://docs.typesafe.ai/concepts/how-to-build-with-system-one), "Decompose the questions" | La táctica sigue siendo una `choice` conjunta (plan §6: movimiento y skill juntos); los factores que el código conoce van precalculados en la opción |
| G8 | **Choice** = uno de un conjunto sin orden; **Score** = posición en niveles ordenados que describen situaciones (hasta 10); **Noul** = sí/no, "the probability itself is the useful signal", sin `confidence`, una condición por noul, "phrase the question so that a high value means yes" | [Primitives](https://docs.typesafe.ai/primitives), "Choose a question type"; [Noul](https://docs.typesafe.ai/primitives/noul), "Writing a Noul question"; [Score](https://docs.typesafe.ai/primitives/score), "Writing good levels" | Tácticas: Choice. Score descartado (no hay orden entre tácticas). Noul opcional por opción como señal absoluta (M16.5) |
| G9 | "A Choice over options and one Noul per option answer different questions: the Choice is relative, settling *which* option, while each Noul is absolute and can be low for all of them"; no trasladar umbrales de un Noul a un Choice | Jaggedness, "Common-sense structural invariants"; [Skill suggestion](https://docs.typesafe.ai/cookbooks/skill_suggestion) (Choice para cuál, Nouls para si alguno sirve; umbral 0,30) | Regla `FitGate` con umbrales propios, calibrados aparte (M16.5) |
| G10 | Preguntas en una misma solicitud: independientes, en paralelo, "costs only the tokens for the extra questions"; dos solicitudes solo si la segunda depende de verdad de la primera | [Primitives](https://docs.typesafe.ai/primitives), "Ask multiple questions together" y "When one question depends on another"; [Speculative fan-out](https://docs.typesafe.ai/patterns/fan-out) | Nouls y contingencias en la misma solicitud; sin diseño en dos etapas (M16.5) |
| G11 | Choice admite hasta 255 opciones; "give the model the full list … Add an `other` or `none of the above` option when the list might not cover every input" | [Choice](https://docs.typesafe.ai/primitives/choice), "Good practice" | El problema no es cuántas opciones sino que se solapen; tope de seguridad 8 tras deduplicar (M16.1). `hold` hace de opción conservadora; no se añade "none" (no hay acción "ninguna" ejecutable) |
| G12 | Umbrales según el riesgo de la acción; "Test thresholds by plotting confidence against accuracy on your data"; empezar conservador | [Confidence](https://docs.typesafe.ai/confidence), "Thresholds scale with risk"; [Confidence-gated routing](https://docs.typesafe.ai/patterns/confidence-routing); [How to build](https://docs.typesafe.ai/concepts/how-to-build-with-system-one), "Route on uncertainty" | Regla de elección del umbral con curva por escenario distinto (M16.7) |
| G13 | Autoconsistencia: TypeSafe repite su etiqueta mayoritaria el 90,8 % de las veces en un caso límite; con probabilidad máxima ≥ 0,60 el acuerdo sube al 99,2 % con acción automática en el 74,2 % | [Self-consistency: choices](https://docs.typesafe.ai/cookbooks/consistency_choice_cookbook), resumen inicial | Repetir la misma solicitud 200 veces apenas añade información: el banco necesita **escenarios distintos**, no repeticiones (M16.10) |
| G14 | Precio `jev-1.13.0`: 0,042 USD por millón de tokens de entrada (salida gratis); 64k tokens por solicitud y 32k para estado + pregunta más larga; fijar el ID versionado si se calibran umbrales | [Models](https://docs.typesafe.ai/models), "Current models" y "Aliases" | Precio del plan confirmado (punto 3 de la revisión del usuario en el [informe](Informe-0.0.1.md)); estimaciones de M16.10 |
| G15 | El estado es "the material you would present to a panel of experts"; objeto con nombres descriptivos; contenido separado de las preguntas | [State](https://docs.typesafe.ai/concepts/state) | Se conserva el estado como objeto; el contenido de opción va en criterios, no en el estado |

## Diagnóstico del banco del 2026-09-29

Fuentes: [informe del banco](evidencia/live-scenario-bench-2026-09-29.md), `src/test/resources/scenarios/seed-2026-09-29/*.json` (los archivos tal como se enviaron), `ScenarioFacts`, `LiveScenarioBench`, `BenchStatistics`.

| # | Hallazgo | Evidencia |
|---|---|---|
| D1 | **Opciones idénticas.** En cinco de los seis escenarios, `approach` y `press_and_strike` llevaron el mismo criterio literal (`move toward the target and fight up close`), y `keep_distance` era un prefijo exacto de `keep_distance_and_shoot`. Por G2, dos opciones iguales se reparten la probabilidad y la confianza no puede subir | Criterios de `seed-melee-duel.json`, `seed-pillar.json`, `seed-shield.json`, `seed-shooter-backing-away.json`, `seed-tank-and-shooter.json` |
| D2 | **Jev no supo qué hacía ninguna skill.** El banco usó el perfil de ejemplo leído sin binder, así que los `PredictValues` estaban vacíos y ningún criterio decía "uses a skill that …" (desviación 9 de M13). En producción sí llegan | `ScenarioFacts.example()` (`ProfileLoader`), `ProfileParser.parse` ("The skill PredictValues stay empty") |
| D3 | **Inferencia (no verificable sin respuestas crudas, D8): la muestra efectiva serían unas 6 respuestas, no 1 200.** El margen 0,00 da 0,5 % de cambios de mejor táctica entre repeticiones (unas 6 en 1 194), y las cuentas por tramo son **compatibles** con escenarios casi deterministas de 200 respuestas: 248 = 200 + 48; 552 = 400 + 152; 16 + 384 = 400 con 184 + 16 = 200 de un escenario en la frontera de 0,7; y 48 + 152 = 200 de otro en la frontera de 0,3 | Tablas del informe; G13 |
| D4 | **Inferencia (misma limitación): la curva "no monótona" sería la composición de escenarios**, no una propiedad del modelo: el tramo 0,3–0,5 serían sobre todo dos o tres escenarios concretos, y 190 respuestas prohibidas en ese tramo encajan con un solo escenario. Sin las respuestas por escenario no se puede confirmar; por eso el re-banco guarda respuestas crudas (M16.10) y da la tabla por escenario | Tabla de fiabilidad (190 prohibidas en 0,3–0,5) |
| D5 | **Instrucción genérica** ("Which tactic should mob_1 use now against its current target?"): no nombra el objetivo del mob ni qué hechos pesar | `SemanticPhrases.instructions` |
| D6 | **Contingencias no medidas** (0 en el banco) y, en el código, con las mismas opciones que `NOW` aunque la hipótesis cambie la elegibilidad (con salud crítica, `low_health_phase` restringe las tácticas) | `EncounterQuestions.plan` |
| D7 | **Etiquetas sin revisar**; `seed-low-health` ofrece dos opciones y las dos son aceptables, así que acierta siempre y no informa | `labelsProposedBy`; [informe de la 0.0.1](Informe-0.0.1.md#5-revisión-pendiente-del-usuario) |
| D8 | El banco no guarda respuestas crudas: no se puede volver a analizar sin volver a pagar | `BenchStatistics` solo escribe agregados |

Conclusión: el 66,7 % de descartes a 0,5 dice más del planteamiento (D1, D2, D5, comprobados en los archivos) y del banco (D7 comprobado; D3 y D4 inferidos) que del modelo. M16 corrige primero el planteamiento y el banco, y solo después elige umbrales.

## Subpuntos

### M16.1 Opciones distinguibles y tope por pregunta

- **Propósito:** que nunca lleguen a Jev dos opciones con el mismo texto (D1, G1) y que una pregunta no crezca sin control.
- **Diseño:** dos pasos, cada uno donde vive su información.
  - **Paso 1, en el motor (puro): tácticas repetidas y tope.** Regla pura `domain.OptionSet`:

    ```java
    static List<TacticId> distinct(List<TacticId> ordered, TacticalProfile profile, Optional<TacticId> keep)
    static List<TacticId> capped(List<TacticId> distinct, int maxOptions, Optional<TacticId> keep)
    ```

    `distinct` quita solo tácticas **idénticas en todo** (mismo movimiento, propósito igual según `SkillPurpose.semanticallyEqual` y misma descripción), conservando la primera en el orden recibido (prioridad descendente y luego ID, como hoy) salvo que otra sea `keep` (la táctica de la intención vigente, para que la histéresis de M10.5 siga comparando contra ella). `capped` corta a `maxOptions` conservando siempre `keep`. `engine.EncounterQuestions.options` aplica los dos con `EngineSettings.maxOptionsPerQuestion` y la táctica vigente (el plan recibe `Function<MobKey, Optional<TacticId>> current`) y la guarda en `QuestionSpec.keep` (nuevo, `Optional<TacticId>`). Si queda una sola opción, se resuelve en local como hoy.
  - **Paso 2, en `adapter.jev`: deduplicación sobre el texto final.** Solo quien escribe el texto sabe si dos opciones se leen igual (el nivel `compact` omite la descripción, el alcance y el cooldown, y recorta a 120 caracteres; `standard` y `full` escriben el cooldown y la preparación solo con `authored()`). Por eso la deduplicación usa **el propio texto final**, la salida exacta de la misma función que escribe el cuerpo, después del recorte:

    ```java
    record RenderedCriterion(String text) {}                       // texto final: en compact la cadena; en standard/full, el objeto JSON serializado
    enum Discriminator { NOTE_EXCERPT, REACH_BAND, BEST_WHEN }
    final class CriterionRenderer {                                  // adapter.jev, puro
        RenderedCriterion render(Tactic tactic, TacticalProfile profile, QuestionDetail detail,
                                 OptionFit fit, Set<Discriminator> discriminators)
    }
    final class OptionTextDedup {                                    // adapter.jev, puro
        record Result(List<TacticId> sent, Map<TacticId, RenderedCriterion> texts, Map<TacticId, TacticId> mergedInto) {}
        static Result apply(List<TacticId> options, Optional<TacticId> keep,
                            BiFunction<TacticId, Set<Discriminator>, RenderedCriterion> render)
    }
    ```

    Algoritmo, iterativo y acotado (como mucho 4 pasadas: la inicial y una por discriminador):
    1. Renderiza cada opción a su nivel, **con el recorte aplicado**.
    2. Agrupa las opciones por `RenderedCriterion` idéntico.
    3. A cada miembro de cada grupo con colisión le añade **el siguiente discriminador que le aplica** (los que no le aplican se saltan sin gastar pasada; así, una opción sin descripción recibe `REACH_BAND` ya en la segunda pasada), en este orden: `NOTE_EXCERPT` (los primeros 40 caracteres de la `Description`, cortados en un espacio; se salta para quien no tiene descripción), `REACH_BAND` (banda de M15.7 sobre `reachBlocks`, o `radiusBlocks` en `AREA_AROUND_SELF`; **se salta si la entrega no tiene ninguno**, como `SELF` o una entrega a distancia sin `Reach`), `BEST_WHEN` (la primera `UseCondition` de `BestWhen`; se salta si no hay).
    4. Vuelve a renderizar con el recorte, que **nunca quita un discriminador ya añadido**.
    5. Repite desde 2 hasta que no haya colisiones o se agoten los discriminadores. Cada pasada añade como mucho un discriminador por opción y hay tres, así que el bucle hace como mucho **4 pasadas** (la inicial y tres).
    6. Si dos opciones siguen idénticas, son indistinguibles para Jev: se envía la primera por prioridad (o `keep`) y las demás quedan en `mergedInto`.

    Presupuesto de longitud en `compact`: una opción **sin colisión** mantiene el tope de **120** caracteres (recortando, en este orden, la preparación, el daño y la `poor fit now`). Una opción **con colisión** puede crecer hasta un tope **duro de 160**: sus discriminadores nunca se quitan para volver a 120; se recortan las mismas piezas y, si hace falta, el extracto hasta 12 caracteres. El caso peor cabe por construcción: la base recortada más larga ("circle to the target's side; area hit around self summons allies", 65) más `; note: ` y 12 caracteres (20), `; reach: ranged` (15) y `; best when caster nearly dead` (30) suma 130 < 160, y una prueba lo comprueba. Formas de los sufijos en `compact`: `note: <extracto>`, `reach: melee|short|ranged|far`, `best when <forma corta>` ("target nearly dead", "target shielding", "target fleeing", "caster nearly dead", "enemies grouped", "target at range", "target in melee"). En `standard` y `full` el texto ya incluye descripción, alcance, `best_when`, cooldown y preparación, así que una colisión allí significa contenido idéntico y el paso 6 se aplica directamente.
  - **Preparación antes del envío (deduplicar ANTES de despachar).** La deduplicación por texto cambia qué preguntas y opciones se envían, así que ocurre **antes** de reservar presupuesto y de abrir la solicitud en vuelo; nada del motor ve nunca una respuesta sintética.
    - Puerto `engine.DecisionClient`, firmas nuevas:

      ```java
      public interface DecisionClient {
          Preparation prepare(DecisionRequest request);                        // puro; lo llama el dispatcher en el hilo del servidor
          CompletableFuture<DecisionResult> decide(PreparedRequest prepared);   // envía el cuerpo ya codificado; no vuelve a codificar
          CompletableFuture<ServiceCheck> check();
          void close();
          static DecisionClient unavailable(ServiceCheck check) { … }          // prepare → Invalid; decide → Failed como hoy
      }

      sealed interface Preparation permits Preparation.Ready, Preparation.NothingToAsk, Preparation.Invalid {
          record Ready(PreparedRequest prepared) implements Preparation {}
          record NothingToAsk(Set<MobKey> localMobs) implements Preparation {}   // todas las preguntas cayeron
          record Invalid(String detail) implements Preparation {}               // el cuerpo no cabe o una pregunta es inválida
      }

      record PreparedRequest(DecisionRequest sent,                 // solo preguntas enviadas, con `options` = opciones enviadas y `fitChecks` solo de ellas
                             String body,                          // JSON exacto que se enviará (texto opaco para `engine`)
                             Set<QuestionKey> droppedQuestions,    // preguntas quitadas (NOW con < 2 opciones distintas y sus contingencias)
                             Set<MobKey> localMobs,                // mobs cuyo NOW no se envía
                             int bodyChars) {}
      ```

      `PreparedRequest` y `Preparation` viven en `engine` junto al puerto (como `InboxEntry`), porque llevan el cuerpo como texto opaco que `domain` no debe contener (01 §9). `prepare` es la codificación de siempre (T2: construir la solicitud desde datos inmutables), ahora llamada en el hilo del servidor; su coste, medido tras la auditoría con el código de M16 (columna "Prepare us" de la tabla por nivel de `JevMeasurements` en `m13Measure`, p50 en caliente), es de unos 17 µs para la solicitud de producción en `compact`, 188 µs para 16 × 16 en `compact`, 425 µs en `standard` y 475 µs en `full` (la auditoría midió 26 / 147 / 357 / 405 µs en otra máquina), dentro de `maxTickWorkMicros` (2 000 µs), y se mide como tramo `PREPARE` de `DecisionMetrics`. (La cifra de M13, 17–27 µs y 118 µs, era de la codificación anterior a M16.)
    - Qué hace `prepare`: renderiza y deduplica cada pregunta por texto (arriba). Una pregunta que queda con **menos de 2 opciones distintas** se quita. Si se quita el `NOW` de un mob, **se quitan también sus contingencias**, para que `AnswerGroups` conserve su invariante de un `NOW` por mob; ese mob entra en `localMobs`. Las `FitCheck` de opciones fusionadas o de preguntas quitadas se quitan antes de codificar. Si la codificación falla (`SystemOneRequestException`, por ejemplo `RequestSizeGuard`), devuelve `Invalid` en lugar de lanzar.
    - Flujo de `DecisionDispatcher.dispatch`, en este orden:
      1. captura y plan de preguntas (como hoy; sin preguntas → `NO_QUESTIONS`);
      2. `tracker.allowsDispatch` (como hoy → `SERVICE_UNAVAILABLE`);
      3. **`sequence = encounter.schedule().nextSequence()`**, tomada justo antes de `prepare` y usada en la `DecisionRequest`. Que una secuencia tomada no llegue a enviarse (`NO_BUDGET`, `NOTHING_TO_ASK`, `PREPARE_FAILED`) deja huecos inofensivos: el dispatcher nunca despacha mientras hay otra solicitud en vuelo del encuentro, y `DecisionAcceptor` (líneas 71–72) solo compara con la última tomada. `schedule().sequence()` pasa a significar "última secuencia tomada" (Javadoc actualizado);
      4. **`client.prepare(request)`**:
         - `Invalid` → sin reserva ni solicitud en vuelo; `metrics.increment(INVALID_REQUEST)`, una línea de aviso limitada a una cada 10 s con el detalle y nunca el cuerpo, y **ningún efecto en `ServiceStatusTracker`**: es un fallo local del encuentro (por ejemplo, un encuentro demasiado grande para la guarda de tamaño), no del servicio, así que el resto de encuentros sigue preguntando a Jev y los mobs de este siguen la cadena de fallback como con `NothingToAsk`; resultado `PREPARE_FAILED`. (Cambio de diseño tras la auditoría: antes llamaba a `tracker.onResult(Failed(INVALID_REQUEST, …))` y un solo encuentro sobredimensionado pausaba el servicio para todos. Los fallos reales del servicio conservan su efecto.)
         - `NothingToAsk` → **ni envío, ni reserva, ni `InFlightRequest`, ni `DecisionResult`, ni efecto en `ServiceStatusTracker` ni en `ModelExpectation`**; resultado `NOTHING_TO_ASK`, que cuenta el propio `counted()` del dispatcher como cualquier otro resultado (no hay un segundo contador). El coordinador llama igual a `Agenda.decided` (como tras cualquier despacho), así que el encuentro no se vuelve a ofrecer en cada tick;
         - `Ready` → sigue;
      5. **presupuesto con el tamaño preparado:** `RequestBudget.tryReserve(world, encounter, tokens, now)` con `tokens = ceil(bodyChars / cpt)`. `cpt` (caracteres por token) empieza en 1,5 y aprende con cada respuesta: `cpt += 0,2 · (bodyChars / input_tokens − cpt)` y después se acota a [1,3; 2,0] (`bodyChars` sale de la `InFlightRequest` y `input_tokens` de `usage`). Sustituye a la fórmula 400 + 120 por pregunta + 60 por participante: `RequestBudget.estimateTokens(q, p)` **se retira del motor**; si alguna herramienta de medida (`JevMeasurements`, `EngineMeasurements`) aún necesita una estimación sin cuerpo, se copia a esa herramienta en `src/test`, y `RequestBudgetTest` y `DecisionDispatcherTest` pasan a probar la reserva por tamaño. `RequestSizeGuard` **sigue fijo en 1,5 caracteres por token**: es la cota prudente para los límites de 32k y 64k tokens de Jev y el valor aprendido nunca la mueve. Sin presupuesto → `NO_BUDGET`;
      6. `InFlightRequest` con **las preguntas enviadas** (`prepared.sent().questions()`) y `bodyChars`, `client.decide(prepared)` y el resto como hoy.
    - Los mobs de `localMobs` (y de `NothingToAsk`) se resuelven sin código nuevo; los resuelve `FallbackStep` en el ciclo reactivo (regla awaiting, `LocalPlanner.choose`), como hoy a un mob sin pregunta; nivel `LOCAL`; no pasan por `DecisionAcceptor`, no llaman a `markAnswered` y no afectan a `ServiceStatusTracker` ni a `ModelExpectation`.
    - `SystemOneClient.decide(prepared)` envía `prepared.body()` tal cual; `ResponseParser` valida cada respuesta contra `prepared.sent()` (sus `options` ya son las enviadas), así que un `Answer` solo nombra opciones enviadas. `OptionContext` (M16.2) se calcula dentro de `prepare` con los hechos de la solicitud (regla pura de `domain`).
  - Nueva clave global `decision.maxOptionsPerQuestion`: entero, default **8**, rango 2–32. No se amplía desde un perfil (regla de límites globales de M03). El ejemplo de M15 tiene 8 tácticas, así que el default no corta nada en él; el tope protege perfiles grandes del coste en tokens (M16.10).
  - Aviso del binder `BindingWarnings.indistinguishable` para tácticas idénticas en todo (paso 1): `Tactics.<a> and Tactics.<b> describe the same option to Jev; Jev only sees '<a>'`. Las fusiones del paso 2 se cuentan en `DecisionMetrics` (`options_merged`) y aparecen en `status`.
- **Archivos:** crear `domain/OptionSet.java`, `adapter/jev/CriterionRenderer.java` (con `RenderedCriterion`), `adapter/jev/OptionTextDedup.java` (con `Discriminator`), `engine/Preparation.java`, `engine/PreparedRequest.java`; modificar `engine/DecisionClient.java` (`prepare`, `decide(PreparedRequest)`), `engine/UnavailableDecisionClient.java`, `engine/DecisionDispatcher.java` (orden nuevo y resultados `NOTHING_TO_ASK`, `PREPARE_FAILED`), `engine/RequestBudget.java` (reserva por tamaño preparado), `domain/QuestionSpec.java` (`keep`), `domain/SkillPurpose.java` (`semanticallyEqual`), `adapter/jev/QuestionBuilder.java`, `RequestEncoder.java`, `ResponseParser.java`, `SystemOneClient.java`, `engine/EncounterQuestions.java`, `engine/EngineSettings.java`, `engine/DecisionMetrics.java`, `config/DecisionSettings.java`, `src/main/resources/config.yml`, `adapter/server/binding/BindingWarnings.java`; pruebas `OptionSetTest`, `CriterionRendererTest`, `OptionTextDedupTest`, `RequestEncoderTest`, `ResponseParserTest`, `EncounterQuestionsTest`, `DecisionSettingsTest`, `MythicProfileBinderSuiteTest`.
- **Reglas:** A5 (tope explícito y bucle acotado), C2, C3 (`RenderedCriterion` y `Discriminator` tipados, nada de `List<Object>`), C13, I4, D2, D4, T2.
- **Pruebas:** del flujo (`DecisionDispatcherTest`, `CoordinatorTest` con dobles): `evaluated` nunca planifica (el mob quitado lo resuelve `FallbackStep` en el ciclo reactivo); la secuencia se toma antes de `prepare` y un hueco de secuencia no hace descartar la respuesta siguiente; la regla de aprendizaje de `cpt` y su acotación; `RequestSizeGuard` no cambia con `cpt`; `NothingToAsk` no reserva, no abre solicitud, no produce resultado ni toca `ServiceStatusTracker` ni `ModelExpectation`, cuenta el resultado `NOTHING_TO_ASK` y el encuentro recibe `Agenda.decided`; un `NOW` quitado quita sus contingencias y `AnswerGroups` sigue viendo un `NOW` por mob; el mob quitado queda en `LOCAL` por `LocalPlanner` sin `markAnswered`; `Invalid` no tiene efecto en el tracker (el servicio sigue `READY`), se cuenta como `INVALID_REQUEST`, emite un aviso limitado a uno cada 10 s sin el cuerpo, y los mobs del encuentro juegan en local por la cadena de fallback; un encuentro sobredimensionado no pausa a los demás; la reserva usa el tamaño preparado; `decide` no vuelve a codificar (el doble del cliente cuenta codificaciones). **Invariante de texto única, "ninguna pregunta contiene dos cadenas de criterio iguales"**, como prueba de propiedad en los tres niveles sobre: el banco completo (M16.9); el ejemplo de M15.9; y perfiles generados de peor caso (tres papeles largos, `CONE` y `FLANK`, todas las frases presentes, entregas sin `Reach`, propósitos `authored()` e inferidos, todas las clases de cooldown, descripciones iguales en sus 40 primeros caracteres, tácticas que solo difieren en el cooldown o en la preparación). Además: ninguna opción supera 120 caracteres sin colisión ni 160 con colisión; nunca se quita un discriminador añadido; el bucle termina en ≤ 4 pasadas; `keep` sobrevive a una fusión; una pregunta que queda con una opción se quita en `prepare` junto con sus contingencias; `ResponseParser` rechaza una respuesta que nombra una opción fusionada; tope 2 y 32 en frontera; perfil de 255 tácticas → 8 opciones.
- **Aceptación:**
  - [x] Ninguna solicitud contiene dos criterios iguales **en ninguno de los tres niveles**, incluido el default `compact` (propiedad sobre banco, ejemplo y peor caso).
  - [x] La táctica vigente siempre está entre las opciones enviadas de su mob.

### M16.2 Nivel de detalle configurable y criterios con propósito y "por qué ahora"

- **Propósito:** que el usuario decida cuánto detalle (y cuánto gasto) lleva cada pregunta, con un default compacto, y que cada opción diga a Jev qué hace (M15) y, si se pide, qué del momento la favorece o la desaconseja, calculado en código (G3, G5).
- **Diseño:**
  - **Nivel de detalle** (decisión del usuario): clave global `decision.questionDetail: compact | standard | full` (default **`compact`** en el diseño; **`full`** desde el re-banco de M16.10, decisión del usuario del 2026-09-30) y `Timing.QuestionDetail` por perfil (cualquiera de los tres; solo afecta a las preguntas de los mobs de ese perfil). **Una sola fuente en tiempo de ejecución:** `DecisionTiming.questionDetail`; `config` resuelve el global como default de cada perfil al cargar (igual que `minimumConfidence`), y el motor y el codificador solo leen el valor del perfil (C13). Los límites de tokens siguen siendo configurables y globales (`decision.maxInputTokensPerMinute` y los de M09); el detalle no los amplía. `standard` y `full` son opcionales y **solo pasan a ser el default** si el re-banco (M16.10) muestra, frente a `compact`, al menos +10 puntos de respuestas conservadas a `minimumConfidence` 0,5 con el mismo acierto (no más de 2 puntos por debajo) y sin más prohibidas.

    | Nivel | Criterio de cada opción | Instrucción | Contingencias | Idoneidad `noul` (M16.5) |
    |---|---|---|---|---|
    | `compact` (default) | **Texto** de ≤ 120 caracteres: `<movimiento corto>; <skill corta>` y, como mucho, **una** frase `poor fit now: …` (la primera de `OptionContext` en el orden de la tabla de abajo); ninguna clave JSON repetida; sin `fits_now` | Texto corto, sin frase de `focus` | Como mucho 5 opciones (`COMPACT_CONTINGENCY_OPTIONS`, las de mayor prioridad tras deduplicar; siempre la vigente) | No |
    | `standard` | Objeto con `movement`, `skill`, `best_when`, `risks`, `note` (M15.7 completo) | Objeto de M16.3 sin `focus` | Hasta `maxOptionsPerQuestion` | No |
    | `full` | `standard` + `fits_now` y `poor_fit_now` (hasta 3 cada una) | Objeto de M16.3 con `focus` | Hasta `maxOptionsPerQuestion` | Solo aquí, si `fitChecks` está activo |

    Cuando el texto `compact` supera 120 caracteres se quitan, en este orden, la preparación, el daño y la frase `poor fit now`. Las opciones que colisionan con otra reciben discriminadores y pueden llegar a 160 (M16.1); ese recorte nunca quita un discriminador. Formas cortas (inglés, fijas, en `SemanticPhrases`):

    | Pieza | Formas cortas |
    |---|---|
    | Movimiento | `APPROACH` "close in"; `KEEP_DISTANCE` "hold mid range"; `RETREAT` "retreat"; `HOLD` "hold position"; `FLANK` "circle to the target's side"; `NONE` "native movement" |
    | Entrega | `MELEE` "melee hit"; `PROJECTILE` "projectile"; `HITSCAN` "instant ranged hit"; `AREA_AROUND_SELF` "area hit around self"; `AREA_AT_TARGET` "area hit at target"; `CONE` "cone sweep"; `SELF` "self" |
    | Papel principal distinto de `DAMAGE` | `CONTROL` "stun or slow"; `GAP_CLOSE` "gap closer"; `ESCAPE` "escape"; `DEFENSE` "self shield"; `SUSTAIN` "self heal"; `BUFF` "self buff"; `DEBUFF` "weakens target"; `SUMMON` "summons allies"; `ZONE` "area denial"; `INTERRUPT` "interrupt"; `FINISHER` "finisher"; `OPENER` "opener" |
    | Daño | "low damage", "medium damage", "high damage", "damage" (`UNRATED`); nada con `NONE` |
    | Preparación (solo propósito escrito por el autor) | "slow wind-up" si pasa de 10 ticks; nada si no |
    | `poor fit now` | `OUT_OF_REACH` "out of reach now"; `NO_LINE_OF_SIGHT` "no line of sight now"; `SHIELD_BLOCKS_IT` "shield would block it"; `EXPOSED_WHILE_CRITICAL` "too risky at critical health"; `CANNOT_REACH_ON_FOOT` "target unreachable on foot"; `CASTER_ALREADY_HEALTHY` "caster already healthy"; `TARGET_CAN_DODGE` "target can dodge it" |

    Ejemplo `compact` (guardián, objetivo bloqueando a distancia cuerpo a cuerpo): `"press_and_strike": "close in; melee hit, medium damage; poor fit now: shield would block it"`, `"flank_and_strike": "circle to the target's side; melee hit, medium damage"`, `"retreat_and_defend": "retreat; self heal"`, `"approach": "close in"`.
  - **Coste y capacidad por nivel** (solicitud de producción: 1 mob, 1 participante, `NOW` + 1 contingencia, 7 opciones del ejemplo, estado 1 contra 1 de unos 650 caracteres). Tokens reales **estimados** a 1,73 caracteres por token (la media medida, DT-31) y, entre paréntesis, la cota a 1,5 que usan las herramientas. La capacidad se da en "mobs aislados en combate continuo": cada uno es un encuentro que envía **60 solicitudes por minuto** (una por latido de 20 ticks, `Agenda`; M13 midió 600 en 10 min con 1 mob). La capacidad efectiva es el **mínimo** de dos topes independientes de M09:
    - **tokens:** `decision.maxInputTokensPerMinute` (600 000 por defecto) ÷ (60 × tokens por solicitud);
    - **solicitudes:** `decision.maxRequestsPerMinute` (600 por defecto → 10 mobs aislados en todo el servidor) y `decision.maxRequestsPerMinutePerWorld` (300 por defecto → **5 mobs aislados por mundo**); la cuenta de TypeSafe admite **1 200 solicitudes/min** (20 mobs aislados), y `maxRequestsPerMinute` no puede pasar de 1 200 (M03).

    | Nivel | Caracteres por solicitud | Tokens por solicitud | Mobs por tokens a 600 000/min | Mobs por tokens a 1 500 000/min | Tope de solicitudes con defaults: global (600/min) / por mundo (300/min) | Tope de la cuenta (1 200/min) | **Efectivo con defaults**: 1 mundo / varios mundos | **Efectivo con 1 500 000 tokens/min y los dos topes de solicitudes a 1 200** |
    |---|---|---|---|---|---|---|---|---|
    | 0.0.1 actual (5 opciones; estimado a partir de los 2 253 caracteres de M13, no medido: lo medido fueron 613 tokens con solo `NOW`) | ≈ 2 250 | ≈ 1 300 (estimado) | ≈ 7,7 | ≈ 19,2 | 10 / 5 | 20 | 5 / 7 | 19 |
    | `compact` | ≈ 2 100 | ≈ 1 200 (≤ 1 400) | ≈ 8,3 | ≈ 20,8 | 10 / 5 | 20 | **5 / 8** | **20** (manda la cuenta) |
    | `standard` | ≈ 4 000 | ≈ 2 300 (≤ 2 650) | ≈ 4,3 | ≈ 10,9 | 10 / 5 | 20 | 4 / 4 | 10 |
    | `full` | ≈ 5 100 | ≈ 2 950 (≤ 3 400) | ≈ 3,4 | ≈ 8,5 | 10 / 5 | 20 | 3 / 3 | 8 |
    | `full` + idoneidad | ≈ 6 900 | ≈ 4 000 (≤ 4 600) | ≈ 2,5 | ≈ 6,25 | 10 / 5 | 20 | 2 / 2 | 6 |

    Las columnas "efectivo" redondean hacia abajo (un mob no se reparte). Lectura: con los defaults, en **un solo mundo** manda el tope de solicitudes por mundo (5 mobs aislados en `compact`), y en varios mundos, el de tokens (8). `decision.maxInputTokensPerMinute` es un **tope local** de ThePredict, no el límite de la cuenta (250 000 tokens/s y 1 200 solicitudes/min, [Models](https://docs.typesafe.ai/models)); el administrador puede subirlo, pero **subir el tope de tokens no basta**: hay que subir también `decision.maxRequestsPerMinute` y `decision.maxRequestsPerMinutePerWorld`, y ninguno puede superar las 1 200 solicitudes/min de la cuenta, que limitan a unos 20 mobs aislados sea cual sea el detalle. A plena carga, 600 000 tokens/min cuestan ≈ 1,51 USD/h y 1 500 000 ≈ 3,78 USD/h. **Los mobs que comparten encuentro reducen las solicitudes**: un encuentro envía una sola solicitud por latido con una pregunta por mob (M09), así que 3 mobs contra el mismo jugador gastan 60 solicitudes/min en total, no 180; en ese caso manda el tope de tokens, y cada mob añade sus preguntas (≈ 500–800 tokens en `compact`) pero no un estado nuevo. Los mobs sin presupuesto juegan en local (`NO_BUDGET`). La guía del administrador publica esta tabla con los tokens reales del re-banco en lugar de las estimaciones.
  - **Criterio estructurado** (`standard` y `full`): cada valor de `criteria` es un objeto JSON con **las mismas claves posibles en todas las opciones** (G3), omitiendo las vacías:

    | Clave | Contenido | Origen | Nivel |
    |---|---|---|---|
    | `movement` | Frase de movimiento de hoy (`SemanticPhrases.movement`) | Táctica | `standard`, `full` |
    | `skill` | Frase de propósito de M15.7 (entrega, papeles, alcance, área, preparación, daño, cooldown), sin `best when` ni riesgos | Choice | `standard`, `full` |
    | `best_when` | Frases de `BestWhen` | Choice | `standard`, `full` |
    | `risks` | Frases de `Risks` y de `ignoresShield` | Choice | `standard`, `full` |
    | `fits_now` | Hasta 3 frases de "encaja ahora" | `OptionContext` (abajo) | `full` |
    | `poor_fit_now` | Hasta 3 frases de "encaja mal ahora" | `OptionContext` | `full` |
    | `note` | `Description` del autor | Táctica | `standard`, `full` |

    Ejemplo `full` (guardián, objetivo con escudo a distancia cuerpo a cuerpo):

    ```json
    "flank_and_strike": {
      "movement": "circle to the side of the target before striking",
      "skill": "strikes the target up close to deal damage, only inside melee reach, after a short wind-up, deals medium damage, can be used again soon",
      "best_when": ["the target is inside melee reach"],
      "fits_now": ["the target is inside melee reach", "the target's shield does not cover its sides"]
    },
    "press_and_strike": {
      "movement": "move toward the target and fight up close",
      "skill": "strikes the target up close to deal damage, only inside melee reach, after a short wind-up, deals medium damage, can be used again soon",
      "best_when": ["the target is inside melee reach"],
      "fits_now": ["the target is inside melee reach"],
      "poor_fit_now": ["the target's shield would block it"]
    }
    ```

  - Regla pura `domain.OptionContext.of(Tactic, Optional<SkillChoice>, MobFacts, Optional<ParticipantFacts> target, Set<UseCondition> now, MovementSettings)` → `record OptionFit(List<FitReason> fits, List<FitReason> poorFits)`, con `enum FitReason` cerrado (las frases viven en `SemanticPhrases`, 01 §9). `now` es `ActiveUseConditions.of(...)` de M15.8. La usan `full` (listas completas) y `compact` (primera `poorFit`). Reglas, en este orden (se cortan en 3 por lista):

    | `FitReason` | Lista | Condición |
    |---|---|---|
    | `BEST_WHEN_<condición>` (una por `UseCondition`) | fits | `bestWhen ∩ now` |
    | `FLANK_BEATS_SHIELD` | fits | `FLANK` y `TARGET_SHIELDING` |
    | `GOES_THROUGH_SHIELD` | fits | `ignoresShield` y `TARGET_SHIELDING` |
    | `OUT_OF_REACH` | poor | choice en `choicesOutOfReach` y `!PurposeRules.closesDistance` |
    | `NO_LINE_OF_SIGHT` | poor | riesgo `NEEDS_LINE_OF_SIGHT` y objetivo no visible |
    | `SHIELD_BLOCKS_IT` | poor | dañina (M15.6), sin `ignoresShield`, `TARGET_SHIELDING` y movimiento distinto de `FLANK` |
    | `EXPOSED_WHILE_CRITICAL` | poor | riesgo `LEAVES_CASTER_EXPOSED` o `ROOTS_CASTER` y `SELF_LOW_HEALTH` |
    | `CANNOT_REACH_ON_FOOT` | poor | movimiento `APPROACH` o `FLANK`, o choice `MELEE`, y objetivo no alcanzable |
    | `CASTER_ALREADY_HEALTHY` | poor | papel principal `SUSTAIN` y salud del mob `HEALTHY` |
    | `TARGET_CAN_DODGE` | poor | riesgo `SLOW_PROJECTILE` y `TARGET_FLEEING` |

    Frases largas (`full`): `BEST_WHEN_*` repite la frase de M15.7 ("the target is inside melee reach"); `FLANK_BEATS_SHIELD` "the target's shield does not cover its sides"; `GOES_THROUGH_SHIELD` "it goes through the target's shield"; `OUT_OF_REACH` "the target is out of this skill's reach"; `NO_LINE_OF_SIGHT` "there is no line of sight to the target"; `SHIELD_BLOCKS_IT` "the target's shield would block it"; `EXPOSED_WHILE_CRITICAL` "the caster's health is critical and this leaves it exposed"; `CANNOT_REACH_ON_FOOT` "the target cannot be reached on foot"; `CASTER_ALREADY_HEALTHY` "the caster is healthy"; `TARGET_CAN_DODGE` "a target backing away can dodge it".
  - Son **hechos derivados**, no la respuesta: cada uno sale de un hecho que ya está en el estado. El re-banco los mide (brazo C `standard` sin ellos frente a D `full`, y B `compact` con una sola) para comprobar que no empujan a Jev hacia etiquetas falsas; una etiqueta de banco nunca se escribe mirando `fits_now` ni `poor fit now` (M16.9).
  - `QuestionBuilder.criterion(tactic, profile, detail, fit)` devuelve `JsonElement` (texto en `compact`, objeto en `standard` y `full`); `RequestSizeGuard` mide la pregunta serializada como hoy.
- **Archivos:** crear `domain/OptionContext.java` (con `OptionFit` y `FitReason`), `domain/QuestionDetail.java` (`COMPACT`, `STANDARD`, `FULL`); modificar `domain/DecisionTiming.java` (`questionDetail`), `adapter/jev/QuestionBuilder.java`, `SemanticPhrases.java`, `config/DecisionSettings.java`, `ProfileSectionParser.java`, `ProfileSchema.java` (`TIMING` gana `QuestionDetail`), `src/main/resources/config.yml`; pruebas `OptionContextTest`, `QuestionBuilderTest`, `RequestEncoderTest`, `DecisionSettingsTest`, `ProfileLoaderTest`, `ContractFixturesTest` (el servidor de contrato acepta criterios objeto, OpenAPI `ChoiceQuestion.criteria` admite `object`).
- **Reglas:** I4, I5 (el `ChoiceId` no llega), I6, 01 §1 y §9, C13 (un default en un solo lugar), T2 (se calcula fuera del hilo del servidor en `adapter.jev`, salvo `OptionContext`, que es puro).
- **Pruebas:** una prueba por fila de cada tabla; `compact` nunca pasa de 120 caracteres (prueba de propiedad sobre todas las tácticas del banco y del ejemplo) y lleva como mucho una frase `poor fit now`; orden de recorte; claves en orden fijo y ausentes si vacías en `standard`/`full`; JSON válido contra el esquema de la OpenAPI pública; tamaño máximo de una opción `full` ≤ 900 caracteres; el perfil puede fijar su nivel y el global se hereda.
- **Aceptación:**
  - [x] Ningún criterio tiene cifras ni `ChoiceId`.
  - [x] Con el default `compact`, la solicitud de producción del ejemplo cuesta ≤ 1 400 tokens estimados a 1,5 c/t (≈ 1 200 reales).
  - [x] Tabla de coste y capacidad por nivel publicada en la guía del administrador.

### M16.3 Instrucciones estructuradas por tipo de pregunta

- **Propósito:** decir exactamente qué se pregunta y sobre qué parte del estado (G4, G5, G6; D5).
- **Diseño:**
  - `RequestEncoder` codifica **primero el estado** (con su recorte) y después las preguntas, para conocer el índice final de cada mob y de su objetivo en los arreglos (`StateDraft` expone `int indexOfMob(String ref)` e `int indexOfParticipant(String ref)` tras renderizar). La guarda de tamaño se aplica igual al final.
  - `instructions` de cada `Situation` (objeto; rutas entre comillas invertidas):

    ```json
    {
      "question": "Which tactic should mob_1 use now against its current target?",
      "objective": "`mobs[0].objective`",
      "judge_by": ["`mobs[0].facts`", "`mobs[0].target.facts`", "`participants[0].facts`", "`participants[0].cues`"],
      "focus": "Choose the tactic that best serves the objective with only these facts. Options whose poor_fit_now is not empty are usually worse right now."
    }
    ```

    | `Situation` | `question` | `assume` |
    |---|---|---|
    | `NOW` | "Which tactic should mob_1 use now against its current target?" | — |
    | `IF_TARGET_IN_MELEE` | "Suppose the target of mob_1 comes inside melee reach in the next seconds. Which tactic should mob_1 switch to then?" | "the target is inside melee reach" |
    | `IF_OWN_HEALTH_CRITICAL` | "Suppose the health of mob_1 becomes critical in the next seconds. Which tactic should mob_1 switch to then?" | "mob_1's health is critical" |

    En `compact` la instrucción es texto corto, sin rutas ni `focus`: `NOW` "Which tactic best serves mob_1's objective now?"; `IF_TARGET_IN_MELEE` "If mob_1's target comes inside melee reach soon, which tactic then?"; `IF_OWN_HEALTH_CRITICAL` "If mob_1's health becomes critical soon, which tactic then?". El objeto de arriba es el de `standard` (sin `focus`) y `full` (con `focus`).

    La clave `assume` repite la hipótesis con la misma frase que usaría el estado, y los criterios de la contingencia se calculan con esa hipótesis (M16.6), así que instrucción y criterios no se contradicen (G6). `judge_by` omite rutas que no existen (sin objetivo participante, sin señales). Las rutas se generan, no se escriben a mano; el arreglo de participantes se llama `participants` desde M16.4 (antes, `players`).
  - La frase de `focus` es fija, la misma en todas las preguntas y solo existe en `full`; el brazo A del re-banco usa la instrucción de texto de hoy como control.
- **Archivos:** modificar `adapter/jev/RequestEncoder.java`, `QuestionBuilder.java`, `SemanticPhrases.java`, `StateDraft.java`; pruebas `RequestEncoderTest`, `QuestionBuilderTest`, `SemanticEncoderTest`.
- **Reglas:** I3 (las claves de pregunta siguen siendo `q0`, `q1`… y no llegan al modelo, G: "Question IDs are for your code"), I6, T2.
- **Pruebas:** rutas correctas tras recortar el estado (un mob sin pregunta eliminado antes de uno preguntado); instrucción de cada situación; sin rutas colgantes.
- **Aceptación:**
  - [x] Cada ruta de `objective` y `judge_by` resuelve a un valor del estado enviado (prueba que las evalúa sobre el JSON).

### M16.4 Mejoras del estado

- **Propósito:** menos saltos y menos ruido para las preguntas (G5, G15).
- **Diseño:**
  1. **Señales del objetivo dentro del mob:** `mobs[i].target.facts` añade las frases de señales del participante objetivo (`player_1 is blocking` → `target is blocking`, con el sujeto `target`, como las de distancia). Las señales siguen también en `participants[j].cues`; el recorte de `StateDraft` las quita primero de `participants` y nunca de `target` de un mob preguntado.
  2. **Salud relativa:** `mobs[i].facts` añade, si las bandas difieren, `mob_1 is in better shape than its target` o `mob_1 is in worse shape than its target` (comparación de `HealthBand` en código, G5).
  3. **Agrupación:** `target stands close to other enemies` (M15.5).
  4. **Eventos relevantes primero:** `recent_events` conserva primero los eventos cuyo participante es objetivo de un mob preguntado o cuyo mob es preguntado; el resto se recorta antes que ellos (hoy solo por antigüedad).
  5. **`players` pasa a `participants`** en el estado (mismas entradas y mismo orden). Se hace aquí, y no en M17, para que el re-banco de M16.10 calibre ya con el nombre definitivo y M17 no cambie el cuerpo de las solicitudes de encuentros solo con jugadores (sus defaults quedan byte a byte iguales).
  6. Sin cambios: `objective` del autor tal cual, `duration`, `headcount`, amenaza.
- **Archivos:** `adapter/jev/SemanticEncoder.java`, `StateDraft.java`, `SemanticPhrases.java`; pruebas `SemanticEncoderTest`, `SemanticPhrasesTest`.
- **Reglas:** I6, A5 (`maxStateChars` intacto), C8.
- **Pruebas:** las cuatro reglas; orden de recorte nuevo; el estado más grande admitido (16×16×16) sigue por debajo de 12 000 caracteres (hoy 7 699).
- **Aceptación:**
  - [x] Ningún hecho del objetivo de un mob preguntado exige mirar otro arreglo del estado.

### M16.5 Señal de idoneidad por opción (`noul`) y regla de aceptación

- **Propósito:** tener, en la **misma** solicitud, una señal absoluta de si cada opción sirve (G8, G9), para rescatar respuestas útiles con confianza relativa baja y vetar las que nada respalda.
- **Diseño:**
  - **Decisiones de tipo de pregunta:**

    | Opción | Decisión | Motivo |
    |---|---|---|
    | Solo `choice` | Base, siempre | La táctica es una elección conjunta de movimiento y skill (plan §6); G8 |
    | `score` para elegir táctica | **Descartada** | Las tácticas no tienen orden (G8); una `score` de "peligro" no elige nada ejecutable |
    | Dos etapas (Choice amplia y luego re-pregunta sobre las 3 mejores) | **Descartada** | Duplica la latencia de ida y vuelta (p50 179 ms, p99 328 ms medidos) en un combate que cambia; G10 dice que solo se justifica si la segunda solicitud necesita datos nuevos, y aquí no los hay |
    | `choice` + una `noul` por opción en la misma solicitud | **Opcional**, apagada por defecto | Sin latencia añadida (paralelo, G10); cuesta tokens (M16.10); se activa por perfil si el re-banco lo justifica |

  - Dominio: `record FitCheck(QuestionKey key, MobKey mob, TacticId tactic)`; `DecisionRequest` gana `List<FitCheck> fitChecks` (vacía por defecto); `DecisionResult.Answered` gana `Map<QuestionKey, Double> fits`. Las claves comparten la secuencia `q0`, `q1`… de la solicitud (I3). `ResponseParser` exige `type: "noul"` y `noul` finito en `[0, 1]` para cada `FitCheck` enviado. Las `FitCheck` de opciones fusionadas por texto igual o de preguntas quitadas se eliminan en `prepare`, antes de codificar (M16.1).
  - Pregunta (solo para la pregunta `NOW` de cada mob, una por opción):

    ```json
    {
      "type": "noul",
      "instructions": {
        "question": "Is `press_and_strike` a sound tactic for mob_1 right now?",
        "tactic": { "movement": "…", "skill": "…" },
        "judge_by": ["`mobs[0].facts`", "`mobs[0].target.facts`"]
      },
      "criteria": {
        "true": "The facts make this tactic useful for the objective of mob_1 right now.",
        "false": "The facts make this tactic useless, risky or impossible right now."
      }
    }
    ```

    Solo `movement` y `skill` en `tactic` (sin `fits_now`), para que la señal sea independiente del cálculo de M16.2.
  - Regla pura `domain.FitGate`:

    ```java
    record FitThresholds(double veto, double rescue) {}   // 0 <= veto < rescue <= 1
    static FitVerdict judge(Answer answer, Map<TacticId, Double> fits, double minimumConfidence, FitThresholds t)
    sealed interface FitVerdict permits FitVerdict.Adopt, FitVerdict.Reject {
        record Adopt(TacticId tactic, ControlLevel level, boolean rescued) implements FitVerdict {}  // level: JEV o ALTERNATIVE
        record Reject(FallbackReason reason) implements FitVerdict {}
    }
    ```

    1. Recorre las tácticas por probabilidad descendente y descarta las de `fit < veto` (veto). **Si el veto las quita todas → `Reject(LOW_CONFIDENCE)`** (ninguna opción respaldada: A6).
    2. Si la primera superviviente es la de mayor probabilidad y `confidence ≥ minimumConfidence` → `Adopt(tactic, JEV, false)`.
    3. Si `confidence < minimumConfidence`: `Adopt(tactic, nivel, true)` solo si la primera superviviente tiene `fit ≥ rescue` y ninguna otra superviviente alcanza `rescue` (señal absoluta única, G9), con `nivel` = `JEV` si es la de mayor probabilidad y `ALTERNATIVE` si no; si no, `Reject(LOW_CONFIDENCE)`.
    4. Si `confidence ≥ minimumConfidence` pero el veto quitó la de mayor probabilidad, la primera superviviente se adopta como `Adopt(tactic, ALTERNATIVE, false)` (el mismo nivel que ya usa M10 para "la siguiente válida de la misma respuesta").

    `Adopt.level` solo puede ser `JEV` o `ALTERNATIVE` (invariante del constructor).

    `IntentPolicy` delega en `FitGate` cuando la respuesta trae `fits`; sin ellos, la regla de hoy sin cambios.
  - Solo se envían con `questionDetail: full` (M16.2); en `compact` y `standard` se ignoran aunque estén activas, con un aviso al cargar.
  - Configuración: `decision.fitChecks` (booleano, default `false`), `decision.fitVeto` (0,2) y `decision.fitRescue` (0,7) globales; `Timing.FitChecks` por perfil (solo puede activarlo si el global no lo prohíbe con `decision.fitChecksAllowed: false`, default `true`). Los umbrales definitivos salen del re-banco (M16.10) con la misma regla de M16.7.
- **Archivos:** crear `domain/FitCheck.java`, `FitGate.java`; modificar `domain/DecisionRequest.java`, `DecisionResult.java`, `IntentPolicy.java`, `engine/EncounterQuestions.java`, `engine/InFlightRequest.java`, `adapter/jev/QuestionBuilder.java`, `ResponseParser.java`, `RequestEncoder.java`, `config/DecisionSettings.java`, `ProfileSectionParser.java`, `ProfileSchema.java` (`TIMING` gana `FitChecks`), (la reserva de presupuesto ya usa el tamaño del cuerpo preparado, M16.1, que incluye las `noul`); pruebas `FitGateTest`, `ResponseParserTest`, `QuestionBuilderTest`, `IntentPolicyTest`, `DecisionSettingsTest`, `ProfileLoaderTest`, `SystemOneContractServer` con fixtures `noul`.
- **Reglas:** A6 (ante la duda, `LOW_CONFIDENCE` como hoy), A7, I3, T2, C3 (`FitVerdict` sellado), C13.
- **Pruebas:** tabla de verdad de `FitGate` (veto de la primera → `ALTERNATIVE`, veto de todas → `Reject(LOW_CONFIDENCE)`, rescate único de la primera → `JEV` y de otra → `ALTERNATIVE`, rescate doble rechazado, sin fits → regla de hoy, `Adopt` con `LOCAL` rechazado por el constructor); respuesta sin la `noul` pedida → `INVALID_RESPONSE`; presupuesto de tokens con y sin idoneidad.
- **Aceptación:**
  - [x] Con `fitChecks: false`, las solicitudes son byte a byte las de M16.1–M16.4 (ninguna `noul`).
  - [x] Con `fitChecks: true`, una sola solicitud por encuentro y decisión (G10).

### M16.6 Contingencias con opciones hipotéticas

- **Propósito:** que una contingencia pregunte entre las tácticas que serían elegibles **en esa situación** (D6, G6) y que se mida su uso.
- **Diseño:**
  - Captura: `ReadyTactics` calcula, además de las elegibles de ahora, `MobFacts.contingencyEligible: Map<Situation, Set<TacticId>>` para `IF_TARGET_IN_MELEE` (atajos con `targetDistance = MELEE`; choices con alcance cuerpo a cuerpo dentro; `FLANK` según el escudo actual) y `IF_OWN_HEALTH_CRITICAL` (atajos con salud `CRITICAL`). Los contextos con condiciones nativas de MythicMobs **no** se reevalúan bajo hipótesis (se evalúan sobre la entidad real, M07.7): se conservan como están, y se documenta.
  - `EncounterQuestions`: las opciones de una contingencia salen de `contingencyEligible.get(situation)` con `OptionSet.distinct` y el tope; si quedan menos de 2, no se pregunta esa contingencia.
  - `OptionContext` se evalúa con `now` hipotético (`TARGET_IN_MELEE` sustituye a `TARGET_AT_RANGE`; `SELF_LOW_HEALTH` añadido).
  - Métrica: `DecisionMetrics` cuenta contingencias preguntadas, disparadas (M10.4) y disparadas con resultado (`OutcomeWindow`, M16.8); `/thepredict status` las muestra como `contingencies: asked= fired= effective=`.
- **Archivos:** `adapter/server/context/ReadyTactics.java`, `FactsAssembler.java`; `domain/MobFacts.java`; `engine/EncounterQuestions.java`, `DecisionMetrics.java`, `StatusAssembler.java`; pruebas `ReadyTacticsTest`, `EncounterQuestionsTest`, `ContingencyPolicyTest`, `StatusFormatterTest`.
- **Reglas:** A5 (dos conjuntos extra por mob y captura, tamaño ≤ tácticas), A7, D2.
- **Pruebas:** con el ejemplo, la contingencia de salud crítica ofrece solo `retreat_and_defend` y `keep_distance_and_shoot` (el contexto `low_health_phase`) y, al tener 2, se pregunta; con una sola, no; la de cuerpo a cuerpo incluye `close_in_and_slam`.
- **Aceptación:**
  - [x] Ninguna contingencia ofrece una táctica que no sería elegible en su hipótesis.
  - [x] El banco nuevo incluye contingencias etiquetadas (M16.9).

### M16.7 `minimumConfidence` por perfil y default con evidencia

- **Propósito:** elegir el umbral con una regla escrita y datos suficientes, no a ojo (G12; DT-31).
- **Diseño:**
  - Ya existen `decision.minimumConfidence` (global, 0,5) y `Timing.MinimumConfidence` (perfil, 0–0,99). M16 no cambia el mecanismo; fija **cómo** se elige el valor y lo aplica en M16.10.
  - Regla `CalibrationRule` (herramienta de pruebas, `src/test`, usada por `BenchStatistics`): sobre las respuestas `NOW` del brazo elegido con etiquetas `REVIEWED`, para cada `t` en `{0,20; 0,25; …; 0,70}`:
    - **agregación de repeticiones** (cada escenario distinto cuenta una vez): su confianza es la **media** de las confianzas de sus repeticiones; su táctica es la **moda** de las mejores tácticas de sus repeticiones (empate: la de mayor probabilidad media); es aceptable, `best` o prohibido según esa táctica modal; el número de repeticiones cuya mejor táctica difiere de la moda se publica aparte;
    - `kept(t)` = proporción de escenarios cuya confianza media es ≥ `t`;
    - `acceptable(t)` = escenarios conservados cuya táctica modal es aceptable, con su intervalo de Wilson al 95 %;
    - `forbidden(t)` = escenarios conservados cuya táctica modal es prohibida.

    Se elige el **menor** `t` que cumple a la vez:
    1. `n(t)` ≥ 30, donde `n(t)` es el número de escenarios `NOW` distintos, `REVIEWED` y no `uninformative`, conservados a `t`;
    2. `acceptable(t)` ≥ 0,90 (estimación puntual);
    3. límite inferior de Wilson al 95 % de `acceptable(t)` ≥ 0,75, **publicando `n(t)`** junto al valor;
    4. como mucho **1** escenario prohibido entre los conservados (el 2 % solo se exige cuando `n(t)` ≥ 50).

    Motivo: con el banco de 48 escenarios `NOW` y el objetivo de conservar ≥ 70 %, `n` ronda 30–40. Con ese `n`, un límite inferior de Wilson ≥ 0,85 exige acertar casi todo (33 de 34 da ≈ 0,85; 31 de 34, un 91 %, da ≈ 0,77), así que el objetivo anterior era inalcanzable salvo con acierto perfecto. Con ≥ 0,75 y `n` = 30 hace falta **≥ 28/30** (93 %, límite 0,79; 27/30 = 90 % da 0,744 y no cumple); con `n` mayor basta algo menos (31/34 → 0,77; 36/40 → 0,77). Si ninguno cumple, se conserva 0,5 y M16 no se cierra (se itera el planteamiento, M16.10).
  - **Revisión exigida:** el veredicto que cambia defaults exige los **48 escenarios `NOW` y los 12 de contingencia en `REVIEWED`** (M16.9). Con solo las 24 preguntas núcleo revisadas, el informe calcula la misma tabla marcada "preliminar" y no cambia ningún default.
  - El default global cambia en `config.yml` y en la guía del administrador solo con ese resultado (W3). Un perfil puede fijar el suyo; la guía del autor explica cuándo (un jefe cuyo error es caro sube el umbral, G12).
  - Contingencias: la misma regla con sus propias respuestas; si hay menos de 8 escenarios de contingencia distintos, heredan el umbral de `NOW`.
- **Archivos:** `src/test/java/akyrion/thepredict/adapter/jev/CalibrationRule.java`, `BenchStatistics.java`; tras el re-banco, `config.yml`, `config/DecisionSettings.java` (default) y `Guias/Guia-del-administrador.md`; pruebas `CalibrationRuleTest`.
- **Reglas:** W3, C13 (un default en un solo lugar), A7.
- **Pruebas:** regla sobre datos sintéticos (monótonos, no monótonos, sin ningún umbral válido, `n` < 30); agregación (moda, empate por probabilidad media, media de confianza); Wilson en casos borde (0 y n) y en los ejemplos del texto (28/30 cumple, 27/30 no, 31/34, 36/40, 22/24).
- **Aceptación:**
  - [x] El informe del re-banco muestra la tabla de la regla y el umbral elegido con su intervalo (brazo D: 0,45, `n` 41, 90,2 %, Wilson 0,775; ver "Resultado del re-banco").
  - [x] DT-31 pasa a "Resuelta" para `minimumConfidence` o queda "Abierta" con la causa (Resuelta, 2026-09-30).

### M16.8 Métrica de efectividad: `OutcomeWindow`

- **Propósito:** medir si una intención adoptada **funcionó**, más allá de si la respuesta se conservó.
- **Diseño:**
  - Entrada `engine.OutcomeEvents` (como `ThreatEnrichment`: la llaman los listeners, en el hilo del servidor, T1/T6):

    ```java
    void dealt(UUID mobId, UUID victimId, double healthFraction, long tick)   // el miembro dañó a alguien
    void taken(UUID mobId, double healthFraction, long tick)                   // el miembro recibió daño
    void died(UUID entityId, long tick)                                        // murió un miembro o un participante
    ```

    `healthFraction` = daño final / vida máxima de la víctima, calculado en el adapter (Paper: `getFinalDamage()`, atributo `MAX_HEALTH`), así que `engine` no lee entidades (D2). `CombatOutcomeListener.onDamage` atribuye el daño al miembro si es el causante directo, el que disparó el proyectil o el lanzador de una skill de MythicMobs (el `damage` de MythicMobs llega como `EntityDamageByEntityEvent` con el lanzador como causante; se confirma en servidor, DT-35). Un listener nuevo de `EntityDamageEvent` en `MONITOR` registra `taken` solo si la víctima tiene sesión (búsqueda O(1)); `EntityDeathEvent` registra `died` para miembros y participantes (índice O(1)).
  - `engine.OutcomeWindow` (una abierta por sesión como mucho): se abre cuando `IntentControl` **adopta** una intención nueva de nivel `JEV`, `ALTERNATIVE` o `LOCAL` (no en renovaciones) y se cierra al terminar la intención o a los `OUTCOME_WINDOW_TICKS = 60` ticks, lo primero. Guarda: nivel, táctica, movimiento, papel principal de la choice, banda de distancia inicial y final (de las sondas de M07.6), `dealt` y `taken` sumados, casts de la época (`ExecutionOutcome.CAST`), golpes confirmados (un `dealt` a su objetivo en los 20 ticks siguientes a un cast dañino), muertes del mob y del objetivo.
  - `reached` por movimiento: `APPROACH` → se vio `MELEE`; `KEEP_DISTANCE` → la banda final es `RANGED`; `RETREAT` → la banda final es más lejana que la inicial o `BEYOND`; `FLANK` → hubo cast fuera del ángulo de bloqueo (`CAST` con movimiento `FLANK`); `HOLD` y `NONE` → no aplica.
  - Al cerrarse, la ventana produce `record OutcomeWindow.Summary(ControlLevel level, TacticId tactic, Movement movement, long startTick, long endTick, double dealt, double taken, boolean reached, int casts, int hits, boolean mobDied, boolean targetDied, double effect)`, el **único** tipo de resumen: lo usan `OutcomeMetrics`, `inspect` y el registrador de duelos de M18 (`DecisionTap.outcomeClosed`).
  - Índice comparativo por ventana (no es "la verdad", sirve para comparar brazos con el mismo perfil): `effect = dealt − taken + 0,25·reached + 1·targetDied − 1·mobDied`.
  - `engine.OutcomeMetrics` (tamaño fijo, A5): por `ControlLevel` × `Movement` (4 × 6 celdas) cuenta ventanas, suma `dealt`, `taken`, `effect`, `reached`, casts y golpes; `MobSession` guarda sus últimas 8 ventanas (anillo) para `inspect`.
  - Informe: `/thepredict status` gana `outcomes: level=JEV windows= dealt= taken= reach= hit_rate= effect=` por nivel; `inspect <mob>` muestra las últimas ventanas con táctica y nivel. Con `decision.logDecisions: true`, una línea por ventana cerrada `Outcome <nivel> <táctica> dealt=0.12 taken=0.03 reached=yes casts=1 hits=1` (C8, con el mismo límite de frecuencia que las líneas de M10).
  - Arneses: `TacticalAB.Result` gana `effect` y `hitRate` con un modelo simple de daño por cast guionizado (mismo para los dos brazos); el simulador de duelo de M18 usa `OutcomeWindow` real con un mundo simulado.
- **Archivos:** crear `engine/OutcomeEvents.java`, `OutcomeWindow.java`, `OutcomeMetrics.java`; modificar `engine/IntentControl.java`, `MobSession.java`, `Diagnostics.java`, `StatusAssembler.java`, `StatusReport.java`, `SessionReport.java`; `adapter/server/events/CombatOutcomeListener.java` y un listener nuevo `OutcomeListener.java` (C10); `admin/StatusFormatter.java`, `InspectFormatter.java`; raíz `SessionServices` o `DecisionServices` (conexión, D8); pruebas `OutcomeWindowTest`, `OutcomeMetricsTest`, `CombatOutcomeListenerTest`, `StatusFormatterTest`, `InspectFormatterTest`, `TacticalExecutionABTest`.
- **Reglas:** A3, A5, A7, D2, D5, D6, T1, T6, C8.
- **Pruebas:** apertura solo en adopciones; cierre por fin de intención y por plazo; atribución de proyectil y de skill; `reached` de cada movimiento; tamaño fijo tras 10 000 ventanas.
- **Aceptación:**
  - [x] `status` e `inspect` muestran la efectividad por nivel (las salidas locales pueden incluir UUID de mobs cuando ayudan a depurar, decisión del usuario; lo que se envía a Jev nunca, I6).
  - [ ] Guion de servidor, sesión 16 (M16.11): ventanas con daño y alcance coherentes con lo observado.

### M16.9 Banco etiquetado y revisión de etiquetas

- **Propósito:** un banco de **escenarios distintos** con etiquetas explícitas y revisables por el usuario (D3, D4, D7; G12, G13).
- **Diseño:**
  - **Nota de M15 (2026-09-29):** M15.9 regeneró los seis `scenarios/seed/*.json` con los propósitos declarados del ejemplo y, en las cinco semillas cuyo objetivo es alcanzable, ofreció también `close_in_and_slam`, sin tocar las etiquetas (propuestas por M13). **Esas etiquetas necesitan revisión antes de usarse:** por ejemplo, `seed-melee-duel` solo acepta `press_and_strike` aunque ahora también ofrece `close_in_and_slam`. El histórico del banco pagado sigue intacto en `scenarios/seed-2026-09-29/`.
  - Carpeta nueva `src/test/resources/scenarios/bank/`, generada por `ScenarioBank` (test) desde una matriz declarativa, con tres perfiles, todos con `Purpose` declarado para no depender del binder (D2): el guardián de M15.9 (`src/main/resources/examples/AI/ThePredictGuardian.yml`), `bank-skirmisher` (arquero con `ESCAPE`, `GAP_CLOSE`, `INTERRUPT` y `FINISHER`, en `src/test/resources/scenarios/bank/profiles/`) y `purpose-shield` de M15.8 (en `src/test/resources/profiles/purpose-shield.yml`, único sitio; el banco lo lee de ahí).
  - Familias (48 `NOW` + 12 contingencias = 60 escenarios, un solo mob salvo donde se indica):

    Cada escenario se llama `bank-<familia>-<nn>` (`nn` desde `01`). Familias y sus identificadores:

    | Familia | Identificador | N | Variantes |
    |---|---|---|---|
    | Duelo cuerpo a cuerpo | `melee` | 6 | objetivo sano/crítico × acercándose/quieto/alejándose |
    | Tirador | `shooter` | 6 | objetivo a distancia con arco tensado/tenso del todo/ballesta cargada × se acerca o se aleja |
    | Escudo | `shield` | 6 | bloqueando / levantando / escudo desactivado × cuerpo a cuerpo o distancia media; la mitad con el perfil de prueba `src/test/resources/profiles/purpose-shield.yml` de M15.8 (se lee de esa ruta, no se copia a `bank/profiles/`) |
    | Salud baja del mob | `lowhp` | 6 | objetivo sano/crítico × cuerpo a cuerpo/distancia; **sin** el contexto `low_health_phase` para que haya opciones malas (corrige `seed-low-health`) |
    | Inalcanzable (pilar) | `pillar` | 4 | con y sin línea de visión × distancia media/lejos |
    | Agrupados | `grouped` | 4 | 2 y 3 jugadores juntos junto al objetivo × salud del mob |
    | Objetivo crítico (remate) | `finisher` | 4 | skirmisher con `FINISHER` × distancia |
    | Objetivo bebiendo o comiendo | `drinking` | 4 | skirmisher con `INTERRUPT` × distancia |
    | Sin línea de visión | `nolos` | 4 | objetivo invisible / detrás de bloques × distancia |
    | Objetivo huyendo | `fleeing` | 4 | skirmisher con `GAP_CLOSE` y proyectil lento |
    | Contingencia "entra en cuerpo a cuerpo" | `cont-melee` | 6 | desde distancia, con y sin escudo |
    | Contingencia "salud crítica" | `cont-critical` | 6 | con y sin el contexto `low_health_phase` (con el contexto: 2 opciones, se marca `uninformative` y no cuenta para acierto) |

  - Conjuntos con nombre que entiende la herramienta (`-Pscenarios=`): **`core12`** = `bank-melee-01`, `bank-shooter-01`, `bank-shield-01`, `bank-lowhp-01`, `bank-pillar-01`, `bank-grouped-01`, `bank-finisher-01`, `bank-drinking-01`, `bank-nolos-01`, `bank-fleeing-01`, `bank-cont-melee-01`, `bank-cont-critical-01` (el primero de cada familia); **`core24`** = `core12` más los `-02` de cada familia; `all` = los 60.
  - Formato de documento 2 (compatible con `ScenarioDocument` de hoy más campos nuevos):

    ```json
    "labels": {
      "q0": {
        "best": ["retreat_and_defend"],
        "acceptable": ["retreat_and_defend", "keep_distance_and_shoot"],
        "forbidden": ["press_and_strike", "hold"],
        "rationale": "Critical health, healthy target closing in: trading blows loses; distance or heal keeps it alive.",
        "status": "PROPOSED",
        "reviewedBy": "",
        "reviewedOn": ""
      }
    }
    ```

    Reglas (`ScenarioBankTest`): `best ⊆ acceptable ⊆ opciones`; `forbidden ∩ acceptable = ∅`; `status ∈ {PROPOSED, REVIEWED, DISPUTED}`; `REVIEWED` exige `reviewedBy` y fecha ISO; una pregunta con `acceptable` igual a todas las opciones o con menos de una opción no aceptable se marca `"uninformative": true` y el banco la excluye del acierto (solo latencia y tokens). `forbidden` pasa de texto a lista; el lector acepta el formato 1.
  - **Procedimiento de revisión para el usuario** (sin red ni costo):
    1. `./gradlew labelReviewSheet --offline` escribe `build/reports/m16/label-review.md`: por escenario, el estado en frases legibles, las opciones con su criterio **sin** `fits_now`/`poor_fit_now` (para no sesgar la etiqueta, M16.2), las etiquetas propuestas y el motivo.
    2. El usuario edita `src/test/resources/scenarios/bank/label-review.yml`, una entrada por escenario: `status: REVIEWED | DISPUTED`, y, si cambia algo, `best`, `acceptable`, `forbidden`, `note`. Sin editar JSON.
    3. `./gradlew applyLabelReview --offline` vuelca la revisión en cada JSON (con `reviewedBy` del campo `reviewer:` del YAML y la fecha) y ejecuta `ScenarioBankTest`.
    4. Revisión: para un **re-banco preliminar** basta `core24` (las 24 preguntas núcleo, dos por familia); para el **veredicto que cambia defaults** (M16.7) hacen falta los 60 en `REVIEWED`. El informe separa siempre `REVIEWED` de `PROPOSED`. Tiempo estimado para el usuario: 20–30 min para `core24`, 45–60 min para los 60.
- **Archivos:** crear `src/test/java/akyrion/thepredict/adapter/jev/ScenarioBank.java`, `ScenarioBankTest.java`, `LabelReviewSheet.java`, `LabelReviewApply.java`; modificar `ScenarioDocument.java` (formato 2), `build.gradle.kts` (tareas `labelReviewSheet` y `applyLabelReview`, `JavaExec` sobre `src/test`, fuera de `check`); recursos en `src/test/resources/scenarios/bank/`.
- **Reglas:** I6 (lo que se envía a Jev solo lleva alias), W3, C11, P6; el build normal nunca llama a la red.
- **Pruebas:** `ScenarioBankTest` (reglas de etiquetas, archivos iguales a los generados, ningún UUID en los cuerpos que se enviarían, `core12` y `core24` existen); `LabelReviewApply` con un YAML de ejemplo; lectura de formato 1 y 2.
- **Aceptación:**
  - [x] 60 escenarios generados, todos con al menos una opción prohibida salvo los `uninformative`.
  - [x] Hoja de revisión legible sin abrir JSON; revisión aplicable con una sola tarea.
  - [x] `seed-low-health` sustituido por la familia "Salud baja del mob" con opciones prohibidas.

### M16.10 Re-banco de pago

- **Propósito:** medir con la cuenta real si M16 mejora conservación, acierto y calibración, y **qué nivel de detalle** conviene, con presupuesto explícito. El usuario ya autorizó llamadas de pago; este plan las acota.
- **Diseño:**
  - **Brazos**, todos sobre el mismo banco (M16.9) y el mismo modelo `jev-1.13.0`. Cada llamada del banco lleva una sola pregunta (el escenario), 1 mob, 1 participante y hasta 7 opciones; los caracteres se estiman con un estado 1 contra 1 de unos 650 caracteres:

    | Brazo | Planteamiento | Caracteres por llamada | Tokens por llamada (cota 1,5 c/t) |
    |---|---|---|---|
    | A (control) | Criterio de texto de M15.7 e instrucción de texto de hoy; sin deduplicar | ≈ 2 170 | ≈ 1 450 |
    | B | **`compact`** (default): M16.1 + M16.4 + texto corto con como mucho una `poor fit now` + instrucción corta | ≈ 1 460 | ≈ 970 |
    | C | `standard`: objetos sin `fits_now`/`poor_fit_now` + instrucción estructurada sin `focus` | ≈ 2 650 | ≈ 1 770 |
    | D | `full`: C + `fits_now`/`poor_fit_now` + `focus` | ≈ 3 360 | ≈ 2 240 |
    | E | D + idoneidad `noul` (M16.5) | ≈ 5 180 | ≈ 3 450 |

  - **Fases** (cada una exige `-PliveJev=true`, la clave en el entorno y la estimación impresa antes de empezar, como hoy):

    | Fase | Qué | Llamadas | Tokens (cota) | USD a 0,042/M |
    |---|---|---|---|---|
    | P0 | Bucle local contra el servidor de contrato (costo 0) | 0 | 0 | 0 |
    | P1 | Piloto de varianza: `core12` × 10 repeticiones, brazo B | 120 | ≈ 116 000 | ≈ 0,005 |
    | P2 | Principal: 60 escenarios × 2 repeticiones × brazos A–E | 600 | ≈ 1 186 000 | ≈ 0,050 |
    | **Total** | | **720** | **≈ 1,30 M** | **≈ 0,055** |

    Cota de la tarea: `-PmaxCalls=780`; techo de gasto de M16: **0,10 USD**. Si P1 muestra que las repeticiones no cambian la mejor táctica en más del 2 % (lo esperado por G13), P2 puede bajar a 1 repetición (300 llamadas, ≈ 0,025 USD). Si los objetivos no se cumplen, **una** iteración más del planteamiento con los dos brazos mejores (≈ 240 llamadas, ≈ 0,02 USD) antes de tocar umbrales; total máximo con la iteración: ≈ 0,075 USD.
  - Cambios de la herramienta (`src/test`, nunca en el JAR): `LiveScenarioBench` acepta `-Parms=A,B,C,D,E` y `-Pscenarios=core12|core24|all|<ids>`, codifica cada escenario con el brazo pedido desde `ScenarioBank` (no reenvía cuerpos grabados) y guarda **respuestas crudas** en `build/reports/m16/answers.jsonl` (escenario, brazo, repetición, probabilidades, confianza, `noul`, tokens; sin clave ni cabeceras, W6); `BenchStatistics` añade: tabla por escenario (mejor táctica, confianza media, cambios entre repeticiones: confirma o descarta D3 y D4), curva por **escenarios distintos** (cada escenario pesa 1), margen entre las dos primeras y entropía normalizada por tramo (G2), acierto de `best`, `acceptable` y `forbidden` separados por `REVIEWED`/`PROPOSED`, contingencias por situación, idoneidad frente a etiquetas (para calibrar `fitVeto` y `fitRescue` con la regla de M16.7), tokens reales por brazo con la solicitud de producción estimada (`NOW` + contingencia) y la tabla de `CalibrationRule`.
  - Comandos (el usuario o el orquestador con su aprobación; nunca se lee `.env` desde herramientas, W6):

    ```text
    set -a; source .env; set +a
    ./gradlew liveScenarioBench --no-daemon -PliveJev=true -PscenarioDir=src/test/resources/scenarios/bank -Parms=B -Prepetitions=10 -Pscenarios=core12 -PmaxCalls=130
    ./gradlew liveScenarioBench --no-daemon -PliveJev=true -PscenarioDir=src/test/resources/scenarios/bank -Parms=A,B,C,D,E -Prepetitions=2 -Pscenarios=all -PmaxCalls=620
    ```

    Informe y `answers.jsonl` se copian a `evidencia/m16-rebanco-<fecha>/` tras comprobar que no llevan clave ni cabeceras.
  - **Objetivos de aceptación** (brazo B, el default `compact`; preguntas `NOW` `REVIEWED`; cada escenario distinto pesa 1):

    | Medida | Objetivo | Referencia 2026-09-29 |
    |---|---|---|
    | Conservadas a `minimumConfidence` 0,5 | ≥ 70 % | 33,3 % |
    | Aceptables entre conservadas a 0,5 | ≥ 90 % puntual y Wilson inferior ≥ 0,75 con `n` publicado (`n` ≥ 30), como en M16.7 | 100 % de 2 escenarios |
    | Prohibidas entre conservadas a 0,5 | ≤ 1 escenario (≤ 2 % si `n` ≥ 50) | 0 % |
    | `best` entre conservadas | ≥ 60 % | — |
    | Calibración | Acierto no decreciente entre los tramos 0–0,3, 0,3–0,5, 0,5–0,7 y 0,7–1 con ≥ 8 escenarios distintos por tramo (tramos con menos se funden con el vecino) | No monótona (D4) |
    | Mejora frente a A | +30 puntos en conservadas a 0,5 sin bajar el acierto | — |
    | Contingencias | ≥ 80 % aceptables entre conservadas | Sin datos |
    | Tokens reales de la solicitud de producción en `compact` (`NOW` + 1 contingencia, 7 opciones) | ≤ 1 200 (≤ 1 400 a 1,5 c/t) | ≈ 1 300 **estimado** (0.0.1, 5 opciones, a partir de 2 253 caracteres; lo medido fueron 613 tokens con solo `NOW`) |
    | Cambio de default a `standard` o `full` | Solo con +10 puntos de conservadas a 0,5 frente a B, acierto no más de 2 puntos por debajo y sin más prohibidas (M16.2) | — |

- **Archivos:** los de "Cambios de la herramienta"; `build.gradle.kts` (propiedades nuevas de la tarea); `evidencia/m16-rebanco-<fecha>/`; guion de servidor §9 con la fila nueva.
- **Reglas:** W6, W3, A7, C11; W7 propuesta (abajo).
- **Pruebas:** `LiveToolsLoopbackTest` corre los cinco brazos contra el servidor de contrato (costo 0); la estimación impresa coincide con la tabla ± 10 %; la tarea se niega sin `-PliveJev=true`, sin clave o por encima de `maxCalls`; `-Pscenarios=core12` selecciona exactamente los 12 identificadores de M16.9.
- **Aceptación:**
  - [x] Informe del re-banco con todas las tablas y respuestas crudas en `evidencia/` (`evidencia/m16-rebanco-2026-09-30/`).
  - [ ] Objetivos cumplidos, o M16 abierto con la causa y la iteración siguiente propuesta. Sin marcar: con el default elegido (brazo D a 0,45) se cumplen todos menos dos, la mejora de +30 puntos frente a A y los ≤ 1 200 tokens de producción; el usuario aceptó el cambio de default con esos dos resultados (ver "Resultado del re-banco").
  - [x] Gasto real registrado (`usage.input_tokens`) y ≤ 0,10 USD (750 611 tokens, ≈ 0,0315 USD).

### M16.11 Efectividad en el servidor

- **Propósito:** ver el efecto de M16 en combate real, primero sin costo.
- **Diseño:** sesión nueva **16** del guion (se añade al paquete de pruebas):
  - 16.1 (sin clave): guardián de M15.9 en modo local contra un jugador, 3 combates de 60 s; `/thepredict status` antes y después; `outcomes:` por nivel `LOCAL`.
  - 16.2 (**PAGO**): igual con clave, `decision.logDecisions: true`, `questionDetail: compact`; `outcomes:` por nivel `JEV` y `ALTERNATIVE`; líneas `Outcome …` en el registro. 3 combates × ≈ 60 solicitudes × ≈ 1 400 tokens (cota) ≈ 252 000 tokens ≈ **0,011 USD**.
  - 16.3: la sección §7 del guion (A/B frente a MythicMobs) gana las medidas de `outcomes:`; su estimación de costo se actualiza: con `compact`, ≈ 3 600 solicitudes × ≈ 1 400 tokens ≈ 5,0 M tokens ≈ **0,21 USD** (con `standard`, ≈ 0,43 USD).
- **Archivos:** `Guion-servidor-0.0.1.md` (§7, §9 y sesión 16), `paquete-pruebas/Guia-de-pruebas-por-sesiones.md`, `Checklist-DT-y-casillas.md` (DT-35).
- **Reglas:** W3, W6, A8.
- **Pruebas:** `UserTestPackTest` con los archivos nuevos.
- **Aceptación:**
  - [x] Sesión 16 escrita con comandos exactos, esperado y costo.

## Cambios propuestos a 00/01

**00. Arquitectura y reglas**

- §1, principio nuevo **A9 Medible por su resultado**: "Cada intención adoptada abre una ventana de resultado acotada (`OutcomeWindow`); la calidad de las decisiones se juzga por su efecto medido y por un banco etiquetado, no solo por la confianza."
- §9, regla nueva **W7 Ejecuciones de pago**: "Toda herramienta que llame a TypeSafe imprime antes de empezar las llamadas, los tokens estimados a 1,5 caracteres por token y el costo; se niega por encima de `maxCalls`; guarda las respuestas crudas sin clave ni cabeceras para poder volver a analizarlas sin pagar."
- §6, I3: añadir "las preguntas `noul` de idoneidad comparten la secuencia `q0`, `q1`… de su solicitud".
- §7, C13: añadir "`decision.questionDetail` y `Timing.QuestionDetail` (`compact` por defecto); el nivel de detalle nunca amplía los límites globales de tokens".
- §4, **enmienda del puerto `DecisionClient`**: métodos `Preparation prepare(DecisionRequest)` (puro, sin E/S; lo llama `DecisionDispatcher` en el hilo del servidor antes de reservar presupuesto y de abrir `InFlightRequest`), `CompletableFuture<DecisionResult> decide(PreparedRequest)` (envía el cuerpo ya preparado; la future se completa en hilos HTTP como hoy), `check()`, `close()` y `unavailable(ServiceCheck)`. Tipos `Preparation` (`Ready`, `NothingToAsk`, `Invalid`) y `PreparedRequest` en `engine`. Motivo: la deduplicación por texto decide qué se envía, y hacerlo antes del envío evita respuestas sintéticas que contaminarían `ModelExpectation`, `ServiceStatusTracker` y `AnswerGroups`, y reservas de presupuesto por solicitudes que no se envían.
- §5, T2: añadir "`prepare` es parte de construir la solicitud desde datos inmutables y PUEDE ejecutarse en el hilo del servidor; no hace E/S ni lee estado mutable".
- Encabezado: "Revisión 10: A9, W7, I3, C13, T2 y el puerto `DecisionClient` (M16)".

**01. Modelo de dominio**

- Encabezado: "**Revisión 11** (fecha): `FitCheck`, respuestas de idoneidad, `contingencyEligible`, `QuestionDetail`, `OptionSet`, `OptionContext`, `FitGate` y ventanas de resultado (M16, sección 10)."
- §5: `record FitCheck(QuestionKey key, MobKey mob, TacticId tactic)`; `DecisionRequest` gana `List<FitCheck> fitChecks`; `DecisionResult.Answered` gana `Map<QuestionKey, Double> fits` (finitos en `[0, 1]`); invariante: cada `FitCheck` **enviado** nombra una de las opciones **enviadas** de la pregunta `NOW` de su mob (las de opciones fusionadas o preguntas quitadas se eliminan en `prepare`).
- §4: `MobFacts` gana `Map<Situation, Set<TacticId>> contingencyEligible` (claves solo `IF_TARGET_IN_MELEE` e `IF_OWN_HEALTH_CRITICAL`; cada conjunto ⊆ tácticas del perfil).
- §5: `QuestionSpec` gana `Optional<TacticId> keep` (la táctica vigente; si está, pertenece a `options`). Las preguntas y opciones que llegan a `InFlightRequest` y a `ResponseParser` son las **enviadas** (`PreparedRequest.sent()`); `DecisionResult` no cambia por la deduplicación (sin respuestas sintéticas). `RenderedCriterion`, `Discriminator` y la deduplicación por texto viven en `adapter.jev` (01 §9: los textos no entran en `domain`).
- §7, reglas puras nuevas: `OptionSet` (quita tácticas idénticas en todo y aplica el tope, conservando `keep`), `OptionContext` (con `OptionFit` y `FitReason`), `FitGate` (con `FitThresholds` y `FitVerdict` sellado: `Adopt(TacticId, ControlLevel level, boolean rescued)` con `level` `JEV` o `ALTERNATIVE`, y `Reject(FallbackReason)`; si el veto quita todas las opciones, `Reject(LOW_CONFIDENCE)`). `IntentPolicy`: "con idoneidad, delega en `FitGate`".
- §3: `enum QuestionDetail { COMPACT, STANDARD, FULL }`; `DecisionTiming` gana `QuestionDetail questionDetail` y `boolean fitChecks` (la idoneidad solo se envía con `FULL`).
- §8: `Preparation` y `PreparedRequest` (tipos del puerto `DecisionClient`); `DecisionDispatcher.Outcome` gana `NOTHING_TO_ASK` y `PREPARE_FAILED`; `DecisionMetrics` cuenta `NOTHING_TO_ASK` como cualquier resultado del dispatcher y gana `options_merged` y el tramo `PREPARE`; `RequestBudget` reserva por `ceil(bodyChars / cpt)` con `cpt += 0,2 · (bodyChars / input_tokens − cpt)` acotado a [1,3; 2,0] y retira `estimateTokens(q, p)` (sustituye a 400 / 120 / 60; `RequestSizeGuard` sigue fijo en 1,5); `InFlightRequest` guarda solo las preguntas enviadas y gana `int bodyChars`; `EncounterSchedule.sequence()` significa "última secuencia tomada".
- §8: `OutcomeEvents`, `OutcomeWindow` (una por sesión, con el record `OutcomeWindow.Summary` que también usa M18) y `OutcomeMetrics` (4 × 6 celdas y 8 ventanas por sesión); `EngineSettings` gana `maxOptionsPerQuestion`, `fitVeto`, `fitRescue` (el nivel de detalle **no** va aquí: su única fuente es `DecisionTiming.questionDetail` de cada perfil, que hereda el default global al cargar, como `minimumConfidence`; C13); `MobSession` gana el anillo de 8 ventanas.
- §10, fila: "Idoneidad por opción, contingencias hipotéticas, opciones distinguibles y ventanas de resultado: sin ellos Jev recibe opciones idénticas, las contingencias ofrecen tácticas imposibles y no se puede medir si una decisión funcionó | M16".

**Registro de decisiones (entradas nuevas propuestas)**

- **DT-35. Atribución del daño de skills de MythicMobs.** ¿El `damage` de MythicMobs llega como `EntityDamageByEntityEvent` con el lanzador como causante? Si no, `OutcomeListener` atribuye por `io.lumine.mythic.bukkit.events.MythicDamageEvent` (la clase existe en el JAR 5.13.1; sus accesores se comprueban con javap antes de implementar). Se comprueba en la sesión 16.1. Estado: Abierta.
- **DT-31** se actualiza al cerrar M16.10 con la regla de M16.7.

## Riesgos y decisiones abiertas

| Riesgo | Mitigación |
|---|---|
| `fits_now` empuja a Jev a repetir lo que el código ya calculó y el banco lo premia de forma circular | Etiquetas escritas sin ver `fits_now` ni `poor fit now` (hoja de revisión, M16.9); brazo C (`standard`) sin ellos frente a D (`full`); si D no mejora a C, se retiran de `full` |
| Más detalle cuesta tokens y capacidad | Default `compact` (≈ 1 200 tokens reales estimados por solicitud de producción, como la 0.0.1). Capacidad con defaults: **5 mobs aislados por mundo** (tope de 300 solicitudes/min por mundo) y **8 en varios mundos** (tope de 600 000 tokens/min); con 1 500 000 tokens/min y los dos topes de solicitudes subidos a 1 200, **20** (límite de 1 200 solicitudes/min de la cuenta). Subir el tope de tokens exige subir también `maxRequestsPerMinute` y `maxRequestsPerMinutePerWorld`; los mobs que comparten encuentro gastan menos solicitudes. `standard` y `full` son opcionales y solo pasan a default con la regla de M16.2; tabla por nivel en M16.2 y en la guía del administrador. Los mobs sin presupuesto juegan en local (`NO_BUDGET`) |
| Las etiquetas del usuario tardan | Re-banco preliminar con `core24` (20–30 min); veredicto final con los 60 (45–60 min); el informe separa `REVIEWED` de `PROPOSED` |
| El umbral elegido no generaliza a combates reales | La sesión 16 y el banco grabado en servidor (guion §8) repiten la curva con escenarios reales |
| `noul` por opción duplica tokens | Apagada por defecto; solo con `full`, por perfil y solo si el re-banco (brazo E) la justifica |

## Terminado

M16 está terminado cuando se cumple W4 y además:

- [ ] Casillas de M16.1–M16.11 marcadas.
- [ ] Re-banco ejecutado con objetivos cumplidos y gasto ≤ 0,10 USD, o M16 abierto con causa documentada.
- [x] Default de `minimumConfidence` fijado con la regla de M16.7 (o conservado con su motivo) y DT-31 actualizada (0,45 con el brazo D; DT-31 Resuelta).
- [x] 00 y 01 actualizados; DT-35 registrada.
- [x] Sesión 16 en el paquete de pruebas.

## Estado de implementación

> **Local listo — re-banco de pago y servidor pendientes (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` (también con `--rerun-tasks`) termina en `BUILD SUCCESSFUL` sin avisos de javac ni de Javadoc estricto: **1484 pruebas en 201 clases, 0 fallos** (1350 de partida; 12 clases nuevas; 1477 en la entrega y 7 más con las correcciones de la auditoría, ver "Correcciones tras la auditoría"). `verifyPluginJar` y `ArchitectureRulesTest` pasan; ningún comentario `//` ni `/* */` en Java (C9, comprobado con un analizador que salta cadenas y bloques de texto). **Ninguna llamada de red** (ni a TypeSafe ni a otro host; los bancos y herramientas se probaron solo contra el servidor de contrato en `127.0.0.1`), no se ejecutó ninguna tarea de pago, nadie leyó `.env` (W6) y no se arrancó ningún servidor de Minecraft: las casillas de servidor y de re-banco siguen sin marcar y no hay evidencia inventada. 00 recibe la revisión 10 y 01 la revisión 11; el registro de M01 recibe DT-35 (`Abierta`) y una nota en DT-31 (sigue `Abierta`: el umbral solo cambia con el re-banco).

### Artefacto

`build/libs/ThePredict-0.0.1.jar`, 883 729 bytes, 457 entradas, SHA-256 `41c21f9c46aec78f8cef0d8b208f1e8dedab3c52bb52e161bcc2bbf1b091a08a` (tras las correcciones de la auditoría y la reauditoría; el de la entrega era `72c37a1d…95f8`); reproducible (dos builds limpios dan la misma huella). Registrado en el [CHANGELOG](../../CHANGELOG.md#artefacto).

### Archivos

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…` salvo indicación) |
|---|---|
| M16.1 | Creados `domain/OptionSet`, `engine/Preparation`, `engine/PreparedRequest`, `adapter/jev/CriterionRenderer` (con `RenderedCriterion`), `adapter/jev/OptionTextDedup` (con `Discriminator`), `adapter/jev/StatePaths`, `config/QuestionSettings`. Modificados `engine/DecisionClient` (`prepare`, `decide(PreparedRequest)`), `UnavailableDecisionClient`, `DecisionDispatcher` (orden nuevo, `NOTHING_TO_ASK`, `PREPARE_FAILED`), `RequestBudget` (`tokensFor`, `charsPerToken`; retirado `estimateTokens`), `InFlightRequest` (`fitChecks`, `bodyChars`), `EncounterSchedule` (Javadoc de `sequence()`), `EncounterQuestions`, `EngineSettings`, `DecisionMetrics` (`PREPARE`, `OPTIONS_MERGED`), `domain/QuestionSpec` (`keep`, `withOptions`), `SkillPurpose` (`semanticallyEqual`), `adapter/jev/QuestionBuilder`, `RequestEncoder` (`prepare`), `ResponseParser`, `SystemOneClient`, `TimingLog`, `config/DecisionSettings`, `PluginConfigurationLoader`, `ProposedConfiguration`, `src/main/resources/config.yml`, `adapter/server/binding/BindingWarnings` (`indistinguishable`) y `MythicProfileBinder`, raíz `DecisionServices` |
| M16.2 | Creados `domain/QuestionDetail`, `domain/OptionContext` (con `OptionFit` y `FitReason`). Modificados `domain/DecisionTiming` (`questionDetail`, `fitChecks`, `sendsFitChecks`), `adapter/jev/SemanticPhrases` (formas cortas, razones, instrucciones), `config/PluginSettings`, `ProfileSectionParser`, `ProfileSchema`, `ProfileParser` |
| M16.3 | `adapter/jev/RequestEncoder` (estado antes que preguntas), `QuestionBuilder` (`instructions`), `StateDraft` (`indexOfMob`, `indexOfParticipant`), `StatePaths` |
| M16.4 | `adapter/jev/SemanticEncoder` (hechos del objetivo dentro del mob, salud relativa, relevancia de eventos, `trimmed`), `StateDraft` (`participants`, orden de recorte nuevo), `SemanticPhrases` |
| M16.5 | Creados `domain/FitCheck`, `domain/FitGate` (con `FitThresholds` y `FitVerdict`). Modificados `domain/DecisionRequest` (`fitChecks`), `DecisionResult` (`Answered.fits`), `IntentPolicy` (sobrecargas con `fits`), `engine/AnswerGroups`, `DecisionAcceptor`, `TacticalExecution`, `adapter/jev/QuestionBuilder` (`fitCheck`), `ResponseParser` |
| M16.6 | `domain/MobFacts` (`contingencyEligible`, `eligibleIn`), `adapter/server/context/ReadyTactics` (`contingencyEligible`), `FactsAssembler`, `engine/EncounterQuestions`, `IntentControl` (`applyContingency`), `ReactiveLayer` |
| M16.7 | `src/test/java/…/adapter/jev/CalibrationRule` (sin cambio de defaults: no hay evidencia todavía) |
| M16.8 | Creados `engine/OutcomeEvents`, `OutcomeWindow` (con `Summary`), `OutcomeMetrics` (con `LevelOutcomes`), `SessionOutcomes`, `adapter/server/events/OutcomeListener`. Modificados `engine/IntentControl`, `ReactiveLayer`, `MobSession` (anillo de 8), `TacticalExecution` (`Parts.outcomes`), `StatusReport` (`Effectiveness`), `StatusAssembler`, `SessionReport` (`outcomes`), `Diagnostics` (`Sources.outcomes`), `adapter/server/events/CombatOutcomeListener` (`attackerId`, daño hecho), `admin/StatusFormatter` (sección `Effectiveness`, tramo `prepare`), `InspectFormatter` (línea `Outcomes`), raíz `SessionServices` |
| M16.9 | `src/test/java/…/adapter/jev/`: creados `BankScenario`, `BankMatrix`, `ScenarioBank`, `ScenarioBankTest`, `LabelReviewSheet`, `LabelReviewApply`; modificados `ScenarioDocument` (formato 2), `ScenarioFacts` (sin `LOW_HEALTH`, `production`), `ScenarioSeedBankTest`. Recursos `src/test/resources/scenarios/bank/` (60 documentos, `label-review.yml`, `profiles/bank-skirmisher.yml`); `scenarios/seed/` regenerado sin `seed-low-health.json`. `build.gradle.kts`: tareas `labelReviewSheet`, `applyLabelReview` y `scenarioBankTest`, fuera de `check` |
| M16.10 | `src/test/java/…/adapter/jev/`: creados `BenchArm`, `BankBench`, `BankStatistics`; modificados `LiveScenarioBench` (modo banco por defecto), `ScenarioDocument`, `BenchStatistics` (etiquetas de formato 2), `JevMeasurements` (tabla por nivel), `LiveToolsLoopbackTest`. `build.gradle.kts`: `-Parms`, `-Pscenarios`, `-PmaxUsd` e informe en `build/reports/m16/` |
| M16.11 | [Guía por sesiones](paquete-pruebas/Guia-de-pruebas-por-sesiones.md) (sesión 16 sin clave y con clave), [checklist](paquete-pruebas/Checklist-DT-y-casillas.md) (DT-35 y casillas de M16), [README del paquete](paquete-pruebas/README.md), [guion 0.0.1](Guion-servidor-0.0.1.md) (§7.2, §8, §9 y §16) |
| Documentos | Este documento; [README](README.md); [00](00-Arquitectura-y-reglas.md) (revisión 10); [01](01-Modelo-de-dominio.md) (revisión 11); [registro de M01](M01-Registro-de-decisiones.md) (DT-35, nota en DT-31); [guía del administrador](../../Guias/Guia-del-administrador.md) (§4.2, §5, §6.3–§6.5, §7, §7.1, §7.2, §8.2); [guía del autor](../../Guias/Guia-del-autor.md) (§4.6–§4.8, §7); [informe](Informe-0.0.1.md) (nota de cabecera); [CHANGELOG](../../CHANGELOG.md) |

### Pruebas (134 nuevas: 127 de la entrega y 7 de la auditoría)

| Clase | Pruebas | Qué demuestra |
|---|---|---|
| `domain/OptionSetTest` (nueva) | 5 | Solo se fusionan tácticas idénticas en todo (el origen del propósito no cuenta; tier, movimiento, descripción o choice sí); `keep` sobrevive a su grupo; el tope nunca quita `keep`; 255 tácticas → 8 opciones; tope 2 a 32 |
| `domain/OptionContextTest` (nueva) | 7 | Una prueba por fila de la tabla de `FitReason` en su orden y el corte a 3; escudo y flanco; área alrededor del lanzador no bloqueada; hipótesis de cada contingencia |
| `domain/FitGateTest` (nueva) | 8 | Tabla de verdad (con una táctica excluida que nunca sobrevive, ni con veto 0): veto de la primera → `ALTERNATIVE`, veto de todas → `LOW_CONFIDENCE`, rescate único → `JEV`/`ALTERNATIVE`, rescate doble rechazado, opción sin fit, `Adopt` con `LOCAL`/`BASE` rechazado, umbrales ordenados |
| `engine/DispatchPreparationTest` (nueva) | 10 | Secuencia tomada antes de `prepare` y un hueco que no descarta la respuesta siguiente; `NothingToAsk` sin reserva, sin solicitud en vuelo, sin efecto en el tracker ni en el modelo, una vez por latido, mob en `LOCAL` sin `markAnswered`; `Invalid` como fallo local (`INVALID_REQUEST` contado, servicio `READY`, sin reserva, mob en `LOCAL`, un aviso sin cuerpo y como mucho uno cada 10 s); un encuentro sobredimensionado no pausa a los demás, que siguen preguntando a Jev; `decide` sin volver a preparar y `evaluated` que nunca planifica; `NOW` quitado con sus contingencias y un `NOW` por mob en `AnswerGroups`; `OPTIONS_MERGED`; reserva por tamaño y aprendizaje de `cpt`; idoneidad en la misma solicitud |
| `engine/OutcomeWindowTest`, `OutcomeMetricsTest`, `OutcomeEventsTest` (nuevas) | 6 + 2 + 7 | Meta de cada movimiento; golpe confirmado a 20 ticks de un cast dañino; fracciones de vida acotadas a [0, 1]; índice `effect`; ventana de 60 ticks; la muerte de un participante solo llega a las ventanas de sus encuentros en su mundo; celdas por nivel y movimiento y anillo fijo tras 10 000 ventanas; apertura solo en adopciones, cierre por fin de intención, por plazo y por muerte; contingencia disparada y efectiva |
| `adapter/jev/CriterionRendererTest` (nueva) | 9 | Ejemplo `compact` exacto del diseño; formas cortas; orden de recorte a 120; discriminadores hasta 160 sin perderse; extracto de 40 cortado en palabra; claves fijas de `standard`/`full`; opción `full` más larga medida y las del ejemplo ≤ 900; sin cifras ni nombres de choice |
| `adapter/jev/OptionTextDedupTest` (nueva) | 8 | Discriminadores en orden sin gastar pasada; como mucho 4 renderizados; fusión en la primera o en `keep`; **invariante de texto en los tres niveles sobre el ejemplo, el banco completo y perfiles de peor caso**; 120/160 y una sola `poor fit now` |
| `adapter/jev/ScenarioBankTest` (nueva) | 11 | 48 + 12 escenarios en 12 familias; `core12`, `core24` y `all`; documentos iguales a los generados y sin UUID; reglas de etiquetas; `uninformative`; la familia `lowhp` sustituye a `seed-low-health`; contingencias hipotéticas; hoja sin "encaja ahora"; aplicación de la revisión con revisor, fecha y rechazos |
| `adapter/jev/CalibrationRuleTest` (nueva) | 8 | Wilson en los ejemplos del texto (28/30 sí, 27/30 no, 31/34, 36/40, 33/34, 22/24) y en 0 y n; umbral mínimo; curva no monótona; sin umbral válido y n < 30; solo `REVIEWED` e informativos; moda, empate por probabilidad media y media de confianza; límite de prohibidas |
| `adapter/server/events/OutcomeListenerTest` (nueva) | 3 | Fracción de vida, acotada a [0, 1] (sobredaño, NaN); daño y muertes ajenos ignorados sin leer la entidad |
| Ampliadas | 49 (51 nuevas, 2 retiradas) | `IntentPolicyTest` (+6, con veto 0), `DecisionRequestTest`, `QuestionSpecTest`, `DecisionResultTest`, `MobFactsTest`, `DecisionTimingTest`, `SkillPurposeTest` (+1 cada una), `EncounterQuestionsTest` (+5), `RequestBudgetTest` (+4, −1), `DecisionClientTest` (+1), `RequestEncoderTest` (+7), `ResponseParserTest` (+2), `SemanticEncoderTest` (+3), `SemanticPhrasesTest` (+1), `ContractFixturesTest` (+2), `SystemOneClientTest` (+1, −1), `LiveToolsLoopbackTest` (+2), `DecisionSettingsTest` (+1), `ProfileLoaderTest` (+2), `ReadyTacticsTest` (+2), `FactsAssemblerTest` (+1), `MythicProfileBinderSuiteTest` (+1), `CombatOutcomeListenerTest` (+2), `StatusFormatterTest` (+1), `TacticalExecutionABTest` (+1), `UserGuidesTest` (+1), `UserTestPackTest` (+1) |

**Pruebas existentes cambiadas por un comportamiento que M16 cambia a propósito:**
- Goldens `systemone/golden/*.json`: el estado usa `participants` y repite los hechos del objetivo dentro del mob (M16.4); el cuerpo completo usa `compact` (M16.2). Desde el re-banco el default es `full`, así que `RequestEncoderTest` y `ScenarioSeedBank` fijan `COMPACT` de forma explícita y los goldens no cambian.
- Semillas `scenarios/seed/*.json`: regeneradas con los mismos cambios; `seed-low-health.json` se retira (M16.9) y la copia congelada `seed-2026-09-29/` no cambia.
- `RequestEncoderTest`:
  - `questionsReferToTheirMobByCaptureOrder` espera la instrucción `compact`.
  - Las pruebas de guarda con descripciones largas usan `standard`, porque `compact` no escribe la descripción.
  - `theSizeGuardMeasuresTheLongestOptionsOfM15` mide el objeto `standard`.
- `SemanticEncoderTest.trimmingFollows…`: orden de recorte nuevo (eventos irrelevantes, eventos relevantes, señales, señales del objetivo protegido, participantes, mobs).
- `SystemOneClientTest`: la codificación pasa del ejecutor al hilo que llama a `prepare` (M16.1).
  - `decideEncodesOnTheExecutor…` pasa a `prepareEncodesOnTheCallingThread…`.
  - Las pruebas de cola llena y de cancelación bloquean en el transporte, no en el codificador.
  - Se retira `aRequestCancelledWhileEncodingIsNeverSent`, porque ya no hay codificación en el ejecutor.
  - La línea de tiempos ya no lleva `encode`: es el tramo `PREPARE`.
- `RequestBudgetTest.theEstimateConvergesTowardTheRealTokensPerQuestion` pasa a `theCharactersPerTokenLearnFromEachAnswer…`, porque la fórmula 400/120/60 se retira.
- `DecisionDispatcherTest` comprueba la reserva por tamaño preparado.
- Número de líneas o secciones: `InspectFormatterTest` (una línea más, `Outcomes`), `StatusFormatterTest` (sección `Effectiveness`), `SessionServicesTest` (cuatro listeners).
- Los dobles del cliente de `engine` (`CoordinatorFixture.Client`, `LoadFixture.Jev`) preparan con `PreparedFixtures`, cuyo cuerpo mide 1,5 × la estimación de la 0.0.1. Así las cifras de presupuesto de M09 y M13 no cambian: el arnés A/B da las mismas cifras de antes más `effect` y `hitRate`.

### Medidas locales (sin red; `ScenarioFacts`, tabla "Request size per question detail" de `JevMeasurements`)

| Nivel | Producción 1 × 1 (estado ≈ 700 car.) | 1 × 1 con 16 eventos | 16 × 16 con 16 eventos |
|---|---|---|---|
| `compact` | 1 812 car. · 1 208 t a 1,5 · ≈ 1 047 a 1,73 | 2 444 · 1 630 · ≈ 1 413 | 25 592 · 17 062 · ≈ 14 793 |
| `standard` | 4 276 · 2 851 · ≈ 2 472 | 4 608 · 3 072 · ≈ 2 664 | 61 094 · 40 730 · ≈ 35 314 |
| `full` | 4 805 · 3 204 · ≈ 2 777 | 5 459 · 3 640 · ≈ 3 155 | 71 888 · 47 926 · ≈ 41 554 |
| `full` + idoneidad | 8 066 · 5 378 · ≈ 4 662 | 8 682 · 5 788 · ≈ 5 018 | rechazada por la guarda (> 64 000 t) |

Antes de M16 (M15): 1 × 1 con 16 eventos medía 3 755 caracteres y 16 × 16, 47 684. `compact` corrige el crecimiento del 67 % que M15 dejó anotado y vuelve a tamaños de la 0.0.1, ahora con 7 opciones en lugar de 5.

El estado más grande admitido sin recortar (16 × 16 × 16) mide 10 091 caracteres (antes 7 699) y cabe en los 12 000 de `maxStateChars`.

Con el default `compact` de entonces, la capacidad estimada con estos tamaños era de 5 mobs aislados por mundo y 9 en varios mundos (el diseño estimaba 8). Tras el re-banco el default es `full`; la capacidad con los tokens reales está en "Resultado del re-banco". La guía del administrador (§7.1) publica la tabla con los caracteres medidos.

### Resultado del re-banco (M16.10, 2026-09-30)

El orquestador ejecutó las dos fases con la aprobación del usuario: P1 (`core12` × 10, brazo B, 120 llamadas) y P2 (60 escenarios × 2 repeticiones × brazos A–E, 600 llamadas). Informes y respuestas crudas, sin clave ni cabeceras, están en [`evidencia/m16-rebanco-2026-09-30/`](evidencia/m16-rebanco-2026-09-30/) (`P1/`, `P2/`). Como el informe de P2 se escribió antes de terminar la revisión de las 60 etiquetas, las tablas se **recalcularon sin red** con las etiquetas actuales desde `P2/answers.jsonl` (`./gradlew reanalyseBank --offline`, `BankReanalysis`): [`recomputed-2026-09-30.md`](evidencia/m16-rebanco-2026-09-30/recomputed-2026-09-30.md). Los 600 cuerpos se prepararon de nuevo desde el banco y los 600 tienen el tamaño grabado, así que se juzga exactamente lo que se envió.

**Gasto real:** P1 80 670 tokens de entrada (≈ 0,0034 USD); P2 669 941 (≈ 0,0281 USD). Total 750 611 tokens, **≈ 0,0315 USD**, por debajo del techo de 0,10. P2 tuvo 3 fallos, todos del brazo A.

**Tabla por brazo** (preguntas `NOW`, 48 escenarios `REVIEWED` e informativos, cada escenario distinto pesa 1; tokens de la solicitud de producción: 1 mob, 1 participante, `NOW` + una contingencia, 7 opciones, medidos con `usage.input_tokens`):

| Brazo | Conservadas a 0,5 | Aceptables entre conservadas | Wilson inferior | `best` entre conservadas | Prohibidas conservadas | Umbral de la regla de M16.7 | Tokens de producción |
|---|---|---|---|---|---|---|---|
| A (control) | 68,8 % | 78,8 % | 0,622 | 60,6 % | 0 | ninguno | 1 388 |
| B (`compact`) | 54,2 % | 92,3 % | 0,759 (`n` 26) | 65,4 % | 0 | ninguno | 953 |
| C (`standard`) | 79,2 % | 78,9 % | 0,637 | 57,9 % | 1 | ninguno | 1 701 |
| D (`full`) | 66,7 % | 93,8 % | 0,799 | 71,9 % | 0 | **0,45** (`n` 41, 90,2 %, Wilson 0,775, 0 prohibidas) | 1 884 |
| E (`full` + idoneidad) | 70,8 % | 91,2 % | 0,770 | 67,6 % | 0 | 0,50 (`n` 34, 91,2 %, Wilson 0,770) | 2 950 |

Contingencias: 9 escenarios informativos por brazo, menos de los 8 por situación que pide la regla, así que heredan el umbral de `NOW`; en todos los brazos el 100 % de las contingencias conservadas a 0,5 es aceptable.

**Decisión del usuario (2026-09-30):** `decision.questionDetail: full` y `decision.minimumConfidence: 0.45`.

- **Detalle `full`:** cumple la regla de cambio de default de M16.2 frente a B: +12,5 puntos de conservadas a 0,5 (66,7 % frente a 54,2 %), acierto mayor (93,8 % frente a 92,3 %) y ninguna prohibida más (0).
- **Umbral 0,45:** es el menor que cumple la regla de M16.7 en el brazo D: 41 escenarios conservados (85,4 %), 90,2 % aceptables, Wilson inferior 0,775, 0 prohibidos, 63,4 % `best`.
- Calibración del brazo D, no decreciente con tramos fundidos: 75 % (0–0,5, 16 escenarios), 88,9 % (0,5–0,7) y 95,7 % (0,7–1).
- `fitVeto` y `fitRescue` no cambian: E no mejora a D y cuesta un 57 % más.

**Objetivos de M16.10 con el default elegido (brazo D a 0,45):**

| Medida | Objetivo | Resultado |
|---|---|---|
| Conservadas | ≥ 70 % | 85,4 % |
| Aceptables | ≥ 90 % y Wilson ≥ 0,75 con `n` ≥ 30 | 90,2 %, Wilson 0,775, `n` 41 |
| Prohibidas | ≤ 1 | 0 |
| `best` | ≥ 60 % | 63,4 % |
| Calibración | no decreciente | sí |
| Contingencias | ≥ 80 % aceptables | 100 % |
| Mejora frente a A en conservadas a 0,5 | +30 puntos | −2,1 puntos (**no cumple**) |
| Tokens reales de producción | ≤ 1 200 | 1 884 (**no cumple**; `compact`: 953) |

Los objetivos se escribieron para `compact`, y con `compact` fallan las conservadas (54,2 %) y la mejora frente a A. El usuario eligió más calidad por más tokens; el coste es el doble que con `compact`.

**Capacidad y coste con los tokens reales:**

| Detalle | Tokens de producción | Mobs aislados por mundo | Mobs en varios mundos | USD por mob-hora a plena carga |
|---|---|---|---|---|
| `compact` | 953 | 5 | 10 | ≈ 0,14 |
| `full` (default) | 1 884 | 5 | 5 | ≈ 0,28 |

Cada mob aislado envía unas 60 solicitudes por minuto. En un solo mundo manda, con los dos niveles, el tope de 300 solicitudes/min por mundo (5 mobs). En varios mundos, con `full` manda el tope de 600 000 tokens/min (5 mobs). Con `compact` mandan **a la vez** el tope de tokens (600 000 / (953 × 60) = 10,5) y el global de 600 solicitudes/min (10 mobs), así que el efectivo es 10. Es la misma lectura que la guía del administrador, §7.1.

**Discrepancia con la nota del orquestador:** el orquestador daba 8 mobs en varios mundos para `compact`. Con los 953 tokens medidos salen 10: por tokens, 10,5; por el tope global de solicitudes, 10. El 8 es la estimación de diseño de M16.2, que usaba ≈ 1 208 tokens a 1,5 c/t. Para `full` salen 5, igual que dice el orquestador.

**DT-31** pasa a Resuelta con esta evidencia. La muestra es modesta: 48 escenarios informativos × 2 repeticiones; el límite de Wilson ya lo tiene en cuenta.

### Re-banco de pago (M16.10): herramienta (ejecutado el 2026-09-30; lo que sigue es la herramienta y su estimación previa)

Lo que cada llamada envía se mide con el código real, codificando cada escenario con su brazo, 1 mob, 1 participante y una sola pregunta:

| Brazo | Caracteres por llamada (medidos) | Tokens por llamada (cota a 1,5) | Diseño |
|---|---|---|---|
| A (control) | 2 110 | 1 407 | ≈ 2 170 |
| B (`compact`) | 1 195 | 797 | ≈ 1 460 |
| C (`standard`) | 2 329 | 1 553 | ≈ 2 650 |
| D (`full`) | 2 631 | 1 754 | ≈ 3 360 |
| E (`full` + idoneidad) | 5 277 | 3 518 | ≈ 5 180 |

| Fase | Llamadas | Tokens (cota) | USD a 0,042/M |
|---|---|---|---|
| P1: `core12` × 10, brazo B | 120 | 94 670 | ≈ 0,0040 |
| P2: 60 escenarios × 2 × brazos A–E | 600 | 1 083 828 | ≈ 0,0455 |
| **Total** | **720** | **≈ 1,18 M** | **≈ 0,050** (a 1,73 c/t reales, ≈ 0,043) |
| Iteración opcional: brazos B y C, 60 × 2 | 240 | 282 142 | ≈ 0,0118 |

Comandos, que el usuario o el orquestador ejecuta con su aprobación (las 60 etiquetas ya están revisadas, 2026-09-30):

```text
set -a; source .env; set +a
./gradlew liveScenarioBench --no-daemon -PliveJev=true -PscenarioDir=src/test/resources/scenarios/bank -Parms=B -Prepetitions=10 -Pscenarios=core12 -PmaxCalls=130
./gradlew liveScenarioBench --no-daemon -PliveJev=true -PscenarioDir=src/test/resources/scenarios/bank -Parms=A,B,C,D,E -Prepetitions=2 -Pscenarios=all -PmaxCalls=620
```

Cada ejecución hace esto:
- Imprime la estimación antes de la primera llamada (W7).
- Se niega sin `-PliveJev=true`, sin `TYPESAFE_API_KEY` en el entorno, por encima de `-PmaxCalls` o por encima de `-PmaxUsd` (0,10 por defecto).
- Escribe `build/reports/m16/live-scenario-bench.md` y `build/reports/m16/answers.jsonl`, sin clave ni cabeceras.
- Sobrescribe los dos archivos, así que tras P1 hay que copiarlos a `evidencia/m16-rebanco-<fecha>/p1/` antes de lanzar P2.

El informe tiene estas tablas:
- tokens por brazo y solicitud de producción estimada;
- una fila por escenario (táctica modal, confianza media, cambios entre repeticiones);
- curva por tramos con margen y entropía;
- conservadas a 0,5 y acierto, `REVIEWED` separado de `PROPOSED`;
- contingencias;
- idoneidad frente a etiquetas;
- regla de M16.7 para `NOW` y contingencias, marcada `PRELIMINARY` mientras no estén los 60 revisados (desde el 2026-09-30 lo están, así que ya no sale);
- objetivos de M16.10.

### Revisión de etiquetas por el usuario (M16.9)

Las 60 etiquetas las propuso el implementador, escritas desde la situación sin mirar "encaja ahora". **El usuario revisó en el chat las 60: las 24 de `core24` el 2026-09-29 y las 36 restantes el 2026-09-30. El banco completo está `REVIEWED`** (`BankStatistics.wholeBankReviewed()` es verdadero), así que la regla de M16.7 ya no sale marcada `PRELIMINARY` y el re-banco puede fijar defaults. Se aplicó con `label-review.yml` y `./gradlew applyLabelReview --offline` (60 documentos escritos; ninguna marca `uninformative` cambió). La herramienta tiene un solo campo `reviewer:`, así que los 60 documentos llevan `reviewedBy: "usuario (revisión por chat 2026-09-30)"` y `reviewedOn: 2026-09-30` (fecha UTC al aplicar); la fecha real de la revisión de `core24`, el 2026-09-29, queda en esta tabla.

**Revisión de `core24` (2026-09-29):**

| Escenario | Decisión del usuario |
|---|---|
| `bank-nolos-01`, `bank-nolos-02` | `REVIEWED` con cambio: `keep_distance` pasa de aceptable a prohibida (prohibidas: `keep_distance_and_shoot`, `hold`, `keep_distance`; aceptables: `approach`, `press_and_strike`, `close_in_and_slam`). Nota: "esperar a distancia también pierde al objetivo" |
| `bank-lowhp-01` | `REVIEWED` con cambio: mejor `retreat_and_defend`; aceptables `retreat_and_defend` y `keep_distance`; `keep_distance_and_shoot` pasa a neutral; prohibidas sin cambio (`press_and_strike`, `close_in_and_slam`, `approach`, `hold`). Nota: "el usuario acepta: retirarse, habilidad de alto daño o defensa; aquí no hay opción de alto daño ni postura defensiva (manejo de posturas pendiente)" |
| `bank-shield-02` | `REVIEWED` sin cambios (el slam sigue aceptable). Nota: el usuario confirmará en el servidor si el escudo bloquea el daño de MythicMobs (DT-33; evidencia javap: `damage` usa por defecto `cause` `ENTITY_ATTACK` → `mobAttack` e `ignoreshield=false`, así que el escudo podría bloquearlo). No cambia ninguna regla de código; DT-33 sigue `Abierta` y el paso 15.5 del paquete lo pregunta de forma literal |
| Los otros 20 de `core24` | `REVIEWED` sin cambios (`bank-pillar-01` y `bank-pillar-02` se cambian después, en la revisión del 2026-09-30) |

**Revisión de los 36 restantes y ajuste de `pillar` (2026-09-30):**

| Escenario | Decisión del usuario |
|---|---|
| `bank-nolos-03` (#49, invisible y lejos) | Mejor `close_in_and_slam`; aceptables `close_in_and_slam`, `retreat_and_defend`, `approach`; prohibidas `keep_distance_and_shoot`, `hold`, `keep_distance`; `press_and_strike` neutral. Nota: "sin visión: priorizar daño en área o curación para castigar la huida temporal" |
| `bank-nolos-04` (#50, oculto tras bloques, lejos, en combate) | Mejor `approach`; aceptables `approach`, `close_in_and_slam`; prohibidas `keep_distance_and_shoot`, `hold`, `keep_distance`. Nota: "buscar en el último punto conocido y daño en área; futuro: búsqueda de última posición y habilidades de carga" |
| `bank-pillar-01` (#19, visible, puede disparar) y `bank-pillar-03` (#51, visible, ofensivo) | `hold` pasa de aceptable a prohibida (aceptables `keep_distance_and_shoot`, `keep_distance`; prohibidas `press_and_strike`, `close_in_and_slam`, `approach`, `hold`). Nota: "hold solo fuera de combate o en postura defensiva" |
| `bank-pillar-02` (#20, sin línea de visión) | `hold` pasa a neutral (espera defensiva): aceptable solo `keep_distance`; prohibidas sin cambio. Misma nota |
| `bank-pillar-04` (#52, sin línea de visión, inalcanzable) | El usuario pidió "lo mismo que el primero que respondí" (#49: daño en área o curación); el slam no alcanza a un objetivo inalcanzable, así que se aplica curación o defensa: mejor `retreat_and_defend`; aceptables `retreat_and_defend`, `keep_distance`; `keep_distance_and_shoot` neutral (sin visión) y `hold` neutral (defensiva); prohibidas `press_and_strike`, `close_in_and_slam`, `approach`. Nota: "hold solo fuera de combate o en postura defensiva; interpretación: como #49 (curación/defensa); el área no alcanza a un objetivo inalcanzable" |
| Los otros 32 | `REVIEWED` sin cambios ("todo lo demás okey") |

**Punto futuro: manejo de posturas.** El usuario acepta, con poca vida, retirarse, una habilidad de alto daño o una postura defensiva. El perfil de ejemplo no ofrece ninguna postura defensiva y ThePredict no modela posturas (defensiva, bloqueo, carga); queda como punto futuro, fuera de M16.

**Punto futuro: `hold` solo fuera de combate o defensivo (posturas).** Regla del usuario: en combate no se sugiere `hold`; solo fuera de combate o como espera defensiva. Hoy las etiquetas lo aplican; el motor y los perfiles no lo imponen. Va con el manejo de posturas. Otros puntos futuros de la revisión: búsqueda de la última posición conocida del objetivo y habilidades de carga (`bank-nolos-04`).

Para revisar de nuevo o cambiar etiquetas:

1. `./gradlew labelReviewSheet --offline` escribe `build/reports/m16/label-review.md`, sin red ni costo. Muestra primero los 24 de `core24` y después los otros 36; cada uno con el estado en frases, las opciones como las describe `standard` sin "encaja ahora", la propuesta con su motivo y la entrada YAML que hay que copiar.
2. Editar `src/test/resources/scenarios/bank/label-review.yml`:
   - `reviewer:` con el nombre de quien revisa;
   - por escenario, `status: REVIEWED` (de acuerdo) o `DISPUTED` (el escenario está mal);
   - solo si cambia algo, `best:`, `acceptable:`, `forbidden:` (nombres de opción) y `note:`.
3. `./gradlew applyLabelReview --offline` vuelca la revisión en los 60 JSON con el revisor y la fecha, y ejecuta `ScenarioBankTest`. Rechaza con el escenario y el campo una opción que no se ofrece, `best` fuera de `acceptable` o una táctica a la vez aceptable y prohibida.

`core24` basta para el re-banco preliminar (20–30 min); cambiar un default exige los 60 (45–60 min). Tres escenarios de `cont-critical` (con `low_health_phase`) son `uninformative` y no cuentan para el acierto.

### Correcciones tras la auditoría (2026-09-29)

| # | Hallazgo | Corrección |
|---|---|---|
| B1 | `TacticalExecution` (4 argumentos) y `TacticalExecution.Parts` (11) eran constructores de compatibilidad muertos con un solo uso; el de `Parts` creaba un `OutcomeEvents` desconectado | Retirados; `LoadFixture` usa la forma completa. Desviación 6 y 01 (§5 y §8) corregidas |
| B2 | Un `Preparation.Invalid` (por ejemplo, un encuentro por encima de la guarda de tamaño) pausaba el servicio para todos | Cambio de diseño: `PREPARE_FAILED` contado como `INVALID_REQUEST`, aviso limitado sin cuerpo, nada enviado ni reservado y **sin efecto en el tracker**; los mobs del encuentro juegan en local. Pruebas nuevas: un encuentro sobredimensionado no pausa a los demás y el servicio sigue `READY`. M16.1, desviaciones 8 y 18, 01 §5 y §8, guía del administrador (§6.1, tabla `dispatch`, §7.1) y M18 actualizados |
| B3 | El informe del banco está en `build/reports/m16/`, no en `m13/`; los topes por defecto del guion eran los de la reproducción de cuerpos grabados | Guía por sesiones K5.6 y guion §8.5 con `build/reports/m16/`; guion §9: con el banco, `maxCalls` 780 y `maxUsd` 0,10 por defecto; 1 300 solo al reproducir cuerpos grabados |
| B4 | "Seis semillas" (con la retirada `seed-low-health`) en CHANGELOG, guía K5.5 e informe | Cinco semillas y referencia al banco `scenarios/bank/` (también en M14) |
| — | Constructores de compatibilidad sin nota de retirada | Javadoc como el de `SkillChoice` en `DecisionTiming`, `QuestionSpec`, `DecisionRequest`, `DecisionResult.Answered`, `MobFacts`, `EngineSettings` y `DecisionSettings` |
| — | `OutcomeListener.onDeath` sin `ignoreCancelled` | `ignoreCancelled = true` |
| — | Fracciones de vida sin acotar | Acotadas a [0, 1] en `OutcomeListener.fraction` y en `OutcomeWindow` (NaN y negativos cuentan 0); pruebas nuevas |
| — | `OutcomeEvents.died` recorría todas las sesiones | Índice de participantes (desviación 22); prueba nueva |
| — | `CombatOutcomeListener` afirmaba DT-35 "confirmado en servidor" | Javadoc corregido: DT-35 sigue `Abierta` |
| — | Con `fitVeto: 0` una opción no aplicable daba `INVALID_RESPONSE` | Se salta y se adopta la siguiente válida (desviación 21); pruebas nuevas en `FitGateTest` e `IntentPolicyTest` |
| — | Coste de `prepare` citado de M13 | Medido con `JevMeasurements` (columna "Prepare us", solo local): p50 ≈ 17 / 188 / 425 / 475 µs para producción `compact`, 16 × 16 `compact`, `standard` y `full` |
| — | Guion §9 y 16.3: `standard` ≈ 0,40 USD | ≈ 0,43 USD (también en la guía por sesiones y en M16.11) |
| — | `LabelReviewApply` escribía documento a documento | Valida todas las entradas y construye todos los documentos antes de escribir; cada uno va a un temporal y se mueve a su sitio. Prueba: una entrada inválida no cambia ningún documento ni deja temporales |
| — | `SemanticEncoder.draft` pasaba de 30 líneas | Partido (`mobEntries`); desviación 19 corregida |
| — | Nota de M17.0 | Texto de M17.0 en el documento de M17 (solo documentación) y "Pendiente" de este documento |
| R1 | (Reauditoría) `PRELIMINARY` se calculaba solo con los escenarios de la ejecución, así que un informe de `core12` revisado dejaba de marcarse | Se calcula sobre **todo el banco** (`BankStatistics.wholeBankReviewed`: los 60 `REVIEWED` o no informativos). Con `core24` revisado y 36 `PROPOSED`, todo informe decía `PRELIMINARY`. Tras la revisión de los 60 (2026-09-30) ya no sale, y `LiveToolsLoopbackTest` exige `wholeBankReviewed()` verdadero y un informe sin `PRELIMINARY` |
| R2 | (Reauditoría) El modo de reproducción de cuerpos grabados no escribía `answers.jsonl` | Lo escribe junto al informe, con el mismo formato (una línea compartida, `BankBench.line`, brazo `REPLAY`) y las mismas garantías: sin clave, cabeceras ni cuerpo. Prueba en `LiveToolsLoopbackTest` |
| R3 | (Reauditoría) Texto de pruebas de M16.1, Javadoc de clase de `DecisionDispatcher` (ahora guarda el estado del aviso) y cota superior en `DispatchPreparationTest` (≤ 10 fallos en 180 ticks: una preparación por latido) | Corregidos; nota histórica en M13 sobre las semillas |

### Desviaciones y decisiones

| # | Desviación o decisión | Motivo |
|---|---|---|
| 1 | `OptionSet.distinct(ordered, profile)` y `distinct(ordered, profile, keep)`, `capped(distinct, max)` y `capped(distinct, max, keep)` (sobrecargas) en lugar de un parámetro `Optional<TacticId>`; `OptionSet.identical` pública (la usa el aviso del binder) | C4 prohíbe `Optional` como parámetro de una firma pública |
| 2 | `OptionContext.of(Tactic, TacticalProfile, MobFacts, Set<UseCondition>)` y `forSituation(…, Situation)` con `hypothetical(now, situation)`, en lugar de `of(Tactic, Optional<SkillChoice>, MobFacts, Optional<ParticipantFacts>, Set<UseCondition>, MovementSettings)`. La choice y el movimiento salen del perfil y ninguna regla necesita el participante: el escudo llega en `now`, y la visibilidad y el alcance en la relación. Bajo `IF_TARGET_IN_MELEE` se quitan `TARGET_AT_RANGE` y `TARGET_FLEEING` y no se evalúa `OUT_OF_REACH`, medido a la distancia real; bajo `IF_OWN_HEALTH_CRITICAL` el lanzador cuenta como crítico. `SHIELD_BLOCKS_IT` exige además una entrega dirigida al objetivo, porque un área alrededor del lanzador no la para el escudo (M15.6) | C4; que criterios e instrucción no se contradigan (G6) |
| 3 | `RenderedCriterion` vive dentro de `CriterionRenderer` y `Discriminator` dentro de `OptionTextDedup`; `render` es estático y `RenderedCriterion.json()` convierte el texto de un objeto en `JsonObject`. `QuestionBuilder.criterion(tactic, profile, detail, fit)` no existe: el criterio lo escribe `CriterionRenderer.render`, `QuestionBuilder` gana `choice`, `instructions` y `fitCheck`, y conserva `build(situation, options, profile, ref)` y `criterion(tactic, profile)` como brazo A de control | La deduplicación compara el texto final, que solo escribe el renderizador; el brazo A necesita el criterio de M15.7 intacto |
| 4 | Forma corta de la skill: `<entrega> <papel>` separados por espacio ("area hit around self summons allies"), solo el papel con `SELF` ("self heal"), y después `, <daño>` y `, slow wind-up`. La base más larga mide 64 caracteres, no 65 | Es la forma de los ejemplos del diseño. El caso peor de 160 cabe igualmente, y la prueba lo comprueba |
| 5 | `EngineSettings` gana `maxOptionsPerQuestion` y `FitGate.FitThresholds fitThresholds` (no dos `double`). `config.QuestionSettings` agrupa las seis claves nuevas de `decision.*` como componente `questions` de `DecisionSettings`. Los defaults viven en `OptionSet.DEFAULT_MAX_OPTIONS`, `QuestionDetail.DEFAULT` y `FitThresholds.DEFAULT` | Un default en un solo lugar (C13); `config` y `engine` los leen de `domain` |
| 6 | Constructores de compatibilidad (W5): `DecisionTiming` de 6 argumentos, `QuestionSpec` de 4, `DecisionRequest` de 5, `DecisionResult.Answered` de 3, `MobFacts` de 7, `EngineSettings` de 12 y `DecisionSettings` de 20; el Javadoc de cada uno dice, como el de `SkillChoice`, que producción pasa la forma completa y que se conserva solo mientras lo usen pruebas (`DecisionSettings` de 20 lo usan además `standard()` y la migración del esquema 1, y se retira con ella). Los de `TacticalExecution` (4 argumentos) y `TacticalExecution.Parts` (11) **se retiraron tras la auditoría**: tenían un solo uso, `LoadFixture`, y el de `Parts` creaba un `OutcomeEvents` desconectado. Un `MobFacts` sin `contingencyEligible` ofrece en cada contingencia las tácticas elegibles ahora | Unas 100 construcciones de prueba; producción siempre usa la forma completa. La captura real rellena `contingencyEligible` siempre que hay objetivo |
| 7 | `InFlightRequest` conserva `participants` y `estimatedTokens`, y gana `fitChecks` (mapa por clave) y `bodyChars` | `AnswerGroups` necesita los fit checks enviados para dar los fits por opción; el diagnóstico sigue usando los participantes |
| 8 | `DecisionDispatcher`: un cliente que lanza en `prepare` produce `PREPARE_FAILED` con el mismo efecto que `Invalid` (desde la auditoría, sin efecto en el tracker); uno que lanza o no devuelve future en `decide` sigue siendo `CLIENT_ERROR` con el efecto de un `INVALID_REQUEST` en el tracker. `OPTIONS_MERGED` cuenta, en cada pregunta enviada, las opciones planificadas que no se enviaron y, en cada pregunta que cayó por sí sola, todas menos una; no cuenta las contingencias que caen con el `NOW` de su mob, porque nunca se compararon. `CONTINGENCIES_ASKED` se cuenta al enviar | Fallo seguro (A6); contadores sin inflar (A7) |
| 9 | Los hechos del objetivo dentro del mob repiten también la salud, la armadura y el rango de amenaza del participante objetivo, no solo sus señales. Van antes de los hechos de relación, así que `grouped` sigue siendo el último (M15). El recorte quita primero las señales de participantes no protegidos y luego las del objetivo protegido (repetidas dentro del mob), antes que ninguna entrada completa | La casilla de M16.4 exige que ningún hecho del objetivo obligue a mirar otro arreglo; con solo las señales, la salud del objetivo seguía fuera. Cuesta unos 80 caracteres por mob preguntado |
| 10 | La relevancia de los eventos solo cambia el orden de recorte; el orden de presentación sigue siendo del más nuevo al más antiguo | "Conserva primero" es una prioridad de recorte |
| 11 | `OutcomeWindow` no guarda el papel principal de la choice, porque ni `Summary`, ni `OutcomeMetrics`, ni `inspect` lo usan. Un golpe confirmado consume el cast dañino (un golpe por cast). Una ventana se abre al cambiar de táctica o de nivel y en cada contingencia disparada: una renovación o un replan local de la misma táctica no es una adopción nueva. `BASE` nunca abre ventana. Un cambio de generación descarta la ventana abierta. El anillo de 8 vive en `SessionOutcomes`, auxiliar de paquete de `MobSession` | `Summary` es el único tipo de resumen (M18); `MobSession` ya pasaba de 400 líneas |
| 12 | `OutcomeListener` escucha la muerte en `LOW` (con `ignoreCancelled = true` desde la auditoría) y no en `MONITOR` | MythicMobs notifica su `MythicMobDeathEvent` desde el mismo evento y el ciclo de vida cierra la sesión; la ventana debe ver la muerte antes. Se comprueba en la sesión 16.1 junto con DT-35 |
| 13 | La sección nueva de `status` se llama `Effectiveness` y sus líneas empiezan por `outcomes: level=…`. El grupo de contadores `outcomes` de `ExecutionOutcome` conserva su nombre. `LevelOutcomes` lleva además los totales de casts y golpes | La guía y el guion ya citaban el grupo `outcomes`; el arnés A/B necesita los totales para `hitRate` |
| 14 | El arnés A/B (`TacticalAB`) modela el daño así, igual para los dos brazos: 0,05 de la vida del objetivo por cast en cuerpo a cuerpo sin escudo levantado y 0,002 por tick en cuerpo a cuerpo para el mob. Ninguna cifra previa del arnés cambia | Lo pide M16.8 ("modelo simple de daño por cast guionizado") |
| 15 | El banco usa un modelo de alcance: objetivo a 7 bloques en `RANGED` y más allá del alcance a distancia en `BEYOND`. Todos los brazos ven las mismas opciones, así que el banco no aplica el tope de 5 opciones de las contingencias `compact`. La familia `shield` usa `purpose-shield` en cuerpo a cuerpo y el guardián flanqueable a distancia; `lowhp` y `cont-critical` sin contexto usan el guardián sin `low_health_phase`. La revisión guarda un campo `note` y conserva la fecha de una etiqueta que no cambió | Etiquetas comparables entre brazos; la matriz del diseño no fija estos detalles |
| 16 | Herramienta del re-banco: clases nuevas `BenchArm`, `BankBench` y `BankStatistics`. `BenchStatistics` se queda para reenviar cuerpos grabados de M13 con otra carpeta, y el banco es la carpeta por defecto. Hay un tope extra `-PmaxUsd` (0,10). El brazo A usa el estado de M16.4, porque solo existe una versión del estado | C10; techo de gasto de M16 |
| 17 | **La estimación impresa no coincide con la tabla del diseño ± 10 %** en los brazos B, C y D, que miden entre un 12 % y un 22 % menos. La prueba comprueba que la estimación es la suma de los cuerpos que se enviarán y que respeta los topes. El total medido es de 1,18 M tokens y ≈ 0,050 USD, frente a 1,30 M y 0,055 | La tabla del diseño se escribió antes que el código; lo medido es la cota real |
| 18 | La opción `full` más larga posible (tres papeles, todas las piezas, cuatro condiciones, cuatro riesgos, descripción de 200 caracteres y tres razones por lista) mide **1 211** caracteres, no ≤ 900 | Medido y fijado en `CriterionRendererTest`. Las opciones `full` del ejemplo miden ≤ 900 (prueba). `RequestSizeGuard` sigue midiendo la pregunta más larga; una solicitud que la supera es `PREPARE_FAILED` **solo para su encuentro**, sin pausar el servicio (auditoría, M16.1) |
| 19 | Tamaño de clases (C10): `SemanticPhrases` 486 líneas; `IntentPolicy` 301; `DecisionDispatcher`, `RequestBudget`, `IntentControl`, `CriterionRenderer`, `CombatOutcomeListener` y `FactsAssembler` entre 250 y 290; `OptionContext`, `SkillPurpose`, `ReadyTactics`, `SemanticEncoder`, `QuestionBuilder`, `StatusFormatter`, `ProfileSectionParser` y `DecisionAcceptor` entre 200 y 230. Tras la auditoría ningún método de lo tocado pasa de 30 líneas: la entrega afirmaba lo mismo, pero `SemanticEncoder.draft` pasaba de 30 y se partió (`mobEntries`) | Son sobre todo Javadoc y tablas de frases fijas donde el diseño pide ponerlas (`SemanticPhrases`, `OptionContext.FitReason`). Separarlas movería la responsabilidad que el diseño asigna a cada clase |
| 20 | Las etiquetas de los 60 escenarios las propuso el implementador; el usuario revisó las 24 de `core24` el 2026-09-29 y las 36 restantes el 2026-09-30 (ver "Revisión de etiquetas"); las 60 están `REVIEWED` | M16.9 pide proponerlas y que las revise el usuario; con los 60 revisados el re-banco ya puede cambiar defaults |
| 21 | `FitGate.judge(answer, fits, excluded, minimumConfidence, thresholds)`: las tácticas no aplicables se excluyen antes del veto, en lugar de inyectarles un fit 0 | Con `decision.fitVeto: 0` un fit 0 no veta, y la opción no aplicable acababa adoptada y rechazada como `INVALID_RESPONSE`; ahora se salta y se juzga la siguiente válida (auditoría) |
| 22 | `OutcomeEvents` recibe `EncounterRegistry`; `died(entity, tick)` solo cierra la ventana de un miembro y `participantDied(world, player)` recorre solo los miembros de los encuentros de ese participante por el índice de participantes | La muerte de un participante recorría todas las sesiones (auditoría) |
| 23 | `DecisionDispatcher` recibe un `Consumer<String>` para el aviso limitado de `PREPARE_FAILED` (la raíz le pasa el mismo receptor de avisos que al coordinador) | El aviso sin cuerpo del cambio de diseño de M16.1 |

### Pendiente

- ~~Re-banco de pago (M16.10)~~ **Hecho el 2026-09-30.** Resultado arriba. Defaults `questionDetail: full` y `minimumConfidence: 0.45` aplicados en la entrega de M17. `fitVeto` y `fitRescue` no cambian. DT-31 está Resuelta.
- **Sesión 16 del paquete:** 16.1 sin clave (incluye DT-35 y la muerte vista en `LOW`) y 16.2 con clave (≈ 0,011 USD).
- **Para M17:** M17.0 debe grabar sus referencias doradas con este código, que ya usa la clave `participants` (M16.4), antes de tocar nada. Los 60 escenarios de `scenarios/bank/` a los tres niveles son la base de su prueba de igualdad. Tras la auditoría, el texto de M17.0 añade tres cosas: las referencias se regeneran si M16.10 cambia algún default o la redacción; los SHA-256 del motor son de cuerpos reales codificados, no de los cuerpos de relleno (`"x"`) de `PreparedFixtures`; y el punto 4 registra también `OutcomeEvents.dealt` (y `taken`/`died`).
- **Manejo de posturas** (punto futuro de la revisión de `bank-lowhp-01`).
- **`hold` solo fuera de combate o defensivo (posturas):** regla del usuario de la revisión del 2026-09-30, hoy solo en las etiquetas.
- **Búsqueda de la última posición conocida y habilidades de carga** (punto futuro de `bank-nolos-04`).
- **Para M18:** `OutcomeWindow.Summary` es el tipo que recibe `DecisionTap.outcomeClosed`, y `DecisionDispatcher` ya tiene `Preparation` y el cuerpo en el hilo del servidor para `questionsPlanned` y `requestBody`.

Nota de implementación M26 (2026-10-07). Las frases de `EnemyPattern` viven en `EnemyPatternFact`. El orden de recorte acordado es zona, grupo, memoria y patrones; la memoria la inserta M27 y esta entrega no recorta el borrador de estado.
