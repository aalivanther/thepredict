package akyrion.thepredict.adapter.server.duel;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The one writer thread of a duel recording (rule T1-bis of 00 §5, M18.4): a daemon thread named
 * {@value #THREAD_NAME}, alive only while its recording runs, takes immutable text rows from a queue of
 * {@link #QUEUE_CAPACITY} and writes them with a buffer into the folder of the recording. It never calls Bukkit,
 * MythicMobs or {@code engine} and reads no mutable state but its own. {@link #offer} never blocks: a full queue drops the
 * row and counts it. A file that would pass {@link #MAX_FILE_BYTES} or a recording that would pass
 * {@link #MAX_RECORDING_BYTES} stops the writing of rows and reports the limit, which the recorder reads on the server
 * thread to stop by itself. {@link #finish} writes what is queued, creates every file not written yet with its header,
 * then {@code summary.md}, and ends the thread. The same pattern as {@code adapter.jev.ScenarioRecorder}.
 */
public final class DuelWriter {
    /** Name of the writer thread. */
    public static final String THREAD_NAME = "ThePredict-DuelWriter";
    /** Rows waiting to be written; one more is dropped and counted. */
    public static final int QUEUE_CAPACITY = 8192;
    /** Largest file of a recording, 128 MB. */
    public static final long MAX_FILE_BYTES = 128L * 1024 * 1024;
    /** Largest recording, 512 MB. */
    public static final long MAX_RECORDING_BYTES = 512L * 1024 * 1024;
    /** Limit text of a file that is full. */
    public static final String FILE_LIMIT = "128 MB per file";
    /** Limit text of a recording that is full. */
    public static final String RECORDING_LIMIT = "512 MB per recording";
    /** Longest wait of the thread for a row before it flushes and looks for the end. */
    static final long POLL_MILLIS = 100;

    /** Opens a new file of the recording; the tests replace it with a slow or failing disk. */
    @FunctionalInterface
    interface Opener {
        Writer open(Path file) throws IOException;
    }

    private record Row(DuelFile file, String text) {
    }

    private final Path directory;
    private final ArrayBlockingQueue<Row> queue;
    private final long maxFileBytes;
    private final long maxRecordingBytes;
    private final Opener opener;
    private final Thread thread;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong skippedByLimit = new AtomicLong();
    private final AtomicLong bytes = new AtomicLong();
    private final AtomicReference<String> limit = new AtomicReference<>();
    private final AtomicReference<String> failure = new AtomicReference<>();
    private final Map<DuelFile, Writer> writers = new EnumMap<>(DuelFile.class);
    private final Map<DuelFile, Long> fileBytes = new EnumMap<>(DuelFile.class);
    private volatile boolean finishing;
    private volatile String summary = "";

    private DuelWriter(Path directory, int capacity, long maxFileBytes, long maxRecordingBytes, Opener opener) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.maxFileBytes = maxFileBytes;
        this.maxRecordingBytes = maxRecordingBytes;
        this.opener = Objects.requireNonNull(opener, "opener");
        this.thread = new Thread(this::run, THREAD_NAME);
        thread.setDaemon(true);
    }

    /**
     * Creates the writer of a new folder without starting its thread; rows may already be queued. The recorder calls
     * {@link #begin} last, once the rest of the recording exists, so a failed start never leaves a thread behind.
     *
     * @param directory folder of the recording, which must not exist yet; the thread creates it with its parents
     * @return the writer, not started
     */
    public static DuelWriter create(Path directory) {
        return new DuelWriter(directory, QUEUE_CAPACITY, MAX_FILE_BYTES, MAX_RECORDING_BYTES, DuelWriter::openNew);
    }

    /**
     * Creates and starts the writer of a new folder.
     *
     * @param directory folder of the recording, which must not exist yet
     * @return the running writer
     */
    public static DuelWriter start(Path directory) {
        var writer = create(directory);
        writer.begin();
        return writer;
    }

    static DuelWriter start(Path directory, int capacity, long maxFileBytes, long maxRecordingBytes, Opener opener) {
        var writer = new DuelWriter(directory, capacity, maxFileBytes, maxRecordingBytes, opener);
        writer.begin();
        return writer;
    }

    /** Starts the writer thread; does nothing when it already started. */
    public void begin() {
        if (thread.getState() == Thread.State.NEW) {
            thread.start();
        }
    }

    /**
     * Queues one row; returns at once and never throws.
     *
     * @param file file of the row
     * @param line text of the row, without its line break
     * @return {@code false} when the queue was full or the writer is finishing; the row is counted as dropped
     */
    public boolean offer(DuelFile file, String line) {
        if (finishing || !queue.offer(new Row(file, line))) {
            dropped.incrementAndGet();
            return false;
        }
        return true;
    }

    /**
     * Writes what is queued, then {@code summary.md}, and ends the thread; later rows are dropped. Returns at once.
     *
     * @param summaryText content of {@code summary.md}
     */
    public void finish(String summaryText) {
        summary = Objects.requireNonNull(summaryText, "summaryText");
        finishing = true;
    }

    /**
     * Waits for the thread to end after {@link #finish}, at most {@code millis} milliseconds.
     *
     * @param millis longest wait
     * @return whether the thread ended; when not, it keeps writing on its own as a daemon
     */
    public boolean awaitStopped(long millis) {
        try {
            thread.join(Math.max(1, millis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return !thread.isAlive();
    }

    /**
     * Tells whether the writer thread still runs.
     *
     * @return {@code true} until the thread ended
     */
    public boolean running() {
        return thread.isAlive();
    }

    /**
     * Counts the rows dropped because the queue was full or the writer was finishing.
     *
     * @return dropped rows
     */
    public long dropped() {
        return dropped.get();
    }

    /**
     * Counts the rows not written because a size limit was reached.
     *
     * @return rows skipped by a limit
     */
    public long skippedByLimit() {
        return skippedByLimit.get();
    }

    /**
     * Counts the bytes written, headers included.
     *
     * @return bytes written
     */
    public long bytesWritten() {
        return bytes.get();
    }

    /**
     * Reports the size limit reached, if any.
     *
     * @return {@link #FILE_LIMIT} or {@link #RECORDING_LIMIT}
     */
    public Optional<String> limitReached() {
        return Optional.ofNullable(limit.get());
    }

    /**
     * Reports a failed write, if any; nothing more is written after one.
     *
     * @return the simple name of the exception
     */
    public Optional<String> failure() {
        return Optional.ofNullable(failure.get());
    }

    private void run() {
        try {
            Files.createDirectories(directory.getParent());
            Files.createDirectory(directory);
            while (!finishing) {
                var row = queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (row == null) {
                    flush();
                } else {
                    write(row);
                }
            }
            for (var row = queue.poll(); row != null; row = queue.poll()) {
                write(row);
            }
            for (var file : DuelFile.values()) {
                if (file != DuelFile.SUMMARY) {
                    writer(file);
                }
            }
            writer(DuelFile.SUMMARY).write(summary);
            bytes.addAndGet(summary.getBytes(StandardCharsets.UTF_8).length);
        } catch (IOException | RuntimeException exception) {
            failure.compareAndSet(null, exception.getClass().getSimpleName());
        } catch (InterruptedException exception) {
            failure.compareAndSet(null, "InterruptedException");
        } finally {
            closeAll();
        }
    }

    private void write(Row row) throws IOException {
        if (limit.get() != null) {
            skippedByLimit.incrementAndGet();
            return;
        }
        var length = row.text().getBytes(StandardCharsets.UTF_8).length + 1L;
        var output = writer(row.file());
        if (fileBytes.get(row.file()) + length > maxFileBytes) {
            reached(FILE_LIMIT);
        } else if (bytes.get() + length > maxRecordingBytes) {
            reached(RECORDING_LIMIT);
        } else {
            output.write(row.text());
            output.write('\n');
            fileBytes.merge(row.file(), length, Long::sum);
            bytes.addAndGet(length);
        }
    }

    private void reached(String reason) {
        limit.compareAndSet(null, reason);
        skippedByLimit.incrementAndGet();
    }

    /** The writer of a file, opened with its header the first time. */
    private Writer writer(DuelFile file) throws IOException {
        var open = writers.get(file);
        if (open != null) {
            return open;
        }
        var created = opener.open(directory.resolve(file.fileName()));
        writers.put(file, created);
        fileBytes.put(file, 0L);
        var header = file.header();
        if (header.isPresent()) {
            created.write(header.get());
            created.write('\n');
            var length = header.get().getBytes(StandardCharsets.UTF_8).length + 1L;
            fileBytes.put(file, length);
            bytes.addAndGet(length);
        }
        return created;
    }

    private void flush() throws IOException {
        for (var writer : writers.values()) {
            writer.flush();
        }
    }

    private void closeAll() {
        for (var writer : writers.values()) {
            try {
                writer.close();
            } catch (IOException exception) {
                failure.compareAndSet(null, exception.getClass().getSimpleName());
            }
        }
        writers.clear();
    }

    private static BufferedWriter openNew(Path file) throws IOException {
        return Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }
}
