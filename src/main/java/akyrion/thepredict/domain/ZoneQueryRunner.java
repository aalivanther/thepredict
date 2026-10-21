package akyrion.thepredict.domain;

/**
 * The seven zone queries (M24.4). Deterministic: a tie keeps the earlier candidate index. Scratch lives on the
 * instance, so one query allocates nothing. The 33rd query of a tick is deferred and the next tick serves its first.
 * A ring of 8 angles is the eight compass directions at its radius. A ring of 16 is those eight, then the same eight
 * moved one column east on even angles and one column west on odd ones: not yet the sixteen true angles of M24.4.
 */
public final class ZoneQueryRunner {
    private static final int CANDIDATES = 24;
    private static final int[] COS = {1000, 707, 0, -707, -1000, -707, 0, 707};
    private static final int[] SIN = {0, 707, 1000, 707, 0, -707, -1000, -707};
    private final int[] cell = new int[CANDIDATES];
    private final int[] score = new int[CANDIDATES];
    private final int[] order = new int[CANDIDATES];
    private int served;
    private int limit = 32;

    /** Creates a runner with room for 24 candidates and the default query cap. */
    public ZoneQueryRunner() {
    }

    /**
     * Replaces the per-tick cap. The delivered default is 32.
     *
     * @param queries queries served before the next one is deferred, at least 1
     */
    public void limit(int queries) {
        if (queries < 1) {
            throw new IllegalArgumentException("queries must be >= 1, was " + queries);
        }
        limit = queries;
    }

    /**
     * Forgets the queries of the tick that just ended.
     */
    public void nextTick() {
        served = 0;
    }

    /**
     * Cover hidden from the threat. Rings of radius 2, 4 and 6.
     *
     * @param view the zone
     * @param mobX mob column
     * @param mobZ mob column
     * @param threatX threat column
     * @param threatZ threat column
     * @return the packed answer
     */
    public long cover(ZoneView view, int mobX, int mobZ, int threatX, int threatZ) {
        if (defer()) {
            return ZonePick.deferred();
        }
        return coverInside(view, mobX, mobZ, threatX, threatZ);
    }

    private long coverInside(ZoneView view, int mobX, int mobZ, int threatX, int threatZ) {
        if (view.epoch() <= 0) {
            return ZonePick.unknown();
        }
        var count = ring(view, mobX, mobZ, 2, 8, 0);
        count = ring(view, mobX, mobZ, 4, 8, count);
        count = ring(view, mobX, mobZ, 6, 8, count);
        return hidden(view, mobX, mobZ, threatX, threatZ, count, ZoneMargins.TWO, ZoneMargins.TWO, true);
    }

    /**
     * A retreat at {@code distance} and at half of it.
     *
     * @param view the zone
     * @param mobX mob column
     * @param mobZ mob column
     * @param awayX retreat direction X, -1, 0 or 1
     * @param awayZ retreat direction Z
     * @param distance preferred distance
     * @return the packed answer
     */
    public long retreat(ZoneView view, int mobX, int mobZ, int awayX, int awayZ, int distance) {
        if (defer()) {
            return ZonePick.deferred();
        }
        if (view.epoch() <= 0) {
            return ZonePick.unknown();
        }
        var count = ring(view, mobX, mobZ, distance, 16, 0);
        count = ring(view, mobX, mobZ, distance / 2, 16, count);
        return bestSafe(view, mobX, mobZ, count, ZoneMargins.TWO, ZoneMargins.THREE, awayX, awayZ, true, false);
    }

    /**
     * Q2 at distance 3, keeping only the forward half. The rear half ({@code dot <= 0}) is not a candidate.
     *
     * @param view the zone
     * @param mobX mob column
     * @param mobZ mob column
     * @param awayX direction away from the threat
     * @param awayZ direction away from the threat
     * @return the packed answer
     */
    public long reposition(ZoneView view, int mobX, int mobZ, int awayX, int awayZ) {
        if (defer()) {
            return ZonePick.deferred();
        }
        if (view.epoch() <= 0) {
            return ZonePick.unknown();
        }
        var count = ring(view, mobX, mobZ, 3, 16, 0);
        return bestSafe(view, mobX, mobZ, count, ZoneMargins.TWO, ZoneMargins.THREE, awayX, awayZ, true, true);
    }

