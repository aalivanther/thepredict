package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CandidateIntent;
import akyrion.thepredict.domain.CandidateSet;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.ReactionBand;
import akyrion.thepredict.domain.ReactionBands;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One visit of each session state: reactions, micro, the cast, and the candidate set stay where the layer leaves them. */
class TickLayerIntegrationTest {
    private static final ChoiceId STRIKE = new ChoiceId("strike");
    private static final ChoiceId FINISH = new ChoiceId("finish");
    private static final TacticId KEEP = new TacticId("keep");
    private static final long TICK = 6;
    private static final long NOW = 1_000;

    @Test
    void eachSessionStateKeepsItsColumnAndTheLayerDoesNotReplan() {
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var gate = new Gate();
        var spy = new Spy(sessions, metrics, gate);
        var profile = profile();
        var jev = opened(sessions, 1, profile, ControlLevel.JEV);
        var alternative = opened(sessions, 2, profile, ControlLevel.ALTERNATIVE);
        var local = opened(sessions, 3, profile, ControlLevel.LOCAL);
        var base = sessions.registry.open(EngineFixtures.id(4), profile);
        var debug = opened(sessions, 5, profile, ControlLevel.JEV);
        debug.debugIntent(true);
        var suspended = opened(sessions, 6, profile, ControlLevel.JEV);
        suspended.state(SessionState.SUSPENDED);
        var closed = opened(sessions, 7, profile, ControlLevel.JEV);
        closed.state(SessionState.CLOSED);
        var stale = new MobSession(new MobKey(EngineFixtures.id(8), 7), profile);
        prepare(stale, ControlLevel.JEV);
        var before = Map.of(jev, snapshot(jev), alternative, snapshot(alternative), local, snapshot(local),
                debug, snapshot(debug));
        TickLayer.visit(List.of(jev, alternative, local, base, debug, suspended, closed, stale), 8, 0, TICK, NOW,
                sessions.board, reader(), steps(spy), metrics, found -> {
                }, 600, 4, 0, 0, () -> 0L, found -> {
                });
        for (var session : List.of(jev, alternative, local)) {
            assertEquals(MicroBehaviour.ORBIT, session.micro());
            assertTrue(session.slotUntil(ReactionBand.OPPORTUNITY) > TICK);
            assertEquals(List.of(mark(session, FINISH.value())), castsOf(gate, session));
            assertEquals(before.get(session).candidates, session.candidates().orElseThrow());
            assertUnchanged(session, before.get(session));
            assertFalse(session.intent().orElseThrow().skillConsumed());
        }
        assertEquals(MicroBehaviour.NATIVE, base.micro());
        assertEquals(0, base.slotUntil(ReactionBand.OPPORTUNITY));
        assertEquals(List.of(), castsOf(gate, base));
        assertEquals(MicroBehaviour.NATIVE, debug.micro());
        assertEquals(0, debug.slotUntil(ReactionBand.OPPORTUNITY));
        assertEquals(List.of(mark(debug, STRIKE.value())), castsOf(gate, debug));
        assertUnchanged(debug, before.get(debug));
        for (var session : List.of(suspended, closed, stale)) {
            assertEquals(MicroBehaviour.NATIVE, session.micro());
            assertEquals(0, session.slotUntil(ReactionBand.OPPORTUNITY));
            assertEquals(List.of(), castsOf(gate, session));
        }
        assertEquals(0, spy.applyCalls);
        assertEquals(0, spy.localCalls);
        assertEquals(0, spy.baseCalls);
    }

    private static void assertUnchanged(MobSession session, Shot shot) {
        var intent = session.intent().orElseThrow();
        assertEquals(shot.tactic, intent.tactic());
        assertEquals(shot.level, intent.level());
        assertEquals(shot.expires, intent.expiresAtNanos());
        assertEquals(shot.start, intent.startTick());
    }

    private static MobSession opened(EngineFixtures.Sessions sessions, int mob, TacticalProfile profile,
                                     ControlLevel level) {
        var session = sessions.registry.open(EngineFixtures.id(mob), profile);
        prepare(session, level);
        sessions.board.publish(session.key(), session.intent().orElseThrow().toView());
        return session;
    }

