package akyrion.thepredict.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Installs bundled setup files once without replacing administrator edits. */
public final class FirstRunFiles {
    private static final List<String> AICONFIG_FILES = List.of(
            "AIConfig/config.yml",
            "AIConfig/Profiles/README.txt",
            "AIConfig/Profiles/example.yml.example");
    private static final List<String> EXAMPLE_FILES = List.of(
            "examples/README.txt",
            "examples/MythicMobs/Mobs/ThePredictGuardian.yml",
            "examples/MythicMobs/Skills/ThePredictGuardianSkills.yml",
            "examples/AI/ThePredictGuardian.yml");

    /** Creates the installer for bundled setup and example resources. */
    public FirstRunFiles() {
    }

    /**
     * Installs missing AIConfig files and the boss examples. An existing file is left byte for byte.
     * Nothing is written in the MythicMobs folder outside {@code AIConfig/}.
     *
     * @param locations resolved data folders
     * @return relative names of files created during this call
     * @throws IOException when a bundled resource cannot be read or written, or a write would leave AIConfig
     */
    public List<String> installMissing(ConfigLocations locations) throws IOException {
        Objects.requireNonNull(locations, "locations");
        var created = new ArrayList<String>();
        for (var name : AICONFIG_FILES) {
            copyBundled(locations.mythicDataFolder().resolve(name), name, created, locations.aiConfig());
        }
        created.addAll(installExamples(locations.dataFolder()));
        return List.copyOf(created);
    }

    /**
     * Installs the boss examples under the plugin data folder and does not touch AIConfig.
     * Used while a 0.0.1 location still waits for a move, so the new folder stays empty.
     *
     * @param dataFolder plugin data folder
     * @return relative names of example files created during this call
     * @throws IOException when a bundled example cannot be read or written
     */
    public List<String> installExamples(Path dataFolder) throws IOException {
        Objects.requireNonNull(dataFolder, "dataFolder");
        var created = new ArrayList<String>();
        for (var name : EXAMPLE_FILES) {
            copyBundled(dataFolder.resolve(name), name, created, dataFolder);
        }
        return List.copyOf(created);
    }

    private static void copyBundled(Path destination, String resource, List<String> created, Path root)
            throws IOException {
        var target = destination.toAbsolutePath().normalize();
        if (!target.startsWith(root.toAbsolutePath().normalize())) {
            throw new IOException("Refusing to write outside " + root + ": " + destination);
        }
        if (Files.exists(destination)) {
            return;
        }
        Files.createDirectories(destination.getParent());
        try (InputStream stream = FirstRunFiles.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Bundled setup resource is missing: " + resource);
            }
            try {
                Files.copy(stream, destination);
                created.add(resource);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
            }
        }
    }
}
