package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerCacheTest {
    @Test
    void aHashCollisionMissesAndLimitsDropTheOldestEntry() {
        var metrics = new DecisionMetrics(() -> 0L);
        var cache = new AnswerCache(AnswerCacheMode.ON, 100, 2, 0.0, 1L, 2, metrics);
        var left = "Aa";
        var right = "BB";
        assertEquals(left.hashCode(), right.hashCode());
        cache.remember("jev-1.0.0", left, Set.of(new QuestionKey("q0")), answered(3), 0L);
        assertInstanceOf(AnswerCache.Lookup.Miss.class, cache.lookup("jev-1.0.0", right, Set.of(new QuestionKey("q0")), 1L));
        cache.remember("jev-1.0.0", "one", Set.of(new QuestionKey("q0")), answered(3), 0L);
        cache.remember("jev-1.0.0", "two", Set.of(new QuestionKey("q0")), answered(3), 0L);
        cache.remember("jev-1.0.0", "three", Set.of(new QuestionKey("q0")), answered(3), 0L);
        assertInstanceOf(AnswerCache.Lookup.Miss.class, cache.lookup("jev-1.0.0", "one", Set.of(new QuestionKey("q0")), 1L));
        assertInstanceOf(AnswerCache.Lookup.Found.class, cache.lookup("jev-1.0.0", "three", Set.of(new QuestionKey("q0")), 1L));
        cache.remember("jev-latest", "body", Set.of(new QuestionKey("q0")), answered(3), 0L);
        assertInstanceOf(AnswerCache.Lookup.Miss.class, cache.lookup("jev-latest", "body", Set.of(new QuestionKey("q0")), 1L));
    }

    @Test
    void contextChangesAgeAndQuestionKeysEachHaveTheirOwnResult() {
        var metrics = new DecisionMetrics(() -> 0L);
        var cache = new AnswerCache(AnswerCacheMode.SHADOW, 100, 8, 0.0, 1L, 1, metrics);
        cache.noteContext(1L, "jev-1.0.0", "full");
        cache.remember("jev-1.0.0", "body", Set.of(new QuestionKey("q0")), answered(9), 0L);
        cache.noteContext(2L, "jev-1.0.0", "full");
        assertInstanceOf(AnswerCache.Lookup.Miss.class,
                cache.lookup("jev-1.0.0", "body", Set.of(new QuestionKey("q0")), 1L));
        cache.remember("jev-1.0.0", "body", Set.of(new QuestionKey("q0")), answered(9), 10L);
        assertInstanceOf(AnswerCache.Lookup.Found.class,
                cache.lookup("jev-1.0.0", "body", Set.of(new QuestionKey("q0")), 110L));
        assertInstanceOf(AnswerCache.Lookup.Expired.class,
                cache.lookup("jev-1.0.0", "body", Set.of(new QuestionKey("q0")), 211L));
        assertEquals(1, metrics.cache(DecisionMetrics.CacheCounter.EXPIRED));
        cache.remember("jev-1.0.0", "body", Set.of(new QuestionKey("q0")), answered(9), 300L);
        assertInstanceOf(AnswerCache.Lookup.Mismatch.class,
                cache.lookup("jev-1.0.0", "body", Set.of(new QuestionKey("q1")), 300L));
        assertEquals(0, answered(9).fromCache().inputTokens());
        assertEquals(9, AnswerCache.savedTokens(answered(9)));
        cache.noteShadow(new EncounterId(1), 1L, new TacticId("keep_distance"), 0.5, 101L);
        assertFalse(cache.compare(new EncounterId(1), 1L, new TacticId("keep_distance"), 0.5));
        assertEquals(0, cache.shadow().size(), "a compared shadow leaves the pending map");
        assertTrue(cache.shadow().put(new EncounterId(1), 1L, new TacticId("keep_distance"), 0.5, 1L));
        assertEquals(1, cache.shadow().size());
        assertFalse(cache.shadow().put(new EncounterId(2), 1L, new TacticId("keep_distance"), 0.5, 1L),
                "the map holds one pending shadow");
        assertEquals(1, cache.shadow().skippedFull());
        assertEquals(0, metrics.cache(DecisionMetrics.CacheCounter.SHADOW_SKIPPED_FULL),
                "a direct put is not counted; only noteShadow counts the skip");
        cache.noteShadow(new EncounterId(3), 1L, new TacticId("keep_distance"), 0.5, 1L);
        assertEquals(1, metrics.cache(DecisionMetrics.CacheCounter.SHADOW_SKIPPED_FULL));
    }

    @Test
    void theFirstAnswerIsTheLowestKeyWhateverTheMapOrder() {
        var tactic = new TacticId("keep_distance");
        var other = new TacticId("press");
        var answers = new java.util.HashMap<QuestionKey, Answer>();
        for (var index = 9; index >= 0; index--) {
            answers.put(QuestionKey.of(index), new Answer(Map.of(index == 0 ? tactic : other, 1.0), 0.1 * (index + 1)));
        }
        var answered = new DecisionResult.Answered(answers, "jev-1.0.0", 1);
        var first = AnswerCache.first(answered).orElseThrow();
        assertEquals(tactic, first.getKey());
        assertEquals(0.1, first.getValue(), 1e-9);
        assertTrue(AnswerCache.first(new DecisionResult.Answered(Map.of(), "jev-1.0.0", 1)).isEmpty());
    }

    private static DecisionResult.Answered answered(int tokens) {
        var tactic = new TacticId("keep_distance");
        return new DecisionResult.Answered(Map.of(new QuestionKey("q0"), new Answer(Map.of(tactic, 1.0), 0.8)),
                "jev-1.0.0", tokens);
    }
}
