package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.Steering;
import akyrion.thepredict.domain.ZoneColumn;
import akyrion.thepredict.domain.ZoneGrid;
import akyrion.thepredict.domain.ZoneQueryRunner;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SP-07: an unknown column, an unloaded or unscanned chunk, is unsafe for a destination (M24 P3). */
class ZoneDestinationTest {
    private static final int WIDTH = 15;

    @Test
    void aPointOnAnUnknownColumnIsRefusedAndADeferredAnswerKeepsIt() {
        var map = mapWithUnknownBlock();
        var queries = new ZoneQueryRunner();
        queries.limit(32);
        queries.nextTick();
        var unknown = new Steering(7.5, 7.5);
        assertTrue(ZoneDestination.filter(queries, map, MicroBehaviour.SIDESTEP, unknown, 0, 0).isEmpty());
        var known = ZoneDestination.filter(queries, map, MicroBehaviour.SIDESTEP, new Steering(2.5, 2.5), 1, 1);
        assertEquals(2.5, known.orElseThrow().x());
        var spent = new ZoneQueryRunner();
        spent.limit(1);
        spent.nextTick();
        spent.destination(map, 2, 2);
        assertEquals(unknown, ZoneDestination.filter(spent, map, MicroBehaviour.SIDESTEP, unknown, 0, 0)
                .orElseThrow());
    }

    @Test
    void aFlankPointOnAnUnknownColumnIsRefused() {
        var map = mapWithUnknownBlock();
        var queries = new ZoneQueryRunner();
        queries.limit(32);
        queries.nextTick();
        assertFalse(ZoneFlank.allows(queries, map, new Location(null, 7.5, 64.0, 7.5)));
        assertTrue(ZoneFlank.allows(queries, map, new Location(null, 2.5, 64.0, 2.5)));
    }

    /** Floor everywhere except a five by five square of unknown columns around the centre. */
    private static ZoneMap mapWithUnknownBlock() {
        var grid = new ZoneGrid(WIDTH, 0, 0, 64, 3);
        for (var z = 0; z < WIDTH; z++) {
            for (var x = 0; x < WIDTH; x++) {
                var unknown = x >= 5 && x <= 9 && z >= 5 && z <= 9;
                grid.set(z * WIDTH + x, unknown ? 0L : ZoneColumn.floor(64, 10));
            }
        }
        new ZoneColumnScan(new ZoneMaterialTable()).layers(grid);
        grid.ready(1);
        return new ZoneMap(grid, false);
    }
}
