package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A guarded backstep beside a harmful near push becomes a sidestep (M24.5). */
class MicroPolicyZoneTest {
    @Test
    void aGuardedBackstepBesideHarmOrLethalBecomesASidestepAndUnknownDoesNot() {
        assertEquals(MicroBehaviour.SIDESTEP, MicroPolicy.awayFromEdge(MicroBehaviour.BACKSTEP, true,
                PushDanger.HARM.ordinal()));
        assertEquals(MicroBehaviour.SIDESTEP, MicroPolicy.awayFromEdge(MicroBehaviour.BACKSTEP, true,
                PushDanger.LETHAL.ordinal()));
        assertEquals(MicroBehaviour.BACKSTEP, MicroPolicy.awayFromEdge(MicroBehaviour.BACKSTEP, true,
                PushDanger.UNKNOWN.ordinal()));
        assertEquals(MicroBehaviour.BACKSTEP, MicroPolicy.awayFromEdge(MicroBehaviour.BACKSTEP, false,
                PushDanger.LETHAL.ordinal()));
        assertEquals(MicroBehaviour.ORBIT, MicroPolicy.awayFromEdge(MicroBehaviour.ORBIT, true,
                PushDanger.LETHAL.ordinal()));
    }
}
