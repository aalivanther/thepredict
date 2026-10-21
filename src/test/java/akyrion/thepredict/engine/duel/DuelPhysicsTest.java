package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The movement of the vanilla world (SIM-09): a walking zombie settles at its terminal step, a speed modifier scales
 * the step by its square, a body turns at most 90° per tick and a goal is selected only on the parity of the body.
 */
class DuelPhysicsTest {
    private static DuelWorld walk(DuelPhysics physics, double speed) {
        var red = new DuelWorld.Body(new UUID(3, 0), DuelWorld.Side.RED, 0, 0);
        var blue = new DuelWorld.Body(new UUID(3, 1), DuelWorld.Side.BLUE, 0, 17);
        var world = new DuelWorld(red, blue, hit -> {
        }, physics, TickRules.FAIR, Arena.OPEN18, 0);
        Motion.face(world, red);
        red.goal = Motion.Steer.toward(speed, DuelWorld.STOP_DISTANCE);
        return world;
    }

    @Test
    void aWalkingZombieReachesItsTerminalSpeed() {
        var world = walk(DuelPhysics.VANILLA, 1.0);
        var before = 0.0;
        for (var tick = 0; tick < 10; tick++) {
            before = world.red.z;
            Motion.advance(world);
        }
        assertEquals(0.114, world.red.z - before, 0.005, "the step of the tenth tick");
        assertEquals(DuelPhysics.VANILLA.step(1.0), 0.1142, 1e-12);
    }

    @Test
    void aSpeedModifierScalesTheStepByItsSquare() {
        assertEquals(DuelPhysics.VANILLA.step(1.0) * 1.2 * 1.2, DuelPhysics.VANILLA.step(1.2), 1e-12);
        assertEquals(DuelPhysics.LEGACY.step(1.0) * 1.2, DuelPhysics.LEGACY.step(1.2), 1e-12);
        var fast = walk(DuelPhysics.VANILLA, 1.2);
        var slow = walk(DuelPhysics.VANILLA, 1.0);
        for (var tick = 0; tick < 30; tick++) {
            Motion.advance(fast);
            Motion.advance(slow);
        }
        assertEquals(1.44, fast.red.z / slow.red.z, 0.02);
    }

    @Test
    void aBodyTurnsAtMostNinetyDegreesPerTick() {
        var world = walk(DuelPhysics.VANILLA, 1.0);
        world.red.yaw = -Math.PI / 2;
        Motion.advance(world);
        assertEquals(0.0, world.red.yaw, 1e-9, "from facing away to sideways in one tick");
        Motion.advance(world);
        assertEquals(Math.PI / 2, world.red.yaw, 1e-9);
    }

    @Test
    void aGoalIsSelectedOnlyOnTheParityOfTheBody() {
        var world = walk(DuelPhysics.VANILLA, 1.0);
        world.red.goalPhase = 1;
        Motion.select(world, world.red, Motion.Steer.HOLD, 0);
        assertTrue(world.red.goal.active(17), "tick 0 is not a selection tick of an odd body");
        Motion.select(world, world.red, Motion.Steer.HOLD, 1);
        assertEquals(Motion.Steer.HOLD, world.red.goal);
    }
}
