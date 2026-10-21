package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PatternExploit;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.ZoneGuardKind;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What {@link IntentControl} tells the {@link DecisionTap} (M18.4). Package-private; server thread only. An answer is
 * told by the acceptance with its question, so an applied answer is not told again here; everything else that changes
 * an intent is: a fired contingency, a new local plan with the reason of the drop, a change of level, a change of tactic
 * of an alternative and each drop to level 4. No event is built while nothing records.
 */
final class IntentTap {
    /** Where an applied intent comes from, which decides what the tap hears. */
    enum Change {
        /** The intent of an accepted answer, already told by the acceptance. */
        ANSWER,
        /** An alternative or a debug intent. */
        ALTERNATIVE,
        /** A fired contingency. */
        CONTINGENCY,
        /** A local intent of the fallback chain. */
        LOCAL
    }

    private final DecisionTap tap;

    IntentTap(DecisionTap tap) {
        this.tap = Objects.requireNonNull(tap, "tap");
    }

    /** Tells an applied intent; nothing for an answer, a renewal or a local replan of the same tactic. */
    void applied(MobSession session, TacticalIntent intent, Change change, boolean newTactic, boolean newLevel,
                 Optional<FallbackReason> reason) {
        if (!tap.recording() || change == Change.ANSWER) {
            return;
        }
        DecisionTap.IntentEventKind kind;
        if (change == Change.CONTINGENCY) {
            kind = DecisionTap.IntentEventKind.CONTINGENCY_FIRED;
        } else if (intent.level() == ControlLevel.LOCAL) {
            kind = newTactic || newLevel ? DecisionTap.IntentEventKind.LOCAL_PLANNED : null;
        } else {
            kind = newLevel ? DecisionTap.IntentEventKind.LEVEL_CHANGED
                    : newTactic ? DecisionTap.IntentEventKind.ADOPTED : null;
        }
        if (kind != null) {
            var from = session.intent().map(TacticalIntent::level).orElse(ControlLevel.BASE);
            tap.intentEvent(session.key(), new DecisionTap.IntentEvent(kind, intent.tactic(), intent.movement(),
                    intent.confidence(), from, intent.level(), Optional.empty(), reason));
        }
    }

    /** Tells that a session drops to level 4 with a reason; called before its intent is dropped. */
    void dropped(MobSession session, FallbackReason reason) {
        if (tap.recording()) {
            told(session, Optional.of(reason));
        }
    }

    /** Tells that a session drops to level 4 without a reason, as a release does. */
    void dropped(MobSession session) {
        if (tap.recording()) {
            told(session, Optional.empty());
        }
    }

    private void told(MobSession session, Optional<FallbackReason> reason) {
        session.intent().ifPresent(intent -> tap.intentEvent(session.key(), new DecisionTap.IntentEvent(
                DecisionTap.IntentEventKind.LEVEL_CHANGED, intent.tactic(), intent.movement(), intent.confidence(),
                intent.level(), ControlLevel.BASE, Optional.empty(), reason)));
    }

    /** Tells a counted cast outcome of the choice of an intent. */
    void cast(MobSession session, TacticalIntent intent, ExecutionOutcome outcome) {
        if (tap.detail() == TapDetail.FULL) {
            tap.castAttempted(session.key(), intent.tactic(), intent.choice().orElseThrow(), outcome);
        }
    }

    /** Tells a counted cast of a candidate that is not the choice of the intent (M19.5). */
    void readyCast(MobSession session, TacticalIntent intent, ExecutionOutcome outcome) {
        if (tap.detail() == TapDetail.FULL) {
            tap.castAttempted(session.key(), intent.tactic(), intent.choice().orElseThrow(), outcome, true);
        }
    }

    /** Sends a waiting stance line and any tell result that has closed (M23.10). */
    void posture(MobSession session, long tick) {
        PostureRecords.flush(session, tick, tap);
    }

    /** Tells that a zone guard held a cast. */
    void zoneGuard(MobKey mob, ZoneGuardKind kind) {
        tap.zoneGuard(mob, kind);
    }

    /**
     * Tells a motivated pattern cast. There is no source field (errata E26-6).
     *
     * @param mob the session
     * @param participant the enemy
     * @param exploit the exploitation
     * @param choice the skill choice
     * @param tactic the tactic
     * @param quotaUsed motivated casts in the window
     */
    void patternExploited(MobKey mob, UUID participant, PatternExploit exploit, String choice, String tactic,
                          int quotaUsed) {
        tap.patternExploited(mob, participant, exploit, choice, tactic, quotaUsed);
    }

    /**
     * Tells the {@code pattern_exploit} event of a motivated cast whose result has settled, once (E-10).
     *
     * @param session the caster
     */
    void flushExploit(MobSession session) {
        session.pendingExploit().flush(tap, session.key());
    }

    /** Tells a counted cast of one candidate choice. */
    void readyCast(MobSession session, TacticalIntent intent, akyrion.thepredict.domain.ChoiceId choice,
                   ExecutionOutcome outcome) {
        if (tap.detail() == TapDetail.FULL) {
            tap.castAttempted(session.key(), intent.tactic(), choice, outcome, true);
        }
    }
}
