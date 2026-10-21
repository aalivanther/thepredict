package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SPIBB uses overrideZ. A tree never overrides. Errata E29-5 applied: the label z stays 1.645. */
class ValueOverrideRuleTest {
    @Test
    void aTreeAndAThinEstimateStayOnTheLocalOrder() {
        assertEquals(DistillSource.LOCAL_ORDER, ValueOverrideRule.source(3, 1.0, 0.0, 0.01, 0.01, 2.0, true));
        assertEquals(DistillSource.LOCAL_ORDER, ValueOverrideRule.source(2, 1.0, 0.0, 0.01, 0.01, 2.0, false));
        assertEquals(DistillSource.VALUE_OVERRIDE, ValueOverrideRule.source(3, 1.0, 0.0, 0.01, 0.01, 2.0, false));
        assertEquals(1.645, LabelRule.Z);
    }
}
