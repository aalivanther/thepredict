package akyrion.thepredict.eval;

import java.util.Map;

/**
 * Replay of a saved answer by model and body hash (M22.4).
 */
public final class ReplayAnswers {
    private ReplayAnswers() {
    }

    /**
     * Looks up one body.
     *
     * @param bank saved answers
     * @param key model and body hash
     * @return {@code REPLAY} or {@code REPLAY_MISS}
     */
    public static String lookup(Map<String, String> bank, String key) {
        return bank.containsKey(key) ? "REPLAY" : "REPLAY_MISS";
    }

    /**
     * Names a bank whose misses exceed one fifth.
     *
     * @param misses misses
     * @param total lookups
     * @return {@code REPLAY_PARTIAL} or {@code REPLAY}
     */
    public static String rate(int misses, int total) {
        return misses / (double) total > 0.20 ? "REPLAY_PARTIAL" : "REPLAY";
    }
}
