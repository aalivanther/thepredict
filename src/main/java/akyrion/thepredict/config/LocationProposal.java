package akyrion.thepredict.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds one migration proposal: a move from the 0.0.1 location, or a schema 1 conversion of the active AIConfig
 * files. It never moves or deletes an administrator file.
 */
final class LocationProposal {
    /**
     * Proposes the 0.0.1 files at the new paths. Schema 1 is converted; schema 2 and unreadable YAML are copied raw.
     *
     * @param locations resolved folders, with content only in the old location
     * @return the proposal
     * @throws IOException when a directory cannot be listed or the report template is missing
     */
    MigrationProposal relocate(ConfigLocations locations) throws IOException {
        return propose(locations.legacyMasterFile(), locations.legacyProfilesDirectory(), true);
    }

    /**
     * Proposes a conversion of the schema 1 files already in AIConfig. Schema 2 files are left out.
     *
     * @param locations resolved folders
     * @return the proposal, or {@link MigrationProposal#none()} when nothing is schema 1
     * @throws IOException when a directory cannot be listed or the report template is missing
     */
    MigrationProposal activeSchema(ConfigLocations locations) throws IOException {
        return propose(locations.masterFile(), locations.profilesDirectory(), false);
    }

    private static MigrationProposal propose(Path master, Path profiles, boolean copyCurrent) throws IOException {
        var notes = new MigrationNotes();
        var files = new LinkedHashMap<String, String>();
        addMaster(master, copyCurrent, files, notes);
        addProfiles(profiles, copyCurrent, files, notes);
        return files.isEmpty() ? MigrationProposal.none() : new MigrationProposal(files, MigrationPlanner.render(files, notes));
    }

    private static void addMaster(Path master, boolean copyCurrent, Map<String, String> files, MigrationNotes notes)
            throws IOException {
        if (!Files.isRegularFile(master)) {
            return;
        }
        var raw = readText(master, ConfigLocations.masterLabel(), notes);
        if (raw.isEmpty()) {
            return;
        }
        var yaml = parse(raw.get());
        if (yaml.isPresent() && new PluginConfigurationLoader().isVersionOne(yaml.get())) {
            files.put(ConfigLocations.proposedMaster(),
                    ProposedConfiguration.write(ConfigurationMigration.convert(yaml.get(), notes)));
        } else if (copyCurrent) {
            files.put(ConfigLocations.proposedMaster(), raw.get());
            noteCopy(ConfigLocations.masterLabel(), yaml.isEmpty(), notes);
        }
    }

    private static void addProfiles(Path directory, boolean copyCurrent, Map<String, String> files,
                                    MigrationNotes notes) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        List<Path> profiles;
        try (var listing = Files.list(directory)) {
            profiles = listing.filter(ConfigLocations::isProfileFile).sorted().toList();
        }
        for (var path : profiles) {
            addProfile(path, copyCurrent, files, notes);
        }
    }

    private static void addProfile(Path path, boolean copyCurrent, Map<String, String> files, MigrationNotes notes)
            throws IOException {
        var name = path.getFileName().toString();
        var raw = readText(path, name, notes);
        if (raw.isEmpty()) {
            return;
        }
        var yaml = parse(raw.get());
        if (yaml.isPresent() && ProfileLoader.isVersionOne(yaml.get())) {
            var text = ProfileMigration.convert(name, yaml.get(), notes);
            validate(name, text, notes);
            files.put(ConfigLocations.proposedProfile(name), text);
        } else if (copyCurrent) {
            files.put(ConfigLocations.proposedProfile(name), raw.get());
            noteCopy(name, yaml.isEmpty(), notes);
        }
    }

    private static void noteCopy(String name, boolean unreadable, MigrationNotes notes) {
        if (unreadable) {
            notes.manual(name + ": YAML could not be read; the proposal copies the file unchanged for hand review");
        } else {
            notes.decision(name + " is already schema 2 and was copied unchanged");
        }
    }

    private static Optional<String> readText(Path path, String name, MigrationNotes notes) {
        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            notes.manual(name + ": could not be read; copy it by hand");
            return Optional.empty();
        }
    }

    private static Optional<YamlConfiguration> parse(String text) {
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
            return Optional.of(yaml);
        } catch (InvalidConfigurationException exception) {
            return Optional.empty();
        }
    }

    private static void validate(String file, String text, MigrationNotes notes) {
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException exception) {
            notes.manual(file + ": the proposal is not valid YAML; correct it by hand");
            return;
        }
        var parser = new ProfileParser(PluginSettings.standard());
        for (var key : yaml.getKeys(false)) {
            var section = yaml.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                parser.parse(section, file, new ArrayList<>());
            } catch (ConfigurationException exception) {
                notes.manual(file + ": the proposal does not validate yet: " + exception.getMessage());
            }
        }
    }
}
