package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisraGriesMergeTest {
    @Test
    void aThousandZipfMergesRespectTheBoundAndTheExactTotal() {
        var summaries = new EnemyProfileSummary[1000];
        var truth = new HashMap<Integer, Integer>();
        var total = 0;
        for (var index = 0; index < summaries.length; index++) {
            var key = key(index);
            truth.merge(key, 1, Integer::sum);
            total++;
            summaries[index] = one(key);
        }
        var sequential = EnemyProfileSummary.empty();
        for (var summary : summaries) {
            sequential = EnemyProfileMath.merge(sequential, summary);
        }
        var tree = mergeTree(summaries, 0, summaries.length);
        assertEquals(total, sequential.transitionTotal());
        assertEquals(total, tree.transitionTotal());
        assertEquals(1000, sequential.actions()[1]);
        assertEquals(sequential.actions()[1], tree.actions()[1]);
        bound(sequential, truth, total);
        bound(tree, truth, total);
    }

    private static void bound(EnemyProfileSummary summary, HashMap<Integer, Integer> truth, int total) {
        var slack = total / 17.0;
        for (var entry : truth.entrySet()) {
            var estimate = EnemyProfileMath.estimate(summary, entry.getKey());
            assertTrue(estimate <= entry.getValue());
            assertTrue(estimate + 1e-9 >= entry.getValue() - slack);
        }
    }

    private static EnemyProfileSummary mergeTree(EnemyProfileSummary[] summaries, int from, int to) {
        if (to - from == 1) {
            return summaries[from];
        }
        var mid = (from + to) / 2;
        return EnemyProfileMath.merge(mergeTree(summaries, from, mid), mergeTree(summaries, mid, to));
    }

    private static EnemyProfileSummary one(int key) {
        var actions = new int[EnemyProfileSummary.ACTIONS];
        actions[1] = 1;
        return new EnemyProfileSummary(1, actions, new int[EnemyProfileSummary.EPISODES],
                new int[EnemyProfileSummary.BETA], new int[EnemyProfileSummary.BETA],
                new int[EnemyProfileSummary.BETA], 0f, 0f, 0, new int[] {key}, new int[] {1}, 1, 0,
                new int[EnemyProfileSummary.BINS], 0, 0);
    }

    private static int key(int index) {
        var word = StableRandom.mix(26L ^ index);
        var rank = (int) ((word >>> 11) & 31);
        return rank < 8 ? rank : 8 + (int) ((word >>> 17) & 63);
    }
}
