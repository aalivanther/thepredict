package akyrion.thepredict;

import akyrion.thepredict.adapter.jev.ScenarioRecorder;
import akyrion.thepredict.adapter.jev.SystemOneClient;
import akyrion.thepredict.adapter.jev.SystemOneSettings;
import akyrion.thepredict.config.JevSettings;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.engine.DecisionClient;
import akyrion.thepredict.engine.ServerClock;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Translates {@link JevSettings} into {@link SystemOneSettings} and creates the process-wide
 * {@link DecisionClient} (rule D10, M08.9). Creating a client opens no connection and makes no request; the
 * coordinator of M09 calls {@code check()} on its first tick.
 */
final class SystemOneWiring {
    private SystemOneWiring() {
    }

    /**
     * Translates the {@code jev.*} settings. The environment variable wins over {@code jev.apiKey}.
     *
     * @param jev validated {@code jev.*} settings
     * @param environment returns the value of a variable, or {@code null} when it is not set
     * @param pluginVersion plugin version for the {@code User-Agent} header
     * @return the client settings
     * @throws IllegalArgumentException if no key resolves or a value is unsafe in a header
     */
    static SystemOneSettings settings(JevSettings jev, UnaryOperator<String> environment, String pluginVersion) {
        Objects.requireNonNull(jev, "jev");
        if (jev.provider() == akyrion.thepredict.config.JevProvider.KEV) {
            var key = jev.resolvedApiKey(environment);
            return new SystemOneSettings(jev.baseUrl(), jev.model(), key, jev.connectTimeout(), jev.timeout(),
                    jev.maxResponseBytes(), jev.maxStateChars(), pluginVersion, !key.isBlank());
        }
        if (!jev.isConfigured(environment)) {
            throw new IllegalArgumentException("no TypeSafe API key is configured");
        }
        return new SystemOneSettings(jev.baseUrl(), jev.model(), jev.resolvedApiKey(environment), jev.connectTimeout(),
                jev.timeout(), jev.maxResponseBytes(), jev.maxStateChars(), pluginVersion);
    }

    /**
     * Translates {@code debug.recordScenarios} into the scenario recorder of M13.6 (rule D10).
     *
     * @param recordScenarios {@code debug.recordScenarios}
     * @param dataFolder data folder of the plugin; the scenarios go to its {@code scenarios} folder
     * @param log receiver of the recorder's single notices
     * @return a recorder into {@code <dataFolder>/scenarios}, or the disabled recorder
     */
    static ScenarioRecorder recorder(boolean recordScenarios, Path dataFolder, Consumer<String> log) {
        return recordScenarios ? ScenarioRecorder.into(dataFolder.resolve("scenarios"), log)
                : ScenarioRecorder.disabled();
    }

    /**
     * Returns the state the service starts in for these settings.
     *
     * @param jev validated {@code jev.*} settings
     * @param environment returns the value of a variable, or {@code null} when it is not set
     * @return {@code CHECKING} when a key resolves, {@code UNCONFIGURED} otherwise
     */
    static ServiceCheck initialState(JevSettings jev, UnaryOperator<String> environment) {
        if (jev.provider() == akyrion.thepredict.config.JevProvider.KEV || jev.isConfigured(environment)) {
            return new ServiceCheck(ServiceState.CHECKING, "start");
        }
        return new ServiceCheck(ServiceState.UNCONFIGURED, "no TypeSafe API key");
    }

    /**
     * The warning of a start whose JDK HTTP client would log headers, and so the {@code Authorization} header of every
     * request (JS-27, rules C8 and W6). It reads a system property, never the environment, and never shows the key.
     *
     * @param properties returns the value of a system property, or {@code null} when it is not set
     * @return the warning, empty when headers are not logged
     */
    static Optional<String> headerWarning(UnaryOperator<String> properties) {
        var property = properties.apply("jdk.httpclient.HttpClient.log");
        return SystemOneClient.headerLogWarning(property == null ? "" : property);
    }

    /**
     * Creates the client of a plugin start: the null client reporting {@code initial} unless it is
     * {@code CHECKING}, and the null client in {@code INVALID_CONFIGURATION} when a value is unusable in a header.
     * The detail is the reason of the client settings, which never carry a value; the resolved key is still masked
     * if a reason ever held it (W6).
     *
     * @param initial state from {@link #initialState}
     * @param jev validated {@code jev.*} settings
     * @param environment returns the value of a variable, or {@code null} when it is not set
     * @param pluginVersion plugin version for the {@code User-Agent} header
     * @param clock monotonic clock
     * @param diagnostics timing sink, a no-op unless {@code decision.logDecisions} is on
     * @param recorder scenario bank of M13.6, disabled unless {@code debug.recordScenarios} is on
     * @return the one client of the process
     */
    static DecisionClient createForStart(ServiceCheck initial, JevSettings jev, UnaryOperator<String> environment,
                                         String pluginVersion, ServerClock clock, Consumer<String> diagnostics,
                                         ScenarioRecorder recorder) {
        if (initial.state() != ServiceState.CHECKING) {
            return DecisionClient.unavailable(initial);
        }
        try {
            return create(jev, environment, pluginVersion, clock, diagnostics, recorder);
        } catch (IllegalArgumentException exception) {
            return DecisionClient.unavailable(new ServiceCheck(ServiceState.INVALID_CONFIGURATION,
                    reason(exception, jev.resolvedApiKey(environment))));
        }
    }

    private static String reason(IllegalArgumentException exception, String key) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "a jev setting cannot be sent in a header";
        }
        return key.isBlank() ? message : message.replace(key, "<hidden>");
    }

    /**
     * Creates the System One client, or the null client when no key resolves.
     *
     * @param jev validated {@code jev.*} settings
     * @param environment returns the value of a variable, or {@code null} when it is not set
     * @param pluginVersion plugin version for the {@code User-Agent} header
     * @param clock monotonic clock
     * @param diagnostics timing sink, a no-op unless {@code decision.logDecisions} is on
     * @param recorder scenario bank of M13.6, disabled unless {@code debug.recordScenarios} is on
     * @return the client; {@code check()} of the null client reports {@code UNCONFIGURED}
     */
    static DecisionClient create(JevSettings jev, UnaryOperator<String> environment, String pluginVersion,
                                 ServerClock clock, Consumer<String> diagnostics, ScenarioRecorder recorder) {
        if (jev.provider() != akyrion.thepredict.config.JevProvider.KEV && !jev.isConfigured(environment)) {
            return DecisionClient.unavailable(new ServiceCheck(ServiceState.UNCONFIGURED,
                    "no TypeSafe API key; set " + jev.apiKeyEnvironmentLabel() + " or jev.apiKey"));
        }
        return new SystemOneClient(settings(jev, environment, pluginVersion), clock, diagnostics, recorder);
    }
}
