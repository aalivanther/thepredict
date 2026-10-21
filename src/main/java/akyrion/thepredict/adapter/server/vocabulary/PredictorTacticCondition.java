package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.config.MythicLineConfig;

import java.util.Locale;
import java.util.Objects;

/**
 * {@code predictortactic{t=<tactic>}} (alias {@code tactic=}): true while ThePredict controls the mob
 * with that tactic, for example {@code predictortactic{t=retreat_and_defend}}. The argument must be a
 * valid {@link TacticId} written exactly, in lower case (rule I4), other than the reserved
 * {@link TacticId#NONE}, which only views that never control carry; anything else makes the condition
 * always false (rule A6) and is reported as invalid. Reserved {@code debug_} tactics stay valid because
 * debug intents control. Safe on any thread; see {@link IntentCondition} for the thread and fail-safe
 * guarantees.
 */
public final class PredictorTacticCondition extends IntentCondition {
    /** Name of the condition in MythicMobs lines, compared without case (rule I8). */
    public static final String NAME = "predictortactic";

    private static final String[] KEYS = {"t", "tactic"};

    private final TacticId tactic;

    /**
     * Creates the condition from its MythicMobs line.
     *
     * @param config line configuration with {@code t} or {@code tactic}
     * @param board intent board read on evaluation
     * @param clock clock whose {@code nanoTime} dates the evaluation
     */
    public PredictorTacticCondition(MythicLineConfig config, IntentBoard board, ServerClock clock) {
        this(argument(config), board, clock);
    }

    /**
     * Creates the condition from the text of its argument.
     *
     * @param tacticArgument value of {@code t} or {@code tactic}; empty when missing
     * @param board intent board read on evaluation
     * @param clock clock whose {@code nanoTime} dates the evaluation
     */
    public PredictorTacticCondition(String tacticArgument, IntentBoard board, ServerClock clock) {
        super(board, clock, problemOf(tacticArgument));
        this.tactic = isTactic(tacticArgument) ? new TacticId(tacticArgument) : TacticId.NONE;
    }

    /**
     * Reads the tactic argument of a MythicMobs line.
     *
     * @param config line configuration
     * @return value of {@code t} or {@code tactic}, or empty text when missing
     */
    public static String argument(MythicLineConfig config) {
        var value = Objects.requireNonNull(config, "config").getString(KEYS, "");
        return value == null ? "" : value;
    }

    /**
     * Returns the tactic the condition asks for; {@link TacticId#NONE} when the argument is invalid.
     *
     * @return configured tactic
     */
    public TacticId tactic() {
        return tactic;
    }

    /**
     * Pure rule of the condition, tested without MythicMobs. Allocation-free.
     *
     * @param view current view of the mob
     * @param tactic tactic the condition asks for
     * @param nowNanos current monotonic time
     * @return {@code true} if the view controls with that tactic
     */
    public static boolean matches(IntentView view, TacticId tactic, long nowNanos) {
        return view.tactic().equals(tactic) && view.controls(nowNanos);
    }

    @Override
    boolean accepts(IntentView view, long nowNanos) {
        return matches(view, tactic, nowNanos);
    }

    /** Tells whether the text is a valid tactic ID without throwing. */
    private static boolean isTactic(String text) {
        try {
            new TacticId(text);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** Describes an invalid tactic argument, or returns empty text when it is a valid tactic ID. */
    private static String problemOf(String tacticArgument) {
        Objects.requireNonNull(tacticArgument, "tacticArgument");
        if (TacticId.NONE.value().equals(tacticArgument)) {
            return "tactic 'none' is reserved for views without a tactic, which never control a mob";
        }
        if (isTactic(tacticArgument)) {
            return "";
        }
        var problem = "tactic " + ArgumentText.describe(tacticArgument)
                + " is not a tactic ID ([a-z][a-z0-9_]{2,39})";
        var lower = tacticArgument.trim().toLowerCase(Locale.ROOT);
        return isTactic(lower) ? problem + "; tactic IDs are lower case, for example '" + lower + "'" : problem;
    }
}
