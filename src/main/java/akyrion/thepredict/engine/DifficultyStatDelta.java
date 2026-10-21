package akyrion.thepredict.engine;

import java.util.Objects;

/**
 * One difficulty row to merge (M22.6). The start-rung pair is kept by the later epoch, not by addition.
 *
 * @param profile profile id
 * @param context {@code v1:} context
 * @param rung rung 0 to 8
 * @param fights fights in this delta
 * @param logTtkMean mean of the log time to kill
 * @param logTtkM2 second moment
 * @param censored censored fights
 * @param excluded excluded fights
 * @param startRungEstimate prior {@code u}
 * @param startRungEpochMs epoch of that prior
 * @param firstEpochMs earliest epoch in the delta
 * @param lastEpochMs latest epoch in the delta
 */
public record DifficultyStatDelta(String profile, String context, int rung, long fights, double logTtkMean,
                                  double logTtkM2, long censored, long excluded, double startRungEstimate,
                                  long startRungEpochMs, long firstEpochMs, long lastEpochMs) {
    /**
     * Checks the row.
     *
     * @throws IllegalArgumentException if a count is negative or the rung is outside 0–8
     */
    public DifficultyStatDelta {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(context, "context");
        if (rung < 0 || rung > 8 || fights < 0 || censored < 0 || excluded < 0) {
            throw new IllegalArgumentException("difficulty delta is out of range");
        }
    }
}
