package akyrion.thepredict.domain.learn;

/** What a labelled decision is scored against (M28.4). */
public enum CalibrationTarget {
    /** The answer is in the acceptable set. */
    ACCEPTABLE,
    /** The answer is the best label. */
    BEST
}
