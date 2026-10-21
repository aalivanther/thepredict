package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.StorageHealth;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.StorageStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the writer answers to the commands of M29.8 and M22.5: a refused proposal says why, the global modes of the
 * specification and the profiles read now decide, and an imported verdict reaches the gates without a restart, also
 * with records and aggregates off (CAR-03, CAR-04, CAR-08, JS-20).
 */
class WriterAdminRepliesTest {
    private static final String BASE = "1".repeat(64);
    private static final String VERDICT = "a".repeat(64);

    @TempDir
    Path root;

    private final List<StorageReply.VerdictsLoaded> loads = new ArrayList<>();
    private final List<StorageReply.AdminResult> results = new ArrayList<>();

    @Test
    void aRefusedProposalAnswersWithItsReasonAndTheSpecAndTheProfilesReadNowDecide() throws Exception {
        var data = root.resolve("plugins/ThePredict");
        var proposal = Files.createDirectories(data.resolve("autoconfig")).resolve("guardian_ai.txt");
        var inbox = new Inbox<InboxEntry>(32);
        var modes = new OverlayModes(ZoneMode.GUARD, GroupMode.FAIR, DistillMode.OFF, PatternMode.OBSERVE);
        var writer = new StorageWriter(WriterHosts.spec(data.resolve("records"), data.resolve("aggregates.db"), false,
                false, modes), inbox);
        writer.useProfiles(ProfileBases.of(List.of(AutoconfigAdminTest.profile("guardian_ai")),
                Map.of("guardian_ai", BASE)));
        Files.writeString(proposal, "base=" + "2".repeat(64) + "\nZone.Mode=guard\n");
        writer.request(new StorageWriter.Work.Command(new AdminCommand.AutoconfigApply(proposal.toString()), 1));
        var stale = result(inbox, 1);
        assertFalse(stale.ok());
        assertTrue(stale.detail().startsWith("OVERLAY_STALE: ") && stale.detail().contains(BASE), stale.detail());
        Files.writeString(proposal, "base=" + BASE + "\nZone.Mode=guard\n");
        writer.request(new StorageWriter.Work.Command(new AdminCommand.AutoconfigApply(proposal.toString()), 2));
        var applied = result(inbox, 2);
        assertTrue(applied.ok(), applied.detail());
        assertEquals("applied guardian_ai.txt", applied.detail());
        writer.close(3000);
    }

    @Test
    void anImportedVerdictIsDeliveredAgainSoTheGatesSeeItWithoutARestart() throws Exception {
        var inbox = new Inbox<InboxEntry>(32);
        var writer = new StorageWriter(WriterHosts.spec(root.resolve("records"), root.resolve("aggregates.db"), true,
                false, 8, SegmentFiles.DISK, 8), inbox);
        WriterHosts.until(() -> drained(inbox) && loads.size() == 1);
        assertEquals(List.of(), loads.getFirst().rows());
        writer.request(new StorageWriter.Work.Command(new AdminCommand.VerdictImport(source().toString()), 4));
        WriterHosts.until(() -> drained(inbox) && loads.size() == 2);
        assertEquals(List.of(new StorageReply.GateVerdictRow(VERDICT, "G2", "PASS", "run-9", 5L)),
                loads.get(1).rows());
        assertTrue(results.getFirst().ok() && results.getFirst().detail().startsWith("imported " + VERDICT),
                results.getFirst().detail());
        writer.close(3000);
    }

    @Test
    void aServiceWithRecordsAndAggregatesOffServesOnlyTheVerdictsAndSaysSo() throws Exception {
        var records = root.resolve("records");
        VerdictFiles.write(records.resolve("eval/verdicts"), new StorageReply.GateVerdictRow(VERDICT, "G1",
                "PASS_SIM_ONLY", "run-1", 3L));
        var inbox = new Inbox<InboxEntry>(32);
        var service = StorageService.open(WriterHosts.spec(records, root.resolve("aggregates.db"), false, false, 8,
                SegmentFiles.DISK, 8), RecordLevel.OFF, RecordBodies.NONE, QuestionDetail.FULL, AnswerCacheMode.OFF,
                inbox, null, () -> {
                }, () -> 1L);
        WriterHosts.until(() -> drained(inbox) && loads.size() == 1);
        assertEquals(VERDICT, loads.getFirst().rows().getFirst().sha256());
        assertEquals(StorageService.VERDICTS_ONLY, service.status().cause());
        assertNotEquals(StorageStatus.ABSENT, service.status());
        assertEquals(StorageHealth.DISABLED, service.health());
        assertFalse(service.aggregatesAttached() || service.recordTap().isPresent());
        var answers = new ArrayList<StorageReply.AdminResult>();
        assertTrue(service.submit(new AdminCommand.VerdictImport(source().toString()), answers::add));
        WriterHosts.until(() -> drained(inbox) && loads.size() == 2);
        results.forEach(service::deliver);
        assertEquals(1, answers.size());
        assertTrue(answers.getFirst().ok(), answers.getFirst().detail());
        assertEquals(2, loads.get(1).rows().size());
        service.close(3000);
    }

    @Test
    void theServiceHandsTheProfilesReadNowToItsWriter() throws Exception {
        var data = root.resolve("plugins/ThePredict");
        var proposal = Files.createDirectories(data.resolve("autoconfig")).resolve("archer_ai.txt");
        Files.writeString(proposal, "base=" + BASE + "\nZone.Radius=40\n");
        var inbox = new Inbox<InboxEntry>(32);
        var service = StorageService.open(WriterHosts.spec(data.resolve("records"), data.resolve("aggregates.db"),
                false, false, OverlayModes.DEFAULTS), RecordLevel.OFF, RecordBodies.NONE, QuestionDetail.FULL,
                AnswerCacheMode.OFF, inbox, null, () -> {
                }, () -> 1L);
        service.useProfiles(ProfileBases.of(List.of(AutoconfigAdminTest.profile("guardian_ai")),
                Map.of("guardian_ai", BASE)));
        var answers = new ArrayList<StorageReply.AdminResult>();
        assertTrue(service.submit(new AdminCommand.AutoconfigApply(proposal.toString()), answers::add));
        WriterHosts.until(() -> drained(inbox) && !results.isEmpty());
        results.forEach(service::deliver);
        assertFalse(answers.getFirst().ok());
        assertTrue(answers.getFirst().detail().startsWith("OVERLAY_UNMATCHED: "), answers.getFirst().detail());
        service.close(3000);
    }

    private Path source() throws Exception {
        var source = root.resolve("incoming.json");
        Files.writeString(source, "{\"sha256\":\"" + VERDICT + "\",\"gate\":\"G2\",\"verdict\":\"PASS\","
                + "\"run_id\":\"run-9\",\"decided_epoch_ms\":5}\n");
        return source;
    }

    private StorageReply.AdminResult result(Inbox<InboxEntry> inbox, long id) {
        WriterHosts.until(() -> drained(inbox) && results.stream().anyMatch(result -> result.commandId() == id));
        return results.stream().filter(result -> result.commandId() == id).findFirst().orElseThrow();
    }

    private boolean drained(Inbox<InboxEntry> inbox) {
        inbox.drainStorage(32, reply -> {
            if (reply instanceof StorageReply.VerdictsLoaded loaded) {
                loads.add(loaded);
            } else if (reply instanceof StorageReply.AdminResult result) {
                results.add(result);
            }
        });
        return true;
    }
}
