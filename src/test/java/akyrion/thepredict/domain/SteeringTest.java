package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Geometry of the micro destinations (M20.5): band and arena caps, the orbit step and its symmetry. */
class SteeringTest {
    private static final double EPSILON = 1e-9;

    @Test
    void anOrbitStepsAroundTheTargetInsideTheArena() {
        var point = Steering.orbit(10, 0, 0, 0, MicroSide.LEFT, 3, 8, 0, 0, 64);
        var radius = StrictMath.hypot(point.x(), point.z());
        assertTrue(radius >= 3 && radius <= 8);
        assertEquals(point, Steering.orbit(10, 0, 0, 0, MicroSide.LEFT, 3, 8, 0, 0, 64));
    }

    @Test
    void aMobStandingOnTheTargetGetsAFiniteOrbitAtTheInnerBand() {
        var point = Steering.orbit(0, 0, 0, 0, MicroSide.LEFT, 5, 9, 0, 0, 0);
        assertTrue(Double.isFinite(point.x()) && Double.isFinite(point.z()), point.toString());
        assertEquals(5.0, StrictMath.hypot(point.x(), point.z()), EPSILON);
        var right = Steering.orbit(3, 4, 3, 4, MicroSide.RIGHT, 2, 9, 0, 0, 0);
        assertEquals(2.0, StrictMath.hypot(right.x() - 3, right.z() - 4), EPSILON);
        assertEquals(new Steering(3, 4), Steering.orbit(3, 4, 3, 4, MicroSide.LEFT, 0, 9, 0, 0, 0));
    }

    @Test
    void theOrbitStepIsOneAndAHalfBlocksOfArcAndKeepsTheRadiusInTheBand() {
        var radius = 6.0;
        var point = Steering.orbit(radius, 0, 0, 0, MicroSide.LEFT, 3, 8, 0, 0, 0);
        assertEquals(radius, StrictMath.hypot(point.x(), point.z()), EPSILON);
        var angle = StrictMath.atan2(point.z(), point.x());
        assertEquals(Steering.ORBIT_STEP, angle * radius, EPSILON);
        var far = Steering.orbit(20, 0, 0, 0, MicroSide.LEFT, 3, 8, 0, 0, 0);
        assertEquals(8.0, StrictMath.hypot(far.x(), far.z()), EPSILON);
        var near = Steering.orbit(1, 0, 0, 0, MicroSide.LEFT, 3, 8, 0, 0, 0);
        assertEquals(3.0, StrictMath.hypot(near.x(), near.z()), EPSILON);
    }

    @Test
    void leftAndRightAreMirrorImagesAcrossTheLineToTheMob() {
        var left = Steering.orbit(6, 0, 0, 0, MicroSide.LEFT, 3, 8, 0, 0, 0);
        var right = Steering.orbit(6, 0, 0, 0, MicroSide.RIGHT, 3, 8, 0, 0, 0);
        assertEquals(left.x(), right.x(), EPSILON);
        assertEquals(left.z(), -right.z(), EPSILON);
        assertTrue(left.z() > 0.0);
        var sideLeft = Steering.sidestep(6, 0, 0, 0, MicroSide.LEFT);
        var sideRight = Steering.sidestep(6, 0, 0, 0, MicroSide.RIGHT);
        assertEquals(Steering.STEP_BLOCKS, StrictMath.hypot(sideLeft.x() - 6, sideLeft.z()), EPSILON);
        assertEquals(sideLeft.z(), -sideRight.z(), EPSILON);
    }

    @Test
    void theArenaRadiusPullsAnOrbitBackInside() {
        var free = Steering.orbit(8, 0, 0, 0, MicroSide.LEFT, 3, 8, 0, 0, 64);
        assertEquals(8.0, StrictMath.hypot(free.x(), free.z()), EPSILON);
        var capped = Steering.orbit(8, 0, 0, 0, MicroSide.LEFT, 3, 8, 0, 0, 4);
        assertEquals(4.0, StrictMath.hypot(capped.x(), capped.z()), EPSILON);
        assertEquals(StrictMath.atan2(free.z(), free.x()), StrictMath.atan2(capped.z(), capped.x()), EPSILON);
    }

    @Test
    void backstepAndHoverStayOnTheLineFromTheTarget() {
        var back = Steering.backstep(4, 0, 0, 0);
        assertEquals(new Steering(4 + Steering.STEP_BLOCKS, 0), back);
        var hover = Steering.hover(10, 0, 0, 0, 3.0);
        assertEquals(new Steering(3.5, 0), hover);
        assertEquals(1.1 * 1.15, Steering.enterSpeed(1.15), EPSILON);
    }
}
