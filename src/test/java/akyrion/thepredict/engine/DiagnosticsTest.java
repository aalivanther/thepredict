package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.ThreatTableState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reports of {@code /thepredict status} and {@code inspect} over the whole engine (M12.1, M12.2, rule P2). */
class DiagnosticsTest {
    private final ExecutionFixture fixture = new ExecutionFixture();
    private final ContextSignals signals = new ContextSignals();
    private final Diagnostics diagnostics = new Diagnostics(new Diagnostics.Sources(fixture.sessions.registry,
            fixture.sessions.encounters, fixture.sessions.metrics, fixture.sessions.lifecycle,
            new ContextEvents(fixture.sessions.encounters, fixture.sessions.registry, fixture.sessions.agenda,
                    fixture.clock), signals, fixture.metrics, fixture.threat, fixture.budget, ExecutionFixture.settings(2000),
            fixture.tracker, fixture.inFlight, fixture.intake, fixture.mobs, fixture.reader, fixture.clock, "jev-1.13.0",
            fixture.outcomes.metrics()));

    private void answeredByJev() {
        fixture.ready();
        fixture.open(1);
        fixture.open(2);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.answer(0, question -> ExecutionFixture.prefer(ExecutionFixture.KEEP, 0.8, 0.9));
        fixture.run(1);
    }

    @Test
    void theStatusCountsSessionsByLevelAndStateAndShowsServiceModelBudgetsAndTokens() {
        answeredByJev();
        var status = diagnostics.status();
        assertEquals(1, status.sessions().byLevel().get(ControlLevel.JEV));
        assertEquals(1, status.sessions().byLevel().get(ControlLevel.BASE));
        assertEquals(2, status.sessions().byState().get(SessionState.ACTIVE));
        assertEquals(0, status.sessions().byState().get(SessionState.SUSPENDED));
        assertEquals(ServiceState.READY, status.service().status().state());
        assertTrue(status.service().pauseRemainingMillis().isEmpty());
        assertEquals("jev-1.13.0", status.service().configuredModel());
        assertEquals(Optional.of("jev-1.13.0"), status.service().lastModel());
        assertEquals(new StatusReport.Budget(1, 2000, 0, 8), status.server());
        assertTrue(status.world().isEmpty());
        assertEquals(new StatusReport.Budget(1, 2000, 0, 4), diagnostics.status(EngineFixtures.WORLD).world().orElseThrow());
        assertEquals(new StatusReport.Budget(0, 2000, 0, 4),
                diagnostics.status(EngineFixtures.OTHER_WORLD).world().orElseThrow());
        assertEquals(400, status.consumption().inputTokensLastMinute());
        assertEquals(1, status.encounters().active());
        assertEquals(1, status.latency().get(DecisionMetrics.Span.ROUNDTRIP).samples());
        assertTrue(status.latency().get(DecisionMetrics.Span.CAPTURE).p95Micros().isPresent());
    }

    @Test
    void observeWithoutAVerdictNamesTheUnverifiedCauseAndOffClearsIt() {
        assertTrue(diagnostics.status().patterns().isEmpty());
        var gate = new PatternModeGate();
        assertEquals(PatternMode.OBSERVE, gate.effective());
        assertEquals(PatternModeGate.UNVERIFIED, gate.cause());
        gate.offer(PatternMode.BIAS, false);
        diagnostics.hearPatterns(gate);
        var heard = diagnostics.status().patterns().orElseThrow();
        assertEquals("observe", heard.mode());
        assertEquals(PatternModeGate.UNVERIFIED, heard.cause());
        gate.offer(PatternMode.FULL, false);
        assertEquals("observe", diagnostics.status().patterns().orElseThrow().mode());
        gate.offer(PatternMode.OFF, false);
        var off = diagnostics.status().patterns().orElseThrow();
        assertEquals("off", off.mode());
        assertEquals("", off.cause());
    }

    @Test
    void recentCausesKeepTheLastThreeWithTheirAgeAndCountersShowOnlyNonZeroValues() {
        for (var kind : List.of(FailureKind.NETWORK, FailureKind.TIMEOUT, FailureKind.TIMEOUT, FailureKind.OVERLOADED)) {
            fixture.metrics.increment(kind);
            fixture.clock.tick += 20;
        }
        fixture.metrics.increment(FallbackReason.SERVICE_PAUSED);
        var status = diagnostics.status();
        assertEquals(List.of(new StatusReport.RecentCause("TIMEOUT", 60), new StatusReport.RecentCause("TIMEOUT", 40),
                new StatusReport.RecentCause("OVERLOADED", 20)), status.recentFailures());
        assertEquals(List.of(new StatusReport.RecentCause("SERVICE_PAUSED", 0)), status.recentFallbacks());
        var failures = status.counters().stream().filter(group -> group.name().equals("failures")).findFirst()
                .orElseThrow();
        assertEquals(List.of("OVERLOADED", "NETWORK", "TIMEOUT"), List.copyOf(failures.values().keySet()));
        assertEquals(2L, failures.values().get("TIMEOUT"));
        assertEquals(List.of("totals", "failures", "dispatch", "fallbacks", "outcomes", "discards"),
                status.counters().stream().map(StatusReport.CounterGroup::name).toList());
    }

