package akyrion.thepredict.engine;

import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FallbackReason;

import java.util.ArrayList;
import java.util.List;

import static akyrion.thepredict.MeasurementReport.number;

/**
 * Local measurements of the engine for M13, part one: the load steps with their time per tick, the fragmentation of
 * encounters, the cost of {@code assign} and the reconciliation. Run by {@code ./gradlew m13Measure}, never by the
 * build. Counts are deterministic; times are wall times of this machine and vary between runs.
 */
public final class EngineMeasurements {
    private static final int[] STEPS = {1, 10, 30, 100, 200};

    private EngineMeasurements() {
    }

    /**
     * Writes every measurement of this part.
     *
     * @param report report to write into
     */
    public static void write(MeasurementReport report) {
        loadSteps(report);
        localLoadSteps(report);
        fragmentation(report);
        assignCost(report);
        reconciliation(report);
    }

    private static void loadSteps(MeasurementReport report) {
        report.section("Load steps of M13.3 (engine with doubles, 10 minutes of combat, two worlds, two players each)");
        report.line("Counts are exact for the scripted fight; the time is the wall time of `Coordinator.tick()` with O(1)"
                + " doubles, so it is the engine's own cost without captures, probes or installs of a server.");
        var rows = new ArrayList<List<String>>();
        for (var mobs : STEPS) {
            try (var warmUp = LoadScenarios.combat(mobs)) {
                warmUp.run(LoadScenarios.TEN_MINUTES_TICKS, LoadScenarios.combatScript(warmUp, mobs));
            }
            try (var fixture = LoadScenarios.combat(mobs)) {
                var nanos = new long[LoadScenarios.TEN_MINUTES_TICKS];
                fixture.runTimed(LoadScenarios.TEN_MINUTES_TICKS, LoadScenarios.combatScript(fixture, mobs), nanos);
                var peaks = fixture.peaks;
                rows.add(List.of(String.valueOf(mobs), String.valueOf(fixture.jev.requests.size()),
                        String.valueOf(fixture.jev.tokens), number(fixture.jev.tokens * 0.042 / 1_000_000, 4),
                        String.valueOf(peaks.requestsPerMinute), String.valueOf(peaks.tokensPerMinute),
                        String.valueOf(peaks.inFlight), String.valueOf(peaks.encounters),
                        peaks.capturesPerTick + "/" + peaks.probesPerTick + "/" + peaks.installsPerTick,
                        String.valueOf(fixture.actions.casts), String.valueOf(fixture.actions.duplicateCasts()),
                        number(100.0 * peaks.baseTicks / Math.max(1, peaks.mobTicks), 2) + " % / " + peaks.baseRun,
                        MeasurementReport.meanMicros(nanos), MeasurementReport.micros(nanos)));
            }
        }
        report.table(List.of("Mobs", "Requests", "Tokens (0.0.1 estimate)", "USD at 0.042", "Peak req/min",
                "Peak est. tokens/min", "Peak in flight",
                "Peak encounters", "Peak captures/probes/installs per tick", "Casts", "Duplicated casts",
                "Base AI share / longest run (ticks)", "Mean us/tick", "p50 / p95 / p99 / max us/tick"), rows);
        levelsNote(report);
    }

    private static void localLoadSteps(MeasurementReport report) {
        report.section("Load steps without a key (every session LOCAL, real-time readiness of M10; native Cooldown of"
                + " the doubles 20 ticks)");
        report.line("The same fight as the load steps, with the service UNCONFIGURED: while a skill cools down each"
                + " reactive cycle reads one O(1) cooldown per eligible tactic and runs the local planner once.");
        var rows = new ArrayList<List<String>>();
        for (var mobs : new int[] {30, 100, 200}) {
            try (var warmUp = LoadScenarios.combatWithoutKey(mobs)) {
                warmUp.run(LoadScenarios.TEN_MINUTES_TICKS, LoadScenarios.combatScript(warmUp, mobs));
            }
            try (var fixture = LoadScenarios.combatWithoutKey(mobs)) {
                var nanos = new long[LoadScenarios.TEN_MINUTES_TICKS];
                fixture.runTimed(LoadScenarios.TEN_MINUTES_TICKS, LoadScenarios.combatScript(fixture, mobs), nanos);
                rows.add(List.of(String.valueOf(mobs), String.valueOf(fixture.jev.requests.size()),
                        String.valueOf(fixture.actions.casts), String.valueOf(fixture.actions.duplicateCasts()),
                        String.valueOf(fixture.metrics.count(ExecutionOutcome.NOT_ELIGIBLE)),
                        MeasurementReport.meanMicros(nanos), MeasurementReport.micros(nanos)));
            }
        }
        report.table(List.of("Mobs", "Requests", "Casts", "Duplicated casts", "NOT_ELIGIBLE counted", "Mean us/tick",
                "p50 / p95 / p99 / max us/tick"), rows);
    }

