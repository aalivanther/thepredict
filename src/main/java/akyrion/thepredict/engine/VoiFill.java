package akyrion.thepredict.engine;

import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.learn.ValueEstimate;
import akyrion.thepredict.engine.learn.LearnedArtifacts;
import akyrion.thepredict.engine.learn.LearnedLocalPick;

import java.util.List;

/**
 * Fills the value-of-information cell from the active table when a capture is armed (M28.8). Shadow still asks. The
 * agreement of the cell sums the questions and agreements of every tactic it holds, not only those of the best one.
 * The table is read with the same key as the other readers of the learned table ({@code LearnedOrder},
 * {@code FallbackStep}, {@code DecisionDispatcher.hint}), so all of them change to the lookup by name together.
 */
final class VoiFill {
    private VoiFill() {
    }

    /**
     * One filled cell.
     *
     * @param pass artifact verdict allows the scope
     * @param usable the best cell has enough windows
     * @param gap value gap
     * @param sdBest deviation of the best
     * @param sdSecond deviation of the second
     * @param seen questions in the cell
     * @param agree agreements in the cell
     * @param delta disagreement delta
     * @param forbidden first capture, signature change, contingency or g0
     * @param tactic best tactic, or the first option
     */
    record Prepared(boolean pass, boolean usable, double gap, double sdBest, double sdSecond, int seen, int agree,
                    double delta, boolean forbidden, TacticId tactic) {
    }

    /**
     * Reads the active table and stores the cell on the session.
     *
     * @param session the session
     * @param encounter the encounter
     * @param plan the planned questions
     * @param settings learning section
     * @param options {@code NOW} options
     * @return the cell the arm publishes
     */
    static Prepared prepare(MobSession session, Encounter encounter, EncounterQuestions.Plan plan,
                            LearningSettings settings, List<TacticId> options) {
        var learning = session.learning();
        var ranked = rank(session, options, settings.voiMinWindows());
        var usable = ranked.pass() && ranked.best().nEff() >= settings.voiMinWindows();
        var gap = usable && !ranked.second().none()
                ? (ranked.best().valueMillis() - ranked.second().valueMillis()) / 1000.0 : 0.0;
        var forbidden = forbidden(session, encounter, plan);
        learning.installVoi(usable, gap, ranked.best().sdMillis() / 1000.0, ranked.second().sdMillis() / 1000.0,
                ranked.seen(), ranked.agree(), ranked.best().deltaMillis() / 1000.0, forbidden);
        return new Prepared(ranked.pass(), usable, gap, ranked.best().sdMillis() / 1000.0,
                ranked.second().sdMillis() / 1000.0, ranked.seen(), ranked.agree(),
                ranked.best().deltaMillis() / 1000.0, forbidden, ranked.tactic());
    }

    /**
     * The learned leader of a capture: the option of highest value in the live cell of the active table, ranked as
     * {@link #prepare} ranks it, or empty without an active table or without any estimate. The closing window of the
     * answer compares its tactic with it ({@code jev_agree}, M28.2).
     *
     * @param session the session
     * @param options {@code NOW} options
     * @param minimum usable windows of a table read by code
     * @return the tactic id, or empty
     */
    static String leader(MobSession session, List<TacticId> options, int minimum) {
        var ranked = rank(session, options, minimum);
        return ranked.pass() && !ranked.best().none() ? ranked.tactic().value() : "";
    }

    private static Rank rank(MobSession session, List<TacticId> options, int minimum) {
        var learning = session.learning();
        var pass = learning.artifactState() == LearnedArtifacts.State.ACTIVE && learning.tableActive();
        var best = ValueEstimate.NONE;
        var second = ValueEstimate.NONE;
        var tactic = options.isEmpty() ? new TacticId("none") : options.getFirst();
        if (!pass) {
            return new Rank(false, best, second, tactic, 0, 0);
        }
        var seen = 0;
        var agree = 0;
        for (var option : options) {
            var estimate = CellLookup.estimate(session, option.value(), minimum);
            if (estimate.none()) {
                continue;
            }
            seen += estimate.jevSeen();
            agree += estimate.jevAgree();
            if (best.none() || estimate.valueMillis() > best.valueMillis()) {
                second = best;
                best = estimate;
                tactic = option;
            } else if (second.none() || estimate.valueMillis() > second.valueMillis()) {
                second = estimate;
            }
        }
        return new Rank(true, best, second, tactic, seen, agree);
    }

    private static boolean forbidden(MobSession session, Encounter encounter, EncounterQuestions.Plan plan) {
        var learning = session.learning();
        if (learning.captures() == 0 || learning.signatureMoved(session.liveSignature())) {
            return true;
        }
        for (var question : plan.questions()) {
            if (question.mob().equals(session.key()) && question.situation() != Situation.NOW) {
                return true;
            }
        }
        return encounter.memberCount() >= 2 && encounter.groupMode(GroupMode.OFF) != GroupMode.OFF;
    }

    private record Rank(boolean pass, ValueEstimate best, ValueEstimate second, TacticId tactic, int seen,
                        int agree) {
    }
}
