package akyrion.thepredict.domain.learn;

import java.util.Objects;

/**
 * Whether a learned artifact may act in one capture (M28.9, rules A16 and W8). Without a player in the encounter a
 * {@code PASS} or {@code PASS_SIM_ONLY} verdict is enough. With a player, the scope must be {@code all} and the
 * verdict {@code PASS}; the {@code bench} scope never acts against players. Any other verdict never acts.
 */
public final class LearnedScope {
    /** The verdict that may act against players. */
    public static final String PASS = "PASS";
    /** The verdict that may act only without players. */
    public static final String PASS_SIM_ONLY = "PASS_SIM_ONLY";

    private LearnedScope() {
    }

    /**
     * The rule.
     *
     * @param scope {@code learning.scope}: {@code bench} or {@code all}
     * @param verdict status of the artifact's verdict
     * @param playersPresent whether a player takes part in the encounter now
     * @return {@code true} when the artifact may act
     */
    public static boolean allows(String scope, String verdict, boolean playersPresent) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(verdict, "verdict");
        if (playersPresent) {
            return "all".equals(scope) && PASS.equals(verdict);
        }
        return PASS.equals(verdict) || PASS_SIM_ONLY.equals(verdict);
    }
}
