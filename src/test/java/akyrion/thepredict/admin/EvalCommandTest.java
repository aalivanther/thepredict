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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Eval commands queue, deliver their result to the sender and reject a climbing path (M22.11, CAR-09); without a
 * writer they say so instead of {@code busy} (CAR-22).
 */
class EvalCommandTest {
    @Test
    void reportQueuesAndAClimbingPathIsRejected() {
        var watcher = new AtomicReference<Consumer<StorageReply.AdminResult>>();
        var queued = new AtomicReference<AdminCommand>();
        var command = new EvalCommand(accepting(watcher, queued), id -> Optional.of("Normal"), (id, rung) -> true);
        var messages = new ArrayList<String>();
        var sender = sender(true, true, messages);
        assertTrue(plain(command.execute(sender, new String[] {"status"})).contains("preset Normal"));
        assertTrue(plain(command.execute(sender, new String[] {"difficulty", "nearest"})).contains("player"));
        assertTrue(plain(command.execute(sender, new String[] {"report", "day"})).contains("queued"));
        assertInstanceOf(AdminCommand.EvalReport.class, queued.get());
        watcher.get().accept(new StorageReply.AdminResult(1, true, "eval report is not available on the server", 1L));
        assertEquals(List.of(AdminText.PREFIX + "Eval: eval report is not available on the server"), messages);
        assertTrue(plain(command.execute(sender, new String[] {"report", ".."})).contains("path"));
        var id = "00000000-0000-0000-0000-0000000000aa";
        assertTrue(plain(command.execute(sender, new String[] {"difficulty", id})).contains("Normal"));
        assertTrue(plain(command.execute(sender, new String[] {"pressure", id, "3"})).contains("pressure"));
        assertTrue(plain(command.execute(sender(true, false, messages), new String[] {"pressure", id, "3"}))
                .contains("thepredict.debug"));
        assertTrue(plain(command.execute(sender, new String[] {"render"}))
                .contains("report (not available on the server in this release)"), "JS-13: the help says so");
    }

    @Test
    void anImportDeliversItsFailureToTheSender() {
        var watcher = new AtomicReference<Consumer<StorageReply.AdminResult>>();
        var queued = new AtomicReference<AdminCommand>();
        var command = new EvalCommand(accepting(watcher, queued), id -> Optional.empty(), (id, rung) -> false);
        var messages = new ArrayList<String>();
        var sender = sender(true, false, messages);
        assertTrue(plain(command.execute(sender, new String[] {"verdict", "import", "eval/inbox/v.json"}))
                .contains("queued"));
        assertEquals("eval/inbox/v.json", ((AdminCommand.VerdictImport) queued.get()).path());
        watcher.get().accept(new StorageReply.AdminResult(2, false, "verdict", 1L));
        assertEquals(List.of(AdminText.PREFIX + "Eval verdict"), messages);
    }

    @Test
    void verdictListShowsTheLoadedVerdictsWithinItsCap() {
        var command = new EvalCommand(accepting(new AtomicReference<>(), new AtomicReference<>()),
                id -> Optional.empty(), (id, rung) -> false);
        var sender = sender(true, false, new ArrayList<>());
        assertEquals(AdminText.PREFIX + "Eval: no verdict loaded", plain(command.execute(sender, new String[] {"verdict", "list"})));
        var many = new ArrayList<String>();
        for (var index = 0; index < EvalCommand.MAX_VERDICT_LINES + 3; index++) {
            many.add("3f9c1a2b44d" + index + " G1 PASS");
        }
        command.useVerdicts(() -> many);
        var lines = AdminFixtures.plain(command.execute(sender, new String[] {"verdict", "list"}));
        assertEquals(EvalCommand.MAX_VERDICT_LINES, lines.size());
        assertEquals(AdminText.PREFIX + "Verdict: 3f9c1a2b44d0 G1 PASS", lines.getFirst());
    }

    @Test
    void withoutAWriterReportAndImportSaySoInsteadOfBusy() {
        var command = new EvalCommand(StorageControl.NONE, id -> Optional.empty(), (id, rung) -> false);
        var sender = sender(true, false, new ArrayList<>());
        assertEquals(AdminText.PREFIX + "Eval report needs the storage writer.",
                plain(command.execute(sender, new String[] {"report", "day"})));
        assertEquals(AdminText.PREFIX + "Eval verdict import needs the storage writer.",
                plain(command.execute(sender, new String[] {"verdict", "import", "v.json"})));
    }

    private static String plain(List<Component> lines) {
        return String.join("\n", AdminFixtures.plain(lines));
    }

    private static StorageControl accepting(AtomicReference<Consumer<StorageReply.AdminResult>> watcher,
                                            AtomicReference<AdminCommand> queued) {
        return new StorageControl() {
            @Override
            public StorageStatus status() {
                return new StorageStatus(StorageState.READY, "", "decisions", 0, 0, 0, "3.49.1", "1", "wal", false,
                        false, "");
            }

            @Override
            public boolean submit(AdminCommand command, Consumer<StorageReply.AdminResult> next) {
                queued.set(command);
                watcher.set(next);
                return true;
            }

            @Override
            public boolean atCapacity() {
                return false;
            }
        };
    }

    private static CommandSender sender(boolean eval, boolean debug, List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[] {ConsoleCommandSender.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> {
                        var permission = String.valueOf(arguments[0]);
                        yield permission.equals(EvalCommand.PERMISSION) ? eval
                                : permission.equals(ThePredictCommand.DEBUG_PERMISSION) && debug;
                    }
                    case "sendMessage" -> {
                        if (arguments[0] instanceof Component component) {
                            messages.addAll(AdminFixtures.plain(List.of(component)));
                        }
                        yield null;
                    }
                    case "name", "getName" -> "test";
                    default -> null;
                });
    }
}
