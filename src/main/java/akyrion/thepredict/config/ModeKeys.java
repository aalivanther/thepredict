package akyrion.thepredict.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.function.Function;

/**
 * Reads a mode key of {@code config.yml} or of a profile: a bare {@code off} counts as the word {@code off} (C13), an
 * absent key is the delivered default and an unknown word names the key with every accepted mode.
 */
final class ModeKeys {
    private ModeKeys() {
    }

    /**
     * Reads the key.
     *
     * @param section the section
     * @param key the key inside the section
     * @param fallback the delivered default
     * @param parse the parser of the enumeration, which throws {@link IllegalArgumentException} on an unknown word
     * @param <E> the enumeration
     * @return the mode
     */
    static <E extends Enum<E>> E parse(ConfigurationSection section, String key, E fallback, Function<String, E> parse) {
        if (!section.contains(key)) {
            return fallback;
        }
        return required(section, key, fallback.getDeclaringClass(), parse);
    }

    /**
     * Reads a key that is present.
     *
     * @param section the section
     * @param key the key inside the section
     * @param type the enumeration, for the message
     * @param parse the parser of the enumeration, which throws {@link IllegalArgumentException} on an unknown word
     * @param <E> the enumeration
     * @return the mode
     */
    static <E extends Enum<E>> E required(ConfigurationSection section, String key, Class<E> type,
                                           Function<String, E> parse) {
        var text = ConfigurationValues.token(section, key, "").trim().toLowerCase(Locale.ROOT);
        try {
            return parse.apply(text);
        } catch (ConfigurationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(ConfigurationValues.path(section, key), "must be " + ModeNames.of(type));
        }
    }
}
