package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.ReleaseOutcome;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TickSignals;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The release lane handles each session inside its own boundary and reads the signals of the release tick. */
class ReleaseLaneTest {
    private static final long TICK = 100L;

    @Test
    void aReleaseThatThrowsDropsOnlyItsSessionAndTheOthersReleaseAndCast() {
        var board = new IntentBoard();
        var broken = due(board);
        var healthy = due(board);
        var steps = new Steps();
        steps.throwing.add(broken.key());
        var failed = new ArrayList<MobKey>();
        visit(List.of(broken, healthy), TICK, board, quiet(), steps, session -> failed.add(session.key()));
        assertEquals(List.of(broken.key()), failed);
        assertFalse(broken.tellOpen(), "the failed tell closes so the lane cannot pick it again");
        assertEquals(1, steps.released.getOrDefault(healthy.key(), 0));
        assertEquals(1, steps.casts.getOrDefault(healthy.key(), 0), "the ring still runs on the same tick");
        healthy.scheduleReady(TICK + 1);
        visit(List.of(broken, healthy), TICK + 1, board, quiet(), steps, session -> failed.add(session.key()));
        assertEquals(List.of(broken.key()), failed, "the dropped session fails once, not on every tick");
        assertEquals(2, steps.casts.getOrDefault(healthy.key(), 0));
    }

    @Test
    void aStanceStepThatThrowsDropsOnlyItsSession() {
        var board = new IntentBoard();
        var broken = due(board);
        var healthy = due(board);
        broken.closeTell();
        healthy.closeTell();
        broken.nextStanceTick(TICK);
        healthy.nextStanceTick(TICK);
        var steps = new Steps();
        steps.throwingStance.add(broken.key());
        var failed = new ArrayList<MobKey>();
        visit(List.of(broken, healthy), TICK, board, quiet(), steps, session -> failed.add(session.key()));
        assertEquals(List.of(broken.key()), failed);
        assertEquals(1, steps.advanced.getOrDefault(healthy.key(), 0));
    }

    @Test
    void theReleaseReadsTheInvulnerabilityOfItsOwnTick() {
        var board = new IntentBoard();
        var session = due(board);
        session.tickSignals(TickSignals.pack(TickSignals.PRESENT | (1L << TickSignals.HAS_TARGET), 8, 0, 0, 0, 100,
                100));
        session.signalsAtTick(TICK - 2);
        var steps = new Steps();
        steps.real = true;
        var target = session.lastTargetId().orElseThrow();
        var fresh = TickSignals.pack(TickSignals.PRESENT | (1L << TickSignals.HAS_TARGET), 8, 0, 0, 12, 100, 100);
        visit(List.of(session), TICK, board, new QuietReader(Map.of(target, fresh), false), steps, ignored -> {
        });
        assertEquals(ReleaseOutcome.DEFERRED_INVULNERABLE, steps.outcomes.get(0),
                "h = 12 of this tick is above the wind-up of 10; the stale reading said 0");
        assertTrue(session.tellOpen());
        assertEquals(TICK + 1, session.releaseTick());
    }

    @Test
    void aReleaseDeferredByTheCapKeepsItsWindUpUntilItLeaves() {
        var board = new IntentBoard();
        var sessions = new ArrayList<MobSession>();
        for (var index = 0; index < 40; index++) {
            var session = due(board);
            session.nextStanceTick(TICK);
            sessions.add(session);
        }
        var steps = new Steps();
        visit(sessions, TICK, board, quiet(), steps, ignored -> {
        });
        var deferred = 0;
        for (var session : sessions) {
            if (session.releaseDeferred()) {
                deferred++;
                assertEquals(0, steps.advanced.getOrDefault(session.key(), 0),
                        "a deferred release does not step its stance before the hit");
            }
        }
        assertEquals(40 - sessions.get(0).profile().telegraph().maxReleasesPerTick(), deferred);
    }

    private static void visit(List<MobSession> active, long tick, IntentBoard board, CombatReader reader, Steps steps,
                              Consumer<MobSession> fail) {
        TickLayer.visit(active, 96, 0, tick, 1_000L, board, reader, steps, new DecisionMetrics(() -> 0L), fail, 600, 4,
                0, 0L, () -> 0L, ignored -> {
                });
    }

    /** A controlled session whose tell, of wind-up 10 and shown 28, is due at {@link #TICK} with its cast. */
    private static MobSession due(IntentBoard board) {
        var choice = slam();
        var base = EngineFixtures.profile("Guardian");
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                Map.of(choice.id(), choice), base.contexts(), base.movement(), base.health(), base.threat(),
                base.baseAi(), base.timing());
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), profile);
        var target = UUID.randomUUID();
        session.recordTarget(target, 3.0);
        var intent = new TacticalIntent(session.key(), new TacticId("hold_ground"), Movement.HOLD, Optional.empty(),
                0.9, List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE, false);
        session.adoptIntent(intent);
        board.publish(session.key(), intent.toView());
        session.openTell(0, TICK - 18, 28, 18, 10, false, target);
        session.scheduleReady(TICK);
        return session;
    }

    private static SkillChoice slam() {
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(3),
                OptionalDouble.empty(), 10, CooldownClass.SHORT, DamageTier.HIGH, Set.of(), Set.of(), true,
                PurposeOrigin.DECLARED);
        return SkillChoice.typed(new ChoiceId("slam"), "Slam", Set.of(), true, 0, purpose);
    }

    private static CombatReader quiet() {
        return new QuietReader(Map.of(), false);
    }

    /** Steps that record releases, casts and stance steps, and throw for the sessions a test names. */
    private static final class Steps implements ExecutionSteps {
        final Set<MobKey> throwing = new java.util.HashSet<>();
        final Set<MobKey> throwingStance = new java.util.HashSet<>();
        final Map<MobKey, Integer> released = new HashMap<>();
        final Map<MobKey, Integer> casts = new HashMap<>();
        final Map<MobKey, Integer> advanced = new HashMap<>();
        final List<ReleaseOutcome> outcomes = new ArrayList<>();
        boolean real;

        @Override
        public void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
        }

        @Override
        public void react(MobSession session, MobProbe probe, long tick, long nowNanos) {
        }

        @Override
        public void evaluated(Encounter encounter, long tick, long nowNanos) {
        }

        @Override
        public void castReady(MobSession session, long tick, long nowNanos) {
            casts.merge(session.key(), 1, Integer::sum);
            session.scheduleReady(Long.MAX_VALUE);
        }

        @Override
        public void releaseTell(MobSession session, long tick) {
            if (throwing.contains(session.key())) {
                throw new IllegalStateException("skill of the author failed");
            }
            released.merge(session.key(), 1, Integer::sum);
            if (real) {
                outcomes.add(TelegraphRelease.release(session, tick, QuietActions.INSTANCE, new long[5]));
            } else {
                session.releaseTick(MobSession.NEVER);
            }
        }

        @Override
        public void advanceStance(MobSession session, long tick) {
            if (throwingStance.contains(session.key())) {
                throw new IllegalStateException("stance step failed");
            }
            advanced.merge(session.key(), 1, Integer::sum);
            session.nextStanceTick(Long.MAX_VALUE);
        }
    }
}
