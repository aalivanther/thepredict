package akyrion.thepredict.engine.duel;

import akyrion.thepredict.eval.EvalStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The matrix A to D of M18.3 measured again (§7.8, step 1 of the simulator audit): the duel pack against the pack
 * classic, once in the simulator of M18.7 ({@code LEGACY}, the golden one) and once with the vanilla world and the fair
 * tick, with the profile of the pack in both. It shows what the order of the tick and the physics did to the old
 * figures; the cells of the bench do not read it.
 */
final class BenchArms {
    private static final List<DuelSimulator.Arm> ARMS = List.of(DuelSimulator.Arm.A, DuelSimulator.Arm.B,
            DuelSimulator.Arm.C, DuelSimulator.Arm.D);

    private BenchArms() {
    }

    /**
     * One arm of one world.
     *
     * @param world {@code LEGACY} or {@code VANILLA + FAIR}
     * @param arm the arm
     * @param rounds rounds
     * @param redScore the score of red, a draw counting half
     * @param wilson its Wilson interval at 95 %
     * @param draws draws
     * @param meanTicks mean length
     */
    record Row(String world, DuelSimulator.Arm arm, int rounds, double redScore, double[] wilson, int draws,
               double meanTicks) {
    }

    static List<Row> measure(BenchSettings settings, BenchRunner runner) {
        var rows = new ArrayList<Row>();
        var legacy = DuelMatrix.run(ARMS, settings.armRounds(), new DuelSimulator.Mode.None());
        for (var arm : ARMS) {
            var summary = legacy.get(arm);
            rows.add(new Row("LEGACY", arm, summary.rounds(), summary.redScore(), summary.wilson(), summary.draws(),
                    summary.ticks().mean()));
        }
        var scenario = Scenario.bench(Kit.K1, Arena.OPEN18, 60);
        var pack = ClassicConfig.pack(Kit.K1);
        var setup = new DuelSimulator.Setup(runner.physics(), runner.rules(), scenario, pack, pack);
        var profiles = runner.profiles(BenchProfiles.Timing.PACK, Kit.K1);
        for (var arm : ARMS) {
            var results = runner.pool().map(settings.armRounds(), index -> new DuelSimulator(
                    new DuelSimulator.Mode.None(), profiles, setup).round(arm, settings.firstSeed() + index,
                    (index / 2) % 2 == 1, DuelSimulator.Options.PLAIN));
            rows.add(row("VANILLA + FAIR", arm, results));
        }
        return rows;
    }

    private static Row row(String world, DuelSimulator.Arm arm, List<DuelResult> results) {
        var score = 0.0;
        var ticks = 0.0;
        var draws = 0;
        for (var result : results) {
            score += result.redScore();
            ticks += result.ticks();
            draws += result.winner() == DuelResult.Winner.DRAW ? 1 : 0;
        }
        return new Row(world, arm, results.size(), score / results.size(), EvalStats.wilson(score, results.size()),
                draws, ticks / results.size());
    }

    /** The rows by world, for the report. */
    static Map<String, List<Row>> byWorld(List<Row> rows) {
        var out = new java.util.LinkedHashMap<String, List<Row>>();
        for (var row : rows) {
            out.computeIfAbsent(row.world(), key -> new ArrayList<>()).add(row);
        }
        return out;
    }
}
