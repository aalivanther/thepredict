package akyrion.thepredict.eval;

import java.util.Arrays;

/**
 * Copies an oracle transcript and scores a gain contrast (M22.3).
 */
public final class OutcomeOracle {
    private OutcomeOracle() {
    }

    /**
     * A byte-for-byte copy.
     *
     * @param bytes the transcript
     * @return a new array
     */
    public static byte[] copy(byte[] bytes) {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * GSPRT of pair halves for {@code GAIN} ({@code 0.50} to {@code 0.55}).
     *
     * @param halves pair score divided by two; the length is the pair count and must be a multiple of 10
     * @return the GSPRT decision
     */
    public static String gain(double[] halves) {
        return EvalStats.gsprtPairs(halves, halves.length, 0.50, 0.55, EvalStats.MAX_PAIRS, EvalStats.SPRT_A);
    }
}
