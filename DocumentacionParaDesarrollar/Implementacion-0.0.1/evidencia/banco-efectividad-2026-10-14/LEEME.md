# Banco de efectividad v2 (2026-10-14)

Pregunta: en el simulador local y sin gastar créditos de Jev, ¿gana ThePredict al menos el 75 % contra la IA nativa y contra configuraciones buenas de MythicMobs?

## Resultado

**No cumple.** El criterio quedó fijado antes de medir: en cada celda, la cota inferior de Wilson unilateral con Bonferroni debe ser ≥ 0,75. Las puertas de validez V1–V6 pasan, así que el veredicto vale.

| ThePredict | Sin Jev | Con Jev simulado (p = 0,85, latencia medida) | p* |
|---|---|---|---|
| Defaults de entrega (responde a la pregunta) | no cumple: 12 de 29 celdas | no cumple | no existe para p ≤ 1 |
| Perfil del paquete | no cumple: 12 de 29 celdas | no cumple | no existe para p ≤ 1 |

- **Peor celda sin Jev:** K3-OPEN18-60 contra `adversarial`, 0 V / 0 E / 400 D. **Con Jev:** K1-OPEN18-60 contra `adversarial`, s = 0,024.
- **Contra quién gana:**
  - el zombi vanilla, pero por el alcance de `Mob.attack` (2,5 bloques de hueco); con el golpe del propio zombi saca 0 V / 209 E / 191 D;
  - el clásico del paquete, en K1, K2 y K3;
  - `legal-equal`, en K1.
- **Contra quién pierde:**
  - contra `legal-best`, entre el 85 % y el 98 % de las rondas en K1, K2 y K3 a 60 de vida;
  - contra `adversarial`, entre el 95 % y el 100 % en K1-OPEN18-60, K1-PILLARS18-60, K2 y K3. En K1-SMALL8-60, arena para la que no se buscó, ThePredict saca 0,671. En K1 a 200 de vida, 378 de 400 rondas acaban en empate.
- **Causa principal:** ThePredict pierde golpes contra la ventana de invulnerabilidad que abren sus propios impactos en vuelo. `ImpactTiming` solo mira la ventana actual del rival. Contra `legal-best` en K1 hace 211 de daño por minuto frente a 259, y pierde 13,3 golpes enteros por minuto frente a 0. Se ve en «Daño por minuto» de `banco.md`.
- **Jev** recibe unas 2 peticiones por ronda. Ni un juez perfecto mueve s más de 0,033.
- **K4** empata siempre por construcción: el daño máximo iguala a la mitigación máxima. Ninguna IA puede pasar esa celda.
- **El detalle**, los hallazgos del producto y los límites están en `ThePredict-pulido/AUDITORIA-2026-10-14/arreglos-bench-v2.md`.

## Qué hay aquí

- `banco.md` es el informe. Empieza por el veredicto y sigue con:
  - el criterio y el método;
  - las puertas, los rivales y las celdas de cada configuración;
  - el daño por minuto y la conducta;
  - la sensibilidad a Jev y las demás sensibilidades;
  - la matriz A–D re-medida, las búsquedas y los límites.
- Tablas `.tsv`, separadas por tabuladores y con cabecera:
  - `celdas.tsv`: victorias, empates, derrotas, cota y diagnóstico de cada celda;
  - `danio.tsv`: daño y golpes perdidos por minuto de cada celda;
  - `habilidades.tsv`: casts y golpes por minuto, por celda y habilidad;
  - `puertas.tsv`, `rivales.tsv`, `jev.tsv`, `conducta.tsv`, `busqueda.tsv` y `brazos.tsv`.
- `clasicos/`: el archivo de MythicMobs de cada rival.
- `congelados/`: los ganadores de las búsquedas, con su huella. Son copia de `src/test/resources/eval/classic-legal-best-v1.yml` y `classic-adversarial-v1.yml`.
- `perfiles/`: el perfil de ThePredict de cada kit, generado desde el kit y cargado con el `ProfileLoader` real.

## Cómo se reproduce

Desde la raíz del proyecto, sin red:

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew effectivenessBench --offline
```

- Escribe en `build/reports/efectividad/`.
- Lee los rivales buscados de `src/test/resources/eval/`.
- Vuelve a buscar si el código de un rival ya no da su huella o si cambió el perfil de ThePredict.
- Propiedades:
  - `-Pthreads=4`: hilos; el resultado no depende de este número.
  - `-Pseeds=200`: semillas por celda; cada semilla juega los dos lados.
  - `-Psearch=auto|force|never`: usar los rivales congelados, buscar siempre o no buscar nunca.
  - `-Pfreeze=true`: escribir los ganadores de una búsqueda en `src/test/resources/eval/`.
- Es determinista: dos corridas con los mismos archivos dan las mismas tablas. Solo cambia el tiempo medido al final de `banco.md`.
  - Esta evidencia salió de `-Psearch=force -Pfreeze=true`: unos 11 min, casi todos de búsqueda.
  - Con los congelados tarda unos 20 s.
- La prueba de humo `EffectivenessBenchSmokeTest` corre dentro de `check`, con un tamaño mínimo.
