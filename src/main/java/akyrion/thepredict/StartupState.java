package akyrion.thepredict;

import akyrion.thepredict.admin.SetupReport;
import akyrion.thepredict.config.ConfigLocations;
import akyrion.thepredict.config.ConfigProblem;
import akyrion.thepredict.config.ConfigurationException;
import akyrion.thepredict.config.FirstRunFiles;
import akyrion.thepredict.config.MigrationPlanner;
import akyrion.thepredict.config.PluginConfigurationLoader;
import akyrion.thepredict.config.PluginSettings;
import akyrion.thepredict.config.ProfileCatalog;
import org.bukkit.configuration.InvalidConfigurationException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * What the start of the plugin learns before anything is wired (M03.5, M03.6, M12.4): the detected versions, the
 * first-run files, a migration proposal and the master file read strictly from AIConfig. Only the root reads
 * {@code config} (rule D10). Immutable; server thread.
 *
 * @param versions versions detected by {@link RuntimeCompatibility}
 * @param settings settings of a valid master file, empty when it is invalid, uses schema 1, or still lives in 0.0.1
 * @param configurationProblem why activation is blocked, empty when the new location is valid
 * @param migrationReport path of the migration report relative to the data folder, while a proposal remains
 * @param locationNotice warning that old files are ignored because AIConfig already has configuration
 */
