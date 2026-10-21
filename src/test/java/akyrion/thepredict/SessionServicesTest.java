package akyrion.thepredict;

import akyrion.thepredict.adapter.server.events.CombatContextListener;
import akyrion.thepredict.adapter.server.events.CombatOutcomeListener;
import akyrion.thepredict.adapter.server.events.LifecycleListener;
import akyrion.thepredict.adapter.server.events.OutcomeListener;
import akyrion.thepredict.adapter.server.events.MobDirectory;
import akyrion.thepredict.adapter.server.events.ServerThread;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.engine.CombatReader;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.MobSession;
import akyrion.thepredict.engine.ServerClock;
import akyrion.thepredict.engine.SessionMetrics;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionServicesTest {
    private static final UUID MOB = new UUID(0x51L, 1);

    private final List<String> warnings = new ArrayList<>();
    private final List<MobKey> released = new ArrayList<>();
    private final IntentBoard board = new IntentBoard();
    private long tick;
    private boolean broken;
    private final MobDirectory mobs = new MobDirectory() {
        @Override
        public Optional<String> mobTypeOf(UUID entityId) {
            if (broken) {
                throw new IllegalStateException("secret detail");
            }
            return Optional.of("Guardian");
        }

        @Override
        public boolean isActive(UUID entityId) {
            return true;
        }

        @Override
        public List<UUID> activeMobIds() {
            return List.of(MOB);
        }
    };
    private static final UUID WORLD = new UUID(0xA11L, 1);

    private final List<UUID> inactive = new ArrayList<>();
    private final CombatReader reader = new CombatReader() {
        @Override
        public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context) {
            return Optional.empty();
        }

        @Override
        public Optional<MobProbe> probe(MobKey mob) {
            return Optional.of(new MobProbe(mob, WORLD, HealthBand.HEALTHY, Optional.empty(), Optional.empty()));
        }

        @Override
        public boolean withinFollowRange(MobKey mob, MobKey member) {
            return true;
        }

        @Override
        public boolean inCombat(UUID entityId) {
            return false;
        }

        @Override
        public boolean isActive(UUID entityId) {
            return !inactive.contains(entityId);
        }

        @Override
        public Optional<ThreatTableState> threatTable(MobKey mob) {
            return Optional.of(ThreatTableState.DISABLED);
        }
    };
    private final SessionServices services = new SessionServices(board, ServerClock.of(() -> tick, () -> 0L),
            released::add, mobs, reader, mobId -> Optional.of(profile()), () -> { }, new ServerThread(() -> true,
            Runnable::run, warnings::add), warnings::add, new NoCombatActions());

    private static TacticalProfile profile() {
        var hold = new TacticId("hold_ground");
        return new TacticalProfile("guardian_ai", "Guardian", "Defeat the target",
                Map.of(hold, new Tactic(hold, Movement.HOLD, Optional.empty(), Optional.empty(), 50)), Map.of(),
                List.of(), new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                new DecisionTiming(40, 20, 100, 0.5, 0.2, 1));
    }

    @Test
    void theScanOpensTheExistingMobsAndTheMetricsCountOnlyFixedCounters() {
        services.startScan();
        services.tick();
        assertEquals(Optional.of(new MobKey(MOB, 0)), key());
        assertEquals(1, services.sessionMetrics().openedSessions());
        assertEquals(0, services.lifecycle().pendingReleases());
        assertEquals(0, services.contextEvents().metrics().recordedEvents());
        services.lifecycle().ended(MOB, SessionMetrics.SessionEnd.UNBOUND);
        assertTrue(key().isEmpty());
        assertEquals(1, services.sessionMetrics().endedSessions(SessionMetrics.SessionEnd.UNBOUND));
    }

    private Optional<MobKey> key() {
        return services.registry().find(MOB).map(MobSession::key);
    }

    @Test
    void aFailingTickIsLoggedByTypeOnlyAndAtMostOncePerInterval() {
        broken = true;
        services.startScan();
        services.tick();
        services.startScan();
        tick = SessionServices.FAILURE_LOG_INTERVAL_TICKS - 1;
        services.tick();
        assertEquals(List.of("Session lifecycle tick failed: IllegalStateException"), warnings);
        services.startScan();
        tick = SessionServices.FAILURE_LOG_INTERVAL_TICKS;
        services.tick();
        assertEquals(2, warnings.size());
    }

    @Test
    void shutdownClearsTheViewsAndReleasesTheActiveSessions() {
        services.startScan();
        services.tick();
        assertEquals(1, services.shutdown());
        assertEquals(List.of(new MobKey(MOB, 0)), released);
        assertEquals(0, board.size());
    }

    @Test
    void aCatalogChangeAdvancesEverySessionLikeAReload() {
        services.startScan();
        services.tick();
        services.catalogChanged();
        assertEquals(Optional.of(new MobKey(MOB, 1)), key());
    }

    @Test
    void theReconciliationReadsIsActiveFromTheCombatReader() {
        services.startScan();
        services.tick();
        inactive.add(MOB);
        tick = 200;
        services.tick();
        assertTrue(key().isEmpty());
        assertEquals(1, services.sessionMetrics().endedSessions(SessionMetrics.SessionEnd.RECONCILED));
    }

    @Test
    void theContextListenersAreRegisteredAfterTheLifecycleListener() {
        var listeners = services.listeners();
        assertEquals(4, listeners.size());
        assertTrue(listeners.getFirst() instanceof LifecycleListener);
        assertTrue(listeners.get(1) instanceof CombatContextListener);
        assertTrue(listeners.get(2) instanceof CombatOutcomeListener);
        assertTrue(listeners.get(3) instanceof OutcomeListener);
    }
}
