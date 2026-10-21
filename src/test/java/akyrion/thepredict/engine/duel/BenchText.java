package akyrion.thepredict.engine.duel;

import java.util.List;
import java.util.Locale;

/**
 * The fixed texts and the longer rows of {@code banco.md}: the question and the criterion, the method, the verdict
 * with Jev, its table, a row of a cell, the limits and how to reproduce the run.
 */
final class BenchText {
    private BenchText() {
    }

    static String question(BenchOutcome outcome) {
        var cells = outcome.cells().values().stream().mapToInt(List::size).max().orElse(0);
        return String.format(Locale.ROOT, "## Pregunta y criterio (fijados antes de medir)%n%n"
                + "- **Pregunta.** Sin Jev, ¿gana ThePredict al menos el 75 %% contra un zombi vanilla con el mismo "
                + "equipo, contra el clásico del paquete, contra el clásico con sus mismas ventanas y velocidades "
                + "(`legal-equal`), contra el mejor clásico legal (`legal-best`) y contra el clásico legal buscado "
                + "contra él (`adversarial`)?%n"
                + "- **Celda.** Un rival en un escenario: %d semillas × los dos lados = %d rondas. Victoria 1, empate "
                + "½, derrota 0.%n"
                + "- **Criterio.** En cada celda, la cota inferior de Wilson unilateral al 95 %% con Bonferroni sobre "
                + "las %d celdas de la configuración es ≥ 0,75, y todas las puertas de validez pasan. Se publica pase "
                + "o no.%n%n", outcome.settings().seeds(), 2 * outcome.settings().seeds(), cells);
    }

    static String method(BenchOutcome outcome) {
        var settings = outcome.settings();
        return String.format(Locale.ROOT, "## Método%n%n"
                + "- Simulador de duelo con el motor real de ThePredict (`DuelEngine`), mundo `VANILLA` y tick `FAIR` "
                + "(§7.2): los dos lados deciden sobre la misma foto, se mueven a la vez, el orden del commit se sortea "
                + "por tick y los efectos de un tick caen juntos (dos golpes letales son empate); invulnerabilidad "
                + "vanilla con exceso, empuje de 0,4 bloques, salto en vuelo de 8 ticks, giro de 90° por tick, "
                + "rozamiento 0,546 y modificadores cuadráticos (PLAUSIBLE, sin `javap`).%n"
                + "- Puertos del motor como en el servidor: tendencia y alcance del rival, línea de visión con pilares, "
                + "golpes y muertes como eventos, progreso real y espera de `ImpactTiming`.%n"
                + "- Cada semilla sortea con `StableRandom` el ángulo y la distancia de aparición (simétrica, 8 a 24 "
                + "bloques), la fase global de los temporizadores, la fase del reloj del motor y la paridad de "
                + "selección de metas de cada mob. Semillas %s desde %d; las búsquedas usan 1 a 10 000 y "
                + "las puertas desde %d.%n"
                + "- ThePredict se mide con dos perfiles generados desde el kit y cargados con el `ProfileLoader` real: "
                + "**perfil del paquete** (`Timing: TickLayer: casts, Cadence: standard`) y **defaults de entrega** "
                + "(sin `Timing`). En nivel 4 juega el clásico del paquete de su kit, nunca el del rival.%n"
                + "- Una ronda nueva, con su motor, por cada partida: nada pasa de una ronda a otra. %d hilos.%n%n",
                settings.evaluation() ? "de evaluación" : "de desarrollo (no dan el veredicto)", settings.firstSeed(),
                BenchRunner.GATE_SEED, settings.threads());
    }

    static String jevVerdict(BenchOutcome outcome, BenchProfiles.Timing timing) {
        var family = outcome.jev(timing);
        var z = BenchOutcome.z(family.size());
        var out = new StringBuilder();
        for (var latency : outcome.settings().latencies()) {
            var at = family.stream().filter(cell -> cell.spec().judge().filter(judge -> judge.latency() == latency
                    && Math.abs(judge.pBest() - 0.85) < 1e-9).isPresent()).toList();
            if (at.isEmpty()) {
                continue;
            }
            var verdict = BenchOutcome.verdict(at, outcome.gatesPassed(timing));
            out.append("- **").append(timing.label).append(", con Jev simulado (p = 0,85, ")
                    .append(latency == DuelSimulator.Mode.Latency.MEASURED ? "latencia medida" : "latencia 0")
                    .append("):** ").append(BenchReport.words(verdict)).append('.');
            BenchOutcome.worst(at).ifPresent(worst -> out.append(" Peor celda: ").append(BenchReport.cellLine(worst,
                    z)).append('.'));
            out.append(" p* = ").append(pStar(outcome, timing, latency)).append(".\n");
        }
        return out.toString();
    }

