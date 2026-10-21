package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.HazardPush;
import akyrion.thepredict.domain.TickTier;
import akyrion.thepredict.domain.ZoneCaps;
import akyrion.thepredict.domain.ZoneColumn;
import akyrion.thepredict.domain.ZoneSettings;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneRegistryTest {
    @Test
    void anUnloadedChunkIsNotRequestedAndPublishesNoFloor() {
        var calls = new ArrayList<String>();
        var registry = registry();
        try {
            var id = new EncounterId(1);
            registry.open(id, List.of(), ZoneSettings.DEFAULT);
            registry.anchor(id, world(false, calls), 0, 0, 64);
            var map = published(registry, id);
            assertEquals("getMinHeight", calls.get(0));
            assertFalse(calls.contains("getChunkAt"));
            assertFalse(calls.contains("getChunkAtAsync"));
            assertTrue(calls.stream().skip(1).allMatch(name -> name.equals("isChunkLoaded")));
            assertEquals(0, map.floorColumns());
            assertTrue(map.scannedOnZoneThread());
            assertEquals(ZoneColumn.UNKNOWN, ZoneColumn.state(map.column(0)));
            assertTrue(map.width() > 16);
        } finally {
            registry.closeWorker();
        }
    }

    @Test
    void aLoadedChunkPublishesFloorColumnsFromTheSnapshot() {
        var calls = new ArrayList<String>();
        var registry = registry();
        try {
            var id = new EncounterId(2);
            registry.open(id, List.of(), ZoneSettings.DEFAULT);
            registry.anchor(id, world(true, calls), 0, 0, 64);
            var map = published(registry, id);
            assertEquals("getMinHeight", calls.get(0));
            assertFalse(calls.contains("getChunkAtAsync"));
            assertTrue(calls.contains("getChunkSnapshot"));
            for (var index = 0; index < calls.size(); index++) {
                if (!calls.get(index).equals("getChunkAt")) {
                    continue;
                }
                assertEquals("isChunkLoaded", calls.get(index - 1));
                assertEquals("getChunkSnapshot", calls.get(index + 1));
            }
            var radius = akyrion.thepredict.domain.ZoneCaps.DEFAULT.defaultRadiusBlocks();
            var origin = 8 - radius;
            var width = radius * 2 + 1;
            var cell = (8 - origin) * width + (8 - origin);
            assertEquals(width, map.width());
            assertTrue(map.floorColumns() > 0);
            assertTrue(map.scannedOnZoneThread());
            assertEquals(ZoneColumn.FLOOR, ZoneColumn.state(map.column(cell)));
            assertTrue(ZoneColumn.flag(map.column(cell), ZoneColumn.LAVA));
            assertTrue(map.hazardSource(cell));
            assertTrue(map.publishedGrid().sealed());
            var grid = map.publishedGrid();
            var refused = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> grid.set(cell, 0L));
            assertEquals("published zone grid", refused.getMessage());
            assertEquals("published zone grid", org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class, grid::columns).getMessage());
            assertTrue(registry.status(id).orElseThrow().ready());
        } finally {
            registry.closeWorker();
        }
    }

    @Test
    void eachDeliveredCapStopsTheExtraWork() throws Exception {
        var limited = new ZoneRegistry(new ZoneWorker(1), () -> 0L, caps(1, 4, 16, 256), null);
        try {
            limited.open(new EncounterId(3), List.of(), ZoneSettings.DEFAULT);
            limited.open(new EncounterId(4), List.of(), ZoneSettings.DEFAULT);
            assertEquals(1, limited.openZones());
        } finally {
            limited.closeWorker();
        }
        var reads = new int[] {0};
        var clocked = new ZoneRegistry(new ZoneWorker(1), () -> {
            reads[0]++;
            return 0L;
        });
        try {
            clocked.open(new EncounterId(5), List.of(), ZoneSettings.DEFAULT);
            clocked.open(new EncounterId(6), List.of(), ZoneSettings.DEFAULT);
            clocked.step(1L, 1_000_000L);
            assertTrue(reads[0] >= 3);
        } finally {
            clocked.closeWorker();
        }
        var calls = new ArrayList<String>();
        var snapshots = new ZoneRegistry(new ZoneWorker(1), () -> 0L, caps(4, 1, 16, 256), null);
        try {
            var id = new EncounterId(7);
            snapshots.open(id, List.of(), ZoneSettings.DEFAULT);
            snapshots.anchor(id, world(true, calls), 0, 0, 64);
            snapshots.step(1L, 1_000_000_000L);
            assertEquals(1, calls.stream().filter(name -> name.equals("getChunkAt")).count());
        } finally {
            snapshots.closeWorker();
        }
        var columnCalls = new ArrayList<String>();
        var columns = new ZoneRegistry(new ZoneWorker(1), () -> 0L, caps(4, 4, 0, 256), null);
        try {
            assertTrue(columns.readColumn(world(true, columnCalls), 0, 64, 0).isEmpty());
            assertTrue(columnCalls.isEmpty());
        } finally {
            columns.closeWorker();
        }
        var dirtyCalls = new ArrayList<String>();
        var dirty = new ZoneRegistry(new ZoneWorker(1), () -> 0L, caps(4, 4, 16, 16), null);
        try {
            var id = new EncounterId(8);
            var ground = world(false, dirtyCalls);
            dirty.open(id, List.of(), ZoneSettings.DEFAULT);
            dirty.anchor(id, ground, 0, 0, 64);
            for (var index = 0; index < 20; index++) {
                dirty.noteBlock(ground, -16 + index, -16, 1L);
            }
            assertTrue(dirty.dirtyDropped() > 0);
        } finally {
            dirty.closeWorker();
        }
        overflowWhileBuilding();
        var wideCalls = new ArrayList<String>();
        var wide = registry();
        try {
            var id = new EncounterId(10);
            var settings = new ZoneSettings(java.util.Optional.empty(), java.util.OptionalInt.of(64), true,
                    akyrion.thepredict.domain.HazardPush.OFF);
            wide.open(id, List.of(), settings);
            wide.anchor(id, world(false, wideCalls), 0, 0, 64);
            assertEquals(97, published(wide, id).width());
        } finally {
            wide.closeWorker();
        }
    }

    @Test
    void theStepReadsTheFactPeriodAndDropsAnIdleAnchor() {
        var calls = new ArrayList<String>();
        var registry = registry();
        try {
            var id = new EncounterId(11);
            registry.open(id, List.of(), ZoneSettings.DEFAULT);
            registry.anchor(id, world(false, calls), 0, 0, 64);
            registry.step(10L, 0L);
            assertEquals(ZoneCaps.DEFAULT.factPeriodTicks(), registry.factPeriod());
            registry.noteCadence(id, TickTier.HOT);
            assertEquals(1, registry.openZones());
            registry.step(10L + ZoneCaps.DEFAULT.idleDropTicks(), 0L);
            assertEquals(0, registry.openZones());
            assertFalse(calls.contains("getChunkAt"));
        } finally {
            registry.closeWorker();
        }
    }

    @Test
    void aFailedContinueOfferDoesNotPublishTheEpochThatForgotTheRepeatedChunks() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var parked = new CountDownLatch(1);
        var releasePark = new CountDownLatch(1);
        var filled = new CountDownLatch(1);
        var generation = new AtomicInteger();
        var blocked = new AtomicBoolean();
        var calls = new ArrayList<String>();
        var worker = new ZoneWorker(1);
        var registry = new ZoneRegistry(worker, () -> 0L, caps(4, 4, 16, 16), null);
        worker.parkAfterNext(() -> {
            parked.countDown();
            try {
                releasePark.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            var id = new EncounterId(30);
            var ground = stagedWorld(calls, generation, blocked, started, release);
            registry.open(id, List.of(), radius16());
            registry.anchor(id, ground, 0, 0, 64);
            registry.step(1L, 1_000_000_000L);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTrue(worker.offer(() -> filled.countDown()));
            for (var index = 0; index < 20; index++) {
                registry.noteBlock(ground, -8 + index, -8, 2L);
            }
            generation.set(1);
            release.countDown();
            assertTrue(parked.await(2, TimeUnit.SECONDS));
            registry.step(3L, 1_000_000_000L);
            assertTrue(registry.map(id).isEmpty());
            assertEquals(1, registry.buildingCount());
            releasePark.countDown();
            assertTrue(filled.await(2, TimeUnit.SECONDS));
            var map = mapAt(registry, id, 4L);
            assertTrue(ZoneColumn.flag(map.column(16), ZoneColumn.LAVA));
            assertFalse(ZoneColumn.flag(map.column(28), ZoneColumn.LAVA));
        } finally {
            release.countDown();
            releasePark.countDown();
            registry.closeWorker();
        }
    }

    @Test
    void twentyFlowsInsideTheRefreshGapReadOnceAndKeepTheMark() {
        var calls = new ArrayList<String>();
        var registry = registry();
        try {
            var id = new EncounterId(31);
            var ground = world(true, calls);
            registry.open(id, List.of(), radius16());
            registry.anchor(id, ground, 0, 0, 64);
            publishAt(registry, id, 1_000L);
            var before = calls.stream().filter(name -> name.equals("getChunkAt")).count();
            for (var tick = 1_010; tick <= 1_013; tick++) {
                for (var copy = 0; copy < 5; copy++) {
                    registry.noteBlock(ground, -8, -8, tick);
                }
                registry.step(tick, 1_000_000_000L);
            }
            settle(registry, id, 1_013L);
            var extra = calls.stream().filter(name -> name.equals("getChunkAt")).count() - before;
            assertEquals(1L, extra);
            assertTrue(registry.dirtyCount(id) >= 1);
        } finally {
            registry.closeWorker();
        }
    }

    @Test
    void aRecentColumnSurvivesInstallOfAnotherChunk() {
        var calls = new ArrayList<String>();
        var registry = registry();
        try {
            var id = new EncounterId(32);
            var ground = world(true, calls);
            registry.open(id, List.of(), radius16());
            registry.anchor(id, ground, 0, 0, 64);
            publishAt(registry, id, 1_000L);
            assertEquals(33, registry.map(id).orElseThrow().width());
            registry.noteBlock(ground, -8, -8, 1_050L);
            settle(registry, id, 1_050L);
            assertTrue(dirtyHas(registry, id, 0));
            registry.noteBlock(ground, -8, -8, 1_051L);
            registry.noteBlock(ground, 20, -8, 1_053L);
            settle(registry, id, 1_053L);
            assertTrue(dirtyHas(registry, id, 0));
        } finally {
            registry.closeWorker();
        }
    }

    /** SP-02: two members in neighbouring chunks anchor once, on their centroid, and never bump the epoch. */
    @Test
    void twoMembersInNeighbouringChunksAnchorOnceOnTheCentroid() {
        var calls = new ArrayList<String>();
        var registry = registry();
        try {
            var id = new EncounterId(50);
            var ground = world(true, calls);
            registry.open(id, List.of(), ZoneSettings.DEFAULT);
            for (var tick = 1L; tick <= 10L; tick++) {
                registry.ensureOpen(id, ZoneSettings.DEFAULT, 0);
                registry.notePosition(id, ground, 4, 64, 8);
                registry.notePosition(id, ground, 20, 64, 8);
                registry.step(tick, 1_000_000_000L);
            }
            assertEquals(1, registry.status(id).orElseThrow().epoch());
            assertEquals(0, registry.staleBuilds());
            var map = mapAt(registry, id, 11L);
            assertEquals(12 - ZoneCaps.DEFAULT.defaultRadiusBlocks(), map.originX());
            assertEquals(8 - ZoneCaps.DEFAULT.defaultRadiusBlocks(), map.originZ());
            assertEquals(1, map.epoch());
        } finally {
            registry.closeWorker();
        }
    }

    /** SP-02: a member outside the inner square re-anchors after 40 noted ticks, at most once every 100 ticks. */
    @Test
    void aMemberOutsideTheInnerSquareReanchorsWithHysteresisAndACap() {
        var calls = new ArrayList<String>();
        var registry = registry();
        try {
            var id = new EncounterId(51);
            var ground = world(false, calls);
            registry.open(id, List.of(), ZoneSettings.DEFAULT);
            registry.notePosition(id, ground, 8, 64, 8);
            registry.step(1L, 0L);
            assertEquals(1, registry.status(id).orElseThrow().epoch());
            for (var tick = 2L; tick <= 100L; tick++) {
                registry.notePosition(id, ground, 33, 64, 8);
                registry.step(tick, 0L);
                assertEquals(1, registry.status(id).orElseThrow().epoch(), "tick " + tick);
            }
            assertTrue(registry.outsideTicks(id) >= ZoneRegistry.REANCHOR_AFTER_TICKS);
            registry.notePosition(id, ground, 33, 64, 8);
            registry.step(101L, 0L);
            assertEquals(2, registry.status(id).orElseThrow().epoch());
            for (var tick = 102L; tick <= 200L; tick++) {
                registry.notePosition(id, ground, 58, 64, 8);
                registry.step(tick, 0L);
                assertEquals(2, registry.status(id).orElseThrow().epoch(), "tick " + tick);
            }
            registry.notePosition(id, ground, 58, 64, 8);
            registry.step(201L, 0L);
            assertEquals(3, registry.status(id).orElseThrow().epoch());
        } finally {
            registry.closeWorker();
        }
    }

    /** SP-02: a member that steps out and comes back inside forty ticks never re-anchors. */
    @Test
    void aReturnInsideFortyTicksKeepsTheAnchor() {
        var calls = new ArrayList<String>();
        var registry = registry();
        try {
            var id = new EncounterId(52);
            var ground = world(false, calls);
            registry.open(id, List.of(), ZoneSettings.DEFAULT);
            registry.notePosition(id, ground, 8, 64, 8);
            registry.step(1L, 0L);
            for (var tick = 2L; tick <= 300L; tick++) {
                var outside = tick % 40L != 0L;
                registry.notePosition(id, ground, outside ? 33 : 8, 64, 8);
                registry.step(tick, 0L);
            }
            assertEquals(1, registry.status(id).orElseThrow().epoch());
            assertTrue(registry.outsideTicks(id) < ZoneRegistry.REANCHOR_AFTER_TICKS);
        } finally {
            registry.closeWorker();
        }
    }

    /** SP-03: after a re-anchor, the old map seeds the new grid by world coordinates, never by index. */
    @Test
    void aNewAnchorSeedsTheOldMapByWorldCoordinates() {
        var calls = new ArrayList<String>();
        var registry = new ZoneRegistry(new ZoneWorker(1), () -> 0L, caps(4, 1, 16, 256), null);
        try {
            var id = new EncounterId(53);
            var ground = patchyWorld(calls);
            registry.open(id, List.of(), ZoneSettings.DEFAULT);
            registry.anchor(id, ground, 0, 0, 64);
            settle(registry, id, 1_000L);
            var old = registry.map(id).orElseThrow();
            assertEquals(ZoneColumn.FLOOR, ZoneColumn.state(old.column(worldIndex(old, 5, 5))));
            assertTrue(ZoneColumn.flag(old.column(worldIndex(old, 5, 5)), ZoneColumn.LAVA));
            assertEquals(ZoneColumn.PIT, ZoneColumn.state(old.column(worldIndex(old, 3, 5))));
            registry.anchor(id, ground, 1, 0, 64);
            var moved = mapWithEpoch(registry, id, 1_001L, 2);
            assertEquals(0, moved.originX());
            assertEquals(ZoneColumn.FLOOR, ZoneColumn.state(moved.column(worldIndex(moved, 5, 5))));
            assertTrue(ZoneColumn.flag(moved.column(worldIndex(moved, 5, 5)), ZoneColumn.LAVA));
            assertEquals(ZoneColumn.PIT, ZoneColumn.state(moved.column(worldIndex(moved, 3, 5))));
            assertEquals(ZoneColumn.PIT, ZoneColumn.state(moved.column(worldIndex(moved, 21, 5))));
            assertFalse(ZoneColumn.flag(moved.column(worldIndex(moved, 21, 5)), ZoneColumn.LAVA));
        } finally {
            registry.closeWorker();
        }
    }

    /** SP-08: a chunk that unloads while a build is in flight is unknown in the map that build installs. */
    @Test
    void aChunkUnloadedDuringABuildIsUnknownWhenTheBuildInstalls() throws InterruptedException {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new ArrayList<String>();
        var registry = new ZoneRegistry(new ZoneWorker(1), () -> 0L, caps(4, 4, 16, 16), null);
        try {
            var id = new EncounterId(54);
            var ground = blockingWorld(calls, started, release);
            registry.open(id, List.of(), ZoneSettings.DEFAULT);
            registry.anchor(id, ground, 0, 0, 64);
            registry.step(1L, 1_000_000_000L);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            registry.unload(ground, 0, 0);
            release.countDown();
            var map = mapAt(registry, id, 2L);
            assertEquals(ZoneColumn.UNKNOWN, ZoneColumn.state(map.column(worldIndex(map, 8, 8))));
            assertEquals(ZoneColumn.FLOOR, ZoneColumn.state(map.column(worldIndex(map, -8, 8))));
        } finally {
            release.countDown();
            registry.closeWorker();
        }
    }

    /** SP-09: a zone the idle drop closed comes back when the encounter is sampled again. */
    @Test
    void anIdleZoneReopensWhenTheEncounterIsSampledAgain() {
        var calls = new ArrayList<String>();
        var registry = registry();
        try {
            var id = new EncounterId(55);
            var ground = world(false, calls);
            registry.open(id, List.of(), ZoneSettings.DEFAULT);
            registry.anchor(id, ground, 0, 0, 64);
            registry.step(10L, 0L);
            registry.noteCadence(id, TickTier.HOT);
            registry.step(10L + ZoneCaps.DEFAULT.idleDropTicks(), 0L);
            assertEquals(0, registry.openZones());
            registry.ensureOpen(id, ZoneSettings.DEFAULT, 0);
            assertEquals(1, registry.openZones());
            registry.notePosition(id, ground, 8, 64, 8);
            var map = mapAt(registry, id, 11L + ZoneCaps.DEFAULT.idleDropTicks());
            assertTrue(registry.status(id).orElseThrow().ready());
            assertEquals(1, map.epoch());
        } finally {
            registry.closeWorker();
        }
    }

    /** SP-16: a chunk load asks for one copy of that chunk, not two hundred and fifty-six dirty columns. */
    @Test
    void aChunkLoadRepeatsOneChunkInsteadOfTwoHundredColumns() {
        var calls = new ArrayList<String>();
        var registry = registry();
        try {
            var id = new EncounterId(56);
            var ground = world(true, calls);
            registry.open(id, List.of(), radius16());
            registry.anchor(id, ground, 0, 0, 64);
            publishAt(registry, id, 1_000L);
            var before = calls.stream().filter(name -> name.equals("getChunkAt")).count();
            registry.markChunk(ground, 0, 0, 1_001L);
            assertEquals(0, registry.dirtyCount(id));
            assertEquals(0, registry.dirtyDropped());
            settle(registry, id, 1_001L);
            assertEquals(1L, calls.stream().filter(name -> name.equals("getChunkAt")).count() - before);
        } finally {
            registry.closeWorker();
        }
    }

    private static int worldIndex(ZoneMap map, int worldX, int worldZ) {
        return (worldZ - map.originZ()) * map.width() + (worldX - map.originX());
    }

    private static ZoneMap mapWithEpoch(ZoneRegistry registry, EncounterId id, long tick, int epoch) {
        var deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            registry.step(tick, 1_000_000_000L);
            var map = registry.map(id);
            if (map.isPresent() && map.get().epoch() == epoch) {
                return map.get();
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("epoch " + epoch + " was not published");
    }

    /**
     * One lava floor at world (5, 5), local (5, 5) of chunk (0, 0), and air everywhere else: without a server the
     * material table knows only lava, so every other column is a pit.
     */
    private static World patchyWorld(List<String> calls) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> {
                    var name = method.getName();
                    if (name.equals("getMinHeight") || name.equals("isChunkLoaded") || name.equals("getChunkAt")) {
                        calls.add(name);
                    }
                    return switch (name) {
                        case "getMinHeight" -> -64;
                        case "isChunkLoaded" -> true;
                        case "getChunkAt" -> patchyChunk((Integer) args[0], (Integer) args[1]);
                        case "getChunkAtAsync" -> throw new AssertionError("async chunk load");
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "world";
                        default -> value(method.getReturnType());
                    };
                });
    }

    private static Chunk patchyChunk(int chunkX, int chunkZ) {
        var snapshot = (ChunkSnapshot) Proxy.newProxyInstance(ChunkSnapshot.class.getClassLoader(),
                new Class<?>[] {ChunkSnapshot.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockType" -> {
                        if ((Integer) args[1] != 64) {
                            yield Material.AIR;
                        }
                        var lava = chunkX == 0 && chunkZ == 0 && (Integer) args[0] == 5 && (Integer) args[2] == 5;
                        yield lava ? Material.LAVA : Material.AIR;
                    }
                    case "getHighestBlockYAt" -> 64;
                    case "isSectionEmpty" -> false;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "snapshot";
                    default -> value(method.getReturnType());
                });
        return (Chunk) Proxy.newProxyInstance(Chunk.class.getClassLoader(), new Class<?>[] {Chunk.class},
                (proxy, method, args) -> method.getName().equals("getChunkSnapshot") ? snapshot
                        : value(method.getReturnType()));
    }

    private static void overflowWhileBuilding() throws InterruptedException {
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new ArrayList<String>();
        var registry = new ZoneRegistry(new ZoneWorker(1), () -> 0L, caps(4, 4, 16, 16), null);
        try {
            var id = new EncounterId(9);
            var ground = blockingWorld(calls, started, release);
            registry.open(id, List.of(), ZoneSettings.DEFAULT);
            registry.anchor(id, ground, 0, 0, 64);
            var epoch = registry.status(id).orElseThrow().epoch();
            registry.step(1L, 1_000_000_000L);
            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
            for (var index = 0; index < 20; index++) {
                registry.noteBlock(ground, -16 + index, -16, 2L);
            }
            assertTrue(registry.dirtyDropped() > 0);
            assertEquals(1, registry.buildingCount());
            assertEquals(epoch, registry.status(id).orElseThrow().epoch());
        } finally {
            release.countDown();
            registry.closeWorker();
        }
    }

    private static ZoneCaps caps(int maxZones, int snapshots, int columnReads, int dirtyQueue) {
        var delivered = akyrion.thepredict.domain.ZoneCaps.DEFAULT;
        var threshold = Math.min(delivered.dirtyRebuildThreshold(), dirtyQueue);
        return new akyrion.thepredict.domain.ZoneCaps(delivered.mode(), delivered.defaultRadiusBlocks(),
                delivered.maxRadiusBlocks(), delivered.bandBlocks(), maxZones, snapshots,
                delivered.maxConcurrentBuilds(), delivered.maxDropScanBlocks(), delivered.maxMicrosPerTick(),
                columnReads, delivered.minColumnRefreshTicks(), dirtyQueue, threshold, delivered.verifyIntervalTicks(),
                delivered.idleDropTicks(), delivered.maxQueriesPerTick(), delivered.knockbackDepthBlocks(),
                delivered.strongPushDepthBlocks(), delivered.maxSafeDropBlocks(), delivered.factPeriodTicks(),
                delivered.factOnTicks(), delivered.factOffTicks(), delivered.maxFactsPerMob(),
                delivered.pushDeferTicks(), delivered.pushDeferMaxTicks(), delivered.pushAttributionTicks(),
                delivered.answerRetryTicks(), delivered.answerMaxWaitTicks());
    }

    private static World blockingWorld(List<String> calls, java.util.concurrent.CountDownLatch started,
                                       java.util.concurrent.CountDownLatch release) {
        var snapshot = (ChunkSnapshot) Proxy.newProxyInstance(ChunkSnapshot.class.getClassLoader(),
                new Class<?>[] {ChunkSnapshot.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getBlockType")) {
                        started.countDown();
                        release.await();
                        return ((Integer) args[1]) == 64 ? Material.LAVA : Material.AIR;
                    }
                    return switch (method.getName()) {
                        case "getHighestBlockYAt" -> 64;
                        case "isSectionEmpty" -> false;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "snapshot";
                        default -> value(method.getReturnType());
                    };
                });
        return world(true, calls, snapshot);
    }

    private static ZoneRegistry registry() {
        return new ZoneRegistry(new ZoneWorker(4), () -> 0L);
    }

    private static ZoneMap published(ZoneRegistry registry, EncounterId id) {
        var deadline = System.nanoTime() + 1_000_000_000L;
        var tick = 1L;
        while (System.nanoTime() < deadline) {
            registry.step(tick, 1_000_000L);
            tick++;
            var map = registry.map(id);
            if (map.isPresent()) {
                return map.get();
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("map was not published");
    }

    private static ZoneMap mapAt(ZoneRegistry registry, EncounterId id, long tick) {
        var deadline = System.nanoTime() + 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            registry.step(tick, 1_000_000_000L);
            var map = registry.map(id);
            if (map.isPresent()) {
                return map.get();
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("map was not published");
    }

    private static void publishAt(ZoneRegistry registry, EncounterId id, long tick) {
        var deadline = System.nanoTime() + 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            registry.step(tick, 1_000_000_000L);
            if (registry.map(id).isPresent() && registry.buildingCount() == 0) {
                registry.step(tick, 1_000_000_000L);
                if (registry.buildingCount() == 0) {
                    return;
                }
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("map was not published");
    }

    private static void settle(ZoneRegistry registry, EncounterId id, long tick) {
        var deadline = System.nanoTime() + 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            registry.step(tick, 1_000_000_000L);
            if (registry.buildingCount() == 0 && registry.map(id).isPresent()) {
                registry.step(tick, 1_000_000_000L);
                if (registry.buildingCount() == 0) {
                    return;
                }
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("build did not settle");
    }

    private static boolean dirtyHas(ZoneRegistry registry, EncounterId id, int column) {
        for (var cursor = 0; cursor < registry.dirtyCount(id); cursor++) {
            if (registry.dirtyColumn(id, cursor) == column) {
                return true;
            }
        }
        return false;
    }

    private static ZoneSettings radius16() {
        return new ZoneSettings(Optional.empty(), OptionalInt.of(16), true, HazardPush.OFF);
    }

    private static World stagedWorld(List<String> calls, AtomicInteger generation, AtomicBoolean blocked,
                                     CountDownLatch started, CountDownLatch release) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> {
                    var name = method.getName();
                    if (name.equals("getMinHeight") || name.equals("isChunkLoaded") || name.equals("getChunkAt")
                            || name.equals("getChunkAtAsync")) {
                        calls.add(name);
                    }
                    return switch (name) {
                        case "getMinHeight" -> -64;
                        case "isChunkLoaded" -> true;
                        case "getChunkAt" -> stagedChunk(generation.get(), blocked, started, release);
                        case "getChunkAtAsync" -> throw new AssertionError("async chunk load");
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "world";
                        default -> value(method.getReturnType());
                    };
                });
    }

    private static Chunk stagedChunk(int generation, AtomicBoolean blocked, CountDownLatch started,
                                     CountDownLatch release) {
        var snapshot = stagedSnapshot(generation, blocked, started, release);
        return (Chunk) Proxy.newProxyInstance(Chunk.class.getClassLoader(), new Class<?>[] {Chunk.class},
                (proxy, method, args) -> method.getName().equals("getChunkSnapshot") ? snapshot
                        : value(method.getReturnType()));
    }

    private static ChunkSnapshot stagedSnapshot(int generation, AtomicBoolean blocked, CountDownLatch started,
                                                CountDownLatch release) {
        return (ChunkSnapshot) Proxy.newProxyInstance(ChunkSnapshot.class.getClassLoader(),
                new Class<?>[] {ChunkSnapshot.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockType" -> {
                        if (blocked.compareAndSet(false, true)) {
                            started.countDown();
                            try {
                                release.await();
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        yield ((Integer) args[1]) == 64
                                ? generation == 0 ? Material.STONE : Material.LAVA : Material.AIR;
                    }
                    case "getHighestBlockYAt" -> 64;
                    case "isSectionEmpty" -> false;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "snapshot";
                    default -> value(method.getReturnType());
                });
    }

    private static World world(boolean loaded, List<String> calls) {
        return world(loaded, calls, snapshot());
    }

    private static World world(boolean loaded, List<String> calls, ChunkSnapshot snapshot) {
        var chunk = (Chunk) Proxy.newProxyInstance(Chunk.class.getClassLoader(), new Class<?>[] {Chunk.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getChunkSnapshot")) {
                        calls.add("getChunkSnapshot");
                        assertEquals(Boolean.TRUE, args[0]);
                        assertEquals(Boolean.FALSE, args[1]);
                        assertEquals(Boolean.FALSE, args[2]);
                        assertEquals(Boolean.FALSE, args[3]);
                        return snapshot;
                    }
                    return value(method.getReturnType());
                });
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> {
                    var name = method.getName();
                    if (name.equals("getMinHeight") || name.equals("isChunkLoaded") || name.equals("getChunkAt")
                            || name.equals("getChunkAtAsync")) {
                        calls.add(name);
                    }
                    return switch (name) {
                        case "getMinHeight" -> -64;
                        case "isChunkLoaded" -> loaded;
                        case "getChunkAt" -> {
                            if (!loaded) {
                                throw new AssertionError("unloaded chunk was requested");
                            }
                            yield chunk;
                        }
                        case "getChunkAtAsync" -> throw new AssertionError("async chunk load");
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "world";
                        default -> value(method.getReturnType());
                    };
                });
    }

    private static ChunkSnapshot snapshot() {
        return (ChunkSnapshot) Proxy.newProxyInstance(ChunkSnapshot.class.getClassLoader(),
                new Class<?>[] {ChunkSnapshot.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockType" -> ((Integer) args[1]) == 64 ? Material.LAVA : Material.AIR;
                    case "getHighestBlockYAt" -> 64;
                    case "isSectionEmpty" -> false;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "snapshot";
                    default -> value(method.getReturnType());
                });
    }

    private static Object value(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }
}
