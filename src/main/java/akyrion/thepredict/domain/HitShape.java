package akyrion.thepredict.domain;

/**
 * Shape of the first damaging mechanic of a skill (M23.3).
 */
public enum HitShape {
    /** {@code @target} or {@code @trigger}. */
    SINGLE_TARGET,
    /** A radius targeter. */
    AREA,
    /** A cone targeter. */
    CONE,
    /** A projectile mechanic. */
    PROJECTILE,
    /** Nothing readable. */
    UNKNOWN
}
