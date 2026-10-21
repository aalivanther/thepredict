package akyrion.thepredict.engine;

import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.ToLongFunction;

import static akyrion.thepredict.MeasurementReport.number;

/**
 * Local measurements of the engine for M13, part two: the work budget against expensive captures and probes, the
 * stuck local mob, the effective budget window, the pause thresholds, and the two A/B harnesses. Every number here is
 * deterministic; the simulated costs only move the controllable clock.
 */
public final class EngineScenarioMeasurements {
    private EngineScenarioMeasurements() {
    }

    /**
     * Writes every measurement of this part.
     *
     * @param report report to write into
     */
    public static void write(MeasurementReport report) {
        workBudget(report);
        probeBudget(report);
        stuckMob(report);
        budgetWindow(report);
        pauses(report);
        tacticalAb(report, 0.85);
        tacticalAb(report, 1.0);
        rearmCadence(report);
        threatAb(report);
    }

    private static void workBudget(MeasurementReport report) {
        report.section("Work budget against expensive captures (pending of M09, maxTickWorkMicros 2000)");
        var rows = new ArrayList<List<String>>();
        for (var encounters : new int[] {10, 20, 40}) {
            for (var captureMicros : new int[] {500, 1500, 3000}) {
                try (var fixture = LoadScenarios.separateEncounters(encounters, LoadFixture.settings(2000))) {
                    fixture.arena.captureCostNanos = captureMicros * 1_000L;
                    fixture.resetPeaks();
                    fixture.run(LoadScenarios.MINUTE_TICKS);
                    rows.add(List.of(String.valueOf(encounters), String.valueOf(captureMicros),
                            String.valueOf(fixture.peaks.capturesPerTick), String.valueOf(fixture.arena.maxCaptureGapTicks),
                            String.valueOf(fixture.metrics.total(DecisionMetrics.Counter.DEFERRED_TICKS)),
                            fixture.arena.capturesPerWorld.getOrDefault(LoadFixture.WORLD_A, 0L) + " / "
                                    + fixture.arena.capturesPerWorld.getOrDefault(LoadFixture.WORLD_B, 0L)));
                }
            }
        }
        report.table(List.of("Encounters (2 worlds)", "Simulated capture us", "Most captures in a tick",
                "Longest gap between captures of one encounter (ticks)", "Deferred ticks in 60 s",
                "Captures world A / B"), rows);
    }

    private static void probeBudget(MeasurementReport report) {
        report.section("Work budget against expensive probes (steps 1 to 4 always run)");
        var rows = new ArrayList<List<String>>();
        for (var probeMicros : new long[] {10, 60, 120, 134, 200}) {
            try (var fixture = LoadScenarios.combat(30)) {
                fixture.arena.probeCostNanos = probeMicros * 1_000L;
                var before = fixture.arena.captures;
                fixture.run(LoadScenarios.MINUTE_TICKS, LoadScenarios.combatScript(fixture, 30));
                rows.add(List.of("30", String.valueOf(probeMicros), String.valueOf(fixture.arena.captures - before),
                        String.valueOf(fixture.metrics.total(DecisionMetrics.Counter.DEFERRED_TICKS))));
            }
        }
        report.table(List.of("Mobs", "Simulated probe us", "Captures in 60 s", "Deferred ticks"), rows);
        report.line("Evaluations stop once the probes of one tick reach the budget: with half the sessions probed per"
                + " tick (at most maxProbesPerTick = 64), about 2000 / (mobs / 2) us per probe; 31 us from 128 mobs on.");
    }

    private static void stuckMob(MeasurementReport report) {
        report.section("Stuck mob: LOCAL/BASE alternation (pending of M10, deviation 18)");
        var rows = new ArrayList<List<String>>();
        rows.add(row("without Jev", StuckMobs.withoutJev(LoadScenarios.MINUTE_TICKS)));
        rows.add(row("Jev insists on press_attack", StuckMobs.withJevInsisting(LoadScenarios.MINUTE_TICKS,
                ExecutionFixture.PRESS)));
        report.table(List.of("Case (one minute)", "Falls to BASE", "Ticks JEV/ALT/LOCAL/BASE", "NO_PROGRESS",
                "Requests", "Tactic signals", "Cast calls"), rows);
    }

