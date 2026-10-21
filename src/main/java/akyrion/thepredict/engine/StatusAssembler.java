package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.PruneReason;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.TacticalIntent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Builds the {@link StatusReport} of {@link Diagnostics} from the counters the engine already keeps (M12.1).
 * Package-private; server thread only. Every loop is bounded by the sessions or by the size of an enum.
 */
final class StatusAssembler {
    private static final double[] PERCENTILES = {0.50, 0.95, 0.99};

    private final Diagnostics.Sources sources;

    StatusAssembler(Diagnostics.Sources sources) {
        this.sources = sources;
    }

    StatusReport status() {
        var now = sources.clock().nanoTime();
        var budget = sources.budget();
        var settings = sources.settings();
        var server = new StatusReport.Budget(budget.requestsLastMinute(now), settings.maxRequestsPerMinute(),
                budget.inFlight(), settings.maxInFlightRequests());
        return new StatusReport(service(now), sessions(now), encounters(), server, Optional.empty(),
                new StatusReport.Consumption(budget.tokensLastMinute(now), settings.maxInputTokensPerMinute()),
                latency(now), recent(sources.metrics().recentFailures()), recent(sources.metrics().recentFallbacks()),
                counters(), context(), threat(), effectiveness());
    }

    private StatusReport.Effectiveness effectiveness() {
        var metrics = sources.metrics();
        return new StatusReport.Effectiveness(sources.outcomes().byLevel(),
                metrics.total(DecisionMetrics.Counter.CONTINGENCIES_ASKED),
                metrics.total(DecisionMetrics.Counter.CONTINGENCIES_FIRED),
                metrics.total(DecisionMetrics.Counter.CONTINGENCIES_EFFECTIVE));
    }

    StatusReport.Budget world(UUID worldId) {
        var now = sources.clock().nanoTime();
        var settings = sources.settings();
        return new StatusReport.Budget(sources.budget().requestsLastMinute(worldId, now),
                settings.maxRequestsPerMinutePerWorld(), sources.budget().inFlight(worldId),
                settings.maxInFlightRequestsPerWorld());
    }

    private StatusReport.Service service(long now) {
        var tracker = sources.tracker();
        var pause = tracker.pauseRemainingNanos(now);
        return new StatusReport.Service(tracker.status(), pause.isPresent()
                ? OptionalLong.of(pause.getAsLong() / 1_000_000L) : OptionalLong.empty(), tracker.inTrial(),
                sources.configuredModel(), sources.intake().lastModel());
    }

    private StatusReport.Sessions sessions(long now) {
        var byLevel = new EnumMap<ControlLevel, Integer>(ControlLevel.class);
        for (var level : ControlLevel.values()) {
            byLevel.put(level, 0);
        }
        var debug = 0;
        for (var session : sources.sessions().active()) {
            var level = session.intent().filter(intent -> !intent.expired(now)).map(TacticalIntent::level)
                    .orElse(ControlLevel.BASE);
            byLevel.merge(level, 1, Integer::sum);
            debug += session.debugIntent() ? 1 : 0;
        }
        var byState = new EnumMap<SessionState, Integer>(SessionState.class);
        for (var state : SessionState.values()) {
            byState.put(state, sources.sessions().count(state));
        }
        var metrics = sources.sessionMetrics();
        var ended = new LinkedHashMap<String, Long>();
        for (var cause : SessionMetrics.SessionEnd.values()) {
            ended.put(cause.name(), metrics.endedSessions(cause));
        }
        return new StatusReport.Sessions(byLevel, byState, debug, metrics.openedSessions(), ended,
                sources.lifecycle().pendingReleases(), metrics.failedReleases());
    }

    private StatusReport.Encounters encounters() {
        var metrics = sources.sessionMetrics();
        var closed = 0L;
        for (var cause : SessionMetrics.EncounterEnd.values()) {
            closed += metrics.closedEncounters(cause);
        }
        return new StatusReport.Encounters(sources.encounters().size(), sources.inFlight().size(),
                sources.encounters().contextPending(), metrics.openedEncounters(), closed,
                metrics.averageEncounterTicks());
    }

