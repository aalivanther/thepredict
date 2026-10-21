package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.SessionState;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Bounded reconciliation that covers lost lifecycle events (M06.6). Every {@link #ROUND_INTERVAL_TICKS} ticks a
 * round takes the UUIDs of the active sessions and then checks at most {@link #SESSIONS_PER_TICK} of them per
 * tick; a session whose mob has no active MythicMobs instance, or whose entity is no longer valid, closes with
 * {@code SESSION_INVALID}. The same round closes the encounters left without members. Server thread only.
 *
 * <p>Starting a round copies the active UUIDs once, without any call to the game; only the checks, which read
 * MythicMobs, are limited per tick (rule A5).
 */
public final class SessionReconciler {
    /** Ticks between the starts of two rounds. */
    public static final int ROUND_INTERVAL_TICKS = 200;
    /** Maximum sessions checked per tick. */
    public static final int SESSIONS_PER_TICK = 32;

    private final SessionRegistry sessions;
    private final EncounterRegistry encounters;
    private final Predicate<UUID> isActive;
    private final SessionMetrics metrics;
    private final ArrayDeque<UUID> round = new ArrayDeque<>();
    private long nextRoundTick;
    private boolean started;

    /**
     * Creates the reconciliation.
     *
     * @param sessions sessions to check
     * @param encounters encounters whose empty ones close in each round
     * @param isActive {@code CombatReader.isActive}: the mob has an active MythicMobs instance and a valid entity
     * @param metrics counters of reconciled sessions
     */
    public SessionReconciler(SessionRegistry sessions, EncounterRegistry encounters, Predicate<UUID> isActive,
                             SessionMetrics metrics) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.isActive = Objects.requireNonNull(isActive, "isActive");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Starts a round when it is due and checks the next sessions of the current round.
     *
     * @param tick current server tick
     * @return number of sessions checked in this tick, at most {@link #SESSIONS_PER_TICK}
     */
    public int tick(long tick) {
        if (round.isEmpty() && (!started || tick - nextRoundTick >= 0)) {
            startRound(tick);
        }
        var checked = 0;
        while (checked < SESSIONS_PER_TICK && !round.isEmpty()) {
            var entityId = round.pollFirst();
            checked++;
            var session = sessions.find(entityId);
            if (session.isPresent() && session.get().state() == SessionState.ACTIVE && !isActive.test(entityId)) {
                sessions.close(entityId, FallbackReason.SESSION_INVALID);
                metrics.sessionEnded(SessionMetrics.SessionEnd.RECONCILED);
            }
        }
        return checked;
    }

    /**
     * Counts the sessions still to check in the current round.
     *
     * @return remaining sessions of the round
     */
    public int remaining() {
        return round.size();
    }

    private void startRound(long tick) {
        started = true;
        nextRoundTick = tick + ROUND_INTERVAL_TICKS;
        sessions.active().forEach(session -> round.addLast(session.key().entityId()));
        encounters.closeEmpty();
    }
}
