package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.domain.OutcomeStatMath;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.AggregateQuery;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.OutcomeStatDelta;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.StorageState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JS-02: a writer that cannot move waits instead of spinning. A turn that moves nothing waits up to 100 ms, so a
 * 300 ms window holds at most 3 idle turns plus the few that still move something; {@link #MOST_TURNS} leaves room for
 * a slow host, while a spinning loop runs tens of thousands of turns in the same window.
 */
class StorageWriterIdleTest {
    private static final long WINDOW_NANOS = 300_000_000L;
    private static final int MOST_TURNS = 12;

    @TempDir
    Path directory;

    @Test
    void aFullDiskDropsThePausedEventsAndTheThreadWaits() throws Exception {
        var records = directory.resolve("full");
        Files.createDirectories(records);
        var writer = new StorageWriter(WriterHosts.spec(records, directory.resolve("full.db"), true, false, 2048,
                file -> failing(), 8), new Inbox<InboxEntry>(8));
        for (var index = 0; index < 1000; index++) {
            assertTrue(writer.offer(new RecordEvent("planned", "20261006T120000Z-0a1b", 1L, 2L, index, "", Map.of())));
        }
        WriterHosts.until(() -> writer.dropped("paused") == 1000);
        assertTrue(writer.pausedSize());
        assertEquals(1000, writer.droppedEvents());
        assertTrue(turnsInWindow(writer) <= MOST_TURNS);
        writer.close(3000);
    }

    @Test
    void aFullReplyBufferWithQueuedWorkWaitsAndThenServes() throws Exception {
        var inbox = blockedInbox();
        var database = directory.resolve("blocked.db");
        var writer = blockedWriter(inbox, database, "blocked");
        for (var id = 1; id <= 5; id++) {
            assertTrue(writer.submit(batch(id)));
        }
        assertTrue(writer.request(new StorageWriter.Work.Query(new AggregateQuery.OutcomeStatsQuery(List.of()))));
        assertTrue(turnsInWindow(writer) <= MOST_TURNS);
        assertEquals(0, rows(database));
        inbox.drainStorage(Inbox.STORAGE_CAPACITY, reply -> {
        });
        WriterHosts.until(() -> {
            inbox.drainStorage(Inbox.STORAGE_CAPACITY, reply -> {
            });
            return rows(database) == 1 && windows(database) == 5;
        });
        writer.close(3000);
    }

    @Test
    void aCloseWithAFullReplyBufferStillAppliesTheQueuedBatches() throws Exception {
        var inbox = blockedInbox();
        var database = directory.resolve("closing.db");
        var writer = blockedWriter(inbox, database, "closing");
        for (var id = 1; id <= 3; id++) {
            assertTrue(writer.submit(batch(id)));
        }
        var report = writer.close(batch(4), 3000);
        assertTrue(report.finished(), report.unapplied());
        assertEquals(4, windows(database));
    }

    private StorageWriter blockedWriter(Inbox<InboxEntry> inbox, Path database, String name) throws Exception {
        var records = directory.resolve(name);
        Files.createDirectories(records);
        var writer = new StorageWriter(WriterHosts.spec(records, database, false, true, 8, SegmentFiles.DISK, 1),
                inbox);
        WriterHosts.until(() -> writer.health().state() == StorageState.READY && !writer.pruning());
        return writer;
    }

    private static Inbox<InboxEntry> blockedInbox() {
        var inbox = new Inbox<InboxEntry>(8);
        for (var index = 0; index < Inbox.STORAGE_CAPACITY; index++) {
            assertTrue(inbox.offerStorage(new StorageReply.BatchApplied(1, index)));
        }
        return inbox;
    }

    private static int turnsInWindow(StorageWriter writer) {
        var before = writer.passes();
        WriterHosts.during(WINDOW_NANOS);
        return writer.passes() - before;
    }

    private static AggregateBatch batch(long id) {
        var key = new OutcomeStatKey("guardian", new TacticId("keep_distance"), Movement.APPROACH, ControlLevel.JEV,
                "v1:NONE:NONE");
        var stat = OutcomeStatMath.add(OutcomeStat.EMPTY, 0.25, 0, 0, false, false, false, 0, 0);
        return new AggregateBatch(id, List.of(new OutcomeStatDelta(key, stat, id)), List.of(), List.of());
    }

    private static long rows(Path database) {
        return query(database, "SELECT COUNT(*) FROM outcome_stats");
    }

    private static long windows(Path database) {
        return query(database, "SELECT COALESCE(SUM(windows), 0) FROM outcome_stats");
    }

    private static long query(Path database, String sql) {
        try (var connection = SqliteConnections.readOnly(database);
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            return result.next() ? result.getLong(1) : 0L;
        } catch (java.sql.SQLException exception) {
            return -1L;
        }
    }

    private static Writer failing() {
        return new Writer() {
            @Override
            public void write(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("No space left on device");
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }
}
