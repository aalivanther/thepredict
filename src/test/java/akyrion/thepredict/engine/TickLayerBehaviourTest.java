package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.CastPreference;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.Reaction;
import akyrion.thepredict.domain.ReactionBand;
import akyrion.thepredict.domain.ReactionBands;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.Trend;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Preference lifetime, probe vision and the evade backstep (M20.4, M20.5). */
class TickLayerBehaviourTest {
    private static final ChoiceId STRIKE = new ChoiceId("strike");
    private static final ChoiceId FINISH = new ChoiceId("finish");
    private static final ChoiceId ESCAPE = new ChoiceId("escape");
    private static final TacticId PRESS = new TacticId("press");
    private static final TacticId KEEP = new TacticId("keep");

    @Test
    void theFourPreferencesStayPositiveOnTheArmingTick() {
        for (var reaction : List.of(Reaction.PUNISH_CONSUMING, Reaction.PUNISH_RECHARGE, Reaction.RESPECT_SHIELD,
                Reaction.FINISH)) {
            assertTrue(reaction.preferenceTicks() > 0, reaction.name());
        }
    }

    /**
     * The order of the bands alone: the finisher lands ten ticks after the strike, so the own window of E-1
     * ({@code StrongerFirst}) does not order the two.
     */
    @Test
    void finishMovesAheadOfTheIntentChoiceOnlyWhenItsBandIsAhead() {
        assertEquals(List.of(FINISH.value()), fire(ReactionBands.DEFAULT_AHEAD));
        assertEquals(List.of(STRIKE.value()), fire(Set.of(ReactionBand.SURVIVAL)));
    }

    @Test
    void dodgeUsesTheLastProbeAndEvadeBackstepsOnlyWithoutAReadyEscape() {
        assertEquals(MicroBehaviour.NATIVE, micro(shot(false), false, Movement.HOLD, TickSignals.TARGET_SHOT_READY));
        assertEquals(MicroBehaviour.SIDESTEP, micro(shot(true), false, Movement.HOLD, TickSignals.TARGET_SHOT_READY));
        assertEquals(MicroBehaviour.SIDESTEP, micro(false, false, Movement.HOLD, TickSignals.INCOMING_PROJECTILE));
        assertEquals(MicroBehaviour.BACKSTEP, micro(false, false, Movement.HOLD, -1));
        assertEquals(MicroBehaviour.NATIVE, micro(false, true, Movement.HOLD, -1));
    }

    @Test
    void theLastDegradationStepStillSteersTheSessionsItVisits() {
        assertEquals(MicroBehaviour.SIDESTEP, micro(false, false, Movement.HOLD, TickSignals.INCOMING_PROJECTILE, 3),
                "step 3 freezes only the sessions the cap leaves out (M20.3)");
        assertEquals(MicroBehaviour.BACKSTEP, micro(false, false, Movement.HOLD, -1, 3));
    }

    @Test
    void sustainMovesAheadOnlyWhileItsSurvivalBandIsAhead() {
        assertEquals(List.of("sustain"), sustain(ReactionBands.DEFAULT_AHEAD));
        assertEquals(List.of(STRIKE.value()), sustain(Set.of(ReactionBand.OPPORTUNITY)));
    }

