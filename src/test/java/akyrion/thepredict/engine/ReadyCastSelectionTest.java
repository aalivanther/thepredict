package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.ReadyCastOrder;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.UntypedCastMode;
import akyrion.thepredict.domain.UseCondition;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
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

/**
 * Order, direction and firing of {@code ANY_READY} (M19.5). The direction rows are the ones of
 * {@link ReadyCastOrder}; the engine applies them to every candidate except the choice of the intent.
 */
class ReadyCastSelectionTest {
    private static final TacticId PRESS = new TacticId("press_attack");
    private static final ChoiceId STRIKE = new ChoiceId("strike");
    private static final ChoiceId BOLT = new ChoiceId("bolt");
    private static final ChoiceId LEAP = new ChoiceId("leap");
    private static final ChoiceId FLEE = new ChoiceId("flee");
    private static final ChoiceId BARE = new ChoiceId("bare");
    private static final ChoiceId SWING = new ChoiceId("swing");

    @Test
    void directionFollowsTheReadyCastTable() {
        var settings = MovementSettings.DEFAULTS;
        var gap = role(SkillRole.GAP_CLOSE, DamageTier.LOW);
        var escape = role(SkillRole.ESCAPE, DamageTier.NONE);
        var both = new SkillPurpose(SkillRole.GAP_CLOSE, Set.of(SkillRole.GAP_CLOSE, SkillRole.ESCAPE), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.NONE, Set.of(),
                Set.of(), false, PurposeOrigin.DECLARED);
        var plain = role(SkillRole.DAMAGE, DamageTier.LOW);
        assertTrue(ReadyCastOrder.directionAllows(gap, Movement.APPROACH, 1, settings));
        assertTrue(ReadyCastOrder.directionAllows(gap, Movement.FLANK, 1, settings));
        assertFalse(ReadyCastOrder.directionAllows(gap, Movement.RETREAT, 1, settings));
        assertFalse(ReadyCastOrder.directionAllows(gap, Movement.HOLD, 12, settings));
        assertFalse(ReadyCastOrder.directionAllows(gap, Movement.NONE, 12, settings));
        assertTrue(ReadyCastOrder.directionAllows(gap, Movement.KEEP_DISTANCE, 10, settings));
        assertFalse(ReadyCastOrder.directionAllows(gap, Movement.KEEP_DISTANCE, 9, settings));
        assertTrue(ReadyCastOrder.directionAllows(escape, Movement.RETREAT, 1, settings));
        assertFalse(ReadyCastOrder.directionAllows(escape, Movement.APPROACH, 1, settings));
        assertTrue(ReadyCastOrder.directionAllows(escape, Movement.KEEP_DISTANCE, 4, settings));
        assertFalse(ReadyCastOrder.directionAllows(escape, Movement.KEEP_DISTANCE, 5, settings));
        assertFalse(ReadyCastOrder.directionAllows(both, Movement.APPROACH, 12, settings));
        assertFalse(ReadyCastOrder.directionAllows(both, Movement.RETREAT, 1, settings));
        assertFalse(ReadyCastOrder.directionAllows(both, Movement.KEEP_DISTANCE, 4, settings));
        assertFalse(ReadyCastOrder.directionAllows(both, Movement.KEEP_DISTANCE, 12, settings));
        assertTrue(ReadyCastOrder.directionAllows(plain, Movement.HOLD, 1, settings));
    }

