package akyrion.thepredict.domain;

/**
 * One hazard attributed to a push (M24.10). Identity stays out of the record line.
 *
 * @param damage damage taken
 * @param died whether the victim died
 * @param ticks ticks from the push to the damage
 * @param kind the push that was pending
 * @param outcome why the hazard was charged
 */
public record HazardRecord(int damage, boolean died, int ticks, DisplacementKind kind, HazardOutcome outcome) {
    /**
     * A record opened before the damage is attributed.
     *
     * @param damage damage taken
     * @param died whether the victim died
     * @param ticks ticks from the push to the damage
     * @param kind the push that was pending
     */
    public HazardRecord(int damage, boolean died, int ticks, DisplacementKind kind) {
        this(damage, died, ticks, kind, HazardOutcome.UNATTRIBUTED);
    }

    /**
     * Checks the record.
     *
     * @throws IllegalArgumentException when a count is negative or a component is missing
     */
    public HazardRecord {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        if (damage < 0 || ticks < 0) {
            throw new IllegalArgumentException("hazard counts must be >= 0");
        }
    }
}
