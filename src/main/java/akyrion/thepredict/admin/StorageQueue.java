package akyrion.thepredict.admin;

import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.StorageControl;
import akyrion.thepredict.engine.StorageStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The answers every command queued on the storage writer shares (M21.9, CAR-22). The result reaches the sender
 * through the watcher; a refused command says why: the writer is missing (storage was not opened, stays off, or
 * another process holds {@code records/.lock}), the watchers are full ({@code busy}), or a command of the same kind is
 * still running.
 */
final class StorageQueue {
    /** Causes of a service that runs without a writer: the lock is held by another process, or could not be taken. */
    private static final Set<String> WITHOUT_WRITER = Set.of("LOCK_HELD", "LOCK_FAILED");

    private StorageQueue() {
    }

    /**
     * Whether no writer can take a command.
     *
     * @param storage the store
     * @return {@code true} without storage, with storage off, or without the records lock
     */
    static boolean writerMissing(StorageControl storage) {
        var status = storage.status();
        return status.equals(StorageStatus.ABSENT) || WITHOUT_WRITER.contains(status.cause());
    }

    /**
     * The refusal of a command that needs the writer.
     *
     * @param noun the command family, such as {@code Learn}
     * @param action the action word
     * @return the error line
     */
    static Component needsWriter(String noun, String action) {
        return AdminText.error(noun + " " + action + " needs the storage writer.");
    }

    /**
     * Queues a command whose result is sent to the sender when it arrives.
     *
     * @param sender who asked
     * @param storage the store
     * @param command the command
     * @param noun the command family, such as {@code Records}
     * @param action the action word, for the refusal without a writer
     * @return {@code queued}, or why the command was refused
     */
    static List<Component> submit(CommandSender sender, StorageControl storage, AdminCommand command, String noun,
                                  String action) {
        var accepted = storage.submit(command, result -> sender.sendMessage(result.ok()
                ? AdminText.line(noun, result.detail())
                : AdminText.error(noun + " " + result.detail())));
        if (accepted) {
            return List.of(AdminText.line(noun, "queued"));
        }
        if (writerMissing(storage)) {
            return List.of(needsWriter(noun, action));
        }
        if (storage.atCapacity()) {
            return List.of(AdminText.error("busy"));
        }
        var family = noun.toLowerCase(Locale.ROOT);
        var article = "aeiou".indexOf(family.charAt(0)) >= 0 ? "An " : "A ";
        return List.of(AdminText.error(article + family + " command of that kind is already running."));
    }
}
