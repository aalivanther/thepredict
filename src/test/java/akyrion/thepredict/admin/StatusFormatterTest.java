package akyrion.thepredict.admin;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.engine.OutcomeMetrics;
import akyrion.thepredict.engine.PatternModeGate;
import akyrion.thepredict.engine.StatusReport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusFormatterTest {
    private final StatusFormatter formatter = new StatusFormatter();

    @Test
    void theNineSectionsAppearInOrderWithOneLinePerDatumAndStayBounded() {
        var lines = AdminFixtures.plain(formatter.format(AdminFixtures.setup(), AdminFixtures.status()));
        var headers = lines.stream().filter(line -> !line.contains(": ")).map(line -> line.substring(13)).toList();
        assertEquals(List.of("Environment", "Service", "Catalog", "Sessions", "Encounters", "Budget", "Consumption",
                "Latency (p50, p95, p99 of the last two minutes)", "Errors", "Counters", "Effectiveness"), headers);
        assertTrue(lines.size() <= StatusFormatter.MAX_LINES, lines.toString());
        assertTrue(lines.stream().allMatch(line -> line.startsWith("[ThePredict] ")));
    }

    @Test
    void theWholeStatusIsBoundedAfterTheLinesAddedBehindTheFormatter() {
        var lines = new ArrayList<>(formatter.format(AdminFixtures.setup(), AdminFixtures.status()));
        while (lines.size() < StatusFormatter.MAX_LINES + 4) {
            lines.add(AdminText.line("Memory", "line " + lines.size()));
        }
        var bounded = AdminFixtures.plain(StatusFormatter.bounded(lines));
        assertEquals(StatusFormatter.MAX_LINES, bounded.size());
        assertEquals("[ThePredict] 5 more status lines are not shown", bounded.getLast());
        var short3 = List.of(AdminText.line("A", "1"), AdminText.line("B", "2"), AdminText.line("C", "3"));
        assertEquals(3, StatusFormatter.bounded(short3).size());
    }

    @Test
    void unverifiedPatternModeIsPrintedOnlyWhenTheReportCarriesIt() {
        var absent = AdminFixtures.plain(formatter.format(AdminFixtures.setup(), AdminFixtures.status()));
        assertTrue(absent.stream().noneMatch(line -> line.contains("UNVERIFIED_PATTERN_MODE")));
        var gate = new PatternModeGate();
        var report = AdminFixtures.status().withPatterns(new StatusReport.Patterns(
                gate.effective().name().toLowerCase(Locale.ROOT), gate.cause()));
        var lines = AdminFixtures.plain(formatter.format(AdminFixtures.setup(), report));
        assertTrue(lines.stream().anyMatch(line -> line.contains("mode=observe")
                && line.contains("cause=" + PatternModeGate.UNVERIFIED)), lines.toString());
        assertTrue(lines.size() <= StatusFormatter.MAX_LINES);
    }

    @Test
    void everySectionShowsItsData() {
        var text = String.join("\n", AdminFixtures.plain(formatter.format(AdminFixtures.setup(), AdminFixtures.status())));
        for (var expected : List.of("Java 25 (minimum 25), Minecraft 26.2 (minimum 26.2), MythicMobs 5.13.1-SNAPSHOT",
                "Jev PAUSED (timeout, 1200 ms)", "configured jev-1.13.0; last answer jev-1.13.0", "Pause: ends in 2 s",
                "Profiles: LOADED (revision 3, 1 profiles read, 1 active, 0 errors, 0 warnings)", "Migration: none pending",
                "4 active, 1 suspended; levels JEV 2, ALTERNATIVE 0, LOCAL 1, BASE 1; debug intents 1",
                "opened 7, ended died=2 unbound=1", "2 active, 1 with a request in flight, 1 with pending context",
                "Server: 30/600 requests in 60 s, 1/8 in flight", "Your world: 12/300 requests in 60 s, 1/4 in flight",
                "12000/600000 in 60 s", "capture: 100 us, 400 us, >2 s; 12 samples, max 2500 us", "roundtrip:",
                "inbox_wait:", "apply:", "Recent failures: TIMEOUT 12 s ago", "Recent fallbacks: SERVICE_PAUSED 1 s ago",
                "failures: timeout=2", "discards: none", "predictordecide pending 0, dropped 4",
                "events recorded 10, repeated 2, pending marks 7, member paths 3",
                "assists 1, blocked hits 2, harass 3, unreachable decays 4, limited 5, failures 0; points +12.50 / -4.00",
                "outcomes: level=JEV windows=3 dealt=0.12 taken=0.03 reach=0.67 hit_rate=0.50 effect=0.21",
                "contingencies: asked=4 fired=2 effective=1")) {
            assertTrue(text.contains(expected), expected + "\n" + text);
        }
    }

    @Test
    void theCostPerHourIsTokensPerMinuteTimesSixtyTimesThePricePerMillion() {
        assertEquals(12_000 * 60 * 0.042 / 1_000_000, StatusFormatter.estimatedUsdPerHour(12_000), 1e-12);
        assertEquals(0.0302, StatusFormatter.estimatedUsdPerHour(12_000), 1e-4);
        var text = String.join("\n", AdminFixtures.plain(formatter.format(AdminFixtures.setup(), AdminFixtures.status())));
        assertTrue(text.contains("estimated cost 0.0302 USD per hour (estimate at 0.042 USD per million input tokens)"),
                text);
    }

    @Test
    void theOutputNeverShowsAKeyOrAPlayerAndMarksAPendingMigration() {
        var setup = new SetupReport(25, "26.2", "5.13.1", SetupReport.CatalogState.INVALID, 4, 0, 2, "0 of 2 active",
                Optional.of("AIConfig/config.yml: jev.timeoutMillis: must be between 100 and 5000"),
                Optional.of("migration/20260929-120000/report.md"), Optional.empty());
        var text = String.join("\n", AdminFixtures.plain(formatter.format(setup, AdminFixtures.status())));
        assertTrue(text.contains("Migration pending: review migration/20260929-120000/report.md"), text);
        assertTrue(text.contains("Configuration: AIConfig/config.yml: jev.timeoutMillis"), text);
        assertFalse(text.contains("config.yml: config.yml"), text);
        assertFalse(text.toLowerCase().contains("apikey") || text.contains("Bearer") || text.contains("sk-"), text);
        assertFalse(text.contains(AdminFixtures.MOB.toString()), text);
    }

    @Test
    void withoutTheCoordinatorOnlyTheInstallationIsShown() {
        var lines = AdminFixtures.plain(formatter.format(AdminFixtures.setup()));
        assertTrue(lines.stream().anyMatch(line -> line.contains("the coordinator did not start")), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.contains("Versions: Java 25")), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.contains("Profiles: LOADED")), lines.toString());
    }

    @Test
    void aLocationNoticeAppearsInStatusAndDoesNotStartAMoveProposal() {
        var notice = "Old files in plugins/ThePredict/config.yml and plugins/ThePredict/AI/ are ignored because"
                + " plugins/MythicMobs/AIConfig/ already has configuration. Nothing was moved or deleted.";
        var setup = new SetupReport(25, "26.2", "5.13.1", SetupReport.CatalogState.LOADED, 1, 1, 1, "1 of 1 active",
                Optional.empty(), Optional.empty(), Optional.of(notice));
        var text = String.join("\n", AdminFixtures.plain(formatter.format(setup, AdminFixtures.status())));
        assertTrue(text.contains("Location: " + notice), text);
        assertTrue(text.contains("Migration: none pending"), text);
    }

    @Test
    void theEffectivenessOfEveryLevelWithWindowsStillFitsTheBound() {
        var base = AdminFixtures.status();
        var levels = new ArrayList<OutcomeMetrics.LevelOutcomes>();
        for (var level : List.of(ControlLevel.JEV,
                ControlLevel.ALTERNATIVE, ControlLevel.LOCAL)) {
            levels.add(new OutcomeMetrics.LevelOutcomes(level, 5, 0.1, 0.05, 0.4, 0.5, 0.3,
                    10, 5));
        }
        var full = new StatusReport(base.service(), base.sessions(), base.encounters(),
                base.server(), base.world(), base.consumption(), base.latency(), base.recentFailures(),
                base.recentFallbacks(), base.counters(), base.context(), base.threat(),
                new StatusReport.Effectiveness(levels, 9, 4, 2));
        var lines = AdminFixtures.plain(formatter.format(AdminFixtures.setup(), full));
        assertTrue(lines.size() <= StatusFormatter.MAX_LINES, lines.size() + " lines");
        assertTrue(String.join("\n", lines).contains("prepare:"), "the span of the preparation of M16.1");
        assertEquals(3, lines.stream().filter(line -> line.contains("outcomes: level=")).count());
    }
}
