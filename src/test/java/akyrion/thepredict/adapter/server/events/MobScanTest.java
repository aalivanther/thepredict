package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.domain.SessionState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobScanTest {
    private final LifecycleFixtures fixture = new LifecycleFixtures();
    private final MobScan scan = fixture.scan;

    @Test
    void theInitialScanOpensTheBoundMobsAtThirtyTwoPerTick() {
        for (var mob = 1; mob <= 100; mob++) {
            fixture.mob(mob, mob % 10 == 0 ? "Zombie" : "Guardian");
        }
        scan.start();
        var perTick = new ArrayList<Integer>();
        while (scan.remaining() > 0) {
            perTick.add(scan.tick());
        }
        assertEquals(List.of(32, 32, 32, 4), perTick);
        assertEquals(90, fixture.sessions.count(SessionState.ACTIVE));
        assertEquals(0, scan.tick());
    }

    @Test
    void startingAgainReplacesAnUnfinishedScan() {
        for (var mob = 1; mob <= 40; mob++) {
            fixture.mob(mob, "Guardian");
        }
        scan.start();
        scan.tick();
        scan.start();
        assertEquals(40, scan.remaining());
    }

    @Test
    void discoveringResolvesTheTypeThroughTheCurrentCatalog() {
        var bound = fixture.mob(1, "Guardian");
        var unbound = fixture.mob(2, "Zombie");
        assertTrue(scan.discover(bound));
        assertFalse(scan.discover(unbound));
        assertFalse(scan.discover(LifecycleFixtures.id(3)));
        assertEquals(1, fixture.sessions.count(SessionState.ACTIVE));
    }

    @Test
    void aMobWhoseInstanceIsNoLongerActiveIsNeitherOpenedNorResumed() {
        var ghost = fixture.mob(1, "Guardian");
        var suspended = fixture.mob(2, "Guardian");
        fixture.lifecycle.discovered(suspended, LifecycleFixtures.profile("Guardian"));
        fixture.lifecycle.unloaded(suspended);
        fixture.inactive.add(ghost);
        fixture.inactive.add(suspended);
        assertFalse(scan.discover(ghost));
        assertFalse(scan.discover(suspended));
        assertTrue(fixture.sessions.find(ghost).isEmpty());
        assertEquals(SessionState.SUSPENDED, fixture.sessions.find(suspended).orElseThrow().state());
    }
}
