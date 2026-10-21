package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.LeaderDeath;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.PlanQuestion;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.Trend;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DOC-03: {@code search_last_known} is offered while no member sees its target and the last sighting is younger than
 * {@code group.lkpForgetTicks}, and never otherwise.
 */
class GroupSearchPlanTest {
    private static final long BUDGET = 1_000_000_000L;
    private static final String SEARCH = GroupPlan.SEARCH_LAST_KNOWN.option();

    @Test
    void theSearchPlanNeedsATargetNobodySeesNowAndALastSightingThatIsStillFresh() {
        var sessions = new EngineFixtures.Sessions();
        var caps = GroupCaps.DEFAULT;
        var director = new GroupDirector(sessions.encounters, sessions.clock, new DecisionMetrics(() -> 0L),
                full(caps), sessions.agenda);
        var leader = member(sessions, 1, 80);
        var ally = member(sessions, 2, 10);
        var encounter = sessions.encounters.open().iterator().next();
        sees(leader, false);
        sees(ally, false);
        director.step(0, BUDGET);
        assertFalse(director.brief(encounter.id()).candidates().contains(SEARCH), "never seen: no position to search");
        sees(leader, true);
        director.step(20, BUDGET);
        assertTrue(director.brief(encounter.id()).asksPlan());
        assertFalse(director.brief(encounter.id()).candidates().contains(SEARCH), "a member sees its target");
        sees(leader, false);
        director.step(40, BUDGET);
        assertTrue(director.brief(encounter.id()).candidates().contains(SEARCH),
                "DOC-03: nobody sees the target, seen 20 ticks ago: " + director.brief(encounter.id()).candidates());
        director.step(20 + caps.lkpForgetTicks(), BUDGET);
        assertTrue(director.brief(encounter.id()).candidates().contains(SEARCH), "still within lkpForgetTicks");
        director.step(21 + caps.lkpForgetTicks(), BUDGET);
        assertFalse(director.brief(encounter.id()).candidates().contains(SEARCH),
                "the last known position is forgotten after group.lkpForgetTicks");
    }

    private static MobSession member(EngineFixtures.Sessions sessions, int id, int priority) {
        var session = sessions.registry.open(EngineFixtures.id(id), profile(priority));
        session.recordTarget(EngineFixtures.player(1), 4);
        sessions.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(1), key -> true);
        return session;
    }

    private static void sees(MobSession session, boolean visible) {
        var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.PLAYER, 1), DistanceBand.MELEE,
                Trend.STEADY, true, visible, false, false, false);
        session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(), Set.of(),
                Set.of(), Set.of()));
    }

    private static TacticalProfile profile(int priority) {
        var base = EngineFixtures.profile(priority > 50 ? "Leader" : "Minion");
        var group = new GroupSettings(Optional.of(GroupMode.FULL), "", priority, 1, Set.of(), LeaderDeath.SUCCEED,
                BenchPolicy.FREE, Optional.empty(), PlanQuestion.ON_CHANGE, new RoleCapabilities(500, 400, 0, 0, 0,
                400));
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), base.choices(),
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing(),
                base.participants(), base.difficulty(), base.stances(), base.telegraph(), base.poise(), base.zone(),
                group);
    }

    private static GroupCaps full(GroupCaps base) {
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
