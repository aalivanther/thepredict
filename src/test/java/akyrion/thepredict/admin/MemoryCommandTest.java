package akyrion.thepredict.admin;

import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.StorageControl;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.StorageState;
import akyrion.thepredict.engine.StorageStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCommandTest {
    @Test
    void permissionsQueueAndRejectARepeatedKind() {
        var messages = new ArrayList<String>();
        var pending = new AtomicReference<Consumer<StorageReply.AdminResult>>();
        var seen = new AtomicReference<AdminCommand>();
        var control = control(pending, seen, false);
        var command = new MemoryCommand(control, name -> Optional.empty());
        var stats = command.execute(sender(messages, false), new String[] {"stats"});
        assertTrue(plain(stats.getFirst()).contains("queued"));
        var denied = command.execute(sender(messages, false), new String[] {"show", "Steve"});
        assertTrue(plain(denied.getFirst()).contains("thepredict.memory"));
        pending.get().accept(new StorageReply.AdminResult(1, true, "ok", 1L));
        var unknown = command.execute(sender(messages, true), new String[] {"show", "Steve"});
        assertTrue(plain(unknown.getFirst()).contains("Usage"));
        var id = new UUID(4, 4).toString();
        var queued = command.execute(sender(messages, true), new String[] {"show", id});
        assertTrue(plain(queued.getFirst()).contains("queued"));
        assertEquals(id, ((AdminCommand.MemoryShow) seen.get()).subject());
        var again = command.execute(sender(messages, true), new String[] {"reset", id});
        assertTrue(plain(again.getFirst()).contains("already running"));
        assertEquals(List.of("replace", "newer"), command.complete(new String[] {"import", "memory.jsonl", ""}));
        var adminMessages = new ArrayList<String>();
        var adminSeen = new AtomicReference<AdminCommand>();
        var admin = new MemoryCommand(control(new AtomicReference<>(), adminSeen, false),
                name -> Optional.empty());
        var idOnly = new UUID(6, 6).toString();
        var shown = admin.execute(sender(adminMessages, "thepredict.admin"), new String[] {"show", idOnly});
        assertTrue(plain(shown.getFirst()).contains("queued"));
        assertEquals(idOnly, ((AdminCommand.MemoryShow) adminSeen.get()).subject());
        var named = new AtomicReference<AdminCommand>();
        var player = new UUID(6, 7);
        var export = new MemoryCommand(control(new AtomicReference<>(), named, false),
                name -> "Steve".equals(name) ? Optional.of(player) : Optional.empty());
        export.execute(sender(new ArrayList<>(), "thepredict.memory"), new String[] {"export", "Steve"});
        assertEquals(player.toString(), ((AdminCommand.MemoryExport) named.get()).selector());
        var everyone = new AtomicReference<AdminCommand>();
        new MemoryCommand(control(new AtomicReference<>(), everyone, false), name -> Optional.empty())
                .execute(sender(new ArrayList<>(), "thepredict.memory"), new String[] {"export", "all"});
        assertEquals("all", ((AdminCommand.MemoryExport) everyone.get()).selector());
    }

    @Test
    void aFullWatcherCapAnswersBusy() {
        var lines = new MemoryCommand(control(new AtomicReference<>(), new AtomicReference<>(), true),
                name -> Optional.empty()).execute(sender(new ArrayList<>(), true), new String[] {"export"});
        assertTrue(plain(lines.getFirst()).contains("busy"));
    }

    private static StorageControl control(AtomicReference<Consumer<StorageReply.AdminResult>> pending,
                                          AtomicReference<AdminCommand> seen, boolean full) {
        return new StorageControl() {
            private boolean busy;

            @Override
            public StorageStatus status() {
                return new StorageStatus(StorageState.READY, "", "decisions", 0, 0, 0, "3.49.1", "1", "wal", false,
                        false, "");
            }

            @Override
            public boolean submit(AdminCommand command, Consumer<StorageReply.AdminResult> watcher) {
                if (full || busy) {
                    return false;
                }
                busy = true;
                seen.set(command);
                pending.set(result -> {
                    busy = false;
                    watcher.accept(result);
                });
                return true;
            }

            @Override
            public boolean atCapacity() {
                return full;
            }
        };
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static CommandSender sender(List<String> messages, boolean allowed) {
        return sender(messages, permission -> allowed);
    }

    private static CommandSender sender(List<String> messages, String permission) {
        return sender(messages, name -> name.equals(permission));
    }

    private static CommandSender sender(List<String> messages, java.util.function.Predicate<String> allowed) {
        return (CommandSender) Proxy.newProxyInstance(ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[] {ConsoleCommandSender.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> allowed.test(String.valueOf(arguments[0]));
                    case "sendMessage" -> {
                        if (arguments != null && arguments[0] instanceof Component component) {
                            messages.add(plain(component));
                        }
                        yield null;
                    }
                    case "name", "getName" -> "test";
                    default -> null;
                });
    }
}
