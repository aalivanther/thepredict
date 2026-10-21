package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.engine.Encounter;
import akyrion.thepredict.engine.GoldenWindows;
import akyrion.thepredict.golden.FactsText;
import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import com.destroystokyo.paper.event.player.PlayerReadyArrowEvent;
import io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import io.papermc.paper.event.entity.EntityLungeEvent;
import io.papermc.paper.event.player.PlayerItemGroupCooldownEvent;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Event sequences of the golden references of M17.0 through the real {@link CombatOutcomeListener},
 * {@link CombatContextListener} and {@link OutcomeListener} over the real engine (rule P4): hits, blocked hits,
 * projectiles, misses, potions, clouds, deaths and the context events, with a player participant, a player stranger
 * and a vanilla zombie as victim, shooter or actor. After each event the reference writes what reached the engine:
 * new history entries, the pending mark, the counters of {@code ContextEvents} and of the threat, every
 * {@code addThreat}, the open outcome window of the member ({@code dealt}, {@code taken}, deaths) and the work moved to
 * the next tick. The engine entry points are final classes, so their effects stand for their calls.
 */
public final class ListenerGoldens {
    private static final UUID ZOMBIE = new UUID(0x2AL, 1);
    private static final DamageSource SOURCE = (DamageSource) Proxy.newProxyInstance(
            DamageSource.class.getClassLoader(), new Class<?>[] {DamageSource.class}, (proxy, method, arguments) -> null);

    private ListenerGoldens() {
    }

    /**
     * The golden text of every sequence, by file name.
     *
     * @return file name to text, in a fixed order
     */
    public static Map<String, String> all() {
        var files = new LinkedHashMap<String, String>();
        files.put("combat-outcome.txt", outcome());
        files.put("combat-context.txt", context());
        files.put("outcome-windows.txt", windows());
        return files;
    }

    @SuppressWarnings("removal")
    private static EntityDamageByEntityEvent hit(Entity damager, Entity victim) {
        return new EntityDamageByEntityEvent(damager, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, SOURCE, 3.0);
    }

