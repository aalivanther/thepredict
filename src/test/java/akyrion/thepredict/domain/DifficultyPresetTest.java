package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Named presets and the precedence of T (M22.13, errata E22-4). */
class DifficultyPresetTest {
    @Test
    void namesFloorsAndMinutesMatchTheDelivery() {
        assertEquals(DifficultyPreset.NORMAL, DifficultyPreset.DEFAULT);
        assertEquals(DifficultyMode.AUTO, DifficultyMode.DEFAULT);
        assertEquals(45, DifficultyPreset.FACIL.targetMinutes());
        assertEquals(30, DifficultyPreset.NORMAL.targetMinutes());
        assertEquals(15, DifficultyPreset.DIFICIL.targetMinutes());
        assertEquals(10, DifficultyPreset.CAOS.targetMinutes());
        assertEquals(5, DifficultyPreset.VACIO.targetMinutes());
        assertEquals(5, DifficultyPreset.FACIL.reactionFloorTicks());
        assertEquals(5, DifficultyPreset.NORMAL.reactionFloorTicks());
        assertEquals(3, DifficultyPreset.DIFICIL.reactionFloorTicks());
        assertEquals(1, DifficultyPreset.CAOS.reactionFloorTicks());
        assertEquals(0, DifficultyPreset.VACIO.reactionFloorTicks());
        assertEquals(4, DifficultyPreset.FACIL.entropyReactionFloorTicks());
        assertEquals(3, DifficultyPreset.NORMAL.entropyReactionFloorTicks());
        assertEquals(0, DifficultyPreset.DIFICIL.entropyReactionFloorTicks());
        assertEquals(7.0, DifficultyPreset.FACIL.entropyAdaptStartSeconds());
        assertEquals(3.5, DifficultyPreset.DIFICIL.entropyAdaptStartSeconds());
        assertEquals(1.5, DifficultyPreset.CAOS.entropyAdaptStartSeconds());
        assertEquals(DifficultyPreset.DIFICIL, DifficultyPreset.parse("Difícil"));
        assertEquals(DifficultyPreset.VACIO, DifficultyPreset.parse("vacio"));
        assertThrows(IllegalArgumentException.class, () -> DifficultyPreset.parse("heroic"));
    }

    @Test
    void worldPresetBeatsSecondsAndAnAbsentTargetUsesThePreset() {
        var preset = DifficultyResolution.preset(DifficultyPreset.CAOS, DifficultyPreset.FACIL, DifficultyPreset.NORMAL,
                DifficultyPreset.DEFAULT);
        assertEquals(DifficultyPreset.CAOS, preset);
        assertEquals(DifficultyPreset.FACIL, DifficultyResolution.preset(null, DifficultyPreset.FACIL, null,
                DifficultyPreset.DEFAULT));
        assertEquals(600, DifficultyResolution.targetSeconds(true, 600, 90, 120, 1800));
        assertEquals(90, DifficultyResolution.targetSeconds(false, 600, 90, 120, 1800));
        assertEquals(120, DifficultyResolution.targetSeconds(false, 600, DifficultyResolution.ABSENT, 120, 1800));
        assertEquals(1800, DifficultyResolution.targetSeconds(false, 600, DifficultyResolution.ABSENT,
                DifficultyResolution.ABSENT, 1800));
        assertEquals(PlayMode.PLAYER, DifficultyResolution.play(DifficultyMode.AUTO, true));
        assertEquals(PlayMode.BENCH, DifficultyResolution.play(DifficultyMode.AUTO, false));
        assertEquals(true, DifficultyResolution.entropy(null, true));
        assertEquals(false, DifficultyResolution.entropy(Boolean.FALSE, true));
    }
}
