package akyrion.thepredict;

import akyrion.thepredict.admin.SetupMessages;
import akyrion.thepredict.config.ConfigLocations;
import akyrion.thepredict.config.MigrationPlanner;
import akyrion.thepredict.config.PluginConfigurationLoader;
import akyrion.thepredict.config.ProfileCatalog;
import akyrion.thepredict.domain.ServiceState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Move proposals for a 0.0.1 location. Nothing is loaded, moved or deleted. */
class LegacyLocationTest {
    private static final RuntimeCompatibility.DetectedVersions VERSIONS =
            new RuntimeCompatibility.DetectedVersions(25, "26.2", "5.13.1-SNAPSHOT");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 9, 0, 0);
    private static final String SCHEMA_TWO = "schemaVersion: 2\njev:\n  timeoutMillis: 900\n";
    private static final String SCHEMA_ONE = """
            provider:
              type: OPENROUTER
              model: 'openai/gpt-4o-mini'
              apiKey: 'sk-or-v1-do-not-copy-this-key'
            decision:
              maxRequestsPerMinute: 30
            """;

    @TempDir
    Path root;

    private final List<String> log = new ArrayList<>();

    @Test
    void onlyTheOldLocationWritesAProposalWarnsAndActivatesNothing() throws Exception {
        var locations = locations();
        Files.writeString(locations.legacyMasterFile(), SCHEMA_TWO);
        var state = StartupState.load(locations, VERSIONS, NOW, log::add, name -> null);

        assertTrue(state.settings().isEmpty());
        assertTrue(state.configurationProblem().orElseThrow().toString().contains("0.0.1 location"));
        assertTrue(state.locationNotice().isEmpty());
        assertEquals("migration/20261002-090000/report.md", state.migrationReport().orElseThrow());
        assertTrue(Files.exists(locations.dataFolder().resolve(
                "migration/20261002-090000/AIConfig/config.yml.proposed")));
        assertEquals(SCHEMA_TWO, Files.readString(locations.dataFolder().resolve(
                "migration/20261002-090000/AIConfig/config.yml.proposed")));
        assertEquals(SCHEMA_TWO, Files.readString(locations.legacyMasterFile()));
        assertFalse(Files.exists(locations.masterFile()));
        var lines = SetupMessages.startup(state.report(ProfileCatalog.empty(), 0, "blocked"),
                ServiceState.INVALID_CONFIGURATION);
        assertTrue(lines.stream().anyMatch(line -> line.contains("No file was changed and no query was enabled")),
                lines.toString());
        assertTrue(SetupMessages.joinNotice(state.report(ProfileCatalog.empty(), 0, "blocked"),
                ServiceState.INVALID_CONFIGURATION).orElseThrow().contains("a migration is pending"));
    }

    @Test
    void bothLocationsLetAiConfigWinAndDoNotProposeAMove() throws Exception {
        var locations = locations();
        Files.createDirectories(locations.profilesDirectory());
        Files.writeString(locations.masterFile(), SCHEMA_TWO);
        Files.writeString(locations.legacyMasterFile(), "schemaVersion: 2\njev:\n  timeoutMillis: 1500\n");
        var legacy = Files.readString(locations.legacyMasterFile());
        var state = StartupState.load(locations, VERSIONS, NOW, log::add, name -> null);

        assertEquals(900, state.settings().orElseThrow().jev().timeoutMillis());
        assertEquals(StartupState.OLD_FILES_IGNORED, state.locationNotice().orElseThrow());
        assertTrue(state.migrationReport().isEmpty());
        assertTrue(state.configurationProblem().isEmpty());
        assertEquals(legacy, Files.readString(locations.legacyMasterFile()));
        assertFalse(Files.exists(locations.dataFolder().resolve("migration")));
        var report = state.report(ProfileCatalog.empty(), 0, "0 profiles");
        assertTrue(SetupMessages.startup(report, ServiceState.UNCONFIGURED).contains(StartupState.OLD_FILES_IGNORED));
        assertTrue(SetupMessages.joinNotice(report, ServiceState.READY).orElseThrow()
                .contains("old configuration files are ignored"));
    }

    @Test
    void onlyTheNewLocationDoesNotWarn() throws Exception {
        var state = StartupState.load(locations(), VERSIONS, NOW, log::add, name -> null);
        assertTrue(state.locationNotice().isEmpty());
        assertTrue(state.migrationReport().isEmpty());
        assertTrue(state.settings().isPresent());
    }

    @Test
    void aRepeatedStartReusesTheSameProposal() throws Exception {
        var locations = locations();
        Files.writeString(locations.legacyProfilesDirectory().resolve("guardian.yaml"), "schemaVersion: 2\nguardian_ai:\n"
                + "  MythicID: Guardian\n  Tactics:\n    hold: { Movement: HOLD, Description: Wait }\n");
        var first = StartupState.load(locations, VERSIONS, NOW, log::add, name -> null);
        var second = StartupState.load(locations, VERSIONS, NOW.plusHours(2), log::add, name -> null);
        assertEquals(first.migrationReport(), second.migrationReport());
        try (var directories = Files.list(locations.dataFolder().resolve("migration"))) {
            assertEquals(1, directories.count());
        }
        assertTrue(Files.exists(locations.dataFolder().resolve(
                "migration/20261002-090000/AIConfig/Profiles/guardian.yaml.proposed")));
    }

    @Test
    void schemaOneInTheOldLocationBecomesOneConvertedProposalAtTheNewPaths() throws Exception {
        var locations = locations();
        Files.writeString(locations.legacyMasterFile(), SCHEMA_ONE);
        Files.writeString(locations.legacyProfilesDirectory().resolve("guardian.yml"), """
                guardian_ai:
                  MythicID: Guardian
                  Objective: Survive
                  Choices:
                    melee: { MythicID: GuardianMelee }
                  Operations:
                    - Behavior: Idle
                      DirectChoice: melee
                """);
        var proposal = new MigrationPlanner().plan(locations);
        assertEquals(List.of("AIConfig/config.yml.proposed", "AIConfig/Profiles/guardian.yml.proposed"),
                List.copyOf(proposal.files().keySet()));
        var proposed = new YamlConfiguration();
        proposed.loadFromString(proposal.files().get("AIConfig/config.yml.proposed"));
        assertFalse(new PluginConfigurationLoader().isVersionOne(proposed));
        assertEquals("", new PluginConfigurationLoader().parse(proposed).jev().apiKey());
        assertFalse(proposal.files().values().stream().anyMatch(text -> text.contains("sk-or-v1-do-not-copy")));
        assertTrue(proposal.files().get("AIConfig/Profiles/guardian.yml.proposed").contains("schemaVersion: 2"));
        var state = StartupState.load(locations, VERSIONS, NOW, log::add, name -> null);
        assertTrue(state.settings().isEmpty());
        assertEquals(SCHEMA_ONE, Files.readString(locations.legacyMasterFile()));
        assertTrue(Files.exists(locations.legacyProfilesDirectory().resolve("guardian.yml")));
        assertFalse(Files.exists(locations.masterFile()));
    }

    private ConfigLocations locations() throws Exception {
        var locations = new ConfigLocations(root.resolve("MythicMobs"), root.resolve("ThePredict"));
        Files.createDirectories(locations.dataFolder());
        Files.createDirectories(locations.mythicDataFolder());
        Files.createDirectories(locations.legacyProfilesDirectory());
        return locations;
    }

}
