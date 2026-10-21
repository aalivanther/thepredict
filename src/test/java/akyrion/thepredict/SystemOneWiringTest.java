package akyrion.thepredict;

import akyrion.thepredict.adapter.jev.ScenarioRecorder;
import akyrion.thepredict.adapter.jev.SystemOneClient;
import akyrion.thepredict.config.JevSettings;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.engine.ServerClock;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemOneWiringTest {
    private static final ServerClock CLOCK = ServerClock.of(() -> 0, () -> 0);
    private static final UnaryOperator<String> NO_ENVIRONMENT = name -> null;

    @Test
    void theEnvironmentVariableWinsOverTheConfiguredKey() {
        var settings = SystemOneWiring.settings(jev("config-key"), environment("env-key"), "0.0.1");
        assertEquals("env-key", settings.apiKey());
    }

    @Test
    void theConfiguredKeyIsUsedWithoutTheVariable() {
        assertEquals("config-key", SystemOneWiring.settings(jev("config-key"), NO_ENVIRONMENT, "0.0.1").apiKey());
    }

    @Test
    void everyJevValueIsTranslated() {
        var settings = SystemOneWiring.settings(jev("config-key"), NO_ENVIRONMENT, "0.0.1");
        assertEquals(URI.create("http://127.0.0.1:9"), settings.baseUrl());
        assertEquals("jev-1.13.0", settings.model());
        assertEquals(Duration.ofMillis(700), settings.requestTimeout());
        assertEquals(Duration.ofMillis(1200), settings.connectTimeout());
        assertEquals(32_768, settings.maxResponseBytes());
        assertEquals(4000, settings.maxStateChars());
        assertEquals("0.0.1", settings.pluginVersion());
        assertFalse(settings.toString().contains("config-key"));
    }

    @Test
    void withoutAKeyTheClientIsTheUnconfiguredNullClient() {
        var client = SystemOneWiring.create(jev(""), NO_ENVIRONMENT, "0.0.1", CLOCK, line -> {
        }, ScenarioRecorder.disabled());
        var check = client.check().join();
        assertEquals(ServiceState.UNCONFIGURED, check.state());
        assertTrue(check.detail().contains("TYPESAFE_API_KEY"), check.detail());
        assertThrows(IllegalArgumentException.class, () -> SystemOneWiring.settings(jev(""), NO_ENVIRONMENT, "0.0.1"));
    }

    @Test
    void withAKeyTheSystemOneClientIsCreatedWithoutAnyRequest() {
        var client = SystemOneWiring.create(jev("config-key"), NO_ENVIRONMENT, "0.0.1", CLOCK, line -> {
        }, ScenarioRecorder.disabled());
        try {
            assertInstanceOf(SystemOneClient.class, client);
        } finally {
            client.close();
        }
    }

    @Test
    void recordScenariosTranslatesIntoARecorderOfTheScenariosFolderAndOffIntoTheDisabledOne() {
        var dataFolder = Path.of("plugins", "ThePredict");
        var on = SystemOneWiring.recorder(true, dataFolder, line -> {
        });
        var off = SystemOneWiring.recorder(false, dataFolder, line -> {
        });
        try {
            assertTrue(on.enabled());
            assertEquals(Optional.of(dataFolder.resolve("scenarios")), on.directory());
            assertFalse(off.enabled());
            assertEquals(Optional.empty(), off.directory());
        } finally {
            on.close();
            off.close();
        }
    }

    @Test
    void theServiceStartsCheckingOnlyWhenAKeyResolves() {
        assertEquals(ServiceState.CHECKING, SystemOneWiring.initialState(jev("config-key"), NO_ENVIRONMENT).state());
        assertEquals(ServiceState.CHECKING, SystemOneWiring.initialState(jev(""), environment("env-key")).state());
        assertEquals(ServiceState.UNCONFIGURED, SystemOneWiring.initialState(jev(""), NO_ENVIRONMENT).state());
    }

    @Test
    void theStartClientIsTheNullClientUnlessTheServiceStartsChecking() {
        var unconfigured = new ServiceCheck(ServiceState.UNCONFIGURED, "no TypeSafe API key");
        var quiet = SystemOneWiring.createForStart(unconfigured, jev("config-key"), NO_ENVIRONMENT, "0.0.1", CLOCK,
                line -> {
                }, ScenarioRecorder.disabled());
        assertEquals(unconfigured, quiet.check().join());
        var client = SystemOneWiring.createForStart(SystemOneWiring.initialState(jev("config-key"), NO_ENVIRONMENT),
                jev("config-key"), NO_ENVIRONMENT, "0.0.1", CLOCK, line -> {
                }, ScenarioRecorder.disabled());
        try {
            assertInstanceOf(SystemOneClient.class, client);
        } finally {
            client.close();
        }
    }

    @Test
    void aKeyUnusableInAHeaderLeavesTheNullClientWithoutEchoingTheKey() {
        var broken = environment("bad key-4f1c");
        var client = SystemOneWiring.createForStart(SystemOneWiring.initialState(jev(""), broken), jev(""), broken,
                "0.0.1", CLOCK, line -> {
                }, ScenarioRecorder.disabled());
        var check = client.check().join();
        assertEquals(ServiceState.INVALID_CONFIGURATION, check.state());
        assertFalse(check.detail().contains("4f1c"), check.detail());
        assertEquals("apiKey must be printable text without spaces; the value is not shown", check.detail(),
                "the reason of the rejection is shown, never the value (JS-27)");
    }

    @Test
    void theStartWarnsWhenTheJdkWouldLogTheAuthorizationHeader() {
        assertTrue(SystemOneWiring.headerWarning(name -> "jdk.httpclient.HttpClient.log".equals(name)
                ? "errors,headers" : null).isPresent());
        assertEquals(Optional.empty(), SystemOneWiring.headerWarning(name -> null));
        assertEquals(Optional.empty(), SystemOneWiring.headerWarning(name -> "errors"));
    }

    private static UnaryOperator<String> environment(String key) {
        return Map.of(JevSettings.DEFAULT_API_KEY_ENVIRONMENT, key)::get;
    }

    private static JevSettings jev(String apiKey) {
        return new JevSettings(URI.create("http://127.0.0.1:9"), "jev-1.13.0", JevSettings.DEFAULT_API_KEY_ENVIRONMENT,
                apiKey, Duration.ofMillis(700), Duration.ofMillis(1200), 32_768, 4000);
    }
}
