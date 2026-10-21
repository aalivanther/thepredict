package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionTimingTest {
    private static String error(int heartbeat, int interval, int maximum, double confidence, double margin,
                                int contingencies) {
        return assertThrows(IllegalArgumentException.class,
                () -> new DecisionTiming(heartbeat, interval, maximum, confidence, margin, contingencies)).getMessage();
    }

    @Test
    void boundaryValuesAreAccepted() {
        var timing = new DecisionTiming(20, 20, 20, 0, 0, 0);
        assertEquals(20, timing.heartbeatTicks());
        assertEquals(2, new DecisionTiming(40, 20, 100, 0.99, 0.99, 2).contingenciesPerMob());
    }

    @Test
    void heartbeatMustNotBeShorterThanTheMinimumInterval() {
        assertTrue(error(19, 20, 100, 0.5, 0.2, 1).contains("heartbeatTicks"));
    }

    @Test
    void maximumIntentMustNotBeShorterThanTheMinimumInterval() {
        assertTrue(error(40, 20, 19, 0.5, 0.2, 1).contains("maximumIntentTicks"));
    }

    @Test
    void tickValuesMustBePositive() {
        assertTrue(error(40, 0, 100, 0.5, 0.2, 1).contains("minimumDecisionIntervalTicks"));
    }

    @Test
    void confidenceAndMarginMustBeInsideTheHalfOpenUnitInterval() {
        assertTrue(error(40, 20, 100, 1, 0.2, 1).contains("minimumConfidence"));
        assertTrue(error(40, 20, 100, -0.1, 0.2, 1).contains("minimumConfidence"));
        assertTrue(error(40, 20, 100, 0.5, 1, 1).contains("switchMargin"));
        assertTrue(error(40, 20, 100, 0.5, Double.NaN, 1).contains("switchMargin"));
    }

    @Test
    void contingenciesPerMobMustBeBetweenZeroAndTwo() {
        assertTrue(error(40, 20, 100, 0.5, 0.2, 3).contains("contingenciesPerMob"));
        assertTrue(error(40, 20, 100, 0.5, 0.2, -1).contains("contingenciesPerMob"));
    }

    @Test
    void theDetailDefaultsToFullWithoutFitChecksAndFitChecksAreSentOnlyWithFull() {
        var standard = new DecisionTiming(40, 20, 100, 0.5, 0.2, 2);
        assertEquals(QuestionDetail.FULL, standard.questionDetail());
        assertEquals(QuestionDetail.DEFAULT, standard.questionDetail());
        assertFalse(standard.fitChecks());
        assertFalse(new DecisionTiming(40, 20, 100, 0.5, 0.2, 2, QuestionDetail.STANDARD, true).sendsFitChecks());
        assertTrue(new DecisionTiming(40, 20, 100, 0.5, 0.2, 2, QuestionDetail.FULL, true).sendsFitChecks());
        assertFalse(new DecisionTiming(40, 20, 100, 0.5, 0.2, 2, QuestionDetail.FULL, false).sendsFitChecks());
        assertThrows(NullPointerException.class, () -> new DecisionTiming(40, 20, 100, 0.5, 0.2, 2, null, false));
    }

    @Test
    void compatibilityConstructorsKeepIntentOnlyHorizonZeroAndTacticOnly() {
        var six = new DecisionTiming(40, 20, 100, 0.5, 0.2, 2);
        var eight = new DecisionTiming(40, 20, 100, 0.5, 0.2, 2, QuestionDetail.FULL, true);
        var ten = new DecisionTiming(40, 20, 100, 0.5, 0.2, 2, QuestionDetail.STANDARD, false, CastMode.ANY_READY, 20);
        for (var timing : new DecisionTiming[] {six, eight}) {
            assertEquals(CastMode.INTENT_ONLY, timing.castMode());
            assertEquals(0, timing.readyHorizonTicks());
            assertEquals(UntypedCastMode.TACTIC_ONLY, timing.untypedCast());
        }
        assertEquals(CastMode.DEFAULT, ten.castMode());
        assertEquals(20, ten.readyHorizonTicks());
        assertEquals(UntypedCastMode.DEFAULT, ten.untypedCast());
        var full = new DecisionTiming(40, 20, 100, 0.5, 0.2, 2, QuestionDetail.STANDARD, true, CastMode.ANY_READY, 100,
                UntypedCastMode.ANY_READY);
        var copied = full.withCasting(CastMode.INTENT_ONLY, 0);
        assertEquals(CastMode.INTENT_ONLY, copied.castMode());
        assertEquals(0, copied.readyHorizonTicks());
        assertEquals(UntypedCastMode.ANY_READY, copied.untypedCast());
        assertEquals(QuestionDetail.STANDARD, copied.questionDetail());
        assertTrue(copied.fitChecks());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> full.withCasting(CastMode.ANY_READY, 101))
                .getMessage().contains("readyHorizonTicks"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> full.withCasting(CastMode.ANY_READY, -1))
                .getMessage().contains("readyHorizonTicks"));
    }
}
