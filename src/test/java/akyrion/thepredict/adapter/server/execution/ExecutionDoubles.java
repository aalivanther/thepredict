package akyrion.thepredict.adapter.server.execution;

import io.lumine.mythic.api.skills.Skill;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.BoundingBox;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Doubles of Paper and MythicMobs for {@link CombatActionsAdapter} (rule P4): proxies of {@code World},
 * {@code LivingEntity}, {@code Player} and {@code Skill} that answer only the calls the adapter makes, and a
 * {@link CastSource} that records what it is asked to do. Any other call fails the test.
 */
final class ExecutionDoubles {
    private ExecutionDoubles() {
    }

    static World world(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hashCode" -> name.hashCode();
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    static Skill skill(String name) {
        return (Skill) Proxy.newProxyInstance(Skill.class.getClassLoader(), new Class<?>[] {Skill.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hashCode" -> name.hashCode();
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** A mob at a position that records its melee attacks. */
    static final class Caster {
        final List<String> calls = new ArrayList<>();
        World world;
        double x;
        double z;
        boolean sight = true;
        int sightChecks;
        final LivingEntity entity;

        Caster(World world, double x, double z) {
            this.world = world;
            this.x = x;
            this.z = z;
            entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                    new Class<?>[] {LivingEntity.class}, (proxy, method, arguments) -> switch (method.getName()) {
                        case "getWorld" -> this.world;
                        case "getLocation" -> new Location(this.world, this.x, 64, this.z);
                        case "getBoundingBox" -> new BoundingBox(this.x - 0.3, 64, this.z - 0.3, this.x + 0.3, 65.8,
                                this.z + 0.3);
                        case "hasLineOfSight" -> {
                            sightChecks++;
                            yield sight;
                        }
                        case "attack" -> record("attack");
                        case "swingMainHand" -> record("swing");
                        case "hashCode" -> 1;
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "caster";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private Object record(String call) {
            calls.add(call);
            return null;
        }
    }

    /** A player target facing south (yaw 0) at the origin. */
    static final class Target {
        World world;
        int noDamageTicks;
        int maximumNoDamageTicks = 20;
        boolean blocking;
        boolean online = true;
        boolean dead;
        UUID uniqueId = new UUID(0x9L, 1);
        final Player player;

        Target(World world) {
            this.world = world;
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getWorld" -> this.world;
                        case "getLocation" -> new Location(this.world, 0, 64, 0, 0f, 0f);
                        case "getBoundingBox" -> new BoundingBox(-0.3, 64, -0.3, 0.3, 65.8, 0.3);
                        case "getNoDamageTicks" -> noDamageTicks;
                        case "getMaximumNoDamageTicks" -> maximumNoDamageTicks;
                        case "isBlocking" -> blocking;
                        case "isOnline" -> online;
                        case "isDead" -> dead;
                        case "getUniqueId" -> uniqueId;
                        case "hashCode" -> 2;
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "target";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    /** A zombie target that is not a player (M17.7), facing south at the origin. */
    static final class EntityTarget {
        World world;
        boolean valid = true;
        boolean dead;
        UUID uniqueId = new UUID(0x2AL, 1);
        final LivingEntity entity;

        EntityTarget(World world) {
            this.world = world;
            entity = (LivingEntity) Proxy.newProxyInstance(Zombie.class.getClassLoader(), new Class<?>[] {Zombie.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getWorld" -> this.world;
                        case "getLocation" -> new Location(this.world, 0, 64, 0, 0f, 0f);
                        case "getBoundingBox" -> new BoundingBox(-0.3, 64, -0.3, 0.3, 65.95, 0.3);
                        case "getNoDamageTicks" -> 0;
                        case "getMaximumNoDamageTicks" -> 20;
                        case "isValid" -> valid;
                        case "isDead" -> dead;
                        case "getUniqueId" -> uniqueId;
                        case "hasActiveItem" -> false;
                        case "hashCode" -> 3;
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "zombie";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    /** A cast source whose answers each test sets and that records every request. */
    static final class Source implements CastSource {
        final List<String> calls = new ArrayList<>();
        final List<java.util.concurrent.atomic.AtomicBoolean> flags = new ArrayList<>();
        Optional<LivingEntity> caster = Optional.empty();
        Optional<LivingEntity> target = Optional.empty();
        boolean onCooldown;
        OptionalLong remaining = OptionalLong.of(0);
        Optional<UUID> aiTarget = Optional.empty();
        boolean executes = true;
        boolean threatTable = true;
        boolean conditionsHold = true;

        @Override
        public Optional<LivingEntity> caster(UUID mobId) {
            return caster;
        }

        @Override
        public Optional<LivingEntity> target(UUID mobId) {
            return target;
        }

        @Override
        public boolean onCooldown(UUID mobId, Skill skill) {
            return onCooldown;
        }

        @Override
        public OptionalLong remainingMillis(UUID mobId, Skill skill) {
            return remaining;
        }

        @Override
        public Optional<UUID> aiTarget(UUID mobId) {
            return aiTarget;
        }

        @Override
        public boolean conditionsHold(UUID mobId, Skill skill) {
            calls.add("conditions " + skill);
            return conditionsHold;
        }

        @Override
        public boolean conditionsHoldAt(UUID mobId, Skill skill, LivingEntity target) {
            calls.add("conditionsAt " + skill + " " + target);
            return conditionsHold;
        }

        @Override
        public boolean execute(UUID mobId, Skill skill) {
            calls.add("execute " + skill);
            return executes;
        }

        @Override
        public boolean executeAt(UUID mobId, Skill skill, LivingEntity target) {
            calls.add("executeAt " + skill + " " + target);
            return executes;
        }

        @Override
        public boolean executeAt(UUID mobId, Skill skill, LivingEntity target,
                                 java.util.concurrent.atomic.AtomicBoolean terminated) {
            calls.add("executeAt " + skill + " " + target + " flagged");
            flags.add(terminated);
            return executes;
        }

        @Override
        public boolean signal(UUID mobId, String signal) {
            calls.add("signal " + signal);
            return caster.isPresent();
        }

        @Override
        public boolean changeThreat(UUID mobId, LivingEntity participant, double amount) {
            calls.add("threat " + amount);
            return threatTable;
        }
    }
}
