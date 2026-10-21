package akyrion.thepredict.domain;

/**
 * The three derived layers of a zone (M24.1). A patch reads a source window of radius 17 and writes an output window
 * of radius 9. A chamfer on the 19 by 19 window alone is the stored counterexample and does not match a rebuild.
 */
public final class ZoneLayers {
    /** Cap of every layer, in thirds. */
    public static final short CAP = 24;
    /** Radius of the source window. The window is 35 by 35. */
    public static final int SOURCE_RADIUS = 17;
    /** Radius of the written window. The window is 19 by 19. */
    public static final int OUTPUT_RADIUS = 9;
    /** Columns a patch must have read around the change. The window is 37 by 37. */
    public static final int COLUMN_RADIUS = 18;

    private ZoneLayers() {
    }

    /**
     * Whether a column is a hazard source: harmful, a pit or void.
     *
     * @param column packed column
     * @return whether the chamfer starts here
     */
    public static boolean hazardSource(long column) {
        var state = ZoneColumn.state(column);
        return ZoneColumn.harmful(column) || state == ZoneColumn.PIT || state == ZoneColumn.VOID;
    }

    /**
     * Whether a walkable column touches a pit, void, unknown neighbour or an unsafe drop.
     *
     * @param columns packed columns
     * @param width width of the square
     * @param x column X
     * @param z column Z
     * @param maxSafeDrop drop that is still safe
     * @return whether the chamfer starts here
     */
    public static boolean edgeSource(long[] columns, int width, int x, int z, int maxSafeDrop) {
        var column = columns[z * width + x];
        if (!ZoneColumn.walkable(column, ZoneColumn.QUERY_BODY_BLOCKS)) {
            return false;
        }
        var floor = ZoneColumn.floorY(column);
        for (var dz = -1; dz <= 1; dz++) {
            for (var dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                var nx = x + dx;
                var nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= width || nz >= width) {
                    return true;
                }
                var neighbour = columns[nz * width + nx];
                var state = ZoneColumn.state(neighbour);
                if (state == ZoneColumn.PIT || state == ZoneColumn.VOID || state == ZoneColumn.UNKNOWN) {
                    return true;
                }
                if (state == ZoneColumn.FLOOR && !ZoneColumn.flag(neighbour, ZoneColumn.SOFT_LANDING)
                        && floor - ZoneColumn.floorY(neighbour) > maxSafeDrop) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a column blocks walking.
     *
     * @param column packed column
     * @return whether it is an obstacle
     */
    public static boolean obstacleSource(long column) {
        return !ZoneColumn.walkable(column, ZoneColumn.QUERY_BODY_BLOCKS);
    }

    /**
     * Fills the three source bitsets from the columns.
     *
     * @param columns packed columns
     * @param width width of the square
     * @param maxSafeDrop safe drop
     * @param hazard hazard sources
     * @param edge edge sources
     * @param obstacle obstacle sources
     */
    public static void sources(long[] columns, int width, int maxSafeDrop, boolean[] hazard, boolean[] edge,
                               boolean[] obstacle) {
        for (var z = 0; z < width; z++) {
            for (var x = 0; x < width; x++) {
                var index = z * width + x;
                hazard[index] = hazardSource(columns[index]);
                edge[index] = edgeSource(columns, width, x, z, maxSafeDrop);
                obstacle[index] = obstacleSource(columns[index]);
            }
        }
    }

    /**
     * Reclassifies the 3 by 3 sources around one column.
     *
     * @param columns packed columns
     * @param width width
     * @param x column X
     * @param z column Z
     * @param maxSafeDrop safe drop
     * @param hazard hazard sources
     * @param edge edge sources
     * @param obstacle obstacle sources
     */
    public static void sourcesAround(long[] columns, int width, int x, int z, int maxSafeDrop, boolean[] hazard,
                                     boolean[] edge, boolean[] obstacle) {
        for (var dz = -1; dz <= 1; dz++) {
            for (var dx = -1; dx <= 1; dx++) {
                var nx = x + dx;
                var nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= width || nz >= width) {
                    continue;
                }
                var index = nz * width + nx;
                hazard[index] = hazardSource(columns[index]);
                edge[index] = edgeSource(columns, width, nx, nz, maxSafeDrop);
                obstacle[index] = obstacleSource(columns[index]);
            }
        }
    }

    /**
     * Rebuilds one layer from its sources.
     *
     * @param source source bitset
     * @param layer destination thirds
     * @param width width
     * @param scratch scratch of at least {@code width * width}
     */
    public static void rebuild(boolean[] source, short[] layer, int width, int[] scratch) {
        ZoneChamfer.window(source, width, 0, 0, width - 1, width - 1, layer, scratch, 0, 0, width - 1, width - 1);
    }

    /**
     * Patches one layer. The source window has radius {@link #SOURCE_RADIUS}; only radius {@link #OUTPUT_RADIUS} is
     * written.
     *
     * @param source source bitset
     * @param layer destination thirds
     * @param width width
     * @param x changed column X
     * @param z changed column Z
     * @param scratch scratch of at least {@code 35 * 35}
     */
    public static void patch(boolean[] source, short[] layer, int width, int x, int z, int[] scratch) {
        write(source, layer, width, x, z, SOURCE_RADIUS, OUTPUT_RADIUS, scratch);
    }

    /**
     * The narrow transform that must not match a rebuild: chamfer only the 19 by 19 window.
     *
     * @param source source bitset
     * @param layer destination thirds
     * @param width width
     * @param x changed column X
     * @param z changed column Z
     * @param scratch scratch of at least {@code 19 * 19}
     */
    public static void patchNarrow(boolean[] source, short[] layer, int width, int x, int z, int[] scratch) {
        write(source, layer, width, x, z, OUTPUT_RADIUS, OUTPUT_RADIUS, scratch);
    }

    private static void write(boolean[] source, short[] layer, int width, int x, int z, int read, int written,
                              int[] scratch) {
        var x0 = clamp(x - read, 0, width - 1);
        var z0 = clamp(z - read, 0, width - 1);
        var x1 = clamp(x + read, 0, width - 1);
        var z1 = clamp(z + read, 0, width - 1);
        var wx0 = clamp(x - written, 0, width - 1);
        var wz0 = clamp(z - written, 0, width - 1);
        var wx1 = clamp(x + written, 0, width - 1);
        var wz1 = clamp(z + written, 0, width - 1);
        ZoneChamfer.window(source, width, x0, z0, x1, z1, layer, scratch, wx0, wz0, wx1, wz1);
    }

    private static int clamp(int value, int least, int most) {
        if (value < least) {
            return least;
        }
        return value > most ? most : value;
    }
}
