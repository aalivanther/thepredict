package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankPolicyTest {
    @Test
    void aFreeEntityHasNoQuotaAndAPlayerInTheSameFightDoes() {
        assertEquals(BenchPolicy.FREE, PatternProfile.DEFAULT.againstEntities());
        assertEquals(1.0, ExploitPolicy.share(0.5, 0.5, true, false));
        assertEquals(0.5, ExploitPolicy.share(0.5, 0.5, false, false));
        assertEquals(PatternMode.OBSERVE, PatternMode.DEFAULT);
    }
}
