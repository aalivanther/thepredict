package akyrion.thepredict;

import akyrion.thepredict.config.DecisionSettings;
import akyrion.thepredict.config.JevSettings;
import akyrion.thepredict.config.StorageSettings;
import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ZoneCaps;
import akyrion.thepredict.engine.AggregateCache;
import akyrion.thepredict.engine.AnswerCache;
import akyrion.thepredict.engine.CombatActions;
import akyrion.thepredict.engine.ContextSignals;
import akyrion.thepredict.engine.Coordinator;
import akyrion.thepredict.engine.DebugIntents;
import akyrion.thepredict.engine.DecisionClient;
import akyrion.thepredict.engine.DecisionDispatcher;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.Diagnostics;
import akyrion.thepredict.engine.EngineSettings;
import akyrion.thepredict.engine.ForwardingAggregateStore;
import akyrion.thepredict.engine.ExecutionSettings;
import akyrion.thepredict.engine.InFlightRegistry;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.MobControl;
import akyrion.thepredict.engine.RequestBudget;
import akyrion.thepredict.engine.ResultIntake;
import akyrion.thepredict.engine.ServerClock;
import akyrion.thepredict.engine.ServiceStatusTracker;
import akyrion.thepredict.engine.SessionUpkeep;
import akyrion.thepredict.engine.StorageMetrics;
import akyrion.thepredict.engine.TacticalExecution;

import java.util.Objects;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * Composition of the M09 coordinator over the M06 and M07 components of {@link SessionServices}: budget, service
 * tracker, inbox, requests in flight, intake, upkeep, dispatcher, the tactical execution of M10, the coordinator
 * (M09.7) and the diagnostics and debug intents of {@code /thepredict} (M12). Built only by {@link ThePredictPlugin}
 * (rule D8), once per plugin start, so a profile reload never resets the budget. Server thread only.
 */
final class DecisionServices {
    /** Storage replies applied per tick: no key sets it, and {@code ResultIntake} uses the same number by default. */
    static final int STORAGE_REPLIES_PER_TICK = 4;

    private final ServerClock clock;
    private final ServiceStatusTracker tracker;
    private final ResultIntake intake;
    private final Coordinator coordinator;
    private final TacticalExecution steps;
    private final Diagnostics diagnostics;
    private final AnswerCache answers;
    private final AggregateCache aggregates;
    private final ForwardingAggregateStore store;
    private final Inbox<InboxEntry> inbox;
    private final SessionUpkeep upkeep;

    /**
     * Builds the coordinator; nothing runs until {@link #tick()}.
     *
     * @param sessions M06 and M07 components; its {@code tick} is the lifecycle step
     * @param wiring what the coordinator needs from the root besides the sessions
     */
    DecisionServices(SessionServices sessions, Wiring wiring) {
        var settings = wiring.settings();
        this.clock = wiring.clock();
        var metrics = sessions.decisionMetrics();
        var budget = new RequestBudget(settings);
        tracker = new ServiceStatusTracker(wiring.initial(), settings.watchdogNanos(), wiring.random(), wiring.info());
        inbox = new Inbox<>(settings.inboxCapacity());
        var inFlight = new InFlightRegistry();
        answers = new AnswerCache(settings.answerCache(), settings.answerCacheTtlTicks(), settings.answerCacheEntries(),
                settings.answerCacheRevalidateRatio(), 0L, settings.maxInFlightRequests() + 8, metrics);
        store = new ForwardingAggregateStore();
        aggregates = new AggregateCache(store, new StorageMetrics(), settings.maxPendingKeys(),
                settings.flushIntervalTicks(), settings.maxStorageRepliesPerTick(), inbox);
        steps = new TacticalExecution(new TacticalExecution.Parts(sessions.registry(), sessions.encounters(),
                sessions.agenda(), wiring.board(), sessions.reader(), wiring.control(), tracker, budget, metrics,
                wiring.catalogRevision(), sessions.threat(), sessions.outcomes(), wiring.tap()), wiring.actions(),
                wiring.execution(),
                wiring.info(),
                settings.fitThresholds());
        steps.usePruning(settings.pruning());
        intake = new ResultIntake(inbox, inFlight, budget, tracker, wiring.client(), metrics, steps, clock,
                wiring.tap(), answers, ignored -> {
                }, settings.maxStorageRepliesPerTick());
        sessions.registry().difficulty(settings.difficulty());
        sessions.registry().candidateScoring(settings.candidateScoring());
        upkeep = new SessionUpkeep(sessions.registry(), sessions.encounters(), sessions.agenda(), wiring.board(),
                wiring.signals(), sessions.reader(), wiring.control(), wiring.catalogRevision(), steps, metrics);
        var dispatcher = new DecisionDispatcher(sessions.capture(), sessions.registry(), budget, tracker, inFlight,
                wiring.client(), inbox, metrics, clock, wiring.catalogRevision(), settings, wiring.warnings(),
                wiring.tap(), intake, answers, aggregates,
                () -> LocalDate.now(ZoneOffset.UTC).toString());
        coordinator = new Coordinator(settings, clock, intake, upkeep, dispatcher, steps, sessions.agenda(),
                sessions::tick, metrics, wiring.warnings());
        diagnostics = new Diagnostics(new Diagnostics.Sources(sessions.registry(), sessions.encounters(),
                sessions.sessionMetrics(), sessions.lifecycle(), sessions.contextEvents(), wiring.signals(), metrics,
                sessions.threat(), budget, settings, tracker, inFlight, intake, wiring.control(), sessions.reader(),
                clock, wiring.modelLabel(), sessions.outcomes().metrics()));
    }

