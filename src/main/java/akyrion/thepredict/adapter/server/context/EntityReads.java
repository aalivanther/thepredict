package akyrion.thepredict.adapter.server.context;

import org.bukkit.entity.LivingEntity;

import java.util.OptionalDouble;

/**
 * Reads of Paper state that need attributes, items, data components, the pathfinder or the combat tracker, kept
 * apart so {@link CombatCapture} is tested with doubles (rule P4). The live implementation is
 * {@link PaperEntityReads}. Server thread only.
 */
public interface EntityReads {
    /** Path the navigator already follows, read without computing a new one. */
    enum CurrentPath {
        /** No path in progress. */
        NONE,
        /** The path in progress reaches its final point. */
        REACHES,
        /** The path in progress falls short of its final point. */
        FALLS_SHORT
    }

    /**
     * Reads the follow range, which MythicMobs sets from {@code Options.FollowRange}.
     *
     * @param mob member mob
     * @return value of {@code FOLLOW_RANGE} in blocks, {@code 0} when the attribute is missing
     */
    double followRangeBlocks(LivingEntity mob);

    /**
     * Reads the maximum health.
     *
     * @param entity mob or player
     * @return value of {@code MAX_HEALTH}, {@code NaN} when the attribute is missing
     */
    double maxHealth(LivingEntity entity);

    /**
     * Reads everything the cues and bands of one participant need (M07.3, M17.4); the fields that only exist for
     * players take a neutral value on any other entity.
     *
     * @param participant perceived participant, a player or any other living entity
     * @return the raw reading
     */
    ParticipantReading participant(LivingEntity participant);

    /**
     * Reads how far a target can hit the mob (M17.4, DT-37), with the way that reach is compared ({@link TargetReach}):
     * for a player, {@code ATTACK_RANGE.maxReach()} of the main hand item when it has that component, otherwise
     * {@code ENTITY_INTERACTION_RANGE}, compared with the distance as in 0.0.1; for a rival bound to ThePredict, the
     * reach of its profile compared with the gap between the boxes; for any other {@code Mob}, the vanilla melee check
     * with the {@code ATTACK_RANGE} of its weapon or the default reach; unknown for a mob with a native ranged attack,
     * a ranged weapon in hand, a MythicMobs mob without a profile of ThePredict, and anything that is not a mob.
     *
     * @param target target of the mob
     * @return the reach, {@link TargetReach#UNKNOWN} when nothing is known
     */
    TargetReach targetReach(LivingEntity target);

    /**
     * Reads the blocking angle of a shield the target is raising or blocking with (M05.3, M17.4).
     *
     * @param target target of the mob, a player or any other living entity
     * @return angle in degrees, empty when no item with {@code BLOCKS_ATTACKS} is raised
     */
    OptionalDouble raisedShieldAngleDegrees(LivingEntity target);

    /**
     * Reads the path the mob's navigator already follows.
     *
     * @param mob member mob
     * @return state of the path in progress
     */
    CurrentPath currentPath(LivingEntity mob);

    /**
     * Computes a new path to the target synchronously; only called with the engine's permission (M07.4).
     *
     * @param mob member mob
     * @param target its target
     * @return whether the computed path reaches its final point
     */
    boolean findPathReaches(LivingEntity mob, LivingEntity target);

    /**
     * Reads {@code CombatTracker#getCombatDuration()}, in ticks (static evidence in the M07 document).
     *
     * @param entity member mob
     * @return combat duration in ticks
     */
    int combatDurationTicks(LivingEntity entity);

    /**
     * Reads {@code CombatTracker#isInCombat()}.
     *
     * @param entity mob or player
     * @return whether vanilla considers the entity in combat
     */
    boolean trackerInCombat(LivingEntity entity);
}
