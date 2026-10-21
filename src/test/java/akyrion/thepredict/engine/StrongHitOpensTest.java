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
 * The strong melee hit opens the exchange (segunda ronda de efectividad, {@link StrongerFirst#opens}): out of melee, a
 * damaging gap-closer waits while a stronger melee hit is ready, so the first hit of the contact is the strong one and
 * the gap-closer follows it; in melee, with the strong hit spent or no stronger, it goes as before; and it never waits
 * more than {@value StrongerFirst#MAX_WAIT_TICKS} ticks in a row. The leap is the choice of the intent, as in the
 * {@code leap_in} tactic of the bench; the target is an entity.
 */
class StrongHitOpensTest {
    private static final TacticId LEAP_IN = new TacticId("leap_in");
    private static final ChoiceId LEAP = new ChoiceId("leap");
    private static final ChoiceId STRIKE = new ChoiceId("strike");

    @Test
    void outOfMeleeTheLeapWaitsAndTheStrikeOpensTheContact() {
        var bench = new Bench(DamageTier.HIGH, DamageTier.MEDIUM, DistanceBand.RANGED);
        bench.run(0, 6);
        assertEquals(List.of(), bench.actions.casts, "the strike is ready but out of reach: the leap waits");
        bench.contact();
        bench.run(6, 40);
        assertEquals(List.of("strike@6", "leap@14"), bench.actions.casts,
                "the strike opens at the contact; the leap lands at 24, after the window the strike opens at 14");
    }

    @Test
    void theLeapWaitsTwentyTicksAtMost() {
        var bench = new Bench(DamageTier.HIGH, DamageTier.MEDIUM, DistanceBand.RANGED);
        bench.run(0, 40);
        assertEquals(List.of("leap@" + StrongerFirst.MAX_WAIT_TICKS), bench.actions.casts,
                "a strike that never comes into reach holds the leap two windows, not forever");
    }

    @Test
    void aStrikeCoolingDownOrNoStrongerDoesNotHoldTheLeap() {
        var cooling = new Bench(DamageTier.HIGH, DamageTier.MEDIUM, DistanceBand.RANGED);
        cooling.actions.readyAt.put(STRIKE.value(), 50L);
        cooling.run(0, 2);
        assertEquals(List.of("leap@0"), cooling.actions.casts, "the leap covers the recharge of the strike");
        var equal = new Bench(DamageTier.MEDIUM, DamageTier.MEDIUM, DistanceBand.RANGED);
        equal.run(0, 2);
        assertEquals(List.of("leap@0"), equal.actions.casts, "a strike no stronger than the leap does not open");
    }

    @Test
    void inMeleeTheLeapIsNotHeldForTheStrike() {
        var bench = new Bench(DamageTier.HIGH, DamageTier.MEDIUM, DistanceBand.MELEE);
        bench.capture(DistanceBand.MELEE, Set.of(STRIKE));
        bench.actions.outOfReach.add(STRIKE.value());
        bench.run(0, 2);
        assertEquals(List.of("leap@0"), bench.actions.casts,
                "the strike is ready but cannot reach: in melee the leap is damage, as E-2 lets it fire");
    }

    private static SkillChoice leap(DamageTier damage) {
        var purpose = new SkillPurpose(SkillRole.GAP_CLOSE, Set.of(SkillRole.GAP_CLOSE, SkillRole.DAMAGE),
                Delivery.HITSCAN, OptionalDouble.of(8.0), OptionalDouble.empty(), 10, CooldownClass.UNKNOWN, damage,
                Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        return SkillChoice.typed(LEAP, "TPV_" + LEAP.value(), Set.of(), true, 0, purpose);
    }

    private static SkillChoice strike(DamageTier damage) {
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 8, CooldownClass.UNKNOWN, damage, Set.of(), Set.of(),
                false, PurposeOrigin.DECLARED);
        return SkillChoice.typed(STRIKE, "TPV_" + STRIKE.value(), Set.of(), true, 0, purpose);
    }

    /** One mob of {@code ANY_READY} whose intent leaps in, with a strike that is out of reach until the contact. */
    private static final class Bench {
        final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
        final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
        final Clocked actions = new Clocked();
        final IntentControl control;
        final MobSession session;

        Bench(DamageTier strike, DamageTier leap, DistanceBand band) {
            control = new IntentControl(sessions.board, actions, metrics, new ExecutionLog(line -> {
            }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                    DecisionTap.NONE);
            var timing = new DecisionTiming(20, 20, 100, 0.5, 0.2, 1, QuestionDetail.DEFAULT, false,
                    CastMode.ANY_READY, 0, UntypedCastMode.TACTIC_ONLY);
            var tactics = new LinkedHashMap<TacticId, Tactic>();
            tactics.put(LEAP_IN, new Tactic(LEAP_IN, Movement.APPROACH, Optional.of(LEAP), Optional.empty(), 55));
            var declared = new LinkedHashMap<ChoiceId, SkillChoice>();
            declared.put(STRIKE, strike(strike));
            declared.put(LEAP, leap(leap));
            var profile = new TacticalProfile("strong_opens", "Duelist", "Defeat the target", tactics, declared,
                    List.of(), MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6),
                    new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), timing);
            session = sessions.registry.open(EngineFixtures.id(1), profile);
            session.recordTarget(EngineFixtures.player(1), band == DistanceBand.MELEE ? 2.0 : 6.0);
            session.adoptIntent(new TacticalIntent(session.key(), LEAP_IN, Movement.APPROACH, Optional.of(LEAP), 0.9,
                    List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE / 2, false));
            capture(band);
            session.playerTarget(false);
            session.tickSignals(TickSignals.pack(TickSignals.PRESENT | (1L << TickSignals.HAS_TARGET), 4, 0, 0, 0,
                    100, 100));
            if (band != DistanceBand.MELEE) {
                actions.outOfReach.add(STRIKE.value());
            }
        }

        /** The target comes into melee: the strike reaches it. */
        void contact() {
            capture(DistanceBand.MELEE, Set.of());
            actions.outOfReach.clear();
        }

        private void capture(DistanceBand band) {
            capture(band, band == DistanceBand.MELEE ? Set.of() : Set.of(STRIKE));
        }

        void capture(DistanceBand band, Set<ChoiceId> outOfReach) {
            var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.ENTITY, 1), band, Trend.CLOSING,
                    true, true, false, false, false);
            session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(),
                    Set.of(LEAP_IN), Set.of(), outOfReach, Map.of(), Set.of(LEAP_IN)));
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

    /** Combat actions on a tick clock: a cast starts a recharge of 120 ticks; a skill out of reach is not admitted. */
    private static final class Clocked implements CombatActions {
        final List<String> casts = new ArrayList<>();
        final Map<String, Long> readyAt = new HashMap<>();
        final Set<String> outOfReach = new HashSet<>();
        long now;

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            var id = choice.id().value();
            if (outOfReach.contains(id) || readyAt.getOrDefault(id, 0L) > now) {
                return ExecutionOutcome.NOT_ELIGIBLE;
            }
            casts.add(id + "@" + now);
            readyAt.put(id, now + 120);
            return ExecutionOutcome.CAST;
        }

        @Override
        public boolean cheaplyAdmitted(MobKey mob, SkillChoice choice, Movement movement) {
            return !outOfReach.contains(choice.id().value());
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
