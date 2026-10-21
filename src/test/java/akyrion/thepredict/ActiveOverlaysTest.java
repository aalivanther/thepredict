package akyrion.thepredict;

import akyrion.thepredict.adapter.server.binding.MythicFixtures;
import akyrion.thepredict.adapter.server.binding.MythicProfileBinder;
import akyrion.thepredict.config.CatalogStatus;
import akyrion.thepredict.config.PluginConfigurationLoader;
import akyrion.thepredict.config.ProfileCatalog;
import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.TickLayerMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CAR-03: an active overlay changes only its own profile, and only while the author profile is the one it was built on. */
class ActiveOverlaysTest {
    private static final String PROFILES = """
            schemaVersion: 2
            guardian_ai:
              MythicID: Guardian
              Objective: Survive
              Choices:
                melee: { MythicID: GuardianMelee }
              Tactics:
                press_and_strike: { Movement: APPROACH, Choice: melee }
                hold: { Movement: HOLD }
            archer_ai:
              MythicID: Archer
              Objective: Keep distance
              Choices:
                shot: { MythicID: ArcherShot }
              Tactics:
                keep_distance_and_shoot: { Movement: KEEP_DISTANCE, Choice: shot }
                hold: { Movement: HOLD }
            """;

    @TempDir
    Path directory;

    @Test
    void anOverlayChangesOnlyTheProfileItNames() throws Exception {
        var reloader = loaded();
        var catalog = reloader.noteOverlays(new ActiveOverlays.Read(List.of(ActiveOverlays.parse("guardian_ai.txt",
                "Decision.Diversity=on\nZone.Radius=40\n")), List.of()));
        assertEquals("on", catalog.profiles().get("Guardian").decision().diversity());
        assertEquals(40, catalog.profiles().get("Guardian").zone().radius().orElseThrow());
        assertEquals("inherit", catalog.profiles().get("Archer").decision().diversity());
        assertTrue(catalog.profiles().get("Archer").zone().radius().isEmpty());
        assertEquals(Map.of("guardian_ai:Decision.Diversity", "on", "guardian_ai:Zone.Radius", "40"), catalog.overlay());
        assertTrue(overlayProblems(catalog).isEmpty(), catalog.problems().toString());
        assertEquals(catalog.profiles().get("Guardian"), reloader.bindings().get("Guardian").profile());
    }

    @Test
    void aProfileLineNamesTheProfileWhateverTheFileIsCalled() throws Exception {
        var catalog = loaded().noteOverlays(new ActiveOverlays.Read(List.of(ActiveOverlays.parse("proposal.txt",
                "# applied by hand\nprofile=archer_ai\npin=Decision.Pruning\nDecision.Diversity=on\n")), List.of()));
        assertEquals("on", catalog.profiles().get("Archer").decision().diversity());
        assertEquals("inherit", catalog.profiles().get("Guardian").decision().diversity());
        assertEquals(Map.of("archer_ai:Decision.Diversity", "on"), catalog.overlay());
    }

    @Test
    void aBaseThatNoLongerMatchesTheAuthorProfileIsNotApplied() throws Exception {
        var reloader = loaded();
        var live = new ProfileLoader().load(directory).fingerprints().get("guardian_ai");
        assertEquals(64, live.length());
        var stale = reloader.noteOverlays(new ActiveOverlays.Read(List.of(ActiveOverlays.parse("guardian_ai.txt",
                "base=" + "0".repeat(64) + "\nDecision.Diversity=on\n")), List.of()));
        assertEquals("inherit", stale.profiles().get("Guardian").decision().diversity());
        assertEquals(CatalogStatus.LOADED, stale.status());
        assertEquals("autoconfig/active/guardian_ai.txt: guardian_ai: OVERLAY_STALE: the author profile changed after"
                + " the overlay was built; the author profile is used", stale.problems().getLast().toString());
        var fresh = loaded().noteOverlays(new ActiveOverlays.Read(List.of(ActiveOverlays.parse("guardian_ai.txt",
                "base=" + live + "\nDecision.Diversity=on\n")), List.of()));
        assertEquals("on", fresh.profiles().get("Guardian").decision().diversity());
    }

