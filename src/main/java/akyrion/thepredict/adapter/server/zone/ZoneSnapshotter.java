package akyrion.thepredict.adapter.server.zone;

import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

import java.util.Optional;

/**
 * The only place that asks a world for a chunk (rule C19, M24.2). A chunk that is not already loaded is skipped.
 * The snapshot is taken on the same tick, immediately after that check. This type never generates a chunk.
 */
public final class ZoneSnapshotter {
    /** Creates the snapshotter. */
    public ZoneSnapshotter() {
    }

    /**
     * Copies one loaded chunk. Biomes, light and fluids are not copied.
     *
     * @param world the world
     * @param chunkX chunk coordinate
     * @param chunkZ chunk coordinate
     * @return the snapshot, or empty when the chunk is not loaded
     */
    public Optional<ChunkSnapshot> capture(World world, int chunkX, int chunkZ) {
        if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
            return Optional.empty();
        }
        return Optional.of(world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(true, false, false, false));
    }
}
