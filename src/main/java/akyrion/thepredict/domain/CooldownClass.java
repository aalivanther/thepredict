package akyrion.thepredict.domain;

/**
 * How often a skill can be used, read from its native MythicMobs {@code Cooldown} plus the compatibility {@code CD}
 * (M15.3); never declared by the author.
 */
public enum CooldownClass {
    /** No fixed readable value: not a regular skill, a placeholder, or not resolved yet. */
    UNKNOWN,
    /** No cooldown at all. */
    NONE,
    /** Up to 3 seconds. */
    SHORT,
    /** More than 3 and up to 10 seconds. */
    MEDIUM,
    /** More than 10 seconds. */
    LONG
}
