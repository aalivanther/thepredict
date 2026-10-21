package akyrion.thepredict.config;

import akyrion.thepredict.domain.PatternMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternSettingsTest {
    @TempDir
    Path directory;

    @Test
    void anUnknownKeyNamesItsPath() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
                schemaVersion: 2
                patterns:
                  theta: 0.5
                """);
        var problem = assertThrows(ConfigurationException.class, () -> new PluginConfigurationLoader().parse(yaml));
        assertTrue(problem.toProblem(PluginConfigurationLoader.FILE).toString().contains("patterns.theta"));
        assertTrue(problem.toProblem(PluginConfigurationLoader.FILE).toString().contains("is not a known field"));
    }

    @Test
    void aProfileAboveTheGlobalModeWarnsAndUsesTheMinimum() throws Exception {
        var source = Path.of("src/test/resources/profiles/purpose-shield.yml");
        var text = Files.readString(source) + """
                
                Patterns:
                  Mode: full
                """;
        var file = directory.resolve("purpose-shield.yml");
        var patched = text.replace("purpose_shield:", "purpose_shield:\n");
        if (!patched.contains("Patterns:")) {
            patched = Files.readString(source);
            var marker = "  Objective:";
            patched = patched.replace(marker, "  Patterns:\n    Mode: full\n" + marker);
        }
        Files.writeString(file, patched.contains("\n  Patterns:") ? patched : indented(Files.readString(source)));
        var loaded = new ProfileLoader().load(directory);
        assertTrue(loaded.problems().stream().anyMatch(problem -> problem.toString().contains(
                "is above Patterns.Mode and uses the minimum")));
        var profile = loaded.profiles().get("PurposeShieldMob");
        assertEquals(PatternMode.OBSERVE, profile.patterns().effective(PatternMode.OBSERVE));
    }

    private static String indented(String source) {
        var lines = source.split("\n", -1);
        var out = new StringBuilder();
        var inserted = false;
        for (var line : lines) {
            out.append(line).append('\n');
            if (!inserted && line.startsWith("  Objective:")) {
                out.append("  Patterns:\n    Mode: full\n");
                inserted = true;
            }
        }
        return out.toString();
    }
}
