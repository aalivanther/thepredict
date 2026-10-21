package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ReleaseOutcome;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The release lane stays outside {@code castIfReady} and keeps its tick (M23.4, B1). */
class TelegraphReleaseTest {
    @Test
    void aLeadOfFourteenDoesNotMoveWhenTheLayerRecalculatesAndDoesNotCastReady() {
        var session = session();
        long start = 1_000L;
        session.openTell(0, start, 24, 14, 20, false, UUID.randomUUID());
        session.busyUntil(start + 44);
        var casts = new AtomicInteger();
        var releases = new AtomicInteger();
        var steps = steps(casts, releases);
        for (var tick = start; tick < start + 14; tick++) {
            visit(List.of(session), tick, steps);
            assertEquals(start + 14, session.releaseTick(), "tick " + tick);
            assertEquals(start + 44, session.busyUntilTick());
        }
        assertEquals(0, casts.get());
        assertEquals(0, releases.get());
        visit(List.of(session), start + 14, steps);
        assertEquals(1, releases.get());
        assertEquals(0, casts.get());
    }

    @Test
    void fortyReleasesAdmitThirtyTwoAndOneHundredWaitAtMostFourTicks() {
        assertAdmitted(40, 32);
        var waiting = sessions(100);
        var steps = steps(new AtomicInteger(), new AtomicInteger());
        var left = 100;
        for (var tick = 0; tick < 4 && left > 0; tick++) {
            var before = stillDue(waiting, tick);
            visit(waiting, tick, steps);
            var released = before - stillDue(waiting, tick + 1);
            assertTrue(released <= 32, "tick " + tick + " released " + released);
            left -= released;
        }
        assertEquals(0, stillDue(waiting, 4));
    }

    @Test
    void aReleaseAtTheLeadStartsTheCastOnThatTick() {
        long start = 1_000L;
        var session = session();
        session.openTell(0, start, 24, 14, 20, false, UUID.randomUUID());
        var outcome = TelegraphRelease.release(session, start + 14, released(), new long[5]);
        assertEquals(ReleaseOutcome.RELEASED, outcome);
        assertEquals(start + 14, session.lastCastStartTick());
    }

    @Test
    void theReleaseWaitsWhileTheTargetStaysInvulnerableBeyondTheWindUpLeft() {
        long start = 1_000L;
        var session = typed();
        session.openTell(0, start, 28, 18, 20, false, UUID.randomUUID());
        session.tickSignals(hittable(12));
        assertEquals(ReleaseOutcome.DEFERRED_INVULNERABLE,
                TelegraphRelease.release(session, start + 18, released(), new long[5]),
                "only the wind-up of 10 is left before the hit: h = 12 lands in invulnerability");
        assertTrue(session.tellOpen());
        assertEquals(start + 19, session.releaseTick());
        session.tickSignals(hittable(10));
        assertEquals(ReleaseOutcome.RELEASED, TelegraphRelease.release(session, start + 19, released(), new long[5]),
                "h = w lands on the first hittable tick");
    }

    @Test
    void aTargetHittableBeforeTheWindUpEndsIsReleasedAtTheLead() {
        long start = 1_000L;
        var session = typed();
        session.openTell(0, start, 28, 18, 20, false, UUID.randomUUID());
        session.tickSignals(hittable(0));
        assertEquals(ReleaseOutcome.RELEASED, TelegraphRelease.release(session, start + 18, released(), new long[5]));
        assertEquals(start + 18, session.lastCastStartTick());
    }

    private static long hittable(int ticks) {
        return akyrion.thepredict.domain.TickSignals.pack(akyrion.thepredict.domain.TickSignals.PRESENT
                | (1L << akyrion.thepredict.domain.TickSignals.HAS_TARGET), 8, 0, 0, ticks, 100, 100);
    }

    /** A session whose only choice is a typed damaging melee of wind-up 10. */
    private static MobSession typed() {
        var purpose = new akyrion.thepredict.domain.SkillPurpose(akyrion.thepredict.domain.SkillRole.DAMAGE,
                Set.of(akyrion.thepredict.domain.SkillRole.DAMAGE), akyrion.thepredict.domain.Delivery.MELEE,
                java.util.OptionalDouble.of(3), java.util.OptionalDouble.empty(), 10,
                akyrion.thepredict.domain.CooldownClass.SHORT, akyrion.thepredict.domain.DamageTier.HIGH, Set.of(),
                Set.of(), true, akyrion.thepredict.domain.PurposeOrigin.DECLARED);
        var choice = SkillChoice.typed(new ChoiceId("slam"), "Slam", Set.of(), true, 0, purpose);
        var profile = EngineFixtures.profile("Guardian");
        var withChoice = new akyrion.thepredict.domain.TacticalProfile(profile.id(), profile.mythicMobId(),
                profile.objective(), profile.tactics(), java.util.Map.of(choice.id(), choice), profile.contexts(),
                profile.movement(), profile.health(), profile.threat(), profile.baseAi(), profile.timing());
        return new MobSession(new MobKey(UUID.randomUUID(), 0), withChoice);
    }

