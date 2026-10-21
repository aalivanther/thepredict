package akyrion.thepredict.admin;

import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.StorageControl;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * {@code /thepredict memory} (M27.9). It talks only to {@link StorageControl}. The reply is {@code queued} before the
 * storage thread finishes. A name that is not online and not cached must be a UUID.
 */
public final class MemoryCommand {
    /** Permission of every memory command except {@code stats}. */
    public static final String PERMISSION = "thepredict.memory";

    /** Words completed after {@code memory}. */
    public static final List<String> ACTIONS = List.of("stats", "show", "reset", "purge", "export", "import");

    private final StorageControl storage;
    private final Function<String, Optional<UUID>> names;

    /**
     * Creates the command.
     *
     * @param storage the store
     * @param names online player or a cached offline player
     */
    public MemoryCommand(StorageControl storage, Function<String, Optional<UUID>> names) {
        this.storage = storage;
        this.names = names;
    }

    /**
     * Runs the words that follow {@code memory}.
     *
     * @param sender the sender
     * @param arguments words after {@code memory}
     * @return the lines already sent
     */
    public List<Component> execute(CommandSender sender, String[] arguments) {
        if (arguments.length == 0) {
            return List.of(usage());
        }
        var action = arguments[0].toLowerCase(Locale.ROOT);
        if (action.equals("show")) {
            if (!sender.hasPermission(PERMISSION) && !sender.hasPermission("thepredict.admin")) {
                return List.of(AdminText.error("You need the thepredict.memory permission."));
            }
        } else if (!action.equals("stats") && !sender.hasPermission(PERMISSION)) {
            return List.of(AdminText.error("You need the thepredict.memory permission."));
        }
        AdminCommand command;
        try {
            command = command(action, arguments);
        } catch (RuntimeException exception) {
            return List.of(usage());
        }
        if (command == null) {
            return List.of(usage());
        }
        return StorageQueue.submit(sender, storage, command, "Memory", action);
    }

    /**
     * Completions of the word at the end.
     *
     * @param arguments words after {@code memory}
     * @return the options
     */
    public List<String> complete(String[] arguments) {
        if (arguments.length <= 1) {
            return ACTIONS;
        }
        if (arguments[0].equalsIgnoreCase("import") && arguments.length == 3) {
            return List.of("replace", "newer");
        }
        return List.of();
    }

    private AdminCommand command(String action, String[] arguments) {
        return switch (action) {
            case "stats" -> arguments.length == 1 ? new AdminCommand.MemoryStats() : null;
            case "show" -> arguments.length == 2 ? new AdminCommand.MemoryShow(subject(arguments[1])) : null;
            case "reset" -> arguments.length == 2 ? new AdminCommand.MemoryReset(subject(arguments[1])) : null;
            case "purge" -> arguments.length == 2 ? new AdminCommand.MemoryPurge(Integer.parseInt(arguments[1])) : null;
            case "export" -> exportCommand(arguments);
            case "import" -> importCommand(arguments);
            default -> null;
        };
    }

    private AdminCommand exportCommand(String[] arguments) {
        if (arguments.length == 1) {
            return new AdminCommand.MemoryExport();
        }
        if (arguments.length != 2) {
            return null;
        }
        return new AdminCommand.MemoryExport(selector(arguments[1]));
    }

    private String selector(String word) {
        if (word.equalsIgnoreCase("all")) {
            return "all";
        }
        return subject(word);
    }

    private AdminCommand importCommand(String[] arguments) {
        if (arguments.length != 3) {
            return null;
        }
        var replace = arguments[2].equalsIgnoreCase("replace");
        if (!replace && !arguments[2].equalsIgnoreCase("newer")) {
            return null;
        }
        return new AdminCommand.MemoryImport(arguments[1], replace);
    }

    private String subject(String word) {
        try {
            return UUID.fromString(word).toString();
        } catch (IllegalArgumentException exception) {
            return names.apply(word).orElseThrow(() -> new IllegalArgumentException("subject")).toString();
        }
    }

    private static Component usage() {
        return AdminText.error("Usage: /thepredict memory [stats|show <player>|reset <player>|purge <days>"
                + "|export [player|uuid|all]|import <file> newer|import <file> replace]");
    }
}
