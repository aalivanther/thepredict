package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.StorageReply;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Gate verdict files (M22.5). They work without SQLite. One file per candidate and stage, {@code <sha256>-<gate>.json},
 * so the {@code G1} and {@code G2} of one fingerprint live side by side and {@code full} is reachable without SQLite;
 * a file of the older form {@code <sha256>.json} is still read, since every {@code .json} file is read by its content.
 * At most 200 files, each at most 64 KiB. A write goes through {@code AtomicFiles}: a forced temporary file in the same
 * directory and an atomic move. The oldest files are removed first. A file that is not a verdict, such as one cut short
 * by a power loss or edited by hand, is skipped and counted, so the other verdicts still load: the fingerprint is 64
 * lowercase hex digits, the gate is {@code G0} to {@code G4}, the verdict is {@code PASS}, {@code PASS_SIM_ONLY},
 * {@code FAIL} or {@code INCONCLUSIVE} and the run id is 1 to 64 letters, digits, {@code .}, {@code _}, {@code :} or
 * {@code -}.
 */
public final class VerdictFiles {
    /** Most verdict files kept. */
    public static final int MAX_FILES = 200;
    /** Largest accepted file. */
    public static final int MAX_BYTES = 64 * 1024;
    /** Largest file a report or an import may read. */
    public static final long READ_CAP_BYTES = 512L * 1024L * 1024L;

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern GATE = Pattern.compile("G[0-4]");
    private static final Pattern RUN = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Set<String> VERDICTS = Set.of("PASS", "PASS_SIM_ONLY", "FAIL", "INCONCLUSIVE");

    private VerdictFiles() {
    }

    /**
     * What a read found.
     *
     * @param rows the valid verdicts
     * @param skipped files that were too large or not a verdict
     */
    public record Loaded(List<StorageReply.GateVerdictRow> rows, int skipped) {
        /**
         * Copies the rows.
         */
        public Loaded {
            rows = List.copyOf(rows);
        }
    }

    /**
     * Validates one verdict file and installs it with a temp file, an atomic move and a prune.
     *
     * @param source the file to import
     * @param directory the verdict directory
     * @return the installed row
     * @throws IOException when the file is too large or the write fails
     * @throws IllegalArgumentException when the path climbs or the JSON is not a verdict
     */
    public static StorageReply.GateVerdictRow importFile(Path source, Path directory) throws IOException {
        if (source.toString().indexOf("..") >= 0) {
            throw new IllegalArgumentException("path");
        }
        if (Files.size(source) > MAX_BYTES) {
            throw new IOException("verdict larger than 64KiB");
        }
        var row = parse(Files.readString(source));
        write(directory, row);
        return row;
    }

    /**
     * Writes one verdict to {@code <sha256>-<gate>.json} and prunes the directory down to {@link #MAX_FILES}. Another
     * stage of the same candidate keeps its own file.
     *
     * @param directory the verdict directory
     * @param row the verdict
     * @throws IOException when the write fails
     * @throws IllegalArgumentException when the row is not a valid verdict
     */
    public static void write(Path directory, StorageReply.GateVerdictRow row) throws IOException {
        require(row);
        Files.createDirectories(directory);
        var body = body(row);
        if (body.length() > MAX_BYTES) {
            throw new IOException("verdict larger than 64KiB");
        }
        AtomicFiles.write(directory.resolve(fileName(row)), body);
        prune(directory);
    }

    /**
     * The file of one verdict: {@code <sha256>-<gate>.json}.
     *
     * @param row a valid verdict
     * @return the file name
     */
    static String fileName(StorageReply.GateVerdictRow row) {
        return row.sha256() + "-" + row.gate() + ".json";
    }

    /**
     * Reads every valid verdict file. The order of the rows is not defined.
     *
     * @param directory the verdict directory
     * @return the rows; empty when the directory is absent
     * @throws IOException when the directory cannot be listed
     */
    public static List<StorageReply.GateVerdictRow> read(Path directory) throws IOException {
        return load(directory).rows();
    }

    /**
     * Reads every verdict file and counts the ones skipped.
     *
     * @param directory the verdict directory
     * @return the valid rows and the count of skipped files
     * @throws IOException when the directory cannot be listed
     */
    public static Loaded load(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return new Loaded(List.of(), 0);
        }
        var rows = new ArrayList<StorageReply.GateVerdictRow>();
        var skipped = 0;
        try (var stream = Files.list(directory)) {
            for (var file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                var row = readOne(file);
                if (row.isPresent()) {
                    rows.add(row.get());
                } else {
                    skipped++;
                }
            }
        }
        return new Loaded(rows, skipped);
    }

    private static java.util.Optional<StorageReply.GateVerdictRow> readOne(Path file) {
        try {
            if (Files.size(file) > MAX_BYTES) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(parse(Files.readString(file)));
        } catch (IOException | RuntimeException exception) {
            return java.util.Optional.empty();
        }
    }

    private static void prune(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            var files = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(VerdictFiles::modified)).toList();
            var extra = files.size() - MAX_FILES;
            for (var index = 0; index < extra; index++) {
                Files.deleteIfExists(files.get(index));
            }
        }
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static String body(StorageReply.GateVerdictRow row) {
        return "{\"sha256\":\"" + row.sha256() + "\",\"gate\":\"" + row.gate() + "\",\"verdict\":\""
                + row.verdict() + "\",\"run_id\":\"" + row.runId() + "\",\"decided_epoch_ms\":"
                + row.decidedEpochMs() + "}\n";
    }

    private static StorageReply.GateVerdictRow parse(String text) {
        var row = new StorageReply.GateVerdictRow(field(text, "sha256"), field(text, "gate"), field(text, "verdict"),
                field(text, "run_id"), Long.parseLong(field(text, "decided_epoch_ms")));
        require(row);
        return row;
    }

    private static void require(StorageReply.GateVerdictRow row) {
        if (!SHA256.matcher(row.sha256()).matches() || !GATE.matcher(row.gate()).matches()
                || !VERDICTS.contains(row.verdict()) || !RUN.matcher(row.runId()).matches()
                || row.decidedEpochMs() < 0) {
            throw new IllegalArgumentException("verdict");
        }
    }

    private static String field(String text, String name) {
        var key = "\"" + name + "\":";
        var at = text.indexOf(key);
        if (at < 0) {
            throw new IllegalArgumentException(name);
        }
        var start = at + key.length();
        if (start < text.length() && text.charAt(start) == '"') {
            var end = text.indexOf('"', start + 1);
            if (end < 0) {
                throw new IllegalArgumentException(name);
            }
            return text.substring(start + 1, end);
        }
        var end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        return text.substring(start, end);
    }
}