    /**
     * A flank around the target.
     *
     * @param view the zone
     * @param mobX mob column
     * @param mobZ mob column
     * @param targetX target column
     * @param targetZ target column
     * @param reach ring radius
     * @param ranged whether height and a line of sight count
     * @return the packed answer
     */
    public long flank(ZoneView view, int mobX, int mobZ, int targetX, int targetZ, int reach, boolean ranged) {
        if (defer()) {
            return ZonePick.deferred();
        }
        if (view.epoch() <= 0) {
            return ZonePick.unknown();
        }
        var count = ring(view, targetX, targetZ, reach, 16, 0);
        var best = -1;
        var bestScore = Integer.MIN_VALUE;
        var walkable = 0;
        var hazard = 0;
        var edge = 0;
        for (var i = 0; i < count; i++) {
            var index = cell[i];
            var column = view.column(index);
            if (!ZoneColumn.walkable(column, ZoneColumn.QUERY_BODY_BLOCKS)) {
                continue;
            }
            walkable++;
            if (view.hazardThirds(index) < ZoneMargins.TWO) {
                hazard++;
                continue;
            }
            if (view.edgeThirds(index) < ZoneMargins.TWO) {
                edge++;
                continue;
            }
            var dy = ZoneColumn.floorY(column) - view.anchorY();
            if (dy > 3) {
                continue;
            }
            var value = flankScore(view, mobX, mobZ, targetX, targetZ, index, ranged, dy);
            if (value > bestScore) {
                bestScore = value;
                best = i;
            }
        }
        if (best < 0) {
            return ZonePick.none(miss(count, walkable, hazard, edge, false));
        }
        if (ranged && ZoneLine.blocked(view, columnX(view, cell[best]), columnZ(view, cell[best]), targetX, targetZ)) {
            return ZonePick.none(ZoneMiss.ALL_VISIBLE);
        }
        return ZonePick.pack(cell[best], clampScore(bestScore), ZonePick.FOUND, ZoneMiss.NONE);
    }

    /**
     * A safe cell under the feet or in a spiral of radius 2. Allocates nothing. An unknown cell is skipped, never
     * chosen. With no safe cell the answer is {@link ZonePick#UNKNOWN} only when the requested cell itself is
     * unknown; a known requested cell that is not safe gives {@link ZonePick#NONE}, so the caller rejects it (A12).
     * The spiral stops at the first radius that holds a safe cell, because a later radius cannot score higher.
     *
     * @param view the zone
     * @param x column X
     * @param z column Z
     * @return the packed answer
     */
    public long destination(ZoneView view, int x, int z) {
        if (defer()) {
            return ZonePick.deferred();
        }
        if (view.epoch() <= 0) {
            return ZonePick.unknown();
        }
        var width = view.width();
        var best = -1;
        var bestScore = Integer.MIN_VALUE;
        for (var radius = 0; radius <= 2 && best < 0; radius++) {
            for (var dz = -radius; dz <= radius; dz++) {
                for (var dx = -radius; dx <= radius; dx++) {
                    if (StrictMath.abs(dx) != radius && StrictMath.abs(dz) != radius) {
                        continue;
                    }
                    var index = safeCell(view, width, x + dx, z + dz);
                    var value = 400 - (StrictMath.abs(dx) + StrictMath.abs(dz)) * 30;
                    if (index >= 0 && value > bestScore) {
                        bestScore = value;
                        best = index;
                    }
                }
            }
        }
        if (best >= 0) {
            return ZonePick.pack(best, clampScore(bestScore), ZonePick.FOUND, ZoneMiss.NONE);
        }
        return unknownAt(view, width, x, z) ? ZonePick.unknown() : ZonePick.none(ZoneMiss.NO_WALKABLE);
    }

    private static int safeCell(ZoneView view, int width, int x, int z) {
        if (x < 0 || z < 0 || x >= width || z >= width) {
            return -1;
        }
        var index = z * width + x;
        if (!ZoneColumn.walkable(view.column(index), ZoneColumn.QUERY_BODY_BLOCKS)
                || view.hazardThirds(index) < ZoneMargins.ONE_AND_HALF
                || view.edgeThirds(index) < ZoneMargins.ONE_AND_HALF) {
            return -1;
        }
        return index;
    }

