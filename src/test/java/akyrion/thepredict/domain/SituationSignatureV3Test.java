package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Signature v3: the two pattern bits are 19 and 20 and nothing else moves (M26.7). */
class SituationSignatureV3Test {
    private static final int LOWER = (1 << 19) - 1;

    @Test
    void onlyAFilteredMaskChangesBitsNineteenAndTwenty() {
        assertEquals(0, SituationSignature.patternBits(0));
        var mask = EnemyPattern.SPAMS_CLICKS.bit() | EnemyPattern.FIXED_CADENCE.bit();
        assertEquals((EnemyPattern.SPAMS_CLICKS.ordinal() + 1) & 3, SituationSignature.patternBits(mask));
        var plain = pack(0);
        for (var bits = 0; bits < 4; bits++) {
            var withFacts = pack(bits);
            assertEquals(plain & LOWER, withFacts & LOWER, "bits " + bits);
            assertEquals(bits, (withFacts >>> 19) & 3);
            assertEquals(0, withFacts >>> 21);
        }
        assertEquals(pack(1), pack(5));
    }

    @Test
    void fixedCadenceAloneIsNotAFactAndNeverMovesTheBits() {
        assertEquals(0, SituationSignature.patternBits(EnemyPattern.FIXED_CADENCE.bit()));
        var jumper = EnemyPattern.CRIT_JUMPER.bit();
        assertEquals(SituationSignature.patternBits(jumper),
                SituationSignature.patternBits(jumper | EnemyPattern.FIXED_CADENCE.bit()));
        assertEquals((EnemyPattern.CRIT_JUMPER.ordinal() + EnemyPattern.NO_SHIELD.ordinal() + 1) & 3,
                SituationSignature.patternBits(jumper | EnemyPattern.NO_SHIELD.bit()));
    }

    private static int pack(int patternBits) {
        return SituationSignature.pack(DistanceBand.MELEE, HealthBand.HEALTHY, HealthBand.HEALTHY, false,
                false, false, false, true, true, false, 0, false, false, patternBits);
    }
}
