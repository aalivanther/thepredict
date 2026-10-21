package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.learn.ExploreArm;
import akyrion.thepredict.domain.learn.ExplorePolicy;
import akyrion.thepredict.domain.learn.VoiArm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureGateQuestionTest {
    @Test
    void captureDropsAnExploreQuestionAndHoldsASkip() {
        var encounter = new Encounter(new EncounterId(28), new UUID(0, 1), 0);
        var key = new MobKey(new UUID(2, 1), 0);
        var session = new MobSession(key, EngineFixtures.profile("boss"));
        encounter.addMember(session);
        ExploreArm armed = null;
        for (var seed = 1L; seed < 5000L; seed++) {
            var drawn = ExplorePolicy.choose(seed, 4L, 0, 2, 0.10, 0, 0, 0.2, false, BehaviorPolicy.JEV_ARGMAX);
            if (drawn.inStratum()) {
                armed = new ExploreArm(seed, 0, 2, 0.10, 0, 0, 0.2, false, BehaviorPolicy.JEV_ARGMAX, false);
                break;
            }
        }
        session.learning().armExplore(armed);
        var plan = plan(key, session);
        assertTrue(CaptureGate.apply(encounter, plan, 4L).questions().isEmpty());
        session.learning().noteAcceptedJev();
        assertEquals(1, CaptureGate.apply(encounter, plan, 4L).questions().size());
        session.learning().armExplore(ExploreArm.NONE);
        var skip = new VoiArm(true, "on", true, 1.0, 0.01, 0.01, 80, 80, 0.90, 0.0, 0.02, 0, 2, false,
                new TacticId("press"), 4, 30L);
        session.learning().armVoi(skip);
        assertTrue(CaptureGate.apply(encounter, plan, 10L).questions().isEmpty());
        assertEquals(BehaviorPolicy.VOI_SKIP, session.learning().held().orElseThrow().behavior());
        assertEquals(new TacticId("press"), session.learning().held().orElseThrow().tactic());
        session.learning().release();
        session.learning().armVoi(new VoiArm(true, "shadow", true, 1.0, 0.01, 0.01, 80, 80, 0.90, 0.0, 0.02, 0, 2,
                false, new TacticId("press"), 4, 30L));
        assertFalse(CaptureGate.apply(encounter, plan, 10L).questions().isEmpty());
        assertTrue(session.learning().wouldSkip());
        assertTrue(session.learning().held().isEmpty());
    }

    private static EncounterQuestions.Plan plan(MobKey key, MobSession session) {
        var question = new QuestionSpec(QuestionKey.of(0), key, Situation.NOW,
                List.of(new TacticId("press"), new TacticId("keep")), Optional.of(new TacticId("press")));
        return new EncounterQuestions.Plan(List.of(question), Map.of(key, session.profile()), List.of());
    }
}
