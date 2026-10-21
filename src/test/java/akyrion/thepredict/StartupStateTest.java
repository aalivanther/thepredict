package akyrion.thepredict;

import akyrion.thepredict.admin.SetupReport;
import akyrion.thepredict.config.ConfigLocations;
import akyrion.thepredict.config.ProfileCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** First start, configuration problems and the migration experience of M12.4 and M12.6, on temporary folders. */
class StartupStateTest {
    private static final RuntimeCompatibility.DetectedVersions VERSIONS =
            new RuntimeCompatibility.DetectedVersions(25, "26.2", "5.13.1-SNAPSHOT");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 12, 0, 0);
    private static final UnaryOperator<String> NO_ENVIRONMENT = name -> null;
    private static final String LEGACY_KEY = "sk-legacy-never-copy-0123456789";
    private static final String SCHEMA_ONE = """
            provider:
              model: 'vendor/model'
              timeoutMillis: 3000
              apiKey: '%s'
            decision:
              maxConcurrentRequests: 8
            """.formatted(LEGACY_KEY);

    @TempDir
    Path folder;

    private final List<String> log = new ArrayList<>();

    @Test
    void aCleanInstallationCreatesItsFilesAndStartsWithValidSettingsAndNothingPending() throws IOException {
        var state = StartupState.load(locations(), VERSIONS, NOW, log::add, NO_ENVIRONMENT);
        assertTrue(state.settings().isPresent());
        assertTrue(state.configurationProblem().isEmpty());
        assertTrue(state.migrationReport().isEmpty());
        assertTrue(state.locationNotice().isEmpty());
        assertEquals(1, log.size(), log.toString());
        assertTrue(log.getFirst().startsWith("Created setup files: AIConfig/config.yml, AIConfig/Profiles/README.txt"),
                log.getFirst());
        var report = state.report(ProfileCatalog.empty(), 0, "0 profiles");
        assertEquals(new SetupReport(25, "26.2", "5.13.1-SNAPSHOT", SetupReport.CatalogState.SETUP_PENDING, 0, 0, 0,
                "0 profiles", Optional.empty(), Optional.empty(), Optional.empty()), report);
        StartupState.load(locations(), VERSIONS, NOW, log::add, NO_ENVIRONMENT);
        assertEquals(1, log.size(), "a second start creates nothing and the shipped file has no obsolete key");
    }

    @Test
    void anOldConfigurationWithTheObsoleteReadyCapStartsAndLogsItOnce() throws IOException {
        StartupState.load(locations(), VERSIONS, NOW, log::add, NO_ENVIRONMENT);
        var master = locations().masterFile();
        Files.writeString(master, Files.readString(master).replace("  readyHorizonTicks: 0\n",
                "  readyHorizonTicks: 0\n  maxReadyChecksPerTick: 64\n"));
        log.clear();
        var state = StartupState.load(locations(), VERSIONS, NOW, log::add, NO_ENVIRONMENT);
        assertTrue(state.settings().isPresent());
        assertEquals(List.of("AIConfig/config.yml decision.maxReadyChecksPerTick is obsolete; the tick layer uses"
                + " decision.tickLayer.maxSessionsPerTick"), log);
    }

    @Test
    void schemaOneWritesAProposalAndAReportChangesNothingAndNeverCopiesTheKey() throws IOException {
        var master = locations().masterFile();
        Files.createDirectories(master.getParent());
        Files.writeString(master, SCHEMA_ONE);
        var state = StartupState.load(locations(), VERSIONS, NOW, log::add, NO_ENVIRONMENT);
        assertTrue(state.settings().isEmpty());
        assertTrue(state.configurationProblem().orElseThrow().toString().contains("uses schema 1"));
        assertEquals(Optional.of("migration/20260929-120000/report.md"), state.migrationReport());
        assertEquals(SCHEMA_ONE, Files.readString(master));
        assertTrue(Files.exists(data().resolve("migration/20260929-120000/AIConfig/config.yml.proposed")));
        assertTrue(Files.readString(data().resolve("migration/20260929-120000/report.md")).contains("TYPESAFE_API_KEY"));
        assertFalse(proposed().stream().anyMatch(text -> text.contains(LEGACY_KEY)));
        assertFalse(log.stream().anyMatch(line -> line.contains(LEGACY_KEY)));
        assertTrue(state.report(ProfileCatalog.empty(), 0, "blocked").migrationReport().isPresent());
    }

    @Test
    void aRepeatedStartReusesTheIdenticalProposalWhileSchemaOneRemains() throws IOException {
        var master = locations().masterFile();
        Files.createDirectories(master.getParent());
        Files.writeString(master, SCHEMA_ONE);
        var first = StartupState.load(locations(), VERSIONS, NOW, log::add, NO_ENVIRONMENT);
        var second = StartupState.load(locations(), VERSIONS, NOW.plusHours(1), log::add, NO_ENVIRONMENT);
        assertEquals(first.migrationReport(), second.migrationReport());
        try (Stream<Path> directories = Files.list(data().resolve("migration"))) {
            assertEquals(1, directories.count());
        }
    }

    @Test
    void brokenYamlAndAnInvalidValueAreProblemsWithFileAndPathNeverDefaults() throws IOException {
        var master = locations().masterFile();
        Files.createDirectories(master.getParent());
        Files.writeString(master, "schemaVersion: 2\njev: [broken\n");
        var broken = StartupState.load(locations(), VERSIONS, NOW, log::add, NO_ENVIRONMENT);
        assertEquals("AIConfig/config.yml: has invalid YAML syntax", broken.configurationProblem().orElseThrow().toString());
        assertTrue(broken.settings().isEmpty());
        assertEquals(20, broken.settingsInUse().profileDefaults().minimumDecisionIntervalTicks());
        Files.writeString(master, "schemaVersion: 2\njev:\n  timeoutMillis: 1\n");
        var invalid = StartupState.load(locations(), VERSIONS, NOW, log::add, NO_ENVIRONMENT);
        assertTrue(invalid.configurationProblem().orElseThrow().toString().startsWith(
                "AIConfig/config.yml: jev.timeoutMillis"), invalid.configurationProblem().orElseThrow().toString());
        assertTrue(invalid.migrationReport().isEmpty());
    }

    @Test
    void aValueADomainRecordRefusesBlocksActivationWithFileAndPathInsteadOfDisablingThePlugin() throws IOException {
        var master = locations().masterFile();
        Files.createDirectories(master.getParent());
        for (var entry : List.of(
                new String[] {"difficulty:\n  toleranceRatio: 0.9\n", "difficulty.toleranceRatio"},
                new String[] {"difficulty:\n  presets:\n    normal:\n      targetMinutes: 0\n",
                        "difficulty.presets.normal.targetMinutes"},
                new String[] {"stance:\n  nativeMode: wirte\n", "stance.nativeMode"},
                new String[] {"decision:\n  minimumCalibratedConfidence: 0.3\n", "decision.minimumCalibratedConfidence"},
                new String[] {"jev:\n  model: 'sk-live-PASTED_0123'\n  apiKey: 'sk-live-PASTED_0123'\n", "jev.model"})) {
            Files.writeString(master, "schemaVersion: 2\n" + entry[0]);
            var state = StartupState.load(locations(), VERSIONS, NOW, log::add, NO_ENVIRONMENT);
            var problem = state.configurationProblem().orElseThrow().toString();
            assertTrue(problem.startsWith("AIConfig/config.yml: " + entry[1] + ": "), problem);
            assertFalse(problem.contains("PASTED"), problem);
            assertTrue(state.settings().isEmpty());
        }
        Files.writeString(master, "schemaVersion: 2\njev:\n  model: 'sk-live-PASTED_0123'\n");
        var pasted = StartupState.load(locations(), VERSIONS, NOW, log::add,
                name -> "TYPESAFE_API_KEY".equals(name) ? "sk-live-PASTED_0123" : null);
        assertTrue(pasted.configurationProblem().orElseThrow().toString().startsWith("AIConfig/config.yml: jev.model: "));
        assertFalse(pasted.configurationProblem().orElseThrow().toString().contains("PASTED"));
    }

    private ConfigLocations locations() {
        return new ConfigLocations(folder.resolve("MythicMobs"), data());
    }

    private Path data() {
        return folder.resolve("ThePredict");
    }

    private List<String> proposed() throws IOException {
        var texts = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(data().resolve("migration"))) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                texts.add(Files.readString(file));
            }
        }
        return texts;
    }
}
