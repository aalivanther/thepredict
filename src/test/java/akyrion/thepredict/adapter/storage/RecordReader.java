package akyrion.thepredict.adapter.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Reads JSONL records for M22 (M21.10). It accepts plain and gzipped segments, skips a cut last line, rejects
 * {@code v} other than 1, and joins lines by {@code (run, encounter, sequence)}.
 */
public final class RecordReader {
    private RecordReader() {
    }

    /**
     * One parsed line.
     *
     * @param object the JSON object
     */
    public record EventLine(JsonObject object) {
        /**
         * Schema version.
         *
         * @return {@code v}
         */
        public int version() {
            return object.get("v").getAsInt();
        }

        /**
         * Event type.
         *
         * @return the type
         */
        public String type() {
            return object.get("type").getAsString();
        }

        /**
         * Run id.
         *
         * @return the run
         */
        public String run() {
            return object.get("run").getAsString();
        }

        /**
         * Operator tag, or empty.
         *
         * @return the tag
         */
        public String tag() {
            return object.has("tag") && !object.get("tag").isJsonNull() ? object.get("tag").getAsString() : "";
        }

        /**
         * Encounter, or 0 when the line has none.
         *
         * @return the encounter
         */
        public long encounter() {
            return object.has("encounter") ? object.get("encounter").getAsLong() : 0L;
        }

        /**
         * Sequence, or {@link Long#MIN_VALUE} when the line has none.
         *
         * @return the sequence
         */
        public long sequence() {
            return object.has("sequence") ? object.get("sequence").getAsLong() : Long.MIN_VALUE;
        }

        /**
         * Time to kill, or 0 when the line has none.
         *
         * @return the ttk
         */
        public long ttk() {
            return object.has("ttk") ? object.get("ttk").getAsLong() : 0L;
        }
    }

    /**
     * Lines of one request.
     *
     * @param run run id
     * @param encounter encounter
     * @param sequence sequence
     * @param lines lines in file order
     */
    public record Joined(String run, long encounter, long sequence, List<EventLine> lines) {
    }

    /**
     * Reads a {@code .jsonl} or {@code .jsonl.gz} file.
     *
     * @param file the file
     * @return the lines
     * @throws IOException when the file cannot be read
     */
    public static List<EventLine> read(Path file) throws IOException {
        if (file.getFileName().toString().endsWith(".gz")) {
            try (var gzip = new GZIPInputStream(Files.newInputStream(file));
                 var reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
                var text = new StringBuilder();
                var buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    text.append(buffer, 0, count);
                }
                return parse(List.of(text.toString().split("\n", -1)));
            }
        }
        return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    /**
     * Parses lines. A broken last line is skipped. Any other broken line, or {@code v} other than 1, fails.
     *
     * @param lines the text lines
     * @return the events
     */
    public static List<EventLine> parse(List<String> lines) {
        var parsed = new ArrayList<EventLine>();
        for (var index = 0; index < lines.size(); index++) {
            var text = lines.get(index);
            if (text.isBlank()) {
                continue;
            }
            JsonObject object;
            try {
                object = JsonParser.parseString(text).getAsJsonObject();
            } catch (RuntimeException exception) {
                if (index == lines.size() - 1) {
                    break;
                }
                throw exception;
            }
            if (!object.has("v") || object.get("v").getAsInt() != 1) {
                throw new IllegalArgumentException("v");
            }
            parsed.add(new EventLine(object));
        }
        return parsed;
    }

    /**
     * Joins decision lines by run, encounter and sequence. Lines without a sequence stay out.
     *
     * @param lines the lines
     * @return one group per key, in first-seen order
     */
    public static List<Joined> join(List<EventLine> lines) {
        var groups = new LinkedHashMap<String, List<EventLine>>();
        var order = new ArrayList<String>();
        for (var line : lines) {
            if (line.sequence() == Long.MIN_VALUE || line.encounter() == 0L) {
                continue;
            }
            var key = line.run() + "|" + line.encounter() + "|" + line.sequence();
            if (groups.putIfAbsent(key, new ArrayList<>()) == null) {
                order.add(key);
            }
            groups.get(key).add(line);
        }
        var joined = new ArrayList<Joined>();
        for (var key : order) {
            var sample = groups.get(key).getFirst();
            joined.add(new Joined(sample.run(), sample.encounter(), sample.sequence(), List.copyOf(groups.get(key))));
        }
        return joined;
    }

    /**
     * Groups by tag.
     *
     * @param lines the lines
     * @return tag to lines
     */
    public static Map<String, List<EventLine>> byTag(List<EventLine> lines) {
        var groups = new LinkedHashMap<String, List<EventLine>>();
        for (var line : lines) {
            groups.computeIfAbsent(line.tag(), ignored -> new ArrayList<>()).add(line);
        }
        return groups;
    }

    /**
     * Groups by run.
     *
     * @param lines the lines
     * @return run to lines
     */
    public static Map<String, List<EventLine>> byRun(List<EventLine> lines) {
        var groups = new LinkedHashMap<String, List<EventLine>>();
        for (var line : lines) {
            groups.computeIfAbsent(line.run(), ignored -> new ArrayList<>()).add(line);
        }
        return groups;
    }
}
