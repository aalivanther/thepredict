package akyrion.thepredict.admin;

import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.DuelRecording;
import akyrion.thepredict.engine.StorageControl;
import akyrion.thepredict.engine.learn.LearnStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@code /thepredict} (M12.1 to M12.3, M18.4, M19.8): {@code status}, {@code check}, {@code inspect <nearest|uuid>},
 * {@code intention suggest [profile]} and, with {@link #DEBUG_PERMISSION}, {@code debug intent|release|goals} and
 * {@code duel start|status|stop}. Bukkit runs it on the server thread (rule T1). The whole command needs
 * {@code thepredict.admin}; {@code debug} also needs {@link #DEBUG_PERMISSION}, which the server console always has
 * (DT-17), and without it the debug and duel subcommands are neither listed nor run. {@code intention suggest} only
 * prints lines and never writes a MythicMobs file (rules D6 and C24). Every output is bounded and names no player.
 */
public final class ThePredictCommand implements CommandExecutor, TabCompleter {
    /** Permission required by {@code /thepredict debug}; {@code default: false} in plugin.yml. */
    public static final String DEBUG_PERMISSION = "thepredict.debug";

    /** Radius of {@code nearest}, in blocks. */
    public static final double NEAREST_RADIUS_BLOCKS = 16.0;

    private static final List<String> SUBCOMMANDS = List.of("status", "check", "inspect", "intention", "records",
            "eval", "zone", "memory", "learn", "distill", "autoconfig");

    /** Details of {@code inspect <nearest|uuid> [detail]}, in any case (CAR-22). */
    private static final List<String> INSPECT_DETAILS = List.of("tick", "stance", "telegraph", "group", "patterns");

    private static final String INSPECT_USAGE = "Usage: /thepredict inspect <nearest|uuid> ["
            + String.join("|", INSPECT_DETAILS) + "]";

    private final Supplier<SetupReport> setup;
    private final CommandServices services;
    private final Supplier<Collection<TacticalProfile>> profiles;
    private final StatusFormatter status = new StatusFormatter();
    private final InspectFormatter inspect = new InspectFormatter();
    private final IntentionFormatter intentions = new IntentionFormatter();
    private final DebugCommand debug;
    private final DuelCommand duel;
    private final RecordsCommand records;
    private final EvalCommand eval;
    private final MemoryCommand memory;
    private final LearnCommand learn;
    private final DistillCommand distill;
    private final AutoconfigCommand autoconfig;
    private Supplier<String> learnStatus = () -> LearnStatus.line(akyrion.thepredict.domain.LearningSettings.DEFAULT);
    private Supplier<String> distillStatus = () -> akyrion.thepredict.engine.distill.DistillStatus.line(
            akyrion.thepredict.engine.distill.DistilledArtifacts.State.NONE,
            akyrion.thepredict.domain.distill.DistillMode.OFF, akyrion.thepredict.domain.distill.RegimeKey.ALL.text());
    private java.util.function.Supplier<java.util.List<String>> memoryStatus = java.util.List::of;

    /**
     * Creates the command.
     *
     * @param setup installation facts, read at each {@code status}
     * @param services the engine, adapted by the composition root
     * @param duels the duel recorder of {@code /thepredict duel} (M18.4)
     * @param worlds UUID of a loaded world by name, for the console form of {@code duel start}
     */
    public ThePredictCommand(Supplier<SetupReport> setup, CommandServices services, DuelRecording duels,
                             Function<String, Optional<UUID>> worlds) {
        this(setup, services, duels, worlds, List::of);
    }

    /**
     * Creates the command with the bound profiles of {@code intention suggest}.
     *
     * @param setup installation facts, read at each {@code status}
     * @param services the engine, adapted by the composition root
     * @param duels the duel recorder of {@code /thepredict duel} (M18.4)
     * @param worlds UUID of a loaded world by name, for the console form of {@code duel start}
     * @param profiles bound profiles, read when {@code intention suggest} runs; never a MythicMobs file
     */
    public ThePredictCommand(Supplier<SetupReport> setup, CommandServices services, DuelRecording duels,
                             Function<String, Optional<UUID>> worlds,
                             Supplier<Collection<TacticalProfile>> profiles) {
        this(setup, services, duels, worlds, profiles, StorageControl.NONE);
    }

    /**
     * Creates the command with the records control of M21.
     *
     * @param setup installation facts, read at each {@code status}
     * @param services the engine, adapted by the composition root
     * @param duels the duel recorder of {@code /thepredict duel} (M18.4)
     * @param worlds UUID of a loaded world by name, for the console form of {@code duel start}
     * @param profiles bound profiles, read when {@code intention suggest} runs
     * @param storage records and aggregates; {@link StorageControl#NONE} when storage was not opened
     */
    public ThePredictCommand(Supplier<SetupReport> setup, CommandServices services, DuelRecording duels,
                             Function<String, Optional<UUID>> worlds, Supplier<Collection<TacticalProfile>> profiles,
                             StorageControl storage) {
        this.setup = Objects.requireNonNull(setup, "setup");
        this.services = Objects.requireNonNull(services, "services");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.debug = new DebugCommand(services, inspect, this::resolve);
        this.duel = new DuelCommand(duels, worlds);
        this.records = new RecordsCommand(Objects.requireNonNull(storage, "storage"));
        this.memory = new MemoryCommand(storage, this::player);
        this.learn = new LearnCommand(storage, () -> learnStatus.get());
        this.distill = new DistillCommand(storage, () -> distillStatus.get());
        this.autoconfig = new AutoconfigCommand(storage);
        this.eval = new EvalCommand(storage, services::difficultyView, services::forcePressure,
                () -> DifficultyFormatter.bench(DifficultyGlobals.DEFAULT),
                (sender, word) -> sender instanceof Player player ? nearest(player) : Optional.empty());
    }

    /**
     * Supplies the {@code difficulty} section in use, for {@code eval status} without a nearby session (CAR-21).
     *
     * @param globals the loaded section
     */
    public void useDefaultDifficulty(DifficultyGlobals globals) {
        Objects.requireNonNull(globals, "globals");
        eval.useStatus(() -> DifficultyFormatter.bench(globals));
    }

    /**
     * Supplies the lines of {@code eval verdict list}: the verdicts the server loaded (CAR-09).
     *
     * @param lines at most {@link EvalCommand#MAX_VERDICT_LINES} lines, read when the command runs
     */
    public void useVerdicts(Supplier<List<String>> lines) {
        eval.useVerdicts(lines);
    }

    /**
     * Runs {@code /thepredict}.
     *
     * @param sender player with {@code thepredict.admin} or the console
     * @param command registered command
     * @param label alias used
     * @param arguments subcommand and its arguments
     * @return always {@code true}; wrong usage answers with the usage line
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        var action = arguments.length == 0 ? "status" : arguments[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "status" -> status(sender);
            case "check" -> check(sender);
            case "inspect" -> inspect(sender, arguments);
            case "intention" -> intention(sender, Arrays.copyOfRange(arguments, 1, arguments.length));
            case "records" -> records.execute(sender, Arrays.copyOfRange(arguments, 1, arguments.length))
                    .forEach(sender::sendMessage);
            case "eval" -> eval.execute(sender, Arrays.copyOfRange(arguments, 1, arguments.length))
                    .forEach(sender::sendMessage);
            case "zone" -> sender.sendMessage(AdminText.info(ZoneDump.LINE));
            case "memory" -> memory.execute(sender, Arrays.copyOfRange(arguments, 1, arguments.length))
                    .forEach(sender::sendMessage);
            case "learn" -> learn.execute(sender, Arrays.copyOfRange(arguments, 1, arguments.length))
                    .forEach(sender::sendMessage);
            case "distill" -> distill.execute(sender, Arrays.copyOfRange(arguments, 1, arguments.length))
                    .forEach(sender::sendMessage);
            case "autoconfig" -> autoconfig.execute(sender, Arrays.copyOfRange(arguments, 1, arguments.length))
                    .forEach(sender::sendMessage);
            case "debug" -> debugIfAllowed(sender, Arrays.copyOfRange(arguments, 1, arguments.length));
            case "duel" -> duelIfAllowed(sender, Arrays.copyOfRange(arguments, 1, arguments.length));
            default -> sender.sendMessage(AdminText.error("Usage: /thepredict [" + String.join("|", visible(sender))
                    + "]"));
        }
        return true;
    }

    /**
     * Completes subcommands, {@code nearest} and the debug actions; nothing debug without the permission.
     *
     * @param sender who is typing
     * @param command registered command
     * @param label alias used
     * @param arguments arguments typed so far
     * @return matching completions
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] arguments) {
        var options = switch (arguments.length) {
            case 1 -> visible(sender);
            case 2 -> second(sender, arguments[0]);
            case 3 -> third(sender, arguments);
            default -> List.<String>of();
        };
        var typed = arguments.length == 0 ? "" : arguments[arguments.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(typed)).toList();
    }

    /** Tells whether a sender may use {@code debug}: the console always, a player with the permission. */
    static boolean mayDebug(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender.hasPermission(DEBUG_PERMISSION);
    }

    private List<String> visible(CommandSender sender) {
        return mayDebug(sender)
                ? List.of("status", "check", "inspect", "intention", "records", "eval", "zone", "memory", "learn",
                "distill", "autoconfig", "debug", "duel")
                : SUBCOMMANDS;
    }

    private List<String> second(CommandSender sender, String action) {
        if (action.equalsIgnoreCase("inspect")) {
            return List.of("nearest");
        }
        if (action.equalsIgnoreCase("intention")) {
            return List.of("suggest");
        }
        if (action.equalsIgnoreCase("records")) {
            return records.complete(new String[]{""});
        }
        if (action.equalsIgnoreCase("eval")) {
            return EvalCommand.ACTIONS;
        }
        if (action.equalsIgnoreCase("memory")) {
            return memory.complete(new String[]{""});
        }
        if (action.equalsIgnoreCase("learn")) {
            return LearnCommand.ACTIONS;
        }
        if (action.equalsIgnoreCase("distill")) {
            return DistillCommand.ACTIONS;
        }
        if (action.equalsIgnoreCase("autoconfig")) {
            return AutoconfigCommand.ACTIONS;
        }
        if (action.equalsIgnoreCase("debug") && mayDebug(sender)) {
            return DebugCommandParser.ACTIONS;
        }
        if (action.equalsIgnoreCase("duel") && mayDebug(sender)) {
            return DuelCommand.ACTIONS;
        }
        return List.of();
    }

    private List<String> third(CommandSender sender, String[] arguments) {
        if (arguments[0].equalsIgnoreCase("debug") && mayDebug(sender)) {
            return List.of("nearest");
        }
        if (arguments[0].equalsIgnoreCase("inspect")) {
            return INSPECT_DETAILS;
        }
        if (arguments[0].equalsIgnoreCase("intention") && arguments[1].equalsIgnoreCase("suggest")) {
            return intentions.profileIds(profiles.get());
        }
        if (arguments[0].equalsIgnoreCase("records")) {
            return records.complete(Arrays.copyOfRange(arguments, 1, arguments.length));
        }
        if (arguments[0].equalsIgnoreCase("memory")) {
            return memory.complete(Arrays.copyOfRange(arguments, 1, arguments.length));
        }
        return List.of();
    }

    private void intention(CommandSender sender, String[] arguments) {
        intentions.format(List.of(arguments), profiles.get()).forEach(sender::sendMessage);
    }

    private void status(CommandSender sender) {
        var report = sender instanceof Player player ? services.status(player.getWorld().getUID()) : services.status();
        var lines = new java.util.ArrayList<>(report.map(found -> status.format(setup.get(), found))
                .orElseGet(() -> status.format(setup.get())));
        for (var line : services.fairnessLines()) {
            lines.add(AdminText.problem("Fairness", line));
        }
        for (var line : memoryStatus.get()) {
            lines.add(AdminText.line("Memory", line));
        }
        StatusFormatter.bounded(lines).forEach(sender::sendMessage);
    }

    private void check(CommandSender sender) {
        var started = services.requestCheck(result -> sender.sendMessage(AdminText.info(
                SetupMessages.checkResult(result))));
        sender.sendMessage(switch (started) {
            case STARTED -> AdminText.info("Jev check started; the result arrives here without blocking the server. "
                    + SetupMessages.CHECK_SCOPE);
            case BUSY -> AdminText.error("Other checks are already waiting; see /thepredict status.");
            case UNAVAILABLE -> AdminText.error("The coordinator did not start; nothing to check. See the console.");
        });
    }

    private void inspect(CommandSender sender, String[] arguments) {
        var detail = arguments.length == 3 ? arguments[2].toLowerCase(Locale.ROOT) : "";
        if (arguments.length != 2 && !(arguments.length == 3 && INSPECT_DETAILS.contains(detail))) {
            sender.sendMessage(AdminText.error(INSPECT_USAGE));
            return;
        }
        var selector = MobSelector.parse(arguments[1]);
        if (selector.isEmpty()) {
            sender.sendMessage(AdminText.error("Mob must be 'nearest' or an entity UUID, was '"
                    + AdminText.echo(arguments[1]) + "'"));
            return;
        }
        resolve(sender, selector.get()).ifPresent(entityId -> inspectMob(sender, entityId, detail));
    }

    private void inspectMob(CommandSender sender, UUID entityId, String detail) {
        switch (detail) {
            case "" -> services.session(entityId).ifPresentOrElse(
                    report -> inspect.format(report).forEach(sender::sendMessage),
                    () -> sender.sendMessage(noSession(entityId)));
            case "tick" -> services.tickSignals(entityId).ifPresentOrElse(
                    signals -> InspectFormatter.tick(signals).forEach(sender::sendMessage),
                    () -> sender.sendMessage(noSession(entityId)));
            default -> {
                var extra = detailLines(entityId, detail);
                if (extra.isEmpty()) {
                    sender.sendMessage(noSession(entityId));
                } else {
                    extra.forEach(line -> sender.sendMessage(AdminText.line(detail, line)));
                }
            }
        }
    }

    private List<String> detailLines(UUID entityId, String detail) {
        return switch (detail) {
            case "stance" -> services.inspectStance(entityId);
            case "group" -> services.inspectGroup(entityId);
            case "patterns" -> services.inspectPatterns(entityId);
            default -> services.inspectTelegraph(entityId);
        };
    }

    private static Component noSession(UUID entityId) {
        return AdminText.info(AdminText.shortId(entityId)
                + " has no session: it is not a bound mob with Predictor: true and an active profile.");
    }

    private void debugIfAllowed(CommandSender sender, String[] arguments) {
        if (!mayDebug(sender)) {
            sender.sendMessage(AdminText.error("You need the " + DEBUG_PERMISSION + " permission to use debug tools"));
            return;
        }
        debug.run(sender, Arrays.asList(arguments));
    }

    private void duelIfAllowed(CommandSender sender, String[] arguments) {
        if (!mayDebug(sender)) {
            sender.sendMessage(AdminText.error("You need the " + DEBUG_PERMISSION + " permission to record duels"));
            return;
        }
        duel.run(sender, Arrays.asList(arguments));
    }

    /**
     * Supplies the line of {@code learn status} from the loaded settings of the running server.
     *
     * @param line the line, read when the command runs
     */
    public void useLearnStatus(Supplier<String> line) {
        this.learnStatus = Objects.requireNonNull(line, "line");
    }

    /**
     * Supplies the line of {@code distill status} from the live distill control of the running server.
     *
     * @param line the line, read when the command runs
     */
    public void useDistillStatus(Supplier<String> line) {
        this.distillStatus = Objects.requireNonNull(line, "line");
    }

    /**
     * Supplies the three memory lines of {@code status}.
     *
     * @param lines the lines, read when status runs
     */
    public void useMemoryStatus(java.util.function.Supplier<java.util.List<String>> lines) {
        this.memoryStatus = Objects.requireNonNull(lines, "lines");
    }

    /**
     * An online player, or a cached offline player. An unknown name is empty: the caller must already have a UUID.
     *
     * @param word the name
     * @return the id
     */
    private Optional<UUID> player(String word) {
        var online = org.bukkit.Bukkit.getPlayerExact(word);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }
        var cached = org.bukkit.Bukkit.getOfflinePlayerIfCached(word);
        if (cached == null) {
            return Optional.empty();
        }
        return Optional.of(cached.getUniqueId());
    }

    /** Finds the mob of a selector, or tells the sender why it cannot. */
    private Optional<UUID> resolve(CommandSender sender, MobSelector selector) {
        if (selector instanceof MobSelector.ById byId) {
            return Optional.of(byId.entityId());
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(AdminText.error("'nearest' needs a player; use an entity UUID from the console"));
            return Optional.empty();
        }
        var found = nearest(player);
        if (found.isEmpty()) {
            sender.sendMessage(AdminText.error("No bound mob with a session within "
                    + (int) NEAREST_RADIUS_BLOCKS + " blocks"));
        }
        return found;
    }

    private Optional<UUID> nearest(Player player) {
        var origin = player.getLocation();
        var best = Optional.<UUID>empty();
        var bestDistance = NEAREST_RADIUS_BLOCKS * NEAREST_RADIUS_BLOCKS;
        for (var entity : player.getNearbyEntities(NEAREST_RADIUS_BLOCKS, NEAREST_RADIUS_BLOCKS, NEAREST_RADIUS_BLOCKS)) {
            var distance = entity.getLocation().distanceSquared(origin);
            if (distance <= bestDistance && services.bound(entity.getUniqueId())) {
                best = Optional.of(entity.getUniqueId());
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Sends lines built elsewhere, for {@link DebugCommand}. */
    static void send(CommandSender sender, List<Component> lines) {
        lines.forEach(sender::sendMessage);
    }
}
