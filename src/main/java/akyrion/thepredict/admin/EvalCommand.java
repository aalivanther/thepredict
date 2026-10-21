package akyrion.thepredict.admin;

import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.StorageControl;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@code /thepredict eval} (M22.11). Report and import answer {@code queued}, and their result reaches the sender
 * when the writer answers (CAR-09); report is not generated on the server in this release (JS-13). {@code verdict
 * list} shows the verdicts the server loaded, at most {@link #MAX_VERDICT_LINES} lines. A path that contains
 * {@code ..} is rejected. Pressure needs {@code thepredict.debug} and is not stored.
 */
public final class EvalCommand {
    /** Permission of the eval commands. */
    public static final String PERMISSION = "thepredict.eval";

    /** Words after {@code eval}. */
    public static final List<String> ACTIONS = List.of("status", "difficulty", "report", "verdict", "pressure");

    /** Most lines of {@code verdict list}. */
    public static final int MAX_VERDICT_LINES = 8;

    private final StorageControl storage;
    private final Function<UUID, Optional<String>> difficulty;
    private final BiFunction<UUID, Integer, Boolean> pressure;
    private Supplier<String> status;
    private Supplier<List<String>> verdicts = List::of;
    private final BiFunction<CommandSender, String, Optional<UUID>> locate;

    /**
     * Creates the command without a live difficulty block. Status prints the bench block of the delivered default
     * preset.
     *
     * @param storage aggregates and verdict import
     * @param difficulty one-line description of a session
     * @param pressure sets a debug rung, {@code -1} clears it; returns whether the session exists
     */
    public EvalCommand(StorageControl storage, Function<UUID, Optional<String>> difficulty,
                       BiFunction<UUID, Integer, Boolean> pressure) {
        this(storage, difficulty, pressure, () -> DifficultyFormatter.bench(DifficultyGlobals.DEFAULT),
                (sender, word) -> Optional.empty());
    }

    /**
     * Creates the command.
     *
     * @param storage aggregates and verdict import
     * @param difficulty description of a session
     * @param pressure sets a debug rung, {@code -1} clears it; returns whether the session exists
     * @param status two-line block when no session is nearest
     * @param locate resolves {@code nearest} for a player
     */
    public EvalCommand(StorageControl storage, Function<UUID, Optional<String>> difficulty,
                       BiFunction<UUID, Integer, Boolean> pressure, Supplier<String> status,
                       BiFunction<CommandSender, String, Optional<UUID>> locate) {
        this.storage = storage;
        this.difficulty = difficulty;
        this.pressure = pressure;
        this.status = status;
        this.locate = locate;
    }

    /**
     * Supplies the block {@code status} prints without a nearby session.
     *
     * @param block the two lines, read when the command runs
     */
    public void useStatus(Supplier<String> block) {
        this.status = Objects.requireNonNull(block, "block");
    }

    /**
     * Supplies the lines of {@code verdict list}.
     *
     * @param lines at most {@link #MAX_VERDICT_LINES} lines; empty when no verdict loaded
     */
    public void useVerdicts(Supplier<List<String>> lines) {
        this.verdicts = Objects.requireNonNull(lines, "lines");
    }

    /**
     * Runs the words after {@code eval}.
     *
     * @param sender the sender
     * @param arguments words after {@code eval}
     * @return the lines already sent
     */
    public List<Component> execute(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of(AdminText.error("You need the thepredict.eval permission."));
        }
        if (arguments.length == 0) {
            return List.of(usage());
        }
        var action = arguments[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "status" -> List.of(AdminText.line("Eval", statusOf(sender)));
            case "difficulty" -> difficulty(sender, arguments);
            case "report" -> report(sender, arguments);
            case "verdict" -> verdict(sender, arguments);
            case "pressure" -> pressure(sender, arguments);
            default -> List.of(usage());
        };
    }

    private String statusOf(CommandSender sender) {
        if (sender instanceof Player) {
            var id = locate.apply(sender, "nearest");
            if (id.isPresent()) {
                var text = difficulty.apply(id.get());
                if (text.isPresent()) {
                    return text.get();
                }
            }
        }
        return status.get();
    }

    private List<Component> difficulty(CommandSender sender, String[] arguments) {
        if (arguments.length != 2) {
            return List.of(usage());
        }
        var id = identify(sender, arguments[1]);
        if (id.message() != null) {
            return List.of(id.message());
        }
        if (id.value().isEmpty()) {
            return List.of(usage());
        }
        return List.of(AdminText.line("Eval", difficulty.apply(id.value().get()).orElse("no session")));
    }

    private List<Component> report(CommandSender sender, String[] arguments) {
        var tag = arguments.length > 1 ? arguments[1] : "";
        var since = arguments.length > 2 ? arguments[2] : "";
        var format = arguments.length > 3 ? arguments[3] : "md";
        if (tag.contains("..") || since.contains("..")) {
            return List.of(AdminText.error("path"));
        }
        if (StorageQueue.writerMissing(storage)) {
            return List.of(StorageQueue.needsWriter("Eval", "report"));
        }
        return StorageQueue.submit(sender, storage, new AdminCommand.EvalReport(tag, since, format), "Eval", "report");
    }

    private List<Component> verdict(CommandSender sender, String[] arguments) {
        if (arguments.length == 2 && arguments[1].equalsIgnoreCase("list")) {
            var lines = verdicts.get();
            if (lines.isEmpty()) {
                return List.of(AdminText.line("Eval", "no verdict loaded"));
            }
            return lines.stream().limit(MAX_VERDICT_LINES).map(line -> AdminText.line("Verdict", line)).toList();
        }
        if (arguments.length != 3 || !arguments[1].equalsIgnoreCase("import")) {
            return List.of(usage());
        }
        AdminCommand command;
        try {
            command = new AdminCommand.VerdictImport(arguments[2]);
        } catch (IllegalArgumentException exception) {
            return List.of(AdminText.error("path"));
        }
        if (StorageQueue.writerMissing(storage)) {
            return List.of(StorageQueue.needsWriter("Eval", "verdict import"));
        }
        return StorageQueue.submit(sender, storage, command, "Eval", "verdict import");
    }

    private List<Component> pressure(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission(ThePredictCommand.DEBUG_PERMISSION)) {
            return List.of(AdminText.error("You need the thepredict.debug permission."));
        }
        if (arguments.length != 3) {
            return List.of(usage());
        }
        var id = identify(sender, arguments[1]);
        if (id.message() != null) {
            return List.of(id.message());
        }
        if (id.value().isEmpty()) {
            return List.of(usage());
        }
        int rung;
        if (arguments[2].equals("auto")) {
            rung = -1;
        } else {
            try {
                rung = Integer.parseInt(arguments[2]);
            } catch (NumberFormatException exception) {
                return List.of(usage());
            }
            if (rung < 0 || rung > 8) {
                return List.of(usage());
            }
        }
        var applied = pressure.apply(id.value().get(), rung);
        return List.of(AdminText.line("Eval", applied ? "pressure" : "no session"));
    }

    private Found identify(CommandSender sender, String text) {
        if (text.equals("nearest")) {
            if (!(sender instanceof Player)) {
                return new Found(Optional.empty(), AdminText.error(
                        "'nearest' needs a player; use an entity UUID from the console"));
            }
            var found = locate.apply(sender, text);
            if (found.isEmpty()) {
                return new Found(Optional.empty(), AdminText.error("No bound mob with a session within "
                        + (int) ThePredictCommand.NEAREST_RADIUS_BLOCKS + " blocks"));
            }
            return new Found(found, null);
        }
        try {
            return new Found(Optional.of(UUID.fromString(text)), null);
        } catch (IllegalArgumentException exception) {
            return new Found(Optional.empty(), null);
        }
    }

    private record Found(Optional<UUID> value, Component message) {
    }

    private static Component usage() {
        return AdminText.error("Usage: /thepredict eval status|difficulty <nearest|uuid>|report (not available on the"
                + " server in this release)|verdict list|verdict import <file>|pressure <nearest|uuid> <0-8|auto>");
    }
}
