package akyrion.thepredict.domain;

/**
 * Defaults and ranges of the {@code memory} section (M27). Every default lives here. {@code refreshSeconds} is
 * validated and has no effect with SQLite (errata E27-3 applied). {@code backend} is only {@code sqlite}.
 *
 * @param mode shipped mode
 * @param fightDecay per-fight decay
 * @param halfLifeDays half life in days
 * @param crossFightH CUSUM threshold
 * @param styleBreakFactor break factor
 * @param seedMaxTrials seed cap
 * @param minFightTrials own trials required for activation
 * @param rungPriorFights prior fights of a profile rung
 * @param maxRungOffset clip around the profile rung
 * @param minDamageShareRatio share required for a win
 * @param maxProfilesPerPlayer profiles kept
 * @param maxFactsPerRequest facts in one request
 * @param factMaxAgeDays oldest fact
 * @param cacheSubjects cache entries
 * @param maxHintsPerTick hints drained per tick
 * @param loadTimeoutSeconds time before a load fails
 * @param refreshSeconds validated, unused with SQLite
 * @param maxPendingDigestsPerSubject pending digests of one subject
 * @param maxPendingSubjects subjects with pending digests
 * @param maxSubjectsPerBatch subjects in one batch
 * @param retentionDays rows kept
 * @param maxRows row cap
 * @param allowUnverified whether an unverified mode may pass
 * @param backend {@code sqlite}
 */
public record MemorySettings(MemoryMode mode, double fightDecay, int halfLifeDays, int crossFightH,
                             double styleBreakFactor, int seedMaxTrials, int minFightTrials, int rungPriorFights,
                             int maxRungOffset, double minDamageShareRatio, int maxProfilesPerPlayer,
                             int maxFactsPerRequest, int factMaxAgeDays, int cacheSubjects, int maxHintsPerTick,
                             int loadTimeoutSeconds, int refreshSeconds, int maxPendingDigestsPerSubject,
                             int maxPendingSubjects, int maxSubjectsPerBatch, int retentionDays, int maxRows,
                             boolean allowUnverified, String backend) {
    /** A fresh installation. */
    public static final MemorySettings DEFAULT = new MemorySettings(MemoryMode.RECORD, 0.9, 30, 4, 0.25, 6, 4, 3, 3,
            0.2, 8, 2, 60, 512, 16, 10, 60, 8, 1024, 64, 180, 1_000_000,
            LearningSettings.DEFAULT.allowUnverified(), "sqlite");

    /**
     * Checks every range.
     *
     * @throws IllegalArgumentException if a value is outside its range or the backend is not sqlite
     */
    public MemorySettings {
        if (mode == null || backend == null) {
            throw new NullPointerException("memory settings");
        }
        range(fightDecay, 0.5, 1.0, "fightDecay");
        range(halfLifeDays, 1, 365, "halfLifeDays");
        range(crossFightH, 2, 8, "crossFightH");
        range(styleBreakFactor, 0.05, 0.5, "styleBreakFactor");
        range(seedMaxTrials, 0, 12, "seedMaxTrials");
        range(minFightTrials, 1, 8, "minFightTrials");
        range(rungPriorFights, 0, 20, "rungPriorFights");
        range(maxRungOffset, 0, 8, "maxRungOffset");
        range(minDamageShareRatio, 0.0, 1.0, "minDamageShareRatio");
        range(maxProfilesPerPlayer, 1, 12, "maxProfilesPerPlayer");
        range(maxFactsPerRequest, 0, 4, "maxFactsPerRequest");
        range(factMaxAgeDays, 1, 365, "factMaxAgeDays");
        range(cacheSubjects, 64, 8192, "cacheSubjects");
        range(maxHintsPerTick, 1, 64, "maxHintsPerTick");
        range(loadTimeoutSeconds, 2, 60, "loadTimeoutSeconds");
        range(refreshSeconds, 10, 3600, "refreshSeconds");
        range(maxPendingDigestsPerSubject, 2, 32, "maxPendingDigestsPerSubject");
        range(maxPendingSubjects, 64, 8192, "maxPendingSubjects");
        range(maxSubjectsPerBatch, 8, 256, "maxSubjectsPerBatch");
        range(retentionDays, 7, 3650, "retentionDays");
        range(maxRows, 1000, 10_000_000, "maxRows");
        if (!backend.equals("sqlite")) {
            throw new IllegalArgumentException("memory.backend");
        }
    }

    private static void range(double value, double low, double high, String name) {
        if (!(value >= low && value <= high)) {
            throw new IllegalArgumentException(name);
        }
    }

    private static void range(int value, int low, int high, String name) {
        if (value < low || value > high) {
            throw new IllegalArgumentException(name);
        }
    }
}
