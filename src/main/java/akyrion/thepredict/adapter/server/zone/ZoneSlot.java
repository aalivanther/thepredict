package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.TickTier;
import akyrion.thepredict.domain.ZoneSettings;

import org.bukkit.World;

/**
 * One encounter's zone on the server thread (M24.2). The published map is replaced, never written in place. The
 * anchor is a block centre, not a chunk: the registry anchors on the centroid of the positions noted in a tick and
 * re-anchors with the hysteresis of M24.1 (SP-02). An arena fixes the centre.
 */
final class ZoneSlot {
    /** Chunks the registry remembers while a build is in flight: repeats and unloads. */
    static final int CHUNK_NOTES = 32;

    private final DirtyColumns dirty;
    World world;
    ZoneSettings settings = ZoneSettings.DEFAULT;
    int chunkX;
    int chunkZ;
    int centerX;
    int centerZ;
    int anchorY;
    int minY;
    int radius;
    int span;
    int originX;
    int originZ;
    int width = 1;
    int epoch;
    boolean anchored;
    boolean building;
    long snapshotTick = -1L;
    ZoneBuildJob.Handoff handoff;
    ZoneMap map;
    private int[] chunkXs = new int[0];
    private int[] chunkZs = new int[0];
    int chunkCount;
    int cursor;
    long touched;
    long verified = Long.MIN_VALUE;
    boolean verifying;
    TickTier cadence = TickTier.HOT;
    private int[] refreshAt = new int[0];
    private final int[] repeatX = new int[CHUNK_NOTES];
    private final int[] repeatZ = new int[CHUNK_NOTES];
    int repeatCount;
    private final int[] unloadedX = new int[CHUNK_NOTES];
    private final int[] unloadedZ = new int[CHUNK_NOTES];
    int unloadedCount;
    int arenaRadius;
    long anchorTick = Long.MIN_VALUE;
    int outsideTicks;
    long sumX;
    long sumY;
    long sumZ;
    int noted;
    boolean outsideNoted;
    World notedWorld;

    ZoneSlot(int dirtyCapacity) {
        dirty = new DirtyColumns(dirtyCapacity);
    }

    DirtyColumns dirty() {
        return dirty;
    }

    /**
     * Places the grid around a block centre. Everything that indexed the previous grid is dropped: dirty columns,
     * refresh stamps, repeated and unloaded chunks and a pending verification (SP-03).
     */
    void place(World world, int centerX, int centerZ, int anchorY, int minY, int radius) {
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.chunkX = centerX >> 4;
        this.chunkZ = centerZ >> 4;
        this.anchorY = anchorY;
        this.minY = minY;
        this.radius = radius;
        originX = centerX - radius;
        originZ = centerZ - radius;
        width = radius * 2 + 1;
        span = Math.max(Math.max(chunkX - ((centerX - radius) >> 4), ((centerX + radius) >> 4) - chunkX),
                Math.max(chunkZ - ((centerZ - radius) >> 4), ((centerZ + radius) >> 4) - chunkZ));
        var side = span * 2 + 1;
        chunkXs = new int[side * side];
        chunkZs = new int[side * side];
        chunkCount = fillChunks(span);
        cursor = 0;
        anchored = true;
        dirty.clear();
        refreshAt = new int[0];
        repeatCount = 0;
        unloadedCount = 0;
        verifying = false;
        verified = Long.MIN_VALUE;
    }

    /** Accumulates one position of this tick and whether it left the inner square or the inner band (M24.1). */
    void note(World world, int blockX, int blockY, int blockZ, int innerMargin, int bandBlocks) {
        if (noted == 0) {
            notedWorld = world;
        }
        sumX += blockX;
        sumY += blockY;
        sumZ += blockZ;
        noted++;
        if (!anchored) {
            return;
        }
        var inner = radius - innerMargin;
        var band = bandBlocks - innerMargin;
        if (world != this.world || Math.abs(blockX - centerX) > inner || Math.abs(blockZ - centerZ) > inner
                || Math.abs(blockY - anchorY) > band) {
            outsideNoted = true;
        }
    }

    int centroidX() {
        return (int) Math.floorDiv(sumX, noted);
    }

