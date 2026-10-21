package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Constant-rate projection, decay and the floor (M22.8, B2, errata E22-7). */
class TtkEstimatorTest {
    @Test
    void aConstantRateProjectsSixtySecondsWithinFivePercent() {
        var target = 60.0;
        var half = TtkEstimator.scaledHalfLifeTicks(100, target);
        assertTrue(half >= TtkEstimator.MIN_HALF_LIFE_TICKS);
        assertTrue(half <= TtkEstimator.MAX_HALF_LIFE_TICKS);
        var perTick = 1.0 / 1200.0;
        var integrator = 0.0;
        var previous = 0L;
        for (var tick = 0L; tick <= 200L; tick++) {
            integrator = TtkEstimator.hit(integrator, previous, tick, perTick, half);
            previous = tick;
            if (tick != 60L && tick != 200L) {
                continue;
            }
            var dealt = (tick + 1) * perTick;
            var estimate = TtkEstimator.project(integrator, dealt, 1.0 - dealt, 0L, tick, half, 60, target, 4.0);
            assertTrue(estimate.valid(), "tick " + tick);
            assertEquals(60.0, estimate.seconds(), 60.0 * 0.05, "tick " + tick);
        }
    }

    @Test
    void aPauseDecaysUntilTheCheckAndTheFloorLimitsOptimism() {
        var half = TtkEstimator.scaledHalfLifeTicks(100, 60.0);
        var integrator = TtkEstimator.hit(0.0, 0L, 0L, 0.2, half);
        var decayed = TtkEstimator.decay(integrator, 0L, 80L, half);
        assertTrue(decayed < integrator);
        var skipped = TtkEstimator.project(decayed, 0.02, 0.98, 0L, 80L, half, 60, 60.0, 4.0);
        assertFalse(skipped.valid());
        var young = TtkEstimator.project(integrator, 0.2, 0.8, 0L, 10L, half, 60, 60.0, 4.0);
        assertFalse(young.valid());
        var burst = TtkEstimator.project(integrator, 0.2, 0.8, 0L, 80L, half, 60, 60.0, 4.0);
        assertTrue(burst.valid());
        assertTrue(burst.seconds() <= 4.0 * 60.0);
        var ageSeconds = 80 * TtkEstimator.SECONDS_PER_TICK;
        var floor = 0.5 * 0.2 / ageSeconds;
        var stalled = TtkEstimator.project(0.0, 0.2, 0.8, 0L, 80L, half, 60, 60.0, 4.0);
        assertTrue(stalled.valid());
        assertEquals(0.0, stalled.ratePerSecond());
        assertEquals(ageSeconds + 0.8 / floor, stalled.seconds(), 1e-9);
        var slow = TtkEstimator.project(0.0, 0.05, 0.95, 0L, 400L, half, 60, 60.0, 4.0);
        assertEquals(4.0 * 60.0, slow.seconds(), 1e-9);
    }

    @Test
    void halfLifeScalesWithTargetAndASawtoothStaysInsideEightPercent() {
        var shortFight = TtkEstimator.scaledHalfLifeTicks(100, 5.0 * 60.0);
        var longFight = TtkEstimator.scaledHalfLifeTicks(100, 45.0 * 60.0);
        assertTrue(longFight >= shortFight);
        assertEquals(TtkEstimator.MAX_HALF_LIFE_TICKS, longFight);
        var half = TtkEstimator.scaledHalfLifeTicks(100, 60.0);
        var integrator = 0.0;
        var previous = 0L;
        var worst = 0.0;
        for (var tick = 0L; tick <= 400L; tick++) {
            var fraction = tick % 5L == 0L ? 5.0 / 1200.0 : 0.0;
            if (fraction > 0.0) {
                integrator = TtkEstimator.hit(integrator, previous, tick, fraction, half);
                previous = tick;
            }
            if (tick < 80L || tick % 20L != 0L) {
                continue;
            }
            var decayed = TtkEstimator.decay(integrator, previous, tick, half);
            var dealt = Math.min(1.0, (tick / 5L) * 5.0 / 1200.0);
            var estimate = TtkEstimator.project(decayed, dealt, Math.max(0.0, 1.0 - dealt), 0L, tick, half, 60, 60.0,
                    4.0);
            if (estimate.valid() && dealt < 0.9) {
                worst = Math.max(worst, Math.abs(estimate.seconds() - 60.0) / 60.0);
            }
        }
        assertTrue(worst <= 0.08, "sawtooth " + worst);
    }
}