    private static List<String> row(String name, StuckMobs.Alternation alternation) {
        var ticks = alternation.ticksAt();
        return List.of(name, String.valueOf(alternation.switchesToBase()),
                ticks.getOrDefault(ControlLevel.JEV, 0) + "/"
                        + ticks.getOrDefault(ControlLevel.ALTERNATIVE, 0) + "/"
                        + ticks.getOrDefault(ControlLevel.LOCAL, 0) + "/"
                        + ticks.getOrDefault(ControlLevel.BASE, 0),
                String.valueOf(alternation.noProgressFallbacks()), String.valueOf(alternation.requests()),
                String.valueOf(alternation.signals()), String.valueOf(alternation.casts()));
    }

    private static void budgetWindow(MeasurementReport report) {
        report.section("Effective budget window of 59-60 s (pending of M09, maxRequestsPerMinute 600)");
        var greedy = RequestBudgetWindow.sender(600, true, 10_000_000L, 300_000_000_000L);
        var rows = new ArrayList<List<String>>();
        rows.add(List.of("greedy, every 10 ms", String.valueOf(greedy.size()),
                String.valueOf(RequestBudgetWindow.mostInAnyMinute(greedy))));
        for (var perSecond : new int[] {10, 20, 40}) {
            var steady = RequestBudgetWindow.sender(600, false, 1_000_000_000L / perSecond, 300_000_000_000L);
            rows.add(List.of("steady, " + perSecond + " per second", String.valueOf(steady.size()),
                    String.valueOf(RequestBudgetWindow.mostInAnyMinute(steady))));
        }
        report.table(List.of("Sender over 5 minutes", "Admitted", "Most in any real 60 s"), rows);
    }

    private static void pauses(MeasurementReport report) {
        report.section("Pause thresholds (2 timeouts, 3 invalid responses): simulated independent failures");
        var rows = new ArrayList<List<String>>();
        for (var kind : new FailureKind[] {FailureKind.TIMEOUT, FailureKind.INVALID_RESPONSE}) {
            for (var ratio : new double[] {0.01, 0.05, 0.10, 0.20}) {
                var outcome = pauseRun(kind, ratio);
                rows.add(List.of(kind.name(), number(ratio * 100, 0) + " %", number(outcome[0], 2),
                        number(outcome[1], 2) + " %"));
            }
        }
        report.table(List.of("Failure", "Rate", "Pauses per 1000 requests", "Seconds without sending"), rows);
        report.line("One request per second, 10 000 seconds, pause factor 1.0, every check READY.");
    }

    private static double[] pauseRun(FailureKind kind, double ratio) {
        var tracker = new ServiceStatusTracker(new ServiceCheck(ServiceState.CHECKING, "start"), 2_000_000_000L,
                () -> 0.5, line -> {
                });
        var random = new Random(42);
        var pauses = 0;
        var idle = 0;
        var sent = 0;
        var previous = tracker.state();
        for (var second = 0; second < 10_000; second++) {
            var now = second * 1_000_000_000L;
            tracker.checkToStart(now).ifPresent(generation -> tracker.onCheck(generation,
                    new ServiceCheck(ServiceState.READY, "listed"), now));
            if (tracker.allowsDispatch(0)) {
                sent++;
                tracker.onResult(random.nextDouble() < ratio
                        ? new DecisionResult.Failed(kind, Optional.empty(), "scripted")
                        : new DecisionResult.Answered(Map.of(), ExecutionFixture.MODEL, 700), now);
            } else {
                idle++;
            }
            if (tracker.state() == ServiceState.PAUSED && previous != ServiceState.PAUSED) {
                pauses++;
            }
            previous = tracker.state();
        }
        return new double[] {1000.0 * pauses / Math.max(1, sent), idle / 100.0};
    }

