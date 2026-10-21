package akyrion.thepredict.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The tactic a mob executes until it expires or is replaced. Immutable: changes create copies.
 *
 * <p>Invariants: confidence within {@code [0, 1]}; at most {@value #MAX_ALTERNATIVES} distinct
 * alternatives, none equal to {@code tactic}; contingencies only for {@code IF_TARGET_IN_MELEE}
 * and {@code IF_OWN_HEALTH_CRITICAL}; {@code startTick >= 0}.
 *
 * @param mob mob session
 * @param tactic tactic being executed
 * @param movement movement of the tactic
 * @param choice skill choice of the tactic, if any
 * @param confidence confidence of the answer that produced it
 * @param alternatives next tactics by preference, used at level {@code ALTERNATIVE}
 * @param contingencies tactic prepared for each situation
 * @param level control level that produced it
 * @param startTick server tick when the tactic started
 * @param expiresAtNanos monotonic deadline, compared overflow-safely
 * @param skillConsumed whether the choice was already cast for this intent
 */
public record TacticalIntent(MobKey mob, TacticId tactic, Movement movement,
                             Optional<ChoiceId> choice, double confidence,
                             List<TacticId> alternatives, Map<Situation, TacticId> contingencies,
                             ControlLevel level, long startTick, long expiresAtNanos,
                             boolean skillConsumed) {
    /** Most alternatives an intent keeps. */
    public static final int MAX_ALTERNATIVES = 3;

    /**
     * Validates the intent and copies its collections.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public TacticalIntent {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(tactic, "tactic");
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(choice, "choice");
        DomainChecks.requireUnit(confidence, "confidence");
        alternatives = List.copyOf(Objects.requireNonNull(alternatives, "alternatives"));
        contingencies = Map.copyOf(Objects.requireNonNull(contingencies, "contingencies"));
        Objects.requireNonNull(level, "level");
        DomainChecks.requireMaximumSize(alternatives, MAX_ALTERNATIVES, "alternatives");
        if (alternatives.contains(tactic) || new HashSet<>(alternatives).size() != alternatives.size()) {
            throw new IllegalArgumentException("alternatives must be distinct and exclude the tactic " + tactic);
        }
        if (contingencies.containsKey(Situation.NOW)) {
            throw new IllegalArgumentException("contingencies must not use situation NOW");
        }
        if (startTick < 0) {
            throw new IllegalArgumentException("startTick must be >= 0, was " + startTick);
        }
    }

    /**
     * Returns a copy marked as having cast its choice; this intent is not changed.
     *
     * @return the copy with {@code skillConsumed = true}
     */
    public TacticalIntent withSkillConsumed() {
        return new TacticalIntent(mob, tactic, movement, choice, confidence, alternatives, contingencies,
                level, startTick, expiresAtNanos, true);
    }

    /**
     * Copy with another deadline. Used by a reactive extension; not an adoption and not a renewal.
     *
     * @param deadlineNanos monotonic deadline
     * @return the copy
     */
    public TacticalIntent withExpiresAtNanos(long deadlineNanos) {
        return new TacticalIntent(mob, tactic, movement, choice, confidence, alternatives, contingencies,
                level, startTick, deadlineNanos, skillConsumed);
    }

    /**
     * Tells whether the intent expired, tolerating {@code System.nanoTime()} overflow.
     *
     * @param nowNanos current monotonic time
     * @return {@code true} if {@code nowNanos - expiresAtNanos >= 0}
     */
    public boolean expired(long nowNanos) {
        return nowNanos - expiresAtNanos >= 0;
    }

    /**
     * Builds the view that conditions and placeholders read.
     *
     * @return a view with the generation of {@code mob}
     */
    public IntentView toView() {
        return new IntentView(mob.generation(), level, movement, tactic, (float) confidence, expiresAtNanos);
    }
}
