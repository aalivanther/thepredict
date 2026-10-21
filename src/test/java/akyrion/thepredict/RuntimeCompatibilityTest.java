package akyrion.thepredict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeCompatibilityTest {
    @Test
    void acceptsStableVersionsFromTheCompiledPaperBaseline() {
        var compatibility = new RuntimeCompatibility();

        assertTrue(compatibility.supportsMinecraft("26.2"));
        assertTrue(compatibility.supportsMinecraft("26.2.1"));
        assertTrue(compatibility.supportsMinecraft("26.3"));
        assertTrue(compatibility.supportsMinecraft("27.0"));
    }

    @Test
    void rejectsOlderOrUnparseableVersions() {
        var compatibility = new RuntimeCompatibility();

        assertFalse(compatibility.supportsMinecraft("26.1"));
        assertFalse(compatibility.supportsMinecraft("1.21.11"));
        assertFalse(compatibility.supportsMinecraft("26.2-SNAPSHOT"));
        assertFalse(compatibility.supportsMinecraft(null));
    }
}
