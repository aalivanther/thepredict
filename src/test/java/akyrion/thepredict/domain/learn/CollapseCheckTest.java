package akyrion.thepredict.domain.learn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollapseCheckTest {
    @Test
    void eightyPercentOnOneTacticIsARiskAndDoesNotThrow() {
        assertTrue(CollapseCheck.collapsed(new double[] {0.81, 0.19}));
        assertFalse(CollapseCheck.collapsed(new double[] {0.8, 0.2}));
        assertFalse(CollapseCheck.collapsed(new double[] {0.0}));
    }
}
