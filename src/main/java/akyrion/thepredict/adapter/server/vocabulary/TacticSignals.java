package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.TacticId;

import java.util.Locale;
import java.util.Objects;

/**
 * Names of the MythicMobs signals that telegraph the start of a tactic (rule I9, M04.6).
 *
 * <p>Format: {@code PREDICTOR_} followed by the tactic ID in upper case, for example
 * {@code PREDICTOR_RETREAT_AND_DEFEND}; authors react with {@code ~onSignal:PREDICTOR_RETREAT_AND_DEFEND}.
 * The name is at most 50 characters. Semantics, implemented by M10: one signal when an intent starts
 * with a new tactic at level {@code JEV}, {@code ALTERNATIVE} or {@code LOCAL}; never when the same
 * tactic is renewed, and never when control passes to {@code BASE}. Pure and stateless.
 */
public final class TacticSignals {
    /** Prefix of every tactic signal. */
    public static final String PREFIX = "PREDICTOR_";

    private TacticSignals() {
    }

    /**
     * Builds the signal name of a tactic.
     *
     * @param tactic tactic that starts
     * @return {@code PREDICTOR_} plus the tactic ID in upper case with {@link Locale#ROOT}
     * @throws NullPointerException if {@code tactic} is {@code null}
     */
    public static String nameOf(TacticId tactic) {
        return PREFIX + Objects.requireNonNull(tactic, "tactic").value().toUpperCase(Locale.ROOT);
    }
}
