package akyrion.thepredict.engine.distill;

/**
 * When a group regime may change (M29.5). The tick is the group replan tick. It does not bump
 * {@code planEpoch} or {@code membershipEpoch}, and it waits for {@code minPlanTicks}.
 */
public final class GroupRegimeClock {
    private GroupRegimeClock() {
    }

    /**
     * Whether this tick may switch the group regime.
     *
     * @param tick server tick
     * @param period {@code group.replanPeriodTicks}
     * @param planAge ticks the current plan has lived
     * @param minPlan {@code group.minPlanTicks}
     * @return {@code true} when a switch is due
     */
    public static boolean due(long tick, int period, int planAge, int minPlan) {
        return period > 0 && Math.floorMod(tick, period) == 0 && planAge >= minPlan;
    }
}
