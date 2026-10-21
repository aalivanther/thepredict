package akyrion.thepredict.adapter.server.execution;

import akyrion.thepredict.adapter.server.context.ReachGeometry;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ImpactTiming;
import akyrion.thepredict.domain.PurposeRules;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;

import java.util.UUID;

/**
 * Local checks that reproduce vanilla hit rules before a cast (plan §5, M10.3, M10.9, M15.6, M19.3). Package-private;
 * the entity reads run on the server thread only, the rest is pure.
 *
 * <p>A typed purpose is checked by its {@link akyrion.thepredict.domain.ReachMeasure}: reach with
 * {@link ReachGeometry}. An untyped choice has no reach check. A vanilla melee choice keeps the 0.0.1 rule: the gap
 * between the boxes is at most {@code meleeReachBlocks}. After the reach, and only then, a line of sight is required
 * when the purpose risks {@code NEEDS_LINE_OF_SIGHT} or the native facts require {@code lineofsight} (M19.3).
 * {@code getLocation()} is read only when the measure is between positions.
 *
 * <p>Evidence (javap, Paper 26.2 server patched locally, never started): {@code LivingEntity.hurtServer} ignores a
 * hit that does not beat the previous one while {@code (float) invulnerableTime > (float) invulnerableDuration / 2};
 * {@code CraftLivingEntity.getNoDamageTicks()} and {@code getMaximumNoDamageTicks()} return those two fields.
 * {@code CraftLivingEntity.attack(Entity)} calls {@code doHurtTarget} for a mob with no reach limit of its own, so the
 * reach of a vanilla melee choice is checked here.
 */
final class HitChecks {
    private HitChecks() {
    }

    /** Result of the reach and sight check of a cast. */
    enum ReachVerdict {
        /** The target is within reach and, when required, in sight. */
        IN_REACH,
        /** The target is beyond the reach of the choice. */
        OUT_OF_REACH,
        /** The choice needs a line of sight and the caster does not see the target. */
        NO_LINE_OF_SIGHT
    }

    /**
     * Tells whether a choice hits the target: a vanilla melee choice, or a choice that needs a target and is damaging.
     * An untyped choice that needs a target counts as damaging ({@link PurposeRules#UNTYPED_DEFAULTS}). Only such a
     * cast waits for invulnerability and respects the shield (plan §5, M19.2).
     *
     * @param choice choice of the cast
     * @return whether the hit rules apply
     */
    static boolean damaging(SkillChoice choice) {
        if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
            return true;
        }
        if (!choice.requiresTarget()) {
            return false;
        }
        return choice.purpose().map(purpose -> purpose.damaging() && purpose.aimedAtTarget())
                .orElse(PurposeRules.UNTYPED_DEFAULTS.damaging());
    }

    /**
     * Reach and sight of a cast. A vanilla melee choice uses the gap against {@code meleeReachBlocks} and reads no
     * sight. An untyped choice is always in reach. A typed purpose uses {@link ReachGeometry#withinReach}. The line of
     * sight is read only after the reach, when {@link #needsLineOfSight} is true, through {@code sight} so one tick
     * reads at most one ray per mob (M19.5). Both entities are in the same world.
     *
     * @param choice choice of the intent
     * @param caster living caster
     * @param target current target
     * @param meleeReachBlocks melee reach of the session profile, for the vanilla melee rule
     * @param mobId UUID of the caster, the key of a remembered sight
     * @param sight line of sight reader of the adapter
     * @return the verdict
     */
    static ReachVerdict reach(SkillChoice choice, LivingEntity caster, LivingEntity target, double meleeReachBlocks,
                              UUID mobId, LineOfSight sight) {
        if (!inReach(choice, caster, target, meleeReachBlocks)) {
            return ReachVerdict.OUT_OF_REACH;
        }
        if (choice.kind() != ChoiceKind.VANILLA_MELEE && needsLineOfSight(choice)
                && !sight.sees(mobId, caster, target)) {
            return ReachVerdict.NO_LINE_OF_SIGHT;
        }
        return ReachVerdict.IN_REACH;
    }

    /**
     * Reach without a line of sight (M19.5). The cheap admission of a candidate uses this so a missed line of sight
     * is not confused with being out of reach.
     *
     * @param choice choice of the cast
     * @param caster living caster
     * @param target current target
     * @param meleeReachBlocks melee reach of the session profile, for the vanilla melee rule
     * @return whether the target is within reach
     */
    static boolean inReach(SkillChoice choice, LivingEntity caster, LivingEntity target, double meleeReachBlocks) {
        if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
            return gapBlocks(caster.getBoundingBox(), target.getBoundingBox()) <= meleeReachBlocks;
        }
        var purpose = choice.purpose();
        if (purpose.isEmpty()) {
            return true;
        }
        var found = purpose.get();
        return found.effectiveReachBlocks().isEmpty() || ReachGeometry.withinReach(found, caster.getBoundingBox(),
                target.getBoundingBox(), positionDistanceSquared(found, caster, target));
    }

    /**
     * Whether a cast must see its target before it runs: the native {@code lineofsight} condition, or the risk
     * {@code NEEDS_LINE_OF_SIGHT}.
     *
     * @param choice choice of the cast
     * @return whether the line of sight gates the cast
     */
    static boolean needsLineOfSight(SkillChoice choice) {
        return choice.nativeFacts().requiresLineOfSight() || choice.purpose()
                .filter(purpose -> purpose.risks().contains(SkillRisk.NEEDS_LINE_OF_SIGHT)).isPresent();
    }

    /**
     * Squared distance between the entity positions, read only when the measure is between positions; {@code 0} for
     * any other measure, which ignores it.
     */
    private static double positionDistanceSquared(SkillPurpose purpose, LivingEntity caster, LivingEntity target) {
        return purpose.reachMeasure().betweenPositions()
                ? caster.getLocation().distanceSquared(target.getLocation()) : 0.0;
    }

    /**
     * Tells whether a damaging cast waits for the vanilla invulnerability window. Native facts that ignore
     * invulnerability omit it (M19.3).
     *
     * @param choice choice of the cast
     * @param target current target
     * @return whether the cast is deferred
     */
    static boolean deferInvulnerable(SkillChoice choice, LivingEntity target) {
        if (choice.nativeFacts().damageIgnoresInvulnerability()) {
            return false;
        }
        var hittable = ImpactTiming.ticksUntilHittable(target.getNoDamageTicks(), target.getMaximumNoDamageTicks());
        return ImpactTiming.mustWait(hittable, choice.busyTicks());
    }

    /**
     * Distance between two bounding boxes: 0 when they touch or overlap ({@link ReachGeometry#gapBlocks}).
     *
     * @param first first box
     * @param second second box
     * @return shortest distance in blocks
     */
    static double gapBlocks(BoundingBox first, BoundingBox second) {
        return ReachGeometry.gapBlocks(first, second);
    }
}
