package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Dwell, hysteresis and the describe default (M23.2). */
class StanceSettingsTest {
    @Test
    void dwellAndHysteresisStayInRangeAndDescribeIsOff() {
        assertFalse(StanceSettings.DEFAULT.describe());
        assertEquals(20, StanceSettings.DEFAULT.minDwellTicks());
        assertEquals(2, StanceSettings.DEFAULT.hysteresisCycles());
        assertEquals(4, settings(4, 1).minDwellTicks());
        assertEquals(200, settings(200, 7).minDwellTicks());
        assertEquals(7, settings(20, 7).hysteresisCycles());
        assertThrows(IllegalArgumentException.class, () -> settings(3, 2));
        assertThrows(IllegalArgumentException.class, () -> settings(201, 2));
        assertThrows(IllegalArgumentException.class, () -> settings(20, 0));
        assertThrows(IllegalArgumentException.class, () -> settings(20, 8));
    }

    private static StanceSettings settings(int dwell, int cycles) {
        return new StanceSettings(StanceNativeMode.DEFAULT, dwell, cycles, Map.of(), Map.of(), List.of(), List.of(),
                false);
    }
}
