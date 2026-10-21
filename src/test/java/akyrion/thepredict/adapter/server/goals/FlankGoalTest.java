package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.adapter.server.context.ShieldGeometry;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.IntentBoard;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlankGoalTest {
    private static final double TOLERANCE = 1e-9;
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    private final IntentBoard board = new IntentBoard();
    private final AtomicLong ticks = new AtomicLong(100);
    private final AtomicReference<Location> targetAt = new AtomicReference<>(new Location(null, 0, 64, 0, 0f, 0f));
    private final AtomicReference<OptionalDouble> shield = new AtomicReference<>(OptionalDouble.of(90));
    private final List<Long> findPathTicks = new ArrayList<>();
    private final List<Integer> pointsBySide = new ArrayList<>(List.of(6, 4));
    private final List<Boolean> reachableBySide = new ArrayList<>(List.of(true, true));
    private final List<Integer> followed = new ArrayList<>();
    private boolean running;
    private final FlankGoal goal = new FlankGoal(GoalFixtures.mob(ID, EntityType.ZOMBIE, pathfinder(),
            () -> GoalFixtures.target(targetAt::get), new Location(null, 5, 64, 5)), GoalFixtures.KEYS.flank(), board,
            GoalFixtures.clock(ticks, new AtomicLong()), 0, 2.5, 1.15, target -> shield.get());

    private Pathfinder pathfinder() {
        return GoalFixtures.proxy(Pathfinder.class, (instance, method, args) -> switch (method.getName()) {
            case "findPath" -> {
                var side = findPathTicks.size() % 2;
                findPathTicks.add(ticks.get());
                yield GoalFixtures.path(pointsBySide.get(side), reachableBySide.get(side));
            }
            case "moveTo" -> {
                followed.add(((Pathfinder.PathResult) args[0]).getPoints().size());
                yield true;
            }
            case "stopPathfinding" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private void flankIntent() {
        board.publish(new MobKey(ID, 0), new IntentView(0, ControlLevel.LOCAL, Movement.FLANK,
                new TacticId("flank_shield"), 1f, Long.MAX_VALUE));
    }

    private void selectorTick() {
        if (!running && goal.shouldActivate()) {
            goal.start();
            running = true;
        } else if (running && !goal.shouldStayActive()) {
            goal.stop();
            running = false;
        } else if (running) {
            goal.tick();
        }
        ticks.incrementAndGet();
    }

    @Test
    void wantsFlankOnlyForAControllingFlankViewOfItsGeneration() {
        var flank = new IntentView(0, ControlLevel.JEV, Movement.FLANK, new TacticId("flank_shield"), 1f, 100L);
        assertTrue(FlankGoal.wantsFlank(flank, 0, 0));
        assertFalse(FlankGoal.wantsFlank(flank, 1, 0));
        assertFalse(FlankGoal.wantsFlank(flank, 0, 100));
        var hold = new IntentView(0, ControlLevel.JEV, Movement.HOLD, new TacticId("hold_ground"), 1f, 100L);
        assertFalse(FlankGoal.wantsFlank(hold, 0, 0));
    }

    @Test
    void replansOnlyAfterTenTicks() {
        assertFalse(FlankGoal.shouldReplan(100, 109));
        assertTrue(FlankGoal.shouldReplan(100, 110));
        assertTrue(FlankGoal.shouldReplan(Integer.MAX_VALUE - 4, Integer.MIN_VALUE + 5));
    }

    @Test
    void zeroTurnPointsAlongTheMinecraftFacing() {
        var south = ShieldGeometry.offset(0f, 0, 2.5);
        assertEquals(0.0, south.getX(), TOLERANCE);
        assertEquals(2.5, south.getZ(), TOLERANCE);
        var west = ShieldGeometry.offset(90f, 0, 1.0);
        assertEquals(-1.0, west.getX(), TOLERANCE);
        assertEquals(0.0, west.getZ(), TOLERANCE);
    }

    @Test
    void flankPointsAreSymmetricBehindTheShieldAngle() {
        var turn = ShieldGeometry.flankTurnDegrees(ShieldGeometry.DEFAULT_BLOCKING_ANGLE_DEGREES);
        var left = ShieldGeometry.offset(0f, turn, 2.5);
        var right = ShieldGeometry.offset(0f, -turn, 2.5);
        assertEquals(-left.getX(), right.getX(), TOLERANCE);
        assertEquals(left.getZ(), right.getZ(), TOLERANCE);
        assertTrue(left.getZ() < 0, "110 degrees from the facing is slightly behind the target");
        assertEquals(2.5, left.length(), TOLERANCE);
        assertEquals(0.0, left.getY(), TOLERANCE);
    }

    @Test
    void neverPlansMoreThanOneRoundEveryTenTicksWhileTheTargetKeepsMoving() {
        flankIntent();
        for (var step = 0; step < 100; step++) {
            targetAt.set(new Location(null, step * 2.0, 64, 0, step * 40f, 0f));
            selectorTick();
        }
        assertEquals(0, findPathTicks.size() % 2);
        for (var round = 2; round < findPathTicks.size(); round += 2) {
            assertEquals(findPathTicks.get(round - 2), findPathTicks.get(round - 1), "both sides in one round");
            assertTrue(findPathTicks.get(round) - findPathTicks.get(round - 2) >= FlankGoal.REPLAN_INTERVAL_TICKS,
                    findPathTicks.toString());
        }
        assertEquals(20, findPathTicks.size(), findPathTicks.toString());
    }

    @Test
    void aStillTargetIsPlannedOnlyOnce() {
        flankIntent();
        for (var step = 0; step < 50; step++) {
            selectorTick();
        }
        assertTrue(running);
        assertEquals(2, findPathTicks.size());
    }

    @Test
    void aRestartInsideTheIntervalWaitsBeforePlanningAgain() {
        flankIntent();
        selectorTick();
        board.clear(ID);
        selectorTick();
        assertFalse(running);
        flankIntent();
        while (ticks.get() < 100 + FlankGoal.REPLAN_INTERVAL_TICKS) {
            selectorTick();
            assertFalse(running);
        }
        assertEquals(2, findPathTicks.size());
        selectorTick();
        assertTrue(running);
        assertEquals(List.of(100L, 100L, 110L, 110L), findPathTicks);
    }

    @Test
    void followsTheReachableSideWithFewerPoints() {
        flankIntent();
        selectorTick();
        assertEquals(List.of(4), followed);
        reachableBySide.set(1, false);
        pointsBySide.set(0, 9);
        targetAt.set(new Location(null, 3, 64, 0));
        for (var step = 0; step < FlankGoal.REPLAN_INTERVAL_TICKS; step++) {
            selectorTick();
        }
        assertEquals(List.of(4, 9), followed);
    }

    @Test
    void withoutAPathOnEitherSideTheGoalStopsAndDoesNotRetryBeforeTheInterval() {
        reachableBySide.set(0, false);
        reachableBySide.set(1, false);
        flankIntent();
        for (var step = 0; step < FlankGoal.REPLAN_INTERVAL_TICKS - 1; step++) {
            selectorTick();
        }
        assertFalse(running);
        assertEquals(2, findPathTicks.size());
        assertTrue(followed.isEmpty());
    }

    @Test
    void doesNotActivateWithoutARaisedShieldOrAgainstAShieldThatBlocksEverySide() {
        flankIntent();
        shield.set(OptionalDouble.empty());
        assertFalse(goal.shouldActivate());
        shield.set(OptionalDouble.of(180));
        assertFalse(goal.shouldActivate());
        shield.set(OptionalDouble.of(179.9));
        assertTrue(goal.shouldActivate());
        assertTrue(findPathTicks.isEmpty());
    }

    @Test
    void doesNotActivateForAnotherMovementAndDeclaresOnlyMove() {
        board.publish(new MobKey(ID, 0), new IntentView(0, ControlLevel.LOCAL, Movement.HOLD,
                new TacticId("hold_ground"), 1f, Long.MAX_VALUE));
        assertFalse(goal.shouldActivate());
        assertEquals(EnumSet.of(GoalType.MOVE), goal.getTypes());
    }
}
