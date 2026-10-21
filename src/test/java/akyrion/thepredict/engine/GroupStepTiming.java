package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.LeaderDeath;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PlanQuestion;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.TacticalProfile;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Wall time of {@link GroupDirector#step} for the group column. No server and no network.
 */
public final class GroupStepTiming {
    private static final int[] ENCOUNTERS = {1, 5, 10};
    private static final int[] MEMBERS = {3, 8, 16};

    private GroupStepTiming() {
    }

    /**
     * One sample of each size, in microseconds, after one warmup step. Rows follow {@code encounters}, columns follow
     * {@code members}.
     *
     * @return microseconds
     */
    public static long[][] micros() {
        var table = new long[ENCOUNTERS.length][MEMBERS.length];
        for (var row = 0; row < ENCOUNTERS.length; row++) {
            for (var column = 0; column < MEMBERS.length; column++) {
                table[row][column] = sample(ENCOUNTERS[row], MEMBERS[column]);
            }
        }
        return table;
    }

    /**
     * Encounter counts of {@link #micros()}.
     *
     * @return the counts
     */
    public static int[] encounters() {
        return ENCOUNTERS.clone();
    }

    /**
     * Member counts of {@link #micros()}.
     *
     * @return the counts
     */
    public static int[] members() {
        return MEMBERS.clone();
    }

    private static long sample(int encounters, int members) {
        var clock = new EngineFixtures.TestClock();
        var agenda = new Agenda();
        var registry = new EncounterRegistry(agenda, clock, new SessionMetrics());
        var metrics = new DecisionMetrics(clock::currentTick);
        var director = new GroupDirector(registry, clock, metrics, coordinate(), agenda);
        var profile = profile();
        for (var encounter = 0; encounter < encounters; encounter++) {
            var target = new UUID(0x251L, encounter + 1L);
            for (var member = 0; member < members; member++) {
                var session = new MobSession(new MobKey(new UUID(encounter + 1L, member + 1L), 0), profile);
                var follow = member > 0;
                registry.assign(session, EngineFixtures.WORLD, target, key -> follow);
                session.recordTarget(target, 4);
                session.playerTarget(true);
            }
        }
        clock.tick = 1;
        director.step(1, 1_000_000_000L);
        var started = System.nanoTime();
        clock.tick = 2;
        director.step(2, 1_000_000_000L);
        return Math.max(0, (System.nanoTime() - started) / 1_000L);
    }

    private static GroupCaps coordinate() {
        var base = GroupCaps.DEFAULT;
        return new GroupCaps(GroupMode.COORDINATE, base.maxSquads(), base.brokenTicks(), base.arbitrationPeriodTicks(),
                base.replanPeriodTicks(), base.maxReplansPerTick(), base.maxMicrosPerTick(),
                base.maxMeleeAttackersPerPlayer(), base.maxRangedAttackersPerPlayer(), base.minImpactGapTicks(),
                base.tokenIdleTimeoutTicks(), base.stealMarginMillis(), base.idleWarnTicks(),
                base.roleSwitchMarginMillis(), base.exitSectorDegrees(), base.lkpForgetTicks(),
                base.maxMediumTellsPerPlayer(), base.bigImpactSpacingTicks(), base.leaderReserveTicks(),
                base.projectileFlightMarginTicks(), base.maxPlanCandidates(), base.minPlanTicks(),
                base.planHorizonTicks(), base.assignmentThreatMargin(), base.factOnTicks(), base.factOffTicks(),
                base.maxFacts());
    }

    private static TacticalProfile profile() {
        var base = EngineFixtures.profile("Guardian");
        var group = new GroupSettings(Optional.of(GroupMode.COORDINATE), "", 80, 1, Set.of(), LeaderDeath.SUCCEED,
                BenchPolicy.FAIR, Optional.empty(), PlanQuestion.ON_CHANGE,
                new RoleCapabilities(500, 400, 0, 0, 0, 400));
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), base.choices(),
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing(),
                base.participants(), base.difficulty(), base.stances(), base.telegraph(), base.poise(), base.zone(),
                group);
    }
}
