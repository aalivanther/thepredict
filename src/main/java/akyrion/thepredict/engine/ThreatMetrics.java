package akyrion.thepredict.engine;

/**
 * Fixed counters of {@link ThreatEnrichment} for the diagnostics of M12 and the server script (M11). Server thread
 * only; only {@link ThreatEnrichment} changes them. Never a series per player or per mob (rule A5). They count the
 * changes handed to {@code CombatActions.addThreat}, which still drops one without a threat table or for a player
 * offline, dead or in another world.
 */
public final class ThreatMetrics {
    long assists;
    long blockedHits;
    long rangedHarass;
    long decays;
    long limited;
    long failures;
    double pointsAdded;
    double pointsRemoved;

    ThreatMetrics() {
    }

    /**
     * Counts the assist gains sent to a member's table.
     *
     * @return threat gains for {@code ALLY_ASSISTED}
     */
    public long assists() {
        return assists;
    }

    /**
     * Counts the blocked-hit gains sent to a member's table.
     *
     * @return threat gains for {@code MELEE_BLOCKED}
     */
    public long blockedHits() {
        return blockedHits;
    }

    /**
     * Counts the harass gains of participants who were not the target sent to a member's table.
     *
     * @return threat gains for {@code RANGED_HIT_ON_MOB}
     */
    public long rangedHarass() {
        return rangedHarass;
    }

    /**
     * Counts the losses sent for targets that stayed unreachable.
     *
     * @return decays applied
     */
    public long decays() {
        return decays;
    }

    /**
     * Counts the gains the window refused entirely because it was spent or full.
     *
     * @return limited gains
     */
    public long limited() {
        return limited;
    }

    /**
     * Counts the changes whose call to {@code CombatActions.addThreat} threw; the table keeps its value.
     *
     * @return failed changes
     */
    public long failures() {
        return failures;
    }

    /**
     * Sums the threat points ThePredict sent to add.
     *
     * @return points sent to add, in MythicMobs threat points
     */
    public double pointsAdded() {
        return pointsAdded;
    }

    /**
     * Sums the threat points ThePredict asked to remove; MythicMobs removes at most what the player had.
     *
     * @return points asked to remove, positive
     */
    public double pointsRemoved() {
        return pointsRemoved;
    }
}
