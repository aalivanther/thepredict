package akyrion.thepredict.admin;

import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.StorageControl;
import akyrion.thepredict.engine.StorageStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /thepredict records} (M21.9). It talks only to {@link StorageControl}. Stats answers at once. Every other
 * command answers {@code queued} and delivers the result through the watcher. A second command of the same kind is
 * rejected.
 */
public final class RecordsCommand {
    /** Permission of every records command except {@code stats}. */
    public static final String PERMISSION = "thepredict.records";

    /** Words completed after {@code records}. */
    public static final List<String> ACTIONS = List.of("stats", "export", "prune", "verify", "backup", "flush",
            "cache", "tag");

    private final StorageControl storage;

    /**
     * Creates the command.
     *
     * @param storage the store
     */
    public RecordsCommand(StorageControl storage) {
        this.storage = storage;
    }

    /**
     * Runs the words that follow {@code records}.
     *
     * @param sender the sender
     * @param arguments words after {@code records}
     * @return the lines already sent; the watcher may send one more later
     */
    public List<Component> execute(CommandSender sender, String[] arguments) {
        if (arguments.length == 0) {
            return List.of(usage());
        }
        var action = arguments[0].toLowerCase(Locale.ROOT);
        if (action.equals("stats")) {
            return List.of(AdminText.line("Records", text(storage.status())));
        }
        if (!sender.hasPermission(PERMISSION)) {
            return List.of(AdminText.error("You need the thepredict.records permission."));
        }
        AdminCommand command;
        try {
            command = AdminCommand.parse(Arrays.asList(arguments));
        } catch (RuntimeException exception) {
            return List.of(usage());
        }
        return StorageQueue.submit(sender, storage, command, "Records", action);
    }

    /**
     * Completions of the word at {@code index}.
     *
     * @param arguments words after {@code records}, including the partial one
     * @return the options
     */
    public List<String> complete(String[] arguments) {
        if (arguments.length <= 1) {
            return ACTIONS;
        }
        return switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "export" -> arguments.length == 2 ? List.of("outcomes", "events") : List.of();
            case "cache" -> List.of("clear");
            case "tag" -> List.of("clear");
            default -> List.of();
        };
    }

    private static Component usage() {
        return AdminText.error("Usage: /thepredict records [stats|export outcomes [profile]|export events <from> <to>"
                + " [types]|prune [days]|verify|backup|flush|cache clear|tag <tag>|tag clear]");
    }

    private static String text(StorageStatus status) {
        return status.state() + (status.cause().isEmpty() ? "" : " " + status.cause()) + " records "
                + status.recordsLevel() + " dropped " + status.droppedEvents() + " inFlight "
                + status.batchesInFlight() + " failed " + status.failedBatches() + " sqlite "
                + status.sqliteVersion();
    }
}
