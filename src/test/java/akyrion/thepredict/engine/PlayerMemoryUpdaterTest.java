package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CounterMemory;
import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.ProfileHash;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PZ-13: the counters of a profile merge by pattern and keep the two best hit rates over three effective tries. */
class PlayerMemoryUpdaterTest {
    private static final UUID PLAYER = new UUID(5, 5);
    private static final int BOSS = ProfileHash.of("boss_ai");

    @Test
    void twoFightsOfTheSamePatternMergeIntoOneFadedEntry() {
        var first = PlayerMemoryUpdater.apply(PlayerMemory.empty(), List.of(digest(1_000L,
                new CounterMemory(5, 3, 4))), 2);
        var second = PlayerMemoryUpdater.apply(first, List.of(digest(2_000L, new CounterMemory(5, 1, 5))), 2);
        var gamma = MemorySettings.DEFAULT.fightDecay();
        var counters = second.profiles().getFirst().counters();
        assertEquals(1, counters.size(), "one entry per pattern, " + counters);
        assertEquals(5, counters.getFirst().pattern());
        assertEquals((float) (4 * gamma + 5), counters.getFirst().tries(), 1.0e-5);
        assertEquals((float) (3 * gamma + 1), counters.getFirst().landed(), 1.0e-5);
    }

    @Test
    void theTwoBestHitRatesStayNotTheTwoWithMostTries() {
        var stored = PlayerMemoryUpdater.apply(PlayerMemory.empty(), List.of(digest(1_000L,
                new CounterMemory(5, 3, 4), new CounterMemory(7, 1, 9))), 2);
        var merged = PlayerMemoryUpdater.apply(stored, List.of(digest(2_000L, new CounterMemory(9, 4, 4))), 2);
        var patterns = merged.profiles().getFirst().counters().stream().map(CounterMemory::pattern).toList();
        assertEquals(List.of(9, 5), patterns, "rates 1.0 and 0.75 beat 0.11, which has the most tries");
    }

    @Test
    void aFightBelowThreeTriesCountsOnceTheStoredEvidenceLiftsItOver() {
        var stored = PlayerMemoryUpdater.apply(PlayerMemory.empty(), List.of(digest(1_000L,
                new CounterMemory(4, 2, 3))), 2);
        var merged = PlayerMemoryUpdater.apply(stored, List.of(digest(2_000L, new CounterMemory(4, 1, 1))), 2);
        var counters = merged.profiles().getFirst().counters();
        assertEquals(1, counters.size());
        assertEquals((float) (3 * MemorySettings.DEFAULT.fightDecay() + 1), counters.getFirst().tries(), 1.0e-5);
    }

    @Test
    void theOverloadsWithoutOptionalFoldTheSameRecord() {
        var digests = List.of(digest(1_000L, new CounterMemory(5, 3, 4)));
        var counters = new MemoryCounters();
        var legacy = PlayerMemoryUpdater.apply(PlayerMemory.empty(), digests, Optional.empty(), 2,
                MemorySettings.DEFAULT, counters);
        var plain = PlayerMemoryUpdater.apply(PlayerMemory.empty(), digests, 2, MemorySettings.DEFAULT, counters);
        assertEquals(legacy.fights(), plain.fights());
        assertEquals(legacy.style(), plain.style());
        assertEquals(legacy.profiles().getFirst().counters(), plain.profiles().getFirst().counters());
        assertEquals(java.util.Arrays.toString(legacy.profiles().getFirst().rungs()),
                java.util.Arrays.toString(plain.profiles().getFirst().rungs()));
    }

    private static FightDigest digest(long end, CounterMemory... counters) {
        return new FightDigest(PLAYER, BOSS, FightOutcome.WIN, PartyBand.ONE, DifficultySection.SOLO,
                Optional.empty(), end, false, List.of(counters));
    }
}
