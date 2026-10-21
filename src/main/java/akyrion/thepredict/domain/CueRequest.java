package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * The signal a tell, a stance or a pattern asks the adapter to send (M23.5, M26.11). No effect is attached.
 *
 * @param signal MythicMobs signal name, already prefixed
 */
public record CueRequest(String signal) {
    /**
     * Validates the signal.
     *
     * @throws IllegalArgumentException if the signal is blank
     */
    public CueRequest {
        DomainChecks.requireText(Objects.requireNonNull(signal, "signal"), "signal");
    }

    /**
     * Signal of an answer.
     *
     * @param answer the answer
     * @return {@code PREDICTOR_TELL_} plus the answer
     */
    public static CueRequest tell(TellAnswer answer) {
        return new CueRequest("PREDICTOR_TELL_" + Objects.requireNonNull(answer, "answer").name());
    }

    /**
     * Signal of a visible stance.
     *
     * @param stance the stance
     * @return {@code PREDICTOR_STANCE_} plus the stance
     */
    public static CueRequest stance(CombatStance stance) {
        return new CueRequest("PREDICTOR_STANCE_" + Objects.requireNonNull(stance, "stance").name());
    }

    /**
     * Signal that a pattern became active. It names the reading, not a tactic.
     *
     * @param pattern the pattern
     * @return {@code PREDICTOR_PATTERN_} plus the pattern
     */
    public static CueRequest pattern(EnemyPattern pattern) {
        return new CueRequest("PREDICTOR_PATTERN_" + Objects.requireNonNull(pattern, "pattern").name());
    }

    /**
     * Signal of a role change. The adapter sends it only when the mob handles it.
     *
     * @param role the role
     * @return {@code PREDICTOR_ROLE_} plus the role
     */
    public static CueRequest role(GroupRole role) {
        return new CueRequest("PREDICTOR_ROLE_" + Objects.requireNonNull(role, "role").name());
    }

    /**
     * Signal that a plan was adopted. The adapter sends it only when the mob handles it.
     *
     * @param plan the plan
     * @return {@code PREDICTOR_PLAN_} plus the plan
     */
    public static CueRequest plan(GroupPlan plan) {
        return new CueRequest("PREDICTOR_PLAN_" + Objects.requireNonNull(plan, "plan").name());
    }
}
