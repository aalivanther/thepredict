package akyrion.thepredict.domain;

/**
 * Two-wave retreat of {@code fall_back_and_regroup} (M25). The healthier half holds and shoots while the other
 * retreats, then the halves swap. The rally point is two blocks past the leader, away from the participants.
 */
public final class RegroupPolicy {
    /** Ticks one wave lasts before the halves swap. */
    public static final int WAVE_TICKS = 50;
    /** Blocks from the rally point at which the plan ends. */
    public static final int DONE_BLOCKS = 4;
    /** Ticks after which the plan ends. */
    public static final int DONE_TICKS = 100;

    private RegroupPolicy() {
    }

    /**
     * Whether this member retreats on the current wave. A higher health rank holds on wave 0.
     *
     * @param index member index
     * @param health higher means healthier
     * @param elapsed ticks since the plan started
     * @return whether the plan assigns {@code RETREAT}
     */
    public static boolean retreats(int index, int[] health, int elapsed) {
        if (index < 0 || index >= health.length || health.length == 0) {
            return false;
        }
        var healthier = 0;
        for (var other = 0; other < health.length; other++) {
            if (other == index) {
                continue;
            }
            if (health[other] > health[index] || (health[other] == health[index] && other < index)) {
                healthier++;
            }
        }
        var holding = healthier < Math.max(1, health.length / 2);
        var wave = Math.floorDiv(Math.max(0, elapsed), WAVE_TICKS) % 2;
        var hold = wave == 0 ? holding : !holding;
        return !hold;
    }

    /**
     * Whether the retreat has arrived or run out of time.
     *
     * @param blocks distance to the rally point
     * @param elapsed ticks since the plan started
     * @return whether the plan ends
     */
    public static boolean finished(double blocks, int elapsed) {
        return blocks <= DONE_BLOCKS || elapsed >= DONE_TICKS;
    }

    /**
     * Rally point, two blocks beyond the leader away from the centroid.
     *
     * @param leaderX leader column
     * @param leaderZ leader column
     * @param centroidX mean of the participants
     * @param centroidZ mean of the participants
     * @return {@code [x, z]}
     */
    public static double[] rally(double leaderX, double leaderZ, double centroidX, double centroidZ) {
        var dx = leaderX - centroidX;
        var dz = leaderZ - centroidZ;
        var length = StrictMath.hypot(dx, dz);
        if (length < 1.0e-6) {
            return new double[] {leaderX, leaderZ - 2.0};
        }
        return new double[] {leaderX + 2.0 * dx / length, leaderZ + 2.0 * dz / length};
    }
}
