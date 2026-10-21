package akyrion.thepredict.domain;

/**
 * Why an admission used, or refused, a subject's memory (M27.5).
 */
public enum MemoryAdmissionCause {
    /** A seed was applied. */
    SEEDED,
    /** The record was loaded and held no usable history. */
    COLD,
    /** The record was not loaded. The whole fight stays unloaded. */
    NOT_LOADED,
    /** The player is exempt. Nothing is produced. */
    EXEMPT,
    /** The mode is record, so the gate does not read the record. */
    MODE_RECORD,
    /** The mode was not verified and {@code learning.allowUnverified} is false. */
    UNVERIFIED
}
