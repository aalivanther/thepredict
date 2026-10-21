package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimingHistogramTest {
    @Test
    void sevenTicksLandInTheBinThatCoversThem() {
        var histogram = new TimingHistogram(2, 16);
        histogram.add(7);
        histogram.add(7);
        assertEquals(2, histogram.samples());
        assertTrue(histogram.percentileTicks(500) >= 6);
        assertTrue(histogram.percentileTicks(500) <= 8);
    }
}
