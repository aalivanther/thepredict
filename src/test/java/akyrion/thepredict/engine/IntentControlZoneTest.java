package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.Feasibility;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.PushDanger;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.ZoneCaps;
import akyrion.thepredict.domain.ZoneMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cast order of A12 in the ready step (EFO-10, E-1, E-12): a lethal push against a player is deferred one step at
 * a time on its own counts and skipped at the configured maximum, an infeasible answer is dropped for the whiff
 * cooldown, and a push behind a tell that shows its minimum is allowed.
 */
class IntentControlZoneTest {
    private static final ChoiceId SHOVE = new ChoiceId("shove");
    private static final ChoiceId SLAM = new ChoiceId("slam");
    private static final TacticId PUSH = new TacticId("push_off");
    private static final long LETHAL_NEAR = (1L << TickSignals.VALID) | (2L << TickSignals.TARGET_PUSH_NEAR);

    private final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
    private final ExecutionFixture.Actions actions = new ExecutionFixture.Actions(sessions.clock);
    private final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
    private final IntentControl control = new IntentControl(sessions.board, actions, metrics,
            new ExecutionLog(line -> {
            }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
            DecisionTap.NONE);

    private static SkillChoice pushing(ChoiceId id, DamageTier damage) {
        return new SkillChoice(id, ChoiceKind.MYTHIC_SKILL, Optional.of("TPV_" + id.value()), Set.of(), true, 0,
                new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(3.0),
                        OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, damage, Set.of(), Set.of(), false,
                        PurposeOrigin.DECLARED)).withDisplacement(DisplacementKind.KNOCKBACK);
    }

    /** A session whose intent names {@code intentChoice}; the other choice is held, so it is never a candidate. */
    private MobSession session(ChoiceId intentChoice, Feasibility feasibility) {
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(SHOVE, pushing(SHOVE, DamageTier.MEDIUM));
        choices.put(SLAM, pushing(SLAM, DamageTier.HIGH));
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactics.put(PUSH, new Tactic(PUSH, Movement.APPROACH, Optional.of(intentChoice), Optional.empty(), 50));
        var profile = new TacticalProfile("zone_ai", "Zoner", "Defeat the target", tactics, choices, List.of(),
                new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                ExecutionFixture.timing(1).withCasting(CastMode.ANY_READY, 0));
        var session = sessions.registry.open(EngineFixtures.id(1), profile);
        session.recordTarget(EngineFixtures.player(1), 3.0);
        session.playerTarget(true);
        session.answerProbe(answer -> feasibility);
        session.zoneReading(ZoneMode.GUARD, PushDanger.LETHAL, feasibility);
        session.tickSignals(LETHAL_NEAR);
        session.adoptIntent(new TacticalIntent(session.key(), PUSH, Movement.APPROACH, Optional.of(intentChoice), 0.9,
                List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE, false));
        session.scheduleReady(sessions.clock.tick);
        session.ensureChoiceSlots(choices.size());
        session.holdChoice(intentChoice.equals(SHOVE) ? 1 : 0, Long.MAX_VALUE / 2);
        return session;
    }

    /** Runs the ready step as the tick layer does, on every due tick, and returns the ticks of each deferral. */
    private List<Long> drive(MobSession session, long from, long to, int[] visits) {
        var deferrals = new ArrayList<Long>();
        for (var tick = from; tick <= to; tick++) {
            sessions.clock.tick = tick;
            if (session.nextReadyTick() > tick) {
                continue;
            }
            var before = session.pushDeferredTicks();
            control.castIfReady(session, tick, tick * ExecutionFixture.TICK_NANOS);
            visits[0]++;
            if (session.pushDeferredTicks() > before) {
                deferrals.add(tick);
            }
        }
        return deferrals;
    }

    @Test
    void aLethalPushIsDeferredOneStepAtATimeAndSkippedAtTheMaximum() {
        var session = session(SHOVE, Feasibility.UNKNOWN);
        var step = ZoneCaps.DEFAULT.pushDeferTicks();
        var maximum = ZoneCaps.DEFAULT.pushDeferMaxTicks();
        var visits = new int[1];
        var deferrals = drive(session, 100, 100 + maximum, visits);
        var expected = new ArrayList<Long>();
        for (var deferred = 0; deferred < maximum; deferred += step) {
            expected.add(100L + deferred);
        }
        assertEquals(expected, deferrals, "one deferral per step of " + step + " ticks until the maximum");
        assertEquals(maximum, session.pushDeferredTicks());
        assertTrue(visits[0] <= maximum / step + 1, "the session sleeps through each step: " + visits[0]);
        assertTrue(actions.castTicks(1).isEmpty(), "the lethal push never fired");
    }

