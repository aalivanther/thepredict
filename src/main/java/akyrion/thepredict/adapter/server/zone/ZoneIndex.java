package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.EncounterId;

import java.util.HashMap;
import java.util.Map;

/**
 * Encounter registration at {@code open}, before any member is installed (M24.2). An event is replayed only when its
 * tick is at least the snapshot tick. A build that overflows while {@code BUILDING} keeps the same epoch.
 */
public final class ZoneIndex {
    private final Map<EncounterId, Slot> slots = new HashMap<>();

    /** Creates an empty index. */
    public ZoneIndex() {
    }

    /**
     * Registers the encounter on open.
     *
     * @param encounter the encounter
     * @param tick the open tick
     */
    public void open(EncounterId encounter, long tick) {
        slots.put(encounter, new Slot(tick, 1, false));
    }

    /**
     * Drops the encounter.
     *
     * @param encounter the encounter
     */
    public void close(EncounterId encounter) {
        slots.remove(encounter);
    }

    /**
     * Binds the encounter to the chunk span of its anchor.
     *
     * @param encounter the encounter
     * @param chunkX anchor chunk
     * @param chunkZ anchor chunk
     * @param radiusChunks chunks from the anchor, inclusive
     */
    public void bind(EncounterId encounter, int chunkX, int chunkZ, int radiusChunks) {
        var slot = slots.get(encounter);
        if (slot == null) {
            return;
        }
        slot.chunkX = chunkX;
        slot.chunkZ = chunkZ;
        slot.radiusChunks = radiusChunks;
        slot.bound = true;
    }

    /**
     * Drops the chunk binding. The encounter stays open until {@link #close}.
     *
     * @param encounter the encounter
     */
    public void unbind(EncounterId encounter) {
        var slot = slots.get(encounter);
        if (slot == null) {
            return;
        }
        slot.bound = false;
    }

    /**
     * Whether the encounter was registered at open.
     *
     * @param encounter the encounter
     * @return {@code true} when {@link #open} ran
     */
    public boolean contains(EncounterId encounter) {
        return slots.containsKey(encounter);
    }

    /**
     * Whether an event from {@code eventTick} is applied to a snapshot taken at {@code snapshotTick}.
     *
     * @param eventTick the event
     * @param snapshotTick the snapshot
     * @return {@code true} when the event is not older than the snapshot
     */
    public boolean replay(long eventTick, long snapshotTick) {
        return eventTick >= snapshotTick;
    }

    /**
     * Marks a build in progress without advancing the epoch.
     *
     * @param encounter the encounter
     */
    public void beginBuild(EncounterId encounter) {
        var slot = slots.get(encounter);
        if (slot == null || slot.building) {
            return;
        }
        slot.building = true;
    }

    /**
     * Drops the building flag without advancing the epoch. A stale handoff uses this.
     *
     * @param encounter the encounter
     */
    public void abandon(EncounterId encounter) {
        var slot = slots.get(encounter);
        if (slot == null) {
            return;
        }
        slot.building = false;
    }

    /**
     * Notes a build overflow. The epoch does not advance while the slot is still building.
     *
     * @param encounter the encounter
     * @return the epoch, unchanged when the slot is building
     */
    public int overflow(EncounterId encounter) {
        var slot = slots.get(encounter);
        if (slot == null) {
            return 0;
        }
        if (!slot.building) {
            slot.epoch++;
            slot.building = true;
        }
        return slot.epoch;
    }

    /**
     * Marks the build published and advances the epoch for the next one.
     *
     * @param encounter the encounter
     * @param snapshotTick tick of the snapshot
     */
    public void published(EncounterId encounter, long snapshotTick) {
        var slot = slots.get(encounter);
        if (slot == null) {
            return;
        }
        slot.building = false;
        slot.snapshotTick = snapshotTick;
        slot.epoch++;
    }

    /**
     * The snapshot tick stored for the encounter.
     *
     * @param encounter the encounter
     * @return the tick, or {@code -1}
     */
    public long snapshotTick(EncounterId encounter) {
        var slot = slots.get(encounter);
        return slot == null ? -1L : slot.snapshotTick;
    }

    private static final class Slot {
        private long snapshotTick;
        private int epoch;
        private boolean building;
        private int chunkX;
        private int chunkZ;
        private int radiusChunks;
        private boolean bound;

        private Slot(long snapshotTick, int epoch, boolean building) {
            this.snapshotTick = snapshotTick;
            this.epoch = epoch;
            this.building = building;
        }
    }
}