    private static boolean unknownAt(ZoneView view, int width, int x, int z) {
        return x >= 0 && z >= 0 && x < width && z < width
                && ZoneColumn.state(view.column(z * width + x)) == ZoneColumn.UNKNOWN;
    }

    /**
     * A kite lane within 90 degrees of the retreat, walked at most 8 blocks.
     *
     * @param view the zone
     * @param mobX mob column
     * @param mobZ mob column
     * @param awayX retreat direction
     * @param awayZ retreat direction
     * @return the packed answer
     */
    public long kite(ZoneView view, int mobX, int mobZ, int awayX, int awayZ) {
        if (defer()) {
            return ZonePick.deferred();
        }
        if (view.epoch() <= 0) {
            return ZonePick.unknown();
        }
        var best = -1;
        var bestScore = Integer.MIN_VALUE;
        var width = view.width();
        for (var angle = 0; angle < 8; angle++) {
            var dx = COS[angle];
            var dz = SIN[angle];
            var dot = dx * awayX + dz * awayZ;
            if (dot < 0) {
                continue;
            }
            var length = walk(view, width, mobX, mobZ, dx, dz);
            var deviation = 4 - (dot > 900 ? 4 : dot > 600 ? 3 : dot > 200 ? 2 : dot > 0 ? 1 : 0);
            var value = length * 20 - deviation * 5;
            if (length > 0 && value > bestScore) {
                bestScore = value;
                best = length;
                cell[0] = (mobZ + (dz > 0 ? length : dz < 0 ? -length : 0)) * width
                        + mobX + (dx > 0 ? length : dx < 0 ? -length : 0);
            }
        }
        return best < 0 ? ZonePick.none(ZoneMiss.NO_WALKABLE) : ZonePick.pack(cell[0], clampScore(bestScore),
                ZonePick.FOUND, ZoneMiss.NONE);
    }

    /**
     * What a push does. Allocates nothing. Lethal beats harm, harm beats unknown, unknown beats none.
     *
     * @param view the zone
     * @param x start column
     * @param z start column
     * @param dx step X, -1, 0 or 1
     * @param dz step Z
     * @param depth steps to read
     * @param health current health points
     * @return the danger
     */
    public PushDanger push(ZoneView view, int x, int z, int dx, int dz, int depth, int health) {
        if (defer() || view.epoch() <= 0) {
            return PushDanger.UNKNOWN;
        }
        var worst = PushDanger.NONE;
        var width = view.width();
        var origin = columnAt(view, width, x, z);
        var originY = ZoneColumn.floorY(origin);
        for (var step = 1; step <= depth; step++) {
            var lateral = step == depth ? 1 : 0;
            for (var side = -lateral; side <= lateral; side++) {
                var cx = x + dx * step + (dz == 0 ? side : 0);
                var cz = z + dz * step + (dx == 0 ? side : 0);
                worst = worst.worse(cellDanger(view, width, cx, cz, originY, health));
                if (worst == PushDanger.LETHAL) {
                    return worst;
                }
            }
        }
        return worst;
    }

    /**
     * Whether an answer fits the ground. One query of the tick cap, also for {@code BREAK_SIGHT}, whose cover search
     * runs inside it: a cap reached by this query alone answers {@link Feasibility#UNKNOWN}, never infeasible.
     *
     * @param view the zone
     * @param answer the answer the tell would ask
     * @param mobX attacker column
     * @param mobZ attacker column
     * @param targetX target column
     * @param targetZ target column
     * @param reach hit reach in blocks
     * @return the feasibility
     */
    public Feasibility feasible(ZoneView view, TellAnswer answer, int mobX, int mobZ, int targetX, int targetZ,
                                int reach) {
        if (defer() || view.epoch() <= 0) {
            return Feasibility.UNKNOWN;
        }
        if (answer == TellAnswer.BLOCK || answer == TellAnswer.INTERRUPT) {
            return Feasibility.FEASIBLE;
        }
        if (answer == TellAnswer.DODGE) {
            return lateral(view, targetX, targetZ) ? Feasibility.FEASIBLE : Feasibility.INFEASIBLE;
        }
        if (answer == TellAnswer.BACK_OFF) {
            return backOff(view, mobX, mobZ, targetX, targetZ, reach);
        }
        var cover = coverInside(view, targetX, targetZ, mobX, mobZ);
        if (ZonePick.state(cover) == ZonePick.UNKNOWN) {
            return Feasibility.UNKNOWN;
        }
        if (ZonePick.state(cover) != ZonePick.FOUND) {
            return Feasibility.INFEASIBLE;
        }
        var width = view.width();
        var index = ZonePick.cell(cover);
        var distance = StrictMath.abs(index % width - targetX) + StrictMath.abs(index / width - targetZ);
        return distance <= 4 ? Feasibility.FEASIBLE : Feasibility.INFEASIBLE;
    }

