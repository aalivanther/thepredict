package akyrion.thepredict;

import akyrion.thepredict.adapter.server.context.MythicParticipantLookup;
import akyrion.thepredict.adapter.server.duel.DuelListener;
import akyrion.thepredict.adapter.server.duel.DuelRecorder;
import akyrion.thepredict.adapter.server.duel.DuelWriter;
import akyrion.thepredict.adapter.server.duel.LiveDuelScene;
import akyrion.thepredict.adapter.server.events.ServerThread;
import akyrion.thepredict.engine.DuelRecording;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import akyrion.thepredict.engine.SwitchableDecisionTap;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Builds the duel recorder of {@code /thepredict duel} (M18.4) for {@link ThePredictPlugin} (rule D8): the recorder
 * over the live scene, its listener and its sample task, run every 5 ticks by the one engine task of the root. Server thread only, during
 * {@code onEnable}. Off by default: nothing records until {@code /thepredict duel start}. A failure is logged once and
 * leaves {@link DuelRecording#UNAVAILABLE}, so the rest of the plugin is untouched.
 */
final class DuelWiring {
    private DuelWiring() {
    }

    /**
     * Builds and registers the recorder; its samples run in the one engine task through {@code hook}.
     *
     * @return the recorder, empty when it could not start
     */
    static Optional<DuelRecorder> recorder(JavaPlugin plugin, ServerClock clock, IntentBoard board,
                                           SwitchableDecisionTap tap, ProfileCatalogs catalogs,
                                           ServerThread thread, SampleHook hook) {
        try {
            var lookup = new MythicParticipantLookup(MythicBukkit.inst().getMobManager(), catalogs::binding);
            var recorder = new DuelRecorder(new DuelRecorder.Parts(plugin.getDataFolder().toPath().resolve("duels"),
                    clock, Clock.systemUTC(), new LiveDuelScene(lookup, catalogs::boundProfiles), board, tap,
                    hook,
                    new DuelRecorder.Notifier() {
                        @Override
                        public void stopped(String line) {
                            plugin.getLogger().info(line);
                        }

                        @Override
                        public void stopped(UUID requester, String line) {
                            plugin.getLogger().info(line);
                            Optional.ofNullable(Bukkit.getPlayer(requester)).ifPresent(player ->
                                    player.sendPlainMessage(line));
                        }
                    }, DuelWriter::create));
            plugin.getServer().getPluginManager().registerEvents(new DuelListener(recorder, thread), plugin);
            return Optional.of(recorder);
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.SEVERE, "The duel recorder could not start; /thepredict duel is off",
                    exception);
            return Optional.empty();
        }
    }

    /**
     * The sample task of a recording, run by the one periodic engine task of the root, so the engine keeps a single
     * periodic task (rule of {@code ArchitectureRulesTest}). Without a recording {@link #run} reads one field.
     */
    static final class SampleHook implements DuelRecorder.Repeater, Runnable {
        private Optional<Runnable> task = Optional.empty();
        private long period = 1;
        private long ticks;

        @Override
        public AutoCloseable every(long periodTicks, Runnable work) {
            period = Math.max(1, periodTicks);
            ticks = 0;
            var scheduled = Optional.of(work);
            task = scheduled;
            return () -> {
                if (task == scheduled) {
                    task = Optional.empty();
                }
            };
        }

        @Override
        public void run() {
            if (task.isPresent() && ++ticks % period == 0) {
                task.get().run();
            }
        }
    }

    /** The UUID of a loaded world by name, for the console form of {@code /thepredict duel start}. */
    static Optional<UUID> world(String name) {
        return Optional.ofNullable(Bukkit.getWorld(name)).map(World::getUID);
    }
}
