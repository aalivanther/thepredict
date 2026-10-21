package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.learn.ArtifactFormat;
import akyrion.thepredict.domain.learn.ValueArtifactFormat;
import akyrion.thepredict.engine.AdminCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Artifact commands of {@code /thepredict learn} (M28.9). Only the storage thread calls this. {@code learn build} is
 * not here: the writer runs it page by page as a job ({@code LearnBuildJob}), so it never blocks a batch for the whole
 * table. An import is read and checked as a value artifact before it is copied.
 */
public final class LearnAdmin {
    private LearnAdmin() {
    }

    /**
     * Runs one learn command other than {@code build}.
     *
     * @param command the command
     * @param records the records directory; artifacts live in its sibling {@code learn}
     * @param write the writable connection
     * @param read the read connection
     * @return a short result that names the writer thread
     * @throws IOException when a file operation fails
     * @throws SQLException when SQL fails
     * @throws IllegalArgumentException for {@code learn build}, which runs as a writer job, or an import that is not
     *         a value artifact
     */
    public static String run(AdminCommand command, Path records, Connection write, Connection read)
            throws IOException, SQLException {
        var root = records.resolveSibling("learn");
        var detail = switch (command) {
            case AdminCommand.LearnExport export -> export(root, export.sha256(), export.file());
            case AdminCommand.LearnImport imported -> importFile(root, required(write), imported.path());
            case AdminCommand.LearnRollback ignored -> rollback(root);
            case AdminCommand.LearnDisable disable -> disable(root, disable.kind());
            default -> throw new IllegalArgumentException("learn");
        };
        return detail + " " + Thread.currentThread().getName();
    }

    private static String export(Path root, String sha, String file) throws IOException {
        var source = root.resolve("artifacts").resolve(sha + ".tplv");
        if (!Files.isRegularFile(source)) {
            throw new IOException("missing");
        }
        var export = root.resolve("export");
        Files.createDirectories(export);
        Files.copy(source, export.resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return "exported " + sha;
    }

    private static String importFile(Path root, Connection write, String file) throws IOException, SQLException {
        var source = root.resolve("export").resolve(file);
        if (!Files.isRegularFile(source)) {
            source = root.resolve("import").resolve(file);
        }
        var bytes = Files.readAllBytes(source);
        var artifact = ValueArtifactFormat.read(bytes);
        var sha = ArtifactFormat.sha256(bytes);
        AtomicFiles.write(root.resolve("artifacts").resolve(sha + ".tplv"), bytes);
        ArtifactSql.insertIgnore(write, sha, "VALUE", bytes.length, artifact.cells().size(), artifact.batch(),
                (long) artifact.day() * 86_400_000L, "PENDING_VERDICT");
        return "imported " + sha;
    }

    private static String rollback(Path root) throws IOException {
        var file = root.resolve("active.json");
        var next = LearnActive.read(file).swapped();
        next.write(file);
        return "rolled " + next.value();
    }

    private static String disable(Path root, String kind) throws IOException {
        var file = root.resolve("active.json");
        LearnActive.read(file).cleared(kind).write(file);
        return "disabled " + kind;
    }

    private static Connection required(Connection connection) throws SQLException {
        if (connection == null) {
            throw new SQLException("DISABLED");
        }
        return connection;
    }
}
