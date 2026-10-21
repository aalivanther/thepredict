package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.EncounterId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneIndexTest {
    @Test
    void openRegistersBeforeInstallAndOverflowKeepsTheEpoch() {
        var index = new ZoneIndex();
        var id = new EncounterId(7);
        assertFalse(index.contains(id));
        index.open(id, 10L);
        assertTrue(index.contains(id));
        assertTrue(index.replay(10L, 10L));
        assertFalse(index.replay(9L, 10L));
        var epoch = index.overflow(id);
        assertEquals(epoch, index.overflow(id));
    }
}
