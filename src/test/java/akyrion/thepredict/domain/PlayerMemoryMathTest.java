package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMemoryMathTest {
    @Test
    void halfLifeAndForwardDayMatchTheFormula() {
        assertEquals(0.5, PlayerMemoryMath.tau(30, 30), 1e-12);
        assertEquals(0.125, PlayerMemoryMath.tau(90, 30), 1e-12);
        assertEquals(1.0, PlayerMemoryMath.tau(-4, 30), 0.0);
        assertEquals(40L, PlayerMemoryMath.forwardDay(40, 10));
        assertEquals(12L, PlayerMemoryMath.forwardDay(3, 12));
    }
}
