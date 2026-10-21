package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.Movement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.TICK_NANOS;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.2: an accepted answer is published for the right generation within the tick, signalled once per tactic. */
class IntentPublicationTest {
    private final ExecutionFixture fixture = new ExecutionFixture();

    private void engage() {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.run(3);
    }

    @Test
    void anAdoptedIntentIsPublishedWithTheGenerationOfItsSessionAndExpiresInTheConfiguredNanoseconds() {
        fixture.open(1);
        fixture.sessions.registry.advanceGeneration(EngineFixtures.id(1));
        fixture.ready();
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.answer(0, nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        var acceptedAt = fixture.clock.nanos;
        fixture.run(1);
        var view = fixture.view(1);
        assertEquals(1, view.generation());
        assertEquals(new IntentView(1, ControlLevel.JEV, Movement.KEEP_DISTANCE, KEEP, 0.9f,
                acceptedAt + 100 * TICK_NANOS), view);
        assertTrue(view.controls(acceptedAt + 100 * TICK_NANOS - 1));
        assertFalse(view.controls(acceptedAt + 100 * TICK_NANOS));
    }

    @Test
    void theSignalIsSentOnlyWhenTheTacticChangesAndNeverOnARenewal() {
        engage();
        assertEquals(List.of(PRESS.value()), fixture.actions.signals(1), "the wait for the first answer is local");
        fixture.answer(0, nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        assertEquals(List.of(PRESS.value(), KEEP.value()), fixture.actions.signals(1));
        fixture.runUntilRequest(2);
        fixture.answer(1, nowAndContingency(prefer(KEEP, 0.6, 0.8), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        assertEquals(List.of(PRESS.value(), KEEP.value()), fixture.actions.signals(1));
        assertEquals(KEEP, fixture.view(1).tactic());
        assertEquals(0.8f, fixture.view(1).confidence());
    }

    @Test
    void aRenewedTacticNeverSignalsAgainAndRecastsOnlyWhenItsSkillIsReady() {
        engage();
        fixture.answer(0, nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        var castsBefore = fixture.actions.castTicks(1).size();
        fixture.runUntilRequest(2);
        fixture.run(3);
        fixture.answer(1, nowAndContingency(prefer(PRESS, 0.95, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        var castTick = fixture.clock.tick;
        assertEquals(castsBefore + 1, fixture.actions.castTicks(1).size(), "the adopted choice is cast in the same tick");
        assertTrue(fixture.session(1).intent().orElseThrow().skillConsumed());
        for (var request = 3; request <= 4; request++) {
            fixture.runUntilRequest(request);
            fixture.answer(request - 1, nowAndContingency(prefer(PRESS, 0.9, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
            fixture.run(3);
        }
        var cooldowns = (fixture.clock.tick - castTick) / ExecutionFixture.Actions.DEFAULT_COOLDOWN_TICKS;
        assertTrue(fixture.actions.castTicks(1).size() <= castsBefore + 1 + cooldowns,
                "only the readiness of the skill re-arms the choice, never an answer (DT-28)");
        assertEquals(List.of(PRESS.value(), KEEP.value(), PRESS.value()), fixture.actions.signals(1));
    }

    @Test
    void aLowConfidenceAnswerLengthensTheStreakAndKeepsTheCurrentIntent() {
        engage();
        fixture.answer(0, nowAndContingency(prefer(KEEP, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        fixture.runUntilRequest(2);
        fixture.answer(1, nowAndContingency(prefer(PRESS, 0.9, 0.2), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        var session = fixture.session(1);
        assertEquals(1, session.lowConfidenceStreak());
        assertEquals(KEEP, session.intent().orElseThrow().tactic());
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        assertEquals(1, fixture.count(FallbackReason.LOW_CONFIDENCE));
    }
}
