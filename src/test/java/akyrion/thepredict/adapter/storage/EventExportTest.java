package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventExportTest {
    @TempDir
    Path directory;

    @Test
    void gzippedDaysAndATypeFilterAreCopiedUnderExports() throws Exception {
        var day = directory.resolve("events").resolve("2026-10-06");
        Files.createDirectories(day);
        var line = "{\"v\":1,\"type\":\"planned\",\"run\":\"r\"}\n";
        try (var gzip = new GZIPOutputStream(Files.newOutputStream(day.resolve("events-r-0.jsonl.gz")))) {
            gzip.write(line.getBytes(StandardCharsets.UTF_8));
            gzip.write("{\"v\":1,\"type\":\"round\",\"run\":\"r\"}\n".getBytes(StandardCharsets.UTF_8));
        }
        var result = EventExport.write(directory, "2026-10-06 2026-10-06 planned");
        assertTrue(result.contains("events 1"));
        var exported = Files.readString(directory.resolve("exports").resolve("events-2026-10-06-2026-10-06.jsonl"));
        assertEquals(line.strip(), exported.strip());
    }
}