    private boolean defer() {
        if (served >= limit) {
            return true;
        }
        served++;
        return false;
    }

    private int ring(ZoneView view, int x, int z, int radius, int angles, int start) {
        var width = view.width();
        var count = start;
        for (var angle = 0; angle < angles && count < CANDIDATES; angle++) {
            var direction = angle % 8;
            var nudge = angle < 8 ? 0 : (angle & 1) == 0 ? 1 : -1;
            var cx = x + COS[direction] * radius / 1000 + nudge;
            var cz = z + SIN[direction] * radius / 1000;
            if (cx < 0 || cz < 0 || cx >= width || cz >= width) {
                continue;
            }
            cell[count] = cz * width + cx;
            score[count] = radius;
            count++;
        }
        return count;
    }

    private long hidden(ZoneView view, int mobX, int mobZ, int threatX, int threatZ, int count, int hazardNeed,
                        int edgeNeed, boolean sight) {
        var ranked = 0;
        var walkable = 0;
        var hazardFail = 0;
        var edgeFail = 0;
        for (var i = 0; i < count; i++) {
            var index = cell[i];
            var column = view.column(index);
            if (ZoneColumn.state(column) == ZoneColumn.UNKNOWN) {
                return ZonePick.unknown();
            }
            if (!ZoneColumn.walkable(column, ZoneColumn.QUERY_BODY_BLOCKS)) {
                continue;
            }
            walkable++;
            if (view.hazardThirds(index) < hazardNeed) {
                hazardFail++;
                continue;
            }
            if (view.edgeThirds(index) < edgeNeed) {
                edgeFail++;
                continue;
            }
            var clearance = view.clearanceThirds(index) / 3;
            if (clearance > 4) {
                clearance = 4;
            }
            var margin = view.hazardThirds(index);
            if (view.edgeThirds(index) < margin) {
                margin = view.edgeThirds(index);
            }
            margin /= 3;
            if (margin > 4) {
                margin = 4;
            }
            score[ranked] = 1000 - score[i] * 20 + clearance * 10 + margin * 10;
            order[ranked] = i;
            ranked++;
        }
        sort(order, ranked);
        var lines = 0;
        for (var n = 0; n < ranked && lines < 8; n++) {
            var index = cell[order[n]];
            lines++;
            var hidden = ZoneLine.blocked(view, columnX(view, index), columnZ(view, index), threatX, threatZ);
            if (!sight || hidden) {
                return ZonePick.pack(index, clampScore(score[n]), ZonePick.FOUND, ZoneMiss.NONE);
            }
        }
        return ZonePick.none(miss(count, walkable, hazardFail, edgeFail, ranked > 0));
    }

