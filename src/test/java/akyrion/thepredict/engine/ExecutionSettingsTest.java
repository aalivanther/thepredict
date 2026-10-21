package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** The settings of M10 reject values no rule could use. */
class ExecutionSettingsTest {
    @Test
    void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionSettings(0, 3, "jev-1.13.0", false));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionSettings(1, 0, "jev-1.13.0", false));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionSettings(1, 3, " ", false));
        assertThrows(NullPointerException.class, () -> new ExecutionSettings(1, 3, null, false));
    }
}
