package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.MobSighting;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetCue;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.UseCondition;
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
import java.util.function.BiPredicate;
import java.util.function.Function;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;

/**
 * The engine of M09 with the tactical execution of M10 over doubles of the ports and a controllable clock (rule P2).
 * One tick is 50 ms of monotonic time, so intent lifetimes and answer ages behave as on a server at 20 TPS.
 */
final class ExecutionFixture {
    static final TacticId PRESS = new TacticId("press_attack");
    static final TacticId KEEP = new TacticId("keep_range");
    static final TacticId FALL_BACK = new TacticId("fall_back");
    static final TacticId GUARD = new TacticId("guard_post");
    static final ChoiceId STRIKE = new ChoiceId("strike");
    static final ChoiceId GUARD_BREAK = new ChoiceId("guard_break");
    static final TacticId SHIELD_PRESS = new TacticId("press");
    static final TacticId BREAK_GUARD = new TacticId("break_guard");
    static final long TICK_NANOS = 50_000_000L;
    static final String MODEL = "jev-1.13.0";

    final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
    final EngineFixtures.TestClock clock = sessions.clock;
    final Reader reader = new Reader(clock, sessions.registry);
    final Mobs mobs = new Mobs();
    final Actions actions = new Actions(clock);
    final CoordinatorFixture.Client client = new CoordinatorFixture.Client();
    final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
    final List<String> log = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();
    final EngineSettings settings = CoordinatorFixture.settings(1_000_000);
    final RequestBudget budget;
    final ServiceStatusTracker tracker;
    final InFlightRegistry inFlight = new InFlightRegistry();
    final SwitchableDecisionTap tap = new SwitchableDecisionTap();
    final ThreatEnrichment threat;
    final OutcomeEvents outcomes;
    final TacticalExecution steps;
    final ResultIntake intake;
    final Coordinator coordinator;
    long revision = 1;

    ExecutionFixture() {
        this(new ExecutionSettings(1_000_000_000L, 3, MODEL, true), settings(2000));
    }

    /** A fixture whose service starts in {@code initial}; {@code UNCONFIGURED} never contacts Jev. */
    static ExecutionFixture starting(ServiceState initial) {
        return new ExecutionFixture(new ExecutionSettings(1_000_000_000L, 3, MODEL, true), settings(2000), initial);
    }

    ExecutionFixture(ExecutionSettings execution, EngineSettings engine) {
        this(execution, engine, ServiceState.CHECKING);
    }

