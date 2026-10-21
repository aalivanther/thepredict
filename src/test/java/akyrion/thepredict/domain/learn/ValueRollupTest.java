package akyrion.thepredict.domain.learn;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueRollupTest {
    private static final String PROFILE = "thepredict_guardian";

    @Test
    void twoPagesInAnyOrderGiveTheSameBytesAsOne() {
        var rows = new ArrayList<ValueStatDelta>();
        for (var index = 0; index < 12; index++) {
            rows.add(row(index % 2 == 0 ? "press" : "guard", fine(index % 3, index % 2 == 0), "bench", 10 + index % 3,
                    20 + index, 0.1 * index, 5.0, 4, 3));
        }
        var whole = rollup();
        whole.add(rows);
        var split = rollup();
        var reversed = new ArrayList<>(rows);
        java.util.Collections.reverse(reversed);
        split.add(reversed.subList(0, 5));
        split.add(reversed.subList(5, reversed.size()));
        assertArrayEquals(ValueArtifactFormat.write(whole.build(3)), ValueArtifactFormat.write(split.build(3)));
    }

    @Test
    void kappaIsEstimatedFromTheSpreadOfTheChildren() {
        assertEquals(4_000, kappaOfLevelThree(0.9));
        assertEquals(100_000, kappaOfLevelThree(0.0));
        var middle = kappaOfLevelThree(0.1);
        assertTrue(middle > 4_000 && middle < 100_000, "kappa " + middle);
        var within = 50 * 0.25 * 4 / (4 * 49.0);
        var tau = 0.01 - within / 50.0;
        assertEquals(Math.round(ShrinkageMath.kappa(within, tau) * 1000.0), middle);
    }

    @Test
    void theStoredDeviationIsThePosteriorNotTheRawSpread() {
        var build = rollup();
        build.add(List.of(row("press", fine(0, true), "bench", 10, 100, 0.4, 25.0, 0, 0)));
        var artifact = build.build(1);
        var cell = level(artifact, 3).getFirst();
        var within = 25.0 / 99.0;
        var kappa = artifact.kappaMillis().get(3) / 1000.0;
        assertEquals(ShrinkageMath.millis(ShrinkageMath.deviation(within, 100, kappa)), cell.sdMillis());
        assertNotEquals(500, cell.sdMillis());
        for (var level = 0; level <= 2; level++) {
            assertTrue(level(artifact, level).getFirst().sdMillis() > 0, "level " + level);
        }
    }

    @Test
    void oneCellPerKeyAcrossWeeksAndSourcesWithWeekWeights() {
        var build = rollup();
        build.add(List.of(row("press", fine(0, true), "sim", 12, 10, 1.0, 0.0, 0, 0),
                row("press", fine(0, true), "bench", 12, 10, 1.0, 0.0, 0, 0),
                row("press", fine(0, true), "bench", 10, 10, 0.0, 0.0, 0, 0)));
        var fine = level(build.build(1), 3);
        assertEquals(1, fine.size());
        var weighted = 20 + 10 * ShrinkageMath.weekWeight(2, 14);
        assertEquals(Math.round(weighted), fine.getFirst().nEff());
        assertEquals(0.5, ShrinkageMath.weekWeight(2, 14), 1.0e-12);
    }

    @Test
    void pacedTruncatedShutdownWeekZeroAndOldKeysAreLeftOut() {
        var build = rollup();
        build.add(List.of(row("press", fine(0, true), "player_paced", 12, 10, 1.0, 0.0, 0, 0),
                row("press", fine(0, true), LearnCause.CREDIT_TRUNCATED.name(), 12, 10, 0.0, 0.0, 0, 0),
                row("press", fine(0, true), "SHUTDOWN", 12, 10, 0.0, 0.0, 0, 0),
                row("press", fine(0, true), "sim", 0, 10, 0.0, 0.0, 0, 0),
                row("press", "v2c:MELEE:MOB:NONE", "sim", 12, 10, 0.0, 0.0, 0, 0),
                row("press", fine(1, true), "bench", 12, 4, 0.7, 0.0, 0, 0)));
        assertEquals(5, build.skipped());
        var fine = level(build.build(1), 3);
        assertEquals(1, fine.size());
        assertEquals(4, fine.getFirst().nEff());
    }

    @Test
    void weeksBeyondTheRetentionAreDropped() {
        var build = new ValueRollup(TrainingSource.DEFAULTS, 14, 2, 20_000);
        build.add(List.of(row("press", fine(0, true), "bench", 20, 10, 1.0, 0.0, 0, 0),
                row("press", fine(1, true), "bench", 18, 10, 1.0, 0.0, 0, 0)));
        assertEquals(1, level(build.build(1), 3).size());
    }

    @Test
    void theDisagreementDeltaIsPositiveWhenJevsOtherPickDidBetterInTheSameContext() {
        var better = rollup();
        better.add(List.of(row("press", fine(0, true), "bench", 10, 50, 0.2, 2.0, 0, 0),
                row("press", fine(1, true), "bench", 10, 200, 0.9, 8.0, 0, 0),
                row("guard", fine(0, true), "bench", 10, 50, 0.6, 2.0, 50, 0)));
        var artifact = better.build(1);
        var coarse = level(artifact, 2);
        assertEquals(2, coarse.size());
        assertTrue(coarse.getFirst().deltaMillis() > 0, "delta " + coarse.getFirst().deltaMillis());
        for (var cell : level(artifact, 3)) {
            assertEquals(coarse.getFirst().deltaMillis(), cell.deltaMillis());
        }
        var agreed = rollup();
        agreed.add(List.of(row("press", fine(0, true), "bench", 10, 50, 0.2, 2.0, 0, 0),
                row("guard", fine(0, true), "bench", 10, 50, 0.6, 2.0, 50, 50)));
        assertEquals(0, level(agreed.build(1), 2).getFirst().deltaMillis());
    }

    @Test
    void tacticsWhoseLegacyHashesCollideKeepSeparateCells() {
        assertEquals(ValueHierarchy.tacticCode("press_and_strike"), ValueHierarchy.tacticCode("retreat_and_defend"));
        var context = fine(0, true);
        var build = rollup();
        build.add(List.of(row("press_and_strike", context, "bench", 10, 60, 0.9, 6.0, 0, 0),
                row("retreat_and_defend", context, "bench", 10, 60, -0.4, 6.0, 0, 0)));
        var artifact = build.build(1);
        assertEquals(List.of("press_and_strike", "retreat_and_defend"), artifact.tactics());
        var fine = level(artifact, 3);
        assertEquals(2, fine.size());
        assertNotEquals(fine.get(0).valueMillis(), fine.get(1).valueMillis());
        assertTrue(ValueHierarchy.legacy(artifact).isEmpty());
    }

    @Test
    void theArtifactRoundTripsAndRefusesBrokenFiles() {
        var build = rollup();
        build.add(List.of(row("press", fine(0, true), "bench", 10, 30, 0.5, 3.0, 9, 8)));
        var artifact = build.build(7);
        var bytes = ValueArtifactFormat.write(artifact);
        assertEquals(artifact, ValueArtifactFormat.read(bytes));
        assertEquals(ArtifactFormat.sha256(bytes), ArtifactFormat.sha256(ValueArtifactFormat.write(artifact)));
        var truncated = Arrays.copyOf(bytes, bytes.length - 3);
        assertRefused(truncated);
        var format = bytes.clone();
        format[7] = 3;
        assertRefused(format);
        var magic = bytes.clone();
        magic[0] = 'X';
        assertRefused(magic);
        var count = bytes.clone();
        count[16] = 0x7f;
        assertRefused(count);
        assertRefused(Arrays.copyOf(ArtifactFormat.valueArtifact(1, 1, RewardSpec.ID,
                new ArtifactFormat.ValueCell[] {new ArtifactFormat.ValueCell(1, 2, 3, 4L, 5, 6, 7)}), 40));
    }

    private static void assertRefused(byte[] bytes) {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ValueArtifactFormat.read(bytes));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ArtifactFormat.readValue(bytes));
    }

    private static int kappaOfLevelThree(double spread) {
        var build = rollup();
        var rows = new ArrayList<ValueStatDelta>();
        for (var index = 0; index < 4; index++) {
            var mean = 0.1 + (index % 2 == 0 ? spread : -spread);
            rows.add(row("press", fine(index, true), "bench", 10, 50, mean, 50 * 0.25, 0, 0));
        }
        build.add(rows);
        return build.build(1).kappaMillis().get(3);
    }

    private static List<ArtifactFormat.ValueCell> level(ValueArtifact artifact, int level) {
        var cells = new ArrayList<ArtifactFormat.ValueCell>();
        for (var cell : artifact.cells()) {
            if (cell.level() == level) {
                cells.add(cell);
            }
        }
        return cells;
    }

    private static ValueRollup rollup() {
        return new ValueRollup(TrainingSource.DEFAULTS, 14, 8, 20_000);
    }

    private static String fine(int variant, boolean player) {
        var own = HealthBand3.values()[variant % 3];
        var group = GroupBand.values()[variant / 3 % 3];
        return ContextKey.of(0, own, HealthBand3.HIGH, player, ZoneBand.OPEN, group, 0).text();
    }

    private static ValueStatDelta row(String tactic, String context, String source, int week, int windows,
                                      double mean, double m2, int seen, int agree) {
        var key = new ValueStatKey(PROFILE, tactic, context, source, RewardSpec.ID, week);
        return new ValueStatDelta(key, windows, mean, m2, 0.0, 0.0, 0.0, seen, agree, 1_791_900_000_000L);
    }
}
