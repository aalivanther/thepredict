package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternCause;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.PatternState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M26.3: a pattern is a proportion over its occasions, so a habit the enemy does not have never activates. */
class EnemyModelTest {
    private static final PatternSettings SETTINGS = PatternSettings.DEFAULT;

    @Test
    void everyPatternOneTokenActivatesIsPolledOnceEvenWhenTheLastChangeNamesOnlyOne() {
        var model = new EnemyModel(new UUID(0, 9), 4, 2);
        var activated = 0;
        for (var trial = 0; trial < 30 && model.activeMask() == 0; trial++) {
            model.learn(EnemyAction.STEP_OUT, SETTINGS);
            activated |= model.pollActivated();
        }
        var active = model.activeMask();
        assertTrue(Integer.bitCount(active) > 1, "one step out is the occasion of several patterns: " + active);
        assertEquals(active, activated, "DOC-01: each activation is polled, not only the last change");
        assertEquals(0, model.pollActivated(), "a poll clears what it returned");
        for (var trial = 0; trial < 5; trial++) {
            model.learn(EnemyAction.STEP_OUT, SETTINGS);
        }
        assertEquals(0, model.pollActivated(), "patterns that stay active are not activated again");
    }

    @Test
    void balancedStrafesStayInactiveBecauseEachSideIsTheFailureOfTheOther() {
        var model = new EnemyModel(new UUID(0, 1), 4, 2);
        for (var trial = 0; trial < 50; trial++) {
            model.learn(EnemyAction.STRAFE_LEFT, SETTINGS);
            model.learn(EnemyAction.STRAFE_RIGHT, SETTINGS);
        }
        assertEquals(0, model.activeMask(), "a 50/50 strafer has no predictable side");
        var left = model.tracker(EnemyPattern.PREDICTABLE_STRAFE_LEFT).evidence();
        assertEquals(100, left.trials(), "every strafe is a trial of both sides");
        var mean = left.alpha() / (left.alpha() + left.beta());
        assertTrue(mean > 0.45 && mean < 0.55, "half successes, half failures, with the forgetting of 0.98: " + mean);
    }

    @Test
    void aPredictableStraferActivatesOnlyHisSide() {
        var model = new EnemyModel(new UUID(0, 2), 4, 2);
        for (var round = 0; round < 20; round++) {
            for (var repeat = 0; repeat < 4; repeat++) {
                model.learn(EnemyAction.STRAFE_LEFT, SETTINGS);
            }
            model.learn(EnemyAction.STRAFE_RIGHT, SETTINGS);
        }
        assertTrue((model.activeMask() & EnemyPattern.PREDICTABLE_STRAFE_LEFT.bit()) != 0, "80 % left activates");
        assertEquals(0, model.activeMask() & EnemyPattern.PREDICTABLE_STRAFE_RIGHT.bit(), "20 % right does not");
    }

    @Test
    void aStrikeIsNoTrialOfAMovementHabitSoItDoesNotDiluteIt() {
        var model = new EnemyModel(new UUID(0, 3), 4, 2);
        for (var trial = 0; trial < 30; trial++) {
            model.learn(EnemyAction.STRAFE_LEFT, SETTINGS);
            model.learn(EnemyAction.STRIKE, SETTINGS);
        }
        assertTrue((model.activeMask() & EnemyPattern.PREDICTABLE_STRAFE_LEFT.bit()) != 0);
        assertEquals(30, model.tracker(EnemyPattern.PREDICTABLE_STRAFE_LEFT).evidence().trials());
        assertEquals(30, model.tracker(EnemyPattern.SPAMS_CLICKS).evidence().trials(),
                "a full strike is a failed trial of clicking early");
        assertEquals(0, model.activeMask() & EnemyPattern.SPAMS_CLICKS.bit());
    }

