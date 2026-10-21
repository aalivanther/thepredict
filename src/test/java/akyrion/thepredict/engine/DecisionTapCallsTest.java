package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every call of the {@link DecisionTap} from the real engine (M18.4), heard by a recording double attached to the
 * {@link SwitchableDecisionTap} of the execution fixture: where each call comes from, in which order, and with which
 * join key. Rule P2: doubles of the ports and a controllable clock.
 */
class DecisionTapCallsTest {
    private final ExecutionFixture fixture = new ExecutionFixture();
    private final Heard heard = new Heard();

    @Test
    void aSentRequestTellsItsPlanItsBodyItsResultAndTheVerdictWithItsQuestion() {
        fixture.tap.attach(heard, () -> {
        });
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        var request = fixture.runUntilRequest(1);
        fixture.answer(0, nowAndContingency(prefer(PRESS, 0.8, 0.9), prefer(FALL_BACK, 0.7, 0.8)));
        fixture.run(1);
        var key = " 1/" + request.sequence();
        assertEquals("planned SENT" + key, heard.calls.get(0));
        assertEquals("body" + key + " " + fixture.client.sent.getFirst().body().length(), heard.calls.get(1));
        assertEquals("result Answered" + key, heard.calls.get(2));
        assertTrue(heard.calls.contains("intent ADOPTED press_attack q0 JEV"), heard.calls.toString());
        assertTrue(heard.calls.stream().anyMatch(call -> call.startsWith("intent CONTINGENCY_STORED fall_back q")),
                heard.calls.toString());
        assertTrue(heard.calls.contains("cast press_attack strike CAST"), heard.calls.toString());
        assertTrue(heard.calls.indexOf("result Answered" + key) < heard.calls.indexOf("intent ADOPTED press_attack q0 JEV"));
    }

    @Test
    void anExpiredRequestTellsItsTimeoutOnceAndItsCancelledFutureIsNoSecondResult() {
        fixture.tap.attach(heard, () -> {
        });
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        var request = fixture.runUntilRequest(1);
        fixture.run(60);
        var key = " 1/" + request.sequence();
        assertTrue(heard.calls.contains("result Failed TIMEOUT" + key), heard.calls.toString());
        assertFalse(heard.calls.contains("result Failed NETWORK" + key), "the cancelled future is the echo of the release");
        assertFalse(heard.calls.contains("discarded STALE_SEQUENCE" + key), heard.calls.toString());
    }

    @Test
    void nothingToAskAndAFailedPreparationTellOnlyTheirPlanWithTheirRealSequence() {
        fixture.tap.attach(heard, () -> {
        });
        fixture.ready();
        var session = fixture.open(1);
        fixture.target(1, 1);
        fixture.client.preparation = request -> new Preparation.NothingToAsk(Set.of(session.key()));
        runUntilPlanned(1);
        fixture.client.preparation = request -> new Preparation.Invalid("too large");
        runUntilPlanned(2);
        var planned = heard.calls.stream().filter(call -> call.startsWith("planned")).toList();
        assertEquals(List.of("planned NOTHING_TO_ASK 1/1", "planned PREPARE_FAILED 1/2"), planned);
        assertTrue(heard.calls.stream().noneMatch(call -> call.startsWith("body") || call.startsWith("result")));
    }

    @Test
    void aRequestWithoutBudgetTellsItsPlanAndNothingElse() {
        var poor = new ExecutionFixture(new ExecutionSettings(1_000_000_000L, 3, ExecutionFixture.MODEL, true),
                ExecutionFixture.settings(1));
        poor.tap.attach(heard, () -> {
        });
        poor.ready();
        poor.open(1);
        poor.target(1, 1);
        poor.runAnswering(60, nowAndContingency(prefer(KEEP, 0.8, 0.9), prefer(FALL_BACK, 0.7, 0.8)));
        assertTrue(heard.calls.contains("planned NO_BUDGET 1/2"), heard.calls.toString());
        assertTrue(heard.calls.stream().noneMatch(call -> call.endsWith(" 1/2") && !call.startsWith("planned")));
    }

    @Test
    void withoutJevTheLocalPlanAndTheWindowsAreToldAndNoRequestIs() {
        var local = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        local.tap.attach(heard, () -> {
        });
        local.open(1);
        local.target(1, 1);
        local.run(200);
        assertTrue(heard.calls.stream().anyMatch(call -> call.startsWith("intent LOCAL_PLANNED ")), heard.calls.toString());
        assertTrue(heard.calls.stream().anyMatch(call -> call.startsWith("outcome LOCAL ")), heard.calls.toString());
        assertTrue(heard.calls.stream().noneMatch(call -> call.startsWith("planned")));
    }

