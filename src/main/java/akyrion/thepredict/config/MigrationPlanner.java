package akyrion.thepredict.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Proposes a schema 2 conversion or a move into AIConfig without touching the administrator's files
 * or enabling queries (M03.6). Planning is deterministic, so repeating it yields the same proposal.
 */
public final class MigrationPlanner {
    /** Directory below the plugin data folder that receives proposals. */
    public static final String DIRECTORY = "migration";
    /** Name of the report inside each proposal. */
    public static final String REPORT = "report.md";
    private static final String TEMPLATE = "migration/report-template.md";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Creates the planner. */
    public MigrationPlanner() {
    }

    /**
     * Proposes a move when the 0.0.1 location has content and AIConfig is empty. Otherwise it proposes a schema 1
     * conversion of the files already in AIConfig. Neither path moves or deletes an administrator file.
     *
     * @param locations resolved data folders
     * @return the proposal, or {@link MigrationProposal#none()} when nothing needs one
     * @throws IOException when a directory cannot be listed or the report template is missing
     */
    public MigrationProposal plan(ConfigLocations locations) throws IOException {
        Objects.requireNonNull(locations, "locations");
        var proposals = new LocationProposal();
        if (locations.legacyHasContent() && locations.aiConfigEmpty()) {
            return proposals.relocate(locations);
        }
        return proposals.activeSchema(locations);
    }

    /**
     * Writes a proposal to {@code migration/<date-time>/}, never overwriting anything. If an identical
     * proposal already exists, nothing is written and its directory is returned.
     *
     * @param dataFolder plugin data folder
     * @param proposal proposal to write
     * @param time local time that names the new directory
     * @return the directory holding the proposal, or empty when there is nothing to migrate
     * @throws IOException when a file cannot be written
     */
    public Optional<Path> write(Path dataFolder, MigrationProposal proposal, LocalDateTime time) throws IOException {
        Objects.requireNonNull(proposal, "proposal");
        if (proposal.isEmpty()) {
            return Optional.empty();
        }
        var root = dataFolder.resolve(DIRECTORY);
        var existing = findIdentical(root, proposal);
        if (existing.isPresent()) {
            return existing;
        }
        var stamp = STAMP.format(time);
        var target = root.resolve(stamp);
        for (int suffix = 2; Files.exists(target); suffix++) {
            target = root.resolve(stamp + "-" + suffix);
        }
        for (var entry : contents(proposal).entrySet()) {
            var file = target.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        return Optional.of(target);
    }

    private static Optional<Path> findIdentical(Path root, MigrationProposal proposal) throws IOException {
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        List<Path> directories;
        try (var listing = Files.list(root)) {
            directories = listing.filter(Files::isDirectory).sorted().toList();
        }
        var expected = contents(proposal);
        for (var directory : directories) {
            if (expected.equals(readTree(directory))) {
                return Optional.of(directory);
            }
        }
        return Optional.empty();
    }

    private static Map<String, String> readTree(Path directory) throws IOException {
        var contents = new LinkedHashMap<String, String>();
        List<Path> files;
        try (var walk = Files.walk(directory)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        for (var file : files) {
            contents.put(directory.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/"),
                    Files.readString(file, StandardCharsets.UTF_8));
        }
        return contents;
    }

    private static Map<String, String> contents(MigrationProposal proposal) {
        var contents = new LinkedHashMap<>(proposal.files());
        contents.put(REPORT, proposal.report());
        return contents;
    }

    static String render(Map<String, String> files, MigrationNotes notes) throws IOException {
        String template;
        try (InputStream resource = MigrationPlanner.class.getClassLoader().getResourceAsStream(TEMPLATE)) {
            if (resource == null) {
                throw new IOException("Bundled resource is missing: " + TEMPLATE);
            }
            template = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        return template.replace("{{files}}", bullets(files.keySet().stream().map(name -> "`" + name + "`").toList()))
                .replace("{{decisions}}", bullets(notes.decisions()))
                .replace("{{manual}}", bullets(notes.manual()));
    }

    private static String bullets(List<String> lines) {
        return lines.isEmpty() ? "- None." : lines.stream().map(line -> "- " + line).collect(Collectors.joining("\n"));
    }
}
