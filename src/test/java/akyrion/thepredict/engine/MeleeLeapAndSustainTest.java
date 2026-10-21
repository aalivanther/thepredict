package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.UntypedCastMode;
import akyrion.thepredict.domain.UseCondition;
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
 * Every useful skill fires as soon as it is ready (fase de efectividad E-2): a leap that also deals damage fires in
 * melee whatever the intent holds, except while it retreats, and a heal or a guard whose {@code BestWhen} holds goes
 * before the damage of the same tick. The choice of the intent cools down, so only candidates are tried.
 */
class MeleeLeapAndSustainTest {
    private static final TacticId GUARD_UP = new TacticId("guard_up");
    private static final ChoiceId WARD = new ChoiceId("ward");
    private static final ChoiceId LEAP = new ChoiceId("leap");
    private static final ChoiceId STRIKE = new ChoiceId("strike");
    private static final ChoiceId HEAL = new ChoiceId("heal");

    @Test
    void aDamagingLeapFiresInMeleeWhileTheIntentHolds() {
        var bench = new Bench(Movement.HOLD, DistanceBand.MELEE, leap());
        bench.fire(0);
        assertEquals(List.of(LEAP.value()), bench.actions.casts);
    }

    @Test
    void awayFromMeleeOrWhileRetreatingTheLeapStillWaits() {
        var ranged = new Bench(Movement.HOLD, DistanceBand.RANGED, leap());
        ranged.fire(0);
        assertEquals(List.of(), ranged.actions.casts, "a hold away from melee keeps the old rows");
        var retreat = new Bench(Movement.RETREAT, DistanceBand.MELEE, leap());
        retreat.fire(0);
        assertEquals(List.of(), retreat.actions.casts);
    }

    @Test
    void aHealUnderItsConditionGoesBeforeAStrikeOfTheSameTick() {
        var low = new Bench(Movement.HOLD, DistanceBand.MELEE, strike(), heal());
        low.session.noteConditions(Set.of(UseCondition.SELF_LOW_HEALTH, UseCondition.TARGET_IN_MELEE));
        low.fire(0);
        assertEquals(List.of(HEAL.value()), low.actions.casts);
        var healthy = new Bench(Movement.HOLD, DistanceBand.MELEE, strike(), heal());
        healthy.session.noteConditions(Set.of(UseCondition.TARGET_IN_MELEE));
        healthy.fire(0);
        assertEquals(List.of(STRIKE.value()), healthy.actions.casts, "without its condition the heal keeps its place");
    }

    private static SkillChoice leap() {
        return typed(LEAP, Set.of(SkillRole.GAP_CLOSE, SkillRole.DAMAGE), Delivery.HITSCAN, DamageTier.LOW, Set.of(),
                true);
    }

    private static SkillChoice strike() {
        return typed(STRIKE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, DamageTier.MEDIUM,
                Set.of(UseCondition.TARGET_IN_MELEE), true);
    }

    private static SkillChoice heal() {
        return typed(HEAL, Set.of(SkillRole.SUSTAIN), Delivery.SELF, DamageTier.NONE,
                Set.of(UseCondition.SELF_LOW_HEALTH), false);
    }

    private static SkillChoice typed(ChoiceId id, Set<SkillRole> roles, Delivery delivery, DamageTier damage,
                                     Set<UseCondition> bestWhen, boolean target) {
        var primary = roles.contains(SkillRole.GAP_CLOSE) ? SkillRole.GAP_CLOSE : roles.iterator().next();
        var reach = delivery == Delivery.SELF ? OptionalDouble.empty() : OptionalDouble.of(8.0);
        var purpose = new SkillPurpose(primary, roles, delivery, reach, OptionalDouble.empty(), 0,
                CooldownClass.UNKNOWN, damage, bestWhen, Set.of(), false, PurposeOrigin.DECLARED);
        return SkillChoice.typed(id, "TPV_" + id.value(), Set.of(), target, 0, purpose);
    }

    /** One mob of {@code ANY_READY} whose intent choice, a ward on itself, cools down. */
    private static final class Bench {
        final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
        final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
        final Gate actions = new Gate();
        final IntentControl control;
        final MobSession session;

        Bench(Movement movement, DistanceBand band, SkillChoice... choices) {
            control = new IntentControl(sessions.board, actions, metrics, new ExecutionLog(line -> {
            }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                    DecisionTap.NONE);
            var timing = new DecisionTiming(20, 20, 100, 0.5, 0.2, 1, QuestionDetail.DEFAULT, false,
                    CastMode.ANY_READY, 0, UntypedCastMode.TACTIC_ONLY);
            var tactics = new LinkedHashMap<TacticId, Tactic>();
            tactics.put(GUARD_UP, new Tactic(GUARD_UP, movement, Optional.of(WARD), Optional.empty(), 50));
            var declared = new LinkedHashMap<ChoiceId, SkillChoice>();
            declared.put(WARD, typed(WARD, Set.of(SkillRole.DEFENSE), Delivery.SELF, DamageTier.NONE, Set.of(), false));
            for (var choice : choices) {
                declared.put(choice.id(), choice);
            }
            var profile = new TacticalProfile("melee_leap", "Duelist", "Defeat the target", tactics, declared,
                    List.of(), MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6),
                    new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing);
            session = sessions.registry.open(EngineFixtures.id(1), profile);
            session.recordTarget(EngineFixtures.player(1), band == DistanceBand.MELEE ? 2.0 : 6.0);
            session.adoptIntent(new TacticalIntent(session.key(), GUARD_UP, movement, Optional.of(WARD), 0.9,
                    List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE / 2, false));
            var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.ENTITY, 1), band, Trend.STEADY,
                    true, true, false, false, false);
            session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(),
                    Set.of(GUARD_UP), Set.of(), Set.of(), Map.of(), Set.of(GUARD_UP)));
            session.playerTarget(false);
            session.tickSignals(TickSignals.pack(TickSignals.PRESENT | (1L << TickSignals.HAS_TARGET), 4, 0, 0, 0,
                    100, 100));
            actions.cooling.add(WARD.value());
        }

        void fire(long tick) {
            session.scheduleReady(tick);
            control.castIfReady(session, tick, 0);
        }
    }

    /** Combat actions that cast every ready choice, except the ones cooling down. */
    private static final class Gate implements CombatActions {
        final List<String> casts = new ArrayList<>();
        final Set<String> cooling = new java.util.HashSet<>();

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            if (cooling.contains(choice.id().value())) {
                return ExecutionOutcome.NOT_ELIGIBLE;
            }
            casts.add(choice.id().value());
            return ExecutionOutcome.CAST;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            return !cooling.contains(choice.id().value());
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, UUID participantId, double amount) {
        }
    }
}
