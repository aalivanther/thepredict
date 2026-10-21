package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.adapter.server.zone.ZoneFlank;
import akyrion.thepredict.domain.ZoneColumn;
import akyrion.thepredict.domain.ZoneGrid;
import akyrion.thepredict.domain.ZoneQueryRunner;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A flank keeps the safe side and drops a harmful cell or a thin edge (M24.5). */
class FlankPathsZoneTest {
    @Test
    void anUnsafeSideIsDroppedAndAHarmfulOrThinCellIsRefused() {
        assertEquals(-1, FlankPaths.prefer(2, false, 2, false));
        assertEquals(1, FlankPaths.prefer(1, false, 4, true));
        assertEquals(0, FlankPaths.prefer(2, true, 5, true));
        var queries = new ZoneQueryRunner();
        var safe = ground(ZoneColumn.pack(ZoneColumn.FLOOR, 64, 15, 0, 0L));
        assertTrue(ZoneFlank.allows(queries, safe, new Location(null, 2.2, 64, 2.2)));
        var harmful = ground(ZoneColumn.pack(ZoneColumn.FLOOR, 64, 15, 0, 1L << ZoneColumn.LAVA));
        assertFalse(ZoneFlank.allows(queries, harmful, new Location(null, 2.2, 64, 2.2)));
        var thin = ground(ZoneColumn.pack(ZoneColumn.FLOOR, 64, 15, 0, 0L));
        thin.edgeLayer()[2 * thin.width() + 2] = 2;
        assertFalse(ZoneFlank.allows(queries, thin, new Location(null, 2.2, 64, 2.2)));
        var none = ground(ZoneColumn.pack(ZoneColumn.VOID, 0, 0, 0, 0L));
        assertFalse(ZoneFlank.allows(queries, none, new Location(null, 2.2, 64, 2.2)));
        assertFalse(ZoneFlank.allows(queries, safe, new Location(null, -1, 64, 2.2)));
    }

    private static ZoneGrid ground(long column) {
        var grid = new ZoneGrid(7, 0, 0, 64, 3);
        for (var index = 0; index < 49; index++) {
            grid.set(index, column);
        }
        grid.ready(1);
        return grid;
    }
}
