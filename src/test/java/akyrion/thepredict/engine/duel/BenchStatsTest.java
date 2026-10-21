package akyrion.thepredict.engine.duel;

import akyrion.thepredict.eval.EvalStats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The statistics of a cell (§7.6, SIM-06): the side test reads only the discordant pairs of a seed, the bound is
 * one-sided with the quantile of its family, and a draw counts half.
 */
class BenchStatsTest {
    private static BenchCell.Duel duel(long seed, boolean red, BenchCell.Outcome outcome) {
        return new BenchCell.Duel(seed, red, outcome, 100, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), Map.of(),
                Map.of(), Conduct.EMPTY);
    }

    private static BenchCell.Result cell(BenchCell.Outcome[] a, BenchCell.Outcome[] b) {
        var spec = new BenchCell.Spec("r", ClassicConfig.pack(Kit.K1), Scenario.bench(Kit.K1, Arena.OPEN18, 60),
                BenchProfiles.Timing.DEFAULTS, "NONE", Optional.empty());
        var left = new ArrayList<BenchCell.Duel>();
        var right = new ArrayList<BenchCell.Duel>();
        for (var at = 0; at < a.length; at++) {
            left.add(duel(at, true, a[at]));
            right.add(duel(at, false, b[at]));
        }
        return new BenchCell.Result(spec, left, right, new int[a.length]);
    }

    @Test
    void pairedComparisonUsesDiscordantPairs() {
        var win = BenchCell.Outcome.WIN;
        var loss = BenchCell.Outcome.LOSS;
        var concordant = cell(new BenchCell.Outcome[] {win, win, loss, loss, win, win},
                new BenchCell.Outcome[] {win, win, loss, loss, win, win});
        assertEquals(1.0, concordant.sides(), 0.0, "pairs that agree say nothing about the side");
        var red = cell(new BenchCell.Outcome[] {win, win, win, win, win, win},
                new BenchCell.Outcome[] {loss, loss, loss, loss, loss, loss});
        assertEquals(EvalStats.mcnemar(6, 0), red.sides(), 0.0);
        assertTrue(red.sides() < 0.05);
    }

    @Test
    void aDrawCountsHalfAndTheBoundFollowsItsFamily() {
        var draws = cell(new BenchCell.Outcome[] {BenchCell.Outcome.DRAW, BenchCell.Outcome.WIN},
                new BenchCell.Outcome[] {BenchCell.Outcome.DRAW, BenchCell.Outcome.LOSS});
        assertEquals(0.5, draws.score(), 0.0);
        assertEquals(2, draws.draws());
        var outcomes = new BenchCell.Outcome[200];
        java.util.Arrays.fill(outcomes, BenchCell.Outcome.WIN);
        for (var at = 0; at < 38; at++) {
            outcomes[at] = BenchCell.Outcome.LOSS;
        }
        var strong = cell(outcomes, outcomes);
        assertEquals(324, strong.wins());
        assertTrue(strong.lower(BenchOutcome.z(1)) > strong.lower(BenchOutcome.z(29)), "Bonferroni widens the bound");
        assertTrue(strong.lower(BenchOutcome.z(24)) < BenchSettings.GOAL, "324 of 400 does not reach 0.75 for 24");
    }
}
