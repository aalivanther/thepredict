# M03. Configuración v2, binder y migración

> Estado: Local listo — servidor pendiente (2026-09-29; ver "Estado de implementación") · Entrega: A · Depende de: M02 · Desbloquea: M04–M08 · Diseño: [plan §8 y §9](../Plan-Jev-y-combate-tactico.md) · Reglas: [00](00-Arquitectura-y-reglas.md) · Dominio: [01](01-Modelo-de-dominio.md)

> **Revisión 25 (2026-10-09, M29).** El perfil puede declarar `Decision` (`Pruning`, `MaxOptions`, `CandidateScoring`, `Diversity`) y `Distill.Mode`. Esas claves solo bajan el global. No hay clase `OverlayLoader`: la superposición es `ProfileOverlay` y el escritor de `autoconfig` no abre el archivo del autor. Erratas E29-1 a E29-13 aplicadas en M29, no en este módulo.

## Objetivo

Leer `config.yml` y los perfiles v2 de forma estricta, producir un catálogo inmutable de `TacticalProfile` vinculado a MythicMobs y recargarlo sin perder el último catálogo válido. Las instalaciones v1 obtienen una propuesta de migración que nunca activa consultas.

## Alcance

### Incluye

- Esquema v2 de `config.yml` y de los perfiles `AI/*.yml`, con valores por defecto en un solo lugar.
- Contextos definidos con condiciones nativas de MythicMobs.
- Binder en `adapter.server.binding` con validaciones de IDs, skills, especies y textos.
- Catálogo versionado y recarga transaccional.
- Propuesta de migración v1 → v2 y primer inicio v2.

### No incluye

- La generación de metas (M05) y la evaluación de contextos en combate (M07).
- Aplicar la migración sobre los archivos del administrador: en la 0.0.1 es manual (M12.6 decide que no hay comando propio).
- El contenido final del boss de ejemplo (M12).

## Base existente

| Archivo | Acción |
|---|---|
| `config/ConfigurationValues.java` | Se conserva; se añaden lectura de listas, secciones y URI |
| `config/PluginConfigurationLoader.java` | Se amplía con las rutas v2 y la detección de esquema |
| `config/ProviderSettings.java` | Se sustituye por `config/JevSettings.java` (se conserva hasta M12 para el camino actual) |
| `config/RuntimeSettings.java` | Se sustituye por `config/DecisionSettings.java` (se conserva hasta M09) |
| `config/ProfileDefaults.java` | Se amplía con los campos v2 |
| `config/ProfileLoader.java`, `ProfileSchema.java` | Se amplían para v2 y producen `domain.TacticalProfile` |
| `config/PredictorProfile.java` | Se conserva para el camino actual; M12 la elimina |
| `config/FirstRunFiles.java` | Se actualiza la lista de recursos |
| `adapter/mythicmobs/MythicProfileBinder.java`, `MythicDefinitionLookup.java`, `BoundProfile.java`, `MythicCompatibility.java` | Pasan a `adapter/server/binding/` y se amplían |
| `src/main/resources/config.yml`, `AI/README.txt`, `AI/example.yml.example` | Se reescriben en v2 |
| `build.gradle.kts` (`verifyPluginJar`) | Se actualiza la lista de recursos obligatorios |
| Pruebas de `config` y `MythicProfileBinderSuiteTest` | Se amplían con los casos v2 |

## Subpuntos

### M03.1 `config.yml` v2

