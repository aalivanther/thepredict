package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The knobs of the duel world (§7.2 of the simulator audit): the vanilla rule of invulnerability (SIM-08 b), the
 * simultaneous landing that makes two lethal hits a draw (SIM-03), the flight of a leap, the knockback, the pillars and
 * the copy the judge plays forward. Each knob is checked against the world of M18.7, which keeps its old behaviour.
 */
class DuelWorldTest {
    private static DuelWorld world(DuelPhysics physics, TickRules rules, Arena arena, double redZ, double blueZ) {
        var red = new DuelWorld.Body(new UUID(1, 0), DuelWorld.Side.RED, 0, redZ);
        var blue = new DuelWorld.Body(new UUID(1, 1), DuelWorld.Side.BLUE, 0, blueZ);
        return new DuelWorld(red, blue, hit -> {
        }, physics, rules, arena, 0);
    }

    private static void land(DuelWorld world, long from, long to) {
        for (var tick = from; tick <= to; tick++) {
            world.commit(tick, true);
            world.step(tick);
        }
    }

    @Test
    void aStrongerHitInsideTheWindowDealsTheExcess() {
        var vanilla = world(DuelPhysics.VANILLA, TickRules.FAIR, Arena.OPEN18, -1, 1);
        vanilla.cast(vanilla.red, DuelSkill.BOLT, 0);
        land(vanilla, 0, 10);
        vanilla.cast(vanilla.red, DuelSkill.STRIKE, 10);
        land(vanilla, 10, 15);
        assertEquals(60 - 3 - (5 - 3), vanilla.blue.health, 1e-9, "the strike inside the window deals 5 − 3");
        var legacy = world(DuelPhysics.LEGACY, TickRules.LEGACY, Arena.OPEN18, -1, 1);
        legacy.cast(legacy.red, DuelSkill.BOLT, 0);
        legacy.step(10);
        legacy.cast(legacy.red, DuelSkill.STRIKE, 10);
        legacy.step(15);
        assertEquals(60 - 3, legacy.blue.health, 1e-9, "M18.7 loses the whole strike");
    }

    @Test
    void aWeakerHitInsideTheWindowIsLostAndCounted() {
        var vanilla = world(DuelPhysics.VANILLA, TickRules.FAIR, Arena.OPEN18, -1, 1);
        vanilla.cast(vanilla.red, DuelSkill.STRIKE, 0);
        land(vanilla, 0, 5);
        vanilla.cast(vanilla.red, DuelSkill.LEAP, 0);
        land(vanilla, 5, 10);
        assertEquals(55, vanilla.blue.health, 1e-9);
        assertEquals(1, vanilla.red.lostToInvulnerability);
    }

    @Test
    void twoLethalHitsOfOneTickAreADrawOnlyWithSimultaneousRules() {
        assertTrue(BenchGates.doubleKill(DuelPhysics.VANILLA, TickRules.FAIR).passed());
        assertTrue(BenchGates.doubleKill(DuelPhysics.LEGACY, new TickRules(true, true, false)).passed());
        assertFalse(BenchGates.doubleKill(DuelPhysics.LEGACY, TickRules.LEGACY).passed(),
                "M18.7 lands in cast order and skips the dead caster: red always wins");
    }

    @Test
    void aLeapFliesForItsTicksAndLandsTwoBlocksFromItsTarget() {
        var world = world(DuelPhysics.VANILLA, TickRules.FAIR, Arena.OPEN18, -5, 5);
        world.cast(world.red, DuelSkill.LEAP, 0);
        world.commit(0, true);
        assertEquals(-5, world.red.z, 1e-9, "a leap takes off; it does not teleport");
        for (var tick = 0; tick < DuelPhysics.VANILLA.leapFlightTicks(); tick++) {
            Motion.advance(world);
        }
        assertEquals(DuelWorld.LEAP_LANDING, world.distance(), 1e-6);
        var legacy = world(DuelPhysics.LEGACY, TickRules.LEGACY, Arena.OPEN18, -5, 5);
        legacy.cast(legacy.red, DuelSkill.LEAP, 0);
        assertEquals(DuelWorld.LEAP_LANDING, legacy.distance(), 1e-6, "M18.7 jumps at once");
    }

    @Test
    void aDamagingHitKnocksTheVictimBack() {
        var world = world(DuelPhysics.VANILLA, TickRules.FAIR, Arena.OPEN18, -1, 1);
        world.cast(world.red, DuelSkill.STRIKE, 0);
        land(world, 0, 5);
        assertEquals(2 + DuelPhysics.VANILLA.knockbackBlocks(), world.distance(), 1e-9);
    }

    @Test
    void aPillarBlocksTheSightAndTheWay() {
        var world = world(DuelPhysics.VANILLA, TickRules.FAIR, Arena.PILLARS18, 0, 0);
        world.red.x = 3;
        world.red.z = 3;
        world.blue.x = 9;
        world.blue.z = 9;
        assertFalse(world.lineOfSight(), "the pillar at (6, 6) stands between them");
        assertFalse(world.conditions(world.red, DuelSkill.BOLT), "the bolt needs sight");
        world.blue.x = 9;
        world.blue.z = 3;
        assertTrue(world.lineOfSight());
        world.red.x = 6.5;
        world.red.z = 6;
        Arena.PILLARS18.keepInside(world.red);
        assertTrue(Math.hypot(world.red.x - 6, world.red.z - 6) >= 1.5 + Arena.HALF_WIDTH - 1e-9);
    }

    @Test
    void theCopyPlaysForwardWithoutTouchingTheOriginal() {
        var hits = new ArrayList<DuelWorld.Hit>();
        var red = new DuelWorld.Body(new UUID(2, 0), DuelWorld.Side.RED, 0, -1);
        var blue = new DuelWorld.Body(new UUID(2, 1), DuelWorld.Side.BLUE, 0, 1);
        var world = new DuelWorld(red, blue, hits::add, DuelPhysics.VANILLA, TickRules.FAIR, Arena.OPEN18, 0);
        world.cast(red, DuelSkill.STRIKE, 0);
        world.commit(0, true);
        var copy = world.copy();
        for (var tick = 0L; tick <= 5; tick++) {
            copy.step(tick);
        }
        assertEquals(55, copy.blue.health, 1e-9);
        assertEquals(60, blue.health, 1e-9);
        assertTrue(hits.isEmpty(), "the copy reports its hits nowhere");
        assertTrue(world.windingUp(red, 1), "the original still holds the effect in flight");
    }
}
