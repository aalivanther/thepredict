package akyrion.thepredict.domain;

/**
 * Whether a column blocks the eye line between two floors (M24.4). Eyes sit 1.62 blocks above the floor, in
 * hundredths. A column blocks when the top of its solid reaches the line. Endpoints are skipped. Unknown and stale
 * columns block; a pit, void, glass and a fence do not.
 */
public final class ZoneLine {
    /** Eye height above the floor, in hundredths of a block. */
    public static final int EYE_HUNDREDTHS = 162;

    private ZoneLine() {
    }

    /**
     * Whether the segment is blocked.
     *
     * @param view the zone
     * @param fromX column X of the start
     * @param fromZ column Z of the start
     * @param toX column X of the end
     * @param toZ column Z of the end
     * @return whether some column between the ends blocks the line
     */
    public static boolean blocked(ZoneView view, int fromX, int fromZ, int toX, int toZ) {
        var width = view.width();
        var from = column(view, width, fromX, fromZ);
        var to = column(view, width, toX, toZ);
        if (ZoneColumn.state(from) != ZoneColumn.FLOOR || ZoneColumn.state(to) != ZoneColumn.FLOOR) {
            return true;
        }
        var y0 = ZoneColumn.floorY(from) * 100 + EYE_HUNDREDTHS;
        var y1 = ZoneColumn.floorY(to) * 100 + EYE_HUNDREDTHS;
        var dx = StrictMath.abs(toX - fromX);
        var dz = StrictMath.abs(toZ - fromZ);
        var steps = dx > dz ? dx : dz;
        if (steps <= 1) {
            return false;
        }
        var sx = toX > fromX ? 1 : -1;
        var sz = toZ > fromZ ? 1 : -1;
        var x = fromX;
        var z = fromZ;
        var err = dx - dz;
        for (var step = 0; step < steps; step++) {
            var error2 = err << 1;
            if (error2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (error2 < dx) {
                err += dx;
                z += sz;
            }
            if (x == toX && z == toZ) {
                return false;
            }
            if (blocks(view, width, x, z, height(y0, y1, step + 1, steps))) {
                return true;
            }
        }
        return false;
    }

    private static boolean blocks(ZoneView view, int width, int x, int z, int lineHundredths) {
        if (x < 0 || z < 0 || x >= width || z >= width) {
            return true;
        }
        var column = view.column(z * width + x);
        var state = ZoneColumn.state(column);
        if (state == ZoneColumn.UNKNOWN || state == ZoneColumn.PIT || state == ZoneColumn.VOID) {
            return state == ZoneColumn.UNKNOWN;
        }
        if (ZoneColumn.flag(column, ZoneColumn.SEE_THROUGH) || ZoneColumn.flag(column, ZoneColumn.FENCE_LIKE)) {
            return false;
        }
        return ZoneColumn.floorY(column) * 100 >= lineHundredths;
    }

    private static int height(int y0, int y1, int step, int steps) {
        return y0 + (int) ((long) (y1 - y0) * step / steps);
    }

    private static long column(ZoneView view, int width, int x, int z) {
        if (x < 0 || z < 0 || x >= width || z >= width) {
            return 0L;
        }
        return view.column(z * width + x);
    }
}
