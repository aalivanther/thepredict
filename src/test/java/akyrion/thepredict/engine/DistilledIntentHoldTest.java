package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HeldIntent;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.HeldIntentRevalidation;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.distill.DistillHold;
import akyrion.thepredict.domain.learn.CaptureDecision;
import akyrion.thepredict.domain.learn.ExploreArm;
import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.domain.learn.VoiArm;
import akyrion.thepredict.engine.learn.LearnedLocalPick;
import akyrion.thepredict.engine.learn.ValueTableBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A distilled hold wins over a value-of-information skip and is not replaced by the local order. */
class DistilledIntentHoldTest {
    @Test
    void aDistilledHoldWinsAndTheSameArmStillSkips() throws Exception {
        var arm = new VoiArm(true, "on", true, 1.0, 0.01, 0.01, 80, 80, 0.90, 0.0, 0.02, 0, 2, false,
                ExecutionFixture.PRESS, 4, 30L);
        var hold = DistillHold.delegated("press", 4, 10L, 30, "DISTILLED_CELL");
        var delegated = CaptureDecision.decide(ExploreArm.NONE, arm, 10L, hold);
        assertTrue(delegated.dropQuestions());
        assertEquals(BehaviorPolicy.DISTILLED, delegated.held().orElseThrow().behavior());
        assertEquals(new akyrion.thepredict.domain.TacticId("press"), delegated.held().orElseThrow().tactic());
        var skipped = CaptureDecision.decide(ExploreArm.NONE, arm, 10L, DistillHold.NONE);
        assertEquals(BehaviorPolicy.VOI_SKIP, skipped.held().orElseThrow().behavior());
        var method = FallbackStep.class.getDeclaredMethod("heldCause", BehaviorPolicy.class, boolean.class);
        method.setAccessible(true);
        assertEquals(LearnCause.DISTILLED_SITUATION_CHANGED, method.invoke(null, BehaviorPolicy.DISTILLED, true));
        assertEquals(LearnCause.DISTILLED_EXPIRED, method.invoke(null, BehaviorPolicy.DISTILLED, false));
        assertEquals(LearnCause.VOI_SKIP_SITUATION_CHANGED, method.invoke(null, BehaviorPolicy.VOI_SKIP, true));
        assertEquals(LearnCause.VOI_SKIP_EXPIRED, method.invoke(null, BehaviorPolicy.VOI_SKIP, false));
        var intent = new HeldIntent(ExecutionFixture.PRESS, BehaviorPolicy.DISTILLED, 4, 10L, 40L);
        assertEquals(HeldIntentRevalidation.Result.SITUATION_CHANGED,
                HeldIntentRevalidation.check(12L, intent.expiresTick(), intent.captureSignature(), 4, true));
        assertEquals(HeldIntentRevalidation.Result.EXPIRED,
                HeldIntentRevalidation.check(41L, intent.expiresTick(), intent.captureSignature(), 4, false));
        assertEquals(HeldIntentRevalidation.Result.KEEP,
                HeldIntentRevalidation.check(12L, intent.expiresTick(), intent.captureSignature(), 4, false));
    }

    @Test
    void fallbackKeepsTheDistilledTacticWhenTheTablePrefersAnother() {
        var changed = held(new HeldIntent(ExecutionFixture.FALL_BACK, BehaviorPolicy.DISTILLED, 0, 0L, 1_000_000L),
                false);
        assertEquals(Optional.of(LearnCause.DISTILLED_SITUATION_CHANGED), changed.session().learning().skipCause());
        assertTrue(changed.session().learning().held().isEmpty());
        var expired = held(new HeldIntent(ExecutionFixture.KEEP, BehaviorPolicy.DISTILLED, 0, 0L, 0L), true);
        assertEquals(Optional.of(LearnCause.DISTILLED_EXPIRED), expired.session().learning().skipCause());
        var kept = held(new HeldIntent(ExecutionFixture.KEEP, BehaviorPolicy.DISTILLED, 0, 0L, 1_000_000L), true);
        assertEquals(ExecutionFixture.KEEP, kept.session().learning().held().orElseThrow().tactic());
        assertEquals(ExecutionFixture.KEEP, kept.view().tactic());
    }

    private record HeldRun(MobSession session, IntentView view) {
    }

    private static HeldRun held(HeldIntent intent, boolean matchSignature) {
        var fixture = new ExecutionFixture();
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        var session = fixture.session(1);
        session.recordInstall(new InstallResult.Installed(3), 1L);
        session.facts(facts(session));
        session.candidateScoring(CandidateScoring.LEARNED);
        var builder = new ValueTableBuilder();
        builder.add(1, LearnedLocalPick.tacticCode(ExecutionFixture.PRESS.value()), 3, session.learning().contextFine(),
                1.0, 0.01, 40, 1.0);
        builder.add(1, LearnedLocalPick.tacticCode(ExecutionFixture.KEEP.value()), 3, session.learning().contextFine(),
                0.0, 0.01, 40, 1.0);
        session.learning().activate(builder.build(), 1, session.learning().contextFine());
        var signature = matchSignature ? session.liveSignature() : intent.captureSignature();
        session.learning().hold(new HeldIntent(intent.tactic(), intent.behavior(), signature, intent.adoptedTick(),
                intent.expiresTick()));
        fixture.client.futures.getFirst().complete(new DecisionResult.Failed(FailureKind.RATE_LIMITED,
                Optional.of(Duration.ofSeconds(30)), "rate limited"));
        fixture.run(1);
        return new HeldRun(session, fixture.view(1));
    }

    private static MobFacts facts(MobSession session) {
        var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.PLAYER, 1), DistanceBand.RANGED,
                Trend.STEADY, true, true, true, false, false);
        return new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(),
                Set.of(ExecutionFixture.PRESS, ExecutionFixture.KEEP), Set.of(), Set.of());
    }
}
