package akyrion.thepredict.domain;

/**
 * An adopted local intent that a later reorder must not replace (M28.8, errata G5 and E28-13 applied). M20 described
 * it and did not implement it. Every {@link BehaviorPolicy#VOI_SKIP} is one, even when the tactic is candidate 0.
 *
 * @param tactic the tactic that will be executed
 * @param behavior the recorded behavior
 * @param captureSignature signature at adoption
 * @param adoptedTick tick of adoption
 * @param expiresTick last tick the intent is valid, never before {@code adoptedTick}
 */
public record HeldIntent(TacticId tactic, BehaviorPolicy behavior, int captureSignature, long adoptedTick,
                         long expiresTick) {
    /**
     * Validates the intent.
     *
     * @throws NullPointerException if the tactic or the behavior is {@code null}
     * @throws IllegalArgumentException if the intent expires before its adoption
     */
    public HeldIntent {
        if (tactic == null || behavior == null) {
            throw new NullPointerException("held intent");
        }
        if (expiresTick < adoptedTick) {
            throw new IllegalArgumentException("expiresTick must be >= adoptedTick, was " + expiresTick + " < "
                    + adoptedTick);
        }
    }
}
