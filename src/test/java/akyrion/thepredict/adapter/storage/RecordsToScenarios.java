package akyrion.thepredict.adapter.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Counts joined requests in a records tree (M21.10). It does not call the network. M22 reads the same
 * {@link RecordReader} rows when it builds scenarios.
 */
public final class RecordsToScenarios {
    private RecordsToScenarios() {
    }

    /**
     * How many joined requests the lines contain. A cut last line does not count.
     *
     * @param lines JSONL text
     * @return the number of scenarios
     */
    public static int scenarios(List<String> lines) {
        return RecordReader.join(RecordReader.parse(lines)).size();
    }

    /**
     * Prints the scenario count of a records directory. No arguments prints the usage and returns.
     *
     * @param arguments the records directory, optional
     * @throws IOException when a segment cannot be read
     */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length == 0) {
            System.out.println("usage: RecordsToScenarios <records-directory>");
            return;
        }
        var lines = new ArrayList<String>();
        try (var walk = Files.walk(Path.of(arguments[0]))) {
            for (var file : walk.filter(path -> {
                var name = path.getFileName().toString();
                return name.endsWith(".jsonl") || name.endsWith(".jsonl.gz");
            }).toList()) {
                RecordReader.read(file).forEach(line -> lines.add(line.object().toString()));
            }
        }
        System.out.println(scenarios(lines));
    }
}
