package akyrion.thepredict.adapter.storage;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared writer specs for the M21 tests.
 */
final class WriterHosts {
    private WriterHosts() {
    }

    static StorageWriter.Spec spec(Path records, Path database, boolean recordsOn, boolean aggregates, int queue,
                                   SegmentFiles.Opener opener, int replies) {
        return new StorageWriter.Spec(records, database, recordsOn, aggregates, queue, 64L * 1024L * 1024L,
                60L * 60_000_000_000L, false, 14, 2_048L * 1024L * 1024L, "normal", 2000, 0, 7,
                "20261006T120000Z-0a1b", "0.0.1", "jev-1.0.0", "abc", opener, replies);
    }

    static StorageWriter.Spec spec(Path records, Path database, StorageLimits limits) {
        var base = spec(records, database, false, true, 8, SegmentFiles.DISK, 8);
        return new StorageWriter.Spec(base.directory(), base.database(), base.records(), base.aggregates(),
                base.queueCapacity(), base.segmentBytes(), base.segmentNanos(), base.compress(), base.retentionDays(),
                base.maxBytes(), base.synchronous(), base.busyTimeoutMillis(), base.backupHours(), base.backupsKept(),
                base.run(), base.pluginVersion(), base.model(), base.configSha256(), base.opener(),
                base.replyCapacity(), limits);
    }

    static StorageWriter.Spec spec(Path records, Path database, boolean recordsOn, boolean aggregates,
                                   OverlayModes modes) {
        var base = spec(records, database, recordsOn, aggregates, 8, SegmentFiles.DISK, 8);
        return new StorageWriter.Spec(base.directory(), base.database(), base.records(), base.aggregates(),
                base.queueCapacity(), base.segmentBytes(), base.segmentNanos(), base.compress(), base.retentionDays(),
                base.maxBytes(), base.synchronous(), base.busyTimeoutMillis(), base.backupHours(), base.backupsKept(),
                base.run(), base.pluginVersion(), base.model(), base.configSha256(), base.opener(),
                base.replyCapacity(), base.limits(), modes);
    }

    static void until(BooleanSupplier condition) {
        var deadline = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("timed out");
            }
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        }
    }

    /**
     * Lets {@code nanos} of wall time pass, for a test that counts what the writer did in that window. No result
     * depends on an ordering inside the window: the count is compared with a bound that holds for any schedule.
     */
    static void during(long nanos) {
        var end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        }
    }
}
