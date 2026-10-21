package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The close of one fight rejects what the clocks never produce, so a NaN cannot poison a rung prior. */
class FightSummaryTest {
    @Test
    void theFormsOfTheClocksAreAccepted() {
        var plain = new FightSummary(FightEventKind.TARGET_DIED, 0, 0.0, 3);
        assertEquals(-1, plain.reactionP50Ticks());
        assertEquals(0, plain.targetDamageSharePermille());
        var full = new FightSummary(FightEventKind.TARGET_DIED, 600, StrictMath.log(600), 8, 7, 1000, 1000, 40f);
        assertEquals(1000, full.targetDamageSharePermille());
    }

    @Test
    void aNonFiniteLogTimeOrANegativeDurationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new FightSummary(FightEventKind.TARGET_DIED, 10, Double.NaN, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new FightSummary(FightEventKind.TARGET_DIED, 10, Double.NEGATIVE_INFINITY, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new FightSummary(FightEventKind.TARGET_DIED, -1, 0.0, 3));
    }

    @Test
    void theDamageShareAndTheMaxHealthStayInTheirRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> new FightSummary(FightEventKind.TARGET_DIED, 10, 1.0, 3, -1, -1, 1001, 20f));
        assertThrows(IllegalArgumentException.class,
                () -> new FightSummary(FightEventKind.TARGET_DIED, 10, 1.0, 3, -1, -1, -1, 20f));
        assertThrows(IllegalArgumentException.class,
                () -> new FightSummary(FightEventKind.TARGET_DIED, 10, 1.0, 3, -1, -1, 10, Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new FightSummary(FightEventKind.TARGET_DIED, 10, 1.0, 3, -1, -1, 10, -1f));
        assertThrows(NullPointerException.class, () -> new FightSummary(null, 10, 1.0, 3));
    }
}
