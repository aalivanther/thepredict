package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.TickSignals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-tick reading of the simulator is the one {@code TickSignalReader} packs on a server (E-8): the target, the
 * gap, the window of the rival and both healths in the bits the engine reads. With the ports of M18.7 it stays
 * {@code PRESENT}; with the ports of a server the reactions of M20.4 can fire.
 */
class DuelSignalsTest {
    @TempDir
    Path root;

    private static DuelWorld world() {
        var red = new DuelWorld.Body(new UUID(6, 0), DuelWorld.Side.RED, 0, -2);
        var blue = new DuelWorld.Body(new UUID(6, 1), DuelWorld.Side.BLUE, 0, 2);
        return new DuelWorld(red, blue, hit -> {
        }, DuelPhysics.VANILLA, TickRules.FAIR, Arena.OPEN18, 0);
    }

    @Test
    void theReadingCarriesTheTargetTheGapTheWindowAndBothHealths() {
        var world = world();
        world.red.health = 30;
        world.red.absorption = 6;
        world.blue.health = 45;
        world.blue.invulnerableUntil = 106;
        var signals = DuelSignals.read(world, world.red, world.blue.id, 100);
        assertTrue(TickSignals.bit(signals, TickSignals.VALID));
        assertTrue(TickSignals.bit(signals, TickSignals.HAS_TARGET));
        assertTrue(TickSignals.bit(signals, TickSignals.MOB_ON_GROUND));
        assertFalse(TickSignals.bit(signals, TickSignals.MOB_STAGGERED));
        assertFalse(TickSignals.bit(signals, TickSignals.TARGET_MISMATCH));
        assertEquals(TickSignals.quartersOf(4 - DuelWorld.WIDTH), TickSignals.gapQuarters(signals));
        assertEquals(6, TickSignals.hittableTicks(signals));
        assertEquals(60, TickSignals.mobHealthPercent(signals));
        assertEquals(75, TickSignals.targetHealthPercent(signals));
        assertEquals(0, TickSignals.octant(signals), "a rival that never turned looks at the mob");
        assertEquals(0, TickSignals.facingError(signals));
    }

    @Test
    void aDeadMobReadsZeroAndAnotherTargetReadsNoTarget() {
        var world = world();
        var other = DuelSignals.read(world, world.red, new UUID(7, 7), 0);
        assertTrue(TickSignals.bit(other, TickSignals.VALID));
        assertFalse(TickSignals.bit(other, TickSignals.HAS_TARGET));
        world.red.flightTicks = 3;
        world.red.invulnerableUntil = 5;
        var flying = DuelSignals.read(world, world.red, world.blue.id, 0);
        assertFalse(TickSignals.bit(flying, TickSignals.MOB_ON_GROUND), "a leap flies");
        assertTrue(TickSignals.bit(flying, TickSignals.MOB_STAGGERED));
        world.red.health = 0;
        assertEquals(0L, DuelSignals.read(world, world.red, world.blue.id, 0));
    }

    @Test
    void theLegacyPortsReadOnlyPresent() {
        var engine = new DuelEngine(new DuelSimulator.Mode.None(), akyrion.thepredict.engine.DecisionTap.NONE);
        var world = world();
        engine.round(world, (body, skill) -> {
        });
        var reader = new DuelReader(engine);
        assertEquals(TickSignals.PRESENT, reader.tickSignals(new MobKey(world.red.id, 0),
                world.blue.id));
    }

    @Test
    void reactionsFireOnlyWithTheReadingOfAServer() {
        var profiles = BenchProfiles.load(Kit.K1, BenchProfiles.Timing.DEFAULTS, root.resolve("defaults"));
        var rival = ClassicSpace.legalEqual(Kit.K1, profiles.get("DuelRed").movement());
        assertTrue(reactions(profiles, rival, TickRules.FAIR) > 0, "with the server reading a reaction fires");
        assertEquals(0, reactions(profiles, rival, new TickRules(true, true, false)), "with PRESENT none does");
    }

    private static int reactions(Map<String, akyrion.thepredict.domain.TacticalProfile> profiles,
                                 ClassicConfig rival, TickRules rules) {
        var setup = new DuelSimulator.Setup(DuelPhysics.VANILLA, rules, Scenario.bench(Kit.K1, Arena.OPEN18, 60),
                ClassicConfig.pack(Kit.K1), rival);
        var fired = 0;
        for (var seed = 1L; seed <= 6; seed++) {
            var simulator = new DuelSimulator(new DuelSimulator.Mode.None(), profiles, setup);
            var counted = new int[1];
            simulator.listen(new akyrion.thepredict.engine.DecisionTap() {
                @Override
                public void reactionFired(MobKey mob, akyrion.thepredict.domain.Reaction reaction) {
                    counted[0]++;
                }
            });
            simulator.round(DuelSimulator.Arm.A, seed, false, DuelSimulator.Options.PLAIN);
            fired += counted[0];
        }
        return fired;
    }
}
