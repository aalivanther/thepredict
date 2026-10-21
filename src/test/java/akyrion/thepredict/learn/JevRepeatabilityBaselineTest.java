package akyrion.thepredict.learn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** X1: arm D repeats the same choice on 59 of 60 complete pairs. */
class JevRepeatabilityBaselineTest {
    @Test
    void armDRepeatsTheChosenOptionOnFiftyNineOfSixtyPairs() throws Exception {
        var figures = JevRepeatabilityBaseline.pairs("D");
        assertEquals(60, figures[0], "pairs");
        assertEquals(59, figures[1], "same best");
    }
}
