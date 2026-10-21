package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.engine.DecisionTap;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The progress the simulator reports to {@code ProgressWatch} (SIM-21): with the ports of a server the movement goal
 * runs only while the goal the body selected wants to move at this distance, as a native goal stops when its condition
 * fails; with the ports of M18.7 it always runs.
 */
class DuelControlTest {
    private static boolean running(TickRules rules, Motion.Steer goal, double gap) {
        var engine = new DuelEngine(new DuelSimulator.Mode.None(), new DecisionTap() {
        }, rules, Kit.K1);
        var red = new DuelWorld.Body(new UUID(7, 1), DuelWorld.Side.RED, -gap / 2, 0, 60);
        var blue = new DuelWorld.Body(new UUID(7, 2), DuelWorld.Side.BLUE, gap / 2, 0, 60);
        engine.round(new DuelWorld(red, blue, hit -> {
        }, DuelPhysics.VANILLA, rules, Arena.OPEN18, 0), (body, skill) -> {
        });
        red.goal = goal;
        return new DuelControl(engine).progress(new MobKey(red.id, 0)).movementGoalRunning();
    }

    @Test
    void theGoalRunsOnlyWhileItWantsToMoveWithTheServerPorts() {
        var approach = Motion.Steer.toward(1.15, 2.5);
        assertTrue(running(TickRules.FAIR, approach, 10), "far from the rival the approach runs");
        assertFalse(running(TickRules.FAIR, approach, 2), "within reach the approach stops");
        assertFalse(running(TickRules.FAIR, Motion.Steer.HOLD, 10), "a hold never runs");
        assertTrue(running(TickRules.FAIR, Motion.Steer.away(1.2, 8), 4), "a retreat runs while too close");
        assertTrue(running(TickRules.LEGACY, Motion.Steer.HOLD, 10), "M18.7: the goal always runs");
        assertTrue(running(TickRules.LEGACY, approach, 2), "M18.7: the goal always runs");
    }
}
