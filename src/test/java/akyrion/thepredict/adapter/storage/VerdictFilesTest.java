package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.StorageReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JS-06: one broken verdict file never blocks the others. */
class VerdictFilesTest {
    private static final String GOOD = "c".repeat(64);

    @TempDir
    Path directory;

    @Test
    void anEmptyOrCutFileIsSkippedAndCountedAndTheValidOneLoads() throws Exception {
        var verdicts = directory.resolve("verdicts");
        var row = new StorageReply.GateVerdictRow(GOOD, "G1", "PASS", "20261014T120000Z-0a1b", 9L);
        VerdictFiles.write(verdicts, row);
        Files.writeString(verdicts.resolve("empty.json"), "");
        Files.writeString(verdicts.resolve("cut.json"), "{\"sha256\":\"" + "d".repeat(64) + "\",\"gate\":\"G1\",\"verd");
        Files.writeString(verdicts.resolve("number.json"), "{\"sha256\":\"" + "e".repeat(64)
                + "\",\"gate\":\"G1\",\"verdict\":\"PASS\",\"run_id\":\"r\",\"decided_epoch_ms\":99999999999999999999}");
        var loaded = VerdictFiles.load(verdicts);
        assertEquals(java.util.List.of(row), loaded.rows());
        assertEquals(3, loaded.skipped());
    }

    @Test
    void theWriterStillDeliversTheValidVerdictsAndNamesTheSkip() throws Exception {
        var records = directory.resolve("records");
        var verdicts = records.resolve("eval/verdicts");
        var row = new StorageReply.GateVerdictRow(GOOD, "G1", "PASS", "20261014T120000Z-0a1b", 9L);
        VerdictFiles.write(verdicts, row);
        Files.writeString(verdicts.resolve("empty.json"), "");
        var inbox = new Inbox<InboxEntry>(8);
        var writer = new StorageWriter(WriterHosts.spec(records, directory.resolve("aggregates.db"), false, false, 8,
                SegmentFiles.DISK, 8), inbox);
        var delivered = new AtomicReference<StorageReply.VerdictsLoaded>();
        WriterHosts.until(() -> {
            inbox.drainStorage(16, reply -> {
                if (reply instanceof StorageReply.VerdictsLoaded loaded) {
                    delivered.set(loaded);
                }
            });
            return delivered.get() != null;
        });
        assertEquals(java.util.List.of(row), delivered.get().rows());
        assertEquals("VERDICTS_SKIPPED", writer.maintenanceCause());
        writer.close(3000);
    }

    /** §3.2.2 of config-admin-raiz: one file per stage, so G1 and G2 of one fingerprint both load without SQLite. */
    @Test
    void twoStagesOfOneFingerprintKeepTheirOwnFilesAndAFileOfTheOldFormStillLoads() throws Exception {
        var verdicts = directory.resolve("verdicts");
        var simulator = new StorageReply.GateVerdictRow(GOOD, "G1", "PASS", "run-1", 1L);
        var bench = new StorageReply.GateVerdictRow(GOOD, "G2", "PASS", "run-2", 2L);
        VerdictFiles.write(verdicts, simulator);
        VerdictFiles.write(verdicts, bench);
        assertTrue(Files.isRegularFile(verdicts.resolve(GOOD + "-G1.json")));
        assertTrue(Files.isRegularFile(verdicts.resolve(GOOD + "-G2.json")));
        var old = "d".repeat(64);
        Files.writeString(verdicts.resolve(old + ".json"), "{\"sha256\":\"" + old + "\",\"gate\":\"G1\",\"verdict\":"
                + "\"FAIL\",\"run_id\":\"run-0\",\"decided_epoch_ms\":0}\n");
        var loaded = VerdictFiles.load(verdicts);
        assertEquals(java.util.Set.of(simulator, bench, new StorageReply.GateVerdictRow(old, "G1", "FAIL", "run-0", 0L)),
                java.util.Set.copyOf(loaded.rows()));
        assertEquals(0, loaded.skipped());
    }

    @Test
    void anImportOfSomethingElseIsRefusedAndWritesNothing() throws Exception {
        var verdicts = directory.resolve("verdicts");
        refused("{\"sha256\":\"../../x\",\"gate\":\"G1\",\"verdict\":\"PASS\",\"run_id\":\"r\",\"decided_epoch_ms\":1}");
        refused("{\"sha256\":\"" + GOOD + "\",\"gate\":\"G9\",\"verdict\":\"PASS\",\"run_id\":\"r\",\"decided_epoch_ms\":1}");
        refused("{\"sha256\":\"" + GOOD + "\",\"gate\":\"G1\",\"verdict\":\"MAYBE\",\"run_id\":\"r\",\"decided_epoch_ms\":1}");
        refused("{\"sha256\":\"" + GOOD + "\",\"gate\":\"G1\",\"verdict\":\"PASS\",\"run_id\":\"a\\\"b\",\"decided_epoch_ms\":1}");
        assertFalse(Files.exists(verdicts));
    }

    private void refused(String json) throws Exception {
        var source = directory.resolve("incoming.json");
        Files.writeString(source, json);
        assertThrows(IllegalArgumentException.class, () -> VerdictFiles.importFile(source,
                directory.resolve("verdicts")));
    }
}
