package akyrion.thepredict.domain;

/**
 * Where a surround slot sits relative to the player (M25). Distances are in blocks.
 */
public final class SlotGeometry {
    private SlotGeometry() {
    }

    /**
     * Blocks from a mob to the slot. The mob offset is in blocks from the player.
     *
     * @param packed slot {@code (ring << 8) | angle}, or negative when none
     * @param mobX blocks east of the player
     * @param mobZ blocks south of the player
     * @return the distance, or 0 when the slot is absent
     */
    public static double blocksFrom(int packed, double mobX, double mobZ) {
        if (packed < 0) {
            return 0.0;
        }
        var ring = packed >>> 8;
        var angle = packed & 0xFF;
        var count = count(ring);
        var radians = StrictMath.toRadians(SurroundSlots.angleDegrees(Math.min(angle, count - 1), count));
        var radius = radius(ring);
        var slotX = radius * StrictMath.cos(radians);
        var slotZ = radius * StrictMath.sin(radians);
        return StrictMath.hypot(mobX - slotX, mobZ - slotZ);
    }

    /**
     * Whether two slots are closer than a block and a half.
     *
     * @param left packed slot
     * @param right packed slot
     * @return whether they crowd each other
     */
    public static boolean closerThanContact(int left, int right) {
        if (left < 0 || right < 0) {
            return false;
        }
        return blocksBetween(left, right) < 1.5;
    }

    static double blocksBetween(int left, int right) {
        var leftRing = left >>> 8;
        var rightRing = right >>> 8;
        var leftAngle = angle(left);
        var rightAngle = angle(right);
        var leftRadius = radius(leftRing);
        var rightRadius = radius(rightRing);
        var dx = leftRadius * StrictMath.cos(leftAngle) - rightRadius * StrictMath.cos(rightAngle);
        var dz = leftRadius * StrictMath.sin(leftAngle) - rightRadius * StrictMath.sin(rightAngle);
        return StrictMath.hypot(dx, dz);
    }

    private static double angle(int packed) {
        var ring = packed >>> 8;
        var count = count(ring);
        return StrictMath.toRadians(SurroundSlots.angleDegrees(Math.min(packed & 0xFF, count - 1), count));
    }

    /**
     * East offset of a slot from the player, in blocks.
     *
     * @param packed slot
     * @return the offset, or 0
     */
    public static double east(int packed) {
        if (packed < 0) {
            return 0.0;
        }
        return radius(packed >>> 8) * StrictMath.cos(angle(packed));
    }

    /**
     * South offset of a slot from the player, in blocks.
     *
     * @param packed slot
     * @return the offset, or 0
     */
    public static double south(int packed) {
        if (packed < 0) {
            return 0.0;
        }
        return radius(packed >>> 8) * StrictMath.sin(angle(packed));
    }

    static double radius(int ring) {
        if (ring == 2) {
            return 8.0;
        }
        return ring == 0 ? 3.0 : 5.0;
    }

    static int count(int ring) {
        if (ring == 2) {
            return SurroundSlots.RANGED;
        }
        return ring == 0 ? SurroundSlots.INNER : SurroundSlots.OUTER;
    }
}
