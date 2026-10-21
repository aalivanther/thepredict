package akyrion.thepredict.adapter.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunks an eval report (M22.11). A chunk is at most 1 MiB. A second report is rejected while one is running. A path
 * that contains {@code ..} is rejected. Reads stop at 512 MiB.
 */
public final class EvalReportTask {
    /** Largest chunk. */
    public static final int CHUNK_BYTES = 1024 * 1024;

    private EvalReportTask() {
    }

    /**
     * Splits text into chunks of at most {@code maxBytes} characters.
     *
     * @param text the report
     * @param maxBytes the cap
     * @return the chunks
     */
    public static List<String> chunks(String text, int maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes");
        }
        if (text.length() > VerdictFiles.READ_CAP_BYTES) {
            throw new IllegalArgumentException("read cap");
        }
        var parts = new ArrayList<String>();
        for (var at = 0; at < text.length(); at += maxBytes) {
            parts.add(text.substring(at, Math.min(text.length(), at + maxBytes)));
        }
        return List.copyOf(parts);
    }

    /**
     * Rejects a second report and a path that climbs.
     *
     * @param busy whether a report is already running
     * @param path the requested path
     */
    public static void admit(boolean busy, String path) {
        if (busy) {
            throw new IllegalStateException("report");
        }
        if (path.contains("..")) {
            throw new IllegalArgumentException("path");
        }
    }
}
