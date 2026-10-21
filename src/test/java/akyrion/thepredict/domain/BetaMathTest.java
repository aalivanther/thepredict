package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetaMathTest {
    @Test
    void theFrozenTableMatchesWithinOneMillionth() throws Exception {
        var rows = 0;
        try (var reader = new BufferedReader(new InputStreamReader(
                BetaMathTest.class.getResourceAsStream("/m26/beta-cdf.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                var parts = line.split("\t");
                var a = Double.parseDouble(parts[0]);
                var b = Double.parseDouble(parts[1]);
                var x = Double.parseDouble(parts[2]);
                var expected = Double.parseDouble(parts[3]);
                assertEquals(expected, BetaMath.cdf(x, a, b), 1e-6);
                rows++;
            }
        }
        assertEquals(200, rows);
    }

    @Test
    void logGammaSymmetryAndTheQuantileAgree() {
        assertEquals(0.0, BetaMath.logGamma(1.0), 1e-12);
        assertEquals(Math.log(120.0), BetaMath.logGamma(6.0), 1e-9);
        assertEquals(1.0, BetaMath.cdf(0.3, 2.0, 5.0) + BetaMath.cdf(0.7, 5.0, 2.0), 1e-6);
        assertEquals(0.25, BetaMath.cdf(0.25, 1.0, 1.0), 1e-6);
        var checks = 0;
        var cases = 200;
        for (var index = 1; index <= cases; index++) {
            var word = StableRandom.mix(26L ^ index);
            var a = 1.0 + (word & 15);
            var b = 1.0 + ((word >>> 4) & 15);
            var q = 0.05 + ((word >>> 11) & 15) / 100.0;
            var theta = BetaMath.lowerQuantile(q, a, b);
            assertTrue(BetaMath.tailBelow(theta, q, a, b), "the bisection keeps CDF(low) < q");
            if (BetaMath.tailBelow(theta - 1e-6, q, a, b)) {
                checks++;
            }
            assertTrue(BetaMath.lowerQuantile(q, a, b) >= 0.0);
        }
        assertEquals(cases, checks, "a monotone CDF stays below q left of the quantile");
    }
}
