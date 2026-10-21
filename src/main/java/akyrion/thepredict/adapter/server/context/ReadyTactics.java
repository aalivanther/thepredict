package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.Eligibility;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ReachMeasure;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import org.bukkit.util.BoundingBox;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Inputs of {@link Eligibility} read at capture time, so {@code MobFacts.eligibleTactics} holds only tactics the
 * mob can use now (plan §6: "solo tácticas elegibles; sin números"). Stateless and pure; the skill cooldown comes
 * from MythicMobs through a predicate. The skill's own conditions are checked again right before the cast (M10.3).
 */
final class ReadyTactics {
    private ReadyTactics() {
    }

    /**
     * Eligible tactics of a mob, without choices out of reach.
     *
     * @param profile tactical profile
     * @param active active contexts
     * @param brainSpecies whether the species only allows {@code Movement.NONE}
     * @param hasTarget whether the mob has a target in the capture
     * @param flankable whether the target relation is flankable
     * @param offCooldown tells whether the skill of a {@code MYTHIC_SKILL} choice is off its native cooldown
     * @return eligible tactics by descending priority
     */
    static Set<TacticId> eligible(TacticalProfile profile, Set<ContextId> active, boolean brainSpecies,
                                  boolean hasTarget, boolean flankable, Predicate<ChoiceId> offCooldown) {
        return eligible(profile, active, brainSpecies, hasTarget, flankable, offCooldown, Set.of());
    }

    /**
     * Eligible tactics of a mob; a tactic whose declared skill is out of reach and whose movement never lets it cast is
     * not eligible (M15.5).
     *
     * @param profile tactical profile
     * @param active active contexts
     * @param brainSpecies whether the species only allows {@code Movement.NONE}
     * @param hasTarget whether the mob has a target in the capture
     * @param flankable whether the target relation is flankable
     * @param offCooldown tells whether the skill of a {@code MYTHIC_SKILL} choice is off its native cooldown
     * @param outOfReach choices out of reach now ({@link #outOfReach})
     * @return eligible tactics by descending priority
     */
    static Set<TacticId> eligible(TacticalProfile profile, Set<ContextId> active, boolean brainSpecies,
                                  boolean hasTarget, boolean flankable, Predicate<ChoiceId> offCooldown,
                                  Set<ChoiceId> outOfReach) {
        return Eligibility.eligible(profile, active, readyChoices(profile, hasTarget, offCooldown),
                availableMovements(brainSpecies, hasTarget, flankable), outOfReach);
    }

    /**
     * Tactics that would be eligible under each contingency hypothesis (M16.6), so a contingency only offers what it
     * could really use: {@code IF_TARGET_IN_MELEE} evaluates the shortcut contexts with the target in melee, counts out
     * of reach only the declared choices whose reach is shorter than {@code meleeReachBlocks} and keeps the flank of the
     * current shield; {@code IF_OWN_HEALTH_CRITICAL} evaluates the shortcut contexts with a critical health and keeps
     * the choices out of reach now. Contexts with native MythicMobs conditions are not evaluated again under a
     * hypothesis (they read the real entity, M07.7): they keep their current state.
     *
     * @param profile tactical profile
     * @param active contexts active now
     * @param brainSpecies whether the species only allows {@code Movement.NONE}
     * @param flankable whether the target relation is flankable now
     * @param offCooldown tells whether the skill of a {@code MYTHIC_SKILL} choice is off its native cooldown
     * @param outOfReach choices out of reach now
     * @param now health and target distance now
     * @return the eligible tactics of each contingency situation
     */
    static Map<Situation, Set<TacticId>> contingencyEligible(TacticalProfile profile, Set<ContextId> active,
                                                              boolean brainSpecies, boolean flankable,
                                                              Predicate<ChoiceId> offCooldown, Set<ChoiceId> outOfReach,
                                                              ShortcutFacts now) {
        var ready = readyChoices(profile, true, offCooldown);
        var movements = availableMovements(brainSpecies, true, flankable);
        var melee = hypothetical(profile, active, new ShortcutFacts(now.health(), Optional.of(DistanceBand.MELEE)));
        var critical = hypothetical(profile, active, new ShortcutFacts(HealthBand.CRITICAL, now.targetDistance()));
        var eligible = new EnumMap<Situation, Set<TacticId>>(Situation.class);
        eligible.put(Situation.IF_TARGET_IN_MELEE, Eligibility.eligible(profile, melee, ready, movements,
                outOfReachInMelee(profile)));
        eligible.put(Situation.IF_OWN_HEALTH_CRITICAL, Eligibility.eligible(profile, critical, ready, movements,
                outOfReach));
        return eligible;
    }

