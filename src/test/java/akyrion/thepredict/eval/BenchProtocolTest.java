package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wins outrank a shorter fight (M22.10). */
class BenchProtocolTest {
    @Test
    void fewerWinsLoseEvenWhenTheFightIsShorter() {
        var fasterButFewer = BenchProtocol.compare(10, 12, 40, 80, 1.0, 2.0, 0.0, 0.1);
        assertTrue(fasterButFewer < 0);
        var sameWins = BenchProtocol.compare(12, 12, 40, 80, 3.0, 1.0, 1.0, 0.0);
        assertTrue(sameWins > 0);
    }
}
