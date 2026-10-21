# E2 local. Matriz de zona (M24)

200 semillas pareadas (cada una en los dos lados, 400 rondas) en los ocho terrenos del plan. En off el mob camina en línea hacia el objetivo. En guard y en full solo acepta el destino de Q4 o se queda. Una muerte es pisar vacío o lava. No es el duelo de M18. No se regeneraron las doradas de M17, M20 ni M22. No se ejecutó el banco E1, ni E6, ni la sesión 24. No hay rayo de cobertura.

| Terreno | Modo | Rondas | Muertes | Por hora | Destino propio | Supervivencia | Wilson muertes |
|---|---|---|---|---|---|---|---|
| flat | off | 400 | 0 | 0.0 | 0 | 400 | [0.000, 0.010] |
| flat | guard | 400 | 0 | 0.0 | 0 | 400 | [0.000, 0.010] |
| flat | full | 400 | 0 | 0.0 | 0 | 400 | [0.000, 0.010] |
| pillars | off | 400 | 0 | 0.0 | 0 | 400 | [0.000, 0.010] |
| pillars | guard | 400 | 0 | 0.0 | 0 | 400 | [0.000, 0.010] |
| pillars | full | 400 | 0 | 0.0 | 0 | 400 | [0.000, 0.010] |
| lava_moat | off | 400 | 400 | 17799.8 | 0 | 0 | [0.990, 1.000] |
| lava_moat | guard | 400 | 200 | 1469.4 | 0 | 200 | [0.451, 0.549] |
| lava_moat | full | 400 | 200 | 1469.4 | 0 | 200 | [0.451, 0.549] |
| cliff | off | 400 | 400 | 17465.1 | 0 | 0 | [0.990, 1.000] |
| cliff | guard | 400 | 200 | 1469.4 | 0 | 200 | [0.451, 0.549] |
| cliff | full | 400 | 200 | 1469.4 | 0 | 200 | [0.451, 0.549] |
| tower | off | 400 | 0 | 0.0 | 0 | 400 | [0.000, 0.010] |
| tower | guard | 400 | 0 | 0.0 | 0 | 400 | [0.000, 0.010] |
| tower | full | 400 | 0 | 0.0 | 0 | 400 | [0.000, 0.010] |
| bridge | off | 400 | 400 | 36000.0 | 0 | 0 | [0.990, 1.000] |
| bridge | guard | 400 | 200 | 1469.4 | 0 | 200 | [0.451, 0.549] |
| bridge | full | 400 | 200 | 1469.4 | 0 | 200 | [0.451, 0.549] |
| bog | off | 400 | 0 | 0.0 | 0 | 400 | [0.000, 0.010] |
| bog | guard | 400 | 0 | 0.0 | 0 | 400 | [0.000, 0.010] |
| bog | full | 400 | 0 | 0.0 | 0 | 400 | [0.000, 0.010] |
| corridor | off | 400 | 400 | 36000.0 | 0 | 0 | [0.990, 1.000] |
| corridor | guard | 400 | 200 | 1469.4 | 0 | 200 | [0.451, 0.549] |
| corridor | full | 400 | 200 | 1469.4 | 0 | 200 | [0.451, 0.549] |

## Puertas

- Destino propio con guard en cliff, lava_moat y bridge: 0, 0, 0. no cumple (el duelo no lo midió; hace falta 0).
- cliff: HAZARD_DEATHS_MOB_PER_HOUR off 17465.1, guard 1469.4, reducción 91.6 %. no cumple (el duelo no lo midió).
- lava_moat: HAZARD_DEATHS_MOB_PER_HOUR off 17799.8, guard 1469.4, reducción 91.7 %. no cumple (el duelo no lo midió).
- bridge: HAZARD_DEATHS_MOB_PER_HOUR off 36000.0, guard 1469.4, reducción 95.9 %. no cumple (el duelo no lo midió).
- FAIRNESS_VIOLATIONS: 0 de 200 decisiones letales MEDIUM contra un jugador, sin entropía. no cumple (el duelo no lo midió).
- Supervivencia de guard frente a off en cada terreno: no cumple (el duelo no lo midió).
- G1 de M22 por rival: no cumple. El duelo no se volvió a correr; el simulador no tiene terreno.
- flat: muertes off/guard/full 0/0/0. Muertes idénticas: no cumple (el duelo no lo midió). Supervivencia de full y guard: no cumple (el duelo no lo midió). Paridad byte a byte del duelo plano: no medida, no cumple.
- ZONE_FACT_CHANGES_PER_MINUTE en full: 0. Esta matriz no emite hechos. no cumple (el duelo no lo midió; el tope es 2).
- Activación del default a guard (E7): no cumple. Faltan las sesiones 24.5 y 24.7. zone.mode entregado: observe. No se pasa a guard.

Medido en 0.043 s en esta máquina.
