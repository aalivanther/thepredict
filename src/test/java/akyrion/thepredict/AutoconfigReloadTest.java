package akyrion.thepredict;

import akyrion.thepredict.adapter.server.binding.MythicFixtures;
import akyrion.thepredict.adapter.server.binding.MythicProfileBinder;
import akyrion.thepredict.adapter.storage.AutoconfigAdmin;
import akyrion.thepredict.config.CatalogStatus;
import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.ZoneMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * M29.8 through the path the server takes: {@code autoconfig apply} ({@code AutoconfigAdmin.apply} with the shipped
 * global modes) and the reload of {@link ProfileCatalogs} after it. The proposal carries the live
 * {@code baseProfileSha256}, which the reload compares. An author key the proposal does not write stays, an unpinned
 * key changes, a pin stays, a global raise is refused, the sessions are told and the author file is not written.
 */
class AutoconfigReloadTest {
    private static final String GUARDIAN = """
            schemaVersion: 2
            guardian_ai:
              MythicID: Guardian
              Objective: Survive
              Choices:
                melee: { MythicID: GuardianMelee, CD: 20 }
              Tactics:
                press_and_strike: { Movement: APPROACH, Choice: melee }
                hold: { Movement: HOLD }
              Zone:
                Mode: observe
                Radius: 32
              Decision:
                Diversity: "off"
            """;

    @TempDir
    Path directory;

    private int changes;

    @Test
    void applyAndTheReloadAfterItGiveTheAuthorProfilePlusTheUnpinnedKeys() throws Exception {
        var catalogs = started();
        var author = directory.resolve("profiles").resolve("guardian.yml");
        var authorBytes = Files.readAllBytes(author);
        var before = catalogs.revision();
        Files.writeString(proposal(), "base=" + base() + "\npin=Decision.Pruning\nDecision.Pruning=safe\n"
                + "Decision.Diversity=on\n");
        assertEquals("applied guardian_ai.txt", AutoconfigAdmin.apply(proposal().toString()));
        catalogs.reloadAfterOverlay();
        assertEquals(1, changes);
        assertEquals(before + 2, catalogs.revision(), "one exchange for the read and one for the overlay");
        assertArrayEquals(authorBytes, Files.readAllBytes(author));
        assertFalse(Files.readString(proposal().resolveSibling("active").resolve("guardian_ai.txt"))
                .contains("Decision.Pruning"));
        var effective = catalogs.profile("Guardian").orElseThrow();
        assertEquals(32, effective.zone().radius().orElseThrow());
        assertEquals(ZoneMode.OBSERVE, effective.zone().mode().orElseThrow());
        assertEquals("on", effective.decision().diversity());
        assertEquals("inherit", effective.decision().pruning());
        assertEquals(CatalogStatus.LOADED, catalogs.catalog().status());
    }

    @Test
    void aProposalThatRaisesAGlobalModeIsRefusedAndNothingReloads() throws Exception {
        var catalogs = started();
        var before = catalogs.revision();
        for (var raise : List.of("Zone.Mode=guard", "Group.Mode=coordinate", "Zone.Mode=off")) {
            Files.writeString(proposal(), "base=" + base() + "\n" + raise + "\n");
            var refused = assertThrows(IOException.class, () -> AutoconfigAdmin.apply(proposal().toString()));
            assertEquals("OVERLAY_RAISES_GLOBAL", refused.getMessage());
        }
        assertEquals(before, catalogs.revision());
        assertEquals(0, changes);
    }

    private ProfileCatalogs started() throws Exception {
        var profiles = Files.createDirectories(directory.resolve("profiles"));
        Files.writeString(profiles.resolve("guardian.yml"), GUARDIAN);
        var data = directory.resolve("plugins").resolve("ThePredict");
        Files.createDirectories(data.resolve("autoconfig"));
        var catalogs = catalogs(profiles);
        catalogs.useDataFolder(data);
        catalogs.start();
        return catalogs;
    }

    private Path proposal() {
        return directory.resolve("plugins").resolve("ThePredict").resolve("autoconfig").resolve("guardian_ai.txt");
    }

    /** The {@code baseProfileSha256} of the author profile, as the reload compares it with {@code base=}. */
    private String base() throws IOException {
        return new ProfileLoader().load(directory.resolve("profiles")).fingerprints().get("guardian_ai");
    }

    private ProfileCatalogs catalogs(Path profiles) {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var definitions = MythicFixtures.definitions()
                .add(MythicFixtures.mob("Guardian", "ZOMBIE", Map.of("Predictor", true)))
                .add(MythicFixtures.skill("GuardianMelee", Map.of("PredictValues", List.of("MELEE_ATTACK"))));
        return new ProfileCatalogs(new CatalogReloader(new ProfileLoader(), profiles,
                new MythicProfileBinder(definitions)), logger, (task, ticks) -> { }, () -> true, () -> changes++);
    }
}
