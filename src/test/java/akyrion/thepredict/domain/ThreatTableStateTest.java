package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThreatTableStateTest {
    @Test
    void aDisabledTableHasNoEntriesAndNoThreat() {
        assertFalse(ThreatTableState.DISABLED.enabled());
        assertEquals(0, ThreatTableState.DISABLED.entries());
        assertEquals(new ThreatTableState(true, 0, 0.0, 0.0).entries(), 0);
    }

    @Test
    void negativeOrNonFiniteValuesAndEntriesWithoutATableAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ThreatTableState(true, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ThreatTableState(true, 1, -0.5, 0));
        assertThrows(IllegalArgumentException.class, () -> new ThreatTableState(true, 1, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ThreatTableState(true, 1, 1, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new ThreatTableState(false, 2, 0, 0));
    }
}
