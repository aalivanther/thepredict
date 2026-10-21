package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.AuditSampler;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The step is a pure function of its request: it returns on the calling thread with no client and no clock. Shadow
 * never delegates; assist audits exactly the captures the stable draw picks and delegates the others.
 */
class DistillNeverWaitsTest {
    @Test
    void shadowNeverDelegatesAndAssistSplitsByTheStableDraw() {
        var audits = 0;
        var delegations = 0;
        for (var index = 0; index < 500; index++) {
            var shadow = DistillStep.consider(request(DistillMode.SHADOW, index));
            assertFalse(shadow.delegate());
            assertFalse(shadow.audit());
            assertTrue(shadow.held().isEmpty());
            var assist = DistillStep.consider(request(DistillMode.ASSIST, index));
            assertEquals(AuditSampler.audit(2L, index + 1, 10), assist.audit(), "capture " + index);
            assertEquals(!assist.audit(), assist.delegate());
            assertEquals(assist.delegate(), assist.held().isPresent());
            audits += assist.audit() ? 1 : 0;
            delegations += assist.delegate() ? 1 : 0;
        }
        assertEquals(500, audits + delegations);
        assertTrue(audits > 25 && audits < 75, "about one capture in ten is audited: " + audits);
    }

    private static DistillStep.Request request(DistillMode mode, int index) {
        return new DistillStep.Request(mode, true, Optional.empty(), DistillSource.TABLE, "press", 2L, index, 10,
                false, false, 4, 10L, 30);
    }
}
