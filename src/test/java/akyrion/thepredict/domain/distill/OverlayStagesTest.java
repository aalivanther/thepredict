package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OverlayStagesTest {
    @Test
    void aHigherModeAndAnOffZoneAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> OverlayStages.check("Zone.Mode", "off", 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> OverlayStages.check("Group.Mode", "off", 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> OverlayStages.check("Distill.Mode", "autonomous", DistillMode.SHADOW.rank(),
                        DistillMode.AUTONOMOUS.rank()));
        OverlayStages.check("Distill.Mode", "shadow", DistillMode.ASSIST.rank(), DistillMode.SHADOW.rank());
        assertEquals("gate", OverlayStages.perKey(Map.of("Threat.Weight", "gate")).get("Threat.Weight"));
    }
}
