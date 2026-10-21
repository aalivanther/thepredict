package akyrion.thepredict.engine.duel;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The frozen winners of the searches (§7.3 of the simulator audit): {@code classic-legal-best-v1.yml} and
 * {@code classic-adversarial-v1.yml} under {@code src/test/resources/eval}. Each entry keeps the code that rebuilds the
 * classic, the SHA-256 of its MythicMobs files, those files, the score of the search and its seeds; an adversarial
 * entry also keeps the fingerprint of the ThePredict profile it was searched against. A rival is read back only when
 * its code still gives the same files; an adversarial one only when the profile is the same, otherwise the search runs
 * again.
 */
final class BenchFrozen {
    static final String LEGAL_BEST = "classic-legal-best-v1.yml";
    static final String ADVERSARIAL = "classic-adversarial-v1.yml";

    private BenchFrozen() {
    }

    /**
     * One frozen rival.
     *
     * @param key {@code K1}, or {@code K1-DEFAULTS} for an adversarial one
     * @param config the classic
     * @param score the score the search gave it
     * @param seeds the seeds of that score
     * @param profile the SHA-256 of the ThePredict profile it was searched against, empty for the legal best
     */
    record Entry(String key, ClassicConfig config, double score, String seeds, String profile) {
    }

    /**
     * Writes a frozen file.
     *
     * @param file the file
     * @param title what the entries are
     * @param entries the entries, in order
     */
    static void write(Path file, String title, Map<String, Entry> entries) {
        var out = new StringBuilder("# ").append(title).append('\n')
                .append("# Escrito por effectivenessBench (banco de efectividad v2). No editar a mano: el banco\n")
                .append("# reconstruye cada rival desde 'code' y exige que 'sha256' siga siendo la huella de 'mythic'.\n");
        for (var entry : entries.values()) {
            out.append(entry.key()).append(":\n");
            out.append("  name: '").append(entry.config().id()).append("'\n");
            out.append("  code: '").append(entry.config().code()).append("'\n");
            out.append("  sha256: '").append(entry.config().sha256()).append("'\n");
            out.append(String.format(Locale.ROOT, "  score: %.4f%n", entry.score()));
            out.append("  seeds: '").append(entry.seeds()).append("'\n");
            out.append("  profile: '").append(entry.profile()).append("'\n");
            out.append("  describe: '").append(entry.config().describe().replace("'", "''")).append("'\n");
            out.append("  mythic: |\n");
            for (var line : entry.config().toMythicYaml().split("\n")) {
                out.append("    ").append(line).append('\n');
            }
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Reads the entries of a frozen file whose code still gives its fingerprint.
     *
     * @param file the file
     * @return the entries by key, empty when the file is missing
     */
    static Map<String, Entry> read(Path file) {
        var entries = new LinkedHashMap<String, Entry>();
        if (!Files.isRegularFile(file)) {
            return entries;
        }
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        for (var key : yaml.getKeys(false)) {
            var section = yaml.getConfigurationSection(key);
            var kit = kit(key.split("-")[0]);
            if (section == null || kit.isEmpty()) {
                continue;
            }
            var config = ClassicSpace.parse(kit.get(), section.getString("name", key), section.getString("code", ""));
            if (!config.sha256().equals(section.getString("sha256"))) {
                throw new IllegalStateException(file + ": " + key + " no longer gives its fingerprint");
            }
            entries.put(key, new Entry(key, config, section.getDouble("score"), section.getString("seeds", ""),
                    section.getString("profile", "")));
        }
        return entries;
    }

    private static Optional<Kit> kit(String id) {
        return Kit.ALL.stream().filter(kit -> kit.id().equals(id)).findFirst();
    }
}
