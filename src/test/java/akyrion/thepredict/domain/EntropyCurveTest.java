package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reaction starts at the normal floor and ends at the entropy floor (M22.13, errata E22-4). */
class EntropyCurveTest {
    @Test
    void eachPresetStartsAtItsNormalFloorAndEndsAtItsEntropyFloor() {
        for (var preset : DifficultyPreset.values()) {
            var start = EntropyCurve.reactionTicks(preset.reactionFloorTicks(), preset.entropyReactionFloorTicks(),
                    0.0, preset.targetSeconds());
            var end = EntropyCurve.reactionTicks(preset.reactionFloorTicks(), preset.entropyReactionFloorTicks(),
                    preset.targetSeconds(), preset.targetSeconds());
            assertEquals(preset.reactionFloorTicks(), start, preset.name());
            assertEquals(preset.entropyReactionFloorTicks(), end, preset.name());
            var mid = EntropyCurve.reactionTicks(preset.reactionFloorTicks(), preset.entropyReactionFloorTicks(),
                    preset.targetSeconds() / 2.0, preset.targetSeconds());
            assertTrue(mid <= preset.reactionFloorTicks(), preset.name());
            assertEquals(preset.entropyAdaptStartSeconds(),
                    EntropyCurve.adaptSeconds(preset.entropyAdaptStartSeconds(), 0.0, preset.targetSeconds()));
            assertEquals(0.0, EntropyCurve.adaptSeconds(preset.entropyAdaptStartSeconds(), preset.targetSeconds(),
                    preset.targetSeconds()));
            var atEnd = EntropyCurve.adaptSeconds(preset.entropyAdaptStartSeconds(), preset.targetSeconds(),
                    preset.targetSeconds());
            assertEquals(0, EntropyCurve.effectiveIntervalTicks(atEnd, 6), preset.name() + ": no wait at T");
        }
        assertEquals(5, EntropyCurve.reactionTicks(5, 3, 0.0, 1800.0));
        assertEquals(3, EntropyCurve.reactionTicks(5, 3, 1800.0, 1800.0));
    }

    @Test
    void theIntervalIsTheSmallerOfTheCurveAndTheNormalOneAndZeroMeansNoWait() {
        assertEquals(6, EntropyCurve.effectiveIntervalTicks(1.0, 6));
        assertEquals(4, EntropyCurve.effectiveIntervalTicks(0.2, 6));
        assertEquals(1, EntropyCurve.effectiveIntervalTicks(0.05, 6));
        assertEquals(0, EntropyCurve.effectiveIntervalTicks(0.02, 6));
        assertEquals(0, EntropyCurve.effectiveIntervalTicks(0.0, 6));
        assertEquals(0, EntropyCurve.effectiveIntervalTicks(-1.0, 6));
        assertEquals(20, EntropyCurve.effectiveIntervalTicks(1.0, 0), "a normal interval of zero does not cap");
    }
}
