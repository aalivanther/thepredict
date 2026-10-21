package akyrion.thepredict.engine;

import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSettings;
import akyrion.thepredict.domain.distill.DistillSource;
import akyrion.thepredict.domain.distill.OodCause;
import akyrion.thepredict.engine.distill.DistillControl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Disable drops the next capture. A mode command cannot pass the global or the verdict. */
class DistillControlCaptureTest {
    @Test
    void disableClearsTheNextCaptureAndAutonomousStaysAtAssist() {
        var autonomous = DistillSettings.DEFAULT.withMode(DistillMode.AUTONOMOUS).withJevForOod(false);
        var open = new DistillControl(DistillMode.AUTONOMOUS, DistillMode.AUTONOMOUS);
        var before = capture(autonomous.withMode(open.effective(autonomous.mode())));
        assertEquals(1, before.learning().delegated());
        open.disable();
        var after = capture(autonomous.withMode(open.effective(autonomous.mode())));
        assertEquals(0, after.learning().delegated());
        assertEquals(DistillMode.OFF, open.effective(autonomous.mode()));
        var capped = new DistillControl(DistillMode.AUTONOMOUS, DistillMode.ASSIST);
        assertEquals(DistillMode.ASSIST, capped.raise(DistillMode.AUTONOMOUS));
        var assist = DistillSettings.DEFAULT.withMode(DistillMode.ASSIST);
        var fixture = new CoordinatorFixture();
        fixture.dispatcher.useDistill(assist, akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        fixture.dispatcher.useControl(capped);
        assertEquals(DistillMode.ASSIST, fixture.dispatcher.distillForCapture().mode());
        capped.disable();
        assertEquals(DistillMode.OFF, fixture.dispatcher.distillForCapture().mode());
    }

    private static MobSession capture(DistillSettings settings) {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(1);
        var encounter = fixture.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(1),
                EngineFixtures.near());
        session.learning().armDistill(DistillMode.OFF, true, Optional.<OodCause>empty(), DistillSource.TABLE,
                "press", 2L, 10, false, false, "r1:ALL", "fine", 0, false);
        var options = List.of(new TacticId("press_attack"), new TacticId("hold_ground"));
        var question = new QuestionSpec(QuestionKey.of(0), session.key(), Situation.NOW, options,
                Optional.of(options.getFirst()));
        var plan = new EncounterQuestions.Plan(List.of(question), Map.of(session.key(), session.profile()), List.of());
        CaptureGate.apply(encounter, plan, 4L, null, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        return session;
    }
}
