package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.CoordinationPolicy;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.ReactionBand;
import akyrion.thepredict.domain.ReactionBands;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
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

/**
 * Applying an answer does not cast (EFO-04, M19.4): the choice of Jev goes through the tick layer of the same tick,
 * so a band that M20.9 puts ahead of it casts first.
 */
class IntentCoordinationAheadTest {
    private static final ChoiceId STRIKE = new ChoiceId("strike");
    private static final ChoiceId SUSTAIN = new ChoiceId("sustain");
    private static final TacticId PRESS = new TacticId("press");
    private static final long TICK = 5;

    @Test
    void theSurvivalBandAheadCastsBeforeTheChoiceOfAnAnswerAppliedOnTheSameTick() {
        assertEquals(List.of(SUSTAIN.value()), castsOnTheAnswerTick(ReactionBands.DEFAULT_AHEAD));
    }

    @Test
    void withoutABandAheadTheChoiceOfTheAnswerCastsOnTheSameTick() {
        assertEquals(List.of(STRIKE.value()), castsOnTheAnswerTick(Set.of(ReactionBand.OPPORTUNITY)));
    }

    /** The answer is applied in the results step, then the tick layer of the same tick runs, as the coordinator does. */
    private static List<String> castsOnTheAnswerTick(Set<ReactionBand> ahead) {
        var gate = new Gate();
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var control = new IntentControl(sessions.board, gate, metrics, new ExecutionLog(line -> {
        }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                DecisionTap.NONE);
        var profile = profile(ahead);
        var session = sessions.registry.open(EngineFixtures.id(1), profile);
        session.recordTarget(EngineFixtures.player(1), 2);
        var encounter = new Encounter(new EncounterId(1), new UUID(0, 1), 0);
        encounter.addMember(session);
        var answer = new TacticalIntent(session.key(), PRESS, Movement.APPROACH, Optional.of(STRIKE), 0.9, List.of(),
                Map.of(), ControlLevel.JEV, TICK, Long.MAX_VALUE / 2, false);
        var candidates = List.of(new CoordinationPolicy.Candidate(answer, Optional.empty(), Set.of(PRESS), profile));
        coordination(sessions, control).apply(encounter, candidates, List.of(session), TICK, 0);
        var signals = TickSignals.pack(flag(TickSignals.VALID) | flag(TickSignals.HAS_TARGET), 8, 0, 0, 0, 20, 100);
        TickLayer.visit(List.of(session), 8, 0, TICK, 1_000, sessions.board, reader(signals), steps(control), metrics,
                found -> {
                }, 600, 4, 0, 0, () -> 0L, found -> {
                });
        return gate.casts;
    }

    private static IntentCoordination coordination(EngineFixtures.Sessions sessions, IntentControl control) {
        var budget = new RequestBudget(ExecutionFixture.settings(2000));
        var tracker = new ServiceStatusTracker(new ServiceCheck(ServiceState.READY, "listed"), 1_000_000L,
                () -> 0.5, line -> {
                });
        var fallback = new FallbackStep(tracker, sessions.encounters, budget, control,
                new ExecutionSettings(1_000_000_000L, 3, ExecutionFixture.MODEL, false));
        return new IntentCoordination(control, fallback);
    }

    private static TacticalProfile profile(Set<ReactionBand> ahead) {
        var timing = new DecisionTiming(40, 20, 100, 0.5, 0.2, 0).withCasting(CastMode.ANY_READY, 0)
                .withLayer(TickLayerMode.REACTIONS, ReactionScope.ALL, ahead, 0, DecisionCadence.STANDARD, 6, 40);
        var declared = new LinkedHashMap<ChoiceId, SkillChoice>();
        declared.put(STRIKE, choice(STRIKE, SkillRole.DAMAGE, DamageTier.MEDIUM));
        declared.put(SUSTAIN, choice(SUSTAIN, SkillRole.SUSTAIN, DamageTier.NONE));
        return new TacticalProfile("guardian_ai", "Guardian", "Defeat the target",
                Map.of(PRESS, new Tactic(PRESS, Movement.APPROACH, Optional.of(STRIKE), Optional.empty(), 50)),
                declared, List.of(), MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6),
                new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing);
    }

    private static SkillChoice choice(ChoiceId id, SkillRole role, DamageTier damage) {
        var purpose = new SkillPurpose(role, Set.of(role), Delivery.MELEE, OptionalDouble.of(2.5), OptionalDouble.empty(),
                0, CooldownClass.UNKNOWN, damage, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
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
