package akyrion.thepredict.domain;

/**
 * At most {@code maxExploitedPatterns} biases, by confidence times counter value, ties by ordinal (M26.6). A held
 * pattern stays while its score is within {@code 0.05} of the pattern it would displace. Allocates nothing.
 */
public final class VarietyPicker {
    private static final double HYSTERESIS = 0.05;
    private static final EnemyPattern[] PATTERNS = EnemyPattern.values();

    private VarietyPicker() {
    }

    /**
     * Bits of the patterns that may carry a bias. The {@code limit} best active patterns are kept first. Then each
     * held pattern left out, from the best down, replaces the weakest kept pattern that is not held, but only while
     * its score is at most {@code 0.05} below that one.
     *
     * @param active bits that are active
     * @param confidence one value per pattern ordinal, in {@code [0, 1]}
     * @param held bits already biased
     * @param limit how many may stay biased
     * @return the chosen bits
     */
    public static int select(int active, double[] confidence, int held, int limit) {
        var eligible = eligible(active, confidence.length);
        var chosen = 0;
        for (var kept = 0; kept < limit; kept++) {
            var best = best(eligible & ~chosen, confidence);
            if (best < 0) {
                break;
            }
            chosen |= 1 << best;
        }
        return preferHeld(chosen, eligible & held & ~chosen, confidence, held);
    }

    private static int eligible(int active, int length) {
        var bits = 0;
        for (var ordinal = 0; ordinal < length; ordinal++) {
            if ((active & (1 << ordinal)) != 0 && ordinal != EnemyPattern.FIXED_CADENCE.ordinal()) {
                bits |= 1 << ordinal;
            }
        }
        return bits;
    }

    private static int preferHeld(int chosen, int waiting, double[] confidence, int held) {
        var left = waiting;
        while (left != 0) {
            var next = best(left, confidence);
            left &= ~(1 << next);
            var victim = lowestUnheld(chosen, held, confidence);
            if (victim < 0) {
                break;
            }
            if (score(confidence, next) + HYSTERESIS >= score(confidence, victim)) {
                chosen = (chosen & ~(1 << victim)) | (1 << next);
            }
        }
        return chosen;
    }

    private static int best(int bits, double[] confidence) {
        var best = -1;
        for (var ordinal = 0; ordinal < confidence.length; ordinal++) {
            if ((bits & (1 << ordinal)) != 0 && (best < 0 || score(confidence, ordinal) > score(confidence, best))) {
                best = ordinal;
            }
        }
        return best;
    }

    private static int lowestUnheld(int chosen, int held, double[] confidence) {
        var victim = -1;
        for (var ordinal = 0; ordinal < confidence.length; ordinal++) {
            if ((chosen & (1 << ordinal)) == 0 || (held & (1 << ordinal)) != 0) {
                continue;
            }
            if (victim < 0 || score(confidence, ordinal) < score(confidence, victim)) {
                victim = ordinal;
            }
        }
        return victim;
    }

    private static double score(double[] confidence, int ordinal) {
        return confidence[ordinal] * CounterMap.of(PATTERNS[ordinal]).factor();
    }
}
