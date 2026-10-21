package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.ControlLevel;

import java.util.List;
import java.util.Map;

/**
 * What one simulated round gave (M18.7): the winner, the ticks, the damage of each side as fractions of maximum health,
 * the casts and hits of each skill per side, the retreats, the ticks of the ThePredict duelists at each control level,
 * the requests and input tokens, the outcome windows of each tactic, the duplicated casts (casts the world saw that the
 * engine did not count once per intent, which must stay 0) and the sequence of casts of red, for the equivalence of
 * level 4 and the classic duelist.
 *
 * @param baseDelayTicks ticks between the moment red stops perceiving its rival and its drop to level 4, or −1
 * @param redLostHits damaging hits of red that the window of invulnerability of blue took whole
 * @param blueLostHits damaging hits of blue that the window of invulnerability of red took whole
 * @param redConduct what red did, {@link Conduct#EMPTY} in the rounds of M18.7
 * @param blueConduct what blue did, {@link Conduct#EMPTY} in the rounds of M18.7
 */
record DuelResult(DuelSimulator.Arm arm, long seed, boolean swapped, Winner winner, int ticks, double redDealt,
                  double blueDealt, Map<String, Integer> redCasts, Map<String, Integer> blueCasts,
                  Map<String, Integer> redHits, Map<String, Integer> blueHits, int retreats,
                  Map<ControlLevel, Integer> levelTicks, int requests, long tokens,
                  Map<String, double[]> tactics, long duplicateCasts, List<String> redSequence, long baseDelayTicks,
                  String eventDigest, long redLostHits, long blueLostHits, Conduct redConduct,
                  Conduct blueConduct) {
    /** Who won a round. */
    enum Winner {
        RED, BLUE, DRAW
    }

    /** Red wins as a score: 1, a draw 0.5, a loss 0. */
    double redScore() {
        return switch (winner) {
            case RED -> 1.0;
            case BLUE -> 0.0;
            case DRAW -> 0.5;
        };
    }
}
