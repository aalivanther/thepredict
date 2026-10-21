# M01. Registro de decisiones técnicas

> Módulo: [M01](M01-Verificacion-nativa.md) · Guion: [M01-Guion-de-servidor.md](M01-Guion-de-servidor.md) · Reglas: W2 y W3 de [00](00-Arquitectura-y-reglas.md) · Consumen: M03, M04, M05, M06, M07, M10, M11, M12, M13
>
> **Revisión M12 (2026-09-29):** M12 retiró las herramientas provisionales de depuración (presets, `autoinstall`, `unbind`, `capture`, `reapply`, `signal`, `cast`, `trace`, `signals` y la sonda `predictorprobe`). El [guion](M01-Guion-de-servidor.md) conserva los números de paso y los reescribe con `status`, `inspect`, `debug intent|release|goals`, la instalación automática de las sesiones, YAML nativo de MythicMobs y spark; las entradas afectadas lo indican en su evidencia. Ninguna entrada cambia de estado: no hay evidencia de servidor.
>
> **Estado del registro (2026-09-29): Pendiente de servidor.** Ninguna entrada tiene todavía evidencia observada en juego. La evidencia estática (`javap` sobre MythicMobs Premium 5.13.1-SNAPSHOT y Paper API 26.2 build 129) se cita como tal y no confirma comportamiento. Mientras una entrada esté `Abierta`, su módulo usa la alternativa indicada.

## Cómo se usa

- Cada entrada responde una pregunta con evidencia reproducible. Se rellena al ejecutar el paso del guion indicado en **Pasos**.
- **Estado:** `Abierta` (sin evidencia de servidor; el módulo usa la alternativa), `Confirmada` (la evidencia respalda el diseño del plan) o `Alternativa aplicada` (la evidencia lo contradice y el módulo usa la alternativa). `Resuelta` (desde M13) marca una entrada que la cuenta real de TypeSafe zanja por completo, sin necesitar servidor.
- La evidencia se copia literalmente: versiones y huellas, pasos, salida de consola y del chat. Nunca se escribe un resultado que no se haya observado.
- Una entrada solo cambia de estado con la evidencia pegada debajo, con fecha y quién la obtuvo.

### Plantilla

```markdown
### DT-nn. Título

- **Pregunta:**
- **Contexto:** qué módulo la necesita y por qué.
- **Evidencia estática:** javap o fuentes, si existe.
- **Evidencia en servidor:** versiones (ver DT-00), pasos del guion, salida literal. Fecha y persona.
- **Decisión:**
- **Consecuencia:** qué cambia en el diseño o la configuración.
- **Alternativa si no se confirma:** la del plan §13 o la del módulo.
- **Módulos afectados:**
- **Estado:** Abierta | Confirmada | Alternativa aplicada | Resuelta | Descartada
```

## Índice

