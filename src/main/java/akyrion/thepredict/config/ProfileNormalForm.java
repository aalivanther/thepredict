package akyrion.thepredict.config;

import akyrion.thepredict.domain.distill.ProfileFingerprint;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The normalized profile of M22.5 and the {@code baseProfileSha256} of an overlay (M29.8): one {@code path=value} line
 * per leaf of the profile section, hashed with {@link ProfileFingerprint#of}, which sorts the lines. The fingerprint
 * ignores the order of the YAML keys, comments and spacing, and changes with any value. A value keeps its line: a
 * backslash and a line break are escaped.
 */
public final class ProfileNormalForm {
    private ProfileNormalForm() {
    }

    /**
     * Fingerprint of one profile section, as written by the author.
     *
     * @param profile the section of one profile ID
     * @return lower-case hex SHA-256
     */
    public static String fingerprint(ConfigurationSection profile) {
        return ProfileFingerprint.of(lines(profile));
    }

    /**
     * The leaf lines of a section, in the order of the section.
     *
     * @param section the profile section
     * @return one {@code path=value} line per leaf
     */
    static List<String> lines(ConfigurationSection section) {
        var lines = new ArrayList<String>();
        for (var path : section.getKeys(true)) {
            var value = section.get(path);
            if (!(value instanceof ConfigurationSection)) {
                lines.add(escape(path) + "=" + escape(text(value)));
            }
        }
        return lines;
    }

    private static String text(Object value) {
        return switch (value) {
            case null -> "null";
            case ConfigurationSection section -> text(section.getValues(false));
            case Map<?, ?> map -> {
                var sorted = new TreeMap<String, String>();
                map.forEach((key, item) -> sorted.put(String.valueOf(key), text(item)));
                yield sorted.toString();
            }
            case List<?> list -> {
                var items = new ArrayList<String>(list.size());
                for (var item : list) {
                    items.add(text(item));
                }
                yield items.toString();
            }
            default -> String.valueOf(value);
        };
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }
}
