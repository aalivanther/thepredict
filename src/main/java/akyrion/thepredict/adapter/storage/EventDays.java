package akyrion.thepredict.adapter.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The day folders of {@code records/events} on disk (M21.3): their sizes, for {@link RetentionPolicy}, and their
 * deletion. A file that disappears during the walk counts as empty instead of failing it. Only the storage thread
 * calls this.
 */
final class EventDays {
    private EventDays() {
    }

    /**
     * The size of every day folder.
     *
     * @param root the {@code events} folder
     * @return one entry per folder; empty when the folder is absent
     * @throws IOException when a folder cannot be listed
     */
    static List<RetentionPolicy.DaySize> sizes(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        var days = new ArrayList<RetentionPolicy.DaySize>();
        try (var stream = Files.list(root)) {
            for (var day : stream.filter(Files::isDirectory).toList()) {
                days.add(new RetentionPolicy.DaySize(day.getFileName().toString(), bytes(day)));
            }
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
        return days;
    }

    /**
     * Deletes a folder and everything in it. An absent folder is left alone.
     *
     * @param folder the folder
     * @throws IOException when a file cannot be deleted
     */
    static void delete(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (var walk = Files.walk(folder)) {
            for (var path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private static long bytes(Path day) throws IOException {
        var total = 0L;
        try (var files = Files.walk(day)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                total += size(file);
            }
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
        return total;
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            return 0L;
        }
    }
}
