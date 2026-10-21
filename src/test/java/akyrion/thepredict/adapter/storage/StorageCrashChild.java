package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.domain.OutcomeStatMath;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.OutcomeStatDelta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Child process of {@link StorageCrashTest}. It commits one batch, writes a marker and then waits to be killed.
 */
public final class StorageCrashChild {
    private StorageCrashChild() {
    }

    /**
     * Opens the database named by the arguments.
     *
     * @param arguments database path, marker path and jsonl path
     * @throws Exception when the database cannot be prepared
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            System.out.println("usage: StorageCrashChild <database> <marker> <jsonl>");
            return;
        }
        var database = Path.of(arguments[0]);
        var marker = Path.of(arguments[1]);
        var jsonl = Path.of(arguments[2]);
        SchemaMigrator.open(database, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L,
                database.getParent().resolve("backups"));
        var key = new OutcomeStatKey("guardian", new TacticId("keep_distance"), Movement.APPROACH, ControlLevel.JEV,
                "v1:NONE:NONE");
        var stat = OutcomeStatMath.add(OutcomeStat.EMPTY, 0.25, 0, 0, false, false, false, 0, 0);
        try (var connection = SqliteConnections.open(database, "normal", 2000)) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, config_sha256, sqlite_version) "
                            + "VALUES ('run', 1, 0, '0.0.1', 'jev-1.0.0', 'abc', '3.49.1')")) {
                statement.executeUpdate();
            }
            BatchApply.apply(connection, new AggregateBatch(1, List.of(new OutcomeStatDelta(key, stat, 1L)), List.of(),
                    List.of()), "run");
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(FULL)");
            }
        }
        Files.writeString(jsonl, "{\"v\":1,\"type\":\"planned\",\"run\":\"run\"}\n", StandardCharsets.UTF_8);
        Files.writeString(marker, "READY\n", StandardCharsets.UTF_8);
        System.out.println("READY");
        System.out.flush();
        while (true) {
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000_000L);
        }
    }
}
