package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticalIntent;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The preparation of M16.1 inside the whole engine (rule P2): the sequence is taken right before {@code prepare}, a
 * request that is not sent leaves no trace in the budget, the service tracker, the model expectation or the answer
 * groups, a request that cannot be prepared is a local failure of its encounter, and a mob whose question is not sent
 * is resolved by the fallback chain in the reactive cycle.
 */
class DispatchPreparationTest {
    private final ExecutionFixture fixture = new ExecutionFixture();

    private MobSession asked(int mob) {
        var session = fixture.open(mob);
        fixture.target(mob, 1);
        return session;
    }

    private long dispatchedAfterPlanning() {
        return fixture.metrics.count(DecisionDispatcher.Outcome.SENT)
                + fixture.metrics.count(DecisionDispatcher.Outcome.NO_BUDGET)
                + fixture.metrics.count(DecisionDispatcher.Outcome.NOTHING_TO_ASK)
                + fixture.metrics.count(DecisionDispatcher.Outcome.PREPARE_FAILED)
                + fixture.metrics.count(DecisionDispatcher.Outcome.CLIENT_ERROR);
    }

    @Test
    void aSequenceTakenAndNotSentLeavesAGapThatNeverDiscardsTheNextAnswer() {
        fixture.ready();
        var session = asked(1);
        fixture.client.preparation = request -> new Preparation.NothingToAsk(Set.of(session.key()));
        for (var tick = 0; tick < 200 && fixture.client.prepared.isEmpty(); tick++) {
            fixture.run(1);
        }
        assertEquals(1, fixture.client.prepared.getFirst().sequence());
        fixture.client.preparation = PreparedFixtures::ready;
        var request = fixture.runUntilRequest(1);
        assertEquals(2, request.sequence());
        fixture.answer(0, question -> ExecutionFixture.prefer(KEEP, 0.8, 0.9));
        fixture.run(1);
        assertEquals(0, fixture.metrics.count(DiscardReason.STALE_SEQUENCE));
        assertEquals(ControlLevel.JEV, session.intent().map(TacticalIntent::level).orElseThrow());
        assertEquals(KEEP, session.intent().orElseThrow().tactic());
    }

    @Test
    void nothingToAskSendsReservesAndChangesNothingAndItsMobPlaysLocallyUntilTheNextHeartbeat() {
        fixture.ready();
        var session = asked(1);
        fixture.client.preparation = request -> new Preparation.NothingToAsk(Set.of(session.key()));
        fixture.run(45);
        var dispatched = fixture.metrics.count(DecisionDispatcher.Outcome.NOTHING_TO_ASK);
        assertTrue(dispatched >= 2 && dispatched <= 3, "one per heartbeat, never every tick: " + dispatched);
        assertTrue(fixture.client.requests.isEmpty());
        assertEquals(0, fixture.budget.requestsLastMinute(fixture.clock.nanos));
        assertEquals(0, fixture.budget.tokensLastMinute(fixture.clock.nanos));
        assertEquals(0, fixture.inFlight.size());
        assertEquals(ServiceState.READY, fixture.tracker.status().state());
        assertEquals(0, fixture.metrics.count(FailureKind.INVALID_REQUEST));
        assertEquals(0, fixture.metrics.total(DecisionMetrics.Counter.ANSWERED));
        assertEquals(ControlLevel.LOCAL, session.intent().map(TacticalIntent::level).orElseThrow());
        assertFalse(session.jevAnswered(), "a local mob is never marked answered");
        assertEquals(fixture.client.prepared.size(), dispatchedAfterPlanning());
    }

