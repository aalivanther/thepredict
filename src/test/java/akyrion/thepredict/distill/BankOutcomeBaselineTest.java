package akyrion.thepredict.distill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The bank baseline accepts outcome labels and refuses Jev. */
class BankOutcomeBaselineTest {
    @Test
    void outcomeIsALabelAndJevIsNot() {
        assertEquals("press", BankOutcomeBaseline.label(new BankOutcomeBaseline.Row("outcome", "press")));
        assertEquals("press", BankOutcomeBaseline.label(new BankOutcomeBaseline.Row("rollout", "press")));
        assertEquals("press", BankOutcomeBaseline.label(new BankOutcomeBaseline.Row("kev-0.8b", "press")));
        assertThrows(IllegalArgumentException.class,
                () -> BankOutcomeBaseline.label(new BankOutcomeBaseline.Row("jev", "press")));
        assertThrows(IllegalArgumentException.class,
                () -> BankOutcomeBaseline.label(new BankOutcomeBaseline.Row("JEV_ARGMAX", "press")));
    }
}
