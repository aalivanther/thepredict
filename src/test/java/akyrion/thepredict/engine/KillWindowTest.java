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
 * The kill window (fase de efectividad): a local intent that retreats turns back while its target is one own hit from
 * death, and only then. One hit is the largest share one own hit took from this target.
 */
class KillWindowTest {
    private final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
    private final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
    private final IntentControl control = new IntentControl(sessions.board, new Cooling(), metrics,
            new ExecutionLog(line -> {
            }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
            DecisionTap.NONE);

    @Test
    void theWindowOpensWhenTheTargetIsOneOwnHitFromDeath() {
        var session = retreating(7);
        assertFalse(KillWindow.open(session), "before any own hit there is no measure of one");
        session.ownImpacts().noteDealt(EngineFixtures.player(1), 0.083);
        assertTrue(KillWindow.open(session), "7 % left against hits of 8.3 %");
        session.tickSignals(signals(9));
        assertFalse(KillWindow.open(session), "9 % needs two hits");
        session.tickSignals(TickSignals.PRESENT);
        assertFalse(KillWindow.open(session), "an invalid reading never opens it");
    }

    @Test
    void anotherTargetForgetsTheMeasureOfOneHit() {
        var session = retreating(7);
        session.ownImpacts().noteDealt(EngineFixtures.player(1), 0.083);
        session.recordTarget(EngineFixtures.player(2), 2.0);
        assertEquals(0.0, session.ownImpacts().strongestShare());
        session.ownImpacts().noteDealt(EngineFixtures.player(2), 0.05);
        session.ownImpacts().noteDealt(EngineFixtures.player(2), 0.02);
        assertEquals(0.05, session.ownImpacts().strongestShare(), "the largest share stays");
    }

    @Test
    void aRetreatingLocalIntentTurnsBackInsideTheWindow() {
        var session = retreating(7);
        session.ownImpacts().noteDealt(EngineFixtures.player(1), 0.083);
        fallback().apply(session, EngineFixtures.WORLD, ControlLevel.LOCAL, true, 10, 0);
        var intent = session.intent().orElseThrow();
        assertEquals(KEEP, intent.tactic(), "the best local tactic that does not retreat");
        assertEquals(ControlLevel.LOCAL, intent.level());
    }

    @Test
    void outsideTheWindowTheRetreatGoesOn() {
        var session = retreating(30);
        session.ownImpacts().noteDealt(EngineFixtures.player(1), 0.083);
        fallback().apply(session, EngineFixtures.WORLD, ControlLevel.LOCAL, true, 10, 0);
        assertEquals(FALL_BACK, session.intent().orElseThrow().tactic());
    }

    private FallbackStep fallback() {
        var engineSettings = new EngineSettings(2000, 2000, 8, 4, 600_000, 10, 2, 64, 16, 64, 1_000_000, 800_000_000L);
        var tracker = new ServiceStatusTracker(new ServiceCheck(ServiceState.UNCONFIGURED, "no key"),
                engineSettings.watchdogNanos(), () -> 0.5, line -> {
                });
        return new FallbackStep(tracker, sessions.encounters, new RequestBudget(engineSettings), control,
                new ExecutionSettings(1_000_000_000L, 3, "jev-1.13.0", false));
    }

    /** A session whose local intent retreats, its strike cooling down, the target at {@code health} percent. */
    private MobSession retreating(int health) {
        var session = sessions.registry.open(EngineFixtures.id(1),
                ExecutionFixture.profile(ExecutionFixture.timing(1), 0));
        session.recordInstall(new InstallResult.Installed(3), 1L);
        session.recordTarget(EngineFixtures.player(1), 2.0);
        var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.ENTITY, 1), DistanceBand.MELEE,
                Trend.STEADY, true, true, false, false, false);
        session.facts(new MobFacts(session.key(), HealthBand.CRITICAL, Optional.of(relation), Set.of(),
                Set.of(KEEP, FALL_BACK, GUARD), Set.of(), Set.of(), Map.of(), Set.of(PRESS, KEEP, FALL_BACK, GUARD)));
        session.adoptIntent(new TacticalIntent(session.key(), FALL_BACK, Movement.RETREAT, Optional.empty(), 0.0,
                List.of(), Map.of(), ControlLevel.LOCAL, 0, Long.MAX_VALUE / 2, false));
        session.tacticStarted(0);
        session.tickSignals(signals(health));
        return session;
    }

    private static long signals(int targetHealth) {
        return TickSignals.pack(TickSignals.PRESENT | (1L << TickSignals.HAS_TARGET), 4, 0, 0, 0, 20, targetHealth);
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
