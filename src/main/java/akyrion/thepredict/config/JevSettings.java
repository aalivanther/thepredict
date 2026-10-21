package akyrion.thepredict.config;

import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * The {@code jev.*} section of {@code config.yml}: how to reach TypeSafe Jev (M03.1). The root
 * translates it into the settings of {@code adapter.jev} (rule D10). {@link #toString()} never shows the key.
 *
 * @param baseUrl service base URL; {@code https}, or {@code http} only for a loopback host
 * @param model versioned Jev model ID matching {@link #MODEL}
 * @param apiKeyEnvironment environment variable name with priority over {@code apiKey}, matching
 *                          {@link #API_KEY_ENVIRONMENT}; may be empty
 * @param apiKey key stored in {@code config.yml}; may be empty
 * @param timeout request timeout, 100 to 5000 ms
 * @param connectTimeout connection timeout, 100 to 10 000 ms
 * @param maxResponseBytes largest accepted response body, 16 384 to 4 194 304 bytes
 * @param maxStateChars largest encoded state, 2000 to {@value #MAX_STATE_CHARS} characters
 * @param provider {@code jev.provider}, {@code typesafe} or {@code kev} (M29)
 */
public record JevSettings(URI baseUrl, String model, String apiKeyEnvironment, String apiKey, Duration timeout,
                          Duration connectTimeout, int maxResponseBytes, int maxStateChars, JevProvider provider) {
    /** Default base URL of the TypeSafe API. */
    public static final URI DEFAULT_BASE_URL = URI.create("https://api.typesafe.ai");
    /** Tested Jev model ID preconfigured on a fresh installation. */
    public static final String DEFAULT_MODEL = "jev-1.13.0";
    /** Environment variable of the official SDKs. */
    public static final String DEFAULT_API_KEY_ENVIRONMENT = "TYPESAFE_API_KEY";
    /** Accepted form of {@code jev.apiKeyEnvironment}: empty or an environment variable name. */
    public static final Pattern API_KEY_ENVIRONMENT = Pattern.compile("|[A-Za-z_][A-Za-z0-9_]{0,127}");
    /** Accepted form of {@code jev.model}: a versioned model ID such as {@code jev-1.13.0}. */
    public static final Pattern MODEL = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    /**
     * Largest accepted {@code jev.maxStateChars}: the client refuses to send a {@code state} plus its longest question
     * above 32 000 estimated tokens at 1.5 characters per token, that is 48 000 characters (M08, calibrated in M13), so
     * a larger state would turn a big encounter into {@code INVALID_REQUEST} and pause the service. 45 000 leaves 3 000
     * characters for the longest question. {@code config} cannot read the client's constants (rule D3); a test of the
     * client keeps both in step.
     */
    public static final int MAX_STATE_CHARS = 45_000;
    /**
     * Reason given when the key sits in {@code jev.model}: the model travels in every request body and in the
     * records (rules C8 and W6), so the file is refused at load instead of at the first request. No value is shown.
     */
    public static final String KEY_IN_MODEL = "holds the API key, or jev.model and the key are swapped; put the model"
            + " ID in jev.model and the key in the environment. The values are not shown";
    private static final Pattern DISPLAYABLE_MODEL = Pattern.compile("(jev|kev)-[0-9][0-9.]*");
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    /**
     * Validates every value.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws ConfigurationException if a value is outside its range, naming its {@code jev.*} path
     */
    public JevSettings {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(apiKeyEnvironment, "apiKeyEnvironment");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(provider, "provider");
        requireBaseUrl(baseUrl);
        if (provider == JevProvider.KEV && !LOOPBACK_HOSTS.contains(host(baseUrl))) {
            throw new ConfigurationException("jev.baseUrl", "must be localhost when jev.provider is kev");
        }
        if (!MODEL.matcher(model).matches()) {
            throw new ConfigurationException("jev.model", "must be a model ID of 1 to 64 letters, digits, '.', '_' or '-'"
                    + " starting with a letter or digit, such as " + DEFAULT_MODEL + "; the value is not shown");
        }
        if (!API_KEY_ENVIRONMENT.matcher(apiKeyEnvironment).matches()) {
            throw new ConfigurationException("jev.apiKeyEnvironment", "must be empty or an environment variable name"
                    + " such as " + DEFAULT_API_KEY_ENVIRONMENT + "; put the key itself in the environment or in"
                    + " jev.apiKey. The value is not shown");
        }
        if (keyInModel(model, apiKey)) {
            throw new ConfigurationException("jev.model", KEY_IN_MODEL);
        }
        SettingsChecks.requireRange(millis(timeout), 100, 5000, "jev.timeoutMillis");
        SettingsChecks.requireRange(millis(connectTimeout), 100, 10_000, "jev.connectTimeoutMillis");
        SettingsChecks.requireRange(maxResponseBytes, 16_384, 4_194_304, "jev.maxResponseBytes");
        SettingsChecks.requireRange(maxStateChars, 2000, MAX_STATE_CHARS, "jev.maxStateChars");
    }

    /**
     * Returns the defaults of a fresh installation, with an empty key.
     *
     * @return the default settings
     */
    public static JevSettings standard() {
        return new JevSettings(DEFAULT_BASE_URL, DEFAULT_MODEL, DEFAULT_API_KEY_ENVIRONMENT, "",
                Duration.ofMillis(800), Duration.ofMillis(1500), 262_144, 12_000);
    }

    /**
     * Settings from before {@code jev.provider} (rule W5). The provider is TypeSafe.
     *
     * @param baseUrl service base URL
     * @param model versioned model ID
     * @param apiKeyEnvironment environment variable name
     * @param apiKey key stored in {@code config.yml}
     * @param timeout request timeout
     * @param connectTimeout connection timeout
     * @param maxResponseBytes largest accepted response body
     * @param maxStateChars largest encoded state
     */
    public JevSettings(URI baseUrl, String model, String apiKeyEnvironment, String apiKey, Duration timeout,
                       Duration connectTimeout, int maxResponseBytes, int maxStateChars) {
        this(baseUrl, model, apiKeyEnvironment, apiKey, timeout, connectTimeout, maxResponseBytes, maxStateChars,
                JevProvider.TYPESAFE);
    }

    /**
     * Reads the {@code jev} section; absent keys keep {@link #standard()}.
     *
     * @param configuration root of {@code config.yml}
     * @return the validated settings
     * @throws ConfigurationException if a value has the wrong type or range
     */
    public static JevSettings from(ConfigurationSection configuration) {
        var standard = standard();
        return new JevSettings(
                ConfigurationValues.uri(configuration, "jev.baseUrl", standard.baseUrl()),
                ConfigurationValues.text(configuration, "jev.model", standard.model()),
                ConfigurationValues.text(configuration, "jev.apiKeyEnvironment", standard.apiKeyEnvironment()),
                ConfigurationValues.text(configuration, "jev.apiKey", standard.apiKey()),
                Duration.ofMillis(ConfigurationValues.intValue(configuration, "jev.timeoutMillis",
                        standard.timeoutMillis())),
                Duration.ofMillis(ConfigurationValues.intValue(configuration, "jev.connectTimeoutMillis",
                        millis(standard.connectTimeout()))),
                ConfigurationValues.intValue(configuration, "jev.maxResponseBytes", standard.maxResponseBytes()),
                ConfigurationValues.intValue(configuration, "jev.maxStateChars", standard.maxStateChars()),
                JevProvider.parse(ConfigurationValues.text(configuration, "jev.provider", "typesafe")));
    }

    /**
     * Returns the request timeout in milliseconds.
     *
     * @return {@code timeout} in milliseconds
     */
    public int timeoutMillis() {
        return millis(timeout);
    }

    /**
     * Resolves the key from the process environment: the variable wins over {@code apiKey}.
     *
     * @return the key, possibly empty while setup is incomplete
     */
    public String resolvedApiKey() {
        return resolvedApiKey(System::getenv);
    }

    /**
     * Resolves the key from the given environment: the variable wins over {@code apiKey}.
     *
     * @param environment returns the value of a variable, or {@code null} when it is not set
     * @return the key, possibly empty while setup is incomplete
     */
    public String resolvedApiKey(UnaryOperator<String> environment) {
        Objects.requireNonNull(environment, "environment");
        if (provider == JevProvider.KEV) {
            var local = environment.apply("KEV_API_KEY");
            return local == null ? "" : local;
        }
        if (!apiKeyEnvironment.isBlank()) {
            var fromEnvironment = environment.apply(apiKeyEnvironment);
            if (fromEnvironment != null && !fromEnvironment.isBlank()) {
                return fromEnvironment;
            }
        }
        return apiKey;
    }

    /**
     * Checks the key the process resolves against the model, with the rule of the client settings: a model equal to
     * the key, or a key that looks like a versioned model ID next to a model that does not, is a key pasted into
     * {@code jev.model}. The file key is already checked by the constructor; this covers the environment variable.
     *
     * @param environment returns the value of a variable, or {@code null} when it is not set
     * @return the error at {@code jev.model}, or empty when the two fields are consistent
     */
    public java.util.Optional<ConfigurationException> keyProblem(UnaryOperator<String> environment) {
        if (keyInModel(model, resolvedApiKey(environment))) {
            return java.util.Optional.of(new ConfigurationException("jev.model", KEY_IN_MODEL));
        }
        return java.util.Optional.empty();
    }

    private static boolean keyInModel(String model, String key) {
        return !key.isEmpty() && (model.equals(key)
                || DISPLAYABLE_MODEL.matcher(key).matches() && !DISPLAYABLE_MODEL.matcher(model).matches());
    }

    /**
     * A non-blank {@code jev.apiKey} is ignored when the provider is Kev.
     *
     * @return the warning, or empty
     */
    public java.util.Optional<String> ignoredKeyWarning() {
        if (provider == JevProvider.KEV && !apiKey.isBlank()) {
            return java.util.Optional.of("jev.apiKey is ignored when jev.provider is kev");
        }
        return java.util.Optional.empty();
    }

    /**
     * Tells whether a Jev client can be created with the process environment.
     *
     * @return {@code true} when a key resolves
     */
    public boolean isConfigured() {
        return isConfigured(System::getenv);
    }

    /**
     * Tells whether a Jev client can be created with the given environment.
     *
     * @param environment returns the value of a variable, or {@code null} when it is not set
     * @return {@code true} when a key resolves
     */
    public boolean isConfigured(UnaryOperator<String> environment) {
        return provider == JevProvider.KEV || !resolvedApiKey(environment).isBlank();
    }

    /**
     * Describes the model for messages. Only the default or a versioned {@code jev-}/{@code kev-} ID is shown,
     * because a key pasted into {@code jev.model} can fit the model pattern. The real value is still the one
     * sent to the service.
     *
     * @return the model ID when it is displayable, otherwise {@code <custom model>}
     */
    public String modelLabel() {
        return model.equals(DEFAULT_MODEL) || DISPLAYABLE_MODEL.matcher(model).matches() ? model : "<custom model>";
    }

    /**
     * Describes the environment variable for messages. A custom name is not echoed, because even a valid
     * name could be a key pasted into the wrong field.
     *
     * @return the default variable name, {@code <none>} when empty, or {@code <custom variable>}
     */
    public String apiKeyEnvironmentLabel() {
        if (apiKeyEnvironment.isEmpty()) {
            return "<none>";
        }
        return apiKeyEnvironment.equals(DEFAULT_API_KEY_ENVIRONMENT) ? apiKeyEnvironment : "<custom variable>";
    }

    /**
     * Describes the settings without the key, the path of the base URL, a custom model or a custom variable name.
     *
     * @return the settings with secrets redacted
     */
    @Override
    public String toString() {
        return "JevSettings[baseUrl=" + baseUrl.getScheme() + "://" + baseUrl.getRawAuthority() + ", model=" + modelLabel()
                + ", apiKeyEnvironment=" + apiKeyEnvironmentLabel()
                + ", apiKey=" + (apiKey.isEmpty() ? "<empty>" : "<redacted>") + ", timeoutMillis=" + timeoutMillis()
                + ", connectTimeoutMillis=" + millis(connectTimeout) + ", maxResponseBytes=" + maxResponseBytes
                + ", maxStateChars=" + maxStateChars + "]";
    }

    private static String host(URI baseUrl) {
        return baseUrl.getHost() == null ? "" : baseUrl.getHost().toLowerCase(Locale.ROOT);
    }

    private static void requireBaseUrl(URI baseUrl) {
        var scheme = baseUrl.getScheme() == null ? "" : baseUrl.getScheme().toLowerCase(Locale.ROOT);
        var host = host(baseUrl);
        if (host.isEmpty() || baseUrl.getRawUserInfo() != null || baseUrl.getRawQuery() != null
                || baseUrl.getRawFragment() != null) {
            throw new ConfigurationException("jev.baseUrl", "must be a URL with a host and without user, query or fragment");
        }
        if (!scheme.equals("https") && !(scheme.equals("http") && LOOPBACK_HOSTS.contains(host))) {
            throw new ConfigurationException("jev.baseUrl", "must use https; http is accepted only for localhost");
        }
    }

    private static int millis(Duration duration) {
        return Math.clamp(duration.toMillis(), Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}
