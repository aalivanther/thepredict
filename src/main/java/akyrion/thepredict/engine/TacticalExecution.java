package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FitGate;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.PruningMode;
import akyrion.thepredict.domain.SkillChoice;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The {@link ExecutionSteps} of M10, which replace the placeholder steps of M09: acceptance of answers
 * (M10.1, M10.2, M10.5, M10.7), the reactive cycle every 2 ticks (M10.3, M10.4, M10.6), and once per evaluation the
 * {@code NO_PROGRESS} rule (M10.4) and then the unreachable-target decay of the enriched threat (M11.2). Server thread
 * only (rule T1); it never blocks and never touches the network.
 *
 * <p>Built once per plugin start over the components of the coordinator, so the model learned behind an alias and
 * the rate limit of the log survive profile reloads.
 */
public final class TacticalExecution implements ExecutionSteps, SessionFailure {
    private final DecisionAcceptor acceptor;
    private final ReactiveLayer reactive;
    private final ProgressWatch progress;
    private final ThreatEnrichment threat;
    private final MobControl mobs;
    private final DebugIntents debugIntents;
    private final IntentControl intents;

    /**
     * Wires the execution.
     *
     * @param parts components of the coordinator the execution reads or changes
     * @param actions port that casts, signals and changes threat
     * @param settings maximum answer age, low-confidence streak, configured model and decision logging
     * @param log receiver of rate-limited lines
     * @param fitThresholds veto and rescue thresholds of the fit checks ({@link EngineSettings#fitThresholds()})
     */
    public TacticalExecution(Parts parts, CombatActions actions, ExecutionSettings settings, Consumer<String> log,
                             FitGate.FitThresholds fitThresholds) {
        Objects.requireNonNull(parts, "parts");
        var executionLog = new ExecutionLog(log, Objects.requireNonNull(settings, "settings").logDecisions());
        var control = new IntentControl(parts.board(), Objects.requireNonNull(actions, "actions"), parts.metrics(),
                executionLog, parts.outcomes(), parts.tap());
        parts.outcomes().logTo(executionLog);
        parts.outcomes().tapTo(parts.tap());
        var fallback = new FallbackStep(parts.tracker(), parts.encounters(), parts.budget(), control, settings);
        control.usePatternConditions(fallback::useConditions);
        acceptor = new DecisionAcceptor(parts.sessions(), parts.encounters(), parts.reader(), parts.catalogRevision(),
                control, fallback, parts.metrics(), settings, executionLog, fitThresholds, parts.tap());
        reactive = new ReactiveLayer(parts.encounters(), parts.agenda(), control, fallback);
        progress = new ProgressWatch(parts.control(), control);
        threat = parts.threat();
        threat.logTo(executionLog);
        threat.tapTo(parts.tap());
        mobs = parts.control();
        debugIntents = new DebugIntents(parts.sessions(), control);
        intents = control;
    }

    /**
     * Returns the intents of {@code /thepredict debug}, which share the intent control of the execution (M12.3).
     *
     * @return the debug intents
     */
    public DebugIntents debugIntents() {
        return debugIntents;
    }

    /**
     * Turns rule 8 on when pruning is safe, or leaves it to the cadence when pruning is off (M20.7).
     *
     * @param mode {@code decision.pruning}; tests and the duel stay at {@link PruningMode#OFF}
     */
    public void usePruning(PruningMode mode) {
        acceptor.usePruning(mode);
    }

    /**
     * Marks distilled coverage stale when a model change is discarded (errata E29-13 applied).
     *
     * @param hook called once per stale answer
     */
    public void onCoverageStale(Runnable hook) {
        acceptor.onCoverageStale(hook);
    }

    /**
     * Forwards accepted casts that move a target to the hazard ledger.
     *
     * @param notes the ledger
     */
    public void useHazard(HazardNotes notes) {
        intents.useHazard(notes);
    }

    @Override
    public void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
        acceptor.accept(completed, tick, nowNanos);
    }

    @Override
    public void react(MobSession session, MobProbe probe, long tick, long nowNanos) {
        reactive.react(session, probe, tick, nowNanos);
    }

    @Override
    public void evaluated(Encounter encounter, long tick, long nowNanos) {
        progress.evaluated(encounter, tick, nowNanos);
        threat.evaluated(encounter, tick, mobs);
    }

    @Override
    public void castReady(MobSession session, long tick, long nowNanos) {
        intents.castIfReady(session, tick, nowNanos);
    }

    @Override
    public boolean choiceReady(MobSession session, SkillChoice choice, long tick) {
        return intents.ready(session, choice, tick);
    }

    @Override
    public void releaseTell(MobSession session, long tick) {
        intents.releaseTell(session, tick);
    }

    @Override
    public void advanceStance(MobSession session, long tick) {
        intents.advanceStance(session, tick);
    }

    @Override
    public void failed(MobSession session, long tick) {
        intents.fail(session, tick);
    }

    /**
     * Components of the coordinator that the execution uses.
     *
     * @param sessions session registry
     * @param encounters encounter registry
     * @param agenda agenda, to mark context pending after a contingency
     * @param board intent board where views are published
     * @param reader combat reader, for the probe of each answered mob
     * @param control goal ownership, for movement progress
     * @param tracker state of the service
     * @param budget request budget
     * @param metrics counters of discards, fallbacks and cast outcomes
     * @param catalogRevision current catalog revision
     * @param threat enriched threat, for the unreachable-target decay of each evaluation
     * @param outcomes outcome windows of the adopted intents (M16.8)
     * @param tap listener of the intents, casts, threat changes and outcome windows (M18.4); {@link DecisionTap#NONE}
     *            for none
     */
    public record Parts(SessionRegistry sessions, EncounterRegistry encounters, Agenda agenda, IntentBoard board,
                        CombatReader reader, MobControl control, ServiceStatusTracker tracker, RequestBudget budget,
                        DecisionMetrics metrics, LongSupplier catalogRevision, ThreatEnrichment threat,
                        OutcomeEvents outcomes, DecisionTap tap) {
        /**
         * Validates the components.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Parts {
            Objects.requireNonNull(sessions, "sessions");
            Objects.requireNonNull(encounters, "encounters");
            Objects.requireNonNull(agenda, "agenda");
            Objects.requireNonNull(board, "board");
            Objects.requireNonNull(reader, "reader");
            Objects.requireNonNull(control, "control");
            Objects.requireNonNull(tracker, "tracker");
            Objects.requireNonNull(budget, "budget");
            Objects.requireNonNull(metrics, "metrics");
            Objects.requireNonNull(catalogRevision, "catalogRevision");
            Objects.requireNonNull(threat, "threat");
            Objects.requireNonNull(outcomes, "outcomes");
            Objects.requireNonNull(tap, "tap");
        }
    }
}
