package akyrion.thepredict.golden;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Writes the golden references of M17.0 ({@code ./gradlew m17Goldens}, outside {@code check}): every file of
 * {@link GoldenSet} under the folder given as argument, and removes the files of that folder the set no longer has.
 * The references are reviewed and kept in the project in a step of their own; a wanted difference is regenerated in
 * another step and justified in the document of M17.
 */
public final class GoldenRecorder {
    private GoldenRecorder() {
    }

    /**
     * Writes the references.
     *
     * @param arguments the target folder, {@code src/test/resources/golden/m17} by default
     * @throws IOException if a file cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        var folder = arguments.length > 0 ? Path.of(arguments[0]) : GoldenSet.FOLDER;
        var files = GoldenSet.all();
        if (Files.exists(folder)) {
            try (Stream<Path> existing = Files.walk(folder)) {
                for (var path : existing.sorted(Comparator.reverseOrder()).toList()) {
                    var relative = folder.relativize(path).toString().replace('\\', '/');
                    if (Files.isRegularFile(path) && !files.containsKey(relative)) {
                        Files.delete(path);
                    }
                }
            }
        }
        long bytes = 0;
        for (var entry : files.entrySet()) {
            var path = folder.resolve(entry.getKey());
            Files.createDirectories(path.getParent());
            var content = entry.getValue().getBytes(StandardCharsets.UTF_8);
            Files.write(path, content);
            bytes += content.length;
        }
        System.out.println("M17.0 golden references: " + files.size() + " files, " + bytes + " bytes in " + folder);
    }
}
