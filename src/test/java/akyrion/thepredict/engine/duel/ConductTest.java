package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conduct a fair round reports agrees with the round itself: the shields it counts are the guards ThePredict cast,
 * the rival's hits are the hits the round counted for the rival, the pillars hide the rival some of the time, and
 * the rounds of M18.7 report nothing.
 */
class ConductTest {
    @Test
    void theConductAgreesWithTheRound() {
        var pack = ClassicConfig.pack(Kit.K1);
        var setup = new DuelSimulator.Setup(DuelPhysics.VANILLA, TickRules.FAIR, Scenario.bench(Kit.K1,
                Arena.PILLARS18, 60), pack, pack);
        for (var seed = 1L; seed <= 8; seed++) {
            var result = new DuelSimulator(new DuelSimulator.Mode.None(), Map.of(), setup).round(DuelSimulator.Arm.A,
                    seed, false, DuelSimulator.Options.PLAIN);
            var did = result.redConduct();
            assertEquals(result.redCasts().getOrDefault(DuelSkill.GUARD.id, 0), did.guards(), "seed " + seed);
            assertEquals(result.blueHits().values().stream().mapToInt(Integer::intValue).sum(), did.rivalStrikes(),
                    "seed " + seed);
            assertEquals(result.redHits().values().stream().mapToInt(Integer::intValue).sum(), did.hits());
            assertTrue(did.controlled() > 0 && did.controlled() <= result.ticks());
            assertTrue(did.guardsTimely() <= did.guardsUsed() && did.guardsUsed() <= did.guards());
            assertTrue(did.punishes() <= did.hits() && did.foreseen() <= did.rivalStrikes());
            assertTrue(did.forecastTicks() <= did.controlled());
        }
        var legacy = new DuelSimulator(new DuelSimulator.Mode.None()).round(DuelSimulator.Arm.A, 1, false,
                DuelSimulator.Options.PLAIN);
        assertEquals(Conduct.EMPTY, legacy.redConduct());
    }

    @Test
    void aPillarBetweenThemCountsAsTimeWithoutSightUntilItIsRecovered() {
        var red = new DuelWorld.Body(new java.util.UUID(8, 0), DuelWorld.Side.RED, 3, 3);
        var blue = new DuelWorld.Body(new java.util.UUID(8, 1), DuelWorld.Side.BLUE, 9, 9);
        var world = new DuelWorld(red, blue, hit -> {
        }, DuelPhysics.VANILLA, TickRules.FAIR, Arena.PILLARS18, 0);
        var tally = new ConductTally(red, java.util.Optional.of(DuelSkill.STRIKE));
        tally.tick(world);
        tally.tick(world);
        blue.z = 3;
        tally.tick(world);
        var did = tally.conduct(Map.of(), Double.NaN);
        assertEquals(2, did.noSight());
        assertEquals(1, did.sightGaps(), "two ticks behind the pillar, then sight again");
    }
}
