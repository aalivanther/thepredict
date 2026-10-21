package akyrion.thepredict.adapter.server.duel;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Joins the typed lines of a {@code decisions.jsonl} of the duel recorder into one JSON object per request (M18.4):
 * the {@code planned}, {@code body}, {@code result} and {@code acceptance} lines of each
 * {@code (encounter, sequence)}, in file order, whatever other encounters wrote in between. The group of
 * {@code sequence} −1, the {@code local} lines, belongs to no request and is left out. A last line cut short (the
 * server stopped while writing it) is skipped; a broken line anywhere else is an error. No network; the task
 * {@code duelJoin} runs it outside {@code check}. The same with {@code jq}:
 * {@code jq -s 'group_by([.encounter,.sequence]) | map({key: .[0].encounter, seq: .[0].sequence, lines: .})'
 * decisions.jsonl}.
 */
public final class DuelJoin {
    private DuelJoin() {
    }

    /**
     * One request with its lines.
     *
     * @param encounter encounter of the request
     * @param sequence sequence of the request
     * @param lines its lines in file order
     */
    public record Request(long encounter, long sequence, List<JsonObject> lines) {
        /**
         * The types of the lines, in order.
         *
         * @return for example {@code [planned, body, result, acceptance]}
         */
        public List<String> types() {
            return lines.stream().map(line -> line.get("type").getAsString()).toList();
        }
    }

    /**
     * Joins the lines by their key.
     *
     * @param jsonLines the lines of {@code decisions.jsonl}
     * @return one request per key, in the order of its first line, without the local lines
     */
    public static List<Request> join(List<String> jsonLines) {
        var groups = new LinkedHashMap<List<Long>, List<JsonObject>>();
        for (var index = 0; index < jsonLines.size(); index++) {
            var text = jsonLines.get(index);
            if (text.isBlank()) {
                continue;
            }
            JsonObject line;
            try {
                line = JsonParser.parseString(text).getAsJsonObject();
            } catch (JsonParseException | IllegalStateException exception) {
                if (index == jsonLines.size() - 1) {
                    break;
                }
                throw exception;
            }
            var sequence = line.get("sequence").getAsLong();
            if (sequence == DuelDecisionLines.LOCAL_SEQUENCE) {
                continue;
            }
            var key = List.of(line.get("encounter").getAsLong(), sequence);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(line);
        }
        var requests = new ArrayList<Request>();
        groups.forEach((key, lines) -> requests.add(new Request(key.getFirst(), key.getLast(), List.copyOf(lines))));
        return requests;
    }

    /**
     * Writes the joined requests of a recording as a JSON array.
     *
     * @param arguments the {@code decisions.jsonl} to read and the JSON file to write
     * @throws IOException when a file cannot be read or written
     */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: DuelJoin <decisions.jsonl> <output.json>");
        }
        var output = new JsonArray();
        for (var request : join(Files.readAllLines(Path.of(arguments[0]), StandardCharsets.UTF_8))) {
            var joined = new JsonObject();
            joined.addProperty("encounter", request.encounter());
            joined.addProperty("sequence", request.sequence());
            var lines = new JsonArray();
            request.lines().forEach(lines::add);
            joined.add("lines", lines);
            output.add(joined);
        }
        var target = Path.of(arguments[1]).toAbsolutePath();
        Files.createDirectories(target.getParent());
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(output),
                StandardCharsets.UTF_8);
        System.out.println(output.size() + " requests joined into " + target);
    }
}
