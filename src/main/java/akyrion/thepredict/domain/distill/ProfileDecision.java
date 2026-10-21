package akyrion.thepredict.domain.distill;

/**
 * Profile overrides of the decision section (M29.8). {@code inherit} and {@code 0} keep the global value.
 *
 * @param pruning {@code inherit}, {@code off} or {@code safe}
 * @param maxOptions {@code 0} inherits, otherwise 2 to 8
 * @param scoring {@code inherit}, {@code priority}, {@code utility} or {@code learned}
 * @param diversity {@code inherit}, {@code off} or {@code on}
 */
public record ProfileDecision(String pruning, int maxOptions, String scoring, String diversity) {
    /** Every field inherits the global. */
    public static final ProfileDecision INHERIT = new ProfileDecision("inherit", 0, "inherit", "inherit");

    /**
     * Checks the tokens.
     */
    public ProfileDecision {
        if (!allowed(pruning, "inherit", "off", "safe") || !allowed(scoring, "inherit", "priority", "utility", "learned")
                || !allowed(diversity, "inherit", "off", "on")) {
            throw new IllegalArgumentException("Decision");
        }
        if (maxOptions != 0 && (maxOptions < 2 || maxOptions > 8)) {
            throw new IllegalArgumentException("Decision.MaxOptions");
        }
    }

    private static boolean allowed(String value, String... tokens) {
        for (var token : tokens) {
            if (token.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
