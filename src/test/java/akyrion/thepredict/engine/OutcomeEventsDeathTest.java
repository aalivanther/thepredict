package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FightEventKind;
import akyrion.thepredict.domain.Situation;
import org.junit.jupiter.api.Test;

import static akyrion.thepredict.engine.ExecutionFixture.GUARD;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The death of the target closes the fight whether or not an outcome window is open (EFO-01), and a player worn down
 * by small hits counts as killed for the group clock (EFO-07).
 */
class OutcomeEventsDeathTest {
    private final ExecutionFixture fixture = new ExecutionFixture();

    private MobSession fighting() {
        fixture.ready();
        var session = fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.answer(0, question -> ExecutionFixture.prefer(question.situation() == Situation.NOW ? PRESS : GUARD,
                0.8, 0.9));
        fixture.run(1);
        fixture.outcomes.dealt(session.key().entityId(), EngineFixtures.player(1), 0.2, fixture.clock.tick);
        assertTrue(session.difficulty().clock().open(), "the hit on the target opened the fight");
        return session;
    }

    @Test
    void theTargetDyingAfterTheWindowClosedStillClosesTheFightAsTargetDied() {
        var session = fighting();
        fixture.run(OutcomeWindow.OUTCOME_WINDOW_TICKS + 2);
        assertTrue(session.outcomes().open().isEmpty(), "the window closed after its longest duration");
        var clock = session.difficulty().clock();
        assertTrue(clock.open(), "the fight goes on after the window");
        fixture.outcomes.participantDied(EngineFixtures.WORLD, EngineFixtures.player(1));
        assertFalse(clock.open());
        assertTrue(clock.targetDied());
        assertEquals(FightEventKind.TARGET_DIED, clock.closedKind().orElseThrow());
        assertEquals(FightEventKind.TARGET_DIED, clock.summary().orElseThrow().kind());
    }

    @Test
    void theDeathOfAnotherParticipantLeavesTheFightOpen() {
        var session = fighting();
        fixture.run(OutcomeWindow.OUTCOME_WINDOW_TICKS + 2);
        assertTrue(fixture.sessions.encounters.addParticipant(session.encounter().orElseThrow(),
                EngineFixtures.player(2)), "a second participant joins the encounter, so its death reaches the member");
        fixture.outcomes.participantDied(EngineFixtures.WORLD, EngineFixtures.player(2));
        assertTrue(session.difficulty().clock().open(), "only the death of the target of the fight closes it");
        assertFalse(session.difficulty().clock().targetDied());
    }

    @Test
    void aPlayerWornDownBySmallHitsEndsTheGroupClockAsTargetDied() {
        var session = fighting();
        var group = new GroupFightClock();
        session.groupFight(group, 0);
        for (var hit = 0; hit < 10; hit++) {
            fixture.outcomes.dealt(session.key().entityId(), EngineFixtures.player(1), 0.15, fixture.clock.tick);
            fixture.run(5);
        }
        assertEquals(FightEventKind.ENGAGED, group.last(0).orElseThrow(), "no single hit took the whole health");
        fixture.outcomes.participantDied(EngineFixtures.WORLD, EngineFixtures.player(1));
        assertEquals(FightEventKind.TARGET_DIED, group.last(0).orElseThrow());
        group.close();
        assertEquals(FightEventKind.TARGET_DIED, group.summary().orElseThrow().kind());
    }

    @Test
    void aGroupClockThatNeverEngagedThePlayerIgnoresItsDeath() {
        var group = new GroupFightClock();
        assertFalse(group.playerDied(EngineFixtures.player(1)));
        assertTrue(group.last(0).isEmpty());
        group.noteHit(0, EngineFixtures.player(1), 10L, false, "guardian_ai");
        assertFalse(group.playerDied(EngineFixtures.player(2)), "another player is not engaged");
        assertTrue(group.playerDied(EngineFixtures.player(1)));
    }
}