    private Map<DecisionMetrics.Span, StatusReport.Latency> latency(long now) {
        var metrics = sources.metrics();
        var latency = new EnumMap<DecisionMetrics.Span, StatusReport.Latency>(DecisionMetrics.Span.class);
        for (var span : DecisionMetrics.Span.values()) {
            var percentiles = new OptionalLong[PERCENTILES.length];
            for (var index = 0; index < PERCENTILES.length; index++) {
                percentiles[index] = metrics.percentileMicros(span, PERCENTILES[index], now);
            }
            latency.put(span, new StatusReport.Latency(percentiles[0], percentiles[1], percentiles[2],
                    metrics.samples(span), metrics.maxMicros(span)));
        }
        return latency;
    }

    private List<StatusReport.RecentCause> recent(List<DecisionMetrics.Recent> causes) {
        var tick = sources.clock().currentTick();
        var recent = new ArrayList<StatusReport.RecentCause>(causes.size());
        for (var cause : causes) {
            recent.add(new StatusReport.RecentCause(cause.cause().name(), Math.max(0, tick - cause.tick())));
        }
        return recent;
    }

    private List<StatusReport.CounterGroup> counters() {
        var plain = new LinkedHashMap<String, Long>();
        for (var counter : DecisionMetrics.Counter.values()) {
            putNonZero(plain, counter.name(), sources.metrics().total(counter));
        }
        for (var counter : DecisionMetrics.ReadyCounter.values()) {
            putNonZero(plain, counter.name(), sources.metrics().total(counter));
        }
        for (var counter : DecisionMetrics.TellCounter.values()) {
            putNonZero(plain, counter.name(), sources.metrics().total(counter));
        }
        for (var counter : DecisionMetrics.PatternCounter.values()) {
            putNonZero(plain, counter.name(), sources.metrics().total(counter));
        }
        for (var counter : DecisionMetrics.LayerCounter.values()) {
            putNonZero(plain, counter.name(), sources.metrics().layer(counter));
        }
        for (var reason : PruneReason.values()) {
            putNonZero(plain, reason.name(), sources.metrics().pruned(reason));
        }
        for (var bucket = 0; bucket < 4; bucket++) {
            putNonZero(plain, "READY_LATENCY_" + bucket, sources.metrics().readyLatencyBucket(bucket));
        }
        return List.of(new StatusReport.CounterGroup("totals", plain), group("failures", FailureKind.values()),
                group("dispatch", DecisionDispatcher.Outcome.values()), group("fallbacks", FallbackReason.values()),
                group("outcomes", ExecutionOutcome.values()), group("discards", DiscardReason.values()));
    }

    private StatusReport.CounterGroup group(String name, Enum<?>[] causes) {
        var values = new LinkedHashMap<String, Long>();
        for (var cause : causes) {
            putNonZero(values, cause.name(), sources.metrics().count(cause));
        }
        return new StatusReport.CounterGroup(name, values);
    }

    private static void putNonZero(Map<String, Long> values, String name, long value) {
        if (value != 0) {
            values.put(name, value);
        }
    }

    private StatusReport.Context context() {
        var events = sources.contextEvents().metrics();
        var signals = sources.signals();
        return new StatusReport.Context(events.recordedEvents(), events.repeatedEvents(), events.pendingMarks(),
                events.memberPathfinds(), signals.pendingCount(), signals.droppedCount());
    }

    private StatusReport.Threat threat() {
        var threat = sources.threat().metrics();
        return new StatusReport.Threat(threat.assists(), threat.blockedHits(), threat.rangedHarass(), threat.decays(),
                threat.limited(), threat.failures(), threat.pointsAdded(), threat.pointsRemoved());
    }
}
