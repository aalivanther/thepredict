package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Y4 space and the 81×20 successive-halving budget. 3.67 rounds/s is the M22.3 target and is not measured.
 * The measured simulator rate cited by M22 is 761.17 rounds/s. {@code initialConfigs} stays 81.
 */
class AutoconfigSpaceTest {
    @Test
    void thePublishedSpaceAndThePairBudgetStay() {
        var boss = 2 * 4 * 3 * 3 * 4 * 3 * 3 * 2;
        assertEquals(5184, boss);
        assertEquals(62208, boss * 3 * 4);
        assertEquals(20736, boss * 2 * 2);
        assertEquals(81, AutoconfigSettings.DEFAULT.initialConfigs());
        assertEquals(3, AutoconfigSettings.DEFAULT.eta());
        assertEquals("off", AutoconfigSettings.DEFAULT.autoApply());
        assertEquals(6480, pairs(81, 20, 3));
    }

    private static int pairs(int configs, int radius, int eta) {
        var total = 0;
        while (configs > 1) {
            total += configs * radius;
            configs = Math.max(1, configs / eta);
            radius *= eta;
        }
        return total;
    }
}
