package akyrion.thepredict.engine;

import akyrion.thepredict.domain.PlayerMemory;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectMemoryTest {
    @Test
    void anEmptyRecordIsNotTheSameAsAMissingLoad() {
        var cache = new MemoryCache(64, new MemoryCounters());
        var subject = new UUID(1, 2);
        assertEquals(MemoryInspect.notLoaded(), cache.inspect(subject));
        cache.loaded(subject, new byte[0], false, false, 1);
        assertEquals(MemoryInspect.emptyHistory(), cache.inspect(subject));
        assertEquals(MemoryInspect.emptyHistory(), MemoryInspect.loaded(PlayerMemory.empty()));
        assertTrue(MemoryInspect.loaded(new PlayerMemory(1, 2, 3, Optional.empty(), Optional.empty(), Optional.empty(),
                java.util.List.of())).contains("rung"));
        assertTrue(MemoryInspect.loaded(new PlayerMemory(1, 2, 3, Optional.empty(), Optional.empty(), Optional.empty(),
                java.util.List.of())).contains("seed at admission"));
    }
}