- **Propósito:** una configuración global estricta, con unidades y rangos, de la que salen `JevSettings`, `DecisionSettings` y `ProfileDefaults`.
- **Diseño:**

  | Clave | Tipo | Rango | Default | Unidad |
  |---|---|---|---|---|
  | `schemaVersion` | int | 2 | obligatorio | — |
  | `jev.baseUrl` | URI | `https`; `http` solo con `localhost` | `https://api.typesafe.ai` | — |
  | `jev.model` | texto | no vacío | `jev-1.13.0` | — |
  | `jev.apiKeyEnvironment` | texto | puede estar vacío | `TYPESAFE_API_KEY` | — |
  | `jev.apiKey` | texto | puede estar vacío | `''` | — |
  | `jev.timeoutMillis` | int | 100–5000 | 800 | ms |
  | `jev.connectTimeoutMillis` | int | 100–10 000 | 1500 | ms |
  | `jev.maxResponseBytes` | int | 16 384–4 194 304 | 262 144 | bytes |
  | `jev.maxStateChars` | int | 2000–45 000 (hasta M13, 100 000: la guarda de tamaño de M08 rechaza estado más pregunta por encima de 48 000 caracteres a 1,5 por token) | 12 000 | caracteres |
  | `decision.maxRequestsPerMinute` | int | 1–1200 | 600 | solicitudes/min |
  | `decision.maxRequestsPerMinutePerWorld` | int | 1–`maxRequestsPerMinute` | 300 | solicitudes/min |
  | `decision.maxInFlightRequests` | int | 1–64 | 8 | solicitudes |
  | `decision.maxInFlightRequestsPerWorld` | int | 1–`maxInFlightRequests` | 4 | solicitudes |
  | `decision.maxInputTokensPerMinute` | int | 1000–10 000 000 | 600 000 | tokens/min |
  | `decision.encounterHeartbeatTicks` | int | 10–200 | 20 | ticks |
  | `decision.contingenciesPerMob` | int | 0–2 | 1 | preguntas |
  | `decision.minimumConfidence` | decimal | 0–0,99 | 0,5 | — |
  | `decision.tacticSwitchMargin` | decimal | 0–0,99 | 0,15 | — |
  | `decision.maxInstallsPerTick` | int | 1–100 | 10 (M01 lo ajusta) | mobs/tick |
  | `decision.maxEncounterEvaluationsPerTick` | int | 1–64 | 2 (M13 lo ajusta) | encuentros/tick |
  | `decision.maxProbesPerTick` | int | 8–1024 | 64 | sesiones/tick |
  | `decision.maxCompletionsPerTick` | int | 1–256 | 16 | respuestas/tick |
  | `decision.maxSignalsPerTick` | int | 1–1024 | 64 | señales/tick |
  | `decision.maxTickWorkMicros` | int | 100–20 000 | 2000 | µs |
  | `decision.maxDecisionAgeMillis` | int | 100–5000 | 1000 | ms |
  | `decision.lowConfidenceStreakForLocal` | int | 1–20 | 3 | respuestas |
  | `decision.searchRadiusBlocks` | decimal | 8–128 | 48 | bloques (tope sobre `FOLLOW_RANGE`) |
  | `decision.logDecisions` | bool | — | false | — |
  | `debug.recordScenarios` | bool | — | false | — (M13; graba escenarios con alias) |
  | `profileDefaults.observationIntervalTicks` | int | 1–20 | 2 | ticks |
  | `profileDefaults.minimumDecisionIntervalTicks` | int | 10–200 | 20 | ticks |
  | `profileDefaults.maximumIntentDurationTicks` | int | 20–400 | 40 | ticks |
  | `profileDefaults.criticalHealthRatio` | decimal | 0–1 | 0,3 | — |
  | `profileDefaults.woundedHealthRatio` | decimal | `criticalHealthRatio`–1 | 0,6 | — |
  | `profileDefaults.meleeReachBlocks` | decimal | >0 | 2,5 | bloques |
  | `profileDefaults.bandInnerBlocks` | decimal | >`meleeReachBlocks` | 5 | bloques |
  | `profileDefaults.bandOuterBlocks` | decimal | >`bandInnerBlocks` | 9 | bloques |
  | `profileDefaults.rangedReachBlocks` | decimal | ≥`bandOuterBlocks` | 16 | bloques |
  | `profileDefaults.retreatDistanceBlocks` | decimal | >0 | 10 | bloques |
  | `profileDefaults.retreatSpeed` | decimal | 0,1–3 | 1,2 | multiplicador |
  | `profileDefaults.approachSpeed` | decimal | 0,1–3 | 1,15 | multiplicador |

  - Validaciones cruzadas: `encounterHeartbeatTicks >= minimumDecisionIntervalTicks`; `maximumIntentDurationTicks >= minimumDecisionIntervalTicks`; `timeoutMillis < maximumIntentDurationTicks × 50`, para que una respuesta pueda llegar antes de que venza la intención; `maxDecisionAgeMillis >= timeoutMillis`.
  - Records: `config.JevSettings(URI baseUrl, String model, String apiKeyEnvironment, String apiKey, Duration timeout)` con `resolvedApiKey()` e `isConfigured()`, igual que hoy; `config.DecisionSettings(...)` con los campos `decision.*`; `config.ProfileDefaults` con los campos `profileDefaults.*`.
  - La variable de entorno tiene prioridad sobre `jev.apiKey`. La clave nunca aparece en mensajes.
- **Archivos:** `config/JevSettings.java`, `config/DecisionSettings.java`, `config/ProfileDefaults.java`, `config/PluginConfigurationLoader.java`, `resources/config.yml`.
- **Reglas:** C5, C13, C8.
- **Pruebas:** cada clave fuera de rango; clave desconocida; tipo incorrecto; cada validación cruzada; YAML roto sin sustituir por defaults activos.
- **Aceptación:**
  - [x] Cada default existe solo en `ProfileDefaults.standard()` o en el record correspondiente.
  - [x] Los errores indican `config.yml` y la ruta completa.

### M03.2 Esquema de perfil v2

- **Propósito:** que un autor declare tácticas, contextos, movimiento, amenaza, IA base y tiempos en un archivo validado.
- **Diseño:**
  - Estructura (ejemplo del plan §9 ampliado con `Contexts` y `Timing`):

    ```yaml
    schemaVersion: 2
    guardian_ai:
      MythicID: ThePredictGuardian
      TypeOfMythicID: MOB
      Type: NORMAL
      Objective: Survive while defeating the current target
      Choices:
        melee: { MythicID: ThePredictGuardianMelee, TypeOfMythicID: SKILL, RequiresTarget: true }
      Tactics:
        press_and_strike: { Movement: APPROACH, Choice: melee, Priority: 70 }
        hold: { Movement: HOLD }
      Contexts:
        low_health:
          Conditions:
          - <línea de condición de MythicMobs>
          Priority: 90
          Tactics: [hold]
          DirectTactic: hold
      Movement:
        Band: { Inner: 5, Outer: 9 }
        Retreat: { Distance: 10, Speed: 1.2 }
        Approach: { Speed: 1.15 }
        Arena: { Radius: 24 }
      Threat: { AssistsOthers: 1.5, RangedHarass: 1.0, BlockedHits: 0.25 }
      BaseAI:
      - 1 meleeAttack
      Timing: { MinimumConfidence: 0.5, SwitchMargin: 0.15 }
    ```

  - Precedencia: defaults internos → `config.yml` → perfil. Los límites globales (`decision.*`) no se amplían desde un perfil.
  - `CD` se acepta por compatibilidad como `extraCooldownTicks`; el cooldown recomendado es el nativo de la skill.
  - `Choices.<id>.Kind` acepta `MYTHIC_SKILL` (por defecto, exige `MythicID`) o `VANILLA_MELEE` (sin `MythicID`; ataque vanilla de M10.9) y produce `ChoiceKind`.
  - `Health.Critical` y `Health.Wounded` sustituyen por perfil a `profileDefaults.criticalHealthRatio` y `woundedHealthRatio`, y producen `HealthThresholds`; `Movement.MeleeReach` y `Movement.RangedReach` sustituyen a los alcances por defecto.
  - Campos v1 (`Behaviors`, `Combinators`, `Operations`, `DecisionIntervalTicks`, `LowHealthRatio`, `AttackRangeBlocks`) en un archivo v2 son un error que remite a la migración.
  - Formato de error: `<archivo>: <perfil>.<ruta>: <mensaje>`, por ejemplo `guardian.yml: guardian_ai.Tactics.press_and_strike.Choice: unknown choice 'mele'`.
  - Un perfil con errores se excluye; los demás se cargan (plan §8). Dos perfiles para el mismo mob se excluyen ambos.
