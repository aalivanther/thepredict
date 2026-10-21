package akyrion.thepredict.engine.duel;

import akyrion.thepredict.eval.EvalGate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The report {@code banco.md} of the effectiveness bench, in Spanish (§7.0 of the simulator audit): the verdict first,
 * then the criterion fixed before measuring, the method, the validity gates, the rivals, every cell, the damage per
 * minute and the conduct of each cell, the sensitivity to Jev, the other sensitivities, the matrix A to D measured
 * again, the searches and the limits. Pure but for the measured time it states last.
 */
final class BenchReport {
    private BenchReport() {
    }

    /** Writes {@code banco.md} and the tables. */
    static void write(BenchOutcome outcome, Path output) throws IOException {
        BenchTables.write(outcome, output);
        Files.writeString(output.resolve("banco.md"), markdown(outcome), StandardCharsets.UTF_8);
    }

    static String markdown(BenchOutcome outcome) {
        var out = new StringBuilder("# Banco de efectividad v2: ThePredict contra la IA nativa y MythicMobs bien "
                + "configurado\n\n");
        out.append(verdict(outcome)).append(BenchText.question(outcome)).append(BenchText.method(outcome));
        out.append(gates(outcome)).append(rivals(outcome));
        for (var timing : outcome.settings().timings()) {
            out.append(cells(outcome, timing));
        }
        out.append(BenchDamage.section(outcome)).append(BenchConduct.section(outcome)).append(jev(outcome)).append(sensitivities(outcome))
                .append(arms(outcome)).append(searches(outcome)).append(BenchText.limits());
        out.append(BenchText.reproduce(outcome.settings()));
        return out.append(String.format(Locale.ROOT, "Medido en %.0f s en esta máquina.%n", outcome.seconds()))
                .toString();
    }

    private static String verdict(BenchOutcome outcome) {
        var out = new StringBuilder("## Veredicto\n\n");
        for (var timing : outcome.settings().timings()) {
            var family = outcome.cells().get(timing);
            var verdict = outcome.verdict(timing);
            out.append("- **").append(timing.label).append(", sin Jev:** ").append(words(verdict)).append('.');
            BenchOutcome.worst(family).ifPresent(worst -> out.append(" Peor celda: ").append(cellLine(worst,
                    BenchOutcome.z(family.size()))).append('.'));
            out.append('\n');
            if (outcome.settings().jevTimings().contains(timing)) {
                out.append(BenchText.jevVerdict(outcome, timing));
            }
        }
        BenchGates.failed(outcome.gates()).ifPresent(gate -> out.append("- **Puerta fallida:** ").append(gate.id())
                .append(" (").append(gate.what()).append("): ").append(gate.measured()).append(". Las celdas que "
                        + "dependen de ella no tienen veredicto.\n"));
        return out.append("\nLa pregunta del usuario se responde con los **defaults de entrega**: es lo que se "
                + "entrega. Un veredicto solo vale para este simulador; la evidencia en servidor sigue pendiente.\n\n")
                .toString();
    }

    static String words(String verdict) {
        return switch (verdict) {
            case EvalGate.PASS_SIM_ONLY, EvalGate.PASS -> "**cumple** (todas las celdas con cota inferior ≥ 0,75)";
            case EvalGate.FAIL -> "**no cumple** (alguna celda con cota inferior < 0,75)";
            default -> "**sin veredicto** (falló una puerta de validez)";
        };
    }

    static String cellLine(BenchCell.Result cell, double z) {
        return String.format(Locale.ROOT, "%s contra %s, %s, s = %.3f, cota %.3f (%d V, %d E, %d D)",
                cell.spec().scenario().id(), cell.spec().rival(), cell.spec().mode(), cell.score(), cell.lower(z),
                cell.wins(), cell.draws(), cell.losses());
    }

    private static String gates(BenchOutcome outcome) {
        var out = new StringBuilder("## Puertas de validez\n\nVan antes de leer ninguna celda; si una falla, las "
                + "celdas que dependen de ella no tienen veredicto.\n\n| Puerta | Qué | Umbral | Medido | ¿Pasa? |\n"
                + "|---|---|---|---|---|\n");
        for (var gate : outcome.gates()) {
            out.append("| ").append(gate.id()).append(" | ").append(gate.what()).append(" | ").append(gate.threshold())
                    .append(" | ").append(gate.measured()).append(" | ").append(gate.passed() ? "sí" : "**no**")
                    .append(" |\n");
        }
        return out.append('\n').toString();
    }

    private static String rivals(BenchOutcome outcome) {
        var out = new StringBuilder("## Rivales\n\n| Kit | Rival | Contra | Origen | Configuración (MythicMobs) | Huella "
                + "| Búsqueda |\n|---|---|---|---|---|---|---|\n");
        for (var rival : outcome.rivals()) {
            out.append("| ").append(rival.kit().id()).append(" | ").append(rival.name()).append(" | ")
                    .append(rival.timing().map(timing -> timing.label).orElse("todas")).append(" | ")
                    .append(rival.origin()).append(" | ").append(rival.config().describe()).append(" | `")
                    .append(rival.config().sha256(), 0, 12).append("` | ").append(Double.isNaN(rival.score()) ? "—"
                            : String.format(Locale.ROOT, "%.3f (semillas %s)", rival.score(), rival.seeds()))
                    .append(" |\n");
        }
        out.append("\nLos archivos de MythicMobs de cada rival están en `clasicos/`. Para `legal-best` la búsqueda da "
                + "su fuerza de Bradley–Terry en la liga final; para `adversarial`, la puntuación de ThePredict contra "
                + "él en semillas nuevas.\n\n");
        return out.toString();
    }