    /** The smallest accuracy whose cells against every searched rival have their lower bound at the goal. */
    static String pStar(BenchOutcome outcome, BenchProfiles.Timing timing, DuelSimulator.Mode.Latency latency) {
        var family = outcome.jev(timing);
        var z = BenchOutcome.z(family.size());
        for (var accuracy : outcome.settings().accuracies()) {
            var cells = family.stream().filter(cell -> cell.spec().judge().filter(judge -> judge.latency() == latency
                    && Double.compare(judge.pBest(), accuracy) == 0).isPresent()).toList();
            if (!cells.isEmpty() && cells.stream().allMatch(cell -> cell.lower(z) >= BenchSettings.GOAL)) {
                return accuracy < 0 ? "1/k" : String.format(Locale.ROOT, "%.2f", accuracy);
            }
        }
        return "ninguna p medida";
    }

    static String jevTable(BenchOutcome outcome, BenchProfiles.Timing timing) {
        var family = outcome.jev(timing);
        var z = BenchOutcome.z(family.size());
        var out = new StringBuilder(String.format(Locale.ROOT, "### %s%n%n%d celdas, z = %.3f. p* con latencia medida: "
                + "%s; con latencia 0: %s.%n%n| Rival | Modo | Rondas | V / E / D | s(p) | Cota | ¿≥ 0,75? | s(p) − "
                + "s(NONE) | Peticiones por ronda |%n|---|---|---|---|---|---|---|---|---|%n", timing.label,
                family.size(), z, pStar(outcome, timing, DuelSimulator.Mode.Latency.MEASURED),
                pStar(outcome, timing, DuelSimulator.Mode.Latency.NONE)));
        for (var cell : family) {
            var none = BenchReport.none(outcome, timing, cell.spec().rival());
            out.append(String.format(Locale.ROOT, "| %s | %s | %d | %d / %d / %d | %.3f | %.3f | %s | %s | %.1f |%n",
                    cell.spec().rival(), cell.spec().mode(), cell.rounds(), cell.wins(), cell.draws(), cell.losses(),
                    cell.score(), cell.lower(z), cell.lower(z) >= BenchSettings.GOAL ? "sí" : "**no**",
                    none.map(found -> String.format(Locale.ROOT, "%+.3f", cell.score() - found.score())).orElse("—"),
                    cell.meanRequests()));
        }
        return out.append('\n').toString();
    }

    static String cellRow(BenchCell.Result cell, double z) {
        var ttk = BenchCell.quartiles(cell.winTicks());
        var ratios = cell.logRatios();
        return String.format(Locale.ROOT, "| %s | %s | %d | %d / %d / %d | %.3f | %.3f | %s | %.1f [%.1f; %.1f] | %s "
                        + "| %.3f | %.2f | %s | %.3f |%n", cell.spec().scenario().id(), cell.spec().rival(),
                cell.rounds(), cell.wins(), cell.draws(), cell.losses(), cell.score(), cell.lower(z),
                cell.lower(z) >= BenchSettings.GOAL ? "sí" : "**no**", ttk[1] / 20, ttk[0] / 20, ttk[2] / 20,
                ratios.length == 0 ? "—" : String.format(Locale.ROOT, "%+.3f (≤ %+.3f)", BenchTables.mean(ratios),
                        BenchTables.upper(ratios)), cell.meanTaken(), cell.meanLostHits(),
                BenchTables.percent(cell.baseShare()), cell.sides());
    }

    static String limits() {
        return "## Límites\n\n"
                + "- Es un simulador. La física `VANILLA` (velocidad, giro, empuje, vuelo del salto) es PLAUSIBLE: no "
                + "se verificó con `javap` porque el JAR de Paper es un parche. Se publica también la matriz A–D en "
                + "`LEGACY`.\n"
                + "- El golpe vanilla de 0 del clásico no abre ventana de invulnerabilidad ni empuja (SIM-19, "
                + "pendiente de DT-40 e en el servidor).\n"
                + "- El aura que hace esperar al clásico su propia ventana supone que una aplicación posterior no "
                + "acorta la anterior (PLAUSIBLE; comprobar en juego).\n"
                + "- Un espejo clásico idéntico acaba siempre en empate: la distancia es común y las reglas son "
                + "simétricas. V1 confirma que no queda sesgo de orden, no que haya ruido.\n"
                + "- El rival se busca en un espacio legal amplio pero finito (temporizadores, distancias, vida, "
                + "velocidades, huida, banda y aura). Un autor puede escribir cosas fuera de él.\n"
                + "- Jev real no se mide: el juez es un oráculo de anticipación de 40 ticks. p* dice qué precisión "
                + "necesitaría Jev, no cuál tiene.\n"
                + "- La evidencia en servidor (G4 de M22: TTK y casts por minuto frente al simulador) sigue "
                + "pendiente.\n\n";
    }

    static String reproduce(BenchSettings settings) {
        return String.format(Locale.ROOT, "## Reproducción%n%n```%nJAVA_HOME=…/temurin-25.jdk/Contents/Home "
                + "./gradlew effectivenessBench --offline%n```%n%nPropiedades: `-Pseeds=` (%d), `-Pthreads=` (%d), "
                + "`-Psearch=auto|force|never`, `-Pfreeze=true` para escribir los ganadores en `%s`.%n%n",
                settings.seeds(), settings.threads(), settings.frozen());
    }
}
