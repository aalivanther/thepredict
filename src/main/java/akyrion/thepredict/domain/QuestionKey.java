package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opaque key of one question inside a request, such as {@code q0} (rule I3). The model never
 * sees it as meaningful text. Pattern {@code q[0-9]{1,4}}. Immutable.
 *
 * @param value the key text
 */
public record QuestionKey(String value) {
    /** Largest index accepted by {@link #of(int)}. */
    public static final int MAX_INDEX = 9999;

    private static final Pattern FORMAT = Pattern.compile("q[0-9]{1,4}");

    /**
     * Validates the format.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} does not match the pattern
     */
    public QuestionKey {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("question key must match q[0-9]{1,4}, was '" + value + "'");
        }
    }

    /**
     * Builds the key of the question at a position.
     *
     * @param index position within the request, from {@code 0} to {@value #MAX_INDEX}
     * @return the key {@code q<index>}
     * @throws IllegalArgumentException if the index is out of range
     */
    public static QuestionKey of(int index) {
        if (index < 0 || index > MAX_INDEX) {
            throw new IllegalArgumentException("question index must be within [0, " + MAX_INDEX + "], was " + index);
        }
        return new QuestionKey("q" + index);
    }

    /**
     * Returns the raw value so error messages stay readable.
     *
     * @return the key text
     */
    @Override
    public String toString() {
        return value;
    }
}
