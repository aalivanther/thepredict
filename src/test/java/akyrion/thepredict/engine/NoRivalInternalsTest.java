package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pattern sources never read the internals of a rival (C21): no input, inventory, hunger, level, goals, brain,
 * AI target, threat table or engine state of the other side. The folder must exist, so a moved package cannot make
 * the rule pass without reading anything (EFO-28).
 */
class NoRivalInternalsTest {
    private static final Path PATTERNS = Path.of("src/main/java/akyrion/thepredict/adapter/server/patterns");

    @Test
    void patternSourcesDoNotReadARival() throws Exception {
        var banned = List.of("getCurrentInput", "PlayerInputEvent", "getInventory(", "getEnderChest",
                "getFoodLevel", "getSaturation", "getLevel(", "MobGoals", "getBrain(", "getTarget(",
                "IntentBoard", "MobSession", "getThreatTable");
        assertTrue(Files.isDirectory(PATTERNS), "run from the project folder: " + PATTERNS.toAbsolutePath());
        var read = 0;
        try (var walk = Files.walk(PATTERNS)) {
            for (var path : walk.filter(file -> file.toString().endsWith(".java")).toList()) {
                var text = Files.readString(path);
                read++;
                for (var token : banned) {
                    assertTrue(!text.contains(token), path + " " + token);
                }
            }
        }
        assertTrue(read >= 5, "the pattern sources were read: " + read);
    }
}
