package akyrion.thepredict.config;

import akyrion.thepredict.domain.distill.DistillMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A profile Distill.Mode above the global is stored as the minimum and warned. */
class ProfileModeLowersOnlyTest {
    @TempDir
    Path directory;

    @Test
    void assistAboveOffStoresOffAndWarns() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), profile("assist"));
        var loaded = new ProfileLoader().load(directory);
        assertEquals(DistillMode.OFF, loaded.profiles().get("Guardian").distillMode().orElseThrow());
        assertTrue(loaded.problems().stream().anyMatch(problem -> problem.path().contains("Distill.Mode")
                && problem.message().contains("is above distill.mode and uses the minimum")));
    }

    @Test
    void assistAboveShadowStoresShadowAndShadowItselfDoesNotWarn() throws Exception {
        var text = Files.readString(Path.of("src/main/resources/AIConfig/config.yml"));
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        yaml.set("distill.mode", "shadow");
        var settings = new PluginConfigurationLoader().parse(yaml);
        assertEquals(DistillMode.SHADOW, settings.distill().mode());
        Files.writeString(directory.resolve("high.yml"), profile("assist"));
        Files.writeString(directory.resolve("same.yml"), profile("shadow").replace("guardian_ai:", "guardian_same:")
                .replace("MythicID: Guardian", "MythicID: Other"));
        var loaded = new ProfileLoader(settings).load(directory);
        assertEquals(DistillMode.SHADOW, loaded.profiles().get("Guardian").distillMode().orElseThrow());
        assertEquals(DistillMode.SHADOW, loaded.profiles().get("Other").distillMode().orElseThrow());
        assertTrue(loaded.problems().stream().anyMatch(problem -> problem.file().contains("high.yml")
                && problem.message().contains("is above distill.mode and uses the minimum")));
        assertTrue(loaded.problems().stream().noneMatch(problem -> problem.file().contains("same.yml")
                && problem.message().contains("is above distill.mode")));
    }

    private static String profile(String mode) {
        return """
                schemaVersion: 2
                guardian_ai:
                  MythicID: Guardian
                  Objective: Survive
                  Choices:
                    melee: { MythicID: GuardianMelee, CD: 20 }
                  Tactics:
                    press_and_strike: { Movement: APPROACH, Choice: melee }
                    hold: { Movement: HOLD }
                  Distill:
                    Mode: %s
                """.formatted(mode);
    }
}
