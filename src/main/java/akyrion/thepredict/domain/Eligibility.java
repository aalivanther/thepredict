package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure rule that decides which tactics of a profile a mob may use now. Stateless, reads no clock,
 * safe on any thread.
 *
 * <p>The active context with the highest priority, ties broken by declaration order, restricts
 * the tactics to its allowed set; without an active context every tactic is a candidate. A
 * candidate stays eligible only if its choice, when it has one, is ready and its movement is
 * available. Tactics without a choice are never excluded by choices. A tactic whose choice is out of reach now is
 * excluded only when that choice has a purpose written by the author ({@link SkillPurpose#authored()}) and its movement
 * never lets it cast ({@link PurposeRules.PurposeFit#NEVER_CASTS}, M15.5); every other tactic stays, because waiting for
 * the target to come is legitimate.
 */
public final class Eligibility {
    private Eligibility() {
    }

    /**
     * Computes the eligible tactics without choices out of reach, as the 0.0.1 did.
     *
     * @param profile tactical profile
     * @param active contexts whose native conditions hold
     * @param readyChoices choices that can be cast now (cooldown, conditions and target)
     * @param availableMovements movements the mob can perform now
     * @return immutable set ordered by descending priority and then declaration order
     */
    public static Set<TacticId> eligible(TacticalProfile profile, Set<ContextId> active,
                                         Set<ChoiceId> readyChoices, Set<Movement> availableMovements) {
        return eligible(profile, active, readyChoices, availableMovements, Set.of());
    }

    /**
     * Computes the eligible tactics.
     *
     * @param profile tactical profile
     * @param active contexts whose native conditions hold
     * @param readyChoices choices that can be cast now (cooldown, conditions and target)
     * @param availableMovements movements the mob can perform now
     * @param choicesOutOfReach choices whose reach does not get to the target now ({@code MobFacts.choicesOutOfReach})
     * @return immutable set ordered by descending priority and then declaration order
     */
    public static Set<TacticId> eligible(TacticalProfile profile, Set<ContextId> active, Set<ChoiceId> readyChoices,
                                         Set<Movement> availableMovements, Set<ChoiceId> choicesOutOfReach) {
        Objects.requireNonNull(readyChoices, "readyChoices");
        Objects.requireNonNull(availableMovements, "availableMovements");
        Objects.requireNonNull(choicesOutOfReach, "choicesOutOfReach");
        var governing = governingContext(profile, active);
        var candidates = new ArrayList<Tactic>();
        for (var tactic : profile.tactics().values()) {
            var allowed = governing.map(context -> context.allowedTactics().contains(tactic.id())).orElse(true);
            var choiceReady = tactic.choice().map(readyChoices::contains).orElse(true);
            if (allowed && choiceReady && availableMovements.contains(tactic.movement())
                    && !neverCasts(profile, tactic, choicesOutOfReach)) {
                candidates.add(tactic);
            }
        }
        candidates.sort(Comparator.comparingInt(Tactic::priority).reversed());
        var ordered = new LinkedHashSet<TacticId>();
        for (var tactic : candidates) {
            ordered.add(tactic.id());
        }
        return Collections.unmodifiableSet(ordered);
    }

    /**
     * Tells whether a tactic can never cast now: its choice is out of reach, has a purpose written by the author and
     * its movement keeps the target beyond that reach.
     *
     * @param profile tactical profile
     * @param tactic declared tactic
     * @param choicesOutOfReach choices out of reach now
     * @return whether the tactic is excluded
     */
    public static boolean neverCasts(TacticalProfile profile, Tactic tactic, Set<ChoiceId> choicesOutOfReach) {
        if (choicesOutOfReach.isEmpty() || tactic.choice().isEmpty()
                || !choicesOutOfReach.contains(tactic.choice().orElseThrow())) {
            return false;
        }
        var choice = profile.choices().get(tactic.choice().orElseThrow());
        return choice != null && choice.authored() && PurposeRules.fit(tactic.movement(), choice.typedPurpose(),
                profile.movement()) == PurposeRules.PurposeFit.NEVER_CASTS;
    }

    /**
     * Returns the direct tactic of the governing context when it is eligible, so it is used without
     * asking the model.
     *
     * @param profile tactical profile
     * @param active contexts whose native conditions hold
     * @param eligible tactics eligible now
     * @return the direct tactic, or empty when there is none or it is not eligible
     */
    public static Optional<TacticId> direct(TacticalProfile profile, Set<ContextId> active, Set<TacticId> eligible) {
        Objects.requireNonNull(eligible, "eligible");
        return governingContext(profile, active).flatMap(ContextRule::directTactic).filter(eligible::contains);
    }

    /**
     * Returns the active context with the highest priority, ties broken by declaration order.
     *
     * @param profile tactical profile
     * @param active contexts whose native conditions hold
     * @return the governing context, or empty when no declared context is active
     */
    public static Optional<ContextRule> governingContext(TacticalProfile profile, Set<ContextId> active) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(active, "active");
        ContextRule governing = null;
        for (var context : profile.contexts()) {
            if (active.contains(context.id()) && (governing == null || context.priority() > governing.priority())) {
                governing = context;
            }
        }
        return Optional.ofNullable(governing);
    }
}
