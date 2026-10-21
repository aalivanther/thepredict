package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.TacticalProfile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Turns the native lifecycle events of M06.2 into {@link SessionRegistry} operations and owns the release of
 * goals after an unbind and at shutdown. Server thread only (rule T1); the listeners of
 * {@code adapter.server.events} move every event here on the server thread first (rule T6).
 *
 * <p>Unbinding follows M05.4: the view is removed in the same call and {@code MobControl.release} runs on a
 * later tick, never the same one; at most {@link #MAX_RELEASES_PER_TICK} releases run per tick and the rest wait
 * with their conditions already false. Shutdown follows T7: every view is removed first and then at most
 * {@link #MAX_RELEASES_AT_SHUTDOWN} active sessions are released; the others keep the generated goals, whose
 * {@code predictor*} conditions are false without a view, so their base AI acts (M05.4).
 */
public final class SessionLifecycle {
    /** Maximum goal releases at plugin shutdown (T7, M05.4). */
    public static final int MAX_RELEASES_AT_SHUTDOWN = 200;
    /** Maximum goal releases per tick after unbinding. */
    public static final int MAX_RELEASES_PER_TICK = 32;

    private final SessionRegistry sessions;
    private final IntentBoard board;
    private final Consumer<MobKey> release;
    private final SessionReconciler reconciler;
    private final SessionMetrics metrics;
    private final ServerClock clock;
    private final ArrayDeque<PendingRelease> releases = new ArrayDeque<>();

    /**
     * Creates the lifecycle.
     *
     * @param sessions session registry
     * @param board intent board, emptied first at shutdown
     * @param release {@code MobControl.release}: gives the movement AI back to MythicMobs
     * @param reconciler bounded reconciliation run on every tick
     * @param metrics session counters
     * @param clock server clock
     */
    public SessionLifecycle(SessionRegistry sessions, IntentBoard board, Consumer<MobKey> release,
                            SessionReconciler reconciler, SessionMetrics metrics, ServerClock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.board = Objects.requireNonNull(board, "board");
        this.release = Objects.requireNonNull(release, "release");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * A bound mob appeared: one tick after {@code MythicMobSpawnEvent}, one tick after {@code EntitiesLoadEvent}
     * or during the initial scan. Opens its session, resumes a suspended one, and leaves an active one alone.
     * The session is marked {@code installPending}, so its goals are installed on a later tick (M05.4).
     *
     * @param entityId UUID of the mob
     * @param profile profile bound to its type
     */
    public void discovered(UUID entityId, TacticalProfile profile) {
        var before = sessions.find(entityId);
        sessions.open(entityId, profile);
        if (before.isEmpty()) {
            metrics.sessionOpened();
        }
    }

    /**
     * The chunk of a mob unloaded ({@code EntitiesUnloadEvent} or {@code EntityRemoveEvent} with cause
     * {@code UNLOAD}): its session suspends.
     *
     * @param entityId UUID of the mob
     */
    public void unloaded(UUID entityId) {
        sessions.suspend(entityId);
    }

    /**
     * A mob died, despawned or was removed: its session closes with {@code SESSION_INVALID}. No release is needed
     * because the entity is gone.
     *
     * @param entityId UUID of the mob
     * @param cause counted cause
     */
    public void ended(UUID entityId, SessionMetrics.SessionEnd cause) {
        if (sessions.close(entityId, FallbackReason.SESSION_INVALID).isPresent()) {
            metrics.sessionEnded(Objects.requireNonNull(cause, "cause"));
        }
    }

    /**
     * A mob changed world: it leaves its encounter and gets the next generation, which also asks for its goals
     * again because the server replaces the entity on a dimension change.
     *
     * @param entityId UUID of the mob
     */
    public void changedWorld(UUID entityId) {
        sessions.leaveEncounter(entityId);
        sessions.advanceGeneration(entityId);
    }

    /**
     * MythicMobs reloaded and M03 bound the new catalog: every session whose type is still bound advances with
     * the new profile; the others are unbound. The whole pass runs in one call so no view of the old generation
     * survives the tick (rule A6); the goal releases it causes are bounded per tick.
     *
     * @param boundProfiles profile bound to a MythicMobs mob ID in the new catalog, only with {@code Predictor: true}
     */
    public void reloaded(Function<String, Optional<TacticalProfile>> boundProfiles) {
        for (var session : sessions.all()) {
            var entityId = session.key().entityId();
            boundProfiles.apply(session.profile().mythicMobId()).ifPresentOrElse(
                    profile -> sessions.advanceGeneration(entityId, profile), () -> unbind(entityId));
        }
    }

    /**
     * Unbinds a mob: closes its session and removes its view now, and releases its goals on a later tick. A
     * suspended mob is unloaded and has no goals to release, so nothing is queued for it.
     *
     * @param entityId UUID of the mob
     */
    public void unbind(UUID entityId) {
        var wasActive = sessions.find(entityId).filter(session -> session.state() == SessionState.ACTIVE).isPresent();
        sessions.close(entityId, FallbackReason.SESSION_INVALID).ifPresent(key -> {
            metrics.sessionEnded(SessionMetrics.SessionEnd.UNBOUND);
            if (wasActive) {
                releases.addLast(new PendingRelease(key, clock.currentTick() + 1));
            }
        });
    }

    /**
     * Runs the per-tick work: at most {@link #MAX_RELEASES_PER_TICK} pending releases due on this tick, then the
     * reconciliation. A release is skipped, and still counts toward the limit, if the mob has a session again,
     * which installs its own goals.
     */
    public void tick() {
        var tick = clock.currentTick();
        for (var handled = 0; handled < MAX_RELEASES_PER_TICK && !releases.isEmpty()
                && releases.peekFirst().dueTick() <= tick; handled++) {
            var pending = releases.pollFirst();
            if (sessions.find(pending.key().entityId()).isEmpty()) {
                releaseSafely(pending.key());
            }
        }
        reconciler.tick(tick);
    }

    /**
     * Shuts down in the order of T7 once the task is stopped and the requests cancelled: removes every view, so
     * every condition turns false, then releases the goals of at most {@link #MAX_RELEASES_AT_SHUTDOWN} mobs, first
     * those unbound and still waiting for their release, then the active sessions in opening order.
     *
     * @return number of released mobs
     */
    public int shutdown() {
        board.clearAll();
        sessions.active().forEach(MobSession::invalidateReady);
        var keys = new ArrayList<MobKey>();
        releases.forEach(pending -> keys.add(pending.key()));
        releases.clear();
        sessions.active().forEach(session -> keys.add(session.key()));
        var count = Math.min(keys.size(), MAX_RELEASES_AT_SHUTDOWN);
        keys.subList(0, count).forEach(this::releaseSafely);
        return count;
    }

    /**
     * Counts the releases still waiting for their tick.
     *
     * @return pending releases
     */
    public int pendingReleases() {
        return releases.size();
    }

    /** Releases the goals of a mob; a failure is counted and never stops the loop (rule C7). */
    private void releaseSafely(MobKey key) {
        try {
            release.accept(key);
        } catch (RuntimeException exception) {
            metrics.releaseFailed();
        }
    }

    /** A release waiting for its tick. */
    private record PendingRelease(MobKey key, long dueTick) {
    }
}
