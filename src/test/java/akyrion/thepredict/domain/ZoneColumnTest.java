package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneColumnTest {
    @Test
    void aFloorOutsideTheBandBecomesUnknown() {
        assertEquals(0L, ZoneColumn.pack(ZoneColumn.FLOOR, 600, 2, 0, 0L));
        var floor = ZoneColumn.floor(4, 3);
        assertEquals(ZoneColumn.FLOOR, ZoneColumn.state(floor));
        assertEquals(4, ZoneColumn.floorY(floor));
        assertTrue(ZoneColumn.walkable(floor, 2));
    }
}
