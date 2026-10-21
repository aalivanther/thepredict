package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.adapter.jev.OptionTextDedup.Discriminator;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OptionContext.FitReason;
import akyrion.thepredict.domain.OptionContext.OptionFit;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.UseCondition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The criterion of one option at each detail (M16.1, M16.2): exact forms, caps, order of the cuts and keys. */
class CriterionRendererTest {
    private static final OptionFit SHIELD = new OptionFit(List.of(), List.of(FitReason.SHIELD_BLOCKS_IT));

    private static TacticalProfile example() {
        return ScenarioFacts.example();
    }

    private static String render(TacticalProfile profile, String tactic, QuestionDetail detail, OptionFit fit,
                                 Discriminator... discriminators) {
        return CriterionRenderer.render(profile.tactic(new TacticId(tactic)).orElseThrow(), profile, detail, fit,
                discriminators.length == 0 ? Set.of() : EnumSet.of(discriminators[0], discriminators)).text();
    }

    @Test
    void theCompactExampleOfTheDesignIsWrittenExactly() {
        var profile = example();
        assertEquals("close in; melee hit, medium damage; poor fit now: shield would block it",
                render(profile, "press_and_strike", QuestionDetail.COMPACT, SHIELD));
        assertEquals("circle to the target's side; melee hit, medium damage",
                render(profile, "flank_and_strike", QuestionDetail.COMPACT, OptionFit.NONE));
        assertEquals("retreat; self heal", render(profile, "retreat_and_defend", QuestionDetail.COMPACT, OptionFit.NONE));
        assertEquals("close in", render(profile, "approach", QuestionDetail.COMPACT, OptionFit.NONE));
        assertEquals("close in; area hit around self stun or slow, medium damage, slow wind-up",
                render(profile, "close_in_and_slam", QuestionDetail.COMPACT, OptionFit.NONE));
    }

