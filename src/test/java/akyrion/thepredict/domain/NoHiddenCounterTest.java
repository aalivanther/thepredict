package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoHiddenCounterTest {
    @Test
    void noCounterShortensAWindUpOrDropsBelowOne() {
        for (var pattern : EnemyPattern.values()) {
            var bias = CounterMap.of(pattern);
            assertTrue(bias.delayTicks() >= 0 && bias.delayTicks() <= 20, pattern.name());
            assertTrue(bias.factor() >= 1.0 && bias.factor() <= 1.2, pattern.name());
        }
        assertEquals(20, Reaction.EXPLOIT_PATTERN.cooldownTicks());
        for (var reason : PruneReason.values()) {
            assertFalse(reason.name().contains("PATTERN"), reason.name());
        }
        assertThrows(IllegalArgumentException.class, () -> new CounterMap.Bias(0L, MicroBehaviour.NATIVE, 0, false,
                java.util.Optional.empty(), 21));
        assertThrows(IllegalArgumentException.class, () -> new CounterMap.Bias(0L, MicroBehaviour.NATIVE, 201, false,
                java.util.Optional.empty(), 0));
        assertEquals(500, UtilityScore.withPattern(500, 1.0));
        assertEquals(600, UtilityScore.withPattern(500, 1.2));
        assertEquals(600, UtilityScore.withPattern(500, 1.4));
        assertFalse(ExploitPolicy.liftsQuota(true, false));
        assertFalse(ExploitPolicy.liftsRest(true, false));
        assertFalse(ExploitPolicy.liftsVariety(false, true));
        assertFalse(ExploitPolicy.liftsExploitShare(true, false));
        assertTrue(ExploitPolicy.liftsQuota(true, true));
        assertTrue(ExploitPolicy.liftsRest(true, true));
        assertTrue(ExploitPolicy.liftsVariety(true, true));
        assertTrue(ExploitPolicy.liftsExploitShare(true, true));
        assertEquals(0, ExploitPolicy.restTicks(300, true, true));
        assertEquals(300, ExploitPolicy.restTicks(300, true, false));
        assertEquals(EnemyPattern.values().length, ExploitPolicy.variety(1, true, true));
        assertEquals(EnemyPattern.values().length, ExploitPolicy.variety(3, true, true));
        assertEquals(1, ExploitPolicy.variety(1, true, false));
        assertEquals(0.4, ExploitPolicy.share(0.5, 0.4, false, false));
        assertEquals(1.0, ExploitPolicy.share(0.5, 0.4, false, true));
        assertEquals(TokenEnd.YIELDED, ReadyCastOrder.yieldReservation(ReservationHold.MAX_TICKS, 100).orElseThrow());
        assertFalse(ReadyCastOrder.yieldReservation(ReservationHold.MAX_TICKS - 1, 100).isPresent());
        assertEquals(java.util.Optional.empty(), ReadyCastOrder.yieldReservation(10, 100).filter(
                end -> end == TokenEnd.RELEASED));
    }
}
