package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.Agenda;
import akyrion.thepredict.engine.CombatActions;
import akyrion.thepredict.engine.ContextEvents;
import akyrion.thepredict.engine.Encounter;
import akyrion.thepredict.engine.EncounterRegistry;
import akyrion.thepredict.engine.DecisionMetrics;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.OutcomeEvents;
import akyrion.thepredict.engine.OutcomeMetrics;
import akyrion.thepredict.engine.ServerClock;
import akyrion.thepredict.engine.SessionMetrics;
import akyrion.thepredict.engine.SessionRegistry;
import akyrion.thepredict.engine.ThreatEnrichment;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The real engine behind the context listeners, with proxies of players, projectiles and a controllable thread. */
final class ContextListenerFixtures {
    static final UUID WORLD_ID = new UUID(0xA11L, 7);

    long tick;
    boolean primaryThread = true;
    final List<Runnable> nextTick = new ArrayList<>();
    final List<String> log = new ArrayList<>();
    final ServerClock clock = ServerClock.of(() -> tick, () -> 0L);
    final Agenda agenda = new Agenda();
    final SessionMetrics metrics = new SessionMetrics();
    final EncounterRegistry encounters = new EncounterRegistry(agenda, clock, metrics);
    final SessionRegistry sessions = new SessionRegistry(new IntentBoard(), encounters, agenda);
    final ContextEvents events = new ContextEvents(encounters, sessions, agenda, clock);
    final List<String> threats = new ArrayList<>();
    final ThreatEnrichment threat = new ThreatEnrichment(encounters, sessions, new RecordingActions(threats), clock);
    final ServerThread thread = new ServerThread(() -> primaryThread, nextTick::add, log::add);
    final CombatContextListener context = new CombatContextListener(events, thread);
    final OutcomeEvents outcomes = new OutcomeEvents(sessions, encounters, new OutcomeMetrics(), new DecisionMetrics(() -> tick));
    final Set<UUID> blockingEntities = new HashSet<>();
    final CombatOutcomeListener outcome = new CombatOutcomeListener(events, threat, outcomes, thread, () -> tick,
            entity -> blockingEntities.contains(entity.getUniqueId()));
    final World world = LifecycleFixtures.world(WORLD_ID);

    static UUID playerId(int value) {
        return new UUID(0x9L, value);
    }

    /** Opens a session for a mob and forms its encounter against a player, already decided once. */
    Encounter encounter(int mob, UUID playerId) {
        var session = sessions.open(LifecycleFixtures.id(mob), LifecycleFixtures.profile("Guardian"));
        var encounter = encounters.assign(session, WORLD_ID, playerId, member -> true);
        agenda.decided(encounter, tick);
        return encounter;
    }

    /** Opens a session for a mob and forms its encounter against another living entity (M17), already decided once. */
    Encounter encounterAgainstEntity(int mob, UUID entityId) {
        var session = sessions.open(LifecycleFixtures.id(mob), LifecycleFixtures.profile("Guardian"));
        var encounter = encounters.assign(session, WORLD_ID, entityId, member -> true);
        agenda.decided(encounter, tick);
        return encounter;
    }

    /** A zombie that answers its identity, world and maximum health, as a participant that is not a player. */
    LivingEntity zombie(UUID id) {
        return (LivingEntity) Proxy.newProxyInstance(Zombie.class.getClassLoader(), new Class<?>[] {Zombie.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getWorld" -> world;
                    case "getAttribute" -> attribute(20.0);
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "zombie";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** A maximum health of {@code value}: every member hit reads it for the fight clock (arreglos-engine-FO §3.6). */
    static AttributeInstance attribute(double value) {
        return (AttributeInstance) Proxy.newProxyInstance(AttributeInstance.class.getClassLoader(),
                new Class<?>[] {AttributeInstance.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getValue" -> value;
                    case "hashCode" -> 1;
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    void runNextTick() {
        var tasks = new ArrayList<>(nextTick);
        nextTick.clear();
        tasks.forEach(Runnable::run);
    }

    Player player(UUID id, boolean blocking) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getWorld" -> world;
                    case "getAttribute" -> attribute(20.0);
                    case "isBlocking" -> blocking;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "player";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    static Projectile arrow(LivingEntity shooter) {
        return (Projectile) Proxy.newProxyInstance(AbstractArrow.class.getClassLoader(),
                new Class<?>[] {AbstractArrow.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getShooter" -> shooter;
                    case "getUniqueId" -> new UUID(0xA7L, 1);
                    case "hashCode" -> 1;
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** Combat actions that only record threat changes as {@code player amount}; nothing else is expected. */
    private record RecordingActions(List<String> threats) implements CombatActions {
        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            throw new UnsupportedOperationException("cast");
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            throw new UnsupportedOperationException("ready");
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
            throw new UnsupportedOperationException("signalTactic");
        }

        @Override
        public void addThreat(MobKey mob, UUID playerId, double amount) {
            threats.add(playerId.getLeastSignificantBits() + " " + amount);
        }
    }

    static Projectile snowball(Player shooter) {
        return (Projectile) Proxy.newProxyInstance(Projectile.class.getClassLoader(), new Class<?>[] {Projectile.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getShooter" -> shooter;
                    case "hashCode" -> 2;
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
