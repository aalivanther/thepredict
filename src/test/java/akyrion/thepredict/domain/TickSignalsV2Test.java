package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickSignalsV2Test {
    @Test
    void thePackWithoutAZoneWritesUnknownAndAReadingReplacesIt() {
        var old = TickSignals.pack(0L, 1, 0, 0, 0, 20, 20);
        var unknown = PushDanger.UNKNOWN.ordinal();
        assertEquals(unknown, TickSignals.push(old, TickSignals.MOB_PUSH_NEAR));
        assertEquals(unknown, TickSignals.push(old, TickSignals.MOB_PUSH_FAR));
        assertEquals(unknown, TickSignals.push(old, TickSignals.TARGET_PUSH_NEAR));
        assertEquals(unknown, TickSignals.push(old, TickSignals.TARGET_PUSH_FAR));
        var lethal = PushDanger.LETHAL.ordinal();
        var packed = TickSignals.pack(0L, 1, 0, 0, 0, 20, 20, lethal, 0, 0, 0);
        assertEquals(lethal, TickSignals.push(packed, TickSignals.MOB_PUSH_NEAR));
        assertEquals(0, TickSignals.push(packed, TickSignals.MOB_PUSH_FAR));
        assertEquals(0, TickSignals.push(packed, TickSignals.TARGET_PUSH_NEAR));
        assertEquals(0, TickSignals.push(packed, TickSignals.TARGET_PUSH_FAR));
        var v1 = SituationSignature.pack(DistanceBand.MELEE, HealthBand.HEALTHY, HealthBand.HEALTHY, false, false,
                false, false, false, false, false, 0);
        assertEquals(0, v1 & (1 << 17));
        assertEquals(0, v1 & (1 << 18));
    }
}
