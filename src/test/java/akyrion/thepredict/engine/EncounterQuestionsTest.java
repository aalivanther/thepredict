package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.Eligibility;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.Trend;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M09.8 steps 2 to 4, M15.5 and M16.1, M16.5, M16.6: the options of a question are the distinct eligible tactics of the
 * capture, capped without dropping the current tactic; a contingency offers what its hypothesis allows.
 */
class EncounterQuestionsTest {
    private static final MobKey MOB = new MobKey(EngineFixtures.id(1), 0);
    private static final ChoiceId STRIKE = new ChoiceId("strike");
    private static final TacticId PRESS = new TacticId("press");
    private static final TacticId KEEP = new TacticId("keep_and_strike");
    private static final TacticId HOLD = new TacticId("hold_and_strike");

    @Test
    void noQuestionOffersATacticWhoseDeclaredSkillNeverCastsOutOfReach() {
        var profile = profile(declared());
        var plan = plan(profile, Eligibility.eligible(profile, Set.of(), Set.of(STRIKE), EnumSet.allOf(Movement.class),
                Set.of(STRIKE)), Set.of(STRIKE));
        assertTrue(plan.questions().stream().noneMatch(question -> question.options().contains(KEEP)));
        assertEquals(List.of(PRESS, HOLD), plan.questions().getFirst().options());
    }

    @Test
    void theSameTacticIsOfferedWhileTheDeclaredSkillIsInReach() {
        var profile = profile(declared());
        var plan = plan(profile, Eligibility.eligible(profile, Set.of(), Set.of(STRIKE), EnumSet.allOf(Movement.class),
                Set.of()), Set.of());
        assertEquals(List.of(PRESS, KEEP, HOLD), plan.questions().getFirst().options());
    }

    @Test
    void anInferredMeleeSkillIsOfferedAsInTheZeroZeroOne() {
        var profile = profile(new SkillChoice(STRIKE, ChoiceKind.MYTHIC_SKILL, Optional.of("Strike"),
                Set.of(PredictValue.MELEE_ATTACK), true, 0));
        var plan = plan(profile, Eligibility.eligible(profile, Set.of(), Set.of(STRIKE), EnumSet.allOf(Movement.class),
                Set.of(STRIKE)), Set.of());
        assertEquals(List.of(PRESS, KEEP, HOLD), plan.questions().getFirst().options());
    }

    private static EncounterQuestions.Plan plan(TacticalProfile profile, Set<TacticId> eligible,
                                                Set<ChoiceId> outOfReach) {
        var relation = new TargetRelation(new ParticipantAlias(1), DistanceBand.RANGED, Trend.STEADY, true, true, false,
                false, false);
        var mob = new MobFacts(MOB, HealthBand.HEALTHY, Optional.of(relation), Set.of(), eligible, Set.of(), outOfReach);
        var facts = new CombatFacts(new EncounterId(1), 10, 0, List.of(mob), List.of(new ParticipantFacts(new ParticipantAlias(1),
                HealthBand.HEALTHY, ArmorBand.LIGHT, Set.of(), 1)), List.of(), 0);
        return EncounterQuestions.plan(facts, key -> Optional.of(profile), key -> Optional.empty(), 8);
    }

