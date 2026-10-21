package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.GroupAnswer;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.IntentPolicy;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.UntypedCastMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.MODEL;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.1: an answer applies only to the incarnation, catalog, model and context it was asked for. */
class DecisionAcceptorTest {
    private static final Function<QuestionSpec, Answer> KEEP_RANGE = nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9));

    private ExecutionFixture fixture = new ExecutionFixture();

    private void engage(int... mobs) {
        for (var mob : mobs) {
            fixture.open(mob);
            fixture.target(mob, 1);
        }
        fixture.runUntilRequest(1);
    }

    private boolean appliedKeep(int mob) {
        return fixture.view(mob).level() == ControlLevel.JEV && fixture.view(mob).tactic().equals(KEEP);
    }

    @Test
    void aLateAnswerOfAnOlderSequenceIsDiscardedAfterTheWatchdogReleasedIt() {
        engage(1);
        var first = fixture.sent(0, 1);
        fixture.run(40);
        fixture.runUntilRequest(2);
        assertEquals(1, fixture.metrics.total(DecisionMetrics.Counter.WATCHDOG_RELEASES));
        var cancelledCopy = fixture.count(DiscardReason.STALE_SEQUENCE);
        fixture.deliver(first, fixture.answered(0, KEEP_RANGE, MODEL));
        assertEquals(cancelledCopy + 1, fixture.count(DiscardReason.STALE_SEQUENCE));
        assertFalse(appliedKeep(1));
        assertEquals(ServiceState.READY, fixture.tracker.state(), "one timeout is counted once, never twice");
    }

    @Test
    void aDuplicateAnswerIsDiscardedTheSecondTime() {
        engage(1);
        var result = fixture.answered(0, KEEP_RANGE, MODEL);
        fixture.deliver(fixture.sent(0, 1), result);
        assertTrue(appliedKeep(1));
        var signals = fixture.actions.signals(1).size();
        fixture.deliver(fixture.sent(0, 1), result);
        assertEquals(1, fixture.count(DiscardReason.STALE_SEQUENCE));
        assertEquals(signals, fixture.actions.signals(1).size());
    }

    @Test
    void anAnswerForAnOlderGenerationIsDiscarded() {
        engage(1);
        fixture.sessions.registry.advanceGeneration(EngineFixtures.id(1));
        fixture.deliver(fixture.sent(0, 1), fixture.answered(0, KEEP_RANGE, MODEL));
        assertEquals(1, fixture.count(DiscardReason.SESSION_CHANGED));
        assertEquals(1, fixture.view(1).generation());
        assertEquals(ControlLevel.BASE, fixture.view(1).level());
    }

    @Test
    void aClosedSessionIsDiscardedWhileTheOtherMobOfTheSameAnswerApplies() {
        engage(1, 2);
        fixture.sessions.registry.close(EngineFixtures.id(2), FallbackReason.SESSION_INVALID);
        fixture.deliver(fixture.sent(0, 1), fixture.answered(0, KEEP_RANGE, MODEL));
        assertEquals(1, fixture.count(DiscardReason.SESSION_INACTIVE));
        assertTrue(appliedKeep(1));
        assertEquals(ControlLevel.BASE, fixture.view(2).level());
    }

    @Test
    void aSessionReopenedAtGenerationZeroInAnotherEncounterIsDiscardedByEncounterIdentity() {
        engage(1, 2);
        var request = fixture.sent(0, 1);
        fixture.sessions.registry.close(EngineFixtures.id(2), FallbackReason.SESSION_INVALID);
        var reopened = fixture.open(2);
        fixture.target(2, 7);
        fixture.run(2);
        assertEquals(0, reopened.key().generation());
        assertNotEquals(Optional.of(request.encounter()), reopened.encounter());
        fixture.deliver(request, fixture.answered(0, KEEP_RANGE, MODEL));
        assertEquals(1, fixture.count(DiscardReason.SESSION_CHANGED));
        assertTrue(appliedKeep(1));
        assertFalse(appliedKeep(2));
    }

    @Test
    void anAnswerBuiltForAnOlderCatalogIsDiscarded() {
        engage(1);
        fixture.revision = 2;
        fixture.deliver(fixture.sent(0, 1), fixture.answered(0, KEEP_RANGE, MODEL));
        assertEquals(1, fixture.count(DiscardReason.CATALOG_CHANGED));
        assertFalse(appliedKeep(1));
    }

    @Test
    void aVersionedModelRefusesEveryOtherEffectiveModel() {
        engage(1);
        fixture.deliver(fixture.sent(0, 1), fixture.answered(0, KEEP_RANGE, "jev-1.14.0"));
        assertEquals(1, fixture.count(DiscardReason.MODEL_CHANGED));
        assertFalse(appliedKeep(1));
    }

    @Test
    void anAliasLearnsItsFirstModelAndDiscardsTheAnswerThatRevealsAChangeOnce() {
        fixture = new ExecutionFixture(new ExecutionSettings(1_000_000_000L, 3, "jev-latest", false),
                ExecutionFixture.settings(2000));
        engage(1);
        fixture.deliver(fixture.sent(0, 1), fixture.answered(0, KEEP_RANGE, "jev-1.13.0"));
        assertTrue(appliedKeep(1));
        fixture.runUntilRequest(2);
        var change = nowAndContingency(prefer(PRESS, 0.9, 0.9), prefer(FALL_BACK, 0.7, 0.9));
        fixture.deliver(fixture.sent(1, 1), fixture.answered(1, change, "jev-1.14.0"));
        assertEquals(1, fixture.count(DiscardReason.MODEL_CHANGED));
        assertEquals(1, fixture.log.stream().filter(line -> line.contains("changed from jev-1.13.0 to jev-1.14.0")).count());
        fixture.runUntilRequest(3);
        fixture.deliver(fixture.sent(2, 1), fixture.answered(2, KEEP_RANGE, "jev-1.14.0"));
        assertEquals(1, fixture.count(DiscardReason.MODEL_CHANGED));
        assertTrue(appliedKeep(1));
    }

    @Test
    void aReactiveAnswerLivesItsHorizonFromItsCaptureAndAStandardOneFromItsAcceptance() {
        var reactive = new DecisionTiming(20, 20, 100, 0.5, 0.2, 1, QuestionDetail.DEFAULT, false,
                CastMode.INTENT_ONLY, 0, UntypedCastMode.DEFAULT, TickLayerMode.CASTS, ReactionScope.ALL, Set.of(), 0,
                DecisionCadence.REACTIVE, 6, 10);
        fixture.open(1, ExecutionFixture.profile(reactive, 0));
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        var request = fixture.sent(0, 1);
        fixture.clock.nanos = request.captureNanos() + 6 * ExecutionFixture.TICK_NANOS;
        fixture.deliver(request, fixture.answered(0, KEEP_RANGE, MODEL));
        assertTrue(appliedKeep(1));
        assertEquals(request.captureNanos() + 10 * IntentPolicy.NANOS_PER_TICK,
                fixture.session(1).intent().orElseThrow().expiresAtNanos(),
                "AE-09: accepted six ticks after its capture, it has four ticks of its horizon left");
        fixture = new ExecutionFixture();
        engage(1);
        var standard = fixture.sent(0, 1);
        fixture.clock.nanos = standard.captureNanos() + 6 * ExecutionFixture.TICK_NANOS;
        fixture.deliver(standard, fixture.answered(0, KEEP_RANGE, MODEL));
        assertTrue(appliedKeep(1));
        assertEquals(fixture.clock.nanos + 100 * IntentPolicy.NANOS_PER_TICK,
                fixture.session(1).intent().orElseThrow().expiresAtNanos(), "the standard cadence keeps M19");
    }

    @Test
    void anAnswerOlderThanTheMaximumAgeFromItsCaptureIsDiscardedAndTheBoundaryIsKept() {
        fixture = new ExecutionFixture(new ExecutionSettings(200_000_000L, 3, MODEL, false),
                ExecutionFixture.settings(2000));
        engage(1);
        var request = fixture.sent(0, 1);
        fixture.clock.nanos = request.captureNanos() + 200_000_001L;
        fixture.deliver(request, fixture.answered(0, KEEP_RANGE, MODEL));
        assertEquals(1, fixture.count(DiscardReason.TOO_OLD));
        var fresh = new ExecutionFixture(new ExecutionSettings(200_000_000L, 3, MODEL, false),
                ExecutionFixture.settings(2000));
        fixture = fresh;
        engage(1);
        request = fixture.sent(0, 1);
        fixture.clock.nanos = request.captureNanos() + 200_000_000L;
        fixture.deliver(request, fixture.answered(0, KEEP_RANGE, MODEL));
        assertTrue(appliedKeep(1));
    }

    @Test
    void anAnswerForAMobWhoseTargetChangedOrCannotBeProbedIsDiscarded() {
        engage(1, 2);
        fixture.reader.probeTargets.put(EngineFixtures.id(1), EngineFixtures.player(9));
        fixture.reader.unreadable.add(EngineFixtures.id(2));
        fixture.deliver(fixture.sent(0, 1), fixture.answered(0, KEEP_RANGE, MODEL));
        assertEquals(1, fixture.count(DiscardReason.TARGET_CHANGED));
        assertEquals(1, fixture.count(DiscardReason.PROBE_UNAVAILABLE));
        assertFalse(appliedKeep(1));
        assertFalse(appliedKeep(2));
    }

    @Test
    void aCancelledResultIsDiscardedWithoutFallbackAndTheRealAnswerStillApplies() {
        engage(1);
        var request = fixture.sent(0, 1);
        fixture.deliver(request, new DecisionResult.Failed(FailureKind.NETWORK, Optional.empty(),
                DecisionAcceptor.CANCELLED));
        assertEquals(1, fixture.count(DiscardReason.STALE_SEQUENCE));
        assertEquals(0, fixture.count(FallbackReason.SERVICE_PAUSED));
        fixture.deliver(request, fixture.answered(0, KEEP_RANGE, MODEL));
        assertTrue(appliedKeep(1));
    }

    @Test
    void everyDiscardIsCountedByItsCauseAndLoggedOnlyWithDecisionLogging() {
        engage(1);
        fixture.revision = 5;
        fixture.deliver(fixture.sent(0, 1), fixture.answered(0, KEEP_RANGE, MODEL));
        assertEquals(1, fixture.count(DiscardReason.CATALOG_CHANGED));
        assertTrue(fixture.log.stream().anyMatch(line -> line.endsWith(": CATALOG_CHANGED")));
        var quiet = new ExecutionFixture(new ExecutionSettings(1_000_000_000L, 3, MODEL, false),
                ExecutionFixture.settings(2000));
        fixture = quiet;
        engage(1);
        fixture.revision = 5;
        fixture.deliver(fixture.sent(0, 1), fixture.answered(0, KEEP_RANGE, MODEL));
        assertEquals(1, fixture.count(DiscardReason.CATALOG_CHANGED));
        assertEquals(List.of(), fixture.log);
    }

    @Test
    void aChangedMembershipDropsThePlanAndKeepsTheTactic() {
        engage(1);
        fixture.session(1).groupCopy().publish(9, 9, null, -1, 0, -1, 0, 1, GroupMode.COORDINATE);
        var answered = fixture.answered(0, KEEP_RANGE, MODEL)
                .withGroup(new GroupAnswer(GroupPlan.SURROUND_TARGET, 2));
        fixture.deliver(withBody(fixture.sent(0, 1), "membership 9"), answered);
        assertEquals(1, fixture.count(DiscardReason.GROUP_EPOCH_CHANGED));
        assertTrue(appliedKeep(1));
        assertFalse(encounterOf(1).hasGroupAnswer());
    }

    @Test
    void aMatchingMembershipKeepsThePlanWhateverTheBodySays() {
        engage(1);
        fixture.session(1).groupCopy().publish(2, 9, null, -1, 0, -1, 0, 1, GroupMode.COORDINATE);
        var answered = fixture.answered(0, KEEP_RANGE, MODEL)
                .withGroup(new GroupAnswer(GroupPlan.SURROUND_TARGET, 2));
        fixture.deliver(withBody(fixture.sent(0, 1), "membership 4"), answered);
        assertEquals(0, fixture.count(DiscardReason.GROUP_EPOCH_CHANGED));
        assertTrue(appliedKeep(1));
        assertTrue(encounterOf(1).hasGroupAnswer());
        assertEquals(GroupPlan.SURROUND_TARGET, encounterOf(1).groupAnswerPlan());
        assertEquals(2, encounterOf(1).groupAnswerMembership());
    }

    @Test
    void aPlanAnswerUnderTheLeadersConfidenceFloorKeepsTheLocalPlan() {
        engage(1);
        fixture.session(1).groupCopy().publish(2, 9, null, -1, 0, -1, 0, 1, GroupMode.COORDINATE);
        var floor = fixture.session(1).profile().timing().minimumConfidence();
        var low = fixture.answered(0, KEEP_RANGE, MODEL)
                .withGroup(new GroupAnswer(GroupPlan.SURROUND_TARGET, 2, Math.max(0.0, floor - 0.1)));
        fixture.deliver(fixture.sent(0, 1), low);
        assertTrue(appliedKeep(1));
        assertFalse(encounterOf(1).hasGroupAnswer());
        assertEquals(0, fixture.count(DiscardReason.GROUP_EPOCH_CHANGED));
    }

    private Encounter encounterOf(int mob) {
        return fixture.sessions.encounters.find(fixture.session(mob).encounter().orElseThrow()).orElseThrow();
    }

    private static InFlightRequest withBody(InFlightRequest sent, String body) {
        return new InFlightRequest(sent.encounter(), sent.worldId(), sent.sequence(), sent.catalogRevision(),
                sent.questions(), sent.fitChecks(), sent.participants(), sent.captureNanos(), sent.sentAtNanos(),
                sent.deadlineNanos(), sent.estimatedTokens(), sent.bodyChars(), sent.captureTick(), body);
    }
}
