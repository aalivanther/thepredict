package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.config.MythicLineConfig;

import java.util.Objects;

/**
 * {@code predictormove{m=<movement>}} (alias {@code movement=}): true while ThePredict controls the mob
 * and its view asks for that movement. The movement is read without case, for example
 * {@code predictormove{m=keep_distance}}. The generated goal set uses it in {@code mobconditions}.
 * An unknown or missing movement makes the condition always false (rule A6). Safe on any thread; see
 * {@link IntentCondition} for the thread and fail-safe guarantees.
 */
public final class PredictorMoveCondition extends IntentCondition {
    /** Name of the condition in MythicMobs lines, compared without case (rule I8). */
    public static final String NAME = "predictormove";

    private static final String[] KEYS = {"m", "movement"};

    private final Movement movement;

    /**
     * Creates the condition from its MythicMobs line.
     *
     * @param config line configuration with {@code m} or {@code movement}
     * @param board intent board read on evaluation
     * @param clock clock whose {@code nanoTime} dates the evaluation
     */
    public PredictorMoveCondition(MythicLineConfig config, IntentBoard board, ServerClock clock) {
        this(argument(config), board, clock);
    }

    /**
     * Creates the condition from the text of its argument.
     *
     * @param movementArgument value of {@code m} or {@code movement}, any case; empty when missing
     * @param board intent board read on evaluation
     * @param clock clock whose {@code nanoTime} dates the evaluation
     */
    public PredictorMoveCondition(String movementArgument, IntentBoard board, ServerClock clock) {
        super(board, clock, problemOf(movementArgument));
        this.movement = Movement.parse(movementArgument).orElse(Movement.NONE);
    }

    /**
     * Reads the movement argument of a MythicMobs line.
     *
     * @param config line configuration
     * @return value of {@code m} or {@code movement}, or empty text when missing
     */
    public static String argument(MythicLineConfig config) {
        var value = Objects.requireNonNull(config, "config").getString(KEYS, "");
        return value == null ? "" : value;
    }

    /**
     * Returns the movement the condition asks for; {@link Movement#NONE} when the argument is invalid.
     *
     * @return configured movement
     */
    public Movement movement() {
        return movement;
    }

    /**
     * Pure rule of the condition, tested without MythicMobs. Allocation-free.
     *
     * @param view current view of the mob
     * @param movement movement the condition asks for
     * @param nowNanos current monotonic time
     * @return {@code true} if the view controls and requests that movement
     */
    public static boolean matches(IntentView view, Movement movement, long nowNanos) {
        return view.movement() == movement && view.controls(nowNanos);
    }

    @Override
    boolean accepts(IntentView view, long nowNanos) {
        return matches(view, movement, nowNanos);
    }

    /** Describes an invalid movement argument, or returns empty text when it names a movement. */
    private static String problemOf(String movementArgument) {
        Objects.requireNonNull(movementArgument, "movementArgument");
        if (Movement.parse(movementArgument).isPresent()) {
            return "";
        }
        return "movement " + ArgumentText.describe(movementArgument)
                + " is not one of APPROACH, KEEP_DISTANCE, RETREAT, HOLD, FLANK or NONE";
    }
}
