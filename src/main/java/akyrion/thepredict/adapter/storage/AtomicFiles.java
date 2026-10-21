package akyrion.thepredict.adapter.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Replaces a small file so a crash leaves either the old bytes or the new ones, never a part (T1-ter, M28.9, M29.8):
 * the bytes go to a temporary file in the same folder, which is forced to the disk, moved over the target with
 * {@code ATOMIC_MOVE} and followed by a force of the folder. A file system without atomic moves falls back to a plain
 * replace; a folder that cannot be forced is left as written. Only the storage thread calls this.
 */
final class AtomicFiles {
    private AtomicFiles() {
    }

    /**
     * Writes UTF-8 text.
     *
     * @param target the file to replace
     * @param text the whole new content
     * @throws IOException when the temporary file cannot be written or moved
     */
    static void write(Path target, String text) throws IOException {
        write(target, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes bytes.
     *
     * @param target the file to replace
     * @param bytes the whole new content
     * @throws IOException when the temporary file cannot be written or moved
     */
    static void write(Path target, byte[] bytes) throws IOException {
        var folder = target.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        var temp = folder.resolve(target.getFileName() + ".tmp");
        try (var channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        move(temp, target);
        force(folder);
    }

    private static void move(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean force(Path folder) {
        try (var channel = FileChannel.open(folder, StandardOpenOption.READ)) {
            channel.force(true);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
