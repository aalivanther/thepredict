package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The searches of the legal space (SIM-02): deterministic, the legal best beats the pack classic on seeds the search
 * never saw, and the adversarial classic is a configuration of the space, never worse for the classic than the best
 * of its starts on the training seeds.
 */
class ClassicSearchTest {
    private static final ClassicSearch.Sizes SMALL = new ClassicSearch.Sizes(new int[] {2, 2, 2, 4}, 0.25, 4, 4, 6, 1,
            3, 2, 3, 2, 64);

    private static BenchRunner runner(BenchPool pool) {
        return new BenchRunner(pool, DuelPhysics.VANILLA, TickRules.FAIR, Map.of(BenchProfiles.Timing.DEFAULTS,
                Map.of(Kit.K1, DuelSimulator.packs())));
    }

    @Test
    void theLegalBestIsDeterministicAndBeatsThePackOnReservedSeeds() {
        try (var pool = new BenchPool(2)) {
            var runner = runner(pool);
            var space = new ClassicSpace(Kit.K1);
            var scenario = Scenario.bench(Kit.K1, Arena.OPEN18, 60);
            var equal = ClassicSpace.legalEqual(Kit.K1, DuelSimulator.packs().get("DuelRed").movement());
            var first = ClassicSearch.legalBest(space, scenario, runner, equal, SMALL);
            var second = ClassicSearch.legalBest(space, scenario, runner, equal, SMALL);
            assertEquals(first.index(), second.index());
            assertEquals(first.steps().size(), 4);
            var reserved = ClassicSearch.versus(scenario, runner, first.winner(), ClassicConfig.pack(Kit.K1),
                    BenchRunner.EVALUATION_SEED, 20);
            assertTrue(reserved >= 0.5, "the legal best against the pack on unseen seeds: " + reserved);
        }
    }

    @Test
    void theAdversarialClassicLiesInTheSpaceAndRepeats() {
        try (var pool = new BenchPool(2)) {
            var runner = runner(pool);
            var space = new ClassicSpace(Kit.K1);
            var pack = ClassicConfig.pack(Kit.K1);
            var spec = new BenchCell.Spec("search", pack, Scenario.bench(Kit.K1, Arena.OPEN18, 60),
                    BenchProfiles.Timing.DEFAULTS, "NONE", Optional.empty());
            var starts = List.of(ClassicSpace.legalEqual(Kit.K1, DuelSimulator.packs().get("DuelRed").movement()));
            var first = ClassicSearch.adversarial(space, spec, runner, starts, SMALL);
            var second = ClassicSearch.adversarial(space, spec, runner, starts, SMALL);
            assertEquals(first.index(), second.index());
            assertEquals(first.winner().code(), space.config(first.index()).code());
            assertTrue(first.score() >= 0 && first.score() <= 1);
        }
    }
}
