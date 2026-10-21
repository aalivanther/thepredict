package akyrion.thepredict.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The normalized profile (M22.5) behind {@code baseProfileSha256} (M29.8). */
class ProfileNormalFormTest {
    private static final String PROFILE = """
            schemaVersion: 2
            guardian_ai:
              MythicID: Guardian
              Objective: Survive
              Choices:
                melee: { MythicID: GuardianMelee, CD: 20 }
              Tactics:
                press_and_strike: { Movement: APPROACH, Choice: melee }
                hold: { Movement: HOLD }
              Contexts:
                wounded: { Conditions: ['healthpercent{p=<30%}'], Tactics: [hold] }
            """;

    @TempDir
    Path directory;

    @Test
    void theOrderOfTheKeysCommentsAndSpacingDoNotChangeTheFingerprint() throws Exception {
        var reordered = """
                # the same profile, written in another order
                guardian_ai:
                  Tactics:
                    hold:
                      Movement: HOLD
                    press_and_strike: { Choice: melee, Movement: APPROACH }
                  Contexts:
                    wounded:
                      Tactics: [hold]
                      Conditions:
                        - 'healthpercent{p=<30%}'
                  Objective: Survive
                  Choices:
                    melee: {CD: 20, MythicID: GuardianMelee}
                  MythicID: Guardian
                schemaVersion: 2
                """;
        assertEquals(fingerprint(PROFILE), fingerprint(reordered));
        assertEquals(64, fingerprint(PROFILE).length());
    }

    @Test
    void anyValueChangesTheFingerprint() throws Exception {
        var base = fingerprint(PROFILE);
        assertNotEquals(base, fingerprint(PROFILE.replace("CD: 20", "CD: 21")));
        assertNotEquals(base, fingerprint(PROFILE.replace("Survive", "Survive!")));
        assertNotEquals(base, fingerprint(PROFILE.replace("Tactics: [hold]", "Tactics: [hold, press_and_strike]")));
        assertNotEquals(base, fingerprint(PROFILE + "  Decision:\n    Diversity: \"off\"\n"));
    }

    @Test
    void aValueWithALineBreakKeepsItsOwnLine() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("p:\n  a: \"x\\nb=y\"\n");
        var lines = ProfileNormalForm.lines(yaml.getConfigurationSection("p"));
        assertEquals(1, lines.size());
        assertTrue(lines.getFirst().startsWith("a=x\\nb=y"), lines.toString());
    }

    @Test
    void theLoaderPublishesTheFingerprintOfEveryValidProfile() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), PROFILE);
        var result = new ProfileLoader().load(directory);
        assertEquals(fingerprint(PROFILE), result.fingerprints().get("guardian_ai"));
        assertEquals(1, result.fingerprints().size());
    }

    private static String fingerprint(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return ProfileNormalForm.fingerprint(yaml.getConfigurationSection("guardian_ai"));
    }
}
