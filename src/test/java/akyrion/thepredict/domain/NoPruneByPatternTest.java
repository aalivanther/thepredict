package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NoPruneByPatternTest {
    @Test
    void patternSourcesDoNotPruneCandidates() throws Exception {
        var root = Path.of("src/main/java/akyrion/thepredict");
        try (var walk = Files.walk(root)) {
            var pruned = walk.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        var name = path.getFileName().toString();
                        return name.startsWith("Pattern") || name.startsWith("Enemy") || name.startsWith("Counter")
                                || name.startsWith("Exploit") || name.startsWith("Variety")
                                || name.startsWith("Reservation") || name.startsWith("Rest")
                                || name.startsWith("FixedCadence") || name.equals("CueRequest.java");
                    })
                    .filter(path -> {
                        try {
                            var text = Files.readString(path);
                            return text.contains("prune(") || text.contains("rule 8");
                        } catch (Exception exception) {
                            return false;
                        }
                    }).findAny();
            assertFalse(pruned.isPresent());
        }
    }
}
