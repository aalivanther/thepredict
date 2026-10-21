package akyrion.thepredict.domain;

/** Situation a question asks about: now, or a contingency prepared in advance. */
public enum Situation {
    /** The best tactic right now. */
    NOW,
    /** The tactic to use if the target reaches melee distance. */
    IF_TARGET_IN_MELEE,
    /** The tactic to use if the mob's own health becomes critical. */
    IF_OWN_HEALTH_CRITICAL
}