    @Test
    void aPausedServiceReportsTheTimeLeftAndTheSignalsReportTheirDrops() {
        fixture.ready();
        fixture.tracker.onResult(new DecisionResult.Failed(FailureKind.NETWORK, Optional.empty(), "network"),
                fixture.clock.nanos);
        for (var index = 0; index <= ContextSignals.CAPACITY; index++) {
            signals.offer(EngineFixtures.id(1000 + index));
        }
        var status = diagnostics.status();
        assertEquals(ServiceState.PAUSED, status.service().status().state());
        var remaining = status.service().pauseRemainingMillis().orElseThrow();
        assertTrue(remaining >= 800 && remaining <= 1200, String.valueOf(remaining));
        assertEquals(ContextSignals.CAPACITY, status.context().signalsPending());
        assertEquals(1, status.context().signalsDropped());
    }

    @Test
    void theSessionReportExplainsLevelIntentGoalsAndThreatTableAndIsEmptyWithoutSession() {
        answeredByJev();
        fixture.mobs.goals.put(EngineFixtures.id(1), List.of(new GoalSummary("thepredict:hold", Set.of("MOVE"), true)));
        var report = diagnostics.session(EngineFixtures.id(1)).orElseThrow();
        assertEquals(new MobKey(EngineFixtures.id(1), 0), report.key());
        assertEquals("Guardian", report.mythicMobId());
        assertEquals("guardian_ai", report.profileId());
        assertEquals(ControlLevel.JEV, report.level());
        var intent = report.intent().orElseThrow();
        assertEquals(ExecutionFixture.KEEP, intent.tactic());
        assertFalse(intent.debug());
        assertTrue(intent.remainingTicks() > 0);
        assertEquals(1, report.goals().size());
        assertEquals(Optional.of(ThreatTableState.DISABLED), report.threatTable());
        var alias = report.perception().orElseThrow().target().orElseThrow().target();
        assertEquals(alias, report.targetFacts().orElseThrow().alias());
        assertEquals(1, report.perceivedPlayers());
        assertEquals(0.0, report.threatAddedInWindowPoints());
        assertTrue(report.encounter().isPresent());
        var idle = diagnostics.session(EngineFixtures.id(2)).orElseThrow();
        assertEquals(ControlLevel.BASE, idle.level());
        assertTrue(idle.intent().isEmpty());
        assertTrue(idle.perception().isEmpty() && idle.targetFacts().isEmpty());
        assertTrue(diagnostics.session(EngineFixtures.id(9)).isEmpty());
        assertTrue(diagnostics.bound(EngineFixtures.id(1)) && !diagnostics.bound(EngineFixtures.id(9)));
    }

    @Test
    void goalsOfAMobWithoutSessionAreReadWithGenerationZero() {
        fixture.open(1);
        fixture.sessions.registry.advanceGeneration(EngineFixtures.id(1));
        diagnostics.goals(EngineFixtures.id(1));
        diagnostics.goals(EngineFixtures.id(9));
        diagnostics.progress(EngineFixtures.id(9));
        assertEquals(List.of(new MobKey(EngineFixtures.id(1), 1), new MobKey(EngineFixtures.id(9), 0)),
                fixture.mobs.goalReads);
        assertEquals(new MobKey(EngineFixtures.id(9), 0), fixture.mobs.progressReads.getLast());
    }

    @Test
    void theThreatOfTheOpenWindowsOfAMemberTableIsShownPerMob() {
        answeredByJev();
        var encounter = fixture.sessions.encounters.find(fixture.session(1).encounter().orElseThrow()).orElseThrow();
        encounter.threat().admit(fixture.session(1).key(), EngineFixtures.player(1),
                EventType.MELEE_BLOCKED, 0.25, 20, fixture.clock.tick);
        assertEquals(0.25, diagnostics.session(EngineFixtures.id(1)).orElseThrow().threatAddedInWindowPoints());
        fixture.clock.tick += 100;
        assertEquals(0.0, diagnostics.session(EngineFixtures.id(1)).orElseThrow().threatAddedInWindowPoints());
    }

    @Test
    void aReportKeepsOnlyTheLastEightEventsOfTheCapture() {
        var events = new ArrayList<EncounterEvent>();
        for (var tick = 0; tick < 12; tick++) {
            events.add(new EncounterEvent(tick, EventType.MELEE_HIT, Optional.empty(), Optional.empty()));
        }
        var report = new SessionReport(new MobKey(EngineFixtures.id(1), 0), "Guardian", "guardian_ai",
                SessionState.ACTIVE, ControlLevel.BASE, Optional.empty(), Optional.empty(), 0, 0, false,
                OptionalLong.empty(), List.of(), new MovementProgress(false, false, false,
                OptionalDouble.empty()), Optional.empty(), Optional.empty(), 0, events, 0, Optional.empty(),
                0.0, Optional.empty(), Optional.empty(), Map.of(), List.of());
        assertEquals(SessionReport.LISTED_EVENTS, report.recentEvents().size());
        assertEquals(4, report.recentEvents().getFirst().tick());
    }
}
