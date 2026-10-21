package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.ServiceState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.engine.LoadFixture.WORLD_A;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Load steps of M13.3 over the whole engine with a geometric double of the world (rules A5, T1, T5, P2): the
 * mandatory conditions of plan §12 that do not need a server. No world access from a callback, no duplicated cast per
 * answer, bounded queues and maps, no request with the Predictor off, and the fallback under load. Nothing here is
 * timed; the time per tick is measured by {@code M13Measurements}, outside the build.
 */
class CoordinatorLoadTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 10, 30})
    void aStepOfBoundMobsInTwoWorldsStaysWithinEveryLimitForTenMinutesOfCombat(int mobCount) {
        try (var fixture = LoadScenarios.combat(mobCount)) {
            fixture.run(LoadScenarios.TEN_MINUTES_TICKS, LoadScenarios.combatScript(fixture, mobCount));

            var peaks = fixture.peaks;
            var settings = fixture.settings;
            assertTrue(peaks.capturesPerTick <= settings.maxEncounterEvaluationsPerTick(), "captures per tick");
            assertTrue(peaks.requestsPerTick <= settings.maxEncounterEvaluationsPerTick(), "requests per tick");
            assertTrue(peaks.probesPerTick <= settings.maxProbesPerTick(), "probes per tick");
            assertTrue(peaks.installsPerTick <= settings.maxInstallsPerTick(), "installs per tick");
            assertTrue(peaks.inbox <= settings.inboxCapacity(), "inbox");
            assertTrue(peaks.inFlight <= settings.maxInFlightRequests(), "in flight");
            assertTrue(peaks.inFlightPerWorld <= settings.maxInFlightRequestsPerWorld(), "in flight per world");
            assertTrue(peaks.requestsPerMinute <= settings.maxRequestsPerMinute(), "requests per minute");
            assertTrue(peaks.requestsPerMinutePerWorld <= settings.maxRequestsPerMinutePerWorld(), "per world");
            assertTrue(peaks.tokensPerMinute <= settings.maxInputTokensPerMinute(), "tokens per minute");
            assertTrue(peaks.encounters <= mobCount, "encounters");
            assertTrue(peaks.members <= EncounterRegistry.MAX_MEMBERS, "members");
            assertTrue(peaks.participants <= EncounterRegistry.MAX_PARTICIPANTS, "participants");
            assertTrue(peaks.events <= 16, "event history");
            assertTrue(peaks.threatWindows <= 768, "threat windows");
            assertTrue(peaks.signalsPending <= ContextSignals.CAPACITY, "signals");
            assertTrue(peaks.views <= mobCount, "views");

            assertFalse(fixture.jev.requests.isEmpty());
            assertTrue(fixture.actions.casts > 0);
            assertEquals(0, fixture.actions.duplicateCasts(), "one cast per intent");
            assertTrue(peaks.baseRun <= fixture.profile.timing().minimumDecisionIntervalTicks() + 2,
                    "a mob with a target only waits at level 4 from a target switch to the next capture");
            onlyTheServerThreadReachedThePorts(fixture);
        }
    }

    @Test
    void turningThePredictorOffUnbindsEveryMobAndNoRequestIsSentAfterwards() {
        try (var fixture = LoadScenarios.combat(30)) {
            fixture.run(LoadScenarios.MINUTE_TICKS, LoadScenarios.combatScript(fixture, 30));
            fixture.sessions.lifecycle.reloaded(mythicMobId -> Optional.empty());
            var sent = fixture.jev.requests.size();
            fixture.run(LoadScenarios.MINUTE_TICKS, LoadScenarios.combatScript(fixture, 30));

            assertEquals(sent, fixture.jev.requests.size(), "no request with the Predictor off");
            assertTrue(fixture.sessions.registry.all().isEmpty());
            assertEquals(0, fixture.sessions.board.size());
            assertEquals(0, fixture.sessions.encounters.size());
            assertEquals(30, fixture.mobs.released.size(), "every goal set is released once");
            assertEquals(0, fixture.sessions.lifecycle.pendingReleases());
        }
    }

    @Test
    void thirtyBoundMobsLoadingTogetherAreInstalledTenPerTick() {
        try (var fixture = new LoadFixture()) {
            fixture.player(1, WORLD_A, 0, 0);
            for (var index = 1; index <= 30; index++) {
                fixture.spawn(index, WORLD_A, index, 0, 1);
            }
            var perTick = new long[4];
            for (var tick = 0; tick < perTick.length; tick++) {
                var before = fixture.mobs.installs;
                fixture.run(1);
                perTick[tick] = fixture.mobs.installs - before;
            }
            assertEquals(10, perTick[0]);
            assertEquals(10, perTick[1]);
            assertEquals(10, perTick[2]);
            assertTrue(fixture.sessions.registry.active().stream().noneMatch(MobSession::installPending));
        }
    }

    @Test
    void aFloodOfSignalsIsBoundedDrainedSixtyFourPerTickAndCountedWhenDropped() {
        try (var fixture = LoadScenarios.combat(30)) {
            for (var round = 0; round < 200; round++) {
                for (var index = 1; index <= 30; index++) {
                    fixture.signals.offer(LoadFixture.mob(index));
                }
            }
            assertTrue(fixture.signals.pendingCount() <= ContextSignals.CAPACITY);
            var before = fixture.signals.pendingCount();
            fixture.run(1);
            assertTrue(before - fixture.signals.pendingCount() <= fixture.settings.maxSignalsPerTick());
            fixture.run(40);
            assertEquals(0, fixture.signals.pendingCount());
            assertTrue(fixture.peaks.capturesPerTick <= fixture.settings.maxEncounterEvaluationsPerTick(),
                    "many signals for one encounter produce one advance, not a burst of evaluations");
        }
    }

    @Test
    void anOutageUnderLoadSendsNothingWhilePausedFallsBackAndRecoversWithTheFirstAnswer() {
        try (var fixture = LoadScenarios.combat(30)) {
            var script = LoadScenarios.combatScript(fixture, 30);
            fixture.run(LoadScenarios.MINUTE_TICKS, script);
            fixture.jev.failure = Optional.of(FailureKind.NETWORK);
            fixture.run(LoadScenarios.MINUTE_TICKS, script);
            assertTrue(fixture.peaks.pausedTicks > 0);
            assertEquals(0, fixture.peaks.requestsWhilePaused, "no request leaves while the service is paused");
            assertEquals(0, fixture.atLevel(ControlLevel.JEV), "no mob keeps an answer of a failed service");
            assertEquals(30, fixture.atLevel(ControlLevel.LOCAL) + fixture.atLevel(ControlLevel.BASE));

            fixture.jev.failure = Optional.empty();
            fixture.run(2 * LoadScenarios.MINUTE_TICKS, script);
            assertEquals(ServiceState.READY, fixture.tracker.state());
            assertTrue(fixture.atLevel(ControlLevel.JEV) + fixture.atLevel(ControlLevel.ALTERNATIVE) > 0,
                    "back to Jev with the first accepted answer");
            assertEquals(0, fixture.actions.duplicateCasts());
            onlyTheServerThreadReachedThePorts(fixture);
        }
    }

    private static void onlyTheServerThreadReachedThePorts(LoadFixture fixture) {
        var server = Set.of(fixture.serverThread);
        assertEquals(server, fixture.arena.threads, "combat reader");
        assertEquals(server, fixture.mobs.threads, "mob control");
        assertEquals(server, fixture.actions.threads, "combat actions");
        assertEquals(server, fixture.jev.threads, "decision client");
        assertFalse(fixture.jev.completionThreads.contains(fixture.serverThread), "answers come from another thread");
        assertFalse(fixture.jev.completionThreads.isEmpty());
    }
}
