package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Name of a skill choice, local to its profile and never sent to the model (rule I5).
 * Pattern {@code [a-z][a-z0-9_]{0,39}}. Immutable.
 *
 * @param value the snake_case choice name
 */
public record ChoiceId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]{0,39}");

    /**
     * Validates the format.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} does not match the pattern
     */
    public ChoiceId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("choice must match [a-z][a-z0-9_]{0,39}, was '" + value + "'");
        }
    }

    /**
     * Returns the raw value so error messages stay readable.
     *
     * @return the choice name
     */
    @Override
    public String toString() {
        return value;
    }
}
