package akyrion.thepredict.domain;

/**
 * Why a stance transition happened (M23.1). Every transition carries one.
 */
public enum StanceCause {
    /** The tactic declared the base. */
    TACTIC,
    /** The movement table supplied the base. */
    MOVEMENT_DEFAULT,
    /** A live evade skipped dwell and hysteresis. */
    SURVIVAL,
    /** Dwell and hysteresis both elapsed. */
    DWELL_RELEASED,
    /** A base queued during an action applied when the action ended. */
    DEFERRED_APPLIED,
    /** A tell opened. */
    TELEGRAPH_START,
    /** The tell reached impact. */
    IMPACT,
    /** Recovery ended. */
    RECOVERY_END,
    /** The tell whiffed or was feinted, so the action closed without a cast. */
    WHIFF,
    /** The target left before release. */
    TARGET_LOST,
    /** MythicMobs rejected the release. */
    REJECTED,
    /** Poise broke. */
    POISE_BREAK,
    /** The stagger ended. */
    STAGGER_END,
    /** Enraged became true. */
    ENRAGE_ON,
    /** Enraged became false. */
    ENRAGE_OFF,
    /** The level is base or nothing controls the mob. */
    BASE_LEVEL,
    /** The session was invalidated. */
    INVALIDATED,
    /** A debug command set the stance. */
    DEBUG
}
