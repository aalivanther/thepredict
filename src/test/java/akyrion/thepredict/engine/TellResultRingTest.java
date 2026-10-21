package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.TelegraphEnd;
import akyrion.thepredict.domain.TelegraphResult;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Result window edges and the two-slot cap (M23.10). */
class TellResultRingTest {
    @Test
    void aSessionKeepsAtMostTwoPendingResults() {
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), EngineFixtures.profile("Guardian"));
        assertEquals(2, session.tellResults().capacity());
        var target = UUID.randomUUID();
        assertTrue(session.tellResults().offer(1_000L, 10, false, target, TelegraphEnd.RELEASED));
        assertTrue(session.tellResults().offer(1_100L, 10, false, target, TelegraphEnd.RELEASED));
        assertFalse(session.tellResults().offer(1_200L, 10, false, target, TelegraphEnd.RELEASED));
        assertEquals(2, session.tellResults().pending());
    }

    @Test
    void aHitOnTheEdgesLandsAndAHitAfterTheWindowDoesNot() {
        var target = UUID.randomUUID();
        assertEquals(TelegraphResult.LANDED, resultAfterHit(target, 99));
        assertEquals(TelegraphResult.LANDED, resultAfterHit(target, 110));
        assertEquals(TelegraphResult.AVOIDED, resultAfterHit(target, 111));
    }

    @Test
    void aBlockBeatsAMissAndAProjectileWaitsFortyTicks() {
        var target = UUID.randomUUID();
        var blocked = new TellResultRing();
        assertTrue(blocked.offer(100L, 10, false, target, TelegraphEnd.RELEASED));
        blocked.noteBlocked(target, 105L);
        assertEquals(1, blocked.drain(110L));
        assertEquals(TelegraphResult.BLOCKED, blocked.drained(0).result());
        var projectile = new TellResultRing();
        assertTrue(projectile.offer(0L, 10, true, target, TelegraphEnd.RELEASED));
        assertEquals(0, projectile.drain(39L));
        assertEquals(1, projectile.drain(40L));
        assertEquals(TelegraphResult.AVOIDED, projectile.drained(0).result());
    }

    @Test
    void anInterruptClosesAsNoneWithoutWaiting() {
        var ring = new TellResultRing();
        assertTrue(ring.offer(50L, 10, false, UUID.randomUUID(), TelegraphEnd.INTERRUPTED));
        ring.noteDealt(UUID.randomUUID(), 0.2, 50L);
        assertEquals(1, ring.drain(50L));
        assertEquals(TelegraphResult.NONE, ring.drained(0).result());
    }

    private static TelegraphResult resultAfterHit(UUID target, long hit) {
        var ring = new TellResultRing();
        if (hit < 100L) {
            ring.noteDealt(target, 0.2, hit);
        }
        assertTrue(ring.offer(100L, 10, false, target, TelegraphEnd.RELEASED));
        if (hit >= 100L) {
            ring.noteDealt(target, 0.2, hit);
        }
        assertEquals(1, ring.drain(hit < 110L ? 110L : hit));
        return ring.drained(0).result();
    }
}
