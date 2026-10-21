package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;

import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.learn.CalibrationKey;
import akyrion.thepredict.domain.learn.ConfidenceGate;
import akyrion.thepredict.engine.learn.ConfidenceCalibrator;
import akyrion.thepredict.engine.learn.LearnedArtifacts;
import akyrion.thepredict.engine.learn.LearnedLocalPick;
import akyrion.thepredict.engine.learn.ValueTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Turns one due encounter into at most one request with all its questions (M09.8, M16.1, rules I1, I3, T2, T4, T5).
 * Server thread only; its only state is the rate limit of the preparation-failure warning (the time of the last line
 * and the failures not logged since).
 *
 * <p>The encounter is always captured, because the fallback of M10 and the closing rule of M06.4 need fresh facts
 * even when Jev cannot be asked. The steps then run in this order, each ending the dispatch with a counted
 * {@link Outcome}: plan the questions ({@code NO_QUESTIONS} when no member qualifies: an active session with installed
 * goals, no debug intent, a perceived target and two distinct eligible tactics); ask the service tracker
 * ({@code SERVICE_UNAVAILABLE}); take the next sequence; {@link DecisionClient#prepare prepare} the request, which
 * deduplicates the options by their final text ({@code PREPARE_FAILED} or {@code NOTHING_TO_ASK}, both without any
 * reservation, request in flight, result or effect on the service tracker); reserve the tokens of the prepared body
 * ({@code NO_BUDGET}); and send. A request that cannot be prepared (for example a body over the size guard) is a local
 * fault of that encounter, not of the service: it is counted as {@code INVALID_REQUEST} and logged at most once every
 * {@link #PREPARE_FAILURE_LOG_INTERVAL_NANOS} with its short detail and never the body (rule C8), and the other
 * encounters keep asking Jev. A sequence taken and not sent leaves a harmless gap:
 * the dispatcher never dispatches while the encounter has a request in flight, and the acceptance only compares with
 * the last sequence taken. The mobs whose {@code NOW} question is not sent are resolved by the fallback chain in the
 * reactive cycle, like a mob without a question. The callback of the future only deposits the result in the
 * {@link Inbox}. At most {@link #PATH_CHECKS_PER_EVALUATION} members may compute a path per capture, in rotation by an
 * offset kept in the encounter, and none while the service does not allow a send: without Jev the reachability keeps
 * its last known value (M07.4), which neither the closing rule of M06.4 nor the eligibility of M10.6 reads.
 *
 * <p>Once a sequence was taken, the {@link DecisionTap} hears the planned questions with the dispatch decided after the
 * preparation and the reservation attempt ({@code SENT}, {@code NO_BUDGET}, {@code NOTHING_TO_ASK} or
 * {@code PREPARE_FAILED}), before {@code decide}, and then the exact body handed to {@code decide} (M18.4). A client
 * that throws on {@code decide} leaves a planned {@code SENT} without a body: a programming error, counted as
 * {@code CLIENT_ERROR}.
 */
public final class DecisionDispatcher {
    /** Most members allowed to compute a new path in one capture (M07.4). */
    public static final int PATH_CHECKS_PER_EVALUATION = 2;
    /** Detail of a client that threw instead of preparing or returning a future: a programming error. */
    static final String CLIENT_THREW = "the request could not be handed to the client";
    /** Minimum time between two logged preparation failures. */
    public static final long PREPARE_FAILURE_LOG_INTERVAL_NANOS = 10_000_000_000L;

    /** What happened to one due encounter. */
    public enum Outcome {
        /** A request was sent. */
        SENT,
        /** No member could be read. */
        EMPTY_CAPTURE,
        /** No member has a perceived target and two eligible tactics. */
        NO_QUESTIONS,
        /** The service is not ready, is paused or is in its trial with a request in flight. */
        SERVICE_UNAVAILABLE,
        /** A request, token or in-flight limit refused the reservation of the prepared body. */
        NO_BUDGET,
        /** Every question fell in the preparation: no option set kept two distinct texts (M16.1). */
        NOTHING_TO_ASK,
        /**
         * The preparation could not encode the request: counted as {@code INVALID_REQUEST}, with no effect on the
         * service tracker, and the mobs of the encounter follow the local fallback (M16.1).
         */
        PREPARE_FAILED,
        /** The client threw instead of returning a future: a programming error, counted as {@code INVALID_REQUEST}. */
        CLIENT_ERROR,
        /** An exact cache hit was applied without {@code decide} (M21.7). */
        CACHE_HIT
    }

    private final EncounterCapture capture;
    private final SessionRegistry sessions;
    private final RequestBudget budget;
    private final ServiceStatusTracker tracker;
    private final InFlightRegistry inFlight;
    private final DecisionClient client;
    private final Inbox<InboxEntry> inbox;
    private final DecisionMetrics metrics;
    private final ServerClock clock;
    private final LongSupplier catalogRevision;
    private final EngineSettings settings;
    private final Consumer<String> warnings;
    private final DecisionTap tap;
    private LearningSettings learningSettings;
    private akyrion.thepredict.domain.distill.DistillSettings distillSettings;
    private akyrion.thepredict.engine.distill.TeachingBoard teaching =
            akyrion.thepredict.engine.distill.TeachingBoard.NONE;
    private akyrion.thepredict.engine.distill.DistillRuntime distillRuntime =
            akyrion.thepredict.engine.distill.DistillRuntime.EMPTY;
    private akyrion.thepredict.engine.distill.DistillControl distillControl;
    private ValueTable offeredTable;
    private LearnedArtifacts.State offeredState = LearnedArtifacts.State.NONE;
    private int offeredProfile;
    private long offeredFine;
    private String offeredVerdict = akyrion.thepredict.domain.learn.LearnedScope.PASS_SIM_ONLY;
    private String offeredScope = "bench";
    private ConfidenceGate confidenceGate = ConfidenceGate.RAW;
    private ConfidenceCalibrator calibrator;
    private CalibrationKey calibrationKey;
    private String calibrationModel = "";
    private double calibrationTarget = Double.NaN;
    private Encounter dispatching;
    private final ResultIntake sameTick;
    private final AnswerCache cache;
    private final AggregateCache aggregates;
    private final Supplier<String> utcDay;
    /** Day used when no adapter supplied a UTC day. Cache hits on this path are not a production run. */
    private static final Supplier<String> UNTIMED_DAY = () -> "1970-01-01";
    private long lastPrepareWarning;
    private boolean prepareWarned;
    private long preparesNotLogged;

    /**
     * Creates the dispatcher.
     *
     * @param capture capture step of M07
     * @param sessions sessions, for the profile of each asked mob
     * @param budget request and token budget
     * @param tracker state of the service
     * @param inFlight requests in flight
     * @param client decision client port
     * @param inbox inbox where callbacks deposit results
     * @param metrics metrics of dispatch time and outcomes
     * @param clock server clock
     * @param catalogRevision current catalog revision (rule I11)
     * @param settings limits and request timeout
     * @param warnings receiver of the rate-limited lines of the preparation failures
     * @param tap listener of each planned request and each body sent (M18.4); {@link DecisionTap#NONE} for none
     */
    public DecisionDispatcher(EncounterCapture capture, SessionRegistry sessions, RequestBudget budget,
                              ServiceStatusTracker tracker, InFlightRegistry inFlight, DecisionClient client,
                              Inbox<InboxEntry> inbox, DecisionMetrics metrics, ServerClock clock,
                              LongSupplier catalogRevision, EngineSettings settings, Consumer<String> warnings,
                              DecisionTap tap) {
        this(capture, sessions, budget, tracker, inFlight, client, inbox, metrics, clock, catalogRevision, settings,
                warnings, tap, AnswerCache.off(metrics), AggregateCache.off(new StorageMetrics()), UNTIMED_DAY, null);
    }

    /**
     * Dispatcher that delivers an already completed future through {@link ResultIntake#acceptNow} instead of the
     * inbox (M20.7). A future that is not done still waits for the next tick.
     *
     * @param capture capture step of M07
     * @param sessions sessions, for the profile of each asked mob
     * @param budget request and token budget
     * @param tracker state of the service
     * @param inFlight requests in flight
     * @param client decision client port
     * @param inbox inbox where callbacks deposit results
     * @param metrics metrics of dispatch time and outcomes
     * @param clock server clock
     * @param catalogRevision current catalog revision (rule I11)
     * @param settings limits and request timeout
     * @param warnings receiver of the rate-limited lines of the preparation failures
     * @param tap listener of each planned request and each body sent (M18.4); {@link DecisionTap#NONE} for none
     * @param sameTick intake of the coordinator; the constructor without it keeps the inbox path (AE-12)
     * @param cache answer cache; {@code off} never serves a stored answer
     * @param aggregates outcome cache told when a window closes
     * @param utcDay UTC day {@code yyyy-MM-dd} supplied by the adapter; never read from a clock in this class
     */
    public DecisionDispatcher(EncounterCapture capture, SessionRegistry sessions, RequestBudget budget,
                              ServiceStatusTracker tracker, InFlightRegistry inFlight, DecisionClient client,
                              Inbox<InboxEntry> inbox, DecisionMetrics metrics, ServerClock clock,
                              LongSupplier catalogRevision, EngineSettings settings, Consumer<String> warnings,
                              DecisionTap tap, ResultIntake sameTick, AnswerCache cache, AggregateCache aggregates,
                              Supplier<String> utcDay) {
        this(capture, sessions, budget, tracker, inFlight, client, inbox, metrics, clock, catalogRevision, settings,
                warnings, tap, cache, aggregates, utcDay, Objects.requireNonNull(sameTick, "sameTick"));
    }

    /** The constructors meet here; only the one without an intake passes a missing {@code sameTick}. */
    private DecisionDispatcher(EncounterCapture capture, SessionRegistry sessions, RequestBudget budget,
                               ServiceStatusTracker tracker, InFlightRegistry inFlight, DecisionClient client,
                               Inbox<InboxEntry> inbox, DecisionMetrics metrics, ServerClock clock,
                               LongSupplier catalogRevision, EngineSettings settings, Consumer<String> warnings,
                               DecisionTap tap, AnswerCache cache, AggregateCache aggregates, Supplier<String> utcDay,
                               ResultIntake sameTick) {
        this.capture = Objects.requireNonNull(capture, "capture");
        this.sameTick = sameTick;
        this.cache = Objects.requireNonNull(cache, "cache");
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.utcDay = Objects.requireNonNull(utcDay, "utcDay");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.inFlight = Objects.requireNonNull(inFlight, "inFlight");
        this.client = Objects.requireNonNull(client, "client");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.catalogRevision = Objects.requireNonNull(catalogRevision, "catalogRevision");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
        this.tap = Objects.requireNonNull(tap, "tap");
    }

    /**
     * Dispatcher of M20.7 without a cache. The cache stays off.
     *
     * @param capture capture step of M07
     * @param sessions sessions
     * @param budget request and token budget
     * @param tracker state of the service
     * @param inFlight requests in flight
     * @param client decision client port
     * @param inbox inbox where callbacks deposit results
     * @param metrics metrics of dispatch time and outcomes
     * @param clock server clock
     * @param catalogRevision current catalog revision
     * @param settings limits and request timeout
     * @param warnings receiver of the rate-limited lines
     * @param tap listener of each planned request
     * @param sameTick intake of the coordinator; the constructor without it keeps the inbox path (AE-12)
     */
    public DecisionDispatcher(EncounterCapture capture, SessionRegistry sessions, RequestBudget budget,
                              ServiceStatusTracker tracker, InFlightRegistry inFlight, DecisionClient client,
                              Inbox<InboxEntry> inbox, DecisionMetrics metrics, ServerClock clock,
                              LongSupplier catalogRevision, EngineSettings settings, Consumer<String> warnings,
                              DecisionTap tap, ResultIntake sameTick) {
        this(capture, sessions, budget, tracker, inFlight, client, inbox, metrics, clock, catalogRevision, settings,
                warnings, tap, sameTick, AnswerCache.off(metrics), AggregateCache.off(new StorageMetrics()),
                UNTIMED_DAY);
    }

    /**
     * Installs the learning section used to arm a capture. Until one is installed every capture stays unarmed.
     *
     * @param settings the learning section
     */
    public void useLearning(LearningSettings settings) {
        learningSettings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Installs distillation. {@code off} does not change a capture. The board receives teaching rows.
     *
     * @param settings the distill section
     * @param board the teaching board shared with the intake
     */
    public void useDistill(akyrion.thepredict.domain.distill.DistillSettings settings,
                           akyrion.thepredict.engine.distill.TeachingBoard board) {
        distillSettings = Objects.requireNonNull(settings, "settings");
        teaching = Objects.requireNonNull(board, "board");
    }

    /**
     * Installs the coverage runtime. A model change marks it stale through the acceptor.
     *
     * @param runtime the runtime
     */
    public void useRuntime(akyrion.thepredict.engine.distill.DistillRuntime runtime) {
        distillRuntime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * Installs the live mode ceiling. Disable and {@code distill mode} apply on the next capture.
     *
     * @param control the control shared with the command
     */
    public void useControl(akyrion.thepredict.engine.distill.DistillControl control) {
        distillControl = Objects.requireNonNull(control, "control");
    }

    /**
     * The distill section of the next capture, lowered by the live control when one is installed.
     *
     * @return the section, or null when distillation was not installed
     */
    akyrion.thepredict.domain.distill.DistillSettings distillForCapture() {
        if (distillSettings == null || distillControl == null) {
            return distillSettings;
        }
        return distillSettings.withMode(distillControl.effective(distillSettings.mode()));
    }

    /**
     * Offers a value table. It becomes active on the next capture only when {@code state} is {@code ACTIVE}.
     *
     * @param table the table
     * @param state the artifact state
     * @param profile profile code
     * @param fine packed fine context
     */
    public void offerTable(ValueTable table, LearnedArtifacts.State state, int profile, long fine) {
        offerTable(table, state, akyrion.thepredict.domain.learn.LearnedScope.PASS_SIM_ONLY, "bench", profile, fine);
    }

    /**
     * Offers a value table with the verdict of its fingerprint and {@code learning.scope} (A16, LD-09). It becomes
     * active on the next capture only when {@code state} is {@code ACTIVE}, and it acts with a player present only
     * with {@code PASS} and the scope {@code all}.
     *
     * @param table the table
     * @param state the artifact state
     * @param verdict status of {@code GateVerdicts} for the artifact
     * @param scope {@code learning.scope}
     * @param profile profile code
     * @param fine packed fine context
     */
    public void offerTable(ValueTable table, LearnedArtifacts.State state, String verdict, String scope, int profile,
                           long fine) {
        offeredTable = table;
        offeredState = state;
        offeredVerdict = java.util.Objects.requireNonNull(verdict, "verdict");
        offeredScope = java.util.Objects.requireNonNull(scope, "scope");
        offeredProfile = profile;
        offeredFine = fine;
    }

    /**
     * Installs {@code c*} for a calibrated gate. A raw gate is stored and does not move the profile floor.
     *
     * @param gate the confidence gate
     * @param model the calibrator
     * @param key the calibration key
     * @param liveModel the live model
     * @param target the parity target
     */
    public void useCalibrator(ConfidenceGate gate, ConfidenceCalibrator model, CalibrationKey key, String liveModel,
                              double target) {
        confidenceGate = gate;
        calibrator = model;
        calibrationKey = key;
        calibrationModel = liveModel;
        calibrationTarget = target;
    }

    private void installLearning(Encounter encounter) {
        if (calibrator == null) {
            return;
        }
        for (var session : encounter.memberSessions()) {
            session.learning().useCalibrator(confidenceGate, calibrator, calibrationKey, calibrationModel,
                    calibrationTarget);
        }
    }

    /**
     * Records a shadow skip and a shadow hint. The prepared body is already fixed and is not changed.
     */
    private void annotate() {
        var encounter = dispatching;
        if (encounter == null || learningSettings == null) {
            return;
        }
        var voi = "";
        var hint = "";
        for (var session : encounter.memberSessions()) {
            if (voi.isEmpty() && session.learning().wouldSkip() && !"off".equals(learningSettings.voiSkip())) {
                voi = "would_skip";
            }
            if (hint.isEmpty()) {
                hint = hint(session);
            }
        }
        if (!voi.isEmpty() || !hint.isEmpty()) {
            tap.noteAnnotation(voi, hint);
        }
        noteDistill(encounter);
    }

    private void noteDistill(Encounter encounter) {
        var distillSettings = distillForCapture();
        if (distillSettings == null || distillSettings.mode() == akyrion.thepredict.domain.distill.DistillMode.OFF
                && !distillSettings.collect()) {
            return;
        }
        var note = DistillNote.planned(distillSettings, encounter);
        if (!note.distill().isEmpty() || !note.patternFacts().isEmpty()) {
            tap.noteDistill(note.distill(), note.patternFacts());
        }
    }

    private String hint(MobSession session) {
        if (learningSettings == null || !"shadow".equals(learningSettings.jevHint())
                || session.candidateScoring() != CandidateScoring.LEARNED || !session.learning().tableActive()) {
            return "";
        }
        var tactics = new ArrayList<>(session.profile().tactics().values());
        var ones = new double[tactics.size()];
        java.util.Arrays.fill(ones, 1.0);
        var lead = CellLookup.lead(session, tactics, false, learningSettings.minWindowsForValue(),
                learningSettings.minValueSpreadMillis(), ones);
        return lead.map(id -> id.value() + " has worked best here").orElse("");
    }

    /**
     * Captures an open encounter without a request in flight and sends its request when allowed.
     *
     * <p>The send time is read from the clock just before the reservation, so {@code ROUNDTRIP} starts at the real send.
     *
     * @param encounter due encounter
     * @return what happened, also counted in {@link DecisionMetrics}
     */
    public Outcome dispatch(Encounter encounter) {
        if (encounter.schedule().requestInFlight()) {
            throw new IllegalStateException("encounter " + encounter.id().value() + " already has a request in flight");
        }
        dispatching = encounter;
        cache.noteCatalog(catalogRevision.getAsLong());
        snapshot(encounter);
        var facts = capture.capture(encounter.id(), pathChecks(encounter));
        if (facts.isEmpty()) {
            return counted(Outcome.EMPTY_CAPTURE);
        }
        var captured = publishZone(facts.get());
        var plan = EncounterQuestions.plan(captured, this::activeProfile, this::currentTactic,
                settings.maxOptionsPerQuestion(), settings.pruning(), settings.zone().mode());
        installLearning(encounter);
        CaptureArm.prepare(encounter, offeredTable, offeredState, offeredVerdict, offeredScope, offeredProfile,
                offeredFine);
        plan = LearnedOrder.apply(encounter, plan);
        plan = CaptureGate.apply(encounter, plan, clock.currentTick(), learningSettings, tap, distillForCapture(),
                teaching, distillRuntime, clock.nanoTime() / 60_000_000_000L);
        recordPruning(encounter, plan);
        settleExplored(encounter);
        if (plan.questions().isEmpty() && !captured.brief().asksPlan()) {
            hearPrunes(encounter.id(), 0, plan);
            return counted(Outcome.NO_QUESTIONS);
        }
        if (!tracker.allowsDispatch(inFlight.size())) {
            return counted(Outcome.SERVICE_UNAVAILABLE);
        }
        var sequence = encounter.schedule().nextSequence();
        var request = new DecisionRequest(encounter.id(), sequence, captured, plan.questions(), plan.profiles(),
                plan.fitChecks());
        return counted(switch (prepare(request)) {
            case Preparation.Ready ready -> reserveAndSend(encounter, ready, plan, request);
            case Preparation.NothingToAsk nothing -> unsent(plan, request, nothing, Outcome.NOTHING_TO_ASK);
            case Preparation.Invalid invalid -> {
                unsent(plan, request, invalid, Outcome.PREPARE_FAILED);
                yield notPrepared(encounter, invalid.detail());
            }
        });
    }

    /** Stores the signature of this capture. A later capture does not replace an intent's saved copy. */
    private void snapshot(Encounter encounter) {
        for (var member : encounter.memberSessions()) {
            member.noteCaptureSignature(LiveSignature.of(member));
        }
    }

    /**
     * Counts every dropped option and remembers it on the session for {@code inspect}. The tactic a {@code NOW}
     * question resolved, when one option remains, is what the local planner may cast; a later contingency does not
     * replace it, and pruning off clears it.
     */
    private void recordPruning(Encounter encounter, EncounterQuestions.Plan plan) {
        for (var member : encounter.memberSessions()) {
            member.clearResolvedTactic();
        }
        var seen = new HashSet<java.util.UUID>();
        for (var notice : plan.prunes()) {
            notice.result().dropped().values().forEach(reason -> metrics.pruned(reason, 1));
            if (notice.result().vetoesUndone()) {
                metrics.add(DecisionMetrics.ReadyCounter.PRUNE_UNDONE, 1);
            }
            if (notice.resolved()) {
                metrics.add(DecisionMetrics.ReadyCounter.RESOLVED_BY_PRUNING, 1);
            }
            sessions.find(notice.mob()).ifPresent(session -> {
                session.notePruned(notice.result().dropped());
                if (seen.add(notice.mob().entityId()) && notice.resolved() && notice.result().kept().size() == 1) {
                    session.noteResolvedTactic(notice.result().kept().get(0));
                }
            });
        }
    }

    /**
     * Settles the sampled index after pruning has cleared the tactic. Inside the stratum the cast is that index.
     */
    private void settleExplored(Encounter encounter) {
        for (var session : encounter.memberSessions()) {
            var learning = session.learning();
            var choice = learning.exploreChoice();
            if (choice.isEmpty() || !choice.get().inStratum() || learning.explore().acceptedJev()) {
                continue;
            }
            var picked = choice.get();
            var options = learning.exploreOptions();
            if (picked.index() < 0 || picked.index() >= options.size()) {
                continue;
            }
            session.noteResolvedTactic(options.get(picked.index()));
            tap.explored(session.key(), picked.behavior(), picked.index());
        }
    }

    /** Tells the tap which options the prune removed, once the sequence of the request is known. */
    private void hearPrunes(akyrion.thepredict.domain.EncounterId encounter, long sequence,
                            EncounterQuestions.Plan plan) {
        for (var notice : plan.prunes()) {
            tap.optionsPruned(encounter, sequence, notice.mob(), notice.result());
        }
    }

    /** A request that is not sent: nothing to ask counts its merged options; the tap hears the plan of both. */
    private Outcome unsent(EncounterQuestions.Plan plan, DecisionRequest request, Preparation preparation,
                           Outcome outcome) {
        if (outcome == Outcome.NOTHING_TO_ASK) {
            metrics.add(DecisionMetrics.Counter.OPTIONS_MERGED, merged(plan, List.of()));
        }
        annotate();
        tap.questionsPlanned(request.encounter(), request.sequence(), plan.questions(), preparation, outcome,
                request.facts());
        hearPrunes(request.encounter(), request.sequence(), plan);
        return outcome;
    }

    /** Prepares on the server thread and measures it; a client that throws is a programming error. */
    private Preparation prepare(DecisionRequest request) {
        var started = clock.nanoTime();
        try {
            return client.prepare(request);
        } catch (RuntimeException exception) {
            return new Preparation.Invalid(CLIENT_THREW);
        } finally {
            var end = clock.nanoTime();
            metrics.record(DecisionMetrics.Span.PREPARE, end - started, end);
        }
    }

    private Outcome reserveAndSend(Encounter encounter, Preparation.Ready ready, EncounterQuestions.Plan plan,
                                   DecisionRequest planned) {
        var prepared = ready.prepared();
        metrics.add(DecisionMetrics.Counter.OPTIONS_MERGED, merged(plan, prepared.sent().questions()));
        var cached = lookup(encounter, planned, prepared, ready, plan);
        if (cached != null) {
            return cached;
        }
        var tokens = budget.tokensFor(prepared.bodyChars());
        var sentAt = clock.nanoTime();
        var reserved = budget.tryReserve(encounter.worldId(), encounter.id(), tokens, sentAt);
        annotate();
        tap.questionsPlanned(encounter.id(), planned.sequence(), plan.questions(), ready,
                reserved ? Outcome.SENT : Outcome.NO_BUDGET, planned.facts());
        hearPrunes(encounter.id(), planned.sequence(), plan);
        if (!reserved) {
            return Outcome.NO_BUDGET;
        }
        var request = inFlight(encounter, prepared, tokens, sentAt);
        CompletableFuture<DecisionResult> future;
        try {
            future = client.decide(prepared);
        } catch (RuntimeException exception) {
            future = null;
        }
        if (future == null) {
            budget.complete(request, OptionalInt.empty(), sentAt);
            return failed(CLIENT_THREW, Outcome.CLIENT_ERROR);
        }
        tap.requestBody(encounter.id(), planned.sequence(), prepared.body());
        track(encounter, prepared.sent(), request, future, sentAt);
        return Outcome.SENT;
    }

    /**
     * Measures a sent request, keeps it in flight and makes its future deposit the result in the inbox. A future
     * that is already complete is accepted here, on the server thread and outside any callback (rules T4, T5), so a
     * failure of the acceptance reaches the step boundary of the coordinator (rule C7). {@code join} on a complete
     * future never blocks.
     */
    private void track(Encounter encounter, DecisionRequest sent, InFlightRequest request,
                       CompletableFuture<DecisionResult> future, long sentAt) {
        var end = clock.nanoTime();
        metrics.record(DecisionMetrics.Span.DISPATCH, end - sentAt, end);
        metrics.add(DecisionMetrics.Counter.CONTINGENCIES_ASKED,
                sent.questions().stream().filter(question -> question.situation() != Situation.NOW).count());
        inFlight.add(encounter, request, future);
        if (sameTick != null && future.isDone()) {
            var result = future.handle((value, failure) -> value != null ? value : failure(failure)).join();
            sameTick.acceptNow(new InboxEntry.CompletedRequest(request, result, clock.nanoTime()),
                    clock.currentTick(), clock.nanoTime());
            return;
        }
        future.whenComplete((result, failure) -> inbox.offer(new InboxEntry.CompletedRequest(request,
                result != null ? result : failure(failure), clock.nanoTime())));
    }

    /** The identity of a prepared request: only what is sent, with its size for the budget (M16.1). */
    private InFlightRequest inFlight(Encounter encounter, PreparedRequest prepared, int tokens, long sentAt) {
        var sent = prepared.sent();
        var questions = new LinkedHashMap<QuestionKey, QuestionSpec>();
        sent.questions().forEach(question -> questions.put(question.key(), question));
        var checks = new LinkedHashMap<QuestionKey, FitCheck>();
        sent.fitChecks().forEach(check -> checks.put(check.key(), check));
        return new InFlightRequest(encounter.id(), encounter.worldId(), sent.sequence(), catalogRevision.getAsLong(),
                questions, checks, sent.facts().participants().size(), sent.facts().captureNanos(), sentAt,
                sentAt + settings.watchdogNanos(), tokens, prepared.bodyChars(), clock.currentTick(),
                prepared.body());
    }

    /**
     * Consults the answer cache after {@code prepare} and before the budget. A pure hit does not call {@code decide}.
     * A missing same-tick intake is treated as a miss. Shadow and revalidation continue the send.
     *
     * @return {@link Outcome#CACHE_HIT}, or {@code null} when the send continues
     */
    private Outcome lookup(Encounter encounter, DecisionRequest planned, PreparedRequest prepared,
                           Preparation.Ready ready, EncounterQuestions.Plan plan) {
        var keys = new HashSet<QuestionKey>();
        prepared.sent().questions().forEach(question -> keys.add(question.key()));
        var looked = cache.lookup(cache.modelName(), prepared.body(), keys, clock.currentTick());
        if (!(looked instanceof AnswerCache.Lookup.Found found)) {
            if (!(looked instanceof AnswerCache.Lookup.Off)) {
                noteCache(encounter.id(), planned.sequence(), looked);
            }
            return null;
        }
        AnswerCache.first(found.answered()).ifPresent(best -> cache.noteShadow(encounter.id(), planned.sequence(),
                best.getKey(), best.getValue(), clock.currentTick() - found.originTick()));
        if (found.alsoSend() || sameTick == null) {
            noteCache(encounter.id(), planned.sequence(), looked);
            return null;
        }
        var now = clock.nanoTime();
        annotate();
        tap.questionsPlanned(encounter.id(), planned.sequence(), plan.questions(), ready, Outcome.CACHE_HIT,
                planned.facts());
        hearPrunes(encounter.id(), planned.sequence(), plan);
        var request = new InFlightRequest(encounter.id(), encounter.worldId(), planned.sequence(),
                catalogRevision.getAsLong(), questionsOf(prepared), checksOf(prepared),
                planned.facts().participants().size(), planned.facts().captureNanos(), now, now, 0,
                prepared.bodyChars(), clock.currentTick(), prepared.body());
        sameTick.acceptCached(request, found.answered().fromCache(), clock.currentTick(), now);
        noteCache(encounter.id(), planned.sequence(), looked);
        aggregates.addDecision(new DecisionDailyDelta(utcDay.get(), cache.modelName().isBlank() ? "-" : cache.modelName(),
                "-", cache.questionDetail(), 1, 0, 0, 0, 1, Math.max(0, found.answered().inputTokens()), 0, 0));
        return Outcome.CACHE_HIT;
    }

    private void noteCache(EncounterId encounter, long sequence, AnswerCache.Lookup looked) {
        if (!tap.recording() || !cache.active()) {
            return;
        }
        var hit = looked instanceof AnswerCache.Lookup.Found;
        var tokens = hit ? ((AnswerCache.Lookup.Found) looked).answered().inputTokens() : 0;
        tap.answerCache(encounter, sequence, new akyrion.thepredict.domain.AnswerCacheEvent(cache.mode(), hit, 0,
                false, 0.0, tokens));
    }

    private Map<QuestionKey, QuestionSpec> questionsOf(PreparedRequest prepared) {
        var questions = new LinkedHashMap<QuestionKey, QuestionSpec>();
        prepared.sent().questions().forEach(question -> questions.put(question.key(), question));
        return questions;
    }

    private Map<QuestionKey, FitCheck> checksOf(PreparedRequest prepared) {
        var checks = new LinkedHashMap<QuestionKey, FitCheck>();
        prepared.sent().fitChecks().forEach(check -> checks.put(check.key(), check));
        return checks;
    }

    /**
     * A request that could not be prepared: a local fault of this encounter, counted and logged without the body, with
     * no reservation, request in flight or effect on the service tracker, so the other encounters keep asking Jev.
     */
    private Outcome notPrepared(Encounter encounter, String detail) {
        metrics.increment(FailureKind.INVALID_REQUEST);
        var now = clock.nanoTime();
        if (prepareWarned && now - lastPrepareWarning < PREPARE_FAILURE_LOG_INTERVAL_NANOS) {
            preparesNotLogged++;
            return Outcome.PREPARE_FAILED;
        }
        var skipped = preparesNotLogged == 0 ? "" : " (" + preparesNotLogged + " more since the last line)";
        warnings.accept("The request of encounter " + encounter.id().value() + " was not sent, its mobs use the local "
                + "fallback: " + detail + skipped);
        prepareWarned = true;
        lastPrepareWarning = now;
        preparesNotLogged = 0;
        return Outcome.PREPARE_FAILED;
    }

    private CombatFacts publishZone(CombatFacts captured) {
        var mobs = new ArrayList<MobFacts>(captured.mobs().size());
        for (var mob : captured.mobs()) {
            var stored = sessions.find(mob.mob()).flatMap(MobSession::lastMobFacts);
            mobs.add(stored.orElse(mob));
        }
        return captured.withMobs(mobs);
    }

    /** A client that threw on send: no request in flight, the effect of an encoding failure on the tracker. */
    private Outcome failed(String detail, Outcome outcome) {
        metrics.increment(FailureKind.INVALID_REQUEST);
        tracker.onResult(new DecisionResult.Failed(FailureKind.INVALID_REQUEST, Optional.empty(), detail),
                clock.nanoTime());
        return outcome;
    }

    /**
     * Options planned but not sent because they read the same as another: the difference in each question sent, and,
     * for each question dropped by itself, all its options but one. A contingency dropped with the {@code NOW} of its
     * mob is not counted, because it was never compared.
     */
    private static long merged(EncounterQuestions.Plan plan, List<QuestionSpec> sent) {
        var sentByKey = new HashMap<QuestionKey, QuestionSpec>();
        sent.forEach(question -> sentByKey.put(question.key(), question));
        var askedMobs = new HashSet<MobKey>();
        sent.forEach(question -> askedMobs.add(question.mob()));
        var merged = 0L;
        for (var planned : plan.questions()) {
            var kept = sentByKey.get(planned.key());
            if (kept != null) {
                merged += planned.options().size() - kept.options().size();
            } else if (planned.situation() == Situation.NOW || askedMobs.contains(planned.mob())) {
                merged += planned.options().size() - 1;
            }
        }
        return merged;
    }

    /**
     * The members allowed to compute a path in this capture: none while Jev cannot be asked, otherwise the next
     * {@link #PATH_CHECKS_PER_EVALUATION} members after the encounter's own offset, which then moves past them, so
     * every member computes a path within {@code ceil(members / 2)} evaluations whatever the heartbeat.
     */
    private Set<MobKey> pathChecks(Encounter encounter) {
        var members = encounter.members();
        var allowed = new HashSet<MobKey>();
        if (members.isEmpty() || !tracker.allowsDispatch(inFlight.size())) {
            return allowed;
        }
        var count = Math.min(PATH_CHECKS_PER_EVALUATION, members.size());
        var start = Math.floorMod(encounter.schedule().pathOffset(), members.size());
        for (var offset = 0; offset < count; offset++) {
            allowed.add(members.get((start + offset) % members.size()));
        }
        encounter.schedule().advancePathOffset(count, members.size());
        return allowed;
    }

    /**
     * The profile of an active session whose goals are installed and that no debug intent holds: only a mob ThePredict
     * can move is asked about, and a debug intent never causes a request (M12.3).
     */
    private Optional<TacticalProfile> activeProfile(MobKey key) {
        return sessions.find(key).filter(session -> session.state() == SessionState.ACTIVE && !session.installPending()
                && session.installedRevision().isPresent() && !session.debugIntent()).map(MobSession::profile);
    }

    /** The tactic of the current intent of a session, which the options always keep (M16.1). */
    private Optional<TacticId> currentTactic(MobKey key) {
        return sessions.find(key).flatMap(MobSession::intent).map(TacticalIntent::tactic);
    }

    private Outcome counted(Outcome outcome) {
        metrics.increment(outcome);
        return outcome;
    }

    /** A future never completes exceptionally by contract; a cancellation is a local failure (M09.2). */
    private static DecisionResult failure(Throwable failure) {
        var cancelled = failure instanceof CancellationException
                || failure != null && failure.getCause() instanceof CancellationException;
        return new DecisionResult.Failed(FailureKind.NETWORK, Optional.empty(),
                cancelled ? "cancelled" : "the decision future failed");
    }
}
