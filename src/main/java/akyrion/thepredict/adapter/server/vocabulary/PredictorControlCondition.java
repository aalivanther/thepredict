package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.config.MythicLineConfig;

import java.util.Objects;

/**
 * {@code predictorcontrol}: true while ThePredict controls the mob, that is while its view is at level
 * {@code JEV}, {@code ALTERNATIVE} or {@code LOCAL} and has not expired. Authors negate it to silence
 * classic skills: {@code Conditions: [ - predictorcontrol false ]}. Takes no arguments. Safe on any
 * thread; see {@link IntentCondition} for the thread and fail-safe guarantees.
 */
public final class PredictorControlCondition extends IntentCondition {
    /** Name of the condition in MythicMobs lines, compared without case (rule I8). */
    public static final String NAME = "predictorcontrol";

    /**
     * Creates the condition from its MythicMobs line; arguments are ignored.
     *
     * @param config line configuration, unused because the condition has no arguments
     * @param board intent board read on evaluation
     * @param clock clock whose {@code nanoTime} dates the evaluation
     */
    public PredictorControlCondition(MythicLineConfig config, IntentBoard board, ServerClock clock) {
        this(board, clock);
        Objects.requireNonNull(config, "config");
    }

    /**
     * Creates the condition.
     *
     * @param board intent board read on evaluation
     * @param clock clock whose {@code nanoTime} dates the evaluation
     */
    public PredictorControlCondition(IntentBoard board, ServerClock clock) {
        super(board, clock, "");
    }

    /**
     * Pure rule of the condition, tested without MythicMobs. Allocation-free.
     *
     * @param view current view of the mob
     * @param nowNanos current monotonic time
     * @return {@code true} if the view controls the mob
     */
    public static boolean matches(IntentView view, long nowNanos) {
        return view.controls(nowNanos);
    }

    @Override
    boolean accepts(IntentView view, long nowNanos) {
        return matches(view, nowNanos);
    }
}
