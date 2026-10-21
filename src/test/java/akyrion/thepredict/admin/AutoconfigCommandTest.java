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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Apply writes an overlay command, not a learn import. Paths outside autoconfig are refused. */
class AutoconfigCommandTest {
    @Test
    void applyIsNotALearnImportAndAForeignPathIsRefused() {
        var seen = new AtomicReference<AdminCommand>();
        var command = new AutoconfigCommand(control(seen));
        var sender = sender();
        var path = "/tmp/plugins/ThePredict/autoconfig/proposal.txt";
        var apply = command.execute(sender, new String[] {"apply", path});
        assertTrue(plain(apply.getFirst()).contains("queued"));
        assertInstanceOf(AdminCommand.AutoconfigApply.class, seen.get());
        var refused = command.execute(sender, new String[] {"apply", "/tmp/plugins/MythicMobs/AIConfig/guardian.yml"});
        assertTrue(plain(refused.getFirst()).contains("plugins/ThePredict/autoconfig/"));
        var revert = command.execute(sender, new String[] {"revert", path});
        assertInstanceOf(AdminCommand.AutoconfigRevert.class, seen.get());
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
