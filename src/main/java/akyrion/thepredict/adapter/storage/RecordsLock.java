package akyrion.thepredict.adapter.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The folder lock {@code records/.lock} (M21.3). A second process that cannot take it leaves records and aggregates
 * off.
 */
public final class RecordsLock implements AutoCloseable {
    private final FileChannel channel;

    private RecordsLock(FileChannel channel) {
        this.channel = channel;
    }

    /**
     * Tries to lock the folder.
     *
     * @param directory the records directory
     * @return the lock, empty when another process holds it
     * @throws IOException when the file cannot be created
     */
    public static java.util.Optional<RecordsLock> tryAcquire(Path directory) throws IOException {
        Files.createDirectories(directory);
        var channel = FileChannel.open(directory.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        java.nio.channels.FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException held) {
            channel.close();
            return java.util.Optional.empty();
        }
        if (lock == null) {
            channel.close();
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new RecordsLock(channel));
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