    private static void fragmentation(MeasurementReport report) {
        report.section("Encounter fragmentation (pending of M06)");
        var rows = new ArrayList<List<String>>();
        for (var players : new int[] {1, 10}) {
            for (var seed = 1L; seed <= 3; seed++) {
                try (var fixture = LoadScenarios.scattered(200, players, 320, seed)) {
                    fixture.run(20 * fixture.profile.timing().heartbeatTicks());
                    var minimum = LoadScenarios.minimumEncounters(fixture);
                    rows.add(List.of("200", String.valueOf(players), String.valueOf(seed),
                            String.valueOf(fixture.sessions.encounters.size()), String.valueOf(minimum),
                            String.valueOf(fixture.sessions.encounters.size() - minimum),
                            String.valueOf(fixture.peaks.requestsPerMinute)));
                }
            }
        }
        report.table(List.of("Mobs", "Players", "Seed", "Encounters", "Fewest possible", "Extra", "Peak req/min"),
                rows);
        report.line("Two groups of 5 mobs that meet after forming apart: " + twoGroups(true) + " encounters and "
                + twoGroups(false) + " when the same 10 mobs start together (requests per minute follow the"
                + " encounter count).");
    }

    private static String twoGroups(boolean apart) {
        try (var fixture = new LoadFixture()) {
            fixture.player(1, LoadFixture.WORLD_A, 0, 0);
            for (var index = 1; index <= 10; index++) {
                fixture.spawn(index, LoadFixture.WORLD_A, apart ? (index <= 5 ? -60 - index : 60 + index) : index, 0, 1);
            }
            fixture.ready();
            fixture.run(40);
            for (var index = 1; index <= 10; index++) {
                fixture.move(index, index - 5.5, 1);
            }
            fixture.resetPeaks();
            fixture.run(LoadScenarios.MINUTE_TICKS);
            return fixture.sessions.encounters.size() + " (" + fixture.jev.requests.size() + " requests in the first"
                    + " 62 s)";
        }
    }

    private static void assignCost(MeasurementReport report) {
        report.section("Cost of EncounterRegistry.assign with scattered mobs (pending of M06)");
        var rows = new ArrayList<List<String>>();
        for (var players : new int[] {1, 10}) {
            EncounterScale.run(200, players, 320, 3, 99L);
            var scale = EncounterScale.run(200, players, 320, 3, 13L);
            var calls = scale.calls().size();
            rows.add(List.of("200", String.valueOf(players), String.valueOf(calls), String.valueOf(scale.encounters()),
                    String.valueOf(scale.maxCandidates()), String.valueOf(scale.maxChecks()),
                    number(scale.meanChecks(), 2), number(scale.nanos() / 1000.0 / calls, 3)));
        }
        report.table(List.of("Mobs", "Players", "Calls", "Open encounters at the end", "Most encounters of one target (k)",
                "Most predicates in one call", "Mean predicates per call", "Mean us per call"), rows);
    }

    private static void reconciliation(MeasurementReport report) {
        report.section("Reconciliation with many sessions (pending of M06)");
        var rows = new ArrayList<List<String>>();
        for (var count : new int[] {200, 1000, 7000}) {
            var sessions = new EngineFixtures.Sessions();
            for (var index = 1; index <= count; index++) {
                sessions.open(index);
            }
            var nanos = new long[SessionReconciler.ROUND_INTERVAL_TICKS * 2];
            for (var tick = 0; tick < nanos.length; tick++) {
                var started = System.nanoTime();
                sessions.reconciler.tick(tick);
                nanos[tick] = System.nanoTime() - started;
            }
            var round = (count + SessionReconciler.SESSIONS_PER_TICK - 1) / SessionReconciler.SESSIONS_PER_TICK;
            rows.add(List.of(String.valueOf(count), String.valueOf(round),
                    String.valueOf(SessionReconciler.ROUND_INTERVAL_TICKS + round), MeasurementReport.micros(nanos)));
        }
        report.table(List.of("Active sessions", "Ticks per round", "Worst ticks until a ghost closes",
                "p50 / p95 / p99 / max us per tick (the round start copies every UUID)"), rows);
        report.line("Suspended sessions are never reconciled: 40 mobs removed while unloaded stay 40 suspended sessions"
                + " after one simulated hour (`SessionReconcilerScaleTest`). Their growth rate needs a server.");
    }

    private static void levelsNote(MeasurementReport report) {
        try (var fixture = LoadScenarios.combat(30)) {
            fixture.run(LoadScenarios.TEN_MINUTES_TICKS, LoadScenarios.combatScript(fixture, 30));
            report.line("Levels at the end of the 30-mob step: JEV " + fixture.atLevel(ControlLevel.JEV) + ", ALTERNATIVE "
                    + fixture.atLevel(ControlLevel.ALTERNATIVE) + ", LOCAL " + fixture.atLevel(ControlLevel.LOCAL)
                    + ", BASE " + fixture.atLevel(ControlLevel.BASE) + "; fallbacks EXPIRED "
                    + fixture.metrics.count(FallbackReason.EXPIRED) + " (one per target switch of the script).");
        }
    }
}
