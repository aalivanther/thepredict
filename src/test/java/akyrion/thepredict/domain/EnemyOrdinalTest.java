package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnemyOrdinalTest {
    @Test
    void actionAndPatternOrdinalsStayFixed() {
        assertEquals(0, EnemyAction.IDLE.ordinal());
        assertEquals(1, EnemyAction.STRIKE.ordinal());
        assertEquals(15, EnemyAction.CONSUME.ordinal());
        assertEquals(16, EnemyAction.COUNT);
        assertEquals(0, EnemyPattern.SPAMS_CLICKS.ordinal());
        assertEquals(24, EnemyPattern.FIXED_CADENCE.ordinal());
        assertEquals(25, EnemyPattern.values().length);
        assertEquals(10, EnemyEpisode.values().length);
        assertEquals(0, EnemyEpisode.HEAL_CONSUMED.ordinal());
    }
}
