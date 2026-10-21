package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Design gain of the uncalibrated ladder stays inside ±10 % of 0.12 (M22.7). */
class LadderCalibrationTest {
    @Test
    void designGainIsTheUncalibratedConstant() throws Exception {
        assertEquals(0.12, DifficultyGlobals.LADDER_K, 0.12 * 0.10);
        try (var input = PressureLadder.class.getResourceAsStream(PressureLadder.RESOURCE)) {
            var text = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.contains("\"calibrated\": false"));
            assertTrue(text.contains("\"k\": 0.12"));
            assertTrue(text.contains("uncalibrated ladder"));
            assertTrue(!text.contains("ladder-v2"));
        }
    }
}
