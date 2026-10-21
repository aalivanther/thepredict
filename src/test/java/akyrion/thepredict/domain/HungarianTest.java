package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hungarian assignment against brute force for n at most 7 (M25). */
class HungarianTest {
    @Test
    void threeThousandCasesMatchBruteForce() {
        var hungarian = new Hungarian(8);
        var state = 1L;
        for (var sample = 0; sample < 3_000; sample++) {
            state = state * 1_103_515_245L + 12_345L;
            var n = 1 + (int) ((state >>> 16) & 7);
            if (n > 7) {
                n = 7;
            }
            var rows = new int[n][n];
            for (var row = 0; row < n; row++) {
                for (var column = 0; column < n; column++) {
                    state = state * 1_103_515_245L + 12_345L;
                    rows[row][column] = (int) ((state >>> 16) & 63);
                }
            }
            var assignment = new int[n];
            var total = hungarian.assign(rows, n, n, assignment);
            assertEquals(brute(rows), total, sample);
            assertTrue(total < GroupFairnessRules.INFEASIBLE);
        }
    }

    @Test
    void equalCostsStayDeterministic() {
        var hungarian = new Hungarian(4);
        var rows = new int[][] {{5, 5, 5}, {5, 5, 5}, {5, 5, 5}};
        var first = new int[3];
        var second = new int[3];
        assertEquals(15, hungarian.assign(rows, 3, 3, first));
        assertEquals(15, hungarian.assign(rows, 3, 3, second));
        assertEquals(first[0], second[0]);
        assertEquals(first[1], second[1]);
        assertEquals(first[2], second[2]);
    }

    @Test
    void reserveIsChosenWhenEveryOtherRoleIsInfeasible() {
        var hungarian = new Hungarian(1);
        var distance = new int[1][7];
        var assigned = new GroupRole[1];
        RoleAssignment.assign(new RoleCapabilities[] {RoleCapabilities.NONE}, distance, new GroupRole[] {null}, 0,
                hungarian, assigned);
        assertEquals(GroupRole.RESERVE, assigned[0]);
    }

    @Test
    void uniformCostsStayInsideInt() {
        var n = 16;
        var hungarian = new Hungarian(n);
        var rows = new int[n][n];
        for (var row = 0; row < n; row++) {
            for (var column = 0; column < n; column++) {
                rows[row][column] = 2_000;
            }
        }
        var assignment = new int[n];
        assertEquals(32_000, hungarian.assign(rows, n, n, assignment));
    }

    private static int brute(int[][] rows) {
        var n = rows.length;
        var used = new boolean[n];
        return search(rows, used, 0, 0, Integer.MAX_VALUE);
    }

    private static int search(int[][] rows, boolean[] used, int row, int total, int best) {
        if (row == rows.length) {
            return total;
        }
        var found = best;
        for (var column = 0; column < rows.length; column++) {
            if (used[column]) {
                continue;
            }
            used[column] = true;
            var next = search(rows, used, row + 1, total + rows[row][column], found);
            if (next < found) {
                found = next;
            }
            used[column] = false;
        }
        return found;
    }
}
