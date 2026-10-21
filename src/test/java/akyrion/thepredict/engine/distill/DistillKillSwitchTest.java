package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.DistillMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Disable is visible on the next read and does not rise by itself. */
class DistillKillSwitchTest {
    @Test
    void killDropsTheNextReadToOffUntilModeRestoresIt() {
        var artifacts = new DistilledArtifacts();
        artifacts.publish("r1:ALL", DistillMode.AUTONOMOUS, DistillMode.AUTONOMOUS);
        assertEquals(DistillMode.AUTONOMOUS, artifacts.mode("r1:ALL"));
        assertFalse(artifacts.killed());
        artifacts.kill();
        assertTrue(artifacts.killed());
        assertEquals(DistillMode.OFF, artifacts.mode("r1:ALL"));
        assertEquals(DistillMode.OFF, artifacts.mode("r1:ALL"));
        artifacts.restore("r1:ALL", DistillMode.ASSIST);
        assertFalse(artifacts.killed());
        assertEquals(DistillMode.ASSIST, artifacts.mode("r1:ALL"));
    }
}
