package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MobKey;
import org.junit.jupiter.api.Test;

import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.profile;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rest of M26.6 opens after three motivated hits in a row, not three motivated casts (E-10, domain-MZ-10): a cast
 * counts once a hit on the target confirms it within the hit window, and one that does not land clears the streak.
 */
class MobSessionRestStreakTest {
    private final MobSession session = new MobSession(new MobKey(id(1), 0), profile("Guardian"));
    private int tick = 100;

    private void cast(boolean lands) {
        session.noteCastQuota(tick, true);
        if (lands) {
            session.noteMotivatedHit(tick + 5);
        }
        tick += 40;
    }

    @Test
    void threeMissesNeverOpenARest() {
        cast(false);
        cast(false);
        cast(false);
        session.noteCastQuota(tick, false);
        assertFalse(session.takeRest());
        assertFalse(session.patternResting(tick));
        assertFalse(session.lastMotivatedLanded());
    }

    @Test
    void aMissInsideTheRunStartsTheStreakAgain() {
        cast(true);
        cast(false);
        cast(true);
        cast(true);
        assertFalse(session.takeRest(), "two hits after the miss are not three");
        var third = tick;
        cast(true);
        assertTrue(session.takeRest(), "hit, miss, hit, hit, hit opens the rest after the last three");
        assertTrue(session.patternResting(third + 5));
        assertTrue(session.lastMotivatedLanded());
    }

    @Test
    void aHitAfterTheWindowDoesNotConfirmTheCast() {
        session.noteCastQuota(tick, true);
        session.noteMotivatedHit(tick + OutcomeWindow.HIT_WINDOW_TICKS + 1);
        session.noteCastQuota(tick + 60, false);
        assertFalse(session.lastMotivatedLanded(), "a late hit is not the result of that cast");
        session.noteCastQuota(tick + 100, true);
        session.noteMotivatedHit(tick + 100 + OutcomeWindow.HIT_WINDOW_TICKS);
        assertTrue(session.lastMotivatedLanded(), "a hit on the last tick of the window still counts");
    }
}
