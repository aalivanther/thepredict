package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternFactFilterTest {
    @Test
    void aFlickerNeverEntersAndAStableFactWaitsForTheGaps() {
        var flicker = new PatternFactFilter();
        var bit = EnemyPattern.SPAMS_CLICKS.bit();
        for (var tick = 0; tick < 300; tick++) {
            var mask = (tick / 30) % 2 == 0 ? bit : 0;
            flicker.update(mask, tick, 40, 80, 0);
        }
        assertEquals(0, flicker.shown());
        var stable = new PatternFactFilter();
        for (var tick = 0; tick < 40; tick++) {
            stable.update(bit, tick, 40, 80, 0);
        }
        assertEquals(bit, stable.shown());
        for (var tick = 40; tick < 119; tick++) {
            stable.update(0, tick, 40, 80, 0);
        }
        assertEquals(bit, stable.shown());
        stable.update(0, 119, 40, 80, 0);
        assertEquals(0, stable.shown());
    }

    @Test
    void restingClearsAtOnceAndChangesOfOneFactStayEightyTicksApart() {
        var filter = new PatternFactFilter();
        var bit = EnemyPattern.CRIT_JUMPER.bit();
        for (var tick = 0; tick < 40; tick++) {
            filter.update(bit, tick, 40, 80, 0);
        }
        assertEquals(bit, filter.shown());
        filter.update(0, 40, 40, 80, bit);
        assertEquals(0, filter.shown());
        var changes = 0;
        var last = Integer.MIN_VALUE;
        var shown = 0;
        var seeded = new PatternFactFilter();
        var active = true;
        var tick = 0;
        while (tick < 20_000) {
            var run = 30 + StableRandom.below(StableRandom.mix(26L ^ tick), 151);
            for (var step = 0; step < run && tick < 20_000; step++, tick++) {
                var next = seeded.update(active ? bit : 0, tick, 40, 80, 0);
                if (next != shown) {
                    if (last != Integer.MIN_VALUE) {
                        assertTrue(tick - last >= 80, "gap " + (tick - last) + " at " + tick);
                    }
                    last = tick;
                    shown = next;
                    changes++;
                }
            }
            active = !active;
        }
        assertTrue(changes >= 20, "changes " + changes);
    }

    @Test
    void oneParticipantChangesItsFactsAtMostOncePerOnTicks() {
        var filter = new PatternFactFilter();
        var first = EnemyPattern.CRIT_JUMPER.bit();
        var second = EnemyPattern.SPAMS_CLICKS.bit();
        var lastChange = Integer.MIN_VALUE;
        var shown = 0;
        for (var tick = 0; tick < 400; tick++) {
            var mask = (tick < 200 ? first : 0) | (tick >= 20 ? second : 0);
            var next = filter.update(mask, tick, 40, 80, 0);
            if (next != shown) {
                if (lastChange != Integer.MIN_VALUE) {
                    assertTrue(tick - lastChange >= 40, "participant gap " + (tick - lastChange));
                }
                lastChange = tick;
                shown = next;
            }
        }
        assertEquals(second, shown);
    }
}
