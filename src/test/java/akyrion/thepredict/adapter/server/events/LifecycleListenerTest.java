package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.engine.SessionMetrics;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static akyrion.thepredict.adapter.server.events.LifecycleFixtures.at;
import static akyrion.thepredict.adapter.server.events.LifecycleFixtures.chunk;
import static akyrion.thepredict.adapter.server.events.LifecycleFixtures.entity;
import static akyrion.thepredict.adapter.server.events.LifecycleFixtures.world;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleListenerTest {
    private static final UUID OVERWORLD = new UUID(0xA, 1);
    private static final UUID NETHER = new UUID(0xA, 2);

    private final LifecycleFixtures fixture = new LifecycleFixtures();
    private final LifecycleListener listener = fixture.listener;

    private SessionState state(UUID entityId) {
        return fixture.sessions.find(entityId).orElseThrow().state();
    }

    @Test
    void aSpawnOpensTheSessionOnTheNextTickAndNeverInTheTickOfTheEvent() {
        var mob = fixture.mob(1, "Guardian");
        listener.spawned(mob);
        assertTrue(fixture.sessions.find(mob).isEmpty());
        assertSame(IntentView.ABSENT, fixture.board.current(mob));
        fixture.runNextTick();
        assertTrue(fixture.sessions.find(mob).orElseThrow().installPending());
        assertEquals(IntentView.base(0), fixture.board.current(mob));
    }

    @Test
    void aSpawnOfAnUnboundTypeOpensNothing() {
        var mob = fixture.mob(1, "Zombie");
        listener.spawned(mob);
        fixture.runNextTick();
        assertTrue(fixture.sessions.find(mob).isEmpty());
    }

    @Test
    void aChunkUnloadSuspendsAtOnceAndItsLoadResumesOnTheNextTick() {
        var mob = fixture.mob(1, "Guardian");
        var other = fixture.mob(2, "Zombie");
        fixture.lifecycle.discovered(mob, LifecycleFixtures.profile("Guardian"));
        var world = world(OVERWORLD);
        listener.onEntitiesUnload(new EntitiesUnloadEvent(chunk(world), List.of(entity(mob), entity(other))));
        assertEquals(SessionState.SUSPENDED, state(mob));
        listener.onEntitiesLoad(new EntitiesLoadEvent(chunk(world), List.of(entity(mob), entity(other))));
        assertEquals(SessionState.SUSPENDED, state(mob));
        fixture.runNextTick();
        assertEquals(SessionState.ACTIVE, state(mob));
        assertEquals(1, fixture.sessions.find(mob).orElseThrow().key().generation());
        assertTrue(fixture.sessions.find(other).isEmpty());
    }

    @Test
    void aChunkWithoutEntitiesSchedulesNothing() {
        listener.onEntitiesLoad(new EntitiesLoadEvent(chunk(world(OVERWORLD)), List.of()));
        assertTrue(fixture.nextTick.isEmpty());
    }

    @Test
    void aRemovalByChunkUnloadSuspendsAndAnyOtherRemovalCloses() {
        var unloaded = fixture.mob(1, "Guardian");
        var removed = fixture.mob(2, "Guardian");
        fixture.lifecycle.discovered(unloaded, LifecycleFixtures.profile("Guardian"));
        fixture.lifecycle.discovered(removed, LifecycleFixtures.profile("Guardian"));
        listener.onEntityRemove(new EntityRemoveEvent(entity(unloaded), EntityRemoveEvent.Cause.UNLOAD));
        listener.onEntityRemove(new EntityRemoveEvent(entity(removed), EntityRemoveEvent.Cause.PLUGIN));
        assertEquals(SessionState.SUSPENDED, state(unloaded));
        assertTrue(fixture.sessions.find(removed).isEmpty());
        assertEquals(1, fixture.metrics.endedSessions(SessionMetrics.SessionEnd.REMOVED));
        assertTrue(fixture.released.isEmpty());
    }

    @Test
    void anAsynchronousDeathOnlyChangesStateOnTheServerThread() {
        var mob = fixture.mob(1, "Guardian");
        fixture.lifecycle.discovered(mob, LifecycleFixtures.profile("Guardian"));
        listener.ended(mob, SessionMetrics.SessionEnd.DIED, true);
        assertEquals(SessionState.ACTIVE, state(mob));
        fixture.runNextTick();
        assertTrue(fixture.sessions.find(mob).isEmpty());
        assertEquals(1, fixture.metrics.endedSessions(SessionMetrics.SessionEnd.DIED));
    }

    @Test
    void aSynchronousEventSeenOffTheServerThreadIsMovedToIt() {
        var mob = fixture.mob(1, "Guardian");
        fixture.lifecycle.discovered(mob, LifecycleFixtures.profile("Guardian"));
        fixture.primaryThread = false;
        listener.ended(mob, SessionMetrics.SessionEnd.DESPAWNED, false);
        assertEquals(SessionState.ACTIVE, state(mob));
        fixture.runNextTick();
        assertTrue(fixture.sessions.find(mob).isEmpty());
    }

    @Test
    void onlyATeleportToAnotherWorldAdvancesTheGeneration() {
        var mob = fixture.mob(1, "Guardian");
        fixture.lifecycle.discovered(mob, LifecycleFixtures.profile("Guardian"));
        var overworld = world(OVERWORLD);
        var nether = world(NETHER);
        listener.onEntityTeleport(new EntityTeleportEvent(entity(mob), at(overworld), at(world(OVERWORLD))));
        assertEquals(0, fixture.sessions.find(mob).orElseThrow().key().generation());
        listener.onEntityTeleport(new EntityTeleportEvent(entity(mob), at(overworld), null));
        assertEquals(0, fixture.sessions.find(mob).orElseThrow().key().generation());
        listener.onEntityPortal(new EntityPortalEvent(entity(mob), at(overworld), at(nether)));
        assertEquals(1, fixture.sessions.find(mob).orElseThrow().key().generation());
        assertEquals(IntentView.base(1), fixture.board.current(mob));
        assertTrue(fixture.sessions.find(mob).orElseThrow().installPending());
    }

    @Test
    void worldsAreComparedByIdentifier() {
        assertFalse(LifecycleListener.changesWorld(at(world(OVERWORLD)), at(world(OVERWORLD))));
        assertTrue(LifecycleListener.changesWorld(at(world(OVERWORLD)), at(world(NETHER))));
        assertFalse(LifecycleListener.changesWorld(at(world(OVERWORLD)), at(null)));
    }

    @Test
    void aReloadRebindsFirstThenAdvancesOrUnbindsAndScansForNewlyBoundMobs() {
        var kept = fixture.mob(1, "Guardian");
        var dropped = fixture.mob(2, "Retired");
        var newlyBound = fixture.mob(3, "Newcomer");
        fixture.catalog.put("Retired", LifecycleFixtures.profile("Retired"));
        fixture.lifecycle.discovered(kept, fixture.catalog.get("Guardian"));
        fixture.lifecycle.discovered(dropped, fixture.catalog.get("Retired"));
        fixture.catalog.remove("Retired");
        fixture.catalog.put("Newcomer", LifecycleFixtures.profile("Newcomer"));
        listener.reloaded();
        assertEquals(List.of("rebind"), fixture.log);
        assertEquals(1, fixture.sessions.find(kept).orElseThrow().key().generation());
        assertTrue(fixture.sessions.find(dropped).isEmpty());
        assertSame(IntentView.ABSENT, fixture.board.current(dropped));
        assertTrue(fixture.sessions.find(newlyBound).isEmpty());
        assertEquals(3, fixture.scan.remaining());
        fixture.scan.tick();
        assertEquals(0, fixture.sessions.find(newlyBound).orElseThrow().key().generation());
        assertEquals(1, fixture.sessions.find(kept).orElseThrow().key().generation());
    }
}
