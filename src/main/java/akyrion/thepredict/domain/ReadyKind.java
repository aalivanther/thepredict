package akyrion.thepredict.domain;

/** How soon a choice can fire, as far as the native cooldown can say (M20.7). */
public enum ReadyKind {
    /** A finite number of ticks, including zero. */
    READY_IN,
    /** No charges and no pending recharge ({@code Long.MAX_VALUE}). */
    EXHAUSTED,
    /** The skill is not an abstract skill, or MythicMobs is absent. */
    UNKNOWN
}
