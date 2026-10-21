package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.engine.IncomingProjectiles;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Remembers a launch so the next tick readings can see it (M20.2). Monitor and ignore-cancelled: a shot the server
 * already rejected is not a threat. The event is rare and does no work per tick. The aim is the yaw of the velocity
 * in the entity convention, {@code atan2(-vx, vz)}: vanilla {@code Projectile.shoot} stores the mirrored
 * {@code atan2(vx, vz)} in the projectile's own rotation (javap of the patched server), so {@code getYaw()} cannot
 * be compared with the yaw from the shooter towards the mob (SP-06).
 */
public final class ProjectileWatch implements Listener {
    private final IncomingProjectiles shots;
    private final LongSupplier tick;

    /**
     * Creates the listener.
     *
     * @param shots ring the signal reader asks
     * @param tick current server tick
     */
    public ProjectileWatch(IncomingProjectiles shots, LongSupplier tick) {
        this.shots = Objects.requireNonNull(shots, "shots");
        this.tick = Objects.requireNonNull(tick, "tick");
    }

    /**
     * Stores the shooter, the tick, the aim and the speed.
     *
     * @param event a projectile that left its shooter
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        var projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Entity shooter)) {
            return;
        }
        var velocity = projectile.getVelocity();
        shots.launched(shooter.getUniqueId(), tick.getAsLong(), aimYaw(velocity.getX(), velocity.getZ()),
                velocity.length());
    }

    /**
     * The yaw of a velocity in the entity convention, the one {@code TickSignalReader} compares against.
     *
     * @param velocityX blocks per tick along X
     * @param velocityZ blocks per tick along Z
     * @return degrees, {@code 0} towards +Z and {@code 90} towards -X
     */
    public static float aimYaw(double velocityX, double velocityZ) {
        return (float) StrictMath.toDegrees(StrictMath.atan2(-velocityX, velocityZ));
    }
}
