package akyrion.thepredict.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure rule that tells which {@link UseCondition}s hold in a capture (M15.8): the local planner breaks ties with them
 * and M16 describes with them what fits an option now. Stateless, reads no clock, safe on any thread.
 *
 * <p>{@code TARGET_LOW_HEALTH} is a {@code CRITICAL} target; {@code TARGET_SHIELDING} a target {@code BLOCKING} or
 * {@code RAISING_SHIELD}; {@code TARGET_FLEEING} a target {@code BACKING_AWAY} outside {@code MELEE};
 * {@code SELF_LOW_HEALTH} a {@code CRITICAL} mob; {@code ENEMIES_GROUPED} a {@code grouped} target;
 * {@code TARGET_AT_RANGE} and {@code TARGET_IN_MELEE} the {@code RANGED} and {@code MELEE} distance bands. Without a
 * target only {@code SELF_LOW_HEALTH} can hold; without the facts of the target participant, its health and shield do
 * not count.
 */
public final class ActiveUseConditions {
    private ActiveUseConditions() {
    }

    /**
     * The conditions that hold for a mob, reading its target among the participants of the same capture.
     *
     * @param mob facts of the mob
     * @param participants participants of the capture; the one with the alias of the mob's target is used
     * @return a new set of the conditions that hold
     */
    public static Set<UseCondition> of(MobFacts mob, List<ParticipantFacts> participants) {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(participants, "participants");
        var conditions = relation(mob);
        mob.target().ifPresent(relation -> {
            for (var participant : participants) {
                if (participant.alias().equals(relation.target())) {
                    addTarget(conditions, participant);
                    return;
                }
            }
        });
        return conditions;
    }

    /**
     * The conditions that hold for a mob and the facts of its target.
     *
     * @param mob facts of the mob, with a target
     * @param target facts of the participant the mob targets
     * @return a new set of the conditions that hold
     * @throws IllegalArgumentException if the mob has no target or targets another participant
     */
    public static Set<UseCondition> of(MobFacts mob, ParticipantFacts target) {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(target, "target");
        if (mob.target().map(relation -> !relation.target().equals(target.alias())).orElse(true)) {
            throw new IllegalArgumentException("target " + target.alias().text() + " is not the target of the mob");
        }
        var conditions = relation(mob);
        addTarget(conditions, target);
        return conditions;
    }

    private static Set<UseCondition> relation(MobFacts mob) {
        var conditions = EnumSet.noneOf(UseCondition.class);
        if (mob.health() == HealthBand.CRITICAL) {
            conditions.add(UseCondition.SELF_LOW_HEALTH);
        }
        mob.target().ifPresent(relation -> {
            if (relation.trend() == Trend.BACKING_AWAY && relation.distance() != DistanceBand.MELEE) {
                conditions.add(UseCondition.TARGET_FLEEING);
            }
            if (relation.grouped()) {
                conditions.add(UseCondition.ENEMIES_GROUPED);
            }
            if (relation.distance() == DistanceBand.RANGED) {
                conditions.add(UseCondition.TARGET_AT_RANGE);
            } else if (relation.distance() == DistanceBand.MELEE) {
                conditions.add(UseCondition.TARGET_IN_MELEE);
            }
        });
        return conditions;
    }

    private static void addTarget(Set<UseCondition> conditions, ParticipantFacts target) {
        if (target.health() == HealthBand.CRITICAL) {
            conditions.add(UseCondition.TARGET_LOW_HEALTH);
        }
        if (target.cues().contains(TargetCue.BLOCKING) || target.cues().contains(TargetCue.RAISING_SHIELD)) {
            conditions.add(UseCondition.TARGET_SHIELDING);
        }
    }
}
