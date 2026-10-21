package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.TickTier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickLayerTest {
    @Test
    void tiersFollowTheWarmFormula() {
        assertEquals(TickTier.COLD, TickTier.of(false, true, 10, 1, false, true, false));
        assertEquals(TickTier.COLD, TickTier.of(true, false, 10, 1, false, true, false));
        assertEquals(TickTier.COLD, TickTier.of(true, true, Long.MAX_VALUE, 1, false, true, false));
        assertEquals(TickTier.WARM, TickTier.of(true, true, 10, 1, true, false, true));
        assertEquals(TickTier.WARM, TickTier.of(true, true, 8, 1, false, true, false));
        assertEquals(TickTier.HOT, TickTier.of(true, true, 3, 1, false, true, false));
        assertEquals(TickTier.HOT, TickTier.of(true, true, 20, 1, false, true, true));
    }

    @Test
    void aWarmSessionCastsOnTheTickTheSkillComesDue() {
        var casts = new AtomicInteger();
        var session = session(TickLayerMode.REACTIONS);
        var board = controlling(session);
        var target = UUID.randomUUID();
        session.recordTarget(target, 12);
        session.noteProbe(new MobProbe(session.key(), new UUID(0, 1), HealthBand.HEALTHY, Optional.of(target),
                Optional.of(DistanceBand.BEYOND)), 0);
        session.scheduleReady(7);
        var steps = new ExecutionSteps() {
            @Override
            public void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
            }

            @Override
            public void react(MobSession found, MobProbe probe, long tick, long nowNanos) {
            }

            @Override
            public void evaluated(Encounter encounter, long tick, long nowNanos) {
            }

            @Override
            public void castReady(MobSession found, long tick, long nowNanos) {
                casts.incrementAndGet();
            }
        };
        TickLayer.visit(List.of(session), 8, 0, 7, 1_000, board, reader(), steps, metrics(), sessionFound -> {
        }, 600, 4, 0, 0, () -> 0L, found -> {
        });
        assertEquals(1, casts.get());
    }

    @Test
    void comparisonsDoNotSpendTheVisitCapAndTheClockIsReadOnTheBudgetPoints() {
        var reads = new AtomicInteger();
        var sessions = new ArrayList<MobSession>();
        var board = new IntentBoard();
        for (var index = 0; index < 8; index++) {
            var session = session(TickLayerMode.REACTIONS);
            controlling(board, session);
            session.recordTarget(UUID.randomUUID(), 3);
            session.scheduleReady(2);
            sessions.add(session);
        }
        var cold = session(TickLayerMode.REACTIONS);
        controlling(board, cold);
        sessions.add(cold);
        var walk = TickLayer.visit(sessions, 96, 0, 1, 1_000, board, reader(), quiet(), metrics(), found -> {
        }, 600, 4, 0, 0, () -> {
            reads.incrementAndGet();
            return 0L;
        }, found -> {
        });
        assertEquals(1, reads.get());
        assertEquals(8, walk.visits(), "the cold session is a comparison, not a visit");
    }

    @Test
    void aCastReadsTheClockOncePerCast() {
        var reads = new AtomicInteger();
        var casts = new AtomicInteger();
        var sessions = new ArrayList<MobSession>();
        var board = new IntentBoard();
        for (var index = 0; index < 8; index++) {
            var session = session(TickLayerMode.REACTIONS);
            controlling(board, session);
            session.recordTarget(UUID.randomUUID(), 3);
            session.scheduleReady(1);
            sessions.add(session);
        }
        TickLayer.visit(sessions, 96, 0, 1, 1_000, board, reader(), counting(casts), metrics(), found -> {
        }, 600, 4, 0, 0, () -> {
            reads.incrementAndGet();
            return 0L;
        }, found -> {
        });
        assertEquals(8, casts.get());
        assertEquals(8, reads.get());
    }

    @Test
    void threeHundredHotSessionsFinishWithinFourTicksInRotatingOrder() {
        var sessions = new ArrayList<MobSession>();
        var board = new IntentBoard();
        for (var index = 0; index < 300; index++) {
            var session = session(TickLayerMode.REACTIONS);
            controlling(board, session);
            session.recordTarget(UUID.randomUUID(), 3);
            session.scheduleReady(1);
            sessions.add(session);
        }
        var order = new ArrayList<MobSession>();
        var cursor = 0;
        for (var tick = 1; tick <= 4; tick++) {
            var walked = TickLayer.visit(sessions, 96, cursor, tick, 1_000, board, reader(), recording(order),
                    metrics(), found -> {
                    }, 600, 4, 0, 0, () -> 0L, found -> {
                    });
            assertEquals(Math.min(96, 300 - 96 * (tick - 1)), walked.visits(), "tick " + tick);
            cursor = walked.cursor();
        }
        assertEquals(sessions, order, "each tick goes on where the previous one stopped, in enrollment order");
    }

    @Test
    void noDegradationStepDelaysACastThatFitsTheCap() {
        for (var step = 0; step <= 3; step++) {
            var board = new IntentBoard();
            var casts = new AtomicInteger();
            var sessions = new ArrayList<MobSession>();
            for (var index = 0; index < 4; index++) {
                var session = session(TickLayerMode.FULL);
                controlling(board, session);
                session.recordTarget(UUID.randomUUID(), 3);
                session.scheduleReady(7);
                sessions.add(session);
            }
            TickLayer.visit(sessions, 8, 0, 7, 1_000, board, reader(), counting(casts), metrics(), found -> {
            }, 600, 4, step, 0, () -> 0L, found -> {
            });
            assertEquals(4, casts.get(), "step " + step);
        }
    }

    @Test
    void aPackedWalkKeepsItsFourFieldsUpToTheirLimits() {
        for (var visits : new int[] {0, 1, 96, Integer.MAX_VALUE}) {
            for (var cursor : new int[] {0, 7, Integer.MAX_VALUE}) {
                for (var flags = 0; flags < 4; flags++) {
                    var walk = new TickLayer.Walk(visits, cursor, (flags & 1) != 0, (flags & 2) != 0);
                    var packed = TickLayer.Walk.pack(visits, cursor, walk.capped(), walk.underHalf());
                    assertEquals(walk, TickLayer.Walk.of(packed), "PZ-17: the upkeep reads the walk without a record");
                }
            }
        }
    }

    @Test
    void underDegradationTheDeferredCountIsWhatTheSameStepWouldHaveVisited() {
        for (var step = 1; step <= 3; step++) {
            var capped = warm(40);
            var metrics = metrics();
            var walk = TickLayer.visit(capped, 1, 0, 9, 1_000, board(capped), reader(), quiet(), metrics, found -> {
            }, 600, 4, step, 0, () -> 0L, found -> {
            });
            var rest = warm(40).subList(walk.cursor(), 40);
            var free = TickLayer.visit(rest, 96, 0, 9, 1_000, board(rest), reader(), quiet(), metrics(), found -> {
            }, 600, 4, step, 0, () -> 0L, found -> {
            });
            assertEquals(free.visits(), metrics.layer(DecisionMetrics.LayerCounter.TICK_LAYER_DEFERRED),
                    "step " + step + ": the counter counts the sessions this step would visit, not those of step 0");
        }
    }

    /** {@code count} warm sessions with fixed ids, so two calls give the same hashes. */
    private static List<MobSession> warm(int count) {
        var sessions = new ArrayList<MobSession>();
        for (var index = 0; index < count; index++) {
            var session = session(TickLayerMode.REACTIONS, new UUID(7, index));
            session.recordTarget(new UUID(8, index), 3);
            session.scheduleReady(20);
            sessions.add(session);
        }
        return sessions;
    }

    private static IntentBoard board(List<MobSession> sessions) {
        var board = new IntentBoard();
        for (var session : sessions) {
            controlling(board, session);
        }
        return board;
    }

    private static ExecutionSteps recording(List<MobSession> order) {
        return new ExecutionSteps() {
            @Override
            public void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
            }

            @Override
            public void react(MobSession found, MobProbe probe, long tick, long nowNanos) {
            }

            @Override
            public void evaluated(Encounter encounter, long tick, long nowNanos) {
            }

            @Override
            public void castReady(MobSession found, long tick, long nowNanos) {
                order.add(found);
                found.scheduleReady(Long.MAX_VALUE);
            }
        };
    }

    private static ExecutionSteps counting(AtomicInteger casts) {
        return new ExecutionSteps() {
            @Override
            public void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
            }

            @Override
            public void react(MobSession found, MobProbe probe, long tick, long nowNanos) {
            }

            @Override
            public void evaluated(Encounter encounter, long tick, long nowNanos) {
            }

            @Override
            public void castReady(MobSession found, long tick, long nowNanos) {
                casts.incrementAndGet();
                found.scheduleReady(Long.MAX_VALUE);
            }
        };
    }

    private static ExecutionSteps quiet() {
        return counting(new AtomicInteger());
    }

    private static CombatReader reader() {
        return new CombatReader() {
            @Override
            public java.util.Optional<akyrion.thepredict.domain.CombatFacts> capture(
                    akyrion.thepredict.domain.EncounterId encounter, List<MobKey> members,
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

    private static DecisionMetrics metrics() {
        return new DecisionMetrics(() -> 0L);
    }

    private static IntentBoard controlling(MobSession session) {
        var board = new IntentBoard();
        controlling(board, session);
        return board;
    }

    private static void controlling(IntentBoard board, MobSession session) {
        var intent = new TacticalIntent(session.key(), new TacticId("hold_ground"), Movement.HOLD, Optional.empty(),
                0.9, List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE, false);
        session.adoptIntent(intent);
        board.publish(session.key(), intent.toView());
    }

    private static MobSession session(TickLayerMode mode) {
        return session(mode, UUID.randomUUID());
    }

    private static MobSession session(TickLayerMode mode, UUID id) {
        var hold = new TacticId("hold_ground");
        var timing = new DecisionTiming(40, 20, 100, 0.5, 0.2, 1).withLayer(mode, ReactionScope.ALL, java.util.Set.of(),
                0, DecisionCadence.STANDARD, 6, 40);
        var profile = new TacticalProfile("guardian_ai", "Guardian", "Defeat the target",
                Map.of(hold, new akyrion.thepredict.domain.Tactic(hold, Movement.HOLD, Optional.empty(), Optional.empty(),
                        50)),
                Map.of(), List.of(), new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing);
        return new MobSession(new MobKey(id, 1), profile);
    }
}
