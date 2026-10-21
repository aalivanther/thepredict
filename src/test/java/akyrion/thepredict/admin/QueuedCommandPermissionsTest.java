package akyrion.thepredict.admin;

import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.StorageControl;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.StorageState;
import akyrion.thepredict.engine.StorageStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAR-05: {@code thepredict.admin}, which every {@code /thepredict} already needs, never stands in for the permission
 * of {@code learn}, {@code distill} or {@code autoconfig}; only {@code learn status} accepts it. CAR-22: without a
 * writer the queued commands say so instead of claiming that a command is already running.
 */
class QueuedCommandPermissionsTest {
    private static final String PATH = "/tmp/plugins/ThePredict/autoconfig/guardian_ai.txt";

    @Test
    void theAdminPermissionAloneQueuesNoLearnDistillOrAutoconfigCommand() {
        var submitted = new AtomicInteger();
        var storage = ready(submitted);
        var admin = sender(Set.of("thepredict.admin"));
        assertEquals("You need the thepredict.learn permission.", first(new LearnCommand(storage, () -> "learn none")
                .execute(admin, new String[] {"rollback"})));
        assertEquals("You need the thepredict.distill permission.", first(new DistillCommand(storage, () -> "NONE")
                .execute(admin, new String[] {"mode", "autonomous"})));
        assertEquals("You need the thepredict.distill permission.", first(new DistillCommand(storage, () -> "NONE")
                .execute(admin, new String[] {"status"})));
        assertEquals("You need the thepredict.autoconfig permission.", first(new AutoconfigCommand(storage)
                .execute(admin, new String[] {"apply", PATH})));
        assertEquals(0, submitted.get());
        assertEquals("Learn: learn none", first(new LearnCommand(storage, () -> "learn none")
                .execute(admin, new String[] {"status"})));
    }

    @Test
    void theirOwnPermissionQueuesThem() {
        var submitted = new AtomicInteger();
        var storage = ready(submitted);
        assertEquals("Learn: queued", first(new LearnCommand(storage, () -> "")
                .execute(sender(Set.of("thepredict.learn")), new String[] {"rollback"})));
        assertEquals("Distill: queued", first(new DistillCommand(storage, () -> "")
                .execute(sender(Set.of("thepredict.distill")), new String[] {"mode", "shadow"})));
        assertEquals("Autoconfig: queued", first(new AutoconfigCommand(storage)
                .execute(sender(Set.of("thepredict.autoconfig")), new String[] {"apply", PATH})));
        assertEquals(3, submitted.get());
    }

    @Test
    void withoutAWriterEveryQueuedCommandSaysSo() {
        var all = sender(Set.of("thepredict.memory", "thepredict.records", "thepredict.learn"));
        assertEquals("Memory stats needs the storage writer.", first(new MemoryCommand(StorageControl.NONE,
                name -> java.util.Optional.empty()).execute(all, new String[] {"stats"})));
        assertEquals("Records verify needs the storage writer.", first(new RecordsCommand(StorageControl.NONE)
                .execute(all, new String[] {"verify"})));
        var locked = held();
        assertEquals("Learn build needs the storage writer.", first(new LearnCommand(locked, () -> "")
                .execute(all, new String[] {"build"})));
    }

    private static String first(List<Component> lines) {
        return AdminFixtures.plain(lines).getFirst().substring(AdminText.PREFIX.length());
    }

    private static StorageControl ready(AtomicInteger submitted) {
        return control(new StorageStatus(StorageState.READY, "", "decisions", 0, 0, 0, "3.49.1", "1", "wal", false,
                false, ""), submitted);
    }

    private static StorageControl held() {
        return control(new StorageStatus(StorageState.DISABLED, "LOCK_HELD", "decisions", 0, 0, 0, "", "", "", false,
                false, "LOCK_HELD"), new AtomicInteger());
    }

    private static StorageControl control(StorageStatus status, AtomicInteger submitted) {
        return new StorageControl() {
            @Override
            public StorageStatus status() {
                return status;
            }

            @Override
            public boolean submit(AdminCommand command, Consumer<StorageReply.AdminResult> watcher) {
                submitted.incrementAndGet();
                return status.state() != StorageState.DISABLED;
            }
        };
    }

    private static CommandSender sender(Set<String> permissions) {
        return (CommandSender) Proxy.newProxyInstance(ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[] {ConsoleCommandSender.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> permissions.contains(String.valueOf(arguments[0]));
                    case "name", "getName" -> "test";
                    default -> null;
                });
    }
}
