package akyrion.thepredict.adapter.jev;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The live tools of M13 refuse to run without the explicit property and without the key in the environment
 * (M13.6: "la tarea falla sin clave o sin la propiedad explícita"), and never show the key. No test calls them for
 * real: the build has no task that runs them.
 */
class LiveJevGateTest {
    private static final UnaryOperator<String> NOTHING = name -> null;

    @Test
    void withoutTheExplicitPropertyTheToolsRefuseEvenWithAKey() {
        var refusal = LiveJevGate.refusal(NOTHING, environment("live-key-51ab"));
        assertTrue(refusal.orElseThrow().contains("-PliveJev=true"));
        assertFalse(refusal.orElseThrow().contains("51ab"));
        assertTrue(LiveJevGate.refusal(Map.of(LiveJevGate.PROPERTY, "yes")::get, environment("k")).isPresent());
    }

    @Test
    void withoutTheKeyInTheEnvironmentTheToolsRefuseAndNeverSuggestReadingDotEnv() {
        var refusal = LiveJevGate.refusal(enabled(), NOTHING).orElseThrow();
        assertTrue(refusal.contains(LiveJevGate.KEY));
        assertTrue(refusal.contains("never reads .env"));
        assertTrue(LiveJevGate.refusal(enabled(), environment("  ")).isPresent());
    }

    @Test
    void withBothTheToolsMayRunWithThePinnedModelAndTheProductionEndpoint() {
        assertTrue(LiveJevGate.refusal(enabled(), environment("live-key-51ab")).isEmpty());
        var settings = LiveJevGate.settings(enabled(), environment("live-key-51ab"));
        assertEquals("jev-1.13.0", settings.model());
        assertEquals(LiveJevGate.PRODUCTION, settings.baseUrl());
        assertFalse(settings.toString().contains("51ab"));
    }

    private static UnaryOperator<String> enabled() {
        return Map.of(LiveJevGate.PROPERTY, "true")::get;
    }

    private static UnaryOperator<String> environment(String key) {
        return Map.of(LiveJevGate.KEY, key)::get;
    }
}
