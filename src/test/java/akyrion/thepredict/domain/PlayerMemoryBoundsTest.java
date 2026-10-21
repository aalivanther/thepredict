package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMemoryBoundsTest {
    @Test
    void daysAndCountsRejectNegativesAndTheCapStays1024() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerMemory(-1, 0, 0, java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.List.of()));
        assertEquals(1024, PlayerMemoryCodec.MAX_BYTES);
        var encoded = PlayerMemoryCodec.encode(PlayerMemory.empty(), 8);
        assertTrue(encoded.bytes().length <= 1024);
        assertEquals(0, encoded.profilesEvicted());
    }
}
