package akyrion.thepredict.config;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.UseCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurposeParserTest {
    private static final String PATH = "AIConfig/Profiles/guardian.yml: guardian_ai.Choices.melee.Purpose.";
    private static final String ROLES = "DAMAGE, CONTROL, GAP_CLOSE, ESCAPE, DEFENSE, SUSTAIN, BUFF, DEBUFF, SUMMON, ZONE,"
            + " INTERRUPT, FINISHER, OPENER";

    @TempDir
    Path directory;

    @Test
    void aCompletePurposeIsReadWithOriginDeclaredAndAnUnknownCooldown() throws Exception {
        var choice = choice(load("""
                Roles: [CONTROL, DAMAGE]
                Delivery: AREA_AROUND_SELF
                Radius: 3
                WindUp: 15
                Damage: MEDIUM
                BestWhen: [ENEMIES_GROUPED, TARGET_IN_MELEE]
                Risks: [ROOTS_CASTER]
                """));
        var purpose = choice.typedPurpose();
        assertEquals(SkillRole.CONTROL, purpose.primary());
        assertEquals(Set.of(SkillRole.CONTROL, SkillRole.DAMAGE), purpose.roles());
        assertEquals(Delivery.AREA_AROUND_SELF, purpose.delivery());
        assertEquals(OptionalDouble.of(3), purpose.radiusBlocks());
        assertTrue(purpose.reachBlocks().isEmpty());
        assertEquals(15, purpose.windUpTicks());
        assertEquals(DamageTier.MEDIUM, purpose.damage());
        assertEquals(Set.of(UseCondition.ENEMIES_GROUPED, UseCondition.TARGET_IN_MELEE), purpose.bestWhen());
        assertEquals(Set.of(SkillRisk.ROOTS_CASTER), purpose.risks());
        assertFalse(purpose.ignoresShield());
        assertEquals(PurposeOrigin.DECLARED, purpose.origin());
        assertEquals(CooldownClass.UNKNOWN, purpose.cooldown());
        assertEquals(Set.of(), choice.values());
    }

    @Test
    void rolesAreRequiredFromOneToThreeKnownAndOnce() throws Exception {
        assertEquals(List.of(PATH + "Roles: is required"), problems("Delivery: SELF\n"));
        assertEquals(List.of(PATH + "Roles: must list 1 to 3 roles, had 0"), problems("Roles: []\nDelivery: SELF\n"));
        assertEquals(List.of(PATH + "Roles: must list 1 to 3 roles, had 4"),
                problems("Roles: [BUFF, ZONE, OPENER, SUMMON]\nDelivery: SELF\n"));
        assertEquals(List.of(PATH + "Roles: unknown role 'TANK'; known: " + ROLES),
                problems("Roles: [TANK]\nDelivery: SELF\n"));
        assertEquals(List.of(PATH + "Roles: lists 'buff' twice"), problems("Roles: [BUFF, buff]\nDelivery: SELF\n"));
    }

    @Test
    void theDeliveryIsRequiredAndKnown() throws Exception {
        assertEquals(List.of(PATH + "Delivery: is required"), problems("Roles: [BUFF]\n"));
        assertEquals(List.of(PATH + "Delivery: unknown delivery 'BEAM'; known: MELEE, PROJECTILE, HITSCAN,"
                + " AREA_AROUND_SELF, AREA_AT_TARGET, CONE, SELF"), problems("Roles: [BUFF]\nDelivery: BEAM\n"));
    }

    @Test
    void reachIsRequiredForMeleeAndConeForbiddenForSelfAndAroundSelfAndAtMostSixtyFourBlocks() throws Exception {
        var damage = "Roles: [DAMAGE]\nDamage: LOW\n";
        assertEquals(List.of(PATH + "Reach: is required for Delivery MELEE"), problems(damage + "Delivery: MELEE\n"));
        assertEquals(List.of(PATH + "Reach: is required for Delivery CONE"), problems(damage + "Delivery: CONE\n"));
        assertEquals(List.of(PATH + "Reach: must be absent for Delivery SELF"),
                problems(damage + "Delivery: SELF\nReach: 2\n"));
        assertEquals(List.of(PATH + "Reach: must be absent for Delivery AREA_AROUND_SELF"),
                problems(damage + "Delivery: AREA_AROUND_SELF\nRadius: 3\nReach: 2\n"));
        assertEquals(64.0, choice(load(damage + "Delivery: HITSCAN\nReach: 64\n")).typedPurpose().reachBlocks()
                .getAsDouble());
        assertEquals(List.of(PATH + "Reach: must be greater than 0 and at most 64 blocks, was 64.0001"),
                problems(damage + "Delivery: HITSCAN\nReach: 64.0001\n"));
        assertEquals(List.of(PATH + "Reach: must be greater than 0 and at most 64 blocks, was 0"),
                problems(damage + "Delivery: MELEE\nReach: 0\n"));
        assertEquals(List.of(PATH + "Reach: must be a finite number"), problems(damage + "Delivery: MELEE\nReach: far\n"));
        assertTrue(choice(load(damage + "Delivery: PROJECTILE\n")).typedPurpose().reachBlocks().isEmpty());
    }

    @Test
    void radiusIsRequiredExactlyForTheAreasAndAtMostSixteenBlocks() throws Exception {
        var damage = "Roles: [DAMAGE]\nDamage: LOW\n";
        assertEquals(List.of(PATH + "Radius: is required for Delivery AREA_AT_TARGET"),
                problems(damage + "Delivery: AREA_AT_TARGET\n"));
        assertEquals(List.of(PATH + "Radius: is required for Delivery AREA_AROUND_SELF"),
                problems(damage + "Delivery: AREA_AROUND_SELF\n"));
        assertEquals(List.of(PATH + "Radius: must be absent for Delivery CONE"),
                problems(damage + "Delivery: CONE\nReach: 4\nRadius: 2\n"));
        assertEquals(16.0, choice(load(damage + "Delivery: AREA_AT_TARGET\nRadius: 16\n")).typedPurpose().radiusBlocks()
                .getAsDouble());
        assertEquals(List.of(PATH + "Radius: must be greater than 0 and at most 16 blocks, was 16.5"),
                problems(damage + "Delivery: AREA_AT_TARGET\nRadius: 16.5\n"));
    }

    @Test
    void windUpIsZeroByDefaultAndBetweenZeroAndOneHundredTicks() throws Exception {
        var self = "Roles: [BUFF]\nDelivery: SELF\n";
        assertEquals(0, choice(load(self)).typedPurpose().windUpTicks());
        assertEquals(100, choice(load(self + "WindUp: 100\n")).typedPurpose().windUpTicks());
        assertEquals(List.of(PATH + "WindUp: must be between 0 and 100 ticks, was 101"), problems(self + "WindUp: 101\n"));
        assertEquals(List.of(PATH + "WindUp: must be between 0 and 100 ticks, was -1"), problems(self + "WindUp: -1\n"));
        assertEquals(List.of(PATH + "WindUp: must be an integer"), problems(self + "WindUp: 2.5\n"));
    }

    @Test
    void damageIsRequiredWithDamageOrFinisherAndUnratedIsOnlyForInference() throws Exception {
        assertEquals(List.of(PATH + "Damage: is required when Roles has DAMAGE or FINISHER"),
                problems("Roles: [DAMAGE]\nDelivery: HITSCAN\n"));
        assertEquals(List.of(PATH + "Damage: must not be NONE when Roles has DAMAGE or FINISHER"),
                problems("Roles: [FINISHER]\nDelivery: HITSCAN\nDamage: NONE\n"));
        assertEquals(List.of(PATH + "Damage: unknown damage 'UNRATED'; known: NONE, LOW, MEDIUM, HIGH"),
                problems("Roles: [DAMAGE]\nDelivery: HITSCAN\nDamage: UNRATED\n"));
        assertEquals(DamageTier.NONE, choice(load("Roles: [DEBUFF]\nDelivery: HITSCAN\n")).typedPurpose().damage());
        assertEquals(DamageTier.HIGH, choice(load("Roles: [DEBUFF]\nDelivery: HITSCAN\nDamage: high\n")).typedPurpose()
                .damage());
    }

    @Test
    void bestWhenAndRisksListUpToFourKnownValuesOnce() throws Exception {
        var self = "Roles: [BUFF]\nDelivery: SELF\n";
        assertEquals(List.of(PATH + "BestWhen: must list 0 to 4 conditions, had 5"), problems(self
                + "BestWhen: [TARGET_LOW_HEALTH, TARGET_SHIELDING, TARGET_FLEEING, SELF_LOW_HEALTH, ENEMIES_GROUPED]\n"));
        assertEquals(List.of(PATH + "BestWhen: unknown condition 'ALWAYS'; known: TARGET_LOW_HEALTH, TARGET_SHIELDING,"
                + " TARGET_FLEEING, SELF_LOW_HEALTH, ENEMIES_GROUPED, TARGET_AT_RANGE, TARGET_IN_MELEE"),
                problems(self + "BestWhen: [ALWAYS]\n"));
        assertEquals(List.of(PATH + "BestWhen: lists 'TARGET_FLEEING' twice"),
                problems(self + "BestWhen: [target_fleeing, TARGET_FLEEING]\n"));
        assertEquals(List.of(PATH + "Risks: must list 0 to 4 risks, had 5"), problems(self + "Risks: [ROOTS_CASTER,"
                + " SELF_DAMAGE, HITS_ALLIES, SLOW_PROJECTILE, NEEDS_LINE_OF_SIGHT]\n"));
        assertEquals(List.of(PATH + "Risks: unknown risk 'LAG'; known: LEAVES_CASTER_EXPOSED, ROOTS_CASTER, SELF_DAMAGE,"
                + " NEEDS_LINE_OF_SIGHT, SLOW_PROJECTILE, BLOCKED_BY_SHIELD, HITS_ALLIES"), problems(self + "Risks: [LAG]\n"));
        assertEquals(4, choice(load(self + "Risks: [ROOTS_CASTER, SELF_DAMAGE, HITS_ALLIES, SLOW_PROJECTILE]\n"))
                .typedPurpose().risks().size());
    }

    @Test
    void ignoringShieldsIsFalseByDefaultAndExcludesTheShieldRisk() throws Exception {
        var hit = "Roles: [DAMAGE]\nDelivery: MELEE\nReach: 2.5\nDamage: LOW\n";
        assertFalse(choice(load(hit)).typedPurpose().ignoresShield());
        assertTrue(choice(load(hit + "IgnoresShield: true\n")).typedPurpose().ignoresShield());
        assertEquals(List.of(PATH + "IgnoresShield: cannot be true while Risks has BLOCKED_BY_SHIELD"),
                problems(hit + "IgnoresShield: true\nRisks: [BLOCKED_BY_SHIELD]\n"));
        assertEquals(List.of(PATH + "IgnoresShield: must be true or false"), problems(hit + "IgnoresShield: often\n"));
    }

    @Test
    void keysAreExactWhileValuesIgnoreCase() throws Exception {
        assertEquals(List.of(PATH + "reach: is not a known field"),
                problems("Roles: [DAMAGE]\nDelivery: MELEE\nreach: 2.5\nDamage: LOW\n"));
        assertEquals(List.of(PATH + "Cooldown: is not a known field"),
                problems("Roles: [BUFF]\nDelivery: SELF\nCooldown: 3\n"));
        var purpose = choice(load("Roles: [damage]\nDelivery: melee\nReach: 2.5\nDamage: medium\n"
                + "BestWhen: [target_in_melee]\nRisks: [Roots_Caster]\n")).typedPurpose();
        assertEquals(Delivery.MELEE, purpose.delivery());
        assertEquals(Set.of(UseCondition.TARGET_IN_MELEE), purpose.bestWhen());
        assertEquals(Set.of(SkillRisk.ROOTS_CASTER), purpose.risks());
    }

    @Test
    void aPurposeThatIsNotASectionIsAnError() throws Exception {
        write("""
                guardian_ai:
                  MythicID: Guardian
                  Objective: Survive
                  Choices:
                    melee: { MythicID: GuardianMelee, Purpose: DAMAGE }
                  Tactics:
                    press: { Movement: APPROACH, Choice: melee }
                """);
        assertEquals(List.of("AIConfig/Profiles/guardian.yml: guardian_ai.Choices.melee.Purpose: must be a section"), problems());
    }

    @Test
    void aReachBeyondRangedReachIsOnlyAWarning() throws Exception {
        var loaded = load("Roles: [DAMAGE]\nDelivery: PROJECTILE\nReach: 20\nDamage: LOW\n");
        assertEquals(List.of(PATH + "Reach: is beyond RangedReach; Jev reads it as beyond ranged reach"),
                loaded.problems().stream().map(ConfigProblem::toString).toList());
        assertFalse(loaded.problems().getFirst().isError());
        assertEquals(1, loaded.profiles().size());
        assertTrue(load("Roles: [DAMAGE]\nDelivery: PROJECTILE\nReach: 16\nDamage: LOW\n").problems().isEmpty());
    }

    @Test
    void aProfileWithoutPurposeLoadsUntyped() throws Exception {
        write("""
                guardian_ai:
                  MythicID: Guardian
                  Objective: Survive
                  Choices:
                    melee: { MythicID: GuardianMelee, CD: 20 }
                    guard: { MythicID: GuardianGuard, RequiresTarget: false }
                  Tactics:
                    press: { Movement: APPROACH, Choice: melee }
                    defend: { Movement: RETREAT, Choice: guard }
                """);
        var loaded = new ProfileLoader().load(directory);
        assertEquals(List.of(), loaded.problems());
        var profile = loaded.profiles().get("Guardian");
        assertEquals(SkillChoice.untyped(new ChoiceId("melee"), "GuardianMelee", Set.of(), true, 20),
                profile.choices().get(new ChoiceId("melee")));
        assertTrue(profile.choices().get(new ChoiceId("guard")).purpose().isEmpty());
    }

    @Test
    void aVanillaMeleeChoiceInfersItsWholePurposeOrDeclaresADamagingMeleeHit() throws Exception {
        write("""
                guardian_ai:
                  MythicID: Guardian
                  Objective: Survive
                  Movement: { MeleeReach: 3 }
                  Choices:
                    punch: { Kind: VANILLA_MELEE }
                    kick:
                      Kind: VANILLA_MELEE
                      Purpose: { Roles: [DAMAGE, CONTROL], Delivery: MELEE, Damage: LOW, WindUp: 2 }
                  Tactics:
                    brawl: { Movement: APPROACH, Choice: punch }
                    shove: { Movement: APPROACH, Choice: kick }
                """);
        var loaded = new ProfileLoader().load(directory);
        assertEquals(List.of(), loaded.problems());
        var profile = loaded.profiles().get("Guardian");
        var punch = profile.choices().get(new ChoiceId("punch")).typedPurpose();
        assertEquals(PurposeOrigin.INFERRED, punch.origin());
        assertEquals(3.0, punch.reachBlocks().getAsDouble());
        assertEquals(Set.of(UseCondition.TARGET_IN_MELEE), punch.bestWhen());
        var kick = profile.choices().get(new ChoiceId("kick")).typedPurpose();
        assertEquals(PurposeOrigin.DECLARED, kick.origin());
        assertEquals(3.0, kick.reachBlocks().getAsDouble());
        assertEquals(DamageTier.LOW, kick.damage());
    }

    @Test
    void aDeclaredVanillaMeleePurposeMustBeADamagingMeleeHit() throws Exception {
        write(vanilla("{ Roles: [DAMAGE], Delivery: CONE, Reach: 3, Damage: LOW }"));
        assertEquals(List.of("AIConfig/Profiles/guardian.yml: guardian_ai.Choices.punch.Purpose.Delivery: must be MELEE for Kind"
                + " VANILLA_MELEE"), problems());
        write(vanilla("{ Roles: [CONTROL], Delivery: MELEE }"));
        assertEquals(List.of("AIConfig/Profiles/guardian.yml: guardian_ai.Choices.punch.Purpose.Roles: must include DAMAGE for Kind"
                + " VANILLA_MELEE"), problems());
        write(vanilla("{ Roles: [DAMAGE], Delivery: MELEE, Reach: 2, Damage: HIGH }"));
        var profile = new ProfileLoader().load(directory).profiles().get("Guardian");
        assertEquals(2.0, profile.choices().get(new ChoiceId("punch")).typedPurpose().reachBlocks().getAsDouble());
    }

    private static String vanilla(String purpose) {
        return """
                guardian_ai:
                  MythicID: Guardian
                  Objective: Survive
                  Choices:
                    punch: { Kind: VANILLA_MELEE, Purpose: %s }
                  Tactics:
                    brawl: { Movement: APPROACH, Choice: punch }
                """.formatted(purpose);
    }

    private ProfileLoadResult load(String purpose) throws Exception {
        write("""
                guardian_ai:
                  MythicID: Guardian
                  Objective: Survive
                  Choices:
                    melee:
                      MythicID: GuardianMelee
                      Purpose:
                %s
                  Tactics:
                    press: { Movement: APPROACH, Choice: melee }
                """.formatted(purpose.lines().map(line -> "        " + line).reduce((a, b) -> a + "\n" + b)
                .orElse("")));
        return new ProfileLoader().load(directory);
    }

    private static SkillChoice choice(ProfileLoadResult loaded) {
        assertTrue(loaded.problems().stream().noneMatch(ConfigProblem::isError), loaded.problems().toString());
        TacticalProfile profile = loaded.profiles().get("Guardian");
        return profile.choices().get(new ChoiceId("melee"));
    }

    private List<String> problems(String purpose) throws Exception {
        return load(purpose).problems().stream().map(ConfigProblem::toString).toList();
    }

    private List<String> problems() throws Exception {
        return new ProfileLoader().load(directory).problems().stream().map(ConfigProblem::toString).toList();
    }

    private void write(String profile) throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), "schemaVersion: 2\n" + profile);
    }
}
