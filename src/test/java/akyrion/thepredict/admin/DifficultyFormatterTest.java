package akyrion.thepredict.admin;

import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.DifficultyPreset;
import akyrion.thepredict.domain.PresetNumbers;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An administrator sees the preset (M22.11), and the block without a session follows the configuration (CAR-21). */
class DifficultyFormatterTest {
    @Test
    void theAdminBlockNamesThePresetEntropyReactionAndRung() {
        var text = DifficultyFormatter.admin("Normal", true, 5, 5.0, 8);
        assertTrue(text.contains("preset Normal"));
        assertTrue(text.contains("entropy true"));
        assertTrue(text.contains("r 5"));
        assertTrue(text.contains("pressure 8"));
    }

    @Test
    void theBenchBlockUsesTheConfiguredDefaultPresetAndItsFloor() {
        var standard = DifficultyGlobals.standard();
        assertEquals("preset Normal entropy false\nr 5 c 0.0 pressure bench", DifficultyFormatter.bench(standard));
        var hard = new DifficultyGlobals(standard.defaultMode(), DifficultyPreset.DIFICIL,
                Map.of(DifficultyPreset.DIFICIL, new PresetNumbers(15, 2, 0, 3.5)), standard.worlds(),
                standard.adjustIntervalTicks(), standard.toleranceRatio(), standard.deadbandRatio(),
                standard.learningGain(), standard.projectedTtkCapRatio(), standard.dampingReversals(),
                standard.estimatorHalfLifeTicks(), standard.minEvidenceTicks(), standard.disengageTicks(),
                standard.maxTrackedFights(), standard.nativeLevelAllowed());
        assertEquals("preset Difícil entropy false\nr 2 c 0.0 pressure bench", DifficultyFormatter.bench(hard));
    }
}
