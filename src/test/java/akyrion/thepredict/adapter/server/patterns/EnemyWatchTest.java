package akyrion.thepredict.adapter.server.patterns;

import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.engine.PatternInbox;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyWatchTest {
    /** SP-11: a projectile a player shot is no melee strike, and a critical arrow is never a critical strike. */
    @Test
    void aPlayerProjectileIsNotAStrikeAndAnotherEntityHitsPlainly() {
        var player = player(new UUID(1, 1), new ArrayList<>());
        var charged = new ChargeRing.Shot(1.0, true, false);
        assertEquals(-1, EnemyWatch.hitAction(arrow(player), true, charged));
        assertEquals(EnemyAction.CRIT_STRIKE.ordinal(), EnemyWatch.hitAction(player, true, charged));
        assertEquals(EnemyAction.SPRINT_STRIKE.ordinal(), EnemyWatch.hitAction(player, false, charged));
        var zombie = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[] {Entity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> 3;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        assertEquals(EnemyAction.STRIKE.ordinal(), EnemyWatch.hitAction(zombie, true, charged));
    }

    /** SP-18: a swing of a player outside every encounter reads nothing; the right click is not ignore-cancelled. */
    @Test
    void aStrangerSwingReadsNoEquipmentAndTheInteractHandlerSeesCancelledAirClicks() throws Exception {
        var inbox = new PatternInbox();
        var watch = new EnemyWatch(inbox, () -> 3L);
        var reads = new ArrayList<String>();
        var stranger = player(new UUID(9, 9), reads);
        watch.onSwing(new PlayerArmSwingEvent(stranger, EquipmentSlot.HAND));
        assertFalse(reads.contains("getEquipment"), reads.toString());
        var participantReads = new ArrayList<String>();
        var participant = player(new UUID(1, 2), participantReads);
        inbox.admit(participant.getUniqueId());
        watch.onSwing(new PlayerArmSwingEvent(participant, EquipmentSlot.HAND));
        assertEquals(1, participantReads.stream().filter("getEquipment"::equals).count());
        var interact = EnemyWatch.class.getMethod("onInteract", PlayerInteractEvent.class);
        assertFalse(interact.getAnnotation(EventHandler.class).ignoreCancelled());
    }

    private static Player player(UUID id, java.util.List<String> reads) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    reads.add(method.getName());
                    return switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getEquipment" -> null;
                        case "hashCode" -> id.hashCode();
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }

    private static Projectile arrow(Player shooter) {
        return (Projectile) Proxy.newProxyInstance(Projectile.class.getClassLoader(), new Class<?>[] {Projectile.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getShooter" -> shooter;
                    case "hashCode" -> 2;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void monitorIgnoresCancelledAndAStrangerNeverReachesTheRing() throws Exception {
        var method = EnemyWatch.class.getMethod("onDamage", EntityDamageByEntityEvent.class);
        var handler = method.getAnnotation(EventHandler.class);
        assertEquals(EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
        var inbox = new PatternInbox();
        var enemy = new UUID(1, 2);
        inbox.admit(enemy);
        var watch = new EnemyWatch(inbox, () -> 3L);
        var stranger = new UUID(9, 9);
        for (var index = 0; index < 1000; index++) {
            assertFalse(watch.consider(new UUID(index, 3), true, EnemyAction.STRIKE.ordinal(), index));
        }
        assertEquals(0, inbox.size(stranger));
        assertFalse(watch.consider(enemy, false, EnemyAction.STRIKE.ordinal(), 1));
        assertEquals(0, inbox.size(enemy));
        for (var index = 0; index < 70; index++) {
            assertTrue(watch.consider(enemy, true, EnemyAction.STRIKE.ordinal(), index));
        }
        assertEquals(64, inbox.size(enemy));
        assertEquals(6, inbox.dropped(enemy));
        assertEquals(EnemyAction.CRIT_STRIKE, EnemyWatch.classifyHit(true, true, 1.0, false));
        assertEquals(EnemyAction.SPRINT_STRIKE, EnemyWatch.classifyHit(false, true, 1.0, false));
        assertEquals(EnemyAction.STRIKE, EnemyWatch.classifyHit(false, false, 1.0, false));
        assertEquals(EnemyAction.STRIKE_EARLY, EnemyWatch.classifyHit(false, true, 0.2, false));
        assertEquals(EnemyAction.STRIKE, EnemyWatch.classifyHit(true, true, 1.0, true));
        assertEquals(EnemyAction.STRIKE_EARLY, EnemyWatch.classifyHit(true, true, 0.2, true));
    }
}
