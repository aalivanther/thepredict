package akyrion.thepredict.config;

import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads YAML values strictly without coercing incorrect types into defaults. Every error is a
 * {@link ConfigurationException} with the full path of the value inside its file.
 */
final class ConfigurationValues {
    private ConfigurationValues() {
    }

    /** Returns the full path of a key below a section. */
    static String path(ConfigurationSection section, String key) {
        var parent = section.getCurrentPath();
        return parent == null || parent.isEmpty() ? key : parent + "." + key;
    }

    /**
     * Describes a value for an error message without echoing text, which could be a misplaced credential:
     * numbers and booleans are shown, anything else only by its kind.
     */
    static String safeValue(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return value == null ? "nothing" : "a text value";
    }

    /** Reads text or uses a default when a field is absent. */
    static String text(ConfigurationSection configuration, String path, String fallback) {
        if (!configuration.contains(path)) {
            return fallback;
        }
        if (configuration.get(path) instanceof String value) {
            return value;
        }
        throw new ConfigurationException(path(configuration, path), "must be a string");
    }

    /**
     * Reads a mode word or uses a default when absent. YAML 1.1 reads a bare {@code off} as {@code false} and a bare
     * {@code on} as {@code true}, so both booleans are accepted and given back as the words the guide documents.
     */
    static String token(ConfigurationSection configuration, String path, String fallback) {
        if (!configuration.contains(path)) {
            return fallback;
        }
        var raw = configuration.get(path);
        if (raw instanceof String value) {
            return value;
        }
        if (raw instanceof Boolean value) {
            return value ? "on" : "off";
        }
        throw new ConfigurationException(path(configuration, path), "must be a string");
    }

    /** Reads optional nonblank text. */
    static Optional<String> optionalText(ConfigurationSection configuration, String path) {
        if (!configuration.contains(path)) {
            return Optional.empty();
        }
        return Optional.of(requiredText(configuration, path));
    }

    /** Reads required nonblank text. */
    static String requiredText(ConfigurationSection configuration, String path) {
        if (!configuration.contains(path)) {
            throw new ConfigurationException(path(configuration, path), "is required");
        }
        if (configuration.get(path) instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new ConfigurationException(path(configuration, path), "must be a non-empty string");
    }

    /** Reads an integral YAML number or uses a default when absent. */
    static long integer(ConfigurationSection configuration, String path, long fallback) {
        if (!configuration.contains(path)) {
            return fallback;
        }
        var value = configuration.get(path);
        if (value instanceof Integer number) {
            return number.longValue();
        }
        if (value instanceof Long number) {
            return number;
        }
        throw new ConfigurationException(path(configuration, path), "must be an integer");
    }

    /** Reads an integral YAML number that fits a Java int. */
    static int intValue(ConfigurationSection configuration, String path, int fallback) {
        try {
            return Math.toIntExact(integer(configuration, path, fallback));
        } catch (ArithmeticException exception) {
            throw new ConfigurationException(path(configuration, path), "is outside the integer range");
        }
    }

    /** Reads a finite YAML number or uses a default when absent. */
    static double decimal(ConfigurationSection configuration, String path, double fallback) {
        if (!configuration.contains(path)) {
            return fallback;
        }
        var value = configuration.get(path);
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        throw new ConfigurationException(path(configuration, path), "must be a finite number");
    }

    /** Reads a YAML boolean or uses a default when absent. */
    static boolean booleanValue(ConfigurationSection configuration, String path, boolean fallback) {
        if (!configuration.contains(path)) {
            return fallback;
        }
        if (configuration.get(path) instanceof Boolean value) {
            return value;
        }
        throw new ConfigurationException(path(configuration, path), "must be true or false");
    }

    /** Reads a list of nonblank strings; an absent field is an empty list. */
    static List<String> textList(ConfigurationSection configuration, String path) {
        if (!configuration.contains(path)) {
            return List.of();
        }
        if (!(configuration.get(path) instanceof List<?> entries)) {
            throw new ConfigurationException(path(configuration, path), "must be a list of strings");
        }
        var values = new ArrayList<String>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            if (!(entries.get(index) instanceof String text) || text.isBlank()) {
                throw new ConfigurationException(path(configuration, path) + "[" + index + "]",
                        "must be a non-empty string");
            }
            values.add(text);
        }
        return List.copyOf(values);
    }

    /** Reads an optional nested section; a present value that is not a section is an error. */
    static Optional<ConfigurationSection> section(ConfigurationSection configuration, String path) {
        if (!configuration.contains(path)) {
            return Optional.empty();
        }
        var section = configuration.getConfigurationSection(path);
        if (section == null) {
            throw new ConfigurationException(path(configuration, path), "must be a section");
        }
        return Optional.of(section);
    }

    /** Reads a required nested section. */
    static ConfigurationSection requiredSection(ConfigurationSection configuration, String path) {
        return section(configuration, path).orElseThrow(() ->
                new ConfigurationException(path(configuration, path), "is required"));
    }

    /** Reads an absolute URI or uses a default when absent. */
    static URI uri(ConfigurationSection configuration, String path, URI fallback) {
        if (!configuration.contains(path)) {
            return fallback;
        }
        var text = text(configuration, path, "");
        try {
            var uri = new URI(text.trim());
            if (!uri.isAbsolute()) {
                throw new ConfigurationException(path(configuration, path), "must be an absolute URI");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new ConfigurationException(path(configuration, path), "must be a valid URI");
        }
    }
}
