package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.MobSighting;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.ParticipantKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static akyrion.thepredict.engine.EngineFixtures.OTHER_WORLD;
import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.near;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncounterCaptureTest {
    private final EngineFixtures.Sessions fixture = new EngineFixtures.Sessions();
    private final List<CaptureContext> contexts = new ArrayList<>();
    private BiFunction<List<MobKey>, CaptureContext, Optional<CombatFacts>> behaviour = (members, context) -> Optional.empty();
    private final CombatReader reader = new CombatReader() {
        @Override
        public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context) {
            contexts.add(context);
            return behaviour.apply(members, context);
        }

        @Override
        public Optional<MobProbe> probe(MobKey mob) {
            return Optional.empty();
        }

        @Override
        public boolean withinFollowRange(MobKey mob, MobKey member) {
            return true;
        }

        @Override
        public boolean inCombat(UUID entityId) {
            return false;
        }

        @Override
        public boolean isActive(UUID entityId) {
            return true;
        }

        @Override
        public Optional<ThreatTableState> threatTable(MobKey mob) {
            return Optional.of(ThreatTableState.DISABLED);
        }
    };
    private final DecisionMetrics metrics = new DecisionMetrics(() -> 0L);
    private final EncounterCapture capture = new EncounterCapture(reader, fixture.registry, fixture.encounters,
            fixture.lifecycle, fixture.clock, metrics);

    private Encounter encounter(int mob, int target) {
        var session = fixture.registry.find(id(mob)).orElseGet(() -> fixture.open(mob));
        return fixture.encounters.assign(session, WORLD, player(target), near());
    }

    private static CombatFacts facts(EncounterId id, List<MobKey> members, CaptureContext context,
                                     boolean reachable, UUID... players) {
        var participants = new ArrayList<ParticipantFacts>();
        for (var index = 0; index < players.length; index++) {
            participants.add(new ParticipantFacts(context.aliasFor().apply(players[index], ParticipantKind.PLAYER),
                    HealthBand.HEALTHY, ArmorBand.LIGHT, Set.of(), index + 1));
        }
        var mobs = new ArrayList<MobFacts>();
        for (var member : members) {
            var relation = players.length == 0 ? Optional.<TargetRelation>empty() : Optional.of(new TargetRelation(
                    participants.getFirst().alias(), DistanceBand.MELEE, Trend.STEADY, reachable, true, true, false, false));
            mobs.add(new MobFacts(member, HealthBand.HEALTHY, relation, Set.of(), Set.of(), Set.of(), Set.of()));
        }
        return new CombatFacts(id, 0, 0, mobs, participants, context.recentEvents(), 0);
    }

    @Test
    void anUnknownOrEmptyEncounterIsNeverHandedToTheReader() {
        assertTrue(capture.capture(new EncounterId(99), Set.of()).isEmpty());
        var encounter = encounter(1, 1);
        fixture.registry.close(id(1), FallbackReason.SESSION_INVALID);
        assertTrue(capture.capture(encounter.id(), Set.of()).isEmpty());
        assertTrue(contexts.isEmpty());
    }

    @Test
    void perceivedPlayersGetTheEncounterAliasesAndJoinTheParticipantIndex() {
        var encounter = encounter(1, 1);
        behaviour = (members, context) -> Optional.of(facts(encounter.id(), members, context, true, player(1), player(2)));
        var facts = capture.capture(encounter.id(), Set.of()).orElseThrow();
        assertEquals(List.of(new ParticipantAlias(1), new ParticipantAlias(2)),
                facts.participants().stream().map(ParticipantFacts::alias).toList());
        assertTrue(encounter.isParticipant(player(2)));
        assertEquals(List.of(encounter), fixture.encounters.ofParticipant(WORLD, player(2)));
    }

    @Test
    void aParticipantNoLongerPerceivedLeavesTheIndexButKeepsItsAlias() {
        var encounter = encounter(1, 1);
        behaviour = (members, context) -> Optional.of(facts(encounter.id(), members, context, true, player(1), player(2)));
        capture.capture(encounter.id(), Set.of());
        behaviour = (members, context) -> Optional.of(facts(encounter.id(), members, context, true, player(2)));
        capture.capture(encounter.id(), Set.of());
        assertFalse(encounter.isParticipant(player(1)));
        assertTrue(encounter.isParticipant(player(2)));
        assertTrue(fixture.encounters.ofParticipant(WORLD, player(1)).isEmpty());
        assertEquals(new ParticipantAlias(1), encounter.aliasFor(player(1), ParticipantKind.PLAYER));
    }

    @Test
    void aCaptureWithoutFactsLeavesTheParticipantsAlone() {
        var encounter = encounter(1, 1);
        assertTrue(capture.capture(encounter.id(), Set.of()).isEmpty());
        assertTrue(encounter.isParticipant(player(1)));
    }

    @Test
    void sightingsUpdateTheTargetAndItsDistanceOrDropThem() {
        var encounter = encounter(1, 1);
        var key = fixture.registry.find(id(1)).orElseThrow().key();
        behaviour = (members, context) -> {
            context.sightings().accept(new MobSighting(key, WORLD, Optional.of(player(1)), OptionalDouble.of(4.5)));
            return Optional.empty();
        };
        capture.capture(encounter.id(), Set.of());
        var session = fixture.registry.find(id(1)).orElseThrow();
        assertEquals(Optional.of(player(1)), session.lastTargetId());
        assertEquals(OptionalDouble.of(4.5), session.lastTargetDistanceBlocks());
        behaviour = (members, context) -> {
            context.sightings().accept(new MobSighting(key, WORLD, Optional.empty(), OptionalDouble.empty()));
            return Optional.empty();
        };
        capture.capture(encounter.id(), Set.of());
        assertTrue(session.lastTargetId().isEmpty());
        assertTrue(session.lastTargetDistanceBlocks().isEmpty());
    }

    @Test
    void aMemberSeenInAnotherWorldChangesWorldAndLeavesTheEncounter() {
        var encounter = encounter(1, 1);
        encounter(2, 1);
        var key = fixture.registry.find(id(1)).orElseThrow().key();
        behaviour = (members, context) -> {
            context.sightings().accept(new MobSighting(key, OTHER_WORLD, Optional.empty(), OptionalDouble.empty()));
            return Optional.empty();
        };
        capture.capture(encounter.id(), Set.of());
        var session = fixture.registry.find(id(1)).orElseThrow();
        assertEquals(key.generation() + 1, session.key().generation());
        assertTrue(session.encounter().isEmpty());
        assertEquals(1, encounter.memberCount());
        assertTrue(session.installPending());
    }

    @Test
    void aSightingOfAnOlderGenerationChangesNothing() {
        var encounter = encounter(1, 1);
        var stale = new MobKey(id(1), 7);
        behaviour = (members, context) -> {
            context.sightings().accept(new MobSighting(stale, OTHER_WORLD, Optional.empty(), OptionalDouble.empty()));
            return Optional.empty();
        };
        capture.capture(encounter.id(), Set.of());
        assertEquals(0, fixture.registry.find(id(1)).orElseThrow().key().generation());
        assertEquals(1, encounter.memberCount());
    }

    @Test
    void theContextCarriesTheWorldHistoryDistancesPermissionAndLastUnreachableTargets() {
        var encounter = encounter(1, 1);
        var key = fixture.registry.find(id(1)).orElseThrow().key();
        encounter.record(new EncounterEvent(3, EventType.PEARL_THROWN, Optional.of(encounter.aliasFor(player(1), ParticipantKind.PLAYER)),
                Optional.empty()));
        behaviour = (members, context) -> {
            context.sightings().accept(new MobSighting(key, WORLD, Optional.of(player(1)), OptionalDouble.of(6.0)));
            return Optional.of(facts(encounter.id(), members, context, false, player(1)));
        };
        capture.capture(encounter.id(), Set.of(key));
        var first = contexts.getFirst();
        assertEquals(WORLD, first.worldId());
        assertEquals(Set.of(key), first.allowPathfinding());
        assertEquals(encounter.recentEvents(), first.recentEvents());
        assertTrue(first.previousTargetDistanceBlocks().isEmpty());
        assertTrue(first.lastUnreachable().isEmpty());
        capture.capture(encounter.id(), Set.of());
        var second = contexts.get(1);
        assertEquals(6.0, second.previousTargetDistanceBlocks().get(key));
        assertEquals(Set.of(key), second.lastUnreachable());
        assertTrue(second.allowPathfinding().isEmpty());
    }

    @Test
    void theFactsOfEachMobAreKeptInItsSession() {
        var encounter = encounter(1, 1);
        encounter(2, 1);
        behaviour = (members, context) -> Optional.of(facts(encounter.id(), members, context, true, player(1)));
        var facts = capture.capture(encounter.id(), Set.of()).orElseThrow();
        assertEquals(2, facts.mobs().size());
        for (var mob : facts.mobs()) {
            assertEquals(Optional.of(mob), fixture.registry.find(mob.mob()).orElseThrow().lastMobFacts());
        }
    }

    @Test
    void theCaptureTimeIsMeasuredWithTheServerClockIntoTheDecisionMetrics() {
        var encounter = encounter(1, 1);
        behaviour = (members, context) -> {
            fixture.clock.nanos += 250_000;
            return Optional.empty();
        };
        capture.capture(encounter.id(), Set.of());
        fixture.clock.nanos += 1_000_000;
        behaviour = (members, context) -> {
            fixture.clock.nanos += 90_000;
            return Optional.empty();
        };
        capture.capture(encounter.id(), Set.of());
        assertEquals(2, metrics.samples(DecisionMetrics.Span.CAPTURE));
        assertEquals(250, metrics.maxMicros(DecisionMetrics.Span.CAPTURE));
        assertEquals(OptionalLong.of(100), metrics.percentileMicros(DecisionMetrics.Span.CAPTURE, 0.5,
                fixture.clock.nanos));
        assertEquals(OptionalLong.of(250), metrics.percentileMicros(DecisionMetrics.Span.CAPTURE, 1.0,
                fixture.clock.nanos));
    }

    @Test
    void anUnperceivedTargetStaysIndexedAndRepeatedAssignKeepsTheEncounter() {
        var encounter = encounter(1, 1);
        var session = fixture.registry.find(id(1)).orElseThrow();
        behaviour = (members, context) -> {
            context.sightings().accept(new MobSighting(members.getFirst(), WORLD, Optional.of(player(1)),
                    OptionalDouble.of(40.0)));
            return Optional.of(facts(encounter.id(), members, context, true, player(2)));
        };
        for (var heartbeat = 0; heartbeat < 3; heartbeat++) {
            capture.capture(encounter.id(), Set.of());
            assertTrue(encounter.isParticipant(player(1)), "heartbeat " + heartbeat);
            assertEquals(encounter, fixture.encounters.assign(session, WORLD, player(1), near()));
        }
        assertFalse(encounter.isClosed());
        assertEquals(1, fixture.encounters.size());
        assertEquals(List.of(encounter), fixture.encounters.ofParticipant(WORLD, player(1)));
    }

    @Test
    void aNewlyPerceivedPlayerJoinsAFullIndexWhenAStaleParticipantLeaves() {
        var encounter = encounter(1, 1);
        for (var index = 2; index <= EncounterRegistry.MAX_PARTICIPANTS; index++) {
            assertTrue(fixture.encounters.addParticipant(encounter.id(), player(index)));
        }
        assertFalse(fixture.encounters.addParticipant(encounter.id(), player(17)));
        var perceived = new UUID[EncounterRegistry.MAX_PARTICIPANTS];
        for (var index = 0; index < perceived.length; index++) {
            perceived[index] = player(index + 2);
        }
        behaviour = (members, context) -> Optional.of(facts(encounter.id(), members, context, true, perceived));
        var facts = capture.capture(encounter.id(), Set.of()).orElseThrow();
        assertEquals(EncounterRegistry.MAX_PARTICIPANTS, facts.participants().size());
        assertTrue(encounter.isParticipant(player(17)));
        assertFalse(encounter.isParticipant(player(1)));
        assertEquals(List.of(encounter), fixture.encounters.ofParticipant(WORLD, player(17)));
        assertEquals(EncounterRegistry.MAX_PARTICIPANTS, encounter.participants().size());
    }
}
