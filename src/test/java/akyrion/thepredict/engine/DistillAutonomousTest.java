package akyrion.thepredict.engine;

import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSettings;
import akyrion.thepredict.domain.distill.DistillSource;
import akyrion.thepredict.domain.distill.OodCause;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** With jevForOod false, an uncovered autonomous capture sends no question. */
class DistillAutonomousTest {
    @Test
    void anUncoveredCellDropsTheQuestionAndACoveredCellDelegates() {
        var settings = DistillSettings.DEFAULT.withMode(DistillMode.AUTONOMOUS).withJevForOod(false);
        var uncovered = capture(settings, false);
        assertTrue(uncovered.plan().questions().isEmpty());
        assertEquals(0, uncovered.session().learning().delegated());
        var covered = capture(settings, true);
        assertTrue(covered.plan().questions().isEmpty());
        assertEquals(1, covered.session().learning().delegated());
    }

    private static Held capture(DistillSettings settings, boolean covered) {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(1);
        var encounter = fixture.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(1),
                EngineFixtures.near());
        session.learning().armDistill(DistillMode.OFF, covered,
                covered ? Optional.empty() : Optional.of(OodCause.OOD_CELL_UNSEEN), DistillSource.TABLE, "alpha", 2L,
                10, false, false, "r1:ALL", "fine", 0, false);
        var options = List.of(new TacticId("alpha"), new TacticId("bravo"));
        var question = new QuestionSpec(QuestionKey.of(0), session.key(), Situation.NOW, options,
                Optional.of(options.getFirst()));
        var plan = new EncounterQuestions.Plan(List.of(question), Map.of(session.key(), session.profile()), List.of());
        var next = CaptureGate.apply(encounter, plan, 4L, null, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        return new Held(session, next);
    }

    private record Held(MobSession session, EncounterQuestions.Plan plan) {
    }
}
