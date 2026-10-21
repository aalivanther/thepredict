package akyrion.thepredict.domain;

/**
 * Action stance the engine sets for a tell, a recovery or a poise break (M23.1). {@code NONE} leaves the base visible.
 * Priority when two would apply is {@code STAGGERED}, then {@code WINDUP}, then {@code RECOVERY}.
 */
public enum ActionStance {
    /** No tell, recovery or stagger. */
    NONE,
    /** A tell is open. */
    WINDUP,
    /** The caster is exposed after the tell. */
    RECOVERY,
    /** Poise broke. */
    STAGGERED
}
