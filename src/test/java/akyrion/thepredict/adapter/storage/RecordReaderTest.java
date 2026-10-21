package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordReaderTest {
    @TempDir
    Path directory;

    @Test
    void joinsGzipSkipsACutLastLineAndRejectsVersionTwo() throws Exception {
        var planned = line("planned", 1, 1);
        var result = line("result", 1, 1);
        var other = line("planned", 2, 4);
        var cut = planned + "\n" + result + "\n" + other + "\n{\"v\":1,\"type\":";
        var parsed = RecordReader.parse(List.of(cut.split("\n", -1)));
        assertEquals(3, parsed.size());
        var joined = RecordReader.join(parsed);
        assertEquals(2, joined.size());
        assertEquals(List.of("planned", "result"), joined.getFirst().lines().stream().map(RecordReader.EventLine::type).toList());
        var gzip = directory.resolve("events.jsonl.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write((planned + "\n" + result + "\n").getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(1, RecordReader.join(RecordReader.read(gzip)).size());
        assertThrows(IllegalArgumentException.class, () -> RecordReader.parse(List.of(
                "{\"v\":2,\"type\":\"planned\",\"run\":\"r\",\"encounter\":1,\"sequence\":1}")));
    }

    private static String line(String type, long encounter, long sequence) {
        return RecordLine.format(new RecordEvent(type, "20261006T120000Z-0a1b", 1L, 2L, 3L, "arm",
                java.util.Map.of("encounter", Long.toString(encounter), "sequence", Long.toString(sequence))));
    }
}
