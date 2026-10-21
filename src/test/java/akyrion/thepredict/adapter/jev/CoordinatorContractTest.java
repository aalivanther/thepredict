package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.MobSighting;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.engine.Agenda;
import akyrion.thepredict.engine.CombatReader;
import akyrion.thepredict.engine.ContextSignals;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.Coordinator;
import akyrion.thepredict.engine.DecisionDispatcher;
import akyrion.thepredict.engine.DecisionMetrics;
import akyrion.thepredict.engine.Encounter;
import akyrion.thepredict.engine.EncounterCapture;
import akyrion.thepredict.engine.EncounterRegistry;
import akyrion.thepredict.engine.EngineSettings;
import akyrion.thepredict.engine.ExecutionSteps;
import akyrion.thepredict.engine.InFlightRegistry;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.MobControl;
import akyrion.thepredict.engine.MobSession;
import akyrion.thepredict.engine.RequestBudget;
import akyrion.thepredict.engine.ResultIntake;
import akyrion.thepredict.engine.ServerClock;
import akyrion.thepredict.engine.ServiceStatusTracker;
import akyrion.thepredict.engine.SessionLifecycle;
import akyrion.thepredict.engine.SessionMetrics;
import akyrion.thepredict.engine.SessionReconciler;
import akyrion.thepredict.engine.SessionRegistry;
import akyrion.thepredict.engine.SessionUpkeep;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check of M09.7 on the loopback contract server of M08.8: the coordinator, the real System One client and
 * a combat reader double. No request leaves the machine.
 */
class CoordinatorContractTest {
    private static final UUID WORLD = new UUID(0xA11L, 1);
    private static final UUID MOB = new UUID(0x30BL, 1);
    private static final UUID PLAYER = new UUID(0x9L, 1);
    private static final String ANSWER = """
            {"model": "jev-1.13.0", "answers": {
              "q0": {"type": "choice", "choice": "keep_range", "confidence": 0.8,
                     "probabilities": {"keep_range": 0.7, "press_attack": 0.2, "hold_ground": 0.1}},
              "q1": {"type": "choice", "choice": "press_attack", "confidence": 0.7,
                     "probabilities": {"keep_range": 0.2, "press_attack": 0.7, "hold_ground": 0.1}},
              "q2": {"type": "choice", "choice": "hold_ground", "confidence": 0.6,
                     "probabilities": {"keep_range": 0.3, "press_attack": 0.1, "hold_ground": 0.6}}},
             "usage": {"input_tokens": 777, "output_tokens": 3}}
            """;

    private final AtomicLong tick = new AtomicLong();
    private final ServerClock clock = ServerClock.of(tick::get, System::nanoTime);
    private final List<InboxEntry.CompletedRequest> accepted = new CopyOnWriteArrayList<>();

    @Test
    void theCoordinatorChecksTheServiceAsksOneQuestionSetAndHandsTheAnswerOver() throws Exception {
        try (var server = SystemOneContractServer.start()) {
            server.onDecide(SystemOneContractServer.json(200, ANSWER));
            var client = new SystemOneClient(JevFixtures.settings(server.baseUrl()), clock);
            try {
                var engine = new Engine(client);
                engine.sessions.open(MOB, JevFixtures.guardian());
                until(() -> engine.tracker.status().state() == ServiceState.READY, engine);
                until(() -> server.posts() == 1, engine);
                until(() -> !accepted.isEmpty(), engine);
                var result = assertInstanceOf(DecisionResult.Answered.class, accepted.getFirst().result());
                assertEquals(3, result.answers().size());
                assertEquals(777, result.inputTokens());
                assertTrue(engine.budget.inFlight() <= 1);
                assertTrue(engine.budget.requestsLastMinute(System.nanoTime()) >= 1);
                assertEquals(1, engine.metrics.total(DecisionMetrics.Counter.ANSWERED));
                assertEquals(1, engine.metrics.samples(DecisionMetrics.Span.ROUNDTRIP));
                var body = server.received().stream().filter(request -> request.method().equals("POST")).findFirst()
                        .orElseThrow().body();
                assertTrue(body.contains("\"q2\""), body);
                assertTrue(!body.contains(PLAYER.toString()) && !body.contains(MOB.toString()), body);
                assertTrue(server.received().stream().anyMatch(request -> request.path().equals("/v1/models")));
            } finally {
                client.close();
            }
        }
    }

