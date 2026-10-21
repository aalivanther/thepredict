package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A held intent expires at or after its adoption; the producers add a non-negative hold to the tick. */
class HeldIntentTest {
    @Test
    void anIntentThatExpiresAtItsAdoptionTickIsValid() {
        var held = new HeldIntent(new TacticId("press_attack"), BehaviorPolicy.VOI_SKIP, 4, 10L, 10L);
        assertEquals(10L, held.expiresTick());
    }

    @Test
    void anIntentThatExpiresBeforeItsAdoptionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new HeldIntent(new TacticId("press_attack"), BehaviorPolicy.VOI_SKIP, 4, 10L, 9L));
        assertThrows(NullPointerException.class, () -> new HeldIntent(null, BehaviorPolicy.VOI_SKIP, 4, 0L, 1L));
    }
}
