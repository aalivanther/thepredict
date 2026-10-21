package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionDispatcherCacheTest {
    @Test
    void shadowAlwaysSendsAndRevalidationFollowsTheFixedSalt() {
        var shadow = cache(AnswerCacheMode.SHADOW, 0.0);
        var on = cache(AnswerCacheMode.ON, 0.1);
        var always = cache(AnswerCacheMode.ON, 1.0);
        var never = cache(AnswerCacheMode.ON, 0.0);
        var shadowSends = 0;
        var draws = 0;
        var alwaysSends = 0;
        var neverSends = 0;
        for (var tick = 0; tick < 1000; tick++) {
            shadowSends += send(shadow, tick);
            draws += send(on, tick);
            alwaysSends += send(always, tick);
            neverSends += send(never, tick);
        }
        assertEquals(1000, shadowSends);
        assertEquals(1000, alwaysSends);
        assertEquals(0, neverSends);
        assertTrue(draws > 50 && draws < 200);
    }

    private static int send(AnswerCache cache, long tick) {
        var looked = cache.lookup("jev-1.2.3", "body", Set.of(new QuestionKey("q0")), tick);
        return looked instanceof AnswerCache.Lookup.Found found && found.alsoSend() ? 1 : 0;
    }

    private static AnswerCache cache(AnswerCacheMode mode, double ratio) {
        var cache = new AnswerCache(mode, 10_000, 8, ratio, 21L, 4, new DecisionMetrics(() -> 0L));
        var tactic = new TacticId("keep_distance");
        cache.remember("jev-1.2.3", "body", Set.of(new QuestionKey("q0")),
                new DecisionResult.Answered(Map.of(new QuestionKey("q0"), new Answer(Map.of(tactic, 1.0), 0.5)),
                        "jev-1.2.3", 4), 0L);
        return cache;
    }
}
