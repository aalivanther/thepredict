package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OverlayFingerprintTest {
    @Test
    void theSameKeysBuiltForTwoProfilesHaveTwoFingerprints() {
        var keys = Map.of("Decision.Pruning", "safe", "Threat.Weight", "2");
        var first = new ProfileOverlay("x", keys, "base-of-p1");
        var second = new ProfileOverlay("y", keys, "base-of-p2");
        assertNotEquals(first.sha256(), second.sha256());
        assertEquals(first.sha256(), new ProfileOverlay("renamed", keys, "base-of-p1").sha256());
    }

    @Test
    void aKeyAndItsValueCannotBeReadAsAnotherPair() {
        var left = new ProfileOverlay("o", Map.of("a", "b=c"), "base");
        var right = new ProfileOverlay("o", Map.of("a=b", "c"), "base");
        assertNotEquals(left.sha256(), right.sha256());
        var joined = new ProfileOverlay("o", Map.of("a", "1\nb=2"), "base");
        var split = new ProfileOverlay("o", Map.of("a", "1", "b", "2"), "base");
        assertNotEquals(joined.sha256(), split.sha256());
    }

    @Test
    void theCanonicalFormSortsTheKeysByName() {
        var overlay = new ProfileOverlay("o", Map.of("b", "2", "a.b", "3", "a", "1"), "fp");
        assertEquals("overlay v1\nbase 2:fp\n1:a 1:1\n3:a.b 1:3\n1:b 1:2\n", overlay.canonical());
    }
}
