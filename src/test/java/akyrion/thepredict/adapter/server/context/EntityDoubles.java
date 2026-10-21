package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.binding.BoundProfile;
import akyrion.thepredict.domain.ParticipantRules;
import io.papermc.paper.datacomponent.DataComponentType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Doubles of living entities that are not players, and of the native identities the participant policy reads (M17):
 * a proxy of any entity interface ({@code Zombie}, {@code Skeleton}, {@code Cow}, {@code ArmorStand}…) with a type, a
 * spawn category, scoreboard tags and a position, and a {@link ParticipantLookup} over maps. Every read of an entity is
 * written to {@link FakeEntity#reads}, so a test can prove that a default policy reads nothing of it.
 */
final class EntityDoubles {
    private EntityDoubles() {
    }

    /** A living entity that is not a player. */
    static final class FakeEntity {
        final UUID id;
        final List<String> reads = new ArrayList<>();
        final Set<String> tags = new HashSet<>();
        CaptureDoubles.FakeWorld world;
        EntityType type;
        SpawnCategory category;
        double x;
        double z;
        boolean dead;
        boolean valid = true;
        boolean recordReads = true;
        Vector velocity = new Vector();
        ItemStack active;
        int usedTicks;
        ItemStack mainHand = new FakeItem(Material.AIR);
        ItemStack offHand = new FakeItem(Material.AIR);
        Entity vehicle;
        BoundingBox box;
        float fallDistance;
        boolean gliding;
        final LivingEntity entity;

        FakeEntity(UUID id, Class<? extends LivingEntity> kind, EntityType type, SpawnCategory category,
                   CaptureDoubles.FakeWorld world, double x, double z) {
            this.id = id;
            this.type = type;
            this.category = category;
            this.world = world;
            this.x = x;
            this.z = z;
            entity = (LivingEntity) Proxy.newProxyInstance(kind.getClassLoader(), new Class<?>[] {kind},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "hashCode" -> id.hashCode();
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> type.name().toLowerCase();
                        default -> read(method.getName(), method.getReturnType());
                    });
        }

        private EntityEquipment equipment() {
            return (EntityEquipment) Proxy.newProxyInstance(EntityEquipment.class.getClassLoader(),
                    new Class<?>[] {EntityEquipment.class}, (proxy, method, arguments) -> switch (method.getName()) {
                        case "getItemInMainHand" -> mainHand;
                        case "getItemInOffHand" -> offHand;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private Object read(String name, Class<?> returns) {
            if (recordReads) {
                reads.add(name);
            }
            return switch (name) {
                case "getType" -> type;
                case "getSpawnCategory" -> category;
                case "getScoreboardTags" -> Set.copyOf(tags);
                case "getWorld" -> world.world;
                case "getLocation" -> new Location(world.world, x, 64, z);
                case "getBoundingBox" -> box == null ? CaptureDoubles.box(x, z) : box;
                case "hasActiveItem" -> active != null;
                case "getActiveItem" -> active == null ? new FakeItem(Material.AIR) : active;
                case "getActiveItemUsedTime" -> active == null ? 0 : usedTicks;
                case "getEquipment" -> equipment();
                case "getVehicle" -> vehicle;
                case "isGliding" -> gliding;
                case "getVelocity" -> velocity;
                case "isValid" -> valid;
                case "isDead" -> dead;
                case "getHealth" -> 20.0;
                case "getAbsorptionAmount" -> 0.0;
                case "getMaxHealth" -> 20.0;
                case "getX" -> x;
                case "getY" -> 64.0;
                case "getZ" -> z;
                case "getWidth" -> 0.6;
                case "getYaw" -> 0.0f;
                case "getNoDamageTicks" -> 0;
                case "getMaximumNoDamageTicks" -> 20;
                case "isOnGround" -> true;
                case "isHandRaised" -> false;
                case "getTarget" -> null;
                case "getFallDistance" -> fallDistance;
                default -> returns == boolean.class ? Boolean.FALSE : returns == int.class ? 0 : null;
            };
        }
    }

    /**
     * An item with a type and data components, without a server: {@code ItemStack} has a protected constructor that
     * sets nothing, and this double answers the type and the components by identity of their type
     * ({@code TestRegistryAccess} gives one double per component key).
     */
    static final class FakeItem extends ItemStack {
        private final Material material;
        private final Map<DataComponentType, Object> components = new HashMap<>();

        FakeItem(Material material) {
            this.material = material;
        }

        /**
         * Adds a component.
         *
         * @param type component type
         * @param value its value, a double of the component interface, or {@code true} for a non-valued one
         * @return this item
         */
        FakeItem with(DataComponentType type, Object value) {
            components.put(type, value);
            return this;
        }

        @Override
        public Material getType() {
            return material;
        }

        @Override
        public boolean isEmpty() {
            return material == Material.AIR;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getData(DataComponentType.Valued<T> type) {
            return (T) components.get(type);
        }

        @Override
        public boolean hasData(DataComponentType type) {
            return components.containsKey(type);
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public String toString() {
            return "item " + material;
        }
    }

    /**
     * A double of a component interface that answers its methods by name.
     *
     * @param type the interface, such as {@code AttackRange} or {@code BlocksAttacks}
     * @param answers method name to value
     * @param <T> the interface
     * @return the double
     */
    static <T> T component(Class<T> type, Map<String, Object> answers) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, arguments) -> {
                    if (!answers.containsKey(method.getName())) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    return answers.get(method.getName());
                }));
    }

    /** Native identities over maps: MythicMobs types, factions, teams and bound profiles. */
    static final class Lookup implements ParticipantLookup {
        final Map<UUID, String> types = new HashMap<>();
        final Map<UUID, String> factions = new HashMap<>();
        final Map<UUID, String> teams = new HashMap<>();
        final Map<UUID, BoundProfile> bound = new HashMap<>();
        int reads;

        @Override
        public Optional<String> mythicType(UUID entityId) {
            reads++;
            return Optional.ofNullable(types.get(entityId));
        }

        @Override
        public Optional<String> faction(UUID entityId) {
            reads++;
            return Optional.ofNullable(factions.get(entityId));
        }

        @Override
        public Optional<String> team(Entity entity) {
            reads++;
            return Optional.ofNullable(teams.get(entity.getUniqueId()));
        }

        @Override
        public Optional<BoundProfile> bound(UUID entityId) {
            reads++;
            return Optional.ofNullable(bound.get(entityId));
        }
    }

    /** Selector resolution over fixed maps, with the teams of the main scoreboard. */
    static final class Registry implements SelectorRegistry {
        final Map<String, EntityType> types = new HashMap<>();
        final Map<String, Set<EntityType>> tags = new HashMap<>();
        final Set<String> teams = new HashSet<>();
        int reads;

        @Override
        public Optional<EntityType> entityType(NamespacedKey key) {
            reads++;
            return Optional.ofNullable(types.get(key.asString()));
        }

        @Override
        public Optional<Set<EntityType>> entityTag(NamespacedKey key) {
            reads++;
            return Optional.ofNullable(tags.get(key.asString()));
        }

        @Override
        public Set<String> teamNames() {
            reads++;
            return Set.copyOf(teams);
        }
    }

    /**
     * A policy that admits players in {@code SURVIVAL} or {@code ADVENTURE} and the entities of {@code allow}.
     *
     * @param allow allowed selectors, resolved against an empty registry (use kinds, categories and names)
     * @param perceiveNonTargets whether the members perceive entities other than their targets
     * @param maxCandidates cap of non-target entities per member
     * @return the compiled policy
     */
    static ParticipantPolicy policy(List<String> allow, boolean perceiveNonTargets, int maxCandidates) {
        var defaults = new ParticipantDefaults(new ParticipantRules(Set.of("SURVIVAL", "ADVENTURE"), List.of(),
                List.of(), perceiveNonTargets), true, maxCandidates);
        var registry = new Registry();
        return ParticipantPolicy.compile(defaults, ParticipantPolicy.GlobalSelectors.NONE, new ParticipantRules(
                Set.of("SURVIVAL", "ADVENTURE"), allow, List.of(), perceiveNonTargets), registry, (path, reason) -> {
                    throw new AssertionError(path + ": " + reason);
                });
    }
}
