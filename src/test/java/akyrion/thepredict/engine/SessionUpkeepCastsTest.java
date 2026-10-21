package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.PlayMode;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PZ-06, PZ-17 and PZ-21: a casts-only world still runs the pressure step without a new list per tick, and a failure is
 * the execution's own drop.
 */
class SessionUpkeepCastsTest {
    private final EngineFixtures.Sessions fixture = new EngineFixtures.Sessions();

    @Test
    void aWorldOfCastsOnlyProfilesResolvesThePressureOfAPlayerFight() {
        var session = castsOnly();
        var upkeep = upkeep(new Steps());
        upkeep.tickLayer(96, 600, 4, 1L, 1_000L, 1_000L, () -> 1_000L);
        assertEquals(PlayMode.PLAYER, session.difficulty().play(),
                "the tick layer is not running, but the M22 step still resolves the fight");
    }

    @Test
    void aFailureWhileCastingIsDroppedByTheExecutionAtItsTick() {
        var session = castsOnly();
        session.scheduleReady(7L);
        var steps = new Steps();
        steps.failing = true;
        upkeep(steps).tickLayer(96, 600, 4, 7L, 1_000L, 1_000L, () -> 1_000L);
        assertEquals(List.of(session.key() + "@7"), steps.failures,
                "the drop closes the outcome window through the execution, not only the intent");
    }

    @Test
    void aTickOfACastsOnlyWorldAllocatesLessThanOneObjectOnAverage() {
        castsOnly();
        var upkeep = upkeep(new Steps());
        java.util.function.LongSupplier nanos = () -> 1_000L;
        for (var tick = 0L; tick < 30_000L; tick++) {
            upkeep.tickLayer(96, 600, 4, tick, 1_000L, 1_000L, nanos);
        }
        var threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        var thread = Thread.currentThread().threadId();
        var before = threads.getThreadAllocatedBytes(thread);
        var ticks = 10_000L;
        for (var tick = 30_000L; tick < 30_000L + ticks; tick++) {
            upkeep.tickLayer(96, 600, 4, tick, 1_000L, 1_000L, nanos);
        }
        var perTick = (threads.getThreadAllocatedBytes(thread) - before) / (double) ticks;
        assertTrue(perTick < 16.0, "the smallest object is 16 bytes; the walk allocated " + perTick + " per tick");
    }

    private MobSession castsOnly() {
        var base = EngineFixtures.profile("Guardian");
        assertEquals(akyrion.thepredict.domain.TickLayerMode.CASTS, base.timing().tickLayer());
        var session = fixture.registry.open(EngineFixtures.id(1), base);
        var target = EngineFixtures.player(1);
        session.recordTarget(target, 3.0);
        session.adoptIntent(new TacticalIntent(session.key(), new TacticId("hold_ground"), Movement.HOLD,
                Optional.empty(), 0.9, List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE, false));
        return session;
    }

    private SessionUpkeep upkeep(ExecutionSteps steps) {
        return new SessionUpkeep(fixture.registry, fixture.encounters, fixture.agenda, fixture.board,
                new ContextSignals(), new QuietReader(Map.of(), true), control(), () -> 1L, steps,
                new DecisionMetrics(() -> 0L));
    }

    private static MobControl control() {
        return new MobControl() {
            @Override
            public InstallResult ensureInstalled(MobKey key, TacticalProfile profile) {
                return new InstallResult.Installed(1);
            }

            @Override
            public MovementProgress progress(MobKey key) {
                return new MovementProgress(false, false, false, OptionalDouble.empty());
            }

            @Override
            public List<GoalSummary> describeGoals(MobKey key) {
                return List.of();
            }

            @Override
            public void release(MobKey key) {
            }
        };
    }

    /** Steps that can throw on a cast and that drop a session the way the execution does. */
    private static final class Steps implements ExecutionSteps, SessionFailure {
        final List<String> failures = new ArrayList<>();
        boolean failing;

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
            assertTrue(session.nextReadyTick() <= tick);
            if (failing) {
                throw new IllegalStateException("cast failed");
            }
        }

        @Override
        public void failed(MobSession session, long tick) {
            failures.add(session.key() + "@" + tick);
        }
    }
}
