package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The zone section of a profile only lowers the global mode (G11), and absent values are empty, never null. */
class ZoneSettingsTest {
    @Test
    void theEffectiveModeIsTheMinimumOfTheGlobalAndTheAskedMode() {
        for (var global : ZoneMode.values()) {
            assertEquals(global, ZoneSettings.DEFAULT.effective(global));
            for (var asked : ZoneMode.values()) {
                var settings = new ZoneSettings(Optional.of(asked), OptionalInt.empty(), true, HazardPush.OFF);
                var expected = asked.rank() < global.rank() ? asked : global;
                assertEquals(expected, settings.effective(global), asked + " under " + global);
                assertEquals(asked.rank() > global.rank(), settings.above(global));
            }
        }
    }

    @Test
    void absentModeAndRadiusAreEmptyOptionalsNotNull() {
        assertThrows(NullPointerException.class, () -> new ZoneSettings(null, OptionalInt.empty(), true,
                HazardPush.OFF));
        assertThrows(NullPointerException.class, () -> new ZoneSettings(Optional.empty(), null, true,
                HazardPush.OFF));
        assertThrows(IllegalArgumentException.class, () -> new ZoneSettings(Optional.empty(), OptionalInt.of(7), true,
                HazardPush.OFF));
    }
}
