package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.StorageReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verdict files work without SQLite (M22.5). Origin of this path is the file. */
class VerdictFileFallbackTest {
    private static final String FIRST = "a".repeat(64);
    private static final String SECOND = "b".repeat(64);

    @TempDir
    Path directory;

    @Test
    void aFileRoundTripsAnImportLandsBesideItAndAClimbingPathIsRefused() throws Exception {
        var verdicts = directory.resolve("verdicts");
        var row = new StorageReply.GateVerdictRow(FIRST, "G1", "PASS_SIM_ONLY", "run-1", 42L);
        VerdictFiles.write(verdicts, row);
        var loaded = VerdictFiles.read(verdicts);
        assertEquals(1, loaded.size());
        assertEquals(row, loaded.getFirst());
        var source = directory.resolve("incoming.json");
        Files.writeString(source, "{\"sha256\":\"" + SECOND + "\",\"gate\":\"G1\",\"verdict\":\"PASS\","
                + "\"run_id\":\"run-2\",\"decided_epoch_ms\":7}\n");
        var imported = VerdictFiles.importFile(source, verdicts);
        assertEquals(SECOND, imported.sha256());
        assertEquals(Set.of(FIRST, SECOND), VerdictFiles.read(verdicts).stream()
                .map(StorageReply.GateVerdictRow::sha256).collect(Collectors.toSet()));
        assertTrue(Files.isRegularFile(verdicts.resolve(SECOND + "-G1.json")));
        assertThrows(IllegalArgumentException.class, () -> VerdictFiles.importFile(Path.of("../secrets"), verdicts));
    }
}
