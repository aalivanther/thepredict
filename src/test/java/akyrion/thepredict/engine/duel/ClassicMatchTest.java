package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The fast classic round of the searches is the fair round of arm D, seed for seed, so a search scores exactly what
 * the bench later plays; and a ThePredict duelist forced to level 4 plays exactly as the classic of its side (V6).
 */
class ClassicMatchTest {
    @Test
    void theMatchPlaysTheFairRoundOfArmD() {
        var space = new ClassicSpace(Kit.K1);
        var scenario = Scenario.bench(Kit.K1, Arena.PILLARS18, 60);
        for (var pair : List.of(new int[] {0, 1}, new int[] {77_000, 5}, new int[] {123, 45_678})) {
            var setup = new DuelSimulator.Setup(DuelPhysics.VANILLA, TickRules.FAIR, scenario, space.config(pair[0]),
                    space.config(pair[1]));
            for (var seed = 1L; seed <= 6; seed++) {
                var fair = new DuelSimulator(new DuelSimulator.Mode.None(), Map.of(), setup).round(DuelSimulator.Arm.D,
                        seed, seed % 2 == 0, DuelSimulator.Options.PLAIN);
                var match = ClassicMatch.play(setup, seed, seed % 2 == 0);
                assertEquals(fair.winner(), match.winner(), "seed " + seed);
                assertEquals(fair.ticks(), match.ticks(), "seed " + seed);
            }
        }
    }

    @Test
    void aThePredictDuelistForcedToLevelFourPlaysTheClassicOfItsSide() {
        var settings = BenchSettings.quick(java.nio.file.Path.of("build/tmp/unused-frozen"));
        try (var pool = new BenchPool(1)) {
            var runner = new BenchRunner(pool, DuelPhysics.VANILLA, TickRules.FAIR, Map.of(
                    BenchProfiles.Timing.DEFAULTS, Map.of(Kit.K1, DuelSimulator.packs())));
            var equal = ClassicSpace.legalEqual(Kit.K1, DuelSimulator.packs().get("DuelRed").movement());
            var gate = BenchGates.forcedBase(runner, settings.scenarios().getFirst(), BenchProfiles.Timing.DEFAULTS,
                    equal, 10);
            assertEquals(true, gate.passed(), gate.measured());
        }
    }
}
