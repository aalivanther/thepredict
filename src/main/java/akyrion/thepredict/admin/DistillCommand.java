package akyrion.thepredict.admin;

import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.StorageControl;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * {@code /thepredict distill} (M29.6), all of it behind {@link #PERMISSION} (CAR-05). Status and inspect are read from
 * memory. Mode, disable and the file cycle are queued on the storage writer. {@code build} reads the table; the
 * {@code .tpld} is built off the server by {@code distillTrain} (JS-12). This command does not open SQLite, does not
 * train and does not start a thread. A path with {@code ..} is refused. Disable is not a learn disable.
 */
public final class DistillCommand {
    /** Permission of {@code /thepredict distill}. */
    public static final String PERMISSION = "thepredict.distill";

    /** Words completed after {@code distill}. */
    public static final List<String> ACTIONS = List.of("status", "build", "export", "import", "rollback", "disable",
            "inspect", "mode");

    private static final String USAGE = "Usage: /thepredict distill [status|build [profile] (reads the table;"
            + " distillTrain builds the .tpld)|export <file>|import <file>|rollback|disable all|inspect"
            + "|mode <off|shadow|assist|autonomous>]";

    private final StorageControl storage;
    private final Supplier<String> status;

    /**
     * Creates the command.
     *
     * @param storage the store
     * @param status one status line
     */
    public DistillCommand(StorageControl storage, Supplier<String> status) {
        this.storage = storage;
        this.status = status;
    }

    /**
     * Runs the words that follow {@code distill}.
     *
     * @param sender the sender
     * @param arguments words after {@code distill}
     * @return the lines to send
     */
    public List<Component> execute(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of(AdminText.error("You need the thepredict.distill permission."));
        }
        if (arguments.length == 0 || arguments[0].equalsIgnoreCase("status")
                || arguments[0].equalsIgnoreCase("inspect")) {
            return List.of(AdminText.line("Distill", status.get()));
        }
        for (var argument : arguments) {
            if (argument.contains("..")) {
                return List.of(AdminText.error("Distill rejects '..'."));
            }
        }
        var action = arguments[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            return List.of(AdminText.error(USAGE));
        }
        if (StorageQueue.writerMissing(storage)) {
            return List.of(StorageQueue.needsWriter("Distill", action));
        }
        AdminCommand command;
        try {
            command = command(action, arguments);
        } catch (IllegalArgumentException exception) {
            return List.of(AdminText.error(USAGE));
        }
        if (command == null) {
            return List.of(AdminText.error(USAGE));
        }
        return StorageQueue.submit(sender, storage, command, "Distill", action);
    }

    private static AdminCommand command(String action, String[] arguments) {
        return switch (action) {
            case "build" -> arguments.length == 1 ? new AdminCommand.DistillBuild("")
                    : arguments.length == 2 ? new AdminCommand.DistillBuild(arguments[1]) : null;
            case "export" -> arguments.length == 2 ? new AdminCommand.DistillExport(arguments[1]) : null;
            case "import" -> arguments.length == 2 ? new AdminCommand.DistillImport(arguments[1]) : null;
            case "rollback" -> arguments.length == 1 ? new AdminCommand.DistillRollback() : null;
            case "disable" -> arguments.length == 2 && "all".equalsIgnoreCase(arguments[1])
                    ? new AdminCommand.DistillDisable("all") : null;
            case "mode" -> arguments.length == 2 && mode(arguments[1]) != null
                    ? new AdminCommand.DistillModeSet(mode(arguments[1]).name().toLowerCase(Locale.ROOT)) : null;
            default -> null;
        };
    }

    private static DistillMode mode(String token) {
        try {
            return DistillMode.parse(token.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
