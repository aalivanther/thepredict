package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultySettings;
import akyrion.thepredict.domain.ParticipantRules;
import akyrion.thepredict.domain.PoiseMeter;
import akyrion.thepredict.domain.PoiseSettings;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TelegraphSettings;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure poise math and the rival filter of {@code takenFrom} (M23.7). */
class PoiseMeterTest {
    @Test
    void regenStartsAfterTheDelayAndAWindUpMultiplies() {
        var settings = PoiseSettings.defaults();
        assertEquals(0.10, PoiseMeter.value(0.10, 0, 60, settings), 1e-9);
        assertEquals(0.05, PoiseMeter.value(0.10, 0, 80, settings), 1e-9);
        assertEquals(0.0, PoiseMeter.value(0.05, 0, 200, settings), 0.0);
        assertEquals(0.15, PoiseMeter.hit(0.0, 0, 10, 0.10, true, 0, settings), 1e-9);
        assertEquals(0.10, PoiseMeter.hit(0.0, 0, 10, 0.10, false, 0, settings), 1e-9);
        assertEquals(0.0, PoiseMeter.hit(0.0, 0, 10, 0.10, false, 11, settings), 0.0);
        assertTrue(PoiseMeter.broken(0.15, settings));
        assertFalse(PoiseMeter.broken(0.149, settings));
    }

    @Test
    void aRivalWhoIsNotTheCurrentTargetFillsAndAStrangerDoesNot() {
        var harness = new EngineFixtures.Sessions();
        var id = UUID.randomUUID();
        var rival = UUID.randomUUID();
        var stranger = UUID.randomUUID();
        var current = UUID.randomUUID();
        harness.registry.open(id, profile());
        var session = harness.registry.find(id).orElseThrow();
        harness.encounters.assign(session, UUID.randomUUID(), rival, key -> false);
        session.recordTarget(current, 4);
        var events = events(harness);
        events.takenFrom(id, rival, 0.05, 20);
        assertEquals(0.05, session.poiseDamage(), 1e-9);
        events.takenFrom(id, stranger, 0.05, 21);
        assertEquals(0.05, session.poiseDamage(), 1e-9);
    }

    @Test
    void takenWithoutAnAttackerDoesNotFill() {
        var harness = new EngineFixtures.Sessions();
        var id = UUID.randomUUID();
        harness.registry.open(id, profile());
        var session = harness.registry.find(id).orElseThrow();
        events(harness).taken(id, 0.05, 20);
        assertEquals(0.0, session.poiseDamage(), 0.0);
    }

    private static OutcomeEvents events(EngineFixtures.Sessions harness) {
        return new OutcomeEvents(harness.registry, harness.encounters, new OutcomeMetrics(),
                new DecisionMetrics(() -> 0L));
    }

    private static TacticalProfile profile() {
        var base = EngineFixtures.profile("Guardian");
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), base.choices(),
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing(),
                ParticipantRules.DEFAULT, DifficultySettings.DEFAULT, base.stances(), TelegraphSettings.DEFAULT,
                Optional.of(PoiseSettings.defaults()));
    }
}
