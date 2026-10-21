package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupRole;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.LeaderDeath;
import akyrion.thepredict.domain.PlanQuestion;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.TacticalProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** DOC-09: the group step reads {@code Group.Roles} of each profile and {@code group.maxSquads}. */
class GroupProfileKeysTest {
    private static final long BUDGET = 1_000_000_000L;

    @Test
    void aMemberRestrictedToRangedIsKeptOffPressureAndFlank() {
        var sessions = new EngineFixtures.Sessions();
        var director = new GroupDirector(sessions.encounters, sessions.clock, new DecisionMetrics(() -> 0L),
                caps(GroupMode.COORDINATE, GroupCaps.DEFAULT.maxSquads()), sessions.agenda);
        var strong = member(sessions, 1, 80, "", Set.of(GroupRole.RANGED), new RoleCapabilities(900, 800, 300, 0, 0,
                400));
        var second = member(sessions, 2, 10, "", Set.of(), new RoleCapabilities(500, 400, 300, 200, 100, 400));
        var third = member(sessions, 3, 5, "", Set.of(), new RoleCapabilities(500, 400, 300, 200, 100, 400));
        var encounter = sessions.encounters.open().iterator().next();
        encounter.offerGroupCapture(1, true, List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET));
        director.step(20, BUDGET);
        assertEquals(Optional.of(GroupRole.RESERVE), strong.groupCopy().role(),
                "DOC-09: Group.Roles keeps the member to its roles or reserve");
        assertEquals(Set.of(GroupRole.PRESSURE, GroupRole.FLANK), Set.of(second.groupCopy().role().orElseThrow(),
                third.groupCopy().role().orElseThrow()));
    }

    @Test
    void theSquadsPastGroupMaxSquadsJoinTheRemainder() {
        var sessions = new EngineFixtures.Sessions();
        var director = new GroupDirector(sessions.encounters, sessions.clock, new DecisionMetrics(() -> 0L),
                caps(GroupMode.FAIR, 1), sessions.agenda);
        var scout = member(sessions, 1, 80, "scouts", Set.of(), RoleCapabilities.NONE);
        var first = member(sessions, 2, 10, "guards", Set.of(), RoleCapabilities.NONE);
        member(sessions, 3, 10, "guards", Set.of(), RoleCapabilities.NONE);
        member(sessions, 4, 5, "", Set.of(), RoleCapabilities.NONE);
        director.step(20, BUDGET);
        assertEquals(1, scout.groupCopy().squadSize());
        assertEquals(3, first.groupCopy().squadSize(), "DOC-09: the second squad is past the cap of one");
    }

    private static MobSession member(EngineFixtures.Sessions sessions, int id, int priority, String squad,
                                     Set<GroupRole> roles, RoleCapabilities capabilities) {
        var base = EngineFixtures.profile(priority > 50 ? "Leader" : "Minion");
        var group = new GroupSettings(Optional.empty(), squad, priority, 1, roles, LeaderDeath.SUCCEED,
                BenchPolicy.FREE, Optional.empty(), PlanQuestion.ON_CHANGE, capabilities);
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                base.choices(), base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(),
                base.timing(), base.participants(), base.difficulty(), base.stances(), base.telegraph(), base.poise(),
                base.zone(), group);
        var session = sessions.registry.open(EngineFixtures.id(id), profile);
        session.recordTarget(EngineFixtures.player(1), 4);
        sessions.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(1), key -> true);
        return session;
    }

    private static GroupCaps caps(GroupMode mode, int maxSquads) {
        var base = GroupCaps.DEFAULT;
        return new GroupCaps(mode, maxSquads, base.brokenTicks(), base.arbitrationPeriodTicks(),
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
