package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.DemotionCause;
import akyrion.thepredict.domain.distill.DistillMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Demotion lowers one mode. Fairness goes straight to shadow. Only clear rises. */
class DistillGovernorTest {
    @Test
    void aConstantAuditGapAlarmsAndAZeroGapDoesNot() {
        var governor = new DistillGovernor();
        var alarms = 0;
        for (var step = 0; step < 200; step++) {
            if (governor.audit(-0.05, 5)) {
                alarms++;
            }
        }
        assertTrue(alarms >= 1);
        assertEquals(DistillMode.ASSIST, governor.floor());
        assertEquals(DemotionCause.AUDIT_DRIFT, governor.last().orElseThrow());
        var quiet = new DistillGovernor();
        for (var step = 0; step < 200; step++) {
            assertFalse(quiet.audit(0.0, 5));
        }
        assertEquals(DistillMode.AUTONOMOUS, quiet.floor());
    }

    @Test
    void fairnessDropsToShadowAndDisableIsTheNextRead() {
        var governor = new DistillGovernor();
        assertTrue(governor.fairness());
        assertEquals(DistillMode.SHADOW, governor.floor());
        assertFalse(governor.fairness());
        assertEquals(DistillMode.SHADOW, governor.effective(DistillMode.AUTONOMOUS, DistillMode.AUTONOMOUS));
        governor.clear();
        assertEquals(DistillMode.AUTONOMOUS, governor.floor());
        assertTrue(governor.outcome());
        assertEquals(DistillMode.ASSIST, governor.floor());
        var entropy = new DistillGovernor();
        for (var step = 0; step < 9; step++) {
            assertFalse(entropy.entropy(0.9));
        }
        assertTrue(entropy.entropy(0.9));
        var artifacts = new DistilledArtifacts();
        artifacts.publish("r1:ALL", DistillMode.ASSIST, DistillMode.AUTONOMOUS);
        assertEquals(DistillMode.ASSIST, artifacts.mode("r1:ALL"));
        artifacts.kill();
        assertEquals(DistillMode.OFF, artifacts.mode("r1:ALL"));
        artifacts.restore("r1:ALL", DistillMode.ASSIST);
        assertEquals(DistillMode.ASSIST, artifacts.mode("r1:ALL"));
    }

    @Test
    void theEntropyAlarmReadsACollapseTowardOneTacticNotVariedPlay() {
        var collapsed = new DistillSession();
        for (var adoption = 0; adoption < 19; adoption++) {
            collapsed.noteTactic("press");
        }
        collapsed.noteTactic("guard");
        assertEquals(0.95, collapsed.dominantShare(), 1.0e-12);
        assertTrue(collapsed.tacticEntropy() < 0.8, "a collapse has low entropy, so entropy is not the alarm input");
        var governor = new DistillGovernor();
        var lowered = false;
        for (var encounter = 0; encounter < 10; encounter++) {
            lowered = governor.entropy(collapsed.dominantShare());
        }
        assertTrue(lowered);
        var varied = new DistillSession();
        for (var adoption = 0; adoption < 10; adoption++) {
            varied.noteTactic("press");
            varied.noteTactic("guard");
        }
        assertEquals(1.0, varied.tacticEntropy(), 1.0e-12);
        var calm = new DistillGovernor();
        for (var encounter = 0; encounter < 30; encounter++) {
            assertFalse(calm.entropy(varied.dominantShare()));
        }
        assertEquals(0.0, new DistillSession().dominantShare(), 0.0);
    }
}
