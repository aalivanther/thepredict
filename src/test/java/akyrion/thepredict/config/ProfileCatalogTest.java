package akyrion.thepredict.config;

import akyrion.thepredict.domain.TacticalProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCatalogTest {
    @TempDir
    Path directory;

    @Test
    void emptyCatalogIsSetupPendingAtRevisionZero() {
        var empty = ProfileCatalog.empty();

        assertEquals(0, empty.catalogRevision());
        assertEquals(CatalogStatus.SETUP_PENDING, empty.status());
        assertTrue(empty.summary(0).contains("configuration pending"));
    }

    @Test
    void statusFollowsProfilesAndErrors() throws Exception {
        var profile = guardian();
        var warning = ConfigProblem.warning("a.yml", "a.Objective", "may not be in English");
        var error = ConfigProblem.error("b.yml", "b.MythicID", "unknown mob 'B'");

        assertEquals(CatalogStatus.LOADED, new ProfileCatalog(1, Map.of("Guardian", profile), List.of(warning)).status());
        assertEquals(CatalogStatus.SETUP_PENDING, new ProfileCatalog(1, Map.of(), List.of(warning)).status());
        var invalid = new ProfileCatalog(1, Map.of("Guardian", profile), List.of(warning, error));
        assertEquals(CatalogStatus.INVALID, invalid.status());
        assertTrue(invalid.summary(1).endsWith("first error b.yml: b.MythicID: unknown mob 'B'"), invalid.summary(1));
    }

    @Test
    void nextCatalogHasTheFollowingRevision() {
        var next = ProfileCatalog.empty().next(Map.of(), List.of()).next(Map.of(), List.of());

        assertEquals(2, next.catalogRevision());
    }

    @Test
    void rejectsAKeyThatIsNotTheMobOfItsProfileAndANegativeRevision() throws Exception {
        var profile = guardian();

        assertThrows(IllegalArgumentException.class, () -> new ProfileCatalog(1, Map.of("Other", profile), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProfileCatalog(-1, Map.of(), List.of()));
    }

    private TacticalProfile guardian() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), """
                schemaVersion: 2
                guardian_ai:
                  MythicID: Guardian
                  Objective: Survive
                  Tactics:
                    hold:
                      Movement: HOLD
                """);
        return new ProfileLoader().load(directory).profiles().get("Guardian");
    }

    @Test
    void readButInactiveProfilesAreReportedInsteadOfTheSetupHint() {
        var summary = new ProfileCatalog(3, Map.of(), List.of()).summary(2);

        assertTrue(summary.contains("2 profiles read, 0 active"), summary);
        assertTrue(summary.contains("no mob targeted by a profile has Predictor: true"), summary);
        assertFalse(summary.contains("example.yml.example"), summary);
    }
}
