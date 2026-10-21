package akyrion.thepredict.engine.duel;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Sizes, seeds and switches of one run of the effectiveness bench. {@link #full} follows §7 of the simulator audit:
 * 200 seeds per cell (400 rounds, both sides), the 29 cells of each ThePredict configuration (7 scenarios with skills
 * against 4 rivals, and the vanilla one), the searches of §7.3 and the 24 cells of the sensitivity to Jev for each
 * configuration. {@link #quick} is the smoke size that runs inside {@code check}.
 *
 * @param seeds seeds per cell
 * @param jevSeeds seeds per cell of the sensitivity to Jev
 * @param gateSeeds seeds of the mirror gates
 * @param armRounds rounds per arm of the re-measured matrix A to D
 * @param threads threads
 * @param search whether the rivals come from the frozen files or from a new search
 * @param freeze whether a search writes its winners into {@code frozen}
 * @param frozen the folder of the frozen rivals
 * @param sizes the sizes of the searches
 * @param kits the kits
 * @param scenarios the scenarios of the cells
 * @param timings the ThePredict configurations of the cells
 * @param jevTimings the ThePredict configurations of the sensitivity to Jev
 * @param accuracies the accuracies of the judge, {@link OracleJev#UNIFORM} for one over the options
 * @param latencies the latencies of the judge
 * @param firstSeed the first seed of the measured cells: {@link BenchRunner#EVALUATION_SEED} by default, or a set of
 *     development seeds ({@link BenchRunner#DEVELOPMENT_SEED}) so a change is tuned away from the seeds that judge it
 */
record BenchSettings(int seeds, int jevSeeds, int gateSeeds, int armRounds, int threads, Search search,
                     boolean freeze, Path frozen, ClassicSearch.Sizes sizes, List<Kit> kits, List<Scenario> scenarios,
                     List<BenchProfiles.Timing> timings, List<BenchProfiles.Timing> jevTimings, double[] accuracies,
                     List<DuelSimulator.Mode.Latency> latencies, long firstSeed) {
    /** The criterion of the user: the lower bound of every cell is at least this. */
    static final double GOAL = 0.75;
    /** The level of a family of cells, split with Bonferroni. */
    static final double ALPHA = 0.05;
    /** The seed of the judge of the sensitivity to Jev. */
    static final long JUDGE_SEED = 20_261_014L;

    /** Where the rivals come from. */
    enum Search {
        /** From the frozen files when they still match; otherwise a new search. */
        AUTO,
        /** Always a new search. */
        FORCE,
        /** Only from the frozen files. */
        NEVER
    }

    /** The full run of §7. */
    static BenchSettings full(Path frozen, int threads) {
        return new BenchSettings(200, 200, 200, 400, threads, Search.AUTO, false, frozen, ClassicSearch.Sizes.FULL,
                Kit.ALL, Scenario.gateSet(), List.of(BenchProfiles.Timing.DEFAULTS, BenchProfiles.Timing.PACK),
                List.of(BenchProfiles.Timing.DEFAULTS, BenchProfiles.Timing.PACK),
                new double[] {OracleJev.UNIFORM, 0.4, 0.55, 0.7, 0.85, 1.0},
                List.of(DuelSimulator.Mode.Latency.MEASURED, DuelSimulator.Mode.Latency.NONE),
                BenchRunner.EVALUATION_SEED);
    }

    /** The smoke run of {@code check}: the duel pack only, a handful of seeds, tiny searches, never frozen files. */
    static BenchSettings quick(Path frozen) {
        var sizes = new ClassicSearch.Sizes(new int[] {1, 1, 1, 2}, 0.25, 3, 3, 6, 1, 3, 2, 2, 2, 24);
        return new BenchSettings(3, 2, 4, 4, 2, Search.FORCE, false, frozen, sizes, List.of(Kit.K1),
                List.of(Scenario.bench(Kit.K1, Arena.OPEN18, 60)), List.of(BenchProfiles.Timing.DEFAULTS),
                List.of(BenchProfiles.Timing.DEFAULTS), new double[] {OracleJev.UNIFORM, 1.0},
                List.of(DuelSimulator.Mode.Latency.MEASURED), BenchRunner.EVALUATION_SEED);
    }

    /**
     * The full run with the system properties of the Gradle task. {@code thepredict.bench.firstSeed} moves the cells
     * to another set of seeds ({@code dev} names {@link BenchRunner#DEVELOPMENT_SEED}); without it they keep the
     * evaluation seeds.
     */
    static BenchSettings fromProperties() {
        var full = full(Path.of(System.getProperty("thepredict.bench.frozen", "src/test/resources/eval")),
                Integer.getInteger("thepredict.bench.threads", 4));
        var seeds = Integer.getInteger("thepredict.bench.seeds", full.seeds());
        var search = Search.valueOf(System.getProperty("thepredict.bench.search", "auto").toUpperCase(Locale.ROOT));
        var freeze = Boolean.getBoolean("thepredict.bench.freeze");
        return new BenchSettings(seeds, Integer.getInteger("thepredict.bench.jevSeeds", seeds), full.gateSeeds(),
                full.armRounds(), full.threads(), search, freeze, full.frozen(), full.sizes(), full.kits(),
                full.scenarios(), full.timings(), full.jevTimings(), full.accuracies(), full.latencies(),
                firstSeed(System.getProperty("thepredict.bench.firstSeed", "")));
    }

    /**
     * The first seed of the cells for the value of {@code thepredict.bench.firstSeed}.
     *
     * @param value empty for the evaluation seeds, {@code dev} for the development seeds, or a number
     * @return the first seed
     * @throws IllegalArgumentException when a number falls inside the search seeds (1 to 10 000)
     */
    static long firstSeed(String value) {
        var text = value.strip();
        if (text.isEmpty()) {
            return BenchRunner.EVALUATION_SEED;
        }
        if (text.equalsIgnoreCase("dev")) {
            return BenchRunner.DEVELOPMENT_SEED;
        }
        var seed = Long.parseLong(text);
        if (seed <= BenchRunner.LAST_SEARCH_SEED) {
            throw new IllegalArgumentException("the first seed of the cells must lie above the search seeds: " + seed);
        }
        return seed;
    }

    /** Whether the cells play the evaluation seeds, the ones the verdict is read on. */
    boolean evaluation() {
        return firstSeed == BenchRunner.EVALUATION_SEED;
    }

    /** The name of a mode of the judge in the report. */
    static String modeName(DuelSimulator.Mode.Judge judge) {
        var accuracy = judge.pBest() < 0 ? "1/k" : String.format(Locale.ROOT, "%.2f", judge.pBest());
        var latency = judge.latency() == DuelSimulator.Mode.Latency.MEASURED ? "latencia medida" : "latencia 0";
        return "JEV(p=" + accuracy + ", " + latency + ")";
    }
}
