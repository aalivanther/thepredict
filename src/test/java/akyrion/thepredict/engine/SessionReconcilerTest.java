package akyrion.thepredict.engine;

import akyrion.thepredict.domain.SessionState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.near;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionReconcilerTest {
    private final EngineFixtures.Sessions fixture = new EngineFixtures.Sessions();
    private final ArrayList<UUID> checked = new ArrayList<>();
    private final SessionReconciler reconciler = new SessionReconciler(fixture.registry, fixture.encounters,
            entityId -> checked.add(entityId) && !fixture.inactive.contains(entityId), fixture.metrics);

    @Test
    void fiveHundredSessionsAreReconciledInSixteenTicksWithoutExceedingThirtyTwoPerTick() {
        for (var mob = 1; mob <= 500; mob++) {
            fixture.open(mob);
        }
        var ticks = 0;
        do {
            var before = checked.size();
            assertTrue(reconciler.tick(ticks) <= SessionReconciler.SESSIONS_PER_TICK);
            assertTrue(checked.size() - before <= SessionReconciler.SESSIONS_PER_TICK);
            ticks++;
        } while (reconciler.remaining() > 0);
        assertEquals(16, ticks);
        assertEquals(500, checked.size());
        assertEquals(500, fixture.registry.count(SessionState.ACTIVE));
    }

    @Test
    void aGhostSessionClosesWithinOneRound() {
        for (var mob = 1; mob <= 40; mob++) {
            fixture.open(mob);
        }
        fixture.inactive.add(id(37));
        reconciler.tick(0);
        assertTrue(fixture.registry.find(id(37)).isPresent());
        reconciler.tick(1);
        assertTrue(fixture.registry.find(id(37)).isEmpty());
        assertEquals(39, fixture.registry.count(SessionState.ACTIVE));
        assertEquals(1, fixture.metrics.endedSessions(SessionMetrics.SessionEnd.RECONCILED));
    }

    @Test
    void aNewRoundStartsEveryTwoHundredTicksAndSkipsSuspendedSessions() {
        fixture.open(1);
        fixture.open(2);
        fixture.registry.suspend(id(2));
        fixture.inactive.add(id(2));
        assertEquals(1, reconciler.tick(0));
        assertEquals(0, reconciler.tick(199));
        assertEquals(1, reconciler.tick(200));
        assertEquals(2, checked.size());
        assertTrue(fixture.registry.find(id(2)).isPresent());
    }

    @Test
    void eachRoundClosesTheEncountersLeftWithoutMembers() {
        var encounter = fixture.encounters.assign(fixture.open(1), WORLD, player(1), near());
        encounter.removeMember(id(1));
        reconciler.tick(0);
        assertTrue(encounter.isClosed());
        assertEquals(0, fixture.encounters.size());
    }
}
