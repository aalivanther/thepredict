package akyrion.thepredict;

import akyrion.thepredict.adapter.server.binding.MythicFixtures;
import akyrion.thepredict.adapter.server.binding.MythicProfileBinder;
import akyrion.thepredict.config.CatalogStatus;
import akyrion.thepredict.config.ConfigProblem;
import akyrion.thepredict.config.ProfileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCatalogsTest {
    private static final String GUARDIAN = """
            schemaVersion: 2
            guardian_ai:
              MythicID: Guardian
              Objective: Survive
              Choices:
                melee: { MythicID: GuardianMelee }
              Tactics:
                press_and_strike: { Movement: APPROACH, Choice: melee }
                hold: { Movement: HOLD }
              Contexts:
                wounded: { Conditions: ['healthpercent{p=<30%}'], Tactics: [hold] }
              Zone:
                Radius: 32
            """;

    @TempDir
    Path directory;

    private final List<String> lines = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();
    private final List<Runnable> later = new ArrayList<>();
    private final MythicFixtures.Definitions definitions = MythicFixtures.definitions()
            .add(MythicFixtures.mob("Guardian", "ZOMBIE", Map.of("Predictor", true)))
            .add(MythicFixtures.skill("GuardianMelee", Map.of("PredictValues", List.of("MELEE_ATTACK"),
                    "Cooldown", "2")));
    private int changes;

    @Test
    void anInvalidActiveOverlayKeepsTheAuthorProfileAndOnlyWarns() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        var active = Files.createDirectories(directory.resolve("data").resolve("autoconfig").resolve("active"));
        Files.writeString(active.resolve("guardian_ai.txt"), "Zone.Radius=100\n");
        var catalogs = catalogs();
        catalogs.useDataFolder(directory.resolve("data"));
        catalogs.start();
        assertEquals(CatalogStatus.LOADED, catalogs.catalog().status());
        assertEquals(32, catalogs.profile("Guardian").orElseThrow().zone().radius().orElseThrow());
        var warning = catalogs.catalog().problems().getLast().toString();
        assertTrue(warning.startsWith("autoconfig/active/guardian_ai.txt: guardian_ai: OVERLAY_INVALID: Zone.Radius"),
                warning);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("WARNING autoconfig/active/guardian_ai.txt")),
                lines.toString());
        catalogs.onMythicReload();
        assertEquals(CatalogStatus.LOADED, catalogs.catalog().status());
    }

    @Test
    void anAppliedOrRevertedOverlayReloadsAndTellsTheSessions() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        var active = Files.createDirectories(directory.resolve("data").resolve("autoconfig").resolve("active"));
        var catalogs = catalogs();
        catalogs.useDataFolder(directory.resolve("data"));
        catalogs.start();
        Files.writeString(active.resolve("guardian_ai.txt"), "Zone.Radius=40\n");
        catalogs.reloadAfterOverlay();
        assertEquals(1, changes);
        assertEquals(40, catalogs.profile("Guardian").orElseThrow().zone().radius().orElseThrow());
        Files.delete(active.resolve("guardian_ai.txt"));
        catalogs.reloadAfterOverlay();
        assertEquals(2, changes);
        assertEquals(32, catalogs.profile("Guardian").orElseThrow().zone().radius().orElseThrow());
        assertTrue(catalogs.catalog().overlay().isEmpty(), catalogs.catalog().overlay().toString());
    }

    @Test
    void everyReadReachesTheReadListenerWithTheFingerprintsOfTheAuthorProfiles() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        var reads = new ArrayList<akyrion.thepredict.config.ProfileLoadResult>();
        var catalogs = catalogs();
        catalogs.onRead(reads::add);
        catalogs.start();
        catalogs.onMythicReload();
        assertEquals(2, reads.size());
        for (var read : reads) {
            assertTrue(read.profiles().containsKey("Guardian"), read.profiles().toString());
            assertEquals(64, read.fingerprints().get("guardian_ai").length(), read.fingerprints().toString());
        }
    }

    @Test
    void aDeferredCheckVerifiesTheRevisionItWasScheduledFor() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        definitions.unresolved("healthpercent{p=<30%}");
        var catalogs = catalogs();
        catalogs.start();
        catalogs.onMythicReload();
        assertEquals(2, later.size());
        later.getFirst().run();
        assertEquals(0, changes, "the first check belongs to a catalog that was already replaced");
        assertEquals(2, catalogs.revision());
        later.getLast().run();
        assertEquals(1, changes);
        assertTrue(catalogs.profile("Guardian").orElseThrow().contexts().isEmpty());
    }

    @Test
    void onlyASuccessfulApplyOrRevertReloadsTheCatalog() {
        assertTrue(ProfileCatalogs.changesOverlay(true, "applied guardian_ai.txt"));
        assertTrue(ProfileCatalogs.changesOverlay(true, "reverted"));
        assertTrue(!ProfileCatalogs.changesOverlay(false, "reverted"));
        assertTrue(!ProfileCatalogs.changesOverlay(true, "imported guardian_ai.txt"));
        assertTrue(!ProfileCatalogs.changesOverlay(true, "rolled a.tpld"));
    }

    private ProfileCatalogs catalogs() {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(record.getLevel() + " " + record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return new ProfileCatalogs(new CatalogReloader(new ProfileLoader(), directory,
                new MythicProfileBinder(definitions)), logger, (task, ticks) -> {
                    later.add(task);
                    delays.add(ticks);
                }, () -> true, () -> changes++);
    }

    @Test
    void theFirstStartLoadsLogsAndChecksTheConditionsTwoTicksLater() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), """
                schemaVersion: 2
                guardian_ai:
                  MythicID: Guardian
                  Objective: Survive
                  Choices:
                    melee: { MythicID: GuardianMelee }
                  Tactics:
                    press_and_strike: { Movement: APPROACH, Choice: melee }
                    hold: { Movement: HOLD }
                """);
        var catalogs = catalogs();
        catalogs.start();
        assertEquals(CatalogStatus.LOADED, catalogs.catalog().status());
        assertTrue(catalogs.profile("Guardian").isPresent());
        assertTrue(catalogs.binding("Guardian").isPresent());
        assertEquals(1, catalogs.revision());
        assertTrue(lines.getFirst().startsWith("INFO Profile catalog "), lines.toString());
        assertEquals(List.of(ProfileCatalogs.CONDITION_CHECK_DELAY_TICKS), delays);
        later.getFirst().run();
        assertEquals(0, changes, "nothing to disable, so the sessions are not told");
    }

    @Test
    void aBlockedStartIgnoresMythicReloadsAndKeepsTheCatalogEmpty() {
        var catalogs = catalogs();
        catalogs.startBlocked(ConfigProblem.error("config.yml", "", "has invalid YAML syntax"));
        catalogs.onMythicReload();
        assertEquals(CatalogStatus.INVALID, catalogs.catalog().status());
        assertTrue(catalogs.catalog().profiles().isEmpty());
        assertTrue(later.isEmpty());
    }

    @Test
    void theLogOfProblemsIsBoundedToFiftyLines() throws Exception {
        for (var index = 0; index < 60; index++) {
            Files.writeString(directory.resolve("broken" + index + ".yml"), "schemaVersion: 2\nbroken_" + index
                    + ":\n  Unknown: true\n");
        }
        var catalogs = catalogs();
        catalogs.start();
        assertEquals(ProfileCatalogs.MAX_LOGGED_PROBLEMS + 2, lines.size(), lines.toString());
        assertTrue(lines.getLast().contains("more problems are not shown"), lines.getLast());
    }
}
