package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Y4 replays the probe noise. A sort that disagrees with the stored winner fails. */
class SuccessiveHalvingTest {
    @Test
    void everyRepPicksTheStoredWinnerAndTheBudgetIs6480Pairs() throws Exception {
        byte[] raw;
        try (var input = SuccessiveHalvingTest.class.getResourceAsStream("/distill/y4-sh.bin")) {
            raw = input.readAllBytes();
        }
        var at = new int[] {0};
        var reps = i32(raw, at);
        var configs = i32(raw, at);
        var initial = i32(raw, at);
        var eta = i32(raw, at);
        assertEquals(400, reps);
        assertEquals(81, configs);
        assertEquals(20, initial);
        assertEquals(3, eta);
        assertEquals(6480, pairs(configs, initial, eta));
        var best = 0;
        for (var rep = 0; rep < reps; rep++) {
            var scores = doubles(raw, at, configs);
            var stored = i32(raw, at);
            var noise = doubles(raw, at, 120);
            var winner = SuccessiveHalving.winner(noise, configs, initial, eta);
            assertEquals(stored, winner, "rep " + rep);
            if (scores[winner] == max(scores)) {
                best++;
            }
        }
        assertEquals(59, (int) Math.round(best * 100.0 / reps));
    }

    private static int pairs(int configs, int radius, int eta) {
        var total = 0;
        while (configs > 1) {
            total += configs * radius;
            configs = Math.max(1, configs / eta);
            radius *= eta;
        }
        return total;
    }

    private static double max(double[] scores) {
        var best = scores[0];
        for (var score : scores) {
            best = Math.max(best, score);
        }
        return best;
    }

    private static double[] doubles(byte[] raw, int[] at, int count) {
        var values = new double[count];
        for (var index = 0; index < count; index++) {
            values[index] = Double.longBitsToDouble(i64(raw, at));
        }
        return values;
    }

    private static int i32(byte[] raw, int[] at) {
        var value = (raw[at[0]] & 0xff) | ((raw[at[0] + 1] & 0xff) << 8) | ((raw[at[0] + 2] & 0xff) << 16)
                | ((raw[at[0] + 3] & 0xff) << 24);
        at[0] += 4;
        return value;
    }

    private static long i64(byte[] raw, int[] at) {
        var value = 0L;
        for (var shift = 0; shift < 8; shift++) {
            value |= (raw[at[0]++] & 0xffL) << (8 * shift);
        }
        return value;
    }
}
