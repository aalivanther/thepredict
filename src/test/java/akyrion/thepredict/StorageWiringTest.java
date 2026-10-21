package akyrion.thepredict;

import akyrion.thepredict.adapter.storage.StorageLimits;
import akyrion.thepredict.config.ConfigProblem;
import akyrion.thepredict.config.PluginConfigurationLoader;
import akyrion.thepredict.config.PluginSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** JS-05 and CAR-19: the writer gets the configured A5 caps, and an invalid {@code config.yml} opens nothing. */
class StorageWiringTest {
    @TempDir
    Path directory;

    @Test
    void theSpecCarriesTheCapsOfTheConfiguredSections() throws Exception {
        var settings = parse("schemaVersion: 2\nmemory:\n  maxRows: 5000\n  retentionDays: 30\n");
        var spec = StorageWiring.spec(directory, settings, "schemaVersion: 2\n", "0.0.1",
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertEquals(StorageLimits.of(settings.memory(), settings.learning(), settings.distill()), spec.limits());
        assertEquals(5000, spec.limits().memoryRows());
        assertNotEquals(StorageLimits.DEFAULTS, spec.limits());
        assertEquals(directory.resolve("records"), spec.directory());
        assertEquals(StorageWiring.REPLY_CAPACITY, spec.replyCapacity());
    }

    @Test
    void anInvalidConfigurationStartsNoWriterAndBothStoresOffOpenOneForTheVerdicts() throws Exception {
        var problem = ConfigProblem.error(PluginConfigurationLoader.FILE, "zone.mode", "must be off|observe|guard|full");
        assertEquals(Optional.of("off"), StorageWiring.idleWord(Optional.of(problem)));
        assertEquals(Optional.empty(), StorageWiring.idleWord(Optional.empty()));
    }

    @Test
    void theSpecCarriesTheConfiguredGlobalModesForAutoconfigApply() throws Exception {
        var settings = parse("schemaVersion: 2\nzone:\n  mode: guard\n");
        var spec = StorageWiring.spec(Path.of("data"), settings, "", "test", java.time.Clock.systemUTC());
        assertEquals(akyrion.thepredict.domain.ZoneMode.GUARD, spec.overlayModes().zone());
    }

    private static PluginSettings parse(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return new PluginConfigurationLoader().parse(yaml);
    }
}
