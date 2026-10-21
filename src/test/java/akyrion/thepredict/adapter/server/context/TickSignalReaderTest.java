package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.engine.IncomingProjectiles;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ChargedProjectiles;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickSignalReaderTest {
    private static final World WORLD = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[] {World.class}, (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> 1;
                case "equals" -> proxy == args[0];
                case "toString" -> "world";
                default -> throw new UnsupportedOperationException(method.getName());
            });
    private static final UUID MOB = new UUID(1, 1);
    private static final UUID TARGET = new UUID(2, 2);

    @Test
    void anAbsentMobIsZero() {
        var mob = new MobKey(UUID.randomUUID(), 1);
        var word = TickSignalReader.read(id -> Optional.empty(), new IncomingProjectiles(), 1, mob, UUID.randomUUID());
        assertEquals(0L, word);
    }

    /** SP-15: without a target the word has no {@code HAS_TARGET} and a full target health, so nothing to finish. */
    @Test
    void aMissingTargetLeavesNobodyToFinish() {
        var word = TickSignalReader.read(entities(living(LivingEntity.class, MOB, Map.of()), null),
                new IncomingProjectiles(), 1, new MobKey(MOB, 1), TARGET);
        assertTrue(TickSignals.bit(word, TickSignals.VALID));
        assertFalse(TickSignals.bit(word, TickSignals.HAS_TARGET));
        assertEquals(100, TickSignals.targetHealthPercent(word));
        assertEquals(100, TickSignals.mobHealthPercent(word));
    }

    /** SP-12: a loaded crossbow in a hand is a ready shot; a crossbow still being loaded is not. */
    @Test
    void aLoadedCrossbowIsAReadyShotAndALoadingOneIsNot() {
        var charged = EntityDoubles.component(ChargedProjectiles.class, Map.of("projectiles",
                List.of(new EntityDoubles.FakeItem(Material.ARROW))));
        var loaded = new EntityDoubles.FakeItem(Material.CROSSBOW).with(DataComponentTypes.CHARGED_PROJECTILES, charged);
        var ready = player(Map.of("getEquipment", equipment(loaded, new EntityDoubles.FakeItem(Material.AIR))));
        assertTrue(TickSignals.bit(read(ready), TickSignals.TARGET_SHOT_READY));
        var loading = new EntityDoubles.FakeItem(Material.CROSSBOW);
        var answers = new HashMap<String, Object>();
        answers.put("getEquipment", equipment(loading, new EntityDoubles.FakeItem(Material.AIR)));
        answers.put("hasActiveItem", true);
        answers.put("getActiveItem", loading);
        answers.put("getActiveItemUsedTime", 5);
        assertFalse(TickSignals.bit(read(player(answers)), TickSignals.TARGET_SHOT_READY));
    }

    /** SP-24: the v3 bits read the body of the target. */
    @Test
    void theVersionThreeBitsReadSprintAirAndFall() {
        var bare = equipment(new EntityDoubles.FakeItem(Material.AIR), new EntityDoubles.FakeItem(Material.AIR));
        var falling = read(player(Map.of("getEquipment", bare, "isSprinting", true, "isOnGround", false,
                "getFallDistance", 2.0f)));
        assertTrue(TickSignals.bit(falling, TickSignals.TARGET_SPRINTING));
        assertTrue(TickSignals.bit(falling, TickSignals.TARGET_AIRBORNE));
        assertTrue(TickSignals.bit(falling, TickSignals.TARGET_FALLING_FAST));
        var standing = read(player(Map.of("getEquipment", bare, "isOnGround", true)));
        assertFalse(TickSignals.bit(standing, TickSignals.TARGET_SPRINTING));
        assertFalse(TickSignals.bit(standing, TickSignals.TARGET_AIRBORNE));
        assertFalse(TickSignals.bit(standing, TickSignals.TARGET_FALLING_FAST));
        assertTrue(TickSignals.bit(standing, TickSignals.HAS_TARGET));
    }

    private static long read(LivingEntity target) {
        return TickSignalReader.read(entities(living(LivingEntity.class, MOB, Map.of()), target),
                new IncomingProjectiles(), 1, new MobKey(MOB, 1), TARGET);
    }

    private static Function<UUID, Optional<Entity>> entities(LivingEntity mob, LivingEntity target) {
        return id -> MOB.equals(id) ? Optional.of(mob) : TARGET.equals(id) ? Optional.ofNullable(target)
                : Optional.empty();
    }

    private static LivingEntity player(Map<String, Object> answers) {
        var all = new HashMap<>(answers);
        all.putIfAbsent("getX", 5.0);
        all.putIfAbsent("getAttackCooldown", 1.0f);
        return living(Player.class, TARGET, all);
    }

    private static LivingEntity living(Class<? extends LivingEntity> kind, UUID id, Map<String, Object> answers) {
        return (LivingEntity) Proxy.newProxyInstance(kind.getClassLoader(), new Class<?>[] {kind},
                (proxy, method, args) -> {
                    var name = method.getName();
                    if (answers.containsKey(name)) {
                        return answers.get(name);
                    }
                    return switch (name) {
                        case "getUniqueId" -> id;
                        case "getWorld" -> WORLD;
                        case "isValid" -> true;
                        case "isDead" -> false;
                        case "isOnGround" -> true;
                        case "getHealth" -> 20.0;
                        case "getMaximumNoDamageTicks" -> 20;
                        case "getWidth" -> 0.6;
                        case "hashCode" -> id.hashCode();
                        case "equals" -> proxy == args[0];
                        case "toString" -> kind.getSimpleName();
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static EntityEquipment equipment(ItemStack main, ItemStack off) {
        return (EntityEquipment) Proxy.newProxyInstance(EntityEquipment.class.getClassLoader(),
                new Class<?>[] {EntityEquipment.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getItemInMainHand" -> main;
                    case "getItemInOffHand" -> off;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0f;
        return null;
    }
}
