package akyrion.thepredict.admin;

import akyrion.thepredict.engine.DebugIntents;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Runs the parsed {@code /thepredict debug} requests (M12.3) once {@link ThePredictCommand} checked the permission.
 * Package-private; server thread only. A debug intent never sends a request to Jev.
 */
final class DebugCommand {
    private final CommandServices services;
    private final InspectFormatter formatter;
    private final BiFunction<CommandSender, MobSelector, Optional<UUID>> resolver;
    private final DebugCommandParser parser = new DebugCommandParser();

    DebugCommand(CommandServices services, InspectFormatter formatter,
                 BiFunction<CommandSender, MobSelector, Optional<UUID>> resolver) {
        this.services = Objects.requireNonNull(services, "services");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** Parses and runs the arguments after {@code debug}, or shows the usage. */
    void run(CommandSender sender, List<String> arguments) {
        if (arguments.isEmpty()) {
            DebugCommandParser.USAGE.forEach(line -> sender.sendMessage(AdminText.info(line)));
            return;
        }
        switch (parser.parse(arguments)) {
            case DebugParseResult.Accepted accepted -> execute(sender, accepted.request());
            case DebugParseResult.Rejected rejected -> sender.sendMessage(AdminText.error(rejected.reason()
                    + ". Run /thepredict debug for usage"));
        }
    }

    private void execute(CommandSender sender, DebugRequest request) {
        switch (request) {
            case DebugRequest.SetIntent intent -> resolver.apply(sender, intent.mob())
                    .ifPresent(entityId -> setIntent(sender, entityId, intent));
            case DebugRequest.Release release -> resolver.apply(sender, release.mob())
                    .ifPresent(entityId -> release(sender, entityId));
            case DebugRequest.ListGoals goals -> resolver.apply(sender, goals.mob())
                    .ifPresent(entityId -> ThePredictCommand.send(sender, formatter.goals(entityId,
                            services.session(entityId).map(report -> report.key().generation()).orElse(-1),
                            services.goals(entityId), services.progress(entityId))));
        }
    }

    private void setIntent(CommandSender sender, UUID entityId, DebugRequest.SetIntent request) {
        var ticks = request.durationTicks();
        var outcome = ticks.isPresent() ? services.setIntent(entityId, request.tactic(), ticks.getAsInt())
                : services.setIntent(entityId, request.tactic());
        sender.sendMessage(switch (outcome) {
            case DebugIntents.Outcome.Applied applied -> AdminText.info("Debug intent " + applied.intent().tactic()
                    .value() + " (" + applied.intent().movement() + ", LOCAL) on " + AdminText.shortId(entityId)
                    + " generation " + applied.intent().mob().generation() + " for " + applied.durationTicks()
                    + " ticks; it never sends a request to Jev");
            case DebugIntents.Outcome.Rejected rejected -> AdminText.error("Debug intent refused for "
                    + AdminText.shortId(entityId) + ": " + rejected.reason());
        });
    }

    private void release(CommandSender sender, UUID entityId) {
        sender.sendMessage(services.release(entityId)
                ? AdminText.info("Released " + AdminText.shortId(entityId)
                        + " to level 4; the fallback chain decides again within 2 ticks")
                : AdminText.error(AdminText.shortId(entityId) + " has no active session"));
    }
}
