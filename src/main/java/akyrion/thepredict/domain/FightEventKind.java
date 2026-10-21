package akyrion.thepredict.domain;

/** One line of a whole-fight clock (M22.8, M22.13). */
public enum FightEventKind {
    /** The first damage dealt to the current target. */
    ENGAGED,
    /** The target died. Carries the time to kill. */
    TARGET_DIED,
    /** The mob died. */
    MOB_DIED,
    /** The mob died before a valid hit, so the slow loop does not learn. */
    MOB_DIED_NO_EVIDENCE,
    /** The fight went quiet or the target left. */
    DISENGAGED,
    /** The target changed. The clock stays open. */
    TARGET_CHANGED,
    /** The oldest quiet clock was dropped to keep the cap. */
    EVICTED,
    /** Every controlled member of the group left. */
    GROUP_WIPED
}
