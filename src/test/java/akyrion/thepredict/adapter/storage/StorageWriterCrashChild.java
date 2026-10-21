package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OutcomeStat;
import akyrion.thepredict.domain.OutcomeStatKey;
import akyrion.thepredict.domain.OutcomeStatMath;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.OutcomeStatDelta;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.StorageState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Child process of {@link StorageWriterCrashTest} (P8, JS-16). It runs a real {@link StorageWriter} that keeps
 * writing events and one-window batches, writes the marker once a given batch is applied, and keeps working until the
 * parent kills it, so the kill lands in the middle of the work.
 */
public final class StorageWriterCrashChild {
    /** The run id of the child. */
    static final String RUN = "20261014T120000Z-c0de";
    /** The key every batch adds one window to. */
    static final OutcomeStatKey KEY = new OutcomeStatKey("guardian", new TacticId("keep_distance"), Movement.APPROACH,
            ControlLevel.JEV, "v1:NONE:NONE");

    private StorageWriterCrashChild() {
    }

    /**
     * Works until killed.
     *
     * @param arguments records folder, database file, marker file and the batch that writes the marker
     * @throws Exception when the marker cannot be written
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4) {
            System.out.println("usage: StorageWriterCrashChild <records> <database> <marker> <batch>");
            return;
        }
        var records = Path.of(arguments[0]);
        Files.createDirectories(records);
        var inbox = new Inbox<InboxEntry>(64);
        var writer = new StorageWriter(new StorageWriter.Spec(records, Path.of(arguments[1]), true, true, 16_384,
                64L * 1024L * 1024L, 60L * 60_000_000_000L, false, 14, 2_048L * 1024L * 1024L, "normal", 2000, 0, 7,
                RUN, "0.0.1", "jev-1.13.0", "abc", SegmentFiles.DISK, 256), inbox);
        work(writer, inbox, Path.of(arguments[2]), Long.parseLong(arguments[3]));
    }

    private static void work(StorageWriter writer, Inbox<InboxEntry> inbox, Path marker, long target)
            throws Exception {
        var applied = new AtomicLong();
        var stat = OutcomeStatMath.add(OutcomeStat.EMPTY, 0.25, 0, 0, false, false, false, 0, 0);
        var next = 1L;
        var marked = false;
        for (var tick = 0L; ; tick++) {
            writer.offer(new RecordEvent("planned", RUN, System.currentTimeMillis(), System.nanoTime(), tick, "",
                    Map.of("n", Long.toString(tick))));
            if (writer.health().state() == StorageState.READY && writer.submit(new AggregateBatch(next,
                    List.of(new OutcomeStatDelta(KEY, stat, next)), List.of(), List.of()))) {
                next++;
            }
            inbox.drainStorage(Inbox.STORAGE_CAPACITY, reply -> {
                if (reply instanceof StorageReply.BatchApplied batch) {
                    applied.accumulateAndGet(batch.batchId(), Math::max);
                }
            });
            if (!marked && applied.get() >= target) {
                Files.writeString(marker, "READY\n", StandardCharsets.UTF_8);
                System.out.println("READY");
                System.out.flush();
                marked = true;
            }
            LockSupport.parkNanos(200_000L);
        }
    }
}
