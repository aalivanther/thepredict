package akyrion.thepredict.adapter.storage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Names and rotates JSONL segments (M21.3). A segment belongs to one run. Tests replace {@link Opener}.
 */
public final class SegmentFiles {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    /** Opens a segment for append. */
    @FunctionalInterface
    public interface Opener {
        /**
         * Opens the file.
         *
         * @param file the segment
         * @return the writer
         * @throws IOException when the disk refuses
         */
        Writer open(Path file) throws IOException;
    }

    /** The real disk. */
    public static final Opener DISK = file -> new BufferedWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND), 64 * 1024);

    private final Path events;
    private final String run;
    private final Opener opener;
    private final long maxBytes;
    private final long maxNanos;
    private final String prefix;
    private Writer writer;
    private Path current;
    private int index;
    private long bytes;
    private long openedAt = Long.MIN_VALUE;
    private String day = "";
    private final List<Path> closed = new ArrayList<>();

    /**
     * Creates the segments of one run.
     *
     * @param events the {@code events} directory
     * @param run the run id
     * @param opener how a file is opened
     * @param maxBytes rotation size
     * @param maxNanos rotation age
     */
    public SegmentFiles(Path events, String run, Opener opener, long maxBytes, long maxNanos) {
        this(events, run, opener, maxBytes, maxNanos, "events");
    }

    /**
     * Creates the segments of one run and one name prefix ({@code events} or {@code bodies}).
     *
     * @param events the {@code events} directory
     * @param run the run id
     * @param opener how a file is opened
     * @param maxBytes rotation size
     * @param maxNanos rotation age
     * @param prefix file prefix before {@code -<run>-<n>.jsonl}
     */
    public SegmentFiles(Path events, String run, Opener opener, long maxBytes, long maxNanos, String prefix) {
        this.events = events;
        this.run = run;
        this.opener = opener;
        this.maxBytes = maxBytes;
        this.maxNanos = maxNanos;
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    /**
     * Appends one line and rotates when size, age or the UTC day says so.
     *
     * @param line the JSON line without a newline
     * @param epochMs wall time, for the day
     * @param nanos monotonic time, for the age
     * @throws IOException when the disk refuses
     */
    public void append(String line, long epochMs, long nanos) throws IOException {
        var today = DAY.format(Instant.ofEpochMilli(epochMs));
        if (writer == null || bytes >= maxBytes || openedAt != Long.MIN_VALUE && nanos - openedAt >= maxNanos
                || !today.equals(day)) {
            rotate(today, nanos);
        }
        writer.write(line);
        writer.write('\n');
        bytes += line.length() + 1L;
    }

    /**
     * Flushes the open writer.
     *
     * @throws IOException when the flush fails
     */
    public void flush() throws IOException {
        if (writer != null) {
            writer.flush();
        }
    }

    /**
     * Closes the open segment and returns every closed path, including this one.
     *
     * @return closed segments
     * @throws IOException when the close fails
     */
    public List<Path> close() throws IOException {
        closeWriter();
        var copy = List.copyOf(closed);
        closed.clear();
        return copy;
    }

    /**
     * Segments closed by rotation and not yet taken.
     *
     * @return the paths
     */
    public List<Path> pollClosed() {
        var copy = List.copyOf(closed);
        closed.clear();
        return copy;
    }

    /**
     * Closes the open segment after a failed write so the next append starts a new file.
     */
    public void abandon() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException exception) {
            writer = null;
        }
        if (current != null) {
            closed.add(current);
        }
        writer = null;
        current = null;
    }

    private void rotate(String today, long nanos) throws IOException {
        closeWriter();
        day = today;
        var folder = events.resolve(today);
        Files.createDirectories(folder);
        current = folder.resolve(prefix + "-" + run + "-" + index + ".jsonl");
        index++;
        writer = opener.open(current);
        bytes = Files.exists(current) ? Files.size(current) : 0;
        openedAt = nanos;
    }

    private void closeWriter() throws IOException {
        if (writer != null) {
            writer.close();
            closed.add(current);
            writer = null;
        }
    }
}
