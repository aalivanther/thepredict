package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.AggregateCache;
import akyrion.thepredict.engine.AggregateStore;
import akyrion.thepredict.engine.AnswerCache;
import akyrion.thepredict.engine.DecisionDispatcher;
import akyrion.thepredict.engine.DecisionMetrics;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.OutcomeWindow;
import akyrion.thepredict.engine.StorageHealth;
import akyrion.thepredict.engine.StorageMetrics;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.StorageState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Local M21 timings (M21.0). Not part of {@code check}. Writes {@code volume.md} and {@code baseline.md}.
 */
public final class M21Measurements {
    private M21Measurements() {
    }

    /**
     * Writes the two reports. No arguments prints the usage and returns.
     *
     * @param arguments the report directory
     * @throws Exception when a report cannot be written
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 0) {
            System.out.println("usage: M21Measurements <report-directory>");
            return;
        }
        var directory = Path.of(arguments[0]);
        Files.createDirectories(directory);
        var volume = new StringBuilder();
        volume.append("# M21 volume\n\n");
        volume.append("| row | p50 µs | p99 µs |\n|---|---:|---:|\n");
        var events = new ArrayBlockingQueue<RecordEvent>(16384);
        var tap = new RecordTap(RecordLevel.DECISIONS, RecordBodies.HASH, "20261006T120000Z-0a1b", Clock.systemUTC(),
                () -> 1L, System::nanoTime, events::offer);
        var offer = time(() -> tap.questionsPlanned(new akyrion.thepredict.domain.EncounterId(1), 1L, java.util.List.of(),
                null, DecisionDispatcher.Outcome.SENT, null), 2000);
        row(volume, "tap.recordOffer", offer);
        var verdicts = time(() -> tap.questionsPlanned(new akyrion.thepredict.domain.EncounterId(1), 1L, java.util.List.of(),
                null, DecisionDispatcher.Outcome.SENT, null), 2000);
        row(volume, "tap.verdicts", verdicts);
        var metrics = new StorageMetrics();
        var inbox = new Inbox<InboxEntry>(64);
        var cache = new AggregateCache(ready(), metrics, 8192, 1, 4, inbox);
        for (var keys : new int[] {256, 1024, 4096}) {
            var flush = timePrepared(() -> {
                cache.drain(8);
                for (var index = 0; index < keys; index++) {
                    cache.outcomeClosed("p" + index, summary(0.1), index);
                }
            }, () -> cache.flush(0, keys).ifPresent(batch ->
                    inbox.offerStorage(new StorageReply.BatchApplied(batch.batchId(), 1L))), 20);
            row(volume, "aggregate.flush " + keys, flush);
        }
        var applied = timePrepared(() -> {
            cache.drain(8);
            for (var index = 0; index < 1024; index++) {
                cache.outcomeClosed("b" + index, summary(0.2), index);
            }
            cache.flush(1, 1024).ifPresent(batch ->
                    inbox.offerStorage(new StorageReply.BatchApplied(batch.batchId(), 1L)));
        }, () -> cache.drain(1), 20);
        row(volume, "aggregate.batchApplied 1024", applied);
        for (var chars : new int[] {1000, 3000, 8000}) {
            var body = "x".repeat(chars);
            var answers = new AnswerCache(AnswerCacheMode.ON, 100, 64, 0.0, 1L, 4, new DecisionMetrics(() -> 0L));
            var lookup = time(() -> answers.lookup("jev-1.0.0", body, Set.of(new QuestionKey("q0")), 1L), 500);
            row(volume, "answerCache.lookup " + chars, lookup);
        }
        Files.writeString(directory.resolve("volume.md"), volume.toString());
        var baseline = new StringBuilder();
        baseline.append("# M21 baseline\n\n");
        baseline.append("sqlite ").append(probeSqlite()).append("\n\n");
        baseline.append("| level | bodies | lines | bytes |\n|---|---|---:|---:|\n");
        for (var level : new RecordLevel[] {RecordLevel.DECISIONS, RecordLevel.FULL}) {
            for (var bodies : new RecordBodies[] {RecordBodies.NONE, RecordBodies.HASH, RecordBodies.ALL}) {
                var lines = new ArrayList<String>();
                var recorder = new RecordTap(level, bodies, "20261006T120000Z-0a1b", Clock.systemUTC(), () -> 1L,
                        () -> 2L, event -> lines.add(RecordLine.format(event)));
                var body = "body-".repeat(40);
                for (var index = 0; index < 200; index++) {
                    recorder.questionsPlanned(new akyrion.thepredict.domain.EncounterId(1), index, java.util.List.of(),
                            null, DecisionDispatcher.Outcome.SENT, null);
                    recorder.requestBody(new akyrion.thepredict.domain.EncounterId(1), index, body);
                    if (level == RecordLevel.FULL) {
                        recorder.round(1L, 10L);
                    }
                }
                var bytes = lines.stream().mapToInt(line -> line.length() + 1).sum();
                baseline.append("| ").append(level.name().toLowerCase(java.util.Locale.ROOT)).append(" | ")
                        .append(bodies.name().toLowerCase(java.util.Locale.ROOT)).append(" | ").append(lines.size())
                        .append(" | ").append(bytes).append(" |\n");
            }
        }
        var repeats = new int[] {0, 0, 0};
        var windows = new int[] {10, 30, 60};
        for (var slot = 0; slot < windows.length; slot++) {
            var seen = new java.util.HashSet<Integer>();
            var repeated = 0;
            for (var index = 0; index < 400; index++) {
                var hash = ("body-" + (index % windows[slot])).hashCode();
                if (!seen.add(hash)) {
                    repeated++;
                }
            }
            repeats[slot] = repeated;
        }
        baseline.append("\nBody repeats in a synthetic 10/30/60 s window (lower bound, DT-53): ")
                .append(repeats[0]).append(", ").append(repeats[1]).append(", ").append(repeats[2]).append(".\n");
        baseline.append("\ntap.verdicts p99 µs ").append(format(verdicts[1])).append(".\n");
        Files.writeString(directory.resolve("baseline.md"), baseline.toString());
        System.out.println("tap.verdicts p99 µs " + format(verdicts[1]));
    }

    private static void row(StringBuilder report, String name, long[] nanos) {
        report.append("| ").append(name).append(" | ").append(format(nanos[0])).append(" | ")
                .append(format(nanos[1])).append(" |\n");
    }

    private static String format(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1000.0);
    }

    private static long[] time(Runnable work, int samples) {
        for (var warm = 0; warm < 10; warm++) {
            work.run();
        }
        var values = new long[samples];
        for (var index = 0; index < samples; index++) {
            var start = System.nanoTime();
            work.run();
            values[index] = System.nanoTime() - start;
        }
        Arrays.sort(values);
        return new long[] {values[values.length / 2], values[(int) (values.length * 0.99)]};
    }

    private static long[] timePrepared(Runnable prepare, Runnable work, int samples) {
        for (var warm = 0; warm < 3; warm++) {
            prepare.run();
            work.run();
        }
        var values = new long[samples];
        for (var index = 0; index < samples; index++) {
            prepare.run();
            var start = System.nanoTime();
            work.run();
            values[index] = System.nanoTime() - start;
        }
        Arrays.sort(values);
        return new long[] {values[values.length / 2], values[(int) (values.length * 0.99)]};
    }

    private static AggregateStore ready() {
        return new AggregateStore() {
            @Override
            public StorageHealth health() {
                return new StorageHealth(StorageState.READY, "", 0, 0, 0);
            }

            @Override
            public boolean submit(akyrion.thepredict.engine.AggregateBatch batch) {
                return true;
            }

            @Override
            public boolean request(akyrion.thepredict.engine.AggregateQuery query) {
                return true;
            }
        };
    }

    private static OutcomeWindow.Summary summary(double effect) {
        return new OutcomeWindow.Summary(ControlLevel.JEV, new TacticId("keep_distance"), Movement.APPROACH, 0, 1,
                0, 0, false, 0, 0, false, false, effect);
    }

    private static String probeSqlite() {
        try {
            var file = Files.createTempFile("m21-sqlite", ".db");
            var version = SqliteConnections.version(file);
            Files.deleteIfExists(file);
            return version;
        } catch (Exception exception) {
            return exception.getClass().getSimpleName();
        }
    }
}
