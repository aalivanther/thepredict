package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.MobSighting;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.ParticipantKind;

import java.time.Duration;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * The whole engine of M09 to M11 over a geometric double of the world, for the load suite of M13 (rules P2, A5).
 * Mobs and players have a world and a position; the follow range, the distance bands and the targets come from them.
 * One tick is 50 ms of monotonic time. Every port call records its thread, and every Jev answer is completed on a
 * separate thread that the test waits for, so the suite proves that callbacks never reach the world (rules T5, A3).
 * Nothing is timed: the clock only moves when the fixture moves it, or by the simulated cost of a capture.
 */
final class LoadFixture implements AutoCloseable {
    static final long TICK_NANOS = 50_000_000L;
    static final double FOLLOW_RANGE_BLOCKS = 32;
    static final UUID WORLD_A = EngineFixtures.WORLD;
    static final UUID WORLD_B = EngineFixtures.OTHER_WORLD;
    static final UUID WORLD_C = UUID.fromString("00000000-0000-0000-0000-00000000cccc");
    static final List<TacticId> TACTICS = List.of(ExecutionFixture.PRESS, ExecutionFixture.KEEP,
            ExecutionFixture.FALL_BACK, ExecutionFixture.GUARD);

    final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
    final EngineFixtures.TestClock clock = sessions.clock;
    final Thread serverThread = Thread.currentThread();
    final Arena arena = new Arena();
    final Mobs mobs = new Mobs();
    final Actions actions = new Actions();
    final Jev jev = new Jev();
    final ContextSignals signals = new ContextSignals();
    final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
    final EngineSettings settings;
    final RequestBudget budget;
    final ServiceStatusTracker tracker;
    final Inbox<InboxEntry> inbox;
    final ThreatEnrichment threat;
    final OutcomeEvents outcomes;
    final TacticalExecution steps;
    final Coordinator coordinator;
    Peaks peaks = new Peaks();
    final TacticalProfile profile = ExecutionFixture.profile(ExecutionFixture.timing(1), 0);
    private final ExecutorService httpThread = Executors.newSingleThreadExecutor();
    private final Map<UUID, Long> baseRun = new HashMap<>();

    LoadFixture() {
        this(CoordinatorFixture.settings(2000));
    }

    LoadFixture(EngineSettings settings) {
        this(settings, ServiceState.CHECKING);
    }

