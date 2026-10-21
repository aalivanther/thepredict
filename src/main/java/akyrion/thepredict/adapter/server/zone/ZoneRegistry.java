package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.TickTier;
import akyrion.thepredict.domain.ZoneCaps;
import akyrion.thepredict.domain.ZoneGrid;
import akyrion.thepredict.domain.ZoneQueryRunner;
import akyrion.thepredict.domain.ZoneSettings;
import akyrion.thepredict.engine.DecisionMetrics;
import akyrion.thepredict.engine.ZoneControl;
import akyrion.thepredict.engine.ZoneStatus;
import akyrion.thepredict.engine.ZoneStepReport;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The zone port (M24.2). {@link #step} reads the clock before each zone, copies loaded chunks and the zone thread
 * scans them into a {@link ZoneMap} whose width is the anchor radius. A chunk that is not loaded is never requested.
 * The server thread does not scan columns. A stale epoch is counted and not installed.
 *
 * <p>Anchoring follows M24.1 (SP-02): the positions a tick notes through {@link #notePosition} are averaged once in
 * {@link #step}; the first centroid anchors the zone, and the zone re-anchors on the current centroid only after
 * {@link #REANCHOR_AFTER_TICKS} noted ticks with some position outside the inner square or the inner band, at most
 * once every {@link #REANCHOR_GAP_TICKS}. With an arena the centre never moves.
 */
public final class ZoneRegistry implements ZoneControl {
    /** Noted ticks outside the inner square or band before a re-anchor (M24.1). */
    public static final int REANCHOR_AFTER_TICKS = 40;
    /** Least ticks between two re-anchors of one zone (M24.1). */
    public static final int REANCHOR_GAP_TICKS = 100;
    /** Blocks the inner square and the inner band are narrower than the zone (M24.1). */
    public static final int INNER_MARGIN_BLOCKS = 4;

    private final ZoneIndex index = new ZoneIndex();
    private final ZoneWorker worker;
    private final LongSupplier clock;
    private final ZoneCaps caps;
    private final DecisionMetrics metrics;
    private final ZoneSnapshotter snapshotter = new ZoneSnapshotter();
    private final ZoneColumnScan scan;
    private final ZoneColumnReader reader = new ZoneColumnReader();
    private final ZoneQueryRunner queries = new ZoneQueryRunner();
    private final Map<EncounterId, ZoneSlot> zones = new LinkedHashMap<>();
    private int staleBuilds;
    private int dirtyDropped;
    private int columnReads;
    private int factPeriod;
    private long steppedTick;

    /**
     * Creates the registry with the delivered caps.
     *
     * @param worker the zone thread
     * @param clock monotonic clock, read before each zone
     */
    public ZoneRegistry(ZoneWorker worker, LongSupplier clock) {
        this(worker, clock, ZoneCaps.DEFAULT, null);
    }

    /**
     * Creates the registry.
     *
     * @param worker the zone thread
     * @param clock monotonic clock, read before each zone
     * @param caps the global limits
     * @param metrics zone counters, or null in a test that only reads {@link #staleBuilds()}
     */
    public ZoneRegistry(ZoneWorker worker, LongSupplier clock, ZoneCaps caps, DecisionMetrics metrics) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.caps = Objects.requireNonNull(caps, "caps");
        this.metrics = metrics;
        this.scan = new ZoneColumnScan(new ZoneMaterialTable());
        this.factPeriod = caps.factPeriodTicks();
        queries.limit(caps.maxQueriesPerTick());
    }

    /**
     * Ticks between raw fact samples. {@link #step} copies {@link ZoneCaps#factPeriodTicks()}.
     *
     * @return the period
     */
    public int factPeriod() {
        return factPeriod;
    }

    /**
     * Remembers the hottest tier seen since the last step and marks the zone active.
     *
     * @param encounter the encounter
     * @param tier the tier of this visit
     */
    public void noteCadence(EncounterId encounter, TickTier tier) {
        var slot = zones.get(Objects.requireNonNull(encounter, "encounter"));
        if (slot == null) {
            return;
        }
        var next = Objects.requireNonNull(tier, "tier");
        if (next.ordinal() < slot.cadence.ordinal()) {
            slot.cadence = next;
        }
        touch(slot);
    }

    /**
     * The index filled at open.
     *
     * @return the index
     */
    public ZoneIndex index() {
        return index;
    }

    /**
     * Queries served this tick. {@link #step} calls {@link ZoneQueryRunner#nextTick()} and applies the cap.
     *
     * @return the runner
     */
    public ZoneQueryRunner queries() {
        return queries;
    }

    /**
     * The limits this registry was built with.
     *
     * @return the caps
     */
    public ZoneCaps caps() {
        return caps;
    }

    /**
     * Opens the zone of a sampled encounter when it is missing and there is room, so a zone the idle drop closed, or
     * one refused at the cap, comes back while the encounter lasts (SP-09). An arena radius above zero fixes the
     * centre and widens the radius (M24.1); the first arena noted wins.
     *
     * @param encounter the encounter
     * @param settings the zone section of the sampled profile
     * @param arenaRadiusBlocks {@code Arena.Radius} of the profile, or {@code 0} without an arena
     */
    public void ensureOpen(EncounterId encounter, ZoneSettings settings, int arenaRadiusBlocks) {
        var slot = zones.get(Objects.requireNonNull(encounter, "encounter"));
        if (slot == null) {
            open(encounter, List.of(), settings);
            slot = zones.get(encounter);
            if (slot == null) {
                return;
            }
        }
        if (slot.arenaRadius == 0 && arenaRadiusBlocks > 0) {
            slot.arenaRadius = arenaRadiusBlocks;
        }
    }

    /**
     * Notes the position of a member or of its target in this tick. {@link #step} averages the notes into the
     * centroid that anchors or re-anchors the zone (M24.1, SP-02). Nothing is read from the world.
     *
     * @param encounter the encounter
     * @param world the world of the position
     * @param blockX block x
     * @param blockY block y, the feet
     * @param blockZ block z
     */
    public void notePosition(EncounterId encounter, World world, int blockX, int blockY, int blockZ) {
        var slot = zones.get(Objects.requireNonNull(encounter, "encounter"));
        if (slot == null) {
            return;
        }
        slot.note(Objects.requireNonNull(world, "world"), blockX, blockY, blockZ, INNER_MARGIN_BLOCKS,
                caps.bandBlocks());
        touch(slot);
    }

    /**
     * Builds dropped because the epoch changed.
     *
     * @return the count
     */
    public int staleBuilds() {
        return staleBuilds;
    }

    /**
     * Dirty columns dropped because the ring was full.
     *
     * @return the count
     */
    public int dirtyDropped() {
        return dirtyDropped;
    }

    /** Columns still waiting on the encounter. {@code 0} when it has no zone. */
    int dirtyCount(EncounterId encounter) {
        var slot = zones.get(encounter);
        return slot == null ? 0 : slot.dirty().size();
    }

    /** Column index at a dirty cursor. {@code -1} when the encounter has no zone. */
    int dirtyColumn(EncounterId encounter, int cursor) {
        var slot = zones.get(encounter);
        return slot == null ? -1 : slot.dirty().indexAt(cursor);
    }

    /** Ticks the step has counted the zone outside its inner square or band; {@code 0} without a zone. */
    int outsideTicks(EncounterId encounter) {
        var slot = zones.get(encounter);
        return slot == null ? 0 : slot.outsideTicks;
    }

    /**
     * Open zones, never above {@link ZoneCaps#maxZones()}.
     *
     * @return the count
     */
    public int openZones() {
        return zones.size();
    }

    /**
     * Zones whose build is on the zone thread.
     *
     * @return the count
     */
    public int buildingCount() {
        var count = 0;
        for (var slot : zones.values()) {
            if (slot.building) {
                count++;
            }
        }
        return count;
    }

    /**
     * Binds an open encounter to the centre of a chunk. The grid covers the radius, clamped to the cap. The step
     * anchors sampled encounters by itself; this entry is for a caller that knows the chunk.
     *
     * @param encounter the encounter
     * @param world the world that already has the chunk, or not
     * @param chunkX chunk coordinate
     * @param chunkZ chunk coordinate
     * @param anchorY preferred floor
     */
    public void anchor(EncounterId encounter, World world, int chunkX, int chunkZ, int anchorY) {
        var slot = zones.get(Objects.requireNonNull(encounter, "encounter"));
        if (slot == null) {
            return;
        }
        anchorAt(encounter, slot, Objects.requireNonNull(world, "world"), (chunkX << 4) + 8, (chunkZ << 4) + 8,
                anchorY);
    }

    /**
     * The map published for the encounter.
     *
     * @param encounter the encounter
     * @return the map, or empty when none is published
     */
    public Optional<ZoneMap> map(EncounterId encounter) {
        var slot = zones.get(encounter);
        if (slot == null || slot.map == null) {
            return Optional.empty();
        }
        return Optional.of(slot.map);
    }

    /**
     * Marks one column dirty on every zone that covers it, at any height.
     *
     * @param world the world
     * @param x block x
     * @param z block z
     * @param tick event tick
     */
    public void noteBlock(World world, int x, int z, long tick) {
        noteBlock(world, x, Integer.MIN_VALUE, z, tick);
    }

    /**
     * Marks one column dirty on every zone that covers it. A block outside the scanned band and the drop below it
     * cannot change a column and is skipped (M24.3, SP-16). A full ring counts a drop and, while that zone is
     * building, repeats the chunk at install.
     *
     * @param world the world
     * @param x block x
     * @param y block y, or {@link Integer#MIN_VALUE} for any height
     * @param z block z
     * @param tick event tick
     */
    public void noteBlock(World world, int x, int y, int z, long tick) {
        for (var entry : zones.entrySet()) {
            var slot = entry.getValue();
            if (slot.world != world
                    || y != Integer.MIN_VALUE && !slot.coversHeight(y, caps.bandBlocks(), caps.maxDropScanBlocks())) {
                continue;
            }
            var column = slot.indexOf(x, z);
            if (column < 0) {
                continue;
            }
            touch(slot);
            if (slot.dirty().offer(column, tick)) {
                continue;
            }
            noteDirtyDrop();
            if (slot.building) {
                index.beginBuild(entry.getKey());
                slot.repeatChunk(x >> 4, z >> 4);
                index.overflow(entry.getKey());
            }
        }
    }

    /**
     * Asks every zone that covers a loaded chunk to copy it again: at install when a build is in flight, in the
     * next step otherwise. One note per zone, not 256 dirty columns (SP-16). The chunk is not read.
     *
     * @param world the world
     * @param chunkX chunk coordinate
     * @param chunkZ chunk coordinate
     * @param tick event tick
     */
    public void markChunk(World world, int chunkX, int chunkZ, long tick) {
        for (var slot : zones.values()) {
            if (slot.world != world || !slot.anchored || !slot.coversChunk(chunkX, chunkZ)) {
                continue;
            }
            slot.repeatChunk(chunkX, chunkZ);
            touch(slot);
        }
    }

    /**
     * Sets the columns of an unloaded chunk to unknown without reading the world. The published arrays stay sealed;
     * the replacement is a new grid. A build in flight remembers the chunk and blanks it at install (SP-08).
     *
     * @param world the world
     * @param chunkX chunk coordinate
     * @param chunkZ chunk coordinate
     */
    public void unload(World world, int chunkX, int chunkZ) {
        for (var slot : zones.values()) {
            if (slot.world != world || !slot.coversChunk(chunkX, chunkZ)) {
                continue;
            }
            if (slot.map != null) {
                slot.map = clearChunk(slot.map, chunkX, chunkZ);
            }
            if (slot.building) {
                slot.noteUnloaded(chunkX, chunkZ);
            }
        }
    }

    /**
     * One live column read, counted against {@link ZoneCaps#maxColumnReadsPerTick()}. Over the cap the world is not
     * asked.
     *
     * @param world the world
     * @param x block x
     * @param y block y
     * @param z block z
     * @return the type, or empty when the chunk is unloaded or the cap is spent
     */
    public Optional<Material> readColumn(World world, int x, int y, int z) {
        if (columnReads >= caps.maxColumnReadsPerTick()) {
            return Optional.empty();
        }
        columnReads++;
        if (metrics != null) {
            metrics.add(DecisionMetrics.ZoneCounter.ZONE_COLUMNS, 1);
        }
        return reader.type(world, x, y, z);
    }

    @Override
    public void open(EncounterId encounter, List<MobKey> members, ZoneSettings settings) {
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(settings, "settings");
        if (zones.containsKey(encounter)) {
            return;
        }
        if (zones.size() >= caps.maxZones()) {
            return;
        }
        index.open(encounter, 0L);
        var slot = new ZoneSlot(caps.maxDirtyQueue());
        slot.settings = settings;
        zones.put(encounter, slot);
    }

    @Override
    public void close(EncounterId encounter) {
        index.unbind(encounter);
        index.close(encounter);
        zones.remove(encounter);
    }

    @Override
    public ZoneStepReport step(long tick, long budgetNanos) {
        steppedTick = tick;
        factPeriod = caps.factPeriodTicks();
        var origin = clock.getAsLong();
        queries.nextTick();
        queries.limit(caps.maxQueriesPerTick());
        columnReads = 0;
        var published = install();
        settle(tick);
        maintain(tick);
        var deferred = 0;
        var visited = 0;
        var snapshots = caps.maxSnapshotsPerTick();
        var inFlight = buildingCount();
        for (var entry : zones.entrySet()) {
            var now = clock.getAsLong();
            visited++;
            if (budgetNanos <= 0L || now - origin >= budgetNanos) {
                deferred++;
                continue;
            }
            var slot = entry.getValue();
            if (slot.building || !slot.needsWork()) {
                continue;
            }
            if (inFlight >= caps.maxConcurrentBuilds() || snapshots <= 0) {
                deferred++;
                continue;
            }
            var took = queue(entry.getKey(), slot, tick, snapshots);
            if (took > 0) {
                snapshots -= took;
                inFlight++;
            }
        }
        for (var slot : zones.values()) {
            slot.cadence = TickTier.COLD;
        }
        return new ZoneStepReport(visited, published, deferred);
    }

    @Override
    public Optional<ZoneStatus> status(EncounterId encounter) {
        var slot = zones.get(encounter);
        if (slot == null) {
            return Optional.empty();
        }
        var ready = slot.map != null;
        var epoch = ready ? slot.map.epoch() : slot.epoch;
        return Optional.of(new ZoneStatus(ready, epoch, index.snapshotTick(encounter)));
    }

    /**
     * Stops the zone thread. At most one second.
     */
    public void closeWorker() {
        worker.close();
    }

    /** Anchors each zone whose tick noted positions: the first centroid at once, later ones with hysteresis. */
    private void settle(long tick) {
        for (var entry : zones.entrySet()) {
            var slot = entry.getValue();
            if (slot.noted == 0) {
                continue;
            }
            var world = slot.notedWorld;
            if (!slot.anchored || slot.world != world) {
                anchorAt(entry.getKey(), slot, world, slot.centroidX(), slot.centroidZ(), slot.centroidY());
            } else if (slot.arenaRadius == 0 && slot.outsideNoted) {
                slot.outsideTicks++;
                if (slot.outsideTicks >= REANCHOR_AFTER_TICKS && tick - slot.anchorTick >= REANCHOR_GAP_TICKS) {
                    anchorAt(entry.getKey(), slot, world, slot.centroidX(), slot.centroidZ(), slot.centroidY());
                }
            } else {
                slot.outsideTicks = 0;
            }
            slot.forgetNotes();
        }
    }

    private void anchorAt(EncounterId encounter, ZoneSlot slot, World world, int centerX, int centerZ, int anchorY) {
        if (slot.anchored) {
            slot.epoch++;
        } else if (slot.epoch < 1) {
            slot.epoch = 1;
        }
        var requested = slot.arenaRadius > 0 ? caps.arenaRadius(slot.arenaRadius)
                : slot.settings.radius().orElse(caps.defaultRadiusBlocks());
        var radius = requested > caps.maxRadiusBlocks() ? caps.maxRadiusBlocks() : requested;
        slot.place(world, centerX, centerZ, anchorY, world.getMinHeight(), radius);
        slot.anchorTick = steppedTick;
        slot.outsideTicks = 0;
        index.bind(encounter, slot.chunkX, slot.chunkZ, slot.span);
        touch(slot);
    }

    private int install() {
        var published = 0;
        for (var entry : zones.entrySet()) {
            var slot = entry.getValue();
            var handoff = slot.handoff;
            if (handoff == null || !handoff.done) {
                continue;
            }
            if (handoff.epoch != slot.epoch || handoff.map() == null) {
                noteStale();
                slot.building = false;
                slot.handoff = null;
                slot.verifying = false;
                index.abandon(entry.getKey());
                continue;
            }
            if (slot.repeatCount > 0) {
                continueBuild(entry.getKey(), slot, handoff);
                continue;
            }
            var next = blankUnloaded(slot, handoff.map());
            if (slot.verifying) {
                countUnseen(slot, next);
                slot.verifying = false;
            }
            slot.map = next;
            slot.snapshotTick = handoff.tick;
            slot.building = false;
            slot.handoff = null;
            index.published(entry.getKey(), handoff.tick);
            var snapshot = handoff.tick;
            slot.dirty().retain((column, event) -> keepDirty(slot, column, event, snapshot));
            published++;
            if (metrics != null) {
                metrics.add(DecisionMetrics.ZoneCounter.ZONE_BUILDS, 1);
            }
        }
        return published;
    }

    /** The chunks that unloaded while the build ran are unknown again before the map is published (SP-08). */
    private ZoneMap blankUnloaded(ZoneSlot slot, ZoneMap built) {
        var map = built;
        for (var index = 0; index < slot.unloadedCount; index++) {
            map = clearChunk(map, slot.unloadedX(index), slot.unloadedZ(index));
        }
        slot.unloadedCount = 0;
        return map;
    }

    private int queue(EncounterId encounter, ZoneSlot slot, long tick, int budget) {
        var copies = new ArrayList<ZoneChunkCopy>();
        var took = 0;
        if (slot.cursor < slot.chunkCount) {
            while (took < budget && slot.cursor < slot.chunkCount) {
                var cursor = slot.cursor++;
                took++;
                snapshotter.capture(slot.world, slot.chunkXAt(cursor), slot.chunkZAt(cursor))
                        .ifPresent(found -> copies.add(new ZoneChunkCopy(slot.chunkXAt(cursor), slot.chunkZAt(cursor),
                                found)));
            }
        } else if (slot.repeatCount > 0) {
            took = repeatCopies(slot, copies, budget);
        } else if (slot.dirty().size() > 0) {
            took = dirtyCopies(slot, copies, budget, tick);
        }
        if (took == 0 && slot.map != null) {
            return 0;
        }
        var seed = seedFor(slot);
        var handoff = new ZoneBuildJob.Handoff(slot.epoch, tick);
        var job = new ZoneBuildJob(scan, copies, seed, slot.originX, slot.originZ, slot.width, slot.anchorY, slot.minY,
                caps.bandBlocks(), caps.maxSafeDropBlocks(), handoff);
        slot.handoff = handoff;
        slot.building = true;
        slot.unloadedCount = 0;
        if (worker.offer(job)) {
            index.beginBuild(encounter);
            return took == 0 ? 1 : took;
        }
        slot.building = false;
        slot.handoff = null;
        index.abandon(encounter);
        slot.cursor = Math.max(0, slot.cursor - took);
        return 0;
    }

    /** Copies the chunks a load asked to repeat while no build was in flight; the rest wait for the next step. */
    private int repeatCopies(ZoneSlot slot, List<ZoneChunkCopy> copies, int budget) {
        var took = Math.min(budget, slot.repeatCount);
        for (var cursor = 0; cursor < took; cursor++) {
            var chunkX = slot.repeatX(cursor);
            var chunkZ = slot.repeatZ(cursor);
            snapshotter.capture(slot.world, chunkX, chunkZ)
                    .ifPresent(found -> copies.add(new ZoneChunkCopy(chunkX, chunkZ, found)));
        }
        slot.dropRepeats(took);
        return took;
    }

    private int dirtyCopies(ZoneSlot slot, List<ZoneChunkCopy> copies, int budget, long tick) {
        var dirty = slot.dirty();
        var gap = caps.minColumnRefreshTicks();
        var took = 0;
        for (var cursor = 0; cursor < dirty.size() && took < budget; cursor++) {
            if (!index.replay(dirty.tickAt(cursor), slot.snapshotTick)) {
                continue;
            }
            var column = dirty.indexAt(cursor);
            if (slot.recent(column, tick, gap)) {
                continue;
            }
            var worldX = slot.originX + column % slot.width;
            var worldZ = slot.originZ + column / slot.width;
            var chunkX = worldX >> 4;
            var chunkZ = worldZ >> 4;
            if (held(copies, chunkX, chunkZ)) {
                slot.noteRefresh(column, tick);
                continue;
            }
            var captured = snapshotter.capture(slot.world, chunkX, chunkZ);
            took++;
            captured.ifPresent(found -> copies.add(new ZoneChunkCopy(chunkX, chunkZ, found)));
            slot.noteRefresh(column, tick);
        }
        return took;
    }

    private static boolean held(List<ZoneChunkCopy> copies, int chunkX, int chunkZ) {
        for (var copy : copies) {
            if (copy.chunkX() == chunkX && copy.chunkZ() == chunkZ) {
                return true;
            }
        }
        return false;
    }

    /**
     * The seed of a build: the published map when it lies on the same origin, otherwise its columns translated by
     * world coordinates onto the new grid, with the rest unknown (SP-03). A translated seed never shows the terrain
     * of another column.
     */
    private static long[] seedFor(ZoneSlot slot) {
        var map = slot.map;
        if (map == null) {
            return null;
        }
        if (map.originX() == slot.originX && map.originZ() == slot.originZ && map.width() == slot.width) {
            return seedFrom(map);
        }
        var seed = new long[slot.width * slot.width];
        for (var z = 0; z < slot.width; z++) {
            var oldZ = slot.originZ + z - map.originZ();
            if (oldZ < 0 || oldZ >= map.width()) {
                continue;
            }
            for (var x = 0; x < slot.width; x++) {
                var oldX = slot.originX + x - map.originX();
                if (oldX >= 0 && oldX < map.width()) {
                    seed[z * slot.width + x] = map.column(oldZ * map.width() + oldX);
                }
            }
        }
        return seed;
    }

    private static long[] seedFrom(ZoneMap map) {
        if (map == null) {
            return null;
        }
        var cells = map.width() * map.width();
        var seed = new long[cells];
        for (var index = 0; index < cells; index++) {
            seed[index] = map.column(index);
        }
        return seed;
    }

    private void maintain(long tick) {
        var idle = new ArrayList<EncounterId>();
        for (var entry : zones.entrySet()) {
            var slot = entry.getValue();
            if (slot.touched > 0L && tick - slot.touched >= caps.idleDropTicks() && slot.anchored && !slot.building) {
                idle.add(entry.getKey());
                continue;
            }
            scheduleVerify(slot, tick);
            blankDirty(slot);
        }
        for (var id : idle) {
            close(id);
        }
    }

    private void scheduleVerify(ZoneSlot slot, long tick) {
        if (slot.building || slot.map == null || slot.verifying) {
            return;
        }
        var interval = switch (slot.cadence) {
            case HOT -> caps.verifyIntervalTicks();
            case WARM -> caps.verifyIntervalTicks() * 2;
            case COLD -> 0;
        };
        if (interval <= 0) {
            return;
        }
        if (slot.verified == Long.MIN_VALUE) {
            slot.verified = tick;
            return;
        }
        if (tick - slot.verified < interval) {
            return;
        }
        slot.verifying = true;
        slot.verified = tick;
        slot.cursor = 0;
    }

    private void blankDirty(ZoneSlot slot) {
        if (slot.building || slot.map == null) {
            return;
        }
        var dirty = slot.dirty();
        if (dirty.size() == 0 || dirty.size() <= caps.dirtyRebuildThreshold() && !dirty.overflowed()) {
            return;
        }
        var grid = ZoneGrid.copyOf(slot.map);
        var cells = slot.width * slot.width;
        for (var cursor = 0; cursor < dirty.size(); cursor++) {
            var column = dirty.indexAt(cursor);
            if (column >= 0 && column < cells) {
                grid.set(column, 0L);
            }
        }
        scan.layers(grid);
        grid.ready(slot.map.epoch());
        slot.map = new ZoneMap(grid, false);
    }

    private boolean continueBuild(EncounterId encounter, ZoneSlot slot, ZoneBuildJob.Handoff done) {
        var copies = new ArrayList<ZoneChunkCopy>();
        var count = slot.repeatCount;
        for (var cursor = 0; cursor < count; cursor++) {
            var chunkX = slot.repeatX(cursor);
            var chunkZ = slot.repeatZ(cursor);
            snapshotter.capture(slot.world, chunkX, chunkZ)
                    .ifPresent(found -> copies.add(new ZoneChunkCopy(chunkX, chunkZ, found)));
        }
        if (copies.isEmpty()) {
            return false;
        }
        var handoff = new ZoneBuildJob.Handoff(slot.epoch, done.tick);
        var job = new ZoneBuildJob(scan, copies, seedFrom(done.map()), slot.originX, slot.originZ, slot.width,
                slot.anchorY, slot.minY, caps.bandBlocks(), caps.maxSafeDropBlocks(), handoff);
        if (!worker.offer(job)) {
            return false;
        }
        slot.repeatCount = 0;
        slot.handoff = handoff;
        slot.building = true;
        index.beginBuild(encounter);
        return true;
    }

    private boolean keepDirty(ZoneSlot slot, int column, long event, long snapshot) {
        if (index.replay(event, snapshot)) {
            return true;
        }
        return slot.recent(column, snapshot, caps.minColumnRefreshTicks()) && !slot.refreshedAt(column, snapshot);
    }

    /** Columns that changed without a dirty mark, compared only between maps on the same origin. */
    private void countUnseen(ZoneSlot slot, ZoneMap next) {
        var previous = slot.map;
        if (previous == null || next == null || metrics == null || previous.originX() != next.originX()
                || previous.originZ() != next.originZ()) {
            return;
        }
        var cells = previous.width() * previous.width();
        if (next.width() * next.width() < cells) {
            cells = next.width() * next.width();
        }
        var dirty = slot.dirty();
        var unseen = 0L;
        for (var column = 0; column < cells; column++) {
            if (previous.column(column) == next.column(column) || listed(dirty, column)) {
                continue;
            }
            unseen++;
        }
        if (unseen > 0L) {
            metrics.add(DecisionMetrics.ZoneCounter.ZONE_UNSEEN_CHANGES, unseen);
        }
    }

    private static boolean listed(DirtyColumns dirty, int column) {
        for (var cursor = 0; cursor < dirty.size(); cursor++) {
            if (dirty.indexAt(cursor) == column) {
                return true;
            }
        }
        return false;
    }

    private void touch(ZoneSlot slot) {
        if (steppedTick > 0L) {
            slot.touched = steppedTick;
        }
    }

    private ZoneMap clearChunk(ZoneMap source, int chunkX, int chunkZ) {
        var grid = ZoneGrid.copyOf(source);
        var width = grid.width();
        var baseX = chunkX << 4;
        var baseZ = chunkZ << 4;
        for (var localZ = 0; localZ < 16; localZ++) {
            var gridZ = baseZ + localZ - grid.originZ();
            if (gridZ < 0 || gridZ >= width) {
                continue;
            }
            for (var localX = 0; localX < 16; localX++) {
                var gridX = baseX + localX - grid.originX();
                if (gridX < 0 || gridX >= width) {
                    continue;
                }
                grid.set(gridZ * width + gridX, 0L);
            }
        }
        scan.layers(grid);
        grid.ready(source.epoch());
        return new ZoneMap(grid, false);
    }

    private void noteStale() {
        staleBuilds++;
        if (metrics != null) {
            metrics.add(DecisionMetrics.ZoneCounter.ZONE_BUILD_STALE, 1);
        }
    }

    private void noteDirtyDrop() {
        dirtyDropped++;
        if (metrics != null) {
            metrics.add(DecisionMetrics.ZoneCounter.ZONE_DIRTY_DROPPED, 1);
        }
    }
}
