package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.near;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InFlightRegistryTest {
    private final EngineFixtures.Sessions fixture = new EngineFixtures.Sessions();
    private final InFlightRegistry registry = new InFlightRegistry();

    private Encounter encounter(int mob) {
        return fixture.encounters.assign(fixture.open(mob), WORLD, player(mob), near());
    }

    private static InFlightRequest request(Encounter encounter, long sequence, long deadline) {
        var key = QuestionKey.of(0);
        return new InFlightRequest(encounter.id(), WORLD, sequence, 1, Map.of(key, new QuestionSpec(key,
                new MobKey(id(1), 0), Situation.NOW, List.of(new TacticId("hold_ground"), new TacticId("press_attack")))),
                Map.of(), 1, 0, 0, deadline, 10, 15);
    }

    @Test
    void onlyTheRequestInFlightOfItsEncounterIsRemovedAndTheMarkFollowsIt() {
        var encounter = encounter(1);
        var current = request(encounter, 2, 100);
        registry.add(encounter, current, new CompletableFuture<>());
        assertTrue(encounter.schedule().requestInFlight());
        assertThrows(IllegalStateException.class, () -> registry.add(encounter, request(encounter, 3, 100),
                new CompletableFuture<>()));
        assertFalse(registry.remove(request(encounter, 1, 100)));
        assertTrue(encounter.schedule().requestInFlight());
        assertTrue(registry.remove(current));
        assertFalse(encounter.schedule().requestInFlight());
        assertFalse(registry.remove(current));
        assertThrows(IllegalArgumentException.class, () -> registry.add(encounter(2), current, new CompletableFuture<>()));
    }

    @Test
    void expiredRequestsAreRemovedAndCancelledAndTheShutdownCancelsTheRest() {
        var early = encounter(1);
        var late = encounter(2);
        var earlyFuture = new CompletableFuture<Object>();
        var lateFuture = new CompletableFuture<Object>();
        registry.add(early, request(early, 1, 100), earlyFuture);
        registry.add(late, request(late, 1, 500), lateFuture);
        assertEquals(List.of(request(early, 1, 100)), registry.expire(100));
        assertTrue(earlyFuture.isCancelled());
        assertFalse(early.schedule().requestInFlight());
        assertFalse(lateFuture.isDone());
        assertEquals(1, registry.cancelAll().size());
        assertTrue(lateFuture.isCancelled());
        assertFalse(late.schedule().requestInFlight());
        assertEquals(0, registry.size());
    }
}
