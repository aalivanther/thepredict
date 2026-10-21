package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static akyrion.thepredict.domain.DomainFixtures.ENCOUNTER;
import static akyrion.thepredict.domain.DomainFixtures.MOB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatFactsTest {
    static ParticipantFacts participant(int index) {
        return new ParticipantFacts(new ParticipantAlias(index), HealthBand.HEALTHY, ArmorBand.MEDIUM, Set.of(), index);
    }

    static MobFacts mob(MobKey key) {
        return new MobFacts(key, HealthBand.HEALTHY, Optional.empty(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    static MobFacts mobTargeting(MobKey key, ParticipantAlias alias) {
        return new MobFacts(key, HealthBand.HEALTHY, Optional.of(new TargetRelation(alias, DistanceBand.MELEE,
                Trend.CLOSING, true, true, false, true, false)), Set.of(), Set.of(), Set.of(), Set.of());
    }

    static EncounterEvent event(long tick) {
        return new EncounterEvent(tick, EventType.MELEE_HIT, Optional.empty(), Optional.empty());
    }

    private static List<MobFacts> mobs(int count) {
        var mobs = new ArrayList<MobFacts>();
        for (var index = 0; index < count; index++) {
            mobs.add(mob(new MobKey(new UUID(0, index), 0)));
        }
        return mobs;
    }

    private static List<ParticipantFacts> participants(int count) {
        var participants = new ArrayList<ParticipantFacts>();
        for (var index = 1; index <= count; index++) {
            participants.add(participant(index));
        }
        return participants;
    }

    private static List<EncounterEvent> events(int count) {
        var events = new ArrayList<EncounterEvent>();
        for (var index = 0; index < count; index++) {
            events.add(event(index));
        }
        return events;
    }

    private static CombatFacts facts(List<MobFacts> mobs, List<ParticipantFacts> participants, List<EncounterEvent> events) {
        return new CombatFacts(ENCOUNTER, 100, 5_000, mobs, participants, events, 12);
    }

    @Test
    void exactCapsAreAccepted() {
        var facts = facts(mobs(CombatFacts.MAX_MOBS), participants(CombatFacts.MAX_PARTICIPANTS),
                events(CombatFacts.MAX_EVENTS));
        assertEquals(32, facts.mobs().size());
        assertEquals(16, facts.participants().size());
        assertEquals(32, facts.recentEvents().size());
    }

    @Test
    void exceededCapsAreRejected() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> facts(mobs(33), List.of(), List.of()))
                .getMessage().contains("mobs"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> facts(List.of(), participants(17), List.of()))
                .getMessage().contains("participants"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> facts(List.of(), List.of(), events(33)))
                .getMessage().contains("recentEvents"));
    }

    @Test
    void duplicateAliasIsRejected() {
        var twin = new ParticipantFacts(new ParticipantAlias(1), HealthBand.WOUNDED, ArmorBand.LIGHT, Set.of(), 2);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> facts(List.of(), List.of(participant(1), twin), List.of())).getMessage().contains("alias"));
    }

    @Test
    void duplicateThreatRankIsRejected() {
        var sameRank = new ParticipantFacts(new ParticipantAlias(2), HealthBand.WOUNDED, ArmorBand.LIGHT, Set.of(), 1);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> facts(List.of(), List.of(participant(1), sameRank), List.of())).getMessage().contains("threatRank"));
    }

    @Test
    void targetMustBeAParticipant() {
        var lost = mobTargeting(MOB, new ParticipantAlias(5));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> facts(List.of(lost), List.of(participant(1)), List.of())).getMessage().contains("player_5"));
    }

    @Test
    void duplicateMobSessionIsRejected() {
        var once = mob(MOB);
        assertThrows(IllegalArgumentException.class, () -> facts(List.of(once, once), List.of(), List.of()));
    }

    @Test
    void absentGroupPartsAreTheirNoneValuesAndANullIsRejected() {
        var plain = facts(List.of(), List.of(), List.of());
        assertEquals(GroupRelations.NONE, plain.relations());
        assertEquals(GroupBrief.NONE, plain.brief());
        assertThrows(NullPointerException.class, () -> plain.withGroup(null, GroupBrief.NONE));
        assertThrows(NullPointerException.class, () -> plain.withGroup(GroupRelations.NONE, null));
        assertThrows(NullPointerException.class, () -> new GroupRelations(null, Optional.empty(), false));
        assertThrows(NullPointerException.class, () -> new GroupRelations(Optional.empty(), null, true));
    }

    @Test
    void negativeTickAndSecondsAreRejected() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new CombatFacts(ENCOUNTER, -1, 0, List.of(),
                List.of(), List.of(), 0)).getMessage().contains("captureTick"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new CombatFacts(ENCOUNTER, 0, 0, List.of(),
                List.of(), List.of(), -1)).getMessage().contains("combatSeconds"));
    }

    @Test
    void inputCollectionsModifiedAfterConstructionDoNotChangeTheRecord() {
        var mobs = new ArrayList<>(List.of(mobTargeting(MOB, new ParticipantAlias(1))));
        var participants = new ArrayList<>(List.of(participant(1)));
        var events = new ArrayList<>(List.of(event(3)));
        var facts = facts(mobs, participants, events);
        mobs.clear();
        participants.add(participant(2));
        events.clear();
        assertEquals(1, facts.mobs().size());
        assertEquals(1, facts.participants().size());
        assertEquals(1, facts.recentEvents().size());
        assertThrows(UnsupportedOperationException.class, () -> facts.mobs().clear());
    }

    @Test
    void mobLookupFindsOnlyTheExactSession() {
        var facts = facts(List.of(mob(MOB)), List.of(), List.of());
        assertTrue(facts.mob(MOB).isPresent());
        assertTrue(facts.mob(new MobKey(MOB.entityId(), MOB.generation() + 1)).isEmpty());
    }
}