    @Test
    void aFileWithoutAProfileAndASecondFileForTheSameProfileOnlyWarn() throws Exception {
        var catalog = loaded().noteOverlays(new ActiveOverlays.Read(List.of(
                ActiveOverlays.parse("guardian_ai.txt", "Decision.Diversity=on\n"),
                ActiveOverlays.parse("proposal.txt", "Decision.Diversity=off\nprofile=guardian_ai\n"),
                ActiveOverlays.parse("ghost.txt", "Decision.Diversity=on\n")), List.of()));
        assertEquals("on", catalog.profiles().get("Guardian").decision().diversity());
        assertEquals("inherit", catalog.profiles().get("Archer").decision().diversity());
        assertEquals(List.of("autoconfig/active/proposal.txt: guardian_ai: OVERLAY_DUPLICATE: guardian_ai.txt already"
                        + " holds the overlay of this profile; not applied",
                "autoconfig/active/ghost.txt: ghost: OVERLAY_UNMATCHED: no profile read from AIConfig/Profiles is"
                        + " named 'ghost'; not applied"), overlayProblems(catalog));
    }

    @Test
    void anOverlayTickLayerAboveTheGlobalModeIsLoweredLikeTheProfileOne() throws Exception {
        var lowered = loaded().noteOverlays(new ActiveOverlays.Read(List.of(ActiveOverlays.parse("guardian_ai.txt",
                "Timing.TickLayer=full\n")), List.of()));
        assertEquals(TickLayerMode.REACTIONS, lowered.profiles().get("Guardian").timing().tickLayer(),
                "DOC-04: the delivered global is reactions, and an overlay must not run more of the tick layer");
        assertEquals(List.of("autoconfig/active/guardian_ai.txt: guardian_ai: Timing.TickLayer is above the global"
                + " reactions; using reactions"), overlayProblems(lowered));
        var yaml = new YamlConfiguration();
        yaml.loadFromString("schemaVersion: 2\ndecision:\n  tickLayer:\n    mode: full\n");
        var full = loaded(new ProfileLoader(new PluginConfigurationLoader().parse(yaml))).noteOverlays(
                new ActiveOverlays.Read(List.of(ActiveOverlays.parse("guardian_ai.txt", "Timing.TickLayer=full\n")),
                        List.of()));
        assertEquals(TickLayerMode.FULL, full.profiles().get("Guardian").timing().tickLayer());
        assertTrue(overlayProblems(full).isEmpty(), full.problems().toString());
    }

    private static List<String> overlayProblems(ProfileCatalog catalog) {
        return catalog.problems().stream().filter(problem -> problem.file().startsWith(ActiveOverlays.LABEL))
                .map(Object::toString).toList();
    }

    @Test
    void theReadSkipsHiddenAndTemporaryFilesAndCapsTheFiles() throws Exception {
        var active = Files.createDirectories(directory.resolve("active"));
        Files.writeString(active.resolve(".DS_Store"), "junk");
        Files.writeString(active.resolve("guardian_ai.txt.tmp"), "Zone.Radius=9\n");
        Files.writeString(active.resolve("guardian_ai.txt"), "base= \nZone.Radius=40\nnot a key\n");
        var read = ActiveOverlays.read(active);
        assertEquals(List.of(new ActiveOverlays.Overlay("guardian_ai.txt", "guardian_ai", Optional.empty(),
                Map.of("Zone.Radius", "40"))), read.overlays());
        assertTrue(ActiveOverlays.read(directory.resolve("missing")).isEmpty());
        for (var index = 0; index <= ActiveOverlays.MAX_FILES; index++) {
            Files.writeString(active.resolve(String.format("p%04d.txt", index)), "Zone.Radius=40\n");
        }
        var capped = ActiveOverlays.read(active);
        assertEquals(ActiveOverlays.MAX_FILES, capped.overlays().size());
        assertEquals("autoconfig/active: 2 more files are not read; at most " + ActiveOverlays.MAX_FILES
                + " overlays are active", capped.problems().getFirst().toString());
    }

    private CatalogReloader loaded() throws Exception {
        return loaded(new ProfileLoader());
    }

    private CatalogReloader loaded(ProfileLoader loader) throws Exception {
        if (!Files.exists(directory.resolve("profiles.yml"))) {
            Files.writeString(directory.resolve("profiles.yml"), PROFILES);
        }
        var definitions = MythicFixtures.definitions()
                .add(MythicFixtures.mob("Guardian", "ZOMBIE", Map.of("Predictor", true)))
                .add(MythicFixtures.mob("Archer", "SKELETON", Map.of("Predictor", true)))
                .add(MythicFixtures.skill("GuardianMelee", Map.of("PredictValues", List.of("MELEE_ATTACK"))))
                .add(MythicFixtures.skill("ArcherShot", Map.of("PredictValues", List.of("RANGED_ATTACK"))));
        var reloader = new CatalogReloader(loader, directory, new MythicProfileBinder(definitions));
        assertEquals(CatalogStatus.LOADED, reloader.reload().status());
        return reloader;
    }
}
