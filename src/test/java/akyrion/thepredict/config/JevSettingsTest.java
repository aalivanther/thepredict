package akyrion.thepredict.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JevSettingsTest {
    @Test
    void standardValuesMatchTheSchemaTable() {
        var standard = JevSettings.standard();

        assertEquals(URI.create("https://api.typesafe.ai"), standard.baseUrl());
        assertEquals("jev-1.13.0", standard.model());
        assertEquals("TYPESAFE_API_KEY", standard.apiKeyEnvironment());
        assertEquals("", standard.apiKey());
        assertEquals(800, standard.timeoutMillis());
        assertEquals(Duration.ofMillis(1500), standard.connectTimeout());
        assertEquals(262_144, standard.maxResponseBytes());
        assertEquals(12_000, standard.maxStateChars());
    }

    @Test
    void baseUrlRequiresHttpsExceptForLocalhost() {
        assertDoesNotThrow(() -> withBaseUrl("https://api.typesafe.ai/v1"));
        assertDoesNotThrow(() -> withBaseUrl("http://localhost:8080"));
        assertDoesNotThrow(() -> withBaseUrl("http://127.0.0.1:9000"));
        for (var rejected : new String[] {"http://api.typesafe.ai", "ftp://localhost", "https://user:secret@api.typesafe.ai",
                "https://api.typesafe.ai?key=1", "https://api.typesafe.ai#top", "mailto:someone@example.com"}) {
            var error = assertThrows(ConfigurationException.class, () -> withBaseUrl(rejected), rejected);
            assertEquals("jev.baseUrl", error.path(), rejected);
        }
    }

    @Test
    void everyLimitRejectsValuesJustOutsideItsRange() {
        assertRange("jev.timeoutMillis", 100, 5000, value -> settings(value, 1500, 262_144, 12_000));
        assertRange("jev.connectTimeoutMillis", 100, 10_000, value -> settings(800, value, 262_144, 12_000));
        assertRange("jev.maxResponseBytes", 16_384, 4_194_304, value -> settings(800, 1500, value, 12_000));
        assertRange("jev.maxStateChars", 2000, JevSettings.MAX_STATE_CHARS, value -> settings(800, 1500, 262_144, value));
        assertEquals(45_000, JevSettings.MAX_STATE_CHARS);
    }

    @Test
    void modelMustNotBeEmpty() {
        var standard = JevSettings.standard();
        var error = assertThrows(ConfigurationException.class, () -> new JevSettings(standard.baseUrl(), " ",
                "", "", standard.timeout(), standard.connectTimeout(), 262_144, 12_000));
        assertEquals("jev.model", error.path());
    }

    @Test
    void apiKeyEnvironmentMustBeAVariableNameAndIsNeverEchoed() {
        for (var accepted : new String[] {"", "TYPESAFE_API_KEY", "_KEY", "my_key_2", "A" + "B".repeat(127)}) {
            assertDoesNotThrow(() -> withKey(accepted, ""), accepted);
        }
        for (var rejected : new String[] {"sk-abc123-secret-value", "has space", "1ABC", "A" + "B".repeat(128),
                "tsk_live$abc"}) {
            var error = assertThrows(ConfigurationException.class, () -> withKey(rejected, ""), rejected);
            assertEquals("jev.apiKeyEnvironment", error.path());
            assertFalse(error.getMessage().contains(rejected), error.getMessage());
            assertFalse(error.toProblem("config.yml").toString().contains(rejected));
        }
    }

    @Test
    void modelMustBeAModelIdAndIsNeverEchoed() {
        for (var accepted : new String[] {"jev-1.13.0", "jev_2", "J", "j".repeat(64)}) {
            assertDoesNotThrow(() -> withModel(accepted), accepted);
        }
        for (var rejected : new String[] {"sk live secret", "-jev", "jev/1.13", "j".repeat(65), "jev:latest"}) {
            var error = assertThrows(ConfigurationException.class, () -> withModel(rejected), rejected);
            assertEquals("jev.model", error.path());
            assertFalse(error.getMessage().contains(rejected), error.getMessage());
        }
    }

    @Test
    void aKeyThatFitsTheModelPatternIsKeptButNeverDisplayed() {
        var pasted = withModel("sk-SECRETVALUE_123");

        assertEquals("sk-SECRETVALUE_123", pasted.model());
        assertEquals("<custom model>", pasted.modelLabel());
        assertFalse(pasted.toString().contains("SECRETVALUE"), pasted.toString());
        assertEquals("jev-1.13.0", JevSettings.standard().modelLabel());
        assertEquals("jev-2.0.1", withModel("jev-2.0.1").modelLabel());
        assertEquals("kev-1.0", withModel("kev-1.0").modelLabel());
        assertEquals("<custom model>", withModel("jev-latest").modelLabel());
    }

    @Test
    void aCustomVariableNameIsNotShownInMessages() {
        var custom = withKey("ANY_VALID_NAME_123", "");

        assertEquals("<custom variable>", custom.apiKeyEnvironmentLabel());
        assertFalse(custom.toString().contains("ANY_VALID_NAME_123"));
        assertEquals("TYPESAFE_API_KEY", JevSettings.standard().apiKeyEnvironmentLabel());
        assertEquals("<none>", withKey("", "").apiKeyEnvironmentLabel());
        assertFalse(withBaseUrl("https://api.typesafe.ai/private/path").toString().contains("private"));
    }

    @Test
    void environmentVariableWinsOverTheKeyInTheFile() {
        var settings = withKey("MY_JEV_KEY", "file-key");
        var environment = Map.of("MY_JEV_KEY", "environment-key", "BLANK_KEY", " ");

        assertEquals("environment-key", settings.resolvedApiKey(environment::get));
        assertEquals("file-key", settings.resolvedApiKey(name -> null));
        assertEquals("file-key", withKey("BLANK_KEY", "file-key").resolvedApiKey(environment::get));
        assertEquals("file-key", withKey("", "file-key").resolvedApiKey(environment::get));
        assertTrue(settings.isConfigured(name -> null));
        assertFalse(withKey("MY_JEV_KEY", "").isConfigured(name -> null));
        assertTrue(withKey("MY_JEV_KEY", "").isConfigured(environment::get));
    }

    @Test
    void toStringNeverShowsTheKey() {
        var text = withKey("", "very-secret-value").toString();

        assertFalse(text.contains("very-secret-value"));
        assertTrue(text.contains("apiKey=<redacted>"));
        assertTrue(JevSettings.standard().toString().contains("apiKey=<empty>"));
    }

    @Test
    void readsThePartialSectionAndKeepsTheOtherDefaults() throws Exception {
        var configuration = new YamlConfiguration();
        configuration.loadFromString("jev:\n  model: 'jev-2.0.0'\n  apiKeyEnvironment: ''\n");
        var settings = JevSettings.from(configuration);

        assertEquals("jev-2.0.0", settings.model());
        assertEquals("", settings.apiKeyEnvironment());
        assertEquals(JevSettings.standard().baseUrl(), settings.baseUrl());
        assertEquals(800, settings.timeoutMillis());
    }

    private static JevSettings withBaseUrl(String url) {
        var standard = JevSettings.standard();
        return new JevSettings(URI.create(url), standard.model(), "", "", standard.timeout(), standard.connectTimeout(),
                standard.maxResponseBytes(), standard.maxStateChars());
    }

    private static JevSettings withModel(String model) {
        var standard = JevSettings.standard();
        return new JevSettings(standard.baseUrl(), model, "", "", standard.timeout(), standard.connectTimeout(),
                standard.maxResponseBytes(), standard.maxStateChars());
    }

    private static JevSettings withKey(String environment, String key) {
        var standard = JevSettings.standard();
        return new JevSettings(standard.baseUrl(), standard.model(), environment, key, standard.timeout(),
                standard.connectTimeout(), standard.maxResponseBytes(), standard.maxStateChars());
    }

    private static JevSettings settings(int timeout, int connect, int responseBytes, int stateChars) {
        return new JevSettings(JevSettings.DEFAULT_BASE_URL, JevSettings.DEFAULT_MODEL, "", "",
                Duration.ofMillis(timeout), Duration.ofMillis(connect), responseBytes, stateChars);
    }

    static void assertRange(String path, int minimum, int maximum, IntFunction<?> factory) {
        assertDoesNotThrow(() -> factory.apply(minimum), path);
        assertDoesNotThrow(() -> factory.apply(maximum), path);
        for (var outside : new int[] {minimum - 1, maximum + 1}) {
            var error = assertThrows(ConfigurationException.class, () -> factory.apply(outside), path + " " + outside);
            assertEquals(path, error.path());
            assertTrue(error.reason().endsWith("was " + outside), error.getMessage());
        }
    }
}
