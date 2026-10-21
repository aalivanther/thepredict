package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.CandidatePolicy;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Moves a distilled pick when the target's admitted pattern prefers another live candidate (M29.4). The candidate is
 * one that counters the pattern with the counter of {@code CounterMap} ({@link CandidatePolicy#counters}: the movement
 * of its micro, or a choice that fits its {@code CastPreference}), never just another option (AE-08).
 */
public final class DistillPatternShift {
    private static final EnemyPattern[] PATTERNS = EnemyPattern.values();

    private DistillPatternShift() {
    }

    /**
     * A shift of the distilled pick.
     *
     * @param tactic the adopted tactic
     * @param pattern the admitted pattern it counters, the one the motivated cast is marked with (A7)
     */
    public record Shift(String tactic, EnemyPattern pattern) {
        /**
         * Checks the components.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Shift {
            Objects.requireNonNull(tactic, "tactic");
            Objects.requireNonNull(pattern, "pattern");
        }
    }

    /**
     * The shift of a distilled pick, or empty when the plain pick stands: no pattern is admitted, the plain pick
     * already counters an admitted pattern, or no other live candidate counters one. Otherwise the admitted patterns
     * are read by ordinal (C16) and the first one that some other candidate counters moves the pick to the first such
     * candidate in declaration order.
     *
     * @param plain the tactic the policy chose without the pattern
     * @param candidates live candidates, declaration order
     * @param patterns admitted patterns of the target
     * @param profile profile that declares the tactics
     * @return the shift, or empty
     */
    public static Optional<Shift> prefer(String plain, List<String> candidates, Set<EnemyPattern> patterns,
                                         TacticalProfile profile) {
        if (patterns.isEmpty() || plain.isEmpty() || candidates.size() < 2) {
            return Optional.empty();
        }
        var held = profile.tactics().get(new TacticId(plain));
        for (var pattern : PATTERNS) {
            if (patterns.contains(pattern) && held != null && CandidatePolicy.counters(held, profile, pattern)) {
                return Optional.empty();
            }
        }
        for (var pattern : PATTERNS) {
            if (!patterns.contains(pattern)) {
                continue;
            }
            for (var candidate : candidates) {
                var tactic = candidate.equals(plain) ? null : profile.tactics().get(new TacticId(candidate));
                if (tactic != null && CandidatePolicy.counters(tactic, profile, pattern)) {
                    return Optional.of(new Shift(candidate, pattern));
                }
            }
        }
        return Optional.empty();
    }
}
