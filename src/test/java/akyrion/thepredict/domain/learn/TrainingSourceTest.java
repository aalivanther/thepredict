package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.DifficultyPolicy;
import akyrion.thepredict.domain.PlayMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TrainingSourceTest {
    @Test
    void aPlayerFightBelowTheTopRungIsPacedAndNeverTheSimulator() {
        assertEquals(TrainingSource.PLAYER_PACED, TrainingSource.of(true, false, PlayMode.PLAYER, 5));
        assertEquals(TrainingSource.PLAYER_PACED,
                TrainingSource.of(true, false, PlayMode.PLAYER, DifficultyPolicy.MAX_RUNG - 1));
        assertEquals(TrainingSource.PLAYER_FULL,
                TrainingSource.of(true, false, PlayMode.PLAYER, DifficultyPolicy.MAX_RUNG));
        assertEquals(TrainingSource.PLAYER_FULL, TrainingSource.of(true, false, PlayMode.BENCH, 3));
        assertEquals(TrainingSource.PLAYER_TRAINING, TrainingSource.of(true, true, PlayMode.PLAYER, 5));
        assertEquals(TrainingSource.BENCH, TrainingSource.of(false, true, PlayMode.PLAYER, 2));
        assertEquals(TrainingSource.BENCH, TrainingSource.of(false, false, PlayMode.BENCH, 8));
        assertFalse(TrainingSource.DEFAULTS.contains(TrainingSource.of(true, false, PlayMode.PLAYER, 5)));
    }

    @Test
    void theStoredTokenIsTheConfigToken() {
        for (var source : TrainingSource.values()) {
            assertEquals(source, TrainingSource.parse(source.token()));
        }
        assertEquals("player_paced", TrainingSource.PLAYER_PACED.token());
    }

    @Test
    void theWeekIsUtcEpochDaysOverSeven() {
        var day = 86_400_000L;
        assertEquals(0, ValueStatKey.week(0L));
        assertEquals(0, ValueStatKey.week(7 * day - 1));
        assertEquals(1, ValueStatKey.week(7 * day));
        assertEquals(-1, ValueStatKey.week(-1L));
        var epochDays = 20_740L;
        assertEquals((int) (epochDays / 7), ValueStatKey.week(epochDays * day + 5_000L));
        assertEquals(Integer.MAX_VALUE, ValueStatKey.week(Long.MAX_VALUE));
    }
}
