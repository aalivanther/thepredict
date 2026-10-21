package akyrion.thepredict.domain;

/**
 * Result of releasing a tell (M23.4). The release does not check a shield.
 */
public enum ReleaseOutcome {
    /** The skill was cast. */
    RELEASED,
    /** No cast and no gesture. */
    WHIFFED,
    /** The target was gone. */
    TARGET_LOST,
    /** MythicMobs rejected the skill. */
    REJECTED,
    /** The target was still invulnerable. The release waits, at most the wind-up horizon. */
    DEFERRED_INVULNERABLE,
    /** The cap of this tick deferred the release. */
    RELEASE_DEFERRED,
    /** The session was not the one that opened the tell. */
    SESSION_INVALID
}
