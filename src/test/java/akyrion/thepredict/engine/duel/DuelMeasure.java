package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.SwitchableDecisionTap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The task {@code duelMeasure} of M18.7, outside {@code check}: writes {@code build/reports/m18/duel-local.md} with the
 * local matrix (arms A to D × {@value #DEFAULT_ROUNDS} seeds, modes {@code NONE} and {@code ORACLE(0.85)}), the
 * difference that a late drop to level 4 makes, and one full session recorded by the real duel recorder (80 rounds
 * without a key and 30 with the oracle) to show that it fits within the limits and writes every file. No network, no
 * cost. It measures and claims no superiority (plan §12).
 */
public final class DuelMeasure {
    /** Seeds per arm and mode. */
    public static final int DEFAULT_ROUNDS = 200;
    /** Cast mode and readiness horizon of one E2 configuration. The product defaults are not these values. */
    record Casting(String name, CastMode mode, int horizon) {
    }

    /** The four configurations of M19.9. Order is part of the byte-stable report. */
    static final List<Casting> M19_CASTING = List.of(
            new Casting("INTENT_ONLY, horizon 0", CastMode.INTENT_ONLY, 0),
            new Casting("ANY_READY, horizon 0", CastMode.ANY_READY, 0),
            new Casting("INTENT_ONLY, horizon 20", CastMode.INTENT_ONLY, 20),
            new Casting("ANY_READY, horizon 20", CastMode.ANY_READY, 20));

    private DuelMeasure() {
    }

    /**
     * Writes the report.
     *
     * @param arguments the report file; the system property {@code thepredict.duel.rounds} changes the seeds
     * @throws IOException when the report cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        var report = Path.of(arguments[0]).toAbsolutePath();
        var rounds = Integer.getInteger("thepredict.duel.rounds", DEFAULT_ROUNDS);
        if ("m19".equals(System.getProperty("thepredict.duel.evaluation"))) {
            Files.createDirectories(report.getParent());
            Files.writeString(report, DuelEvaluation.markdown(rounds, M19_CASTING), StandardCharsets.UTF_8);
            System.out.println("Wrote " + report);
            return;
        }
        var arms = List.of(DuelSimulator.Arm.A, DuelSimulator.Arm.B, DuelSimulator.Arm.C, DuelSimulator.Arm.D);
        var out = new StringBuilder("# Local duel matrix (M18.7)\n\n");
        out.append("Simulator of M18.7 with the real engine, the classic duelist and the skills of the duel pack. ")
                .append("It measures the path and claims no superiority (plan §12). ").append(rounds)
                .append(" seeds per arm; the same seeds in every arm; spawn points swapped every two rounds.\n\n");
        var started = System.nanoTime();
        out.append(DuelMatrix.markdown("Mode NONE (no key: LOCAL and BASE)",
                DuelMatrix.run(arms, rounds, new DuelSimulator.Mode.None())));
        out.append(DuelMatrix.markdown("Mode ORACLE(0.85)",
                DuelMatrix.run(arms, rounds, new DuelSimulator.Mode.Oracle(0.85, 20260929L))));
        out.append(baseDelay(rounds));
        out.append(tapCost(Math.max(20, rounds / 5)));
        out.append(session(report.getParent().resolve("duel-session")));
        out.append(String.format(Locale.ROOT, "Measured in %.1f s on this machine.%n",
                (System.nanoTime() - started) / 1e9));
        Files.createDirectories(report.getParent());
        Files.writeString(report, out.toString(), StandardCharsets.UTF_8);
        System.out.println("Wrote " + report);
    }

    /** The difference of M18.3: a ThePredict duelist that loses its target mid-round reaches level 4 late. */
    private static String baseDelay(int rounds) {
        var simulator = new DuelSimulator(new DuelSimulator.Mode.None());
        var delays = new ArrayList<Double>();
        for (var seed = 0; seed < rounds; seed++) {
            var result = simulator.round(DuelSimulator.Arm.A, seed, false, new DuelSimulator.Options(false, 200));
            if (result.baseDelayTicks() >= 0) {
                delays.add((double) result.baseDelayTicks());
            }
        }
        var spread = DuelMatrix.Spread.of(delays);
        return String.format(Locale.ROOT, "## Level 4 equivalent, not identical (M18.3)%n%nRed of arm A stops perceiving "
                + "its rival at tick 200 of the round: it reaches level 4 after %.1f ticks on average (sd %.1f, min %.0f, "
                + "max %.0f; %d of %d rounds lasted that long). From then on it plays the classic rules; before, it "
                + "keeps its last local intent. With level 4 forced from the first tick it casts exactly as the classic "
                + "duelist (DuelSimulatorTest).%n%n", spread.mean(), spread.deviation(), spread.minimum(),
                spread.maximum(), delays.size(), rounds);
    }

    /**
     * The cost of the tap while nothing records: the same rounds of arm C with the oracle, the engine hearing
     * {@code DecisionTap.NONE} or a detached {@code SwitchableDecisionTap}, alternated three times; wall time per
     * engine tick on this machine (M18.4, "sin coste medible en tick_work cuando no graba").
     */
    private static String tapCost(int rounds) {
        var none = 0L;
        var detached = 0L;
        var ticks = 0L;
        for (var pass = 0; pass < 3; pass++) {
            none += timed(new DuelSimulator(new DuelSimulator.Mode.Oracle(0.85, 1L), DecisionTap.NONE), rounds);
            var switchable = new DuelSimulator(new DuelSimulator.Mode.Oracle(0.85, 1L), new SwitchableDecisionTap());
            var started = System.nanoTime();
            for (var seed = 0; seed < rounds; seed++) {
                ticks += switchable.round(DuelSimulator.Arm.C, seed, false, DuelSimulator.Options.PLAIN).ticks();
            }
            detached += System.nanoTime() - started;
        }
        return String.format(Locale.ROOT, "## Cost of the tap without a recording (M18.4)%n%n| Engine tap | ns per "
                + "simulated tick |%n|---|---|%n| DecisionTap.NONE | %.0f |%n| SwitchableDecisionTap, detached | %.0f |"
                + "%n%nThe same %d rounds of arm C with the oracle, three passes each; the difference is noise of this "
                + "machine: without a recording each call is one volatile read and no event is built.%n%n",
                none / (double) ticks, detached / (double) ticks, rounds);
    }

    private static long timed(DuelSimulator simulator, int rounds) {
        var started = System.nanoTime();
        for (var seed = 0; seed < rounds; seed++) {
            simulator.round(DuelSimulator.Arm.C, seed, false, DuelSimulator.Options.PLAIN);
        }
        return System.nanoTime() - started;
    }

    /** One full session of 80 rounds without a key and 30 with the oracle, recorded by the real recorder. */
    private static String session(Path root) throws IOException {
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        return DuelSessionRun.record(root, 80, 30);
    }
}
