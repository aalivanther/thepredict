package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.MOB;
import static akyrion.thepredict.domain.DomainFixtures.movement;
import static akyrion.thepredict.domain.DomainFixtures.profile;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Safe vetoes, the merge and the cap of one question (M19.6). */
class OptionPruningTest {
    @Test
    void sustainWhileHealthyDropsOnlyAHealthyAuthoredSustain() {
        var profile = profileOf(choice("defense", sustain()), tacticOf("retreat_and_defend", Movement.RETREAT, "defense", 90));
        assertEquals(PruneReason.SUSTAIN_WHILE_HEALTHY, prune(profile, facts(HealthBand.HEALTHY, true, true),
                CastMode.INTENT_ONLY, 8).dropped().get(tactic("retreat_and_defend")));
        assertFalse(prune(profile, facts(HealthBand.WOUNDED, true, true), CastMode.INTENT_ONLY, 8).dropped()
                .containsKey(tactic("retreat_and_defend")));
    }

    @Test
    void outOfReachDropsHoldAndRetreatOnly() {
        var profile = profileOf(List.of(choice("ranged", hitscan(16))), List.of(
                tacticOf("hold", Movement.HOLD, "ranged", 10),
                tacticOf("retreat", Movement.RETREAT, "ranged", 20),
                tacticOf("approach", Movement.APPROACH, "ranged", 30),
                tacticOf("keep", Movement.KEEP_DISTANCE, null, 5)));
        var dropped = prune(profile, facts(HealthBand.HEALTHY, true, true, Set.of(new ChoiceId("ranged"))),
                CastMode.INTENT_ONLY, 8).dropped();
        assertEquals(PruneReason.OUT_OF_REACH_STATIC, dropped.get(tactic("hold")));
        assertEquals(PruneReason.OUT_OF_REACH_STATIC, dropped.get(tactic("retreat")));
        assertFalse(dropped.containsKey(tactic("approach")));
    }

