package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The damage per minute of a cell: health in points from the shares of each round, per minute of play over both arms,
 * and by skill the casts and the hits of each side, with the vanilla hit of a classic only as hits.
 */
class BenchDamageTest {
    @Test
    void perMinuteOverBothArms() {
        var red = new BenchCell.Duel(1, true, BenchCell.Outcome.WIN, 1200, 0.5, 0.25, 2, 0, 0, 0, 0,
                Map.of("DuelStrike", 10, "DuelBolt", 4), Map.of("DuelStrike", 8),
                Map.of("DuelStrike", 10, "DuelBolt", 2), Map.of("DuelStrike", 8, "vanilla", 3), Conduct.EMPTY);
        var blue = new BenchCell.Duel(1, false, BenchCell.Outcome.LOSS, 1200, 0.5, 0.75, 0, 1, 0, 0, 0,
                Map.of("DuelStrike", 6), Map.of("DuelBolt", 2), Map.of("DuelStrike", 6), Map.of("DuelBolt", 2),
                Conduct.EMPTY);
        var spec = new BenchCell.Spec("pack", ClassicConfig.pack(Kit.K1), Scenario.bench(Kit.K1, Arena.OPEN18, 60),
                BenchProfiles.Timing.DEFAULTS, "NONE", Optional.empty());
        var damage = new BenchCell.Result(spec, List.of(red), List.of(blue), new int[1]).damage();
        assertEquals(30, damage.dealt(), 1e-9, "60 points of health over two minutes");
        assertEquals(30, damage.taken(), 1e-9);
        assertEquals(1, damage.lost(), 1e-9);
        assertEquals(0.5, damage.rivalLost(), 1e-9);
        assertEquals(List.of(new BenchDamage.Skill("DuelBolt", 2, 1, 1, 1),
                new BenchDamage.Skill("DuelStrike", 8, 8, 4, 4),
                new BenchDamage.Skill("vanilla", 0, 0, 0, 1.5)), damage.skills());
    }
}
