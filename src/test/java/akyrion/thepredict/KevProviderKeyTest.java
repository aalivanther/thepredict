package akyrion.thepredict;

import akyrion.thepredict.config.JevProvider;
import akyrion.thepredict.config.JevSettings;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kev reads KEV_API_KEY only. It does not send TYPESAFE_API_KEY. Errata E29-9 applied. */
class KevProviderKeyTest {
    @Test
    void kevIgnoresTheTypesafeVariableAndRequiresLoopback() {
        var seen = new ArrayList<String>();
        UnaryOperator<String> environment = name -> {
            seen.add(name);
            return "TYPESAFE_API_KEY".equals(name) ? "secret-typesafe" : null;
        };
        var jev = new JevSettings(URI.create("http://127.0.0.1:9"), "kev-0.8b", "TYPESAFE_API_KEY", "stored",
                Duration.ofMillis(800), Duration.ofMillis(1500), 262_144, 12_000, JevProvider.KEV);
        assertEquals("", jev.resolvedApiKey(environment));
        assertTrue(jev.isConfigured(environment));
        assertEquals("jev.apiKey is ignored when jev.provider is kev", jev.ignoredKeyWarning().orElseThrow());
        assertFalse(seen.contains("TYPESAFE_API_KEY"));
        var settings = SystemOneWiring.settings(jev, environment, "0.0.1");
        assertFalse(settings.sendAuthorization());
        assertEquals("", settings.apiKey());
        assertThrows(akyrion.thepredict.config.ConfigurationException.class,
                () -> new JevSettings(URI.create("https://api.typesafe.ai"), "kev-0.8b", "", "",
                        Duration.ofMillis(800), Duration.ofMillis(1500), 262_144, 12_000, JevProvider.KEV));
    }
}
