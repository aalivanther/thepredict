package akyrion.thepredict.engine.duel;

import akyrion.thepredict.eval.EvalStats;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mirrors of the fair round sit at one half (SIM-14, gates V1 and V2): ThePredict against itself (arm C) and the
 * classic against itself (arm D), 200 seeds from both spawn points, each within 0.05 of one half with one half inside
 * its Wilson interval. The round of M18.7 does not: there the engine casts first and the red session goes first.
 */
class DuelSymmetryTest {
    private static final int SEEDS = 200;

    private static double mirror(DuelSimulator.Arm arm, DuelSimulator.Setup setup) {
        var score = 0.0;
        for (var seed = 0; seed < SEEDS; seed++) {
            for (var swapped : new boolean[] {false, true}) {
                score += new DuelSimulator(new DuelSimulator.Mode.None(), Map.of(), setup)
                        .round(arm, BenchRunner.GATE_SEED + seed, swapped, DuelSimulator.Options.PLAIN).redScore();
            }
        }
        return score;
    }

    private static void fair(double score, String what) {
        var rounds = 2 * SEEDS;
        var interval = EvalStats.wilson(score, rounds);
        assertTrue(Math.abs(score / rounds - 0.5) <= 0.05, what + " " + score / rounds);
        assertTrue(interval[0] <= 0.5 && 0.5 <= interval[1], what + " Wilson " + interval[0] + " " + interval[1]);
    }

    @Test
    void mirrorCIsFiftyFifty() {
        var pack = ClassicConfig.pack(Kit.K1);
        fair(mirror(DuelSimulator.Arm.C, new DuelSimulator.Setup(DuelPhysics.VANILLA, TickRules.FAIR,
                Scenario.bench(Kit.K1, Arena.OPEN18, 60), pack, pack)), "C");
    }

    @Test
    void mirrorDIsFiftyFifty() {
        var pack = ClassicConfig.pack(Kit.K1);
        fair(mirror(DuelSimulator.Arm.D, new DuelSimulator.Setup(DuelPhysics.VANILLA, TickRules.FAIR,
                Scenario.bench(Kit.K1, Arena.OPEN18, 60), pack, pack)), "D");
    }
}
