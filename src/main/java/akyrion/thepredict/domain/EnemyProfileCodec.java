package akyrion.thepredict.domain;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Binary and JSON forms of {@link EnemyProfileSummary} (M26.9). Binary is big-endian. Counts are unsigned LEB128.
 * The reaction mean and its second moment are the only {@code float32} fields. JSON names are {@code snake_case}.
 */
public final class EnemyProfileCodec {
    private EnemyProfileCodec() {
    }

    /**
     * Writes version 1.
     *
     * @param summary the summary
     * @return the bytes
     */
    public static byte[] encode(EnemyProfileSummary summary) {
        var out = new byte[4096];
        var at = new int[1];
        write(out, at, summary.version());
        varints(out, at, summary.actions());
        varints(out, at, summary.episodes());
        varints(out, at, summary.success());
        varints(out, at, summary.failure());
        varints(out, at, summary.trials());
        float32(out, at, summary.reactionMean());
        float32(out, at, summary.reactionM2());
        write(out, at, summary.reactionWindows());
        write(out, at, summary.transitionKey().length);
        var keys = summary.transitionKey();
        var est = summary.transitionEst();
        for (var index = 0; index < keys.length; index++) {
            write(out, at, keys[index]);
            write(out, at, est[index]);
        }
        write(out, at, summary.transitionTotal());
        write(out, at, summary.patternPeaks());
        varints(out, at, summary.bins());
        write(out, at, summary.cadenceHits());
        write(out, at, summary.cadenceIqr());
        return Arrays.copyOf(out, at[0]);
    }

    /**
     * Reads version 1. Any other first byte is rejected.
     *
     * @param bytes the encoded summary
     * @return the summary
     * @throws IllegalArgumentException when the version is unknown or the bytes end early
     */
    public static EnemyProfileSummary decode(byte[] bytes) {
        var at = new int[1];
        var version = read(bytes, at);
        if (version != EnemyProfileSummary.VERSION) {
            throw new IllegalArgumentException("unknown summary version " + version);
        }
        var actions = readInts(bytes, at, EnemyProfileSummary.ACTIONS);
        var episodes = readInts(bytes, at, EnemyProfileSummary.EPISODES);
        var success = readInts(bytes, at, EnemyProfileSummary.BETA);
        var failure = readInts(bytes, at, EnemyProfileSummary.BETA);
        var trials = readInts(bytes, at, EnemyProfileSummary.BETA);
        var mean = readFloat(bytes, at);
        var m2 = readFloat(bytes, at);
        var windows = read(bytes, at);
        var sketch = readSketch(bytes, at);
        var total = read(bytes, at);
        var peaks = read(bytes, at);
        var bins = readInts(bytes, at, EnemyProfileSummary.BINS);
        var hits = read(bytes, at);
        var iqr = read(bytes, at);
        return new EnemyProfileSummary(version, actions, episodes, success, failure, trials, mean, m2, windows,
                sketch.keys(), sketch.est(), total, peaks, bins, hits, iqr);
    }

    private static EnemyProfileMath.Sketch readSketch(byte[] bytes, int[] at) {
        var kept = read(bytes, at);
        if (kept < 0 || kept > EnemyProfileSummary.KEPT) {
            throw new IllegalArgumentException("transition sketch");
        }
        var keys = new int[kept];
        var est = new int[kept];
        for (var index = 0; index < kept; index++) {
            keys[index] = read(bytes, at);
            est[index] = read(bytes, at);
        }
        return new EnemyProfileMath.Sketch(keys, est);
    }

    /**
     * JSON object of the same fields.
     *
     * @param summary the summary
     * @return one JSON object
     */
    public static String toJson(EnemyProfileSummary summary) {
        var json = new StringBuilder(256);
        json.append("{\"version\":").append(summary.version());
        field(json, "actions", summary.actions());
        field(json, "episodes", summary.episodes());
        field(json, "success", summary.success());
        field(json, "failure", summary.failure());
        field(json, "trials", summary.trials());
        json.append(",\"reaction_mean\":").append(summary.reactionMean());
        json.append(",\"reaction_m2\":").append(summary.reactionM2());
        json.append(",\"reaction_windows\":").append(summary.reactionWindows());
        field(json, "transition_key", summary.transitionKey());
        field(json, "transition_est", summary.transitionEst());
        json.append(",\"transition_total\":").append(summary.transitionTotal());
        json.append(",\"pattern_peaks\":").append(summary.patternPeaks());
        field(json, "bins", summary.bins());
        json.append(",\"cadence_hits\":").append(summary.cadenceHits());
        json.append(",\"cadence_iqr\":").append(summary.cadenceIqr()).append('}');
        return json.toString();
    }

