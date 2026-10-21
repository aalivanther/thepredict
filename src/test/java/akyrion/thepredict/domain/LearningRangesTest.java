package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The rates of {@code learning.*} accept their documented ranges and reject a NaN, which compares false. */
class LearningRangesTest {
    @Test
    void theBoundsOfEveryRateAreAccepted() {
        assertDoesNotThrow(() -> LearningRanges.checkRates(0.0, 1.0, 0.5, 0.5, 0.0));
        assertDoesNotThrow(() -> LearningRanges.checkRates(1.0, 0.0, 1.0, 0.99, 0.2));
    }

    @Test
    void aValueOutsideItsRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LearningRanges.checkRates(1.1, 0.5, 0.9, 0.8, 0.1));
        assertThrows(IllegalArgumentException.class, () -> LearningRanges.checkRates(0.5, 0.5, 0.49, 0.8, 0.1));
        assertThrows(IllegalArgumentException.class, () -> LearningRanges.checkRates(0.5, 0.5, 0.9, 0.995, 0.1));
        assertThrows(IllegalArgumentException.class, () -> LearningRanges.checkRates(0.5, 0.5, 0.9, 0.8, 0.21));
    }

    @Test
    void aNaNInAnyRateIsRejected() {
        var nan = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> LearningRanges.checkRates(nan, 0.5, 0.9, 0.8, 0.1));
        assertThrows(IllegalArgumentException.class, () -> LearningRanges.checkRates(0.5, nan, 0.9, 0.8, 0.1));
        assertThrows(IllegalArgumentException.class, () -> LearningRanges.checkRates(0.5, 0.5, nan, 0.8, 0.1));
        assertThrows(IllegalArgumentException.class, () -> LearningRanges.checkRates(0.5, 0.5, 0.9, nan, 0.1));
        assertThrows(IllegalArgumentException.class, () -> LearningRanges.checkRates(0.5, 0.5, 0.9, 0.8, nan));
    }
}
