package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.MOB;
import static akyrion.thepredict.domain.DomainFixtures.answer;
import static akyrion.thepredict.domain.DomainFixtures.guardian;
import static akyrion.thepredict.domain.DomainFixtures.profile;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static akyrion.thepredict.domain.DomainFixtures.tacticOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntentPolicyTest {
    @Test
    void reactiveCadenceExpiresAtTheIntentHorizon() {
        var timing = PROFILE.timing().withLayer(TickLayerMode.REACTIONS, ReactionScope.ALL, Set.of(), 0,
                DecisionCadence.REACTIVE, 6, 10);
        var profile = new TacticalProfile(PROFILE.id(), PROFILE.mythicMobId(), PROFILE.objective(), PROFILE.tactics(),
                PROFILE.choices(), PROFILE.contexts(), PROFILE.movement(), PROFILE.health(), PROFILE.threat(),
                PROFILE.baseAi(), timing, PROFILE.participants());
        var intent = adopted(IntentPolicy.accept(MOB, answer(0.8, "press_attack", 0.6, "keep_range", 0.3,
                "hold_ground", 0.1), Map.of(), ALL, profile, 1, NOW));
        assertEquals(NOW + 10 * IntentPolicy.NANOS_PER_TICK, intent.expiresAtNanos());
    }

    @Test
    void theLifetimeCountsFromTheGivenBaseInEveryOverloadAndTheCurrentIntentIsStillJudgedNow() {
        var base = NOW - 6 * IntentPolicy.NANOS_PER_TICK;
        var answer = answer(0.8, "press_attack", 0.6, "keep_range", 0.3, "hold_ground", 0.1);
        var floor = PROFILE.timing().minimumConfidence();
        assertEquals(base + LIFETIME, adopted(IntentPolicy.accept(MOB, answer, Map.of(), ALL, PROFILE, 1, NOW, base,
                floor)).expiresAtNanos(), "AE-09: a mob without an intent");
        var held = current("press_attack", ControlLevel.JEV, 0, NOW + 10, false);
        assertEquals(base + LIFETIME, renewed(IntentPolicy.accept(held, answer, Map.of(), ALL, PROFILE, 1, NOW, base,
                floor)).expiresAtNanos(), "the renewal counts from the base too");
        var stale = current("press_attack", ControlLevel.JEV, 0, NOW - 1, false);
        assertEquals(base + LIFETIME, adopted(IntentPolicy.accept(stale, answer, Map.of(), ALL, PROFILE, 1, NOW, base,
                floor)).expiresAtNanos(), "an intent that expired before now is replaced, whatever the base");
        var fits = fits("press_attack", 0.9, "keep_range", 0.9);
        assertEquals(base + LIFETIME, adopted(IntentPolicy.accept(MOB, answer, Map.of(), ALL, PROFILE, 1, NOW, base,
                fits, FITS)).expiresAtNanos(), "with fit checks");
        assertEquals(base + LIFETIME, renewed(IntentPolicy.accept(held, answer, Map.of(), ALL, PROFILE, 1, NOW, base,
                fits, FITS)).expiresAtNanos(), "a renewal with fit checks");
        assertEquals(NOW + LIFETIME, adopted(IntentPolicy.accept(MOB, answer, Map.of(), ALL, PROFILE, 1, NOW))
                .expiresAtNanos(), "the overloads without a base count from now, as before (W5)");
    }

    private static final TacticalProfile PROFILE = guardian();
    private static final Set<TacticId> ALL = PROFILE.tactics().keySet();
    private static final long NOW = 1_000_000L;
    private static final long LIFETIME = 100 * IntentPolicy.NANOS_PER_TICK;

    private static TacticalIntent current(String tacticName, ControlLevel level, long startTick, long expiresAtNanos,
                                          boolean skillConsumed) {
        var tactic = PROFILE.tactic(tactic(tacticName)).orElseThrow();
        return new TacticalIntent(MOB, tactic.id(), tactic.movement(), tactic.choice(), 0.8, List.of(), Map.of(),
                level, startTick, expiresAtNanos, skillConsumed);
    }

    private static TacticalIntent pressing() {
        return current("press_attack", ControlLevel.JEV, 0, NOW + 10, false);
    }

    private static TacticalIntent adopted(IntentOutcome outcome) {
        return assertInstanceOf(IntentOutcome.Adopted.class, outcome).intent();
    }

    private static TacticalIntent renewed(IntentOutcome outcome) {
        return assertInstanceOf(IntentOutcome.Renewed.class, outcome).intent();
    }

    private static FallbackReason rejected(IntentOutcome outcome) {
        return assertInstanceOf(IntentOutcome.Rejected.class, outcome).reason();
    }

    @Test
    void firstAnswerIsAdoptedWithMovementChoiceAlternativesAndDeadline() {
        var intent = adopted(IntentPolicy.accept(MOB, answer(0.8, "press_attack", 0.6, "keep_range", 0.3,
                "hold_ground", 0.1), Map.of(), ALL, PROFILE, 7, NOW));
        assertEquals(tactic("press_attack"), intent.tactic());
        assertEquals(Movement.APPROACH, intent.movement());
        assertEquals(Optional.of(new ChoiceId("melee")), intent.choice());
        assertEquals(List.of(tactic("keep_range"), tactic("hold_ground")), intent.alternatives());
        assertEquals(ControlLevel.JEV, intent.level());
        assertEquals(7, intent.startTick());
        assertEquals(NOW + LIFETIME, intent.expiresAtNanos());
    }

    @Test
    void confidenceExactlyAtTheThresholdIsAccepted() {
        adopted(IntentPolicy.accept(MOB, answer(0.5, "press_attack", 1.0), Map.of(), ALL, PROFILE, 0, NOW));
    }

    @Test
    void confidenceBelowTheThresholdIsRejectedAndKeepsTheCurrentIntent() {
        assertEquals(FallbackReason.LOW_CONFIDENCE,
                rejected(IntentPolicy.accept(MOB, answer(0.49, "press_attack", 1.0), Map.of(), ALL, PROFILE, 0, NOW)));
        assertEquals(FallbackReason.LOW_CONFIDENCE, rejected(IntentPolicy.accept(pressing(),
                answer(0.49, "keep_range", 1.0), Map.of(), ALL, PROFILE, 50, NOW)));
    }

    @Test
    void probabilitiesAreFilteredToEligibleTactics() {
        var intent = adopted(IntentPolicy.accept(MOB, answer(0.9, "defend_self", 0.7, "keep_range", 0.3), Map.of(),
                Set.of(tactic("keep_range"), tactic("hold_ground")), PROFILE, 0, NOW));
        assertEquals(tactic("keep_range"), intent.tactic());
        assertEquals(List.of(), intent.alternatives());
    }

    @Test
    void answerWithoutApplicableTacticIsRejectedAsInvalid() {
        assertEquals(FallbackReason.INVALID_RESPONSE, rejected(IntentPolicy.accept(MOB,
                answer(0.9, "defend_self", 0.5, "unknown_move", 0.5), Map.of(), Set.of(tactic("hold_ground")),
                PROFILE, 0, NOW)));
    }

    @Test
    void eligibleTacticWithZeroProbabilityIsNotApplicable() {
        var answer = answer(0.9, "keep_range", 1.0, "press_attack", 0.0);
        var onlyPress = Set.of(tactic("press_attack"));
        assertEquals(FallbackReason.INVALID_RESPONSE,
                rejected(IntentPolicy.accept(MOB, answer, Map.of(), onlyPress, PROFILE, 0, NOW)));
        assertEquals(FallbackReason.INVALID_RESPONSE,
                rejected(IntentPolicy.accept(pressing(), answer, Map.of(), onlyPress, PROFILE, 50, NOW)));
    }

    @Test
    void contingencyNeverPlansAZeroProbabilityTactic() {
        var contingencies = Map.of(Situation.IF_TARGET_IN_MELEE, answer(0.9, "undeclared_move", 1.0, "defend_self", 0.0));
        assertEquals(Map.of(), adopted(IntentPolicy.accept(MOB, answer(0.8, "keep_range", 1.0), contingencies, ALL,
                PROFILE, 0, NOW)).contingencies());
    }

    @Test
    void zeroMarginDoesNotSwitchOnAProbabilityTie() {
        var noMargin = profile(List.copyOf(PROFILE.tactics().values()), List.copyOf(PROFILE.choices().values()),
                PROFILE.contexts(), new DecisionTiming(40, 20, 100, 0.5, 0.0, 2));
        var intent = renewed(IntentPolicy.accept(pressing(), answer(0.8, "keep_range", 0.5, "press_attack", 0.5),
                Map.of(), ALL, noMargin, 50, NOW));
        assertEquals(tactic("press_attack"), intent.tactic());
        assertEquals(tactic("keep_range"), adopted(IntentPolicy.accept(pressing(),
                answer(0.8, "keep_range", 0.51, "press_attack", 0.49), Map.of(), ALL, noMargin, 50, NOW)).tactic());
    }

    @Test
    void switchExactlyAtTheMarginAfterTheMinimumIntervalIsAdopted() {
        var intent = adopted(IntentPolicy.accept(pressing(), answer(0.8, "keep_range", 0.6, "press_attack", 0.4),
                Map.of(), ALL, PROFILE, 20, NOW));
        assertEquals(tactic("keep_range"), intent.tactic());
        assertEquals(20, intent.startTick());
    }

    @Test
    void switchBelowTheMarginRenewsTheCurrentTactic() {
        var intent = renewed(IntentPolicy.accept(pressing(), answer(0.8, "keep_range", 0.55, "press_attack", 0.45),
                Map.of(), ALL, PROFILE, 20, NOW));
        assertEquals(tactic("press_attack"), intent.tactic());
        assertEquals(0, intent.startTick());
        assertEquals(NOW + LIFETIME, intent.expiresAtNanos());
        assertEquals(List.of(tactic("keep_range")), intent.alternatives());
    }

    @Test
    void switchBeforeTheMinimumIntervalRenewsTheCurrentTactic() {
        var intent = renewed(IntentPolicy.accept(pressing(), answer(0.8, "keep_range", 0.9, "press_attack", 0.1),
                Map.of(), ALL, PROFILE, 19, NOW));
        assertEquals(tactic("press_attack"), intent.tactic());
    }

    @Test
    void sameTacticIsRenewedKeepingStartAndConsumedSkill() {
        var current = current("press_attack", ControlLevel.JEV, 3, NOW + 10, true);
        var intent = renewed(IntentPolicy.accept(current, answer(0.7, "press_attack", 1.0), Map.of(), ALL, PROFILE, 5, NOW));
        assertEquals(3, intent.startTick());
        assertEquals(true, intent.skillConsumed());
        assertEquals(0.7, intent.confidence());
    }

    @Test
    void expiredOrIneligibleCurrentIntentIsReplacedAtOnce() {
        var expired = current("press_attack", ControlLevel.JEV, 0, NOW, false);
        assertEquals(tactic("keep_range"), adopted(IntentPolicy.accept(expired,
                answer(0.8, "keep_range", 0.5, "press_attack", 0.5), Map.of(), ALL, PROFILE, 1, NOW)).tactic());
        assertEquals(tactic("keep_range"), adopted(IntentPolicy.accept(pressing(),
                answer(0.8, "keep_range", 0.5, "press_attack", 0.5), Map.of(),
                Set.of(tactic("keep_range")), PROFILE, 1, NOW)).tactic());
    }

    @Test
    void localIntentIsReplacedWithoutMarginOrMinimumInterval() {
        var local = current("press_attack", ControlLevel.LOCAL, 0, NOW + 10, false);
        assertEquals(tactic("keep_range"), adopted(IntentPolicy.accept(local,
                answer(0.8, "keep_range", 0.51, "press_attack", 0.49), Map.of(), ALL, PROFILE, 1, NOW)).tactic());
    }

    @Test
    void atMostThreeAlternativesWithPositiveProbabilityAreKept() {
        var profile = profile(List.of(tacticOf("aaa_move", Movement.HOLD, null, 1), tacticOf("bbb_move", Movement.HOLD, null, 1),
                tacticOf("ccc_move", Movement.HOLD, null, 1), tacticOf("ddd_move", Movement.HOLD, null, 1),
                tacticOf("eee_move", Movement.HOLD, null, 1)), List.of(), List.of(), DomainFixtures.timing());
        var intent = adopted(IntentPolicy.accept(MOB, answer(0.9, "aaa_move", 0.4, "bbb_move", 0.3, "ccc_move", 0.2,
                "ddd_move", 0.1, "eee_move", 0.0), Map.of(), profile.tactics().keySet(), profile, 0, NOW));
        assertEquals(List.of(tactic("bbb_move"), tactic("ccc_move"), tactic("ddd_move")), intent.alternatives());
        var sparse = adopted(IntentPolicy.accept(MOB, answer(0.9, "aaa_move", 1.0, "bbb_move", 0.0), Map.of(),
                profile.tactics().keySet(), profile, 0, NOW));
        assertEquals(List.of(), sparse.alternatives());
    }

    @Test
    void confidentContingenciesAreKeptUpToTheProfileLimit() {
        var contingencies = Map.of(Situation.IF_TARGET_IN_MELEE, answer(0.9, "defend_self", 0.8, "hold_ground", 0.2),
                Situation.IF_OWN_HEALTH_CRITICAL, answer(0.2, "defend_self", 1.0));
        var intent = adopted(IntentPolicy.accept(MOB, answer(0.8, "keep_range", 1.0), contingencies, ALL, PROFILE, 0, NOW));
        assertEquals(Map.of(Situation.IF_TARGET_IN_MELEE, tactic("defend_self")), intent.contingencies());
        var limited = profile(List.copyOf(PROFILE.tactics().values()), List.copyOf(PROFILE.choices().values()),
                PROFILE.contexts(), new DecisionTiming(40, 20, 100, 0.5, 0.2, 1));
        var both = Map.of(Situation.IF_TARGET_IN_MELEE, answer(0.9, "defend_self", 1.0),
                Situation.IF_OWN_HEALTH_CRITICAL, answer(0.9, "hold_ground", 1.0));
        assertEquals(Map.of(Situation.IF_OWN_HEALTH_CRITICAL, tactic("hold_ground")), adopted(IntentPolicy.accept(MOB,
                answer(0.8, "keep_range", 1.0), both, ALL, limited, 0, NOW)).contingencies());
    }

    @Test
    void contingencyEqualToTheChosenTacticIsDroppedAndNowIsRejected() {
        var same = Map.of(Situation.IF_TARGET_IN_MELEE, answer(0.9, "keep_range", 1.0));
        assertEquals(Map.of(), adopted(IntentPolicy.accept(MOB, answer(0.8, "keep_range", 1.0), same, ALL, PROFILE, 0, NOW))
                .contingencies());
        assertThrows(IllegalArgumentException.class, () -> IntentPolicy.accept(MOB, answer(0.8, "keep_range", 1.0),
                Map.of(Situation.NOW, answer(0.9, "hold_ground", 1.0)), ALL, PROFILE, 0, NOW));
    }

    @Test
    void nextAlternativeSkipsIneligibleAlternativesAndKeepsTheDeadline() {
        var intent = new TacticalIntent(MOB, tactic("press_attack"), Movement.APPROACH, Optional.empty(), 0.8,
                List.of(tactic("defend_self"), tactic("keep_range"), tactic("hold_ground")),
                Map.of(Situation.IF_TARGET_IN_MELEE, tactic("keep_range")), ControlLevel.JEV, 0, NOW + 5, false);
        var next = IntentPolicy.nextAlternative(intent, Set.of(tactic("keep_range"), tactic("hold_ground")), PROFILE, 30)
                .orElseThrow();
        assertEquals(tactic("keep_range"), next.tactic());
        assertEquals(Movement.KEEP_DISTANCE, next.movement());
        assertEquals(ControlLevel.ALTERNATIVE, next.level());
        assertEquals(NOW + 5, next.expiresAtNanos());
        assertEquals(30, next.startTick());
        assertEquals(List.of(tactic("hold_ground")), next.alternatives());
        assertEquals(Map.of(), next.contingencies());
    }

    @Test
    void exhaustedAlternativesYieldNothing() {
        var last = new TacticalIntent(MOB, tactic("keep_range"), Movement.KEEP_DISTANCE, Optional.empty(), 0.8,
                List.of(tactic("hold_ground")), Map.of(), ControlLevel.ALTERNATIVE, 0, NOW, false);
        var hold = IntentPolicy.nextAlternative(last, ALL, PROFILE, 1).orElseThrow();
        assertEquals(Optional.empty(), IntentPolicy.nextAlternative(hold, ALL, PROFILE, 2));
        assertEquals(Optional.empty(), IntentPolicy.nextAlternative(last, Set.of(), PROFILE, 1));
    }

    private static final FitGate.FitThresholds FITS = FitGate.FitThresholds.DEFAULT;

    private static Map<TacticId, Double> fits(Object... tacticsAndFits) {
        var fits = new LinkedHashMap<TacticId, Double>();
        for (var index = 0; index < tacticsAndFits.length; index += 2) {
            fits.put(tactic((String) tacticsAndFits[index]), ((Number) tacticsAndFits[index + 1]).doubleValue());
        }
        return fits;
    }

    @Test
    void withoutFitsTheRuleOfZeroZeroOneIsUnchanged() {
        var now = answer(0.8, "press_attack", 0.6, "keep_range", 0.3, "hold_ground", 0.1);
        assertEquals(IntentPolicy.accept(MOB, now, Map.of(), ALL, PROFILE, 7, NOW),
                IntentPolicy.accept(MOB, now, Map.of(), ALL, PROFILE, 7, NOW, Map.of(), FITS));
        assertEquals(IntentPolicy.accept(pressing(), now, Map.of(), ALL, PROFILE, 30, NOW),
                IntentPolicy.accept(pressing(), now, Map.of(), ALL, PROFILE, 30, NOW, Map.of(), FITS));
    }

    @Test
    void aVetoOfTheBestAdoptsTheNextBackedTacticAsAnAlternativeWithoutVetoedAlternatives() {
        var intent = adopted(IntentPolicy.accept(MOB, answer(0.8, "press_attack", 0.6, "keep_range", 0.3,
                "hold_ground", 0.1), Map.of(), ALL, PROFILE, 7, NOW, fits("press_attack", 0.1, "keep_range", 0.5,
                "hold_ground", 0.05), FITS));
        assertEquals(tactic("keep_range"), intent.tactic());
        assertEquals(ControlLevel.ALTERNATIVE, intent.level());
        assertEquals(List.of(), intent.alternatives());
    }

    @Test
    void aFitRescuesALowConfidenceAndAVetoOfEveryOptionIsALowConfidence() {
        var low = answer(0.3, "press_attack", 0.6, "keep_range", 0.3, "hold_ground", 0.1);
        var rescued = adopted(IntentPolicy.accept(MOB, low, Map.of(), ALL, PROFILE, 7, NOW,
                fits("press_attack", 0.9, "keep_range", 0.4, "hold_ground", 0.3), FITS));
        assertEquals(tactic("press_attack"), rescued.tactic());
        assertEquals(ControlLevel.JEV, rescued.level());
        assertEquals(FallbackReason.LOW_CONFIDENCE, rejected(IntentPolicy.accept(MOB, answer(0.9, "press_attack", 0.6,
                "keep_range", 0.4), Map.of(), ALL, PROFILE, 7, NOW, fits("press_attack", 0.1, "keep_range", 0.1), FITS)));
    }

    @Test
    void anOptionThatIsNoLongerEligibleCountsAsVetoed() {
        var intent = adopted(IntentPolicy.accept(MOB, answer(0.8, "press_attack", 0.6, "keep_range", 0.4), Map.of(),
                Set.of(tactic("keep_range")), PROFILE, 7, NOW, fits("press_attack", 0.9, "keep_range", 0.5), FITS));
        assertEquals(tactic("keep_range"), intent.tactic());
        assertEquals(ControlLevel.ALTERNATIVE, intent.level());
    }

    @Test
    void withAVetoOfZeroAnOptionThatIsNotApplicableIsSkippedForTheNextValidOne() {
        var zero = new FitGate.FitThresholds(0.0, 0.7);
        var intent = adopted(IntentPolicy.accept(MOB, answer(0.8, "press_attack", 0.6, "keep_range", 0.4), Map.of(),
                Set.of(tactic("keep_range")), PROFILE, 7, NOW, fits("press_attack", 0.9, "keep_range", 0.5), zero));
        assertEquals(tactic("keep_range"), intent.tactic());
        assertEquals(ControlLevel.ALTERNATIVE, intent.level());
        assertEquals(FallbackReason.LOW_CONFIDENCE, rejected(IntentPolicy.accept(MOB, answer(0.8, "press_attack", 0.6,
                "keep_range", 0.4), Map.of(), Set.of(), PROFILE, 7, NOW, fits("press_attack", 0.9, "keep_range", 0.5),
                zero)), "no applicable option: nothing survives");
    }

    @Test
    void withFitsACurrentJevIntentKeepsItsHysteresisAndIsRenewedForItsOwnTactic() {
        var now = answer(0.8, "press_attack", 0.45, "keep_range", 0.55);
        var held = renewed(IntentPolicy.accept(pressing(), now, Map.of(), ALL, PROFILE, 30, NOW,
                fits("press_attack", 0.5, "keep_range", 0.5), FITS));
        assertEquals(tactic("press_attack"), held.tactic());
        var same = renewed(IntentPolicy.accept(pressing(), answer(0.8, "press_attack", 0.7, "keep_range", 0.3), Map.of(),
                ALL, PROFILE, 30, NOW, fits("press_attack", 0.5, "keep_range", 0.5), FITS));
        assertEquals(tactic("press_attack"), same.tactic());
        var vetoed = adopted(IntentPolicy.accept(pressing(), answer(0.8, "press_attack", 0.7, "keep_range", 0.3),
                Map.of(), ALL, PROFILE, 30, NOW, fits("press_attack", 0.1, "keep_range", 0.5), FITS));
        assertEquals(tactic("keep_range"), vetoed.tactic());
        assertEquals(ControlLevel.ALTERNATIVE, vetoed.level());
        assertEquals(ControlLevel.JEV, held.level(), "the hysteresis keeps the current level");
    }

    @Test
    void withFitsALocalIntentThatJevConfirmsIsRenewedAtTheLevelOfTheVerdict() {
        var local = current("press_attack", ControlLevel.LOCAL, 3, NOW + 10, false);
        var confirmed = renewed(IntentPolicy.accept(local, answer(0.8, "press_attack", 0.7, "keep_range", 0.3),
                Map.of(), ALL, PROFILE, 30, NOW, fits("press_attack", 0.9, "keep_range", 0.5), FITS));
        assertEquals(tactic("press_attack"), confirmed.tactic());
        assertEquals(ControlLevel.JEV, confirmed.level());
        assertEquals(3, confirmed.startTick());
        var withoutFits = renewed(IntentPolicy.accept(local, answer(0.8, "press_attack", 0.7, "keep_range", 0.3),
                Map.of(), ALL, PROFILE, 30, NOW));
        assertEquals(withoutFits.level(), confirmed.level(), "both paths promote the confirmed intent alike");
        var alternative = current("keep_range", ControlLevel.ALTERNATIVE, 3, NOW + 10, false);
        assertEquals(ControlLevel.JEV, renewed(IntentPolicy.accept(alternative, answer(0.8, "keep_range", 0.7,
                "press_attack", 0.3), Map.of(), ALL, PROFILE, 30, NOW, fits("keep_range", 0.9), FITS)).level());
        var rescued = renewed(IntentPolicy.accept(pressing(), answer(0.8, "keep_range", 0.7, "press_attack", 0.3),
                Map.of(), ALL, PROFILE, 30, NOW, fits("keep_range", 0.1, "press_attack", 0.5), FITS));
        assertEquals(tactic("press_attack"), rescued.tactic());
        assertEquals(ControlLevel.ALTERNATIVE, rescued.level(), "a vetoed best renews the current tactic as an alternative");
    }
}
