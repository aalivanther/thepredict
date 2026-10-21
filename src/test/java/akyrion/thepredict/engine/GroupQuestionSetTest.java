package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.GroupBrief;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupQuestions;
import akyrion.thepredict.domain.GroupRelations;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.LeaderDeath;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.PlanQuestion;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.Trend;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DOC-02: {@code Group.Questions} drops the member questions it does not name, and the group step publishes the
 * question set of the leader with the leader, also when no plan question goes with it.
 */
class GroupQuestionSetTest {
    private static final MobKey LEADER = new MobKey(EngineFixtures.id(1), 0);
    private static final MobKey SECOND = new MobKey(EngineFixtures.id(2), 0);
    private static final MobKey THIRD = new MobKey(EngineFixtures.id(3), 0);
    private static final List<String> PLANS = List.of("press_main_target", "surround_target");

    @Test
    void theQuestionSetDecidesWhichMembersAreAsked() {
        assertEquals(List.of(LEADER, SECOND, THIRD), asked(GroupBrief.NONE));
        assertEquals(List.of(LEADER), asked(brief(GroupQuestions.PLAN_AND_LEADER, PLANS)),
                "DOC-02: the plan and the leader's tactic");
        assertEquals(List.of(LEADER), asked(brief(GroupQuestions.LEADER_ONLY, List.of())), "only the leader's tactic");
        assertEquals(List.of(), asked(brief(GroupQuestions.PLAN_ONLY, PLANS)), "only the group plan");
    }

    @Test
    void theGroupStepPublishesTheQuestionSetOfTheLeaderWithItsEntity() {
        for (var questions : List.of(GroupQuestions.LEADER_ONLY, GroupQuestions.PLAN_ONLY,
                GroupQuestions.PLAN_AND_LEADER)) {
            var sessions = new EngineFixtures.Sessions();
            var director = new GroupDirector(sessions.encounters, sessions.clock, new DecisionMetrics(() -> 0L),
                    full(), sessions.agenda);
            var leader = member(sessions, 1, 80, questions);
            var minion = member(sessions, 2, 10, questions);
            var encounter = sessions.encounters.open().iterator().next();
            director.step(0, 1_000_000_000L);
            var brief = director.brief(encounter.id());
            assertEquals(questions, brief.questions(), questions.name());
            assertEquals(questions.asksPlan(), brief.asksPlan(), questions.name());
            assertEquals(questions != GroupQuestions.PLAN_ONLY, brief.asksMember(leader.key().entityId()));
            assertFalse(brief.asksMember(minion.key().entityId()), questions.name());
        }
        var sessions = new EngineFixtures.Sessions();
        var director = new GroupDirector(sessions.encounters, sessions.clock, new DecisionMetrics(() -> 0L), full(),
                sessions.agenda);
        member(sessions, 1, 80, GroupQuestions.MEMBERS);
        member(sessions, 2, 10, GroupQuestions.MEMBERS);
        director.step(0, 1_000_000_000L);
        assertSame(GroupBrief.NONE, director.brief(sessions.encounters.open().iterator().next().id()),
                "members keeps the body of today");
    }

    private static List<MobKey> asked(GroupBrief brief) {
        var profile = twoTactics();
        var mobs = List.of(mob(LEADER, profile), mob(SECOND, profile), mob(THIRD, profile));
        var facts = new CombatFacts(new EncounterId(1), 10, 0, mobs, List.of(new ParticipantFacts(
                new ParticipantAlias(1), HealthBand.HEALTHY, ArmorBand.LIGHT, Set.of(), 1)), List.of(), 0)
                .withGroup(GroupRelations.NONE, brief);
        var plan = EncounterQuestions.plan(facts, key -> Optional.of(profile), key -> Optional.empty(), 8);
        return plan.questions().stream().map(question -> question.mob()).distinct().toList();
    }

    private static MobFacts mob(MobKey key, TacticalProfile profile) {
        var relation = new TargetRelation(new ParticipantAlias(1), DistanceBand.RANGED, Trend.STEADY, true, true,
                false, false, false);
        return new MobFacts(key, HealthBand.HEALTHY, Optional.of(relation), Set.of(), profile.tactics().keySet(),
                Set.of(), Set.of());
    }

    /** A profile with two distinct tactics, so each mob qualifies for a question of its own. */
    private static TacticalProfile twoTactics() {
        var base = EngineFixtures.profile("Guardian");
        var hold = new TacticId("hold_ground");
        var press = new TacticId("press_on");
        var tactics = new java.util.LinkedHashMap<TacticId, Tactic>();
        tactics.put(press, new Tactic(press, Movement.APPROACH, Optional.empty(), Optional.empty(), 60));
        tactics.put(hold, new Tactic(hold, Movement.HOLD, Optional.empty(), Optional.empty(), 50));
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), tactics, base.choices(),
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing());
    }

    private static GroupBrief brief(GroupQuestions questions, List<String> candidates) {
        return new GroupBrief(questions, candidates, 1L, true, Optional.of(LEADER.entityId()));
    }

    private static MobSession member(EngineFixtures.Sessions sessions, int id, int priority,
                                     GroupQuestions questions) {
        var base = EngineFixtures.profile(priority > 50 ? "Leader" : "Minion");
        var group = new GroupSettings(Optional.of(GroupMode.FULL), "", priority, 1, Set.of(), LeaderDeath.SUCCEED,
                BenchPolicy.FREE, Optional.of(questions), PlanQuestion.ON_CHANGE,
                new RoleCapabilities(500, 400, 0, 0, 0, 400));
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                base.choices(), base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(),
                base.timing(), base.participants(), base.difficulty(), base.stances(), base.telegraph(), base.poise(),
                base.zone(), group);
        var session = sessions.registry.open(EngineFixtures.id(id), profile);
        session.recordTarget(EngineFixtures.player(1), 4);
        sessions.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(1), key -> true);
        return session;
    }

    private static GroupCaps full() {
        var base = GroupCaps.DEFAULT;
        return new GroupCaps(GroupMode.FULL, base.maxSquads(), base.brokenTicks(), base.arbitrationPeriodTicks(),
                base.replanPeriodTicks(), base.maxReplansPerTick(), base.maxMicrosPerTick(),
                base.maxMeleeAttackersPerPlayer(), base.maxRangedAttackersPerPlayer(), base.minImpactGapTicks(),
                base.tokenIdleTimeoutTicks(), base.stealMarginMillis(), base.idleWarnTicks(),
                base.roleSwitchMarginMillis(), base.exitSectorDegrees(), base.lkpForgetTicks(),
                base.maxMediumTellsPerPlayer(), base.bigImpactSpacingTicks(), base.leaderReserveTicks(),
                base.projectileFlightMarginTicks(), base.maxPlanCandidates(), base.minPlanTicks(),
                base.planHorizonTicks(), base.assignmentThreatMargin(), base.factOnTicks(), base.factOffTicks(),
                base.maxFacts());
    }
}
