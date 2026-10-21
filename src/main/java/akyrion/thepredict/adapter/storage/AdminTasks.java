package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.AdminCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;

/**
 * Admin work that touches files or SQLite (M21.9). The storage thread runs it. Tag, cache clear and flush stay on the
 * server thread inside {@link StorageService}. {@code learn build} and {@code distill build} are not run here: the
 * writer runs them page by page as jobs, and this class refuses them with {@link IllegalArgumentException}.
 */
public final class AdminTasks {
    /** The answer to {@code eval report}: the report is built off the server, not by the writer, in this release. */
    public static final String EVAL_REPORT_UNAVAILABLE =
            "eval report is not available on the server in this release; run ./gradlew evalReport";

    private AdminTasks() {
    }

    /**
     * Runs one command.
     *
     * @param command the command
     * @param records the records directory
     * @param database the database file
     * @param write the writable connection, or null
     * @param read the read connection, or null
     * @param backupsKept copies kept after a manual backup
     * @return a short result
     * @throws IOException when a file operation fails
     * @throws SQLException when SQL fails
     */
    public static String run(AdminCommand command, Path records, Path database, Connection write, Connection read,
                             int backupsKept) throws IOException, SQLException {
        return run(command, records, database, write, read, backupsKept,
                akyrion.thepredict.engine.distill.DistillControl.passthrough());
    }

    /**
     * Runs one command with the live distill control.
     *
     * @param command the command
     * @param records the records directory
     * @param database the database file
     * @param write the writable connection, or null
     * @param read the read connection, or null
     * @param backupsKept copies kept after a manual backup
     * @param control the distill control
     * @return a short result
     * @throws IOException when a file operation fails
     * @throws SQLException when SQL fails
     */
    public static String run(AdminCommand command, Path records, Path database, Connection write, Connection read,
                             int backupsKept, akyrion.thepredict.engine.distill.DistillControl control)
            throws IOException, SQLException {
        return run(command, records, database, write, read, backupsKept, control, OverlayModes.DEFAULTS,
                ProfileBases.UNKNOWN);
    }

    /**
     * Runs one command with the live distill control and what {@code autoconfig apply} checks a proposal against.
     *
     * @param command the command
     * @param records the records directory
     * @param database the database file
     * @param write the writable connection, or null
     * @param read the read connection, or null
     * @param backupsKept copies kept after a manual backup
     * @param control the distill control
     * @param modes the global modes in use, which an overlay may not raise
     * @param bases the profiles read now, or {@link ProfileBases#UNKNOWN}
     * @return a short result
     * @throws IOException when a file operation fails or a proposal is refused
     * @throws SQLException when SQL fails
     */
    public static String run(AdminCommand command, Path records, Path database, Connection write, Connection read,
                             int backupsKept, akyrion.thepredict.engine.distill.DistillControl control,
                             OverlayModes modes, ProfileBases bases) throws IOException, SQLException {
        var later = later(command, records, control, modes, bases);
        if (later.isPresent()) {
            return later.get();
        }
        return switch (command) {
            case AdminCommand.Export export -> export.kind().equals("outcomes")
                    ? OutcomeExport.write(required(read), records, export.argument())
                    : EventExport.write(records, export.argument());
            case AdminCommand.Prune prune -> prune(records, prune.days());
            case AdminCommand.Verify ignored -> verify(required(write == null ? read : write));
            case AdminCommand.Backup ignored -> backup(required(write), records, backupsKept);
            case AdminCommand.Flush ignored -> "queued";
            case AdminCommand.CacheClear ignored -> "cleared";
            case AdminCommand.Tag tag -> tag.tag().isEmpty() ? "cleared" : "tagged";
            case AdminCommand.EvalReport ignored -> EVAL_REPORT_UNAVAILABLE;
            case AdminCommand.VerdictImport imported -> importVerdict(records, database, write, imported.path());
            case AdminCommand.MemoryStats ignored -> "rows " + PlayerMemorySql.count(required(read));
            case AdminCommand.MemoryShow show -> MemoryAdmin.show(required(read), show.subject());
            case AdminCommand.MemoryReset reset -> MemoryAdmin.reset(required(write), reset.subject());
            case AdminCommand.MemoryPurge purge -> MemoryAdmin.purge(required(write), purge.days());
            case AdminCommand.MemoryExport export -> MemoryAdmin.export(required(read), records, export.selector());
            case AdminCommand.MemoryImport imported -> MemoryAdmin.importFile(required(write), records, imported.path(),
                    imported.replace());
            case AdminCommand.LearnBuild build -> LearnAdmin.run(build, records, write, read);
            case AdminCommand.LearnExport export -> LearnAdmin.run(export, records, write, read);
            case AdminCommand.LearnImport imported -> LearnAdmin.run(imported, records, write, read);
            case AdminCommand.LearnRollback rollback -> LearnAdmin.run(rollback, records, write, read);
            case AdminCommand.LearnDisable disable -> LearnAdmin.run(disable, records, write, read);
            default -> throw new IllegalArgumentException("command");
        };
    }

