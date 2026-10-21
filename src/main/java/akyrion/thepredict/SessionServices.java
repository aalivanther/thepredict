package akyrion.thepredict;

import akyrion.thepredict.adapter.server.context.Blocking;
import akyrion.thepredict.adapter.server.events.CombatContextListener;
import akyrion.thepredict.adapter.server.events.CombatOutcomeListener;
import akyrion.thepredict.adapter.server.events.LifecycleListener;
import akyrion.thepredict.adapter.server.events.MobDirectory;
import akyrion.thepredict.adapter.server.events.MobScan;
import akyrion.thepredict.adapter.server.events.OutcomeListener;
import akyrion.thepredict.adapter.server.events.ServerThread;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.Agenda;
import akyrion.thepredict.engine.CombatActions;
import akyrion.thepredict.engine.CombatReader;
import akyrion.thepredict.engine.ContextEvents;
import akyrion.thepredict.engine.DecisionMetrics;
import akyrion.thepredict.engine.EncounterCapture;
import akyrion.thepredict.engine.EncounterRegistry;
import akyrion.thepredict.engine.GroupDirector;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.OutcomeEvents;
import akyrion.thepredict.engine.OutcomeMetrics;
import akyrion.thepredict.engine.ServerClock;
import akyrion.thepredict.engine.SessionLifecycle;
import akyrion.thepredict.engine.SessionMetrics;
import akyrion.thepredict.engine.SessionReconciler;
import akyrion.thepredict.engine.SessionRegistry;
import akyrion.thepredict.engine.SpawnLevelBook;
import akyrion.thepredict.engine.ThreatEnrichment;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Composition of the M06 session components (registries, agenda, reconciliation, lifecycle, scan and listener), of
 * the M07 context components (capture step, context events and their listeners), of the M11 enriched threat and of the
 * M16 outcome windows, which the outcome listeners feed. Built only by
 * {@link ThePredictPlugin} (rule D8). Server thread only. {@link #tick()} is the lifecycle work that the coordinator
 * of M09 runs on every tick; {@link DecisionServices} composes the coordinator over these components.
 */
final class SessionServices {
    /** Minimum ticks between two logged failures of the periodic task (rule C8). */
    static final long FAILURE_LOG_INTERVAL_TICKS = 200;

    private final SessionMetrics metrics = new SessionMetrics();
    private final DecisionMetrics decisionMetrics;
    private final Agenda agenda = new Agenda();
    private final EncounterRegistry encounters;
    private final SessionRegistry sessions;
    private final SessionLifecycle lifecycle;
    private final MobScan scan;
    private final List<Listener> listeners;
    private final CombatReader reader;
    private final ContextEvents contextEvents;
    private final EncounterCapture capture;
    private final ThreatEnrichment threat;
    private final OutcomeEvents outcomes;
    private final CombatActions actions;
    private final ServerClock clock;
    private final Consumer<String> warnings;
    private final Function<String, Optional<TacticalProfile>> boundProfiles;
    private long lastFailureLogTick = Long.MIN_VALUE / 2;

    /**
     * Builds the components; nothing runs until {@link #startScan()} and the periodic task. The enriched threat writes
     * through {@code actions}; its lines share the execution log once the coordinator wires it.
     */
    SessionServices(IntentBoard board, ServerClock clock, Consumer<MobKey> release, MobDirectory mobs,
                    CombatReader reader, Function<String, Optional<TacticalProfile>> boundProfiles,
                    Runnable rebindCatalog, ServerThread thread, Consumer<String> warnings, CombatActions actions) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
        this.boundProfiles = Objects.requireNonNull(boundProfiles, "boundProfiles");
        decisionMetrics = new DecisionMetrics(clock::currentTick);
        encounters = new EncounterRegistry(agenda, clock, metrics);
        sessions = new SessionRegistry(board, encounters, agenda);
        var reconciler = new SessionReconciler(sessions, encounters, reader::isActive, metrics);
        lifecycle = new SessionLifecycle(sessions, board, release, reconciler, metrics, clock);
        scan = new MobScan(lifecycle, mobs, boundProfiles);
        contextEvents = new ContextEvents(encounters, sessions, agenda, clock);
        capture = new EncounterCapture(reader, sessions, encounters, lifecycle, clock, decisionMetrics);
        this.actions = Objects.requireNonNull(actions, "actions");
        threat = new ThreatEnrichment(encounters, sessions, actions, clock);
        outcomes = new OutcomeEvents(sessions, encounters, new OutcomeMetrics(), decisionMetrics);
        outcomes.bind(actions);
        var spawns = new SpawnLevelBook();
        outcomes.hearSpawns(spawns);
        threat.hearBlocked(outcomes::blocked);
        var lifecycleListener = new LifecycleListener(lifecycle, scan, thread, rebindCatalog, boundProfiles);
        lifecycleListener.useLevels(spawns);
        listeners = List.of(lifecycleListener,
                new CombatContextListener(contextEvents, thread),
                new CombatOutcomeListener(contextEvents, threat, outcomes, thread, clock::currentTick,
                        Blocking::blocking),
                new OutcomeListener(outcomes, contextEvents, thread, clock::currentTick));
    }

    /** Returns the lifecycle listener, the two context listeners and the outcome listener to register, in that order. */
    List<Listener> listeners() {
        return listeners;
    }

    /** Starts the bounded scan of the mobs that already exist. */
    void startScan() {
        scan.start();
    }

    /**
     * Runs the scan and the lifecycle work of one tick, as step of the coordinator; a failure is logged with a limit
     * and the next tick continues.
     */
    void tick() {
        try {
            scan.tick();
            lifecycle.tick();
        } catch (RuntimeException exception) {
            var tick = clock.currentTick();
            if (tick - lastFailureLogTick >= FAILURE_LOG_INTERVAL_TICKS) {
                lastFailureLogTick = tick;
                warnings.accept("Session lifecycle tick failed: " + exception.getClass().getSimpleName());
            }
        }
    }

    /**
     * Follows a catalog that changed without a MythicMobs reload (a context disabled by the deferred condition
     * check of M03.5): every session advances with its new profile or is unbound, as on a reload.
     */
    void catalogChanged() {
        lifecycle.reloaded(boundProfiles);
    }

    /** Clears every view and releases at most 200 sessions (T7). */
    int shutdown() {
        return lifecycle.shutdown();
    }

    /** Returns the session registry, for the coordinator. */
    SessionRegistry registry() {
        return sessions;
    }

    /** Returns the encounter registry, for the coordinator. */
    EncounterRegistry encounters() {
        return encounters;
    }

    /** Returns the agenda, for the coordinator. */
    Agenda agenda() {
        return agenda;
    }

    /**
     * Stamps group briefs on the next capture and sends role and plan cues through the cast port. One assignment
     * per shared table goes through the threat writer.
     *
     * @param director the group step
     */
    void hearGroup(GroupDirector director) {
        Objects.requireNonNull(director, "director");
        capture.hearGroup(director);
        director.useCues(actions);
        director.useAssignment(threat::assignGroup);
    }

    /**
     * Sends the pattern signals through the cast port (M26.11, DOC-01).
     *
     * @param director the pattern step
     */
    void hearPatterns(akyrion.thepredict.engine.PatternDirector director) {
        Objects.requireNonNull(director, "director").useCues(actions);
    }

    /** Returns the capture step, for the coordinator. */
    EncounterCapture capture() {
        return capture;
    }

    /** Returns the combat reader, for the coordinator. */
    CombatReader reader() {
        return reader;
    }

    /** Returns the enriched threat, whose unreachable-target decay the coordinator runs once per evaluation. */
    ThreatEnrichment threat() {
        return threat;
    }

    /** Returns the outcome windows of M16.8, which the outcome listeners feed and the execution opens. */
    OutcomeEvents outcomes() {
        return outcomes;
    }

    /** Returns the decision metrics, shared with the coordinator. */
    DecisionMetrics decisionMetrics() {
        return decisionMetrics;
    }

    /** Returns the counters of sessions and encounters, for the diagnostics of M12. */
    SessionMetrics sessionMetrics() {
        return metrics;
    }

    /** Returns the lifecycle, for the pending releases shown by the diagnostics of M12. */
    SessionLifecycle lifecycle() {
        return lifecycle;
    }

    /** Returns the context events, for their counters in the diagnostics of M12. */
    ContextEvents contextEvents() {
        return contextEvents;
    }
}
