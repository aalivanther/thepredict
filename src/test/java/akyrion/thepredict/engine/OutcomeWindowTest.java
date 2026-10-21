package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One outcome window of M16.8: goals by movement, hits after a damaging cast, sums, deaths and the effect index. */
class OutcomeWindowTest {
    private static final UUID TARGET = new UUID(9, 9);

    private static OutcomeWindow window(Movement movement) {
        return new OutcomeWindow(ControlLevel.JEV, new TacticId("tactic"), movement, 100, Optional.of(TARGET), false);
    }

    private static boolean reached(Movement movement, DistanceBand... bands) {
        var window = window(movement);
        for (var band : bands) {
            window.probed(Optional.of(band));
        }
        return window.close(120).reached();
    }

    @Test
    void eachMovementReachesItsOwnGoal() {
        assertTrue(reached(Movement.APPROACH, DistanceBand.RANGED, DistanceBand.MELEE, DistanceBand.RANGED));
        assertFalse(reached(Movement.APPROACH, DistanceBand.RANGED, DistanceBand.RANGED));
        assertTrue(reached(Movement.KEEP_DISTANCE, DistanceBand.MELEE, DistanceBand.RANGED));
        assertFalse(reached(Movement.KEEP_DISTANCE, DistanceBand.RANGED, DistanceBand.MELEE));
        assertTrue(reached(Movement.RETREAT, DistanceBand.MELEE, DistanceBand.RANGED));
        assertTrue(reached(Movement.RETREAT, DistanceBand.BEYOND));
        assertFalse(reached(Movement.RETREAT, DistanceBand.RANGED, DistanceBand.RANGED));
        assertFalse(reached(Movement.HOLD, DistanceBand.MELEE));
        assertFalse(reached(Movement.NONE, DistanceBand.MELEE));
        var flank = window(Movement.FLANK);
        assertFalse(flank.close(110).reached());
        flank.cast(true, 105);
        assertTrue(flank.close(110).reached(), "a cast while flanking landed outside the blocking angle");
        assertFalse(window(Movement.APPROACH).close(101).reached(), "without a probe nothing is reached");
    }

    @Test
    void damageToTheTargetWithinTwentyTicksOfADamagingCastIsOneHit() {
        var window = window(Movement.APPROACH);
        window.cast(true, 110);
        window.dealt(TARGET, 0.1, 130);
        window.dealt(TARGET, 0.1, 131);
        window.cast(false, 140);
        window.dealt(TARGET, 0.05, 145);
        window.cast(true, 150);
        window.dealt(new UUID(1, 1), 0.2, 151);
        window.dealt(TARGET, 0.05, 171);
        window.taken(0.3);
        var summary = window.close(160);
        assertEquals(1, summary.hits());
        assertEquals(3, summary.casts());
        assertEquals(0.5, summary.dealt(), 1e-9);
        assertEquals(0.3, summary.taken(), 1e-9);
    }

    @Test
    void theEffectIsDealtMinusTakenPlusAQuarterForTheGoalAndOneForEachDeath() {
        var window = window(Movement.APPROACH);
        window.probed(Optional.of(DistanceBand.MELEE));
        window.dealt(TARGET, 0.4, 101);
        window.taken(0.1);
        window.targetDied();
        var summary = window.close(130);
        assertEquals(0.4 - 0.1 + 0.25 + 1.0, summary.effect(), 1e-9);
        assertTrue(summary.targetDied());
        assertFalse(summary.mobDied());
        var lost = window(Movement.HOLD);
        lost.mobDied();
        assertEquals(-1.0, lost.close(101).effect(), 1e-9);
        assertEquals(OutcomeWindow.Summary.effect(0.2, 0.1, false, false, true), -0.9, 1e-9);
    }

    @Test
    void eachHealthFractionCountsWithinZeroAndOne() {
        var window = window(Movement.APPROACH);
        window.dealt(TARGET, 3.0, 101);
        window.dealt(TARGET, -0.5, 102);
        window.dealt(TARGET, Double.NaN, 103);
        window.taken(2.5);
        window.taken(-1.0);
        var summary = window.close(130);
        assertEquals(1.0, summary.dealt(), 1e-12);
        assertEquals(1.0, summary.taken(), 1e-12);
    }

    @Test
    void aWindowLastsAtMostSixtyTicks() {
        var window = window(Movement.APPROACH);
        assertFalse(window.due(159));
        assertTrue(window.due(160));
        assertEquals(60, OutcomeWindow.OUTCOME_WINDOW_TICKS);
        assertEquals(20, OutcomeWindow.HIT_WINDOW_TICKS);
    }

    @Test
    void aSummaryRejectsNegativeSumsAndAnEndBeforeTheStart() {
        var tactic = new TacticId("tactic");
        assertThrows(IllegalArgumentException.class, () -> new OutcomeWindow.Summary(ControlLevel.JEV, tactic,
                Movement.HOLD, 10, 9, 0, 0, false, 0, 0, false, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new OutcomeWindow.Summary(ControlLevel.JEV, tactic,
                Movement.HOLD, 10, 11, -0.1, 0, false, 0, 0, false, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new OutcomeWindow.Summary(ControlLevel.JEV, tactic,
                Movement.HOLD, 10, 11, 0, 0, false, -1, 0, false, false, 0));
    }
}
