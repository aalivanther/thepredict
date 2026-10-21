package akyrion.thepredict.domain;

/**
 * Why an option left a question (M19.6). The merge of equal effects uses {@link #SAME_EFFECT_AS_HIGHER}
 * (errata E19-1, DP7).
 */
public enum PruneReason {
    /** A sustain skill while the mob is healthy. */
    SUSTAIN_WHILE_HEALTHY,
    /** A hold or retreat whose authored reach does not get to the target. */
    OUT_OF_REACH_STATIC,
    /** A hold that needs a line of sight the target does not give. */
    NO_SIGHT_STATIC,
    /** An approach or flank the mob cannot walk, and the choice cannot reach from here. */
    UNREACHABLE_ON_FOOT,
    /** The same effect as an option kept earlier. The survivor follows {@link OptionSet}. */
    SAME_EFFECT_AS_HIGHER,
    /** The option cap dropped it after the order. */
    BELOW_OPTION_CAP,
    /** A retreat whose raw cover query found no safe ground. */
    CORNERED_RETREAT
}
