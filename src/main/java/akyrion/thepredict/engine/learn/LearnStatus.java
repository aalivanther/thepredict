package akyrion.thepredict.engine.learn;

/**
 * Text of {@code /thepredict learn status} (M28.9). English command text. One line, bounded.
 */
public final class LearnStatus {
    private LearnStatus() {
    }

    /**
     * The line of the running server. The server holds no learned-artifact lifecycle, so the state is
     * {@link LearnedArtifacts.State#NONE}; collection and scope are the loaded {@code learning} settings.
     *
     * @param settings the loaded learning settings
     * @return one line
     */
    public static String line(akyrion.thepredict.domain.LearningSettings settings) {
        return line(LearnedArtifacts.State.NONE, "", "", settings.collect(), settings.scope());
    }

    /**
     * Formats the line.
     *
     * @param state artifact state
     * @param active active sha256, or empty
     * @param verdict verdict name, or empty
     * @param collect whether collection is on
     * @param scope bench or all
     * @return one line
     */
    public static String line(LearnedArtifacts.State state, String active, String verdict, boolean collect,
                              String scope) {
        var sha = active.isEmpty() ? "none" : active.substring(0, Math.min(12, active.length()));
        var gate = verdict.isEmpty() ? "none" : verdict;
        return "learn " + state.name().toLowerCase(java.util.Locale.ROOT) + " artifact " + sha + " verdict " + gate
                + " collect " + collect + " scope " + scope;
    }
}
