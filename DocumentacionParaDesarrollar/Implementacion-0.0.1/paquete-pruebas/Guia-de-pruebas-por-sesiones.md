# Guía de pruebas por sesiones de juego (0.0.1)

> Paquete de pruebas del usuario ([índice](README.md)). Reagrupa por **sesión de juego** los pasos del [guion de M01](../M01-Guion-de-servidor.md) (citado como **M01 n.m**) y del [guion de servidor 0.0.1](../Guion-servidor-0.0.1.md) (citado como **G13 §n**), sin cambiar su contenido: cada paso remite a su número de guion, que sigue siendo la referencia completa. Si esta guía y un guion no coinciden, manda el guion y hay que reportarlo. Excepción de P-AIConfig: la configuración vigente es `plugins/MythicMobs/AIConfig/` (`config.yml` y `Profiles/`). El guion de la 0.0.1 todavía nombra la ubicación antigua y no se reescribe aquí.
>
> **Fase 1 (sesiones 1 a 13 y 15 a 29, y la sesión AIConfig, sin clave): sin clave, costo cero.** Las sesiones 15 a 29 llevan el número de su módulo (M15 a M29); no hay sesión 14. Algunos pasos de las sesiones 21 a 29 son de pago y lo dicen en su fila: no se ejecutan sin aprobación. La sesión AIConfig comprueba la carpeta nueva y la propuesta de migración; está pendiente de servidor. **Fase 2 (sesiones K1 a K5 y las partes con clave de las sesiones 16 y 18): con clave**, solo cuando decidas gastar; cada sesión dice su costo estimado antes de empezar.
>
> Qué copiar y cómo: [Cómo reportar resultados](Como-reportar-resultados.md). Qué cierra cada paso: [Checklist de DT y casillas](Checklist-DT-y-casillas.md). Los archivos listos para copiar están en [`archivos/`](archivos/) y una prueba automática (`UserTestPackTest`) los valida con el cargador real de perfiles y el vinculador de ThePredict sobre dobles de MythicMobs; valida YAML y nombres, no el texto.

## Reglas de todas las sesiones

- **La clave nunca se pega** en el chat, en la consola, en un informe ni en un comando. Nunca `cat .env` ni `echo $TYPESAFE_API_KEY`. En la fase 1 no hay clave en ningún sitio: arranca el servidor desde un terminal en el que no se haya cargado `.env`.
- **El EULA lo aceptas tú**, editando `eula.txt`.
- **Copia la salida literal** (consola y chat), no un resumen. Sustituye los nombres de jugadores por `A`, `B`, `C`, `D` antes de enviar nada.
- **Cada caso dos veces** (regla de M01). Si un paso falla por sintaxis de un archivo del paquete, corrígelo, anótalo y repite.
- **Espera 1–2 s** antes de `/thepredict inspect` tras cambiar algo en juego: las capturas ocurren en cada evaluación del encuentro (cada 20 ticks o antes).
- **Limpieza al empezar cada sesión:** `/kill @e[type=!minecraft:player]` (todos los mobs del paquete tienen `Despawn: false` y sobreviven a reinicios).
- Lo que no puedas observar se anota como **no ejecutado** o **bloqueado**, nunca con un resultado supuesto.

Variables de terminal que usan todas las sesiones (ajusta las rutas si hace falta):

```bash
REPO=~/Desktop/Akyrion/ThePredict
CAND=~/Desktop/Akyrion/ThePredict-candidato
BASE=~/ThePredict-verificacion/26.2-129
JAVA=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/java
PACK="$CAND/DocumentacionParaDesarrollar/Implementacion-0.0.1/paquete-pruebas/archivos"
```

Arrancar el servidor (siempre igual; `N` es el número de arranque):

```bash
cd "$BASE" && "$JAVA" -Xms2G -Xmx2G -jar paper-26.2-129.jar --nogui 2>&1 | tee start-N.log
```

Cuentas: la mayoría de sesiones necesita **una** (A). Las sesiones 4 (§5), 8 y 10 necesitan **dos** (A y B); 8 (paso 11.11) pide **tres** y 10 (paso 13.7) **cuatro**; la carga (sesión 11) pide dos por mundo. Con menos cuentas se ejecuta lo posible y se anota el resto como no ejecutado.

---

# Fase 1. Sin clave (costo cero)

## Sesión 1. Servidor limpio e instalación nueva

**Cierra:** DT-00 (parte), M01.1, M12.4 (arranque limpio), M14.5 paso 1. **Guiones:** M01 1.1, 1.2, 1.5.

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 1.1 | Preparar la carpeta con el JAR candidato entregado (M01 1.1). **No compiles el árbol del proyecto**: no está auditado. `mkdir -p "$BASE/plugins"`; `cp "$REPO/VersionsOfJars/paper-26.2-129.jar" "$BASE/"`; `cp "$REPO/VersionsOfJars/MythicMobsPremium-5.13.1-SNAPSHOT.jar" "$BASE/plugins/"`; `cp "$CAND/ENTREGA/ThePredict-0.0.1-rc1.jar" "$BASE/plugins/"`; `shasum -a 256 "$BASE/plugins/ThePredict-0.0.1-rc1.jar"` | La huella de `ThePredict-0.0.1-rc1.jar` es `b1a731a96c7a160b5d250813a45f7514c90d232d6eea00a8d1201c90f9c4690e` (la misma que `$CAND/ENTREGA/ThePredict-0.0.1-rc1.jar.sha256`). Si no coincide, para | La huella |
| 1.2 | `cd "$BASE" && shasum -a 256 paper-26.2-129.jar plugins/*.jar` | `b1d8f6bfa1b6101fa8e947b53041cb3bdf5540e7b83b6547ca19ba7edefeb083` para Paper y `0e9ffc1362d7831e3cb4875084d017939ac96c2071721b4b22cc70370ac0b733` para MythicMobs; la de `ThePredict-0.0.1-rc1.jar` debe ser `b1a731a96c7a160b5d250813a45f7514c90d232d6eea00a8d1201c90f9c4690e` (paso 1.1) | Las tres huellas |
| 1.3 | Primer arranque (M01 1.2): `cd "$BASE" && "$JAVA" -Xms2G -Xmx2G -jar paper-26.2-129.jar --nogui 2>&1 \| tee first-start.log` | Paperclip descarga y verifica el servidor de Mojang y se detiene pidiendo el EULA | `first-start.log` (sin datos personales) |
| 1.4 | **Tú**: `eula=true` en `eula.txt`; en `server.properties`, `level-type=minecraft\:flat` | — | Una línea: "EULA aceptado por el administrador el <fecha>" |
| 1.5 | Arranque 2, **sin ningún archivo del paquete** (instalación limpia) | Líneas de ThePredict: `Jev not configured: set TYPESAFE_API_KEY or jev.apiKey. MythicMobs works normally. Next: set the key and restart the server, then run /thepredict status.`, `No profiles: copy examples/ or create a .yml file in plugins/MythicMobs/AIConfig/Profiles/. Next: read plugins/MythicMobs/AIConfig/Profiles/README.txt.` y `Profile catalog SETUP_PENDING (revision <n>, 0 profiles read, 0 active, 0 errors, 0 warnings): configuration pending; copy AIConfig/Profiles/example.yml.example to a .yml file`; ningún `ERROR` ni `SEVERE` de ThePredict (las líneas `MythicMobs resolved …` solo aparecen cuando alguna skill usa el vocabulario: sesión 2). MythicMobs no se queja de la carpeta `AIConfig` (DT-100, sesión AIConfig) | `grep -nE 'ERROR\|SEVERE\|ThePredict' start-2.log` |
| 1.6 | Entrar como operador A | Aviso `[ThePredict] Jev is UNCONFIGURED. Run /thepredict status for details.` | El aviso |
| 1.7 | `/thepredict status` | Secciones `Environment`, `Service` (`State: Jev UNCONFIGURED (no TypeSafe API key)`, `Model: configured jev-1.13.0; last answer none yet`), `Catalog` (`Profiles: SETUP_PENDING (revision <n>, 0 profiles read, …): configuration pending; …` y `Migration: none pending`), `Sessions`, `Encounters`, `Budget` (`Server: 0/600 requests in 60 s, 0/8 in flight`), `Consumption`, `Latency …`, `Errors`, `Counters` | Toda la salida |
| 1.8 | `ls "$BASE/plugins/MythicMobs/AIConfig" "$BASE/plugins/MythicMobs/AIConfig/Profiles" "$BASE/plugins/ThePredict/examples"` | `config.yml`, `Profiles/README.txt`, `Profiles/example.yml.example`, `examples/README.txt` y las carpetas `examples/MythicMobs` y `examples/AI`. El maestro no está en `plugins/ThePredict/` | La lista |
| 1.9 | `versions.md` en `$BASE` con `/version`, `/version MythicMobs`, `"$JAVA" -version` y las huellas de 1.2. `stop` | — | `versions.md` |

## Sesión 2. Pack de verificación, dos arranques y permiso de depuración

**Cierra:** DT-00, DT-02, DT-03, DT-13 (aviso), DT-17 (parte negativa), M01.1, M01.2 (casilla de permiso), M01.3. **Guiones:** M01 1.4, 1.5, 2.1, 3.1, 3.5, 1.3.

Preparación (servidor parado): `cp -R "$PACK/01-verificacion/." "$BASE/"` (copia `plugins/MythicMobs/Mobs/ThePredictVerification.yml`, `plugins/MythicMobs/Skills/ThePredictVerification.yml` y `plugins/MythicMobs/AIConfig/Profiles/ThePredictVerification.yml`). **Todavía sin `permissions.yml`.**

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 2.1 | Arranque 3 | `Profile catalog LOADED (revision <n>, 5 profiles read, 5 active, 0 errors, <w> warnings)`; sin `Unknown condition`/`Invalid condition` para `predictormove`, `PredictorMove`, `PREDICTORMOVE` ni `Unknown mechanic` para `predictordecide`; líneas `MythicMobs resolved …` (M01 1.5, 3.1). En la verificación local del paquete la línea es `… 5 profiles read, 5 active, 0 errors, 0 warnings` | `grep -nE 'ERROR\|SEVERE\|ThePredict\|Unknown\|Invalid' start-3.log` |
| 2.2 | `grep -n -i 'deprecat' start-3.log` (M01 3.5) | Si aparece un aviso de API obsoleta de MythicMobs por `Placeholder.meta`, su texto literal | El texto o "no aparece" |
| 2.3 | Como operador A (sin `thepredict.debug`), en `CREATIVE`: `/mm m spawn TPV_Zombie 1`; `/thepredict debug intent nearest hold`; escribir `/thepredict ` y pulsar Tab (M01 2.1) | `You need the thepredict.debug permission to use debug tools`; el autocompletado ofrece `status`, `check` e `inspect`, sin `debug`; `TPV_ShowIntent` sigue mostrando `state=base` | Las tres cosas |
| 2.4 | Espera 10 s con `TPV_Zombie` vivo y `/thepredict status` (M01 3.6) | Ningún error de MythicMobs; en `Counters`, `Context: … predictordecide pending 0, dropped 0` | La línea `Context` |
| 2.5 | `stop`; arranque 4 (segundo arranque consecutivo, M01 1.5); `/thepredict status` | Igual que 2.1; sin errores nuevos | `grep` de `start-4.log` y `status` |
| 2.6 | `stop`; `cp "$PACK/02-permisos/permissions.yml" "$BASE/"` (M01 1.3) | — | — |

## Sesión 3. Herramientas de depuración y vocabulario

**Cierra:** DT-02, DT-03, DT-06 (parte), DT-11, DT-13, DT-17, M01.2, M01.3, M04 (placeholders y recarga). **Guiones:** M01 2.2–2.6, 3.2–3.4. Jugador A en `CREATIVE`.

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 3.1 | Arranque 5; `/kill @e[type=!minecraft:player]`; `/mm m spawn TPV_Zombie 1`; espera 2 s; `/thepredict debug goals nearest` (M01 2.2) | Cabecera `Goals of <uuid> generation 0: <n>` con la UUID completa, una línea por meta con clave y tipos (`running` en las que corren) y `Movement: …` | Toda la salida (anota la UUID) |
| 3.2 | `/thepredict debug intent nearest hold 400` (M01 2.3, 3.2) | `Debug intent hold (HOLD, LOCAL) on <uuid corta> generation 0 for 400 ticks; it never sends a request to Jev`; en ≤5 s `TPV_ShowIntent` muestra `tactic=hold movement=hold level=local confidence=1.00 state=controlled`; los tres mensajes `[TPV] predictormove passed`, `[TPV] PredictorMove passed` y `[TPV] PREDICTORMOVE passed`; `/thepredict inspect nearest` marca la intención con `[debug]` | Chat e `inspect` |
| 3.3 | `/thepredict debug release nearest` (M01 2.4) | `Released <uuid corta> to level 4; the fallback chain decides again within 2 ticks`; `TPV_ShowIntent` vuelve a `tactic=none … level=base … state=base`; `inspect` muestra `Intent: none; level 4 runs the base AI of the goal set` | Chat e `inspect` |
| 3.4 | Consola: `thepredict debug goals <uuid>`, `thepredict debug intent <uuid> approach` y `thepredict debug goals nearest` (M01 2.5) | Las dos primeras funcionan como en jugador; la tercera responde `'nearest' needs a player; use an entity UUID from the console` | Salida de consola |
| 3.5 | `/thepredict debug intent nearest dance_around`, `… intent nearest hold 7000`, `/thepredict debug goals 1-2-3-4-5`, `/thepredict debug cast nearest TPV_Strike` (M01 2.6) | `Debug intent refused for <uuid corta>: … dance_around …`, `Ticks must be a whole number between 1 and 6000. Run /thepredict debug for usage`, `Mob must be 'nearest' or an entity UUID, was '1-2-3-4-5'. Run /thepredict debug for usage` y `Unknown debug action 'cast'; use intent, release, goals. Run /thepredict debug for usage`; nada cambia | Las cuatro respuestas |
| 3.6 | Con el mob vivo: `/mm reload`; repetir 3.2 (M01 3.3) | Mismo resultado; nuevas líneas `MythicMobs resolved …` en consola; la recarga sube la generación y termina la intención de depuración | Consola y chat |
| 3.7 | Placeholders (M01 3.4): `/mm m spawn TPV_ZombiePlain 1` y observar su `TPV_ShowIntent`; observar el de `TPV_Zombie` sin intención, con `debug intent nearest hold 400`, tras `debug release nearest` y tras `/mm reload` | `none/none/none/none/none` sin sesión (Plain); `none/none/base/0.00/base` con sesión sin intención; `hold/hold/local/1.00/controlled` con la intención; tras recargar, `Registered <caster.predictor.*> placeholders after MythicPlaceholdersLoadEvent` en consola | Las cuatro líneas `[TPV] …` y la de consola |

Si un placeholder aparece sin sustituir (`<caster.predictor.tactic>` literal), prueba `/mm reload` y anótalo (DT-13).

