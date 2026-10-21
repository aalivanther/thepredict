package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.Trend;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetRelationReaderTest {
    @Test
    void aPlayerTrendComesFromTheKinematicWordAndNotFromKeys() {
        assertEquals(Trend.STEADY, TargetTrend.trendFromKinematics(0L));
        assertEquals(Trend.STRAFING, TargetTrend.trendFromKinematics(1L | (1L << 44)));
        assertEquals(Trend.STRAFING, TargetTrend.trendFromKinematics(1L | (2L << 44) | (1L << 46)));
        assertEquals(Trend.CLOSING, TargetTrend.trendFromKinematics(1L | (1L << 46)));
        assertEquals(Trend.BACKING_AWAY, TargetTrend.trendFromKinematics(1L | (2L << 46)));
    }

    @Test
    void aChangeOfMoreThanOneBlockOverridesTheReading() {
        assertEquals(Trend.STRAFING, TargetTrend.correctedByDistance(Trend.STRAFING, 5.0, 6.0));
        assertEquals(Trend.STRAFING, TargetTrend.correctedByDistance(Trend.STRAFING, 7.0, 6.0));
        assertEquals(Trend.CLOSING, TargetTrend.correctedByDistance(Trend.STEADY, Math.nextDown(5.0), 6.0));
        assertEquals(Trend.BACKING_AWAY, TargetTrend.correctedByDistance(Trend.CLOSING, Math.nextUp(7.0), 6.0));
    }

    @Test
    void aPathInProgressDecidesAndNoNewPathIsComputed() {
        var computed = new AtomicInteger();
        assertTrue(TargetRelationReader.reachable(EntityReads.CurrentPath.REACHES, true, () -> {
            computed.incrementAndGet();
            return false;
        }, true));
        assertFalse(TargetRelationReader.reachable(EntityReads.CurrentPath.FALLS_SHORT, true, () -> {
            computed.incrementAndGet();
            return true;
        }, false));
        assertEquals(0, computed.get());
    }

    @Test
    void aNewPathIsComputedOnlyWithThePermissionOfTheEngine() {
        var computed = new AtomicInteger();
        assertFalse(TargetRelationReader.reachable(EntityReads.CurrentPath.NONE, true, () -> {
            computed.incrementAndGet();
            return false;
        }, false));
        assertEquals(1, computed.get());
        assertTrue(TargetRelationReader.reachable(EntityReads.CurrentPath.NONE, false, () -> {
            computed.incrementAndGet();
            return false;
        }, false));
        assertFalse(TargetRelationReader.reachable(EntityReads.CurrentPath.NONE, false, () -> {
            computed.incrementAndGet();
            return true;
        }, true));
        assertEquals(1, computed.get());
    }

    @Test
    void theReachIsTheAttackRangeWhenTheWeaponHasOne() {
        assertEquals(3.0, TargetReach.player(3.0, false, 0f).maxBlocks());
        assertEquals(4.5, TargetReach.player(3.0, true, 4.5f).maxBlocks());
        assertEquals(2.0, TargetReach.player(3.0, true, 2.0f).maxBlocks());
        assertEquals(TargetReach.Kind.DISTANCE, TargetReach.player(3.0, false, 0f).kind());
    }

    @Test
    void flankingNeedsAShieldUpSightAndAMobStillInsideTheBlockingAngle() {
        assertTrue(TargetRelationReader.flankable(true, true, false));
        assertFalse(TargetRelationReader.flankable(false, true, false));
        assertFalse(TargetRelationReader.flankable(true, false, false));
        assertFalse(TargetRelationReader.flankable(true, true, true));
    }

    @Test
    void theBlockingAngleOfNinetyDegreesIsInsideAtExactlyNinetyAndOutsideJustBeyond() {
        var target = new Location(null, 0, 64, 0, 0f, 0f);
        assertFalse(outside(target, 89.9));
        assertFalse(outside(target, 90.0));
        assertTrue(outside(target, 90.1));
        assertTrue(TargetRelationReader.flankable(true, true, outside(target, 89.9)));
        assertFalse(TargetRelationReader.flankable(true, true, outside(target, 90.1)));
    }

    private static boolean outside(Location target, double angleDegrees) {
        var radians = Math.toRadians(angleDegrees);
        var attacker = new Location(null, -Math.sin(radians) * 3, 64, Math.cos(radians) * 3);
        return ShieldGeometry.isOutsideBlockingAngle(target, attacker, ShieldGeometry.DEFAULT_BLOCKING_ANGLE_DEGREES);
    }

    @Test
    void theTargetIsGroupedWithAnotherParticipantWithinFourBlocksOfItsCentre() {
        var target = new UUID(9, 1);
        assertFalse(TargetRelationReader.grouped(target, positions(target)));
        assertTrue(TargetRelationReader.grouped(target, positions(target, 3.9)));
        assertTrue(TargetRelationReader.grouped(target, positions(target, 4.0)));
        assertFalse(TargetRelationReader.grouped(target, positions(target, 4.1)));
        assertTrue(TargetRelationReader.grouped(target, positions(target, 4.1, 3.9)));
        assertTrue(TargetRelationReader.grouped(target, positions(target, 4.0, 6.0)));
        assertFalse(TargetRelationReader.grouped(target, positions(target, 4.1, 7.0)));
        assertFalse(TargetRelationReader.grouped(new UUID(9, 99), positions(target, 1.0)));
    }

    private static Map<UUID, Location> positions(UUID target, double... others) {
        var positions = new LinkedHashMap<UUID, Location>();
        positions.put(target, new Location(null, 10, 64, 10));
        for (var index = 0; index < others.length; index++) {
            positions.put(new UUID(9, index + 2), new Location(null, 10, 64, 10 + others[index]));
        }
        return positions;
    }

    @Test
    void theTrendOfAnEntityComesFromItsVelocityProjectedTowardsTheMob() {
        assertEquals(Trend.CLOSING, TargetTrend.trendFromVelocity(10, 0, 0.06, 0));
        assertEquals(Trend.STEADY, TargetTrend.trendFromVelocity(10, 0, 0.05, 0), "0.05 is not more than");
        assertEquals(Trend.BACKING_AWAY, TargetTrend.trendFromVelocity(10, 0, -0.2, 0));
        assertEquals(Trend.STRAFING, TargetTrend.trendFromVelocity(10, 0, 0.03, 0.2));
        assertEquals(Trend.CLOSING, TargetTrend.trendFromVelocity(0, 5, 0.1, 0.2),
                "the larger part decides between closing and strafing");
        assertEquals(Trend.STEADY, TargetTrend.trendFromVelocity(0, 0, 1, 1), "on top of the mob");
        assertEquals(Trend.STEADY, TargetTrend.trendFromVelocity(3, 4, 0, 0));
        assertEquals(Trend.CLOSING, TargetTrend.correctedByDistance(
                TargetTrend.trendFromVelocity(3, 4, 0, 0), 4.0, 6.0), "the distance still corrects it");
    }
}
