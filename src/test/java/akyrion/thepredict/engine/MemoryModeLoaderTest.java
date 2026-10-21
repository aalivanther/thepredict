package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryModeLoaderTest {
    @Test
    void anUnverifiedModeStaysAtRecordUntilAllowUnverified() {
        var loader = new MemoryModeLoader();
        loader.offer(MemoryMode.FULL, false, MemorySettings.DEFAULT);
        assertEquals(MemoryMode.RECORD, loader.active());
        loader.tick();
        assertEquals(MemoryMode.RECORD, loader.active());
        assertTrue(loader.unverified());
        var open = new MemorySettings(MemoryMode.FULL, 0.9, 30, 4, 0.25, 6, 4, 3, 3, 0.2, 8, 2, 60, 64, 16, 10, 60,
                8, 64, 8, 180, 1000, true, "sqlite");
        loader.offer(MemoryMode.FULL, false, open);
        assertEquals(MemoryMode.RECORD, loader.active());
        loader.tick();
        assertEquals(MemoryMode.FULL, loader.active());
        assertFalse(loader.unverified());
        loader.offer(MemoryMode.SEED, true, MemorySettings.DEFAULT);
        assertEquals(MemoryMode.FULL, loader.active());
        loader.tick();
        assertEquals(MemoryMode.SEED, loader.active());
        assertFalse(loader.unverified());
    }
}
