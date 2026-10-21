package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.engine.RequestBudget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The totals of a duel recording, counted in memory while it records and written once as {@code summary.md} (M18.4):
 * per arm, the wins of each side with their Wilson interval at 95 % (a draw counts half a win for each side), the time to
 * kill, the damage, the casts, hits and misses of each skill, the retreats, the guard casts, the time of the ThePredict
 * duelists at each control level, the outcome windows of each tactic, the requests and the input tokens with their
 * cost. Package-private; server thread only; bounded by the arms, the counted skills, the tactics of the profiles and
 * the 500 rounds of a recording.
 */
final class DuelSummary {
    /** Normal quantile of the 95 % Wilson interval. */
    static final double Z_95 = 1.959963984540054;
    /** Ticks between two samples. */
    static final int SAMPLE_TICKS = 5;

    /** Totals of one arm. */
    private static final class Arm {
        private int rounds;
        private int redWins;
        private int blueWins;
        private int draws;
        private int unfinished;
        private final List<Long> killTicks = new ArrayList<>();
        private double redDealt;
        private double blueDealt;
        private final Map<String, long[]> skills = new TreeMap<>();
        private final Map<String, Long> retreats = new TreeMap<>();
        private long guards;
        private final long[] levelTicks = new long[ControlLevel.values().length];
        private final Map<String, double[]> tactics = new TreeMap<>();
        private long requests;
        private long inputTokens;
    }

    private final Map<String, Arm> arms = new TreeMap<>();

    void ended(DuelRounds.Ended ended) {
        var arm = arm(ended.round().arm());
        if (ended.winner().equals("unfinished")) {
            arm.unfinished++;
            return;
        }
        arm.rounds++;
        switch (ended.winner()) {
            case "red" -> arm.redWins++;
            case "blue" -> arm.blueWins++;
            default -> arm.draws++;
        }
        if (ended.endReason().equals("death")) {
            arm.killTicks.add(ended.ticks());
        }
        arm.redDealt += ended.redDealt();
        arm.blueDealt += ended.blueDealt();
    }

    void cast(String arm, String skill) {
        arm(arm).skills.computeIfAbsent(skill, key -> new long[2])[0]++;
    }

    void hit(String arm, String skill) {
        arm(arm).skills.computeIfAbsent(skill, key -> new long[2])[1]++;
    }

    void retreat(String arm, String source) {
        arm(arm).retreats.merge(source, 1L, Long::sum);
    }

    void guard(String arm) {
        arm(arm).guards++;
    }

    void level(String arm, ControlLevel level) {
        arm(arm).levelTicks[level.ordinal()] += SAMPLE_TICKS;
    }

    void outcome(String arm, String tactic, double effect) {
        var cell = arm(arm).tactics.computeIfAbsent(tactic, key -> new double[2]);
        cell[0]++;
        cell[1] += effect;
    }

    void request(String arm) {
        arm(arm).requests++;
    }

    void tokens(String arm, long tokens) {
        arm(arm).inputTokens += tokens;
    }

    /** Requests sent about the duelists since the recording started, every arm together. */
    long requests() {
        return arms.values().stream().mapToLong(arm -> arm.requests).sum();
    }

    /** Input tokens of the answers since the recording started, every arm together. */
    long inputTokens() {
        return arms.values().stream().mapToLong(arm -> arm.inputTokens).sum();
    }

