package akyrion.thepredict.domain;

/**
 * Live vetoes of an answer whose situation signature changed (M20.7, rule 8). A signature change by itself accepts
 * the answer and marks it aged. Only a live safe veto or an exhausted skill discards it.
 */
public final class SituationRules {
    private SituationRules() {
    }

    /**
     * Why a changed situation rejects the tactic, or empty when the answer is only aged.
     *
     * @param sustainWhileHealthy the tactic's purpose is a sustain that is not allowed while healthy
     * @param liveHealth health band read this tick
     * @param outOfReach the movement is hold or retreat and the delivery needs a closer band than the live one
     * @param readiness readiness of the lead choice; a vanilla melee is {@link ReadyIn#in(int)} of 0
     * @return the discard, empty when rule 9 applies
     */
    public static java.util.Optional<DiscardReason> veto(boolean sustainWhileHealthy, HealthBand liveHealth,
                                                         boolean outOfReach, ReadyIn readiness) {
        if (sustainWhileHealthy && liveHealth == HealthBand.HEALTHY) {
            return java.util.Optional.of(DiscardReason.SITUATION_CHANGED);
        }
        if (outOfReach) {
            return java.util.Optional.of(DiscardReason.SITUATION_CHANGED);
        }
        if (readiness.kind() == ReadyKind.EXHAUSTED) {
            return java.util.Optional.of(DiscardReason.SITUATION_CHANGED);
        }
        return java.util.Optional.empty();
    }
}
