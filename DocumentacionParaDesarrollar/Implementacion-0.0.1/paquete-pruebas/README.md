# Paquete de pruebas del usuario (ThePredict 0.0.1)

> Módulo: [M14](../M14-Documentacion-empaquetado-aceptacion.md) · Guiones de referencia: [M01](../M01-Guion-de-servidor.md) y [servidor 0.0.1](../Guion-servidor-0.0.1.md) · Registro: [decisiones técnicas](../M01-Registro-de-decisiones.md)

Todo lo necesario para que ejecutes en tu servidor las pruebas en juego de la 0.0.1, en orden de sesión de juego: primero las gratuitas sin clave y después, cuando decidas, las de pago con clave. Con lo que copies de vuelta (consola, `/thepredict status`, `inspect`) se rellena el registro de decisiones y se marcan las casillas de servidor con evidencia real.

## Contenido

| Archivo | Para qué |
|---|---|
| [Guia-de-pruebas-por-sesiones.md](Guia-de-pruebas-por-sesiones.md) | **Empieza aquí.** Sesiones 1 a 13, 15 a 29 y AIConfig sin clave (algunos pasos de la 21 a la 29 son de pago y lo dicen), y K1 a K5 y las partes con clave de la 16 (M16, efectividad) y la 18 (M18, duelo) con clave; cada paso con el comando exacto, lo esperado y qué copiar |
| [Como-reportar-resultados.md](Como-reportar-resultados.md) | Plantilla corta para enviar los resultados de cada sesión, sin secretos ni nombres de jugadores |
| [Checklist-DT-y-casillas.md](Checklist-DT-y-casillas.md) | Qué entrada DT y qué casilla de aceptación cierra cada paso |
| [Plantilla-informe-duelo.md](Plantilla-informe-duelo.md) | Informe del duelo de la sesión 18 (M18.8): brazos, Wilson, empates, comprobación de D y tabla de diagnóstico |
| [`archivos/`](archivos/) | Archivos listos para copiar a la carpeta del servidor, por etapa |

## Archivos listos para copiar

Cada carpeta reproduce la estructura de la raíz del servidor: `cp -R "$PACK/<carpeta>/." "$BASE/"` los deja en su sitio.

