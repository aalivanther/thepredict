package akyrion.thepredict.eval;

import akyrion.thepredict.domain.MemoryMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryEvalTest {
    @Test
    void theKnownCaseStaysOnRecordAndPublishesTheGain() {
        var known = MemoryEval.knownCase(32, 27L);
        assertEquals(MemoryMode.RECORD, known.mode());
        assertEquals(MemoryMode.RECORD, MemoryMode.DEFAULT);
        assertTrue(known.text().contains("spam_clicks"));
        assertTrue(Double.isFinite(known.gainPoints()));
        assertTrue(known.gainPoints() > 0.0);
        assertTrue(!known.text().contains("full campaign not run"));
    }

    @Test
    void thePlanGridPublishesE1ThroughE5() {
        var text = MemoryGrid.report(MemoryGrid.PLAN_SEED, 1);
        assertTrue(text.contains("seed 27"));
        assertTrue(text.contains("campaigns 1"));
        assertTrue(text.contains("plan campaigns 500"));
        assertTrue(text.contains("patterns 25"));
        assertTrue(text.contains("MEMORY_DETECTION_GAIN"));
        assertTrue(text.contains("MEMORY_STALE_ACTIVATION"));
        assertTrue(text.contains("MEMORY_SEED_PARITY 1"));
        assertTrue(text.contains("E4"));
        assertTrue(text.contains("E5"));
        assertTrue(text.contains("EXPLOIT_GAIN_TTK"));
        assertTrue(text.contains("classic-tuned"));
        assertTrue(text.contains("local-best"));
        assertTrue(text.contains("MEMORY_DETECTION_GAIN no es E3"));
        assertTrue(text.contains("E6 no ejecutada"));
        assertTrue(text.contains("E7 no ejecutada"));
        assertTrue(text.contains("mode record"));
        assertTrue(text.contains("none EXPLOIT_GAIN_TTK"));
        assertTrue(text.contains("classic EXPLOIT_GAIN_TTK"));
        assertTrue(text.contains("arms seed,record"));
        assertTrue(text.contains("fights>=2"));
        assertTrue(text.contains("llr="));
        assertTrue(!text.contains("llr=none"));
        assertTrue(!text.contains("GSPRT no decide"));
        assertTrue(!text.contains("full campaign not run"));
        assertTrue(entityArmsAreIdentical(text), "against entities the memory does not act: Δ = 0 on every pair");
        assertTrue(noGainAgainstEntities(text));
        assertTrue(text.contains("E3 GAIN contra sinteticos no medida"));
    }

    /** Every contrast line has pairs, and each pair has the same death tick on both arms (SIM-05). */
    private static boolean entityArmsAreIdentical(String text) {
        var lines = 0;
        for (var line : text.split("\n")) {
            var mark = line.indexOf("pairTicks=");
            if (mark < 0) {
                continue;
            }
            lines++;
            var pairs = line.substring(mark + "pairTicks=".length()).split(",");
            for (var pair : pairs) {
                var sides = pair.split(":");
                if (sides.length != 2 || !sides[0].equals(sides[1])) {
                    return false;
                }
            }
            if (!line.contains("identical=yes") || !line.contains("meanDelta=0.0 ")) {
                return false;
            }
        }
        return lines == 4;
    }

    /** No entity contrast may claim a gain: the memory cannot act against an entity. */
    private static boolean noGainAgainstEntities(String text) {
        for (var line : text.split("\n")) {
            if (line.contains("EXPLOIT_GAIN_TTK") && line.contains("GAIN 0.50-0.55")) {
                return false;
            }
        }
        return true;
    }
}
