package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.engine.AdminCommand;
import akyrion.thepredict.engine.distill.DistillControl;
import akyrion.thepredict.domain.distill.DistillMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M29.8 and CAR-03, CAR-04, CAR-06, JS-20 and SG-01: apply writes only under autoconfig, one overlay per profile with
 * its base, and refuses before writing what the reload would refuse. The author file stays.
 */
class AutoconfigAdminTest {
    private static final String BASE = "1".repeat(64);
    private static final String OTHER = "2".repeat(64);

    @TempDir
    Path root;

    @Test
    void applyNeedsNoAuthorBaseFileAndWritesTheProfileAndTheBaseBeforeTheKeys() throws Exception {
        var auto = autoconfig();
        var author = root.resolve("plugins/MythicMobs/AIConfig/Profiles/guardian.yml");
        Files.createDirectories(author.getParent());
        var authorBytes = "Decision.Pruning: off\n".getBytes();
        Files.write(author, authorBytes);
        var proposal = proposal("guardian_ai.txt", "base=" + BASE + "\nDecision.Pruning=safe\nZone.Radius=40\n");
        assertFalse(Files.exists(auto.resolve("author-base.txt")));
        assertEquals("applied guardian_ai.txt", AutoconfigAdmin.apply(proposal.toString()));
        assertEquals("profile=guardian_ai\nbase=" + BASE + "\nDecision.Pruning=safe\nZone.Radius=40\n",
                Files.readString(auto.resolve("active/guardian_ai.txt")));
        assertArrayEquals(authorBytes, Files.readAllBytes(author));
        assertEquals("reverted", AutoconfigAdmin.revert(proposal.toString()));
        assertFalse(Files.exists(auto.resolve("active/guardian_ai.txt")));
        assertArrayEquals(authorBytes, Files.readAllBytes(author));
    }

    @Test
    void aProfileLineWinsOverTheFileNameAndEveryPinStaysOut() throws Exception {
        var proposal = proposal("pinned.txt", "profile = guardian_ai\nbase=" + BASE + "\npin=Decision.Pruning\n"
                + "pin=Decision.Diversity\nDecision.Pruning=safe\nDecision.Diversity=wide\nDecision.Rotation=on\n");
        assertEquals("applied pinned.txt", AutoconfigAdmin.apply(proposal.toString()));
        assertEquals("profile=guardian_ai\nbase=" + BASE + "\nDecision.Rotation=on\n",
                Files.readString(autoconfig().resolve("active/pinned.txt")));
        assertFalse(Files.exists(autoconfig().resolve("active/pinned.txt.tmp")));
    }

    @Test
    void aProposalWithoutABaseOrWithALineWithoutAValueWritesNothing() throws Exception {
        refused("guardian_ai.txt", "Decision.Pruning=safe\n", "OVERLAY_INVALID: the proposal has no base= line");
        refused("guardian_ai.txt", "base=" + BASE + "\nDecision.Pruning\n", "OVERLAY_INVALID: line 2 has no key=value");
        assertFalse(Files.exists(autoconfig().resolve("active")));
    }

    @Test
    void withTheProfilesReadNowAStaleBaseAnUnknownProfileOrAKeyThatDoesNotApplyWritesNothing() throws Exception {
        var bases = ProfileBases.of(List.of(profile("guardian_ai")), Map.of("guardian_ai", BASE));
        var stale = refused("guardian_ai.txt", "base=" + OTHER + "\nZone.Radius=40\n", bases);
        assertTrue(stale.startsWith("OVERLAY_STALE: ") && stale.contains(BASE), stale);
        var unmatched = refused("archer_ai.txt", "base=" + BASE + "\nZone.Radius=40\n", bases);
        assertTrue(unmatched.startsWith("OVERLAY_UNMATCHED: ") && unmatched.contains("'archer_ai'"), unmatched);
        var invalid = refused("guardian_ai.txt", "base=" + BASE + "\nZone.Radius=100\n", bases);
        assertTrue(invalid.startsWith("OVERLAY_INVALID: ") && invalid.contains("Zone.Radius"), invalid);
        var number = refused("guardian_ai.txt", "base=" + BASE + "\nDecision.MaxOptions=many\n", bases);
        assertTrue(number.startsWith("OVERLAY_INVALID: "), number);
        assertFalse(Files.exists(autoconfig().resolve("active")));
        var proposal = proposal("guardian_ai.txt", "base=" + BASE + "\nZone.Radius=40\n");
        assertEquals("applied guardian_ai.txt", AutoconfigAdmin.apply(proposal.toString(), OverlayModes.DEFAULTS,
                bases));
    }

