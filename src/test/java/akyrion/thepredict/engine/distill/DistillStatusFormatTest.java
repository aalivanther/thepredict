package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.DistillMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Status names state, mode and regime and names no player. */
class DistillStatusFormatTest {
    @Test
    void theLineIsFixedAndHasNoPlayer() {
        var line = DistillStatus.line(DistilledArtifacts.State.ACTIVE, DistillMode.ASSIST, "r1:ALL");
        assertEquals("state=ACTIVE mode=assist regime=r1:ALL", line);
        assertFalse(line.contains("player"));
        assertFalse(line.contains("uuid"));
    }

    @Test
    void theLiveLineFollowsTheCommandedModeAndDisable() {
        var control = new DistillControl(DistillMode.ASSIST, DistillMode.ASSIST);
        assertEquals("state=NONE mode=shadow regime=r1:ALL", DistillStatus.line(control, DistillMode.SHADOW));
        control.raise(DistillMode.ASSIST);
        assertEquals("state=NONE mode=assist regime=r1:ALL", DistillStatus.line(control, DistillMode.AUTONOMOUS));
        control.disable();
        assertEquals("state=NONE mode=off regime=r1:ALL", DistillStatus.line(control, DistillMode.ASSIST));
    }
}
