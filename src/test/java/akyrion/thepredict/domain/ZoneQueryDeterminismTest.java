package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Q6 and Q7 answers depend only on their inputs, never on the queries asked before them (C16, M24.4). */
class ZoneQueryDeterminismTest {
    private static final int WIDTH = 9;
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Test
    void everyPushGivesTheSameAnswerInForwardAndReverseOrder() {
        var grid = ledge();
        var queries = WIDTH * WIDTH * STEPS.length;
        var forward = new PushDanger[queries];
        var reverse = new PushDanger[queries];
        var runner = new ZoneQueryRunner();
        for (var query = 0; query < queries; query++) {
            runner.nextTick();
            forward[query] = push(runner, grid, query);
        }
        for (var query = queries - 1; query >= 0; query--) {
            runner.nextTick();
            reverse[query] = push(runner, grid, query);
        }
        assertArrayEquals(forward, reverse);
        var seen = EnumSet.noneOf(PushDanger.class);
        for (var danger : forward) {
            seen.add(danger);
        }
        assertTrue(seen.containsAll(EnumSet.of(PushDanger.NONE, PushDanger.LETHAL, PushDanger.UNKNOWN)), seen.toString());
        var fresh = new ZoneQueryRunner();
        for (var query = 0; query < queries; query++) {
            fresh.nextTick();
            assertEquals(forward[query], push(fresh, grid, query), "query " + query);
        }
    }

    @Test
    void twoQueriesLeaveTheNextAnswerUnknownUntilTheNextTick() {
        var grid = new ZoneGrid(17, 0, 0, 64, 3);
        for (var i = 0; i < 17 * 17; i++) {
            grid.set(i, ZoneColumn.floor(64, 4));
        }
        grid.ready(1);
        var runner = new ZoneQueryRunner();
        runner.limit(2);
        assertEquals(Feasibility.INFEASIBLE, runner.feasible(grid, TellAnswer.BREAK_SIGHT, 8, 8, 8, 9, 3));
        assertEquals(Feasibility.FEASIBLE, runner.feasible(grid, TellAnswer.BLOCK, 8, 8, 8, 9, 3));
        assertEquals(Feasibility.UNKNOWN, runner.feasible(grid, TellAnswer.BLOCK, 8, 8, 8, 9, 3));
        runner.nextTick();
        assertEquals(Feasibility.FEASIBLE, runner.feasible(grid, TellAnswer.BLOCK, 8, 8, 8, 9, 3));
        runner.limit(2);
        runner.nextTick();
        runner.push(grid, 1, 1, 1, 0, 2, 20);
        runner.push(grid, 1, 1, 1, 0, 2, 20);
        assertEquals(PushDanger.UNKNOWN, runner.push(grid, 1, 1, 1, 0, 2, 20));
        runner.nextTick();
        assertEquals(PushDanger.NONE, runner.push(grid, 1, 1, 1, 0, 2, 20));
    }

    private static PushDanger push(ZoneQueryRunner runner, ZoneGrid grid, int query) {
        var cell = query / STEPS.length;
        var step = STEPS[query % STEPS.length];
        return runner.push(grid, cell % WIDTH, cell / WIDTH, step[0], step[1], 2, 20);
    }

    private static ZoneGrid ledge() {
        var grid = new ZoneGrid(WIDTH, 0, 0, 64, 3);
        for (var z = 0; z < WIDTH; z++) {
            for (var x = 0; x < WIDTH; x++) {
                var column = x >= 6 ? ZoneColumn.pack(ZoneColumn.VOID, 0, 0, 0, 0L) : ZoneColumn.floor(64, 4);
                grid.set(z * WIDTH + x, column);
            }
        }
        grid.ready(1);
        return grid;
    }
}
