package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FallbackPolicyTest {
    private static FallbackDecision decision(ControlLevel level, FallbackReason reason) {
        return new FallbackDecision(level, Optional.ofNullable(reason));
    }

    private static FallbackDecision level(ServiceState service, boolean budget, int streak, boolean session,
                                          ControlLevel intent, boolean local) {
        return FallbackPolicy.level(service, budget, streak, session, intent, local);
    }

    @Test
    void invalidSessionAlwaysGoesToBase() {
        assertEquals(decision(ControlLevel.BASE, FallbackReason.SESSION_INVALID),
                level(ServiceState.READY, true, 0, false, ControlLevel.JEV, true));
    }

    @Test
    void validJevOrAlternativeIntentKeepsItsLevelEvenWhenTheServiceDegrades() {
        assertEquals(decision(ControlLevel.JEV, null), level(ServiceState.PAUSED, false, 5, true, ControlLevel.JEV, true));
        assertEquals(decision(ControlLevel.ALTERNATIVE, null),
                level(ServiceState.READY, true, 0, true, ControlLevel.ALTERNATIVE, false));
    }

    @Test
    void pausedOrCheckingServiceGoesLocalWhenPossibleAndBaseOtherwise() {
        assertEquals(decision(ControlLevel.LOCAL, FallbackReason.SERVICE_PAUSED),
                level(ServiceState.PAUSED, true, 0, true, ControlLevel.BASE, true));
        assertEquals(decision(ControlLevel.BASE, FallbackReason.SERVICE_PAUSED),
                level(ServiceState.PAUSED, true, 0, true, ControlLevel.BASE, false));
        assertEquals(decision(ControlLevel.LOCAL, FallbackReason.SERVICE_PAUSED),
                level(ServiceState.CHECKING, true, 0, true, ControlLevel.BASE, true));
    }

    @Test
    void unusableServiceStatesAreBlocked() {
        for (var state : List.of(ServiceState.UNCONFIGURED, ServiceState.INVALID_CONFIGURATION,
                ServiceState.INVALID_CREDENTIALS, ServiceState.INVALID_MODEL)) {
            assertEquals(decision(ControlLevel.LOCAL, FallbackReason.SERVICE_BLOCKED),
                    level(state, true, 0, true, ControlLevel.BASE, true), state.name());
        }
    }

    @Test
    void exhaustedBudgetGoesLocal() {
        assertEquals(decision(ControlLevel.LOCAL, FallbackReason.NO_BUDGET),
                level(ServiceState.READY, false, 0, true, ControlLevel.BASE, true));
        assertEquals(decision(ControlLevel.BASE, FallbackReason.NO_BUDGET),
                level(ServiceState.READY, false, 0, true, ControlLevel.BASE, false));
    }

    @Test
    void lowConfidenceStreakGoesLocalFromThreeAnswers() {
        assertEquals(decision(ControlLevel.BASE, FallbackReason.EXPIRED),
                level(ServiceState.READY, true, 2, true, ControlLevel.BASE, true));
        assertEquals(decision(ControlLevel.LOCAL, FallbackReason.LOW_CONFIDENCE),
                level(ServiceState.READY, true, FallbackPolicy.LOW_CONFIDENCE_STREAK_FOR_LOCAL, true, ControlLevel.BASE, true));
    }

    @Test
    void validLocalIntentStaysLocalOnAHealthyService() {
        assertEquals(decision(ControlLevel.LOCAL, null), level(ServiceState.READY, true, 0, true, ControlLevel.LOCAL, true));
        assertEquals(decision(ControlLevel.BASE, FallbackReason.NO_BUDGET),
                level(ServiceState.READY, false, 0, true, ControlLevel.LOCAL, false));
    }

    @Test
    void localIntentWithoutALocalTacticGoesToBaseAsExpired() {
        assertEquals(decision(ControlLevel.BASE, FallbackReason.EXPIRED),
                level(ServiceState.READY, true, 0, true, ControlLevel.LOCAL, false));
    }

    @Test
    void noValidIntentOnAHealthyServiceGoesToBaseAsExpired() {
        assertEquals(decision(ControlLevel.BASE, FallbackReason.EXPIRED),
                level(ServiceState.READY, true, 0, true, ControlLevel.BASE, true));
    }

    @Test
    void negativeStreakIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> level(ServiceState.READY, true, -1, true, ControlLevel.BASE, true));
    }

    @Test
    void theConfiguredStreakReplacesTheDefaultOfThree() {
        assertEquals(decision(ControlLevel.LOCAL, FallbackReason.LOW_CONFIDENCE),
                FallbackPolicy.level(ServiceState.READY, true, 1, 1, true, ControlLevel.BASE, true));
        assertEquals(decision(ControlLevel.BASE, FallbackReason.EXPIRED),
                FallbackPolicy.level(ServiceState.READY, true, 3, 4, true, ControlLevel.BASE, true));
        assertEquals(FallbackPolicy.level(ServiceState.READY, true, 3, true, ControlLevel.BASE, true),
                FallbackPolicy.level(ServiceState.READY, true, 3, FallbackPolicy.LOW_CONFIDENCE_STREAK_FOR_LOCAL, true,
                        ControlLevel.BASE, true));
        assertThrows(IllegalArgumentException.class,
                () -> FallbackPolicy.level(ServiceState.READY, true, 0, 0, true, ControlLevel.BASE, true));
    }
}
