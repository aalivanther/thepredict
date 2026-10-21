package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.learn.ExploreSettings;
import akyrion.thepredict.domain.learn.TrainingSource;
import akyrion.thepredict.domain.learn.ValueArtifactFormat;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;
import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.engine.AdminCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** H and JS-17: {@code learn build} reads page by page into one {@code ValueRollup} and writes a {@code .tplv} 2. */
class LearnBuildJobTest {
    private static final long NOW = System.currentTimeMillis();
    private static final int WEEK = ValueStatKey.week(NOW);

    @TempDir
    Path directory;

    @Test
    void manyPagesGiveTheBytesOfOnePageAndTheSourceBatchIsTheAppliedOne() throws Exception {
        try (var connection = database("pages")) {
            for (var row = 0; row < 450; row++) {
                ValueStatSql.upsert(connection, value("tactic" + row, "v2:MELEE:HIGH:MID:MOB:OPEN:SOLO:NONE",
                        row % 2 == 0 ? "sim" : "bench", WEEK - row % 3, row));
            }
            var paged = build(connection, settings(200), "paged", 7);
            var whole = build(connection, settings(10_000), "whole", 7);
            assertArrayEquals(Files.readAllBytes(whole), Files.readAllBytes(paged));
            var artifact = ValueArtifactFormat.read(Files.readAllBytes(paged));
            assertEquals(7, artifact.batch());
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT source_batch, entries, format_v FROM m28_artifacts")) {
                assertTrue(result.next());
                assertEquals(7, result.getInt(1));
                assertEquals(artifact.cells().size(), result.getInt(2));
                assertEquals(2, result.getInt(3));
            }
        }
    }

    @Test
    void aRowWrittenBetweenTwoPagesIsBuiltAndNoneIsCountedTwice() throws Exception {
        try (var connection = database("between")) {
            for (var row = 0; row < 450; row++) {
                ValueStatSql.upsert(connection, value("a" + row, "v2:MELEE:HIGH:MID:MOB:OPEN:SOLO:NONE", "sim", WEEK,
                        row));
            }
            var job = new LearnBuildJob(settings(200));
            var root = directory.resolve("between-learn");
            job.start(1, "", 3);
            assertTrue(job.step(connection, connection, root).isEmpty());
            ValueStatSql.upsert(connection, value("zz_late", "v2:MELEE:HIGH:MID:MOB:OPEN:SOLO:NONE", "sim", WEEK, 1));
            var detail = "";
            while (job.running()) {
                detail = job.step(connection, connection, root).orElse(detail);
            }
            assertTrue(detail.startsWith("built "), detail);
            var artifact = ValueArtifactFormat.read(Files.readAllBytes(only(root.resolve("artifacts"))));
            assertTrue(artifact.tactics().contains("zz_late"));
            assertEquals(451, artifact.tactics().size());
        }
    }

    @Test
    void anImportThatIsNotAValueArtifactIsRefusedAndNothingIsCopied() throws Exception {
        try (var connection = database("import")) {
            var records = directory.resolve("records");
            var export = records.resolveSibling("learn").resolve("export");
            Files.createDirectories(export);
            Files.write(export.resolve("bundle.tplv"), new byte[] {'T', 'P', 'L', 'V', 0, 0, 0, 9});
            assertThrows(IllegalArgumentException.class, () -> LearnAdmin.run(new AdminCommand.LearnImport(
                    "bundle.tplv"), records, connection, connection));
            assertFalse(Files.exists(records.resolveSibling("learn").resolve("artifacts")));
            assertThrows(IllegalArgumentException.class, () -> LearnAdmin.run(new AdminCommand.LearnBuild(""),
                    records, connection, connection));
        }
    }

    private Path build(Connection connection, LearningSettings learning, String name, long batch) throws Exception {
        var job = new LearnBuildJob(learning);
        var root = directory.resolve(name);
        job.start(1, "", batch);
        var steps = 0;
        while (job.running()) {
            job.step(connection, connection, root);
            steps++;
        }
        assertEquals(learning.buildChunkRows() == 200 ? 3 : 1, steps);
        return only(root.resolve("artifacts"));
    }

    private static Path only(Path folder) throws Exception {
        try (var files = Files.list(folder)) {
            var all = files.toList();
            assertEquals(1, all.size(), all.toString());
            return all.getFirst();
        }
    }

    private Connection database(String name) throws Exception {
        var file = directory.resolve(name + ".db");
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L, directory.resolve("backups"));
        return SqliteConnections.open(file, "normal", 2000);
    }

    private static ValueStatDelta value(String tactic, String context, String source, int week, int salt) {
        return new ValueStatDelta(new ValueStatKey("guardian", tactic, context, source, "r1", week), 2 + salt % 5,
                0.1 + (salt % 7) / 10.0, 0.01, 0.2, 0.1, 0.0, salt % 3, Math.min(salt % 3, salt % 2), NOW - salt);
    }

    private static LearningSettings settings(int chunkRows) {
        var standard = LearningSettings.DEFAULT;
        return new LearningSettings(standard.allowUnverified(), standard.collect(), standard.scope(),
                CandidateScoring.PRIORITY, TrainingSource.DEFAULTS, standard.contextVersion(),
                standard.maxContextKeysPerProfile(), standard.maxOpenWindowsPerSession(), standard.creditShare(),
                standard.groupCreditShare(), standard.creditDecay(), standard.minWindowsForValue(),
                standard.halfLifeDays(), standard.weeksKept(), standard.maxEntries(), standard.minValueSpreadMillis(),
                chunkRows, standard.exploreInDuels(), ExploreSettings.DEFAULT, standard.calibrationMinDecisions(),
                standard.priorStrength(), standard.driftReferenceWindows(), standard.voiSkip(),
                standard.voiMinWindows(), standard.voiMinAgreement(), standard.voiMaxExpectedGain(),
                standard.voiMaxConsecutiveSkips(), standard.jevHint());
    }
}
