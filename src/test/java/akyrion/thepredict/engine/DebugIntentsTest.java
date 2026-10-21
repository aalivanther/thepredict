package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.ContextRule;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The intents of {@code /thepredict debug} (M12.3) over the whole engine with doubles of the ports (rule P2). */
class DebugIntentsTest {
    private ExecutionFixture fixture = new ExecutionFixture();

    private DebugIntents.Outcome set(int mob, TacticId tactic, int ticks) {
        return fixture.steps.debugIntents().set(EngineFixtures.id(mob), tactic, ticks, fixture.clock.tick,
                fixture.clock.nanos);
    }

    @Test
    void aDebugIntentIsALocalIntentOfAProfileTacticThatSignalsItAndIsMarked() {
        fixture.open(1);
        var applied = assertInstanceOf(DebugIntents.Outcome.Applied.class, set(1, ExecutionFixture.GUARD, 100));
        assertEquals(ControlLevel.LOCAL, applied.intent().level());
        assertEquals(Movement.HOLD, applied.intent().movement());
        assertEquals(1.0, applied.intent().confidence());
        assertEquals(ExecutionFixture.GUARD, fixture.view(1).tactic());
        assertTrue(fixture.view(1).controls(fixture.clock.nanos));
        assertTrue(fixture.session(1).debugIntent());
        assertEquals(List.of("guard_post"), fixture.actions.signals(1));
    }

    @Test
    void withoutADurationTheIntentLastsTheMaximumIntentTicksOfTheProfile() {
        fixture.open(1);
        var applied = (DebugIntents.Outcome.Applied) fixture.steps.debugIntents().set(EngineFixtures.id(1),
                ExecutionFixture.KEEP, fixture.clock.tick, fixture.clock.nanos);
        assertEquals(100, applied.durationTicks());
        assertEquals(fixture.clock.nanos + 100 * ExecutionFixture.TICK_NANOS, applied.intent().expiresAtNanos());
    }

    @Test
    void anUnknownTacticANonExistentSessionOrABadDurationChangesNothing() {
        fixture.open(1);
        var unknown = assertInstanceOf(DebugIntents.Outcome.Rejected.class, set(1, new TacticId("dance_around"), 20));
        assertTrue(unknown.reason().contains("does not declare the tactic dance_around"), unknown.reason());
        assertTrue(unknown.reason().contains("press_attack"), unknown.reason());
        var missing = assertInstanceOf(DebugIntents.Outcome.Rejected.class, set(2, ExecutionFixture.GUARD, 20));
        assertTrue(missing.reason().contains("no session"), missing.reason());
        assertThrows(IllegalArgumentException.class, () -> set(1, ExecutionFixture.GUARD, 0));
        assertThrows(IllegalArgumentException.class, () -> set(1, ExecutionFixture.GUARD, DebugIntents.MAX_TICKS + 1));
        assertFalse(fixture.session(1).debugIntent());
        assertEquals(ControlLevel.BASE, fixture.view(1).level());
        fixture.sessions.registry.suspend(EngineFixtures.id(1));
        var suspended = assertInstanceOf(DebugIntents.Outcome.Rejected.class, set(1, ExecutionFixture.GUARD, 20));
        assertTrue(suspended.reason().contains("suspended"), suspended.reason());
    }

    @Test
    void aTacticTheLastCaptureFoundIneligibleIsRefusedWithTheReasonOfEligibility() {
        var context = new ContextRule(new ContextId("wounded"), List.of(), 90, Set.of(ExecutionFixture.FALL_BACK),
                Optional.empty());
        var base = ExecutionFixture.profile(ExecutionFixture.timing(1), 0);
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                base.choices(), List.of(context), base.movement(), base.health(), base.threat(), base.baseAi(),
                base.timing());
        var session = fixture.open(1, profile);
        session.facts(new MobFacts(session.key(), HealthBand.CRITICAL, Optional.empty(), Set.of(context.id()),
                Set.of(ExecutionFixture.FALL_BACK), Set.of(), Set.of()));
        var byContext = assertInstanceOf(DebugIntents.Outcome.Rejected.class, set(1, ExecutionFixture.GUARD, 20));
        assertTrue(byContext.reason().contains("the active context wounded allows only fall_back"), byContext.reason());
        session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.empty(), Set.of(),
                Set.of(ExecutionFixture.GUARD), Set.of(), Set.of()));
        var notReady = assertInstanceOf(DebugIntents.Outcome.Rejected.class, set(1, ExecutionFixture.PRESS, 20));
        assertTrue(notReady.reason().contains("its skill was on its native cooldown or it needs a target"),
                notReady.reason());
        assertInstanceOf(DebugIntents.Outcome.Applied.class, set(1, ExecutionFixture.GUARD, 20));
    }

    @Test
    void theCoordinatorLeavesAHeldMobAloneAndNeverAsksJevAboutIt() {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        set(1, ExecutionFixture.GUARD, 200);
        fixture.run(60);
        assertTrue(fixture.client.requests.isEmpty(), fixture.client.requests.toString());
        assertEquals(ExecutionFixture.GUARD, fixture.view(1).tactic());
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertTrue(fixture.count(DecisionDispatcher.Outcome.NO_QUESTIONS) > 0);
    }

    @Test
    void anExpiredDebugIntentReturnsTheMobToBaseWithExpiredAndIsNotCountedAsAFallback() {
        fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.open(1);
        set(1, ExecutionFixture.GUARD, 10);
        fixture.run(8);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        fixture.run(6);
        assertEquals(ControlLevel.BASE, fixture.view(1).level());
        assertFalse(fixture.session(1).debugIntent());
        assertEquals(Optional.of(FallbackReason.EXPIRED), fixture.session(1).lastFallbackReason());
        assertEquals(0, fixture.count(FallbackReason.EXPIRED));
    }

    @Test
    void aDebugIntentWithAChoiceCastsItOnceLikeAnyLocalIntent() {
        fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.open(1);
        fixture.target(1, 1);
        set(1, ExecutionFixture.PRESS, 100);
        fixture.run(20);
        assertEquals(1, fixture.actions.casts(1));
        assertTrue(fixture.session(1).intent().orElseThrow().skillConsumed());
        assertTrue(fixture.session(1).debugIntent());
    }

    @Test
    void releaseDropsAnyIntentToBaseAndTheChainDecidesAgain() {
        fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.open(1);
        set(1, ExecutionFixture.GUARD, 200);
        assertTrue(fixture.steps.debugIntents().release(EngineFixtures.id(1), fixture.clock.tick));
        assertEquals(ControlLevel.BASE, fixture.view(1).level());
        assertFalse(fixture.session(1).debugIntent());
        assertTrue(fixture.session(1).intent().isEmpty());
        assertFalse(fixture.steps.debugIntents().release(EngineFixtures.id(7), fixture.clock.tick));
        fixture.run(4);
        assertEquals(ControlLevel.BASE, fixture.view(1).level());
    }
}
