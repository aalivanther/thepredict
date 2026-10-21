package akyrion.thepredict.adapter.server.zone;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.Optional;

/**
 * The live column read on the server thread (rule C19, M24.3). It uses {@link World#getType(int, int, int)} and only
 * after {@link World#isChunkLoaded(int, int)} on the same tick. It never loads a chunk.
 */
public final class ZoneColumnReader {
    /** Creates the reader. */
    public ZoneColumnReader() {
    }

    /**
     * The block type of one loaded column.
     *
     * @param world the world
     * @param x block x
     * @param y block y
     * @param z block z
     * @return the type, or empty when the chunk is not loaded
     */
    public Optional<Material> type(World world, int x, int y, int z) {
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return Optional.empty();
        }
        return Optional.ofNullable(world.getType(x, y, z));
    }
}
