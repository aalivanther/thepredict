package akyrion.thepredict.engine.duel;

import akyrion.thepredict.eval.EvalStats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The tables of the effectiveness bench, tab separated with a header, and the MythicMobs files of every rival. Pure
 * functions of the outcome: the same run gives the same bytes ({@code EffectivenessBenchSmokeTest}).
 */
final class BenchTables {
    static final int BOOTSTRAP_DRAWS = 2000;
    static final long BOOTSTRAP_SEED = 20_261_014L;

    private BenchTables() {
    }

    /** Writes every table and the files of the rivals. */
    static void write(BenchOutcome outcome, Path output) throws IOException {
        Files.createDirectories(output);
        Files.writeString(output.resolve("celdas.tsv"), cells(outcome), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("puertas.tsv"), gates(outcome), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("rivales.tsv"), rivals(outcome), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("jev.tsv"), jev(outcome), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("busqueda.tsv"), searches(outcome), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("brazos.tsv"), arms(outcome), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("conducta.tsv"), BenchConduct.table(outcome), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("danio.tsv"), BenchDamage.table(outcome), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("habilidades.tsv"), BenchDamage.skills(outcome), StandardCharsets.UTF_8);
        var classics = output.resolve("clasicos");
        Files.createDirectories(classics);
        for (var rival : outcome.rivals()) {
            Files.writeString(classics.resolve(fileName(rival)), rival.config().toMythicYaml(), StandardCharsets.UTF_8);
        }
    }

    static String fileName(BenchRivals.Rival rival) {
        return rival.kit().id() + "-" + rival.name() + rival.timing().map(timing -> "-" + timing.name()
                .toLowerCase(Locale.ROOT)).orElse("") + ".yml";
    }

    static String cells(BenchOutcome outcome) {
        var out = new StringBuilder("configuracion\tescenario\trival\tmodo\trondas\tvictorias\tempates\tderrotas"
                + "\tpuntuacion\tz\tcota_inferior\tcota_superior\tcumple\tmcnemar_lados_p\tttk_q1\tttk_mediana\tttk_q3"
                + "\tdlog_ttk_media\tdlog_ttk_cota_sup\tdanio_recibido\tgolpes_propios_perdidos\tbase\tretiradas"
                + "\tpeticiones\n");
        for (var entry : outcome.cells().entrySet()) {
            var z = BenchOutcome.z(entry.getValue().size());
            for (var cell : entry.getValue()) {
                out.append(cellRow(entry.getKey().name(), cell, z)).append('\n');
            }
        }
        for (var cell : outcome.sensitivities()) {
            out.append(cellRow(cell.spec().timing().name() + "-SENSIBILIDAD", cell, BenchOutcome.z(1))).append('\n');
        }
        return out.toString();
    }

    static String cellRow(String timing, BenchCell.Result cell, double z) {
        var ttk = BenchCell.quartiles(cell.winTicks());
        var ratios = cell.logRatios();
        return String.format(Locale.ROOT, "%s\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%.4f\t%.3f\t%.4f\t%.4f\t%s\t%.4f\t%.1f"
                        + "\t%.1f\t%.1f\t%s\t%s\t%.4f\t%.3f\t%.4f\t%.3f\t%.2f", timing, cell.spec().scenario().id(),
                cell.spec().rival(), cell.spec().mode(), cell.rounds(), cell.wins(), cell.draws(), cell.losses(),
                cell.score(), z, cell.lower(z), cell.upper(z), cell.lower(z) >= BenchSettings.GOAL ? "si" : "no",
                cell.sides(), ttk[0], ttk[1], ttk[2], decimal(mean(ratios)), decimal(upper(ratios)),
                cell.meanTaken(), cell.meanLostHits(), cell.baseShare(), cell.meanRetreats(), cell.meanRequests());
    }