    @SuppressWarnings("removal")
    private static EntityDamageEvent damage(Entity victim, double amount) {
        return new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, SOURCE, amount);
    }

    private static String outcome() {
        var run = new Run("combat outcome listener");
        var listener = run.fixture.outcome;
        run.step("hit member -> participant", () -> listener.onDamage(hit(run.member, run.player(1, false))));
        run.step("hit member -> blocking participant", () -> listener.onDamage(hit(run.member, run.player(1, true))));
        run.fixture.tick = 20;
        run.step("arrow of participant 2 -> member targeting participant 1",
                () -> listener.onDamage(hit(arrow(run.player(2, false)), run.member)));
        run.step("arrow of target -> member", () -> listener.onDamage(hit(arrow(run.player(1, false)), run.member)));
        run.step("missed arrow of participant", () -> listener.onProjectileHit(new ProjectileHitEvent(
                arrow(run.player(1, false)), null, null, null)));
        run.step("missed snowball of participant", () -> listener.onProjectileHit(new ProjectileHitEvent(
                snowball(run.player(1, false)), null, null, null)));
        run.step("hit member -> zombie", () -> listener.onDamage(hit(run.member, run.zombie)));
        run.step("arrow of zombie -> member", () -> listener.onDamage(hit(arrow(run.zombie), run.member)));
        run.step("missed arrow of zombie", () -> listener.onProjectileHit(new ProjectileHitEvent(arrow(run.zombie),
                null, null, null)));
        run.step("hit zombie -> participant", () -> listener.onDamage(hit(run.zombie, run.player(1, false))));
        run.step("hit stranger mob -> participant", () -> listener.onDamage(hit(run.mob(9), run.player(1, true))));
        run.step("hit member -> stranger player", () -> listener.onDamage(hit(run.member, run.player(3, true))));
        run.step("potion of participant without effects", () -> listener.onPotionSplash(new PotionSplashEvent(
                potion(run.player(1, false)), null, null, null, Map.of(run.player(2, false), 1.0, run.zombie, 1.0))));
        run.step("potion of zombie", () -> listener.onPotionSplash(new PotionSplashEvent(potion(run.zombie), null,
                null, null, Map.of(run.player(1, false), 1.0))));
        run.step("cloud of participant without effects", () -> listener.onCloudApply(new AreaEffectCloudApplyEvent(
                cloud(run.player(1, false)), List.of(run.player(2, false), run.zombie))));
        run.step("cloud of zombie", () -> listener.onCloudApply(new AreaEffectCloudApplyEvent(cloud(run.zombie),
                List.of(run.player(1, false)))));
        run.step("path of member", () -> listener.onPathfind(new EntityPathfindEvent(run.member,
                new Location(run.fixture.world, 0, 64, 0), null)));
        run.step("path of zombie", () -> listener.onPathfind(new EntityPathfindEvent(run.zombie,
                new Location(run.fixture.world, 0, 64, 0), null)));
        run.fixture.tick = 40;
        run.fixture.primaryThread = false;
        run.step("blocked hit off the server thread", () -> listener.onDamage(hit(run.member, run.player(1, true))));
        run.fixture.primaryThread = true;
        run.step("next tick", run.fixture::runNextTick);
        return run.text();
    }

    private static String context() {
        var run = new Run("combat context listener");
        var listener = run.fixture.context;
        var participant = run.player(1, false);
        var stranger = run.player(3, false);
        run.step("ready arrow", () -> listener.onReadyArrow(new PlayerReadyArrowEvent(participant, null, null)));
        run.step("stop using item", () -> listener.onStopUsingItem(new PlayerStopUsingItemEvent(participant, null, 5)));
        run.fixture.tick = 20;
        run.step("load crossbow", () -> listener.onLoadCrossbow(new EntityLoadCrossbowEvent(participant, null,
                EquipmentSlot.HAND)));
        run.step("zombie loads crossbow", () -> listener.onLoadCrossbow(new EntityLoadCrossbowEvent(run.zombie, null,
                EquipmentSlot.HAND)));
        run.step("shield disabled", () -> listener.onShieldDisable(new PlayerShieldDisableEvent(participant,
                run.member, 100)));
        run.step("totem", () -> listener.onResurrect(new EntityResurrectEvent(participant, EquipmentSlot.HAND)));
        run.step("zombie totem", () -> listener.onResurrect(new EntityResurrectEvent(run.zombie, EquipmentSlot.HAND)));
        run.fixture.tick = 40;
        run.step("equipment", () -> listener.onEquipmentChanged(new EntityEquipmentChangedEvent(participant,
                Map.of())));
        run.step("zombie equipment", () -> listener.onEquipmentChanged(new EntityEquipmentChangedEvent(run.zombie,
                Map.of())));
        run.step("group cooldown", () -> listener.onGroupCooldown(new PlayerItemGroupCooldownEvent(participant,
                NamespacedKey.minecraft("ender_pearl"), 20)));
        run.step("pearl teleport", () -> listener.onTeleport(new PlayerTeleportEvent(participant,
                new Location(run.fixture.world, 0, 64, 0), new Location(run.fixture.world, 8, 64, 0),
                PlayerTeleportEvent.TeleportCause.ENDER_PEARL)));
        run.step("command teleport", () -> listener.onTeleport(new PlayerTeleportEvent(participant,
                new Location(run.fixture.world, 0, 64, 0), new Location(run.fixture.world, 8, 64, 0),
                PlayerTeleportEvent.TeleportCause.COMMAND)));
        run.fixture.tick = 60;
        run.step("stranger attacks member", () -> listener.onPreAttack(new PrePlayerAttackEntityEvent(stranger,
                run.member, true)));
        run.step("stranger attacks zombie", () -> listener.onPreAttack(new PrePlayerAttackEntityEvent(stranger,
                run.zombie, true)));
        run.step("smash attempt", () -> listener.onSmashAttempt(new EntityAttemptSmashAttackEvent(participant,
                run.member, null, true)));
        run.step("zombie smash attempt", () -> listener.onSmashAttempt(new EntityAttemptSmashAttackEvent(run.zombie,
                participant, null, true)));
        run.step("lunge", () -> listener.onLunge(new EntityLungeEvent(participant, 1)));
        run.step("zombie lunge", () -> listener.onLunge(new EntityLungeEvent(run.zombie, 1)));
        run.fixture.tick = 65;
        run.step("stranger consumes", () -> listener.onConsume(new PlayerItemConsumeEvent(stranger, null,
                EquipmentSlot.HAND)));
        run.step("stranger shield disabled", () -> listener.onShieldDisable(new PlayerShieldDisableEvent(stranger,
                run.member, 100)));
        run.fixture.primaryThread = false;
        run.step("shield disabled off the server thread", () -> listener.onShieldDisable(new PlayerShieldDisableEvent(
                participant, run.member, 100)));
        run.fixture.primaryThread = true;
        run.fixture.tick = 80;
        run.step("next tick", run.fixture::runNextTick);
        return run.text();
    }

    private static String windows() {
        var run = new Run("outcome listener");
        var listener = new OutcomeListener(run.fixture.outcomes, run.fixture.events, run.fixture.thread,
                () -> run.fixture.tick);
        run.step("member takes damage", () -> listener.onDamage(damage(run.member, 4.0)));
        run.step("zombie takes damage", () -> listener.onDamage(damage(run.zombie, 4.0)));
        run.step("participant takes damage", () -> listener.onDamage(damage(run.player(1, false), 4.0)));
        run.step("zombie dies", () -> listener.onDeath(new EntityDeathEvent(run.zombie, SOURCE, List.of())));
        run.step("stranger dies", () -> listener.onDeath(new EntityDeathEvent(run.player(3, false), SOURCE,
                List.of())));
        run.step("participant 2 dies", () -> listener.onDeath(new EntityDeathEvent(run.player(2, false), SOURCE,
                List.of())));
        run.fixture.tick = 30;
        run.step("target dies", () -> listener.onDeath(new EntityDeathEvent(run.player(1, false), SOURCE, List.of())));
        run.step("member dies", () -> listener.onDeath(new EntityDeathEvent(run.member, SOURCE, List.of())));
        return run.text();
    }

    /** One sequence: an encounter of member 1 against participants 1 and 2, with a window on the member. */
    private static final class Run {
        final ContextListenerFixtures fixture = new ContextListenerFixtures();
        final Encounter encounter;
        final Mob member;
        final LivingEntity zombie;
        final StringBuilder text = new StringBuilder();
        int seenEvents;
        int seenThreats;

        Run(String title) {
            text.append("# M17.0 golden: ").append(title).append('\n');
            encounter = fixture.encounter(1, ContextListenerFixtures.playerId(1));
            fixture.encounters.addParticipant(encounter.id(), ContextListenerFixtures.playerId(2));
            member = mob(1);
            zombie = zombie();
            GoldenWindows.open(fixture.outcomes, fixture.sessions, LifecycleFixtures.id(1),
                    ContextListenerFixtures.playerId(1), 0);
            text.append("setup participants=").append(encounter.participants().size()).append(' ')
                    .append(GoldenWindows.state(fixture.sessions, LifecycleFixtures.id(1), fixture.tick)).append('\n');
        }

        void step(String label, Runnable event) {
            event.run();
            text.append("- ").append(label).append('\n');
            var history = encounter.recentEvents();
            for (var index = seenEvents; index < history.size(); index++) {
                text.append("  ").append(FactsText.event(history.get(index))).append('\n');
            }
            seenEvents = history.size();
            for (var index = seenThreats; index < fixture.threats.size(); index++) {
                text.append("  addThreat ").append(fixture.threats.get(index)).append('\n');
            }
            seenThreats = fixture.threats.size();
            var metrics = fixture.events.metrics();
            var threat = fixture.threat.metrics();
            text.append("  pending=").append(encounter.contextPending()).append(" recorded=")
                    .append(metrics.recordedEvents()).append(" repeated=").append(metrics.repeatedEvents())
                    .append(" marks=").append(metrics.pendingMarks()).append(" pathfinds=")
                    .append(metrics.memberPathfinds()).append(" nextTick=").append(fixture.nextTick.size()).append('\n');
            text.append(String.format(Locale.ROOT, "  threat assists=%d blocked=%d harass=%d limited=%d added=%.6f%n",
                    threat.assists(), threat.blockedHits(), threat.rangedHarass(), threat.limited(),
                    threat.pointsAdded()));
            text.append("  ").append(GoldenWindows.state(fixture.sessions, LifecycleFixtures.id(1), fixture.tick))
                    .append('\n');
        }

        String text() {
            return text.toString();
        }

        Player player(int value, boolean blocking) {
            var id = ContextListenerFixtures.playerId(value);
            return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getWorld" -> fixture.world;
                        case "isBlocking" -> blocking;
                        case "getAttribute" -> attribute(20.0);
                        case "hashCode" -> id.hashCode();
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "player " + value;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        Mob mob(int value) {
            var id = LifecycleFixtures.id(value);
            var target = player(1, false);
            return (Mob) Proxy.newProxyInstance(Mob.class.getClassLoader(), new Class<?>[] {Mob.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getWorld" -> fixture.world;
                        case "getTarget" -> target;
                        case "getAttribute" -> attribute(40.0);
                        case "hashCode" -> id.hashCode();
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "mob " + value;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        LivingEntity zombie() {
            return (LivingEntity) Proxy.newProxyInstance(Zombie.class.getClassLoader(), new Class<?>[] {Zombie.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getUniqueId" -> ZOMBIE;
                        case "getWorld" -> fixture.world;
                        case "getAttribute" -> attribute(20.0);
                        case "hashCode" -> ZOMBIE.hashCode();
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "zombie";
                        default -> defaultValue(method.getReturnType());
                    });
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private static AttributeInstance attribute(double value) {
        return (AttributeInstance) Proxy.newProxyInstance(AttributeInstance.class.getClassLoader(),
                new Class<?>[] {AttributeInstance.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getValue" -> value;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Projectile arrow(Object shooter) {
        return (Projectile) Proxy.newProxyInstance(AbstractArrow.class.getClassLoader(),
                new Class<?>[] {AbstractArrow.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getShooter" -> shooter;
                    case "getUniqueId" -> new UUID(0xA7L, 1);
                    case "hashCode" -> 1;
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Projectile snowball(Object shooter) {
        return (Projectile) Proxy.newProxyInstance(Projectile.class.getClassLoader(), new Class<?>[] {Projectile.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getShooter" -> shooter;
                    case "getUniqueId" -> new UUID(0xA7L, 2);
                    case "hashCode" -> 2;
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static ThrownPotion potion(Object shooter) {
        return (ThrownPotion) Proxy.newProxyInstance(ThrownPotion.class.getClassLoader(),
                new Class<?>[] {ThrownPotion.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getShooter" -> shooter;
                    case "getEffects" -> List.of();
                    case "hashCode" -> 3;
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static AreaEffectCloud cloud(Object source) {
        return (AreaEffectCloud) Proxy.newProxyInstance(AreaEffectCloud.class.getClassLoader(),
                new Class<?>[] {AreaEffectCloud.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getSource" -> source;
                    case "getCustomEffects" -> List.of();
                    case "hashCode" -> 4;
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

}
