package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MicroSide;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.domain.Steering;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicroGoalTest {
    @Test
    void theGoalMovesOnlyWhileItsGenerationControlsANonNativeMicro() {
        var view = new IntentView(2, ControlLevel.JEV, Movement.APPROACH, new TacticId("press_attack"), 0.8f,
                Long.MAX_VALUE).withMicro(MicroBehaviour.ORBIT, akyrion.thepredict.domain.MicroSide.LEFT);
        assertTrue(MicroGoal.shouldMove(view, 2, 10));
        assertFalse(MicroGoal.shouldMove(view, 1, 10));
        assertFalse(MicroGoal.shouldMove(view.withMicro(MicroBehaviour.NATIVE, MicroSide.LEFT), 2, 10));
    }

    @Test
    void aPathEndWithinHalfABlockBelongsToTheGoal() {
        assertTrue(MicroRoute.owns(0, 0, 0.5, 0));
        assertFalse(MicroRoute.owns(0, 0, 0.5, 0.1));
    }

    @Test
    void anAcceptedPathIsPendingForFourTicksAndFailsOnTheFifth() {
        var route = new MicroRoute();
        for (var tick = 1; tick <= 4; tick++) {
            assertEquals(MicroRoute.Mark.PENDING, route.note(true, false, tick));
        }
        assertEquals(MicroRoute.Mark.FLIP, route.note(true, false, 5));
        assertEquals(MicroSide.RIGHT, route.side(MicroSide.LEFT));
    }

    @Test
    void twoFailuresLeaveNativeMovementForTwentyTicks() {
        var route = new MicroRoute();
        assertEquals(MicroRoute.Mark.WAIT, route.note(false, false, 1));
        assertEquals(MicroRoute.Mark.FLIP, route.note(false, false, 2));
        assertEquals(MicroRoute.Mark.WAIT, route.note(false, false, 3));
        assertEquals(MicroRoute.Mark.NATIVE, route.note(false, false, 4));
        assertTrue(route.nativeNow(4));
        assertTrue(route.nativeNow(23));
        assertFalse(route.nativeNow(24));
    }

    @Test
    void sidestepAsksThePerpendicularPointOnce() {
        var body = body(0, 0, 10, 0);
        var goal = armed(body, MicroBehaviour.SIDESTEP, MovementSettings.DEFAULTS, 1L);
        assertTrue(goal.asked().isEmpty());
        goal.start();
        var expected = Steering.sidestep(0, 0, 10, 0, MicroSide.LEFT);
        assertEquals(1, body.points);
        assertEquals(0, body.targets);
        assertEquals(expected.x(), body.lastX, 1e-9);
        assertEquals(expected.z(), body.lastZ, 1e-9);
        body.clock.tick = 2;
        goal.tick();
        body.clock.tick = 4;
        goal.tick();
        assertEquals(1, body.points);
    }

    @Test
    void backstepAsksTwoBlocksAwayOnce() {
        var body = body(0, 0, 10, 0);
        var goal = armed(body, MicroBehaviour.BACKSTEP, MovementSettings.DEFAULTS, 1L);
        goal.start();
        var expected = Steering.backstep(0, 0, 10, 0);
        assertEquals(expected.x(), body.lastX, 1e-9);
        assertEquals(expected.z(), body.lastZ, 1e-9);
        assertEquals(0, body.targets);
        body.clock.tick = 2;
        goal.tick();
        assertEquals(1, body.points);
    }

    @Test
    void hoverWaitsAtThePublishedReach() {
        var body = body(0, 0, 10, 0);
        var goal = armed(body, MicroBehaviour.HOVER, MovementSettings.DEFAULTS, 1L);
        goal.start();
        var expected = Steering.hover(0, 0, 10, 0, 3.0);
        assertEquals(expected.x(), body.lastX, 1e-9);
        assertEquals(expected.z(), body.lastZ, 1e-9);
        assertEquals(0, body.targets);
        assertEquals(1.0, body.lastSpeed, 1e-9);
    }

    @Test
    void orbitUsesThePublishedBandInsteadOfAFixedRadius() {
        var body = body(0, 0, 40, 0);
        var noted = band(6, 12, 20);
        var goal = armed(body, MicroBehaviour.ORBIT, noted, 4L);
        goal.start();
        var expected = Steering.orbit(0, 0, 40, 0, MicroSide.LEFT, 6, 12, 0, 0, 20);
        var fixed = Steering.orbit(0, 0, 40, 0, MicroSide.LEFT, 3, 8, 0, 0, 64);
        var defaults = Steering.orbit(0, 0, 40, 0, MicroSide.LEFT, 5, 9, 0, 0, 0);
        assertEquals(expected.x(), body.lastX, 1e-9);
        assertEquals(expected.z(), body.lastZ, 1e-9);
        assertNotEquals(fixed.x(), body.lastX);
        assertNotEquals(defaults.x(), body.lastX);
    }

    @Test
    void enterPathsOntoTheEntityAtTheApproachSpeed() {
        var body = body(0, 0, 8, 0);
        var goal = armed(body, MicroBehaviour.ENTER, MovementSettings.DEFAULTS, 1L);
        goal.start();
        assertEquals(0, body.points);
        assertEquals(1, body.targets);
        assertEquals(Steering.enterSpeed(1.15), body.lastSpeed, 1e-9);
        var asked = goal.asked().orElseThrow();
        assertEquals(8.0, asked.getX(), 1e-9);
        assertEquals(0.0, asked.getZ(), 1e-9);
    }

    @Test
    void anOrbitRenewsWhenTheProfileRefreshComesDue() {
        var body = body(0, 0, 10, 0);
        var board = new IntentBoard();
        var generation = 3;
        var seed = 99L;
        board.noteWaypointRefresh(7);
        board.noteSteer(body.id, seed, band(6, 12, 20));
        publish(board, body.id, generation, MicroBehaviour.ORBIT);
        var goal = new MicroGoal(body, body.id, null, board, body.clock, new WaypointBudget(32, 400, () -> 0L),
                generation, MovementSettings.DEFAULTS, 0L);
        goal.start();
        assertEquals(1, body.points);
        var phase = StableRandom.below(StableRandom.draw(seed, generation, StableRandom.SALT_PHASE), 7);
        var due = 1 + 7 + phase;
        for (var tick = 2; tick < due; tick++) {
            body.clock.tick = tick;
            goal.tick();
            assertEquals(1, body.points, "no renewal before the refresh and its phase, tick " + tick);
        }
        body.clock.tick = due;
        goal.tick();
        assertEquals(2, body.points, "the first renewal waits the published refresh plus the phase of the seed");
        var expected = Steering.orbit(0, 0, 10, 0, MicroSide.LEFT, 6, 12, 0, 0, 20);
        assertEquals(expected.x(), body.lastX, 1e-9);
        assertEquals(expected.z(), body.lastZ, 1e-9);
        for (var tick = due + 1; tick < due + 7; tick++) {
            body.clock.tick = tick;
            goal.tick();
            assertEquals(2, body.points, "later renewals follow the refresh alone, tick " + tick);
        }
        body.clock.tick = due + 7;
        goal.tick();
        assertEquals(3, body.points);
    }

    @Test
    void steeringDoesNotDependOnTheParityOfTheServerTick() {
        var body = body(0, 0, 10, 0);
        var board = new IntentBoard();
        publish(board, body.id, 1, MicroBehaviour.HOVER);
        board.noteSteer(body.id, 1L, MovementSettings.DEFAULTS);
        var goal = new MicroGoal(body, body.id, null, board, body.clock, new WaypointBudget(32, 400, () -> 0L), 1,
                MovementSettings.DEFAULTS, 0L);
        goal.start();
        assertEquals(1, body.points);
        publish(board, body.id, 1, MicroBehaviour.SIDESTEP);
        body.clock.tick = 3;
        goal.tick();
        assertEquals(2, body.points, "a mob whose tick() lands on odd ticks still sidesteps");
        var expected = Steering.sidestep(0, 0, 10, 0, MicroSide.LEFT);
        assertEquals(expected.x(), body.lastX, 1e-9);
        assertEquals(expected.z(), body.lastZ, 1e-9);
        publish(board, body.id, 1, MicroBehaviour.BACKSTEP);
        body.clock.tick = 4;
        goal.tick();
        assertEquals(2, body.points, "one tick after a steer is too soon");
        body.clock.tick = 5;
        goal.tick();
        assertEquals(3, body.points);
    }

    @Test
    void theSharedBudgetRefillsOnTheNextServerTick() {
        var budget = new WaypointBudget(1, 400, () -> 0L);
        var first = body(0, 0, 10, 0);
        var second = body(0, 0, 10, 0);
        var firstGoal = armed(first, MicroBehaviour.SIDESTEP, budget);
        var secondGoal = armed(second, MicroBehaviour.SIDESTEP, budget);
        firstGoal.start();
        secondGoal.start();
        assertEquals(1, first.points);
        assertEquals(0, second.points, "one path per tick for every micro goal together");
        second.clock.tick = 2;
        secondGoal.tick();
        assertEquals(1, second.points, "the budget starts again on the next tick without a reinstall");
    }

    @Test
    void theFirstPathFailureFlipsTheSideOfTheNextOrbit() {
        var body = body(0, 0, 10, 0);
        var board = new IntentBoard();
        board.noteWaypointRefresh(7);
        board.noteSteer(body.id, 99L, band(6, 12, 20));
        publish(board, body.id, 3, MicroBehaviour.ORBIT);
        var goal = new MicroGoal(body, body.id, null, board, body.clock, new WaypointBudget(32, 400, () -> 0L), 3,
                MovementSettings.DEFAULTS, 0L);
        body.path = false;
        goal.start();
        var left = Steering.orbit(0, 0, 10, 0, MicroSide.LEFT, 6, 12, 0, 0, 20);
        assertEquals(left.x(), body.lastX, 1e-9);
        for (var tick = 2; tick <= 6; tick++) {
            body.clock.tick = tick;
            goal.tick();
        }
        assertEquals(1, body.points, "an accepted path that never shows up fails after four pending ticks");
        var due = 1 + 7 + StableRandom.below(StableRandom.draw(99L, 3, StableRandom.SALT_PHASE), 7);
        body.clock.tick = due;
        goal.tick();
        assertEquals(2, body.points);
        var right = Steering.orbit(0, 0, 10, 0, MicroSide.RIGHT, 6, 12, 0, 0, 20);
        assertEquals(right.x(), body.lastX, 1e-9, "the renewal after the first failure uses the other side");
        assertEquals(right.z(), body.lastZ, 1e-9);
    }

    private static MicroGoal armed(Body body, MicroBehaviour micro, WaypointBudget budget) {
        var board = new IntentBoard();
        publish(board, body.id, 1, micro);
        board.noteSteer(body.id, 1L, MovementSettings.DEFAULTS);
        return new MicroGoal(body, body.id, null, board, body.clock, budget, 1, MovementSettings.DEFAULTS, 0L);
    }

    @Test
    void hoverAndEnterRepathOnlyAfterTheTargetMovesMoreThanOneBlock() {
        var hover = body(0, 0, 10, 0);
        var hoverGoal = armed(hover, MicroBehaviour.HOVER, MovementSettings.DEFAULTS, 2L);
        hoverGoal.start();
        hover.tx = 10.5;
        hover.clock.tick = 2;
        hoverGoal.tick();
        assertEquals(1, hover.points);
        hover.tx = 12.5;
        hover.clock.tick = 4;
        hoverGoal.tick();
        assertEquals(2, hover.points);
        var enter = body(0, 0, 8, 0);
        var enterGoal = armed(enter, MicroBehaviour.ENTER, MovementSettings.DEFAULTS, 2L);
        enterGoal.start();
        enter.tx = 8.5;
        enter.clock.tick = 2;
        enterGoal.tick();
        assertEquals(1, enter.targets);
        enter.tx = 10.5;
        enter.clock.tick = 4;
        enterGoal.tick();
        assertEquals(2, enter.targets);
        assertEquals(0, enter.points);
    }

    @Test
    void stopCutsOnlyThePathThisGoalAskedFor() {
        var owned = body(0, 0, 10, 0);
        var goal = armed(owned, MicroBehaviour.SIDESTEP, MovementSettings.DEFAULTS, 3L);
        goal.start();
        var asked = goal.asked().orElseThrow();
        owned.hasEnd = true;
        owned.endX = asked.getX();
        owned.endZ = asked.getZ();
        goal.stop();
        assertTrue(owned.stopped);
        var foreign = body(0, 0, 10, 0);
        var other = armed(foreign, MicroBehaviour.SIDESTEP, MovementSettings.DEFAULTS, 3L);
        other.start();
        foreign.hasEnd = true;
        foreign.endX = 40;
        foreign.endZ = 40;
        other.stop();
        assertFalse(foreign.stopped);
    }

    private static MicroGoal armed(Body body, MicroBehaviour micro, MovementSettings movement, long seed) {
        var board = new IntentBoard();
        publish(board, body.id, 1, micro);
        board.noteSteer(body.id, seed, movement);
        return new MicroGoal(body, body.id, null, board, body.clock, new WaypointBudget(32, 400, () -> 0L), 1,
                MovementSettings.DEFAULTS, 0L);
    }

    private static void publish(IntentBoard board, UUID id, int generation, MicroBehaviour micro) {
        var view = new IntentView(generation, ControlLevel.JEV, Movement.KEEP_DISTANCE, new TacticId("keep"), 0.9f,
                Long.MAX_VALUE).withMicro(micro, MicroSide.LEFT);
        board.publish(new MobKey(id, generation), view);
    }

    private static MovementSettings band(double inner, double outer, double arena) {
        return new MovementSettings(2.5, inner, outer, 16, 10, 1.2, 1.15, OptionalDouble.of(arena));
    }

    private static Body body(double x, double z, double targetX, double targetZ) {
        var body = new Body();
        body.x = x;
        body.z = z;
        body.tx = targetX;
        body.tz = targetZ;
        return body;
    }

    private static final class Body implements MicroBody {
        final UUID id = UUID.randomUUID();
        final Clock clock = new Clock();
        double x;
        double z;
        double tx;
        double tz;
        int points;
        int targets;
        double lastX;
        double lastZ;
        double lastSpeed;
        boolean hasEnd;
        double endX;
        double endZ;
        boolean stopped;
        boolean path = true;

        @Override
        public double x() {
            return x;
        }

        @Override
        public double y() {
            return 64;
        }

        @Override
        public double z() {
            return z;
        }

        @Override
        public boolean onGround() {
            return true;
        }

        @Override
        public int noDamageTicks() {
            return 0;
        }

        @Override
        public int maximumNoDamageTicks() {
            return 0;
        }

        @Override
        public boolean hasTarget() {
            return true;
        }

        @Override
        public double targetX() {
            return tx;
        }

        @Override
        public double targetY() {
            return 64;
        }

        @Override
        public double targetZ() {
            return tz;
        }

        @Override
        public boolean moveToPoint(double px, double py, double pz, double speed) {
            points++;
            lastX = px;
            lastZ = pz;
            lastSpeed = speed;
            return true;
        }

        @Override
        public boolean moveToTarget(double speed) {
            targets++;
            lastSpeed = speed;
            return true;
        }

        @Override
        public boolean hasPath() {
            return path;
        }

        @Override
        public boolean hasEnd() {
            return hasEnd;
        }

        @Override
        public double endX() {
            return endX;
        }

        @Override
        public double endZ() {
            return endZ;
        }

        @Override
        public void stopPathfinding() {
            stopped = true;
        }

        @Override
        public Location at(double px, double py, double pz) {
            return new Location(null, px, py, pz);
        }
    }

    private static final class Clock implements ServerClock {
        long tick = 1;

        @Override
        public long currentTick() {
            return tick;
        }

        @Override
        public long nanoTime() {
            return 1;
        }
    }
}

