package akyrion.thepredict.admin;

import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.DebugIntents;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * Parses the arguments that follow {@code /thepredict debug} into a {@link DebugRequest} (M12.3). Pure and stateless;
 * safe on any thread. Never throws for user input: it returns a rejection.
 */
public final class DebugCommandParser {
    /** The three debug actions, in the order of the usage lines. */
    public static final List<String> ACTIONS = List.of("intent", "release", "goals");

    /** Usage lines shown when the arguments are missing or invalid. */
    public static final List<String> USAGE = List.of(
            "/thepredict debug intent <nearest|uuid> <tactic> [ticks]",
            "/thepredict debug release <nearest|uuid>",
            "/thepredict debug goals <nearest|uuid>");

    /** Creates a parser; it holds no state. */
    public DebugCommandParser() {
    }

    /**
     * Parses the arguments after {@code debug}.
     *
     * @param arguments arguments without the leading {@code debug}
     * @return the accepted request or a rejection with a reason
     */
    public DebugParseResult parse(List<String> arguments) {
        if (arguments.isEmpty()) {
            return reject("Missing debug action");
        }
        return switch (arguments.getFirst().toLowerCase(Locale.ROOT)) {
            case "intent" -> parseIntent(arguments);
            case "release" -> parseMobOnly(arguments, DebugRequest.Release::new);
            case "goals" -> parseMobOnly(arguments, DebugRequest.ListGoals::new);
            default -> reject("Unknown debug action '" + AdminText.echo(arguments.getFirst()) + "'; use "
                    + String.join(", ", ACTIONS));
        };
    }

    /** Parses {@code intent <mob> <tactic> [ticks]}. */
    private DebugParseResult parseIntent(List<String> arguments) {
        if (arguments.size() < 3 || arguments.size() > 4) {
            return reject("Usage: " + USAGE.getFirst());
        }
        var selector = MobSelector.parse(arguments.get(1));
        if (selector.isEmpty()) {
            return rejectSelector(arguments.get(1));
        }
        var tactic = tactic(arguments.get(2));
        if (tactic.isEmpty()) {
            return reject("Tactic must be a snake_case tactic ID of the mob's profile, was '"
                    + AdminText.echo(arguments.get(2)) + "'");
        }
        var ticks = arguments.size() == 4 ? ticks(arguments.get(3)) : OptionalInt.empty();
        if (arguments.size() == 4 && ticks.isEmpty()) {
            return reject("Ticks must be a whole number between 1 and " + DebugIntents.MAX_TICKS);
        }
        return accept(new DebugRequest.SetIntent(selector.get(), tactic.get(), ticks));
    }

    /** Parses actions whose only argument is the mob. */
    private DebugParseResult parseMobOnly(List<String> arguments, Function<MobSelector, DebugRequest> request) {
        if (arguments.size() != 2) {
            return reject("Usage: /thepredict debug " + arguments.getFirst().toLowerCase(Locale.ROOT)
                    + " <nearest|uuid>");
        }
        return MobSelector.parse(arguments.get(1)).map(selector -> accept(request.apply(selector)))
                .orElseGet(() -> rejectSelector(arguments.get(1)));
    }

    private static Optional<TacticId> tactic(String text) {
        try {
            var tactic = new TacticId(text);
            return tactic.isReserved() ? Optional.empty() : Optional.of(tactic);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static OptionalInt ticks(String text) {
        try {
            var ticks = Integer.parseInt(text);
            return ticks >= 1 && ticks <= DebugIntents.MAX_TICKS ? OptionalInt.of(ticks) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    /** Builds the rejection for an unreadable mob selector. */
    static DebugParseResult rejectSelector(String text) {
        return reject("Mob must be 'nearest' or an entity UUID, was '" + AdminText.echo(text) + "'");
    }

    private static DebugParseResult accept(DebugRequest request) {
        return new DebugParseResult.Accepted(request);
    }

    private static DebugParseResult reject(String reason) {
        return new DebugParseResult.Rejected(reason);
    }
}
