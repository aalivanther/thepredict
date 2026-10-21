package akyrion.thepredict.domain;

/** How candidates are ordered (M20.6). The only default lives here (rule C13). */
public enum CandidateScoring {
    /** {@link LocalPlanner#score} scaled to 0–1000. Delivery default. */
    PRIORITY,
    /**
     * The priority score of {@link #PRIORITY} times the enemy-pattern factor of M26 ({@link CandidatePolicy#rank});
     * the learned order reads only the priority curve of {@link UtilityScore}. The other curves of {@link UtilityScore}
     * (reach, readiness, shield, risk) are not wired in this version.
     */
    UTILITY,
    /** Base score times a learned factor. Acts only with an active value artifact (M28). */
    LEARNED;

    /** Delivery default (decision M20-D5). */
    public static final CandidateScoring DEFAULT = PRIORITY;
}
