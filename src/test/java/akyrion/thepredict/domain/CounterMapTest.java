package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CounterMapTest {
    @Test
    void everyBiasingRowLiftsByOneTenthAndOnlyNoShieldAndCadenceStayFlat() {
        assertEquals(1, CounterMap.VERSION);
        var flat = Set.of(EnemyPattern.NO_SHIELD, EnemyPattern.FIXED_CADENCE);
        for (var pattern : EnemyPattern.values()) {
            var bias = CounterMap.of(pattern);
            assertEquals(flat.contains(pattern) ? 0 : 100, bias.utilityMillis(), pattern.name());
            assertEquals(flat.contains(pattern), bias.preference() == 0L, pattern.name() + " biases a choice");
        }
        assertEquals(20, CounterMap.of(EnemyPattern.PEARLS_WHEN_LOW).delayTicks());
        assertTrue(CounterMap.of(EnemyPattern.PEARLS_WHEN_LOW).reserve());
        assertEquals(ReactionBand.OPPORTUNITY, Reaction.EXPLOIT_PATTERN.band());
        assertEquals(Reaction.values()[Reaction.values().length - 1], Reaction.EXPLOIT_PATTERN);
    }

    @Test
    void eachFailedAnswerRowAsksForTheChoiceThatDeclaresThatAnswer() {
        var failed = new EnemyPattern[] {EnemyPattern.FAILS_ANSWER_BLOCK, EnemyPattern.FAILS_ANSWER_DODGE,
                EnemyPattern.FAILS_ANSWER_BACK_OFF, EnemyPattern.FAILS_ANSWER_BREAK_SIGHT,
                EnemyPattern.FAILS_ANSWER_INTERRUPT};
        assertEquals(TellAnswer.values().length, failed.length);
        for (var answer : TellAnswer.values()) {
            assertEquals(CastPreference.answer(answer), CounterMap.of(failed[answer.ordinal()]).preference(),
                    answer.name());
        }
        assertEquals(CastPreference.answer(TellAnswer.BLOCK), CounterMap.of(EnemyPattern.SHIELD_LATE).preference());
    }

    @Test
    void aRowCannotLeaveTheFactorOrTheDelayOfA14() {
        assertThrows(IllegalArgumentException.class,
                () -> new CounterMap.Bias(0L, MicroBehaviour.NATIVE, 201, false, Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CounterMap.Bias(0L, MicroBehaviour.NATIVE, -1, false, Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CounterMap.Bias(0L, MicroBehaviour.NATIVE, 0, false, Optional.empty(), 21));
        assertThrows(IllegalArgumentException.class,
                () -> new CounterMap.Bias(0L, MicroBehaviour.NATIVE, 0, false, Optional.empty(), -1));
        assertEquals(1.2, new CounterMap.Bias(0L, MicroBehaviour.NATIVE, 200, false, Optional.empty(), 20).factor(),
                1e-12);
    }
}