    @Test
    void aThreatChangeHandedToThePortIsTold() {
        fixture.tap.attach(heard, () -> {
        });
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.answer(0, nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        fixture.threat.blockedHit(id(1), player(1));
        assertTrue(heard.calls.contains("threat MELEE_BLOCKED"), heard.calls.toString());
    }

    @Test
    void nothingIsBuiltOrToldWhileNothingRecords() {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.answer(0, nowAndContingency(prefer(PRESS, 0.8, 0.9), prefer(FALL_BACK, 0.7, 0.8)));
        fixture.run(30);
        assertTrue(heard.calls.isEmpty());
    }

    @Test
    void aTapThatThrowsNeverLeaksBudgetOrRequestsAndLeavesTheServiceAsItWas() {
        var failures = new int[1];
        fixture.tap.attach(new DecisionTap() {
            @Override
            public boolean recording() {
                return true;
            }

            @Override
            public void questionsPlanned(EncounterId encounter, long sequence, List<QuestionSpec> planned,
                                         Preparation preparation, DecisionDispatcher.Outcome dispatch,
                                         CombatFacts facts) {
                throw new IllegalStateException("broken recorder");
            }
        }, () -> failures[0]++);
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        assertEquals(1, failures[0], "the recording was told once and detached");
        assertEquals(1, fixture.inFlight.size(), "the request went out as without a tap");
        fixture.answer(0, nowAndContingency(prefer(PRESS, 0.8, 0.9), prefer(FALL_BACK, 0.7, 0.8)));
        fixture.run(1);
        assertEquals(0, fixture.inFlight.size(), "no request stays in flight");
        assertEquals(0, fixture.budget.inFlight(), "no reservation stays open");
        assertEquals(akyrion.thepredict.domain.ServiceState.READY, fixture.tracker.status().state());
        assertEquals(1, fixture.budget.requestsLastMinute(fixture.clock.nanos), "one reservation, released by the answer");
        assertEquals(akyrion.thepredict.domain.ControlLevel.JEV, fixture.session(1).intent().orElseThrow().level());
    }

    private void runUntilPlanned(int count) {
        for (var tick = 0; tick < 200 && heard.calls.stream().filter(call -> call.startsWith("planned")).count() < count;
             tick++) {
            fixture.run(1);
        }
    }

    /** A recording that writes each call as one line. */
    private static final class Heard implements DecisionTap {
        private final List<String> calls = new ArrayList<>();

        @Override
        public boolean recording() {
            return true;
        }

        @Override
        public void questionsPlanned(EncounterId encounter, long sequence, List<QuestionSpec> planned,
                                     Preparation preparation, DecisionDispatcher.Outcome dispatch, CombatFacts facts) {
            calls.add("planned " + dispatch + " " + encounter.value() + "/" + sequence);
        }

        @Override
        public void requestBody(EncounterId encounter, long sequence, String body) {
            calls.add("body " + encounter.value() + "/" + sequence + " " + body.length());
        }

        @Override
        public void resultReceived(EncounterId encounter, long sequence, DecisionResult result, long roundTripNanos) {
            var kind = result instanceof DecisionResult.Failed failed ? "Failed " + failed.kind() : "Answered";
            calls.add("result " + kind + " " + encounter.value() + "/" + sequence);
        }

        @Override
        public void answerDiscarded(EncounterId encounter, long sequence, DiscardReason reason) {
            calls.add("discarded " + reason + " " + encounter.value() + "/" + sequence);
        }

        @Override
        public void answerDiscarded(EncounterId encounter, long sequence, QuestionKey question, MobKey mob,
                                    DiscardReason reason) {
            calls.add("discarded " + reason + " " + question.value());
        }

        @Override
        public void intentEvent(MobKey mob, IntentEvent event) {
            calls.add("intent " + event.kind() + " " + event.tactic().value() + " "
                    + event.question().map(QuestionKey::value).orElse("-") + " " + event.to());
        }

        @Override
        public void castAttempted(MobKey mob, TacticId tactic, ChoiceId choice, ExecutionOutcome outcome) {
            calls.add("cast " + tactic.value() + " " + choice.value() + " " + outcome);
        }

        @Override
        public void threatChanged(MobKey mob, UUID participant, double amount, String cause) {
            calls.add("threat " + cause);
        }

        @Override
        public void outcomeClosed(MobKey mob, OutcomeWindow.Summary summary) {
            calls.add("outcome " + summary.level() + " " + summary.tactic().value());
        }
    }
}
