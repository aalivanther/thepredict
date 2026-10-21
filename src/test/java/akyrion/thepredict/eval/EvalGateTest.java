package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A gate is the worst stage (M22.5), through the real {@link EvalGate} (SIM-17). */
class EvalGateTest {
    @Test
    void aFailureBeatsAPassAndAnInconclusiveDoesNotPass() {
        assertEquals("FAIL", EvalGate.overall("PASS", "FAIL", "PASS"));
        assertEquals("FAIL", EvalGate.overall("INCONCLUSIVE", "FAIL"));
        assertEquals("INCONCLUSIVE", EvalGate.overall("PASS", "INCONCLUSIVE"));
        assertEquals("PASS", EvalGate.overall("PASS", "SKIPPED"));
        assertEquals("PASS_SIM_ONLY", EvalGate.overall("PASS_SIM_ONLY", "SKIPPED"));
        assertEquals("PASS", EvalGate.overall());
        assertThrows(IllegalArgumentException.class, () -> EvalGate.overall("MAYBE"));
    }
}
