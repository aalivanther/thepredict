package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.ZoneColumn;
import akyrion.thepredict.domain.ZoneGrid;
import akyrion.thepredict.domain.ZoneLayers;
import akyrion.thepredict.domain.ZoneMaterialFlags;

import org.bukkit.ChunkSnapshot;

/**
 * Reads one chunk snapshot into packed columns (M24.2). Pure over the snapshot: it never asks a world for a chunk.
 * The zone thread calls it. Empty sections are skipped.
 */
public final class ZoneColumnScan {
    private final ZoneScanner scanner = new ZoneScanner();
    private final ZoneMaterialTable table;

    /**
     * Creates a scan that classifies blocks with the shared material table.
     *
     * @param table material bits, read-only
     */
    public ZoneColumnScan(ZoneMaterialTable table) {
        if (table == null) {
            throw new IllegalArgumentException("material table is required");
        }
        this.table = table;
    }

    /**
     * Fills a 16-wide grid from one snapshot. Coordinates are local to the chunk.
     *
     * @param grid the grid under construction
     * @param snapshot the loaded chunk
     * @param chunkX chunk coordinate
     * @param chunkZ chunk coordinate
     * @param anchorY preferred floor
     * @param band half-band in blocks
     * @param minY world minimum height
     */
    public void fill(ZoneGrid grid, ChunkSnapshot snapshot, int chunkX, int chunkZ, int anchorY, int band, int minY) {
        var width = grid.width();
        var originX = grid.originX();
        var originZ = grid.originZ();
        var baseX = chunkX << 4;
        var baseZ = chunkZ << 4;
        for (var localZ = 0; localZ < 16; localZ++) {
            var gridZ = baseZ + localZ - originZ;
            if (gridZ < 0 || gridZ >= width) {
                continue;
            }
            for (var localX = 0; localX < 16; localX++) {
                var gridX = baseX + localX - originX;
                if (gridX < 0 || gridX >= width) {
                    continue;
                }
                grid.set(gridZ * width + gridX, column(snapshot, localX, localZ, anchorY, band, minY));
            }
        }
    }

    /**
     * Rebuilds the three layers from the columns already stored.
     *
     * @param grid the grid
     */
    public void layers(ZoneGrid grid) {
        var width = grid.width();
        var cells = width * width;
        var hazard = new boolean[cells];
        var edge = new boolean[cells];
        var obstacle = new boolean[cells];
        ZoneLayers.sources(grid.columns(), width, grid.maxSafeDrop(), hazard, edge, obstacle);
        var scratch = new int[cells];
        ZoneLayers.rebuild(hazard, grid.hazardLayer(), width, scratch);
        ZoneLayers.rebuild(edge, grid.edgeLayer(), width, scratch);
        ZoneLayers.rebuild(obstacle, grid.clearanceLayer(), width, scratch);
    }

    private long column(ChunkSnapshot snapshot, int x, int z, int anchorY, int band, int minY) {
        var top = anchorY + band;
        var bottom = anchorY - band;
        if (bottom < minY) {
            bottom = minY;
        }
        var highest = snapshot.getHighestBlockYAt(x, z);
        if (highest < bottom) {
            return scanner.drop(dropOf(anchorY, minY), true);
        }
        var floor = nearest(snapshot, x, z, anchorY, top < highest ? top : highest, bottom);
        if (floor < bottom) {
            return scanner.drop(dropOf(anchorY, bottom), floor < minY);
        }
        return scanner.floor(floor, airAbove(snapshot, x, z, floor, top), table.flags(snapshot.getBlockType(x, floor, z)));
    }

    private int nearest(ChunkSnapshot snapshot, int x, int z, int anchorY, int top, int bottom) {
        var span = top - bottom;
        if (span < 0) {
            span = 0;
        }
        for (var delta = 0; delta <= span; delta++) {
            var up = anchorY + delta;
            if (up <= top && occupied(snapshot, x, up, z)) {
                return up;
            }
            var down = anchorY - delta;
            if (delta > 0 && down >= bottom && occupied(snapshot, x, down, z)) {
                return down;
            }
        }
        return bottom - 1;
    }

    private boolean occupied(ChunkSnapshot snapshot, int x, int y, int z) {
        if (snapshot.isSectionEmpty(y >> 4)) {
            return false;
        }
        var flags = table.flags(snapshot.getBlockType(x, y, z));
        return (flags & ZoneMaterialFlags.SOLID) != 0 || (flags & ZoneMaterialFlags.LAVA) != 0
                || (flags & ZoneMaterialFlags.WATER) != 0 || (flags & ZoneMaterialFlags.BURNING) != 0;
    }

    private int airAbove(ChunkSnapshot snapshot, int x, int z, int floor, int top) {
        var air = 0;
        for (var y = floor + 1; y <= top && air < ZoneColumn.FREE_SATURATED; y++) {
            if (occupied(snapshot, x, y, z)) {
                return air;
            }
            air++;
        }
        return air;
    }

    private static int dropOf(int from, int to) {
        var drop = from - to;
        if (drop < 1) {
            return 1;
        }
        return drop > 63 ? 63 : drop;
    }
}
