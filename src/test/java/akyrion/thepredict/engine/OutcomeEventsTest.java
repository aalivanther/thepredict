package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.Situation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static akyrion.thepredict.engine.ExecutionFixture.GUARD;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The outcome windows of M16.8 inside the whole engine: when they open and close and what they count (rule P2). */
class OutcomeEventsTest {
    private final ExecutionFixture fixture = new ExecutionFixture();

    private MobSession pressing() {
        fixture.ready();
        var session = fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.answer(0, question -> ExecutionFixture.prefer(question.situation() == Situation.NOW ? PRESS : GUARD,
                0.8, 0.9));
        fixture.run(1);
        return session;
    }

    @Test
    void anAdoptedIntentOpensAWindowAndARenewalKeepsIt() {
        var session = pressing();
        var window = session.outcomes().open().orElseThrow();
        assertEquals(ControlLevel.JEV, session.intent().orElseThrow().level());
        fixture.runAnswering(10, question -> ExecutionFixture.prefer(question.situation() == Situation.NOW ? PRESS
                : GUARD, 0.8, 0.9));
        assertTrue(session.outcomes().open().filter(open -> open == window).isPresent(), "renewals keep the window");
        assertTrue(session.recentOutcomes().stream().noneMatch(summary -> summary.level() == ControlLevel.JEV));
    }

    @Test
    void aWindowClosesAfterSixtyTicksWithWhatTheMemberDealtAndTook() {
        var session = pressing();
        var mob = session.key().entityId();
        var start = fixture.clock.tick;
        assertTrue(fixture.actions.casts(1) > 0, "the strike was cast");
        fixture.outcomes.dealt(mob, EngineFixtures.player(1), 0.2, start);
        fixture.outcomes.taken(mob, 0.05, start);
        fixture.reader.distances.put(mob, DistanceBand.MELEE);
        fixture.run(OutcomeWindow.OUTCOME_WINDOW_TICKS + 2);
        var closed = session.recentOutcomes().stream().filter(summary -> summary.level() == ControlLevel.JEV)
                .findFirst().orElseThrow();
        assertEquals(PRESS, closed.tactic());
        assertEquals(0.2, closed.dealt(), 1e-9);
        assertEquals(0.05, closed.taken(), 1e-9);
        assertEquals(1, closed.hits(), "damage to the target right after a damaging cast");
        assertTrue(closed.reached(), "the approach saw its target in melee");
        assertTrue(closed.endTick() - closed.startTick() <= OutcomeWindow.OUTCOME_WINDOW_TICKS);
        assertTrue(fixture.outcomes.metrics().windows(ControlLevel.JEV, closed.movement()) >= 1);
    }

    @Test
    void anIntentThatEndsClosesItsWindowAndTheDeathOfTheMobClosesItWithTheDeath() {
        var session = pressing();
        fixture.outcomes.participantDied(EngineFixtures.WORLD, EngineFixtures.player(1));
        assertTrue(session.outcomes().open().isPresent(), "the death of the target is noted, the window goes on");
        fixture.outcomes.died(session.key().entityId(), fixture.clock.tick);
        assertTrue(session.outcomes().open().isEmpty());
        var last = session.recentOutcomes().getLast();
        assertTrue(last.mobDied());
        assertTrue(last.targetDied());
        assertFalse(fixture.outcomes.tracks(session.key().entityId()));
    }

    @Test
    void theDeathOfAParticipantReachesOnlyTheWindowsOfItsEncountersInItsWorld() {
        var session = pressing();
        fixture.outcomes.participantDied(new UUID(77, 77), EngineFixtures.player(1));
        fixture.outcomes.participantDied(EngineFixtures.WORLD, EngineFixtures.player(2));
        fixture.outcomes.died(EngineFixtures.player(1), fixture.clock.tick);
        fixture.outcomes.died(session.key().entityId(), fixture.clock.tick);
        var last = session.recentOutcomes().getLast();
        assertTrue(last.mobDied());
        assertFalse(last.targetDied(), "another world, another player, or a player reported as a member");
    }

    @Test
    void aDropToTheBaseAiClosesTheWindow() {
        var session = pressing();
        var control = new IntentControl(fixture.sessions.board, fixture.actions, fixture.metrics,
                new ExecutionLog(line -> {
                }, false), fixture.outcomes, DecisionTap.NONE);
        control.toBase(session, FallbackReason.EXPIRED, fixture.clock.tick);
        assertTrue(session.outcomes().open().isEmpty());
        assertEquals(ControlLevel.JEV, session.recentOutcomes().getLast().level());
    }

    @Test
    void eventsAboutMobsWithoutAWindowChangeNothing() {
        fixture.outcomes.dealt(EngineFixtures.id(42), EngineFixtures.player(1), 0.5, 1);
        fixture.outcomes.taken(EngineFixtures.id(42), 0.5, 1);
        fixture.outcomes.died(EngineFixtures.id(42), 1);
        assertTrue(fixture.outcomes.metrics().byLevel().isEmpty());
    }

    @Test
    void aFiredContingencyIsCountedAndItsWindowWithAPositiveEffectMakesItEffective() {
        var session = pressing();
        assertTrue(session.intent().orElseThrow().contingencies().containsKey(Situation.IF_TARGET_IN_MELEE));
        fixture.reader.distances.put(session.key().entityId(), DistanceBand.MELEE);
        fixture.run(2);
        assertEquals(GUARD, session.intent().orElseThrow().tactic());
        assertEquals(1, fixture.metrics.total(DecisionMetrics.Counter.CONTINGENCIES_FIRED));
        fixture.outcomes.dealt(session.key().entityId(), EngineFixtures.player(1), 0.3, fixture.clock.tick);
        fixture.run(OutcomeWindow.OUTCOME_WINDOW_TICKS + 2);
        assertEquals(1, fixture.metrics.total(DecisionMetrics.Counter.CONTINGENCIES_EFFECTIVE));
        assertTrue(fixture.metrics.total(DecisionMetrics.Counter.CONTINGENCIES_ASKED) >= 1);
    }
}
