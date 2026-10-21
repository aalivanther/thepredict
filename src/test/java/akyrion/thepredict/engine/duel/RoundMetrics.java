package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.Reaction;

import java.util.Map;
import java.util.OptionalLong;

/**
 * One simulated round, the hook M22 reads (M20.10). The M18 duel path does not fill it yet.
 *
 * @param winner side that won, or empty text on a draw
 * @param ticks length of the round
 * @param timeToKillTicks ticks until the winner's kill, empty on a draw
 * @param dealt damage dealt by the winner
 * @param taken damage taken by the winner
 * @param castsBySkill casts by skill id
 * @param maxCastsBySkill cooldown ceiling while in range
 * @param reactions reactions armed
 * @param microTicks ticks spent in each micro
 * @param answersApplied answers that became an intent
 * @param answersAged answers dropped because they aged
 * @param answersDiscarded discards by reason
 * @param jevCoverage share of ticks under a Jev intent
 * @param tickLayerNanosP50 tick-layer nanos, p50
 * @param tickLayerNanosP99 tick-layer nanos, p99
 * @param requests requests sent
 * @param inputTokens tokens reserved
 * @param telegraphs tells opened
 * @param landed tells whose hit landed
 * @param avoided tells the target avoided
 * @param blocked tells the target blocked
 * @param punished tells punished during recovery
 * @param staggers staggers
 * @param unwarnedHits hits of medium or harder with no minimum tell
 */
public record RoundMetrics(String winner, long ticks, OptionalLong timeToKillTicks, double dealt, double taken,
                           Map<String, Integer> castsBySkill, Map<String, Integer> maxCastsBySkill,
                           Map<Reaction, Integer> reactions, Map<MicroBehaviour, Integer> microTicks,
                           int answersApplied, int answersAged, Map<DiscardReason, Integer> answersDiscarded,
                           double jevCoverage, long tickLayerNanosP50, long tickLayerNanosP99, int requests,
                           long inputTokens, int telegraphs, int landed, int avoided, int blocked, int punished,
                           int staggers, int unwarnedHits) {
    /**
     * Dodge ratio of real tells. Feints are not part of these counters.
     *
     * @return {@code (avoided + blocked) / (landed + avoided + blocked)}, or 0 when nothing closed that way
     */
    public double dodgeAfterTelegraphRatio() {
        var denominator = landed + avoided + blocked;
        if (denominator == 0) {
            return 0.0;
        }
        return (avoided + blocked) / (double) denominator;
    }
}
