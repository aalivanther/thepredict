package akyrion.thepredict.engine.duel;

import akyrion.thepredict.eval.PatternEval;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternScriptTest {
    @Test
    void tenThousandDrawsStayNearTheScriptedFrequency() {
        var text = PatternEval.frequency(new PatternScript("spam_clicks", 0.7), 10_000, 26L);
        var frequency = Double.parseDouble(text.substring(text.lastIndexOf(' ') + 1));
        assertTrue(text.startsWith("spam_clicks"));
        assertTrue(Math.abs(frequency - 0.7) < 0.02);
    }
}
