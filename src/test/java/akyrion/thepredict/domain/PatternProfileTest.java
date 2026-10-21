package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Patterns section of a profile: mode, facts and the entity quota it can lower (M26.11, C13). */
class PatternProfileTest {
    @Test
    void factsAreShownOnlyInAnEffectiveFullModeWithFactsOn() {
        var silent = new PatternProfile(Optional.empty(), BenchPolicy.FREE, false);
        var lowered = new PatternProfile(Optional.of(PatternMode.OFF), BenchPolicy.FREE, true);
        for (var global : PatternMode.values()) {
            assertEquals(global == PatternMode.FULL, PatternProfile.DEFAULT.factsShown(global), global.name());
            assertFalse(silent.factsShown(global));
            assertFalse(lowered.factsShown(global));
            assertEquals(PatternMode.OFF, lowered.effective(global));
            assertEquals(PatternProfile.DEFAULT.factsShown(global),
                    PatternProfile.DEFAULT.factsShown(PatternProfile.DEFAULT.effective(global)));
        }
    }

    @Test
    void onlyAFreeEntityTargetLiftsTheQuota() {
        assertTrue(PatternProfile.DEFAULT.entityFree(false));
        assertFalse(PatternProfile.DEFAULT.entityFree(true));
        var fair = new PatternProfile(Optional.empty(), BenchPolicy.FAIR, true);
        assertFalse(fair.entityFree(false));
        assertFalse(fair.entityFree(true));
    }

    @Test
    void thePatternSignatureBitsFollowTheModeAndTheFactsOfTheProfile() {
        var mask = EnemyPattern.CRIT_JUMPER.bit();
        var bits = SituationSignature.patternBits(mask);
        assertTrue(bits != 0);
        for (var mode : PatternMode.values()) {
            assertEquals(mode == PatternMode.FULL ? bits : 0, SituationSignature.patternBits(mode, true, mask));
            assertEquals(0, SituationSignature.patternBits(mode, false, mask));
        }
    }
}