    private static java.util.Optional<String> later(AdminCommand command, Path records,
                                                    akyrion.thepredict.engine.distill.DistillControl control,
                                                    OverlayModes modes, ProfileBases bases) throws IOException {
        return switch (command) {
            case AdminCommand.DistillModeSet mode -> java.util.Optional.of(control
                    .raise(akyrion.thepredict.domain.distill.DistillMode.parse(mode.mode())).name()
                    .toLowerCase(java.util.Locale.ROOT));
            case AdminCommand.DistillDisable ignored -> {
                control.disable();
                yield java.util.Optional.of("disable next capture");
            }
            case AdminCommand.DistillExport export -> java.util.Optional.of(DistillAdmin.export(records, export.file()));
            case AdminCommand.DistillImport imported -> java.util.Optional.of(
                    DistillAdmin.importFile(records, imported.path()));
            case AdminCommand.DistillRollback ignored -> java.util.Optional.of(DistillAdmin.rollback(records));
            case AdminCommand.AutoconfigImport imported -> java.util.Optional.of(AutoconfigAdmin.copyIn(imported.path()));
            case AdminCommand.AutoconfigApply apply -> java.util.Optional.of(AutoconfigAdmin.apply(apply.path(), modes,
                    bases));
            case AdminCommand.AutoconfigShow show -> java.util.Optional.of(AutoconfigAdmin.show(show.path()));
            case AdminCommand.AutoconfigRevert revert -> java.util.Optional.of(AutoconfigAdmin.revert(revert.path()));
            default -> java.util.Optional.empty();
        };
    }

    private static String importVerdict(Path records, Path database, Connection write, String path)
            throws IOException, SQLException {
        var row = VerdictFiles.importFile(VerdictSource.resolve(database, path), records.resolve("eval/verdicts"));
        if (write == null) {
            return "imported " + row.sha256() + "; sqlite skipped";
        }
        GateVerdictSql.upsert(write, row);
        return "imported " + row.sha256();
    }

    private static Connection required(Connection connection) throws SQLException {
        if (connection == null) {
            throw new SQLException("DISABLED");
        }
        return connection;
    }

    private static String prune(Path records, int days) throws IOException {
        if (days < 0) {
            throw new IOException("days");
        }
        var root = records.resolve("events");
        if (!Files.isDirectory(root)) {
            return "pruned 0";
        }
        var sizes = new ArrayList<RetentionPolicy.DaySize>();
        try (var stream = Files.list(root)) {
            for (var day : stream.filter(Files::isDirectory).toList()) {
                sizes.add(new RetentionPolicy.DaySize(day.getFileName().toString(), 0));
            }
        }
        var today = java.time.LocalDate.now(ZoneOffset.UTC).toString();
        var plan = RetentionPolicy.plan(sizes, today, days, Long.MAX_VALUE);
        for (var day : plan.delete()) {
            EventDays.delete(root.resolve(day));
        }
        return "pruned " + plan.delete().size();
    }

    private static String verify(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA integrity_check")) {
            if (result.next() && "ok".equalsIgnoreCase(result.getString(1))) {
                return "ok";
            }
            return result.getString(1);
        }
    }

    private static String backup(Connection connection, Path records, int backupsKept) throws IOException, SQLException {
        var directory = records.resolve("backups");
        Files.createDirectories(directory);
        var target = BackupFiles.next(directory, Instant.now());
        BackupFiles.vacuumInto(connection, target);
        BackupFiles.trim(directory, backupsKept);
        return target.getFileName().toString();
    }
}