    private static void prepare(MobSession session, ControlLevel level) {
        session.adoptIntent(new TacticalIntent(session.key(), KEEP, Movement.KEEP_DISTANCE, Optional.of(STRIKE), 0.9,
                List.of(), Map.of(), level, 0, Long.MAX_VALUE / 2, false));
        session.recordTarget(EngineFixtures.player(1), 7);
        session.scheduleReady(TICK);
        session.candidates(CandidateSet.of(1, 1, List.of(
                new CandidateIntent(KEEP, Movement.KEEP_DISTANCE, Optional.of(STRIKE), 400, 0)), Map.of(),
                List.of(KEEP)));
    }

    private static Shot snapshot(MobSession session) {
        var intent = session.intent().orElseThrow();
        return new Shot(intent.tactic(), intent.level(), intent.expiresAtNanos(), intent.startTick(),
                session.candidates().orElseThrow());
    }

    private static List<String> castsOf(Gate gate, MobSession session) {
        var mark = session.key().entityId().getLeastSignificantBits() + ":";
        return gate.casts.stream().filter(cast -> cast.startsWith(mark)).toList();
    }

    private static String mark(MobSession session, String choice) {
        return session.key().entityId().getLeastSignificantBits() + ":" + choice;
    }

    private static TacticalProfile profile() {
        var timing = new DecisionTiming(40, 20, 100, 0.5, 0.2, 0).withCasting(CastMode.ANY_READY, 0)
                .withLayer(TickLayerMode.FULL, ReactionScope.ALL, ReactionBands.DEFAULT_AHEAD, 0,
                        DecisionCadence.STANDARD, 6, 40);
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(STRIKE, choice(STRIKE, SkillRole.DAMAGE, DamageTier.MEDIUM));
        choices.put(FINISH, choice(FINISH, SkillRole.FINISHER, DamageTier.HIGH));
        return new TacticalProfile("guardian_ai", "Guardian", "Defeat the target",
                Map.of(KEEP, new Tactic(KEEP, Movement.KEEP_DISTANCE, Optional.of(STRIKE), Optional.empty(), 40)),
                choices, List.of(), MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6),
                new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing);
    }

    private static SkillChoice choice(ChoiceId id, SkillRole role, DamageTier damage) {
        var purpose = new SkillPurpose(role, Set.of(role), Delivery.MELEE, OptionalDouble.of(2.5),
                OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, damage, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
        return SkillChoice.typed(id, "TPV_" + id.value(), Set.of(), true, 0, purpose);
    }

    private static ExecutionSteps steps(Spy spy) {
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
                spy.castIfReady(session, tick, nowNanos);
            }
        };
    }

    private static CombatReader reader() {
        var signals = TickSignals.pack((1L << TickSignals.VALID) | (1L << TickSignals.HAS_TARGET), 28, 0, 0, 0, 100,
                20);
        return new CombatReader() {
            @Override
            public Optional<akyrion.thepredict.domain.CombatFacts> capture(EncounterId encounter, List<MobKey> members,
                                                                          CaptureContext context) {
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
            public Optional<ThreatTableState> threatTable(MobKey mob) {
                return Optional.empty();
            }

            @Override
            public long tickSignals(MobKey mob, UUID expectedTarget) {
                return signals;
            }
        };
    }

    private record Shot(TacticId tactic, ControlLevel level, long expires, long start, CandidateSet candidates) {
    }

    private static final class Spy extends IntentControl {
        int applyCalls;
        int localCalls;
        int baseCalls;

        Spy(EngineFixtures.Sessions sessions, DecisionMetrics metrics, Gate gate) {
            super(sessions.board, gate, metrics, new ExecutionLog(line -> {
            }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                    DecisionTap.NONE);
        }

        @Override
        void apply(MobSession session, TacticalIntent intent, long tick) {
            applyCalls++;
            super.apply(session, intent, tick);
        }

        @Override
        void applyLocal(MobSession session, TacticalIntent intent, Optional<FallbackReason> reason, long tick) {
            localCalls++;
            super.applyLocal(session, intent, reason, tick);
        }

        @Override
        void toBase(MobSession session, FallbackReason reason, long tick) {
            baseCalls++;
            super.toBase(session, reason, tick);
        }
    }

    private static final class Gate implements CombatActions {
        final List<String> casts = new ArrayList<>();

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            casts.add(mob.entityId().getLeastSignificantBits() + ":" + choice.id().value());
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
        public void addThreat(MobKey mob, UUID playerId, double amount) {
        }
    }
}
