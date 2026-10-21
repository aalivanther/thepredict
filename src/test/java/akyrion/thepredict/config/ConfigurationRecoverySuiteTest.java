package akyrion.thepredict.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationRecoverySuiteTest {
    @TempDir
    Path directory;

    private ConfigLocations locations() {
        return new ConfigLocations(directory.resolve("MythicMobs"), directory.resolve("ThePredict"));
    }

    @Test
    void startupFilesSurviveMalformedConfigurationAndRecoverAfterAdministratorFixesIt() throws Exception {
        var installer = new FirstRunFiles();
        assertEquals(7, installer.installMissing(locations()).size());
        var configurationPath = locations().masterFile();
        var bundled = Files.readString(configurationPath);
        Files.writeString(configurationPath, "schemaVersion: 2\njev: [broken\n");
        assertThrows(InvalidConfigurationException.class, () -> new PluginConfigurationLoader().load(configurationPath));
        assertTrue(installer.installMissing(locations()).isEmpty());
        assertEquals("schemaVersion: 2\njev: [broken\n", Files.readString(configurationPath));

        Files.writeString(configurationPath, bundled.replace("minimumDecisionIntervalTicks: 20",
                "minimumDecisionIntervalTicks: 15").replace("apiKey: ''", "apiKey: 'temporary-test-key'"));
        var settings = new PluginConfigurationLoader().load(configurationPath);
        assertEquals(15, settings.profileDefaults().minimumDecisionIntervalTicks());
        assertTrue(settings.jev().isConfigured(name -> null));
    }

    @Test
    void profileReloadRejectsTyposAndDuplicatesThenRestoresTheCorrectExample() throws Exception {
        new FirstRunFiles().installMissing(locations());
        var active = locations().profilesDirectory().resolve("guardian.yml");
        var example = Files.readString(locations().dataFolder().resolve("examples/AI/ThePredictGuardian.yml"));
        Files.writeString(active, example.replace("RequiresTarget: false", "RequiresTarget: false\n      Colldown: 40"));
        var loader = new ProfileLoader();
        var typo = loader.load(locations().profilesDirectory());
        assertTrue(typo.profiles().isEmpty());
        assertEquals("AIConfig/Profiles/guardian.yml: thepredict_guardian.Choices.defense.Colldown: is not a known field",
                typo.problems().getFirst().toString());

        Files.writeString(active, example);
        var valid = loader.load(locations().profilesDirectory());
        assertEquals(Set.of("ThePredictGuardian"), valid.profiles().keySet());
        assertEquals(4, valid.profiles().get("ThePredictGuardian").choices().size());

        var duplicate = locations().profilesDirectory().resolve("duplicate.yml");
        Files.writeString(duplicate, example.replace("thepredict_guardian:", "other_guardian:"));
        var collision = loader.load(locations().profilesDirectory());
        assertTrue(collision.profiles().isEmpty());
        assertTrue(collision.problems().getFirst().message().contains("has 2 profiles"));

        Files.delete(duplicate);
        assertEquals(1, loader.load(locations().profilesDirectory()).profiles().size());
        assertTrue(new FirstRunFiles().installMissing(locations()).isEmpty());
    }

    @Test
    void aFreshConfigurationHasNoKeySoJevStaysUnconfigured() throws Exception {
        new FirstRunFiles().installMissing(locations());
        var configurationPath = locations().masterFile();
        var bundled = Files.readString(configurationPath);
        assertTrue(bundled.contains("provider: typesafe"));
        assertFalse(bundled.contains("openrouter"));
        var settings = new PluginConfigurationLoader().load(configurationPath);
        assertFalse(settings.jev().isConfigured(name -> null));
        assertTrue(settings.jev().isConfigured(name -> name.equals("TYPESAFE_API_KEY") ? "from-environment" : null));
        assertFalse(settings.jev().toString().contains("from-environment"));
    }
}
