package akyrion.thepredict.domain;

/**
 * Where a last-known cell came from (M25).
 */
public enum KnowledgeSource {
    /** A member saw it. */
    FIRST_HAND,
    /** Another member reported it. */
    SHARED
}