    private long bestSafe(ZoneView view, int mobX, int mobZ, int count, int hazardNeed, int edgeNeed, int awayX,
                          int awayZ, boolean stepped, boolean forwardOnly) {
        var best = -1;
        var bestScore = Integer.MIN_VALUE;
        var walkable = 0;
        var hazardFail = 0;
        var edgeFail = 0;
        var blocked = 0;
        var width = view.width();
        for (var i = 0; i < count; i++) {
            var index = cell[i];
            var column = view.column(index);
            if (!ZoneColumn.walkable(column, ZoneColumn.QUERY_BODY_BLOCKS)) {
                continue;
            }
            walkable++;
            if (view.hazardThirds(index) < hazardNeed) {
                hazardFail++;
                continue;
            }
            if (view.edgeThirds(index) < edgeNeed) {
                edgeFail++;
                continue;
            }
            var cx = index % width;
            var cz = index / width;
            if (stepped && !steps(view, mobX, mobZ, cx, cz)) {
                blocked++;
                continue;
            }
            var dot = Integer.signum(cx - mobX) * awayX + Integer.signum(cz - mobZ) * awayZ;
            if (forwardOnly && dot <= 0) {
                continue;
            }
            var clearance = view.clearanceThirds(index) / 3;
            if (clearance > 4) {
                clearance = 4;
            }
            var value = (dot > 0 ? 40 : 0) + clearance * 10;
            if (clearance <= 1) {
                value -= 60;
            }
            if (value > bestScore) {
                bestScore = value;
                best = index;
            }
        }
        if (best < 0) {
            return ZonePick.none(blocked > 0 && walkable > 0 ? ZoneMiss.BLOCKED_STEP
                    : miss(count, walkable, hazardFail, edgeFail, false));
        }
        return ZonePick.pack(best, clampScore(bestScore), ZonePick.FOUND, ZoneMiss.NONE);
    }

    private static boolean steps(ZoneView view, int x0, int z0, int x1, int z1) {
        var width = view.width();
        var steps = StrictMath.abs(x1 - x0) + StrictMath.abs(z1 - z0);
        if (steps == 0) {
            return true;
        }
        var x = x0;
        var z = z0;
        for (var i = 0; i < steps; i++) {
            if (x != x1) {
                x += x1 > x ? 1 : -1;
            } else if (z != z1) {
                z += z1 > z ? 1 : -1;
            }
            var from = view.column(z0 * width + x0);
            var to = view.column(z * width + x);
            if (!ZoneColumn.step(from, to)) {
                return false;
            }
            x0 = x;
            z0 = z;
        }
        return true;
    }

    private int flankScore(ZoneView view, int mobX, int mobZ, int targetX, int targetZ, int index, boolean ranged,
                           int dy) {
        var width = view.width();
        var cx = index % width;
        var cz = index / width;
        var shield = (mobX - targetX) * (cx - targetX) + (mobZ - targetZ) * (cz - targetZ);
        var value = shield <= 0 ? 40 : 0;
        if (ranged && dy > 0) {
            value += (dy > 3 ? 3 : dy) * 15;
        }
        var distance = StrictMath.abs(cx - mobX) + StrictMath.abs(cz - mobZ);
        return value - distance * 10;
    }

    private int walk(ZoneView view, int width, int x, int z, int dx, int dz) {
        var length = 0;
        var px = x;
        var pz = z;
        var stepX = dx > 200 ? 1 : dx < -200 ? -1 : 0;
        var stepZ = dz > 200 ? 1 : dz < -200 ? -1 : 0;
        for (var i = 0; i < 8; i++) {
            var nx = px + stepX;
            var nz = pz + stepZ;
            if (nx < 0 || nz < 0 || nx >= width || nz >= width) {
                break;
            }
            var column = view.column(nz * width + nx);
            if (!ZoneColumn.walkable(column, ZoneColumn.QUERY_BODY_BLOCKS)) {
                break;
            }
            if (view.hazardThirds(nz * width + nx) < ZoneMargins.ONE_AND_HALF
                    || view.edgeThirds(nz * width + nx) < ZoneMargins.ONE_AND_HALF) {
                break;
            }
            if (!ZoneColumn.step(view.column(pz * width + px), column)) {
                break;
            }
            px = nx;
            pz = nz;
            length++;
        }
        return length;
    }

