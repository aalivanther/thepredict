package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentionVocabularyTest {
    @Test
    void anEmptyLineIsAbsent() {
        assertInstanceOf(IntentionParse.Absent.class, IntentionVocabulary.parse(List.of()));
        assertInstanceOf(IntentionParse.Absent.class, IntentionVocabulary.parse(List.of(" ", "")));
    }

    @Test
    void theMinimalLineIsADamagingMeleeHitWithoutTier() {
        var draft = parsed("DAMAGE", "MELEE").draft();
        assertEquals(SkillRole.DAMAGE, draft.primary());
        assertEquals(Delivery.MELEE, draft.delivery());
        assertEquals(Optional.empty(), draft.declaredDamage());
        assertEquals(Set.of(), draft.bestWhen());
        assertEquals(Set.of(), draft.risks());
    }

    @Test
    void eachPrimitiveIsReadInItsFamily() {
        var draft = parsed("CONTROL", "DAMAGE", "AREA_AROUND_SELF", "DAMAGE_HIGH", "WHEN_ENEMIES_GROUPED",
                "RISK_ROOTS_CASTER", "IGNORES_SHIELD").draft();
        assertEquals(SkillRole.CONTROL, draft.primary());
        assertEquals(Set.of(SkillRole.CONTROL, SkillRole.DAMAGE), draft.roles());
        assertEquals(Delivery.AREA_AROUND_SELF, draft.delivery());
        assertEquals(Optional.of(DamageTier.HIGH), draft.declaredDamage());
        assertEquals(Set.of(UseCondition.ENEMIES_GROUPED), draft.bestWhen());
        assertEquals(Set.of(SkillRisk.ROOTS_CASTER), draft.risks());
        assertTrue(draft.ignoresShield());
    }

    @Test
    void theFirstRoleIsThePrimaryOne() {
        assertEquals(SkillRole.FINISHER, parsed("FINISHER", "DAMAGE", "MELEE").draft().primary());
        assertEquals(SkillRole.DAMAGE, parsed("DAMAGE", "FINISHER", "MELEE").draft().primary());
    }

    @Test
    void caseSpacesDashesAndTheCommaTextReadTheSame() {
        var expected = parsed("DAMAGE", "MELEE", "WHEN_TARGET_IN_MELEE").draft();
        assertEquals(expected, parsed(" damage ", "Melee", "when-target in   melee").draft());
        assertEquals(expected, parsed(" damage , melee , when_target_in_melee".split(",")).draft());
    }

    @Test
    void anUnknownPrimitiveIsIgnoredWithItsWarningAndAFamilyPrefixMakesItFuture() {
        var parse = parsed("DAMAGE", "FOO", "MELEE", "WHEN_TARGET_BURNING", "RISK_X", "DAMAGE_EXTREME");
        assertEquals(List.of(new IntentionIssue(IntentionIssueKind.UNKNOWN_TOKEN, 1, "FOO"),
                new IntentionIssue(IntentionIssueKind.FUTURE_TOKEN, 3, "WHEN_TARGET_BURNING"),
                new IntentionIssue(IntentionIssueKind.FUTURE_TOKEN, 4, "RISK_X"),
                new IntentionIssue(IntentionIssueKind.FUTURE_TOKEN, 5, "DAMAGE_EXTREME")), parse.warnings());
        assertEquals(Delivery.MELEE, parse.draft().delivery());
    }

    @Test
    void aRepeatedPrimitiveIsIgnoredWithItsWarning() {
        var parse = parsed("DAMAGE", "MELEE", "damage");
        assertEquals(List.of(new IntentionIssue(IntentionIssueKind.REPEATED_TOKEN, 2, "DAMAGE")), parse.warnings());
        assertEquals(Set.of(SkillRole.DAMAGE), parse.draft().roles());
    }

    @Test
    void anInjectedAliasIsTakenAsItsSubstituteWithAWarning() {
        var parse = IntentionVocabulary.parse(List.of("HURT", "MELEE"), Map.of("HURT", IntentionToken.DAMAGE));
        var result = assertInstanceOf(IntentionParse.Parsed.class, parse);
        assertEquals(SkillRole.DAMAGE, result.draft().primary());
        assertEquals(List.of(new IntentionIssue(IntentionIssueKind.ALIAS_USED, 0, "HURT")), result.warnings());
        assertEquals(Map.of(), IntentionVocabulary.ALIASES);
    }

    @Test
    void threeRolesAreValidAndFourAreNot() {
        parsed("DAMAGE", "CONTROL", "DEBUFF", "MELEE");
        assertEquals(List.of(new IntentionIssue(IntentionIssueKind.TOO_MANY_ROLES, 3, "ZONE")),
                invalid("DAMAGE", "CONTROL", "DEBUFF", "ZONE", "MELEE").errors());
    }

    @Test
    void fourBestWhenAreValidAndFiveAreNot() {
        parsed("DAMAGE", "MELEE", "WHEN_TARGET_LOW_HEALTH", "WHEN_TARGET_SHIELDING", "WHEN_TARGET_FLEEING",
                "WHEN_SELF_LOW_HEALTH");
        assertEquals(IntentionIssueKind.TOO_MANY_BEST_WHEN, invalid("DAMAGE", "MELEE", "WHEN_TARGET_LOW_HEALTH",
                "WHEN_TARGET_SHIELDING", "WHEN_TARGET_FLEEING", "WHEN_SELF_LOW_HEALTH", "WHEN_ENEMIES_GROUPED")
                .errors().getFirst().kind());
    }

    @Test
    void fourRisksAreValidAndFiveAreNot() {
        parsed("DAMAGE", "MELEE", "RISK_LEAVES_CASTER_EXPOSED", "RISK_ROOTS_CASTER", "RISK_SELF_DAMAGE",
                "RISK_NEEDS_LINE_OF_SIGHT");
        assertEquals(IntentionIssueKind.TOO_MANY_RISKS, invalid("DAMAGE", "MELEE", "RISK_LEAVES_CASTER_EXPOSED",
                "RISK_ROOTS_CASTER", "RISK_SELF_DAMAGE", "RISK_NEEDS_LINE_OF_SIGHT", "RISK_HITS_ALLIES")
                .errors().getFirst().kind());
    }

    @Test
    void theOtherErrorsMakeTheWholeLineInvalid() {
        assertEquals(List.of(IntentionIssue.line(IntentionIssueKind.NO_ROLE)), invalid("MELEE").errors());
        assertEquals(List.of(IntentionIssue.line(IntentionIssueKind.NO_DELIVERY)), invalid("DAMAGE").errors());
        assertEquals(IntentionIssueKind.MORE_THAN_ONE_DELIVERY, invalid("DAMAGE", "MELEE", "CONE").errors()
                .getFirst().kind());
        assertEquals(IntentionIssueKind.MORE_THAN_ONE_DAMAGE, invalid("DAMAGE", "MELEE", "DAMAGE_LOW", "DAMAGE_HIGH")
                .errors().getFirst().kind());
        assertEquals(List.of(new IntentionIssue(IntentionIssueKind.SHIELD_CONTRADICTION, 2, "IGNORES_SHIELD")),
                invalid("DAMAGE", "MELEE", "IGNORES_SHIELD", "RISK_BLOCKED_BY_SHIELD").errors());
        assertEquals(List.of(IntentionIssue.line(IntentionIssueKind.NO_ROLE),
                IntentionIssue.line(IntentionIssueKind.NO_DELIVERY)), invalid("FOO").errors());
    }

    @Test
    void sixteenRawPrimitivesAreReadAndSeventeenAreTooLong() {
        var sixteen = new ArrayList<>(List.of("DAMAGE", "CONTROL", "DEBUFF", "MELEE", "DAMAGE_LOW",
                "WHEN_TARGET_LOW_HEALTH", "WHEN_TARGET_SHIELDING", "WHEN_TARGET_FLEEING", "WHEN_SELF_LOW_HEALTH",
                "RISK_LEAVES_CASTER_EXPOSED", "RISK_ROOTS_CASTER", "RISK_SELF_DAMAGE", "RISK_HITS_ALLIES",
                "IGNORES_SHIELD", "FOO", "BAR"));
        assertEquals(16, sixteen.size());
        assertEquals(14, sixteen.size() - 2);
        assertEquals(15, IntentionVocabulary.MAX_VALID_TOKENS);
        assertEquals(2, parsed(sixteen.toArray(String[]::new)).warnings().size());
        sixteen.add("BAZ");
        assertEquals(List.of(IntentionIssue.line(IntentionIssueKind.TOO_LONG)),
                invalid(sixteen.toArray(String[]::new)).errors());
    }

    @Test
    void fourHundredCharactersAreReadAndFourHundredAndOneAreTooLong() {
        var padding = 400 - "DAMAGE,MELEE".length();
        parsed("DAMAGE", "MELEE" + " ".repeat(padding));
        assertEquals(IntentionIssueKind.TOO_LONG, invalid("DAMAGE", "MELEE" + " ".repeat(padding + 1)).errors()
                .getFirst().kind());
    }

    @Test
    void renderGivesTheCanonicalOrderSoAnotherOrderIsReadAndWrittenTheSame() {
        var draft = parsed("RISK_ROOTS_CASTER", "WHEN_TARGET_IN_MELEE", "DAMAGE_MEDIUM", "AREA_AROUND_SELF",
                "DAMAGE", "CONTROL", "WHEN_ENEMIES_GROUPED").draft();
        assertEquals("DAMAGE, CONTROL, AREA_AROUND_SELF, DAMAGE_MEDIUM, WHEN_ENEMIES_GROUPED, WHEN_TARGET_IN_MELEE,"
                + " RISK_ROOTS_CASTER", IntentionVocabulary.text(IntentionVocabulary.render(draft)));
    }

    @Test
    void aDeclaredAnswerEndsTheLineAndSurvivesTheRoundTrip() {
        var draft = parsed("DAMAGE", "MELEE", "DAMAGE_MEDIUM").draft();
        for (var answer : TellAnswer.values()) {
            var tokens = IntentionVocabulary.render(draft, answer);
            assertEquals(IntentionToken.of(answer), tokens.getLast());
            assertEquals(IntentionVocabulary.render(draft), tokens.subList(0, tokens.size() - 1));
            var read = parsed(IntentionVocabulary.text(tokens).split(", "));
            assertEquals(Optional.of(answer), read.answer());
            assertEquals(draft, read.draft());
            assertEquals(List.of(), read.warnings());
        }
        assertEquals(Optional.empty(), parsed(IntentionVocabulary.text(IntentionVocabulary.render(draft)).split(", "))
                .answer());
    }

    @Test
    void everyGeneratedValidDraftSurvivesTheRoundTripWithoutWarnings() {
        var tiers = List.of(Optional.<DamageTier>empty(), Optional.of(DamageTier.LOW), Optional.of(DamageTier.MEDIUM),
                Optional.of(DamageTier.HIGH));
        var conditions = subsetsUpToTwo(UseCondition.values());
        var risks = subsetsUpToTwo(SkillRisk.values());
        var checked = 0;
        for (var role : SkillRole.values()) {
            for (var delivery : Delivery.values()) {
                for (var tier : tiers) {
                    for (var when : conditions) {
                        for (var risk : risks) {
                            assertRoundTrip(new IntentionDraft(role, Set.of(role), delivery, tier, when, risk, false));
                            checked++;
                        }
                    }
                }
            }
        }
        assertEquals(13 * 7 * 4 * conditions.size() * risks.size(), checked);
        assertEquals(1 + 7 + 21, conditions.size());
    }

    @Test
    void roleSubsetsOfTwoAndThreeRoundTripWithTheLastRoleAsPrimary() {
        var roles = SkillRole.values();
        var checked = 0;
        for (var i = 0; i < roles.length; i++) {
            for (var j = i + 1; j < roles.length; j++) {
                assertRoundTrip(new IntentionDraft(roles[j], EnumSet.of(roles[i], roles[j]), Delivery.MELEE,
                        Optional.empty(), Set.of(), Set.of(), false));
                checked++;
                for (var k = j + 1; k < roles.length; k++) {
                    assertRoundTrip(new IntentionDraft(roles[k], EnumSet.of(roles[i], roles[j], roles[k]),
                            Delivery.PROJECTILE, Optional.of(DamageTier.HIGH),
                            Set.of(UseCondition.TARGET_FLEEING), Set.of(SkillRisk.HITS_ALLIES), true));
                    checked++;
                }
            }
        }
        assertEquals(78 + 286, checked);
    }

    @Test
    void theDraftOfADeclaredPurposeAndTheLineCompleteToTheSamePurpose() {
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.HITSCAN,
                OptionalDouble.of(16), OptionalDouble.empty(), 10, CooldownClass.MEDIUM, DamageTier.LOW,
                Set.of(UseCondition.TARGET_AT_RANGE, UseCondition.TARGET_FLEEING),
                Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT), false, PurposeOrigin.DECLARED);
        var draft = IntentionVocabulary.draftOf(purpose);
        var line = IntentionVocabulary.text(IntentionVocabulary.render(draft));
        assertEquals("DAMAGE, HITSCAN, DAMAGE_LOW, WHEN_TARGET_FLEEING, WHEN_TARGET_AT_RANGE,"
                + " RISK_NEEDS_LINE_OF_SIGHT", line);
        var read = parsed(line.split(",")).draft();
        assertEquals(draft, read);
        assertEquals(purpose, complete(draft, purpose, false));
        assertEquals(purpose, complete(read, purpose, false));
        var otherCooldown = purpose.withCooldown(CooldownClass.LONG);
        assertEquals(draft, IntentionVocabulary.draftOf(otherCooldown));
        var skill = new SkillPurpose(purpose.primary(), purpose.roles(), purpose.delivery(), purpose.reachBlocks(),
                purpose.radiusBlocks(), purpose.windUpTicks(), purpose.cooldown(), purpose.damage(),
                purpose.bestWhen(), purpose.risks(), purpose.ignoresShield(), PurposeOrigin.SKILL,
                ReachMeasure.POSITIONS_STRICT);
        var centres = new SkillPurpose(purpose.primary(), purpose.roles(), purpose.delivery(), purpose.reachBlocks(),
                purpose.radiusBlocks(), 0, CooldownClass.UNKNOWN, purpose.damage(), purpose.bestWhen(),
                purpose.risks(), purpose.ignoresShield(), PurposeOrigin.SKILL, ReachMeasure.CENTRES);
        assertEquals(IntentionVocabulary.draftOf(skill), IntentionVocabulary.draftOf(centres));
        assertEquals(skill, complete(parsed(IntentionVocabulary.text(IntentionVocabulary.render(
                IntentionVocabulary.draftOf(skill))).split(",")).draft(), skill, false));
    }

    @Test
    void anEmptyTierCompletesFromTheSameNativeDamageFact() {
        var control = new SkillPurpose(SkillRole.CONTROL, Set.of(SkillRole.CONTROL), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 5, CooldownClass.SHORT, DamageTier.UNRATED,
                Set.of(), Set.of(), false, PurposeOrigin.SKILL);
        var sustain = new SkillPurpose(SkillRole.SUSTAIN, Set.of(SkillRole.SUSTAIN), Delivery.SELF,
                OptionalDouble.empty(), OptionalDouble.empty(), 0, CooldownClass.MEDIUM, DamageTier.NONE,
                Set.of(UseCondition.SELF_LOW_HEALTH), Set.of(), false, PurposeOrigin.DECLARED);
        for (var purpose : List.of(control, sustain)) {
            var draft = IntentionVocabulary.draftOf(purpose);
            assertEquals(Optional.empty(), draft.declaredDamage());
            var read = parsed(IntentionVocabulary.text(IntentionVocabulary.render(draft)).split(",")).draft();
            var damaging = purpose == control;
            assertEquals(purpose, complete(draft, purpose, damaging));
            assertEquals(complete(draft, purpose, damaging), complete(read, purpose, damaging));
        }
        assertEquals(DamageTier.NONE, complete(IntentionVocabulary.draftOf(control), control, false).damage());
    }

    @Test
    void anUnratedOrNoneDamageGivesNoTierPrimitive() {
        var unrated = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.UNRATED,
                Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        assertEquals(Optional.empty(), IntentionVocabulary.draftOf(unrated).declaredDamage());
        assertEquals("DAMAGE, MELEE", IntentionVocabulary.text(IntentionVocabulary.render(
                IntentionVocabulary.draftOf(unrated))));
    }

    @Test
    void parseNeverThrowsOnBoundedRandomText() {
        var random = new Random(19);
        var alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ_- ,<>{}=0123456789éñ\t".toCharArray();
        var names = EnumSet.allOf(IntentionToken.class).stream().map(Enum::name).toList();
        for (var run = 0; run < 2000; run++) {
            var entries = new ArrayList<String>();
            var count = random.nextInt(20);
            for (var entry = 0; entry < count; entry++) {
                if (random.nextBoolean()) {
                    entries.add(names.get(random.nextInt(names.size())));
                } else {
                    var text = new StringBuilder();
                    var length = random.nextInt(30);
                    for (var index = 0; index < length; index++) {
                        text.append(alphabet[random.nextInt(alphabet.length)]);
                    }
                    entries.add(text.toString());
                }
            }
            var parse = IntentionVocabulary.parse(entries);
            assertTrue(parse instanceof IntentionParse.Parsed || parse instanceof IntentionParse.Invalid
                    || parse instanceof IntentionParse.Absent);
        }
    }

    private static void assertRoundTrip(IntentionDraft draft) {
        var line = IntentionVocabulary.text(IntentionVocabulary.render(draft));
        var parse = parsed(line.split(","));
        assertEquals(draft, parse.draft(), line);
        assertEquals(List.of(), parse.warnings(), line);
    }

    private static <T> List<Set<T>> subsetsUpToTwo(T[] values) {
        var subsets = new ArrayList<Set<T>>();
        subsets.add(Set.of());
        for (var i = 0; i < values.length; i++) {
            subsets.add(Set.of(values[i]));
            for (var j = i + 1; j < values.length; j++) {
                subsets.add(Set.of(values[i], values[j]));
            }
        }
        return subsets;
    }

    private static SkillPurpose complete(IntentionDraft draft, SkillPurpose metadata, boolean hasDamagingMechanic) {
        var rated = draft.roles().contains(SkillRole.DAMAGE) || draft.roles().contains(SkillRole.FINISHER)
                || hasDamagingMechanic;
        var damage = draft.declaredDamage().orElse(rated ? DamageTier.UNRATED : DamageTier.NONE);
        return new SkillPurpose(draft.primary(), draft.roles(), draft.delivery(), metadata.reachBlocks(),
                metadata.radiusBlocks(), metadata.windUpTicks(), metadata.cooldown(), damage, draft.bestWhen(),
                draft.risks(), draft.ignoresShield(), metadata.origin(), metadata.reachMeasure());
    }

    private static IntentionParse.Parsed parsed(String... entries) {
        return assertInstanceOf(IntentionParse.Parsed.class, IntentionVocabulary.parse(List.of(entries)));
    }

    private static IntentionParse.Invalid invalid(String... entries) {
        return assertInstanceOf(IntentionParse.Invalid.class, IntentionVocabulary.parse(List.of(entries)));
    }
}
