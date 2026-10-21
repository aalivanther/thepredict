package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AttackPool;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.TokenEnd;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternJoinTest {
    @Test
    void observePublishesWithoutMovingTheBodyAndAQuietTickHidesTheMask() throws Exception {
        var source = Files.readString(Path.of("src/main/java/akyrion/thepredict/engine/Coordinator.java"));
        var group = source.indexOf("run(\"group\"");
        var patterns = source.indexOf("run(\"patterns\"");
        var layer = source.indexOf("run(\"tickLayer\"");
        assertTrue(group >= 0 && group < patterns && patterns < layer, source.substring(Math.max(0, group), layer + 20));
        var gate = new PatternModeGate();
        gate.offer(PatternMode.OBSERVE, false);
        var book = new KinematicBook();
        var enemy = new UUID(8, 9);
        book.store(enemy, 1L | (1L << 44), 4);
        var inbox = new PatternInbox();
        var director = new PatternDirector(inbox, book, PatternSettings.DEFAULT, gate, () -> 0L);
        var session = new MobSession(new MobKey(new UUID(1, 2), 0), EngineFixtures.profile("Guardian"));
        session.recordTarget(enemy, 3.0);
        session.tickSignals(TickSignals.PRESENT);
        session.noteLiveSignature(17);
        for (var index = 0; index < 12; index++) {
            inbox.admit(enemy);
            inbox.offer(enemy, EnemyAction.STRIKE_EARLY.ordinal(), index);
        }
        director.visit(session, 12);
        assertTrue(session.patternMask() != 0, "mask " + session.patternMask());
        assertEquals(17, session.liveSignature());
        assertTrue(session.intent().isEmpty());
        assertTrue(session.candidates().isEmpty());
        assertFalse(session.patternReserve());
        assertEquals(1.0, session.patternFactor());
        assertEquals(PatternMode.OBSERVE, session.patternMode());
        assertFalse(session.patternPass());
        assertFalse(session.patternCounterActive());
        assertEquals(book.word(enemy), director.storedWord(enemy));
        session.tickSignals(0L);
        director.visit(session, 13);
        assertEquals(0, session.patternMask());
        assertEquals(-1, session.patternForecast());
        assertEquals(17, session.liveSignature());
        director.step(14, 0L);
        assertEquals(1, director.deferred());
        var clock = new long[] {0L};
        var ring = new PatternInbox();
        var slow = new PatternDirector(ring, new KinematicBook(), PatternSettings.DEFAULT, gate,
                () -> clock[0] += 1_000L);
        var crowded = new UUID(11, 12);
        ring.admit(crowded);
        for (var index = 0; index < 8; index++) {
            ring.offer(crowded, EnemyAction.STRIKE.ordinal(), index);
        }
        var waiting = new MobSession(new MobKey(new UUID(13, 14), 0), EngineFixtures.profile("Guardian"));
        waiting.recordTarget(crowded, 3.0);
        waiting.tickSignals(TickSignals.PRESENT);
        slow.visitWithin(waiting, 30, 1L);
        assertTrue(slow.deferred() >= 1);
        assertEquals(8, ring.size(crowded));
    }

    @Test
    void aReservedTokenReturnsYieldedAndDoesNotStayReleased() {
        var ledger = new AttackLedger();
        assertTrue(ledger.grant(0, 1, AttackPool.MELEE, 10L, 4, 1).isEmpty());
        ledger.markReserved(0, 1, AttackPool.MELEE);
        assertTrue(ledger.holds(0, 1, AttackPool.MELEE, 49L, 100));
        assertFalse(ledger.holds(0, 1, AttackPool.MELEE, 50L, 100));
        assertEquals(TokenEnd.YIELDED, ledger.lastClosed());
        assertTrue(ledger.grant(0, 1, AttackPool.MELEE, 50L, 4, 1).isEmpty());
        var plain = new AttackLedger();
        assertTrue(plain.grant(0, 1, AttackPool.MELEE, 10L, 4, 1).isEmpty());
        assertTrue(plain.holds(0, 1, AttackPool.MELEE, 109L, 100));
        assertFalse(plain.holds(0, 1, AttackPool.MELEE, 110L, 100));
        assertEquals(TokenEnd.IDLE_TIMEOUT, plain.lastClosed());
    }

    @Test
    void biasWithAPassArmsTheCounterAndObserveDoesNot() {
        var session = new MobSession(new MobKey(new UUID(3, 4), 0), EngineFixtures.profile("Guardian"));
        session.patternMode(PatternMode.OBSERVE);
        session.patternCounterActive(true);
        assertFalse(PatternCast.exploitReady(session));
        session.patternMode(PatternMode.BIAS);
        session.patternPass(true);
        session.patternCounterActive(true);
        assertTrue(PatternCast.exploitReady(session));
        var without = PatternCast.share(session);
        session.patternPass(false);
        session.patternMode(PatternMode.BIAS);
        var blocked = PatternCast.share(session);
        assertEquals(without, blocked);
        session.patternMode(PatternMode.OFF);
        assertFalse(PatternCast.exploitReady(session));
    }
}
