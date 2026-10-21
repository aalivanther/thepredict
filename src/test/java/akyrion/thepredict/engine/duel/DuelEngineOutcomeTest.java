package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.OutcomeWindow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the ports of a server the hits of the world reach the outcome windows of the engine as the listeners report
 * them (SIM-07): a window that landed a strike counts it as dealt, one that took a hit counts it as taken. With the
 * ports of M18.7 nothing reaches them and every window measures only {@code 0.25·reached}.
 */
class DuelEngineOutcomeTest {
    private static List<OutcomeWindow.Summary> windows(TickRules rules) {
        var setup = new DuelSimulator.Setup(DuelPhysics.VANILLA, rules, Scenario.bench(Kit.K1, Arena.OPEN18, 60),
                ClassicConfig.pack(Kit.K1), ClassicConfig.pack(Kit.K1));
        var summaries = new ArrayList<OutcomeWindow.Summary>();
        for (var seed = 1L; seed <= 3; seed++) {
            var simulator = new DuelSimulator(new DuelSimulator.Mode.None(), Map.of(), setup);
            simulator.listen(new DecisionTap() {
                @Override
                public void outcomeClosed(MobKey mob, OutcomeWindow.Summary summary) {
                    summaries.add(summary);
                }
            });
            simulator.round(DuelSimulator.Arm.A, seed, false, DuelSimulator.Options.PLAIN);
        }
        return summaries;
    }

    @Test
    void aLandedStrikeCountsAsDealt() {
        var server = windows(TickRules.FAIR);
        assertTrue(server.stream().anyMatch(summary -> summary.dealt() > 0), "a window dealt damage");
        assertTrue(server.stream().anyMatch(summary -> summary.taken() > 0), "a window took damage");
        var legacy = windows(new TickRules(true, true, false));
        assertTrue(!legacy.isEmpty() && legacy.stream().allMatch(summary -> summary.dealt() <= 0.0
                && summary.taken() <= 0.0), "without the ports no hit reaches a window");
    }
}
