package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.DifficultyPolicy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sword skill 0.5 against a 60 s target (M22.8). The time-to-kill is {@code 30·exp(0.12·(8−rung))}, the probe model
 * whose ideal rung is inside the ladder. Defaults are not tuned.
 */
class DifficultyControlSimTest {
    @Test
    void fightsElevenToFortyStayWithinFifteenPercentOfTheTarget() {
        var globals = DifficultyGlobals.DEFAULT;
        var prior = 8.0;
        var seconds = new double[40];
        var rungs = new int[40];
        for (var fight = 0; fight < 40; fight++) {
            var rung = (int) Math.round(prior);
            rung = Math.max(0, Math.min(8, rung));
            rungs[fight] = rung;
            seconds[fight] = 30.0 * Math.exp(DifficultyGlobals.LADDER_K * (8 - rung));
            var delta = DifficultyPolicy.deltaU(Math.log(seconds[fight]), Math.log(60.0), globals.deadbandRatio(),
                    globals.effectiveGain(), DifficultyGlobals.LADDER_K, true);
            prior = DifficultyPolicy.nextU(prior, delta, 0, 8);
        }
        var tail = Arrays.copyOfRange(seconds, 10, 40);
        Arrays.sort(tail);
        var median = tail[tail.length / 2];
        var reversals = 0;
        for (var index = 2; index < rungs.length; index++) {
            if ((rungs[index] - rungs[index - 1]) * (rungs[index - 1] - rungs[index - 2]) < 0) {
                reversals++;
            }
        }
        assertTrue(median > 0.0);
        assertTrue(median >= 60.0 * 0.85 && median <= 60.0 * 1.15, "median " + median);
        assertTrue(reversals <= 40 / 5, "reversals " + reversals);
        assertEquals(8, rungs[0]);
    }
}