    private PushDanger cellDanger(ZoneView view, int width, int x, int z, int originY, int health) {
        if (x < 0 || z < 0 || x >= width || z >= width) {
            return PushDanger.UNKNOWN;
        }
        var column = view.column(z * width + x);
        var state = ZoneColumn.state(column);
        if (state == ZoneColumn.UNKNOWN) {
            return PushDanger.UNKNOWN;
        }
        if (state == ZoneColumn.VOID || ZoneColumn.flag(column, ZoneColumn.LAVA)) {
            return PushDanger.LETHAL;
        }
        if (state == ZoneColumn.PIT || (state == ZoneColumn.FLOOR
                && originY - ZoneColumn.floorY(column) > view.maxSafeDrop()
                && !ZoneColumn.flag(column, ZoneColumn.SOFT_LANDING))) {
            var fall = state == ZoneColumn.PIT ? ZoneColumn.depth(column) : originY - ZoneColumn.floorY(column);
            var damage = fall <= 3 ? 0 : fall - 3;
            if (ZoneColumn.flag(column, ZoneColumn.SPIKES)) {
                damage *= 2;
            }
            if (ZoneColumn.flag(column, ZoneColumn.SOFT_LANDING)) {
                damage = 0;
            }
            return damage >= health ? PushDanger.LETHAL : PushDanger.HARM;
        }
        if (ZoneColumn.harmful(column)) {
            return PushDanger.HARM;
        }
        return PushDanger.NONE;
    }

    private boolean lateral(ZoneView view, int x, int z) {
        var width = view.width();
        return safeAnswer(view, width, x - 2, z) || safeAnswer(view, width, x + 2, z)
                || safeAnswer(view, width, x, z - 2) || safeAnswer(view, width, x, z + 2);
    }

    private Feasibility backOff(ZoneView view, int mobX, int mobZ, int targetX, int targetZ, int reach) {
        var awayX = Integer.signum(targetX - mobX);
        var awayZ = Integer.signum(targetZ - mobZ);
        var width = view.width();
        var distance = reach + 1;
        var unknown = false;
        for (var angle = 0; angle < 5; angle++) {
            var side = angle - 2;
            var still = awayX == 0 && awayZ == 0;
            var dx = still ? side : awayX * distance - awayZ * side;
            var dz = still ? distance : awayZ * distance + awayX * side;
            var cx = targetX + dx;
            var cz = targetZ + dz;
            if (cx < 0 || cz < 0 || cx >= width || cz >= width) {
                unknown = true;
                continue;
            }
            if (ZoneColumn.state(view.column(cz * width + cx)) == ZoneColumn.UNKNOWN) {
                unknown = true;
                continue;
            }
            if (safeAnswer(view, width, cx, cz)) {
                return Feasibility.FEASIBLE;
            }
        }
        return unknown ? Feasibility.UNKNOWN : Feasibility.INFEASIBLE;
    }

    private static boolean safeAnswer(ZoneView view, int width, int x, int z) {
        if (x < 0 || z < 0 || x >= width || z >= width) {
            return false;
        }
        var index = z * width + x;
        var column = view.column(index);
        return ZoneColumn.walkable(column, ZoneColumn.QUERY_BODY_BLOCKS)
                && view.hazardThirds(index) >= ZoneMargins.ONE && view.edgeThirds(index) >= ZoneMargins.ONE;
    }

    private static long columnAt(ZoneView view, int width, int x, int z) {
        if (x < 0 || z < 0 || x >= width || z >= width) {
            return 0L;
        }
        return view.column(z * width + x);
    }

    private static int columnX(ZoneView view, int index) {
        return index % view.width();
    }

    private static int columnZ(ZoneView view, int index) {
        return index / view.width();
    }

    private void sort(int[] order, int count) {
        for (var i = 1; i < count; i++) {
            var key = order[i];
            var value = score[i];
            var j = i - 1;
            while (j >= 0 && score[j] < value) {
                score[j + 1] = score[j];
                order[j + 1] = order[j];
                j--;
            }
            score[j + 1] = value;
            order[j + 1] = key;
        }
    }

    private static ZoneMiss miss(int count, int walkable, int hazard, int edge, boolean visible) {
        if (walkable == 0) {
            return ZoneMiss.NO_WALKABLE;
        }
        if (hazard >= walkable) {
            return ZoneMiss.ALL_HAZARD;
        }
        if (edge > 0 && walkable - hazard == edge) {
            return ZoneMiss.ALL_EDGE;
        }
        if (visible) {
            return ZoneMiss.ALL_VISIBLE;
        }
        return count == 0 ? ZoneMiss.NO_WALKABLE : ZoneMiss.ALL_HAZARD;
    }

    private static int clampScore(int value) {
        if (value < 0) {
            return 0;
        }
        return value > 4095 ? 4095 : value;
    }
}
