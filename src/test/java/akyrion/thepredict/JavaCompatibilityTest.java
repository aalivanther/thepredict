package akyrion.thepredict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaCompatibilityTest {
    @Test
    void requiresJava25OrNewer() {
        var compatibility = new JavaCompatibility();

        assertFalse(compatibility.supports(21));
        assertFalse(compatibility.supports(24));
        assertTrue(compatibility.supports(25));
        assertTrue(compatibility.supports(26));
    }
}
