package akyrion.thepredict.adapter.server.context;

import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldGeometryTest {
    private static final double TOLERANCE = 1e-9;

    private static Location at(double x, double z, float yaw) {
        return new Location(null, x, 64, z, yaw, 0);
    }

    private static DamageReduction reduction(float angle) {
        return (DamageReduction) Proxy.newProxyInstance(ShieldGeometryTest.class.getClassLoader(),
                new Class<?>[] {DamageReduction.class}, (instance, method, args) -> {
                    if (method.getName().equals("horizontalBlockingAngle")) {
                        return angle;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void facingFollowsMinecraftYawInTheFourQuadrants() {
        assertOffset(0, 1, ShieldGeometry.offset(0f, 0, 1));
        assertOffset(-1, 0, ShieldGeometry.offset(90f, 0, 1));
        assertOffset(0, -1, ShieldGeometry.offset(180f, 0, 1));
        assertOffset(1, 0, ShieldGeometry.offset(-90f, 0, 1));
        assertOffset(1, 0, ShieldGeometry.offset(270f, 0, 1));
        assertEquals(2.5, ShieldGeometry.offset(37f, 110, 2.5).length(), TOLERANCE);
    }

    @Test
    void angleFromFacingIsZeroInFrontNinetyAsideAndOneEightyBehindInEveryQuadrant() {
        for (var yaw : new float[] {0f, 90f, 180f, -90f, 45f, 225f}) {
            var front = ShieldGeometry.offset(yaw, 0, 3);
            var side = ShieldGeometry.offset(yaw, 90, 3);
            var back = ShieldGeometry.offset(yaw, 180, 3);
            assertEquals(0, ShieldGeometry.angleFromFacingDegrees(yaw, front.getX(), front.getZ()), 1e-6, "yaw " + yaw);
            assertEquals(90, ShieldGeometry.angleFromFacingDegrees(yaw, side.getX(), side.getZ()), 1e-6, "yaw " + yaw);
            assertEquals(180, ShieldGeometry.angleFromFacingDegrees(yaw, back.getX(), back.getZ()), 1e-6, "yaw " + yaw);
        }
    }

    @Test
    void anAttackerOnTheSameSpotCountsAsNinetyDegreesLikeVanilla() {
        assertEquals(90, ShieldGeometry.angleFromFacingDegrees(0f, 0, 0), TOLERANCE);
        assertEquals(90, ShieldGeometry.angleFromFacingDegrees(0f, 1e-6, -1e-6), TOLERANCE);
        assertFalse(ShieldGeometry.isOutsideBlockingAngle(at(5, 5, 0f), at(5, 5, 0f), 90));
    }

    @Test
    void theNinetyDegreeBoundaryIsBlockedAndJustBeyondIsNot() {
        var target = at(0, 0, 0f);
        var inside = ShieldGeometry.offset(0f, 89.9, 3);
        var outside = ShieldGeometry.offset(0f, 90.1, 3);
        assertFalse(ShieldGeometry.isOutsideBlockingAngle(target, at(inside.getX(), inside.getZ(), 0f), 90));
        assertTrue(ShieldGeometry.isOutsideBlockingAngle(target, at(outside.getX(), outside.getZ(), 0f), 90));
        assertFalse(ShieldGeometry.isOutsideBlockingAngle(target, at(3, 0, 0f), 90));
        assertTrue(ShieldGeometry.isOutsideBlockingAngle(target, at(3, 0, 0f), 90.0 - 1e-6));
    }

    @Test
    void anAttackFromEitherFlankDestinationEscapesTheShieldForAnyYawAndAngle() {
        for (var yaw : new float[] {0f, 30f, 90f, 135f, 180f, 225f, 270f, -45f, 359f}) {
            for (var angle : new double[] {30, 60, 90, 120, 150, 170, 179}) {
                var target = at(10, -4, yaw);
                var turn = ShieldGeometry.flankTurnDegrees(angle);
                for (var side : new double[] {turn, -turn}) {
                    var offset = ShieldGeometry.offset(yaw, side, 2.5);
                    var attacker = at(10 + offset.getX(), -4 + offset.getZ(), 0f);
                    assertTrue(ShieldGeometry.isOutsideBlockingAngle(target, attacker, angle),
                            "yaw " + yaw + " angle " + angle + " side " + side);
                }
            }
        }
    }

    @Test
    void flankTurnAddsTheMarginAndStopsStraightBehind() {
        assertEquals(110, ShieldGeometry.flankTurnDegrees(90), TOLERANCE);
        assertEquals(180, ShieldGeometry.flankTurnDegrees(160), TOLERANCE);
        assertEquals(180, ShieldGeometry.flankTurnDegrees(175), TOLERANCE);
        assertEquals(20, ShieldGeometry.flankTurnDegrees(0), TOLERANCE);
    }

    @Test
    void blockingAngleIsTheWidestReductionOrNinetyWithoutAny() {
        assertEquals(90, ShieldGeometry.blockingAngleDegrees(List.of()), TOLERANCE);
        assertEquals(120, ShieldGeometry.blockingAngleDegrees(List.of(reduction(45f), reduction(120f), reduction(90f))),
                TOLERANCE);
        assertEquals(30, ShieldGeometry.blockingAngleDegrees(List.of(reduction(30f))), TOLERANCE);
    }

    /**
     * Since M17.4 any living target may raise a shield; one without an active item has none, and the obsolete
     * {@code isHandRaised} is never read: only {@code hasActiveItem} answers.
     */
    @Test
    void aTargetWithoutAnActiveItemHasNoRaisedShieldWhateverItsKind() {
        var zombie = (LivingEntity) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {LivingEntity.class},
                (instance, method, args) -> switch (method.getName()) {
                    case "hasActiveItem" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        assertTrue(ShieldGeometry.raisedShieldAngleDegrees(zombie).isEmpty());
    }

    private static void assertOffset(double x, double z, Vector offset) {
        assertEquals(x, offset.getX(), TOLERANCE);
        assertEquals(0, offset.getY(), TOLERANCE);
        assertEquals(z, offset.getZ(), TOLERANCE);
    }
}
