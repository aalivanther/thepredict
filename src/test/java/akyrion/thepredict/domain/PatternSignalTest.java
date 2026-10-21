package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DOC-01: one signal per activation, only where the vocabulary shows the patterns, and never on another change. */
class PatternSignalTest {
    @Test
    void anActivationSignalsInBiasAndFullAndNowhereElse() {
        var request = PatternSignals.of(EnemyPattern.SPAMS_CLICKS, PatternMode.BIAS).orElseThrow();
        assertEquals("PREDICTOR_PATTERN_SPAMS_CLICKS", request.signal());
        assertFalse(request.signal().startsWith("pattern_"));
        assertEquals(request, PatternSignals.of(EnemyPattern.SPAMS_CLICKS, PatternMode.FULL).orElseThrow());
        assertTrue(PatternSignals.of(EnemyPattern.SPAMS_CLICKS, PatternMode.OBSERVE).isEmpty(),
                "observe moves no skill of the author");
        assertTrue(PatternSignals.of(EnemyPattern.SPAMS_CLICKS, PatternMode.OFF).isEmpty());
    }

    @Test
    void onlyAnEntryIntoActiveIsAnActivation() {
        assertTrue(PatternSignals.activation(PatternCause.ENTER, PatternState.ACTIVE));
        assertTrue(PatternSignals.activation(PatternCause.REST_END, PatternState.ACTIVE));
        assertFalse(PatternSignals.activation(PatternCause.REST_END, PatternState.INACTIVE));
        assertFalse(PatternSignals.activation(PatternCause.REST, PatternState.RESTING), "resting emits nothing");
        assertFalse(PatternSignals.activation(PatternCause.CHANGEPOINT_HALVED, PatternState.ACTIVE),
                "a pattern that stays active is no new activation");
        assertFalse(PatternSignals.activation(PatternCause.EXIT_MEAN, PatternState.INACTIVE));
        assertFalse(PatternSignals.activation(PatternCause.ENCOUNTER_END, PatternState.INACTIVE));
    }
}
