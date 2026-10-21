package akyrion.thepredict.domain;

/**
 * Whether one choice may push on the tick it comes due (M24.6). Pure. An unknown danger never changes the cast.
 * Only a lethal push against a player can defer: it passes only behind a tell of class heavy or above that shows its
 * minimum ({@link TelegraphPolicy#showsMinimum}) with a feasible answer (A12). Entropy allows the push only in guard
 * or full (errata E24-3). A pull is allowed. An entity is allowed.
 */
public final class PushPolicy {
    private PushPolicy() {
    }

    /**
     * Decides one choice for a caller that does not say whether the tell shows its minimum. It assumes it does not,
     * so a lethal push against a player never passes outside entropy in guard or full (A12, safe default).
     *
     * @param player whether the target is a player
     * @param displacement how the choice moves the target
     * @param danger what that push does
     * @param expected the class the tell would use
     * @param feasibility whether that answer fits the ground
     * @param deferredTicks ticks this choice has already waited
     * @param deferMax ticks after which the choice is skipped
     * @param entropy whether entropy lifts the limit
     * @param mode effective zone mode; entropy lifts a lethal player push only in guard or full
     * @return the decision
     */
    public static PushDecision decide(boolean player, DisplacementKind displacement, PushDanger danger,
                                      DangerClass expected, Feasibility feasibility, int deferredTicks, int deferMax,
                                      boolean entropy, ZoneMode mode) {
        return decide(player, displacement, danger, expected, feasibility, deferredTicks, deferMax, entropy, mode,
                false);
    }

    /**
     * Decides one choice.
     *
     * @param player whether the target is a player
     * @param displacement how the choice moves the target
     * @param danger what that push does
     * @param expected the class the tell would use
     * @param feasibility whether that answer fits the ground
     * @param deferredTicks ticks this choice has already waited
     * @param deferMax ticks after which the choice is skipped
     * @param entropy whether entropy lifts the limit
     * @param mode effective zone mode; entropy lifts a lethal player push only in guard or full
     * @param tellShowsMinimum whether the tell this cast would open shows at least its minimum, from
     *        {@link TelegraphPolicy#showsMinimum}
     * @return the decision
     */
    public static PushDecision decide(boolean player, DisplacementKind displacement, PushDanger danger,
                                      DangerClass expected, Feasibility feasibility, int deferredTicks, int deferMax,
                                      boolean entropy, ZoneMode mode, boolean tellShowsMinimum) {
        if (!player || displacement == DisplacementKind.PULL || displacement == DisplacementKind.NONE
                || displacement == DisplacementKind.SELF_FORWARD || displacement == DisplacementKind.SELF_BACKWARD
                || displacement == DisplacementKind.SELF_OTHER) {
            return PushDecision.ALLOW;
        }
        if (danger != PushDanger.LETHAL) {
            return PushDecision.ALLOW;
        }
        if (entropy && (mode == ZoneMode.GUARD || mode == ZoneMode.FULL)) {
            return PushDecision.ALLOW;
        }
        var heavy = expected.ordinal() >= DangerClass.HEAVY.ordinal();
        if (heavy && feasibility == Feasibility.FEASIBLE && tellShowsMinimum) {
            return PushDecision.ALLOW;
        }
        if (deferredTicks >= deferMax) {
            return PushDecision.SKIP;
        }
        return PushDecision.DEFER;
    }
}
