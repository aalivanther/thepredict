package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.AggregateQuery;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.StorageState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JS-33: the pending digests of a player are dropped only when the writer accepted the reset. */
class StorageServiceResetTest {
    @TempDir
    Path directory;

    @Test
    void aResetTheWriterRefusesLeavesThePendingDigestsAndAnAcceptedOneDropsThem() {
        var inbox = new Inbox<InboxEntry>(8);
        var service = blockedService(inbox);
        var resets = new ArrayList<String>();
        service.onMemoryReset(resets::add);
        var queued = 0;
        while (service.writer().request(new StorageWriter.Work.Query(new AggregateQuery.OutcomeStatsQuery(List.of())))) {
            queued++;
        }
        assertEquals(256, queued);
        var subject = new UUID(4, 2).toString();
        var results = new ArrayList<StorageReply.AdminResult>();
        assertFalse(service.submit(new AdminCommand.MemoryReset(subject), results::add));
        assertTrue(resets.isEmpty(), "a refused reset keeps the digests");
        inbox.drainStorage(Inbox.STORAGE_CAPACITY, reply -> {
        });
        WriterHosts.until(() -> {
            inbox.drainStorage(Inbox.STORAGE_CAPACITY, reply -> {
            });
            return service.submit(new AdminCommand.MemoryReset(subject), results::add);
        });
        assertEquals(List.of(subject), resets);
        service.close(3000);
    }

    /** A service whose engine inbox is full, so the writer holds every answer and its read queue fills. */
    private StorageService blockedService(Inbox<InboxEntry> inbox) {
        for (var index = 0; index < Inbox.STORAGE_CAPACITY; index++) {
            assertTrue(inbox.offerStorage(new StorageReply.BatchApplied(1, index)));
        }
        var service = StorageService.open(WriterHosts.spec(directory.resolve("records"),
                directory.resolve("reset.db"), false, true, 8, SegmentFiles.DISK, 1), RecordLevel.OFF,
                RecordBodies.NONE, QuestionDetail.FULL, AnswerCacheMode.OFF, inbox, null, () -> {
                }, () -> 1L);
        WriterHosts.until(() -> service.writer().health().state() == StorageState.READY);
        return service;
    }
}
