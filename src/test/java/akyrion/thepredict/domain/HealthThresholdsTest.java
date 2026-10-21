package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthThresholdsTest {
    @Test
    void documentedDefaultsAreValid() {
        assertEquals(0.3, new HealthThresholds(0.3, 0.6).criticalRatio());
    }

    @Test
    void ratiosMustBeOrderedInsideTheOpenUnitInterval() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new HealthThresholds(0, 0.6))
                .getMessage().contains("criticalRatio"));
        assertThrows(IllegalArgumentException.class, () -> new HealthThresholds(0.6, 0.6));
        assertThrows(IllegalArgumentException.class, () -> new HealthThresholds(0.3, 1));
        assertThrows(IllegalArgumentException.class, () -> new HealthThresholds(Double.NaN, 0.6));
    }
}
