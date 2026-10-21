package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.CombatStance;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.config.MythicLineConfig;

import java.util.Locale;
import java.util.Objects;

/**
 * {@code predictorstance{s=windup,guarded}} (alias {@code stance}): true when the published stance is one of the
 * names, or when {@code enraged} is listed and the mob is enraged (M23.8). An unknown name makes it always false.
 */
public final class PredictorStanceCondition extends IntentCondition {
    /** Name of the condition in MythicMobs lines. */
    public static final String NAME = "predictorstance";

    private static final String[] KEYS = {"s", "stance"};

    private final int mask;
    private final boolean enraged;
    private final boolean validNames;

    /**
     * Creates the condition from its MythicMobs line.
     *
     * @param config line configuration
     * @param board intent board
     * @param clock clock whose {@code nanoTime} dates the evaluation
     */
    public PredictorStanceCondition(MythicLineConfig config, IntentBoard board, ServerClock clock) {
        this(argument(config), board, clock);
    }

    /**
     * Creates the condition from the text of its argument.
     *
     * @param text comma-separated names, any case
     * @param board intent board
     * @param clock clock
     */
    public PredictorStanceCondition(String text, IntentBoard board, ServerClock clock) {
        super(board, clock, problemOf(text));
        var parsed = parse(text);
        this.mask = parsed[0];
        this.enraged = parsed[1] == 1;
        this.validNames = parsed[2] == 1;
    }

    /**
     * Reads {@code s} or {@code stance}.
     *
     * @param config line configuration
     * @return the argument, or empty text
     */
    public static String argument(MythicLineConfig config) {
        var value = Objects.requireNonNull(config, "config").getString(KEYS, "");
        return value == null ? "" : value;
    }

    /**
     * Pure rule. A missing session is false.
     *
     * @param view current view
     * @param mask bits of {@link CombatStance#ordinal()}
     * @param enraged whether {@code enraged} was requested
     * @param valid whether every name was known
     * @return whether the view matches
     */
    public static boolean matches(IntentView view, int mask, boolean enraged, boolean valid) {
        if (!valid || view.generation() < 0) {
            return false;
        }
        if (enraged && view.enraged()) {
            return true;
        }
        return (mask & (1 << view.stance().ordinal())) != 0;
    }

    @Override
    boolean accepts(IntentView view, long nowNanos) {
        return matches(view, mask, enraged, validNames);
    }

    private static String problemOf(String text) {
        if (text == null || text.isBlank()) {
            return "missing stance";
        }
        return parse(text)[2] == 1 ? "" : "unknown stance";
    }

    /** Returns mask, enraged flag, valid flag. */
    private static int[] parse(String text) {
        var mask = 0;
        var enraged = 0;
        var valid = 1;
        if (text == null || text.isBlank()) {
            return new int[] {0, 0, 0};
        }
        for (var part : text.split(",")) {
            var name = part.trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            if (name.equals("enraged")) {
                enraged = 1;
                continue;
            }
            var found = false;
            for (var stance : CombatStance.values()) {
                if (stance.name().toLowerCase(Locale.ROOT).equals(name)) {
                    mask |= 1 << stance.ordinal();
                    found = true;
                }
            }
            if (!found) {
                valid = 0;
            }
        }
        if (mask == 0 && enraged == 0) {
            valid = 0;
        }
        return new int[] {mask, enraged, valid};
    }
}
