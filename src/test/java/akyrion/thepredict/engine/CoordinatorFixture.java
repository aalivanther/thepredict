package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.MobSighting;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.ParticipantKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;

/** The whole engine of M09 over doubles of the ports and a controllable clock (rule P2). */
final class CoordinatorFixture {
    static final TacticId HOLD = new TacticId("hold_ground");
    static final TacticId PRESS = new TacticId("press_attack");
    static final TacticId KEEP = new TacticId("keep_range");

    final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
    final EngineFixtures.TestClock clock = sessions.clock;
    final Reader reader = new Reader();
    final Control control = new Control();
    final Client client = new Client();
    final Steps steps = new Steps();
    final ContextSignals signals = new ContextSignals();
    final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
    final List<String> warnings = new ArrayList<>();
    final List<String> serviceLog = new ArrayList<>();
    final EngineSettings settings;
    final RequestBudget budget;
    final ServiceStatusTracker tracker;
    final Inbox<InboxEntry> inbox;
    final InFlightRegistry inFlight = new InFlightRegistry();
    final EncounterCapture capture;
    final ResultIntake intake;
    final SessionUpkeep upkeep;
    final DecisionDispatcher dispatcher;
    final Coordinator coordinator;
    long revision = 1;
    int lifecycleRuns;

    CoordinatorFixture() {
        this(settings(2000));
    }

    CoordinatorFixture(EngineSettings settings) {
        this.settings = settings;
        budget = new RequestBudget(settings);
        tracker = new ServiceStatusTracker(new ServiceCheck(ServiceState.CHECKING, "start"), settings.watchdogNanos(),
                () -> 0.5, serviceLog::add);
        inbox = new Inbox<>(settings.inboxCapacity());
        capture = new EncounterCapture(reader, sessions.registry, sessions.encounters, sessions.lifecycle, clock, metrics);
        intake = new ResultIntake(inbox, inFlight, budget, tracker, client, metrics, steps, clock, DecisionTap.NONE);
        upkeep = new SessionUpkeep(sessions.registry, sessions.encounters, sessions.agenda, sessions.board, signals,
                reader, control, () -> revision, steps, metrics);
        dispatcher = new DecisionDispatcher(capture, sessions.registry, budget, tracker, inFlight, client, inbox, metrics,
                clock, () -> revision, settings, warnings::add, DecisionTap.NONE);
        coordinator = new Coordinator(settings, clock, intake, upkeep, dispatcher, steps, sessions.agenda, () -> {
            lifecycleRuns++;
            sessions.lifecycle.tick();
        }, metrics, warnings::add);
    }

    static EngineSettings settings(int maxTickWorkMicros) {
        return new EngineSettings(600, 300, 8, 4, 600_000, 10, 2, 64, 16, 64, maxTickWorkMicros, 800_000_000L);
    }

    static TacticalProfile profile(int heartbeatTicks, int minimumIntervalTicks, int contingencies) {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactics.put(HOLD, new Tactic(HOLD, Movement.HOLD, Optional.empty(), Optional.empty(), 10));
        tactics.put(PRESS, new Tactic(PRESS, Movement.APPROACH, Optional.empty(), Optional.empty(), 60));
        tactics.put(KEEP, new Tactic(KEEP, Movement.KEEP_DISTANCE, Optional.empty(), Optional.empty(), 60));
        return new TacticalProfile("guardian_ai", "Guardian", "Defeat the target", tactics, Map.of(), List.of(),
                new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                new DecisionTiming(heartbeatTicks, minimumIntervalTicks, 100, 0.5, 0.2, contingencies));
    }

    /** Opens an active session with the three-tactic profile. */
    MobSession open(int value) {
        return sessions.registry.open(EngineFixtures.id(value), profile(20, 20, 1));
    }

    /** Gives a mob a target that its probe and capture report. */
    void target(int mob, int player) {
        reader.targets.put(EngineFixtures.id(mob), EngineFixtures.player(player));
    }

    /** Brings the service to {@code READY} without a trial, as after a check and a first answer. */
    void ready() {
        var generation = tracker.checkToStart(clock.nanos).orElseThrow();
        tracker.onCheck(generation, new ServiceCheck(ServiceState.READY, "listed"), clock.nanos);
        tracker.onResult(answered(0), clock.nanos);
    }

    /** Runs {@code count} ticks, one tick and one millisecond apart. */
    void run(int count) {
        for (var index = 0; index < count; index++) {
            coordinator.tick();
            clock.tick++;
            clock.nanos += 1_000_000;
        }
    }

    static DecisionResult.Answered answered(int tokens) {
        return new DecisionResult.Answered(Map.of(), "jev-1.13.0", tokens);
    }

    /** Combat reader double: targets, eligibility, follow range and combat state are set by each test. */
    static final class Reader implements CombatReader {
        final Map<UUID, UUID> targets = new HashMap<>();
        final Map<UUID, Set<TacticId>> eligible = new HashMap<>();
        final Map<UUID, DistanceBand> distances = new HashMap<>();
        final Set<UUID> inCombat = new HashSet<>();
        final Set<UUID> failingProbes = new HashSet<>();
        final List<Set<MobKey>> pathPermissions = new ArrayList<>();
        BiPredicate<MobKey, MobKey> followRange = (mob, member) -> true;
        UUID world = WORLD;
        boolean failingCapture;
        boolean failingInCombat;
        int captures;
        int probes;