    @Test
    void theGlobalModesInUseBoundEveryModeKeyIncludingPatternsMode() throws Exception {
        for (var raise : List.of("Patterns.Mode=full", "Zone.Mode=guard", "Group.Mode=coordinate",
                "Distill.Mode=assist")) {
            assertEquals("OVERLAY_RAISES_GLOBAL", refused("guardian_ai.txt", "base=" + BASE + "\n" + raise + "\n"));
        }
        var raised = new OverlayModes(ZoneMode.GUARD, GroupMode.COORDINATE, DistillMode.ASSIST, PatternMode.FULL);
        var proposal = proposal("guardian_ai.txt", "base=" + BASE + "\nPatterns.Mode=full\nZone.Mode=guard\n"
                + "Group.Mode=coordinate\nDistill.Mode=assist\n");
        assertEquals("applied guardian_ai.txt", AutoconfigAdmin.apply(proposal.toString(), raised,
                ProfileBases.UNKNOWN));
    }

    @Test
    void aModeIsReadAsTheReloadReadsItSoCaseAndSpacesCannotSlipPastTheGate() throws Exception {
        for (var raise : List.of("Zone.Mode=OFF", "Group.Mode= Off", "Zone.Mode=GUARD", "Patterns.Mode=Full")) {
            assertEquals("OVERLAY_RAISES_GLOBAL", refused("guardian_ai.txt", "base=" + BASE + "\n" + raise + "\n"),
                    raise);
        }
        var loud = refused("guardian_ai.txt", "base=" + BASE + "\nZone.Mode=loud\n");
        assertTrue(loud.startsWith("OVERLAY_INVALID: "), loud);
        assertFalse(Files.exists(autoconfig().resolve("active")));
    }

    @Test
    void aProposalLargerThanTheReadCapOfTheReloadWritesNothing() throws Exception {
        var text = "base=" + BASE + "\n" + "#".repeat(AutoconfigAdmin.MAX_BYTES) + "\n";
        assertEquals("OVERLAY_UNREADABLE: larger than 65536 bytes; nothing was written",
                refused("guardian_ai.txt", text));
        var fits = "base=" + BASE + "\n#" + "x".repeat(AutoconfigAdmin.MAX_BYTES - BASE.length() - 8) + "\n";
        assertEquals(AutoconfigAdmin.MAX_BYTES, fits.length());
        assertEquals("applied guardian_ai.txt", AutoconfigAdmin.apply(proposal("guardian_ai.txt", fits).toString()));
    }

    @Test
    void theWriterAppliesModeAndDisableWithoutTraining() throws Exception {
        var control = new DistillControl(DistillMode.ASSIST, DistillMode.ASSIST);
        var records = Files.createTempDirectory("m29-distill");
        assertEquals("assist", AdminTasks.run(new AdminCommand.DistillModeSet("autonomous"), records, null, null,
                null, 1, control).toLowerCase(java.util.Locale.ROOT));
        assertEquals(DistillMode.ASSIST, control.effective(DistillMode.AUTONOMOUS));
        assertEquals("disable next capture", AdminTasks.run(new AdminCommand.DistillDisable("all"), records, null,
                null, null, 1, control));
        assertEquals(DistillMode.OFF, control.effective(DistillMode.ASSIST));
        assertThrows(IllegalArgumentException.class, () -> AdminTasks.run(new AdminCommand.DistillBuild(""), records,
                null, null, null, 1, control));
        assertEquals(AdminTasks.EVAL_REPORT_UNAVAILABLE, AdminTasks.run(new AdminCommand.EvalReport("", "", "md"),
                records, null, null, null, 1, control));
        assertEquals(0, Files.list(records).count());
    }

    /** A profile of the loader with only what {@code ProfileKeys.apply} needs. */
    static TacticalProfile profile(String id) {
        var hold = new TacticId("hold_ground");
        return new TacticalProfile(id, "Guardian", "Defeat the target",
                Map.of(hold, new Tactic(hold, Movement.HOLD, Optional.empty(), Optional.empty(), 50)), Map.of(),
                List.of(), new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                new DecisionTiming(40, 20, 100, 0.5, 0.2, 1));
    }

    private Path autoconfig() throws IOException {
        return Files.createDirectories(root.resolve("plugins/ThePredict/autoconfig"));
    }

    private Path proposal(String name, String text) throws IOException {
        var proposal = autoconfig().resolve(name);
        Files.writeString(proposal, text);
        return proposal;
    }

    private String refused(String name, String text) throws IOException {
        return refused(name, text, ProfileBases.UNKNOWN);
    }

    private String refused(String name, String text, ProfileBases bases) throws IOException {
        var proposal = proposal(name, text);
        return assertThrows(OverlayRefusal.class, () -> AutoconfigAdmin.apply(proposal.toString(),
                OverlayModes.DEFAULTS, bases)).getMessage();
    }

    private void refused(String name, String text, String prefix) throws IOException {
        var message = refused(name, text);
        assertTrue(message.startsWith(prefix), message);
    }
}
