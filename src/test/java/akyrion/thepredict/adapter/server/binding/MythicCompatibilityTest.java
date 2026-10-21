package akyrion.thepredict.adapter.server.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicCompatibilityTest {
    @Test
    void acceptsTheCompiledSnapshotAndLaterVersions() {
        var compatibility = new MythicCompatibility();

        assertTrue(compatibility.supports("5.13.1-SNAPSHOT-530971de"));
        assertTrue(compatibility.supports("5.13.1"));
        assertTrue(compatibility.supports("5.14.0"));
        assertTrue(compatibility.supports("6.0.0"));
    }

    @Test
    void rejectsOlderAndUnparseableVersions() {
        var compatibility = new MythicCompatibility();

        assertFalse(compatibility.supports("5.13.0"));
        assertFalse(compatibility.supports("5.12.9"));
        assertFalse(compatibility.supports("unknown"));
        assertFalse(compatibility.supports(null));
    }
}
