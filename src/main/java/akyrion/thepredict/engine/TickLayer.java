package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.ReactionBand;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.TickTier;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Per-tick walk (M20.3, M20.4, M20.5). A world whose every profile is {@link TickLayerMode#CASTS} stays on the M19
 * walk. This class holds no cursor: the caller keeps it. Comparisons are not visits. The clock supplier is read
 * after each cast and after every eight visits that did not cast. The release lane runs first ({@link ReleaseLane}),
 * then the ring; every session of both is handled inside its own boundary (rule C7). Each visit republishes the
 * group, pattern and zone fields of the board when they changed ({@link StanceBoard#syncShared}).
 */
final class TickLayer {
    private TickLayer() {
    }

    /**
     * One pass.
     *
     * @param active sessions in enrollment order
     * @param maximum visit cap
     * @param cursor where the previous tick stopped
     * @param tick current tick
     * @param nowNanos monotonic time
     * @param board intent board
     * @param reader signal source
     * @param steps ready casts
     * @param metrics counters
     * @param fail drops one session to level 4
     * @param maxMicros time cap of the pass
     * @param minHold minimum ticks a micro stays published
     * @param degradeStep degradation step, 0 to 3
     * @param originNanos clock at the start of the pass
     * @param nanos clock, read only on the budget points
     * @param mismatch marks context pending once per target mismatch
     * @return visits, the next cursor, and whether a cap was hit
     */
    static Walk visit(List<MobSession> active, int maximum, int cursor, long tick, long nowNanos, IntentBoard board,
                      CombatReader reader, ExecutionSteps steps, DecisionMetrics metrics, Consumer<MobSession> fail,
                      int maxMicros, int minHold, int degradeStep, long originNanos, LongSupplier nanos,
                      Consumer<MobSession> mismatch) {
        return visit(active, maximum, cursor, tick, nowNanos, board, reader, steps, metrics, fail, maxMicros, minHold,
                degradeStep, originNanos, nanos, mismatch, DecisionTap.NONE);
    }

    /**
     * One pass that tells {@code tap} about a fired reaction and a micro change when {@link DecisionTap#detail()} is
     * {@link TapDetail#FULL} (M21.1).
     *
     * @param active sessions in enrollment order
     * @param maximum visit cap
     * @param cursor where the previous tick stopped
     * @param tick current tick
     * @param nowNanos monotonic time
     * @param board intent board
     * @param reader signal source
     * @param steps ready casts
     * @param metrics counters
     * @param fail drops one session to level 4
     * @param maxMicros time cap of the pass
     * @param minHold minimum ticks a micro stays published
     * @param degradeStep degradation step, 0 to 3
     * @param originNanos clock at the start of the pass
     * @param nanos clock, read only on the budget points
     * @param mismatch marks context pending once per target mismatch
     * @param tap records tap; {@link DecisionTap#NONE} builds nothing
     * @return visits, the next cursor, and whether a cap was hit
     */
    static Walk visit(List<MobSession> active, int maximum, int cursor, long tick, long nowNanos, IntentBoard board,
                      CombatReader reader, ExecutionSteps steps, DecisionMetrics metrics, Consumer<MobSession> fail,
                      int maxMicros, int minHold, int degradeStep, long originNanos, LongSupplier nanos,
                      Consumer<MobSession> mismatch, DecisionTap tap) {
        return visit(active, maximum, cursor, tick, nowNanos, board, reader, steps, metrics, fail, minHold,
                degradeStep, mismatch, tap, new TickBudget(originNanos, maxMicros, nanos));
    }

    /**
     * One pass with a budget the caller reuses from tick to tick (rule C12).
     *
     * @param active sessions in enrollment order
     * @param maximum visit cap
     * @param cursor where the previous tick stopped
     * @param tick current tick
     * @param nowNanos monotonic time
     * @param board intent board
     * @param reader signal source
     * @param steps ready casts
     * @param metrics counters
     * @param fail drops one session to level 4
     * @param minHold minimum ticks a micro stays published
     * @param degradeStep degradation step, 0 to 3
     * @param mismatch marks context pending once per target mismatch
     * @param tap records tap; {@link DecisionTap#NONE} builds nothing
     * @param budget time cap of this pass, already reset
     * @return visits, the next cursor, and whether a cap was hit
     */
    static Walk visit(List<MobSession> active, int maximum, int cursor, long tick, long nowNanos, IntentBoard board,
                      CombatReader reader, ExecutionSteps steps, DecisionMetrics metrics, Consumer<MobSession> fail,
                      int minHold, int degradeStep, Consumer<MobSession> mismatch, DecisionTap tap,
                      TickBudget budget) {
        return Walk.of(pass(active, maximum, cursor, tick, nowNanos, board, reader, steps, metrics, fail, minHold,
                degradeStep, mismatch, tap, budget));
    }

    /**
     * {@link #visit(List, int, int, long, long, IntentBoard, CombatReader, ExecutionSteps, DecisionMetrics, Consumer,
     * int, int, Consumer, DecisionTap, TickBudget)} with the result packed in a {@code long} ({@link Walk#pack}), so the
     * upkeep of every tick allocates no {@link Walk} (rule C12).
     *
     * @param active sessions in enrollment order
     * @param maximum visit cap
     * @param cursor where the previous tick stopped
     * @param tick current tick
     * @param nowNanos monotonic time
     * @param board intent board
     * @param reader signal source
     * @param steps ready casts
     * @param metrics counters
     * @param fail drops one session to level 4
     * @param minHold minimum ticks a micro stays published
     * @param degradeStep degradation step, 0 to 3
     * @param mismatch marks context pending once per target mismatch
     * @param tap records tap; {@link DecisionTap#NONE} builds nothing
     * @param budget time cap of this pass, already reset
     * @return the packed walk
     */
    static long pass(List<MobSession> active, int maximum, int cursor, long tick, long nowNanos, IntentBoard board,
                     CombatReader reader, ExecutionSteps steps, DecisionMetrics metrics, Consumer<MobSession> fail,
                     int minHold, int degradeStep, Consumer<MobSession> mismatch, DecisionTap tap, TickBudget budget) {
        drainBoard(board, metrics);
        var count = active.size();
        if (count == 0 || maximum < 1) {
            return Walk.pack(0, cursor, false, true);
        }
        ReleaseLane.run(active, tick, steps, reader, mismatch, fail, budget);
        ReleaseLane.flush(active, tick, tap, fail);
        var index = Math.floorMod(cursor, count);
        var scanned = 0;
        var visits = 0;
        var stopped = false;
        for (; scanned < count && visits < maximum && !stopped; scanned++) {
            var outcome = guarded(active.get(index), tick, nowNanos, board, reader, steps, minHold, degradeStep,
                    mismatch, tap, fail);
            if (outcome != Outcome.SKIP) {
                visits++;
                stopped = budget.note(outcome == Outcome.CAST);
            }
            index = (index + 1) % count;
        }
        var capped = stopped || visits == maximum && scanned < count;
        if (capped) {
            defer(active, index, scanned, count, tick, nowNanos, board, degradeStep, metrics, tap);
        }
        return Walk.pack(visits, index, capped, visits * 2 < maximum && budget.underHalf());
    }

    /**
     * Reads this tick's signals of one session (M20.3 step 2): the player flag, the zone and the target mismatch. The
     * release lane calls it before a release so both read {@code h} at the same point of the tick (M20.4).
     *
     * @param session the session
     * @param tick current tick
     * @param reader signal source
     * @param mismatch marks context pending once per target mismatch
     * @return whether the reading is valid
     */
    static boolean sample(MobSession session, long tick, CombatReader reader, Consumer<MobSession> mismatch) {
        var target = session.lastTargetId();
        var signals = target.isEmpty() ? 0L : reader.tickSignals(session.key(), target.get());
        session.playerTarget(target.isPresent() && reader.playerTarget(target.get()));
        signals = reader.applyZone(session, signals);
        session.tickSignals(signals);
        session.signalsAtTick(tick);
        if (!TickSignals.bit(signals, TickSignals.VALID)) {
            return false;
        }
        session.lastGapQuarters(TickSignals.gapQuarters(signals));
        if (TickSignals.bit(signals, TickSignals.TARGET_MISMATCH)) {
            if (!session.mismatchOpen()) {
                session.mismatchOpen(true);
                mismatch.accept(session);
            }
        } else {
            session.mismatchOpen(false);
        }
        return true;
    }

    private static void drainBoard(IntentBoard board, DecisionMetrics metrics) {
        var pending = board.drainMicroPending();
        if (pending > 0) {
            metrics.layer(DecisionMetrics.LayerCounter.MICRO_PATH_PENDING, pending);
        }
        var missing = board.drainMicroMissing();
        if (missing > 0) {
            metrics.layer(DecisionMetrics.LayerCounter.MICRO_NO_PATH, missing);
        }
    }

    /** One visit inside the boundary of its session: an exception drops only that session to level 4. */
    private static Outcome guarded(MobSession session, long tick, long nowNanos, IntentBoard board,
                                   CombatReader reader, ExecutionSteps steps, int minHold, int degradeStep,
                                   Consumer<MobSession> mismatch, DecisionTap tap, Consumer<MobSession> fail) {
        try {
            return one(session, tick, nowNanos, board, reader, steps, minHold, degradeStep, mismatch, tap);
        } catch (RuntimeException exception) {
            fail.accept(session);
            return Outcome.VISIT;
        }
    }

    private static void defer(List<MobSession> active, int index, int scanned, int count, long tick, long nowNanos,
                              IntentBoard board, int degradeStep, DecisionMetrics metrics, DecisionTap tap) {
        var deferred = 0;
        var ready = 0;
        var rest = scanned;
        var look = index;
        while (rest < count) {
            var session = active.get(look);
            if (needsVisit(session, tick, nowNanos, board, degradeStep)) {
                deferred++;
                if (session.nextReadyTick() <= tick) {
                    ready++;
                }
                if (degradeStep >= 3) {
                    TickSteer.freeze(board, session, tap);
                }
            }
            look = (look + 1) % count;
            rest++;
        }
        if (ready > 0) {
            metrics.add(DecisionMetrics.ReadyCounter.READY_DEFERRED, ready);
        }
        if (deferred > 0) {
            metrics.layer(DecisionMetrics.LayerCounter.TICK_LAYER_DEFERRED, deferred);
        }
    }

    private static Outcome one(MobSession session, long tick, long nowNanos, IntentBoard board, CombatReader reader,
                               ExecutionSteps steps, int minHold, int degradeStep, Consumer<MobSession> mismatch,
                               DecisionTap tap) {
        if (session.state() != SessionState.ACTIVE) {
            return Outcome.SKIP;
        }
        session.difficulty().touch(session, tick, reader, tap, board);
        StanceBoard.syncShared(board, session);
        var mode = session.profile().timing().tickLayer();
        var due = session.nextReadyTick() <= tick;
        if (mode == TickLayerMode.CASTS) {
            return due ? castOnly(session, tick, nowNanos, reader, steps, mismatch) : Outcome.SKIP;
        }
        var tier = tier(session, tick, nowNanos, board);
        session.cadence(tier);
        if (tier == TickTier.COLD) {
            return Outcome.SKIP;
        }
        if (session.debugIntent()) {
            return due ? cast(session, tick, nowNanos, steps, mismatch) : Outcome.SKIP;
        }
        return layered(session, detail(tier, session, tick, degradeStep), due, tick, nowNanos, board, reader, steps,
                minHold, mismatch, tap);
    }

    /** The visit of a session the layer works on: signals, reactions and micro by its tier, then the due cast. */
    private static Outcome layered(MobSession session, boolean detail, boolean due, long tick, long nowNanos,
                                   IntentBoard board, CombatReader reader, ExecutionSteps steps, int minHold,
                                   Consumer<MobSession> mismatch, DecisionTap tap) {
        if (!detail && !due) {
            return Outcome.SKIP;
        }
        var valid = detail ? sample(session, tick, reader, mismatch) : fresh(session, tick, reader, mismatch);
        if (!valid) {
            return Outcome.VISIT;
        }
        if (detail) {
            react(session, tick, board, steps, minHold, tap);
        }
        return due ? cast(session, tick, nowNanos, steps, mismatch) : Outcome.VISIT;
    }

    /**
     * Reactions and, in {@code FULL}, the micro. Every degradation step steers the sessions it visits: step 3 only
     * freezes the ones the cap leaves out (M20.3).
     */
    private static void react(MobSession session, long tick, IntentBoard board, ExecutionSteps steps, int minHold,
                              DecisionTap tap) {
        TickReactions.arm(session, session.tickSignals(), tick, tap);
        if (session.profile().timing().tickLayer() == TickLayerMode.FULL) {
            TickSteer.steer(session, session.tickSignals(), tick, board, steps, minHold, tap);
        }
    }

    private static Outcome castOnly(MobSession session, long tick, long nowNanos, CombatReader reader,
                                    ExecutionSteps steps, Consumer<MobSession> mismatch) {
        if (!fresh(session, tick, reader, mismatch)) {
            return Outcome.VISIT;
        }
        return cast(session, tick, nowNanos, steps, mismatch);
    }

    private static Outcome cast(MobSession session, long tick, long nowNanos, ExecutionSteps steps,
                                Consumer<MobSession> mismatch) {
        steps.castReady(session, tick, nowNanos);
        if (session.consumeContextMiss()) {
            mismatch.accept(session);
        }
        return Outcome.CAST;
    }

    /**
     * The signals of this tick for a due cast: read now, or the ones the release lane already read on this tick (M20.3
     * step 2 reads them for a cast only when this tick has not). A visit due by its tier always reads. An invalid
     * reading does not cast.
     */
    private static boolean fresh(MobSession session, long tick, CombatReader reader, Consumer<MobSession> mismatch) {
        if (session.signalsAtTick() != tick) {
            return sample(session, tick, reader, mismatch);
        }
        return TickSignals.bit(session.tickSignals(), TickSignals.VALID);
    }

    private static boolean needsVisit(MobSession session, long tick, long nowNanos, IntentBoard board,
                                      int degradeStep) {
        if (session.state() != SessionState.ACTIVE) {
            return false;
        }
        if (session.profile().timing().tickLayer() == TickLayerMode.CASTS) {
            return session.nextReadyTick() <= tick;
        }
        var tier = tier(session, tick, nowNanos, board);
        if (tier == TickTier.COLD) {
            return false;
        }
        return session.nextReadyTick() <= tick || detail(tier, session, tick, degradeStep);
    }

    private static TickTier tier(MobSession session, long tick, long nowNanos, IntentBoard board) {
        var view = board.current(session.key().entityId());
        var controlled = session.state() == SessionState.ACTIVE
                && view.generation() == session.key().generation() && view.controls(nowNanos);
        var band = session.probeBand();
        var beyond = band.isPresent() && band.get() == DistanceBand.BEYOND;
        return TickTier.of(controlled, session.lastTargetId().isPresent(), session.nextReadyTick(), tick, beyond,
                session.micro() == MicroBehaviour.NATIVE, armed(session, tick),
                session.tellOpen() || session.actionStance() != ActionStance.NONE,
                session.groupCopy().holdsToken());
    }

    private static boolean armed(MobSession session, long tick) {
        if (session.slotUntil(ReactionBand.SURVIVAL) > tick || session.slotUntil(ReactionBand.OPPORTUNITY) > tick) {
            return true;
        }
        for (var since : session.reactionArmedSince()) {
            if (since >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean detail(TickTier tier, MobSession session, long tick, int step) {
        if (tier == TickTier.HOT && step < 2) {
            return true;
        }
        var hash = session.key().entityId().hashCode();
        if (tier == TickTier.WARM && step >= 1) {
            return Math.floorMod(hash + tick, 4) == 1;
        }
        return Math.floorMod(hash + tick, 2) != 0;
    }

    private enum Outcome {
        SKIP, VISIT, CAST
    }

    /**
     * Visits performed, the next cursor, whether a cap stopped the pass, and whether both caps stayed under half. The
     * per-tick upkeep reads the same four fields from a packed {@code long}: the cursor in bits 0 to 30, the visits in
     * bits 31 to 61, the cap in bit 62 and the half mark in bit 63.
     *
     * @param visits visits performed, at least zero
     * @param cursor next cursor, at least zero
     * @param capped whether a cap stopped the pass
     * @param underHalf whether both caps stayed under half
     */
    record Walk(int visits, int cursor, boolean capped, boolean underHalf) {
        private static final long FIELD = 0x7FFF_FFFFL;
        private static final int VISITS_SHIFT = 31;
        private static final long CAPPED = 1L << 62;
        private static final long UNDER_HALF = 1L << 63;

        /**
         * Packs a walk without allocating.
         *
         * @param visits visits performed, at least zero
         * @param cursor next cursor, at least zero
         * @param capped whether a cap stopped the pass
         * @param underHalf whether both caps stayed under half
         * @return the packed walk
         */
        static long pack(int visits, int cursor, boolean capped, boolean underHalf) {
            return (cursor & FIELD) | (visits & FIELD) << VISITS_SHIFT | (capped ? CAPPED : 0L)
                    | (underHalf ? UNDER_HALF : 0L);
        }

        /**
         * Unpacks a walk.
         *
         * @param packed a value of {@link #pack}
         * @return the walk
         */
        static Walk of(long packed) {
            return new Walk(visits(packed), cursor(packed), capped(packed), underHalf(packed));
        }

        static int visits(long packed) {
            return (int) (packed >>> VISITS_SHIFT & FIELD);
        }

        static int cursor(long packed) {
            return (int) (packed & FIELD);
        }

        static boolean capped(long packed) {
            return (packed & CAPPED) != 0L;
        }

        static boolean underHalf(long packed) {
            return (packed & UNDER_HALF) != 0L;
        }
    }
}
