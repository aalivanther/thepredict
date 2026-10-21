package akyrion.thepredict.domain;

/**
 * Read-only state of the native MythicMobs threat table of one mob, for {@code /thepredict inspect} (M11, M12.2).
 * It never names a participant (rule I6). Immutable.
 *
 * @param enabled whether the mob has a threat table ({@code Modules.ThreatTable}); without one the enriched threat
 *                of M11 is inactive for the mob
 * @param entries entries of the table, {@code >= 0}
 * @param topThreat threat of the leader, finite and {@code >= 0}; {@code 0} for an empty table
 * @param totalThreat sum of the table, finite and {@code >= 0}
 */
public record ThreatTableState(boolean enabled, int entries, double topThreat, double totalThreat) {
    /** A mob without a threat table. */
    public static final ThreatTableState DISABLED = new ThreatTableState(false, 0, 0.0, 0.0);

    /**
     * Validates the counters.
     *
     * @throws IllegalArgumentException if a value is negative or not finite, or a disabled table has entries
     */
    public ThreatTableState {
        DomainChecks.requireRange(entries, 0, Integer.MAX_VALUE, "entries");
        DomainChecks.requireNonNegative(topThreat, "topThreat");
        DomainChecks.requireNonNegative(totalThreat, "totalThreat");
        if (!enabled && entries > 0) {
            throw new IllegalArgumentException("a disabled threat table has no entries");
        }
    }
}
