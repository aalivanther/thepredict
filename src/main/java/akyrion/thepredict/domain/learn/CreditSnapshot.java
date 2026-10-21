package akyrion.thepredict.domain.learn;

/**
 * One read of the fight clocks used to score terminal credit (M28.2).
 *
 * @param mobDied whether the mob died
 * @param targetDied whether the credited target died
 * @param elapsedTicks ticks on the individual clock, 0 when the clock is not valid
 * @param referenceTicks reference duration, 0 when none is known
 * @param grouped whether a group clock of at least two members is in force
 * @param groupAllDead whether the group was wiped
 * @param groupWon whether the group won
 * @param groupElapsedTicks ticks on the group clock
 * @param groupShare {@code learning.groupCreditShare}
 */
public record CreditSnapshot(boolean mobDied, boolean targetDied, int elapsedTicks, int referenceTicks,
                             boolean grouped, boolean groupAllDead, boolean groupWon, int groupElapsedTicks,
                             double groupShare) {
}
