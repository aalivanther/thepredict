package akyrion.thepredict.engine;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fixed counters of sessions and encounters for {@code /thepredict status} (M06.6, M12): opened and closed
 * sessions by cause, opened and closed encounters by cause, average encounter duration and failed releases.
 * Memory is constant: nothing is kept per UUID (rule A5). Server thread only.
 */
public final class SessionMetrics {
    /** Why a session ended. */
    public enum SessionEnd {
        /** {@code MythicMobDeathEvent}. */
        DIED,
        /** {@code MythicMobDespawnEvent}. */
        DESPAWNED,
        /** {@code EntityRemoveEvent} without a MythicMobs event. */
        REMOVED,
        /** Its type is no longer bound or {@code Predictor} is false. */
        UNBOUND,
        /** The reconciliation found no active MythicMobs instance. */
        RECONCILED
    }

    /** Why an encounter closed. */
    public enum EncounterEnd {
        /** No member session remained. */
        NO_MEMBERS,
        /** No member had a target and nobody was in combat any more. */
        COMBAT_ENDED
    }

    private final Map<SessionEnd, Long> sessionEnds = new EnumMap<>(SessionEnd.class);
    private final Map<EncounterEnd, Long> encounterEnds = new EnumMap<>(EncounterEnd.class);
    private long openedSessions;
    private long openedEncounters;
    private long closedEncounterTicks;
    private long failedReleases;

    /** Creates zeroed counters. */
    public SessionMetrics() {
    }

    /**
     * Returns the number of sessions opened since start.
     *
     * @return opened sessions
     */
    public long openedSessions() {
        return openedSessions;
    }

    /**
     * Returns the number of sessions that ended for a cause.
     *
     * @param cause end cause
     * @return closed sessions with that cause
     */
    public long endedSessions(SessionEnd cause) {
        return sessionEnds.getOrDefault(Objects.requireNonNull(cause, "cause"), 0L);
    }

    /**
     * Returns the number of encounters opened since start.
     *
     * @return opened encounters
     */
    public long openedEncounters() {
        return openedEncounters;
    }

    /**
     * Returns the number of encounters that closed for a cause.
     *
     * @param cause end cause
     * @return closed encounters with that cause
     */
    public long closedEncounters(EncounterEnd cause) {
        return encounterEnds.getOrDefault(Objects.requireNonNull(cause, "cause"), 0L);
    }

    /**
     * Returns the average duration of the closed encounters.
     *
     * @return ticks from formation to close, 0 before the first close
     */
    public long averageEncounterTicks() {
        var closed = encounterEnds.values().stream().mapToLong(Long::longValue).sum();
        return closed == 0 ? 0 : closedEncounterTicks / closed;
    }

    /**
     * Returns the number of goal releases that threw.
     *
     * @return failed {@code MobControl.release} calls
     */
    public long failedReleases() {
        return failedReleases;
    }

    void sessionOpened() {
        openedSessions++;
    }

    void sessionEnded(SessionEnd cause) {
        sessionEnds.merge(cause, 1L, Long::sum);
    }

    void encounterOpened() {
        openedEncounters++;
    }

    void encounterClosed(EncounterEnd cause, long durationTicks) {
        encounterEnds.merge(cause, 1L, Long::sum);
        closedEncounterTicks += Math.max(0, durationTicks);
    }

    void releaseFailed() {
        failedReleases++;
    }
}
