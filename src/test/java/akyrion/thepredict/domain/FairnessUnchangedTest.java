package akyrion.thepredict.domain;

import akyrion.thepredict.adapter.jev.FairQuestion;
import akyrion.thepredict.domain.distill.DemotionCause;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.engine.distill.DistillGovernor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Fairness still drops straight to shadow. The question body does not gain a distill field. */
class FairnessUnchangedTest {
    @Test
    void assistAndAutonomousShareTheQuestionAndFairnessDoesNotRise() {
        var governor = new DistillGovernor();
        governor.fairness();
        assertEquals(DistillMode.SHADOW, governor.floor());
        assertEquals(DemotionCause.FAIRNESS, governor.last().orElseThrow());
        assertEquals(DistillMode.SHADOW, governor.effective(DistillMode.AUTONOMOUS, DistillMode.ASSIST));
        var profile = DomainFixtures.guardian();
        var options = List.of(new TacticId("press_attack"), new TacticId("hold_ground"));
        var body = FairQuestion.json(Situation.NOW, options, profile);
        assertEquals(body, FairQuestion.json(Situation.NOW, options, profile));
        assertFalse(body.contains("distill"));
        assertFalse(body.contains("autonomous"));
    }
}
