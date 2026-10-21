# Checklist: qué DT y qué casilla cierra cada paso

> Paquete de pruebas ([índice](README.md)). Una casilla se marca y una entrada DT cambia de estado solo con la evidencia literal pegada (regla W2 y "Cómo se usa" del [registro](../M01-Registro-de-decisiones.md)). Los números de paso son los de la [guía por sesiones](Guia-de-pruebas-por-sesiones.md); entre paréntesis, el paso del guion de origen.

## Entradas del registro de decisiones

| DT | Pregunta | Pasos del paquete |
|---|---|---|
| DT-00 | Entorno, versiones y huellas | 1.1–1.9, 2.1, 2.5 |
| DT-01 | Condiciones del mob en cada meta condicional | 4.9 (M01 5.1, 5.7, 5.9), 6.5 |
| DT-02 | Registro de extensiones en primer arranque y recarga | 1.5, 2.1, 3.2, 3.6 |
| DT-03 | Registro por eventos de carga | 1.5, 2.1, 3.6 |
| DT-04 | Restauración con `reapplyAIGoalSelectors` | 4.5, 6.11, 7.8 |
| DT-05 | Instalación y reinstalación de metas | 4.1–4.5, 6.5, 7.2 |
| DT-06 | Clave de las metas de MythicMobs | 3.1, 4.5 |
| DT-07 | Rangos y forma de limitar la huida | 4.9 (M01 5.1–5.6, 5.8), 6.7–6.9 |
| DT-08 | Metas de Paper `hold`, `flank`, `owner` y cesión | 4.10–4.13, 6.10, 9.9 |
| DT-09 | Hilo de evaluación de condiciones | 4.14 |
| DT-10 | `Options.AsyncPathfinding` | 4.14 |
| DT-11 | `~onSignal` y nombres de placeholders | 3.2, 3.3, 3.7, 3.10, 5.1, 9.2 |
| DT-12 | Trigger `THEPREDICT` | 5.2–5.4, 9.3 |
| DT-13 | API de registro de placeholders | 2.2, 3.7 |
| DT-14 | Mecánica `predictordecide` | 2.4, 3.12 |
| DT-15 | `clear` frente a metas de Paper y marcador | 4.4, 4.13, 6.12 |
| DT-16 | Línea de meta sin prioridad | 4.7 |
| DT-17 | Permiso de depuración y consola | 2.3, 3.4 |
| DT-18 | Costo de instalación | 4.6, 6.13, 11.4 |
| DT-19 | Especies con `Brain` | 4.8, 6.14 |
| DT-20 | Eliminaciones, descargas y sesiones suspendidas | 7.3, 7.5, 13 |
| DT-21 | Cambio de mundo | 7.6 |
| DT-22 | Invisibilidad por efecto | 8.4 |
| DT-23 | Cooldown de la perla | 8.5 |
| DT-24 | Condiciones de contexto con `evaluateEntity` | 8.8 |
| DT-25 | Duración del combate | 8.9 |
| DT-26 | Golpe vanilla, invulnerabilidad y alcance | 9.3, 9.4 |
| DT-27 | Amenaza enriquecida en la tabla nativa | 10.1–10.7 |
| DT-28 | Skill de una táctica renovada por Jev (igualdad de daño; desde el 2026-09-30 sale al ritmo de su `Cooldown` en los dos modos) | 9.3, 12.1, K5.1 |
| DT-29 | Comandos de spark | 11.1 |
| DT-30 | Errores reales de System One | Resuelta con la cuenta real (no necesita servidor) |
| DT-31 | **Resuelta** (2026-09-30) con el re-banco de pago de M16.10 y la regla de M16.7: `minimumConfidence` 0,45 y `questionDetail` `full`; `tacticSwitchMargin` 0,15 se conserva. Lo que queda, `timeoutMillis` desde el host, es del guion §6.5 | Hecho fuera del servidor ([M16.10](../M16-Efectividad-de-las-respuestas-de-Jev.md#resultado-del-re-banco-m1610-2026-09-30)); K3.2–K3.4 para `timeoutMillis` |
| DT-32 | Dónde se declara el propósito de una skill (resuelta; ampliada por M19: el perfil sigue pudiendo anular, y la skill puede llevar `Intention`) | 15.1 (histórico, con `Purpose` en el perfil) y 19.1 (origen `skill`, sin `Purpose` en el perfil) |
| DT-33 | Escudo frente a daño de área de MythicMobs | 15.5 |
| DT-34 | `Cooldown` estático y con placeholder | 15.1, 15.1b |
| DT-41 | Radio de los targeters de MythicMobs: posiciones y comparación estricta | 15.3 |
| DT-35 | Atribución del daño de skills de MythicMobs (`damage` notificado con el lanzador como causante) | 16.1.b, 16.1.d |
| DT-36 | Tendencia de una entidad por su velocidad (`getVelocity()` de un mob que camina) | 17.2 |
| DT-37 | Alcance cuerpo a cuerpo vanilla de un mob (javap: caja inflada ≈ 0,83 bloques solo en horizontal, cada eje por separado, o el `ATTACK_RANGE` del arma) | 17.2 |
| DT-38 | Escudo de un no jugador (objeto activo con `BLOCKS_ATTACKS` y su retardo redondeado como vanilla). Levantar el escudo exige una configuración propia (`startUsingItem`): ni vanilla ni MythicMobs 5.13.1 lo hacen | 17.5 |
| DT-39 | Etiquetas de tipos tras `/minecraft:reload` (lobo `TP_TagWatcher`, que solo admite `tag:mipack:presas`) | 17.2b |
| DT-40 | Paquete de duelo: herencia de `Template` (`Skills` y `AIGoalSelectors`), `glow` durante toda la ronda, sin refuerzos de zombi con `spawn_mobs false` en difícil, eventos `MythicSkillEvent` por cast de cada regla clásica (conteo de `casts.csv` frente a partículas y daño), y (e) que el golpe vanilla con `Damage: 0` no da empuje ni invulnerabilidad asimétricos; (f) resuelta con javap: `healthpercent{p=<N%}` | 18.2, 18.3 |
| DT-42 | Opción del motor: golpes vanilla en alcance durante `APPROACH`/`HOLD` sin consumir la choice | Descartada por decisión del usuario (2026-09-30): el daño cuerpo a cuerpo lo dispara una skill; ThePredict no da golpes vanilla automáticos (no se prueba en servidor) |
| DT-43 | ¿MythicMobs 5.13.1 conserva la clave `Intention` sin quejarse? | 19.6. Abierta |
| DT-44 | ¿Se reinicia el cooldown en curso de una skill tras `/mm reload`? | 19.3. Abierta |
| DT-45 | ¿`getConfigLine()` y `getLine()` devuelven la línea con sus llaves y su targeter? | 19.1. Abierta |
| DT-46 | ¿Qué texto devuelve `getTypeName()` para `targetwithin`, `delay` y `effect:particles`? | 19.1. Abierta |
| DT-47 | Con tabla de amenaza, ¿con qué frecuencia el líder no es `Mob.getTarget()`, y cuánto espera una skill con `targetwithin`? | 19.7. Abierta |
| DT-48 | Tick del impacto de un cast con `delay`, con `same_tick` y con `mythic_default`, servidor sano y en retraso. Severidad baja | 20.2. Abierta |
| DT-49 | ¿Los mobs de MythicMobs usan la navegación asíncrona y cuántos µs cuesta `Pathfinder.moveTo` de una ruta corta? | 20.1. Abierta |
| DT-50 | Retraso entre publicar la vista y el `start()` de `MicroGoal` (se esperan 0 a 2 ticks) | 20.1. Abierta |
| DT-51 | Driver SQLite y acceso nativo en el servidor (sin aviso JEP 472; SQLite 3.49.1, JSONB y WAL) | 21.1. Abierta, severidad baja |
| DT-52 | Volumen y coste reales de los registros (`decisions` frente a `off`) | 21.2. Abierta |
| DT-53 | Repetición de cuerpos y valor de la caché de respuestas | 21.5. Abierta |
| DT-59 | ¿`setTerminatedReference` detiene lo que queda tras el siguiente `delay`? | 23.3. Abierta |
| DT-60 | Variable `predictor_stance`, `setStance` y placeholders en vivo | 23.1. Abierta, severidad baja |
| DT-61 | Legibilidad de `PREDICTOR_TELL_*` con los manejadores del ejemplo | 23.4 y 23.7. Abierta |
| DT-62 | Mínimos de aviso y justicia percibida con jugadores reales | 23.5. Abierta |
| DT-63 | Gestos nativos por especie | Retirada (decisiones 9 y 10: ThePredict no usa gestos) |
| DT-64 | Coste real de la zona y si ThePredict carga chunks | 24.1 y 24.7. Abierta |
| DT-65 | Cambios de bloque sin evento (`/fill`, WorldEdit, `setblock`) | 24.2. Abierta |
| DT-66 | Navegación nativa junto a peligros y `AIPathfindingMalus` | 24.3. Abierta |
| DT-67 | Distancia real de empuje y `EntityKnockbackByEntityEvent` | 24.4. Abierta |
| DT-68 | `predictorzone` y estabilidad de los hechos | 24.6. Abierta |
| DT-69 | Coste del paso de grupo | 25.1 y 25.7. Abierta |
| DT-70 | Escuadra, líder y alerta nativa | 25.2. Abierta |
| DT-71 | Justicia percibida del grupo | 25.3 y 25.4. Abierta |
| DT-72 | Huecos de cerco y sector de salida | 25.5. Abierta |
| DT-73 | Vocabulario de grupo y tamaño de `g0` | 25.6. 25.8 solo con aprobación. Abierta |
| DT-74 | Coste real de la observación de patrones | 26.6. Abierta |
| DT-75 | Clasificación de golpes y del rodeo en vivo | 26.1. Abierta |
| DT-76 | Detección con jugadores reales | 26.3 y 26.4. Abierta |
| DT-77 | Justicia percibida de la explotación | 26.5. Abierta |
| DT-78 | Tokens reales de los hechos de patrón | 26.7 solo con aprobación. Abierta |
| DT-79 | Coste, tamaño y latencia reales de la memoria | 27.1 y 27.2. Abierta |
| DT-80 | Valor de la memoria con jugadores reales | 27.3 y 27.4. Abierta |
| DT-81 | Justicia percibida de que el jefe recuerda | 27.3 y 27.5. Abierta |
| DT-82 | Red con MySQL o MariaDB | No se prepara. Abierta; se descarta si no se pide la red |
| DT-83 | Patente y nombres | Decidida en el diseño. Consulta legal pendiente del usuario. 27.5 |
| DT-84 | Tokens reales de los hechos de historia | 27.8 solo con aprobación. Abierta |
| DT-85 | La confianza de Jev no es una propensión | 28.1. Abierta |
| DT-86 | Recompensa r1 y crédito diferido | 28.1. Abierta. La matriz local no se ejecutó |
| DT-87 | Exploración solo sin jugadores, salvo PredictorTraining | 28.2. Abierta |
| DT-88 | Método y datos de la calibración | 28.3 solo con aprobación. Abierta |
| DT-89 | Veredicto mínimo para activar un valor aprendido | 28.2 y 28.5. Abierta |
| DT-90 | Valor aprendido como pista para Jev | De pago. Abierta |
| DT-91 | Omisión de Jev por valor de la información | 28.4 solo con aprobación. Abierta |
| DT-92 | Señal de entrenamiento por resultado, sin imitar a Jev | 29.2 solo con aprobación. Decidida la señal; abiertas las cifras |
| DT-93 | Representación híbrida | 29.2 solo con aprobación. Abierta |
| DT-94 | Cobertura por celda fina y no inferioridad en resultado | 29.2 y 29.3. Abierta |
| DT-95 | Regímenes por enemigos y fase, con histéresis | 29.1 y 29.4. Abierta |
| DT-96 | Degradación automática de un solo sentido | 29.5. Abierta |
| DT-97 | Autoconfiguración por reducción sucesiva, sin aplicación automática | 29.6. Abierta |
| DT-98 | No inferioridad frente a Jev con una cinta del campeón | Abierta hasta que el usuario grabe la cinta |
| DT-99 | Kev como proveedor experimental | 29.7. Abierta. No se levanta Kev en la preparación |
| DT-100 | MythicMobs ignora la carpeta `AIConfig` al cargar y con `/mm reload` | AIConfig.2. Abierta |

## Casillas de aceptación por módulo

| Módulo y subpunto | Casilla | Pasos |
|---|---|---|
| M01.1 | Carpeta fuera del repositorio con los tres JAR y el pack; `versions.md`; EULA; dos arranques limpios | 1.1–1.4, 1.9, 2.1, 2.5 |
| M01.2 | Sin permiso, error sin cambios; `intent`/`release` cambian `predictormove`; `goals` muestra claves y tipos | 2.3; 3.2–3.3; 3.1 |
| M01.3 | Resolución en primer arranque; `recheckForPlaceholders()`; mayúsculas; eventos de carga | 2.1, 3.6; 3.7; 3.2; 2.1, 3.6 |
| M01.4 | Casos 1–6; claves del caso 7; costo del caso 8 | 4.1–4.5; 4.5; 4.6 |
| M01.5 | Significado de parámetros; huida limitada al objetivo; banda sin oscilación | 4.9 |
| M01.6 | `HOLD` quieto; ninguna meta base; cesión en ≤2 ticks (en servidor solo la primera lectura posible); tipos de `owner` | 4.10; 4.10; 4.11, 9.9; 4.13 |
| M01.7 | Hilo de cada contexto; `AsyncPathfinding` | 4.14 |
| M01.8 | Formato de señal; placeholders; trigger `THEPREDICT` | 5.1; 3.7; 5.2–5.4 |
| M01.9 y Terminado | Todas las filas con entrada y estado | Al final de la fase 1 |
| M03.3 | Condición desconocida detectada en MythicMobs real | 6.18 |
| M03 (pendientes de servidor) | Recarga con perfil roto; `getEntityTypeString` de especies con `Brain`; `ThreatTable.DecayUnreachable` leído; primer arranque limpio | 6.19; 4.8, 6.14; 10.5; 1.5–1.8 |
| M04.4 | Placeholders estables; tras `/mm reload` | 3.7 |
| M04.5 | Sin errores de condición desconocida; cualquier capitalización | 2.1, 3.6; 3.2 |
| M04.6 | Sin señales repetidas al renovar | 9.2 |
| M04.7 y Terminado | Cada ejemplo de vocabulario con prueba en servidor | 3.8–3.13 |
| M05.2 | Instalar dos veces no cambia metas; revisión nueva sin duplicar; error de instalación con causa | 4.4, 6.12; 4.4, 6.12; 6.17 |
| M05.4 | Chunk con mobs instalado en ticks acotados; desvincular devuelve la IA | 6.13, 11.4; 6.11, 7.8 |
| M05.6 | IA genérica como zombie de MythicMobs (`TPV_Zombie` no tiene `BaseAI` ni `AIGoalSelectors`); ninguna especie con `Brain` recibe metas | 4.11; 4.8, 6.14 |
| M05.7 y Terminado | Escenarios con evidencia; ningún mob congelado | 6.5–6.17 |
| M06.2 y Terminado | `Predictor: false` cierra la sesión y devuelve la IA; escenarios de ciclo de vida | 7.8, 7.9; 7.1–7.11 |
| M07.1 | Captura con 16 jugadores medida | 8.11, 11.6 |
| M07.3 y Terminado | Cada señal y evento con escenario de servidor; costo de captura y sonda | 8.1–8.10; 8.11, 11.5 |
| M08 (pendientes) | Latencia desde el host y tamaño de `state` en combate; 403, 429 y 529 si aparecen | K3; cualquier sesión K |
| M09 (pendiente) | 429 y 529 con `retry-after` | Cualquier sesión K, si aparecen |
| M10.9 y Terminado | Sin dos golpes en la misma intención; daño doble, flanqueo, pérdida de Jev y recuperación | 9.3–9.5; K2 |
| M11.1 | Tabla de defaults en la guía del autor | Hecho en M14 ([guía](../../../Guias/Guia-del-autor.md#11-amenaza-enriquecida)) |
| M11.6 | Escenarios con evidencia; comparación sin enriquecer y con defaults | 10.1–10.8 |
| M12.4 | Instalación limpia sin errores con `status` explicativo | 1.5–1.7 |
| M12.5 | Predictor apagado como zombie de MythicMobs; con Predictor todos los movimientos en `inspect` | 6.3; 6.5–6.10 |
| M12.7 | Pasos de MythicScript 2077 en la guía del autor | Hecho en M14 ([guía](../../../Guias/Guia-del-autor.md#13-vocabulario-para-mythicscript-2077)) |
| M13.2 | Evidencia con plantilla; sin claves ni nombres | 11.2 y todas las sesiones |
| M13.3 | Tabla de tres escalones; colas acotadas; capacidad del host | 11.3, K4 |
| M13.4 | Percentiles por tramo; decisión de `timeoutMillis` | K3 |
| M13.5 | A/B con dispersión; conclusiones limitadas | 12, K5.1 |
| M13.6 | Umbrales con la curva del modelo | K5 y la revisión de etiquetas |
| M13.7 | Informe final; defaults y guías coinciden | Tras K5 |
| M14.1 | Un autor reproduce el ejemplo siguiendo solo la guía | 6.1–6.6 |
| M14.5 | Los ocho pasos aprobados; ninguno requirió compartir la clave | Ver la tabla de aceptación de la guía por sesiones |
| M15.4 | El aviso del lint en `status` y en el registro de carga, con la ruta de la táctica | 15.2 |
| M15.6 | El golpe de área no se lanza fuera de su radio | 15.3 |
| M15.9 | El ejemplo vincula sin avisos con el pack de verificación; `slam` no daña a otros mobs | 15.1, 15.3 |
| M16.6 | Contingencias medidas en combate (`contingencies: asked= fired= effective=`) | 16.2.c |
| M16.8 | `status` e `inspect` muestran la efectividad por nivel (`LOCAL` sin clave; `JEV` y `ALTERNATIVE` con clave) | 16.1.a–16.1.c; 16.2.c |
| M16.8 | Guion de servidor, sesión 16: ventanas con daño y alcance coherentes con lo observado (pendiente) | 16.1.b–16.1.e; 16.2.b |
| M16.10 | Re-banco de pago con etiquetas revisadas (en la máquina de desarrollo, no en el servidor; tras revisar al menos `core24`) | Fuera del servidor: [M16.10](../M16-Efectividad-de-las-respuestas-de-Jev.md#m1610-re-banco-de-pago) |
| M16.11 | Sesión 16 escrita con comandos exactos, esperado y costo | Hecho en M16 (esta guía) |
| M17.2–M17.5 | Con los defaults, `inspect` como en la 0.0.1 (solo `player_1`); con `kind:MONSTER`, `entity_1` con especie, salud, tendencia y alcance | 17.1, 17.2 |
| M17.6 | Dos mobs de ThePredict de facciones distintas forman dos encuentros: `status` dice `Encounters: 2 active, …` y cada `inspect` da un `encounter <id>` distinto con `Target: entity_1 (zombie)` | 17.3 |
| M17.2 | `PlayerGameModes: []` nunca toma a un jugador | 17.4 |
| M17.4 | Señales de un esqueleto admitido (`DRAWING_BOW`); un mob con escudo que no lo levanta no da `RAISING_SHIELD` ni `BLOCKING` | 17.5 |
| M17.3 | Costo de `perceiveNonTargets` (`tick_work`) con 10 mobs y 30 entidades | 17.6 |
| M17.10 | Sesión 17 escrita con comandos exactos y esperados | Hecho en M17 (esta guía) |
| M18.1 | Mismas estadísticas, metas base, skills y cooldowns en las cuatro variantes; el perfil solo admite al rival | Hecho en local (`UserTestPackTest`); 18.2 lo confirma en juego (DT-40 a) |
| M18.2 | Ningún jugador ni mob ajeno recibe daño de un duelista | 18.1 |
| M18.3 | Informe con los cuatro brazos, dispersión, intervalos, empates y la comprobación de D | 18.3 (y 18.4 con clave), con la [plantilla del duelo](Plantilla-informe-duelo.md) |
| M18.4 | Una sesión completa (80 + 30 rondas) cabe en los límites y produce todos los archivos; cada decisión de Jev se reconstruye por `(encounter, sequence)`; sin coste en `tick_work` sin grabar | 18.3, 18.4, 18.5 (el simulador lo comprueba en local) |
| M18.5 | Brillo toda la ronda, barra con la táctica y partículas de cada señal | 18.2 |
| M18.6 | Sesión 18 con comandos exactos, esperado, costo y copia de vuelta | Hecho en M18 (esta guía) |
| M18.8 | Informe del duelo con conclusiones limitadas a esta base (plan §12) | Tras 18.3 y 18.4 |
| M19.8 | Sesión 19 escrita (sin clave, costo cero, comandos y esperado). No ejecutada | Hecho en la guía; la ejecución es del usuario |
| M19.1 | 38 primitivas en la guía del autor, la misma tabla del plan | Hecho en la guía; `UserGuidesTest` de las dos listas lo cierra el frente de código |
| M19 servidor | DT-43 a DT-47 con evidencia literal. DT-48 no se cierra en la sesión 19 (el paso 19.9 solo lo cita; se mide en 20.2) | 19.1, 19.3, 19.6, 19.7 y 19.9. Casillas abiertas |
| M20 servidor | Sesión 20 escrita. DT-48, DT-49 y DT-50 abiertas. No ejecutada | 20.1–20.6. Pendiente de servidor |
| M21.12 | Sesión 21 escrita con comandos, esperado y costo. 21.5 queda opcional | Hecho en la guía; la ejecución es del usuario |
| M21 servidor | Sesión 21 escrita. DT-51, DT-52 y DT-53 abiertas. No ejecutada. 21.5 ≈ 0,19 USD, techo 0,60 USD, solo con aprobación | 21.1–21.5. Pendiente de servidor |
| P-AIConfig | Sesión AIConfig escrita. DT-100 abierta. No ejecutada | AIConfig.1–AIConfig.5. Pendiente de servidor |
| M23 | Sesión 23 escrita. DT-59 a DT-62 abiertas. DT-63 retirada. No ejecutada. Costo cero | 23.1–23.7. Pendiente de servidor |
| M24 | Sesión 24 escrita. DT-64 a DT-68 abiertas. No ejecutada. Costo cero. El paso 24.5 no tiene DT | 24.1–24.7. Pendiente de servidor |
| M25 | Sesión 25 escrita. DT-69 a DT-73 abiertas. No ejecutada. 25.8 de pago, no ejecutada | 25.1–25.8. Pendiente de servidor |
| M26 | Sesión 26 escrita. DT-74 a DT-78 abiertas. No ejecutada. 26.7 de pago, no ejecutada | 26.1–26.7. Pendiente de servidor |
| M27 | Sesión 27 escrita. DT-79 a DT-84 abiertas. No ejecutada. 27.7 no se prepara. 27.8 de pago, no ejecutada | 27.1–27.6 y 27.8. Pendiente de servidor |
| M28 | Sesión 28 escrita. DT-85 a DT-91 abiertas. No ejecutada. 28.3 y 28.4 de pago, no ejecutadas | 28.1–28.5. Pendiente de servidor |
| M29 | Sesión 29 escrita. DT-92 a DT-99 abiertas. No ejecutada. 29.2 y 29.3 B de pago, no ejecutadas. 29.7 no levanta Kev. DT-98 abierta hasta la cinta | 29.1–29.7. Pendiente de servidor |
| M19 medidas | Presupuestos de `readyCasts`, poda y parse; E1, E2 y columna de tokens con poda | Pendiente. No medido en este frente |
