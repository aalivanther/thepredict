package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Destination, feasibility and retreat queries of M24.4 on small painted grids. */
class ZoneQueryRunnerTest {
    private static final long UNKNOWN = 0L;
    private static final long VOID = ZoneColumn.pack(ZoneColumn.VOID, 0, 0, 0, 0L);
    private static final long PIT = ZoneColumn.pack(ZoneColumn.PIT, 0, 0, 5, 0L);

    @Test
    void anUnknownNeighbourDoesNotSendTheMobToAnUnsafeRequestedCell() {
        var grid = flat(5, 64);
        grid.hazardLayer()[index(grid, 2, 2)] = 3;
        grid.set(index(grid, 2, 1), UNKNOWN);
        var pick = new ZoneQueryRunner().destination(grid, 2, 2);
        assertEquals(ZonePick.FOUND, ZonePick.state(pick));
        assertEquals(index(grid, 1, 2), ZonePick.cell(pick));
    }

    @Test
    void aKnownUnsafeRequestWithNoSafeCellIsRejectedAndOnlyAnUnknownRequestStaysUnknown() {
        var grid = flat(5, 64);
        java.util.Arrays.fill(grid.hazardLayer(), (short) 3);
        grid.set(index(grid, 2, 1), UNKNOWN);
        var unsafe = new ZoneQueryRunner().destination(grid, 2, 2);
        assertEquals(ZonePick.NONE, ZonePick.state(unsafe));
        grid.set(index(grid, 2, 2), UNKNOWN);
        assertEquals(ZonePick.UNKNOWN, ZonePick.state(new ZoneQueryRunner().destination(grid, 2, 2)));
        var safeBeside = flat(5, 64);
        safeBeside.set(index(safeBeside, 2, 2), UNKNOWN);
        var beside = new ZoneQueryRunner().destination(safeBeside, 2, 2);
        assertEquals(ZonePick.FOUND, ZonePick.state(beside));
        assertEquals(index(safeBeside, 2, 1), ZonePick.cell(beside));
    }

    @Test
    void aSafeRequestedCellIsChosenFirstAndANearSafeCellBeatsAFarOne() {
        var grid = flat(7, 64);
        assertEquals(index(grid, 3, 3), ZonePick.cell(new ZoneQueryRunner().destination(grid, 3, 3)));
        for (var dz = -1; dz <= 1; dz++) {
            for (var dx = -1; dx <= 1; dx++) {
                grid.hazardLayer()[index(grid, 3 + dx, 3 + dz)] = 3;
            }
        }
        var far = new ZoneQueryRunner().destination(grid, 3, 3);
        assertEquals(ZonePick.FOUND, ZonePick.state(far));
        assertEquals(index(grid, 3, 1), ZonePick.cell(far));
        grid.hazardLayer()[index(grid, 4, 3)] = ZoneLayers.CAP;
        var near = new ZoneQueryRunner().destination(grid, 3, 3);
        assertEquals(index(grid, 4, 3), ZonePick.cell(near));
        assertTrue(ZonePick.score(near) > ZonePick.score(far));
    }

    @Test
    void breakSightIsOneQueryAndTheCapNeverMakesItInfeasible() {
        var grid = flat(17, 64);
        grid.set(index(grid, 8, 9), ZoneColumn.floor(66, 4));
        var runner = new ZoneQueryRunner();
        runner.limit(1);
        assertEquals(Feasibility.FEASIBLE, runner.feasible(grid, TellAnswer.BREAK_SIGHT, 8, 2, 8, 8, 3));
        assertEquals(Feasibility.UNKNOWN, runner.feasible(grid, TellAnswer.BREAK_SIGHT, 8, 2, 8, 8, 3));
        runner.nextTick();
        assertEquals(Feasibility.INFEASIBLE, runner.feasible(flat(17, 64), TellAnswer.BREAK_SIGHT, 8, 2, 8, 8, 3));
    }

    @Test
    void aRetreatDoesNotCrossAPitEvenWhenTheFloorIsAtHeightZero() {
        var grid = flat(17, 0);
        for (var z = 0; z < 17; z++) {
            grid.set(index(grid, 10, z), PIT);
        }
        var pick = new ZoneQueryRunner().retreat(grid, 8, 8, 1, 0, 4);
        assertEquals(ZonePick.FOUND, ZonePick.state(pick));
        assertTrue(ZonePick.cell(pick) % 17 < 10, "crossed the pit to column " + ZonePick.cell(pick) % 17);
        var open = new ZoneQueryRunner().retreat(flat(17, 0), 8, 8, 1, 0, 4);
        assertEquals(index(grid, 12, 8), ZonePick.cell(open));
    }

    @Test
    void aBackOffAlongEastOrWestSpreadsItsCandidatesSideways() {
        var grid = flat(17, 64);
        for (var x = 9; x < 17; x++) {
            grid.set(index(grid, x, 8), VOID);
        }
        var runner = new ZoneQueryRunner();
        assertEquals(Feasibility.FEASIBLE, runner.feasible(grid, TellAnswer.BACK_OFF, 6, 8, 8, 8, 3));
        assertEquals(Feasibility.FEASIBLE, runner.feasible(grid, TellAnswer.BACK_OFF, 10, 8, 8, 8, 3));
        var sealed = flat(17, 64);
        for (var z = 0; z < 17; z++) {
            for (var x = 10; x < 17; x++) {
                sealed.set(index(sealed, x, z), VOID);
            }
        }
        assertEquals(Feasibility.INFEASIBLE, runner.feasible(sealed, TellAnswer.BACK_OFF, 6, 8, 8, 8, 3));
    }

    private static ZoneGrid flat(int width, int floorY) {
        var grid = new ZoneGrid(width, 0, 0, floorY, 3);
        for (var index = 0; index < width * width; index++) {
            grid.set(index, ZoneColumn.floor(floorY, 4));
        }
        grid.ready(1);
        return grid;
    }

    private static int index(ZoneGrid grid, int x, int z) {
        return z * grid.width() + x;
    }
}
