package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.adapter.server.context.ParticipantDefaults;
import akyrion.thepredict.adapter.server.context.SelectorRegistry;
import akyrion.thepredict.config.FirstRunFiles;
import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DangerRules;
import akyrion.thepredict.domain.ParticipantRules;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TellAnswer;
import io.lumine.mythic.api.skills.Skill;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicProfileBinderSuiteTest {
    private static final String VALUES_LEFT = "Skills/test.yml:GuardianMelee.PredictValues: PredictValues of skill"
            + " 'GuardianMelee' are no longer read; write an Intention";

    private static final String GUARDIAN = """
            schemaVersion: 2
            guardian_ai:
              MythicID: Guardian
              Objective: Survive while defeating the current target
              Choices:
                melee: { MythicID: GuardianMelee, CD: 40 }
                punch: { Kind: VANILLA_MELEE }
              Tactics:
                press_and_strike: { Movement: APPROACH, Choice: melee }
                brawl: { Movement: APPROACH, Choice: punch }
                hold: { Movement: HOLD, Description: Wait for the target to come closer }
              Contexts:
                wounded: { Conditions: ['healthpercent{p=<30%}'], Priority: 90, Tactics: [hold] }
                idle: { Priority: 10, Tactics: [hold] }
            """;

    @TempDir
    Path directory;

    @Test
    void bundledExampleBindsTheRealPremiumApiTypesAcrossToggleChanges() throws Exception {
        new FirstRunFiles().installMissing(new akyrion.thepredict.config.ConfigLocations(
                directory.resolve("MythicMobs"), directory));
        var profiles = new ProfileLoader().load(directory.resolve("examples/AI")).profiles();
        var skills = YamlConfiguration.loadConfiguration(
                directory.resolve("examples/MythicMobs/Skills/ThePredictGuardianSkills.yml").toFile());
        var definitions = MythicFixtures.definitions();
        for (var id : skills.getKeys(false)) {
            MythicYaml.attach(definitions, id, skills.getConfigurationSection(id));
        }
        var mob = YamlConfiguration.loadConfiguration(
                directory.resolve("examples/MythicMobs/Mobs/ThePredictGuardian.yml").toFile());
        var config = new HashMap<String, Object>(Map.of("Predictor", mob.getBoolean("ThePredictGuardian.Predictor"),
                "Skills", mob.getStringList("ThePredictGuardian.Skills"), "ThreatTable.DecayUnreachable",
                mob.getBoolean("ThePredictGuardian.ThreatTable.DecayUnreachable")));
        definitions.add(MythicFixtures.mob("ThePredictGuardian", "ZOMBIE", config));
        var binder = new MythicProfileBinder(definitions);

        var cueWarnings = List.of(
                new BindingProblem(BindingProblem.Severity.WARNING, Optional.of("thepredict_guardian"),
                        "thepredict_guardian.Choices.melee",
                        "skill 'ThePredictGuardianMelee' telegraph has no visual (or audio) cue; ThePredict adds none:"
                                + " add a ~onSignal:PREDICTOR_TELL_BLOCK handler"),
                new BindingProblem(BindingProblem.Severity.WARNING, Optional.of("thepredict_guardian"),
                        "thepredict_guardian.Choices.slam",
                        "skill 'ThePredictGuardianSlam' telegraph has no visual (or audio) cue; ThePredict adds none:"
                                + " add a ~onSignal:PREDICTOR_TELL_BACK_OFF handler"));
        var disabled = binder.bind(profiles);
        assertTrue(disabled.profiles().isEmpty());
        assertEquals(cueWarnings, disabled.problems());
        config.put("Predictor", true);
        var enabled = binder.bind(profiles);
        assertEquals(cueWarnings, enabled.problems());
        var bound = enabled.profiles().get("ThePredictGuardian");
        assertEquals(4, bound.skills().size());
        assertEquals(Set.of(PredictValue.DEFENSE, PredictValue.HEALS_CASTER),
                bound.profile().choices().get(new ChoiceId("defense")).values());
        assertEquals("ZOMBIE", bound.entityType());
        assertFalse(bound.brainSpecies());
        assertEquals(Set.of("approach", "press_and_strike", "keep_distance", "keep_distance_and_shoot",
                "retreat_and_defend", "flank_and_strike", "close_in_and_slam", "hold"), bound.profile().tactics().keySet().stream()
                .map(TacticId::value).collect(Collectors.toSet()));
    }

    @Test
    void bindsSkillsValuesAndNativeConditionsOfAnEnabledMob() throws Exception {
        var bound = binder(standardDefinitions()).bind(profiles(GUARDIAN));
        var guardian = bound.profiles().get("Guardian");

        assertEquals(List.of(VALUES_LEFT), messages(bound));
        assertEquals(Set.of(new ChoiceId("melee")), guardian.skills().keySet());
        assertEquals(Set.of(PredictValue.MELEE_ATTACK, PredictValue.IGNORES_SHIELD),
                guardian.profile().choices().get(new ChoiceId("melee")).values());
        assertEquals(Set.of(), guardian.profile().choices().get(new ChoiceId("punch")).values());
        assertEquals(Set.of(new ContextId("wounded")), guardian.conditions().keySet());
        assertEquals(List.of("healthpercent{p=<30%}"), guardian.conditions().get(new ContextId("wounded")).lines());
    }

    @Test
    void enabledMobWithoutProfileAndNonBooleanToggleAreErrors() throws Exception {
        var definitions = standardDefinitions()
                .add(MythicFixtures.mob("Orphan", "ZOMBIE", Map.of("Predictor", true)))
                .add(MythicFixtures.mob("Typo", "ZOMBIE", Map.of("Predictor", "yes")));
        var result = binder(definitions).bind(profiles(GUARDIAN));

        assertEquals(1, result.profiles().size());
        assertEquals(List.of(VALUES_LEFT, "Orphan.Predictor: is true but no valid AI profile targets this mob",
                "Typo.Predictor: must be true or false"), messages(result));
        assertTrue(result.problems().stream().allMatch(problem -> problem.profileId().isEmpty()));
    }

    @Test
    void wrongCapitalizationSuggestsTheExactId() throws Exception {
        var mob = binder(standardDefinitions()).bind(profiles(GUARDIAN.replace("MythicID: Guardian\n", "MythicID: guardian\n")));
        assertEquals(List.of("guardian_ai.MythicID: unknown mob 'guardian'; IDs are case-sensitive, did you mean 'Guardian'?",
                "Guardian.Predictor: is true but no valid AI profile targets this mob"), messages(mob));

        var skill = binder(standardDefinitions()).bind(profiles(GUARDIAN.replace("GuardianMelee", "guardianmelee")));
        assertEquals(List.of("guardian_ai.Choices.melee.MythicID: unknown skill 'guardianmelee'; IDs are case-sensitive,"
                + " did you mean 'GuardianMelee'?"), messages(skill));
        assertTrue(skill.profiles().isEmpty());

        var missing = binder(standardDefinitions().removeSkill("GuardianMelee")).bind(profiles(GUARDIAN));
        assertEquals(List.of("guardian_ai.Choices.melee.MythicID: unknown skill 'GuardianMelee'"), messages(missing));
    }

    @Test
    void predictValuesAreReadAsListOrTextAndUnknownValuesAreRejected() throws Exception {
        var text = standardDefinitions().add(MythicFixtures.skill("GuardianMelee",
                Map.of("PredictValues", "MELEE_ATTACK, high_damage", "Cooldown", 2)));
        assertEquals(Set.of(PredictValue.MELEE_ATTACK, PredictValue.HIGH_DAMAGE), binder(text).bind(profiles(GUARDIAN))
                .profiles().get("Guardian").profile().choices().get(new ChoiceId("melee")).values());

        var unknown = standardDefinitions().add(MythicFixtures.skill("GuardianMelee",
                Map.of("PredictValues", List.of("MELEE_ATTACK", "TELEPORTS"))));
        assertEquals(List.of("guardian_ai.Choices.melee.MythicID: skill 'GuardianMelee' PredictValues: Unknown "
                + "PredictValues entry: TELEPORTS", VALUES_LEFT), messages(binder(unknown).bind(profiles(GUARDIAN))));

        var none = standardDefinitions().add(MythicFixtures.skill("GuardianMelee", Map.of()));
        assertEquals(List.of("Skills/test.yml:GuardianMelee.Intention: skill 'GuardianMelee' has no Intention; the"
                + " choice is untyped and only casts with its own tactic"), messages(binder(none).bind(profiles(GUARDIAN))));
    }

    @Test
    void skillWithoutAnyCooldownIsAWarningThatStillBinds() throws Exception {
        var noCooldown = GUARDIAN.replace("{ MythicID: GuardianMelee, CD: 40 }", "{ MythicID: GuardianMelee }");
        var warned = binder(standardDefinitions()).bind(profiles(noCooldown));
        assertEquals(1, warned.profiles().size());
        assertEquals(List.of(VALUES_LEFT, "guardian_ai.Choices.melee: skill 'GuardianMelee' has no static Cooldown;"
                + " with CastMode ANY_READY it fires whenever it can"), messages(warned));
        assertEquals(BindingProblem.Severity.WARNING, warned.problems().getFirst().severity());

        var nativeCooldown = standardDefinitions().add(MythicFixtures.skill("GuardianMelee",
                Map.of("PredictValues", List.of("MELEE_ATTACK"), "Cooldown", 2, "Intention", "DAMAGE, MELEE")));
        assertEquals(List.of(), binder(nativeCooldown).bind(profiles(noCooldown)).problems());
    }

    @Test
    void choiceSkillAlsoCastByANormalTriggerIsAWarning() throws Exception {
        var duplicated = standardDefinitions().add(MythicFixtures.mob("Guardian", "ZOMBIE", Map.of("Predictor", true,
                "Skills", List.of("skill{s=GuardianMelee} @target ~onAttack", "skill{s=GuardianMeleeHeavy} ~onTimer:20",
                        "skill{s=GuardianMelee} @self"))));
        var result = binder(duplicated).bind(profiles(GUARDIAN));

        assertEquals(1, result.profiles().size());
        assertEquals(List.of(VALUES_LEFT, "guardian_ai.Choices.melee.MythicID: mob 'Guardian' also casts 'GuardianMelee'"
                + " from its Skills"
                + " with a trigger; it may add damage while ThePredict controls the mob"), messages(result));

        var otherSkillOnly = standardDefinitions().add(MythicFixtures.mob("Guardian", "ZOMBIE", Map.of("Predictor", true,
                "Skills", List.of("skill{s=GuardianMeleeHeavy} ~onAttack"))));
        assertEquals(List.of(VALUES_LEFT), messages(binder(otherSkillOnly).bind(profiles(GUARDIAN))));
    }

    @Test
    void brainSpeciesAcceptOnlyTacticsWithoutMovement() throws Exception {
        var piglin = standardDefinitions().add(MythicFixtures.mob("Guardian", "PIGLIN_BRUTE", Map.of("Predictor", true)));
        var rejected = binder(piglin).bind(profiles(GUARDIAN));
        assertTrue(rejected.profiles().isEmpty());
        assertEquals(List.of("guardian_ai.Tactics.press_and_strike.Movement: species PIGLIN_BRUTE uses a Brain; only "
                + "Movement: NONE is allowed"), messages(rejected));

        var still = GUARDIAN.replace("Movement: APPROACH", "Movement: NONE").replace("Movement: HOLD", "Movement: NONE");
        var accepted = binder(piglin).bind(profiles(still)).profiles().get("Guardian");
        assertTrue(accepted.brainSpecies());
        assertEquals("PIGLIN_BRUTE", accepted.entityType());
    }

    @Test
    void textThatMayNotBeEnglishIsOnlyAWarning() throws Exception {
        var spanish = GUARDIAN.replace("Survive while defeating the current target", "Sobrevivir mientras derrota al objetivo")
                .replace("Wait for the target to come closer", "Espera a que el objetivo se acerque");
        var result = binder(standardDefinitions()).bind(profiles(spanish));

        assertEquals(1, result.profiles().size());
        assertEquals(3, result.problems().size());
        assertTrue(result.problems().stream().allMatch(problem -> problem.severity() == BindingProblem.Severity.WARNING));
        assertEquals("Skills/test.yml:GuardianMelee.PredictValues", result.problems().getFirst().path());
        assertEquals("guardian_ai.Objective", result.problems().get(1).path());
        assertEquals("guardian_ai.Tactics.hold.Description", result.problems().get(2).path());
    }

    @Test
    void conditionRejectedByMythicMobsDisablesOnlyItsContext() throws Exception {
        var result = binder(standardDefinitions().invalid("healthpercent{p=<30%}")).bind(profiles(GUARDIAN));
        var guardian = result.profiles().get("Guardian");

        assertEquals(List.of(VALUES_LEFT, "guardian_ai.Contexts.wounded.Conditions: MythicMobs rejected the condition"
                + " 'healthpercent{p=<30%}'; context 'wounded' is disabled"), messages(result));
        assertEquals(List.of(new ContextId("idle")), guardian.profile().contexts().stream().map(context -> context.id()).toList());
        assertTrue(guardian.conditions().isEmpty());
    }

    @Test
    void unresolvedCustomConditionIsDisabledOneTickLater() throws Exception {
        var definitions = standardDefinitions().unresolved("healthpercent{p=<30%}");
        var binder = binder(definitions);
        var bound = binder.bind(profiles(GUARDIAN));
        assertEquals(List.of(VALUES_LEFT), messages(bound));
        assertEquals(2, bound.profiles().get("Guardian").profile().contexts().size());

        var verified = binder.verifyConditions(bound.profiles());
        assertEquals(List.of("guardian_ai.Contexts.wounded.Conditions: no plugin resolved the condition 'healthpercent{p=<30%}';"
                + " context 'wounded' is disabled"), messages(verified));
        assertEquals(1, verified.profiles().get("Guardian").profile().contexts().size());

        var resolved = binder(standardDefinitions()).bind(profiles(GUARDIAN));
        assertEquals(List.of(), binder(standardDefinitions()).verifyConditions(resolved.profiles()).problems());
    }

    @Test
    void nativeUnreachableDecayReplacesTheProfileDecay() throws Exception {
        var decaying = standardDefinitions().add(MythicFixtures.mob("Guardian", "ZOMBIE",
                Map.of("Predictor", true, "ThreatTable.DecayUnreachable", true)));
        var result = binder(decaying).bind(profiles(GUARDIAN));

        assertEquals(0.0, result.profiles().get("Guardian").profile().threat().unreachableDecayPerSecond());
        assertEquals(List.of(VALUES_LEFT, "guardian_ai.Threat.UnreachableDecayPerSecond: is ignored because mob"
                + " 'Guardian' enables ThreatTable.DecayUnreachable"), messages(result));
    }

    @Test
    void aRejectedProfileDoesNotStopTheOthers() throws Exception {
        var both = GUARDIAN + GUARDIAN.replace("schemaVersion: 2\n", "").replace("guardian_ai", "brute_ai")
                .replace("MythicID: Guardian\n", "MythicID: Brute\n").replace("GuardianMelee", "Missing");
        var definitions = standardDefinitions().add(MythicFixtures.mob("Brute", "ZOMBIE", Map.of("Predictor", true)));
        var result = binder(definitions).bind(profiles(both));

        assertEquals(Set.of("Guardian"), result.profiles().keySet());
        assertEquals(List.of(VALUES_LEFT, "brute_ai.Choices.melee.MythicID: unknown skill 'Missing'"), messages(result));
        assertEquals(Optional.of("brute_ai"), result.problems().get(1).profileId());
    }

    @Test
    void aDeclaredPurposeBindsAndASkillWithoutPurposeStaysUntyped() throws Exception {
        var definitions = standardDefinitions().add(MythicFixtures.skill("GuardianMelee", Map.of("Cooldown", "2")));
        var declared = binder(definitions).bind(profiles(purposeProfile("""
                    melee:
                      MythicID: GuardianMelee
                      Purpose: { Roles: [DAMAGE], Delivery: MELEE, Reach: 2.5, Damage: MEDIUM }
                """, "press: { Movement: APPROACH, Choice: melee }")));
        assertEquals(List.of(new BindingProblem(BindingProblem.Severity.WARNING, Optional.of("guardian_ai"),
                "guardian_ai.Choices.melee",
                "skill 'GuardianMelee' telegraph has no visual (or audio) cue; ThePredict adds none:"
                        + " add a ~onSignal:PREDICTOR_TELL_BLOCK handler")), declared.problems());
        var melee = declared.profiles().get("Guardian").profile().choices().get(new ChoiceId("melee"));
        assertEquals(Set.of(), melee.values());
        assertEquals(PurposeOrigin.DECLARED, melee.typedPurpose().origin());
        assertEquals(CooldownClass.UNKNOWN, melee.typedPurpose().cooldown());

        var untyped = binder(definitions).bind(profiles(purposeProfile("""
                    melee: { MythicID: GuardianMelee }
                """, "press: { Movement: APPROACH, Choice: melee }")));
        assertTrue(untyped.profiles().get("Guardian").profile().choices().get(new ChoiceId("melee")).purpose().isEmpty());
        assertTrue(messages(untyped).stream().noneMatch(message -> message.contains("PredictValues requires")),
                messages(untyped).toString());
    }

    @Test
    void predictValuesInferNoPurposeAndEveryCooldownClassIsResolved() throws Exception {
        var bound = binder(standardDefinitions()).bind(profiles(GUARDIAN)).profiles().get("Guardian").profile();
        var melee = bound.choices().get(new ChoiceId("melee"));
        assertTrue(melee.purpose().isEmpty(), "PredictValues no longer infer a purpose (M19, decision 5)");
        assertEquals(CooldownClass.SHORT, bound.choices().get(new ChoiceId("punch")).typedPurpose().cooldown());
        var slow = binder(standardDefinitions()).bind(profiles(GUARDIAN.replace("punch: { Kind: VANILLA_MELEE }",
                "punch: { Kind: VANILLA_MELEE, CD: 60 }"))).profiles().get("Guardian").profile();
        assertEquals(CooldownClass.MEDIUM, slow.choices().get(new ChoiceId("punch")).typedPurpose().cooldown());
    }

    @Test
    void theIgnoresShieldLabelNeverReachesADeclaredPurposeWithOrWithoutTheShieldRisk() throws Exception {
        var plain = binder(standardDefinitions()).bind(profiles(purposeProfile("""
                    melee:
                      MythicID: GuardianMelee
                      CD: 40
                      Purpose: { Roles: [DAMAGE], Delivery: MELEE, Reach: 2.5, Damage: LOW }
                """, "press: { Movement: APPROACH, Choice: melee }")));
        assertEquals(List.of(), plain.problems());
        assertFalse(plain.profiles().get("Guardian").profile().choices().get(new ChoiceId("melee")).typedPurpose()
                .ignoresShield(), "a declared purpose does not gain ignoresShield from PredictValues (E19-1 DP5)");
        var risky = binder(standardDefinitions()).bind(profiles(purposeProfile("""
                    melee:
                      MythicID: GuardianMelee
                      CD: 40
                      Purpose: { Roles: [DAMAGE], Delivery: MELEE, Reach: 2.5, Damage: LOW, Risks: [BLOCKED_BY_SHIELD] }
                """, "press: { Movement: APPROACH, Choice: melee }")));
        assertFalse(risky.profiles().get("Guardian").profile().choices().get(new ChoiceId("melee")).typedPurpose()
                .ignoresShield());
        assertTrue(messages(risky).stream().noneMatch(message -> message.contains("PredictValues say IGNORES_SHIELD")),
                messages(risky).toString());
    }

    @Test
    void eachLabelADeclaredPurposeContradictsIsAWarningWithItsPath() throws Exception {
        var definitions = standardDefinitions()
                .add(skill("Swing", "MELEE_ATTACK")).add(skill("Shot", "RANGED_ATTACK"))
                .add(skill("Mend", "HEALS_CASTER")).add(skill("Call", "SUMMONS_ALLY"))
                .add(skill("Crush", "HIGH_DAMAGE"));
        var result = binder(definitions).bind(profiles(purposeProfile("""
                    swing: { MythicID: Swing, CD: 20, Purpose: { Roles: [DAMAGE], Delivery: PROJECTILE, Damage: LOW } }
                    shot: { MythicID: Shot, CD: 20, Purpose: { Roles: [DAMAGE], Delivery: MELEE, Reach: 2, Damage: LOW } }
                    mend: { MythicID: Mend, CD: 20, RequiresTarget: false, Purpose: { Roles: [BUFF], Delivery: SELF } }
                    call: { MythicID: Call, CD: 20, RequiresTarget: false, Purpose: { Roles: [BUFF], Delivery: SELF } }
                    crush: { MythicID: Crush, CD: 20, Purpose: { Roles: [DAMAGE], Delivery: HITSCAN, Damage: LOW } }
                """, "press: { Movement: APPROACH, Choice: swing }")));
        assertEquals(List.of(), messages(result), "PredictValues are not compared once a purpose is declared (E19-1 DP4)");
        assertEquals(Set.of("Guardian"), result.profiles().keySet());
    }

    @Test
    void theTacticLintWarnsOnlyAboutTacticsWithADeclaredPurpose() throws Exception {
        var definitions = standardDefinitions().add(skill("Jab", "MELEE_ATTACK")).add(skill("Lance", "MELEE_ATTACK"));
        var result = binder(definitions).bind(profiles(purposeProfile("""
                    melee:
                      MythicID: GuardianMelee
                      CD: 40
                      Purpose: { Roles: [DAMAGE], Delivery: MELEE, Reach: 2.5, Damage: MEDIUM }
                    jab: { MythicID: Jab, CD: 20, Purpose: { Roles: [DAMAGE], Delivery: MELEE, Reach: 1, Damage: LOW } }
                    lance: { MythicID: Lance, CD: 20, Purpose: { Roles: [DAMAGE], Delivery: CONE, Reach: 6, Damage: LOW } }
                """, """
                keep_and_strike: { Movement: KEEP_DISTANCE, Choice: melee }
                    hold_and_strike: { Movement: HOLD, Choice: melee }
                    press: { Movement: APPROACH, Choice: melee }
                    poke: { Movement: APPROACH, Choice: jab }
                    keep_and_lance: { Movement: KEEP_DISTANCE, Choice: lance }""")));
        var never = "; this tactic never casts";
        var only = "; this tactic casts only if the target comes within reach";
        assertEquals(List.of(
                "guardian_ai.Choices.melee: skill 'GuardianMelee' telegraph has no visual (or audio) cue; ThePredict"
                        + " adds none: add a ~onSignal:PREDICTOR_TELL_BLOCK handler",
                "guardian_ai.Choices.lance: skill 'Lance' telegraph has no visual (or audio) cue; ThePredict adds none:"
                        + " add a ~onSignal:PREDICTOR_TELL_BLOCK handler",
                "guardian_ai.Tactics.keep_and_strike: Movement KEEP_DISTANCE keeps the target beyond Band.Inner 5 but"
                        + " skill 'melee' reaches 2.5" + never,
                "guardian_ai.Tactics.hold_and_strike: Movement HOLD does not close in to RangedReach 16 but skill 'melee'"
                        + " reaches 2.5" + only,
                "guardian_ai.Tactics.poke: Movement APPROACH stops at MeleeReach 2.5 but skill 'jab' reaches 1" + only,
                "guardian_ai.Tactics.keep_and_lance: Movement KEEP_DISTANCE keeps the target up to Band.Outer 9 but"
                        + " skill 'lance' reaches 6" + only), messages(result));
        assertTrue(result.problems().stream().allMatch(problem -> problem.severity() == BindingProblem.Severity.WARNING));
        assertEquals(Set.of("Guardian"), result.profiles().keySet());
    }

    @Test
    void anInferredMeleeSkillNeverGetsTheTacticLint() throws Exception {
        var inferred = GUARDIAN.replace("hold: { Movement: HOLD, Description: Wait for the target to come closer }",
                "hold: { Movement: HOLD, Description: Wait for the target to come closer }\n"
                        + "    keep_and_strike: { Movement: KEEP_DISTANCE, Choice: melee }");
        assertEquals(List.of(VALUES_LEFT), messages(binder(standardDefinitions()).bind(profiles(inferred))));
    }

    @Test
    void theBoundProfileCarriesTheSignalHandlersOfTheMobLines() throws Exception {
        var definitions = MythicFixtures.definitions()
                .add(MythicFixtures.mob("Guardian", "ZOMBIE", Map.of("Predictor", true, "Skills", List.of(
                        "sound{s=block.anvil.land} @self ~onSignal:PREDICTOR_TELL_BLOCK",
                        "skill{s=GuardianStun} @self ~onSignal:PREDICTOR_STANCE_STAGGERED"))))
                .add(MythicFixtures.skill("GuardianMelee", Map.of()));
        var handlers = binder(definitions).bind(profiles(GUARDIAN)).profiles().get("Guardian").cueHandlers();
        assertTrue(handlers.handles("PREDICTOR_TELL_BLOCK"));
        assertTrue(handlers.handles("PREDICTOR_STANCE_STAGGERED"));
        assertFalse(handlers.handles("PREDICTOR_TELL_DODGE"));
        var silent = binder(standardDefinitions()).bind(profiles(GUARDIAN)).profiles().get("Guardian").cueHandlers();
        assertFalse(silent.handles("PREDICTOR_TELL_BLOCK"), "a mob without ~onSignal lines handles nothing");
    }

    @Test
    void theAnswerOfTheIntentionLineIsTheAnswerOfTheTellUnlessTheProfileDeclaresOne() throws Exception {
        var line = MythicFixtures.definitions()
                .add(MythicFixtures.mob("Guardian", "ZOMBIE", Map.of("Predictor", true)))
                .add(MythicFixtures.skill("GuardianMelee", Map.of("Cooldown", 2, "Intention",
                        "DAMAGE, MELEE, ANSWER_DODGE")));
        var bound = binder(line).bind(profiles(GUARDIAN)).profiles().get("Guardian").profile()
                .choices().get(new ChoiceId("melee"));
        assertEquals(Optional.of(TellAnswer.DODGE), bound.telegraph().answer(),
                "DOC-05: the line asks to dodge, a melee hit would derive BLOCK");
        assertEquals(TellAnswer.DODGE, DangerRules.answer(bound));
        var declared = GUARDIAN.replace("{ MythicID: GuardianMelee, CD: 40 }",
                "{ MythicID: GuardianMelee, CD: 40, Telegraph: { Answer: BACK_OFF } }");
        var kept = binder(line).bind(profiles(declared)).profiles().get("Guardian").profile()
                .choices().get(new ChoiceId("melee"));
        assertEquals(TellAnswer.BACK_OFF, DangerRules.answer(kept), "the declaration of the profile wins");
        var plain = MythicFixtures.definitions()
                .add(MythicFixtures.mob("Guardian", "ZOMBIE", Map.of("Predictor", true)))
                .add(MythicFixtures.skill("GuardianMelee", Map.of("Cooldown", 2, "Intention", "DAMAGE, MELEE")));
        var derived = binder(plain).bind(profiles(GUARDIAN)).profiles().get("Guardian").profile()
                .choices().get(new ChoiceId("melee"));
        assertTrue(derived.telegraph().answer().isEmpty());
        assertEquals(TellAnswer.BLOCK, DangerRules.answer(derived));
    }

    private static Skill skill(String id, String value) {
        return MythicFixtures.skill(id, Map.of("PredictValues", List.of(value)));
    }

    private static String purposeProfile(String choices, String tactics) {
        return """
                schemaVersion: 2
                guardian_ai:
                  MythicID: Guardian
                  Objective: Survive while defeating the current target
                  Choices:
                %s
                  Tactics:
                    %s
                """.formatted(choices.stripTrailing(), tactics.strip());
    }

    private MythicFixtures.Definitions standardDefinitions() {
        return MythicFixtures.definitions()
                .add(MythicFixtures.mob("Guardian", "ZOMBIE", Map.of("Predictor", true)))
                .add(MythicFixtures.skill("GuardianMelee", Map.of("PredictValues", List.of("MELEE_ATTACK", "IGNORES_SHIELD"))));
    }

    private static MythicProfileBinder binder(MythicDefinitionLookup definitions) {
        return new MythicProfileBinder(definitions);
    }

    private Map<String, TacticalProfile> profiles(String yaml) throws Exception {
        var folder = Files.createTempDirectory(directory, "ai");
        Files.writeString(folder.resolve("profiles.yml"), yaml);
        var result = new ProfileLoader().load(folder);
        assertEquals(List.of(), result.problems());
        return result.profiles();
    }

    private static List<String> messages(BindingResult result) {
        return result.problems().stream().map(problem -> problem.path() + ": " + problem.message()).toList();
    }

    @Test
    void tacticsIdenticalInEverythingGetAWarningNamingTheOneJevSees() throws Exception {
        var twins = GUARDIAN.replace("hold: { Movement: HOLD, Description: Wait for the target to come closer }",
                "hold: { Movement: HOLD, Description: Wait for the target to come closer }\n"
                        + "    wait: { Movement: HOLD, Description: Wait for the target to come closer, Priority: 5 }");
        var result = binder(standardDefinitions()).bind(profiles(twins));
        assertEquals(List.of(VALUES_LEFT, "guardian_ai.Tactics.wait: Tactics.hold and Tactics.wait describe the same"
                + " option to Jev; Jev only sees 'hold'"), messages(result));
        assertEquals(Set.of("Guardian"), result.profiles().keySet());
    }

    /** Resolves only {@code minecraft:zombie} and the tag {@code mipack:duelistas}; counts every read. */
    private static final class Registry implements SelectorRegistry {
        int reads;

        @Override
        public Optional<EntityType> entityType(NamespacedKey key) {
            reads++;
            return key.asString().equals("minecraft:zombie") ? Optional.of(EntityType.ZOMBIE) : Optional.empty();
        }

        @Override
        public Optional<Set<EntityType>> entityTag(NamespacedKey key) {
            reads++;
            return key.asString().equals("mipack:duelistas") ? Optional.of(Set.of(EntityType.SKELETON))
                    : Optional.empty();
        }

        @Override
        public Set<String> teamNames() {
            reads++;
            return Set.of();
        }
    }

    @Test
    void eachBoundProfileGetsItsPolicyAndAnUnresolvedSelectorIsAWarningWithItsPath() throws Exception {
        var registry = new Registry();
        var defaults = new ParticipantDefaults(new ParticipantRules(Set.of("SURVIVAL", "ADVENTURE"),
                List.of("type:minecraft:dragon", "tag:mipack:duelistas"), List.of(), false), true, 8);
        var loaded = profiles(GUARDIAN + "  Participants:\n    Entities: { Allow: ['type:zombie', 'tag:mipack:nada'] }\n");
        var result = new MythicProfileBinder(standardDefinitions(), defaults, registry).bind(loaded);
        assertEquals(List.of("participants.entities.allow[0]: unknown entity type 'minecraft:dragon' is ignored",
                VALUES_LEFT,
                "guardian_ai.Participants.Entities.Allow[1]: unknown entity type tag 'mipack:nada' is ignored; after"
                        + " /minecraft:reload run /mm reload"), messages(result));
        var policy = result.profiles().get("Guardian").participants();
        assertTrue(policy.admitsEntities());
        assertEquals(loaded.get("Guardian").participants(), result.profiles().get("Guardian").profile().participants());
    }

    @Test
    void withTheDefaultsTheBinderNeverReadsTheRegistriesAndThePolicyIsThatOf001() throws Exception {
        var registry = new Registry();
        var result = new MythicProfileBinder(standardDefinitions(), ParticipantDefaults.STANDARD, registry)
                .bind(profiles(GUARDIAN));
        assertEquals(List.of(VALUES_LEFT), messages(result));
        assertEquals(0, registry.reads);
        var policy = result.profiles().get("Guardian").participants();
        assertFalse(policy.admitsEntities());
        assertFalse(policy.perceivesEntities());
    }
}