    private static List<String> fire(Set<ReactionBand> ahead) {
        var gate = new Gate();
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var control = new IntentControl(sessions.board, gate, metrics, new ExecutionLog(line -> {
        }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                DecisionTap.NONE);
        var timing = timing(TickLayerMode.REACTIONS, ahead);
        var session = sessions.registry.open(EngineFixtures.id(1), profile(timing, PRESS, Movement.APPROACH, STRIKE,
                choice(STRIKE, SkillRole.DAMAGE, DamageTier.MEDIUM),
                choice(FINISH, SkillRole.FINISHER, DamageTier.HIGH, OwnImpacts.WINDOW_TICKS)));
        adopt(session, PRESS, Movement.APPROACH, STRIKE);
        sessions.board.publish(session.key(), session.intent().orElseThrow().toView());
        session.recordTarget(EngineFixtures.player(1), 10);
        session.scheduleReady(5);
        var signals = TickSignals.pack(flag(TickSignals.VALID) | flag(TickSignals.HAS_TARGET), 40, 0, 0, 0, 100, 20);
        TickLayer.visit(List.of(session), 8, 0, 5, 1_000, sessions.board, reader(signals), steps(control), metrics,
                found -> {
                }, 600, 4, 0, 0, () -> 0L, found -> {
                });
        assertTrue(session.slotUntil(ReactionBand.OPPORTUNITY) > 5);
        assertEquals(5 + Reaction.FINISH.preferenceTicks(), session.slotUntil(ReactionBand.OPPORTUNITY));
        return gate.casts;
    }

    private static List<String> sustain(Set<ReactionBand> ahead) {
        var gate = new Gate();
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var control = new IntentControl(sessions.board, gate, metrics, new ExecutionLog(line -> {
        }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                DecisionTap.NONE);
        var sustain = new ChoiceId("sustain");
        var session = sessions.registry.open(EngineFixtures.id(1), profile(timing(TickLayerMode.REACTIONS, ahead), PRESS,
                Movement.APPROACH, STRIKE, choice(STRIKE, SkillRole.DAMAGE, DamageTier.MEDIUM),
                choice(sustain, SkillRole.SUSTAIN, DamageTier.NONE)));
        adopt(session, PRESS, Movement.APPROACH, STRIKE);
        sessions.board.publish(session.key(), session.intent().orElseThrow().toView());
        session.recordTarget(EngineFixtures.player(1), 2);
        session.scheduleReady(5);
        var signals = TickSignals.pack(flag(TickSignals.VALID) | flag(TickSignals.HAS_TARGET), 8, 0, 0, 0, 20, 100);
        TickLayer.visit(List.of(session), 8, 0, 5, 1_000, sessions.board, reader(signals), steps(control), metrics,
                found -> {
                }, 600, 4, 0, 0, () -> 0L, found -> {
                });
        return gate.casts;
    }

    private static MicroBehaviour micro(boolean seen, boolean escapeReady, Movement movement, int bit) {
        return micro(seen, escapeReady, movement, bit, 0);
    }

    /**
     * The micro after a visit at degradation {@code step}. From step 2 on a hot session reads its detail every other
     * tick, so the visit runs on ticks 3 and 4 to meet one of them.
     */
    private static MicroBehaviour micro(boolean seen, boolean escapeReady, Movement movement, int bit, int step) {
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var evade = bit < 0;
        var health = evade ? 20 : 100;
        var gap = evade ? 8 : 16;
        var flags = flag(TickSignals.VALID) | flag(TickSignals.HAS_TARGET);
        if (bit >= 0) {
            flags |= flag(bit);
        }
        var signals = TickSignals.pack(flags, gap, 0, 0, 0, health, 100);
        var tactic = movement == Movement.KEEP_DISTANCE ? KEEP : PRESS;
        var timing = timing(TickLayerMode.FULL, ReactionBands.DEFAULT_AHEAD);
        var choices = new ArrayList<SkillChoice>();
        choices.add(choice(STRIKE, SkillRole.DAMAGE, DamageTier.MEDIUM));
        choices.add(choice(ESCAPE, SkillRole.ESCAPE, DamageTier.NONE));
        var session = sessions.registry.open(EngineFixtures.id(1),
                profile(timing, tactic, movement, STRIKE, choices.toArray(SkillChoice[]::new)));
        adopt(session, tactic, movement, STRIKE);
        sessions.board.publish(session.key(), session.intent().orElseThrow().toView());
        session.recordTarget(EngineFixtures.player(1), gap / 4.0);
        session.scheduleReady(3);
        if (seen) {
            session.facts(seenFacts(session.key()));
        }
        var steps = escape(escapeReady);
        for (var tick = 3; tick <= (step >= 2 ? 4 : 3); tick++) {
            TickLayer.visit(List.of(session), 8, 0, tick, 1_000, sessions.board, reader(signals), steps, metrics,
                    found -> {
                    }, 600, 4, step, 0, () -> 0L, found -> {
                    });
        }
        return session.micro();
    }

    /** Steps whose escape choice is ready or not. */
    private static ExecutionSteps escape(boolean escapeReady) {
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
            public boolean choiceReady(MobSession found, SkillChoice choice, long tick) {
                return escapeReady;
            }
        };
    }

    private static boolean shot(boolean seen) {
        return seen;
    }

    private static MobFacts seenFacts(MobKey key) {
        var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.PLAYER, 1),
                akyrion.thepredict.domain.DistanceBand.RANGED, Trend.STEADY, true, true, true, false, false);
        return new MobFacts(key, HealthBand.HEALTHY, Optional.of(relation), Set.of(), Set.of(PRESS), Set.of(),
                Set.of());
    }

    private static void adopt(MobSession session, TacticId tactic, Movement movement, ChoiceId choice) {
        session.adoptIntent(new TacticalIntent(session.key(), tactic, movement, Optional.of(choice), 0.9, List.of(),
                Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE / 2, false));
    }

    private static DecisionTiming timing(TickLayerMode mode, Set<ReactionBand> ahead) {
        return new DecisionTiming(40, 20, 100, 0.5, 0.2, 0).withCasting(CastMode.ANY_READY, 0)
                .withLayer(mode, ReactionScope.ALL, ahead, 0, DecisionCadence.STANDARD, 6, 40);
    }

    private static TacticalProfile profile(DecisionTiming timing, TacticId tactic, Movement movement, ChoiceId lead,
                                           SkillChoice... choices) {
        var declared = new LinkedHashMap<ChoiceId, SkillChoice>();
        for (var choice : choices) {
            declared.put(choice.id(), choice);
        }
        return new TacticalProfile("guardian_ai", "Guardian", "Defeat the target",
                Map.of(tactic, new Tactic(tactic, movement, Optional.of(lead), Optional.empty(), 50)), declared,
                List.of(), MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6),
                new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing);
    }

    private static SkillChoice choice(ChoiceId id, SkillRole role, DamageTier damage) {
        return choice(id, role, damage, 0);
    }

    private static SkillChoice choice(ChoiceId id, SkillRole role, DamageTier damage, int windUp) {
        var purpose = new SkillPurpose(role, Set.of(role), Delivery.MELEE, OptionalDouble.of(2.5), OptionalDouble.empty(),
                windUp, CooldownClass.UNKNOWN, damage, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        return SkillChoice.typed(id, "TPV_" + id.value(), Set.of(), true, 0, purpose);
    }

    private static long flag(int bit) {
        return 1L << bit;
    }

    private static ExecutionSteps steps(IntentControl control) {
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
                control.castIfReady(session, tick, nowNanos);
            }
        };
    }

    private static CombatReader reader(long signals) {
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

    private static final class Gate implements CombatActions {
        final List<String> casts = new ArrayList<>();

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            casts.add(choice.id().value());
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
