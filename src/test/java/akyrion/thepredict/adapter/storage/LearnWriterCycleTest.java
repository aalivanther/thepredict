package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.admin.LearnCommand;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;
import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.AggregateQuery;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.StorageControl;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.StorageState;
import akyrion.thepredict.engine.StorageStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearnWriterCycleTest {
    @TempDir
    Path directory;

    @Test
    void pagesAndTheFiveCommandsRunOnTheStorageThread() throws Exception {
        var records = directory.resolve("records");
        Files.createDirectories(records);
        var inbox = new Inbox<InboxEntry>(32);
        var writer = new StorageWriter(WriterHosts.spec(records, directory.resolve("aggregates.db"), false, true, 8,
                SegmentFiles.DISK, 32), inbox);
        WriterHosts.until(() -> writer.health().state() == StorageState.READY);
        assertTrue(writer.submit(batch(1, delta("press"))));
        awaitBatch(inbox, 1);
        var first = page(writer, inbox, "");
        assertEquals(1, first.rows().size());
        assertEquals("press", first.rows().getFirst().key().tactic());
        assertEquals(4, first.rows().getFirst().windows());
        var absent = new LearnCommand(StorageControl.NONE, () -> "status").execute(sender(), new String[] {"build"});
        assertTrue(plain(absent.getFirst()).contains("needs the storage writer"));
        var control = control(writer);
        var command = new LearnCommand(control, () -> "status");
        assertTrue(plain(command.execute(sender(), new String[] {"build"}).getFirst()).contains("queued"));
        var built = awaitAdmin(inbox);
        assertTrue(built.contains(StorageWriter.THREAD_NAME));
        var sha = sha(built);
        assertTrue(writer.submit(batch(2, delta("keep"))));
        awaitBatch(inbox, 2);
        var head = page(writer, inbox, "");
        assertEquals(1, head.rows().size());
        assertFalse(head.nextCursor().isEmpty());
        var tail = page(writer, inbox, head.nextCursor());
        assertEquals(1, tail.rows().size());
        assertTrue(plain(command.execute(sender(), new String[] {"build"}).getFirst()).contains("queued"));
        var again = awaitAdmin(inbox);
        var second = sha(again);
        assertFalse(second.equals(sha));
        assertTrue(plain(command.execute(sender(), new String[] {"export", second, "bundle.tplv"}).getFirst())
                .contains("queued"));
        assertTrue(awaitAdmin(inbox).contains("exported " + second));
        assertTrue(plain(command.execute(sender(), new String[] {"import", "bundle.tplv"}).getFirst()).contains("queued"));
        assertTrue(awaitAdmin(inbox).contains("imported " + second));
        assertTrue(plain(command.execute(sender(), new String[] {"rollback"}).getFirst()).contains("queued"));
        assertTrue(awaitAdmin(inbox).contains("rolled " + sha));
        assertTrue(plain(command.execute(sender(), new String[] {"disable", "value"}).getFirst()).contains("queued"));
        assertTrue(awaitAdmin(inbox).contains("disabled value"));
        var active = Files.readString(records.resolveSibling("learn").resolve("active.json"));
        assertTrue(active.contains("\"value\":\"\""));
        var loaded = new AtomicReference<StorageReply.ArtifactsLoaded>();
        assertTrue(writer.request(new StorageWriter.Work.Query(new AggregateQuery.ArtifactsQuery())));
        WriterHosts.until(() -> {
            inbox.drainStorage(16, reply -> {
                if (reply instanceof StorageReply.ArtifactsLoaded artifacts) {
                    loaded.set(artifacts);
                }
            });
            return loaded.get() != null && loaded.get().sha256().contains(second);
        });
        assertFalse(Files.readString(Path.of("src/main/java/akyrion/thepredict/admin/LearnCommand.java")).contains("sqlite"));
        assertFalse(Files.readString(Path.of("src/main/java/akyrion/thepredict/admin/LearnCommand.java")).contains("new Thread"));
        writer.close(null, 3000);
    }

    private static StorageReply.ValueStatsPage page(StorageWriter writer, Inbox<InboxEntry> inbox, String cursor) {
        var query = new AggregateQuery.ValueStatsQuery("guardian", 0, 1, cursor);
        assertTrue(writer.request(new StorageWriter.Work.Query(query)));
        var page = new AtomicReference<StorageReply.ValueStatsPage>();
        WriterHosts.until(() -> {
            inbox.drainStorage(16, reply -> {
                if (reply instanceof StorageReply.ValueStatsPage values && values.rows().size() <= 1) {
                    page.set(values);
                }
            });
            return page.get() != null;
        });
        return page.get();
    }

    private static void awaitBatch(Inbox<InboxEntry> inbox, long id) {
        var seen = new AtomicReference<Long>();
        WriterHosts.until(() -> {
            inbox.drainStorage(16, reply -> {
                if (reply instanceof StorageReply.BatchApplied applied && applied.batchId() == id) {
                    seen.set(id);
                }
            });
            return id == (seen.get() == null ? 0L : seen.get());
        });
    }

    private static String awaitAdmin(Inbox<InboxEntry> inbox) {
        var detail = new AtomicReference<String>();
        WriterHosts.until(() -> {
            inbox.drainStorage(16, reply -> {
                if (reply instanceof StorageReply.AdminResult result) {
                    detail.set(result.detail());
                }
            });
            return detail.get() != null;
        });
        assertTrue(detail.get().contains(StorageWriter.THREAD_NAME), detail.get());
        return detail.get();
    }

    private static String sha(String detail) {
        for (var word : detail.split(" ")) {
            if (word.length() == 64) {
                return word;
            }
        }
        throw new AssertionError(detail);
    }

    private static AggregateBatch batch(long id, ValueStatDelta delta) {
        return new AggregateBatch(id, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(delta));
    }

    private static ValueStatDelta delta(String tactic) {
        var now = System.currentTimeMillis();
        var key = new ValueStatKey("guardian", tactic, "v2:MELEE:HIGH:MID:MOB:OPEN:SOLO:NONE", "SIM", "r1",
                ValueStatKey.week(now));
        return new ValueStatDelta(key, 4, 0.25, 0.01, 0.2, 0.1, 0.0, 1, 1, now);
    }

    private static StorageControl control(StorageWriter writer) {
        return new StorageControl() {
            @Override
            public StorageStatus status() {
                return new StorageStatus(StorageState.READY, "", "decisions", 0, 0, 0, "", "", "", false, false, "");
            }

            @Override
            public boolean submit(AdminCommand command, Consumer<StorageReply.AdminResult> watcher) {
                return writer.request(new StorageWriter.Work.Command(command, 7));
            }
        };
    }

    private static CommandSender sender() {
        return (CommandSender) Proxy.newProxyInstance(ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[] {ConsoleCommandSender.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> true;
                    case "sendMessage" -> null;
                    case "name", "getName" -> "test";
                    default -> null;
                });
    }

    private static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }
}
