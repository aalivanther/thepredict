package akyrion.thepredict.domain;

/**
 * Rule 8 for a held skip (M20 plan, implemented in M28). The parity exception does not apply. A failed live predicate
 * is {@link Result#SITUATION_CHANGED}. A changed signature whose predicates still hold is aged. The caller maps
 * that result to {@code VOI_SKIP_SITUATION_CHANGED} or {@code DISTILLED_SITUATION_CHANGED}.
 */
public final class HeldIntentRevalidation {
    private HeldIntentRevalidation() {
    }

    /** What the reactive visit should do with the held intent. */
    public enum Result {
        /** Signature unchanged and not expired. */
        KEEP,
        /** Signature changed and the live predicates still hold. */
        AGED,
        /** A live predicate failed. */
        SITUATION_CHANGED,
        /** Past {@code expiresTick}, or the next capture arrived. */
        EXPIRED
    }

    /**
     * Checks one visit.
     *
     * @param tick current tick
     * @param expiresTick last valid tick
     * @param captureSignature signature at adoption
     * @param liveSignature signature now
     * @param predicatesFail live safe-veto or exhausted skill
     * @return the result
     */
    public static Result check(long tick, long expiresTick, int captureSignature, int liveSignature,
                               boolean predicatesFail) {
        if (tick > expiresTick) {
            return Result.EXPIRED;
        }
        if (liveSignature == captureSignature) {
            return predicatesFail ? Result.SITUATION_CHANGED : Result.KEEP;
        }
        return predicatesFail ? Result.SITUATION_CHANGED : Result.AGED;
    }
}
