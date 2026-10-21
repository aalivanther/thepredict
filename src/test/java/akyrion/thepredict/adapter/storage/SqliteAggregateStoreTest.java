package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatMath;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.OutcomeStatDelta;
import akyrion.thepredict.engine.StorageReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteAggregateStoreTest {
    @TempDir
    Path directory;

    @Test
    void aBatchIsAppliedOnceWhenRepeatedAndReadsStopWhenTheReplyBufferIsFull() throws Exception {
        var records = directory.resolve("records");
        var database = directory.resolve("aggregates.db");
        Files.createDirectories(records);
        var inbox = new Inbox<InboxEntry>(8);
        var writer = new StorageWriter(WriterHosts.spec(records, database, false, true, 8, SegmentFiles.DISK, 2), inbox);
        var stat = OutcomeStatMath.add(OutcomeStat.EMPTY, 0.5, 0.1, 0.2, true, false, false, 1, 1);
        var batch = new AggregateBatch(1, List.of(new OutcomeStatDelta(key(), stat, 5L)), List.of(), List.of());
        assertTrue(writer.submit(batch));
        WriterHosts.until(() -> inbox.drainStorage(8, reply -> {
        }) > 0 || writer.passes() > 2);
        writer.submit(batch);
        writer.close(null, 3000);
        try (var connection = SqliteConnections.open(database, "normal", 2000);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT windows, effect_mean FROM outcome_stats")) {
            assertTrue(result.next());
            assertEquals(stat.windows(), result.getLong(1));
            assertEquals(stat.effectMean(), result.getDouble(2), 1e-9);
            assertTrue(!result.next());
        }
        var blockedInbox = new Inbox<InboxEntry>(8);
        for (var index = 0; index < Inbox.STORAGE_CAPACITY; index++) {
            assertTrue(blockedInbox.offerStorage(new StorageReply.BatchApplied(1, index)));
        }
        var blockedRecords = directory.resolve("blocked-records");
        Files.createDirectories(blockedRecords);
        var blocked = new StorageWriter(WriterHosts.spec(blockedRecords, directory.resolve("blocked.db"), true, true, 8,
                SegmentFiles.DISK, 1), blockedInbox, WriterTrace.bounded(WriterTrace.MAX_WORDS));
        WriterHosts.until(() -> blocked.passes() > 0 && blocked.trace().contains("load"));
        var before = blocked.trace();
        assertEquals(0L, before.stream().filter(word -> word.equals("read")).count());
        assertEquals(0L, before.stream().filter(word -> word.equals("batch")).count());
        assertTrue(blocked.offer(new RecordEvent("planned", "20261006T120000Z-0a1b", 1L, 2L, 3L, "", java.util.Map.of())));
        for (var id = 2; id <= 40; id++) {
            assertTrue(blocked.submit(new AggregateBatch(id, List.of(new OutcomeStatDelta(key(), stat, id)), List.of(),
                    List.of())));
        }
        assertTrue(blocked.request(new StorageWriter.Work.Query(
                new akyrion.thepredict.engine.AggregateQuery.OutcomeStatsQuery(List.of("guardian")))));
        var seen = blocked.passes();
        WriterHosts.until(() -> blocked.passes() > seen && blocked.trace().contains("event"));
        var after = blocked.trace();
        assertEquals(0L, after.stream().filter(word -> word.equals("read")).count());
        assertEquals(0L, after.stream().filter(word -> word.equals("batch")).count());
        assertTrue(after.contains("event"));
        blockedInbox.drainStorage(Inbox.STORAGE_CAPACITY, reply -> {
        });
        blocked.close(null, 3000);
    }

    private static OutcomeStatKey key() {
        return new OutcomeStatKey("guardian", new TacticId("keep_distance"), Movement.APPROACH, ControlLevel.JEV,
                "v1:NONE:NONE");
    }
}
