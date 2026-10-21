package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupRole;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.Reaction;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.TickSignals;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tick layer reads only the session it visits (M25.2). A reader that is handed another mob fails the test.
 */
class TickLayerGroupLocalityTest {
    @Test
    void theLayerAsksOnlyForTheSessionItVisits() {
        var first = session(1);
        var second = session(2);
        var decoy = new MobKey(UUID.randomUUID(), 1);
        armSlot(first);
        armSlot(second);
        var seen = new ArrayList<MobKey>();
        var word = new long[1];
        var reader = reader(seen, decoy, word);
        var board = new IntentBoard();
        control(board, first, Movement.HOLD);
        control(board, second, Movement.HOLD);
        visit(List.of(first), board, reader, word, far());
        assertEquals(List.of(first.key()), List.copyOf(seen));
        assertFalse(seen.contains(decoy));
        assertEquals(MicroBehaviour.SLOT, first.micro());
        assertEquals(MicroBehaviour.NATIVE, second.micro());
        seen.clear();
        visit(List.of(first, second), board, reader, word, far());
        assertEquals(List.of(first.key(), second.key()), List.copyOf(seen));
        assertEquals(MicroBehaviour.SLOT, second.micro());
    }

    @Test
    void enterWithoutAMeleeTokenHoversAndKeepsTheIntent() {
        var session = session(3);
        session.groupCopy().publish(1, 1, GroupRole.PRESSURE, -1, 1, GroupPlan.PRESS_MAIN_TARGET.ordinal(), 0, 1,
                GroupMode.COORDINATE);
        session.armReaction(Reaction.PUNISH_RECHARGE.ordinal(), 20, 40);
        var board = new IntentBoard();
        control(board, session, Movement.APPROACH);
        var word = new long[1];
        visit(List.of(session), board, reader(new ArrayList<>(), new MobKey(UUID.randomUUID(), 1), word), word,
                enter());
        assertEquals(MicroBehaviour.HOVER, session.micro());
        assertEquals(Movement.APPROACH, session.intent().orElseThrow().movement());
        assertTrue(session.groupCopy().takeMeleeAsk());
    }

    private static void armSlot(MobSession session) {
        session.groupCopy().publish(1, 1, GroupRole.PRESSURE, 0, 1, GroupPlan.SURROUND_TARGET.ordinal(), 0, 1,
                GroupMode.COORDINATE);
        session.groupCopy().slotFar(true);
    }

    private static long far() {
        return TickSignals.pack(TickSignals.PRESENT, 40, 0, 0, 0, 100, 100);
    }

    private static long enter() {
        return TickSignals.pack(TickSignals.PRESENT | (1L << TickSignals.TARGET_ATTACK_RECHARGING), 8, 0, 0, 0, 100,
                100);
    }

    private static void visit(List<MobSession> active, IntentBoard board, CombatReader reader, long[] word,
                              long signals) {
        word[0] = signals;
        TickLayer.visit(active, 8, 0, 0L, 1_000L, board, reader, quiet(), new DecisionMetrics(() -> 0L),
                ignored -> {
                }, 1_000_000, 0, 0, 0L, () -> 0L, ignored -> {
                }, DecisionTap.NONE);
    }

    private static void control(IntentBoard board, MobSession session, Movement movement) {
        var intent = new TacticalIntent(session.key(), new TacticId("hold_ground"), movement, Optional.empty(), 0.9,
                List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE, false);
        session.adoptIntent(intent);
        session.recordTarget(EngineFixtures.player(1), 4);
        session.scheduleReady(0);
        board.publish(session.key(), intent.toView());
    }

    private static CombatReader reader(List<MobKey> seen, MobKey decoy, long[] word) {
        return new CombatReader() {
            @Override
            public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members,
                                                 akyrion.thepredict.domain.CaptureContext context) {
                return Optional.empty();
            }

            @Override
            public Optional<MobProbe> probe(MobKey mob) {
                reject(mob);
                return Optional.empty();
            }

            @Override
            public boolean inCombat(UUID entityId) {
                return false;
            }

            @Override
            public boolean withinFollowRange(MobKey mob, MobKey member) {
                reject(mob);
                reject(member);
                return false;
            }

            @Override
            public boolean isActive(UUID entityId) {
                return true;
            }

            @Override
            public Optional<ThreatTableState> threatTable(MobKey mob) {
                reject(mob);
                return Optional.empty();
            }

            @Override
            public boolean playerTarget(UUID entityId) {
                return true;
            }

            @Override
            public long tickSignals(MobKey mob, UUID expectedTarget) {
                reject(mob);
                seen.add(mob);
                return word[0];
            }

            @Override
            public long applyZone(MobSession session, long signals) {
                reject(session.key());
                return signals;
            }

            private void reject(MobKey mob) {
                if (mob.equals(decoy)) {
                    throw new IllegalStateException("tick layer asked for another session");
                }
            }
        };
    }

    private static ExecutionSteps quiet() {
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
        };
    }

    private static MobSession session(int id) {
        var hold = new TacticId("hold_ground");
        var timing = new DecisionTiming(40, 20, 100, 0.5, 0.2, 1).withLayer(TickLayerMode.FULL, ReactionScope.ALL,
                Set.of(), 0, DecisionCadence.STANDARD, 6, 40);
        var profile = new TacticalProfile("guardian_ai", "Guardian", "Defeat the target",
                Map.of(hold, new Tactic(hold, Movement.HOLD, Optional.empty(), Optional.empty(), 50)), Map.of(),
                List.of(), new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing);
        return new MobSession(new MobKey(new UUID(3, id), 1), profile);
    }
}