    /**
     * Translates {@code decision.*} and {@code jev.timeoutMillis} into the engine limits, the option cap and the fit
     * thresholds (rule D10).
     *
     * @param decision validated decision settings
     * @param jev validated Jev settings
     * @return the engine settings
     */
    static EngineSettings engineSettings(DecisionSettings decision, JevSettings jev) {
        return new EngineSettings(decision.maxRequestsPerMinute(), decision.maxRequestsPerMinutePerWorld(),
                decision.maxInFlightRequests(), decision.maxInFlightRequestsPerWorld(), decision.maxInputTokensPerMinute(),
                decision.maxInstallsPerTick(), decision.maxEncounterEvaluationsPerTick(), decision.maxProbesPerTick(),
                decision.maxCompletionsPerTick(), decision.maxSignalsPerTick(), decision.maxTickWorkMicros(),
                jev.timeout().toNanos(), decision.questions().maxOptionsPerQuestion(),
                decision.questions().fitThresholds(), decision.maxSessionsPerTick(), decision.tickLayerMaxMicros(),
                decision.guardMspt(), decision.microMinHoldTicks(), decision.waypointRefreshTicks(),
                decision.maxWaypointPathsPerTick(), decision.maxWaypointPathMicrosPerTick(),
                decision.candidateScoring(), decision.candidateDiversity(), decision.pruning());
    }

    /**
     * Translates decision, Jev and storage settings. The answer cache and the flush interval come from storage.
     *
     * @param decision validated decision settings
     * @param jev validated Jev settings
     * @param storage validated storage settings
     * @return the engine settings
     */
    static EngineSettings engineSettings(DecisionSettings decision, JevSettings jev, StorageSettings storage) {
        var base = engineSettings(decision, jev);
        return new EngineSettings(base.maxRequestsPerMinute(), base.maxRequestsPerMinutePerWorld(),
                base.maxInFlightRequests(), base.maxInFlightRequestsPerWorld(), base.maxInputTokensPerMinute(),
                base.maxInstallsPerTick(), base.maxEncounterEvaluationsPerTick(), base.maxProbesPerTick(),
                base.maxCompletionsPerTick(), base.maxSignalsPerTick(), base.maxTickWorkMicros(),
                base.requestTimeoutNanos(), base.maxOptionsPerQuestion(), base.fitThresholds(),
                base.maxSessionsPerTick(), base.tickLayerMaxMicros(), base.guardMspt(), base.microMinHoldTicks(),
                base.waypointRefreshTicks(), base.maxWaypointPathsPerTick(), base.maxWaypointPathMicrosPerTick(),
                base.candidateScoring(), base.candidateDiversity(), base.pruning(), storage.flushIntervalTicks(),
                storage.maxPendingKeys(), STORAGE_REPLIES_PER_TICK, storage.answerCache(), storage.answerCacheTtlTicks(),
                storage.answerCacheEntries(), storage.answerCacheRevalidateRatio(), DifficultyGlobals.DEFAULT);
    }

