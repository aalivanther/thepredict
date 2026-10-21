package akyrion.thepredict.engine;

import akyrion.thepredict.adapter.server.patterns.KinematicStamp;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyKinematics;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.MicroSide;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.Steering;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** E-11: a predictable strafe picks the orbit side that keeps the mob in front of it (M26.5, MicroSidePriority). */
class TickSteerTest {
    @Test
    void theSideOfAStrafeHabitMovesTheMobAlongTheStrafeAroundTheCompass() {
        for (var degrees = 0; degrees < 360; degrees += 30) {
            var angle = Math.toRadians(degrees);
            var mobX = 5 * Math.cos(angle);
            var mobZ = 5 * Math.sin(angle);
            for (var sign = -1; sign <= 1; sign += 2) {
                var strafeX = -mobZ / 5 * sign;
                var strafeZ = mobX / 5 * sign;
                var lateral = KinematicStamp.motion(strafeX, strafeZ, mobX, mobZ, 0.5, true) & 3;
                var pattern = lateral == EnemyKinematics.LEFT_OR_CLOSING ? EnemyPattern.PREDICTABLE_STRAFE_LEFT
                        : EnemyPattern.PREDICTABLE_STRAFE_RIGHT;
                var session = exploiting(pattern.bit());
                var side = MicroSide.values()[TickSteer.patternSide(session, 10L)];
                var next = Steering.orbit(mobX, mobZ, 0, 0, side, 2, 8, 0, 0, 64);
                var along = (next.x() - mobX) * strafeX + (next.z() - mobZ) * strafeZ;
                assertTrue(along > 0, degrees + "° sign " + sign + ": the orbit must follow the strafe, moved "
                        + along);
            }
        }
    }

    @Test
    void aConfidentForecastOfAStrafeChoosesTheSideWithoutAnActivePatternOfIt() {
        var session = exploiting(EnemyPattern.SPAMS_CLICKS.bit());
        session.publishPattern(EnemyPattern.SPAMS_CLICKS.bit(), EnemyAction.STRAFE_LEFT.ordinal());
        assertEquals(MicroSide.RIGHT.ordinal(), TickSteer.patternSide(session, 10L));
        session.publishPattern(EnemyPattern.SPAMS_CLICKS.bit(), EnemyAction.STRAFE_RIGHT.ordinal());
        assertEquals(MicroSide.LEFT.ordinal(), TickSteer.patternSide(session, 10L));
    }

    @Test
    void observeAndBothStrafesLeaveTheSideToTheSeed() {
        var observe = exploiting(EnemyPattern.PREDICTABLE_STRAFE_LEFT.bit());
        observe.patternMode(PatternMode.OBSERVE);
        assertEquals(-1, TickSteer.patternSide(observe, 10L));
        var both = exploiting(EnemyPattern.PREDICTABLE_STRAFE_LEFT.bit()
                | EnemyPattern.PREDICTABLE_STRAFE_RIGHT.bit());
        assertEquals(-1, TickSteer.patternSide(both, 10L));
    }

    /** A session whose bias holds {@code held} with a live counter and room in its quota. */
    private static MobSession exploiting(int held) {
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), EngineFixtures.profile("Guardian"));
        session.patternMode(PatternMode.BIAS);
        session.patternPass(true);
        session.patternCounterActive(true);
        session.patternHeld(held);
        session.patternWindow(400);
        session.patternShare(1.0);
        return session;
    }
}
