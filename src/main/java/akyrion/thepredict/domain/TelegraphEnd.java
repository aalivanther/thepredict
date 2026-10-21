package akyrion.thepredict.domain;

/**
 * How an open tell closed (M23.4). {@link #FEINTED} stays last so later ends can be appended.
 */
public enum TelegraphEnd {
    /** The cast was released. */
    RELEASED,
    /** The cast was not made and no gesture was played. */
    WHIFFED,
    /** Poise broke while the tell was in flight. */
    INTERRUPTED,
    /** The caster left. */
    CASTER_GONE,
    /** The target left. */
    TARGET_LOST,
    /** MythicMobs rejected the skill. */
    REJECTED,
    /** The session was no longer the one that opened the tell. */
    SESSION_INVALID,
    /** The tell was a feint. No cast and no recovery. */
    FEINTED
}
