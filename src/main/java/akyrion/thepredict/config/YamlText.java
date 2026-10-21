package akyrion.thepredict.config;

import java.util.List;
import java.util.stream.Collectors;

/** Formats scalar values for the YAML files written by the migration proposal. */
final class YamlText {
    private YamlText() {
    }

    /** Quotes text with single quotes, doubling any single quote inside. */
    static String quote(String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    /** Writes a decimal in plain notation. */
    static String number(double value) {
        return Double.toString(value);
    }

    /** Writes a flow list of identifiers, for example {@code [melee, ranged]}. */
    static String identifiers(List<String> values) {
        return values.stream().collect(Collectors.joining(", ", "[", "]"));
    }
}