- **Archivos:** `config/ProfileLoader.java`, `config/ProfileSchema.java`, `resources/AI/example.yml.example`, `resources/AI/README.txt`.
- **Reglas:** C13, I4, I5.
- **Pruebas:** perfil completo válido; cada campo desconocido; referencias rotas; ID de táctica inválido o reservado (`none`, `debug_*`); prioridades contradictorias; dos perfiles independientes; perfil duplicado por mob.
- **Aceptación:**
  - [x] El resultado es un `TacticalProfile` que cumple 01 §3.
  - [x] Ningún error de un perfil impide cargar otro válido.

### M03.3 Contextos nativos

- **Propósito:** que los contextos se escriban con condiciones de MythicMobs y no con un lenguaje propio (plan §4).
- **Diseño:**
  - Al vincular, cada `ContextRule.nativeConditions` se convierte con `MythicBukkit.inst().getSkillManager().getConditions(List<String>)`, que devuelve `SkillCondition`.
  - Las condiciones desconocidas se resuelven un tick después (plan §2). El binder programa una validación diferida de un tick: un contexto con una condición sin resolver se desactiva con un error visible. M01 confirma cómo detectarlo; la alternativa es comprobar el primer resultado de evaluación en M07.
  - Atajos compatibles: `idle` (sin objetivo), `attacking_phase` (objetivo en banda `MELEE`), `tracking_enemy` (objetivo en `RANGED` o `BEYOND`) y `low_health_phase` (salud `CRITICAL`). No tienen líneas nativas; M07 los calcula con las bandas.
  - La evaluación en combate pertenece a M07; M03 solo guarda las condiciones ya convertidas en el `BoundProfile`.
- **Archivos:** `adapter/server/binding/MythicProfileBinder.java`, `adapter/server/binding/BoundProfile.java`.
- **Reglas:** A1, D5.
- **Pruebas:** con dobles de `MythicDefinitionLookup`: líneas válidas, vacías y desconocidas; atajos sin líneas.
- **Aceptación:**
  - [x] Ninguna condición se interpreta en código propio.
  - [x] Una condición desconocida produce un error con archivo, perfil y contexto — parte local: con dobles de `MythicDefinitionLookup`, el error llega con archivo, perfil y contexto y el contexto queda desactivado.
  - [ ] Una condición desconocida produce un error con archivo, perfil y contexto — parte de servidor: confirmar que `CustomCondition#getCondition()` sin resolver se detecta en MythicMobs real (ver "Pendiente de verificación en servidor").

### M03.4 Binder

- **Propósito:** vincular cada perfil con objetos cargados de MythicMobs y rechazar lo que no se puede ejecutar.
- **Diseño:**
  - `MythicProfileBinder.bind(Map<String, TacticalProfile>)` devuelve `Map<String, BoundProfile>` y una lista de problemas.
  - `BoundProfile` (record local del adapter) guarda el `TacticalProfile`, las `Skill` por `ChoiceId`, las condiciones por `ContextId`, el nombre del tipo de entidad y si la especie usa `Brain`.
  - IDs exactos (`MobExecutor.getMythicMob` usa la clave exacta): si falla, se busca una coincidencia ignorando mayúsculas en `getMobTypes()` o en las skills cargadas y se sugiere en el mensaje.
  - `PredictValues` de cada skill con la lectura actual (lista o texto separado por comas), más `IGNORES_SHIELD`.
  - Se lee el `Cooldown` nativo de cada skill; si falta y no hay `CD`, se emite un aviso.
  - Aviso si una skill de `Choices` también aparece en la lista `Skills` del mob con un trigger normal, porque podría sumar daño mientras ThePredict controla.
  - Especies con `Brain` (lista del plan §2: Allay, Armadillo, Axolotl, Camel, Frog, Tadpole, Goat, CopperGolem, HappyGhast, Nautilus, ZombieNautilus, Sniffer, Zoglin, Breeze, Creaking, Hoglin, Piglin, PiglinBrute, Warden y Villager): solo tácticas con `Movement: NONE`. El tipo se lee del `Type` del mob; M01 confirma la forma exacta del nombre.
  - Aviso si `Objective` o `Description` parecen no estar en inglés: caracteres no ASCII o palabras frecuentes de otros idiomas. Es un aviso, nunca un error.
  - `Predictor` se lee de forma estricta como hoy: ausente es falso; un valor no booleano es un error.
- **Archivos:** mover y ampliar `adapter/server/binding/MythicProfileBinder.java`, `MythicDefinitionLookup.java`, `BoundProfile.java` y `MythicCompatibility.java`.
- **Reglas:** I7, D5, D7, C7.
- **Pruebas:** con dobles del lookup: ID con otra capitalización, skill inexistente, `PredictValues` desconocido, especie con `Brain` y movimiento, trigger normal duplicado, texto no inglés.
- **Aceptación:**
  - [x] Cada rechazo indica archivo, perfil y ruta.
  - [x] Los avisos no impiden cargar el perfil.

### M03.5 Catálogo inmutable y recarga transaccional

