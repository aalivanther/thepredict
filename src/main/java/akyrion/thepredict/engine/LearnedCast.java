package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.engine.learn.LearnedResult;

/**
 * The first damaging cast of a learned exploit (M28.7). A miss stays {@code neutral}. The counter mean is not
 * touched here. The in-fight weight that the result moves is the one of the tactic the mob was running, by its id
 * (LD-16, entry K of learn-distill), so it follows that tactic wherever it sits in the next eligible list.
 */
final class LearnedCast {
    private LearnedCast() {
    }

    /**
     * Settles a pending learned exploit when the cast is damaging.
     *
     * @param session the session
     * @param choice the choice that was attempted
     * @param outcome the attempt
     */
    static void settle(MobSession session, SkillChoice choice, ExecutionOutcome outcome) {
        var learning = session.learning();
        if (!learning.learnedPending() || choice.purpose().isEmpty() || !choice.purpose().get().damaging()) {
            return;
        }
        var result = result(outcome);
        if (result == LearnedResult.NEUTRAL) {
            return;
        }
        learning.settleLearned(result);
        var effect = result == LearnedResult.LANDED ? 1.0 : -1.0;
        var tactic = session.intent().map(TacticalIntent::tactic);
        if (tactic.isPresent()) {
            learning.observeWeight(session.entropyActive(), session.candidateScoring() == CandidateScoring.LEARNED,
                    tactic.get().value(), effect, 0.0);
        }
    }

    /** The result an attempt settles, or {@code NEUTRAL} for an attempt that settles nothing (EFO-24). */
    private static LearnedResult result(ExecutionOutcome outcome) {
        return switch (outcome) {
            case CAST -> LearnedResult.LANDED;
            case SKIPPED_SHIELD -> LearnedResult.BLOCKED;
            case REJECTED_BY_MYTHICMOBS -> LearnedResult.AVOIDED;
            default -> LearnedResult.NEUTRAL;
        };
    }
}
