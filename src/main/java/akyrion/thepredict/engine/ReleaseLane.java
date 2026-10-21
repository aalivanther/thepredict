package akyrion.thepredict.engine;

import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.TelegraphEnd;

import java.util.List;
import java.util.function.Consumer;

/**
 * The release lane of the tick layer (M23.4): due tells are released before the ring, deferred ones first, then by the
 * earliest release tick and ring order; due stances then advance and posture lines are flushed. Each session is handled
 * inside its own boundary (rule C7, A6): an exception in a release, a stance step or a flush drops only that session to
 * level 4 and closes its tell, so the lane never picks it again and the other sessions go on. A release reads the
 * signals of its own tick first, the point where the cast path reads them (M20.4). An empty scan does not read the
 * clock and does not count a visit. Allocation-free; server thread only.
 */
final class ReleaseLane {
    /** Releases admitted when no releasable session names its cap. */
    static final int DEFAULT_CAP = 32;

    private ReleaseLane() {
    }

    /**
     * Releases due tells and advances due stances.
     *
     * @param active sessions in enrollment order
     * @param tick current tick
     * @param steps releases and stance steps
     * @param reader signal source of the release tick
     * @param mismatch marks context pending once per target mismatch
     * @param fail drops one session to level 4
     * @param budget time cap of the pass
     */
    static void run(List<MobSession> active, long tick, ExecutionSteps steps, CombatReader reader,
                    Consumer<MobSession> mismatch, Consumer<MobSession> fail, TickBudget budget) {
        if (!anyDue(active, tick)) {
            return;
        }
        var cap = cap(active, tick);
        var admitted = 0;
        var stopped = false;
        for (var index = 0; index < active.size() && !stopped && admitted < cap; index++) {
            var session = active.get(index);
            if (session.releaseDeferred() && releasable(session, tick)) {
                stopped = release(session, tick, steps, reader, mismatch, fail, budget);
                admitted++;
            }
        }
        while (!stopped && admitted < cap) {
            var best = earliest(active, tick);
            if (best == null) {
                break;
            }
            stopped = release(best, tick, steps, reader, mismatch, fail, budget);
            admitted++;
        }
        settle(active, tick, steps, fail);
    }

    /**
     * Flushes the posture lines and the closed tells of every session, each inside its own boundary.
     *
     * @param active sessions in enrollment order
     * @param tick current tick
     * @param tap records tap
     * @param fail drops one session to level 4
     */
    static void flush(List<MobSession> active, long tick, DecisionTap tap, Consumer<MobSession> fail) {
        for (var index = 0; index < active.size(); index++) {
            var session = active.get(index);
            try {
                PostureRecords.flush(session, tick, tap);
            } catch (RuntimeException exception) {
                fail.accept(session);
            }
        }
    }

    /**
     * Whether a session's tell may be released on this tick.
     *
     * @param session the session
     * @param tick current tick
     * @return {@code true} for an active session whose release tick has come
     */
    static boolean releasable(MobSession session, long tick) {
        return session.state() == SessionState.ACTIVE && session.releaseTick() <= tick;
    }

    private static boolean anyDue(List<MobSession> active, long tick) {
        for (var index = 0; index < active.size(); index++) {
            var session = active.get(index);
            if (releasable(session, tick) || stanceDue(session, tick)) {
                return true;
            }
        }
        return false;
    }

    private static boolean stanceDue(MobSession session, long tick) {
        return session.state() == SessionState.ACTIVE && session.nextStanceTick() <= tick;
    }

    /** The cap of {@code telegraph.maxReleasesPerTick}. Profiles inherit it from the global section and never set it. */
    private static int cap(List<MobSession> active, long tick) {
        for (var index = 0; index < active.size(); index++) {
            var session = active.get(index);
            if (releasable(session, tick)) {
                return session.profile().telegraph().maxReleasesPerTick();
            }
        }
        return DEFAULT_CAP;
    }

    private static MobSession earliest(List<MobSession> active, long tick) {
        MobSession best = null;
        var bestTick = Long.MAX_VALUE;
        for (var index = 0; index < active.size(); index++) {
            var session = active.get(index);
            if (session.releaseDeferred() || !releasable(session, tick) || session.releaseTick() >= bestTick) {
                continue;
            }
            bestTick = session.releaseTick();
            best = session;
        }
        return best;
    }

    /**
     * One release inside the boundary of its session. Signals are read first unless this tick already read them. A
     * session still due after its turn waits as deferred, so one pass never releases the same tell twice.
     *
     * @return whether the time cap is spent
     */
    private static boolean release(MobSession session, long tick, ExecutionSteps steps, CombatReader reader,
                                   Consumer<MobSession> mismatch, Consumer<MobSession> fail, TickBudget budget) {
        try {
            if (session.signalsAtTick() != tick) {
                TickLayer.sample(session, tick, reader, mismatch);
            }
            steps.releaseTell(session, tick);
        } catch (RuntimeException exception) {
            drop(session, tick, fail);
        }
        if (releasable(session, tick)) {
            session.releaseDeferred(true);
        }
        return budget.noteRelease();
    }

    /**
     * Marks the overflow deferred and advances due stances. A deferred release keeps its wind-up: its stance does not
     * step to recovery before the hit leaves (M23.4).
     */
    private static void settle(List<MobSession> active, long tick, ExecutionSteps steps, Consumer<MobSession> fail) {
        for (var index = 0; index < active.size(); index++) {
            var session = active.get(index);
            if (releasable(session, tick)) {
                session.releaseDeferred(true);
                continue;
            }
            if (!stanceDue(session, tick)) {
                continue;
            }
            try {
                steps.advanceStance(session, tick);
            } catch (RuntimeException exception) {
                drop(session, tick, fail);
            }
        }
    }

    /**
     * Drops a session after an exception: its tell closes first, so the lane cannot pick it again, then the group books
     * hear the end and the session falls to level 4. A failure of the books still reaches the boundary of the step,
     * with the tell already closed.
     */
    private static void drop(MobSession session, long tick, Consumer<MobSession> fail) {
        var open = session.tellOpen();
        session.closeTell();
        try {
            if (open) {
                GroupTellClose.apply(session, TelegraphEnd.SESSION_INVALID, tick);
            }
        } finally {
            fail.accept(session);
        }
    }
}
