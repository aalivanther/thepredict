package akyrion.thepredict.adapter.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Copies JSONL events of a day range into {@code records/exports} (M21.9). The export stops at 512 MiB, counting the
 * bytes already written. Gzipped segments are read, not copied compressed. Lines are written as they are read, so the
 * heap holds one line at a time, not the whole export.
 */
public final class EventExport {
    /** Cap of one export, including what a gzipped source expands into the output. */
    public static final long MAX_BYTES = 512L * 1024 * 1024;

    private EventExport() {
    }

    /**
     * Exports events. {@code argument} is {@code from to} and optional type names.
     *
     * @param records the records directory
     * @param argument the argument from the command
     * @return a short result
     * @throws IOException when a file cannot be read or written
     */
    public static String write(Path records, String argument) throws IOException {
        var words = argument == null ? List.<String>of() : List.of(argument.trim().split("\\s+"));
        if (words.size() < 2 || !words.get(0).matches("\\d{4}-\\d{2}-\\d{2}")
                || !words.get(1).matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IOException("usage");
        }
        var from = words.get(0);
        var to = words.get(1);
        var types = words.size() > 2 ? words.subList(2, words.size()) : List.<String>of();
        var exports = records.resolve("exports");
        Files.createDirectories(exports);
        var name = "events-" + from + "-" + to + ".jsonl";
        var root = records.resolve("events");
        try (var out = Files.newBufferedWriter(exports.resolve(name), StandardCharsets.UTF_8)) {
            var sink = new Sink(out);
            if (Files.isDirectory(root)) {
                try (var days = Files.list(root)) {
                    for (var day : days.filter(Files::isDirectory).sorted().toList()) {
                        var label = day.getFileName().toString();
                        if (label.compareTo(from) >= 0 && label.compareTo(to) <= 0 && !readDay(day, types, sink)) {
                            break;
                        }
                    }
                }
            }
            return "events " + sink.lines + " " + name + (sink.capped ? " capped" : "");
        }
    }

    /** Copies the kept lines of one day; {@code false} once the cap is reached. */
    private static boolean readDay(Path day, List<String> types, Sink sink) throws IOException {
        try (var files = Files.list(day)) {
            for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
                var name = file.getFileName().toString();
                if (!name.endsWith(".jsonl") && !name.endsWith(".jsonl.gz")) {
                    continue;
                }
                try (var reader = reader(file, name.endsWith(".gz"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (kept(line, types) && !sink.add(line)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean kept(String line, List<String> types) {
        if (types.isEmpty()) {
            return true;
        }
        for (var type : types) {
            if (line.contains("\"type\":\"" + type + "\"")) {
                return true;
            }
        }
        return false;
    }

    private static BufferedReader reader(Path file, boolean gzip) throws IOException {
        var input = Files.newInputStream(file);
        if (!gzip) {
            return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(input), StandardCharsets.UTF_8));
    }

    /** The open export and what it holds so far. */
    private static final class Sink {
        private final BufferedWriter out;
        private long bytes;
        private int lines;
        private boolean capped;

        private Sink(BufferedWriter out) {
            this.out = out;
        }

        /** Writes one line; {@code false} when it would pass the cap, which then stops the export. */
        private boolean add(String line) throws IOException {
            if (bytes + line.length() + 1 > MAX_BYTES) {
                capped = true;
                return false;
            }
            out.write(line);
            out.newLine();
            bytes += line.length() + 1L;
            lines++;
            return true;
        }
    }
}
