package akyrion.thepredict.admin;

import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupMessagesTest {
    private static SetupReport setup(SetupReport.CatalogState catalog, Optional<String> problem,
                                     Optional<String> migration) {
        return setup(catalog, 0, problem, migration);
    }

    private static SetupReport setup(SetupReport.CatalogState catalog, int profilesRead, Optional<String> problem,
                                     Optional<String> migration) {
        return new SetupReport(25, "26.2", "5.13.1", catalog, 1, 0, profilesRead, "0 profiles", problem, migration,
                Optional.empty());
    }

    @Test
    void aCleanInstallationOnlyInformsAndGivesTheNextStep() {
        var lines = SetupMessages.startup(setup(SetupReport.CatalogState.SETUP_PENDING, Optional.empty(),
                Optional.empty()), ServiceState.UNCONFIGURED);
        assertEquals(List.of("Jev not configured: set TYPESAFE_API_KEY or jev.apiKey. MythicMobs works normally. Next:"
                        + " set the key and restart the server, then run /thepredict status.",
                "No profiles: copy examples/ or create a .yml file in plugins/MythicMobs/AIConfig/Profiles/. Next: read"
                        + " plugins/MythicMobs/AIConfig/Profiles/README.txt."), lines);
    }

    @Test
    void anInvalidConfigurationAMigrationAndACheckInProgressHaveTheirOwnLines() {
        var lines = SetupMessages.startup(setup(SetupReport.CatalogState.SETUP_PENDING,
                Optional.of("config.yml: uses schema 1"), Optional.of("migration/20260929-120000/report.md")),
                ServiceState.INVALID_CONFIGURATION);
        assertEquals(2, lines.size(), lines.toString());
        assertTrue(lines.getFirst().startsWith("Invalid configuration: config.yml: uses schema 1."), lines.getFirst());
        assertTrue(lines.getFirst().endsWith("Next: fix plugins/MythicMobs/AIConfig/config.yml and restart the server,"
                + " then run /thepredict status."), lines.getFirst());
        assertTrue(lines.getFirst().contains("/thepredict status still works"), lines.getFirst());
        assertTrue(lines.get(1).contains("migration/20260929-120000/report.md") && lines.get(1).contains("No file was"
                + " changed and no query was enabled"), lines.get(1));
        var checking = SetupMessages.startup(setup(SetupReport.CatalogState.LOADED, Optional.empty(), Optional.empty()),
                ServiceState.CHECKING);
        assertEquals(List.of("Checking Jev without spending tokens; the result follows in the console."), checking);
    }

    @Test
    void everyCheckResultHasADistinctMessageWithItsNextStep() {
        var messages = new HashSet<String>();
        for (var state : ServiceState.values()) {
            var message = SetupMessages.checkResult(new ServiceCheck(state, "detail"));
            assertTrue(message.contains("Next") || message.contains("retries") || message.contains("enabled")
                    || message.contains("still running"), message);
            messages.add(message);
        }
        assertEquals(ServiceState.values().length, messages.size());
        assertTrue(SetupMessages.checkResult(new ServiceCheck(ServiceState.READY,
                "credential valid; model unverified until first inference")).contains("does not verify an inference"));
    }

    @Test
    void theJoinNoticeNamesEveryCauseAndIsSilentWhenAllIsReady() {
        assertTrue(SetupMessages.joinNotice(setup(SetupReport.CatalogState.LOADED, Optional.empty(), Optional.empty()),
                ServiceState.READY).isEmpty());
        var notice = SetupMessages.joinNotice(setup(SetupReport.CatalogState.INVALID, Optional.of("config.yml: x"),
                Optional.of("migration/a/report.md")), ServiceState.UNCONFIGURED).orElseThrow();
        assertEquals("[ThePredict] plugins/MythicMobs/AIConfig/config.yml has errors; some profiles have errors;"
                + " a migration is pending; Jev is UNCONFIGURED. Run /thepredict status for details.", notice);
        assertEquals(Optional.of(Component.text(notice)), SetupMessages.joinMessage(setup(
                SetupReport.CatalogState.INVALID, Optional.of("config.yml: x"), Optional.of("migration/a/report.md")),
                ServiceState.UNCONFIGURED), "the same text, sent as a component");
    }

    @Test
    void profilesReadWithoutAnyMobWithPredictorGetTheirOwnMessage() {
        var lines = SetupMessages.startup(setup(SetupReport.CatalogState.SETUP_PENDING, 1, Optional.empty(),
                Optional.empty()), ServiceState.READY);
        assertEquals(List.of("1 profiles read, but no mob targeted by a profile has Predictor: true. Next: set"
                + " Predictor: true on the MythicMobs mob and run /mm reload."), lines);
    }

    @Test
    void everyNextStepThatNeedsANewKeyModelOrConfigurationSaysToRestartTheServer() {
        for (var state : List.of(ServiceState.INVALID_CREDENTIALS, ServiceState.INVALID_MODEL,
                ServiceState.INVALID_CONFIGURATION, ServiceState.UNCONFIGURED)) {
            var message = SetupMessages.checkResult(new ServiceCheck(state, "detail"));
            assertTrue(message.endsWith(SetupMessages.RESTART), message);
            assertTrue(!message.contains("/thepredict check"), message);
        }
        assertTrue(SetupMessages.checkResult(new ServiceCheck(ServiceState.INVALID_CREDENTIALS, "authentication"))
                .contains("The key is read only at startup."));
    }
}
