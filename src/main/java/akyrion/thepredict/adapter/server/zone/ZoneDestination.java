package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.Steering;
import akyrion.thepredict.domain.ZoneColumn;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.domain.ZonePick;
import akyrion.thepredict.domain.ZoneQueryRunner;
import akyrion.thepredict.domain.ZoneView;
import akyrion.thepredict.engine.MobSession;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Q4 for a micro path (M24.5). Asked only in guard or full, and only when a map is published. {@link ZonePick#NONE}
 * refuses the path, and so does an unknown column under the point (M24 P3). Deferred keeps the point the micro
 * already chose. Enter is not filtered.
 */
public final class ZoneDestination {
    private ZoneDestination() {
    }

    /**
     * The point to path to, or empty when the zone has no safe cell.
     *
     * @param zones the registry
     * @param sessions session of the mob
     * @param entityId the mob
     * @param behaviour the micro; enter is returned unchanged
     * @param point the point the micro would have used
     * @param blockX mob column
     * @param blockZ mob column
     * @return the destination
     */
    public static Optional<Steering> ask(ZoneRegistry zones, Function<UUID, Optional<MobSession>> sessions,
                                         UUID entityId, MicroBehaviour behaviour, Steering point, int blockX,
                                         int blockZ) {
        if (behaviour == MicroBehaviour.ENTER) {
            return Optional.of(point);
        }
        var session = sessions.apply(entityId).orElse(null);
        if (session == null) {
            return Optional.of(point);
        }
        var mode = session.zoneMode();
        if (mode != ZoneMode.GUARD && mode != ZoneMode.FULL) {
            return Optional.of(point);
        }
        var map = session.encounter().flatMap(zones::map).orElse(null);
        if (map == null || map.epoch() <= 0) {
            return Optional.of(point);
        }
        return filter(zones.queries(), map, behaviour, point, blockX, blockZ);
    }

    /**
     * Q4 of the point, or Q2 at distance 3 for a reposition. The mob column is only the origin of that reposition.
     *
     * @param queries the runner
     * @param map a published map
     * @param behaviour the micro
     * @param point the point the micro would have used
     * @param blockX mob column
     * @param blockZ mob column
     * @return the destination, or empty when the zone refuses it
     */
    public static Optional<Steering> filter(ZoneQueryRunner queries, ZoneView map, MicroBehaviour behaviour,
                                            Steering point, int blockX, int blockZ) {
        if (behaviour == MicroBehaviour.REPOSITION) {
            return reposition(queries, map, point, blockX, blockZ);
        }
        var columnX = (int) Math.floor(point.x()) - map.originX();
        var columnZ = (int) Math.floor(point.z()) - map.originZ();
        return cell(map, queries.destination(map, columnX, columnZ), point);
    }

    private static Optional<Steering> reposition(ZoneQueryRunner queries, ZoneView map, Steering point, int blockX,
                                                 int blockZ) {
        var awayX = Integer.signum(blockX - (int) Math.floor(point.x()));
        var awayZ = Integer.signum(blockZ - (int) Math.floor(point.z()));
        if (awayX == 0 && awayZ == 0) {
            awayZ = -1;
        }
        var pick = queries.reposition(map, blockX - map.originX(), blockZ - map.originZ(), awayX, awayZ);
        return cell(map, pick, point);
    }

    /**
     * The cell of a pick. {@code NONE} refuses the point; {@code UNKNOWN} refuses it too when the point's own column
     * is unknown, an unloaded or unscanned chunk that M24 P3 calls unsafe for a destination (SP-07); a deferred
     * answer keeps the point.
     */
    private static Optional<Steering> cell(ZoneView map, long pick, Steering point) {
        var state = ZonePick.state(pick);
        if (state == ZonePick.NONE || state == ZonePick.UNKNOWN && unknownColumn(map, point)) {
            return Optional.empty();
        }
        if (state != ZonePick.FOUND) {
            return Optional.of(point);
        }
        var width = map.width();
        var index = ZonePick.cell(pick);
        return Optional.of(new Steering(map.originX() + index % width + 0.5, map.originZ() + index / width + 0.5));
    }

    private static boolean unknownColumn(ZoneView map, Steering point) {
        var x = (int) Math.floor(point.x()) - map.originX();
        var z = (int) Math.floor(point.z()) - map.originZ();
        if (x < 0 || z < 0 || x >= map.width() || z >= map.width()) {
            return true;
        }
        return ZoneColumn.state(map.column(z * map.width() + x)) == ZoneColumn.UNKNOWN;
    }
}
