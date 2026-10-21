package akyrion.thepredict.adapter.server.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirtyColumnsTest {
    /** SP-16: the overflow flag is not forever; a ring with room again is not overflowed. */
    @Test
    void theOverflowClearsWhenRetainMakesRoom() {
        var dirty = new DirtyColumns(2);
        assertTrue(dirty.offer(1, 10L));
        assertTrue(dirty.offer(2, 10L));
        assertFalse(dirty.offer(3, 10L));
        assertTrue(dirty.overflowed());
        dirty.retain((column, tick) -> true);
        assertTrue(dirty.overflowed());
        dirty.retain((column, tick) -> column != 1);
        assertFalse(dirty.overflowed());
        assertEquals(1, dirty.size());
        assertEquals(2, dirty.indexAt(0));
        assertTrue(dirty.offer(3, 11L));
        dirty.clear();
        assertEquals(0, dirty.size());
        assertFalse(dirty.overflowed());
    }
}
