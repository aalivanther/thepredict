package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.SkillChoice;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bench is fair before it measures (§7.1): {@code legal-equal} casts in ThePredict's windows (SIM-01), every speed
 * modifier of ThePredict is one the classic can use (SIM-04, V5), and the pack classic of the bench is the one of the
 * duel pack.
 */
class BenchValidityTest {
    @Test
    void legalEqualClassicHasTheThePredictCastWindows() {
        var profile = DuelSimulator.packs().get("DuelRed");
        var equal = ClassicSpace.legalEqual(Kit.K1, profile.movement());
        for (var rule : equal.rules()) {
            var choice = profile.choices().values().stream().filter(found -> found.mythicSkillId()
                    .filter(rule.skill().id::equals).isPresent()).findFirst().orElseThrow();
            assertEquals(1, rule.timerTicks(), "every tick, like the tick layer: " + rule.skill().id);
            assertEquals(0, rule.notWithinBlocks(), "no minimum the profile does not have: " + rule.skill().id);
            if (rule.skill().damage > 0) {
                assertEquals(window(choice), rule.withinBlocks(), 1e-9, rule.skill().id);
                assertTrue(rule.awaitWindow(), "waits for the window as ImpactTiming does: " + rule.skill().id);
            }
        }
        var pack = ClassicConfig.pack(Kit.K1);
        assertEquals(4, pack.rules().stream().filter(rule -> rule.skill() == DuelSkill.BOLT).findFirst().orElseThrow()
                .notWithinBlocks(), "the pack classic keeps its minimum of 4 blocks");
    }

    /** The farthest distance between positions at which a damaging choice of the profile can cast. */
    private static double window(SkillChoice choice) {
        var purpose = choice.purpose().orElseThrow();
        var reach = purpose.effectiveReachBlocks().orElse(Double.MAX_VALUE);
        var skill = DuelSkill.of(choice.mythicSkillId().orElseThrow()).orElseThrow();
        var byReach = purpose.delivery() == Delivery.MELEE ? reach + DuelWorld.WIDTH : reach;
        return Math.min(byReach, skill.withinBlocks);
    }

    @Test
    void sameSpeedModifiersOnBothSides() {
        var movement = DuelSimulator.packs().get("DuelRed").movement();
        var equals = List.of(ClassicSpace.legalEqual(Kit.K1, movement), ClassicSpace.legalEqual(Kit.K2, movement));
        var gate = BenchGates.sameStats(Scenario.gateSet(), movement, equals);
        assertTrue(gate.passed(), gate.measured());
        var faster = new MovementSettings(movement.meleeReachBlocks(), movement.bandInnerBlocks(),
                movement.bandOuterBlocks(), movement.rangedReachBlocks(), movement.retreatDistanceBlocks(),
                movement.retreatSpeed(), 1.3, OptionalDouble.empty());
        assertFalse(BenchGates.sameStats(Scenario.gateSet(), faster, equals).passed(),
                "an approach the classic cannot match fails V5");
    }

    @Test
    void theGateSetHasEightScenariosAndTheirKitsLoad() {
        var scenarios = Scenario.gateSet();
        assertEquals(8, scenarios.size());
        for (var scenario : scenarios) {
            var points = scenario.spawnPoints(42L);
            assertEquals(-points[0], points[2], 1e-12, "symmetric around the centre");
            assertEquals(-points[1], points[3], 1e-12, "symmetric around the centre");
            assertTrue(scenario.arena().free(points[0], points[1]), scenario.id());
        }
    }
}
