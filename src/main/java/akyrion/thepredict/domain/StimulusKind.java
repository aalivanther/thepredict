package akyrion.thepredict.domain;

/**
 * Windows that give a token its occasion (M26.2). A ring of four is kept per participant.
 */
public enum StimulusKind {
    /** The member just struck. Window of 20 ticks. */
    AFTER_OWN_STRIKE,
    /** The enemy just blocked. Window of 16 ticks. */
    AFTER_BLOCKED,
    /** A tell is open, until impact plus 5 ticks. */
    TELL_OPEN,
    /** The mob stepped back. Window of 20 ticks. */
    MOB_STEPS_BACK,
    /** The enemy is low on health. Window of 60 ticks. */
    LOW_HEALTH,
    /** A projectile is incoming, for its flight plus 5 ticks. */
    PROJECTILE_INCOMING
}
