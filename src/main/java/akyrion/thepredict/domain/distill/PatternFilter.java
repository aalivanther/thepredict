package akyrion.thepredict.domain.distill;

/**
 * Pattern filter of a teaching row (M29.1). Facts are counted on the target. A positive count with the effective
 * pattern at {@code NONE} is not a label and increments {@code DISTILL_PATTERN_FILTERED}.
 */
public final class PatternFilter {
    private PatternFilter() {
    }

    /**
     * Whether the row may become a label.
     *
     * @param patternFacts facts on the target
     * @param patternNone whether the effective pattern is {@code NONE}
     * @return {@code true} when the row counts
     */
    public static boolean counts(int patternFacts, boolean patternNone) {
        return !(patternFacts > 0 && patternNone);
    }
}