    /** The active contexts with the shortcut contexts evaluated under a hypothesis and the native ones kept. */
    private static Set<ContextId> hypothetical(TacticalProfile profile, Set<ContextId> active, ShortcutFacts hypothesis) {
        var contexts = new LinkedHashSet<ContextId>();
        for (var context : profile.contexts()) {
            if (context.isShortcut() ? hypothesis.holds(context.id()) : active.contains(context.id())) {
                contexts.add(context.id());
            }
        }
        return contexts;
    }

    /**
     * Authored choices whose reach misses a target inside melee reach. {@code POSITIONS_STRICT} compares
     * {@code r < meleeReach + 0.6} (about half a player box); every other measure keeps {@code r < meleeReach}.
     * An untyped choice and an inferred purpose are not measured.
     */
    static Set<ChoiceId> outOfReachInMelee(TacticalProfile profile) {
        var out = new LinkedHashSet<ChoiceId>();
        var melee = profile.movement().meleeReachBlocks();
        for (var choice : profile.choices().values()) {
            var purpose = choice.purpose().filter(SkillPurpose::authored).orElse(null);
            if (purpose == null || purpose.effectiveReachBlocks().isEmpty()) {
                continue;
            }
            var limit = purpose.reachMeasure() == ReachMeasure.POSITIONS_STRICT ? melee + 0.6 : melee;
            if (purpose.effectiveReachBlocks().getAsDouble() < limit) {
                out.add(choice.id());
            }
        }
        return out;
    }

    /**
     * Tells whether a profile has a choice whose reach the capture measures: a purpose written by the author with a
     * reach limit. Without one, the capture reads no bounding box.
     *
     * @param profile tactical profile
     * @return whether {@link #outOfReach} can find anything
     */
    static boolean measuresReach(TacticalProfile profile) {
        for (var choice : profile.choices().values()) {
            var purpose = choice.purpose().filter(SkillPurpose::authored).orElse(null);
            if (purpose != null && purpose.effectiveReachBlocks().isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Choices whose skill does not reach the target now, with the geometry of the pre-cast check
     * ({@link ReachGeometry}). Only choices with a purpose written by the author and a reach limit are measured: an
     * untyped choice and an inferred purpose are never out of reach, so a profile without {@code Purpose} is eligible
     * as in the 0.0.1. At most
     * one O(1) measure per choice, without line of sight or path; the set is created only when a choice is out of reach
     * (C12).
     *
     * @param profile tactical profile
     * @param caster bounding box of the mob
     * @param target bounding box of its perceived target
     * @param positionDistanceSquared squared distance between the positions of the mob and its target
     * @return the choices out of reach, empty when every choice reaches
     */
    static Set<ChoiceId> outOfReach(TacticalProfile profile, BoundingBox caster, BoundingBox target,
                                    double positionDistanceSquared) {
        Set<ChoiceId> out = null;
        for (var choice : profile.choices().values()) {
            var purpose = choice.purpose().filter(SkillPurpose::authored).orElse(null);
            if (purpose != null && purpose.effectiveReachBlocks().isPresent()
                    && !ReachGeometry.withinReach(purpose, caster, target, positionDistanceSquared)) {
                if (out == null) {
                    out = new LinkedHashSet<>();
                }
                out.add(choice.id());
            }
        }
        return out == null ? Set.of() : out;
    }

    /**
     * Choices that can be cast now: a choice that requires a target needs one, and a MythicMobs skill must be off
     * its native cooldown.
     *
     * @param profile tactical profile
     * @param hasTarget whether the mob has a target
     * @param offCooldown tells whether the skill of a {@code MYTHIC_SKILL} choice is off cooldown
     * @return ready choices
     */
    static Set<ChoiceId> readyChoices(TacticalProfile profile, boolean hasTarget, Predicate<ChoiceId> offCooldown) {
        var ready = new LinkedHashSet<ChoiceId>();
        for (var choice : profile.choices().values()) {
            if (choice.requiresTarget() && !hasTarget) {
                continue;
            }
            if (choice.kind() == ChoiceKind.MYTHIC_SKILL && !offCooldown.test(choice.id())) {
                continue;
            }
            ready.add(choice.id());
        }
        return ready;
    }

    /**
     * Movements the mob can perform now: only {@code NONE} for a species with a {@code Brain}; only {@code HOLD}
     * and {@code NONE} without a target, because every other movement is relative to it; with a target every
     * movement except {@code FLANK}, which needs a flankable target (M10.8).
     *
     * @param brainSpecies whether the species only allows {@code Movement.NONE}
     * @param hasTarget whether the mob has a target
     * @param flankable whether the target relation is flankable
     * @return available movements
     */
    static Set<Movement> availableMovements(boolean brainSpecies, boolean hasTarget, boolean flankable) {
        if (brainSpecies) {
            return EnumSet.of(Movement.NONE);
        }
        if (!hasTarget) {
            return EnumSet.of(Movement.HOLD, Movement.NONE);
        }
        var movements = EnumSet.allOf(Movement.class);
        if (!flankable) {
            movements.remove(Movement.FLANK);
        }
        return movements;
    }
}
