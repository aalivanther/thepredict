package akyrion.thepredict.engine.duel;

import java.util.List;
import java.util.Objects;

/**
 * The floor of a duel (§7.4 of the simulator audit): a circle with a wall at {@code radius} and round pillars that
 * block movement and the line of sight. Distances are between positions on the plane, as {@code targetwithin} measures
 * them. Immutable and pure.
 *
 * @param id name in the report
 * @param radius the wall: no position lies farther from the centre
 * @param pillars obstacles, empty for an open arena
 */
record Arena(String id, double radius, List<Pillar> pillars) {
    /** Half the width of a body: a centre never comes closer than this to a pillar. */
    static final double HALF_WIDTH = DuelWorld.WIDTH / 2;

    /** An open circle of radius 18, the arena of the duel pack. */
    static final Arena OPEN18 = new Arena("OPEN18", 18, List.of());
    /** An open circle of radius 8: no room to flee. */
    static final Arena SMALL8 = new Arena("SMALL8", 8, List.of());
    /** Radius 18 with four pillars of radius 1.5 at (±6, ±6), which cut the line of sight of a bolt. */
    static final Arena PILLARS18 = new Arena("PILLARS18", 18, List.of(new Pillar(6, 6, 1.5), new Pillar(-6, 6, 1.5),
            new Pillar(6, -6, 1.5), new Pillar(-6, -6, 1.5)));

    /**
     * One pillar.
     *
     * @param x centre
     * @param z centre
     * @param radius radius of the column
     */
    record Pillar(double x, double z, double radius) {
    }

    /**
     * Validates the arena.
     *
     * @throws IllegalArgumentException when the radius is not positive
     */
    Arena {
        Objects.requireNonNull(id, "id");
        pillars = List.copyOf(pillars);
        if (!(radius > 0)) {
            throw new IllegalArgumentException("radius must be positive, was " + radius);
        }
    }

    /**
     * Whether nothing stands between two positions: no pillar crosses the segment.
     *
     * @param ax first x
     * @param az first z
     * @param bx second x
     * @param bz second z
     * @return the line of sight
     */
    boolean sight(double ax, double az, double bx, double bz) {
        for (var index = 0; index < pillars.size(); index++) {
            var pillar = pillars.get(index);
            if (segmentDistance(pillar.x(), pillar.z(), ax, az, bx, bz) < pillar.radius()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a position is free: inside the wall and outside every pillar with half a body of margin.
     *
     * @param x position
     * @param z position
     * @return whether a body may stand there
     */
    boolean free(double x, double z) {
        if (Math.hypot(x, z) > radius - HALF_WIDTH) {
            return false;
        }
        for (var index = 0; index < pillars.size(); index++) {
            var pillar = pillars.get(index);
            if (Math.hypot(x - pillar.x(), z - pillar.z()) < pillar.radius() + HALF_WIDTH) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pushes a body out of the pillars it overlaps, along the line from each centre, then inside the wall.
     *
     * @param body the body that moved
     */
    void keepInside(DuelWorld.Body body) {
        for (var index = 0; index < pillars.size(); index++) {
            var pillar = pillars.get(index);
            var dx = body.x - pillar.x();
            var dz = body.z - pillar.z();
            var distance = Math.hypot(dx, dz);
            var limit = pillar.radius() + HALF_WIDTH;
            if (distance < limit) {
                var nx = distance < 1.0e-9 ? 1.0 : dx / distance;
                var nz = distance < 1.0e-9 ? 0.0 : dz / distance;
                body.x = pillar.x() + nx * limit;
                body.z = pillar.z() + nz * limit;
            }
        }
        var fromCenter = Math.hypot(body.x, body.z);
        if (fromCenter > radius) {
            body.x *= radius / fromCenter;
            body.z *= radius / fromCenter;
        }
    }

    private static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
        var dx = bx - ax;
        var dz = bz - az;
        var length = dx * dx + dz * dz;
        var t = length < 1.0e-12 ? 0.0 : ((px - ax) * dx + (pz - az) * dz) / length;
        t = Math.max(0.0, Math.min(1.0, t));
        return Math.hypot(ax + t * dx - px, az + t * dz - pz);
    }
}
