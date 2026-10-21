package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.TellAnswer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** X2 and X3, within 0.02, seed 23 (M23.10). */
class TellResponseModelTest {
    private static final int SAMPLES = 20_000;
    private static final long SEED = 23L;
    private static final int[] LENGTHS = {14, 16, 18, 20, 22, 24, 26};

    @Test
    void versionTwoMatchesThePublishedShieldAndBackoffCurves() {
        assertCurve(TellAnswer.BLOCK, 40.0, new double[] {0.42, 0.73, 0.90, 0.96, 0.99, 1.00, 1.00});
        assertCurve(TellAnswer.BLOCK, 100.0, new double[] {0.22, 0.57, 0.81, 0.93, 0.98, 0.99, 1.00});
        assertCurve(TellAnswer.BLOCK, 200.0, new double[] {0.02, 0.22, 0.56, 0.81, 0.93, 0.98, 0.99});
        assertCurve(TellAnswer.BACK_OFF, 100.0, new double[] {0.00, 0.09, 0.39, 0.71, 0.88, 0.96, 0.99});
    }

    @Test
    void effectiveMinimumsMatchThePublishedClassTable() {
        assertPoint(Danger.HEAVY_BLOCK, 17, new double[] {0.83, 0.70, 0.39});
        assertPoint(Danger.HEAVY_BACK, 20, new double[] {0.83, 0.70, 0.39});
        assertPoint(Danger.SEVERE_BLOCK, 25, new double[] {1.00, 1.00, 0.99});
        assertPoint(Danger.MEDIUM_BLOCK, 9, new double[] {0.00, 0.00, 0.00});
    }

    private static void assertCurve(TellAnswer answer, double rtt, double[] expected) {
        for (var index = 0; index < LENGTHS.length; index++) {
            var rate = TellResponseModel.rate(LENGTHS[index], rtt, TellResponseModel.Player.TYPICAL,
                    TellResponseModel.actionTicks(answer), SAMPLES, SEED, true);
            assertEquals(expected[index], rate, 0.02, answer + " rtt " + rtt + " T " + LENGTHS[index]);
        }
    }

    private static void assertPoint(Danger danger, int ticks, double[] expected) {
        var rtt = new double[] {40.0, 100.0, 200.0};
        for (var index = 0; index < rtt.length; index++) {
            var rate = TellResponseModel.rate(ticks, rtt[index], TellResponseModel.Player.TYPICAL,
                    TellResponseModel.actionTicks(danger.answer), SAMPLES, SEED, true);
            assertEquals(expected[index], rate, 0.02, danger + " rtt " + rtt[index]);
        }
    }

    private enum Danger {
        HEAVY_BLOCK(TellAnswer.BLOCK),
        HEAVY_BACK(TellAnswer.BACK_OFF),
        SEVERE_BLOCK(TellAnswer.BLOCK),
        MEDIUM_BLOCK(TellAnswer.BLOCK);

        private final TellAnswer answer;

        Danger(TellAnswer answer) {
            this.answer = answer;
        }
    }
}
