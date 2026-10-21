package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import akyrion.thepredict.engine.SwitchableDecisionTap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doubles of the duel recorder for the tests and the local simulator of M18.7: a scene the test moves, a clock the
 * test advances, a repeater the test runs, the notifier lines and the profiles of the duel pack as the loader reads them.
 */
public final class DuelTestKit {
    /** The pack folder of the ThePredict duel profiles. */
    public static final Path PACK_PROFILES = Path.of(
            "DocumentacionParaDesarrollar/Implementacion-0.0.1/paquete-pruebas/archivos/13-duelo/plugins/MythicMobs/AIConfig/Profiles");
    /** The pack file of the duel skills. */
    public static final Path PACK_SKILLS = Path.of("DocumentacionParaDesarrollar/Implementacion-0.0.1/paquete-pruebas/"
            + "archivos/13-duelo/plugins/MythicMobs/Skills/ThePredictDuelSkills.yml");
    /** The world of the arena. */
    public static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000d0e1");

    /** The scene. */
    public final Scene scene = new Scene();
    /** The clock. */
    public final TickClock clock = new TickClock();
    /** The intent board the samples read. */
    public final IntentBoard board;
    /** The tap of the engine. */
    public final SwitchableDecisionTap tap;
    /** The sample task of the recording, run by the test. */
    public final Manual repeater = new Manual();
    /** The lines of recordings that stopped by themselves. */
    public final List<String> notices = new ArrayList<>();
    /** The recorder. */
    public final DuelRecorder recorder;

    /**
     * Creates a recorder writing under {@code root}.
     *
     * @param root plugin data folder of the test
     * @param board intent board
     * @param tap tap of the engine
     */
    public DuelTestKit(Path root, IntentBoard board, SwitchableDecisionTap tap) {
        this.board = board;
        this.tap = tap;
        recorder = new DuelRecorder(new DuelRecorder.Parts(root.resolve("duels"), clock,
                Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC), scene, board, tap, repeater,
                new Notices(notices), DuelWriter::create));
    }

    /**
     * The profiles of the duel pack by MythicMobs ID, as the loader reads them.
     *
     * @return {@code DuelRed} and {@code DuelBlue}
     */
    public static Map<String, TacticalProfile> packProfiles() {
        try {
            return new ProfileLoader().load(PACK_PROFILES).profiles();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Collects the lines of recordings that stopped by themselves, for the console or a player alike. */
    public static final class Notices implements DuelRecorder.Notifier {
        private final List<String> lines;

        /**
         * Creates the collector.
         *
         * @param lines where the lines go
         */
        public Notices(List<String> lines) {
            this.lines = lines;
        }

        @Override
        public void stopped(String line) {
            lines.add(line);
        }

        @Override
        public void stopped(UUID requester, String line) {
            lines.add(line);
        }
    }

    /** Runs the sample task every {@code period} ticks while the clock advances. */
    public static final class Manual implements DuelRecorder.Repeater {
        private Optional<Runnable> task = Optional.empty();
        private long period = 1;

        @Override
        public AutoCloseable every(long periodTicks, Runnable work) {
            period = periodTicks;
            task = Optional.of(work);
            return () -> task = Optional.empty();
        }

        /**
         * Runs the task when the tick is one of its period.
         *
         * @param tick current tick
         */
        public void tick(long tick) {
            if (tick % period == 0) {
                task.ifPresent(Runnable::run);
            }
        }

        /**
         * Tells whether a task is scheduled.
         *
         * @return {@code true} while recording
         */
        public boolean scheduled() {
            return task.isPresent();
        }
    }

    /** A clock the test moves: 50 ms per tick. */
    public static final class TickClock implements ServerClock {
        /** Current tick. */
        public long tick;
        /** Current time. */
        public long nanos;

        @Override
        public long currentTick() {
            return tick;
        }

        @Override
        public long nanoTime() {
            return nanos;
        }
    }

    /** A scene the test fills with duelists, factions and profiles. */
    public static class Scene implements DuelScene {
        /** Living duelists by UUID. */
        public final Map<UUID, DuelistReading> alive = new LinkedHashMap<>();
        /** Factions by UUID. */
        public final Map<UUID, String> factions = new HashMap<>();
        /** Bound profiles by UUID. */
        public final Map<UUID, TacticalProfile> bound = new HashMap<>();
        /** Every bound profile of the catalog. */
        public final List<TacticalProfile> catalog = new ArrayList<>();
        /** Whether the world is loaded. */
        public boolean loaded = true;

        /**
         * Puts a living duelist.
         *
         * @param reading the duelist
         */
        public void put(DuelistReading reading) {
            alive.put(reading.id(), reading);
            factions.put(reading.id(), reading.faction());
        }

        @Override
        public List<DuelistReading> duelists(UUID worldId, double centerX, double centerY, double centerZ,
                                             double radiusBlocks, String sideA, String sideB) {
            return alive.values().stream().filter(reading -> reading.faction().equalsIgnoreCase(sideA)
                    || reading.faction().equalsIgnoreCase(sideB)).limit(MAX_DUELISTS).toList();
        }

        @Override
        public Optional<String> faction(UUID entityId) {
            return Optional.ofNullable(factions.get(entityId));
        }

        @Override
        public Optional<TacticalProfile> profile(UUID entityId) {
            return Optional.ofNullable(bound.get(entityId));
        }

        @Override
        public Collection<TacticalProfile> profiles() {
            return catalog;
        }

        @Override
        public boolean worldLoaded(UUID worldId) {
            return loaded;
        }
    }

    /**
     * A duelist at a position with full health and no target.
     *
     * @param id its UUID
     * @param faction its faction
     * @param x position
     * @param z position
     * @return the reading
     */
    public static DuelistReading at(UUID id, String faction, double x, double z) {
        return new DuelistReading(id, faction, 1.0, 0.0, x, 65.0, z, 0f, 0.6, 1.95, Optional.empty(), false, false, "");
    }
}
