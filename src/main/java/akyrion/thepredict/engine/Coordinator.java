package akyrion.thepredict.engine;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The one periodic task of the engine (M09.1, rules A3, A5, A6, T1, T4, T5, C7). Runs every tick on the server
 * thread, reads the tick and the time once, and works in a fixed order with a limit per step:
 *
 * <ol>
 *     <li>results: at most {@code maxCompletionsPerTick} inbox entries;</li>
 *     <li>watchdog: requests past their deadline are released as {@code TIMEOUT}; a due service check starts;</li>
 *     <li>signals: at most {@code maxSignalsPerTick} {@code predictordecide} signals;</li>
 *     <li>probes, the reactive layer: at most {@code maxProbesPerTick} probes, each placing its session and running
 *     M10.4;</li>
 *     <li>lifecycle: the bounded scan, releases and reconciliation of M06;</li>
 *     <li>group: the group step of M25, within {@code group.maxMicrosPerTick};</li>
 *     <li>patterns: the pattern step of M26, within {@code patterns.maxMicrosPerTick};</li>
 *     <li>tick layer: visits due casts and, outside casts mode, reactions and micro, at most
 *     {@code maxSessionsPerTick} (M20.3); the ready-cast walk of M19 lives inside this step;</li>
 *     <li>zone: the zone step of M24, within {@code zone.maxMicrosPerTick};</li>
 *     <li>installations: at most {@code maxInstallsPerTick} goal installations, only within the tick budget;</li>
 *     <li>decisions: at most {@code maxEncounterEvaluationsPerTick} due encounters, each prepared, dispatched, handed
 *     to {@link ExecutionSteps#evaluated} and closed if its combat ended, only within the tick budget;</li>
 *     <li>storage: the aggregate cache drains replies and flushes (M21);</li>
 *     <li>memory: the hints of the player memory (M27);</li>
 *     <li>metrics: the duration of the tick.</li>
 * </ol>
 *
 * <p>Once the tick has used {@code maxTickWorkMicros}, installations and decisions wait for the next tick; the first
 * steps always run because they free resources or protect control. Each step catches {@link RuntimeException} at its
 * boundary, and each encounter at its own; failures are counted and logged at most once every
 * {@link #FAILURE_LOG_INTERVAL_TICKS} ticks with their type only (rule C8), and the next tick continues.
 */
public final class Coordinator {
    /** Minimum ticks between two logged failures. */
    public static final long FAILURE_LOG_INTERVAL_TICKS = 200;

    private final EngineSettings settings;
    private final ServerClock clock;
    private final ResultIntake intake;
    private final SessionUpkeep upkeep;
    private final DecisionDispatcher dispatcher;
    private final ExecutionSteps steps;
    private final Agenda agenda;
    private final Runnable lifecycle;
    private final DecisionMetrics metrics;
    private final Consumer<String> warnings;
    private StorageTick storage = StorageTick.NONE;
    private ZoneControl zone = ZoneControl.NONE;
    private GroupControl group = GroupControl.NONE;
    private PatternControl patterns = PatternControl.NONE;
    private Runnable memoryHints = () -> {
    };
    private long lastWarningTick = Long.MIN_VALUE / 2;

    /**
     * Creates the coordinator.
     *
     * @param settings limits per tick and work budget
     * @param clock server clock, read once per tick and for the work budget
     * @param intake steps 1 and 2
     * @param upkeep steps 3 to 5 and the upkeep before a decision
     * @param dispatcher capture and send of one encounter
     * @param steps steps of M10, here the hook after each evaluation
     * @param agenda due encounters
     * @param lifecycle bounded lifecycle work of M06: scan, releases and reconciliation
     * @param metrics tick duration and failure counters
     * @param warnings receiver of rate-limited warnings
     */
    public Coordinator(EngineSettings settings, ServerClock clock, ResultIntake intake, SessionUpkeep upkeep,
                       DecisionDispatcher dispatcher, ExecutionSteps steps, Agenda agenda, Runnable lifecycle,
                       DecisionMetrics metrics, Consumer<String> warnings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.intake = Objects.requireNonNull(intake, "intake");
        this.upkeep = Objects.requireNonNull(upkeep, "upkeep");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.steps = Objects.requireNonNull(steps, "steps");
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    /**
     * Forces tick-layer step 2 while the previous server tick exceeded the MSPT guard (M20.3).
     *
     * @param over whether that tick was over the guard
     */
    public void noteGuard(boolean over) {
        upkeep.noteGuard(over);
    }

    /**
     * The configured MSPT guard, or zero when the guard is off.
     *
     * @return milliseconds, or zero
     */
    public int guardMspt() {
        return settings.guardMspt();
    }

    /**
     * Replaces the storage step. The default step does nothing.
     *
     * @param storage the step
     */
    public void useStorage(StorageTick storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    /**
     * Replaces the zone step. The default step does nothing. It runs after the tick layer and before installs.
     *
     * @param zone the step
     */
    public void useZone(ZoneControl zone) {
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /**
     * Replaces the group step. The default step does nothing. It runs after lifecycle and before the tick layer.
     *
     * @param group the step
     */
    public void useGroup(GroupControl group) {
        this.group = Objects.requireNonNull(group, "group");
    }

    /**
     * Replaces the pattern step. The default step does nothing, so a fight with patterns off stays identical.
     * It runs after the group step and before the tick layer, and reads the signals of the previous tick.
     *
     * @param patterns the step
     */
    public void usePatterns(PatternControl patterns) {
        this.patterns = Objects.requireNonNull(patterns, "patterns");
    }

    /**
     * Installs the learning section on the dispatcher. Defaults leave capture unarmed.
     *
     * @param settings the learning section
     */
    public void useLearning(akyrion.thepredict.domain.LearningSettings settings) {
        dispatcher.useLearning(settings);
    }

    /**
     * Installs distillation on the dispatcher and the intake. The same board is the only teaching writer.
     *
     * @param settings the distill section
     * @param board the teaching board
     */
    public void useDistill(akyrion.thepredict.domain.distill.DistillSettings settings,
                           akyrion.thepredict.engine.distill.TeachingBoard board) {
        dispatcher.useDistill(settings, board);
        intake.useTeaching(board);
    }

    /**
     * Installs the coverage runtime consulted on the next capture.
     *
     * @param runtime the runtime
     */
    public void useRuntime(akyrion.thepredict.engine.distill.DistillRuntime runtime) {
        dispatcher.useRuntime(runtime);
    }

    /**
     * Installs the live mode ceiling consulted on the next capture.
     *
     * @param control the control
     */
    public void useControl(akyrion.thepredict.engine.distill.DistillControl control) {
        dispatcher.useControl(control);
    }

    /**
     * Offers a value table to the next capture.
     *
     * @param table the table
     * @param state the artifact state
     * @param profile profile code
     * @param fine packed fine context
     */
    public void offerTable(akyrion.thepredict.engine.learn.ValueTable table,
                           akyrion.thepredict.engine.learn.LearnedArtifacts.State state, int profile, long fine) {
        dispatcher.offerTable(table, state, profile, fine);
    }

    /**
     * Offers a value table with its verdict and {@code learning.scope} to the next capture (A16, LD-09).
     *
     * @param table the table
     * @param state the artifact state
     * @param verdict status of {@code GateVerdicts} for the artifact
     * @param scope {@code learning.scope}
     * @param profile profile code
     * @param fine packed fine context
     */
    public void offerTable(akyrion.thepredict.engine.learn.ValueTable table,
                           akyrion.thepredict.engine.learn.LearnedArtifacts.State state, String verdict, String scope,
                           int profile, long fine) {
        dispatcher.offerTable(table, state, verdict, scope, profile, fine);
    }

    /**
     * Installs a calibrator so the next capture compares {@code c*} when the gate is calibrated.
     *
     * @param gate the confidence gate
     * @param model the calibrator
     * @param key the calibration key
     * @param liveModel the live model
     * @param target the parity target
     */
    public void useCalibrator(akyrion.thepredict.domain.learn.ConfidenceGate gate,
                              akyrion.thepredict.engine.learn.ConfidenceCalibrator model,
                              akyrion.thepredict.domain.learn.CalibrationKey key, String liveModel, double target) {
        dispatcher.useCalibrator(gate, model, key, liveModel, target);
    }

    /**
     * Drains prefetch hints on every tick (errata E27-7 applied). The default does nothing.
     *
     * @param hints the drain
     */
    public void useMemoryHints(Runnable hints) {
        this.memoryHints = Objects.requireNonNull(hints, "hints");
    }

    /**
     * Runs one tick of the engine. Server thread only; never blocks. The tick is published on the intent board before
     * any step, for the readers off the server thread (SA-10).
     */
    public void tick() {
        var tick = clock.currentTick();
        var start = clock.nanoTime();
        upkeep.publishTick(tick);
        var failedSessions = metrics.total(DecisionMetrics.Counter.SESSION_FAILURES);
        run("results", tick, () -> intake.drain(settings.maxCompletionsPerTick(), tick, start));
        run("watchdog", tick, () -> intake.expire(tick, start));
        run("signals", tick, () -> upkeep.signals(settings.maxSignalsPerTick(), tick));
        run("probes", tick, () -> upkeep.probes(settings.maxProbesPerTick(), tick, start));
        run("lifecycle", tick, lifecycle);
        run("group", tick, () -> group.step(tick, Math.max(0L, settings.group().maxMicrosPerTick() * 1_000L)));
        run("patterns", tick, () -> patterns.step(tick,
                Math.max(0L, settings.patterns().maxMicrosPerTick() * 1_000L)));
        run("tickLayer", tick, () -> {
            var origin = clock.nanoTime();
            upkeep.noteWaypointRefresh(settings.waypointRefreshTicks());
            upkeep.tickLayer(settings.maxSessionsPerTick(), settings.tickLayerMaxMicros(),
                    settings.microMinHoldTicks(), tick, start, origin, clock::nanoTime);
        });
        run("zone", tick, () -> zone.step(tick, Math.max(0L, settings.zone().maxMicrosPerTick() * 1_000L)));
        budgeted(tick, start);
        run("storage", tick, () -> storage.onTick(tick, clock.nanoTime()));
        run("memory", tick, memoryHints);
        finish(tick, start, failedSessions);
    }

    /** Installs and decisions, each only while the work budget of this tick lasts. */
    private void budgeted(long tick, long start) {
        if (withinBudget(start)) {
            run("installs", tick, () -> upkeep.installs(settings.maxInstallsPerTick(), () -> withinBudget(start)));
        }
        if (withinBudget(start)) {
            run("decisions", tick, () -> decide(tick, start));
        } else {
            deferred(tick);
        }
    }

    /** Records the work of this tick and warns once about the sessions that fell back during it. */
    private void finish(long tick, long start, long failedSessions) {
        var end = clock.nanoTime();
        metrics.record(DecisionMetrics.Span.TICK_WORK, end - start, end);
        var failed = metrics.total(DecisionMetrics.Counter.SESSION_FAILURES) - failedSessions;
        if (failed > 0) {
            warn(tick, failed + " session(s) fell back to the base AI after an internal error");
        }
    }

    /**
     * Step 6: evaluates the due encounters while the work budget lasts; the rest stay due. The agenda is asked for one
     * encounter at a time, so its rotation between worlds only advances for an encounter that is evaluated: asking for
     * all of them at once and deferring the second one left the same world first on every tick, and one expensive
     * capture per tick starved the other world (found by the load suite of M13).
     */
    private void decide(long tick, long start) {
        for (var evaluated = 0; evaluated < settings.maxEncounterEvaluationsPerTick(); evaluated++) {
            if (!withinBudget(start)) {
                deferred(tick);
                return;
            }
            var next = agenda.due(tick, 1);
            if (next.isEmpty()) {
                return;
            }
            upkeep.prepare(next.getFirst()).ifPresent(encounter -> evaluate(encounter, tick, start));
        }
    }

    /**
     * Dispatches one prepared encounter and hands the evaluation to M10, then, in a boundary of its own, closes it if
     * its combat ended, so a failing dispatch or hook never keeps a finished encounter open. A failure is contained
     * here and an open encounter still waits for its next heartbeat, so it never stays first in line and never starves
     * its world. {@link ExecutionSteps#evaluated} is not called when the dispatch failed.
     */
    private void evaluate(Encounter encounter, long tick, long start) {
        try {
            contained(tick, () -> {
                dispatcher.dispatch(encounter);
                steps.evaluated(encounter, tick, start);
            });
            contained(tick, () -> upkeep.closeIfFinished(encounter));
        } finally {
            if (!encounter.isClosed()) {
                agenda.decided(encounter, tick);
            }
        }
    }

    /** Counts a tick whose installations or decisions wait for the next tick, only if one was actually waiting. */
    private void deferred(long tick) {
        if (agenda.hasDue(tick) || upkeep.installsPending()) {
            metrics.add(DecisionMetrics.Counter.DEFERRED_TICKS, 1);
        }
    }

    private void contained(long tick, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException exception) {
            metrics.add(DecisionMetrics.Counter.STEP_FAILURES, 1);
            warn(tick, "Encounter evaluation failed: " + exception.getClass().getSimpleName());
        }
    }

    private boolean withinBudget(long start) {
        return clock.nanoTime() - start < settings.maxTickWorkNanos();
    }

    private void run(String step, long tick, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException exception) {
            metrics.add(DecisionMetrics.Counter.STEP_FAILURES, 1);
            warn(tick, "Coordinator step " + step + " failed: " + exception.getClass().getSimpleName());
        }
    }

    private void warn(long tick, String message) {
        if (tick - lastWarningTick >= FAILURE_LOG_INTERVAL_TICKS) {
            lastWarningTick = tick;
            warnings.accept(message);
        }
    }
}
