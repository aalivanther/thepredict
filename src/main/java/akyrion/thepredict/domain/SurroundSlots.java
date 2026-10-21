package akyrion.thepredict.domain;

/**
 * Angles of the three rings around a player (M25). Zero is the player's look. Sides alternate.
 * The exit sector is centred at 180 degrees.
 */
public final class SurroundSlots {
    /** Slots on the inner ring. */
    public static final int INNER = 8;
    /** Slots on the outer ring. */
    public static final int OUTER = 12;
    /** Slots on the ranged ring. */
    public static final int RANGED = 8;

    private SurroundSlots() {
    }

    /**
     * Angle of one slot. Index 0 is the look direction. Later indexes alternate sides.
     *
     * @param index slot index, starting at 0
     * @param count slots on the ring
     * @return degrees in {@code [0, 360)}
     */
    public static int angleDegrees(int index, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1");
        }
        if (index <= 0) {
            return 0;
        }
        var step = 360 / count;
        var turn = (index + 1) / 2;
        var signed = (index % 2 == 1 ? 1 : -1) * turn * step;
        return Math.floorMod(signed, 360);
    }

    /**
     * Whether an angle sits in the exit sector behind the player.
     *
     * @param angleDegrees angle from the player's look
     * @param sectorDegrees full width of the sector
     * @return whether the angle is excluded
     */
    public static boolean inExitSector(int angleDegrees, int sectorDegrees) {
        var half = sectorDegrees / 2;
        var delta = Math.floorMod(angleDegrees - 180, 360);
        if (delta > 180) {
            delta -= 360;
        }
        var distance = delta < 0 ? -delta : delta;
        return distance <= half;
    }

    /**
     * Nearest angle on the edge of the sector, used when a guard line falls inside it.
     *
     * @param sectorDegrees full width
     * @param preferLeft whether the left edge is closer to the group
     * @return the edge angle
     */
    public static int sectorEdge(int sectorDegrees, boolean preferLeft) {
        var half = sectorDegrees / 2;
        var edge = Math.floorMod(180 + (preferLeft ? -half - 1 : half + 1), 360);
        return edge;
    }
}
