package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.distill.ChoiceStatDelta;
import akyrion.thepredict.domain.distill.ChoiceStatKey;
import akyrion.thepredict.domain.distill.PackagedSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The writer builds a tactic table from choice rows. A packaged directory is imported, not trained. */
class DistillBuildChunkTest {
    @TempDir
    Path directory;

    @Test
    void theWriterReadsATableFromChoiceRowsAndSaysNoArtifactIsWritten() throws Exception {
        try (var connection = database("predict.db")) {
            ChoiceStatSql.upsert(connection, row("Guardian", "bravo", 5, 4));
            ChoiceStatSql.upsert(connection, row("Guardian", "alpha", 5, 1));
            var job = new DistillTableChunk.Job();
            job.begin();
            job.turn(connection, 2000);
            assertTrue(job.done());
            assertEquals("read 2 rows in 1 chunks; no artifact written, distillTrain builds the .tpld", job.note());
            assertEquals("bravo", job.cells().get("0").orElseThrow().tactics().getFirst());
            var resumed = new DistillTableChunk.Job();
            resumed.begin();
            resumed.turn(connection, 1);
            assertFalse(resumed.done());
            assertTrue(resumed.cells().get("0").isPresent());
            while (!resumed.done()) {
                resumed.turn(connection, 1);
            }
            assertEquals("bravo", resumed.cells().get("0").orElseThrow().tactics().getFirst());
            assertEquals(2, resumed.built().rows());
        }
        var absent = new DistillTableChunk.Job();
        absent.begin();
        absent.turn(null, 2000);
        assertTrue(absent.done());
        assertEquals("read 0 rows in 0 chunks; no artifact written, distillTrain builds the .tpld", absent.note());
    }

    @Test
    void aRowWrittenBetweenTurnsIsReadOnceAndTheFilterKeepsOneProfile() throws Exception {
        try (var connection = database("keyset.db")) {
            for (var tactic = 0; tactic < 6; tactic++) {
                ChoiceStatSql.upsert(connection, row("Guardian", "t" + tactic, 2, 1));
            }
            ChoiceStatSql.upsert(connection, row("Warden", "t0", 9, 9));
            var job = new DistillTableChunk.Job();
            job.begin("Guardian");
            job.turn(connection, 2);
            ChoiceStatSql.upsert(connection, row("Guardian", "a_before", 4, 4));
            ChoiceStatSql.upsert(connection, row("Guardian", "z_after", 4, 3));
            while (!job.done()) {
                job.turn(connection, 2);
            }
            assertEquals(7, job.built().rows());
            var order = job.cells().get("0").orElseThrow().tactics();
            assertEquals("z_after", order.getFirst());
            assertFalse(order.contains("a_before"));
        }
    }

    private Connection database(String name) throws Exception {
        var file = directory.resolve(name);
        SchemaMigrator.open(file, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L,
                directory.resolve("backups"));
        return SqliteConnections.open(file, "normal", 2000);
    }

    @Test
    void aPackagedVerdictIsImportedAndAJevSourceIsRefused() throws Exception {
        var packaged = directory.resolve("learn").resolve("packaged").resolve("sample");
        Files.createDirectories(packaged);
        Files.writeString(packaged.resolve("verdict.json"), "{\"source\":\"outcome\",\"status\":\"PASS\"}\n");
        var loaded = PackagedSource.load(directory);
        assertEquals(1, loaded.size());
        assertEquals("sample", loaded.getFirst().name());
        assertEquals("outcome", loaded.getFirst().source());
        assertTrue(loaded.getFirst().imported());
        var jar = directory.resolve("packaged.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("learn/packaged/"));
            output.closeEntry();
            output.putNextEntry(new JarEntry("learn/packaged/sample/"));
            output.closeEntry();
            output.putNextEntry(new JarEntry("learn/packaged/sample/verdict.json"));
            output.write("{\"source\":\"outcome\",\"status\":\"PASS\"}\n".getBytes());
            output.closeEntry();
        }
        try (var loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            var imported = PackagedSource.load(loader);
            assertEquals(1, imported.size());
            assertEquals("sample", imported.getFirst().name());
            assertEquals("outcome", imported.getFirst().source());
            assertTrue(imported.getFirst().imported());
        }
        Files.writeString(packaged.resolve("verdict.json"), "{\"source\":\"jev\"}\n");
        assertThrows(IllegalArgumentException.class, () -> PackagedSource.load(directory));
    }

    private static ChoiceStatDelta row(String profile, String tactic, int offered, int chosen) {
        return new ChoiceStatDelta(new ChoiceStatKey(profile, "r1:ALL", "0", tactic, "window", 0, "outcome"),
                offered, chosen, 1L);
    }
}
