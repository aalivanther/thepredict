package akyrion.thepredict.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigurationLoaderTest {
    @TempDir
    Path directory;

    private final PluginConfigurationLoader loader = new PluginConfigurationLoader();

    @Test
    void bundledConfigurationDeclaresEveryKeyWithTheStandardValues() throws Exception {
        var path = directory.resolve("config.yml");
        try (var bundled = getClass().getResourceAsStream("/AIConfig/config.yml")) {
            Files.writeString(path, new String(bundled.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(PluginSettings.standard(), loader.load(path));
        var declared = new java.util.HashSet<>(PluginConfigurationLoader.ALLOWED_PATHS);
        declared.remove("decision.maxDecisionAgeMillis");
        declared.remove("difficulty.worlds");
        declared.remove("decision.maxReadyChecksPerTick");
        assertEquals(declared, loader.read(path).getKeys(true));
        assertEquals(1000, loader.load(path).decision().maxDecisionAgeMillis());
    }

    @Test
    void malformedYamlIsRejectedInsteadOfUsingDefaults() throws Exception {
        var path = directory.resolve("config.yml");
        Files.writeString(path, "schemaVersion: 2\njev: [unfinished\n");

        assertThrows(InvalidConfigurationException.class, () -> loader.load(path));
    }

    @Test
    void schemaVersionIsRequiredAndMustBeTwo() {
        assertEquals("AIConfig/config.yml: schemaVersion: is required and must be 2",
                problem("jev:\n  model: 'jev-1.13.0'\n"));
        assertEquals("AIConfig/config.yml: schemaVersion: must be 2, was 3", problem("schemaVersion: 3\n"));
        assertEquals("AIConfig/config.yml: schemaVersion: must be 2, was a text value", problem("schemaVersion: two\n"));
    }

    @Test
    void unknownFieldNamesTheFileAndTheFullPath() {
        assertEquals("AIConfig/config.yml: decision.searchRaduisBlocks: is not a known field",
                problem("schemaVersion: 2\ndecision:\n  searchRaduisBlocks: 32\n"));
        assertEquals("AIConfig/config.yml: extra: is not a known field", problem("schemaVersion: 2\nextra: true\n"));
    }

    @Test
    void schemaOneFieldInASchemaTwoFilePointsToTheMigration() {
        assertTrue(problem("schemaVersion: 2\nprovider:\n  model: x\n")
                .startsWith("AIConfig/config.yml: provider: is a schema 1 field"));
        assertTrue(problem("schemaVersion: 2\ndecision:\n  maxConcurrentRequests: 4\n")
                .startsWith("AIConfig/config.yml: decision.maxConcurrentRequests: is a schema 1 field"));
    }

    @Test
    void wrongTypesNameTheirPath() {
        assertEquals("AIConfig/config.yml: decision.logDecisions: must be true or false",
                problem("schemaVersion: 2\ndecision:\n  logDecisions: sometimes\n"));
        assertEquals("AIConfig/config.yml: jev.timeoutMillis: must be an integer",
                problem("schemaVersion: 2\njev:\n  timeoutMillis: 2.5\n"));
        assertEquals("AIConfig/config.yml: jev.model: must be a string", problem("schemaVersion: 2\njev:\n  model: 5\n"));
        assertEquals("AIConfig/config.yml: decision: must be a section", problem("schemaVersion: 2\ndecision: 3\n"));
        assertEquals("AIConfig/config.yml: jev.baseUrl: must be a valid URI",
                problem("schemaVersion: 2\njev:\n  baseUrl: 'https://bad host'\n"));
        assertEquals("AIConfig/config.yml: decision.maxRequestsPerMinute: is outside the integer range",
                problem("schemaVersion: 2\ndecision:\n  maxRequestsPerMinute: 999999999999\n"));
    }

    @Test
    void rangeErrorsNameTheirPath() {
        assertEquals("AIConfig/config.yml: decision.maxRequestsPerMinute: must be between 1 and 1200, was 0",
                problem("schemaVersion: 2\ndecision:\n  maxRequestsPerMinute: 0\n"));
        assertEquals("AIConfig/config.yml: profileDefaults.retreatSpeed: must be between 0.1 and 3.0, was 3.5",
                problem("schemaVersion: 2\nprofileDefaults:\n  retreatSpeed: 3.5\n"));
    }

    @Test
    void schemaOneFilesAreDetectedByTheirOwnFields() throws Exception {
        assertTrue(loader.isVersionOne(yaml("provider:\n  type: OPENROUTER\n")));
        assertTrue(loader.isVersionOne(yaml("decision:\n  maxConcurrentRequests: 4\n")));
        assertTrue(loader.isVersionOne(yaml("profileDefaults:\n  lowHealthRatio: 0.3\n")));
        assertFalse(loader.isVersionOne(yaml("schemaVersion: 2\nprovider:\n  type: OPENROUTER\n")));
        assertFalse(loader.isVersionOne(yaml("jev:\n  apiKey: old\n")));
        assertFalse(loader.isVersionOne(yaml("decision:\n  maxRequestsPerMinute: 30\n")));
    }

    @Test
    void loadRejectsASchemaOneFile() throws Exception {
        var path = directory.resolve("config.yml");
        Files.writeString(path, "provider:\n  type: OPENROUTER\n  apiKey: ''\n");

        var error = assertThrows(ConfigurationException.class, () -> loader.load(path));
        assertEquals("config.yml: uses schema 1; review the migration proposal under migration/",
                error.toProblem("config.yml").toString());
    }

    @Test
    void partialFileKeepsTheOtherDefaults() throws Exception {
        var settings = loader.parse(yaml("schemaVersion: 2\njev:\n  timeoutMillis: 600\ndecision:\n  maxInFlightRequests: 2\n"));

        assertEquals(600, settings.jev().timeoutMillis());
        assertEquals(2, settings.decision().maxInFlightRequests());
        assertEquals(2, settings.decision().maxInFlightRequestsPerWorld());
        assertEquals(800, settings.decision().maxDecisionAgeMillis());
        assertEquals(ProfileDefaults.standard(), settings.profileDefaults());
    }

    private String problem(String text) {
        var error = assertThrows(ConfigurationException.class, () -> loader.parse(yaml(text)));
        return error.toProblem(PluginConfigurationLoader.FILE).toString();
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        var configuration = new YamlConfiguration();
        configuration.loadFromString(text);
        return configuration;
    }

    @Test
    void aKeyPastedIntoTextFieldsIsRejectedWithoutBeingEchoed() {
        var secret = "sk-live-abc123secret";
        for (var text : new String[] {
                "schemaVersion: 2\njev:\n  apiKeyEnvironment: '" + secret + "'\n",
                "schemaVersion: 2\njev:\n  model: '" + secret + " '\n",
                "schemaVersion: '" + secret + "'\n"}) {
            var message = problem(text);
            assertFalse(message.contains(secret), message);
            assertFalse(message.contains("abc123"), message);
        }
    }
}
