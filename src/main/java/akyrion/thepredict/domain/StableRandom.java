package akyrion.thepredict.domain;

/**
 * Stateless reproducible draws (M20.1, rule C16). The same {@code (seed, tick, salt)} always returns the same word,
 * in any call order. Ties of candidates, reactions and casts are never broken here.
 */
public final class StableRandom {
    /** Side of an orbit or a sidestep when it starts. */
    public static final int SALT_SIDE = 1;
    /** Side flip of an orbit. */
    public static final int SALT_FLIP = 2;
    /** Phase of waypoint renewal, so mobs do not path on the same tick. */
    public static final int SALT_PHASE = 3;
    /**
     * Whether an adoption falls in the explore stratum (M28). {@code 4} is {@code TelegraphPolicy.SALT_FEINT}.
     * The index inside the stratum uses {@link #SALT_EXPLORE_INDEX}.
     */
    public static final int SALT_EXPLORE = 5;
    /** Index drawn inside the explore stratum. Independent of {@link #SALT_EXPLORE}. */
    public static final int SALT_EXPLORE_INDEX = 6;
    /**
     * Assist audit draw (M29.4, errata E29-1 applied). One bucket of {@code distill.auditEvery} is the audit.
     */
    public static final int SALT_DISTILL_AUDIT = 7;

    private StableRandom() {
    }

    /**
     * SplitMix64 finalizer.
     *
     * @param z mixed word
     * @return the finalized word
     */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /**
     * Session seed from a base and two identifiers.
     *
     * @param base installation seed ({@code debug.seed})
     * @param a first identifier
     * @param b second identifier
     * @return {@code mix(base ^ mix(a ^ mix(b)))}
     */
    public static long seed(long base, long a, long b) {
        return mix(base ^ mix(a ^ mix(b)));
    }

    /**
     * One draw. Order of calls does not matter.
     *
     * @param seed session seed
     * @param tick server tick
     * @param salt constant salt of this use
     * @return the word
     */
    public static long draw(long seed, long tick, int salt) {
        return mix(seed ^ mix(tick * 0x9E3779B97F4A7C15L + salt));
    }

    /**
     * Uniform integer in {@code [0, bound)}.
     *
     * @param word a mixed word
     * @param bound exclusive upper bound, in {@code [1, 2^31)}
     * @return the integer
     * @throws IllegalArgumentException if {@code bound} is outside that range
     */
    public static int below(long word, int bound) {
        if (bound < 1) {
            throw new IllegalArgumentException("bound must be within [1, 2^31), was " + bound);
        }
        return (int) (((word >>> 32) * (bound & 0xffffffffL)) >>> 32);
    }

    /**
     * A chance of {@code permille} parts per thousand.
     *
     * @param word a mixed word
     * @param permille parts per thousand
     * @return whether the draw falls below {@code permille}
     */
    public static boolean chance(long word, int permille) {
        return below(word, 1000) < permille;
    }
}
