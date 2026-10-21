package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TickLayerMode;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.near;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AgendaTest {
    @Test
    void theFirstPendingCauseIsKept() {
        var encounter = new Encounter(new EncounterId(1), new UUID(0, 1), 0);
        var agenda = new Agenda();
        agenda.markContextPending(encounter, 4, Agenda.PendingCause.SIGNATURE);
        agenda.markContextPending(encounter, 5, Agenda.PendingCause.CONTEXT);
        assertEquals(Agenda.PendingCause.SIGNATURE, encounter.pendingCause());
        agenda.markContextPending(encounter, 6, Agenda.PendingCause.PLAN_LOST);
        assertEquals(Agenda.PendingCause.PLAN_LOST, encounter.pendingCause());
        var fresh = new Encounter(new EncounterId(2), new UUID(0, 2), 0);
        agenda.markContextPending(fresh, 7, Agenda.PendingCause.PLAN_LOST);
        assertEquals(Agenda.PendingCause.PLAN_LOST, fresh.pendingCause());
    }

    /** M20.7: the heartbeat stays at the profile's value; only the wait to ask again after a mark shrinks. */
    @Test
    void aReactiveEncounterAsksAgainAfterTheReactiveIntervalAndKeepsItsHeartbeat() {
        var fixture = new EngineFixtures.Sessions();
        var encounter = fixture.encounters.assign(fixture.registry.open(id(1), reactive("Guardian", 6)), WORLD,
                player(1), near());
        fixture.encounters.assign(fixture.registry.open(id(2), reactive("Guardian", 4)), WORLD, player(1), near());
        assertEquals(6, encounter.reactiveIntervalTicks(), "the largest interval of the members governs");
        fixture.agenda.decided(encounter, 100);
        assertEquals(140, encounter.schedule().nextDecisionTick(), "the heartbeat is still 40 ticks");
        fixture.agenda.markContextPending(encounter, 103, Agenda.PendingCause.SIGNATURE);
        assertEquals(106, encounter.schedule().nextDecisionTick(), "last decision plus Timing.ReactiveInterval");
        fixture.agenda.decided(encounter, 106);
        fixture.agenda.markContextPending(encounter, 130);
        assertEquals(130, encounter.schedule().nextDecisionTick(), "a mark after the interval is due at once");
    }

    @Test
    void oneStandardMemberKeepsTheMinimumIntervalOfTheWholeEncounter() {
        var fixture = new EngineFixtures.Sessions();
        var encounter = fixture.encounters.assign(fixture.registry.open(id(1), reactive("Guardian", 6)), WORLD,
                player(1), near());
        fixture.encounters.assign(fixture.registry.open(id(2), EngineFixtures.profile("Guardian")), WORLD, player(1),
                near());
        assertEquals(-1, encounter.reactiveIntervalTicks(), "the saving mode of one member governs");
        fixture.agenda.decided(encounter, 100);
        fixture.agenda.markContextPending(encounter, 103, Agenda.PendingCause.SIGNATURE);
        assertEquals(120, encounter.schedule().nextDecisionTick(), "last decision plus minimumDecisionIntervalTicks");
    }

    private static TacticalProfile reactive(String mythicMobId, int intervalTicks) {
        var profile = EngineFixtures.profile(mythicMobId);
        var timing = profile.timing().withLayer(TickLayerMode.REACTIONS, ReactionScope.ALL, Set.of(), 0,
                DecisionCadence.REACTIVE, intervalTicks, 10);
        return profile.withOverlay(timing, profile.zone(), profile.group(), profile.patterns(), profile.decision(),
                profile.distillMode());
    }
}
