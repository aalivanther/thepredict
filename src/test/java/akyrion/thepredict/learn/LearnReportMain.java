package akyrion.thepredict.learn;

/**
 * Read-only count of coarse cells. Without a database path it prints the query and exits. It does not open a
 * server and does not write.
 */
public final class LearnReportMain {
    private LearnReportMain() {
    }

    /**
     * Prints the report contract.
     *
     * @param arguments optional sqlite path, unused until a writer database is supplied by the operator
     */
    public static void main(String[] arguments) {
        var path = arguments.length == 0 ? "(none)" : arguments[0];
        System.out.println("learnReport database " + path);
        System.out.println("cells v1:<banda>:<tipo> with windows >= 10 are counted from outcome_stats, "
                + "m26_pattern_stats and m27_player_memory when a database is open.");
    }
}
