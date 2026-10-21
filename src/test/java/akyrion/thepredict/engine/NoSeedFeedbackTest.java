package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PlayerMemory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoSeedFeedbackTest {
    @Test
    void aDigestStoresTheFightAndNotASeedMass() {
        var digest = new FightDigest(new UUID(1, 1), 7, FightOutcome.WIN, PartyBand.ONE, DifficultySection.SOLO,
                Optional.empty(), 1L, false);
        var memory = PlayerMemoryUpdater.apply(PlayerMemory.empty(), List.of(digest), 2);
        var profile = memory.profiles().getFirst();
        assertTrue(profile.counters().isEmpty());
        assertTrue(memory.style().isEmpty());
        assertEqualsFights(1, profile.fights());
    }

    private static void assertEqualsFights(long expected, long actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
