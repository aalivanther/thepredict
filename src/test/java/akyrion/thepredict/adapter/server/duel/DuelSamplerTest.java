package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.engine.IntentBoard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The sampler remembers only followed duelists, so a long recording never grows its memory (SA-18, A5). */
class DuelSamplerTest {
    @Test
    void anIntentRetreatOfAMobThatIsNotFollowedLeavesNoMemory() {
        var rounds = new DuelRounds(0);
        var followed = new UUID(0x5EL, 1);
        rounds.follow(followed, DuelRounds.Side.RED, DuelRounds.Kind.TP, -9.5);
        var rows = new ArrayList<String>();
        var sampler = new DuelSampler(rounds, new IntentBoard(), (file, row) -> rows.add(file + " " + row),
                new DuelSummary());
        for (var index = 0; index < 1000; index++) {
            sampler.intentRetreat(new UUID(0x5FL, index), index);
        }
        assertEquals(0, sampler.remembered());
        sampler.intentRetreat(followed, 7);
        assertEquals(1, sampler.remembered());
        sampler.forget(followed, 50);
        assertEquals(0, sampler.remembered());
        assertEquals(1, rows.size(), "the open retreat of a followed duelist is written when it is forgotten");
    }
}
