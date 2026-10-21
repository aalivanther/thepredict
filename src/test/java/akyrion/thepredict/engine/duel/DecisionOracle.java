package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;

/**
 * The option a perfect judge would pick, from the world, never from the engine's own score (§7.5 of the simulator
 * audit, SIM-12).
 */
interface DecisionOracle {
    /**
     * The best option of a question.
     *
     * @param snapshot a copy of the world when the request was sent, which the oracle may play forward
     * @param rival the classic duelist of the other side, as it plays now
     * @param question the question, with its options
     * @param profile the profile of the asking mob
     * @param index tick of the round, which the rival's timers and the goal selection follow
     * @param tick tick of the clock, which the effects in flight follow
     * @return one of the options
     */
    TacticId best(DuelWorld snapshot, ClassicDuelist rival, QuestionSpec question, TacticalProfile profile, int index,
                  long tick);
}