        @Override
        public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context) {
            captures++;
            if (failingCapture) {
                throw new IllegalStateException("capture failed");
            }
            pathPermissions.add(Set.copyOf(context.allowPathfinding()));
            var mobs = new ArrayList<MobFacts>();
            var players = new LinkedHashSet<UUID>();
            for (var member : members) {
                var target = Optional.ofNullable(targets.get(member.entityId()));
                context.sightings().accept(new MobSighting(member, world, target,
                        target.map(id -> OptionalDouble.of(6.0)).orElseGet(OptionalDouble::empty)));
                target.ifPresent(players::add);
                var relation = target.map(id -> new TargetRelation(context.aliasFor().apply(id, ParticipantKind.PLAYER),
                        distances.getOrDefault(member.entityId(), DistanceBand.RANGED), Trend.STEADY, true, true, false,
                        false, false));
                mobs.add(new MobFacts(member, HealthBand.HEALTHY, relation, Set.of(),
                        eligible.getOrDefault(member.entityId(), Set.of(HOLD, PRESS, KEEP)), Set.of(), Set.of()));
            }
            var participants = new ArrayList<ParticipantFacts>();
            for (var player : players) {
                participants.add(new ParticipantFacts(context.aliasFor().apply(player, ParticipantKind.PLAYER), HealthBand.HEALTHY,
                        ArmorBand.LIGHT, Set.of(), participants.size() + 1));
            }
            return Optional.of(new CombatFacts(encounter, 0, 0, mobs, participants, context.recentEvents(), 0));
        }

        @Override
        public Optional<MobProbe> probe(MobKey mob) {
            probes++;
            if (failingProbes.contains(mob.entityId())) {
                throw new IllegalStateException("probe failed");
            }
            var target = Optional.ofNullable(targets.get(mob.entityId()));
            return Optional.of(new MobProbe(mob, world, HealthBand.HEALTHY, target,
                    target.map(id -> distances.getOrDefault(mob.entityId(), DistanceBand.RANGED))));
        }

        @Override
        public boolean withinFollowRange(MobKey mob, MobKey member) {
            return followRange.test(mob, member);
        }

        @Override
        public boolean inCombat(UUID entityId) {
            if (failingInCombat) {
                throw new IllegalStateException("combat tracker failed");
            }
            return inCombat.contains(entityId);
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

    /** Goal ownership double: scripted results per mob, then {@code Installed}; optional time cost per call. */
    final class Control implements MobControl {
        final Map<UUID, ArrayDeque<InstallResult>> scripted = new HashMap<>();
        final List<MobKey> calls = new ArrayList<>();
        long nanosPerCall;

        @Override
        public InstallResult ensureInstalled(MobKey key, TacticalProfile profile) {
            calls.add(key);
            clock.nanos += nanosPerCall;
            var queue = scripted.get(key.entityId());
            return queue == null || queue.isEmpty() ? new InstallResult.Installed(3) : queue.poll();
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

        int callsFor(int mob) {
            return (int) calls.stream().filter(key -> key.entityId().equals(EngineFixtures.id(mob))).count();
        }
    }

    /**
     * Decision client double: prepares every request as planned unless a test scripts another preparation, counts
     * preparations and sends, and keeps every decision future pending until the test completes it.
     */
    static final class Client implements DecisionClient {
        final List<DecisionRequest> requests = new ArrayList<>();
        final List<DecisionRequest> prepared = new ArrayList<>();
        final List<PreparedRequest> sent = new ArrayList<>();
        final List<CompletableFuture<DecisionResult>> futures = new ArrayList<>();
        ServiceCheck checkResult = new ServiceCheck(ServiceState.READY, "listed");
        Function<DecisionRequest, Preparation> preparation = PreparedFixtures::ready;
        int checks;
        boolean failDecide;
        boolean returnNull;
        boolean closed;
        Function<PreparedRequest, DecisionResult> immediate;

        @Override
        public Preparation prepare(DecisionRequest request) {
            prepared.add(request);
            return preparation.apply(request);
        }

        @Override
        public CompletableFuture<DecisionResult> decide(PreparedRequest request) {
            if (failDecide) {
                throw new IllegalArgumentException("broken request");
            }
            if (returnNull) {
                return null;
            }
            sent.add(request);
            requests.add(request.sent());
            if (immediate != null) {
                var future = CompletableFuture.completedFuture(immediate.apply(request));
                futures.add(future);
                return future;
            }
            var future = new CompletableFuture<DecisionResult>();
            futures.add(future);
            return future;
        }

        @Override
        public CompletableFuture<ServiceCheck> check() {
            checks++;
            return CompletableFuture.completedFuture(checkResult);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** M10 steps double: records what the coordinator hands over. */
    static final class Steps implements ExecutionSteps {
        final List<InboxEntry.CompletedRequest> accepted = new ArrayList<>();
        final List<MobKey> reacted = new ArrayList<>();
        final List<EncounterId> evaluated = new ArrayList<>();
        final Set<UUID> failing = new HashSet<>();
        boolean failingAccept;
        boolean failingEvaluated;

        @Override
        public void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
            accepted.add(completed);
            if (failingAccept) {
                throw new IllegalStateException("acceptance failed");
            }
        }

        @Override
        public void evaluated(Encounter encounter, long tick, long nowNanos) {
            evaluated.add(encounter.id());
            if (failingEvaluated) {
                throw new IllegalStateException("evaluation hook failed");
            }
        }

        @Override
        public void react(MobSession session, MobProbe probe, long tick, long nowNanos) {
            if (failing.contains(session.key().entityId())) {
                throw new IllegalStateException("reactive cycle failed");
            }
            reacted.add(session.key());
        }
    }
}
