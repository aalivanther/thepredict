# ThePredict: tareas por iteración

> **Nota de historia (M12, 2026-09-29):** documento conservado como historia. El camino con OpenRouter que describe se retiró en M12 del código, las pruebas, los recursos y la documentación activa; la 0.0.1 decide solo con Jev (TypeSafe). Las guías `Verificacion-servidor-0.0.1.md` y `Concurrencia-y-costos-0.0.1.md` están ahora en `archive/`.

> Referencia de la base existente con OpenRouter. La 0.0.1 continúa en el [plan de Jev y combate táctico](Plan-Jev-y-combate-tactico.md), cuyos 14 módulos (sección 11) sustituyen a estas tareas y aún están pendientes.

**Fecha:** 2026-09-28. **Objetivo inmediato:** convertir la base compilable en un addon verificable en un servidor Paper 26.2 con MythicMobs Premium. Cada iteración debe terminar con una prueba observable antes de ampliar el contrato.

## Módulos de trabajo y orden

Cada módulo tiene una entrada y salida comprobables. Se avanza al siguiente cuando su prueba local pasa; las verificaciones que requieren el servidor alojado quedan marcadas como pendientes hasta ejecutarlas allí.

| Módulo | Responsabilidad única | Entrega comprobable | Estado |
|---|---|---|---|
| M1. Entorno | Leer y validar Java, Minecraft/Paper y MythicMobs | Versiones detectadas o causa precisa de rechazo; sin iniciar decisiones | Implementado localmente; falta servidor |
| M2. Primer inicio | Crear únicamente archivos ausentes | `config.yml`, guía y ejemplos inactivos; segundo inicio no sobrescribe | Implementado y probado localmente; falta servidor |
| M3. Configuración global | Parsear YAML y validar tipos/rangos | Ajustes inmutables o error visible; nunca convertir YAML roto en defaults activos | Implementado y probado localmente |
| M4. Diagnóstico | Mantener estados de perfiles y proveedor | `/thepredict status` y `/thepredict check` con causas seguras | Implementado localmente; falta prueba en juego |
| M5. Valores heredables | Resolver defaults globales y overrides por mob | Precedencia documentada y probada sin duplicar valores en perfiles | Implementado y probado localmente |
| M6. Perfiles por mob | Leer YAML, validar referencias y vincular IDs de MythicMobs | Un perfil por tipo de mob; errores con archivo y campo | Parser y binder probados contra interfaces Premium; falta servidor |
| M7. Ejemplo completo | Entregar mob, skills y perfil compatibles entre sí | Boss de prueba listo para copiar, desactivado por defecto | Archivos y parser probados; falta combate real |
| M8. Estado por instancia | Aislar cooldowns, peticiones y limpieza por UUID | Dos instancias del mismo tipo no comparten estado | Implementado y probado localmente; falta prueba simultánea |
| M9. Cast de MythicMobs | Revalidar y ejecutar skill mediante su API | Condiciones, targeters y cooldowns observados en servidor | Compila con Premium; falta integración real |
| M10. Decisión remota | Acotar candidatos, validar respuesta y aplicar fallback | Decisión real y fallo sin cast del addon | Ciclos HTTP locales con recuperación probados; falta clave real |
| M11. Pruebas de servidor | Ejecutar matriz de arranque, recarga y combate | Evidencia reproducible con versiones y salida sin secretos | Matriz lista; ejecución pendiente del servidor alojado |
| M12. Entrega | Limpiar JAR, documentar instalación y límites | Otro administrador puede reproducir instalación y diagnóstico | `build` verifica el JAR, las pruebas y la guía; aceptación en servidor pendiente |

**Regla de alcance:** M1–M4 estabilizan la base, M5–M7 hacen utilizable la configuración, M8–M10 cierran el gameplay y M11–M12 cierran la 0.0.1. El selector de perfil para dos instancias del mismo tipo queda después de 0.0.1.

## Estado actual y límite

- `Predictor: true` activa un mob; `false` conserva el comportamiento normal de MythicMobs.
- Un perfil `AI/*.yml` se vincula a un **tipo** de mob mediante `MythicID`. El código rechaza dos perfiles para el mismo tipo.
- Las instancias del mismo tipo comparten la definición del perfil, pero tienen cooldowns, frecuencia de evaluación y consultas pendientes separados por UUID.
- `config.yml` tiene valores globales de proveedor, ejecución y `profileDefaults`, heredados por perfiles salvo override explícito.
- `AI/example.yml.example` sirve de guía y no se ejecuta. `examples/` incluye un boss completo con mob, skills y perfil listos para copiar; empieza con `Predictor: false`.
- No existe selección de perfiles distintos entre dos instancias del mismo tipo. Esto requiere un contrato adicional de selección, no solo otro archivo YAML.

## Iteración 1: arranque, diagnóstico y fallos explícitos

