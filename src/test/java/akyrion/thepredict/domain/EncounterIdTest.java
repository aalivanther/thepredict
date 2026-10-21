package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncounterIdTest {
    @Test
    void positiveValuesAreAcceptedAndEqualByValue() {
        assertEquals(new EncounterId(1), new EncounterId(1));
    }

    @Test
    void zeroAndNegativeValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EncounterId(0));
        assertThrows(IllegalArgumentException.class, () -> new EncounterId(-5));
    }
}
