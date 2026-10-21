package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure planner of control level 3: chooses a tactic without the model. Stateless, reads no clock, safe on any
 * thread.
 *
 * <p>The direct tactic of the governing context wins when it is eligible. Otherwise the eligible tactic with the
 * highest score wins, ties broken by declaration order. The score is the priority plus {@value #BEST_WHEN_BONUS} for
 * each {@code BestWhen} condition of the tactic's skill that holds now, only when the author wrote that purpose
 * ({@link SkillPurpose#authored()}, M15.8); an inferred purpose, or a tactic without a choice, scores its priority, as
 * in the 0.0.1.
 */
public final class LocalPlanner {
    /** Score a tactic gains for each condition of its declared {@code BestWhen} that holds now. */
    public static final int BEST_WHEN_BONUS = 20;

    private LocalPlanner() {
    }

    /**
     * Chooses the local tactic by priority alone, as the 0.0.1 did.
     *
     * @param profile tactical profile
     * @param active contexts whose native conditions hold
     * @param eligible tactics eligible now, as computed by {@link Eligibility}
     * @return the chosen tactic, or empty when no declared tactic is eligible
     */
    public static Optional<Tactic> choose(TacticalProfile profile, Set<ContextId> active, Set<TacticId> eligible) {
        return choose(profile, active, eligible, Set.of());
    }

    /**
     * Chooses the local tactic, breaking ties with the declared {@code BestWhen} of each skill.
     *
     * @param profile tactical profile
     * @param active contexts whose native conditions hold
     * @param eligible tactics eligible now, as computed by {@link Eligibility}
     * @param now use conditions that hold now ({@link ActiveUseConditions})
     * @return the chosen tactic, or empty when no declared tactic is eligible
     */
    public static Optional<Tactic> choose(TacticalProfile profile, Set<ContextId> active, Set<TacticId> eligible,
                                          Set<UseCondition> now) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(now, "now");
        var direct = Eligibility.direct(profile, active, eligible);
        if (direct.isPresent()) {
            return profile.tactic(direct.orElseThrow());
        }
        Tactic chosen = null;
        var best = Integer.MIN_VALUE;
        for (var tactic : profile.tactics().values()) {
            if (!eligible.contains(tactic.id())) {
                continue;
            }
            var score = score(profile, tactic, now);
            if (chosen == null || score > best) {
                chosen = tactic;
                best = score;
            }
        }
        return Optional.ofNullable(chosen);
    }

    /**
     * Score of a tactic: its priority plus {@value #BEST_WHEN_BONUS} per declared {@code BestWhen} condition that holds.
     *
     * @param profile tactical profile
     * @param tactic declared tactic
     * @param now use conditions that hold now
     * @return the score
     */
    public static int score(TacticalProfile profile, Tactic tactic, Set<UseCondition> now) {
        var choice = tactic.choice().map(profile.choices()::get);
        if (now.isEmpty() || choice.isEmpty() || !choice.get().authored()) {
            return tactic.priority();
        }
        var matches = 0;
        for (var condition : choice.get().typedPurpose().bestWhen()) {
            if (now.contains(condition)) {
                matches++;
            }
        }
        return tactic.priority() + BEST_WHEN_BONUS * matches;
    }
}
