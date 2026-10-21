package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyEventsTest {
    @Test
    void theSixtyFifthEventDropsTheOldest() {
        var events = new EnemyEvents();
        var id = new UUID(0, 1);
        for (var tick = 0; tick < 65; tick++) {
            events.offer(id, tick, tick);
        }
        assertEquals(EnemyEvents.CAPACITY, events.size());
        assertEquals(1, events.dropped(), "65 offers into a ring of 64 drop exactly one event");
        assertEquals(1, events.poll());
    }
}
