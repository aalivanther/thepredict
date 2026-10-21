package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.binding.BoundProfile;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.ContextRule;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobSighting;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.skills.Skill;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Doubles of Paper and MythicMobs for {@link CombatCapture} (rule P4): proxies of {@code World},
 * {@code LivingEntity} and {@code Player} answering only the calls the capture makes, a {@link MemberSource} and an
 * {@link EntityReads}. Any other call fails the test.
 */
final class CaptureDoubles {
    static final UUID WORLD_ID = new UUID(0xA11L, 1);
    static final UUID OTHER_WORLD_ID = new UUID(0xA11L, 2);

    final FakeWorld world = new FakeWorld(WORLD_ID);
    final Source source = new Source();
    final Reads reads = new Reads();
    final List<MobSighting> sightings = new ArrayList<>();
    final Map<UUID, ParticipantAlias> aliases = new LinkedHashMap<>();
    long tick = 100;
    long nanos = 5_000;
    final ServerClock clock = ServerClock.of(() -> tick, () -> nanos);
    final List<String> warnings = new ArrayList<>();
    final NativeContextEvaluator contexts = new NativeContextEvaluator(warnings::add, () -> tick);
    double searchRadiusBlocks = 32;
    boolean serverThread = true;
    ParticipantLookup lookup = ParticipantLookup.NONE;

    static UUID mobId(int value) {
        return new UUID(0x30BL, value);
    }

    static UUID playerId(int value) {
        return new UUID(0x9L, value);
    }

    CombatCapture capture() {
        return new CombatCapture(source, reads, id -> Optional.ofNullable(source.entities.get(id)), contexts,
                (condition, entity) -> false, clock, () -> serverThread, searchRadiusBlocks, lookup);
    }

    CaptureContext context(Set<MobKey> allow, Map<MobKey, Double> previous, Set<MobKey> unreachable,
                           List<EncounterEvent> events) {
        return new CaptureContext((id, kind) -> aliases.computeIfAbsent(id, key -> new ParticipantAlias(kind,
                (int) aliases.values().stream().filter(alias -> alias.kind() == kind).count() + 1)),
                allow, previous, events, WORLD_ID, unreachable, sightings::add);
    }

    CaptureContext context() {
        return context(Set.of(), Map.of(), Set.of(), List.of());
    }

    static BoundProfile bound(List<ContextRule> contexts, boolean brainSpecies) {
        return new BoundProfile(ContextFixtures.profile(contexts), Map.of(ContextFixtures.STRIKE, skill("Strike"),
                ContextFixtures.GUARD, skill("Guard")), Map.of(), "ZOMBIE", brainSpecies);
    }

    /** The bound profile of the members with another participant policy (M17). */
    static BoundProfile bound(ParticipantPolicy policy) {
        return new BoundProfile(ContextFixtures.profile(List.of()), Map.of(ContextFixtures.STRIKE, skill("Strike"),
                ContextFixtures.GUARD, skill("Guard")), Map.of(), "ZOMBIE", false, policy);
    }