    @Test
    void anyReadyFiresAnotherSkillWhileTheIntentChoiceCoolsDownAndDoesNotConsumeIt() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0), bolt(DamageTier.LOW));
        bench.actions.cooling.add(STRIKE.value());
        bench.fire(0);
        assertEquals(List.of(BOLT.value()), bench.actions.casts);
        assertFalse(bench.session.intent().orElseThrow().skillConsumed());
        assertEquals(0, bench.session.intent().orElseThrow().startTick());
        assertEquals(1, bench.metrics.total(DecisionMetrics.ReadyCounter.READY_CASTS));
    }

    @Test
    void intentOnlyCastsOnlyItsOwnChoice() {
        var bench = bench(CastMode.INTENT_ONLY, UntypedCastMode.TACTIC_ONLY, strike(0), bolt(DamageTier.HIGH));
        bench.fire(0);
        assertEquals(List.of(STRIKE.value()), bench.actions.casts);
        assertTrue(bench.session.intent().orElseThrow().skillConsumed());
    }

    @Test
    void theIntentChoiceFiresFirstAndASecondStartWaitsForTheNextTick() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0),
                pastWindows(bolt(DamageTier.HIGH)));
        bench.fire(0);
        bench.fire(0);
        assertEquals(List.of(STRIKE.value()), bench.actions.casts);
        assertEquals(1, bench.session.nextReadyTick());
        var overlapping = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0), bolt(DamageTier.HIGH));
        overlapping.fire(0);
        assertEquals(List.of(BOLT.value()), overlapping.actions.casts,
                "a stronger hit the window of the intent choice would take goes first (E-1, also without signals)");
    }

    @Test
    void aWindUpBlocksTheNextCandidate() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(5),
                pastWindows(bolt(DamageTier.HIGH)));
        bench.fire(0);
        bench.fire(1);
        assertEquals(List.of(STRIKE.value()), bench.actions.casts);
        assertEquals(1, bench.metrics.total(DecisionMetrics.ReadyCounter.BUSY_BLOCKED));
        assertEquals(5, bench.session.nextReadyTick());
    }

    @Test
    void aWindUpAgainstAnEntityLetsTheNextSkillStartAsMythicMobsDoes() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(5),
                pastWindows(bolt(DamageTier.HIGH)));
        bench.target(ParticipantKind.ENTITY, false);
        bench.fire(0);
        bench.actions.cooling.add(STRIKE.value());
        bench.fire(1);
        assertEquals(List.of(STRIKE.value(), BOLT.value()), bench.actions.casts);
        assertEquals(0, bench.metrics.total(DecisionMetrics.ReadyCounter.BUSY_BLOCKED));
        assertEquals(5, bench.session.busyUntilTick());
    }

    @Test
    void aWindUpAgainstNoEntityStillBlocksTheNextSkill() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(5),
                pastWindows(bolt(DamageTier.HIGH)));
        bench.fire(0);
        bench.actions.cooling.add(STRIKE.value());
        bench.fire(1);
        assertEquals(List.of(STRIKE.value()), bench.actions.casts);
        assertEquals(1, bench.metrics.total(DecisionMetrics.ReadyCounter.BUSY_BLOCKED));
    }

    @Test
    void onlyAnEntityTargetOnBothReadingsAllowsTheOverlap() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(5));
        assertFalse(IntentControl.overlapAllowed(bench.session), "no target in the capture");
        bench.target(ParticipantKind.ENTITY, false);
        assertTrue(IntentControl.overlapAllowed(bench.session));
        bench.target(ParticipantKind.PLAYER, false);
        assertFalse(IntentControl.overlapAllowed(bench.session), "a player in the capture");
        bench.target(ParticipantKind.ENTITY, true);
        assertFalse(IntentControl.overlapAllowed(bench.session), "a player on this tick");
    }

    @Test
    void aConsumedIntentChoiceRearmsWithoutChangingTheStartTick() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0), bolt(DamageTier.LOW));
        bench.fire(0);
        bench.actions.casts.clear();
        bench.fire(5);
        assertEquals(List.of(), bench.actions.casts, "at 5 the strike would land in its own window and deal nothing");
        bench.fire(OwnImpacts.WINDOW_TICKS);
        assertEquals(List.of(STRIKE.value()), bench.actions.casts);
        assertEquals(0, bench.session.intent().orElseThrow().startTick());
        assertTrue(bench.session.intent().orElseThrow().skillConsumed());
        assertEquals(1, bench.session.choiceEpoch());
    }

    @Test
    void anIneligibleIntentChoiceDoesNotBlockOtherCandidatesOrSpin() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0), bolt(DamageTier.LOW));
        bench.session.facts(new MobFacts(bench.session.key(), HealthBand.HEALTHY, Optional.empty(), Set.of(), Set.of(),
                Set.of(), Set.of(), Map.of(), Set.of()));
        bench.fire(0);
        assertEquals(List.of(BOLT.value()), bench.actions.casts);
        var alone = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0));
        alone.session.facts(new MobFacts(alone.session.key(), HealthBand.HEALTHY, Optional.empty(), Set.of(), Set.of(),
                Set.of(), Set.of(), Map.of(), Set.of()));
        alone.fire(0);
        assertTrue(alone.actions.casts.isEmpty());
        assertEquals(2, alone.session.nextReadyTick());
    }

    @Test
    void untypedSkillsFireLastAndOnlyWhenTheProfileAllowsThem() {
        var held = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0), bolt(DamageTier.LOW), bare());
        held.actions.cooling.add(STRIKE.value());
        held.actions.script.add(ExecutionOutcome.NOT_ELIGIBLE);
        held.fire(0);
        assertEquals(List.of(BOLT.value()), held.actions.casts);
        var open = bench(CastMode.ANY_READY, UntypedCastMode.ANY_READY, strike(0), bolt(DamageTier.LOW), bare());
        open.actions.cooling.add(STRIKE.value());
        open.actions.script.add(ExecutionOutcome.NOT_ELIGIBLE);
        open.fire(0);
        assertEquals(List.of(BOLT.value(), BARE.value()), open.actions.casts);
    }

    @Test
    void aMythicRejectionHoldsThatCandidateAndTriesTheNext() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0), bolt(DamageTier.HIGH),
                leap(SkillRole.DAMAGE, DamageTier.LOW));
        bench.actions.cooling.add(STRIKE.value());
        bench.actions.script.add(ExecutionOutcome.REJECTED_BY_MYTHICMOBS);
        bench.fire(0);
        assertEquals(List.of(BOLT.value(), LEAP.value()), bench.actions.casts);
        assertTrue(bench.session.choiceHeld(1, 0));
        assertEquals(20, bench.session.choiceFreeTick(1));
    }

    @Test
    void oneMissedLineOfSightStopsTheTickAndACheapMissTriesTheNext() {
        var blind = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0),
                bolt(DamageTier.HIGH, Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT)),
                leap(SkillRole.DAMAGE, DamageTier.LOW));
        blind.actions.cooling.add(STRIKE.value());
        blind.actions.sight = false;
        blind.fire(0);
        assertTrue(blind.actions.casts.isEmpty());
        assertEquals(1, blind.actions.sightChecks);
        assertEquals(1, blind.session.nextReadyTick());
        var missed = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0), bolt(DamageTier.HIGH),
                leap(SkillRole.DAMAGE, DamageTier.LOW));
        missed.actions.cooling.add(STRIKE.value());
        missed.actions.blocked.add(BOLT.value());
        missed.fire(0);
        assertEquals(List.of(LEAP.value()), missed.actions.casts);
        assertEquals(0, missed.actions.sightChecks);
    }

    @Test
    void aSightFailureInsideTheCastDoesNotTryAnotherSkill() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0),
                bolt(DamageTier.HIGH, Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT)),
                leap(SkillRole.DAMAGE, DamageTier.LOW));
        bench.actions.cooling.add(STRIKE.value());
        bench.actions.script.add(ExecutionOutcome.NOT_ELIGIBLE);
        bench.fire(0);
        assertEquals(List.of(BOLT.value()), bench.actions.casts);
        assertEquals(1, bench.actions.sightChecks);
        assertEquals(1, bench.session.nextReadyTick());
    }

    @Test
    void retreatDoesNotFireAGapCloseAndKeepDistanceUsesTheBand() {
        var retreat = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0),
                leap(SkillRole.GAP_CLOSE, DamageTier.HIGH), flee());
        retreat.actions.cooling.add(STRIKE.value());
        retreat.movement(Movement.RETREAT);
        retreat.fire(0);
        assertEquals(List.of(FLEE.value()), retreat.actions.casts);
        var closing = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0),
                leap(SkillRole.GAP_CLOSE, DamageTier.LOW), flee());
        closing.actions.cooling.add(STRIKE.value());
        closing.movement(Movement.KEEP_DISTANCE);
        closing.session.recordTarget(EngineFixtures.player(1), 12);
        closing.fire(0);
        assertEquals(List.of(LEAP.value()), closing.actions.casts);
        var escaping = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0),
                leap(SkillRole.GAP_CLOSE, DamageTier.HIGH), flee());
        escaping.actions.cooling.add(STRIKE.value());
        escaping.movement(Movement.KEEP_DISTANCE);
        escaping.session.recordTarget(EngineFixtures.player(1), 3);
        escaping.fire(0);
        assertEquals(List.of(FLEE.value()), escaping.actions.casts);
    }

    @Test
    void aProbeRefreshesDistanceAndLeavesTheOtherConditions() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0),
                pastWindows(aimed(BOLT, UseCondition.TARGET_AT_RANGE, DamageTier.HIGH)),
                pastWindows(aimed(LEAP, UseCondition.TARGET_IN_MELEE, DamageTier.LOW)),
                pastWindows(aimed(FLEE, UseCondition.TARGET_SHIELDING, DamageTier.MEDIUM)));
        bench.actions.cooling.add(STRIKE.value());
        bench.session.noteConditions(Set.of(UseCondition.TARGET_AT_RANGE, UseCondition.TARGET_SHIELDING));
        bench.session.noteProbe(new MobProbe(bench.session.key(), UUID.randomUUID(), HealthBand.HEALTHY,
                Optional.of(EngineFixtures.player(1)), Optional.of(DistanceBand.MELEE)), 1);
        bench.actions.script.add(ExecutionOutcome.NOT_ELIGIBLE);
        bench.actions.script.add(ExecutionOutcome.NOT_ELIGIBLE);
        bench.fire(0);
        assertEquals(List.of(FLEE.value(), LEAP.value(), BOLT.value()), bench.actions.casts);
    }

    @Test
    void inRecoveryAnEscapeStartsBeforeTheBusyDeadlineWhileTheStrikeWaits() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0), flee());
        bench.movement(Movement.RETREAT);
        bench.session.stanceState(akyrion.thepredict.domain.StanceState.withAction(
                akyrion.thepredict.domain.StanceState.INITIAL,
                akyrion.thepredict.domain.ActionStance.RECOVERY.ordinal()));
        bench.session.busyUntil(10);
        bench.fire(1);
        assertEquals(List.of(FLEE.value()), bench.actions.casts,
                "E-3, decision 6 of M23: a mob that recovers can still evade, and only evade");
    }

    @Test
    void aRejectionOfTheIntentChoiceLetsAReadyCandidateCastOnTheSameTick() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0), bolt(DamageTier.LOW));
        bench.actions.script.add(ExecutionOutcome.REJECTED_BY_MYTHICMOBS);
        bench.fire(0);
        assertEquals(List.of(STRIKE.value(), BOLT.value()), bench.actions.casts,
                "a refusal that started no cast leaves the start of this tick to the next candidate");
        assertEquals(0, bench.session.lastCastStartTick());
    }

    @Test
    void aVanillaHitOutOfReachIsTriedAgainOnTheNextTick() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0));
        bench.actions.cooling.add(STRIKE.value());
        var intent = bench.session.intent().orElseThrow();
        bench.session.adoptIntent(new TacticalIntent(intent.mob(), intent.tactic(), intent.movement(),
                Optional.of(SWING), intent.confidence(), intent.alternatives(), intent.contingencies(), intent.level(),
                intent.startTick(), intent.expiresAtNanos(), false));
        bench.actions.script.add(ExecutionOutcome.NOT_ELIGIBLE);
        bench.fire(0);
        assertEquals(List.of(SWING.value()), bench.actions.casts);
        assertEquals(1, bench.session.nextReadyTick(),
                "a rival with the same rhythm would land the first hit of every exchange if the retry waited a cycle");
        bench.fire(1);
        assertEquals(List.of(SWING.value(), SWING.value()), bench.actions.casts);
    }

    @Test
    void aRejectionWithNothingElseReadyKeepsTheRetryItScheduled() {
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0));
        bench.actions.script.add(ExecutionOutcome.REJECTED_BY_MYTHICMOBS);
        bench.fire(0);
        assertEquals(List.of(STRIKE.value()), bench.actions.casts);
        assertEquals(FallbackStep.localIntentTicks(bench.session.profile().timing()), bench.session.nextReadyTick());
    }

    @Test
    void aTellThatOutlastsTheInvulnerabilityOpensWhileTheTargetIsStillProtected() {
        var heavy = SkillChoice.typed(STRIKE, "TPV_Strike", Set.of(), true, 0, role(SkillRole.DAMAGE,
                DamageTier.HIGH, 0));
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, heavy);
        bench.session.playerTarget(true);
        var shown = TellOpening.impactTicks(bench.session, heavy);
        assertTrue(shown > 10, "the minimum of a heavy tell against a player, " + shown);
        bench.session.tickSignals(akyrion.thepredict.domain.TickSignals.pack(
                akyrion.thepredict.domain.TickSignals.PRESENT | (1L << akyrion.thepredict.domain.TickSignals.HAS_TARGET),
                4, 0, 0, 10, 100, 100));
        bench.fire(0);
        assertTrue(bench.session.tellOpen(), "the hit lands after the whole tell, when the target is hittable again");
        assertEquals(shown, bench.session.telegraphTicks());
    }

    @Test
    void aCounterThatAsksForATellAnswerPrefersTheChoiceWhoseTellAsksForIt() {
        var bench = answers(akyrion.thepredict.domain.EnemyPattern.FAILS_ANSWER_BLOCK.bit());
        bench.fire(0);
        assertEquals(List.of(LEAP.value()), bench.actions.casts);
    }

    @Test
    void eachHeldPatternIsMatchedOnItsOwnAndNotThroughTheUnionOfTheirMasks() {
        var bench = answers(akyrion.thepredict.domain.EnemyPattern.FAILS_ANSWER_BLOCK.bit()
                | akyrion.thepredict.domain.EnemyPattern.WEAK_TO_AREA.bit());
        bench.fire(0);
        assertEquals(List.of(LEAP.value()), bench.actions.casts,
                "the melee choice that answers with a block fits the first counter although it is no area");
    }

    /** A bench whose intent choice cools down, a heavy bolt asks to dodge and a light leap asks to block. */
    private static Bench answers(int held) {
        var bolt = SkillChoice.typed(BOLT, "TPV_Bolt", Set.of(), true, 0,
                new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.PROJECTILE, OptionalDouble.of(2.5),
                        OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.HIGH, Set.of(), Set.of(), false,
                        PurposeOrigin.DECLARED));
        var leap = SkillChoice.typed(LEAP, "TPV_Leap", Set.of(), true, 0, role(SkillRole.DAMAGE, DamageTier.LOW));
        assertEquals(akyrion.thepredict.domain.TellAnswer.DODGE, akyrion.thepredict.domain.DangerRules.answer(bolt));
        assertEquals(akyrion.thepredict.domain.TellAnswer.BLOCK, akyrion.thepredict.domain.DangerRules.answer(leap));
        var bench = bench(CastMode.ANY_READY, UntypedCastMode.TACTIC_ONLY, strike(0), bolt, leap);
        bench.actions.cooling.add(STRIKE.value());
        var preference = 0L;
        for (var pattern : akyrion.thepredict.domain.EnemyPattern.values()) {
            if ((held & pattern.bit()) != 0) {
                preference |= akyrion.thepredict.domain.CounterMap.of(pattern).preference();
            }
        }
        bench.session.patternHeld(held);
        bench.session.patternPreference(preference);
        bench.session.armReaction(akyrion.thepredict.domain.Reaction.EXPLOIT_PATTERN.ordinal(), 50, 50);
        return bench;
    }

    @Test
    void deathAndAFinishedEncounterDropTheReadyTick() {
        var sessions = new EngineFixtures.Sessions();
        var session = sessions.open(1);
        session.scheduleReady(4);
        sessions.registry.close(session.key().entityId(), FallbackReason.SESSION_INVALID);
        assertEquals(MobSession.NEVER, session.nextReadyTick());
        var living = sessions.open(2);
        var encounter = sessions.encounters.assign(living, UUID.randomUUID(), EngineFixtures.player(1), key -> true);
        living.scheduleReady(4);
        assertTrue(sessions.encounters.closeIfFinished(encounter.id(), id -> false));
        assertEquals(MobSession.NEVER, living.nextReadyTick());
    }

    private static Bench bench(CastMode mode, UntypedCastMode untyped, SkillChoice... choices) {
        return new Bench(mode, untyped, choices);
    }

    private static SkillChoice strike(int windUp) {
        return SkillChoice.typed(STRIKE, "TPV_Strike", Set.of(), true, 0, role(SkillRole.DAMAGE, DamageTier.MEDIUM, windUp));
    }

    private static SkillChoice bolt(DamageTier damage) {
        return bolt(damage, Set.of());
    }

    private static SkillChoice bolt(DamageTier damage, Set<SkillRisk> risks) {
        return SkillChoice.typed(BOLT, "TPV_Bolt", Set.of(), true, 0,
                purpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), damage, 0, Set.of(), risks));
    }

    private static SkillChoice leap(SkillRole role, DamageTier damage) {
        var roles = role == SkillRole.DAMAGE ? Set.of(SkillRole.DAMAGE) : Set.of(role);
        return SkillChoice.typed(LEAP, "TPV_Leap", Set.of(), true, 0, purpose(role, roles, damage, 0, Set.of(), Set.of()));
    }

    private static SkillChoice flee() {
        return SkillChoice.typed(FLEE, "TPV_Flee", Set.of(), true, 0,
                purpose(SkillRole.ESCAPE, Set.of(SkillRole.ESCAPE), DamageTier.NONE, 0, Set.of(), Set.of()));
    }

    /**
     * The same choice with a damage that ignores invulnerability (a native fact): its hit neither waits for the own
     * window of the mob nor holds another hit, so a test of another rule of the ready step keeps its subject now that
     * a profile of {@code TickLayer: casts} also waits for its own hits (D-EF2 b).
     */
    private static SkillChoice pastWindows(SkillChoice choice) {
        return choice.withNativeFacts(new akyrion.thepredict.domain.NativeSkillFacts(OptionalDouble.empty(), 1,
                akyrion.thepredict.domain.NativeSkillFacts.SEQUENTIAL, java.util.OptionalInt.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false, true, false, false));
    }

    private static SkillChoice bare() {
        return SkillChoice.untyped(BARE, "TPV_Bare", Set.of(), true, 0);
    }

    private static SkillChoice aimed(ChoiceId id, UseCondition bestWhen, DamageTier damage) {
        return SkillChoice.typed(id, "TPV_" + id.value(), Set.of(), true, 0,
                purpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), damage, 0, Set.of(bestWhen), Set.of()));
    }

    private static SkillPurpose role(SkillRole role, DamageTier damage) {
        return role(role, damage, 0);
    }

    private static SkillPurpose role(SkillRole role, DamageTier damage, int windUp) {
        var roles = role == SkillRole.DAMAGE ? Set.of(SkillRole.DAMAGE) : Set.of(role);
        return purpose(role, roles, damage, windUp, Set.of(), Set.of());
    }

    private static SkillPurpose purpose(SkillRole primary, Set<SkillRole> roles, DamageTier damage, int windUp,
                                        Set<UseCondition> bestWhen, Set<SkillRisk> risks) {
        return new SkillPurpose(primary, roles, Delivery.MELEE, OptionalDouble.of(2.5), OptionalDouble.empty(), windUp,
                CooldownClass.UNKNOWN, damage, bestWhen, risks, false, PurposeOrigin.DECLARED);
    }

    private static final class Bench {
        final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
        final DecisionMetrics metrics = new DecisionMetrics(sessions.clock::currentTick);
        final Gate actions = new Gate();
        final IntentControl control;
        final MobSession session;

        Bench(CastMode mode, UntypedCastMode untyped, SkillChoice... choices) {
            control = new IntentControl(sessions.board, actions, metrics, new ExecutionLog(line -> {
            }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                    DecisionTap.NONE);
            var timing = new DecisionTiming(20, 20, 100, 0.5, 0.2, 1, QuestionDetail.DEFAULT, false, mode, 0, untyped);
            var tactics = new LinkedHashMap<TacticId, Tactic>();
            tactics.put(PRESS, new Tactic(PRESS, Movement.APPROACH, Optional.of(STRIKE), Optional.empty(), 50));
            var declared = new LinkedHashMap<ChoiceId, SkillChoice>();
            for (var choice : choices) {
                declared.put(choice.id(), choice);
            }
            if (!declared.containsKey(STRIKE)) {
                declared.put(STRIKE, strike(0));
            }
            declared.put(SWING, new SkillChoice(SWING, ChoiceKind.VANILLA_MELEE, Optional.empty(), Set.of(), true, 0));
            var profile = new TacticalProfile("ready_ai", "Guardian", "Defeat the target", tactics, declared, List.of(),
                    MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10),
                    List.of(), timing);
            session = sessions.registry.open(EngineFixtures.id(1), profile);
            session.recordTarget(EngineFixtures.player(1), 0.0);
            session.adoptIntent(new TacticalIntent(session.key(), PRESS, Movement.APPROACH, Optional.of(STRIKE), 0.9,
                    List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE / 2, false));
            session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.empty(), Set.of(), Set.of(PRESS),
                    Set.of(), Set.of(), Map.of(), Set.of(PRESS)));
            session.scheduleReady(0);
        }

        void fire(long tick) {
            control.castIfReady(session, tick, 0);
        }

        void target(ParticipantKind kind, boolean playerThisTick) {
            var relation = new TargetRelation(new ParticipantAlias(kind, 1), DistanceBand.MELEE, Trend.STEADY, true,
                    true, false, false, false);
            session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(),
                    Set.of(PRESS), Set.of(), Set.of(), Map.of(), Set.of(PRESS)));
            session.playerTarget(playerThisTick);
        }

        void movement(Movement movement) {
            var intent = session.intent().orElseThrow();
            session.adoptIntent(new TacticalIntent(intent.mob(), intent.tactic(), movement, intent.choice(),
                    intent.confidence(), intent.alternatives(), intent.contingencies(), intent.level(),
                    intent.startTick(), intent.expiresAtNanos(), intent.skillConsumed()));
        }
    }

    private static final class Gate implements CombatActions {
        /** Ticks a skill that cools down still needs, long enough that no ready step waits for it. */
        static final int COOLING_TICKS = 100;
        final List<String> casts = new ArrayList<>();
        final ArrayDeque<ExecutionOutcome> script = new ArrayDeque<>();
        final Set<String> cooling = new java.util.HashSet<>();
        final Set<String> blocked = new java.util.HashSet<>();
        boolean sight = true;
        int sightChecks;

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            casts.add(choice.id().value());
            if (!script.isEmpty()) {
                return script.poll();
            }
            return cooling.contains(choice.id().value()) ? ExecutionOutcome.NOT_ELIGIBLE : ExecutionOutcome.CAST;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            return !cooling.contains(choice.id().value());
        }

        /**
         * A skill that cools down does so for a long while, as {@code CombatActionsAdapter.ticksUntilReady} reads the
         * remaining cooldown on a server; the default of {@link CombatActions} would say it is back on the next tick.
         */
        @Override
        public java.util.OptionalInt ticksUntilReady(MobKey mob, SkillChoice choice) {
            return java.util.OptionalInt.of(cooling.contains(choice.id().value()) ? COOLING_TICKS : 0);
        }

        @Override
        public boolean cheaplyAdmitted(MobKey mob, SkillChoice choice, Movement movement) {
            return !blocked.contains(choice.id().value());
        }

        @Override
        public boolean seesTarget(MobKey mob) {
            sightChecks++;
            return sight;
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, UUID participantId, double amount) {
        }
    }
}
