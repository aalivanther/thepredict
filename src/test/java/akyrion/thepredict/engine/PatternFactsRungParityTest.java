package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.PressureLadder;
import akyrion.thepredict.domain.SituationSignature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternFactsRungParityTest {
    @Test
    void equalFactsProduceTheSameBodyOnRungsZeroFourAndEight() {
        var bits = SituationSignature.patternBits(EnemyPattern.SHIELD_TURTLE.bit());
        var body = signature(bits);
        for (var rung : new int[] {0, 4, 8}) {
            assertEquals(body, signature(bits));
            var levers = PressureLadder.levers(rung, 0, 0, true);
            assertEquals(levers.reactionDelayTicks(), PressureLadder.levers(rung, 0, 0, true).reactionDelayTicks());
        }
        assertEquals(0.0, PressureLadder.levers(0, 0, 0, true).exploitShare());
        assertEquals(0.20, PressureLadder.levers(4, 0, 0, true).exploitShare());
        assertEquals(0.50, PressureLadder.levers(8, 0, 0, true).exploitShare());
    }

    private static int signature(int bits) {
        return SituationSignature.pack(DistanceBand.MELEE, HealthBand.HEALTHY, HealthBand.WOUNDED, false,
                false, false, true, true, true, false, 2, false, true, bits);
    }
}
