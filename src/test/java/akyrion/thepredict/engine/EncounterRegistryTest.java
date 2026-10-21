package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static akyrion.thepredict.engine.EngineFixtures.OTHER_WORLD;
import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.far;
import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.near;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncounterRegistryTest {
    private static final Predicate<UUID> NOBODY_FIGHTS = entityId -> false;

    private final EngineFixtures.Sessions fixture = new EngineFixtures.Sessions();
    private final EncounterRegistry encounters = fixture.encounters;

    private Encounter assign(int mob, int target, Predicate<MobKey> withinFollowRange) {
        var session = fixture.registry.find(id(mob)).orElseGet(() -> fixture.open(mob));
        return encounters.assign(session, WORLD, player(target), withinFollowRange);
    }

    @Test
    void threeMobsAgainstTheSamePlayerShareOneEncounter() {
        var first = assign(1, 1, near());
        assertSame(first, assign(2, 1, near()));
        assertSame(first, assign(3, 1, near()));
        assertEquals(3, first.memberCount());
        assertEquals(1, encounters.size());
        assertEquals(Optional.of(first.id()), fixture.registry.find(id(3)).orElseThrow().encounter());
    }

    @Test
    void twoDistantGroupsAgainstDifferentPlayersFormTwoEncounters() {
        var west = assign(1, 1, near());
        assign(2, 1, near());
        var east = assign(3, 2, far());
        assign(4, 2, near());
        assertNotEquals(west.id(), east.id());
        assertEquals(2, west.memberCount());
        assertEquals(2, east.memberCount());
    }

    @Test
    void aSecondGroupOutOfRangeFormsItsOwnEncounterAndLaterMobsJoinTheMatchingOne() {
        var first = assign(1, 1, near());
        var second = assign(2, 1, far());
        assertNotEquals(first.id(), second.id());
        assertEquals(List.of(first, second), encounters.ofParticipant(WORLD, player(1)));
        assertSame(second, assign(3, 1, key -> key.entityId().equals(id(2))));
        assertSame(first, assign(4, 1, key -> key.entityId().equals(id(1))));
        assertEquals(2, encounters.size());
    }

    @Test
    void mobsBeyondTheMemberCapFillTheExistingEncountersInsteadOfFormingOneEach() {
        var counts = new ArrayList<Integer>();
        for (var mob = 1; mob <= 33; mob++) {
            assign(mob, 1, near());
            if (mob == 17 || mob == 18 || mob == 32 || mob == 33) {
                counts.add(encounters.size());
            }
        }
        assertEquals(List.of(2, 2, 2, 3), counts);
        var sizes = encounters.ofParticipant(WORLD, player(1)).stream().map(Encounter::memberCount).toList();
        assertEquals(List.of(16, 16, 1), sizes);
    }

    @Test
    void mobsOfTwoWorldsAgainstOnePlayerFormOneEncounterPerWorld() {
        encounters.assign(fixture.open(1), OTHER_WORLD, player(1), near());
        for (var mob = 2; mob <= 5; mob++) {
            encounters.assign(fixture.open(mob), WORLD, player(1), near());
        }
        assertEquals(2, encounters.size());
        assertEquals(1, encounters.ofParticipant(OTHER_WORLD, player(1)).size());
        assertEquals(4, encounters.ofParticipant(WORLD, player(1)).getFirst().memberCount());
    }

    @Test
    void closingTheFirstEncounterKeepsTheIndexOfTheOthersAndLeavesNoEmptyKey() {
        var first = assign(1, 1, near());
        var second = assign(2, 1, far());
        fixture.registry.close(id(1), FallbackReason.SESSION_INVALID);
        assertTrue(first.isClosed());
        assertEquals(List.of(second), encounters.ofParticipant(WORLD, player(1)));
        assertSame(second, assign(3, 1, near()));
        fixture.registry.close(id(2), FallbackReason.SESSION_INVALID);
        fixture.registry.close(id(3), FallbackReason.SESSION_INVALID);
        assertEquals(0, encounters.size());
        assertEquals(0, encounters.participantKeys());
    }

    @Test
    void anEncounterOfAnotherWorldIsNeverJoined() {
        var here = assign(1, 1, near());
        var there = encounters.assign(fixture.open(2), OTHER_WORLD, player(1), near());
        assertNotEquals(here.id(), there.id());
        assertEquals(OTHER_WORLD, there.worldId());
    }

    @Test
    void theMemberCapOpensASecondEncounter() {
        var first = assign(1, 1, near());
        for (var mob = 2; mob <= EncounterRegistry.MAX_MEMBERS; mob++) {
            assertSame(first, assign(mob, 1, near()));
        }
        var overflow = assign(EncounterRegistry.MAX_MEMBERS + 1, 1, near());
        assertNotEquals(first.id(), overflow.id());
        assertEquals(EncounterRegistry.MAX_MEMBERS, first.memberCount());
        assertEquals(1, overflow.memberCount());
    }

    @Test
    void theParticipantCapIsNeverExceeded() {
        var encounter = assign(1, 1, near());
        for (var player = 2; player <= EncounterRegistry.MAX_PARTICIPANTS; player++) {
            assertTrue(encounters.addParticipant(encounter.id(), player(player)));
        }
        assertFalse(encounters.addParticipant(encounter.id(), player(99)));
        assertTrue(encounters.addParticipant(encounter.id(), player(2)));
        assertEquals(EncounterRegistry.MAX_PARTICIPANTS, encounter.participants().size());
        assertTrue(encounters.ofParticipant(WORLD, player(99)).isEmpty());
        encounters.removeParticipant(encounter.id(), player(2));
        assertTrue(encounters.ofParticipant(WORLD, player(2)).isEmpty());
    }

    @Test
    void aLatePlayerGetsTheNextAliasAndAnAliasSurvivesLeavingAndComingBack() {
        var encounter = assign(1, 1, near());
        assertEquals(new ParticipantAlias(1), encounter.aliasFor(player(1), ParticipantKind.PLAYER));
        encounters.addParticipant(encounter.id(), player(2));
        assertEquals(new ParticipantAlias(2), encounter.aliasFor(player(2), ParticipantKind.PLAYER));
        encounters.removeParticipant(encounter.id(), player(1));
        encounters.addParticipant(encounter.id(), player(3));
        assertEquals(new ParticipantAlias(3), encounter.aliasFor(player(3), ParticipantKind.PLAYER));
        encounters.addParticipant(encounter.id(), player(1));
        assertEquals(new ParticipantAlias(1), encounter.aliasFor(player(1), ParticipantKind.PLAYER));
    }

    @Test
    void aMemberWhoseNewTargetBelongsToAnotherEncounterInRangeMovesAndTheEmptyOneCloses() {
        var alone = assign(1, 1, near());
        var group = assign(2, 2, far());
        assign(3, 2, near());
        var moved = assign(1, 2, near());
        assertSame(group, moved);
        assertEquals(3, group.memberCount());
        assertTrue(alone.isClosed());
        assertTrue(encounters.find(alone.id()).isEmpty());
        assertEquals(1, fixture.metrics.closedEncounters(SessionMetrics.EncounterEnd.NO_MEMBERS));
    }

    @Test
    void aMemberKeepsItsEncounterWhenItAlreadyHasTheTarget() {
        var encounter = assign(1, 1, near());
        encounters.addParticipant(encounter.id(), player(2));
        assertSame(encounter, assign(1, 2, far()));
        assertSame(encounter, assign(1, 1, far()));
        assertEquals(1, encounters.size());
    }

    @Test
    void identitiesGrowFromOneAndAreNeverReused() {
        var first = assign(1, 1, near());
        fixture.registry.close(id(1), FallbackReason.SESSION_INVALID);
        var second = assign(2, 2, near());
        var third = assign(3, 3, far());
        assertEquals(List.of(1L, 2L, 3L), List.of(first.id().value(), second.id().value(), third.id().value()));
        assertTrue(encounters.find(first.id()).isEmpty());
    }

    @Test
    void anEncounterClosesWhenItsCombatEndsAndStaysWhileAnyoneStillFights() {
        var encounter = assign(1, 1, near());
        assign(2, 1, near());
        fixture.registry.find(id(1)).orElseThrow().recordTarget(player(1), 3);
        assertFalse(encounters.closeIfFinished(encounter.id(), NOBODY_FIGHTS));
        fixture.registry.find(id(1)).orElseThrow().loseTarget();
        assertFalse(encounters.closeIfFinished(encounter.id(), player(1)::equals));
        assertFalse(encounters.closeIfFinished(encounter.id(), id(2)::equals));
        fixture.clock.tick = 300;
        assertTrue(encounters.closeIfFinished(encounter.id(), NOBODY_FIGHTS));
        assertTrue(encounter.isClosed());
        assertTrue(fixture.registry.find(id(1)).orElseThrow().encounter().isEmpty());
        assertEquals(1, fixture.metrics.closedEncounters(SessionMetrics.EncounterEnd.COMBAT_ENDED));
        assertEquals(300, fixture.metrics.averageEncounterTicks());
        assertTrue(encounters.closeIfFinished(encounter.id(), NOBODY_FIGHTS));
    }

    @Test
    void theCombatCheckMakesAtMostThirtyTwoCalls() {
        var encounter = assign(1, 1, near());
        for (var mob = 2; mob <= EncounterRegistry.MAX_MEMBERS; mob++) {
            assign(mob, 1, near());
        }
        for (var player = 2; player <= EncounterRegistry.MAX_PARTICIPANTS; player++) {
            encounters.addParticipant(encounter.id(), player(player));
        }
        var calls = new AtomicInteger();
        encounters.closeIfFinished(encounter.id(), entityId -> calls.incrementAndGet() < 0);
        assertEquals(32, calls.get());
    }

    @Test
    void closingDropsTheHistoryAliasesAndParticipantIndex() {
        var encounter = assign(1, 1, near());
        encounter.aliasFor(player(1), ParticipantKind.PLAYER);
        encounter.record(new EncounterEvent(5, EventType.MELEE_HIT, Optional.empty(), Optional.empty()));
        encounters.closeIfFinished(encounter.id(), NOBODY_FIGHTS);
        assertTrue(encounter.recentEvents().isEmpty());
        assertEquals(0, encounter.aliases().size());
        assertTrue(encounters.ofParticipant(WORLD, player(1)).isEmpty());
        assertEquals(0, encounters.participantKeys());
        assertTrue(encounter.members().isEmpty());
    }

    @Test
    void suspendingOrChangingWorldTakesTheSessionOutOfItsEncounter() {
        var encounter = assign(1, 1, near());
        assign(2, 1, near());
        fixture.registry.suspend(id(1));
        assertEquals(List.of(new MobKey(id(2), 0)), encounter.members());
        fixture.lifecycle.changedWorld(id(2));
        assertTrue(encounter.isClosed());
        assertTrue(fixture.registry.find(id(2)).orElseThrow().encounter().isEmpty());
    }

    @Test
    void membersFollowTheGenerationOfTheirSession() {
        var encounter = assign(1, 1, near());
        fixture.registry.advanceGeneration(id(1));
        assertEquals(List.of(new MobKey(id(1), 1)), encounter.members());
    }

    @Test
    void onlyActiveSessionsJoinAnEncounter() {
        var session = fixture.open(1);
        fixture.registry.suspend(id(1));
        assertThrows(IllegalStateException.class, () -> encounters.assign(session, WORLD, player(1), near()));
    }

    @Test
    void theHistoryKeepsTheLastSixteenEventsOldestFirst() {
        var encounter = assign(1, 1, near());
        for (var tick = 0; tick < 20; tick++) {
            encounter.record(new EncounterEvent(tick, EventType.RANGED_MISS, Optional.empty(), Optional.empty()));
        }
        var ticks = encounter.recentEvents().stream().map(EncounterEvent::tick).toList();
        assertEquals(Encounter.HISTORY_SIZE, ticks.size());
        assertEquals(4L, ticks.getFirst());
        assertEquals(19L, ticks.getLast());
    }

    @Test
    void sequencesGrowPerEncounterAndTheSafetyNetClosesEmptyEncounters() {
        var encounter = assign(1, 1, near());
        assertEquals(1, encounter.schedule().nextSequence());
        assertEquals(2, encounter.schedule().nextSequence());
        encounter.removeMember(id(1));
        assertEquals(1, encounters.closeEmpty());
        assertEquals(Set.of(), encounter.participants());
    }

    /** Places a mob against another mob, as a rival bound to ThePredict does (M17.6). */
    private Encounter assignAgainstMob(int mob, int targetMob, Predicate<MobKey> withinFollowRange) {
        var session = fixture.registry.find(id(mob)).orElseGet(() -> fixture.open(mob));
        return encounters.assign(session, WORLD, id(targetMob), withinFollowRange);
    }

    @Test
    void twoMobsOfThePredictThatTargetEachOtherFormTwoEncountersOneSideEach() {
        var first = assignAgainstMob(1, 2, near());
        var second = assignAgainstMob(2, 1, near());
        assertNotEquals(first.id(), second.id(), "B never joins the encounter that lists it as a participant");
        assertEquals(Set.of(id(2)), first.participants());
        assertEquals(Set.of(id(1)), second.participants());
        assertEquals(1, first.memberCount());
        assertEquals(1, second.memberCount());
        assertFalse(first.participants().contains(id(1)));
        assertFalse(second.participants().contains(id(2)));
    }

    @Test
    void twoAgainstTwoWithFactionsFormTwoEncountersOfTwoMembers() {
        Predicate<MobKey> allies12 = key -> key.entityId().equals(id(1)) || key.entityId().equals(id(2));
        Predicate<MobKey> allies34 = key -> key.entityId().equals(id(3)) || key.entityId().equals(id(4));
        var red = assignAgainstMob(1, 3, allies12);
        assertSame(red, assignAgainstMob(2, 3, allies12));
        var blue = assignAgainstMob(3, 1, allies34);
        assertSame(blue, assignAgainstMob(4, 1, allies34));
        assertNotEquals(red.id(), blue.id());
        assertEquals(2, red.memberCount());
        assertEquals(2, blue.memberCount());
        for (var encounter : List.of(red, blue)) {
            for (var member : encounter.members()) {
                assertFalse(encounter.isParticipant(member.entityId()), "no entity is member and participant");
            }
        }
    }

    @Test
    void aMobNeverJoinsAnEncounterThatListsItEvenWithinRangeAndWithRoom() {
        var hunters = assignAgainstMob(1, 9, near());
        encounters.addParticipant(hunters.id(), id(5));
        var own = assignAgainstMob(5, 9, near());
        assertNotEquals(hunters.id(), own.id());
        assertEquals(1, hunters.memberCount());
        assertTrue(hunters.isParticipant(id(5)));
    }

    @Test
    void theLastMemberIsStillAttachedWhenTheCloseIsHeard() {
        var seen = new ArrayList<Integer>();
        encounters.hearCloses(encounter -> seen.add(encounter.memberCount()));
        assign(1, 1, near());
        fixture.registry.close(id(1), FallbackReason.SESSION_INVALID);
        assertEquals(List.of(1), seen, "the listeners of the close see the whole fight (M27, M28)");
        assertEquals(0, encounters.size());
    }

    @Test
    void aSessionThatLeavesForGoodDropsItsTeachingSnapshot() {
        var board = new akyrion.thepredict.engine.distill.TeachingBoard();
        encounters.useTeaching(board, rows -> {
        });
        assign(1, 1, near());
        assign(2, 1, near());
        board.note(id(1).toString(), "guardian_ai", "r1:ALL", "ctx", "outcome", List.of("a"), List.of(), 0, true);
        board.note(id(2).toString(), "guardian_ai", "r1:ALL", "ctx", "outcome", List.of("a"), List.of(), 0, true);
        assertTrue(board.noted(id(1).toString()));
        fixture.registry.close(id(1), FallbackReason.SESSION_INVALID);
        assertFalse(board.noted(id(1).toString()), "no window of a closed session will close the snapshot");
        assertTrue(board.noted(id(2).toString()), "the member that stays keeps its snapshot");
    }
}
