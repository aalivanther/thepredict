package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.adapter.server.zone.ZoneDestination;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MicroSide;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.Steering;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.ZoneColumn;
import akyrion.thepredict.domain.ZoneGrid;
import akyrion.thepredict.domain.ZoneQueryRunner;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Q4 is asked of the point, and a second miss holds native movement (M24.5). */
class MicroGoalZoneTest {
    @Test
    void aLavaPointIsNotTheFeetAndASecondMissHoldsNativeForTwentyTicks() {
        var body = body(8, 8, 18, 8);
        var point = Steering.sidestep(8, 8, 18, 8, MicroSide.LEFT);
        var grid = floors();
        var px = (int) Math.floor(point.x());
        var pz = (int) Math.floor(point.z());
        grid.set(pz * grid.width() + px, lava());
        var moved = goal(body, MicroBehaviour.SIDESTEP, grid);
        moved.start();
        assertEquals(1, body.points);
        var landedX = (int) Math.floor(body.lastX);
        var landedZ = (int) Math.floor(body.lastZ);
        assertEquals(1, Math.abs(landedX - px) + Math.abs(landedZ - pz));
        assertNotEquals(8, landedZ);
        var refused = floors();
        for (var index = 0; index < refused.width() * refused.width(); index++) {
            refused.set(index, lava());
        }
        var parked = body(8, 8, 18, 8);
        var board = new IntentBoard();
        var parkedGoal = goal(parked, MicroBehaviour.SIDESTEP, refused, board);
        parkedGoal.start();
        assertEquals(0, parked.points);
        assertEquals(MicroBehaviour.NATIVE, board.current(parked.id).micro());
        assertEquals(21L, board.microNativeUntil(parked.id));
    }

    @Test
    void enterIsNotFilteredAndRepositionStaysOnTheForwardSide() {
        var body = body(8, 8, 18, 8);
        var entered = goal(body, MicroBehaviour.ENTER, floors(), (id, behaviour, point, blockX, blockZ) ->
                java.util.Optional.empty());
        entered.start();
        assertEquals(1, body.targets);
        assertEquals(0, body.points);
        var queries = new ZoneQueryRunner();
        var forward = ZoneDestination.filter(queries, floors(), MicroBehaviour.REPOSITION, new Steering(18, 8), 8, 8);
        assertTrue(forward.isPresent());
        var cellX = (int) Math.floor(forward.get().x());
        var cellZ = (int) Math.floor(forward.get().z());
        var dot = Integer.signum(cellX - 8) * -1 + Integer.signum(cellZ - 8) * 0;
        assertTrue(dot > 0);
    }

    @Test
    void theOppositeSideIsUsedWhenTheFirstSideIsNone() {
        var body = body(8, 8, 18, 8);
        var left = Steering.sidestep(8, 8, 18, 8, MicroSide.LEFT);
        var right = Steering.sidestep(8, 8, 18, 8, MicroSide.RIGHT);
        var grid = floors();
        var width = grid.width();
        var lava = lava();
        var lx = (int) Math.floor(left.x());
        var lz = (int) Math.floor(left.z());
        for (var dz = -2; dz <= 2; dz++) {
            for (var dx = -2; dx <= 2; dx++) {
                var cx = lx + dx;
                var cz = lz + dz;
                if (cx >= 0 && cz >= 0 && cx < width && cz < width) {
                    grid.set(cz * width + cx, lava);
                }
            }
        }
        var goal = goal(body, MicroBehaviour.SIDESTEP, grid);
        goal.start();
        assertEquals(1, body.points);
        assertEquals((int) Math.floor(right.x()), (int) Math.floor(body.lastX));
        assertEquals((int) Math.floor(right.z()), (int) Math.floor(body.lastZ));
    }

    private static MicroGoal goal(Body body, MicroBehaviour micro, ZoneGrid grid) {
        return goal(body, micro, grid, new IntentBoard());
    }

    private static MicroGoal goal(Body body, MicroBehaviour micro, ZoneGrid grid, IntentBoard board) {
        var queries = new ZoneQueryRunner();
        return goal(body, micro, grid, board, (id, behaviour, point, blockX, blockZ) ->
                ZoneDestination.filter(queries, grid, behaviour, point, blockX, blockZ));
    }

    private static MicroGoal goal(Body body, MicroBehaviour micro, ZoneGrid grid, DestinationGuard guard) {
        return goal(body, micro, grid, new IntentBoard(), guard);
    }

    private static MicroGoal goal(Body body, MicroBehaviour micro, ZoneGrid grid, IntentBoard board,
                                  DestinationGuard guard) {
        publish(board, body.id, micro);
        board.noteSteer(body.id, 1L, MovementSettings.DEFAULTS);
        return new MicroGoal(body, body.id, null, board, body.clock, new WaypointBudget(32, 400, () -> 0L), 1,
                MovementSettings.DEFAULTS, 0L, guard);
    }

    private static void publish(IntentBoard board, UUID id, MicroBehaviour micro) {
        var view = new IntentView(1, ControlLevel.JEV, Movement.KEEP_DISTANCE, new TacticId("keep"), 0.9f,
                Long.MAX_VALUE).withMicro(micro, MicroSide.LEFT);
        board.publish(new MobKey(id, 1), view);
    }

    private static ZoneGrid floors() {
        var grid = new ZoneGrid(32, 0, 0, 64, 3);
        var floor = ZoneColumn.pack(ZoneColumn.FLOOR, 64, 15, 0, 0L);
        for (var index = 0; index < grid.width() * grid.width(); index++) {
            grid.set(index, floor);
        }
        grid.ready(1);
        return grid;
    }

    private static long lava() {
        return ZoneColumn.pack(ZoneColumn.FLOOR, 64, 15, 0, 1L << ZoneColumn.LAVA);
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

        @Override public double x() { return x; }
        @Override public double y() { return 64; }
        @Override public double z() { return z; }
        @Override public boolean onGround() { return true; }
        @Override public int noDamageTicks() { return 0; }
        @Override public int maximumNoDamageTicks() { return 0; }
        @Override public boolean hasTarget() { return true; }
        @Override public double targetX() { return tx; }
        @Override public double targetY() { return 64; }
        @Override public double targetZ() { return tz; }

        @Override
        public boolean moveToPoint(double px, double py, double pz, double speed) {
            points++;
            lastX = px;
            lastZ = pz;
            return true;
        }

        @Override
        public boolean moveToTarget(double speed) {
            targets++;
            return true;
        }

        @Override public boolean hasPath() { return false; }
        @Override public boolean hasEnd() { return false; }
        @Override public double endX() { return 0; }
        @Override public double endZ() { return 0; }
        @Override public void stopPathfinding() { }
        @Override public Location at(double px, double py, double pz) { return new Location(null, px, py, pz); }
    }

    private static final class Clock implements ServerClock {
        long tick = 1;

        @Override public long currentTick() { return tick; }
        @Override public long nanoTime() { return 1; }
    }
}
