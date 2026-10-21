package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.between;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.5 end to end in the engine: confidence, switch margin, minimum hold and the low-confidence streak. */
class HysteresisScenarioTest {
    private final ExecutionFixture fixture = new ExecutionFixture();
    private final List<Long> changes = new ArrayList<>();

    private void engage() {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.answer(0, nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        assertEquals(KEEP, fixture.view(1).tactic());
    }

    /** Runs tick by tick answering each request, and records the tick of every change of tactic. */
    private void runRecording(int ticks, Function<Integer, Answer> answerForRequest) {
        for (var index = 0; index < ticks; index++) {
            var before = fixture.view(1).tactic();
            for (var request = 0; request < fixture.client.futures.size(); request++) {
                if (!fixture.client.futures.get(request).isDone()) {
                    var now = answerForRequest.apply(request);
                    fixture.answer(request, question -> question.situation() == Situation.NOW ? now
                            : prefer(FALL_BACK, 0.7, 0.9));
                }
            }
            fixture.run(1);
            if (!fixture.view(1).tactic().equals(before)) {
                changes.add(fixture.clock.tick - 1);
            }
        }
    }

    @Test
    void answersAlternatingNearTheMarginNeverMakeTheTacticOscillate() {
        engage();
        runRecording(200, request -> request % 2 == 0 ? between(PRESS, 0.5, KEEP, 0.35, 0.9)
                : between(KEEP, 0.5, PRESS, 0.35, 0.9));
        assertTrue(changes.isEmpty(), changes.toString());
        assertEquals(List.of(KEEP.value()), fixture.actions.signals(1));
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
    }

    @Test
    void aSufficientMarginSwitchesButNeverTwiceWithinTheMinimumInterval() {
        engage();
        runRecording(200, request -> request % 2 == 0 ? between(PRESS, 0.7, KEEP, 0.1, 0.9)
                : between(KEEP, 0.7, PRESS, 0.1, 0.9));
        assertTrue(changes.size() >= 2, changes.toString());
        for (var index = 1; index < changes.size(); index++) {
            assertTrue(changes.get(index) - changes.get(index - 1) >= 20, changes.toString());
        }
    }

    @Test
    void threeLowConfidenceAnswersLeadToLocalOnceTheIntentExpiresAndAnAdoptionResetsTheStreak() {
        engage();
        Function<QuestionSpec, Answer> doubtful = nowAndContingency(prefer(PRESS, 0.9, 0.2), prefer(FALL_BACK, 0.7, 0.9));
        fixture.runAnswering(70, doubtful);
        assertEquals(3, fixture.session(1).lowConfidenceStreak());
        assertEquals(ControlLevel.JEV, fixture.view(1).level(), "the current intent runs until it expires");
        fixture.runAnswering(40, doubtful);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertEquals(Optional.of(FallbackReason.LOW_CONFIDENCE), fixture.session(1).lastFallbackReason());
        fixture.runAnswering(25, nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        assertEquals(0, fixture.session(1).lowConfidenceStreak());
    }

    @Test
    void oneLowConfidenceAnswerYieldsToTheBaseAiAndAFullStreakGoesLocalUntilAnAdoptionRestoresTheLocalWait() {
        var short20 = new ExecutionFixture();
        short20.ready();
        short20.open(1, ExecutionFixture.profile(new DecisionTiming(20, 20, 20, 0.5, 0.2, 1), 0));
        short20.target(1, 1);
        short20.runUntilRequest(1);
        Function<QuestionSpec, Answer> keep = nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9));
        Function<QuestionSpec, Answer> doubtful = nowAndContingency(prefer(PRESS, 0.9, 0.2), prefer(FALL_BACK, 0.7, 0.9));
        short20.answer(0, keep);
        short20.run(1);
        short20.runUntilRequest(2);
        short20.answer(1, doubtful);
        short20.run(1);
        assertEquals(1, short20.session(1).lowConfidenceStreak());
        short20.run(8);
        assertEquals(ControlLevel.BASE, short20.view(1).level(), "one rejection with no valid intent yields to base");
        assertEquals(Optional.of(FallbackReason.EXPIRED), short20.session(1).lastFallbackReason());
        for (var request = 3; request <= 4; request++) {
            short20.runUntilRequest(request);
            short20.answer(request - 1, doubtful);
            short20.run(2);
        }
        assertEquals(3, short20.session(1).lowConfidenceStreak());
        assertEquals(ControlLevel.LOCAL, short20.view(1).level());
        assertEquals(Optional.of(FallbackReason.LOW_CONFIDENCE), short20.session(1).lastFallbackReason());
        short20.runUntilRequest(5);
        short20.answer(4, keep);
        short20.run(1);
        assertEquals(ControlLevel.JEV, short20.view(1).level());
        assertEquals(0, short20.session(1).lowConfidenceStreak());
        short20.runUntilRequest(6);
        short20.run(10);
        assertEquals(ControlLevel.LOCAL, short20.view(1).level(), "with the streak reset the expired intent waits locally");
        assertEquals(Optional.of(FallbackReason.EXPIRED), short20.session(1).lastFallbackReason());
    }
}