    LoadFixture(EngineSettings settings, ServiceState initial) {
        this.settings = settings;
        budget = new RequestBudget(settings);
        tracker = new ServiceStatusTracker(new ServiceCheck(initial, "start"), settings.watchdogNanos(), () -> 0.5,
                line -> {
                });
        inbox = new Inbox<>(settings.inboxCapacity());
        var inFlight = new InFlightRegistry();
        var capture = new EncounterCapture(arena, sessions.registry, sessions.encounters, sessions.lifecycle, clock,
                metrics);
        threat = new ThreatEnrichment(sessions.encounters, sessions.registry, actions, clock);
        outcomes = new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics);
        steps = new TacticalExecution(new TacticalExecution.Parts(sessions.registry, sessions.encounters,
                sessions.agenda, sessions.board, arena, mobs, tracker, budget, metrics, () -> 1L, threat,
                outcomes, DecisionTap.NONE), actions,
                new ExecutionSettings(1_000_000_000L, 3, ExecutionFixture.MODEL, false), line -> {
                }, settings.fitThresholds());
        var intake = new ResultIntake(inbox, inFlight, budget, tracker, jev, metrics, steps, clock,
                DecisionTap.NONE);
        var upkeep = new SessionUpkeep(sessions.registry, sessions.encounters, sessions.agenda, sessions.board,
                signals, arena, mobs, () -> 1L, steps, metrics);
        var dispatcher = new DecisionDispatcher(capture, sessions.registry, budget, tracker, inFlight, jev, inbox,
                metrics, clock, () -> 1L, settings, line -> {
                }, DecisionTap.NONE);
        coordinator = new Coordinator(settings, clock, intake, upkeep, dispatcher, steps, sessions.agenda,
                sessions.lifecycle::tick, metrics, line -> {
                });
        sessions.release = mobs::release;
    }

    /** Engine settings of {@code config.yml} with another work budget per tick. */
    static EngineSettings settings(int maxTickWorkMicros) {
        return new EngineSettings(600, 300, 8, 4, 600_000, 10, 2, 64, 16, 64, maxTickWorkMicros,
                Duration.ofMillis(800).toNanos());
    }

    static UUID mob(int index) {
        return EngineFixtures.id(index);
    }

    static UUID player(int index) {
        return EngineFixtures.player(index);
    }

    /** Places a player. */
    void player(int index, UUID world, double x, double z) {
        arena.players.put(player(index), new Body(world, x, z));
    }

    /** Places a bound mob, targeting a player or nobody, and opens its session as a spawn does. */
    void spawn(int index, UUID world, double x, double z, int targetPlayer) {
        arena.mobs.put(mob(index), new Body(world, x, z));
        target(index, targetPlayer);
        sessions.lifecycle.discovered(mob(index), profile);
    }

    /** Sets the target of a mob; {@code 0} leaves it without a target. */
    void target(int mobIndex, int playerIndex) {
        if (playerIndex == 0) {
            arena.targets.remove(mob(mobIndex));
        } else {
            arena.targets.put(mob(mobIndex), player(playerIndex));
        }
    }

    /** Moves a mob. */
    void move(int mobIndex, double x, double z) {
        var body = arena.mobs.get(mob(mobIndex));
        arena.mobs.put(mob(mobIndex), new Body(body.world(), x, z));
    }

    /** Runs ticks; before each tick the script sees the tick, due answers are delivered and peaks are kept. */
    void run(int ticks, IntConsumer script) {
        runTimed(ticks, script, new long[ticks]);
    }

    /**
     * Runs ticks like {@link #run(int, IntConsumer)} and writes the wall time of each coordinator tick. Only the
     * local measurements read the times; no test asserts on them.
     */
    void runTimed(int ticks, IntConsumer script, long[] tickNanos) {
        for (var index = 0; index < ticks; index++) {
            script.accept((int) clock.tick);
            jev.deliverDue();
            var before = Snapshot.of(this);
            var paused = tracker.state() == ServiceState.PAUSED;
            var started = System.nanoTime();
            coordinator.tick();
            tickNanos[index] = System.nanoTime() - started;
            var after = Snapshot.of(this);
            if (paused) {
                peaks.requestsWhilePaused += after.requests() - before.requests();
                peaks.pausedTicks++;
            }
            peaks.update(before, after, this);
            baseRuns();
            clock.tick++;
            clock.nanos += TICK_NANOS;
        }
    }

    void run(int ticks) {
        run(ticks, tick -> {
        });
    }

    /** Keeps the longest run of ticks a mob with a target spent at level 4, and the total of such ticks. */
    private void baseRuns() {
        for (var session : sessions.registry.active()) {
            var entityId = session.key().entityId();
            if (arena.targets.containsKey(entityId) && sessions.board.current(entityId).level() == ControlLevel.BASE) {
                var run = baseRun.merge(entityId, 1L, Long::sum);
                peaks.baseRun = Math.max(peaks.baseRun, run);
                peaks.baseTicks++;
            } else {
                baseRun.remove(entityId);
            }
            peaks.mobTicks++;
        }
    }

    /** Brings the service to {@code READY} after its check and trial, as the first seconds of a start do. */
    void ready() {
        run(1);
        tracker.onResult(new DecisionResult.Answered(Map.of(), ExecutionFixture.MODEL, 0), clock.nanos);
    }

    /** Forgets the peaks kept so far, for a measurement that starts after a change of the scenario. */
    void resetPeaks() {
        peaks = new Peaks();
    }

    /** Counts the sessions whose published view is at a level. */
    int atLevel(ControlLevel level) {
        var count = 0;
        for (var session : sessions.registry.active()) {
            if (sessions.board.current(session.key().entityId()).level() == level) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void close() {
        httpThread.shutdownNow();
    }

    /** Position of a mob or player. */
    record Body(UUID world, double x, double z) {
        double distance(Body other) {
            return Math.hypot(x - other.x, z - other.z);
        }
    }

    /** Counters read before and after one tick. */
    record Snapshot(long captures, long probes, long installs, long requests, long casts, long followRangeChecks) {
        static Snapshot of(LoadFixture fixture) {
            return new Snapshot(fixture.arena.captures, fixture.arena.probes, fixture.mobs.installs,
                    fixture.jev.requests.size(), fixture.actions.castCalls, fixture.arena.followRangeChecks);
        }
    }

    /** Highest value of each bounded quantity over the run. */
    static final class Peaks {
        long capturesPerTick;
        long probesPerTick;
        long installsPerTick;
        long requestsPerTick;
        long followRangeChecksPerTick;
        long inbox;
        long inFlight;
        long inFlightPerWorld;
        long requestsPerMinute;
        long requestsPerMinutePerWorld;
        long tokensPerMinute;
        long encounters;
        long members;
        long participants;
        long events;
        long threatWindows;
        long signalsPending;
        long views;
        long requestsWhilePaused;
        long pausedTicks;
        long baseRun;
        long baseTicks;
        long mobTicks;

        void update(Snapshot before, Snapshot after, LoadFixture fixture) {
            capturesPerTick = Math.max(capturesPerTick, after.captures() - before.captures());
            probesPerTick = Math.max(probesPerTick, after.probes() - before.probes());
            installsPerTick = Math.max(installsPerTick, after.installs() - before.installs());
            requestsPerTick = Math.max(requestsPerTick, after.requests() - before.requests());
            followRangeChecksPerTick = Math.max(followRangeChecksPerTick,
                    after.followRangeChecks() - before.followRangeChecks());
            var now = fixture.clock.nanos;
            inbox = Math.max(inbox, fixture.inbox.size());
            inFlight = Math.max(inFlight, fixture.budget.inFlight());
            inFlightPerWorld = Math.max(inFlightPerWorld, Math.max(fixture.budget.inFlight(WORLD_A),
                    fixture.budget.inFlight(WORLD_B)));
            requestsPerMinute = Math.max(requestsPerMinute, fixture.budget.requestsLastMinute(now));
            requestsPerMinutePerWorld = Math.max(requestsPerMinutePerWorld, Math.max(
                    fixture.budget.requestsLastMinute(WORLD_A, now), fixture.budget.requestsLastMinute(WORLD_B, now)));
            tokensPerMinute = Math.max(tokensPerMinute, fixture.budget.tokensLastMinute(now));
            encounters = Math.max(encounters, fixture.sessions.encounters.size());
            signalsPending = Math.max(signalsPending, fixture.signals.pendingCount());
            views = Math.max(views, fixture.sessions.board.size());
            for (var session : fixture.sessions.registry.active()) {
                session.encounter().flatMap(fixture.sessions.encounters::find).ifPresent(encounter -> {
                    members = Math.max(members, encounter.memberCount());
                    participants = Math.max(participants, encounter.participants().size());
                    events = Math.max(events, encounter.recentEvents().size());
                    threatWindows = Math.max(threatWindows, encounter.threat().windowCount());
                });
            }
        }
    }

    /** Geometric combat reader: follow range, distance bands and targets come from positions. */
    final class Arena implements CombatReader {
        final Map<UUID, Body> mobs = new HashMap<>();
        final Map<UUID, Body> players = new HashMap<>();
        final Map<UUID, UUID> targets = new HashMap<>();
        final Set<UUID> removed = new HashSet<>();
        final Map<UUID, String> factions = new HashMap<>();
        final Set<Thread> threads = new HashSet<>();
        final Map<EncounterId, Long> lastCaptureTick = new HashMap<>();
        final Map<UUID, Long> capturesPerWorld = new HashMap<>();
        long captureCostNanos;
        long probeCostNanos;
        long maxCaptureGapTicks;
        long captures;
        long probes;
        long followRangeChecks;
        long capturedMembers;

        @Override
        public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context) {
            threads.add(Thread.currentThread());
            captures++;
            clock.nanos += captureCostNanos;
            capturesPerWorld.merge(context.worldId(), 1L, Long::sum);
            var previous = lastCaptureTick.put(encounter, clock.tick);
            if (previous != null) {
                maxCaptureGapTicks = Math.max(maxCaptureGapTicks, clock.tick - previous);
            }
            var facts = new ArrayList<MobFacts>();
            var perceived = new LinkedHashSet<UUID>();
            var memberIds = new HashSet<UUID>();
            members.forEach(member -> memberIds.add(member.entityId()));
            for (var member : members) {
                read(member, context, perceived, memberIds).ifPresent(facts::add);
            }
            var participants = new ArrayList<ParticipantFacts>();
            for (var participant : perceived) {
                var kind = kind(participant);
                participants.add(new ParticipantFacts(context.aliasFor().apply(participant, kind), HealthBand.HEALTHY,
                        ArmorBand.LIGHT, Set.of(), participants.size() + 1, kind == ParticipantKind.ENTITY
                        ? Optional.of("zombie") : Optional.empty()));
            }
            return facts.isEmpty() ? Optional.empty() : Optional.of(new CombatFacts(encounter, clock.tick,
                    clock.nanos, facts, participants, context.recentEvents(), 0));
        }

        /**
         * Reads one member as the capture adapter does: a sighting for every member, facts only in the world. A target
         * may be a player or, since M17, another mob; a member of the captured encounter is never a target (rule I13).
         */
        private Optional<MobFacts> read(MobKey member, CaptureContext context, Set<UUID> perceived, Set<UUID> members) {
            var body = mobs.get(member.entityId());
            if (body == null || removed.contains(member.entityId())) {
                return Optional.empty();
            }
            capturedMembers++;
            var target = Optional.ofNullable(targets.get(member.entityId())).filter(this::targetable)
                    .filter(id -> !members.contains(id));
            var distance = target.map(id -> body.distance(bodyOf(id)));
            context.sightings().accept(new MobSighting(member, body.world(), target,
                    distance.map(OptionalDouble::of).orElseGet(OptionalDouble::empty)));
            if (!body.world().equals(context.worldId())) {
                return Optional.empty();
            }
            target.ifPresent(perceived::add);
            var relation = target.map(id -> new TargetRelation(context.aliasFor().apply(id, kind(id)),
                    band(distance.get()), Trend.STEADY, true, true, false, false, false));
            return Optional.of(new MobFacts(member, HealthBand.HEALTHY, relation, Set.of(), Set.copyOf(TACTICS),
                    Set.of(), Set.of()));
        }

        @Override
        public Optional<MobProbe> probe(MobKey mob) {
            threads.add(Thread.currentThread());
            probes++;
            clock.nanos += probeCostNanos;
            var body = mobs.get(mob.entityId());
            if (body == null || removed.contains(mob.entityId())) {
                return Optional.empty();
            }
            var target = Optional.ofNullable(targets.get(mob.entityId())).filter(this::targetable);
            return Optional.of(new MobProbe(mob, body.world(), HealthBand.HEALTHY, target,
                    target.map(id -> band(body.distance(bodyOf(id))))));
        }

        /** A player, or a mob of the arena that is still there (M17). */
        private boolean targetable(UUID id) {
            return players.containsKey(id) || mobs.containsKey(id) && !removed.contains(id);
        }

        private Body bodyOf(UUID id) {
            var player = players.get(id);
            return player != null ? player : mobs.get(id);
        }

        private ParticipantKind kind(UUID id) {
            return players.containsKey(id) ? ParticipantKind.PLAYER : ParticipantKind.ENTITY;
        }

        @Override
        public boolean withinFollowRange(MobKey mob, MobKey member) {
            threads.add(Thread.currentThread());
            followRangeChecks++;
            var first = mobs.get(mob.entityId());
            var second = mobs.get(member.entityId());
            return first != null && second != null && first.world().equals(second.world())
                    && first.distance(second) <= FOLLOW_RANGE_BLOCKS && !rivals(mob.entityId(), member.entityId());
        }

        /** Two mobs of different factions are rivals, as the participant policy of M17 makes them (rule I13). */
        private boolean rivals(UUID first, UUID second) {
            var one = factions.get(first);
            var other = factions.get(second);
            return one != null && other != null && !one.equals(other);
        }

        @Override
        public boolean inCombat(UUID entityId) {
            threads.add(Thread.currentThread());
            return targets.containsKey(entityId) || targets.containsValue(entityId);
        }

        @Override
        public boolean isActive(UUID entityId) {
            return mobs.containsKey(entityId) && !removed.contains(entityId);
        }

        @Override
        public Optional<ThreatTableState> threatTable(MobKey mob) {
            return Optional.of(ThreatTableState.DISABLED);
        }

        private static DistanceBand band(double distance) {
            return distance <= 2.5 ? DistanceBand.MELEE : distance <= 16 ? DistanceBand.RANGED : DistanceBand.BEYOND;
        }
    }

    /**
     * Goal ownership double: the first call for a generation installs, later calls only find the marker, as the self
     * check before a decision does on a server; progress is always made unless the mob is stuck.
     */
    final class Mobs implements MobControl {
        final Set<UUID> stuck = new HashSet<>();
        final Set<MobKey> installed = new HashSet<>();
        final List<MobKey> released = new ArrayList<>();
        final Set<Thread> threads = new HashSet<>();
        long installs;
        long checks;

        @Override
        public InstallResult ensureInstalled(MobKey key, TacticalProfile profile) {
            threads.add(Thread.currentThread());
            if (installed.add(key)) {
                installs++;
            } else {
                checks++;
            }
            return new InstallResult.Installed(12);
        }

        @Override
        public MovementProgress progress(MobKey key) {
            threads.add(Thread.currentThread());
            return stuck.contains(key.entityId()) ? new MovementProgress(false, false, false, OptionalDouble.of(6.0))
                    : new MovementProgress(true, true, true, OptionalDouble.empty());
        }

        @Override
        public List<GoalSummary> describeGoals(MobKey key) {
            return List.of();
        }

        @Override
        public void release(MobKey key) {
            threads.add(Thread.currentThread());
            released.add(key);
        }
    }

    /**
     * Combat actions double: every cast succeeds once the native cooldown of its skill has passed ({@link NativeCooldowns},
     * 20 ticks) and is counted once per armed choice (intent and re-arm); threat changes are summed.
     */
    final class Actions implements CombatActions {
        final NativeCooldowns cooldowns = new NativeCooldowns(() -> clock.tick,
                ExecutionFixture.Actions.DEFAULT_COOLDOWN_TICKS);
        final Map<String, Integer> castsPerIntent = new HashMap<>();
        final Set<Thread> threads = new HashSet<>();
        long castCalls;
        long casts;
        long signals;
        long threatChanges;

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            threads.add(Thread.currentThread());
            castCalls++;
            if (!cooldowns.ready(mob, choice)) {
                return ExecutionOutcome.NOT_ELIGIBLE;
            }
            casts++;
            var session = sessions.registry.find(mob).orElseThrow();
            var intent = session.intent().orElseThrow();
            castsPerIntent.merge(mob + "/" + intent.tactic().value() + "/" + intent.level() + "/" + intent.startTick()
                    + "/" + session.choiceEpoch(), 1, Integer::sum);
            cooldowns.cast(mob, choice);
            return ExecutionOutcome.CAST;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            threads.add(Thread.currentThread());
            return cooldowns.ready(mob, choice);
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
            threads.add(Thread.currentThread());
            signals++;
        }

        @Override
        public void addThreat(MobKey mob, UUID playerId, double amount) {
            threads.add(Thread.currentThread());
            threatChanges++;
        }

        long duplicateCasts() {
            return castsPerIntent.values().stream().filter(count -> count > 1).count();
        }
    }

    /**
     * Jev double: each request is answered {@code latencyTicks} ticks after it was sent, on a separate thread, with
     * the answer the policy gives each question, or fails while {@code failure} is set.
     */
    final class Jev implements DecisionClient {
        final List<DecisionRequest> requests = new ArrayList<>();
        final List<Pending> pending = new ArrayList<>();
        final Set<Thread> threads = new HashSet<>();
        final Set<Thread> completionThreads = new HashSet<>();
        Function<QuestionSpec, Answer> policy = question -> ExecutionFixture.prefer(question.options().getFirst(),
                0.7, 0.8);
        Optional<FailureKind> failure = Optional.empty();
        Function<DecisionRequest, Preparation> preparation = PreparedFixtures::ready;
        final List<String> bodies = new ArrayList<>();
        int latencyTicks = 6;
        long tokens;
        long checks;

        @Override
        public Preparation prepare(DecisionRequest request) {
            threads.add(Thread.currentThread());
            return preparation.apply(request);
        }

        @Override
        public CompletableFuture<DecisionResult> decide(PreparedRequest prepared) {
            threads.add(Thread.currentThread());
            var request = prepared.sent();
            requests.add(request);
            bodies.add(prepared.body());
            var future = new CompletableFuture<DecisionResult>();
            pending.add(new Pending(future, request, clock.tick + latencyTicks));
            return future;
        }

        @Override
        public CompletableFuture<ServiceCheck> check() {
            threads.add(Thread.currentThread());
            checks++;
            return CompletableFuture.completedFuture(failure.isPresent()
                    ? new ServiceCheck(ServiceState.PAUSED, "network") : new ServiceCheck(ServiceState.READY, "listed"));
        }

        @Override
        public void close() {
        }

        /** Completes the due requests on the separate thread and waits until each callback returned. */
        void deliverDue() {
            var due = pending.stream().filter(entry -> entry.dueTick() <= clock.tick).toList();
            pending.removeAll(due);
            for (var entry : due) {
                var result = result(entry.request());
                try {
                    httpThread.submit(() -> {
                        completionThreads.add(Thread.currentThread());
                        entry.future().complete(result);
                    }).get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                } catch (ExecutionException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }

        private DecisionResult result(DecisionRequest request) {
            if (failure.isPresent()) {
                return new DecisionResult.Failed(failure.get(), Optional.empty(), "scripted");
            }
            var answers = new LinkedHashMap<QuestionKey, Answer>();
            request.questions().forEach(question -> answers.put(question.key(), policy.apply(question)));
            var used = PreparedFixtures.legacyTokens(request);
            tokens += used;
            return new DecisionResult.Answered(answers, ExecutionFixture.MODEL, used);
        }
    }

    /** One request waiting for its scripted answer. */
    record Pending(CompletableFuture<DecisionResult> future, DecisionRequest request, long dueTick) {
    }
}
