package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.GroupBrief;
import akyrion.thepredict.domain.GroupQuestions;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static akyrion.thepredict.engine.CoordinatorFixture.HOLD;
import static akyrion.thepredict.engine.CoordinatorFixture.KEEP;
import static akyrion.thepredict.engine.CoordinatorFixture.PRESS;
import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.near;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionDispatcherTest {
    private final CoordinatorFixture fixture = new CoordinatorFixture();

    private Encounter member(int mob, int target, int contingencies) {
        var session = fixture.sessions.registry.open(id(mob), CoordinatorFixture.profile(20, 20, contingencies));
        session.recordInstall(new InstallResult.Installed(3), 1);
        fixture.target(mob, target);
        return fixture.sessions.encounters.assign(session, WORLD, player(target), near());
    }

    private static List<Situation> situations(List<QuestionSpec> questions) {
        return questions.stream().map(QuestionSpec::situation).toList();
    }

    @Test
    void eachMemberGetsANowQuestionAndItsContingenciesWithDeterministicOptionsAndKeys() {
        fixture.ready();
        var encounter = member(1, 1, 2);
        member(2, 1, 1);
        fixture.reader.distances.put(id(2), DistanceBand.MELEE);
        assertEquals(DecisionDispatcher.Outcome.SENT, fixture.dispatcher.dispatch(encounter));
        var request = fixture.client.requests.getFirst();
        assertEquals(List.of(Situation.NOW, Situation.IF_TARGET_IN_MELEE, Situation.IF_OWN_HEALTH_CRITICAL, Situation.NOW,
                Situation.IF_OWN_HEALTH_CRITICAL), situations(request.questions()));
        assertEquals(List.of(QuestionKey.of(0), QuestionKey.of(1), QuestionKey.of(2), QuestionKey.of(3), QuestionKey.of(4)),
                request.questions().stream().map(QuestionSpec::key).toList());
        assertTrue(request.questions().stream().allMatch(question -> question.options().equals(List.of(KEEP, PRESS, HOLD))));
        assertEquals(Set.of(id(1), id(2)), request.profiles().keySet().stream().map(MobKey::entityId)
                .collect(Collectors.toSet()));
        assertEquals(1, request.sequence());
        assertEquals(encounter.id(), request.encounter());
    }

    @Test
    void aMemberWithOneTacticWithoutPerceivedTargetOrWithoutGoalsIsNotAsked() {
        fixture.ready();
        var encounter = member(1, 1, 0);
        member(2, 1, 0);
        member(3, 1, 0);
        member(4, 1, 0);
        fixture.reader.eligible.put(id(2), Set.of(HOLD));
        fixture.reader.targets.remove(id(3));
        fixture.sessions.registry.find(id(4)).orElseThrow().recordInstall(new InstallResult.Unsupported("brain"), 1);
        fixture.dispatcher.dispatch(encounter);
        var asked = fixture.client.requests.getFirst().questions();
        assertEquals(1, asked.size());
        assertEquals(id(1), asked.getFirst().mob().entityId());
    }

    @Test
    void anEncounterWithoutAQualifyingMemberIsCapturedButNotSent() {
        fixture.ready();
        var encounter = member(1, 1, 1);
        fixture.reader.eligible.put(id(1), Set.of(HOLD));
        assertEquals(DecisionDispatcher.Outcome.NO_QUESTIONS, fixture.dispatcher.dispatch(encounter));
        assertEquals(1, fixture.reader.captures);
        assertTrue(fixture.client.requests.isEmpty());
        assertEquals(1, fixture.metrics.count(DecisionDispatcher.Outcome.NO_QUESTIONS));
    }

    @Test
    void theGroupQuestionSetDropsMemberQuestionsAndPlanOnlySendsTheGroupPlanAlone() {
        fixture.ready();
        var encounter = member(1, 1, 0);
        member(2, 1, 0);
        var plans = List.of("press_main_target", "surround_target");
        fixture.capture.hearGroup(id -> new GroupBrief(GroupQuestions.PLAN_AND_LEADER, plans, 1L, true,
                java.util.Optional.of(id(1))));
        assertEquals(DecisionDispatcher.Outcome.SENT, fixture.dispatcher.dispatch(encounter));
        assertEquals(List.of(id(1)), fixture.client.requests.getFirst().questions().stream()
                .map(question -> question.mob().entityId()).toList(), "DOC-02: only the leader is asked");
        var planOnly = new CoordinatorFixture();
        planOnly.ready();
        var session = planOnly.sessions.registry.open(id(1), CoordinatorFixture.profile(20, 20, 0));
        session.recordInstall(new InstallResult.Installed(3), 1);
        planOnly.target(1, 1);
        var only = planOnly.sessions.encounters.assign(session, WORLD, player(1), near());
        planOnly.capture.hearGroup(id -> new GroupBrief(GroupQuestions.PLAN_ONLY, plans, 1L, true,
                java.util.Optional.of(id(1))));
        assertEquals(DecisionDispatcher.Outcome.SENT, planOnly.dispatcher.dispatch(only));
        var sent = planOnly.client.requests.getFirst();
        assertTrue(sent.questions().isEmpty(), "plan_only asks no member");
        assertTrue(sent.facts().brief().asksPlan(), "the group plan travels alone");
    }

    @Test
    void leaderOnlyAsksTheLeaderAloneAndSendsNoPlanQuestion() {
        fixture.ready();
        var encounter = member(1, 1, 0);
        member(2, 1, 0);
        fixture.capture.hearGroup(id -> new GroupBrief(GroupQuestions.LEADER_ONLY, List.of(), 1L, true,
                java.util.Optional.of(id(1))));
        assertEquals(DecisionDispatcher.Outcome.SENT, fixture.dispatcher.dispatch(encounter));
        var sent = fixture.client.requests.getFirst();
        assertEquals(List.of(id(1)), sent.questions().stream().map(question -> question.mob().entityId()).distinct()
                .toList(), "DOC-02: the capture carries a brief that asks no plan, and it still drops the minion");
        assertFalse(sent.facts().brief().asksPlan());
        assertFalse(encounter.hasGroupCapture(), "no plan question, no plan candidates to adopt");
    }

    @Test
    void aRequestHoldsTheQuestionsOfItsOwnEncounterOnly() {
        fixture.ready();
        var first = member(1, 1, 0);
        var second = member(2, 2, 0);
        assertFalse(first.id().equals(second.id()));
        fixture.dispatcher.dispatch(first);
        fixture.dispatcher.dispatch(second);
        assertEquals(List.of(id(1)), fixture.client.requests.get(0).questions().stream()
                .map(question -> question.mob().entityId()).toList());
        assertEquals(List.of(id(2)), fixture.client.requests.get(1).questions().stream()
                .map(question -> question.mob().entityId()).toList());
    }

    @Test
    void theServiceStateAndTheBudgetGateTheSendAfterTheCapture() {
        var encounter = member(1, 1, 0);
        assertEquals(DecisionDispatcher.Outcome.SERVICE_UNAVAILABLE, fixture.dispatcher.dispatch(encounter));
        assertEquals(1, fixture.reader.captures);
        var tight = new CoordinatorFixture(new EngineSettings(1, 1, 8, 4, 600_000, 10, 2, 64, 16, 64, 2000, 800_000_000L));
        tight.ready();
        var session = tight.sessions.registry.open(id(1), CoordinatorFixture.profile(20, 20, 0));
        session.recordInstall(new InstallResult.Installed(3), 1);
        tight.target(1, 1);
        var blocked = tight.sessions.encounters.assign(session, WORLD, player(1), near());
        assertTrue(tight.budget.tryReserve(WORLD, new EncounterId(99), 10, 0));
        assertEquals(DecisionDispatcher.Outcome.NO_BUDGET, tight.dispatcher.dispatch(blocked));
        assertTrue(tight.client.requests.isEmpty());
        assertFalse(blocked.schedule().requestInFlight());
    }

    @Test
    void aClientThatThrowsReleasesTheReservationAndCountsAnInvalidRequest() {
        fixture.ready();
        var encounter = member(1, 1, 0);
        fixture.client.failDecide = true;
        assertEquals(DecisionDispatcher.Outcome.CLIENT_ERROR, fixture.dispatcher.dispatch(encounter));
        assertEquals(0, fixture.budget.inFlight());
        assertFalse(encounter.schedule().requestInFlight());
        assertEquals(1, fixture.metrics.count(FailureKind.INVALID_REQUEST));
        assertEquals(ServiceState.PAUSED, fixture.tracker.status().state());
    }

    @Test
    void theCallbackOnlyDepositsTheResultInTheInboxAndNeverTwoRequestsFlyForOneEncounter() {
        fixture.ready();
        var encounter = member(1, 1, 0);
        fixture.dispatcher.dispatch(encounter);
        assertTrue(encounter.schedule().requestInFlight());
        assertThrows(IllegalStateException.class, () -> fixture.dispatcher.dispatch(encounter));
        assertFalse(fixture.budget.tryReserve(WORLD, encounter.id(), 10, 0));
        fixture.client.futures.getFirst().complete(CoordinatorFixture.answered(300));
        assertEquals(1, fixture.inbox.size());
        assertEquals(1, fixture.budget.inFlight());
        assertTrue(encounter.schedule().requestInFlight());
        assertTrue(fixture.steps.accepted.isEmpty());
        var entries = new ArrayList<InboxEntry>();
        fixture.inbox.drain(1, entries::add);
        var request = ((InboxEntry.CompletedRequest) entries.getFirst()).request();
        assertEquals(encounter.worldId(), request.worldId());
        assertEquals(request.sentAtNanos() + fixture.settings.watchdogNanos(), request.deadlineNanos());
        assertEquals(fixture.budget.tokensFor(fixture.client.sent.getFirst().bodyChars()), request.estimatedTokens());
        assertEquals(fixture.client.sent.getFirst().bodyChars(), request.bodyChars());
    }

    @Test
    void noMemberComputesAPathWhileJevCannotBeAskedAndAtMostTwoDoOtherwise() {
        var encounter = member(1, 1, 0);
        member(2, 1, 0);
        member(3, 1, 0);
        assertEquals(DecisionDispatcher.Outcome.SERVICE_UNAVAILABLE, fixture.dispatcher.dispatch(encounter));
        assertTrue(fixture.reader.pathPermissions.getFirst().isEmpty());
        fixture.ready();
        fixture.dispatcher.dispatch(encounter);
        assertEquals(DecisionDispatcher.PATH_CHECKS_PER_EVALUATION, fixture.reader.pathPermissions.get(1).size());
    }

    @Test
    void aClientThatReturnsNoFutureIsTreatedLikeOneThatThrows() {
        fixture.ready();
        var encounter = member(1, 1, 0);
        fixture.client.returnNull = true;
        assertEquals(DecisionDispatcher.Outcome.CLIENT_ERROR, fixture.dispatcher.dispatch(encounter));
        assertEquals(0, fixture.budget.inFlight());
        assertFalse(encounter.schedule().requestInFlight());
        assertEquals(1, fixture.metrics.count(FailureKind.INVALID_REQUEST));
    }

    @Test
    void theOptionOrderIsPriorityThenIdentifier() {
        fixture.ready();
        var encounter = member(1, 1, 0);
        fixture.reader.eligible.put(id(1), Set.of(HOLD, new TacticId("press_attack"), KEEP));
        fixture.dispatcher.dispatch(encounter);
        assertEquals(List.of(KEEP, PRESS, HOLD), fixture.client.requests.getFirst().questions().getFirst().options());
    }
}
