package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Shared engine test doubles: a controllable clock and one wired set of session components. */
public final class EngineFixtures {
    static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");
    static final UUID OTHER_WORLD = UUID.fromString("00000000-0000-0000-0000-00000000bbbb");

    private EngineFixtures() {
    }

    static UUID id(int value) {
        return new UUID(0x5E55L, value);
    }

    static UUID player(int value) {
        return new UUID(0x9L, value);
    }

    public static TacticalProfile profile(String mythicMobId) {
        return profile(mythicMobId, 40, 20);
    }

    static TacticalProfile profile(String mythicMobId, int heartbeatTicks, int minimumIntervalTicks) {
        return profile(mythicMobId, heartbeatTicks, minimumIntervalTicks, new ThreatWeights(1, 1, 1, 0.5, 10));
    }

    static TacticalProfile profile(String mythicMobId, ThreatWeights threat) {
        return profile(mythicMobId, 40, 20, threat);
    }

    static TacticalProfile profile(String mythicMobId, int heartbeatTicks, int minimumIntervalTicks,
                                   ThreatWeights threat) {
        var hold = new TacticId("hold_ground");
        return new TacticalProfile(mythicMobId.toLowerCase() + "_ai", mythicMobId, "Defeat the target",
                Map.of(hold, new Tactic(hold, Movement.HOLD, Optional.empty(), Optional.empty(), 50)), Map.of(),
                List.of(), new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), threat, List.of(),
                new DecisionTiming(heartbeatTicks, minimumIntervalTicks, 100, 0.5, 0.2, 1));
    }

    static TacticalIntent intent(MobKey key) {
        return new TacticalIntent(key, new TacticId("hold_ground"), Movement.HOLD, Optional.empty(), 0.9, List.of(),
                Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE, false);
    }

    static Predicate<MobKey> near() {
        return key -> true;
    }

    static Predicate<MobKey> far() {
        return key -> false;
    }

    /** A clock whose tick and time the test moves. */
    public static final class TestClock implements ServerClock {
        long tick;
        long nanos;

        @Override
        public long currentTick() {
            return tick;
        }

        @Override
        public long nanoTime() {
            return nanos;
        }
    }

    /** One wired set of registries with a controllable clock and recorded releases. */
    static final class Sessions {
        final TestClock clock = new TestClock();
        final IntentBoard board = new IntentBoard();
        final SessionMetrics metrics = new SessionMetrics();
        final Agenda agenda = new Agenda();
        final EncounterRegistry encounters = new EncounterRegistry(agenda, clock, metrics);
        final SessionRegistry registry = new SessionRegistry(board, encounters, agenda);
        final List<MobKey> released = new ArrayList<>();
        final List<UUID> inactive = new ArrayList<>();
        final SessionReconciler reconciler = new SessionReconciler(registry, encounters,
                entityId -> !inactive.contains(entityId), metrics);
        Consumer<MobKey> release = released::add;
        final SessionLifecycle lifecycle = new SessionLifecycle(registry, board, key -> release.accept(key),
                reconciler, metrics, clock);

        MobSession open(int value) {
            return registry.open(id(value), profile("Guardian"));
        }
    }
}