    @Test
    void noSightDropsABlindHoldAndKeepsItWhenIntentOnlyHasNoOtherHold() {
        var sight = choice("ranged", hitscan(16, Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT)));
        var profile = profileOf(List.of(sight), List.of(
                tacticOf("hold", Movement.HOLD, "ranged", 10),
                tacticOf("wait", Movement.HOLD, null, 5),
                tacticOf("approach", Movement.APPROACH, null, 20)));
        assertEquals(PruneReason.NO_SIGHT_STATIC, prune(profile, facts(HealthBand.HEALTHY, true, false),
                CastMode.INTENT_ONLY, 8).dropped().get(tactic("hold")));
        var alone = profileOf(List.of(sight), List.of(tacticOf("hold", Movement.HOLD, "ranged", 10),
                tacticOf("approach", Movement.APPROACH, null, 20),
                tacticOf("press", Movement.APPROACH, null, 15)));
        assertFalse(prune(alone, facts(HealthBand.HEALTHY, true, false), CastMode.INTENT_ONLY, 8).dropped()
                .containsKey(tactic("hold")));
        assertEquals(PruneReason.NO_SIGHT_STATIC, prune(alone, facts(HealthBand.HEALTHY, true, false),
                CastMode.ANY_READY, 8).dropped().get(tactic("hold")));
        assertFalse(prune(profile, facts(HealthBand.HEALTHY, true, true), CastMode.ANY_READY, 8).dropped()
                .containsKey(tactic("hold")));
    }

    @Test
    void unreachableOnFootSkipsRangedAndUntypedChoices() {
        var melee = profileOf(List.of(choice("melee", meleePurpose())), List.of(
                tacticOf("approach", Movement.APPROACH, "melee", 20),
                tacticOf("flank", Movement.FLANK, "melee", 10),
                tacticOf("hold", Movement.HOLD, null, 1),
                tacticOf("wait", Movement.HOLD, null, 0)));
        var dropped = prune(melee, facts(HealthBand.HEALTHY, false, true), CastMode.INTENT_ONLY, 8).dropped();
        assertEquals(PruneReason.UNREACHABLE_ON_FOOT, dropped.get(tactic("approach")));
        assertEquals(PruneReason.UNREACHABLE_ON_FOOT, dropped.get(tactic("flank")));
        var ranged = profileOf(List.of(choice("shot", hitscan(16))), List.of(
                tacticOf("approach", Movement.APPROACH, "shot", 20)));
        assertFalse(prune(ranged, facts(HealthBand.HEALTHY, false, true), CastMode.INTENT_ONLY, 8).dropped()
                .containsKey(tactic("approach")));
        var untyped = profileOf(List.of(SkillChoice.untyped(new ChoiceId("raw"), "Raw", Set.of(), true, 0)), List.of(
                tacticOf("approach", Movement.APPROACH, "raw", 20)));
        assertFalse(prune(untyped, facts(HealthBand.HEALTHY, false, true), CastMode.ANY_READY, 8).dropped()
                .containsKey(tactic("approach")));
        assertTrue(prune(ranged, facts(HealthBand.HEALTHY, true, true), CastMode.INTENT_ONLY, 8).dropped().isEmpty());
    }

    @Test
    void keepIsNeverRemovedAndAVetoPassThatLeavesOneOptionIsUndone() {
        var profile = profileOf(List.of(choice("defense", sustain()), choice("melee", meleePurpose())), List.of(
                tacticOf("retreat_and_defend", Movement.RETREAT, "defense", 90),
                tacticOf("press", Movement.APPROACH, "melee", 20)));
        var kept = prune(profile, facts(HealthBand.HEALTHY, true, true), CastMode.INTENT_ONLY, 8,
                tactic("retreat_and_defend"));
        assertTrue(kept.kept().contains(tactic("retreat_and_defend")));
        assertFalse(kept.dropped().containsKey(tactic("retreat_and_defend")));
        var undone = prune(profile, facts(HealthBand.HEALTHY, true, true), CastMode.INTENT_ONLY, 8);
        assertTrue(undone.vetoesUndone());
        assertEquals(2, undone.kept().size());
        assertFalse(undone.dropped().containsValue(PruneReason.SUSTAIN_WHILE_HEALTHY));
    }

    @Test
    void aCriticalHypothesisOrNoFightKeepsTheSustain() {
        var profile = profileOf(choice("defense", sustain()),
                tacticOf("retreat_and_defend", Movement.RETREAT, "defense", 90));
        var ordered = new ArrayList<>(profile.tactics().keySet());
        var hypothesis = OptionPruning.prune(ordered, profile, facts(HealthBand.HEALTHY, true, true),
                Set.of(UseCondition.SELF_LOW_HEALTH), CastMode.INTENT_ONLY, 8);
        assertFalse(hypothesis.dropped().containsKey(tactic("retreat_and_defend")));
        assertFalse(prune(profile, facts(HealthBand.HEALTHY, false, true), CastMode.INTENT_ONLY, 8).dropped()
                .containsKey(tactic("retreat_and_defend")));
        assertFalse(prune(profile, facts(HealthBand.HEALTHY, true, false), CastMode.INTENT_ONLY, 8).dropped()
                .containsKey(tactic("retreat_and_defend")));
    }

    @Test
    void criticalHealthDoesNotDropSustain() {
        var profile = profileOf(choice("defense", sustain()),
                tacticOf("retreat_and_defend", Movement.RETREAT, "defense", 90));
        assertFalse(prune(profile, facts(HealthBand.CRITICAL, true, true), CastMode.INTENT_ONLY, 8).dropped()
                .containsKey(tactic("retreat_and_defend")));
    }

    @Test
    void anInferredOrSecondarySustainIsNotASafeVeto() {
        var inferred = new SkillPurpose(SkillRole.SUSTAIN, Set.of(SkillRole.SUSTAIN), Delivery.SELF,
                OptionalDouble.empty(), OptionalDouble.empty(), 0, CooldownClass.MEDIUM, DamageTier.NONE,
                Set.of(UseCondition.SELF_LOW_HEALTH), Set.of(), false, PurposeOrigin.INFERRED);
        var inferredProfile = profileOf(choice("defense", inferred),
                tacticOf("retreat_and_defend", Movement.RETREAT, "defense", 90));
        assertFalse(prune(inferredProfile, facts(HealthBand.HEALTHY, true, true), CastMode.INTENT_ONLY, 8).dropped()
                .containsKey(tactic("retreat_and_defend")));
        var secondary = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE, SkillRole.SUSTAIN), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 0, CooldownClass.SHORT, DamageTier.LOW, Set.of(),
                Set.of(), false, PurposeOrigin.DECLARED);
        var secondaryProfile = profileOf(choice("defense", secondary),
                tacticOf("retreat_and_defend", Movement.RETREAT, "defense", 90));
        assertFalse(prune(secondaryProfile, facts(HealthBand.HEALTHY, true, true), CastMode.ANY_READY, 8).dropped()
                .containsValue(PruneReason.SUSTAIN_WHILE_HEALTHY));
    }

    @Test
    void anUntypedChoiceIsExemptFromVetoesAndFromTheMerge() {
        var raw = SkillChoice.untyped(new ChoiceId("raw"), "Raw", Set.of(), false, 0);
        var profile = profileOf(List.of(raw, choice("melee", meleePurpose())), List.of(
                tacticOf("heal", Movement.HOLD, "raw", 40),
                tacticOf("press", Movement.APPROACH, "melee", 20),
                tacticOf("also", Movement.APPROACH, "raw", 10)));
        var dropped = prune(profile, facts(HealthBand.HEALTHY, false, false), CastMode.ANY_READY, 8).dropped();
        assertFalse(dropped.containsKey(tactic("heal")));
        assertFalse(dropped.containsKey(tactic("also")));
        assertEquals(PruneReason.UNREACHABLE_ON_FOOT, dropped.get(tactic("press")));
    }

    @Test
    void aMissingChoiceAndASkillWithoutRangedReachAreUnreachableOnFoot() {
        var profile = profileOf(List.of(choice("slam", area()), choice("shot", hitscan(16))), List.of(
                tacticOf("walk", Movement.APPROACH, null, 10),
                tacticOf("circle", Movement.FLANK, "slam", 5),
                tacticOf("shoot", Movement.APPROACH, "shot", 8),
                tacticOf("hold", Movement.HOLD, null, 1)));
        var dropped = prune(profile, facts(HealthBand.WOUNDED, false, true), CastMode.INTENT_ONLY, 8).dropped();
        assertEquals(PruneReason.UNREACHABLE_ON_FOOT, dropped.get(tactic("walk")));
        assertEquals(PruneReason.UNREACHABLE_ON_FOOT, dropped.get(tactic("circle")));
        assertFalse(dropped.containsKey(tactic("shoot")));
        assertFalse(dropped.containsKey(tactic("hold")));
    }

    @Test
    void noSightDoesNotDropARetreat() {
        var sight = choice("ranged", hitscan(16, Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT)));
        var profile = profileOf(List.of(sight), List.of(
                tacticOf("retreat", Movement.RETREAT, "ranged", 10),
                tacticOf("hold", Movement.HOLD, null, 1),
                tacticOf("wait", Movement.HOLD, null, 0)));
        assertFalse(prune(profile, facts(HealthBand.HEALTHY, true, false), CastMode.ANY_READY, 8).dropped()
                .containsKey(tactic("retreat")));
    }

    @Test
    void keepDistanceMergesGapCloseOnlyBeyondMeleeAndEscapeOnlyInMelee() {
        var gap = rangedRole(SkillRole.GAP_CLOSE);
        var shot = rangedRole(SkillRole.DAMAGE);
        var escape = rangedRole(SkillRole.ESCAPE);
        var profile = profileOf(List.of(choice("shot", shot), choice("gap", gap), choice("escape", escape)), List.of(
                tacticOf("kiting", Movement.KEEP_DISTANCE, "shot", 30),
                tacticOf("lunge", Movement.KEEP_DISTANCE, "gap", 20),
                tacticOf("flee", Movement.KEEP_DISTANCE, "escape", 10)));
        var beyond = prune(profile, facts(HealthBand.HEALTHY, true, true, DistanceBand.BEYOND), CastMode.ANY_READY, 8);
        assertEquals(PruneReason.SAME_EFFECT_AS_HIGHER, beyond.dropped().get(tactic("lunge")));
        assertFalse(beyond.dropped().containsKey(tactic("flee")));
        assertTrue(beyond.kept().contains(tactic("kiting")));
        var melee = prune(profile, facts(HealthBand.HEALTHY, true, true, DistanceBand.MELEE), CastMode.ANY_READY, 8);
        assertFalse(melee.dropped().containsKey(tactic("lunge")));
        assertEquals(PruneReason.SAME_EFFECT_AS_HIGHER, melee.dropped().get(tactic("flee")));
        var ranged = prune(profile, facts(HealthBand.HEALTHY, true, true, DistanceBand.RANGED), CastMode.ANY_READY, 8);
        assertEquals(beyond.dropped(), ranged.dropped());
    }

    @Test
    void identicalMythicSkillsMergeAndAVanillaMeleeStays() {
        var melee = meleePurpose();
        var profile = profileOf(List.of(choice("a", melee), choice("b", melee),
                SkillChoice.vanillaMelee(new ChoiceId("punch"), true, 0, melee)), List.of(
                tacticOf("skill_a", Movement.APPROACH, "a", 30),
                tacticOf("skill_b", Movement.APPROACH, "b", 20),
                tacticOf("punch_in", Movement.APPROACH, "punch", 10)));
        var result = prune(profile, facts(HealthBand.WOUNDED, true, true), CastMode.ANY_READY, 8);
        assertEquals(PruneReason.SAME_EFFECT_AS_HIGHER, result.dropped().get(tactic("skill_b")));
        assertFalse(result.dropped().containsKey(tactic("punch_in")));
        assertTrue(result.kept().contains(tactic("skill_a")));
    }

    @Test
    void aHypotheticalNowReordersTheCapAndOnlyThePassedListIsPartitioned() {
        var profile = profileOf(List.of(choice("melee", meleePurpose()), choice("plain", plainMelee())), List.of(
                tacticOf("alpha", Movement.APPROACH, "melee", 10),
                tacticOf("beta", Movement.HOLD, "plain", 10),
                tacticOf("gamma", Movement.RETREAT, "plain", 25)));
        var calm = prune(profile, facts(HealthBand.WOUNDED, true, true), CastMode.INTENT_ONLY, 2);
        assertEquals(List.of(tactic("gamma"), tactic("alpha")), calm.kept());
        var ordered = List.of(tactic("alpha"), tactic("beta"), tactic("gamma"));
        var pressed = OptionPruning.prune(ordered, profile, facts(HealthBand.WOUNDED, true, true),
                Set.of(UseCondition.TARGET_IN_MELEE), CastMode.INTENT_ONLY, 2);
        assertEquals(List.of(tactic("alpha"), tactic("gamma")), pressed.kept());
        assertPartition(ordered, pressed);
        var contingency = List.of(tactic("alpha"), tactic("beta"));
        var hypothesis = OptionPruning.prune(contingency, profile, facts(HealthBand.CRITICAL, true, true),
                Set.of(UseCondition.SELF_LOW_HEALTH), CastMode.ANY_READY, 8);
        assertEquals(contingency, hypothesis.kept());
        assertPartition(contingency, hypothesis);
    }

    @Test
    void safePruningIsNotTheDefault() {
        assertEquals(PruningMode.OFF, PruningMode.DEFAULT);
    }

    @Test
    void anyReadyMergesTheApproachGroupAndIntentOnlyDoesNot() {
        var profile = guardianApproaches();
        var merged = prune(profile, facts(HealthBand.WOUNDED, true, true), CastMode.ANY_READY, 8);
        assertTrue(merged.kept().contains(tactic("press_and_strike")));
        assertEquals(PruneReason.SAME_EFFECT_AS_HIGHER, merged.dropped().get(tactic("close_in_and_slam")));
        assertEquals(PruneReason.SAME_EFFECT_AS_HIGHER, merged.dropped().get(tactic("approach")));
        assertTrue(merged.kept().contains(tactic("flank_and_strike")));
        var described = withDescription(profile, "close_in_and_slam", "Slam when they bunch up");
        assertFalse(prune(described, facts(HealthBand.WOUNDED, true, true), CastMode.ANY_READY, 8).dropped()
                .containsKey(tactic("close_in_and_slam")));
        var only = prune(profile, facts(HealthBand.WOUNDED, true, true), CastMode.INTENT_ONLY, 8);
        assertFalse(only.dropped().containsKey(tactic("close_in_and_slam")));
        var kept = prune(profile, facts(HealthBand.WOUNDED, true, true), CastMode.ANY_READY, 8, tactic("approach"));
        assertTrue(kept.kept().contains(tactic("approach")));
        assertEquals(PruneReason.SAME_EFFECT_AS_HIGHER, kept.dropped().get(tactic("press_and_strike")));
    }

    @Test
    void vanillaMeleeDoesNotMerge() {
        var melee = SkillChoice.vanillaMelee(new ChoiceId("punch"), true, 0, meleePurpose());
        var skill = choice("slam", area());
        var profile = profileOf(List.of(melee, skill), List.of(
                tacticOf("punch_in", Movement.APPROACH, "punch", 40),
                tacticOf("slam_in", Movement.APPROACH, "slam", 30)));
        var dropped = prune(profile, facts(HealthBand.WOUNDED, true, true), CastMode.ANY_READY, 8).dropped();
        assertFalse(dropped.containsKey(tactic("punch_in")));
        assertFalse(dropped.containsKey(tactic("slam_in")));
    }

    @Test
    void theCapBreaksScoreTiesByDeclarationOrderAndKeepsTheCurrentTactic() {
        var profile = profileOf(List.of(choice("a", meleePurpose()), choice("b", meleePurpose()),
                choice("c", meleePurpose())), List.of(
                tacticOf("first", Movement.APPROACH, "a", 10),
                tacticOf("second", Movement.HOLD, "b", 10),
                tacticOf("third", Movement.RETREAT, "c", 50)));
        var result = prune(profile, facts(HealthBand.WOUNDED, true, true), CastMode.INTENT_ONLY, 2);
        assertEquals(List.of(tactic("third"), tactic("first")), result.kept());
        assertEquals(PruneReason.BELOW_OPTION_CAP, result.dropped().get(tactic("second")));
        var kept = prune(profile, facts(HealthBand.WOUNDED, true, true), CastMode.INTENT_ONLY, 2, tactic("second"));
        assertTrue(kept.kept().contains(tactic("second")));
    }

    @Test
    void aCorneredRetreatIsDroppedOnlyInFullWithARawRetreatMiss() {
        var profile = profileOf(List.of(choice("escape", escape()), choice("melee", meleePurpose())), List.of(
                tacticOf("fall_back", Movement.RETREAT, "escape", 30),
                tacticOf("press", Movement.APPROACH, "melee", 20),
                tacticOf("hold", Movement.HOLD, null, 10)));
        var ordered = new ArrayList<>(profile.tactics().keySet());
        var cornered = facts(HealthBand.WOUNDED, true, true).withZone(Set.of(), true, 0L);
        var free = facts(HealthBand.WOUNDED, true, true).withZone(Set.of(), false, 0L);
        assertEquals(PruneReason.CORNERED_RETREAT, OptionPruning.prune(ordered, profile, cornered, Set.of(),
                CastMode.ANY_READY, 8, tactic("hold"), ZoneMode.FULL).dropped().get(tactic("fall_back")));
        for (var mode : new ZoneMode[] {ZoneMode.OFF, ZoneMode.OBSERVE, ZoneMode.GUARD}) {
            assertFalse(OptionPruning.prune(ordered, profile, cornered, Set.of(), CastMode.ANY_READY, 8,
                    tactic("hold"), mode).dropped().containsKey(tactic("fall_back")), mode.name());
        }
        assertFalse(OptionPruning.prune(ordered, profile, free, Set.of(), CastMode.ANY_READY, 8, tactic("hold"),
                ZoneMode.FULL).dropped().containsKey(tactic("fall_back")));
        assertFalse(OptionPruning.prune(ordered, profile, cornered, Set.of(), CastMode.ANY_READY, 8,
                tactic("fall_back"), ZoneMode.FULL).dropped().containsKey(tactic("fall_back")));
    }

    @Test
    void keptAndDroppedPartitionTheEligibleList() {
        var profile = guardianApproaches();
        var ordered = List.of(tactic("press_and_strike"), tactic("close_in_and_slam"), tactic("approach"),
                tactic("flank_and_strike"));
        var result = OptionPruning.prune(ordered, profile, facts(HealthBand.HEALTHY, false, false), Set.of(),
                CastMode.ANY_READY, 8);
        assertPartition(ordered, result);
    }

    private static void assertPartition(List<TacticId> ordered, OptionPruning.PruneResult result) {
        var union = new LinkedHashSet<>(result.kept());
        union.addAll(result.dropped().keySet());
        assertEquals(new LinkedHashSet<>(ordered), union);
        assertTrue(result.kept().stream().noneMatch(result.dropped()::containsKey));
        assertTrue(ordered.containsAll(result.kept()));
    }

    private static OptionPruning.PruneResult prune(TacticalProfile profile, MobFacts mob, CastMode mode, int cap) {
        return OptionPruning.prune(new ArrayList<>(profile.tactics().keySet()), profile, mob, Set.of(), mode, cap);
    }

    private static OptionPruning.PruneResult prune(TacticalProfile profile, MobFacts mob, CastMode mode, int cap,
                                                   TacticId keep) {
        return OptionPruning.prune(new ArrayList<>(profile.tactics().keySet()), profile, mob, Set.of(), mode, cap, keep);
    }

    private static TacticalProfile guardianApproaches() {
        return profileOf(List.of(choice("melee", meleePurpose()), choice("slam", area())), List.of(
                tacticOf("press_and_strike", Movement.APPROACH, "melee", 70),
                tacticOf("close_in_and_slam", Movement.APPROACH, "slam", 65),
                tacticOf("approach", Movement.APPROACH, null, 0),
                tacticOf("flank_and_strike", Movement.FLANK, "melee", 40)));
    }

    private static TacticalProfile withDescription(TacticalProfile profile, String id, String description) {
        var tactics = new java.util.LinkedHashMap<>(profile.tactics());
        var tactic = tactics.get(new TacticId(id));
        tactics.put(tactic.id(), new Tactic(tactic.id(), tactic.movement(), tactic.choice(), Optional.of(description),
                tactic.priority()));
        return profile(new ArrayList<>(tactics.values()), new ArrayList<>(profile.choices().values()), List.of(),
                anyReady());
    }

    private static TacticalProfile profileOf(SkillChoice choice, Tactic tactic) {
        return profileOf(List.of(choice), List.of(tactic, tacticOf("hold", Movement.HOLD, null, 1),
                tacticOf("approach", Movement.APPROACH, null, 2)));
    }

    private static TacticalProfile profileOf(List<SkillChoice> choices, List<Tactic> tactics) {
        return profile(tactics, choices, List.of(), anyReady());
    }

    private static DecisionTiming anyReady() {
        return DomainFixtures.timing().withCasting(CastMode.ANY_READY, 0);
    }

    private static SkillChoice choice(String id, SkillPurpose purpose) {
        return SkillChoice.typed(new ChoiceId(id), "Skill_" + id, Set.of(), true, 0, purpose);
    }

    private static Tactic tacticOf(String id, Movement movement, String choice, int priority) {
        return new Tactic(new TacticId(id), movement, Optional.ofNullable(choice).map(ChoiceId::new), Optional.empty(),
                priority);
    }

    private static SkillPurpose sustain() {
        return new SkillPurpose(SkillRole.SUSTAIN, Set.of(SkillRole.SUSTAIN), Delivery.SELF, OptionalDouble.empty(),
                OptionalDouble.empty(), 0, CooldownClass.MEDIUM, DamageTier.NONE, Set.of(UseCondition.SELF_LOW_HEALTH),
                Set.of(), false, PurposeOrigin.DECLARED);
    }

    private static SkillPurpose escape() {
        return new SkillPurpose(SkillRole.ESCAPE, Set.of(SkillRole.ESCAPE), Delivery.SELF, OptionalDouble.empty(),
                OptionalDouble.empty(), 0, CooldownClass.MEDIUM, DamageTier.NONE, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
    }

    private static SkillPurpose plainMelee() {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(2.5),
                OptionalDouble.empty(), 5, CooldownClass.SHORT, DamageTier.MEDIUM, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
    }

    private static SkillPurpose rangedRole(SkillRole role) {
        return new SkillPurpose(role, Set.of(role), Delivery.PROJECTILE, OptionalDouble.of(16), OptionalDouble.empty(),
                0, CooldownClass.SHORT, DamageTier.LOW, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
    }

    private static SkillPurpose meleePurpose() {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(2.5),
                OptionalDouble.empty(), 5, CooldownClass.SHORT, DamageTier.MEDIUM,
                Set.of(UseCondition.TARGET_IN_MELEE), Set.of(), false, PurposeOrigin.DECLARED);
    }

    private static SkillPurpose hitscan(double reach) {
        return hitscan(reach, Set.of());
    }

    private static SkillPurpose hitscan(double reach, Set<SkillRisk> risks) {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.HITSCAN, OptionalDouble.of(reach),
                OptionalDouble.empty(), 10, CooldownClass.SHORT, DamageTier.LOW,
                Set.of(UseCondition.TARGET_AT_RANGE), risks, false, PurposeOrigin.SKILL);
    }

    private static SkillPurpose area() {
        return new SkillPurpose(SkillRole.CONTROL, Set.of(SkillRole.CONTROL, SkillRole.DAMAGE),
                Delivery.AREA_AROUND_SELF, OptionalDouble.empty(), OptionalDouble.of(3), 15, CooldownClass.MEDIUM,
                DamageTier.MEDIUM, Set.of(UseCondition.ENEMIES_GROUPED), Set.of(SkillRisk.ROOTS_CASTER), false,
                PurposeOrigin.DECLARED);
    }

    private static MobFacts facts(HealthBand health, boolean reachable, boolean visible) {
        return facts(health, reachable, visible, Set.of(), DistanceBand.MELEE);
    }

    private static MobFacts facts(HealthBand health, boolean reachable, boolean visible, Set<ChoiceId> outOfReach) {
        return facts(health, reachable, visible, outOfReach, DistanceBand.MELEE);
    }

    private static MobFacts facts(HealthBand health, boolean reachable, boolean visible, DistanceBand distance) {
        return facts(health, reachable, visible, Set.of(), distance);
    }

    private static MobFacts facts(HealthBand health, boolean reachable, boolean visible, Set<ChoiceId> outOfReach,
                                  DistanceBand distance) {
        var relation = new TargetRelation(new ParticipantAlias(1), distance, Trend.CLOSING, reachable, visible, false,
                true, false);
        return new MobFacts(MOB, health, Optional.of(relation), Set.of(), Set.of(), Set.of(), outOfReach);
    }
}
