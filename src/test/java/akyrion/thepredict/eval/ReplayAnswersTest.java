package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Replay hits and misses by model and body hash (M22.4). */
class ReplayAnswersTest {
    @Test
    void aKnownBodyHitsAndAnUnknownBodyMisses() {
        var bank = Map.of("jev|abc", "answer");
        assertEquals("REPLAY", ReplayAnswers.lookup(bank, "jev|abc"));
        assertEquals("REPLAY_MISS", ReplayAnswers.lookup(bank, "jev|nope"));
        assertEquals("REPLAY_PARTIAL", ReplayAnswers.rate(1, 4));
    }
}
