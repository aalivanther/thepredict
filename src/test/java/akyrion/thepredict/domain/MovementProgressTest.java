package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MovementProgressTest {
    @Test
    void pathReachingTheTargetRequiresAPath() {
        assertThrows(IllegalArgumentException.class, () -> new MovementProgress(true, false, true, OptionalDouble.empty()));
    }

    @Test
    void distanceMustBeFiniteAndNonNegative() {
        assertThrows(IllegalArgumentException.class, () -> new MovementProgress(true, true, true, OptionalDouble.of(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> new MovementProgress(true, true, true, OptionalDouble.of(Double.NaN)));
        assertEquals(OptionalDouble.of(0), new MovementProgress(false, true, false, OptionalDouble.of(0)).targetDistanceBlocks());
    }
}
