# Cómo reportar resultados

> Paquete de pruebas ([índice](README.md)). Una respuesta por sesión. Copia literal, sin resumir; sin clave, sin cabeceras HTTP y sin nombres de jugadores.

## Antes de enviar

1. Sustituye cada nombre de jugador por `A`, `B`, `C` o `D` (ThePredict escribe `player_1`, `player_2`…; eso se deja tal cual).
2. Nunca incluyas `.env`, `config.yml` con `apiKey` relleno ni la salida de `env`. Si adjuntas `config.yml`, redáctalo primero: `sed -E "s/^([[:space:]]*apiKey:).*/\1 '<redactada>'/" plugins/MythicMobs/AIConfig/config.yml`.
3. Si guardas evidencia en disco, ejecuta las comprobaciones de [G13 §2](../Guion-servidor-0.0.1.md#2-plantilla-de-evidencia-m132); ninguna debe imprimir nada. **Fase 1 (sin clave):** solo la 1 (cabeceras y `TYPESAFE_API_KEY=`) y la 3 (nombres de jugadores). **Sesiones K (con clave):** las tres; la 2 (`${TYPESAFE_API_KEY:?}`) se ejecuta en un shell en el que se haya cargado el entorno con `set -a; source .env; set +a`, sin imprimirlo.
4. Los informes de spark nunca se suben al visor web sin tu aprobación; copia las cifras del chat o de la consola.

## Plantilla

````markdown
## Sesión <n>: <nombre> — <AAAA-MM-DD>

- Versiones: Paper <build>, MythicMobs <versión>, Java <versión>; SHA-256 del JAR de ThePredict: <huella>
- Clave de Jev: sin clave | con clave (sesión K)
- Cuentas usadas: <número>
- Cambios en archivos del paquete (si algo no cargó): <qué y por qué, o "ninguno">

### Paso <n.m>: <título corto>
- Resultado: Aprobado | Fallido | Bloqueado | No ejecutado
- Ejecución 1:
```text
<salida literal de consola/chat/status/inspect>
```
- Ejecución 2:
```text
<salida literal>
```
- Observación en juego: <lo que viste, en una o dos frases>

### (repetir por paso)

### Costo (solo sesiones K)
- `Input tokens` al final: <línea>
- `totals`: <línea>

### Duelo (solo sesión 18)
- Carpeta de la grabación: `Evidencia-0.0.1/<fecha>-duelo/<etiqueta>-<UTC>/` completa (los CSV y `decisions.jsonl` llevan UUID de los duelistas a propósito; los cuerpos enviados a Jev no)
- `grep -c` de UUID sobre las líneas `"type":"body"`: <número, debe ser 0>
- `/thepredict duel status` al final: <línea>
- Marcador de la barra lateral (`red_wins`, `blue_wins`): <valores>
- Informe con la [plantilla del duelo](Plantilla-informe-duelo.md)
````

## Qué pasa después

Con cada informe, el orquestador: pega la evidencia en la entrada `DT-nn` del [registro](../M01-Registro-de-decisiones.md) y cambia su estado (`Confirmada` o `Alternativa aplicada`) solo si la evidencia lo respalda; marca las casillas de servidor de los módulos según el [checklist](Checklist-DT-y-casillas.md); y, si algo falla, corrige el contrato del módulo responsable y te pide repetir el paso (plan §11).