    @Test
    void anInvalidPreparationIsALocalFailureWithoutAnyReservationOrEffectOnTheService() {
        fixture.ready();
        var session = asked(1);
        fixture.client.preparation = request -> new Preparation.Invalid("request exceeds the estimated limit");
        for (var tick = 0; tick < 200 && fixture.client.prepared.isEmpty(); tick++) {
            fixture.run(1);
        }
        assertEquals(1, fixture.metrics.count(DecisionDispatcher.Outcome.PREPARE_FAILED));
        assertEquals(1, fixture.metrics.count(FailureKind.INVALID_REQUEST));
        assertEquals(ServiceState.READY, fixture.tracker.status().state());
        assertEquals(0, fixture.budget.requestsLastMinute(fixture.clock.nanos));
        assertEquals(0, fixture.budget.tokensLastMinute(fixture.clock.nanos));
        assertEquals(0, fixture.inFlight.size());
        assertTrue(fixture.client.requests.isEmpty());
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.getFirst().endsWith("request exceeds the estimated limit"),
                fixture.warnings.getFirst());
        fixture.run(15);
        assertEquals(ControlLevel.LOCAL, session.intent().map(TacticalIntent::level).orElseThrow());
        assertEquals(ServiceState.READY, fixture.tracker.status().state());
    }

    @Test
    void anEncounterThatNeverFitsKeepsTheServiceReadyAndLogsAtMostOneLinePerInterval() {
        fixture.ready();
        asked(1);
        fixture.client.preparation = request -> new Preparation.Invalid("request exceeds the estimated limit");
        fixture.run(180);
        var failures = fixture.metrics.count(DecisionDispatcher.Outcome.PREPARE_FAILED);
        assertTrue(failures >= 5 && failures <= 10, "one preparation per heartbeat, never every tick: " + failures);
        assertEquals(failures, fixture.metrics.count(FailureKind.INVALID_REQUEST));
        assertEquals(ServiceState.READY, fixture.tracker.status().state());
        assertEquals(1, fixture.warnings.size(), "180 ticks are 9 s, inside one log interval");
        fixture.run(40);
        assertEquals(2, fixture.warnings.size());
        assertTrue(fixture.warnings.getLast().contains("more since the last line"), fixture.warnings.getLast());
    }

    @Test
    void anOversizedEncounterDoesNotPauseTheOtherEncounters() {
        fixture.ready();
        var oversized = asked(1);
        fixture.open(2);
        fixture.target(2, 2);
        var other = fixture.session(2);
        fixture.client.preparation = request -> request.questions().stream()
                .anyMatch(question -> question.mob().equals(oversized.key()))
                ? new Preparation.Invalid("request exceeds the estimated limit")
                : PreparedFixtures.ready(request);
        var request = fixture.runUntilRequest(1);
        assertTrue(request.questions().stream().allMatch(question -> question.mob().equals(other.key())));
        assertTrue(fixture.metrics.count(DecisionDispatcher.Outcome.PREPARE_FAILED) >= 1);
        assertEquals(ServiceState.READY, fixture.tracker.status().state());
        fixture.answer(0, question -> ExecutionFixture.prefer(KEEP, 0.8, 0.9));
        fixture.run(1);
        assertEquals(ControlLevel.JEV, other.intent().map(TacticalIntent::level).orElseThrow());
        assertEquals(ControlLevel.LOCAL, oversized.intent().map(TacticalIntent::level).orElseThrow());
        fixture.run(40);
        assertTrue(fixture.client.requests.size() >= 2, "the other encounter keeps asking Jev");
        assertEquals(ServiceState.READY, fixture.tracker.status().state());
    }

    @Test
    void theDispatcherSendsThePreparationItGotWithoutPreparingAgainAndEvaluatedNeverPlans() {
        fixture.ready();
        asked(1);
        fixture.runAnswering(120, question -> ExecutionFixture.prefer(KEEP, 0.8, 0.9));
        assertTrue(fixture.client.sent.size() >= 4, String.valueOf(fixture.client.sent.size()));
        assertEquals(fixture.client.prepared.size(), dispatchedAfterPlanning(),
                "only the dispatcher prepares: the evaluation of M10 never plans a question");
        assertEquals(fixture.client.prepared.size(), fixture.client.sent.size());
        for (var index = 0; index < fixture.client.sent.size(); index++) {
            assertSame(fixture.client.prepared.get(index), fixture.client.sent.get(index).sent());
        }
    }

    @Test
    void aDroppedNowDropsItsContingenciesTheOtherMobKeepsOneNowAndTheDroppedMobPlaysLocally() {
        fixture.ready();
        var dropped = asked(1);
        var kept = asked(2);
        fixture.client.preparation = request -> {
            var sent = request.questions().stream().filter(question -> question.mob().equals(kept.key())).toList();
            if (sent.isEmpty()) {
                return new Preparation.NothingToAsk(Set.of(dropped.key()));
            }
            var keys = new HashSet<QuestionKey>();
            request.questions().stream().filter(question -> !sent.contains(question))
                    .forEach(question -> keys.add(question.key()));
            var only = new DecisionRequest(request.encounter(), request.sequence(), request.facts(), sent,
                    request.profiles());
            var body = "x".repeat(900);
            return new Preparation.Ready(new PreparedRequest(only, body, keys, Set.of(dropped.key()), body.length()));
        };
        var request = fixture.runUntilRequest(1);
        assertEquals(List.of(Situation.NOW, Situation.IF_TARGET_IN_MELEE), request.questions().stream()
                .map(QuestionSpec::situation).toList());
        assertTrue(request.questions().stream().allMatch(question -> question.mob().equals(kept.key())));
        assertEquals(1, fixture.metrics.total(DecisionMetrics.Counter.CONTINGENCIES_ASKED));
        fixture.answer(0, question -> ExecutionFixture.prefer(KEEP, 0.8, 0.9));
        fixture.run(3);
        assertEquals(0, fixture.metrics.count(FallbackReason.INVALID_RESPONSE), "one NOW per asked mob");
        assertEquals(ControlLevel.JEV, kept.intent().map(TacticalIntent::level).orElseThrow());
        assertEquals(ControlLevel.LOCAL, dropped.intent().map(TacticalIntent::level).orElseThrow());
        assertFalse(dropped.jevAnswered());
        assertTrue(kept.jevAnswered());
    }

    @Test
    void theOptionsThatReadTheSameAreCountedAsMerged() {
        fixture.ready();
        var session = asked(1);
        fixture.client.preparation = request -> {
            var questions = request.questions().stream().map(question -> question.withOptions(
                    question.options().subList(0, 2))).toList();
            var sent = new DecisionRequest(request.encounter(), request.sequence(), request.facts(), questions,
                    request.profiles());
            return new Preparation.Ready(PreparedFixtures.prepared(sent));
        };
        var request = fixture.runUntilRequest(1);
        var planned = fixture.client.prepared.getFirst().questions().stream().mapToInt(question -> question.options()
                .size()).sum();
        var sent = request.questions().stream().mapToInt(question -> question.options().size()).sum();
        assertEquals(planned - sent, fixture.metrics.total(DecisionMetrics.Counter.OPTIONS_MERGED));
        assertTrue(fixture.metrics.total(DecisionMetrics.Counter.OPTIONS_MERGED) > 0);
        assertEquals(new MobKey(session.key().entityId(), session.key().generation()), request.questions().getFirst()
                .mob());
    }

    @Test
    void theReservationIsTheSizeOfThePreparedBodyAndTheBudgetLearnsFromTheAnswer() {
        fixture.ready();
        asked(1);
        fixture.client.preparation = request -> {
            var body = "x".repeat(3000);
            return new Preparation.Ready(new PreparedRequest(request, body, Set.of(), Set.of(), body.length()));
        };
        fixture.runUntilRequest(1);
        assertEquals(2000, fixture.budget.tokensLastMinute(fixture.clock.nanos));
        fixture.answer(0, question -> ExecutionFixture.prefer(KEEP, 0.8, 0.9));
        fixture.run(1);
        assertEquals(400, fixture.budget.tokensLastMinute(fixture.clock.nanos));
        assertEquals(RequestBudget.MAX_CHARS_PER_TOKEN, fixture.budget.charsPerToken(),
                "3000 characters for 400 tokens teach 7.5, bounded to 2");
    }

    @Test
    void fitChecksTravelInTheSameRequestAsTheQuestions() {
        fixture.ready();
        var timing = new DecisionTiming(20, 20, 100, 0.5, 0.2, 1,
                QuestionDetail.FULL, true);
        fixture.open(1, ExecutionFixture.profile(timing, 0));
        fixture.target(1, 1);
        var request = fixture.runUntilRequest(1);
        var now = request.questions().getFirst();
        assertEquals(now.options().size(), request.fitChecks().size());
        assertEquals(1, fixture.client.sent.size(), "one request per encounter and decision (G10)");
        assertTrue(request.fitChecks().stream().allMatch(check -> now.options().contains(check.tactic())));
    }
}
