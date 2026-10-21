package akyrion.thepredict.domain;

/** Why a received answer is not applied (M10). */
public enum DiscardReason {
    /** A newer request of the encounter exists. */
    STALE_SEQUENCE,
    /** The mob changed generation. */
    SESSION_CHANGED,
    /** The session is not active. */
    SESSION_INACTIVE,
    /** Profiles or MythicMobs were reloaded. */
    CATALOG_CHANGED,
    /** The answering model is not the configured one. */
    MODEL_CHANGED,
    /** The answer arrived after its deadline. */
    TOO_OLD,
    /** The mob changed target. */
    TARGET_CHANGED,
    /** The mob could not be probed. */
    PROBE_UNAVAILABLE,
    /** A reactive answer no longer matches the live situation (M20.7). Kept last so older dumps stay stable. */
    SITUATION_CHANGED,
    /** {@code g0} was captured under another membership epoch (M25). The leader question is kept. */
    GROUP_EPOCH_CHANGED
}
