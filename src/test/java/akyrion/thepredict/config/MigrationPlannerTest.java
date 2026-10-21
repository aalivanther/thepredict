package akyrion.thepredict.config;

import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationPlannerTest {
    private static final String OPENROUTER_KEY = "sk-or-v1-do-not-copy-this-key";
    private static final String SCHEMA_ONE_CONFIGURATION = """
            provider:
              type: OPENROUTER
              model: 'openai/gpt-4o-mini'
              timeoutMillis: 3000
              apiKeyEnvironment: OPENROUTER_API_KEY
              apiKey: '%s'
            decision:
              searchRadiusBlocks: 32
              maxConcurrentRequests: 8
              maxConcurrentRequestsPerWorld: 2
              maxRequestsPerMinute: 30
              maxRequestsPerMinutePerWorld: 10
              minimumSecondsBetweenRequestsPerMob: 10
              minimumSecondsAfterResponsePerMob: 5
              maxMobEvaluationsPerPoll: 16
              logDecisions: false
            profileDefaults:
              decisionIntervalTicks: 20
              lowHealthRatio: 0.3
              attackRangeBlocks: 6
            """.formatted(OPENROUTER_KEY);
    private static final String SCHEMA_ONE_GUARDIAN = """
            thepredict_guardian:
              MythicID: ThePredictGuardian
              TypeOfMythicID: MOB
              Type: NORMAL
              Objective: Survive while defeating the current target
              Choices:
                melee:
                  MythicID: ThePredictGuardianMelee
                  TypeOfMythicID: SKILL
                  CD: 40
                  Priority: 70
                ranged:
                  MythicID: ThePredictGuardianRanged
                  TypeOfMythicID: SKILL
                  CD: 60
                  Priority: 60
                defense:
                  MythicID: ThePredictGuardianDefense
                  TypeOfMythicID: SKILL
                  CD: 100
                  Priority: 90
                  RequiresTarget: false
              Behaviors: [Idle, AttackingPhase, LowHealthPhase, TrackingEnemy]
              Combinators: true
              Operations:
                - Behavior: LowHealthPhase
                  Choices: [defense, ranged]
                - Behavior: AttackingPhase
                  Choices: [melee, ranged]
                - Behavior: TrackingEnemy
                  DirectChoice: ranged
                - Behavior: Idle
                  DirectChoice: defense
            """;

    @TempDir
    Path directory;

    private final MigrationPlanner planner = new MigrationPlanner();

    private ConfigLocations locations() {
        return new ConfigLocations(directory.resolve("MythicMobs"), directory);
    }

    @Test
    void theRepositorySchemaOneConfigurationBecomesAValidSchemaTwoProposal() throws Exception {
        Files.writeString(directory.resolve("config.yml"), SCHEMA_ONE_CONFIGURATION);
        var proposal = planner.plan(locations());
        var proposed = new YamlConfiguration();
        proposed.loadFromString(proposal.files().get(ConfigLocations.proposedMaster()));
        var settings = new PluginConfigurationLoader().parse(proposed);

        assertEquals(List.of(ConfigLocations.proposedMaster()), List.copyOf(proposal.files().keySet()));
        assertEquals(JevSettings.standard(), settings.jev());
        assertEquals(30, settings.decision().maxRequestsPerMinute());
        assertEquals(10, settings.decision().maxRequestsPerMinutePerWorld());
        assertEquals(8, settings.decision().maxInFlightRequests());
        assertEquals(2, settings.decision().maxInFlightRequestsPerWorld());
        assertEquals(32.0, settings.decision().searchRadiusBlocks());
        assertEquals(20, settings.profileDefaults().minimumDecisionIntervalTicks());
        assertEquals(0.3, settings.profileDefaults().criticalHealthRatio());
        assertTrue(proposal.report().contains("provider.apiKey was not copied"));
        assertTrue(proposal.report().contains("decision.minimumSecondsBetweenRequestsPerMob has no equivalent"));
        assertTrue(proposal.report().contains("profileDefaults.attackRangeBlocks has no equivalent"));
    }

    @Test
    void theProposalNeverEnablesQueriesNorCopiesTheOpenRouterKey() throws Exception {
        Files.writeString(directory.resolve("config.yml"), SCHEMA_ONE_CONFIGURATION);
        var proposal = planner.plan(locations());
        var proposed = new YamlConfiguration();
        proposed.loadFromString(proposal.files().get(ConfigLocations.proposedMaster()));
        var jev = new PluginConfigurationLoader().parse(proposed).jev();

        assertFalse(jev.isConfigured(name -> null));
        assertEquals("", jev.apiKey());
        assertFalse(proposal.report().contains(OPENROUTER_KEY));
        assertTrue(proposal.files().values().stream().noneMatch(text -> text.contains(OPENROUTER_KEY)));
        assertTrue(proposal.files().values().stream().noneMatch(text -> text.contains("OPENROUTER_API_KEY")));
    }

    @Test
    void theSchemaOneExampleProfileBecomesTacticsWithoutMovementAndShortcutContexts() throws Exception {
        Files.createDirectories(directory.resolve("AI"));
        Files.writeString(directory.resolve("AI/guardian.yml"), SCHEMA_ONE_GUARDIAN);
        var proposal = planner.plan(locations());
        var check = Files.createDirectories(directory.resolve("check"));
        Files.writeString(check.resolve("guardian.yml"), proposal.files().get(ConfigLocations.proposedProfile("guardian.yml")));
        var result = new ProfileLoader().load(check);
        var profile = result.profiles().get("ThePredictGuardian");

        assertEquals(List.of(), result.problems());
        assertTrue(profile.tactics().values().stream().allMatch(tactic -> tactic.movement() == Movement.NONE));
        assertEquals(90, profile.tactic(new TacticId("defense")).orElseThrow().priority());
        assertEquals(List.of(new ContextId("low_health_phase"), new ContextId("attacking_phase"),
                new ContextId("tracking_enemy"), new ContextId("idle")),
                profile.contexts().stream().map(context -> context.id()).toList());
        assertEquals(List.of(100, 90, 80, 70), profile.contexts().stream().map(context -> context.priority()).toList());
        assertEquals(Optional.of(new TacticId("ranged")), profile.contexts().get(2).directTactic());
        assertTrue(proposal.report().contains("Operations[3] became context idle with Priority 70 and DirectTactic defense"));
        assertTrue(proposal.report().contains("Behaviors was dropped"));
        assertTrue(proposal.report().contains("- None."));
    }

    @Test
    void repeatingTheMigrationGivesTheSameProposalAndReusesItsDirectory() throws Exception {
        Files.writeString(directory.resolve("config.yml"), SCHEMA_ONE_CONFIGURATION);
        Files.createDirectories(directory.resolve("AI"));
        Files.writeString(directory.resolve("AI/guardian.yml"), SCHEMA_ONE_GUARDIAN);
        var first = planner.plan(locations());
        var written = planner.write(directory, first, LocalDateTime.of(2026, 9, 29, 12, 0, 0)).orElseThrow();

        assertEquals(first, planner.plan(locations()));
        assertEquals(directory.resolve("migration/20260929-120000"), written);
        assertEquals(Optional.of(written), planner.write(directory, planner.plan(locations()),
                LocalDateTime.of(2026, 9, 29, 13, 0, 0)));
        try (var entries = Files.list(directory.resolve("migration"))) {
            assertEquals(1, entries.count());
        }
        assertTrue(Files.exists(written.resolve(ConfigLocations.proposedMaster())));
        assertTrue(Files.exists(written.resolve(ConfigLocations.proposedProfile("guardian.yml"))));
        assertTrue(Files.exists(written.resolve("report.md")));
    }

    @Test
    void noAdministratorFileChanges() throws Exception {
        Files.writeString(directory.resolve("config.yml"), SCHEMA_ONE_CONFIGURATION);
        Files.createDirectories(directory.resolve("AI"));
        Files.writeString(directory.resolve("AI/guardian.yml"), SCHEMA_ONE_GUARDIAN);
        Files.writeString(directory.resolve("AI/notes.txt"), "keep me");
        var before = snapshot();

        planner.write(directory, planner.plan(locations()), LocalDateTime.of(2026, 9, 29, 12, 0, 0));

        assertEquals(before, snapshot());
    }

    @Test
    void schemaTwoFilesAreNotMigrated() throws Exception {
        new FirstRunFiles().installMissing(locations());
        Files.copy(directory.resolve("examples/AI/ThePredictGuardian.yml"),
                locations().profilesDirectory().resolve("guardian.yml"));
        var proposal = planner.plan(locations());

        assertTrue(proposal.isEmpty());
        assertEquals(Optional.empty(), planner.write(directory, proposal, LocalDateTime.of(2026, 9, 29, 12, 0, 0)));
        assertFalse(Files.exists(directory.resolve("migration")));
    }

    @Test
    void aSchemaOneProfileNextToASchemaTwoConfigurationIsMigratedAlone() throws Exception {
        new FirstRunFiles().installMissing(locations());
        Files.writeString(locations().profilesDirectory().resolve("guardian.yml"), SCHEMA_ONE_GUARDIAN);

        assertEquals(List.of(ConfigLocations.proposedProfile("guardian.yml")),
                List.copyOf(planner.plan(locations()).files().keySet()));
    }

    @Test
    void ambiguousCombinationsAreLeftForManualCorrection() throws Exception {
        Files.createDirectories(directory.resolve("AI"));
        Files.writeString(directory.resolve("AI/odd.yml"), """
                odd_ai:
                  MythicID: Odd
                  Objective: Survive
                  LowHealthRatio: 0.9
                  Mood: angry
                  Choices:
                    AI:
                      MythicID: OddSkill
                  Combinators: true
                  Operations:
                    - Behavior: Idle
                      DirectChoice: AI
                    - Behavior: Idle
                      Choices: [AI]
                    - Behavior: Tracking
                      Choices: [missing]
                """);
        var report = planner.plan(locations()).report();

        assertTrue(report.contains("odd.yml: odd_ai.Choices.AI was renamed to ai"));
        assertTrue(report.contains("became tactic use_ai"));
        assertTrue(report.contains("Operations[1] (Behavior Idle) is unknown or repeats a context"));
        assertTrue(report.contains("Operations[2] references the unknown choice 'missing'"));
        assertTrue(report.contains("LowHealthRatio (0.9) does not fit Health.Critical"));
        assertTrue(report.contains("odd_ai.Mood is not a schema 1 field"));
    }

    @Test
    void schemaOneLimitsOutsideTheNewRangesKeepTheDefaults() throws Exception {
        Files.writeString(directory.resolve("config.yml"), SCHEMA_ONE_CONFIGURATION
                .replace("maxRequestsPerMinute: 30", "maxRequestsPerMinute: 3600")
                .replace("decisionIntervalTicks: 20", "decisionIntervalTicks: 5"));
        var proposal = planner.plan(locations());
        var proposed = new YamlConfiguration();
        proposed.loadFromString(proposal.files().get(ConfigLocations.proposedMaster()));
        var settings = new PluginConfigurationLoader().parse(proposed);

        assertEquals(600, settings.decision().maxRequestsPerMinute());
        assertEquals(20, settings.profileDefaults().minimumDecisionIntervalTicks());
        assertTrue(proposal.report().contains("decision.maxRequestsPerMinute (3600) does not fit"));
        assertTrue(proposal.report().contains("profileDefaults.decisionIntervalTicks (5) does not fit"));
    }

    private Map<String, String> snapshot() throws IOException {
        var files = new TreeMap<String, String>();
        try (Stream<Path> walk = Files.walk(directory)) {
            for (var file : walk.filter(Files::isRegularFile).toList()) {
                var relative = directory.relativize(file).toString();
                if (!relative.startsWith("migration")) {
                    files.put(relative, Files.readString(file));
                }
            }
        }
        return files;
    }

    @Test
    void textValuesOfSchemaOneFieldsAreNotEchoedInTheReport() throws Exception {
        Files.writeString(directory.resolve("config.yml"), SCHEMA_ONE_CONFIGURATION
                .replace("maxRequestsPerMinute: 30", "maxRequestsPerMinute: 'sk-pasted-secret'"));
        var report = planner.plan(locations()).report();

        assertTrue(report.contains("decision.maxRequestsPerMinute (a text value) does not fit"), report);
        assertFalse(report.contains("sk-pasted-secret"));
    }
}
