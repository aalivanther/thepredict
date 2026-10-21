package akyrion.thepredict.engine;

import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSettings;
import akyrion.thepredict.domain.distill.DistillSource;
import akyrion.thepredict.engine.distill.DistillGovernor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The effective mode is the minimum of configured, verdict and floor. Bench does not act on a player. */
class DistilledArtifactsTest {
    @Test
    void theMinimumOfThreeHoldsAndBenchWithholdsAPlayer() {
        var governor = new DistillGovernor();
        governor.outcome();
        assertEquals(DistillMode.ASSIST, governor.effective(DistillMode.AUTONOMOUS, DistillMode.AUTONOMOUS));
        assertEquals(DistillMode.SHADOW, governor.effective(DistillMode.ASSIST, DistillMode.SHADOW));
        var bench = DistillSettings.DEFAULT.withMode(DistillMode.ASSIST);
        assertEquals(0, delegated(bench, true));
        assertEquals(1, delegated(bench, false));
    }

    private static int delegated(DistillSettings settings, boolean player) {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(1);
        var encounter = fixture.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(1),
                EngineFixtures.near());
        session.playerTarget(player);
        session.learning().armDistill(DistillMode.OFF, true, Optional.empty(), DistillSource.TABLE, "alpha", 2L, 10,
                false, false, "r1:ALL", "fine", 0, false);
        var options = List.of(new TacticId("alpha"), new TacticId("bravo"));
        var question = new QuestionSpec(QuestionKey.of(0), session.key(), Situation.NOW, options,
                Optional.of(options.getFirst()));
        var plan = new EncounterQuestions.Plan(List.of(question), Map.of(session.key(), session.profile()), List.of());
        CaptureGate.apply(encounter, plan, 4L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        CaptureGate.apply(encounter, plan, 8L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        return session.learning().delegated();
    }
}