    @Test
    void anUntypedChoiceOmitsTheSkillClause() {
        var base = JevFixtures.guardian();
        var choices = new LinkedHashMap<>(base.choices());
        var volley = choices.get(new ChoiceId("volley")).withoutPurpose();
        choices.put(volley.id(), volley);
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), choices,
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing());
        var tactic = profile.tactic(new TacticId("keep_range")).orElseThrow();
        var compact = CriterionRenderer.render(tactic, profile, QuestionDetail.COMPACT, OptionFit.NONE, Set.of()).text();
        assertEquals("hold mid range", compact);
        assertFalse(compact.contains("skill"));
        var full = JsonParser.parseString(CriterionRenderer.render(tactic, profile, QuestionDetail.FULL, OptionFit.NONE,
                Set.of()).text()).getAsJsonObject();
        assertFalse(full.has("skill"));
        assertEquals("stay between the inner and outer distance band", full.get("movement").getAsString());
        assertEquals("Shoot while the target cannot reach you", full.get("note").getAsString());
    }

    @Test
    void everyShortFormOfTheTablesHasItsPhrase() {
        assertEquals("hold mid range", SemanticPhrases.movementShort(Movement.KEEP_DISTANCE));
        assertEquals("native movement", SemanticPhrases.movementShort(Movement.NONE));
        assertEquals("instant ranged hit", SemanticPhrases.deliveryShort(Delivery.HITSCAN));
        assertEquals("cone sweep", SemanticPhrases.deliveryShort(Delivery.CONE));
        assertEquals("", SemanticPhrases.roleShort(SkillRole.DAMAGE));
        assertEquals("weakens target", SemanticPhrases.roleShort(SkillRole.DEBUFF));
        assertEquals("damage", SemanticPhrases.damageShort(DamageTier.UNRATED));
        assertEquals("", SemanticPhrases.damageShort(DamageTier.NONE));
        assertEquals("", SemanticPhrases.windUpShort(10));
        assertEquals("slow wind-up", SemanticPhrases.windUpShort(11));
        assertEquals("too risky at critical health", SemanticPhrases.poorFitShort(FitReason.EXPOSED_WHILE_CRITICAL));
        assertEquals("", SemanticPhrases.poorFitShort(FitReason.FLANK_BEATS_SHIELD));
        assertEquals("the target's shield does not cover its sides", SemanticPhrases.fitReason(
                FitReason.FLANK_BEATS_SHIELD));
        assertEquals("target nearly dead", SemanticPhrases.useConditionShort(UseCondition.TARGET_LOW_HEALTH));
    }

    @Test
    void aCompactCriterionOverOneHundredTwentyDropsTheWindUpAndThenTheDamage() {
        var summoner = profile(Movement.FLANK, purpose(SkillRole.SUMMON, Delivery.AREA_AROUND_SELF, DamageTier.HIGH, 40,
                PurposeOrigin.DECLARED), Optional.empty());
        assertEquals("circle to the target's side; area hit around self summons allies, high damage, slow wind-up",
                render(summoner, "option", QuestionDetail.COMPACT, OptionFit.NONE));
        assertEquals("circle to the target's side; area hit around self summons allies, high damage; poor fit now:"
                + " caster already healthy", render(summoner, "option", QuestionDetail.COMPACT, new OptionFit(List.of(),
                List.of(FitReason.CASTER_ALREADY_HEALTHY))));
        var cut = render(summoner, "option", QuestionDetail.COMPACT, new OptionFit(List.of(),
                List.of(FitReason.EXPOSED_WHILE_CRITICAL)));
        assertEquals("circle to the target's side; area hit around self summons allies; poor fit now: too risky at"
                + " critical health", cut);
        assertTrue(cut.length() <= CriterionRenderer.COMPACT_CHARS, cut);
        var inferred = profile(Movement.FLANK, purpose(SkillRole.SUMMON, Delivery.AREA_AROUND_SELF, DamageTier.HIGH, 40,
                PurposeOrigin.INFERRED), Optional.empty());
        assertFalse(render(inferred, "option", QuestionDetail.COMPACT, OptionFit.NONE).contains("wind-up"),
                "the wind-up is said only for a purpose written by the author");
    }

    @Test
    void aDiscriminatorIsNeverCutAndACollisionMayGrowToOneHundredSixty() {
        var worst = profile(Movement.FLANK, purpose(SkillRole.SUMMON, Delivery.AREA_AROUND_SELF, DamageTier.NONE, 0,
                PurposeOrigin.DECLARED), Optional.of("Keep the whole altar room under pressure while the others flank"));
        var text = render(worst, "option", QuestionDetail.COMPACT, new OptionFit(List.of(),
                List.of(FitReason.EXPOSED_WHILE_CRITICAL)), Discriminator.NOTE_EXCERPT, Discriminator.REACH_BAND,
                Discriminator.BEST_WHEN);
        assertTrue(text.length() <= CriterionRenderer.COLLISION_CHARS, text.length() + " " + text);
        assertTrue(text.contains("; note: Keep the whole altar room under"), text);
        assertTrue(text.contains("; reach: melee"), text);
        assertTrue(text.contains("; best when caster nearly dead"), text);
        var base = "circle to the target's side; area hit around self summons allies";
        assertEquals(64, base.length());
        assertTrue(base.length() + "; note: ".length() + CriterionRenderer.MIN_EXCERPT_CHARS + "; reach: ranged".length()
                + "; best when caster nearly dead".length() < CriterionRenderer.COLLISION_CHARS);
    }

    @Test
    void theExcerptIsCutAtASpaceWithinForty() {
        assertEquals("Stay outside melee reach and shoot while", CriterionRenderer.excerpt(
                "Stay outside melee reach and shoot while the target approaches", 40));
        assertEquals("Short note", CriterionRenderer.excerpt("Short note", 40));
        assertEquals("abcdefghijkl", CriterionRenderer.excerpt("abcdefghijklmnop", 12));
        assertEquals("Stay outside", CriterionRenderer.excerpt("Stay outside melee reach", 15));
    }

    @Test
    void theStructuredCriterionHasItsKeysInAFixedOrderAndLeavesOutTheEmptyOnes() {
        var profile = example();
        var fit = new OptionFit(List.of(FitReason.BEST_WHEN_TARGET_IN_MELEE, FitReason.FLANK_BEATS_SHIELD),
                List.of());
        var full = CriterionRenderer.render(profile.tactic(new TacticId("flank_and_strike")).orElseThrow(), profile,
                QuestionDetail.FULL, fit, Set.of()).json().getAsJsonObject();
        assertEquals(List.of("movement", "skill", "best_when", "fits_now"), List.copyOf(full.keySet()));
        assertEquals("circle to the side of the target before striking", full.get("movement").getAsString());
        assertEquals("strikes the target up close to deal damage, only inside melee reach, after a short wind-up,"
                + " deals medium damage", full.get("skill").getAsString());
        assertEquals("[\"the target is inside melee reach\",\"the target's shield does not cover its sides\"]",
                full.get("fits_now").toString());
        var standard = CriterionRenderer.render(profile.tactic(new TacticId("keep_distance_and_shoot")).orElseThrow(),
                profile, QuestionDetail.STANDARD, SHIELD, Set.of()).json().getAsJsonObject();
        assertEquals(List.of("movement", "skill", "best_when", "risks", "note"), List.copyOf(standard.keySet()));
        assertFalse(standard.has("poor_fit_now"), "standard never says what fits now");
        assertEquals("Stay outside melee reach and shoot while the target approaches",
                standard.get("note").getAsString());
        var plain = CriterionRenderer.render(profile.tactic(new TacticId("hold")).orElseThrow(), profile,
                QuestionDetail.STANDARD, OptionFit.NONE, Set.of()).json().getAsJsonObject();
        assertEquals(List.of("movement"), List.copyOf(plain.keySet()));
    }

    @Test
    void aShieldPiercingSkillSaysItAmongItsRisks() {
        var piercing = profile(Movement.APPROACH, new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE),
                Delivery.MELEE, OptionalDouble.of(2.5), OptionalDouble.empty(), 5, CooldownClass.SHORT,
                DamageTier.MEDIUM, Set.of(), Set.of(SkillRisk.ROOTS_CASTER), true, PurposeOrigin.DECLARED),
                Optional.empty());
        JsonObject criterion = CriterionRenderer.render(piercing.tactic(new TacticId("option")).orElseThrow(), piercing,
                QuestionDetail.STANDARD, OptionFit.NONE, Set.of()).json().getAsJsonObject();
        assertEquals("[\"the caster cannot move while using it\",\"goes through shields\"]",
                criterion.get("risks").toString());
    }

    @Test
    void theLongestFullOptionIsMeasuredAndTheExampleStaysUnderNineHundred() {
        var worst = JevFixtures.worstCaseProfile(1);
        var fit = new OptionFit(List.of(FitReason.BEST_WHEN_ENEMIES_GROUPED, FitReason.BEST_WHEN_SELF_LOW_HEALTH,
                FitReason.BEST_WHEN_TARGET_SHIELDING), List.of(FitReason.EXPOSED_WHILE_CRITICAL,
                FitReason.CANNOT_REACH_ON_FOOT, FitReason.OUT_OF_REACH));
        assertEquals(QuestionBuilderTest.MAX_FULL_OPTION_CHARS, CriterionRenderer.render(worst.tactic(
                new TacticId("worst_case_000")).orElseThrow(), worst, QuestionDetail.FULL, fit, Set.of()).text()
                .length());
        var profile = example();
        for (var tactic : profile.tactics().values()) {
            var text = CriterionRenderer.render(tactic, profile, QuestionDetail.FULL, new OptionFit(
                    List.of(FitReason.FLANK_BEATS_SHIELD, FitReason.GOES_THROUGH_SHIELD, FitReason.BEST_WHEN_TARGET_IN_MELEE),
                    List.of(FitReason.EXPOSED_WHILE_CRITICAL, FitReason.CANNOT_REACH_ON_FOOT, FitReason.OUT_OF_REACH)),
                    Set.of()).text();
            assertTrue(text.length() <= 900, tactic.id() + ": " + text.length());
        }
    }

    @Test
    void noCriterionCarriesANumberNorAChoiceName() {
        var profile = example();
        for (var detail : QuestionDetail.values()) {
            for (var tactic : profile.tactics().values()) {
                var text = CriterionRenderer.render(tactic, profile, detail, SHIELD, EnumSet.allOf(Discriminator.class))
                        .text();
                assertFalse(text.matches(".*[0-9].*"), detail + " " + tactic.id() + ": " + text);
                for (var choice : List.of("defense", "slam")) {
                    assertFalse(text.contains(choice), choice + " in " + text);
                }
            }
        }
    }

    private static SkillPurpose purpose(SkillRole primary, Delivery delivery, DamageTier damage, int windUp,
                                        PurposeOrigin origin) {
        var reach = delivery == Delivery.MELEE || delivery == Delivery.CONE ? OptionalDouble.of(2.5)
                : OptionalDouble.empty();
        var radius = delivery == Delivery.AREA_AROUND_SELF || delivery == Delivery.AREA_AT_TARGET
                ? OptionalDouble.of(3) : OptionalDouble.empty();
        var roles = damage == DamageTier.NONE ? Set.of(primary) : Set.of(primary, SkillRole.DAMAGE);
        return new SkillPurpose(primary, roles, delivery, reach, radius, windUp, CooldownClass.SHORT, damage,
                Set.of(UseCondition.SELF_LOW_HEALTH), Set.of(SkillRisk.ROOTS_CASTER), false, origin);
    }

    private static TacticalProfile profile(Movement movement, SkillPurpose purpose, Optional<String> description) {
        var base = example();
        var choice = new SkillChoice(new ChoiceId("probe"), ChoiceKind.MYTHIC_SKILL, Optional.of("Probe"), Set.of(),
                true, 0, purpose);
        var tactics = new LinkedHashMap<>(base.tactics());
        tactics.put(new TacticId("option"), new Tactic(new TacticId("option"), movement, Optional.of(choice.id()),
                description, 50));
        var choices = new LinkedHashMap<>(base.choices());
        choices.put(choice.id(), choice);
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), tactics, choices, base.contexts(),
                base.movement(), base.health(), base.threat(), base.baseAi(), base.timing());
    }
}