    int centroidY() {
        return (int) Math.floorDiv(sumY, noted);
    }

    int centroidZ() {
        return (int) Math.floorDiv(sumZ, noted);
    }

    void forgetNotes() {
        sumX = 0L;
        sumY = 0L;
        sumZ = 0L;
        noted = 0;
        outsideNoted = false;
        notedWorld = null;
    }

    boolean needsWork() {
        return anchored && (map == null || cursor < chunkCount || dirty.size() > 0 || repeatCount > 0);
    }

    int indexOf(int x, int z) {
        var gridX = x - originX;
        var gridZ = z - originZ;
        if (gridX < 0 || gridZ < 0 || gridX >= width || gridZ >= width) {
            return -1;
        }
        return gridZ * width + gridX;
    }

    /** Whether a block height can change what the scan reads: the band, plus the drop followed below it. */
    boolean coversHeight(int y, int bandBlocks, int dropBlocks) {
        return y >= anchorY - bandBlocks - dropBlocks && y <= anchorY + bandBlocks + 2;
    }

    boolean coversChunk(int x, int z) {
        var blockX = x << 4;
        var blockZ = z << 4;
        return blockX + 15 >= originX && blockX < originX + width && blockZ + 15 >= originZ && blockZ < originZ + width;
    }

    int chunkXAt(int index) {
        return chunkXs[index];
    }

    int chunkZAt(int index) {
        return chunkZs[index];
    }

    boolean recent(int column, long tick, int gap) {
        if (gap <= 0 || column < 0 || column >= refreshAt.length) {
            return false;
        }
        var at = refreshAt[column];
        return at != 0 && tick - at < gap;
    }

    void noteRefresh(int column, long tick) {
        if (refreshAt.length != width * width) {
            refreshAt = new int[Math.max(1, width * width)];
        }
        if (column >= 0 && column < refreshAt.length) {
            refreshAt[column] = tick > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) tick;
        }
    }

    boolean refreshedAt(int column, long tick) {
        if (column < 0 || column >= refreshAt.length) {
            return false;
        }
        var at = refreshAt[column];
        var stamped = tick > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) tick;
        return at != 0 && at == stamped;
    }

    void repeatChunk(int chunkX, int chunkZ) {
        repeatCount = remember(repeatX, repeatZ, repeatCount, chunkX, chunkZ);
    }

    /** Forgets the first repeated chunks, which a build just copied, and keeps the rest in order. */
    void dropRepeats(int taken) {
        for (var index = taken; index < repeatCount; index++) {
            repeatX[index - taken] = repeatX[index];
            repeatZ[index - taken] = repeatZ[index];
        }
        repeatCount -= taken;
    }

    int repeatX(int index) {
        return repeatX[index];
    }

    int repeatZ(int index) {
        return repeatZ[index];
    }

    /** Remembers a chunk that unloaded while a build was in flight, so the install blanks it (SP-08). */
    void noteUnloaded(int chunkX, int chunkZ) {
        unloadedCount = remember(unloadedX, unloadedZ, unloadedCount, chunkX, chunkZ);
    }

    int unloadedX(int index) {
        return unloadedX[index];
    }

    int unloadedZ(int index) {
        return unloadedZ[index];
    }

    private static int remember(int[] xs, int[] zs, int count, int chunkX, int chunkZ) {
        for (var index = 0; index < count; index++) {
            if (xs[index] == chunkX && zs[index] == chunkZ) {
                return count;
            }
        }
        if (count == xs.length) {
            return count;
        }
        xs[count] = chunkX;
        zs[count] = chunkZ;
        return count + 1;
    }

    private int fillChunks(int span) {
        var written = 0;
        chunkXs[written] = chunkX;
        chunkZs[written] = chunkZ;
        written++;
        for (var ring = 1; ring <= span; ring++) {
            for (var dx = -ring; dx <= ring; dx++) {
                for (var dz = -ring; dz <= ring; dz++) {
                    if (dx != -ring && dx != ring && dz != -ring && dz != ring) {
                        continue;
                    }
                    chunkXs[written] = chunkX + dx;
                    chunkZs[written] = chunkZ + dz;
                    written++;
                }
            }
        }
        return written;
    }
}