    /** The markdown of {@code summary.md}, after the header lines the recorder gives. */
    String render(List<String> header) {
        var out = new StringBuilder();
        header.forEach(line -> out.append(line).append('\n'));
        section(out, "Rounds by arm (an unfinished round, cut by the stop, counts in no rate)", "| Arm | Rounds | Unfinished | Red wins | Blue wins | Draws | Red rate (draw = 1/2) | Wilson 95 % "
                + "| Blue rate | Wilson 95 % | Time to kill, median (IQR) s | Red dealt, health fractions | "
                + "Blue dealt, health fractions |");
        arms.forEach((name, arm) -> out.append(rounds(name, arm)).append('\n'));
        section(out, "Casts, hits and misses per skill", "| Arm | Skill | Casts | Hits | Misses |");
        arms.forEach((name, arm) -> arm.skills.forEach((skill, cell) -> out.append(row(name, skill, cell[0], cell[1],
                Math.max(0, cell[0] - cell[1]))).append('\n')));
        section(out, "Retreats and guards", "| Arm | Retreats (intent) | Retreats (motion) | Guard casts |");
        arms.forEach((name, arm) -> out.append(row(name, arm.retreats.getOrDefault("INTENT", 0L),
                arm.retreats.getOrDefault("MOTION", 0L), arm.guards)).append('\n'));
        section(out, "Time per control level of the ThePredict duelists, s", "| Arm | JEV | ALTERNATIVE | LOCAL | BASE |");
        arms.forEach((name, arm) -> out.append(row(name, seconds(arm.levelTicks[0]), seconds(arm.levelTicks[1]),
                seconds(arm.levelTicks[2]), seconds(arm.levelTicks[3]))).append('\n'));
        section(out, "Outcome windows per tactic", "| Arm | Tactic | Windows | Mean effect |");
        arms.forEach((name, arm) -> arm.tactics.forEach((tactic, cell) -> out.append(row(name, tactic, (long) cell[0],
                DuelRows.number(cell[1] / cell[0]))).append('\n')));
        section(out, "Requests and tokens", "| Arm | Requests | Input tokens | Cost, USD |");
        arms.forEach((name, arm) -> out.append(row(name, arm.requests, arm.inputTokens,
                DuelRows.number(arm.inputTokens * RequestBudget.USD_PER_MILLION_INPUT_TOKENS / 1_000_000))).append('\n'));
        return out.toString();
    }

    private static String rounds(String name, Arm arm) {
        var red = arm.redWins + arm.draws / 2.0;
        var blue = arm.blueWins + arm.draws / 2.0;
        return row(name, arm.rounds, arm.unfinished, arm.redWins, arm.blueWins, arm.draws, rate(red, arm.rounds),
                interval(red, arm.rounds), rate(blue, arm.rounds), interval(blue, arm.rounds), killTime(arm.killTicks),
                DuelRows.number(arm.redDealt), DuelRows.number(arm.blueDealt));
    }

    private static void section(StringBuilder out, String title, String header) {
        out.append('\n').append("## ").append(title).append("\n\n").append(header).append('\n')
                .append(header.replaceAll("[^|]+", "---")).append('\n');
    }

    private static String row(Object... cells) {
        var out = new StringBuilder("|");
        for (var cell : cells) {
            out.append(' ').append(cell).append(" |");
        }
        return out.toString();
    }

    private Arm arm(String name) {
        return arms.computeIfAbsent(name, key -> new Arm());
    }

    private static String rate(double successes, int trials) {
        return trials == 0 ? "-" : DuelRows.number(successes / trials);
    }

    private static String interval(double successes, int trials) {
        if (trials == 0) {
            return "-";
        }
        var bounds = wilson(successes, trials);
        return String.format(Locale.ROOT, "[%s, %s]", DuelRows.number(bounds[0]), DuelRows.number(bounds[1]));
    }

    /** The Wilson score interval at 95 % of {@code successes} over {@code trials}; a draw may count half. */
    static double[] wilson(double successes, int trials) {
        var p = successes / trials;
        var z2 = Z_95 * Z_95;
        var centre = (p + z2 / (2.0 * trials)) / (1 + z2 / trials);
        var margin = Z_95 * Math.sqrt(p * (1 - p) / trials + z2 / (4.0 * trials * trials)) / (1 + z2 / trials);
        return new double[] {Math.max(0, centre - margin), Math.min(1, centre + margin)};
    }

    /** Median and interquartile range of the ticks to kill, in seconds, by linear interpolation. */
    static String killTime(List<Long> ticks) {
        if (ticks.isEmpty()) {
            return "-";
        }
        var sorted = ticks.stream().mapToDouble(value -> value / 20.0).sorted().toArray();
        return DuelRows.number(quantile(sorted, 0.5)) + " (" + DuelRows.number(quantile(sorted, 0.75)
                - quantile(sorted, 0.25)) + ")";
    }

    private static double quantile(double[] sorted, double q) {
        var position = q * (sorted.length - 1);
        var lower = (int) Math.floor(position);
        var upper = Math.min(sorted.length - 1, lower + 1);
        return sorted[lower] + (position - lower) * (sorted[upper] - sorted[lower]);
    }

    private static String seconds(long ticks) {
        return DuelRows.number(ticks / 20.0);
    }
}