    @Test
    void eightDeferredLeaveBeforeThirtyNew() {
        var deferred = sessions(8);
        for (var session : deferred) {
            session.releaseDeferred(true);
        }
        var fresh = sessions(30);
        var active = new ArrayList<MobSession>();
        active.addAll(fresh);
        active.addAll(deferred);
        var order = new ArrayList<MobSession>();
        visit(active, 0L, steps(new AtomicInteger(), new AtomicInteger(), order));
        assertEquals(32, order.size());
        assertEquals(deferred, order.subList(0, 8));
    }

    private static void assertAdmitted(int count, int cap) {
        var waiting = sessions(count);
        var releases = new AtomicInteger();
        visit(waiting, 0L, steps(new AtomicInteger(), releases));
        assertEquals(cap, releases.get());
        assertEquals(count - cap, stillDue(waiting, 1));
    }

    private static int stillDue(List<MobSession> waiting, long tick) {
        var due = 0;
        for (var session : waiting) {
            if (session.releaseTick() <= tick) {
                due++;
            }
        }
        return due;
    }

    private static List<MobSession> sessions(int count) {
        var waiting = new ArrayList<MobSession>();
        for (var index = 0; index < count; index++) {
            var session = session();
            session.openTell(0, 0L, 10, 1, 0, false, UUID.randomUUID());
            session.releaseTick(0L);
            waiting.add(session);
        }
        return waiting;
    }

    private static void visit(List<MobSession> active, long tick, ExecutionSteps steps) {
        TickLayer.visit(active, 8, 0, tick, tick, new IntentBoard(), reader(), steps, new DecisionMetrics(() -> 0L),
                found -> {
                }, 600, 4, 0, 0L, () -> 0L, found -> {
                });
    }

    private static ExecutionSteps steps(AtomicInteger casts, AtomicInteger releases) {
        return steps(casts, releases, null);
    }

    private static ExecutionSteps steps(AtomicInteger casts, AtomicInteger releases, List<MobSession> order) {
        return new ExecutionSteps() {
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
                casts.incrementAndGet();
            }

            @Override
            public void releaseTell(MobSession session, long tick) {
                releases.incrementAndGet();
                if (order != null) {
                    order.add(session);
                }
                session.releaseTick(MobSession.NEVER);
            }
        };
    }

    private static CombatActions released() {
        return new CombatActions() {
            @Override
            public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
                return ExecutionOutcome.CAST;
            }

            @Override
            public boolean ready(MobKey mob, SkillChoice choice) {
                return true;
            }

            @Override
            public void signalTactic(MobKey mob, TacticId tactic) {
            }

            @Override
            public void addThreat(MobKey mob, UUID participantId, double amount) {
            }

            @Override
            public ReleaseOutcome release(MobKey mob, SkillChoice choice, Movement movement, UUID expectedTarget,
                                          akyrion.thepredict.engine.CastInterrupt interrupt) {
                return ReleaseOutcome.RELEASED;
            }
        };
    }

    private static CombatReader reader() {
        return new CombatReader() {
            @Override
            public Optional<akyrion.thepredict.domain.CombatFacts> capture(EncounterId encounter, List<MobKey> members,
                    akyrion.thepredict.domain.CaptureContext context) {
                return Optional.empty();
            }

            @Override
            public Optional<MobProbe> probe(MobKey mob) {
                return Optional.empty();
            }

            @Override
            public boolean inCombat(UUID entityId) {
                return false;
            }

            @Override
            public boolean withinFollowRange(MobKey mob, MobKey member) {
                return false;
            }

            @Override
            public boolean isActive(UUID entityId) {
                return true;
            }

            @Override
            public Optional<akyrion.thepredict.domain.ThreatTableState> threatTable(MobKey mob) {
                return Optional.empty();
            }
        };
    }

    private static MobSession session() {
        var choice = new ChoiceId("melee");
        var hold = new TacticId("hold_ground");
        var profile = EngineFixtures.profile("Guardian");
        var withChoice = new akyrion.thepredict.domain.TacticalProfile(profile.id(), profile.mythicMobId(),
                profile.objective(), profile.tactics(), java.util.Map.of(choice,
                SkillChoice.untyped(choice, "Melee", Set.of(), true, 0)), profile.contexts(), profile.movement(),
                profile.health(), profile.threat(), profile.baseAi(), profile.timing());
        return new MobSession(new MobKey(UUID.randomUUID(), 0), withChoice);
    }
}
