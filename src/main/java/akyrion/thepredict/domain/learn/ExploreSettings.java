package akyrion.thepredict.domain.learn;

/**
 * Exploration settings (M28.5). The only accepted policy is epsilon (errata E28-8 applied).
 *
 * @param epsilon explore probability
 * @param maxShare share of adoptions that may explore
 */
public record ExploreSettings(double epsilon, double maxShare) {
    /** Delivery defaults. */
    public static final ExploreSettings DEFAULT = new ExploreSettings(0.10, 0.2);

    /**
     * Validates the ranges.
     */
    public ExploreSettings {
        if (epsilon < 0.0 || epsilon > 0.3 || maxShare < 0.0 || maxShare > 0.5) {
            throw new IllegalArgumentException("exploration settings out of range");
        }
    }
}
