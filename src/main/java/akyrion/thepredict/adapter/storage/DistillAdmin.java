package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.LearnPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * File commands of {@code /thepredict distill} (M29.6). Training is not executed. Only the storage thread calls this.
 * {@code distill build} is not here: the writer reads the table page by page as a job ({@link DistillTableChunk}).
 */
public final class DistillAdmin {
    private DistillAdmin() {
    }

    /**
     * Copies one artifact into {@code learn/distill/export}.
     *
     * @param records the records directory
     * @param file the file name
     * @return the note
     * @throws IOException when the file is missing or the copy fails
     */
    public static String export(Path records, String file) throws IOException {
        LearnPath.requireName(file);
        var source = root(records).resolve("artifacts").resolve(file);
        if (!Files.isRegularFile(source)) {
            throw new IOException("missing");
        }
        var export = root(records).resolve("export");
        Files.createDirectories(export);
        Files.copy(source, export.resolve(file), StandardCopyOption.REPLACE_EXISTING);
        return "exported " + file;
    }

    /**
     * Copies one exported file back into {@code learn/distill/artifacts}.
     *
     * @param records the records directory
     * @param file the file name
     * @return the note
     * @throws IOException when the file is missing or the copy fails
     */
    public static String importFile(Path records, String file) throws IOException {
        LearnPath.requireName(file);
        var source = root(records).resolve("export").resolve(file);
        if (!Files.isRegularFile(source)) {
            throw new IOException("missing");
        }
        var artifacts = root(records).resolve("artifacts");
        Files.createDirectories(artifacts);
        Files.copy(source, artifacts.resolve(file), StandardCopyOption.REPLACE_EXISTING);
        return "imported " + file;
    }

    /**
     * Restores the previous active name when one was saved. It does not train.
     *
     * @param records the records directory
     * @return the note
     * @throws IOException when the previous file is missing
     */
    public static String rollback(Path records) throws IOException {
        var root = root(records);
        var previous = root.resolve("previous.txt");
        if (!Files.isRegularFile(previous)) {
            throw new IOException("missing");
        }
        var name = Files.readString(previous).trim();
        LearnPath.requireName(name);
        Files.writeString(root.resolve("active.txt"), name);
        return "rolled " + name;
    }

    private static Path root(Path records) {
        return records.resolveSibling("learn").resolve("distill");
    }
}
