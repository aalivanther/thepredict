package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutcomeStatMathTest {
    @Test
    void chanMatchesWelfordIncludingEmptyAndSingleWindows() {
        var sequential = OutcomeStat.EMPTY;
        var left = OutcomeStat.EMPTY;
        var right = OutcomeStat.EMPTY;
        var effects = new double[] {0.0, 1.0, -1.0, 0.5, 3.0, -2.0, 0.25};
        for (var index = 0; index < effects.length; index++) {
            sequential = OutcomeStatMath.add(sequential, effects[index], 0.1, 0.2, true, false, false, 1, 0);
            if (index < 3) {
                left = OutcomeStatMath.add(left, effects[index], 0.1, 0.2, true, false, false, 1, 0);
            } else {
                right = OutcomeStatMath.add(right, effects[index], 0.1, 0.2, true, false, false, 1, 0);
            }
        }
        var merged = OutcomeStatMath.merge(left, right);
        assertEquals(sequential.windows(), merged.windows());
        assertEquals(sequential.effectMean(), merged.effectMean(), 1e-9);
        assertEquals(sequential.effectM2(), merged.effectM2(), 1e-6);
        assertEquals(OutcomeStatMath.variance(sequential), sequential.effectM2() / sequential.windows(), 1e-12);
        var one = OutcomeStatMath.add(OutcomeStat.EMPTY, 0.4, 0, 0, false, false, false, 0, 0);
        assertEquals(1, one.windows());
        assertEquals(0.4, one.effectMean(), 0);
        assertEquals(0.0, one.effectM2(), 0);
        assertEquals(OutcomeStat.EMPTY, OutcomeStatMath.merge(OutcomeStat.EMPTY, OutcomeStat.EMPTY));
        assertEquals(0.0, OutcomeStatMath.variance(OutcomeStat.EMPTY), 0);
    }
}
