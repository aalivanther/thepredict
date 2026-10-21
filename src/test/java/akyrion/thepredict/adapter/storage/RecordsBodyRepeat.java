package akyrion.thepredict.adapter.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/**
 * Counts repeated {@code body_sha256} values (M21.10, DT-53). No arguments prints the usage and returns.
 */
public final class RecordsBodyRepeat {
    private RecordsBodyRepeat() {
    }

    /**
     * Prints how many distinct bodies repeat.
     *
     * @param arguments the records directory, optional
     * @throws IOException when a segment cannot be read
     */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length == 0) {
            System.out.println("usage: RecordsBodyRepeat <records-directory>");
            return;
        }
        var counts = new LinkedHashMap<String, Integer>();
        try (var walk = Files.walk(Path.of(arguments[0]))) {
            for (var file : walk.filter(path -> {
                var name = path.getFileName().toString();
                return name.endsWith(".jsonl") || name.endsWith(".jsonl.gz");
            }).toList()) {
                for (var line : RecordReader.read(file)) {
                    if (line.object().has("body_sha256") && !line.object().get("body_sha256").isJsonNull()) {
                        counts.merge(line.object().get("body_sha256").getAsString(), 1, Integer::sum);
                    }
                }
            }
        }
        var repeated = counts.values().stream().filter(count -> count > 1).count();
        System.out.println("repeated " + repeated + " of " + counts.size());
    }
}
