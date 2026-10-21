package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.EnemyKinematics;
import akyrion.thepredict.domain.Trend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KinematicBandParityTest {
    @Test
    void lateralIsStrafingAndRadialIsClosingOrBackingAway() {
        assertEquals(Trend.STEADY, TargetTrend.trendFromKinematics(0L));
        assertEquals(Trend.STRAFING, TargetTrend.trendFromKinematics(valid(EnemyKinematics.LEFT_OR_CLOSING, 0)));
        assertEquals(Trend.CLOSING, TargetTrend.trendFromKinematics(valid(0, EnemyKinematics.LEFT_OR_CLOSING)));
        assertEquals(Trend.BACKING_AWAY, TargetTrend.trendFromKinematics(valid(0, EnemyKinematics.RIGHT_OR_AWAY)));
        assertEquals(Trend.STRAFING, TargetTrend.trendFromKinematics(valid(EnemyKinematics.RIGHT_OR_AWAY,
                EnemyKinematics.LEFT_OR_CLOSING)));
    }

    private static long valid(int lateral, int radial) {
        return 1L | ((long) lateral << EnemyKinematics.LATERAL_SHIFT) | ((long) radial << EnemyKinematics.RADIAL_SHIFT);
    }
}
