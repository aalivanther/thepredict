package akyrion.thepredict.domain;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyTokenizerTest {
    private static Optional<EnemyAction> sink = Optional.empty();

    @Test
    void aStrafeMergesAtSixTicksAndNotAtSeven() {
        var tokenizer = new EnemyTokenizer(6, 20, 20);
        assertTrue(tokenizer.offer(EnemyAction.STRAFE_LEFT, 0, true).isPresent());
        assertTrue(tokenizer.offer(EnemyAction.STRAFE_LEFT, 6, true).isEmpty());
        assertTrue(tokenizer.offer(EnemyAction.STRAFE_LEFT, 13, true).isPresent());
    }

    @Test
    void idleNeedsAWitnessAndTwentyTicks() {
        var tokenizer = new EnemyTokenizer(6, 20, 20);
        tokenizer.offer(EnemyAction.STRIKE, 0, true);
        assertTrue(tokenizer.offer(EnemyAction.IDLE, 19, true).isEmpty());
        assertTrue(tokenizer.offer(EnemyAction.IDLE, 20, false).isEmpty());
        assertEquals(EnemyAction.IDLE, tokenizer.offer(EnemyAction.IDLE, 20, true).orElseThrow());
    }

    @Test
    void aFullBucketKeepsTheStrikeAndReleasesTheStrafe() {
        var tokenizer = new EnemyTokenizer(0, 20, 20);
        for (var tick = 0; tick < 20; tick++) {
            assertEquals(EnemyAction.STRAFE_LEFT, tokenizer.offer(EnemyAction.STRAFE_LEFT, tick, true).orElseThrow());
        }
        assertTrue(tokenizer.released().isEmpty());
        assertEquals(EnemyAction.STRIKE, tokenizer.offer(EnemyAction.STRIKE, 19, true).orElseThrow());
        assertEquals(EnemyAction.STRAFE_LEFT, tokenizer.released().orElseThrow());
        assertEquals(1, tokenizer.dropped());
        assertTrue(tokenizer.offer(EnemyAction.STEP_OUT, 19, true).isEmpty());
        assertEquals(2, tokenizer.dropped());
    }

    @Test
    void thirtyStrikesInOneSecondKeepTwenty() {
        var tokenizer = new EnemyTokenizer(6, 20, 20);
        var kept = 0;
        for (var index = 0; index < 30; index++) {
            if (tokenizer.offer(EnemyAction.STRIKE, index / 2, true).isPresent()) {
                kept++;
            }
        }
        assertEquals(20, kept);
        assertEquals(10, tokenizer.dropped());
    }

    @Test
    void theSameTickRanksStrikesBeforeMovement() {
        assertTrue(EnemyAction.STRIKE.sameTickRank() < EnemyAction.SHIELD_UP.sameTickRank());
        assertTrue(EnemyAction.SHIELD_UP.sameTickRank() < EnemyAction.STRAFE_LEFT.sameTickRank());
        assertTrue(EnemyAction.STRAFE_LEFT.sameTickRank() < EnemyAction.JUMP.sameTickRank());
        assertTrue(EnemyAction.JUMP.sameTickRank() < EnemyAction.SPRINT_RESET.sameTickRank());
        assertTrue(EnemyAction.SPRINT_RESET.droppedBeforeStrikes());
        assertTrue(!EnemyAction.STRIKE.droppedBeforeStrikes());
    }

    @Test
    void theSameTokenIsTheSameSharedValue() {
        var first = new EnemyTokenizer(0, 20, 20).offer(EnemyAction.STRIKE, 0, true);
        var second = new EnemyTokenizer(0, 20, 20).offer(EnemyAction.STRIKE, 0, true);
        assertSame(first, second);
    }

    @Test
    void offeringAndReadingTokensAllocateNothingAfterWarmup() {
        var tokenizer = new EnemyTokenizer(0, 20, 4);
        for (var tick = 0; tick < 400; tick++) {
            step(tokenizer, tick);
        }
        var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) {
            return;
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        var thread = Thread.currentThread().threadId();
        var before = bean.getThreadAllocatedBytes(thread);
        for (var tick = 400; tick < 800; tick++) {
            step(tokenizer, tick);
        }
        var after = bean.getThreadAllocatedBytes(thread);
        assertEquals(before, after);
        assertTrue(tokenizer.dropped() > 0, "the bucket filled, so the movement scan and the release ran");
    }

    private static void step(EnemyTokenizer tokenizer, int tick) {
        sink = tokenizer.offer(EnemyAction.STRAFE_LEFT, tick, true);
        sink = tokenizer.offer(EnemyAction.STEP_IN, tick, true);
        sink = tokenizer.offer(EnemyAction.STRIKE, tick, true);
        sink = tokenizer.offer(EnemyAction.CRIT_STRIKE, tick, true);
        sink = tokenizer.released();
    }
}
