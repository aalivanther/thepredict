package akyrion.thepredict.domain;

/**
 * The order of a cast that comes due against a player (M24.6, revision 3, errata E24-7). Feasibility is asked first
 * and opens nothing. An infeasible answer retries and does not reach the push policy, except under entropy, which
 * lifts that block (errata E24-3 does not lift guards or reposition). A deferral or a skip opens
 * nothing. The tell opens only on allow, and only when the expected class asks for one. Releasing an open tell does
 * not call this type.
 */
public final class ZoneCastOrder {
    /** What the cast does this tick. */
    public enum Step {
        /** Cast on this tick. The class does not open a tell. */
        CAST,
        /** Open the tell, then wait. */
        OPEN,
        /** Wait, with no signal and no telegraph line. */
        DEFER,
        /** Skip the choice until the danger drops. */
        SKIP,
        /** Ask again later. Nothing was opened. */
        RETRY,
        /** Give up the tell for the whiff cooldown. */
        DROP
    }

    private ZoneCastOrder() {
    }

    /**
     * The step for a caller that does not say whether the tell shows its minimum. It assumes it does not, so a lethal
     * push against a player defers and then skips (A12, safe default).
     *
     * @param player whether the target is a player
     * @param mode effective zone mode
     * @param entropy whether entropy is active for this mob
     * @param expected class taken from {@link DangerRules}, before any tell exists
     * @param feasibility answer of the expected class
     * @param displacement how the choice moves the target
     * @param danger push danger of that displacement
     * @param deferredTicks ticks already deferred
     * @param deferMax ticks before a skip
     * @param waitedTicks ticks spent retrying an infeasible answer
     * @param waitMax ticks before the tell is dropped
     * @return the step
     */
    public static Step step(boolean player, ZoneMode mode, boolean entropy, DangerClass expected,
                            Feasibility feasibility, DisplacementKind displacement, PushDanger danger,
                            int deferredTicks, int deferMax, int waitedTicks, int waitMax) {
        return step(player, mode, entropy, expected, feasibility, displacement, danger, deferredTicks, deferMax,
                waitedTicks, waitMax, false);
    }

    /**
     * The step for a choice that is not already holding a tell.
     *
     * @param player whether the target is a player
     * @param mode effective zone mode
     * @param entropy whether entropy is active for this mob
     * @param expected class taken from {@link DangerRules}, before any tell exists
     * @param feasibility answer of the expected class
     * @param displacement how the choice moves the target
     * @param danger push danger of that displacement
     * @param deferredTicks ticks already deferred
     * @param deferMax ticks before a skip
     * @param waitedTicks ticks spent retrying an infeasible answer
     * @param waitMax ticks before the tell is dropped
     * @param tellShowsMinimum whether the tell would show at least its minimum, from
     *        {@link TelegraphPolicy#showsMinimum}
     * @return the step
     */
    public static Step step(boolean player, ZoneMode mode, boolean entropy, DangerClass expected,
                            Feasibility feasibility, DisplacementKind displacement, PushDanger danger,
                            int deferredTicks, int deferMax, int waitedTicks, int waitMax, boolean tellShowsMinimum) {
        if (!player || mode == ZoneMode.OFF) {
            return expected.enforced() ? Step.OPEN : Step.CAST;
        }
        if (feasibility == Feasibility.INFEASIBLE && !entropy) {
            return waitedTicks >= waitMax ? Step.DROP : Step.RETRY;
        }
        var decision = PushPolicy.decide(player, displacement, danger, expected, feasibility, deferredTicks, deferMax,
                entropy, mode, tellShowsMinimum);
        if (decision == PushDecision.DEFER) {
            return Step.DEFER;
        }
        if (decision == PushDecision.SKIP) {
            return Step.SKIP;
        }
        return expected.enforced() ? Step.OPEN : Step.CAST;
    }
}
