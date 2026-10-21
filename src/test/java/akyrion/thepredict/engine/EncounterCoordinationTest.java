package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticalIntent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.GUARD;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** M10.7 in the engine: one answer for three mobs that all want to retreat. */
class EncounterCoordinationTest {
    private static final Map<Long, Double> CONFIDENCE = Map.of(1L, 0.7, 2L, 0.95, 3L, 0.8);

    private static Answer answerFor(QuestionSpec question) {
        return question.situation() == Situation.NOW
                ? prefer(FALL_BACK, 0.7, CONFIDENCE.get(question.mob().entityId().getLeastSignificantBits()))
                : prefer(GUARD, 0.7, 0.9);
    }

    private static ExecutionFixture engaged() {
        var fixture = new ExecutionFixture();
        for (var mob = 1; mob <= 3; mob++) {
            fixture.open(mob);
            fixture.target(mob, 1);
        }
        fixture.runUntilRequest(1);
        assertEquals(6, fixture.client.requests.getFirst().questions().size());
        return fixture;
    }

    private static List<String> outcome(ExecutionFixture fixture) {
        var views = new ArrayList<String>();
        for (var mob = 1; mob <= 3; mob++) {
            views.add(fixture.view(mob).level() + " " + fixture.view(mob).movement() + " " + fixture.view(mob).tactic());
        }
        return views;
    }

    @Test
    void onlyTheMostConfidentRetreatOfThreeMembersRunsAndTheOthersTakeTheirNextAlternative() {
        var fixture = engaged();
        fixture.answer(0, EncounterCoordinationTest::answerFor);
        fixture.run(1);
        assertEquals(List.of("ALTERNATIVE HOLD guard_post", "JEV RETREAT fall_back", "ALTERNATIVE HOLD guard_post"),
                outcome(fixture));
        assertEquals(1, List.of(1, 2, 3).stream().filter(mob -> fixture.view(mob).movement() == Movement.RETREAT).count());
        assertEquals(ControlLevel.JEV, fixture.view(2).level());
    }

    @Test
    void theResultDoesNotDependOnTheOrderOfTheAnswersInTheResponse() {
        var inOrder = engaged();
        var reversed = engaged();
        inOrder.answer(0, EncounterCoordinationTest::answerFor);
        var request = reversed.client.requests.getFirst();
        var keys = new ArrayList<>(request.questions());
        Collections.reverse(keys);
        var answers = new LinkedHashMap<QuestionKey, Answer>();
        keys.forEach(question -> answers.put(question.key(), answerFor(question)));
        reversed.client.futures.getFirst().complete(new DecisionResult.Answered(answers, ExecutionFixture.MODEL, 400));
        inOrder.run(1);
        reversed.run(1);
        assertEquals(outcome(inOrder), outcome(reversed));
    }

    @Test
    void aRetreatRefusedByTheCoordinationRecordsRetreatLimitWhetherItPlansLocallyOrFallsToBase() {
        var fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        var session = fixture.open(1);
        fixture.target(1, 1);
        fixture.run(5);
        var control = new IntentControl(fixture.sessions.board, fixture.actions, fixture.metrics,
                new ExecutionLog(line -> {
                }, false), new OutcomeEvents(fixture.sessions.registry, fixture.sessions.encounters,
                new OutcomeMetrics(), fixture.metrics), DecisionTap.NONE);
        var fallback = new FallbackStep(fixture.tracker, fixture.sessions.encounters, fixture.budget, control,
                new ExecutionSettings(1_000_000_000L, 3, ExecutionFixture.MODEL, false));
        var retreat = new TacticalIntent(session.key(), FALL_BACK, Movement.RETREAT, Optional.empty(), 0.9, List.of(),
                Map.of(), ControlLevel.JEV, fixture.clock.tick, fixture.clock.nanos + 5_000_000_000L, false);
        control.apply(session, retreat, fixture.clock.tick);
        fallback.withoutRetreat(session, fixture.clock.tick, fixture.clock.nanos);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertEquals(Optional.of(FallbackReason.RETREAT_LIMIT), session.lastFallbackReason());
        control.apply(session, retreat, fixture.clock.tick);
        fixture.reader.eligible.put(EngineFixtures.id(1), Set.of(FALL_BACK));
        fixture.run(25);
        control.apply(session, retreat, fixture.clock.tick);
        fallback.withoutRetreat(session, fixture.clock.tick, fixture.clock.nanos);
        assertEquals(IntentView.base(0), fixture.view(1));
        assertEquals(Optional.of(FallbackReason.RETREAT_LIMIT), session.lastFallbackReason());
        assertEquals(2, fixture.count(FallbackReason.RETREAT_LIMIT));
    }
}
