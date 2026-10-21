package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.config.MythicLineConfig;

import java.util.Objects;

/**
 * {@code predictorplan{plan=surround_target}} (key {@code plan} or {@code p}). True while the view controls the mob and
 * the published plan matches; false in {@code BASE} or after the deadline (A6). An unknown plan is always false.
 * Allocation-free. Syntax is pending a server check (G8).
 */
public final class PredictorPlanCondition extends IntentCondition {
    /** Name of the condition. */
    public static final String NAME = "predictorplan";

    private final GroupPlan plan;

    /**
     * Creates the condition from its MythicMobs line.
     *
     * @param config line configuration
     * @param board intent board
     * @param clock clock whose {@code nanoTime} dates the evaluation
     */
    public PredictorPlanCondition(MythicLineConfig config, IntentBoard board, ServerClock clock) {
        this(argument(config), board, clock);
    }

    /**
     * Creates the condition from the plan token.
     *
     * @param token plan name
     * @param board intent board
     * @param clock clock
     */
    public PredictorPlanCondition(String token, IntentBoard board, ServerClock clock) {
        super(board, clock, problem(token));
        this.plan = GroupPlan.parse(token == null ? "" : token).orElse(GroupPlan.PRESS_MAIN_TARGET);
    }

    /**
     * Reads {@code plan} or {@code p}.
     *
     * @param config line configuration
     * @return the token
     */
    public static String argument(MythicLineConfig config) {
        var value = Objects.requireNonNull(config, "config").getString(new String[] {"plan", "p"}, "");
        return value == null ? "" : value;
    }

    @Override
    boolean accepts(IntentView view, long nowNanos) {
        var current = view.plan();
        return view.controls(nowNanos) && current.isPresent() && current.get() == plan;
    }

    private static String problem(String token) {
        return token != null && GroupPlan.parse(token).isPresent() ? "" : "plan is not a group plan";
    }
}
