package akyrion.thepredict.domain;

/**
 * Identity of one combat encounter, monotonic per plugin start and never persisted (rule I2).
 * Immutable.
 *
 * @param value positive sequence number
 */
public record EncounterId(long value) {
    /**
     * Validates the identity.
     *
     * @throws IllegalArgumentException if {@code value <= 0}
     */
    public EncounterId {
        if (value <= 0) {
            throw new IllegalArgumentException("encounter must be > 0, was " + value);
        }
    }
}
