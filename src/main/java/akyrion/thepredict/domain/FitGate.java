package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure acceptance rule of M16.5 for an answer that carries fit checks: the relative {@code choice} settles which option,
 * and the absolute {@code noul} of each option can veto it or rescue a low confidence (G9). Stateless, reads no clock,
 * safe on any thread.
 *
 * <ol>
 *     <li>Walks the tactics by descending probability and drops those with {@code fit < veto}; when the veto drops every
 *     tactic the answer is rejected with {@code LOW_CONFIDENCE} (no option is backed, A6).</li>
 *     <li>If the first survivor is the most probable tactic and {@code confidence >= minimumConfidence}, it is adopted
 *     at level {@code JEV}.</li>
 *     <li>If {@code confidence < minimumConfidence}, the first survivor is adopted (rescued) only when its fit reaches
 *     {@code rescue} and no other survivor does; at level {@code JEV} when it is the most probable, {@code ALTERNATIVE}
 *     otherwise. Else {@code LOW_CONFIDENCE}.</li>
 *     <li>If {@code confidence >= minimumConfidence} but the veto dropped the most probable tactic, the first survivor
 *     is adopted at level {@code ALTERNATIVE}.</li>
 * </ol>
 *
 * <p>A tactic without a fit value is never vetoed and never rescues. A tactic the caller excludes (one that is not
 * applicable) never survives, whatever its fit and the veto, so a veto of {@code 0} skips it and moves to the next
 * valid option.
 */
public final class FitGate {
    private FitGate() {
    }

    /**
     * Thresholds of the rule, from {@code decision.fitVeto} and {@code decision.fitRescue}.
     *
     * @param veto fits below it veto an option
     * @param rescue fit an option needs to rescue a low confidence
     */
    public record FitThresholds(double veto, double rescue) {
        /** Defaults of {@code decision.fitVeto} (0.2) and {@code decision.fitRescue} (0.7): the only place of them (C13). */
        public static final FitThresholds DEFAULT = new FitThresholds(0.2, 0.7);

        /**
         * Validates the thresholds.
         *
         * @throws IllegalArgumentException unless {@code 0 <= veto < rescue <= 1}
         */
        public FitThresholds {
            if (!(veto >= 0.0 && veto < rescue && rescue <= 1.0)) {
                throw new IllegalArgumentException("fit thresholds need 0 <= veto < rescue <= 1, were " + veto + " and "
                        + rescue);
            }
        }
    }

    /** What the rule decides. */
    public sealed interface FitVerdict permits FitVerdict.Adopt, FitVerdict.Reject {
        /**
         * Adopt a tactic.
         *
         * @param tactic adopted tactic
         * @param level {@code JEV} or {@code ALTERNATIVE}
         * @param rescued whether the fit rescued a confidence below the minimum
         */
        record Adopt(TacticId tactic, ControlLevel level, boolean rescued) implements FitVerdict {
            /**
             * Validates the verdict.
             *
             * @throws NullPointerException if a component is {@code null}
             * @throws IllegalArgumentException if the level is not {@code JEV} or {@code ALTERNATIVE}
             */
            public Adopt {
                Objects.requireNonNull(tactic, "tactic");
                Objects.requireNonNull(level, "level");
                if (level != ControlLevel.JEV && level != ControlLevel.ALTERNATIVE) {
                    throw new IllegalArgumentException("an adopted fit verdict is JEV or ALTERNATIVE, was " + level);
                }
            }
        }

        /**
         * Reject the answer.
         *
         * @param reason cause of the rejection
         */
        record Reject(FallbackReason reason) implements FitVerdict {
            /**
             * Validates the verdict.
             *
             * @throws NullPointerException if the reason is {@code null}
             */
            public Reject {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    /**
     * Judges an answer with its fits.
     *
     * @param answer answer to the {@code NOW} question
     * @param fits probability of {@code true} of the fit check of each option
     * @param minimumConfidence confidence required without a rescue
     * @param thresholds veto and rescue thresholds
     * @return the verdict
     */
    public static FitVerdict judge(Answer answer, Map<TacticId, Double> fits, double minimumConfidence,
                                   FitThresholds thresholds) {
        return judge(answer, fits, Set.of(), minimumConfidence, thresholds);
    }

    /**
     * Judges an answer with its fits, never adopting an excluded tactic.
     *
     * @param answer answer to the {@code NOW} question
     * @param fits probability of {@code true} of the fit check of each option
     * @param excluded tactics that are not applicable: dropped before the veto, like a vetoed option
     * @param minimumConfidence confidence required without a rescue
     * @param thresholds veto and rescue thresholds
     * @return the verdict
     */
    public static FitVerdict judge(Answer answer, Map<TacticId, Double> fits, Set<TacticId> excluded,
                                   double minimumConfidence, FitThresholds thresholds) {
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(fits, "fits");
        Objects.requireNonNull(excluded, "excluded");
        Objects.requireNonNull(thresholds, "thresholds");
        var ranked = answer.ranked();
        var survivors = new ArrayList<TacticId>(ranked.size());
        for (var tactic : ranked) {
            var fit = fits.get(tactic);
            if (!excluded.contains(tactic) && (fit == null || fit >= thresholds.veto())) {
                survivors.add(tactic);
            }
        }
        if (survivors.isEmpty()) {
            return new FitVerdict.Reject(FallbackReason.LOW_CONFIDENCE);
        }
        var first = survivors.getFirst();
        var level = first.equals(ranked.getFirst()) ? ControlLevel.JEV : ControlLevel.ALTERNATIVE;
        if (answer.confidence() >= minimumConfidence) {
            return new FitVerdict.Adopt(first, level, false);
        }
        var rescuers = survivors.stream().filter(tactic -> fits.containsKey(tactic)
                && fits.get(tactic) >= thresholds.rescue()).toList();
        if (rescuers.size() == 1 && rescuers.getFirst().equals(first)) {
            return new FitVerdict.Adopt(first, level, true);
        }
        return new FitVerdict.Reject(FallbackReason.LOW_CONFIDENCE);
    }
}
