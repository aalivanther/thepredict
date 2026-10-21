package akyrion.thepredict.domain;

/**
 * Stance MythicMobs and placeholders read (M23.1): the action when one is set, otherwise the base.
 */
public enum CombatStance {
    /** Base {@link BaseStance#NEUTRAL}. */
    NEUTRAL,
    /** Base {@link BaseStance#PRESSING}. */
    PRESSING,
    /** Base {@link BaseStance#GUARDED}. */
    GUARDED,
    /** Base {@link BaseStance#EVASIVE}. */
    EVASIVE,
    /** Action {@link ActionStance#WINDUP}. */
    WINDUP,
    /** Action {@link ActionStance#RECOVERY}. */
    RECOVERY,
    /** Action {@link ActionStance#STAGGERED}. */
    STAGGERED;

    private static final CombatStance[] STANCES = values();
    private static final int FIRST_ACTION = BaseStance.values().length - 1;

    /**
     * Visible stance of a packed state. Allocation-free: a stance republish calls it (rule C12).
     *
     * @param base base ordinal
     * @param action action ordinal
     * @return the visible stance
     */
    public static CombatStance visible(int base, int action) {
        if (action != ActionStance.NONE.ordinal()) {
            return STANCES[FIRST_ACTION + action];
        }
        return STANCES[base];
    }
}
