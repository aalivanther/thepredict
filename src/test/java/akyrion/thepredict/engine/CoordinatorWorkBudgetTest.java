package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static akyrion.thepredict.engine.LoadFixture.WORLD_A;
import static akyrion.thepredict.engine.LoadFixture.WORLD_B;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The work budget of the coordinator against expensive captures and probes (pending of M09 for M13):
 * {@code maxTickWorkMicros} is checked before each evaluation, not during a capture, so an expensive capture defers
 * the rest to the next tick. The simulated cost only moves the controllable clock; nothing is timed (rule P2).
 */
class CoordinatorWorkBudgetTest {
    @ParameterizedTest
    @CsvSource({"10, 3000", "40, 3000", "40, 1500", "20, 500"})
    void expensiveCapturesDeferEvaluationsButNoEncounterOrWorldStarves(int encounters, int captureMicros) {
        try (var fixture = LoadScenarios.separateEncounters(encounters, LoadFixture.settings(2000))) {
            fixture.arena.captureCostNanos = captureMicros * 1_000L;
            fixture.resetPeaks();
            fixture.run(LoadScenarios.MINUTE_TICKS);

            var perTick = Math.max(1, (2000 + captureMicros - 1) / captureMicros);
            var round = (encounters + Math.min(perTick, 2) - 1) / Math.min(perTick, 2);
            var heartbeat = fixture.profile.timing().heartbeatTicks();
            assertTrue(fixture.arena.maxCaptureGapTicks <= Math.max(heartbeat, round) + round,
                    "no encounter waits longer than one full round after its heartbeat");
            var worldA = fixture.arena.capturesPerWorld.getOrDefault(WORLD_A, 0L);
            var worldB = fixture.arena.capturesPerWorld.getOrDefault(WORLD_B, 0L);
            assertTrue(Math.abs(worldA - worldB) <= Math.max(2, (worldA + worldB) / 10), "worlds share the budget");
            if (captureMicros >= 2000) {
                assertTrue(fixture.peaks.capturesPerTick <= 1, "one expensive capture spends the budget of its tick");
                assertTrue(fixture.metrics.total(DecisionMetrics.Counter.DEFERRED_TICKS) > 0);
            }
        }
    }

    @Test
    void threeWorldsShareAnExhaustedBudgetEvenly() {
        try (var fixture = LoadScenarios.separateEncounters(30, 3, LoadFixture.settings(2000))) {
            fixture.arena.captureCostNanos = 3_000_000L;
            fixture.resetPeaks();
            fixture.run(LoadScenarios.MINUTE_TICKS);
            var captures = List.of(WORLD_A, WORLD_B, LoadFixture.WORLD_C).stream()
                    .map(world -> fixture.arena.capturesPerWorld.getOrDefault(world, 0L)).toList();
            var most = captures.stream().mapToLong(Long::longValue).max().orElseThrow();
            var least = captures.stream().mapToLong(Long::longValue).min().orElseThrow();
            assertTrue(least > 0 && most - least <= 2, "captures per world " + captures);
            assertTrue(fixture.arena.maxCaptureGapTicks <= 30 + 2, "no encounter waits more than one round");
        }
    }

    @Test
    void aTickWithoutAnyDueEncounterOrPendingInstallIsNeverCountedAsDeferred() {
        try (var fixture = new LoadFixture()) {
            fixture.player(1, WORLD_A, 0, 0);
            fixture.spawn(1, WORLD_A, 4, 0, 0);
            fixture.ready();
            fixture.run(5);
            fixture.arena.probeCostNanos = 3_000_000L;
            fixture.run(200);
            assertEquals(0, fixture.metrics.total(DecisionMetrics.Counter.DEFERRED_TICKS),
                    "the budget ran out but no decision or installation was waiting");
        }
    }

    @Test
    void probesThatSpendTheWholeBudgetLeaveNoRoomForEvaluationsUntilTheyGetCheaper() {
        try (var fixture = LoadScenarios.combat(30)) {
            var captures = fixture.arena.captures;
            fixture.arena.probeCostNanos = 2_000_000L / 15 + 1;
            fixture.run(200);
            assertEquals(captures, fixture.arena.captures, "15 probes per tick at 134 us leave no evaluation");
            assertTrue(fixture.metrics.total(DecisionMetrics.Counter.DEFERRED_TICKS) >= 200);

            fixture.arena.probeCostNanos = 20_000L;
            fixture.run(200);
            assertTrue(fixture.arena.captures > captures, "evaluations resume once the probes fit in the budget");
        }
    }
}
