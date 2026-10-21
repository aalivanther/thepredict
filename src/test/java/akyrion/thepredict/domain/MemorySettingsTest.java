package akyrion.thepredict.domain;

import akyrion.thepredict.config.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemorySettingsTest {
    @Test
    void defaultsMatchThePlanAndMysqlIsReserved() {
        var settings = MemorySettings.DEFAULT;
        assertEquals(MemoryMode.RECORD, settings.mode());
        assertEquals(0.9, settings.fightDecay(), 0.0);
        assertEquals(4, settings.minFightTrials());
        assertEquals(6, settings.seedMaxTrials());
        assertEquals("sqlite", settings.backend());
        assertEquals(60, settings.refreshSeconds());
        assertThrows(IllegalArgumentException.class, () -> new MemorySettings(MemoryMode.RECORD, 0.9, 30, 4, 0.25, 6,
                4, 3, 3, 0.2, 8, 2, 60, 64, 16, 10, 60, 8, 64, 8, 180, 1000, false, "mysql"));
        assertEquals("is reserved and not available in this release", MemoryConfiguration.MYSQL_UNAVAILABLE);
    }

    /** CAR-13: the reserved backend is refused with the path and an English reason that does not repeat the value. */
    @Test
    void theReservedBackendIsRefusedInEnglishWithItsPathAndWithoutItsValue() throws Exception {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.loadFromString("memory:\n  backend: mysql\n");
        var refused = assertThrows(akyrion.thepredict.config.ConfigurationException.class,
                () -> MemoryConfiguration.memory(yaml));
        var shown = refused.toProblem("AIConfig/config.yml").toString();
        assertEquals("AIConfig/config.yml: memory.backend: is reserved and not available in this release", shown);
        assertFalse(shown.contains("mysql"));
        assertTrue(shown.chars().allMatch(character -> character < 128));
    }

    @Test
    void aDecimalThatIsNotANumberIsRejectedLikeAnyOutOfRangeValue() {
        assertThrows(IllegalArgumentException.class, () -> new MemorySettings(MemoryMode.RECORD, Double.NaN, 30, 4,
                0.25, 6, 4, 3, 3, 0.2, 8, 2, 60, 64, 16, 10, 60, 8, 64, 8, 180, 1000, false, "sqlite"));
        assertThrows(IllegalArgumentException.class, () -> new MemorySettings(MemoryMode.RECORD, 0.9, 30, 4,
                Double.NaN, 6, 4, 3, 3, 0.2, 8, 2, 60, 64, 16, 10, 60, 8, 64, 8, 180, 1000, false, "sqlite"));
        assertThrows(IllegalArgumentException.class, () -> new MemorySettings(MemoryMode.RECORD, 0.9, 30, 4, 0.25,
                6, 4, 3, 3, Double.NaN, 8, 2, 60, 64, 16, 10, 60, 8, 64, 8, 180, 1000, false, "sqlite"));
        assertEquals(0.9, new MemorySettings(MemoryMode.RECORD, 0.9, 30, 4, 0.25, 6, 4, 3, 3, 0.2, 8, 2, 60, 64, 16,
                10, 60, 8, 64, 8, 180, 1000, false, "sqlite").fightDecay(), 0.0);
    }
}
