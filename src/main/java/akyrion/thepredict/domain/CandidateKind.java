package akyrion.thepredict.domain;

/** What an evaluation candidate is (M22.4). */
public enum CandidateKind {
    /** The delivered configuration. */
    CONFIG_DEFAULT,
    /** A profile. */
    PROFILE,
    /** A code preset that does not move the mob. */
    CODE_PRESET,
    /** A learned artifact. It needs a passing gate before it acts. */
    LEARNED_ARTIFACT
}
