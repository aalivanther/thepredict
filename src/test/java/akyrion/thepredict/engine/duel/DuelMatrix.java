package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.eval.EvalStats;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The matrix of M18.3 in the simulator (M18.7): {@code run(arms, rounds, mode)} plays each round index in every arm,
 * the arms in the order A, B, C, D, D, C, B, A…, with the same seed for all arms and the spawn points swapped every two
 * rounds, and aggregates each arm with the mean, deviation, minimum and maximum of the round length and the damage,
 * and the Wilson interval at 95 % of the red score (a draw counts half). It never claims that one arm is better: it
 * measures (plan §12).
 */
final class DuelMatrix {
    private DuelMatrix() {
    }

    /** The aggregate of one arm. */
    record Summary(DuelSimulator.Arm arm, int rounds, int redWins, int blueWins, int draws, double redScore,
                   double[] wilson, Spread ticks, Spread redDealt, Spread blueDealt, Map<String, int[]> casts,
                   int retreats, Map<ControlLevel, Long> levelTicks, long requests, long tokens,
                   Map<String, double[]> tactics, long duplicateCasts) {
    }

    /** Mean, standard deviation, minimum and maximum of a series. */
    record Spread(double mean, double deviation, double minimum, double maximum) {
        static Spread of(List<Double> values) {
            var mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            var variance = values.stream().mapToDouble(value -> (value - mean) * (value - mean)).sum()
                    / Math.max(1, values.size() - 1);
            return new Spread(mean, Math.sqrt(variance), values.stream().mapToDouble(Double::doubleValue).min()
                    .orElse(0), values.stream().mapToDouble(Double::doubleValue).max().orElse(0));
        }
    }

    /** Plays {@code rounds} round indexes in every arm, alternating the order of the arms. */
    static Map<DuelSimulator.Arm, Summary> run(List<DuelSimulator.Arm> arms, int rounds, DuelSimulator.Mode mode) {
        var simulator = new DuelSimulator(mode);
        var results = new EnumMap<DuelSimulator.Arm, List<DuelResult>>(DuelSimulator.Arm.class);
        for (var index = 0; index < rounds; index++) {
            var order = index % 2 == 0 ? arms : arms.reversed();
            for (var arm : order) {
                results.computeIfAbsent(arm, key -> new ArrayList<>()).add(simulator.round(arm, index,
                        (index / 2) % 2 == 1, DuelSimulator.Options.PLAIN));
            }
        }
        var summaries = new EnumMap<DuelSimulator.Arm, Summary>(DuelSimulator.Arm.class);
        results.forEach((arm, list) -> summaries.put(arm, summary(arm, list)));
        return summaries;
    }

    static Summary summary(DuelSimulator.Arm arm, List<DuelResult> results) {
        var red = (int) results.stream().filter(result -> result.winner() == DuelResult.Winner.RED).count();
        var blue = (int) results.stream().filter(result -> result.winner() == DuelResult.Winner.BLUE).count();
        var draws = results.size() - red - blue;
        var score = results.stream().mapToDouble(DuelResult::redScore).sum();
        var casts = new TreeMap<String, int[]>();
        var levels = new EnumMap<ControlLevel, Long>(ControlLevel.class);
        var tactics = new TreeMap<String, double[]>();
        for (var result : results) {
            count(casts, result.redCasts(), 0);
            count(casts, result.blueCasts(), 1);
            count(casts, result.redHits(), 2);
            count(casts, result.blueHits(), 3);
            result.levelTicks().forEach((level, ticks) -> levels.merge(level, (long) ticks, Long::sum));
            result.tactics().forEach((tactic, cell) -> {
                var total = tactics.computeIfAbsent(tactic, key -> new double[2]);
                total[0] += cell[0];
                total[1] += cell[1];
            });
        }
        return new Summary(arm, results.size(), red, blue, draws, score / results.size(),
                wilson(score, results.size()), Spread.of(results.stream().map(result -> (double) result.ticks()).toList()),
                Spread.of(results.stream().map(DuelResult::redDealt).toList()),
                Spread.of(results.stream().map(DuelResult::blueDealt).toList()), casts,
                results.stream().mapToInt(DuelResult::retreats).sum(), levels,
                results.stream().mapToLong(DuelResult::requests).sum(), results.stream().mapToLong(DuelResult::tokens).sum(),
                tactics, results.stream().mapToLong(DuelResult::duplicateCasts).sum());
    }

    private static void count(Map<String, int[]> table, Map<String, Integer> values, int column) {
        values.forEach((skill, count) -> table.computeIfAbsent(skill, key -> new int[4])[column] += count);
    }

    /** The Wilson score interval at 95 %, the one of {@link EvalStats#wilson(double, int)} (SIM-20). */
    static double[] wilson(double successes, int trials) {
        return EvalStats.wilson(successes, trials);
    }

    /** The markdown table of a matrix. */
    static String markdown(String title, Map<DuelSimulator.Arm, Summary> matrix) {
        var out = new StringBuilder("## ").append(title).append("\n\n");
        out.append("| Arm | Rounds | Red wins | Blue wins | Draws | Red score | Wilson 95 % | Ticks mean (sd, min-max) "
                + "| Red dealt mean | Blue dealt mean | Retreats | Requests | Tokens | Duplicated casts |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        matrix.values().forEach(summary -> out.append(String.format(Locale.ROOT,
                "| %s | %d | %d | %d | %d | %.3f | [%.3f, %.3f] | %.0f (%.0f, %.0f-%.0f) | %.3f | %.3f | %d | %d | %d | %d |%n",
                summary.arm(), summary.rounds(), summary.redWins(), summary.blueWins(), summary.draws(),
                summary.redScore(), summary.wilson()[0], summary.wilson()[1], summary.ticks().mean(),
                summary.ticks().deviation(), summary.ticks().minimum(), summary.ticks().maximum(),
                summary.redDealt().mean(), summary.blueDealt().mean(), summary.retreats(), summary.requests(),
                summary.tokens(), summary.duplicateCasts())));
        out.append("\n| Arm | Skill | Red casts | Blue casts | Red hits | Blue hits |\n|---|---|---|---|---|---|\n");
        matrix.values().forEach(summary -> summary.casts().forEach((skill, cell) -> out.append(String.format(
                Locale.ROOT, "| %s | %s | %d | %d | %d | %d |%n", summary.arm(), skill, cell[0], cell[1], cell[2],
                cell[3]))));
        out.append("\n| Arm | JEV ticks | ALTERNATIVE ticks | LOCAL ticks | BASE ticks |\n|---|---|---|---|---|\n");
        matrix.values().forEach(summary -> out.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d |%n",
                summary.arm(), summary.levelTicks().getOrDefault(ControlLevel.JEV, 0L),
                summary.levelTicks().getOrDefault(ControlLevel.ALTERNATIVE, 0L),
                summary.levelTicks().getOrDefault(ControlLevel.LOCAL, 0L),
                summary.levelTicks().getOrDefault(ControlLevel.BASE, 0L))));
        out.append("\n| Arm | Tactic | Windows | Mean effect |\n|---|---|---|---|\n");
        matrix.values().forEach(summary -> summary.tactics().forEach((tactic, cell) -> out.append(String.format(
                Locale.ROOT, "| %s | %s | %.0f | %.3f |%n", summary.arm(), tactic, cell[0],
                cell[0] == 0 ? 0 : cell[1] / cell[0]))));
        return out.append('\n').toString();
    }
}
