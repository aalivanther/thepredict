package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.GroupRole;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.config.MythicLineConfig;

import java.util.Locale;
import java.util.Objects;

/**
 * {@code predictorrole{role=pressure}} (key {@code role} or {@code r}). True while the view controls the mob and the
 * published role matches; false in {@code BASE} or after the deadline, like every other condition (A6). An unknown
 * role is always false. Allocation-free. Syntax is pending a server check (G8).
 */
public final class PredictorRoleCondition extends IntentCondition {
    /** Name of the condition. */
    public static final String NAME = "predictorrole";

    private final GroupRole role;

    /**
     * Creates the condition from its MythicMobs line.
     *
     * @param config line configuration
     * @param board intent board
     * @param clock clock whose {@code nanoTime} dates the evaluation
     */
    public PredictorRoleCondition(MythicLineConfig config, IntentBoard board, ServerClock clock) {
        this(argument(config), board, clock);
    }

    /**
     * Creates the condition from the role token.
     *
     * @param token role name
     * @param board intent board
     * @param clock clock
     */
    public PredictorRoleCondition(String token, IntentBoard board, ServerClock clock) {
        super(board, clock, problem(token));
        this.role = parse(token).orElse(GroupRole.RESERVE);
    }

    /**
     * Reads {@code role} or {@code r}.
     *
     * @param config line configuration
     * @return the token
     */
    public static String argument(MythicLineConfig config) {
        var value = Objects.requireNonNull(config, "config").getString(new String[] {"role", "r"}, "");
        return value == null ? "" : value;
    }

    @Override
    boolean accepts(IntentView view, long nowNanos) {
        var current = view.role();
        return view.controls(nowNanos) && current.isPresent() && current.get() == role;
    }

    private static String problem(String token) {
        return parse(token).isPresent() ? "" : "role is not a group role";
    }

    private static java.util.Optional<GroupRole> parse(String token) {
        if (token == null || token.isBlank()) {
            return java.util.Optional.empty();
        }
        var normalized = token.toLowerCase(Locale.ROOT);
        for (var role : GroupRole.values()) {
            if (role.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return java.util.Optional.of(role);
            }
        }
        return java.util.Optional.empty();
    }
}
