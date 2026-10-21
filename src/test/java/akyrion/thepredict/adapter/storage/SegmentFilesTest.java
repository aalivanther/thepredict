package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.config.ConfigurationException;
import akyrion.thepredict.config.StorageSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentFilesTest {
    @TempDir
    Path directory;

    @Test
    void rotationFollowsSizeAgeAndUtcDayAndNeverReusesAnotherRun() throws Exception {
        var bySize = new SegmentFiles(directory.resolve("size"), "run-a", SegmentFiles.DISK, 8L, Long.MAX_VALUE);
        bySize.append("{\"v\":1}", 1_700_000_000_000L, 0L);
        bySize.append("{\"v\":1,\"pad\":\"0123456789\"}", 1_700_000_000_000L, 1L);
        bySize.close();
        assertTrue(files(directory.resolve("size")).size() >= 2);
        var byAge = new SegmentFiles(directory.resolve("age"), "run-a", SegmentFiles.DISK, Long.MAX_VALUE, 5L);
        byAge.append("{}", 1_700_000_000_000L, 0L);
        byAge.append("{}", 1_700_000_000_000L, 6L);
        byAge.close();
        assertEquals(2, files(directory.resolve("age")).size());
        var byDay = new SegmentFiles(directory.resolve("day"), "run-b", SegmentFiles.DISK, Long.MAX_VALUE, Long.MAX_VALUE);
        byDay.append("{}", 1_700_000_000_000L, 0L);
        byDay.append("{}", 1_700_000_000_000L + 86_400_000L, 1L);
        byDay.close();
        var names = files(directory.resolve("day")).stream().map(path -> path.getFileName().toString()).toList();
        assertEquals(2, names.size());
        assertTrue(names.stream().allMatch(name -> name.contains("run-b")));
        assertTrue(names.stream().noneMatch(name -> name.contains("run-a")));
        var yaml = new YamlConfiguration();
        yaml.set("records.directory", "../outside");
        assertThrows(ConfigurationException.class, () -> StorageSettings.from(yaml));
    }

    private static java.util.List<Path> files(Path root) throws Exception {
        try (var walk = Files.walk(root)) {
            return walk.filter(path -> path.getFileName().toString().endsWith(".jsonl")).toList();
        }
    }
}