    ExecutionFixture(ExecutionSettings execution, EngineSettings engine, ServiceState initial) {
        budget = new RequestBudget(engine);
        tracker = new ServiceStatusTracker(new ServiceCheck(initial, "start"), engine.watchdogNanos(),
                () -> 0.5, line -> {
                });
        var inbox = new Inbox<InboxEntry>(engine.inboxCapacity());
        var capture = new EncounterCapture(reader, sessions.registry, sessions.encounters, sessions.lifecycle, clock,
                metrics);
        threat = new ThreatEnrichment(sessions.encounters, sessions.registry, actions, clock);
        outcomes = new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics);
        steps = new TacticalExecution(new TacticalExecution.Parts(sessions.registry, sessions.encounters,
                sessions.agenda, sessions.board, reader, mobs, tracker, budget, metrics, () -> revision, threat,
                outcomes, tap), actions, execution, log::add, engine.fitThresholds());
        intake = new ResultIntake(inbox, inFlight, budget, tracker, client, metrics, steps, clock, tap);
        var upkeep = new SessionUpkeep(sessions.registry, sessions.encounters, sessions.agenda, sessions.board,
                new ContextSignals(), reader, mobs, () -> revision, steps, metrics);
        var dispatcher = new DecisionDispatcher(capture, sessions.registry, budget, tracker, inFlight, client, inbox,
                metrics, clock, () -> revision, engine, warnings::add, tap, intake);
        coordinator = new Coordinator(engine, clock, intake, upkeep, dispatcher, steps, sessions.agenda,
                sessions.lifecycle::tick, metrics, line -> {
                });
    }

    static EngineSettings settings(int maxRequestsPerMinute) {
        return new EngineSettings(maxRequestsPerMinute, maxRequestsPerMinute, 8, 4, 600_000, 10, 2, 64, 16, 64,
                1_000_000, 800_000_000L);
    }

    static TacticalProfile profile(DecisionTiming timing, int extraCooldownTicks) {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactics.put(PRESS, new Tactic(PRESS, Movement.APPROACH, Optional.of(STRIKE), Optional.empty(), 50));
        tactics.put(KEEP, new Tactic(KEEP, Movement.KEEP_DISTANCE, Optional.empty(), Optional.empty(), 40));
        tactics.put(FALL_BACK, new Tactic(FALL_BACK, Movement.RETREAT, Optional.empty(), Optional.empty(), 30));
        tactics.put(GUARD, new Tactic(GUARD, Movement.HOLD, Optional.empty(), Optional.empty(), 10));
        var movement = new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty());
        var strike = new SkillChoice(STRIKE, ChoiceKind.MYTHIC_SKILL, Optional.of("TPV_Strike"),
                Set.of(PredictValue.MELEE_ATTACK), true, extraCooldownTicks, recordedMelee(movement));
        return new TacticalProfile("guardian_ai", "Guardian", "Defeat the target", tactics, Map.of(STRIKE, strike),
                List.of(), movement,
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing);
    }

    /**
     * Two {@code APPROACH} tactics of priority 60 with declared purposes, as the test profile {@code purpose-shield} of
     * M15.8: {@code press} strikes without {@code BestWhen}, {@code break_guard} goes through shields and works best
     * against a shielding target.
     */
    static TacticalProfile shieldProfile() {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactics.put(SHIELD_PRESS, new Tactic(SHIELD_PRESS, Movement.APPROACH, Optional.of(STRIKE), Optional.empty(), 60));
        tactics.put(BREAK_GUARD, new Tactic(BREAK_GUARD, Movement.APPROACH, Optional.of(GUARD_BREAK), Optional.empty(),
                60));
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(STRIKE, declared(STRIKE, Set.of(), false));
        choices.put(GUARD_BREAK, declared(GUARD_BREAK, Set.of(UseCondition.TARGET_SHIELDING), true));
        return new TacticalProfile("shield_ai", "Guardian", "Break the guard of the target", tactics, choices,
                List.of(), new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing(1));
    }

    private static SkillChoice declared(ChoiceId id, Set<UseCondition> bestWhen, boolean ignoresShield) {
        return new SkillChoice(id, ChoiceKind.MYTHIC_SKILL, Optional.of("TPV_Strike"), Set.of(), true, 0,
                new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(2.5),
                        OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.MEDIUM, bestWhen, Set.of(),
                        ignoresShield, PurposeOrigin.DECLARED));
    }

    /**
     * The melee purpose the retired {@code PurposeInference} attached to {@code MELEE_ATTACK}. The fixture states it
     * explicitly so the recorded requests of M17.0 stay byte for byte; the binder no longer infers it (decision 5).
     */
    private static SkillPurpose recordedMelee(MovementSettings movement) {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(Math.min(movement.meleeReachBlocks(), SkillPurpose.MAX_REACH_BLOCKS)),
                OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.UNRATED, Set.of(), Set.of(), false,
                PurposeOrigin.INFERRED);
    }

    static DecisionTiming timing(int contingencies) {
        return new DecisionTiming(20, 20, 100, 0.5, 0.2, contingencies);
    }

    /** Opens an active session with the four-tactic profile and one contingency per request. */
    MobSession open(int mob) {
        return sessions.registry.open(EngineFixtures.id(mob), profile(timing(1), 0));
    }

    MobSession open(int mob, TacticalProfile profile) {
        return sessions.registry.open(EngineFixtures.id(mob), profile);
    }

    MobSession session(int mob) {
        return sessions.registry.find(EngineFixtures.id(mob)).orElseThrow();
    }

    IntentView view(int mob) {
        return sessions.board.current(EngineFixtures.id(mob));
    }

    void target(int mob, int player) {
        reader.targets.put(EngineFixtures.id(mob), EngineFixtures.player(player));
    }

    /**
     * Makes the capture read the native cooldowns as {@code FactsAssembler} does: a tactic whose skill is cooling down is
     * not eligible at capture time, while it stays among the tactics eligible when ready.
     */
    void captureCooldowns() {
        reader.cooling = (mob, tactic) -> sessions.registry.find(mob).flatMap(session -> session.profile().tactic(tactic)
                .flatMap(Tactic::choice).map(session.profile().choices()::get))
                .filter(choice -> !actions.cooldowns.ready(mob, choice)).isPresent();
    }

    /** Brings the service to {@code READY} without a trial. */
    void ready() {
        var generation = tracker.checkToStart(clock.nanos).orElseThrow();
        tracker.onCheck(generation, new ServiceCheck(ServiceState.READY, "listed"), clock.nanos);
        tracker.onResult(CoordinatorFixture.answered(0), clock.nanos);
    }

    /** Runs {@code count} ticks, 50 ms apart. */
    void run(int count) {
        for (var index = 0; index < count; index++) {
            coordinator.tick();
            clock.tick++;
            clock.nanos += TICK_NANOS;
        }
    }

    /** Runs ticks until the client received {@code requests} requests, at most 200 ticks. */
    DecisionRequest runUntilRequest(int requests) {
        for (var index = 0; index < 200 && client.requests.size() < requests; index++) {
            run(1);
        }
        return client.requests.get(requests - 1);
    }

    /** Completes the request with index {@code index}: each question gets the answer chosen for its situation. */
    void answer(int index, Function<QuestionSpec, Answer> answers) {
        answer(index, answers, MODEL);
    }

    void answer(int index, Function<QuestionSpec, Answer> answers, String model) {
        client.futures.get(index).complete(answered(index, answers, model));
    }

    /** Runs {@code ticks} ticks, answering every new request at once with {@code answers}. */
    void runAnswering(int ticks, Function<QuestionSpec, Answer> answers) {
        for (var index = 0; index < ticks; index++) {
            for (var request = 0; request < client.futures.size(); request++) {
                if (!client.futures.get(request).isDone()) {
                    answer(request, answers);
                }
            }
            run(1);
        }
    }

    /** An answer that gives {@code first} and {@code second} their probabilities and shares the rest. */
    static Answer between(TacticId first, double firstProbability, TacticId second, double secondProbability,
                          double confidence) {
        var probabilities = new LinkedHashMap<TacticId, Double>();
        probabilities.put(first, firstProbability);
        probabilities.put(second, secondProbability);
        var rest = List.of(PRESS, KEEP, FALL_BACK, GUARD).stream()
                .filter(tactic -> !tactic.equals(first) && !tactic.equals(second)).toList();
        for (var tactic : rest) {
            probabilities.put(tactic, (1.0 - firstProbability - secondProbability) / rest.size());
        }
        return new Answer(probabilities, confidence);
    }

    /** The result that answers request {@code index}, without completing its future. */
    DecisionResult.Answered answered(int index, Function<QuestionSpec, Answer> answers, String model) {
        var byKey = new LinkedHashMap<QuestionKey, Answer>();
        client.requests.get(index).questions().forEach(question -> byKey.put(question.key(), answers.apply(question)));
        return new DecisionResult.Answered(byKey, model, 400);
    }

    /** The in-flight identity of request {@code index} as the dispatcher built it, with the given catalog revision. */
    InFlightRequest sent(int index, long catalogRevision) {
        var request = client.requests.get(index);
        var byKey = new LinkedHashMap<QuestionKey, QuestionSpec>();
        request.questions().forEach(question -> byKey.put(question.key(), question));
        var checks = new LinkedHashMap<QuestionKey, FitCheck>();
        request.fitChecks().forEach(check -> checks.put(check.key(), check));
        return new InFlightRequest(request.encounter(), WORLD, request.sequence(), catalogRevision, byKey, checks,
                request.facts().participants().size(), request.facts().captureNanos(), 0, Long.MAX_VALUE, 0, 0);
    }

    /** Hands a result straight to the acceptance of M10 in the current tick, as step 1 of the coordinator does. */
    void deliver(InFlightRequest request, DecisionResult result) {
        steps.accept(new InboxEntry.CompletedRequest(request, result, clock.nanos), clock.tick, clock.nanos);
    }

    /** An answer whose best tactic is {@code best} with probability {@code probability}. */
    static Answer prefer(TacticId best, double probability, double confidence) {
        var probabilities = new LinkedHashMap<TacticId, Double>();
        probabilities.put(best, probability);
        var rest = List.of(PRESS, KEEP, FALL_BACK, GUARD).stream().filter(tactic -> !tactic.equals(best)).toList();
        for (var tactic : rest) {
            probabilities.put(tactic, (1.0 - probability) / rest.size());
        }
        return new Answer(probabilities, confidence);
    }

    /** Answers every {@code NOW} question with {@code now} and every contingency with {@code contingency}. */
    static Function<QuestionSpec, Answer> nowAndContingency(Answer now, Answer contingency) {
        return question -> question.situation() == Situation.NOW ? now : contingency;
    }

    long count(Enum<?> cause) {
        return metrics.count(cause);
    }

    /**
     * Combat reader double: targets, bystanders it perceives, active contexts, health, distances and eligibility are set
     * by each test.
     */
    static final class Reader implements CombatReader {
        final EngineFixtures.TestClock clock;
        final SessionRegistry registry;
        final Map<UUID, UUID> targets = new HashMap<>();
        final Map<UUID, UUID> probeTargets = new HashMap<>();
        final Map<UUID, Set<TacticId>> eligible = new HashMap<>();
        final Map<UUID, DistanceBand> distances = new HashMap<>();
        final Map<UUID, HealthBand> health = new HashMap<>();
        final Set<UUID> unreadable = new HashSet<>();
        final Set<UUID> failing = new HashSet<>();
        final Set<UUID> hiddenTargets = new HashSet<>();
        final Set<UUID> unreachable = new HashSet<>();
        final Set<UUID> bystanders = new LinkedHashSet<>();
        final Map<UUID, Set<ContextId>> contexts = new HashMap<>();
        final Map<UUID, Set<TargetCue>> cues = new HashMap<>();
        final Set<UUID> players = new HashSet<>();
        final Set<UUID> entropic = new HashSet<>();
        BiPredicate<MobKey, TacticId> cooling = (mob, tactic) -> false;
        int captures;
        int probes;

        Reader(EngineFixtures.TestClock clock, SessionRegistry registry) {
            this.clock = clock;
            this.registry = registry;
        }

        /**
         * The world declares a mob a training one when the test marked its session so (PZ-06): a casts-only world now
         * resolves the pressure step, which copies this declaration onto the session.
         */
        @Override
        public boolean declaredTraining(MobKey mob) {
            var session = registry.find(mob.entityId());
            return session.isPresent() && session.get().learning().training();
        }

        /** A target is a player only when the test says so; the default port reads none (PZ-11). */
        @Override
        public boolean playerTarget(UUID entityId) {
            return players.contains(entityId);
        }

        /** A mob file declares {@code PredictorEntropy: true} only when the test says so (PZ-11). */
        @Override
        public boolean declaredEntropy(MobKey mob) {
            return entropic.contains(mob.entityId());
        }

        @Override
        public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context) {
            captures++;
            var mobs = new ArrayList<MobFacts>();
            var players = new LinkedHashSet<UUID>();
            for (var member : members) {
                var target = Optional.ofNullable(targets.get(member.entityId()));
                context.sightings().accept(new MobSighting(member, WORLD, target,
                        target.map(id -> OptionalDouble.of(6.0)).orElseGet(OptionalDouble::empty)));
                var perceived = target.filter(id -> !hiddenTargets.contains(member.entityId()));
                perceived.ifPresent(players::add);
                var relation = perceived.map(id -> new TargetRelation(context.aliasFor().apply(id, ParticipantKind.PLAYER),
                        distances.getOrDefault(member.entityId(), DistanceBand.RANGED), Trend.STEADY,
                        !unreachable.contains(member.entityId()), true, false, false, false));
                var whenReady = eligible.getOrDefault(member.entityId(), Set.of(PRESS, KEEP, FALL_BACK, GUARD));
                var now = new LinkedHashSet<TacticId>(whenReady);
                now.removeIf(tactic -> cooling.test(member, tactic));
                mobs.add(new MobFacts(member, health.getOrDefault(member.entityId(), HealthBand.HEALTHY), relation,
                        contexts.getOrDefault(member.entityId(), Set.of()), now, Set.of(), Set.of(), Map.of(),
                        whenReady));
            }
            players.addAll(bystanders);
            var participants = new ArrayList<ParticipantFacts>();
            for (var player : players) {
                participants.add(new ParticipantFacts(context.aliasFor().apply(player, ParticipantKind.PLAYER), HealthBand.HEALTHY,
                        ArmorBand.LIGHT, cues.getOrDefault(player, Set.of()), participants.size() + 1));
            }
            return Optional.of(new CombatFacts(encounter, clock.tick, clock.nanos, mobs, participants,
                    context.recentEvents(), 0));
        }

        @Override
        public Optional<MobProbe> probe(MobKey mob) {
            probes++;
            if (failing.contains(mob.entityId())) {
                throw new IllegalStateException("probe failed");
            }
            if (unreadable.contains(mob.entityId())) {
                return Optional.empty();
            }
            var target = Optional.ofNullable(probeTargets.getOrDefault(mob.entityId(), targets.get(mob.entityId())));
            return Optional.of(new MobProbe(mob, WORLD, health.getOrDefault(mob.entityId(), HealthBand.HEALTHY), target,
                    target.map(id -> distances.getOrDefault(mob.entityId(), DistanceBand.RANGED))));
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

    /** Goal ownership double: every set installs; the progress of each mob is set by the test. */
    static final class Mobs implements MobControl {
        final Map<UUID, ArrayDeque<MovementProgress>> progress = new HashMap<>();
        final List<MobKey> progressReads = new ArrayList<>();
        final Map<UUID, List<GoalSummary>> goals = new HashMap<>();
        final List<MobKey> goalReads = new ArrayList<>();
        int installs;

        @Override
        public InstallResult ensureInstalled(MobKey key, TacticalProfile profile) {
            installs++;
            return new InstallResult.Installed(3);
        }

        @Override
        public MovementProgress progress(MobKey key) {
            progressReads.add(key);
            var queue = progress.get(key.entityId());
            if (queue == null || queue.isEmpty()) {
                return new MovementProgress(true, true, true, OptionalDouble.empty());
            }
            return queue.size() == 1 ? queue.peek() : queue.poll();
        }

        @Override
        public List<GoalSummary> describeGoals(MobKey key) {
            goalReads.add(key);
            return goals.getOrDefault(key.entityId(), List.of());
        }

        @Override
        public void release(MobKey key) {
        }
    }

    /**
     * Combat actions double: records every call with its thread and answers casts from a script, then {@code CAST}, or
     * {@code NOT_ELIGIBLE} while the native cooldown of the skill runs ({@link NativeCooldowns}, 20 ticks by default);
     * threat changes are also kept with their player and amount.
     */
    static final class Actions implements CombatActions {
        /** Native {@code Cooldown} of every skill of the doubles unless a test sets its own: 1 s. */
        static final int DEFAULT_COOLDOWN_TICKS = 20;
        final NativeCooldowns cooldowns;
        final EngineFixtures.TestClock clock;
        final Map<UUID, List<Long>> castTicks = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        final List<Threat> threats = new ArrayList<>();
        final List<Thread> threads = new ArrayList<>();
        final Map<UUID, ArrayDeque<ExecutionOutcome>> scripted = new HashMap<>();
        final Set<UUID> failing = new HashSet<>();

        Actions(EngineFixtures.TestClock clock) {
            this.clock = clock;
            cooldowns = new NativeCooldowns(clock::currentTick, DEFAULT_COOLDOWN_TICKS);
        }

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            threads.add(Thread.currentThread());
            calls.add("cast " + mob.entityId().getLeastSignificantBits() + " " + choice.id().value() + " " + movement);
            if (failing.contains(mob.entityId())) {
                throw new IllegalStateException("cast failed");
            }
            var queue = scripted.get(mob.entityId());
            var outcome = queue == null || queue.isEmpty()
                    ? (cooldowns.ready(mob, choice) ? ExecutionOutcome.CAST : ExecutionOutcome.NOT_ELIGIBLE)
                    : queue.poll();
            if (outcome == ExecutionOutcome.CAST) {
                cooldowns.cast(mob, choice);
                castTicks.computeIfAbsent(mob.entityId(), id -> new ArrayList<>()).add(clock.tick);
            }
            return outcome;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            threads.add(Thread.currentThread());
            return cooldowns.ready(mob, choice);
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
            threads.add(Thread.currentThread());
            calls.add("signal " + mob.entityId().getLeastSignificantBits() + " " + tactic.value());
        }

        @Override
        public void addThreat(MobKey mob, UUID playerId, double amount) {
            threads.add(Thread.currentThread());
            calls.add("threat " + mob.entityId().getLeastSignificantBits());
            threats.add(new Threat(mob, playerId, amount));
        }

        long casts(int mob) {
            return calls.stream().filter(call -> call.startsWith("cast " + mob + " ")).count();
        }

        /** The ticks at which a mob cast a choice with outcome {@code CAST}, in order. */
        List<Long> castTicks(int mob) {
            return castTicks.getOrDefault(EngineFixtures.id(mob), List.of());
        }

        List<String> signals(int mob) {
            return calls.stream().filter(call -> call.startsWith("signal " + mob + " ")).map(call -> call.substring(
                    call.lastIndexOf(' ') + 1)).toList();
        }

        void script(int mob, ExecutionOutcome... outcomes) {
            scripted.computeIfAbsent(EngineFixtures.id(mob), id -> new ArrayDeque<>()).addAll(List.of(outcomes));
        }
    }

    /** One threat change handed to the port. */
    record Threat(MobKey mob, UUID playerId, double amount) {
    }
}
