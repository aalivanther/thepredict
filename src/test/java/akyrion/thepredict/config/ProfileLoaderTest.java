package akyrion.thepredict.config;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.ReactionBands;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.NativeLevelMode;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ParticipantRules;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.StanceNativeMode;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.ThreatWeights;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileLoaderTest {
    private static final String MINIMAL = """
            guardian_ai:
              MythicID: Guardian
              Objective: Survive while defeating the current target
              Choices:
                melee:
                  MythicID: GuardianMelee
              Tactics:
                press_and_strike:
                  Movement: APPROACH
                  Choice: melee
                hold:
                  Movement: HOLD
            """;

    @TempDir
    Path directory;

    @Test
    void loadsACompleteProfileIntoATacticalProfile() throws Exception {
        write("guardian.yml", """
                guardian_ai:
                  MythicID: Guardian
                  TypeOfMythicID: MOB
                  Type: NORMAL
                  Objective: Survive while defeating the current target
                  Choices:
                    melee: { MythicID: GuardianMelee, TypeOfMythicID: SKILL, RequiresTarget: true, CD: 40 }
                    punch: { Kind: VANILLA_MELEE, RequiresTarget: false }
                  Tactics:
                    press_and_strike: { Movement: APPROACH, Choice: melee, Priority: 70 }
                    flank_and_strike: { Movement: FLANK, Choice: melee, Description: Strike from the side }
                    hold: { Movement: HOLD }
                  Contexts:
                    low_health:
                      Conditions:
                      - healthpercent{p=<30%}
                      Priority: 90
                      Tactics: [hold]
                      DirectTactic: hold
                    attacking_phase: { Priority: 40, Tactics: [press_and_strike, flank_and_strike] }
                  Movement:
                    Band: { Inner: 5, Outer: 9 }
                    Retreat: { Distance: 12, Speed: 1.3 }
                    Approach: { Speed: 1.1 }
                    Arena: { Radius: 24 }
                    MeleeReach: 3
                    RangedReach: 20
                  Health: { Critical: 0.25, Wounded: 0.5 }
                  Threat: { AssistsOthers: 2.0, MaxPerWindow: 30 }
                  BaseAI:
                  - 1 meleeAttack
                  Timing: { MinimumConfidence: 0.6, SwitchMargin: 0.2 }
                """);
        var result = new ProfileLoader().load(directory);
        var profile = result.profiles().get("Guardian");

        assertEquals(List.of(), result.problems());
        assertEquals("guardian_ai", profile.id());
        assertEquals(List.of(new TacticId("press_and_strike"), new TacticId("flank_and_strike"), new TacticId("hold")),
                List.copyOf(profile.tactics().keySet()));
        assertEquals(70, profile.tactic(new TacticId("press_and_strike")).orElseThrow().priority());
        assertEquals(ProfileDefaults.PRIORITY, profile.tactic(new TacticId("hold")).orElseThrow().priority());
        assertEquals(Optional.of("Strike from the side"),
                profile.tactic(new TacticId("flank_and_strike")).orElseThrow().description());
        var melee = profile.choices().get(new ChoiceId("melee"));
        assertEquals(Optional.of("GuardianMelee"), melee.mythicSkillId());
        assertEquals(40, melee.extraCooldownTicks());
        assertEquals(Set.of(), melee.values());
        var punch = profile.choices().get(new ChoiceId("punch"));
        assertEquals(ChoiceKind.VANILLA_MELEE, punch.kind());
        assertFalse(punch.requiresTarget());
        assertEquals(List.of("healthpercent{p=<30%}"), profile.contexts().getFirst().nativeConditions());
        assertEquals(Optional.of(new TacticId("hold")), profile.contexts().getFirst().directTactic());
        assertTrue(profile.contexts().get(1).isShortcut());
        assertEquals(3.0, profile.movement().meleeReachBlocks());
        assertEquals(12.0, profile.movement().retreatDistanceBlocks());
        assertEquals(24.0, profile.movement().arenaRadiusBlocks().orElseThrow());
        assertEquals(new HealthThresholds(0.25, 0.5), profile.health());
        assertEquals(new ThreatWeights(2.0, 1.0, 0.25, 2.0, 30.0), profile.threat());
        assertEquals(List.of("1 meleeAttack"), profile.baseAi());
        assertEquals(new DecisionTiming(20, 20, 40, 0.6, 0.2, 1).withCasting(CastMode.DEFAULT, 0)
                .withLayer(TickLayerMode.DEFAULT, ReactionScope.DEFAULT, ReactionBands.DEFAULT_AHEAD, 0,
                        DecisionCadence.DEFAULT, 6, 10), profile.timing());
        assertEquals("AIConfig/Profiles/guardian.yml", result.files().get("guardian_ai"));
    }

    @Test
    void absentSectionsInheritConfigurationDefaults() throws Exception {
        write("guardian.yml", MINIMAL);
        var defaults = new ProfileDefaults(2, 20, 60, 0.2, 0.5, 3, 6, 10, 18, 11, 1.0, 1.0);
        var settings = new PluginSettings(JevSettings.standard(), DecisionSettings.standard(), defaults,
                ParticipantSettings.standard());
        var profile = new ProfileLoader(settings).load(directory).profiles().get("Guardian");

        assertEquals(defaults.movement(), profile.movement());
        assertEquals(defaults.health(), profile.health());
        assertEquals(ProfileDefaults.THREAT, profile.threat());
        assertEquals(settings.timing(), profile.timing());
        assertTrue(profile.choices().get(new ChoiceId("melee")).requiresTarget());
        assertEquals(List.of(), profile.baseAi());
    }

    @Test
    void errorNamesTheFileTheProfileAndTheFullPath() throws Exception {
        write("guardian.yml", MINIMAL.replace("Choice: melee", "Choice: mele"));

        assertEquals(List.of("AIConfig/Profiles/guardian.yml: guardian_ai.Tactics.press_and_strike.Choice: unknown choice 'mele'"),
                problems());
    }

    @Test
    void unknownFieldsAreRejectedAtEveryLevel() throws Exception {
        assertProblem(MINIMAL + "  Colldown: 3\n", "guardian_ai.Colldown: is not a known field");
        assertProblem(MINIMAL.replace("MythicID: GuardianMelee", "MythicID: GuardianMelee\n      Colldown: 20"),
                "guardian_ai.Choices.melee.Colldown: is not a known field");
        assertProblem(MINIMAL.replace("Movement: HOLD", "Movement: HOLD\n      Speed: 2"),
                "guardian_ai.Tactics.hold.Speed: is not a known field");
        assertProblem(MINIMAL + "  Contexts:\n    idle: { Tactics: [hold], Weight: 3 }\n",
                "guardian_ai.Contexts.idle.Weight: is not a known field");
        assertProblem(MINIMAL + "  Movement:\n    Band: { Inner: 5, Middle: 7 }\n",
                "guardian_ai.Movement.Band.Middle: is not a known field");
        assertProblem(MINIMAL + "  Health: { Low: 0.2 }\n", "guardian_ai.Health.Low: is not a known field");
        assertProblem(MINIMAL + "  Threat: { Taunts: 1 }\n", "guardian_ai.Threat.Taunts: is not a known field");
        assertProblem(MINIMAL + "  Timing: { HeartbeatTicks: 5 }\n", "guardian_ai.Timing.HeartbeatTicks: is not a known field");
    }

    @Test
    void schemaOneFieldsInASchemaTwoFilePointToTheMigration() throws Exception {
        assertProblem(MINIMAL + "  Operations: []\n",
                "guardian_ai.Operations: is a schema 1 field; review the migration proposal under migration/");
        assertProblem(MINIMAL.replace("MythicID: GuardianMelee", "MythicID: GuardianMelee\n      Priority: 50"),
                "guardian_ai.Choices.melee.Priority: is a schema 1 field; review the migration proposal under migration/");
    }

    @Test
    void invalidAndReservedTacticNamesAreRejected() throws Exception {
        assertProblemStartsWith(MINIMAL.replace("hold:", "Hold:"), "guardian_ai.Tactics.Hold: is not a valid tactic name");
        assertProblemStartsWith(MINIMAL.replace("hold:", "ho:"), "guardian_ai.Tactics.ho: is not a valid tactic name");
        assertProblem(MINIMAL.replace("hold:", "none:"),
                "guardian_ai.Tactics.none: uses a reserved tactic name ('none' or the prefix 'debug_')");
        assertProblem(MINIMAL.replace("hold:", "debug_hold:"),
                "guardian_ai.Tactics.debug_hold: uses a reserved tactic name ('none' or the prefix 'debug_')");
        assertProblemStartsWith(MINIMAL.replace("melee:", "Melee:").replace("Choice: melee", "Choice: Melee"),
                "guardian_ai.Choices.Melee: is not a valid choice name");
    }

    @Test
    void brokenReferencesAndRequiredFieldsAreRejected() throws Exception {
        assertProblem(MINIMAL.replace("Movement: HOLD", "Movement: HOLD\n  Contexts:\n    idle: { Tactics: [hold], DirectTactic: press_and_strike }"),
                "guardian_ai.Contexts.idle.DirectTactic: must be listed in Tactics");
        assertProblem(MINIMAL + "  Contexts:\n    idle: { Tactics: [hold, wait] }\n",
                "guardian_ai.Contexts.idle.Tactics: unknown tactic 'wait'");
        assertProblem(MINIMAL.replace("Movement: HOLD", "Movement: FLANK"),
                "guardian_ai.Tactics.hold.Choice: is required for Movement FLANK");
        assertProblem(MINIMAL.replace("Movement: HOLD", "Movement: DANCE"),
                "guardian_ai.Tactics.hold.Movement: unknown movement 'DANCE'");
        assertProblem(MINIMAL.replace("  MythicID: Guardian\n", ""), "guardian_ai.MythicID: is required");
        assertProblem(MINIMAL.replace("  Tactics:", "  Other:"), "guardian_ai.Other: is not a known field");
        assertProblem(MINIMAL.replace("MythicID: GuardianMelee", "Kind: VANILLA_MELEE\n      MythicID: GuardianMelee"),
                "guardian_ai.Choices.melee.MythicID: must be absent for Kind VANILLA_MELEE");
        assertProblem(MINIMAL.replace("MythicID: GuardianMelee", "Kind: MAGIC\n      MythicID: GuardianMelee"),
                "guardian_ai.Choices.melee.Kind: must be MYTHIC_SKILL or VANILLA_MELEE, was 'MAGIC'");
        assertProblem(MINIMAL.replace("MythicID: GuardianMelee", "MythicID: GuardianMelee\n      CD: -1"),
                "guardian_ai.Choices.melee.CD: must be >= 0 ticks, was -1");
        assertProblem(MINIMAL.replace("MythicID: GuardianMelee", "MythicID: GuardianMelee\n      CD: 2.0"),
                "guardian_ai.Choices.melee.CD: must be an integer");
        assertProblem(MINIMAL.replace("  Objective:", "  Type: TRAINED\n  Objective:"),
                "guardian_ai.Type: only supports NORMAL in 0.0.1, was 'TRAINED'");
    }

    @Test
    void contextConditionsFollowTheShortcutRule() throws Exception {
        assertProblem(MINIMAL + "  Contexts:\n    idle:\n      Conditions:\n      - hastarget\n      Tactics: [hold]\n",
                "guardian_ai.Contexts.idle.Conditions: must be absent: 'idle' is a shortcut context computed without native conditions");
        assertProblem(MINIMAL + "  Contexts:\n    wounded: { Tactics: [hold] }\n",
                "guardian_ai.Contexts.wounded.Conditions: must list at least one MythicMobs condition line");
        assertProblem(MINIMAL + "  Contexts:\n    wounded: { Conditions: [' '], Tactics: [hold] }\n",
                "guardian_ai.Contexts.wounded.Conditions[0]: must be a non-empty string");
        assertProblem(MINIMAL + "  Contexts:\n    idle: { Tactics: [hold, hold] }\n",
                "guardian_ai.Contexts.idle.Tactics: lists 'hold' twice");
    }

    @Test
    void atMostSixteenContextsArePerProfile() throws Exception {
        var contexts = new StringBuilder("  Contexts:\n");
        for (int index = 0; index < 16; index++) {
            contexts.append("    c").append(index).append(": { Conditions: [hastarget], Priority: ").append(index)
                    .append(", Tactics: [hold] }\n");
        }
        write("guardian.yml", MINIMAL + contexts);
        assertEquals(List.of(), problems());

        write("guardian.yml", MINIMAL + contexts + "    c16: { Conditions: [hastarget], Priority: 16, Tactics: [hold] }\n");
        assertEquals(List.of("AIConfig/Profiles/guardian.yml: guardian_ai.Contexts: must declare at most 16 contexts, had 17"), problems());
    }

    @Test
    void equalContextPrioritiesLoadWithAWarning() throws Exception {
        write("guardian.yml", MINIMAL + """
                  Contexts:
                    idle: { Priority: 30, Tactics: [hold] }
                    tracking_enemy: { Priority: 30, Tactics: [press_and_strike] }
                """);
        var result = new ProfileLoader().load(directory);

        assertEquals(1, result.profiles().size());
        assertEquals(List.of(ConfigProblem.warning("AIConfig/Profiles/guardian.yml", "guardian_ai.Contexts.tracking_enemy.Priority",
                "has the same Priority 30 as context 'idle'; the one declared first wins when both are active")),
                result.problems());
    }

    @Test
    void prioritiesStayBetweenZeroAndOneHundred() throws Exception {
        assertProblem(MINIMAL.replace("Movement: HOLD", "Movement: HOLD\n      Priority: 101"),
                "guardian_ai.Tactics.hold.Priority: must be between 0 and 100, was 101");
        assertProblem(MINIMAL + "  Contexts:\n    idle: { Priority: -1, Tactics: [hold] }\n",
                "guardian_ai.Contexts.idle.Priority: must be between 0 and 100, was -1");
    }

    @Test
    void numericSectionsReportTheirYamlNames() throws Exception {
        assertProblem(MINIMAL + "  Movement:\n    Band: { Inner: 9, Outer: 5 }\n",
                "guardian_ai.Movement: Band.Inner must be < Band.Outer, was 9.0 >= 5.0");
        assertProblem(MINIMAL + "  Movement:\n    Arena: { Radius: 8 }\n",
                "guardian_ai.Movement: Band.Outer must be < Arena.Radius, was 9.0 >= 8.0");
        assertProblem(MINIMAL + "  Movement:\n    Retreat: { Speed: 3.5 }\n",
                "guardian_ai.Movement.Retreat.Speed: must be between 0.1 and 3.0, was 3.5");
        assertProblemStartsWith(MINIMAL + "  Health: { Critical: 0.7 }\n", "guardian_ai.Health: health thresholds must satisfy");
        assertProblem(MINIMAL + "  Threat: { BlockedHits: -1 }\n",
                "guardian_ai.Threat: BlockedHits must be finite and >= 0, was -1.0");
        assertProblem(MINIMAL + "  Threat: { MaxPerWindow: 0 }\n", "guardian_ai.Threat.MaxPerWindow: must be > 0, was 0.0");
        assertProblem(MINIMAL + "  Timing: { MinimumConfidence: 1.0 }\n",
                "guardian_ai.Timing.MinimumConfidence: must be between 0.0 and 0.99, was 1.0");
        assertProblem(MINIMAL + "  BaseAI: [1 meleeAttack, 3]\n", "guardian_ai.BaseAI[1]: must be a non-empty string");
    }

    @Test
    void aBrokenProfileDoesNotStopTheOthers() throws Exception {
        write("a.yml", MINIMAL + MINIMAL.replace("guardian_ai", "archer_ai").replace("Guardian", "Archer")
                .replace("Choice: melee", "Choice: bow"));
        write("b.yml", MINIMAL.replace("guardian_ai", "brute_ai").replace("Guardian", "Brute"));
        var result = new ProfileLoader().load(directory);

        assertEquals(Set.of("Guardian", "Brute"), result.profiles().keySet());
        assertEquals(List.of("AIConfig/Profiles/a.yml: archer_ai.Tactics.press_and_strike.Choice: unknown choice 'bow'"),
                result.problems().stream().map(ConfigProblem::toString).toList());
    }

    @Test
    void twoProfilesForTheSameMobAreBothExcluded() throws Exception {
        write("a.yml", MINIMAL);
        write("b.yml", MINIMAL.replace("guardian_ai", "other_ai"));
        write("c.yml", MINIMAL.replace("guardian_ai", "brute_ai").replace("Guardian", "Brute"));
        var result = new ProfileLoader().load(directory);

        assertEquals(Set.of("Brute"), result.profiles().keySet());
        assertEquals(2, result.problems().size());
        assertTrue(result.problems().getFirst().toString().startsWith("AIConfig/Profiles/a.yml: guardian_ai.MythicID: mob 'Guardian' has 2 profiles"));
    }

    @Test
    void theSameProfileIdInTwoFilesExcludesBoth() throws Exception {
        write("a.yml", MINIMAL);
        write("b.yml", MINIMAL.replace("Guardian", "Brute"));
        var result = new ProfileLoader().load(directory);

        assertTrue(result.profiles().isEmpty());
        assertEquals("AIConfig/Profiles/b.yml: guardian_ai: profile ID is declared 2 times"
                + " (AIConfig/Profiles/a.yml, AIConfig/Profiles/b.yml); every copy is excluded",
                result.problems().get(1).toString());
    }

    @Test
    void schemaOneFilesAreReportedAndNotLoaded() throws Exception {
        Files.writeString(directory.resolve("old.yml"), """
                old_ai:
                  MythicID: Old
                  Objective: Survive
                  Choices:
                    defense: { MythicID: DefenseSkill }
                  Behaviors: [Idle]
                """);
        Files.writeString(directory.resolve("choices-only.yml"), """
                plain_ai:
                  MythicID: Plain
                  Objective: Survive
                  Choices:
                    defense: { MythicID: DefenseSkill }
                """);
        write("current.yml", MINIMAL);
        var result = new ProfileLoader().load(directory);

        assertEquals(Set.of("Guardian"), result.profiles().keySet());
        assertEquals(2, result.problems().size());
        assertTrue(result.problems().stream().allMatch(problem -> problem.path().equals("schemaVersion")
                && problem.message().contains("schema 1 profile file")));
    }

    @Test
    void schemaVersionMustBeTwo() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), MINIMAL);
        Files.writeString(directory.resolve("future.yml"), "schemaVersion: 3\n" + MINIMAL.replace("Guardian", "Future"));

        assertEquals(List.of("AIConfig/Profiles/future.yml: schemaVersion: must be 2, was 3",
                "AIConfig/Profiles/guardian.yml: schemaVersion: must be 2, was nothing"), problems());
    }

    @Test
    void unreadableFilesAreCountedAndOnlyAllOfThemIsAGlobalFailure() throws Exception {
        assertFalse(new ProfileLoader().load(directory).unreadable());
        Files.writeString(directory.resolve("broken.yml"), "guardian_ai: [unfinished\n");
        var onlyBroken = new ProfileLoader().load(directory);
        assertTrue(onlyBroken.unreadable());
        assertTrue(onlyBroken.problems().getFirst().toString().startsWith("AIConfig/Profiles/broken.yml: is not valid YAML"));

        write("guardian.yml", MINIMAL);
        var mixed = new ProfileLoader().load(directory);
        assertFalse(mixed.unreadable());
        assertEquals(2, mixed.fileCount());
        assertEquals(1, mixed.unreadableFiles());
        assertEquals(1, mixed.profiles().size());
    }

    @Test
    void ignoresFilesThatAreNotYaml() throws Exception {
        Files.writeString(directory.resolve("example.yml.example"), "example_ai:\n  MythicID: Placeholder\n");
        Files.writeString(directory.resolve("README.txt"), "Setup guide");
        var result = new ProfileLoader().load(directory);

        assertEquals(0, result.fileCount());
        assertTrue(result.profiles().isEmpty());
        assertTrue(result.problems().isEmpty());
    }

    @Test
    void topLevelValueThatIsNotAProfileIsAnError() throws Exception {
        write("guardian.yml", MINIMAL + "stray: 3\n");

        assertEquals(List.of("AIConfig/Profiles/guardian.yml: stray: must be a section"), problems());
    }

    @Test
    void bundledExamplesLoadWithoutProblems() throws Exception {
        var locations = new ConfigLocations(directory.resolve("MythicMobs"), directory);
        new FirstRunFiles().installMissing(locations);
        var examples = new ProfileLoader().load(directory.resolve("examples/AI"));
        assertEquals(List.of(), examples.problems());
        var guardian = examples.profiles().get("ThePredictGuardian");
        assertEquals(8, guardian.tactics().size());
        assertEquals(Movement.FLANK, guardian.tactic(new TacticId("flank_and_strike")).orElseThrow().movement());
        assertTrue(guardian.choices().values().stream().noneMatch(SkillChoice::isTyped),
                "Intention is completed when the profile binds");
        assertTrue(guardian.contexts().stream().allMatch(context -> context.isShortcut()));

        Files.copy(locations.profilesDirectory().resolve("example.yml.example"),
                locations.profilesDirectory().resolve("example.yml"));
        var template = new ProfileLoader().load(locations.profilesDirectory());
        assertEquals(List.of(), template.problems());
        assertEquals(List.of(new ContextId("wounded"), new ContextId("idle")),
                template.profiles().get("ReplaceWithYourMythicMobId").contexts().stream().map(context -> context.id()).toList());
    }

    @Test
    void domainErrorsOfAProfileNameTheFileOnceAndTheYamlKey() throws Exception {
        assertProblem(MINIMAL + "  Patterns:\n    AgainstEntities: frie\n",
                "guardian_ai.Patterns.AgainstEntities: must be free|fair");
        assertProblem(MINIMAL + "  Telegraph:\n    Commitment: 40\n",
                "guardian_ai.Telegraph.Commitment: must be within [50, 100], was 40");
        assertProblem(MINIMAL + "  Stances:\n    MinDwellTicks: 2\n",
                "guardian_ai.Stances.MinDwellTicks: must be within [4, 200], was 2");
        assertProblem(MINIMAL + "  Zone:\n    Radius: 100\n",
                "guardian_ai.Zone.Radius: must be within [8, 64], was 100");
        assertProblem(MINIMAL + "  Group:\n    Weight: 9\n",
                "guardian_ai.Group.Weight: must be within [1, 4], was 9");
        assertProblem(MINIMAL + "  Difficulty:\n    StartRung: 9\n",
                "guardian_ai.Difficulty.StartRung: must be within [0, 8], was 9");
    }

    @Test
    void aBareOffIsAcceptedOnEveryProfileModeKey() throws Exception {
        write("guardian.yml", MINIMAL + """
                  Zone: { Mode: off, HazardPush: off }
                  Group: { Mode: off }
                  Patterns: { Mode: off }
                  Memory: { Mode: off }
                  Distill: { Mode: off }
                """);
        var result = new ProfileLoader().load(directory);
        assertEquals(List.of(), result.problems().stream().map(ConfigProblem::toString).toList());
        var profile = result.profiles().get("Guardian");
        assertEquals("OFF", profile.zone().mode().orElseThrow().name());
        assertEquals("OFF", profile.zone().hazardPush().name());
        assertEquals("OFF", profile.group().mode().orElseThrow().name());
        assertEquals("OFF", profile.patterns().mode().orElseThrow().name());
        assertEquals("OFF", profile.memory().mode().orElseThrow().name());
        assertEquals("OFF", profile.distillMode().orElseThrow().name());
    }

    @Test
    void theStanceNativeLevelAndDecisionModeKeysAlsoTakeABareOffOrOn() throws Exception {
        write("guardian.yml", MINIMAL + """
                  Stances: { Native: off }
                  Difficulty: { NativeLevel: off }
                  Decision: { Pruning: off, Diversity: on }
                """);
        var result = new ProfileLoader().load(directory);
        var profile = result.profiles().get("Guardian");
        assertTrue(profile != null, "DOC-07: " + result.problems());
        assertEquals(StanceNativeMode.OFF, profile.stances().nativeMode());
        assertEquals(NativeLevelMode.OFF, profile.difficulty().nativeLevel());
        assertEquals("off", profile.decision().pruning());
        assertEquals("on", profile.decision().diversity());
        assertEquals(List.of(INERT.formatted("Decision.Pruning"), INERT.formatted("Decision.Diversity")),
                result.problems().stream().map(ConfigProblem::toString).toList());
    }

    @Test
    void aProfileKeyThisBuildDoesNotReadWarnsOnlyWhenItAsksForSomethingElse() throws Exception {
        write("guardian.yml", MINIMAL + """
                  Stances: { Describe: true }
                  Group: { PlanQuestion: every_heartbeat }
                  Decision: { MaxOptions: 3, CandidateScoring: utility }
                """);
        assertEquals(List.of(INERT.formatted("Stances.Describe"), INERT.formatted("Group.PlanQuestion"),
                INERT.formatted("Decision.MaxOptions"), INERT.formatted("Decision.CandidateScoring")), problems());
        write("guardian.yml", MINIMAL + """
                  Stances: { Describe: false }
                  Group: { PlanQuestion: on_change }
                  Decision: { Pruning: inherit, MaxOptions: 0, CandidateScoring: inherit, Diversity: inherit }
                """);
        assertEquals(List.of(), problems());
    }

    private static final String INERT = "AIConfig/Profiles/guardian.yml: guardian_ai.%s: has no effect in this build";

    private void write(String file, String profiles) throws Exception {
        Files.writeString(directory.resolve(file), "schemaVersion: 2\n" + profiles);
    }

    private List<String> problems() throws Exception {
        return new ProfileLoader().load(directory).problems().stream().map(ConfigProblem::toString).toList();
    }

    private void assertProblem(String profiles, String expected) throws Exception {
        write("guardian.yml", profiles);
        assertEquals(List.of("AIConfig/Profiles/guardian.yml: " + expected), problems());
    }

    private void assertProblemStartsWith(String profiles, String expected) throws Exception {
        write("guardian.yml", profiles);
        var problems = problems();
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().startsWith("AIConfig/Profiles/guardian.yml: " + expected), problems.getFirst());
    }

    @Test
    void theQuestionDetailAndTheFitChecksInheritTheGlobalDefaultsAndMayBeSetPerProfile() throws Exception {
        write("guardian.yml", MINIMAL);
        var inherited = new ProfileLoader().load(directory).profiles().get("Guardian").timing();
        assertEquals(QuestionDetail.FULL, inherited.questionDetail());
        assertFalse(inherited.fitChecks());
        var standard = PluginSettings.standard();
        var questions = new QuestionSettings(8, QuestionDetail.STANDARD, false, true, 0.2, 0.7);
        var decision = standard.decision();
        var global = new PluginSettings(standard.jev(), new DecisionSettings(decision.maxRequestsPerMinute(),
                decision.maxRequestsPerMinutePerWorld(), decision.maxInFlightRequests(),
                decision.maxInFlightRequestsPerWorld(), decision.maxInputTokensPerMinute(),
                decision.encounterHeartbeatTicks(), decision.contingenciesPerMob(), decision.minimumConfidence(),
                decision.tacticSwitchMargin(), decision.maxInstallsPerTick(), decision.maxEncounterEvaluationsPerTick(),
                decision.maxProbesPerTick(), decision.maxCompletionsPerTick(), decision.maxSignalsPerTick(),
                decision.maxTickWorkMicros(), decision.maxDecisionAgeMillis(), decision.lowConfidenceStreakForLocal(),
                decision.searchRadiusBlocks(), decision.logDecisions(), decision.recordScenarios(), questions),
                standard.profileDefaults(), standard.participants());
        assertEquals(QuestionDetail.STANDARD, new ProfileLoader(global).load(directory).profiles().get("Guardian")
                .timing().questionDetail());
        write("guardian.yml", MINIMAL + "  Timing: { QuestionDetail: full, FitChecks: true, MinimumConfidence: 0.7 }\n");
        var own = new ProfileLoader(global).load(directory);
        assertEquals(List.of(), own.problems());
        var timing = own.profiles().get("Guardian").timing();
        assertEquals(QuestionDetail.FULL, timing.questionDetail());
        assertTrue(timing.sendsFitChecks());
        assertEquals(0.7, timing.minimumConfidence());
    }

    @Test
    void fitChecksWithoutTheFullDetailLoadWithAWarningAndAreAnErrorWhereForbidden() throws Exception {
        write("guardian.yml", MINIMAL + "  Timing: { QuestionDetail: compact, FitChecks: true }\n");
        var result = new ProfileLoader().load(directory);
        assertEquals(1, result.profiles().size());
        assertEquals(List.of(ConfigProblem.warning("AIConfig/Profiles/guardian.yml", "guardian_ai.Timing.FitChecks",
                "is ignored: fit checks are only sent with QuestionDetail full, and this profile uses compact")),
                result.problems());
        var standard = PluginSettings.standard();
        var decision = standard.decision();
        var forbidding = new PluginSettings(standard.jev(), new DecisionSettings(decision.maxRequestsPerMinute(),
                decision.maxRequestsPerMinutePerWorld(), decision.maxInFlightRequests(),
                decision.maxInFlightRequestsPerWorld(), decision.maxInputTokensPerMinute(),
                decision.encounterHeartbeatTicks(), decision.contingenciesPerMob(), decision.minimumConfidence(),
                decision.tacticSwitchMargin(), decision.maxInstallsPerTick(), decision.maxEncounterEvaluationsPerTick(),
                decision.maxProbesPerTick(), decision.maxCompletionsPerTick(), decision.maxSignalsPerTick(),
                decision.maxTickWorkMicros(), decision.maxDecisionAgeMillis(), decision.lowConfidenceStreakForLocal(),
                decision.searchRadiusBlocks(), decision.logDecisions(), decision.recordScenarios(),
                new QuestionSettings(8, QuestionDetail.FULL, false, false, 0.2, 0.7)), standard.profileDefaults(),
                standard.participants());
        assertEquals(List.of("AIConfig/Profiles/guardian.yml: guardian_ai.Timing.FitChecks: must be false while"
                + " decision.fitChecksAllowed is false"), new ProfileLoader(forbidding).load(directory).problems()
                .stream().map(ConfigProblem::toString).toList());
        assertProblem(MINIMAL + "  Timing: { QuestionDetail: verbose }\n",
                "guardian_ai.Timing.QuestionDetail: must be compact, standard or full");
    }

    @Test
    void aProfileWithoutParticipantsInheritsTheGlobalModesAndPerception() throws Exception {
        write("guardian.yml", MINIMAL);
        assertEquals(new ParticipantRules(Set.of("SURVIVAL", "ADVENTURE"), List.of(), List.of(), false),
                new ProfileLoader().load(directory).profiles().get("Guardian").participants());
    }

    @Test
    void theParticipantsOfAProfileAreReadAndItsModesMustBeAmongTheGlobalOnes() throws Exception {
        write("guardian.yml", MINIMAL + """
                  Participants:
                    PlayerGameModes: []
                    Entities:
                      Allow: ['faction:duel_blue', 'kind:MONSTER']
                      Deny: ['type:creeper']
                """);
        var rules = new ProfileLoader().load(directory).profiles().get("Guardian").participants();
        assertTrue(rules.playerGameModes().isEmpty());
        assertEquals(List.of("faction:duel_blue", "kind:MONSTER"), rules.entityAllow());
        assertEquals(List.of("type:creeper"), rules.entityDeny());
        assertFalse(rules.perceiveNonTargets());
        assertProblem(MINIMAL + "  Participants:\n    PlayerGameModes: [CREATIVE]\n",
                "guardian_ai.Participants.PlayerGameModes[0]: must be one of the global participants.playerGameModes"
                        + " [SURVIVAL, ADVENTURE], was 'CREATIVE'");
        assertProblem(MINIMAL + "  Participants:\n    Entities: { PerceiveNonTargets: true }\n",
                "guardian_ai.Participants.Entities.PerceiveNonTargets: can only be true while"
                        + " participants.entities.perceiveNonTargets is true");
        assertProblem(MINIMAL + "  Participants:\n    Entities: { Allow: ['specie:zombie'] }\n",
                "guardian_ai.Participants.Entities.Allow[0]: has the unknown prefix 'specie'; use type, tag,"
                        + " category, kind, mythic, faction, team, tagged or predictor");
        assertProblem(MINIMAL + "  Participants:\n    Players: []\n",
                "guardian_ai.Participants.Players: is not a known field");
        assertProblem(MINIMAL + "  Participants:\n    Entities: { Share: true }\n",
                "guardian_ai.Participants.Entities.Share: is not a known field");
    }

    @Test
    void withProfilesMayAllowOffTheAllowOfAProfileIsIgnoredWithAWarningAndGlobalPerceptionIsInherited()
            throws Exception {
        write("guardian.yml", MINIMAL + "  Participants:\n    Entities: { Allow: ['kind:MONSTER'] }\n");
        var standard = PluginSettings.standard();
        var global = new ParticipantSettings(new ParticipantRules(Set.of("SURVIVAL"), List.of("tagged:arena"),
                List.of(), true), false, 4);
        var settings = new PluginSettings(standard.jev(), standard.decision(), standard.profileDefaults(), global);
        var result = new ProfileLoader(settings).load(directory);
        assertEquals(List.of(ConfigProblem.warning("AIConfig/Profiles/guardian.yml", "guardian_ai.Participants.Entities.Allow",
                "is ignored: participants.entities.profilesMayAllow is false")), result.problems());
        var rules = result.profiles().get("Guardian").participants();
        assertEquals(List.of(), rules.entityAllow());
        assertEquals(Set.of("SURVIVAL"), rules.playerGameModes());
        assertTrue(rules.perceiveNonTargets());
    }
}
