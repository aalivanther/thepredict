package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Role assignment always has a feasible answer (M25.4) and keeps the answer of groups that already had one. */
class RoleAssignmentTest {
    private static final RoleCapabilities ARCHER = new RoleCapabilities(0, 0, 600, 0, 0, 0);
    private static final RoleCapabilities BRAWLER = new RoleCapabilities(500, 200, 0, 0, 0, 400);
    private static final RoleCapabilities ALL_ROUND = new RoleCapabilities(500, 400, 300, 200, 100, 400);

    @Test
    void twoArchersTakeRangedAndReserveInsteadOfAnUnfillablePressure() {
        var assigned = assign(ARCHER, ARCHER);
        assertEquals(EnumSet.of(GroupRole.RANGED, GroupRole.RESERVE), EnumSet.of(assigned[0], assigned[1]));
    }

    @Test
    void fourBrawlersWithoutARangedMemberStillGetRoles() {
        var assigned = assign(BRAWLER, BRAWLER, BRAWLER, BRAWLER);
        var seen = EnumSet.noneOf(GroupRole.class);
        var reserves = 0;
        for (var role : assigned) {
            assertTrue(BRAWLER.allows(role), role.name());
            if (role == GroupRole.RESERVE) {
                reserves++;
            } else {
                assertTrue(seen.add(role), "one member per role column: " + role);
            }
        }
        assertEquals(EnumSet.of(GroupRole.PRESSURE, GroupRole.FLANK, GroupRole.GUARD), seen);
        assertEquals(1, reserves);
    }

    @Test
    void aGroupThatCouldAlreadyFillTheFirstRolesKeepsTheSameAnswer() {
        var assigned = assign(ALL_ROUND, ALL_ROUND, ALL_ROUND);
        assertEquals(EnumSet.of(GroupRole.PRESSURE, GroupRole.FLANK, GroupRole.RESERVE),
                EnumSet.of(assigned[0], assigned[1], assigned[2]));
        var single = assign(ALL_ROUND);
        assertArrayEquals(new GroupRole[] {GroupRole.RESERVE}, single);
    }

    @Test
    void theHeldRoleWinsATieThroughTheSwitchMargin() {
        var capabilities = new RoleCapabilities[] {BRAWLER, BRAWLER};
        var assigned = new GroupRole[2];
        RoleAssignment.assign(capabilities, new int[2][GroupRole.values().length],
                new GroupRole[] {GroupRole.RESERVE, GroupRole.PRESSURE}, 150, new Hungarian(2), assigned);
        assertArrayEquals(new GroupRole[] {GroupRole.RESERVE, GroupRole.PRESSURE}, assigned);
    }

    @Test
    void aMemberTakesOnlyTheRolesItsProfileAllowsOrReserve() {
        var best = new RoleCapabilities(900, 800, 300, 0, 0, 400);
        var capabilities = new RoleCapabilities[] {best, ALL_ROUND, ALL_ROUND};
        var free = assign(capabilities);
        assertTrue(free[0] == GroupRole.PRESSURE || free[0] == GroupRole.FLANK, "the strongest member leads: " + free[0]);
        var assigned = new GroupRole[3];
        RoleAssignment.assign(capabilities, new int[] {RoleAssignment.allowed(java.util.Set.of(GroupRole.RANGED)),
                        RoleAssignment.allowed(java.util.Set.of()), RoleAssignment.allowed(java.util.Set.of())},
                new int[3][GroupRole.values().length], new GroupRole[3], 150, new Hungarian(3), assigned);
        assertEquals(GroupRole.RESERVE, assigned[0], "DOC-09: Group.Roles [RANGED] keeps it off pressure and flank");
        assertEquals(EnumSet.of(GroupRole.PRESSURE, GroupRole.FLANK), EnumSet.of(assigned[1], assigned[2]));
        assertEquals((1 << GroupRole.values().length) - 1, RoleAssignment.allowed(java.util.Set.of()));
    }

    private static GroupRole[] assign(RoleCapabilities... capabilities) {
        var assigned = new GroupRole[capabilities.length];
        RoleAssignment.assign(capabilities, new int[capabilities.length][GroupRole.values().length],
                new GroupRole[capabilities.length], 150, new Hungarian(Math.max(1, capabilities.length)), assigned);
        return assigned;
    }
}
