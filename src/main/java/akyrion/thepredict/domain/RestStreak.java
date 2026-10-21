package akyrion.thepredict.domain;

/**
 * Motivated-hit streak that opens a rest (M26.6). A miss clears it. The rest itself lives on the tracker.
 */
public final class RestStreak {
    private RestStreak() {
    }

    /**
     * The streak after one motivated cast.
     *
     * @param streak hits already in a row
     * @param success whether this cast was a motivated hit
     * @return the next streak
     */
    public static int next(int streak, boolean success) {
        return success ? streak + 1 : 0;
    }

    /**
     * Whether the streak opens a rest.
     *
     * @param streak hits in a row
     * @param afterSuccesses the configured threshold
     * @return {@code true} when a rest starts
     */
    public static boolean opens(int streak, int afterSuccesses) {
        return streak >= afterSuccesses;
    }
}
