package akyrion.thepredict.adapter.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/**
 * Summarises a records directory by type (M21.10). No arguments prints the usage and returns.
 */
public final class RecordsReport {
    private RecordsReport() {
    }

    /**
     * Counts lines by type.
     *
     * @param arguments the records directory, optional
     * @throws IOException when a segment cannot be read
     */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length == 0) {
            System.out.println("usage: RecordsReport <records-directory>");
            return;
        }
        var counts = new LinkedHashMap<String, Integer>();
        try (var walk = Files.walk(Path.of(arguments[0]))) {
            for (var file : walk.filter(path -> {
                var name = path.getFileName().toString();
                return name.endsWith(".jsonl") || name.endsWith(".jsonl.gz");
            }).toList()) {
                for (var line : RecordReader.read(file)) {
                    counts.merge(line.type(), 1, Integer::sum);
                }
            }
        }
        counts.forEach((type, count) -> System.out.println(type + " " + count));
    }
}
