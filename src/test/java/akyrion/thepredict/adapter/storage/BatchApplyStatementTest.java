package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.domain.OutcomeStatMath;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.OutcomeStatDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JS-25: a batch prepares the upsert of a table once and binds every row of that table to it, and it runs in
 * {@code BEGIN IMMEDIATE … COMMIT} (M21.2).
 */
class BatchApplyStatementTest {
    @TempDir
    Path directory;

    private int outsiders;

    @Test
    void threeRowsOfOneTablePrepareItsUpsertOnce() throws Exception {
        var file = database("statements.db");
        try (var real = SqliteConnections.open(file, "normal", 2000)) {
            insertRun(real, "run");
            var prepared = new ArrayList<String>();
            assertTrue(BatchApply.apply(watching(real, prepared::add), batch(1), "run"));
            assertEquals(1, prepared.stream().filter(OutcomeStatsSql.UPSERT::equals).count(), prepared.toString());
            assertEquals(3, scalar(real, "SELECT COUNT(*) FROM outcome_stats"));
        }
    }

    /**
     * The write lock is taken as the batch begins: when the batch prepares its first statement, before it reads
     * anything, another connection already cannot write. A deferred begin holds no lock at that point.
     */
    @Test
    void aBatchHoldsTheWriteLockBeforeItsFirstStatement() throws Exception {
        var file = database("immediate.db");
        try (var real = SqliteConnections.open(file, "normal", 2000);
             var other = SqliteConnections.open(file, "normal", 0)) {
            insertRun(real, "run");
            var outsider = new ArrayList<String>();
            var watched = watching(real, sql -> {
                if (outsider.isEmpty()) {
                    outsider.add(tryWrite(other));
                }
            });
            assertTrue(BatchApply.apply(watched, batch(1), "run"));
            assertEquals(List.of("SQLITE_BUSY"), outsider);
            assertEquals("written", tryWrite(other), "the lock ends with the batch");
            assertEquals(1, scalar(real, "SELECT last_applied_batch FROM runs WHERE run = 'run'"));
        }
    }

    /** A begin that finds the lock held fails before writing, leaves nothing open, and the next batch applies whole. */
    @Test
    void aBusyBeginWritesNothingAndTheNextBatchAppliesWhole() throws Exception {
        var file = database("busy.db");
        try (var writer = SqliteConnections.open(file, "normal", 0);
             var holder = SqliteConnections.open(file, "normal", 0)) {
            insertRun(writer, "run");
            execute(holder, "BEGIN IMMEDIATE");
            var busy = assertThrows(SQLException.class, () -> BatchApply.apply(writer, batch(1), "run"));
            assertTrue(busy.getMessage().contains("SQLITE_BUSY"), busy.getMessage());
            execute(holder, "ROLLBACK");
            assertTrue(writer.getAutoCommit());
            assertEquals(0, scalar(writer, "SELECT COUNT(*) FROM outcome_stats"));
            assertTrue(BatchApply.apply(writer, batch(1), "run"));
            assertEquals(3, scalar(writer, "SELECT COUNT(*) FROM outcome_stats"));
            assertEquals(1, scalar(writer, "SELECT last_applied_batch FROM runs WHERE run = 'run'"));
        }
    }

    private Path database(String name) {
        var file = directory.resolve(name);
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        return file;
    }

    private static AggregateBatch batch(long id) {
        var stat = OutcomeStatMath.add(OutcomeStat.EMPTY, 0.5, 0, 0, false, false, false, 0, 0);
        var rows = new ArrayList<OutcomeStatDelta>();
        for (var tactic : List.of("strike", "guard", "dodge")) {
            rows.add(new OutcomeStatDelta(new OutcomeStatKey("guardian", new TacticId(tactic), Movement.APPROACH,
                    ControlLevel.JEV, "v1:NONE:NONE"), stat, 1L));
        }
        return new AggregateBatch(id, rows, List.of(), List.of());
    }

    private static Connection watching(Connection real, Consumer<String> onPrepare) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("prepareStatement")) {
                        onPrepare.accept((String) arguments[0]);
                    }
                    try {
                        return method.invoke(real, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private String tryWrite(Connection other) {
        outsiders++;
        try {
            insertRun(other, "outsider-" + outsiders);
            return "written";
        } catch (SQLException exception) {
            return exception.getMessage().contains("SQLITE_BUSY") ? "SQLITE_BUSY" : exception.getMessage();
        }
    }

    private static void insertRun(Connection connection, String run) throws SQLException {
        execute(connection, "INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, "
                + "config_sha256, sqlite_version) VALUES ('" + run + "', 1, 0, '0.0.1', 'jev-1.13.0', 'abc', '3.49.1')");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
