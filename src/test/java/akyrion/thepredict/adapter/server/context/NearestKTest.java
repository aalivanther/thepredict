package akyrion.thepredict.adapter.server.context;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NearestKTest {
    @Test
    void retainsOnlyTheNearestCandidatesInStableDistanceOrder() {
        var candidates = IntStream.range(0, 1000).boxed().toList();
        var distanceReads = new AtomicInteger();

        assertEquals(List.of(499, 501, 498, 502), NearestK.select(candidates, 4,
                candidate -> candidate != 500,
                candidate -> {
                    distanceReads.incrementAndGet();
                    return (candidate - 500) * (candidate - 500);
                }));
        assertEquals(999, distanceReads.get());
    }

    @Test
    void ignoresInvalidDistancesAndHandlesSmallerSamples() {
        var candidates = List.of(-1.0, Double.NaN, 4.0, 1.0, Double.POSITIVE_INFINITY);

        assertEquals(List.of(1.0, 4.0), NearestK.select(candidates, 16,
                candidate -> true, candidate -> candidate));
        assertThrows(IllegalArgumentException.class, () -> NearestK.select(candidates, 0,
                candidate -> true, candidate -> candidate));
    }
}
