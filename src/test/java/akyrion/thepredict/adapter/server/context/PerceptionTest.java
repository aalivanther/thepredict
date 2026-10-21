package akyrion.thepredict.adapter.server.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerceptionTest {
    private final CaptureDoubles doubles = new CaptureDoubles();

    @Test
    void theRadiusIsTheFollowRangeCappedByTheSearchRadius() {
        assertEquals(16.0, Perception.radiusBlocks(16, 32));
        assertEquals(32.0, Perception.radiusBlocks(40, 32));
        assertEquals(32.0, Perception.radiusBlocks(32, 32));
    }

    @Test
    void aMissingOrBrokenFollowRangeFallsBackToTheCap() {
        assertEquals(32.0, Perception.radiusBlocks(0, 32));
        assertEquals(32.0, Perception.radiusBlocks(-5, 32));
        assertEquals(32.0, Perception.radiusBlocks(Double.NaN, 32));
        assertEquals(32.0, Perception.radiusBlocks(Double.POSITIVE_INFINITY, 32));
    }

    @Test
    void theSphereIncludesItsSurfaceAndExcludesTheBoxCorners() {
        assertTrue(Perception.withinRadius(256, 16));
        assertFalse(Perception.withinRadius(Math.nextUp(256.0), 16));
        assertFalse(Perception.withinRadius(16 * 16 * 2, 16));
    }

    @Test
    void visibilityNeedsSightAndNoInvisibility() {
        assertTrue(Perception.visible(true, false));
        assertFalse(Perception.visible(true, true));
        assertFalse(Perception.visible(false, false));
        assertFalse(Perception.visible(false, true));
    }

    @Test
    void aLineOfSightIsComputedOncePerPairInOneCapture() {
        var mob = doubles.mob(1, 0, 0);
        var first = doubles.player(1, 2, 0);
        var second = doubles.player(2, 3, 0);
        mob.sight = player -> player.equals(first.id);
        var perception = new Perception();
        for (var round = 0; round < 3; round++) {
            assertTrue(perception.lineOfSight(mob.entity, first.player));
            assertFalse(perception.lineOfSight(mob.entity, second.player));
        }
        assertEquals(2, mob.sightChecks);
        assertEquals(2, perception.computedLinesOfSight());
    }
}
