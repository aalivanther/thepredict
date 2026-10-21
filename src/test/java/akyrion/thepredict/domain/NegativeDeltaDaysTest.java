package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NegativeDeltaDaysTest {
    @Test
    void aClockSkewDoesNotForget() {
        assertEquals(PlayerMemoryMath.tau(0, 30), PlayerMemoryMath.tau(-90, 30), 0.0);
        assertEquals(80L, PlayerMemoryMath.forwardDay(80, 1));
    }
}
