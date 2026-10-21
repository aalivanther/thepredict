package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.ZoneMaterialFlags;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Material bits for one block (M24.1). The array is filled once, on the thread that constructs the table, which is
 * the server thread at startup. {@code ThePredict-Zone} only indexes that array. Campfires and sweet berries are
 * marked even when their lit state is unknown, because a snapshot does not carry block data (rule C19). Waterlogged
 * blocks keep their own material.
 */
public final class ZoneMaterialTable {
    private final short[] bits;

    /** Builds the table from the block registry. Called once, before the zone thread runs. */
    public ZoneMaterialTable() {
        var materials = Material.values();
        bits = new short[materials.length];
        var live = Bukkit.getServer() != null;
        for (var material : materials) {
            bits[material.ordinal()] = live ? classify(material) : known(material);
        }
    }

    private static short known(Material material) {
        if (material == Material.LAVA || material == Material.LAVA_CAULDRON) {
            return ZoneMaterialFlags.LAVA;
        }
        return 0;
    }

    /**
     * The bits of one material. This does not ask the block registry.
     *
     * @param material the block
     * @return the packed flags; air and an unknown ordinal are zero
     */
    public short flags(Material material) {
        if (material == null) {
            return 0;
        }
        var ordinal = material.ordinal();
        if (ordinal < 0 || ordinal >= bits.length) {
            return 0;
        }
        return bits[ordinal];
    }

    private static short classify(Material material) {
        if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR
                || material.isAir()) {
            return 0;
        }
        if (material == Material.LAVA) {
            return ZoneMaterialFlags.LAVA;
        }
        var packed = 0;
        if (material.isSolid()) {
            packed |= ZoneMaterialFlags.SOLID;
        }
        if (material.isOccluding()) {
            packed |= ZoneMaterialFlags.OCCLUDING;
        }
        if (material.isCollidable()) {
            packed |= ZoneMaterialFlags.COLLIDABLE;
        }
        if (material == Material.LAVA_CAULDRON) {
            packed |= ZoneMaterialFlags.LAVA;
        }
        if (material == Material.WATER || material == Material.BUBBLE_COLUMN || material == Material.KELP
                || material == Material.KELP_PLANT || material == Material.SEAGRASS
                || material == Material.TALL_SEAGRASS) {
            packed |= ZoneMaterialFlags.WATER;
        }
        if (Tag.FIRE.isTagged(material) || Tag.CAMPFIRES.isTagged(material) || material == Material.MAGMA_BLOCK) {
            packed |= ZoneMaterialFlags.BURNING;
        }
        if (material == Material.CACTUS || material == Material.SWEET_BERRY_BUSH || material == Material.WITHER_ROSE) {
            packed |= ZoneMaterialFlags.DAMAGING;
        }
        if (material == Material.POWDER_SNOW) {
            packed |= ZoneMaterialFlags.FREEZING;
        }
        if (material == Material.WITHER_ROSE) {
            packed |= ZoneMaterialFlags.CAUTIOUS;
        }
        if (Tag.SPELEOTHEMS.isTagged(material)) {
            packed |= ZoneMaterialFlags.SPIKES;
        }
        if (material == Material.SOUL_SAND || material == Material.SLIME_BLOCK || material == Material.HONEY_BLOCK) {
            packed |= ZoneMaterialFlags.SLOW;
        }
        if (Tag.CLIMBABLE.isTagged(material)) {
            packed |= ZoneMaterialFlags.CLIMBABLE;
        }
        if (Tag.DOORS.isTagged(material) || Tag.FENCE_GATES.isTagged(material) || Tag.TRAPDOORS.isTagged(material)) {
            packed |= ZoneMaterialFlags.DOOR;
        }
        if (material == Material.WATER || material == Material.SLIME_BLOCK || material == Material.HAY_BLOCK) {
            packed |= ZoneMaterialFlags.SOFT;
        }
        if (Tag.FENCES.isTagged(material) || Tag.WALLS.isTagged(material)) {
            packed |= ZoneMaterialFlags.FENCE;
        }
        if (material == Material.COBWEB) {
            packed |= ZoneMaterialFlags.WEB;
        }
        return (short) packed;
    }
}