- **Propósito:** que una recarga fallida no deje al servidor sin perfiles (plan §8).
- **Diseño:**
  - `config.CatalogStatus { SETUP_PENDING, LOADED, INVALID }`: `SETUP_PENDING` sin perfiles ni errores, `INVALID` con algún error, `LOADED` en el resto; lo usa `/thepredict status` (M12).
  - `config.ProfileCatalog(long catalogRevision, Map<String, TacticalProfile> profiles, List<ConfigProblem> problems)`, con `CatalogStatus status()` y `config.ConfigProblem(Severity, String file, String path, String message)`, records locales de `config`.
  - La recarga construye un catálogo nuevo completo (leer, validar y vincular) y lo intercambia en el hilo del servidor en una sola asignación. `catalogRevision` aumenta en cada intercambio.
  - Si falla algo global (lectura del directorio, YAML ilegible de todos los archivos), se conserva el catálogo anterior y se informa.
  - `MythicReloadedEvent` vuelve a vincular los perfiles ya leídos con los objetos nuevos de MythicMobs, porque los anteriores pueden quedar obsoletos.
  - Corrige el comportamiento actual de `ThePredictPlugin.reloadProfiles`, que vacía los vínculos al fallar.
- **Archivos:** `config/ProfileCatalog.java`, `config/ConfigProblem.java`, cambios en `ThePredictPlugin.java`.
- **Reglas:** A6, A7, I11.
- **Pruebas:** recarga válida; recarga con un perfil roto (los demás siguen); fallo global (se conserva el anterior); revisión creciente.
- **Aceptación:**
  - [x] Nunca hay un momento sin catálogo tras un fallo.
  - [x] Las sesiones pueden comparar `catalogRevision` (M06).

### M03.6 Migración v1 → v2

- **Propósito:** ofrecer al administrador una conversión revisable sin tocar sus archivos ni activar consultas.
- **Diseño:**
  - Detección: `config.yml` sin `schemaVersion` y con `provider:` es v1; un perfil sin `schemaVersion` con `Operations` o `Behaviors` es v1.
  - Salida: `plugins/ThePredict/migration/<fecha-hora>/config.yml.proposed`, `AI/<archivo>.yml.proposed` y `report.md`. No se sobrescribe nada.
  - Reglas de conversión: `provider` → `jev` con `model: jev-1.13.0`, `apiKey: ''` y `apiKeyEnvironment: TYPESAFE_API_KEY`; la clave de OpenRouter nunca se copia. Los límites v1 sin equivalente se listan en el informe. Cada choice se convierte en una táctica sin movimiento (`Movement: NONE`) que conserva sus restricciones; cada `Operation` pasa a un contexto con el atajo equivalente; `DirectChoice` pasa a `DirectTactic`; las combinaciones ambiguas quedan marcadas para corrección manual.
  - Idempotente: repetirla genera la misma propuesta; un archivo v2 no se migra.
  - En la 0.0.1, aplicar la propuesta es manual: el administrador revisa el informe, guarda sus archivos y copia los propuestos (M12.6). No hay comando de migración.
- **Archivos:** `config/MigrationPlanner.java` y recursos de plantilla del informe.
- **Reglas:** A6, C8, W3.
- **Pruebas:** configuración v1 real del repositorio; perfil v1 de ejemplo; migración repetida; clave presente en v1 que no aparece en la propuesta.
- **Aceptación:**
  - [x] Ningún archivo del administrador cambia.
  - [x] El informe lista cada decisión de conversión.

### M03.7 Primer inicio v2

- **Propósito:** que una instalación nueva quede lista para configurar, sin alarmas falsas (plan §9).
- **Diseño:**
  - `FirstRunFiles` crea solo archivos ausentes: `config.yml` v2 (clave vacía, modelo probado), `AI/README.txt`, `AI/example.yml.example` y el paquete `examples/` inactivo.
  - Sin clave: estado `UNCONFIGURED`. Sin perfiles: "configuración pendiente". Ninguno es un error.
  - No se lanzan inferencias automáticas; la comprobación de metadatos es de M08.
  - `verifyPluginJar` exige los recursos nuevos y sigue comprobando `apiKey: ''`.
- **Archivos:** `config/FirstRunFiles.java`, recursos en `src/main/resources`, `build.gradle.kts`.
- **Reglas:** C13, C14.
- **Pruebas:** instalación limpia; segundo inicio sin sobrescribir; `PluginResourcesTest` actualizado.
- **Aceptación:**
  - [x] Dos inicios seguidos no cambian archivos existentes.
  - [x] El JAR contiene los recursos v2 y ninguna clave.

## Riesgos y decisiones abiertas

- M01 confirma cómo detectar una condición nativa que no se resolvió y cómo nombra MythicMobs el tipo de entidad de un mob.
- La heurística de idioma puede dar avisos falsos; por eso nunca es un error.
- `decision.maxInstallsPerTick` y los valores de banda son iniciales; M01 y M13 los ajustan.
- `config.ProfileCatalog` y `config.ConfigProblem` son tipos locales de `config`, no del dominio.

## Terminado

- [x] Configuración y perfiles v2 cargan, validan y vinculan con mensajes con archivo y ruta.
- [x] Recarga transaccional probada; el catálogo anterior sobrevive a un fallo.
- [x] Propuesta de migración generada para la configuración v1 actual.
- [x] `./gradlew clean build javadoc` pasa.

## Estado de implementación

