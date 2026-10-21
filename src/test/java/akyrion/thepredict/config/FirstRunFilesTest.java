package akyrion.thepredict.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstRunFilesTest {
    @TempDir
    Path root;

    @Test
    void createsAllSetupFilesAndNeverOverwritesAdministratorChanges() throws Exception {
        var setup = new FirstRunFiles();
        var locations = locations();

        assertEquals(7, setup.installMissing(locations).size());
        assertTrue(Files.exists(locations.masterFile()));
        assertTrue(Files.exists(locations.profilesDirectory().resolve("example.yml.example")));
        assertTrue(Files.exists(locations.dataFolder().resolve("examples/AI/ThePredictGuardian.yml")));
        var owned = locations.masterFile();
        var bytes = "administrator settings".getBytes();
        Files.write(owned, bytes);
        Files.setLastModifiedTime(owned, FileTime.fromMillis(1_000_000L));
        var modified = Files.getLastModifiedTime(owned);

        assertTrue(setup.installMissing(locations).isEmpty());
        assertEquals("administrator settings", Files.readString(owned));
        assertEquals(modified, Files.getLastModifiedTime(owned));
        assertEquals(bytes.length, Files.size(owned));
    }

    @Test
    void repairsOnlyMissingBundledFiles() throws Exception {
        var setup = new FirstRunFiles();
        var locations = locations();
        setup.installMissing(locations);
        var readme = locations.profilesDirectory().resolve("README.txt");
        Files.delete(readme);

        assertEquals(java.util.List.of("AIConfig/Profiles/README.txt"), setup.installMissing(locations));
        assertTrue(Files.exists(readme));
    }

    @Test
    void writesNothingOutsideAiConfigInsideMythicMobs() throws Exception {
        var setup = new FirstRunFiles();
        var locations = locations();
        var mob = locations.mythicDataFolder().resolve("Mobs/custom.yml");
        Files.createDirectories(mob.getParent());
        Files.writeString(mob, "keep");
        var before = Files.getLastModifiedTime(mob);

        setup.installMissing(locations);

        assertEquals("keep", Files.readString(mob));
        assertEquals(before, Files.getLastModifiedTime(mob));
        try (Stream<Path> walk = Files.walk(locations.mythicDataFolder())) {
            var outside = walk.filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(locations.aiConfig()))
                    .toList();
            assertEquals(java.util.List.of(mob), outside);
        }
    }

    private ConfigLocations locations() {
        return new ConfigLocations(root.resolve("MythicMobs"), root.resolve("ThePredict"));
    }
}
