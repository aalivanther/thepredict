package akyrion.thepredict.domain;

/**
 * Weights of the enriched threat (M11). Every value is finite and non-negative. Immutable.
 *
 * @param assistsOthers threat added when a player assists another participant
 * @param rangedHarass threat added per ranged hit on the mob
 * @param blockedHits threat added per melee hit the player blocks
 * @param unreachableDecayPerSecond threat removed per second while the player is unreachable
 * @param maxPerWindow maximum threat added to one participant per window
 */
public record ThreatWeights(double assistsOthers, double rangedHarass, double blockedHits,
                            double unreachableDecayPerSecond, double maxPerWindow) {
    /**
     * Validates the weights.
     *
     * @throws IllegalArgumentException if a weight is negative or not finite
     */
    public ThreatWeights {
        DomainChecks.requireNonNegative(assistsOthers, "assistsOthers");
        DomainChecks.requireNonNegative(rangedHarass, "rangedHarass");
        DomainChecks.requireNonNegative(blockedHits, "blockedHits");
        DomainChecks.requireNonNegative(unreachableDecayPerSecond, "unreachableDecayPerSecond");
        DomainChecks.requireNonNegative(maxPerWindow, "maxPerWindow");
    }
}
