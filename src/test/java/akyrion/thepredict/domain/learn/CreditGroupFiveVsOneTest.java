package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.LearningSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreditGroupFiveVsOneTest {
    @Test
    void fiveAgainstOneMixesAndAMissingClockIsHalf() {
        var speed = 0.8;
        var share = LearningSettings.DEFAULT.groupCreditShare();
        var dead = CreditMath.individual(true, true, speed);
        var won = CreditMath.group(false, true, speed);
        var sacrifice = CreditMath.mix(dead, won, true, share);
        assertEquals(-0.5 + 0.5 * speed, sacrifice, 1.0e-12);
        assertEquals(-0.75 + 0.25 * speed, CreditMath.mix(dead, won, true, 0.25), 1.0e-12);
        var survivor = CreditMath.individual(false, true, speed);
        assertEquals(speed, CreditMath.mix(survivor, won, true, share), 1.0e-12);
        assertEquals(-1.0, CreditMath.mix(dead, won, false, share), 0.0);
        assertEquals(0.5, CreditMath.speed(0, 200), 0.0);
        assertEquals(1.0, CreditMath.deferred(1.0, 5, 1.0), 0.0);
        assertEquals(0.32768, CreditMath.deferred(1.0, 5), 1.0e-12);
        assertEquals(1.0, CreditMath.reward(1.0, 0.0, 0.0, 0.0), 0.0);
        var invalid = new CreditSnapshot(true, false, 0, 0, false, false, false, 0, share);
        assertEquals(-1.0, CreditMath.terminal(invalid), 0.0);
        var grouped = new CreditSnapshot(true, false, 80, 100, true, false, true, 80, share);
        assertEquals(-0.5 + 0.5 * CreditMath.speed(100, 80), CreditMath.terminal(grouped), 1.0e-12);
    }
}
