package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The Memory section of a profile only lowers the global mode and can withhold the history phrase (M27, C13). */
class MemoryProfileTest {
    @Test
    void theFactIsShownOnlyInAnEffectiveFullModeWithFactsOn() {
        var silent = new MemoryProfile(Optional.empty(), false);
        var lowered = new MemoryProfile(Optional.of(MemoryMode.SEED), true);
        for (var global : MemoryMode.values()) {
            assertEquals(global == MemoryMode.FULL, MemoryProfile.DEFAULT.factShown(global), global.name());
            assertFalse(silent.factShown(global));
            assertFalse(lowered.factShown(global));
            assertEquals(global.rank() < MemoryMode.SEED.rank() ? global : MemoryMode.SEED, lowered.effective(global));
        }
    }
}
