package akyrion.thepredict.admin;

import java.util.Objects;
import java.util.Optional;

/**
 * What the composition root knows about the installation for {@code /thepredict status}, the startup messages and the
 * notice to administrators (M12.1, M12.4): versions, catalog, configuration and migration. It never holds a key or
 * any other configured value that could be a secret (rule C8). Immutable.
 *
 * @param javaFeature Java feature release of the server
 * @param minecraftVersion Minecraft version reported by Paper
 * @param mythicVersion MythicMobs version
 * @param catalog state of the profile catalog
 * @param catalogRevision current catalog revision (rule I11)
 * @param activeProfiles active profiles, one per bound MythicMobs mob type
 * @param profilesRead valid profiles read from {@code AIConfig/Profiles}, active or not
 * @param catalogSummary summary of the last profile load
 * @param configurationProblem why {@code AIConfig/config.yml} blocks activation, empty when it is valid
 * @param migrationReport path of the migration report relative to the plugin folder, while a proposal remains
 * @param locationNotice warning that 0.0.1 files are ignored because AIConfig already has configuration
 */
public record SetupReport(int javaFeature, String minecraftVersion, String mythicVersion, CatalogState catalog,
                          long catalogRevision, int activeProfiles, int profilesRead, String catalogSummary,
                          Optional<String> configurationProblem, Optional<String> migrationReport,
                          Optional<String> locationNotice) {
    /** State of the profile catalog, as {@code config.CatalogStatus} reports it. */
    public enum CatalogState {
        /** No profiles and no errors: a fresh installation. */
        SETUP_PENDING,
        /** Profiles loaded without errors. */
        LOADED,
        /** At least one error; the valid profiles are still loaded. */
        INVALID
    }

    /**
     * Validates the components.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public SetupReport {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(mythicVersion, "mythicVersion");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(catalogSummary, "catalogSummary");
        Objects.requireNonNull(configurationProblem, "configurationProblem");
        Objects.requireNonNull(migrationReport, "migrationReport");
        Objects.requireNonNull(locationNotice, "locationNotice");
    }
}