    @Test
    void theIntentChoiceCountsItsOwnDeferralsWhateverCandidateWasArmedBefore() {
        var session = session(SHOVE, Feasibility.UNKNOWN);
        session.armChoice(1);
        drive(session, 100, 100, new int[1]);
        session.armChoice(0);
        assertEquals(ZoneCaps.DEFAULT.pushDeferTicks(), session.pushDeferredTicks(), "the shove deferred once");
        session.armChoice(1);
        assertEquals(0, session.pushDeferredTicks(), "the held slam was not tried, so its count stays at zero");
    }

    @Test
    void theConfiguredMaximumOfTheZoneDecidesTheSkip() {
        var session = session(SHOVE, Feasibility.UNKNOWN);
        var defaults = ZoneCaps.DEFAULT;
        session.zoneLimits(new ZoneCaps(defaults.mode(), defaults.defaultRadiusBlocks(), defaults.maxRadiusBlocks(),
                defaults.bandBlocks(), defaults.maxZones(), defaults.maxSnapshotsPerTick(),
                defaults.maxConcurrentBuilds(), defaults.maxDropScanBlocks(), defaults.maxMicrosPerTick(),
                defaults.maxColumnReadsPerTick(), defaults.minColumnRefreshTicks(), defaults.maxDirtyQueue(),
                defaults.dirtyRebuildThreshold(), defaults.verifyIntervalTicks(), defaults.idleDropTicks(),
                defaults.maxQueriesPerTick(), defaults.knockbackDepthBlocks(), defaults.strongPushDepthBlocks(),
                defaults.maxSafeDropBlocks(), defaults.factPeriodTicks(), defaults.factOnTicks(),
                defaults.factOffTicks(), defaults.maxFactsPerMob(), defaults.pushDeferTicks(), 30,
                defaults.pushAttributionTicks(), defaults.answerRetryTicks(), defaults.answerMaxWaitTicks()));
        assertEquals(30, session.pushDeferMaxTicks());
        var deferrals = drive(session, 100, 200, new int[1]);
        assertEquals(List.of(100L, 110L, 120L), deferrals, "zone.pushDeferMaxTicks of 30 skips after three steps");
    }

    @Test
    void anInfeasibleAnswerIsDroppedForTheWhiffCooldownAndStartsAgainAfterIt() {
        var session = session(SHOVE, Feasibility.INFEASIBLE);
        var whiff = session.profile().telegraph().whiffCooldownTicks();
        var dropped = -1L;
        for (var tick = 100L; tick <= 200 && dropped < 0; tick++) {
            sessions.clock.tick = tick;
            if (session.nextReadyTick() <= tick) {
                control.castIfReady(session, tick, tick * ExecutionFixture.TICK_NANOS);
                if (session.whiffUntil(0) > tick) {
                    dropped = tick;
                }
            }
        }
        assertTrue(dropped >= 100 + ZoneCaps.DEFAULT.answerMaxWaitTicks(), "the retries came first: " + dropped);
        assertEquals(dropped + whiff, session.whiffUntil(0), "the drop holds the choice for the whiff cooldown");
        assertEquals(0, session.answerWaitedTicks(), "the wait starts again after the drop");
        assertFalse(control.ready(session, session.profile().choices().get(SHOVE), dropped + whiff - 1));
        assertTrue(control.ready(session, session.profile().choices().get(SHOVE), dropped + whiff));
    }

    @Test
    void aFeasibleReadingForgetsTheRetriesOfAnInfeasibleAnswer() {
        var session = session(SHOVE, Feasibility.INFEASIBLE);
        drive(session, 100, 110, new int[1]);
        assertTrue(session.answerWaitedTicks() > 0);
        session.zoneReading(ZoneMode.GUARD, PushDanger.LETHAL, Feasibility.FEASIBLE);
        assertEquals(0, session.answerWaitedTicks());
        session.armChoice(0);
        assertEquals(0, session.answerWaitedTicks(), "the per-choice wait is reset too");
    }

    @Test
    void aLethalPushBehindATellThatShowsItsMinimumOpensTheTell() {
        var session = session(SLAM, Feasibility.FEASIBLE);
        drive(session, 100, 100, new int[1]);
        assertEquals(0, session.pushDeferredTicks(), "a severe push with a feasible answer and a full tell is allowed");
        assertTrue(session.tellOpen(), "the tell opens, which shows the player the push before it lands");
    }
}