| DT | Pregunta (plan §13 y subpuntos) | Subpunto | Pasos | Desbloquea | Estado |
|---|---|---|---|---|---|
| [DT-00](#dt-00-entorno-de-verificación) | Entorno, versiones y huellas | M01.1 | 1.1–1.5 | Todas | Abierta |
| [DT-01](#dt-01-condiciones-del-mob-en-cada-meta-condicional) | Condiciones del mob en cada meta condicional | M01.5 | 5.1, 5.7, 5.9 | M05 | Abierta |
| [DT-02](#dt-02-registro-de-extensiones-en-primer-arranque-y-recarga) | Registro de extensiones en primer arranque y recarga | M01.3 | 1.5, 3.1–3.3 | M04 | Abierta |
| [DT-03](#dt-03-registro-por-eventos-de-carga) | Registro por eventos de carga en primer arranque y recarga | M01.3 | 1.5, 3.1, 3.3 | M04 | Abierta |
| [DT-04](#dt-04-restauración-con-reapplyaigoalselectors) | Restauración con `reapplyAIGoalSelectors`, con y sin `AIGoalSelectors` | M01.4 | 4.5 | M05, M10 | Abierta |
| [DT-05](#dt-05-instalación-y-reinstalación-de-metas) | Instalación tras spawn y carga; reinstalación tras carga, recarga o skills | M01.4 | 4.1–4.4, 4.6 | M05, M06 | Abierta |
| [DT-06](#dt-06-clave-de-las-metas-de-mythicmobs-en-getrunninggoals) | Clave de las metas de MythicMobs en `getRunningGoals` | M01.4 | 2.2, 4.7 | M05, M12 | Abierta |
| [DT-07](#dt-07-significado-de-rangos-y-forma-de-limitar-la-huida) | Significado de rangos y forma de limitar la huida | M01.5 | 5.1–5.6, 5.8 | M03, M05 | Abierta |
| [DT-08](#dt-08-metas-de-paper-navegación-para-flank-y-tipos-de-owner) | Navegación de Paper 26.2 para `flank`, `hold` y tipos de `owner` | M01.6 | 6.1–6.5 | M05 | Abierta |
| [DT-09](#dt-09-hilo-de-evaluación-de-condiciones) | Hilo de evaluación de condiciones | M01.7 | 7.1–7.4 | M04 | Abierta |
| [DT-10](#dt-10-optionsasyncpathfinding-con-metas-condicionales) | `Options.AsyncPathfinding` con metas condicionales | M01.7 | 7.3 | M03, M05 | Abierta |
| [DT-11](#dt-11-comparación-de-onsignal-y-nombres-de-placeholders) | Comparación de `~onSignal` y nombres de placeholders | M01.8 | 2.3, 2.4, 3.4, 8.1–8.3 | M04, M10 | Abierta |
| [DT-12](#dt-12-trigger-thepredict) | Trigger `THEPREDICT` y `executeIfUsable` | M01.8 | 8.4–8.6 | M10 | Abierta |
| [DT-13](#dt-13-api-de-registro-de-placeholders) | API de registro de placeholders (obsoleta en esta build) | M01.3 | 3.4, 3.5 | M04 | Abierta |
| [DT-14](#dt-14-mecánica-como-implementación-directa-de-inotargetskill) | `MythicMechanicLoadEvent.register` con `INoTargetSkill` directo | M01.3 | 3.6 | M04 | Abierta |
| [DT-15](#dt-15-metas-de-paper-frente-a-clear-y-lectura-del-marcador) | `clear` frente a metas de Paper; lectura del marcador `owner` | M01.4, M01.6 | 4.10, 6.6 | M05 | Abierta |
| [DT-16](#dt-16-línea-de-meta-sin-prioridad) | Línea de `AIGoalSelectors` sin prioridad | M01.4 | 4.9 | M05 | Abierta |
| [DT-17](#dt-17-permiso-de-depuración-y-consola) | Permiso `thepredict.debug` y uso desde consola | M01.2 | 2.1, 2.5 | M12 | Abierta |
| [DT-18](#dt-18-costo-de-instalación) | Costo de instalación para 1 y 30 mobs | M01.4 | 4.8 | M03 | Abierta |
| [DT-19](#dt-19-especies-con-brain) | Exclusión de especies con `Brain` | M01.1, M01.4 | 4.11 | M03, M05 | Abierta |
| [DT-20](#dt-20-cobertura-de-eliminaciones-y-descargas) | Cobertura de eliminaciones y descargas (`EntityRemoveEvent`, `MythicMobDespawnEvent`) | M06.2 | 10.3–10.5 | M06 | Abierta |
| [DT-21](#dt-21-cambio-de-mundo) | Cambio de mundo: `EntityTeleportEvent` frente a `EntityPortalEvent` | M06.2 | 10.6 | M06 | Abierta |
| [DT-22](#dt-22-invisibilidad-por-efecto) | ¿`isInvisible()` refleja el efecto de invisibilidad? | M07.2 | 11.4 | M07 | Abierta |
| [DT-23](#dt-23-grupo-de-cooldown-de-la-perla) | ¿El cooldown de la perla se lee por su grupo `minecraft:ender_pearl`? | M07.3 | 11.5 | M07 | Abierta |
| [DT-24](#dt-24-condiciones-de-contexto-sobre-el-mob) | ¿`evaluateEntity` sirve para las condiciones de contexto del mob, como `healthpercent{p=<30%}`? | M07.7 | 11.8 | M07 | Abierta |
| [DT-25](#dt-25-resumen-de-combattracker) | Unidad de `getCombatDuration()` y clasificación por etiquetas de daño | M07.8 | 11.9 | M07, M11 | Abierta |
| [DT-26](#dt-26-golpe-vanilla-invulnerabilidad-y-alcance) | Golpe vanilla declarado: alcance de `attack`, aplazamiento por invulnerabilidad y ausencia de doble golpe | M10.3, M10.9 | 12.3, 12.4 | M10 | Abierta |
| [DT-27](#dt-27-amenaza-enriquecida-en-la-tabla-nativa) | Amenaza enriquecida: unidad de la tabla, suelo de `threatLoss`, golpe bloqueado y objetivo de la tabla | M11.1–M11.5 | 13.1–13.7 | M11 | Abierta |
| [DT-28](#dt-28-skill-de-una-táctica-renovada-por-jev) | Skill de una táctica renovada por Jev: rearme en cuanto la choice está lista, al ritmo del `Cooldown` de la skill, en `LOCAL` y con Jev (M13, revisado el 2026-09-30 por decisión del usuario); falta confirmar la igualdad de daño en servidor | M10.3, M13.5 | Guion 0.0.1 §7 | M10, M13 | Abierta |
| [DT-29](#dt-29-comandos-de-spark-incluidos-en-paper) | Comandos de spark incluidos en Paper 26.2 build 129 | M13.2 | Guion 0.0.1 §4.1 | M13 | Abierta |
| [DT-30](#dt-30-errores-reales-de-system-one-modelo-inexistente-y-clave-inválida) | Forma real de los errores de System One: modelo inexistente (¿422 o 404?) y clave inválida | M08.5 | Herramientas de M13 con la cuenta real | M08, M09 | Resuelta |
| [DT-31](#dt-31-calibración-con-la-cuenta-real) | Calibración con la cuenta real: caracteres por token, estimación de tokens, tolerancia de probabilidades, `minimumConfidence` (0,45) y `questionDetail` (`full`), todo resuelto; `tacticSwitchMargin` 0,15 se conserva; `timeoutMillis` pasa al guion §6.5 | M08, M09, M10, M13.6, M16.10 | `liveScenarioBench` y re-banco de M16.10 | M08, M09, M10, M13, M16 | Resuelta (2026-09-30, re-banco de M16.10) |
| [DT-32](#dt-32-dónde-se-declara-el-propósito-de-una-skill) | ¿Dónde se declara el propósito de una skill: en el perfil de ThePredict o en la skill de MythicMobs? | M15.2, M19 | Sesión 15 del paquete (15.1) y sesión 19 (19.1) | M15, M16, M18, M19 | Resuelta; ampliada por M19 |
| [DT-33](#dt-33-escudo-frente-a-daño-de-área-de-mythicmobs) | ¿Un escudo de frente bloquea el `damage` de una skill `AREA_AROUND_SELF` con el lanzador como causante? | M15.6 | Sesión 15 del paquete (15.5) | M15 | Abierta |
| [DT-34](#dt-34-cooldown-estático) | ¿`AbstractSkill.getCooldown().isStatic()` es falso para un `Cooldown` con placeholder y verdadero para un número? | M15.3 | Sesión 15 del paquete (15.1, 15.1b) | M15 | Abierta |
| [DT-35](#dt-35-atribución-del-daño-de-skills-de-mythicmobs) | ¿El `damage` de MythicMobs llega como `EntityDamageByEntityEvent` con el lanzador como causante, para atribuir el daño de sus skills a la ventana de resultado del mob? | M16.8 | Sesión 16 del paquete (16.1) | M16, M18 | Abierta |
| [DT-36](#dt-36-tendencia-de-una-entidad-por-su-velocidad) | ¿`getVelocity()` de un mob que camina con el navegador refleja su desplazamiento, para leer la tendencia de un participante no jugador? | M17.4 | Sesión 17 del paquete (17.2) | M17, M18 | Abierta |
| [DT-37](#dt-37-alcance-cuerpo-a-cuerpo-vanilla-de-un-mob) | Alcance cuerpo a cuerpo vanilla de un `Mob` en Paper 26.2 (caja de ataque inflada solo en horizontal, `ATTACK_RANGE` del arma), para `outsideTargetReach` de un objetivo no jugador | M17.4 | javap del servidor parcheado (hecho); sesión 17 (17.2) | M17, M18 | Abierta |
| [DT-38](#dt-38-escudo-de-un-no-jugador) | ¿El objeto activo con `BLOCKS_ATTACKS` y `getActiveItemUsedTime()` frente a `blockDelaySeconds()` bastan para saber que un mob bloquea? | M17.4 | Sesión 17 del paquete (17.5) | M17, M18 | Abierta |
| [DT-39](#dt-39-etiquetas-de-tipos-tras-recargar-datapacks) | ¿El `Tag` de `Bukkit.getTag` refleja una etiqueta cambiada por `/minecraft:reload`? | M17.2 | Sesión 17 del paquete (17.2) | M17 | Abierta |
| [DT-40](#dt-40-paquete-de-duelo-en-5131-y-paper-262) | Paquete de duelo: ¿se heredan y suman `Skills` y `AIGoalSelectors` de un `Template`, sigue funcionando `glow{color;duration}`, impide `spawn_mobs false` los refuerzos de zombi, cuántos `MythicSkillEvent` da cada regla clásica por cast real y da un golpe vanilla de 0 empuje o invulnerabilidad? (f, la vida en porcentaje, resuelta con javap) | M18.1, M18.2, M18.4 | Sesión 18 del paquete (18.2) | M18 | Abierta |
| [DT-42](#dt-42-opción-del-motor-golpes-vanilla-en-alcance-durante-approachhold-sin-consumir-la-choice) | Opción del motor: golpes vanilla en alcance durante `APPROACH`/`HOLD` sin consumir la choice, para que un jefe de ThePredict no pierda el daño pasivo del golpe vanilla que tiene el clásico | M05, M10, M18 | Decisión del usuario | M05, M10, M18 | Descartada |
| [DT-41](#dt-41-radio-de-los-targeters-de-mythicmobs) | ¿`@PlayersInRadius{r=…}` mide la distancia entre las posiciones (pies) del lanzador y del jugador con `<`, como dice javap, y coincide con `Radius` de ThePredict? | M15.6 | Sesión 15 del paquete (15.3) | M15 | Abierta |
| [DT-43](#dt-43-clave-intention-en-skills-de-mythicmobs) | ¿MythicMobs 5.13.1 conserva la clave `Intention` sin quejarse? | M19.1, M19.8 | Sesión 19 (19.6) | M19 | Abierta |
| [DT-44](#dt-44-cooldown-tras-mm-reload) | ¿Se reinicia el cooldown en curso de una skill recargada con `/mm reload`? | M19.2 | Sesión 19 (19.3) | M19 | Abierta |
| [DT-45](#dt-45-lectura-de-líneas-nativas) | ¿`SkillMechanic.getConfigLine()` y `SkillCondition.getLine()` devuelven la línea con sus llaves y su targeter? | M19.3 | Sesión 19 (19.1) | M19 | Abierta |
| [DT-46](#dt-46-nombres-de-tipo-de-mythicmobs) | ¿Qué texto exacto devuelve `getTypeName()` para `targetwithin`, `delay` y `effect:particles`? | M19.3 | Sesión 19 (19.1) | M19 | Abierta |
| [DT-47](#dt-47-objetivo-de-targetwithin-frente-al-objetivo-del-cast) | Con tabla de amenaza, ¿con qué frecuencia difieren el líder y `Mob.getTarget()`, y cuánto esperan las skills con `targetwithin`? | M19.3, M19.4 | Sesión 19 (19.7) | M19 | Abierta |
| [DT-48](#dt-48-tick-del-impacto-de-un-cast-con-delay) | Tick del impacto de un cast de ThePredict con `delay`, con `same_tick` y con `mythic_default` | M20.4, M19.4 | Sesión 20 (20.2) | M19, M20 | Abierta |
| [DT-49](#dt-49-navegación-asíncrona-y-coste-de-moveto) | Navegación asíncrona de MythicMobs y coste de `Pathfinder.moveTo` | M20.5 | Sesión 20 (20.1) | M20 | Abierta |
| [DT-50](#dt-50-retraso-de-la-meta-micro) | Retraso entre la vista publicada y el `start()` de `MicroGoal` | M20.5 | Sesión 20 (20.1) | M20 | Abierta |
| [DT-51](#dt-51-driver-sqlite-y-acceso-nativo-en-el-servidor) | Driver SQLite y acceso nativo en el servidor | M21.4, M21.12 | Sesión 21 (21.1) | M21 | Abierta, severidad baja |
| [DT-52](#dt-52-volumen-y-coste-reales-de-los-registros) | Volumen y coste reales de los registros | M21.0, M21.3 | Sesión 21 (21.2) | M21 | Abierta |
| [DT-53](#dt-53-repetición-de-cuerpos-y-valor-de-la-caché-de-respuestas) | Repetición de cuerpos y valor de la caché de respuestas | M21.7 | Sesión 21 (21.5) | M21 | Abierta |
| [DT-54](#dt-54-simulador-frente-al-servidor) | Simulador frente al servidor | M22.8 | Sesión 22 (22.5) | M22 | Abierta |
| [DT-55](#dt-55-justicia-percibida) | Justicia percibida | M22.7 | Sesión 22 (22.1, 22.2) | M22 | Abierta |
| [DT-56](#dt-56-variable-en-vivo) | Variable en vivo | M22.9 | Sesión 22 (22.3) | M22 | Abierta |
| [DT-57](#dt-57-daño-de-difícil-frente-a-normal) | Daño de Difícil frente a Normal | M22.13 | Sesión 22 (22.4) | M22 | Abierta |
| [DT-58](#dt-58-calibración-con-jev-real) | Calibración con Jev real | M22.6 | Sesión 22 (22.6) | M22 | Abierta |
| [DT-59](#dt-59-interrupción-nativa-de-un-cast-de-thepredict) | Interrupción nativa de un cast de ThePredict | M23.7 | Sesión 23 (23.3) | M23 | Abierta |
| [DT-60](#dt-60-postura-nativa-variable-y-vocabulario-en-vivo) | Postura nativa, variable y vocabulario en vivo | M23.8 | Sesión 23 (23.1) | M23 | Abierta, severidad baja |
| [DT-61](#dt-61-legibilidad-de-las-señales-con-los-manejadores-del-ejemplo) | Legibilidad de las señales con los manejadores del ejemplo | M23.5 | Sesión 23 (23.4, 23.7) | M23 | Abierta |
| [DT-62](#dt-62-mínimos-de-aviso-y-justicia-percibida-con-jugadores-reales) | Mínimos de aviso y justicia percibida con jugadores reales | M23.4 | Sesión 23 (23.5) | M23 | Abierta |
| [DT-63](#dt-63-gestos-nativos-por-especie) | Gestos nativos por especie | M23.5 | — | M23 | Retirada |
| [DT-64](#dt-64-coste-real-de-la-zona-en-el-servidor) | Coste real de la zona en el servidor | M24.0 | Sesión 24 (24.1 y 24.7) | M24 | Abierta |
| [DT-65](#dt-65-cambios-de-bloque-sin-evento) | Cambios de bloque sin evento | M24.3 | Sesión 24 (24.2) | M24 | Abierta |
| [DT-66](#dt-66-navegación-nativa-junto-a-peligros) | Navegación nativa junto a peligros | M24.5 | Sesión 24 (24.3) | M24 | Abierta |
| [DT-67](#dt-67-distancia-real-de-empuje) | Distancia real de empuje y atribución | M24.6 | Sesión 24 (24.4) | M24 | Abierta |
| [DT-68](#dt-68-vocabulario-de-zona-en-vivo) | Vocabulario de zona en vivo | M24.7 | Sesión 24 (24.6) | M24 | Abierta |
| [DT-69](#dt-69-coste-del-paso-de-grupo) | Coste del paso de grupo | M25.2 | Sesión 25 (25.1, 25.7) | M25 | Abierta |
| [DT-70](#dt-70-escuadra-líder-y-alerta-nativa) | Escuadra, líder y alerta nativa | M25.1 | Sesión 25 (25.2) | M25 | Abierta |
| [DT-71](#dt-71-justicia-de-grupo-percibida) | Justicia de grupo percibida | M25.3 | Sesión 25 (25.3, 25.4) | M25 | Abierta |
| [DT-72](#dt-72-huecos-de-cerco) | Huecos de cerco y sector de salida | M25.5 | Sesión 25 (25.5) | M25 | Abierta |
| [DT-73](#dt-73-vocabulario-de-grupo-en-vivo) | Vocabulario de grupo en vivo | M25.10 | Sesión 25 (25.6, 25.8) | M25 | Abierta |
| [DT-74](#dt-74-coste-real-de-la-observación-de-patrones) | Coste real de la observación de patrones | M26.0 | Sesión 26 (26.6) | M26 | Abierta |
| [DT-75](#dt-75-clasificación-de-golpes-y-del-rodeo-en-vivo) | Clasificación de golpes y del rodeo en vivo | M26.1 | Sesión 26 (26.1) | M26 | Abierta |
| [DT-76](#dt-76-detección-con-jugadores-reales) | Detección con jugadores reales | M26.3 | Sesión 26 (26.3, 26.4) | M26 | Abierta |
| [DT-77](#dt-77-justicia-percibida-de-la-explotación) | Justicia percibida de la explotación | M26.6 | Sesión 26 (26.5) | M26 | Abierta |
| [DT-78](#dt-78-tokens-reales-de-los-hechos-de-patrón) | Tokens reales de los hechos de patrón | M26.8 | Sesión 26 (26.7) | M26 | Abierta |
| [DT-79](#dt-79-coste-tamaño-y-latencia-reales-de-la-memoria) | Coste, tamaño y latencia reales de la memoria | M27 | Sesión 27 (27.1, 27.2) | M27 | Abierta |
| [DT-80](#dt-80-valor-de-la-memoria-con-jugadores-reales) | Valor de la memoria con jugadores reales | M27 | Sesión 27 (27.3, 27.4) | M27 | Abierta |
| [DT-81](#dt-81-justicia-percibida-de-que-el-jefe-recuerda) | Justicia percibida de que el jefe recuerda | M27 | Sesión 27 (27.3, 27.5) | M27 | Abierta |
| [DT-82](#dt-82-red-con-mysql-o-mariadb) | Red con MySQL o MariaDB | M27.10 | No se prepara | M27 | Abierta |
| [DT-83](#dt-83-patente-y-nombres) | Patente y nombres | M27 | Consulta legal pendiente | M27 | Decidida en el diseño |
| [DT-84](#dt-84-tokens-reales-de-los-hechos-de-historia) | Tokens reales de los hechos de historia | M27 | Sesión 27 (27.8) | M27 | Abierta |
| [DT-85](#dt-85-la-confianza-de-jev-no-es-una-propensión) | La confianza de Jev no es una propensión | M28.0 | Sesión 28 (28.1) | M28 | Abierta |
| [DT-86](#dt-86-recompensa-r1-y-crédito-diferido) | Recompensa r1 y crédito diferido | M28.2 | Sesión 28 (28.1) | M28 | Abierta |
| [DT-87](#dt-87-exploración-solo-sin-jugadores) | Exploración solo sin jugadores, salvo PredictorTraining | M28.5 | Sesión 28 (28.2) | M28 | Abierta |
| [DT-88](#dt-88-método-y-datos-de-la-calibración) | Método y datos de la calibración | M28.4 | Sesión 28 (28.3) | M28 | Abierta |
| [DT-89](#dt-89-veredicto-mínimo-para-activar-un-valor-aprendido) | Veredicto mínimo para activar un valor aprendido | M28.9 | Sesión 28 (28.2, 28.5) | M28 | Abierta |
| [DT-90](#dt-90-valor-aprendido-como-pista-para-jev) | Valor aprendido como pista para Jev | M28.7 | Sesión 28 | M28 | Abierta |
| [DT-91](#dt-91-omisión-de-jev-por-valor-de-la-información) | Omisión de Jev por valor de la información | M28.8 | Sesión 28 (28.4) | M28 | Abierta |
| [DT-92](#dt-92-señal-de-entrenamiento-por-resultado-sin-imitar-a-jev) | Señal de entrenamiento por resultado, sin imitar a Jev | M29.2 | Sesión 29 (29.2) | M29 | Decidida la señal; abiertas las cifras |
| [DT-93](#dt-93-representación-híbrida) | Representación híbrida | M29.1 | Sesión 29 (29.2) | M29 | Abierta |
| [DT-94](#dt-94-cobertura-por-celda-fina) | Cobertura por celda fina y no inferioridad en resultado | M29.3 | Sesión 29 (29.2, 29.3) | M29 | Abierta |
| [DT-95](#dt-95-regímenes-por-enemigos-y-fase) | Regímenes por enemigos y fase, con histéresis | M29.5 | Sesión 29 (29.1, 29.4) | M29 | Abierta |
| [DT-96](#dt-96-degradación-automática) | Degradación automática de un solo sentido | M29.7 | Sesión 29 (29.5) | M29 | Abierta |
| [DT-97](#dt-97-autoconfiguración-sin-aplicación-automática) | Autoconfiguración por reducción sucesiva, sin aplicación automática | M29.8 | Sesión 29 (29.6) | M29 | Abierta |
| [DT-98](#dt-98-cinta-del-campeón) | No inferioridad frente a Jev con una cinta del campeón | M29.6 | Sin paso hasta que el usuario grabe la cinta | M29 | Abierta |
| [DT-99](#dt-99-kev-como-proveedor-experimental) | Kev como proveedor experimental | M29.9 | Sesión 29 (29.7) | M29 | Abierta |
| [DT-100](#dt-100-mythicmobs-ignora-la-carpeta-aiconfig) | MythicMobs ignora la carpeta `AIConfig` al cargar y con `/mm reload` | Consolidado 2.7 | Sesión AIConfig (AIConfig.2) | P-AIConfig | Abierta |

Las filas DT-01 a DT-11 son las de la tabla de M01.9. DT-12 a DT-19 salen de las aceptaciones de M01.2, M01.4 y M01.8 y de las preguntas que M04 y M05 delegan en M01 ("M01 confirma"). DT-20 y DT-21 son las dos preguntas que M06.2 delega en M01. DT-22 a DT-25 son las que M07 delega ("M01 confirma"). DT-27 reúne las que M11 delega. DT-32 a DT-34 son las de M15 ("Cambios propuestos a 00/01"); DT-41 la añadió la corrección de la auditoría de M15. DT-35 es la de M16 ("Cambios propuestos a 00/01"). DT-36 a DT-39 son las de M17 ("Cambios propuestos a 00/01"); DT-40 es la de M18 ("Cambios propuestos a 00/01") y DT-42 la abrió la auditoría de M18 (descartada por el usuario el 2026-09-30). DT-43 a DT-47 son las de M19 y siguen abiertas hasta la sesión 19. DT-32 queda resuelta y ampliada por M19. DT-48, DT-49 y DT-50 son las de M20 y siguen abiertas hasta la sesión 20. La sesión 19.9 solo cita DT-48. DT-51, DT-52 y DT-53 son las de M21 y siguen abiertas hasta la sesión 21. La sesión 21.5 es de pago y no se ejecuta sin aprobación. DT-100 es la de P-AIConfig y sigue abierta hasta la sesión AIConfig. DT-92 a DT-99 son las de M29 y siguen abiertas hasta la sesión 29. La señal de DT-92 está decidida; las cifras no. DT-98 sigue abierta hasta que el usuario grabe la cinta. La sesión 29.2 es de pago y no se ejecuta sin aprobación. La 29.7 no levanta Kev. DT-54 a DT-58 son las de M22 y siguen abiertas hasta la sesión 22. La sesión 22.6 es de pago y no se ejecuta sin aprobación. DT-59 a DT-62 son las de M23 y siguen abiertas hasta la sesión 23. DT-63 está retirada: ThePredict no usa gestos. DT-74 a DT-78 son las de M26 y siguen abiertas hasta la sesión 26. La sesión 26.7 es de pago y no se ejecuta sin aprobación.

## Entradas

### DT-00. Entorno de verificación

- **Pregunta:** ¿Con qué binarios exactos se obtuvo la evidencia?
- **Contexto:** MythicMobs es una SNAPSHOT; una actualización puede cambiar comportamientos. Todas las entradas dependen de esta.
- **Evidencia estática:** SHA-256 de `VersionsOfJars` en la máquina de desarrollo (2026-09-29): `paper-26.2-129.jar` `b1d8f6bfa1b6101fa8e947b53041cb3bdf5540e7b83b6547ca19ba7edefeb083`; `MythicMobsPremium-5.13.1-SNAPSHOT.jar` `0e9ffc1362d7831e3cb4875084d017939ac96c2071721b4b22cc70370ac0b733`.
- **Evidencia en servidor:** Pendiente de servidor. Pegar `versions.md` (`/version`, `/version MythicMobs`, `java -version`, huellas), `first-start.log` (descarga y verificación de Paperclip), constancia de que el administrador aceptó el EULA, y los dos arranques limpios con `/thepredict status`.
- **Decisión:** Pendiente.
- **Consecuencia:** Una huella distinta invalida las demás entradas hasta repetirlas.
- **Alternativa si no se confirma:** No aplica; sin entorno no hay evidencia.
- **Módulos afectados:** todos.
- **Estado:** Abierta.

### DT-01. Condiciones del mob en cada meta condicional

- **Pregunta:** ¿Gobiernan de verdad `mobconditions` las metas `fleeConditional`, `moveWithinConditional`, `moveTowardsTargetConditional` y `bowAttackConditional`? ¿`lookAtTarget` corre en paralelo con metas de solo `MOVE`?
- **Contexto:** M05 genera el conjunto de metas del plan §3 sobre esta base.
- **Evidencia estática:** las clases `v26_2` aceptan `mobconditions` (alias `mc`, `mcond`, `mconditions`) y `targetconditions` (`c`, `cond`, `conditions`); `fleeConditional` añade `fleeconditions`. Nombres: `fleeConditional` (alias `fleeIf`), `moveWithinDistanceOfTargetConditional` (alias `moveWithinConditional`), `moveTowardsTargetConditional`, `bowAttackConditional`, `lookAtTarget`.
- **Evidencia estática adicional (M05):** `v26_2.VolatileAIHandlerImpl.addPathfinderGoals` divide cada línea con `split(" ")` y lee la meta de la segunda parte, así que los espacios dentro de `mc=[ - … ]` la cortarían; `MobType` aplica `MythicLineConfigImpl.unparseBlock` a cada línea de `AIGoalSelectors` al leerla (espacios a `<&csp>` y guiones a `<&da>` dentro de `{…}`). M05 aplica el mismo escape a sus líneas y a los presets de M01 (`GoalSetInstaller.selectorLines`).
- **Evidencia en servidor:** Pendiente de servidor (pasos 5.1, 5.7, 5.9 y 9.1).
- **Decisión:** Pendiente.
- **Consecuencia:** Si una meta no respeta la condición, solo esa capacidad pasa a la ruta 2 o 3 (plan §5).
- **Alternativa si no se confirma:** puente mínimo de Paper para la capacidad que falle (plan §13).
- **Módulos afectados:** M05.
- **Estado:** Abierta.

### DT-02. Registro de extensiones en primer arranque y recarga

- **Pregunta:** ¿La resolución diferida de un tick basta para que `predictormove` y `predictorprobe` estén disponibles en el primer arranque y tras `/mm reload`? ¿Importan las mayúsculas del nombre?
- **Contexto:** M04 registra todo el vocabulario igual.
- **Evidencia estática:** `CustomCondition` resuelve el componente con `Schedulers.sync().runLater(..., 1)`. Los nombres de condiciones se comparan en mayúsculas (plan §2). `VocabularyRegistrar` (M04, sustituye a `ProvisionalVocabularyListener`) compara sin distinguir mayúsculas y conserva las líneas `MythicMobs resolved …` del guion.
- **Evidencia en servidor:** Pendiente de servidor (pasos 1.5, 3.1–3.3; líneas `MythicMobs resolved …` y mensajes `[TPV] … passed`).
- **Decisión:** Pendiente.
- **Consecuencia:** Si falla en el primer arranque, el registro se mueve o se fuerza una recarga documentada.
- **Alternativa si no se confirma:** cambiar el orden de registro y reenlace; no exigir un reinicio a ciegas (plan §13).
- **Módulos afectados:** M04.
- **Estado:** Abierta.

### DT-03. Registro por eventos de carga

- **Pregunta:** ¿`MythicConditionLoadEvent`, `MythicMechanicLoadEvent` y `MythicPlaceholdersLoadEvent` cubren primer arranque y recarga sin `CustomComponentRegistry`?
- **Contexto:** Regla C15; M04 sustituye el listener provisional por `VocabularyRegistrar`.
- **Evidencia estática:** los tres eventos tienen `register(...)` o `getManager()` públicos (javap). `MythicPlaceholdersLoadEvent` se emite dentro de la carga de MythicMobs, antes de habilitar ThePredict, por lo que la primera vez los placeholders se registran directamente en `onEnable`.
- **Evidencia en servidor:** Pendiente de servidor (pasos 1.5, 3.1, 3.3, 3.4).
- **Decisión:** Pendiente.
- **Consecuencia:** Confirma C15 o documenta el caso que no cubren.
- **Alternativa si no se confirma:** registrar además en `MythicReloadedEvent`, sin estado estático.
- **Módulos afectados:** M04.
- **Estado:** Abierta.

### DT-04. Restauración con `reapplyAIGoalSelectors`

- **Pregunta:** ¿Qué metas deja `MythicMob#reapplyAIGoalSelectors(activeMob)` en un mob con `AIGoalSelectors` y en uno sin ellos?
- **Contexto:** Desvinculación en M05 y cadena de fallback en M10.
- **Evidencia estática:** el método existe en la API (`MythicMob`, javap).
- **Evidencia estática adicional (M05):** `MobType.reapplyAIGoalSelectors` retorna sin hacer nada si las líneas del mob no contienen `clear` (campo `aiGoalSelectorsClear`); con `clear` llama a `addPathfinderGoals` con las líneas del mob.
- **Evidencia en servidor:** Pendiente de servidor (pasos 4.5 y 9.6; salida de `debug goals` antes y después). Desde M12 la restauración se provoca desvinculando el mob (`Predictor: false` y `/mm reload`), que llama a `reapplyAIGoalSelectors` a través de `MobControl.release`; `debug reapply` se retiró.
- **Decisión:** Pendiente.
- **Consecuencia:** Define el estado final de un mob desvinculado.
- **Alternativa si no se confirma:** conservar la IA base del conjunto generado como estado final (plan §13).
- **Módulos afectados:** M05, M10.
- **Estado:** Abierta.

### DT-05. Instalación y reinstalación de metas

- **Pregunta:** ¿`addPathfinderGoals` con `0 clear` deja solo las metas de ThePredict, un tick después de `MythicMobSpawnEvent` y de `EntitiesLoadEvent`? ¿MythicMobs reinstala sus metas al cargar un chunk o con `/mm reload`? ¿`clear` respeta el selector de objetivos?
- **Contexto:** Ciclo de vida de M05 y sesiones de M06.
- **Evidencia estática:** `MobType.applyMobVolatileOptions` instala las metas en `registerActiveMob` y al cargar desde chunk (plan §2).
- **Evidencia estática adicional (M06):** `MythicMobSpawnEvent` solo se construye en `MobType` y, como los otros tres eventos de ciclo de vida de MythicMobs, llama a `Event()`, así que es síncrono (javap). M06 abre la sesión con `installPending` un tick después de `MythicMobSpawnEvent` y de `EntitiesLoadEvent`; la llamada a `ensureInstalled` llega con el coordinador de M09.
- **Evidencia en servidor:** Pendiente de servidor (pasos 4.1–4.4, 4.6 y 10.2). Desde M12 la instalación la hace la sesión sin comando (M09) y se observa con `debug goals` e `inspect` (`Goals: installed for revision <n>`); las líneas `autoinstall …` se retiraron.
- **Decisión:** Pendiente.
- **Consecuencia:** Fija los momentos de instalación y la autocomprobación con `owner`.
- **Alternativa si no se confirma:** reinstalar en la siguiente decisión y documentar la skill o evento que interfiere (plan §13).
- **Módulos afectados:** M05, M06.
- **Estado:** Abierta.

### DT-06. Clave de las metas de MythicMobs en `getRunningGoals`

- **Pregunta:** ¿Qué `NamespacedKey` asigna Paper a las metas NMS de MythicMobs?
- **Contexto:** Observación de progreso (M05) y diagnóstico (M12).
- **Evidencia estática:** `MobGoalHelper` deriva la clave de la clase de la meta (plan §2).
- **Evidencia en servidor:** Pendiente de servidor (pasos 2.2 y 4.7; copiar las claves literalmente de `debug goals`, que desde M12 lee `MobControl.describeGoals`).
- **Decisión:** Pendiente.
- **Consecuencia:** Permite reconocer una meta gobernada concreta.
- **Alternativa si no se confirma:** comparar por `GoalType.MOVE` en curso en lugar de por clave (plan §13).
- **Módulos afectados:** M05, M12.
- **Estado:** Abierta.

### DT-07. Significado de rangos y forma de limitar la huida

- **Pregunta:** Significado real de `distance`, `speed`, `safespeed` (`fleeConditional`); `minrange`, `range`, `maxrange` (`moveWithinConditional`); `minrange`, `maxrange` (`moveTowardsTargetConditional`); `maxrange`, `minrange`, `speed`, `droptarget` (`goToSpawnLocation`). ¿Qué expresión limita la huida al objetivo? ¿Qué banda no oscila?
- **Contexto:** M03 valida perfiles con estos parámetros; M05 genera las líneas.
- **Evidencia estática:** cadenas de clave presentes en las clases `v26_2` (javap; la correspondencia entre alias no se deduce de ahí): `fleeConditional` `distance`, `d`, `speed`, `s`, `safespeed`, `ss`; `moveWithinDistanceOfTarget` (base de `moveWithinConditional`) `minrange`, `range`, `r`, `maxrange`, `maxr`, `distance`, `d`, `speed`, `s`; `moveTowardsTargetConditional` `minrange`, `minr`, `maxrange`, `mr`, `range`, `r`, `speed`, `s`; `goToSpawnLocation` `maxrange`, `max`, `minrange`, `min`, `mr`, `r`, `speed`, `s`, `droptarget`, `dt`. La condición `target` tiene alias `isTarget` y la condición `isplayer` existe.
- **Evidencia en servidor:** Pendiente de servidor (pasos 5.1–5.6 y 5.8; muestras de distancia y cruces de frontera).
- **Decisión:** Pendiente.
- **Consecuencia:** Valores iniciales de `MovementSettings` y reglas del binder.
- **Alternativa si no se confirma:** usar `targetconditions=[ - isplayer true ]` para la huida; si la banda oscila, ampliar la distancia entre `fleeConditional{distance}` y `moveWithinConditional{range}`.
- **Módulos afectados:** M03, M05.
- **Estado:** Abierta.

### DT-08. Metas de Paper: navegación para `flank` y tipos de `owner`

- **Pregunta:** ¿`hold` (solo `MOVE`) mantiene al mob quieto mirando al objetivo y bloquea la IA base? ¿La cesión tarda ≤2 ticks? ¿`Pathfinder.findPath` y `moveTo` llevan al lateral en `flank`? ¿Acepta Paper un `owner` sin tipos?
- **Contexto:** M05 completa `HoldGoal`, `FlankGoal` y `OwnerMarkerGoal` a partir de los prototipos de M01.
- **Evidencia estática:** `PaperCustomGoal` se elige cada 2 ticks; `doNothing` declara `MOVE_LOOK`; `aware = false` detiene navegación y metas (plan §2).
- **Evidencia estática adicional (M05):** en el servidor Paper 26.2 parcheado localmente (sin arrancar), `PaperCustomGoal` añade `UNKNOWN_BEHAVIOR` a una meta que no declara tipos, así que un marcador sin tipos es aceptado y visible para `hasGoal`/`getGoal`. M05 usa `LOOK` para el marcador del conjunto generado, como su diseño.
- **Evidencia en servidor:** Pendiente de servidor (pasos 6.1–6.4 y 9.2–9.5). Desde M12 las líneas `Release +N tick: running MOVE goals …` no existen: el paso 6.3 solo observa qué meta queda `running` tras `debug release` (el guion 0.0.1, §6.4, lo repite en la primera lectura posible); el tiempo exacto de cesión (≤2 ticks) solo tiene evidencia local, porque ningún comando lee metas tick a tick y M13 no crea métricas. El paso 6.5 (marcador sin tipos) se retiró con los presets: el marcador declara siempre `LOOK`.
- **Decisión:** Pendiente.
- **Consecuencia:** Tipos definitivos de `owner` y validación de la ruta de Paper para `flank`.
- **Alternativa si no se confirma:** `owner` con `UNKNOWN_BEHAVIOR` o `LOOK` (presets `paper_unknown`, `paper_look`); si la navegación falla, usar una API de Paper comprobada o declarar la especie no soportada (plan §13).
- **Módulos afectados:** M05.
- **Estado:** Abierta.

### DT-09. Hilo de evaluación de condiciones

- **Pregunta:** ¿En qué hilo evalúa MythicMobs las condiciones de cada contexto (`mobconditions`, `targetconditions`, `Conditions` de skill con `~onTimer`) y las mecánicas?
- **Contexto:** Regla T3; M04 escribe condiciones seguras entre hilos.
- **Evidencia estática:** las condiciones no declaran `ThreadSafetyLevel`; `fleeConditional` usa `AsyncAvoidEntityGoal` y `AsyncEntityScan` (plan §2).
- **Evidencia en servidor:** Pendiente de servidor (sección 7). Desde M12 el hilo se observa con el perfilador spark incluido en Paper (`/spark profiler --thread *`); la traza `debug trace` y la sonda `predictorprobe` se retiraron.
- **Decisión:** Pendiente.
- **Consecuencia:** Solo ajusta la documentación: las condiciones ya son seguras en cualquier hilo.
- **Alternativa si no se confirma:** ninguna necesaria (plan §13).
- **Módulos afectados:** M04.
- **Estado:** Abierta.

### DT-10. `Options.AsyncPathfinding` con metas condicionales

- **Pregunta:** ¿Funcionan las metas condicionales y `goToSpawnLocation` con `Options.AsyncPathfinding: true`?
- **Contexto:** Opción de los mobs vinculados (M03) y generación de metas (M05).
- **Evidencia estática:** `MobType` lee `Options.AsyncPathfinding`; `VolatileAIHandler` tiene `enableAsyncPathfinding` (plan §2, javap).
- **Evidencia en servidor:** Pendiente de servidor (sección 7, punto 3, con `TPV_ZombieAsync` y spark).
- **Decisión:** Pendiente.
- **Consecuencia:** Se permite o se desactiva la opción en mobs vinculados.
- **Alternativa si no se confirma:** navegación síncrona para mobs vinculados (plan §13).
- **Módulos afectados:** M03, M05.
- **Estado:** Abierta.

### DT-11. Comparación de `~onSignal` y nombres de placeholders

- **Pregunta:** ¿`~onSignal` distingue mayúsculas? ¿Registrar `caster.predictor.tactic` produce `<caster.predictor.tactic>`, y lo mismo para `movement`, `level`, `confidence` y `state`, con y sin sesión?
- **Contexto:** Formato de señales `PREDICTOR_<TACTIC>` (M04, M10) y placeholders (M04).
- **Evidencia estática:** `PlaceholderExecutor.register(String, Placeholder)` divide el nombre por puntos en un árbol y, si el gestor está inicializado, vacía la caché y llama a `recheckForPlaceholders()` (javap). Señales (M04): `MobType.signalSkills` es un `ConcurrentMap` (`Maps.newConcurrentMap`) cuya clave es el texto literal capturado por `~onSignal:([a-zA-Z0-9_-]*)` (`computeIfAbsent(group(1))`), y `executeSignalSkill` lo consulta con `Map.get` exacto, sin cambiar mayúsculas: estáticamente, `~onSignal` distingue mayúsculas.
- **Evidencia estática (M10):** `CombatActionsAdapter.signalTactic` envía `TacticSignals.nameOf(táctica)` (`PREDICTOR_` y la táctica en mayúsculas) con `ActiveMob.signalMob(activeMob.getEntity(), nombre)`, como `debug signal`; `engine` la emite una sola vez por táctica nueva (`IntentPublicationTest`).
- **Evidencia en servidor:** Pendiente de servidor (pasos 2.3, 2.4, 3.4, 8.1, 8.3, 12.2). Desde M12 la señal `PREDICTOR_HOLD` la envía ThePredict al cambiar de táctica con `debug intent`; el paso 8.2 (`debug signal` en minúsculas) se retiró y 8.1 compara las dos grafías de `~onSignal`.
- **Decisión:** Pendiente.
- **Consecuencia:** Nombres definitivos de señales y placeholders antes de documentarlos a autores (W3).
- **Alternativa si no se confirma:** ajustar formato y nombres antes de documentarlos (plan §13); si `<caster.…>` no resuelve, probar `Placeholder.entity` con el nombre sin prefijo.
- **Módulos afectados:** M04, M10.
- **Estado:** Abierta.

### DT-12. Trigger `THEPREDICT`

- **Pregunta:** ¿`executeIfUsable` con metadatos del trigger `THEPREDICT` respeta condiciones, targeters y cooldown de la skill? ¿Se conserva el nombre?
- **Contexto:** Ejecución táctica en M10.
- **Evidencia estática:** `SkillTrigger.register` usa `putIfAbsent` sobre una clave de record, así que registrar el mismo nombre desde `TriggeredSkillCast` y desde el `MythicSkillExecutor` actual es inocuo (javap).
- **Evidencia estática (M10, corregida tras la auditoría):** `MetaSkill.executeIfUsable(meta)` sobrescribe el método por defecto de `Skill`: llama a `isUsable(meta)` y después al privado `executeInternal(meta)`, que lanza el `MythicSkillEvent` cancelable, toma el bloqueo de cooldown (`tryAcquireCooldownLock`) y consume una carga (`consumeCharge`), y devuelve `false` si cualquiera lo impide. `MetaSkill.isUsable` tira `rollChance`, comprueba `SkillCaster.isSkillOnCooldown` y evalúa las condiciones de lanzador (`getConditions`, `SkillCondition.evaluateCaster`), disparador (`getConditionsTrigger`, `evaluateTrigger`) y objetivo (`getConditionsTarget`, `evaluateTargets`), ejecutando sus skills `onFail`/`onPass`. `SkillCondition.handleOutcome` ejecuta otra skill con las acciones `CAST`, `CASTINSTEAD` y `ORELSECAST`, multiplica la potencia con `POWER` y con `REQUIRED` y `CANCEL` solo envía un mensaje si el lanzador es un jugador. Por eso `CombatActionsAdapter` lee `Skill.onCooldown(ActiveMob)`, comprueba alcance, invulnerabilidad y escudo, evalúa sin efectos las condiciones `REQUIRED` y `CANCEL` de las tres listas con metadatos propios (`SkillConditions`; si alguna falla, `NOT_ELIGIBLE` y se reintenta en otro ciclo reactivo) y solo entonces llama una vez a `executeIfUsable` con el trigger `THEPREDICT`. Un `Chance` fallido, un `MythicSkillEvent` cancelado o sin carga dan `REJECTED_BY_MYTHICMOBS` (final).
- **Evidencia en servidor:** Pendiente de servidor (pasos 8.4–8.6, 12.3). Desde M12 `TriggeredSkillCast` y `debug cast` se retiraron: los pasos 8.4–8.6 lanzan `TPV_Strike` por el camino real de M10 con `debug intent` y `decision.logDecisions`.
- **Decisión:** Pendiente.
- **Consecuencia:** M10 conserva el trigger `THEPREDICT`. Unas condiciones `REQUIRED`/`CANCEL` que fallan hacen esperar la skill dentro de la misma intención; las condiciones con otras acciones y el `Chance` se resuelven una sola vez en `executeIfUsable`.
- **Alternativa si no se confirma:** revalidar condiciones y cooldown con `isUsable` y `onCooldown` antes de `execute`.
- **Módulos afectados:** M10.
- **Estado:** Abierta.

### DT-13. API de registro de placeholders

- **Pregunta:** ¿Qué API usa ThePredict para registrar placeholders con dependencias inyectadas?
- **Contexto:** M04.4 propone `Placeholder.meta(...)` o `Placeholder.entity(...)`.
- **Evidencia estática:** en 5.13.1-SNAPSHOT, `Placeholder.meta`, `Placeholder.entity` y el resto de fábricas están marcadas `@Deprecated(forRemoval = true)`; `meta` llama a `ClassInfo.warnClassIsUsingDeprecatedAPI` con el mensaje "You should create a separate class for each placeholder, and register them via the CustomComponentRegistry". `MetaPlaceholder` también está obsoleta. `CustomComponentRegistry` crea las clases por reflexión y obligaría a estado estático (D8).
- **Evidencia en servidor:** Pendiente de servidor (pasos 3.4 y 3.5; copiar el aviso literal si aparece).
- **Evidencia estática adicional (M04):** la vía no obsoleta, `PlaceholderManager.register(Class)` (la que usa `CustomComponentRegistry`), comprueba `GenericPlaceholder.isAssignableFrom` y crea la instancia por reflexión con `getConstructor(GenericPlaceholderArguments)` (o `EntityScopedPlaceholderArguments`): no hay forma de pasar `IntentBoard` sin estado estático (javap de `PlaceholderExecutor`). La sobrecarga `register(String, Class, GenericPlaceholderArguments, Constructor)` exige además un `Constructor` obtenido por reflexión, que C11 también descarta.
- **Decisión:** Provisional en M01 y mantenida en M04: `Placeholder.meta` con `@SuppressWarnings("removal")` documentado en el Javadoc de `PredictorPlaceholders.registerWith`.
- **Evaluación de M12 (2026-09-29):** no se puede cerrar sin servidor, porque una entrada solo cambia de estado con evidencia observada. La evidencia estática no cambió: la única vía sin API obsoleta sigue exigiendo reflexión (C11) o estado estático (D8). M12 mantiene `Placeholder.meta` y deja la entrada `Abierta`; los pasos 3.4 y 3.5 siguen vigentes sin cambios de fondo.
- **Consecuencia:** Una versión futura de MythicMobs puede eliminar la API; M04 debe decidir con la evidencia.
- **Alternativa si no se confirma:** clases `GenericPlaceholder` registradas con `register(String, Class, …)` sin estado estático, si MythicMobs lo permite; si no, mantener `meta` y fijar la versión mínima de MythicMobs.
- **Módulos afectados:** M04.
- **Estado:** Abierta.

### DT-14. Mecánica como implementación directa de `INoTargetSkill`

- **Pregunta:** ¿Acepta `MythicMechanicLoadEvent.register(ISkillMechanic)` una clase que implementa `INoTargetSkill` sin extender la clase base de mecánicas?
- **Contexto:** Riesgo abierto de M04 para `predictordecide`.
- **Evidencia estática:** `register` recibe `ISkillMechanic`; `INoTargetSkill` la extiende y `getThreadSafetyLevel()` tiene implementación por defecto (javap).
- **Evidencia en servidor:** Pendiente de servidor (paso 3.6). Desde M12 el paso usa `predictordecide`, registrada igual (`INoTargetSkill` directo); la sonda `PredictorProbeMechanic` se retiró.
- **Decisión:** Pendiente.
- **Consecuencia:** M04 implementa `PredictorDecideMechanic` igual o extiende la clase base. M04 lo implementa ya como `INoTargetSkill` directo con `ThreadSafetyLevel.EITHER`; si el paso 3.6 falla, se aplica la alternativa.
- **Alternativa si no se confirma:** extender `SkillMechanic` de MythicMobs con dependencias por constructor.
- **Módulos afectados:** M04.
- **Estado:** Abierta.

### DT-15. Metas de Paper frente a `clear` y lectura del marcador

- **Pregunta:** ¿`0 clear` de `addPathfinderGoals` elimina también las metas de Paper instaladas antes? ¿`MobGoals#getGoal(mob, owner)` devuelve la instancia de ThePredict?
- **Contexto:** M05.2 decide si retira sus metas antes de reinstalar y si lee la revisión del marcador.
- **Evidencia estática:** ninguna concluyente.
- **Evidencia estática adicional (M05):** `clear` llama a `clearGoalSelector`, que vacía `lockedFlags` y `getAvailableGoals()` del selector de metas; `PaperMobGoals.addGoal` guarda las metas de Paper que no declaran `TARGET` en ese mismo conjunto, envueltas en `PaperCustomGoal`; `getGoal` recorre ese conjunto y devuelve `PaperCustomGoal.getHandle()`, la instancia de ThePredict (javap de MythicMobs y del servidor Paper parcheado localmente, sin arrancar). M05 aplica igualmente la alternativa (quitar las tres claves antes de instalar) y lee el marcador con `getGoal`; una clave `owner` con otra clase se reinstala.
- **Evidencia estática adicional (auditoría de M05):** `addPathfinderGoals` captura `Exception` en todo su cuerpo, registra `An error occurred while adding an AIGoalSelector, enable debugging for a stack trace.` y retorna sin lanzar; una línea con error corta en silencio las líneas siguientes. Además, una línea cuya meta no es válida para la especie (`AI pathfinder {0} is not valid for mob type {1}`) o que no se puede construir (`Failed to construct AI pathfinder {0}`) se salta. La línea no válida se salta y las siguientes continúan. `FloatGoal`, `RandomStrollGoal` y `FleeConditionalGoal` solo son válidas si `AbstractEntity.isCreature()` (entidad `org.bukkit.entity.Creature`); slime, magma cube, ghast, phantom y murciélago son `Mob` pero no `Creature`. M05 comprueba después, con `MobGoals`, que el selector tiene una meta por línea; si faltan, no añade las metas de Paper, devuelve el control a MythicMobs, deja un marcador `owner` de rechazo con la revisión y la generación y responde `Unsupported`; los mobs que no son `Creature` responden `Unsupported` antes de tocar sus metas. El recuento es completo: la `Goal` de `net.minecraft` añade `UNKNOWN_BEHAVIOR` cuando su conjunto de banderas está vacío y `PaperMobGoals.getAllGoals` recorre todos los `GoalType`; solo una meta del selector de metas que declarase `TARGET` quedaría fuera, y ninguna de las generadas lo hace.
- **Evidencia en servidor:** Pendiente de servidor (pasos 4.10, 6.6 y 9.7). Los valores `paperBefore`, `paperAfterClear` y `paperAdded` se retiraron con los presets en M12; como M05 ya aplica la alternativa (quitar las tres claves antes de instalar), la primera pregunta queda sin observación directa y el guion comprueba la ausencia de claves repetidas y la lectura del marcador.
- **Decisión:** Pendiente.
- **Consecuencia:** Orden de instalación de M05.
- **Alternativa si no se confirma:** `removeGoal` de las tres claves antes de instalar; guardar la revisión instalada en `MobSession` (M05).
- **Módulos afectados:** M05.
- **Estado:** Abierta.

### DT-16. Línea de meta sin prioridad

- **Pregunta:** ¿Cómo interpreta MythicMobs una línea de `AIGoalSelectors` sin número de prioridad?
- **Contexto:** M05 desplaza +10 las líneas de la IA base.
- **Evidencia estática:** ninguna.
- **Evidencia estática adicional (M05):** en `addPathfinderGoals`, una línea cuya primera parte no es numérica recibe como prioridad su posición en la lista, empezando en 1 (contador que aumenta con cada línea). Tras la auditoría, `BaseAiLines` aplica esa misma regla a la IA base y escribe la prioridad explícita. Una línea que falla (por ejemplo, una vacía, cuyo número `""` no se puede leer) activa la captura general del método y corta las líneas restantes (ver DT-15).
- **Evidencia en servidor:** Pendiente de servidor (paso 4.9). Desde M12 el paso usa un mob nativo sin ThePredict (`TPV_NoPriority`, `Predictor: false`) consultado por UUID con `debug goals`; el preset `no_priority` se retiró.
- **Decisión:** Pendiente.
- **Consecuencia:** Regla de desplazamiento del generador.
- **Alternativa si no se confirma:** el generador escribe siempre prioridades explícitas (ya previsto en M05).
- **Módulos afectados:** M05.
- **Estado:** Abierta.

### DT-17. Permiso de depuración y consola

- **Pregunta:** ¿Bloquea `thepredict.debug` (`default: false`) a los operadores que no lo tienen, y puede la consola usar `debug` con UUID?
- **Contexto:** M01.2; M12 traslada el comando a `admin`.
- **Evidencia estática:** Bukkit resuelve un permiso no asignado con su valor por defecto, y `default: false` no se concede ni a operadores ni a la consola. Por eso `admin.ThePredictCommand.mayDebug` (desde M12; antes `ThePredictStatusCommand`) permite siempre a `ConsoleCommandSender`; RCON y bloques de comandos siguen necesitando el permiso.
- **Evidencia en servidor:** Pendiente de servidor (pasos 2.1 y 2.5).
- **Decisión:** Aplicada localmente: consola siempre permitida; jugadores con el permiso. En el servidor de verificación el permiso se concede con `permissions.yml` (guion 1.3). **M12 conserva la regla** en `admin.ThePredictCommand`: sin el permiso, los subcomandos `debug` no se listan en el autocompletado ni se ejecutan; `ThePredictCommandTest` lo prueba en local.
- **Consecuencia:** M12 conserva la regla; solo la evidencia de servidor la cambiaría.
- **Alternativa si no se confirma:** exigir el permiso también a la consola y documentar cómo concederlo.
- **Módulos afectados:** M12.
- **Estado:** Abierta.

### DT-18. Costo de instalación

- **Pregunta:** ¿Cuánto tarda instalar el conjunto generado en 1 y en 30 mobs?
- **Contexto:** Valor inicial de `decision.maxInstallsPerTick` (M03).
- **Evidencia estática:** ninguna.
- **Evidencia en servidor:** Pendiente de servidor (paso 4.8). Desde M12 la medida es la línea `tick_work` de `/thepredict status` (p50, p95, p99 y `max` del trabajo por tick del coordinador, que incluye las instalaciones) con 1 y 30 mobs, y opcionalmente spark; las líneas `micros`, `totalMicros` y `maxMicros` se retiraron.
- **Decisión:** Pendiente.
- **Consecuencia:** Límite de instalaciones por tick.
- **Alternativa si no se confirma:** valor conservador en M03 (por ejemplo 4 por tick) hasta medir.
- **Módulos afectados:** M03.
- **Estado:** Abierta.

### DT-19. Especies con `Brain`

- **Pregunta:** ¿Tiene un mob con `Brain` (Piglin) un selector de metas que ThePredict pueda gobernar?
- **Contexto:** El binder de M03 excluye esas especies del control de movimiento; `Movement.NONE` es su única opción (01 §3).
- **Evidencia estática:** lista de especies que sobrescriben `makeBrain` en el plan §2.
- **Evidencia en servidor:** Pendiente de servidor (pasos 4.11 y 9.9).
- **Decisión:** Pendiente.
- **Consecuencia:** Confirma la regla del binder.
- **Alternativa si no se confirma:** mantener la exclusión por lista de especies.
- **Módulos afectados:** M03, M05.
- **Estado:** Abierta.

### DT-20. Cobertura de eliminaciones y descargas

- **Pregunta:** ¿Cubren `MythicMobDeathEvent`, `MythicMobDespawnEvent` y `EntityRemoveEvent` toda desaparición de un mob vinculado? ¿Distingue M06 una descarga de chunk de una eliminación?
- **Contexto:** M06.2 cierra la sesión en cada eliminación y la suspende en cada descarga; la reconciliación de M06.6 corrige lo que falte.
- **Evidencia estática (M06, javap de MythicMobs 5.13.1 y del servidor Paper 26.2 parcheado localmente, sin arrancar):** los cuatro eventos de ciclo de vida de MythicMobs llaman a `Event()` (síncronos) y `PaperEventManager.callEvent` rechaza un evento síncrono fuera del hilo principal salvo durante el apagado. `CraftEventFactory.callEntityRemoveEvent` no emite nada para jugadores ni cuando la causa es nula; `PaperHooks.unloadEntity` emite `EntityRemoveEvent` con causa `UNLOAD` al descargar entidades de un chunk. `ActiveMob.setUnloaded()` emite `MythicMobDespawnEvent` (a través de `BukkitBootstrap.handleMobDespawnEvent`, que lo programa con `Schedulers.of(entity).run`) solo si su `DespawnMode` despawnea al descargar; si no, guarda el mob y lo quita del registro activo con `MobRegistry.setInactive`. `MythicMobDeathEvent` sale de `BukkitSkillTriggers.onDeathTrigger` (`EntityDeathEvent`). M06 suspende con `UNLOAD` y cierra con cualquier otra causa. Modos de despawn (`DespawnMode`, campos `despawnWithoutNearbyPlayers`, `savesToDisk`, `despawnsOnUnload`, `obeysKillCommand`): `NORMAL` (valor por defecto; `true`, `yes`) no se guarda y despawnea al descargar; `NEVER` (`false`, `no`, `never`) y `PERSISTENT` (`Options.Persistent`) se guardan y no despawnean al descargar; `CHUNK` despawnea al descargar. `MobType` lee `Options.Despawn` con `DespawnMode.get`, que ignora mayúsculas.
- **Evidencia en servidor:** Pendiente de servidor (pasos 10.3–10.5; desde M12, líneas `Bound mobs: …` y `Lifecycle: …` de `/thepredict status`).
- **Decisión:** Pendiente.
- **Consecuencia:** Confirma que la reconciliación solo actúa como red de seguridad. Límite conocido: la reconciliación solo revisa sesiones `ACTIVE`; una sesión `SUSPENDED` cuyo mob se borra mientras está descargado (mundo eliminado, regiones editadas) queda en memoria hasta el siguiente arranque. No se añade un plazo de cierre en M06 porque exigiría una clave de configuración nueva; M13 mide si se acumulan (pendiente en M13).
- **Alternativa si no se confirma:** `EntityAddToWorldEvent` y `EntityRemoveFromWorldEvent` de Paper (M06.2); mientras tanto, la reconciliación cierra cada 200 ticks las sesiones sin instancia activa.
- **Módulos afectados:** M06.
- **Estado:** Abierta.

### DT-21. Cambio de mundo

- **Pregunta:** ¿Qué evento llega cuando un mob vinculado cambia de mundo por portal y por comando: `EntityTeleportEvent`, `EntityPortalEvent` o ambos?
- **Contexto:** M06.2 avanza la generación y saca la sesión de su encuentro; mientras M01 no confirme, escucha los dos.
- **Evidencia estática (M06, javap del servidor Paper 26.2 parcheado localmente, sin arrancar):** todo `Entity.teleport(TeleportTransition)` de una entidad que no es jugador emite `EntityTeleportEvent` (o `EntityTeleportEndGatewayEvent` en una puerta del End) antes de moverla. Entre dimensiones, `teleportCrossDimension` crea una entidad nueva con `EntityType.create`, copia el estado con `restoreFrom` (que también traslada la `CraftEntity`) y retira la antigua con `removeAfterChangingDimensions`, que llama a `setRemoved(CHANGED_DIMENSION, null)`: sin causa no hay `EntityRemoveEvent`. Por eso las metas instaladas se pierden y M06 marca `installPending` con la generación nueva. `EntityPortalEvent` extiende `EntityTeleportEvent` pero tiene su propia `HandlerList`, así que un listener de uno no recibe el otro.
- **Evidencia en servidor:** Pendiente de servidor (paso 10.6; anotar si la generación sube una o dos unidades).
- **Decisión:** Pendiente.
- **Consecuencia:** Si llegan ambos en un viaje por portal, la generación sube dos veces, lo que es inocuo; si uno basta, M06 deja de escuchar el otro.
- **Alternativa si no se confirma:** la captura de M07 detecta el mundo distinto y avanza la generación (riesgo de M06). **Implementada en M07:** cada captura informa del mundo de cada miembro (`MobSighting`) y `engine.EncounterCapture` llama a `SessionLifecycle.changedWorld` si no es el del encuentro; así un evento perdido se corrige en la siguiente captura.
- **Módulos afectados:** M06, M07.
- **Estado:** Abierta.

### DT-22. Invisibilidad por efecto

- **Pregunta:** ¿`Entity#isInvisible()` es verdadero para un jugador con el efecto de invisibilidad, o hay que comprobar además `hasPotionEffect(PotionEffectType.INVISIBILITY)`?
- **Contexto:** M07.2 muestra solo `INVISIBLE` de un jugador invisible y ninguna otra señal visual.
- **Evidencia estática (M07, javap del servidor Paper 26.2 parcheado localmente, sin arrancar):** `CraftEntity.isInvisible()` devuelve `Entity.isInvisible()` del servidor; `LivingEntity.updateInvisibilityStatus()` llama a `setInvisible(hasEffect(MobEffects.INVISIBILITY))` cuando hay efectos activos y a `setInvisible(false)` cuando no los hay.
- **Evidencia en servidor:** Pendiente de servidor (paso 11.4).
- **Decisión:** Pendiente. M07 usa solo `isInvisible()`.
- **Consecuencia:** Si se confirma, no hace falta leer efectos para la percepción.
- **Alternativa si no se confirma:** leer además `hasPotionEffect(PotionEffectType.INVISIBILITY)` en `PaperEntityReads.participant`.
- **Módulos afectados:** M07.
- **Estado:** Abierta.

### DT-23. Grupo de cooldown de la perla

- **Pregunta:** ¿`HumanEntity#getCooldown(Key)` con `minecraft:ender_pearl` devuelve el cooldown que deja una perla lanzada, igual que `getCooldown(Material.ENDER_PEARL)`?
- **Contexto:** M07.3 (`PEARL_ON_COOLDOWN`). `getCooldown(Material)` crea un `ItemStack` en cada llamada; la lectura por grupo no asigna.
- **Evidencia estática (M07, javap del servidor Paper 26.2 parcheado localmente, sin arrancar):** `CraftHumanEntity.getCooldown(Material)` construye `new ItemStack(material)` y llama a `getCooldown(ItemStack)`, que resuelve `ItemCooldowns.getCooldownGroup(stack)` y lee `getRemainingCooldown(grupo)`. `getCooldownGroup` devuelve el grupo del componente `USE_COOLDOWN` si existe y, si no, el ID del ítem (`BuiltInRegistries.ITEM.getKey`). `getCooldown(Key)` lee `getRemainingCooldown(PaperAdventure.asVanilla(key))` directamente.
- **Evidencia en servidor:** Pendiente de servidor (paso 11.5, fila de la perla).
- **Decisión:** Pendiente. M07 lee el grupo `minecraft:ender_pearl`.
- **Consecuencia:** Una perla con otro grupo de `USE_COOLDOWN` (ítem personalizado) no se detecta; es aceptable para la 0.0.1.
- **Alternativa si no se confirma:** `getCooldown(Material.ENDER_PEARL)`.
- **Módulos afectados:** M07.
- **Estado:** Abierta.

### DT-24. Condiciones de contexto sobre el mob

- **Pregunta:** ¿`SkillCondition.evaluateEntity(BukkitAdapter.adapt(mob))` evalúa correctamente las condiciones de los contextos del perfil, como `healthpercent{p=<30%}`, o hace falta `evaluateCaster(metadata)`?
- **Contexto:** M07.7 evalúa cada contexto una vez por captura, sin construir `SkillMetadata`.
- **Evidencia estática (M07, javap de MythicMobs 5.13.1):** `evaluateEntity` resuelve la condición y la despacha como `IEntityCondition.check(entity)`, `ILocationCondition.check(entity.getLocation())` o `ICasterCondition.check(getSkillManager().getCaster(entity))`; devuelve falso para otras formas y captura `Exception` y `Error`, que registra con `MythicLogger.errorCondition`. `evaluateCaster` añade las formas `ISkillMetaCondition` e `ISkillMetaComparisonCondition`, que necesitan metadatos. `HealthCondition` implementa `IEntityCondition` e `ISkillMetaComparisonCondition`.
- **Nota (auditoría de M18):** la condición de ejemplo `health{h=<30%}` era inválida en 5.13.1 (vida absoluta; el `%` da `NumberFormatException`, DT-40 f); todos los ejemplos y pruebas usan ahora `healthpercent{p=<30%}`, que también implementa `IEntityCondition`.
- **Evidencia en servidor:** Pendiente de servidor (paso 11.8).
- **Decisión:** Pendiente. M07 usa `evaluateEntity`.
- **Consecuencia:** Las condiciones que solo existen como `ISkillMetaCondition` (dependen del trigger o del objetivo de una skill) quedan falsas en un contexto; se documenta para autores en M14.
- **Alternativa si no se confirma:** `evaluateCaster` con metadatos construidos como en `MythicSkillExecutor`.
- **Módulos afectados:** M07.
- **Estado:** Abierta.

### DT-25. Resumen de `CombatTracker`

- **Pregunta:** ¿`CombatTracker#getCombatDuration()` está en ticks? ¿La clasificación de `CombatEntry` con `Registry#getTag(DamageTypeTagKeys.…)` y `Tag#contains(TypedKey.create(RegistryKey.DAMAGE_TYPE, tipo.key()))` reconoce flechas como `IS_PROJECTILE`?
- **Contexto:** M07.8 (`combatSeconds`, totales por tipo de daño) y M11.
- **Evidencia estática (M07, javap del servidor Paper 26.2 parcheado localmente, sin arrancar):** `CombatTracker.getCombatDuration()` devuelve `tickCount - combatStartTime` en combate y `combatEndTime - combatStartTime` fuera de él; `PaperCombatTrackerWrapper` lo reenvía sin cambios, igual que `isInCombat`. `recheckStatus` reinicia el estado tras 300 ticks sin daño en combate (100 fuera de él). La API expone `Registry#getTag(TagKey)`, `Tag` como `RegistryKeySet` con `contains(TypedKey)`, y `TypedKey.create(RegistryKey, Key)`.
- **Evidencia en servidor:** Pendiente de servidor (paso 11.9; `inspect` muestra `combat <n> s`). M12 retiró la clasificación por etiquetas (`damageTotals`), que solo usaba `debug capture`; la pregunta queda reducida a la unidad de `getCombatDuration()`.
- **Decisión:** Pendiente. M07 convierte ticks en segundos dividiendo entre 20.
- **Consecuencia:** Si la conversión de clave fallara, la clasificación daría `OTHER` y M11 perdería el tipo de daño; `combatSeconds` no depende de ella.
- **Alternativa si no se confirma:** `Registry.DAMAGE_TYPE.getKey(tipo)` para la clave.
- **Módulos afectados:** M07. Nota de M11: la amenaza enriquecida no usa los totales por tipo; el daño de cada golpe sale de `EntityDamageEvent#getFinalDamage()` (DT-27).
- **Estado:** Abierta.

### DT-26. Golpe vanilla, invulnerabilidad y alcance

- **Pregunta:** ¿`LivingEntity#attack` aplica su propio límite de alcance? ¿Aplazar un cast mientras `getNoDamageTicks() > getMaximumNoDamageTicks() / 2` evita desperdiciar el golpe? ¿Un mob controlado recibe un solo golpe por intención, sin golpe de la meta base?
- **Contexto:** M10.3 (invulnerabilidad) y M10.9 (choice `VANILLA_MELEE`), plan §5.
- **Evidencia estática (M10, javap del servidor Paper 26.2 parcheado localmente, sin arrancar):** `CraftLivingEntity.attack(Entity)` comprueba solo que el objetivo no sea nulo y que no se esté generando el mundo, y llama a `LivingEntity.doHurtTarget(ServerLevel, Entity)` para un mob (`Player.attack` para un jugador): no hay límite de alcance. `swingMainHand()` llama a `swing(MAIN_HAND, true)`. `getNoDamageTicks()` y `getMaximumNoDamageTicks()` devuelven `invulnerableTime` e `invulnerableDuration`; `LivingEntity.hurtServer` trata un golpe como dentro de la invulnerabilidad si `(float) invulnerableTime > (float) invulnerableDuration / 2`. `CraftHumanEntity.isBlocking()` es `Player.isBlocking()`.
- **Evidencia en servidor:** Pendiente de servidor (pasos 12.3 y 12.4).
- **Decisión:** Pendiente. M10 limita el alcance de `VANILLA_MELEE` con la distancia entre cajas de colisión frente a `meleeReachBlocks` y aplaza con la misma comparación en `float`.
- **Consecuencia:** Si el golpe aplazado se pierde igualmente o aparece un segundo golpe base, M13 lo registra y se ajusta la regla.
- **Alternativa si no se confirma:** medir el alcance como la lectura de alcance de M07 (`EntityReads.reachBlocks`) y lanzar el golpe en el tick en que termina la invulnerabilidad.
- **Módulos afectados:** M10.
- **Estado:** Abierta.

### DT-27. Amenaza enriquecida en la tabla nativa

- **Pregunta:** ¿Un punto de amenaza de ThePredict equivale a un punto de daño final, como la amenaza de `ThreatTable.UseDamageTaken`? ¿`threatLoss` deja de restar en cero? ¿Un golpe de un mob bloqueado con escudo llega a `EntityDamageByEntityEvent` sin cancelar y con `isBlocking()` verdadero en el jugador? ¿`Mob#getTarget()` es el objetivo que eligió la tabla? ¿El líder de la tabla solo cambia cuando otro jugador la supera en un 10 %?
- **Contexto:** M11.1 (unidad de los pesos), M11.2 (golpe bloqueado y acoso a distancia de quien no es el objetivo), M11.3 (pérdidas sin limitador) y M11.5 (rango de amenaza).
- **Evidencia estática (M11, javap de MythicMobs 5.13.1 y del servidor Paper 26.2 parcheado localmente, sin arrancar):** `DamageTriggerListeners.onCombatTrigger(EntityDamageByEntityEvent)`, en prioridad `HIGHEST`, resuelve el lanzador de un `Projectile` con `getShooter()`, fija como daño del evento `EntityAttackMetadata.getFinalDamage()` (`setDamage`) y, si el mob usa tabla y `getThreatTableUseDamageTaken()` es verdadero, llama a `threatGain(atacante, EntityAttackMetadata.getFinalDamage())`: un punto por punto de ese daño. El `EntityDamageEvent#getFinalDamage()` que lee M11 en `MONITOR` es posterior a armadura, resistencia y absorción, así que es menor o igual que la amenaza nativa del mismo golpe (nota para M14). `ActiveMob.ThreatTable.threatGain` ignora al propio mob, a su dueño y a jugadores en `CREATIVE` o `SPECTATOR`; sin objetivo, la entidad pasa a serlo (`targetEvent`); con otro objetivo, cambia a la entidad si su amenaza supera `targetThreat × 1,1` (`targetThreateningEntity`). `threatLoss` no hace nada si la entidad no está en la tabla, deja el valor en 0 como mínimo (resta solo lo que había) y, si era el objetivo, llama a `targetHighestThreat()`, que elige el valor más alto del mapa. `getTopThreatHolder()` devuelve el objetivo actual (`target`), no el máximo. `tickThreat()` descarta un objetivo muerto (`targetDeath`), desconectado o en otro mundo y, con `getThreatTableDecaysUnreachable()`, llama a `decayTargetThreat()` (×0,96) mientras no hay línea de visión. `getThreatTable()` devuelve el mapa vivo `UUID → amenaza` y `asMap()` una copia: el listado para ordenar existe. En Paper, `LivingEntity.hurtServer` calcula `applyItemBlocking` antes de `handleEntityDamage`, que lanza el evento con el modificador `BLOCKING`; `CombatTracker.RESET_DAMAGE_STATUS_TIME` es 100.
- **Evidencia en servidor:** Pendiente de servidor (sección 13 del guion).
- **Decisión:** Pendiente. M11 usa la evidencia estática: pesos en puntos de daño final (el acoso, con el daño tras armadura, resistencia y absorción), pérdidas sin limitador (el suelo es 0 en MythicMobs), sin `threatSet` ni `Taunt`, ninguna amenaza a un jugador muerto, desconectado o en otro mundo, `MELEE_BLOCKED` por `isBlocking()` (M07), el objetivo por `Mob#getTarget()` leído después de la amenaza nativa del mismo golpe, el decaimiento solo con una ruta viva que no llega y rangos únicos `1..n` por amenaza total (M07).
- **Consecuencia:** Si la escala no fuera la del daño, solo cambia la documentación de los pesos (M14); si `threatLoss` bajara de cero, se limita la pérdida acumulada a `maxPerWindow` por ventana; si el evento no llegara con el golpe bloqueado, `blockedHits` no tendría efecto y M13 lo registra.
- **Alternativa si no se confirma:** pesos documentados como "puntos de amenaza" sin equivalencia; tope de pérdidas por ventana; golpe bloqueado por el modificador `BLOCKING` del evento; objetivo por `getTopThreatHolder()`.
- **Módulos afectados:** M11, M13, M14.
- **Estado:** Abierta.

### DT-28. Skill de una táctica renovada por Jev

- **Pregunta:** Con Jev, ¿pierde daño el boss porque una táctica que Jev renueva conserva `skillConsumed` y no vuelve a lanzar su skill hasta que la táctica cambia?
- **Contexto:** M10 (desviación 15): una intención `LOCAL` replanificada es nueva y puede lanzar otra vez cuando el `Cooldown` nativo lo permite; una renovación `JEV` conserva `skillConsumed` (plan §2: una respuesta no autoriza repetir un cast). Mientras ThePredict controla, la meta de ataque base no golpea (guion de M01, 12.3).
- **Evidencia local (M13, sin servidor):** arnés `TacticalAB`, duelo cuerpo a cuerpo de 30 s con la misma skill de golpe: 19,1 ± 1,2 casts sin Jev (nivel `LOCAL`), 4,4 ± 2,1 con un Jev guionizado que acierta el 85 % y 1,0 con uno que repite siempre la misma táctica. No hay casts duplicados en ningún brazo.
- **Evidencia en servidor:** Pendiente (A/B del guion 0.0.1, §7: daño hecho y tiempo en alcance con Predictor y clave frente al brazo clásico y al brazo local).
- **Decisión (usuario, 2026-09-29, tras la auditoría de M13; revisada el mismo día: prima la paridad con el modo local):** es un defecto de diseño de M10 que rompe la equidad del plan §12, cuya regla es un cast por intención (una replanificación local es una intención nueva). La choice de una intención `JEV` o `ALTERNATIVE` renovada se rearma por reloj, no por respuesta, exactamente cada vida de intención local, `min(maximumIntentTicks, minimumDecisionIntervalTicks)` (`FallbackStep.localIntentTicks`, compartido con la replanificación local). Lo que limita cuántas veces sale una skill es su `Cooldown` nativo y el cooldown extra; sin señal nueva y sin tocar histéresis ni permanencia mínima; una respuesta sin intervalo transcurrido nunca vuelve a lanzar. Aplicada en `IntentControl`, `FallbackStep` y `MobSession` (desviación 28 de M10; 01, revisión 9).
- **Evidencia local tras el cambio:** duelo del arnés con la temporización por defecto (intervalo 20, duración máxima 40), Jev que repite la táctica, 10 semillas: 17,6 ± 1,3 casts frente a 19,1 ± 1,2 del local (antes del rearme, 1,0); con duración máxima 20, 20,8 ± 1,2; con 100, 17,6 ± 1,3. Sin casts duplicados por época (`CastSemanticsTest`, `IntentPublicationTest`, `TacticalExecutionABTest`).
- **Revisión (usuario, 2026-09-30): disponibilidad en tiempo real.** El rearme por reloj hacía que una skill con `Cooldown` de 30 ticks saliera cada 40 (esperaba a la siguiente ventana de 20 ticks). Ahora la choice se rearma en el primer ciclo reactivo (cada 2 ticks) en que está lista: cooldown nativo (`CombatActions.ready`), cooldown extra y elegibilidad de la última captura con el alcance (`MobFacts.eligibleWhenReady`). En `LOCAL`, si la choice vigente está consumida o no está lista y el planificador elige una táctica cuya choice sí lo está, se rearma o se replanifica sin esperar a que venza la intención local; un cambio de táctica así respeta la permanencia mínima salvo que la táctica vigente ya no sea elegible. Al vencer una intención local cuya táctica sigue siendo la preferida del planificador salvo por el cooldown de su propia skill, se conserva esa táctica (como una renovación de Jev) en lugar de pasar a una de relleno durante el enfriamiento. Se mantienen: un cast como mucho por época, una respuesta sola nunca vuelve a lanzar, sin señal en un rearme, histéresis y permanencia mínima intactas. Tras un consumo que no fue `CAST` el reintento sigue esperando una vida de intención local. Un golpe vanilla (`VANILLA_MELEE`) conserva el ritmo de ataque vanilla, uno cada 20 ticks (`MeleeAttackGoal`), para no dar más daño que vanilla (plan §12). El control del spam es el `Cooldown` de cada skill ("se controlará el cd en cada skill"). Aplicada en `IntentControl`, `FallbackStep`, `MobSession`, `MobFacts` y el puerto `CombatActions` (desviación 29 de M10; 00, revisión 14; 01, revisión 14).
- **Evidencia local tras la revisión:** `ReadinessCadenceTest`: una skill con `Cooldown` de 30 ticks sale cada 30 ± 2 ticks en `LOCAL` y con Jev renovando la táctica, sin casts duplicados por época. Matriz del simulador de M18 en `evidencia/m18-simulador-2026-09-29/duel-local.md` (regenerada).
- **Consecuencia:** los dos brazos del A/B lanzan sus skills al ritmo de su `Cooldown` nativo; la guía del autor lo explica ("la skill sale a su propio `Cooldown`; ajusta `Cooldown` para controlar el spam").
- **Alternativa si no se confirma:** si el A/B del servidor muestra aún pérdida de daño con Jev, revisar el `Cooldown` de las skills del ejemplo; el rearme ya no es el límite.
- **Módulos afectados:** M10, M13, M14.
- **Estado:** Abierta: la decisión de diseño está tomada y aplicada; solo queda la confirmación en servidor (daño y tiempo en alcance del A/B).

### DT-29. Comandos de spark incluidos en Paper

- **Pregunta:** ¿Qué comandos de spark ofrece la versión incluida en Paper 26.2 build 129, y cuáles dan tiempo por tick y memoria sin subir datos a la web?
- **Contexto:** M13.2 (evidencia con spark sin publicar datos del servidor sin aprobación) y M13.3 (tiempo por tick y memoria).
- **Evidencia estática (M13, sin servidor):** `paper-26.2-129.jar` trae `META-INF/libraries/me/lucko/spark-paper/1.10.180/spark-paper-1.10.180.jar` (`META-INF/libraries.list`). javap de `me.lucko.spark.paper.common.command.modules`: `HealthModule` registra `tps`, `ping` y `health` (alias `healthreport`; opciones `--memory`, `--network`, `--upload`) con las cabeceras `TPS from last 5s, 10s, 1m, 5m, 15m:`, `Tick durations (min/med/95%ile/max ms) from last 10s, 1m:`, `Memory usage:` y `Usage at last GC:`; `TickMonitoringModule`, `tickmonitor` (alias `tickmonitoring`; `--threshold`, `--threshold-tick`, `--without-gc`); `GcMonitoringModule`, `gc` y `gcmonitor`; `SamplerModule`, `profiler` (`start`, `stop`, `cancel`, `open`; `--timeout`, `--thread`, `--only-ticks-over`, `--save-to-file`); `HeapAnalysisModule`, `heapsummary` y `heapdump` (`--save-to-file`, `--run-gc-before`, `--compress`).
- **Evidencia en servidor:** Pendiente (guion 0.0.1, paso 4.1).
- **Decisión:** Pendiente. El guion de M13 usa `/spark tps`, `/spark health --memory` y `/spark tickmonitor`, que responden en el chat, y nunca `--upload` ni el visor web sin aprobación.
- **Consecuencia:** Si alguno no responde, el guion usa `tick_work` de `/thepredict status` para el tiempo que añade el plugin y anota la memoria como no medida.
- **Alternativa si no se confirma:** `/tps` de Paper para el tiempo por tick y `tick_work` de ThePredict.
- **Módulos afectados:** M13.
- **Estado:** Abierta.

### DT-30. Errores reales de System One: modelo inexistente y clave inválida

- **Pregunta:** ¿Qué responde `POST /v1/systemone` con un modelo que no existe (¿422 con `loc` de `model` o 404?) y cuál es la forma real de un 401?
- **Contexto:** M08.5 (clasificación de fallos); un modelo inexistente debe bloquear como `INVALID_MODEL` y no pausar y reintentar.
- **Evidencia estática:** la OpenAPI 0.2.0 solo documenta 422.
- **Evidencia con la cuenta real (2026-09-29, M13, llamadas aprobadas por el usuario y ejecutadas por el orquestador; sin servidor):** modelo inexistente: HTTP **400** con `{"detail":{"error_type":"api_usage_error","message":"Unknown model: <modelo>"}}`. Clave inválida: HTTP 401 con `{"detail":{"error_type":"authentication_error","message":"Cannot authenticate with the server. Please check your API key and try again."}}`.
- **Decisión:** 400 con `api_usage_error` y un mensaje que empieza por `Unknown model` → `MODEL_UNAVAILABLE` con un detalle fijo que no repite el modelo; cualquier otro 400 → `INVALID_REQUEST`; el 401 se clasifica por el estado y su cuerpo se analiza sin error. Fixtures `error-400-unknown-model.json` y `error-401.json`.
- **Consecuencia:** un modelo mal escrito deja el servicio en `INVALID_MODEL` (bloqueado hasta `/thepredict check` o reinicio) en vez de pausar y reintentar sin fin (corrección 5 de M13).
- **Alternativa si no se confirma:** —
- **Módulos afectados:** M08, M09.
- **Estado:** Resuelta. El uso real de 403, y 429 y 529, siguen sin observarse (M08, «Verificación con la cuenta real»).

### DT-31. Calibración con la cuenta real

- **Pregunta:** ¿Cuántos caracteres por token tiene una solicitud real, cuánto se desvía la estimación de M09, y qué `minimumConfidence`, `tacticSwitchMargin` y tolerancia de la suma de probabilidades convienen a `jev-1.13.0`?
- **Contexto:** M08 (desviación 18), M09.3, M10.2 y M13.6.
- **Evidencia con la cuenta real (2026-09-29, banco semilla de M13, 1 200 decisiones, [informe](evidencia/live-scenario-bench-2026-09-29.md)):** 1,59 / 1,73 / 1,82 caracteres por token; 613 tokens por llamada frente a 590 estimados; confianza 0,50 conserva el 33,3 % con un 100 % aceptable (0,40: 84,7 %; 0,30: 45,4 %); margen 0,00 da un 0,5 % de cambios y desde 0,10 ninguno; las 1 200 respuestas del informe cumplen la suma 1 ± 0,02; modelo efectivo `jev-1.13.0` en todas; ida y vuelta p99 328 ms.
- **Decisión:** `RequestSizeGuard` pasa de 2 a 1,5 caracteres por token (calibrado con texto en inglés); se mantienen la estimación 400 / 120 / 60 y la tolerancia ± 0,02. `minimumConfidence` 0,5 y `tacticSwitchMargin` 0,15 quedan respaldados de forma preliminar: las etiquetas son las propuestas por M13 (`labelsProposedBy`) y el usuario no las ha revisado; la muestra efectiva son 6 escenarios de una sola pregunta repetidos 200 veces; la curva no es monótona (0,0–0,3: 69 % aceptable; 0,3–0,5: 5,8 %) y no hubo contingencias. Siguen abiertos hasta que el usuario revise las etiquetas y haya escenarios más variados. `timeoutMillis` 800 queda pendiente de los datos del host (guion 0.0.1, §6.5): el p99 de 328 ms se midió desde la máquina de desarrollo.
- **Consecuencia:** la guarda de tamaño nunca subestima con texto en inglés (objetivos de autor en otras escrituras, como CJK, pueden tener menos caracteres por token; M14 lo documenta). A 0,5, el 66,7 % de las respuestas pagadas de este banco se descartan por confianza baja (`LOW_CONFIDENCE`): la mayor parte del gasto compra respuestas que no se usan. **Punto clave para que el usuario lo revise**; opciones para M14 o la 0.0.2: mejorar la redacción de las preguntas y las etiquetas, o un `minimumConfidence` por perfil. Una versión nueva del modelo repite el banco (plan §6).
- **Alternativa si no se confirma:** —
- **Módulos afectados:** M08, M09, M10, M13.
- **Estado:** **Resuelta** (2026-09-30) para `jev-1.13.0`: caracteres por token, estimación de M09, tolerancia ± 0,02 y, con el re-banco de M16.10, `minimumConfidence` (0,45) y `questionDetail` (`full`). `tacticSwitchMargin` 0,15 se conserva: el re-banco no lo mide y los datos del 2026-09-29 no dan cambios desde 0,10. `timeoutMillis` 800 no es un umbral del modelo; su medida desde el host sigue en el guion 0.0.1, §6.5.
- **Nota de M16 (2026-09-29):** M16 atribuye buena parte del 66,7 % de descartes al planteamiento (opciones con el mismo criterio literal, instrucción genérica, skills sin describir) y al banco (etiquetas sin revisar, 6 escenarios repetidos); lo corrige antes de tocar umbrales. La estimación 400 / 120 / 60 de M09 se retira: el presupuesto reserva por el tamaño del cuerpo preparado con los caracteres por token aprendidos (1,3–2,0); la guarda sigue en 1,5. M16 deja preparados el banco etiquetado de 60 escenarios, la hoja de revisión de etiquetas, el re-banco de pago (M16.10, 720 llamadas, ≈ 0,055 USD, sin ejecutar) y la regla escrita para elegir el umbral (M16.7: menor umbral con `n` ≥ 30 escenarios distintos revisados, ≥ 90 % aceptable, límite inferior de Wilson al 95 % ≥ 0,75 y como mucho un escenario prohibido). Esta entrada se actualiza cuando se ejecute M16.10 con las etiquetas revisadas; hasta entonces sigue **Abierta** en umbrales y ningún default cambia.
- **Resultado de M16.10 (2026-09-30, [evidencia](evidencia/m16-rebanco-2026-09-30/recomputed-2026-09-30.md)):** 720 llamadas pagadas (P1 `core12` × 10 con el brazo B; P2 60 escenarios × 2 repeticiones × brazos A–E), 750 611 tokens de entrada, ≈ 0,0315 USD. Las tablas se recalcularon sin red con las 60 etiquetas `REVIEWED` desde `P2/answers.jsonl`. La regla de M16.7 no da ningún umbral para A, B ni C. Para D (`full`) da **0,45**: 41 escenarios conservados, 90,2 % aceptables, Wilson inferior 0,775, 0 prohibidos. Para E da 0,50. A 0,5, D conserva el 66,7 % con un 93,8 % aceptable y un 71,9 % `best`; B (`compact`) conserva el 54,2 %. D cumple la regla de cambio de default de M16.2 frente a B: +12,5 puntos, acierto mayor y sin más prohibidas. La solicitud de producción mide 1 884 tokens reales con `full` y 953 con `compact`.
- **Decisión del usuario (2026-09-30):** `decision.questionDetail: full` y `decision.minimumConfidence: 0.45`, aplicados en la entrega de M17. `fitVeto` y `fitRescue` no cambian. **La muestra es modesta:** 48 escenarios `NOW` informativos × 2 repeticiones; el límite de Wilson lo tiene en cuenta, pero una versión nueva del modelo o un banco mayor repite la medida (plan §6).

### DT-32. Dónde se declara el propósito de una skill

- **Pregunta:** ¿El propósito tipado de una skill (papel, entrega, alcance, área, preparación, daño, cuándo conviene y riesgos) se declara en el perfil de ThePredict o en el YAML de la skill de MythicMobs?
- **Contexto:** M15.1 y M15.2. MythicMobs no expone nada de esto: `Skill` solo da nombre, configuración, condiciones, cooldown y uso; adivinar la entrega recorriendo la cola de mecánicas exigiría clases internas de `core` y aun así no diría cuándo conviene (A1).
- **Evidencia estática:** javap sobre `MythicMobsPremium-5.13.1-SNAPSHOT.jar` (M15, "Evidencia nativa"): `io.lumine.mythic.api.skills.Skill` y `AbstractSkill` (`getCooldown(): PlaceholderDuration`, `getCharges()`, `getCooldownMode()`, `getTargeter()`); `MetaSkill` extiende `AbstractSkill`.
- **Evidencia en servidor:** no necesita; la sesión 15 (paso 15.1) confirma que el ejemplo con sus propósitos vincula sin avisos.
- **Decisión (usuario, 2026-09-29):** por ahora **en el perfil** (`Choices.<id>.Purpose`), con la validación estricta de M03, porque el alcance y la preparación pueden depender del mob, ThePredict no amplía su huella en los archivos de MythicMobs y MythicScript 2077 no necesita vocabulario nuevo. El cooldown no se declara: se lee del `Cooldown` nativo. Dirección futura: leerlo también de la propia skill (origen `SKILL`, reservado en `PurposeOrigin`; precedencia perfil > skill > `PredictValues`).
- **Consecuencia:** `ProfileSchema.PURPOSE` y `config.PurposeParser`; sin `Purpose`, el propósito se infiere de `PredictValues` sin cambiar el comportamiento (M15.3).
- **Ampliación (M19, 2026-10-06):** el propósito también puede declararse en la skill con la línea `Intention` (origen `SKILL`). La precedencia es el perfil (`DECLARED`) sobre la skill (`SKILL`). `PredictValues` deja de dar propósito (decisión 5 del usuario): sin `Intention` y sin `Purpose` interno la choice queda sin tipo. `Purpose` del perfil sigue siendo la anulación del administrador y no se documenta para autores (decisión 7). `INFERRED` queda para el golpe vanilla fijo y los registros antiguos (errata E19-1 DP2). La frase de M15.3 sobre inferir desde `PredictValues` queda sustituida por esta ampliación.
- **Alternativa si no se confirma:** —
- **Módulos afectados:** M15, M16, M18, M19.
- **Estado:** Resuelta; ampliada por M19 (2026-10-06). La decisión de M15 (declararlo en el perfil) se conserva.

### DT-33. Escudo frente a daño de área de MythicMobs

- **Pregunta:** ¿Un escudo levantado de frente bloquea el `damage` de una skill `AREA_AROUND_SELF` (por ejemplo `damage{a=5} @PlayersInRadius{r=3}`) cuyo causante es el lanzador?
- **Contexto:** M15.6. Una skill declarada de área alrededor del lanzador no está "apuntada al objetivo", así que ThePredict no la retiene por el escudo (`SKIPPED_SHIELD`) ni por la invulnerabilidad; si vanilla la bloquea, lanzarla contra un escudo desperdicia la skill.
- **Evidencia estática:** javap sobre el JAR de MythicMobs (2026-09-29): la mecánica `damage` usa por defecto `cause` `ENTITY_ATTACK`, que MythicMobs aplica como `mobAttack` (el lanzador como causante del daño), y `ignoreshield=false`. Con esos defaults el escudo podría bloquearlo como bloquea un golpe cuerpo a cuerpo; no es concluyente hasta verlo en el servidor, porque el bloqueo también depende de la dirección que Paper atribuye al daño de área.
- **Evidencia en servidor:** Pendiente (sesión 15 del paquete, paso 15.5).
- **Revisión del usuario (2026-09-29):** en la revisión de etiquetas de `core24`, el usuario deja `bank-shield-02` como está (el slam sigue aceptable) y confirmará en el servidor si el escudo bloquea el daño de MythicMobs. No cambia ninguna regla de código hasta entonces.
- **Decisión:** Pendiente. Hoy el área se lanza aunque el objetivo bloquee.
- **Consecuencia:** si el escudo la bloquea, las áreas pasan a `ignoresShield` por defecto en la inferencia o se tratan como apuntadas al escudo (decisión de M15 o M16 con la evidencia).
- **Alternativa si no se confirma:** el autor declara `Risks: [BLOCKED_BY_SHIELD]` en la skill afectada.
- **Módulos afectados:** M15.
- **Estado:** Abierta.

### DT-34. `Cooldown` estático

- **Pregunta:** ¿`AbstractSkill.getCooldown().isStatic()` es falso para un `Cooldown` con placeholder y verdadero para un número, y `get()` devuelve entonces su `Duration`?
- **Contexto:** M15.3: la clase de cooldown (`NONE`, `SHORT`, `MEDIUM`, `LONG`) se lee del `Cooldown` nativo; un valor sin valor fijo legible queda `UNKNOWN`.
- **Evidencia estática:** javap: `PlaceholderDuration` extiende `IGenericPlaceholder<Duration>`; `IPlaceholder.isStatic()` e `isPresent()`; `IGenericPlaceholder.get()` llama a `get(PlaceholderContext.of())`; `PlaceholderParser.isStatic()` es `tokens.isStatic && !doMath && !papi`; `isPresent()` es `strValue` no vacío; `Duration.getSeconds()` convierte sus milisegundos. `AbstractSkill` inicia `cooldown` a `null`.
- **Evidencia en servidor:** Pendiente (sesión 15, pasos 15.1 y 15.1b con `/thepredict inspect`, línea `Skills`).
- **Decisión:** Pendiente. Mientras tanto la regla de M15.3 (un `Cooldown` sin valor fijo legible da `UNKNOWN`, que no se describe a Jev).
- **Consecuencia:** si un número da `UNKNOWN`, la clase de cooldown se leería de otra forma (por ejemplo del texto de la configuración); si un placeholder da una clase fija, se revisa `isStatic()`.
- **Alternativa si no se confirma:** leer `Cooldown` como número de `skill.getConfig()` y dejar `UNKNOWN` para cualquier texto que no sea un número.
- **Módulos afectados:** M15.
- **Estado:** Abierta.

### DT-35. Atribución del daño de skills de MythicMobs

- **Pregunta:** ¿El `damage` de MythicMobs llega a Paper como `EntityDamageByEntityEvent` con el lanzador de la skill como causante (`getDamager()`), de modo que el daño de una skill se atribuye al mob que la lanzó?
- **Contexto:** M16.8. La ventana de resultado (`OutcomeWindow`) suma el daño que hace cada mob vinculado; `CombatOutcomeListener.onDamage` lo atribuye al causante directo, al que disparó el proyectil o al lanzador de una skill, y calcula la fracción con `getFinalDamage()` sobre el atributo `MAX_HEALTH` de la víctima. El daño recibido y las muertes los registra `OutcomeListener` (`EntityDamageEvent` y `EntityDeathEvent`).
- **Evidencia estática:** la clase `io.lumine.mythic.bukkit.events.MythicDamageEvent` existe en el JAR 5.13.1; no se comprobó sin servidor si la mecánica `damage` dispara además el evento de Bukkit con el lanzador como causante.
- **Evidencia en servidor:** Pendiente (sesión 16 del paquete, pasos 16.1.b y 16.1.d: una skill del guardián que daña al jugador debe dar `dealt` mayor que 0 en su línea `Outcome …`).
- **Decisión:** Pendiente. Mientras tanto, atribución por `EntityDamageByEntityEvent`.
- **Consecuencia:** si se confirma, las ventanas miden el daño de las skills tal cual; si no, `dealt` y los golpes confirmados subestiman el efecto de las tácticas con skills.
- **Alternativa si no se confirma:** `OutcomeListener` atribuye el daño con `MythicDamageEvent` (sus accesores se comprueban con javap antes de implementarlo).
- **Módulos afectados:** M16, M18.
- **Estado:** Abierta.

### DT-36. Tendencia de una entidad por su velocidad

- **Pregunta:** ¿`getVelocity()` de un mob que camina con el navegador refleja su desplazamiento, de modo que la tendencia de un participante no jugador (`CLOSING`, `BACKING_AWAY`, `STRAFING`, `STEADY`) se puede leer de su velocidad?
- **Contexto:** M17.4. En jugadores la tendencia sale de las teclas (`Player#getCurrentInput()`), porque `getVelocity()` no refleja el paso de un jugador (plan §6). Un mob no tiene teclas: `TargetRelationReader.trendFromVelocity` proyecta la velocidad horizontal sobre la dirección hacia el mob (más de 0,05 bloques/tick hacia el mob es `CLOSING`, alejándose `BACKING_AWAY`, una componente lateral mayor `STRAFING`) y después aplica la misma corrección por distancia entre capturas que en jugadores.
- **Evidencia estática:** `Entity#getVelocity(): Vector` (javap, `paper-api` 26.2); en el servidor el movimiento de un mob lo aplica el propio servidor sobre su velocidad.
- **Evidencia en servidor:** Pendiente (sesión 17, paso 17.2: `inspect` de un mob que persigue a un zombi admitido).
- **Decisión:** Pendiente. Mientras tanto, velocidad proyectada más corrección por distancia.
- **Consecuencia:** si se confirma, la tendencia de un rival no jugador es tan informativa como la de un jugador; si no, solo la corrección por distancia decide.
- **Alternativa si no se confirma:** solo la corrección por distancia entre capturas (`correctedByDistance` sobre `STEADY`).
- **Módulos afectados:** M17, M18.
- **Estado:** Abierta.

### DT-37. Alcance cuerpo a cuerpo vanilla de un mob

- **Pregunta:** ¿Cómo decide Paper 26.2 que un `Mob` vanilla alcanza cuerpo a cuerpo a su víctima, para afirmar `outsideTargetReach` de un objetivo no jugador sin perfil de ThePredict sin afirmar nunca un hecho falso (A6)?
- **Contexto:** M17.4. Un rival de ThePredict da su alcance desde su perfil (M15). Cualquier otro `Mob` se compara como lo hace el servidor. Si no se sabe, no se afirma nada.
- **Evidencia estática:** javap `-c` sobre el servidor parcheado con paperclip (`versions/26.2/paper-26.2.jar`, obtenido en una sesión anterior y reutilizado sin red) y sobre `paper-api` 26.2:
  - `Mob.isWithinMeleeAttackRange(LivingEntity)` lee `getActiveItem().get(ATTACK_RANGE)`. En el servidor, `LivingEntity.getActiveItem()` devuelve el objeto en uso o, si no hay, el de la mano principal. Con el componente, el alcance es `AttackRange.effectiveMaxRange(this)` y el mínimo `effectiveMinRange(this)`; para un no jugador valen `maxReach × mobFactor` y `minReach × mobFactor`, en `float`. Sin el componente, el alcance es `DEFAULT_ATTACK_REACH = Math.sqrt(2.04f) - 0.6f` (≈ 0,8283) y el mínimo 0.
  - Comprueba `getAttackBoundingBox(max).intersects(víctima.getHitbox())` y, con un mínimo mayor que 0, que `getAttackBoundingBox(min)` **no** la corte.
  - `getAttackBoundingBox(r)` es la caja del mob ensanchada en `x` y `z` hasta la de su vehículo, si monta, e inflada con `inflate(r, 0, r)`: **solo en horizontal**.
  - `AABB.intersects` es estricto en los tres ejes. Por eso cada eje horizontal se compara por separado y en vertical las cajas deben solaparse. No es un hueco euclídeo: dos cajas en diagonal con 0,82 bloques de hueco en `x` y en `z` están al alcance aunque su hueco euclídeo sea de ≈ 1,16.
  - `LivingEntity.getHitbox()` sube el suelo de la caja de la víctima a la posición de montura cuando monta. ThePredict usa la caja entera, lo que solo puede dar "al alcance", nunca un "fuera" falso.
  - Paper API: `LivingEntity#hasActiveItem`, `getActiveItem`, `getEquipment().getItemInMainHand()`, `ItemStack#getData(DataComponentTypes.ATTACK_RANGE)` con `AttackRange#maxReach()`, `minReach()` y `mobFactor()`, `Entity#getVehicle()` y `getBoundingBox()`.
- **Evidencia en servidor:** Pendiente (sesión 17, paso 17.2: zombi admitido de frente a menos y a más de ≈ 0,83 bloques de hueco, y en diagonal).
- **Decisión (corregida tras la auditoría de M17):** `TargetReach` reproduce ese cálculo exacto (`withinVanillaMelee`) y `VanillaMobReach` elige el arma y el alcance como el servidor. No se afirma nada (alcance desconocido) en estos casos:
  - un mob con ataque a distancia nativo: `RangedEntity` de Paper (esqueletos, drowned, illusioner, llama, piglin, pillager, gólem de nieve, bruja, wither), un `Spellcaster` (evoker, illusioner), blaze, breeze, creeper, ender dragon, ghast, guardian, shulker y warden;
  - un arco, ballesta o tridente en la mano o en uso;
  - un mob de MythicMobs sin perfil de ThePredict, cuyas skills se desconocen;
  - cualquier entidad que no sea un `Mob`.
  La primera versión usaba un hueco euclídeo en 3D y siempre 0,83, y así afirmaba "fuera del alcance" en diagonal, con desnivel o con lanzas.
- **Consecuencia:** `outsideTargetReach` de un mob sin perfil solo es verdadero cuando el servidor tampoco le dejaría golpear.
- **Alternativa si no se confirma:** no afirmar `outsideTargetReach` para mobs sin perfil (alcance desconocido).
- **Módulos afectados:** M17, M18.
- **Estado:** Abierta.

### DT-38. Escudo de un no jugador

- **Pregunta:** ¿`hasActiveItem()`, el objeto activo con `BLOCKS_ATTACKS` y `getActiveItemUsedTime()` frente a `Math.round(blockDelaySeconds() × 20f)` (como `BlocksAttacks.blockDelayTicks` del servidor, javap; la primera versión redondeaba hacia arriba) bastan para saber que un mob levanta el escudo y bloquea?
- **Contexto:** M17.4 y M17.7. `HumanEntity#isBlocking()` solo existe en humanos. `adapter.server.context.Blocking` usa en el resto `hasActiveItem()` (levantado) y el tiempo de uso frente al retardo del componente (bloqueando; `0` bloquea al instante). No se usan `isHandRaised()` ni `getHandRaisedTime()` (`@ApiStatus.Obsolete(since = "1.20.4")`) ni `getShieldBlockingDelay()` (`@Deprecated(since = "1.21.9")`, devuelve 5 fijo).
- **Evidencia estática:** fuentes de `paper-api` 26.2: `LivingEntity#isHandRaised()` y `getHandRaisedTime()` son métodos por defecto que devuelven `hasActiveItem()` y `getActiveItemUsedTime()`; `BlocksAttacks#blockDelaySeconds(): float`.
- **Evidencia estática del servidor:** javap: `LivingEntity.getItemBlockingWith()` devuelve el objeto en uso cuando lleva `BLOCKS_ATTACKS` y `getUseDuration − useItemRemaining ≥ blockDelayTicks()`. Es la misma comparación que `Blocking`.
- **Cómo se levanta un escudo en un mob:** ni vanilla ni MythicMobs 5.13.1 lo hacen solos. Un zombi con escudo en la mano secundaria nunca lo levanta. javap sobre el JAR de MythicMobs: solo existe `VolatileEntityHandler.stopUsingItem` (mecánica `StopUsingItem`), ninguna mecánica ni meta llama a `LivingEntity#startUsingItem`. La única vía es `LivingEntity#startUsingItem(EquipmentSlot)` de Paper, que exige una configuración propia (un plugin o script de pruebas del usuario).
- **Evidencia en servidor:** Pendiente (sesión 17, paso 17.5). Sin esa configuración propia el paso solo comprueba lo observable: un mob con escudo que no lo levanta no da `RAISING_SHIELD` ni `BLOCKING`.
- **Decisión:** Pendiente. Mientras tanto, `Blocking.of`.
- **Consecuencia:** si se confirma, `RAISING_SHIELD`, `BLOCKING`, `flankable` y `SKIPPED_SHIELD` valen contra mobs; si no, solo `RAISING_SHIELD`.
- **Alternativa si no se confirma:** solo `RAISING_SHIELD` para no jugadores (nunca `BLOCKING`).
- **Módulos afectados:** M17, M18.
- **Estado:** Abierta.

### DT-39. Etiquetas de tipos tras recargar datapacks

- **Pregunta:** ¿El `Tag` que devuelve `Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, clave, EntityType.class)` refleja una etiqueta cambiada por `/minecraft:reload`?
- **Contexto:** M17.2. El selector `tag:` se vuelca a un `EnumSet` al vincular, en cada revisión de catálogo (`/mm reload`, que vuelve a leer y vincular los perfiles: `ProfileCatalogs.onMythicReload`; `/thepredict` no tiene subcomando de recarga).
- **Evidencia estática:** `Bukkit.getTag(String, NamespacedKey, Class)` no está marcado obsoleto (fuentes de `paper-api` 26.2); devuelve el `Tag` del servidor en ese momento.
- **Evidencia en servidor:** Pendiente (sesión 17, paso 17.2: cambiar la etiqueta de un datapack de prueba, `/minecraft:reload` y `/mm reload`).
- **Decisión:** volcar la etiqueta en cada vinculación; la guía del administrador dice que tras `/minecraft:reload` hace falta `/mm reload`.
- **Consecuencia:** una etiqueta cambiada por datapack solo cuenta tras la siguiente revisión del catálogo.
- **Alternativa si no se confirma:** la misma: documentar `/mm reload` tras recargar datapacks.
- **Módulos afectados:** M17.
- **Estado:** Abierta.

### DT-40. Paquete de duelo en 5.13.1 y Paper 26.2

- **Pregunta:** (a) ¿las líneas `Skills` y `AIGoalSelectors` de una variante se heredan y se suman a las de su `Template`? (b) ¿`glow{color=…;duration=2400}`, marcada obsoleta, sigue funcionando? (c) ¿`spawn_mobs false` impide los refuerzos de zombi en difícil? (d) `MythicSkillEvent` se dispara en `executeInternal` antes del bloqueo de cooldown y de las cargas, en `execute()` sin comprobación de uso y en los envoltorios `DuelRule*`: ¿cuántos eventos por cast real da cada regla clásica? (e) con `Damage: 0`, ¿el golpe vanilla de `meleeAttack` no da empuje ni invulnerabilidad asimétricos? (f) ¿qué condición mide la vida en porcentaje?
- **Contexto:** M18.1 (paquete de duelo con `Template: DuelBase`), M18.2 (arena en un mundo propio con reglas de juego por mundo) y M18.4 (el registrador cuenta como `ATTEMPT` los `MythicSkillEvent` de las cinco skills del duelo).
- **Evidencia estática (javap, sin red, 2026-09-29):**
  - (a) `MobExecutor` lee `Template` (o `Templates`) de cada mob y `TemplateUtil.processTemplate` llama a `config().merge(padre, exclusiones)` sobre la variante. `MythicConfigImpl.merge` recorre las claves del padre: si la variante tiene una lista no vacía con esa clave, la deja como las líneas del padre seguidas de las suyas (`addAll` sobre la lista del padre); si la clave es una sección, la mezcla; si no, copia el valor del padre solo cuando la variante no lo tiene. Así `Skills` suma las reglas y `DuelTimeout` de `DuelBase` con el brillo, la puntuación y las señales de cada variante, y `AIGoalSelectors`, que solo tiene la plantilla, pasa entera. `Exclude` y `Hidden` no se usan.
  - (b) `GlowEffect` sigue registrada como `effect:glow` (alias `glow`, `e:glow`) con `deprecated=true`; su campo `color` (`c`, por defecto `WHITE`) y la `duration` de `Aura` (`ticks`, `t`, `d`, `time`, por defecto `200`).
  - (c) `Zombie.hurtServer` del servidor 26.2 solo crea refuerzos (`EntitySpawnReason.REINFORCEMENT`) si `ServerLevel.isSpawningMonsters()`, que exige las reglas `SPAWN_MOBS` y `SPAWN_MONSTERS` verdaderas; `org.bukkit.GameRules` tiene `SPAWN_MOBS` (`spawn_mobs`), `SPAWN_MONSTERS` (`spawn_monsters`) y `ADVANCE_TIME` (`advance_time`).
  - (d) La de M10: `MetaSkill.executeInternal` lanza el evento antes de `tryAcquireCooldownLock` y `consumeCharge`.
  - (f) **Resuelta estáticamente (auditoría de M18).** `HealthCondition` (`health`, alias `hp`, argumento `amount`/`a`/`health`/`h`) construye `RangedDouble` con el texto tal cual y lo compara con la vida **absoluta** (`AbstractEntity.getHealth()`, más la absorción con `includeabsorption`); `RangedDouble` quita el operador (`>=`, `<=`, `>`, `<`, `to`) y llama a `Double.parseDouble`, así que `health{h=<50%}` lanza `NumberFormatException` (sin tabla de excepciones en `HealthCondition`; `SkillCondition` la registra como error de condición y la evalúa falsa). La condición en porcentaje es `HealthPercentCondition` (`healthpercent`, alias `hppercent`, argumento `percent`/`p`, por defecto `0`): con `%` final quita el signo y compara `RangedDouble` con `vida / vida máxima × 100` (`<50%` es menor que el 50 %); sin `%`, compara con la razón (`<0.5`); una entrada inválida da el error `Invalid health percent input` y falso. Implementa `IEntityCondition`, así que sirve en los contextos de ThePredict (`evaluateEntity`, DT-24). Por eso todo el repositorio pasa a `healthpercent{p=<N%}` (con ` true` en las `Conditions` de skills, como antes): el paquete de duelo, las skills y la plantilla de perfil del JAR, las etapas 03, 06, 09 y 10 del paquete, las guías, los guiones y las pruebas; `UserTestPackTest.noShippedPackOrDocumentedYamlChecksHealthInPercentWithTheAbsoluteHealthCondition` falla si vuelve a aparecer `health{…%}`.
  - Además: `nearestSpecificFaction` (alias `specificFaction`, argumento `faction`/`f`, lista separada por comas, comparada en mayúsculas con la facción del `ActiveMob` o con `FactionProvider.isInFaction` de un jugador); `PermissionFactionProvider.isInFaction` pide `faction.<nombre en minúsculas>` y rechaza a quien tiene `mythicmobs.nofaction`; `modifyscore` (`objective`, `entry`, `action` con `ADD`, `value`) escribe en el scoreboard principal; `BossBar.Enabled`, `Title`, `Range`, `Color` (`RED`, `BLUE`…) y `Style` (`SOLID`…); `/mm mobs kill <tipo>` marca el `ActiveMob` como despawn y quita la entidad (sin muerte ni `~onDeath`); `/mm mobs spawn <tipo> <n> <mundo>,<x>,<y>,<z>`; `execute in <dimensión> run …` y `fill … hollow` en los comandos vanilla del servidor 26.2.
- **Evidencia en servidor:** Pendiente (sesión 18, paso 18.2).
- **Decisión:** paquete con `Template` y `glow`, arena en un mundo propio con `spawn_mobs false`, `Damage: 0` en `DuelBase` (el golpe vanilla no hace daño a ningún lado; todo el daño sale de las cinco skills), y los clásicos medidos por intento (`MYTHIC_EVENT`, `ATTEMPT`) y acierto por daño en 20 ticks. (e) se observa en 18.2: con `Damage: 0` ninguno de los dos debería retroceder ni parpadear por el golpe; si el golpe vanilla empuja o da invulnerabilidad, afecta a los dos lados cuando están a nivel 4 pero no al de ThePredict mientras controla, y se anota.
- **Consecuencia:** el duelo es justo solo si (a) y (c) se confirman; el registrador no afirma que un `MYTHIC_EVENT` sea un cast terminado.
- **Alternativa si no se confirma:** (a) repetir las líneas en cada variante; (b) `potion{type=GLOWING;duration=2400;level=0} @self` con equipos del scoreboard; (c) `Options` de refuerzos si MythicMobs los expone o elegir una especie sin refuerzos; (d) medir los clásicos solo por daño; (e) quitar la meta `meleeAttack` de `DuelBase` y dejar que los clásicos se acerquen por `randomStroll` y las reglas, o una meta de acercamiento sin ataque.
- **Módulos afectados:** M18.
- **Estado:** Abierta.

### DT-42. Opción del motor: golpes vanilla en alcance durante APPROACH/HOLD sin consumir la choice

- **Pregunta:** ¿debe ThePredict dar a un mob que controla el golpe vanilla de su meta base cuando el objetivo está en alcance durante `APPROACH` o `HOLD`, sin consumir la choice de la intención?
- **Contexto:** auditoría de M18. Mientras ThePredict controla, la meta base de ataque no golpea (M05); el golpe vanilla solo llega como choice `Kind: VANILLA_MELEE`, que ocupa la intención. Un jefe clásico de MythicMobs suma el golpe vanilla a sus skills por trigger. Así un jefe de ThePredict pierde el daño pasivo cuerpo a cuerpo que tiene el clásico, aunque su táctica lance una skill. El duelo de M18 lo evita con `Damage: 0` en los dos lados; el problema general queda.
- **Evidencia estática:** M05 (propiedad de las metas: `thepredict:owner` y las metas de movimiento sustituyen a `meleeAttack` mientras la intención controla) y el simulador de M18.7 (con `Damage: 2`, el brazo A perdía casi siempre aunque el plan local no se retirara).
- **Evidencia en servidor:** No aplica todavía.
- **Decisión (usuario, 2026-09-30):** no. El daño cuerpo a cuerpo lo dispara una skill; ThePredict no da golpes vanilla automáticos. M18 lo había dejado documentado en la guía del autor (§4.2) y como límite conocido en el informe de la 0.0.1.
- **Consecuencia:** el autor pone el daño cuerpo a cuerpo de un jefe controlado en una skill de su táctica (una skill con `MELEE_ATTACK` o un `Purpose` de golpe) o en una choice `Kind: VANILLA_MELEE`. Desde la disponibilidad en tiempo real de M10 (desviación 29), esa skill sale al ritmo de su `Cooldown`, así que el `Cooldown` controla cuánto daño hace. Comparar un jefe de ThePredict con uno clásico sigue exigiendo igualar el golpe vanilla (por ejemplo `Damage: 0` en los dos, como el duelo de M18).
- **Alternativa si no se confirma:** —
- **Módulos afectados:** M05, M10, M18.
- **Estado:** Descartada por decisión del usuario (2026-09-30): el daño cuerpo a cuerpo lo dispara una skill; ThePredict no da golpes vanilla automáticos.

### DT-41. Radio de los targeters de MythicMobs

- **Pregunta:** ¿`@PlayersInRadius{r=3}` alcanza a un jugador cuando la distancia entre la posición del lanzador y la del jugador (`getLocation()`, los pies) es estrictamente menor que 3, de modo que `Radius: 3` de ThePredict decide igual?
- **Contexto:** M15.6 (corrección tras la auditoría, desviación 22 de M15): `AREA_AROUND_SELF` se mide como el `r` de los targeters de radio; antes se medía el hueco entre cajas y el `slam` podía lanzarse sin alcanzar.
- **Evidencia estática:** javap sobre `MythicMobsPremium-5.13.1-SNAPSHOT.jar`: `PlayersInRadiusTargeter.getEntities` recorre `world.getPlayers()` y guarda un jugador si `IEntitySelector.distanceSquared(caster.getEntity().getLocation(), jugador) < r²` (`dcmpg`/`ifge`); `IEntitySelector.distanceSquared` usa `AbstractLocation.distanceSquared(entity.getLocation())` salvo con `bb=true` (`useboundingbox`, falso por defecto). `EntitiesNearbyTargeter` (`@EntitiesInRadius`, `EIR`) con `SphereShape` guarda `≤ r²` desde `caster.getLocation()`. `TargetWithinCondition` compara `target.getLocation().distanceSquared(mob.getLocation()) < d²`.
- **Evidencia en servidor:** Pendiente (sesión 15, paso 15.3: el jugador a unos 2,9 y 3,1 bloques entre posiciones).
- **Decisión:** `ReachGeometry.withinRadius`: distancia entre posiciones al cuadrado estrictamente menor que `Radius²`.
- **Consecuencia:** el autor copia el `r` del targeter en `Radius`; ThePredict nunca lanza un área que `@PlayersInRadius` o `@EntitiesInRadius` no alcanzaría.
- **Alternativa si no se confirma:** si en servidor la posición del lanzador fuera otra (por ejemplo los ojos), `ReachGeometry` pasa a esa posición.
- **Módulos afectados:** M15.
- **Estado:** Abierta.

### DT-43. Clave Intention en skills de MythicMobs

- **Pregunta:** ¿MythicMobs 5.13.1 conserva la clave `Intention` sin quejarse?
- **Contexto:** M19.1 y M19.8. La línea vive en el YAML de la skill. ThePredict ya lee claves propias de la skill (`PredictValues`).
- **Evidencia estática:** R1 del plan de M19. Las únicas claves que son constantes de texto de `MetaSkill` no incluyen `Intention`. En el JAR no aparece un mensaje de clave desconocida para skills. Ningún changelog de 5.12.0–5.13.1 revisado en el plan habla de validar claves de skills.
- **Evidencia en servidor:** Pendiente (sesión 19, paso 19.6: filtrar la consola al cargar la etapa `19-intencion`).
- **Decisión:** Pendiente. Mientras tanto la guía usa la clave y marca la queja de consola como pendiente de servidor.
- **Consecuencia:** si MythicMobs la quitara o la rechazara, la skill no podría llevar el propósito y haría falta otra vía.
- **Alternativa si no se confirma:** documentar el fallo y no dar la clave por conservada.
- **Módulos afectados:** M19.
- **Estado:** Abierta.

### DT-44. Cooldown tras `/mm reload`

- **Pregunta:** ¿Se reinicia el cooldown en curso de una skill recargada?
- **Contexto:** M19.2. Tras `/mm reload`, ThePredict vuelve a vincular (`LifecycleListener.onMythicReloaded`).
- **Evidencia estática:** Inferencia de R3. `AbstractSkill` asigna `uuid = UUID.randomUUID()` en su constructor, y el registro de cooldowns guarda el estado por UUID de skill, así que una skill recargada sería un objeto nuevo y el cooldown en curso se reiniciaría. No está observado en ejecución.
- **Evidencia en servidor:** Pendiente (sesión 19, paso 19.3).
- **Decisión:** Pendiente. `nextReadyTick` se invalida con la generación y se recalcula.
- **Consecuencia:** si se confirma, el autor cuenta con que `/mm reload` reinicia el cooldown de MythicMobs. Si no, se anota la diferencia.
- **Alternativa si no se confirma:** la misma regla de invalidar `nextReadyTick`; solo cambia lo que se documenta del cooldown nativo.
- **Módulos afectados:** M19.
- **Estado:** Abierta.

### DT-45. Lectura de líneas nativas

- **Pregunta:** ¿`SkillMechanic.getConfigLine()` y `SkillCondition.getLine()` devuelven la línea con sus llaves y su targeter?
- **Contexto:** M19.3. El lector nativo reinterpreta el texto con `MythicLineConfigImpl.of(String)` porque varios campos de distancia son privados.
- **Evidencia estática:** javap de `SkillMechanic.getConfigLine()` y `SkillCondition.getLine()` (R6 y R7 del plan). No se ha visto el texto en un servidor.
- **Evidencia en servidor:** Pendiente (sesión 19, paso 19.1: `inspect` muestra la preparación, el alcance con su medida y el radio, iguales a los de la skill).
- **Decisión:** Pendiente. Si la línea no trae las llaves, el dato queda vacío.
- **Consecuencia:** un área o un cono sin radio legible no inventa un alcance (A6).
- **Alternativa si no se confirma:** riesgo 14 del plan. El dato queda vacío; `AREA_AROUND_SELF` o `CONE` sin radio pasan a exigir el `Purpose` del perfil, y la preparación queda en 0.
- **Módulos afectados:** M19.
- **Estado:** Abierta.

### DT-46. Nombres de tipo de MythicMobs

- **Pregunta:** ¿Qué texto exacto devuelve `getTypeName()` para `targetwithin`, `delay` y `effect:particles` (con o sin prefijo `effect:`, en minúsculas o no)?
- **Contexto:** M19.3. La preparación suma mecánicas `delay` y efectos cosméticos hasta la primera mecánica que no es cosmética. El conjunto de cosméticas usa los textos de `getTypeName()`.
- **Evidencia estática:** el lector compara sin distinguir mayúsculas y acepta las dos formas de los efectos (plan de M19). Los textos exactos en ejecución no están copiados aquí.
- **Evidencia en servidor:** Pendiente (sesión 19, paso 19.1: `inspect` imprime los nombres leídos en modo depuración).
- **Decisión:** Pendiente. La comparación sigue sin distinguir mayúsculas y acepta el prefijo `effect:` y la forma corta.
- **Consecuencia:** si un nombre no coincide, esa mecánica no cuenta como cosmética o como `delay`, y la preparación puede quedar corta.
- **Alternativa si no se confirma:** riesgo 14. La preparación queda en 0 cuando el tipo no se reconoce, sin inventar ticks.
- **Módulos afectados:** M19.
- **Estado:** Abierta.

### DT-47. Objetivo de targetwithin frente al objetivo del cast

- **Pregunta:** ¿Con qué frecuencia difieren el líder de la tabla de amenaza y `Mob.getTarget()` cuando hay participantes entidad (M17), y cuánto esperan por ello las skills con `targetwithin`?
- **Contexto:** M19.3 y M19.4. `TargetWithinCondition` mide contra `Mob.getTarget()` (R6). El objetivo de ThePredict es el líder de la tabla de amenaza y, si no hay, `Mob.getTarget()` (M17.5).
- **Evidencia estática:** javap de `TargetWithinCondition.check` (R6): el lanzador ha de ser un mob y la distancia se mide contra `Mob.getTarget()`, no contra el objetivo del `SkillMetadata` del cast.
- **Evidencia en servidor:** Pendiente (sesión 19, paso 19.7). No hay frecuencia medida.
- **Decisión:** la precondición 3 compara siempre con la regla de ThePredict y exige `Mob.getTarget()` solo a las skills con `targetwithin` nativo. La frecuencia queda abierta.
- **Consecuencia:** una skill sin `targetwithin` no espera por un `Mob.getTarget()` distinto del líder. Una con `targetwithin` espera cuando los dos objetivos difieren.
- **Alternativa si no se confirma:** anotar la frecuencia observada. No se exige `Mob.getTarget()` a todas las skills: eso rompería los mobs con tabla de amenaza.
- **Módulos afectados:** M19.
- **Estado:** Abierta.

### DT-48. Tick del impacto de un cast con delay

- **Pregunta:** confirmar en servidor el tick del impacto de una skill con `delay`, con `decision.castThread: same_tick` y con `mythic_default`, con el servidor sano y en retraso, y el coste por cast.
- **Contexto:** M19.4 adopta `CastThread.SAME_TICK`. M20 lo usa. Hecho estático (R1 y R2): sin `delay` el cast corre en el hilo del servidor e impacta en el mismo tick. Con `delay N`, la espera es de reloj de pared y el impacto llega en `cast + N + (0..1)` ticks, o antes si el servidor va retrasado.
- **Evidencia estática:** R1 y R2 del plan de M20, revisados en la auditoría del plan.
- **Evidencia en servidor:** Pendiente (sesión 20, paso 20.2). La sesión 19.9 solo cita esta entrada.
- **Decisión:** Pendiente. Severidad baja.
- **Consecuencia:** hasta medir, la guía no afirma el tick de impacto en juego.
- **Alternativa si no se confirma:** recomendar `mythic_default` o targeters baratos si un `delay` con `same_tick` pasa de 150 µs p95 por cast.
- **Módulos afectados:** M19, M20.
- **Estado:** Abierta, severidad baja.

### DT-49. Navegación asíncrona y coste de moveTo

- **Pregunta:** ¿los mobs de MythicMobs usan `AsyncGroundPathNavigation`? ¿Qué devuelven `Pathfinder.moveTo`, `hasPath` y `getCurrentPath` mientras la ruta está pendiente, y cuántos µs cuesta una ruta corta en el hilo del servidor, con y sin `Options.AsyncPathfinding`?
- **Contexto:** M20.5. El tope es 8 rutas y 400 µs por tick. Si una ruta corta pasa de 100 µs p95, el tope baja a 4.
- **Evidencia estática:** R4 del plan de M20. `Options.AsyncPathfinding` llama a `enableAsyncPathfinding`. El coste real no está medido.
- **Evidencia en servidor:** Pendiente (sesión 20, paso 20.1).
- **Decisión:** Pendiente. El default sigue en 8 rutas.
- **Consecuencia:** la guía marca el coste de la ruta como pendiente de servidor.
- **Alternativa si no se confirma:** bajar `decision.tickLayer.maxWaypointPathsPerTick` a 4 y dejar el micro en `NATIVE` tras dos fallos.
- **Módulos afectados:** M20.
- **Estado:** Abierta.

### DT-50. Retraso de la meta micro

- **Pregunta:** medir el retraso entre la publicación de la vista y el `start()` de `MicroGoal`. Se esperan de 0 a 2 ticks.
- **Contexto:** M20.5. El coordinador corre antes de las entidades. La selección de metas de Paper es cada 2 ticks.
- **Evidencia estática:** V1, V3 y V4 del plan de M20.
- **Evidencia en servidor:** Pendiente (sesión 20, paso 20.1).
- **Decisión:** Pendiente. Lo que exige un tick (casts) va en el coordinador, no en la meta.
- **Consecuencia:** la guía dice 0 a 2 ticks como expectativa, no como medida.
- **Alternativa si no se confirma:** documentar el retraso medido. No se usa NMS ni `requiresUpdateEveryTick`.
- **Módulos afectados:** M20.
- **Estado:** Abierta.

### DT-51. Driver SQLite y acceso nativo en el servidor

- **Pregunta:** confirmar en el servidor real, y en un arranque que no sea `java -jar`, que ThePredict abre la base sin el aviso de JEP 472 y con SQLite 3.49.1, JSONB y WAL.
- **Contexto:** M21.4. Paper 26.2 build 129 trae `sqlite-jdbc` 3.49.1.0 y su paperclip declara `Enable-Native-Access: ALL-UNNAMED`. Sin driver, los agregados se apagan y los JSONL siguen.
- **Evidencia estática:** el plan de M21, S1 y S7. La evidencia local de la auditoría del plan (2026-09-30) cargó el driver con un `URLClassLoader` propio.
- **Evidencia en servidor:** Pendiente (sesión 21, paso 21.1).
- **Decisión:** Pendiente. Severidad baja.
- **Consecuencia:** hasta medir, la guía no afirma que el servidor real arranque sin el aviso.
- **Alternativa si no se confirma:** dejar `storage.aggregates: off`. Los registros JSONL no dependen del driver.
- **Módulos afectados:** M21.
- **Estado:** Abierta, severidad baja.

### DT-52. Volumen y coste reales de los registros

- **Pregunta:** bytes por hora, descartes y `tick_work` con `records.level: decisions` frente a `off`, con 5 jefes.
- **Contexto:** M21.0 y M21.3. La estimación del plan es ≈ 47 MB/h sin comprimir con 5 jefes a plena carga y `bodies: hash`, y ≤ 15 µs por tick en el hilo del servidor.
- **Evidencia estática:** presupuestos de M21.1. La medida local de `m21Measure` no sustituye el servidor.
- **Evidencia en servidor:** Pendiente (sesión 21, paso 21.2).
- **Decisión:** Pendiente. El default sigue en `decisions`.
- **Consecuencia:** la guía marca el volumen en juego como pendiente de servidor.
- **Alternativa si no se confirma:** bajar `records.level` a `off` si el p99 medido en local de `tap.verdicts` pasa de 20 µs, o si el servidor muestra una diferencia media de `tick_work` mayor.
- **Módulos afectados:** M21.
- **Estado:** Abierta.

### DT-53. Repetición de cuerpos y valor de la caché de respuestas

- **Pregunta:** tasa de aciertos con la vigencia de 100 ticks y concordancia en sombra por edad.
- **Contexto:** M21.7. La mejor táctica cambia entre repeticiones del mismo cuerpo en el 0,84–1,67 % de los casos (K3 del plan). La tasa de aciertos en combate es desconocida. La caché se entrega en `off`.
- **Evidencia estática:** K3 del plan de M21. `recordsBodyRepeat` puede contar `body_sha256` sin clave, a posteriori.
- **Evidencia en servidor:** Pendiente (sesión 21, paso 21.5), y solo con la aprobación del usuario.
- **Decisión:** Pendiente. `decision.answerCache` sigue en `off`.
- **Consecuencia:** no se cruza la puerta que pasaría la caché a `on`.
- **Alternativa si no se confirma:** dejar la caché en `off`. La sombra no ahorra llamadas: siempre envía.
- **Módulos afectados:** M21.
- **Estado:** Abierta.

### DT-54. Simulador frente al servidor

- **Pregunta:** el controlador y la escalera medidos en el simulador, ¿se parecen al servidor?
- **Contexto:** M22.8 y M22.10. La sesión 22.5 compara una pelea corta con el informe local.
- **Evidencia estática:** `DifficultyControlSimTest` con los defaults, sin retocarlos.
- **Evidencia en servidor:** Pendiente (sesión 22, paso 22.5).
- **Decisión:** Pendiente.
- **Consecuencia:** la guía no afirma que el servidor reproduzca el simulador.
- **Alternativa si no se confirma:** dejar la escalera v1 sin calibrar.
- **Módulos afectados:** M22.
- **Estado:** Abierta.

### DT-55. Justicia percibida

- **Pregunta:** un jugador nota la bajada de presión como justa.
- **Contexto:** M22.7 y M22.13. Los pasos 22.1 y 22.2 miran el ritmo, no el daño.
- **Evidencia estática:** los cuerpos entre peldaños no cambian (`FairnessInvariantsTest`).
- **Evidencia en servidor:** Pendiente (sesión 22, pasos 22.1 y 22.2).
- **Decisión:** Pendiente.
- **Consecuencia:** no se publica `ladder-v2.json`.
- **Alternativa si no se confirma:** seguir con la tabla v1.
- **Módulos afectados:** M22.
- **Estado:** Abierta.

### DT-56. Variable en vivo

- **Pregunta:** `predictor_target_seconds` y `predictor_difficulty` cambian el ajuste siguiente sin reiniciar el reloj.
- **Contexto:** M22.9. La sintaxis de MythicMobs está leída en estático.
- **Evidencia estática:** `TargetOverrideTest` sobre el puerto `CombatReader`.
- **Evidencia en servidor:** Pendiente (sesión 22, paso 22.3).
- **Decisión:** Pendiente.
- **Consecuencia:** la guía marca el ejemplo de fases como pendiente de servidor.
- **Alternativa si no se confirma:** el preset del archivo sigue mandando.
- **Módulos afectados:** M22.
- **Estado:** Abierta.

### DT-57. Daño de Difícil frente a Normal

- **Pregunta:** la razón de daño Difícil/Normal ronda 1,5, y en `MAGIC` ronda 1,0.
- **Contexto:** M22.13, paso 22.4. El plugin no escribe el daño.
- **Evidencia estática:** los presets no tocan el daño de la skill.
- **Evidencia en servidor:** Pendiente (sesión 22, paso 22.4).
- **Decisión:** Pendiente. El default no se cambia para forzar la razón.
- **Consecuencia:** no hay una clave de daño en `difficulty`.
- **Alternativa si no se confirma:** dejar el daño al archivo de MythicMobs.
- **Módulos afectados:** M22.
- **Estado:** Abierta.

### DT-58. Calibración con Jev real

- **Pregunta:** la ECE del brazo D en vivo se parece a la del banco (0,205).
- **Contexto:** M22.6, paso 22.6. Es una evaluación de pago y no se ejecuta en esta entrega.
- **Evidencia estática:** `EvalCalibrationBaselineTest` sobre `answers.jsonl` ya grabado.
- **Evidencia en servidor:** Pendiente. Hace falta la aprobación del usuario.
- **Decisión:** Pendiente. No se recalibra.
- **Consecuencia:** M22 mide y no cambia umbrales.
- **Alternativa si no se confirma:** seguir con la línea base del banco.
- **Módulos afectados:** M22.
- **Estado:** Abierta.

### DT-59. Interrupción nativa de un cast de ThePredict

- **Pregunta:** con `same_tick` y con `mythic_default`, ¿poner a verdadero el `AtomicBoolean` fijado con `setTerminatedReference` detiene lo que queda tras el siguiente `delay`? ¿Qué sigue: `repeat=`, proyectiles, auras, `terminable` anidado?
- **Contexto:** M23.7, paso 23.3. `SkillMetadata.terminate`, `isTerminated` y `setTerminatedReference` existen. `MetaSkill` comprueba la terminación en cada paso. Las mecánicas instantáneas corren en la misma llamada, así que el corte se ve tras un `delay`.
- **Evidencia estática:** `javap` de MythicMobs Premium 5.13.1. Lo ya lanzado sigue.
- **Evidencia en servidor:** Pendiente (sesión 23, paso 23.3).
- **Decisión:** Pendiente.
- **Consecuencia:** el aviso se interrumpe en el libro de ThePredict; el corte nativo no se da por verificado.
- **Alternativa si no se confirma:** interrumpir solo antes de la liberación y recomendar `terminable` con `predictorstance{s=staggered}`.
- **Módulos afectados:** M23.
- **Estado:** Abierta.

### DT-60. Postura nativa, variable y vocabulario en vivo

- **Pregunta:** en `hybrid`, ¿`variableEquals{var=caster.predictor_stance;value=windup}` ve la escritura en la misma comprobación y distingue mayúsculas? En `write`, ¿`stance{s=predictor_windup}` ve la escritura en el mismo tick y el conflicto se detecta en ≤ 2 ticks? ¿La forma `<caster.stance>` es la correcta? ¿Cuánto cuesta cada escritura?
- **Contexto:** M23.8, paso 23.1. `ActiveMob.setStance` y `getStance` son públicos. `variableEquals` distingue mayúsculas.
- **Evidencia estática:** `javap` de `VariableRegistry.putString` y `ActiveMob.setStance`.
- **Evidencia en servidor:** Pendiente (sesión 23, paso 23.1).
- **Decisión:** Pendiente. Sin la variable, el vocabulario propio basta.
- **Consecuencia:** `hybrid` escribe `predictor_stance` en minúsculas y no llama a `setStance`.
- **Alternativa si no se confirma:** quedarse en las condiciones y placeholders de ThePredict.
- **Módulos afectados:** M23.
- **Estado:** Abierta, severidad baja.

### DT-61. Legibilidad de las señales con los manejadores del ejemplo

- **Pregunta:** con los manejadores del ejemplo, ¿el usuario identifica la respuesta en ≥ 4 de 5 casos con sonido y sin sonido? ¿Cuánto cuesta una señal con 20 jefes?
- **Contexto:** M23.5, pasos 23.4 y 23.7. ThePredict solo envía `PREDICTOR_TELL_<respuesta>`. Los efectos son del manejador del autor.
- **Evidencia estática:** ninguna llamada de sonido, partícula o gesto fuera de `swingMainHand` en `VANILLA_MELEE`.
- **Evidencia en servidor:** Pendiente (sesión 23).
- **Decisión:** Pendiente.
- **Consecuencia:** la guía no publica los manejadores como verificados.
- **Alternativa si no se confirma:** el autor escribe su propio `~onSignal`.
- **Módulos afectados:** M23.
- **Estado:** Abierta.

### DT-62. Mínimos de aviso y justicia percibida con jugadores reales

- **Pregunta:** ¿la tasa de esquiva tras un `SEVERE` supera 0,8 en todas las respuestas? ¿La mediana de «se veían venir» es ≥ 5 de 7? ¿Algún golpe injusto? ¿La recuperación deja castigar? ¿Cuál es la RTT real?
- **Contexto:** M23.4, paso 23.5. El modelo local v2 usa bases 0/8/16/24 más respuesta y margen.
- **Evidencia estática:** `TellResponseModel` no está publicado como medición de jugadores reales.
- **Evidencia en servidor:** Pendiente (sesión 23, paso 23.5).
- **Decisión:** Pendiente. Si falla, se sube el margen o los términos por respuesta. Las bases no bajan y no hay margen por ping.
- **Consecuencia:** los mínimos locales no se presentan como justicia medida en servidor.
- **Alternativa si no se confirma:** subir el margen de entrega.
- **Módulos afectados:** M23.
- **Estado:** Abierta.

### DT-63. Gestos nativos por especie

- **Pregunta:** retirada. Decisiones 9 y 10 del usuario: ThePredict no usa gestos.
- **Contexto:** M23.5. No hay kit de aviso por defecto.
- **Evidencia estática:** no hay llamadas de gesto propias.
- **Evidencia en servidor:** No aplica.
- **Decisión:** Retirada.
- **Consecuencia:** el autor pone los efectos en sus `~onSignal`.
- **Alternativa si no se confirma:** no aplica.
- **Módulos afectados:** M23.
- **Estado:** Retirada.

### DT-64. Coste real de la zona en el servidor

- **Pregunta:** ¿cuántos µs cuesta una instantánea por chunk, una columna con `World.getType` y una zona de radio 24? ¿Qué `tick_work` p99 y qué memoria dan 10 encuentros? ¿ThePredict carga algún chunk?
- **Contexto:** M24.0 y M24.2, pasos 24.1 y 24.7. La medida local usa rejillas sintéticas.
- **Evidencia estática:** `ZoneSnapshotter` solo llama a `getChunkAt` tras `isChunkLoaded`. No hay `getChunkAtAsync`.
- **Evidencia en servidor:** Pendiente (sesión 24, pasos 24.1 y 24.7).
- **Decisión:** Pendiente.
- **Consecuencia:** los presupuestos publicados son los de la medida local. El default no sube a `guard`.
- **Alternativa si no se confirma:** bajar `maxSnapshotsPerTick`, el radio o, al final, el radio a 16.
- **Módulos afectados:** M24.
- **Estado:** Abierta.

### DT-65. Cambios de bloque sin evento

- **Pregunta:** ¿qué caminos no lanzan evento (`/fill`, `setblock` de MythicMobs, WorldEdit, `setType` de otros plugins)? ¿Cuántas columnas corrige la verificación?
- **Contexto:** M24.3, paso 24.2. `BlockPhysicsEvent` no se escucha.
- **Evidencia estática:** la lista de eventos de `ZoneBlockWatch`.
- **Evidencia en servidor:** Pendiente (sesión 24, paso 24.2).
- **Decisión:** Pendiente.
- **Consecuencia:** la verificación periódica sigue en 200 ticks.
- **Alternativa si no se confirma:** bajar `verifyIntervalTicks`.
- **Módulos afectados:** M24.
- **Estado:** Abierta, severidad media.

### DT-66. Navegación nativa junto a peligros

- **Pregunta:** ¿un mob cruza nieve polvo, cactus, magma o fuego? ¿Un jefe de 200 PV salta un precipicio de 20 bloques? ¿La sintaxis `AIPathfindingMalus` cambia la ruta?
- **Contexto:** M24.5, paso 24.3. ThePredict no llama a `Mob.setPathfindingMalus`.
- **Evidencia estática:** ninguna llamada a `setPathfindingMalus`.
- **Evidencia en servidor:** Pendiente (sesión 24, paso 24.3).
- **Decisión:** Pendiente.
- **Consecuencia:** la guía marca `AIPathfindingMalus` como pendiente de servidor.
- **Alternativa si no se confirma:** la zona no escribe malus.
- **Módulos afectados:** M24.
- **Estado:** Abierta.

### DT-67. Distancia real de empuje

- **Pregunta:** ¿cuánto desplaza un golpe, con y sin resistencia? ¿El `damage` de MythicMobs lanza `EntityKnockbackByEntityEvent` con el jefe como causante? ¿La atribución cubre `throw`?
- **Contexto:** M24.6 y M24.10, paso 24.4. El evento es `com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent`.
- **Evidencia estática:** el listener usa ese tipo, no el de `org.bukkit`. La física local usa 1,55 bloques y `floor(caída − 3)`.
- **Evidencia en servidor:** Pendiente (sesión 24, paso 24.4).
- **Decisión:** Pendiente.
- **Consecuencia:** la atribución por evento queda preparada y sin confirmar en vivo.
- **Alternativa si no se confirma:** atribución solo por cast.
- **Módulos afectados:** M24.
- **Estado:** Abierta.

### DT-68. Vocabulario de zona en vivo

- **Pregunta:** ¿`predictorzone{fact=edge_behind}` responde en un segundo o menos? ¿Los hechos cambian como mucho una vez cada dos segundos? ¿La sintaxis de contexto nativo es la de la guía?
- **Contexto:** M24.7, paso 24.6. La histéresis local es 10 y 40 ticks.
- **Evidencia estática:** `PredictorZoneCondition` y los placeholders leen `IntentBoard`.
- **Evidencia en servidor:** Pendiente (sesión 24, paso 24.6).
- **Decisión:** Pendiente.
- **Consecuencia:** la guía no da la sintaxis nativa por verificada.
- **Alternativa si no se confirma:** el autor usa solo el vocabulario de ThePredict.
- **Módulos afectados:** M24.
- **Estado:** Abierta, severidad baja.

### DT-69. Coste del paso de grupo

- **Pregunta:** ¿el paso `group` cabe en el presupuesto con 1, 5 y 10 encuentros de 5, y con 10 encuentros de 8?
- **Contexto:** M25.2, pasos 25.1 y 25.7.
- **Evidencia estática:** el paso existe en el coordinador y el presupuesto por tick es `group.maxMicrosPerTick` (default 150).
- **Evidencia en servidor:** Pendiente (sesión 25, pasos 25.1 y 25.7).
- **Decisión:** Pendiente.
- **Consecuencia:** el default sigue en `fair`.
- **Alternativa si no se confirma:** bajar `maxReplansPerTick` o `maxMicrosPerTick` dentro de su rango.
- **Módulos afectados:** M25.
- **Estado:** Abierta.

### DT-70. Escuadra, líder y alerta nativa

- **Pregunta:** ¿`sip`/`sio`, `itt` y `ctt` forman la escuadra y el grupo de tabla que `inspect` muestra? ¿`break` y `succeed` se distinguen al morir el jefe?
- **Contexto:** M25.1, paso 25.2. Perfil `jefe_invocador`.
- **Evidencia estática:** `GroupSettings` lee `Squad`, `LeaderPriority` y `OnLeaderDeath`.
- **Evidencia en servidor:** Pendiente (sesión 25, paso 25.2).
- **Decisión:** Pendiente.
- **Consecuencia:** la lectura de relaciones nativas no se da por comprobada.
- **Alternativa si no se confirma:** escuadra solo por el nombre declarado.
- **Módulos afectados:** M25.
- **Estado:** Abierta.

### DT-71. Justicia de grupo percibida

- **Pregunta:** ¿dos atacantes y 6 ticks se perciben como ritmo o como espera? ¿Un aviso grande a la vez se percibe justo?
- **Contexto:** M25.3 y M25.7, pasos 25.3 y 25.4.
- **Evidencia estática:** topes default 2 y separación 6, con suelo de 4 ticks.
- **Evidencia en servidor:** Pendiente (sesión 25, pasos 25.3 y 25.4).
- **Decisión:** Pendiente.
- **Consecuencia:** no se ajustan los topes por una opinión que todavía no existe.
- **Alternativa si no se confirma:** mover `minImpactGapTicks` y `bigImpactSpacingTicks` dentro de A13.
- **Módulos afectados:** M25.
- **Estado:** Abierta.

### DT-72. Huecos de cerco

- **Pregunta:** ¿cinco mobs salen del cerco por detrás sin golpes desde el sector libre, en campo abierto y en un pasillo?
- **Contexto:** M25.5, paso 25.5. La separación por debajo de 0,6 bloques es NO VERIFICADO.
- **Evidencia estática:** `SurroundSlots` existe. El director publica el hueco como -1 en esta entrega local.
- **Evidencia en servidor:** Pendiente (sesión 25, paso 25.5).
- **Decisión:** Pendiente.
- **Consecuencia:** el sector de salida no está confirmado en juego.
- **Alternativa si no se confirma:** histéresis mayor o sin sector en pasillos.
- **Módulos afectados:** M25.
- **Estado:** Abierta.

### DT-73. Vocabulario de grupo en vivo

- **Pregunta:** ¿`predictorrole`, `predictorplan` y `predictortoken` responden en un segundo o menos? ¿Cuántos tokens reales gasta `plan_and_leader`?
- **Contexto:** M25.10, pasos 25.6 y 25.8. 25.8 es de pago y no se ejecuta sin aprobación.
- **Evidencia estática:** las condiciones y los placeholders leen `IntentBoard`. La estimación publicada es 3114 tokens.
- **Evidencia en servidor:** Pendiente (sesión 25, paso 25.6). 25.8 no ejecutada.
- **Decisión:** Pendiente.
- **Consecuencia:** la guía marca la sintaxis como pendiente de servidor.
- **Alternativa si no se confirma:** el autor no usa el vocabulario de grupo hasta medirlo.
- **Módulos afectados:** M25.
- **Estado:** Abierta.

### DT-74. Coste real de la observación de patrones

- **Pregunta:** ¿cuántos µs cuesta `enemyKinematics` en el servidor y cuánto cuesta el paso `patterns` con 10 encuentros de 16 participantes?
- **Contexto:** M26.0, paso 26.6. En local, un `EnemyModel` con 32 contextos mide 4080 bytes de arrays y 5568 bytes estimados (cabecera de 48 bytes; no es JOL).
- **Evidencia estática:** `M26Measurements` publica la tabla «Patterns (M26)». JOL no está en el classpath de `--offline`.
- **Evidencia en servidor:** Pendiente (sesión 26, paso 26.6).
- **Decisión:** Pendiente.
- **Consecuencia:** el presupuesto de servidor no está confirmado.
- **Alternativa si no se confirma:** `sampleTicks` a 4, `maxWatchedPerEncounter` a 2 y `maxTokensPerSecond` a 10.
- **Módulos afectados:** M26.
- **Estado:** Abierta.

### DT-75. Clasificación de golpes y del rodeo en vivo

- **Pregunta:** ¿los tokens de golpe coinciden con lo que se oye, y el rodeo se distingue de girar la cámara?
- **Contexto:** M26.1, paso 26.1. En v1 solo cuentan los golpes que impactan a un miembro. `patterns.airSwings` sigue en `observe`.
- **Evidencia estática:** `StrikeClassifier` y `ArmSwingNoiseTest`. `EnemyWatch` no está registrado: la sesión 26.1 no se ha corrido.
- **Evidencia en servidor:** Pendiente (sesión 26, paso 26.1).
- **Decisión:** Pendiente.
- **Consecuencia:** `on` de los golpes al aire no se entrega.
- **Alternativa si no se confirma:** dejar `airSwings` en `observe` y subir `strafeBlocks` a 0,25.
- **Módulos afectados:** M26.
- **Estado:** Abierta.

### DT-76. Detección con jugadores reales

- **Pregunta:** ¿un guion escrito activa el patrón en 60 segundos o menos y lo pierde en 20 segundos o menos?
- **Contexto:** M26.3, pasos 26.3 y 26.4. Con λ = 1, q = 0,05, 8 ensayos y 3 colas, el prior Beta(2, 2) entra en el ensayo 9, no en el 8.
- **Evidencia estática:** `PatternStateMachineTest` con semilla 26. Falsos positivos a p = 0,5: 60 ensayos y 400 semillas, y 400 ensayos y 200 semillas, ambos ≤ 0,005. Detección a p = 0,8: 80 ensayos y 200 semillas, ≥ 0,9. CUSUM: 41 caminos, mediana ≥ 250.
- **Evidencia en servidor:** Pendiente (sesión 26, pasos 26.3 y 26.4).
- **Decisión:** Pendiente.
- **Consecuencia:** el resultado de puerta es `PASS_SIM_ONLY` hasta la sesión. No se tocan θ ni los defaults.
- **Alternativa si no se confirma:** q a 0,02 o `minTrials` a 12.
- **Módulos afectados:** M26.
- **Estado:** Abierta.

### DT-77. Justicia percibida de la explotación

- **Pregunta:** ¿la cuota del 50 % y el descanso se sienten como lectura o como trampa?
- **Contexto:** M26.6, paso 26.5. `EXPLOIT_PATTERN` no está armado por la escalera genérica. `observe` no sesga.
- **Evidencia estática:** `A14InvariantsTest` y `ExploitPolicyTest`. `FAIRNESS_VIOLATIONS` existe y el contador de la prueba local no registra violaciones.
- **Evidencia en servidor:** Pendiente (sesión 26, paso 26.5).
- **Decisión:** Pendiente.
- **Consecuencia:** `bias` no pasa a default.
- **Alternativa si no se confirma:** cuota 0,3, descanso 600 ticks y un solo patrón con sesgo.
- **Módulos afectados:** M26.
- **Estado:** Abierta.

### DT-78. Tokens reales de los hechos de patrón

- **Pregunta:** ¿cuántos tokens reales añaden 2 y 4 hechos?
- **Contexto:** M26.8, paso 26.7. Es de pago y no se ejecuta sin aprobación. La estimación local de `M26Measurements.tokens` cuenta caracteres de la frase divididos por 4.
- **Evidencia estática:** las frases 0–23 no llevan guion, dígitos ni el nombre del enum.
- **Evidencia en servidor:** Pendiente. 26.7 no ejecutada.
- **Decisión:** Pendiente.
- **Consecuencia:** la guía no afirma un costo medido con Jev.
- **Alternativa si no se confirma:** dejar `Patterns.Facts` en false para ese perfil.
- **Módulos afectados:** M26.
- **Estado:** Abierta.

### DT-79. Coste, tamaño y latencia reales de la memoria

- **Pregunta:** ¿qué porcentaje de inicios llega a `LOADED` antes de `PlayerJoinEvent`, cuánto tarda un lote de 64 sujetos y qué p99 tienen los registros tras una semana?
- **Contexto:** M27. El códec local mide 680 bytes en el peor caso y 713 saturado. La precarga en el servidor no se ha medido.
- **Evidencia estática:** `PlayerMemoryCodecTest`, `PlayerMemoryBoundsTest` y `V004MigrationTest`.
- **Evidencia en servidor:** Pendiente (sesión 27, pasos 27.1 y 27.2).
- **Decisión:** Pendiente.
- **Consecuencia:** la guía no afirma un p99 de jugadores reales.
- **Alternativa si no se confirma:** pedir la carga también en el primer objetivo del jugador y bajar `maxProfilesPerPlayer` a 6 si el p99 pasa de 600 bytes.
- **Módulos afectados:** M27.
- **Estado:** Abierta.

### DT-80. Valor de la memoria con jugadores reales

- **Pregunta:** ¿un guion repetido activa el patrón antes en la segunda y la tercera pelea, se suelta en dos peleas al cambiar, y el peldaño inicial acerca el tiempo de pelea al objetivo?
- **Contexto:** M27. El caso local no sustituye a jugadores reales. E6 sale de 27.3 y 27.4.
- **Evidencia estática:** `MinFightTrialsTest`, `SubjectDifficultyPriorsTest` y `MemoryEvalTest`.
- **Evidencia en servidor:** Pendiente (sesión 27, pasos 27.3 y 27.4).
- **Decisión:** Pendiente.
- **Consecuencia:** el modo entregado sigue en `record`. El veredicto de simulación es `PASS_SIM_ONLY` hasta esa sesión.
- **Alternativa si no se confirma:** `seedMaxTrials` a 4 y `crossFightH` a 3.
- **Módulos afectados:** M27.
- **Estado:** Abierta.

### DT-81. Justicia percibida de que el jefe recuerda

- **Pregunta:** ¿se siente como reconocimiento o como trampa?
- **Contexto:** M27. No hay vocabulario de memoria para el autor.
- **Evidencia estática:** la guía del autor no anuncia la memoria. `A15InvariantsTest`.
- **Evidencia en servidor:** Pendiente (sesión 27, pasos 27.3 y 27.5).
- **Decisión:** Pendiente.
- **Consecuencia:** no se anuncia en el juego.
- **Alternativa si no se confirma:** no sembrar un patrón explotado con éxito en la pelea anterior.
- **Módulos afectados:** M27.
- **Estado:** Abierta.

### DT-82. Red con MySQL o MariaDB

- **Pregunta:** ¿un jugador que salta entre dos servidores conserva sus peleas?
- **Contexto:** M27.10 está aplazado. Solo existe el valor reservado `memory.backend: mysql`.
- **Evidencia estática:** `MemorySettingsTest` rechaza ese valor con `no disponible en esta versión`.
- **Evidencia en servidor:** No se prepara la sesión 27.7.
- **Decisión:** Pendiente.
- **Consecuencia:** no hay conector ni claves `storage.mysql.*`.
- **Alternativa si no se confirma:** dejar SQLite por servidor.
- **Módulos afectados:** M27.
- **Estado:** Abierta. Se cierra con la implementación o se descarta si el usuario no pide la red.

### DT-83. Patente y nombres

- **Pregunta:** ¿la consulta legal confirma que el diseño neutro puede distribuirse?
- **Contexto:** US 10,926,179 B2. El usuario aceptó nombres neutros y no anunciar la memoria. Consultará a un abogado antes de cualquier distribución. Esto no es asesoramiento legal.
- **Evidencia estática:** no hay rangos, facciones ni el nombre "nemesis". `inspect` y `memory show` son de administrador.
- **Evidencia en servidor:** Pendiente el paso 27.5. La consulta legal sigue pendiente del usuario.
- **Decisión:** Decidida en el diseño. La consulta legal queda pendiente.
- **Consecuencia:** la guía del administrador avisa la consulta. La del autor no menciona la memoria.
- **Alternativa si no se confirma:** dejar la memoria solo para la dificultad y la narrativa, sin semilla de estilo.
- **Módulos afectados:** M27.
- **Estado:** Decidida en lo de diseño; consulta legal pendiente del usuario.

### DT-84. Tokens reales de los hechos de historia

- **Pregunta:** ¿cuántos tokens reales añaden una y dos frases de historia, y cambia la táctica?
- **Contexto:** M27. Estimación local de unas 15 tokens por frase. El paso 27.8 es de pago.
- **Evidencia estática:** cuatro frases fijas en `SemanticPhrases`. `JevMeasurements` publica la columna.
- **Evidencia en servidor:** Pendiente. 27.8 no ejecutada.
- **Decisión:** Pendiente.
- **Consecuencia:** `full` no es el modo entregado.
- **Alternativa si no se confirma:** `Memory.Facts: false`.
- **Módulos afectados:** M27.
- **Estado:** Abierta.

### DT-85. La confianza de Jev no es una propensión

- **Pregunta:** ¿Jev repite la opción y esa repetición sigue sin ser la propensión de ThePredict?
- **Contexto:** M28. X1 en el re-banco: 59 de 60 pares del brazo D eligen lo mismo.
- **Evidencia estática:** `JevRepeatabilityBaseline` sobre `answers.jsonl`. La sesión 28.1 no se ejecutó.
- **Evidencia en servidor:** Pendiente.
- **Decisión:** Pendiente.
- **Consecuencia:** contra jugadores la propensión registrada de la táctica ejecutada es 1.
- **Alternativa si no se confirma:** si Jev fuera estocástico, ThePredict seguiría ejecutando el máximo.
- **Módulos afectados:** M28.
- **Estado:** Abierta.

### DT-86. Recompensa r1 y crédito diferido

- **Pregunta:** ¿r1 correlaciona con ganar más que effect?
- **Contexto:** M28.2. La matriz de 200 semillas no se ejecutó en esta entrega.
- **Evidencia estática:** una escaramuza de semilla 28 repite su deathTick. Eso no es la alineación.
- **Evidencia en servidor:** Pendiente (28.1).
- **Decisión:** Pendiente.
- **Consecuencia:** r1 no se declara alineada.
- **Alternativa si no se confirma:** revisar el crédito antes de activar `learned`.
- **Módulos afectados:** M28.
- **Estado:** Abierta.

### DT-87. Exploración solo sin jugadores

- **Pregunta:** ¿la exploración queda fuera de las peleas con jugadores salvo `PredictorTraining: true`?
- **Contexto:** M28.5. Errata E28-2 aplicada. No hay comando, temporizador ni interruptor global.
- **Evidencia estática:** `TrainingEligibility` exige las dos claves. La sesión 28.2 no se ejecutó.
- **Evidencia en servidor:** Pendiente.
- **Decisión:** Pendiente.
- **Consecuencia:** el default no explora contra jugadores.
- **Alternativa si no se confirma:** dejar `learning.exploration.epsilon` en el simulador.
- **Módulos afectados:** M28.
- **Estado:** Abierta.

### DT-88. Método y datos de la calibración

- **Pregunta:** ¿Platt del brazo D sigue en ECE10 0,070 y Brier 0,112?
- **Contexto:** M28.4. X2 reproducido en Java dentro de ±0,002. La muestra es pequeña.
- **Evidencia estática:** `CalibrationFitBaseline`. La sesión 28.3 es de pago y no se ejecutó.
- **Evidencia en servidor:** Pendiente.
- **Decisión:** Pendiente.
- **Consecuencia:** el default del umbral sigue en `raw`.
- **Alternativa si no se confirma:** dejar `confidenceGate: raw`.
- **Módulos afectados:** M28.
- **Estado:** Abierta.

### DT-89. Veredicto mínimo para activar un valor aprendido

- **Pregunta:** ¿basta `PASS_SIM_ONLY` para `scope: bench` y hace falta G4 para `scope: all`?
- **Contexto:** M28.9. El ciclo completo del escritor no está cableado. Las sesiones 28.2 y 28.5 no se ejecutaron.
- **Evidencia estática:** `LearnedArtifacts` no activa sin veredicto que pase.
- **Evidencia en servidor:** Pendiente.
- **Decisión:** Pendiente.
- **Consecuencia:** sin artefacto activo, `learned` no reordena.
- **Alternativa si no se confirma:** exigir G4 también en `bench`.
- **Módulos afectados:** M28.
- **Estado:** Abierta.

### DT-90. Valor aprendido como pista para Jev

- **Pregunta:** ¿una pista cambia la tasa de aceptables o solo copia la tabla?
- **Contexto:** M28.7. `learning.jevHint` default `"off"`. No se envió ninguna pista.
- **Evidencia estática:** el default no añade la pista al cuerpo.
- **Evidencia en servidor:** Pendiente. Sería de pago.
- **Decisión:** Pendiente.
- **Consecuencia:** Jev no recibe el valor aprendido.
- **Alternativa si no se confirma:** dejar la pista apagada.
- **Módulos afectados:** M28.
- **Estado:** Abierta.

### DT-91. Omisión de Jev por valor de la información

- **Pregunta:** ¿qué fracción cumpliría las condiciones de omisión y con qué acuerdo?
- **Contexto:** M28.8. `learning.voiSkip` default `"off"`. La sombra no está medida en servidor.
- **Evidencia estática:** `VoiPolicy` en `shadow` no omite. La sesión 28.4 es de pago y no se ejecutó.
- **Evidencia en servidor:** Pendiente.
- **Decisión:** Pendiente.
- **Consecuencia:** toda captura sigue preguntando cuando el resto de la política lo pide.
- **Alternativa si no se confirma:** dejar `voiSkip: off`.
- **Módulos afectados:** M28.
- **Estado:** Abierta.

### DT-92. Señal de entrenamiento por resultado, sin imitar a Jev

- **Pregunta:** ¿cuánto pierde el estudiante sin imitar, y cierran esa diferencia el simulador y Kev?
- **Contexto:** M29.2. Decisión 1 del usuario (2026-09-30). Las salidas de Jev no son etiquetas.
- **Evidencia estática:** `ChoiceStatKey` solo admite `outcome`, `rollout` y `kev-*`. `distillTrain` rechaza `jev`. `verifyPluginJar` rechaza una fuente `jev` bajo `learn/packaged/`.
- **Evidencia en servidor:** Pendiente (sesión 29.2, de pago).
- **Decisión:** Decidida la señal. Abiertas las cifras.
- **Consecuencia:** el acuerdo con Jev es una métrica de sombra, no una etiqueta.
- **Alternativa si no se confirma:** más despliegues, Kev mayor, o quedarse en `assist`. Nunca volver a imitar a Jev sin permiso escrito de TypeSafe.
- **Módulos afectados:** M29.
- **Estado:** Decidida la señal; abiertas las cifras.

### DT-93. Representación híbrida

- **Pregunta:** ¿cuántas celdas finas alcanzan 8 ofertas, y se leen las hojas en `inspect`?
- **Contexto:** M29.1. Y1 local queda dentro de las bandas publicadas.
- **Evidencia estática:** `DistillReproducesY1Test` en verde con las bandas sin ampliar.
- **Evidencia en servidor:** Pendiente (sesión 29.2).
- **Decisión:** Pendiente.
- **Consecuencia:** la tabla cubre las celdas vistas y el árbol las demás.
- **Alternativa si no se confirma:** solo tabla, o un árbol más profundo.
- **Módulos afectados:** M29.
- **Estado:** Abierta.

### DT-94. Cobertura por celda fina

- **Pregunta:** ¿cuál es la curva real de cobertura y cae alguna prohibida en una celda cubierta?
- **Contexto:** M29.3. La cota es de resultado, no de acuerdo.
- **Evidencia estática:** `CoverageNonInferiorityTest`. Errata E29-1 aplicada.
- **Evidencia en servidor:** Pendiente (sesiones 29.2 y 29.3).
- **Decisión:** Pendiente.
- **Consecuencia:** sin `n` y sin cota, la celda no se delega.
- **Alternativa si no se confirma:** subir `minCellDecisions` o delegar por hoja con la misma prueba.
- **Módulos afectados:** M29.
- **Estado:** Abierta.

### DT-95. Regímenes por enemigos y fase

- **Pregunta:** ¿la partición encuentra un régimen con datos reales, y algún cambio coincide con un aviso?
- **Contexto:** M29.5. 40 ticks desde el evento. Cambios solo en capturas.
- **Evidencia estática:** `RegimeTelegraphTest` y `RegimeGroupTest`.
- **Evidencia en servidor:** Pendiente (sesiones 29.1 y 29.4).
- **Decisión:** Pendiente.
- **Consecuencia:** sin datos de dos regímenes se usa `r1:ALL`.
- **Alternativa si no se confirma:** un solo artefacto por perfil.
- **Módulos afectados:** M29.
- **Estado:** Abierta.

### DT-96. Degradación automática

- **Pregunta:** ¿cuántas falsas alarmas da el CUSUM en una semana?
- **Contexto:** M29.7. Bajar es automático. Subir solo con `distill mode`.
- **Evidencia estática:** `DistillGovernorTest` y `DistillControlCaptureTest`.
- **Evidencia en servidor:** Pendiente (sesión 29.5).
- **Decisión:** Pendiente.
- **Consecuencia:** `disable all` deja la captura siguiente en off.
- **Alternativa si no se confirma:** solo aviso, y degradación manual.
- **Módulos afectados:** M29.
- **Estado:** Abierta.

### DT-97. Autoconfiguración sin aplicación automática

- **Pregunta:** ¿cabe la búsqueda en 90 min con el ritmo medido, y pasa G1 la ganadora?
- **Contexto:** M29.8. `autoApply` solo `off`. Y4 local: el ganador redondea a 59 %.
- **Evidencia estática:** `SuccessiveHalvingTest`, `OverlayAuthorPinnedTest` y `AutoconfigAdminTest`. El ritmo 3,67 rondas/s del plan no es una medida. M22.0 midió 761,17 rondas/s.
- **Evidencia en servidor:** Pendiente (sesión 29.6). La búsqueda de 90 min no se ejecutó.
- **Decisión:** Pendiente.
- **Consecuencia:** ninguna superposición se aplica sola ni escribe MythicMobs.
- **Alternativa si no se confirma:** menos configuraciones iniciales.
- **Módulos afectados:** M29.
- **Estado:** Abierta.

### DT-98. Cinta del campeón

- **Pregunta:** ¿bastan 100 pares por rival para el GSPRT `NON_REGRESSION`?
- **Contexto:** M29.6. Coste 1,20 USD (4×100×2×0,0015) o 4,80 USD (8×200×2×0,0015). `evalTape` rechaza `-PliveJev=true`.
- **Evidencia estática:** `JevChampionTapeTest` y `TapeIsolationTest`. `autonomous` no se activa.
- **Evidencia en servidor:** Pendiente. La cinta no se grabó.
- **Decisión:** Pendiente.
- **Consecuencia:** el veredicto local deja `autonomous` en `assist`.
- **Alternativa si no se confirma:** más pares, o quedarse en `assist`.
- **Módulos afectados:** M29.
- **Estado:** Abierta.

### DT-99. Kev como proveedor experimental

- **Pregunta:** ¿qué aceptables, prohibidas, calibración y latencia da Kev-0.8B con los cuerpos reales?
- **Contexto:** M29.9. Las cifras K2–K4 son del proveedor, no de ThePredict.
- **Evidencia estática:** `KevProviderKeyTest`. Con `kev` no se lee `TYPESAFE_API_KEY`.
- **Evidencia en servidor:** Pendiente (sesión 29.7). Kev no se levantó.
- **Decisión:** Pendiente.
- **Consecuencia:** ningún default depende de Kev.
- **Alternativa si no se confirma:** dejar Kev documentado como no apto.
- **Módulos afectados:** M29.
- **Estado:** Abierta.

### DT-100. MythicMobs ignora la carpeta AIConfig

- **Pregunta:** MythicMobs ignora la carpeta `AIConfig` al cargar y con `/mm reload`.
- **Contexto:** P-AIConfig. La configuración humana vive en `plugins/MythicMobs/AIConfig/` (`config.yml` y `Profiles/`). ThePredict la crea solo si falta.
- **Evidencia estática:** `javap` del enum `DataFolder` de MythicMobs Premium 5.13.1 lista `ARCHETYPES`, `CAMERA`, `CONFIG`, `DATA`, `DIALOGS`, `DROP`, `ENCHANTMENTS`, `FILES`, `ITEMS`, `MENUS`, `MOBS`, `MODELS`, `MONO`, `RANDOM`, `SCHEMATICS`, `SKILLS` y `SPAWNERS`, más los packs. `AIConfig` no está. Consolidado §2.7 (decisión del usuario, 2026-10-02).
- **Evidencia en servidor:** Pendiente (sesión AIConfig, paso AIConfig.2).
- **Decisión:** Pendiente.
- **Consecuencia:** hasta medirlo, la guía no afirma que el servidor real ignore la carpeta.
- **Alternativa si no se confirma:** no escribir en la carpeta de MythicMobs y volver a proponer otra ubicación al usuario. Esta entrega no cambia de carpeta por sí sola.
- **Módulos afectados:** P-AIConfig. M22–M29 ya nombran `AIConfig/`.
- **Estado:** Abierta.
