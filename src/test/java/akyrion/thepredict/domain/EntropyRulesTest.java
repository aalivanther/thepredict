package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No production file reads raw player input (errata E22-6, completed by M26, errata E26-1 aplicada). C21 has no
 * exception for the old trend reader.
 */
class EntropyRulesTest {
    @Test
    void noProductionFileMentionsRawInput() throws Exception {
        var hits = new ArrayList<String>();
        try (var walk = Files.walk(Path.of("src/main/java"))) {
            for (var file : walk.filter(path -> path.toString().endsWith(".java")).toList()) {
                var text = Files.readString(file);
                if (text.contains("PlayerInputEvent") || text.contains("getCurrentInput")) {
                    hits.add(file.getFileName().toString());
                }
            }
        }
        assertTrue(hits.isEmpty(), hits.toString());
    }

    @Test
    void thePatternPackageDoesNotReadARivalOrRawInput() throws Exception {
        var banned = java.util.List.of("PlayerInputEvent", "getCurrentInput", "getInventory", "getEnderChest",
                "getFoodLevel", "getSaturation", "getLevel(", "MobGoals", "getBrain", "getTarget(", "IntentBoard",
                "MobSession", "getThreatTable", "getLocation(", "getVelocity(");
        var hits = new ArrayList<String>();
        var root = Path.of("src/main/java/akyrion/thepredict/adapter/server/patterns");
        try (var walk = Files.walk(root)) {
            for (var file : walk.filter(path -> path.toString().endsWith(".java")).toList()) {
                var text = Files.readString(file);
                for (var name : banned) {
                    if (text.contains(name)) {
                        hits.add(file.getFileName() + " " + name);
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), hits.toString());
    }
}
