package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BetaEvidenceTest {
    @Test
    void oneSuccessMovesTheMeanAndAHalfKeepsThePrior() {
        var evidence = new BetaEvidence();
        evidence.update(true, 1.0);
        assertEquals(1.0, evidence.successes(), 1e-12);
        assertEquals(0.6, evidence.mean(), 1e-12);
        evidence.halve();
        assertEquals(0.5, evidence.successes(), 1e-12);
        assertEquals(1, evidence.trials());
    }

    @Test
    void aForgettingOutsideTheHalfOpenUnitIsRejectedAndLeavesTheEvidence() {
        var evidence = new BetaEvidence();
        evidence.update(true, 0.9);
        for (var lambda : new double[] {0.0, -0.1, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> evidence.update(false, lambda), "lambda " + lambda);
        }
        assertEquals(1, evidence.trials());
        assertEquals(1.0, evidence.successes(), 1e-12);
        assertEquals(0.0, evidence.failures(), 1e-12);
    }

    @Test
    void aSeedMustBeFiniteAndNonNegative() {
        var evidence = new BetaEvidence();
        evidence.seed(4.4, 1.6);
        assertEquals(6, evidence.seededTrials());
        for (var mass : new double[] {-1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> evidence.seed(mass, 0.0), "success " + mass);
            assertThrows(IllegalArgumentException.class, () -> evidence.seed(0.0, mass), "failure " + mass);
        }
        assertEquals(6, evidence.seededTrials());
        assertEquals(4.4, evidence.successes(), 1e-12);
        assertEquals(1.6, evidence.failures(), 1e-12);
    }
}
