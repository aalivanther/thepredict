package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Profile difficulty inherits T and rejects a target outside 5–3600 (M22.9, errata E22-2, E22-3). */
class DifficultySettingsTest {
    @Test
    void anAbsentTargetIsLegalAndAShortTargetIsNot() {
        assertFalse(DifficultySettings.DEFAULT.targetSeconds().isPresent());
        assertTrue(DifficultySettings.DEFAULT.learn());
        assertEquals(NativeLevelMode.OFF, DifficultySettings.DEFAULT.nativeLevel());
        assertThrows(IllegalArgumentException.class, () -> new DifficultySettings(
                java.util.Optional.empty(), OptionalInt.of(4), java.util.OptionalDouble.empty(), 0, 8,
                OptionalInt.empty(), true, true, NativeLevelMode.OFF));
        assertEquals(3600, new DifficultySettings(java.util.Optional.of(DifficultyMode.BENCH), OptionalInt.of(3600),
                java.util.OptionalDouble.empty(), 0, 8, OptionalInt.empty(), true, true, NativeLevelMode.OFF)
                .targetSeconds().getAsInt());
        assertEquals(0.25, DifficultyGlobals.DEFAULT.learningGain());
        assertEquals(0.20, DifficultyGlobals.DEFAULT.toleranceRatio());
        assertEquals(100, DifficultyGlobals.DEFAULT.adjustIntervalTicks());
        assertEquals(Math.min(0.25, 0.06 / 0.18), DifficultyGlobals.DEFAULT.effectiveGain(), 1e-12);
    }
}
