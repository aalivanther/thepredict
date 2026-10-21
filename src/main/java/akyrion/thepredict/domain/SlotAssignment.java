package akyrion.thepredict.domain;

/**
 * One slot per member (M25.5). Cost is the distance in quarter-blocks from the member to the slot, minus 8 on the
 * slot already held, plus 400 when the slot is within a block and a half of one fixed outside this assignment.
 * Missing columns cost {@link GroupFairnessRules#NO_SLOT_COST}.
 */
public final class SlotAssignment {
    private static final int QUARTERS = 4;
    private static final int KEEP = 8;
    private static final int CROWD = 400;

    private SlotAssignment() {
    }

    /**
     * Assigns slots. {@code valid} is indexed {@code ring * 16 + angle}. A false entry is not a column.
     * The guard, when {@code guardRow} is in range, is pulled to {@code guardAngle} and that slot is the fixed one.
     * Distances are measured from the player, as if every member stood on it.
     *
     * @param roles role of each member
     * @param current packed slot already held, or -1
     * @param valid flags of length at least 48
     * @param guardRow member who guards, or -1
     * @param guardAngle degrees of the guard line, already outside the exit sector
     * @param hungarian buffers of at least 24
     * @param assigned filled with {@code (ring << 8) | angle}, or -1
     * @return the total cost
     */
    public static int assign(GroupRole[] roles, int[] current, boolean[] valid, int guardRow, int guardAngle,
                             Hungarian hungarian, int[] assigned) {
        return assign(roles, current, valid, guardRow, guardAngle, hungarian, assigned, new boolean[0], 0);
    }

    /**
     * Assigns slots. A row whose {@code mayEnterSector} entry is false cannot take a column inside the exit sector; a
     * row past the end of that array may. Distances are measured from the player, as if every member stood on it.
     *
     * @param roles role of each member
     * @param current packed slot already held, or -1
     * @param valid flags of length at least 48
     * @param guardRow member who guards, or -1
     * @param guardAngle degrees of the guard line
     * @param hungarian buffers of at least 24
     * @param assigned filled with {@code (ring << 8) | angle}, or -1
     * @param mayEnterSector whether each row may stand in the exit sector
     * @param sectorDegrees full width of the exit sector, 0 when absent
     * @return the total cost
     */
    public static int assign(GroupRole[] roles, int[] current, boolean[] valid, int guardRow, int guardAngle,
                             Hungarian hungarian, int[] assigned, boolean[] mayEnterSector, int sectorDegrees) {
        return assign(roles, current, valid, guardRow, guardAngle, hungarian, assigned, mayEnterSector, sectorDegrees,
                new double[roles.length], new double[roles.length]);
    }

    /**
     * Assigns slots by where each member stands (M25.5): a member's cost to a slot is its own distance to that slot.
     * Offsets use the frame of {@link SlotGeometry#east} and {@link SlotGeometry#south}.
     *
     * @param roles role of each member
     * @param current packed slot already held, or -1
     * @param valid flags of length at least 48
     * @param guardRow member who guards, or -1
     * @param guardAngle degrees of the guard line
     * @param hungarian buffers of at least 24
     * @param assigned filled with {@code (ring << 8) | angle}, or -1
     * @param mayEnterSector whether each row may stand in the exit sector
     * @param sectorDegrees full width of the exit sector, 0 when absent
     * @param eastBlocks blocks east of the player of each member
     * @param southBlocks blocks south of the player of each member
     * @return the total cost
     * @throws IllegalArgumentException when a position array is shorter than the members
     */
    public static int assign(GroupRole[] roles, int[] current, boolean[] valid, int guardRow, int guardAngle,
                             Hungarian hungarian, int[] assigned, boolean[] mayEnterSector, int sectorDegrees,
                             double[] eastBlocks, double[] southBlocks) {
        java.util.Objects.requireNonNull(mayEnterSector, "mayEnterSector");
        var rows = roles.length;
        if (eastBlocks.length < rows || southBlocks.length < rows) {
            throw new IllegalArgumentException("one position per member");
        }
        var columns = new int[24];
        var columnCount = columns(roles, valid, guardAngle, columns);
        var width = Math.min(24, Math.max(rows, columnCount));
        var fixed = guardRow >= 0 && guardRow < rows ? pack(ring(roles[guardRow]), nearest(ring(roles[guardRow]),
                guardAngle, valid)) : -1;
        var matrix = new int[rows][width];
        for (var row = 0; row < rows; row++) {
            var open = row >= mayEnterSector.length || mayEnterSector[row];
            for (var column = 0; column < width; column++) {
                var packed = column < columnCount ? columns[column] : -1;
                matrix[row][column] = !open && inSector(packed, sectorDegrees) ? GroupFairnessRules.NO_SLOT_COST
                        : cost(roles[row], current[row], packed, row == guardRow, fixed, eastBlocks[row],
                        southBlocks[row]);
            }
        }
        var choice = new int[rows];
        var total = hungarian.assign(matrix, rows, width, choice);
        for (var row = 0; row < rows; row++) {
            assigned[row] = choice[row] < columnCount ? columns[choice[row]] : -1;
        }
        return total;
    }

