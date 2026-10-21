package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceTelegraph;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupRole;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.LeaderDeath;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PlanQuestion;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TellAnswer;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Local section 11 of M25: adoption, slots, the leader reserve and a plan that dies in the same step. */
class GroupSection11Test {
    @Test
    void theGroupStepPublishesARealSlotAndTheLeaderReserve() {
        var world = new World(GroupMode.COORDINATE);
        var leader = world.member(1, 80, LeaderDeath.SUCCEED, true);
        var left = world.member(2, 0, LeaderDeath.SUCCEED, false);
        var right = world.member(3, 0, LeaderDeath.SUCCEED, false);
        var encounter = world.encounter();
        encounter.offerGroupCapture(1, true, List.of(GroupPlan.SCREEN_THE_LEADER, GroupPlan.PRESS_MAIN_TARGET));
        world.step(0);
        assertEquals(GroupPlan.SCREEN_THE_LEADER, world.director.plan(encounter.id()));
        assertEquals(2, world.director.planEpoch(encounter.id()));
        assertTrue(leader.groupBooks().tells().anyLeader(0));
        assertRealSlots(leader, left, right);
        encounter.offerGroupAnswer(GroupPlan.SCREEN_THE_LEADER, 1, 1);
        world.step(5);
        assertEquals(GroupPlan.SCREEN_THE_LEADER, world.director.plan(encounter.id()));
        assertEquals(2, world.director.planEpoch(encounter.id()));
        assertTrue(leader.groupBooks().tells().leaderHolding(0, 5)
                || leader.groupBooks().tells().anyLeader(5));
    }

