package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.MicroSide;

/**
 * Path state of {@link MicroGoal} without Bukkit (M20.5). A final point within half a block is this goal's path.
 * An accepted path with nothing visible stays pending for four ticks and fails on the fifth. A rejected path fails
 * on the next renewal, not on the first observation. The first failure flips side; the second leaves native movement
 * for twenty ticks.
 */
final class MicroRoute {
    /** Squared distance, in blocks, inside which a path end still belongs to the asked point. */
    static final double OWN_RADIUS_SQUARED = 0.25;

    /** Ticks an accepted path may stay invisible. */
    static final int PENDING_LIMIT = 4;

    /** Ticks of native movement after the second failure. */
    static final int NATIVE_TICKS = 20;

    /** What one observation did. */
    enum Mark {
        CLEAR, WAIT, PENDING, FLIP, NATIVE
    }

    private int pending;
    private int failures;
    private boolean rejectedOnce;
    private boolean flipped;
    private boolean tracking;
    private long nativeUntil = Long.MIN_VALUE;

    static boolean owns(double askedX, double askedZ, double finalX, double finalZ) {
        var dx = askedX - finalX;
        var dz = askedZ - finalZ;
        return dx * dx + dz * dz <= OWN_RADIUS_SQUARED;
    }

    boolean nativeNow(long tick) {
        return tick < nativeUntil;
    }

    /** Holds native movement for {@link #NATIVE_TICKS} without counting a path failure. */
    void park(long tick) {
        nativeUntil = tick + NATIVE_TICKS;
        pending = 0;
        tracking = false;
    }

    long nativeUntil() {
        return nativeUntil;
    }

    boolean tracking() {
        return tracking;
    }

    MicroSide side(MicroSide published) {
        if (!flipped) {
            return published;
        }
        return published == MicroSide.LEFT ? MicroSide.RIGHT : MicroSide.LEFT;
    }

    Mark note(boolean accepted, boolean hasPath, long tick) {
        if (nativeNow(tick)) {
            return Mark.NATIVE;
        }
        if (hasPath) {
            pending = 0;
            rejectedOnce = false;
            tracking = false;
            return Mark.CLEAR;
        }
        if (accepted) {
            return pending(tick);
        }
        if (!rejectedOnce) {
            rejectedOnce = true;
            return Mark.WAIT;
        }
        return fail(tick);
    }

    private Mark pending(long tick) {
        pending++;
        tracking = true;
        if (pending <= PENDING_LIMIT) {
            return Mark.PENDING;
        }
        return fail(tick);
    }

    private Mark fail(long tick) {
        failures++;
        pending = 0;
        tracking = false;
        rejectedOnce = false;
        if (failures == 1) {
            flipped = true;
            return Mark.FLIP;
        }
        nativeUntil = tick + NATIVE_TICKS;
        return Mark.NATIVE;
    }
}
