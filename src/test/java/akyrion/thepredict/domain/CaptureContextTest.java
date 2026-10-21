package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static akyrion.thepredict.domain.DomainFixtures.MOB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureContextTest {
    private static final UUID WORLD = new UUID(0xA11L, 1);
    private static final EncounterEvent EVENT = new EncounterEvent(1, EventType.MELEE_HIT, Optional.empty(), Optional.empty());

    @Test
    void collectionsAreCopiedAndTheAliasFunctionIsKept() {
        var allowed = new HashSet<>(Set.of(MOB));
        var distances = new HashMap<>(Map.of(MOB, 4.5));
        var events = new ArrayList<>(List.of(EVENT));
        var unreachable = new HashSet<>(Set.of(MOB));
        var seen = new ArrayList<MobSighting>();
        var context = new CaptureContext((uuid, kind) -> new ParticipantAlias(7), allowed, distances, events, WORLD,
                unreachable, seen::add);
        allowed.clear();
        distances.clear();
        events.clear();
        unreachable.clear();
        assertEquals(Set.of(MOB), context.allowPathfinding());
        assertEquals(4.5, context.previousTargetDistanceBlocks().get(MOB));
        assertEquals(List.of(EVENT), context.recentEvents());
        assertEquals(Set.of(MOB), context.lastUnreachable());
        assertEquals(WORLD, context.worldId());
        assertEquals(new ParticipantAlias(7), context.aliasFor().apply(UUID.randomUUID(), ParticipantKind.PLAYER));
        var sighting = new MobSighting(MOB, WORLD, Optional.empty(), OptionalDouble.empty());
        context.sightings().accept(sighting);
        assertEquals(List.of(sighting), seen);
    }

    @Test
    void negativeOrNonFiniteDistancesAreRejected() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new CaptureContext((uuid, kind) -> new ParticipantAlias(1),
                Set.of(), Map.of(MOB, -1.0), List.of(), WORLD, Set.of(), sighting -> { })).getMessage().contains("previousTargetDistanceBlocks"));
        assertThrows(IllegalArgumentException.class, () -> new CaptureContext((uuid, kind) -> new ParticipantAlias(1),
                Set.of(), Map.of(MOB, Double.NaN), List.of(), WORLD, Set.of(), sighting -> { }));
    }

    @Test
    void eventsBeyondTheCapAreRejected() {
        var events = new ArrayList<EncounterEvent>();
        for (var index = 0; index <= CombatFacts.MAX_EVENTS; index++) {
            events.add(EVENT);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new CaptureContext((uuid, kind) -> new ParticipantAlias(1), Set.of(), Map.of(), events, WORLD, Set.of(),
                        sighting -> { }));
    }

    @Test
    void theWorldTheUnreachableSetAndTheSightingReceiverAreRequired() {
        assertThrows(NullPointerException.class, () -> new CaptureContext((uuid, kind) -> new ParticipantAlias(1), Set.of(),
                Map.of(), List.of(), null, Set.of(), sighting -> { }));
        assertThrows(NullPointerException.class, () -> new CaptureContext((uuid, kind) -> new ParticipantAlias(1), Set.of(),
                Map.of(), List.of(), WORLD, null, sighting -> { }));
        assertThrows(NullPointerException.class, () -> new CaptureContext((uuid, kind) -> new ParticipantAlias(1), Set.of(),
                Map.of(), List.of(), WORLD, Set.of(), null));
    }
}
