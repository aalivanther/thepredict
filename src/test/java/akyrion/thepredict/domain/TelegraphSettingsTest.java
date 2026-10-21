package akyrion.thepredict.domain;

import akyrion.thepredict.config.ConfigurationException;
import akyrion.thepredict.config.PluginConfigurationLoader;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Crossed minima and the absence of a global poise key (M23.4, M23.7). */
class TelegraphSettingsTest {
    @Test
    void minimaRiseAndHeavyStaysAtLeastTen() {
        assertEquals(TelegraphSettings.DEFAULT, new TelegraphSettings(true, false, 0, 8, 16, 24, 0, 0, 3, 3, 3, 1, 32,
                10, 40, 10, 100, 25, ShortWindUp.DEFAULT, 20));
        assertThrows(IllegalArgumentException.class, () -> copy(0, 8, 9, 24));
        assertThrows(IllegalArgumentException.class, () -> copy(5, 4, 16, 24));
        assertThrows(IllegalArgumentException.class, () -> copy(0, 12, 10, 24));
        assertThrows(IllegalArgumentException.class, () -> copy(0, 8, 16, 12));
    }

    @Test
    void aGlobalPoiseKeyIsUnknown() throws InvalidConfigurationException {
        var configuration = new YamlConfiguration();
        configuration.loadFromString("schemaVersion: 2\npoise:\n  max: 0.15\n");
        var error = assertThrows(ConfigurationException.class, () -> new PluginConfigurationLoader().parse(configuration));
        assertEquals("AIConfig/config.yml: poise: is not a known field",
                error.toProblem(PluginConfigurationLoader.FILE).toString());
    }

    private static TelegraphSettings copy(int light, int medium, int heavy, int severe) {
        var settings = TelegraphSettings.DEFAULT;
        return new TelegraphSettings(settings.againstPlayers(), settings.againstEntities(), light, medium, heavy,
                severe, settings.blockTicks(), settings.dodgeTicks(), settings.backOffTicks(),
                settings.breakSightTicks(), settings.interruptTicks(), settings.deliveryMarginTicks(),
                settings.maxReleasesPerTick(), settings.whiffRecoveryTicks(), settings.whiffCooldownTicks(),
                settings.resultWindowTicks(), settings.commitmentPercent(), settings.entropyCommitmentPercent(),
                settings.shortWindUp(), settings.defaultRecoveryTicks());
    }
}
