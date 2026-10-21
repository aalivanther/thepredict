package akyrion.thepredict.config;

import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TickLayerMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads the schema 2 profiles of {@code AIConfig/Profiles} (M03.2). A profile with an error is excluded
 * and the others still load; two profiles with the same ID or for the same mob are both excluded.
 */
public final class ProfileLoader {
    private final ProfileParser parser;
    private final TickLayerMode tickLayerCeiling;

    /** Creates a loader that inherits the defaults of a fresh installation. */
    public ProfileLoader() {
        this(PluginSettings.standard());
    }

    /**
     * Creates a loader that inherits the values of {@code config.yml}.
     *
     * @param settings validated plugin settings
     */
    public ProfileLoader(PluginSettings settings) {
        this.parser = new ProfileParser(settings);
        this.tickLayerCeiling = settings.decision().tickLayerMode();
    }

    /**
     * The global {@code decision.tickLayer.mode}: no profile, and no overlay of one, runs more of the tick layer
     * (M20.3, DOC-04).
     *
     * @return the global mode
     */
    public TickLayerMode tickLayerCeiling() {
        return tickLayerCeiling;
    }

    /**
     * Tells whether a profile file is schema 1: no {@code schemaVersion} and a profile that uses a schema 1
     * field ({@code Operations}, {@code Behaviors}...) or declares no {@code Tactics}.
     *
     * @param file parsed profile file
     * @return {@code true} for a file that needs migration
     */
    public static boolean isVersionOne(ConfigurationSection file) {
        if (file.contains("schemaVersion")) {
            return false;
        }
        for (var key : file.getKeys(false)) {
            var profile = file.getConfigurationSection(key);
            if (profile != null && (!profile.contains("Tactics")
                    || profile.getKeys(false).stream().anyMatch(ProfileSchema.VERSION_ONE_PROFILE::contains))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads every {@code .yml} and {@code .yaml} file of a directory, creating it when absent.
     *
     * @param directory profile directory, {@code AIConfig/Profiles} on a server
     * @return valid profiles and every problem found
     * @throws IOException when the directory itself cannot be created or listed
     */
    public ProfileLoadResult load(Path directory) throws IOException {
        Files.createDirectories(directory);
        List<Path> paths;
        try (var listing = Files.list(directory)) {
            paths = listing.filter(ProfileLoader::isProfileFile).sorted().toList();
        }
        var problems = new ArrayList<ConfigProblem>();
        var parsed = new ArrayList<Parsed>();
        var unreadable = 0;
        for (var path : paths) {
            var file = ConfigLocations.profilesLabel() + "/" + path.getFileName();
            var yaml = read(path, file, problems);
            if (yaml.isEmpty()) {
                unreadable++;
            } else if (acceptsSchema(yaml.orElseThrow(), file, problems)) {
                parseProfiles(yaml.orElseThrow(), file, problems, parsed);
            }
        }
        var kept = excludeDuplicates(parsed, problems);
        var profiles = new LinkedHashMap<String, TacticalProfile>();
        var files = new HashMap<String, String>();
        var fingerprints = new HashMap<String, String>();
        for (var entry : kept) {
            profiles.put(entry.profile().mythicMobId(), entry.profile());
            files.put(entry.profile().id(), entry.file());
            fingerprints.put(entry.profile().id(), entry.fingerprint());
        }
        return new ProfileLoadResult(profiles, files, problems, paths.size(), unreadable, fingerprints);
    }

    private static boolean isProfileFile(Path path) {
        var name = path.getFileName().toString();
        return Files.isRegularFile(path) && (name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    private static Optional<YamlConfiguration> read(Path path, String file, List<ConfigProblem> problems) {
        var yaml = new YamlConfiguration();
        try {
            yaml.load(path.toFile());
            return Optional.of(yaml);
        } catch (InvalidConfigurationException exception) {
            var detail = String.valueOf(exception.getMessage()).lines().findFirst().orElse("syntax error");
            problems.add(ConfigProblem.error(file, "", "is not valid YAML: " + detail));
        } catch (IOException exception) {
            problems.add(ConfigProblem.error(file, "", "could not be read"));
        }
        return Optional.empty();
    }

    private static boolean acceptsSchema(YamlConfiguration yaml, String file, List<ConfigProblem> problems) {
        if (isVersionOne(yaml)) {
            problems.add(ConfigProblem.error(file, "schemaVersion",
                    "is missing: this is a schema 1 profile file; review the migration proposal under migration/"));
            return false;
        }
        if (!(yaml.get("schemaVersion") instanceof Integer version) || version != ProfileSchema.SCHEMA_VERSION) {
            problems.add(ConfigProblem.error(file, "schemaVersion", "must be " + ProfileSchema.SCHEMA_VERSION
                    + ", was " + ConfigurationValues.safeValue(yaml.get("schemaVersion"))));
            return false;
        }
        return true;
    }

    private void parseProfiles(YamlConfiguration yaml, String file, List<ConfigProblem> problems, List<Parsed> parsed) {
        for (var key : yaml.getKeys(false)) {
            if (key.equals("schemaVersion")) {
                continue;
            }
            var warnings = new ArrayList<ConfigProblem>();
            try {
                var section = ConfigurationValues.section(yaml, key).orElseThrow();
                parsed.add(new Parsed(file, parser.parse(section, file, warnings),
                        ProfileNormalForm.fingerprint(section)));
                problems.addAll(warnings);
            } catch (ConfigurationException exception) {
                problems.add(exception.toProblem(file));
            }
        }
    }

    private static List<Parsed> excludeDuplicates(List<Parsed> parsed, List<ConfigProblem> problems) {
        var byId = group(parsed, entry -> entry.profile().id());
        var byMob = group(parsed, entry -> entry.profile().mythicMobId());
        var kept = new ArrayList<Parsed>();
        for (var entry : parsed) {
            var sameId = byId.get(entry.profile().id());
            var sameMob = byMob.get(entry.profile().mythicMobId());
            if (sameId.size() > 1) {
                problems.add(ConfigProblem.error(entry.file(), entry.profile().id(), "profile ID is declared "
                        + sameId.size() + " times (" + filesOf(sameId) + "); every copy is excluded"));
            } else if (sameMob.size() > 1) {
                problems.add(ConfigProblem.error(entry.file(), entry.profile().id() + ".MythicID", "mob '"
                        + entry.profile().mythicMobId() + "' has " + sameMob.size() + " profiles (" + idsOf(sameMob)
                        + "); all of them are excluded"));
            } else {
                kept.add(entry);
            }
        }
        return kept;
    }

    private static Map<String, List<Parsed>> group(List<Parsed> parsed, Function<Parsed, String> key) {
        return parsed.stream().collect(Collectors.groupingBy(key));
    }

    private static String filesOf(List<Parsed> entries) {
        return entries.stream().map(Parsed::file).collect(Collectors.joining(", "));
    }

    private static String idsOf(List<Parsed> entries) {
        return entries.stream().map(entry -> entry.profile().id()).collect(Collectors.joining(", "));
    }

    private record Parsed(String file, TacticalProfile profile, String fingerprint) {
    }
}
