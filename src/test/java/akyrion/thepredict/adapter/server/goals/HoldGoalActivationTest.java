package akyrion.thepredict.adapter.server.goals;

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

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldGoalActivationTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    private final IntentBoard board = new IntentBoard();
    private final AtomicLong nanos = new AtomicLong(1_000);
    private final AtomicInteger stops = new AtomicInteger();
    private final HoldGoal goal = new HoldGoal(GoalFixtures.mob(ID, EntityType.ZOMBIE, pathfinder(), () -> null,
            new Location(null, 0, 64, 0)), GoalFixtures.KEYS.hold(), board, GoalFixtures.clock(new AtomicLong(), nanos), 2);

    private Pathfinder pathfinder() {
        return GoalFixtures.proxy(Pathfinder.class, (instance, method, args) -> {
            if (method.getName().equals("stopPathfinding")) {
                stops.incrementAndGet();
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private void publish(int generation, ControlLevel level, long expiresAtNanos) {
        board.publish(new MobKey(ID, generation), new IntentView(generation, level, Movement.KEEP_DISTANCE,
                new TacticId("keep_range"), 0.8f, expiresAtNanos));
    }

    @Test
    void activatesAndStaysWhileAControllingViewOfItsGenerationIsValid() {
        publish(2, ControlLevel.JEV, 2_000);
        assertTrue(goal.shouldActivate());
        assertTrue(goal.shouldStayActive());
        nanos.set(1_999);
        assertTrue(goal.shouldStayActive());
    }

    @Test
    void stopsExactlyAtTheDeadline() {
        publish(2, ControlLevel.LOCAL, 2_000);
        nanos.set(2_000);
        assertFalse(goal.shouldActivate());
        assertFalse(goal.shouldStayActive());
    }

    @Test
    void ignoresAnotherGenerationTheBaseLevelAndAClearedView() {
        publish(3, ControlLevel.JEV, 5_000);
        assertFalse(goal.shouldActivate());
        board.publish(new MobKey(ID, 2), IntentView.base(2));
        assertFalse(goal.shouldActivate());
        publish(2, ControlLevel.ALTERNATIVE, 5_000);
        assertTrue(goal.shouldActivate());
        board.clear(ID);
        assertFalse(goal.shouldActivate());
        assertFalse(goal.shouldStayActive());
    }

    @Test
    void neverDeclaresLookSoLookAtTargetKeepsTurningTheMob() {
        assertEquals(EnumSet.of(GoalType.MOVE), goal.getTypes());
        assertFalse(goal.getTypes().contains(GoalType.LOOK));
        assertEquals(GoalFixtures.KEYS.hold(), goal.getKey());
    }

    @Test
    void startingStopsTheCurrentNavigationOnceAndTickDoesNothing() {
        goal.start();
        goal.tick();
        assertEquals(1, stops.get());
    }
}