    /** Ticks the coordinator until the condition holds; the HTTP threads complete in the meantime. */
    private void until(BooleanSupplier condition, Engine engine) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() - deadline > 0) {
                throw new AssertionError("condition not reached; service " + engine.tracker.status());
            }
            engine.coordinator.tick();
            tick.incrementAndGet();
            Thread.onSpinWait();
        }
    }

    /** The engine of M09 wired as the plugin wires it, over the doubles of this test. */
    private final class Engine {
        final IntentBoard board = new IntentBoard();
        final Agenda agenda = new Agenda();
        final SessionMetrics sessionMetrics = new SessionMetrics();
        final EncounterRegistry encounters = new EncounterRegistry(agenda, clock, sessionMetrics);
        final SessionRegistry sessions = new SessionRegistry(board, encounters, agenda);
        final DecisionMetrics metrics = new DecisionMetrics(() -> 0L);
        final EngineSettings settings = new EngineSettings(600, 300, 8, 4, 600_000, 10, 2, 64, 16, 64, 2000,
                Duration.ofSeconds(5).toNanos());
        final RequestBudget budget = new RequestBudget(settings);
        final ServiceStatusTracker tracker = new ServiceStatusTracker(new ServiceCheck(ServiceState.CHECKING, "start"),
                settings.watchdogNanos(), () -> 0.5, line -> {
        });
        final Coordinator coordinator;

        Engine(SystemOneClient client) {
            var lifecycle = new SessionLifecycle(sessions, board, key -> {
            }, new SessionReconciler(sessions, encounters, id -> true, sessionMetrics), sessionMetrics, clock);
            var capture = new EncounterCapture(new Reader(), sessions, encounters, lifecycle, clock, metrics);
            var inbox = new Inbox<InboxEntry>(settings.inboxCapacity());
            var inFlight = new InFlightRegistry();
            var steps = new Steps();
            var intake = new ResultIntake(inbox, inFlight, budget, tracker, client, metrics, steps, clock,
                    DecisionTap.NONE);
            var upkeep = new SessionUpkeep(sessions, encounters, agenda, board, new ContextSignals(), new Reader(),
                    new Control(), () -> 1, steps, metrics);
            var dispatcher = new DecisionDispatcher(capture, sessions, budget, tracker, inFlight, client, inbox, metrics,
                    clock, () -> 1, settings, line -> {
                    }, DecisionTap.NONE);
            coordinator = new Coordinator(settings, clock, intake, upkeep, dispatcher, steps, agenda, lifecycle::tick, metrics,
                    line -> {
                    });
        }
    }

    /** A mob with a ranged player target and three eligible tactics of the guardian profile. */
    private static final class Reader implements CombatReader {
        @Override
        public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context) {
            var mobs = new ArrayList<MobFacts>();
            for (var member : members) {
                context.sightings().accept(new MobSighting(member, WORLD, Optional.of(PLAYER), OptionalDouble.of(7.0)));
                mobs.add(new MobFacts(member, HealthBand.HEALTHY, Optional.of(new TargetRelation(
                        context.aliasFor().apply(PLAYER, ParticipantKind.PLAYER), DistanceBand.RANGED, Trend.STEADY, true, true, false, false, false)),
                        Set.of(), Set.of(JevFixtures.tactic("press_attack"), JevFixtures.tactic("keep_range"),
                        JevFixtures.tactic("hold_ground")), Set.of(), Set.of()));
            }
            var player = new ParticipantFacts(context.aliasFor().apply(PLAYER, ParticipantKind.PLAYER), HealthBand.HEALTHY, ArmorBand.MEDIUM,
                    Set.of(), 1);
            return Optional.of(new CombatFacts(encounter, 0, 0, mobs, List.of(player), context.recentEvents(), 4));
        }

        @Override
        public Optional<MobProbe> probe(MobKey mob) {
            return Optional.of(new MobProbe(mob, WORLD, HealthBand.HEALTHY, Optional.of(PLAYER),
                    Optional.of(DistanceBand.RANGED)));
        }

        @Override
        public boolean withinFollowRange(MobKey mob, MobKey member) {
            return true;
        }

        @Override
        public boolean inCombat(UUID entityId) {
            return true;
        }

        @Override
        public boolean isActive(UUID entityId) {
            return true;
        }

        @Override
        public Optional<ThreatTableState> threatTable(MobKey mob) {
            return Optional.of(ThreatTableState.DISABLED);
        }
    }

    /** Goal ownership that always reports the set installed. */
    private static final class Control implements MobControl {
        @Override
        public InstallResult ensureInstalled(MobKey key, TacticalProfile profile) {
            return new InstallResult.Installed(3);
        }

        @Override
        public MovementProgress progress(MobKey key) {
            return new MovementProgress(false, false, false, OptionalDouble.empty());
        }

        @Override
        public List<GoalSummary> describeGoals(MobKey key) {
            return List.of();
        }

        @Override
        public void release(MobKey key) {
        }
    }

    /** M10 steps that record every accepted result. */
    private final class Steps implements ExecutionSteps {
        @Override
        public void accept(InboxEntry.CompletedRequest completed, long currentTick, long nowNanos) {
            accepted.add(completed);
        }

        @Override
        public void react(MobSession session, MobProbe probe, long currentTick, long nowNanos) {
        }

        @Override
        public void evaluated(Encounter encounter, long currentTick, long nowNanos) {
        }
    }
}
