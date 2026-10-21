package akyrion.thepredict.domain;

/**
 * Assigns one role to each member (M25). Cost is {@code 1000 - aptitude + 3 * distance + switch}.
 * A role without capacity costs {@link GroupFairnessRules#INFEASIBLE}. The columns are the non-reserve roles in
 * declaration order, each at most once and at most {@code members - 1}, then {@link GroupRole#RESERVE}. A role joins
 * the columns only while the members can still fill every role column together, so a feasible assignment always
 * exists (M25.4): a group of archers takes {@code RANGED} instead of an unfillable {@code PRESSURE}. A group that can
 * fill the first roles keeps exactly the columns it had. The plan's role slots of M25.9 are not modelled here. A member
 * whose profile lists {@code Group.Roles} takes only those roles or {@link GroupRole#RESERVE} (DOC-09).
 */
public final class RoleAssignment {
    private static final int EVERY_ROLE = (1 << GroupRole.values().length) - 1;

    private RoleAssignment() {
    }

    /**
     * Fills {@code assigned} with one role per member.
     *
     * @param capabilities aptitude of each member
     * @param distanceBlocks distance of each member to the natural point of each non-reserve role
     * @param current role already held, or null
     * @param switchMarginMillis cost of a change
     * @param hungarian buffers
     * @param assigned filled with the chosen role
     * @return the total cost
     */
    public static int assign(RoleCapabilities[] capabilities, int[][] distanceBlocks, GroupRole[] current,
                             int switchMarginMillis, Hungarian hungarian, GroupRole[] assigned) {
        var every = new int[capabilities.length];
        java.util.Arrays.fill(every, EVERY_ROLE);
        return assign(capabilities, every, distanceBlocks, current, switchMarginMillis, hungarian, assigned);
    }

    /**
     * Fills {@code assigned} with one role per member, each among the roles its profile allows.
     *
     * @param capabilities aptitude of each member
     * @param allowed roles each member may take, as {@link #allowed}; reserve is always allowed
     * @param distanceBlocks distance of each member to the natural point of each non-reserve role
     * @param current role already held, or null
     * @param switchMarginMillis cost of a change
     * @param hungarian buffers
     * @param assigned filled with the chosen role
     * @return the total cost
     */
    public static int assign(RoleCapabilities[] capabilities, int[] allowed, int[][] distanceBlocks,
                             GroupRole[] current, int switchMarginMillis, Hungarian hungarian, GroupRole[] assigned) {
        var rows = capabilities.length;
        var matrix = new int[rows][rows];
        var columns = new GroupRole[rows];
        columnsOf(capabilities, allowed, columns);
        for (var row = 0; row < rows; row++) {
            for (var column = 0; column < rows; column++) {
                matrix[row][column] = permits(allowed[row], columns[column])
                        ? cost(capabilities[row], columns[column], distanceBlocks[row], current[row],
                        switchMarginMillis) : GroupFairnessRules.INFEASIBLE;
            }
        }
        var choice = new int[rows];
        var total = hungarian.assign(matrix, rows, rows, choice);
        for (var row = 0; row < rows; row++) {
            assigned[row] = columns[choice[row]];
        }
        return total;
    }

    /**
     * The roles a profile allows, for {@link #assign(RoleCapabilities[], int[], int[][], GroupRole[], int, Hungarian,
     * GroupRole[])}: every role when the list is empty (DOC-09).
     *
     * @param roles {@code Group.Roles} of the profile
     * @return one bit per allowed role
     */
    public static int allowed(java.util.Set<GroupRole> roles) {
        if (roles.isEmpty()) {
            return EVERY_ROLE;
        }
        var mask = 1 << GroupRole.RESERVE.ordinal();
        for (var role : roles) {
            mask |= 1 << role.ordinal();
        }
        return mask;
    }

    private static boolean permits(int allowed, GroupRole role) {
        return role == GroupRole.RESERVE || (allowed & 1 << role.ordinal()) != 0;
    }

    private static void columnsOf(RoleCapabilities[] capabilities, int[] allowed, GroupRole[] columns) {
        var rows = capabilities.length;
        var owner = new int[rows];
        java.util.Arrays.fill(owner, -1);
        var filled = 0;
        for (var role : GroupRole.values()) {
            if (role == GroupRole.RESERVE || filled >= rows - 1) {
                continue;
            }
            columns[filled] = role;
            if (fillable(filled, capabilities, allowed, columns, owner, new boolean[rows])) {
                filled++;
            }
        }
        while (filled < rows) {
            columns[filled++] = GroupRole.RESERVE;
        }
    }

    private static boolean fillable(int column, RoleCapabilities[] capabilities, int[] allowed, GroupRole[] columns,
                                    int[] owner, boolean[] seen) {
        for (var member = 0; member < capabilities.length; member++) {
            if (seen[member] || !capabilities[member].allows(columns[column])
                    || !permits(allowed[member], columns[column])) {
                continue;
            }
            seen[member] = true;
            if (owner[member] < 0 || fillable(owner[member], capabilities, allowed, columns, owner, seen)) {
                owner[member] = column;
                return true;
            }
        }
        return false;
    }

    private static int cost(RoleCapabilities capabilities, GroupRole role, int[] distance, GroupRole current,
                            int switchMarginMillis) {
        if (role == GroupRole.RESERVE) {
            return GroupFairnessRules.RESERVE_COST;
        }
        if (!capabilities.allows(role)) {
            return GroupFairnessRules.INFEASIBLE;
        }
        var blocks = distance == null ? 0 : distance[role.ordinal()];
        var change = current != null && current != role ? switchMarginMillis : 0;
        return 1000 - capabilities.aptitude(role) + 3 * blocks + change;
    }
}