    @Test
    void theSameCaptureReplacesTheProvisionalBeforeMinPlanTicks() {
        var world = new World(GroupMode.COORDINATE);
        world.member(1, 80, LeaderDeath.SUCCEED, true);
        world.member(2, 0, LeaderDeath.SUCCEED, false);
        var encounter = world.encounter();
        encounter.offerGroupCapture(1, true, List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET));
        world.step(0);
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, world.director.plan(encounter.id()));
        encounter.offerGroupAnswer(GroupPlan.SURROUND_TARGET, 1, 1);
        world.step(5);
        assertEquals(GroupPlan.SURROUND_TARGET, world.director.plan(encounter.id()));
        assertEquals(3, world.director.planEpoch(encounter.id()));
    }

    @Test
    void aCaptureWithoutG0NeverReplacesThePlan() {
        var world = new World(GroupMode.COORDINATE);
        world.member(1, 80, LeaderDeath.SUCCEED, true);
        world.member(2, 0, LeaderDeath.SUCCEED, false);
        var encounter = world.encounter();
        encounter.offerGroupCapture(1, true, List.of(GroupPlan.PRESS_MAIN_TARGET));
        world.step(0);
        encounter.offerGroupAnswer(GroupPlan.PRESS_MAIN_TARGET, 1, 1);
        world.step(5);
        var epoch = world.director.planEpoch(encounter.id());
        encounter.offerGroupCapture(2, false, List.of(GroupPlan.SCREEN_THE_LEADER, GroupPlan.SURROUND_TARGET));
        world.step(20);
        world.step(50);
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, world.director.plan(encounter.id()));
        assertEquals(epoch, world.director.planEpoch(encounter.id()));
    }

    @Test
    void aLaterAnswerWaitsUntilMinPlanTicks() {
        var world = new World(GroupMode.COORDINATE);
        world.member(1, 80, LeaderDeath.SUCCEED, true);
        world.member(2, 0, LeaderDeath.SUCCEED, false);
        var encounter = world.encounter();
        encounter.offerGroupCapture(1, true, List.of(GroupPlan.PRESS_MAIN_TARGET));
        world.step(0);
        encounter.offerGroupAnswer(GroupPlan.PRESS_MAIN_TARGET, 1, 1);
        world.step(5);
        var epoch = world.director.planEpoch(encounter.id());
        encounter.offerGroupCapture(2, true, List.of(GroupPlan.SCREEN_THE_LEADER));
        world.step(20);
        encounter.offerGroupAnswer(GroupPlan.SURROUND_TARGET, 2, 1);
        world.step(25);
        world.step(44);
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, world.director.plan(encounter.id()));
        assertEquals(epoch, world.director.planEpoch(encounter.id()));
        world.step(45);
        assertEquals(GroupPlan.SURROUND_TARGET, world.director.plan(encounter.id()));
        assertEquals(epoch + 1, world.director.planEpoch(encounter.id()));
    }

    @Test
    void aDeadPlanIsReplacedInTheSameStep() {
        var world = new World(GroupMode.COORDINATE);
        var leader = world.member(1, 80, LeaderDeath.SUCCEED, true);
        var left = world.member(2, 0, LeaderDeath.SUCCEED, false);
        var right = world.member(3, 0, LeaderDeath.SUCCEED, false);
        var encounter = world.encounter();
        encounter.offerGroupCapture(1, true, List.of(GroupPlan.SCREEN_THE_LEADER, GroupPlan.PRESS_MAIN_TARGET));
        world.step(0);
        encounter.offerGroupAnswer(GroupPlan.SCREEN_THE_LEADER, 1, 1);
        world.step(5);
        encounter.offerGroupCapture(2, false, List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET,
                GroupPlan.SCREEN_THE_LEADER));
        world.step(20);
        assertEquals(GroupPlan.SCREEN_THE_LEADER, world.director.plan(encounter.id()));
        var epoch = world.director.planEpoch(encounter.id());
        world.registry.leave(leader);
        world.step(31);
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, world.director.plan(encounter.id()));
        assertEquals(epoch + 1, world.director.planEpoch(encounter.id()));
        assertEquals(2, left.groupCopy().membershipEpoch());
        assertEquals(GroupMode.COORDINATE, left.groupCopy().mode());
        assertTrue(encounter.contextPending());
        assertEquals(Agenda.PendingCause.PLAN_LOST, encounter.pendingCause());
        assertRealSlots(left, right);
        assertNotEquals(GroupRole.GUARD, left.groupCopy().role().orElse(null));
        assertNotEquals(GroupRole.GUARD, right.groupCopy().role().orElse(null));
    }

    @Test
    void aDeadPlanWithNoCandidateFallsToFair() {
        var world = new World(GroupMode.COORDINATE);
        var leader = world.member(1, 80, LeaderDeath.SUCCEED, true);
        var left = world.member(2, 0, LeaderDeath.SUCCEED, false);
        var right = world.member(3, 0, LeaderDeath.SUCCEED, false);
        var encounter = world.encounter();
        encounter.offerGroupCapture(1, true, List.of(GroupPlan.SCREEN_THE_LEADER));
        world.step(0);
        encounter.offerGroupAnswer(GroupPlan.SCREEN_THE_LEADER, 1, 1);
        world.step(5);
        encounter.offerGroupCapture(2, false, List.of(GroupPlan.SCREEN_THE_LEADER));
        world.step(20);
        world.registry.leave(leader);
        world.step(31);
        assertNull(world.director.plan(encounter.id()));
        assertEquals(Agenda.PendingCause.PLAN_LOST, encounter.pendingCause());
        assertEquals(-1, left.groupCopy().slotIndex());
        assertEquals(-1, right.groupCopy().slotIndex());
        assertTrue(left.groupCopy().role().isEmpty());
        assertTrue(right.groupCopy().role().isEmpty());
        assertEquals(GroupMode.FAIR, left.groupCopy().mode());
        assertTrue(left.groupCopy().plan().isEmpty());
    }

    @Test
    void aBreakClearsThePlanInsteadOfNamingASuccessor() {
        var world = new World(GroupMode.COORDINATE);
        var leader = world.member(1, 80, LeaderDeath.BREAK, true);
        world.member(2, 10, LeaderDeath.SUCCEED, false);
        world.member(3, 0, LeaderDeath.SUCCEED, false);
        var encounter = world.encounter();
        encounter.offerGroupCapture(1, true, List.of(GroupPlan.SCREEN_THE_LEADER, GroupPlan.PRESS_MAIN_TARGET));
        world.step(0);
        encounter.offerGroupAnswer(GroupPlan.SCREEN_THE_LEADER, 1, 1);
        world.step(5);
        world.registry.leave(leader);
        world.step(31);
        assertNull(world.director.plan(encounter.id()));
        assertEquals(Agenda.PendingCause.PLAN_LOST, encounter.pendingCause());
        assertEquals(GroupMode.FAIR, world.encounter().memberSessions().iterator().next().groupCopy().mode());
    }

    @Test
    void fairStillPublishesTheLeaderReserveAndKeepsTheSlotEmpty() {
        var world = new World(GroupMode.FAIR);
        var leader = world.member(1, 80, LeaderDeath.SUCCEED, true);
        var other = world.member(2, 0, LeaderDeath.SUCCEED, false);
        world.step(0);
        assertTrue(leader.groupBooks().tells().anyLeader(0));
        assertEquals(-1, leader.groupCopy().slotIndex());
        assertEquals(-1, other.groupCopy().slotIndex());
        assertEquals(GroupMode.FAIR, leader.groupCopy().mode());
    }

    @Test
    void anUnopenedReserveIsNotRepublishedForFortyTicks() {
        var world = new World(GroupMode.COORDINATE);
        var leader = world.member(1, 80, LeaderDeath.SUCCEED, true);
        world.member(2, 0, LeaderDeath.SUCCEED, false);
        world.step(0);
        assertTrue(leader.groupBooks().tells().anyLeader(0));
        world.step(19);
        assertTrue(leader.groupBooks().tells().anyLeader(19));
        world.step(20);
        assertFalse(leader.groupBooks().tells().anyLeader(20));
        assertEquals(1, world.metrics.total(DecisionMetrics.GroupCounter.LEADER_RESERVE_EXPIRED));
        world.step(59);
        assertFalse(leader.groupBooks().tells().anyLeader(59));
        world.step(60);
        assertTrue(leader.groupBooks().tells().anyLeader(60));
    }

    @Test
    void theGroupStepDropsG0WhenTheMembershipEpochMoved() {
        var world = new World(GroupMode.COORDINATE);
        world.member(1, 80, LeaderDeath.SUCCEED, true);
        world.member(2, 0, LeaderDeath.SUCCEED, false);
        var encounter = world.encounter();
        encounter.offerGroupCapture(1, true, List.of(GroupPlan.PRESS_MAIN_TARGET));
        world.step(0);
        encounter.offerGroupAnswer(GroupPlan.PRESS_MAIN_TARGET, 1, 1);
        world.step(5);
        var epoch = world.director.planEpoch(encounter.id());
        encounter.offerGroupAnswer(GroupPlan.SURROUND_TARGET, 4, 9);
        world.step(6);
        assertEquals(1, world.metrics.count(DiscardReason.GROUP_EPOCH_CHANGED));
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, world.director.plan(encounter.id()));
        assertEquals(epoch, world.director.planEpoch(encounter.id()));
        encounter.offerGroupAnswer(GroupPlan.SURROUND_TARGET, 4, 1);
        world.step(46);
        assertEquals(1, world.metrics.count(DiscardReason.GROUP_EPOCH_CHANGED));
        assertEquals(GroupPlan.SURROUND_TARGET, world.director.plan(encounter.id()));
    }

    private static void assertRealSlots(MobSession... sessions) {
        var seen = new HashSet<Integer>();
        for (var session : sessions) {
            var slot = session.groupCopy().slotIndex();
            assertNotEquals(-1, slot);
            assertTrue(slot >= 0);
            assertTrue(seen.add(slot));
        }
    }

    private static TacticalProfile profile(GroupSettings group, SkillChoice choice) {
        var base = EngineFixtures.profile(group.leaderPriority() > 50 ? "Leader" : "Minion");
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        if (choice != null) {
            choices.put(choice.id(), choice);
        }
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), choices,
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing(),
                base.participants(), base.difficulty(), base.stances(), base.telegraph(), base.poise(), base.zone(),
                group);
    }

    private static GroupCaps caps(GroupMode mode) {
        var base = GroupCaps.DEFAULT;
        return new GroupCaps(mode, base.maxSquads(), base.brokenTicks(), base.arbitrationPeriodTicks(),
                base.replanPeriodTicks(), base.maxReplansPerTick(), base.maxMicrosPerTick(),
                base.maxMeleeAttackersPerPlayer(), base.maxRangedAttackersPerPlayer(), base.minImpactGapTicks(),
                base.tokenIdleTimeoutTicks(), base.stealMarginMillis(), base.idleWarnTicks(),
                base.roleSwitchMarginMillis(), base.exitSectorDegrees(), base.lkpForgetTicks(),
                base.maxMediumTellsPerPlayer(), base.bigImpactSpacingTicks(), base.leaderReserveTicks(),
                base.projectileFlightMarginTicks(), base.maxPlanCandidates(), base.minPlanTicks(),
                base.planHorizonTicks(), base.assignmentThreatMargin(), base.factOnTicks(), base.factOffTicks(),
                base.maxFacts());
    }

    private static final class World {
        final EngineFixtures.TestClock clock = new EngineFixtures.TestClock();
        final Agenda agenda = new Agenda();
        final EncounterRegistry registry = new EncounterRegistry(agenda, clock, new SessionMetrics());
        final DecisionMetrics metrics = new DecisionMetrics(clock::currentTick);
        final GroupDirector director;

        World(GroupMode mode) {
            director = new GroupDirector(registry, clock, metrics, caps(mode), agenda);
        }

        MobSession member(int id, int priority, LeaderDeath death, boolean heavy) {
            SkillChoice choice = null;
            if (heavy) {
                choice = SkillChoice.untyped(new ChoiceId("slam"), "Slam", Set.of(), true, 0).withTelegraph(
                        new ChoiceTelegraph(Optional.of(DangerClass.HEAVY), Optional.of(TellAnswer.BLOCK),
                                OptionalInt.empty()));
            }
            var settings = new GroupSettings(Optional.of(GroupMode.COORDINATE), "", priority, 1, Set.of(), death,
                    BenchPolicy.FREE, Optional.empty(), PlanQuestion.ON_CHANGE,
                    new RoleCapabilities(500, 400, 0, 0, 0, 400));
            var session = new MobSession(new MobKey(EngineFixtures.id(id), 0), profile(settings, choice));
            registry.assign(session, EngineFixtures.WORLD, EngineFixtures.player(1), key -> true);
            session.recordTarget(EngineFixtures.player(1), 4);
            if (heavy) {
                session.listedChoices();
                session.noteChoiceReady(0, 4, 0);
            }
            return session;
        }

        Encounter encounter() {
            return registry.open().iterator().next();
        }

        void step(long tick) {
            clock.tick = tick;
            director.step(tick, 1_000_000_000L);
        }
    }
}
