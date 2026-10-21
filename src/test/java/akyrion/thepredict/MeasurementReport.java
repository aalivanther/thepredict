package akyrion.thepredict;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Markdown report of the local measurements of M13, written by {@code M13Measurements} outside the build. Only the
 * measurement tasks use it; no test asserts on timings (rule P2).
 */
public final class MeasurementReport {
    private final List<String> lines = new ArrayList<>();

    /** Creates an empty report. */
    public MeasurementReport() {
    }

    /**
     * Starts a section.
     *
     * @param title section title
     */
    public void section(String title) {
        lines.add("");
        lines.add("## " + title);
        lines.add("");
    }

    /**
     * Adds a paragraph.
     *
     * @param text one line of text
     */
    public void line(String text) {
        lines.add(text);
    }

    /**
     * Adds a table.
     *
     * @param header column titles
     * @param rows rows with as many cells as the header
     */
    public void table(List<String> header, List<List<String>> rows) {
        lines.add("| " + String.join(" | ", header) + " |");
        lines.add("|" + "---|".repeat(header.size()));
        rows.forEach(row -> lines.add("| " + String.join(" | ", row) + " |"));
        lines.add("");
    }

    /**
     * Returns the report as Markdown.
     *
     * @return the text
     */
    public String text() {
        return String.join("\n", lines) + "\n";
    }

    /**
     * Formats a number with a fixed number of decimals, independent of the locale.
     *
     * @param value number
     * @param decimals decimals
     * @return the text
     */
    public static String number(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /**
     * Percentiles of wall times, in microseconds: p50, p95, p99 and maximum, sorted by rank.
     *
     * @param nanos samples in nanoseconds
     * @return {@code "p50 / p95 / p99 / max"}
     */
    public static String micros(long[] nanos) {
        if (nanos.length == 0) {
            return "-";
        }
        var sorted = nanos.clone();
        Arrays.sort(sorted);
        return micros(rank(sorted, 0.50)) + " / " + micros(rank(sorted, 0.95)) + " / " + micros(rank(sorted, 0.99))
                + " / " + micros(sorted[sorted.length - 1]);
    }

    /**
     * Mean of wall times in microseconds.
     *
     * @param nanos samples in nanoseconds
     * @return the mean with one decimal
     */
    public static String meanMicros(long[] nanos) {
        return number(Arrays.stream(nanos).average().orElse(0) / 1000.0, 1);
    }

    private static long rank(long[] sorted, double ratio) {
        var index = (int) Math.ceil(ratio * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static String micros(long nanos) {
        return number(nanos / 1000.0, 1);
    }
}
