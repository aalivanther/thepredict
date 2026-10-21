package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.config.MythicLineConfig;

import java.util.Locale;
import java.util.Objects;

/**
 * {@code predictortoken{pool=melee}} (key {@code pool}). {@code melee} is bit 0 and {@code ranged} is bit 1. True
 * while the view controls the mob and holds the token; false in {@code BASE} or after the deadline (A6). Another
 * pool is always false. Syntax is pending a server check (G8).
 */
public final class PredictorTokenCondition extends IntentCondition {
    /** Name of the condition. */
    public static final String NAME = "predictortoken";

    private final int bit;

    /**
     * Creates the condition from its MythicMobs line.
     *
     * @param config line configuration
     * @param board intent board
     * @param clock clock whose {@code nanoTime} dates the evaluation
     */
    public PredictorTokenCondition(MythicLineConfig config, IntentBoard board, ServerClock clock) {
        this(argument(config), board, clock);
    }

    /**
     * Creates the condition from the pool token.
     *
     * @param token {@code melee} or {@code ranged}
     * @param board intent board
     * @param clock clock
     */
    public PredictorTokenCondition(String token, IntentBoard board, ServerClock clock) {
        super(board, clock, problem(token));
        this.bit = bit(token);
    }

    /**
     * Reads {@code pool}.
     *
     * @param config line configuration
     * @return the token
     */
    public static String argument(MythicLineConfig config) {
        var value = Objects.requireNonNull(config, "config").getString("pool", "");
        return value == null ? "" : value;
    }

    @Override
    boolean accepts(IntentView view, long nowNanos) {
        return view.controls(nowNanos) && bit >= 0 && (view.tokenMask() & (1 << bit)) != 0;
    }

    private static String problem(String token) {
        return bit(token) >= 0 ? "" : "pool must be melee or ranged";
    }

    private static int bit(String token) {
        if (token == null) {
            return -1;
        }
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "melee" -> 0;
            case "ranged" -> 1;
            default -> -1;
        };
    }
}
