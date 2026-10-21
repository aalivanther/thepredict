package akyrion.thepredict.domain.learn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Errata E28-9 applied: level 2 pools a coarse parent and stores the disagreement delta. The legacy codes keep the
 * statistics of {@link ValueRollup}; only the keys are hashed.
 */
class ValueHierarchyTest {
    @Test
    void levelTwoPoolsTheFineCellsOfOneParentAndStoresDisagreement() {
        var nearHigh = ContextKey.of(0, HealthBand3.HIGH, HealthBand3.MID, false, ZoneBand.OPEN, GroupBand.SOLO, 0);
        var nearLow = ContextKey.of(0, HealthBand3.LOW, HealthBand3.HIGH, false, ZoneBand.HAZARDOUS, GroupBand.SMALL,
                0);
        assertEquals(nearHigh.coarse(), nearLow.coarse());
        var rows = List.of(row("press", nearHigh.text(), 10, 1.0, 7, 6), row("press", nearLow.text(), 30, 0.5, 3, 2),
                row("guard", nearHigh.text(), 20, 0.2, 4, 4));
        var cells = ValueHierarchy.cells(rows);
        ArtifactFormat.ValueCell pooled = null;
        ArtifactFormat.ValueCell fine = null;
        for (var cell : cells) {
            if (cell.level() == 2 && cell.nEff() == 40) {
                pooled = cell;
            }
            if (cell.level() == 3 && cell.nEff() == 10) {
                fine = cell;
            }
        }
        assertNotNull(pooled);
        assertNotNull(fine);
        assertEquals(40, pooled.nEff());
        assertEquals(nearHigh.coarseCode(), pooled.context());
        assertEquals(nearHigh.packed(), fine.context());
        assertEquals(0, pooled.deltaMillis(), "Jev never picked another tactic than the leader, so nothing to gain");
        assertEquals(pooled.deltaMillis(), fine.deltaMillis());
        assertEquals(7, fine.jevSeen());
        assertEquals(6, fine.jevAgree());
        assertEquals(ValueHierarchy.profileCode("guardian"), fine.profile());
        assertEquals(ValueHierarchy.tacticCode("press"), fine.tactic());
    }

    @Test
    void theLegacyCellsCarryTheRollupStatistics() {
        var context = ContextKey.of(1, HealthBand3.MID, HealthBand3.MID, true, ZoneBand.NA, GroupBand.SOLO, 0);
        var rows = List.of(row("press", context.text(), 40, 0.8, 0, 0), row("guard", context.text(), 40, 0.1, 0, 0));
        var rollup = ValueRollup.of(akyrion.thepredict.domain.LearningSettings.DEFAULT);
        rollup.add(rows);
        var artifact = rollup.build(0);
        var legacy = ValueHierarchy.cells(rows);
        assertEquals(artifact.cells().size(), legacy.size());
        for (var cell : artifact.cells()) {
            var tactic = ValueHierarchy.tacticCode(artifact.tactics().get(cell.tactic()));
            var match = legacy.stream().filter(found -> found.tactic() == tactic && found.level() == cell.level())
                    .findFirst().orElseThrow();
            assertEquals(cell.valueMillis(), match.valueMillis());
            assertEquals(cell.sdMillis(), match.sdMillis());
            assertEquals(cell.nEff(), match.nEff());
        }
    }

    private static ValueStatDelta row(String tactic, String context, int windows, double mean, int seen, int agree) {
        var key = new ValueStatKey("guardian", tactic, context, "sim", "r1", 1);
        return new ValueStatDelta(key, windows, mean, 0.0, 0.0, 0.0, 0.0, seen, agree, 1L);
    }
}
