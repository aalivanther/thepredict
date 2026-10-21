package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merge, distinct step and score cap of {@link OptionPruning} (M19.6). Package-private so the public rule stays in
 * one type. The distance used for {@code KEEP_DISTANCE} is {@code 0} in {@code MELEE} and just beyond
 * {@code Band.Outer} for {@code RANGED} and {@code BEYOND}, because a capture stores a band and not a block count;
 * both tactics of a comparison use that same distance.
 */
final class PruneSteps {
    private PruneSteps() {
    }

    static List<TacticId> merge(List<TacticId> open, TacticalProfile profile, MobFacts mob, CastMode mode,
                                Map<TacticId, PruneReason> dropped, TacticId keep, boolean keeping) {
        if (mode != CastMode.ANY_READY) {
            return open;
        }
        var castable = castableSets(open, profile, mob);
        var survivors = new ArrayList<TacticId>(open.size());
        for (var candidate : open) {
            var twin = twinOf(survivors, candidate, profile, castable);
            if (twin < 0) {
                survivors.add(candidate);
            } else if (keeping && candidate.equals(keep)) {
                dropped.put(survivors.set(twin, candidate), PruneReason.SAME_EFFECT_AS_HIGHER);
            } else {
                dropped.put(candidate, PruneReason.SAME_EFFECT_AS_HIGHER);
            }
        }
        return survivors;
    }

    static List<TacticId> distinct(List<TacticId> merged, TacticalProfile profile, Map<TacticId, PruneReason> dropped,
                                   TacticId keep, boolean keeping) {
        var ordinary = ordinary(merged, profile);
        var base = keeping && ordinary.contains(keep)
                ? OptionSet.distinct(ordinary, profile, keep) : OptionSet.distinct(ordinary, profile);
        var kept = new ArrayList<TacticId>(merged.size());
        for (var id : merged) {
            if (base.contains(id) || vanilla(profile, id)) {
                kept.add(id);
            } else {
                dropped.put(id, PruneReason.SAME_EFFECT_AS_HIGHER);
            }
        }
        return kept;
    }

    static List<TacticId> cap(List<TacticId> distinct, TacticalProfile profile, Set<UseCondition> now, int maxOptions,
                              Map<TacticId, PruneReason> dropped, TacticId keep, boolean keeping,
                              List<TacticId> eligibleOrdered) {
        var scored = new ArrayList<>(distinct);
        scored.sort((left, right) -> compareScore(profile, now, eligibleOrdered, left, right));
        var capped = keeping && scored.contains(keep)
                ? OptionSet.capped(scored, maxOptions, keep) : OptionSet.capped(scored, maxOptions);
        for (var id : scored) {
            if (!capped.contains(id)) {
                dropped.put(id, PruneReason.BELOW_OPTION_CAP);
            }
        }
        return capped;
    }

    private static int twinOf(List<TacticId> survivors, TacticId candidate, TacticalProfile profile,
                              Map<TacticId, Set<ChoiceId>> castable) {
        if (!castable.containsKey(candidate)) {
            return -1;
        }
        var tactic = profile.tactic(candidate).orElseThrow();
        for (var index = 0; index < survivors.size(); index++) {
            var earlier = survivors.get(index);
            if (!castable.containsKey(earlier)) {
                continue;
            }
            var other = profile.tactic(earlier).orElseThrow();
            if (tactic.movement() == other.movement() && tactic.description().equals(other.description())
                    && castable.get(candidate).equals(castable.get(earlier))) {
                return index;
            }
        }
        return -1;
    }

    private static Map<TacticId, Set<ChoiceId>> castableSets(List<TacticId> open, TacticalProfile profile,
                                                            MobFacts mob) {
        var distance = directionDistance(profile, mob);
        var sets = new LinkedHashMap<TacticId, Set<ChoiceId>>();
        for (var id : open) {
            var tactic = profile.tactic(id).orElseThrow();
            if (exemptFromMerge(profile, tactic)) {
                continue;
            }
            sets.put(id, castable(profile, tactic, distance));
        }
        return sets;
    }

    private static double directionDistance(TacticalProfile profile, MobFacts mob) {
        var beyondMelee = mob.target().map(relation -> relation.distance() != DistanceBand.MELEE).orElse(false);
        return beyondMelee ? profile.movement().bandOuterBlocks() + 1.0 : 0.0;
    }

    private static boolean exemptFromMerge(TacticalProfile profile, Tactic tactic) {
        return tactic.choice().map(profile.choices()::get)
                .filter(choice -> !choice.isTyped() || choice.kind() == ChoiceKind.VANILLA_MELEE).isPresent();
    }

    private static List<TacticId> ordinary(List<TacticId> merged, TacticalProfile profile) {
        var ordinary = new ArrayList<TacticId>(merged.size());
        for (var id : merged) {
            if (!vanilla(profile, id)) {
                ordinary.add(id);
            }
        }
        return ordinary;
    }

    private static boolean vanilla(TacticalProfile profile, TacticId id) {
        return profile.tactic(id).flatMap(Tactic::choice).map(profile.choices()::get)
                .filter(choice -> choice.kind() == ChoiceKind.VANILLA_MELEE).isPresent();
    }

    private static Set<ChoiceId> castable(TacticalProfile profile, Tactic tactic, double distance) {
        var allowed = new LinkedHashSet<ChoiceId>();
        tactic.choice().ifPresent(allowed::add);
        for (var choice : profile.choices().values()) {
            if (choice.kind() != ChoiceKind.MYTHIC_SKILL) {
                continue;
            }
            if (!choice.isTyped() && profile.timing().untypedCast() != UntypedCastMode.ANY_READY) {
                continue;
            }
            if (choice.isTyped() && !ReadyCastOrder.directionAllows(choice.typedPurpose(), tactic.movement(), distance,
                    profile.movement())) {
                continue;
            }
            allowed.add(choice.id());
        }
        return allowed;
    }

    private static int compareScore(TacticalProfile profile, Set<UseCondition> now, List<TacticId> order,
                                    TacticId left, TacticId right) {
        var score = Integer.compare(LocalPlanner.score(profile, profile.tactic(right).orElseThrow(), now),
                LocalPlanner.score(profile, profile.tactic(left).orElseThrow(), now));
        return score != 0 ? score : Integer.compare(order.indexOf(left), order.indexOf(right));
    }
}
