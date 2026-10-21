package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StableRandomTest {
    /** Chi-square critical value, 9 degrees of freedom, 99.9 percent. */
    private static final double CHI_SQUARE_999 = 27.877;

    @Test
    void mixAndDrawMatchTheSplitMixVectors() {
        assertEquals(0L, StableRandom.mix(0L));
        assertEquals(6238072747940578789L, StableRandom.mix(1L));
        assertEquals(-2606959012126976886L, StableRandom.mix(2L));
        assertEquals(-2152535657050944081L, StableRandom.mix(0x9E3779B97F4A7C15L));
        var seeded = StableRandom.seed(0L, 1L, 2L);
        assertEquals(7247713632874657023L, seeded);
        assertEquals(6913395887601658397L, StableRandom.draw(seeded, 0L, 1));
        assertEquals(3683812619085550082L, StableRandom.draw(seeded, 3L, 1));
        assertEquals(2723936093352955464L, StableRandom.draw(seeded, 7L, 3));
        assertEquals(-8935080181348647554L, StableRandom.draw(7L, 3L, 1));
        assertEquals(StableRandom.draw(7L, 3L, StableRandom.SALT_SIDE), StableRandom.draw(7L, 3L, 1));
    }

    @Test
    void belowIsUniformAndRejectsABoundOutsideTheRange() {
        assertEquals(0, StableRandom.below(StableRandom.draw(1L, 0L, 1), 1));
        assertEquals(0, StableRandom.below(0L, 1));
        assertThrows(IllegalArgumentException.class, () -> StableRandom.below(1L, 0));
        assertThrows(IllegalArgumentException.class, () -> StableRandom.below(1L, -1));
        var buckets = new int[10];
        var seed = StableRandom.mix(0xC0FFEEL);
        var samples = 1_000_000;
        for (var index = 0; index < samples; index++) {
            buckets[StableRandom.below(StableRandom.draw(seed, index, StableRandom.SALT_PHASE), 10)]++;
        }
        var expected = samples / 10.0;
        var chi = 0.0;
        for (var bucket : buckets) {
            var delta = bucket - expected;
            chi += delta * delta / expected;
        }
        assertTrue(chi < CHI_SQUARE_999, chi + " >= " + CHI_SQUARE_999);
    }

    @Test
    void chanceFollowsThePermilleOfTheSameWord() {
        var word = StableRandom.draw(4L, 8L, 2);
        var drawn = StableRandom.below(word, 1000);
        assertFalse(StableRandom.chance(word, 0));
        assertFalse(StableRandom.chance(word, drawn));
        assertTrue(StableRandom.chance(word, drawn + 1));
    }
}
