# ThePredict 0.0.1 — requisitos y roadmap

> **Nota de historia (M12, 2026-09-29):** documento conservado como historia. El camino con OpenRouter que describe se retiró en M12 del código, las pruebas, los recursos y la documentación activa; la 0.0.1 decide solo con Jev (TypeSafe). Las guías `Verificacion-servidor-0.0.1.md` y `Concurrencia-y-costos-0.0.1.md` están ahora en `archive/`.

> Este documento describe los requisitos de la base con OpenRouter. Las decisiones que completan la 0.0.1 —Jev exclusivo, IA de movimiento gestionada por ThePredict, control táctico y fallback escalonado— están en el [plan de la 0.0.1](Plan-Jev-y-combate-tactico.md). Sus propuestas todavía no están implementadas.

**Estado:** implementación compilada y probada de forma local; falta validación en el servidor alojado.  
**Fecha:** 2026-09-28.  
**Alcance:** addon de MythicMobs Premium para mobs sobre Paper 26.2 estable y Java 25, con intención de compatibilidad 26.x posterior.

El orden de trabajo y sus criterios de aceptación están en [Plan-iterativo-0.0.1.md](Plan-iterativo-0.0.1.md).

## 1. Visión y frontera del producto

MythicMobs define mobs, items, skills, nombres y ejecución de contenido. En 0.0.1, ThePredict añade selección contextual de skills para mobs configurados; la IA existente de MythicMobs sigue siendo la base del comportamiento y el respaldo ante fallos. El motor de decisión se comunica con OpenRouter mediante el port `DecisionProvider`; otros proveedores podrán tener adapters propios. La integración con Paper y MythicMobs permanece en adapters.

La ambición posterior es sustituir metas, objetivos y decisiones del mob con una IA capaz de aprender y mejorar durante el combate, además de incorporar perfiles `TRAINED` y `UNPREDICTABLE`. **La 0.0.1 no aprende ni entrena modelos**: se concentra en una decisión en tiempo real entre skills elegibles de un mob vinculado, con `NORMAL` como único perfil ejecutable.

## 2. Resultado observable de la 0.0.1

Un autor configura un mob de MythicMobs y un perfil de ThePredict. Al entrar en una situación de combate válida, el addon captura el contexto del mob y su objetivo, determina qué acciones configuradas están disponibles, consulta al proveedor predictivo y ejecuta mediante MythicMobs una acción elegida que siga siendo válida. Registra la decisión y el resultado para poder explicar y diagnosticar el comportamiento.

Si el proveedor falla o tarda más que el plazo configurado, ThePredict deja esa decisión a la IA y las skills existentes de MythicMobs. El addon no repite una respuesta remota caducada.

### Requisitos funcionales

1. `Predictor: true` activa las consultas al modelo para el mob; `false` o ausencia del campo las desactiva. Apagarlo permite probar el mismo boss y su configuración con la IA existente de MythicMobs, sin consumir el servicio remoto.
2. El autor escribe el perfil en la carpeta `AI`. Activar `Predictor` no genera archivos. Cada mob activado se vincula a un único perfil; perfiles duplicados o incompletos se rechazan al cargar, con un error que identifica archivo y campo.
3. La 0.0.1 acepta perfiles `NORMAL`. Los otros nombres de modo se reservan para versiones posteriores y se rechazan como no implementados.
4. Una acción del perfil apunta a una skill existente de MythicMobs. Esa skill puede realizar efectos de MythicMobs, incluidos invocar mobs o usar items, sin crear tres mecanismos de ejecución en la primera versión.
5. Cada skill candidata declara `PredictValues`: etiquetas legibles de alcance, objetivo, daño, efecto o intención que ayudan a compararla con la situación actual. ThePredict mantiene y valida internamente el vocabulario y el significado de cada etiqueta.
6. Antes de consultar al proveedor se filtran acciones no disponibles por requisitos y cooldown. El proveedor solo puede devolver un identificador de acción recibido entre los candidatos.
7. La evaluación usa hechos capturados con vigencia conocida: identidad del mob y objetivo, salud, posiciones de jugadores, distancia y demás datos necesarios para los contextos de la primera entrega. El adapter puede aprovechar los métodos de MythicMobs para encontrar y filtrar objetivos.
8. Los contextos o reglas configuradas acotan los candidatos. Si ninguna acción es elegible, se registra abstención y no se consulta al proveedor.
9. Antes de ejecutar se valida que el mob y objetivo sigan existiendo, que la situación y configuración no hayan invalidado la decisión y que la acción conserve su elegibilidad.
10. Las consultas remotas son asíncronas, tienen plazo y concurrencia limitada por mob. El acceso a entidades y la ejecución de skills ocurren en el contexto de servidor permitido por Paper y MythicMobs.
11. Ante timeout, error de red, respuesta inválida o falta de candidatos, ThePredict se abstiene y deja operar la IA existente de MythicMobs.
12. El log informa errores del proveedor y, si se activa `decision.logDecisions`, las skills ejecutadas. La trazabilidad detallada por evaluación sigue pendiente para cerrar 0.0.1.