    static double mean(double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        var sum = 0.0;
        for (var value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    /** The one-sided 95 % bootstrap upper bound of a mean, NaN for an empty series. */
    static double upper(double[] values) {
        return values.length == 0 ? Double.NaN : EvalStats.bootstrapQuantile(values, BOOTSTRAP_DRAWS, BOOTSTRAP_SEED,
                0.95);
    }

    static String decimal(double value) {
        return Double.isNaN(value) ? "" : String.format(Locale.ROOT, "%.4f", value);
    }

    static String gates(BenchOutcome outcome) {
        var out = new StringBuilder("puerta\tque\tconfiguracion\tumbral\tmedido\tpasa\n");
        for (var gate : outcome.gates()) {
            out.append(gate.id()).append('\t').append(gate.what()).append('\t')
                    .append(gate.timing().map(Enum::name).orElse("todas")).append('\t').append(gate.threshold())
                    .append('\t').append(gate.measured()).append('\t').append(gate.passed() ? "si" : "no").append('\n');
        }
        return out.toString();
    }

    static String rivals(BenchOutcome outcome) {
        var out = new StringBuilder("kit\trival\tconfiguracion\torigen\tcodigo\tsha256\tpuntuacion_busqueda\tsemillas"
                + "\tdescripcion\n");
        for (var rival : outcome.rivals()) {
            out.append(rival.kit().id()).append('\t').append(rival.name()).append('\t')
                    .append(rival.timing().map(Enum::name).orElse("todas")).append('\t').append(rival.origin())
                    .append('\t').append(rival.config().code()).append('\t').append(rival.config().sha256())
                    .append('\t').append(decimal(rival.score())).append('\t').append(rival.seeds()).append('\t')
                    .append(rival.config().describe()).append('\n');
        }
        return out.toString();
    }

    static String jev(BenchOutcome outcome) {
        var out = new StringBuilder("configuracion\trival\tmodo\trondas\tvictorias\tempates\tderrotas\tpuntuacion\tz"
                + "\tcota_inferior\tcumple\tdelta_frente_a_none\tpeticiones\n");
        for (var timing : outcome.settings().jevTimings()) {
            var family = outcome.jev(timing);
            var z = BenchOutcome.z(family.size());
            for (var cell : family) {
                var none = BenchReport.none(outcome, timing, cell.spec().rival());
                out.append(String.format(Locale.ROOT, "%s\t%s\t%s\t%d\t%d\t%d\t%d\t%.4f\t%.3f\t%.4f\t%s\t%s\t%.2f%n",
                        timing.name(), cell.spec().rival(), cell.spec().mode(), cell.rounds(), cell.wins(),
                        cell.draws(), cell.losses(), cell.score(), z, cell.lower(z), cell.lower(z) >= BenchSettings.GOAL
                                ? "si" : "no", none.map(found -> decimal(cell.score() - found.score())).orElse(""),
                        cell.meanRequests()));
            }
        }
        return out.toString();
    }

    static String searches(BenchOutcome outcome) {
        var out = new StringBuilder("busqueda\tetapa\tcandidatos\trondas\tmejores\n");
        for (var search : outcome.searches()) {
            for (var step : search.steps()) {
                var best = new StringBuilder();
                for (var scored : step.best()) {
                    best.append(best.isEmpty() ? "" : " ").append(String.format(Locale.ROOT, "cfg-%d:%.4f",
                            scored.index(), scored.score()));
                }
                out.append(search.title()).append('\t').append(step.stage()).append('\t').append(step.candidates())
                        .append('\t').append(step.rounds()).append('\t').append(best).append('\n');
            }
        }
        return out.toString();
    }

    static String arms(BenchOutcome outcome) {
        var out = new StringBuilder("mundo\tbrazo\trondas\tpuntuacion_rojo\twilson_bajo\twilson_alto\tempates"
                + "\tticks_media\n");
        for (var row : outcome.arms()) {
            out.append(String.format(Locale.ROOT, "%s\t%s\t%d\t%.4f\t%.4f\t%.4f\t%d\t%.1f%n", row.world(), row.arm(),
                    row.rounds(), row.redScore(), row.wilson()[0], row.wilson()[1], row.draws(), row.meanTicks()));
        }
        return out.toString();
    }

    /** The share of a list, as a percentage with one decimal. */
    static String percent(double share) {
        return String.format(Locale.ROOT, "%.1f %%", share * 100);
    }
}
