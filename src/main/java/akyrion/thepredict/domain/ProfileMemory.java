package akyrion.thepredict.domain;

import java.util.List;

/**
 * History of one player against one profile (M27.1). Counts are exact and do not decay. A rung byte of {@code 255}
 * means the band was never seen. Six rungs are three party bands by two ladder sections.
 *
 * @param hash FNV-1a of the profile name
 * @param fights encounters
 * @param wins wins
 * @param losses losses
 * @param disengaged disengagements
 * @param lastOutcome last result
 * @param lastEpochDay day of the last encounter
 * @param ttkWeight decayed TTK weight
 * @param ttkMean decayed log-TTK mean
 * @param ttkM2 decayed second moment
 * @param rungs six rung bytes
 * @param counters at most two counters
 */
public record ProfileMemory(int hash, long fights, long wins, long losses, long disengaged, FightOutcome lastOutcome,
                            long lastEpochDay, float ttkWeight, float ttkMean, float ttkM2, int[] rungs,
                            List<CounterMemory> counters) {
    /** Rungs stored for one profile. */
    public static final int RUNGS = 6;
    /** Byte that means the band has no rung. */
    public static final int NONE = 0xFF;

    /**
     * Copies the rungs and the counters.
     *
     * @throws IllegalArgumentException if a count is negative or the rung vector is not six bytes
     */
    public ProfileMemory {
        if (fights < 0 || wins < 0 || losses < 0 || disengaged < 0 || lastEpochDay < 0) {
            throw new IllegalArgumentException("profile counts");
        }
        if (rungs.length != RUNGS) {
            throw new IllegalArgumentException("rungs");
        }
        rungs = rungs.clone();
        counters = List.copyOf(counters);
        if (counters.size() > 2) {
            throw new IllegalArgumentException("counters");
        }
    }
}
