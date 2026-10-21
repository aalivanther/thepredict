package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Apply fills unpinned keys. The author map stays. A stale base is refused. Revert is the author map. */
class OverlayApplyRevertTest {
    @Test
    void applyDoesNotWriteTheAuthorAndRevertRestoresIt() {
        var author = new LinkedHashMap<String, String>();
        author.put("Decision.Pruning", "off");
        var overlay = new ProfileOverlay("proposal", Map.of("Decision.Pruning", "safe"), "base");
        var applied = overlay.apply(author, List.of(), "base");
        assertEquals("safe", applied.get("Decision.Pruning"));
        assertEquals("off", author.get("Decision.Pruning"));
        var reverted = Map.copyOf(author);
        assertEquals("off", reverted.get("Decision.Pruning"));
        assertThrows(IllegalArgumentException.class, () -> overlay.apply(author, List.of(), "other"));
    }
}
