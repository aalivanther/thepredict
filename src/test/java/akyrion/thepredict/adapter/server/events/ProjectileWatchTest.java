package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.engine.IncomingProjectiles;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileWatchTest {
    @Test
    void theListenerIsMonitorAndIgnoresCancelledShots() throws Exception {
        assertThrows(NullPointerException.class, () -> new ProjectileWatch(null, () -> 0));
        var method = ProjectileWatch.class.getMethod("onLaunch", org.bukkit.event.entity.ProjectileLaunchEvent.class);
        var handler = method.getAnnotation(org.bukkit.event.EventHandler.class);
        assertEquals(org.bukkit.event.EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
        assertEquals(0, new IncomingProjectiles().size());
    }

    /**
     * SP-06: an archer at (10, 0) shoots west at a mob at (0, 0). The projectile's own yaw is the mirrored
     * {@code atan2(vx, vz)} of vanilla; the stored aim must be the entity yaw of the velocity, which the signal
     * reader compares with the yaw from the shooter towards the mob.
     */
    @Test
    void theAimIsTheYawOfTheVelocityNotTheProjectileRotation() {
        var shooterId = new UUID(5, 6);
        var shots = new IncomingProjectiles();
        var watch = new ProjectileWatch(shots, () -> 100L);
        var velocity = new Vector(-3.0, 0.0, 0.0);
        watch.onLaunch(new ProjectileLaunchEvent(projectile(shooterId, velocity)));
        var toward = StrictMath.toDegrees(StrictMath.atan2(-(0.0 - 10.0), 0.0 - 0.0));
        assertTrue(shots.recent(shooterId, 101L, 10.0, toward));
        assertFalse(shots.recent(shooterId, 101L, 10.0, toward + 180.0));
        assertEquals(90.0f, ProjectileWatch.aimYaw(-3.0, 0.0), 1.0e-4f);
        assertEquals(0.0f, ProjectileWatch.aimYaw(0.0, 3.0), 1.0e-4f);
        assertEquals(-90.0f, ProjectileWatch.aimYaw(3.0, 0.0), 1.0e-4f);
    }

    private static Projectile projectile(UUID shooterId, Vector velocity) {
        var shooter = (Entity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> shooterId;
                    case "hashCode" -> shooterId.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var mirrored = (float) StrictMath.toDegrees(StrictMath.atan2(velocity.getX(), velocity.getZ()));
        return (Projectile) Proxy.newProxyInstance(Projectile.class.getClassLoader(), new Class<?>[] {Projectile.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getShooter" -> shooter;
                    case "getVelocity" -> velocity.clone();
                    case "getYaw" -> mirrored;
                    case "getUniqueId" -> new UUID(7, 8);
                    case "hashCode" -> 78;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
