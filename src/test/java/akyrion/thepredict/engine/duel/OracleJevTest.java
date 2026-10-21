package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The judge of the sensitivity to Jev (SIM-12): at {@code p = 1/k} it is uniform over the options, not an
 * anti-oracle; its draws come from the seed of the round and the sequence of the question, so they do not depend on
 * the order the rounds run in; and the measured latency keeps its tail and its lost answers.
 */
class OracleJevTest {
    private static final List<TacticId> OPTIONS = List.of(new TacticId("alpha"), new TacticId("bravo"),
            new TacticId("charlie"), new TacticId("delta"));

    private static OracleJev judge(double p, long seed) {
        return new OracleJev((snapshot, rival, question, profile, index, tick) -> OPTIONS.getFirst(), p,
                LatencyModel.fixed(0), seed);
    }

    private static double[] shares(OracleJev judge, int draws) {
        var counts = new double[OPTIONS.size()];
        for (var sequence = 0; sequence < draws; sequence++) {
            counts[OPTIONS.indexOf(judge.pick(OPTIONS.get(1), OPTIONS, sequence))]++;
        }
        for (var at = 0; at < counts.length; at++) {
            counts[at] /= draws;
        }
        return counts;
    }

    @Test
    void pAtOneOverKIsUniform() {
        for (var share : shares(judge(OracleJev.UNIFORM, 7L), 40_000)) {
            assertEquals(0.25, share, 0.01);
        }
        assertEquals(1.0, shares(judge(1.0, 7L), 1_000)[1], 0.0, "p = 1 always answers the best option");
        assertEquals(0.7, shares(judge(0.7, 7L), 40_000)[1], 0.01);
        var others = shares(judge(0.7, 7L), 40_000);
        assertEquals(others[0], others[2], 0.01, "the misses are uniform over the other options");
    }

    @Test
    void drawsDoNotDependOnArmOrder() {
        var forward = judge(0.55, 11L);
        var backward = judge(0.55, 11L);
        var first = new TacticId[500];
        for (var sequence = 0; sequence < first.length; sequence++) {
            first[sequence] = forward.pick(OPTIONS.get(2), OPTIONS, sequence);
        }
        for (var sequence = first.length - 1; sequence >= 0; sequence--) {
            assertEquals(first[sequence], backward.pick(OPTIONS.get(2), OPTIONS, sequence), "sequence " + sequence);
        }
    }

    @Test
    void theMeasuredLatencyKeepsItsTailAndItsLostAnswers() {
        var model = LatencyModel.measured(20L);
        var lost = 0;
        var slow = 0;
        var draws = 200_000;
        for (var index = 0; index < draws; index++) {
            var ticks = model.ticks(index);
            lost += ticks == LatencyModel.LOST ? 1 : 0;
            slow += ticks >= 7 ? 1 : 0;
        }
        assertEquals(0.005, lost / (double) draws, 0.001);
        assertTrue(slow / (double) draws >= 0.009, "the tail above 330 ms: " + slow);
        assertEquals(model.ticks(1234), LatencyModel.measured(20L).ticks(1234));
        assertEquals(0, LatencyModel.fixed(0).ticks(5));
    }
}
