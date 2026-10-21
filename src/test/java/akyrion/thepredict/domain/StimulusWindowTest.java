package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StimulusWindowTest {
    @Test
    void aFifthWindowDropsTheOldestAndATellRecordsTheDelay() {
        var window = new StimulusWindow();
        window.open(StimulusKind.AFTER_OWN_STRIKE, 0, 20);
        window.open(StimulusKind.TELL_OPEN, 10, 20);
        window.open(StimulusKind.AFTER_BLOCKED, 12, 16);
        window.open(StimulusKind.LOW_HEALTH, 14, 60);
        window.open(StimulusKind.PROJECTILE_INCOMING, 30, 10);
        assertFalse(window.open(StimulusKind.AFTER_OWN_STRIKE, 5));
        assertTrue(window.open(StimulusKind.TELL_OPEN, 17));
        assertTrue(window.open(StimulusKind.AFTER_BLOCKED, 17));
        assertEquals(7, window.reactionTicks(17));
    }
}
