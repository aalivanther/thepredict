package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.StyleMemory;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubjectEnemyModelSeedTest {
    @Test
    void aSeedIsCappedAndForgottenAndDoesNotTouchActions() {
        var success = new float[StyleMemory.PATTERNS];
        var failure = new float[StyleMemory.PATTERNS];
        success[0] = 8f;
        failure[0] = 2f;
        var style = new StyleMemory(1f, 1, success, failure, new int[StyleMemory.PATTERNS], new float[StyleMemory.ACTIONS],
                0f, new int[0], new float[0], new float[StyleMemory.HISTOGRAMS],
                new byte[StyleMemory.HISTOGRAMS][StyleMemory.BINS], 0f, 0f, 0f, new float[StyleMemory.EPISODES],
                new float[StyleMemory.EPISODES]);
        var memory = new PlayerMemory(1, 1, 1, Optional.empty(), Optional.empty(), Optional.of(style), java.util.List.of());
        var seed = new SubjectEnemyModelSeed(memory, MemorySettings.DEFAULT, 1).seedFor(new UUID(1, 1));
        var trials = seed.successes()[0] + seed.failures()[0];
        assertTrue(trials <= MemorySettings.DEFAULT.seedMaxTrials() + 1e-6);
        assertTrue(trials > 0);
        var later = new SubjectEnemyModelSeed(memory, MemorySettings.DEFAULT, 1 + 30).seedFor(new UUID(9, 9));
        assertTrue(later.successes()[0] < seed.successes()[0]);
        assertEquals(0.0, later.successes()[1], 0.0);
    }
}
