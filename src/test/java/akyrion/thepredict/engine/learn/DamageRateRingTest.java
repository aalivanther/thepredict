package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.learn.CreditMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageRateRingTest {
    @Test
    void oldHitsLeaveTheSpanAndTheRateIsPerTick() {
        var ring = new DamageRateRing();
        ring.add(100L, 0.10);
        ring.add(140L, 0.10);
        assertEquals(0.20 / DamageRateRing.TICKS, ring.rate(150L), 1.0e-15);
        assertEquals(0.10 / DamageRateRing.TICKS, ring.rate(170L), 1.0e-15);
        assertEquals(0.0, ring.rate(400L), 0.0);
        assertEquals(0.0, ring.rate(99L), 0.0);
    }

    @Test
    void hitsOnOneTickAddUpAndAReusedSlotForgetsItsOldTick() {
        var ring = new DamageRateRing();
        ring.add(5L, 0.25);
        ring.add(5L, 0.25);
        assertEquals(0.5 / DamageRateRing.TICKS, ring.rate(5L), 1.0e-15);
        ring.add(65L, 0.10);
        assertEquals(0.10 / DamageRateRing.TICKS, ring.rate(65L), 1.0e-15);
        ring.add(66L, 2.0);
        ring.add(67L, -1.0);
        ring.add(68L, Double.NaN);
        assertEquals(1.10 / DamageRateRing.TICKS, ring.rate(68L), 1.0e-15);
    }

    @Test
    void theAuditScenarioNoLongerRewardsADefenceThatReducedNothing() {
        var ring = new DamageRateRing();
        ring.add(10L, 0.10);
        ring.add(20L, 0.10);
        var before = ring.rate(1000L);
        var during = 0.10 / 40.0;
        assertEquals(0.0, CreditMath.avoided(before, during, 40, true), 0.0);
    }

    @Test
    void theLegacyEventRingReportsFractionsOfHealth() {
        var ring = new DamageRateRing();
        for (var event = 0; event < 60; event++) {
            ring.add(1000);
        }
        assertEquals(1.0, ring.rate(), 1.0e-12);
        var single = new DamageRateRing();
        single.add(100);
        single.add(100);
        assertEquals(0.20 / DamageRateRing.TICKS, single.rate(), 1.0e-15);
        assertEquals(0.0, new DamageRateRing().rate(), 0.0);
    }
}