    @Test
    void aPlayerWhoRaisesTheShieldHalfTheTimeIsNotShieldless() {
        var model = new EnemyModel(new UUID(0, 4), 4, 2);
        for (var trial = 0; trial < 40; trial++) {
            model.learn(EnemyAction.STRIKE, SETTINGS);
            model.learn(EnemyAction.SHIELD_UP, SETTINGS);
        }
        assertEquals(0, model.activeMask() & EnemyPattern.NO_SHIELD.bit(), "theta 0.80 needs a real habit");
        assertFalse(EnemyOccasions.trial(EnemyPattern.NO_SHIELD, EnemyAction.STRAFE_LEFT));
        assertTrue(EnemyOccasions.trial(EnemyPattern.NO_SHIELD, EnemyAction.SHIELD_UP));
        assertFalse(EnemyOccasions.trial(EnemyPattern.FIXED_CADENCE, EnemyAction.IDLE));
    }

    @Test
    void theFightStyleCountsFailuresAsWellAsSuccesses() {
        var model = new EnemyModel(new UUID(0, 5), 4, 2);
        model.learn(EnemyAction.STRAFE_LEFT, SETTINGS);
        model.learn(EnemyAction.STRAFE_RIGHT, SETTINGS);
        var left = model.tracker(EnemyPattern.PREDICTABLE_STRAFE_LEFT).evidence();
        assertEquals(2, left.trials(), "the left strafe is the success and the right strafe the failure");
        assertTrue(left.beta() > akyrion.thepredict.domain.BetaEvidence.PRIOR, "the failure moved beta");
        assertTrue(left.alpha() > akyrion.thepredict.domain.BetaEvidence.PRIOR, "the success moved alpha");
    }

    @Test
    void aRestEndsAtItsTickAndAHabitStillAboveThetaComesBackActiveAndSignalsAgain() {
        var model = leftStrafer();
        var pattern = EnemyPattern.PREDICTABLE_STRAFE_LEFT;
        model.pollActivated();
        var before = model.activations(pattern);
        model.rest(pattern, 100L);
        assertEquals(0, model.activeMask() & pattern.bit(), "a rest drops the pattern from the active mask");
        assertEquals(0, model.pollActivated(), "a rest is no activation");
        assertFalse(model.endRests(99L), "the rest has not run out");
        assertEquals(PatternState.RESTING, model.tracker(pattern).state());
        assertTrue(model.endRests(100L), "PD-02: the rest closes at its tick");
        assertTrue((model.activeMask() & pattern.bit()) != 0, "the habit is still there, so the pattern is active");
        assertEquals(pattern.bit(), model.pollActivated() & pattern.bit(), "the return is signalled like an activation");
        assertEquals(before + 1, model.activations(pattern), "and counted as a transition into ACTIVE");
        assertFalse(model.endRests(200L), "a closed rest does not close twice");
    }

    @Test
    void aChangepointWhileActiveIsNoActivation() {
        var model = new EnemyModel(new UUID(0, 7), 4, 2);
        var pattern = EnemyPattern.PREDICTABLE_STRAFE_LEFT;
        var changepoints = 0;
        for (var step = 0; step < 140; step++) {
            var left = step < 100 ? step % 5 != 4 : step % 2 != 0;
            model.learn(left ? EnemyAction.STRAFE_LEFT : EnemyAction.STRAFE_RIGHT, SETTINGS);
            if (model.pollChange() == pattern.ordinal()
                    && model.changeCause() == PatternCause.CHANGEPOINT_HALVED.ordinal()) {
                changepoints++;
            }
        }
        assertEquals(PatternState.ACTIVE, model.tracker(pattern).state());
        assertTrue(changepoints >= 1, "the 50/50 tail trips the changepoint while the pattern stays active");
        assertEquals(1, model.activations(pattern), "PD-04: only the entry is a transition into ACTIVE");
    }

    private static EnemyModel leftStrafer() {
        var model = new EnemyModel(new UUID(0, 6), 4, 2);
        for (var step = 0; step < 100; step++) {
            model.learn(step % 5 != 4 ? EnemyAction.STRAFE_LEFT : EnemyAction.STRAFE_RIGHT, SETTINGS);
        }
        assertTrue((model.activeMask() & EnemyPattern.PREDICTABLE_STRAFE_LEFT.bit()) != 0, "80 % left activates");
        return model;
    }
}