record StartupState(RuntimeCompatibility.DetectedVersions versions, Optional<PluginSettings> settings,
                    Optional<ConfigProblem> configurationProblem, Optional<String> migrationReport,
                    Optional<String> locationNotice) {
    /** Console and status text when both locations have content (errata EP-2). */
    static final String OLD_FILES_IGNORED = "Old files in plugins/ThePredict/config.yml and plugins/ThePredict/AI/"
            + " are ignored because plugins/MythicMobs/AIConfig/ already has configuration. Nothing was moved or"
            + " deleted.";

    StartupState {
        Objects.requireNonNull(versions, "versions");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(configurationProblem, "configurationProblem");
        Objects.requireNonNull(migrationReport, "migrationReport");
        Objects.requireNonNull(locationNotice, "locationNotice");
    }

    /**
     * Creates the missing setup files, writes one proposal when needed and reads {@code AIConfig/config.yml}.
     * A 0.0.1 location with an empty AIConfig is not loaded and does not receive default files. An invalid or
     * schema 1 file is a {@link ConfigProblem}, never a fallback to defaults, and no administrator file changes.
     *
     * @param locations MythicMobs and plugin data folders
     * @param versions versions detected by {@link RuntimeCompatibility}
     * @param now local time that names a new proposal directory
     * @param log receiver of the fixed setup lines
     * @param environment returns the value of a variable, or {@code null} when it is not set; only the key is read
     * @return the state
     * @throws IOException when a bundled setup file cannot be written
     */
    static StartupState load(ConfigLocations locations, RuntimeCompatibility.DetectedVersions versions,
                             LocalDateTime now, Consumer<String> log, UnaryOperator<String> environment)
            throws IOException {
        Objects.requireNonNull(locations, "locations");
        Objects.requireNonNull(environment, "environment");
        var legacy = locations.legacyHasContent();
        var empty = locations.aiConfigEmpty();
        var created = legacy && empty
                ? new FirstRunFiles().installExamples(locations.dataFolder())
                : new FirstRunFiles().installMissing(locations);
        if (!created.isEmpty()) {
            log.accept("Created setup files: " + String.join(", ", created));
        }
        var notice = legacy && !empty ? Optional.of(OLD_FILES_IGNORED) : Optional.<String>empty();
        var migration = proposeMigration(locations, now, log);
        if (legacy && empty) {
            return new StartupState(versions, Optional.empty(), Optional.of(problem("still uses the 0.0.1 location"
                    + " (plugins/ThePredict/config.yml or plugins/ThePredict/AI/) while plugins/MythicMobs/AIConfig/"
                    + " has no configuration; review the migration proposal under " + MigrationPlanner.DIRECTORY
                    + "/. Nothing was moved or deleted and no query was enabled.")), migration, notice);
        }
        return readActive(locations, versions, log, migration, notice, environment);
    }

    /**
     * Reads the master file. Every failure is a problem with file and path, so ThePredict stays inactive with
     * {@code /thepredict status} working instead of being disabled (A6): a range failure that a {@code domain}
     * record reports as a plain {@link IllegalArgumentException} is caught here as the last net.
     */
    private static StartupState readActive(ConfigLocations locations, RuntimeCompatibility.DetectedVersions versions,
                                           Consumer<String> log, Optional<String> migration, Optional<String> notice,
                                           UnaryOperator<String> environment) {
        var loader = new PluginConfigurationLoader();
        try {
            var configuration = loader.read(locations.masterFile());
            if (loader.isVersionOne(configuration)) {
                return new StartupState(versions, Optional.empty(), Optional.of(problem("uses schema 1; review the"
                        + " migration proposal under " + MigrationPlanner.DIRECTORY + "/")), migration, notice);
            }
            if (configuration.contains("decision.maxReadyChecksPerTick")) {
                log.accept(PluginConfigurationLoader.FILE + " decision.maxReadyChecksPerTick is obsolete; the tick"
                        + " layer uses decision.tickLayer.maxSessionsPerTick");
            }
            return new StartupState(versions, Optional.of(loader.parse(configuration, environment)), Optional.empty(),
                    migration, notice);
        } catch (InvalidConfigurationException exception) {
            return new StartupState(versions, Optional.empty(), Optional.of(problem("has invalid YAML syntax")),
                    migration, notice);
        } catch (IOException exception) {
            return new StartupState(versions, Optional.empty(), Optional.of(problem("could not be read")), migration,
                    notice);
        } catch (ConfigurationException exception) {
            return new StartupState(versions, Optional.empty(),
                    Optional.of(exception.toProblem(PluginConfigurationLoader.FILE)), migration, notice);
        } catch (IllegalArgumentException exception) {
            return new StartupState(versions, Optional.empty(), Optional.of(problem("has an invalid value: "
                    + exception.getMessage())), migration, notice);
        }
    }

    private static ConfigProblem problem(String reason) {
        return ConfigProblem.error(PluginConfigurationLoader.FILE, "", reason);
    }

    /** Writes a proposal when one is needed and returns the path of its report; nothing else changes. */
    private static Optional<String> proposeMigration(ConfigLocations locations, LocalDateTime now, Consumer<String> log) {
        try {
            var planner = new MigrationPlanner();
            return planner.write(locations.dataFolder(), planner.plan(locations), now).map(directory -> locations
                    .dataFolder().relativize(directory.resolve(MigrationPlanner.REPORT)).toString().replace('\\', '/'));
        } catch (IOException | RuntimeException exception) {
            log.accept("The migration proposal could not be written (" + exception.getClass().getSimpleName() + ")");
            return Optional.empty();
        }
    }

    /**
     * Returns the settings in use: those of the master file, or the defaults while it is invalid, so the parts
     * that stay alive (catalog, sessions without Jev) have their limits.
     *
     * @return the settings in use
     */
    PluginSettings settingsInUse() {
        return settings.orElse(PluginSettings.standard());
    }

    /**
     * Builds the installation facts of {@code /thepredict status} and of the setup messages.
     *
     * @param catalog catalog in use
     * @param profilesRead valid profiles read last, active or not
     * @param catalogSummary summary of its last load
     * @return the report
     */
    SetupReport report(ProfileCatalog catalog, int profilesRead, String catalogSummary) {
        Objects.requireNonNull(catalog, "catalog");
        return new SetupReport(versions.javaFeature(), versions.minecraftVersion(), versions.mythicVersion(),
                SetupReport.CatalogState.valueOf(catalog.status().name()), catalog.catalogRevision(),
                catalog.profiles().size(), profilesRead, catalogSummary, configurationProblem.map(ConfigProblem::toString),
                migrationReport, locationNotice);
    }
}
