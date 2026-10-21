package akyrion.thepredict.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Human-configuration routes (decision of 2026-10-02). The composition root resolves the two data folders;
 * this type only names paths. The legacy directory name exists here solely to detect a 0.0.1 installation.
 *
 * @param mythicDataFolder MythicMobs data folder, from that plugin's {@code getDataFolder()}
 * @param dataFolder ThePredict data folder, which keeps generated data
 */
public record ConfigLocations(Path mythicDataFolder, Path dataFolder) {
    /** Directory ThePredict may create inside the MythicMobs data folder. */
    public static final String AICONFIG = "AIConfig";
    /** Master configuration file name, in the new folder and in the 0.0.1 folder. */
    public static final String MASTER_FILE = "config.yml";
    /** Profile directory under {@link #AICONFIG}. */
    public static final String PROFILES = "Profiles";
    /** Profile directory of a 0.0.1 installation, used only for detection. */
    public static final String LEGACY_PROFILES = "AI";

    /**
     * Rejects a null folder.
     *
     * @throws NullPointerException if a folder is {@code null}
     */
    public ConfigLocations {
        Objects.requireNonNull(mythicDataFolder, "mythicDataFolder");
        Objects.requireNonNull(dataFolder, "dataFolder");
    }

    /**
     * Returns {@code plugins/MythicMobs/AIConfig}.
     *
     * @return the human-configuration directory
     */
    public Path aiConfig() {
        return mythicDataFolder.resolve(AICONFIG);
    }

    /**
     * Returns the master file {@code AIConfig/config.yml}.
     *
     * @return the master file
     */
    public Path masterFile() {
        return aiConfig().resolve(MASTER_FILE);
    }

    /**
     * Returns {@code AIConfig/Profiles}.
     *
     * @return the profile directory
     */
    public Path profilesDirectory() {
        return aiConfig().resolve(PROFILES);
    }

    /**
     * Returns the 0.0.1 master file under the plugin data folder.
     *
     * @return the old master file, used only for detection
     */
    public Path legacyMasterFile() {
        return dataFolder.resolve(MASTER_FILE);
    }

    /**
     * Returns the 0.0.1 profile directory under the plugin data folder.
     *
     * @return the old profile directory, used only for detection
     */
    public Path legacyProfilesDirectory() {
        return dataFolder.resolve(LEGACY_PROFILES);
    }

    /**
     * Returns the label cited by master-file problems.
     *
     * @return {@code AIConfig/config.yml}
     */
    public static String masterLabel() {
        return AICONFIG + "/" + MASTER_FILE;
    }

    /**
     * Returns the label cited by profile-directory problems.
     *
     * @return {@code AIConfig/Profiles}
     */
    public static String profilesLabel() {
        return AICONFIG + "/" + PROFILES;
    }

    /**
     * Returns the proposal key of the master file.
     *
     * @return {@code AIConfig/config.yml.proposed}
     */
    public static String proposedMaster() {
        return masterLabel() + ".proposed";
    }

    /**
     * Returns the proposal key of one profile file, keeping its original name.
     *
     * @param fileName profile file name, for example {@code guardian.yml}
     * @return key relative to the proposal directory
     */
    public static String proposedProfile(String fileName) {
        return profilesLabel() + "/" + fileName + ".proposed";
    }

    /**
     * Tells whether the 0.0.1 location has a master file or at least one profile.
     * {@code README.txt} and {@code *.yml.example} are not content.
     *
     * @return {@code true} when a move proposal may be required
     * @throws IOException when a profile directory cannot be listed
     */
    public boolean legacyHasContent() throws IOException {
        return Files.isRegularFile(legacyMasterFile()) || containsProfile(legacyProfilesDirectory());
    }

    /**
     * Tells whether {@code AIConfig} has no master file and no profile. A readme or an example template does not
     * count, so seeding those files cannot hide a pending move.
     *
     * @return {@code true} when the new location has no configuration yet
     * @throws IOException when the profile directory cannot be listed
     */
    public boolean aiConfigEmpty() throws IOException {
        return !Files.isRegularFile(masterFile()) && !containsProfile(profilesDirectory());
    }

    /**
     * Tells whether a path is a profile file ({@code .yml} or {@code .yaml}).
     *
     * @param path candidate file
     * @return {@code true} for a regular profile file
     */
    public static boolean isProfileFile(Path path) {
        var name = path.getFileName().toString();
        return Files.isRegularFile(path) && (name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    private static boolean containsProfile(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var listing = Files.list(directory)) {
            return listing.anyMatch(ConfigLocations::isProfileFile);
        }
    }
}
