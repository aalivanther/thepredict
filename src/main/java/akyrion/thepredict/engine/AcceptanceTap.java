package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentOutcome;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;

import java.util.Objects;
import java.util.Optional;

/**
 * What the acceptance tells the {@link DecisionTap} about each mob it offered to the intent policy (M18.4): the verdict
 * with the {@code NOW} question, adopted or renewed with the tactic, level and confidence of the intent, or rejected
 * with the best tactic of the answer and its fallback reason, and each contingency stored with an adopted or renewed
 * intent, with its own question. Package-private; server thread only; called only while something records.
 */
final class AcceptanceTap {
    private final DecisionTap tap;

    AcceptanceTap(DecisionTap tap) {
        this.tap = Objects.requireNonNull(tap, "tap");
    }

    /** Tells the tap the verdict about one mob with its question, and the contingencies stored with an intent. */
    void told(MobSession session, AnswerGroups.Group group, InFlightRequest request, IntentOutcome outcome) {
        var from = session.intent().map(TacticalIntent::level).orElse(ControlLevel.BASE);
        var question = AnswerGroups.questionOf(request, group.mob(), Situation.NOW);
        switch (outcome) {
            case IntentOutcome.Adopted adopted -> accepted(session, group, request, DecisionTap.IntentEventKind.ADOPTED,
                    adopted.intent(), from, question);
            case IntentOutcome.Renewed renewed -> accepted(session, group, request, DecisionTap.IntentEventKind.RENEWED,
                    renewed.intent(), from, question);
            case IntentOutcome.Rejected rejected -> {
                var best = group.now().best();
                tap.intentEvent(session.key(), new DecisionTap.IntentEvent(DecisionTap.IntentEventKind.REJECTED, best,
                        movementOf(session, best), group.now().confidence(), from, from, question,
                        Optional.of(rejected.reason())));
            }
        }
    }

    private void accepted(MobSession session, AnswerGroups.Group group, InFlightRequest request,
                          DecisionTap.IntentEventKind kind, TacticalIntent intent, ControlLevel from,
                          Optional<QuestionKey> question) {
        tap.intentEvent(session.key(), new DecisionTap.IntentEvent(kind, intent.tactic(), intent.movement(),
                intent.confidence(), from, intent.level(), question, Optional.empty()));
        intent.contingencies().forEach((situation, tactic) -> tap.intentEvent(session.key(),
                new DecisionTap.IntentEvent(DecisionTap.IntentEventKind.CONTINGENCY_STORED, tactic,
                        movementOf(session, tactic), group.contingencies().getOrDefault(situation, group.now())
                        .confidence(), from, intent.level(), AnswerGroups.questionOf(request, group.mob(), situation),
                        Optional.empty())));
    }

    private static Movement movementOf(MobSession session, TacticId tactic) {
        return session.profile().tactic(tactic).map(Tactic::movement).orElse(Movement.NONE);
    }
}
