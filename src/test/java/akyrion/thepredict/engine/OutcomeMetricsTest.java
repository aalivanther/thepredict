package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The totals of M16.8: one cell per level and movement, means per level, and fixed memory. */
class OutcomeMetricsTest {
    private static OutcomeWindow.Summary summary(ControlLevel level, Movement movement, double dealt, double taken,
                                                 boolean reached, int casts, int hits) {
        return new OutcomeWindow.Summary(level, new TacticId("tactic"), movement, 0, 60, dealt, taken, reached, casts,
                hits, false, false, OutcomeWindow.Summary.effect(dealt, taken, reached, false, false));
    }

    @Test
    void eachLevelReportsItsMeansReachAndHitRate() {
        var metrics = new OutcomeMetrics();
        metrics.record(summary(ControlLevel.JEV, Movement.APPROACH, 0.2, 0.1, true, 2, 1));
        metrics.record(summary(ControlLevel.JEV, Movement.KEEP_DISTANCE, 0.0, 0.1, false, 2, 0));
        metrics.record(summary(ControlLevel.LOCAL, Movement.HOLD, 0.0, 0.0, false, 0, 0));
        var levels = metrics.byLevel();
        assertEquals(List.of(ControlLevel.JEV, ControlLevel.LOCAL), levels.stream().map(OutcomeMetrics.LevelOutcomes::level)
                .toList());
        var jev = levels.getFirst();
        assertEquals(2, jev.windows());
        assertEquals(0.1, jev.dealt(), 1e-9);
        assertEquals(0.1, jev.taken(), 1e-9);
        assertEquals(0.5, jev.reachRate(), 1e-9);
        assertEquals(0.25, jev.hitRate(), 1e-9);
        assertEquals((0.35 - 0.1) / 2, jev.effect(), 1e-9);
        assertEquals(0.0, levels.get(1).hitRate(), "no cast, no hit rate");
        assertEquals(1, metrics.windows(ControlLevel.JEV, Movement.APPROACH));
    }

    @Test
    void tenThousandWindowsKeepTheCellsAndTheRingOfASessionFixed() {
        var metrics = new OutcomeMetrics();
        var ring = new SessionOutcomes();
        for (var index = 0; index < 10_000; index++) {
            var level = ControlLevel.values()[index % 3];
            var movement = Movement.values()[index % Movement.values().length];
            metrics.record(summary(level, movement, 0.01, 0.0, false, 1, 0));
            ring.open(new OutcomeWindow(level, new TacticId("tactic"), movement, index, Optional.empty(), false));
            ring.close(index + 1);
        }
        assertEquals(3, metrics.byLevel().size());
        assertEquals(10_000, metrics.byLevel().stream().mapToLong(OutcomeMetrics.LevelOutcomes::windows).sum());
        assertEquals(SessionOutcomes.RECENT, ring.recent().size());
        assertEquals(9_999, ring.recent().getLast().startTick());
    }
}
