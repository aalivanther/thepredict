package akyrion.thepredict.adapter.storage;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * The file {@code eval verdict import} reads (M22.5, DOC-08): a path inside the plugin folder, which is the folder of
 * the database file because {@code storage.file} is a bare file name. A relative path is resolved against that folder,
 * never against the working directory of the server. An absolute path elsewhere, a {@code ..} or a link that leads out
 * of the folder is refused with its reason before anything is read.
 */
final class VerdictSource {
    /** The answer to a path that leaves the plugin folder. */
    static final String OUTSIDE = "path: must stay inside the plugin folder";

    private VerdictSource() {
    }

    /**
     * Resolves the path the administrator typed.
     *
     * @param database the database file of the writer
     * @param path the path as typed
     * @return the real path of the file, inside the plugin folder
     * @throws AdminRefusal when the path leaves the plugin folder
     * @throws IOException when the file cannot be reached
     */
    static Path resolve(Path database, String path) throws IOException {
        var folder = database.toAbsolutePath().normalize().getParent();
        if (folder == null) {
            throw new AdminRefusal(OUTSIDE);
        }
        Path given;
        try {
            given = Path.of(path);
        } catch (InvalidPathException exception) {
            throw new AdminRefusal(OUTSIDE);
        }
        var resolved = folder.resolve(given).normalize();
        if (!resolved.startsWith(folder)) {
            throw new AdminRefusal(OUTSIDE);
        }
        var real = resolved.toRealPath();
        if (!real.startsWith(folder.toRealPath())) {
            throw new AdminRefusal(OUTSIDE);
        }
        return real;
    }
}
