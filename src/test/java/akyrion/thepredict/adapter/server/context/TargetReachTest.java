package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.context.EntityDoubles.FakeEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.entity.Zombie;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reach of a target (M17.4, DT-37): the vanilla melee check of {@code Mob.isWithinMeleeAttackRange} (javap on the
 * patched Paper 26.2 server) with boxes of 0.6 × 1.95 blocks, the gap of a rival of ThePredict, the distance of a
 * player, and nothing when the reach is unknown.
 */
class TargetReachTest {
    private static final double REACH = TargetReach.MOB_DEFAULT_ATTACK_REACH_BLOCKS;
    private static final BoundingBox VICTIM = box(0, 64, 0);

    private final CaptureDoubles doubles = new CaptureDoubles();

    private static BoundingBox box(double x, double y, double z) {
        return new BoundingBox(x - 0.3, y, z - 0.3, x + 0.3, y + 1.95, z + 0.3);
    }

    /** Box whose near faces stand {@code gapX} and {@code gapZ} away from those of {@link #VICTIM}. */
    private static BoundingBox apart(double gapX, double gapY, double gapZ) {
        return box(0.6 + gapX, 64 + gapY, 0.6 + gapZ);
    }

    private FakeEntity entity(int value, BoundingBox box) {
        var entity = new FakeEntity(new UUID(0x7EAL, value), Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER,
                doubles.world, box.getCenterX(), box.getCenterZ());
        entity.box = box;
        return entity;
    }

    @Test
    void eachHorizontalAxisIsCheckedApartSoADiagonalGapBeyondTheReachIsStillWithin() {
        var diagonal = apart(0.82, 0, 0.82);
        assertTrue(TargetReach.withinVanillaMelee(diagonal, VICTIM, REACH, 0.0));
        assertEquals(1.16, ReachGeometry.gapBlocks(diagonal, VICTIM), 0.01, "the euclidean gap is about 1.16");
        assertTrue(TargetReach.withinVanillaMelee(apart(0.82, 0, 0), VICTIM, REACH, 0.0));
        assertFalse(TargetReach.withinVanillaMelee(apart(0.83, 0, 0), VICTIM, REACH, 0.0));
        assertFalse(TargetReach.withinVanillaMelee(apart(0.5, 0, 0.83), VICTIM, REACH, 0.0));
    }

    @Test
    void theBoxIsNotInflatedVerticallySoTheBoxesMustOverlapInHeight() {
        assertTrue(TargetReach.withinVanillaMelee(apart(0.1, 1.9, 0), VICTIM, REACH, 0.0), "overlap of 0.05");
        assertFalse(TargetReach.withinVanillaMelee(apart(0.1, 1.95, 0), VICTIM, REACH, 0.0), "touching is not");
        assertFalse(TargetReach.withinVanillaMelee(box(0, 66.2, 0), VICTIM, REACH, 0.0), "right above, 0.25 apart");
    }

    @Test
    void aMinimumReachOfTheWeaponLeavesACloseVictimOutOfReach() {
        var spear = TargetReach.vanillaMelee(true, 4.5f, 2.0f, 0.5f);
        assertEquals(2.25, spear.maxBlocks(), 1e-6);
        assertEquals(1.0, spear.minBlocks(), 1e-6);
        assertFalse(TargetReach.withinVanillaMelee(apart(0.5, 0, 0), VICTIM, 2.25, 1.0), "inside the minimum");
        assertTrue(TargetReach.withinVanillaMelee(apart(1.5, 0, 0), VICTIM, 2.25, 1.0));
        assertFalse(TargetReach.withinVanillaMelee(apart(2.3, 0, 0), VICTIM, 2.25, 1.0));
    }

    @Test
    void theOutsideFactUsesTheBoxesOfTheMobAndItsTargetAndTheVehicleOfTheTarget() {
        var mob = entity(1, VICTIM);
        var target = entity(2, apart(1.0, 0, 0));
        var melee = TargetReach.vanillaMelee(false, 0f, 0f, 0f);
        assertTrue(melee.outside(mob.entity, target.entity, 1.6));
        var horse = entity(3, new BoundingBox(0.5, 64, -0.7, 2.2, 65.6, 0.7));
        target.vehicle = horse.entity;
        assertFalse(melee.outside(mob.entity, target.entity, 1.6), "the vehicle widens the attack box on x and z");
    }

    @Test
    void eachKindIsComparedAsItsSourceMeasuresIt() {
        var mob = entity(1, VICTIM);
        var target = entity(2, apart(2.0, 0, 0));
        assertFalse(TargetReach.UNKNOWN.outside(mob.entity, target.entity, 100.0), "unknown is never out of reach");
        assertTrue(TargetReach.player(3.0, false, 0f).outside(mob.entity, target.entity, 3.1));
        assertFalse(TargetReach.player(3.0, false, 0f).outside(mob.entity, target.entity, 3.0));
        assertTrue(TargetReach.profile(1.9).outside(mob.entity, target.entity, 0.0));
        assertFalse(TargetReach.profile(2.01).outside(mob.entity, target.entity, 0.0));
    }

    @Test
    void anAttackRangeWithValuesThatAreNotUsableGivesNothingAndABadReachIsRejected() {
        assertEquals(TargetReach.UNKNOWN, TargetReach.vanillaMelee(true, 1.0f, 2.0f, 1.0f));
        assertEquals(TargetReach.UNKNOWN, TargetReach.vanillaMelee(true, Float.NaN, 0.0f, 1.0f));
        assertEquals(TargetReach.UNKNOWN, TargetReach.vanillaMelee(true, 3.0f, 0.0f, -1.0f));
        assertThrows(IllegalArgumentException.class, () -> new TargetReach(TargetReach.Kind.DISTANCE, -1.0, 0.0));
        assertThrows(NullPointerException.class, () -> new TargetReach(null, 1.0, 0.0));
    }
}
