package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistogramQuantizeTest {
    /**
     * Two equal halves give shares of 127.5 each: both floors are 127, both remainders 0.5 and one unit is left, so
     * the tie decides it and the lower index takes it.
     */
    @Test
    void cellsSumTo255AndTiesPreferTheLowerIndex() {
        var shape = new byte[16];
        shape[0] = (byte) 255;
        var added = new float[16];
        added[1] = 1f;
        var next = HistogramQuantizer.add(1f, shape, 1f, added);
        var sum = 0;
        for (var cell : next.shape()) {
            sum += cell & 0xFF;
        }
        assertEquals(255, sum);
        assertEquals(128, next.shape()[0] & 0xFF);
        assertEquals(127, next.shape()[1] & 0xFF);
        var mirrored = new byte[16];
        mirrored[1] = (byte) 255;
        var other = new float[16];
        other[0] = 1f;
        var swapped = HistogramQuantizer.add(1f, mirrored, 1f, other);
        assertEquals(128, swapped.shape()[0] & 0xFF, "the tie follows the index, not the stored side");
        assertEquals(127, swapped.shape()[1] & 0xFF);
    }
}