> **Local listo — servidor pendiente (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` pasa sin avisos de compilación ni de Javadoc, y `verifyPluginJar` exige los recursos v2: **409 pruebas, 0 fallos** (313 de partida + 96 netas, incluidas las 9 de las correcciones tras la auditoría). Ninguna prueba hace llamadas de red ni lee `.env` (W6). 00 y 01 no se modificaron. Las confirmaciones que dependen de un servidor real están en "Pendiente de verificación en servidor".

### Archivos

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…` salvo indicación) |
|---|---|
| M03.1 | Creados `config/JevSettings`, `DecisionSettings`, `PluginSettings` (los tres apartados y sus comprobaciones cruzadas), `ConfigurationException`, `SettingsChecks`. Reescritos `config/ProfileDefaults`, `PluginConfigurationLoader` y `ConfigurationValues` (listas, secciones y URI). Reescrito `src/main/resources/config.yml` |
| M03.2 | Reescrito `config/ProfileLoader`; reescrito `config/ProfileSchema`; creados `config/ProfileParser`, `ProfileSectionParser` y `ProfileLoadResult`. Reescritos `resources/AI/example.yml.example` y `AI/README.txt` |
| M03.3 | `adapter/server/binding/NativeConditions`, `LiveNativeConditions`; `domain/ContextRule` gana `SHORTCUTS` e `isShortcut()` |
| M03.4 | Creados en `adapter/server/binding/`: `MythicProfileBinder`, `BoundProfile`, `BindingResult`, `BindingProblem`, `BindingWarnings`, `MythicDefinitionLookup`, `LiveMythicDefinitions`, `SkillMetadata`, `BrainSpecies`, `EnglishTextHint`. Movido `MythicCompatibility` desde `adapter/mythicmobs/`. Borrados `adapter/mythicmobs/MythicProfileBinder` y `MythicDefinitionLookup` |
| M03.5 | Creados `config/ProfileCatalog`, `ConfigProblem`, `CatalogStatus` y `CatalogReloader` (raíz); cambios en `ThePredictPlugin` |
| M03.6 | Creados `config/MigrationPlanner`, `MigrationProposal`, `MigrationNotes`, `ConfigurationMigration`, `ProposedConfiguration`, `ProfileMigration`, `OperationMigration`, `YamlText`; recurso `src/main/resources/migration/report-template.md` |
| M03.7 | `config/FirstRunFiles` sin cambios (la lista de siete recursos se conserva; cambia su contenido). Convertidos a v2 `resources/examples/AI/ThePredictGuardian.yml` y `examples/README.txt`. `build.gradle.kts`: `verifyPluginJar` exige `AI/README.txt`, `examples/README.txt` y `migration/report-template.md`, `schemaVersion: 2` y la ausencia de `provider:`, y sigue exigiendo `apiKey: ''` |
| Otros | `RuntimeCompatibility` importa `MythicCompatibility` desde `adapter.server.binding`. Se conservan sin cambios `config/ProviderSettings`, `RuntimeSettings`, `PredictorProfile` y `adapter/mythicmobs/BoundProfile` (W5) |

### Pruebas (409; 96 netas nuevas)

- `config`: `ConfigProblemTest` (4), `ProfileCatalogTest` (4), `JevSettingsTest` (7), `DecisionSettingsTest` (6), `ProfileDefaultsTest` (7, antes 1), `PluginSettingsTest` (4), `PluginConfigurationLoaderTest` (10, antes 5), `RuntimeSettingsTest` (3, trasladadas sin cambios desde `PluginConfigurationLoaderTest` porque `RuntimeSettings` sigue viva), `ProfileLoaderTest` (21, antes 11), `ConfigurationRecoverySuiteTest` (2, reescritas en v2), `MigrationPlannerTest` (9). `ProviderSettingsTest` y `FirstRunFilesTest` sin cambios.
- `adapter.server.binding`: `MythicProfileBinderSuiteTest` (13, antes 2 en `adapter.mythicmobs`), `BoundProfileTest` (2), `BrainSpeciesTest` (2), `EnglishTextHintTest` (2), `MythicCompatibilityTest` (2, movida). `MythicFixtures` es un ayudante de prueba con dobles de las interfaces Premium.
- Raíz: `CatalogReloaderTest` (10); `PluginResourcesTest` (3, antes 2); `domain.ContextRuleTest` (5, antes 4).
- `adapter.openrouter.DecisionLifecycleSuiteTest` (3) conserva todas sus aserciones del camino OpenRouter; como `config.yml` y los perfiles empaquetados ya son v2 y el loader v1 desaparece, construye sus entradas v1 como datos de prueba (texto `provider:` y el `PredictorProfile` del ejemplo v1).

Casos de frontera cubiertos: cada clave numérica justo dentro y justo fuera de su rango; clave desconocida y campo v1 en un archivo v2; tipos incorrectos (texto, decimal en entero, escalar en lugar de sección, URI rota, desbordamiento de entero); cada comprobación cruzada en su límite; YAML roto sin defaults activos; IDs de táctica inválidos, cortos y reservados (`none`, `debug_*`); choice, táctica y `DirectTactic` desconocidas; skill inexistente y con otra capitalización; `PredictValues` desconocido, vacío y en texto separado por comas; especie con `Brain` con y sin movimiento; texto no inglés; trigger normal duplicado; condición rechazada y condición sin resolver; atajos con y sin `Conditions`; 16 y 17 contextos; perfil duplicado por ID y por mob; archivo v1 detectado y no cargado; fallo global de lectura con conservación del último catálogo; revisión creciente; propuesta v1 que nunca activa consultas ni copia la clave; migración repetida idéntica; ningún archivo del administrador cambia.

### Desviaciones y decisiones

