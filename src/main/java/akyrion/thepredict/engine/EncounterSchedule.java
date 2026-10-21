package akyrion.thepredict.engine;

import java.util.OptionalLong;

/**
 * Scheduling state of one {@link Encounter} (01 §8): request sequence and the last one handled by M10, request in
 * flight, pending context, next and
 * last decision, the version that makes older agenda entries stale, and the offset of the path permissions. Owned
 * by its encounter; {@link Agenda}, {@link InFlightRegistry} and {@link DecisionDispatcher} change it. Server thread
 * only.
 */
final class EncounterSchedule {
    private long sequence;
    private long handledSequence;
    private boolean requestInFlight;
    private boolean contextPending;
    private Agenda.PendingCause pendingCause = Agenda.PendingCause.CONTEXT;
    private long nextDecisionTick;
    private OptionalLong lastDecisionTick = OptionalLong.empty();
    private long version;
    private int pathOffset;

    /**
     * Takes the next request sequence, starting at 1. The dispatcher takes it just before preparing a request, so a
     * request that is then not sent (no budget, nothing to ask or a failed preparation) leaves a harmless gap (M16.1).
     */
    long nextSequence() {
        return ++sequence;
    }

    /** The last sequence taken, sent or not, 0 before the first; only its request can still be accepted. */
    long sequence() {
        return sequence;
    }

    /** The sequence whose result M10 already handled, so a duplicate or late result of it is discarded. */
    long handledSequence() {
        return handledSequence;
    }

    void handledSequence(long handled) {
        handledSequence = handled;
    }

    boolean requestInFlight() {
        return requestInFlight;
    }

    void requestInFlight(boolean inFlight) {
        requestInFlight = inFlight;
    }

    boolean contextPending() {
        return contextPending;
    }

    void contextPending(boolean pending) {
        contextPending = pending;
    }

    Agenda.PendingCause pendingCause() {
        return pendingCause;
    }

    void pendingCause(Agenda.PendingCause cause) {
        pendingCause = cause;
    }

    long nextDecisionTick() {
        return nextDecisionTick;
    }

    void nextDecisionTick(long tick) {
        nextDecisionTick = tick;
    }

    OptionalLong lastDecisionTick() {
        return lastDecisionTick;
    }

    void lastDecisionTick(long tick) {
        lastDecisionTick = OptionalLong.of(tick);
    }

    long version() {
        return version;
    }

    /** Makes every older agenda entry stale and returns the new version. */
    long nextVersion() {
        return ++version;
    }

    /** Position of the first member allowed to compute a path in the next capture (M09.8). */
    int pathOffset() {
        return pathOffset;
    }

    /** Moves the path permissions past the members that just used them. */
    void advancePathOffset(int count, int members) {
        pathOffset = Math.floorMod(pathOffset + count, Math.max(1, members));
    }
}
