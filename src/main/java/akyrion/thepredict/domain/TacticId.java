package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * Semantic tactic name that reaches the model as an option name (rule I4).
 *
 * <p>Pattern {@code [a-z][a-z0-9_]{2,39}}. The value {@code none} and the prefix {@code debug_}
 * are reserved: {@code none} means "no tactic" and {@code debug_} stays set apart for diagnostic
 * tooling (the M01 debug intents used it until M12). Profiles cannot declare reserved values; the
 * binder rejects them. Immutable.
 * The constructor checks that shape, including while {@code none} is initialized.
 *
 * @param value the snake_case tactic name
 */
public record TacticId(String value) {
    /** Reserved tactic used when no tactic is active. */
    public static final TacticId NONE = new TacticId("none");

    /** Prefix reserved for diagnostic tooling; profiles cannot declare it. */
    public static final String DEBUG_PREFIX = "debug_";

    /**
     * Validates the format.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} does not match the pattern
     */
    public TacticId {
        Objects.requireNonNull(value, "value");
        if (!shaped(value)) {
            throw new IllegalArgumentException("tactic must match [a-z][a-z0-9_]{2,39}, was '" + value + "'");
        }
    }

    /**
     * A checked id. Equal to {@code new TacticId(value)}. Nothing is kept: no static state (C11, D8) and no
     * unbounded cache (A5); the shape check compiles no pattern, so a cache would save nothing.
     *
     * @param value the snake_case tactic name
     * @return the id
     */
    public static TacticId of(String value) {
        return new TacticId(value);
    }

    private static boolean shaped(String value) {
        var length = value.length();
        if (length < 3 || length > 40) {
            return false;
        }
        var first = value.charAt(0);
        if (first < 'a' || first > 'z') {
            return false;
        }
        for (var index = 1; index < length; index++) {
            var symbol = value.charAt(index);
            var letter = symbol >= 'a' && symbol <= 'z';
            var digit = symbol >= '0' && symbol <= '9';
            if (!letter && !digit && symbol != '_') {
                return false;
            }
        }
        return true;
    }

    /**
     * Tells whether a profile is forbidden from declaring this tactic.
     *
     * @return {@code true} for {@code none} and for any {@code debug_} tactic
     */
    public boolean isReserved() {
        return value.equals(NONE.value) || value.startsWith(DEBUG_PREFIX);
    }

    /**
     * Returns the raw value so error messages stay readable.
     *
     * @return the tactic name
     */
    @Override
    public String toString() {
        return value;
    }
}
