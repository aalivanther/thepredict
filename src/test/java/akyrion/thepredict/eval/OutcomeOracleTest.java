package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** A cloned oracle agrees byte for byte, and a perfect gain stops for H1 (M22.3). */
class OutcomeOracleTest {
    @Test
    void aCloneMatchesAndGainAgainstRandomLegalAcceptsH1() {
        var bytes = new byte[] {1, 2, 3, 4};
        assertArrayEquals(bytes, OutcomeOracle.copy(bytes));
        var halves = new double[40];
        Arrays.fill(halves, 1.0);
        halves[0] = 0.0;
        assertEquals("ACCEPT_H1", OutcomeOracle.gain(halves));
    }
}