**Ejemplos de vocabulario de la guía del autor** (cierran M04.7: "cada ejemplo tiene una prueba en servidor registrada"). Preparación: `cp -R "$PACK/11-vocabulario/." "$BASE/"` (el mob `VocabularyGuardian`, sus skills y su perfil, idénticos a la sección 10 de la [guía del autor](../../../Guias/Guia-del-autor.md#10-vocabulario-de-thepredict-en-mythicmobs)); `/mm reload`.

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 3.8 | `/thepredict status` | `Profiles: LOADED (revision <n>, 6 profiles read, 6 active, 0 errors, <w> warnings)` (local: `6 profiles read, 6 active, 0 errors, 0 warnings`) | La línea `Profiles` |
| 3.9 | A en `CREATIVE`: `/mm m spawn VocabularyGuardian 1`; esperar 4 s | Cada 2 s el mensaje `none (0.00)` (placeholders sin intención) | El chat |
| 3.10 | `/thepredict debug intent nearest retreat_and_defend 200` | El rugido de `~onSignal:PREDICTOR_RETREAT_AND_DEFEND` una vez; el mensaje pasa a `retreat_and_defend (1.00)`; partículas `crit` de `GuardianDefendAura` cada segundo mientras dura | Chat y observación |
| 3.11 | A en `SURVIVAL` a 3 bloques durante 10 s; después `Predictor: false` en `plugins/MythicMobs/Mobs/VocabularyGuardian.yml`, `/mm reload` y otros 10 s; al terminar, `Predictor: true` y `/mm reload` | Con `Predictor: true` el mob está en control local (`predictorcontrol` verdadero) y `GuardianSlam` no golpea; con `Predictor: false` no hay sesión (`predictorcontrol` falso) y `GuardianSlam` golpea cada 3 s (`damage{a=6}`) además del ataque normal | Observación (vida de A) |
| 3.12 | Golpear al mob en `SURVIVAL` (dispara `predictordecide @self ~onDamaged`) y `/thepredict status` | Ningún error de MythicMobs; `Context: … predictordecide pending 0, dropped 0` | La línea `Context` |
| 3.13 | Quitar el ejemplo: `/kill @e[type=!minecraft:player]`; `rm "$BASE/plugins/MythicMobs/Mobs/VocabularyGuardian.yml" "$BASE/plugins/MythicMobs/Skills/VocabularyGuardianSkills.yml" "$BASE/plugins/MythicMobs/AIConfig/Profiles/VocabularyGuardian.yml"`; `/mm reload` | Vuelve a `5 profiles read, 5 active` | La línea `Profiles` |

## Sesión 4. Metas, movimiento y metas de Paper

**Cierra:** DT-01, DT-04, DT-05, DT-06, DT-07, DT-08, DT-09, DT-10, DT-15, DT-16, DT-18, DT-19, M01.4, M01.5, M01.6, M01.7. **Guiones:** M01 §4, §5, §6, §7. Jugador A en `CREATIVE` salvo donde se indica; §5 pide un segundo jugador B.

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 4.1 | `/kill @e[type=!minecraft:player]`; M01 4.1: `/mm m spawn TPV_Zombie 1`, 2 s, `debug goals nearest` e `inspect nearest` | Solo `float`, `fleeConditional` ×2, `moveWithinConditional`, `moveTowardsTargetConditional`, `goToSpawnLocation`, `thepredict:flank`, `thepredict:hold`, `thepredict:owner` y la IA base desplazada +10 con `lookAtTarget`; `inspect`: `Goals: installed for revision <n>` | Ambas salidas |
| 4.2 | M01 4.2: igual con `TPV_ZombieCustomAI` | Metas de ThePredict y su IA base desplazada +10, sin `meleeAttack` duplicado | Salida |
| 4.3 | M01 4.3: alejarse hasta descargar el chunk de `TPV_Zombie` y volver; 2 s | `inspect`: generación siguiente e `installed for revision <n>`; `debug goals` igual que 4.1 | Salidas |
| 4.4 | M01 4.4 y 4.10: `/mm reload` (2 s) dos veces seguidas, dos rondas | `inspect` con la revisión nueva; una sola `thepredict:owner`, `thepredict:hold` y `thepredict:flank` | Salidas |
| 4.5 | M01 4.5–4.7: anotar UUID de `TPV_Zombie` y `TPV_ZombieCustomAI`; `Predictor: false` en ambos en `ThePredictVerification.yml`; `/mm reload`; `thepredict debug goals <uuid>` de cada uno. Después devolver `Predictor: true` y `/mm reload` | Ninguna clave `thepredict:*`; las metas que deja `reapplyAIGoalSelectors` con `clear` (CustomAI) y sin él (TPV_Zombie); comparar metas `TARGET` con las de 4.1; copiar literalmente las claves de las metas de MythicMobs | Salidas completas |
| 4.6 | M01 4.8: `/thepredict status` (línea `tick_work`); `/mm m spawn TPV_Zombie 1` y a los 2 s `status`; `/mm m spawn TPV_Zombie 30` y a los 2 s `status` | `tick_work` con p50, p95, p99 y `max` con 1 y con 30 instalaciones (10 por tick) | Las tres líneas `tick_work` |
| 4.7 | M01 4.9: `/mm m spawn TPV_NoPriority 1`; su UUID con `/tellraw @s {"selector":"@e[type=minecraft:zombie,sort=nearest,limit=1]"}` (Mayús+clic en el nombre); `thepredict debug goals <uuid>` con A en `SURVIVAL` cerca | ¿Instala las cuatro líneas sin prioridad? ¿Cuál queda `running`? | Salida |
| 4.8 | M01 4.11: `/mm m spawn TPV_Piglin 1`; `debug goals nearest` e `inspect nearest` | `inspect`: `Goals: not installed (MythicMobs keeps its AI)`; `debug goals` con las metas propias del piglin | Salidas |
| 4.9 | `/kill @e[type=!minecraft:player]`. M01 §5 (casos 5.1–5.9) con `TPV_Zombie`, A inmóvil y B que se acerca y aleja; intenciones con `debug intent nearest <táctica> 600`; distancia cada 10 ticks con la línea `Movement: … target … blocks` de `debug goals` | Las preguntas de cada caso de M01 §5 | Distancias y observación por caso |
| 4.10 | M01 6.1–6.2: `debug intent nearest hold 400`, A en movimiento | No se desplaza y sigue mirando; `thepredict:hold … running` y `lookAtTarget … running`; ninguna meta base `running` | `debug goals` |
| 4.11 | M01 6.3: con 6.1 activo y A en `SURVIVAL` a 6–10 bloques: `debug release nearest` e inmediatamente `debug goals nearest` dos veces | Una meta base o local `running` en la primera lectura posible | Las dos lecturas |
| 4.12 | M01 6.4: A en `SURVIVAL` con escudo levantado 2 s antes: `debug intent nearest flank_and_strike 400` | Alcanza un lateral; `Movement: … path yes` durante el recorrido | `debug goals` |
| 4.13 | M01 6.6: tras 4.4, `debug goals nearest` e `inspect nearest` | Una sola `thepredict:owner`; misma revisión sin reinstalaciones repetidas | Salidas |
| 4.14 | M01 §7: `/spark profiler start --thread *`; `debug intent nearest retreat 600`, luego `keep_distance` y `approach` (30 s cada una); `/spark profiler stop --save-to-file`; repetir con `TPV_ZombieAsync` y los casos 5.1, 5.4 y 5.5. **Nunca sin `--save-to-file` ni abrir el visor web sin tu aprobación** | En el archivo guardado, en qué hilos aparece `PredictorMoveCondition` | Nombre de los hilos (no el archivo) |

## Sesión 5. Señales, placeholders y trigger

**Cierra:** DT-11, DT-12, M01.8. **Guiones:** M01 §8. Preparación: servidor parado, `decision.logDecisions: true` en `plugins/MythicMobs/AIConfig/config.yml`; arrancar.

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 5.1 | `/kill @e[type=!minecraft:player]`; `/mm m spawn TPV_Zombie 1`; `debug intent nearest approach 100` y después `debug intent nearest hold 400` (M01 8.1) | ¿Aparece `[TPV] onSignal PREDICTOR_HOLD fired`? ¿También `[TPV] onSignal predictor_hold fired`? | El chat |
| 5.2 | A en `SURVIVAL` a 2 bloques: `debug intent nearest hold_and_strike 400` (M01 8.4) | Línea de consola `… hold_and_strike choice strike: NOT_ELIGIBLE` y ningún `[TPV] TPV_Strike executed` | Consola y chat |
| 5.3 | `debug intent nearest press_and_strike 400`; antes de 5 s `debug intent nearest approach 20` y `debug intent nearest press_and_strike 400` (M01 8.5) | Primera: `choice strike: CAST` y `[TPV] TPV_Strike executed`. Segunda: `Debug intent refused … not eligible at the last capture: its skill was on its native cooldown or it needs a target, …` o `choice strike: NOT_ELIGIBLE`; nunca un segundo `executed` | Consola y chat |
| 5.4 | A en `CREATIVE`; consola: `thepredict debug intent <uuid> press_and_strike 100` (M01 8.6) | Rechazo por no elegible, o se aplica y sale `choice strike: NOT_ELIGIBLE` | Consola |

Al terminar, `decision.logDecisions: false` si no vas directo a la sesión 6.

## Sesión 6. El boss de ejemplo y la propiedad de la IA

**Cierra:** M12.5, M05.7, M14.1 (el autor reproduce el ejemplo), M14.5 pasos 3 y 4 (parte local), DT-01, DT-04, DT-05, DT-07, DT-08, DT-15, DT-19. **Guiones:** `examples/README.txt`, M01 §9.

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 6.1 | Seguir **literalmente** `plugins/ThePredict/examples/README.txt` pasos 1–3: `cp "$BASE/plugins/ThePredict/examples/MythicMobs/Mobs/ThePredictGuardian.yml" "$BASE/plugins/MythicMobs/Mobs/"`; `cp "$BASE/plugins/ThePredict/examples/MythicMobs/Skills/ThePredictGuardianSkills.yml" "$BASE/plugins/MythicMobs/Skills/"`; `cp "$BASE/plugins/ThePredict/examples/AI/ThePredictGuardian.yml" "$BASE/plugins/MythicMobs/AIConfig/Profiles/"` | — | — |
| 6.2 | Paso 4: `/mm reload` y `/thepredict status` | `[ThePredict] Profiles: LOADED (revision <n>, 6 profiles read, 5 active, 0 errors, <w> warnings)` (con el pack de la sesión 2; sin él, `SETUP_PENDING (revision N, 1 profiles read, 0 active, 0 errors, 0 warnings): no mob targeted by a profile has Predictor: true; set it in MythicMobs and reload MythicMobs`). En la verificación local del paquete: `6 profiles read, 5 active, 0 errors, 0 warnings` | La línea `Profiles` |
| 6.3 | Paso 5: A en `SURVIVAL`; `/mm m spawn ThePredictGuardian 1` | Con `Predictor: false`, un zombie normal de MythicMobs: persigue y golpea con su ataque (**M14.5 paso 3**) | Observación (y `inspect nearest`: `No bound mob with a session within 16 blocks`) |
| 6.4 | Paso 6: `/kill @e[type=!minecraft:player]`; `cp "$PACK/03-guardian/plugins/MythicMobs/Mobs/ThePredictGuardian.yml" "$BASE/plugins/MythicMobs/Mobs/"` (el mismo mob con `Predictor: true` y `Despawn: false`); `/mm reload`; `/thepredict status` | `[ThePredict] Profiles: LOADED (revision <n>, 6 profiles read, 6 active, 0 errors, <w> warnings)` (local: `6 profiles read, 6 active, 0 errors, 0 warnings`) | La línea `Profiles` |
| 6.5 | Paso 7 y M01 9.1: `/mm m spawn ThePredictGuardian 1`; 2 s; `inspect nearest` y `debug goals nearest` | `Goals: installed for revision <n>; 12 goals …`; metas `float`, `goToSpawnLocation`, dos `fleeConditional`, `moveWithinConditional`, `moveTowardsTargetConditional`, `meleeAttack`, `randomStroll`, `lookAtTarget`, `thepredict:flank`, `thepredict:hold`, `thepredict:owner`; sin `An error occurred while adding an AIGoalSelector` ni `Failed to construct AI pathfinder` | Ambas salidas |
| 6.6 | Pelear 30 s en `SURVIVAL` e `inspect nearest` cada 10 s (**M14.5 paso 4, parte local**) | `Session: ThePredictGuardian, profile thepredict_guardian, ACTIVE, level LOCAL, encounter <n>`; `Intent: <táctica> (<MOVIMIENTO>…)`; en `Budget`, `Server: 0/600 requests in 60 s, 0/8 in flight` | Tres `inspect` y la línea `Server` |
| 6.7 | M01 9.2: `debug intent nearest approach 400` con A a 10 bloques | Se acerca y se detiene hacia 2,5 bloques; `movement goal running yes` mientras se mueve | `debug goals` |
| 6.8 | M01 9.3: `debug intent nearest keep_distance 600`, A se acerca y aleja 30 s | Banda 5–9 sin alternar; `target` cada 10 ticks | Distancias |
| 6.9 | M01 9.4: `debug intent nearest retreat_and_defend 600`; alejar al mob más de 24 bloques del punto de aparición | Se aleja por terreno transitable y `goToSpawnLocation` lo devuelve | Observación |
| 6.10 | M01 9.5: `debug intent nearest hold 400`; después `flank_and_strike` con escudo levantado (2 s antes) y sin escudo | `hold`: quieto mirando (`thepredict:hold` y `lookAtTarget` `running`). Con escudo: termina fuera del ángulo. Sin escudo: `Debug intent refused … not eligible at the last capture: … FLANK was not available (no target, or FLANK without a raised shield to flank)` | Salidas |
| 6.11 | M01 9.6: anotar UUID; `Predictor: false` en `ThePredictGuardian` y en `TPV_ZombieCustomAI`; `/mm reload`; `thepredict debug goals <uuid>` de cada uno; después devolver `Predictor: true` y `/mm reload` | `status`: `Lifecycle: … unbound=` sube; sin `clear`, metas generadas sin `thepredict:*` y la IA base actúa; con `clear`, las del mob | Salidas |
| 6.12 | M01 9.7: `/mm reload` dos veces con el mob vinculado | Cada recarga reinstala una vez (`inspect`); sin claves `thepredict:*` repetidas | `inspect` |
| 6.13 | M01 9.8: 30 `ThePredictGuardian` en un chunk, descargar y cargar | Todos instalados en pocos ticks (`inspect` de varios y `tick_work`) | `tick_work` e `inspect` de 3 mobs |
| 6.14 | M01 9.9: `/mm m spawn TPV_Piglin 1`; `inspect nearest` | `Goals: not installed (MythicMobs keeps its AI)` | `inspect` |
| 6.15 | M01 9.10: `/kill @e[type=!minecraft:player]`; `cp -R "$PACK/04-slime/." "$BASE/"`; `/mm reload`; `/thepredict status`; `/mm m spawn ThePredictGuardianSlime 1`; `inspect nearest` | `Profiles: LOADED (revision <n>, 7 profiles read, 7 active, 0 errors, <w> warnings)` (local: `7 profiles read, 7 active, 0 errors, 0 warnings`); `Goals: not installed (MythicMobs keeps its AI)`; ninguna línea `not valid for mob type` | `status` e `inspect` |
| 6.16 | Quitar el slime: `/kill @e[type=minecraft:slime]`; `rm "$BASE/plugins/MythicMobs/Mobs/ThePredictGuardianSlime.yml" "$BASE/plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardianSlime.yml"`; `/mm reload` | Vuelve a `6 profiles read, 6 active` | La línea `Profiles` |
| 6.17 | M01 9.11: en `plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardian.yml` añadir `- 3 metaInexistente` a `BaseAI`; `/mm reload`; spawn e `inspect nearest`; `/mm reload` otra vez; después quitar la línea y `/mm reload` | Aviso con causa `install incomplete: 1 MythicMobs goals missing`; `inspect` con `not installed`; el mob conserva su IA de MythicMobs; la segunda recarga vuelve a rechazar sin errores por tick; tras corregir, `installed for revision <n>` | Consola e `inspect` |
| 6.18 | Condición desconocida (M03.3, parte de servidor): añadir a `Contexts` de `plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardian.yml` el contexto `broken_native: { Conditions: [ 'thisconditiondoesnotexist' ], Priority: 80, Tactics: [hold] }`; `/mm reload`; esperar 1 s; `/thepredict status`; después quitarlo y `/mm reload` | Un error del catálogo con archivo, perfil y contexto: `… Contexts.broken_native.Conditions: MythicMobs rejected the condition 'thisconditiondoesnotexist'; context 'broken_native' is disabled` o `… no plugin resolved the condition 'thisconditiondoesnotexist'; context 'broken_native' is disabled` (anotar cuál); el perfil sigue activo sin ese contexto | Consola y `Profiles` |
| 6.19 | Perfil roto (M03.5): en `plugins/MythicMobs/AIConfig/Profiles/ThePredictVerification.yml`, cambiar `retreat: { Movement: RETREAT }` de `tpv_zombie` por `retreat: { Movement: SIDEWAYS }`; `/mm reload`; `/thepredict status`; después deshacerlo y `/mm reload` | `Profiles: INVALID (revision <n>, …): first error AIConfig/Profiles/ThePredictVerification.yml: tpv_zombie.Tactics.retreat.Movement: unknown movement 'SIDEWAYS'`; el resto de perfiles sigue activo; la revisión crece en cada recarga | La línea `Profiles` y la consola |

## Sesión 7. Ciclo de vida

**Cierra:** DT-20, DT-21, M06 (escenarios de servidor), M05.4, M14.5 paso 7 (parte). **Guiones:** M01 §10. Tras cada paso copia de `/thepredict status` las líneas `Bound mobs: …` y `Lifecycle: …`, y la cabecera de `debug goals nearest` si el mob sigue vivo.

Preparación: `/kill @e[type=!minecraft:player]`; `cp -R "$PACK/05-temporal/." "$BASE/"` (mob `ThePredictGuardianTemp` con `Despawn: true` y su perfil); `/mm reload`; `/thepredict status` debe decir `Profiles: LOADED (revision <n>, 7 profiles read, 7 active, 0 errors, <w> warnings)` (local: `7 profiles read, 7 active, 0 errors, 0 warnings`).

| # | Acción exacta | Esperado exacto |
|---|---|---|
| 7.1 | M01 10.1: dos `ThePredictGuardian` vivos; `stop`; arrancar; `status` al entrar | `2 active` en pocos ticks, sin avisos |
| 7.2 | M01 10.2: `/mm m spawn ThePredictGuardian 1` y en el mismo segundo `debug goals nearest` dos veces | La sesión aparece un tick después, `generation 0` |
| 7.3 | M01 10.3: descargar el chunk y volver | Al descargar `1 suspended` y `ended … despawned` y `removed` sin cambios; al volver `1 active` y `generation 1` |
| 7.4 | M01 10.4: matar el mob | `died` sube en 1 |
| 7.5 | M01 10.5: `/mm m spawn ThePredictGuardianTemp 1`; `1 active` más; descargar su chunk | `despawned` sube en 1 (o `removed`: anotar cuál); tras 200 ticks ninguna sesión fantasma |
| 7.6 | M01 10.6: un mob por un portal del Nether y, en otra prueba, `/tp` del mob a otro mundo | `generation` aumenta (anotar cuánto y qué evento) y la sesión sigue `active` |
| 7.7 | M01 10.7: `/mm reload` sin cambios | `generation` +1 (o +2) por mob vivo; ninguna sesión perdida |
| 7.8 | M01 10.8: anotar UUID; `mv "$BASE/plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardian.yml" "$BASE/"`; `/mm reload`; en el tick siguiente `thepredict debug goals <uuid>`; devolver el archivo a `plugins/MythicMobs/AIConfig/Profiles/` y `/mm reload` | `unbound` sube; cabecera `(no session)`; ninguna `thepredict:*` |
| 7.9 | M01 10.9: `debug intent nearest retreat_and_defend 600` con A a 5 bloques; `Predictor: false` en el mob y `/mm reload`; después devolver `Predictor: true` | `unbound` sube en 1; el mob deja de huir y vuelve a perseguir y atacar |
| 7.10 | Quitar el temporal: `rm "$BASE/plugins/MythicMobs/Mobs/ThePredictGuardianTemp.yml" "$BASE/plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardianTemp.yml"`; `/mm reload` | `6 profiles read, 6 active` |
| 7.11 | M01 10.10: `/mm m spawn ThePredictGuardian 250`; `stop` | El apagado termina sin errores ni pausas visibles |

Copiar: las líneas `Bound mobs` y `Lifecycle` tras cada paso y el registro del apagado de 7.11. Después de 7.11, al arrancar otra vez, `/kill @e[type=!minecraft:player]`.

## Sesión 8. Contexto nativo y percepción

**Cierra:** DT-22, DT-23, DT-24, DT-25, M07 (señales y eventos en servidor). **Guiones:** M01 §11. Cuentas: A y B (11.2, 11.6, 11.10); una tercera para 11.11.

Preparación: `cp -R "$PACK/06-contexto-nativo/." "$BASE/"` (el perfil del ejemplo con el contexto `wounded_native`); `/mm reload`; `/thepredict status` con el perfil activo y sin errores. A en `SURVIVAL`; `/mm m spawn ThePredictGuardian 1` a unos 6 bloques y golpearlo una vez. Cada paso mantiene la situación al menos 2 s y copia de `/thepredict inspect nearest` (o `thepredict inspect <uuid>` desde la consola) las líneas `Perception`, `Target` y `Events`, y de `status` las líneas `Context` y `capture`.

| # | Acción (M01) | Esperado exacto |
|---|---|---|
| 8.1 | 11.1: A objetivo a 6 bloques | `Perception: health HEALTHY; target player_1 RANGED …`; `Target: player_1 health HEALTHY, armor …`; ningún nombre ni UUID de A |
| 8.2 | 11.2: B a 40 bloques, luego a 10, luego B en `CREATIVE` | `perceived players 1`, después `2`, en `CREATIVE` `1` |
| 8.3 | 11.3: A tras un muro de 3 bloques con escudo levantado; `inspect` desde consola | `Perception: … visible no …; cues [NO_LINE_OF_SIGHT]` y `Target: player_1 … cues []` |
| 8.4 | 11.4: `/effect give @s minecraft:invisibility 60`, escudo levantado y a la vista | `Target: player_1 … cues [INVISIBLE]` y `visible no` |
| 8.5 | 11.5: cada situación de M01 11.5 (escudo `/give @s minecraft:shield[minecraft:blocks_attacks={block_delay_seconds:5}]`, arco, ballesta, poción, tótem, perla, `/give @s minecraft:iron_spear`, élitros, golpe al aire, caída con maza) | `RAISING_SHIELD` y luego `BLOCKING`; `DRAWING_BOW` y luego `BOW_FULLY_DRAWN`; `CROSSBOW_LOADED`; `CONSUMING`; `HOLDS_TOTEM`; `PEARL_ON_COOLDOWN`; `LONG_REACH_WEAPON`; `GLIDING`; `ATTACK_RECHARGING`; `MACE_DIVE`; anotar las no observadas |
| 8.6 | 11.6: los eventos de M01 11.6, uno por vez, A sin armadura | `MELEE_HIT`, `MELEE_BLOCKED`, `RANGED_HIT_ON_MOB`, `RANGED_MISS`, `PEARL_THROWN`, `POTION_CONSUMED`, `TOTEM_USED`, `SHIELD_DISABLED`, `ALLY_ASSISTED`; `events recorded` sube en 1 por evento |
| 8.7 | 11.7: sin escudo, arco y soltar, cargar ballesta, cambiar ítem, teclas 2 s | `pending marks` sube (como mucho 5 con las teclas); ninguna línea nueva en `Events` |
| 8.8 | 11.8: `/damage @e[type=minecraft:zombie,sort=nearest,limit=1] 75 minecraft:out_of_world` | `Perception: health CRITICAL; … contexts [low_health_phase, wounded_native]` y `eligible` solo con `retreat_and_defend` y `keep_distance_and_shoot` listas; sin errores de condición |
| 8.9 | 11.9: mob nuevo, tres flechas de A; `inspect`; 20 s más e `inspect` | `Target: … combat <n> s` crece |
| 8.10 | 11.10: B a 60 bloques; después B lanza perlas, bebe y dispara al suelo sin tocar al mob | Ningún contador de `Context` cambia |
| 8.11 | 11.11: con 1 y con 3 jugadores cerca, 20 s de combate | La línea `capture` de `Latency` en ambos casos |

## Sesión 9. Ejecución táctica y fallback sin clave

**Cierra:** DT-08 (cesión), DT-11, DT-12, DT-26, DT-28 (parte local), M10 (escenarios sin clave), M04.6 (señales sin repetir). **Guiones:** M01 §12 (salvo 12.8), G13 §6.4.

Preparación: servidor parado; `decision.logDecisions: true` en `config.yml`; `cp -R "$PACK/07-senales/." "$BASE/"` (el mob con `Predictor: true`, `Despawn: false` y una línea `message … ~onSignal:PREDICTOR_<TÁCTICA>` por cada una de las ocho tácticas del ejemplo: `APPROACH`, `PRESS_AND_STRIKE`, `KEEP_DISTANCE`, `KEEP_DISTANCE_AND_SHOOT`, `RETREAT_AND_DEFEND`, `FLANK_AND_STRIKE`, `CLOSE_IN_AND_SLAM` y `HOLD`); arrancar; `/kill @e[type=!minecraft:player]`. `/thepredict status` muestra `State: Jev UNCONFIGURED (no TypeSafe API key)`.

| # | Acción (M01) | Esperado exacto |
|---|---|---|
| 9.1 | 12.1: `/mm m spawn ThePredictGuardian 1` a 6 bloques de A (`SURVIVAL`) y golpearlo | En ≤2 s una línea `Mob <uuid> LOCAL <táctica>`; `inspect` con `level LOCAL`; `Server: 0/600 requests in 60 s, 0/8 in flight` |
| 9.2 | 12.2: 30 s moviéndose alrededor | Un mensaje `signal <táctica> local` cada vez que cambia la táctica (sea cual sea, porque las ocho tienen su línea), nunca al replanificar la misma |
| 9.3 | 12.3: con `press_and_strike` y A a 6 bloques, contar golpes; después añadir `- targetwithin{d=3} true` a `ThePredictGuardianMelee` y repetir; después una choice `Kind: VANILLA_MELEE` | Una línea `Mob <uuid> LOCAL press_and_strike`; a 6 bloques ningún golpe de la skill y una sola `press_and_strike choice melee: NOT_ELIGIBLE`; a 2,5 bloques `… choice melee: CAST` y el golpe; ningún golpe de la meta base mientras controla; con `VANILLA_MELEE`, un solo golpe vanilla por ciclo y nunca a más de 2,5 bloques entre cajas |
| 9.4 | 12.4: `/damage @s 1` a A justo antes del ataque | `DEFERRED_INVULNERABLE` y después `CAST` en la misma intención |
| 9.5 | 12.5: A bloquea de frente; después escudo levantado con `flank_and_strike` | Frente al escudo `SKIPPED_SHIELD`; con flanqueo `NOT_ELIGIBLE` dentro del ángulo y `CAST` fuera |
| 9.6 | 12.6: A en un pilar de 3 bloques con el mob en `press_and_strike` | Tras ≈2 s, `fell back: NO_PROGRESS`; `inspect` con `last fallback NO_PROGRESS`. **Anota cuántas veces aparece el mensaje `signal`** mientras el mob sigue atascado (hallazgo 5 de M13: un mob local atascado reemite su señal) |
| 9.7 | 12.7: A pasa a `CREATIVE` o se aleja 60 bloques | `state=base` en ≤2 s; IA base sin reinstalar metas (misma revisión) |
| 9.8 | 12.9: en control local, `debug intent nearest hold 400` y `debug release nearest` | `hold` con `[debug]`; tras `release`, nivel 4 y local otra vez en ≤2 ticks |
| 9.9 | G13 §6.4: A en `CREATIVE` a 6–10 bloques, `debug intent nearest hold 400`; `debug release nearest` e inmediatamente `debug goals nearest` dos veces | En la primera lectura `thepredict:hold` ya no está `running` y ninguna `thepredict:*` lo está |

Copiar: las líneas de consola `Mob …` (como mucho 20 cada 10 s; si aparece `Tactical execution: <n> log line(s) suppressed`, anotarlo), los mensajes `signal …` del chat y las salidas de `inspect`. Al terminar, deshacer los cambios de 9.3 en la skill.

## Sesión 10. Amenaza enriquecida

**Cierra:** DT-27, M11.6, M11.1 (guía). **Guiones:** M01 §13. Cuentas: A y B; 13.7 pide cuatro.

Preparación: `decision.logDecisions: true`; `cp -R "$PACK/08-amenaza/." "$BASE/"` (el mob de la sesión 9 más `message{m="top <caster.tt.top>"} @PIR{r=20} ~onTimer:20`); `/mm reload`; `/kill @e[type=!minecraft:player]`; `/mm m spawn ThePredictGuardian 1`.

| # | Acción (M01) | Esperado exacto |
|---|---|---|
| 10.1 | 13.1: A cuerpo a cuerpo; B dispara 10 s desde 10 bloques | Por flecha de B, `Threat +<daño final> RANGED_HIT_ON_MOB for player_<B> sent to the table of mob <uuid>`; ninguna por las de A; suma de B ≤ 20 por ventana de 5 s; `top` cambia a B solo al superar a A en un 10 % |
| 10.2 | 13.2: A bloquea 20 golpes | `Threat +0.25 MELEE_BLOCKED for player_<A> …` por golpe bloqueado; `top` no cambia |
| 10.3 | 13.3: B lanza una arrojadiza de curación y después una persistente de regeneración | `Threat +1.50 ALLY_ASSISTED for player_<B> …` por mob del encuentro; la persistente no pasa de 20 por ventana; daño: ninguna |
| 10.4 | 13.4: A en un pilar de 3 bloques, B cerca | Tras dos decisiones `Threat -2.00 UNREACHABLE for player_<A> …` en cada decisión mientras el mob sigue una ruta que no llega; el líder pasa a B |
| 10.5 | 13.5: `ThreatTable.DecayUnreachable: true` en el mob; `/mm reload`; repetir 10.4 | Aviso `Threat.UnreachableDecayPerSecond: is ignored because mob 'ThePredictGuardian' enables ThreatTable.DecayUnreachable`; ninguna línea `UNREACHABLE`. Después devolver `false` |
| 10.6 | 13.6: A muere, se desconecta o cambia de mundo mientras B acosa; repetir con un mob sin `Modules.ThreatTable` | Sin errores; `top` nunca muestra a A ausente; sin tabla, `inspect`: `no threat table (Modules.ThreatTable off): enriched threat inactive for this mob` |
| 10.7 | 13.7: cuatro jugadores con roles mezclados, 60 s | Ningún cambio de `top` dos veces en la misma ventana de 5 s |
| 10.8 | 13.8: `cp -R "$PACK/09-amenaza-sin-pesos/." "$BASE/"`; `/mm reload`; repetir 10.1–10.4 | Ninguna línea `Threat …` (brazo sin amenaza enriquecida) |

Al terminar: `cp -R "$PACK/06-contexto-nativo/." "$BASE/"` para recuperar los pesos, o `cp "$BASE/plugins/ThePredict/examples/AI/ThePredictGuardian.yml" "$BASE/plugins/MythicMobs/AIConfig/Profiles/"` para volver al ejemplo exacto; `decision.logDecisions: false`; `/mm reload`.

## Sesión 11. Piloto y carga sin clave

**Cierra:** DT-18, DT-29, M13.2 (piloto), M13.3 (variante sin clave), M07.6, M14.5 paso 5 (dos instancias y dos mundos). **Guiones:** G13 §1.3–1.4, §2, §4, §5 (variante 5.A), §5.3, §5.4. Cuentas: dos por mundo (cuatro).

Preparación: `config.yml` con los defaults y `decision.logDecisions: false`; reiniciar. Crear el Nether de prueba (G13 §1.3); copia de la arena con el servidor parado: `cp -R world world-arena-original`. Mob: el de `03-guardian` (`cp "$PACK/03-guardian/plugins/MythicMobs/Mobs/ThePredictGuardian.yml" "$BASE/plugins/MythicMobs/Mobs/"`) y el perfil del ejemplo.

| # | Acción (G13) | Esperado y copiar |
|---|---|---|
| 11.1 | §4.1: `/spark tps`, `/spark health --memory`, `/spark tickmonitor --threshold-tick 55` y `/spark tickmonitor` | Responden en el chat; copiar las cabeceras literales (`TPS from last 5s, 10s, 1m, 5m, 15m:`, `Tick durations (min/med/95%ile/max ms) from last 10s, 1m:`) |
| 11.2 | §4.2: un escalón de 1 mob durante 2 min | Una carpeta de evidencia completa (plantilla de G13 §2) con las comprobaciones vacías |
| 11.3 | §5.1–5.2 variante 5.A: escalones de 1, 10 y 30 mobs, la mitad en cada mundo, 10 min cada uno, con los pasos 1 a 6 de la tabla de §5.2 | Tabla de medidas de §5.2 rellena; paso 5: `Server: 0/600 requests in 60 s, 0/8 in flight` con `Predictor: false`; paso 6 sin líneas |
| 11.4 | §5.3: 30 mobs en un chunk, descargar, `/spark tickmonitor --threshold-tick 55`, volver, 5 s, `status`, `/spark tickmonitor` | Líneas del monitor; `tick_work`; `inspect` de 3 mobs con `Goals: installed for revision <n>` |
| 11.5 | §5.4.1: 100 mobs, dos jugadores, 2 min | `tick_work` p50/p95/p99/`max`; `deferred_ticks=` no aparece en `totals` |
| 11.6 | §5.4.2: con 1 y con todas las cuentas cerca de un mob, 20 s | Línea `capture` y número de cuentas |

## Sesión 12. Comparación A/B sin clave

**Cierra:** DT-28 (daño en servidor, parte local), M13.5 (brazo A y brazo B local). **Guiones:** G13 §7.

Preparación: `cp -R "$PACK/10-ab/." "$BASE/"` (brazo A `ThePredictGuardianClassic` y `ABClassic.yml`; el mob `ThePredictGuardian` del brazo B con `Predictor: true`, `Despawn: false` y las dos líneas de medida); perfil del ejemplo sin cambios (`cp "$BASE/plugins/ThePredict/examples/AI/ThePredictGuardian.yml" "$BASE/plugins/MythicMobs/AIConfig/Profiles/"`); `/mm reload`; si MythicMobs rechaza alguna línea, anótalo. Una vez: `/scoreboard objectives add dealt minecraft.custom:minecraft.damage_dealt` y `/scoreboard objectives add taken minecraft.custom:minecraft.damage_taken`.

| # | Acción (G13 §7.2) | Copiar por repetición |
|---|---|---|
| 12.1 | Seis escenarios (duelo; arco y retroceso; escudo; salud baja con `/damage @e[type=minecraft:zombie,sort=nearest,limit=1] 75 minecraft:out_of_world`; tanque y tirador; pilar), 60 s o hasta la muerte, **10 repeticiones por brazo** alternando A, B, B, A…; brazo A con `/mm m spawn ThePredictGuardianClassic 1`, brazo B con `/mm m spawn ThePredictGuardian 1` | `/scoreboard players reset @a dealt` y `… taken` antes; `/scoreboard players list <jugador>` después; `grep -c 'AB in_reach' logs/latest.log` antes y después; hora de `AB died`; en el brazo B, `status` antes y después (`outcomes`) |
| 12.2 | Brazo B con `decision.logDecisions: true` para contar cambios de posición | Solo las líneas `Mob <uuid> <NIVEL> <táctica>` |

## Sesión 13. Sesión larga (se puede jugar normalmente)

**Cierra:** DT-20 (acumulación de sesiones suspendidas). **Guiones:** G13 §5.5. Durante al menos 2 h de juego normal con el boss vinculado en varias zonas, copiar cada 30 min la línea `Bound mobs: <n> active, <m> suspended; …`.

## Sesión 15. Propósitos de las skills (M15)

**Cierra:** DT-32 (decisión aplicada: el propósito vive en el perfil), DT-33 (escudo frente a daño de área), DT-34 (`Cooldown` estático), M15.4 (aviso del lint en consola y en `status`), M15.6 (el golpe de área espera a su radio) y M15.9 (el ejemplo vincula sin avisos). **Guion:** esta sesión es su propia referencia (M15.9); en juego nada de lo que sigue está observado todavía.

Preparación: servidor parado, con el JAR que incluye M15; `cp -R "$PACK/03-guardian/." "$BASE/"` (el mob con `Predictor: true` y `Despawn: false`, y el perfil y las skills del ejemplo con el `Purpose` de cada choice, la skill `ThePredictGuardianSlam` y la táctica `close_in_and_slam`); `decision.logDecisions: true`; arrancar **sin clave**; `/kill @e[type=!minecraft:player]`. Si MythicMobs rechaza alguna línea de `ThePredictGuardianSlam` al cargar, anótala literalmente.

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 15.1 | `/thepredict status`; `/mm m spawn ThePredictGuardian 1` a 6 bloques de A (`SURVIVAL`), golpearlo y `/thepredict inspect nearest` | En `Catalog`, ningún error ni aviso del perfil del guardián (con solo el ejemplo instalado: `Profiles: LOADED (revision N, 1 profiles read, 1 active, 0 errors, 0 warnings)`). En `inspect`, la línea `Skills: melee MELEE reach 2.50 BOX_GAP wind-up 5 cooldown SHORT declared, ranged HITSCAN reach 16.00 CENTRES wind-up 10 needs sight cooldown SHORT declared, defense SELF wind-up 0 cooldown MEDIUM declared, slam AREA_AROUND_SELF radius 3.00 wind-up 15 cooldown MEDIUM declared` (**DT-34**: un `Cooldown` numérico da su clase; si sale `UNKNOWN`, `isStatic()` es falso para un número) | Sección `Catalog` y la línea `Skills` |
| 15.1b | Cambiar `Cooldown: 5` de `ThePredictGuardianDefense` por `Cooldown: <caster.level>`; `/mm reload`; `inspect nearest` sobre un guardián nuevo. Después devolver `Cooldown: 5` y `/mm reload` | `defense SELF cooldown UNKNOWN declared` (**DT-34**: un placeholder no es estático) | La línea `Skills` |
| 15.2 | `cp -R "$PACK/15-propositos/." "$BASE/"`; `/mm reload`; `/thepredict status` | En consola, un aviso (no un error): `AIConfig/Profiles/ThePredictGuardian.yml: thepredict_guardian.Tactics.keep_distance_and_strike: Movement KEEP_DISTANCE keeps the target beyond Band.Inner 5 but skill 'melee' reaches 2.5; this tactic never casts`; en `status`, el guardián sigue activo y el catálogo cuenta `0 errors, 1 warnings` (con solo el ejemplo: `Profiles: LOADED (revision N, 1 profiles read, 1 active, 0 errors, 1 warnings)`). Después `cp -R "$PACK/03-guardian/." "$BASE/"` y `/mm reload` | La línea de consola y `Catalog` |
| 15.3 | Un zombi vanilla quieto junto al guardián (`/summon minecraft:zombie ~ ~ ~ {NoAI:1b}` a 1 bloque de él); `/thepredict debug intent nearest close_in_and_slam 400`; A se mantiene a unos 5 bloques (retrocediendo mientras el guardián se acerca, o sobre un bloque a 5 bloques) y `inspect nearest`; después A se acerca a 2 bloques | A 5 bloques: `Why: … last cast NOT_ELIGIBLE`, ningún golpe del slam; en `Perception`, `out of reach [melee, slam]`. A 2 bloques (menos de 3 entre las posiciones de los dos, que es lo que mide `@PlayersInRadius{r=3}` y `Radius: 3`): `last cast CAST`, la nube de partículas, y 15 ticks después A recibe daño y sale despedido. **El zombi vanilla no recibe daño ni empujón** (el targeter `@PlayersInRadius` solo alcanza jugadores) | `inspect` en las dos distancias y la observación del zombi. **DT-41:** si puedes, repite con A a unos 2,9 y a unos 3,1 bloques entre posiciones (F3): a 2,9 el slam sale y alcanza a A; a 3,1 ThePredict espera (`NOT_ELIGIBLE`) y, si se lanzara, el targeter no lo alcanzaría |
| 15.4 | A detrás de un pilar de 3 bloques de alto, a unos 8 bloques del guardián; `/thepredict debug intent nearest keep_distance_and_shoot 400`; `inspect nearest`; después A sale del pilar | Tras el pilar: `last cast NOT_ELIGIBLE` y ningún daño de fuego; al salir: `last cast CAST` y el daño | `inspect` en los dos casos |
| 15.5 | Con el slam en alcance (paso 15.3, A a 2 bloques del guardián): A **levanta el escudo mirando al mob** (clic derecho mantenido con el escudo en la mano secundaria) y lo mantiene; el mob lanza el slam (`/thepredict debug intent nearest close_in_and_slam 400` si no lo lanza solo); **anota si A recibe daño** | Una de dos, anotada literalmente: "A recibe daño y empujón con el escudo levantado" o "el escudo bloquea: A no pierde vida" (**DT-33**). **Tu respuesta cambia la regla del escudo para las skills de MythicMobs:** si el escudo bloquea el daño, ThePredict tendrá que tratar esas skills como bloqueables por el escudo (hoy las lanza aunque el objetivo bloquee); si no lo bloquea, la regla actual se queda. Evidencia estática (javap): `damage` usa por defecto `cause` `ENTITY_ATTACK` (`mobAttack`) y `ignoreshield=false`, así que el escudo podría bloquearlo | La observación y la vida de A antes y después |

Copiar además las líneas de consola `Mob …` de las intenciones. Al terminar: `/thepredict debug release nearest`, `/kill @e[type=!minecraft:player]` y `decision.logDecisions: false`.

## Sesión 16. Efectividad sin clave (M16)

**Cierra:** DT-35 (atribución del daño de las skills de MythicMobs), M16.8 (`status` e `inspect` muestran la efectividad por nivel; la sesión 16 da ventanas con daño y alcance coherentes con lo observado) en modo `LOCAL`. **Guion:** [G13 sesión 16](../Guion-servidor-0.0.1.md#16-efectividad-de-las-respuestas-m1611), paso 16.1 (M16.11). **Costo:** cero. La parte con clave (16.2 y 16.3) está en la fase 2.

Preparación: servidor parado, con el JAR que incluye M16; `cp -R "$PACK/03-guardian/." "$BASE/"` (el guardián de M15.9 con sus cuatro skills); en `config.yml`, `decision.logDecisions: true` (el resto de claves de M16 con sus defaults: `decision.questionDetail: full` desde M16.10, `decision.fitChecks: false`; sin clave no se envía nada, así que el detalle no cambia esta parte); arrancar **sin clave**; `/kill @e[type=!minecraft:player]`. Sin clave, el guardián pelea en modo local, así que todas las ventanas son de nivel `LOCAL`.

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 16.1.a | `/thepredict status` antes de pelear | Sección `Effectiveness` con `outcomes: no closed window yet` y `contingencies: asked=0 fired=0 effective=0`; en `totals`, ningún `options_merged=` (el ejemplo no tiene tácticas idénticas) | Secciones `Counters` y `Effectiveness` |
| 16.1.b | A en `SURVIVAL` con espada y sin escudo; `/mm m spawn ThePredictGuardian 1` a 6 bloques; golpearlo y pelear **60 s** (acércate y aléjate varias veces); `/thepredict inspect nearest` a los 30 s y al terminar; `/kill @e[type=!minecraft:player]` | En consola, líneas `Outcome LOCAL <táctica> dealt=<d> taken=<t> reached=yes\|no casts=<c> hits=<h>` (como mucho 20 cada 10 s). Cuando una skill del guardián te golpea (el `damage{a=6}` del golpe, el `damage{a=4}` del disparo o el `damage{a=5}` del slam) dentro de una ventana, esa ventana muestra `dealt=` mayor que 0 y, si el golpe llega en los 20 ticks siguientes a un `CAST`, `hits=` mayor que 0. Cuando le pegas, `taken=` mayor que 0. En `inspect`, la línea `Outcomes:` con las últimas ventanas (como mucho 8) | Las líneas `Outcome …`, `Mob …` y las dos salidas de `inspect`; tu vida antes y después de cada golpe |
| 16.1.c | Repetir 16.1.b dos veces más (3 combates de 60 s en total) y `/thepredict status` al final | `outcomes: level=LOCAL windows=<n> dealt=<d> taken=<t> reach=<r> hit_rate=<h> effect=<e>` con `n` > 0; `dealt` > 0 si el guardián te dañó con sus skills; `reach` cerca de 1 en las ventanas de `APPROACH` que llegaron al cuerpo a cuerpo | Sección `Effectiveness` |
| 16.1.d | **DT-35.** Comparar las líneas `Outcome …` con lo observado: ¿cada golpe de una skill del guardián que te quitó vida cayó en una ventana con `dealt` > 0? | Si **sí**: DT-35 se confirma (MythicMobs notifica su `damage` con el lanzador como causante). Si una skill te daña y `dealt` sigue en `0.00` en todas las ventanas, DT-35 queda refutada: el listener deberá atribuir el daño con `MythicDamageEvent` | Tu conclusión con las líneas que la apoyan |
| 16.1.e | En el último combate, mata al guardián a espadazos (sin `/kill`, que no es una muerte en combate) | En la consola, la última línea `Outcome …` del guardián aparece en el mismo tick de su muerte (la muerte del mob cierra su ventana); `inspect` ya no lo encuentra. Si te mata él, la ventana abierta anota `target died` y la verás en `inspect` antes de reaparecer | La línea `Outcome …` y la hora de la muerte |

Al terminar: `/kill @e[type=!minecraft:player]` y `decision.logDecisions: false` si no vas a seguir con la parte con clave.

## Sesión 17. Participantes y objetivos como entidades (M17)

**Cierra:** DT-36 (tendencia de una entidad por su velocidad), DT-37 (alcance cuerpo a cuerpo vanilla de un mob), DT-38 (escudo de un no jugador), DT-39 (etiquetas de tipos tras `/minecraft:reload`) y las casillas de servidor de M17.10. **Guion:** [G13 sesión 17](../Guion-servidor-0.0.1.md#17-participantes-y-objetivos-como-entidades-m1710). **Costo:** cero (sin clave).

Preparación: servidor parado, con el JAR que incluye M17; `cp -R "$PACK/03-guardian/." "$BASE/"` y `cp -R "$PACK/12-entidades/." "$BASE/"`. Eso instala:
- el cazador `TP_MonsterHunter` (`Faction: hunters`, admite `kind:MONSTER`, apunta con `nearestOtherFactionMonsters`, así que los cazadores no se atacan entre sí);
- el lobo `TP_TagWatcher`, que solo admite la etiqueta `tag:mipack:presas` y nunca jugadores;
- los duelistas `TP_DuelRed` y `TP_DuelBlue` (`Faction: red`/`blue`), que solo admiten la facción contraria y nunca jugadores;
- el datapack `world/datapacks/mipack`, cuya etiqueta `mipack:presas` empieza con `["minecraft:rabbit"]`. Si tu mundo no se llama `world`, mueve la carpeta a `<mundo>/datapacks/`.

`config.yml` va **sin** sección `participants` (defaults). Arranca **sin clave** y ejecuta `/kill @e[type=!minecraft:player]`. `/thepredict status` debe decir `0 errors` en `Catalog`. `/datapack list` debe mostrar `file/mipack`.

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 17.1 | **Defaults.** A en `SURVIVAL`; `/mm m spawn ThePredictGuardian 1` a 6 bloques de A; `/summon minecraft:zombie ~3 ~ ~` junto al guardián; golpear al guardián; `/thepredict inspect nearest` | `Target: player_1 health …; perceived players 1; …`; ninguna línea nombra `entity_`: el zombi vanilla no existe para ThePredict, como en la 0.0.1 | La salida de `inspect` |
| 17.2 | **Entidades admitidas.** `/kill @e[type=!minecraft:player]`; `/mm m spawn TP_MonsterHunter 1` y `/summon minecraft:zombie ~6 ~ ~`; A se aleja 30 bloques; esperar a que el cazador persiga al zombi; `/thepredict inspect <uuid del cazador>` dos veces con 2 s de diferencia | `Target: entity_1 (zombie) health …`; en `Perception`, `target entity_1 MELEE\|RANGED CLOSING\|STEADY…` y `reachable yes`. **DT-36:** mientras el zombi camina hacia el cazador, `CLOSING`; parado, `STEADY`. **DT-37:** con los dos a la misma altura, `outside target reach no` cuando el hueco entre las cajas es menor de ≈ 0,83 bloques **en cada eje horizontal** (también en diagonal, hasta ≈ 1,17 bloques de hueco total); `outside target reach yes` cuando el hueco en `x` o en `z` pasa de 0,83, o cuando uno está encima del otro sin solapar en altura | Las dos salidas de `inspect`, lo que hacía el zombi y su posición respecto al cazador |
| 17.2b | **DT-39.** `/kill @e[type=!minecraft:player]`; A en `SPECTATOR` a 10 bloques; `/mm m spawn TP_TagWatcher 1`; `/summon minecraft:sheep ~4 ~ ~` dos veces; esperar hasta 60 s a que el lobo persiga a una oveja (los lobos salvajes eligen presa al azar); `/thepredict inspect <uuid del lobo>`. Después, con el servidor en marcha, cambiar `presas.json` a `["minecraft:sheep"]`; `/minecraft:reload`; `inspect`; `/mm reload`; `inspect` | Con `rabbit` en la etiqueta, el lobo persigue a la oveja pero ThePredict no la admite: `Target: none; perceived players 0; …` y `Session: …, no encounter`. Tras `/minecraft:reload` sigue igual, porque la etiqueta se leyó al vincular. Tras `/mm reload`, **si Paper refleja la etiqueta recargada** (lo que DT-39 pregunta), `Target: entity_1 (sheep) health …`. Si sigue sin admitirla, anótalo: DT-39 se resuelve con "hace falta reiniciar" | `inspect` en cada paso y `/datapack list` |
| 17.3 | **Bandos.** `/kill @e[type=!minecraft:player]`; A en `SPECTATOR` lejos; `/mm m spawn TP_DuelRed 1` y, a 8 bloques, `/mm m spawn TP_DuelBlue 1`; esperar a que se ataquen; `/thepredict status` e `inspect` de cada uno | En `status`, sección `Encounters`: `Encounters: 2 active, …`. En el `inspect` de cada duelista: `Session: TP_DuelRed, profile tp_duel_red_ai, ACTIVE, level LOCAL, encounter <n>` y `Session: TP_DuelBlue, profile tp_duel_blue_ai, ACTIVE, level LOCAL, encounter <m>`, con `<n>` distinto de `<m>`; en los dos, `Target: entity_1 (zombie) health …`. Nunca los dos en el mismo `encounter` | `status` y los dos `inspect` |
| 17.4 | Sin matar a los duelistas: A pasa a `SURVIVAL` y entra entre los dos; golpea a uno de ellos | Ninguno toma a A como participante (`PlayerGameModes: []`): el `inspect` de los dos sigue sin `player_`. Si MythicMobs cambia su objetivo hacia A, el duelista queda sin objetivo para ThePredict: `Target: none` y ninguna pregunta | Los dos `inspect` |
| 17.5 | **DT-38.** `/kill @e[type=!minecraft:player]`; `/mm m spawn TP_MonsterHunter 1` y `/summon minecraft:skeleton ~8 ~ ~` con A lejos; cuando el esqueleto tense el arco contra el cazador, `inspect` del cazador varias veces. Después `/kill @e[type=minecraft:skeleton]` y `/summon minecraft:zombie ~6 ~ ~ {equipment:{offhand:{id:"minecraft:shield",count:1}}}` (formato `equipment` de 1.21.5 en adelante; la clave `equipment` y la ranura `offhand` están comprobadas con javap sobre el servidor 26.2, el comando no); `inspect` del cazador mientras lo persigue | Con el esqueleto tensando: `Target: entity_1 (skeleton) … cues [DRAWING_BOW]` y `BOW_FULLY_DRAWN` a partir de 1 s. Con el zombi con escudo: `cues []`, **nunca** `RAISING_SHIELD` ni `BLOCKING`, porque ni vanilla ni MythicMobs 5.13.1 hacen que un mob levante el escudo (javap: MythicMobs solo tiene `StopUsingItem`). **La parte de DT-38 que pide un escudo levantado exige una configuración propia:** un plugin o script tuyo que llame `LivingEntity#startUsingItem(EquipmentSlot.OFF_HAND)` sobre ese zombi. Con ella, `RAISING_SHIELD` y, 5 ticks después (`Math.round(0,25 × 20)`), `BLOCKING`. Sin ella, anota "sin configuración propia": DT-38 sigue abierta en esa parte | Las salidas de `inspect` y si usaste una configuración propia |
| 17.6 | **Costo.** `/kill @e[type=!minecraft:player]`; 10 cazadores y 30 zombis mezclados (`/mm m spawn TP_MonsterHunter 10`, `/summon minecraft:zombie` 30 veces); 60 s; `/thepredict status` (línea `tick_work`). Después `participants.entities.perceiveNonTargets: true` en `config.yml` y en el perfil del cazador `Participants.Entities.PerceiveNonTargets: true`; reiniciar y repetir | `tick_work` p95 por debajo de `decision.maxTickWorkMicros` (2 000 µs) en los dos casos. Con `perceiveNonTargets` sube: una consulta de entidades por mob, hasta 8 entidades por mob y como mucho 16 participantes por encuentro. Los cazadores comparten `Faction: hunters`: no se atacan entre sí, y los que persiguen al mismo zombi comparten encuentro | Las dos líneas `tick_work` y `Encounters` |

Al terminar: `/kill @e[type=!minecraft:player]`, quitar `12-entidades` (`rm "$BASE/plugins/MythicMobs/AIConfig/Profiles/ThePredictEntities.yml" "$BASE/plugins/MythicMobs/Mobs/ThePredictEntities.yml"` y `rm -r "$BASE/world/datapacks/mipack"`) y devolver `config.yml` a los defaults.

## Sesión 18. Duelo de mobs controlados sin clave (M18)

**Cierra:** DT-40 (paquete de duelo: `Template`, `glow`, refuerzos de zombi y eventos de skill por cast) y las casillas de servidor de M18.2, M18.5 y la parte local de M18.3 y M18.4. **Guion:** [G13 §13](../Guion-servidor-0.0.1.md#13-duelo-de-mobs-controlados-m186). **Costo:** cero (sin clave). **Necesita:** Multiverse-Core (u otro gestor de mundos: usa sus comandos de crear y teletransportar) y, para 18.1, una segunda cuenta (B).

Preparación (una vez): servidor parado, con el JAR que incluye M18; `cp -R "$PACK/13-duelo/." "$BASE/"`. Eso instala `plugins/MythicMobs/Mobs/ThePredictDuel.yml` (la plantilla `DuelBase` y los cuatro duelistas `DuelRed`, `DuelRedClassic`, `DuelBlue` y `DuelBlueClassic`), `plugins/MythicMobs/Skills/ThePredictDuelSkills.yml` (las cinco skills `DuelStrike`, `DuelBolt`, `DuelLeap`, `DuelGuard`, `DuelHeal`, el `DuelTimeout` de 90 s y las reglas clásicas `DuelRule*`) y `plugins/MythicMobs/AIConfig/Profiles/ThePredictDuel.yml` (los perfiles `duel_red` y `duel_blue`). En `plugins/MythicMobs/AIConfig/Profiles/` deja solo `ThePredictDuel.yml` (mueve los demás `.yml` de sesiones anteriores a otra carpeta). `config.yml` con `jev.apiKey: ''` y arranque desde un terminal **sin** `TYPESAFE_API_KEY`.

**18.0 Preparación de la arena (al empezar cada sesión de duelo).** Como operador A, en el chat:

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

Esperado: tras `/mm reload`, en la consola `Profile catalog LOADED (revision <n>, 2 profiles read, 2 active, 0 errors, 0 warnings)`; `/thepredict status` da `State: Jev UNCONFIGURED (no TypeSafe API key)` y la misma línea `Profiles: LOADED (revision <n>, 2 profiles read, 2 active, 0 errors, 0 warnings)`. El `/scoreboard objectives add` da un error de objetivo existente desde la segunda sesión: es normal. Desde la **consola**, las reglas y el relleno se escriben con `execute in` (`minecraft:duel` es la clave de dimensión que da Paper a un mundo llamado `duel`; si tu gestor de mundos usa otra, `execute in ` + tabulador la muestra):

```text
execute in minecraft:duel run gamerule spawn_mobs false
execute in minecraft:duel run gamerule advance_time false
execute in minecraft:duel run time set noon
execute in minecraft:duel run fill -21 64 -21 21 70 21 minecraft:barrier hollow
execute in minecraft:duel run fill -20 64 -20 20 64 20 minecraft:grass_block
```

Seguridad antes de empezar: ningún jugador de prueba tiene `faction.duel_red` ni `faction.duel_blue` (con LuckPerms, `/lp user B permission check faction.duel_red` y `… faction.duel_blue` dan `false`); los operadores y quien tiene `*` también tienen `mythicmobs.nofaction`, que MythicMobs mira antes, así que nunca cuentan como de una facción. Observadores siempre en `SPECTATOR`.

**Una ronda** (la misma en 18.1 a 18.4): primero la limpieza, nunca `killall`; después los dos duelistas del brazo en sus puntos, con los tipos y lados de la tabla (el orden A, B, C, D, D, C, B, A se repite cada 8 rondas; los lados se intercambian cada dos):

```text
/mm mobs kill DuelRed
/mm mobs kill DuelRedClassic
/mm mobs kill DuelBlue
/mm mobs kill DuelBlueClassic
/mm mobs spawn <rojo> 1 duel,0.5,65,<z rojo>
/mm mobs spawn <azul> 1 duel,0.5,65,<z azul>
```

| Ronda (y cada 8 más) | Brazo | `<rojo>` | `<azul>` | `<z rojo>` | `<z azul>` |
|---|---|---|---|---|---|
| 1 | A | `DuelRed` | `DuelBlueClassic` | `-9.5` | `10.5` |
| 2 | B | `DuelRedClassic` | `DuelBlue` | `-9.5` | `10.5` |
| 3 | C | `DuelRed` | `DuelBlue` | `10.5` | `-9.5` |
| 4 | D | `DuelRedClassic` | `DuelBlueClassic` | `10.5` | `-9.5` |
| 5 | D | `DuelRedClassic` | `DuelBlueClassic` | `-9.5` | `10.5` |
| 6 | C | `DuelRed` | `DuelBlue` | `-9.5` | `10.5` |
| 7 | B | `DuelRedClassic` | `DuelBlue` | `10.5` | `-9.5` |
| 8 | A | `DuelRed` | `DuelBlueClassic` | `10.5` | `-9.5` |

**Radio de la grabación: 30.** La caja interior mide 41 × 41, así que sus esquinas quedan a unos 29 bloques del centro `0 65 0`: con el radio por defecto (24) un duelista arrinconado en una esquina dejaría de muestrearse; con 30 se cubre toda la arena.

La ronda termina con `DUEL red died`, `DUEL blue died` (en el chat y en la consola, tras `[Server]`) o, a los 90 s, con dos `DUEL timeout` (empate). El marcador de la barra lateral suma `blue_wins` cuando muere rojo y `red_wins` cuando muere azul, acumulado toda la sesión (no se reinicia entre rondas).

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 18.1 | **Seguridad.** B en `SURVIVAL` dentro de la arena, a 5 bloques del centro; una ronda del brazo A (fila 1); 60 s; durante la ronda `/thepredict inspect <uuid de DuelRed>` dos veces (el UUID sale de `/mm mobs listactive`, o usa `inspect nearest` volando cerca) | B no recibe daño ni es objetivo de ninguno. En `inspect`: `Session: DuelRed, profile duel_red, ACTIVE, level LOCAL, encounter <n>` y `Target: entity_1 (zombie) health …`; ninguna línea nombra `player_` | Los dos `inspect` y la vida de B al final |
| 18.2 | **Visual y DT-40.** `/difficulty hard`; una ronda del brazo C (fila 3) mirando desde arriba; después `/difficulty` a tu valor de siempre | Brillo rojo y azul **toda la ronda** (DT-40 b; si se apaga a los 10 s, anótalo); barras de jefe `[RED · ThePredict] <táctica> controlled` y `[BLUE · ThePredict] …` que cambian de táctica; partículas `heart`, `cloud`, `enchanted_hit`, `flame` o un sonido por cada señal nueva; los dos duelistas empiezan con 60 de vida y lanzan las reglas cuando caen a nivel 4, así que la plantilla se heredó (DT-40 a). `DuelLeap` no saca a nadie de la caja (si lo hace, baja `leap{velocity=100}` en `ThePredictDuelSkills.yml`, `/mm reload`, y anótalo). Ningún zombi de refuerzo aparece al herir a un duelista en difícil (DT-40 c; javap: `Zombie.hurtServer` exige `spawn_mobs`). Cuando un duelista cae a nivel 4 y su meta base lo pone al lado del rival, el golpe vanilla (`Damage: 0`) no le quita vida, no lo empuja ni lo hace parpadear (DT-40 e; si lo empuja o parpadea, anótalo) | Lo observado, capturas si puedes, y la velocidad de `leap` si la cambiaste |
| 18.3 | **Matriz local (80 rondas).** `/thepredict status` (anota `State`); `/thepredict duel start local-<fecha> duel_red duel_blue duel 0 65 0 30` (con `<fecha>` como `20261001`); 80 rondas en el orden de la tabla; `/thepredict duel status` cada 10 rondas; al final `/thepredict duel stop` y `/thepredict status` | Al empezar: `[ThePredict] Duel recording 'local-<fecha>' started: red = duel_red, blue = duel_blue, radius 30.00, folder <ruta>/plugins/ThePredict/duels/local-<fecha>-<UTC>`. En `duel status`: `Duel recording 'local-<fecha>': recording, rounds <n>, followed <m>, requests 0, input tokens 0, rows dropped 0, bytes <b>, folder …` con `<n>` igual a las rondas jugadas y `rows dropped 0`. Al parar: `Duel recording 'local-<fecha>' stopped after 80 rounds, 0 rows dropped; summary.md is written in …`. El marcador suma 80 menos los empates. Si la grabación se detiene sola, la consola dice `Duel recording 'local-<fecha>' stopped: <límite> reached` y `summary.md` tiene `stopped_by: <límite>` | Las salidas de `duel status` y de `status`, y el marcador final |
| 18.5 | **Copia de vuelta** (sin servidor): la carpeta de la grabación, las dos salidas de `status` y el registro filtrado a `Evidencia-0.0.1/<fecha>-duelo/` (ver abajo) | La carpeta tiene `decisions.jsonl`, `samples.csv`, `casts.csv`, `damage.csv`, `intents.csv`, `defense.csv`, `retreats.csv`, `threat.csv` (vacío: `ThreatTable: false`), `outcomes.csv`, `rounds.csv` (81 líneas: cabecera y 80 rondas) y `summary.md`; el `grep -c` de UUID sobre las líneas `body` da `0` (sin clave no hay ninguna) | La carpeta completa |

Copia de vuelta (con `EVID` apuntando a `Evidencia-0.0.1/<fecha>-duelo/`):

```bash
cp -R "$BASE/plugins/ThePredict/duels/<etiqueta>-<UTC>" "$EVID/"
grep -E 'DUEL|Outcome|Tactical execution|Duel recording' "$BASE/logs/latest.log" > "$EVID/latest-filtrado.log"
grep '"type":"body"' "$EVID/<etiqueta>-<UTC>/decisions.jsonl" | grep -c -E '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
cd "$REPO" && ./gradlew duelJoin --offline -Pdecisions="$EVID/<etiqueta>-<UTC>/decisions.jsonl"
```

Los archivos llevan los UUID de los duelistas a propósito (salida local, M18.4); los cuerpos enviados a Jev (`"type":"body"`) no deben llevar ninguno (regla I6): el `grep -c` da `0`. `duelJoin` escribe `build/reports/m18/decisions-joined.json`, una entrada por solicitud (sin clave: ninguna).

Al terminar la sesión: la ronda de limpieza (las cuatro `/mm mobs kill`), `/thepredict duel status` (`stopped (command)`) y, si no vas a repetir, quita `13-duelo` (`rm "$BASE/plugins/MythicMobs/AIConfig/Profiles/ThePredictDuel.yml" "$BASE/plugins/MythicMobs/Mobs/ThePredictDuel.yml" "$BASE/plugins/MythicMobs/Skills/ThePredictDuelSkills.yml"`) y devuelve los perfiles que moviste.

---

## Sesión 19. Intención en la skill (M19)

**Cierra:** DT-43, DT-44, DT-45, DT-46 y DT-47, si la evidencia literal coincide con lo esperado. DT-48 (se registra con M20) solo se cita en el paso 19.9: no se abre aquí.

**Costo:** cero. Sin clave. Arranca desde un terminal en el que no se haya cargado `.env`. No se llama a Jev.

Preparación (una vez): servidor parado. `cp -R "$PACK/19-intencion/." "$BASE/"`. Eso sustituye el mob, las skills y el perfil del ejemplo por la forma con `Intention` y sin `Purpose` en el perfil (`Predictor: true`, `Despawn: false`). Deja en `plugins/MythicMobs/AIConfig/Profiles/` solo `ThePredictGuardian.yml` si quieres el catálogo de un solo perfil. `config.yml` con los defaults de esta entrega (`castMode: any_ready`, `castThread: same_tick`, `untypedCast: tactic_only`, `readyHorizonTicks: 0`, `pruning: "off"`) y `jev.apiKey: ''`.

Los pasos 19.2, 19.4, 19.7 y 19.9 editan esos archivos en el servidor y vuelven a dejarlos como en `19-intencion` al terminar. No hace falta otra carpeta.

| # | Acción | Esperado | Copiar |
|---|---|---|---|
| 19.1 | `/mm reload` y `/thepredict status`. `/thepredict inspect nearest` con el guardián cerca, en `SURVIVAL`. Si hay modo depuración, anota los nombres de tipo que imprima `inspect` | `Profiles: LOADED (revision <n>, 1 profiles read, 1 active, 0 errors, 0 warnings)`. En `inspect`, `Skills: melee MELEE reach 2.50 BOX_GAP wind-up 5 cooldown SHORT skill, ranged HITSCAN reach 16.00 POSITIONS_STRICT wind-up 10 needs sight cooldown SHORT skill, defense SELF wind-up 0 cooldown MEDIUM skill, slam AREA_AROUND_SELF radius 3.00 wind-up 15 cooldown MEDIUM skill` (origen `skill` y la medida leída; el `Cooldown` numérico da la clase, DT-34). Una línea inválida no debe aparecer. Qué texto exacto devuelven `getLine()`, `getConfigLine()` y `getTypeName()` es la evidencia de DT-45 y DT-46: cópialo; no lo supongas | `status`, el `inspect` y, si sale, los nombres de tipo |
| 19.2 | En `ThePredictGuardianMelee`, añade `FOO` a `Intention` y recarga. Luego deja la línea en `MELEE` solo (sin papel) y recarga. Restaura la línea del paquete | Con `FOO`: aviso de primitiva desconocida y la choice sigue con tipo si el resto es válido. Con solo `MELEE`: aviso de línea inválida e `inspect` muestra la choice sin tipo (`untyped`). MythicMobs no debe marcar el archivo como error de esquema por esa clave | Los avisos de consola y los dos `inspect` |
| 19.3 | Con la skill en cooldown visible, cambia un token de `Intention` (por ejemplo el daño del golpe) y `/mm reload`. Restaura la línea | `inspect` muestra el valor nuevo y `origin=skill`. Anota si el cooldown que estaba en curso se reinicia (DT-44). No afirmes el reinicio si no lo viste | Consola del reload y el `inspect` antes y después |
| 19.4 | En `ThePredictGuardianMelee` añade `Charges: 3`, `/mm reload`, y observa tres usos seguidos. Quita `Charges` al terminar | La skill sale tres veces antes de esperar el `Cooldown`. Si sale otro número, cópialo: es la evidencia, no un fallo que haya que inventar | Lo observado y el fragmento de `latest.log` |
| 19.5 | `Cooldown: 1.5` en el golpe (30 ticks). Mira el intervalo entre dos casts. Luego deja el `slam` (`Cooldown: 8`, `delay 15`) salir por `any_ready` mientras el golpe también está listo | El golpe, si sale, lo hace cada 30 o 31 ticks (cadencia por tick; el margen de la 0.0.1 era de 2). El `slam` no empieza otra vez mientras dura su preparación. Anota los ticks si el registro los da; si no, «no medido» | Intervalos anotados o «no medido» |
| 19.6 | Lee la consola de MythicMobs desde el `/mm reload` de 19.1, filtrando `Intention` y `Unknown` | MythicMobs 5.13.1 no se queja de la clave `Intention` (DT-43). Si aparece un aviso de clave desconocida, cópialo literal | Las líneas que mencionen `Intention` o la ausencia de queja |
| 19.7 | Con `ThreatTable: true`, provoca que el líder de la tabla no sea `Mob.getTarget()` (un segundo jugador pega y se va, o el que describes en la nota). Observa el disparo (`targetwithin`) y el golpe (sin `targetwithin`) | La skill sin `targetwithin` puede salir hacia el líder de la tabla. La que tiene `targetwithin` espera si `Mob.getTarget()` no es el objetivo de ThePredict. Anota con qué frecuencia difieren (DT-47). Si en la partida no llegan a diferir, anota «no observado», no un número | `inspect` del objetivo y lo que hizo cada skill |
| 19.8 | `/thepredict debug intent nearest press_and_strike` (o la UUID) y espera el `Cooldown` del golpe. `/thepredict debug release nearest` al terminar | Mientras dura la intención de depuración sale la skill de esa táctica en cuanto está lista, y no sale otra skill del perfil. `debug` no envía nada a Jev | `inspect` durante la intención y al soltarla |
| 19.9 | Con `decision.castThread: same_tick`, mira un `delay 10` (el disparo ya lo tiene). Si puedes inducir retraso del servidor sin tocar otra cosa, anota el tick de impacto frente al tick de cast. Prueba aparte `mythic_default` y anota la diferencia. Restaura `same_tick` | Con `same_tick` el impacto cae en `castTick + 10` también con el servidor retrasado, si el registro lo muestra. Con `mythic_default` anota la diferencia que veas. DT-48 (se registra con M20): esta sesión no cierra esa entrada | Ticks copiados o «no medido», y la diferencia de `mythic_default` |

Al terminar: restaura los cuatro archivos desde `19-intencion` si los editaste, `/mm reload`, y `/kill @e[type=!minecraft:player]`.

---

## Sesión 20. Capa por tick (M20)

**Cierra:** DT-48, DT-49 y DT-50, si la evidencia literal coincide con lo esperado. No cierra el default `full` ni baja las rutas a 4: eso espera estas medidas.

**Costo:** cero. Sin clave. No se llama a Jev. Los perfiles de duelo de esta sesión siguen en `Timing.Cadence: standard` y `Timing.TickLayer: casts` salvo el paso 20.6, que los cambia y los restaura. El brazo `reactive` de pago no es esta sesión (E5 de M20, no se ejecuta aquí).

Preparación: servidor parado. Reutiliza `13-duelo` y `19-intencion` (`cp -R "$PACK/13-duelo/." "$BASE/"` y, para 20.1–20.5, el guardián de `19-intencion`). `config.yml` con los defaults de M20 (`tickLayer.mode: reactions`, `candidateScoring: priority`, `candidateDiversity: false`, `debug.seed: 0`) y `jev.apiKey: ''`. No hay carpeta `20-…`: los archivos ya están en las sesiones 13 y 19.

| # | Acción | Esperado | Copiar |
|---|---|---|---|
| 20.1 | Un jefe, luego 5 y 20, en combate. `/thepredict status` y `/thepredict inspect <nearest\|uuid> tick`. Una ruta corta con `Options.AsyncPathfinding` en verdadero y en falso. Anota el retraso entre la vista y el `start` de la meta `micro` si usas `full` un momento y lo restauras | `tick_work` p50/p95/p99. `inspect … tick` muestra la lectura decodificada (el formato no está fijado). El coste de la ruta y si `moveTo` espera (DT-49). Retraso de la meta de 0 a 2 ticks (DT-50). `PREPARE` p95, si el registro lo da | `status`, el `inspect tick` y los microsegundos anotados |
| 20.2 | Skill con `delay 10`, primero `decision.castThread: same_tick` y luego `mythic_default`, con el servidor sano y, si puedes, con retraso. Restaura `same_tick` | Tick de impacto frente al tick de cast, y el coste por cast (DT-48). Si no lo mides, «no medido» | Ticks o «no medido» |
| 20.3 | Con `TickLayer: full` un momento, un jugador: rondar, esquivar un arco, esperar y entrar, retroceder. Restaura `reactions` | Como mucho 2 cambios de meta `MOVE` y 4 destinos por mob y segundo en p95, si el registro lo da. Si no, describe lo visto | Lo visto |
| 20.4 | Escudo levantado, beber y vida crítica, cada uno con `inspect … tick` | La lectura muestra el bit correspondiente y el registro, si lo hay, nombra la reacción | Los `inspect` |
| 20.5 | 5 y 20 jefes con `full`. Restaura `reactions` | `tick_work` y si aparece degradación. p99 ≤ 2 ms con 20 jefes es la puerta E4; si no llega, anota el número | `status` |
| 20.6 | Duelo de la sesión 18, 80 rondas locales, una pasada con `TickLayer: reactions` y otra con `full`, las dos contra el clásico y con `Cadence: standard`. Restaura `casts` y `standard` | Compáralo con el simulador local (`golden/m20` es la pasada `casts`). No afirmes superioridad | El informe de rondas que copies |

Al terminar: restaura los YAML, `/mm reload`, y la limpieza de la sesión 18 si spawneaste duelistas.

## Sesión 21. Registros y almacenamiento (M21)

**Cierra:** DT-51, DT-52 y, solo si se ejecuta 21.5, DT-53. No activa `decision.answerCache: on`.

**Costo:** 21.1–21.4 son cero, sin clave. 21.5 es opcional, ≈ 0,19 USD, techo 0,60 USD, y solo con aprobación del usuario. No se llama a Jev en 21.1–21.4.

Preparación: servidor parado. No hay carpeta `21-…`. Reutiliza `13-duelo` y `19-intencion` (`cp -R "$PACK/13-duelo/." "$BASE/"` y, para 21.2, el guardián de `19-intencion`). `config.yml` con los defaults de M21 (`records.level: decisions`, `records.bodies: hash`, `storage.aggregates: sqlite`, `decision.answerCache: off`) y `jev.apiKey: ''`. La carpeta `plugins/ThePredict/records/` no debe estar en una unidad de red. El driver SQLite lo trae Paper; ThePredict no lo empaqueta.

| # | Acción | Esperado | Copiar |
|---|---|---|---|
| 21.1 | Arranca el servidor. Mira el registro de arranque. `/thepredict records stats` y el bloque `Storage` de `/thepredict status` | Sin el aviso de acceso nativo de JEP 472. `stats` muestra SQLite 3.49.1, journal `wal` y synchronous normal (el valor leído puede ser `1`). JSONB usable (DT-51). Si el aviso aparece, anótalo; no cambies el arranque | El registro de arranque y `records stats` |
| 21.2 | 30 min con 5 jefes en combate, primero `records.level: decisions` y `records.bodies: hash`, luego una pasada corta con `off`. Restaura `decisions` | Bytes por hora, descartes 0 y `tick_work` sin cambio medible frente a `off` (DT-52). Diferencia media de `tick_work` p99 ≤ 20 µs es la puerta E4; si no llega, anota el número | `status`, el tamaño de `records/` y de `aggregates.db` |
| 21.3 | `stop` normal. Arranca, mira la última línea `run_stop`. Para de nuevo con `kill -9` del proceso Java y vuelve a arrancar. `/thepredict records verify` | Tras `stop`, la línea `run_stop` lleva `clean: true`. Tras `kill -9`, aviso de cierre sucio, `verify` = `ok` y los agregados siguen | La línea `run_stop`, el aviso y el resultado de `verify` |
| 21.4 | Copia `records/` y `aggregates.db` a un lado. Sobre la copia, `/thepredict records prune 0`, `export outcomes` y `export events <desde> <hasta>`. No podes la carpeta real si aún la quieres | `prune 0` no borra el día en curso. Los export quedan en `records/exports/` (CSV de resultados y JSONL de eventos, tope 512 MiB) | Las rutas escritas y el texto `queued` |
| 21.5 | Solo con aprobación. Duelo de la sesión 18, 30 rondas con Jev, `decision.answerCache: shadow`. Restaura `off` | Aciertos, concordancia y Δ (DT-53). La sombra siempre envía, así que no ahorra la llamada. Costo ≈ 0,19 USD, techo 0,60 USD | El informe del duelo y las líneas `cache` |

Al terminar: restaura `records.level: decisions` y `decision.answerCache: off`, y la limpieza de la sesión 18 si spawneaste duelistas. Fuera del servidor, un JSONL se lee con `jq` y la base con DuckDB; no hace falta tener el servidor abierto.

## Sesión 22. Dificultad y entropía (M22)

**Cierra:** DT-54, DT-55, DT-56 y DT-57 si los pasos salen como se espera. DT-58 solo con el paso 22.6, que es de pago y no forma parte de esta sesión salvo aprobación. **Costo:** 22.1–22.5 son cero, sin clave. 22.6 no se ejecuta aquí.

**Pendiente de servidor:** esta sesión no se ha ejecutado. Nada de lo que sigue está verificado en un servidor Paper.

Preparación: servidor parado. No hay carpeta `22-…`. Reutiliza `13-duelo` y `19-intencion`. `config.yml` con los defaults de M22 (`difficulty.defaultMode: auto`, `difficulty.defaultPreset: normal`) y `jev.apiKey: ''`. Un mob con `Predictor: true`.

| # | Acción | Esperado | Copiar |
|---|---|---|---|
| 22.1 | Un jugador contra el jefe en Normal, sin Entropía. Mira el ritmo, no el daño. `/thepredict eval status` | El comando responde sin bloquear. El ritmo puede bajar de peldaño. No afirmes que se siente justo: anota lo visto (DT-55) | `eval status` y lo visto |
| 22.2 | El mismo jefe con `PredictorEntropy: true`. Restaura el valor | La reacción se nota más rápida al avanzar la pelea y el peldaño no baja. Si no lo puedes mirar, «no medido» | Lo visto o «no medido» |
| 22.3 | Con la pelea abierta, fija la variable `predictor_target_seconds` a 120 y `predictor_difficulty` a `Caos` | El ajuste siguiente usa esos valores y el reloj no vuelve a cero (DT-56). Si la variable no entra, anótalo | El valor leído o «no medido» |
| 22.4 | Difícil y Normal, el mismo encuentro, sin tocar el daño de la skill | Anota la razón de daño si el registro la da. No se espera un número fijo (DT-57) | El número o «no medido» |
| 22.5 | Una pelea corta y el informe local del simulador, si lo tienes a mano | Si se parecen o no (DT-54). No afirmes que el servidor reproduce el simulador | Lo comparado |
| 22.6 | No lo ejecutes sin aprobación. Sería una pasada con Jev real (DT-58, E8) | No forma parte del costo cero | Nada |

Al terminar: restaura el mob y `difficulty.defaultPreset: normal`.

## Sesión 23. Posturas y telegrafiado (M23)

**Cierra:** DT-59, DT-60, DT-61 y DT-62 si los pasos salen como se espera. DT-63 está retirada: ThePredict no usa gestos. **Costo:** cero. Sin clave. No se llama a Jev. **Pendiente de servidor:** esta sesión no se ha ejecutado. Nada de lo que sigue está verificado en un servidor Paper. Los manejadores de señal y la sintaxis de fases del ejemplo no se dan por comprobados.

Preparación: servidor parado. No hay carpeta `23-…`. Reutiliza el guardián de ejemplo. `jev.apiKey: ''`.

| # | Acción | Esperado | Copiar |
|---|---|---|---|
| 23.1 | Con `Stances.Native: hybrid`, mira `predictor_stance` durante un aviso y al invalidar la sesión. Con `write`, mira `stance{s=predictor_windup}` | Anota si la variable se ve en la misma comprobación y si distingue mayúsculas (DT-60). Si no puedes leerla, «no medido» | Lo visto o «no medido» |
| 23.2 | Un aviso con avance contra un jugador. No afirmes justicia | El cast sale en el tick de liberación, no antes. Anota si el golpe se veía venir | Lo visto |
| 23.3 | Rompe el aguante durante un aviso con `delay` | Anota si lo que queda tras el `delay` se corta (DT-59). Lo ya lanzado puede seguir | Lo visto o «no medido» |
| 23.4 | Los `~onSignal:PREDICTOR_TELL_*` del autor, con sonido y sin sonido | ¿Identificas la respuesta en 4 de 5 casos? (DT-61). No está medido hasta este paso | El conteo o «no medido» |
| 23.5 | Un `SEVERE` en las cinco respuestas y un castigo en la recuperación | Tasa de esquiva, «se veían venir» y si la recuperación deja castigar (DT-62). No hay margen por ping | Lo visto o «no medido» |
| 23.6 | `inspect <mob> telegraph` y `inspect <mob> stance` | Muestra lo leído del YAML. Si el comando no lista esos bloques, anótalo | El texto o «no medido» |
| 23.7 | 20 jefes avisando a la vez. Mira `tick_work` | Anota el coste de la señal. Cero llamadas a Jev | `tick_work` o «no medido» |

Al terminar: restaura `Stances.Native: hybrid` y no dejes `Describe: true`.

## Sesión 24. Contexto de zona (M24)

**Cierra:** DT-64, DT-65, DT-66, DT-67 y DT-68 si los pasos salen como se espera. El paso 24.5 no tiene DT. **Costo:** cero. Sin clave. No se llama a Jev. **Pendiente de servidor:** esta sesión no se ha ejecutado. Nada de lo que sigue está verificado en un servidor Paper. `AIPathfindingMalus` y las condiciones de contexto nativas no se dan por comprobadas.

Preparación: servidor parado. `zone.mode: observe`. No subas el default a `guard`. Reutiliza el guardián de ejemplo o copia `archivos/24-zona` (perfil con `Zone`, contexto `wounded_native` y `AIPathfindingMalus`). `jev.apiKey: ''`. `AIPathfindingMalus` queda (pendiente de servidor): no está verificado.

Arenas de la 24.5 y del puente. **No ejecutado.** El suelo de referencia está en y=64 y el origen del cuadrado es la esquina noroeste. Nada de esto se ha lanzado en un servidor.

```text
# cliff — no ejecutado. x>10 es vacío; el resto es suelo.
/fill ~ ~-1 ~ ~10 ~-1 ~24 stone
/fill ~11 ~-1 ~ ~24 ~-1 ~24 air
# lava_moat — no ejecutado. Isla y cruz en (12,12); el resto es lava.
/fill ~ ~-1 ~ ~24 ~-1 ~24 lava
/fill ~4 ~-1 ~4 ~20 ~-1 ~20 air
/fill ~12 ~-1 ~ ~12 ~-1 ~24 stone
/fill ~ ~-1 ~12 ~24 ~-1 ~12 stone
/fill ~4 ~-1 ~4 ~20 ~-1 ~20 stone
# pillars — no ejecutado. Cuatro pilares de 3 por 3 hasta y=67.
/fill ~ ~-1 ~ ~24 ~-1 ~24 stone
/fill ~7 ~ ~7 ~9 ~2 ~9 stone
/fill ~7 ~ ~15 ~9 ~2 ~17 stone
/fill ~15 ~ ~7 ~17 ~2 ~9 stone
/fill ~15 ~ ~15 ~17 ~2 ~17 stone
# bridge — no ejecutado. |z-12|<=1 es suelo; el resto es vacío.
/fill ~ ~-1 ~ ~24 ~-1 ~24 air
/fill ~ ~-1 ~11 ~24 ~-1 ~13 stone
```

| # | Acción | Esperado | Copiar |
|---|---|---|---|
| 24.1 | Un encuentro y mira si ThePredict aparece en un `ChunkLoadEvent`. Anota el coste de una instantánea y de una columna | Ningún `ChunkLoadEvent` con ThePredict en la pila (DT-64). Si no puedes medirlo, «no medido» | Lo visto o «no medido» |
| 24.2 | Rompe, coloca, lava, fuego, pistón, TNT, `/fill` y un `setblock` | Anota qué cambios no tienen evento (DT-65) | Lo visto o «no medido» |
| 24.3 | Un jefe de mucha vida junto a nieve polvo, cactus, magma, fuego y un precipicio. Después prueba `AIPathfindingMalus` | Anota si cruza y si el malus cambia la ruta (DT-66). La sintaxis no está verificada | Lo visto o «no medido» |
| 24.4 | Mide el empuje con F3, sin aviso y con un aviso `HEAVY`. Mira una línea `hazard` | Sin aviso no hay empuje letal. Anota si el evento nombra al jefe (DT-67) | Lo visto o «no medido» |
| 24.5 | Arenas `cliff`, `lava_moat` y `pillars` con `guard`, cinco peleas | Ninguna caída por un micro. Anota las caídas de la navegación nativa aparte | Lo visto o «no medido» |
| 24.6 | `predictorzone{fact=edge_behind}` y el placeholder `pushdanger` | Respuesta en un segundo o menos y hechos que no cambian más de una vez cada dos segundos (DT-68) | Lo visto o «no medido» |
| 24.7 | Diez encuentros con lava y bordes en `guard`. Mira `tick_work` | Anota p99, el paso `zone` y la memoria (DT-64) | `tick_work` o «no medido» |

Al terminar: vuelve `zone.mode` a `observe`.

## Sesión 25. Coordinación de grupo (M25)

**Cierra:** DT-69, DT-70, DT-71, DT-72 y DT-73 si los pasos salen como se espera. El paso 25.8 es de pago y no se ejecuta sin aprobación. **Costo:** cero en 25.1 a 25.7. **Pendiente de servidor:** esta sesión no se ha ejecutado. Nada de lo que sigue está verificado en un servidor Paper. La sintaxis de las condiciones de grupo queda (pendiente de servidor).

Preparación: servidor parado. `group.mode: fair`. No subas el default a `coordinate` ni a `full`. Copia `archivos/25-grupo` (`jefe_invocador`, `patrulla_5`, `trio_hechiceros`). `jev.apiKey: ''`.

| # | Acción | Esperado | Copiar |
|---|---|---|---|
| 25.1 | 1, 5 y 10 encuentros de 5 mobs con `fair` y con `coordinate`. Anota `tick_work` | p99 del paso `group` (DT-69). Si no puedes medirlo, «no medido» | Lo visto o «no medido» |
| 25.2 | `jefe_invocador` con la escuadra por defecto, con `itt=true` y con `ctt=true`. Mata al jefe con `succeed` y con `break` | Escuadra, líder y grupo de tabla en `inspect` (DT-70) | Lo visto o «no medido» |
| 25.3 | `patrulla_5` contra el usuario, 5 peleas con `fair` | Nunca más de 2 preparando a la vez. ¿Se percibe como ritmo o como espera? (DT-71) | Lo visto o «no medido» |
| 25.4 | `trio_hechiceros`, 5 peleas | Nunca dos avisos grandes a la vez. Anota `IMPACT_DEVIATION_TICKS` (DT-71) | Lo visto o «no medido» |
| 25.5 | `patrulla_5` con `coordinate` en campo abierto y en un pasillo | Cinco salidas por detrás sin golpes desde el sector libre (DT-72). Separación &lt; 0,6 bloques: NO VERIFICADO | Lo visto o «no medido» |
| 25.6 | `predictorrole`, `predictorplan`, `predictortoken` y los placeholders de grupo | Respuesta en un segundo o menos (DT-73). Sintaxis (pendiente de servidor) | Lo visto o «no medido» |
| 25.7 | 10 encuentros de 8 mobs con `coordinate` | `tick_work` p99, `GROUP_REPLAN_DEFERRED` y `GROUP_FRAGMENTED` (DT-69) | Lo visto o «no medido» |
| 25.8 | Opcional, solo con aprobación: `full` y `plan_and_leader`, 10 peleas | Tokens reales y latencia (DT-73). No ejecutar sin aprobación | No ejecutado |

Al terminar: vuelve `group.mode` a `fair`.

## Sesión 26. Patrones del enemigo (M26)

**Cierra:** DT-74, DT-75, DT-76, DT-77 y DT-78 si los pasos salen como se espera. El paso 26.7 es de pago y no se ejecuta sin aprobación. **Costo:** cero en 26.1 a 26.6. **Pendiente de servidor:** esta sesión no se ha ejecutado. Nada de lo que sigue está verificado en un servidor Paper.

Preparación: servidor parado. `patterns.mode: observe`. Copia `archivos/26-patrones`. Pon `records.level: full` solo para esta sesión y vuelve a `decisions` al terminar. `jev.apiKey: ''`. Los mensajes de `~onSignal:PREDICTOR_PATTERN_*` son diagnóstico de chat, no una recomendación de efecto.

| # | Acción | Esperado | Copiar |
|---|---|---|---|
| 26.1 | Frente al jefe inmóvil: golpes cargados y sin cargar, crítico, esprint, escudo, rodeos (andando, esprintando y agachado), salto, arco, beber, perla, girar la cámara, torre, 5 perlas, 5 bolas de nieve, golpes a bloques, 10 golpes al aire y lanza | Ningún `STRIKE*` falso. `AIR_SWING` solo como diagnóstico (DT-75) | Lo visto o «no medido» |
| 26.2 | Las mismas acciones tras un muro, invisible, y comer a 10 y a 20 bloques | Sin tokens visuales tras el muro ni invisible. Comer cuenta a 10 y no a 20 | Lo visto o «no medido» |
| 26.3 | Guion «rodea a la izquierda», luego a la derecha. Repite con clics sin recarga y con críticos | Activación en ≤ 60 s y pérdida en ≤ 20 s (DT-76) | Lo visto o «no medido» |
| 26.4 | «Bebe al 30 %», «perla al 30 %», falla los avisos de esquivar y bloquea los de bloquear | `HEALS_AT_LOW_HP`, `PEARLS_WHEN_LOW` y `FAILS_ANSWER_DODGE` activos; `FAILS_ANSWER_BLOCK` no (DT-76) | Lo visto o «no medido» |
| 26.5 | Con `bias` y un veredicto importado, peldaños 8, 4 y 0 | Cuota, descanso y señal en el chat. Anota si se siente justo (DT-77) | Lo visto o «no medido» |
| 26.6 | 10 encuentros y `m13Measure` en el servidor | `tick_work` p99 y diferidos ≈ 0 (DT-74) | Lo visto o «no medido» |
| 26.7 | Opcional, solo con aprobación: `full`, 5 min con hechos | Tokens reales con 0, 2 y 4 hechos (DT-78). No ejecutar sin aprobación | No ejecutado |

Al terminar: vuelve `patterns.mode` a `observe` y `records.level` a `decisions`.

## Sesión 27. Memoria por jugador (M27)

**Cierra:** DT-79, DT-80, DT-81 y DT-83 si los pasos salen como se espera. DT-82 no se cierra: la red no se implementa. DT-84 solo con el paso 27.8. **Costo:** cero en 27.1 a 27.6. El paso 27.8 es de pago (unos 0,01 USD por brazo) y no se ejecuta sin aprobación. **Pendiente de servidor:** esta sesión no se ha ejecutado. La 27.7 no se prepara.

Preparación: servidor parado. `memory.mode: record`. `patterns.mode: observe`. Copia `archivos/27-memoria`. Pon `records.level: full` solo para esta sesión y vuelve a `decisions` al terminar. `jev.apiKey: ''`. El jefe de ejemplo no tiene condición, placeholder ni señal de memoria.

| # | Acción | Esperado | Copiar |
|---|---|---|---|
| 27.1 | Con `record`: gana, pierde, sal, entra, reinicia y muere contra el jefe con 4 esbirros del mismo perfil | Conteos y narrativa exactos tras el reinicio. `bytes_after` ≤ 600. Un cierre sucio no duplica la pelea (DT-79) | Lo visto o «no medido» |
| 27.2 | 20 entradas, arranque en `WARMING` y `/reload` | ≥ 95 % `LOADED` antes de `PlayerJoinEvent`. En `WARMING`, la primera pelea da `NOT_LOADED` (DT-79) | Lo visto o «no medido» |
| 27.3 | Con `seed` y `bias` y veredictos importados: 3 peleas a la izquierda y 2 a la derecha | Activa antes, nunca con menos de 4 ensayos propios, y aparece `MEMORY_STYLE_BREAK` (DT-80, DT-81) | Lo visto o «no medido» |
| 27.4 | Pierde a propósito y luego juega bien. Reinicia entre medias | El peldaño baja al perder, sube al ganar, persiste y no sube por una muerte (DT-80) | Lo visto o «no medido» |
| 27.5 | Pelea tras perder, tras ganar y como jugador nuevo. Repite con `thepredict.memory.exempt` | Nada de la memoria se ve en el juego. `inspect` solo para el admin (DT-83) | Lo visto o «no medido» |
| 27.6 | `memory export`, `memory reset`, `memory import` y una poda | Ida y vuelta idénticas. `reset` vuelve a frío. La poda no altera `tick_work` | Lo visto o «no medido» |
| 27.8 | Opcional, solo con aprobación: `full`, 5 min con hechos | Tokens reales con 0, 1 y 2 hechos (DT-84). No ejecutar sin aprobación | No ejecutado |

Al terminar: vuelve `memory.mode` a `record` y `records.level` a `decisions`.

## Sesión 28. Valor aprendido y calibración (M28)

**Cierra:** DT-85, DT-86, DT-87 y DT-89 si los pasos salen como se espera. DT-88 solo con 28.3. DT-91 solo con 28.4. DT-90 no tiene paso gratis. **Costo:** cero en 28.1, 28.2 y 28.5. 28.3 y 28.4 son de pago (techo 1,00 USD) y no se ejecutan sin aprobación. **Pendiente de servidor:** esta sesión no se ha ejecutado. Errata E28-16 aplicada: el paso 28.2 incluye un jefe con `PredictorTraining: true` y otro sin la clave.

Preparación: servidor parado. `learning.collect: true`. `decision.candidateScoring` se queda en `priority` hasta el paso que diga lo contrario. `records.level: decisions` (`full` solo en 28.2). Copia `archivos/28-aprendizaje`. `jev.apiKey: ''` en los pasos gratis.

| # | Acción | Esperado | Copiar |
|---|---|---|---|
| 28.1 | 10 duelos entre entidades y 5 peleas contra el jefe, con `records tag m28-1` | Cada `outcome` se une a un `acceptance` y a un `credit`. `LOCAL_ARGMAX` tiene propensión 1. `V005` no pierde filas (DT-85, DT-86) | Lo visto o «no medido» |
| 28.2 | `scope: bench` y `candidateScoring: learned` tras un veredicto `PASS_SIM_ONLY`. 20 duelos con `exploreInDuels: false` y 10 con `true`. Luego entra el usuario. Un jefe con `PredictorTraining: true` y otro sin la clave | `explore` solo sin jugador, o en el jefe de entrenamiento, y como mucho `maxShare`. `learned` no actúa con el usuario en `bench` (DT-87, DT-89) | Lo visto o «no medido» |
| 28.3 | Opcional, solo con aprobación: 10 peleas `raw` y 10 `calibrated` | Las mismas aceptaciones en respuestas iguales (DT-88). No ejecutar sin aprobación | No ejecutado |
| 28.4 | Opcional, solo con aprobación: 20 peleas con `voiSkip: shadow` | Ninguna solicitud cambia. Anota la fracción `would_skip` (DT-91). No ejecutar sin aprobación | No ejecutado |
| 28.5 | `build`, `export`, `import` en otra carpeta, veredicto, activar, `rollback` y `disable`, sin reiniciar | La misma SHA-256. `disable` vuelve a `priority` en el ciclo siguiente (DT-89) | Lo visto o «no medido» |

Al terminar: vuelve `decision.candidateScoring` a `priority`, `learning.voiSkip` a `"off"` y `records.level` a `decisions`.

## Sesión 29. IA aprendida y autoconfiguración (M29)

**Cierra:** DT-95 si 29.1 y 29.4 salen como se espera. DT-96 con 29.5. DT-97 con 29.6. DT-92, DT-93 y DT-94 solo con 29.2. DT-94 también cita 29.3. DT-99 solo con 29.7. DT-98 no tiene paso: la cinta no se graba aquí. **Costo:** cero en 29.1, 29.4, 29.5 y 29.6. 29.2 es opcional, con clave, ≈ 0,10 USD. 29.3 A es gratis; 29.3 B es con clave y solo con `PASS`. 29.7 es opcional, sin coste de TypeSafe, y no se levanta Kev en esta preparación. **Pendiente de servidor:** esta sesión no se ha ejecutado.

Preparación: servidor parado. El duelo de M18 y el jefe de M28 con 4–6 tácticas. `records.level: decisions` (`full` solo en 29.2). `learning.collect: true` y `distill.collect: true`. `distill.mode: off` hasta el paso que diga lo contrario. `autoconfig.autoApply` se queda en `off`. Copia `archivos/29-m29`. `inspect` y `distill inspect` en otra ventana. `jev.apiKey: ''` en los pasos gratis.

| # | Acción | Esperado | Copiar |
|---|---|---|---|
| 29.1 | Arranca con una copia de la base `V005`. 10 duelos 1 contra 1 y luego 3 contra 1. `records tag m29-1` | `V006` sin pérdida. Líneas `regime` con su causa y sin cambios durante un aviso. Sin `planned.distill` con `distill.mode: off` (DT-95) | Lo visto o «no medido» |
| 29.2 | Opcional, solo con aprobación: artefacto importado, `shadow`, 20 peleas y 10 duelos con clave | Ningún delta de etiqueta por respuesta de Jev. No inferioridad por celda. Acuerdo con Wilson, solo como métrica. Ninguna decisión cambia (DT-92, DT-93, DT-94). No ejecutar sin aprobación | No ejecutado |
| 29.3 | A: `assist` y `scope: bench`, 20 duelos; al final entra el usuario. B, solo con aprobación y con `PASS`: `scope: all`, 10 peleas | A: `DISTILLED` en celdas cubiertas, auditoría 1 de cada 10, nada destilado contra el usuario. B: TTK y casts frente al simulador (DT-94). B no se ejecuta sin aprobación | A: lo visto o «no medido». B: no ejecutado |
| 29.4 | Jefe contra 1 entidad. A los 20 s entran 3 más. Luego salen | Un cambio por transición, con 40 ticks de permanencia. Ninguno durante un aviso. En `coordinate`, todos en el mismo tick de replanificación (DT-95) | Lo visto o «no medido» |
| 29.5 | `distill build`, `export`, `import`, veredicto, `shadow`, `assist`, degradación forzada, `distill mode assist`, `rollback`, `disable all` | La misma SHA-256. La degradación baja un modo y no sube sola. `disable all` deja la captura siguiente sin delegar (DT-96). `build` no entrena un modelo | Lo visto o «no medido» |
| 29.6 | `autoconfig import`, `show`, `apply`, 10 duelos, `revert` | Origen por clave. `authorPinned` intacto. Ningún archivo del autor cambia, comprobado por huella. `revert` vuelve al perfil del autor (DT-97) | Lo visto o «no medido» |
| 29.7 | Opcional: Kev-0.8B en `fp32`, `evalRun -Psources=bank -Pprovider=kev`, 5 duelos. No levantar Kev sin el usuario | Banco frente al brazo D. p50 y p95. Sin la clave de TypeSafe. Aviso de umbral crudo (DT-99). No ejecutar en esta preparación | No ejecutado |

Al terminar: vuelve `distill.mode` a `off` y `records.level` a `decisions`. No actives `autonomous`: no hay cinta.

## Sesión AIConfig. Carpeta de configuración y migración (P-AIConfig)

**Cierra:** DT-100, si MythicMobs no avisa ni falla por la carpeta `AIConfig` al arrancar ni con `/mm reload`. **Costo:** cero. Sin clave. No se llama a Jev. **Pendiente de servidor:** esta sesión no se ha ejecutado.

Preparación: servidor parado. No hay carpeta `AIConfig-…` en el paquete: se usan los archivos que el propio plugin crea y, en los pasos 4 y 5, copias hechas a mano. Parte de una instalación en la que no existan ni `plugins/MythicMobs/AIConfig/` ni la ubicación antigua (`plugins/ThePredict/config.yml` y `plugins/ThePredict/AI/`).

| # | Acción | Esperado | Copiar |
|---|---|---|---|
| AIConfig.1 | Arranque limpio | Se crea `plugins/MythicMobs/AIConfig/` con `config.yml`, `Profiles/README.txt` y `Profiles/example.yml.example`. El boss de ejemplo queda en `plugins/ThePredict/examples/`. Nada fuera de `AIConfig/` dentro de la carpeta de MythicMobs. Un segundo arranque no cambia esos archivos | `ls` de las dos carpetas y la línea `Created setup files:` |
| AIConfig.2 | Mira el registro de arranque y ejecuta `/mm reload` (DT-100) | MythicMobs no da avisos ni errores por la carpeta `AIConfig`, ni al arrancar ni al recargar | El registro de arranque y el de `/mm reload` |
| AIConfig.3 | Copia `Profiles/example.yml.example` a `Profiles/ejemplo.yml` (o el perfil de `plugins/ThePredict/examples/AI/ThePredictGuardian.yml` a `Profiles/`) y `/mm reload` | El perfil nuevo se lee. `status` lo cuenta en `Profiles:`. Un problema de carga, si lo provocas, cita `AIConfig/Profiles/` | La línea `Profiles` |
| AIConfig.4 | Servidor parado. Deja archivos en la ubicación antigua y vacía `AIConfig/` de `config.yml` y de perfiles (el README y la plantilla pueden quedarse). Arranca | Aparecen la propuesta en `plugins/ThePredict/migration/<fecha>/` (`AIConfig/config.yml.proposed`, `AIConfig/Profiles/<archivo>.proposed` y `report.md`) y el aviso. No se mueve ni se borra ningún archivo. No hay consultas. Un segundo arranque reutiliza la misma propuesta | El aviso, `report.md` y un `ls` de las dos ubicaciones antes y después |
| AIConfig.5 | Con las dos ubicaciones con contenido (maestro o un perfil en la antigua, y `config.yml` o un perfil en `AIConfig/`), arranca | Manda `AIConfig/`. El aviso dice que los archivos de la ubicación antigua se ignoran. No hay propuesta de traslado | La línea `Location` de `status` y el aviso al entrar |

Al terminar: deja la configuración vigente solo en `plugins/MythicMobs/AIConfig/`. No borres la ubicación antigua hasta haber copiado a mano lo que quieras conservar.

---

# Fase 2. Con clave (pago)

Precio publicado de Jev 1.13: 0,042 USD por millón de tokens de entrada (confírmalo en `https://docs.typesafe.ai/models` antes de empezar). Las cifras de cada sesión son estimaciones (G13 §9); `/thepredict status` muestra el consumo real.

**Cómo poner la clave sin mostrarla**, en el terminal que arranca el servidor y solo en ese:

```bash
set -a; source "$REPO/.env"; set +a
[ -n "$TYPESAFE_API_KEY" ] && echo "clave cargada"
cd "$BASE" && "$JAVA" -Xms2G -Xmx2G -jar paper-26.2-129.jar --nogui 2>&1 | tee start-K.log
```

No ejecutes `cat`, `echo` ni `env` sobre la clave. Para volver a la fase 1, cierra ese terminal y arranca desde uno nuevo.

## Sesión K1. Clave y primer encuentro corto con un mob

**Costo estimado:** unas 60 solicitudes y entre 37 000 y 42 000 tokens (≈ 0,002 USD) por minuto de combate con un mob. **Cierra:** DT-31 (tokens reales en combate), M12.2 (`check` real en juego), M14.5 pasos 2 y 4 (Jev), M08 (latencia desde el host, primera muestra). **Guiones:** M01 12.8 (primera parte), G13 §6.1 (`check`).

Preparación (servidor parado): `decision.logDecisions: true` en `config.yml`; recuperar el mob de `07-senales` y el perfil del ejemplo, porque las sesiones 10 a 12 los sobrescribieron: `cp "$PACK/07-senales/plugins/MythicMobs/Mobs/ThePredictGuardian.yml" "$BASE/plugins/MythicMobs/Mobs/"` y `cp "$BASE/plugins/ThePredict/examples/AI/ThePredictGuardian.yml" "$BASE/plugins/MythicMobs/AIConfig/Profiles/"`; arrancar con la clave como arriba; `/kill @e[type=!minecraft:player]`.

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| K1.1 | Arranque con la clave | `Checking Jev without spending tokens; the result follows in the console.` y después `Jev check: READY (credential valid; model unverified until first inference). Decisions are enabled. The check reads the key and the model list; it does not verify an inference or its quality.` Ningún texto de la clave | Las dos líneas |
| K1.2 | `/thepredict status` | `State: Jev READY (…)`; `Model: configured jev-1.13.0; last answer none yet` | Sección `Service` |
| K1.3 | `/thepredict check` | Enseguida `Jev check started; the result arrives here without blocking the server. …`; al llegar `Jev check: READY (…). Decisions are enabled. …` (**M14.5 paso 2**) | Las dos líneas |
| K1.4 | A en `SURVIVAL`; `/mm m spawn ThePredictGuardian 1` a 6 bloques; golpearlo y pelear **60 s**; `inspect nearest` a los 20 y 40 s | Líneas `Mob <uuid> JEV <táctica>`; `inspect` con `level JEV` y `confidence` real (no `1.00`); mensajes `signal <táctica> jev`; ningún `MODEL_CHANGED` (**M14.5 paso 4**) | Consola, chat e `inspect` |
| K1.5 | `/thepredict status` justo al terminar | `Model: … last answer jev-1.13.0`; `Server: <x>/600 requests in 60 s, …`; `Input tokens: <t>/600000 in 60 s; estimated cost <c> USD per hour …`; `roundtrip` con p50/p95/p99; `totals: answered=<n> input_tokens=<m> …`; `fallbacks` (si hay `low_confidence=`, anótalo: es el punto de revisión del usuario) | Toda la salida |
| K1.6 | `/kill @e[type=!minecraft:player]` y `status` 70 s después | `Server: 0/600 requests in 60 s` | La línea |

## Sesión K2. Pérdida de servicio simulada

**Costo estimado:** como K1 durante 2 min (≈ 0,004 USD). **Cierra:** M10.1, M10.6, M14.5 paso 6. **Guiones:** M01 12.8 (segunda parte).

Los jugadores deben estar conectados por `localhost` desde la misma máquina, para que cortar la red del host no los desconecte.

| # | Acción exacta | Esperado exacto |
|---|---|---|
| K2.1 | Combate como K1.4 | Líneas `JEV <táctica>` |
| K2.2 | En pleno combate, cortar la red del host 30 s (desactivar Wi-Fi o desconectar el cable) sin tocar la clave | La intención `JEV` sigue hasta vencer y después `LOCAL` con `fell back: SERVICE_PAUSED` en ≤2 ticks; `status`: `Pause: ends in … s` y `Recent failures`; ningún error ni mob congelado |
| K2.3 | Restablecer la red | Vuelta a `JEV` con la primera respuesta aceptada |

Copiar: consola del corte, `status` durante la pausa y después.

## Sesión K3. Latencia desde el host

**Costo estimado:** §6.1 sin tokens; §6.2 ≈ 2 900 solicitudes, ≈ 2,9 M tokens, ≈ 0,12 USD. **Cierra:** M13.4, M08 (latencia del host). **Guiones:** G13 §6.1–6.3, §6.5.

| # | Acción (G13) | Copiar |
|---|---|---|
| K3.1 | §6.1: el bucle de 50 llamadas con `curl -K -` (la clave va por la entrada estándar, nunca en la línea de comandos) | `check-latency.txt` (sin cabeceras) y percentiles de `time_total` |
| K3.2 | §6.2: dos encuentros simultáneos, 2 min por escenario de §7.2, en dos horarios | La línea `roundtrip` al final de cada bloque |
| K3.3 | §6.3 | `capture`, `dispatch`, `roundtrip`, `inbox_wait`, `apply` |
| K3.4 | §6.5: aplicar la regla con los datos | La decisión sobre `timeoutMillis` |

## Sesión K4. Carga con clave

**Costo estimado:** 1 mob ≈ 0,018 USD; 10 mobs ≈ 0,10 USD; 30 mobs ≈ 0,23 USD (10 min cada uno). **Cierra:** M13.3 (variante con clave), capacidad del host (G13 §5.6). **Guiones:** G13 §5.2 variante 5.B, incluido el paso 7 (corte de red de 30 s con 30 mobs).

Mismos pasos y tabla que la sesión 11.3, con la clave. Copiar además `Input tokens`, `Server` y `fallbacks` en los minutos 2, 4, 6, 8 y 10.

## Sesión K5. A/B con clave y banco de escenarios

**Costo estimado:** A/B brazo B ≈ 0,11–0,17 USD; banco: se imprime antes de empezar (tope `-PmaxCalls`, 1 300). **Cierra:** M13.5 (brazo B con Jev), M13.6 (banco grabado), DT-28, DT-31 (umbrales). **Guiones:** G13 §7.2 (brazo B con clave), §8.1–8.5.

| # | Acción (G13) | Copiar |
|---|---|---|
| K5.1 | §7.2 brazo B con clave: 6 escenarios × 10 repeticiones | Lo mismo que la sesión 12 más `answered=`, `input_tokens=` e `Input tokens` |
| K5.2 | §8.1: `debug.recordScenarios: true`, reiniciar con la clave, jugar los escenarios | Número de archivos en `plugins/ThePredict/scenarios/`; la línea `Scenario recording reached its limit of 1000 files; later decisions are not recorded` si aparece |
| K5.3 | §8.2: `grep -c -E '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' plugins/ThePredict/scenarios/*.json` | 0 en todos |
| K5.4 | §8.3: `debug.recordScenarios: false` y reiniciar | — |
| K5.5 | §8.4: copiar los escenarios a la evidencia y **etiquetarlos tú** (tácticas aceptables y, si la hay, la prohibida). Las etiquetas de calibración ya no son las de las semillas (cinco desde M16.9, que retiró `seed-low-health`): desde M16 se revisan en el banco `src/test/resources/scenarios/bank/` (hoja con `./gradlew labelReviewSheet --offline`, [revisión pendiente](../../../CHANGELOG.md#revisión-pendiente-del-usuario)) | Los archivos etiquetados |
| K5.6 | §8.5, en la máquina de desarrollo: `./gradlew liveScenarioBench --no-daemon -PliveJev=true -PscenarioDir=<carpeta> -Prepetitions=<n>` en un shell con la clave cargada como arriba | `build/reports/m16/live-scenario-bench.md` y `build/reports/m16/answers.jsonl` al lado (las respuestas crudas, sin clave ni cabeceras; con una carpeta grabada, brazo `REPLAY`) |

## Sesión 16 con clave. Efectividad con Jev (M16.11, pasos 16.2 y 16.3)

**Solo con tu aprobación explícita del gasto.** **Costo estimado de 16.2:** 3 combates × ≈ 60 solicitudes × ≈ 1 400 tokens (cota a 1,5 caracteres por token con `compact`) ≈ 252 000 tokens ≈ **0,011 USD**. Esta sesión fija `compact` a propósito; con el default `full` (≈ 1 884 tokens reales por solicitud, M16.10) serían ≈ 340 000 tokens, ≈ 0,014 USD. **Cierra:** M16.8 (ventanas `JEV` y `ALTERNATIVE` con daño y alcance coherentes con lo observado), M16.6 (contingencias disparadas en combate). **Guion:** [G13 sesión 16](../Guion-servidor-0.0.1.md#16-efectividad-de-las-respuestas-m1611).

Preparación: la de la sesión 16 sin clave (`03-guardian`, `decision.logDecisions: true`, y además `decision.questionDetail: compact` escrito a mano, porque el default es `full`), arrancando con la clave como al principio de esta fase; `/kill @e[type=!minecraft:player]`.

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 16.2.a | `/thepredict status` | `State: Jev READY (…)`; sección `Effectiveness` | `Service`, `Consumption` y `Effectiveness` |
| 16.2.b | 3 combates de 60 s como 16.1.b, con `inspect nearest` a los 30 s de cada uno | Líneas `Mob <uuid> JEV <táctica>` y `Outcome JEV <táctica> dealt=… taken=… reached=… casts=… hits=…`; si una contingencia se dispara (por ejemplo te acercas al cuerpo a cuerpo o le bajas la vida a crítica), una línea `Mob <uuid> ALTERNATIVE <táctica>` y su `Outcome ALTERNATIVE …` | Todas las líneas `Mob …` y `Outcome …`, e `inspect` |
| 16.2.c | `/thepredict status` al terminar | `outcomes: level=JEV windows=…` (y `level=ALTERNATIVE` si hubo); `contingencies: asked=<a> fired=<f> effective=<e>` con `asked` > 0; `Input tokens` y `totals: answered=… input_tokens=…` (el consumo real frente a los ≈ 1 200 tokens por solicitud estimados); `dispatch` sin `prepare_failed` | Toda la salida |
| 16.3 | [G13 §7](../Guion-servidor-0.0.1.md#7-ab-frente-a-la-ia-de-mythicmobs-m135), brazo B con clave, anotando además las líneas `outcomes:` de `status` antes y después de cada bloque | Las medidas de §7 más `outcomes:` por nivel. **Costo estimado con `compact`:** ≈ 3 600 solicitudes × ≈ 1 400 tokens ≈ 5,0 M tokens ≈ **0,21 USD** (con `standard`, ≈ 0,43 USD) | Lo de la sesión K5.1 más `outcomes:` |

Al terminar: `decision.logDecisions: false` y cerrar el terminal con la clave.

## Sesión 18 con clave. Duelo con Jev (M18, paso 18.4)

**Solo con tu aprobación explícita del gasto y después del re-banco de M16.10 (hecho el 2026-09-30).** **Costo estimado (W7, a 1,5 caracteres por token, con el default `full`):** 30 rondas (10 de A, 10 de B, 10 de C) × ≈ 60 solicitudes por encuentro y ronda ≈ 2 400 solicitudes × ≈ 3 204 tokens ≈ 7,7 M tokens ≈ **0,32 USD** (máximo, con rondas de 90 s, ≈ 0,48 USD). Con los tokens reales del re-banco (≈ 1 884 por solicitud con `full`) ≈ **0,19 USD** (máximo ≈ 0,28). Con `decision.questionDetail: compact` escrito a mano, ≈ 0,14 USD a 1,5 c/t (≈ 0,096 con los 953 tokens reales). **Techo:** 0,60 USD: detente si `input tokens` de `/thepredict duel status` pasa de **14 000 000**. **Guion:** [G13 §13](../Guion-servidor-0.0.1.md#13-duelo-de-mobs-controlados-m186). **Cierra:** la parte con clave de M18.3 y M18.4.

Preparación: la de la sesión 18 (paquete `13-duelo`, arena del paso 18.0), arrancando con la clave como al principio de esta fase; la ronda de limpieza.

| # | Acción exacta | Esperado exacto | Copiar |
|---|---|---|---|
| 18.4.a | `/thepredict check`; `/thepredict status` | `Jev check: READY (…). Decisions are enabled. …`; `State: Jev READY (…)` | Las dos salidas |
| 18.4.b | `/thepredict duel start jev-<fecha> duel_red duel_blue duel 0 65 0 30`; 30 rondas en el orden de la tabla de abajo; `/thepredict duel status` y `/thepredict status` cada 5 rondas | `started: …` como en 18.3; en `duel status`, `requests <r>, input tokens <t>` creciendo ≈ 60 solicitudes por encuentro y ronda. **Detente** si `<t>` pasa de 14 000 000 | Cada `duel status` y cada `status` |
| 18.4.c | `/thepredict duel stop`; `/thepredict status` | `stopped after 30 rounds, 0 rows dropped; …` | Las dos salidas |
| 18.5 | La copia de vuelta de la sesión 18, con la carpeta `jev-<fecha>-<UTC>` | Todos los archivos; el `grep -c` de UUID sobre las líneas `body` da `0`; `duelJoin` reconstruye cada solicitud (`planned`, `body`, `result` y sus `acceptance`) | La carpeta completa y `decisions-joined.json` |

Orden de las 30 rondas con clave (A, B, C, C, B, A, repetido 5 veces; lados intercambiados cada dos rondas):

| Ronda (y cada 6 más) | Brazo | `<rojo>` | `<azul>` | `<z rojo>` | `<z azul>` |
|---|---|---|---|---|---|
| 1 | A | `DuelRed` | `DuelBlueClassic` | `-9.5` | `10.5` |
| 2 | B | `DuelRedClassic` | `DuelBlue` | `-9.5` | `10.5` |
| 3 | C | `DuelRed` | `DuelBlue` | `10.5` | `-9.5` |
| 4 | C | `DuelRed` | `DuelBlue` | `10.5` | `-9.5` |
| 5 | B | `DuelRedClassic` | `DuelBlue` | `-9.5` | `10.5` |
| 6 | A | `DuelRed` | `DuelBlueClassic` | `-9.5` | `10.5` |

Al terminar: cerrar el terminal con la clave.

---

# Aceptación alojada de la 0.0.1 (M14.5)

La 0.0.1 queda aceptada cuando el mismo boss cumple los ocho pasos con evidencia. Detalle en [G13 §12](../Guion-servidor-0.0.1.md#12-aceptación-alojada-m145).

| Paso de M14.5 | Dónde se prueba |
|---|---|
| 1. Instalación limpia y primer inicio sin errores | Sesión 1 (1.5–1.7) |
| 2. Clave por entorno y `check` en `READY` | Sesión K1 (K1.1–K1.3) |
| 3. Boss de ejemplo con Predictor apagado: IA de MythicMobs | Sesión 6 (6.3) |
| 4. Predictor activo: IA de ThePredict, decisiones de Jev y movimientos en `inspect` | Sesión 6 (6.5–6.10) y K1 (K1.4) |
| 5. Dos instancias y dos mundos: aislamiento | Sesión 11 (11.3, escalón de 10 mobs en dos mundos) y sesión 7 (7.6) |
| 6. Pérdida de servicio simulada y vuelta a `JEV` | Sesión K2 |
| 7. `/mm reload` y carga de chunks: metas reinstaladas | Sesiones 4 (4.3, 4.4), 6 (6.12, 6.13) y 7 (7.3, 7.7) |
| 8. Desactivar el plugin: mobs con su IA de MythicMobs | Paso final siguiente |

**Paso final (M14.5 paso 8).** Servidor parado. Sacar el pack de verificación y el permiso (`mv "$BASE/plugins/MythicMobs/Mobs/ThePredictVerification.yml" "$BASE/plugins/MythicMobs/Skills/ThePredictVerification.yml" "$BASE/permissions.yml" <carpeta aparte>`), dejar dos `ThePredictGuardian` vivos del mob de `03-guardian` y mover `plugins/ThePredict-0.0.1.jar` fuera de `plugins`. Arrancar sin clave. Esperado: MythicMobs carga los dos mobs y estos persiguen y golpean con su IA de MythicMobs; ninguna línea de ThePredict; si MythicMobs escribe algo sobre la clave `Predictor`, anotarlo literalmente. Copiar: `grep -nE 'ERROR\|SEVERE\|ThePredict\|Predictor' start-N.log` y la observación. Después devolver el JAR y los archivos.
