package akyrion.thepredict.config;

import java.util.function.UnaryOperator;

/** Range checks shared by the settings records; each failure names the YAML path of the value. */
final class SettingsChecks {
    private SettingsChecks() {
    }

    /** Requires an integer within an inclusive range. */
    static int requireRange(int value, int minimum, int maximum, String path) {
        if (value < minimum || value > maximum) {
            throw new ConfigurationException(path, "must be between " + minimum + " and " + maximum
                    + ", was " + value);
        }
        return value;
    }

    /** Requires a finite decimal within an inclusive range. */
    static double requireRange(double value, double minimum, double maximum, String path) {
        if (!(Double.isFinite(value) && value >= minimum && value <= maximum)) {
            throw new ConfigurationException(path, "must be between " + minimum + " and " + maximum
                    + ", was " + value);
        }
        return value;
    }

    /** Requires a finite decimal strictly greater than a lower bound. */
    static double requireGreater(double value, double lowerBound, String path, String boundName) {
        if (!(Double.isFinite(value) && value > lowerBound)) {
            throw new ConfigurationException(path, "must be finite and greater than " + boundName
                    + " (" + lowerBound + "), was " + value);
        }
        return value;
    }

    /** Requires a finite decimal greater than or equal to a lower bound. */
    static double requireAtLeast(double value, double lowerBound, String path, String boundName) {
        if (!(Double.isFinite(value) && value >= lowerBound)) {
            throw new ConfigurationException(path, "must be finite and at least " + boundName
                    + " (" + lowerBound + "), was " + value);
        }
        return value;
    }

    /**
     * Converts a validation failure of a {@code domain} record into a configuration error at the YAML leaf. The
     * records name the failing field first ({@code toleranceRatio must be within [0.05, 0.50], was 0.9}), so the
     * leaf is {@code section.field} unless the field already starts with the section. A message without a reason
     * means the range failed. A {@link ConfigurationException} is returned unchanged, because it is already precise.
     *
     * @param section YAML path of the section the record was read from, empty for the root
     * @param failure the failure
     * @return the error to throw
     */
    static ConfigurationException leaf(String section, IllegalArgumentException failure) {
        return leaf(section, failure, UnaryOperator.identity());
    }

    /**
     * Converts a validation failure of a {@code domain} record into a configuration error at the YAML leaf, renaming
     * the record field into the key the administrator or author wrote.
     *
     * @param section YAML path of the section the record was read from, empty for the root
     * @param failure the failure
     * @param rename maps a record field to its YAML key
     * @return the error to throw
     */
    static ConfigurationException leaf(String section, IllegalArgumentException failure, UnaryOperator<String> rename) {
        if (failure instanceof ConfigurationException precise) {
            return precise;
        }
        var message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return new ConfigurationException(section, "has an invalid value");
        }
        var cut = message.indexOf(' ');
        var field = cut < 0 ? message : message.substring(0, cut);
        var reason = cut < 0 ? "is outside its range" : message.substring(cut + 1);
        var key = section.isEmpty() || field.startsWith(section + ".") ? field : section + "." + rename.apply(field);
        return new ConfigurationException(key, reason);
    }
}