    /**
     * Translates decision, Jev, storage and difficulty settings.
     *
     * @param decision validated decision settings
     * @param jev validated Jev settings
     * @param storage validated storage settings
     * @param difficulty validated difficulty settings
     * @return the engine settings
     */
    static EngineSettings engineSettings(DecisionSettings decision, JevSettings jev, StorageSettings storage,
                                        DifficultyGlobals difficulty) {
        var base = engineSettings(decision, jev, storage);
        return new EngineSettings(base.maxRequestsPerMinute(), base.maxRequestsPerMinutePerWorld(),
                base.maxInFlightRequests(), base.maxInFlightRequestsPerWorld(), base.maxInputTokensPerMinute(),
                base.maxInstallsPerTick(), base.maxEncounterEvaluationsPerTick(), base.maxProbesPerTick(),
                base.maxCompletionsPerTick(), base.maxSignalsPerTick(), base.maxTickWorkMicros(),
                base.requestTimeoutNanos(), base.maxOptionsPerQuestion(), base.fitThresholds(),
                base.maxSessionsPerTick(), base.tickLayerMaxMicros(), base.guardMspt(), base.microMinHoldTicks(),
                base.waypointRefreshTicks(), base.maxWaypointPathsPerTick(), base.maxWaypointPathMicrosPerTick(),
                base.candidateScoring(), base.candidateDiversity(), base.pruning(), base.flushIntervalTicks(),
                base.maxPendingKeys(), base.maxStorageRepliesPerTick(), base.answerCache(), base.answerCacheTtlTicks(),
                base.answerCacheEntries(), base.answerCacheRevalidateRatio(), difficulty);
    }

    /**
     * Translates decision, Jev, storage, difficulty, zone, group, pattern and memory settings: the translation the root
     * uses.
     *
     * @param decision validated decision settings
     * @param jev validated Jev settings
     * @param storage validated storage settings
     * @param difficulty validated difficulty settings
     * @param zone validated zone limits
     * @param group validated group limits
     * @param patterns validated pattern limits
     * @param memory validated memory limits
     * @return the engine settings
     */
    static EngineSettings engineSettings(DecisionSettings decision, JevSettings jev, StorageSettings storage,
                                        DifficultyGlobals difficulty, ZoneCaps zone, GroupCaps group,
                                        akyrion.thepredict.domain.PatternSettings patterns,
                                        akyrion.thepredict.domain.MemorySettings memory) {
        var base = engineSettings(decision, jev, storage, difficulty);
        return new EngineSettings(base.maxRequestsPerMinute(), base.maxRequestsPerMinutePerWorld(),
                base.maxInFlightRequests(), base.maxInFlightRequestsPerWorld(), base.maxInputTokensPerMinute(),
                base.maxInstallsPerTick(), base.maxEncounterEvaluationsPerTick(), base.maxProbesPerTick(),
                base.maxCompletionsPerTick(), base.maxSignalsPerTick(), base.maxTickWorkMicros(),
                base.requestTimeoutNanos(), base.maxOptionsPerQuestion(), base.fitThresholds(),
                base.maxSessionsPerTick(), base.tickLayerMaxMicros(), base.guardMspt(), base.microMinHoldTicks(),
                base.waypointRefreshTicks(), base.maxWaypointPathsPerTick(), base.maxWaypointPathMicrosPerTick(),
                base.candidateScoring(), base.candidateDiversity(), base.pruning(), base.flushIntervalTicks(),
                base.maxPendingKeys(), base.maxStorageRepliesPerTick(), base.answerCache(), base.answerCacheTtlTicks(),
                base.answerCacheEntries(), base.answerCacheRevalidateRatio(), difficulty, zone, group, patterns,
                memory);
    }

    /**
     * Translates {@code decision.maxDecisionAgeMillis}, {@code decision.lowConfidenceStreakForLocal},
     * {@code decision.logDecisions} and {@code jev.model} into the settings of the tactical execution (rule D10).
     *
     * @param decision validated decision settings
     * @param jev validated Jev settings
     * @return the execution settings
     */
    static ExecutionSettings executionSettings(DecisionSettings decision, JevSettings jev) {
        return new ExecutionSettings(decision.maxDecisionAgeMillis() * 1_000_000L, decision.lowConfidenceStreakForLocal(),
                jev.model(), decision.logDecisions());
    }

    /** Runs one coordinator tick; the task of the plugin calls it every tick. */
    void tick() {
        coordinator.tick();
    }

    /** MSPT guard of the tick layer. A guard of zero never reaches here. */
    void noteGuard(boolean over) {
        coordinator.noteGuard(over);
    }