1. Comprobar Java 25, Paper 26.2 y MythicMobs 5.13.1+ antes de iniciar decisiones. Mostrar en consola qué componente falló y la versión detectada cuando exista.
2. Crear `config.yml`, guía y ejemplo únicamente si faltan. Un primer inicio sin clave o perfiles debe mostrar `UNCONFIGURED` o `SETUP_PENDING`, no un error.
3. Cargar `config.yml` sin sustituir silenciosamente un YAML roto por valores por defecto. Validar tipos y rangos de todos los campos.
4. Clasificar fallos de proveedor: clave, modelo, límite de solicitudes, timeout, red, respuesta inválida e ID no permitido. No registrar claves ni cuerpos HTTP.
5. Mostrar Java, MythicMobs, perfiles y proveedor en `/thepredict status`; permitir `/thepredict check` para una comprobación de lectura sin generación pagada.
6. Mantener el addon sin decisiones cuando la configuración o el proveedor no estén listos, dejando a MythicMobs ejecutar su comportamiento.

**Aceptación:** primer arranque, YAML inválido, versión incompatible, clave incorrecta y servicio inaccesible tienen mensajes distintos. Las pruebas unitarias y de contrato cubren cada caso. La verificación en una JVM anterior a 25 depende del propio cargador de Paper: un JAR de bytecode 25 puede ser rechazado antes de ejecutar `onEnable`.

## Iteración 2: configuración general y ejemplos utilizables

1. Definir una precedencia única: valores internos seguros → `config.yml` global → perfil del tipo de mob. La clave y el modelo permanecen globales en 0.0.1.
2. Mover `DecisionIntervalTicks`, `LowHealthRatio` y `AttackRangeBlocks` por defecto a una sección `profileDefaults` de `config.yml`. Mantener `Choices`, `Objective` y `MythicID` obligatorios en el perfil del mob.
3. Crear un paquete `examples/` con un mob MythicMobs, dos o tres skills con `PredictValues` y su perfil AI correspondiente. Los ejemplos deben estar desactivados hasta que el administrador los copie a las carpetas de MythicMobs y AI.
4. Validar IDs duplicados, referencias inexistentes, operaciones contradictorias y campos mal escritos antes de activar una recarga. Informar archivo, perfil y campo.
5. Documentar el archivo global, el perfil por mob y el ejemplo completo con el resultado esperado.

**Aceptación:** dos mobs con perfiles diferentes heredan los mismos valores generales, conservan sus elecciones y objetivos propios, y el ejemplo funciona al copiar sus archivos sin escribir Java.

## Iteración 3: ciclo de vida por instancia y vínculo MythicMobs

1. Confirmar en el servidor Premium que MythicMobs conserva `Predictor` en mobs y `PredictValues` en skills; si una build no los conserva, usar un mecanismo de configuración compatible sin asumir que la lectura actual funciona.
2. Registrar y limpiar estado por UUID al aparecer o desaparecer un mob. Verificar que dos instancias del mismo tipo no compartan cooldowns ni respuestas pendientes.
3. Revalidar la referencia al mob, target, perfil, condiciones y cooldown justo antes del cast. Una recarga debe invalidar respuestas antiguas.
4. Confirmar que el cast programático respeta condiciones, targeters, cooldowns y triggers de MythicMobs. Evitar casts duplicados con skills también activadas por triggers normales.
5. Medir coste por tick con varios bosses y mantener límites de observación y concurrencia.

**Aceptación:** dos instancias simultáneas del mismo boss actúan independientemente y ninguna respuesta atrasada se aplica a otra instancia o a un perfil recargado.

## Iteración 4: decisión remota y fallback en gameplay

1. Probar una clave real de OpenRouter en el servidor alojado. La comprobación de lectura valida clave y modelo; una prueba de combate valida además la ruta de generación.
2. Verificar que el modelo solo puede devolver un ID elegible o abstenerse. Un HTTP fallido, timeout o respuesta ajena al conjunto no ejecuta ninguna skill del addon.
3. Definir recuperación de fallos temporales: reintento administrativo y, si se justifica con observación real, reintento automático con intervalo limitado.
4. Medir latencia, uso de tokens y frecuencia de decisiones para ajustar timeout, intervalo y concurrencia sin cambiar el contrato de perfiles.

**Aceptación:** un combate real demuestra snapshot → candidatos → respuesta remota → revalidación → cast; al desconectar OpenRouter continúa la IA normal de MythicMobs.

## Iteración 5: entrega 0.0.1

1. Ejecutar la matriz de compatibilidad con el JAR Premium proporcionado y Paper 26.2 estable. La compatibilidad con otras 26.x queda documentada hasta probarla.
2. Revisar que el JAR solo contenga código y recursos activos, sin claves ni dependencias Premium empaquetadas.
3. Publicar guía de instalación, ejemplo, referencia de YAML, comandos, permisos, mensajes de error y limitaciones verificadas.
4. Guardar una prueba reproducible del servidor alojado: versiones, configuración sin secretos, salida de `/thepredict status`, un cast correcto y un fallo con fallback.

**Aceptación:** otro administrador puede instalar el addon, copiar el ejemplo, observar una decisión y diagnosticar un fallo usando solo la documentación.

## Después de 0.0.1: perfiles distintos entre instancias del mismo tipo

Primero se debe elegir un identificador de perfil por instancia, por ejemplo una variable de MythicMobs establecida al spawn. El selector debe validarse contra perfiles ya cargados; cambiarlo debe cancelar solicitudes pendientes y reiniciar solo el estado de esa instancia. Esto no debe introducirse hasta que el perfil por tipo y el aislamiento por UUID estén probados, porque amplía el contrato de configuración y la política de recarga. El aprendizaje y los modos `TRAINED` y `UNPREDICTABLE` siguen fuera de 0.0.1.
