package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.domain.OutcomeStatMath;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.OutcomeStatDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupManagerTest {
    @TempDir
    Path directory;

    @Test
    void aManualCopyKeepsTheConfiguredCountAndABurstLosesNoWindow() throws Exception {
        var records = directory.resolve("records");
        var database = directory.resolve("aggregates.db");
        var backups = records.resolve("backups");
        Files.createDirectories(backups);
        SchemaMigrator.open(database, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, backups);
        for (var index = 0; index < 8; index++) {
            Files.writeString(backups.resolve("aggregates-2026100" + index + "T000000Z.db"), "old" + index);
        }
        try (var connection = SqliteConnections.open(database, "normal", 2000)) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, config_sha256, sqlite_version) "
                            + "VALUES ('run', 1, 0, '0.0.1', 'jev-1.0.0', 'abc', '3.49.1')")) {
                statement.executeUpdate();
            }
            var key = new OutcomeStatKey("guardian", new TacticId("keep_distance"), Movement.APPROACH, ControlLevel.JEV,
                    "v1:NONE:NONE");
            for (var id = 1; id <= 20; id++) {
                var stat = OutcomeStatMath.add(OutcomeStat.EMPTY, 0.1, 0, 0, false, false, false, 0, 0);
                BatchApply.apply(connection, new AggregateBatch(id, List.of(new OutcomeStatDelta(key, stat, id)),
                        List.of(), List.of()), "run");
            }
            var name = AdminTasks.run(new AdminCommand.Backup(), records, database, connection, connection, 4);
            try (var stream = Files.list(backups)) {
                assertEquals(4, stream.filter(path -> path.getFileName().toString().endsWith(".db")).count());
            }
            assertTrue(Files.exists(backups.resolve(name)));
            assertTrue(Files.exists(backups.resolve("aggregates-20261007T000000Z.db")));
            assertTrue(!Files.exists(backups.resolve("aggregates-20261000T000000Z.db")));
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT windows FROM outcome_stats")) {
                assertTrue(result.next());
                assertEquals(20, result.getLong(1));
            }
        }
    }
}