    /** {@code decision.tickLayer.guardMspt} of this start. */
    int guardMspt() {
        return coordinator.guardMspt();
    }

    /** Starts a new service check on the next tick ({@code /thepredict check}). */
    void requestCheck() {
        tracker.requestCheck();
    }

    /**
     * Waits for the next check that leaves {@code CHECKING}, for {@code /thepredict check} and the start messages.
     *
     * @param watcher called once on the server thread with the check as the client reported it
     * @return {@code false} when too many watchers already wait
     */
    boolean awaitCheck(Consumer<ServiceCheck> watcher) {
        return intake.awaitCheck(watcher);
    }

    /** Returns the diagnostics of {@code /thepredict status}, {@code inspect} and {@code debug goals} (M12). */
    Diagnostics diagnostics() {
        return diagnostics;
    }

    /** The answer cache of this start. */
    AnswerCache answers() {
        return answers;
    }

    /** The aggregate cache of this start. */
    AggregateCache aggregates() {
        return aggregates;
    }

    /** The store the plugin attaches once the writer exists. */
    ForwardingAggregateStore store() {
        return store;
    }

    /** The inbox whose storage lane the writer fills. */
    Inbox<InboxEntry> inbox() {
        return inbox;
    }

    /**
     * Forwards accepted casts that move a target.
     *
     * @param notes the hazard ledger
     */
    void useHazard(akyrion.thepredict.engine.HazardNotes notes) {
        steps.useHazard(notes);
    }

    /** The upkeep that hears the records tap. */
    SessionUpkeep upkeep() {
        return upkeep;
    }

    /**
     * Forwards a model-change discard to distilled coverage (errata E29-13 applied).
     *
     * @param hook the coverage hook
     */
    void onCoverageStale(Runnable hook) {
        steps.onCoverageStale(hook);
    }

    /** The coordinator, so the plugin can install the storage step. */
    Coordinator coordinator() {
        return coordinator;
    }

    /** Returns the debug intents of {@code /thepredict debug} (M12.3). */
    DebugIntents debugIntents() {
        return steps.debugIntents();
    }

    /** Returns the server clock the coordinator runs on. */
    ServerClock clock() {
        return clock;
    }

    /** Returns the state of the service and the enumerated cause of its last change. */
    ServiceCheck service() {
        return tracker.status();
    }

    /** Cancels every request in flight and drops the waiting results (rule T7, second step). */
    int cancelRequests() {
        return intake.cancelAll();
    }

    /**
     * What the coordinator needs from the root.
     *
     * @param settings engine limits
     * @param clock server clock
     * @param client the one decision client of the process
     * @param initial initial service state: {@code UNCONFIGURED} or {@code INVALID_CONFIGURATION} never contact Jev
     * @param board intent board
     * @param signals {@code predictordecide} signals
     * @param control goal ownership port
     * @param catalogRevision current catalog revision
     * @param actions casts, signals and threat of M10 over MythicMobs and Paper
     * @param execution settings of the tactical execution
     * @param random uniform source for the pause factor
     * @param info receiver of service state changes, model changes and the rate-limited decision lines of
     *             {@code decision.logDecisions}
     * @param warnings receiver of rate-limited warnings
     * @param modelLabel configured model as it may be shown ({@code JevSettings.modelLabel}), never a custom value
     * @param tap the switchable tap of the duel recorder (M18.4), silent until a recording attaches itself
     */
    record Wiring(EngineSettings settings, ServerClock clock, DecisionClient client, ServiceCheck initial,
                  IntentBoard board, ContextSignals signals, MobControl control, LongSupplier catalogRevision,
                  CombatActions actions, ExecutionSettings execution, DoubleSupplier random, Consumer<String> info,
                  Consumer<String> warnings, String modelLabel, DecisionTap tap) {
        Wiring {
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(client, "client");
            Objects.requireNonNull(initial, "initial");
            Objects.requireNonNull(board, "board");
            Objects.requireNonNull(signals, "signals");
            Objects.requireNonNull(control, "control");
            Objects.requireNonNull(catalogRevision, "catalogRevision");
            Objects.requireNonNull(actions, "actions");
            Objects.requireNonNull(execution, "execution");
            Objects.requireNonNull(random, "random");
            Objects.requireNonNull(info, "info");
            Objects.requireNonNull(warnings, "warnings");
            Objects.requireNonNull(modelLabel, "modelLabel");
            Objects.requireNonNull(tap, "tap");
        }
    }
}