| Carpeta | Contenido | Sesión |
|---|---|---|
| `01-verificacion` | Pack de M01 §1.4: mobs `TPV_*` (todos con `Despawn: false`), sus skills y los cinco perfiles `tpv_*`, incluido `tpv_piglin` (solo `Movement: NONE`) | 2 |
| `02-permisos` | `permissions.yml` que da `thepredict.debug` a los operadores (M01 §1.3) | 2 (al final) |
| `03-guardian` | El mob del ejemplo como queda tras el paso 6 de `examples/README.txt` (`Predictor: true`) más `Options.Despawn: false`; desde M15.9 también copias idénticas del perfil y las skills del ejemplo, con el `Purpose` de cada choice, la skill `ThePredictGuardianSlam` y la táctica `close_in_and_slam` | 6, 7, 11, 15, 16, 17 |
| `04-slime` | Copia del ejemplo como `SLIME` con su perfil (M01 9.10) | 6 |
| `05-temporal` | Copia del ejemplo con `Despawn: true` y su perfil (M01 10.5) | 7 |
| `06-contexto-nativo` | Perfil del ejemplo con el contexto `wounded_native` (M01 §11) | 8, 9 |
| `07-senales` | Mob de `03-guardian` con una línea `message … ~onSignal:PREDICTOR_<TÁCTICA>` por cada una de las ocho tácticas del ejemplo (M01 §12; `CLOSE_IN_AND_SLAM` desde M15.9) | 9, K1, K2 |
| `08-amenaza` | Mob de `07-senales` con el líder de la tabla cada segundo (M01 §13) | 10 |
| `09-amenaza-sin-pesos` | Perfil de `06-contexto-nativo` con todos los pesos de amenaza a 0 (M01 13.8) | 10 |
| `10-ab` | Brazo A `ThePredictGuardianClassic` y `ABClassic.yml`, y el mob del brazo B (G13 §7.1) | 12, K5 |
| `11-vocabulario` | El ejemplo de vocabulario de la [guía del autor](../../../Guias/Guia-del-autor.md#10-vocabulario-de-thepredict-en-mythicmobs): mob `VocabularyGuardian`, skills y perfil | 3 |
| `12-entidades` | El cazador `TP_MonsterHunter` (`Faction: hunters`, admite `kind:MONSTER`), el lobo `TP_TagWatcher` (solo admite `tag:mipack:presas`), los duelistas `TP_DuelRed` y `TP_DuelBlue` (`Faction: red`/`blue`, solo la facción contraria y nunca jugadores), sus perfiles y el datapack `world/datapacks/mipack` con la etiqueta `mipack:presas` (M17) | 17 |
| `13-duelo` | El paquete de duelo de M18: la plantilla `DuelBase` y los duelistas `DuelRed`, `DuelRedClassic`, `DuelBlue` y `DuelBlueClassic` (`Template: DuelBase`, facciones `duel_red`/`duel_blue`, solo el rival y nunca jugadores, sin tabla de amenaza), las cinco skills del duelo con sus reglas clásicas `DuelRule*` y el `DuelTimeout` de 90 s, y los perfiles `duel_red` y `duel_blue` | 18 |
| `15-propositos` | Perfil del ejemplo más la táctica `keep_distance_and_strike` (`KEEP_DISTANCE` con el golpe declarado de 2,5 bloques), que da el aviso del lint de M15.4 | 15 |
| `19-intencion` | El ejemplo migrado: skills con línea `Intention` (y `PredictValues` como historia) y perfil sin `Purpose`. El mob lleva `Predictor: true` y `Despawn: false`. Sustituye a los tres archivos del ejemplo al copiarse; no añade otra identidad | 19 |
| `24-zona` | Mob `ZoneGuardian` con `AIPathfindingMalus` (pendiente de servidor), su skill `ZoneGuardianMelee` y perfil con `Zone` (`Mode`, `Radius`, `Facts`, `HazardPush: "off"`) más el contexto nativo `wounded_native`. No sustituye al guardián de ejemplo. Los `/fill` no se han ejecutado | 24 |
| `25-grupo` | Mobs `JefeInvocador`, `Patrulla5` y `TrioHechiceros` (todos con `Despawn: false`), sus skills `JefeInvocadorMelee`, `Patrulla5Melee`, `TrioHechicerosHeal` (cura a los otros hechiceros con `@MobsInRadius`) y `TrioHechicerosBolt`, y los perfiles `jefe_invocador`, `patrulla_5` y `trio_hechiceros` con `Group` en `fair`. Los mobs se invocan a mano | 25 |
| `26-patrones` | Jefe `JefePatrones` con mensajes de chat `~onSignal:PREDICTOR_PATTERN_*` (diagnóstico), su skill `JefePatronesMelee` y el perfil `jefe_patrones` con `Patterns` en `observe` | 26 |
| `27-memoria` | Jefe `JefeMemoria` y esbirro `EsbirroMemoria`, sus skills `JefeMemoriaMelee` y `EsbirroMemoriaMelee`, y los perfiles `jefe_memoria` y `esbirro_memoria` con `Memory` en `record` y `Patterns` en `observe`. Sin señales ni condiciones de memoria | 27 |
| `28-aprendizaje` | Solo `LEEME.txt`: usa el jefe de `13-duelo` y dos copias hechas a mano (con y sin `PredictorTraining`) | 28 |
| `29-m29` | Solo `LEEME.txt`: usa el duelo de `13-duelo` y el jefe de la sesión 28 | 29 |
| (sin carpeta) | La sesión AIConfig usa los archivos que crea el propio plugin en `plugins/MythicMobs/AIConfig/` y copias hechas a mano | AIConfig |

Al cargar `24-zona`, `25-grupo`, `26-patrones` o `27-memoria` solos, `/thepredict status` da `0 errors` y **1 aviso**: la skill melee de un perfil (la de daño medio) no tiene una pista visual o de sonido para `PREDICTOR_TELL_BLOCK` (`telegraph has no visual (or audio) cue; ThePredict adds none`). Es esperado: ThePredict no añade efectos y el paquete no los añade por ti. Las líneas esperadas son `1 profiles read, 1 active, 0 errors, 1 warnings` (24 y 26), `3 profiles read, 3 active, 0 errors, 1 warnings` (25) y `2 profiles read, 2 active, 0 errors, 1 warnings` (27).

El perfil y las skills del ejemplo se copian desde `plugins/ThePredict/examples/` del propio servidor siguiendo `examples/README.txt` (sesión 6), así la prueba usa los archivos reales del JAR. Desde M15.9 `03-guardian` lleva además copias del perfil y las skills, idénticas a las del JAR salvo los comentarios de cabecera (`UserTestPackTest` lo comprueba), para que la sesión 15 tenga los propósitos aunque el ejemplo se haya copiado antes de actualizar el plugin.

**Validación local.** `UserTestPackTest` carga cada combinación de etapas con el cargador real de perfiles (`ProfileLoader`) y el vinculador de ThePredict sobre dobles de MythicMobs (`MythicProfileBinder` con dobles construidos desde estos YAML; valida YAML y nombres, no el texto), y comprueba las líneas `Profiles:` que cita la guía, que todo `skill{s=…}` exista, que todos los mobs de prueba tengan `Despawn: false` (salvo el temporal), que los archivos coincidan con los bloques de los guiones y que cada variante solo cambie lo que el guion pide. Lo que no se puede comprobar sin servidor (que MythicMobs acepte cada línea de mecánica o condición) queda para la sesión en la que se usa: si MythicMobs rechaza una línea al recargar, se anota y se corrige.

## Cómo empezar

1. Lee las reglas al principio de la [guía por sesiones](Guia-de-pruebas-por-sesiones.md) y define las variables de terminal `REPO`, `CAND`, `BASE`, `JAVA` y `PACK`. `PACK` apunta al paquete del candidato (`$CAND`), que es el que trae los archivos completos de las sesiones 24 a 27.
2. Ejecuta la **sesión 1** (servidor limpio). No hace falta clave.
3. Tras cada sesión, envía lo copiado con la plantilla de [Cómo reportar resultados](Como-reportar-resultados.md). El orquestador rellena las entradas DT y marca las casillas con esa evidencia.
4. La **fase 2** (sesiones K1 a K5 y las partes con clave de las sesiones 16 y 18) solo empieza cuando tú lo decidas; cada sesión dice su costo estimado. La sesión 16 (M16) no necesita archivos nuevos: usa `03-guardian` y dos claves de `config.yml`. La primera, K1, es un encuentro corto con un solo mob para ver decisiones de Jev y el costo real.

La evidencia de cada sesión se guarda, con la plantilla de [G13 §2](../Guion-servidor-0.0.1.md#2-plantilla-de-evidencia-m132), en `DocumentacionParaDesarrollar/Evidencia-0.0.1/<AAAA-MM-DD>-<escenario>/`; la carpeta se crea al ejecutar la primera prueba.
