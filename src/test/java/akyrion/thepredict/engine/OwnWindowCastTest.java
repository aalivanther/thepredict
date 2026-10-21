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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ready step of {@code ANY_READY} with the own window of invulnerability (fase de efectividad E-1): a damaging cast
 * waits until its hit takes nothing from the hits of the mob in flight, gives way to a stronger hit about to be ready
 * when that costs the stronger one more than it costs itself, and never waits for it more than
 * {@value StrongerFirst#MAX_WAIT_TICKS} ticks in a row. The target is an entity, so wind-ups may overlap (I-02).
 */
class OwnWindowCastTest {
    private static final TacticId PRESS = new TacticId("press_attack");
    private static final ChoiceId STRIKE = new ChoiceId("strike");
    private static final ChoiceId BOLT = new ChoiceId("bolt");

    @Test
    void aBoltDoesNotLandInsideTheWindowOfTheStrikeInFlight() {
        var bench = new Bench(choice(STRIKE, DamageTier.MEDIUM, 5), choice(BOLT, DamageTier.LOW, 10));
        bench.run(0, 8);
        assertEquals(List.of("strike@0", "bolt@5"), bench.actions.casts,
                "cast at 1 the bolt would land at 11, inside the window the strike opens at 5 until 15");
    }

    @Test
    void aWeakLongWindUpGivesWayToAStrongerHitAboutToBeReady() {
        var bench = new Bench(choice(STRIKE, DamageTier.MEDIUM, 5), choice(BOLT, DamageTier.LOW, 10));
        bench.actions.readyAt.put(STRIKE.value(), 3L);
        bench.run(0, 10);
        assertEquals(List.of("strike@3", "bolt@8"), bench.actions.casts,
                "a bolt at 0 would land at 10 and push the strike ready at 3 back to 15");
    }

    @Test
    void aWeakHitGoesFirstWhenTheStrongerOneLosesLessByWaiting() {
        var bench = new Bench(choice(STRIKE, DamageTier.LOW, 5), choice(BOLT, DamageTier.MEDIUM, 10));
        bench.actions.readyAt.put(BOLT.value(), 3L);
        bench.run(0, 6);
        assertEquals(List.of("strike@0", "bolt@5"), bench.actions.casts,
                "the bolt waits 2 ticks for the strike; the strike would wait 18 for the bolt");
    }

    @Test
    void aStrongerReadyCandidateGoesBeforeTheWeakerIntentChoiceItWouldOverlap() {
        var bench = new Bench(choice(STRIKE, DamageTier.LOW, 5), choice(BOLT, DamageTier.HIGH, 5));
        bench.run(0, 12);
        assertEquals(List.of("bolt@0", "strike@10"), bench.actions.casts,
                "together the two would deal only the high hit; the low one lands after its window");
    }

    @Test
    void aNewTargetLetsTheBoltGoAtOnce() {
        var bench = new Bench(choice(STRIKE, DamageTier.MEDIUM, 5), choice(BOLT, DamageTier.LOW, 10));
        bench.run(0, 1);
        bench.session.recordTarget(EngineFixtures.player(2), 2.0);
        bench.run(1, 2);
        assertEquals(List.of("strike@0", "bolt@1"), bench.actions.casts,
                "the windows of the strike belong to the previous target");
    }

    @Test
    void aStrongerHitThatNeverGoesHoldsAWeakOneTwentyTicksAtMost() {
        var bench = new Bench(choice(STRIKE, DamageTier.MEDIUM, 5), choice(BOLT, DamageTier.LOW, 10));
        bench.actions.readyAt.put(STRIKE.value(), 3L);
        bench.actions.refused.add(STRIKE.value());
        bench.run(0, 30);
        assertEquals(List.of("bolt@" + StrongerFirst.MAX_WAIT_TICKS), bench.actions.casts);
    }

    @Test
    void theWeightedRuleComparesWhatEachHitWouldLoseByWaiting() {
        assertEquals(true, StrongerFirst.yields(1, 10, 2, 8), "bolt 10 against strike 8: 2 x 12 against 1 x 8");
        assertEquals(false, StrongerFirst.yields(1, 5, 2, 13), "strike 5 against bolt 13: 2 x 2 against 1 x 18");
        assertEquals(false, StrongerFirst.yields(1, 10, 3, 20), "ten ticks apart the two do not touch");
        assertEquals(true, StrongerFirst.yields(2, 10, 2, 9), "an equal hit that lands first goes first");
        assertEquals(false, StrongerFirst.yields(2, 9, 2, 10));
    }

    private static SkillChoice choice(ChoiceId id, DamageTier damage, int windUp) {
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), windUp, CooldownClass.UNKNOWN, damage, Set.of(),
                Set.of(), false, PurposeOrigin.DECLARED);
        return SkillChoice.typed(id, "TPV_" + id.value(), Set.of(), true, 0, purpose);
    }

    /** One mob of {@code ANY_READY} against an entity, whose skills recharge in 30 ticks unless told otherwise. */
    private static final class Bench {
        final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
        final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
        final Clocked actions = new Clocked();
        final IntentControl control;
        final MobSession session;

        Bench(SkillChoice... choices) {
            control = new IntentControl(sessions.board, actions, metrics, new ExecutionLog(line -> {
            }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                    DecisionTap.NONE);
            var timing = new DecisionTiming(20, 20, 100, 0.5, 0.2, 1, QuestionDetail.DEFAULT, false,
                    CastMode.ANY_READY, 0, UntypedCastMode.TACTIC_ONLY);
            var tactics = new LinkedHashMap<TacticId, Tactic>();
            tactics.put(PRESS, new Tactic(PRESS, Movement.APPROACH, Optional.of(STRIKE), Optional.empty(), 50));
            var declared = new LinkedHashMap<ChoiceId, SkillChoice>();
            for (var choice : choices) {
                declared.put(choice.id(), choice);
            }
            var profile = new TacticalProfile("own_window", "Duelist", "Defeat the target", tactics, declared,
                    List.of(), MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6),
                    new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing);
            session = sessions.registry.open(EngineFixtures.id(1), profile);
            session.recordTarget(EngineFixtures.player(1), 2.0);
            session.adoptIntent(new TacticalIntent(session.key(), PRESS, Movement.APPROACH, Optional.of(STRIKE), 0.9,
                    List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE / 2, false));
            var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.ENTITY, 1), DistanceBand.MELEE,
                    Trend.STEADY, true, true, false, false, false);
            session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(),
                    Set.of(PRESS), Set.of(), Set.of(), Map.of(), Set.of(PRESS)));
            session.playerTarget(false);
            session.tickSignals(TickSignals.pack(TickSignals.PRESENT | (1L << TickSignals.HAS_TARGET), 4, 0, 0, 0,
                    100, 100));
        }

        /** Visits the mob on every tick from {@code from} to {@code until}, exclusive, as the ready step does. */
        void run(long from, long until) {
            for (var tick = from; tick < until; tick++) {
                actions.now = tick;
                session.scheduleReady(tick);
                control.castIfReady(session, tick, 0);
            }
        }
    }

    /** Combat actions on a tick clock: a cast starts a recharge of 30 ticks, and a refused skill never casts. */
    private static final class Clocked implements CombatActions {
        final List<String> casts = new ArrayList<>();
        final Map<String, Long> readyAt = new HashMap<>();
        final Set<String> refused = new HashSet<>();
        long now;

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            var id = choice.id().value();
            if (refused.contains(id) || readyAt.getOrDefault(id, 0L) > now) {
                return ExecutionOutcome.NOT_ELIGIBLE;
            }
            casts.add(id + "@" + now);
            readyAt.put(id, now + 30);
            return ExecutionOutcome.CAST;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            return readyAt.getOrDefault(choice.id().value(), 0L) <= now;
        }

        @Override
        public OptionalInt ticksUntilReady(MobKey mob, SkillChoice choice) {
            return OptionalInt.of((int) Math.max(0, readyAt.getOrDefault(choice.id().value(), 0L) - now));
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, UUID participantId, double amount) {
        }
    }
}
