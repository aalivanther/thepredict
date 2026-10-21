package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Safe pruning of one question (M19.6). Pure, stateless and without a clock. {@link PruningMode#OFF} never calls it:
 * the question keeps today's priority order. A tactic whose choice has no purpose is exempt from the vetoes and from
 * the merge. {@code keep} is never removed.
 */
public final class OptionPruning {
    private static final Set<Delivery> RANGED = Set.of(Delivery.PROJECTILE, Delivery.HITSCAN,
            Delivery.AREA_AT_TARGET, Delivery.CONE);

    private OptionPruning() {
    }

    /**
     * Options kept after the safe vetoes, the merge, the distinct step and the cap, and why each other option left.
     *
     * @param kept options sent to the question, in the pruned order
     * @param dropped why each removed option left; empty when a veto pass was undone
     * @param vetoesUndone whether the veto pass would have left fewer than two options and was discarded
     */
    public record PruneResult(List<TacticId> kept, Map<TacticId, PruneReason> dropped, boolean vetoesUndone) {
        /**
         * Copies the lists.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public PruneResult {
            kept = List.copyOf(Objects.requireNonNull(kept, "kept"));
            dropped = Map.copyOf(Objects.requireNonNull(dropped, "dropped"));
        }
    }

    /**
     * Prunes without a tactic to keep.
     *
     * @param eligibleOrdered options by descending priority and then by ID, already inside the horizon
     * @param profile profile that declares them
     * @param mob facts of the mob
     * @param now use conditions that hold for this question, hypothetical for a contingency
     * @param mode cast mode; the merge runs only for {@code ANY_READY}
     * @param maxOptions cap, {@code >= 2}
     * @return kept options and the cause of each drop
     */
    public static PruneResult prune(List<TacticId> eligibleOrdered, TacticalProfile profile, MobFacts mob,
                                    Set<UseCondition> now, CastMode mode, int maxOptions) {
        return prune(eligibleOrdered, profile, mob, now, mode, maxOptions, null, false, null);
    }

    /**
     * Prunes with the effective zone mode. An empty profile mode does not count as off when the global mode is full.
     *
     * @param eligibleOrdered options by descending priority and then by ID
     * @param profile profile that declares them
     * @param mob facts of the mob
     * @param now use conditions that hold
     * @param mode cast mode
     * @param maxOptions cap, {@code >= 2}
     * @param zoneMode effective zone mode
     * @return kept options and the cause of each drop
     */
    public static PruneResult prune(List<TacticId> eligibleOrdered, TacticalProfile profile, MobFacts mob,
                                    Set<UseCondition> now, CastMode mode, int maxOptions, ZoneMode zoneMode) {
        return prune(eligibleOrdered, profile, mob, now, mode, maxOptions, null, false,
                Objects.requireNonNull(zoneMode, "zoneMode"));
    }

    /**
     * Prunes, never removing {@code keep}.
     *
     * @param eligibleOrdered options by descending priority and then by ID
     * @param profile profile that declares them
     * @param mob facts of the mob
     * @param now use conditions that hold for this question
     * @param mode cast mode
     * @param maxOptions cap, {@code >= 2}
     * @param keep tactic of the current intent
     * @return kept options and the cause of each drop
     */
    public static PruneResult prune(List<TacticId> eligibleOrdered, TacticalProfile profile, MobFacts mob,
                                    Set<UseCondition> now, CastMode mode, int maxOptions, TacticId keep) {
        return prune(eligibleOrdered, profile, mob, now, mode, maxOptions,
                Objects.requireNonNull(keep, "keep"), true, null);
    }

    /**
     * Prunes, never removing {@code keep}, with the effective zone mode.
     *
     * @param eligibleOrdered options by descending priority and then by ID
     * @param profile profile that declares them
     * @param mob facts of the mob
     * @param now use conditions that hold
     * @param mode cast mode
     * @param maxOptions cap, {@code >= 2}
     * @param keep tactic of the current intent
     * @param zoneMode effective zone mode
     * @return kept options and the cause of each drop
     */
    public static PruneResult prune(List<TacticId> eligibleOrdered, TacticalProfile profile, MobFacts mob,
                                    Set<UseCondition> now, CastMode mode, int maxOptions, TacticId keep,
                                    ZoneMode zoneMode) {
        return prune(eligibleOrdered, profile, mob, now, mode, maxOptions,
                Objects.requireNonNull(keep, "keep"), true, Objects.requireNonNull(zoneMode, "zoneMode"));
    }

    private static PruneResult prune(List<TacticId> eligibleOrdered, TacticalProfile profile, MobFacts mob,
                                     Set<UseCondition> now, CastMode mode, int maxOptions, TacticId keep,
                                     boolean keeping, ZoneMode zoneMode) {
        Objects.requireNonNull(eligibleOrdered, "eligibleOrdered");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(mode, "mode");
        DomainChecks.requireRange(maxOptions, OptionSet.MIN_MAX_OPTIONS, Integer.MAX_VALUE, "maxOptions");
        var dropped = new LinkedHashMap<TacticId, PruneReason>();
        var vetoed = vetoes(eligibleOrdered, profile, mob, now, mode, keep, keeping, zoneMode);
        var undone = eligibleOrdered.size() - vetoed.size() < QuestionSpec.MIN_OPTIONS && !vetoed.isEmpty();
        var open = new ArrayList<TacticId>(eligibleOrdered.size());
        for (var id : eligibleOrdered) {
            if (undone || !vetoed.containsKey(id)) {
                open.add(id);
            }
        }
        if (!undone) {
            dropped.putAll(vetoed);
        }
        var merged = PruneSteps.merge(open, profile, mob, mode, dropped, keep, keeping);
        var distinct = PruneSteps.distinct(merged, profile, dropped, keep, keeping);
        var capped = PruneSteps.cap(distinct, profile, now, maxOptions, dropped, keep, keeping, eligibleOrdered);
        return new PruneResult(capped, dropped, undone);
    }

    private static Map<TacticId, PruneReason> vetoes(List<TacticId> ordered, TacticalProfile profile, MobFacts mob,
                                                     Set<UseCondition> now, CastMode mode, TacticId keep,
                                                     boolean keeping, ZoneMode zoneMode) {
        var vetoed = new LinkedHashMap<TacticId, PruneReason>();
        for (var id : ordered) {
            if (keeping && id.equals(keep)) {
                continue;
            }
            veto(profile, mob, now, mode, ordered, id, zoneMode).ifPresent(reason -> vetoed.put(id, reason));
        }
        return vetoed;
    }

    private static Optional<PruneReason> veto(TacticalProfile profile, MobFacts mob, Set<UseCondition> now,
                                              CastMode mode, List<TacticId> ordered, TacticId id, ZoneMode zoneMode) {
        var tactic = profile.tactic(id).orElseThrow(() -> new IllegalArgumentException("undeclared tactic " + id));
        if (untyped(profile, tactic)) {
            return Optional.empty();
        }
        if (sustainWhileHealthy(profile, tactic, mob, now)) {
            return Optional.of(PruneReason.SUSTAIN_WHILE_HEALTHY);
        }
        if (outOfReach(profile, tactic, mob)) {
            return Optional.of(PruneReason.OUT_OF_REACH_STATIC);
        }
        if (noSight(profile, tactic, mob, mode, ordered)) {
            return Optional.of(PruneReason.NO_SIGHT_STATIC);
        }
        if (unreachable(profile, tactic, mob)) {
            return Optional.of(PruneReason.UNREACHABLE_ON_FOOT);
        }
        var effective = zoneMode == null ? profile.zone().mode().orElse(ZoneMode.OFF) : zoneMode;
        if (mob.rawNoRetreat() && tactic.movement() == Movement.RETREAT && effective == ZoneMode.FULL) {
            return Optional.of(PruneReason.CORNERED_RETREAT);
        }
        return Optional.empty();
    }

    private static boolean sustainWhileHealthy(TacticalProfile profile, Tactic tactic, MobFacts mob,
                                               Set<UseCondition> now) {
        if (now.contains(UseCondition.SELF_LOW_HEALTH) || !fightInFront(mob)) {
            return false;
        }
        var purpose = authored(profile, tactic);
        return mob.health() == HealthBand.HEALTHY && purpose.isPresent() && purpose.get().primary() == SkillRole.SUSTAIN;
    }

    /** A healthy heal is useless only while a visible, reachable target can be fought. */
    private static boolean fightInFront(MobFacts mob) {
        return mob.target().filter(relation -> relation.visible() && relation.reachable()).isPresent();
    }

    private static boolean outOfReach(TacticalProfile profile, Tactic tactic, MobFacts mob) {
        if (tactic.movement() != Movement.HOLD && tactic.movement() != Movement.RETREAT) {
            return false;
        }
        return tactic.choice().filter(mob.choicesOutOfReach()::contains).isPresent() && authored(profile, tactic).isPresent();
    }

    private static boolean noSight(TacticalProfile profile, Tactic tactic, MobFacts mob, CastMode mode,
                                   List<TacticId> ordered) {
        if (tactic.movement() != Movement.HOLD || mob.target().map(TargetRelation::visible).orElse(true)) {
            return false;
        }
        var purpose = authored(profile, tactic);
        if (purpose.isEmpty() || !purpose.get().risks().contains(SkillRisk.NEEDS_LINE_OF_SIGHT)) {
            return false;
        }
        return mode != CastMode.INTENT_ONLY || otherHold(profile, ordered, tactic.id());
    }

    private static boolean otherHold(TacticalProfile profile, List<TacticId> ordered, TacticId self) {
        for (var id : ordered) {
            if (!id.equals(self) && profile.tactic(id).map(tactic -> tactic.movement() == Movement.HOLD).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean unreachable(TacticalProfile profile, Tactic tactic, MobFacts mob) {
        if (tactic.movement() != Movement.APPROACH && tactic.movement() != Movement.FLANK) {
            return false;
        }
        if (mob.target().map(TargetRelation::reachable).orElse(true)) {
            return false;
        }
        return tactic.choice().flatMap(id -> ranged(profile.choices().get(id))).isEmpty();
    }

    private static Optional<SkillPurpose> ranged(SkillChoice choice) {
        if (choice == null || !choice.isTyped() || !RANGED.contains(choice.typedPurpose().delivery())) {
            return Optional.empty();
        }
        return Optional.of(choice.typedPurpose());
    }

    private static boolean untyped(TacticalProfile profile, Tactic tactic) {
        return tactic.choice().map(profile.choices()::get).filter(choice -> !choice.isTyped()).isPresent();
    }

    private static Optional<SkillPurpose> authored(TacticalProfile profile, Tactic tactic) {
        return tactic.choice().map(profile.choices()::get).filter(Objects::nonNull).filter(SkillChoice::authored)
                .map(SkillChoice::typedPurpose);
    }
}
