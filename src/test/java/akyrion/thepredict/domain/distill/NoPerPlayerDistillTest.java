package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Artifact and manifest bytes have no per-player field and no clock. */
class NoPerPlayerDistillTest {
    @Test
    void twoWritesMatchAndNameNoPlayer() {
        var cells = new DistillArtifactFormat.Reserve[] {new DistillArtifactFormat.Reserve("fine", 8, 0.0, 0.0)};
        var first = DistillArtifactFormat.write("finger", cells);
        var second = DistillArtifactFormat.write("finger", cells);
        assertArrayEquals(first, second);
        var text = new String(first, StandardCharsets.UTF_8);
        assertFalse(text.contains("player"));
        assertFalse(text.contains("uuid"));
        var manifest = RegimeManifestFormat.write(new RegimeManifest(List.of(
                new RegimeManifest.Rule(1, 1, "r1:ALL", "abc"))));
        var manifestText = new String(manifest, StandardCharsets.UTF_8);
        assertFalse(manifestText.contains("player"));
        var overlay = new ProfileOverlay("id", java.util.Map.of("Decision.Pruning", "safe"), "base");
        assertFalse(overlay.sha256().isBlank());
        assertFalse(String.join(",", overlay.keys().keySet()).contains("player"));
    }
}
