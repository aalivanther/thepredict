package akyrion.thepredict.engine;

import akyrion.thepredict.adapter.jev.JevMeasurements;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.TickSignals;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyParityObserveTest {
    @Test
    void observeMatchesOffOnTheBodyTheSignatureAndTheExtension() {
        var mask = EnemyPattern.SPAMS_CLICKS.bit();
        var empty = JevMeasurements.patternBody(PatternMode.FULL, 0);
        var off = JevMeasurements.patternBody(PatternMode.OFF, mask);
        var observe = JevMeasurements.patternBody(PatternMode.OBSERVE, mask);
        var full = JevMeasurements.patternBody(PatternMode.FULL, mask);
        assertEquals(empty, off);
        assertEquals(off, observe);
        assertNotEquals(observe, full);
        var gate = new PatternModeGate();
        gate.offer(PatternMode.OBSERVE, false);
        var enemy = new UUID(2, 3);
        var inbox = new PatternInbox();
        var director = new PatternDirector(inbox, new KinematicBook(), PatternSettings.DEFAULT, gate, () -> 0L);
        var session = new MobSession(new MobKey(new UUID(4, 5), 0), EngineFixtures.profile("Guardian"));
        session.recordTarget(enemy, 6.0);
        session.tickSignals(TickSignals.PRESENT);
        session.patternMode(PatternMode.OBSERVE);
        session.publishPattern(mask, -1);
        var produced = LiveSignature.of(session);
        session.noteLiveSignature(produced);
        session.holdIntentCapture(produced, 5L);
        for (var index = 0; index < 12; index++) {
            inbox.admit(enemy);
            inbox.offer(enemy, EnemyAction.STRIKE_EARLY.ordinal(), index);
        }
        director.visit(session, 20);
        assertEquals(produced, session.liveSignature());
        assertEquals(produced, LiveSignature.of(session));
        assertEquals(session.liveSignature(), session.intentCaptureSignature());
        assertTrue(session.patternMask() != 0);
        assertTrue(session.intent().isEmpty());
        assertTrue(session.candidates().isEmpty());
        assertEquals(1.0, session.patternFactor());
        var fullSession = new MobSession(new MobKey(new UUID(6, 7), 0), EngineFixtures.profile("Guardian"));
        fullSession.recordTarget(enemy, 6.0);
        fullSession.tickSignals(TickSignals.PRESENT);
        fullSession.patternMode(PatternMode.FULL);
        fullSession.publishPattern(mask, -1);
        assertNotEquals(LiveSignature.of(session), LiveSignature.of(fullSession));
    }
}
