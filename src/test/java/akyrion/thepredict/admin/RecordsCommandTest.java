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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordsCommandTest {
    @Test
    void statsNeedNoExtraPermissionAndARepeatedKindIsRejected() {
        var messages = new ArrayList<String>();
        var pending = new AtomicReference<Consumer<StorageReply.AdminResult>>();
        var control = new StorageControl() {
            private boolean busy;

            @Override
            public StorageStatus status() {
                return new StorageStatus(StorageState.READY, "", "decisions", 0, 0, 0, "3.49.1", "1", "wal", false,
                        false, "");
            }

            @Override
            public boolean submit(AdminCommand command, Consumer<StorageReply.AdminResult> watcher) {
                if (busy) {
                    return false;
                }
                busy = true;
                pending.set(watcher);
                return true;
            }
        };
        var command = new RecordsCommand(control);
        var lines = command.execute(sender(messages, false), new String[] {"stats"});
        assertTrue(plain(lines.getFirst()).contains("3.49.1"));
        var denied = command.execute(sender(messages, false), new String[] {"verify"});
        assertTrue(plain(denied.getFirst()).contains("thepredict.records"));
        var queued = command.execute(sender(messages, true), new String[] {"verify"});
        assertTrue(plain(queued.getFirst()).contains("queued"));
        var again = command.execute(sender(messages, true), new String[] {"backup"});
        assertTrue(plain(again.getFirst()).contains("already running"));
        var bad = command.execute(sender(messages, true), new String[] {"tag", "Bad_Tag"});
        assertTrue(plain(bad.getFirst()).contains("Usage"));
        pending.get().accept(new StorageReply.AdminResult(1, true, "ok", 1L));
        assertTrue(messages.stream().anyMatch(line -> line.contains("Records: ok")));
    }

    @Test
    void eightWatchersAnswerBusy() {
        var control = new StorageControl() {
            @Override
            public StorageStatus status() {
                return new StorageStatus(StorageState.READY, "", "decisions", 0, 0, 0, "3.49.1", "1", "wal", false,
                        false, "");
            }

            @Override
            public boolean submit(AdminCommand command, java.util.function.Consumer<StorageReply.AdminResult> watcher) {
                return false;
            }

            @Override
            public boolean atCapacity() {
                return true;
            }
        };
        var lines = new RecordsCommand(control).execute(sender(new ArrayList<>(), true), new String[] {"verify"});
        assertTrue(plain(lines.getFirst()).contains("busy"));
        assertTrue(!plain(lines.getFirst()).contains("already running"));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static CommandSender sender(List<String> messages, boolean allowed) {
        return (CommandSender) Proxy.newProxyInstance(ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[] {ConsoleCommandSender.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> allowed;
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
