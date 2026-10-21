# Guion de servidor 0.0.1 (M13)

> Estado: **Pendiente de servidor**. Nadie lo ha ejecutado todavía. · Módulo: [M13](M13-Suite-carga-latencia-calidad.md) · Sustituye a `archive/Verificacion-servidor-0.0.1.md` · Reutiliza las secciones 1 a 13 del [guion de M01](M01-Guion-de-servidor.md) · Registro: [M01-Registro-de-decisiones.md](M01-Registro-de-decisiones.md)

El [paquete de pruebas](paquete-pruebas/README.md) (M14) reagrupa este guion y el de M01 por sesión de juego, con los archivos listos para copiar y validados por `UserTestPackTest`; la sección 12 es la aceptación alojada de M14.5.

Este guion cubre las filas de la matriz de M13.1 que exigen un servidor y añade lo propio de M13: plantilla de evidencia, carga, latencia, A/B, banco de escenarios e informe final. Cada paso dice qué ejecutar, qué esperar y qué copiar. Lo que no se observe se anota como no ejecutado o bloqueado, nunca con resultados supuestos.

## 0. Reglas

- Valen todas las reglas de la sección 0 del guion de M01: EULA aceptado en persona, carpeta del servidor fuera del repositorio, Java 25 explícito, salida literal y dos ejecuciones de cada caso.
- **PAGO** marca cada paso que llama a la cuenta real de TypeSafe. Solo se ejecuta después de que el usuario apruebe la estimación de costo de la [sección 9](#9-llamadas-de-pago-y-estimación-de-costo). Sin clave, el plugin no hace ninguna llamada.
- La clave vive solo en `.env` o en el entorno del servidor como `TYPESAFE_API_KEY` (regla W6). Nunca se imprime, se copia a la evidencia ni se pasa como argumento de un comando.
- spark viene dentro de Paper 26.2 build 129 (`META-INF/libraries/me/lucko/spark-paper/1.10.180`). Nunca se usa `--upload`, ni se abre el visor web, ni se comparte un enlace de spark sin aprobación del administrador (M13.2). Se copian las cifras que spark escribe en el chat o en la consola. **Ojo:** en spark 1.10.180, `/spark profiler stop` y `/spark heapsummary` suben el resultado a bytebin (el servicio web de spark) salvo que lleven `--save-to-file`; en este guion solo se usan con `--save-to-file`, y abrir el archivo guardado en el visor web también exige esa aprobación. `/spark tps`, `/spark health` (sin `--upload`) y `/spark tickmonitor` solo escriben en el chat.
- Comandos de spark 1.10.180 comprobados en el JAR (javap de `me.lucko.spark.paper.common.command.modules`, sin servidor): `/spark tps`, `/spark health` (alias `healthreport`, opciones `--memory` y `--network`), `/spark tickmonitor` (alias `tickmonitoring`, opciones `--threshold <porcentaje>`, `--threshold-tick <ms>` y `--without-gc`; el mismo comando lo apaga), `/spark gc`, `/spark gcmonitor`, `/spark profiler start|stop|cancel` (`--timeout`, `--thread`, `--only-ticks-over`, `--save-to-file`; sin `--save-to-file`, `stop` sube el informe) y `/spark heapsummary` (`--save-to-file`, `--run-gc-before`; sin `--save-to-file` sube el resumen). El paso 4.1 confirma cuáles responden en el servidor real.

## 1. Entorno

1. Servidor, binarios, huellas, primer arranque, permisos, pack de prueba y arena: secciones 1.1 a 1.5 del guion de M01, sin cambios.
2. Ejemplo del boss copiado siguiendo `plugins/ThePredict/examples/README.txt` (pasos 1 a 6), con `Options: { FollowRange: 32, Despawn: false }` en el mob copiado, igual que la preparación de la sección 10 del guion de M01.
3. Segundo mundo para las pruebas de carga: el Nether que Paper crea por defecto (`world_nether`). Con el servidor en marcha y un operador:
   ```text
   /execute in minecraft:the_nether run fill -20 69 -20 20 69 20 minecraft:obsidian
   /execute in minecraft:the_nether run fill -20 70 -20 20 75 20 minecraft:air
   /execute in minecraft:the_nether run tp @s 0 70 0
   ```
4. Copia de la arena para repetir escenarios (M13.2): con el servidor parado, `cp -R world world-arena-original`; para restaurarla, servidor parado, `rm -rf world && cp -R world-arena-original world`.
5. Cuentas: la carga pide dos jugadores por mundo (cuatro cuentas) y el A/B uno o dos. Si hay menos cuentas, el paso se ejecuta con las que haya y la evidencia lo dice.

## 2. Plantilla de evidencia (M13.2)

Cada ejecución crea `DocumentacionParaDesarrollar/Evidencia-0.0.1/<AAAA-MM-DD>-<escenario>/` con estos archivos. La carpeta se crea al ejecutar la primera prueba, no antes.

| Archivo | Contenido | Cómo |
|---|---|---|
| `versiones.txt` | Salida de `/version`, `/version MythicMobs` y `/thepredict status` | Copiar de la consola |
| `config.yml`, `AI/` | Configuración y perfiles usados, con la clave vacía o redactada | `sed -E "s/^([[:space:]]*apiKey:).*/\1 '<redactada>'/" plugins/ThePredict/config.yml > <carpeta>/config.yml` y `cp -R plugins/ThePredict/AI <carpeta>/AI` |
| `thepredict.log` | Líneas de ThePredict del registro | `grep -F '[ThePredict]' logs/latest.log > <carpeta>/thepredict.log` |
| `inspect-<momento>.txt` | Salidas de `/thepredict inspect` en los momentos clave | Copiar de la consola |
| `spark.txt` | Salidas de `/spark tps` y `/spark health --memory` | Copiar del chat o de la consola |
| `resultado.md` | `Aprobado`, `Fallido` o `Bloqueado`, con la causa y el paso | Escrito a mano |

Antes de guardar, estas comprobaciones; ninguna debe imprimir nada. La primera y la tercera, siempre; la segunda, solo en los pasos con clave (**PAGO**), desde un shell con el entorno cargado (sin clave no hay nada que buscar y `${TYPESAFE_API_KEY:?}` fallaría a propósito):

```bash
grep -n -i -E 'bearer|authorization|TYPESAFE_API_KEY=' -r <carpeta>
grep -rF -f <(printf '%s\n' "${TYPESAFE_API_KEY:?}") <carpeta>
grep -n -i -f nombres-de-jugadores.txt -r <carpeta>
```

La segunda busca la clave literal sin ponerla en los argumentos de ningún proceso (la lee de la sustitución de proceso) y exige haber cargado el entorno con `set -a; source .env; set +a` en ese shell; `${TYPESAFE_API_KEY:?}` hace fallar el comando si la clave está vacía, en vez de buscar una cadena vacía que coincidiría con todo. `nombres-de-jugadores.txt` es un archivo local con los nombres de las cuentas usadas, una por línea, que no entra en la evidencia. ThePredict no escribe nombres ni UUID de jugadores (C8, I6); la tercera comprobación cubre lo que se copie a mano.

## 3. Matriz de M13.1: filas y secciones de servidor

La tabla completa, con las pruebas automáticas de cada fila, está en [M13.1](M13-Suite-carga-latencia-calidad.md#m131-matriz-de-pruebas-a-clases). Aquí solo la parte de servidor.

| Fila del plan §12 | Sección de servidor |
|---|---|
| Configuración | M01 §1.5 (instalación limpia y dos arranques) y M01 §9 (catálogo con y sin `Predictor: true`) |
| HTTP | §6.1 (`check` con la cuenta real, **PAGO**) y M01 §12.8 |
| Concurrencia | §5 |
| Integración Mythic | M01 §3, §4, §8 y §12 |
| Vocabulario Mythic | M01 §3 |
| Encuentros | M01 §10 y §11.2; §5.2 (dos mundos) |
| Contexto nativo | M01 §11 |
| Propiedad de la IA | M01 §4, §6 y §9; §5.3 (carga masiva de chunk) |
| Fallback escalonado | M01 §12.1, §12.6, §12.7 y §12.8 (**PAGO**) |
| Movimiento | M01 §5 y §9 |
| Lifecycle | M01 §10 |
| Fallos en combate | M01 §12.8 (**PAGO**) y §5.2, paso 7 |
| Carga sostenida | §5 |
| Calidad táctica | §7 (A/B) y §8 (banco) |

## 4. Piloto

Antes de la carga se ejecuta una vez el guion completo en pequeño para validar la plantilla (M13.2, "Pruebas"):

| Paso | Acción | Esperado |
|---|---|---|
| 4.1 | `/spark tps`, `/spark health --memory`, `/spark tickmonitor --threshold-tick 55` y otra vez `/spark tickmonitor` | Las cuatro responden en el chat; la tercera activa el monitor y la cuarta lo apaga. Anotar el texto literal de las cabeceras (`TPS from last 5s, 10s, 1m, 5m, 15m:` y `Tick durations (min/med/95%ile/max ms) from last 10s, 1m:` según el JAR) |
| 4.2 | Un paso de carga de 1 mob (§5.2) durante 2 minutos, sin clave | Una carpeta de evidencia completa con los seis archivos y las dos comprobaciones de §2 vacías |

## 5. Carga (M13.3)

### 5.1 Preparación

1. Dos variantes por escalón: **5.A sin clave** (nivel `LOCAL`, costo cero; mide lo que añade ThePredict al tick sin HTTP) y **5.B con clave** (**PAGO**; añade solicitudes, tokens y latencia). Se ejecuta 5.A completa antes que 5.B.
2. `config.yml` con los valores por defecto y `decision.logDecisions: false` (las líneas por decisión no forman parte de la medida). Reiniciar tras cambiarlo.
3. En cada mundo, dos jugadores en `SURVIVAL` a unos 6 bloques entre sí, con `/effect give @s minecraft:resistance infinite 3 true` y `/effect give @s minecraft:regeneration infinite 1 true` para aguantar 10 minutos.
4. Los mobs se crean junto a los jugadores con `/mm m spawn ThePredictGuardian <n>` y reciben `/effect give @e[type=minecraft:zombie,distance=..40] minecraft:regeneration infinite 1 true`, para que el combate dure sin que mueran.

### 5.2 Escalones de 1, 10 y 30 mobs

Cada escalón: la mitad de los mobs en cada mundo (1 mob: solo en el mundo principal), 10 minutos de combate continuo en que los jugadores se mueven y golpean.

| Paso | Momento | Acción | Copiar |
|---|---|---|---|
| 1 | Antes de crear mobs | `/spark health --memory` y `/thepredict status` | Memoria (`Memory usage:` y `Usage at last GC:`), `Bound mobs`, `Encounters` |
| 2 | Minuto 0 | Crear los mobs (5.1.4) | — |
| 3 | Minutos 2, 4, 6, 8 y 10 | `/thepredict status` y `/spark tps` | De `status`: `Bound mobs`, `Encounters`, `Server` (Budget), `Input tokens` (Consumption), las líneas `capture`, `roundtrip`, `inbox_wait`, `apply`, `dispatch` y `tick_work` de `Latency`, `totals` (en particular `deferred_ticks=`, que solo aparece si no es 0: `status` omite los contadores a cero), `dispatch`, `fallbacks`, `outcomes` y `Context` (`predictordecide pending`, `dropped`). De spark: `Tick durations (min/med/95%ile/max ms)` y TPS |
| 4 | Minuto 10 | `/spark health --memory` | Memoria después |
| 5 | Minuto 10 | Poner `Predictor: false` en el mob copiado, `/mm reload`, esperar 70 s, `/thepredict status` | `Server: 0/600 requests in 60 s, 0/8 in flight` y `Bound mobs: 0 active, …`: ningún envío con Predictor apagado |
| 6 | Siempre | `grep -n -E 'Asynchronous|IllegalStateException|ConcurrentModificationException' logs/latest.log` | Vacío: ningún acceso al mundo desde un callback |
| 7 | Solo 5.B con 30 mobs | Cortar la red del servidor 30 s en el minuto 5, como el paso 12.8 del guion de M01 | `fallbacks` sube en `service_paused`; `Pause: ends in … s`; ningún error; vuelta a `JEV` al restablecer |

Condiciones obligatorias del plan §12 (se anotan en `resultado.md`): ninguna línea del paso 6; `outcomes` sin casts de más (en juego, M01 §12.3); `Encounters` y `predictordecide pending` que no crecen entre el minuto 2 y el 10; memoria tras el GC sin tendencia creciente; el paso 5 cumplido; y, en 5.B, el paso 7 cumplido.

Tabla de medidas de cada escalón (plantilla):

| Medida | 1 mob | 10 mobs | 30 mobs | Fuente |
|---|---|---|---|---|
| Tick del servidor: mediana (`med`), p95 y máximo (ms) | | | | `/spark tps`, `Tick durations (min/med/95%ile/max ms)` (spark no da media) |
| `tick_work` p50, p95, p99 (us) | | | | `status`, Latency |
| `capture` p50, p95, p99 (us) | | | | `status`, Latency |
| Solicitudes por minuto frente a 600 | | | | `status`, Budget |
| Tokens por minuto y USD por hora | | | | `status`, Consumption |
| `deferred_ticks` acumulados (0 si no aparece) | | | | `status`, Counters |
| Encuentros activos, `predictordecide pending` | | | | `status` |
| Memoria antes y después (tras GC) | | | | `/spark health --memory` |

Referencia local (no sustituye a estas medidas): con dobles del puerto, el propio motor gasta 4–22 us de media por tick de 1 a 200 mobs y el límite de 600 000 tokens estimados por minuto se alcanza con unos 30 mobs en combate continuo (tabla de carga del [estado de M13](M13-Suite-carga-latencia-calidad.md#medidas-locales)).

### 5.3 Carga masiva de chunk (M05.4, DT-18)

| Paso | Acción | Esperado y copiar |
|---|---|---|
| 5.3.1 | 30 `ThePredictGuardian` con `Despawn: false` en un mismo chunk; alejarse hasta descargarlo; `/spark tickmonitor --threshold-tick 55` | El monitor queda activo |
| 5.3.2 | Volver al chunk; esperar 5 s; `/thepredict status`; `/spark tickmonitor` para apagarlo | Líneas del monitor con ticks de más de 55 ms (o ninguna); `tick_work` (p99 y `max`); `inspect` de 3 mobs con `Goals: installed for revision <n>`. Las instalaciones van a 10 por tick (`decision.maxInstallsPerTick`): localmente, 30 sesiones abiertas a la vez quedan instaladas en 3 ticks (`CoordinatorLoadTest`) |

### 5.4 Sondas con 100 mobs (M07.6) y captura con varios jugadores (M07.1)

| Paso | Acción | Esperado y copiar |
|---|---|---|
| 5.4.1 | Sin clave, 100 mobs vinculados en combate con dos jugadores durante 2 minutos | `tick_work` p50, p95, p99 y `max`; `deferred_ticks=` no aparece en `totals` (`status` omite los contadores a cero, y un tick solo cuenta como aplazado si una decisión o una instalación esperaba). Local: las evaluaciones se detienen si las sondas de un tick gastan `maxTickWorkMicros`, unos 2000 / (mobs / 2) us por sonda (40 us con 100 mobs); si `tick_work` p95 se acerca a 2 ms, bajar `decision.maxProbesPerTick` y repetir |
| 5.4.2 | Con 1 y con todas las cuentas disponibles cerca de un mob, 20 s de combate (como M01 §11.11) | Línea `capture`; 16 jugadores solo si hay 16 cuentas; si no, se anota el número usado |

### 5.5 Sesiones suspendidas en un servidor de larga duración (pendiente de M06, DT-20)

Durante una sesión de juego normal de al menos 2 horas con el boss vinculado en varias zonas, copiar cada 30 minutos la línea `Bound mobs` (`<n> active, <m> suspended`). Si `suspended` crece sin volver a bajar cuando los jugadores regresan a las zonas, se propone el plazo de cierre de DT-20; si se estabiliza, DT-20 se cierra sin cambios. Localmente está probado que una sesión suspendida cuyo mob desaparece sin evento sigue en memoria (`SessionReconcilerScaleTest`).

### 5.6 Capacidad soportada del host

Con las tablas de 5.2 se publica en el informe final (§10): mobs en combate sostenido, solicitudes por minuto y tiempo añadido por tick (diferencia de la mediana `med` de `Tick durations` de `/spark tps` entre 0 y N mobs, tomada en la ventana de 1 minuto, y `tick_work` p99). El objetivo inicial es `tick_work` p99 ≤ 1 ms con 30 mobs.

## 6. Latencia (M13.4)

### 6.1 Red hacia TypeSafe y `check` real (**PAGO**, sin tokens)

`GET /v1/models` no consume tokens pero usa la cuenta. Desde el host del servidor, 50 llamadas separadas 10 s, sin poner la clave en la línea de comandos (se pasa por la entrada estándar a `curl -K -`):

```bash
set -a; source .env; set +a
for i in $(seq 1 50); do
  printf 'header = "Authorization: Bearer %s"\n' "$TYPESAFE_API_KEY" \
    | curl -s -o /dev/null -K - -w '%{http_code} %{time_connect} %{time_starttransfer} %{time_total}\n' \
      https://api.typesafe.ai/v1/models
  sleep 10
done > check-latency.txt
```

Después, en el juego, `/thepredict check` una vez: respuesta inmediata y, al llegar, `Jev check: READY (…)` (M12.2). Desde la máquina de desarrollo, la misma serie la hace `./gradlew liveJevCheck --no-daemon -PliveJev=true` (§9). Se copian `check-latency.txt` (sin cabeceras) y los percentiles de `time_total`.

### 6.2 Inferencia (**PAGO**)

Con clave, dos encuentros simultáneos (un jugador y un boss en cada extremo de la arena, a más de 32 bloques entre sí) durante 2 minutos por escenario de §7.2, en dos horarios distintos del día. Al final de cada bloque de 2 minutos, `/thepredict status` y copiar la línea `roundtrip` (p50, p95, p99, muestras y `max` de los dos últimos minutos): con dos encuentros a un latido de 20 ticks salen unas 240 muestras por bloque, por encima de las 200 de M13.4.

### 6.3 Extremo a extremo por tramo

De la misma salida de 6.2: `capture` (evento → captura en la evaluación), `dispatch`, `roundtrip`, `inbox_wait` y `apply`. El tramo "aplicación → meta de movimiento en curso" no tiene métrica (M13 no crea métricas nuevas): se observa con `/thepredict debug goals nearest` que la meta del movimiento aparece `running` en la primera lectura tras un cambio de táctica. Las contingencias y la siguiente alternativa se aplican en ≤2 ticks sin red (`ReactiveLayerTest`), es decir ≤100 ms a 20 TPS; en el servidor solo se observa que el cambio es inmediato.

### 6.4 Cesión a la IA base tras `release` (DT-08)

Con el boss vinculado, el jugador en `CREATIVE` a 6–10 bloques (sin objetivo percibido, así el modo local no vuelve a tomar el control) y `debug intent nearest hold 400` activo: `/thepredict debug release nearest` e inmediatamente `/thepredict debug goals nearest` dos veces. Esperado: en la primera lectura `thepredict:hold` ya no está `running` y ninguna meta `thepredict:*` lo está; sin objetivo, las metas base pueden estar inactivas. Cada comando llega en su propio tick, así que este paso comprueba la cesión en la primera lectura posible, no el número exacto de ticks: no hay herramienta ni métrica que lea metas tick a tick desde M12 y M13 no crea métricas. El límite de ≤2 ticks queda respaldado solo por la evidencia local (la vista se retira en el mismo tick y las condiciones `predictor*` la leen en su siguiente evaluación) y se anota así en DT-08.

### 6.5 Decisión sobre `timeoutMillis` y latido

- Si más del 5 % de las muestras de `roundtrip` de 6.2 pasan de 800 ms, o `failures` muestra `timeout=` de forma habitual, se sube `jev.timeoutMillis` al p99 medido (máximo 1 500) y se anota en el informe; el latido no baja.
- Si el p99 queda por debajo de 400 ms en los dos horarios, se mantiene `timeoutMillis: 800`.
- En ningún caso se aumentan las consultas sin límite (plan §13).

## 7. A/B frente a la IA de MythicMobs (M13.5)

### 7.1 Variante clásica del boss (brazo A)

`plugins/MythicMobs/Mobs/ThePredictGuardianClassic.yml`: mismas estadísticas, tabla de amenaza y skills que el ejemplo, disparadas por reglas clásicas de MythicMobs; `Predictor: false` y sin perfil de ThePredict.

```yaml
ThePredictGuardianClassic:
  Type: ZOMBIE
  Display: 'ThePredict Guardian'
  Health: 100
  Damage: 3
  Predictor: false
  Options:
    FollowRange: 32
    PreventOtherDrops: true
    Despawn: false
  Modules:
    ThreatTable: true
  ThreatTable:
    UseDamageTaken: true
    DecayUnreachable: false
  Skills:
  - damage{a=2} @target ~onAttack
  - skill{s=ABClassicMelee} @self ~onTimer:20
  - skill{s=ABClassicRanged} @self ~onTimer:20
  - skill{s=ABClassicSlam} @self ~onTimer:20
  - skill{s=ABClassicDefense} @self ~onTimer:20
  - skill{s=ABReach} @self ~onTimer:20
  - command{c="say AB died"} @self ~onDeath
```

`plugins/MythicMobs/Skills/ABClassic.yml`:

```yaml
ABClassicMelee:
  Conditions:
  - targetwithin{d=3} true
  Skills:
  - skill{s=ThePredictGuardianMelee} @target
ABClassicRanged:
  Conditions:
  - targetwithin{d=5} false
  Skills:
  - skill{s=ThePredictGuardianRanged} @target
ABClassicSlam:
  Conditions:
  - targetwithin{d=3} true
  Skills:
  - skill{s=ThePredictGuardianSlam} @self
ABClassicDefense:
  Conditions:
  - healthpercent{p=<30%} true
  Skills:
  - skill{s=ThePredictGuardianDefense} @self
ABReach:
  Conditions:
  - targetwithin{d=3} true
  Skills:
  - command{c="say AB in_reach"} @self
```

Las skills del ejemplo conservan su `Cooldown` nativo y sus condiciones propias (línea de visión y 16 bloques en la de distancia; salud por debajo del 90 % en la defensa), así que ningún brazo tiene más capacidades que el otro. Desde M15.9 el ejemplo tiene una cuarta skill, `ThePredictGuardianSlam` (área de 3 bloques alrededor del boss, solo jugadores), y el brazo A la dispara con `ABClassicSlam` cuando el objetivo está a menos de 3 bloques: `targetwithin{d=3}` compara las posiciones del mob y de su objetivo con `<` (javap de `TargetWithinCondition`), la misma medida que `@PlayersInRadius{r=3}` y que `Radius: 3` en el brazo B, así que los dos brazos lanzan el slam en la misma geometría. En el brazo B (el ejemplo con `Predictor: true`) se añaden al mob copiado las mismas dos últimas líneas (`ABReach` y el mensaje de muerte), que no cambian el combate y dan la misma medida en los dos brazos. Si MythicMobs rechaza alguna línea al recargar, se corrige la sintaxis, se anota y se repite.

### 7.2 Escenarios, repeticiones y medidas

Escenarios: duelo cuerpo a cuerpo; jugador que dispara con arco y retrocede; jugador con escudo; mob con salud baja (`/damage @e[type=minecraft:zombie,sort=nearest,limit=1] 75 minecraft:out_of_world` al empezar); dos jugadores (uno tanquea y otro dispara); objetivo sobre un pilar de 3 bloques sin acceso. Cada uno 60 s o hasta la muerte del mob, **10 repeticiones por brazo**, alternando el orden (A, B, B, A, A, B…), con la arena restaurada (§1.4) si el terreno cambió. El brazo B se ejecuta primero sin clave (local, costo cero) y, tras aprobación, con clave (**PAGO**).

Preparación de las medidas, una vez:

```text
/scoreboard objectives add dealt minecraft.custom:minecraft.damage_dealt
/scoreboard objectives add taken minecraft.custom:minecraft.damage_taken
```

Por repetición: `/scoreboard players reset @a dealt` y `/scoreboard players reset @a taken`; `/thepredict status` antes y después (brazo B); crear el mob; combatir; al terminar, `/scoreboard players list <jugador>` por jugador y `grep -c 'AB in_reach' logs/latest.log` antes y después.

| Medida | Fuente | Brazo |
|---|---|---|
| Tiempo en alcance útil (s) | Líneas `AB in_reach` (una por segundo con el objetivo a ≤3 bloques) | A y B |
| Tiempo de supervivencia del mob (s) | Hora de la línea `AB died` menos la de la creación (o 60 s) | A y B |
| Daño hecho y recibido | `dealt` y `taken` (décimas de punto de vida) | A y B |
| Golpes evitados en invulnerabilidad | `outcomes: deferred_invulnerable=` (diferencia) | B |
| Ataques evitados contra escudo | `outcomes: skipped_shield=` (diferencia) | B |
| Cambios de posición | Solo las líneas `Mob <uuid> <NIVEL> <táctica>` (nivel `JEV`, `ALTERNATIVE` o `LOCAL`) con `decision.logDecisions: true`; no las de `choice`, `fell back` ni `Threat`. El registro admite 20 líneas cada 200 ticks (10 s) y el primer mensaje de la ventana siguiente dice `Tactical execution: <n> log line(s) suppressed`: si aparece, la cuenta de esa repetición es un mínimo y se anota | B |
| Solicitudes, tokens y costo | `totals: answered=`, `input_tokens=` y `Input tokens` (diferencias) | B con clave |
| Efectividad por nivel (M16.8) | Líneas `outcomes: level=<NIVEL> windows= dealt= taken= reach= hit_rate= effect=` de la sección `Effectiveness` de `status` (diferencia de ventanas y medias del bloque) y, con `decision.logDecisions: true`, las líneas `Outcome <nivel> <táctica> dealt= taken= reached= casts= hits=` | B (`LOCAL` sin clave; `JEV` y `ALTERNATIVE` con clave) |
| Contingencias (M16.6) | `contingencies: asked= fired= effective=` (diferencias) | B con clave |

Resultado por escenario y brazo: media, desviación, mínimo y máximo de cada medida (M13.5 pide dispersión). Un aumento de daño por golpes duplicados no cuenta como mejora (M01 §12.3 comprueba que no los hay). Las conclusiones se limitan a esta base medida.

Referencia local: el arnés `TacticalAB` compara el mismo combate con y sin un Jev guionizado (estado de M13). Detectó que, con Jev, una táctica que se renovaba no volvía a lanzar su skill; tras la corrección de DT-28 la skill se rearma cada vida de intención local, como en el modo local (en el duelo, 17,6 casts en 30 s con los defaults frente a 19,1 en local). El brazo B mide si el boss sigue perdiendo daño.

## 8. Banco de escenarios (M13.6)

| Paso | Acción | Esperado |
|---|---|---|
| 8.1 | **PAGO.** `debug.recordScenarios: true` en `config.yml`, reiniciar, jugar los escenarios de §7.2 con clave | Un archivo `plugins/ThePredict/scenarios/scenario-<AAAAMMDD-HHMMSS>-<nonce>-<nnnn>.json` (hora UTC del arranque y un nonce aleatorio de 8 cifras hexadecimales por arranque) por decisión respondida, como mucho 1 000 por arranque; al llegar al límite, una sola línea `Scenario recording reached its limit of 1000 files; later decisions are not recorded` |
| 8.2 | `grep -c -E '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' plugins/ThePredict/scenarios/*.json` y la comprobación de nombres de §2 | 0 en todos los archivos: solo alias (I6) |
| 8.3 | `debug.recordScenarios: false` y reiniciar | Grabación desactivada |
| 8.4 | Copiar los archivos a `Evidencia-0.0.1/<fecha>-banco/scenarios/` y etiquetarlos a mano: en `labels`, las tácticas aceptables de cada pregunta y, si la hay, la prohibida | Archivos etiquetados |
| 8.5 | **PAGO.** En la máquina de desarrollo: `./gradlew liveScenarioBench --no-daemon -PliveJev=true -PscenarioDir=<ruta de 8.4> -Prepetitions=<n>` (§9) | Informe en `build/reports/m16/live-scenario-bench.md`: latencia, fallos, tokens, caracteres por token, fiabilidad de la confianza por tramos, cobertura y acierto de cada `minimumConfidence`, cambios que permitiría cada `tacticSwitchMargin` y contingencias distintas de la respuesta para ahora. Se copia a la carpeta de 8.4 junto al ID de modelo |

El banco semilla del repositorio (`src/test/resources/scenarios/seed/`, cinco escenarios con etiquetas propuestas por M13 y a revisar; M16.9 retiró `seed-low-health`, cuyas dos opciones eran aceptables, y el banco pagado del 2026-09-29 sigue congelado en `scenarios/seed-2026-09-29/`) permite ejecutar 8.5 sin haber grabado nada. Desde M16 la calibración usa el banco etiquetado de 60 escenarios (`src/test/resources/scenarios/bank/`, M16.9), con la hoja de revisión que genera `./gradlew labelReviewSheet --offline` y el re-banco de M16.10 (§9).

## 9. Llamadas de pago y estimación de costo

Precio publicado de Jev 1.13: 0,042 USD por millón de tokens de entrada (plan §6; confirmar en `https://docs.typesafe.ai/models` antes de aprobar, pendiente de M12). Estimaciones con el ejemplo; los tokens reales dependen de `usage.input_tokens`.

| Paso | Llamadas | Tokens estimados | USD estimado |
|---|---|---|---|
| §6.1 `curl` o `./gradlew liveJevCheck --no-daemon -PliveJev=true` (50 × `GET /v1/models`, 10 s) | 50 | 0 | 0 |
| §8.5 `./gradlew liveScenarioBench --no-daemon -PliveJev=true` (banco semilla, 1 repetición) | 6 | ≤ 4 264 | ≤ 0,0002 |
| §8.5 igual con `-Prepetitions=200` (serie de inferencia de M13.4) | 1 200 | ≤ 852 800 (ejecutado el 2026-09-29: 735 800) | ≤ 0,036 (real: 0,0309) |
| §5.2 variante 5.B, 1 mob, 10 min | ≈ 600 | ≈ 419 000 | ≈ 0,018 |
| §5.2 variante 5.B, 10 mobs, 10 min | ≈ 2 300 | ≈ 2,5 M | ≈ 0,10 |
| §5.2 variante 5.B, 30 mobs, 10 min | ≈ 2 400 | ≈ 5,4 M | ≈ 0,23 |
| §6.2, 6 escenarios × 2 min × 2 encuentros × 2 horarios | ≈ 2 900 | ≈ 2,9 M | ≈ 0,12 |
| §7.2 brazo B con clave, 6 escenarios × 10 repeticiones × 60 s (estimación de M13, antes de M16) | ≈ 3 600 | ≈ 2,5–4,1 M | ≈ 0,11–0,17 |
| §7.2 brazo B con clave desde M16, `decision.questionDetail: compact` (≈ 1 400 tokens por solicitud, cota a 1,5 c/t) | ≈ 3 600 | ≈ 5,0 M | ≈ 0,21 (con `standard`, ≈ 0,43) |
| Re-banco de M16.10 en la máquina de desarrollo: P1 `./gradlew liveScenarioBench --no-daemon -PliveJev=true -PscenarioDir=src/test/resources/scenarios/bank -Parms=B -Prepetitions=10 -Pscenarios=core12 -PmaxCalls=130` y P2 `… -Parms=A,B,C,D,E -Prepetitions=2 -Pscenarios=all -PmaxCalls=620`; exige revisar antes al menos las etiquetas de `core24` y la aprobación del usuario | 720 (120 + 600) | ≈ 1,18 M (cota medida por la herramienta: 94 670 + 1 083 828) | ≈ 0,050 (techo de M16: 0,10) |
| Sesión 16.2 del paquete: 3 combates de 60 s con clave y `compact` | ≈ 180 | ≈ 252 000 (cota) | ≈ 0,011 |
| §13 paso 18.4 (sesión 18 con clave): 30 rondas, 10 de A, 10 de B y 10 de C, con el default `full` | ≈ 2 400 (máx. 3 600) | ≈ 7,7 M a 1,5 c/t (máx. 11,5 M); ≈ 4,5 M con los 1 884 tokens reales | ≈ 0,32 (máx. 0,48); real ≈ 0,19 (máx. 0,28); techo 0,60 |
| §13 paso 18.4 con `decision.questionDetail: compact` | ≈ 2 400 (máx. 3 600) | ≈ 3,4 M a 1,5 c/t; ≈ 2,3 M con los 953 reales | ≈ 0,14 (máx. 0,21); real ≈ 0,096 |

Desde M16 el presupuesto reserva por el tamaño real del cuerpo preparado (`ceil(caracteres / cpt)`, `cpt` aprendido entre 1,3 y 2,0) en lugar de la estimación de M09; las solicitudes de producción usan `full` por defecto desde el re-banco de M16.10 (antes `compact`). Las cifras de §5.2 salen de la simulación local con la estimación de M09 (400 + 120 por pregunta + 60 por participante), anterior a M15 y M16; las del banco, de 1,5 caracteres por token, la cota de `RequestSizeGuard` calibrada con la cuenta real (1,59–1,82 observados; con 2 la estimación quedaba por debajo del consumo). `liveScenarioBench` imprime llamadas, tokens estimados a 1,5 caracteres por token y costo antes de empezar y se niega a pasar de sus topes (regla W7 de M16): con el banco etiquetado (`-PscenarioDir=src/test/resources/scenarios/bank`, el valor por defecto) `-PmaxCalls` 780 y `-PmaxUsd` 0,10 por defecto; 1 300 llamadas solo al reproducir los cuerpos grabados de otra carpeta, como en M13; desde M16 guarda además las respuestas crudas, sin clave ni cabeceras, en `build/reports/m16/answers.jsonl`. Las dos tareas de Gradle se niegan sin `-PliveJev=true` y sin `TYPESAFE_API_KEY` en el entorno; se ejecutan con `set -a; source .env; set +a` en el mismo shell y `--no-daemon`, para que la clave no quede en un proceso de Gradle.

## 10. Informe final (M13.7)

`DocumentacionParaDesarrollar/Evidencia-0.0.1/Informe-final.md`, creado cuando haya evidencia, con estas secciones y sin afirmaciones sin evidencia:

1. Entorno y versiones (`versiones.txt`, huellas de M01 §1.1, ID de modelo efectivo).
2. Cobertura de la matriz de M13.1: cada fila con su prueba automática y su sección de servidor, y el estado de cada una.
3. Carga: las tablas de §5.2, §5.3 y §5.4, y la capacidad de §5.6.
4. Latencia: percentiles de §6.1 a §6.3 con número de muestras, la observación de §6.4 y la decisión de §6.5.
5. A/B: tabla de §7.2 por escenario y brazo, con dispersión.
6. Calibración: informe del banco (§8.5) y umbrales elegidos con la curva de fiabilidad.
7. Defaults revisados: `timeoutMillis`, `encounterHeartbeatTicks`, `minimumDecisionIntervalTicks`, `maximumIntentDurationTicks`, `maxRequestsPerMinute`, `maxInFlightRequests`, `contingenciesPerMob`, `minimumConfidence`, `tacticSwitchMargin` y pesos de amenaza, cada uno con su evidencia o "sin cambios" (C13).
8. Fallos pendientes y decisiones (DT abiertas).

## 11. Cierre

- Cada fila de §3 tiene evidencia con la plantilla de §2 o está marcada como bloqueada con su causa.
- M13 pasa a `Verificado en servidor` solo con §5, §6, §7 y §8 completos; las llamadas de pago, solo con la aprobación del usuario.

## 12. Aceptación alojada (M14.5)

Subconjunto del guion que se ejecuta en el host del usuario para aceptar la 0.0.1 (plan §13). Cada paso reutiliza pasos ya descritos; el orden de juego y los archivos listos para copiar están en el [paquete de pruebas](paquete-pruebas/Guia-de-pruebas-por-sesiones.md#aceptación-alojada-de-la-001-m145). La evidencia usa la plantilla de §2, en `DocumentacionParaDesarrollar/Evidencia-0.0.1/aceptacion/`, y ningún paso requiere compartir la clave.

| Paso | Qué se comprueba | Pasos del guion |
|---|---|---|
| 1 | Instalación limpia y primer inicio sin errores | M01 1.1, 1.2 y el arranque sin pack de la sesión 1 del paquete (mensajes de M12.4) |
| 2 | Clave por entorno y `check` en `READY` | §6.1 (`/thepredict check`) y M01 12.8, primera parte (**PAGO**, sin tokens) |
| 3 | Boss de ejemplo con Predictor apagado: IA de MythicMobs | `examples/README.txt` paso 5 |
| 4 | Predictor activo: IA gestionada por ThePredict, decisiones de Jev y movimientos observados con `inspect` | M01 9.1–9.5 y M01 12.8, combate de 60 s (**PAGO**) |
| 5 | Dos instancias y dos mundos: aislamiento | §5.2 (escalón de 10 mobs en dos mundos) y M01 10.6 |
| 6 | Pérdida de servicio simulada sin tocar la clave y vuelta a `JEV` | M01 12.8, corte de red de 30 s (**PAGO**) |
| 7 | `/mm reload` y carga de chunks: metas reinstaladas | M01 4.3, 4.4, 9.7, 9.8, 10.3 y 10.7 |
| 8 | Desactivar el plugin: mobs con su IA de MythicMobs | Paso nuevo, abajo |

**Paso 8.** Con el servidor parado, sacar de `plugins/` el JAR de ThePredict (y el pack de verificación de M01 §1.4, cuyas skills usan el vocabulario `predictor*`, que sin ThePredict MythicMobs no reconoce), dejando dos `ThePredictGuardian` vivos con `Despawn: false`. Arrancar sin clave. Esperado: MythicMobs carga los mobs y estos persiguen y golpean con su IA de MythicMobs; ninguna línea de ThePredict. Copiar `grep -nE 'ERROR|SEVERE|ThePredict|Predictor' logs/latest.log`, con el texto literal de cualquier línea de MythicMobs sobre la clave `Predictor`. Devolver después el JAR y los archivos.

La 0.0.1 queda aceptada cuando el mismo boss cumple los ocho pasos con evidencia. Mientras falte alguno, el artefacto se publica como **candidato pendiente de integración** (M14.6).

## 13. Duelo de mobs controlados (M18.6)

Sesión nueva de M18: dos duelistas iguales en todo salvo quién decide, en un mundo propio `duel`, primero sin clave (80 rondas, costo 0) y después, con la aprobación del usuario, con clave (30 rondas). El orden exacto, con el comando, lo esperado y qué copiar, está en la [sesión 18 del paquete de pruebas](paquete-pruebas/Guia-de-pruebas-por-sesiones.md#sesión-18-duelo-de-mobs-controlados-sin-clave-m18) y su [parte con clave](paquete-pruebas/Guia-de-pruebas-por-sesiones.md#sesión-18-con-clave-duelo-con-jev-m18-paso-184). Archivos: `paquete-pruebas/archivos/13-duelo` (mobs, skills y perfiles del duelo). Requiere un gestor de mundos (los comandos usan Multiverse-Core, que no está entre los JAR comprobados con javap; con otro, sus equivalentes de crear y teletransportar).

**18.0 Preparación** (una vez por sesión, en el mundo `duel`):

```text
/mm reload
/mv create duel normal -t FLAT
/mv tp duel
/gamerule spawn_mobs false
/gamerule advance_time false
/time set noon
/fill -21 64 -21 21 70 21 minecraft:barrier hollow
/fill -20 64 -20 20 64 20 minecraft:grass_block
/scoreboard objectives add duel dummy "Duel"
/scoreboard objectives setdisplay sidebar duel
/scoreboard players reset red_wins duel
/scoreboard players reset blue_wins duel
/tp @s 0.5 67 0.5
/gamemode spectator
```

Desde la consola, con `execute in` (clave de dimensión `minecraft:duel`, la que Paper da a un mundo llamado `duel`; comprobado con las fuentes de `WorldCreator` y javap de `ExecuteCommand`, `GameRuleCommand` y `FillCommand` del servidor 26.2):

```text
execute in minecraft:duel run gamerule spawn_mobs false
execute in minecraft:duel run gamerule advance_time false
execute in minecraft:duel run time set noon
execute in minecraft:duel run fill -21 64 -21 21 70 21 minecraft:barrier hollow
execute in minecraft:duel run fill -20 64 -20 20 64 20 minecraft:grass_block
```

**Una ronda** (limpieza sin `killall`, después los dos duelistas del brazo; `/mm mobs spawn <tipo> <n> <mundo>,<x>,<y>,<z>` comprobado con javap de `SpawnCommand`):

```text
/mm mobs kill DuelRed
/mm mobs kill DuelRedClassic
/mm mobs kill DuelBlue
/mm mobs kill DuelBlueClassic
/mm mobs spawn <rojo> 1 duel,0.5,65,<z rojo>
/mm mobs spawn <azul> 1 duel,0.5,65,<z azul>
```

| Paso | Acción | Esperado | Costo |
|---|---|---|---|
| 18.1 | Seguridad: un segundo jugador en `SURVIVAL` dentro de la arena durante una ronda de A | No recibe daño ni es objetivo; `inspect` del duelista de ThePredict: solo `entity_1` | 0 |
| 18.2 | Visual: una ronda de C en difícil | Brillo toda la ronda, barras con la táctica, partículas por señal; `DuelLeap` no saca a nadie de la caja; ningún zombi de refuerzo (**DT-40**) | 0 |
| 18.3 | Matriz local (radio 30: las esquinas de la caja de 41 × 41 quedan a ≈ 29 bloques del centro): `/thepredict duel start local-<fecha> duel_red duel_blue duel 0 65 0 30` y 80 rondas en el orden A, B, C, D, D, C, B, A…, lados intercambiados cada dos rondas; `/thepredict duel stop` | `summary.md` con los cuatro brazos; D ≈ 50 % (con 20 rondas solo detecta sesgos de 75/25 o más) | 0 |
| 18.4 | Con clave (**PAGO**, con aprobación): `/thepredict duel start jev-<fecha> duel_red duel_blue duel 0 65 0 30`; 30 rondas (A, B, C, C, B, A…); `/thepredict duel status` cada 5 rondas | `requests` e `input tokens` acumulados en `duel status`; parar si `input tokens` pasa de 14 000 000 | Ver la tabla |
| 18.5 | Copia de vuelta | La carpeta `plugins/ThePredict/duels/<etiqueta>-<UTC>/`, las salidas de `status` antes y después y el registro filtrado en `Evidencia-0.0.1/<fecha>-duelo/`; el `grep -c` de UUID sobre las líneas `body` da 0 | 0 |

Copia de vuelta (18.5):

```bash
cp -R "$BASE/plugins/ThePredict/duels/<etiqueta>-<UTC>" "$EVID/"
grep -E 'DUEL|Outcome|Tactical execution|Duel recording' "$BASE/logs/latest.log" > "$EVID/latest-filtrado.log"
grep '"type":"body"' "$EVID/<etiqueta>-<UTC>/decisions.jsonl" | grep -c -E '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
cd "$REPO" && ./gradlew duelJoin --offline -Pdecisions="$EVID/<etiqueta>-<UTC>/decisions.jsonl"
```

**Costo por ronda con clave** (solicitud de producción de M16.2: 1 mob, 1 entidad, `NOW` + 1 contingencia, 7 opciones, la forma exacta de los perfiles del duelo; estimación W7 a 1,5 caracteres por token y, al lado, con los tokens reales del re-banco de M16.10; los reales salen de `/thepredict duel status` y de `decisions.jsonl`):

| Brazo | Encuentros de ThePredict | Solicitudes por ronda (≤ 1/s; ronda típica 60 s, máximo 90) | `compact` (≈ 1 400 tokens por solicitud a 1,5 c/t; 953 reales) | `full`, el default (≈ 3 204 tokens a 1,5 c/t; 1 884 reales) | `full` + idoneidad (≈ 4 600 a 1,5 c/t) |
|---|---|---|---|---|---|
| A o B | 1 | 60 (máx. 90) | ≈ 0,0035 USD (máx. 0,0053) | ≈ 0,0081 (máx. 0,012); real ≈ 0,0047 | ≈ 0,012 (máx. 0,017) |
| C | 2 | 120 (máx. 180) | ≈ 0,0071 (máx. 0,011) | ≈ 0,016 (máx. 0,024); real ≈ 0,0095 | ≈ 0,023 (máx. 0,035) |
| **Total 18.4** (10 A + 10 B + 10 C) | | ≈ 2 400 (máx. 3 600) | **≈ 0,14 USD (máx. 0,21)**; real ≈ 0,096 | **≈ 0,32 USD (máx. 0,48)**; real **≈ 0,19 (máx. 0,28)** | ≈ 0,46 (máx. 0,70) |

Con el default `full`, el costo esperado es **≈ 0,19 USD** con los tokens reales (≈ 0,32 USD con la cota W7 de 1,5 c/t; máximo ≈ 0,48 con rondas de 90 s). Techo de M18: **0,60 USD**; la sesión se detiene si `input tokens` de `/thepredict duel status` pasa de 14 000 000 (≈ 0,59 USD).

La evidencia usa la plantilla de §2 en `DocumentacionParaDesarrollar/Evidencia-0.0.1/<fecha>-duelo/`, con el informe de la [plantilla del duelo](paquete-pruebas/Plantilla-informe-duelo.md). Las casillas de servidor de M18 solo se marcan con esas salidas pegadas.

## 16. Efectividad de las respuestas (M16.11)

Sesión nueva de M16: ver en combate real qué consiguen las intenciones adoptadas, primero sin costo. El orden de juego exacto, con el comando, lo esperado y qué copiar de cada paso, está en la [sesión 16 del paquete de pruebas](paquete-pruebas/Guia-de-pruebas-por-sesiones.md#sesión-16-efectividad-sin-clave-m16) y su [parte con clave](paquete-pruebas/Guia-de-pruebas-por-sesiones.md#sesión-16-con-clave-efectividad-con-jev-m1611-pasos-162-y-163). Archivos: `paquete-pruebas/archivos/03-guardian` (el guardián de M15.9) y, en `config.yml`, `decision.logDecisions: true` con `decision.questionDetail: compact`.

| Paso | Acción | Esperado | Costo |
|---|---|---|---|
| 16.1 | Sin clave: guardián de M15.9 en modo local contra un jugador, 3 combates de 60 s; `/thepredict status` antes y después; `inspect` durante | `outcomes: level=LOCAL windows=…` en la sección `Effectiveness`; líneas `Outcome LOCAL …` con `dealt` mayor que 0 cuando una skill del guardián golpea (**DT-35**: si una skill daña y `dealt` sigue en 0, el daño de MythicMobs no llega con el lanzador como causante y el listener debe usar `MythicDamageEvent`); `taken` mayor que 0 cuando el jugador le pega; `Outcomes:` en `inspect` | 0 |
| 16.2 | **PAGO.** Igual con clave, `decision.logDecisions: true`, `decision.questionDetail: compact` | `outcomes:` por nivel `JEV` y, si alguna contingencia se dispara, `ALTERNATIVE`; líneas `Outcome …` en el registro; `contingencies: asked= fired= effective=` | 3 combates × ≈ 60 solicitudes × ≈ 1 400 tokens (cota) ≈ 252 000 tokens ≈ **0,011 USD** |
| 16.3 | **PAGO.** §7 (A/B) con las medidas de `outcomes:` añadidas en §7.2 | Tabla de §7.2 con la efectividad por nivel del brazo B | Con `compact`, ≈ 3 600 solicitudes × ≈ 1 400 tokens ≈ 5,0 M tokens ≈ **0,21 USD** (con `standard`, ≈ 0,43 USD) |

La evidencia usa la plantilla de §2 en `DocumentacionParaDesarrollar/Evidencia-0.0.1/<fecha>-efectividad/`. La casilla de servidor de M16.8 ("ventanas con daño y alcance coherentes con lo observado") solo se marca con esas líneas pegadas.

## 17. Participantes y objetivos como entidades (M17.10)

Sesión nueva de M17, sin clave y sin costo: comprobar que los defaults no cambian nada y que la política de participantes funciona en juego. El orden exacto, con el comando, lo esperado y qué copiar, está en la [sesión 17 del paquete de pruebas](paquete-pruebas/Guia-de-pruebas-por-sesiones.md#sesión-17-participantes-y-objetivos-como-entidades-m17). Archivos: `paquete-pruebas/archivos/03-guardian` y `paquete-pruebas/archivos/12-entidades`.

| Paso | Acción | Esperado | Costo |
|---|---|---|---|
| 17.1 | Defaults: guardián contra un jugador con un zombi vanilla cerca; `/thepredict inspect nearest` | Solo `player_1`; `perceived players 1` | 0 |
| 17.2 | Perfil `tp_monster_hunter_ai` (`Entities.Allow: ['kind:MONSTER']`, `Faction: hunters`, `AITargetSelectors` `- 1 nearestOtherFactionMonsters`) contra un zombi | El mob apunta al zombi; `inspect` muestra `entity_1 (zombie)` con salud, tendencia (**DT-36**) y alcance (**DT-37**: `outside target reach` según la caja vanilla, cada eje horizontal por separado) | 0 |
| 17.2b | Lobo `TP_TagWatcher` (`Entities.Allow: ['tag:mipack:presas']`, datapack de `12-entidades` con `minecraft:rabbit`) persiguiendo ovejas; la etiqueta pasa a `minecraft:sheep`, `/minecraft:reload` y `/mm reload` | Oveja no admitida (`Target: none`) hasta `/mm reload`; después, `Target: entity_1 (sheep)` si Paper refleja la etiqueta recargada (**DT-39**) | 0 |
| 17.3 | Dos mobs de ThePredict con `Faction: red` y `Faction: blue`, `AITargetSelectors: - 1 nearestSpecificFaction{f=blue}` y a la inversa | `/thepredict status`: `Encounters: 2 active, …`; cada `inspect`, un `encounter <id>` distinto y `Target: entity_1 (zombie) …` | 0 |
| 17.4 | Un jugador en `SURVIVAL` entra en la zona de 17.3 con `PlayerGameModes: []` en los perfiles | Nunca es participante ni objetivo de ThePredict (`inspect`) | 0 |
| 17.5 | Un esqueleto vanilla admitido tensa el arco; un zombi con `{equipment:{offhand:{id:"minecraft:shield",count:1}}}` | `DRAWING_BOW` en `inspect`. El zombi nunca levanta el escudo (`cues []`): ni vanilla ni MythicMobs 5.13.1 lo hacen. `RAISING_SHIELD` y `BLOCKING` (**DT-38**) solo con una configuración propia que llame `startUsingItem(EquipmentSlot.OFF_HAND)` | 0 |
| 17.6 | 10 mobs y 30 entidades admitidas, `perceiveNonTargets` falso y verdadero | `tick_work` de `status` en los dos casos, p95 por debajo de 2 000 µs | 0 |

La evidencia usa la plantilla de §2 en `DocumentacionParaDesarrollar/Evidencia-0.0.1/<fecha>-entidades/`. Las casillas de servidor de M17.10 solo se marcan con esas salidas pegadas.

