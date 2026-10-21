package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.HeldIntent;
import akyrion.thepredict.domain.HeldIntentRevalidation;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiPolicyTest {
    @Test
    void shadowRecordsTheSkipAndAHeldIntentSurvivesCandidateZero() throws Exception {
        var decision = VoiPolicy.evaluate(true, "shadow", true, 1.0, 0.01, 0.01, 80, 80, 0.90, 0.0, 0.02, 0, 2,
                false);
        assertFalse(decision.skip());
        assertTrue(decision.wouldSkip());
        assertTrue(decision.cause().isEmpty());
        var live = VoiPolicy.evaluate(true, "on", true, 1.0, 0.01, 0.01, 80, 80, 0.90, 0.0, 0.02, 0, 2, false);
        assertTrue(live.skip());
        assertEquals(java.util.Optional.of(LearnCause.VOI_SKIP), live.cause());
        var held = new HeldIntent(new TacticId("press"), BehaviorPolicy.VOI_SKIP, 4, 10L, 40L);
        assertEquals(HeldIntentRevalidation.Result.KEEP,
                HeldIntentRevalidation.check(12L, held.expiresTick(), held.captureSignature(), 4, false));
        assertEquals(HeldIntentRevalidation.Result.SITUATION_CHANGED,
                HeldIntentRevalidation.check(12L, held.expiresTick(), held.captureSignature(), 4, true));
        assertEquals(HeldIntentRevalidation.Result.AGED,
                HeldIntentRevalidation.check(12L, held.expiresTick(), held.captureSignature(), 9, false));
        assertEquals(VoiDecision.ASK, VoiPolicy.evaluate(true, "off", true, 1.0, 0.0, 0.0, 80, 80, 0.90, 0.0, 0.02,
                0, 2, false));
        var arm = new VoiArm(true, "on", true, 1.0, 0.01, 0.01, 80, 80, 0.90, 0.0, 0.02, 0, 2, false,
                new TacticId("press"), 4, 30L);
        var skipped = CaptureDecision.decide(ExploreArm.NONE, arm, 10L);
        assertTrue(skipped.dropQuestions());
        assertEquals(BehaviorPolicy.VOI_SKIP, skipped.held().orElseThrow().behavior());
        assertEquals(new TacticId("press"), skipped.held().orElseThrow().tactic());
        var shadow = new VoiArm(true, "shadow", true, 1.0, 0.01, 0.01, 80, 80, 0.90, 0.0, 0.02, 0, 2, false,
                new TacticId("press"), 4, 30L);
        var asked = CaptureDecision.decide(ExploreArm.NONE, shadow, 10L);
        assertFalse(asked.dropQuestions());
        assertTrue(asked.held().isEmpty());
        assertTrue(asked.wouldSkip());
    }
}
