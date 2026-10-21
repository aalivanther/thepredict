package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Reaction and block chance of a synthetic (M22.3). */
class SyntheticPlayerTest {
    @Test
    void skillZeroAndOneMatchTheFormula() {
        var slow = new SyntheticPlayer("shield-flanker", 0.0);
        var fast = new SyntheticPlayer("kiter-archer", 1.0);
        var mid = new SyntheticPlayer("tower", 0.5);
        assertEquals(12, slow.reactionTicks());
        assertEquals(0.2, slow.blockChance(), 1e-9);
        assertEquals(4, fast.reactionTicks());
        assertEquals(0.8, fast.blockChance(), 1e-9);
        assertEquals(8, mid.reactionTicks());
        assertEquals(0.5, mid.blockChance(), 1e-9);
    }
}
