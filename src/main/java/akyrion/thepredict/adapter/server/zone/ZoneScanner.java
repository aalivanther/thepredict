package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.ZoneColumn;
import akyrion.thepredict.domain.ZoneMaterialFlags;

/**
 * Packs one column from material bits (M24.1). Pure. The worker calls it; tests call it without a world.
 */
public final class ZoneScanner {
    /** Creates the scanner. */
    public ZoneScanner() {
    }

    /**
     * A floor column. Lava, deep water and a fence are flags on the packed word.
     *
     * @param floorY floor height
     * @param freeHeight air above the floor, saturated at 15
     * @param material bits from {@link ZoneMaterialTable}
     * @return the packed column
     */
    public long floor(int floorY, int freeHeight, short material) {
        long flags = 0L;
        if ((material & ZoneMaterialFlags.LAVA) != 0) {
            flags |= 1L << ZoneColumn.LAVA;
        }
        if ((material & ZoneMaterialFlags.BURNING) != 0) {
            flags |= 1L << ZoneColumn.BURNING;
        }
        if ((material & ZoneMaterialFlags.DAMAGING) != 0) {
            flags |= 1L << ZoneColumn.DAMAGING;
        }
        if ((material & ZoneMaterialFlags.FREEZING) != 0) {
            flags |= 1L << ZoneColumn.FREEZING;
        }
        if ((material & ZoneMaterialFlags.CAUTIOUS) != 0) {
            flags |= 1L << ZoneColumn.CAUTIOUS;
        }
        if ((material & ZoneMaterialFlags.SPIKES) != 0) {
            flags |= 1L << ZoneColumn.SPIKES;
        }
        if ((material & ZoneMaterialFlags.SLOW) != 0) {
            flags |= 1L << ZoneColumn.SLOW;
        }
        if ((material & ZoneMaterialFlags.WATER) != 0) {
            flags |= 1L << ZoneColumn.WATER;
        }
        if ((material & ZoneMaterialFlags.CLIMBABLE) != 0) {
            flags |= 1L << ZoneColumn.CLIMBABLE;
        }
        if ((material & ZoneMaterialFlags.DOOR) != 0) {
            flags |= 1L << ZoneColumn.DOOR;
        }
        if ((material & ZoneMaterialFlags.SOFT) != 0) {
            flags |= 1L << ZoneColumn.SOFT_LANDING;
        }
        if ((material & ZoneMaterialFlags.FENCE) != 0) {
            flags |= 1L << ZoneColumn.FENCE_LIKE;
        }
        if ((material & ZoneMaterialFlags.WEB) != 0) {
            flags |= 1L << ZoneColumn.SLOW;
        }
        return ZoneColumn.pack(ZoneColumn.FLOOR, floorY, freeHeight, 0, flags);
    }

    /**
     * A pit or the void.
     *
     * @param depth drop in blocks
     * @param voidDrop whether the drop is the void
     * @return the packed column
     */
    public long drop(int depth, boolean voidDrop) {
        return ZoneColumn.pack(voidDrop ? ZoneColumn.VOID : ZoneColumn.PIT, 0, 0, depth, 0L);
    }
}
