package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EngineSettingsTest {
    @Test
    void theDerivedValuesFollowTheLimits() {
        var settings = CoordinatorFixture.settings(2000);
        assertEquals(18, settings.inboxCapacity());
        assertEquals(1_800_000_000L, settings.watchdogNanos());
        assertEquals(2_000_000L, settings.maxTickWorkNanos());
    }

    @Test
    void everyLimitMustBePositiveAndNoWorldMayExceedTheServer() {
        assertThrows(IllegalArgumentException.class, () -> new EngineSettings(0, 0, 8, 4, 600_000, 10, 2, 64, 16, 64,
                2000, 800_000_000L));
        assertThrows(IllegalArgumentException.class, () -> new EngineSettings(600, 700, 8, 4, 600_000, 10, 2, 64, 16,
                64, 2000, 800_000_000L));
        assertThrows(IllegalArgumentException.class, () -> new EngineSettings(600, 300, 8, 9, 600_000, 10, 2, 64, 16,
                64, 2000, 800_000_000L));
        assertThrows(IllegalArgumentException.class, () -> new EngineSettings(600, 300, 8, 4, 600_000, 10, 2, 64, 16,
                64, 0, 800_000_000L));
        assertThrows(IllegalArgumentException.class, () -> new EngineSettings(600, 300, 8, 4, 600_000, 10, 2, 64, 16,
                64, 2000, 0));
    }
}
