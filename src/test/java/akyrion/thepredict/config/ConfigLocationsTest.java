package akyrion.thepredict.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLocationsTest {
    @TempDir
    Path root;

    @Test
    void humanConfigurationLivesInAiConfigAndGeneratedDataStaysInThePluginFolder() {
        var locations = locations();

        assertEquals(locations.mythicDataFolder().resolve("AIConfig/config.yml"), locations.masterFile());
        assertEquals(locations.mythicDataFolder().resolve("AIConfig/Profiles"), locations.profilesDirectory());
        assertEquals(locations.dataFolder().resolve("records"), locations.dataFolder().resolve("records"));
        assertEquals(locations.dataFolder().resolve("aggregates.db"), locations.dataFolder().resolve("aggregates.db"));
        assertEquals("AIConfig/config.yml", ConfigLocations.masterLabel());
        assertEquals("AIConfig/Profiles/guardian.yml.proposed", ConfigLocations.proposedProfile("guardian.yml"));
        assertTrue(locations.masterFile().startsWith(locations.aiConfig()));
        assertFalse(locations.dataFolder().startsWith(locations.mythicDataFolder()));
    }

    @Test
    void contentIsAMasterFileOrAProfileAndTemplatesDoNotCount() throws Exception {
        var locations = locations();
        assertFalse(locations.legacyHasContent());
        assertTrue(locations.aiConfigEmpty());

        Files.createDirectories(locations.legacyProfilesDirectory());
        Files.writeString(locations.legacyProfilesDirectory().resolve("README.txt"), "notes");
        Files.writeString(locations.legacyProfilesDirectory().resolve("example.yml.example"), "template");
        assertFalse(locations.legacyHasContent());

        Files.writeString(locations.legacyProfilesDirectory().resolve("guardian.yaml"), "schemaVersion: 2\n");
        assertTrue(locations.legacyHasContent());
        Files.createDirectories(locations.profilesDirectory());
        Files.writeString(locations.profilesDirectory().resolve("README.txt"), "notes");
        assertTrue(locations.aiConfigEmpty());
        Files.writeString(locations.masterFile(), "schemaVersion: 2\n");
        assertFalse(locations.aiConfigEmpty());
    }

    private ConfigLocations locations() {
        return new ConfigLocations(root.resolve("plugins/MythicMobs"), root.resolve("plugins/ThePredict"));
    }
}
