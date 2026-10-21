package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.engine.IncomingProjectiles;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Packs one tick reading from the mob and its expected target (M20.2, v3 bits of M26). The common case, a lowered
 * hand, does not read the active item. Positions come from the components, never from a location or a velocity
 * vector. Without a valid target the word says so ({@code HAS_TARGET} clear) and carries a full target health, so a
 * finishing reaction cannot arm against nobody (SP-15).
 */
public final class TickSignalReader {
    /** Target health packed when there is no target: nobody to finish. */
    static final int NO_TARGET_HEALTH = 100;
    /** Fall distance that counts as falling fast (bit {@code TARGET_FALLING_FAST}). */
    static final double FAST_FALL_BLOCKS = 1.5;

    private TickSignalReader() {
    }

    /**
     * Reads one mob. Zero when the mob is gone.
     *
     * @param entities loaded entity of a UUID
     * @param shots shots still in flight
     * @param tick current server tick
     * @param mob session key; only this mob is read
     * @param expectedTarget target the session expects
     * @return the packed word
     */
    public static long read(Function<UUID, Optional<Entity>> entities, IncomingProjectiles shots, long tick,
                            MobKey mob, UUID expectedTarget) {
        Objects.requireNonNull(entities, "entities");
        Objects.requireNonNull(shots, "shots");
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(expectedTarget, "expectedTarget");
        var found = entities.apply(mob.entityId()).orElse(null);
        if (!(found instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
            return 0L;
        }
        var flags = mobFlags(living);
        var target = entities.apply(expectedTarget).orElse(null);
        if (!(target instanceof LivingEntity live) || !live.isValid() || live.isDead()
                || live.getWorld() != living.getWorld()) {
            return TickSignals.pack(flags, 0, 0, 0, 0, health(living), NO_TARGET_HEALTH);
        }
        return withTarget(flags, living, live, shots, tick, expectedTarget);
    }

    private static long mobFlags(LivingEntity living) {
        var flags = 1L << TickSignals.VALID;
        if (living.isOnGround()) {
            flags |= 1L << TickSignals.MOB_ON_GROUND;
        }
        if (ImpactWait.of(living) > 0) {
            flags |= 1L << TickSignals.MOB_STAGGERED;
        }
        return flags;
    }

    private static long withTarget(long flags, LivingEntity living, LivingEntity live, IncomingProjectiles shots,
                                   long tick, UUID expectedTarget) {
        flags |= 1L << TickSignals.HAS_TARGET;
        if (living instanceof Mob body && body.getTarget() != null
                && !expectedTarget.equals(body.getTarget().getUniqueId())) {
            flags |= 1L << TickSignals.TARGET_MISMATCH;
        }
        var dx = living.getX() - live.getX();
        var dz = living.getZ() - live.getZ();
        var gap = StrictMath.hypot(dx, dz) - (living.getWidth() + live.getWidth()) / 2.0;
        if (StrictMath.abs(living.getY() - live.getY()) > 1.5) {
            flags |= 1L << TickSignals.VERTICAL_GAP;
        }
        var lookX = TickSignals.lookX(live.getYaw());
        var lookZ = TickSignals.lookZ(live.getYaw());
        var octant = TickSignals.octantOf(lookX, lookZ, dx, dz);
        var facing = TickSignals.octantOf(TickSignals.lookX(living.getYaw()), TickSignals.lookZ(living.getYaw()),
                -dx, -dz);
        if (TickSignals.leftOf(lookX, lookZ, dx, dz)) {
            flags |= 1L << TickSignals.MOB_LEFT_OF_TARGET;
        }
        flags |= posture(live) | motion(live);
        var toward = StrictMath.toDegrees(StrictMath.atan2(-dx, dz));
        if (shots.recent(expectedTarget, tick, StrictMath.hypot(dx, dz), toward)) {
            flags |= 1L << TickSignals.INCOMING_PROJECTILE;
        }
        return TickSignals.pack(flags, TickSignals.quartersOf(gap), octant, facing,
                ImpactWait.of(live), health(living), health(live));
    }

    /** Health percent with the {@code MAX_HEALTH} attribute; a double without the attribute uses its health. */
    private static int health(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        var max = attribute == null ? entity.getHealth() : attribute.getValue();
        return TickSignals.percent(entity.getHealth(), entity.getAbsorptionAmount(), max);
    }

    /**
     * Blocking, recharging, a ready shot and a consumable in use. A drawn bow is ready after 20 ticks of use; a
     * crossbow is ready when it holds charged projectiles in either hand, not while it is being loaded (SP-12).
     * A consumable is anything with the {@code CONSUMABLE} component, as the capture reads it (SP-23).
     */
    private static long posture(LivingEntity target) {
        var flags = 0L;
        var shield = Blocking.of(target);
        if (shield.blocking()) {
            flags |= 1L << TickSignals.TARGET_BLOCKING;
        }
        if (shield.raised()) {
            flags |= 1L << TickSignals.TARGET_RAISING;
        }
        if (target instanceof Player player && player.getAttackCooldown() < 0.9f) {
            flags |= 1L << TickSignals.TARGET_ATTACK_RECHARGING;
        }
        if (crossbowLoaded(target)) {
            flags |= 1L << TickSignals.TARGET_SHOT_READY;
        }
        if (!target.hasActiveItem()) {
            return flags;
        }
        var active = target.getActiveItem();
        if (active.getType() == Material.BOW && target.getActiveItemUsedTime() >= 20) {
            flags |= 1L << TickSignals.TARGET_SHOT_READY;
        }
        if (active.hasData(DataComponentTypes.CONSUMABLE)) {
            flags |= 1L << TickSignals.TARGET_CONSUMING;
        }
        return flags;
    }

    /** The v3 bits of M26 that read the body: airborne, sprinting and a fast fall (SP-24). */
    private static long motion(LivingEntity target) {
        var flags = 0L;
        if (!target.isOnGround()) {
            flags |= 1L << TickSignals.TARGET_AIRBORNE;
        }
        if (target instanceof Player player && player.isSprinting()) {
            flags |= 1L << TickSignals.TARGET_SPRINTING;
        }
        if (target.getFallDistance() > FAST_FALL_BLOCKS) {
            flags |= 1L << TickSignals.TARGET_FALLING_FAST;
        }
        return flags;
    }

    private static boolean crossbowLoaded(LivingEntity target) {
        var equipment = target.getEquipment();
        if (equipment == null) {
            return false;
        }
        return charged(equipment.getItemInMainHand()) || charged(equipment.getItemInOffHand());
    }

    private static boolean charged(ItemStack item) {
        if (item == null || item.getType() != Material.CROSSBOW) {
            return false;
        }
        var charged = item.getData(DataComponentTypes.CHARGED_PROJECTILES);
        return charged != null && !charged.projectiles().isEmpty();
    }

    /** Ticks until a living entity can be hurt, shared so the reader stays free of the formula. */
    static final class ImpactWait {
        private ImpactWait() {
        }

        static int of(LivingEntity entity) {
            return akyrion.thepredict.domain.ImpactTiming.ticksUntilHittable(entity.getNoDamageTicks(),
                    entity.getMaximumNoDamageTicks());
        }
    }
}
