package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A cell does not depend on the number of threads or on the order its rounds run in: every round builds its own
 * simulator, engine and world.
 */
class BenchPoolTest {
    @Test
    void oneThreadAndFourThreadsGiveTheSameCell() {
        var spec = new BenchCell.Spec("pack", ClassicConfig.pack(Kit.K1), Scenario.bench(Kit.K1, Arena.SMALL8, 60),
                BenchProfiles.Timing.DEFAULTS, "NONE", Optional.empty());
        var profiles = Map.of(BenchProfiles.Timing.DEFAULTS, Map.of(Kit.K1, DuelSimulator.packs()));
        BenchCell.Result one;
        BenchCell.Result four;
        try (var pool = new BenchPool(1)) {
            one = new BenchRunner(pool, DuelPhysics.VANILLA, TickRules.FAIR, profiles).cell(spec,
                    BenchRunner.EVALUATION_SEED, 6);
        }
        try (var pool = new BenchPool(4)) {
            four = new BenchRunner(pool, DuelPhysics.VANILLA, TickRules.FAIR, profiles).cell(spec,
                    BenchRunner.EVALUATION_SEED, 6);
        }
        assertEquals(one.a(), four.a());
        assertEquals(one.b(), four.b());
        assertEquals(java.util.Arrays.toString(one.mirrorTicks()), java.util.Arrays.toString(four.mirrorTicks()));
    }
}
