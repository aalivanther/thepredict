package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.Trend;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.GUARD;
import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Retreat only from a pursuer (segunda ronda de efectividad, {@link Pursuit}): out of melee, the local chain does not
 * run from a target that the last capture did not see closing in; it keeps the best tactic that stays. A target that
 * closes in, or one in melee, leaves the author's retreat as it was.
 */
class PursuitTest {
    private final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
    private final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
    private int opened;
    private final IntentControl control = new IntentControl(sessions.board, new Cooling(), metrics,
            new ExecutionLog(line -> {
            }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
            DecisionTap.NONE);

    @Test
    void onlyATargetAwayFromMeleeThatDoesNotCloseInIsNoPursuer() {
        assertTrue(Pursuit.absent(session(DistanceBand.RANGED, Trend.STEADY)), "it holds its distance");
        assertTrue(Pursuit.absent(session(DistanceBand.RANGED, Trend.BACKING_AWAY)), "it backs away");
        assertFalse(Pursuit.absent(session(DistanceBand.RANGED, Trend.CLOSING)), "it comes at the mob");
        assertFalse(Pursuit.absent(session(DistanceBand.MELEE, Trend.STEADY)), "it is already on the mob");
        var blind = sessions.registry.open(EngineFixtures.id(++opened),
                ExecutionFixture.profile(ExecutionFixture.timing(1), 0));
        assertFalse(Pursuit.absent(blind), "without a capture the retreat stands");
    }

    @Test
    void aRetreatFromATargetThatHoldsItsDistanceTurnsBack() {
        var session = retreating(DistanceBand.RANGED, Trend.STEADY);
        fallback().apply(session, EngineFixtures.WORLD, ControlLevel.LOCAL, true, 10, 0);
        assertEquals(KEEP, session.intent().orElseThrow().tactic(), "the best local tactic that does not retreat");
    }

    @Test
    void aRetreatFromAPursuerOrInMeleeGoesOn() {
        var chased = retreating(DistanceBand.RANGED, Trend.CLOSING);
        fallback().apply(chased, EngineFixtures.WORLD, ControlLevel.LOCAL, true, 10, 0);
        assertEquals(FALL_BACK, chased.intent().orElseThrow().tactic());
        var close = retreating(DistanceBand.MELEE, Trend.STEADY);
        fallback().apply(close, EngineFixtures.WORLD, ControlLevel.LOCAL, true, 10, 0);
        assertEquals(FALL_BACK, close.intent().orElseThrow().tactic());
    }

    @Test
    void withoutAPursuerTheChainDoesNotPlanARetreat() {
        var session = session(DistanceBand.RANGED, Trend.STEADY);
        fallback().apply(session, EngineFixtures.WORLD, ControlLevel.BASE, true, 10, 0);
        assertEquals(GUARD, session.intent().orElseThrow().tactic(), "the retreat would have the highest priority");
        var chased = session(DistanceBand.RANGED, Trend.CLOSING);
        fallback().apply(chased, EngineFixtures.WORLD, ControlLevel.BASE, true, 10, 0);
        assertEquals(FALL_BACK, chased.intent().orElseThrow().tactic());
    }

    private FallbackStep fallback() {
        var engineSettings = new EngineSettings(2000, 2000, 8, 4, 600_000, 10, 2, 64, 16, 64, 1_000_000, 800_000_000L);
        var tracker = new ServiceStatusTracker(new ServiceCheck(ServiceState.UNCONFIGURED, "no key"),
                engineSettings.watchdogNanos(), () -> 0.5, line -> {
                });
        return new FallbackStep(tracker, sessions.encounters, new RequestBudget(engineSettings), control,
                new ExecutionSettings(1_000_000_000L, 3, "jev-1.13.0", false));
    }

    /** A session whose capture sees its target in {@code band} with {@code trend}; retreat and guard are eligible. */
    private MobSession session(DistanceBand band, Trend trend) {
        var session = sessions.registry.open(EngineFixtures.id(++opened),
                ExecutionFixture.profile(ExecutionFixture.timing(1), 0));
        session.recordInstall(new InstallResult.Installed(3), 1L);
        session.recordTarget(EngineFixtures.player(1), band == DistanceBand.MELEE ? 2.0 : 7.0);
        var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.ENTITY, 1), band, trend, true, true,
                false, false, false);
        session.facts(new MobFacts(session.key(), HealthBand.CRITICAL, Optional.of(relation), Set.of(),
                Set.of(FALL_BACK, GUARD), Set.of(), Set.of(), Map.of(), Set.of(PRESS, KEEP, FALL_BACK, GUARD)));
        session.tickSignals(TickSignals.pack(TickSignals.PRESENT | (1L << TickSignals.HAS_TARGET), 4, 0, 0, 0, 20,
                50));
        return session;
    }

    /** As {@link #session}, with a local intent that retreats and the stay tactics eligible too. */
    private MobSession retreating(DistanceBand band, Trend trend) {
        var session = session(band, trend);
        var relation = session.lastMobFacts().orElseThrow().target();
        session.facts(new MobFacts(session.key(), HealthBand.CRITICAL, relation, Set.of(),
                Set.of(KEEP, FALL_BACK, GUARD), Set.of(), Set.of(), Map.of(), Set.of(PRESS, KEEP, FALL_BACK, GUARD)));
        session.adoptIntent(new TacticalIntent(session.key(), FALL_BACK, Movement.RETREAT, Optional.empty(), 0.0,
                List.of(), Map.of(), ControlLevel.LOCAL, 0, Long.MAX_VALUE / 2, false));
        session.tacticStarted(0);
        return session;
    }

    /** Combat actions whose every skill cools down. */
    private static final class Cooling implements CombatActions {
        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            return false;
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, UUID participantId, double amount) {
        }
    }
}