| Desviación o decisión | Motivo |
|---|---|
| `JevSettings` tiene además `connectTimeout`, `maxResponseBytes` y `maxStateChars`, y sobrescribe `toString()` para ocultar la clave | La firma de M03.1 lista cinco componentes, pero la tabla incluye las claves de la revisión 2 que M08 necesita para `SystemOneSettings`. El `toString` de un record mostraría la clave |
| `resolvedApiKey(UnaryOperator<String>)` e `isConfigured(UnaryOperator<String>)` además de las versiones sin argumento | Probar la prioridad de la variable de entorno sin leer el entorno real (W6); las versiones sin argumento usan `System::getenv` |
| `jev.baseUrl` con `http` se acepta para `localhost`, `127.0.0.1` y `::1`; se rechazan usuario, consulta y fragmento en la URL | "localhost" se interpreta como bucle local; una URL con credenciales o consulta podría filtrar datos en registros |
| Default de `decision.maxDecisionAgeMillis` = `min(5000, jev.timeoutMillis + 200)` (1000 con el timeout por defecto), y la clave **no aparece** en el `config.yml` empaquetado ni en la propuesta de migración, para que el default derivado actúe; los límites por mundo toman por defecto `min(default, límite global)` | Coincide con la tabla para los defaults y con M10 ("`timeoutMillis + 200`"); escribir 1000 de forma explícita anularía la derivación al cambiar el timeout. La clave sigue siendo válida si el administrador la escribe |
| `debug.recordScenarios` vive en `DecisionSettings.recordScenarios` | La clave pertenece a la tabla de M03.1 y no merece un record propio; la raíz la traducirá con el resto (D10) |
| Tipos nuevos de `config` no previstos en el documento: `PluginSettings`, `ConfigurationException`, `ProfileLoadResult`, `MigrationProposal`; auxiliares internos `ProfileParser`, `ProfileSectionParser`, `SettingsChecks`, `MigrationNotes`, `ConfigurationMigration`, `ProposedConfiguration`, `ProfileMigration`, `OperationMigration`, `YamlText` | `PluginSettings` aloja las comprobaciones cruzadas entre secciones; `ConfigurationException` separa ruta y mensaje para el formato `<archivo>: <ruta>: <mensaje>`; el resto respeta C10. Son tipos locales de `config` (01 §9), no del dominio, así que 01 no cambia |
| `MythicProfileBinder.bind` devuelve `BindingResult(Map<String, BoundProfile>, List<BindingProblem>)`; `BindingProblem` lleva severidad, perfil opcional, ruta y mensaje, y la raíz añade el archivo al convertirlo en `ConfigProblem` | "devuelve un mapa y una lista de problemas" necesita un resultado; el binder no conoce los archivos y D5 le impide importar `config` |
| Las condiciones de cada contexto se guardan como `NativeConditions` (líneas, `SkillCondition` convertidas, líneas inválidas y líneas sin resolver); la implementación viva es `LiveNativeConditions` | El constructor de `SkillCondition` llama a `MythicBukkit` y no se puede crear en pruebas; la interfaz permite dobles. M07 obtiene las `SkillCondition` con `conditions()` |
| La validación diferida se ejecuta 2 ticks después de vincular, no 1 | `CustomCondition` programa su resolución a +1 tick al convertirse (evidencia `javap`); esperar un tick más evita un falso "sin resolver" por el orden de tareas del planificador |
| Una línea que MythicMobs rechaza al convertirla (`InvalidCondition` o resultado vacío) también desactiva solo su contexto, con error | Mismo trato que una condición sin resolver (M03.3): el perfil sigue cargado (A6) |
| Atajos de contexto: `ContextRule.SHORTCUTS` e `isShortcut()` en `domain` (constante y ayudante, sin tipo nuevo). Un atajo con `Conditions` y un contexto normal sin `Conditions` son errores | El loader y M07 necesitan la misma lista; `domain` es el único paquete común. 01 no cambia porque no se añade ningún tipo |
| "Prioridades contradictorias": dos contextos con la misma `Priority` son un aviso (gana el declarado primero); una prioridad fuera de 0–100 es un error | M02 desempata por orden de declaración, así que el empate no es inválido, pero el autor debe saberlo |
| `Timing` del perfil solo admite `MinimumConfidence` y `SwitchMargin`, con el rango de `decision.*`; latido, intervalos y contingencias son siempre globales. `Retreat.Speed` y `Approach.Speed` usan el rango de `profileDefaults` (0,1–3) | "Los límites globales no se amplían desde un perfil"; es el ejemplo del documento |
| `Movement` es obligatorio en cada táctica; `Priority` por defecto 50 (`ProfileDefaults.PRIORITY`); como máximo 255 tácticas (`QuestionSpec.MAX_OPTIONS`) y 16 contextos (M07.7) | Explícito mejor que un movimiento implícito; los topes vienen de 01 y M07 |
| Defaults por perfil sin clave en `config.yml` como constantes de `ProfileDefaults`: `PRIORITY`, `EXTRA_COOLDOWN_TICKS`, `REQUIRES_TARGET`, `THREAT` (1,5; 1,0; 0,25; 2,0; 20,0 de M11.1), `MIN_SPEED`, `MAX_SPEED` | "Cada default existe solo en `ProfileDefaults` o en el record correspondiente" |
| Detección v1 ampliada: `config.yml` sin `schemaVersion` con cualquier clave exclusiva de v1 (no solo `provider`); perfil sin `schemaVersion` con un campo v1 **o sin `Tactics`**. Un archivo sin `schemaVersion` que no es v1 es un error | Un v1 antiguo puede no tener `provider` ni `Behaviors`; un perfil sin `Tactics` no puede ser v2. Un bloque histórico `jev:` sin versión no se acepta (plan §9) |
| Un fallo global de lectura conserva los perfiles leídos por última vez y los **vuelve a vincular** con los objetos actuales de MythicMobs, en una revisión nueva | Tras `/mm reload` los objetos anteriores pueden estar obsoletos (plan §8); los perfiles del catálogo son los mismos |
| `MythicReloadedEvent` vuelve a leer `AI` y a vincular; si la lectura falla globalmente, vuelve a vincular los perfiles leídos por última vez | No hay otra forma de recargar perfiles hasta M12; hoy `/mm reload` es la vía documentada. `config.yml` se sigue leyendo solo al arrancar |
| `ProfileCatalog.profiles` contiene solo los perfiles vinculados (`Predictor: true`); sin perfiles ni errores el estado es `SETUP_PENDING`. El resumen (`ProfileCatalog.summary(int readProfiles)`, `CatalogReloader.summary()`) distingue perfiles leídos de activos y, si hay leídos pero ninguno activo, pide `Predictor: true` en lugar de la pista de primer inicio | Es lo que usarán las sesiones (M06); A7 exige explicar por qué no hay perfiles activos |
| `adapter/mythicmobs/BoundProfile` se conserva sin cambios y el vínculo nuevo es `adapter/server/binding/BoundProfile` | El runner de OpenRouter y `MythicSkillExecutor` usan el tipo antiguo (W5) hasta M09 y M10 |
| En ejecución el camino OpenRouter queda inactivo: `config.yml` v2 no tiene `provider`, así que la raíz entrega `Optional.empty()` al runner. `DecisionProviderFactory` queda sin uso en `main` hasta que M12 la borre. `/thepredict check` informa del estado local de Jev sin red | W5 exige que compile y que sus pruebas pasen, lo que se cumple; no existe forma válida de configurar OpenRouter en el esquema 2, y M03 no debe hacer llamadas (W6) |
| El loader v1 desaparece: `ProfileLoader` produce solo `TacticalProfile`. `DecisionLifecycleSuiteTest` construye sus entradas v1 como datos de prueba | "`ProfileLoader` se amplía para v2 y produce `domain.TacticalProfile`"; conservar un loader v1 solo para una prueba duplicaría código |
| El binder también aplica `ThreatTable.DecayUnreachable` (aviso y decaimiento 0) y descarta entradas vacías de `PredictValues` | M11.1 asigna esa lectura "al binder (M03)"; una skill sin `PredictValues` producía antes "Unknown PredictValues entry:" en vez de "requires at least one entry" |
| Heurística de idioma: caracteres no ASCII o una lista corta de palabras frecuentes en español, alemán, francés, italiano y portugués que no son palabras inglesas | Nunca es un error (M03.4); la lista evita falsos avisos con palabras como `die` o `per` |
| Migración: se genera al arrancar; si ya existe una propuesta idéntica en `migration/`, no se escribe otra y se informa de la existente; la propuesta de perfiles se valida con el esquema 2 y los fallos se listan para revisión manual; los IDs de choice se normalizan y una táctica de menos de 3 letras recibe el prefijo `use_`; `LowHealthRatio` pasa a `Health.Critical` si cabe bajo `Wounded` | Idempotencia también en disco; cada decisión queda en el informe (M03.6) |
| `StatusSnapshot` no cambia: la línea "Provider" muestra el estado de Jev (`UNCONFIGURED` o `INVALID_CONFIGURATION`) y "Active profiles" el resumen del catálogo; el aviso al administrador que entra solo aparece con errores | M12 rediseña el estado; M03.7 pide que falta de clave o de perfiles no sean alarmas |
| `examples/AI/ThePredictGuardian.yml` y `examples/README.txt` pasan a v2 (tácticas del plan §9) | Sin ello el ejemplo empaquetado no cargaría; el contenido final sigue siendo de M12 |
| C10: `ThePredictPlugin` pasa de 293 a 332 líneas; `MythicProfileBinder` (211) y `ProfileSectionParser` (207) rondan el límite | La raíz es heredada y M12 la reduce; los otros dos están en el margen de "unas 200" |
| `ArchitectureRulesTest` solo actualiza la descripción de los paquetes heredados (raíz y `adapter.mythicmobs`) | `adapter.server.binding` cae en la regla D5 existente y `CatalogReloader` vive en la raíz, heredada hasta M12 |