    /**
     * Whether one column may hold a body. A zone that is not ready accepts every column.
     * A hazard or an edge closer than a block and a half, in thirds rounded up, rejects it.
     *
     * @param view the zone, or null
     * @param columnX world column
     * @param columnZ world column
     * @param bodyBlocks body height
     * @return whether the slot is valid
     */
    public static boolean columnAllowed(ZoneView view, int columnX, int columnZ, int bodyBlocks) {
        if (view == null || view.epoch() == 0) {
            return true;
        }
        var x = columnX - view.originX();
        var z = columnZ - view.originZ();
        if (x < 0 || z < 0 || x >= view.width() || z >= view.width()) {
            return false;
        }
        var index = z * view.width() + x;
        if (!ZoneColumn.walkable(view.column(index), Math.max(1, bodyBlocks))) {
            return false;
        }
        return view.hazardThirds(index) >= 5 && view.edgeThirds(index) >= 5;
    }

    /**
     * Degrees of the guard line. A line inside the exit sector moves to the sector edge.
     *
     * @param lineDegrees authored line
     * @param sectorDegrees exit sector
     * @param preferLeft which edge to use when the line sits inside the sector
     * @return degrees in {@code [0, 360)}
     */
    public static int guardAngle(int lineDegrees, int sectorDegrees, boolean preferLeft) {
        if (sectorDegrees <= 0 || !SurroundSlots.inExitSector(lineDegrees, sectorDegrees)) {
            return Math.floorMod(lineDegrees, 360);
        }
        return SurroundSlots.sectorEdge(sectorDegrees, preferLeft);
    }

    private static int cost(GroupRole role, int current, int packed, boolean guard, int fixed, double east,
                            double south) {
        if (packed < 0) {
            return GroupFairnessRules.NO_SLOT_COST;
        }
        if (ring(role) != (packed >>> 8) && !guard) {
            return GroupFairnessRules.NO_SLOT_COST;
        }
        var distance = (int) Math.round(SlotGeometry.blocksFrom(packed, east, south) * QUARTERS);
        var cost = distance - (packed == current ? KEEP : 0);
        if (!guard && fixed >= 0 && packed != fixed && SlotGeometry.closerThanContact(packed, fixed)) {
            cost += CROWD;
        }
        if (guard && packed == fixed) {
            return 0;
        }
        return Math.max(0, cost);
    }

    private static int columns(GroupRole[] roles, boolean[] valid, int guardAngle, int[] columns) {
        var count = 0;
        for (var role : roles) {
            var ring = ring(role);
            var angles = SlotGeometry.count(ring);
            for (var angle = 0; angle < angles && count < columns.length; angle++) {
                var packed = pack(ring, angle);
                if (!valid[ring * 16 + angle] || contains(columns, count, packed)) {
                    continue;
                }
                columns[count++] = packed;
            }
        }
        if (count == 0) {
            columns[0] = pack(1, nearest(1, guardAngle, valid));
            return 1;
        }
        return count;
    }

    private static boolean contains(int[] columns, int count, int packed) {
        for (var index = 0; index < count; index++) {
            if (columns[index] == packed) {
                return true;
            }
        }
        return false;
    }

    private static int nearest(int ring, int degrees, boolean[] valid) {
        var angles = SlotGeometry.count(ring);
        var best = 0;
        var bestDelta = 360;
        for (var angle = 0; angle < angles; angle++) {
            if (!valid[ring * 16 + angle]) {
                continue;
            }
            var delta = Math.abs(Math.floorMod(SurroundSlots.angleDegrees(angle, angles) - degrees + 180, 360) - 180);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = angle;
            }
        }
        return best;
    }

    /**
     * Ring of a role. Pressure, flank and control use the inner ring. Ranged uses the outer ranged ring.
     *
     * @param role the role
     * @return 0 inner, 1 outer, or 2 ranged
     */
    public static int ring(GroupRole role) {
        if (role == GroupRole.RANGED) {
            return 2;
        }
        if (role == GroupRole.PRESSURE || role == GroupRole.FLANK || role == GroupRole.CONTROL) {
            return 0;
        }
        return 1;
    }

    private static int pack(int ring, int angle) {
        return (ring << 8) | angle;
    }

    private static boolean inSector(int packed, int degrees) {
        if (packed < 0 || degrees <= 0) {
            return false;
        }
        var ring = packed >>> 8;
        var angle = packed & 0xFF;
        var angles = ring == 2 ? SurroundSlots.RANGED : ring == 0 ? SurroundSlots.INNER : SurroundSlots.OUTER;
        if (angle >= angles) {
            return false;
        }
        return SurroundSlots.inExitSector(SurroundSlots.angleDegrees(angle, angles), degrees);
    }
}
