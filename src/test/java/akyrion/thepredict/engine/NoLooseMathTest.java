package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule C16 in {@code domain} and {@code engine}, subpackages included (EFO-22, task 30 of the final round): a
 * transcendental function goes through {@link StrictMath}, so the simulator gives the same bytes on every JVM and CPU.
 * {@code Math.round}, {@code Math.max} and the like are exact and allowed.
 */
class NoLooseMathTest {
    private static final Path ENGINE = Path.of("src/main/java/akyrion/thepredict/engine");
    private static final Path DOMAIN = Path.of("src/main/java/akyrion/thepredict/domain");
    private static final Pattern LOOSE = Pattern.compile("(?<![A-Za-z])Math\\.(sin|cos|tan|asin|acos|atan|atan2|sinh"
            + "|cosh|tanh|exp|expm1|log|log10|log1p|pow|cbrt|hypot|toDegrees|toRadians)\\s*\\(");

    @Test
    void theDomainAndTheEngineCallOnlyStrictTranscendentalFunctions() throws IOException {
        var found = new ArrayList<String>();
        var engine = scan(ENGINE, found);
        var domain = scan(DOMAIN, found);
        assertTrue(engine > 200, "the engine sources and their subpackages were read: " + engine);
        assertTrue(domain > 300, "the domain sources and their subpackages were read: " + domain);
        assertEquals(new ArrayList<String>(), found);
    }

    /** Adds every loose call under {@code root}, subfolders included, and returns how many files were read. */
    private static int scan(Path root, List<String> found) throws IOException {
        assertTrue(Files.isDirectory(root), "run from the project folder: " + root.toAbsolutePath());
        var files = 0;
        try (var walk = Files.walk(root)) {
            for (var file : walk.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                files++;
                var lines = Files.readAllLines(file);
                for (var number = 0; number < lines.size(); number++) {
                    var line = lines.get(number).strip();
                    if (line.startsWith("*") || line.startsWith("/**")) {
                        continue;
                    }
                    if (LOOSE.matcher(line).find()) {
                        found.add(root.relativize(file) + ":" + (number + 1) + " " + line);
                    }
                }
            }
        }
        return files;
    }

    @Test
    void thePatternSeesALooseCallAndLeavesAStrictOneAlone() {
        assertTrue(LOOSE.matcher("var a = Math.atan2(-dx, dz);").find());
        assertTrue(LOOSE.matcher("var d = Math.hypot(x, z);").find());
        assertTrue(!LOOSE.matcher("var a = StrictMath.atan2(-dx, dz);").find());
        assertTrue(!LOOSE.matcher("var r = Math.round(x) + Math.max(a, b);").find());
    }
}
