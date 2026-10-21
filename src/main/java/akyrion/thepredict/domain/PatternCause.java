package akyrion.thepredict.domain;

/**
 * Why a pattern record was written (M26.3). {@link #CHANGEPOINT_HALVED} does not change the state.
 */
public enum PatternCause {
    /** The pattern became active. */
    ENTER,
    /** The posterior mean fell below the exit line. */
    EXIT_MEAN,
    /** A changepoint halved the evidence and left the state as it was. */
    CHANGEPOINT_HALVED,
    /** Motivated hits opened a rest. */
    REST,
    /** The rest ended. */
    REST_END,
    /** The encounter closed. */
    ENCOUNTER_END
}
