package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SituationSignatureTest {
    @Test
    void aChangedBitIsVisibleAndStaysInsideSeventeenBits() {
        var first = SituationSignature.pack(DistanceBand.MELEE, HealthBand.HEALTHY, HealthBand.WOUNDED, false, false,
                false, false, true, true, false, 1);
        var second = SituationSignature.pack(DistanceBand.MELEE, HealthBand.HEALTHY, HealthBand.WOUNDED, true, false,
                false, false, true, true, false, 1);
        assertNotEquals(first, second);
        assertTrue(SituationSignature.bit(second, 6));
        assertEquals(first & ~0x1ffff, 0);
    }
}