    /**
     * Reads JSON written by {@link #toJson}.
     *
     * @param json the object
     * @return the summary
     */
    public static EnemyProfileSummary fromJson(String json) {
        return decode(encode(fromNumbers(json)));
    }

    private static EnemyProfileSummary fromNumbers(String json) {
        return new EnemyProfileSummary(number(json, "version"), array(json, "actions", EnemyProfileSummary.ACTIONS),
                array(json, "episodes", EnemyProfileSummary.EPISODES),
                array(json, "success", EnemyProfileSummary.BETA),
                array(json, "failure", EnemyProfileSummary.BETA),
                array(json, "trials", EnemyProfileSummary.BETA), floatNumber(json, "reaction_mean"),
                floatNumber(json, "reaction_m2"), number(json, "reaction_windows"),
                array(json, "transition_key", count(json, "transition_key")),
                array(json, "transition_est", count(json, "transition_est")), number(json, "transition_total"),
                number(json, "pattern_peaks"), array(json, "bins", EnemyProfileSummary.BINS),
                number(json, "cadence_hits"), number(json, "cadence_iqr"));
    }

    private static void field(StringBuilder json, String name, int[] values) {
        json.append(",\"").append(name).append("\":[");
        for (var index = 0; index < values.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(values[index]);
        }
        json.append(']');
    }

    private static int number(String json, String name) {
        return Integer.parseInt(slice(json, name));
    }

    private static float floatNumber(String json, String name) {
        return Float.parseFloat(slice(json, name));
    }

    private static int[] array(String json, String name, int length) {
        var body = slice(json, name);
        if (length == 0) {
            return new int[0];
        }
        var parts = body.split(",");
        var values = new int[parts.length];
        for (var index = 0; index < parts.length; index++) {
            values[index] = Integer.parseInt(parts[index]);
        }
        return values;
    }

    private static int count(String json, String name) {
        var body = slice(json, name);
        return body.isEmpty() ? 0 : body.split(",").length;
    }

    private static String slice(String json, String name) {
        var key = "\"" + name + "\":";
        var start = json.indexOf(key);
        if (start < 0) {
            throw new IllegalArgumentException(name);
        }
        start += key.length();
        if (json.charAt(start) == '[') {
            var end = json.indexOf(']', start);
            return json.substring(start + 1, end);
        }
        var end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        return json.substring(start, end);
    }

    private static void varints(byte[] out, int[] at, int[] values) {
        for (var value : values) {
            write(out, at, value);
        }
    }

    private static void write(byte[] out, int[] at, int value) {
        var rest = value;
        while ((rest & ~0x7F) != 0) {
            out[at[0]++] = (byte) ((rest & 0x7F) | 0x80);
            rest >>>= 7;
        }
        out[at[0]++] = (byte) rest;
    }

    private static void float32(byte[] out, int[] at, float value) {
        var bits = ByteBuffer.allocate(4).putFloat(value).array();
        System.arraycopy(bits, 0, out, at[0], 4);
        at[0] += 4;
    }

    private static int[] readInts(byte[] bytes, int[] at, int length) {
        var values = new int[length];
        for (var index = 0; index < length; index++) {
            values[index] = read(bytes, at);
        }
        return values;
    }

    private static int read(byte[] bytes, int[] at) {
        var shift = 0;
        var value = 0;
        while (true) {
            if (at[0] >= bytes.length) {
                throw new IllegalArgumentException("truncated summary");
            }
            var piece = bytes[at[0]++] & 0xFF;
            value |= (piece & 0x7F) << shift;
            if ((piece & 0x80) == 0) {
                return value;
            }
            shift += 7;
        }
    }

    private static float readFloat(byte[] bytes, int[] at) {
        if (at[0] > bytes.length - Float.BYTES) {
            throw new IllegalArgumentException("truncated summary");
        }
        var buffer = ByteBuffer.wrap(bytes, at[0], Float.BYTES);
        at[0] += Float.BYTES;
        return buffer.getFloat();
    }
}
