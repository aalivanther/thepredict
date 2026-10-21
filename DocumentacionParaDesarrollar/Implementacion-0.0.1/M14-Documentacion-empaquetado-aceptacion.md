# M14. Documentación, empaquetado y aceptación alojada

> **Estado:** Local listo — aceptación alojada pendiente (candidato pendiente de integración; ver [Estado de implementación](#estado-de-implementación)) · **Entrega:** E · **Depende de:** M13 · **Desbloquea:** publicación de la 0.0.1 · **Diseño:** [plan §11](../Plan-Jev-y-combate-tactico.md#11-plan-por-módulos-de-la-001), [plan §12](../Plan-Jev-y-combate-tactico.md#12-matriz-de-pruebas-y-evidencia) y [plan §13](../Plan-Jev-y-combate-tactico.md#13-decisiones-pendientes-de-evidencia-no-de-preferencias) · Reglas: [00](00-Arquitectura-y-reglas.md) · Dominio: [01](01-Modelo-de-dominio.md)

## Objetivo

Publicar la 0.0.1 con documentación de uso completa, un artefacto reproducible y evidencia real del servidor alojado. Sin host o credenciales reales, el resultado se rotula como candidato pendiente de integración, aunque compile (plan §11).

## Alcance

### Incluye

- Guía del autor y guía del administrador.
- Javadoc y build limpio con las comprobaciones ampliadas de `verifyPluginJar`.
- Matriz de versiones probadas y notas de migración.
- Guion de aceptación alojada y su evidencia.
- Pasos documentados para publicar la versión.

### No incluye

- Ejecutar `git push`, crear tags o publicar releases. Esos pasos solo se documentan; se ejecutan cuando el usuario lo pida de forma explícita.
- Nuevas funciones o cambios de defaults sin evidencia de M13.
- Afirmar compatibilidad con combinaciones no probadas (plan §1, W3).

## Base existente

| Archivo | Acción |
|---|---|
| `README.md` | Hoy describe la base con OpenRouter. Pasa a ser una introducción breve con enlaces a las guías y a la matriz de versiones. |
| `src/main/resources/AI/README.txt`, `examples/README.txt` | Guías cortas incluidas en el JAR; se alinean con la guía del autor (M12 los reescribe a v2). |
| `build.gradle.kts` | `version = "0.0.1"`, toolchain Java 25, dependencias `compileOnly` y `verifyPluginJar`. Se amplía la verificación. |
| `src/main/resources/plugin.yml` | `version: 0.0.1`, `api-version: '26.2'`, `depend: [MythicMobs]`. Se comprueba su coherencia con el build. |
| `DocumentacionParaDesarrollar/Evidencia-0.0.1/` (M13) | Fuente de la evidencia y de los defaults publicados. |

## Subpuntos

### M14.1 Guía del autor

- **Propósito:** que un autor de MythicMobs configure un boss con ThePredict sin leer Java.
- **Diseño:** nuevo documento `Guias/Guia-del-autor.md` en la raíz del repositorio, separado de la documentación de desarrollo. Secciones:
  1. Qué hace ThePredict y qué conserva MythicMobs (plan §1 y §3).
  2. Activar un mob: `Predictor: true`, `Options.FollowRange`, tabla de amenaza y especies con `Brain`, que solo admiten tácticas sin movimiento.
  3. Perfil v2: `Objective`, `Choices`, `Tactics`, `Movement`, `Threat`, `BaseAI` y contextos con condiciones de MythicMobs, con unidades.
  4. Nombres de tácticas: inglés semántico en `snake_case` (I4), porque llegan al modelo; ejemplos buenos y malos.
  5. `Objective` y `Description` en inglés y por qué (plan §6).
  6. Movimientos: `APPROACH`, `KEEP_DISTANCE`, `RETREAT`, `HOLD`, `FLANK` y `NONE`, con qué meta nativa se ejecuta cada uno.
  7. `PredictValues`, incluida `IGNORES_SHIELD`, y `Cooldown` nativo frente a `CD`.
  8. Vocabulario: `predictorcontrol`, `predictormove`, `predictortactic`, `predictordecide`, placeholders y señales `PREDICTOR_<TACTIC>`, con ejemplos.
  9. Amenaza enriquecida: pesos, unidades y efecto (M11).
  10. Vocabulario para MythicScript 2077 (M12.7).
  11. Límites conocidos: voladores, acuáticos, `Brain`, cancelación de skills ya iniciadas.
  - Cada ejemplo YAML se valida con el loader de M03 en una prueba de recursos, igual que el ejemplo de M12.5.
- **Archivos:** crear `Guias/Guia-del-autor.md`; actualizar `AI/README.txt` y `examples/README.txt` para enlazarla.
- **Reglas:** W1, W3, I4, I8.
- **Pruebas:** prueba de recursos que parsea los fragmentos de perfil de la guía guardados como fixtures.
- **Aceptación:**
  - [ ] Un autor reproduce el boss de ejemplo siguiendo solo la guía. Pendiente de servidor: sesión 6 del [paquete de pruebas](paquete-pruebas/Guia-de-pruebas-por-sesiones.md#sesión-6-el-boss-de-ejemplo-y-la-propiedad-de-la-ia).
  - [x] Todos los fragmentos YAML de la guía pasan la validación de M03. `UserGuidesTest`: cada bloque YAML carga; cada perfil carga con `ProfileLoader` sin problemas y se vincula sin problemas con `MythicProfileBinder` sobre dobles de MythicMobs construidos desde los mobs y skills de la propia guía (valida YAML y nombres, no el texto); los tres archivos del ejemplo aparecen literalmente.

### M14.2 Guía del administrador

- **Propósito:** instalar, configurar, diagnosticar y estimar costos sin leer Java.
- **Diseño:** nuevo documento `Guias/Guia-del-administrador.md`. Secciones:
  1. Requisitos: Java 25, Paper 26.2, MythicMobs Premium; matriz de M14.4.
  2. Instalación y primer inicio (M12.4).
  3. `config.yml` v2: cada clave con tipo, unidad, default y rango.
  4. Clave de Jev: `TYPESAFE_API_KEY` en el entorno del proceso; `jev.apiKey` solo como alternativa; nunca pegarla en chats ni en evidencias.
  5. Comandos y permisos: `status`, `check`, `inspect`, `debug` y los permisos `thepredict.admin` y `thepredict.debug` (M12).
  6. Diagnóstico por `ServiceState` y `FallbackReason`, con causa y acción. Extracto; la guía cubre todos los valores de 01:

     | Estado o motivo | Causa típica | Acción |
     |---|---|---|
     | `UNCONFIGURED` | Falta la clave | Definir `TYPESAFE_API_KEY` y reiniciar |
     | `INVALID_CREDENTIALS` | Clave rechazada (401) | Revisar la clave en la consola de TypeSafe |
     | `INVALID_MODEL` | Modelo inexistente | Usar el ID versionado probado |
     | `PAUSED` | 429, 529, timeout o red | Esperar la pausa; revisar latencia y límites |
     | `NO_BUDGET` | Presupuesto agotado | Revisar `maxRequestsPerMinute` y la carga |
     | `LOW_CONFIDENCE` | Confianza baja repetida | Revisar perfil, contextos y descripciones |
     | `NO_PROGRESS` | Movimiento sin avance | Revisar terreno, arena y especie |
     | `SESSION_INVALID` | Mob recargado, muerto o cambiado de mundo | Ninguna; es transitorio |
     | `INTERNAL_ERROR` | Excepción en ThePredict | Reportar con el registro; el mob vuelve a su IA base |

  7. Costos y límites: precio y límites publicados por TypeSafe (plan §6), cálculo de costo por hora que muestra `status` y capacidad soportada medida en M13.
  8. Migración desde v1 (M12.6).
  9. Desactivar: `Predictor: false` devuelve el mob a MythicMobs; quitar el plugin libera todas las metas.
- **Archivos:** crear `Guias/Guia-del-administrador.md`; reducir `README.md` a una introducción con enlaces.
- **Reglas:** W1, W3, C8.
- **Pruebas:** revisión cruzada de cada clave de configuración documentada contra el loader de M03; una clave sin documentar bloquea el cierre.
- **Aceptación:**
  - [x] Cada clave de `config.yml` aparece con tipo, unidad, default y rango. `UserGuidesTest.theAdministratorGuideDocumentsEveryConfigurationKeyWithTheBundledDefault` recorre `PluginConfigurationLoader.ALLOWED_PATHS` y compara cada default con el `config.yml` del JAR.
  - [x] Cada `ServiceState` y `FallbackReason` tiene causa y acción. `UserGuidesTest.theAdministratorGuideGivesACauseAndAnActionForEveryServiceStateAndFallbackReason`.

### M14.3 Javadoc y build

- **Propósito:** un artefacto reproducible que cumple las reglas C9 y C14.
- **Diseño:**
  - Comando de referencia: `./gradlew clean build javadoc`. Debe terminar sin fallos ni avisos nuevos de Javadoc.
  - Javadoc: **decisión de M14** entre activar `-Xdoclint` estricto para miembros públicos o una revisión manual; se propone activarlo cuando todo el código cumpla C9, para que un olvido rompa el build.
  - Ampliaciones de `verifyPluginJar` (sobre las de M12.8):
    - Lista v2 de recursos obligatorios: `plugin.yml`, `config.yml`, `AI/README.txt`, `AI/example.yml.example`, `examples/README.txt`, los tres archivos del boss de ejemplo y la clase principal.
    - `config.yml` contiene `schemaVersion: 2` y `apiKey: ''`.
    - Ninguna entrada contiene `adapter/openrouter/`.
    - La versión de `plugin.yml` coincide con la versión del proyecto.
    - Se conservan las comprobaciones actuales: bytecode Java 25 (versión mayor 69) y ninguna dependencia empaquetada (`io/lumine/`, `org/bukkit/`, `com/google/`, `.jar`).
- **Archivos:** `build.gradle.kts`.
- **Reglas:** C9, C14, P7.
- **Pruebas:** el build rompe si falta un recurso, si aparece un paquete prohibido o si las versiones no coinciden (probado introduciendo cada fallo en una copia de trabajo).
- **Aceptación:**
  - [x] `./gradlew clean build javadoc` verde. Con `--offline` (y `--rerun-tasks`): 1230 pruebas, 0 fallos, sin avisos, Javadoc estricto.
  - [x] Cada comprobación nueva de `verifyPluginJar` falla cuando debe. Probado en copias de trabajo (ver Estado de implementación): versión de `plugin.yml`, clase de herramienta de pago, clase de medida, clase de prueba, secreto en tres formas, `.env`, además de recurso ausente, paquete retirado, dependencia empaquetada y esquema.

### M14.4 Matriz de versiones y notas de migración

- **Propósito:** declarar solo lo probado y explicar cómo pasar de v1 a v2.
- **Diseño:**
  - Matriz en la guía del administrador y en `README.md`:

    | Componente | Versión probada | Fuente |
    |---|---|---|
    | ThePredict | 0.0.1 | `build.gradle.kts`, `plugin.yml` |
    | Java | 25 | toolchain del build |
    | Paper | 26.2 build 129 (`paper-api 26.2.build.129-stable`) | `VersionsOfJars/paper-26.2-129.jar` |
    | MythicMobs Premium | 5.13.1-SNAPSHOT (build `530971de`) | `plugin.yml` del JAR suministrado |
    | Modelo de Jev | `jev-1.13.0` | configuración por defecto |
    | API de TypeSafe | OpenAPI `0.2.0` | `api.typesafe.ai/openapi.json` |

  - Solo se añaden filas con evidencia de M13 (W3). Otras builds de Paper 26.x quedan como "no probadas".
  - Notas de migración v1 → v2: qué cambió en `config.yml` y en los perfiles, qué hace el migrador de M03 y qué debe revisar el administrador.
  - Registro de cambios en `CHANGELOG.md` en la raíz, con la entrada de la 0.0.1.
- **Archivos:** crear `CHANGELOG.md`; secciones en las guías y en `README.md`.
- **Reglas:** W3.
- **Pruebas:** revisión de que cada fila tiene evidencia en `Evidencia-0.0.1/`.
- **Aceptación:**
  - [x] Ninguna fila sin evidencia. La matriz de la guía del administrador separa la evidencia actual (compilación, pruebas, cuenta real) de la columna "Probada en servidor", que dice "Pendiente" en cada fila.
  - [x] Notas de migración coherentes con el informe del migrador. Escritas desde `ConfigurationMigration`, `ProfileMigration`, `OperationMigration` y `migration/report-template.md` (en la guía del administrador §9 y en `CHANGELOG.md`).

### M14.5 Guion de aceptación alojada

- **Propósito:** comprobar en el servidor real el criterio de cierre de la 0.0.1 (plan §13).
- **Diseño:**
  - Subconjunto del guion de M13 que se ejecuta en el host del usuario:
    1. Instalación limpia y primer inicio sin errores.
    2. Clave por entorno y `check` en `READY`.
    3. Boss de ejemplo con Predictor apagado: IA de MythicMobs.
    4. Predictor activo: IA gestionada por ThePredict, decisiones de Jev y movimientos observados con `inspect`.
    5. Dos instancias y dos mundos: aislamiento.
    6. Pérdida de servicio simulada sin tocar la clave, por ejemplo bloqueando temporalmente la salida del host hacia `api.typesafe.ai`: paso por los niveles de fallback y vuelta a `JEV` al recuperar la red.
    7. `/mm reload` y carga de chunks: metas reinstaladas.
    8. Desactivar el plugin: mobs con su IA de MythicMobs.
  - Evidencia con la plantilla de M13.2, sin secretos.
  - La 0.0.1 queda aceptada cuando el mismo boss cumple el criterio del plan §13: funciona con Predictor apagado, pasa a la IA de ThePredict al activarlo, consulta Jev, combina movimiento y habilidades con aislamiento, degrada por los cuatro niveles y respeta los límites medidos.
- **Archivos:** sección de aceptación en `Guion-servidor-0.0.1.md` (M13) y evidencia en `Evidencia-0.0.1/aceptacion/`.
- **Reglas:** A6, A8, C8, W3.
- **Pruebas:** la ejecución completa del guion en el host.
- **Aceptación:**
  - [ ] Los ocho pasos aprobados con evidencia.
  - [ ] Ningún paso requirió compartir la clave.

### M14.6 Publicación

- **Propósito:** dejar escritos los pasos para publicar la 0.0.1 de forma reproducible.
- **Diseño:**
  1. Confirmar `version = "0.0.1"` en `build.gradle.kts` y `version: 0.0.1` en `plugin.yml`.
  2. Ejecutar `./gradlew clean build javadoc`.
  3. Calcular la huella del artefacto: `shasum -a 256 build/libs/ThePredict-0.0.1.jar`, y registrarla en `CHANGELOG.md` y en la evidencia.
  4. Adjuntar el informe final de M13 y la evidencia de aceptación.
  5. Publicación en el canal que elija el usuario. El proyecto no es hoy un repositorio git; crear repositorio, tags o releases solo se hace si el usuario lo pide de forma explícita.
  - Si la aceptación alojada no se completa, el artefacto se rotula "candidato pendiente de integración".
- **Archivos:** `CHANGELOG.md` y evidencia.
- **Reglas:** W3, W4.
- **Pruebas:** la huella registrada coincide con la del artefacto entregado.
- **Aceptación:**
  - [ ] Artefacto, huella, informe y evidencia enlazados en el registro de cambios. Artefacto, huella, informe de la 0.0.1 y evidencia de la cuenta real enlazados; falta la evidencia de aceptación alojada.
  - [x] Ningún paso de publicación se ejecutó sin petición explícita. No se creó repositorio, tag ni release, ni se publicó el artefacto.

## Riesgos y decisiones abiertas

- Ubicación de las guías de usuario: `Guias/` en la raíz, separada de `DocumentacionParaDesarrollar/` (aplicado). Si el usuario prefiere otra ubicación, solo cambian los enlaces.
- Javadoc estricto con `-Xdoclint`: **decidido en M14**, activado (`-Xdoclint:all` y `-Werror`).
- Canal de publicación: pendiente del usuario.
- Sin host alojado, la 0.0.1 queda como candidata aunque el build y la suite local estén verdes.

## Terminado

- Guías del autor y del administrador revisadas contra el código y la configuración.
- `./gradlew clean build javadoc` verde con las comprobaciones ampliadas.
- Matriz de versiones, notas de migración y registro de cambios con evidencia.
- Aceptación alojada aprobada o rótulo de candidato.
- Regla W4 de 00 cumplida.

## Estado de implementación

> **Local listo — aceptación alojada pendiente (2026-09-29).** `JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline` (también con `--rerun-tasks`) termina en `BUILD SUCCESSFUL` sin avisos de javac ni de Javadoc, ahora con Javadoc estricto: **1230 pruebas en 180 clases, 0 fallos** (1208 de partida + 22; tras la auditoría, el mismo número: se amplió una prueba existente). Ninguna llamada de red (ni a TypeSafe ni a otro servicio), no se ejecutaron las tareas de pago, nadie leyó `.env` (W6) y no se arrancó ningún servidor de Minecraft: las casillas de juego siguen sin marcar y no hay evidencia inventada. El artefacto es un **candidato pendiente de integración** hasta que el usuario ejecute la aceptación alojada con el [paquete de pruebas](paquete-pruebas/README.md). 00 y 01 no cambian.

### Archivos

| Tipo | Archivos |
|---|---|
| Creados (usuario) | [`Guias/Guia-del-autor.md`](../../Guias/Guia-del-autor.md), [`Guias/Guia-del-administrador.md`](../../Guias/Guia-del-administrador.md), [`CHANGELOG.md`](../../CHANGELOG.md) |
| Creados (desarrollo) | [Informe-0.0.1.md](Informe-0.0.1.md); [paquete-pruebas/](paquete-pruebas/README.md): `README.md`, `Guia-de-pruebas-por-sesiones.md`, `Como-reportar-resultados.md`, `Checklist-DT-y-casillas.md` y `archivos/01-verificacion` a `archivos/11-vocabulario` (19 archivos YAML) |
| Pruebas | Nuevas `src/test/java/akyrion/thepredict/config/UserGuidesTest` (8), `src/test/java/akyrion/thepredict/UserTestPackTest` (14) y el ayudante `adapter/server/binding/MythicYaml` (dobles de MythicMobs desde YAML sobre `MythicFixtures`) |
| Modificados | `build.gradle.kts` (Javadoc estricto; JAR reproducible; `verifyPluginJar` con versión de `plugin.yml`, clases de prueba, de herramientas de pago y de medida, y secretos); `README.md` (introducción con enlaces, W3); `src/main/resources/AI/README.txt` y `examples/README.txt` (candidato y enlace a las guías); [guion de M01](M01-Guion-de-servidor.md) (§1.4 con `Despawn: false` y nota de revisión); [guion 0.0.1](Guion-servidor-0.0.1.md) (§12 aceptación alojada y nota del paquete); [README](README.md) (estado de los módulos); casillas de M11.1 y M12.7 que dependían de las guías; este documento |
| Código Java de `src/main` | Ninguno |

### `verifyPluginJar`: cada comprobación falla cuando debe

Cada caso se probó en una copia de trabajo del proyecto (sin `.env`), con `./gradlew verifyPluginJar --offline`:

| Fallo introducido | Mensaje |
|---|---|
| Ninguno (control) | Pasa |
| `version: 0.0.2` en `plugin.yml` | `plugin.yml version [0.0.2] must be the project version 0.0.1` |
| Clase `adapter/jev/LiveJevProbe` en `src/main` | `Plugin JAR contains test, live or measurement classes: [akyrion/thepredict/adapter/jev/LiveJevProbe.class]` |
| Clase `EngineMeasurements` en `src/main` | `… [akyrion/thepredict/EngineMeasurements.class]` |
| Clase `engine/TacticalAB` (nombre de un arnés de prueba) en `src/main` | `… [akyrion/thepredict/engine/TacticalAB.class]` (intersección con las clases de prueba) |
| `TYPESAFE_API_KEY=…` en `AI/README.txt` | `Plugin JAR contains a secret or an environment file: [AI/README.txt]` |
| `Authorization: Bearer …` en `examples/README.txt` | `… [examples/README.txt]` |
| `apiKey: …` con valor en `AI/example.yml.example` | `… [AI/example.yml.example]` |
| Recurso `.env` | `… [.env]` |
| Sin `examples/README.txt` | `Plugin JAR is missing required resources: [examples/README.txt]` |
| Paquete `snapshot` | `Plugin JAR contains a package retired in M12: …` |
| Recurso `lib.jar` | `Plugin JAR contains a bundled server, MythicMobs, or third-party dependency` |
| `schemaVersion: 3` en `config.yml` | `Bundled config.yml must use schema 2` |

Javadoc estricto: un método público sin comentario en una copia de trabajo da `warning: no comment` y `error: warnings found and -Werror specified`.

### Artefacto

`build/libs/ThePredict-0.0.1.jar`, 743 858 bytes, 403 entradas, SHA-256 `14c7180b4b4b039c667ef6f17ae362ef6769912ae42edf953d7e6c5f9b895fc7`; reproducible (dos builds limpios dan la misma huella). Registrado en el [CHANGELOG](../../CHANGELOG.md#artefacto).

### Pruebas nuevas (22)

| Clase | Qué demuestra |
|---|---|
| `UserGuidesTest` (8) | Todo bloque YAML de las dos guías es YAML válido; cada perfil de la guía del autor carga y se vincula sin problemas contra sus mobs y skills; los tres archivos del ejemplo aparecen literalmente; la guía del autor nombra cada campo del esquema, cada `Movement`, `PredictValue` y `ChoiceKind`, los contextos atajo, el vocabulario, los placeholders y las señales; la del administrador documenta cada clave de `config.yml` con su default real, da causa y acción para cada `ServiceState` y `FallbackReason`, lista comandos y permisos, y ningún documento de usuario ni archivo del paquete contiene una clave o un valor de entorno |
| `UserTestPackTest` (14) | Cada combinación de etapas del paquete, cargada con `CatalogReloader`, `ProfileLoader` y `MythicProfileBinder` sobre dobles construidos desde los YAML, da las líneas `Profiles:` que cita la guía por sesiones (5/5, 6/5, 6/6, 7/7 y el `INVALID` del perfil roto); el piglin solo con `NONE`; el slime como especie propia; el contexto nativo y los pesos a cero; el aviso de `DecayUnreachable` que cita M01; los dos brazos del A/B; `Despawn: false` en todos los mobs salvo el temporal; toda referencia `skill{s=…}` resuelta; los archivos coinciden con los bloques de M01 §1.3–1.4 y G13 §7.1 y con los ejemplos de la guía del autor; cada variante solo cambia lo que el guion pide |

### Desviaciones

| # | Desviación | Motivo |
|---|---|---|
| 1 | `verifyPluginJar` va más allá de la lista de M14.3: rechaza clases de prueba, de herramientas de pago y de medida, y secretos o `.env`. La búsqueda de secretos lee solo las entradas que no son `.class`; las clases se controlan por nombre y por la intersección con las clases de prueba (documentado en la guía del administrador §8.2 y en el CHANGELOG) | Las herramientas de pago y las medidas viven en `src/test` desde M13; la orden de M14 pide un JAR sin ellas ni secretos (W6) |
| 2 | JAR reproducible (`isPreserveFileTimestamps = false`, `isReproducibleFileOrder = true`) | M14.3 pide un artefacto reproducible y M14.6 una huella comprobable |
| 3 | La casilla de M14.6 sobre el registro de cambios queda sin marcar | Falta la evidencia de aceptación alojada |
| 4 | El paquete añade pasos que no estaban en los guiones: 3.8–3.13 (ejemplos de vocabulario de la guía del autor, M04.7), 6.18 (condición desconocida, M03.3), 6.19 (perfil roto, M03.5) y el paso 8 de la aceptación (sin el plugin) | Cierran casillas de servidor que no tenían paso; el paso 8 también se añadió al guion 0.0.1 (§12) |
| 5 | `Despawn: false` en todos los mobs del pack de M01 §1.4 | Sin él el paso 4.3 no se puede observar (ver la preparación de M01 §10); el guion se actualizó a la vez y `UserTestPackTest` comprueba que coinciden |
| 6 | El ejemplo de vocabulario de la guía del autor usa un mob completo (`VocabularyGuardian`, con `Despawn: false`) en vez del fragmento de M04 | Así se puede cargar, vincular y probar en el servidor tal cual |
| 7 | Casillas marcadas en otros módulos: M11.1 (tabla de defaults en la guía del autor) y M12.7 (pasos de MythicScript 2077 en la guía del autor) | Dependían solo de la guía, que ya existe; la extensión no se modificó |
| 8 | Las guías describen el comportamiento en juego con la marca "pendiente de servidor" y la capacidad como "pendiente de medición en servidor" | W3: nada se ha observado en un servidor real |

### Pendiente de verificación en servidor

- M14.1: un autor reproduce el boss de ejemplo siguiendo solo la guía (sesión 6 del paquete).
- M14.5: los ocho pasos de la aceptación alojada ([guion 0.0.1, §12](Guion-servidor-0.0.1.md#12-aceptación-alojada-m145)) y que ninguno requiera compartir la clave.
- M14.6: enlazar la evidencia de aceptación en el registro de cambios; publicar solo cuando el usuario lo pida y en el canal que elija.
- Todo lo que el [checklist](paquete-pruebas/Checklist-DT-y-casillas.md) asigna a cada sesión.

### Revisión pendiente del usuario

Ver el [registro de cambios](../../CHANGELOG.md#revisión-pendiente-del-usuario) y el [informe de la 0.0.1](Informe-0.0.1.md#5-revisión-pendiente-del-usuario): el 66,7 % de respuestas pagadas descartadas a `minimumConfidence` 0,5 (DT-31) y las etiquetas propuestas por M13 para los escenarios semilla (cinco desde M16.9; la calibración de M16 usa el banco etiquetado `src/test/resources/scenarios/bank/`).

### Correcciones tras la auditoría (2026-09-29)

`JAVA_HOME=…/temurin-25.jdk/Contents/Home ./gradlew clean build javadoc --offline --rerun-tasks` pasa sin avisos con **1230 pruebas, 0 fallos** (mismo número: `UserTestPackTest.theVariantsCarryTheLinesTheScriptsAskFor` se amplió). El JAR no cambia (ningún recurso ni clase de `src/main` se tocó): misma huella. Sin red, sin tareas de pago, sin leer `.env` y sin servidor.

| Hallazgo | Corrección | Prueba |
|---|---|---|
| **Bloqueante:** la guía del autor y la guía por sesiones decían que la prueba usa "el vinculador de MythicMobs", como si validara contra MythicMobs real | Redacción de la del paquete: "vinculador de ThePredict sobre dobles de MythicMobs; valida YAML y nombres, no el texto", también en el README del paquete, el informe y la casilla de M14.1 | — (documentos) |
| **Bloqueante:** `AssistsOthers` descrito como puntos por mob | Peso × otros participantes alcanzados, sumado en la tabla de cada miembro activo del encuentro (`ThreatEnrichment.assisted`) | — |
| **Bloqueante:** bandas de salud "por debajo" | "Igual o por debajo" (`Bands` usa `<=`) | — |
| **Bloqueante:** plantilla de la línea de amenaza solo con `+` | `Threat ±<puntos>` con ejemplos `+1.50 ALLY_ASSISTED` y `-2.00 UNREACHABLE` | — |
| **Bloqueante:** `searchRadiusBlocks` limitaba también el `maxrange` de `APPROACH` | Solo limita la percepción; `APPROACH` usa el `FollowRange` sin tope (o `RangedReach` si falta el atributo), según `GoalOwnership` y `MovementReading.followRangeBlocks` | — |
| **Bloqueante:** solo 5 de las 7 tácticas del ejemplo tenían línea `~onSignal`, y 9.2 esperaba un mensaje en cada cambio de táctica | Líneas `PREDICTOR_APPROACH` y `PREDICTOR_KEEP_DISTANCE` en `07-senales`, `08-amenaza` y la preparación de M01 §12; README del paquete, preparación de la sesión 9 y paso 9.2 actualizados | `UserTestPackTest.theVariantsCarryTheLinesTheScriptsAskFor` (siete líneas exactas en `07-senales`, las siete tácticas en M01 y `08-amenaza` empieza por `07-senales`) |
| **Bloqueante:** la comprobación de la clave (`${TYPESAFE_API_KEY:?}`) se pedía también en la fase 1 | En la fase 1 solo las comprobaciones 1 y 3; la 2 en las sesiones K desde un shell con el entorno cargado; G13 §2 dice lo mismo | — |
| Promesa de marca "pendiente de servidor" en cada comportamiento | Marcas añadidas en las condiciones de contexto, la instalación de metas, `NO_PROGRESS`, el lanzamiento de skills, la cadena de fallback, la tabla de `Threat`, la línea de registro y toda la §12 | — |
| "Idénticos a los del JAR" | "Salvo los comentarios de cabecera" | — |
| `http` solo con `localhost` | `localhost`, `127.0.0.1` o `::1` (`JevSettings.LOOPBACK_HOSTS`) | — |
| La propuesta de migración "se regenera" | Una propuesta idéntica no se reescribe: se reutiliza su carpeta (`MigrationPlanner.write`), en la guía del administrador y en el CHANGELOG | — |
| Grupo de contadores vacío | Muestra `none` | — |
| Orden de la sesión 6 | 6.17 antes de 6.18 y 6.19 | — |
| Preparación de K1 | `cp` exacto del mob de `07-senales` y del perfil del ejemplo, que las sesiones 10–12 sobrescriben | — |
| Nota de M04 sobre el traslado a la guía | Actualizada: M14 lo trasladó con cada elemento marcado como pendiente de servidor | — |
| Alcance de la búsqueda de secretos de `verifyPluginJar` | Documentado: solo entradas que no son `.class` (guía del administrador §8.2, CHANGELOG y desviación 1) | — |

