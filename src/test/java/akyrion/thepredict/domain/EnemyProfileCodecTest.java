package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyProfileCodecTest {
    @Test
    void emptyAndFilledSummariesRoundTrip() {
        var empty = EnemyProfileSummary.empty();
        assertArrayEquals(empty.actions(), EnemyProfileCodec.decode(EnemyProfileCodec.encode(empty)).actions());
        var json = EnemyProfileCodec.toJson(empty);
        assertTrue(json.contains("reaction_mean"));
        assertArrayEquals(empty.actions(), EnemyProfileCodec.fromJson(json).actions());
        var filled = sample();
        var back = EnemyProfileCodec.fromJson(EnemyProfileCodec.toJson(filled));
        assertArrayEquals(filled.actions(), back.actions());
        assertArrayEquals(filled.success(), back.success());
        assertEquals(filled.transitionTotal(), back.transitionTotal());
        assertEquals(filled.reactionWindows(), back.reactionWindows());
        assertThrows(IllegalArgumentException.class, () -> EnemyProfileCodec.decode(new byte[] {2}));
    }

    @Test
    void everyStrictPrefixOfAnEncodingIsRejectedAsTruncated() {
        var bytes = EnemyProfileCodec.encode(sample());
        var decoded = EnemyProfileCodec.decode(bytes);
        assertEquals(sample().reactionMean(), decoded.reactionMean());
        assertEquals(sample().reactionM2(), decoded.reactionM2());
        for (var length = 0; length < bytes.length; length++) {
            var prefix = Arrays.copyOf(bytes, length);
            assertThrows(IllegalArgumentException.class, () -> EnemyProfileCodec.decode(prefix), "length " + length);
        }
    }

    @Test
    void aHundredModestSummariesStayUnderFourHundredBytes() {
        var lengths = new int[100];
        for (var index = 0; index < lengths.length; index++) {
            lengths[index] = EnemyProfileCodec.encode(modest(index)).length;
        }
        Arrays.sort(lengths);
        assertTrue(lengths[98] <= 400, "p99 " + lengths[98]);
    }

    private static EnemyProfileSummary sample() {
        var actions = new int[EnemyProfileSummary.ACTIONS];
        actions[1] = 3000;
        var success = new int[EnemyProfileSummary.BETA];
        success[0] = 9;
        var keys = new int[] {4, 8};
        var est = new int[] {3, 1};
        return new EnemyProfileSummary(1, actions, new int[EnemyProfileSummary.EPISODES], success,
                new int[EnemyProfileSummary.BETA], new int[EnemyProfileSummary.BETA], 4.5f, 1.25f, 2, keys, est, 12,
                3, new int[EnemyProfileSummary.BINS], 4, 2);
    }

    private static EnemyProfileSummary modest(int index) {
        var actions = new int[EnemyProfileSummary.ACTIONS];
        actions[index % actions.length] = 1 + index % 7;
        return new EnemyProfileSummary(1, actions, new int[EnemyProfileSummary.EPISODES],
                new int[EnemyProfileSummary.BETA], new int[EnemyProfileSummary.BETA],
                new int[EnemyProfileSummary.BETA], 1f, 0f, index % 3, new int[] {index}, new int[] {1}, 1,
                index % 4, new int[EnemyProfileSummary.BINS], 0, 0);
    }
}
