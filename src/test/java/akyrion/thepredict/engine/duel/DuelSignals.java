package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.TickSignals;

import java.util.UUID;

/**
 * The per-tick reading of the simulator (M20.2, E-8), packed in the format of {@link TickSignals} as
 * {@code TickSignalReader} packs it on a server: {@code VALID} while the mob lives, on the ground unless a leap flies it,
 * staggered inside its own hurt window; with the expected target alive, {@code HAS_TARGET}, the gap between the boxes in
 * quarter-blocks, the octant of the mob around the target's look and the facing error of the mob, the side, the
 * ticks until the target can be hit ({@code h}) and both health percents with absorption. What a zombie never shows
 * stays clear: no target mismatch (the target of a duelist is always its rival), no vertical gap on the flat floor,
 * no shield, bow, food or attack charge, and no projectile in flight (the bolt is a hit, not an arrow); the push fields
 * stay {@code UNKNOWN}, as without a zone. Look vectors are the bodies' headings; a body that never turned looks at
 * its rival, as a mob looks at its target.
 */
final class DuelSignals {
    private DuelSignals() {
    }

    /**
     * Reads one mob.
     *
     * @param world the world
     * @param mob the mob
     * @param expectedTarget the target its session expects
     * @param tick current tick
     * @return the packed word, 0 when the mob is dead
     */
    static long read(DuelWorld world, DuelWorld.Body mob, UUID expectedTarget, long tick) {
        if (mob.health <= 0) {
            return 0L;
        }
        var flags = 1L << TickSignals.VALID;
        if (mob.flightTicks <= 0) {
            flags |= 1L << TickSignals.MOB_ON_GROUND;
        }
        if (hittable(mob, tick) > 0) {
            flags |= 1L << TickSignals.MOB_STAGGERED;
        }
        var target = world.rival(mob);
        if (!target.id.equals(expectedTarget) || target.health <= 0) {
            return TickSignals.pack(flags, 0, 0, 0, 0, percent(mob), 0);
        }
        return withTarget(flags | 1L << TickSignals.HAS_TARGET, mob, target, tick);
    }

    private static long withTarget(long flags, DuelWorld.Body mob, DuelWorld.Body target, long tick) {
        var dx = mob.x - target.x;
        var dz = mob.z - target.z;
        var gap = Math.hypot(dx, dz) - DuelWorld.WIDTH;
        var targetYaw = heading(target, dx, dz);
        var mobYaw = heading(mob, -dx, -dz);
        var lookX = Math.cos(targetYaw);
        var lookZ = Math.sin(targetYaw);
        var octant = TickSignals.octantOf(lookX, lookZ, dx, dz);
        var facing = TickSignals.octantOf(Math.cos(mobYaw), Math.sin(mobYaw), -dx, -dz);
        var side = TickSignals.leftOf(lookX, lookZ, dx, dz) ? 1L << TickSignals.MOB_LEFT_OF_TARGET : 0L;
        return TickSignals.pack(flags | side, TickSignals.quartersOf(gap), octant, facing, hittable(target, tick),
                percent(mob), percent(target));
    }

    /** The heading of a body, or the direction to its rival {@code (towardX, towardZ)} when it never turned. */
    private static double heading(DuelWorld.Body body, double towardX, double towardZ) {
        return Double.isNaN(body.yaw) ? Math.atan2(towardZ, towardX) : body.yaw;
    }

    /**
     * {@code ImpactTiming.ticksUntilHittable}: the ticks left of the window of 10, 0 when it can be hit. A body never
     * hit keeps {@code Long.MIN_VALUE}, which a subtraction would turn into a full window.
     */
    static int hittable(DuelWorld.Body body, long tick) {
        if (body.invulnerableUntil <= tick) {
            return 0;
        }
        return (int) Math.min(DuelWorld.INVULNERABLE_TICKS, body.invulnerableUntil - tick);
    }

    private static int percent(DuelWorld.Body body) {
        return TickSignals.percent(Math.max(0, body.health), body.absorption, body.maxHealth);
    }
}
