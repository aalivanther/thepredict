package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OverlayAuthorPinnedTest {
    @Test
    void aPinnedKeyKeepsTheAuthorValueAndAStaleBaseIsRefused() {
        var overlay = new ProfileOverlay("o1", Map.of("Threat.Weight", "2", "Distill.Mode", "shadow"), "base");
        assertEquals("base", overlay.baseFingerprint());
        var merged = overlay.apply(Map.of("Threat.Weight", "9", "Distill.Mode", "off"), List.of("Threat.Weight"),
                "base");
        assertEquals("9", merged.get("Threat.Weight"));
        assertEquals("shadow", merged.get("Distill.Mode"));
        assertThrows(IllegalArgumentException.class,
                () -> overlay.apply(Map.of("Threat.Weight", "9"), List.of(), "other"));
    }
}
