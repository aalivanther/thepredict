package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.ZoneGrid;

import org.bukkit.ChunkSnapshot;

import java.util.List;

/**
 * One immutable scan handed to the zone thread (M24.2). It reads snapshots, writes a new grid and publishes it.
 * The grid field is cleared before publication, so the thread does not keep the arrays. It never blocks the server
 * thread. A scan that fails, with an exception or an error, still publishes an unknown map, so the slot never stays
 * building (SP-17).
 */
final class ZoneBuildJob implements Runnable {
    private final ZoneColumnScan scan;
    private final List<ZoneChunkCopy> copies;
    private final long[] seed;
    private final int originX;
    private final int originZ;
    private final int width;
    private final int anchorY;
    private final int minY;
    private final int band;
    private final int safeDrop;
    private final Handoff handoff;
    private ZoneGrid grid;

    ZoneBuildJob(ZoneColumnScan scan, List<ZoneChunkCopy> copies, long[] seed, int originX, int originZ, int width,
                 int anchorY, int minY, int band, int safeDrop, Handoff handoff) {
        this.scan = scan;
        this.copies = copies;
        this.seed = seed;
        this.originX = originX;
        this.originZ = originZ;
        this.width = width;
        this.anchorY = anchorY;
        this.minY = minY;
        this.band = band;
        this.safeDrop = safeDrop;
        this.handoff = handoff;
    }

    @Override
    public void run() {
        ZoneMap map;
        try {
            map = build();
        } catch (RuntimeException | Error exception) {
            grid = null;
            map = ZoneMap.unknown(originX, originZ, anchorY, safeDrop, width, handoff.epoch, onZoneThread());
        }
        grid = null;
        handoff.publish(map);
    }

    private ZoneMap build() {
        grid = new ZoneGrid(width, originX, originZ, anchorY, safeDrop);
        if (seed != null) {
            var cells = width * width;
            var limit = seed.length < cells ? seed.length : cells;
            for (var index = 0; index < limit; index++) {
                grid.set(index, seed[index]);
            }
        }
        for (var copy : copies) {
            scan.fill(grid, copy.snapshot(), copy.chunkX(), copy.chunkZ(), anchorY, band, minY);
        }
        scan.layers(grid);
        grid.ready(handoff.epoch);
        var published = new ZoneMap(grid, onZoneThread());
        grid = null;
        return published;
    }

    private static boolean onZoneThread() {
        return ZoneWorker.NAME.equals(Thread.currentThread().getName());
    }

    static final class Handoff {
        final int epoch;
        final long tick;
        private ZoneMap map;
        volatile boolean done;

        Handoff(int epoch, long tick) {
            this.epoch = epoch;
            this.tick = tick;
        }

        void publish(ZoneMap published) {
            map = published;
            done = true;
        }

        ZoneMap map() {
            return map;
        }
    }
}

/**
 * One copied chunk. The snapshot is already taken on the server thread.
 *
 * @param chunkX chunk coordinate
 * @param chunkZ chunk coordinate
 * @param snapshot the copy
 */
record ZoneChunkCopy(int chunkX, int chunkZ, ChunkSnapshot snapshot) {
}
