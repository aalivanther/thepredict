package akyrion.thepredict.domain;

/**
 * How much of each option and instruction a question sends to Jev (M16.2). The user chooses it with
 * {@code decision.questionDetail} and, per profile, {@code Timing.QuestionDetail}; its only runtime source is
 * {@link DecisionTiming#questionDetail()}. More detail costs more tokens and never widens the global token limits.
 */
public enum QuestionDetail {
    /** One short text of at most 120 characters per option and a short text instruction; the cheapest level. */
    COMPACT,
    /** One object per option with the full purpose phrases of M15.7, and a structured instruction. */
    STANDARD,
    /** Default since the re-bench of M16.10: {@code STANDARD} plus what fits and what fits poorly now, and the fixed
     * focus phrase; the only level that may send the optional fit checks. */
    FULL;

    /**
     * Detail of a fresh installation and of every timing built without one: the only place of this default (C13).
     * {@code FULL} since the re-bench of M16.10 (user decision of 2026-09-30, DT-31).
     */
    public static final QuestionDetail DEFAULT = FULL;
}