    static BoundProfile declaredBound() {
        return new BoundProfile(ContextFixtures.declaredProfile(), Map.of(ContextFixtures.STRIKE, skill("Strike"),
                ContextFixtures.BOLT, skill("Bolt"), ContextFixtures.SLAM, skill("Slam"), ContextFixtures.GUARD,
                skill("Guard")), Map.of(), "ZOMBIE", false);
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

    /** Adds a bound member at a position; returns its double. */
    FakeMob mob(int value, double x, double z) {
        var mob = new FakeMob(mobId(value), world, x, z);
        source.members.put(mob.id, new MemberSource.Member(mob.entity, bound(List.of(), false)));
        source.entities.put(mob.id, mob.entity);
        return mob;
    }

    /** Adds a bound member with another profile at a position; returns its double. */
    FakeMob mob(int value, double x, double z, BoundProfile bound) {
        var mob = mob(value, x, z);
        source.members.put(mob.id, new MemberSource.Member(mob.entity, bound));
        return mob;
    }

    /** Bounding box of a zombie or a player, 0.6 blocks wide, standing at a position of height 64. */
    static BoundingBox box(double x, double z) {
        return new BoundingBox(x - 0.3, 64, z - 0.3, x + 0.3, 65.8, z + 0.3);
    }

    /** Adds a survival player visible to the world query; returns its double. */
    FakePlayer player(int value, double x, double z) {
        var player = new FakePlayer(playerId(value), world, x, z);
        world.players.add(player.player);
        return player;
    }

    /**
     * A world whose nearby-player query returns every player: a box larger than any sphere. Its nearby queries and the
     * lines of sight of its mobs are written to {@link #calls}, with their arguments, for the golden references of
     * M17.0.
     */
    static final class FakeWorld {
        final UUID id;
        final List<Player> players = new ArrayList<>();
        final List<LivingEntity> living = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        final World world;

        FakeWorld(UUID id) {
            this.id = id;
            world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getUID" -> id;
                        case "getNearbyPlayers" -> {
                            calls.add("getNearbyPlayers " + at((Location) arguments[0]) + " " + arguments[1]);
                            yield List.copyOf(players);
                        }
                        case "getNearbyLivingEntities" -> {
                            calls.add("getNearbyLivingEntities " + at((Location) arguments[0]) + " " + arguments[1]);
                            var all = new ArrayList<LivingEntity>(players);
                            all.addAll(living);
                            @SuppressWarnings("unchecked")
                            var filter = arguments.length > 2 ? (Predicate<Object>) arguments[2] : null;
                            yield filter == null ? all : all.stream().filter(filter::test).toList();
                        }
                        case "hashCode" -> id.hashCode();
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "world";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private static String at(Location location) {
            return "(" + location.getX() + "," + location.getY() + "," + location.getZ() + ")";
        }
    }

    /** A member mob. */
    static final class FakeMob {
        final UUID id;
        FakeWorld world;
        double x;
        double z;
        double health = 20;
        double absorption;
        boolean water;
        boolean lava;
        boolean climbing;
        Predicate<UUID> sight = player -> true;
        int sightChecks;
        int boxReads;
        final LivingEntity entity;

        FakeMob(UUID id, FakeWorld world, double x, double z) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.z = z;
            entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                    new Class<?>[] {LivingEntity.class}, (proxy, method, arguments) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getWorld" -> this.world.world;
                        case "getLocation" -> new Location(this.world.world, this.x, 64, this.z);
                        case "getHealth" -> health;
                        case "getAbsorptionAmount" -> absorption;
                        case "getBoundingBox" -> {
                            boxReads++;
                            yield box(this.x, this.z);
                        }
                        case "hasLineOfSight" -> {
                            sightChecks++;
                            var other = ((Entity) arguments[0]).getUniqueId();
                            this.world.calls.add("hasLineOfSight " + id + " -> " + other);
                            yield sight.test(other);
                        }
                        case "isValid" -> true;
                        case "isDead" -> false;
                        case "isInWater" -> water;
                        case "isInLava" -> lava;
                        case "isClimbing" -> climbing;
                        case "hashCode" -> id.hashCode();
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "mob";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    /** A player. */
    static final class FakePlayer {
        final UUID id;
        final FakeWorld world;
        double x;
        double z;
        float yaw;
        GameMode mode = GameMode.SURVIVAL;
        boolean dead;
        boolean valid = true;
        boolean forward;
        boolean backward;
        boolean left;
        boolean right;
        final Player player;

        FakePlayer(UUID id, FakeWorld world, double x, double z) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.z = z;
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getWorld" -> world.world;
                        case "getLocation" -> new Location(world.world, this.x, 64, this.z, yaw, 0f);
                        case "getGameMode" -> mode;
                        case "getBoundingBox" -> box(this.x, this.z);
                        case "isValid" -> valid && !dead;
                        case "isDead" -> dead;
                        case "getCurrentInput" -> input();
                        case "hashCode" -> id.hashCode();
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "player";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private Input input() {
            return (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "isForward" -> forward;
                        case "isBackward" -> backward;
                        case "isLeft" -> left;
                        case "isRight" -> right;
                        default -> false;
                    });
        }
    }

    /** MythicMobs side of the members. */
    static final class Source implements MemberSource {
        final Map<UUID, Member> members = new HashMap<>();
        final Map<UUID, LivingEntity> entities = new HashMap<>();
        final Map<UUID, LivingEntity> targets = new HashMap<>();
        final Map<UUID, Map<UUID, Double>> threat = new HashMap<>();
        final Set<UUID> threatCombat = new HashSet<>();
        final Set<Skill> cooling = new HashSet<>();
        final Map<UUID, ThreatTableState> threatTables = new HashMap<>();
        int memberCalls;

        @Override
        public Optional<Member> member(UUID mobId) {
            memberCalls++;
            return Optional.ofNullable(members.get(mobId));
        }

        @Override
        public Optional<LivingEntity> target(UUID mobId) {
            return Optional.ofNullable(targets.get(mobId));
        }

        @Override
        public double threat(UUID mobId, UUID participantId) {
            return threat.getOrDefault(mobId, Map.of()).getOrDefault(participantId, 0.0);
        }

        @Override
        public boolean onCooldown(UUID mobId, Skill skill) {
            return cooling.contains(skill);
        }

        @Override
        public boolean threatInCombat(UUID mobId) {
            return threatCombat.contains(mobId);
        }

        @Override
        public boolean isActive(UUID mobId) {
            return members.containsKey(mobId);
        }

        @Override
        public Optional<ThreatTableState> threatTable(UUID mobId) {
            return members.containsKey(mobId) ? Optional.of(threatTables.getOrDefault(mobId, ThreatTableState.DISABLED))
                    : Optional.empty();
        }
    }

    /** Paper reads with fixed answers and call counters. */
    static final class Reads implements EntityReads {
        final Map<UUID, ParticipantReading> readings = new HashMap<>();
        final Map<UUID, Double> maxHealth = new HashMap<>();
        final Map<UUID, Integer> combatTicks = new HashMap<>();
        double followRange = 16;
        TargetReach reach = TargetReach.player(3, false, 0.0f);
        OptionalDouble shieldAngle = OptionalDouble.empty();
        CurrentPath path = CurrentPath.NONE;
        boolean pathReaches = true;
        boolean trackerCombat;
        int findPathCalls;

        @Override
        public double followRangeBlocks(LivingEntity mob) {
            return followRange;
        }

        @Override
        public double maxHealth(LivingEntity entity) {
            return maxHealth.getOrDefault(entity.getUniqueId(), 20.0);
        }

        @Override
        public ParticipantReading participant(LivingEntity participant) {
            return readings.getOrDefault(participant.getUniqueId(), TargetCueReaderTest.calm());
        }

        @Override
        public TargetReach targetReach(LivingEntity target) {
            return reach;
        }

        @Override
        public OptionalDouble raisedShieldAngleDegrees(LivingEntity target) {
            return shieldAngle;
        }

        @Override
        public CurrentPath currentPath(LivingEntity mob) {
            return path;
        }

        @Override
        public boolean findPathReaches(LivingEntity mob, LivingEntity target) {
            findPathCalls++;
            return pathReaches;
        }

        @Override
        public int combatDurationTicks(LivingEntity entity) {
            return combatTicks.getOrDefault(entity.getUniqueId(), 0);
        }

        @Override
        public boolean trackerInCombat(LivingEntity entity) {
            return trackerCombat;
        }
    }
}
