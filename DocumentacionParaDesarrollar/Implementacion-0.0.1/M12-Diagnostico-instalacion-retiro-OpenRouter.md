# M12. Diagnóstico, instalación, ejemplo y retiro de OpenRouter

> **Estado:** Local listo — servidor pendiente (ver [Estado de implementación](#estado-de-implementación)) · **Entrega:** E · **Depende de:** M03 a M11 · **Desbloquea:** M13 · **Diseño:** [plan §9](../Plan-Jev-y-combate-tactico.md#9-configuración-ejemplo-y-migración), [plan §10](../Plan-Jev-y-combate-tactico.md#10-refactor-y-reglas-de-implementación) y [plan §11](../Plan-Jev-y-combate-tactico.md#11-plan-por-módulos-de-la-001) · Reglas: [00](00-Arquitectura-y-reglas.md) · Dominio: [01](01-Modelo-de-dominio.md)

## Objetivo

Que un administrador instale, configure, migre y diagnostique ThePredict sin leer Java, y que el código y la documentación activos dejen de mencionar OpenRouter. El módulo reúne la cara visible de la 0.0.1: comandos, mensajes, ejemplo completo, migración y vocabulario del editor.

## Alcance

### Incluye

- `/thepredict status`, `/thepredict check` e `/thepredict inspect` ampliados.
- Decisión sobre los comandos de depuración de M01.
- Primer inicio y mensajes por estado del servicio, del catálogo y de la migración.
- Boss de ejemplo v2 completo e inactivo por defecto.
- Experiencia de migración desde v1 sobre el migrador de M03.
- Instrucciones para añadir el vocabulario de ThePredict a MythicScript 2077.
- Retiro de OpenRouter del código, las pruebas, los recursos y la documentación activa.

### No incluye

- La lógica de migración y validación, que es de M03.
- Métricas nuevas: M12 solo muestra las que produce M09.
- Guías completas de autor y administrador, que son de M14.
- Cambios en la extensión MythicScript 2077: M12 documenta los pasos; aplicarlos es decisión del usuario.

## Base existente

| Archivo | Acción |
|---|---|
| `ThePredictStatusCommand.java` | Se sustituye por `admin/ThePredictCommand.java` con subcomandos y autocompletado. |
| `StatusSnapshot.java` | Se sustituye por los informes inmutables que produce `engine` (01, revisión 2) y el formateo de `admin`. |
| `ThePredictPlugin.java` | Conserva el aviso a administradores al entrar (`onAdministratorJoin`) y lo amplía; retira los textos de OpenRouter. |
| `DecisionProviderFactory.java` | Se elimina. |
| `adapter/openrouter/OpenRouterDecisionProvider.java` | Se elimina tras M08. |
| `config/ProviderSettings.java` | Sustituido por `config.JevSettings` (M03); se elimina. |
| `decision/ProviderHealth.java`, `ProviderFailure.java` | Sustituidos por `domain.ServiceCheck`, `ServiceState` y `FailureKind` (M02); se eliminan. |
| `src/main/resources/plugin.yml` | Descripción, uso del comando y permiso `thepredict.debug`. |
| `src/main/resources/config.yml`, `AI/README.txt`, `AI/example.yml.example`, `examples/**` | Se reescriben a v2. |
| `config/FirstRunFiles.java` | Lista de recursos v2. |
| `build.gradle.kts` (`verifyPluginJar`) | Recursos obligatorios v2 y comprobación de ausencia de OpenRouter. |
| Pruebas `ThePredictStatusCommandTest`, `PluginResourcesTest`, `ConfigurationRecoverySuiteTest`, `ProviderSettingsTest`, `ProviderHealthMonitorTest`, `ProviderFailureTest`, `adapter/openrouter/*Test` | Se adaptan o eliminan según M12.8. |

## Subpuntos

### M12.1 `/thepredict status` ampliado

- **Propósito:** una vista global del estado de la 0.0.1 en una sola pantalla.
- **Diseño:**
  - Secciones, en este orden y con una línea por dato:
    1. **Entorno:** Java, versión de Minecraft según Paper, versión de MythicMobs.
    2. **Servicio:** `ServiceState`, detalle, modelo configurado y modelo efectivo de la última respuesta, fin de la pausa si `PAUSED`.
    3. **Catálogo:** estado (`SETUP_PENDING`, cargado o inválido con el último válido conservado), `catalogRevision`, perfiles activos y mobs vinculados.
    4. **Sesiones:** cuántas hay por `ControlLevel` (`JEV`, `ALTERNATIVE`, `LOCAL`, `BASE`) y por `SessionState`.
    5. **Encuentros:** activos, con solicitud en vuelo y con contexto pendiente.
    6. **Presupuesto:** solicitudes en la ventana y límite, solicitudes en vuelo y límite, por servidor y por el mundo del emisor si es jugador.
    7. **Consumo:** tokens de entrada por minuto y costo estimado por hora: `tokens por minuto × 60 × 0.042 / 1 000 000` USD (plan §6). Se muestra como estimación.
    8. **Latencia:** p50, p95 y p99 por tramo de M09 (captura, HTTP, espera en inbox, aplicación).
    9. **Errores:** los tres últimos `FailureKind` y `FallbackReason` con su antigüedad.
  - Los datos llegan de un informe inmutable que construye `engine` en el hilo del servidor. **Incorporado en 01 §8 (revisión 2):** `StatusReport`, record de solo lectura con los contadores anteriores.
  - El formato vive en `admin.StatusFormatter`, con Adventure para colores (D6). Ningún dato incluye claves, cabeceras ni nombres de jugadores (C8, I6).
- **Archivos:** crear `admin/ThePredictCommand.java` y `admin/StatusFormatter.java`; eliminar `ThePredictStatusCommand.java` y `StatusSnapshot.java`.
- **Reglas:** A7, C8, D6, T1.
- **Pruebas:** `StatusFormatterTest` con un `StatusReport` fijo: aparecen todas las secciones, el costo se calcula bien y no aparecen secretos. `ThePredictCommandTest` sustituye a `ThePredictStatusCommandTest` con el mismo estilo de proxy de `CommandSender`.
- **Aceptación:**
  - [x] Las nueve secciones aparecen en consola y para un jugador con `thepredict.admin`. Local: `ThePredictCommandTest` (consola y jugador, este con el presupuesto de su mundo) y `StatusFormatterTest` (orden de las secciones); la salida en un servidor real es el paso 1.5 del guion.
  - [x] El costo por hora coincide con el cálculo manual. `StatusFormatterTest.theCostPerHourIsTokensPerMinuteTimesSixtyTimesThePricePerMillion`.
  - [x] Ninguna salida contiene la clave ni un nombre de jugador. `StatusFormatterTest`, `InspectFormatterTest` (solo alias y UUID de mob abreviada) y `ConfigurationRecoverySuiteTest` (la clave no aparece en `JevSettings.toString`).

### M12.2 `/thepredict check` e `/thepredict inspect`

- **Propósito:** comprobar el servicio bajo demanda y explicar qué hace un mob concreto.
- **Diseño:**
  - `check`: llama a `DecisionClient.check()` sin bloquear (T4). Informa en el hilo del servidor cuando termina. Recuerda que `/v1/models` no verifica una inferencia ni la calidad (plan §6). Si el modelo es un ID versionado ausente de la lista, muestra "credencial válida; modelo sin verificar hasta la primera inferencia".
  - `inspect nearest`: solo para jugadores; busca el mob vinculado más cercano a 16 bloques con la API de Bukkit (permitido por D6) y consulta el engine por su UUID. `inspect <uuid>`: consola o jugador.
  - Datos mostrados: UUID abreviado y generación (I1), tipo de MythicMobs, perfil, `SessionState`, `ControlLevel`, táctica vigente con movimiento, confianza, ticks restantes y si su skill se consumió, alternativas, contingencias, metas instaladas y meta de movimiento en curso, estado de la tabla de amenaza y amenaza añadida por ThePredict (M11), último `FallbackReason` y último `ExecutionOutcome`.
  - **Incorporado en 01 §8 (revisión 2):** `SessionReport`, record de solo lectura con esos datos; y añadir a `MobSession` el último `ExecutionOutcome`.
  - **Incorporado en 00 §4 (revisión 2):** `MobControl.describeGoals(MobKey)` que devuelva nombres legibles de metas instaladas y en curso, porque `admin` no puede usar `MobGoals` directamente (D6 no permite `com.destroystokyo.paper`).
  - Autocompletado: subcomandos y `nearest`.
- **Archivos:** `admin/ThePredictCommand.java`, `admin/InspectFormatter.java`.
- **Reglas:** D6, D7, T4, I1, I6.
- **Pruebas:** `InspectFormatterTest` con un `SessionReport` fijo; prueba de que `check` no llama a `join()` (future incompleta en el doble de `DecisionClient`).
- **Aceptación:**
  - [x] `check` responde sin congelar el servidor aunque el servicio tarde. Local: `DecisionServicesTest.aCheckFromTheCommandNeverWaitsForAnIncompleteFutureAndReportsOnceItCompletes` (40 ticks con la future incompleta, el aviso llega en el tick en que se completa).
  - [x] `inspect` explica por qué un mob está en `BASE` o `LOCAL` con su `FallbackReason`. Línea `Why` con el último `FallbackReason`, el último `ExecutionOutcome` y las rachas, y línea `Perception` con contextos y tácticas elegibles (`InspectFormatterTest`, `DiagnosticsTest`).
  - [x] `inspect` de un mob no vinculado responde "sin sesión" sin error. `ThePredictCommandTest.inspectOfAMobWithoutSessionAnswersWithoutErrorAndNearestNeedsAPlayer`.

### M12.3 Comandos de depuración

- **Propósito:** conservar la herramienta de M01 para autores, sin riesgo para servidores en producción.
- **Diseño:**
  - Decisión: se conservan en la 0.0.1 bajo el permiso `thepredict.debug`, con `default: false` en `plugin.yml`.
  - Subcomandos: `debug intent <nearest|uuid> <tacticId>` fija una intención local con `ControlLevel.LOCAL` y vigencia de `maximumIntentTicks`; `debug release <nearest|uuid>` devuelve el mob a `BASE`; `debug goals <nearest|uuid>` lista metas instaladas y en curso.
  - Nunca consumen API. `inspect` marca la intención como "debug" para no confundirla con una decisión real.
  - Una táctica no elegible se rechaza con el motivo de `Eligibility`.
- **Archivos:** `admin/ThePredictCommand.java` (subcomandos); `plugin.yml` (permiso).
- **Reglas:** A6, A7, W3.
- **Pruebas:** permisos denegados sin `thepredict.debug`; intención de depuración que vence y vuelve a `BASE`.
- **Aceptación:**
  - [x] Sin el permiso, los subcomandos `debug` no aparecen ni se ejecutan. `ThePredictCommandTest.withoutThePermissionDebugIsNeitherListedNorRun`; en servidor, pasos 2.1 y 2.5 del guion (DT-17).
  - [x] Una intención de depuración nunca genera solicitudes a Jev. `DebugIntentsTest.theCoordinatorLeavesAHeldMobAloneAndNeverAsksJevAboutIt` (servicio `READY`, 60 ticks, ninguna solicitud).

### M12.4 Primer inicio y mensajes

- **Propósito:** que una instalación nueva no parezca un error y que un fallo real sea evidente.
- **Diseño:**
  - Estados visibles y su mensaje de consola en el arranque:

    | Estado | Condición | Mensaje |
    |---|---|---|
    | `UNCONFIGURED` | Falta la clave de Jev | "Jev no configurado: define TYPESAFE_API_KEY o jev.apiKey. MythicMobs funciona normalmente." |
    | `INVALID_CONFIGURATION` | `config.yml` inválido | Archivo, ruta y motivo; el comando `status` sigue disponible |
    | Catálogo `SETUP_PENDING` | Carpeta `AI` sin perfiles | "Sin perfiles: copia examples/ o crea un .yml en AI/" |
    | `CHECKING` → `READY` | Comprobación al arrancar | Resultado de `check` |
    | `INVALID_CREDENTIALS`, `INVALID_MODEL` | Resultado de `check` | Causa y paso siguiente |
    | Migración pendiente | Configuración v1 detectada | Ruta de la propuesta y del informe (M12.6) |

  - El estado del catálogo lo expone M03 con `config.CatalogStatus` (`SETUP_PENDING`, `LOADED` e `INVALID`.
  - Aviso al entrar para jugadores con `thepredict.admin` si el servicio no está `READY`, el catálogo es inválido o hay una migración pendiente. Se amplía el `onAdministratorJoin` actual.
  - Primer inicio: `FirstRunFiles` crea solo archivos ausentes (plan §9): `config.yml` v2 con clave vacía y modelo probado, `AI/README.txt`, `AI/example.yml.example` y `examples/**` v2. No lanza inferencias de prueba.
- **Archivos:** `ThePredictPlugin.java`, `config/FirstRunFiles.java`, `src/main/resources/config.yml`, `src/main/resources/AI/README.txt`, `src/main/resources/AI/example.yml.example`.
- **Reglas:** A6, A7, C8, C13.
- **Pruebas:** `FirstRunFilesTest` actualizado a la lista v2; segundo inicio no sobrescribe; `ConfigurationRecoverySuiteTest` cubre YAML roto y clave ausente sin OpenRouter.
- **Aceptación:**
  - [ ] Una instalación limpia arranca sin mensajes de error y con `status` explicativo. Local: `SetupMessagesTest.aCleanInstallationOnlyInformsAndGivesTheNextStep` y `StartupStateTest`; el arranque real es el paso 1.5 del guion (pendiente de servidor).
  - [x] Cada estado de la tabla tiene un mensaje distinto y un paso siguiente. `SetupMessagesTest`.

### M12.5 Boss de ejemplo v2

- **Propósito:** un ejemplo que funcione al copiarlo y que muestre todas las capacidades de la 0.0.1.
- **Diseño:**
  - `examples/MythicMobs/Mobs/ThePredictGuardian.yml`: zombie con `Predictor: false` por defecto, `Options.FollowRange: 32`, `Options.AsyncPathfinding` solo si M01 lo valida, `Modules.ThreatTable` activo y sin `AIGoalSelectors` gobernadas (las genera ThePredict, plan §3). Conserva un ataque base simple para comprobar la cesión de control.
  - `examples/MythicMobs/Skills/ThePredictGuardianSkills.yml`: `ThePredictGuardianMelee`, `ThePredictGuardianRanged` y `ThePredictGuardianDefense` con `PredictValues` y `Cooldown` nativo; la defensa no cura con salud completa y el ataque a distancia comprueba línea de visión (plan §9).
  - `examples/AI/ThePredictGuardian.yml`: perfil v2 del plan §9 con tácticas `approach`, `press_and_strike`, `keep_distance`, `keep_distance_and_shoot`, `retreat_and_defend`, `flank_and_strike` y `hold`; secciones `Movement`, `Threat` y `BaseAI`. El esquema exacto lo fija M03.
  - `examples/README.txt`: pasos de copia, activación de Predictor, `/mm reload`, `/thepredict status` y qué observar con `inspect`.
  - El mismo contenido funciona con Predictor apagado como un zombie de MythicMobs.
- **Archivos:** los cuatro recursos anteriores; `config/FirstRunFiles.java`; `build.gradle.kts` (`verifyPluginJar`, lista de recursos obligatorios).
- **Reglas:** I4 (IDs de táctica en inglés), W3.
- **Pruebas:** prueba de recursos que carga el perfil de ejemplo con el loader de M03 y lo valida contra dobles de MythicMobs (como hoy `MythicProfileBinderSuiteTest`); escenario de servidor en M13.
- **Aceptación:**
  - [x] El perfil de ejemplo pasa la validación de M03 sin avisos. `MythicProfileBinderSuiteTest.bundledExampleBindsTheRealPremiumApiTypesAcrossToggleChanges` (loader de M03 y binder con dobles, sin problemas con `Predictor` apagado y encendido).
  - [ ] Con Predictor apagado el mob se comporta como un zombie de MythicMobs. Pendiente de servidor (M13; `examples/README.txt`, paso 5).
  - [ ] Con Predictor activo se observan todos los movimientos en `inspect`. Pendiente de servidor (guion, sección 9).

### M12.6 Migración desde v1

- **Propósito:** llevar instalaciones con `provider:` de OpenRouter a v2 sin perder datos ni activar consultas.
- **Diseño:**
  - Al arrancar, si el migrador de M03 detecta esquema v1, escribe `plugins/ThePredict/migration/<fecha-hora>/config.yml.proposed`, `AI/<archivo>.yml.proposed` y `report.md` (M03.6). Nunca reemplaza archivos activos.
  - El informe lista: claves trasladadas, claves descartadas (`provider.type`, `provider.model` de OpenRouter), claves nuevas con su default, perfiles convertidos a tácticas sin movimiento y combinaciones ambiguas que requieren edición.
  - La clave de OpenRouter nunca se copia (plan §9); el informe indica configurar `TYPESAFE_API_KEY`.
  - `status` y el aviso al entrar muestran "migración pendiente" mientras exista configuración v1 activa.
  - La propuesta se regenera en cada arranque mientras siga habiendo v1; no hay comando propio en la 0.0.1.
- **Archivos:** raíz (invocación del migrador en el arranque) y `admin` (mensajes); la lógica está en `config` (M03).
- **Reglas:** A6, C8, W3.
- **Pruebas:** con un directorio temporal: v1 genera propuesta e informe; los archivos activos quedan intactos; migración repetida idéntica; la clave antigua nunca aparece en la salida.
- **Aceptación:**
  - [x] Ninguna migración activa consultas ni modifica archivos activos. `StartupStateTest.schemaOneWritesAProposalAndAReportChangesNothingAndNeverCopiesTheKey` y `MigrationPlannerTest`.
  - [x] El informe basta para completar la migración a mano. El informe lista archivos propuestos, decisiones y revisión manual, y los pasos para aplicarlo (`migration/report-template.md`, `MigrationPlannerTest`).

### M12.7 Vocabulario en MythicScript 2077

- **Propósito:** que el editor no marque como inventados los nombres de ThePredict.
- **Diseño:** la extensión (`~/.vscode/extensions/local.mythicscript-2077-1.0.0`) acepta vocabulario extra por dos vías, según su README:
  1. **Solo colores:** listas planas en `vocab/*.json`, que `tools/regen.py` lee en cada regeneración.

     | Archivo | Entradas |
     |---|---|
     | `vocab/conditions.json` | `predictorcontrol`, `predictormove`, `predictortactic` |
     | `vocab/mechanics.json` | `predictordecide` |
     | `vocab/keys-leaf.json` | `Predictor`, `PredictValues` |

     Si un autor escribe `PredictValues` como bloque de lista, se añade también a `vocab/keys-block.json`.
  2. **Colores y autocompletado:** un archivo de dataset por tipo declarado en `MythicScribe.customDatasets` de `settings.json`, con `source: "File"`, en el formato `[{"name": ["predictormove"], "description": "…", "attributes": []}]`. El formato de `attributes` (`m`, `t`) se comprueba contra MythicScribe antes de documentarlo; si no se confirma, se deja la lista vacía.
  - Los placeholders `<caster.predictor.*>` no necesitan entrada: la gramática los reconoce por el punto. Sus nombres finales los confirma M01.
  - Después de cualquiera de las vías: `python3 tools/regen.py` y recargar la ventana de VS Code.
- **Archivos:** ninguno en este repositorio salvo la sección correspondiente de la guía del autor (M14). La extensión no se modifica desde este módulo.
- **Reglas:** I8, W1.
- **Pruebas:** manual: un archivo de ejemplo con los cuatro nombres no muestra marcas de "inventado" tras regenerar.
- **Aceptación:**
  - [x] Los pasos están en la guía del autor con las entradas exactas. [Guía del autor, §13](../../Guias/Guia-del-autor.md#13-vocabulario-para-mythicscript-2077) (M14), con el texto del [estado de implementación](#texto-para-la-guía-del-autor-m127); la extensión no se modificó.
  - [x] Los nombres coinciden con los registrados en M04. `VocabularyRegistrarTest` fija `predictorcontrol`, `predictormove`, `predictortactic` y `predictordecide`, los mismos del texto.

### M12.8 Retiro de OpenRouter

- **Propósito:** que la 0.0.1 publicada no contenga código, configuración ni instrucciones activas de OpenRouter.
- **Diseño:** lista de comprobación, ejecutada cuando M08 y M09 ya sustituyen el camino actual (W5):
  - Código: eliminar `adapter/openrouter/OpenRouterDecisionProvider.java`, `DecisionProviderFactory.java`, `config/ProviderSettings.java`, `decision/ProviderHealth.java`, `decision/ProviderFailure.java` y `decision/ProviderHealthMonitor.java`; y lo que M09 conservó sin uso (desviación 21 de M09): `decision/PredictionPlanner.java`, `decision/DecisionPlan.java`, `decision/DecisionRequest.java`, `decision/DecisionProvider.java` (el paquete `decision` desaparece), `snapshot/CombatSnapshot.java` y `snapshot/VitalSnapshot.java` (el paquete `snapshot` desaparece) y `config/RuntimeSettings.java`; `StatusSnapshot` deja de usar `ProviderHealth` y muestra `ServiceStatusTracker.status()`; retirar de `ThePredictPlugin.java` los textos "OpenRouter is not configured yet", "Checking OpenRouter without spending tokens" y "OpenRouter status".
  - Pruebas: eliminar `adapter/openrouter/OpenRouterDecisionProviderTest.java`, `DecisionLifecycleSuiteTest.java`, `config/ProviderSettingsTest.java`, `decision/ProviderHealthMonitorTest.java`, `decision/ProviderFailureTest.java`, `decision/PredictionPlannerTest.java`, `decision/DecisionRequestTest.java` y `config/RuntimeSettingsTest.java`, y quitar de `ArchitectureRulesTest` las entradas `decision` y `snapshot` de `LEGACY_PACKAGES`, o reescribirlas contra `adapter.jev` y `engine` si su escenario sigue vigente; actualizar `PluginResourcesTest` (hoy exige `type: OPENROUTER`), `ThePredictStatusCommandTest` y `ConfigurationRecoverySuiteTest`.
  - Recursos: `config.yml` sin `provider:`; `AI/README.txt` y `examples/README.txt` sin `OPENROUTER_API_KEY`; `plugin.yml` con descripción del comando sin "OpenRouter".
  - Build: `verifyPluginJar` añade una comprobación de que ninguna entrada del JAR contiene `adapter/openrouter/` y de que `config.yml` contiene `schemaVersion: 2`; conserva la comprobación de `apiKey: ''` y de bytecode Java 25.
  - Documentación: `README.md` describe solo Jev; `Verificacion-servidor-0.0.1.md` y `Concurrencia-y-costos-0.0.1.md` pasan a `archive/` y los sustituye el guion de M13; `Plan-iterativo-0.0.1.md` y `ThePredict-0.0.1-Requisitos.md` conservan una nota de historia.
  - Puerta final: `grep -ri openrouter src README.md` solo devuelve el reconocimiento de claves v1 dentro del migrador de `config`.
- **Archivos:** los listados.
- **Reglas:** W3, W5, C14.
- **Pruebas:** `./gradlew clean build javadoc` verde con `verifyPluginJar` ampliado; la puerta de `grep`.
- **Aceptación:**
  - [x] El JAR no contiene clases ni textos de OpenRouter. `verifyPluginJar` rechaza los paquetes retirados y cualquier recurso de texto que lo nombre salvo `migration/report-template.md`; la única clase que conserva el nombre es el migrador `ConfigurationMigration` (reconocimiento de claves v1), como permite la casilla siguiente.
  - [x] Ninguna guía activa pide una clave de OpenRouter. `README.md`, `AI/README.txt`, `examples/README.txt` y `plugin.yml` reescritos; `Verificacion-servidor-0.0.1.md` y `Concurrencia-y-costos-0.0.1.md` en `archive/`.
  - [x] La única mención restante está en el migrador y en `archive/`. `ArchitectureRulesTest.onlyTheSchemaOneMigratorStillNamesTheRetiredProvider` sobre `src/main` y `README.md`; la puerta `grep -ri openrouter src README.md` devuelve solo `ConfigurationMigration`, `report-template.md` y sus pruebas (`MigrationPlannerTest`, `PluginConfigurationLoaderTest`).

## Riesgos y decisiones abiertas

- **Incorporado en 01 §8 (revisión 2):** `StatusReport` y `SessionReport` como informes inmutables del engine para `admin`, y el último `ExecutionOutcome` en `MobSession`.
- **Incorporado en 00 §4 (revisión 2):** `MobControl.describeGoals(MobKey)` para diagnóstico.
- **Incorporado en M03 (revisión 2):** `config.CatalogStatus`.
- El costo mostrado es una estimación con el precio público de Jev 1.13; si TypeSafe lo cambia, se actualiza en un solo lugar (C13).
- `thepredict.debug` con `default: false` puede sorprender a administradores en pruebas; la guía lo explica.
- **Pendiente de M09 (hecho en M12):** sacar de `ThePredictPlugin` el resumen del servicio (`jevHealth`, `providerState`) y la respuesta de `/thepredict check` (`reportJevStatus`) hacia `admin` junto con `StatusSnapshot`; la raíz creció a 428 líneas en M09 (desviación 24 de M09). Hoy `status` lee `ServiceStatusTracker.status()` a través de `engine.Diagnostics`, `check` usa `ResultIntake.awaitCheck` y la raíz tiene 204 líneas.
- **Pendiente de M09 (W3, hecho en M12):** el `README.md` de la raíz del proyecto aún describe OpenRouter y no el camino de Jev con coordinador, presupuesto y pausas; M12 lo reescribe y M14 lo revisa antes de publicar. Reescrito: describe solo Jev y marca lo pendiente de servidor.

## Terminado

- Comandos, mensajes, ejemplo y migración con pruebas verdes.
- Puerta de retiro de OpenRouter superada.
- `./gradlew clean build javadoc` verde con el `verifyPluginJar` ampliado.
- Regla W4 de 00 cumplida.

## Estado de implementación

> **Local listo — servidor pendiente (2026-09-29).** Todo lo que se puede hacer sin servidor está hecho y probado: `./gradlew clean build javadoc --offline` termina en `BUILD SUCCESSFUL` sin avisos de javac ni de Javadoc, con 1128 pruebas en 165 clases, todas verdes tras las correcciones de la auditoría (1143 antes de M12; se retiraron las suites del camino anterior y se añadieron las de M12). Ninguna llamada de red: ni a TypeSafe ni a otro servicio. No se leyó `.env`. Las casillas que exigen juego quedan sin marcar y no hay evidencia de servidor inventada.

### Implementado

| Subpunto | Archivos (`src/main/java/akyrion/thepredict/…`) |
|---|---|
| M12.1 `status` | `admin/ThePredictCommand`, `admin/StatusFormatter`, `admin/AdminText`, `admin/SetupReport` (entorno, catálogo, configuración y migración, que construye la raíz), `admin/CommandServices`; `engine/StatusReport`, `engine/Diagnostics`, `engine/StatusAssembler`; accesores nuevos: `RequestBudget.requestsLastMinute(UUID, long)` e `inFlight(UUID)`, `ServiceStatusTracker.pauseRemainingNanos`, `ServicePause.remainingNanos`, `EncounterRegistry.contextPending`, `DecisionMetrics.recentFailures`/`recentFallbacks`, `ResultIntake.lastModel` |
| M12.2 `check` e `inspect` | `ResultIntake.awaitCheck` (hasta 8 esperas; aviso en el hilo del servidor cuando el servicio sale de `CHECKING`), `admin/InspectFormatter`, `engine/SessionReport`, `domain/ThreatTableState`, `CombatReader.threatTable` (implementado en `adapter/server/context/CombatCapture` y `MythicMemberSource`), `Encounter.lastFacts` (guardado por `EncounterCapture`), `ThreatWindows.pointsFor`; `inspect` usa `MobControl.describeGoals` y `progress` |
| M12.3 depuración | `admin/DebugCommand`, `admin/DebugCommandParser`, `admin/DebugRequest`, `admin/MobSelector`; `engine/DebugIntents` (expuesto por `TacticalExecution.debugIntents()`), marca `MobSession.debugIntent`, `IntentControl.applyDebug`/`endDebug`/`release`, `ReactiveLayer` (sesión retenida), `DecisionDispatcher` (una sesión retenida no genera preguntas); `plugin.yml` (`thepredict.debug`, `default: false`) |
| M12.4 primer inicio | Raíz: `StartupState` (archivos de primer inicio, propuesta de migración, lectura estricta de `config.yml`), `ThePredictPlugin.announce` y `onAdministratorJoin`; `admin/SetupMessages` (textos por estado con paso siguiente y aviso al entrar); `AI/README.txt` |
| M12.5 ejemplo | `examples/MythicMobs/Mobs/ThePredictGuardian.yml` (`Predictor: false`, `FollowRange: 32`, `Modules.ThreatTable`, sin `AIGoalSelectors`, ataque base), `examples/MythicMobs/Skills/ThePredictGuardianSkills.yml` (`PredictValues`, `Cooldown` nativo, preparación visible, línea de visión y alcance en la de distancia, sin curar con salud completa), `examples/AI/ThePredictGuardian.yml` (siete tácticas del plan §9), `examples/README.txt` |
| M12.6 migración | Raíz: `StartupState` invoca el migrador de M03 en cada arranque mientras haya esquema 1; `status`, la consola y el aviso al entrar muestran la ruta del informe; sin comando propio |
| M12.7 vocabulario | Texto para la guía del autor más abajo; la extensión no se modifica |
| M12.8 retiro | Eliminados (30 archivos de `main`): `DecisionProviderFactory`, `StatusSnapshot`, `ThePredictStatusCommand`, `DebugCommandHandler`, `DebugProfileGoals`, `DebugCapture`, `adapter/openrouter/OpenRouterDecisionProvider`, `config/ProviderSettings`, `config/RuntimeSettings`, `config/PredictorProfile`, `decision/*` (7), `snapshot/*` (2), `adapter/server/binding/PredictorMobLocator`, `adapter/server/events/DebugAutoInstaller`, `adapter/server/execution/TriggeredSkillCast`, `adapter/server/goals/DebugGoalPreset`, `DebugGoalPresets`, `DebugPresetInstaller`, `InstallMeasurement`, `adapter/server/vocabulary/ConditionTrace`, `PredictorProbeCondition`, `PredictorProbeMechanic`, `ProbeLabels`. Código muerto tras el retiro, también eliminado: `TacticId.debug(Movement)`, `PredictValue.description()`, `CombatSummaryReader.damageTotals`/`classify`/`DamageKind`, `GoalInspector.describe`/`runningMoveGoals`/`formatGoal`, `GoalOwnership.install(preset)` (y `reapplyMythicGoals` pasa a privado), `GoalSetInstaller.installPreset`, el marcador de depuración de `OwnerMarkerGoal` (`DEBUG_REVISION`), `SessionServices.currentSession`/`sessionKey`/`unbound`/`debugCapture`/`summary` y `DecisionServices.usage`. `build.gradle.kts` (`verifyPluginJar`: sin paquetes `adapter/openrouter`, `decision` ni `snapshot`; ningún recurso de texto nombra el proveedor retirado salvo el informe de migración; `schemaVersion: 2`, `apiKey: ''` y Java 25 se conservan) |
| Raíz (D8) | `ThePredictPlugin` pasa de 459 a 204 líneas y solo ordena el arranque, el apagado (T7) y el aviso al entrar; `StartupState`, `ProfileCatalogs` (carga, recarga, verificación diferida y registro acotado del catálogo), `ServerComponents` (construcción de los adapters), `EngineCommands` (adaptador de `CommandServices`), `NoCombatActions` y `NoMobControl`; `CatalogReloader`, `JavaCompatibility` y `RuntimeCompatibility` pasan a ser de paquete. `jevHealth`, `providerState` y `reportJevStatus` desaparecen: `status` lee `ServiceStatusTracker.status()` y `check` usa `awaitCheck` |
| Arquitectura | `ArchitectureRulesTest`: sin `LEGACY_PACKAGES`; regla propia de la raíz (D8: puede usar todos los paquetes, `java.*`, `org.bukkit`, `io.papermc`, `com.destroystokyo.paper` e `io.lumine.mythic`; nadie la usa); solo `ThePredictPlugin` es pública en la raíz; ningún fuente nombra lo retirado en M12; solo el migrador nombra el proveedor retirado en `src/main` y `README.md` |
| Documentación | `README.md` de la raíz reescrito (solo Jev; W3); `Verificacion-servidor-0.0.1.md` y `Concurrencia-y-costos-0.0.1.md` pasan a `archive/` con nota; nota de historia en `Plan-iterativo-0.0.1.md` y `ThePredict-0.0.1-Requisitos.md`; [guion de M01](M01-Guion-de-servidor.md) y [registro](M01-Registro-de-decisiones.md) actualizados a los comandos que quedan; 00 (revisión 8) y 01 (revisión 8) |

### Comandos finales

| Comando | Permiso | Muestra |
|---|---|---|
| `/thepredict` o `/thepredict status` | `thepredict.admin` | `Environment` (Java, Minecraft, MythicMobs); `Service` (estado y causa, prueba, modelo configurado y de la última respuesta, fin de la pausa, problema de `config.yml`); `Catalog` (resumen con estado y revisión, migración pendiente); `Sessions` (activas y suspendidas, por nivel, intenciones de depuración; abiertas, cerradas por causa, liberaciones); `Encounters` (activos, con solicitud en vuelo, con contexto pendiente, abiertos, cerrados y duración media); `Budget` (servidor y, para un jugador, su mundo); `Consumption` (tokens de 60 s y costo estimado por hora); `Latency` (p50, p95, p99, muestras y máximo de captura, HTTP, espera en inbox, aplicación, envío y trabajo por tick); `Errors` (tres últimos fallos y caídas con su antigüedad); `Counters` (totales, fallos, resultados del despachador, caídas, resultados de cast y descartes, solo los distintos de cero; eventos de contexto, marcas, rutas, `predictordecide` pendientes y descartadas; amenaza enriquecida) |
| `/thepredict check` | `thepredict.admin` | Respuesta inmediata y, cuando el servicio sale de `CHECKING`, `Jev check: <ESTADO> (<detalle>)` con el paso siguiente; recuerda que `/v1/models` no verifica una inferencia |
| `/thepredict inspect <nearest\|uuid>` | `thepredict.admin` | UUID abreviada y generación; tipo, perfil, estado, nivel y encuentro; intención (táctica, movimiento, choice, confianza, ticks restantes, skill intentada, `[debug]`); alternativas y contingencias; motivo (último `FallbackReason`, último `ExecutionOutcome`, rachas); percepción de la última captura; objetivo por alias y jugadores percibidos, segundos de combate; últimos eventos; metas instaladas y en curso; movimiento; tabla de amenaza y amenaza añadida en las ventanas abiertas. "Sin sesión" para un mob no vinculado |
| `/thepredict debug intent <nearest\|uuid> <táctica> [ticks]` | `thepredict.debug` | Aplica o rechaza (con el motivo) una intención `LOCAL` de una táctica del perfil |
| `/thepredict debug release <nearest\|uuid>` | `thepredict.debug` | Baja el mob a nivel 4 |
| `/thepredict debug goals <nearest\|uuid>` | `thepredict.debug` | UUID completa y generación (o sin sesión), una línea por meta y el progreso del movimiento |

### Pruebas

| Clase | Qué prueba |
|---|---|
| `admin/StatusFormatterTest` (5) | Nueve secciones en orden, datos de cada una, costo por hora, sin secretos ni jugadores, estado sin coordinador |
| `admin/InspectFormatterTest` (3) | Líneas de la inspección, marca `[debug]`, mob sin tabla de amenaza, listado de metas acotado |
| `admin/SetupMessagesTest` (6) | Instalación limpia sin errores, un mensaje distinto con paso siguiente por estado, perfiles leídos sin `Predictor: true`, reinicio del servidor en todo paso que cambia clave, modelo o configuración, aviso al entrar |
| `admin/ThePredictCommandTest` (8) | `status` en consola y jugador, `check` sin espera y con respuesta posterior, `inspect nearest` a 16 bloques, "sin sesión", permisos y autocompletado de `debug`, consola |
| `admin/DebugCommandParserTest` (9) | Gramática `intent|release|goals`, tácticas reservadas, ticks, acciones retiradas, eco de la entrada acotado a 40 caracteres |
| `engine/DebugIntentsTest` (8) | Intención local marcada con señal, validez por defecto, rechazos (táctica, sesión, suspendida, elegibilidad con su motivo), sin solicitudes a Jev, vencimiento a `BASE` con `EXPIRED` sin contarlo, cast una vez, `release` |
| `engine/DiagnosticsTest` (7) | Sesiones por nivel y estado, servicio, modelo, presupuestos de servidor y mundo, tokens, latencias; últimas causas con antigüedad y contadores distintos de cero; pausa y señales descartadas; informe de sesión con percepción, objetivo y tabla; metas sin sesión; amenaza de las ventanas; eventos acotados |
| `DecisionServicesTest` (+4) | `check` con future incompleta, esperas acotadas, `EngineCommands` con y sin coordinador |
| `StartupStateTest` (4), `ProfileCatalogsTest` (3) | Primer inicio, esquema 1 con propuesta e informe sin tocar nada ni copiar la clave, migración repetida idéntica, YAML roto y valor inválido; carga, bloqueo y registro acotado del catálogo |
| `PluginResourcesTest` (7), `MythicProfileBinderSuiteTest`, `ConfigurationRecoverySuiteTest` (+1) | `plugin.yml` y permisos, ejemplo (mob, skills, guía, con las líneas reales de `Profiles:` antes y después de `Predictor: true`), ejemplo validado sin avisos con `Cooldown` nativo, clave ausente sin sección del proveedor retirado |
| `CombatCaptureTest` (+1), `domain/ThreatTableStateTest` (2) | Tabla de amenaza sin identidad de jugadores y fuera del hilo del servidor; invariantes de `ThreatTableState` |
| `ArchitectureRulesTest` (+4) | Regla de la raíz y paquetes que no la alcanzan, única clase pública de la raíz, nombres retirados en M12, puerta del proveedor retirado |

Eliminadas con sus clases (15): `ThePredictStatusCommandTest`, `DebugCaptureTest`, `DebugProfileGoalsTest`, `adapter/openrouter/DecisionLifecycleSuiteTest`, `OpenRouterDecisionProviderTest`, `config/ProviderSettingsTest`, `RuntimeSettingsTest`, `decision/DecisionRequestTest`, `PredictionPlannerTest`, `ProviderFailureTest`, `ProviderHealthMonitorTest`, `goals/DebugGoalPresetsTest`, `events/DebugAutoInstallerTest`, `vocabulary/ConditionTraceTest`, `ProbeLabelsTest`. Adaptadas: `SessionServicesTest`, `VocabularyRegistrarTest`, `GoalSetInstallerTest`, `GoalInspectorTest`, `OwnerMarkerGoalTest`, `InstallDecisionTest`, `CombatSummaryReaderTest`, `FallbackChainTest` y `ReactiveLayerTest` (las pruebas de la vista de depuración antigua pasan a `DebugIntentsTest`) y las que usaban `TacticId.debug`.

### Desviaciones

| # | Desviación | Motivo |
|---|---|---|
| 1 | `debug intent` acepta `[ticks]` opcional (1–6000); sin él, la vigencia es `maximumIntentTicks` como pide M12.3 | Con el valor por defecto (40 ticks) los pasos de 30 s del guion (5.x, 9.3) exigirían repetir el comando cada 2 s |
| 2 | Elegibilidad de `debug intent`: antes de la primera captura (sin objetivo percibido) se acepta cualquier táctica del perfil; después, solo las elegibles de la última captura (`lastMobFacts`), que puede estar desfasada: solo se captura dentro de un encuentro, así que tras un combate quedan los hechos de su última evaluación. El motivo se deduce del contexto que gobierna o nombra lo que comprueba `ReadyTactics`: cooldown nativo, objetivo que la choice necesita y movimiento disponible (sin objetivo, o `FLANK` sin escudo levantado); las `Conditions` de la skill no entran, las evalúa el cast | `Eligibility` devuelve un conjunto, no motivos; sin captura no hay contextos, y rechazarlo todo dejaría la herramienta inútil fuera de combate |
| 3 | La intención de depuración tiene confianza 1, intenta su choice una vez como cualquier intención local y, al vencer, baja a `BASE` con `EXPIRED` en la sesión sin contarlo en `DecisionMetrics`; `release` baja cualquier intención a `BASE` sin motivo | No es una decisión de la cadena de fallback (A7 sin falsear contadores); el guion observa los placeholders con `confidence=1.00` |
| 4 | `debug goals` de un mob sin sesión usa `MobKey(uuid, 0)` | `describeGoals` resuelve el mob por UUID; el guion consulta mobs nativos sin ThePredict (DT-04, DT-16) |
| 5 | `inspect` añade `Perception`, `Target` (con segundos de combate) y `Events`, y `Encounter` guarda los hechos de su última captura (01, revisión 8) | Sustituyen a `debug capture` en la sección 11 del guion; solo alias, bandas y señales (I6) |
| 6 | `CombatReader.threatTable` y `domain.ThreatTableState` (00 y 01, revisión 8) | "Estado de la tabla de amenaza" de M12.2 y M11; `admin` no puede leer MythicMobs (D6) |
| 7 | `DecisionMetrics` guarda las tres últimas causas de fallo y de caída con su tick | M12.1 pide los tres últimos con su antigüedad; sin ello la sección `Errors` no existe. Es el único dato nuevo; el resto ya lo producía M09 |
| 8 | `ResultIntake` guarda hasta 8 esperas de `check` y el modelo de la última respuesta (etiquetado con `ModelExpectation.label`) | Informar al operador en el hilo del servidor cuando termina (M12.2) sin bloquear (T4); "modelo efectivo de la última respuesta" de M12.1 sin mostrar un valor no reconocido (C8) |
| 9 | `SetupReport` vive en `admin` y lo construye la raíz; `StatusReport` solo tiene datos de `engine` | Entorno, catálogo, configuración y migración no pertenecen a `engine` (D2, D10) |
| 10 | El aviso al entrar incluye `UNCONFIGURED` y `CHECKING` | M12.4 pide avisar si el servicio no está `READY`; el texto es informativo, no un error. M03.7 había decidido lo contrario antes de M12 |
| 11 | Sin comando de migración | M12.6 lo decide ("no hay comando propio en la 0.0.1"); se corrigió la frase contraria de M03 (Alcance, "No incluye") |
| 12 | D8 precisado (00, revisión 8): la raíz es el paquete `akyrion.thepredict` con clases auxiliares de paquete | Ya había composición repartida en la raíz desde M03, M06 y M09; ahora tiene regla propia en `ArchitectureRulesTest` |
| 13 | El ejemplo quita `CD` de las choices y usa solo `Cooldown` nativo; `Options.AsyncPathfinding` no se incluye | M12.5 pide `Cooldown` nativo; `AsyncPathfinding` solo si M01 lo valida (DT-10 sigue abierta) |
| 14 | `status` muestra además los tramos `dispatch` y `tick_work` y el número de intenciones de depuración | `tick_work` sustituye a la medida de instalación retirada del guion (DT-18) |
| 15 | DT-13 sigue `Abierta` | Una entrada solo cambia con evidencia de servidor; la estática no cambió (evaluación anotada en DT-13) |
| 16 | La extensión MythicScript 2077 no se modifica | M12.7 ("No incluye"): el módulo documenta los pasos y aplicarlos es decisión del usuario |
| 17 | C10: `ThePredictPlugin` 204 líneas; `StatusReport` 234 (registros anidados con Javadoc), `ResultIntake` 236 (+48), `DecisionMetrics` 227 (+58), `IntentControl` 211 (+34), `RequestBudget` 266 (+23), `ServiceStatusTracker` 256 (+10), `EncounterRegistry` 256 (+11), `Encounter` 254 (+16), `MobSession` 390 (+16); ningún método pasa de 30 líneas | Casi todo es Javadoc de accesores públicos (C9); `MobSession` y `Encounter` son los contenedores de 01 §8 |

### Pendiente de verificación en servidor

- M12.4: arranque de una instalación limpia sin errores y con `status` explicativo (guion 1.5).
- M12.5: con `Predictor: false`, el ejemplo pelea como un zombie de MythicMobs; con `Predictor: true`, todos los movimientos se observan en `inspect` (guion 9; M13).
- Todo el guion de M01 con los comandos de M12, en particular DT-17 (permiso y consola, pasos 2.1 y 2.5), y la salida real de `status`, `check` e `inspect`.
- `check` con una cuenta real (guion 12.8; llamada de pago que requiere aprobación del usuario). El mismo `SystemOneClient.check()` quedó verificado con la cuenta real en M13 (2026-09-29: 20 de 20 `READY`, «credential valid; model unverified until first inference», [informe](evidencia/live-check-2026-09-29.md)); falta el comando `/thepredict check` en el juego.

### Texto para la guía del autor (M12.7)

Para M14. Los nombres coinciden con los registrados en M04 (`VocabularyRegistrar.NAMES`) y las claves con las del binder:

1. **Solo colores.** En `~/.vscode/extensions/local.mythicscript-2077-1.0.0/vocab/`:

   | Archivo | Entradas |
   |---|---|
   | `conditions.json` | `"predictorcontrol"`, `"predictormove"`, `"predictortactic"` |
   | `mechanics.json` | `"predictordecide"` |
   | `keys-leaf.json` | `"Predictor"`, `"PredictValues"` |

   Si un autor escribe `PredictValues` como bloque de lista, también `"PredictValues"` en `keys-block.json`.
2. **Colores y autocompletado.** Un archivo por tipo declarado en `MythicScribe.customDatasets` de `settings.json` con `source: "File"`, por ejemplo `{ "elementType": "Condition", "source": "File", "path": "/ruta/thepredict-conditions.json" }` y `{ "elementType": "Mechanic", … }`. El formato de `attributes` se comprobó contra los datasets de MythicScribe 1.7.8 (`data/conditions/MythicMobs.json`: cada atributo tiene `name` como lista de alias, `type`, `description` y `default_value`):

   ```json
   [
     { "name": ["predictorcontrol"], "description": "True while ThePredict controls the mob", "attributes": [] },
     { "name": ["predictormove"], "description": "True while ThePredict asks for this movement",
       "attributes": [ { "name": ["movement", "m"], "type": "String",
         "description": "APPROACH, KEEP_DISTANCE, RETREAT, HOLD, FLANK or NONE", "default_value": "" } ] },
     { "name": ["predictortactic"], "description": "True while this tactic of the profile is current",
       "attributes": [ { "name": ["tactic", "t"], "type": "String",
         "description": "snake_case tactic ID of the profile", "default_value": "" } ] }
   ]
   ```

   y para la mecánica `[ { "name": ["predictordecide"], "description": "Asks ThePredict to decide again soon", "attributes": [] } ]`.
3. Los placeholders `<caster.predictor.tactic|movement|level|confidence|state>` no necesitan entrada: la gramática los reconoce por el punto.
4. Después de cualquiera de las vías: `python3 tools/regen.py` en la carpeta de la extensión y recargar la ventana de VS Code.

### Pendiente para módulos posteriores

- M13: medir el tiempo de cesión a la IA base tras `release` (DT-08, paso 6.3; la lectura tick a tick se retiró), el costo de instalación con `tick_work` y spark (DT-18), la latencia real de `check` y del resto de tramos, y el escenario del boss de ejemplo con Predictor apagado y encendido; confirmar que el precio de Jev 1.13 (`StatusFormatter.USD_PER_MILLION_INPUT_TOKENS`) sigue vigente.
- M14: guía del autor con el texto de M12.7; guía del administrador con los comandos y permisos de la tabla anterior (`thepredict.debug` con `default: false` puede sorprender); revisión final de `README.md`, `AI/README.txt` y `examples/README.txt`; changelog de la migración desde el esquema 1.
- DT-13 sigue abierta hasta el paso 3.5 del guion.

### Correcciones tras la auditoría (2026-09-29)

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** el paso siguiente de `INVALID_CREDENTIALS` decía repetir `/thepredict check`, pero la clave solo se lee al arrancar | Todo paso que cambia la clave, el modelo o `config.yml` (`UNCONFIGURED`, `INVALID_CREDENTIALS`, `INVALID_MODEL`, `INVALID_CONFIGURATION` y la configuración inválida del arranque) termina en `… and restart the server, then run /thepredict status.` (`SetupMessages.RESTART`); `INVALID_CREDENTIALS` añade `The key is read only at startup.`. `README.md` y `AI/README.txt` lo dicen también | `SetupMessagesTest.everyNextStepThatNeedsANewKeyModelOrConfigurationSaysToRestartTheServer` |
| **Bloqueante:** "No profiles: copy examples/…" salía también con perfiles leídos y ningún mob con `Predictor: true` | `SetupReport.profilesRead` (de `CatalogReloader.profilesRead`); con perfiles leídos: `<n> profiles read, but no mob targeted by a profile has Predictor: true. Next: set Predictor: true on the MythicMobs mob and run /mm reload.` | `SetupMessagesTest.profilesReadWithoutAnyMobWithPredictorGetTheirOwnMessage` |
| **Bloqueante:** el paso 4 de `examples/README.txt` y la preparación de la sección 9 del guion esperaban el perfil "activo" con `Predictor: false`, y `status` no lista nombres | El paso 4 cita la línea real `Profiles: SETUP_PENDING (revision N, 1 profiles read, 0 active, 0 errors, 0 warnings): no mob targeted by a profile has Predictor: true; set it in MythicMobs and reload MythicMobs`; el paso 6 cita `Profiles: LOADED (revision N, 1 profiles read, 1 active, 0 errors, 0 warnings)`; el guion §9 copia las dos, con y sin el pack de la sección 1.4 | `PluginResourcesTest.theExampleGuideQuotesTheRealCatalogLinesBeforeAndAfterPredictorIsSet` (genera las líneas con `ProfileCatalog.summary`) |
| Eco sin límite de la entrada del operador | `AdminText.echo` (40 caracteres y `…`) en `inspect` y en el analizador de `debug` (selector, táctica, acción desconocida) | `DebugCommandParserTest.operatorInputIsEchoedAtMostFortyCharactersLong`, `ThePredictCommandTest` |
| Prefijo `config.yml: config.yml:` | `status` usa la etiqueta `Configuration`; el arranque dice `Invalid configuration: <archivo: ruta: motivo>` | `StatusFormatterTest`, `SetupMessagesTest` |
| M03 esperaba "sin avisos al administrador" en el primer arranque | Alineado con la desviación 10 | — |
| DT-17 citaba `ThePredictStatusCommand` | Cita `admin.ThePredictCommand.mayDebug` | — |
| M07.8 sin nota de la clasificación retirada | Nota con referencia a DT-25 | — |
| Código muerto | Eliminados `StrictJson.optionalObject` y `FallbackPolicy.internalError` (con su prueba; el engine aplica `INTERNAL_ERROR` en `IntentControl.fail`); nota en M02 | Suite completa |
| Motivo de no elegibilidad inexacto (`ReadyTactics` no evalúa las `Conditions`) | `not eligible at the last capture: its skill was on its native cooldown or it needs a target, or <MOVIMIENTO> was not available (no target, or FLANK without a raised shield to flank)`; guion 8.5 actualizado | `DebugIntentsTest` |
| Hechos de la última captura posiblemente desfasados | Documentado en `DebugIntents`, en la desviación 2 y en el guion (§1.4) | — |
| 01 con la revisión 8 antes de la 7; árbol de 00 §2 | Orden corregido; el árbol indica "paquete raíz, con auxiliares de paquete" | — |
