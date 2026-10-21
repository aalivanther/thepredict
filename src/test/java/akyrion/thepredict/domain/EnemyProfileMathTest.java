package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnemyProfileMathTest {
    @Test
    void countsAddExactlyAndChanMatchesThePair() {
        var one = summary(3, 10f, 0f, 2);
        var two = summary(5, 20f, 0f, 2);
        var merged = EnemyProfileMath.merge(one, two);
        assertEquals(8, merged.actions()[0]);
        assertEquals(4, merged.reactionWindows());
        assertEquals(15f, merged.reactionMean(), 1e-5);
        var again = EnemyProfileMath.merge(two, one);
        assertEquals(merged.actions()[0], again.actions()[0]);
        var fold = EnemyProfileSummary.empty();
        for (var index = 0; index < 1000; index++) {
            fold = EnemyProfileMath.merge(fold, summary(3, 0f, 0f, 0));
        }
        assertEquals(3000, fold.actions()[0]);
        var left = fold;
        var right = EnemyProfileMath.merge(summary(3000, 0f, 0f, 0), summary(3000, 0f, 0f, 0));
        assertEquals(6000, right.actions()[0]);
        assertEquals(3000, left.actions()[0]);
    }

    @Test
    void aUnitWeightMatchesTheMergeAndAnUnknownVersionIsRejected() {
        var one = summary(4, 8f, 1f, 1);
        var two = summary(6, 2f, 1f, 1);
        var merged = EnemyProfileMath.merge(one, two);
        var accumulator = new EnemyProfileAccumulator();
        EnemyProfileMath.accumulate(accumulator, one, 1f);
        EnemyProfileMath.accumulate(accumulator, two, 1f);
        assertEquals(merged.actions()[0], accumulator.action(0), 1e-4);
        assertEquals(merged.reactionWindows(), accumulator.reactionWindows, 1e-6);
        assertEquals(merged.reactionMean(), accumulator.reactionMean(), 1e-5);
        assertEquals(merged.reactionM2(), accumulator.reactionM2, 1e-4);
        assertThrows(IllegalArgumentException.class, () -> summaryVersion(9));
    }

    @Test
    void aWeightedAddKeepsTheSpreadOfTwoDifferentMeans() {
        var one = summary(1, 8f, 1f, 1);
        var two = summary(1, 2f, 1f, 1);
        var accumulator = new EnemyProfileAccumulator();
        EnemyProfileMath.accumulate(accumulator, one, 1f);
        EnemyProfileMath.accumulate(accumulator, two, 0.5f);
        var n = 1.0 + 0.5;
        var delta = 2.0 - 8.0;
        assertEquals(8.0 + delta * 0.5 / n, accumulator.reactionMean(), 1e-5);
        assertEquals(1.0 + 0.5 * 1.0 + delta * delta * 1.0 * 0.5 / n, accumulator.reactionM2, 1e-4);
        assertEquals(n, accumulator.reactionWindows, 1e-6);
    }

    private static EnemyProfileSummary summary(int strikes, float mean, float m2, int windows) {
        var actions = new int[EnemyProfileSummary.ACTIONS];
        actions[0] = strikes;
        return new EnemyProfileSummary(1, actions, new int[EnemyProfileSummary.EPISODES],
                new int[EnemyProfileSummary.BETA], new int[EnemyProfileSummary.BETA],
                new int[EnemyProfileSummary.BETA], mean, m2, windows, new int[0], new int[0], 0, 0,
                new int[EnemyProfileSummary.BINS], 0, 0);
    }

    private static EnemyProfileSummary summaryVersion(int version) {
        return new EnemyProfileSummary(version, new int[EnemyProfileSummary.ACTIONS],
                new int[EnemyProfileSummary.EPISODES], new int[EnemyProfileSummary.BETA],
                new int[EnemyProfileSummary.BETA], new int[EnemyProfileSummary.BETA], 0f, 0f, 0, new int[0],
                new int[0], 0, 0, new int[EnemyProfileSummary.BINS], 0, 0);
    }
}
