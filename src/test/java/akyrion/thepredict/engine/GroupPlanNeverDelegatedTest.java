package akyrion.thepredict.engine;

import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSettings;
import akyrion.thepredict.domain.distill.DistillSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A contingency is never delegated. The same cell with only a now-question can be. */
class GroupPlanNeverDelegatedTest {
    @Test
    void aContingencyStaysWithJevAfterTheFirstCapture() {
        var assist = DistillSettings.DEFAULT.withMode(DistillMode.ASSIST);
        var held = capture(Situation.IF_TARGET_IN_MELEE, assist);
        assertEquals(0, held.learning().delegated());
        assertFalse(held.learning().captureDelegated());
        var now = capture(Situation.NOW, assist);
        assertEquals(1, now.learning().delegated());
    }

    private static MobSession capture(Situation situation, DistillSettings settings) {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(1);
        var encounter = fixture.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(1),
                EngineFixtures.near());
        session.learning().armDistill(DistillMode.OFF, true, Optional.empty(), DistillSource.TABLE, "alpha", 2L, 10,
                false, false, "r1:ALL", "fine", 0, false);
        var options = List.of(new TacticId("alpha"), new TacticId("bravo"));
        var plan = plan(session, situation, options);
        CaptureGate.apply(encounter, plan, 4L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        CaptureGate.apply(encounter, plan, 8L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        return session;
    }

    private static EncounterQuestions.Plan plan(MobSession session, Situation situation, List<TacticId> options) {
        var question = new QuestionSpec(QuestionKey.of(0), session.key(), situation, options, Optional.of(options.getFirst()));
        return new EncounterQuestions.Plan(List.of(question), Map.of(session.key(), session.profile()), List.of());
    }
}
