package akyrion.thepredict.adapter.jev;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Gate of the live tools of M13 (M13.4, M13.6), which call the real TypeSafe account and cost money: they run only with
 * the explicit property {@code thepredict.liveJev=true}, which {@code ./gradlew … -PliveJev=true} sets, and with
 * {@code TYPESAFE_API_KEY} in the environment (rule W6: the key is only read from the environment, never from
 * {@code .env}, and never printed). The build never runs these tools.
 */
final class LiveJevGate {
    /** System property that must be {@code true}. */
    static final String PROPERTY = "thepredict.liveJev";
    /** Environment variable with the key. */
    static final String KEY = "TYPESAFE_API_KEY";
    /** Model tested with this release. */
    static final String MODEL = "jev-1.13.0";
    /** Production endpoint. */
    static final URI PRODUCTION = URI.create("https://api.typesafe.ai");

    private LiveJevGate() {
    }

    /**
     * Tells why a live tool may not run.
     *
     * @param properties system properties
     * @param environment environment variables; returns {@code null} when unset
     * @return the reason, empty when the tool may run
     */
    static Optional<String> refusal(UnaryOperator<String> properties, UnaryOperator<String> environment) {
        if (!"true".equals(properties.apply(PROPERTY))) {
            return Optional.of("Refused: this tool calls the paid TypeSafe account. Run it through Gradle with"
                    + " -PliveJev=true after the user approved the estimated cost.");
        }
        var key = environment.apply(KEY);
        if (key == null || key.isBlank()) {
            return Optional.of("Refused: " + KEY + " is not in the environment. Load it with"
                    + " 'set -a; source .env; set +a' in the same shell; the tool never reads .env.");
        }
        return Optional.empty();
    }

    /**
     * Client settings for a live tool: the key from the environment, the pinned model unless overridden, and a request
     * timeout long enough to observe the tail of the latency.
     */
    static SystemOneSettings settings(UnaryOperator<String> properties, UnaryOperator<String> environment) {
        var baseUrl = Optional.ofNullable(properties.apply("thepredict.live.baseUrl")).filter(value -> !value.isBlank())
                .map(URI::create).orElse(PRODUCTION);
        var model = Optional.ofNullable(properties.apply("thepredict.live.model")).filter(value -> !value.isBlank())
                .orElse(MODEL);
        return new SystemOneSettings(baseUrl, model, environment.apply(KEY), Duration.ofMillis(1500),
                Duration.ofSeconds(5), 262_144, 12_000, "0.0.1");
    }

    /** Reads a positive whole number property with its default. */
    static int positive(UnaryOperator<String> properties, String name, int fallback) {
        var value = properties.apply(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        var parsed = Integer.parseInt(value.trim());
        if (parsed < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }
}