### Requisitos de calidad

- La frecuencia de decisión, el plazo remoto y la cantidad máxima de consultas simultáneas son configurables y tienen límites válidos.
- La configuración se valida completa antes de activarse. Una recarga fallida conserva la configuración anterior.
- El núcleo de evaluación no importa Bukkit, Paper, MythicMobs ni un cliente HTTP concreto.
- La compilación usa Java 25, Paper API `26.2.build.129-stable` y el JAR Premium `5.13.1-SNAPSHOT` compartido para desarrollo.
- Una prueba de extremo a extremo debe demostrar: contexto → candidatos → respuesta remota → revalidación → skill de MythicMobs → resultado.

## 3. Configuración implementada

El archivo `plugins/ThePredict/config.yml` define `provider.type`, `provider.model`, `provider.apiKey`, `provider.apiKeyEnvironment` y `provider.timeoutMillis`. Solo se admite `OPENROUTER` como tipo en 0.0.1. El modelo es un identificador de OpenRouter elegido por el administrador; una clave de entorno tiene prioridad sobre `provider.apiKey`. Si faltan modelo o clave, no se crean consultas remotas. El adapter HTTP queda detrás de `DecisionProvider` para añadir otros proveedores posteriormente. Las instalaciones con una sección anterior `jev:` deben cambiarla manualmente a `provider:`.

En el primer inicio se generan `config.yml`, `AI/README.txt` y `AI/example.yml.example`. El archivo `.example` no se carga como perfil y la falta de perfiles se informa como instalación pendiente. El addon exige MythicMobs 5.13.1 o superior y verifica en ejecución que sus managers de mobs y skills estén disponibles. `plugin.yml` declara `depend: [MythicMobs]` para asegurar el orden de carga. La instalación requiere la edición Premium usada durante el desarrollo; la build local no ofrece una señal fiable para distinguir ediciones en ejecución.

El comando administrativo `/thepredict status` presenta versión de Java, versión de MythicMobs, resultado de carga de perfiles y estado de OpenRouter. `/thepredict check` repite las comprobaciones de autenticación y disponibilidad del modelo con consultas HTTP de lectura, sin generar una respuesta del modelo. Los errores de configuración, clave, modelo, timeout, red, límite de peticiones y respuesta inválida se categorizan para el comando y la consola. Si una decisión remota falla después, se bloquean nuevas consultas hasta comprobar de nuevo la conexión; MythicMobs conserva sus acciones normales. No se muestran claves ni cuerpos de respuesta en el estado. El addon carga `config.yml` con un parser estricto y conserva el comando de estado si el YAML es inválido.

`Predictor: true` se lee de la configuración del mob de MythicMobs; `false` es implícito si falta. Es un interruptor de uso del modelo: el perfil puede existir mientras `Predictor` está apagado. La API del JAR Premium expone `MythicMob.getConfig()`; falta probar en el servidor que conserva ese campo personalizado al cargar el mob.

Los archivos escritos por el autor se cargan de `plugins/ThePredict/AI/`. Cada perfil tiene ID propio y referencia a un mob de MythicMobs. `TypeOfMythicID` acepta `MOB` en el perfil y `SKILL` en cada choice; los otros tipos se reservan para el futuro.

Es preferible declarar acciones como mapas YAML que codificar varios parámetros en una cadena. Por ejemplo:

```yaml
guardian_ai:
  MythicID: Guardian
  TypeOfMythicID: MOB
  Type: NORMAL
  Objective: Survive and defeat the current target
  Choices:
    melee:
      MythicID: GuardianMelee
      TypeOfMythicID: SKILL
      CD: 40
    defense:
      MythicID: GuardianDefense
      TypeOfMythicID: SKILL
      CD: 100
      RequiresTarget: false
    ranged:
      MythicID: GuardianRanged
      TypeOfMythicID: SKILL
      CD: 60
  Behaviors: [Idle, AttackingPhase, LowHealthPhase, TrackingEnemy]
  Combinators: true
  Operations:
    - Behavior: LowHealthPhase
      Choices: [defense, ranged]
    - Behavior: AttackingPhase
      Choices: [melee, ranged]
    - Behavior: TrackingEnemy
      DirectChoice: ranged
```

`Idle` significa que no hay target; `AttackingPhase`, que el target está a una distancia menor o igual a `AttackRangeBlocks`; `TrackingEnemy`, que está más lejos; `LowHealthPhase`, que la proporción de salud del mob es menor o igual a `LowHealthRatio`. `LowHealthPhase` puede coexistir con otro comportamiento. Cuando `Combinators` es `true`, la primera operación aplicable tiene prioridad y acota las choices. `DirectChoice` elige una sola skill sin consultar OpenRouter. Cuando es `false`, todas las choices disponibles participan. Si queda una sola choice disponible, también se evita la llamada remota.

