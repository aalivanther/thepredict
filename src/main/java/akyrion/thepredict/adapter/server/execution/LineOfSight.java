package akyrion.thepredict.adapter.server.execution;

import org.bukkit.entity.LivingEntity;

import java.util.UUID;

/**
 * How a cast reads the line of sight of a mob toward its target (M19.5, rule C12: at most one ray per mob and tick).
 * {@link #DIRECT} asks Paper every time; {@link SightCache} remembers the answer of the current tick.
 */
interface LineOfSight {
    /** Reads {@code LivingEntity#hasLineOfSight} on every call. */
    LineOfSight DIRECT = (mobId, caster, target) -> caster.hasLineOfSight(target);

    /**
     * Whether the caster sees the target now.
     *
     * @param mobId UUID of the caster, the key of a remembered answer
     * @param caster living caster
     * @param target current target, in the caster's world
     * @return whether a block line of sight exists
     */
    boolean sees(UUID mobId, LivingEntity caster, LivingEntity target);
}
