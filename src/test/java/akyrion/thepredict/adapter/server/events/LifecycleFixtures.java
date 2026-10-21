package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.engine.Agenda;
import akyrion.thepredict.engine.EncounterRegistry;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import akyrion.thepredict.engine.SessionLifecycle;
import akyrion.thepredict.engine.SessionMetrics;
import akyrion.thepredict.engine.SessionReconciler;
import akyrion.thepredict.engine.SessionRegistry;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/** Real engine sessions behind the lifecycle listeners, with doubles of MythicMobs, Bukkit and the scheduler. */
final class LifecycleFixtures implements MobDirectory {
    final Map<UUID, String> mobTypes = new HashMap<>();
    final Set<UUID> inactive = new HashSet<>();
    final Map<String, TacticalProfile> catalog = new HashMap<>();
    final List<Runnable> nextTick = new ArrayList<>();
    final List<String> log = new ArrayList<>();
    final List<MobKey> released = new ArrayList<>();
    boolean primaryThread = true;
    long tick;

    final IntentBoard board = new IntentBoard();
    final SessionMetrics metrics = new SessionMetrics();
    final ServerClock clock = ServerClock.of(() -> tick, () -> 0L);
    final Agenda agenda = new Agenda();
    final EncounterRegistry encounters = new EncounterRegistry(agenda, clock, metrics);
    final SessionRegistry sessions = new SessionRegistry(board, encounters, agenda);
    final SessionLifecycle lifecycle = new SessionLifecycle(sessions, board, released::add,
            new SessionReconciler(sessions, encounters, this::isActive, metrics), metrics, clock);
    final MobScan scan = new MobScan(lifecycle, this, mobId -> Optional.ofNullable(catalog.get(mobId)));
    final ServerThread thread = new ServerThread(() -> primaryThread, nextTick::add, log::add);
    final LifecycleListener listener = new LifecycleListener(lifecycle, scan, thread, () -> log.add("rebind"),
            mobId -> Optional.ofNullable(catalog.get(mobId)));

    LifecycleFixtures() {
        catalog.put("Guardian", profile("Guardian"));
    }

    static UUID id(int value) {
        return new UUID(0xE7L, value);
    }

    static TacticalProfile profile(String mythicMobId) {
        var hold = new TacticId("hold_ground");
        return new TacticalProfile(mythicMobId.toLowerCase() + "_ai", mythicMobId, "Defeat the target",
                Map.of(hold, new Tactic(hold, Movement.HOLD, Optional.empty(), Optional.empty(), 50)), Map.of(),
                List.of(), new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                new DecisionTiming(40, 20, 100, 0.5, 0.2, 1));
    }

    UUID mob(int value, String mobType) {
        mobTypes.put(id(value), mobType);
        return id(value);
    }

    void runNextTick() {
        var tasks = new ArrayList<>(nextTick);
        nextTick.clear();
        tasks.forEach(Runnable::run);
    }

    @Override
    public Optional<String> mobTypeOf(UUID entityId) {
        return Optional.ofNullable(mobTypes.get(entityId));
    }

    @Override
    public boolean isActive(UUID entityId) {
        return mobTypes.containsKey(entityId) && !inactive.contains(entityId);
    }

    @Override
    public List<UUID> activeMobIds() {
        return List.copyOf(mobTypes.keySet());
    }

    static World world(UUID worldId) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUID" -> worldId;
                    case "hashCode" -> worldId.hashCode();
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    static Location at(World world) {
        return new Location(world, 1, 64, 1);
    }

    static Entity entity(UUID entityId) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[] {Entity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> entityId;
                    case "hashCode" -> entityId.hashCode();
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    static Chunk chunk(World world) {
        return (Chunk) Proxy.newProxyInstance(Chunk.class.getClassLoader(), new Class<?>[] {Chunk.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
