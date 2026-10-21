package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static akyrion.thepredict.engine.EngineFixtures.OTHER_WORLD;
import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.near;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextEventsTest {
    private final EngineFixtures.Sessions fixture = new EngineFixtures.Sessions();
    private final ContextEvents events = new ContextEvents(fixture.encounters, fixture.registry, fixture.agenda,
            fixture.clock);

    private Encounter encounter(int mob, int target) {
        var session = fixture.registry.find(id(mob)).orElseGet(() -> fixture.open(mob));
        var encounter = fixture.encounters.assign(session, WORLD, player(target), near());
        fixture.agenda.decided(encounter, fixture.clock.tick);
        return encounter;
    }

    @Test
    void aPlayerOutsideEveryEncounterCausesNoWork() {
        var encounter = encounter(1, 1);
        assertFalse(events.isParticipant(WORLD, player(2)));
        assertFalse(events.isParticipant(OTHER_WORLD, player(1)));
        assertEquals(0, events.participantChanged(WORLD, player(2)));
        assertEquals(0, events.participantEvent(WORLD, player(2), ParticipantKind.PLAYER, EventType.PEARL_THROWN, true));
        assertEquals(0, events.allyAssisted(WORLD, player(2), ParticipantKind.PLAYER, List.of(player(1))));
        assertFalse(events.memberEvent(id(9), player(1), ParticipantKind.PLAYER, EventType.MELEE_HIT, true));
        assertFalse(events.memberChanged(id(9)));
        assertEquals(0, events.metrics().pendingMarks());
        assertEquals(0, events.metrics().recordedEvents());
        assertTrue(encounter.recentEvents().isEmpty());
        assertFalse(encounter.contextPending());
    }

    @Test
    void anEventOfAParticipantEntersTheHistoryUnderItsAliasAndMarksPendingContext() {
        var encounter = encounter(1, 1);
        fixture.clock.tick = 7;
        assertEquals(1, events.participantEvent(WORLD, player(1), ParticipantKind.PLAYER, EventType.SHIELD_DISABLED, true));
        assertEquals(List.of(new EncounterEvent(7, EventType.SHIELD_DISABLED, Optional.of(new ParticipantAlias(1)),
                Optional.empty())), encounter.recentEvents());
        assertTrue(encounter.contextPending());
        assertEquals(1, events.metrics().recordedEvents());
    }

    @Test
    void aMissIsRecordedWithoutMarkingPendingContext() {
        var encounter = encounter(1, 1);
        events.participantEvent(WORLD, player(1), ParticipantKind.PLAYER, EventType.RANGED_MISS, false);
        assertEquals(1, encounter.recentEvents().size());
        assertFalse(encounter.contextPending());
    }

    @Test
    void theSameEventOfTheSameParticipantWithinTenTicksIsRecordedOnce() {
        var encounter = encounter(1, 1);
        fixture.encounters.addParticipant(encounter.id(), player(2));
        events.participantEvent(WORLD, player(1), ParticipantKind.PLAYER, EventType.PEARL_THROWN, true);
        fixture.clock.tick = ContextEvents.REPEAT_WINDOW_TICKS - 1;
        events.participantEvent(WORLD, player(1), ParticipantKind.PLAYER, EventType.PEARL_THROWN, true);
        events.participantEvent(WORLD, player(1), ParticipantKind.PLAYER, EventType.TOTEM_USED, true);
        events.participantEvent(WORLD, player(2), ParticipantKind.PLAYER, EventType.PEARL_THROWN, true);
        assertEquals(3, encounter.recentEvents().size());
        assertEquals(1, events.metrics().repeatedEvents());
        fixture.clock.tick = ContextEvents.REPEAT_WINDOW_TICKS;
        events.participantEvent(WORLD, player(1), ParticipantKind.PLAYER, EventType.PEARL_THROWN, true);
        assertEquals(4, encounter.recentEvents().size());
    }

    @Test
    void manyMarksInOneTickMoveTheEncounterOnce() {
        var encounter = encounter(1, 1);
        fixture.clock.tick = 30;
        for (var index = 0; index < 100; index++) {
            events.participantChanged(WORLD, player(1));
        }
        assertEquals(100, events.metrics().pendingMarks());
        assertEquals(List.of(encounter.id()), fixture.agenda.due(30, 10));
    }

    @Test
    void theHistoryRingNeverExceedsSixteenEvents() {
        var encounter = encounter(1, 1);
        for (var index = 0; index < 40; index++) {
            fixture.clock.tick = index * (long) ContextEvents.REPEAT_WINDOW_TICKS;
            events.participantEvent(WORLD, player(1), ParticipantKind.PLAYER, EventType.POTION_CONSUMED, false);
        }
        assertEquals(Encounter.HISTORY_SIZE, encounter.recentEvents().size());
        assertEquals(390, encounter.recentEvents().getLast().tick());
        assertEquals(40, events.metrics().recordedEvents());
    }

    @Test
    void aMemberHittingAParticipantIsRecordedWithTheMobAndMarksOnlyWhenAsked() {
        var encounter = encounter(1, 1);
        assertTrue(events.memberEvent(id(1), player(1), ParticipantKind.PLAYER, EventType.MELEE_HIT, false));
        var event = encounter.recentEvents().getFirst();
        assertEquals(Optional.of(new ParticipantAlias(1)), event.participant());
        assertEquals(Optional.of(fixture.registry.find(id(1)).orElseThrow().key()), event.mob());
        assertFalse(encounter.contextPending());
        assertTrue(events.memberEvent(id(1), player(1), ParticipantKind.PLAYER, EventType.MELEE_BLOCKED, true));
        assertTrue(encounter.contextPending());
    }

    @Test
    void aMemberEventWithAPlayerOutsideTheEncounterRecordsNothingButMayMarkPending() {
        var encounter = encounter(1, 1);
        assertTrue(events.memberEvent(id(1), player(5), ParticipantKind.PLAYER, EventType.MELEE_BLOCKED, true));
        assertTrue(encounter.recentEvents().isEmpty());
        assertTrue(encounter.contextPending());
    }

    @Test
    void aSuspendedMobIsNoLongerAMember() {
        encounter(1, 1);
        fixture.registry.suspend(id(1));
        assertFalse(events.memberEvent(id(1), player(1), ParticipantKind.PLAYER, EventType.MELEE_HIT, true));
        assertFalse(events.memberChanged(id(1)));
        assertFalse(events.memberPathfind(id(1)));
    }

    @Test
    void anAttackOnAMemberMarksItsEncounterAndPathsAreOnlyCounted() {
        var encounter = encounter(1, 1);
        assertTrue(events.memberPathfind(id(1)));
        assertFalse(encounter.contextPending());
        assertEquals(1, events.metrics().memberPathfinds());
        assertTrue(events.memberChanged(id(1)));
        assertTrue(encounter.contextPending());
        fixture.open(2);
        assertFalse(events.memberPathfind(id(2)));
        assertEquals(1, events.metrics().memberPathfinds());
    }

    @Test
    void assistingAnotherParticipantIsRecordedForTheSourceOnly() {
        var encounter = encounter(1, 1);
        assertEquals(0, events.allyAssisted(WORLD, player(1), ParticipantKind.PLAYER, List.of(player(1))));
        assertEquals(0, events.allyAssisted(WORLD, player(1), ParticipantKind.PLAYER, List.of(player(3))));
        fixture.encounters.addParticipant(encounter.id(), player(3));
        assertEquals(1, events.allyAssisted(WORLD, player(1), ParticipantKind.PLAYER, List.of(player(1), player(3))));
        var event = encounter.recentEvents().getFirst();
        assertEquals(EventType.ALLY_ASSISTED, event.type());
        assertEquals(Optional.of(new ParticipantAlias(1)), event.participant());
        assertTrue(encounter.contextPending());
    }

    @Test
    void anEventReachesEveryEncounterOfThePlayerInThatWorld() {
        var first = encounter(1, 1);
        var session = fixture.open(2);
        var second = fixture.encounters.assign(session, WORLD, player(1), member -> false);
        assertEquals(2, events.participantEvent(WORLD, player(1), ParticipantKind.PLAYER, EventType.TOTEM_USED, true));
        assertEquals(1, first.recentEvents().size());
        assertEquals(1, second.recentEvents().size());
        assertEquals(0, events.participantEvent(OTHER_WORLD, player(1), ParticipantKind.PLAYER, EventType.TOTEM_USED, true));
    }

    @Test
    void twoMobsHittingOnePlayerWithinTenTicksAreTwoEventsButOneMobTwiceIsOne() {
        var encounter = encounter(1, 1);
        assertEquals(encounter, fixture.encounters.assign(fixture.open(2), WORLD, player(1), near()));
        events.memberEvent(id(1), player(1), ParticipantKind.PLAYER, EventType.MELEE_HIT, false);
        events.memberEvent(id(2), player(1), ParticipantKind.PLAYER, EventType.MELEE_HIT, false);
        fixture.clock.tick = ContextEvents.REPEAT_WINDOW_TICKS - 1;
        events.memberEvent(id(1), player(1), ParticipantKind.PLAYER, EventType.MELEE_HIT, false);
        assertEquals(2, encounter.recentEvents().size());
        assertEquals(1, events.metrics().repeatedEvents());
    }

    /** An encounter of a mob against another living entity, a zombie with the UUID of mob {@code target} (M17). */
    private Encounter encounterAgainstEntity(int mob, int target) {
        var session = fixture.registry.find(id(mob)).orElseGet(() -> fixture.open(mob));
        var encounter = fixture.encounters.assign(session, WORLD, id(target), near());
        fixture.agenda.decided(encounter, fixture.clock.tick);
        return encounter;
    }

    @Test
    void anEntityParticipantIsRecordedUnderAnAliasOfItsKind() {
        var encounter = encounterAgainstEntity(1, 50);
        fixture.clock.tick = 4;
        assertTrue(events.memberEvent(id(1), id(50), ParticipantKind.ENTITY, EventType.MELEE_HIT, false));
        assertEquals(List.of(new EncounterEvent(4, EventType.MELEE_HIT, Optional.of(new ParticipantAlias(
                ParticipantKind.ENTITY, 1)), Optional.of(fixture.registry.find(id(1)).orElseThrow().key()))),
                encounter.recentEvents());
        assertEquals(1, events.participantEvent(WORLD, id(50), ParticipantKind.ENTITY, EventType.RANGED_MISS, false));
        assertEquals("entity_1", encounter.recentEvents().get(1).participant().orElseThrow().text());
    }

    @Test
    void theDeathOrRemovalOfAParticipantMarksItsEncountersPendingInConstantTime() {
        var encounter = encounterAgainstEntity(1, 50);
        assertEquals(1, events.participantGone(WORLD, id(50)));
        assertTrue(encounter.contextPending());
        assertEquals(1, events.metrics().pendingMarks());
        assertEquals(0, events.participantGone(WORLD, id(51)), "an entity outside every encounter does no work");
        assertEquals(0, events.participantGone(OTHER_WORLD, id(50)));
        assertEquals(1, events.metrics().pendingMarks());
        assertTrue(encounter.recentEvents().isEmpty(), "gone is not a recorded event");
    }
}
