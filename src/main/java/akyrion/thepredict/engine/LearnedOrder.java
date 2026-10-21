package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CandidatePolicy;
import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.learn.LearnedLocalPick;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reorders the options Jev sees by learned value (M28.7). The pattern mask and the in-fight weights are not read.
 * An inactive table leaves the plan unchanged.
 */
final class LearnedOrder {
    private LearnedOrder() {
    }

    /**
     * Applies the learned order to every question whose session has an active table.
     *
     * @param encounter the encounter
     * @param plan the planned questions
     * @return the plan Jev sees
     */
    static EncounterQuestions.Plan apply(Encounter encounter, EncounterQuestions.Plan plan) {
        var questions = new ArrayList<QuestionSpec>();
        var changed = false;
        for (var question : plan.questions()) {
            var next = reorder(session(encounter, question), question);
            changed |= next != question;
            questions.add(next);
        }
        if (!changed) {
            return plan;
        }
        return new EncounterQuestions.Plan(questions, plan.profiles(), plan.fitChecks(), plan.prunes());
    }

    private static MobSession session(Encounter encounter, QuestionSpec question) {
        for (var member : encounter.memberSessions()) {
            if (member.key().equals(question.mob())) {
                return member;
            }
        }
        return null;
    }

    private static QuestionSpec reorder(MobSession session, QuestionSpec question) {
        if (session == null || session.candidateScoring() != CandidateScoring.LEARNED
                || !session.learning().tableActive()) {
            return question;
        }
        var tactics = tactics(session, question.options());
        if (tactics.size() != question.options().size()) {
            return question;
        }
        var weights = new double[tactics.size()];
        java.util.Arrays.fill(weights, 1.0);
        var scores = CellLookup.scores(session, tactics, 20, 20, weights);
        var order = CandidatePolicy.learnedOrder(tactics, scores);
        if (order.equals(question.options())) {
            return question;
        }
        return question.withOptions(order);
    }

    private static List<Tactic> tactics(MobSession session, List<TacticId> options) {
        var tactics = new ArrayList<Tactic>();
        for (var option : options) {
            var tactic = session.profile().tactic(option);
            if (tactic.isEmpty()) {
                return List.of();
            }
            tactics.add(tactic.get());
        }
        return tactics;
    }
}
