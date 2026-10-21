package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.EnemyProfileMath;
import akyrion.thepredict.domain.EnemyProfileSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V003MigrationTest {
    @TempDir
    Path directory;

    @Test
    void aV2CopyGainsV3AndAFakeM27LoaderMergesAThousandSummaries() throws Exception {
        var source = directory.resolve("v2.db");
        var backups = directory.resolve("backups");
        var v2 = SchemaMigrator.bundled().subList(0, 2);
        var opened = SchemaMigrator.open(source, v2, "normal", 2000, "0.0.1", 1L, backups);
        assertEquals(2, opened.version());
        var copy = directory.resolve("copy.db");
        Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
        var upgraded = SchemaMigrator.open(copy, SchemaMigrator.bundled().subList(0, 5), "normal", 2000, "0.0.1", 2L,
                directory.resolve("copy-backups"));
        assertEquals(5, upgraded.version());
        try (var connection = SqliteConnections.open(copy, "normal", 2000);
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE name = 'm26_pattern_stats'")) {
            assertTrue(result.next());
        }
        var truth = new HashMap<Integer, Integer>();
        var merged = EnemyProfileSummary.empty();
        for (var index = 0; index < 1000; index++) {
            var key = index % 20;
            truth.merge(key, 1, Integer::sum);
            var actions = new int[EnemyProfileSummary.ACTIONS];
            actions[0] = 2;
            merged = EnemyProfileMath.merge(merged, new EnemyProfileSummary(1, actions,
                    new int[EnemyProfileSummary.EPISODES], new int[EnemyProfileSummary.BETA],
                    new int[EnemyProfileSummary.BETA], new int[EnemyProfileSummary.BETA], 0f, 0f, 0,
                    new int[] {key}, new int[] {1}, 1, 0, new int[EnemyProfileSummary.BINS], 0, 0));
        }
        assertEquals(2000, merged.actions()[0]);
        assertEquals(1000, merged.transitionTotal());
        for (var entry : truth.entrySet()) {
            var estimate = EnemyProfileMath.estimate(merged, entry.getKey());
            assertTrue(estimate <= entry.getValue());
            assertTrue(estimate >= entry.getValue() - 1000 / 17.0);
        }
    }
}
