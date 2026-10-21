package akyrion.thepredict.admin;

import akyrion.thepredict.engine.DuelRecording;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * {@code /thepredict duel start|status|stop} (M18.4), once {@link ThePredictCommand} checked
 * {@code thepredict.debug}. Package-private; server thread only. A player may give only the label and the two
 * factions, and the arena is centred on the player in the player's world; the console must give the world and the
 * centre. The optional radius (24 blocks by default) and {@code skills=} follow in any order.
 */
final class DuelCommand {
    /** Actions of {@code /thepredict duel}. */
    static final List<String> ACTIONS = List.of("start", "status", "stop");
    /** Usage lines. */
    static final List<String> USAGE = List.of(
            "/thepredict duel start <label> <factionA> <factionB> [radius] [skills=A,B] (arena centred on you)",
            "/thepredict duel start <label> <factionA> <factionB> <world> <x> <y> <z> [radius] [skills=A,B]",
            "/thepredict duel status",
            "/thepredict duel stop");
    /** Refusal of the console form without a world. */
    static final String CONSOLE_NEEDS_WORLD = "The console must give <world> <x> <y> <z> after the two factions";

    private final DuelRecording recording;
    private final Function<String, Optional<UUID>> worlds;

    DuelCommand(DuelRecording recording, Function<String, Optional<UUID>> worlds) {
        this.recording = Objects.requireNonNull(recording, "recording");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    /** Runs the arguments after {@code duel}, or shows the usage. */
    void run(CommandSender sender, List<String> arguments) {
        var action = arguments.isEmpty() ? "" : arguments.getFirst().toLowerCase(Locale.ROOT);
        switch (action) {
            case "start" -> start(sender, arguments.subList(1, arguments.size()));
            case "status" -> sender.sendMessage(AdminText.info(describe(recording.status())));
            case "stop" -> stop(sender);
            default -> USAGE.forEach(line -> sender.sendMessage(AdminText.info(line)));
        }
    }

    private void start(CommandSender sender, List<String> arguments) {
        var arena = sender instanceof Player player ? Optional.of(new Arena(player.getWorld().getUID(),
                player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ()))
                : Optional.<Arena>empty();
        var requester = sender instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.<UUID>empty();
        switch (parse(arguments, arena, requester, worlds)) {
            case Parsed.Refused refused -> sender.sendMessage(AdminText.error(refused.reason()
                    + ". Run /thepredict duel for usage"));
            case Parsed.Accepted accepted -> {
                var status = recording.start(accepted.request());
                var request = accepted.request();
                sender.sendMessage(status.recording()
                        ? AdminText.info("Duel recording '" + request.label() + "' started: red = " + request.sideA()
                                + ", blue = " + request.sideB() + ", radius " + AdminText.decimal(request.radiusBlocks())
                                + ", folder " + status.directory())
                        : AdminText.error("Duel recording refused: " + status.stoppedBy().orElse("unknown reason")));
            }
        }
    }

    private void stop(CommandSender sender) {
        if (!recording.status().recording()) {
            sender.sendMessage(AdminText.error("No duel recording is running"));
            return;
        }
        var status = recording.stop();
        sender.sendMessage(AdminText.info("Duel recording '" + status.label() + "' stopped after " + status.rounds()
                + " rounds, " + status.rowsDropped() + " rows dropped; summary.md is written in " + status.directory()));
    }

    /** The one line of {@code /thepredict duel status}. */
    static String describe(DuelRecording.DuelStatus status) {
        if (status.label().isEmpty()) {
            return "Duel recording: none since the start";
        }
        var state = status.recording() ? "recording" : "stopped (" + status.stoppedBy().orElse("-") + ")";
        return "Duel recording '" + status.label() + "': " + state + ", rounds " + status.rounds() + ", followed "
                + status.trackedMobs() + ", requests " + status.requests() + ", input tokens " + status.inputTokens()
                + ", rows dropped " + status.rowsDropped() + ", bytes " + status.bytesWritten() + ", folder "
                + status.directory();
    }

    /** The centre and world of a player who starts a recording. */
    record Arena(UUID worldId, double x, double y, double z) {
    }

    /** A parsed {@code start}. */
    sealed interface Parsed permits Parsed.Accepted, Parsed.Refused {
        /** The request to start. */
        record Accepted(DuelRecording.DuelRequest request) implements Parsed {
        }

        /** Why the arguments were refused. */
        record Refused(String reason) implements Parsed {
        }
    }

    /**
     * Parses the arguments after {@code start}.
     *
     * @param arguments label, factions, then the world and centre or nothing, then the radius and {@code skills=}
     * @param arena the world and centre of a player, empty for the console
     * @param requester the player, empty for the console
     * @param worlds UUID of a loaded world by name
     * @return the request or why it was refused
     */
    static Parsed parse(List<String> arguments, Optional<Arena> arena, Optional<UUID> requester,
                        Function<String, Optional<UUID>> worlds) {
        if (arguments.size() < 3) {
            return new Parsed.Refused("Give a label and the two factions");
        }
        var label = arguments.get(0);
        if (!DuelRecording.DuelRequest.LABEL.matcher(label).matches()) {
            return new Parsed.Refused("Label must match [a-z0-9-]{1,40}, was '" + AdminText.echo(label) + "'");
        }
        var rest = arguments.subList(3, arguments.size());
        var explicit = !rest.isEmpty() && !option(rest.getFirst());
        if (explicit && rest.size() < 4) {
            return new Parsed.Refused("Give <world> <x> <y> <z> after the two factions");
        }
        var world = explicit ? worlds.apply(rest.getFirst()).map(id -> new Arena(id, number(rest.get(1)),
                number(rest.get(2)), number(rest.get(3)))) : arena;
        if (world.isEmpty()) {
            return new Parsed.Refused(explicit ? "World '" + AdminText.echo(rest.getFirst()) + "' is not loaded"
                    : CONSOLE_NEEDS_WORLD);
        }
        return options(arguments, world.get(), explicit ? rest.subList(4, rest.size()) : rest, requester);
    }

    private static Parsed options(List<String> arguments, Arena arena, List<String> options,
                                  Optional<UUID> requester) {
        var radius = OptionalDouble.empty();
        Set<String> skills = Set.of();
        for (var option : options) {
            var lower = option.toLowerCase(Locale.ROOT);
            if (lower.startsWith("skills=")) {
                skills = skills(option.substring("skills=".length()));
            } else {
                var value = number(lower.startsWith("radius=") ? option.substring("radius=".length()) : option);
                if (Double.isNaN(value)) {
                    return new Parsed.Refused("Unknown argument '" + AdminText.echo(option) + "'");
                }
                radius = OptionalDouble.of(value);
            }
        }
        var chosen = radius.orElse(DuelRecording.DuelRequest.DEFAULT_RADIUS_BLOCKS);
        if (Double.isNaN(arena.x()) || Double.isNaN(arena.y()) || Double.isNaN(arena.z())) {
            return new Parsed.Refused("Coordinates must be numbers");
        }
        if (!(chosen > 0.0) || chosen > DuelRecording.DuelRequest.MAX_RADIUS_BLOCKS) {
            return new Parsed.Refused("Radius must be a number in (0, 64]");
        }
        if (arguments.get(1).equalsIgnoreCase(arguments.get(2))) {
            return new Parsed.Refused("The two factions must be different");
        }
        return new Parsed.Accepted(new DuelRecording.DuelRequest(arguments.get(0), arguments.get(1), arguments.get(2),
                arena.worldId(), arena.x(), arena.y(), arena.z(), chosen, skills, requester));
    }

    private static boolean option(String argument) {
        var lower = argument.toLowerCase(Locale.ROOT);
        return lower.startsWith("skills=") || lower.startsWith("radius=") || !Double.isNaN(number(argument));
    }

    private static Set<String> skills(String list) {
        var skills = new LinkedHashSet<String>();
        for (var skill : list.split(",")) {
            if (!skill.isBlank()) {
                skills.add(skill.strip());
            }
        }
        return Set.copyOf(skills);
    }

    private static double number(String text) {
        try {
            var value = Double.parseDouble(text);
            return Double.isFinite(value) ? value : Double.NaN;
        } catch (NumberFormatException exception) {
            return Double.NaN;
        }
    }
}
