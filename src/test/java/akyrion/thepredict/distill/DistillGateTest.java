package akyrion.thepredict.distill;

import akyrion.thepredict.domain.distill.DistillMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Autonomous without a tape is inconclusive. */
class DistillGateTest {
    @Test
    void autonomousWithoutATapeIsInconclusive() {
        assertEquals("INCONCLUSIVE", DistillGate.verdict(DistillMode.AUTONOMOUS, false, "all"));
        assertEquals("PASS", DistillGate.verdict(DistillMode.AUTONOMOUS, true, "all"));
        assertEquals("PASS_SIM_ONLY", DistillGate.verdict(DistillMode.ASSIST, false, "bench"));
        assertEquals("PASS", DistillGate.verdict(DistillMode.SHADOW, false, "bench"));
    }
}
