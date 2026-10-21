package akyrion.thepredict.engine;

/**
 * Joins the pattern model with player memory and with encounter closes (M26.9, M27). The composition root calls it
 * once both the pattern director and the memory host exist, so the seed, the captured style and the
 * {@code pattern_summary} line are never wired against a missing director.
 */
public final class PatternMemoryLink {
    private PatternMemoryLink() {
    }

    /**
     * Seeds new pattern models from memory, lets memory capture the fight style at close, writes one summary per
     * enemy at close and then drops the models of the encounter ({@link PatternDirector#close}). Close listeners
     * registered earlier, such as the memory close, keep running first, so they still read the style.
     *
     * @param patterns the pattern director
     * @param memory the memory host
     * @param minFightTrials own trials required when a memory seed is present
     * @param encounters the encounter registry whose closes write the summary
     */
    public static void attach(PatternDirector patterns, MemoryHost memory, int minFightTrials,
                              EncounterRegistry encounters) {
        patterns.useSeed(memory.modelSeed(), minFightTrials);
        memory.useStyles(patterns::styleOf);
        encounters.hearCloses(patterns::close);
    }
}
