package akyrion.thepredict.domain;

/**
 * Closed ways a pattern may tilt a legal choice (M26.5). None of them prune, shorten a tell or add an attacker.
 */
public enum CounterBias {
    /** An opportunity cast preference. */
    OPPORTUNITY,
    /** A micro-behaviour and its side. */
    MICRO,
    /** The {@link Consideration#ENEMY_PATTERN} factor. */
    CONSIDERATION,
    /** A short reserve of one ready choice. */
    RESERVE,
    /** A hint on the group plan, inside {@code [1, 1.2]}. */
    GROUP_HINT
}
