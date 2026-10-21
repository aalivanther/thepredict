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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Distill commands are not learn commands. Mode is the only raise. Disable is the next capture. */
class DistillCommandTest {
    @Test
    void modeAndDisableDoNotQueueLearnWork() {
        var seen = new AtomicReference<AdminCommand>();
        var command = new DistillCommand(control(seen), () -> "state=NONE mode=off regime=r1:ALL");
        var sender = sender();
        var mode = command.execute(sender, new String[] {"mode", "assist"});
        assertTrue(plain(mode.getFirst()).contains("queued"));
        assertInstanceOf(AdminCommand.DistillModeSet.class, seen.get());
        assertEquals("assist", ((AdminCommand.DistillModeSet) seen.get()).mode());
        var disable = command.execute(sender, new String[] {"disable", "all"});
        assertInstanceOf(AdminCommand.DistillDisable.class, seen.get());
        var build = command.execute(sender, new String[] {"build"});
        assertTrue(plain(build.getFirst()).contains("queued"));
        assertInstanceOf(AdminCommand.DistillBuild.class, seen.get());
        var inspect = command.execute(sender, new String[] {"inspect", "Steve"});
        var line = plain(inspect.getFirst());
        assertTrue(line.contains("mode=off"));
        assertTrue(!line.contains("Steve"));
        var usage = plain(command.execute(sender, new String[] {"train"}).getFirst());
        assertTrue(usage.contains("build [profile] (reads the table; distillTrain builds the .tpld)"), usage);
    }

    private static StorageControl control(AtomicReference<AdminCommand> seen) {
        return new StorageControl() {
            @Override
            public StorageStatus status() {
                return new StorageStatus(StorageState.READY, "", "decisions", 0, 0, 0, "3.49.1", "1", "wal", false,
                        false, "");
            }

            @Override
            public boolean submit(AdminCommand command, Consumer<StorageReply.AdminResult> watcher) {
                seen.set(command);
                return true;
            }

            @Override
            public boolean atCapacity() {
                return false;
            }
        };
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static CommandSender sender() {
        return (CommandSender) Proxy.newProxyInstance(ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[] {ConsoleCommandSender.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> true;
                    case "name", "getName" -> "test";
                    default -> null;
                });
    }
}
