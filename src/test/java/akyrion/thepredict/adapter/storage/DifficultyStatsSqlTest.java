package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.DifficultyStatMath;
import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.engine.DifficultyStatDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SQL Chan matches {@link DifficultyStatMath} on a seeded batch (M22.1). */
class DifficultyStatsSqlTest {
    @TempDir
    Path directory;

    @Test
    void aHundredSeededUpsertsMatchTheJavaMerge() throws Exception {
        var file = directory.resolve("aggregates.db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        var java = new DifficultyStatMath.Row(0, 0, 0, 0, 0, 0, 0, Long.MAX_VALUE, 0);
        try (var connection = SqliteConnections.open(file, "normal", 2000)) {
            for (var batch = 1; batch <= 100; batch++) {
                var word = StableRandom.draw(22L, batch, 0);
                var mean = ((word >>> 11) / (double) (1L << 53)) * 2.0;
                var row = new DifficultyStatMath.Row(1, mean, 0.01, 0, 0, 4, batch, batch, batch);
                java = DifficultyStatMath.merge(java, row);
                DifficultyStatsSql.upsert(connection, new DifficultyStatDelta("guardian", "v1:player:1:NORMAL", 4, 1,
                        mean, 0.01, 0, 0, 4, batch, batch, batch));
            }
            var loaded = DifficultyStatsSql.load(connection, "guardian");
            assertEquals(1, loaded.size());
            assertEquals(java.fights(), loaded.getFirst().fights());
            assertEquals(java.mean(), loaded.getFirst().logTtkMean(), 1e-9);
            assertEquals(java.m2(), loaded.getFirst().logTtkM2(), 1e-6);
            assertEquals(100L, loaded.getFirst().startRungEpochMs());
        }
    }
}
