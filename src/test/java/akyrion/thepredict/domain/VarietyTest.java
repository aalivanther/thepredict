package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Variety of the biased patterns (M26.6): the best ones, and a held one only within 0.05 of what it displaces. */
class VarietyTest {
    @Test
    void fourActivePatternsKeepTwoAndHysteresisHoldsANearTie() {
        var confidence = new double[EnemyPattern.values().length];
        confidence[0] = 0.90;
        confidence[1] = 0.80;
        confidence[2] = 0.70;
        confidence[3] = 0.60;
        var active = (1 << 0) | (1 << 1) | (1 << 2) | (1 << 3);
        assertEquals((1 << 0) | (1 << 1), VarietyPicker.select(active, confidence, 0, 2));
        confidence[2] = 0.79;
        var held = 1 << 2;
        assertEquals((1 << 0) | (1 << 2), VarietyPicker.select(active, confidence, held, 2));
    }

    @Test
    void aSecondHeldPatternDoesNotDisplaceAFreePatternFarAboveIt() {
        var confidence = new double[EnemyPattern.values().length];
        confidence[0] = 0.90;
        confidence[1] = 0.85;
        confidence[2] = 0.82;
        confidence[3] = 0.80;
        var active = (1 << 0) | (1 << 1) | (1 << 2) | (1 << 3);
        var held = (1 << 2) | (1 << 3);
        assertEquals((1 << 0) | (1 << 2), VarietyPicker.select(active, confidence, held, 2));
    }

    @Test
    void fixedCadenceTiesAndLimitsBehave() {
        var confidence = new double[EnemyPattern.values().length];
        var cadence = EnemyPattern.FIXED_CADENCE.ordinal();
        confidence[cadence] = 1.0;
        confidence[4] = 0.5;
        confidence[5] = 0.5;
        var active = (1 << cadence) | (1 << 4) | (1 << 5);
        assertEquals(1 << 4, VarietyPicker.select(active, confidence, 0, 1));
        assertEquals((1 << 4) | (1 << 5), VarietyPicker.select(active, confidence, 0, 3));
        assertEquals(0, VarietyPicker.select(active, confidence, 1 << 5, 0));
        assertEquals(1 << 5, VarietyPicker.select(active, confidence, 1 << 5, 1));
    }
}
