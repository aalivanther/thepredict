package akyrion.thepredict.adapter.jev;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Settings of the System One client, translated by the composition root from {@code config.JevSettings}
 * (rule D10). {@link #toString()} never shows the key (rules C8 and W6).
 *
 * @param baseUrl service base URL, such as {@code https://api.typesafe.ai}; {@code https}, or {@code http} only
 *                for a loopback host; never with user information
 * @param model model ID sent in every request, such as {@code jev-1.13.0}
 * @param apiKey resolved API key; sent only in the {@code Authorization} header
 * @param connectTimeout connection timeout; positive
 * @param requestTimeout per-request timeout until the response headers arrive; positive
 * @param maxResponseBytes largest accepted response body in bytes; positive
 * @param maxStateChars largest encoded {@code state} in characters; positive
 * @param pluginVersion plugin version for the {@code User-Agent} header, such as {@code 0.0.1}
 * @param sendAuthorization whether the {@code Authorization} header is sent; false for Kev without a key (M29)
 */
public record SystemOneSettings(URI baseUrl, String model, String apiKey, Duration connectTimeout,
                                Duration requestTimeout, int maxResponseBytes, int maxStateChars,
                                String pluginVersion, boolean sendAuthorization) {
    private static final Pattern DISPLAYABLE_MODEL = Pattern.compile("(jev|kev)-[0-9][0-9.]*");
    private static final Pattern HEADER_SAFE = Pattern.compile("[\\x21-\\x7e]+");
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    /**
     * Validates every value. Error messages never contain the key or the model. A model equal to the key, or a key
     * that looks like a model ID next to a model that does not, is a key pasted into the model field: it is refused,
     * because the model travels in every request body and in the records (rules C8 and W6).
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if a value is blank, not positive or unsafe in a header, if the key is in the
     *         model field, or if the base URL has user information, a query or a fragment, or uses {@code http} for a
     *         host that is not loopback
     */
    public SystemOneSettings {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        Objects.requireNonNull(pluginVersion, "pluginVersion");
        requireBaseUrl(baseUrl);
        if (!HEADER_SAFE.matcher(model).matches() || !HEADER_SAFE.matcher(pluginVersion).matches()) {
            throw new IllegalArgumentException("model and pluginVersion must be printable text without spaces;"
                    + " the values are not shown");
        }
        if (!apiKey.isEmpty() && (model.equals(apiKey)
                || DISPLAYABLE_MODEL.matcher(apiKey).matches() && !DISPLAYABLE_MODEL.matcher(model).matches())) {
            throw new IllegalArgumentException("the model field holds the API key, or the two fields are swapped;"
                    + " put the model ID in jev.model and the key in the environment. The values are not shown");
        }
        if ((sendAuthorization || !apiKey.isEmpty()) && !HEADER_SAFE.matcher(apiKey).matches()) {
            throw new IllegalArgumentException("apiKey must be printable text without spaces; the value is not shown");
        }
        if (!connectTimeout.isPositive() || !requestTimeout.isPositive()) {
            throw new IllegalArgumentException("timeouts must be positive");
        }
        if (maxResponseBytes < 1 || maxStateChars < 1) {
            throw new IllegalArgumentException("maxResponseBytes and maxStateChars must be positive");
        }
    }

    /**
     * Settings that send {@code Authorization} (rule W5).
     *
     * @param baseUrl service base URL
     * @param model model ID
     * @param apiKey resolved API key
     * @param connectTimeout connection timeout
     * @param requestTimeout per-request timeout
     * @param maxResponseBytes largest accepted response body
     * @param maxStateChars largest encoded state
     * @param pluginVersion plugin version
     */
    public SystemOneSettings(URI baseUrl, String model, String apiKey, Duration connectTimeout,
                             Duration requestTimeout, int maxResponseBytes, int maxStateChars, String pluginVersion) {
        this(baseUrl, model, apiKey, connectTimeout, requestTimeout, maxResponseBytes, maxStateChars, pluginVersion,
                true);
    }

    private static void requireBaseUrl(URI baseUrl) {
        var scheme = baseUrl.getScheme() == null ? "" : baseUrl.getScheme().toLowerCase(Locale.ROOT);
        var host = baseUrl.getHost() == null ? "" : baseUrl.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || baseUrl.getRawUserInfo() != null || baseUrl.getRawQuery() != null
                || baseUrl.getRawFragment() != null) {
            throw new IllegalArgumentException("baseUrl must be an absolute URL with a host and without user,"
                    + " query or fragment");
        }
        if (!scheme.equals("https") && !(scheme.equals("http") && LOOPBACK_HOSTS.contains(host))) {
            throw new IllegalArgumentException("baseUrl must use https; http is accepted only for a loopback host");
        }
    }

    /**
     * Returns the URL of an API path below {@link #baseUrl()}.
     *
     * @param path absolute API path such as {@code /v1/systemone}
     * @return the resolved URL
     */
    public URI endpoint(String path) {
        var base = baseUrl.toString();
        return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path);
    }

    /**
     * Describes the settings without the key, the path or user information of the base URL, or a model that is not a versioned
     * {@code jev-}/{@code kev-} ID, because a key pasted into the model field could fit the model pattern.
     *
     * @return the settings with secrets redacted
     */
    @Override
    public String toString() {
        return "SystemOneSettings[baseUrl=" + baseUrl.getScheme() + "://" + baseUrl.getHost()
                + (baseUrl.getPort() < 0 ? "" : ":" + baseUrl.getPort())
                + ", model=" + (DISPLAYABLE_MODEL.matcher(model).matches() ? model : "<custom model>")
                + ", apiKey=<redacted>, connectTimeoutMillis=" + connectTimeout.toMillis()
                + ", requestTimeoutMillis=" + requestTimeout.toMillis() + ", maxResponseBytes=" + maxResponseBytes
                + ", maxStateChars=" + maxStateChars + ", pluginVersion=" + pluginVersion + "]";
    }
}
