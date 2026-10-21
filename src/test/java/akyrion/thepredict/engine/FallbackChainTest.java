package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.TargetCue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.6: the four levels of the fallback chain, none of which waits for the network. */
class FallbackChainTest {
    private ExecutionFixture fixture = new ExecutionFixture();

    private void keepRange(int request) {
        fixture.answer(request, nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
    }

    private static DecisionResult.Failed rateLimited(Duration retryAfter) {
        return new DecisionResult.Failed(FailureKind.RATE_LIMITED, Optional.of(retryAfter), "rate limited");
    }

    @Test
    void aRateLimitMovesASessionWithoutAValidJevIntentToLocalInTheSameTick() {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        assertEquals(ControlLevel.BASE, fixture.view(1).level());
        fixture.client.futures.getFirst().complete(rateLimited(Duration.ofSeconds(30)));
        fixture.run(1);
        assertEquals(ServiceState.PAUSED, fixture.tracker.state());
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertEquals(Optional.of(FallbackReason.SERVICE_PAUSED), fixture.session(1).lastFallbackReason());
        assertEquals(1, fixture.count(FallbackReason.SERVICE_PAUSED));
    }

    @Test
    void aRateLimitKeepsAValidJevIntentUntilItExpiresAndThenGoesLocalWithinTwoTicks() {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        keepRange(0);
        fixture.run(1);
        var expiresAt = fixture.view(1).expiresAtNanos();
        fixture.runUntilRequest(2);
        fixture.client.futures.get(1).complete(rateLimited(Duration.ofSeconds(30)));
        fixture.run(1);
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        while (fixture.clock.nanos < expiresAt) {
            fixture.run(1);
        }
        fixture.run(2);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertEquals(Optional.of(FallbackReason.SERVICE_PAUSED), fixture.session(1).lastFallbackReason());
    }

    @Test
    void theFirstAcceptedAnswerAfterThePauseReturnsTheSessionToJev() {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.client.futures.getFirst().complete(rateLimited(Duration.ofSeconds(1)));
        fixture.run(1);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        fixture.runAnswering(200, nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        assertEquals(KEEP, fixture.view(1).tactic());
        assertEquals(ServiceState.READY, fixture.tracker.state());
    }

    @Test
    void anExhaustedBudgetKeepsTheJevIntentOnlyUntilItExpiresAndThenGoesLocal() {
        fixture = new ExecutionFixture(new ExecutionSettings(1_000_000_000L, 3, ExecutionFixture.MODEL, false),
                ExecutionFixture.settings(1));
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        keepRange(0);
        fixture.run(60);
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        assertEquals(1, fixture.client.requests.size());
        fixture.run(50);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertEquals(Optional.of(FallbackReason.NO_BUDGET), fixture.session(1).lastFallbackReason());
    }

    @Test
    void anExceptionWhileHandlingOneSessionDropsOnlyThatSessionToBase() {
        engageTogether();
        fixture.actions.failing.add(EngineFixtures.id(2));
        fixture.answer(0, nowAndContingency(prefer(PRESS, 0.9, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        assertEquals(PRESS, fixture.view(1).tactic());
        assertEquals(IntentView.base(0), fixture.view(2));
        assertEquals(Optional.of(FallbackReason.INTERNAL_ERROR), fixture.session(2).lastFallbackReason());
        assertTrue(fixture.metrics.total(DecisionMetrics.Counter.SESSION_FAILURES) >= 1);
    }

    @Test
    void aFailingProbeInTheReactiveCycleDropsOnlyThatSessionToBase() {
        engageTogether();
        fixture.answer(0, nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        fixture.reader.failing.add(EngineFixtures.id(2));
        fixture.run(2);
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        assertEquals(IntentView.base(0), fixture.view(2));
        assertEquals(Optional.of(FallbackReason.INTERNAL_ERROR), fixture.session(2).lastFallbackReason());
        assertEquals(1, fixture.count(FallbackReason.INTERNAL_ERROR), "the coordinator counts the cause as M10 does");
    }

    @Test
    void anInvalidSessionLosesItsControlAndItsAnswers() {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        keepRange(0);
        fixture.run(1);
        fixture.sessions.registry.advanceGeneration(EngineFixtures.id(1));
        assertEquals(IntentView.base(1), fixture.view(1));
        fixture.sessions.registry.close(EngineFixtures.id(1), FallbackReason.SESSION_INVALID);
        assertEquals(IntentView.ABSENT, fixture.view(1));
        var calls = fixture.actions.calls.size();
        fixture.run(40);
        assertEquals(calls, fixture.actions.calls.size());
    }

    @Test
    void withoutJevTheLocalPlannerControlsAtZeroCostAndPlansAgainWhenItsShortIntentExpires() {
        fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.open(1);
        fixture.target(1, 1);
        fixture.run(5);
        var view = fixture.view(1);
        assertEquals(ControlLevel.LOCAL, view.level());
        assertEquals(PRESS, view.tactic());
        assertEquals(0.0f, view.confidence());
        assertEquals(Optional.of(FallbackReason.SERVICE_BLOCKED), fixture.session(1).lastFallbackReason());
        assertEquals(1, fixture.actions.casts(1));
        fixture.run(40);
        assertTrue(fixture.actions.casts(1) >= 2, "each short local intent is a new plan");
        assertEquals(List.of(PRESS.value()), fixture.actions.signals(1), "planning the same tactic again never signals");
        assertTrue(fixture.client.requests.isEmpty());
        assertEquals(0, fixture.client.checks);
    }

    @Test
    void theLocalPlannerBreaksTheGuardOfABlockingTargetWithTheDeclaredBestWhen() {
        fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.actions.cooldowns.setDefault(0);
        fixture.open(1, ExecutionFixture.shieldProfile());
        fixture.target(1, 1);
        fixture.reader.distances.put(EngineFixtures.id(1), DistanceBand.MELEE);
        fixture.reader.eligible.put(EngineFixtures.id(1), Set.of(ExecutionFixture.SHIELD_PRESS,
                ExecutionFixture.BREAK_GUARD));
        fixture.run(5);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertEquals(ExecutionFixture.SHIELD_PRESS, fixture.view(1).tactic(), "a tie goes to the declaration order");
        fixture.reader.cues.put(EngineFixtures.player(1), Set.of(TargetCue.BLOCKING));
        fixture.run(45);
        assertEquals(ExecutionFixture.BREAK_GUARD, fixture.view(1).tactic(), "with both skills ready, BestWhen decides");
        fixture.reader.cues.remove(EngineFixtures.player(1));
        fixture.run(45);
        assertEquals(ExecutionFixture.SHIELD_PRESS, fixture.view(1).tactic());
    }

    @Test
    void theBaseAiTakesOverWhenNoLocalTacticIsLeft() {
        fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.open(1);
        fixture.target(1, 1);
        fixture.run(5);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        fixture.reader.eligible.put(EngineFixtures.id(1), Set.of());
        fixture.run(40);
        assertEquals(IntentView.base(0), fixture.view(1));
        assertEquals(Optional.of(FallbackReason.SERVICE_BLOCKED), fixture.session(1).lastFallbackReason());
    }

    @Test
    void aLocalTacticThatIsNoLongerEligibleIsPlannedAgainAfterTheNextCapture() {
        fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.open(1);
        fixture.target(1, 1);
        fixture.run(5);
        assertEquals(PRESS, fixture.view(1).tactic());
        fixture.reader.eligible.put(EngineFixtures.id(1), Set.of(KEEP, FALL_BACK));
        fixture.run(25);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertEquals(KEEP, fixture.view(1).tactic());
        assertEquals(List.of(PRESS.value(), KEEP.value()), fixture.actions.signals(1));
    }

    @Test
    void aMobWithoutAPerceivedTargetKeepsItsBaseAi() {
        fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.open(1);
        fixture.target(1, 1);
        fixture.reader.hiddenTargets.add(EngineFixtures.id(1));
        fixture.run(40);
        assertEquals(IntentView.base(0), fixture.view(1));
        assertTrue(fixture.actions.calls.isEmpty());
        fixture.reader.targets.remove(EngineFixtures.id(1));
        fixture.run(40);
        assertEquals(IntentView.base(0), fixture.view(1));
    }

    @Test
    void aLostTargetGivesControlBackToTheBaseAi() {
        fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.open(1);
        fixture.target(1, 1);
        fixture.run(5);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        fixture.reader.targets.remove(EngineFixtures.id(1));
        fixture.run(3);
        assertEquals(IntentView.base(0), fixture.view(1));
        assertFalse(fixture.view(1).controls(fixture.clock.nanos));
    }

    @Test
    void aTimeoutCreatesNoIntentAndIsNeverReportedTwiceToTheService() {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.run(40);
        assertEquals(ServiceState.READY, fixture.tracker.state());
        assertEquals(1, fixture.metrics.count(FailureKind.TIMEOUT));
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level(), "the wait for the first answer stays local");
        assertEquals(0, fixture.count(DiscardReason.STALE_SEQUENCE), "the cancelled future is not a stale discard");
    }

    @Test
    void anIntentThatExpiresWhileTheNextRequestIsInFlightWaitsLocallyAndTheNextAnswerReturnsToJev() {
        fixture.ready();
        fixture.open(1, ExecutionFixture.profile(new DecisionTiming(20, 20, 20, 0.5, 0.2, 1), 0));
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        keepRange(0);
        fixture.run(1);
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        fixture.runUntilRequest(2);
        var levels = new ArrayList<ControlLevel>();
        for (var tick = 0; tick < 12; tick++) {
            fixture.run(1);
            levels.add(fixture.view(1).level());
        }
        assertTrue(fixture.inFlight.size() == 1, "the second request is still in flight");
        assertFalse(levels.contains(ControlLevel.BASE), levels.toString());
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertEquals(Optional.of(FallbackReason.EXPIRED), fixture.session(1).lastFallbackReason());
        keepRange(1);
        fixture.run(1);
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
    }

    @Test
    void betweenTwoHeartbeatsAnExpiredIntentWaitsLocallyInsteadOfFlickeringToTheBaseAi() {
        fixture.ready();
        fixture.open(1, ExecutionFixture.profile(new DecisionTiming(40, 20, 20, 0.5, 0.2, 1), 0));
        fixture.target(1, 1);
        var levels = new ArrayList<ControlLevel>();
        fixture.runUntilRequest(1);
        keepRange(0);
        for (var tick = 0; tick < 120; tick++) {
            for (var request = 0; request < fixture.client.futures.size(); request++) {
                if (!fixture.client.futures.get(request).isDone()) {
                    keepRange(request);
                }
            }
            fixture.run(1);
            levels.add(fixture.view(1).level());
        }
        assertFalse(levels.subList(1, levels.size()).contains(ControlLevel.BASE), levels.toString());
        assertTrue(levels.contains(ControlLevel.LOCAL) && levels.contains(ControlLevel.JEV), levels.toString());
    }

    @Test
    void aLocalMobKeepsItsLogWellUnderTheLimitWhileItWaitsOrCastsAgain() {
        fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.open(1);
        fixture.target(1, 1);
        for (var index = 0; index < 300; index++) {
            fixture.actions.script(1, ExecutionOutcome.NOT_ELIGIBLE);
        }
        fixture.run(400);
        assertTrue(fixture.log.size() <= 5, fixture.log.toString());
        assertEquals(1, fixture.log.stream().filter(line -> line.contains("NOT_ELIGIBLE")).count(),
                fixture.log.toString());
        var casting = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        casting.open(1);
        casting.target(1, 1);
        casting.run(400);
        assertTrue(casting.actions.casts(1) >= 10, "a new local intent casts again when the cooldown allows");
        assertTrue(casting.log.size() <= 4, casting.log.toString());
    }

    private void engageTogether() {
        fixture.open(1);
        fixture.target(1, 1);
        fixture.open(2);
        fixture.target(2, 1);
        fixture.runUntilRequest(1);
        assertEquals(4, fixture.client.requests.getFirst().questions().size());
    }
}
