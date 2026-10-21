package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.MemoryFact;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.ProfileHash;
import akyrion.thepredict.domain.ProfileMemory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryFactSelectorTest {
    @Test
    void theFirstMatchWinsAndAnOldFightIsSilent() {
        var many = row(3, 0, FightOutcome.LOSS, 10);
        assertEquals(Optional.of(MemoryFact.LOST_MANY), MemoryFactSelector.select(memory(many), "boss", 10, 60));
        var last = row(1, 0, FightOutcome.LOSS, 10);
        assertEquals(Optional.of(MemoryFact.LOST_LAST), MemoryFactSelector.select(memory(last), "boss", 10, 60));
        var wins = row(0, 3, FightOutcome.WIN, 10);
        assertEquals(Optional.of(MemoryFact.BEAT_MANY), MemoryFactSelector.select(memory(wins), "boss", 10, 60));
        var once = row(0, 1, FightOutcome.WIN, 10);
        assertEquals(Optional.of(MemoryFact.BEAT_BEFORE), MemoryFactSelector.select(memory(once), "boss", 10, 60));
        assertTrue(MemoryFactSelector.select(memory(last), "boss", 80, 60).isEmpty());
        assertTrue(MemoryFactSelector.select(memory(row(0, 0, FightOutcome.DISENGAGED, 10)), "boss", 10, 60).isEmpty());
        assertTrue(MemoryFactSelector.select(memory(row(0, 3, FightOutcome.DISENGAGED, 10)), "boss", 10, 60).isEmpty());
    }

    private static PlayerMemory memory(ProfileMemory profile) {
        return new PlayerMemory(10, 10, 1, Optional.empty(), Optional.empty(), Optional.empty(), List.of(profile));
    }

    private static ProfileMemory row(long losses, long wins, FightOutcome outcome, long day) {
        return new ProfileMemory(ProfileHash.of("boss"), wins + losses, wins, losses, 0, outcome, day, 0f, 0f, 0f,
                new int[] {255, 255, 255, 255, 255, 255}, List.of());
    }
}
