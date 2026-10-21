package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginJarGuardTest {
    @TempDir
    Path directory;

    @Test
    void verifyPluginJarRejectsABundledSqliteClassAndRequiresTheMigration() throws Exception {
        var script = Files.readString(Path.of("build.gradle.kts"));
        assertTrue(script.contains("org/sqlite/"));
        assertTrue(script.contains("com/mysql/"));
        assertTrue(script.contains("storage/sqlite/V001__base.sql"));
        var bad = directory.resolve("bad.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(bad))) {
            zip.putNextEntry(new ZipEntry("org/sqlite/JDBC.class"));
            zip.write(new byte[] {1});
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("storage/sqlite/V001__base.sql"));
            zip.write("CREATE TABLE t(id INTEGER) STRICT;".getBytes());
            zip.closeEntry();
        }
        assertTrue(containsSqlite(bad));
        var good = directory.resolve("good.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(good))) {
            zip.putNextEntry(new ZipEntry("storage/sqlite/V001__base.sql"));
            zip.write("CREATE TABLE t(id INTEGER) STRICT;".getBytes());
            zip.closeEntry();
        }
        assertFalse(containsSqlite(good));
        assertTrue(containsMigration(good));
    }

    private static boolean containsSqlite(Path jar) throws Exception {
        try (var zip = new ZipFile(jar.toFile())) {
            return zip.stream().anyMatch(entry -> entry.getName().startsWith("org/sqlite/")
                    || entry.getName().startsWith("com/mysql/"));
        }
    }

    private static boolean containsMigration(Path jar) throws Exception {
        try (var zip = new ZipFile(jar.toFile())) {
            return zip.getEntry("storage/sqlite/V001__base.sql") != null;
        }
    }
}
