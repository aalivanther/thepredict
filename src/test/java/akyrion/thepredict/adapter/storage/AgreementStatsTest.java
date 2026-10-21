package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.distill.AgreementStatDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Agreement rows sum n, sum_delta and sum_delta_sq. Agreement counters stay a metric. */
class AgreementStatsTest {
    @TempDir
    Path directory;

    @Test
    void twoUpsertsAddTheOutcomeColumns() throws Exception {
        var database = directory.resolve("agree.db");
        SchemaMigrator.open(database, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L,
                directory.resolve("backups"));
        try (var connection = SqliteConnections.open(database, "normal", 2000)) {
            AgreementStatSql.upsert(connection, row(50, -1.0, 0.02, 1, 1));
            AgreementStatSql.upsert(connection, row(10, -0.5, 0.01, 3, 0));
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         SELECT n, sum_delta, sum_delta_sq, compared, agreed FROM m29_agreement_stats
                         """)) {
                result.next();
                assertEquals(60, result.getInt(1));
                assertEquals(-1.5, result.getDouble(2), 1.0E-9);
                assertEquals(0.03, result.getDouble(3), 1.0E-9);
                assertEquals(4, result.getInt(4));
                assertEquals(1, result.getInt(5));
            }
        }
    }

    private static AgreementStatDelta row(int n, double sum, double squares, int compared, int agreed) {
        return new AgreementStatDelta("artifact", "guardian", "r1:ALL", "fine", 0, n, sum, squares, compared,
                agreed, 0, 0, 1L);
    }
}
