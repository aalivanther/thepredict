package akyrion.thepredict.admin;

import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.StorageControl;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * {@code /thepredict learn} (M28.9). Status is read from memory and needs {@link #PERMISSION} or
 * {@code thepredict.admin}. Build, export, import, rollback and disable need {@link #PERMISSION} itself (CAR-05) and
 * are queued on the storage writer. This command does not open SQLite and does not start a thread.
 */
public final class LearnCommand {
    /** Permission of {@code /thepredict learn}. */
    public static final String PERMISSION = "thepredict.learn";

    /** Words completed after {@code learn}. */
    public static final List<String> ACTIONS = List.of("status", "build", "export", "import", "rollback", "disable");

    private static final String USAGE = "Usage: /thepredict learn [status|build|export|import|rollback|disable]";

    private final StorageControl storage;
    private final Supplier<String> status;

    /**
     * Creates the command.
     *
     * @param storage the store; {@link StorageControl#NONE} keeps the five commands unanswered
     * @param status one status line
     */
    public LearnCommand(StorageControl storage, Supplier<String> status) {
        this.storage = storage;
        this.status = status;
    }

    /**
     * Runs the words that follow {@code learn}.
     *
     * @param sender the sender
     * @param arguments words after {@code learn}
     * @return the lines to send
     */
    public List<Component> execute(CommandSender sender, String[] arguments) {
        var statusOnly = arguments.length == 0 || arguments[0].equalsIgnoreCase("status");
        if (!sender.hasPermission(PERMISSION) && !(statusOnly && sender.hasPermission("thepredict.admin"))) {
            return List.of(AdminText.error("You need the thepredict.learn permission."));
        }
        if (statusOnly) {
            return List.of(AdminText.line("Learn", status.get()));
        }
        var action = arguments[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            return List.of(AdminText.error(USAGE));
        }
        if (StorageQueue.writerMissing(storage)) {
            return List.of(StorageQueue.needsWriter("Learn", action));
        }
        AdminCommand command;
        try {
            command = command(action, arguments);
        } catch (RuntimeException exception) {
            command = null;
        }
        if (command == null) {
            return List.of(AdminText.error(USAGE));
        }
        return StorageQueue.submit(sender, storage, command, "Learn", action);
    }

    private static AdminCommand command(String action, String[] arguments) {
        return switch (action) {
            case "build" -> arguments.length == 1 ? new AdminCommand.LearnBuild("")
                    : arguments.length == 2 ? new AdminCommand.LearnBuild(arguments[1]) : null;
            case "export" -> arguments.length == 3 ? new AdminCommand.LearnExport(arguments[1], arguments[2]) : null;
            case "import" -> arguments.length == 2 ? new AdminCommand.LearnImport(arguments[1]) : null;
            case "rollback" -> arguments.length == 1 ? new AdminCommand.LearnRollback() : null;
            case "disable" -> arguments.length == 2 ? new AdminCommand.LearnDisable(arguments[1]) : null;
            default -> null;
        };
    }
}
