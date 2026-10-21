package akyrion.thepredict.domain;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionPredictorTest {
    @Test
    void aRepeatingWtapCycleBeatsChanceAndMatchesItself() {
        var rng = new WtapperProbe.PyRandom(1007);
        assertEquals(0.8559393885152179, rng.nextDouble(), 1.0E-12);
        assertEquals(5, rng.below(16));
        var first = WtapperProbe.accuracy();
        var second = WtapperProbe.accuracy();
        assertEquals(first, second, 1.0E-12);
        assertTrue(Math.abs(first - 0.462) <= 0.01, "w_tapper " + first);
    }

    @Test
    void orderTwoEvictsPastThirtyTwoContextsAndBacksOffBelowTwo() {
        var predictor = new ActionPredictor(32);
        var sequence = distinctPairs();
        for (var action : sequence) {
            var predicted = predictor.predict();
            predictor.update(action, predicted, 1.0);
        }
        assertTrue(predictor.evictions() > 0);
        var fresh = new ActionPredictor(4);
        assertEquals(EnemyAction.IDLE.ordinal(), fresh.predict());
        fresh.update(EnemyAction.STRIKE.ordinal(), fresh.predict(), 1.0);
        fresh.update(EnemyAction.STRIKE.ordinal(), fresh.predict(), 1.0);
        assertEquals(EnemyAction.IDLE.ordinal(), fresh.predict());
        fresh.update(EnemyAction.STRIKE.ordinal(), fresh.predict(), 1.0);
        assertEquals(EnemyAction.STRIKE.ordinal(), fresh.predict());
    }

    @Test
    void forgettingLetsANewActionTakeOver() {
        var predictor = new ActionPredictor(8);
        for (var step = 0; step < 6; step++) {
            predictor.update(EnemyAction.STRIKE.ordinal(), predictor.predict(), 1.0);
        }
        for (var step = 0; step < 12; step++) {
            predictor.update(EnemyAction.STEP_OUT.ordinal(), predictor.predict(), 0.9);
        }
        assertEquals(EnemyAction.STEP_OUT.ordinal(), predictor.predict());
    }

    @Test
    void predictAndUpdateAllocateNothingAfterWarmup() {
        var predictor = new ActionPredictor(8);
        for (var step = 0; step < 30; step++) {
            predictor.update(step % EnemyAction.COUNT, predictor.predict(), 1.0);
        }
        var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) {
            return;
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        var thread = Thread.currentThread().threadId();
        var before = bean.getThreadAllocatedBytes(thread);
        for (var step = 0; step < 200; step++) {
            predictor.update(step % EnemyAction.COUNT, predictor.predict(), 1.0);
        }
        var after = bean.getThreadAllocatedBytes(thread);
        assertEquals(before, after);
    }

    private static int[] distinctPairs() {
        var sequence = new int[37];
        sequence[0] = 0;
        var index = 1;
        for (var hub = 1; hub <= 12; hub++) {
            sequence[index++] = hub;
            sequence[index++] = hub;
            sequence[index++] = 0;
        }
        return sequence;
    }
}
