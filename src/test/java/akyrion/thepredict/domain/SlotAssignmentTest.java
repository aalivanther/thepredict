package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Surround slots are assigned by each member's own distance to the slot (M25.5). */
class SlotAssignmentTest {
    private static final int EAST = 0;
    private static final int WEST = 7;

    @Test
    void twoMembersOnOppositeSidesTakeTheSlotOnTheirOwnSide() {
        var roles = new GroupRole[] {GroupRole.PRESSURE, GroupRole.PRESSURE};
        var assigned = new int[2];
        SlotAssignment.assign(roles, new int[] {-1, -1}, eastAndWest(), -1, 0, new Hungarian(24), assigned,
                new boolean[0], 0, new double[] {-3.0, 3.0}, new double[] {0.0, 0.0});
        assertArrayEquals(new int[] {WEST, EAST}, assigned);
        SlotAssignment.assign(roles, new int[] {-1, -1}, eastAndWest(), -1, 0, new Hungarian(24), assigned,
                new boolean[0], 0, new double[] {3.0, -3.0}, new double[] {0.0, 0.0});
        assertArrayEquals(new int[] {EAST, WEST}, assigned);
    }

    @Test
    void theOverloadsWithoutPositionsMeasureFromThePlayer() {
        var roles = new GroupRole[] {GroupRole.PRESSURE, GroupRole.PRESSURE};
        var plain = new int[2];
        var zeros = new int[2];
        SlotAssignment.assign(roles, new int[] {-1, -1}, eastAndWest(), -1, 0, new Hungarian(24), plain);
        SlotAssignment.assign(roles, new int[] {-1, -1}, eastAndWest(), -1, 0, new Hungarian(24), zeros,
                new boolean[0], 0, new double[2], new double[2]);
        assertArrayEquals(zeros, plain);
    }

    @Test
    void missingPositionsOrPermissionsAreRejected() {
        var roles = new GroupRole[] {GroupRole.PRESSURE, GroupRole.PRESSURE};
        assertThrows(IllegalArgumentException.class, () -> SlotAssignment.assign(roles, new int[] {-1, -1},
                eastAndWest(), -1, 0, new Hungarian(24), new int[2], new boolean[0], 0, new double[1],
                new double[2]));
        assertThrows(NullPointerException.class, () -> SlotAssignment.assign(roles, new int[] {-1, -1},
                eastAndWest(), -1, 0, new Hungarian(24), new int[2], null, 0));
    }

    private static boolean[] eastAndWest() {
        var valid = new boolean[48];
        valid[EAST] = true;
        valid[WEST] = true;
        return valid;
    }
}
