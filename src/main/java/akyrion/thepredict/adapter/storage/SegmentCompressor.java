package akyrion.thepredict.adapter.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPOutputStream;

/**
 * Gzips a closed segment in 1 MiB steps and deletes the original only after the gzip closes (M21.3). A job that fails
 * or is abandoned closes both of its streams and deletes its {@code .gz.partial}; the original stays. A
 * {@code .gz} left by an earlier attempt is replaced.
 */
public final class SegmentCompressor {
    /** Bytes of one step. */
    public static final int CHUNK = 1024 * 1024;

    private SegmentCompressor() {
    }

    /**
     * One compression in progress. A failure leaves the original in place.
     */
    public static final class Job {
        private final Path source;
        private final Path partial;
        private final InputStream input;
        private final OutputStream sink;
        private final GZIPOutputStream gzip;
        private final byte[] buffer = new byte[CHUNK];
        private boolean finished;

        private Job(Path source, Path partial, InputStream input, OutputStream sink) throws IOException {
            this.source = source;
            this.partial = partial;
            this.input = input;
            this.sink = sink;
            this.gzip = new GZIPOutputStream(sink);
        }

        /**
         * Copies one chunk.
         *
         * @return {@code true} when the gzip is complete and the original is gone
         * @throws IOException when the copy fails; the streams are closed, the partial file is deleted and the
         *         original remains
         */
        public boolean step() throws IOException {
            try {
                var read = input.read(buffer);
                if (read < 0) {
                    input.close();
                    gzip.close();
                    Files.move(partial, gzipPath(source), StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(source);
                    finished = true;
                    return true;
                }
                gzip.write(buffer, 0, read);
                return false;
            } catch (IOException exception) {
                abandon();
                throw exception;
            }
        }

        /**
         * Stops the job: closes both streams and deletes the partial file. The original stays. Idempotent.
         */
        public void abandon() {
            closeQuietly(input);
            closeQuietly(gzip);
            closeQuietly(sink);
            deleteQuietly(partial);
        }

        /**
         * Whether the original was deleted.
         *
         * @return {@code true} after a successful last step
         */
        public boolean finished() {
            return finished;
        }
    }

    /**
     * Starts a job. The partial file is {@code name.jsonl.gz.partial}.
     *
     * @param source the closed segment
     * @return the job
     * @throws IOException when the files cannot be opened
     */
    public static Job start(Path source) throws IOException {
        var input = Files.newInputStream(source);
        try {
            return start(source, input, Files.newOutputStream(partial(source)));
        } catch (IOException exception) {
            closeQuietly(input);
            throw exception;
        }
    }

    /**
     * Starts a job over the given streams, for a failure injected by a test.
     *
     * @param source the closed segment
     * @param input the source bytes
     * @param output the partial destination
     * @return the job
     * @throws IOException when the gzip header cannot be written
     */
    public static Job start(Path source, InputStream input, OutputStream output) throws IOException {
        var sink = new BufferedOutputStream(output);
        try {
            return new Job(source, partial(source), new BufferedInputStream(input), sink);
        } catch (IOException exception) {
            closeQuietly(input);
            closeQuietly(sink);
            throw exception;
        }
    }

    /**
     * Compresses a file in full.
     *
     * @param source the closed segment
     * @throws IOException when the copy fails; the original remains
     */
    public static void compress(Path source) throws IOException {
        var job = start(source);
        while (!job.step()) {
            continue;
        }
    }

    /**
     * Deletes every {@code .gz.partial} under the events folder: what a compression stopped by a crash left.
     *
     * @param events the {@code events} folder
     * @return files deleted
     * @throws IOException when the folder cannot be walked
     */
    public static int deletePartials(Path events) throws IOException {
        if (!Files.isDirectory(events)) {
            return 0;
        }
        var deleted = 0;
        try (var walk = Files.walk(events)) {
            for (var path : walk.filter(file -> file.getFileName().toString().endsWith(".gz.partial")).toList()) {
                if (Files.deleteIfExists(path)) {
                    deleted++;
                }
            }
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
        return deleted;
    }

    private static void closeQuietly(java.io.Closeable stream) {
        try {
            stream.close();
        } catch (IOException exception) {
            return;
        }
    }

    private static boolean deleteQuietly(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException exception) {
            return false;
        }
    }

    private static Path partial(Path source) {
        return source.resolveSibling(source.getFileName() + ".gz.partial");
    }

    private static Path gzipPath(Path source) {
        return source.resolveSibling(source.getFileName() + ".gz");
    }
}
