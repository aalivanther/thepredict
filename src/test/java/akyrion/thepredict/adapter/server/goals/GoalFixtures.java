package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.engine.ServerClock;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.lumine.mythic.api.mobs.MythicMob;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Creature;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Doubles of Paper and MythicMobs interfaces for the goal tests; unexpected calls fail the test. */
final class GoalFixtures {
    static final GoalKeys KEYS = new GoalKeys(key("hold"), key("flank"), key("owner"), key("micro"));
    static final MovementSettings MOVEMENT = new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty());

    private GoalFixtures() {
    }

    static GoalKey<Mob> key(String name) {
        return GoalKey.of(Mob.class, new NamespacedKey("thepredict", name));
    }

    static GoalKey<Mob> vanillaKey(String name) {
        return GoalKey.of(Mob.class, NamespacedKey.minecraft(name));
    }

    /** A profile with one tactic per movement, in the given order, and the given base AI. */
    static TacticalProfile profile(MovementSettings movement, List<String> baseAi, Movement... movements) {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        for (var used : movements) {
            var id = new TacticId("use_" + used.name().toLowerCase());
            tactics.put(id, new Tactic(id, used, Optional.of(new ChoiceId("strike")), Optional.empty(), 50));
        }
        var strike = new SkillChoice(new ChoiceId("strike"), ChoiceKind.MYTHIC_SKILL, Optional.of("Strike"),
                Set.of(PredictValue.MELEE_ATTACK), true, 0);
        return new TacticalProfile("test_ai", "TestMob", "Defeat the target", tactics,
                Map.of(strike.id(), strike), List.of(), movement, new HealthThresholds(0.3, 0.6),
                new ThreatWeights(1, 1, 1, 0.5, 10), baseAi, new DecisionTiming(40, 20, 100, 0.5, 0.2, 1));
    }

    /** A clock whose tick and nanoseconds the test controls. */
    static ServerClock clock(AtomicLong ticks, AtomicLong nanos) {
        return ServerClock.of(ticks::get, nanos::get);
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(GoalFixtures.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    /** A creature double with a fixed identity and type, a pathfinder and a target the test controls. */
    static Mob mob(UUID id, EntityType type, Pathfinder pathfinder, Supplier<LivingEntity> target, Location location) {
        return mobOf(Creature.class, id, type, pathfinder, target, location);
    }

    /** A mob double that is not a {@link Creature}, such as a slime. */
    static Mob nonCreature(UUID id, EntityType type) {
        return mobOf(Mob.class, id, type, null, () -> null, new Location(null, 0, 64, 0));
    }

    private static Mob mobOf(Class<? extends Mob> kind, UUID id, EntityType type, Pathfinder pathfinder,
                             Supplier<LivingEntity> target, Location location) {
        return proxy(kind, (instance, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getType" -> type;
            case "getPathfinder" -> pathfinder;
            case "getTarget" -> target.get();
            case "getLocation" -> location.clone();
            case "isValid" -> true;
            case "hashCode" -> System.identityHashCode(instance);
            case "equals" -> instance == args[0];
            case "toString" -> "Mob " + id;
            default -> throw new UnsupportedOperationException("Unexpected Mob call: " + method.getName());
        });
    }

    /** A target double at a location the test controls. */
    static LivingEntity target(Supplier<Location> location) {
        return proxy(LivingEntity.class, (instance, method, args) -> switch (method.getName()) {
            case "getLocation" -> location.get().clone();
            case "hashCode" -> System.identityHashCode(instance);
            case "equals" -> instance == args[0];
            default -> throw new UnsupportedOperationException("Unexpected target call: " + method.getName());
        });
    }

    /** A MythicMobs type double whose only answer is its {@code AIGoalSelectors}. */
    static MythicMob mythicMob(List<String> goalSelectors) {
        return proxy(MythicMob.class, (instance, method, args) -> switch (method.getName()) {
            case "getAIGoalSelectors" -> goalSelectors;
            default -> throw new UnsupportedOperationException("Unexpected MythicMob call: " + method.getName());
        });
    }

    /** A goal double with a key and types that never runs. */
    static Goal<Mob> goal(GoalKey<Mob> key, GoalType... types) {
        return new Goal<>() {
            @Override
            public boolean shouldActivate() {
                return false;
            }

            @Override
            public GoalKey<Mob> getKey() {
                return key;
            }

            @Override
            public EnumSet<GoalType> getTypes() {
                var set = EnumSet.noneOf(GoalType.class);
                set.addAll(List.of(types));
                return set;
            }
        };
    }

    /** A path double with a number of points that may or may not reach its end. */
    static Pathfinder.PathResult path(int points, boolean reachable) {
        return proxy(Pathfinder.PathResult.class, (instance, method, args) -> switch (method.getName()) {
            case "getPoints" -> Collections.nCopies(points, new Location(null, 0, 0, 0));
            case "canReachFinalPoint" -> reachable;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
}
