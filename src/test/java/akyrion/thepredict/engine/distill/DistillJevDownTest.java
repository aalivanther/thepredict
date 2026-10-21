package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSource;
import akyrion.thepredict.domain.distill.OodCause;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Jev being down does not stop a covered student. An uncovered cell stays local and does not call a client. */
class DistillJevDownTest {
    @Test
    void coveredDelegatesAndUncoveredDoesNotAndTheStepTakesNoClient() {
        var covered = DistillStep.consider(new DistillStep.Request(DistillMode.ASSIST, true, Optional.empty(),
                DistillSource.TABLE, "press", 2L, 1, 10, false, false, 4, 10L, 30));
        assertTrue(covered.delegate());
        var local = DistillStep.consider(new DistillStep.Request(DistillMode.ASSIST, false,
                Optional.of(OodCause.OOD_CELL_UNSEEN), DistillSource.TABLE, "press", 2L, 1, 10, false, false, 4, 10L,
                30));
        assertFalse(local.delegate());
        assertTrue(local.held().isEmpty());
        for (var component : DistillStep.Request.class.getRecordComponents()) {
            assertFalse(component.getType().getName().startsWith("akyrion.thepredict.adapter"), component.getName());
            assertFalse(component.getType().getSimpleName().contains("Client"), component.getName());
        }
        for (var method : DistillStep.class.getDeclaredMethods()) {
            for (var type : method.getParameterTypes()) {
                assertFalse(type.getSimpleName().contains("Client"), method.getName());
            }
        }
    }
}
