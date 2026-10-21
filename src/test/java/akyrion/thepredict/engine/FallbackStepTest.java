package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.domain.CandidateIntent;
import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.CandidateSet;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HeldIntent;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.LocalPlanner;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.engine.learn.LearnedLocalPick;
import akyrion.thepredict.engine.learn.ValueTableBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.STRIKE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stored candidate at index 0 is the local plan the chain executes. */
class FallbackStepTest {
    @Test
    void theChainExecutesTheLeadingCandidateRatherThanThePlanner() {
        var fixture = new ExecutionFixture();
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        assertEquals(ControlLevel.BASE, fixture.view(1).level());
        var session = fixture.session(1);
        session.recordInstall(new InstallResult.Installed(3), 1L);
        session.facts(facts(session));
        fixture.actions.cooldowns.set(STRIKE, 1000);
        fixture.actions.cooldowns.cast(session.key(), session.profile().choices().get(STRIKE));
        var leading = CandidateSet.of(1, 7, List.of(
                new CandidateIntent(PRESS, Movement.APPROACH, Optional.of(STRIKE), 100, 0),
                new CandidateIntent(KEEP, Movement.KEEP_DISTANCE, Optional.empty(), 900, 0)),
                Map.of(), List.of(PRESS, KEEP));
        session.candidates(leading);
        fixture.client.futures.getFirst().complete(new DecisionResult.Failed(FailureKind.RATE_LIMITED,
                Optional.of(Duration.ofSeconds(30)), "rate limited"));
        fixture.run(1);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertEquals(KEEP, fixture.view(1).tactic());
        assertEquals(PRESS, LocalPlanner.choose(session.profile(), Set.of(), Set.of(PRESS, KEEP)).orElseThrow().id());
        assertEquals(KEEP, session.candidates().orElseThrow().candidates().getFirst().tactic());
    }

    @Test
    void aHeldIntentReplacesTheCandidateLeadUntilItIsReleased() {
        var fixture = new ExecutionFixture();
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        var session = fixture.session(1);
        session.recordInstall(new InstallResult.Installed(3), 1L);
        session.facts(facts(session));
        var leading = CandidateSet.of(1, 7, List.of(
                new CandidateIntent(KEEP, Movement.KEEP_DISTANCE, Optional.empty(), 900, 0),
                new CandidateIntent(PRESS, Movement.APPROACH, Optional.of(STRIKE), 100, 0)),
                Map.of(), List.of(PRESS, KEEP));
        session.candidates(leading);
        session.learning().hold(new HeldIntent(PRESS, BehaviorPolicy.VOI_SKIP, session.liveSignature(), 0L, 1_000_000L));
        fixture.client.futures.getFirst().complete(new DecisionResult.Failed(FailureKind.RATE_LIMITED,
                Optional.of(Duration.ofSeconds(30)), "rate limited"));
        fixture.run(1);
        assertEquals(PRESS, fixture.view(1).tactic());
    }

    @Test
    void learnedScoringWithAnActiveTableBeatsTheLocalPlanner() {
        var fixture = new ExecutionFixture();
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        var session = fixture.session(1);
        session.recordInstall(new InstallResult.Installed(3), 1L);
        session.facts(facts(session));
        fixture.actions.cooldowns.set(STRIKE, 1000);
        fixture.actions.cooldowns.cast(session.key(), session.profile().choices().get(STRIKE));
        session.candidateScoring(CandidateScoring.LEARNED);
        var keep = LearnedLocalPick.tacticCode(KEEP.value());
        var press = LearnedLocalPick.tacticCode(PRESS.value());
        assertEquals(false, keep == press);
        var builder = new ValueTableBuilder();
        builder.add(1, keep, 3, 7L, 1.0, 0.01, 40, 1.0);
        builder.add(1, press, 3, 7L, 0.0, 0.01, 40, 1.0);
        var table = builder.build();
        session.learning().stage(table, akyrion.thepredict.engine.learn.LearnedArtifacts.State.ACTIVE, "PASS", "all",
                1, 7L);
        session.learning().activate(table, 1, 7L);
        fixture.client.futures.getFirst().complete(new DecisionResult.Failed(FailureKind.RATE_LIMITED,
                Optional.of(Duration.ofSeconds(30)), "rate limited"));
        fixture.run(1);
        assertEquals(PRESS, LocalPlanner.choose(session.profile(), Set.of(), Set.of(PRESS, KEEP)).orElseThrow().id());
        assertEquals(KEEP, fixture.view(1).tactic());
    }

    @Test
    void aHeldSkipExpiresOrLosesItsSituation() {
        var expired = held(new HeldIntent(PRESS, BehaviorPolicy.VOI_SKIP, 0, 0L, 0L), true);
        assertEquals(java.util.Optional.of(LearnCause.VOI_SKIP_EXPIRED), expired.learning().skipCause());
        var changed = held(new HeldIntent(ExecutionFixture.FALL_BACK, BehaviorPolicy.VOI_SKIP, 0, 0L, 1_000_000L),
                false);
        assertEquals(java.util.Optional.of(LearnCause.VOI_SKIP_SITUATION_CHANGED), changed.learning().skipCause());
        assertTrue(changed.learning().held().isEmpty());
    }

    @Test
    void aMobWhoseAllowedSkillsAllCoolDownWaitsWithItsTacticInsteadOfTheBaseAi() {
        var fixture = cooling(Set.of(PRESS));
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertEquals(PRESS, fixture.view(1).tactic());
    }

    @Test
    void aMobWithNoTacticEvenWhenReadyStillGoesToTheBaseAi() {
        var fixture = cooling(Set.of());
        assertEquals(ControlLevel.BASE, fixture.view(1).level());
    }

    private static ExecutionFixture cooling(Set<akyrion.thepredict.domain.TacticId> whenReady) {
        var fixture = new ExecutionFixture();
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        var session = fixture.session(1);
        session.recordInstall(new InstallResult.Installed(3), 1L);
        fixture.reader.eligible.put(EngineFixtures.id(1), whenReady);
        fixture.captureCooldowns();
        fixture.actions.cooldowns.set(STRIKE, 1000);
        fixture.actions.cooldowns.cast(session.key(), session.profile().choices().get(STRIKE));
        var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.PLAYER, 1), DistanceBand.RANGED,
                Trend.STEADY, true, true, true, false, false);
        session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(), Set.of(),
                Set.of(), Set.of(), Map.of(), whenReady));
        fixture.client.futures.getFirst().complete(new DecisionResult.Failed(FailureKind.RATE_LIMITED,
                Optional.of(Duration.ofSeconds(30)), "rate limited"));
        fixture.run(1);
        return fixture;
    }

    private static MobSession held(HeldIntent intent, boolean matchSignature) {
        var fixture = new ExecutionFixture();
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        var session = fixture.session(1);
        session.recordInstall(new InstallResult.Installed(3), 1L);
        session.facts(facts(session));
        var signature = matchSignature ? session.liveSignature() : intent.captureSignature();
        session.learning().hold(new HeldIntent(intent.tactic(), intent.behavior(), signature, intent.adoptedTick(),
                intent.expiresTick()));
        fixture.client.futures.getFirst().complete(new DecisionResult.Failed(FailureKind.RATE_LIMITED,
                Optional.of(Duration.ofSeconds(30)), "rate limited"));
        fixture.run(1);
        return session;
    }

    private static MobFacts facts(MobSession session) {
        var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.PLAYER, 1), DistanceBand.RANGED,
                Trend.STEADY, true, true, true, false, false);
        return new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(), Set.of(PRESS, KEEP),
                Set.of(), Set.of());
    }
}
