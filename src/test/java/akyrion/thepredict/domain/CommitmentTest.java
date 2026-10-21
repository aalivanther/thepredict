package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ten thousand feint draws stay inside two hundredths of the commitment (M23.4). */
class CommitmentTest {
    @Test
    void tenThousandDrawsMatchTheCommitment() {
        for (var commitment : new int[] {50, 75, 100}) {
            var feints = 0;
            for (var sequence = 0L; sequence < 10_000L; sequence++) {
                if (TelegraphPolicy.feint(sequence, 7L, commitment)) {
                    feints++;
                }
            }
            assertEquals((100 - commitment) / 100.0, feints / 10_000.0, 0.02, "commitment " + commitment);
        }
        assertTrue(TelegraphPolicy.feint(1L, 1L, 0) || !TelegraphPolicy.feint(1L, 1L, 100));
    }
}
