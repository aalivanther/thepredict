package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Precedence of preset, T, entropy and play mode (M22.13): world, then variable, then mob file, then default. */
class DifficultyResolutionTest {
    @Test
    void theWorldPresetBeatsTheVariableWhichBeatsTheMobFile() {
        var fallback = DifficultyPreset.DEFAULT;
        var values = DifficultyPreset.values();
        var world = values[0];
        var variable = values[1];
        var mob = values[2];
        assertEquals(world, DifficultyResolution.preset(world, variable, mob, fallback));
        assertEquals(variable, DifficultyResolution.preset(null, variable, mob, fallback));
        assertEquals(mob, DifficultyResolution.preset(null, null, mob, fallback));
        assertEquals(fallback, DifficultyResolution.preset(null, null, null, fallback));
    }

    @Test
    void anImposedWorldBeatsAnExplicitTargetTime() {
        assertEquals(900, DifficultyResolution.targetSeconds(true, 900, 30, 60, 300));
        assertEquals(30, DifficultyResolution.targetSeconds(false, 900, 30, 60, 300));
        assertEquals(60, DifficultyResolution.targetSeconds(false, 900, DifficultyResolution.ABSENT, 60, 300));
        assertEquals(300, DifficultyResolution.targetSeconds(false, 900, DifficultyResolution.ABSENT,
                DifficultyResolution.ABSENT, 300));
    }

    @Test
    void aWorldValueDecidesEntropyAndAutoFollowsTheTarget() {
        assertTrue(DifficultyResolution.entropy(Boolean.TRUE, false));
        assertFalse(DifficultyResolution.entropy(Boolean.FALSE, true));
        assertTrue(DifficultyResolution.entropy(null, true));
        assertEquals(PlayMode.PLAYER, DifficultyResolution.play(DifficultyMode.AUTO, true));
        assertEquals(PlayMode.BENCH, DifficultyResolution.play(DifficultyMode.AUTO, false));
        assertEquals(PlayMode.BENCH, DifficultyResolution.play(DifficultyMode.BENCH, true));
        assertEquals(PlayMode.PLAYER, DifficultyResolution.play(DifficultyMode.PLAYER, false));
    }
}
