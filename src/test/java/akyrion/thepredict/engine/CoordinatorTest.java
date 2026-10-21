package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static akyrion.thepredict.engine.EngineFixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinatorTest {
    private final CoordinatorFixture fixture = new CoordinatorFixture();

    private void sendFirstRequest() {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        for (var tick = 0; tick < 4 && fixture.client.requests.isEmpty(); tick++) {
            fixture.run(1);
        }
        assertEquals(1, fixture.client.requests.size());
    }

    @Test
    void theStorageStepRunsOnEveryTickAndStopsAfterFourReplies() {
        var calls = new int[1];
        var inbox = new Inbox<InboxEntry>(8);
        for (var id = 1; id <= 6; id++) {
            inbox.offerStorage(new StorageReply.BatchApplied(id, id));
        }
        var applied = new int[1];
        fixture.coordinator.useStorage((tick, nanos) -> {
            calls[0]++;
            applied[0] += inbox.drainStorage(4, reply -> {
            });
        });
        fixture.coordinator.tick();
        assertEquals(1, calls[0]);
        assertEquals(4, applied[0]);
        fixture.coordinator.tick();
        assertEquals(2, calls[0]);
        assertEquals(6, applied[0]);
    }

    /** SA-10: every tick publishes the server tick on the board before its steps, for readers off the server thread. */
    @Test
    void everyTickPublishesItsTickOnTheBoardBeforeItsSteps() {
        var board = fixture.sessions.board;
        assertEquals(0L, board.publishedTick());
        var seen = new ArrayList<Long>();
        fixture.coordinator.useStorage((tick, nanos) -> seen.add(board.publishedTick() - tick));
        fixture.clock.tick = 40;
        fixture.run(3);
        assertEquals(42L, board.publishedTick());
        assertEquals(List.of(0L, 0L, 0L), seen, "each step reads the tick it runs on");
    }

    @Test
    void anAnswerDepositedInTheInboxIsHandedOverOnTheVeryNextTick() {
        sendFirstRequest();
        assertEquals(1, fixture.budget.inFlight());
        fixture.client.futures.getFirst().complete(CoordinatorFixture.answered(520));
        assertEquals(1, fixture.inbox.size());
        fixture.coordinator.tick();
        assertEquals(1, fixture.steps.accepted.size());
        assertEquals(0, fixture.budget.inFlight());
        assertEquals(0, fixture.inFlight.size());
        assertEquals(1, fixture.metrics.total(DecisionMetrics.Counter.ANSWERED));
        assertEquals(520, fixture.metrics.total(DecisionMetrics.Counter.INPUT_TOKENS));
        assertEquals(1, fixture.metrics.samples(DecisionMetrics.Span.ROUNDTRIP));
        assertEquals(1, fixture.metrics.samples(DecisionMetrics.Span.APPLY));
    }

    @Test
    void theFirstTickStartsTheCheckAndTheTrialAllowsOneRequestUntilItsAnswer() {
        fixture.open(1);
        fixture.open(2);
        fixture.target(1, 1);
        fixture.target(2, 2);
        fixture.reader.followRange = (mob, member) -> false;
        fixture.run(1);
        assertEquals(1, fixture.client.checks);
        assertTrue(fixture.client.requests.isEmpty());
        fixture.run(25);
        assertTrue(fixture.tracker.inTrial());
        assertEquals(1, fixture.client.requests.size());
        assertTrue(fixture.metrics.count(DecisionDispatcher.Outcome.SERVICE_UNAVAILABLE) >= 1);
        fixture.client.futures.getFirst().complete(CoordinatorFixture.answered(400));
        fixture.run(25);
        assertFalse(fixture.tracker.inTrial());
        assertTrue(fixture.client.requests.size() >= 2);
    }

    @Test
    void noStepGoesBeyondItsLimitPerTick() {
        fixture.ready();
        for (var mob = 1; mob <= 200; mob++) {
            fixture.open(mob);
            fixture.target(mob, mob);
            fixture.signals.offer(id(mob));
        }
        fixture.reader.followRange = (mob, member) -> false;
        for (var index = 0; index < fixture.settings.inboxCapacity(); index++) {
            fixture.inbox.offer(new InboxEntry.CompletedCheck(-1, new ServiceCheck(ServiceState.READY, "old"), 0));
        }
        fixture.coordinator.tick();
        assertEquals(fixture.settings.inboxCapacity() - 16, fixture.inbox.size());
        assertEquals(200 - 64, fixture.signals.pendingCount());
        assertEquals(64, fixture.reader.probes);
        assertEquals(10, fixture.sessions.registry.active().stream().filter(session -> !session.installPending()).count());
        assertEquals(2, fixture.reader.captures);
    }

    @Test
    void installationsAndDecisionsWaitForTheNextTickOnceTheWorkBudgetIsSpent() {
        fixture.ready();
        fixture.control.nanosPerCall = 1_000_000;
        for (var mob = 1; mob <= 6; mob++) {
            fixture.open(mob);
        }
        fixture.signals.offer(id(1));
        fixture.coordinator.tick();
        assertEquals(2, fixture.control.calls.size());
        assertEquals(0, fixture.signals.pendingCount());
        assertEquals(1, fixture.lifecycleRuns);
        assertEquals(1, fixture.metrics.total(DecisionMetrics.Counter.DEFERRED_TICKS));
        fixture.run(3);
        assertEquals(6, fixture.control.calls.size());
    }

    @Test
    void aDecisionThatDidNotFitTheBudgetStaysDueForTheNextTick() {
        fixture.ready();
        var session = fixture.open(1);
        session.recordInstall(new InstallResult.Installed(3), 1);
        fixture.target(1, 1);
        for (var mob = 2; mob <= 6; mob++) {
            fixture.open(mob);
        }
        fixture.control.nanosPerCall = 1_000_000;
        fixture.clock.tick = Math.floorMod(id(1).hashCode(), 2);
        fixture.coordinator.tick();
        assertTrue(session.encounter().isPresent());
        assertEquals(0, fixture.reader.captures);
        assertEquals(1, fixture.metrics.total(DecisionMetrics.Counter.DEFERRED_TICKS));
        fixture.control.nanosPerCall = 0;
        fixture.clock.tick++;
        fixture.coordinator.tick();
        assertEquals(1, fixture.reader.captures);
        assertEquals(1, fixture.client.requests.size());
    }

    @Test
    void aRequestWithoutAnswerIsReleasedAfterItsDeadlineAndTheEncounterDecidesAgain() {
        sendFirstRequest();
        var future = fixture.client.futures.getFirst();
        fixture.clock.nanos += fixture.settings.watchdogNanos();
        fixture.coordinator.tick();
        assertTrue(future.isCancelled());
        assertEquals(0, fixture.budget.inFlight());
        assertEquals(0, fixture.inFlight.size());
        assertEquals(1, fixture.metrics.total(DecisionMetrics.Counter.WATCHDOG_RELEASES));
        assertEquals(1, fixture.metrics.count(FailureKind.TIMEOUT));
        var released = fixture.steps.accepted.getFirst().result();
        assertEquals(FailureKind.TIMEOUT, ((DecisionResult.Failed) released).kind());
        assertEquals(ServiceState.READY, fixture.tracker.status().state());
        fixture.run(25);
        assertEquals(2, fixture.client.requests.size());
    }

    @Test
    void anExceptionInOneSessionDropsOnlyThatSessionToTheBaseAi() {
        fixture.ready();
        var failing = fixture.open(1);
        fixture.open(2);
        fixture.steps.failing.add(id(1));
        fixture.run(4);
        assertTrue(fixture.steps.reacted.contains(fixture.sessions.registry.find(id(2)).orElseThrow().key()));
        assertEquals(Optional.of(FallbackReason.INTERNAL_ERROR), failing.lastFallbackReason());
        assertEquals(IntentView.base(0), fixture.sessions.board.current(id(1)));
        assertTrue(fixture.metrics.total(DecisionMetrics.Counter.SESSION_FAILURES) >= 1);
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.getFirst().contains("internal error"), fixture.warnings.getFirst());
    }

    @Test
    void aFailingStepIsContainedCountedAndLoggedByTypeOnly() {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.reader.failingCapture = true;
        fixture.run(3);
        assertTrue(fixture.metrics.total(DecisionMetrics.Counter.STEP_FAILURES) >= 1);
        assertTrue(fixture.warnings.getFirst().endsWith("IllegalStateException"), fixture.warnings.getFirst());
        assertEquals(1, fixture.warnings.size());
        assertFalse(fixture.warnings.getFirst().contains(id(1).toString()), fixture.warnings.getFirst());
        assertEquals(3, fixture.lifecycleRuns);
    }

    @Test
    void fiftyPendingInstallationsFinishInFiveTicksWithAtMostTenPerTick() {
        for (var mob = 1; mob <= 50; mob++) {
            fixture.open(mob);
        }
        for (var tick = 1; tick <= 5; tick++) {
            fixture.run(1);
            assertEquals(10 * tick, fixture.control.calls.size());
        }
        assertTrue(fixture.sessions.registry.active().stream().noneMatch(MobSession::installPending));
        fixture.run(3);
        assertEquals(50, fixture.control.calls.size());
    }

    @Test
    void aDeferredInstallationIsRetriedAndAnUnsupportedOneWaitsForANewGeneration() {
        var deferred = fixture.open(1);
        var unsupported = fixture.open(2);
        fixture.control.scripted.put(id(1), new ArrayDeque<>(List.of(new InstallResult.Deferred("not loaded"))));
        fixture.control.scripted.put(id(2), new ArrayDeque<>(List.of(new InstallResult.Unsupported("brain"))));
        fixture.run(1);
        assertTrue(deferred.installPending());
        assertFalse(unsupported.installPending());
        assertEquals(OptionalLong.empty(), unsupported.installedRevision());
        assertEquals(IntentView.base(0), fixture.sessions.board.current(id(2)));
        fixture.run(1);
        assertFalse(deferred.installPending());
        assertEquals(OptionalLong.of(1), deferred.installedRevision());
        fixture.run(10);
        assertEquals(1, fixture.control.callsFor(2));
        fixture.sessions.lifecycle.changedWorld(id(2));
        fixture.run(1);
        assertEquals(2, fixture.control.callsFor(2));
        fixture.revision = 2;
        fixture.sessions.lifecycle.reloaded(mobId -> Optional.of(CoordinatorFixture.profile(20, 20, 1)));
        fixture.run(1);
        assertEquals(3, fixture.control.callsFor(2));
        assertEquals(OptionalLong.of(2), unsupported.installedRevision());
    }

    @Test
    void theGoalsOfEveryMemberAreCheckedBeforeTheEncounterDecides() {
        fixture.ready();
        fixture.open(1);
        fixture.run(1);
        assertEquals(1, fixture.control.callsFor(1));
        fixture.target(1, 1);
        fixture.run(3);
        assertEquals(1, fixture.reader.captures);
        assertEquals(2, fixture.control.callsFor(1));
        fixture.control.scripted.put(id(1), new ArrayDeque<>(List.of(new InstallResult.Unsupported("goals removed"))));
        fixture.client.futures.getFirst().complete(CoordinatorFixture.answered(400));
        fixture.run(20);
        assertEquals(2, fixture.reader.captures);
        assertEquals(3, fixture.control.callsFor(1));
        assertEquals(1, fixture.client.requests.size());
        assertEquals(IntentView.base(0), fixture.sessions.board.current(id(1)));
    }

    @Test
    void anEncounterClosesAtTheFirstHeartbeatAfterItsCombatEnded() {
        sendFirstRequest();
        var encounter = fixture.sessions.registry.find(id(1)).orElseThrow().encounter().orElseThrow();
        fixture.client.futures.getFirst().complete(CoordinatorFixture.answered(400));
        fixture.reader.inCombat.add(id(1));
        fixture.reader.targets.clear();
        fixture.run(21);
        assertTrue(fixture.sessions.encounters.find(encounter).isPresent());
        fixture.reader.inCombat.clear();
        fixture.run(21);
        assertTrue(fixture.sessions.encounters.find(encounter).isEmpty());
        assertEquals(1, fixture.client.requests.size());
    }

    @Test
    void signalsMarkTheEncounterOfTheirMobAndSignalsWithoutSessionAreIgnored() {
        sendFirstRequest();
        var encounter = fixture.sessions.encounters.find(fixture.sessions.registry.find(id(1)).orElseThrow().encounter()
                .orElseThrow()).orElseThrow();
        fixture.signals.offer(id(99));
        fixture.signals.offer(id(1));
        fixture.coordinator.tick();
        assertTrue(encounter.contextPending());
        assertEquals(0, fixture.signals.pendingCount());
    }

    @Test
    void probesFormEncountersThroughTheFollowRangeOfTheJoiningMob() {
        fixture.open(1);
        fixture.open(2);
        fixture.open(3);
        fixture.target(1, 1);
        fixture.target(2, 1);
        fixture.target(3, 1);
        fixture.reader.followRange = (mob, member) -> !mob.entityId().equals(id(3)) && !member.entityId().equals(id(3));
        fixture.run(2);
        var first = fixture.sessions.registry.find(id(1)).orElseThrow().encounter();
        assertTrue(first.isPresent());
        assertEquals(first, fixture.sessions.registry.find(id(2)).orElseThrow().encounter());
        assertTrue(fixture.sessions.registry.find(id(3)).orElseThrow().encounter().isPresent());
        assertFalse(first.equals(fixture.sessions.registry.find(id(3)).orElseThrow().encounter()));
    }

    @Test
    void aProfileReloadNeitherRecreatesNorResetsTheBudget() {
        var tight = new CoordinatorFixture(new EngineSettings(1, 1, 8, 4, 600_000, 10, 2, 64, 16, 64, 2000,
                800_000_000L));
        tight.ready();
        tight.open(1);
        tight.target(1, 1);
        tight.run(3);
        assertEquals(1, tight.client.requests.size());
        tight.client.futures.getFirst().complete(CoordinatorFixture.answered(400));
        tight.sessions.lifecycle.reloaded(mobId -> Optional.of(CoordinatorFixture.profile(20, 20, 1)));
        tight.run(45);
        assertEquals(1, tight.client.requests.size());
        assertEquals(1, tight.budget.requestsLastMinute(tight.clock.nanos));
        assertTrue(tight.metrics.count(DecisionDispatcher.Outcome.NO_BUDGET) >= 1);
    }

    @Test
    void everyMemberComputesAPathWithinHalfItsCountOfHeartbeatSpacedEvaluations() {
        for (var members : List.of(4, 5, 8)) {
            var engine = new CoordinatorFixture();
            engine.ready();
            for (var mob = 1; mob <= members; mob++) {
                var session = engine.open(mob);
                session.recordInstall(new InstallResult.Installed(3), 1);
                engine.target(mob, 1);
                engine.sessions.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(1),
                        EngineFixtures.near());
            }
            var evaluations = (members + 1) / 2;
            var evaluationTicks = new ArrayList<Long>();
            for (var tick = 0; evaluationTicks.size() < evaluations && tick < 25 * evaluations; tick++) {
                var before = engine.reader.pathPermissions.size();
                engine.run(1);
                if (engine.reader.pathPermissions.size() > before) {
                    evaluationTicks.add(engine.clock.tick - 1);
                }
                engine.client.futures.forEach(future -> future.complete(CoordinatorFixture.answered(400)));
            }
            var covered = new HashSet<MobKey>();
            engine.reader.pathPermissions.forEach(covered::addAll);
            assertEquals(members, covered.size(), members + " members, permissions " + engine.reader.pathPermissions);
            for (var index = 1; index < evaluationTicks.size(); index++) {
                assertEquals(20, evaluationTicks.get(index) - evaluationTicks.get(index - 1));
            }
        }
    }

    @Test
    void aFailingClosingCheckStillSchedulesTheEncounterAndTheOthersAreEvaluated() {
        fixture.ready();
        for (var mob = 1; mob <= 2; mob++) {
            fixture.open(mob).recordInstall(new InstallResult.Installed(3), 1);
            fixture.target(mob, mob);
        }
        fixture.reader.followRange = (mob, member) -> false;
        fixture.run(3);
        assertEquals(2, fixture.reader.captures);
        fixture.client.futures.forEach(future -> future.complete(CoordinatorFixture.answered(400)));
        fixture.reader.targets.remove(id(1));
        fixture.reader.failingInCombat = true;
        fixture.run(21);
        assertEquals(4, fixture.reader.captures);
        assertEquals(4, fixture.steps.evaluated.size());
        assertTrue(fixture.metrics.total(DecisionMetrics.Counter.STEP_FAILURES) >= 1);
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.getFirst().endsWith("IllegalStateException"), fixture.warnings.getFirst());
        fixture.run(5);
        assertEquals(4, fixture.reader.captures);
    }

    @Test
    void aFailingAcceptanceOfOneExpiredRequestNeitherLeaksTheBudgetNorSkipsTheOthers() {
        fixture.ready();
        for (var mob = 1; mob <= 2; mob++) {
            fixture.open(mob).recordInstall(new InstallResult.Installed(3), 1);
            fixture.target(mob, mob);
        }
        fixture.reader.followRange = (mob, member) -> false;
        fixture.run(3);
        assertEquals(2, fixture.budget.inFlight());
        fixture.steps.failingAccept = true;
        fixture.clock.nanos += fixture.settings.watchdogNanos();
        fixture.coordinator.tick();
        assertEquals(0, fixture.budget.inFlight());
        assertEquals(0, fixture.inFlight.size());
        assertEquals(2, fixture.steps.accepted.size());
        assertEquals(2, fixture.metrics.total(DecisionMetrics.Counter.WATCHDOG_RELEASES));
        assertTrue(fixture.metrics.total(DecisionMetrics.Counter.STEP_FAILURES) >= 1);
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void aFailingAcceptanceOfOneAnswerDoesNotSkipTheNextAnswersOfTheTick() {
        fixture.ready();
        for (var mob = 1; mob <= 2; mob++) {
            fixture.open(mob).recordInstall(new InstallResult.Installed(3), 1);
            fixture.target(mob, mob);
        }
        fixture.reader.followRange = (mob, member) -> false;
        fixture.run(3);
        fixture.steps.failingAccept = true;
        fixture.client.futures.forEach(future -> future.complete(CoordinatorFixture.answered(300)));
        fixture.coordinator.tick();
        assertEquals(2, fixture.steps.accepted.size());
        assertEquals(0, fixture.budget.inFlight());
        assertEquals(0, fixture.inbox.size());
    }

    @Test
    void aFailingEvaluationHookNeverKeepsAFinishedEncounterOpen() {
        sendFirstRequest();
        var encounter = fixture.sessions.registry.find(id(1)).orElseThrow().encounter().orElseThrow();
        fixture.client.futures.getFirst().complete(CoordinatorFixture.answered(400));
        fixture.steps.failingEvaluated = true;
        fixture.reader.targets.clear();
        fixture.run(21);
        assertTrue(fixture.sessions.encounters.find(encounter).isEmpty());
        assertTrue(fixture.metrics.total(DecisionMetrics.Counter.STEP_FAILURES) >= 1);
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void theEvaluationHookOfM10RunsOncePerEvaluation() {
        sendFirstRequest();
        assertEquals(1, fixture.steps.evaluated.size());
        fixture.client.futures.getFirst().complete(CoordinatorFixture.answered(400));
        fixture.run(21);
        assertEquals(fixture.reader.captures, fixture.steps.evaluated.size());
        assertEquals(2, fixture.steps.evaluated.size());
    }

    @Test
    void theShutdownLeavesNoPendingFutureNoResultAndNoView() {
        sendFirstRequest();
        var future = fixture.client.futures.getFirst();
        fixture.intake.cancelAll();
        fixture.sessions.lifecycle.shutdown();
        assertTrue(future.isDone());
        assertEquals(0, fixture.inFlight.size());
        assertEquals(0, fixture.inbox.size());
        assertEquals(0, fixture.sessions.board.size());
    }
}
