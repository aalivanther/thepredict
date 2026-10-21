package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.StorageReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DOC-08: {@code eval verdict import} reads its file inside the plugin folder, never from the working directory of the
 * server, and refuses a path that leaves that folder with its reason.
 */
class VerdictSourceTest {
    private static final String INSIDE = "b".repeat(64);
    private static final String OUTSIDE = "c".repeat(64);

    @TempDir
    Path root;

    @Test
    void aRelativePathIsReadFromThePluginFolderAndAnAbsolutePathInsideItToo() throws Exception {
        var plugin = Files.createDirectories(root.resolve("plugins/ThePredict"));
        Files.createDirectories(plugin.resolve("incoming"));
        Files.writeString(plugin.resolve("incoming/verdict.json"), verdict(INSIDE, "G2"));
        var records = plugin.resolve("records");
        var database = plugin.resolve("aggregates.db");
        assertEquals("imported " + INSIDE + "; sqlite skipped", AdminTasks.run(
                new AdminCommand.VerdictImport("incoming/verdict.json"), records, database, null, null, 1));
        Files.writeString(plugin.resolve("again.json"), verdict(INSIDE, "G3"));
        assertEquals("imported " + INSIDE + "; sqlite skipped", AdminTasks.run(
                new AdminCommand.VerdictImport(plugin.resolve("again.json").toString()), records, database, null,
                null, 1));
        assertEquals(2, VerdictFiles.load(records.resolve("eval/verdicts")).rows().size());
    }

    @Test
    void anAbsolutePathOrALinkThatLeavesThePluginFolderIsRefusedAndNothingIsWritten() throws Exception {
        var plugin = Files.createDirectories(root.resolve("plugins/ThePredict"));
        var elsewhere = Files.writeString(root.resolve("elsewhere.json"), verdict(OUTSIDE, "G1"));
        var records = plugin.resolve("records");
        var database = plugin.resolve("aggregates.db");
        var absolute = assertThrows(AdminRefusal.class, () -> AdminTasks.run(
                new AdminCommand.VerdictImport(elsewhere.toString()), records, database, null, null, 1));
        assertEquals(VerdictSource.OUTSIDE, absolute.getMessage());
        Files.createSymbolicLink(plugin.resolve("link.json"), elsewhere);
        var link = assertThrows(AdminRefusal.class, () -> AdminTasks.run(
                new AdminCommand.VerdictImport("link.json"), records, database, null, null, 1));
        assertEquals(VerdictSource.OUTSIDE, link.getMessage());
        assertThrows(AdminRefusal.class, () -> VerdictSource.resolve(database, "../elsewhere.json"));
        assertFalse(Files.exists(records.resolve("eval/verdicts")), "a refused path writes no verdict");
    }

    @Test
    void theWriterAnswersARefusedPathWithItsReason() throws Exception {
        var plugin = Files.createDirectories(root.resolve("plugins/ThePredict"));
        var elsewhere = Files.writeString(root.resolve("elsewhere.json"), verdict(OUTSIDE, "G1"));
        var inbox = new Inbox<InboxEntry>(32);
        var writer = new StorageWriter(WriterHosts.spec(plugin.resolve("records"), plugin.resolve("aggregates.db"),
                true, false, 8, SegmentFiles.DISK, 8), inbox);
        var results = new ArrayList<StorageReply.AdminResult>();
        writer.request(new StorageWriter.Work.Command(new AdminCommand.VerdictImport(elsewhere.toString()), 7));
        WriterHosts.until(() -> drain(inbox, results) && !results.isEmpty());
        assertFalse(results.getFirst().ok());
        assertEquals(VerdictSource.OUTSIDE, results.getFirst().detail());
        writer.close(3000);
        assertTrue(VerdictFiles.load(plugin.resolve("records/eval/verdicts")).rows().isEmpty());
    }

    private static boolean drain(Inbox<InboxEntry> inbox, List<StorageReply.AdminResult> results) {
        inbox.drainStorage(32, reply -> {
            if (reply instanceof StorageReply.AdminResult result) {
                results.add(result);
            }
        });
        return true;
    }

    private static String verdict(String sha256, String gate) {
        return "{\"sha256\":\"" + sha256 + "\",\"gate\":\"" + gate + "\",\"verdict\":\"PASS\","
                + "\"run_id\":\"run-1\",\"decided_epoch_ms\":5}\n";
    }
}
