package akyrion.thepredict.adapter.server.zone;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * Marks columns dirty from block events on the server thread (M24.3). Physics events are not observed. Each zone
 * keeps its own queue and skips a block outside its band. A chunk unload clears columns without reading them; a
 * chunk load asks for one copy of that chunk.
 */
public final class ZoneBlockWatch implements Listener {
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final ZoneRegistry zones;
    private final LongSupplier tick;

    /**
     * Creates the watch.
     *
     * @param zones the registry whose queues receive the columns
     * @param tick current server tick
     */
    public ZoneBlockWatch(ZoneRegistry zones, LongSupplier tick) {
        this.zones = zones;
        this.tick = tick;
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a player break */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a placement */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a multi-block placement */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        for (var state : event.getReplacedBlockStates()) {
            note(state.getBlock());
        }
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a burn */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a fade */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a form */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event an entity forming a block */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityForm(EntityBlockFormEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a spread */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event an ignition */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a fluid moving from one block to another */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event) {
        note(event.getBlock());
        note(event.getToBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a fluid level change */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFluid(FluidLevelChangeEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a piston extending */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        note(event.getBlock());
        noteAll(event.getBlocks());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a piston retracting */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        note(event.getBlock());
        noteAll(event.getBlocks());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a block explosion */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        note(event.getBlock());
        noteAll(event.blockList());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event an entity explosion */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        noteAll(event.blockList());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event an entity changing a block */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntity(EntityChangeBlockEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a block breaking other blocks */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakBlock(BlockBreakBlockEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a destroy that is not a player break */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event leaves decaying */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeaves(LeavesDecayEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a sponge absorbing */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent event) {
        note(event.getBlock());
        for (var state : event.getBlocks()) {
            note(state.getBlock());
        }
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a grow */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a structure growing */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructure(StructureGrowEvent event) {
        for (var state : event.getBlocks()) {
            note(state.getBlock());
        }
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a cauldron level change */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCauldron(CauldronLevelChangeEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a chunk becoming loaded */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        var chunk = event.getChunk();
        zones.markChunk(chunk.getWorld(), chunk.getX(), chunk.getZ(), tick.getAsLong());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a chunk unloading; columns become unknown without a read */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        var chunk = event.getChunk();
        zones.unload(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    private void noteAll(List<Block> blocks) {
        for (var block : blocks) {
            note(block);
        }
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a bucket emptied; lava or water placed this way raises no place event (SP-14) */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a bucket filled; the fluid is gone without a break event (SP-14) */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        note(event.getBlock());
    }

    /**
     * Marks the changed columns dirty.
     *
     * @param event a dispenser firing; the block it faces may receive a fluid. Its facing is block data, which this
     *              listener never reads (C19), so the column of the dispenser and its four horizontal neighbours are
     *              marked; a vertical dispenser shares its column (SP-14) */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        var block = event.getBlock();
        note(block);
        for (var face : SIDES) {
            note(block.getRelative(face));
        }
    }

    private void note(Block block) {
        zones.noteBlock(block.getWorld(), block.getX(), block.getY(), block.getZ(), tick.getAsLong());
    }
}
