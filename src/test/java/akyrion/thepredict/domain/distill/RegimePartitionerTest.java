package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegimePartitionerTest {
    @Test
    void aSplitNeedsBothSidesTheGainAndAnIntervalThatExcludesZero() {
        assertTrue(RegimePartitioner.accept(2000, 2000, 0.03, 0.01, 0.04, 0.02, 2000));
        assertFalse(RegimePartitioner.accept(1999, 2000, 0.03, 0.01, 0.04, 0.02, 2000));
        assertFalse(RegimePartitioner.accept(2000, 2000, 0.01, 0.01, 0.04, 0.02, 2000));
        assertFalse(RegimePartitioner.accept(2000, 2000, 0.03, -0.01, 0.04, 0.02, 2000));
    }

    @Test
    void anIntervalBelowZeroMeansTheSplitHurtsAndIsRefused() {
        assertFalse(RegimePartitioner.accept(2000, 2000, 0.03, -0.04, -0.01, 0.02, 2000));
    }
}
