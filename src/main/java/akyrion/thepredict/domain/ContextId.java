package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Name of a context rule, local to its profile and never sent to the model (rule I5).
 * Pattern {@code [a-z][a-z0-9_]{0,39}}. Immutable.
 *
 * @param value the snake_case context name
 */
public record ContextId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]{0,39}");

    /**
     * Validates the format.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} does not match the pattern
     */
    public ContextId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("context must match [a-z][a-z0-9_]{0,39}, was '" + value + "'");
        }
    }

    /**
     * Returns the raw value so error messages stay readable.
     *
     * @return the context name
     */
    @Override
    public String toString() {
        return value;
    }
}
