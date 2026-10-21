package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.ZoneColumn;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.domain.ZonePick;
import akyrion.thepredict.domain.ZoneView;
import akyrion.thepredict.engine.MobSession;

import org.bukkit.Location;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Q4 and the edge filter of a flank point (M24.5). A mob without a zone, or a mode below guard, accepts the point;
 * an unknown column inside a published map refuses it.
 */
public final class ZoneFlank {
    /** Less than one block of edge margin, in thirds. */
    public static final int EDGE_BLOCK = 3;

    private ZoneFlank() {
    }

    /**
     * Whether the point is safe for a flank in guard or full.
     *
     * @param zones the registry
     * @param sessions session of the mob
     * @param entityId the mob
     * @param point a path point
     * @return {@code false} when the cell is harmful, on an edge, or Q4 is none
     */
    public static boolean allows(ZoneRegistry zones, Function<UUID, Optional<MobSession>> sessions, UUID entityId,
                                 Location point) {
        var session = sessions.apply(entityId).orElse(null);
        if (session == null) {
            return true;
        }
        var mode = session.zoneMode();
        if (mode != ZoneMode.GUARD && mode != ZoneMode.FULL) {
            return true;
        }
        var map = session.encounter().flatMap(zones::map).orElse(null);
        if (map == null || map.epoch() <= 0) {
            return true;
        }
        return allows(zones.queries(), map, point);
    }

    /**
     * Whether one cell passes the flank filter.
     *
     * @param queries the runner
     * @param map a published map
     * @param point a path point
     * @return {@code false} when the cell is unknown (M24 P3, SP-07), harmful, closer than one block to an edge, or
     *         Q4 is none
     */
    public static boolean allows(akyrion.thepredict.domain.ZoneQueryRunner queries, ZoneView map, Location point) {
        var x = (int) Math.floor(point.getX()) - map.originX();
        var z = (int) Math.floor(point.getZ()) - map.originZ();
        if (x < 0 || z < 0 || x >= map.width() || z >= map.width()) {
            return false;
        }
        var index = z * map.width() + x;
        var column = map.column(index);
        if (ZoneColumn.state(column) == ZoneColumn.UNKNOWN || ZoneColumn.harmful(column)
                || map.edgeThirds(index) < EDGE_BLOCK) {
            return false;
        }
        var state = ZonePick.state(queries.destination(map, x, z));
        return state != ZonePick.NONE;
    }
}
