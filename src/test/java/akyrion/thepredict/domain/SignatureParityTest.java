package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Off, observe and bias, and a profile without pattern facts, keep the bytes of the v2 signature. Only full with facts
 * adds bits 19 and 20 (M26.7). The rule is {@link SituationSignature#patternBits(PatternMode, boolean, int)}.
 */
class SignatureParityTest {
    @Test
    void onlyFullWithFactsChangesTheSignatureOfThePreviousPack() {
        var previous = SituationSignature.pack(DistanceBand.MELEE, HealthBand.HEALTHY, HealthBand.HEALTHY, false,
                false, false, false, true, true, false, 1, false, false);
        var mask = EnemyPattern.SPAMS_CLICKS.bit();
        var changed = 0;
        for (var mode : PatternMode.values()) {
            for (var facts : new boolean[] {true, false}) {
                var packed = SituationSignature.pack(DistanceBand.MELEE, HealthBand.HEALTHY, HealthBand.HEALTHY,
                        false, false, false, false, true, true, false, 1, false, false,
                        SituationSignature.patternBits(mode, facts, mask));
                if (mode == PatternMode.FULL && facts) {
                    assertNotEquals(previous, packed);
                    assertEquals(SituationSignature.patternBits(mask), (packed >>> 19) & 3);
                    changed++;
                } else {
                    assertEquals(previous, packed, mode + " facts " + facts);
                }
            }
        }
        assertEquals(1, changed);
    }
}
