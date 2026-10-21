package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.SessionState;

import java.util.Objects;
import java.util.Optional;

/**
 * Checks that the answers about one mob still belong to its session (M10.1, per question; rules I1, A6).
 * Package-private; server thread only.
 *
 * <p>In order: the same generation ({@code SESSION_CHANGED}); an active session not held by the debug command
 * ({@code SESSION_INACTIVE}); still a member of the encounter that asked, so a session closed and reopened at
 * generation 0 in another encounter never takes an answer of the previous one ({@code SESSION_CHANGED}); a readable
 * mob ({@code PROBE_UNAVAILABLE}); and the target of the capture ({@code TARGET_CHANGED}). The probe is the only read
 * of the world; an exception while probing drops only that session to level 4.
 */
final class AnswerIdentity {
    private final CombatReader reader;
    private final IntentControl control;

    AnswerIdentity(CombatReader reader, IntentControl control) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.control = Objects.requireNonNull(control, "control");
    }

    /** Returns why the answers about {@code mob} are discarded, empty when they may be offered. */
    Optional<DiscardReason> check(MobSession session, MobKey mob, InFlightRequest request, long tick, long nowNanos) {
        try {
            return identity(session, mob, request, nowNanos);
        } catch (RuntimeException exception) {
            if (session.state() == SessionState.ACTIVE) {
                control.fail(session, tick);
            }
            return Optional.of(DiscardReason.PROBE_UNAVAILABLE);
        }
    }

    private Optional<DiscardReason> identity(MobSession session, MobKey mob, InFlightRequest request, long nowNanos) {
        if (session.key().generation() != mob.generation()) {
            return Optional.of(DiscardReason.SESSION_CHANGED);
        }
        if (session.state() != SessionState.ACTIVE || control.heldByDebug(session, nowNanos)) {
            return Optional.of(DiscardReason.SESSION_INACTIVE);
        }
        if (!session.encounter().equals(Optional.of(request.encounter()))) {
            return Optional.of(DiscardReason.SESSION_CHANGED);
        }
        var probe = reader.probe(mob);
        if (probe.isEmpty()) {
            return Optional.of(DiscardReason.PROBE_UNAVAILABLE);
        }
        return probe.get().targetId().equals(session.lastTargetId()) ? Optional.empty()
                : Optional.of(DiscardReason.TARGET_CHANGED);
    }
}
