package akyrion.thepredict.domain.distill;

import akyrion.thepredict.domain.StableRandom;

/**
 * Assist audit sample (M29.4, errata E29-1 applied). One of every {@code distill.auditEvery} delegated decisions
 * is drawn with {@link StableRandom} and salt {@link StableRandom#SALT_DISTILL_AUDIT}. The draw is reproducible
 * and is not a label.
 */
public final class AuditSampler {
    private AuditSampler() {
    }

    /**
     * Whether this delegated index is the audit.
     *
     * @param seed session seed
     * @param delegatedIndex how many delegated decisions this session has already accepted, starting at 1
     * @param every audit period
     * @return {@code true} when the draw falls in the first bucket
     */
    public static boolean audit(long seed, int delegatedIndex, int every) {
        var word = StableRandom.draw(seed, delegatedIndex, StableRandom.SALT_DISTILL_AUDIT);
        return StableRandom.below(word, every) == 0;
    }
}
