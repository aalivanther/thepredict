package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.SessionState;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * The one writer of enriched threat (M11.4): hands a change to {@link CombatActions#addThreat} only for the key of the
 * current active session, checked in the same tick (rule I1), contains an exception (rule A6) and writes the
 * rate-limited line of {@code decision.logDecisions} with the participant's alias (rules C8, I6), in the execution log
 * that {@link TacticalExecution} shares with it, so both stay within one limit. Until it is shared nothing is written.
 * The line says the change was sent: the adapter still drops it without a threat table or for a participant offline,
 * dead or in another world. Logging never assigns an alias. Package-private; server thread only.
 */
final class ThreatChanges {
    private final SessionRegistry sessions;
    private final CombatActions actions;
    private final ThreatMetrics metrics;
    private ExecutionLog log = new ExecutionLog(line -> {
    }, false);
    private DecisionTap tap = DecisionTap.NONE;

    ThreatChanges(SessionRegistry sessions, CombatActions actions, ThreatMetrics metrics) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** Writes from now on in the execution log of the tactical execution, set once when it is wired. */
    void logTo(ExecutionLog executionLog) {
        log = Objects.requireNonNull(executionLog, "executionLog");
    }

    /** Tells the duel recorder of each change handed to the port (M18.4), set once when the execution is wired. */
    void tapTo(DecisionTap decisionTap) {
        tap = Objects.requireNonNull(decisionTap, "decisionTap");
    }

    /**
     * Changes the table of the current session of {@code key}.
     *
     * @return {@code false} when the generation is stale, the session is not active or the port threw
     */
    boolean apply(Encounter encounter, MobKey key, UUID participantId, double amount, String cause, long tick) {
        if (sessions.find(key).filter(session -> session.state() == SessionState.ACTIVE).isEmpty()) {
            return false;
        }
        try {
            actions.addThreat(key, participantId, amount);
        } catch (RuntimeException exception) {
            metrics.failures++;
            return false;
        }
        if (tap.detail() == TapDetail.FULL) {
            tap.threatChanged(key, participantId, amount, cause);
        }
        log.decision(tick, () -> "Threat " + String.format(Locale.ROOT, "%+.2f", amount) + " " + cause + " for "
                + encounter.aliases().find(participantId).map(alias -> alias.text())
                        .orElse("a participant without alias")
                + " sent to the table of mob " + key.entityId());
        return true;
    }
}