Los nombres `MeleeAttack`, `Defense` y `RangedAttack` pueden ser identificadores de ejemplo, no categorías obligatorias. La elección remota debe operar sobre IDs configurados y descripciones acotadas, de modo que un autor pueda añadir habilidades distintas sin cambios en Java.

El autor también quiere describir cada skill en MythicMobs, por ejemplo:

```yaml
MySkill:
  PredictValues: RANGED_ATTACK,SINGLE_TARGET,LOW_DAMAGE,APPLIES_POISON
```

`PredictValues` es metadato declarativo de la skill. ThePredict mantiene etiquetas y descripciones; no requiere códigos numéricos ni un modelo entrenado para interpretarlos. `APPLIES_POISON` expresa un efecto concreto. El código acepta también `APPLIES_BAD_OMEN` y las demás etiquetas enumeradas en la guía de uso. La API del JAR Premium expone `Skill.getConfig()`; falta comprobar en el servidor que conserva este campo personalizado.

## 4. Flujo y responsabilidades

1. **Mythic binding:** descubre mobs activados y relaciona cada uno con un perfil válido.
2. **Snapshot capture:** obtiene datos necesarios del mob, objetivo, jugadores cercanos y combate en el contexto autorizado del servidor, usando las capacidades de MythicMobs cuando correspondan.
3. **Context assessment:** calcula hechos y reglas locales sin efectos de servidor.
4. **Candidate selection:** elimina acciones sin objetivo válido, fuera de alcance, bloqueadas por cooldown o deshabilitadas por reglas.
5. **Decision provider:** recibe un snapshot de datos y las descripciones de acciones elegibles; pregunta al proveedor remoto por una acción entre sus IDs. Timeout, errores y respuestas ajenas al conjunto son resultados explícitos. El proveedor remoto no accede directamente a métodos de MythicMobs ni al mundo vivo.
6. **Action execution:** revalida y activa la skill de MythicMobs mediante el adapter.
7. **Outcome recorder:** registra qué ocurrió y por qué para observabilidad de la 0.0.1. No actualiza pesos ni patrones entre peleas.

No se necesita RAG en 0.0.1 para escoger entre tres acciones configuradas. Solo se incorporará recuperación de recuerdos cuando haya datos persistentes, una consulta definida y una mejora medible en las decisiones.

## 5. Guía de diseño y código

- Cada tipo y método tiene una responsabilidad concreta. Se divide una operación cuando las partes tienen contratos o razones de cambio distintos; no se divide solo para aumentar el número de funciones.
- Identificadores Java en inglés natural, explícitos y consistentes. Se prioriza claridad sobre longitud mínima.
- Comentarios y Javadoc: rige la regla C9 de [Implementacion-0.0.1/00-Arquitectura-y-reglas.md](Implementacion-0.0.1/00-Arquitectura-y-reglas.md), que mantiene la regla "sin comentarios" y solo admite Javadoc para tooltips; sustituye al resto de esta guía.
- Usar construcciones modernas de Java cuando reduzcan estados inválidos o simplifiquen el flujo: records inmutables, resultados tipados, interfaces en fronteras externas y composición. Evitar abstracciones sin un caso real.
- La selección predictiva queda acotada por configuración y validación local. La respuesta remota nunca ejecuta directamente una skill.

## 6. Roadmap corto

| Entrega | Estado y resultado verificable |
|---|---|
| A. Contrato | Configuración, vocabulario, contextos y prioridad documentados; sujetos a revisión con la prueba de gameplay |
| B. Integración MythicMobs | Dependencia y versión mínima verificadas; compila contra Premium `5.13.1-SNAPSHOT`; falta observar un cast en el servidor |
| C. Decisión local | Snapshot, contextos, filtrado y revalidación implementados; pruebas puras aprobadas |
| D. Proveedor remoto | Adapter OpenRouter, comprobación de clave/modelo y timeout implementados; contrato HTTP probado con servidor local, sin clave real |
| E. Cierre 0.0.1 | Pendiente: prueba de extremo a extremo en servidor, trazabilidad y medición básica de latencia/carga |

## 7. Decisiones abiertas

1. Confirmar en el servidor alojado que MythicMobs Premium conserva los campos `Predictor` y `PredictValues` y que la ejecución programática de la skill respeta sus condiciones y targeters.
2. Evitar casts duplicados si una skill de `Choices` también tiene triggers normales de MythicMobs. Recomendación actual: reservar esas skills para ThePredict y mantener otras skills con triggers normales como fallback.
3. Medir la latencia de OpenRouter y el efecto de varias entidades activadas para ajustar `DecisionIntervalTicks`, timeout y concurrencia máxima.
4. Decidir el formato de trazabilidad de decisiones y abstenciones antes de publicar 0.0.1.

## 8. Verificación externa pendiente

La [documentación oficial de la API de MythicMobs](https://wiki.mythiccraft.io/mythicmobs/API) describe dependencias, eventos, mecánicas y triggers para addons. La [guía oficial de OpenRouter](https://openrouter.ai/docs/quickstart) documenta la solicitud HTTP usada por el adapter. La prueba con el servidor alojado es el siguiente paso de integración.
