package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.learn.ContextKey;
import akyrion.thepredict.domain.learn.GroupBand;
import akyrion.thepredict.domain.learn.HealthBand3;
import akyrion.thepredict.domain.learn.RewardSpec;
import akyrion.thepredict.domain.learn.TrainingSource;
import akyrion.thepredict.domain.learn.ValueRollup;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;
import akyrion.thepredict.domain.learn.ZoneBand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueTableTest {
    private static final String PROFILE = "thepredict_guardian";

    @Test
    void lookupStopsAtTheFirstPresentLevelAndWeakDoesNotFallThrough() {
        var builder = new ValueTableBuilder();
        builder.add(1, 2, 3, 7L, 0.4, 0.01, 5, 4.0);
        builder.add(0, 2, 0, 0L, 0.9, 0.01, 40, 4.0);
        var table = builder.build();
        var weak = table.lookup(1, 2, 7L, 0L, 20);
        assertEquals(3, weak.level());
        assertTrue(weak.weak(20));
        assertEquals(400, weak.valueMillis());
        var coarse = table.lookup(1, 2, 9L, 0L, 20);
        assertEquals(0, coarse.level());
        assertEquals(900, coarse.valueMillis());
        assertTrue(table.lookup(-1, 2, 0L, 0L, 20).none());
    }

    @Test
    void tacticsThatShareALegacyHashReadTheirOwnCells() {
        var context = key(HealthBand3.HIGH, GroupBand.SOLO);
        var table = table(List.of(row("press_and_strike", context, 0.9), row("retreat_and_defend", context, -0.4)));
        var press = table.lookup(PROFILE, "press_and_strike", context);
        var retreat = table.lookup(PROFILE, "retreat_and_defend", context);
        assertEquals(3, press.level());
        assertEquals(3, retreat.level());
        assertNotEquals(press.valueMillis(), retreat.valueMillis());
        assertTrue(press.valueMillis() > retreat.valueMillis());
        assertTrue(table.lookup("another_profile", "press_and_strike", context).none());
        assertTrue(table.lookup(PROFILE, "unknown_tactic", context).none());
    }

    @Test
    void anUnseenFineContextReachesTheCoarseLevel() {
        var seen = key(HealthBand3.HIGH, GroupBand.SOLO);
        var unseen = key(HealthBand3.LOW, GroupBand.LARGE);
        assertEquals(seen.coarseCode(), unseen.coarseCode());
        var table = table(List.of(row("press", seen, 0.6)));
        assertEquals(3, table.lookup(PROFILE, "press", seen).level());
        assertEquals(2, table.lookup(PROFILE, "press", unseen).level());
        var otherCoarse = ContextKey.of(2, HealthBand3.HIGH, HealthBand3.HIGH, false, ZoneBand.NA, GroupBand.SOLO, 0);
        assertEquals(1, table.lookup(PROFILE, "press", otherCoarse).level());
    }

    private static ValueTable table(List<ValueStatDelta> rows) {
        var build = new ValueRollup(TrainingSource.DEFAULTS, 14, 8, ValueTable.MAX_ENTRIES);
        build.add(rows);
        return ValueTable.of(build.build(1));
    }

    private static ContextKey key(HealthBand3 own, GroupBand group) {
        return ContextKey.of(0, own, HealthBand3.MID, true, ZoneBand.NA, group, 0);
    }

    private static ValueStatDelta row(String tactic, ContextKey context, double mean) {
        var key = new ValueStatKey(PROFILE, tactic, context.text(), "bench", RewardSpec.ID, 2_900);
        return new ValueStatDelta(key, 60, mean, 6.0, 0.0, 0.0, 0.0, 0, 0, 1_791_900_000_000L);
    }
}