    private static String cells(BenchOutcome outcome, BenchProfiles.Timing timing) {
        var family = outcome.cells().get(timing);
        var z = BenchOutcome.z(family.size());
        var out = new StringBuilder(String.format(Locale.ROOT, "## Celdas: ThePredict con %s, sin Jev%n%n%d celdas, "
                + "z = %.3f (unilateral, Bonferroni sobre %d). TTK en segundos de las victorias de ThePredict. "
                + "Δ log TTK: frente al espejo del rival con la misma semilla, media y cota superior (bootstrap "
                + "unilateral al 95 %%).%n%n", timing.label, family.size(), z, family.size()));
        out.append("| Escenario | Rival | Rondas | V / E / D | s | Cota | ¿≥ 0,75? | TTK mediana [Q1; Q3] | Δ log TTK "
                + "| Daño recibido | Golpes propios perdidos | BASE | McNemar lados |\n"
                + "|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (var cell : family) {
            out.append(BenchText.cellRow(cell, z));
        }
        return out.append('\n').toString();
    }

    private static String jev(BenchOutcome outcome) {
        var out = new StringBuilder("## Sensibilidad a Jev\n\nJuez: `LookaheadOracle` (40 ticks contra el rival "
                + "congelado), nunca la puntuación del plan local. Con probabilidad p elige la mejor opción y, si no, "
                + "otra al azar; p = 1/k es el azar uniforme. Latencia medida: lognormal 179/228 ms con 1 % entre 330 "
                + "y 800 ms y 0,5 % sin respuesta (caduca y decide `LOCAL`); latencia 0 es el techo.\n\n");
        for (var timing : outcome.settings().jevTimings()) {
            out.append(BenchText.jevTable(outcome, timing));
        }
        return out.toString();
    }

    private static String sensitivities(BenchOutcome outcome) {
        if (outcome.sensitivities().isEmpty()) {
            return "";
        }
        var out = new StringBuilder("## Sensibilidades (fuera del veredicto)\n\nEn el servidor, la choice "
                + "`VANILLA_MELEE` golpea con `Mob.attack` (`CombatActionsAdapter`): a 2,5 bloques de hueco y sin "
                + "enfriamiento propio, solo la invulnerabilidad; el zombi nativo golpea a 1,43 bloques cada 20 ticks. "
                + "Las celdas K0 del veredicto usan lo del servidor; las de «golpe propio del zombi», el del zombi. "
                + "Las celdas con `retreat_and_heal` en `APPROACH` cambian una línea del perfil de entrega (no el "
                + "producto) para medir lo que cuesta retirarse mientras la cura recarga. Las de `Approach.Speed 1.2` "
                + "persiguen a la velocidad máxima del espacio legal en vez de 1,15 (decisión del usuario, E-4).\n\n"
                + "| Configuración | Escenario | Rival | Modo | Rondas | V / E / D | s | Cota (z de una celda) |\n"
                + "|---|---|---|---|---|---|---|---|\n");
        var z = BenchOutcome.z(1);
        for (var cell : outcome.sensitivities()) {
            out.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %d | %d / %d / %d | %.3f | %.3f |%n",
                    cell.spec().timing().label, cell.spec().scenario().id(), cell.spec().rival(), cell.spec().mode(),
                    cell.rounds(), cell.wins(), cell.draws(), cell.losses(), cell.score(), cell.lower(z)));
        }
        return out.append('\n').toString();
    }

    private static String arms(BenchOutcome outcome) {
        var out = new StringBuilder("## Matriz A–D medida otra vez (duel pack contra el clásico del paquete)\n\n"
                + "| Mundo | Brazo | Rondas | s rojo | Wilson 95 % | Empates | Ticks medios |\n"
                + "|---|---|---|---|---|---|---|\n");
        for (var row : outcome.arms()) {
            out.append(String.format(Locale.ROOT, "| %s | %s | %d | %.3f | [%.3f; %.3f] | %d | %.0f |%n", row.world(),
                    row.arm(), row.rounds(), row.redScore(), row.wilson()[0], row.wilson()[1], row.draws(),
                    row.meanTicks()));
        }
        return out.append("\nA y B: ThePredict de rojo y de azul; C: ThePredict contra sí mismo; D: el clásico "
                + "contra sí mismo. `LEGACY` es el simulador de M18.7 (dorado); `VANILLA + FAIR`, el del banco.\n\n")
                .toString();
    }

    private static String searches(BenchOutcome outcome) {
        if (outcome.searches().isEmpty()) {
            return "## Búsquedas\n\nNinguna: los rivales buscados salen de los archivos congelados, cuya huella "
                    + "coincide.\n\n";
        }
        var out = new StringBuilder("## Búsquedas\n\n");
        for (var search : outcome.searches()) {
            out.append("- **").append(search.title()).append(":**\n");
            for (var step : search.steps()) {
                out.append("  - ").append(ClassicSearch.describe(step)).append('\n');
            }
        }
        return out.append('\n').toString();
    }

    /** The cell without Jev of the main scenario against a rival, for {@code s(p) − s(NONE)}. */
    static Optional<BenchCell.Result> none(BenchOutcome outcome, BenchProfiles.Timing timing, String rival) {
        var main = Scenario.bench(Kit.K1, Arena.OPEN18, 60).id();
        return outcome.cells().getOrDefault(timing, List.of()).stream().filter(cell -> cell.spec().rival()
                .equals(rival) && cell.spec().scenario().id().equals(main)).findFirst();
    }
}
