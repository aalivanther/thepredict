package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.StorageState;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteAvailabilityTest {
    @Test
    void aMissingDriverAnSqlErrorAndTheVersionFloorAreDistinct() {
        var missing = SqliteAvailability.assess(() -> {
            throw new NoClassDefFoundError("org.sqlite.SQLiteDataSource");
        });
        assertEquals(StorageState.DISABLED, missing.state());
        assertEquals("DRIVER_MISSING", missing.cause());
        var failed = SqliteAvailability.assess(() -> {
            throw new SQLException("open");
        });
        assertEquals(StorageState.FAILED, failed.state());
        assertEquals("SQLException", failed.cause());
        assertEquals("SQLITE_TOO_OLD", SqliteAvailability.classify("3.44.0").cause());
        assertEquals(StorageState.READY, SqliteAvailability.classify("3.45.0").state());
        assertEquals(StorageState.DISABLED, SqliteAvailability.classify(null).state());
    }
}
