package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryDigestsTest {
    @Test
    void capsDropAndOffStoresNothing() {
        var counters = new MemoryCounters();
        var tight = settings(2, 64);
        var digests = new MemoryDigests(tight, counters);
        var subject = new UUID(1, 1);
        assertTrue(digests.add(digest(subject)));
        assertTrue(digests.add(digest(subject)));
        assertFalse(digests.add(digest(subject)));
        assertEquals(1, counters.get(MemoryCounters.Kind.DIGEST_DROPPED));
        for (var index = 0; index < 63; index++) {
            assertTrue(digests.add(digest(new UUID(3, index))));
        }
        assertFalse(digests.add(digest(new UUID(2, 2))));
        assertEquals(2, counters.get(MemoryCounters.Kind.DIGEST_DROPPED));
        assertEquals(9, digests.take(8).size());
        var off = new MemoryDigests(new MemorySettings(MemoryMode.OFF, 0.9, 30, 4, 0.25, 6, 4, 3, 3, 0.2, 8, 2, 60,
                64, 16, 10, 60, 2, 64, 8, 180, 1000, false, "sqlite"), counters);
        assertFalse(off.add(digest(subject)));
    }

    private static MemorySettings settings(int perSubject, int subjects) {
        return new MemorySettings(MemoryMode.RECORD, 0.9, 30, 4, 0.25, 6, 4, 3, 3, 0.2, 8, 2, 60, 64, 16, 10, 60,
                perSubject, subjects, 8, 180, 1000, false, "sqlite");
    }

    private static FightDigest digest(UUID subject) {
        return new FightDigest(subject, 1, FightOutcome.WIN, PartyBand.ONE, DifficultySection.SOLO, Optional.empty(),
                1L, false);
    }
}
