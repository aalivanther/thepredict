package akyrion.thepredict.admin;

import akyrion.thepredict.domain.distill.OverlayPath;
import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.StorageControl;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * {@code /thepredict autoconfig} (M29.8), all of it behind {@link #PERMISSION} (CAR-05). Import, show, apply and
 * revert are queued. A path outside {@code plugins/ThePredict/autoconfig/} or containing {@code ..} is refused.
 * Nothing is applied by itself and a learn import is never queued.
 */
public final class AutoconfigCommand {
    /** Permission of {@code /thepredict autoconfig}. */
    public static final String PERMISSION = "thepredict.autoconfig";

    /** Words completed after {@code autoconfig}. */
    public static final List<String> ACTIONS = List.of("status", "import", "show", "apply", "revert");

    private static final String USAGE = "Usage: /thepredict autoconfig [status|import|show|apply|revert]";

    private final StorageControl storage;

    /**
     * Creates the command.
     *
     * @param storage the store
     */
    public AutoconfigCommand(StorageControl storage) {
        this.storage = storage;
    }

    /**
     * Runs the words that follow {@code autoconfig}.
     *
     * @param sender the sender
     * @param arguments words after {@code autoconfig}
     * @return the lines to send
     */
    public List<Component> execute(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of(AdminText.error("You need the thepredict.autoconfig permission."));
        }
        if (arguments.length == 0 || arguments[0].equalsIgnoreCase("status")) {
            return List.of(AdminText.line("Autoconfig", "autoApply=off"));
        }
        var action = arguments[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action) || arguments.length < 2) {
            return List.of(AdminText.error(USAGE));
        }
        if (!OverlayPath.allowed(arguments[1])) {
            return List.of(AdminText.error("Autoconfig writes only under plugins/ThePredict/autoconfig/."));
        }
        if (StorageQueue.writerMissing(storage)) {
            return List.of(StorageQueue.needsWriter("Autoconfig", action));
        }
        return StorageQueue.submit(sender, storage, command(action, arguments[1]), "Autoconfig", action);
    }

    private static AdminCommand command(String action, String path) {
        return switch (action) {
            case "import" -> new AdminCommand.AutoconfigImport(path);
            case "show" -> new AdminCommand.AutoconfigShow(path);
            case "apply" -> new AdminCommand.AutoconfigApply(path);
            case "revert" -> new AdminCommand.AutoconfigRevert(path);
            default -> throw new IllegalArgumentException(action);
        };
    }
}
