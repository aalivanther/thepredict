package akyrion.thepredict.adapter.server.goals;

import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementReadingTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private final AtomicInteger pathRequests = new AtomicInteger();

    private Pathfinder pathfinder(boolean hasPath, Boolean reachesEnd) {
        return GoalFixtures.proxy(Pathfinder.class, (instance, method, args) -> switch (method.getName()) {
            case "hasPath" -> hasPath;
            case "getCurrentPath" -> reachesEnd == null ? null : GoalFixtures.proxy(Pathfinder.PathResult.class,
                    (path, call, values) -> switch (call.getName()) {
                        case "canReachFinalPoint" -> reachesEnd;
                        default -> throw new UnsupportedOperationException(call.getName());
                    });
            case "findPath", "moveTo" -> {
                pathRequests.incrementAndGet();
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private Mob mob(Pathfinder pathfinder, LivingEntity target) {
        return GoalFixtures.mob(ID, EntityType.ZOMBIE, pathfinder, () -> target, new Location(null, 0, 64, 0));
    }

    @Test
    void readsThePathTheMobAlreadyHasWithoutAskingForANewOne() {
        var target = GoalFixtures.target(() -> new Location(null, 3, 68, 0));
        var progress = MovementReading.of(mob(pathfinder(true, true), target), true);
        assertTrue(progress.movementGoalRunning());
        assertTrue(progress.hasPath());
        assertTrue(progress.pathReachesTarget());
        assertEquals(OptionalDouble.of(5.0), progress.targetDistanceBlocks());
        assertEquals(0, pathRequests.get());
    }

    @Test
    void aPathThatStopsShortOrIsMissingDoesNotReachTheTarget() {
        assertFalse(MovementReading.of(mob(pathfinder(true, false), null), false).pathReachesTarget());
        assertFalse(MovementReading.of(mob(pathfinder(true, null), null), false).pathReachesTarget());
        var stale = MovementReading.of(mob(pathfinder(false, true), null), false);
        assertFalse(stale.hasPath());
        assertFalse(stale.pathReachesTarget());
    }

    @Test
    void withoutATargetThereIsNoDistance() {
        assertEquals(OptionalDouble.empty(), MovementReading.of(mob(pathfinder(false, null), null), false)
                .targetDistanceBlocks());
    }
}
