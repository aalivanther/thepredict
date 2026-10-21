package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternSeed;

import java.util.UUID;

/**
 * Prior a later module may hand to a new encounter (M26.9). M26 does not call it. The gate type is
 * {@code CODE_PRESET}. M27 may replace {@link #NONE}.
 */
public interface EnemyModelSeed {
    /** No prior. */
    EnemyModelSeed NONE = new EnemyModelSeed() {
    };

    /**
     * Prior successes for one pattern. M26 ignores the value.
     *
     * @param participant the enemy
     * @param pattern the pattern
     * @return successes already counted, {@code 0} when there is no prior
     */
    default int priorSuccesses(UUID participant, EnemyPattern pattern) {
        var seed = seedFor(participant);
        if (!pattern.usesBeta()) {
            return 0;
        }
        return (int) Math.round(seed.successes()[pattern.ordinal()]);
    }

    /**
     * Seed masses for every Beta pattern. {@link #NONE} returns an empty seed.
     *
     * @param participant the player the model is about
     * @return the seed
     */
    default PatternSeed seedFor(UUID participant) {
        return PatternSeed.NONE;
    }
}
