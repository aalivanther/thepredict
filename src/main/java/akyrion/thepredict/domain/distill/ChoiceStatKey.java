package akyrion.thepredict.domain.distill;

import java.util.Objects;

/**
 * Primary key of {@code m29_choice_stats} (M29.1).
 *
 * @param profile profile id
 * @param regime regime key
 * @param context fine context text
 * @param tactic tactic id
 * @param source behavior source token
 * @param week week index
 * @param model {@code outcome}, {@code rollout} or {@code kev-*}
 */
public record ChoiceStatKey(String profile, String regime, String context, String tactic, String source, int week,
                            String model) {
    /**
     * Checks the components. A Jev model is not a teacher.
     */
    public ChoiceStatKey {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(regime, "regime");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(tactic, "tactic");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(model, "model");
        if (!allowed(model)) {
            throw new IllegalArgumentException("choice model");
        }
    }

    /**
     * Whether a model token may label a choice.
     *
     * @param model the token
     * @return {@code true} for outcome, rollout and kev names
     */
    public static boolean allowed(String model) {
        return "outcome".equals(model) || "rollout".equals(model) || model.startsWith("kev-");
    }
}
