package akyrion.thepredict.adapter.server.goals;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlankPathsTest {
    private static Location at(double x, double y, double z, float yaw) {
        return new Location(null, x, y, z, yaw, 0);
    }

    @Test
    void movingOneBlockOrLessDoesNotJustifyANewPlan() {
        assertFalse(FlankPaths.targetMovedOrTurned(at(0, 64, 0, 0f), at(1, 64, 0, 0f)));
        assertFalse(FlankPaths.targetMovedOrTurned(at(0, 64, 0, 0f), at(0.6, 64.6, 0.5, 0f)));
        assertTrue(FlankPaths.targetMovedOrTurned(at(0, 64, 0, 0f), at(1.01, 64, 0, 0f)));
        assertTrue(FlankPaths.targetMovedOrTurned(at(0, 64, 0, 0f), at(0, 65.5, 0, 0f)));
    }

    @Test
    void turningThirtyDegreesOrLessDoesNotJustifyANewPlanAcrossTheWrap() {
        assertFalse(FlankPaths.targetMovedOrTurned(at(0, 64, 0, 10f), at(0, 64, 0, 40f)));
        assertTrue(FlankPaths.targetMovedOrTurned(at(0, 64, 0, 10f), at(0, 64, 0, 40.5f)));
        assertFalse(FlankPaths.targetMovedOrTurned(at(0, 64, 0, 350f), at(0, 64, 0, 15f)));
        assertTrue(FlankPaths.targetMovedOrTurned(at(0, 64, 0, 350f), at(0, 64, 0, 25f)));
        assertFalse(FlankPaths.targetMovedOrTurned(at(0, 64, 0, -170f), at(0, 64, 0, 170f)));
    }

    @Test
    void angleDifferencesWrapIntoHalfOpenRange() {
        assertEquals(-180, FlankPaths.wrapDegrees(180), 1e-9);
        assertEquals(-170, FlankPaths.wrapDegrees(190), 1e-9);
        assertEquals(170, FlankPaths.wrapDegrees(-190), 1e-9);
        assertEquals(0, FlankPaths.wrapDegrees(720), 1e-9);
    }
}
