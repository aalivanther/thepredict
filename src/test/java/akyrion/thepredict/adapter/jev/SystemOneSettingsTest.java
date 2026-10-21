package akyrion.thepredict.adapter.jev;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemOneSettingsTest {
    @Test
    void toStringNeverShowsTheKey() {
        var text = JevFixtures.settings(URI.create("https://api.typesafe.ai")).toString();
        assertFalse(text.contains(JevFixtures.KEY), text);
        assertTrue(text.contains("apiKey=<redacted>"), text);
        assertTrue(text.contains("model=jev-1.13.0"), text);
    }

    @Test
    void aCustomModelAndTheUrlPathAreNotShown() {
        var settings = new SystemOneSettings(URI.create("https://proxy.example/private/path"), "sk-SECRETVALUE_123",
                JevFixtures.KEY, Duration.ofSeconds(1), Duration.ofMillis(800), 1024, 2000, "0.0.1");
        var text = settings.toString();
        assertFalse(text.contains("SECRETVALUE"), text);
        assertFalse(text.contains("private"), text);
        assertTrue(text.contains("baseUrl=https://proxy.example"), text);
    }

    @Test
    void endpointsAreResolvedBelowTheBaseUrl() {
        assertEquals(URI.create("https://api.typesafe.ai/v1/systemone"),
                JevFixtures.settings(URI.create("https://api.typesafe.ai")).endpoint("/v1/systemone"));
        assertEquals(URI.create("http://127.0.0.1:8080/v1/models"),
                JevFixtures.settings(URI.create("http://127.0.0.1:8080/")).endpoint("/v1/models"));
    }

    @Test
    void invalidValuesAreRejectedWithoutEchoingThem() {
        var exception = assertThrows(IllegalArgumentException.class, () -> new SystemOneSettings(
                URI.create("https://api.typesafe.ai"), "jev-1.13.0", "key with space " + JevFixtures.KEY,
                Duration.ofSeconds(1), Duration.ofMillis(800), 1024, 2000, "0.0.1"));
        assertFalse(exception.getMessage().contains(JevFixtures.KEY));
        assertThrows(IllegalArgumentException.class, () -> new SystemOneSettings(URI.create("https://api.typesafe.ai"),
                "jev-1.13.0", "", Duration.ofSeconds(1), Duration.ofMillis(800), 1024, 2000, "0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> new SystemOneSettings(URI.create("https://api.typesafe.ai"),
                "jev-1.13.0", JevFixtures.KEY, Duration.ZERO, Duration.ofMillis(800), 1024, 2000, "0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> new SystemOneSettings(URI.create("https://api.typesafe.ai"),
                "jev-1.13.0", JevFixtures.KEY, Duration.ofSeconds(1), Duration.ofMillis(800), 0, 2000, "0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> new SystemOneSettings(URI.create("relative/path"),
                "jev-1.13.0", JevFixtures.KEY, Duration.ofSeconds(1), Duration.ofMillis(800), 1024, 2000, "0.0.1"));
    }

    @Test
    void aKeyPastedIntoTheModelFieldIsRefusedWithoutEchoingEitherValue() {
        var same = assertThrows(IllegalArgumentException.class, () -> new SystemOneSettings(
                URI.create("https://api.typesafe.ai"), JevFixtures.KEY, JevFixtures.KEY, Duration.ofSeconds(1),
                Duration.ofMillis(800), 1024, 2000, "0.0.1"));
        assertFalse(same.getMessage().contains(JevFixtures.KEY), same.getMessage());
        var swapped = assertThrows(IllegalArgumentException.class, () -> new SystemOneSettings(
                URI.create("https://api.typesafe.ai"), "sk-SECRETVALUE_123", "jev-1.13.0", Duration.ofSeconds(1),
                Duration.ofMillis(800), 1024, 2000, "0.0.1"));
        assertFalse(swapped.getMessage().contains("SECRETVALUE"), swapped.getMessage());
        assertFalse(swapped.getMessage().contains("jev-1.13.0"), swapped.getMessage());
        assertTrue(swapped.getMessage().contains("not shown"));
        new SystemOneSettings(URI.create("http://127.0.0.1:9"), "kev-1.0", "", Duration.ofSeconds(1),
                Duration.ofMillis(800), 1024, 2000, "0.0.1", false);
    }

    @Test
    void plainHttpIsOnlyForLoopbackAndUserInformationIsRejected() {
        for (var url : new String[] {"http://api.typesafe.ai", "http://10.0.0.5:8080", "https://user:secret@api.typesafe.ai",
                "https://api.typesafe.ai?key=1", "ftp://127.0.0.1"}) {
            var exception = assertThrows(IllegalArgumentException.class, () -> JevFixtures.settings(URI.create(url)), url);
            assertFalse(exception.getMessage().contains("secret"), exception.getMessage());
        }
        for (var url : new String[] {"http://127.0.0.1:9", "http://localhost:9", "http://[::1]:9", "https://10.0.0.5"}) {
            JevFixtures.settings(URI.create(url));
        }
        assertTrue(JevFixtures.settings(URI.create("http://127.0.0.1:9/x")).toString().contains("baseUrl=http://127.0.0.1:9,"));
    }
}
