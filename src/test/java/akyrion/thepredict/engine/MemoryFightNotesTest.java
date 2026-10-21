package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.Situation;
import org.junit.jupiter.api.Test;

import static akyrion.thepredict.engine.ExecutionFixture.GUARD;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each player of an encounter is judged by its own death (EFO-06): a boss that kills A and is then killed by B leaves
 * a loss in the memory of A and a win in the memory of B, not a loss for both.
 */
class MemoryFightNotesTest {
    private final ExecutionFixture fixture = new ExecutionFixture();

    @Test
    void thePlayerWhoSurvivedAndKilledTheBossIsNotGivenTheDeathOfTheOther() {
        fixture.ready();
        var boss = fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.answer(0, question -> ExecutionFixture.prefer(question.situation() == Situation.NOW ? PRESS : GUARD,
                0.8, 0.9));
        fixture.run(1);
        var first = EngineFixtures.player(1);
        var second = EngineFixtures.player(2);
        var encounter = fixture.sessions.encounters.find(boss.encounter().orElseThrow()).orElseThrow();
        assertTrue(fixture.sessions.encounters.addParticipant(encounter.id(), second));
        var mob = boss.key().entityId();
        var tick = fixture.clock.tick;
        fixture.outcomes.dealt(mob, first, 0.6, tick);
        fixture.outcomes.dealt(mob, first, 0.4, tick + 10);
        fixture.outcomes.participantDied(EngineFixtures.WORLD, first);
        boss.recordTarget(second, 3.0);
        fixture.outcomes.takenFrom(mob, second, 0.5, 20.0, tick + 40);
        fixture.outcomes.takenFrom(mob, second, 0.5, 20.0, tick + 50);
        fixture.outcomes.died(mob, tick + 50);
        assertTrue(boss.sawDeathOf(first));
        assertFalse(boss.sawDeathOf(second));
        var notes = new MemoryFightNotes();
        var survivor = notes.digests(encounter, second, GroupMode.OFF, 1_000L, MemorySettings.DEFAULT);
        assertEquals(1, survivor.size());
        assertNotEquals(FightOutcome.LOSS, survivor.getFirst().outcome(), "the death of the first player is not his");
        assertEquals(FightOutcome.WIN, survivor.getFirst().outcome(), "he took the whole health of the boss");
        var fallen = notes.digests(encounter, first, GroupMode.OFF, 1_000L, MemorySettings.DEFAULT);
        assertEquals(FightOutcome.LOSS, fallen.getFirst().outcome(), "the boss was fighting him when he died");
    }

    @Test
    void aSessionForgetsTheDeathsOfItsLastEncounterWhenItJoinsAnother() {
        var session = fixture.open(1);
        var dead = EngineFixtures.player(3);
        session.joinEncounter(new akyrion.thepredict.domain.EncounterId(7));
        session.noteParticipantDeath(dead, true);
        assertTrue(session.engagedAtDeathOf(dead));
        session.joinEncounter(new akyrion.thepredict.domain.EncounterId(7));
        assertTrue(session.sawDeathOf(dead), "the same encounter keeps its deaths");
        session.joinEncounter(new akyrion.thepredict.domain.EncounterId(8));
        assertFalse(session.sawDeathOf(dead));
        for (var player = 0; player < MobSession.DEATHS_KEPT + 4; player++) {
            session.noteParticipantDeath(EngineFixtures.player(100 + player), false);
        }
        assertTrue(session.sawDeathOf(EngineFixtures.player(100 + MobSession.DEATHS_KEPT - 1)));
        assertFalse(session.sawDeathOf(EngineFixtures.player(100 + MobSession.DEATHS_KEPT)), "the cap holds");
    }
}
