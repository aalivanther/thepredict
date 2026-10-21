package akyrion.thepredict;

import akyrion.thepredict.adapter.server.binding.BoundProfile;
import akyrion.thepredict.config.ConfigProblem;
import akyrion.thepredict.config.ProfileCatalog;
import akyrion.thepredict.config.ProfileLoadResult;
import akyrion.thepredict.domain.TacticalProfile;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The profile catalog of the running plugin (M03.5): the first load, the reload after every {@code /mm reload}, the
 * deferred check of custom conditions two ticks later and the bounded log of problems (rule C8). Wraps
 * {@link CatalogReloader}, which exchanges the catalog in one assignment. Server thread only.
 */
final class ProfileCatalogs {
    /** Ticks MythicMobs needs to resolve the custom conditions of a reload. */
    static final long CONDITION_CHECK_DELAY_TICKS = 2L;

    /** Most problems logged per load. */
    static final int MAX_LOGGED_PROBLEMS = 50;

    private final CatalogReloader reloader;
    private final Logger logger;
    private final BiConsumer<Runnable, Long> later;
    private final BooleanSupplier enabled;
    private final Runnable changed;
    private boolean blocked;
    private Consumer<ProfileLoadResult> reads = read -> { };
    private Path dataFolder = Path.of("");

    /**
     * Creates the catalogs; nothing is read until {@link #start} or {@link #startBlocked}.
     *
     * @param reloader reader and binder of the {@code AI} folder
     * @param logger plugin logger
     * @param later runs a task after the given ticks
     * @param enabled whether the plugin is still enabled when a deferred task runs
     * @param changed tells the sessions that the catalog changed without a MythicMobs reload
     */
    ProfileCatalogs(CatalogReloader reloader, Logger logger, BiConsumer<Runnable, Long> later, BooleanSupplier enabled,
                    Runnable changed) {
        this.reloader = Objects.requireNonNull(reloader, "reloader");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.later = Objects.requireNonNull(later, "later");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    /** Reads and binds the profiles for the first time. */
    void start() {
        reload();
    }

    /**
     * Keeps the catalog empty because {@code config.yml} is invalid; MythicMobs reloads do not read profiles either.
     *
     * @param problem why the configuration is invalid
     */
    void startBlocked(ConfigProblem problem) {
        blocked = true;
        reloader.block(problem);
    }

    /** Reads and binds the profiles again after MythicMobs reloaded its mobs and skills, unless blocked. */
    void onMythicReload() {
        if (!blocked) {
            reload();
        }
    }

    /**
     * Returns the catalog in use.
     *
     * @return the current catalog
     */
    ProfileCatalog catalog() {
        return reloader.catalog();
    }

    /**
     * Returns the summary of the last load.
     *
     * @return the summary for logs and {@code /thepredict status}
     */
    String summary() {
        return reloader.summary();
    }

    /** Returns the number of valid profiles read last, active or not, for the setup messages. */
    int profilesRead() {
        return reloader.profilesRead();
    }

    /** Returns the active profile of a MythicMobs mob ID, for the sessions. */
    Optional<TacticalProfile> profile(String mythicMobId) {
        return Optional.ofNullable(reloader.catalog().profiles().get(mythicMobId));
    }

    /** Returns the binding of a MythicMobs mob ID, for the combat reader. */
    Optional<BoundProfile> binding(String mythicMobId) {
        return Optional.ofNullable(reloader.bindings().get(mythicMobId));
    }

    /** Returns the profiles bound in the catalog in use, for the counted skills of the duel recorder (M18.4). */
    Collection<TacticalProfile> boundProfiles() {
        return reloader.bindings().values().stream().map(BoundProfile::profile).toList();
    }

    /** Returns the revision of the catalog in use (rule I11). */
    long revision() {
        return reloader.catalog().catalogRevision();
    }

    /**
     * Data folder that holds {@code autoconfig/active}.
     *
     * @param folder the plugin data folder
     */
    void useDataFolder(Path folder) {
        dataFolder = folder;
    }

    /**
     * Whether a storage admin result changed {@code autoconfig/active}: an {@code autoconfig apply} or
     * {@code autoconfig revert} that succeeded (M29.8, CAR-06).
     *
     * @param ok whether the command succeeded
     * @param detail the detail of the result
     * @return {@code true} when the catalog must be reloaded
     */
    static boolean changesOverlay(boolean ok, String detail) {
        return ok && (detail.startsWith("applied ") || "reverted".equals(detail));
    }

    /**
     * Reloads the catalog after an overlay was applied or reverted, and tells the live sessions, which advance with
     * their new profile as on a MythicMobs reload (M29.8). The author file is not written.
     */
    void reloadAfterOverlay() {
        if (!blocked) {
            reload();
            changed.run();
        }
    }

    /**
     * Hears every read of the profiles, after the overlays and the log, on the server thread.
     *
     * @param listener receives the profiles before any overlay and their fingerprints
     */
    void onRead(Consumer<ProfileLoadResult> listener) {
        reads = Objects.requireNonNull(listener, "listener");
    }

    private void reload() {
        reloader.reload();
        noteOverlays();
        log();
        reads.accept(reloader.read());
        var revision = reloader.catalog().catalogRevision();
        later.accept(() -> verifyConditions(revision), CONDITION_CHECK_DELAY_TICKS);
    }

    /** Applies {@code autoconfig/active} per profile; a failure keeps the author profiles just read (A6). */
    private void noteOverlays() {
        try {
            var read = ActiveOverlays.read(dataFolder.resolve("autoconfig").resolve("active"));
            if (!read.isEmpty()) {
                reloader.noteOverlays(read);
            }
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "The active overlays were not applied; the author profiles stay", exception);
        }
    }

    /** Disables the contexts whose custom conditions no plugin resolved once MythicMobs had its tick to do so. */
    private void verifyConditions(long revision) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        var before = reloader.catalog();
        if (reloader.verifyConditions(revision) != before) {
            log();
            changed.run();
        }
    }

    private void log() {
        logger.info("Profile catalog " + reloader.summary());
        List<ConfigProblem> problems = reloader.catalog().problems();
        problems.stream().limit(MAX_LOGGED_PROBLEMS).forEach(problem -> {
            if (problem.isError()) {
                logger.severe(problem.toString());
            } else {
                logger.warning(problem.toString());
            }
        });
        if (problems.size() > MAX_LOGGED_PROBLEMS) {
            logger.warning((problems.size() - MAX_LOGGED_PROBLEMS) + " more problems are not shown");
        }
    }
}