### Pendiente de verificación en servidor

- Que `CustomCondition#getCondition()` siga vacío 2 ticks después de `getConditions` para una condición inexistente, y presente para una registrada por un addon (DT de M01; hoy solo hay evidencia `javap`).
- La forma real de `MythicMob#getEntityTypeString()` para especies con `Brain` (`BrainSpecies` ignora mayúsculas, guiones bajos y `BABY`).
- Que `MythicConfig#getBoolean("ThreatTable.DecayUnreachable", false)` lee la ruta anidada del mob.
- `/mm reload` con un perfil válido, uno roto y `Predictor` cambiado: catálogo nuevo, revisión creciente y mensajes en consola.
- Primer arranque limpio (archivos creados, `SETUP_PENDING`, Jev `UNCONFIGURED`; desde M12 el administrador que entra recibe un aviso informativo porque el servicio no está `READY`, desviación 10 de M12, y la consola solo muestra información con el paso siguiente) y arranque con un `config.yml` v1 real (propuesta en `migration/<fecha-hora>/`, nada modificado, plugin inactivo).

### Pendiente para módulos posteriores

- M05: reutilizar `adapter.server.binding.BrainSpecies` en lugar de otra lista.
- M07: evaluar los contextos con `BoundProfile.conditions()` (`NativeConditions.conditions()`) y los atajos de `ContextRule.SHORTCUTS`.
- M08: construir `SystemOneSettings` desde `JevSettings` (ya incluye `connectTimeout`, `maxResponseBytes` y `maxStateChars`).
- M09 (hecho, con la desviación anotada en M09): construir `EngineSettings` desde `DecisionSettings` y `PluginSettings.timing()`; retirar la traducción a `RuntimeSettings` y el runner.
- M12: retirar `ProviderSettings`, `PredictorProfile`, `DecisionProviderFactory` y `adapter/mythicmobs/BoundProfile`, y dar regla a la raíz (incluido `CatalogReloader`). **Hecho en M12** (ver su [estado de implementación](M12-Diagnostico-instalacion-retiro-OpenRouter.md#estado-de-implementación)).

### Correcciones tras la auditoría (2026-09-29)

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante (W6, C8):** `jev.apiKeyEnvironment` aceptaba cualquier texto y se mostraba en consola, en `/thepredict status` y `check` y en `JevSettings.toString()`; una clave pegada ahí se filtraría | `JevSettings` valida `jev.apiKeyEnvironment` con `JevSettings.API_KEY_ENVIRONMENT` (vacío o `[A-Za-z_][A-Za-z0-9_]{0,127}`) y `jev.model` con `JevSettings.MODEL` (`[A-Za-z0-9][A-Za-z0-9._-]{0,63}`, como `jev-1.13.0`); el error nombra la ruta y nunca el valor. Además, los mensajes solo muestran el nombre de variable por defecto (`apiKeyEnvironmentLabel()` da `<custom variable>` para cualquier otro), y `toString()` muestra de `baseUrl` solo esquema y autoridad | `JevSettingsTest.apiKeyEnvironmentMustBeAVariableNameAndIsNeverEchoed`, `modelMustBeAModelIdAndIsNeverEchoed`, `aCustomVariableNameIsNotShownInMessages`; `PluginConfigurationLoaderTest.aKeyPastedIntoTextFieldsIsRejectedWithoutBeingEchoed` |
| Otros ecos de valores de `config.yml` | Revisados todos los mensajes: `schemaVersion` (en `config.yml` y en perfiles) y los valores v1 que el informe de migración cita (`decision.*`, `profileDefaults.*`, `LowHealthRatio`) pasan por `ConfigurationValues.safeValue`, que muestra números y booleanos y describe el texto como `a text value`. Los errores de rango solo muestran números. `provider.apiKey` y `provider.model` nunca se citan. Los mensajes de perfiles sí citan nombres del autor (`unknown choice 'mele'`), como exige el formato de M03.2; los perfiles no contienen credenciales. `ProviderSettings` (heredado) ya no se lee en ejecución | `MigrationPlannerTest.textValuesOfSchemaOneFieldsAreNotEchoedInTheReport`; `PluginConfigurationLoaderTest.schemaVersionIsRequiredAndMustBeTwo` |
| Un `RuntimeException` inesperado en la recarga tras `MythicReloadedEvent` dejaba vínculos a objetos obsoletos | `CatalogReloader.reload()` y `verifyConditions()` capturan el error y cambian a un catálogo vacío de revisión nueva con el error `AI: the reload failed unexpectedly (<tipo>); no profile is active until the next successful reload` (plan §8: permanecer en fallback). La raíz registra el catálogo una vez por recarga | `CatalogReloaderTest.anUnexpectedErrorEmptiesTheCatalogInsteadOfKeepingStaleBindings` |
| Con perfiles válidos pero ningún mob con `Predictor: true`, el estado sugería copiar `AI/example.yml.example` (A7) | El resumen cuenta perfiles leídos y activos y, en ese caso, pide `Predictor: true` y recargar MythicMobs | `ProfileCatalogTest.readButInactiveProfilesAreReportedInsteadOfTheSetupHint`, `CatalogReloaderTest.readProfilesWithoutAnEnabledMobAreReportedAsInactive` |
| `config.yml` fijaba `maxDecisionAgeMillis: 1000` y anulaba el default derivado | La clave se quita del `config.yml` empaquetado (queda un comentario) y de la propuesta de migración; el loader deriva 1000 con el timeout por defecto | `PluginConfigurationLoaderTest.bundledConfigurationDeclaresEveryKeyWithTheStandardValues` |
| Casilla de M03.3 mezclaba lo local con lo de servidor | Dividida en parte local (marcada) y parte de servidor (sin marcar) | — |
| Recuento de líneas de `ThePredictPlugin` | Corregido: 332 líneas tras las correcciones finales | — |
| `examples/README.txt` no decía que el motor táctico aún no actúa | Nota explícita: esta build carga, valida y vincula el ejemplo pero no decide ni envía solicitudes a Jev | — |
| Final (W6): un valor con forma de clave que cumple el patrón de modelo (`sk-SECRETVALUE_123`) se mostraba en `toString()` y en el estado | `JevSettings.modelLabel()` muestra el modelo solo si es el default o cumple `(jev\|kev)-[0-9][0-9.]*`; si no, `<custom model>`. Lo usan `toString()`, el arranque, `/thepredict status` y `check`. El valor real se conserva para enviarlo al servicio (M08) | `JevSettingsTest.aKeyThatFitsTheModelPatternIsKeptButNeverDisplayed` |
| Final: `CatalogReloader.failed()` perdía la traza del error inesperado | Nuevo constructor con `Consumer<RuntimeException>`: el recargador entrega cada excepción una vez y la raíz la registra en `SEVERE` con su traza mediante el logger del plugin; el mensaje fijo no incluye valores de configuración | `CatalogReloaderTest.anUnexpectedErrorEmptiesTheCatalogInsteadOfKeepingStaleBindings` (comprueba una entrega por fallo) |
| Final: notas de discrepancia con M07.7 y M12.6 en "Pendiente para módulos posteriores" | Eliminadas: M07.7 ya usa `ContextRule.SHORTCUTS` con los nombres `*_phase` y M12.6 ya usa `migration/<fecha-hora>/config.yml.proposed`, `AI/<archivo>.yml.proposed` y `report.md`; se conservan los demás pendientes | — |