    private static void tacticalAb(MeasurementReport report, double correctRatio) {
        report.section("Local A/B harness of M13.5, scripted Jev acceptable in " + number(correctRatio * 100, 0)
                + " % of NOW answers (10 seeds, arms alternated)");
        var rows = new ArrayList<List<String>>();
        for (var scenario : TacticalAB.Scenario.values()) {
            var results = TacticalAB.repeat(scenario, 10, correctRatio);
            for (var arm : TacticalAB.Arm.values()) {
                var list = results.get(arm);
                var useful = TacticalAB.Spread.of(list.stream().map(TacticalAB.Result::usefulShare).toList());
                var casts = TacticalAB.Spread.of(list.stream().map(result -> (double) result.casts()).toList());
                rows.add(List.of(scenario.name(), arm.name(), number(useful.mean(), 3) + " ± "
                                + number(useful.deviation(), 3) + " [" + number(useful.minimum(), 3) + ", "
                                + number(useful.maximum(), 3) + "]",
                        number(casts.mean(), 1) + " ± " + number(casts.deviation(), 1),
                        mean(list, TacticalAB.Result::skippedShield), mean(list, TacticalAB.Result::deferredInvulnerable),
                        mean(list, result -> result.tacticSwitches()), mean(list, result -> result.requests()),
                        mean(list, TacticalAB.Result::tokens), number(list.stream()
                                .mapToDouble(TacticalAB.Result::costUsd).average().orElse(0), 6),
                        String.valueOf(list.stream().mapToLong(TacticalAB.Result::duplicateCasts).sum()),
                        number(list.stream().mapToDouble(TacticalAB.Result::effect).average().orElse(0), 3),
                        number(list.stream().mapToDouble(TacticalAB.Result::hitRate).average().orElse(0), 3)));
            }
        }
        report.table(List.of("Scenario (30 s)", "Arm", "Time with an acceptable movement", "Casts",
                "SKIPPED_SHIELD", "DEFERRED_INVULNERABLE", "Tactic switches", "Requests", "Tokens (0.0.1 estimate)",
                "USD", "Duplicated casts", "Effect (M16.8)", "Hit rate"), rows);
    }

    private static void rearmCadence(MeasurementReport report) {
        report.section("Casts of the duel by maximumIntentDurationTicks (DT-28 with real-time readiness, Jev repeating the"
                + " same tactic, 10 seeds; minimumDecisionIntervalTicks 20; native Cooldown of the doubles 20 ticks)");
        var rows = new ArrayList<List<String>>();
        for (var maximum : new int[] {20, 40, 100}) {
            var timing = new DecisionTiming(20, 20, maximum, 0.5, 0.15, 1);
            var local = new ArrayList<Double>();
            var jev = new ArrayList<Double>();
            for (var seed = 1; seed <= 10; seed++) {
                local.add((double) TacticalAB.run(TacticalAB.Scenario.MELEE_DUEL, TacticalAB.Arm.WITHOUT_JEV, seed, 1.0,
                        timing).casts());
                jev.add((double) TacticalAB.run(TacticalAB.Scenario.MELEE_DUEL, TacticalAB.Arm.WITH_JEV, seed, 1.0,
                        timing).casts());
            }
            var localSpread = TacticalAB.Spread.of(local);
            var jevSpread = TacticalAB.Spread.of(jev);
            rows.add(List.of(String.valueOf(maximum), number(localSpread.mean(), 1) + " ± "
                    + number(localSpread.deviation(), 1), number(jevSpread.mean(), 1) + " ± "
                    + number(jevSpread.deviation(), 1)));
        }
        report.table(List.of("maximumIntentDurationTicks", "Casts without Jev (re-armed or replanned when ready)",
                "Casts with Jev (re-armed when ready)"), rows);
    }

    private static String mean(List<TacticalAB.Result> results,
                               ToLongFunction<TacticalAB.Result> value) {
        return number(results.stream().mapToLong(value).average().orElse(0), 1);
    }

    private static void threatAb(MeasurementReport report) {
        report.section("Enriched threat without and with the example weights (M11.6, model table)");
        var rows = new ArrayList<List<String>>();
        for (var scenario : ThreatAB.Scenario.values()) {
            for (var weights : List.of(ThreatAB.OFF, ThreatAB.EXAMPLE)) {
                var result = ThreatAB.run(scenario, weights);
                rows.add(List.of(scenario.name(), weights == ThreatAB.OFF ? "off (13.8)" : "example",
                        String.valueOf(result.leaderChanges()), String.valueOf(result.mostChangesInOneWindow()),
                        String.valueOf(result.firstTickAnotherLeads()), String.valueOf(result.portCalls()),
                        number(result.pointsAdded(), 2), number(result.pointsRemoved(), 2),
                        String.valueOf(result.limited())));
            }
        }
        report.table(List.of("Scenario", "Weights", "Leader changes", "Most changes in 100 ticks",
                "First tick another player leads", "Port calls", "Points added", "Points removed", "Limited"), rows);
    }
}