    private static SkillChoice declared() {
        return new SkillChoice(STRIKE, ChoiceKind.MYTHIC_SKILL, Optional.of("Strike"), Set.of(), true, 0,
                new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(2.5),
                        OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.MEDIUM, Set.of(), Set.of(), false,
                        PurposeOrigin.DECLARED));
    }

    private static TacticalProfile profile(SkillChoice strike) {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactics.put(PRESS, new Tactic(PRESS, Movement.APPROACH, Optional.of(STRIKE), Optional.empty(), 70));
        tactics.put(KEEP, new Tactic(KEEP, Movement.KEEP_DISTANCE, Optional.of(STRIKE), Optional.empty(), 60));
        tactics.put(HOLD, new Tactic(HOLD, Movement.HOLD, Optional.of(STRIKE), Optional.empty(), 50));
        return new TacticalProfile("reach_ai", "Guardian", "Defeat the target", tactics, Map.of(STRIKE, strike),
                List.of(), new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                ExecutionFixture.timing(1));
    }

    private static TacticalProfile wide(int tactics, QuestionDetail detail, boolean fitChecks, int contingencies) {
        var declared = new LinkedHashMap<TacticId, Tactic>();
        for (var index = 0; index < tactics; index++) {
            var id = new TacticId(String.format("tactic_%02d", index));
            declared.put(id, new Tactic(id, Movement.HOLD, Optional.empty(), Optional.of("Variant " + index),
                    90 - index));
        }
        declared.put(new TacticId("twin_a"), new Tactic(new TacticId("twin_a"), Movement.APPROACH, Optional.empty(),
                Optional.empty(), 95));
        declared.put(new TacticId("twin_b"), new Tactic(new TacticId("twin_b"), Movement.APPROACH, Optional.empty(),
                Optional.empty(), 94));
        return new TacticalProfile("wide_ai", "Guardian", "Defeat the target", declared, Map.of(), List.of(),
                new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                new DecisionTiming(20, 20, 100, 0.5, 0.2, contingencies, detail, fitChecks));
    }

    private static EncounterQuestions.Plan planWide(TacticalProfile profile, Optional<TacticId> current, int maxOptions,
                                                    Map<Situation, Set<TacticId>> hypothetical) {
        var relation = new TargetRelation(new ParticipantAlias(1), DistanceBand.RANGED, Trend.STEADY, true, true, false,
                false, false);
        var mob = new MobFacts(MOB, HealthBand.HEALTHY, Optional.of(relation), Set.of(), profile.tactics().keySet(),
                Set.of(), Set.of(), hypothetical);
        var facts = new CombatFacts(new EncounterId(1), 10, 0, List.of(mob), List.of(new ParticipantFacts(
                new ParticipantAlias(1), HealthBand.HEALTHY, ArmorBand.LIGHT, Set.of(), 1)), List.of(), 0);
        return EncounterQuestions.plan(facts, key -> Optional.of(profile), key -> current, maxOptions);
    }

    @Test
    void tacticsIdenticalInEverythingAreOfferedOnceAndTheCapNeverDropsTheCurrentTactic() {
        var profile = wide(12, QuestionDetail.COMPACT, false, 0);
        var plan = planWide(profile, Optional.empty(), 8, Map.of());
        var now = plan.questions().getFirst();
        assertEquals(8, now.options().size());
        assertEquals(new TacticId("twin_a"), now.options().getFirst());
        assertFalse(now.options().contains(new TacticId("twin_b")));
        var current = new TacticId("tactic_11");
        var kept = planWide(profile, Optional.of(current), 8, Map.of()).questions().getFirst();
        assertEquals(8, kept.options().size());
        assertEquals(current, kept.options().getLast());
        assertEquals(Optional.of(current), kept.keep());
        var twin = planWide(profile, Optional.of(new TacticId("twin_b")), 2, Map.of()).questions().getFirst();
        assertEquals(List.of(new TacticId("twin_b"), new TacticId("tactic_00")), twin.options());
    }

    @Test
    void theCapIsTwoToThirtyTwoOptions() {
        var profile = wide(40, QuestionDetail.COMPACT, false, 0);
        assertEquals(2, planWide(profile, Optional.empty(), 2, Map.of()).questions().getFirst().options().size());
        assertEquals(32, planWide(profile, Optional.empty(), 32, Map.of()).questions().getFirst().options().size());
    }

    @Test
    void aContingencyOffersOnlyWhatIsEligibleUnderItsHypothesisAndIsNotAskedWithFewerThanTwo() {
        var profile = wide(3, QuestionDetail.STANDARD, false, 2);
        var melee = Set.of(new TacticId("twin_a"), new TacticId("tactic_02"));
        var plan = planWide(profile, Optional.empty(), 8, Map.of(Situation.IF_TARGET_IN_MELEE, melee,
                Situation.IF_OWN_HEALTH_CRITICAL, Set.of(new TacticId("tactic_01"))));
        assertEquals(List.of(Situation.NOW, Situation.IF_TARGET_IN_MELEE), plan.questions().stream()
                .map(QuestionSpec::situation).toList());
        assertEquals(List.of(new TacticId("twin_a"), new TacticId("tactic_02")), plan.questions().get(1).options());
    }

    @Test
    void aCompactContingencyOffersAtMostFiveOptionsAlwaysWithTheCurrentTactic() {
        var profile = wide(10, QuestionDetail.COMPACT, false, 1);
        var current = new TacticId("tactic_09");
        var plan = planWide(profile, Optional.of(current), 8, Map.of());
        assertEquals(8, plan.questions().getFirst().options().size());
        var contingency = plan.questions().get(1);
        assertEquals(EncounterQuestions.COMPACT_CONTINGENCY_OPTIONS, contingency.options().size());
        assertTrue(contingency.options().contains(current));
        var standard = planWide(wide(10, QuestionDetail.STANDARD, false, 1), Optional.empty(), 8, Map.of());
        assertEquals(8, standard.questions().get(1).options().size());
    }

    @Test
    void fitChecksFollowTheQuestionsOnlyForProfilesThatSendThem() {
        var full = planWide(wide(2, QuestionDetail.FULL, true, 1), Optional.empty(), 8, Map.of());
        assertEquals(2, full.questions().size());
        var now = full.questions().getFirst();
        assertEquals(now.options().size(), full.fitChecks().size());
        for (var index = 0; index < full.fitChecks().size(); index++) {
            var check = full.fitChecks().get(index);
            assertEquals(QuestionKey.of(2 + index), check.key());
            assertEquals(now.options().get(index), check.tactic());
            assertEquals(MOB, check.mob());
        }
        assertTrue(planWide(wide(2, QuestionDetail.STANDARD, true, 1), Optional.empty(), 8, Map.of()).fitChecks()
                .isEmpty(), "only the full detail sends fit checks");
        assertTrue(planWide(wide(2, QuestionDetail.FULL, false, 1), Optional.empty(), 8, Map.of()).fitChecks()
                .isEmpty());
    }

    /** ENGINE-AE-10: with safe pruning, only the contingencies that are asked are listed and counted. */
    @Test
    void safePruningListsOnlyTheContingenciesThatAreAsked() {
        var profile = wide(3, QuestionDetail.STANDARD, false, 1);
        var hypothetical = Map.of(Situation.IF_TARGET_IN_MELEE, Set.of(new TacticId("twin_a"), new TacticId("tactic_02")),
                Situation.IF_OWN_HEALTH_CRITICAL, Set.of(new TacticId("tactic_00"), new TacticId("tactic_01")));
        var relation = new TargetRelation(new ParticipantAlias(1), DistanceBand.RANGED, Trend.STEADY, true, true, false,
                false, false);
        var mob = new MobFacts(MOB, HealthBand.HEALTHY, Optional.of(relation), Set.of(), profile.tactics().keySet(),
                Set.of(), Set.of(), hypothetical);
        var facts = new CombatFacts(new EncounterId(1), 10, 0, List.of(mob), List.of(new ParticipantFacts(
                new ParticipantAlias(1), HealthBand.HEALTHY, ArmorBand.LIGHT, Set.of(), 1)), List.of(), 0);
        var plan = EncounterQuestions.plan(facts, key -> Optional.of(profile), key -> Optional.empty(), 8,
                akyrion.thepredict.domain.PruningMode.SAFE);
        assertEquals(List.of(Situation.NOW, Situation.IF_TARGET_IN_MELEE), plan.questions().stream()
                .map(QuestionSpec::situation).toList());
        assertEquals(2, plan.prunes().size(), "one notice for NOW and one for the contingency that was asked");
    }
}
